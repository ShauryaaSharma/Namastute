-- Ratings are a thumbs up or down, not 1-5 stars (Learning Notes 62: stars inflate until only 5 means "fine").
-- Nothing could write to this table before, so it holds no data to convert.
alter table ratings drop column score;
alter table ratings add column up boolean not null;
