# QA Automation Framework — Design

Date: 2026-09-22
Status: Approved, ready for implementation planning

## 1. Purpose and scope

Build a Java test-automation framework for the Flamingo QA Automation Engineer
home assignment. It covers three targets:

- **Restful Booker** REST API (auth + CRUD) via REST Assured.
- **Hygraph** GraphQL API (positive and negative paths) via REST Assured.
- **DemoQA** web UI (practice form and web tables) via Playwright for Java.

The assignment grades framework architecture at 40%, code quality at 30%, test
design at 20% and documentation at 10%. The design optimises for that weighting:
50 well-chosen tests on top of a framework whose structure is legible
without reading any test body.

Scope is the **full bonus build**: every "nice to have" in the brief (Allure,
GitHub Actions, parallel execution, data-driven tests, custom retry, Lombok) is
in scope alongside the minimum requirements.

## 2. Verified external behaviour

These were probed live on 2026-09-22 rather than assumed. Tests assert the
observed shapes; the README documents them.

### Restful Booker (`${BOOKER_BASE_URL}`)

| Scenario | Observed |
| --- | --- |
| `GET /ping` | 201 |
| `POST /auth`, valid credentials | 200, `{"token":"..."}` |
| `POST /auth`, bad password | **200**, `{"reason":"Bad credentials"}` — not 401 |
| `POST /booking` | 200, `{"bookingid":N,"booking":{...}}` |
| `GET /booking/{missing}` | 404, plain-text `Not Found` |
| `PUT /booking/{id}` with `Cookie: token=...` | 200, returns updated booking |
| `PUT /booking/{id}` without token | 403, plain-text `Forbidden` |
| `DELETE /booking/{id}` with token | **201**, plain-text `Created` |
| `GET` after delete | 404 |

Note: `PUT` and `DELETE` authenticate via a `Cookie: token=<token>` header.
Responses to mutating calls require an explicit `Accept: application/json`.

### Hygraph GraphQL (`${GRAPHQL_ENDPOINT}`)

The endpoint is the **Video Streaming ("Hygraphlix") example schema** from the
Hygraph GraphQL playground. It is not recorded in this repository; see
"Obtaining the endpoint" below.

Chosen because it exposes `movies` / `movie` / `moviesConnection` and a nested
`publishedBy { name }`, which is the exact relationship the brief names. The
Ecommerce and Marketing example schemas from the same playground also respond
and serve as fallbacks if the Video schema is withdrawn; both expose a
list/single/connection triple and at least one relation, so only the query files
would change.

**Obtaining the endpoint.** The playground page renders client-side and does not
carry the endpoint in its served HTML. Select the Video Streaming example schema
in the playground and read the request URL from the browser network panel, or
extract it from the page's JavaScript bundles. Put the result in `.env` as
`GRAPHQL_ENDPOINT`. Confirm the schema with:

```graphql
{ moviesConnection { aggregate { count } } }
```

| Scenario | Observed |
| --- | --- |
| Valid query with variables | 200, `data.movies[]`, `moviesConnection.aggregate.count` = 12 |
| `movie(where:{id:"doesnotexist"})` | **200**, `data.movie: null`, **no** `errors` key |
| Malformed query (syntax error) | **400**, `data: null` + `errors[].message` containing `ParseError` |
| Unknown field on a known type | **400**, `errors[0].message` names the field, `data: null` |
| Wrong variable type, omitted required variable, `first: -1` | **400**, `data: null`, validation message |
| Mutation on a **missing** record | **200**, `data.deleteMovie: null`, error `not allowed`, `path` under `extensions` |
| Mutation on an **existing** record | **403**, `data: null`, `Mutation failed due to permission errors` |

The brief asks us to verify whether GraphQL returns 200 or an errors array.
The answer for Hygraph depends on **the phase in which the request fails**, in
line with the GraphQL-over-HTTP specification:

- **Request phase** (parse or validation failure): the request never executes,
  so the status is 400 and `data` is `null`.
