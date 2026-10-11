# Namastute — Learning Notes

My revision notes from building Namastute. Organised by topic, not by date. Each section: the idea, why it exists, and where it shows up in this project.

Last updated: 2026-10-12 (project scaffold complete: Spring Boot api, Next.js web, Postgres in Docker, CI).

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
18. [Command cheat sheet](#18-command-cheat-sheet)
19. [Glossary](#19-glossary)
20. [Interview questions to practise](#20-interview-questions-to-practise)

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

## 18. Command cheat sheet

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

## 19. Glossary

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
| YAML | Indentation-based settings format |

---

## 20. Interview questions to practise

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
