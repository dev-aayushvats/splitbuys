-- A browser or device that asked to receive push notifications for a user. The endpoint identifies the device, so it is unique:
-- a device belongs to one person at a time. Cascades with the user row for completeness; account deletion also removes the rows.
CREATE TABLE push_subscription (
    id integer,
    user_id bigint not null references app_user (id) on delete cascade,
    endpoint varchar(2048) not null,
    p256dh varchar(128) not null,
    auth_secret varchar(64) not null,
    created_at timestamp not null,
    primary key (id));
CREATE UNIQUE INDEX uq_push_endpoint ON push_subscription (endpoint);
CREATE INDEX idx_push_user ON push_subscription (user_id);
