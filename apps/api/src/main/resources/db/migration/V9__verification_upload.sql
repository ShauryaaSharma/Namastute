-- ID verification, slice 1: uploading (Learning Notes 78)
alter table verifications
  add column gesture      text,         -- the random gesture asked for in the selfie: a simple liveness check
  add column consented_at timestamptz;  -- when the person agreed to the notice (DPDP Act: a record of consent)

-- At most one check waiting for review per person
create unique index verifications_one_pending on verifications (user_id) where status = 'pending';
