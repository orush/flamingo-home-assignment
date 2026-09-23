# CLAUDE.md

Java test-automation framework for three public services: Restful Booker (REST),
Hygraph (GraphQL) and DemoQA (UI). JUnit 5, REST Assured, Playwright for Java,
AssertJ, Jackson, Lombok, Allure, GitHub Actions. `README.md` is the human
guide; the design spec and plan live in `docs/superpowers/`.

## Commands

Always run with `clean`: without it, stale files in `target/test-classes`
(including old `junit-platform.properties`) are silently loaded.

```bash
./mvnw clean test -DexcludedGroups=finding   # the build's verdict: must be green (99 executions)
./mvnw clean test -Dgroups=framework         # framework self-tests only (42 executions)
./mvnw clean test -Dgroups=finding           # defect report: 8 tests that FAIL BY DESIGN
./mvnw clean test -Dgroups=api               # REST + GraphQL
./mvnw clean test -Dgroups=ui                # DemoQA
./mvnw clean test -Dtest=ClassName           # one class
ALLURE_NO_ANALYTICS=true ./mvnw allure:report   # target/site/allure-maven-plugin/index.html
PLAYWRIGHT_SKIP_BROWSER_GC=1 ./mvnw compile exec:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

A plain `./mvnw clean test` includes the findings, so it ends in BUILD FAILURE.
That is expected; only a failure outside `@Tag("finding")` is a regression.

## Secrets — hard rules

- No endpoint URL, username, password or token in any tracked file, test,
  doc or commit message. Refer to them by key: `BOOKER_BASE_URL`,
  `BOOKER_USERNAME`, `BOOKER_PASSWORD`, `GRAPHQL_ENDPOINT`, `UI_BASE_URL`.
  Values live only in `.env` (gitignored) and GitHub Actions secrets.
- Credentials are `Secret` (`Config.bookerUsername()`, `Config.bookerPassword()`,
  `TokenProvider.token()`). `toString()` prints `******`; call `reveal()` only at
  the HTTP boundary. Never pass a credential as a `String` into an `@Step`
  method: Allure writes step arguments into its raw results, and
  `@Param(mode = MASKED)` hides them only in the rendered report, not the JSON.
- All HTTP goes through `RestClientFactory`, whose `RedactingAllureFilter` masks
  `username`, `password`, `token`, `Authorization` and cookies in report
  attachments. Do not add the stock `AllureRestAssured` filter.
- Before every commit, scan the diff and untracked files for the real values
  with `grep -a` (binary files otherwise make `grep -c` print nothing) and treat
  a missing count as a failure, never as a pass.
- The repository is public, so CI artifacts are public. CI's
  `.github/scripts/check_no_secrets.py` must pass before anything is uploaded.
  Endpoints appearing in artifacts is accepted; credentials are not.

## Architecture

- `src/main/java` holds the reusable framework; `src/test/java` holds scenarios
  only. Framework dependencies are therefore compile scope.
- Wiring is by composition, never base classes: `@ApiTest` / `@UiTest` imply
  `@Test` plus the tag (plus Playwright injection for UI). Annotations that
  replace `@Test` — `@ParameterizedTest`, `@RetryOnNetworkError` — need
  `@Tag("api")` / `@Tag("ui")` added directly, and UI ones also need
  `@ExtendWith(PlaywrightExtension.class)`.
- UI tests receive `Page` as a method parameter; `@Actor("name") Page` gives
  extra isolated sessions. Page objects expose actions and data and never
  assert; public actions carry `@Step`.
- Two or more independent checks in a row go in one
  `assertSoftly(softly -> ...)` block, so a failure reports all of them.
  Nothing inside the block may throw: any non-assertion exception, including
  the NPE from chaining after a failed soft `first()` / `singleElement()`,
  discards every collected failure. So a precondition that later lines
  dereference (status before `body().getX()`, GraphQL success before
  `get`/`getList`) stays a hard `assertThat` before the block. For list
  elements, use `extracting(...).containsExactly(...)` or `satisfiesExactly(...)`
  instead of navigating.
- Every test has `@DisplayName`; classes carry `@Epic` / `@Feature` (and
  `@Story`). Framework self-tests live in the `com.flamingo.qa.framework` test
  package and carry `@Tag("framework")` plus `@Epic("Framework")`; a new one
  without the tag would silently move to the scenario workflow.
- Reusable, valid GraphQL queries go in `src/main/resources/graphql/*.graphql`;
  deliberately invalid ones stay inline in the test that breaks them.
- Tests seed their own data and never depend on each other. They do not clean
  up: the services self-reset, and extra requests are load on public services.

## Findings policy

A defect in a system under test becomes a *finding*: the test asserts what a
**correct** service would do, carries `@Tag("finding")`, has a `FINDING:`
display name, and its `.as(...)` message is the defect report (input, expected,
actual, and what was stored). It fails by design and is excluded from the gating
run. *Pinned* behaviour (documented quirks such as unauthenticated `POST`/`GET`,
`DELETE` → 201, 405 for a missing booking) is asserted as observed, with the
correct behaviour named in the title. A new finding also goes into the README
Findings table.

## Verified behaviour — do not re-learn

Probe live behaviour (curl or a throwaway test) before writing an assertion, and
delete probes before committing.

- **Restful Booker** answers `418` to any `Accept` other than exactly
  `application/json`, so never set Accept via `ContentType.JSON` (REST Assured
  expands it to four types). The token goes in a `Cookie: token=` header and is
  enforced only on `PUT`/`PATCH`/`DELETE`.
- **Hygraph** error shape depends on the phase that fails: parse/validation →
  400 with `data: null`; execution → 200 with per-field `null`s (plus `errors`
  when a field is denied); a mutation on an existing record → 403. Never assert
  on error line numbers: Hygraph reformats queries first.
- **DemoQA**: the web table is a plain `<table>` (not react-table) and cannot be
  sorted. Validation is native HTML5 (`:invalid`, `was-validated`). The date
  picker moves focus back to its input about 10 ms after a pick; `DatePicker`
  waits for that, so keep the wait. The confirmation renders dates as
  `dd MMMM,yyyy`. Only first-party requests are allowed (`ThirdPartyBlocker`);
  that is what removes the ad frames.
- In Playwright text filters use `ExactText.of(...)`, never `Pattern.quote`:
  filters run as JavaScript regexes, and `\Q…\E` is Java-only.
- Negative UI checks (`staysOpen`, `confirmationAppears`) are bounded waits, and
  both outcomes must be exercised; a check never seen returning both values
  proves nothing.
- UI assertions go through `Eventually.eventually(...)` /
  `eventuallySoftly(...)`, which re-run the block for up to 5 s. The lambda must
  read the page itself (`tables.records()` inside, not a list captured before),
  and must not perform actions. Negative checks stay outside it as a hard
  `assertThat`: they are bounded waits already. Only `AssertionError` and
  `PlaywrightException` are retried, and the last error is rethrown unchanged.

## Build and tooling

- Parallel runs: classes concurrent, methods same-thread, a fixed 4 threads,
  and `fixed.max-pool-size=4`. Keep the cap: JUnit can otherwise add up to 256
  threads, and each UI thread owns a browser. Tests must not write shared state;
  `ConfigLoaderTest` touches only its own scratch key under
  `@ResourceLock(SYSTEM_PROPERTIES)`.
- `@RetryOnNetworkError` retries only failures with an `IOException` in the
  cause chain and reports retried attempts as aborted. Never use it to paper
  over assertion flakiness.
- Local JDK is 26 and the build targets release 17; CI uses Temurin 17.
  AspectJ must stay at 1.9.25.1 or newer for JDK 26. `lombok.config` pins
  `jacksonVersion += 2`; removing it brings back a warning on every
  `@Jacksonized` model.
- Resolve dependency versions from `repo.maven.apache.org/.../maven-metadata.xml`,
  never from `search.maven.org`, whose index was found to be over a year stale.
  Check GitHub Action majors with `gh api repos/<action>/releases/latest`.
- Allure: the report uses the `allure2` plugin (the default `awesome` one is
  disabled in `allurerc.json` because it embeds Google Analytics), as a single
  file, with `ALLURE_NO_ANALYTICS=true`. `.allure/` is a ~240 MB Node runtime,
  gitignored.
- Check the full compiler output for warnings; filtering Maven output down to
  the `Tests run` lines hid nine warnings for several phases.
- The local shell is zsh: `$var` is not word-split, and `${!var}` is bash-only.
  Pass paths as separate arguments. macOS `strings` misreads `.class` files;
  use `grep -a` instead.

## CI

Two workflows on `ubuntu-24.04` (pinned deliberately), sharing the composite
action `.github/actions/setup` (JDK 17, `.env` from secrets passed as inputs,
Chromium). `.github/workflows/tests.yml` runs the scenario gating step
(`-DexcludedGroups=finding,framework`, 57 executions), runs the findings step
(`-Dmaven.test.failure.ignore=true` plus `continue-on-error`), writes a run
summary, runs the credential guard, and uploads the Allure report, raw results
and failure diagnostics only if the guard passes.
`.github/workflows/framework.yml` does the same for `-Dgroups=framework`, with
`framework-`-prefixed artifacts. A green run has no annotations.

## Keep in sync

When behaviour or counts change, update the README (counts, Findings table,
Challenges), the spec and the plan's status note. Current totals: 54 scenario
tests (31 REST, 12 GraphQL, 11 UI), 35 framework self-tests, 107 executions,
8 failing findings.

## Git

### Commit messages

- Keep them short and meaningful: a single subject line stating what changed and why it matters.
- Include only crucial information. No filler, no restating the diff file by file.
- Use the imperative mood ("Add rate limiting", not "Added rate limiting").
- Add a body only when the reason for the change is not obvious from the subject.

### Attribution

- Do not add `Co-Authored-By: Claude` or any Claude/Anthropic attribution lines to commit messages or PR descriptions.
- Do not add the `🤖 Generated with Claude Code` footer.
