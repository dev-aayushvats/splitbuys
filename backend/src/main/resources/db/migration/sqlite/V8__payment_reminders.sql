-- A reminder one member sent another to pay what they owe. It exists for the cooldown (one per pair and group a day) and a
-- daily cap per sender; it holds no message text.
CREATE TABLE payment_reminder (
    id integer,
    group_id bigint not null references expense_group (id),
    sender_id bigint not null references app_user (id),
    debtor_id bigint not null references app_user (id),
    amount_paise bigint not null,
    created_at timestamp not null,
    primary key (id));
CREATE INDEX idx_reminder_pair ON payment_reminder (group_id, sender_id, debtor_id, created_at);
CREATE INDEX idx_reminder_sender ON payment_reminder (sender_id, created_at);