- **Execution phase**: the status is 200 and problems are reported per field.
  A missing entity is simply a `null` field with no `errors` at all; a denied
  field is a `null` field *plus* an `errors` entry — the partial-result shape.
- **Authorization**: a mutation that reaches an existing record fails a
  permission check and the whole request is rejected with 403.

One deviation from the specification: field errors carry `path` under
`extensions` instead of as a top-level key. It is recorded as a finding.

### DemoQA (`${UI_BASE_URL}`)

`${UI_BASE_URL}/automation-practice-form` and `/webtables` both return 200
and are **client-rendered SPAs** — the served HTML body is `<div id="root"></div>`.
Selectors must therefore be confirmed against a live browser during
implementation, not inferred from served markup.

## 3. Architecture

### 3.1 Layout

Single Maven module. Reusable framework code lives in `src/main/java`; only test
scenarios live in `src/test/java`. This separation is the architecture argument
made visible: a class in `main` is self-evidently a reusable component, and the
boundary is enforced by the compiler rather than by convention.

```
flamingo-home-assignment/
├── .github/workflows/tests.yml
├── .mvn/wrapper/ + mvnw + mvnw.cmd
├── .env.example          # documented keys, placeholder values (committed)
├── .env                  # real values (gitignored, never committed)
├── pom.xml
├── README.md
├── docs/superpowers/specs/
└── src/
    ├── main/java/com/flamingo/qa/
    │   ├── config/      Config, ConfigLoader
    │   ├── api/
    │   │   ├── RestClientFactory, ApiResponse
    │   │   ├── booker/  AuthClient, TokenProvider, BookingClient, model/
    │   │   └── graphql/ GraphQlClient, GraphQlRequest, GraphQlResponse,
    │   │                GraphQlError, QueryLoader
    │   ├── ui/
    │   │   ├── PlaywrightFactory
    │   │   └── pages/   BasePage, PracticeFormPage, WebTablesPage,
    │   │                components/ SubmissionModal, RegistrationDialog,
    │   │                            WebTableGrid
    │   ├── junit/       PlaywrightExtension, RetryOnFailureExtension,
    │   │                @ApiTest, @UiTest, @RetryOnFailure
    │   └── data/        TestDataFactory
    ├── main/resources/  allure.properties, graphql/*.graphql
    └── test/
        ├── java/com/flamingo/qa/
        │   ├── api/     BookerAuthTest, BookingCrudTest, BookingNegativeTest
        │   ├── graphql/ GraphQlPositiveTest, GraphQlNegativeTest
        │   └── ui/      PracticeFormTest, WebTablesTest
        └── resources/fixtures/  avatar.png
```

### 3.2 Build

- Single `pom.xml`, `maven.compiler.release=17`. Builds on the local JDK 25 and
  still runs for a reviewer on 17; the brief requires 11+.
- Committed Maven Wrapper so `./mvnw clean test` works with no prior install.
  Maven is also installed locally (`brew install maven`) so the suite can be
  verified during development.
- Surefire wired to `groups` / `excludedGroups` so `-Dgroups=api` and
  `-Dgroups=ui` behave exactly as the brief's README promises.
- Dependency versions are resolved against current releases at implementation
  time, not written from memory.

Dependencies: JUnit 5, REST Assured, Playwright for Java, AssertJ, Jackson,
Lombok, Allure (junit5 + rest-assured), aspectjweaver.

### 3.3 Configuration and secret handling

No endpoint or credential is stored in the repository. Configuration resolves
through `Config`, a typed facade over `ConfigLoader`, with precedence:

```
system property  →  OS environment variable  →  .env file  →  built-in default
```

Only non-sensitive presentation settings carry built-in defaults
(`ui.browser=chromium`, `ui.headless=true`, `http.timeout.ms=30000`). Every URL
and credential has **no default**: if it is missing, `ConfigLoader` fails fast
with a message naming the key and pointing at `.env.example`. A silent fallback
to a hard-coded endpoint would defeat the point of removing it.

| File | Committed | Contents |
| --- | --- | --- |
| `.env.example` | yes | every key, placeholder values, comments on where each comes from |
| `.env` | **no** (gitignored) | real values for local runs |

