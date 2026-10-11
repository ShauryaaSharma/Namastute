create extension if not exists postgis;
create extension if not exists pgcrypto;

create type verification_status as enum ('unverified','pending','verified','rejected');
create type gender              as enum ('female','male','other');
create type trip_status         as enum ('searching','grouped','cancelled','expired','done');
create type group_status        as enum ('open','locked','met','done','cancelled','expired');
create type member_status       as enum ('requested','accepted','declined','left','removed','no_show');

create table users (
  id            uuid primary key default gen_random_uuid(),
  phone         text not null unique,              -- E.164, +91XXXXXXXXXX
  name          text not null,
  gender        gender,                            -- set ONLY by admin from the verified ID
  verification  verification_status not null default 'unverified',
  verified_at   timestamptz,
  upi_id        text,                              -- optional, shown only to group members
  role          text not null default 'user' check (role in ('user','admin')),
  rating_sum    int not null default 0,
  rating_count  int not null default 0,
  no_show_count int not null default 0,
  banned_at     timestamptz,
  created_at    timestamptz not null default now()
);

create table sessions (                            -- refresh tokens, rotated on every use
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references users on delete cascade,
  token_hash   text not null unique,               -- sha256 of refresh token, never the token
  expires_at   timestamptz not null,
  revoked_at   timestamptz,
  created_at   timestamptz not null default now()
);

create table verifications (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references users on delete cascade,
  doc_type        text not null check (doc_type in ('passport','driving_licence','voter_id','masked_aadhaar')),
  doc_key         text,                            -- S3 key; NULLed once the file is deleted
  selfie_key      text,
  face_similarity real,                            -- from Rekognition, 0..100
  doc_last4       text,                            -- the only document number we keep
  status          verification_status not null default 'pending',
  reviewed_by     uuid references users,
  reviewed_at     timestamptz,
  reject_reason   text,
  created_at      timestamptz not null default now()
);

create table terminals (
  id           text primary key,                   -- 'DEL-T1', 'DEL-T2', 'DEL-T3'
  airport      text not null,                      -- 'DEL'
  name         text not null,
  pickup       geography(point, 4326) not null,
  pickup_note  text                                -- filled by PM after visiting
);

create table trip_requests (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references users,
  terminal_id text not null references terminals,
  flight_no   text,                                -- informational in v1
  exit_at     timestamptz not null,                -- when they expect to be outside
  dest        geography(point, 4326) not null,     -- exact; never sent to other users
  dest_area   text not null,                       -- 'DLF Phase 3, Gurugram' — what others see
  solo_km     real not null,                       -- pickup → dest, computed on create
  seats       smallint not null default 1 check (seats between 1 and 3),
  bags        smallint not null default 1 check (bags between 0 and 6),
  women_only  boolean not null default false,
  status      trip_status not null default 'searching',
  created_at  timestamptz not null default now()
);
create unique index trip_one_active   on trip_requests (user_id) where status in ('searching','grouped');
create index        trip_search       on trip_requests (terminal_id, exit_at) where status = 'searching';
create index        trip_dest_gist    on trip_requests using gist (dest);

create table groups (
  id           uuid primary key default gen_random_uuid(),
  terminal_id  text not null references terminals,
  meet_at      timestamptz not null,               -- max(exit_at) of accepted members
  women_only   boolean not null,
  status       group_status not null default 'open',
  capacity     smallint not null default 4,
  seats_taken  smallint not null default 0,
  bags_taken   smallint not null default 0,
  booker_id    uuid references users,
  fare_total   numeric(10,2),                      -- entered by booker after the ride
  created_at   timestamptz not null default now(),
  check (seats_taken <= capacity)
);
create index groups_open on groups (terminal_id, meet_at) where status = 'open';

create table group_members (
  group_id    uuid not null references groups on delete cascade,
  user_id     uuid not null references users,
  trip_id     uuid not null references trip_requests,
  status      member_status not null default 'requested',
  share       numeric(10,2),                       -- computed fare share
  created_at  timestamptz not null default now(),
  primary key (group_id, user_id)
);

create table messages (
  id          bigint generated always as identity primary key,
  group_id    uuid not null references groups on delete cascade,
  user_id     uuid references users,               -- NULL for system messages
  kind        text not null default 'text' check (kind in ('text','system')),
  body        text not null check (length(body) <= 1000),
  created_at  timestamptz not null default now()
);
create index messages_by_group on messages (group_id, id);

create table ratings (
  group_id  uuid not null references groups,
  rater_id  uuid not null references users,
  ratee_id  uuid not null references users,
  score     smallint not null check (score between 1 and 5),
  primary key (group_id, rater_id, ratee_id)
);

create table reports (
  id          uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references users,
  reported_id uuid not null references users,
  group_id    uuid references groups,
  reason      text not null,
  status      text not null default 'open' check (status in ('open','actioned','dismissed')),
  created_at  timestamptz not null default now()
);

create table blocks (
  blocker_id uuid not null references users,
  blocked_id uuid not null references users,
  primary key (blocker_id, blocked_id)
);

create table push_subscriptions (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references users on delete cascade,
  endpoint   text not null unique,
  keys       jsonb not null,
  created_at timestamptz not null default now()
);