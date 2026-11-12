package com.splitbuys.backend.push;

import com.splitbuys.backend.common.event.ExpenseAdded;
import com.splitbuys.backend.common.event.PaymentRecorded;
import com.splitbuys.backend.common.event.ReminderSent;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns ledger events into notifications. It runs only after the change has committed (a rolled-back expense notifies
 * nobody) and hands the sending to a small background pool, so a slow push service never slows the request that caused it.
 * Delivery is best effort: failures are logged, never retried, and never reach the person who made the change.
 */
@Component
class PushNotifier {
    private static final Logger log = LoggerFactory.getLogger(PushNotifier.class);

    private final PushService push;
    private final PushGateway gateway;
    private final ThreadPoolExecutor pool;

    PushNotifier(PushService push, PushGateway gateway, ThreadPoolExecutor pushPool) {
        this.push = push;
        this.gateway = gateway;
        this.pool = pushPool;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onExpenseAdded(ExpenseAdded event) {
        if (push.enabled()) {
            submit(() -> deliver(event.memberIds(), id -> PushMessages.forExpense(event, id)));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPaymentRecorded(PaymentRecorded event) {
        if (push.enabled()) {
            submit(() -> deliver(java.util.List.of(event.fromUserId(), event.toUserId()), id -> PushMessages.forPayment(event, id)));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onReminderSent(ReminderSent event) {
        if (push.enabled()) {
            submit(() -> deliver(java.util.List.of(event.debtorId()), id -> PushMessages.forReminder(event, id)));
        }
    }

    private void submit(Runnable work) {
        try {
            pool.execute(work);
        } catch (RejectedExecutionException e) {
            log.warn("Push queue is full, dropping a notification batch");
        }
    }

    void deliver(Collection<Long> recipients, java.util.function.LongFunction<Optional<PushMessages.Message>> messageFor) {
        try {
            for (long recipient : recipients) {
                Optional<PushMessages.Message> message = messageFor.apply(recipient);
                if (message.isEmpty()) {
                    continue;
                }
                byte[] payload = message.get().toJson();
                for (PushSubscription device : push.devicesOf(java.util.List.of(recipient))) {
                    PushGateway.Outcome outcome = gateway.send(device.getEndpoint(), device.getP256dh(), device.getAuthSecret(), payload);
                    if (outcome == PushGateway.Outcome.GONE) {
                        push.drop(device.getId());
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not deliver notifications: {}", e.toString());
        }
    }
}
