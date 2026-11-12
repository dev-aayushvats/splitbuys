-- A short note one member leaves on an expense ("why is this 2,300?"). expense_id is not a foreign key on purpose: a deleted
-- expense that is restored comes back with a new id, and its comments are moved to it; the group is the owner for cleanup.
CREATE TABLE expense_comment (
    id integer,
    group_id bigint not null references expense_group (id),
    expense_id bigint not null,
    author_id bigint not null references app_user (id),
    body varchar(500) not null,
    created_at timestamp not null,
    primary key (id));
CREATE INDEX idx_comment_expense ON expense_comment (group_id, expense_id, created_at);
CREATE INDEX idx_comment_author ON expense_comment (author_id, created_at);
