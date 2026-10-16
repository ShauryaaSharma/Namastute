# Load tests

How many travellers one server can take, and what slows down first (Learning Notes 66–67). Run by hand, not in CI:
each run takes about 6 minutes and loads the machine. **Local only:** `setup.py` signs tokens with the server's secret,
so it refuses to run unless `infra/.env` has `DOMAIN=localhost`.

| File | What it does |
|---|---|
| `compose.limits.yml` | Caps the local production stack at the t3.small's 2 cores (API 1.4, Postgres 0.6) |
| `setup.py` | Test users (`+91 1000…` and `+91 1001…`, never real numbers), locked pickup groups of four, tokens in `tokens.json` |
| `arrival-rush.js` | `TRAVELLERS` people plan trips at Terminal 3 within 2 minutes, then refresh trip, requests and matches every minute; some ask and accept |
| `pickup-chatter.js` | `GROUPS` groups of four on WebSockets: location every 15 s, chat every 30–40 s, measuring chat delivery time |

## Run (from the repo root, Docker Desktop running)
```bash
docker compose -f infra/docker-compose.prod.yml -f load/compose.limits.yml --env-file infra/.env up -d --build --wait
python load/setup.py --travellers 300 --groups 75
docker run --rm --network namastute-prod_default -v "$(pwd)/load:/scripts" -w /scripts -e TRAVELLERS=300 grafana/k6:1.3.0 run arrival-rush.js
docker run --rm --network namastute-prod_default -v "$(pwd)/load:/scripts" -w /scripts -e GROUPS=75 grafana/k6:1.3.0 run pickup-chatter.js
python load/setup.py --cleanup
```
- k6 talks to the API inside Docker's network (`http://api:8080`), so Caddy isn't measured; it only passes requests through.
- Watch CPU and memory meanwhile: `docker stats`.
- Pass line: p95 under 500 ms for every kind of request, under 1% errors, chat delivered within 500 ms (p95).
- In Git Bash on Windows, put `MSYS_NO_PATHCONV=1` in front of `docker run` and use a `C:/...` path for `-v`.

## Results
See "Load test results" in the main README.
