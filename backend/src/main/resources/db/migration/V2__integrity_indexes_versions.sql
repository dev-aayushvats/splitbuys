-- Referential integrity, indexes for the hot queries, and optimistic-locking version columns.
-- SQLite cannot add a foreign key to an existing table, so the child tables are rebuilt (parents are only altered).
-- Rows are copied across before the old table is dropped; a row that violates a new constraint fails the migration.

ALTER TABLE expense_group ADD COLUMN version bigint NOT NULL DEFAULT 0;

CREATE TABLE group_member_new (
    expense_group_id bigint not null references expense_group (id) on delete cascade,
    members_id bigint not null references app_user (id),
    primary key (expense_group_id, members_id));
INSERT INTO group_member_new (expense_group_id, members_id) SELECT expense_group_id, members_id FROM group_member;
DROP TABLE group_member;
ALTER TABLE group_member_new RENAME TO group_member;
CREATE INDEX idx_group_member_user ON group_member (members_id);

CREATE TABLE expense_new (
    id integer, version bigint not null default 0,
    amount_paise bigint not null, created_at timestamp, description varchar(255),
    split_type varchar(255) check ((split_type in ('EQUAL','EXACT','PERCENT','SHARES'))),
    created_by_id bigint not null references app_user (id),
    group_id bigint not null references expense_group (id),
    payer_id bigint not null references app_user (id),
    primary key (id));
INSERT INTO expense_new (id, version, amount_paise, created_at, description, split_type, created_by_id, group_id, payer_id)
    SELECT id, 0, amount_paise, created_at, description, split_type, created_by_id, group_id, payer_id FROM expense;
DROP TABLE expense;
ALTER TABLE expense_new RENAME TO expense;
CREATE INDEX idx_expense_group_created ON expense (group_id, created_at);

CREATE TABLE expense_share_new (
    id integer, amount_paise bigint not null,
    expense_id bigint not null references expense (id) on delete cascade,
    user_id bigint not null references app_user (id),
    primary key (id));
INSERT INTO expense_share_new (id, amount_paise, expense_id, user_id) SELECT id, amount_paise, expense_id, user_id FROM expense_share;
DROP TABLE expense_share;
ALTER TABLE expense_share_new RENAME TO expense_share;
CREATE INDEX idx_expense_share_expense ON expense_share (expense_id);
CREATE INDEX idx_expense_share_user ON expense_share (user_id);

CREATE TABLE settlement_new (
    id integer, amount_paise bigint not null, created_at timestamp,
    created_by_id bigint not null references app_user (id),
    from_user_id bigint not null references app_user (id),
    group_id bigint not null references expense_group (id),
    to_user_id bigint not null references app_user (id),
    primary key (id));
INSERT INTO settlement_new (id, amount_paise, created_at, created_by_id, from_user_id, group_id, to_user_id)
    SELECT id, amount_paise, created_at, created_by_id, from_user_id, group_id, to_user_id FROM settlement;
DROP TABLE settlement;
ALTER TABLE settlement_new RENAME TO settlement;
CREATE INDEX idx_settlement_group ON settlement (group_id, created_at);

CREATE INDEX idx_otp_code_email ON otp_code (email, created_at);
