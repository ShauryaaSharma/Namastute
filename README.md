# Namastute

<!-- 1–2 sentences: the problem and what the app does. -->

## Tech stack
<!-- Bullets: backend, frontend, database, CI. Link to the design doc for details. -->

## Run it locally

Requirements: JDK 21, Node 24 + pnpm, Docker Desktop.

1. Start the database (from the repo root):
   ```bash
   docker compose -f infra/docker-compose.yml up -d
   ```
2. Start the API (from `apps/api`), then open http://localhost:8080/actuator/health:
   ```bash
   ./mvnw spring-boot:run
   ```
3. Start the web app (from `apps/web`), then open http://localhost:3000:
   ```bash
   pnpm install
   pnpm dev
   ```

## Docs
- [Design v0.0.2](Docs%20Text/Design%20v0.0.2.md)
- [Proposed Stash](Docs%20Text/Proposed%20Stash.md)