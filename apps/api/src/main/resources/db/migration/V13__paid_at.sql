-- "I've paid" (Learning Notes 92): when a member said they paid the booker. Their own word only: Namastute never
-- sees or checks payments.
alter table group_members add column paid_at timestamptz;
