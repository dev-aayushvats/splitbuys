-- Remembers the outcome of a create request so that a repeat of the same request (a retry after a timeout, a double tap)
-- returns the original result instead of creating a second expense. Rows are disposable and purged after a day.
CREATE TABLE idempotency_key (
    id integer,
    user_id bigint not null references app_user (id) on delete cascade,
    key_value varchar(64) not null,
    fingerprint varchar(64) not null,
    response varchar(16384) not null,
    created_at timestamp not null,
    primary key (id));
CREATE UNIQUE INDEX uq_idempotency_user_key ON idempotency_key (user_id, key_value);
CREATE INDEX idx_idempotency_created ON idempotency_key (created_at);
