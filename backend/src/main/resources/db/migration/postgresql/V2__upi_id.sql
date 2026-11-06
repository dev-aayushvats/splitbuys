-- The UPI ID a user wants to be paid on. Optional; only ever shown to the people who must pay that user.
ALTER TABLE app_user ADD COLUMN upi_id varchar(255);
