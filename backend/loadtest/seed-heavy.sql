-- Power users for the Friends / Activity feeds, on top of seed.sql (the 200 users there stay "typical": 1 group, 4 friends, 40 expenses).
--   10 heavy users (ids 201-210) + 200 fresh co-members (211-410)
--   50 groups of 14 (all 10 heavy users + 4 co-members), 200 equal-split expenses each, 20 payments each
--   => each heavy user: 50 groups, 209 friends (9 other heavy + 200 co-members), 10,000 expenses + about 200 payments in the feed
INSERT INTO app_user (id, email, name)
SELECT 200 + i, 'heavy' || i || '@test.dev', 'Heavy ' || i FROM generate_series(1, 10) i;
INSERT INTO app_user (id, email, name)
SELECT 210 + i, 'peer' || i || '@test.dev', 'Peer ' || i FROM generate_series(1, 200) i;

INSERT INTO expense_group (id, created_at, invite_token, name)
SELECT 40 + g, now(), 'heavy-invite-' || g, 'Heavy group ' || g FROM generate_series(1, 50) g;

INSERT INTO group_member (expense_group_id, members_id)
SELECT 40 + g, 200 + h FROM generate_series(1, 50) g, generate_series(1, 10) h;
INSERT INTO group_member (expense_group_id, members_id)
SELECT 40 + g, 210 + (g - 1) * 4 + k FROM generate_series(1, 50) g, generate_series(1, 4) k;

-- 140,000 paise split 14 ways = 10,000 each; the payer is mostly a heavy user, sometimes a co-member
INSERT INTO expense (id, amount_paise, created_at, description, split_type, created_by_id, group_id, payer_id)
SELECT 10000 + (g - 1) * 200 + e, 140000, now() - ((e * 50 + g) || ' seconds')::interval, 'Heavy expense ' || e, 'EQUAL',
       200 + 1 + (e % 10), 40 + g,
       CASE WHEN e % 7 = 0 THEN 210 + (g - 1) * 4 + 1 + (e % 4) ELSE 200 + 1 + (e % 10) END
FROM generate_series(1, 50) g, generate_series(1, 200) e;

INSERT INTO expense_share (amount_paise, expense_id, user_id)
SELECT 10000, x.id, 200 + h FROM expense x, generate_series(1, 10) h WHERE x.id >= 10000;
INSERT INTO expense_share (amount_paise, expense_id, user_id)
SELECT 10000, x.id, 210 + (x.group_id - 41) * 4 + k FROM expense x, generate_series(1, 4) k WHERE x.id >= 10000;

INSERT INTO settlement (id, amount_paise, created_at, created_by_id, from_user_id, group_id, to_user_id)
SELECT (g - 1) * 20 + s, 5000, now() - ((s * 700 + g) || ' seconds')::interval, 200 + 1 + (s % 10), 200 + 1 + (s % 10), 40 + g, 200 + 1 + ((s + 1) % 10)
FROM generate_series(1, 50) g, generate_series(1, 20) s;

SELECT setval(pg_get_serial_sequence('app_user', 'id'), 410);
SELECT setval(pg_get_serial_sequence('expense_group', 'id'), 90);
SELECT setval(pg_get_serial_sequence('expense', 'id'), 20000);
SELECT setval(pg_get_serial_sequence('settlement', 'id'), 1000);
ANALYZE;
