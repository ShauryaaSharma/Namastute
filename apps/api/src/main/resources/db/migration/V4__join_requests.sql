-- "Keshav's trip asks Asha's trip to share a cab." A group is only created when someone says yes.
-- Requests link trips, not groups, so asking never takes you out of other people's matches.
create table join_requests (
  id           uuid primary key default gen_random_uuid(),
  from_trip_id uuid not null references trip_requests,
  to_trip_id   uuid not null references trip_requests,
  status       text not null default 'pending' check (status in ('pending','accepted','declined','withdrawn')),
  created_at   timestamptz not null default now(),
  unique (from_trip_id, to_trip_id)               -- ask each person once per trip: no re-asking after a "no"
);
create index join_requests_to on join_requests (to_trip_id) where status = 'pending';