`.env` is loaded with `dotenv-java` configured to ignore a missing file, so CI
can supply everything as environment variables with no file present at all.

Key naming: `ConfigLoader.toEnvKey()` maps `booker.base.url` to
`BOOKER_BASE_URL`, which is both the OS environment convention and the `.env`
convention — one mapping serves both layers.

Typed accessors only (`Config.bookerBaseUrl()`, `Config.graphQlEndpoint()`,
`Config.uiBaseUrl()`, `Config.browser()`, `Config.headless()`,
`Config.timeoutMillis()`). No raw string keys in tests.

**CI.** Values live in GitHub Actions repository secrets. The workflow writes
`.env` from those secrets before the test step. `.env` is excluded from every
artifact upload path so it cannot leave the runner.

**Scope note.** The Restful Booker credentials are published in the assignment
brief and in that service's public API documentation, and all three endpoints are
public demo services. This arrangement therefore demonstrates secret-handling
practice rather than protecting live secrets, and the README says so plainly.

## 4. API layer

### 4.1 REST Assured wiring

`RestClientFactory` builds one shared `RequestSpecification`: base URI from
`Config`, JSON content and accept headers, an `AllureRestAssured` filter, and
logging of request and response **only on validation failure**, so green runs
stay quiet and red runs are diagnosable.

`TokenProvider` authenticates **once per JVM** and caches the token. The brief
asks us not to overload these public services; one `/auth` call per suite rather
than one per test is the concrete expression of that.

### 4.2 Client shape

Client methods return `ApiResponse<T>` — a thin wrapper exposing
`statusCode()`, `body()` (Jackson-mapped) and `rawBody()` (for plain-text error
bodies such as `Not Found` and `Forbidden`). One method therefore serves both
the happy path and the negative path, avoiding a duplicated
`getById()` / `getByIdRaw()` pair.

Models — `Booking`, `BookingDates`, `AuthRequest`, `AuthResponse`,
`CreateBookingResponse` — are Lombok `@Value @Builder @Jacksonized`.

### 4.3 Test isolation

Every API test seeds its own booking through `TestDataFactory` and asserts only
on that booking. There is no ordered, inter-dependent CRUD chain: tests do not
share state, which is what makes parallel execution safe and stops one failure
cascading into unrelated reds. JUnit's default `PER_METHOD` lifecycle gives each
test its own instance, so no fixture field is shared across concurrent methods.

Tests deliberately do **not** delete what they create. Teardown is not a
requirement, the service resets its data periodically by design, and an extra
DELETE per test is load on a public service the brief asks us not to overload.
`deletesBooking` still issues a DELETE, because that call is the behaviour under
test rather than cleanup.

Every client call carries a token, which is how a consumer of a booking API
should behave. The service only *enforces* it on `PUT`, `PATCH` and `DELETE`;
that gap is covered deliberately in the authorization suite below rather than
being quietly accommodated.

## 5. GraphQL layer

- Queries are `.graphql` files under `src/main/resources/graphql/`, loaded by
  `QueryLoader`. They stay syntax-highlightable and out of Java string literals.
- `GraphQlRequest` is a Lombok builder carrying `query`, `variables` and
  `operationName`. Variables are sent as a real map — never string-interpolated,
  which the brief explicitly requires.
- `GraphQlClient.execute(GraphQlRequest)` returns `GraphQlResponse`, exposing
  `statusCode()`, `data()`, `errors()`, `hasErrors()` and typed extraction by
  path. It must tolerate a **missing** `errors` key (success), a missing `data`
  key, and `data` present with a null field — all three occur on Hygraph.
- The query and its variables are attached to the Allure report per call.

## 6. UI layer

### 6.1 Playwright lifecycle

A `Playwright` instance is not safe to share across threads. `PlaywrightFactory`
holds `ThreadLocal<Playwright>` and `ThreadLocal<Browser>`: one browser per
worker thread, reused across that thread's tests. Each *test* receives a fresh
`BrowserContext` and `Page`. Contexts are cheap, so this buys full
cookie/storage isolation per test at negligible cost and is what allows parallel
UI execution.

