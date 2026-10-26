# Running Namastute

- **Development:** `docker compose -f infra/docker-compose.yml up -d` starts Postgres and Redis; run the API and web as usual.
- **Production:** `docker-compose.prod.yml` runs everything on one server behind Caddy, under one domain (Learning Notes 54).

⚠️ Production can't send real login codes until an SMS provider is chosen and an `SmsSender` is written for it. Without one, the API refuses to start with the `prod` profile (on purpose).

## Before real users: team checklist
These need a decision, an account or a site visit, not code (Learning Notes 64):
1. **SMS provider + DLT:** choose MSG91 or Firebase and register the sender ID and message template on DLT (required for SMS in India). Then a developer writes the `SmsSender` for it; until then the production API refuses to start.
2. **Pickup points:** the terminal pickup locations in the database are approximate. Someone visits each terminal, notes the real app-cab pickup spot (latitude and longitude), and a developer ships them as a new migration (never by editing `V3__seed_terminals.sql`).
3. **Map search:** OpenStreetMap's Nominatim only allows light use (about 1 search a second). Fine for a small pilot; before growing, choose Ola Maps or Google. Only `apps/web/lib/geocode.ts` changes.
4. **Uptime monitor:** a free monitor (for example UptimeRobot) that opens `https://<your domain>/` every 5 minutes and emails the team when it fails. Without one, nobody knows the site is down.
5. **ID check reviewers (Learning Notes 80):** who reviews, and how fast (the app tells people "usually within a day"). Reviewers should read the checklist on the review page and never approve an unmasked Aadhaar.
6. **Women-only policy:** decide whether IDs showing transgender / third gender count for women-only groups (open question 4 in the design doc). Until then only IDs saying female count; the rule is one line in `UserService.WOMEN_ONLY_USERS`.
7. **Calibrate the face score (Learning Notes 82):** with consenting team members' real IDs and selfies, on a laptop only (never committed or uploaded), note the scores of same-person and different-person pairs. Only then decide whether low scores should reject automatically.
8. **Privacy policy:** it must cover ID photos (what, why, who sees them, deletion within 7 days), matching the consent notice on `/verify` (DPDP Act).

## First-time server setup (AWS, region ap-south-1 Mumbai)
1. **Server:** EC2 **t3.small** (Intel/AMD, 2 GB; the PostGIS image is amd64-only), Ubuntu LTS, 20 GB disk. Attach an **Elastic IP** so the address never changes.
2. **Firewall (security group):** allow **80** and **443** from anywhere, **22** (SSH) only from your own IP. Nothing else: Postgres and Redis are never public.
3. **Domain:** an **A record** for your domain (e.g. `namastute.in`) → the Elastic IP. Wait until `nslookup namastute.in` shows it: Caddy needs that before it can get a certificate.
4. **Docker:** install Docker Engine and the Compose plugin (docs.docker.com/engine/install/ubuntu).
5. **Code and secrets:**
   ```bash
   git clone https://github.com/ShauryaaSharma/Namastute.git && cd Namastute
   cp infra/.env.example infra/.env && chmod 600 infra/.env
   openssl rand -base64 32      # run three times: DB_PASSWORD, JWT_SECRET, OTP_HMAC_SECRET
   # Push notification keys (VAPID): make them once and keep them; new keys break every existing subscription
   openssl ecparam -name prime256v1 -genkey -noout -out vapid.pem
   openssl pkcs8 -topk8 -nocrypt -in vapid.pem -outform DER | basenc --base64url -w0; echo                        # VAPID_PRIVATE_KEY
   openssl ec -in vapid.pem -pubout -outform DER 2>/dev/null | tail -c 65 | basenc --base64url -w0; echo        # VAPID_PUBLIC_KEY
   rm vapid.pem
   nano infra/.env              # paste them all in and set DOMAIN
   ```
