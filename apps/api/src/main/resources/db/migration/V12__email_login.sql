-- Logging in by email (Learning Notes 88): an account has a phone number, an email address, or both.
-- Emails are stored trimmed and lower-cased by the app, so "unique" means one account per address.
alter table users alter column phone drop not null;
alter table users add column email text unique;
alter table users add constraint users_phone_or_email check (phone is not null or email is not null);
