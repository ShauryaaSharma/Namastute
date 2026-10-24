-- ID verification, slice 2b: what the automatic checks found (Learning Notes 82). Only results: never the faces'
-- numbers (embeddings) or the ID's full text. face_similarity (V1) now holds SFace's cosine similarity, -1 to 1.
-- V11, not V10: the refresh-token fix (V10__session_parent.sql) was written at the same time on another branch and
-- is merged first.
alter table verifications
  add column suggested_gender text,          -- read from the ID; the reviewer confirms or changes it
  add column suggested_last4  text,
  add column check_notes      text,          -- e.g. "Possibly a full (unmasked) Aadhaar number"
  add column checked_at       timestamptz;   -- when the checks ran; null = not yet
