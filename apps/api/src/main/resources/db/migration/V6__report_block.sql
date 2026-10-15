-- Report and block (Learning Notes 56)

-- Fixed categories make reports sortable and countable for whoever reviews them
alter table reports add column category text not null default 'other'
  check (category in ('unsafe', 'harassment', 'no_show', 'fake_profile', 'other'));
-- One report per person per group: a second one changes nothing
alter table reports add constraint reports_once unique (reporter_id, reported_id, group_id);

-- An opaque handle for "unblock", so the other person's user id is never sent to the browser
alter table blocks add column id uuid not null default gen_random_uuid() unique;
alter table blocks add column created_at timestamptz not null default now();
