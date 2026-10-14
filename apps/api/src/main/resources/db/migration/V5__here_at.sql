-- When each member tapped "I'm here" at the pickup point (null = not yet). 2+ taps move the group locked → met.
alter table group_members add column here_at timestamptz;
