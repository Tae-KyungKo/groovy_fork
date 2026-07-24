ALTER TABLE calendars ADD COLUMN end_date date NULL;

UPDATE calendars SET end_date = date WHERE end_date IS NULL;

ALTER TABLE calendars MODIFY COLUMN end_date date NOT NULL;
