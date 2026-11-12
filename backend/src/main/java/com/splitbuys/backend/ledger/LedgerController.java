package com.splitbuys.backend.ledger;

import com.splitbuys.backend.common.security.CurrentUser;
import com.splitbuys.backend.common.web.PageCursor;
import com.splitbuys.backend.common.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
class LedgerController {
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final LedgerService ledger;
    private final BalanceService balances;
    private final ReminderService reminders;
    private final SpendingService spending;

    LedgerController(LedgerService ledger, BalanceService balances, ReminderService reminders, SpendingService spending) {
        this.spending = spending;
        this.ledger = ledger;
        this.balances = balances;
        this.reminders = reminders;
    }

    @GetMapping("/expenses")
    PageResponse<ExpenseDto> expenses(@CurrentUser long userId, @PathVariable long groupId,
                                      @RequestParam(defaultValue = "" + PageCursor.DEFAULT_LIMIT) int limit,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false, name = "q") String text,
                                      @RequestParam(required = false) ExpenseCategory category) {
        return ledger.listExpenses(groupId, userId, limit, cursor, text, category);
    }

    @GetMapping("/expenses/{expenseId}")
    ExpenseDto expense(@CurrentUser long userId, @PathVariable long groupId, @PathVariable long expenseId) {
        return ledger.getExpense(groupId, expenseId, userId);
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    ExpenseDto addExpense(@CurrentUser long userId, @PathVariable long groupId,
                          @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                          @Valid @RequestBody ExpenseRequest req) {
        return ledger.addExpense(groupId, userId, idempotencyKey, req);
    }

    @PutMapping("/expenses/{expenseId}")
    ExpenseDto updateExpense(@CurrentUser long userId, @PathVariable long groupId, @PathVariable long expenseId,
                             @Valid @RequestBody ExpenseRequest req) {
        return ledger.updateExpense(groupId, expenseId, userId, req);
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteExpense(@CurrentUser long userId, @PathVariable long groupId, @PathVariable long expenseId) {
        ledger.deleteExpense(groupId, expenseId, userId);
    }

    @PostMapping("/reminders")
    ReminderDto remind(@CurrentUser long userId, @PathVariable long groupId, @Valid @RequestBody ReminderRequest req) {
        return reminders.remind(groupId, userId, req.toUserId());
    }

    @GetMapping("/summary")
    SpendingSummary summary(@CurrentUser long userId, @PathVariable long groupId) {
        return spending.summary(groupId, userId);
    }

    @GetMapping("/history")
    PageResponse<HistoryDto> history(@CurrentUser long userId, @PathVariable long groupId,
                                     @RequestParam(defaultValue = "" + PageCursor.DEFAULT_LIMIT) int limit,
                                     @RequestParam(required = false) String cursor) {
        return ledger.listHistory(groupId, userId, limit, cursor);
    }

    @GetMapping("/expenses/{expenseId}/history")
    java.util.List<HistoryDto> expenseHistory(@CurrentUser long userId, @PathVariable long groupId, @PathVariable long expenseId) {
        return ledger.expenseHistory(groupId, expenseId, userId);
    }

    @PostMapping("/history/{historyId}/undo")
    HistoryDto undo(@CurrentUser long userId, @PathVariable long groupId, @PathVariable long historyId) {
        return ledger.undo(groupId, historyId, userId);
    }

    @GetMapping("/settlements")
    PageResponse<SettlementDto> settlements(@CurrentUser long userId, @PathVariable long groupId,
                                            @RequestParam(defaultValue = "" + PageCursor.DEFAULT_LIMIT) int limit,
                                            @RequestParam(required = false) String cursor) {
        return ledger.listSettlements(groupId, userId, limit, cursor);
    }

    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    SettlementDto addSettlement(@CurrentUser long userId, @PathVariable long groupId,
                                @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                                @Valid @RequestBody SettlementRequest req) {
        return ledger.addSettlement(groupId, userId, idempotencyKey, req);
    }

    @DeleteMapping("/settlements/{settlementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSettlement(@CurrentUser long userId, @PathVariable long groupId, @PathVariable long settlementId) {
        ledger.deleteSettlement(groupId, settlementId, userId);
    }

    @GetMapping("/balances")
    BalancesResponse balances(@CurrentUser long userId, @PathVariable long groupId) {
        return balances.balances(groupId, userId);
    }
}
