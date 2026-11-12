-- What an expense was for, from a short fixed list. Existing expenses become OTHER.
ALTER TABLE expense ADD COLUMN category varchar(16) NOT NULL DEFAULT 'OTHER';
