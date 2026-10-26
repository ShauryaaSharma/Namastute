# Putting Namastute on AWS, step by step

A click-by-click guide for the first deployment, one AWS service at a time. It follows the plan in the Learning Notes (chapters 54–55): **one server** runs everything in Docker (the database, Redis, the API, the web app and Caddy for HTTPS), under **one domain**. The short version for people who've done it before is in [README.md](README.md).

Region: **Asia Pacific (Mumbai), `ap-south-1`**, for every step. Check the region menu at the top right of the AWS console each time you open a new service.

---

## 0. Before you start

**You need:**
- the AWS account (root user already protected with MFA, and the `monthly-cost-guard` budget already set);
- a domain name, or about ₹800–1,500 a year to buy one (step 2);
- about 2 hours. Most of it is waiting for downloads and builds.

**About login codes.** People log in with a code sent by email: through Amazon SES once you have a domain (section 15), or through a Gmail account made for the app, free and with no domain (section 16). Until then, run the server in **staging mode**: login codes are written to the server's log instead of sent. Only someone who can read the server's logs (your team) can log in, so the site can be online and safe to show meanwhile. Step 9 shows the one setting for this.

**Rough monthly cost** (on-demand prices, before any credits; check the [AWS pricing calculator](https://calculator.aws) for today's numbers):

| What | About |
|---|---|
| EC2 `t3.small` server, running all month | US$15–20 |
| 30 GB disk (EBS gp3) | US$3 |
| Public IPv4 address (AWS charges for every public IPv4, including Elastic IPs) | US$3.65 |
| S3 (ID photos, backups) | under US$1 |
| **Total** | **about US$22–28** |

---

## 1. Your own admin login (IAM), not the root user

The root user can do anything, including close the account, so it's only for emergencies.

1. Console → **IAM** → **Users** → **Create user**. Name: your name, e.g. `keshav`. Tick **Provide user access to the AWS Management Console**, choose **I want to create an IAM user**, and set a password.
2. **Permissions:** **Attach policies directly** → tick **AdministratorAccess** → **Create user**. Save the sign-in URL it shows (it contains your account number).
3. Sign out, and sign in at that URL as the new user.
4. **IAM** → **Users** → your user → **Security credentials** → **Assign MFA device** → an authenticator app.

From now on, use this user for everything below.

---

## 2. A domain name

Buy one from any registrar (GoDaddy, Namecheap, Hostinger…, or AWS **Route 53** → **Registered domains**), e.g. `namastute.in`.

Don't set anything up yet: in step 5 you'll point it at the server's address, which doesn't exist yet.

---

## 3. The server (EC2)

### 3a. A key pair (how you'll log in to the server)
**EC2** → **Key pairs** → **Create key pair**:
- name `namastute`;
- type **ED25519**;
- format **.pem**.

The file downloads once; keep it safe (never in the repo). On Windows, move it to `C:\Users\<you>\.ssh\namastute.pem`.

### 3b. The firewall (security group)
**EC2** → **Security groups** → **Create security group**:
- **Name:** `namastute-web`. **VPC:** the default one.
- **Inbound rules:**

  | Type | Port | Source | Why |
  |---|---|---|---|
  | SSH | 22 | **My IP** | only your computer can log in |
  | HTTP | 80 | Anywhere-IPv4 | Caddy proves you own the domain, then redirects to HTTPS |
  | HTTPS | 443 | Anywhere-IPv4 | the site itself |

- **Outbound:** leave as it is (all allowed).

Nothing else, ever: the database and Redis are never reachable from the internet. If your home IP changes, edit the SSH rule to **My IP** again.

### 3c. Launch the server
**EC2** → **Instances** → **Launch instances**:
- **Name:** `namastute-prod`.
- **Image (AMI):** **Ubuntu Server 24.04 LTS**, **64-bit (x86)**. Not Arm: the PostGIS database image is built only for Intel/AMD.
- **Instance type:** **t3.small** (2 vCPU, 2 GB).
- **Key pair:** `namastute`.
- **Network settings** → **Select existing security group** → `namastute-web`.
- **Storage:** **30 GiB**, **gp3**. The Docker images and build caches need more than the default 8.
- **Launch instance**.

---

## 4. A fixed address (Elastic IP)

A server's public address changes when it's stopped and started. An Elastic IP stays the same, so the domain can point at it.

**EC2** → **Elastic IPs** → **Allocate Elastic IP address** → **Allocate**. Then select it → **Actions** → **Associate Elastic IP address** → instance `namastute-prod` → **Associate**.

Note the address (e.g. `13.234.x.x`).

---

## 5. Point the domain at the server (DNS)

At your registrar's DNS settings (or **Route 53** → **Hosted zones** → your domain), add one record:

| Type | Name / Host | Value | TTL |
|---|---|---|---|
| A | `@` (the bare domain) | your Elastic IP | 300 |

Don't add `www` yet: Caddy only serves the domain in `DOMAIN` (step 9), and a `www` address would fail its HTTPS check.

Check from your laptop until it shows the Elastic IP. This can take a few minutes, occasionally hours:
```bash
nslookup namastute.in
```
Caddy can only get the HTTPS certificate once this works.

---

## 6. Private storage for ID photos (S3)

Bucket names are global across all of AWS, so add something unique.

The quickest way is **CloudShell**, the `>_` icon at the top of the console, which opens a terminal already logged in to your account. Paste, with your own bucket name:
```bash
B=namastute-verification-<something-unique>
aws s3api create-bucket --bucket $B --region ap-south-1 --create-bucket-configuration LocationConstraint=ap-south-1
aws s3api put-public-access-block --bucket $B --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-encryption --bucket $B --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
# The safety net: AWS itself deletes any ID photo older than 7 days, even if our own clean-up job stops
aws s3api put-bucket-lifecycle-configuration --bucket $B --lifecycle-configuration '{"Rules":[{"ID":"delete-id-photos","Status":"Enabled","Filter":{"Prefix":"verification/"},"Expiration":{"Days":7}}]}'
echo $B
```

Check in **S3** → your bucket:
- **Permissions** → **Block all public access: On**;
- **Management** → the lifecycle rule `delete-id-photos`.

---

## 7. Let the server use the bucket, with no keys (IAM role)

The API never holds AWS keys. Instead the server has a **role**, and AWS hands it short-lived credentials automatically.

1. **IAM** → **Roles** → **Create role**:
   - **AWS service** → **EC2** → **Next**;
   - skip the list of policies → **Next**;
   - name `namastute-api` → **Create role**.
2. Open the role → **Add permissions** → **Create inline policy** → **JSON**, and paste this with your bucket name:
   ```json
   {"Version": "2012-10-17", "Statement": [{"Effect": "Allow", "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
     "Resource": "arn:aws:s3:::<your bucket>/verification/*"}]}
   ```
   Name it `id-photos` → **Create policy**. The server can now only put, read and delete ID photos, in that one folder of that one bucket.
3. Attach the role: **EC2** → **Instances** → `namastute-prod` → **Actions** → **Security** → **Modify IAM role** → `namastute-api` → **Update IAM role**.
4. One more setting, because the API runs inside a container. Containers are one network step further from the server's metadata service (where the credentials come from), so allow two steps:
   - **EC2** → `namastute-prod` → **Actions** → **Instance settings** → **Modify instance metadata options**;
   - **IMDSv2:** **Required**;
   - **Metadata response hop limit:** **2**;
   - **Save**.

---

## 8. Prepare the server

Connect from your laptop (Git Bash or PowerShell), with your Elastic IP:
```bash
ssh -i ~/.ssh/namastute.pem ubuntu@<elastic ip>
```
If it complains the key is "too open" on Windows, run once in PowerShell: `icacls $HOME\.ssh\namastute.pem /inheritance:r /grant:r "$($env:USERNAME):R"`.

Everything below runs **on the server**.

### 8a. Swap space
2 GB of memory is tight while the images build. Swap lets the build borrow disk instead of crashing:
```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h      # "Swap: 2.0Gi"
```

### 8b. Docker
Docker's official repository for Ubuntu (from docs.docker.com/engine/install/ubuntu):
```bash
sudo apt-get update && sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update && sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu
exit
```
SSH in again (so the `docker` group applies), then check:
```bash
docker run --rm hello-world
```

Ubuntu installs security updates by itself (`unattended-upgrades`). Reboot now and then when it asks (`sudo reboot`); every container restarts automatically.

---

## 9. The code and its secrets

```bash
git clone https://github.com/ShauryaaSharma/Namastute.git && cd Namastute
cp infra/.env.example infra/.env && chmod 600 infra/.env
openssl rand -base64 32      # run three times: DB_PASSWORD, JWT_SECRET, OTP_HMAC_SECRET
# Push notification keys (VAPID): make them ONCE and keep them; new keys break every existing subscription
openssl ecparam -name prime256v1 -genkey -noout -out vapid.pem
openssl pkcs8 -topk8 -nocrypt -in vapid.pem -outform DER | basenc --base64url -w0; echo                    # VAPID_PRIVATE_KEY
openssl ec -in vapid.pem -pubout -outform DER 2>/dev/null | tail -c 65 | basenc --base64url -w0; echo    # VAPID_PUBLIC_KEY
rm vapid.pem
nano infra/.env
```
In `nano`, fill in:
- `DOMAIN`: your domain, no `https://`;
- the three secrets;
- the two VAPID keys;
- `S3_BUCKET`: the name from step 6;
- `SPRING_PROFILES_ACTIVE`: `prod,fake-sms` for staging (login codes go to the log). Change it to `prod` once SES is set up (section 15).
- `EMAIL_FROM`: leave the example for now; section 15 sets the real one.

Save with Ctrl+O, Enter, Ctrl+X.

Check that nothing is left empty. It prints the name of any empty setting, never a value, so no output means you're done. (Don't count lines ending in `=` instead: the secrets and the VAPID public key end in `=` themselves.)
```bash
grep -E '^[A-Z_]+=$' infra/.env
```

**Keep a copy of `infra/.env`** somewhere safe off the server, such as a password manager. Losing `DB_PASSWORD` locks you out of your own database backups, and losing the VAPID keys breaks every push subscription.

---

## 10. Start everything

```bash
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env up -d --build
```
The first build takes about 10–20 minutes on this server. It downloads Java and Node packages, and the face and text models, checking each model file's fingerprint. Watch it settle:
```bash
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env ps       # all "running", the API "healthy"
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env logs -f caddy   # look for "certificate obtained successfully"; Ctrl+C to stop watching
```
If the build stops with **exit code 137**, it ran out of memory: check that the swap from step 8a is on (`free -h`) and run the same `up` command again.

---

## 11. First checks

1. Open `https://<your domain>`: the landing page, with a padlock (a real HTTPS certificate).
2. **Open the app** → enter your email address → read the code from the log:
   ```bash
   docker compose -f infra/docker-compose.prod.yml --env-file infra/.env logs api | grep "Your Namastute code" | tail -1
   ```
3. Make yourself an admin (needed to review ID checks):
   ```bash
   docker compose -f infra/docker-compose.prod.yml --env-file infra/.env exec -T postgres psql -U namastute -d namastute -c \
     "update users set role = 'admin' where email = 'you@example.com'"
   ```
4. Try an ID check from a phone, then review it under **Profile → Admin → Review ID checks**. Then in **S3** → your bucket, the photos should be gone once you decide. This proves the IAM role from step 7 works.

---

## 12. Backups (S3 again)

A nightly database dump, copied off the server. A backup on the same disk dies with the disk.

1. A second bucket. In **CloudShell**:
   ```bash
   K=namastute-backups-<something-unique>
   aws s3api create-bucket --bucket $K --region ap-south-1 --create-bucket-configuration LocationConstraint=ap-south-1
   aws s3api put-public-access-block --bucket $K --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
   aws s3api put-bucket-lifecycle-configuration --bucket $K --lifecycle-configuration '{"Rules":[{"ID":"keep-30-days","Status":"Enabled","Filter":{"Prefix":""},"Expiration":{"Days":30}}]}'
   ```
2. Allow the server to *add* backups only, not read or delete them. **IAM** → **Roles** → `namastute-api` → **Add permissions** → **Create inline policy** → JSON, named `backups`:
   ```json
   {"Version": "2012-10-17", "Statement": [{"Effect": "Allow", "Action": "s3:PutObject", "Resource": "arn:aws:s3:::<backup bucket>/*"}]}
   ```
3. On the server:
   ```bash
   sudo snap install aws-cli --classic
   mkdir -p ~/backups
   crontab -e
   ```
   Add this one line, with your backup bucket. Every night at 03:00 server time (UTC) it dumps, uploads, and keeps 7 local copies. `aws` is written with its full path because scheduled jobs don't search `/snap/bin`:
   ```
   0 3 * * * cd ~/Namastute && docker compose -f infra/docker-compose.prod.yml --env-file infra/.env exec -T postgres pg_dump -U namastute -Fc namastute > ~/backups/namastute-$(date +\%F).dump && /snap/bin/aws s3 cp ~/backups/namastute-$(date +\%F).dump s3://<backup bucket>/ && find ~/backups -name '*.dump' -mtime +7 -delete
   ```
4. The next day, check the file appears in the bucket. How to restore is in [README.md](README.md) under "Backups".

---

## 13. Know when it's down (uptime monitor)

AWS isn't needed for this. Sign up at a free monitor (for example UptimeRobot) and add an HTTPS monitor for `https://<your domain>/`, every 5 minutes, alerting the team's email.

---

## 14. Everyday life

- **Deploy the latest main** (on the server, in `~/Namastute`):
  ```bash
  git pull && docker compose -f infra/docker-compose.prod.yml --env-file infra/.env up -d --build
  ```
- **See what's running, and follow the API's log:** the commands in [README.md](README.md) under "Everyday commands".
- **Going live with real login emails:** section 15 (SES, needs a domain) or section 16 (a Gmail account, no domain).
- **Costs:** check **Billing** → **Bills** after the first week. The budget alert emails you before US$50.

---

## 15. Email login with Amazon SES (once you have a domain)

Until this is done the server stays in staging mode (`prod,fake-sms`), with login codes in its log. SES only sends from a domain whose DNS you control, so `sslip.io` can't be used here.

**15a. Move the site to your domain.** Do step 5 with the real domain, set `DOMAIN` in `infra/.env`, and deploy (step 14). Everyone logs in again and turns notifications back on: browsers tie both to the address.

**15b. Prove you own the domain (SES).** Region **Mumbai**:
1. **Amazon SES** → **Configuration** → **Identities** → **Create identity** → **Domain**, and type your domain.
2. Keep **Easy DKIM** (RSA 2048) → **Create identity**.
3. SES shows three **CNAME** records. Add them at your registrar. If the domain is in Route 53, SES offers to add them for you.
4. Wait until the identity says **Verified** (minutes, sometimes a few hours).

**15c. Tell mail servers what to do with fakes (DMARC).** At your registrar, add:

| Type | Name | Value |
|---|---|---|
| TXT | `_dmarc` | `v=DMARC1; p=none;` |

DKIM from 15b plus this record is what keeps login codes out of spam. `p=none` only asks for reports; tighten it later.

**15d. Let the server send (IAM).** **IAM** → **Roles** → `namastute-api` → **Add permissions** → **Create inline policy** → JSON, named `email`, with your 12-digit account number (top right of the console):
```json
{"Version": "2012-10-17", "Statement": [{"Effect": "Allow", "Action": "ses:SendEmail", "Resource": "arn:aws:ses:ap-south-1:<account number>:identity/*"}]}
```
`identity/*` covers your domain, and, while SES is in its sandbox, the verified addresses it may send to.

**15e. Test while still in the sandbox.** A new SES account can only send to addresses verified one by one:
1. **Identities** → **Create identity** → **Email address** → your own address → click the link in the email AWS sends.
2. In `infra/.env` on the server:
   ```text
   EMAIL_FROM=Namastute <login@yourdomain>
   SPRING_PROFILES_ACTIVE=prod
   ```
3. Deploy (step 14). Log in with that address: the code arrives by email now, not in the log.
4. In Gmail, **⋮** → **Show original** should say **DKIM: PASS** and **DMARC: PASS**.

**15f. Ask to leave the sandbox.** **SES** → **Account dashboard** → **Request production access**:
- **Mail type:** Transactional.
- **Website URL:** your domain.
- **Use case**, in your own words, for example: "One-time login codes for Namastute, a cab-sharing app at Delhi airport. An email is sent only when a person asks to log in on our website, at most 3 an hour per address. No marketing email. Bounces and complaints are handled by SES's suppression list."

AWS usually answers within a day. After that, any address can log in.

**After switching:** accounts made earlier by phone number stay in the database, but the website logs in by email, so log in by email and make that account admin again (step 11). Phone login only returns with an SMS provider and DLT registration.

---

## 16. Email login through a Gmail account (no domain needed)

Free, and running in about 15 minutes, but Gmail allows only about 500 emails a day, so the app sends at most 400 codes a day (Learning Notes 90). Move to SES (section 15) once you have a domain and real traffic.

**16a. A Gmail account only for the app.** Never your personal one: the app password below opens the whole mailbox, and Google can lock an account that sends automated mail.
1. Go to **accounts.google.com/signup** and create an account. Name: **Namastute**. Address: for example `namastute.app@gmail.com`.
2. Use the team's phone number and email for recovery.
3. Keep its password in the team's password manager.

**16b. Turn on 2-Step Verification.** App passwords only exist once it's on. Signed in as the new account: **myaccount.google.com** → **Security** → **2-Step Verification** → follow the steps.

**16c. Create an app password.**
1. Open **myaccount.google.com/apppasswords**.
2. Name it `Namastute server` → **Create**.
3. Google shows **16 letters** in four groups. Copy them **without the spaces**. They're only shown once.

If the page says the setting isn't available, 2-Step Verification isn't on yet.

**16d. Deploy the code that can send through Gmail (step 14),** then edit the settings on the server:
```bash
nano infra/.env
```
Set or add these lines, with your account's address and app password:
```text
EMAIL_VIA=smtp
EMAIL_FROM=Namastute <namastute.app@gmail.com>
SMTP_USERNAME=namastute.app@gmail.com
SMTP_PASSWORD=abcdefghijklmnop
SPRING_PROFILES_ACTIVE=prod
```
`EMAIL_FROM` must be the same Gmail address: Gmail replaces any other "from" address. Save, then apply the settings. Only settings changed, so there's no rebuild:
```bash
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env up -d
```

**16e. Test it.** Log in on the site with your own email. The code now arrives **by email** (the first one may land in Spam: mark it "Not spam"), and no longer appears in the log.

If the site says "We couldn't send the code", the reason is in the log:
```bash
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env logs api | grep -i "SMTP didn't send" | tail -3
```
"Authentication failed" means the address or app password is wrong (make a new app password and paste it again).

**Good to know:**
- **The daily cap:** after 400 codes in a day, people see "Too many people are logging in right now. Try again later." That stops Gmail from blocking the account for a day.
- **The account's Sent folder keeps every code email.** They expire after 5 minutes, so that's harmless, but treat the account as a team secret.
- **If the app password leaks,** delete it on the same page (16c) and make a new one; the account's real password doesn't change.
- **If Google locks the account,** sign in to it in a browser and follow Google's prompts. Meanwhile switch the server back to staging (`SPRING_PROFILES_ACTIVE=prod,fake-sms`, then the same `up -d`).

---

## What this setup doesn't do yet
- **One server, no automatic failover:** if it dies, restore from a backup onto a new one. That's fine for a pilot (why one server: Learning Notes 54); a second server needs a shared chat broker first (README "Known limits").
- **Deploys are by hand** (`git pull` and `up --build`), not automatic from GitHub.
- **No `www.` address.** Add it later as a second name in the Caddyfile plus a DNS record.
