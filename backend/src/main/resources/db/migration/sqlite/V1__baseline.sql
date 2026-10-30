-- Baseline: the schema as Hibernate generated it before Flyway took over.
-- Every statement is IF NOT EXISTS so that an existing database (adopted via baseline-on-migrate) is left untouched.
CREATE TABLE IF NOT EXISTS app_user (id integer, email varchar(255) not null unique, name varchar(255), primary key (id));
CREATE TABLE IF NOT EXISTS expense_group (id integer, created_at timestamp, invite_token varchar(255) not null unique, name varchar(255) not null, primary key (id));
CREATE TABLE IF NOT EXISTS group_member (expense_group_id bigint not null, members_id bigint not null, primary key (expense_group_id, members_id));
CREATE TABLE IF NOT EXISTS expense (id integer, amount_paise bigint not null, created_at timestamp, description varchar(255), split_type varchar(255) check ((split_type in ('EQUAL','EXACT','PERCENT','SHARES'))), created_by_id bigint not null, group_id bigint not null, payer_id bigint not null, primary key (id));
CREATE TABLE IF NOT EXISTS expense_share (id integer, amount_paise bigint not null, expense_id bigint not null, user_id bigint not null, primary key (id));
CREATE TABLE IF NOT EXISTS settlement (id integer, amount_paise bigint not null, created_at timestamp, created_by_id bigint not null, from_user_id bigint not null, group_id bigint not null, to_user_id bigint not null, primary key (id));
CREATE TABLE IF NOT EXISTS otp_code (id integer, attempts integer not null, code_hash varchar(255), created_at timestamp, email varchar(255), expires_at timestamp, primary key (id));