### 6.2 Injection, not inheritance

`PlaywrightExtension` implements `ParameterResolver`, so tests read:

```java
@UiTest
void addsNewRecordToTable(Page page) { … }
```

`@UiTest` and `@ApiTest` are meta-annotations bundling `@Tag` and
`@ExtendWith`. No base classes, no per-class setup boilerplate. The README
states this choice explicitly so it reads as deliberate rather than as an
omission against the rubric's "base classes and helpers" wording.

**Multi-page readiness.** `ParameterResolver` is invoked once *per parameter*,
so the resolver is keyed by qualifier-or-index from the start:

```java
String key = pc.findAnnotation(Actor.class).map(Actor::value)
               .orElseGet(() -> "page-" + pc.getIndex());
```

The `@Actor` qualifier annotation and the keyed resolver **ship now** — together
they are a handful of lines. What is **deferred** is any test that uses them:
only single-`Page` tests ship in this assignment. A future two-browser test
(`void t(@Actor("alice") Page a, @Actor("bob") Page b)`) therefore needs no
framework change, and `void t(Page page)` stays valid under the keyed resolver.
For a page count not known at compile time, a `PageProvider` would be injected
instead of N parameters; `PageProvider` is deferred entirely. All created
contexts are registered in the `ExtensionContext.Store` as `CloseableResource`
so cleanup and failure capture iterate every page, not just the first.

### 6.3 Stability

DemoQA serves Google ad frames that shift layout and intercept clicks — the
dominant flake source on that site. Each `BrowserContext` registers a `route`
handler aborting requests to ad and analytics hosts. This is both faster and
deterministic, and is preferred over scattering `scrollIntoView` calls and retry
loops through the page objects.

Waiting relies on Playwright's auto-waiting plus explicit `locator.waitFor()`
inside page objects where the SPA needs it.

### 6.4 Failure capture

A `TestWatcher.testFailed()` screenshot hook would be **broken here**, because
`testFailed` fires *after* `@AfterEach`, by which point the context is closed.
The extension therefore implements `AfterEachCallback` and checks
`context.getExecutionException().isPresent()`, capturing **before** teardown:

- full-page screenshot → `target/screenshots/` and attached to Allure;
- Playwright trace → attached, openable in Trace Viewer.

### 6.5 Page objects

`BasePage` holds the `Page` and shared navigation helpers. `PracticeFormPage`
and `WebTablesPage` extend it; `SubmissionModal`, `RegistrationDialog` and
`WebTableGrid` wrap repeated regions.

Discipline: page objects expose **actions and data only, never assertions**.
Every assertion lives in the test and uses AssertJ.

## 7. Test inventory

Total: 50 test methods (31 REST + 12 GraphQL + 7 UI); parameterized tests make the
execution count higher. The brief's minimums are
3 API CRUD, 5 GraphQL and 2 UI, so each area clears its minimum with margin
without padding. The data-driven create test is one method producing several
invocations.

### Authorization matrix

`BookingClient` exposes every endpoint in two forms, with and without a token,
so authorization can be probed as a matrix rather than a spot check. Each
endpoint is exercised three ways — no token, valid token, forged token:

| Endpoint | no token | valid | forged | Verdict |
| --- | --- | --- | --- | --- |
| `POST /booking` | 200 | 200 | 200 | **not enforced** |
| `GET /booking` | 200 | 200 | 200 | **not enforced** |
| `GET /booking/{id}` | 200 | 200 | 200 | **not enforced** |
| `PUT /booking/{id}` | 403 | 200 | 403 | enforced |
| `PATCH /booking/{id}` | 403 | 200 | 403 | enforced |
| `DELETE /booking/{id}` | 403 | 201 | 403 | enforced |

The forged-token column matters: the protected verbs reject a fabricated token
rather than trusting the mere presence of the cookie, so enforcement is real
where it exists.

The three unenforced rows are recorded as **findings**. This is documented
behaviour of Restful Booker, so they are design defects in the system under
test, not undocumented regressions.

