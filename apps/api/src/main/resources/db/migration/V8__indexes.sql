-- Indexes for lookups that run on almost every request (Learning Notes 68). Postgres doesn't index foreign keys by itself.

-- "Which group is this trip in?" (groupOf, current group, requests, live location, reports)
create index group_members_trip on group_members (trip_id);

-- Every push looks up the subscriptions of the people it's for
create index push_subscriptions_user on push_subscriptions (user_id);

-- Matching only ever looks for 'searching' trips, but the V1 index covered every trip ever made and grew with history
-- (found in the load test, Learning Notes 67.4). Same name, now partial.
drop index trip_dest_gist;
create index trip_dest_gist on trip_requests using gist (dest) where status = 'searching';
