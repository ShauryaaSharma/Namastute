# Namastute — Learning Notes

My revision notes from building Namastute. Organised by topic, not by date. Each section: the idea, why it exists, and where it shows up in this project.

Last updated: 2026-10-16 (ratings after the ride: concepts and code walkthrough).

---

## Contents

1. [Architecture: monolith vs microservices](#1-architecture-monolith-vs-microservices)
2. [Java and Spring foundations](#2-java-and-spring-foundations)
3. [Spring Boot](#3-spring-boot)
4. [Maven](#4-maven)
5. [The generated Spring project](#5-the-generated-spring-project)
6. [Databases: Postgres, PostGIS, SQL](#6-databases-postgres-postgis-sql)
7. [Docker and Docker Compose](#7-docker-and-docker-compose)
8. [Connecting Spring to the database: JDBC, JPA, Hibernate](#8-connecting-spring-to-the-database-jdbc-jpa-hibernate)
9. [Flyway: database migrations](#9-flyway-database-migrations)
10. [Testing and Spring Modulith](#10-testing-and-spring-modulith)
11. [Frontend: TypeScript, Node, pnpm, React, Next.js](#11-frontend-typescript-node-pnpm-react-nextjs)
12. [Git and GitHub workflow](#12-git-and-github-workflow)
13. [CI with GitHub Actions](#13-ci-with-github-actions)
14. [Cross-platform gotchas: permissions and line endings](#14-cross-platform-gotchas-permissions-and-line-endings)
15. [AWS: account security and cost control](#15-aws-account-security-and-cost-control)
16. [Hosting and infrastructure decisions](#16-hosting-and-infrastructure-decisions)
17. [Terminal basics](#17-terminal-basics)
18. [HTTP and REST APIs](#18-http-and-rest-apis)
19. [Authentication and phone OTP](#19-authentication-and-phone-otp)
20. [Hashing, HMAC and keeping OTPs safe](#20-hashing-hmac-and-keeping-otps-safe)
21. [Redis](#21-redis)
22. [Rate limiting](#22-rate-limiting)
23. [Code walkthrough: the OTP service](#23-code-walkthrough-the-otp-service)
24. [Login tokens: JWT, refresh tokens, cookies](#24-login-tokens-jwt-refresh-tokens-cookies)
25. [Spring Security](#25-spring-security)
26. [JPA entities and Spring Data repositories](#26-jpa-entities-and-spring-data-repositories)
27. [Code walkthrough: tokens, security and users](#27-code-walkthrough-tokens-security-and-users)
28. [Browsers, origins and CORS](#28-browsers-origins-and-cors)
29. [React and Next.js in the browser](#29-react-and-nextjs-in-the-browser)
30. [Code walkthrough: CORS and the login screen](#30-code-walkthrough-cors-and-the-login-screen)
31. [Updating data: PATCH, dirty checking, Unicode](#31-updating-data-patch-dirty-checking-unicode)
32. [Code walkthrough: the profile](#32-code-walkthrough-the-profile)
33. [Places, maps and geocoding](#33-places-maps-and-geocoding)
34. [Time zones, enums and trip rules](#34-time-zones-enums-and-trip-rules)
35. [Code walkthrough: trips](#35-code-walkthrough-trips)
36. [Matching: two-stage search and spatial indexes](#36-matching-two-stage-search-and-spatial-indexes)
37. [Matching: detours, pure functions, privacy, N+1](#37-matching-detours-pure-functions-privacy-n1)
38. [Code walkthrough: matching](#38-code-walkthrough-matching)
39. [Groups: state machines and consent](#39-groups-state-machines-and-consent)
40. [Concurrency: the last-seat race](#40-concurrency-the-last-seat-race)
41. [Code walkthrough: groups (slice 2a)](#41-code-walkthrough-groups-slice-2a)
42. [Groups in matches: routes with several drops](#42-groups-in-matches-routes-with-several-drops)
43. [Module dependencies: cycles and dependency inversion](#43-module-dependencies-cycles-and-dependency-inversion)
44. [Code walkthrough: groups in matches (slice 2b)](#44-code-walkthrough-groups-in-matches-slice-2b)
45. [Real-time: WebSocket and STOMP](#45-real-time-websocket-and-stomp)
46. [Chat: security, saving and history](#46-chat-security-saving-and-history)
47. [Code walkthrough: group chat (slice 3)](#47-code-walkthrough-group-chat-slice-3)
48. [Background jobs: sweeps, idempotency, time](#48-background-jobs-sweeps-idempotency-time)
49. [Code walkthrough: expiry jobs](#49-code-walkthrough-expiry-jobs)
50. [Money: fare split and UPI links](#50-money-fare-split-and-upi-links)
51. [Code walkthrough: fare split (and a pool-starvation bug)](#51-code-walkthrough-fare-split-and-a-pool-starvation-bug)
52. [API contracts: OpenAPI and generated types](#52-api-contracts-openapi-and-generated-types)
53. [Code walkthrough: OpenAPI and generated types](#53-code-walkthrough-openapi-and-generated-types)
54. [Production: containers, config, HTTPS](#54-production-containers-config-https)
55. [Code walkthrough: the production build](#55-code-walkthrough-the-production-build)
56. [Safety: report and block](#56-safety-report-and-block)
57. [Code walkthrough: report and block](#57-code-walkthrough-report-and-block)
58. [Push notifications](#58-push-notifications)
59. [Code walkthrough: push notifications](#59-code-walkthrough-push-notifications)
60. [Live location at the pickup](#60-live-location-at-the-pickup)
61. [Code walkthrough: live location](#61-code-walkthrough-live-location)
62. [Ratings after the ride](#62-ratings-after-the-ride)
63. [Code walkthrough: ratings](#63-code-walkthrough-ratings)
64. [Command cheat sheet](#64-command-cheat-sheet)
65. [Glossary](#65-glossary)
66. [Interview questions to practise](#66-interview-questions-to-practise)

---

## 1. Architecture: monolith vs microservices

**Monolith:** the whole backend is one application. Deployed once, runs as one process, usually one database. One part calls another with a normal function call.

**Microservices:** the backend is split into many small applications (auth service, matching service, chat service…), each deployed separately, usually with its own database. They talk over the network (HTTP, gRPC, message queues).

| | Monolith | Microservices |
|---|---|---|
| Deploy | One app | Many apps, many pipelines |
| Calls between parts | Function call: fast, can't fail on the network | Network call: slower, can time out or fail |
| Debugging | One log, one stack trace | A bug can span services; needs tracing |
| Scaling | Scale the whole app | Scale only the busy service |
| Best for | Small teams, early products | Large teams, each owning a service |

**Modular monolith (what we chose):** one deployable app, but the code is split into strict modules (`auth`, `trips`, `groups`, `chat`…). Each module owns its tables and only talks to others through public interfaces. Deployed like a monolith, organised like microservices.

**Why it fits Namastute:**
- Microservices mainly solve *team* problems (dozens of engineers stepping on each other). With 2 devs, that problem doesn't exist.
- Their costs (network failures, many deploys, distributed debugging) are real from day one.
- One server handles our load; nothing needs scaling separately yet.
- Clean module boundaries keep the option to split a module out later.

**Interview version:** "I chose a modular monolith because microservices solve team-scale and independent-scaling problems we don't have, and their operational cost is real from day one. Clean module boundaries, enforced by a test, keep the option to split later."

---

## 2. Java and Spring foundations

### 2.1 Classes and objects
- A **class** is a blueprint: "a Group has an id, a meeting time, members, and can lock itself".
- An **object** is one real thing built from it while the program runs: *this* group, id 42, meeting at 10:30. Created with `new Group(...)`.

### 2.2 Dependencies
Objects rarely work alone. `GroupService` needs a `GroupRepository` (reads/writes groups in the DB) and a `NotificationService` (sends pushes). Those are its **dependencies**.

### 2.3 Wiring by hand, and why it hurts
```java
DataSource db = new HikariDataSource(config);
GroupRepository groups = new GroupRepository(db);
NotificationService notifications = new NotificationService(new PushSender(keys));
GroupService groupService = new GroupService(groups, notifications);
// ...dozens more, in the right order
```
Problems:
1. **Wiring grows** with every class, and order matters.
2. **Swapping is painful:** tests want a fake `PushSender`; you'd rewrite the wiring per test.
3. **Sharing:** you want exactly one connection pool shared by all; you must pass it around carefully.

### 2.4 Inversion of Control (IoC)
- **Control** = who decides when objects are created and connected.
- Normally *your code* does (`new` everywhere). **IoC flips it:** a framework creates and connects objects; your classes only declare what they need.
- The part of the framework that holds those objects is a **container**. In Spring it's the **ApplicationContext**.
- An object managed by Spring is a **bean**.

### 2.5 Dependency Injection (DI)
The container **hands an object its dependencies from outside**, usually through the **constructor**:
```java
@Service
public class GroupService {
    private final GroupRepository groups;
    private final NotificationService notifications;

    public GroupService(GroupRepository groups, NotificationService notifications) {
        this.groups = groups;
        this.notifications = notifications;
    }
}
```
- `@Service` is an **annotation**: a tag starting with `@` that adds information without changing the code. Spring reads it at startup.
- Our convention: **constructor injection only** (no `@Autowired` on fields), so dependencies are explicit and the class is easy to test.

**What Spring does at startup:**
1. **Scan** `com.namastute` and its sub-packages for `@Service`, `@Repository`, `@RestController`…
2. **Plan:** read constructors to map who needs whom.
3. **Create** in dependency order.
4. **Inject** each object into the constructors that asked for it.
5. **Keep** them: by default one shared instance each (**singleton**).

**How it fixes the problems:** add a class = add a tag; tests can swap in fakes; sharing is automatic.

**In our project:** `ApiApplication.java` has `@SpringBootApplication`, which turns on scanning from `com.namastute`. That's why the package name mattered: Spring only scans that package and below.

---

## 3. Spring Boot

Plain Spring still needs to be *told* how to build infrastructure beans: the **connection pool** (reusable DB connections, because opening one per request is slow), the **web server**, the **JSON converter**, etc. Spring Boot removes that work with three ideas:

### 3.1 Auto-configuration
- The **classpath** = all libraries (jars) available to the app at runtime.
- At startup Boot runs many rules with **conditions**:
  - "PostgreSQL driver on the classpath **and** `spring.datasource.url` set → create a connection pool."
  - "Spring Web MVC on the classpath → start a web server on 8080."
  - "You defined your own bean of this type → back off and use yours."
- So: **adding a dependency turns a feature on; `application.yaml` adjusts it.**
- We saw this live: with JPA + Flyway added but no database configured, the app would refuse to start, because auto-config tried to connect.

### 3.2 Starters
One dependency that brings a tested set of libraries. `spring-boot-starter-data-jpa` = Hibernate + connection pool + transactions, at compatible versions.

### 3.3 Embedded web server
- A **web server** listens on a **port** for HTTP requests and sends responses. A port is a numbered door on a computer (8080, 5432…); each program listens on its own.
- Old way: install Tomcat separately, copy your app into it. Boot way: Tomcat runs **inside** your app; you run one jar. That's why we chose **Jar** packaging.

### 3.4 Spring Boot 4 specifics (we use 4.1.1)
- Built on **Spring Framework 7** and **Jakarta EE 11**.
- **Smaller, focused starters:** `spring-boot-starter-webmvc` (was `-web` in Boot 3), `spring-boot-starter-flyway`, plus a matching `-test` starter per module. Smaller jars, faster startup.
- Most tutorials online are still Boot 3. If something from a tutorial fails, **check the version first**.

### 3.5 Actuator
Adds operational endpoints. `/actuator/health` answers "is the app alive and is the database reachable?". Used by Docker, CI, load balancers and Kubernetes. Ours returned `{"status":"UP"}`.

---

## 4. Maven

### 4.1 What "building" means
1. **Compile** `.java` (text) → `.class` (**bytecode**, run by the **JVM**, the Java Virtual Machine).
2. **Fetch libraries** (jars = zip files of `.class` files) your code uses.
3. **Test.**
4. **Package** your classes + libraries into one runnable `.jar`.

### 4.2 Maven's pieces
- **`pom.xml`** (Project Object Model): project name, dependencies, plugins.
- **Maven Central:** the public online library of jars. Downloads are cached in `C:\Users\Lenovo\.m2\repository`.
- **Transitive dependencies:** need A, and A needs B and C → Maven fetches all three. ~7 lines in our pom → ~100 jars.
- **Lifecycle phases:** `validate → compile → test → package → verify → install`. Running one runs all before it, so you can't package untested code.
- **Plugins** add commands, e.g. `spring-boot:run` from the Spring Boot plugin.

### 4.3 The Maven wrapper (`mvnw`)
A script committed to the repo that downloads the exact Maven version in `.mvn/wrapper/`. Everyone (other dev, CI) builds identically with nothing to install.
- `mvnw` = Linux/Mac/CI shell script. `mvnw.cmd` = Windows version.

### 4.4 Inside our `pom.xml`
- **Why no version numbers on dependencies:** the `<parent>` (`spring-boot-starter-parent`) includes a **BOM (Bill of Materials)**, a list of versions tested together. `spring-modulith-bom` in `<dependencyManagement>` does the same for Modulith. Prevents version clashes.
- **Dependency scopes:**
  - *(none)* = compile: needed to compile and run.
  - `runtime`: needed to run, not to compile (e.g. the PostgreSQL driver; our code only talks to JDBC).
  - `test`: only for tests; never shipped.
- **Project version `0.0.1-SNAPSHOT`:** *our* version. **SNAPSHOT** = in development, not released. Spring Boot's version is in `<parent>`.
- **We trimmed it:** Initializr added 7 Modulith dependencies; we kept only `spring-modulith-starter-core` and `spring-modulith-starter-test`. Rule: **add a dependency when a feature needs it**, so you always know why it's there.

---

## 5. The generated Spring project

### 5.1 start.spring.io settings we chose
| Setting | Value | Why |
|---|---|---|
| Project | Maven | Design decision |
| Spring Boot | 4.1.1 | Newest *stable*; SNAPSHOT/M/RC are unfinished |
| Group | `com.namastute` | Reverse-domain naming avoids package clashes |
| Artifact | `api` | Jar name; lives in `apps/api` |
| Package | `com.namastute` | Initializr appends the artifact by default (`com.namastute.api`); we removed it so modules are `com.namastute.trips`, not `com.namastute.api.trips` |
| Packaging | Jar | Embedded Tomcat |
| Config | YAML | Nested config reads better than long repeated prefixes |
| Java | 21 | LTS (long-term support), installed locally |

### 5.2 Files
| File | Purpose |
|---|---|
| `pom.xml` | Build definition |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | Maven wrapper |
| `src/main/java/com/namastute/ApiApplication.java` | Entry point (`@SpringBootApplication`) |
| `src/main/resources/application.yaml` | Configuration |
| `src/main/resources/db/migration/` | Flyway SQL migrations |
| `src/test/java/...` | Tests |
| `.gitignore` | Keeps `target/` (build output) out of git |

### 5.3 Our `application.yaml`
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/namastute
    username: namastute
    password: ${DB_PASSWORD:namastute}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```
- **`${DB_PASSWORD:namastute}`** is a **placeholder**: use the **environment variable** `DB_PASSWORD` if set (a named value the OS gives a program at start), else the default. Real secrets never go in git.
- **`open-in-view: false`:** by default Spring keeps a DB session open for the whole web request, hiding slow queries and holding connections longer. Turning it off is recommended practice.
- `ddl-auto: validate`: see §8.

---

## 6. Databases: Postgres, PostGIS, SQL

- A **database server** stores data in **tables** and answers questions written in **SQL**. Postgres listens on port **5432**.
- One Postgres server can hold many **databases**; each database has **schemas** (namespaces for tables). The default schema is `public`.
- **DDL** (Data Definition Language) = SQL that changes structure: `create table`, `alter table`.
- An **extension** adds abilities to the database. **PostGIS** adds geographic types and functions (e.g. "points within 15 km"). Our matching depends on it.
- **`spatial_ref_sys`** is a PostGIS table of **coordinate systems** (rules for turning a point on the round Earth into numbers). Our columns use `geography(point, 4326)`; **4326** = the GPS system (WGS 84).
- The **`tiger`** and **`topology`** schemas came from the Docker image's own startup script (US address lookup, topology tools). We don't use them; they exist only locally.

### psql (Postgres's command-line client)
| Flag | Meaning |
|---|---|
| `-U namastute` | Log in as **U**ser `namastute` |
| `-d namastute` | Connect to **d**atabase `namastute` |
| `-c "..."` | Run one **c**ommand, print, exit |

Interactive meta-commands (start with `\`):
| Command | Does |
|---|---|
| `\l` | List databases |
| `\dt` | List tables |
| `\dx` | List installed extensions |
| `\d tablename` | Describe a table |
| `\q` | Quit |

No password was asked inside the container because the Postgres image trusts connections from inside its own container. Spring connects from outside via port 5432, so it needs the password.

---

## 7. Docker and Docker Compose

### 7.1 Why not install Postgres on Windows?
Different versions between devs, fiddly PostGIS setup, always running, messy to remove.

### 7.2 Core terms
- **Image:** a packaged, read-only snapshot of a program plus everything it needs (small Linux system, libraries, settings). E.g. `postgis/postgis:16-3.5`.
- **Container:** a **running copy** of an image, isolated (own files, network, processes). Start/stop/delete without affecting anything else.
- **Docker Engine:** background program that pulls images and runs containers (inside Docker Desktop on Windows).
- **Registry:** online store of images; **Docker Hub** is the default. Downloading = **pulling**.
- **Container vs VM:** a VM simulates a whole computer with its own OS (heavy, slow). A container shares the host's Linux kernel (Docker Desktop provides one on Windows), so it starts in seconds and uses little memory.
- **Port mapping** `"5432:5432"` = host port : container port. Lets Spring reach Postgres at `localhost:5432`.
- **Volume:** storage kept *outside* the container and attached to it, so data survives container deletion. `down` keeps volumes; `down -v` deletes them.

### 7.3 Docker Compose
- Describe containers in a **YAML** file; start them all with one command; commit it so everyone has the same setup.
- **YAML:** `key: value`; indentation (spaces, never tabs) nests things.
- A **project** = the containers from one compose file. Default project name = the folder the file is in (ours would be `infra`), so we added `name: namastute` at the top.
- **Service name** (`postgres` in the file) vs **container name** (`namastute-postgres-1` = project-service-copy number).
- **Healthcheck:** how Docker checks the service is really ready (`pg_isready`), shown as `(healthy)`.

### 7.4 `docker` vs `docker compose`
- `docker ps` = **every** running container on the machine, by container name/ID.
- `docker compose ps` = **only this project's** containers, by service name, with health.
- `-f infra/docker-compose.yml` = which compose file (default is the current folder).
- `docker compose down` stops **only this project**.

### 7.5 Getting inside a container
- **Shell:** a program that reads and runs typed commands (PowerShell on Windows, **bash** on Linux). Inside a container you use Linux commands (`ls`, `cat`, `pwd`).
- **Terminal (TTY):** the text window showing a shell.
- `docker compose ... exec postgres bash` (Compose sets `-it` for you), or `docker exec -it <name-or-id> bash`.
  - `-i` interactive: keep keyboard input connected. `-t`: allocate a terminal.
- Leave with `exit` or **Ctrl+D**. The container keeps running.

### 7.6 `image:` vs `build:` and the `--build` flag
- `image:` = a ready-made image, **pulled** from a registry (all our services today).
- `build:` = an image built on your machine from a **Dockerfile** (a step-by-step recipe: base image, copy files, start command).
- `up --build` rebuilds `build:` services so code changes get in. Useless for `image:` services; we'll need it for the api's Dockerfile in Phase 2.
- After editing the compose file, plain `up -d` creates new services and recreates only changed ones.
- To update a ready-made image: `docker compose ... pull`, then `up -d` (or change the tag).

---

## 8. Connecting Spring to the database: JDBC, JPA, Hibernate

- **JDBC** (Java Database Connectivity): Java's standard API for talking to any database. Each database provides a **driver** implementing it.
- **JDBC URL:** `jdbc:postgresql://localhost:5432/namastute` = protocol : driver : host : port / database. `localhost` = this computer.
- **HikariCP:** the connection pool Spring uses. Log line `HikariPool-1 - Start completed` = connected.
- **ORM** (Object-Relational Mapping): converts between Java objects and table rows.
- **JPA** (Jakarta Persistence API): Java's standard rules for ORMs. **Hibernate**: the library that implements them.
- **`ddl-auto`:**
  - `update`: Hibernate creates/changes tables from Java classes. Risky: it guesses, never safely renames or removes.
  - `validate` (ours): Hibernate only **checks** tables match the classes and refuses to start if not. Flyway owns the schema.

---

## 9. Flyway: database migrations

**Problem:** code is versioned in git, but the database structure isn't. Changes get forgotten; databases drift; "column does not exist".

**Solution:**
- Every structural change is a **migration**: an SQL file in `src/main/resources/db/migration/`, e.g. `V1__init.sql` (capital V, version, **two** underscores, description).
- At startup Flyway checks **`flyway_schema_history`**, runs **only new files in order**, and records them.
- It stores a **checksum** (fingerprint of each file). If an already-applied file is edited, Flyway **refuses to start**.
- **Golden rule:** never edit an applied migration. Add `V2__...` instead (**forward-only**).
- Result: every database (laptop, CI, production) reaches the same structure just by starting the app.

Our `V1__init.sql` = the full data model from Design v0.0.2 §3.1 (12 tables).

---

## 10. Testing and Spring Modulith

### 10.1 Tests
- An **automated test** runs your code and checks the result; fails loudly when wrong.
- **JUnit:** the standard Java test library. Mark a method `@Test`.
- **Unit test:** one small piece, no DB/server. Milliseconds.
- **Integration test:** pieces together (starts Spring, real DB). Seconds; catches what unit tests can't.
- `ApiApplicationTests.contextLoads()` starts the whole app (beans, DB, Flyway). That's why it needs Postgres running locally, and why CI starts a Postgres service.
- Run all: `.\mvnw.cmd test` (or `verify` in CI).

### 10.2 Spring Modulith
Turns "modular monolith" from a claim into a check.
- Each package directly under `com.namastute` is a **module**.
- Classes directly in a module's package = its **public API**.
- Sub-packages (e.g. `com.namastute.groups.internal`) = **private**.
- `ApplicationModules.of(ApiApplication.class).verify()` fails if a module touches another's private classes, or if modules depend on each other in a **cycle** (A uses B and B uses A).
- Our `ModularityTest` doesn't start Spring; it only reads compiled classes, so it's fast.

---

## 11. Frontend: TypeScript, Node, pnpm, React, Next.js

### 11.1 Frontend vs backend
- **Backend** (Spring Boot): runs on a server, holds data, enforces rules, answers requests.
- **Frontend:** runs in the user's browser: screens, buttons, forms.
- Browsers understand **HTML** (structure), **CSS** (appearance), **JavaScript** (behaviour).

### 11.2 TypeScript
JavaScript + **types**, checked before running. Compiled to plain JS for browsers. Planned benefit: types generated from the Java API, so a renamed backend field breaks the frontend **build**, not the live app.

### 11.3 Node.js
Runs JavaScript outside the browser. Needed because all frontend tools (TS compiler, bundler, Next.js dev server) are JS programs.

### 11.4 pnpm
- Same idea as Maven for JavaScript. Packages come from the **npm registry**.
- `package.json` ≈ `pom.xml` (name, dependencies, **scripts** like `dev`, `build`, `lint`).
- `node_modules/` = installed packages; never committed.
- **Lockfile** `pnpm-lock.yaml` = exact versions of everything; **committed** so everyone installs the same.
- Why pnpm: one shared copy per version on disk (fast, small) and **strict** (can't use undeclared packages).
- **Install scripts** can run code during install (a known attack route), so recent pnpm blocks them unless approved. `pnpm-workspace.yaml` records ours (`sharp`, `unrs-resolver` not built; fallbacks work).

### 11.5 React
UI from **components** (functions returning what to show). Each describes the screen for the current data (**state**); React updates only what changed. Written in **JSX/TSX** (HTML-like tags in JS/TS).
- `className` instead of `class` (`class` is a reserved word in JS).
- `export default function Home()` = the component this file provides.

### 11.6 Next.js (we use 16.4, with React 19.3)
Framework on top of React:
- **File-based routing (App Router):** `app/page.tsx` = `/`; `app/trip/new/page.tsx` = `/trip/new`.
- **Server rendering:** HTML prepared on a server so the first screen loads fast on slow mobile networks.
- **Build tooling:** compiles TS, **bundles** (combines and shrinks files), dev server with **hot reload** (browser updates on save).
- `app/layout.tsx` = the shared frame around every page.
- `pnpm dev` (development, forgiving, port 3000) vs `pnpm build` (production build, type-checks everything; what CI runs).
- `.next/` = build output, never committed.
- `AGENTS.md` = Next.js's note for AI assistants: Next 16 changed a lot; read the bundled docs in `node_modules/next/dist/docs/`.

### 11.7 Tailwind and ESLint
- **Tailwind CSS:** style with utility classes in markup: `p-8` padding, `text-3xl` big text, `font-bold`, `mt-2` margin-top.
- **ESLint:** a **linter**; reads code without running it and flags likely bugs.

---

## 12. Git and GitHub workflow

### 12.1 Basics
- **Commit:** a saved snapshot of files + message in local history.
- **Staging area:** choose what goes into the next commit (`git add`), then save it (`git commit`).
- **Push:** copy commits to GitHub. `git push -u origin <branch>` the first time links local and remote branches; afterwards just `git push`.
- **Habit:** `git status` and `git diff --staged` before every commit. (An empty `ci.yml` once got committed because the editor never saved; this would have shown it.)

### 12.2 Branching: GitHub Flow (Design v0.0.2 §6.1)
- One protected `main`, always deployable. Short-lived branches (2–3 days max).
- Names: `type/short-description`, e.g. `feat/14-trip-request`, `fix/31-group-full-race`, `docs/...`, `chore/...`, `ci/...`, `refactor/...`, `test/...`, `spike/...`.
- No GitFlow (`develop`, `release/*`): overhead for 2 devs.
- **Conventional commits:** `feat:`, `fix:`, `docs:`, `chore:`, `ci:`… PR titles use the same format.

### 12.3 Pull requests and merging
- **PR:** a request to merge a branch into `main`; where review and CI happen.
- **Draft PR:** marked not ready; can't merge but CI runs.
- **Squash merge:** all the branch's commits become **one** new commit on `main` (title = PR title). Keeps history clean.
- **Check the PR title before merging.** GitHub fills it from the branch name (PR #3 merged as `Docs/learning notes`); edit it to `type: description` so `main`'s history stays clean.
- After a squash merge, `git branch -d` refuses to delete the branch (git doesn't see its original commits in `main`); use `-D` once you know it's merged.
- `git fetch --prune` removes local records of deleted remote branches.

### 12.4 Branch protection on `main` (what we set)
PR required (also for admins), 0 approvals until the second dev joins (then 1), old approvals dismissed on new commits, comments must be resolved, linear history, no force pushes or deletion, **required checks `api` + `web`**, **branch must be up to date** before merging.
- **Required check:** the Merge button stays disabled until named checks pass. Names = CI job names. GitHub only offers names it has seen run.
- **Up to date before merging:** two PRs can each pass alone but break together; this forces CI to test the real combination ("Update branch" button).
- Free branch protection is only enforced on **public** repos (or paid plans).

### 12.5 Rewriting history (lesson from the Ubery repo)
- Removing a line from a pushed commit means **rewriting** it (`git commit --amend`) and **force-pushing**, which replaces history on GitHub. Anyone who already pulled must reset.
- GitHub's **contributors sidebar is cached**; it kept showing "claude" from a `Co-Authored-By` trailer even after the rewrite. That's why the project moved to the clean `Namastute` repo.
- Rule now: no co-author trailers or AI attribution in commits or PRs.

---

## 13. CI with GitHub Actions

### 13.1 What CI is
**Continuous Integration:** every change is automatically built and tested on a **clean machine** before it can join `main`.
- Clean machine = only what's in git. Catches "works on my laptop" (cached jars, env vars, uncommitted files).
- Automatic = nobody forgets to run tests.
- With required checks, broken code **can't** be merged.

### 13.2 Parts of GitHub Actions
| Term | Meaning | Ours |
|---|---|---|
| Workflow | YAML file in `.github/workflows/` | `ci.yml` |
| Event/trigger | What starts it | PR opened/updated; push to `main` |
| Job | Steps on one machine; jobs run in parallel | `api`, `web` |
| Runner | Fresh VM for a job, deleted after | `ubuntu-latest` |
| Step | One command or action, in order; a failure stops the job | `./mvnw -B verify` |
| Action | Reusable ready-made step (`uses:`) | `actions/checkout`, `setup-java`, `setup-node`, `pnpm/action-setup` |
| Service container | Docker container beside a job | Postgres for `contextLoads` |

### 13.3 Lines worth remembering
- `./mvnw -B verify`: `verify` = compile + test + package + final checks; `-B` = batch mode, cleaner logs.
- Postgres service uses the **same** DB name/user/password as `application.yaml`, so no config changes.
- `pnpm install --frozen-lockfile`: install exactly the lockfile; fail if `package.json` disagrees.
- `cache: maven` / `cache: pnpm`: reuse downloads between runs (much faster).
- `@v5`, `@v4`: pin action major versions so a new release can't change the build silently.
- A workflow run that fails in **0 seconds** with "workflow file issue" = the YAML is invalid or empty.

---

## 14. Cross-platform gotchas: permissions and line endings

### 14.1 Executable permission
- Linux runs a file as a program only if it has the **executable flag**. Windows has no such flag.
- Extracting `mvnw` on Windows lost it: git recorded `100644` (normal) instead of `100755` (executable), so CI would fail with "Permission denied".
- Fix: `git update-index --chmod=+x apps/api/mvnw`. Check: `git ls-files -s apps/api/mvnw`.

### 14.2 Line endings
- Windows ends lines with **CRLF** (`\r\n`, Carriage Return + Line Feed, from typewriters); Linux/Mac with **LF** (`\n`).
- Mixed endings make git show every line as changed and break Linux scripts (`bad interpreter: /bin/sh^M`).
- Fix: root **`.gitattributes`**:
  ```
  * text=auto eol=lf
  *.cmd text eol=crlf
  *.bat text eol=crlf
  ```
  then `git add --renormalize .` to re-apply to existing files.

---

## 15. AWS: account security and cost control

### 15.1 Root user and MFA
- The **root user** (sign-up email) can do anything, including run up huge bills. A leaked root password without MFA is how student accounts get crypto-mining bills.
- **MFA** (multi-factor authentication) added via `https://console.aws.amazon.com/iam/home#/security_credentials` → Assign MFA device → Authenticator app.
- **TOTP** (Time-based One-Time Password): AWS and the phone share a secret from the QR code; each computes `HMAC(secret, time ÷ 30)` → 6 digits. No communication needed, works offline. AWS asks for two consecutive codes to check the clocks agree.
- Keep a backup (cloud-backed authenticator or a second MFA device). Later: use an IAM user for daily work, root only for billing/emergencies.

### 15.2 AWS Budgets (`monthly-cost-guard`)
- A budget is an **alarm, not a limit**: AWS keeps billing after it fires.
- Cost data updates ~3 times a day, so alerts lag; use several low thresholds.
- **Actual** = real spend crossed a line. **Forecasted** = AWS predicts you'll cross it by month-end (earliest warning).
- **Credits trap:** by default credits are subtracted, so cost shows $0 and alerts never fire until credits run out. We **excluded Charge type: Credit** (now under Scope → Filter specific AWS cost dimensions).
- Ours: $50/month, alerts at 20% ($10), 50% ($25), 100% ($50) actual + 100% forecasted.
- **Unblended costs:** what each usage actually cost at the time; the plain number (others matter for Reserved Instances / Savings Plans).
- Habit: **stop the EC2 instance** after demos.

### 15.3 Regions
- The URL showed `ap-southeast-2` (Sydney). For Delhi users, create servers in **`ap-south-1` (Mumbai)**: lower latency, and data stays in India (helps with the DPDP Act). Billing, IAM and Budgets are global.

---

## 16. Hosting and infrastructure decisions

### 16.1 Where Spring Boot runs: one EC2 instance
- Next.js on **Vercel** (free); Spring Boot + Postgres + Redis on **one EC2 instance** with Docker Compose; **Caddy** in front for HTTPS (automatic Let's Encrypt certificates).
- **⚠️ Changed in 54.6:** the web app now runs on the same EC2 server too, under one domain.
- Why: WebSockets need an always-on server (free platforms that sleep drop connections); load balancers/RDS/Elastic Beanstalk add $15–30/month each; one instance ≈ $18/month ≈ 5 months of credits.
- Details to remember:
  - **t4g = ARM (Graviton):** build `linux/arm64` images in CI, or use t3 (Intel). The `postgis/postgis` image has historically been Intel-only.
  - **Memory:** JVM + Postgres + Redis in 2 GB is tight → cap heap `-Xmx512m`, or t4g.medium (4 GB).
  - **HTTPS needs a domain** pointing at the server; use an **Elastic IP** so the address doesn't change (billed while the instance is stopped).
- Deploy at the start of Phase 2, not before.

### 16.2 Why no Kubernetes (Proposed Stash idea 2)
- Kubernetes manages **many containers across many servers** (restarts, load spreading, rolling deploys, autoscaling).
- We have 1 server, 1 app. EKS costs ~$73/month for the control plane alone; k3s needs RAM we don't have; weeks of config for 2 devs.
- Plan: write manifests and test them on a **local** cluster (`kind`/`minikube`) for $0. Interview line: "Production runs on Docker Compose because one instance is enough; I tested Kubernetes manifests locally for when we scale."

### 16.3 C++ in the project (Proposed Stash idea 1)
- Use each language where it genuinely fits. C++ = an **offline** road-distance engine: build a road graph from OpenStreetMap data, run multithreaded **Dijkstra** between ~500 localities, load the distance matrix into Postgres. Replaces the "straight-line × 1.4" shortcut without a paid routing API.

---

## 17. Terminal basics

- Every terminal has a **current directory** (shown in the prompt).
- **`.\file`** (PowerShell) / **`./file`** (bash) = "this folder's file". PowerShell won't run programs from the current folder unless you say so; "not recognized as the name of a cmdlet" usually means you're in the wrong folder.
- `cd apps\api` = go into a folder; `cd ..` = up one level; `cd ..\..` = up two.
- **Environment variable:** a named value the OS gives programs at start (e.g. `DB_PASSWORD`, `JAVA_HOME`).
- After installing a tool (like the JDK), **open a new terminal**; old ones don't see the updated PATH.

---

## 18. HTTP and REST APIs

### 18.1 Request and response
- Browser (frontend) and Spring (backend) talk using **HTTP**, a **protocol** (agreed message format).
- Always a **request** from the client, then a **response** from the server. The server never speaks first.

### 18.2 Parts of a request
```
POST /api/v1/auth/otp HTTP/1.1          ← method + path
Host: api.namastute.in                  ← headers (extra information)
Content-Type: application/json

{"phone": "+919876543210"}              ← body
```
- **Method** = kind of action: `GET` read (no body), `POST` create/trigger, `PATCH` change part, `DELETE` remove.
- **Path** = which thing. **Headers** = information about the request. **Body** = the data.

### 18.3 Parts of a response
```
HTTP/1.1 429 Too Many Requests          ← status code
Content-Type: application/json

{"error": {"code": "OTP_RATE_LIMITED", "message": "Try again in 12 minutes"}}
```
Our error body always has this shape: a machine-readable `code` the frontend can switch on, and a human-readable `message`.

### 18.4 Status codes
| Family | Meaning | Examples |
|---|---|---|
| 2xx | Success | `200 OK`, `201 Created`, `204 No Content` |
| 4xx | Client's fault | `400` invalid input, `401` not logged in, `403` not allowed, `404` not found, `409` conflict (group full), `429` too many requests |
| 5xx | Server's fault | `500` bug/crash |

### 18.5 JSON, APIs, REST, controllers
- **JSON:** text format for bodies: `{"key": value}`, lists in `[...]`. Spring converts Java objects ↔ JSON automatically.
- **API:** the set of requests a program accepts (ours: Design v0.0.2 §3.5).
- **REST:** URLs name *things* (`/trips/42`), methods say what to do with them.
- **Endpoint:** one method + path, e.g. `POST /api/v1/auth/otp`.
- **Controller:** a `@RestController` class whose methods handle endpoints. Tomcat receives the request → Spring finds the method → JSON body becomes a Java object → result becomes JSON.

### 18.6 HTTP is stateless
Each request stands alone; the server doesn't remember previous ones. So after login, **every request must carry proof of identity** (our JWT). This is why login systems look the way they do.

---

## 19. Authentication and phone OTP

### 19.1 Authentication vs authorization
- **Authentication:** *who are you?*
- **Authorization:** *what may you do?* (e.g. join a women-only group)

### 19.2 Why phone OTP
- **OTP** = One-Time Password: short code, single use, expires fast.
- No passwords to forget/reuse/leak; every traveller has a phone; proves the number is real (trust between strangers).
- Trade-off: each SMS costs money and anyone reading the SMS can log in → needs rate limiting.

### 19.3 The flow
```
Browser                         Spring API                       Redis            SMS provider
  |  POST /auth/otp {phone}         |                               |                    |
  |-------------------------------->|  rate limit OK? ------------->|                    |
  |                                 |  make random 6-digit code     |                    |
  |                                 |  store HMAC of code, 5 min -->|                    |
  |                                 |  send code ------------------------------------->  |  SMS to phone
  |  204 No Content                 |                               |                    |
  |<--------------------------------|                               |                    |
  |  POST /auth/verify {phone,code} |                               |                    |
  |-------------------------------->|  fetch stored HMAC ---------->|                    |
  |                                 |  HMAC(code) == stored?        |                    |
  |                                 |  yes → delete it, find or create user in Postgres  |
  |  200 OK  (tokens)               |                               |                    |
  |<--------------------------------|                               |                    |
```
1. `POST /auth/otp {phone}` → rate-limit check → random 6-digit code → store its **HMAC** in Redis for 5 min → send SMS → `204 No Content`.
2. `POST /auth/verify {phone, code}` → fetch stored HMAC → compare → on match delete it, **find or create** the user in Postgres → return tokens.
- No separate sign-up: the first successful verify creates the user.

### 19.4 Our slices
`feat/otp-api` (request + verify) → `feat/auth-tokens` (JWT, refresh, Spring Security) → `feat/otp-ui` (login screen).

---

## 20. Hashing, HMAC and keeping OTPs safe

### 20.1 Hashing
- A **hash function** turns any input into a fixed-length fingerprint (SHA-256 → 64 hex characters).
- **One-way:** no formula to get the input back. **Deterministic:** same input → same fingerprint, so you can check a guess without storing the original.
- Store the hash, not the code: a leaked Redis shows fingerprints, not usable codes. (Flyway's checksum is also a hash.)

### 20.2 Why a plain hash isn't enough for a 6-digit code
Only 1,000,000 possible codes. An attacker with a stolen fingerprint hashes all of them in under a second and finds the match.

### 20.3 HMAC
- **HMAC** = a hash that mixes in a **secret key** only the server knows: `HMAC(secret, code)`.
- Without the secret, an attacker can't compute fingerprints to compare, so trying all codes is useless.
- The secret lives in an environment variable on the server, never in git or Redis. (AWS MFA's TOTP also uses HMAC with a shared secret.)

### 20.4 All the protections
| Protection | Rule | Stops |
|---|---|---|
| Secure random | `SecureRandom` (cryptographically secure), not `Random` (predictable) | Guessing the next code |
| Expiry (TTL) | 5 minutes | Old codes used later |
| Attempt limit | 5 wrong tries → code deleted | Guessing (5 in 1,000,000) |
| Single use | Deleted on success | Reusing an intercepted code |
| Constant-time compare | `MessageDigest.isEqual`, not `equals` | **Timing attacks** (a normal compare stops at the first difference, leaking information through response time) |
| Never log codes | | Leaks through logs |

---

## 21. Redis

### 21.1 What it is
- A **key-value store**: save a **value** under a **key**, fetch it by key.
- Keeps data in **RAM**, so operations take microseconds (Postgres reads disk and does much more per query).
- Each key can have a **TTL** (time to live); Redis deletes it automatically when it runs out.

### 21.2 What goes in Redis vs Postgres
- OTPs are **temporary** (5 min) and **high-churn** (created, checked, deleted constantly). In Postgres, expired rows would pile up and need a **cleanup job**; Redis's TTL removes them for free.
- Rate-limit counters are hit on **every** request, so speed matters.
- Redis: **temporary, high-churn, fast** data that's fine to lose (OTPs, rate-limit counters, live location). If Redis forgets an OTP, the user requests another.
- Postgres: **permanent** data (users, trips, groups).
- So our Redis service has **no volume** on purpose.

### 21.3 Commands
| Command | Does |
|---|---|
| `SET key value EX 300` | Save with a 300 s TTL |
| `GET key` | Read (`(nil)` if missing/expired) |
| `DEL key` | Delete (returns how many keys were deleted) |
| `TTL key` | Seconds left; `-2` = key doesn't exist, `-1` = no expiry |
| `INCR key` | Add 1 atomically; creates it at 1 if missing |
| `EXPIRE key 900` | Set a TTL on an existing key (returns 1 if applied) |
| `KEYS pattern` | List matching keys; **local only**, it blocks a busy server |
| `PING` | Health check (answers `PONG`) |

Open it with: `docker compose -f infra/docker-compose.yml exec redis redis-cli` (leave with `exit`).

### 21.4 Atomic operations and race conditions
- **Atomic** = happens as one indivisible step. Two simultaneous `INCR`s both count.
- Doing "read 4, add 1, save 5" in separate steps from Java, two requests could both read 4 and both save 5, losing a count. That's a **race condition** (same family as the "last seat in a group" problem).
- **Gotcha:** `INCR` then `EXPIRE` are *two* commands. If the app crashes between them, the counter has no TTL and the phone stays blocked forever. Fix: send both as one atomic unit (a Redis transaction/pipeline or a small Lua script), or check for a missing TTL.

### 21.5 Hands-on lab: the OTP flow by hand (what each line did)

**A. Store an OTP and watch it age**
| Command | Result | Why |
|---|---|---|
| `SET otp:+919876543210 "fakehash123" EX 300` | `OK` | Saves a value under the key, with a 300 s TTL. The key name `otp:<phone>` is a **naming convention**: prefix says *what* it is, then *whose*. |
| `GET otp:+919876543210` | `"fakehash123"` | Reads it back by key. In the app, this is where verify fetches the stored HMAC. |
| `TTL ...` (twice) | `280`, then `266` | Seconds left. It counts down by itself: Redis tracks time per key. |

**B. Automatic expiry**
| Command | Result | Why |
|---|---|---|
| `SET short "bye" EX 10` | `OK` | A key that lives 10 s. |
| `GET short` (at once) | `"bye"` | Still alive. |
| `GET short` (after 10 s) | `(nil)` | Redis **deleted it on its own**; `nil` = nothing there. No cleanup job needed. |
| `TTL short` | `-2` | `-2` = key doesn't exist (`-1` would be "exists, never expires"). |

**C. Fixed-window rate limiter**
| Command | Result | Why |
|---|---|---|
| `INCR rate:otp:phone:...` | `1` | Key didn't exist → created at 0 and incremented to 1. First request of the window. |
| `EXPIRE ... 900` | `1` | Starts the 15-minute window (`1` = TTL applied; `0` if the key were missing). |
| `INCR` ×3 more | `2`, `3`, `4` | Requests 2 and 3 are allowed (≤ 3). **4 > 3 → the app would reply 429.** |
| `TTL ...` | `882` | The window is running; at 0 the counter vanishes and the phone may request again. |

**D. Single use**
| Command | Result | Why |
|---|---|---|
| `DEL otp:+919876543210` | `1` | Number of keys deleted. In the app, a correct code is deleted at once so it can't be reused. |
| `GET otp:...` | `(nil)` | Proves it's gone. |
| `KEYS *` | only `rate:otp:phone:...` | The OTP is gone; the counter stays until its window ends. (`KEYS` is for local exploring only.) |

**Lesson hidden in C:** `INCR` and `EXPIRE` were two separate commands. If the app crashed between them, the counter would never expire and the phone would be blocked forever. In code they must run as one atomic unit (see 21.4).

---

## 22. Rate limiting

### 22.1 Why
Stops someone requesting thousands of OTPs to *someone else's* number (harassment + our SMS bill) and endless code guessing.

### 22.2 Our rules
- Per phone: **3 OTPs / 15 minutes**.
- Per **IP address** (the network address a request comes from): **10 / hour**, to stop cycling through many numbers.
- Resend cooldown: **30 seconds**.

### 22.3 Fixed window counter
```
count = INCR rate:otp:phone:<phone>
if count == 1: EXPIRE rate:otp:phone:<phone> 900
if count > 3:  reject with 429
```
- Simple; the TTL resets the window by itself.
- **Weakness:** bursts at the window edge (3 at 14:59 + 3 at 15:01 = 6 in two minutes). Sliding window or token bucket fix it; fine for protecting SMS spend. A classic interview trade-off.

---

## 23. Code walkthrough: the OTP service

File: `apps/api/src/main/java/com/namastute/auth/OtpService.java`. This is the **service layer** for OTP login: all the rules from topics 19–22, with no HTTP in it. Read it next to the file.

### 23.1 The package and imports
```java
package com.namastute.auth;
```
- The file belongs to the **`auth` module** (topic 10.2). Java requires the package to match the folder path.

```java
import com.namastute.shared.ApiException;
import java.time.Duration;
import java.security.MessageDigest;
import org.springframework.data.redis.core.StringRedisTemplate;
...
```
- An **import** names a class that lives in another package so this file can use it by its short name. Without `import java.time.Duration;`, you'd have to write `java.time.Duration` every time.
- `java.*` / `javax.*` = Java's built-in library. `org.springframework.*` = Spring. `com.namastute.*` = our code.

### 23.2 The class
```java
@Service
class OtpService {
```
- **`@Service`** tells Spring: "create one of these at startup and manage it" (a **bean**, topic 2.4). The controller asks for an `OtpService` in its constructor and Spring hands it this object (**dependency injection**, 2.5).
- **No `public`** → **package-private**: only classes in `com.namastute.auth` can use it. Other modules can't reach into our internals (topic 10.2).

### 23.3 The constants (the rules, in one place)
```java
private static final Duration OTP_TTL = Duration.ofMinutes(5);
private static final int MAX_ATTEMPTS = 5;
private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
private static final int PHONE_LIMIT = 3;
private static final Duration PHONE_WINDOW = Duration.ofMinutes(15);
private static final int IP_LIMIT = 10;
private static final Duration IP_WINDOW = Duration.ofHours(1);
```
- **`private`**: only this class can see it. **`static`**: one copy shared by the whole class, not one per object. **`final`**: can never be reassigned. Together = a **constant**, written in `UPPER_SNAKE_CASE`.
- **`Duration`** = Java's type for an amount of time. `Duration.ofMinutes(5)` can't be confused with "5 seconds" the way a bare number `300` could.
- **`int`** = a whole number.
- Why constants at the top: anyone reviewing the code sees every limit at a glance, and changing a rule is a one-line edit.

### 23.4 The rate-limit counter script (Lua)
```java
private static final RedisScript<Long> HIT = RedisScript.of("""
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
        return count
        """, Long.class);
```
- **The problem it solves:** a counter must never exist without a TTL, or a phone gets blocked forever (topic 21.4).
- **Why not the "SET NX, then INCR" trick from the lesson:** it's two separate commands. In a tiny gap at the *end* of a window, the old key can expire between them, and then `INCR` creates a fresh key **with no TTL**: the same bug, just rarer. Rare bugs in rate limiting still lock real users out, so we close it completely.
- **Lua** is a small programming language Redis can run **inside itself**. Redis runs a whole script as **one atomic step**: no other command can run in the middle, and a crash of *our* app can't split it.
- Line by line, inside the script:
  - `local count = redis.call('INCR', KEYS[1])`: run `INCR` on the key we pass in; store the new value in a variable `count`.
  - `if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end`: if this was the **first** hit (the key was just created), give it a TTL. `PEXPIRE` = `EXPIRE` in **milliseconds**. `ARGV[1]` is the window length we pass in.
  - `return count`: send the count back to Java.
- `KEYS[1]` / `ARGV[1]`: Redis scripts receive **keys** and **arguments** as two separate lists, numbered from 1.
- `"""..."""` is a Java **text block**: a string that spans several lines, so the script stays readable.
- `RedisScript.of(script, Long.class)` wraps the script and says "the result is a `Long`" (a whole number object).
- `RedisScript<Long>`: the `<Long>` is a **generic type parameter**: "a RedisScript that returns a Long". The compiler uses it to check we treat the result as a number.

### 23.5 The fields (what each OtpService object holds)
```java
private final StringRedisTemplate redis;
private final SmsSender sms;
private final SecretKeySpec hmacKey;
private final SecureRandom random = new SecureRandom();
```
- **Fields** are variables that belong to the object and live as long as it does.
- `redis`: Spring's Redis client for string keys and values (topic 21; Spring creates it automatically from the Redis starter).
- `sms`: **the interface**, not a concrete class. Today Spring fills it with `LogSmsSender`; later a real provider, with no change here (**polymorphism**).
- `hmacKey`: our secret, prepared once as a key object for the HMAC algorithm.
- `random`: **`SecureRandom`**, a cryptographically secure random generator. Ordinary `Random` can be predicted from its past outputs.
- `final`: each field is set once in the constructor and never changes. That makes the object safe to share between many simultaneous requests (it's a **singleton** bean, topic 2.5).

### 23.6 The constructor
```java
OtpService(StringRedisTemplate redis, SmsSender sms,
           @Value("${namastute.otp.hmac-secret}") String secret) {
    this.redis = redis;
    this.sms = sms;
    this.hmacKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
}
```
- The **constructor** runs once, when Spring creates the object. Its parameters are what Spring **injects**.
- **`@Value("${namastute.otp.hmac-secret}")`**: read this setting from `application.yaml`, which itself reads the environment variable `OTP_HMAC_SECRET` (or the dev default).
- `this.redis = redis`: `this.redis` is the **field**; `redis` alone is the **parameter**. Copy the parameter into the field.
- `secret.getBytes(StandardCharsets.UTF_8)`: turn the text into **bytes** using **UTF-8**, the standard way of encoding characters as numbers. Crypto works on bytes, not text.
- `new SecretKeySpec(bytes, "HmacSHA256")`: wrap those bytes as a key for the HMAC-SHA256 algorithm. Built once here, reused for every code.

### 23.7 `requestOtp`: sending a code
```java
void requestOtp(String phone, String ip) {
```
- `void`: returns nothing. Success = finishing without an exception; failure = throwing `ApiException`.

**Step 1: the 30-second cooldown**
```java
Boolean cooldownStarted = redis.opsForValue().setIfAbsent(cooldownKey(phone), "1", RESEND_COOLDOWN);
if (!Boolean.TRUE.equals(cooldownStarted)) {
    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN",
            "Wait 30 seconds before requesting another code");
}
```
- `opsForValue()`: the group of commands for plain string values (`SET`, `GET`, …).
- `setIfAbsent(key, "1", 30s)` = Redis `SET key 1 EX 30 NX`: create the key **only if it doesn't exist**. Returns `true` if it created it (no code sent in the last 30 s), `false` if it already existed.
- It's **one atomic command**: two requests at the same instant can't both get `true`.
- `Boolean` (capital B) is an object that could be `null` in rare cases. `Boolean.TRUE.equals(x)` is true only when `x` is exactly `true`, and safely false for `false` **or** `null`. (`!x` on a `null` would crash with a `NullPointerException`.)
- `!` means "not". So: if we did **not** start a new cooldown, someone requested within 30 s → **429**.
- `throw` stops the method immediately; the exception travels up to `ApiExceptionHandler`, which sends `{"error": {"code": "OTP_COOLDOWN", ...}}` with status 429.

**Step 2 and 3: the rate limits**
```java
if (hit("rate:otp:phone:" + phone, PHONE_WINDOW) > PHONE_LIMIT) {
    throw rateLimited();
}
if (hit("rate:otp:ip:" + ip, IP_WINDOW) > IP_LIMIT) {
    throw rateLimited();
}
```
- `"rate:otp:phone:" + phone`: `+` joins strings, building the key `rate:otp:phone:+919876543210`.
- `hit(...)` counts this request and returns the count (see 23.9). More than 3 in the window → reject.
- The IP limit catches one attacker cycling through many phone numbers.
- Order matters: cooldown first, so impatient double-clicks are rejected *without* using up the 3-per-15-minutes allowance.

**Step 4: generate the code**
```java
String code = String.format("%06d", random.nextInt(1_000_000));
```
- `random.nextInt(1_000_000)`: a secure random whole number from 0 to 999,999. (`_` is just a readable separator.)
- `String.format("%06d", n)`: write the number with at least 6 digits, padding with zeros: `42` → `"000042"`. Every code is exactly 6 digits.

**Step 5: store the HMAC, reset attempts**
```java
redis.opsForValue().set(codeKey(phone), hmac(code), OTP_TTL);
redis.delete(triesKey(phone));
```
- `set(key, value, 5 min)` = `SET otp:code:<phone> <hmac> EX 300`. One atomic command: the value and its TTL are saved together.
- We store **`hmac(code)`**, never the code itself (topic 20).
- If the user requests a new code, it **overwrites** the old one, so only the latest code works.
- `delete(triesKey)`: a new code starts with zero wrong attempts.

**Step 6: send the SMS**
```java
sms.send(phone, "Your Namastute code is " + code + ". It expires in 5 minutes.");
```
- Calls whichever `SmsSender` Spring injected. In development, `LogSmsSender` prints it to the console.
- **Why store before sending:** if we sent first and then crashed before storing, the user would hold a code the server never saved, a guaranteed failure. Store first, and the worst case is a stored code whose SMS never arrived; the user simply asks again.

### 23.8 `verify`: checking a code
```java
void verify(String phone, String code) {
```

**Step 1: is there a code at all?**
```java
String stored = redis.opsForValue().get(codeKey(phone));
if (stored == null) {
    throw invalidCode();
}
```
- `get` = Redis `GET`. Returns the stored HMAC, or `null` if there's no key (never requested, already used, or expired).
- We answer **400 `OTP_INVALID`, "Code is wrong or expired"** for all of these on purpose. Saying "no code exists for this number" would tell an attacker something.

**Step 2: count the attempt**
```java
if (hit(triesKey(phone), OTP_TTL) > MAX_ATTEMPTS) {
    redis.delete(List.of(codeKey(phone), triesKey(phone)));
    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_MANY_ATTEMPTS",
            "Too many wrong codes, request a new one");
}
```
- Every attempt is counted **before** comparing, so there's no way to guess without being counted.
- More than 5 attempts → delete both keys (the code is now useless) and answer **429**. Guessing a 6-digit code in 5 tries: 5 in 1,000,000.
- `List.of(a, b)`: a fixed list of two keys; `delete(list)` = Redis `DEL a b` in one command.

**Step 3: compare, safely**
```java
boolean matches = MessageDigest.isEqual(
        hmac(code).getBytes(StandardCharsets.UTF_8),
        stored.getBytes(StandardCharsets.UTF_8));
```
- Compute the HMAC of the code the user typed and compare it to the stored HMAC. We never need the original code back; matching fingerprints are enough (topic 20.1).
- `MessageDigest.isEqual` compares in **constant time** (topic 20.4), so response timing leaks nothing.
- `boolean` (small b) is a plain true/false that can't be `null`.

**Step 4: succeed only once**
```java
// Only the request whose DEL actually removes the code wins, so two simultaneous correct guesses can't both pass
if (!matches || !Boolean.TRUE.equals(redis.delete(codeKey(phone)))) {
    throw invalidCode();
}
redis.delete(triesKey(phone));
```
- `||` means "or", and it's **short-circuit**: if `!matches` is true (wrong code), Java doesn't evaluate the right side at all, so a wrong guess **never deletes** the code.
- If the code matches, we `delete` the code key. `delete` returns `true` **only if this call actually removed the key**.
- **The race it prevents:** two requests arrive at the same instant with the correct code (say an attacker who intercepted the SMS, racing the real user). Both pass steps 1–3. Redis handles commands one at a time, so only **one** `DEL` finds the key and gets `true`; the other gets `false` → `OTP_INVALID`. **The code can only ever be used once.**
- Finally, clean up the attempt counter. Reaching the end of the method without an exception = **verified**.

### 23.9 `hit`: the counter helper
```java
private long hit(String key, Duration window) {
    Long count = redis.execute(HIT, List.of(key), String.valueOf(window.toMillis()));
    if (count == null) {
        throw new IllegalStateException("Redis returned no count for " + key);
    }
    return count;
}
```
- `private`: only used inside this class. Written once, used for all three counters (phone, IP, attempts).
- `redis.execute(script, keys, args...)`: run the Lua script from 23.4. `List.of(key)` becomes `KEYS[1]`; the window in milliseconds (`window.toMillis()`, turned into text with `String.valueOf`) becomes `ARGV[1]`.
- The result is a `Long` object that, in theory, could be `null`. We treat that as a broken setup and stop with a clear message rather than a confusing crash later.
- `IllegalStateException`: Java's standard "something is in a state that should be impossible" exception. Not an `ApiException`, so it becomes a **500**: a server problem, not the user's fault.
- `return count`: Java automatically converts the `Long` object to a plain `long` number (**unboxing**).

### 23.10 `hmac`: the fingerprint
```java
private String hmac(String code) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(hmacKey);
        return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
        throw new IllegalStateException("HmacSHA256 unavailable", e);
    }
}
```
- `Mac.getInstance("HmacSHA256")`: get Java's built-in HMAC-SHA256 calculator. (**MAC** = Message Authentication Code.)
- `mac.init(hmacKey)`: load our secret key into it.
- `mac.doFinal(bytes)`: compute the fingerprint: 32 bytes.
- `HexFormat.of().formatHex(...)`: write those bytes as **hex** text (`0-9`, `a-f`, two characters per byte) → 64 characters, easy to store in Redis.
- `try { ... } catch (GeneralSecurityException e) { ... }`: Java forces us to handle this **checked exception** (one the compiler makes you deal with). It can't actually happen, because every Java installation includes HMAC-SHA256, so we convert it into a clear unchecked exception, keeping the original `e` attached as the **cause** for debugging.

### 23.11 Small helpers
```java
private static String codeKey(String phone) { return "otp:code:" + phone; }
private static String triesKey(String phone) { return "otp:tries:" + phone; }
private static String cooldownKey(String phone) { return "otp:cooldown:" + phone; }
private static ApiException invalidCode() { return new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "Code is wrong or expired"); }
private static ApiException rateLimited() { return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED", "Too many codes requested, try again later"); }
```
- One place builds each key name, so a typo can't make `requestOtp` and `verify` use different keys.
- The two error helpers **return** an exception and the caller **throws** it (`throw invalidCode();`). Same error everywhere, written once.
- `static`: they don't use any fields, so they don't need an object.

### 23.12 The tests (`OtpServiceTest.java`)
- **`@SpringBootTest`**: an **integration test**: starts the whole app with the real Redis and Postgres from Docker.
- **The fake SMS:** a `@TestConfiguration` adds a `CapturingSms` bean marked **`@Primary`** ("when there are two `SmsSender`s, inject this one"). It just remembers the last message, so a test can read the code the way a user reads their phone.
- **`@BeforeEach cleanRedis()`**: runs before every test and deletes that test's keys, so tests don't affect each other.
- **`@Autowired`**: in tests, fields can be injected directly (allowed in tests; in app code we use constructors).
- **What each test proves:**
  | Test | Proves |
  |---|---|
  | `correctCodeWorksOnlyOnce` | Right code passes; reusing it gives `OTP_INVALID` (single use) |
  | `secondRequestWithinCooldownIsRejected` | Two requests within 30 s → `OTP_COOLDOWN` |
  | `fourthRequestInWindowIsRateLimited` | 4th request in 15 min → `OTP_RATE_LIMITED`, and the counter **has a TTL** (the "blocked forever" bug is gone) |
  | `sixthWrongGuessLocksTheCode` | 5 wrong guesses → `OTP_INVALID`; 6th → `OTP_TOO_MANY_ATTEMPTS` and the code is deleted |
  | `storesHmacNotTheCode` | Redis holds a 64-character HMAC that doesn't contain the code |
- **AssertJ** (`assertThat(x).isEqualTo(y)`) reads like a sentence and gives clear failure messages.
- `catchThrowable(() -> ...)` runs the code and **captures** the exception instead of letting it fail the test, so we can check its error code. `() -> ...` is a **lambda**: a small piece of code passed around as a value, run later.
- **CI** now also starts a Redis service, otherwise these tests would fail on GitHub.

---

## 24. Login tokens: JWT, refresh tokens, cookies

### 24.1 Two ways to remember who's logged in
HTTP is stateless (18.6), so after login the browser needs proof it sends with every request.
| | Server sessions | Tokens (ours) |
|---|---|---|
| What the browser holds | A random **session ID** in a cookie | A **signed token** that itself says who you are |
| What the server does per request | **Looks up** the session in storage | **Checks the signature** (maths, no lookup) |
| Log someone out instantly | Easy: delete the record | Hard: a token is valid until it expires |
| Many server instances / WebSockets | Need a shared session store | Work as-is |

- **Cookie:** a small piece of data the server asks the browser to store and **send back automatically** with every request to that site.

### 24.2 JWT (JSON Web Token)
`header.payload.signature`, each part **Base64URL**-encoded (an *encoding* using URL-safe characters, **not** encryption).
- **Header:** the algorithm, e.g. `{"alg":"HS256"}`.
- **Payload = claims:** `sub` (subject: our user id), `iat` (issued at), `exp` (expires), `iss` (issuer: `namastute-api`).
- **Signature:** `HMAC-SHA256(secret, header.payload)`, the same algorithm as our OTPs.
- **Key fact: readable by anyone, changeable by no one.** Never put secrets in a JWT. Editing the payload breaks the signature.
- **HS256 vs RS256:** HS256 = one shared secret signs *and* verifies (right when one service does both, like ours). RS256 = a private key signs, a public key verifies (needed when *other* services must check tokens without being able to create them).

### 24.3 Access token + refresh token
| | Access token | Refresh token |
|---|---|---|
| Form | JWT | 32 random bytes (Base64URL text) |
| Lifetime | 15 minutes | 30 days |
| Sent | Every API request: `Authorization: Bearer <token>` | Only to `/api/v1/auth/refresh` (and `/logout`) |
| Checked by | Signature + `exp`, no database | Lookup in the `sessions` table |
| Revocable | No (expires soon) | Yes |
- **Bearer** = "whoever carries this is trusted", so tokens must be protected.
- Short access tokens limit the damage of a stolen one; the refresh token avoids asking for an OTP every 15 minutes.

### 24.4 Rotation and reuse detection
- **Rotation:** each refresh token works **once**; using it returns a new one and revokes the old.
- **Reuse detection:** if an already-used refresh token comes back, someone holds a copy (thief or victim, we can't tell), so **all of that user's sessions are revoked**.

### 24.5 Hash, but not HMAC
`sessions.token_hash` = **plain SHA-256** of the refresh token. Safe because the token has 2²⁵⁶ (about 10⁷⁷) possibilities, impossible to try. The 6-digit OTP needed HMAC because 10⁶ can be tried in a second. **The size of the input space decides.**

### 24.6 Where the browser keeps tokens
- **XSS (Cross-Site Scripting):** attacker's JavaScript runs on our page and reads whatever JS can read.
- **CSRF (Cross-Site Request Forgery):** another site makes the user's browser send a request to our API; the browser attaches our cookies automatically.
- **Access token:** a JavaScript variable in memory (not `localStorage`, which any XSS could read forever).
- **Refresh token:** a cookie with:
  - `HttpOnly`: JavaScript can't read it (stops XSS theft).
  - `Secure`: only sent over HTTPS (browsers treat `http://localhost` as secure too).
  - `SameSite=Lax`: not attached to POSTs started by other sites (stops CSRF).
  - `Path=/api/v1/auth`: only sent to auth endpoints, not every request.
  - `Max-Age`: how long the browser keeps it; `0` deletes it.

---

## 25. Spring Security

### 25.1 Filters
- A **filter** inspects a request **before** it reaches a controller; it can add information or reject the request.
- Spring Security is a **chain of filters**. The **bearer token filter** reads `Authorization: Bearer ...`, asks the **`JwtDecoder`** to check signature, `exp` and issuer, and if valid records the **authenticated principal** (who's calling). Missing/invalid on a protected path → **401**, controller never runs.
- **401** = who are you? (no/bad token). **403** = known, but not allowed.

### 25.2 Our rules (`SecurityFilterChain`)
- Public: `POST /api/v1/auth/otp|verify|refresh|logout`, `/actuator/health`, `/error`.
- Everything else: must be authenticated.
- **Stateless:** never create server sessions.
- **CSRF protection off:** not needed because the API reads the `Authorization` header (browsers never add it on their own) and the refresh cookie is `SameSite=Lax`.
- `/error` is public because Spring forwards failed requests there; if it were protected, a real server error would wrongly show as 401.

### 25.3 The starter
`spring-boot-starter-security-oauth2-resource-server` (Boot 4 name; the old `spring-boot-starter-oauth2-resource-server` is **deprecated**). "**OAuth2 resource server**" = an API that accepts bearer tokens. Includes **Nimbus**, the library that creates/checks JWTs.

---

## 26. JPA entities and Spring Data repositories

- **Entity:** a class marked `@Entity` that maps to a table (`User` ↔ `users`); each field ↔ a column. Spring Boot's naming rule turns `userId` into `user_id`.
- We map **only the columns the code uses**. On insert, the others get their **database defaults** (`verification = 'unverified'`, `role = 'user'`, `created_at = now()`).
- `ddl-auto: validate` (8) checks every mapped field matches a real column at startup, so a typo fails fast.
- **Repository:** an interface extending `JpaRepository<Entity, IdType>`. Spring **generates the implementation**: `save`, `findById`, `delete`… plus **derived queries** built from method names: `findByPhone(String phone)` → `select ... where phone = ?`.
- **`@Query`** writes a query by hand in **JPQL** (SQL-like, but uses entity and field names: `Session s`, `s.revokedAt`). **`@Modifying`** marks it as an update/delete, and it returns how many rows changed.
- **`Optional<T>`:** a box that holds a value or is empty. Forces the caller to handle "not found" (`.map(...)`, `.orElseGet(...)`, `.orElseThrow()`), instead of a surprise `null`.
- **Transaction:** a group of database changes that either **all happen or none do**. `@Transactional` starts one around a method.
- **Forward-only migration in practice:** `users.name` was `NOT NULL`, but OTP login only knows the phone. V1 can't be edited, so **`V2__users_name_optional.sql`** runs `alter table users alter column name drop not null;`.

---

## 27. Code walkthrough: tokens, security and users

Files in `apps/api/src/main/java/com/namastute/`. Read each part next to its file.

### 27.1 `users/User.java`: the entity
```java
@Entity
@Table(name = "users")
class User {
```
- `@Entity`: "Hibernate, this class is stored in a table." `@Table(name = "users")`: which table (the class name `User` alone would map to a table called `user`).

```java
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
```
- `@Id`: this field is the **primary key** (the column that uniquely identifies a row).
- `@GeneratedValue(strategy = GenerationType.UUID)`: Hibernate creates a random **UUID** (a 128-bit unique id like `3f2a...`) when saving a new user.

```java
    private String phone;
    private String name;
```
- Plain fields map to columns with the same name. `name` can be `null` now (V2).

```java
    protected User() { }
    User(String phone) { this.phone = phone; }
```
- **JPA requires a no-argument constructor**: when loading a row, Hibernate creates an empty object, then fills its fields. `protected` keeps our own code from using it by mistake.
- The second constructor is the one *we* use to create a new user from a phone number.

```java
    UUID getId() { return id; }
    ...
```
- **Getters**: read-only access to the fields. No setters: nothing in this slice needs to change a user.

### 27.2 `users/UserRepository.java`
```java
interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhone(String phone);
}
```
- `JpaRepository<User, UUID>`: a repository for `User` entities whose id type is `UUID`. We get `save`, `findById`, `saveAndFlush` and more for free.
- `findByPhone`: Spring reads the name (`find` + `By` + `Phone`) and generates `select * from users where phone = ?`. Returns `Optional` because there may be no such user.
- Package-private: only the `users` module may touch the table directly.

### 27.3 `users/UserService.java`: the module's public API
```java
@Service
public class UserService {
```
- `public`, unlike almost everything else: this is the **one door** other modules (like `auth`) use into `users`. Spring Modulith's test checks they don't go around it.

```java
    public UUID findOrCreateByPhone(String phone) {
        return users.findByPhone(phone)
                .map(User::getId)
                .orElseGet(() -> create(phone));
    }
```
- `findByPhone(phone)`: an `Optional<User>`.
- `.map(User::getId)`: if a user is inside the box, replace it with its id. `User::getId` is a **method reference**: shorthand for `user -> user.getId()`.
- `.orElseGet(() -> create(phone))`: if the box is empty, run `create(phone)` and use its result. (`orElseGet` only runs the lambda when needed; `orElse` would always run it.)

```java
    private UUID create(String phone) {
        try {
            return users.saveAndFlush(new User(phone)).getId();
        } catch (DataIntegrityViolationException e) {
            return users.findByPhone(phone).orElseThrow().getId();
        }
    }
```
- `saveAndFlush`: insert the row **now** (flush = send the SQL immediately), so any database error happens here, inside the `try`.
- **The race it handles:** two first-time logins for the same new number at the same instant. Both find no user, both insert. `phone` is `unique` in the table, so the second insert fails with `DataIntegrityViolationException` (Spring's exception for a broken database constraint). We then simply load the user the other request just created. Same idea as `DEL` returning `true` (23.8): **let the database decide the winner**.

```java
    Profile profile(UUID id) { ... }
    record Profile(UUID id, String phone, String name) {}
```
- Package-private: only `MeController` needs it. `Profile` is a DTO record, turned into the JSON response. We return a record rather than the entity, so internal fields never leak into the API by accident.

### 27.4 `users/MeController.java`: the first protected endpoint
```java
@GetMapping("/api/v1/me")
UserService.Profile me(@AuthenticationPrincipal Jwt jwt) {
    return users.profile(UUID.fromString(jwt.getSubject()));
}
```
- `@GetMapping`: handles `GET /api/v1/me`. It's not in the public list, so Spring Security has **already** checked the token before this runs.
- `@AuthenticationPrincipal Jwt jwt`: Spring hands us the **decoded, verified** token of the caller.
- `jwt.getSubject()`: the `sub` claim, our user id as text. `UUID.fromString` turns it back into a `UUID`.
- Returning a record (not a `ResponseEntity`) means "200 OK with this as JSON".

### 27.5 `V2__users_name_optional.sql`
```sql
alter table users alter column name drop not null;
```
- Removes the `NOT NULL` rule from `users.name`. A new file, because V1 already ran everywhere (9).

### 27.6 `auth/Session.java` and `auth/SessionRepository.java`
- `Session` is an entity for the `sessions` table: `id`, `userId` (→ `user_id`), `tokenHash`, `expiresAt`, `revokedAt`.
- `Instant`: Java's type for an exact moment in time (stored as `timestamptz`, a timestamp with time zone).
- `userId` is a plain `UUID`, not a link to the `User` entity: `auth` doesn't reach into `users`' internals.

```java
@Modifying
@Transactional
@Query("update Session s set s.revokedAt = :now where s.id = :id and s.revokedAt is null")
int revokeIfActive(UUID id, Instant now);
```
- A hand-written JPQL update. `:now` and `:id` are **named parameters**, filled from the method arguments (never glued into the text, so no SQL injection).
- `and s.revokedAt is null`: only revoke it if it's **still active**.
- Returns `int`, the number of rows changed: **1** = "this call revoked it", **0** = "someone already had". This is the race-safe check used by rotation (27.7).
- `@Transactional`: update queries must run inside a transaction; each call is its own small one.
- `revokeAllForUser`: the same, for every active session of a user (reuse detection, logout everywhere).

### 27.7 `auth/RefreshTokens.java`
```java
static final Duration LIFETIME = Duration.ofDays(30);
```
- Package-visible (no `private`) so the controller can use the same value for the cookie's `Max-Age`.

```java
record Rotated(UUID userId, String token) {}
```
- A small record to **return two values** from `rotate`: whose session it was, and the new token.

**`create(userId)`: a new login session**
```java
byte[] bytes = new byte[32];
random.nextBytes(bytes);
String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
sessions.save(new Session(userId, sha256(token), Instant.now().plus(LIFETIME)));
return token;
```
- `new byte[32]`: an array of 32 bytes = 256 bits. `random.nextBytes(bytes)`: fill it from `SecureRandom`.
- `Base64.getUrlEncoder().withoutPadding()`: turn the bytes into cookie-safe text (`A–Z a–z 0–9 - _`, no trailing `=`).
- Save only `sha256(token)` with an expiry 30 days from now. The **raw token** goes back to the user once and is never stored.

**`rotate(token)`: use a refresh token**
```java
Session session = sessions.findByTokenHash(sha256(token)).orElseThrow(RefreshTokens::invalid);
```
- Hash what the browser sent and look it up. Unknown → throw `invalid()` (401).

```java
if (session.getRevokedAt() != null || sessions.revokeIfActive(session.getId(), now) == 0) {
    sessions.revokeAllForUser(session.getUserId(), now);
    throw invalid();
}
```
- **Left side:** the token was already used or logged out, so this is a **replay**.
- **Right side** (only checked if the left is false, short-circuit `||`): try to revoke it. If that returns **0**, another request revoked it a moment earlier: two requests used the same token, also a replay.
- Either way: **revoke every session for this user** (everyone logs in again, the thief's copy dies) and answer 401.
- No `@Transactional` around this method **on purpose**: if it were one transaction, the `throw` would **roll back** the "revoke all", undoing the protection. Each repository call commits on its own.

```java
if (session.getExpiresAt().isBefore(now)) {
    throw invalid();
}
return new Rotated(session.getUserId(), create(session.getUserId()));
```
- Older than 30 days → 401. Otherwise issue a brand-new refresh token for the same user.

**`revoke(token)`: logout**
- Find the session and revoke it if present. `ifPresent(s -> ...)` runs the lambda only when the `Optional` has a value. Unknown tokens are silently ignored: logout always "succeeds".

**`sha256(token)`**
- `MessageDigest.getInstance("SHA-256").digest(bytes)`: plain SHA-256 (no secret; see 24.5), written as 64 hex characters.

### 27.8 `auth/AccessTokens.java`: issuing JWTs
```java
JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(ISSUER)
        .subject(userId.toString())
        .issuedAt(now)
        .expiresAt(now.plus(LIFETIME))
        .build();
```
- A **builder** collects values step by step, then `build()` creates the finished, unchangeable object. It reads clearly when there are many fields.
- The claims: issuer `namastute-api`, subject = user id (only the id; the payload is public), issued now, expires in 15 minutes.

```java
JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
```
- **JWS** (JSON Web Signature) = a *signed* JWT. The header says "signed with HS256".
- `encoder.encode(...)` signs it with our secret; `getTokenValue()` gives the `xxx.yyy.zzz` text.

### 27.9 `auth/SecurityConfig.java`
```java
@Configuration
class SecurityConfig {
```
- `@Configuration`: a class whose `@Bean` methods **create beans** for Spring. Used when we need to build an object ourselves (from a library) rather than mark our own class `@Service`.

```java
this.jwtKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
```
- The JWT secret from `namastute.jwt.secret` (env `JWT_SECRET`), as an HMAC key. HS256 needs **at least 32 bytes**; Nimbus refuses shorter keys at startup.

```java
@Bean
SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/otp", ...).permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
            .build();
}
```
- `HttpSecurity http`: Spring hands us a configuration builder for the filter chain.
- `.csrf(...disable)`: see 25.2. `AbstractHttpConfigurer::disable` is a method reference meaning "call `disable()` on it".
- `.sessionManagement(... STATELESS)`: never create server sessions.
- `.authorizeHttpRequests(...)`: the rules, checked **top to bottom**, first match wins. `permitAll()` = anyone; `.anyRequest().authenticated()` = everything else needs a valid token.
- `.oauth2ResourceServer(o -> o.jwt(...))`: turn on the bearer token filter using our `JwtDecoder` bean. `Customizer.withDefaults()` = "default settings".
- `throws Exception`: the builder methods declare checked exceptions; we let them propagate (a broken security config should stop the app).

```java
@Bean
JwtEncoder jwtEncoder() {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
}
```
- The object `AccessTokens` uses to **sign** tokens. `ImmutableSecret` wraps our key in the form Nimbus expects.

```java
@Bean
JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(AccessTokens.ISSUER));
    return decoder;
}
```
- The object the security filter uses to **check** tokens: same key, HS256 only (a token claiming another algorithm is rejected).
- `createDefaultWithIssuer(...)`: the default checks (signature, `exp`, not-before) **plus** "`iss` must be `namastute-api`".

### 27.10 `auth/AuthController.java` (renamed from `OtpController`)
New since 23:
```java
record TokenResponse(String accessToken, long expiresIn) {}
```
- The JSON body after login/refresh: `{"accessToken": "...", "expiresIn": 900}` (seconds), so the frontend knows when to refresh.

```java
@PostMapping("/verify")
ResponseEntity<TokenResponse> verify(@Valid @RequestBody VerifyRequest req) {
    otp.verify(req.phone(), req.code());
    UUID userId = users.findOrCreateByPhone(req.phone());
    return tokens(userId, refreshTokens.create(userId));
}
```
- Check the code (throws on failure) → find or create the user → start a session → return tokens. Each line is one step of the flow; the logic lives in the services.

```java
@PostMapping("/refresh")
ResponseEntity<TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
```
- `@CookieValue`: read the `refresh_token` cookie the browser sent. `required = false` → `null` if missing, so we can answer our own 401 JSON instead of Spring's generic 400.
- `rotate(token)` → new tokens for that user.

```java
@PostMapping("/logout")
... refreshTokens.revoke(token);
... .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
```
- Revoke the session, and send the same cookie with an empty value and `Max-Age=0`, which tells the browser to **delete** it. Answers 204.

```java
private ResponseEntity<TokenResponse> tokens(UUID userId, String refreshToken) {
    return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, RefreshTokens.LIFETIME).toString())
            .body(new TokenResponse(accessTokens.issue(userId), AccessTokens.LIFETIME.toSeconds()));
}
```
- One helper for verify and refresh: **200**, a `Set-Cookie` header carrying the refresh token, and the access token in the body.
- `Set-Cookie` is the response header that asks the browser to store a cookie.

```java
private static ResponseCookie refreshCookie(String value, Duration maxAge) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
            .httpOnly(true).secure(true).sameSite("Lax").path("/api/v1/auth").maxAge(maxAge)
            .build();
}
```
- Spring's cookie builder; each flag is explained in 24.6.

### 27.11 The tests
- **`CapturingSms`** (now its own test file, shared by both test classes): the fake SMS sender, plus a nested `@TestConfiguration` that tests bring in with `@Import(CapturingSms.Config.class)`.
- **`AuthFlowTest`**: `@AutoConfigureMockMvc` gives a **`MockMvc`**, which sends fake HTTP requests through the **whole** stack (filters, security, controllers) without opening a real port.
  - `mvc.perform(post(...).contentType(...).content(...))` = send a request; `.andExpect(status().isOk())` = check the status; `jsonPath("$.phone")` = check a field of the JSON response (`$` = the root).
  - `JsonPath.read(body, "$.accessToken")` = pull a value out of JSON text.
  - `.cookie(...)` sends a cookie; `getCookie(...)` reads one from a `Set-Cookie` response.
  | Test | Proves |
  |---|---|
  | `accessTokenOpensProtectedEndpoint` | Token → `/me` 200 with the right phone; no token or a fake token → 401 |
  | `sameUserOnEveryLogin` | Logging in twice gives the same user id (find, not create) |
  | `refreshRotatesAndReuseLogsOutEverywhere` | Refresh returns a new, different cookie; replaying the old one → 401 **and** the new one stops working |
  | `logoutRevokesRefreshToken` | Logout deletes the cookie (`Max-Age=0`) and the token no longer refreshes |
  | `refreshCookieIsLockedDown` | The cookie is `HttpOnly`, `Secure`, `Path=/api/v1/auth`, `SameSite=Lax` |
- `throws Exception` on tests: `perform` declares checked exceptions; in a test, letting them fail the test is exactly right.

---

## 28. Browsers, origins and CORS

### 28.1 Origin
**Origin = scheme + host + port.** `http://localhost:3000` (web) and `http://localhost:8080` (API) are **different origins**: change any one part and it's different.

### 28.2 Same-Origin Policy
- Browser rule: JavaScript from one origin may **send** requests to another origin but may **not read the response** unless that origin allows it.
- Why: otherwise a malicious site open in another tab could call your bank's API (the browser attaches your bank cookies) and read your balance.
- Enforced by the **browser** only. Postman and curl ignore it, which is why Postman worked before CORS existed.

### 28.3 CORS (Cross-Origin Resource Sharing)
- The server's permission slip, sent as response headers:
  - `Access-Control-Allow-Origin: http://localhost:3000`: this origin may read responses.
  - `Access-Control-Allow-Credentials: true`: and may send/receive cookies.
- **Preflight:** for non-simple requests (JSON bodies, `Authorization` header), the browser first sends an automatic **`OPTIONS`** request asking permission; the real request only follows if the answer is yes. Visible in the browser's Network tab.
- **Exactly one allowed origin, never `*`.** Browsers forbid `*` with credentials, and it would undo 28.2. Ours comes from `WEB_ORIGIN` (default `http://localhost:3000`).
- A disallowed origin's preflight gets **403** with no allow headers (tested in `CorsTest`).

### 28.4 Origin vs site, and a production decision
- **Site** ≈ registrable domain, ignoring port and subdomains.
  - `localhost:3000` ↔ `localhost:8080`: same site.
  - `app.namastute.in` ↔ `api.namastute.in`: same site.
  - `namastute.vercel.app` ↔ `api.namastute.in`: **different sites**.
- `SameSite=Lax` cookies are only sent on **same-site** requests. If the web app stayed on `*.vercel.app` while the API is on our domain, the refresh cookie would never be sent, and users would be logged out every 15 minutes.
- **Decision for deployment (Phase 2):** host the web app and API under the **same domain** (e.g. `app.namastute.in` + `api.namastute.in`); Vercel allows custom domains on the free plan.

---

## 29. React and Next.js in the browser

### 29.1 Server vs Client Components
- Next.js App Router components are **Server Components** by default: rendered on the server, sent as HTML.
- Anything interactive (typing, clicks, remembering state, browser APIs) must be a **Client Component**: the file starts with the **directive** `"use client";` (a special instruction string at the very top, before imports).
- `"use client"` marks a **boundary**: that file and what it imports run in the browser. Our home page is a Server Component that renders the client `SessionBox` inside it.

### 29.2 State, hooks, events
- **State:** data a component remembers between redraws. `const [code, setCode] = useState("")`. Calling the setter makes React **redraw** with the new value.
- **Hook:** a React function starting with `use` that gives a component abilities (`useState`, `useEffect`, `useRouter`). Only in Client Components, and only at the top level of the component (not inside `if`s or loops).
- **Effect (`useEffect`)**: code that runs **after** the component appears on screen, for things outside React (timers, network calls). The **dependency array** (`[resendIn]`) says when to run it again; `[]` = only once. A returned function is the **cleanup** (e.g. cancel a timer).
- **Events:** `onSubmit={checkCode}`, `onChange={(e) => ...}`, `onClick`. `e.preventDefault()` stops the browser's default form behaviour (reloading the page).
- **Controlled input:** `value={code}` + `onChange` that calls `setCode`: React state is the single source of truth for what's in the box.

### 29.3 `fetch`, Promises, `async`/`await`
- `fetch(url, options)`: the browser's function for HTTP requests.
- It returns a **Promise**: a value that arrives later. `await` pauses an **`async`** function until it arrives.
- `.then(fn)` / `.catch(fn)` / `.finally(fn)`: the non-`await` way to say "when it arrives / if it fails / either way".
- `credentials: "include"`: send and accept cookies on a cross-origin request (the browser side of `Allow-Credentials`).

### 29.4 Staying logged in
- Access token in **memory only**; reloading wipes it.
- **Silent refresh:** on page load, `POST /auth/refresh` with the cookie → new access token, user never notices.
- **On 401 mid-use:** refresh once, retry the request once.
- **The race we hit (and solved):** React's development mode **runs every effect twice** on purpose (React **StrictMode**, to expose effects that aren't safe to repeat), and users may open several tabs at once. Two simultaneous refreshes with the **same** cookie look exactly like token theft to our reuse detection (24.4), which would **log the user out everywhere**. So `refreshSession` allows **one refresh at a time**:
  - in one tab: a shared in-flight Promise (a second caller gets the same Promise);
  - across tabs: the **Web Locks API** (`navigator.locks.request`): a browser-wide lock, so the second tab waits, then uses the already-rotated cookie.
  - Verified in the browser: page load sends exactly **one** refresh.

### 29.5 Environment variables in Next.js
- `NEXT_PUBLIC_API_URL` is readable in browser code. Next.js **copies its value into the JavaScript at build time** (changing it later needs a rebuild).
- **Never put secrets in `NEXT_PUBLIC_` variables**: they end up in files anyone can download.
- `.env.local` holds your values (git-ignored). `.env.example` documents which variables exist (committed; the `.gitignore` needed a `!.env.example` exception because `.env*` matched it).

---

## 30. Code walkthrough: CORS and the login screen

### 30.1 API: `SecurityConfig.java` (CORS part)
```java
.cors(Customizer.withDefaults())
```
- Turns on Spring Security's CORS handling, using our `CorsConfigurationSource` bean. It runs **before** the login check, so preflight `OPTIONS` requests (which never carry a token) get answered instead of rejected with 401.

```java
@Bean
CorsConfigurationSource corsConfigurationSource(@Value("${namastute.web-origin}") String webOrigin) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(webOrigin));
    cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    cors.setAllowCredentials(true);
    cors.setMaxAge(Duration.ofHours(1));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return source;
}
```
- `@Value("${namastute.web-origin}")`: from `application.yaml` → env `WEB_ORIGIN`, default `http://localhost:3000`.
- `setAllowedOrigins(List.of(webOrigin))`: exactly one origin.
- `setAllowedMethods` / `setAllowedHeaders`: only what our API uses.
- `setAllowCredentials(true)`: allows the refresh cookie.
- `setMaxAge(1 hour)`: the browser may remember the preflight answer for an hour, saving an `OPTIONS` round trip per request.
- `registerCorsConfiguration("/api/**", cors)`: apply to every path under `/api/` (`**` = any depth).

**`CorsTest.java`**: sends a fake preflight from `http://localhost:3000` (expects 200 + allow headers) and from `https://evil.example` (expects 403, no allow header).

### 30.2 `lib/api.ts`: the only file that talks to the API
```ts
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
```
- Read the env variable (29.5). `??` = "if the left side is `null`/`undefined`, use the right side".

```ts
export type Profile = { id: string; phone: string; name: string | null };
type TokenResponse = { accessToken: string; expiresIn: number };
```
- **TypeScript types** describing the JSON shapes our API returns. `string | null` = "a string or null" (a **union type**). `export` lets other files use it.

```ts
export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message);
  }
}
```
- Our own error type carrying the HTTP status and our error `code` (`OTP_INVALID`…). `readonly` in the constructor both declares and sets a read-only field.

```ts
let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;
```
- **Module-level variables**: they live as long as the page. `accessToken` is the token in memory; `refreshing` is the refresh currently in progress, if any.

```ts
function send(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (init.body) headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  return fetch(`${API_URL}${path}`, { ...init, headers, credentials: "include" });
}
```
- One low-level request: adds the JSON header when there's a body, the `Bearer` token when we have one, and always `credentials: "include"`.
- `` `Bearer ${accessToken}` `` is a **template literal**: backticks let you insert values with `${...}`.
- `{ ...init, headers, credentials }`: the **spread** `...` copies all of `init`'s properties into a new object, then we add/override `headers` and `credentials`.

```ts
async function toError(res: Response): Promise<ApiError> {
  const body = await res.json().catch(() => null);
  return new ApiError(res.status, body?.error?.code ?? `HTTP_${res.status}`,
    body?.error?.message ?? "Something went wrong. Please try again.");
}
```
- Turn a failed response into an `ApiError`. Spring Security's own 401s have an **empty body**, so `res.json()` fails; `.catch(() => null)` turns that into `null`.
- `body?.error?.code`: **optional chaining**: if `body` or `body.error` is missing, the whole expression is `undefined` instead of crashing.

```ts
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res = await send(path, init);
  if (res.status === 401 && !path.startsWith("/api/v1/auth/") && (await refreshSession())) {
    res = await send(path, init);
  }
  if (!res.ok) throw await toError(res);
  return (res.status === 204 ? undefined : await res.json()) as T;
}
```
- `<T>` is a **generic**: the caller says what type the response is (`request<Profile>(...)`).
- On a 401 from a normal endpoint, refresh once and retry once (29.4). Not for `/auth/` endpoints: a wrong OTP is also a client error, and we must not loop.
- `res.ok`: true for 2xx statuses. Otherwise throw our error.
- `204` has no body, so return `undefined` instead of parsing JSON.

```ts
export function refreshSession(): Promise<boolean> {
  const current = refreshing ?? refreshWithLock().finally(() => (refreshing = null));
  refreshing = current;
  return current;
}
```
- If a refresh is already running, **reuse its Promise**; otherwise start one, and clear `refreshing` when it finishes (success or failure). This is the in-tab half of 29.4.

```ts
async function refreshWithLock(): Promise<boolean> {
  return navigator.locks.request("namastute-refresh", async () => {
    const res = await fetch(`${API_URL}/api/v1/auth/refresh`, { method: "POST", credentials: "include" });
    accessToken = res.ok ? ((await res.json()) as TokenResponse).accessToken : null;
    return res.ok;
  });
}
```
- `navigator.locks.request(name, fn)`: run `fn` while holding a **browser-wide lock** called `namastute-refresh`; another tab asking for the same lock waits. The cross-tab half of 29.4.
- Sends the cookie, stores the new access token (or clears it on failure), returns `true`/`false`.
- `cond ? a : b` = the **ternary operator**: `a` if `cond` is true, else `b`.

```ts
export async function verifyOtp(phone: string, code: string): Promise<void> {
  const tokens = await request<TokenResponse>("/api/v1/auth/verify", { method: "POST", body: JSON.stringify({ phone, code }) });
  accessToken = tokens.accessToken;
}
```
- `JSON.stringify({ phone, code })` turns an object into JSON text. `{ phone, code }` is shorthand for `{ phone: phone, code: code }`.
- On success, keep the access token in memory. The refresh cookie is stored by the browser automatically.

```ts
export async function restoreSession(): Promise<Profile | null> {
  if (!accessToken && !(await refreshSession())) return null;
  return getMe();
}
```
- On page load: if there's no token in memory and refresh fails → logged out (`null`). Otherwise load the profile.

```ts
export async function logout(): Promise<void> {
  try { await request<void>("/api/v1/auth/logout", { method: "POST" }); }
  finally { accessToken = null; }
}
```
- `try { } finally { }`: forget the token **even if** the logout request fails, so the user is never left half-logged-in on screen.

### 30.3 `app/login/page.tsx`: the login screen
```tsx
"use client";
```
- Client Component: it has state and handles typing and clicks (29.1). The file `app/login/page.tsx` automatically becomes the `/login` URL.

```tsx
const router = useRouter();
const [step, setStep] = useState<"phone" | "code">("phone");
const [digits, setDigits] = useState("");
const [code, setCode] = useState("");
const [error, setError] = useState("");
const [busy, setBusy] = useState(false);
const [resendIn, setResendIn] = useState(0);
```
- `useRouter()` (from `next/navigation`): lets code change the page (`router.replace("/")`).
- One piece of state per thing the screen must remember. `"phone" | "code"` = a union of two exact strings, so `step` can only ever be one of those.

```tsx
const phone = `+91${digits}`;
```
- The user types only 10 digits; the full E.164 number is **derived** from state on every redraw, not stored separately (no chance of the two disagreeing).

```tsx
useEffect(() => {
  if (resendIn <= 0) return;
  const timer = setTimeout(() => setResendIn(resendIn - 1), 1000);
  return () => clearTimeout(timer);
}, [resendIn]);
```
- The resend countdown. Each time `resendIn` changes, wait one second and subtract one, until 0. The cleanup cancels the pending timer if the component disappears. 30 seconds matches the API's cooldown, so the button never invites a request the API would reject.

```tsx
async function run(action: () => Promise<void>) {
  setBusy(true); setError("");
  try { await action(); }
  catch (e) { setError(e instanceof ApiError ? e.message : "Can't reach Namastute. ..."); }
  finally { setBusy(false); }
}
```
- One helper for both buttons: show "busy", clear old errors, run the action, show its error message if it fails, always stop "busy".
- `e instanceof ApiError`: our API answered with an error → show its message. Anything else (no network, server down) → a generic message.

```tsx
function sendCode(e?: FormEvent) {
  e?.preventDefault();
  if (!/^[6-9]\d{9}$/.test(digits)) { setError("Enter a 10-digit Indian mobile number."); return; }
  run(async () => { await requestOtp(phone); setStep("code"); setCode(""); setResendIn(RESEND_SECONDS); });
}
```
- `e?` = optional parameter: the form passes an event; the "Resend" button calls it without one.
- `/^[6-9]\d{9}$/` is the same rule as the API's regex (without `+91`), checked **first** for an instant message. The API still checks too: client checks are for convenience, server checks are for safety.
- On success: move to the code step and start the timer.

```tsx
function checkCode(e: FormEvent) {
  e.preventDefault();
  run(async () => { await verifyOtp(phone, code); router.replace("/"); });
}
```
- On success go home. `replace` (not `push`) so the browser's Back button doesn't return to the login form.

**The markup** (`return (...)`):
- `{step === "phone" ? (<form>...) : (<form>...)}`: show one of the two forms depending on `step`.
- `<label htmlFor="phone">` + `<input id="phone">`: links the label to the box, so screen readers announce it and clicking the label focuses the box (**accessibility**).
- `inputMode="numeric"`: phones show a number keypad.
- `autoComplete="one-time-code"`: phones offer to **auto-fill the code from the SMS**.
- `onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}`: keep only digits (`\D` = any non-digit; `g` = everywhere).
- `disabled={busy || code.length !== 6}`: the button only works with a full 6-digit code and no request running.
- `<p role="alert" aria-live="polite">`: screen readers **announce** error messages when they appear.

### 30.4 `app/session-box.tsx` and `app/page.tsx`
```tsx
const [user, setUser] = useState<Profile | null | undefined>(undefined);
useEffect(() => {
  restoreSession().then(setUser).catch(() => setUser(null));
}, []);
```
- Three states in one variable: `undefined` = still checking, `null` = logged out, a `Profile` = logged in.
- On first appearance (`[]`), try the silent refresh. `.then(setUser)` passes the result straight to `setUser`; any failure → logged out.
- Then: "Checking your session…", a `<Link href="/login">` (Next.js's link: navigates without reloading the page), or "Logged in as …" with a Log out button.

`app/page.tsx` stays a **Server Component** (static heading and text) and renders `<SessionBox />`, the only part that needs the browser.

### 30.5 Small changes
- `app/layout.tsx`: page title "Namastute" instead of the template's "Create Next App".
- `.env.example` (committed) and `.env.local` (git-ignored): `NEXT_PUBLIC_API_URL=http://localhost:8080`.
- `apps/web/.gitignore`: `!.env.example` exception.

### 30.6 Verified in a real browser
Invalid number → instant message, no request · valid number → code sent, timer counts down · wrong code → "Code is wrong or expired" · right code → home shows "Logged in as +91…" · preflight `OPTIONS` → 200 before each call · page reload → **one** silent refresh → still logged in · `document.cookie` is empty (HttpOnly works) and nothing in `localStorage` · logout → reload shows "Log in".

---

## 31. Updating data: PATCH, dirty checking, Unicode

### 31.1 PATCH and partial updates
- **`PUT`** = replace the whole thing (send every field). **`PATCH`** = change **part** of it (send only what changes).
- Our `PATCH /api/v1/me` rules:
  - field **left out** of the JSON → arrives in Java as `null` → **unchanged**;
  - `"upiId": ""` (empty string) → **remove** the UPI ID;
  - any other value → validated, then saved.
- This "absent vs empty" distinction is the classic PATCH design question. `null` can't mean both "don't touch" and "remove", so we give "remove" its own value (`""`).
- **Users can only edit themselves:** the id comes from the **token** (`jwt.getSubject()`), never from the URL or body. There's no `PATCH /users/{id}` that someone could point at another person's id.

### 31.2 Validating names for India: Unicode categories
- **Unicode** gives every character in every script a **category**. In regex:
  - `\p{L}` = any **letter** (a, अ, க, অ…);
  - `\p{M}` = any **combining mark**: a sign that attaches to the letter before it.
- In Indian scripts, **vowel signs (matras) are marks, not letters**: in `की`, `क` is a letter and `ी` is a mark. A rule allowing only `\p{L}` rejected `केशव दीक्षित`; the test caught it. Our rule allows letters **and** marks, so Hindi, Tamil, Bengali names all work.
- In JavaScript the same classes need the **`u` flag** (Unicode mode): `/^\p{L}.../u`.
- **Client checks are for friendly instant messages; server checks are for safety.** Both use the same rules.

### 31.3 Changing data with JPA: dirty checking
- So far we only *inserted* (`save`) and *read*. To **update**:
  1. load the entity (`findById`);
  2. change its fields (`user.changeName(...)`);
  3. **that's it**: no `save()` call.
- **Dirty checking:** inside a **transaction**, Hibernate remembers each loaded entity's original values. When the transaction **commits** (at the end of the `@Transactional` method), it compares, finds changed ("**dirty**") fields, and writes an `UPDATE` for just those.
- That's why `updateProfile` is **`@Transactional`**: without a transaction around it, there's no commit moment, so the changes would never be written.
- `@Transactional` also means **all or nothing**: if something throws halfway, every change in the method is rolled back.

### 31.4 Privacy by design
- The UPI ID is **personal data**. Rule (design §4.4): shown only to the user themselves and their own group members.
- Today only `/me` returns it, so it's safe. Every future endpoint that returns *other* users must leave it out unless the caller is in the same group.
- Stored lower-cased (UPI IDs are case-insensitive), so later comparisons are simple.

### 31.5 Testing protected endpoints without logging in
- `spring-boot-starter-security-oauth2-resource-server-test` adds **`jwt()`**: `mvc.perform(get(...).with(jwt().jwt(j -> j.subject(id))))` means "this request carries a valid token for user `id`".
- The full OTP → token flow is already tested in `AuthFlowTest`; profile tests only need "logged in as X", so they skip it. Faster, and each test checks one thing.

### 31.6 Reusing frontend logic: a custom hook
- Login and profile forms both need "busy while saving" + "show the error". Copying that code twice means fixing bugs twice.
- A **custom hook** is your own function starting with `use` that bundles hooks (`useState`) and logic, so components can share it: `const { busy, error, setError, run } = useAction();`.
- Rule of thumb: extract when the **same** logic is needed in a second place, not "in case we need it later".

---

## 32. Code walkthrough: the profile

### 32.1 `users/User.java` (additions)
```java
private String upiId;
```
- Maps to the `upi_id` column (Spring's naming rule turns `upiId` into `upi_id`).

```java
void changeName(String name) { this.name = name; }
void changeUpiId(String upiId) { this.upiId = upiId; }
```
- The only ways to modify a user. Named methods rather than generic setters make it clear **what** can change; later they're the natural place for rules (e.g. "banned users can't rename").

### 32.2 `users/UserService.java` (additions)
```java
@Transactional
Profile updateProfile(UUID id, String name, String upiId) {
    User user = users.findById(id).orElseThrow();
    if (name != null) {
        user.changeName(name);
    }
    if (upiId != null) {
        user.changeUpiId(upiId.isEmpty() ? null : upiId.toLowerCase(Locale.ROOT));
    }
    return toProfile(user);
}
```
- `@Transactional`: start a transaction; commit when the method returns (31.3).
- `findById(id).orElseThrow()`: load the user. The id comes from a valid token, so the user exists; if not, something is badly wrong, and an exception (500) is right.
- `if (name != null)`: only change what was sent (31.1).
- `upiId.isEmpty() ? null : ...`: `""` means remove → store `null`.
- `toLowerCase(Locale.ROOT)`: lower-case with **language-neutral** rules. Plain `toLowerCase()` uses the computer's language; on a Turkish system, `I` becomes `ı` (dotless i), a famous bug.
- No `save()`: dirty checking writes the `UPDATE` at commit.

```java
private static Profile toProfile(User user) { ... }
record Profile(UUID id, String phone, String name, String upiId) {}
```
- One place converts an entity into the response record; `profile()` and `updateProfile()` both use it.

### 32.3 `users/MeController.java` (additions)
```java
static final String NAME = "^\\p{L}([\\p{L}\\p{M} .'-]*[\\p{L}\\p{M}.])?$";
```
Read it piece by piece:
- `^\p{L}`: must **start** with a letter (not a space or a dot).
- `( ... )?`: the rest is optional (the `@Size(min = 2)` rule handles length).
- `[\p{L}\p{M} .'-]*`: then any number of letters, marks, spaces, dots, apostrophes, hyphens (`D'Souza`, `Pinto-Rao`, `Jr.`).
- `[\p{L}\p{M}.]$`: must **end** with a letter, mark or dot, so no trailing space.
- `\\` is doubled because `\` is also Java's escape character inside strings.

```java
static final String UPI_ID_OR_EMPTY = "^$|^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$";
```
- `^$` = the empty string (remove). `|` = **or**.
- `[a-zA-Z0-9._-]{2,256}`: the handle: 2–256 of letters, digits, `.`, `_`, `-`.
- `@[a-zA-Z]{2,64}`: `@` then the bank's handle, letters only (`okaxis`, `ybl`).

```java
record UpdateMe(
        @Size(min = 2, max = 60, message = "must be 2 to 60 characters")
        @Pattern(regexp = NAME, message = "...")
        String name,
        @Pattern(regexp = UPI_ID_OR_EMPTY, message = "must look like yourname@bank")
        String upiId) {}
```
- The PATCH body. Bean Validation **skips `null` fields** (`@Size` and `@Pattern` treat null as valid), which is exactly "left out = unchanged".

```java
@PatchMapping("/api/v1/me")
UserService.Profile update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateMe req) {
    return users.updateProfile(UUID.fromString(jwt.getSubject()), req.name(), req.upiId());
}
```
- `@PatchMapping`: handles `PATCH /api/v1/me`. The user id comes **only** from the token (31.1). Returns the updated profile as JSON.

### 32.4 `ProfileTest.java`
- `freshUser()` creates a user with a random number before each test, so tests never affect each other.
- `jwt().jwt(j -> j.subject(userId.toString()))`: a fake-but-accepted token for that user (31.5).
| Test | Proves |
|---|---|
| `newUserHasNoNameOrUpiId` | A new user starts with neither |
| `setsNameAndUpiIdLowercased` | Both saved; `Keshav.D@OkAxis` stored as `keshav.d@okaxis` |
| `leftOutFieldIsUnchangedAndEmptyUpiIdRemovesIt` | The PATCH rules from 31.1 |
| `acceptsNamesInAnyScript` | Hindi, Tamil, Bengali, `D'Souza-Pinto Jr.` |
| `rejectsBadInput` | Too short, leading space, `<script>`, UPI without `@bank`, bank with digits → 400 `VALIDATION_FAILED` |
| `needsLogin` | No token → 401 |

### 32.5 `lib/use-action.ts` (new)
```ts
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function run(action: () => Promise<void>) { ... }
  return { busy, error, setError, run };
}
```
- The busy/error logic that used to live inside the login page, moved here unchanged (31.6). It returns an **object**, and callers pick what they need with **destructuring**: `const { busy, error, run } = useAction();`.

### 32.6 `lib/api.ts` (additions)
```ts
export type Profile = { id: string; phone: string; name: string | null; upiId: string | null };

export function updateMe(changes: { name?: string; upiId?: string }): Promise<Profile> {
  return request<Profile>("/api/v1/me", { method: "PATCH", body: JSON.stringify(changes) });
}
```
- `name?:` = an **optional property**: it may be missing entirely. `JSON.stringify` leaves out missing properties, which the API treats as "unchanged".

### 32.7 `app/login/page.tsx` (changes)
- Uses `useAction()` instead of its own copy.
- After a successful verify:
  ```ts
  const me = await getMe();
  router.replace(me.name ? "/" : "/profile");
  ```
  First login (no name yet) → profile page; afterwards → home. The decision is made **before** navigating, so there's no flash of the wrong page.

### 32.8 `app/profile/page.tsx` (new)
```tsx
const NAME = /^\p{L}([\p{L}\p{M} .'-]*[\p{L}\p{M}.])?$/u;
const UPI_ID = /^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$/;
```
- The same rules as the API, in JavaScript (`u` flag for `\p{...}`).

```tsx
useEffect(() => {
  restoreSession()
    .then((profile) => {
      if (!profile) return router.replace("/login");
      setMe(profile); setName(profile.name ?? ""); setUpiId(profile.upiId ?? "");
    })
    .catch(() => router.replace("/login"));
}, [router]);
```
- On load: not logged in → login page. Otherwise **prefill** the form with saved values (`?? ""` turns `null` into an empty box).
- `[router]` in the dependency array: the effect uses `router`, so React's rules ask us to list it (it never actually changes).

```tsx
function save(e: FormEvent) {
  e.preventDefault();
  const cleanName = name.trim();
  const cleanUpi = upiId.trim();
  if (... !NAME.test(cleanName)) { setError("Enter your name ..."); return; }
  if (cleanUpi && !UPI_ID.test(cleanUpi)) { setError("A UPI ID looks like ..."); return; }
  run(async () => { await updateMe({ name: cleanName, upiId: cleanUpi }); router.replace("/"); });
}
```
- `.trim()` removes spaces people accidentally type at the ends, so the strict API rule isn't hit by a stray space.
- Check locally first for a clear message, then send. An empty UPI box sends `""`, which removes it.

**The markup:**
- Heading says "Complete your profile" for a new user, "Your profile" afterwards.
- Each input has a `<label htmlFor>` and a short help line linked with **`aria-describedby`**, so screen readers read the help after the label.
- `autoComplete="name"` lets the browser offer the user's saved name; `autoCapitalize="none"` and `spellCheck={false}` stop phones "correcting" a UPI ID.
- `if (!me) return <p>Loading…</p>;`: nothing editable shows until the saved values are loaded, so the form never flashes empty.

### 32.9 `app/session-box.tsx` (changes)
- Shows the **name** when there is one, else the phone: `{user.name ?? user.phone}`.
- Adds an "Edit profile" / "Complete your profile" link.

### 32.10 Verified in a real browser
New number → after the code, lands on **Complete your profile** · invalid UPI → friendly message · Hindi name + `Keshav.D@OkAxis` → home shows the name, database has `keshav.d@okaxis` · Edit profile → form prefilled · clear the UPI ID → database value `null` · logged out → `/profile` redirects to `/login`.

---

## 33. Places, maps and geocoding

### 33.1 Coordinates
- **Latitude:** north–south position, −90° (South Pole) to 90° (North Pole). **Longitude:** east–west, −180° to 180°, measured from Greenwich. Delhi ≈ 28.6° N, 77.2° E.
- **The order trap:** people say "lat, lng", but maths-style libraries (Java's **JTS**, PostGIS, GeoJSON) use **(x, y) = (longitude, latitude)**. Swap them and Delhi (28.6, 77.2) becomes a point in the Indian Ocean. Our code only ever builds points through `Geo.point(lat, lng)`, and a test asks PostGIS itself which number is the latitude.
- **WKT** (Well-Known Text): a text format for shapes: `SRID=4326;POINT(77.0865 28.5555)`, longitude first.

### 33.2 `geography` vs `geometry` in PostGIS
- `geometry`: points on a **flat plane**; distances come out in "degrees", meaningless for real travel.
- `geography`: points on the **round Earth**; distances in **metres**. Ours: `geography(point, 4326)`.

### 33.3 Distance on a sphere: the haversine formula
- The shortest path between two points on a sphere is a **great circle** (the curve a plane flies). The **haversine formula** computes its length from two lat/lng pairs and the Earth's radius (6,371 km).
- That's **straight-line** ("as the crow flies") distance. Roads are longer, so we multiply by **1.4** (a known shortcut, Proposed Stash idea 1 replaces it with real road data).

### 33.4 Spatial data in Java: Hibernate Spatial
- `hibernate-spatial` teaches Hibernate the PostGIS types. A `geography` column maps to a JTS **`Point`** field with `@JdbcTypeCode(SqlTypes.GEOGRAPHY)`.
- **JTS** (Java Topology Suite): the standard Java library for shapes: points, lines, polygons. A `GeometryFactory` creates them with a fixed SRID (4326).

### 33.5 Data migrations (reference data)
- `V3__seed_terminals.sql` **inserts rows**, not structure: the three IGI terminals and their pickup points. Versioned like any migration, so every database has the same terminals.
- The coordinates are approximate; the PM's on-site corrections will ship as a **new** migration (`V4…`), never by editing V3.

### 33.6 Maps in the browser
- **Leaflet:** a small, popular JavaScript map library. A map is built from **tiles**: 256×256 images, loaded for the area and zoom level you're viewing.
- **OpenStreetMap (OSM):** a free, community-made world map. Its licence (**ODbL**) requires showing "**© OpenStreetMap contributors**". OSM's own tile servers are for light use only; a production app uses a tile provider.
- Leaflet needs the browser's `window`, which doesn't exist during server rendering. So the map component is loaded with **`dynamic(() => import(...), { ssr: false })`**: "only load this in the browser". In Next.js 16 this is only allowed inside a Client Component.
- **Accessibility:** a map can't be used by keyboard or screen reader, so the **search box** is the accessible way to set a destination; the map is an extra.

### 33.7 Geocoding
- **Forward geocoding:** text → coordinates ("DLF Phase 3" → 28.49, 77.09). **Reverse geocoding:** coordinates → place name.
- **Nominatim** = OSM's free geocoder. Usage policy: about **1 request per second**, **no search-as-you-type**. So our search runs on a button / Enter, and reverse geocoding only on a map click.
- **Privacy:** other travellers only ever see an **area label** ("Hauz Khas, Delhi"), never the exact pin (design §4.4). The label is built at neighbourhood level (`zoom=16`), never a house number.
- Using a third-party geocoder sends the user's destination to that third party. Fine for development; part of the provider decision before launch (open question 2).
- All geocoding lives in **one file** (`lib/geocode.ts`), so switching to Ola Maps or Google changes only that file.

---

## 34. Time zones, enums and trip rules

### 34.1 Time zones
- The database stores **instants in UTC** (`timestamptz`). Java's `Instant` and JSON strings ending in `Z` ("Zulu" = UTC) are UTC too.
- Users think in **IST** (UTC+05:30). An airport in Delhi is always IST, **whatever time zone the user's phone is set to**, so we convert with an explicit `+05:30`, not "the phone's local time".
- `<input type="datetime-local">` gives `"2026-10-14T00:49"`: a time **with no zone**. `lib/time.ts` is the **one place** that attaches `+05:30` and converts to UTC.
- Verified: 00:49 IST was stored as **19:19 UTC the previous day**.

### 34.2 Next.js Cache Components and "now"
- Our Next.js config has **Cache Components** on: pages are **prerendered** (built ahead of time) where possible.
- Calling `Date.now()` while rendering would freeze the build machine's "now" into the page, stale for every visitor. Next.js 16 stops the build with an error. Fix: compute time-dependent values **in the browser, after load** (inside `useEffect`).

### 34.3 Postgres enum types from Java
- `trip_status` is a **Postgres enum** (a column type allowing only listed words). Java maps it with `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`, and the Java enum's names must match exactly (lower case here).
- **Gotcha found by the tests:** in JPQL, Hibernate writes enum **literals** as `'expired'::Status`, a cast to a type named after the *Java class*, which doesn't exist. Inserts and parameters work fine. Fix: the two bulk updates are **native SQL** (`nativeQuery = true`), where `'expired'` is plain text that Postgres converts to the enum itself.

### 34.4 Trip rules and where each is enforced
| Rule | Where | Why there |
|---|---|---|
| Seats 1–3, bags 0–6, lat/lng in range, area ≤ 120 chars, flight like `AI 865` | Bean Validation on the request | Shape of the input |
| Terminal must exist | Service (`UNKNOWN_TERMINAL`) | Needs the database |
| Exit time between now (−5 min grace) and +24 h | Service (`EXIT_TIME_OUT_OF_RANGE`) | Needs "now" |
| Destination ≤ 100 km from the terminal | Service (`DESTINATION_TOO_FAR`) | Not an airport cab ride otherwise |
| Women-only only for ID-verified women | Service (`WOMEN_ONLY_NEEDS_VERIFICATION`, 403); refused for all until verification exists | The rule exists from day one |
| **One active trip per user** | **Database** unique index `trip_one_active` → 409 `ACTIVE_TRIP_EXISTS` | Two simultaneous requests can't both pass; same "let the database decide" idea as `UserService.create` |
| Can't cancel someone else's trip | SQL `where user_id = :userId` → 404 | Don't even reveal it exists |

- **Stale trips:** a trip expires at exit + 60 min (design §3.7). There's no scheduled job yet, so: `current` simply ignores old trips, and `create` first marks the user's stale trips `expired`, otherwise a forgotten trip would block a new one through the unique index. A scheduled job will do this for everyone later.

### 34.5 Status codes used
- **201 Created:** a new thing was made (`POST /trips`).
- **204 No Content:** success with nothing to return (no current trip; cancel done).
- **409 Conflict:** the request clashes with the current state (already have a trip).
- **404 Not Found:** also used for "exists but isn't yours", so nobody can probe for other users' trips.

---

## 35. Code walkthrough: trips

### 35.1 `pom.xml` and `V3__seed_terminals.sql`
- `hibernate-spatial` added (version managed by Spring Boot's BOM).
- V3 inserts DEL-T1/T2/T3 with `pickup = 'SRID=4326;POINT(lng lat)'` and a note that they're approximate.

### 35.2 `trips/Geo.java`
```java
private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

static Point point(double lat, double lng) {
    return WGS84.createPoint(new Coordinate(lng, lat));   // x = longitude, y = latitude
}
```
- One factory for all points, fixed to SRID 4326. `point(lat, lng)` takes the human order and passes `Coordinate(x = lng, y = lat)`, so the swap happens in **one** place. `lat(p)` = `p.getY()`, `lng(p)` = `p.getX()`.

```java
static double distanceKm(Point a, Point b) {
    double dLat = Math.toRadians(lat(b) - lat(a));
    double dLng = Math.toRadians(lng(b) - lng(a));
    double h = Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(Math.toRadians(lat(a))) * Math.cos(Math.toRadians(lat(b))) * Math.pow(Math.sin(dLng / 2), 2);
    return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
}
```
- The haversine formula (33.3). Trigonometry in Java works in **radians**, so degrees are converted first. `h` is the "haversine" of the angle between the points; `2 × R × asin(√h)` turns it into a distance.
- `final class` + `private Geo() {}`: a **utility class**: only static helpers, can't be extended or instantiated.

### 35.3 `trips/Terminal.java`
- Read-only entity for `terminals`. `@Id private String id;` (`DEL-T3`; the id is text, not a UUID).
- `@JdbcTypeCode(SqlTypes.GEOGRAPHY) private Point pickup;`: the PostGIS `geography` column as a JTS `Point`.

### 35.4 `trips/TripRequest.java`
- Entity for `trip_requests`. Field types chosen to match the columns exactly, because `ddl-auto: validate` checks them:
  - `float soloKm` ↔ `real` (4-byte decimal); `double` would expect `double precision`.
  - `short seats, bags` ↔ `smallint`.
  - `Instant exitAt` ↔ `timestamptz`.
- `enum Status { searching, grouped, cancelled, expired, done }`: lower-case names to match the Postgres enum labels.
- `@Enumerated(EnumType.STRING)` stores the name (not a number); `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` says the column is a Postgres enum (34.3).
- The constructor sets `status = Status.searching`: every new trip starts searching.

### 35.5 `trips/TripRepository.java`
```java
Optional<TripRequest> findFirstByUserIdAndStatusIn(UUID userId, Collection<TripRequest.Status> statuses);
```
- A derived query: `where user_id = ? and status in (?, ?) limit 1`.

```java
@Query(nativeQuery = true, value = """
        update trip_requests set status = 'expired'
        where user_id = :userId and status = 'searching' and exit_at < :cutoff""")
int expireStale(UUID userId, Instant cutoff);
```
- **Native SQL** (34.3). Marks this user's old searching trips expired; returns how many.
- `cancel(id, userId)`: same style; the `user_id = :userId` condition is the ownership check, and it only cancels `searching` trips. Returns 1 or 0.

### 35.6 `trips/TripService.java`
**Constants** at the top: 5-minute grace, 24-hour limit, 60-minute expiry, 100 km limit, and `ACTIVE` = searching or grouped.

**Records:** `CreateTrip` (what the controller passes in) and `TripView` (what the API returns: id, terminal id and name, flight, exit time, area, lat/lng, seats, bags, women-only, status, km).

**`create(userId, req)`**, step by step:
1. `terminals.findById(...).orElseThrow(...)`: unknown terminal → 400.
2. Exit time window check against `Instant.now()`.
3. `if (req.womenOnly())` → 403 (shortcut until ID verification).
4. `Geo.point(...)` and `Geo.distanceKm(terminal pickup, dest)`; > 100 km → 400.
5. `trips.expireStale(userId, now − 60 min)`: clear the way (34.4).
6. `saveAndFlush(new TripRequest(...))` inside `try`. The flight number is normalised (`"ai 865"` → `"AI865"`), the area trimmed, and `soloKm = straight × 1.4`.
7. `catch (DataIntegrityViolationException e)` → 409 `ACTIVE_TRIP_EXISTS`: the unique index refused a second active trip.

**`current(userId)`**
```java
return trips.findFirstByUserIdAndStatusIn(userId, ACTIVE)
        .filter(t -> t.getExitAt().isAfter(Instant.now().minus(EXPIRES_AFTER)))
        .map(t -> view(t, terminals.findById(t.getTerminalId()).orElseThrow()));
```
- `Optional.filter(...)`: keep the trip only if it hasn't expired; otherwise the result becomes empty. A read never changes data.

**`cancel(userId, tripId)`**: if the update changed 0 rows → 404 (not found, not yours, or no longer searching: same answer).

**`view(...)`**: builds `TripView`; `Math.round(km * 10) / 10.0` rounds to one decimal.

### 35.7 `trips/TripController.java`
- `CreateTripRequest`: the validated request body (34.4). `Double destLat` (object, not `double`) so `@NotNull` can catch a **missing** value; a primitive would silently become 0.0, which is a real place (in the Atlantic).
- `@GetMapping("/api/v1/terminals")`: maps each `Terminal` to `TerminalView(id, name, lat, lng, pickupNote)` using `stream().map(...).toList()`.
- `@PostMapping("/api/v1/trips")` → `ResponseEntity.status(HttpStatus.CREATED).body(trip)`: **201**.
- `@GetMapping("/api/v1/trips/current")`: `.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build())`: 200 with the trip, or 204.
- `@DeleteMapping("/api/v1/trips/{id}")` with **`@PathVariable UUID id`**: `{id}` in the path is copied into the parameter (and must be a valid UUID, or Spring answers 400).

### 35.8 Tests
- **`GeoTest`** (unit, no Spring): lat/lng kept apart; T3 → Connaught Place ≈ 15 km; distance symmetric, zero to itself.
- **`TripsTest`** (HTTP + Spring Security with `jwt()`):
  | Test | Proves |
  |---|---|
  | `listsTheThreeIgiTerminals` | V3 seed is there (`$[?(@.id == 'DEL-T3')]` is a JsonPath **filter**) |
  | `createsTripAndStoresTheDestinationTheRightWayRound` | 201; and `ST_Y`/`ST_X` from PostGIS confirm latitude and longitude weren't swapped; km in range |
  | `currentTripThenCancel` | current → 200, cancel → 204, current → 204, cancel again → 404 |
  | `onlyOneActiveTripAtATime` | second create → 409 |
  | `staleTripDoesNotBlockANewOne` | a trip moved 2 h into the past is ignored and gets marked `expired` when a new one is created |
  | `cannotCancelSomeoneElsesTrip` | another user gets 404 |
  | `rejectsBadRequests` | unknown terminal, past/too-far-future time, Mumbai destination, women-only, 4 seats, bad flight number |
  | `needsLogin` | no token → 401 |
- `JdbcTemplate`: Spring's helper for running plain SQL in tests (e.g. moving a trip into the past).
- `"""...""".formatted(exitAt)`: a text block with a `%s` placeholder filled by `formatted`.

### 35.9 `lib/time.ts`
```ts
export function istInputValue(minutesFromNow: number): string {
  const shifted = new Date(Date.now() + (minutesFromNow + IST_OFFSET_MINUTES) * 60_000);
  return shifted.toISOString().slice(0, 16);
}
```
- Trick: shift "now" forward by 5 h 30 min, then print it **as UTC**; the digits are the IST wall-clock time. `.slice(0, 16)` keeps `YYYY-MM-DDTHH:mm`, the format `datetime-local` wants.
- `istInputToIso(value)`: `new Date(value + ":00+05:30").toISOString()`: attach the IST offset, then convert to UTC.
- `formatIst(iso)`: `Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", ... })` formats any instant as Indian time, e.g. "14 Oct 2026, 12:49 am".

### 35.10 `lib/geocode.ts`
- `searchPlaces(query)`: `GET /search` with `countrycodes=in`, a Delhi NCR `viewbox` and `bounded=1` (only results inside the box), `limit=5`. Returns `{lat, lng, name, area}` per result. `new URLSearchParams({...})` builds a correctly escaped query string.
- `areaAt(lat, lng)`: `GET /reverse` at `zoom=16` (neighbourhood level).
- `areaLabel(r)`: picks the first available of neighbourhood / suburb / quarter / residential / village, plus city / town / district, removes duplicates, and joins with ", ". Never a house number.
  - `.filter((part, i, all): part is string => !!part && all.indexOf(part) === i)`: keep non-empty parts appearing for the first time. `part is string` is a **type guard**: it tells TypeScript the result has no `undefined`s.

### 35.11 `lib/api.ts` (additions)
- Types `Terminal`, `NewTrip`, `Trip`. `Omit<NewTrip, "flightNo"> & {...}` = "everything in NewTrip except flightNo, plus these fields": TypeScript **utility types**.
- `getTerminals`, `createTrip` (POST), `cancelTrip` (DELETE).
- `getCurrentTrip`: `(await request(...)) ?? null`: a 204 gives `undefined`, turned into `null` = "no trip".

### 35.12 `app/trip/new/destination-map.tsx`
- `import "leaflet/dist/leaflet.css"`: Leaflet's styles (without them the tiles appear scattered).
- **Refs** (`useRef`): values that survive redraws **without** causing one: the map `<div>`, the Leaflet map object, the marker layer, and the latest `onPick`. Leaflet manages its own drawing, so it lives in refs, not state.
- Effect 1 (`[]`, runs once): create the map centred on the airport, add OSM tiles with attribution, a layer group for markers, and a click handler. The **cleanup** `m.remove()` destroys the map; React's development mode mounts twice, and without cleanup you'd get "Map container is already initialized".
- `latestOnPick` + an effect without dependencies keeps the click handler calling the **newest** `onPick` without recreating the map.
- Effect 2 (`[pickup, destination]`): clear the layer, draw blue (pickup) and red (destination) `circleMarker`s, pan to the destination. Circle markers need no image files (Leaflet's default pin images often break in bundled apps).
- `role="application"` + `aria-label`: tells screen-reader users what the area is and points them to the search box.

### 35.13 `app/trip/new/page.tsx`
- `dynamic(() => import("./destination-map"), { ssr: false, loading: ... })`: load the map only in the browser, with a placeholder (33.6).
- On load: not logged in → `/login`; no name → `/profile`; else set the default exit time (in the browser, 34.2) and load terminals.
- `pickup = terminals.find(t => t.id === terminalId)`: **derived** from state, never stored separately.
- `pickOnMap(point)`: set the pin, show "Finding the area…", then fill in the reverse-geocoded label (empty if the lookup fails; the user can type one).
- Search: Enter in the search box calls `e.preventDefault()` so it searches instead of submitting the trip form (a form can't contain another form). Results are buttons; choosing one sets the pin and area.
- Two separate `useAction()`s (`search`, `submit`): each has its own busy state and error, so a failed search doesn't disable the Save button.
- `save`: requires a destination and area, converts the exit time with `istInputToIso`, sends `createTrip`, then goes home.
- `<fieldset>` + `<legend>`: groups the destination controls under one heading for screen readers.
- `<input type="datetime-local" min max>`: the picker itself limits choices to now…+24 h (the API still checks).
- The disabled women-only checkbox shows the feature exists and why it's unavailable.

### 35.14 `app/current-trip.tsx` and `app/session-box.tsx`
- `CurrentTrip`: loads your trip (`undefined` = loading, `null` = none). Shows "Plan a trip", or the trip (terminal → area, IST time, seats/bags/flight, km) with **Cancel**.
- `` `seat${trip.seats > 1 ? "s" : ""}` ``: simple plurals.
- `SessionBox` renders `<CurrentTrip />` only when the user has a name.

### 35.15 Verified in a real browser
Log in → **Plan a trip** · form defaults to T3 and now + 30 min IST · search "DLF Phase 3" with Enter → results (form not submitted) · choose → blue pickup + red destination on the map, area "Sector 25A, Gurgaon" · click elsewhere → "Hauz Khas, Delhi" · submit → home shows "Terminal 3 → Hauz Khas, Delhi, 12:49 am, 1 seat, 2 bags, flight AI865, about 13.9 km" · database: 00:49 IST stored as 19:19 UTC, latitude/longitude correct · Cancel → back to "Plan a trip".

---

## 36. Matching: two-stage search and spatial indexes

### 36.1 The problem
- Input: **my trip** (terminal, exit time, destination, seats, bags). Output: the **5 best other travellers** to share a cab with.
- "Best" needs real maths (route order, who rides how far extra). That maths is hard to write in SQL and slow to run on every row.

### 36.2 Two-stage search
- **Naive way 1:** load every trip into Java and do the maths on all of them. The `trip_requests` table keeps *every* trip ever (cancelled, expired, done), so this gets slower every day, uses memory, and ships thousands of rows over the network for nothing.
- **Naive way 2:** do all the maths in SQL. Possible, but hard to read, hard to change and hard to test.
- **Two-stage search** (also called **retrieve, then rank**: the same shape search engines and recommendation systems use):
  1. **Prefilter / candidate generation** (database): cheap, coarse conditions that **indexes** can answer quickly. Throws away the obviously wrong rows. A **candidate** = a row that survived stage 1.
  2. **Ranking** (Java): the exact, expensive rules on the few candidates left.
- The rule for stage 1: it should **never throw away a good match** if it can help it; letting a few bad ones through is fine, stage 2 removes them.
- Our stage 1: same terminal · status `searching` · exit time within ±30 min of mine · not me · nobody blocked anybody · destination within **15 km** of mine.
- **Honest trade-off:** 15 km *can* throw away a good match: someone going 30 km further down the same highway (DLF Phase 3 → Manesar) is a perfect share but is more than 15 km away. We accept that for v1 ("heading the same way" ≈ "going to a nearby area"). A smarter prefilter would use **direction (bearing)** from the airport. Marked as a `shortcut:` in the code.

### 36.3 Indexes recap: B-tree, composite, partial
- An **index** is a separate, sorted copy of some columns pointing back to the rows, like the index at the back of a book. Without one, the database does a **sequential scan**: reads every row.
- **B-tree:** Postgres's default index; a sorted tree. Great for `=`, `<`, `between`, `order by`.
- **Composite index:** on several columns, e.g. `trip_search (terminal_id, exit_at)`. Sorted by the first column, then the second inside it, like a phone book (surname, then first name). Works best as "**equal** on the first, **range** on the second": `terminal_id = 'DEL-T3' and exit_at between …`.
- **Partial index:** only contains rows matching a `where`. `trip_search … where status = 'searching'` holds only live trips, so it stays small however many old trips pile up.

### 36.4 Why a B-tree can't do "near this point", and what GiST does
- A B-tree needs **one sort order**. Points are **2-D**: sort by latitude and two places at the same latitude but 50 km apart sit side by side, while a place 1 km north is far away in the list. "Near" isn't "next to each other in a sorted list".
- **GiST** (Generalized Search Tree): a Postgres framework for tree indexes over data that *can't* simply be sorted (shapes, ranges, text search). PostGIS uses it to build an **R-tree**:
  - Every tree node stores a **bounding box**: the smallest rectangle containing everything below it.
  - To find "within 15 km of X": make a box around X grown by 15 km, then walk down **only** into nodes whose boxes overlap it. Whole regions (Noida, Faridabad…) are skipped without looking at a single row inside.
  - The boxes only *approximate* the answer, so Postgres then does the **exact** distance check on the survivors (shown as "Recheck Cond" / "Filter" in a plan). Two stages again, inside the database.
- Ours (V1 migration): `create index trip_dest_gist on trip_requests using gist (dest);`

### 36.5 `ST_DWithin`, not `ST_Distance < x`
- `ST_DWithin(a, b, 15000)`: true if `a` and `b` are within 15,000 **metres** (geography). PostGIS knows how to turn it into the box search above, so the **index is used**.
- `ST_Distance(a, b) < 15000` gives the same answer but calculates the distance for **every row** first: the index **can't** be used. Classic interview gotcha.

### 36.6 Reading query plans: `EXPLAIN`
- The **query planner** is the part of Postgres that decides *how* to run a query (which index, which order), using statistics about the table.
- `EXPLAIN <query>` shows the chosen **plan** without running it. `EXPLAIN ANALYZE <query>` really runs it and adds real row counts and times.
- Words you'll see:
  - **Seq Scan:** reads every row.
  - **Index Scan:** walks an index, then fetches matching rows.
  - **Bitmap Index Scan → Bitmap Heap Scan:** collects matching row locations from an index first, then reads them in disk order. **BitmapAnd** combines two indexes.
  - `cost=0.00..8.27`: the planner's *estimate* in its own units (not milliseconds). `rows=`: estimated rows.
- **Gotcha:** on our dev database with ~10 trips the planner picks **Seq Scan**, and it's *right*: reading one 8 KB page is cheaper than walking an index. That isn't a bug. To see the index in dev: `set enable_seqscan = off;` (only in your own `psql` session, never in the app), or test with thousands of rows.
- With real traffic the planner may prefer `trip_search` (few people leave one terminal in the same hour) and then check distance on those few rows. Also fine: it picks whichever index cuts the most rows.

---

## 37. Matching: detours, pure functions, privacy, N+1

### 37.1 The detour model
- **Pickup** = the terminal's cab pickup point. **Solo km** = straight-line km from pickup to my destination × `Geo.ROAD_FACTOR` (1.4). Already stored on every trip as `solo_km`.
- **Shared route:** one cab, several drops. Order chosen **greedily**: from the pickup, go to the **nearest** remaining drop first.
  - A **greedy algorithm** takes the best-looking step right now and never reconsiders. Not always the shortest order overall (that's the "travelling salesman problem"), but for 2–4 drops it's close enough and trivially simple.
- Each rider's **ride km** = distance along the route until their drop. Whoever is dropped first rides exactly their solo km; the last one rides the whole route.
- **Fairness rule (design §3.3):** nobody rides more than **max(3 km, 25 % of their solo km)** beyond their solo km. `max` because 25 % of a 4 km trip is only 1 km, too strict; 3 km is the floor.
- **Capacity:** seats together ≤ 4 and bags together ≤ 4 (a sedan, design §3.3).
- **Detour (extra km)** = shared route − the **longest** solo trip. That's how much further the cab drives than going straight to the farthest person, and exactly the extra km the last-dropped rider sits through.
  - Why not "shared − *my* solo"? If Asha lives beyond me on the same road, the shared route is long only because Asha's own trip is long. I'm dropped first and ride 0 km extra, yet "shared − my solo" would call it a big detour and rank a perfect match low.
- **Score** = detour km + 0.2 × |minutes apart|, **lowest first**. 0.2 means "1 km of detour is as bad as waiting 5 minutes".

### 37.2 Worked example (from T3, my destination DLF Phase 3, my solo 9.7 km)
| Who | Destination | Their solo | Dropped first | Shared route | Last rider's extra | Limit | Result |
|---|---|---|---|---|---|---|---|
| Neha | Cyber City | 9.4 | Neha | 10.0 | me +0.3 | 3.0 | ✅ detour 0.3 |
| Asha | Sector 29 | 14.0 | me | 15.4 | Asha +1.4 | 3.5 | ✅ detour 1.4 |
| Ravi | Dwarka Sec 21 | 3.9 | Ravi | 14.2 | me +4.5 | 3.0 | ❌ opposite direction |
| Someone | Noida Sec 18 | 32.8 | — | — | — | — | ❌ 24 km from me, never reaches stage 2 |

- Asha leaves 10 min after me: score 1.4 + 0.2 × 10 = **3.4**. Neha leaves 25 min after me: 0.3 + 0.2 × 25 = **5.3**. Asha is shown first.

### 37.3 Pure functions
- A **pure function**: its result depends **only on its inputs**, and it has **no side effects**.
- A **side effect** is anything a function does besides returning a value: reading/writing the database, network calls, logging, reading the clock (`Instant.now()`), random numbers, changing shared variables.
- Why it matters:
  - **Tests are trivial:** "these inputs → this output". No Spring context, no database, no Docker: hundreds of cases run in milliseconds.
  - **Deterministic:** same input, same answer, every time. No flaky tests.
  - **Easy to reason about:** you can read it alone, without knowing what the rest of the app is doing.
- The clock trap: a ranker that calls `Instant.now()` inside gives different answers at different times. Pass times **in** instead.
- **Functional core, imperative shell:** keep the decision logic pure (`MatchRanker`), and put the messy parts (HTTP, auth, SQL, loading names) in a thin outer layer (`TripService`) that calls it.
- What the tests cover: a perfect same-road match (detour 0) · opposite directions rejected · exactly at the limit · the 3 km floor vs 25 % · too many seats/bags · sorting by score · only the top 5 · no candidates.

### 37.4 Privacy in matching
- **Data minimisation:** send only what the screen needs. Matches show **first name, area label, minutes apart, seats, bags, detour km**. Never phone, surname, exact destination, UPI ID or user id. Strangers are being introduced; knowing exactly where a stranger is going late at night is a safety problem. India's **DPDP Act 2023** (Digital Personal Data Protection Act) also expects personal data to be used only for a clear purpose.
- **How the code guarantees it:** a dedicated response **record** (DTO) containing only the allowed fields. An entity is never turned into JSON directly, so a column added later can't leak by accident.
- **Only for your own trip:** matches for a trip that isn't yours → **404**, same idea as cancelling (don't reveal it exists).
- **Leaks through numbers:** a number *derived* from a secret can reveal the secret. Detour km depends on the other person's exact destination; someone creating trips to several fake destinations and noting each detour could narrow it down (**trilateration**: finding a point from distances to known points). Mitigations: round detour to the **nearest 0.5 km**, the area label is already coarse, one active trip at a time.
- **Blocks:** if either person blocked the other, neither sees the other. Checked in both directions.

### 37.5 The N+1 query problem
- Scenario: 5 matches, each needs the person's first name.
- **Naive:** loop over the 5 trips and load each user → **1** query for the trips + **N** = 5 queries for names = **N+1** queries.
- **Why it hurts:** every query is a **round trip** to the database (≈1 ms on your laptop, more in the cloud, plus connection-pool time). A list of 100 = 101 queries. It hides easily: with JPA relations (`@ManyToOne`, loaded lazily), `trip.getUser().getName()` in a loop quietly fires one query per row.
- **Fix:** collect all the ids first, load them in **one** query (`where id in (…)`), and look names up in a `Map<UUID, String>`. (Or a SQL `join`.)
- **In Namastute:** the `trips` module may not touch the `users` module's repository or entity (Spring Modulith's `ModularityTest` would fail), so `UserService` gets one public method that takes all the ids and returns id → first name, in one query.
- **How to spot it:** turn on SQL logging (`logging.level.org.hibernate.SQL: debug`) and count the `select`s for one request.

### 37.6 The whole request
`GET /api/v1/trips/{id}/matches` → my trip, if it's mine and `searching` (else 404) → stage 1 SQL → stage 2 `MatchRanker` → top 5 → one query for their first names → response records with only the allowed fields.

---

## 38. Code walkthrough: matching

No new migration: V1 already created `blocks`, `trip_search` and `trip_dest_gist`.

### 38.1 `trips/MatchRanker.java`: stage 2, the pure function
```java
final class MatchRanker {
    static final int TOP = 5;
    static final int CAR_SEATS = 4;
    static final int CAR_BAGS = 4;
    static final double MIN_ALLOWANCE_KM = 3;
    static final double ALLOWANCE = 0.25;
    static final double KM_PER_MINUTE = 0.2;
```
- `final class` + `private MatchRanker() {}`: nobody can extend it or create one; it's just a home for a static function (like `Geo`).
- Every number from the design is a **named constant**, so `rank` reads like the rule ("max(3 km, 25 %)") instead of magic numbers.

```java
    record Ranked(TripRequest trip, double detourKm, double score) {}
```
- The result: which trip, its detour and its score. It holds the candidate `TripRequest` itself, so the service doesn't need a lookup map to find the trip again.

```java
    static List<Ranked> rank(Point pickup, TripRequest mine, List<TripRequest> candidates) {
        double mySolo = roadKm(pickup, mine.getDest());
        List<Ranked> ok = new ArrayList<>();
        for (TripRequest other : candidates) {
```
- **Inputs only:** the pickup point, my trip, the candidates. No repository, no `Instant.now()`. That's what makes it pure (37.3).
- `TripRequest` is a JPA entity, but here it's used as a plain Java object: building one in a test needs no database.
- `mySolo` is computed once, outside the loop.

```java
            if (mine.getSeats() + other.getSeats() > CAR_SEATS || mine.getBags() + other.getBags() > CAR_BAGS) {
                continue;   // doesn't fit in the car
            }
```
- The cheapest check first. `continue` = skip to the next candidate.

```java
            double theirSolo = roadKm(pickup, other.getDest());
            double longest = Math.max(mySolo, theirSolo);
            double route = Math.min(mySolo, theirSolo) + roadKm(mine.getDest(), other.getDest());
            double detour = route - longest;
```
- The detour model of 37.1 in four lines. **Greedy** with two people = "drop whoever is nearer first", so the route is *nearer person's solo + the hop between the two destinations*.
- The nearer person rides exactly their solo km; the farther person rides the whole route. So `route − longest` is both "how much further than going straight to the farthest person" and "the extra the last rider sits through".
- It can never be negative (the triangle rule: going via a stop is never shorter than going straight), apart from tiny rounding like −0.0000001.

```java
            if (detour > Math.max(MIN_ALLOWANCE_KM, ALLOWANCE * longest)) {
                continue;
            }
```
- The fairness rule. Only the **last** rider has any extra, and the last rider is the one with the `longest` solo trip, so one check covers both people.

```java
            long minutes = Math.abs(Duration.between(mine.getExitAt(), other.getExitAt()).toMinutes());
            ok.add(new Ranked(other, detour, detour + KM_PER_MINUTE * minutes));
        }
        return ok.stream().sorted(Comparator.comparingDouble(Ranked::score)).limit(TOP).toList();
    }
```
- `Duration.between(a, b)`: time from `a` to `b` (negative if `b` is earlier); `Math.abs` because before or after doesn't matter for the score.
- `Comparator.comparingDouble(Ranked::score)`: "sort by score, smallest first". `limit(TOP)` keeps 5.

```java
    private static double roadKm(Point a, Point b) {
        return Geo.distanceKm(a, b) * Geo.ROAD_FACTOR;
    }
```
- Reuses the haversine from `Geo` and the same 1.4 factor used for `solo_km`, so all distances are measured the same way.
- **Pairs only:** groups of 3–4 come in the next slice (`feat/groups`), where the greedy route visits several drops.

### 38.2 `trips/TripRepository.java`: stage 1, the SQL prefilter
```sql
select t.* from trip_requests me
join trip_requests t
  on t.terminal_id = me.terminal_id
 and t.status = 'searching'
 and t.exit_at between me.exit_at - interval '30 minutes' and me.exit_at + interval '30 minutes'
 and t.user_id <> me.user_id
 and ST_DWithin(t.dest, me.dest, 15000)
where me.id = :tripId
  and not exists (select 1 from blocks b
                  where (b.blocker_id = me.user_id and b.blocked_id = t.user_id)
                     or (b.blocker_id = t.user_id and b.blocked_id = me.user_id))
```
- A **self-join**: the table joined with itself under two names. `me` = my trip (one row, found by id), `t` = every other trip. Only **one** parameter (`:tripId`): terminal, time, user and destination all come from my row, so Java never has to pass a point into SQL.
- `<>` means "not equal": never match yourself.
- `ST_DWithin(t.dest, me.dest, 15000)`: within 15 km, index-friendly (36.5). Marked with a `shortcut:` comment (36.2).
- `not exists (select 1 ...)`: "keep `t` only if no blocks row exists for this pair". `select 1` because we only care **whether** a row exists, not what's in it. Both directions are checked.
- `select t.*` + return type `List<TripRequest>`: Hibernate turns each row into a `TripRequest`, including the geography column.
- Native SQL because of the `'searching'` enum literal (34.3) and the PostGIS function.

### 38.3 `users/UserService.java`: names in one query
```java
public Map<UUID, String> firstNames(Collection<UUID> ids) {
    Map<UUID, String> names = new HashMap<>();
    for (User user : users.findAllById(ids)) {
        names.put(user.getId(), user.getName() == null ? "Traveller" : user.getName().split(" ")[0]);
    }
    return names;
}
```
- `public`: this is the users module's **API**; trips may call it (Modulith's `ModularityTest` still passes). The repository stays hidden.
- `findAllById(ids)`: Spring Data's built-in method, **one** `select ... where id in (...)`. That's the N+1 fix (37.5).
- Returns **first names only**: the full name never leaves the users module for this purpose.
- `split(" ")[0]`: the first word. The name rule (31.2) guarantees a name starts with a letter, so the first piece is never empty. Works for Hindi too: "केशव दीक्षित" → "केशव".
- `"Traveller"` if someone never filled in a name (it's nullable since V2).

### 38.4 `trips/TripService.java`: putting the stages together
```java
record MatchView(String firstName, String destArea, long minutesAfterYou, int seats, int bags, double detourKm) {}
```
- The **only** shape that leaves the server for a match: six fields. Privacy is enforced by what this record *doesn't* have (37.4).

```java
List<MatchView> matches(UUID userId, UUID tripId) {
    TripRequest mine = trips.findById(tripId)
            .filter(t -> t.getUserId().equals(userId) && t.getStatus() == TripRequest.Status.searching
                    && t.getExitAt().isAfter(Instant.now().minus(EXPIRES_AFTER)))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "No active trip with that id"));
```
- `Optional.filter`: keep the trip only if it's **mine**, still **searching**, and not stale. Missing, someone else's, cancelled or old → the same **404**, so nobody can probe other people's trip ids.

```java
    Point pickup = terminals.findById(mine.getTerminalId()).orElseThrow().getPickup();
    List<MatchRanker.Ranked> ranked = MatchRanker.rank(pickup, mine, trips.matchCandidates(tripId));
    Map<UUID, String> names = users.firstNames(ranked.stream().map(r -> r.trip().getUserId()).toList());
```
- The **imperative shell** (37.3): load from the database → stage 1 → stage 2 (pure) → names for **only the top 5**, in one call.

```java
    return ranked.stream().map(r -> new MatchView(
            names.get(r.trip().getUserId()),
            r.trip().getDestArea(),
            Duration.between(mine.getExitAt(), r.trip().getExitAt()).toMinutes(),
            r.trip().getSeats(),
            r.trip().getBags(),
            Math.round(r.detourKm() * 2) / 2.0
    )).toList();
```
- `minutesAfterYou` keeps its **sign**: +10 = they're out 10 min after you, −10 = before.
- `Math.round(x * 2) / 2.0`: round to the nearest **0.5** (1.38 → 2.76 → 3 → 1.5). Anti-trilateration (37.4).
- The constructor gets a new parameter `UserService users`: constructor injection, as everywhere else.

### 38.5 `trips/TripController.java`
```java
@GetMapping("/api/v1/trips/{id}/matches")
List<TripService.MatchView> matches(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return trips.matches(userId(jwt), id);
}
```
- The caller's id comes from the **token**, never from the request (same as `/me`). Returning a `List` gives 200 with a JSON array; an empty list is `[]`.

### 38.6 Tests
- **`MatchRankerTest`** (unit, no Spring): 8 tests in **0.04 s**.
  | Test | Proves |
  |---|---|
  | `riderFurtherDownTheSameRoadIsAGoodMatch` | worked example 37.2: detour ≈ 1.4, score ≈ 3.4 |
  | `sameDestinationHasNoDetour` | detour 0; only minutes count (15 min → 3.0) |
  | `oppositeDirectionIsRejected` | Dwarka vs DLF Phase 3 → no match |
  | `shortTripsGetAtLeast3KmAllowance` | detour 2.2 accepted (25 % alone would say 1.4), 3.2 rejected |
  | `longTripsGet25PercentAllowance` | detour 9.7 accepted (limit 10.9), 13.6 rejected (limit 11.3) |
  | `everyoneMustFitInOneCar` | 4 seats/4 bags fine; 5 seats or 5 bags rejected |
  | `lowestScoreFirstAndOnlyTheTop5` | 7 candidates → the right 5, in score order |
  | `noCandidatesNoMatches` | empty in → empty out |
  - Helper `at(kmSouth, kmEast)`: makes a destination at a chosen distance from the pickup. 1° latitude ≈ 111.19 km; 1° longitude ≈ 111.19 × cos(latitude) km (lines of longitude get closer towards the poles). That's how the boundary cases were built on purpose; each number in the test descriptions was checked separately.
  - `isCloseTo(1.4, within(0.1))`: compare decimals with a tolerance; never test `double`s for exact equality.
- **`MatchesTest`** (HTTP + database):
  - `findsTheGoodMatchAndShowsOnlyWhatOthersMaySee`: me + six others at the same time, each excluded for exactly one reason (wrong direction, over 15 km, 90 min later, other terminal, blocked me), plus Asha, who should match. Result: exactly one match, Asha, with the six allowed fields only (`$[0].length()` = 6; `phone` and `destLat` don't exist).
  - `onlyForYourOwnTrip`: someone else's trip id → 404 `TRIP_NOT_FOUND`.
  - `@BeforeEach clearTerminal1()`: cancels leftover T1 trips. The tests run against the **dev database**, and trips from earlier runs would otherwise appear as extra matches. Only T1 is cleared, and only these tests use T1.
- **Mistake caught by the test:** I first expected a 1.5 km detour, but the test uses **Terminal 1**, and my 1.4 km example was worked out from **Terminal 3**. From T1 the detour is 0.498 km → shown as 0.5. The code was right, my expectation wasn't. Always recompute expectations for the exact inputs.

### 38.7 Web: `lib/api.ts` and `app/current-trip.tsx`
- `type Match` mirrors `MatchView`; `getMatches(tripId)` = `GET /api/v1/trips/{id}/matches`.
- `Matches` component, inside the trip card:
  - `useState<Match[] | null>(null)`: `null` = still loading; a separate `failed` flag for errors.
  - `useEffect(..., [tripId])`: fetch when the card appears (or the trip changes).
  - Four states: error → "Couldn't load matches", loading → "Looking for co-travellers…", empty → "No one heading your way yet…", list → "People heading your way".
  - `key={i}`: React needs a key per list item. Normally an id, but matches deliberately have **no ids** (privacy), and the list never reorders while shown, so the position is fine.
  - `timing(10)` → "10 min after you", `timing(-5)` → "5 min before you", `timing(0)` → "at the same time as you".
  - `detourKm === 0 ? "no detour" : ...`.
  - `shortcut:` loaded once per page load; push notifications come with design §3.3 step 3.

### 38.8 Verified in a real browser (two users, one tab, one after the other)
- **A** (+919000000004, "केशव दीक्षित"): T3, out 01:17 IST, Sector 25A, Gurgaon → card shows "No one heading your way yet".
- Log out → **B** (+919000000005, new): profile "Asha Rao" with a UPI ID → T3, out 01:27, Sector 29, Gurgaon → card shows **"केशव · out 10 min before you · Sector 25A, Gurgaon · 1 km detour"**.
- Raw JSON in the network tab: `[{"firstName":"केशव","destArea":"Sector 25A, Gurgaon","minutesAfterYou":-10,"seats":1,"bags":1,"detourKm":1.0}]`. No phone, ids, coordinates or UPI ID.
- Log out → A again → **"Asha · out 10 min after you · Sector 29, Gurgaon · 1 km detour"**.
- Each `/matches` request appeared **twice** in the network log: React **StrictMode** runs effects twice in development (29.4). Harmless for a read; it doesn't happen in production.

### 38.9 `EXPLAIN` on the real query (43 rows in the dev database)
- Normal: `Seq Scan on trip_requests t` + `Hash Join`. With 43 rows, reading the whole table is cheapest, as predicted in 36.6.
- After `set enable_seqscan = off;`:
  ```
  Index Scan using trip_dest_gist on trip_requests t
    Index Cond: (dest && _st_expand(me.dest, '15000'))
    Filter: (status = 'searching' AND ... AND st_dwithin(dest, me.dest, '15000', true))
  ```
  - `&&` = "bounding boxes overlap", and `_st_expand(me.dest, 15000)` = my point grown into a 15 km box: the **R-tree box search** from 36.4. The `Filter` line is the **exact** check on the survivors.
  - `Nested Loop Anti Join` on `blocks`: an **anti join** keeps rows that have **no** partner, which is how Postgres runs `not exists`. It uses the `blocks` primary key index (`blocks_pkey`).

---

## 39. Groups: state machines and consent

### 39.1 State machines
- A **state machine** = a fixed list of **states** a thing can be in, plus the allowed **transitions** (arrows) between them. Anything not drawn is forbidden.
- **Naive way:** a `status` column that any code can set to anything. Sooner or later a bug moves a `cancelled` group back to `open`, or a `done` trip to `searching`, and nobody notices.
- Our three machines (design §3.2):
  - **Trip:** `searching → grouped` (accepted into a group) · `grouped → searching` (left before the group is locked) · `searching → cancelled / expired` · `grouped → done`.
  - **Group:** `open → locked` (someone is booking the cab: no more joins) · `open → cancelled` (last member leaves) · `locked → met → done` (later slices).
  - **Member:** `requested → accepted / declined` · `accepted → left / removed`.
- **How the database enforces it:** every transition is **one conditional `UPDATE`** that names the state it starts from:
  ```sql
  update groups set status = 'locked' where id = :id and status = 'open';
  ```
  If the group isn't `open` any more, **0 rows** change. The code checks the count and answers with an error, so an invalid transition can never sneak through. It's the same pattern as `cancel` in 35.5 (`... and status = 'searching'`).
- ⚠️ **Two meanings of "lock":** a **group lock** (status `locked`: "I'm booking the cab, no more joins") is a product feature. A **database lock** (40.4) is how Postgres stops two transactions changing the same row at once. Same word, unrelated things.

### 39.2 Consent: suggestions, not auto-assignment
- Matching (slice 1) only **suggests**. Nobody ends up in a cab with a stranger without **both sides agreeing** (design §3.3, "suggestions + consent"): safety first; auto-assignment needs trust data we don't have yet.
- **Join request** = "I'd like to share with you". It stays **pending** until the other side answers **accept** or **decline**.
- **Who answers (team decision, 2026-10-14): any accepted member** of the group, not only its creator. Faster at the airport, and a group never gets stuck because one person stopped looking at their phone.
- The newcomer consented by asking; one existing member consents by accepting. Every join needs both.
- All the group rules we chose (who locks, leaving, reasons, reports) are in the table in 41.1.

### 39.3 What a member may see
- Before joining: first name, area, timing, seats, bags (37.4).
- **After** being accepted into a group: the other members' first names, areas, seats and bags, and the booker's **UPI ID** (design: "shown only to group members"; it's needed to pay your share). Still never phone numbers or exact destinations.
- To ask someone, the app needs a **handle** for their trip: its random UUID. An **opaque id** like this isn't personal data. It tells you nothing about the person, and every endpoint that takes it checks who's asking.

---

## 40. Concurrency: the last-seat race

### 40.1 The problem
- A group: capacity 4, **3 seats taken**. Two pending requests: Chirag (1 seat) and Divya (1 seat).
- Asha accepts Chirag at the **same moment** Bilal accepts Divya. Both are allowed to (any member, 39.2). Only **one** of them may succeed.
- **Concurrency:** two pieces of work running **at the same time** and touching the same data. Every web server does this: each request runs on its own **thread** (a separate line of execution inside the same program).

### 40.2 Naive way: check, then act
```java
Group g = groups.findById(id);                 // 1. read: seatsTaken = 3
if (g.seatsTaken() + 1 > g.capacity()) throw ...;   // 2. check: 3 + 1 ≤ 4, fine
g.setSeatsTaken(g.seatsTaken() + 1);           // 3. act: write 4
```
| Time | Asha's request | Bilal's request |
|---|---|---|
| t1 | reads 3 | |
| t2 | | reads 3 |
| t3 | 3 + 1 ≤ 4 ✓ | |
| t4 | | 3 + 1 ≤ 4 ✓ |
| t5 | writes 4, Chirag accepted | |
| t6 | | writes 4, Divya accepted |
- **Five people** in a 4-seat car, and the database says 4. Bilal's write silently overwrote Asha's: a **lost update**.
- This bug class is called **check-then-act** (or **TOCTOU**, time-of-check to time-of-use): the fact you checked was already stale when you acted on it. It passes every normal test and appears only under real traffic (the same idea as the "one active trip" race in 34.4, and the double refresh in 29.4).

### 40.3 Transactions recap (ACID)
- A **transaction** groups statements so they **all happen or none do** (31.3). The four guarantees, **ACID**:
  - **Atomic:** all or nothing.
  - **Consistent:** constraints (`check`, `unique`, foreign keys) hold after every commit.
  - **Isolated:** concurrent transactions don't see each other's half-finished work.
  - **Durable:** once committed, it survives a crash.
- Accepting someone is **three changes** that must succeed together: the request becomes `accepted`, the group gets the seats, the newcomer's trip becomes `grouped`. If any one fails → **roll back** all three.
- **`@Transactional` in Spring:**
  - Rolls back automatically when the method throws an **unchecked** exception (`RuntimeException`, e.g. our `ApiException`); checked exceptions don't roll back by default.
  - Works through a **proxy**: Spring wraps the bean and starts/commits the transaction around each call **from outside**. A method calling another `@Transactional` method **in the same class** skips the proxy (**self-invocation**) and gets no transaction of its own.
  - Remember 24.4: there we deliberately *didn't* use `@Transactional`, because the "revoke all sessions" had to survive the exception. Here we *want* the rollback.
- **But "isolated" doesn't fix 40.2 on its own.** Postgres's default isolation level, **READ COMMITTED**, lets two transactions read the same "3" at the same time. Isolation hides *uncommitted* changes; it doesn't stop two transactions making decisions from the same old value.

### 40.4 The fix: one conditional `UPDATE` (design §3.4)
```sql
update groups
set seats_taken = seats_taken + :seats, bags_taken = bags_taken + :bags
where id = :group and status = 'open'
  and seats_taken + :seats <= capacity;
```
- The **check and the change are one statement**, so there's no gap between them.
- **Row lock:** when an `UPDATE` changes a row, Postgres **locks that row** until the transaction ends. A second `UPDATE` on the same row **waits**.
- When it's allowed to continue, Postgres **re-checks the `WHERE` against the new, committed row** (READ COMMITTED behaviour for updates). Bilal's update now sees `seats_taken = 4`, `4 + 1 ≤ 4` is false → **0 rows** → "the group just filled up" (409).
| Time | Asha | Bilal |
|---|---|---|
| t1 | `UPDATE` matches, locks row, 3 → 4 | |
| t2 | | `UPDATE` waits for the lock |
| t3 | commit | |
| t4 | | re-checks: 4 + 1 ≤ 4? No → 0 rows → 409 `GROUP_FULL`, roll back |
- **Last line of defence:** the table's `check (seats_taken <= capacity)` refuses any write that would overfill a group, even from buggy code.
- The same trick protects the newcomer: `update trip_requests set status = 'grouped' where id = :trip and status = 'searching'`. If two groups accept the same person at once, only one update finds the trip still `searching`.

### 40.5 Alternatives (interview favourites)
- **Pessimistic locking:** `select ... for update` locks the row **while you read it**; others wait until you commit. Then check and update in Java. Correct, but the lock is held across more code, and it's easy to forget the `for update` on one path.
- **Optimistic locking:** add a `version` column (JPA `@Version`). Every update says `where version = :old` and bumps it. If someone else changed the row first, 0 rows → `OptimisticLockException` → **retry** the whole operation. Great when conflicts are rare and the logic is complex.
- **Why the conditional `UPDATE` wins here:** the whole check-and-change fits in **one statement**: no explicit locks, no retries, no version column.

### 40.6 Deadlocks
- **Deadlock:** transaction 1 holds row A and waits for row B; transaction 2 holds B and waits for A. Neither can ever continue.
- Postgres **detects** it (after about 1 second) and kills one of them with an error; the other proceeds.
- **Prevention:** always lock rows in the **same order** in every code path. For accept: the group row first, then member rows, then trips.

### 40.7 Testing a race
- Normal tests run one request at a time, so they **never** catch races. Design §3.4 asks for this test: **10 threads accept at once into a group with 1 free seat; exactly 1 must succeed.**
- Tools from Java's standard library (`java.util.concurrent`):
  - **`ExecutorService`:** a pool of threads that run tasks you submit.
  - **`CountDownLatch`:** a gate. Every thread waits on `latch.await()`; one `countDown()` opens it, so all 10 start at the same instant (more overlap = more chance to expose a bug).
  - **`AtomicInteger`:** a counter that's safe to increase from many threads at once (a plain `int++` from 10 threads can lose increments: the same lost-update problem, in Java memory).
- It must run against **real Postgres**, because the protection lives in the database. We use the dev database, like the other integration tests; many projects use **Testcontainers** (a throwaway database in Docker per test run).
- **Prove the test can fail:** temporarily replace the conditional `UPDATE` with the naive check-then-act and confirm the test goes red. A race test that has never failed proves nothing.

---

## 41. Code walkthrough: groups (slice 2a)

### 41.1 The rules we chose (team decisions, 2026-10-14)
| Rule | Choice |
|---|---|
| What a request links | **Your trip → their trip.** The group is created only when someone says yes, so asking never hides you from other people's matches |
| Who answers | Person asked is alone → **only them**. Person asked is in a group → **any accepted member** |
| Group maker | The **first asker**. The maker is the **booker** (`groups.booker_id`) and the only one who can **lock** |
| Leaving an open group | Allowed, no reason needed. You go back to `searching` |
| Leaving a locked group | Allowed, but a **reason is required**. The group sees it as an update; if a member (not the booker) leaves, it is also filed as a **report on the booker** |
| Maker leaves | The **longest-standing** member becomes maker/booker, and a locked group **re-opens** |
| Fewer than 2 left | Group **cancelled**, the last person goes back to `searching` |
| Several requests | Allowed. When one is accepted, your other pending requests are **withdrawn** in the same transaction |
| Ask again after "no" | Not with the same trip (`unique (from_trip_id, to_trip_id)`) |
| Locking | Waiting requests to the group get **declined** |
- Slice **2b** (next): open groups appear in matches, with a 3–4 drop route in the ranker.

### 41.2 `V4__join_requests.sql`
```sql
create table join_requests (
  id           uuid primary key default gen_random_uuid(),
  from_trip_id uuid not null references trip_requests,
  to_trip_id   uuid not null references trip_requests,
  status       text not null default 'pending' check (status in ('pending','accepted','declined','withdrawn')),
  created_at   timestamptz not null default now(),
  unique (from_trip_id, to_trip_id)
);
create index join_requests_to on join_requests (to_trip_id) where status = 'pending';
```
- `references trip_requests`: a **foreign key**; a request can only point at trips that exist.
- `status text` + `check (...)`: a small state machine without a Postgres enum (same style as `messages.kind`). Text + check is easier to change later than an enum.
- `unique (from_trip_id, to_trip_id)`: the database refuses a second request from the same trip to the same trip (→ 409 `ALREADY_ASKED`).
- The partial index makes "requests waiting for this trip" fast, and stays small.
- `group_members.status = 'requested'` from V1 stays unused: requests live here instead (41.1, row 1).

### 41.3 `trips/TripService.java`: a public door for the groups module
- `public class TripService` (was package-private): the groups module needs it. Only the methods marked **public** are usable from outside; everything else stays inside `trips`.
- `public record TripSummary(id, userId, terminalId, exitAt, destArea, seats, bags, status)`: what groups may know about a trip. Not the exact destination.
- `activeTrip(userId)`: your searching or grouped trip, ignoring stale ones (same filter as `current()`).
- `summaries(ids)`: many trips in **one** query (`findAllById`, no N+1).
- `markGrouped(ids)` / `markSearching(ids)`: the trip state machine's two moves for groups:
  ```sql
  update trip_requests set status = 'grouped' where id in (:ids) and status = 'searching'
  ```
  They return **how many rows changed**; the caller compares with what it expected (40.4).
- `MatchView` gains `tripId`: the **opaque handle** used by "Ask to share" (39.3). `MatchesTest` now expects 7 fields.
- `users/UserService.upiId(id)`: public, used only to show the booker's UPI ID to members.

### 41.4 `groups/GroupService.java`: plain SQL with `JdbcClient`
- **Why not JPA here?** Almost every step is a conditional `UPDATE` whose row count matters. Spring's **`JdbcClient`** runs SQL directly with a fluent API:
  ```java
  db.sql("update ... where id = ? and status = 'pending'").param(requestId).update()   // → rows changed
  db.sql("select ...").param(x).query(Pending.class).optional()                        // → Optional<Pending>
  ```
  - `.query(SomeRecord.class)` maps columns to a record by name: `from_trip_id` → `fromTripId`.
  - `?` = positional parameter; `:name` + `.param("name", v)` = named, handy when a value appears twice.
- `returning id` / `returning trip_id`: Postgres hands back a value from the row it just inserted or updated, so there's no second query.

**`ask(userId, toTripId)`**
1. `searchingTrip(userId)`: you need a trip that's still `searching` (else 409 `NO_SEARCHING_TRIP`).
2. Their trip must exist, not be yours, be at the same terminal, and be `searching` **or** in a group that's still `open` (else 404).
3. `insert ... returning id`; `DuplicateKeyException` (the unique constraint) → 409 `ALREADY_ASKED`.

**`requests(userId)`**: two lists:
- `sent`: your requests that are `pending` or `declined`, with the target's first name.
- `received`: pending requests **to your trip, or to any accepted member of your open group**:
  ```sql
  where r.status = 'pending' and (r.to_trip_id = :trip or r.to_trip_id in (
      select m2.trip_id from group_members m
      join groups g on g.id = m.group_id and g.status = 'open'
      join group_members m2 on m2.group_id = m.group_id and m2.status = 'accepted'
      where m.trip_id = :trip and m.status = 'accepted'))
  ```
  The subquery reads: "the trips of everyone in the open group that my trip belongs to". Names and trips are then loaded in one query each.

**`accept(userId, requestId)`**: `@Transactional`, so it's all or nothing:
1. `pendingRequest(id)`: still pending? Else 404.
2. `answerableGroup(userId, to)`: the **who-answers rule**. If the person asked is in a group, the caller must be an accepted member; if they're alone, the caller must **be** them. Anyone else gets the same 404 as a missing request.
3. **Alone → the group is born:**
   - `trips.markGrouped([from, to]) != 2` → 409. Both trips in **one** statement: if Keshav and Asha asked each other and both press Accept, the two rows are locked in the same order → no deadlock (40.6).
   - `insert into groups (...) returning id` with `booker_id = from.userId()`: **the asker is the maker**. Then Asha's member row.
4. **The last-seat statement** (40.4): adds the newcomer's seats and bags only if the group is `open` and they fit; `meet_at = greatest(meet_at, :exitAt)` keeps the meeting time at the latest exit. 0 rows → 409 `GROUP_FULL`.
5. Request → `accepted` (0 rows: someone else answered first → 409).
6. Joining an existing group: the newcomer's trip `searching → grouped` (0 rows: they just joined elsewhere → 409).
7. `addMember`: `insert ... on conflict (group_id, user_id) do update ...` is an **upsert**: insert, or update if the row exists (someone who left and is accepted again).
8. Withdraw both people's other pending requests.
- Any `throw` in steps 3–8 **rolls back everything before it**. Proven by `tooBigForOneCarIsRefusedAndNothingChanges`: the group insert and both trip updates vanish.

**`decline`**: same who-answers check, then `pending → declined`.

**`lock(userId, groupId)`**: one statement does the whole permission check: `... where id = ? and status = 'open' and booker_id = ?`. Not the maker, or not open → 0 rows → 409 `CANNOT_LOCK`. Then waiting requests to the members are declined.

**`leave(userId, groupId, reason)`**: `@Transactional`:
1. `select status, booker_id ... for update`: **pessimistic lock** on the group row (40.5). We read, then make several decisions; nobody can accept, lock or leave this group until we commit.
2. Locked and no reason → 400 `REASON_REQUIRED` (checked before any write).
3. Member row `accepted → left` (`returning trip_id`); free their seats and bags; trip `grouped → searching`.
4. Remaining members, oldest first (`order by created_at, user_id`):
   - fewer than 2 → group `cancelled` (seats reset to 0), the last person `left` and back to `searching`;
   - else, if the leaver was the booker → `booker_id` = the longest-standing member, `status = 'open'` (re-opens a locked group).
5. Was locked → system message "Name left: reason"; if the leaver wasn't the booker → `insert into reports (...)`.
- `shortcut:` the groups module writes `messages` (owned by chat) and `reports` (owned by feedback) directly, because those modules don't exist yet.

**`current(userId)`**: your group: status, `youAreBooker`, the booker's UPI ID, members (first name, area, seats, bags, booker?, you?) and the 5 newest updates.

### 41.5 `groups/GroupController.java`
| Endpoint | Answer |
|---|---|
| `POST /api/v1/requests` `{toTripId}` | 201 `{id}` |
| `GET /api/v1/requests` | `{sent, received}` |
| `POST /api/v1/requests/{id}/accept` · `/decline` | 204 |
| `GET /api/v1/groups/current` | 200 or 204 |
| `POST /api/v1/groups/{id}/lock` | 204 |
| `POST /api/v1/groups/{id}/leave` `{reason?}` | 204 |
- `@RequestBody(required = false)`: leave may have no body at all (open group).
- `@Size(max = 300)` on the reason: validated before the service runs.

### 41.6 Tests: `GroupsTest` (9 tests, Terminal 2, fresh users each time)
| Test | Proves |
|---|---|
| `askThenAcceptFormsAGroupWithTheAskerAsMaker` | the group is born on accept; asker = booker; members see the booker's UPI; trips `grouped`; the asker's other request withdrawn |
| `whileAloneOnlyThePersonAskedCanAnswer` | a stranger and the asker himself get 404 |
| `anyMemberCanAnswerForTheGroup` | Neha asks Asha, **Keshav** accepts → 3 members |
| `tooBigForOneCarIsRefusedAndNothingChanges` | 3 + 3 seats → 409, and the **rollback** leaves no group and both trips `searching` |
| `onlyTheMakerLocksAndLockingDeclinesWaitingRequests` | Asha can't lock, Keshav can; Neha's request declined; locked group takes no new requests |
| `leavingALockedGroupNeedsAReasonAndReportsTheBooker` | 400 without reason; with reason: update shown, report filed, group stays locked |
| `whenTheMakerLeavesTheLongestStandingMemberTakesOverAndTheGroupReopens` | Asha becomes booker, group `open`, no report |
| `aGroupOfTwoIsCancelledWhenOneLeaves` | the last member goes back to searching |
| `lastSeatRace` | **10 threads, 1 free seat → exactly 1 success**, 9 × `GROUP_FULL`, `seats_taken = 4` |
- **The race test was proven to fail:** with the naive read-check-write code swapped in (plus a 50 ms pause to widen the gap), it went red: **10 successes instead of 1**. The real code was then restored and passed again.
- `CopyOnWriteArrayList`: a list that's safe to add to from many threads (for the failure codes).
- The threads call `groups.accept(...)` directly (not over HTTP): each call still goes through Spring's proxy, so each gets its own transaction.

### 41.7 Web
- `lib/api.ts`: types `SentRequest`, `ReceivedRequest`, `Requests`, `GroupMember`, `Group`; calls `askToShare`, `getRequests`, `answerRequest(id, "accept" | "decline")`, `getCurrentGroup`, `lockGroup`, `leaveGroup`.
- `lib/format.ts`: `plural(2, "bag")` → "2 bags", shared by both cards (its own file avoids a **circular import** between `current-trip.tsx` and `group-card.tsx`).
- `app/current-trip.tsx`:
  - Loads the trip **and** requests together with `Promise.all([...])`: both requests run at the same time.
  - `version` state + `reload()`: every action bumps the number; the effect depends on it, and `key={version}` on child cards **remounts** them, so everything refetches. Simple and always consistent.
  - Searching → matches with **Ask to share** (or "· asked"); grouped → `GroupCard`; plus "Want to share with you" (Accept/Decline) and "Your requests".
  - "Cancel trip" only while searching (a grouped trip must leave the group first).
- `app/group-card.tsx`: members with "(booker)" and "You", "Pay your share to the booker's UPI ID", updates, **Lock** (booker, open group only), the reason box once locked, **Leave**. The reason is checked in the browser for a quick message, and again by the API.
- `const g = group;` after the `if (!group) return null`: TypeScript forgets the "not null" narrowing inside nested functions; copying into a `const` keeps it.

### 41.8 Verified in a real browser (two users, one tab, one after the other)
- **Keshav** plans T3 → Sector 25A, out 11:36.
- **Asha** plans T3 → Sector 29, out 11:46 → sees Keshav with **Ask to share** → clicks → "· asked" and "Your requests: केशव · waiting for an answer".
- **Keshav** sees "Want to share with you: Asha … [Accept] [Decline]" → Accept → "Your cab group: **Asha (booker)**, You · Pay your share to the booker's UPI ID: asha.rao@okaxis". No Lock button for him, no Cancel trip.
- **Asha** sees "You (booker)" + **Lock** → locks → "locked, the booker is booking the cab".
- **Keshav** clicks Leave with an empty reason → "Tell your group why you're leaving." → with "Booker not answering" → back to searching, Asha in his matches again.
- Database: group `cancelled` (2 → 1), report केशव → Asha "Booker not answering", message "केशव left: Booker not answering", both trips `searching`.
- **Found and fixed during the test:** the reason box had no accessible name for screen readers (label now linked with `htmlFor`/`id`), and a cancelled group kept `seats_taken = 1` (now reset to 0).

---

## 42. Groups in matches: routes with several drops

### 42.1 The problem
- After slice 2a, people **in** a group vanish from matches (their trips are `grouped`). A third or fourth person can't find them, so cabs rarely fill up.
- Slice 2b: matches show **open groups with free seats** next to lone travellers, ranked together.

### 42.2 One idea for both: a traveller is a "group of one"
- Naive way: two rankers, one for travellers, one for groups. Twice the code, twice the tests, and the two can quietly disagree.
- Better: a **candidate** is a list of riders already sharing (1 for a traveller, 2–3 for a group). The ranker always asks the same question: "if I join these riders, is the route fair, and how good is it?"
- This is **generalising** a function: the pair case becomes a special case of the N case.

### 42.3 Ordering the drops: greedy, and the travelling salesman problem
- With several drops, the order matters. The **travelling salesman problem (TSP)**: find the shortest route visiting every stop. The number of possible orders grows as **n!** (n factorial): 3 drops = 6 orders, 4 = 24, 10 = 3,628,800, 20 ≈ 2.4 × 10¹⁸. No known method finds the best order quickly for large n.
- A **heuristic** is a quick rule that gives a good, not guaranteed best, answer. Ours (design §3.3) is **greedy nearest-neighbour**: from where the cab is now, always drive to the **nearest** remaining drop.
- When greedy goes wrong (all on one straight road, km from the airport): drops at 2 east, 3 west, 7 east.
  - Greedy: 2 east (2) → 3 west (5) → 7 east (10) = **17 km**.
  - Best: 3 west (3) → 2 east (5) → 7 east (5) = **13 km**.
  - Greedy zig-zagged because "nearest now" isn't "best overall".
- Why greedy is fine for us:
  - At most 4 drops, and the **fairness rule** rejects zig-zag groups anyway: those are exactly the routes where someone rides far beyond their solo trip.
  - "Nearest first" is predictable and easy to explain to riders.
  - With 4 drops, trying all 24 orders (**brute force**) would also be cheap. If greedy ever proves unfair in practice, swapping it is a change inside one pure function.

### 42.4 Fairness for every rider
- Walk the greedy route and add up each rider's **ride km** (pickup → their drop, along the route).
- **Every** rider, old members and newcomer alike, must ride at most **max(3 km, 25 % of their solo km)** beyond their solo km. Members already agreed to their current ride; a newcomer may not push any of them over the limit.
- **Detour** = route km − the **longest solo** km: how much further the cab drives than going straight to the farthest person. For two riders this is exactly the number from slice 1 (37.1), so existing pair results don't change.
- **Capacity:** existing seats + mine ≤ 4 and existing bags + mine ≤ 4.
- **Time:** a group's time is its **meet time** (`meet_at`, the latest exit of its members). Score = detour + 0.2 × |my exit − meet time| (traveller: their exit).

### 42.5 Refactoring with a safety net
- **Refactoring** = changing how code is written without changing what it does.
- The 8 existing `MatchRankerTest` tests describe the pair behaviour. After generalising the ranker they must pass **unchanged**: if one breaks, the refactor changed behaviour (a **regression**). New tests then cover groups of 2–3.

### 42.6 Privacy for group matches
- A group match shows each member's **first name and area**, the group size, free seats, the meet time difference and the detour. Never phones, exact destinations, UPI IDs or user ids.
- "Ask to share" a group uses the opaque handle of **one member's trip**; the slice-2a rule already routes it to the whole group (any member answers).

---

## 43. Module dependencies: cycles and dependency inversion

### 43.1 The problem: a cycle
- Matching lives in `trips`. To list groups, it needs data the `groups` module owns. But `groups` already depends on `trips` (it moves trips to `grouped`).
- `trips → groups` **and** `groups → trips` = a **dependency cycle**. Spring Modulith's `ModularityTest` fails on cycles, and for good reason:
  - Neither module can be understood, tested or changed on its own; they've become one big module in disguise.
  - A cycle is the first step back to a "big ball of mud".

### 43.2 Naive fixes and why they hurt
- **Merge the modules:** the cycle disappears, and so do the boundaries.
- **Let `trips` read the `groups` tables with SQL:** `ModularityTest` can't see SQL, so it passes, but it's the same cycle, just hidden. Change a groups column and matching breaks with no warning.

### 43.3 Dependency inversion
- **Dependency Inversion Principle** (the "D" in **SOLID**): high-level code should depend on an **abstraction** (an interface) that **it** defines, not on the concrete code that provides the data.
- In practice:
  1. `trips` declares an **interface** saying what it needs: "give me the open groups near this trip".
  2. `groups` **implements** that interface.
  3. Spring finds the implementation and **injects** it into `TripService` (constructor injection, as everywhere).
- Arrows now: `groups → trips` (it implements a trips interface). `trips` only knows its own interface. **No cycle.**
- **Ports and adapters** (hexagonal architecture) names the same idea: the interface is a **port** (a socket `trips` defines), the implementation an **adapter** (the plug `groups` provides).
- Sketch:
  ```java
  // in trips: what matching needs, in its own words
  public interface OpenGroups {
      List<OpenGroup> near(UUID userId, String terminalId, Instant exitAt, int seats, int bags);
      record OpenGroup(UUID handleTripId, Instant meetAt, List<UUID> memberTripIds) {}
  }

  // in groups: the implementation, using groups' own tables
  @Component
  class OpenGroupsQuery implements OpenGroups { ... }
  ```
- Bonus: in a unit test, `OpenGroups` can be replaced with a tiny fake that returns fixed groups, with no database at all.

---

## 44. Code walkthrough: groups in matches (slice 2b)

### 44.1 `trips/MatchRanker.java`: from pairs to N riders
```java
record Ranked(List<TripRequest> riders, double detourKm, double score) {}

static List<Ranked> rank(Point pickup, TripRequest mine, List<List<TripRequest>> candidates)
```
- A candidate is now a **list of riders** already sharing: `List.of(asha)` for a lone traveller, `List.of(asha, keshav)` for a group (42.2).
- For each candidate: `everyone` = their riders + me.
  - **Capacity:** `everyone.stream().mapToInt(TripRequest::getSeats).sum() > CAR_SEATS` → skip (same for bags).
  - **Fairness + detour:** `fairDetour(pickup, everyone)` (below). Empty → skip.
  - **Their time:** the latest exit among the riders = the group's meet time (a traveller: their own exit). `max(Comparator.naturalOrder())` picks the latest `Instant`.
  - Score = detour + 0.2 × |minutes| (unchanged).

```java
private static OptionalDouble fairDetour(Point pickup, List<TripRequest> everyone) {
    List<TripRequest> left = new ArrayList<>(everyone);
    Point at = pickup;
    double routeKm = 0;
    double longestSolo = 0;
    while (!left.isEmpty()) {
        Point from = at;
        TripRequest next = left.stream().min(Comparator.comparingDouble(t -> roadKm(from, t.getDest()))).orElseThrow();
        routeKm += roadKm(at, next.getDest());
        double solo = roadKm(pickup, next.getDest());
        if (routeKm - solo > Math.max(MIN_ALLOWANCE_KM, ALLOWANCE * solo)) {
            return OptionalDouble.empty();
        }
        longestSolo = Math.max(longestSolo, solo);
        at = next.getDest();
        left.remove(next);
    }
    return OptionalDouble.of(routeKm - longestSolo);
}
```
- **The greedy loop** (42.3): `left` = riders still in the cab; `at` = where the cab is. Each turn: pick the **nearest** remaining drop, drive there (`routeKm +=`), that rider gets out.
- `Point from = at;`: a lambda may only use variables that never change ("effectively final"); `at` changes every turn, so the lambda gets a fresh copy.
- **Fairness for every rider** (42.4): `routeKm − solo` is how much further this rider rode than going straight. One rider over the limit → the whole candidate is rejected.
- **`OptionalDouble`**: Java's box for "a number, or nothing", clearer than returning a `Double` that might be `null`.
- With two riders this computes exactly what the pair code did: the first rider rides their solo (extra 0), the second rides the whole route.

### 44.2 The safety net held
- The 8 old `MatchRankerTest` tests got one mechanical change: travellers are wrapped by a helper `alone(asha)` = `List.of(List.of(asha))`. **Every expected number stayed the same, and all 8 passed**: the refactor changed no behaviour (42.5).
- 4 new tests:
  | Test | Proves |
  |---|---|
  | `joiningAGroupHeadingTheSameWay` | me (DLF Phase 3) + group (Cyber City, Sector 29): accepted, detour ≈ 1.7 km |
  | `aNewcomerMayNotPushAMemberOverTheirLimit` | group on one straight road (detour 0); I'm nearest so I ride 0 km extra, but the first member would ride 4.05 km extra (limit 3) → rejected |
  | `theWholeCarMustFitMe` | 1 + 2 + 1 seats fine; 1 + 2 + 2 seats or 5 bags rejected |
  | `aGroupsTimeIsItsLastMembersExit` | members out at 0 and +20 min, me at 0 → score 4.0 (only the 20 minutes) |
- Expected numbers were computed **separately in Python** before writing the tests, so the tests don't just repeat the code's own arithmetic.

### 44.3 `trips/OpenGroups.java`: the port
```java
public interface OpenGroups {
    List<List<UUID>> near(UUID userId, String terminalId, Instant exitAt);
}
```
- Lives in `trips`, written in trips' words: "open groups near this trip, as lists of member trip ids" (43.3).
- `trips` never imports anything from `groups`. `ModularityTest` still passes: **no cycle**.

### 44.4 `groups/OpenGroupsQuery.java`: the adapter
- `@Component class OpenGroupsQuery implements OpenGroups`: Spring sees the only implementation of the interface and injects it wherever `OpenGroups` is asked for.
- SQL: open groups at the terminal, `meet_at` within ±30 min, joined with their accepted members, **no blocks** between me and any member (`not exists`), ordered by group then oldest member first.
- `Collectors.groupingBy(Row::groupId, LinkedHashMap::new, Collectors.mapping(Row::tripId, toList()))`: turns rows `(group, trip)` into one list of trips per group. `LinkedHashMap` keeps the row order (a plain `HashMap` would shuffle it).
- No 15 km prefilter for groups: there are only a handful of open groups per terminal per hour, and the ranker's fairness rule rejects the ones going the wrong way.

### 44.5 `trips/TripService.java`
```java
List<List<TripRequest>> candidates = new ArrayList<>();
trips.matchCandidates(tripId).forEach(t -> candidates.add(List.of(t)));            // travellers, as groups of one
List<List<UUID>> groups = openGroups.near(userId, mine.getTerminalId(), mine.getExitAt());
Map<UUID, TripRequest> members = trips.findAllById(groups.stream().flatMap(List::stream).toList()).stream()
        .collect(Collectors.toMap(TripRequest::getId, Function.identity()));       // ALL members, one query
groups.forEach(g -> candidates.add(g.stream().map(members::get).toList()));
```
- `flatMap(List::stream)`: flattens a list of lists into one stream of ids, so all group members load in **one** query (no N+1).
- Names for every rider of every ranked match: one `users.firstNames(...)` call.
- New response shape:
  ```java
  record Rider(UUID tripId, String firstName, String destArea) {}
  record MatchView(UUID tripId, List<Rider> riders, long minutesAfterYou, int seats, int bags, double detourKm) {}
  ```
  `tripId` = the first rider's trip (the handle "Ask to share" sends; any member can answer). `seats`/`bags` = what the riders already take. `minutesAfterYou` = vs the group's meet time.
- The constructor now also takes `OpenGroups openGroups`: dependency inversion in action (43.3).
- `groups/GroupService.RequestView` gains `fromTripId`, so the web can tell "this person already asked you".

### 44.6 Tests: `MatchesTest`
- Updated for the new shape: `$[0].riders[0].firstName`; 6 top-level fields, 3 per rider.
- `showsAnOpenGroupHeadingMyWay`: Asha asks Neha over HTTP, Neha accepts; I plan DLF Phase 3 → one match with **2 riders**, seats 2, "meeting 5 min after you" (Neha is out last).
- `clearTerminal1` now also cancels leftover T1 trips in `grouped` and open T1 groups from earlier runs.

### 44.7 Web: `app/current-trip.tsx`
- `Match` = `{tripId, riders[], minutesAfterYou, seats, bags, detourKm}`; `Rider` = `{tripId, firstName, destArea}`.
- Group line: `Asha (Sector 29, Gurgaon) + केशव (Sector 25A, Gurgaon) · group of 2 · 2 seats free · meeting 5 min after you · 1.5 km detour`. Each area sits next to its person (a joined list of areas would be ambiguous, since every area itself contains a comma).
- `CAR_SEATS = 4` on the web, to show "seats free". It's the same number the API uses; if the car size ever changes, both places change.
- Buttons per match: `· asked` (you asked any rider) / `· asked you (answer below)` (any rider asked you) / **Ask to share**.

### 44.8 Verified in a real browser (three users)
- Setup through the API with real OTP logins (a small Python script: `/auth/otp`, read the code from the log, `/auth/verify`, `/trips`, `/requests`): Keshav → Sector 25A at 12:07, Asha → Sector 29 at 12:17, Asha asks Keshav.
- Browser, **Keshav**: Asha's match shows "· asked you (answer below)" instead of a button → Accept → group of 2, Asha booker.
- Browser, **Neha** (+919000000006, new user → profile "Neha Gupta") → Cyber City at 12:12 → matches show **"Asha (Sector 29, Gurgaon) + केशव (Sector 25A, Gurgaon) · group of 2 · 2 seats free · meeting 5 min after you · 1.5 km detour"** → Ask to share → "· asked", "Your requests: Asha · waiting for an answer".
- Browser, **Keshav** (who was **not** the one asked): "Want to share with you: Neha" → Accept → **group of 3**.
- Database: one open group, `seats_taken = 3`, 3 accepted members, all three trips `grouped`.
- Noticed: Neha's "Your requests" says "Asha" because the handle is Asha's trip, though the whole group answers. A wording polish for later.

---

## 45. Real-time: WebSocket and STOMP

### 45.1 Why plain HTTP isn't enough
- HTTP is **request/response**: the browser asks, the server answers. The server **can't speak first**.
- **Naive way: polling.** The browser asks "anything new?" every 2 seconds. 4 people × 30 requests/minute = 120 requests a minute, almost all answered "nothing", draining batteries and data. And a message can still wait up to 2 s (**latency** = the delay between something happening and you seeing it).
- What chat needs: **server push**, where the server sends the moment a message exists.

### 45.2 WebSocket
- One long-lived, **two-way (full-duplex)** connection: either side can send at any time.
- **Handshake:** a normal HTTP request with `Upgrade: websocket` → the server answers **`101 Switching Protocols`** → from then on, the same connection carries small **frames** both ways.
- `ws://` = plain, `wss://` = encrypted (like http/https). Production uses `wss://`.
- **Connections drop** (airport Wi-Fi, phones switching networks): the client must **reconnect** and catch up on what it missed.

### 45.3 STOMP: addresses on top of the pipe
- A WebSocket is just a pipe of bytes. **STOMP** (Simple Text Oriented Messaging Protocol) adds text **frames** with **destinations**:
  - `CONNECT` (who I am) · `SUBSCRIBE /topic/groups.42.chat` (send me everything posted here) · `SEND /app/groups/42/chat` (here's a message) · `MESSAGE` (what subscribers receive).
- **Publish/subscribe (pub/sub):** senders publish to a topic; everyone subscribed gets a copy. Senders don't know who's listening.
- **In Spring:** `/app/...` frames go to our `@MessageMapping` method → it saves the message and publishes to `/topic/...` → the **message broker** delivers it to every subscriber.
- `shortcut:` Spring's **simple broker** keeps subscriptions in memory, so it works with **one** server only. With two servers: a **broker relay** (RabbitMQ) or Redis pub/sub (design §3.6).

---

## 46. Chat: security, saving and history

### 46.1 Authentication: the token goes in the CONNECT frame
- The browser's WebSocket API **can't add custom headers** like `Authorization`. Cookies would be sent, but our access token lives in memory (24.6).
- So the JWT travels **inside the STOMP `CONNECT` frame**. A Spring **`ChannelInterceptor`** (a checkpoint every incoming frame passes) validates it with our existing JWT decoder and attaches the user to the connection.

### 46.2 Authorization: never trust a client-chosen destination
- Logging in isn't enough: without a check, any user could `SUBSCRIBE /topic/groups.<someone-else>.chat` and read strangers' messages.
- On every **`SUBSCRIBE`** and **`SEND`**, the interceptor asks: "is this user an accepted member of this group, and is the group open or locked?" If not → the frame is refused.
- **CSWSH** (cross-site WebSocket hijacking): an evil site opens a WebSocket to *our* server riding on the victim's cookies. We're not exposed (the token isn't a cookie), and we still only allow our web origin.
- **XSS:** messages are a stranger's text. React **escapes** text by default (`<script>` shows as characters); never `dangerouslySetInnerHTML`. The database caps a message at 1000 characters.

### 46.3 Save first, then broadcast
- Insert the row first (the database gives it an `id`), **then** publish. If broadcasting fails, the message is still safe and shows up in history.
- **Optimistic UI:** your message appears instantly (greyed out) before the server confirms. It carries a random **`clientId`**; when the server's copy arrives with the same `clientId`, it replaces the grey one instead of appearing twice (**deduplication**).
- System messages ("Asha left: …") are rows with `kind = 'system'`, so they show in the chat for free.

### 46.4 History: cursor pagination
- **Naive way: `OFFSET`** ("skip 50, give me 50"): it **shifts** when new messages arrive (page 2 repeats messages you've seen), and it's **slow** for big offsets (the database still walks past every skipped row).
- **Cursor (keyset) pagination:** "50 messages **older than id 1234**": `where group_id = ? and id < 1234 order by id desc limit 50`. Stable (new messages don't move old ones) and fast (jumps straight there via the index `messages_by_group (group_id, id)`).
- After a reconnect, the same idea the other way: "everything **newer than** the last id I have".

### 46.5 Scope of slice 3 (team decisions, 2026-10-14)
| Decision | Choice |
|---|---|
| Who can chat | Accepted members while the group is open or locked; leaving removes access immediately |
| Live group updates | Yes: a small "group changed" event (`/topic/groups.{id}.updated`) so everyone's card refreshes on its own |
| Not in this slice | Live location sharing (needs its own privacy lesson) and push notifications |

---

## 47. Code walkthrough: group chat (slice 3)

### 47.1 The shape
- New **`chat`** module (design §2.2 says chat owns `messages`): `ChatService`, `ChatController`, `StompAuth`, `WebSocketConfig`.
- Dependencies: `chat → groups` (membership check + the `GroupChanged` event) and `chat → users` (first names). `groups` knows nothing about `chat`: **no cycle**.
- `pom.xml`: `spring-boot-starter-websocket` (+ `-websocket-test`). Names checked against Boot 4.1.1's own dependency list first.

### 47.2 Events between modules: `groups/GroupChanged.java`
```java
public record GroupChanged(UUID groupId, String systemMessage) {}
```
- `GroupService` now calls `events.publishEvent(new GroupChanged(...))` after ask (to a group), accept ("Asha joined"), decline, lock ("Asha locked the group and is booking the cab") and leave ("Asha left" / "Asha left: reason").
- **Why an event?** groups must tell chat "post this system message", but chat already depends on groups (membership). A direct call would be a **cycle** (43.1). With an event, groups just announces; whoever cares listens. That's a second way (besides dependency inversion, 43.3) to keep module arrows one-way.
- Groups no longer inserts into `messages` itself, so last slice's `shortcut:` for messages is gone (the `reports` one stays).
- `GroupView.updates` was removed: the chat now shows those system messages.
- `public boolean isActiveMember(groupId, userId)`: the one public method chat uses. "Accepted member of an open or locked group." It replaced a private `isMember`, so there's one rule, not two.

### 47.3 `chat/ChatService.java`
- `MessageView(id, kind, firstName, body, createdAt, clientId, you)`.
  - `firstName` is null for system messages.
  - `clientId` is only echoed on the **live** copy (for deduplication); it isn't stored.
  - `you` is filled in for **history** (the server knows who's asking). For live messages everyone receives the same copy, so the sender's browser recognises its own `clientId` instead.
- **`history(userId, groupId, before, after)`**, cursor pagination (46.4):
  - `after` set: `where id > :after order by id limit 200`, catching up after a reconnect (`shortcut:` 200 is plenty for four people).
  - otherwise: `where id < :before order by id desc limit 50`, then reversed so the page reads oldest → newest. No `before` = `Long.MAX_VALUE` = "the latest 50".
- **`post(...)`**: membership check → trim and check 1–1000 characters → `insert ... returning id, user_id, kind, body, created_at` → `broker.convertAndSend("/topic/groups.<id>.chat", view)`. **Save first, then broadcast** (46.3).
  - `SimpMessagingTemplate` = Spring's "publish this to a broker destination" helper.
- **`onGroupChanged(GroupChanged)`**:
  ```java
  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  ```
  - `@TransactionalEventListener`: runs **after the groups transaction commits**, so nobody hears about a change that then rolled back. `fallbackExecution = true`: also run when there was no transaction (decline, ask).
  - **The trap avoided:** code that runs "after commit" still sits inside the finished transaction; database writes there are **never committed**. `REQUIRES_NEW` starts a fresh transaction for the system-message insert (Spring's docs warn about exactly this).
  - Then it publishes `{"groupId": ...}` to `/topic/groups.<id>.updated`: just "something changed, refetch", no details, so nothing private can leak.
  - `shortcut:` if the app crashes between the commit and the listener, the system message is lost; Spring Modulith's event publication registry (an **outbox**: events saved in the same transaction and retried) would fix that.
  - **⚠️ Replaced in 51.5:** this design needed a second database connection per change and starved the connection pool under load. The listener now saves inside the same transaction and only broadcasts after commit.
- `record Updated(UUID groupId)`: `convertAndSend(dest, Map.of(...))` was **ambiguous** (there's an overload taking headers as a Map), and a small record is clearer anyway.

### 47.4 `chat/StompAuth.java`: the checkpoint (46.1–46.2)
- Implements `ChannelInterceptor.preSend`, which runs for **every** frame the browser sends. Throwing refuses the frame: Spring sends an `ERROR` frame and closes the connection (the `ERROR ... Failed to send message` log line is this, as expected).
- `CONNECT`: reads the `Authorization: Bearer ...` STOMP header → `jwts.decode(...)` (the **same** decoder as HTTP: signature, expiry, issuer) → `stomp.setUser(new JwtAuthenticationToken(jwt))`. Spring remembers this user for every later frame on the connection.
- `SUBSCRIBE`: the destination must match `/topic/groups.<uuid>.(chat|updated)` **and** the user must be an active member → else refused. "Never trust a client-chosen destination."
- `SEND`: only to `/app/...`. Without this, a client could send **straight onto** `/topic/groups.<id>.chat` and the broker would deliver a fake message that skipped saving and the membership check.
- `Pattern.compile("/topic/groups\\.([0-9a-f-]{36})\\.(chat|updated)")`: a regular expression; `\\.` is a literal dot, `([0-9a-f-]{36})` captures the UUID (`topic.group(1)`).

### 47.5 `chat/WebSocketConfig.java` and `auth/SecurityConfig.java`
- `@EnableWebSocketMessageBroker`: turns on STOMP over WebSocket.
- `addEndpoint("/ws").setAllowedOrigins(webOrigin)`: the handshake URL; only our web app's origin may open a socket.
- `enableSimpleBroker("/topic")` (`shortcut:` one instance only, 45.3) + `setApplicationDestinationPrefixes("/app")`.
- `configureClientInboundChannel(...)`: plugs `StompAuth` in.
- `SecurityConfig`: `/ws` is `permitAll()` at the HTTP level, because the handshake can't carry our token; the real check happens in `CONNECT`.

### 47.6 `chat/ChatController.java`: one controller, two doors
- `GET /api/v1/groups/{id}/messages?before=` / `?after=`: history over HTTP.
- `@MessageMapping("/groups/{groupId}/chat")`: handles `SEND /app/groups/<id>/chat` with `{"body", "clientId"}`. `@DestinationVariable` reads `{groupId}` from the destination; `Principal user` is the user set at CONNECT.
- `shortcut:` a refused STOMP message (not a member, empty text) is only logged; the browser checks the length itself.

### 47.7 Tests: `ChatTest` (6 tests, real WebSocket on a random port)
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`: starts the real server; `@LocalServerPort` gives its port.
- `WebSocketStompClient` + `StandardWebSocketClient` + `JacksonJsonMessageConverter`: a Java STOMP client. Real tokens are signed with the app's `JwtEncoder`, same issuer as `AccessTokens`.
- `subscribe(...)` waits until `SimpUserRegistry` shows the subscription, so a message can't slip past before we're listening (no flaky `sleep`).
| Test | Proves |
|---|---|
| `membersGetMessagesLiveAndHistoryOldestFirst` | live delivery (trimmed body, first name, clientId echoed); history oldest-first with the "joined" system message; `you` right for each person |
| `outsidersCanNeitherListenNorRead` | a logged-in stranger's SUBSCRIBE gets nothing; history → 404 |
| `connectingWithoutATokenIsRefused` | CONNECT without a token never succeeds |
| `sendingStraightOntoATopicIsRefused` | a fake message sent to `/topic/...` never arrives |
| `groupChangesAreAnnouncedAndLeavingEndsAccess` | a new request reaches members on `.updated`; after leaving, history → 404 |
| `historyPagesWithACursor` | 55 messages: latest 50, `before` → the 6 older, `after` → the 49 newer |
- **Proven to fail:** with the SUBSCRIBE and SEND checks switched off, the two security tests went **red** (the stranger read "secret plan"; the fake message arrived). Restored, green again.

### 47.8 Web
- `@stomp/stompjs` **7.3.0**, pinned exactly. 7.4.0 was published only 3 days earlier; brand-new releases are a supply-chain risk, so we took the version that's been out since January.
- `lib/api.ts`:
  - `freshAccessToken()`: decodes the JWT's `exp` (the payload is base64url JSON, readable by anyone, 24.2) and refreshes if under a minute is left. The socket can't "refresh and retry" like `request()` does.
  - `connectGroup(groupId, {message, updated, connected, disconnected})`: a STOMP `Client` with `brokerURL` = API URL with `http` → `ws`; `reconnectDelay: 3000`; `beforeConnect` puts a fresh token in the CONNECT headers **before every (re)connect**; `onConnect` subscribes to `.chat` and `.updated`.
  - `sendChat(...)`: `publish` to `/app/groups/<id>/chat` with `content-type: application/json`.
  - `getMessages(groupId, {before} | {after})`.
- `app/group-chat.tsx`:
  - `merge(lines, incoming, mine)`: drops duplicates by `id`, replaces my grey pending line when the server's copy carries the same `clientId`, keeps confirmed lines in id order with pending ones last.
  - On every **connect**: subscribe first, **then** fetch (first time: latest page; after a reconnect: `after = newestId`), so nothing falls in the gap; duplicates are merged away.
  - **Send:** `crypto.randomUUID()` → remember it in `mine` → show a grey "sending…" line → publish.
  - "Show older messages" when the first page was full (50).
  - Text is rendered as React text, so it's **escaped** (no `dangerouslySetInnerHTML`). The `<label>` is visually hidden (`sr-only`) but linked with `htmlFor`.
  - `latestOnUpdated` ref: the newest callback without reconnecting (same trick as `latestOnPick` in 35.12).
- `app/group-card.tsx`: `version` is now a **prop**, not a `key`, so the card refetches on changes but isn't re-created, and the chat connection stays open. `<GroupChat key={g.id} ...>` reconnects only if the group itself changes.
- `lib/time.ts`: `formatIstTime(iso)` → "12:07 pm".

### 47.9 Verified in a real browser (two tabs at once)
- Setup through the API (real OTP logins): Keshav and Asha's trips, Asha asks, Keshav accepts.
- Tab 1 **Keshav**, tab 2 **Asha** (each tab keeps its own in-memory access token; fine for a test under 15 minutes).
- Both cards: "Group chat" with "Asha joined"; no "reconnecting…".
- Asha sends "Landed! At pillar 12" → her tab shows it **once** (pending replaced) → Keshav's tab shows "Asha: Landed! At pillar 12" **without reload**.
- Keshav sends `Coming <b>now</b> <img src=x onerror=alert(1)>` → Asha sees it as plain text; 0 `<img>`, 0 `<b>` elements: **XSS-safe**.
- Asha locks → Keshav's card turns "locked" on its own, the reason box appears, and the chat shows "Asha locked the group and is booking the cab".
- Not tested in the browser: a real network drop and reconnect (the catch-up query is covered by `historyPagesWithACursor`).

### 47.10 Found while testing: two Redis servers on one laptop
- The OTP limits wouldn't clear. The reason: **three** things listen on port 6379 here: a **Windows "Redis" service** (5.0.14, `C:\Program Files\Redis`, starts automatically, bound to `127.0.0.1`), plus Docker's Redis (7.4, reachable on `::1`).
- The API connects to `localhost` → `127.0.0.1` → the **Windows** Redis, not the project's Docker one. It works (same protocol), but it isn't the setup the project documents, and that Redis also holds another project's keys.
- Lesson: when "it should be cleared but isn't", check **which server** you're actually talking to (`Get-NetTCPConnection -LocalPort 6379` lists the listeners).

---

## 48. Background jobs: sweeps, idempotency, time

### 48.1 The problem
- Some changes must happen **because time passed**, not because someone clicked: expire trips an hour after their exit time, lock a group 5 minutes before meeting, close groups long after meeting.
- **Lazy expiry** (what we had): when *you* create a trip, *your* stale trips expire. Everyone else's stale data stays, and "lock 5 minutes before" can't be lazy: no request arrives at that moment.

### 48.2 Background jobs: two shapes
- A **background job** = code the server runs on its own, on a timer, with no HTTP request behind it.
| Shape | How it works | Example |
|---|---|---|
| **Sweep** (periodic) | Every minute: find **everything** that's due, handle it in one go | `update trip_requests set status='expired' where status='searching' and exit_at < now() - interval '60 minutes'` |
| **Delayed task** (per item) | On create: "do X to *this* item at time T" | "expire trip 42 at 13:15" |

### 48.3 Tools and their traps
- **Spring `@Scheduled`**: runs a method on a timer (`fixedDelay = 60_000` = every 60 s after the previous run finished). Needs `@EnableScheduling` once. Traps:
  1. Timers live **in memory**: a delayed task held in memory is **lost on restart**.
  2. **Every server runs it**: two API instances = two runs every minute.
- **db-scheduler** (the design's choice): stores delayed tasks in a `scheduled_tasks` table, so they survive restarts and only one server takes each. Costs a library, a table, and keeping tasks in sync (e.g. rescheduling on a flight delay).
- **Why our sweep avoids both traps:**
  - The truth ("this trip's exit was 2 hours ago") lives **in the rows**, so after a restart the next sweep simply finds it.
  - Every change is a **conditional UPDATE** (`... where status = 'open' and ...`): if two servers sweep at once, one changes the rows and the other changes **0**. With `UPDATE ... RETURNING id`, only the server that actually changed a group announces it.
- **Decision (2026-10-14):** a 1-minute `@Scheduled` sweep, no new library. db-scheduler stays an option for per-item tasks later (flight-delay reschedules).

### 48.4 Idempotency
- **Idempotent** = running it twice has the same effect as once. "Set expired **where still searching**" is idempotent; "add 1 to a counter" isn't.
- Jobs **will** run twice someday (retries, two servers, a crash halfway), so every job must be idempotent.

### 48.5 Time and testing
- Each server's clock drifts a little (**clock skew**). Comparing with the **database's `now()`** inside the SQL gives everyone one clock.
- **Testing time-based code:** never wait an hour in a test. Move the data into the past (`set meet_at = now() - interval '5 hours'`) and **call the sweep method directly**.
- **Precision:** a 1-minute sweep locks "5 minutes before" somewhere between T−5:00 and T−4:00. Fine for a cab.

### 48.6 What our sweep does (decisions, 2026-10-14)
| Job | Rule | Then |
|---|---|---|
| Expire trips | `searching` and exit time more than 60 min ago → `expired` | Replaces the lazy shortcut for everyone |
| Expire unformed groups | `open` and meet time more than 30 min ago → `expired` | Members' trips → `expired` (runs **before** auto-lock, so a group missed while the server was down isn't locked after the fact) |
| Auto-lock | `open` and meet time within 5 min → `locked` | Waiting requests declined; chat: "Locked automatically: meeting in 5 minutes" |
| Finish | `locked` and meet time more than 4 hours ago → `done` | Members' trips → `done` (design §3.2 "or meet_at + 4 h") |

---

## 49. Code walkthrough: expiry jobs

No new library, no new table: one `@Scheduled` sweep per module (48.3).

### 49.1 `ApiApplication.java`
- `@EnableScheduling`: switches on Spring's scheduler. Without it, `@Scheduled` methods are silently ignored.

### 49.2 Trips: `TripRepository` + `TripService`
```sql
update trip_requests set status = 'expired'
where status = 'searching' and exit_at < now() - interval '60 minutes'
```
- `expireAllStale()`: everyone's stale searching trips, judged by the **database's** clock (48.5).
- `TripService.expireStaleTrips()` with `@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)`:
  - `fixedDelay`: wait 60 s **after the previous run finishes** (runs never overlap on one server).
  - `initialDelay`: first run one minute after startup.
  - It's a single statement, and the repository method is already `@Transactional`, so it can sit right in `TripService`.
- The per-user `expireStale` in `create` stays: it clears your own forgotten trip **immediately**, instead of up to a minute later (the old `shortcut:` comment was reworded).
- New public `markDone(ids)` / `markExpired(ids)`: `grouped → done` / `grouped → expired`, for the groups module.

### 49.3 Groups: three jobs in `GroupService`
Each is `@Transactional` and starts with a conditional `UPDATE ... RETURNING id`, so **only groups this run actually changed** get follow-ups and announcements (48.3–48.4).
| Method | Statement | Then |
|---|---|---|
| `expireUnformed()` | `open` and `meet_at < now() - interval '30 minutes'` → `expired` | members' trips → `expired`; `GroupChanged` (cards refresh) |
| `autoLock()` | `open` and `meet_at <= now() + interval '5 minutes'` → `locked` | waiting requests declined; `GroupChanged(id, "Locked automatically: meeting in 5 minutes")` |
| `finish()` | `locked` and `meet_at < now() - interval '4 hours'` → `done` | members' trips → `done`; `GroupChanged` |
- `memberTrips(groupIds)`: `where group_id in (:ids)`: a **list parameter** that `JdbcClient` expands into `in (?, ?, ...)`.
- `declineWaitingRequests(groupId)`: pulled out of `lock()`, now shared by the manual lock and the auto-lock (one rule, one place).

### 49.4 `GroupJobs.java`: why a separate class
```java
@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
void sweep() {
    groups.expireUnformed();
    groups.autoLock();
    groups.finish();
}
```
- **Self-invocation (40.3):** if `sweep()` lived inside `GroupService`, calling `this.autoLock()` would skip Spring's proxy, and the `@Transactional` on `autoLock` would be ignored. From a separate bean, each call goes through the proxy and gets **its own transaction**.
- **Order matters:** `expireUnformed` runs **first**. A group whose meeting passed an hour ago (the server was down) expires, instead of being "auto-locked" an hour late.

### 49.5 Tests (time travel without waiting, 48.5)
| Test | Proves |
|---|---|
| `TripsTest.theSweepExpiresEveryonesStaleTrips` | a trip moved 2 h into the past expires for **any** user; a fresh trip stays `searching` |
| `anOpenGroupLocksItselfFiveMinutesBeforeMeeting` | meeting in 3 min → `locked`, Neha's waiting request `declined`, exactly **one** "Locked automatically" message after running the sweep **twice**; a group meeting in 3 h stays `open` |
| `aLockedGroupIsDoneFourHoursAfterMeeting` | `done`, trips `done`, and you're free to plan a new trip (current → 204) |
| `aForgottenOpenGroupExpiresInsteadOfLocking` | 1 h past meeting and still open → `expired`, not locked |
- **Idempotency proven:** with the auto-lock's `status = 'open'` condition loosened to `status in ('open', 'locked')`, the test went **red** ("expected: 1 but was: 2" messages). Restored → green.

### 49.6 Verified in the running app
- Real login as Keshav (group with Asha, set up through the API) → in the database, the group's `meet_at` moved to 3 minutes from now → **no clicks** → within a minute, Keshav's open page showed "locked, the booker is booking the cab", and the chat said "Locked automatically: meeting in 5 minutes 12:22 pm".
- This proves the **timer itself** runs in the real app; the tests call the sweep directly.

### 49.7 The Redis fix
- The Windows "Redis" service is stopped and set to Manual (47.10). The API now uses the project's Docker Redis 7.4 as designed; `OtpServiceTest` passing confirmed the connection.

---

## 50. Money: fare split and UPI links

### 50.1 Finishing the ride: `met` and the fare
- The group state machine (39.1) continues: `locked → met` when **2 or more** members tap **"I'm here"** at the pickup; then the **booker** enters the real fare after the ride.
- Each step is one conditional `UPDATE` naming its starting state (`... where status = 'met' and fare_total is null`), so the fare can't be entered twice: **idempotent** (48.4).
- **Golden rule:** Namastute never touches the money. It does the arithmetic and hands out a UPI link.

### 50.2 Never `double` for money
- `double` is **binary floating point**: most decimal fractions can't be stored exactly (like 1/3 in decimal). `0.1 + 0.2` = `0.30000000000000004` in Java and JavaScript.
- Java: **`BigDecimal`** (exact decimals; project rule "money as BigDecimal"). Postgres: **`numeric(10,2)`** (exact; `groups.fare_total`, `group_members.share`).
- JavaScript numbers are doubles, but **whole numbers** are exact (up to 2⁵³), so whole-rupee shares are safe in the browser.
- **Minor units:** some systems store paise as integers (₹354.50 → 35450): another way around floating point.

### 50.3 The split and rounding
- **Rule (design §3.8):** share = fare × your solo km ÷ everyone's solo km. The person going furthest pays most.
- Example: fare ₹600; Asha 14.0 km, Keshav 9.7 km (total 23.7): Asha 600 × 14.0 / 23.7 = 354.43 → **₹354**; Keshav (booker) = 600 − 354 = **₹246**.
- **Why the booker takes "the rest":** rounding each share separately can make the total ₹1 off (three people: 199.5 + 199.5 + 201 → 200 + 200 + 201 = ₹601). Everyone except the booker is rounded; booker = fare − the others, so the total is always exact.
- **Rounding modes:** `HALF_UP` (.5 goes up, as at school), `HALF_EVEN` (banker's rounding: .5 goes to the even neighbour, no bias over many sums), `FLOOR` / `CEILING` (always down / up).

### 50.4 UPI deep links
- **UPI** (Unified Payments Interface): India's instant bank-to-bank payments. People are addressed by a **UPI ID / VPA** (virtual payment address), e.g. `asha.rao@okaxis`.
- **Deep link** = a URL that opens an app: `upi://pay?pa=asha.rao@okaxis&pn=Asha&am=354.00&cu=INR&tn=Namastute%20cab` (`pa` payee address, `pn` payee name, `am` amount, `cu` currency, `tn` note). Android shows a chooser of UPI apps, pre-filled; the user confirms in their bank app.
- **URL encoding:** spaces, `&` and Hindi names must be encoded (`encodeURIComponent`).
- **iOS** handles `upi://` unreliably → also show the UPI ID with a **Copy** button (`navigator.clipboard.writeText`).
- **We never confirm or hold payments:** holding or routing other people's money needs **RBI** (Reserve Bank of India) authorisation; a link from member to booker keeps us out of that.
- **Safety:** the link always uses the **booker's own profile UPI ID**, never something typed in chat, and UPI apps show the registered account name before you pay.

### 50.5 Decisions (2026-10-14)
| Decision | Choice |
|---|---|
| Split | Proportional to solo distance |
| Rounding | Whole rupees, `HALF_UP`, booker absorbs the remainder |
| "I'm here" | Included: 2+ taps → `met`; the fare can only be entered once `met` |
| Fare typos | Entered once, after a confirm step that previews every share; no editing |

---

## 51. Code walkthrough: fare split (and a pool-starvation bug)

### 51.1 `groups/FareSplit.java`: the pure function
```java
static Map<UUID, BigDecimal> split(BigDecimal fare, Map<UUID, Double> soloKm, UUID booker)
```
- For everyone except the booker: `fare.multiply(km).divide(totalKm, 0, mode)`. `divide(..., 0, mode)` = divide and round to **0 decimal places** (whole rupees) with the given rounding mode. The money stays `BigDecimal` the whole way; the km are only weights.
- The booker gets `fare.subtract(others)`, so the shares add up to the fare **exactly**.
- **Edge case found while writing it:** a booker going 1 km with three others going 40 km, fare ₹50: the others round up to ₹17 each (₹51) and the booker would get −₹1. Then the others are rounded **down** instead (`FLOOR`), so nobody ever "owes" a negative amount.
- `LinkedHashMap`: keeps the members in the order they were added (oldest first), so lists come out stable.
- **`FareSplitTest`** (5 tests, no Spring): the ₹600 example (354 / 246), equal thirds of ₹1000 (333 + 333 + 334), `HALF_UP` on exactly .5 (199.5 → 200), paise staying with the booker (₹612.40 → 306 + 306.40), and the never-negative edge case. `isEqualByComparingTo("354")`: compares the value, ignoring scale (`354` vs `354.00`).

### 51.2 `V5__here_at.sql` and `TripSummary.soloKm`
- `alter table group_members add column here_at timestamptz;`: when each member tapped "I'm here" (null = not yet).
- `TripService.TripSummary` gains `soloKm`, the weight for the split; groups reads it through the public API, as always.

### 51.3 `GroupService`: the new steps
- `met` joins the active statuses (in `groupOf` and `isActiveMember`), so the card and chat keep working after meeting up.
- **`here(userId, groupId)`**, `@Transactional`:
  1. `select ... for update` on the group row: **pessimistic lock** (40.5). Two members tapping at the same instant would otherwise each count only themselves (under READ COMMITTED neither sees the other's uncommitted tap), and the group would stay `locked` forever.
  2. Group still `open` → 409 `NOT_LOCKED`.
  3. `update group_members set here_at = now() where ... and here_at is null`: 0 rows = you already tapped, so return quietly (**idempotent**).
  4. `GroupChanged("<name> is here")`, then `update groups set status = 'met' where id = ? and status = 'locked' and (select count(*) ... here_at is not null) >= 2`: the state move with the "2 people" rule inside the `WHERE`.
- **`farePreview(...)`**: checks "met, you're the booker, no fare yet" with one `count(*)`, then returns `ShareView(firstName, booker, share)` for everyone. **Saves nothing.**
- **`enterFare(...)`**, `@Transactional`: one conditional `UPDATE` checks all the rules at once: `set fare_total = ? where id = ? and status = 'met' and booker_id = ? and fare_total is null`. 0 rows → 409 `CANNOT_ENTER_FARE` (not the booker, not met, or already entered). Then each member's `share` is saved, and `GroupChanged("Fare entered: ₹600. Check your share and pay the booker.")`.
- Entering the fare **doesn't** end the group: it stays `met`, so everyone keeps seeing their share. The 4-hour sweep now finishes `locked` **and** `met` groups.
- `leave(...)` refuses once `met` (409 `ALREADY_MET`): the ride is on.
- `GroupView` gains `fareTotal`; `MemberView` gains `here` and `share`.

### 51.4 `GroupController`
```java
record FareRequest(@NotNull @DecimalMin("50") @DecimalMax("10000") @Digits(integer = 5, fraction = 2) BigDecimal total) {}
```
- Money arrives as `BigDecimal` (JSON `600` or `612.40` is parsed exactly). `@Digits(fraction = 2)` refuses `601.555`.
- `POST /api/v1/groups/{id}/here` (204), `POST .../fare/preview` (200, shares), `POST .../fare` (204).

### 51.5 The pool-starvation bug (found by a new test)
- New test `simultaneousHereTapsStillMeetTheGroup`: 10 groups, 20 threads, everyone taps "I'm here" at once. The first run **hung for 30 seconds** and failed.
- **Cause:** the chat listener from slice 3 (47.3) ran *after commit* in a `REQUIRES_NEW` transaction. "After commit" code runs **while the first connection is still held**, so every group change briefly needed **two** database connections. The connection pool (**HikariCP**, 10 connections by default) ran out: 10 threads each held one connection and waited forever for a second. That's **pool starvation**, a kind of deadlock, and in production any burst of about 10 simultaneous group changes could have frozen the API for 30 seconds.
- **Fix (`ChatService.onGroupChanged`):**
  ```java
  @EventListener   // runs inside the publisher's transaction, on the same connection
  void onGroupChanged(GroupChanged event) {
      // insert the system message now (same transaction: saved with the change, or not at all)
      // ...then broadcast only after the commit:
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
          @Override public void afterCommit() { broadcast.run(); }
      });
  }
  ```
  - `@EventListener` (plain, not transactional) runs immediately, inside the groups transaction, so the insert reuses its connection.
  - `registerSynchronization(... afterCommit ...)`: "run this when the transaction commits". The broadcast doesn't touch the database, so it needs no connection.
  - No transaction (e.g. a decline) → broadcast right away.
  - **Bonus:** the system message is now saved **atomically** with the change, so the old `shortcut:` ("lost if the app crashes after commit") is gone.
- After the fix, the 20-thread test passes in about 3 seconds. **Proven both ways:** without the `for update` in `here()`, the same test fails ("expected met, but was locked") on two runs out of two.

### 51.6 Web
- `lib/format.ts` `rupees(n)`: `Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" })`, giving "₹246", "₹306.40", and Indian grouping (₹1,00,000).
- `lib/api.ts`: `imHere`, `previewFare`, `enterFare`; the `Group` type gains `fareTotal`, members gain `here` and `share`.
- `app/group-card.tsx`: "· here ✓" per member; **I'm here at the pickup** (locked or met, until you've tapped); Lock only while `open` (a bug found in the browser test: it showed for a met group); Leave hidden once met; `<FareSection>` when met.
- `app/fare-section.tsx`:
  - **Booker, no fare yet:** amount box → "Preview shares" (client check ₹50–₹10,000; the API checks too) → the list → "Confirm fare ₹600". Changing the amount clears the preview.
  - **Booker, fare in:** "X owes you ₹246".
  - **Member, fare in:** "Your share: ₹246, to Asha" + **Pay Asha ₹246 by UPI** (`upi://pay?pa=…&pn=…&am=246.00&cu=INR&tn=…`, name and note `encodeURIComponent`-ed; the UPI ID left as is, since it only has safe characters) + UPI ID with **Copy** (`navigator.clipboard.writeText`) + "Check the name your UPI app shows before paying".

### 51.7 Verified in a real browser (two tabs)
- Setup through the API: Asha asks, Keshav accepts; Asha (booker) locks and taps "I'm here".
- **Keshav:** "Asha (booker) · here ✓" → **I'm here** → "you've met up", both "here ✓", Leave gone, chat "You've met up. Have a good ride!…".
- **Asha:** ₹20 → "Enter the fare you paid: ₹50 to ₹10,000." → ₹600 → preview **केशव ₹246, You ₹354** (the 50.3 example) → Confirm → "केशव owes you ₹246".
- **Keshav's tab, live:** "Your share: ₹246, to Asha", **Pay Asha ₹246 by UPI** with `upi://pay?pa=asha.rao@okaxis&pn=Asha&am=246.00&cu=INR&tn=Namastute%20cab`.
- Not tested: actually tapping the UPI link (needs a phone with a UPI app) and the Copy button (clipboard permission in the test browser).

---

## 52. API contracts: OpenAPI and generated types

### 52.1 The problem: two copies of the same truth
- Every response shape exists twice: a Java record (`MatchView`) and a hand-typed TypeScript type (`Match` in `lib/api.ts`).
- **API contract** = the agreement between both sides about what each request and response looks like.
- **Drift** = the two copies stop matching. TypeScript trusts whatever we typed, so a forgotten update compiles fine and breaks **at runtime** (it nearly happened when `firstName` became `riders[]`, and when `GroupView.updates` was removed).
- Goal: a **single source of truth**: write the shape once (Java) and **generate** the other copy.

### 52.2 OpenAPI
- **OpenAPI** (formerly **Swagger**): a standard JSON/YAML document describing an HTTP API: paths, methods, parameters, request bodies, responses, and **schemas** (data shapes).
- `"$ref": "#/components/schemas/MatchView"` points to a schema defined once, like reusing a record.
- Read by documentation pages (Swagger UI), client generators, testing tools and API gateways.

### 52.3 Code-first vs contract-first
| Approach | How | Good for |
|---|---|---|
| Contract-first | Write the OpenAPI file first; generate server and client from it | Big teams, public APIs |
| Code-first | Write the controllers; a library produces the OpenAPI file | Small teams with existing code (us) |
- **springdoc-openapi** (code-first for Spring) inspects every `@RestController`, the records they return, and Bean Validation annotations (`@Min(1) @Max(3)` → `"minimum": 1, "maximum": 3`), and serves the document at `/v3/api-docs`.

### 52.4 Code generation
- **Code generation** = a program writes source code from a description. **openapi-typescript** turns the OpenAPI JSON into TypeScript types: `components["schemas"]["MatchView"]`.
- **Types only**, not a whole generated client: we keep our own `request()` helper (token refresh, errors).
- Mapping: `UUID` → `string` (format uuid); `Instant` / `OffsetDateTime` → `string` (date-time); `BigDecimal` / `double` / `int` → `number`; `List<X>` → `X[]`.
- OpenAPI only describes HTTP request/response. Our chat (STOMP) would need **AsyncAPI**, its sibling for messaging; for two message shapes it isn't worth it, so chat types stay hand-written.

### 52.5 Generated files and drift checks
- Commit the generated files: they're reviewable ("this PR changed the Match shape") and the web builds without Java.
- **Drift check:** CI regenerates and fails if the result differs from what's committed (`git diff --exit-code`).
- Where the spec comes from: a test (which already starts the app with Postgres and Redis) fetches `/v3/api-docs` and writes the file. No extra machinery.

### 52.6 Security
- The spec lists every endpoint. Not secret, but production doesn't need to serve a map of the API, so `/v3/api-docs` is **off by default** and turned on only in the export test.

### 52.7 Decisions (2026-10-14)
| Decision | Choice |
|---|---|
| Producing the spec | A test writes `apps/web/openapi.json` (committed); the web generates `lib/api-types.ts` from it (committed) |
| Drift checks | API job: fail if `openapi.json` changed; web job: fail if `api-types.ts` doesn't match |
| Using the types | Hand-written HTTP types in `lib/api.ts` become aliases of generated ones; chat types stay hand-written |
| Swagger UI | Skipped for now |

---

## 53. Code walkthrough: OpenAPI and generated types

### 53.1 The pipeline
```
Java records + controllers ──(springdoc, in OpenApiExportTest)──▶ apps/web/openapi.json ──(openapi-typescript)──▶ apps/web/lib/api-types.ts ──▶ lib/api.ts aliases
```
Both generated files are committed, and CI checks that both are up to date.

### 53.2 API side
- `pom.xml`: `springdoc-openapi-starter-webmvc-api` **3.1.1** (built on Spring Boot 4.1.0; published 6 Sep 2026, well over two weeks old). It isn't in Spring Boot's BOM, so its version lives in a `<springdoc.version>` property. The "api" starter has no Swagger UI (decision 52.7).
- `application.yaml`:
  ```yaml
  springdoc:
    api-docs:
      enabled: false                 # off by default (52.6)
    api-docs.version: openapi_3_0    # 3.0 format: see 53.3
    writer-with-order-by-keys: true  # sorted keys: same API → same file (the drift check needs this)
    writer-with-default-pretty-printer: true
  ```
- **`OpenApiExportTest`**: `@SpringBootTest(properties = "springdoc.api-docs.enabled=true")` turns the endpoint on for this test only. It does `GET /v3/api-docs` with `jwt()` (every endpoint except login needs a token, the docs included), then `Files.writeString(Path.of("../web/openapi.json"), ...)`. Maven runs tests from `apps/api`, so `../web` is the web app. `\r\n` is turned into `\n` so Windows and Linux write identical bytes.
- **Deterministic output, checked:** two runs gave identical file fingerprints (SHA-256).

### 53.3 Teaching Java what it can't say on its own
- Java records don't say which fields can be `null`, and springdoc first wrote every field as optional and non-nullable. Experiments:
  - **JSpecify `@Nullable`** (Spring 7's standard annotation): **ignored** by springdoc, in both 3.1 and 3.0 formats.
  - **swagger `@Schema(nullable = true)`** in **OpenAPI 3.0** format → `"nullable": true` ✓. That's why the spec is 3.0, not 3.1.
- So the few genuinely nullable fields carry `@Schema(nullable = true)`: `Profile.name/upiId`, `UpdateMe.name/upiId`, `TripView.flightNo`, `CreateTripRequest.flightNo`, `TerminalView.pickupNote`, `GroupView.bookerUpiId/fareTotal`, `MemberView.share`, `LeaveRequest.reason`, `MessageView.firstName/clientId`.
- Status-like strings list their values, so TypeScript gets a **union**: `@Schema(allowableValues = {"open", "locked", "met"}) String status` → `status: "open" | "locked" | "met"` (also for `TripView.status`, `SentView.status`, `MessageView.kind`).
- **Every other field is required:** the generator runs with `--properties-required-by-default`.

### 53.4 Web side
- `openapi-typescript` **7.13.0**, a dev dependency pinned exactly (published Feb 2026).
- `package.json` script: `"api:types": "openapi-typescript openapi.json -o lib/api-types.ts --properties-required-by-default"`.
- `lib/api-types.ts` (991 generated lines): `components["schemas"]["GroupView"]` is
  ```ts
  { bookerUpiId: string | null; fareTotal: number | null; id: string; members: MemberView[]; status: "open" | "locked" | "met"; youAreBooker: boolean }
  ```
- `lib/api.ts`: `type Schemas = components["schemas"];` and every hand-written type became an alias: `export type Match = Schemas["MatchView"];`, `export type Group = Schemas["GroupView"];`, and so on. `ChatMessage = Schemas["MessageView"]`: the live STOMP copy is the same Java record as the HTTP history, so one type covers both.
- **The rest of the web didn't change**, and `tsc` passed: proof the old hand-written types matched the real API.
- `updateMe(changes: { name?: string; upiId?: string })` stays hand-shaped: PATCH means "send only what changes".

### 53.5 The drift check, proven
- Experiment: rename `MatchView.detourKm` → `detourKilometres` in Java, run the export test, `pnpm api:types`, then `tsc`:
  ```
  app/current-trip.tsx(125,18): error TS2339: Property 'detourKm' does not exist on type '{ ... detourKilometres: number ... }'
  ```
  Before this slice, that mistake would have compiled and broken only in a user's browser. (Restored afterwards; `tsc` green again.)
- **CI** (`.github/workflows/ci.yml`):
  - `api` job, after `./mvnw -B verify`: `git diff --exit-code -- ../web/openapi.json` fails with "openapi.json is out of date" if a Java change wasn't followed by committing the new spec.
  - `web` job, after `pnpm install`: `pnpm api:types && git diff --exit-code -- lib/api-types.ts` fails if the types weren't regenerated.
- **Your routine when a Java record changes:** run the API tests (they rewrite `openapi.json`) → `pnpm api:types` in `apps/web` → fix any TypeScript errors → commit all of it.

### 53.6 Small things found along the way
- An earlier PowerShell `Set-Content -Encoding utf8` had saved `package.json` with a **BOM** (byte-order mark: an invisible `EF BB BF` at the start of the file). Python refused to parse it; it's removed. In PowerShell 5.1, `utf8` means "with BOM".
- OpenAPI doesn't cover STOMP; that would need AsyncAPI (52.4). Chat's message type is still generated, because the HTTP history returns the same record.

---

## 54. Production: containers, config, HTTPS

### 54.1 Dev vs production
| Today (dev) | Fine on a laptop | Dangerous in production |
|---|---|---|
| `LogSmsSender` prints OTP codes | only you see the log | anyone with log access could log in as anyone |
| `JWT_SECRET` falls back to a dev value | convenient | it's on GitHub: anyone could **forge login tokens** |
| DB password `namastute` | local | guessable |
| Postgres/Redis ports open | your tools connect | open to the internet = attacked within hours |
| `http://` | fine | logins readable on airport Wi-Fi |

### 54.2 Configuration: the Twelve-Factor rule
- **The Twelve-Factor App** (rules for server software), factor III: **config in the environment**. One image runs everywhere; only **environment variables** differ.
- **Spring profiles:** `SPRING_PROFILES_ACTIVE=prod` loads `application-prod.yaml` on top of the defaults. `@Profile("!prod")` beans don't exist in prod.
- **Fail fast:** in prod, secrets have **no defaults**, so a missing one stops the app at startup. A crash you see in 2 seconds beats a breach you find in 2 months.
- **Secrets never in git:** on the server they live in an `.env` file only its owner can read (`chmod 600`). Generate with `openssl rand -base64 32`.

### 54.3 Container images
- **Layers and caching:** each Dockerfile step is a layer; unchanged layers are reused. Copy `pom.xml` / `package.json` and install dependencies **before** copying source code, so a code change doesn't re-download everything.
- **Multi-stage build:** stage 1 has the full toolkit (JDK + Maven, or Node + pnpm) and builds; stage 2 is a slim runtime that only gets the result. Smaller image, no compiler or source on the server: a smaller **attack surface** (fewer things an attacker could use).
- **Non-root:** the app runs as an ordinary user inside the container.
- **JVM memory:** `-XX:MaxRAMPercentage=50` = "use at most half of the container's memory limit".
- **Architecture:** images are built per CPU type. `postgis/postgis:16-3.5` exists only for **amd64** (Intel/AMD), so the server must be Intel/AMD (e.g. t3), not ARM (t4g).

### 54.4 Caddy: reverse proxy and HTTPS
- **Reverse proxy:** a server in front of the app; only it faces the internet (ports 80/443) and forwards requests inside.
- **TLS** = the encryption in HTTPS. Caddy **terminates TLS** (decrypts), then talks plain HTTP to the apps on Docker's private network.
- **Let's Encrypt** gives free certificates; Caddy proves domain ownership on port 80 (the **ACME** protocol, HTTP-01 challenge), so the domain must point at the server first. For `localhost`, Caddy uses its own local certificate authority instead.
- WebSockets pass straight through. Postgres and Redis get **no public ports**.

### 54.5 The real client IP behind a proxy
- Behind Caddy every request seems to come from Caddy, so the per-IP OTP limit would lump all users together.
- Caddy adds **`X-Forwarded-For`** with the real IP; Spring reads it with `server.forward-headers-strategy: native`.
- Anyone can send that header, so it's trusted **only from private-network addresses** (our Caddy container). Caddy itself also replaces any `X-Forwarded-For` sent by an untrusted client.

### 54.6 One origin
- **Decision (2026-10-14, replaces the "web on Vercel" plan of 16.1):** web **and** API on the same EC2 server, under **one domain**. Caddy routes `/api/*` and `/ws` to Spring and everything else to Next.js.
- Why: Vercel's free plan is (as far as we know) personal and non-commercial, and Pro costs more than the server; all users are in Delhi, near the Mumbai region, so a global CDN adds little; **one origin** means no CORS, no cross-site WebSocket origin, no "same-site" cookie rule to satisfy; one place to deploy and back up. Cost: Next.js uses about 150–300 MB of the server's memory.
- **Next.js gotcha:** `NEXT_PUBLIC_…` variables are copied into the JavaScript **at build time**: changing one means rebuilding the web image.

### 54.7 Keeping it alive and safe
- `restart: unless-stopped`; health checks on `/actuator/health`; `depends_on: condition: service_healthy`.
- Flyway runs migrations at startup.
- **Backups:** data lives in a Docker volume; a nightly `pg_dump` from the server's cron (design §3.7), later copied to S3.
- No OTP codes in production logs: `LogSmsSender` doesn't exist there.

### 54.8 Decisions (2026-10-14)
| Decision | Choice |
|---|---|
| Hosting | Everything on one EC2 server, one domain (54.6) |
| Local test | Same production compose on the laptop at `https://localhost`, with a `fake-sms` profile that allows the log sender only for this test |
| Real client IP | `server.forward-headers-strategy: native` in prod, trusting private-network proxies only |
| CPU type | Intel/AMD (`linux/amd64`), because the PostGIS image is amd64-only |

---

## 55. Code walkthrough: the production build

### 55.1 What exists now
| File | Job |
|---|---|
| `apps/api/Dockerfile` | API image: build the jar with the JDK, ship it on a slim JRE, non-root |
| `apps/web/Dockerfile` | Web image: build with pnpm, ship Next.js's standalone `server.js`, non-root |
| `apps/*/.dockerignore` | Keep `target/`, `node_modules/`, `.next/`, `.env*.local` out of the build |
| `apps/api/src/main/resources/application-prod.yaml` | Production settings; secrets with no defaults |
| `infra/docker-compose.prod.yml` | The whole server: Postgres, Redis, API, web, Caddy |
| `infra/Caddyfile` | HTTPS and routing for one domain |
| `infra/.env.example` + `infra/.gitignore` | Template for the server's secrets; the real `.env` is never committed |
| `infra/README.md` | Runbook: first-time setup, everyday commands, backups, restore |

### 55.2 `apps/api/Dockerfile`
```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/target/*.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50"
ENTRYPOINT ["java", "-jar", "app.jar"]
```
- `AS build` names stage 1; `COPY --from=build` takes only the jar into stage 2 (54.3).
- **Layer order:** `pom.xml` + `dependency:go-offline` before `COPY src`, so code changes reuse the downloaded-libraries layer.
- `chmod +x mvnw`: files copied from Windows may lose the "executable" flag.
- `-DskipTests`: the tests need Postgres and Redis and already run in CI; the image build only packages.
- `addgroup` / `adduser -S`: a **system user** with no password and no login shell; `USER app` makes everything after it run as that user.
- `JAVA_TOOL_OPTIONS`: an environment variable every Java program reads at startup.
- `-alpine` images: built on Alpine Linux, a very small distribution, so the image is smaller.

### 55.3 `apps/web/Dockerfile` and `next.config.ts`
- `output: "standalone"` in `next.config.ts`: `next build` also produces `.next/standalone/server.js` plus only the files it needs (Next.js traces imports).
- Stage 1: `corepack enable` provides the exact pnpm from `package.json` (`"packageManager": "pnpm@11.1.2"`); lockfile first, `pnpm install --frozen-lockfile`, then the source and `pnpm build`.
- `ARG NEXT_PUBLIC_API_URL` → `ENV`: the API address is **baked in at build time** (54.6), so compose passes it as a build argument.
- Stage 2: copies `standalone/`, plus `.next/static` and `public/`, which `server.js` doesn't include by itself (the installed Next.js docs say so). `USER node` (the Node image's built-in non-root user); `HOSTNAME=0.0.0.0` so it listens on the container's network, not only inside itself.

### 55.4 `application-prod.yaml`
- `password: ${DB_PASSWORD}`, `secret: ${JWT_SECRET}`, `hmac-secret: ${OTP_HMAC_SECRET}`, `web-origin: ${WEB_ORIGIN}`: **no `:default`**, so a missing variable stops the app at startup (fail fast, 54.2).
- `url: jdbc:postgresql://postgres:5432/namastute` and `redis.host: redis`: inside Docker's network, containers reach each other **by service name**.
- `server.forward-headers-strategy: native`: Tomcat's `RemoteIpValve` replaces the "remote address" with `X-Forwarded-For`, but only when the request comes from a private-network address (54.5). `AuthController` still just calls `getRemoteAddr()`; no Java change was needed.
- `LogSmsSender`: `@Profile("!prod | fake-sms")`: a profile **expression**, "not prod, OR fake-sms is on". The real server runs `prod` alone, so no log sender exists there, and with no `SmsSender` at all the app refuses to start until a real one is written.

### 55.5 `infra/docker-compose.prod.yml`
- `name: namastute-prod`: a separate project from dev, so their volumes never mix.
- `${DB_PASSWORD:?set DB_PASSWORD in infra/.env}`: Compose's own fail-fast: refuse to start with that message if unset.
- **No `ports:`** on postgres, redis, api, web: only Caddy publishes 80 and 443.
- `depends_on: condition: service_healthy`: the API waits for a healthy Postgres and Redis; Caddy waits for a healthy API.
- API health check: `wget -qO- http://localhost:8080/actuator/health` (Alpine includes `wget`); `start_period: 30s` gives Java time to start before failures count.
- `restart: unless-stopped`: back up after crashes and reboots.
- `mem_limit`: Postgres 512 MB, Redis 128 MB, API 768 MB (Java takes half), web 256 MB, Caddy 64 MB ≈ 1.7 GB, inside the 2 GB server.
- `caddy_data` volume: keeps certificates across restarts (Let's Encrypt limits how often you can re-issue).

### 55.6 `infra/Caddyfile`
```
{$DOMAIN} {
	encode zstd gzip
	@api path /api/* /ws
	handle @api {
		reverse_proxy api:8080
	}
	handle {
		reverse_proxy web:3000
	}
}
```
- `{$DOMAIN}`: from the environment. A real domain → Let's Encrypt; `localhost` → Caddy's own local certificate.
- `@api` is a **named matcher** (a reusable condition): paths `/api/*` and `/ws`.
- `handle` blocks are tried in order: API paths to Spring, everything else to Next.js. `/actuator` matches neither API path, so it's **not reachable** from the internet (it falls to Next.js, which shows its 404).
- `encode zstd gzip`: compress responses.
- HTTP → HTTPS redirects happen automatically.

### 55.7 `ForwardedIpTest`
- Starts the real server (`RANDOM_PORT`), because MockMvc skips Tomcat, where forwarded headers are handled.
- Sends `POST /api/v1/auth/otp` with `X-Forwarded-For: 203.0.113.x` (a range reserved for documentation) and checks Redis has `rate:otp:ip:203.0.113.x`: counted against the client, not the proxy.
- **Proven to fail:** with `forward-headers-strategy=none` the same test went red.

### 55.8 Verified: the production stack on the laptop
`infra/.env` with `DOMAIN=localhost`, random secrets and `SPRING_PROFILES_ACTIVE=prod,fake-sms`; `docker compose ... up -d --build`. Both images built (API 445 MB, web 297 MB); all five containers healthy; **only Caddy publishes ports** (80, 443).

| Check (from outside, through Caddy) | Result |
|---|---|
| `http://localhost/` | 308 → `https://localhost/` |
| `https://localhost/`, `/login` | 200, Next.js pages |
| `/api/v1/terminals` without a token | 401 from Spring: routing works |
| `/actuator/health`, `/v3/api-docs` | 404: not reachable from outside |
| OTP request → code in the API log (fake-sms) → verify | 204 → 200; cookie `Secure; HttpOnly; SameSite=Lax; Path=/api/v1/auth` |
| `/api/v1/terminals` with the token | 200, DEL-T1/T2/T3: Flyway created and seeded the fresh database |
| Redis rate-limit key | `rate:otp:ip:172.25.0.1` = the caller, **not** Caddy (172.25.0.6): real client IP works |
| WebSocket handshake to `/ws` | 101 from `https://localhost`, **403** from `https://evil.example` |
| API with `prod` but no `JWT_SECRET` | refuses to start: `Could not resolve placeholder 'JWT_SECRET'` |
| API with pure `prod` (no fake-sms) | refuses to start: `required a bean of type 'SmsSender'` |

- **Not tested:** the app in a browser at `https://localhost`. Browsers reject Caddy's *local* certificate unless its certificate authority is added to Windows' trusted list, a system security change we skipped. On the real server, Let's Encrypt gives a certificate every browser trusts.
- Image versions were pinned after checking they exist and are a few weeks old: `eclipse-temurin:21-jdk/jre-alpine`, `node:24-alpine`, `caddy:2.11.4-alpine` (newer 2.11.6/2.11.7 were only days old).

### 55.9 What's left before real users
- An `SmsSender` for the chosen provider (MSG91 or Firebase): **team decision**.
- A domain, an EC2 t3.small in Mumbai with an Elastic IP, then `infra/README.md` from step 1.
- Off-server backup copies (S3), privacy policy and terms.

---

## 56. Safety: report and block

### 56.1 Design for abuse
- Safety features must also ask **"how would a bad person misuse this?"** Doing that systematically is **threat modelling**; the misuse cases are **abuse cases**.
| Abuse case | Rule it forces |
|---|---|
| Reporting a stranger you never met, to get them banned | Only **report people you've shared a group with** (the group is the evidence) |
| Filing 50 reports in anger | **One report per person per group**, enforced by a unique constraint |
| A group ganging up with false reports ("weaponised reporting") | Reports **never punish automatically**; a human reviews them |
| The blocked person noticing and confronting the blocker | **Silent block**: no notification, the same "not available" answer as any ended trip |
| Probing ids to learn who exists | Targets are **opaque trip handles** already shown to you, checked server-side |

### 56.2 Blocking
- Personal: needs no proof, affects only the two people, so you can block **anyone you can see** (in matches or in your group).
- **Two-way invisible:** neither appears in the other's matches (already true in the matching SQL); asking to share is refused with the ordinary "isn't available any more"; pending requests between the two are withdrawn.
- **Silent:** an error that says *why* would leak that a block exists (the information-leak idea).
- Doesn't remove anyone from a current group: if you feel unsafe, **leave** and **report**.
- **Unblock** from a "Blocked people" list on your profile, because people block by mistake.

### 56.3 Reporting
- A report records reporter, reported person, the **group** (proof you met), a **category** (unsafe behaviour, harassment, didn't show up, fake profile, other) and optional details (≤ 500 characters). Categories make reports sortable and countable.
- **Reviewed by a human.** Until the admin panel exists, the runbook has the SQL an admin runs (`shortcut:`).
- **Emergencies aren't reports:** the screen says **"In danger right now? Call 112"** (India's single emergency number).

### 56.4 Privacy of everyone involved
- The reported person never learns a report exists or who filed it (protects against retaliation).
- The reporter gets a calm "Thanks, we'll review it", never what happened to the other person.
- Free text is risky to store (people paste phone numbers): capped, and only admins read it.

### 56.5 Modules
- Design §2.2: **users** owns `blocks`; a new **feedback** module owns `reports` and offers the report/block endpoints.
- feedback depends on trips (handle → person), groups (shared group, withdraw requests) and users (blocks). Groups must not call feedback (that would be a cycle, 43.1), so "left a locked group with a reason" becomes an **event** that feedback turns into a report. This removes the old `shortcut:` where groups wrote reports itself.

### 56.6 Decisions (2026-10-15)
| Decision | Choice |
|---|---|
| Who | Report: only someone you've shared a group with. Block: anyone you can see |
| Blocking | Silent, two-way invisible; refuses new requests and withdraws pending ones; doesn't remove anyone from a current group |
| Reports | Category + optional text (≤ 500), one per person per group, never automatic punishment, "Call 112" shown |
| Undo | "Blocked people" list with Unblock on the profile page |
| Modules | New `feedback` module; leave-with-reason report moves there via an event; blocks in `users` |

---

## 57. Code walkthrough: report and block

### 57.1 `V6__report_block.sql`
- `reports.category`: `not null default 'other'` with a `check` listing the five categories. Old rows get `'other'`.
- `constraint reports_once unique (reporter_id, reported_id, group_id)`: one report per person per group, enforced by the database.
- `blocks.id uuid ... unique`: an **opaque handle** for "unblock", so the other person's user id never reaches the browser. Plus `created_at` for the list's order.
- Before migrating, a query confirmed no existing duplicate reports (a new unique constraint fails on existing duplicates).

### 57.2 `users/UserService`: blocks (the users module owns the table)
- `block(blocker, blocked)`: `insert ... on conflict do nothing` (idempotent).
- `blockedEitherWay(me, others)`: one query, both directions: `(blocker = me and blocked in others) or (blocked = me and blocker in others)`.
- `blocks(blocker)`: `BlockView(id, firstName)`: a handle and a first name, never a user id.
- `unblock(blocker, blockId)`: `delete ... where id = ? and blocker_id = ?`: the `blocker_id` condition means you can only remove **your own** blocks.

### 57.3 `groups/GroupService`
- `ask(...)`: one more `.filter(t -> !users.blockedEitherWay(userId, peopleAround(t)))`. A blocked pair gets the **same** 404 "That traveller isn't available any more" as an ended trip: the **silent block**. `peopleAround(trip)` = the trip's owner plus everyone in their open group.
- `withdrawRequestsBetween(a, b)`: pending requests between the two people's current trips → `withdrawn`. `shortcut:` a request from the blocked person to *another* member of the blocker's group stays visible (decline it).
- `sharedGroup(reporter, reportedTripId)`: a self-join on `group_members` ("them" and "me" in the same group, accepted or since left). That's the evidence a report needs.
- `MemberView.tripId`: group members now carry the opaque handle, so the card can say "Report"/"Block"; it also became the React `key`.
- `leave(...)`: no longer writes `reports`; it publishes `MemberLeftLockedGroup(groupId, memberId, bookerId, reason)`.

### 57.4 The new `feedback` module
- `FeedbackService.block`: trip handle → owner (`ownerOf`, refusing your own trip) → `users.block` → `groups.withdrawRequestsBetween`.
- `report`: owner → `groups.sharedGroup(...)` or 404 `NOT_IN_A_GROUP_TOGETHER` → insert; `DuplicateKeyException` → 409 `ALREADY_REPORTED`.
- `@EventListener onMemberLeftLockedGroup(...)`: files the leave reason as a report on the booker, inside the groups transaction (same connection, 51.5), with `on conflict ... do nothing` in case the member had already reported the booker for that group. The old `shortcut:` in groups is gone.
- `FeedbackController`: `POST /api/v1/blocks` (204), `GET /api/v1/blocks`, `DELETE /api/v1/blocks/{id}` (204 / 404), `POST /api/v1/reports` (204).
  - `category`: `@Pattern("unsafe|harassment|no_show|fake_profile|other")` (a bad value gets our usual `VALIDATION_FAILED`) plus `@Schema(allowableValues)` so TypeScript gets a union. `details`: `@Size(max = 500)`, nullable, optional.
  - Report answers 204: the reporter only needs "thanks", and the web's `request()` expects JSON for any other success status.
- **Dependencies:** feedback → trips, groups, users. Nothing depends on feedback, so `ModularityTest` still passes.

### 57.5 Tests: `FeedbackTest` (3)
| Test | Proves |
|---|---|
| `blockingHidesBothWaysSilentlyAndCanBeUndone` | gone from **both** people's matches; the blocked person's ask gets the ordinary "isn't available any more"; their own blocks list stays empty (not told); the blocker's list has 2 fields (handle + first name); unblock makes them visible again; unblocking twice → 404 |
| `blockingWithdrawsPendingRequests` | Ravi's pending request disappears for both |
| `reportsNeedASharedGroupAndHappenOncePerRide` | the handle comes from the group card; report → 204, again → 409, a stranger → 404; bad category / 501 characters → 400; the category is saved |
- **Proven to fail:** without the block check in `ask`, the first test went red (the blocked person could still ask).
- `GroupsTest`'s "leaving a locked group reports the booker" still passes, now through the event.

### 57.6 Web
- `lib/api.ts`: `blockTraveller`, `getBlocks`, `unblock`, `reportTraveller`; types `BlockView`, `ReportRequest` come from the regenerated `api-types.ts` (`pnpm api:types`, 53.5). The generator made `details` optional because Java marks only `tripId` and `category` `@NotNull`.
- `app/report-form.tsx`: **"In danger right now? Call 112"** first (a `tel:112` link opens the phone dialler), then a category `<select>`, optional details (`maxLength={500}`), and "Thanks. We'll review your report. Asha won't be told who reported them."
- `app/group-card.tsx`: **Report** and **Block** next to every member except you; block asks for confirmation with `window.confirm` ("… won't be told. This doesn't remove anyone from this group: leave it if you feel unsafe.").
- `app/current-trip.tsx`: **Block** on single-traveller matches (a block targets one person, so not on group matches); the match disappears after reloading.
- `app/profile/page.tsx`: "Blocked people" with **Unblock** (only for existing profiles).
- `infra/README.md`: the SQL an admin runs to review open reports and mark them `actioned` or `dismissed`.

### 57.7 Verified in a real browser (as Keshav, with Asha and Neha set up through the API)
- Matches: Neha and Asha, each with **Block** → block Neha (confirmation: "Block Neha? You won't see each other in matches again, and Neha won't be told.") → Neha gone.
- Accept Asha → group card: Asha · **Report · Block** → Report shows "In danger right now? Call 112." (a `tel:112` link), the five categories, and details → Harassment + "Test report: rude messages" → "Thanks… Asha won't be told who reported them." → database: `harassment`, status `open`.
- Block Asha → "Asha is blocked. You can undo it from your profile."
- Profile → "Blocked people: Asha, Neha" → Unblock Neha → only Asha remains (database agrees).
- Testing note: `window.confirm` was replaced in the test page with a function that records the message and answers "OK", because automated browsers can hang on native dialogs. The recorded messages are quoted above.

---

## 58. Push notifications

### 58.1 The problem
- Live updates ride on the WebSocket, which only exists while the page is open. At the airport the phone is in a pocket and the browser closed, so people miss requests and messages.
- SMS costs money (and is needed for OTPs), email is slow, a native app needs app stores; **Web Push** is free and built into browsers.

### 58.2 The four pieces
1. **Service worker:** a JavaScript file the browser keeps **separately from the page** and can wake even with no tab open. The only code that can receive a push.
2. **Push service:** run by the browser maker (Google for Chrome, Mozilla for Firefox, Apple for Safari, Microsoft for Edge). Our server never talks to the phone; it sends to the push service, which delivers.
3. **Subscription:** after the user allows notifications, the browser creates `{endpoint, keys: {p256dh, auth}}`: a unique URL at the push service plus two keys. Stored in `push_subscriptions` (exists since V1).
4. **VAPID keys** (Voluntary Application Server Identification): our server's own key pair. The browser subscribes with our **public** key, so the subscription only accepts pushes **signed** with our **private** key: nobody else can push to our users. Each push carries a small signed JWT.
- Journey: permission → `pushManager.subscribe(our public key)` → POST to our API → (later) API encrypts + signs → POST to the endpoint → push service → browser wakes the service worker → `showNotification` → tap opens the app.

### 58.3 End-to-end encryption
- Messages are encrypted with the subscription's keys before leaving our server (RFC 8291: ECDH key agreement, HKDF, AES-GCM). The push service carries but can't read them.
- **Never write your own crypto.** Library chosen after checking the options: `zerodep-web-push-java` 2.1.5 (Dec 2025), which uses Java's built-in crypto and has **no runtime dependencies**; the alternatives pulled in old BouncyCastle, extra HTTP clients, or the Kotlin runtime.

### 58.4 Asking politely
- Prompting on page load gets hidden or blocked by browsers, and users click "Block" by reflex, which is almost permanent.
- **Soft ask:** our own button, at a sensible moment ("Get notified when someone wants to share"); the browser's prompt appears only after that tap.

### 58.5 Lock-screen privacy
- Notifications show on lock screens. Content stays minimal: a first name at most; never chat text, amounts or places.

### 58.6 Reality checks
- **Best-effort:** pushes can be late or lost; a nudge, never the source of truth.
- **TTL:** how long the push service keeps a message for an offline phone (a "wants to share" push is useless after an hour).
- **Dead subscriptions:** the push service answers **404/410 Gone** → delete the subscription.
- **iPhone:** iOS 16.4+, and only for web apps **added to the Home Screen**, which needs a **web app manifest** (name, icons, colours) = an installable **PWA** (Progressive Web App).
- **No double notifications:** chat pushes only go to members who aren't connected right now.
- **Shared browsers:** on logout, the browser's subscription is removed, or the next person to use that browser would get the previous user's notifications.

### 58.7 SSRF: never let users choose where your server sends requests
- **SSRF** (Server-Side Request Forgery): the endpoint URL comes from the browser, i.e. from the user. An attacker could register `https://169.254.169.254/...` (the cloud's internal metadata address, which can hand out server credentials) or an internal service, and make **our server** send requests there.
- Defence: only accept endpoints that are **HTTPS** on the **known push services' hosts** (an **allowlist**): `fcm.googleapis.com`, `*.push.services.mozilla.com`, `*.push.apple.com`, `*.notify.windows.com`.

### 58.8 Decisions (2026-10-15)
| Decision | Choice |
|---|---|
| Triggers | Someone asks to share with you or your group; you're accepted into a group; a new chat message while you're **not** in the app; the fare is entered |
| Lock screen | First names only; never chat text, amounts or places |
| Permission | Soft-ask button on the trip card, never on page load; a clear message if blocked |
| Crypto | `zerodep-web-push-java` 2.1.5; nothing hand-written |
| iPhone | Web app manifest + icons, and a hint to add Namastute to the Home Screen |

---

## 59. Code walkthrough: push notifications

### 59.1 Settings: `application.yaml`, `application-prod.yaml`
- `namastute.push.vapid-private-key` / `vapid-public-key`: one line each, **base64url**. The private key is PKCS#8 (a standard wrapper for private keys); the public key is the raw 65-byte point (`0x04` + X + Y), which is also what browsers want.
- Development has **dev-only** default keys; production has **no defaults** (`${VAPID_PRIVATE_KEY}`), so a missing key stops the API at startup (54.3).
- `contact`: who push services can contact about our pushes. Production uses `https://<domain>` (RFC 8292 allows a `mailto:` or `https:` address).
- `allowed-hosts` + `require-https`: the SSRF allowlist (58.7). They're settings only so the test can point them at a fake push service on `http://localhost`.

### 59.2 The new `notifications` module: `PushService`
- **Owns `push_subscriptions`** (design 2.2; the table has existed since V1).
- `subscribe(userId, endpoint, p256dh, auth)`:
  1. `isAllowedEndpoint(...)` or **400 `BAD_PUSH_ENDPOINT`**. It parses with `java.net.URI` and requires all of these: scheme `https`, a host, **no user-info** (`https://fcm.googleapis.com@169.254.169.254/` is really a request to `169.254.169.254`), and a host equal to an allowed one or ending in `"." + allowed`. The dot matters: `evilpush.apple.com` must not pass as `push.apple.com`.
  2. Key sizes or **400 `BAD_PUSH_KEYS`**: `p256dh` must decode to 65 bytes (a P-256 public key) and `auth` to 16.
  3. `insert ... on conflict (endpoint) do update set user_id = excluded.user_id, keys = ...`: an **upsert**. One row per browser; if someone else logs in on the same browser, the row moves to them.
- `unsubscribe(userId, endpoint)`: `delete ... where user_id = ? and endpoint = ?`. You can only remove your own.
- `send(userIds, Push(title, body, ttl))`, the method other modules call:
  1. It reads the subscriptions **now**, inside the caller's transaction on the same connection. Never do database work after commit in a new transaction (51.5).
  2. It builds the JSON `{"title", "body"}` with Jackson (`tools.jackson.databind.ObjectMapper`, Jackson 3).
  3. It sends **after commit** (`registerSynchronization(afterCommit)`, the same pattern as chat broadcasts). With no transaction, it sends straight away.
- `deliver(...)`: the library does the hard parts. `StandardHttpClientRequestPreparer.getBuilder()`:
  - `.pushSubscription(...)`: endpoint + keys
  - `.vapidJWTExpiresAfter(12, HOURS)` and `.vapidJWTSubject(contact)`: the signed VAPID token
  - `.pushMessage(bytes)`: encrypted with RFC 8291
  - `.ttl(...)`, `.urgencyHigh()`
  - `.build(vapid).toRequest()`: an ordinary `java.net.http.HttpRequest`
- **`http.sendAsync(...)`**: the request goes out on the HTTP client's own threads, so a slow push service never holds up a request or a database connection. When the answer arrives:
  - **404/410** → delete that subscription (58.6)
  - another error → a warning in the log
  - an exception → logged, never thrown (a push is best-effort)

### 59.3 `PushController`
- `GET /api/v1/push/public-key` → `{"publicKey": "..."}`: what the browser subscribes with.
- `POST /api/v1/push/subscriptions` (204): the body is exactly what the browser's `subscription.toJSON()` produces: `{endpoint, keys: {p256dh, auth}}`. A nested record (`SubscriptionRequest.Keys`) with `@Valid` checks the inner fields too.
- `DELETE /api/v1/push/subscriptions?endpoint=...` (204): used on logout.
- `@Bean static VAPIDKeyPair vapidKeyPair(...)`: base64url → bytes → `PrivateKeySources.ofPKCS8Bytes` / `PublicKeySources.ofUncompressedBytes` → `VAPIDKeyPairs.of(...)`. It's `static` so Spring can create it without first creating the controller.

### 59.4 The triggers (groups and chat call `PushService`)
| Where | Who gets it | Title / body | TTL |
|---|---|---|---|
| `GroupService.ask` | the person asked, plus everyone in their open group (`peopleAround`, `distinct()`) | "Ravi wants to share a cab" / "Open Namastute to answer" | 1 hour |
| `GroupService.accept` | the person who asked | "You're in a cab group" / "Asha said yes. Open Namastute to chat." | 1 hour |
| `GroupService.enterFare` | every member except the booker | "Your share of the cab fare is ready" / "Open Namastute to see it and pay the booker" | 1 day |
| `ChatService.post` | members who aren't connected, never the sender | "New message in your cab group" / "Open Namastute to read it" | 10 minutes |
- **Lock-screen rule (58.5):** at most a first name. No message text, no amount, no place. `nameOf(userId)` falls back to "Someone", because the name is optional (V2).
- `accept` and `enterFare` are `@Transactional`, so their pushes wait for the commit. `ask` and `post` have no transaction: their insert is already committed, so the push goes at once.
- **"Not connected"**: `SimpUserRegistry.getUser(id) == null`. Spring keeps this registry of who has an open WebSocket; the name is the JWT subject, which is the user id (`StompAuth`). `shortcut:` a phone may keep a backgrounded tab's socket open for a while, and that person gets no push meanwhile. Track page visibility if pushes go missing.
- New public `GroupService.memberIds(groupId)` for chat.
- **Dependencies:** groups → notifications, chat → notifications; notifications depends only on shared. No cycle, so `ModularityTest` passes.

### 59.5 Tests: `NotificationsTest` (6)
The test plays **both** outside parties:
- **A fake push service:** `com.sun.net.httpserver.HttpServer`, a tiny HTTP server built into the JDK, on a random localhost port. It records every request and answers 201, or **410** for paths under `/gone/`.
- **A browser (`Browser` class):** its own P-256 key pair and 16-byte auth secret, and a `decrypt(...)` that follows RFC 8291 step by step:
  1. Read the header: salt (16 bytes), record size, the sender's public key (65 bytes).
  2. **ECDH** (Elliptic-Curve Diffie-Hellman): our private key + the sender's public key → a shared secret that only the two ends can compute.
  3. **HKDF** (HMAC-based Key Derivation Function, built from `HmacSHA256`): mix the auth secret, the shared secret and both public keys into the content key and the nonce.
  4. AES-128-GCM decrypt, then remove the padding (the `0x02` delimiter and the zeros after it).

  If the library encrypted wrongly, GCM's authentication tag would reject it and the test would fail.

| Test | Proves |
|---|---|
| `pushesArriveEncryptedSignedAndMinimal` | `Content-Encoding: aes128gcm`, `TTL: 3600`; `Authorization: vapid t=<JWT>, k=<our public key>`; the JWT's `aud` is the push service's origin; the decrypted JSON is **exactly** `{title: "Ravi wants to share a cab", body: "Open Namastute to answer"}`; accepting sends "You're in…" / "Asha said yes" |
| `chatPushesOnlyReachMembersWhoAreNotConnected` | Keshav connected (real STOMP): **no** push; after he disconnects: a push saying "New message…" that **doesn't contain** the text |
| `deadSubscriptionsAreDeleted` | the push service answers 410 → the row is gone |
| `endpointsOutsideTheAllowlistAndBadKeysAreRefused` | `http://169.254.169.254/...` → 400 `BAD_PUSH_ENDPOINT`; a short `auth` → 400 `BAD_PUSH_KEYS`; nothing saved |
| `theProductionAllowlistOnlyTakesHttpsPushServices` | the real list with HTTPS required: FCM/Mozilla/Apple pass; plain HTTP, `fcm.googleapis.com.evil.example`, `evilpush.apple.com`, a `user@` trick, the metadata IP and garbage fail |
| `aSharedBrowserFollowsWhoeverLoggedInLastAndUnsubscribeRemovesIt` | the same endpoint moves from Asha to Ravi; Asha can't delete Ravi's; Ravi can |
- **Proven to fail:** with the "is connected" filter removed, the chat test went red ("Keshav has the app open: … no push").
- Other modules' pushes land in the same fake server, so `poll(browser)` only looks at that browser's endpoint path.
- The full suite: **84 tests**, all green.

### 59.6 Web
- `public/sw.js`, the service worker:
  - `push` → `showNotification(title, {body, icon, tag: title})`. The **tag** makes a second "New message" replace the first instead of stacking up.
  - `notificationclick` → focus an open Namastute tab, or open `/`.
  - No caching, so there's no offline mode to get wrong.
- `app/manifest.ts`: Next serves it as `/manifest.webmanifest` and links it automatically: name, `display: "standalone"`, colours, icons (192, 512, and 512 "maskable").
- `app/apple-icon.png`: Next's file convention adds `<link rel="apple-touch-icon">` for the iPhone Home Screen.
  - The icons (a white "N" on black) were drawn with Python's standard library: PNG = signature + `IHDR` (size, colour type) + `IDAT` (zlib-compressed pixel rows) + `IEND`, each chunk with a CRC checksum.
- `lib/api.ts`:
  - `pushState()`: `unsupported` / `install` (iPhone Safari not added to the Home Screen) / `ask` / `denied` / `on`.
  - `enablePush()`: `Notification.requestPermission()` → `syncPush()`.
  - `syncPush()`:
    1. Register `/sw.js` with `updateViaCache: "none"`, so a changed service worker is always fetched fresh.
    2. Wait for `serviceWorker.ready`.
    3. Fetch our public key.
    4. Reuse the existing subscription, or `subscribe({userVisibleOnly: true, applicationServerKey})`. The base64url key is turned into bytes, which is what Safari needs.
    5. POST it.

    It runs on every load once allowed, so the subscription always belongs to whoever is logged in.
  - `stopPush()`: called first in `logout()`, and its failures are swallowed so it can never block logging out.
- `app/push-prompt.tsx` (on the trip card), the **soft ask**:
  - a "Notify me" button: the browser's prompt appears only after a tap
  - "Notifications are on."
  - the "blocked" message pointing at site settings
  - the iPhone "Share → Add to Home Screen" hint
  - nothing at all for unsupported browsers

  `pushState()` reads browser-only values that don't exist during server rendering, so it runs in `useEffect`. That's the one place the lint rule `react-hooks/set-state-in-effect` is switched off, with the reason written next to it.
- **Production:** `docker-compose.prod.yml` requires `VAPID_PRIVATE_KEY`/`VAPID_PUBLIC_KEY` (`:?`) and sets `PUSH_CONTACT: https://${DOMAIN}`. The runbook has the OpenSSL commands. **Make the keys once and keep them:** new keys break every existing subscription. `shortcut:` there's no key rotation handling.

### 59.7 Verified in a real browser, and what couldn't be
- The built-in browser has service workers and `PushManager`, but its notification permission is fixed at **denied**, and it refuses to fetch any service-worker script (even a wrong file gives the same "unknown error", where Chrome would report a wrong type). So a real notification couldn't appear there.
- What was checked: the trip card shows "Notifications are blocked for Namastute…"; there's no prompt on page load and no push requests while blocked; `/manifest.webmanifest` and `apple-touch-icon` are in the page; `sw.js` is served as JavaScript; logout still works.
- The full path was checked by `NotificationsTest`: real VAPID signing and encryption, then decryption with the browser's algorithm. **Still untested:** delivery through Google's, Mozilla's or Apple's real push services. Try it on a phone after deploying, since push needs HTTPS (or localhost) and a real browser.

---

## 60. Live location at the pickup

### 60.1 Terms
- **Geolocation:** the device's own idea of where it is (latitude, longitude). **GPS** is satellites: good outdoors (often 5–10 m), poor under concrete. **Wi-Fi/cell positioning** looks up nearby networks: works indoors but rougher (tens to hundreds of metres).
- **Accuracy radius:** every position comes with "probably within N metres". A map shows it as a circle.
- **Geolocation API:** `navigator.geolocation.watchPosition(onPosition, onError, options)` calls you each time the position changes; `clearWatch(id)` stops it.
- **Secure context:** HTTPS or `localhost`. Location, like push, is only allowed there.
- **Ephemeral data:** passed along and never saved.
- **Throttling:** sending at most every so often, however often updates arrive.
- **Heartbeat:** repeating your last state on a timer, so newcomers learn it and silence can mean "gone".
- **Staleness:** how old a reading is; old positions may be wrong.

### 60.2 The problem
- After locking, strangers must find each other outside a terminal, often at night. "Pillar 12" in chat helps, but pillars get misread, people are on different levels, someone hasn't come out yet.

### 60.3 The naive way, and why it hurts
- Save every phone's position to the database each second; every phone polls for the others.
- **Privacy:** a table of timestamped positions is a record of where people were; leaked, it's a stalking tool, and kept, it needs deletion rules. **Load:** about 8 requests a second per group, for data useless a minute later. **Trust:** sharing that starts by itself or never stops scares people off.

### 60.4 The design
1. **Your own tap**, every time ("Share my location"), then the browser's prompt: the soft ask again (58.4).
2. **Relay, don't store:** SEND to `/app/groups/{id}/location` → the server checks membership and the window → broadcast on `/topic/groups.{id}.location`. Never written to the database or the logs.
3. **A server-enforced window:** locked or met groups only, from 20 minutes before meeting to 30 after. Outside it the server drops positions, whatever the browser does.
4. **Throttle + heartbeat in the browser:** send after moving 10 m, or every 15 s anyway. The heartbeat means latecomers see you within 15 s without the server remembering anything.
5. **Staleness on screen:** "Asha · about 150 m away (± 40 m) · 20 s ago"; dots hidden after 2 minutes of silence; Stop sends "stopped" so your dot disappears at once.
6. **Distance:** haversine, the same formula as the trips module's `Geo`, in the browser.

### 60.5 Reality checks
- **Indoors, accuracy can be 100 m or worse:** show the circle and say so. Live location helps; "pillar 12" stays the main way to meet.
- **Web pages can't track in the background:** a locked phone or hidden tab stops `watchPosition`. Only native apps can. So: "Keep Namastute open".
- **Battery:** GPS drains it: another reason for the window and Stop.
- **Blocked permission:** a clear message pointing at site settings.
- **Safety:** exact positions, but only to your group and only around pickup; someone you blocked can't be in your group (56).

### 60.6 Decisions (2026-10-16)
| Decision | Choice |
|---|---|
| When | Locked or met groups, from 20 min before meeting to 30 min after (enforced by the server) |
| How it starts | Your own tap every time, with a visible "Sharing · Stop" bar |
| Stored? | No: relayed only, never in the database or logs |
| Precision | Exact, to your group only, with the accuracy circle |
| Display | A small Leaflet map plus "about 150 m away (± 40 m) · 20 s ago" |
| Rate | Send after moving 10 m or every 15 s; dots hidden after 2 min |

---

## 61. Code walkthrough: live location

### 61.1 `groups/GroupService`
- `SHARE_BEFORE = 20 min`, `SHARE_AFTER = 30 min`: the window, defined once.
- `sharingTrip(groupId, userId)`, public for chat: one query returns your **trip id** (the opaque handle, 56) only if you're an accepted member of a **locked or met** group and `now() between meet_at - make_interval(mins => 20) and meet_at + make_interval(mins => 30)`. `make_interval` wants an `integer`, so the Java side casts the minutes to `int` (a `long` arrives as `bigint`, which Postgres won't quietly narrow).
- `GroupView` gains `sharingFrom` / `sharingUntil`: the browser reads the window from the API instead of repeating the 20/30 rule.

### 61.2 `chat`: one more topic, one more handler
- `StompAuth`: the SUBSCRIBE pattern now allows `chat|updated|location`, still for members only.
- `ChatController.location(...)`: `@MessageMapping("/groups/{groupId}/location")` with `Position(Double lat, Double lng, Double accuracy)`. `lat` null = "I stopped sharing".
- `ChatService.shareLocation(...)`:
  - `sharingTrip(...)` or `NOT_PICKUP_TIME` (dropped);
  - `isPosition`: `|lat| ≤ 90`, `|lng| ≤ 180`, accuracy 0–100 km or absent; NaN and infinity fail these comparisons by themselves;
  - broadcast `Located(tripId, sharing, lat, lng, accuracy)`. Nothing saved, nothing logged (the exception messages carry no numbers).
  - `shortcut:` no server-side rate limit; the browser throttles itself.
- A refused STOMP message is only logged on the server (the same shortcut as chat); the browser knows the window, so it shouldn't send outside it.

### 61.3 Test: `ChatTest.locationReachesTheGroupOnlyAroundPickupAndIsNeverStored`
Real STOMP, step by step:
1. Open group → Keshav's position doesn't reach Asha.
2. Lock, move `meet_at` to now + 10 min → `/groups/current` has `sharingFrom/Until`; the position arrives with Keshav's **trip id** and the exact numbers.
3. `lat: 200` → dropped. `{lat: null}` → `sharing: false`.
4. A stranger subscribing to `.location` gets nothing (refused by `StompAuth`).
5. Meeting moved an hour away → dropped again (before the window).
6. The `messages` count is unchanged: positions are relayed, not saved.
- Gotcha: sending `{}` failed with "Payload value must not be empty", so "stopped" is sent as `{"lat": null}`, which is what the browser sends anyway.
- **Proven to fail:** with the status and time conditions removed from `sharingTrip`, step 1 went red. The full log of a run contained none of the test coordinates.
- 85 API tests, all green.

### 61.4 Web
- `lib/api.ts`: `stomp(onConnect, onClose)` now holds what every connection needs (URL, reconnect every 3 s, fresh token before each connect); `connectGroup` (chat) and the new `connectLocation` both use it. `sendLocation(client, groupId, position | null)`. `Located` is typed by hand, with a comment: it travels only over STOMP, so it isn't in `openapi.json`.
- `app/pickup-location.tsx` (inside the group card):
  - a 5-second clock (`now`) drives "20 s ago", stale dots and the window opening and closing;
  - its **own** connection, opened only while the window is open; closing the window or leaving the page clears the watch and sends "stopped";
  - `watchPosition(..., {enableHighAccuracy: true, maximumAge: 5000, timeout: 30000})`; a position is sent if nothing was sent yet, you moved ≥ 10 m, or 15 s passed; the heartbeat effect re-sends the latest position every 15 s when you stand still;
  - errors: "Location is blocked… site settings" or "Couldn't find your location. Tell your group your pillar number in the chat instead.";
  - before the window: "You can share your live location from 5:09 pm…".
- `app/pickup-map.tsx`: Leaflet (loaded with `ssr: false`, like the trip map): a dot and an accuracy circle per person (you blue, others red), centred once and then left alone so it doesn't jump while you pan.

### 61.5 Verified in a real browser (Keshav in the browser, Asha played by a Node script)
- The script logged Asha in through the API, joined the locked group and sent her position every 5 s over STOMP (`@stomp/stompjs` and Node's built-in `WebSocket`).
- Before sharing, Keshav already saw "Asha · 0 s ago" and her dot.
- The built-in browser's location permission is **denied**: tapping Share showed "Location is blocked for Namastute…", then the button came back. That's the real blocked path.
- For the sharing path, `navigator.geolocation.watchPosition` was replaced in the test page by one fixed position (the same trick as `window.confirm` in 57.7). Then:
  - the bar said "Sharing your location with your group until 5:04 pm… Accurate to about 25 m. Stop";
  - "Asha · about 150 m away (± 40 m)" (by hand: 0.001° lat ≈ 111 m, 0.001° lng ≈ 98 m → ~148 m);
  - the map showed both dots and circles;
  - Asha received Keshav's position, then 2 heartbeats in ~35 s from a single fix;
  - Stop → `clearWatch` called, Asha received `sharing: false`; Asha's own stop removed her dot ("No one else is sharing…");
  - the coordinates appeared nowhere in the API log or the `messages` table.
- Meeting moved an hour later → "You can share your live location from 5:09 pm".
- **Not tested:** a real phone's GPS, and how accurate it is inside IGI's terminals.

---

## 62. Ratings after the ride

### 62.1 Terms
- **Reputation system:** lets strangers judge whether to trust someone, from others' experience (Uber stars, Airbnb reviews, eBay feedback).
- **Aggregate:** one number summarising many rows (`count(*)`, `sum(...)`, "92% thumbs up").
- **Denormalisation:** storing a value you *could* calculate (a `rating_count` column instead of counting rows), so reads are cheap. The price: you keep it correct yourself, **in the same transaction** as the change.
- **Rating inflation:** on 5 stars almost everyone gives 5, so 4 secretly means "bad" and the number says little.
- **Cold start:** a new user has no ratings. **Minimum sample:** only show an aggregate once there are enough ratings to mean something.
- **Retaliation:** "you rated me down, so I'll rate you down". It kills honest ratings when people can see who rated them.
- **Collusion / Sybil attack:** friends or fake accounts rating each other up; a Sybil attack is one person with many fake identities.

### 62.2 The problem, the naive way, and why it hurts
- Before sharing a cab with strangers at night, you see only a first name and an area: no trust signal.
- **Naive:** a public 5-star average, and anyone can rate anyone at any time.
- **Why it hurts:** fake ratings both ways (collusion, Sybils, angry strangers); retaliation once people can see who rated them; inflation; one early 1-star makes a newcomer look like the worst user; and if low ratings banned people automatically, ratings would become a harassment tool (the lesson of reports, 56).

### 62.3 The design
1. **Only people you actually rode with:** accepted members of a group that reached **met**, the same "real ride" evidence reports use. Fakes would need a phone number with an OTP *and* a real ride each.
2. **Once per person per ride, final:** the V1 primary key `(group_id, rater_id, ratee_id)` refuses a second one.
3. **👍 / 👎 instead of stars:** honest about what people mean; no inflation.
4. **Private ratings, public total:** nobody in the app, not even the person rated, sees who gave what. Admins can, with SQL.
5. **Minimum sample of 3:** below that, "New" instead of a percentage.
6. **Counters (as the design planned):** `users.rating_count` and `rating_sum` change in the **same transaction** as the new `ratings` row, so they can't disagree, and showing ratings costs no extra counting.
7. **No automatic consequences:** no bans, no blocks, no change to match ranking (ranking by ratings is in the design's later column). A 👎 points to Report for anything serious.

### 62.4 Limits
- Ratings are given on the group card, which is shown until the group closes (4 hours after meeting). Forget, and the chance is gone; a reminder could come later.
- In a group of two, the other person can work out what you gave by watching their own total change. Hiding that would need delays or batching, which isn't worth it yet.

### 62.5 Decisions (2026-10-16)
| Decision | Choice |
|---|---|
| Scale | 👍 / 👎 (V7 migration; the table was empty) |
| Who and when | Accepted members of a met group; each other member once; final; while the group card is shown |
| Where shown | Matches, requests you received, the group card: "92% 👍 · 12 ratings", or "New" below 3 |
| Privacy | Who gave what is never shown in the app; only the total |
| Counting | The existing `users` counters, updated in the same transaction |
| Consequences | None automatic; a 👎 shows a "use Report" hint |

---

## 63. Code walkthrough: ratings

### 63.1 `V7__thumbs_ratings.sql`
- `drop column score` (the 1–5 stars) and `add column up boolean not null`. Safe because nothing could write ratings before, so the table was empty everywhere. Flyway stays forward-only: V1 is never edited.

### 63.2 `users/UserService` (the users module owns the counters)
- `Rating(int count, Integer upPercent)`: `upPercent` is null below `MIN_RATINGS = 3`.
- `ratings(ids)`: one query (`select id, rating_count, rating_sum from users where id in (:ids)`) for everyone on a screen, like `firstNames` (no N+1). An empty list returns early, because SQL `in ()` is a syntax error.
- `rating(count, ups)`: `Math.round(100.0 * ups / count)`, so 11 of 12 → 92.
- `addRating(userId, up)`: `rating_count + 1`, `rating_sum + (up ? 1 : 0)`. Only called inside feedback's transaction.

### 63.3 `groups/GroupService`
- `metWith(me, theirTripId)` → `RideTogether(groupId, userId)`: a self-join on `group_members` ("them" and "me" both accepted) joined to `groups` with `status = 'met'`, excluding yourself. A met group closes 4 hours after meeting (`finish` sweep, 49), which is what ends the rating window.
- `memberTripsByUser(groupId)`: user id → trip handle, so ratings stored by user can be shown against the handles the card uses (users ids never reach the browser, 56).
- `RequestView` and `MemberView` gain `rating`; `current()` and `requests()` fetch them in one query each.

### 63.4 `trips/TripService`
- `Rider` gains `rating`: `matches()` collects the rider ids once and asks for names and ratings with them.

### 63.5 The `feedback` module (owns `ratings`)
- `rate(me, tripId, up)`, `@Transactional`:
  1. `groups.metWith(...)` or 404 `NOT_RATEABLE`;
  2. `insert ... on conflict do nothing`: 0 rows → 409 `ALREADY_RATED` (final: no change of mind);
  3. `users.addRating(...)`. Same transaction, so the row and the counters commit together or not at all.
- `myRatings(me, groupId)`: members only; only the ratings **you** gave, as `MyRating(tripId, up)`.
- `POST /api/v1/ratings {tripId, up}` → 204; `GET /api/v1/groups/{id}/ratings` → your ratings in that group.

### 63.6 Tests
- `FeedbackTest.ratingsNeedAMetRideHappenOnceAndShowOnlyAsATotal`:
  - before meeting → 404;
  - after lock + both "I'm here": 👍 → 204; a second rating → 409; rating yourself → 404; a stranger → 404;
  - "my ratings" shows only your own; the counters are 1/1; the card shows `count 1`, `upPercent null`; with 12/11 → `92`.
- **Proven to fail:** without `g.status = 'met'`, the first step answered 204 instead of 404.
- `MatchesTest`'s privacy guard counted 3 fields per rider; it now names `rating` and expects 4, so any *other* new field still fails it.
- Gotchas:
  - A JSON filter (`$.members[?(...)]`) returns a **list**, so "is null" is checked as `contains(nullValue())`.
  - The first version created an extra searching trip at Terminal 2 for no reason. With today's earlier runs' leftovers, that pushed an expected match out of an older test's top 5. Removing the extra trip fixed it. **Lesson:** tests sharing a database must be careful about what they leave behind.
- 86 API tests, all green.

### 63.7 Web
- `lib/format.ts` `ratingText(r)`: "92% 👍 · 12 ratings" or "New". Used for match riders, received requests and group members.
- `lib/api.ts`: `rateTraveller(tripId, up)`, `getMyRatings(groupId)`.
- `app/ride-ratings.tsx` (shown in met groups): "How was the ride? Only the total is shown to others…", then per other member:
  - 👍 / 👎 buttons, with `aria-label`s like "Thumbs down for Asha" for screen readers;
  - or "you gave 👍";
  - or "you gave 👎. If something serious happened, use Report next to Asha's name."

### 63.8 Verified in a real browser (as Keshav; Asha's side through the API)
- The group went to met (both "I'm here" through the API). The card showed "Asha (New)" and "How was the ride? Asha: 👍 👎".
- 👎 → the Report hint. After a reload it still said "you gave 👎", so no second chance appeared. Database: one row, Asha's counters 1 / 0.
- With Asha's counters temporarily at 12 / 11: "Asha (92% 👍 (12))". The nested brackets read badly, so the format became "92% 👍 · 12 ratings" ("Asha (92% 👍 · 12 ratings)"). The counters were then put back.
- **Not checked in the browser:** the summary in matches and received requests (Keshav was already in a group); they use the same `ratingText`, and `MatchesTest` checks the API side.

---

## 64. Command cheat sheet

### After a PR is merged (from `Namastute`)
```bash
git switch main
git pull
git branch -D <merged-branch>
git fetch --prune
git switch -c <type/next-feature>
```
- If `git switch main` refuses because of uncommitted changes: `git stash` first, run the routine, then `git stash pop` on the new branch.

### Database (from `Namastute`)
```bash
docker compose -f infra/docker-compose.yml up -d      # start Postgres
docker compose -f infra/docker-compose.yml ps         # status (wait for healthy)
docker compose -f infra/docker-compose.yml down       # stop (keeps data)
docker compose -f infra/docker-compose.yml exec postgres psql -U namastute -d namastute   # SQL prompt
```

### Which Redis am I talking to? (PowerShell)
```powershell
Get-NetTCPConnection -LocalPort 6379 -State Listen      # who listens on the Redis port
& "C:\Program Files\Redis\redis-cli.exe" -h 127.0.0.1 KEYS "rate:otp:*"   # the Windows Redis the API uses today
docker compose -f infra/docker-compose.yml exec redis redis-cli KEYS "*"      # the project's Docker Redis
```

### When a Java record changes (API types)
```bash
cd apps/api && ./mvnw test          # rewrites apps/web/openapi.json
cd ../web && pnpm api:types         # regenerates lib/api-types.ts; then fix TypeScript errors and commit both files
```

### Production stack (from `Namastute`; details in `infra/README.md`)
```bash
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env up -d --build   # build and start everything
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env ps             # healthy?
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env logs -f api    # follow API logs
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env down           # stop (keeps data)
```

### Query plans (inside `psql`)
```sql
explain <query>;                 -- the plan Postgres would use (doesn't run it)
explain analyze <query>;         -- runs it too: real row counts and times
set enable_seqscan = off;        -- this session only: see whether an index CAN be used on a tiny table
```

### API (from `apps/api`)
```bash
.\mvnw.cmd spring-boot:run    # run the app (http://localhost:8080/actuator/health)
.\mvnw.cmd test               # run tests (needs Postgres up)
```

### Redis (from `Namastute`)
```bash
docker compose -f infra/docker-compose.yml exec redis redis-cli   # then SET/GET/TTL/INCR..., exit to leave
```

### Web (from `apps/web`)
```bash
pnpm install
pnpm dev      # http://localhost:3000
pnpm lint
pnpm build    # production build, same as CI
```

### Git
```bash
git switch main && git pull
git switch -c feat/short-description
git status
git diff --staged
git add <paths>
git commit -m "type: description"
git push -u origin <branch>       # first push of a branch
gh pr create --base main --title "type: description" --body "..."
gh pr checks <number>
git branch -D <merged-branch>
git fetch --prune
```

---

## 65. Glossary

| Term | Meaning |
|---|---|
| Annotation | `@Something` tag adding information to code without changing it |
| ApplicationContext | Spring's IoC container |
| Bean | An object created and managed by Spring |
| BOM | Bill of Materials: a list of library versions tested together |
| Bytecode | Compiled `.class` instructions run by the JVM |
| Checksum | A fingerprint of a file's contents; changes if the file changes |
| Classpath | All libraries available to the app at runtime |
| Connection pool | Reusable, ready-made database connections |
| Container (Docker) | A running, isolated copy of an image |
| CI | Continuous Integration: automatic build + test on every change |
| DDL | SQL that changes structure (`create table`) |
| DI | Dependency Injection: dependencies handed in from outside |
| Extension (Postgres) | Add-on giving the database new abilities (PostGIS) |
| Healthcheck | A test of whether a service is really ready |
| Hot reload | Browser updates automatically when a file is saved |
| Image (Docker) | Packaged, read-only snapshot of a program and its environment |
| IoC | Inversion of Control: the framework creates and wires objects |
| JDBC | Java's standard database API |
| JPA / Hibernate | Java's ORM standard / its implementation |
| Lifecycle (Maven) | Ordered build phases: compile → test → package → verify |
| Linter | Tool that flags likely bugs without running code |
| Lockfile | Exact versions of all installed packages |
| Migration | A versioned SQL change to the database structure |
| ORM | Object-Relational Mapping: objects ↔ table rows |
| Port | Numbered "door" a program listens on |
| Registry | Online store of packages or images (Maven Central, npm, Docker Hub) |
| Runner | The VM that runs a CI job |
| Schema (DB) | A namespace for tables inside a database |
| Singleton | One shared instance of a bean |
| SNAPSHOT | A Maven version still in development |
| Squash merge | All PR commits combined into one commit on `main` |
| SRID 4326 | ID of the GPS coordinate system (WGS 84) |
| Starter | One dependency bringing a tested set of libraries |
| TOTP | Time-based one-time password (authenticator apps) |
| Volume | Docker storage that outlives containers |
| Atomic | Happens as one indivisible step; can't be interleaved |
| Authentication / Authorization | Who you are / what you may do |
| Dockerfile | Recipe for building your own image |
| Endpoint | One HTTP method + path the API accepts |
| Hash | One-way fixed-length fingerprint of data |
| HMAC | A hash mixed with a secret key |
| HTTP | Request/response protocol between browser and server |
| IP address | The network address a request comes from |
| JSON | Text format for data: `{"key": value}` |
| Key-value store | Database that saves values under keys (Redis) |
| OTP | One-time password |
| Race condition | Bug where the result depends on the timing of simultaneous operations |
| Rate limiting | Capping how often something can be done |
| REST | API style: URLs name things, methods say what to do |
| Stateless | Each request stands alone; the server remembers nothing between them |
| Status code | 3-digit result of an HTTP request (2xx/4xx/5xx) |
| Timing attack | Learning secrets from how long comparisons take |
| TTL | Time to live: automatic expiry of a key |
| YAML | Indentation-based settings format |
| Access token | Short-lived JWT sent with every request |
| Base64URL | Encoding bytes as URL-safe text (not encryption) |
| Bearer token | A token whose holder is trusted; sent as `Authorization: Bearer ...` |
| Claim | A statement inside a JWT (`sub`, `exp`, `iss`, `iat`) |
| Cookie | Data the browser stores and sends back automatically |
| CSRF | Another site making the user's browser send requests to us |
| Entity | A class mapped to a database table |
| Filter | Code that inspects a request before the controller |
| JPQL | SQL-like query language using entity and field names |
| JWT | JSON Web Token: header.payload.signature |
| Optional | A box that holds a value or is empty |
| Principal | The authenticated caller of a request |
| Refresh token | Long-lived random token used only to get new access tokens |
| Repository | Interface whose database code Spring generates |
| Rotation | Replacing a token every time it's used |
| Transaction | Database changes that all happen or none do |
| XSS | Attacker's script running on our page |
| Client Component | React component that runs in the browser (`"use client"`) |
| CORS | Server headers allowing another origin to read responses |
| Effect | Code run after a component appears (`useEffect`) |
| Hook | React function starting with `use` that gives components abilities |
| Origin | Scheme + host + port |
| Preflight | Browser's automatic `OPTIONS` permission check before a cross-origin request |
| Promise | A value that will arrive later; `await` waits for it |
| Same-Origin Policy | Browser rule: scripts can't read other origins' responses by default |
| Server Component | React component rendered on the server (Next.js default) |
| Site | Registrable domain, ignoring port and subdomains |
| State | Data a component remembers; changing it redraws the component |
| StrictMode | React dev mode that runs effects twice to expose unsafe ones |
| Web Locks API | Browser-wide lock shared by all tabs (`navigator.locks`) |
| Combining mark | Unicode sign that attaches to the previous letter (Indian vowel signs); `\p{M}` |
| Custom hook | Your own `use...` function bundling React state and logic for reuse |
| Dirty checking | Hibernate detects changed fields and writes them at commit |
| PATCH | HTTP method for changing part of a resource |
| PUT | HTTP method for replacing a whole resource |
| Geocoding | Text → coordinates (forward) or coordinates → place name (reverse) |
| geography (PostGIS) | Points on the round Earth; distances in metres |
| Great circle | Shortest path between two points on a sphere |
| Haversine | Formula for great-circle distance from two lat/lng pairs |
| JTS | Java Topology Suite: Java's standard shapes library (Point…) |
| Latitude / longitude | North–south / east–west position in degrees |
| Native query | Plain SQL in a repository (`nativeQuery = true`) |
| Postgres enum | A column type allowing only listed values (`trip_status`) |
| Prerendering | Building a page ahead of time instead of per visitor |
| Ref (React) | A value kept between redraws without causing one (`useRef`) |
| Tile | One square image of a map at a given zoom level |
| WKT | Well-Known Text: `POINT(lng lat)` |
| Bounding box | Smallest rectangle containing a shape or a group of shapes |
| Candidate | A row that survived the cheap first stage of a search |
| Composite index | One index over several columns, sorted by the first, then the next |
| Data minimisation | Collect and share only the personal data a feature really needs |
| EXPLAIN | Shows the plan Postgres chose for a query (`ANALYZE` also runs it) |
| GiST | Generalized Search Tree: Postgres index framework for unsortable data like shapes |
| Greedy algorithm | Takes the best-looking step now and never reconsiders |
| N+1 query problem | 1 query for a list, then 1 more per item; fix with one `in (…)` query or a join |
| Partial index | Index holding only rows matching a `where` |
| Pure function | Output depends only on inputs; no side effects |
| Query planner | The part of Postgres that decides how to run a query |
| R-tree | Tree of nested bounding boxes; how PostGIS indexes points |
| Seq Scan | Reading every row of a table |
| Side effect | Anything a function does besides returning a value (DB, network, clock…) |
| Trilateration | Finding a hidden point from its distances to known points |
| Two-stage search | Cheap prefilter in the database, then exact ranking in code |
| Anti join | Keeps rows that have no matching partner; how Postgres runs `not exists` |
| Self-join | A table joined with itself under two names (`me` and `t`) |
| State machine | A fixed set of states plus the allowed transitions between them |
| Transition | One allowed move between states, e.g. `open → locked` |
| Join request | "I'd like to share with you": pending until accepted or declined |
| Opaque id | A random identifier that reveals nothing about the person (a UUID) |
| Concurrency | Several pieces of work running at the same time on shared data |
| Thread | One line of execution inside a program; a server runs many at once |
| Check-then-act (TOCTOU) | Bug where the fact you checked is stale by the time you act on it |
| Lost update | Two writers start from the same old value; one overwrites the other |
| ACID | Atomic, Consistent, Isolated, Durable: what a transaction guarantees |
| READ COMMITTED | Postgres's default isolation level: you only see committed data |
| Row lock | Postgres's hold on a row being changed; other writers wait |
| Pessimistic locking | Lock the row when reading it (`select ... for update`) |
| Optimistic locking | Detect conflicts with a `version` column and retry |
| Deadlock | Two transactions each waiting for a lock the other holds |
| Proxy (Spring) | Wrapper object Spring puts around a bean to add transactions etc. |
| Self-invocation | A method calling another in the same class, bypassing the proxy |
| ExecutorService | A pool of threads that run submitted tasks |
| CountDownLatch | A gate that makes threads wait until it's opened |
| AtomicInteger | A counter that's safe to change from many threads |
| Testcontainers | Library that starts throwaway databases in Docker for tests |
| JdbcClient | Spring's fluent helper for running plain SQL (`db.sql(...).param(...).update()`) |
| RETURNING | Postgres clause that hands back values from the rows just inserted/updated |
| Upsert | Insert, or update if the row already exists (`on conflict ... do update`) |
| Foreign key | A column that must point at an existing row in another table |
| Circular import | Two files importing each other; avoided by moving shared code to a third file |
| Travelling salesman problem | Finding the shortest route through all stops; orders grow as n! |
| Heuristic | A quick rule giving a good (not guaranteed best) answer |
| Brute force | Trying every possibility (e.g. all 24 orders of 4 drops) |
| Refactoring | Changing how code is written without changing what it does |
| Regression | Something that used to work breaking after a change |
| Dependency cycle | Module A depends on B and B on A; neither stands alone |
| Dependency Inversion | Depend on an interface you own; others implement it (the D in SOLID) |
| Port / adapter | The interface a module defines / the code that plugs into it |
| OptionalDouble | Java's box for "a number, or nothing" |
| Effectively final | A variable that never changes after it's set; only these can be used inside a lambda |
| git stash | Puts uncommitted changes on a temporary shelf (`git stash pop` brings them back) |
| Polling | Asking the server "anything new?" again and again |
| Latency | The delay between something happening and you seeing it |
| Server push | The server sending data without being asked |
| WebSocket | A long-lived two-way connection between browser and server |
| Full-duplex | Both sides can send at the same time |
| STOMP | Text protocol over WebSocket with destinations (CONNECT, SUBSCRIBE, SEND, MESSAGE) |
| Pub/sub | Publish to a topic; every subscriber gets a copy |
| Message broker | The part that delivers published messages to subscribers |
| ChannelInterceptor | Spring checkpoint that every incoming STOMP frame passes through |
| CSWSH | Cross-site WebSocket hijacking: another site opening a socket with your cookies |
| Optimistic UI | Showing your action as done before the server confirms it |
| Deduplication | Recognising the same item arriving twice and keeping one |
| Cursor pagination | "Items older than id X": stable and index-friendly, unlike OFFSET |
| Application event | A message one module publishes inside the app; listeners react without the publisher knowing them |
| @TransactionalEventListener | Runs a listener at a transaction phase, by default after commit |
| REQUIRES_NEW | Start a brand-new transaction, separate from any current one |
| Outbox | Saving events in the same transaction as the change, then delivering them reliably |
| Supply-chain risk | Danger of a malicious or broken release of a library you depend on |
| Background job | Code the server runs on a timer, with no request behind it |
| Sweep | A periodic job that handles everything that's due in one go |
| @Scheduled | Spring annotation that runs a method on a timer (needs @EnableScheduling) |
| Idempotent | Running it twice has the same effect as running it once |
| Clock skew | Small differences between the clocks of different machines |
| Floating point | How double stores numbers in binary; most decimal fractions are slightly off |
| BigDecimal | Java's exact decimal number type, used for money |
| Minor units | Money stored as an integer count of the smallest unit (paise) |
| Rounding mode | The rule for .5 and other fractions: HALF_UP, HALF_EVEN, FLOOR, CEILING |
| UPI / VPA | India's instant bank payments / a payment address like name@bank |
| Deep link | A URL that opens an app (e.g. `upi://pay?...`) instead of a web page |
| Connection pool (HikariCP) | A fixed set of reusable database connections (10 by default) |
| Pool starvation | Every connection is taken and threads wait forever for one more |
| TransactionSynchronization | Spring hook to run code at a transaction moment, e.g. afterCommit |
| API contract | The agreed shape of every request and response between two sides |
| Drift | Two copies of the same truth slowly stop matching |
| OpenAPI (Swagger) | Standard document describing an HTTP API's paths and data shapes |
| Code-first / contract-first | Generate the spec from code / write the spec first and generate code |
| Code generation | A program writing source code from a description |
| Drift check | CI regenerates a file and fails if it differs from the committed one |
| AsyncAPI | OpenAPI's sibling standard for message-based APIs (WebSocket, queues) |
| springdoc | Library that generates an OpenAPI document from Spring controllers |
| Deterministic | Same input always gives byte-identical output |
| BOM | Byte-order mark: invisible bytes at a file's start that some tools reject |
| Twelve-Factor App | Rules for server software; factor III: config lives in the environment |
| Spring profile | A named set of settings switched on with SPRING_PROFILES_ACTIVE |
| Fail fast | Stop at startup when something essential is missing, instead of running wrongly |
| Multi-stage build | A Dockerfile that builds in a big image and ships only the result in a small one |
| Attack surface | Everything an attacker could try to use; smaller is safer |
| Reverse proxy | A server in front of your apps that receives requests and forwards them |
| TLS termination | Decrypting HTTPS at the proxy, then forwarding plain HTTP privately |
| Let's Encrypt / ACME | Free certificate authority / the protocol for proving you own a domain |
| X-Forwarded-For | Header a proxy adds with the real client's IP address |
| Standalone output | Next.js build mode producing a small self-contained server.js |
| Profile expression | Spring condition like "!prod \| fake-sms" (not prod, or fake-sms) |
| Named matcher (Caddy) | A reusable request condition, e.g. @api path /api/* /ws |
| Runbook | Step-by-step instructions for operating a system |
| Threat modelling | Systematically asking how a feature could be misused |
| Abuse case | A specific way a bad actor could misuse a feature |
| Silent block | Blocking without telling the blocked person anything |
| Weaponised reporting | Using a report button to harass or get someone banned unfairly |
| Service worker | Background JavaScript the browser can wake even when no tab is open |
| Push service | The browser maker's server that delivers pushes to devices |
| Push subscription | A browser's endpoint URL + keys for receiving pushes |
| VAPID | Our server's key pair; pushes are signed with it so only we can push to our subscribers |
| End-to-end encryption | Only sender and receiver can read it; carriers in between can't |
| Soft ask | Your own "turn on notifications?" button before the browser's real prompt |
| PWA / web app manifest | An installable web app / the file describing its name, icons and colours |
| SSRF | Server-Side Request Forgery: tricking a server into requesting a URL of the attacker's choosing |
| Allowlist | A list of the only values accepted; everything else is refused |
| ECDH | Elliptic-Curve Diffie-Hellman: two sides combine their own private key with the other's public key to get the same shared secret |
| HKDF | HMAC-based Key Derivation Function: turns a shared secret into the exact keys you need |
| Upsert | Insert, or update if the row already exists (`on conflict ... do update`) |
| Notification tag | A label on a notification; a new one with the same tag replaces the old one |
| SimpUserRegistry | Spring's live list of who has an open WebSocket connection |
| Geolocation API | The browser feature for reading the device's position (`watchPosition`, `clearWatch`) |
| Accuracy radius | "Probably within N metres": how sure a position is, drawn as a circle |
| Secure context | A page on HTTPS or localhost; browsers only allow location, push and similar features there |
| Ephemeral data | Data that is passed along and never saved |
| Throttling | Sending at most every so often, however often updates arrive |
| Heartbeat | Repeating your last state on a timer, so newcomers learn it and silence means "gone" |
| Staleness | How old a reading is; old positions may be wrong |
| Reputation system | A way for strangers to judge trust from others' experience (stars, reviews, thumbs) |
| Aggregate | One number summarising many rows, e.g. a count or a percentage |
| Denormalisation | Storing a value you could calculate, for cheap reads; you must keep it correct yourself |
| Rating inflation | Almost everyone gives the top score, so the scale stops carrying information |
| Cold start | A new user (or item) with no history yet |
| Minimum sample | Only show an aggregate once there is enough data for it to mean something |
| Retaliation | Rating someone down because they rated you down |
| Sybil attack | One person running many fake identities to sway a system |

---

## 66. Interview questions to practise

1. Why a modular monolith instead of microservices? How do you *enforce* the module boundaries?
2. What is Inversion of Control? What problem does dependency injection solve?
3. How does Spring Boot decide to create a database connection pool without any setup code?
4. Why use Flyway instead of `ddl-auto: update`? What happens if someone edits an applied migration?
5. What's the difference between a Docker image and a container? A container and a VM?
6. Why does CI run on a clean machine? Give an example of something it caught in this project.
7. What does "require branches to be up to date before merging" protect against?
8. Why is an AWS budget not enough on its own to stop spending? Why exclude credits?
9. Why not Kubernetes for this project? When would you add it?
10. What's the difference between a unit test and an integration test? Which kind is `contextLoads`?
11. Why is HTTP called stateless, and what does that force a login system to do?
12. Why store an HMAC of the OTP instead of a plain SHA-256 hash?
13. Why keep OTPs in Redis rather than Postgres? What happens if Redis loses them?
14. Explain a fixed-window rate limiter and its weakness. What would you use instead?
15. What is a race condition? Give one from this project and how to prevent it.
16. Sessions vs JWTs: trade-offs? Why do we use both an access and a refresh token?
17. Anyone can read a JWT's payload. Why is it still safe to trust it?
18. When would you choose RS256 over HS256?
19. Why is a plain SHA-256 fine for refresh tokens but not for OTP codes?
20. How does refresh-token reuse detection work, and why must its "revoke all" not be inside the same transaction as the error?
21. What do HttpOnly, Secure and SameSite each protect against?
22. What does Spring Security's filter chain do with a request before your controller runs?
23. What problem does the Same-Origin Policy solve, and why doesn't it apply to Postman?
24. What is a CORS preflight, and why must `Allow-Origin` never be `*` with credentials?
25. Origin vs site: why does it matter for SameSite cookies when deploying?
26. Server vs Client Components in Next.js: how do you choose?
27. Why could two refresh calls at once log a user out everywhere, and how did we prevent it?
28. Why is validating input in the browser not enough?
29. PATCH vs PUT. How do you tell "leave unchanged" apart from "remove" in a PATCH?
30. Why does updating a JPA entity need no save() call, and why must the method be @Transactional?
31. A name regex using only \p{L} rejected Hindi names. Why, and what's the fix?
32. Why must the user id for PATCH /me come from the token rather than the request?
33. geometry vs geography in PostGIS: which do you use for real distances, and why?
34. Why is latitude/longitude order a common bug, and how did we guard against it?
35. Why store times in UTC, and how do you handle a time zone for an airport regardless of the user's phone?
36. How does the database enforce "one active trip per user", and why not only check it in Java?
37. Why answer 404 instead of 403 when someone tries to cancel another user's trip?
38. Why must a map library like Leaflet be loaded with ssr: false in Next.js?
39. Why split matching into a database prefilter and in-memory ranking? What must the prefilter never do?
40. Why can't a B-tree answer "points within 15 km", and how does a GiST (R-tree) index do it?
41. Why does `ST_DWithin` use a spatial index while `ST_Distance(...) < x` doesn't?
42. `EXPLAIN` shows a Seq Scan even though an index exists. Is that a bug? How would you check?
43. What is a pure function, and why is a pure ranker easier to test than one that reads the database or the clock?
44. Define the detour for a shared cab. Why is "shared route − my solo route" a misleading measure?
45. What is the N+1 query problem, how do you detect it, and how do you fix it across module boundaries?
46. Which fields may a match show about another traveller, and how could even a distance number leak a secret?
47. Our tests share the dev database. What problem did that cause for the matching test, and what are the proper fixes?
48. What is a state machine, and how can the database itself refuse invalid transitions?
49. Explain the "last seat" race. Why does checking seats in Java and then saving fail under load?
50. What does ACID stand for? Why doesn't "isolated" alone prevent a lost update under READ COMMITTED?
51. Why does `update ... set seats_taken = seats_taken + 1 where seats_taken + 1 <= capacity` stay correct with concurrent requests?
52. Compare pessimistic locking, optimistic locking and a conditional UPDATE. When would you choose each?
53. What is a deadlock, how does Postgres handle it, and how do you avoid one?
54. When does `@Transactional` roll back, and why does calling a `@Transactional` method from the same class not start a transaction?
55. How do you write a test that reliably exposes a race condition, and how do you know the test itself works?
56. Why does accepting a request run in one transaction, and what did the "3 + 3 seats" test prove about rollback?
57. Why did we use plain SQL (`JdbcClient`) for groups instead of JPA entities?
58. Two people who asked each other both press Accept at the same time. What could go wrong, and how does one `UPDATE` over both trips avoid it?
59. What is the travelling salesman problem, and why is a greedy nearest-first route acceptable for our cabs?
60. Show an example where greedy nearest-first gives a worse route than the best order.
61. How do existing tests protect a refactor from introducing a regression?
62. What is a dependency cycle between modules, why is it harmful, and how does dependency inversion break it?
63. Why is reading another module's tables with SQL "the same cycle, just hidden"?
64. Why can a lambda use `from` but not `at` in the greedy loop?
65. How did the old ranker tests prove the N-rider refactor kept pair behaviour the same?
66. Why is polling a poor fit for chat? What does a WebSocket handshake look like?
67. What does STOMP add on top of a WebSocket, and what is pub/sub?
68. How do you authenticate a WebSocket when the browser can't send an Authorization header?
69. Why must the server check membership on every SUBSCRIBE, not just at CONNECT?
70. Why save a message before broadcasting it? How does a clientId prevent duplicates with optimistic UI?
71. Why is cursor pagination better than OFFSET for chat history?
72. Why does Spring's simple broker stop working with two server instances, and what replaces it?
73. Why use an application event (GroupChanged) instead of groups calling chat directly?
74. What goes wrong if a @TransactionalEventListener writes to the database without REQUIRES_NEW?
75. Why must the server refuse STOMP SEND frames aimed straight at /topic/...?
76. Lazy expiry vs a background sweep: what can't lazy expiry do?
77. What are the two traps of in-memory scheduled tasks, and how does a sweep with conditional UPDATEs avoid them?
78. What does idempotent mean, and why must background jobs be idempotent?
79. How do you test code that depends on time without waiting?
80. Why does GroupJobs live in its own class instead of a @Scheduled method inside GroupService?
81. Why does the sweep expire forgotten open groups before auto-locking?
82. Why must money never be stored in a double? What do you use in Java, Postgres and JavaScript?
83. Rounding each share separately can make the total wrong. How does "the booker absorbs the remainder" fix it?
84. What does a UPI deep link contain, and why doesn't Namastute confirm or hold payments?
85. Two members tap "I'm here" at the same instant and the group never becomes met. Why, and how does `select ... for update` fix it?
86. How can an after-commit listener with REQUIRES_NEW starve a connection pool? How did we fix it?
87. Why is the fare entered with one conditional UPDATE (`... and booker_id = ? and fare_total is null`) rather than checks in Java?
88. What is API drift between a backend and a frontend, and why doesn't TypeScript catch it on its own?
89. Code-first vs contract-first OpenAPI: when would you choose each?
90. Should generated files be committed? How does a CI drift check keep them honest?
91. springdoc ignored @Nullable. How did we still get `string | null` in TypeScript, and why the 3.0 format?
92. Why must a generated file be deterministic for a CI drift check to work?
93. What does "config in the environment" mean, and why should production secrets have no defaults?
94. Why use a multi-stage Docker build? What does running as non-root protect against?
95. What does a reverse proxy like Caddy do, and how does it get a free HTTPS certificate?
96. Behind a proxy every request seems to come from one IP. How do you get the real client IP safely?
97. Why are NEXT_PUBLIC_ variables a deployment gotcha?
98. How did we prove the production API refuses to start without a secret or a real SMS sender?
99. Why can't MockMvc test forwarded-header handling, and what did we use instead?
100. What is threat modelling? Name three abuse cases for a "report user" button and the rule each forces.
101. Why must blocking be silent, and how can an error message leak that someone blocked you?
102. Why do reports go to a human instead of automatically banning someone?
103. How does the code make sure you can only report someone you actually met?
104. Why does unblocking use a separate opaque id instead of the blocked person's user id?
105. Walk through how a Web Push message travels from your server to a phone. Who can read it on the way?
106. What are VAPID keys for?
107. Why ask for notification permission with your own button first?
108. What is SSRF, and why does accepting push subscriptions create that risk? How do you prevent it?
109. Why are pushes sent after the transaction commits, and asynchronously? What would go wrong otherwise?
110. Why is checking that a host "ends with push.apple.com" not enough? What else does the endpoint check refuse?
111. How did the tests prove the push is really encrypted correctly, without a real phone?
112. Two people use the same browser one after the other. How do you stop the second one getting the first one's notifications?
113. Why relay live locations instead of storing them? What would storing them oblige you to do?
114. Why must the server, not the browser, enforce when location sharing is allowed?
115. Why send a heartbeat when the phone isn't moving? What problem does it solve without server memory?
116. What can't a web page do with location that a native app can, and how did the design work around it?
117. How did we test location sharing in a browser that blocks location, and what does that test not prove?
118. How do you stop fake or revenge ratings in a ride-sharing app?
119. Why thumbs up/down instead of 5 stars?
120. Why store rating counters on the user row, and how do you keep them consistent with the ratings table?
121. Why show "New" instead of a percentage for someone with one or two ratings?
122. Two tests share a database and one starts failing after the other changes. What's happening, and how do you avoid it?
