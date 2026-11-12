-- What happened to each expense: created, edited, deleted, restored. Snapshots are JSON of the expense before and after the
-- change (user ids only, so a later rename or account deletion shows through). expense_id has no foreign key on purpose: the
-- history of a deleted expense has to outlive the expense. Rows go with the group.
CREATE TABLE expense_history (
    id integer,
    group_id bigint not null references expense_group (id),
    expense_id bigint not null,
    action varchar(16) not null,
    actor_id bigint not null references app_user (id),
    created_at timestamp not null,
    before_json varchar(16384),
    after_json varchar(16384),
    restored_as bigint,
    primary key (id));
CREATE INDEX idx_history_group_created ON expense_history (group_id, created_at, id);
CREATE INDEX idx_history_expense ON expense_history (group_id, expense_id, id);
