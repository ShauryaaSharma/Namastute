# Namastute — Learning Notes

My revision notes from building Namastute. Organised by topic, not by date. Each section: the idea, why it exists, and where it shows up in this project.

Last updated: 2026-10-13 (login tokens: JWT, refresh rotation, Spring Security, users module).

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
28. [Command cheat sheet](#28-command-cheat-sheet)
29. [Glossary](#29-glossary)
30. [Interview questions to practise](#30-interview-questions-to-practise)

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

## 28. Command cheat sheet

### Database (from `Namastute`)
```bash
docker compose -f infra/docker-compose.yml up -d      # start Postgres
docker compose -f infra/docker-compose.yml ps         # status (wait for healthy)
docker compose -f infra/docker-compose.yml down       # stop (keeps data)
docker compose -f infra/docker-compose.yml exec postgres psql -U namastute -d namastute   # SQL prompt
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

## 29. Glossary

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

---

## 30. Interview questions to practise

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
