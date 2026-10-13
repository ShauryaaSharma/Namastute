-- OTP login creates the user from the phone number alone; the name is filled in later on the profile screen
alter table users alter column name drop not null;
