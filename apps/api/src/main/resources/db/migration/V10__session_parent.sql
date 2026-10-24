-- Which session each refresh token replaced, so a lost refresh response can be retried for a few seconds (Learning Notes 24.7)
alter table sessions add column parent_id uuid references sessions on delete cascade;

-- "Is there an active session that replaced this one?" runs on every replayed token
create index sessions_parent on sessions (parent_id);
