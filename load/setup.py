"""Prepares a LOCAL load test (Learning Notes 66): test users, pickup groups and access tokens.

    python load/setup.py --travellers 300 --groups 75     # before a run
    python load/setup.py --cleanup                         # afterwards

Logging in through OTP can't be used: it's rate-limited per phone and per IP on purpose. Instead this signs access
tokens with the server's own JWT secret, so it refuses to run unless infra/.env is the local test setup
(DOMAIN=localhost). The API itself is unchanged: there is no test login in the app.
"""
import argparse
import base64
import hashlib
import hmac
import json
import subprocess
import sys
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENV_FILE = ROOT / "infra" / ".env"
COMPOSE = ["docker", "compose", "-f", str(ROOT / "infra" / "docker-compose.prod.yml"), "--env-file", str(ENV_FILE)]
# Indian mobile numbers start with 6-9, so these can never be real people's numbers
RUSH, CHATTER = "+911000", "+911001"


def env():
    pairs = (line.split("=", 1) for line in ENV_FILE.read_text().splitlines() if "=" in line and not line.startswith("#"))
    return {k.strip(): v.strip() for k, v in pairs}


def psql(sql):
    out = subprocess.run(COMPOSE + ["exec", "-T", "postgres", "psql", "-U", "namastute", "-d", "namastute",
                                    "-v", "ON_ERROR_STOP=1", "-tAq"],
                         input=sql, capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0:
        sys.exit(out.stderr)
    return [line for line in out.stdout.splitlines() if line]


def cleanup():
    # Only load-test users' trips and groups; nobody else's data is touched
    psql(f"""
        update groups set status = 'cancelled' where status in ('open', 'locked', 'met') and id in (
          select m.group_id from group_members m join users u on u.id = m.user_id
          where u.phone like '{RUSH}%' or u.phone like '{CHATTER}%');
        update trip_requests set status = 'cancelled' where status in ('searching', 'grouped') and user_id in (
          select id from users where phone like '{RUSH}%' or phone like '{CHATTER}%');
    """)


def users(prefix, label, count):
    psql(f"""insert into users (phone, name)
             select '{prefix}' || lpad(i::text, 6, '0'), '{label} ' || i from generate_series(1, {count}) i
             on conflict (phone) do nothing;""")
    return psql(f"select id from users where phone like '{prefix}%' order by phone limit {count};")


def pickup_groups(user_ids):
    """Locked groups of four meeting in 10 minutes: inside the live-location window, ready to chat."""
    sql, group_of_member = [], []   # each member's group, in the same order as user_ids
    for i in range(0, len(user_ids) - 3, 4):
        group = str(uuid.uuid4())
        members = user_ids[i:i + 4]
        sql.append(f"""insert into groups (id, terminal_id, meet_at, women_only, status, seats_taken, bags_taken, booker_id)
                       values ('{group}', 'DEL-T3', now() + interval '10 minutes', false, 'locked', 4, 4, '{members[0]}');""")
        for user in members:
            trip = str(uuid.uuid4())
            sql.append(f"""insert into trip_requests (id, user_id, terminal_id, exit_at, dest, dest_area, solo_km, status)
                           values ('{trip}', '{user}', 'DEL-T3', now() + interval '10 minutes',
                                   'SRID=4326;POINT(77.0930 28.4936)', 'DLF Phase 3, Gurugram', 20, 'grouped');
                           insert into group_members (group_id, user_id, trip_id, status) values ('{group}', '{user}', '{trip}', 'accepted');""")
            group_of_member.append(group)
    psql("\n".join(sql))
    return group_of_member


def token(secret, user_id, hours=3):
    # The same token AccessTokens issues (HS256, issuer namastute-api), but valid for the whole test
    b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=").decode()
    now = int(time.time())
    head = b64(json.dumps({"alg": "HS256"}).encode())
    body = b64(json.dumps({"iss": "namastute-api", "sub": user_id, "iat": now, "exp": now + hours * 3600}).encode())
    sig = b64(hmac.new(secret.encode(), f"{head}.{body}".encode(), hashlib.sha256).digest())
    return f"{head}.{body}.{sig}"


def main():
    args = argparse.ArgumentParser()
    args.add_argument("--travellers", type=int, default=100, help="people in the arrival rush")
    args.add_argument("--groups", type=int, default=25, help="groups of four at the pickup")
    args.add_argument("--cleanup", action="store_true", help="only cancel the load-test trips and groups")
    a = args.parse_args()

    settings = env()
    if settings.get("DOMAIN") != "localhost":
        sys.exit("Refusing: infra/.env isn't the local test setup (DOMAIN=localhost). Never load-test a real server's data.")
    cleanup()
    if a.cleanup:
        print("Load-test trips and groups cancelled.")
        return

    rush = users(RUSH, "Rush", a.travellers)
    chatter = users(CHATTER, "Pickup", a.groups * 4)
    group_of_member = pickup_groups(chatter)
    secret = settings["JWT_SECRET"]
    tokens = {
        "rush": [token(secret, u) for u in rush],
        "chatter": [{"token": token(secret, u), "groupId": g} for u, g in zip(chatter, group_of_member)],
    }
    (ROOT / "load" / "tokens.json").write_text(json.dumps(tokens))
    print(f"{len(rush)} rush travellers, {len(group_of_member) // 4} pickup groups; tokens in load/tokens.json (valid 3 hours)")


if __name__ == "__main__":
    main()
