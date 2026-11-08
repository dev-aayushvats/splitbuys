-- A deleted account keeps its row (other people's expense history points at it) but loses everything personal;
-- deleted_at marks it so its login tokens stop working.
ALTER TABLE app_user ADD COLUMN deleted_at timestamp(6) with time zone;