6. **ID photos (private S3 bucket, Learning Notes 78).** From a computer with admin AWS access (or AWS CloudShell). Bucket names are global, so pick your own and put it in `S3_BUCKET` in `infra/.env`:
   ```bash
   B=namastute-verification-<something-unique>
   aws s3api create-bucket --bucket $B --region ap-south-1 --create-bucket-configuration LocationConstraint=ap-south-1
   aws s3api put-public-access-block --bucket $B --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
   aws s3api put-bucket-encryption --bucket $B --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
   # The safety net: AWS itself deletes any ID photo older than 7 days, even if our own job stops
   aws s3api put-bucket-lifecycle-configuration --bucket $B --lifecycle-configuration '{"Rules":[{"ID":"delete-id-photos","Status":"Enabled","Filter":{"Prefix":"verification/"},"Expiration":{"Days":7}}]}'
   ```
   Then give the server access without any keys: **IAM → Roles → Create role → AWS service: EC2**, with this one inline policy (put your bucket name in), named e.g. `namastute-api`:
   ```json
   {"Version": "2012-10-17", "Statement": [{"Effect": "Allow", "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
     "Resource": "arn:aws:s3:::<your bucket>/verification/*"}]}
   ```
   Attach it: **EC2 → your instance → Actions → Security → Modify IAM role**. Containers are one network step further from the instance's metadata service than the machine itself, so allow that, or the API can't fetch its credentials:
   ```bash
   aws ec2 modify-instance-metadata-options --instance-id <your instance id> --http-tokens required --http-put-response-hop-limit 2
   ```
7. **Start:**
   ```bash
   docker compose -f infra/docker-compose.prod.yml --env-file infra/.env up -d --build
   ```
   Open `https://<your domain>`.

## Everyday commands (from the repo folder on the server)
```bash
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env ps          # what's running, and healthy?
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env logs -f api  # follow the API's logs
git pull && docker compose -f infra/docker-compose.prod.yml --env-file infra/.env up -d --build   # deploy the latest main
```

## Backups
Nightly database dump, kept for 7 days (add with `crontab -e`):
```
0 3 * * * cd ~/Namastute && docker compose -f infra/docker-compose.prod.yml --env-file infra/.env exec -T postgres pg_dump -U namastute -Fc namastute > ~/backups/namastute-$(date +\%F).dump && find ~/backups -name '*.dump' -mtime +7 -delete
```
(`mkdir ~/backups` first.) Copy dumps off the server too (e.g. to S3); a backup on the same disk dies with the disk.

Restore (replaces the current data; stop the API first so nothing writes meanwhile):
```bash
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env stop api
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env exec -T postgres pg_restore -U namastute -d namastute --clean < ~/backups/namastute-YYYY-MM-DD.dump
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env start api
```

## Admins
Admins get a push notification for every new report (category only, never names), so a safety report never waits unseen, and for every new ID check, which they review in the app: **Profile → Admin → Review ID checks**.
```bash
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env exec -T postgres psql -U namastute -d namastute -c \
  "update users set role = 'admin' where phone = '+91XXXXXXXXXX'"
```
Then, on the admin's phone: log in, open **Edit profile**, tap **Notify me** (on iPhone, add Namastute to the Home Screen first).

## Reviewing reports (until there's an admin panel)
Open reports, newest first (only admins should ever see these: they can contain personal details):
```bash
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env exec -T postgres psql -U namastute -d namastute -c \
  "select r.created_at, r.category, ur.phone as reporter, ud.phone as reported, r.group_id, r.reason
   from reports r join users ur on ur.id = r.reporter_id join users ud on ud.id = r.reported_id
   where r.status = 'open' order by r.created_at desc"
```
After acting on one: `update reports set status = 'actioned' where id = '...'` (or `'dismissed'`). Reports never punish anyone automatically. If someone is in danger, the app tells them to call 112.

## Local test of the production stack
`infra/.env` with `DOMAIN=localhost` and `SPRING_PROFILES_ACTIVE=prod,fake-sms` (OTP codes then appear in `logs api`), then the same `up -d --build`. Caddy serves `https://localhost` with its own local certificate, so the browser warns once.
