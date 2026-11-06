-- 200 users in 40 groups of 5, each group with 40 equal-split expenses (8,000 expenses, 40,000 shares).
-- Run after the backend has started (Flyway creates the tables).
INSERT INTO app_user (id, email, name)
SELECT i, 'load' || i || '@test.dev', 'User ' || i FROM generate_series(1, 200) i;

INSERT INTO expense_group (id, created_at, invite_token, name)
SELECT g, now(), 'load-invite-' || g, 'Group ' || g FROM generate_series(1, 40) g;

INSERT INTO group_member (expense_group_id, members_id)
SELECT g, (g - 1) * 5 + m FROM generate_series(1, 40) g, generate_series(1, 5) m;

INSERT INTO expense (id, amount_paise, created_at, description, split_type, created_by_id, group_id, payer_id)
SELECT (g - 1) * 40 + e, 50000, now() - (e || ' minutes')::interval, 'Seed expense ' || e, 'EQUAL',
       (g - 1) * 5 + 1 + (e % 5), g, (g - 1) * 5 + 1 + (e % 5)
FROM generate_series(1, 40) g, generate_series(1, 40) e;

INSERT INTO expense_share (amount_paise, expense_id, user_id)
SELECT 10000, x.id, (x.group_id - 1) * 5 + m FROM expense x, generate_series(1, 5) m;

SELECT setval(pg_get_serial_sequence('app_user', 'id'), 200);
SELECT setval(pg_get_serial_sequence('expense_group', 'id'), 40);
SELECT setval(pg_get_serial_sequence('expense', 'id'), 1600);
ANALYZE;
