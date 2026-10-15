# Running Namastute

- **Development:** `docker compose -f infra/docker-compose.yml up -d` starts Postgres and Redis; run the API and web as usual.
- **Production:** `docker-compose.prod.yml` runs everything on one server behind Caddy, under one domain (Learning Notes 54).

⚠️ Production can't send real login codes until an SMS provider is chosen and an `SmsSender` is written for it. Without one, the API refuses to start with the `prod` profile (on purpose).

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
6. **Start:**
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