### How findings are reported

A finding is a test that asserts the behaviour a **correct** service would have,
so it fails, and its failure message is the defect report — input, expected
response, actual response, and what was persisted:

```
POST /booking with checkin="not-a-date" must answer 400 Bad Request;
the service answered 200 and PERSISTED checkin as "0NaN-aN-aN"
```

Findings carry `@Tag("finding")` so a pipeline can separate gating from
reporting:

```
./mvnw test -Dgroups=api -DexcludedGroups=finding   # gating: must be green
./mvnw test -Dgroups=finding                        # defect report: expected red
```

This keeps the build's pass/fail signal meaningful — it reflects whether *our
framework* works — while defects in the service under test stay loud rather
than being normalised into green.

### API — Restful Booker (31)

| Test | Asserts |
| --- | --- |
| Auth returns token for valid credentials | 200, token non-blank |
| Auth rejects bad password | 200, `reason` = `Bad credentials`, no token |
| Create booking returns id and echoes payload | 200, id > 0, fields match |
| Get by id returns created booking | 200, body equals created booking |
| Update replaces booking fields | 200, updated fields, auth via token cookie |
| Partial update changes only named fields | 200, untouched fields preserved |
| Delete removes booking | 201, then `GET` → 404 |
| Get non-existent id | 404, body `Not Found` |
| Non-numeric id | 404, body `Not Found` |
| PUT / PATCH / DELETE a non-existent id | **405 `Method Not Allowed`**, not 404 |
| Malformed JSON | 400 `Bad Request` |
| PUT with a partial body | 400 `Bad Request` |
| FINDING: empty create body | 500, expected 400 |
| FINDING: create missing a required field | 500, expected 400 |
| FINDING: non-numeric `totalprice` | 200, silently stored as `null` |
| FINDING: unparseable dates | 200, persisted as `0NaN-aN-aN` |
| FINDING: checkout before checkin | 200, accepted |
| FINDING: negative `totalprice` | 200, accepted |
| Data-driven create (`@ParameterizedTest`) | Deposit true/false, with and without `additionalneeds` |
| FINDING: unauthenticated `POST /booking` | 200 where 401/403 is expected |
| FINDING: unauthenticated `GET /booking` | 200, and ids really are returned |
| FINDING: unauthenticated `GET /booking/{id}` | 200, guest names disclosed |
| `PUT` / `PATCH` / `DELETE` without a token | 403 `Forbidden` on each |
| `PUT` / `PATCH` / `DELETE` with a forged token | 403 on each |
| Valid token authorises the protected verbs | 200 / 200 / 201 |

### GraphQL — Hygraph (12)

| Test | Asserts |
| --- | --- |
| Page size limits the list | Exactly n items; aggregate count >= n |
| Paging visits every movie exactly once | Pages over `orderBy: id_ASC` cover the collection with no gaps or duplicates |
| Single movie by id | Equals the same movie taken from the list |
| One document, different variables | Identical document text yields both results; no id ever appears in it |
| Fragment + nested `publishedBy { name }` | Fragment fields and nested publisher resolved on every row |
| Non-existent id | 200, `data.movie` null, no `errors` key |
| Mutation on a missing record | 200, `data.deleteMovie` null, field error `not allowed` |
| Mutation on an existing record | 403, `data` null, permission error, record still present afterwards |
| Malformed query | 400, `ParseError`, `data` null |
| Unknown field | 400, message names the field and type, `data` null |
| Invalid variables (parameterized, 3 cases) | 400 for wrong type, omitted required variable, negative page size |
| FINDING: field error `path` not top-level | Spec requires a top-level `path`; Hygraph nests it under `extensions` |

### UI — DemoQA (7)

| Test | Asserts |
| --- | --- |
| Submit complete registration form | Success modal rows match submitted values (upload, datepicker, React-Select dropdowns, radio, checkboxes) |
| Submit form with required fields empty | No modal; required fields flagged invalid |
| Add record to web table | New row present with entered values |
| Edit existing record | Row reflects updated values |
| Delete record | Row absent; row count decremented |
| Search filters rows | Only matching rows remain |
| Column sorting | Resulting order matches expected ordering |

## 8. Cross-cutting concerns

### 8.1 Parallel execution

JUnit 5 parallel execution via `junit-platform.properties`: concurrent across
classes, same-thread within a class, **fixed thread count of 4**. Deliberately
not "threads = cores": the brief asks that these public services not be
overloaded.

### 8.2 Retry

A custom `@RetryOnFailure` extension
(`TestTemplateInvocationContextProvider`) applied **only** to tests touching the
public services, which do intermittently hiccup. It is not applied suite-wide —
a blanket retry hides real defects. This is the first item to cut if scope
tightens.

### 8.3 Reporting

- Allure: `allure-junit5` + `allure-rest-assured`, aspectjweaver via the
  Surefire `argLine`, and the `allure-maven` plugin so `./mvnw allure:serve`
  works without a separate Allure CLI install.
- Attachments: every HTTP request/response, GraphQL query and variables, and on
  UI failure the screenshot plus Playwright trace.
- `@Epic` / `@Feature` / `@DisplayName` so the report reads as a structured
  suite.
- `maven-surefire-report-plugin` stays enabled; the brief accepts a Surefire
  HTML report as the deliverable.

### 8.4 CI — `.github/workflows/tests.yml`

- Triggers: `push`, `pull_request`, `workflow_dispatch`.
- `ubuntu-latest`, Temurin 17, Maven cache keyed on `pom.xml`.
- Explicit browser install step (`playwright install --with-deps chromium`) —
  omitting it is the classic "green locally, red in CI" Playwright failure.
- `./mvnw clean test` with `-Dheadless=true`.
- Uploads Surefire reports, screenshots, traces and `allure-results` with
  `if: always()`, so a failing run is the one that yields evidence.
- No secrets required; all targets are public.

## 9. Documentation and delivery

`README.md` carries exactly the sections the brief specifies — Prerequisites,
How to Run (all, `-Dgroups=api`, `-Dgroups=ui`), Test Strategy, Challenges &
Solutions, What I Would Add With More Time — plus an architecture overview and a
configuration table.

Challenges & Solutions covers: the verified-behaviour tables in section 2 (which
answer the brief's own 200-vs-400 question with evidence), the
`TestWatcher`-fires-after-`@AfterEach` trap, the ad-blocking decision,
Playwright's per-thread constraint, and Restful Booker's periodic data resets
and Heroku cold starts.

`.gitignore` extends to `target/`, `allure-results/`, `allure-report/`,
`test-output/`, and **`.env`**.

No endpoint or credential appears in any commit, including the design and plan
documents, which use `${PLACEHOLDER}` names throughout.

Commits are frequent, imperative-mood, and carry no Claude or Anthropic
attribution, per the repository's `CLAUDE.md`. Planned sequence: scaffold and
wrapper → config → REST client and models → Booker tests → GraphQL layer →
GraphQL tests → Playwright factory and extensions → page objects → UI tests →
Allure → CI → README. Pushed to `orush/flamingo-home-assignment`, with a CI run
screenshot committed as the test-report deliverable.

## 10. Known risks

| Risk | Handling |
| --- | --- |
| Restful Booker resets data periodically | Tests self-seed and clean up; never assume pre-existing data |
| Restful Booker Heroku cold start | Generous timeout on first request; retry extension on service-touching tests |
| Hygraph example schema content changes | Assert structure and invariants (count >= n, non-blank), not specific titles |
| DemoQA selectors unknown until runtime | Confirm live in browser during implementation; SPA serves no useful static markup |
| DemoQA ad frames intercept clicks | Route-level ad blocking per context |
| Public services temporarily down | Brief permits documenting and mocking; README records status |

## 11. Explicitly out of scope

- Multi-page / multi-browser **tests** (the keyed resolver and `@Actor`
  qualifier ship; no test uses them yet). `PageProvider` is not built.
- Mobile or cross-browser matrices beyond Chromium.
- Performance, security or accessibility testing.
- Mocking layer — real services are used, per the brief.
