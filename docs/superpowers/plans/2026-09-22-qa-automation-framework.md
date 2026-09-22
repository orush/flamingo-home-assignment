# QA Automation Framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java test-automation framework covering the Restful Booker REST API, the Hygraph GraphQL API, and the DemoQA web UI, delivering 23 scenario tests (plus 9 framework self-tests) on reusable infrastructure.

**Architecture:** Single Maven module. All reusable framework code lives in `src/main/java` (config, REST/GraphQL clients, page objects, JUnit extensions); `src/test/java` holds only test scenarios. Test wiring is by composition — `@ApiTest` / `@UiTest` meta-annotations plus a `ParameterResolver` that injects Playwright `Page` objects — rather than inheritance.

**Tech Stack:** Java 17 (release level), Maven, JUnit 5, REST Assured, Playwright for Java, AssertJ, Jackson, Lombok, Allure, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-22-qa-automation-framework-design.md`

## Global Constraints

- `maven.compiler.release=17`. Local JDK is 25; CI uses Temurin 17.
- Framework code in `src/main/java`, tests in `src/test/java`. **Consequence:** REST Assured, Playwright, Jackson, AssertJ and JUnit Jupiter are all **compile-scope**, not test-scope, because `src/main` code imports them.
- Page objects expose actions and data only. **Never** assertions. Every `assertThat` lives in a test and uses AssertJ.
- GraphQL variables are always sent as a map. **Never** string-interpolated.
- Parallelism is fixed at 4 threads. The brief requires not overloading these public services.
- Commit messages: short, imperative subject, no Claude/Anthropic attribution (per repo `CLAUDE.md`).
- Group IDs / packages: `com.flamingo.qa`.
- Every API test seeds its own data and asserts only on it. No inter-test ordering
  dependencies, and **no teardown** — the service self-resets, cleanup is not a
  requirement, and extra DELETEs are load on a public service.
- Client calls always send a token. The service only enforces it on `PUT`,
  `PATCH` and `DELETE`; the unenforced endpoints are covered as findings in
  `BookingAuthorizationTest`, which asserts observed behaviour and names the
  expected behaviour in each test title.
- **No URL, username or password may be written to any tracked file.** URLs and
  credentials have no built-in default; a missing one fails fast pointing at
  `.env.example`. Only `ui.browser`, `ui.headless` and `http.timeout.ms` have defaults.

### Pinned versions (resolved from Maven Central, stable only)

| Artifact | Version |
| --- | --- |
| `org.junit.jupiter:junit-jupiter` | 5.12.2 |
| `org.junit.platform:junit-platform-launcher` | 1.12.2 |
| `io.rest-assured:rest-assured` | 5.5.2 |
| `com.microsoft.playwright:playwright` | 1.52.0 |
| `org.assertj:assertj-core` | 3.27.3 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.19.0 |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | 2.19.0 |
| `org.projectlombok:lombok` | 1.18.38 |
| `io.github.cdimascio:dotenv-java` | 3.2.0 |
| `io.qameta.allure:allure-junit5` | 2.29.1 |
| `io.qameta.allure:allure-rest-assured` | 2.29.1 |
| `org.aspectj:aspectjweaver` | 1.9.24 |
| `maven-compiler-plugin` | 3.15.0 |
| `maven-surefire-plugin` | 3.5.6 |
| `maven-surefire-report-plugin` | 3.5.6 |
| `io.qameta.allure:allure-maven` | 2.15.2 |
| `org.codehaus.mojo:exec-maven-plugin` | 3.5.0 |
| `maven-wrapper-plugin` | 3.3.4 |

### Endpoints and credentials

**No endpoint or credential appears in this repository.** All of them resolve at
runtime from `.env` (gitignored locally) or from GitHub Actions secrets in CI.
This plan, the design doc, the source and the README refer to them only by key:

| Key | `.env` / secret name | Source |
| --- | --- | --- |
| `booker.base.url` | `BOOKER_BASE_URL` | assignment brief |
| `booker.username` | `BOOKER_USERNAME` | assignment brief |
| `booker.password` | `BOOKER_PASSWORD` | assignment brief |
| `graphql.endpoint` | `GRAPHQL_ENDPOINT` | Hygraph playground, Video Streaming schema |
| `ui.base.url` | `UI_BASE_URL` | assignment brief |

If you need a value, read it from the brief or the playground — do not copy one
into a tracked file, a commit message, or a test fixture.

---

### Task 1: Project scaffold, build, and configuration

**Files:**
- Create: `pom.xml`, `.mvn/wrapper/*`, `mvnw`, `mvnw.cmd`
- Create: `src/main/java/com/flamingo/qa/config/ConfigLoader.java`
- Create: `src/main/java/com/flamingo/qa/config/Config.java`
- Create: `.env.example`
- Create: `.env` (local only, **never committed**)
- Create: `src/test/resources/junit-platform.properties`
- Modify: `.gitignore`
- Test: `src/test/java/com/flamingo/qa/config/ConfigLoaderTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `ConfigLoader.get(String key) -> String` (throws `IllegalStateException` when absent)
  - `ConfigLoader.toEnvKey(String key) -> String` (package-private, for test)
  - `Config.bookerBaseUrl()`, `Config.bookerUsername()`, `Config.bookerPassword()`, `Config.graphQlEndpoint()`, `Config.uiBaseUrl()` — all `-> String`
  - `Config.browser() -> String`, `Config.headless() -> boolean`, `Config.timeoutMillis() -> int`

- [ ] **Step 1: Install Maven**

```bash
brew install maven
mvn -version
```

Expected: Maven 3.9.x reported, running on JDK 25.

- [ ] **Step 2: Verify Lombok works on the local JDK 25**

This is a real risk: Lombok patches the compiler internals and lags new JDKs. Verify **before** building anything on it.

```bash
mkdir -p /tmp/lomboktest && cd /tmp/lomboktest
mvn -q archetype:generate -DgroupId=t -DartifactId=t -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
cd t
mkdir -p src/main/java/t
cat > src/main/java/t/P.java <<'EOF'
package t;
import lombok.Value;
@Value public class P { String name; }
EOF
mvn -q -DskipTests \
  -Dmaven.compiler.release=17 \
  compile 2>&1 | tail -20
```

First add the Lombok dependency to that scratch `pom.xml`, then compile.

Expected: BUILD SUCCESS.

**If it FAILS** with an `java.lang.IllegalAccessError` / `com.sun.tools.javac` error, Lombok does not yet support JDK 25. Fallback, in order:
1. `brew install openjdk@21` and set `JAVA_HOME=$(/usr/libexec/java_home -v 21)` for this project (add to README).
2. If that is unacceptable, **drop Lombok entirely** — it is a bonus item, not a requirement. Replace `@Value @Builder @Jacksonized` with hand-written immutable classes plus static builders. Record the decision in the README's "Challenges & Solutions".

Do not proceed until this is resolved. Every later task depends on it.

- [ ] **Step 3: Write `pom.xml`**

Complete POM with all dependencies and plugins, so later tasks add code, not build config.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.flamingo.qa</groupId>
  <artifactId>qa-automation-assignment</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>jar</packaging>
  <name>Flamingo QA Automation Assignment</name>

  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

    <junit.version>5.12.2</junit.version>
    <junit.platform.version>1.12.2</junit.platform.version>
    <restassured.version>5.5.2</restassured.version>
    <playwright.version>1.52.0</playwright.version>
    <assertj.version>3.27.3</assertj.version>
    <jackson.version>2.19.0</jackson.version>
    <lombok.version>1.18.38</lombok.version>
    <dotenv.version>3.2.0</dotenv.version>
    <allure.version>2.29.1</allure.version>
    <aspectj.version>1.9.24</aspectj.version>
  </properties>

  <dependencies>
    <!-- Compile scope: src/main framework code imports these. -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>${junit.version}</version>
    </dependency>
    <dependency>
      <groupId>io.rest-assured</groupId>
      <artifactId>rest-assured</artifactId>
      <version>${restassured.version}</version>
    </dependency>
    <dependency>
      <groupId>com.microsoft.playwright</groupId>
      <artifactId>playwright</artifactId>
      <version>${playwright.version}</version>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <version>${assertj.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>${jackson.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.datatype</groupId>
      <artifactId>jackson-datatype-jsr310</artifactId>
      <version>${jackson.version}</version>
    </dependency>
    <dependency>
      <groupId>io.qameta.allure</groupId>
      <artifactId>allure-junit5</artifactId>
      <version>${allure.version}</version>
    </dependency>
    <dependency>
      <groupId>io.qameta.allure</groupId>
      <artifactId>allure-rest-assured</artifactId>
      <version>${allure.version}</version>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <version>${lombok.version}</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>io.github.cdimascio</groupId>
      <artifactId>dotenv-java</artifactId>
      <version>${dotenv.version}</version>
    </dependency>
    <dependency>
      <groupId>org.junit.platform</groupId>
      <artifactId>junit-platform-launcher</artifactId>
      <version>${junit.platform.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.15.0</version>
        <configuration>
          <annotationProcessorPaths>
            <path>
              <groupId>org.projectlombok</groupId>
              <artifactId>lombok</artifactId>
              <version>${lombok.version}</version>
            </path>
          </annotationProcessorPaths>
        </configuration>
      </plugin>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.6</version>
        <configuration>
          <argLine>
            -javaagent:"${settings.localRepository}/org/aspectj/aspectjweaver/${aspectj.version}/aspectjweaver-${aspectj.version}.jar"
          </argLine>
          <systemPropertyVariables>
            <allure.results.directory>${project.build.directory}/allure-results</allure.results.directory>
          </systemPropertyVariables>
        </configuration>
        <dependencies>
          <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>${aspectj.version}</version>
          </dependency>
        </dependencies>
      </plugin>

      <plugin>
        <groupId>io.qameta.allure</groupId>
        <artifactId>allure-maven</artifactId>
        <version>2.15.2</version>
        <configuration>
          <reportVersion>2.29.1</reportVersion>
          <resultsDirectory>${project.build.directory}/allure-results</resultsDirectory>
        </configuration>
      </plugin>

      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.5.0</version>
      </plugin>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-wrapper-plugin</artifactId>
        <version>3.3.4</version>
      </plugin>
    </plugins>
  </build>

  <reporting>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-report-plugin</artifactId>
        <version>3.5.6</version>
      </plugin>
    </plugins>
  </reporting>
</project>
```

- [ ] **Step 4: Generate the Maven Wrapper**

```bash
mvn wrapper:wrapper -Dmaven=3.9.9
./mvnw -version
```

Expected: wrapper downloads Maven and prints its version.

- [ ] **Step 5: Extend `.gitignore`**

Append to the existing file (keep the current editor/OS entries):

```gitignore
# Maven
target/
!.mvn/wrapper/maven-wrapper.jar

# Test output
allure-results/
allure-report/
test-output/

# Local configuration and credentials — never commit
.env
.env.local
```

Commit `.gitignore` **before** creating `.env`, so the file can never be staged
by accident.

- [ ] **Step 6: Write `.env.example` (committed) and `.env` (never committed)**

`.env.example` documents every key with placeholder values only:

```dotenv
# Copy to .env and fill in real values. .env is gitignored — never commit it.
# Sources: BOOKER_* and UI_BASE_URL come from the assignment brief.
# GRAPHQL_ENDPOINT is the Video Streaming example schema from the Hygraph
# playground: select that schema and read the request URL from the browser
# network panel.

# --- Restful Booker ---
BOOKER_BASE_URL=<booker-base-url>
BOOKER_USERNAME=<booker-username>
BOOKER_PASSWORD=<booker-password>

# --- Hygraph GraphQL ---
GRAPHQL_ENDPOINT=<hygraph-content-api-endpoint>

# --- DemoQA UI ---
UI_BASE_URL=<demoqa-base-url>

# --- Optional. Defaults shown; omit to accept them. ---
# UI_BROWSER=chromium
# UI_HEADLESS=true
# HTTP_TIMEOUT_MS=30000
```

Then create the local `.env` by copying and filling it in:

```bash
cp .env.example .env
# edit .env with the real values, then verify it is ignored:
git check-ignore -v .env
git status --short   # .env must NOT appear
```

Both commands must confirm `.env` is ignored before you continue.

- [ ] **Step 7: Write the failing test**

Note what this test does **not** do: it never asserts a real URL or credential,
because none exists in the repository. It exercises the resolution *mechanism*
using a throwaway key plus the defaulted keys.

`src/test/java/com/flamingo/qa/config/ConfigLoaderTest.java`:

```java
package com.flamingo.qa.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigLoaderTest {

    private static final String SCRATCH_KEY = "scratch.test.key";

    @Test
    void systemPropertyTakesPrecedence() {
        System.setProperty(SCRATCH_KEY, "from-system-property");
        try {
            assertThat(ConfigLoader.get(SCRATCH_KEY)).isEqualTo("from-system-property");
        } finally {
            System.clearProperty(SCRATCH_KEY);
        }
    }

    @Test
    void blankSystemPropertyIsIgnoredAndFallsThrough() {
        System.setProperty("ui.browser", "   ");
        try {
            // Falls through to the built-in default rather than returning blank.
            assertThat(ConfigLoader.get("ui.browser")).isEqualTo("chromium");
        } finally {
            System.clearProperty("ui.browser");
        }
    }

    @Test
    void fallsBackToBuiltInDefaultForOptionalKeys() {
        assertThat(ConfigLoader.get("ui.browser")).isEqualTo("chromium");
        assertThat(ConfigLoader.get("ui.headless")).isEqualTo("true");
        assertThat(ConfigLoader.get("http.timeout.ms")).isEqualTo("30000");
    }

    @Test
    void failsFastWithGuidanceWhenRequiredKeyIsMissing() {
        assertThatThrownBy(() -> ConfigLoader.get("definitely.absent.key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("definitely.absent.key")
                .hasMessageContaining(".env.example");
    }

    @Test
    void mapsDottedKeyToEnvironmentVariableName() {
        assertThat(ConfigLoader.toEnvKey("booker.base.url")).isEqualTo("BOOKER_BASE_URL");
        assertThat(ConfigLoader.toEnvKey("http.timeout.ms")).isEqualTo("HTTP_TIMEOUT_MS");
    }

    @Test
    void typedAccessorsParseDefaults() {
        assertThat(Config.headless()).isTrue();
        assertThat(Config.timeoutMillis()).isEqualTo(30000);
        assertThat(Config.browser()).isEqualTo("chromium");
    }

    @Test
    void requiredEndpointsAreResolvable() {
        // Proves .env (or CI env vars) is wired up, without asserting the value.
        assertThat(Config.bookerBaseUrl()).startsWith("http");
        assertThat(Config.graphQlEndpoint()).startsWith("http");
        assertThat(Config.uiBaseUrl()).startsWith("http");
        assertThat(Config.bookerUsername()).isNotBlank();
        assertThat(Config.bookerPassword()).isNotBlank();
    }
}
```

- [ ] **Step 8: Run test to verify it fails**

Run: `./mvnw test -Dtest=ConfigLoaderTest`
Expected: compilation failure — `ConfigLoader` / `Config` do not exist.

- [ ] **Step 9: Write `ConfigLoader`**

```java
package com.flamingo.qa.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves configuration without storing any of it in the repository.
 *
 * <p>Precedence: system property, then OS environment variable, then {@code .env},
 * then a built-in default. Only presentation settings have defaults — every URL
 * and credential is required, and a missing one fails fast rather than silently
 * falling back to a hard-coded endpoint.
 */
public final class ConfigLoader {

    /** Non-sensitive defaults only. Never add a URL or credential here. */
    private static final Map<String, String> DEFAULTS = Map.of(
            "ui.browser", "chromium",
            "ui.headless", "true",
            "http.timeout.ms", "30000");

    // ignoreIfMissing: CI supplies everything as environment variables, with no file.
    private static final Dotenv DOTENV = Dotenv.configure().ignoreIfMissing().load();

    private ConfigLoader() {
    }

    public static String get(String key) {
        String envKey = toEnvKey(key);

        String fromSystem = System.getProperty(key);
        if (isPresent(fromSystem)) {
            return fromSystem;
        }
        String fromEnvironment = System.getenv(envKey);
        if (isPresent(fromEnvironment)) {
            return fromEnvironment;
        }
        String fromDotenv = DOTENV.get(envKey);
        if (isPresent(fromDotenv)) {
            return fromDotenv;
        }
        String fallback = DEFAULTS.get(key);
        if (isPresent(fallback)) {
            return fallback;
        }
        throw new IllegalStateException(
                "Missing required configuration '" + key + "' (" + envKey + "). "
                        + "Copy .env.example to .env and fill it in, or set " + envKey
                        + " in the environment.");
    }

    static String toEnvKey(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
```

CAUTION: `Dotenv.configure().ignoreIfMissing()` is required. Without it, the
suite throws on any machine or CI runner that has no `.env`, which is exactly the
configuration CI uses.

- [ ] **Step 10: Write `Config`**

```java
package com.flamingo.qa.config;

/** Typed access to configuration. Tests never reference raw string keys. */
public final class Config {

    private Config() {
    }

    public static String bookerBaseUrl() {
        return ConfigLoader.get("booker.base.url");
    }

    public static String bookerUsername() {
        return ConfigLoader.get("booker.username");
    }

    public static String bookerPassword() {
        return ConfigLoader.get("booker.password");
    }

    public static String graphQlEndpoint() {
        return ConfigLoader.get("graphql.endpoint");
    }

    public static String uiBaseUrl() {
        return ConfigLoader.get("ui.base.url");
    }

    public static String browser() {
        return ConfigLoader.get("ui.browser");
    }

    public static boolean headless() {
        return Boolean.parseBoolean(ConfigLoader.get("ui.headless"));
    }

    public static int timeoutMillis() {
        return Integer.parseInt(ConfigLoader.get("http.timeout.ms"));
    }
}
```

- [ ] **Step 11: Run test to verify it passes**

Run: `./mvnw test -Dtest=ConfigLoaderTest`
Expected: PASS, 6 tests.

CAUTION: `DOTENV` is loaded once into a static final, but resolution happens per
call, which is why the system-property tests pass. Do not "optimise" by caching
resolved values — `-D` overrides would stop working.

`requiredEndpointsAreResolvable` fails if `.env` is absent or incomplete. That is
intended: it is the check that tells you the setup step was skipped.

- [ ] **Step 12: Commit**

```bash
git add pom.xml mvnw mvnw.cmd .mvn .gitignore .env.example src/
git commit -m "Add Maven scaffold, wrapper, and configuration layer"

# Verify .env did not sneak in:
git show --stat --name-only HEAD | grep -x '.env' && echo "ABORT: .env committed" || echo "OK"
```

---

### Task 2: REST client foundation and authentication tests

**Files:**
- Create: `src/main/java/com/flamingo/qa/api/Json.java`
- Create: `src/main/java/com/flamingo/qa/api/ApiResponse.java`
- Create: `src/main/java/com/flamingo/qa/api/RestClientFactory.java`
- Create: `src/main/java/com/flamingo/qa/api/booker/model/AuthRequest.java`
- Create: `src/main/java/com/flamingo/qa/api/booker/model/AuthResponse.java`
- Create: `src/main/java/com/flamingo/qa/api/booker/AuthClient.java`
- Create: `src/main/java/com/flamingo/qa/api/booker/TokenProvider.java`
- Create: `src/main/java/com/flamingo/qa/junit/ApiTest.java`
- Test: `src/test/java/com/flamingo/qa/api/BookerAuthTest.java`

**Interfaces:**
- Consumes: `Config.bookerBaseUrl()`, `Config.bookerUsername()`, `Config.bookerPassword()` (Task 1).
- Produces:
  - `Json.mapper() -> ObjectMapper`
  - `ApiResponse.from(io.restassured.response.Response, Class<T>) -> ApiResponse<T>`
  - `ApiResponse#statusCode() -> int`, `#body() -> T` (null for non-JSON responses), `#rawBody() -> String`
  - `RestClientFactory.booker() -> io.restassured.specification.RequestSpecification`
  - `AuthClient#createToken(String username, String password) -> ApiResponse<AuthResponse>`
  - `AuthResponse#getToken() -> String`, `#getReason() -> String`
  - `TokenProvider.token() -> String`
  - `@ApiTest` meta-annotation (implies `@Test` + `@Tag("api")`)

- [ ] **Step 1: Write the failing test**

`src/test/java/com/flamingo/qa/api/BookerAuthTest.java`:

```java
package com.flamingo.qa.api;

import com.flamingo.qa.api.booker.AuthClient;
import com.flamingo.qa.api.booker.model.AuthResponse;
import com.flamingo.qa.config.Config;
import com.flamingo.qa.junit.ApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Restful Booker")
@Feature("Authentication")
class BookerAuthTest {

    private final AuthClient authClient = new AuthClient();

    @ApiTest
    @DisplayName("Valid credentials return an auth token")
    void returnsTokenForValidCredentials() {
        ApiResponse<AuthResponse> response =
                authClient.createToken(Config.bookerUsername(), Config.bookerPassword());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getToken()).isNotBlank();
        assertThat(response.body().getReason()).isNull();
    }

    @ApiTest
    @DisplayName("Bad password returns HTTP 200 with a 'Bad credentials' reason, not 401")
    void rejectsBadPasswordWithReasonNotUnauthorized() {
        ApiResponse<AuthResponse> response =
                authClient.createToken(Config.bookerUsername(), "definitely-wrong");

        // Documented quirk of this API: the failure is reported in the body,
        // not the status line.
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getReason()).isEqualTo("Bad credentials");
        assertThat(response.body().getToken()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=BookerAuthTest`
Expected: compilation failure — `AuthClient`, `ApiResponse`, `@ApiTest` do not exist.

- [ ] **Step 3: Write `Json` and `ApiResponse`**

```java
package com.flamingo.qa.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Single shared Jackson mapper so serialisation rules are identical everywhere. */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private Json() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
```

```java
package com.flamingo.qa.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;

/**
 * One return type for both happy and error paths.
 *
 * <p>Restful Booker answers errors with plain text ({@code Not Found},
 * {@code Forbidden}), so {@link #body()} is null whenever the response was not
 * JSON. Negative tests assert on {@link #rawBody()}.
 */
public final class ApiResponse<T> {

    private final int statusCode;
    private final T body;
    private final String rawBody;

    private ApiResponse(int statusCode, T body, String rawBody) {
        this.statusCode = statusCode;
        this.body = body;
        this.rawBody = rawBody;
    }

    public static <T> ApiResponse<T> from(Response response, Class<T> type) {
        String raw = response.asString();
        T parsed = null;
        if (isJson(response) && !raw.isBlank()) {
            try {
                parsed = Json.mapper().readValue(raw, type);
            } catch (JsonProcessingException e) {
                // A JSON response that will not map to its model is a real defect,
                // not something to swallow.
                throw new IllegalStateException(
                        "Could not map JSON response to " + type.getSimpleName() + ": " + raw, e);
            }
        }
        return new ApiResponse<>(response.statusCode(), parsed, raw);
    }

    private static boolean isJson(Response response) {
        String contentType = response.getContentType();
        return contentType != null && contentType.toLowerCase().contains("json");
    }

    public int statusCode() {
        return statusCode;
    }

    /** Mapped body, or null when the response was not JSON. */
    public T body() {
        return body;
    }

    public String rawBody() {
        return rawBody;
    }
}
```

- [ ] **Step 4: Write `RestClientFactory`**

```java
package com.flamingo.qa.api;

import com.flamingo.qa.config.Config;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/** Builds the shared request specifications. */
public final class RestClientFactory {

    static {
        // Quiet on green, fully diagnosable on red.
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL);
    }

    private static final RequestSpecification BOOKER_SPEC = new RequestSpecBuilder()
            .setBaseUri(Config.bookerBaseUrl())
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .addFilter(new AllureRestAssured())
            .build();

    private RestClientFactory() {
    }

    public static RequestSpecification booker() {
        return given().spec(BOOKER_SPEC);
    }
}
```

- [ ] **Step 5: Write the auth models**

```java
package com.flamingo.qa.api.booker.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class AuthRequest {
    String username;
    String password;
}
```

```java
package com.flamingo.qa.api.booker.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Carries either a token (success) or a reason (failure). Never both. */
@Value
@Builder
@Jacksonized
public class AuthResponse {
    String token;
    String reason;
}
```

- [ ] **Step 6: Write `AuthClient` and `TokenProvider`**

```java
package com.flamingo.qa.api.booker;

import com.flamingo.qa.api.ApiResponse;
import com.flamingo.qa.api.RestClientFactory;
import com.flamingo.qa.api.booker.model.AuthRequest;
import com.flamingo.qa.api.booker.model.AuthResponse;
import io.qameta.allure.Step;
import io.restassured.response.Response;

public class AuthClient {

    @Step("Request auth token for user {username}")
    public ApiResponse<AuthResponse> createToken(String username, String password) {
        Response response = RestClientFactory.booker()
                .body(AuthRequest.builder().username(username).password(password).build())
                .post("/auth");
        return ApiResponse.from(response, AuthResponse.class);
    }
}
```

```java
package com.flamingo.qa.api.booker;

import com.flamingo.qa.api.ApiResponse;
import com.flamingo.qa.api.booker.model.AuthResponse;
import com.flamingo.qa.config.Config;

/**
 * Authenticates once per JVM and caches the token.
 *
 * <p>The assignment asks that these public services not be overloaded, so the
 * whole suite spends a single /auth call rather than one per test.
 */
public final class TokenProvider {

    private static volatile String cachedToken;

    private TokenProvider() {
    }

    public static String token() {
        String local = cachedToken;
        if (local == null) {
            synchronized (TokenProvider.class) {
                local = cachedToken;
                if (local == null) {
                    ApiResponse<AuthResponse> response = new AuthClient()
                            .createToken(Config.bookerUsername(), Config.bookerPassword());
                    if (response.statusCode() != 200 || response.body().getToken() == null) {
                        throw new IllegalStateException(
                                "Could not obtain auth token: " + response.rawBody());
                    }
                    local = response.body().getToken();
                    cachedToken = local;
                }
            }
        }
        return local;
    }
}
```

- [ ] **Step 7: Write the `@ApiTest` meta-annotation**

```java
package com.flamingo.qa.junit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an API test. Bundles {@code @Test} and the "api" tag. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Test
@Tag("api")
public @interface ApiTest {
}
```

CAUTION: `@ApiTest` implies `@Test`, so it cannot be combined with
`@ParameterizedTest`. The data-driven test in Task 4 uses
`@ParameterizedTest` + `@Tag("api")` directly.

- [ ] **Step 8: Run test to verify it passes**

Run: `./mvnw test -Dtest=BookerAuthTest`
Expected: PASS, 2 tests. Requires network.

- [ ] **Step 9: Commit**

```bash
git add src/
git commit -m "Add REST client foundation and Booker authentication tests"
```

---

### Task 3: Booking models, client, and CRUD tests

**Files:**
- Create: `src/main/java/com/flamingo/qa/api/booker/model/BookingDates.java`
- Create: `src/main/java/com/flamingo/qa/api/booker/model/Booking.java`
- Create: `src/main/java/com/flamingo/qa/api/booker/model/CreateBookingResponse.java`
- Create: `src/main/java/com/flamingo/qa/api/booker/BookingClient.java`
- Create: `src/main/java/com/flamingo/qa/data/TestDataFactory.java`
- Test: `src/test/java/com/flamingo/qa/api/BookingCrudTest.java`

**Interfaces:**
- Consumes: `ApiResponse`, `RestClientFactory.booker()`, `TokenProvider.token()`, `@ApiTest` (Task 2).
- Produces:
  - `BookingDates#getCheckin() -> LocalDate`, `#getCheckout() -> LocalDate`
  - `Booking#getFirstname()`, `#getLastname()`, `#getTotalprice() -> Integer`, `#getDepositpaid() -> Boolean`, `#getBookingdates() -> BookingDates`, `#getAdditionalneeds() -> String`
  - `Booking.builder()`, `Booking#toBuilder()`
  - `CreateBookingResponse#getBookingid() -> Integer`, `#getBooking() -> Booking`
  - `BookingClient#create(Booking) -> ApiResponse<CreateBookingResponse>`
  - `BookingClient#getById(int) -> ApiResponse<Booking>`
  - `BookingClient#update(int, Booking, String token) -> ApiResponse<Booking>`
  - `BookingClient#delete(int, String token) -> ApiResponse<Void>`
  - `TestDataFactory.randomBooking() -> Booking`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/flamingo/qa/api/BookingCrudTest.java`:

```java
package com.flamingo.qa.api;

import com.flamingo.qa.api.booker.BookingClient;
import com.flamingo.qa.api.booker.TokenProvider;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.CreateBookingResponse;
import com.flamingo.qa.data.TestDataFactory;
import com.flamingo.qa.junit.ApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Booking lifecycle against the live service.
 *
 * <p>Each test seeds the data it needs and asserts only on that data, so the
 * tests are independent and safe to run concurrently. They deliberately do
 * <em>not</em> delete what they create: the service resets periodically by
 * design, teardown is not a requirement, and an extra DELETE per test is load
 * on a public service the brief asks us not to overload.
 *
 * <p>Note which calls carry a token. This API leaves POST and GET
 * unauthenticated and protects only PUT and DELETE, so passing credentials to
 * the first two would assert a rule the API does not enforce.
 */
@Epic("Restful Booker")
@Feature("Booking CRUD")
class BookingCrudTest {

    private final BookingClient bookings = new BookingClient();

    private int seedBooking(Booking booking) {
        ApiResponse<CreateBookingResponse> created = bookings.create(booking);
        assertThat(created.statusCode()).isEqualTo(200);
        return created.body().getBookingid();
    }

    @ApiTest
    @DisplayName("Creating a booking returns an id and echoes the submitted payload")
    void createsBooking() {
        Booking booking = TestDataFactory.randomBooking();

        ApiResponse<CreateBookingResponse> response = bookings.create(booking);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getBookingid()).isPositive();
        assertThat(response.body().getBooking()).isEqualTo(booking);
    }

    @ApiTest
    @DisplayName("Retrieving a booking by id returns the created booking")
    void retrievesBookingById() {
        Booking booking = TestDataFactory.randomBooking();
        int id = seedBooking(booking);

        ApiResponse<Booking> response = bookings.getById(id);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(booking);
    }

    @ApiTest
    @DisplayName("Updating a booking replaces its fields")
    void updatesBooking() {
        int id = seedBooking(TestDataFactory.randomBooking());
        Booking updated = TestDataFactory.randomBooking().toBuilder()
                .firstname("Updated")
                .lastname("Guest")
                .totalprice(999)
                .build();

        ApiResponse<Booking> response = bookings.update(id, updated, TokenProvider.token());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(updated);
        assertThat(bookings.getById(id).body()).isEqualTo(updated);
    }

    @ApiTest
    @DisplayName("Deleting a booking returns 201 and the booking is then gone")
    void deletesBooking() {
        int id = seedBooking(TestDataFactory.randomBooking());

        ApiResponse<Void> response = bookings.delete(id, TokenProvider.token());

        // Documented quirk: this API answers a successful DELETE with 201 Created.
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(bookings.getById(id).statusCode()).isEqualTo(404);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=BookingCrudTest`
Expected: compilation failure — `BookingClient`, `Booking`, `TestDataFactory` do not exist.

- [ ] **Step 3: Write the booking models**

```java
package com.flamingo.qa.api.booker.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

@Value
@Builder(toBuilder = true)
@Jacksonized
public class BookingDates {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate checkin;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate checkout;
}
```

```java
package com.flamingo.qa.api.booker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder(toBuilder = true)
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Booking {
    String firstname;
    String lastname;
    Integer totalprice;
    Boolean depositpaid;
    BookingDates bookingdates;
    String additionalneeds;
}
```

```java
package com.flamingo.qa.api.booker.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class CreateBookingResponse {
    Integer bookingid;
    Booking booking;
}
```

CAUTION: the equality assertions above rely on Lombok `@Value` generating
`equals`. `@JsonInclude(NON_NULL)` matters because Restful Booker omits
`additionalneeds` from responses when it was not sent — without it, a
round-tripped booking would not equal the submitted one.

- [ ] **Step 4: Write `TestDataFactory`**

```java
package com.flamingo.qa.data;

import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.BookingDates;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

/** Builds valid, unique test data. Every API test seeds its own booking. */
public final class TestDataFactory {

    private static final String[] FIRST_NAMES = {"Ada", "Grace", "Alan", "Edsger", "Barbara"};
    private static final String[] LAST_NAMES = {"Lovelace", "Hopper", "Turing", "Dijkstra", "Liskov"};

    private TestDataFactory() {
    }

    public static Booking randomBooking() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        LocalDate checkin = LocalDate.now().plusDays(random.nextInt(1, 30));
        return Booking.builder()
                .firstname(pick(FIRST_NAMES))
                .lastname(pick(LAST_NAMES))
                .totalprice(random.nextInt(50, 5000))
                .depositpaid(random.nextBoolean())
                .bookingdates(BookingDates.builder()
                        .checkin(checkin)
                        .checkout(checkin.plusDays(random.nextInt(1, 14)))
                        .build())
                .additionalneeds("Breakfast")
                .build();
    }

    private static String pick(String[] values) {
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
```

- [ ] **Step 5: Write `BookingClient`**

```java
package com.flamingo.qa.api.booker;

import com.flamingo.qa.api.ApiResponse;
import com.flamingo.qa.api.RestClientFactory;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.CreateBookingResponse;
import io.qameta.allure.Step;
import io.restassured.response.Response;

/**
 * Restful Booker booking endpoints.
 *
 * <p>Mutating calls authenticate with a {@code token} cookie, which is what this
 * API expects in place of a bearer header.
 */
public class BookingClient {

    private static final String BY_ID = "/booking/{id}";

    @Step("Create booking")
    public ApiResponse<CreateBookingResponse> create(Booking booking) {
        Response response = RestClientFactory.booker().body(booking).post("/booking");
        return ApiResponse.from(response, CreateBookingResponse.class);
    }

    @Step("Get booking {id}")
    public ApiResponse<Booking> getById(int id) {
        Response response = RestClientFactory.booker().get(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    @Step("Update booking {id}")
    public ApiResponse<Booking> update(int id, Booking booking, String token) {
        Response response = RestClientFactory.booker()
                .cookie("token", token)
                .body(booking)
                .put(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    /** Update without credentials. Used to prove the endpoint is protected. */
    @Step("Update booking {id} without a token")
    public ApiResponse<Booking> updateWithoutToken(int id, Booking booking) {
        Response response = RestClientFactory.booker().body(booking).put(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    @Step("Delete booking {id}")
    public ApiResponse<Void> delete(int id, String token) {
        Response response = RestClientFactory.booker().cookie("token", token).delete(BY_ID, id);
        return ApiResponse.from(response, Void.class);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw test -Dtest=BookingCrudTest`
Expected: PASS, 4 tests.

If `createsBooking` fails on the `isEqualTo(booking)` assertion, inspect the
logged response: the most likely cause is date serialisation. Confirm the JSON
sent contains `"checkin":"YYYY-MM-DD"` and not an array.

- [ ] **Step 7: Commit**

```bash
git add src/
git commit -m "Add booking models, client, and CRUD tests"
```

---

### Task 4: Booking negative and data-driven tests

**Files:**
- Test: `src/test/java/com/flamingo/qa/api/BookingNegativeTest.java`
- Test: `src/test/java/com/flamingo/qa/api/BookingDataDrivenTest.java`

**Interfaces:**
- Consumes: `BookingClient` (incl. `updateWithoutToken`), `TestDataFactory`, `TokenProvider`, `@ApiTest` (Tasks 2–3).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Write the failing negative test**

```java
package com.flamingo.qa.api;

import com.flamingo.qa.api.booker.BookingClient;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.data.TestDataFactory;
import com.flamingo.qa.junit.ApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Restful Booker")
@Feature("Booking error handling")
class BookingNegativeTest {

    private static final int ABSENT_BOOKING_ID = 99_999_999;

    private final BookingClient bookings = new BookingClient();

    @ApiTest
    @DisplayName("Retrieving a non-existent booking returns 404 with a plain-text body")
    void returnsNotFoundForUnknownId() {
        ApiResponse<Booking> response = bookings.getById(ABSENT_BOOKING_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.rawBody()).isEqualTo("Not Found");
        assertThat(response.body()).isNull();
    }

    @ApiTest
    @DisplayName("Updating a booking without a token is rejected with 403")
    void rejectsUpdateWithoutToken() {
        ApiResponse<Booking> response =
                bookings.updateWithoutToken(ABSENT_BOOKING_ID, TestDataFactory.randomBooking());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.rawBody()).isEqualTo("Forbidden");
    }
}
```

- [ ] **Step 2: Run to verify it fails, then passes**

Run: `./mvnw test -Dtest=BookingNegativeTest`

These should pass immediately — the client methods already exist from Task 3.
If `rawBody()` is not exactly `Not Found` / `Forbidden`, re-probe the API with
curl and update the expected strings; do **not** weaken to `contains`.

- [ ] **Step 3: Write the data-driven test**

```java
package com.flamingo.qa.api;

import com.flamingo.qa.api.booker.BookingClient;
import com.flamingo.qa.api.booker.TokenProvider;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.CreateBookingResponse;
import com.flamingo.qa.data.TestDataFactory;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Restful Booker")
@Feature("Booking CRUD")
class BookingDataDrivenTest {

    private final BookingClient bookings = new BookingClient();

    // @ApiTest implies @Test, which cannot combine with @ParameterizedTest,
    // so the tag is applied directly here.
    @Tag("api")
    @ParameterizedTest(name = "deposit paid={0}, additional needs={1}")
    @CsvSource({
            "true,  Breakfast",
            "false, Breakfast",
            "true,  ",
            "false, "
    })
    void createsBookingsWithVaryingOptionalFields(boolean depositPaid, String additionalNeeds) {
        Booking booking = TestDataFactory.randomBooking().toBuilder()
                .depositpaid(depositPaid)
                .additionalneeds(additionalNeeds)
                .build();

        ApiResponse<CreateBookingResponse> response = bookings.create(booking);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getBookingid()).isPositive();
        assertThat(response.body().getBooking().getDepositpaid()).isEqualTo(depositPaid);
        assertThat(response.body().getBooking().getAdditionalneeds()).isEqualTo(additionalNeeds);

        bookings.delete(response.body().getBookingid(), TokenProvider.token());
    }
}
```

CAUTION: an empty `@CsvSource` trailing column yields `null`, not `""`. That is
intended — it exercises the omitted-field path that `@JsonInclude(NON_NULL)`
handles. If the API echoes `""` instead of null for those rows, change the
assertion to `isNull()` only for the rows where it is actually null; do not
guess. Run it and read the output.

- [ ] **Step 4: Run both test classes**

Run: `./mvnw test -Dtest='BookingNegativeTest,BookingDataDrivenTest'`
Expected: PASS, 2 + 4 invocations.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "Add booking negative and data-driven tests"
```

---

### Task 5: GraphQL client and positive tests

**Files:**
- Create: `src/main/java/com/flamingo/qa/api/graphql/GraphQlRequest.java`
- Create: `src/main/java/com/flamingo/qa/api/graphql/GraphQlError.java`
- Create: `src/main/java/com/flamingo/qa/api/graphql/GraphQlResponse.java`
- Create: `src/main/java/com/flamingo/qa/api/graphql/GraphQlClient.java`
- Create: `src/main/java/com/flamingo/qa/api/graphql/QueryLoader.java`
- Create: `src/main/resources/graphql/movies-page.graphql`
- Create: `src/main/resources/graphql/movie-by-id.graphql`
- Create: `src/main/resources/graphql/movies-with-publisher.graphql`
- Test: `src/test/java/com/flamingo/qa/graphql/GraphQlPositiveTest.java`

**Interfaces:**
- Consumes: `Config.graphQlEndpoint()`, `Json.mapper()` (Tasks 1–2).
- Produces:
  - `GraphQlRequest.builder().query(String).variables(Map<String,Object>).operationName(String).build()`
  - `GraphQlClient#execute(GraphQlRequest) -> GraphQlResponse`
  - `GraphQlResponse#statusCode() -> int`, `#hasErrors() -> boolean`, `#errors() -> List<GraphQlError>`, `#data() -> JsonNode`, `#at(String jsonPointer) -> JsonNode`
  - `GraphQlError#getMessage() -> String`
  - `QueryLoader.load(String fileName) -> String`

- [ ] **Step 1: Write the GraphQL query files**

`src/main/resources/graphql/movies-page.graphql`:

```graphql
query MoviesPage($first: Int!, $skip: Int!) {
  movies(first: $first, skip: $skip) {
    id
    title
  }
  moviesConnection {
    aggregate {
      count
    }
  }
}
```

`src/main/resources/graphql/movie-by-id.graphql`:

```graphql
query MovieById($id: ID!) {
  movie(where: { id: $id }) {
    id
    title
  }
}
```

`src/main/resources/graphql/movies-with-publisher.graphql`:

```graphql
fragment MovieSummary on Movie {
  id
  title
}

query MoviesWithPublisher($first: Int!) {
  movies(first: $first) {
    ...MovieSummary
    publishedBy {
      id
      name
    }
  }
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.flamingo.qa.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.flamingo.qa.api.graphql.GraphQlClient;
import com.flamingo.qa.api.graphql.GraphQlRequest;
import com.flamingo.qa.api.graphql.GraphQlResponse;
import com.flamingo.qa.api.graphql.QueryLoader;
import com.flamingo.qa.junit.ApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Hygraph GraphQL")
@Feature("Queries")
class GraphQlPositiveTest {

    private final GraphQlClient graphQl = new GraphQlClient();

    private String anyMovieId() {
        GraphQlResponse response = graphQl.execute(GraphQlRequest.builder()
                .query(QueryLoader.load("movies-page.graphql"))
                .variables(Map.of("first", 1, "skip", 0))
                .build());
        return response.at("/data/movies/0/id").asText();
    }

    @ApiTest
    @DisplayName("Pagination returns exactly the requested number of movies")
    void limitsResultsToRequestedPageSize() {
        int pageSize = 3;

        GraphQlResponse response = graphQl.execute(GraphQlRequest.builder()
                .query(QueryLoader.load("movies-page.graphql"))
                .variables(Map.of("first", pageSize, "skip", 0))
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.hasErrors()).isFalse();
        assertThat(response.at("/data/movies")).hasSize(pageSize);
        assertThat(response.at("/data/moviesConnection/aggregate/count").asInt())
                .isGreaterThanOrEqualTo(pageSize);
    }

    @ApiTest
    @DisplayName("A movie can be fetched by its id")
    void fetchesSingleMovieById() {
        String id = anyMovieId();

        GraphQlResponse response = graphQl.execute(GraphQlRequest.builder()
                .query(QueryLoader.load("movie-by-id.graphql"))
                .variables(Map.of("id", id))
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.hasErrors()).isFalse();
        assertThat(response.at("/data/movie/id").asText()).isEqualTo(id);
        assertThat(response.at("/data/movie/title").asText()).isNotBlank();
    }

    @ApiTest
    @DisplayName("Variables drive paging: consecutive pages do not overlap")
    void pagesDoNotOverlapWhenSkipChanges() {
        GraphQlResponse firstPage = graphQl.execute(GraphQlRequest.builder()
                .query(QueryLoader.load("movies-page.graphql"))
                .variables(Map.of("first", 2, "skip", 0))
                .build());
        GraphQlResponse secondPage = graphQl.execute(GraphQlRequest.builder()
                .query(QueryLoader.load("movies-page.graphql"))
                .variables(Map.of("first", 2, "skip", 2))
                .build());

        List<String> firstIds = idsOf(firstPage.at("/data/movies"));
        List<String> secondIds = idsOf(secondPage.at("/data/movies"));

        assertThat(firstIds).hasSize(2);
        assertThat(secondIds).hasSize(2);
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
    }

    @ApiTest
    @DisplayName("A fragment resolves alongside nested fields from a related type")
    void resolvesFragmentAndNestedPublisher() {
        GraphQlResponse response = graphQl.execute(GraphQlRequest.builder()
                .query(QueryLoader.load("movies-with-publisher.graphql"))
                .variables(Map.of("first", 2))
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.hasErrors()).isFalse();

        JsonNode movies = response.at("/data/movies");
        assertThat(movies).hasSize(2);
        movies.forEach(movie -> {
            assertThat(movie.at("/id").asText()).isNotBlank();       // from fragment
            assertThat(movie.at("/title").asText()).isNotBlank();    // from fragment
            assertThat(movie.at("/publishedBy/name").asText()).isNotBlank(); // nested type
        });
    }

    private static List<String> idsOf(JsonNode movies) {
        return movies.findValuesAsText("id");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw test -Dtest=GraphQlPositiveTest`
Expected: compilation failure — GraphQL classes do not exist.

- [ ] **Step 4: Write `QueryLoader`**

```java
package com.flamingo.qa.api.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads .graphql files from the classpath so queries stay out of Java strings. */
public final class QueryLoader {

    private static final String BASE = "/graphql/";

    private QueryLoader() {
    }

    public static String load(String fileName) {
        try (InputStream in = QueryLoader.class.getResourceAsStream(BASE + fileName)) {
            if (in == null) {
                throw new IllegalArgumentException("GraphQL query not found: " + BASE + fileName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + fileName, e);
        }
    }
}
```

- [ ] **Step 5: Write the GraphQL request/response types**

```java
package com.flamingo.qa.api.graphql;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/** Variables travel as a map. They are never interpolated into the query string. */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphQlRequest {
    String query;
    Map<String, Object> variables;
    String operationName;
}
```

```java
package com.flamingo.qa.api.graphql;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class GraphQlError {
    String message;
}
```

```java
package com.flamingo.qa.api.graphql;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * A GraphQL envelope.
 *
 * <p>Hygraph varies its shape by failure class, so every accessor tolerates a
 * missing key: a valid query for an absent entity answers 200 with
 * {@code data.movie: null} and no {@code errors} key at all, while parse and
 * validation failures answer 400 with {@code data: null} and an errors array.
 */
public final class GraphQlResponse {

    private final int statusCode;
    private final JsonNode root;

    public GraphQlResponse(int statusCode, JsonNode root) {
        this.statusCode = statusCode;
        this.root = root;
    }

    public int statusCode() {
        return statusCode;
    }

    public JsonNode data() {
        return root.path("data");
    }

    /** Node at a JSON Pointer, e.g. {@code /data/movies/0/id}. Missing yields MissingNode. */
    public JsonNode at(String jsonPointer) {
        return root.at(jsonPointer);
    }

    public boolean hasErrors() {
        JsonNode errors = root.path("errors");
        return errors.isArray() && !errors.isEmpty();
    }

    public List<GraphQlError> errors() {
        List<GraphQlError> result = new ArrayList<>();
        JsonNode errors = root.path("errors");
        if (errors.isArray()) {
            errors.forEach(e -> result.add(
                    GraphQlError.builder().message(e.path("message").asText()).build()));
        }
        return result;
    }

    /** True when the response carries a {@code data} key whose value is JSON null. */
    public boolean hasNullData() {
        return root.has("data") && root.get("data").isNull();
    }

}
```

CAUTION: `JsonNode.path("data")` returns a `NullNode` when `data` is JSON null
and a `MissingNode` when the key is absent. The two are different and the
negative tests in Task 6 depend on the distinction — that is why `hasNullData()`
uses `has()` plus `isNull()` rather than `path(...).isNull()`.

- [ ] **Step 6: Write `GraphQlClient`**

```java
package com.flamingo.qa.api.graphql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.flamingo.qa.api.Json;
import com.flamingo.qa.config.Config;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

public class GraphQlClient {

    @Step("Execute GraphQL request")
    public GraphQlResponse execute(GraphQlRequest request) {
        attachToReport(request);
        Response response = given()
                .baseUri(Config.graphQlEndpoint())
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .filter(new AllureRestAssured())
                .body(serialise(request))
                .post();
        return new GraphQlResponse(response.statusCode(), parse(response.asString()));
    }

    /**
     * Sends a raw body so deliberately malformed queries can be exercised. A
     * syntactically invalid query cannot be stored as a .graphql file without
     * breaking tooling, so it is supplied inline by the caller.
     */
    @Step("Execute raw GraphQL body")
    public GraphQlResponse executeRaw(String rawJsonBody) {
        Allure.addAttachment("GraphQL raw body", "application/json", rawJsonBody, ".json");
        Response response = given()
                .baseUri(Config.graphQlEndpoint())
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .filter(new AllureRestAssured())
                .body(rawJsonBody)
                .post();
        return new GraphQlResponse(response.statusCode(), parse(response.asString()));
    }

    private void attachToReport(GraphQlRequest request) {
        Allure.addAttachment("GraphQL query", "text/plain", request.getQuery(), ".graphql");
        if (request.getVariables() != null) {
            Allure.addAttachment("GraphQL variables", "application/json",
                    serialise(request.getVariables()), ".json");
        }
    }

    private String serialise(Object value) {
        try {
            return Json.mapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise GraphQL payload", e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return Json.mapper().readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("GraphQL response was not JSON: " + body, e);
        }
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./mvnw test -Dtest=GraphQlPositiveTest`
Expected: PASS, 4 tests.

- [ ] **Step 8: Commit**

```bash
git add src/
git commit -m "Add GraphQL client and positive query tests"
```

---

### Task 6: GraphQL negative tests

**Files:**
- Test: `src/test/java/com/flamingo/qa/graphql/GraphQlNegativeTest.java`

**Interfaces:**
- Consumes: `GraphQlClient#execute`, `GraphQlClient#executeRaw`, `GraphQlResponse#hasErrors/#errors/#at/#hasNullData`, `QueryLoader.load` (Task 5).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Write the failing test**

```java
package com.flamingo.qa.graphql;

import com.flamingo.qa.api.graphql.GraphQlClient;
import com.flamingo.qa.api.graphql.GraphQlRequest;
import com.flamingo.qa.api.graphql.GraphQlResponse;
import com.flamingo.qa.api.graphql.QueryLoader;
import com.flamingo.qa.junit.ApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Hygraph GraphQL")
@Feature("Error handling")
class GraphQlNegativeTest {

    private final GraphQlClient graphQl = new GraphQlClient();

    @ApiTest
    @DisplayName("A valid query for a non-existent id returns HTTP 200 with a null field and no errors")
    void returnsNullFieldRatherThanErrorForUnknownId() {
        GraphQlResponse response = graphQl.execute(GraphQlRequest.builder()
                .query(QueryLoader.load("movie-by-id.graphql"))
                .variables(Map.of("id", "this-id-does-not-exist"))
                .build());

        // The brief asks which shape this API uses. For a semantically valid
        // query against a missing entity it is 200 + null field, NOT an error.
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.hasErrors()).isFalse();
        assertThat(response.at("/data/movie").isNull()).isTrue();
    }

    @ApiTest
    @DisplayName("A malformed query returns HTTP 400 with a parse error and null data")
    void reportsParseErrorForMalformedQuery() {
        // Deliberately unbalanced braces. Kept inline because an invalid query
        // cannot live in a .graphql file without breaking editor tooling.
        String malformedBody = "{\"query\":\"query { movies { id title \"}";

        GraphQlResponse response = graphQl.executeRaw(malformedBody);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.hasErrors()).isTrue();
        assertThat(response.errors().get(0).getMessage()).contains("ParseError");
        assertThat(response.hasNullData()).isTrue();
    }

    @ApiTest
    @DisplayName("Requesting an undefined field returns HTTP 400 naming that field")
    void reportsValidationErrorForUnknownField() {
        String unknownFieldBody =
                "{\"query\":\"query { movies(first: 1) { id notARealField } }\"}";

        GraphQlResponse response = graphQl.executeRaw(unknownFieldBody);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.hasErrors()).isTrue();
        assertThat(response.errors().get(0).getMessage())
                .contains("notARealField")
                .contains("Movie");
        assertThat(response.hasNullData()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=GraphQlNegativeTest`
Expected: PASS, 3 tests.

If a status is 200 rather than 400, the API's behaviour has changed since the
spec was written. Re-probe with curl, update the spec's verified-behaviour table
**and** the assertion, and note it in the README. Do not loosen the assertion to
accept both.

- [ ] **Step 3: Commit**

```bash
git add src/
git commit -m "Add GraphQL negative-path tests"
```

---

### Task 7: Playwright lifecycle, injection, and failure capture

**Files:**
- Create: `src/main/java/com/flamingo/qa/ui/PlaywrightFactory.java`
- Create: `src/main/java/com/flamingo/qa/ui/AdBlocker.java`
- Create: `src/main/java/com/flamingo/qa/junit/Actor.java`
- Create: `src/main/java/com/flamingo/qa/junit/ManagedPage.java`
- Create: `src/main/java/com/flamingo/qa/junit/PageRegistry.java`
- Create: `src/main/java/com/flamingo/qa/junit/PlaywrightExtension.java`
- Create: `src/main/java/com/flamingo/qa/junit/UiTest.java`
- Test: `src/test/java/com/flamingo/qa/ui/PlaywrightInjectionSmokeTest.java`

**Interfaces:**
- Consumes: `Config.browser()`, `Config.headless()`, `Config.uiBaseUrl()`, `Config.timeoutMillis()` (Task 1).
- Produces:
  - `PlaywrightFactory.newContext() -> BrowserContext` (ad-blocked, tracing started)
  - `@UiTest` meta-annotation (implies `@Test` + `@Tag("ui")` + `@ExtendWith(PlaywrightExtension.class)`)
  - `@Actor(String value)` parameter qualifier
  - `Page` injectable as a test-method parameter

- [ ] **Step 1: Install Playwright browsers**

```bash
./mvnw compile
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium"
```

Expected: Chromium downloaded. This must run before any UI test.

- [ ] **Step 2: Write the failing smoke test**

```java
package com.flamingo.qa.ui;

import com.flamingo.qa.config.Config;
import com.flamingo.qa.junit.UiTest;
import com.microsoft.playwright.Page;
import io.qameta.allure.Epic;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("DemoQA")
class PlaywrightInjectionSmokeTest {

    @UiTest
    @DisplayName("An isolated Page is injected and can load the site")
    void injectsUsablePage(Page page) {
        page.navigate(Config.uiBaseUrl() + "/webtables");

        assertThat(page.title()).isNotBlank();
        assertThat(page.url()).contains("webtables");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw test -Dtest=PlaywrightInjectionSmokeTest`
Expected: compilation failure — `@UiTest` does not exist.

- [ ] **Step 4: Write `AdBlocker`**

```java
package com.flamingo.qa.ui;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Route;

import java.util.List;

/**
 * DemoQA embeds ad frames that shift layout and intercept clicks. Aborting those
 * requests at the network layer is both faster and more deterministic than
 * scrolling and retrying around them in every page object.
 */
public final class AdBlocker {

    private static final List<String> BLOCKED_PATTERNS = List.of(
            "**/*googlesyndication.com/**",
            "**/*doubleclick.net/**",
            "**/*google-analytics.com/**",
            "**/*googletagmanager.com/**",
            "**/*googletagservices.com/**",
            "**/*adservice.google.com/**");

    private AdBlocker() {
    }

    public static void install(BrowserContext context) {
        BLOCKED_PATTERNS.forEach(pattern -> context.route(pattern, Route::abort));
    }
}
```

- [ ] **Step 5: Write `PlaywrightFactory`**

```java
package com.flamingo.qa.ui;

import com.flamingo.qa.config.Config;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;

/**
 * Playwright objects are not safe to share across threads, so a Playwright
 * instance and a Browser are held per thread and reused across that thread's
 * tests. Each test gets its own BrowserContext, which is cheap and gives full
 * cookie and storage isolation.
 */
public final class PlaywrightFactory {

    private static final ThreadLocal<Playwright> PLAYWRIGHT =
            ThreadLocal.withInitial(Playwright::create);

    private static final ThreadLocal<Browser> BROWSER = ThreadLocal.withInitial(() ->
            browserType(PLAYWRIGHT.get())
                    .launch(new BrowserType.LaunchOptions().setHeadless(Config.headless())));

    private PlaywrightFactory() {
    }

    public static BrowserContext newContext() {
        BrowserContext context = BROWSER.get().newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080));
        context.setDefaultTimeout(Config.timeoutMillis());
        AdBlocker.install(context);
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));
        return context;
    }

    private static BrowserType browserType(Playwright playwright) {
        String name = Config.browser();
        switch (name.toLowerCase()) {
            case "firefox":
                return playwright.firefox();
            case "webkit":
                return playwright.webkit();
            case "chromium":
                return playwright.chromium();
            default:
                throw new IllegalArgumentException("Unsupported browser: " + name);
        }
    }
}
```

NOTE: `Playwright` and `Browser` instances are thread-affine, and JUnit's worker
threads outlive individual tests, so there is no deterministic point at which
they can be closed. They are released when the JVM exits, which also terminates
the Playwright driver process. This is a deliberate, documented trade-off — record
it in the README rather than adding a shutdown hook, which would attempt to close
these objects from the wrong thread.

- [ ] **Step 6: Write `ManagedPage`, `PageRegistry`, and `@Actor`**

```java
package com.flamingo.qa.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names a Page parameter so a test can take more than one independent browser
 * session, e.g. {@code void t(@Actor("alice") Page a, @Actor("bob") Page b)}.
 * Unused today; the resolver supports it so multi-page tests need no framework
 * change later.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Actor {
    String value();
}
```

```java
package com.flamingo.qa.junit;

import com.flamingo.qa.ui.PlaywrightFactory;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

/** A Page together with the context that owns it, so both can be closed. */
public final class ManagedPage {

    private final BrowserContext context;
    private final Page page;

    private ManagedPage(BrowserContext context, Page page) {
        this.context = context;
        this.page = page;
    }

    public static ManagedPage create() {
        BrowserContext context = PlaywrightFactory.newContext();
        return new ManagedPage(context, context.newPage());
    }

    public BrowserContext context() {
        return context;
    }

    public Page page() {
        return page;
    }
}
```

```java
package com.flamingo.qa.junit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Tracks every page created for one test.
 *
 * <p>JUnit's ExtensionContext.Store cannot be enumerated, so the registry keeps
 * its own ordered map. Failure capture and cleanup therefore cover every page a
 * test opened, not just the first.
 */
public final class PageRegistry {

    private final Map<String, ManagedPage> pages = new LinkedHashMap<>();

    public ManagedPage pageFor(String key) {
        return pages.computeIfAbsent(key, k -> ManagedPage.create());
    }

    public void forEach(BiConsumer<String, ManagedPage> action) {
        pages.forEach(action);
    }

    public boolean isEmpty() {
        return pages.isEmpty();
    }
}
```

- [ ] **Step 7: Write `PlaywrightExtension`**

```java
package com.flamingo.qa.junit;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Injects an isolated Playwright Page per test parameter and captures diagnostics
 * on failure.
 *
 * <p>Capture happens in afterEach rather than in TestWatcher.testFailed, because
 * testFailed fires AFTER afterEach — by which time the browser context would
 * already be closed and there would be nothing left to photograph.
 */
public class PlaywrightExtension implements ParameterResolver, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(PlaywrightExtension.class);

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == Page.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) {
        String key = parameterContext.findAnnotation(Actor.class)
                .map(Actor::value)
                .orElseGet(() -> "page-" + parameterContext.getIndex());
        return registry(extensionContext).pageFor(key).page();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        PageRegistry registry = registry(context);
        if (registry.isEmpty()) {
            return;
        }
        boolean failed = context.getExecutionException().isPresent();
        try {
            if (failed) {
                capture(context, registry);
            }
        } finally {
            registry.forEach((name, managed) -> managed.context().close());
        }
    }

    private void capture(ExtensionContext context, PageRegistry registry) {
        String base = context.getRequiredTestClass().getSimpleName()
                + "." + context.getRequiredTestMethod().getName();
        registry.forEach((name, managed) -> {
            byte[] screenshot = managed.page().screenshot(new Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setPath(Paths.get("target", "screenshots", base + "-" + name + ".png")));
            Allure.addAttachment(base + " [" + name + "]", "image/png",
                    new ByteArrayInputStream(screenshot), ".png");

            Path trace = Paths.get("target", "traces", base + "-" + name + ".zip");
            managed.context().tracing().stop(new Tracing.StopOptions().setPath(trace));
        });
    }

    private PageRegistry registry(ExtensionContext context) {
        return context.getStore(NAMESPACE)
                .getOrComputeIfAbsent(PageRegistry.class, key -> new PageRegistry(),
                        PageRegistry.class);
    }
}
```

CAUTION: on the success path `tracing().stop()` is never called explicitly —
closing the context discards the trace, which is what we want. Do not "tidy" this
by stopping tracing in the finally block; that would write a trace file for every
passing test and bloat CI artifacts.

- [ ] **Step 8: Write `@UiTest`**

```java
package com.flamingo.qa.junit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a UI test: tags it, and wires Page injection plus failure capture. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Test
@Tag("ui")
@ExtendWith(PlaywrightExtension.class)
public @interface UiTest {
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: `./mvnw test -Dtest=PlaywrightInjectionSmokeTest`
Expected: PASS, 1 test.

- [ ] **Step 10: Verify failure capture actually works**

Temporarily add a deliberately failing assertion to the smoke test, run it, and
confirm that `target/screenshots/` and `target/traces/` each contain one file.
Then revert the deliberate failure.

Run: `./mvnw test -Dtest=PlaywrightInjectionSmokeTest ; ls target/screenshots target/traces`
Expected: one `.png` and one `.zip`.

This step is not optional. Screenshot-on-failure that has never been observed
failing is not known to work.

- [ ] **Step 11: Commit**

```bash
git add src/
git commit -m "Add Playwright lifecycle, Page injection, and failure capture"
```

---

### Task 8: Web tables page objects and tests

**Files:**
- Create: `src/main/java/com/flamingo/qa/ui/pages/BasePage.java`
- Create: `src/main/java/com/flamingo/qa/ui/pages/WebTablesPage.java`
- Create: `src/main/java/com/flamingo/qa/ui/pages/components/RegistrationDialog.java`
- Create: `src/main/java/com/flamingo/qa/ui/model/WebTableRecord.java`
- Test: `src/test/java/com/flamingo/qa/ui/WebTablesTest.java`

**Interfaces:**
- Consumes: `@UiTest`, injected `Page`, `Config.uiBaseUrl()` (Tasks 1, 7).
- Produces:
  - `BasePage(Page page)`, `BasePage#page() -> Page`, `BasePage#openPath(String) -> void`
  - `WebTablesPage#open() -> WebTablesPage`
  - `WebTablesPage#addRecord(WebTableRecord) -> WebTablesPage`
  - `WebTablesPage#editRecordByEmail(String email, WebTableRecord) -> WebTablesPage`
  - `WebTablesPage#deleteRecordByEmail(String email) -> WebTablesPage`
  - `WebTablesPage#search(String term) -> WebTablesPage`
  - `WebTablesPage#sortBy(String columnLabel) -> WebTablesPage`
  - `WebTablesPage#visibleRecords() -> List<WebTableRecord>`
  - `WebTableRecord.builder()` with `firstName`, `lastName`, `age`, `email`, `salary`, `department`

- [ ] **Step 1: Verify the live selectors**

DemoQA is a client-rendered SPA, so its served HTML contains no usable markup.
Confirm selectors against a real browser before writing page objects.

Create a temporary probe test:

```java
package com.flamingo.qa.ui;

import com.flamingo.qa.config.Config;
import com.flamingo.qa.junit.UiTest;
import com.microsoft.playwright.Page;

class SelectorProbe {

    @UiTest
    void dumpWebTables(Page page) {
        page.navigate(Config.uiBaseUrl() + "/webtables");
        page.waitForSelector("#addNewRecordButton");
        System.out.println(page.locator("div.container").first().innerHTML());
    }
}
```

Run: `./mvnw test -Dtest=SelectorProbe`

Record the real values for: the add button, the dialog's input ids, the submit
button, the search box, the table body rows and cells, the column headers, and
the per-row edit/delete buttons.

Expected (verify, do not assume): `#addNewRecordButton`, `#firstName`,
`#lastName`, `#userEmail`, `#age`, `#salary`, `#department`, `#submit`,
`#searchBox`, `.rt-tbody .rt-tr-group`, `.rt-td`, `.rt-th`,
`#edit-record-{id}`, `#delete-record-{id}`.

Delete `SelectorProbe.java` before committing this task.

- [ ] **Step 2: Write the failing test**

```java
package com.flamingo.qa.ui;

import com.flamingo.qa.junit.UiTest;
import com.flamingo.qa.ui.model.WebTableRecord;
import com.flamingo.qa.ui.pages.WebTablesPage;
import com.microsoft.playwright.Page;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("DemoQA")
@Feature("Web tables")
class WebTablesTest {

    private static WebTableRecord newRecord() {
        return WebTableRecord.builder()
                .firstName("Ada")
                .lastName("Lovelace")
                .age("36")
                .email("ada.lovelace@example.com")
                .salary("120000")
                .department("Engineering")
                .build();
    }

    @UiTest
    @DisplayName("A new record is added to the table")
    void addsRecord(Page page) {
        WebTableRecord record = newRecord();

        List<WebTableRecord> records = new WebTablesPage(page)
                .open()
                .addRecord(record)
                .search(record.getEmail())
                .visibleRecords();

        assertThat(records).containsExactly(record);
    }

    @UiTest
    @DisplayName("An existing record can be edited")
    void editsRecord(Page page) {
        WebTableRecord original = newRecord();
        WebTableRecord edited = original.toBuilder()
                .firstName("Augusta")
                .salary("150000")
                .build();

        List<WebTableRecord> records = new WebTablesPage(page)
                .open()
                .addRecord(original)
                .editRecordByEmail(original.getEmail(), edited)
                .search(edited.getEmail())
                .visibleRecords();

        assertThat(records).containsExactly(edited);
    }

    @UiTest
    @DisplayName("A record can be deleted")
    void deletesRecord(Page page) {
        WebTableRecord record = newRecord();

        WebTablesPage tables = new WebTablesPage(page).open().addRecord(record);
        int countBefore = tables.visibleRecords().size();

        List<WebTableRecord> remaining = tables
                .deleteRecordByEmail(record.getEmail())
                .visibleRecords();

        assertThat(remaining).hasSize(countBefore - 1);
        assertThat(remaining).doesNotContain(record);
    }

    @UiTest
    @DisplayName("Search narrows the table to matching records")
    void searchFiltersRecords(Page page) {
        List<WebTableRecord> records = new WebTablesPage(page)
                .open()
                .search("Cierra")
                .visibleRecords();

        assertThat(records).isNotEmpty();
        assertThat(records).allSatisfy(record ->
                assertThat(record.getFirstName()).isEqualTo("Cierra"));
    }

    @UiTest
    @DisplayName("Clicking a column header sorts the table by that column")
    void sortsByFirstName(Page page) {
        List<WebTableRecord> sorted = new WebTablesPage(page)
                .open()
                .sortBy("First Name")
                .visibleRecords();

        assertThat(sorted).isNotEmpty();
        assertThat(sorted)
                .extracting(WebTableRecord::getFirstName)
                .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }
}
```

CAUTION: `searchFiltersRecords` relies on DemoQA's three seeded rows, one of
which is named Cierra. Confirm during Step 1 that this row still exists. If the
seed data has changed, add a record first and search for that instead — do not
leave the test depending on data you have not verified.

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw test -Dtest=WebTablesTest`
Expected: compilation failure — `WebTablesPage`, `WebTableRecord` do not exist.

- [ ] **Step 4: Write `WebTableRecord`**

```java
package com.flamingo.qa.ui.model;

import lombok.Builder;
import lombok.Value;

/** One row of the DemoQA web table. All fields are strings, as the UI renders them. */
@Value
@Builder(toBuilder = true)
public class WebTableRecord {
    String firstName;
    String lastName;
    String age;
    String email;
    String salary;
    String department;
}
```

- [ ] **Step 5: Write `BasePage`**

```java
package com.flamingo.qa.ui.pages;

import com.flamingo.qa.config.Config;
import com.microsoft.playwright.Page;

/**
 * Shared page behaviour.
 *
 * <p>Page objects expose actions and data only. Assertions belong in tests.
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    public Page page() {
        return page;
    }

    protected void openPath(String path) {
        page.navigate(Config.uiBaseUrl() + path);
    }
}
```

- [ ] **Step 6: Write `RegistrationDialog`**

```java
package com.flamingo.qa.ui.pages.components;

import com.flamingo.qa.ui.model.WebTableRecord;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/** The add/edit modal on the web tables page. */
public class RegistrationDialog {

    private static final String MODAL = "#registration-form-modal";

    private final Page page;

    public RegistrationDialog(Page page) {
        this.page = page;
    }

    public RegistrationDialog waitUntilOpen() {
        page.locator(MODAL).waitFor();
        return this;
    }

    public void submit(WebTableRecord record) {
        fill("#firstName", record.getFirstName());
        fill("#lastName", record.getLastName());
        fill("#userEmail", record.getEmail());
        fill("#age", record.getAge());
        fill("#salary", record.getSalary());
        fill("#department", record.getDepartment());
        page.click("#submit");
        page.locator(MODAL).waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.DETACHED));
    }

    private void fill(String selector, String value) {
        page.locator(selector).fill(value);
    }
}
```

- [ ] **Step 7: Write `WebTablesPage`**

```java
package com.flamingo.qa.ui.pages;

import com.flamingo.qa.ui.model.WebTableRecord;
import com.flamingo.qa.ui.pages.components.RegistrationDialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

public class WebTablesPage extends BasePage {

    private static final String ADD_BUTTON = "#addNewRecordButton";
    private static final String SEARCH_BOX = "#searchBox";
    private static final String ROW = ".rt-tbody .rt-tr-group";
    private static final String CELL = ".rt-td";
    private static final String HEADER = ".rt-th";

    public WebTablesPage(Page page) {
        super(page);
    }

    public WebTablesPage open() {
        openPath("/webtables");
        page.locator(ADD_BUTTON).waitFor();
        return this;
    }

    public WebTablesPage addRecord(WebTableRecord record) {
        page.click(ADD_BUTTON);
        new RegistrationDialog(page).waitUntilOpen().submit(record);
        return this;
    }

    public WebTablesPage editRecordByEmail(String email, WebTableRecord updated) {
        rowContaining(email).locator("span[id^='edit-record-']").click();
        new RegistrationDialog(page).waitUntilOpen().submit(updated);
        return this;
    }

    public WebTablesPage deleteRecordByEmail(String email) {
        rowContaining(email).locator("span[id^='delete-record-']").click();
        return this;
    }

    public WebTablesPage search(String term) {
        page.locator(SEARCH_BOX).fill(term);
        return this;
    }

    public WebTablesPage sortBy(String columnLabel) {
        page.locator(HEADER).filter(new Locator.FilterOptions().setHasText(columnLabel))
                .first().click();
        return this;
    }

    /** Every rendered, non-empty row. DemoQA pads the grid with blank rows. */
    public List<WebTableRecord> visibleRecords() {
        List<WebTableRecord> records = new ArrayList<>();
        Locator rows = page.locator(ROW);
        for (int i = 0; i < rows.count(); i++) {
            List<String> cells = rows.nth(i).locator(CELL).allInnerTexts();
            if (cells.isEmpty() || cells.get(0).isBlank()) {
                continue;
            }
            records.add(WebTableRecord.builder()
                    .firstName(cells.get(0).trim())
                    .lastName(cells.get(1).trim())
                    .age(cells.get(2).trim())
                    .email(cells.get(3).trim())
                    .salary(cells.get(4).trim())
                    .department(cells.get(5).trim())
                    .build());
        }
        return records;
    }

    private Locator rowContaining(String text) {
        return page.locator(ROW).filter(new Locator.FilterOptions().setHasText(text)).first();
    }
}
```

CAUTION: DemoQA renders a fixed number of grid rows and pads with empty ones, so
`visibleRecords()` must skip blanks. Without the blank filter every assertion on
row counts will be wrong.

NOTE (spec divergence): the design doc lists a separate `WebTableGrid` component
alongside `RegistrationDialog`. Row reading is three locators and one loop, so it
stays on `WebTablesPage` rather than becoming a class with a single caller. If
grid handling grows — column-index lookup, pagination, per-column sorting state —
extract `WebTableGrid` then.

- [ ] **Step 8: Run test to verify it passes**

Run: `./mvnw test -Dtest=WebTablesTest`
Expected: PASS, 5 tests.

Adjust the selectors to the values recorded in Step 1 if any differ. On failure,
open the trace: `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="show-trace target/traces/<file>.zip"`

- [ ] **Step 9: Delete the probe and commit**

```bash
rm -f src/test/java/com/flamingo/qa/ui/SelectorProbe.java
git add src/
git commit -m "Add web tables page objects and CRUD tests"
```

---

### Task 9: Practice form page objects and tests

**Files:**
- Create: `src/main/java/com/flamingo/qa/ui/pages/PracticeFormPage.java`
- Create: `src/main/java/com/flamingo/qa/ui/pages/components/SubmissionModal.java`
- Create: `src/main/java/com/flamingo/qa/ui/model/StudentRegistration.java`
- Create: `src/test/resources/fixtures/avatar.png`
- Test: `src/test/java/com/flamingo/qa/ui/PracticeFormTest.java`

**Interfaces:**
- Consumes: `BasePage`, `@UiTest`, injected `Page`, `Config.uiBaseUrl()` (Tasks 7–8).
- Produces:
  - `StudentRegistration.builder()` with `firstName`, `lastName`, `email`, `gender`, `mobile`, `dateOfBirth` (`LocalDate`), `subject`, `hobby`, `picturePath` (`Path`), `currentAddress`, `state`, `city`
  - `PracticeFormPage#open() -> PracticeFormPage`
  - `PracticeFormPage#fill(StudentRegistration) -> PracticeFormPage`
  - `PracticeFormPage#submit() -> SubmissionModal`
  - `PracticeFormPage#submitExpectingRejection() -> PracticeFormPage`
  - `PracticeFormPage#isModalVisible() -> boolean`
  - `PracticeFormPage#invalidFieldCount() -> int`
  - `SubmissionModal#title() -> String`, `#values() -> Map<String,String>`

- [ ] **Step 1: Create the upload fixture**

```bash
mkdir -p src/test/resources/fixtures
python3 -c "
import zlib, struct
def chunk(t, d):
    c = t + d
    return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c))
raw = b''.join(b'\x00' + bytes([200, 120, 60]) * 8 for _ in range(8))
png = (b'\x89PNG\r\n\x1a\n'
       + chunk(b'IHDR', struct.pack('>IIBBBBB', 8, 8, 8, 2, 0, 0, 0))
       + chunk(b'IDAT', zlib.compress(raw))
       + chunk(b'IEND', b''))
open('src/test/resources/fixtures/avatar.png','wb').write(png)
"
ls -la src/test/resources/fixtures/avatar.png
```

Expected: a valid 8x8 PNG of a few hundred bytes.

- [ ] **Step 2: Verify the live selectors**

Re-create the probe from Task 8, pointed at `/automation-practice-form`, and
record the real selectors for: the name/email/mobile inputs, the gender radio
labels, the date-of-birth input and its month/year selects, the subjects
autocomplete, the hobby checkbox labels, the upload input, the state and city
React-Select widgets, the submit button, and the success modal.

Expected (verify, do not assume): `#firstName`, `#lastName`, `#userEmail`,
`label[for='gender-radio-1']`, `#userNumber`, `#dateOfBirthInput`,
`.react-datepicker__month-select`, `.react-datepicker__year-select`,
`.react-datepicker__day--0NN:not(.react-datepicker__day--outside-month)`,
`#subjectsInput`, `label[for='hobbies-checkbox-1']`, `#uploadPicture`,
`#currentAddress`, `#state`, `#city`, `#react-select-3-option-0`, `#submit`,
`#example-modal-sizes-title-lg`, `.table-responsive tbody tr`.

Delete the probe before committing.

- [ ] **Step 3: Write the failing test**

```java
package com.flamingo.qa.ui;

import com.flamingo.qa.junit.UiTest;
import com.flamingo.qa.ui.model.StudentRegistration;
import com.flamingo.qa.ui.pages.PracticeFormPage;
import com.flamingo.qa.ui.pages.components.SubmissionModal;
import com.microsoft.playwright.Page;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("DemoQA")
@Feature("Practice form")
class PracticeFormTest {

    private static StudentRegistration completeRegistration() {
        return StudentRegistration.builder()
                .firstName("Grace")
                .lastName("Hopper")
                .email("grace.hopper@example.com")
                .gender("Female")
                .mobile("5550001234")
                .dateOfBirth(LocalDate.of(1990, 6, 15))
                .subject("Maths")
                .hobby("Reading")
                .picturePath(Paths.get("src/test/resources/fixtures/avatar.png"))
                .currentAddress("1 Compiler Way")
                .state("NCR")
                .city("Delhi")
                .build();
    }

    @UiTest
    @DisplayName("A complete registration is accepted and echoed in the success modal")
    void submitsCompleteRegistration(Page page) {
        StudentRegistration registration = completeRegistration();

        SubmissionModal modal = new PracticeFormPage(page)
                .open()
                .fill(registration)
                .submit();

        Map<String, String> values = modal.values();

        assertThat(modal.title()).isEqualTo("Thanks for submitting the form");
        assertThat(values.get("Student Name"))
                .isEqualTo(registration.getFirstName() + " " + registration.getLastName());
        assertThat(values.get("Student Email")).isEqualTo(registration.getEmail());
        assertThat(values.get("Gender")).isEqualTo(registration.getGender());
        assertThat(values.get("Mobile")).isEqualTo(registration.getMobile());
        assertThat(values.get("Date of Birth")).isEqualTo("15 June,1990");
        assertThat(values.get("Subjects")).isEqualTo(registration.getSubject());
        assertThat(values.get("Hobbies")).isEqualTo(registration.getHobby());
        assertThat(values.get("Picture")).isEqualTo("avatar.png");
        assertThat(values.get("Address")).isEqualTo(registration.getCurrentAddress());
        assertThat(values.get("State and City"))
                .isEqualTo(registration.getState() + " " + registration.getCity());
    }

    @UiTest
    @DisplayName("Submitting with required fields empty shows no modal and flags the fields")
    void rejectsEmptyRequiredFields(Page page) {
        PracticeFormPage form = new PracticeFormPage(page)
                .open()
                .submitExpectingRejection();

        assertThat(form.isModalVisible()).isFalse();
        // First name, last name and mobile are the required fields.
        assertThat(form.invalidFieldCount()).isGreaterThanOrEqualTo(3);
    }
}
```

CAUTION: the `Date of Birth` expectation `15 June,1990` matches DemoQA's exact
rendering, including the missing space after the comma. Confirm it in Step 2 and
correct the literal if it differs — do not relax the assertion.

- [ ] **Step 4: Run test to verify it fails**

Run: `./mvnw test -Dtest=PracticeFormTest`
Expected: compilation failure — `PracticeFormPage`, `StudentRegistration` do not exist.

- [ ] **Step 5: Write `StudentRegistration`**

```java
package com.flamingo.qa.ui.model;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;
import java.time.LocalDate;

@Value
@Builder(toBuilder = true)
public class StudentRegistration {
    String firstName;
    String lastName;
    String email;
    String gender;
    String mobile;
    LocalDate dateOfBirth;
    String subject;
    String hobby;
    Path picturePath;
    String currentAddress;
    String state;
    String city;
}
```

- [ ] **Step 6: Write `SubmissionModal`**

```java
package com.flamingo.qa.ui.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The "Thanks for submitting the form" dialog and its label/value rows. */
public class SubmissionModal {

    private static final String TITLE = "#example-modal-sizes-title-lg";
    private static final String ROW = ".table-responsive tbody tr";

    private final Page page;

    public SubmissionModal(Page page) {
        this.page = page;
    }

    public SubmissionModal waitUntilVisible() {
        page.locator(TITLE).waitFor();
        return this;
    }

    public String title() {
        return page.locator(TITLE).innerText().trim();
    }

    /** Row labels mapped to their values, e.g. "Student Name" -> "Grace Hopper". */
    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        Locator rows = page.locator(ROW);
        for (int i = 0; i < rows.count(); i++) {
            List<String> cells = rows.nth(i).locator("td").allInnerTexts();
            if (cells.size() == 2) {
                values.put(cells.get(0).trim(), cells.get(1).trim());
            }
        }
        return values;
    }
}
```

- [ ] **Step 7: Write `PracticeFormPage`**

```java
package com.flamingo.qa.ui.pages;

import com.flamingo.qa.ui.model.StudentRegistration;
import com.flamingo.qa.ui.pages.components.SubmissionModal;
import com.microsoft.playwright.Page;

public class PracticeFormPage extends BasePage {

    private static final String SUBMIT = "#submit";
    private static final String MODAL_TITLE = "#example-modal-sizes-title-lg";

    public PracticeFormPage(Page page) {
        super(page);
    }

    public PracticeFormPage open() {
        openPath("/automation-practice-form");
        page.locator("#firstName").waitFor();
        return this;
    }

    public PracticeFormPage fill(StudentRegistration registration) {
        page.locator("#firstName").fill(registration.getFirstName());
        page.locator("#lastName").fill(registration.getLastName());
        page.locator("#userEmail").fill(registration.getEmail());
        selectGender(registration.getGender());
        page.locator("#userNumber").fill(registration.getMobile());
        selectDateOfBirth(registration);
        addSubject(registration.getSubject());
        selectHobby(registration.getHobby());
        page.setInputFiles("#uploadPicture", registration.getPicturePath());
        page.locator("#currentAddress").fill(registration.getCurrentAddress());
        selectFromReactSelect("#state", registration.getState());
        selectFromReactSelect("#city", registration.getCity());
        return this;
    }

    public SubmissionModal submit() {
        clickSubmit();
        return new SubmissionModal(page).waitUntilVisible();
    }

    /** Submits without expecting the modal, for the validation scenario. */
    public PracticeFormPage submitExpectingRejection() {
        clickSubmit();
        return this;
    }

    public boolean isModalVisible() {
        return page.locator(MODAL_TITLE).isVisible();
    }

    public int invalidFieldCount() {
        return page.locator("input:invalid").count();
    }

    private void clickSubmit() {
        // The button sits below the fold; ad blocking keeps it clickable.
        page.locator(SUBMIT).scrollIntoViewIfNeeded();
        page.locator(SUBMIT).click();
    }

    private void selectGender(String gender) {
        page.locator("label").filter(new com.microsoft.playwright.Locator.FilterOptions()
                .setHasText(gender)).first().click();
    }

    private void selectHobby(String hobby) {
        page.locator("label").filter(new com.microsoft.playwright.Locator.FilterOptions()
                .setHasText(hobby)).first().click();
    }

    private void selectDateOfBirth(StudentRegistration registration) {
        page.locator("#dateOfBirthInput").click();
        // Typing into this widget is unreliable; drive its selects instead.
        String month = String.valueOf(registration.getDateOfBirth().getMonthValue() - 1);
        page.selectOption(".react-datepicker__month-select", month);
        page.selectOption(".react-datepicker__year-select",
                String.valueOf(registration.getDateOfBirth().getYear()));
        String day = String.format("%03d", registration.getDateOfBirth().getDayOfMonth());
        page.locator(".react-datepicker__day--" + day
                + ":not(.react-datepicker__day--outside-month)").click();
    }

    private void addSubject(String subject) {
        page.locator("#subjectsInput").fill(subject);
        page.keyboard().press("Enter");
    }

    private void selectFromReactSelect(String rootSelector, String optionText) {
        page.locator(rootSelector).scrollIntoViewIfNeeded();
        page.locator(rootSelector).click();
        page.locator("div[id^='react-select'][id$='-option']")
                .filter(new com.microsoft.playwright.Locator.FilterOptions()
                        .setHasText(optionText))
                .first()
                .click();
    }

}
```

CAUTION: `react-datepicker` day classes are zero-padded to three digits
(`--015` for the 15th), and the `:not(--outside-month)` guard is required or the
click can land on a neighbouring month's cell. Both are common sources of silent
wrong-date bugs.

- [ ] **Step 8: Run test to verify it passes**

Run: `./mvnw test -Dtest=PracticeFormTest`
Expected: PASS, 2 tests.

Fix selectors against the Step 2 recordings if any differ. On failure, replay the
trace with `show-trace` as in Task 8.

- [ ] **Step 9: Commit**

```bash
git add src/
git commit -m "Add practice form page objects and submission tests"
```

---

### Task 10: Retry extension for service-touching tests

**Files:**
- Create: `src/main/java/com/flamingo/qa/junit/RetryOnFailure.java`
- Create: `src/main/java/com/flamingo/qa/junit/RetryExtension.java`
- Test: `src/test/java/com/flamingo/qa/junit/RetryExtensionTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `@RetryOnFailure(int value)` — a method annotation usable in place of `@Test`, retrying up to `value` total attempts.

- [ ] **Step 1: Write the failing test**

```java
package com.flamingo.qa.junit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RetryExtensionTest {

    private static final AtomicInteger ATTEMPTS = new AtomicInteger();

    @RetryOnFailure(3)
    void passesOnThirdAttempt() {
        if (ATTEMPTS.incrementAndGet() < 3) {
            throw new AssertionError("simulated flake, attempt " + ATTEMPTS.get());
        }
    }

    @Test
    void defaultsToTwoAttempts() throws NoSuchMethodException {
        int defaultAttempts = (int) RetryOnFailure.class.getMethod("value").getDefaultValue();
        assertThat(defaultAttempts).isEqualTo(2);
    }

    @AfterAll
    static void assertRetried() {
        assertThat(ATTEMPTS.get()).isEqualTo(3);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=RetryExtensionTest`
Expected: compilation failure — `RetryOnFailure` does not exist.

- [ ] **Step 3: Write `@RetryOnFailure`**

```java
package com.flamingo.qa.junit;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Retries a test up to {@code value} total attempts.
 *
 * <p>Applied only to tests that touch the public services, which do
 * intermittently hiccup. Never apply this suite-wide: a blanket retry converts
 * real, reproducible defects into intermittent noise.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(RetryExtension.class)
public @interface RetryOnFailure {
    int value() default 2;
}
```

- [ ] **Step 4: Write `RetryExtension`**

```java
package com.flamingo.qa.junit;

/** Mutable state shared by every attempt of one retried test. */
final class RetryState {

    private final int maxAttempts;
    private int started;
    private boolean succeeded;

    RetryState(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    int maxAttempts() {
        return maxAttempts;
    }

    boolean shouldRunAnotherAttempt() {
        return !succeeded && started < maxAttempts;
    }

    int nextAttempt() {
        return ++started;
    }

    void markSucceeded() {
        succeeded = true;
    }
}
```

```java
package com.flamingo.qa.junit;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

import java.util.List;

/** One attempt. Reports its own outcome back to the shared {@link RetryState}. */
final class RetryInvocationContext implements TestTemplateInvocationContext {

    private final RetryState state;
    private final int attempt;
    private boolean failed;

    RetryInvocationContext(RetryState state) {
        this.state = state;
        this.attempt = state.nextAttempt();
    }

    @Override
    public String getDisplayName(int invocationIndex) {
        return "attempt " + attempt + " of " + state.maxAttempts();
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        TestExecutionExceptionHandler onFailure = (context, throwable) -> {
            failed = true;
            if (attempt >= state.maxAttempts()) {
                throw throwable; // final attempt: let the test fail for real
            }
            // Otherwise swallow, so the next attempt still runs.
        };

        AfterTestExecutionCallback onFinish = context -> {
            // Cannot rely on context.getExecutionException() here: the handler
            // above has already swallowed it, so a failed attempt would look
            // like a pass. The explicit flag is what makes this correct.
            if (!failed) {
                state.markSucceeded();
            }
        };

        return List.of(onFailure, onFinish);
    }
}
```

```java
package com.flamingo.qa.junit;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.AnnotationSupport;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Supplies up to N invocations, stopping as soon as one succeeds.
 *
 * <p>The stream is consumed lazily by JUnit, which is what allows the
 * short-circuit: once an attempt passes, no further contexts are produced.
 */
public class RetryExtension implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(method -> AnnotationSupport.isAnnotated(method, RetryOnFailure.class))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {

        int maxAttempts = context.getTestMethod()
                .flatMap(method -> AnnotationSupport.findAnnotation(method, RetryOnFailure.class))
                .map(RetryOnFailure::value)
                .orElse(2);

        RetryState state = new RetryState(maxAttempts);

        Spliterator<TestTemplateInvocationContext> attempts =
                new Spliterators.AbstractSpliterator<>(maxAttempts, Spliterator.NONNULL) {
                    @Override
                    public boolean tryAdvance(Consumer<? super TestTemplateInvocationContext> action) {
                        if (!state.shouldRunAnotherAttempt()) {
                            return false;
                        }
                        action.accept(new RetryInvocationContext(state));
                        return true;
                    }
                };

        return StreamSupport.stream(attempts, false);
    }
}
```

CAUTION: `AnnotationSupport` (in `org.junit.platform.commons.support`) is the
public API. Do not substitute `org.junit.platform.commons.util.AnnotationUtils`,
which is marked internal and can change between minor versions.

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=RetryExtensionTest`
Expected: PASS. `ATTEMPTS` must end at exactly 3 — if it is higher, the
success short-circuit is not working; fix it before continuing.

- [ ] **Step 6: Apply the retry to service-touching tests**

Add `@RetryOnFailure(2)` **in place of** `@ApiTest` on only these, and add
`@Tag("api")` alongside since `@RetryOnFailure` does not imply a tag:

- `BookingCrudTest#createsBooking`
- `GraphQlPositiveTest#limitsResultsToRequestedPageSize`

Leave every negative test un-retried: those assert deterministic behaviour, and
retrying them would mask a genuine API change.

- [ ] **Step 7: Run the full API suite**

Run: `./mvnw test -Dgroups=api`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/
git commit -m "Add retry extension for tests touching public services"
```

---

### Task 11: Parallel execution and Allure reporting

**Files:**
- Create: `src/test/resources/junit-platform.properties`
- Create: `src/main/resources/allure.properties`
- Modify: `pom.xml` (only if the Surefire `argLine` needs adjusting)

**Interfaces:**
- Consumes: everything from Tasks 1–10.
- Produces: no new Java API.

- [ ] **Step 1: Write `junit-platform.properties`**

```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=same_thread
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.execution.parallel.config.strategy=fixed
junit.jupiter.execution.parallel.config.fixed.parallelism=4
```

Classes run concurrently; methods within a class stay on one thread. The fixed
parallelism of 4 is deliberate — the brief asks that these public services not be
overloaded.

- [ ] **Step 2: Write `allure.properties`**

```properties
allure.results.directory=target/allure-results
allure.link.issue.pattern=https://github.com/orush/flamingo-home-assignment/issues/{}
```

- [ ] **Step 3: Run the full suite in parallel**

Run: `./mvnw clean test`
Expected: 32 test methods pass (23 scenario tests + 9 framework tests), 0
failures. Wall-clock should be noticeably shorter than serial.

If UI tests now fail intermittently, the most likely cause is `PlaywrightFactory`
state leaking across threads. Verify each thread gets its own `Playwright` —
`ThreadLocal.withInitial` must not be replaced by a shared static.

- [ ] **Step 4: Generate and inspect the Allure report**

```bash
./mvnw allure:report
ls target/site/allure-maven-plugin
```

Expected: a generated report. Confirm that API tests carry request/response
attachments and that GraphQL tests carry query and variables attachments.

- [ ] **Step 5: Verify tag filtering works**

```bash
./mvnw test -Dgroups=api
./mvnw test -Dgroups=ui
```

Expected: 16 methods tagged `api` (Surefire reports 19 executions, because the
data-driven test contributes 4) and 8 methods tagged `ui` (the 7 scenarios plus
the injection smoke test).

NOTE: `ConfigLoaderTest` and `RetryExtensionTest` carry no tag, so they run under
`./mvnw clean test` but under neither group filter. That is intended — they test
the framework, not a service. Do not "fix" it by tagging them `api`.

These are the exact commands the README promises, so they must work.

- [ ] **Step 6: Commit**

```bash
git add src/ pom.xml
git commit -m "Enable parallel execution and Allure reporting"
```

---

### Task 12: GitHub Actions workflow

**Files:**
- Create: `.github/workflows/tests.yml`

**Interfaces:**
- Consumes: the Maven wrapper and tag filtering from earlier tasks.
- Produces: CI artifacts.

- [ ] **Step 1: Register the repository secrets**

Only you can supply these; the values come from the brief and the Hygraph
playground. Run them yourself so nothing is echoed into this session:

```bash
gh secret set BOOKER_BASE_URL
gh secret set BOOKER_USERNAME
gh secret set BOOKER_PASSWORD
gh secret set GRAPHQL_ENDPOINT
gh secret set UI_BASE_URL
```

Each prompts for the value on stdin. Confirm with `gh secret list` — it prints
names and timestamps only, never values.

- [ ] **Step 2: Write the workflow**

```yaml
name: Tests

on:
  push:
    branches: [main]
  pull_request:
  workflow_dispatch:

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Create .env from repository secrets
        run: |
          umask 077
          cat > .env <<'EOF'
          BOOKER_BASE_URL=${{ secrets.BOOKER_BASE_URL }}
          BOOKER_USERNAME=${{ secrets.BOOKER_USERNAME }}
          BOOKER_PASSWORD=${{ secrets.BOOKER_PASSWORD }}
          GRAPHQL_ENDPOINT=${{ secrets.GRAPHQL_ENDPOINT }}
          UI_BASE_URL=${{ secrets.UI_BASE_URL }}
          UI_HEADLESS=true
          EOF

      - name: Install Playwright browsers
        run: |
          ./mvnw -B -q compile
          ./mvnw -B exec:java \
            -Dexec.mainClass=com.microsoft.playwright.CLI \
            -Dexec.args="install --with-deps chromium"

      - name: Run tests
        run: ./mvnw -B clean test

      - name: Upload Surefire reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: target/surefire-reports/

      - name: Upload failure diagnostics
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: failure-diagnostics
          path: |
            target/screenshots/
            target/traces/
          if-no-files-found: ignore

      - name: Upload Allure results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: allure-results
          path: target/allure-results/
```

CAUTION: the browser install step is the one people omit, and its absence is the
classic "passes locally, fails in CI" Playwright error
(`Executable doesn't exist at ...`). `clean` runs in the test step, after the
install, because the browsers live outside `target/` — confirm this holds; if
`clean` ever removes them, move the install step after it.

CAUTION (secrets): never `cat`, `echo` or `ls -l` the generated `.env` in a
workflow step. GitHub masks known secret values in logs, but a transformed or
partial value can slip through unmasked. `umask 077` keeps the file
owner-readable. `.env` is deliberately absent from every `upload-artifact` path
below — do not add `.` or `./` as an artifact path, which would sweep it up.

Note the heredoc delimiter is quoted (`<<'EOF'`). The `${{ secrets.* }}`
expressions are substituted by Actions *before* the shell runs, so the quoting
only prevents the shell from re-expanding anything inside the values — for
example a literal `$` in a password.

- [ ] **Step 3: Verify the wrapper is executable**

```bash
chmod +x mvnw
git update-index --chmod=+x mvnw
```

Without this the workflow fails with `Permission denied`.

- [ ] **Step 4: Commit and push**

```bash
git add .github/ mvnw
git commit -m "Add GitHub Actions workflow running the test suite"
git push -u origin main
```

- [ ] **Step 5: Watch the run**

```bash
gh run watch
```

Expected: green. If it fails, read the logs and fix before continuing — a red
badge on the deliverable is worse than no badge.

---

### Task 13: README and test report

**Files:**
- Create: `README.md`
- Create: `docs/test-run.png` (screenshot of a passing run)

**Interfaces:**
- Consumes: everything.
- Produces: the documentation deliverable.

- [ ] **Step 1: Capture the test-run evidence**

```bash
./mvnw clean test | tee /tmp/test-run.txt
tail -30 /tmp/test-run.txt
```

Screenshot the terminal showing the Surefire summary (tests run, failures 0) and
save it to `docs/test-run.png`. Alternatively screenshot the green GitHub Actions
run. The brief accepts either.

- [ ] **Step 2: Write `README.md`**

Use exactly these sections, which the brief specifies, plus the two additions.

````markdown
# QA Automation Test Suite

REST, GraphQL and UI test automation for the Flamingo QA Automation Engineer
assignment.

[![Tests](https://github.com/orush/flamingo-home-assignment/actions/workflows/tests.yml/badge.svg)](https://github.com/orush/flamingo-home-assignment/actions/workflows/tests.yml)

## Prerequisites

- Java 17+ (the build targets release 17; any newer JDK works)
- Maven 3.6+ — or just use the bundled wrapper, `./mvnw`

### Configuration (required before first run)

No endpoints or credentials are stored in this repository. Create your own
`.env` from the template:

```bash
cp .env.example .env
```

Then fill in the five required values. Where to find each one:

| Key | Where it comes from |
| --- | --- |
| `BOOKER_BASE_URL` | the assignment brief (Restful Booker base URL) |
| `BOOKER_USERNAME` | the assignment brief (auth example payload) |
| `BOOKER_PASSWORD` | the assignment brief (auth example payload) |
| `GRAPHQL_ENDPOINT` | the Hygraph playground: choose the Video Streaming example schema and read the request URL from the browser network panel |
| `UI_BASE_URL` | the assignment brief (DemoQA base URL) |

`.env` is gitignored and must never be committed. Any value can also be supplied
as an environment variable (`BOOKER_BASE_URL=...`) or a system property
(`-Dbooker.base.url=...`), which is how CI runs.

Verify the setup:

```bash
./mvnw test -Dtest=ConfigLoaderTest
```

`requiredEndpointsAreResolvable` fails with a message naming the missing key if
anything is absent.

### Browsers

Chromium, installed by Playwright:

```bash
./mvnw compile
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install --with-deps chromium"
```

## How to Run

```bash
# Run all tests
./mvnw clean test

# Run only API tests (REST + GraphQL)
./mvnw test -Dgroups="api"

# Run only UI tests
./mvnw test -Dgroups="ui"

# Run the UI headed, for debugging
./mvnw test -Dgroups="ui" -Dui.headless=false

# Generate and open the Allure report
./mvnw allure:serve
```

## Architecture

Framework code lives in `src/main/java`; `src/test/java` contains only
scenarios. A class in `main` is therefore self-evidently reusable, and the
boundary is enforced by the compiler rather than by convention.

| Package | Responsibility |
| --- | --- |
| `config` | Typed configuration, resolved system property → env var → properties file |
| `api` | Shared REST Assured spec, `ApiResponse<T>` envelope, Jackson mapper |
| `api.booker` | Restful Booker clients, models, cached token provider |
| `api.graphql` | GraphQL request/response types, client, `.graphql` loader |
| `ui` | Playwright lifecycle, ad blocking |
| `ui.pages` | Page objects and components |
| `junit` | `@ApiTest` / `@UiTest` / `@RetryOnFailure`, Page injection, failure capture |
| `data` | Test data factory |

Test wiring is by composition, not inheritance: `@UiTest` bundles `@Test`,
`@Tag("ui")` and the Playwright extension, and the `Page` arrives as a method
parameter. There are deliberately no `*TestBase` classes — the extension model is
the more idiomatic JUnit 5 design and leaves the single-superclass slot free.

## Configuration

Resolution order: **system property → environment variable → `.env` → built-in
default**.

| Key | `.env` name | Default | Required |
| --- | --- | --- | --- |
| `booker.base.url` | `BOOKER_BASE_URL` | — | yes |
| `booker.username` | `BOOKER_USERNAME` | — | yes |
| `booker.password` | `BOOKER_PASSWORD` | — | yes |
| `graphql.endpoint` | `GRAPHQL_ENDPOINT` | — | yes |
| `ui.base.url` | `UI_BASE_URL` | — | yes |
| `ui.browser` | `UI_BROWSER` | `chromium` | no |
| `ui.headless` | `UI_HEADLESS` | `true` | no |
| `http.timeout.ms` | `HTTP_TIMEOUT_MS` | `30000` | no |

Required keys deliberately have **no default**. A missing one fails fast naming
the key, rather than silently falling back to a hard-coded endpoint.

In CI the values come from GitHub Actions repository secrets, which the workflow
writes to `.env` before the test step.

## Test Strategy

33 scenario tests: 19 against Restful Booker, 7 against Hygraph GraphQL, 7
against DemoQA. A further 9 tests cover the framework itself (config resolution, the
retry extension, Playwright injection), for 32 in total.
The brief's minimums are 3, 5 and 2 — each area clears its minimum with margin,
without padding, because the brief asks for quality over quantity and weights
framework architecture at 40%.

What was prioritised, and why:

- **Isolation over convenience.** Every API test seeds its own booking and
  asserts only on that booking. There is no ordered create-read-update-delete
  chain, so one failure never cascades and parallel execution is safe — verified
  by running with methods fully concurrent, not just assumed.
- **No teardown, deliberately.** Tests do not delete what they create. The
  service resets periodically by design, cleanup is not a requirement, and an
  extra DELETE per test is load on a public service the brief asks us not to
  overload.
- **Real behaviour over assumed behaviour.** Every endpoint was probed before
  any assertion was written. Several behave unlike the obvious expectation (see
  below), and the tests assert what the services actually do.
- **Negative paths on both protocols.** Roughly a third of the tests exercise
  failure modes, which is where a framework's error handling is actually proven.
- **One shared auth call.** The token is fetched once per JVM. These are public
  services and the brief asks that they not be overloaded; parallelism is capped
  at 4 threads for the same reason.

## Challenges & Solutions

### The APIs do not behave the way you would guess

Every row here was verified with curl before the assertion was written.

| Scenario | Expected | Actual |
| --- | --- | --- |
| Auth with a bad password | 401 | **200** with `{"reason":"Bad credentials"}` |
| Successful `DELETE /booking/{id}` | 200 or 204 | **201 Created** |
| `PUT` without a token | 401 | **403** with plain-text `Forbidden` |
| `GET` a missing booking | JSON error | **404** with plain-text `Not Found` |
| GraphQL, non-existent id | errors array | **200**, `data.movie: null`, no `errors` key |
| GraphQL, malformed query | 200 + errors | **400**, `data: null` + `errors[]` |
| GraphQL, unknown field | 200 + errors | **400**, `errors[0].message` names the field |

The brief asks specifically whether GraphQL returns 200 with `data: null` or an
errors array. For Hygraph the answer is **both, depending on the failure class**:
a semantically valid query for a missing entity is 200 with a null field, while
parse and validation failures are 400. Because error and success bodies differ in
shape, `ApiResponse<T>` exposes `rawBody()` alongside the mapped `body()`, and
`GraphQlResponse` distinguishes an absent `data` key from a JSON-null one.

### Screenshot-on-failure that never fires

The obvious implementation — a JUnit `TestWatcher.testFailed()` hook that
screenshots the page — does not work. `testFailed` runs *after* `@AfterEach`, so
the browser context is already closed and there is nothing to capture. The
extension implements `AfterEachCallback` instead and checks
`getExecutionException().isPresent()`, capturing before teardown. It writes both
a full-page screenshot and a Playwright trace, which can be replayed
step-by-step in Trace Viewer.

### DemoQA's ads intercept clicks

Google ad frames on DemoQA shift the layout and swallow clicks on elements below
the fold — the dominant flake source on that site. Rather than scattering
`scrollIntoView` calls and retries through the page objects, each browser context
aborts requests to ad and analytics hosts at the network layer. Faster, and
deterministic.

### Playwright is not thread-safe

A `Playwright` instance cannot be shared across threads, so the factory holds one
per thread via `ThreadLocal`, with a fresh `BrowserContext` per test for
isolation. Because worker threads outlive individual tests, there is no
deterministic point at which those instances can be closed; they are released at
JVM exit, which also terminates the driver process. A shutdown hook would be
worse, since it would try to close thread-affine objects from the wrong thread.

### The date picker

DemoQA uses `react-datepicker`, where typing into the field is unreliable. The
page object drives the month and year `<select>` elements and then clicks the day
cell, whose class is zero-padded to three digits (`--015`) and needs a
`:not(--outside-month)` guard so the click cannot land on an adjacent month.

### Keeping endpoints and credentials out of the repository

Configuration lives in `.env` (gitignored) and in GitHub Actions secrets, never
in a tracked file. `ConfigLoader` resolves system property → environment variable
→ `.env` → default, and required keys have no default so a missing one fails fast
instead of silently using a stale hard-coded URL. The design and planning
documents in `docs/` refer to endpoints by key only.

To be straightforward about scope: the Restful Booker credentials are published
in the assignment brief and in that service's own public API documentation, and
all three targets are public demo services. Nothing here is a live secret. The
arrangement demonstrates the handling practice — no credentials in source, no
credentials in history, secrets injected at run time — on a codebase where the
cost of getting it wrong is zero.

### Flaky public services

Restful Booker runs on a free dyno that cold-starts slowly and resets its data
periodically. Tests never assume pre-existing data, and a narrowly-scoped
`@RetryOnFailure` is applied to two service-touching tests. It is deliberately
not applied suite-wide — a blanket retry turns reproducible defects into noise.

## Findings

Defects in the system under test, surfaced by the suite. Each is covered by a
test that asserts current behaviour and names the expected behaviour in its
title, so the suite stays green and the finding is still impossible to miss. If
any is fixed, its test goes red — which is when someone should hear about it.

| # | Severity | Finding |
| --- | --- | --- |
| 1 | Critical | `POST /booking` accepts unauthenticated writes (200, expected 401/403). Anyone can write to the booking store. |
| 2 | Critical | `GET /booking` enumerates every booking id with no token (200). |
| 3 | Critical | `GET /booking/{id}` returns guest first and last names with no token (200). Combined with #2, every guest record is readable by anyone. |
| 4 | Low | `DELETE /booking/{id}` answers a successful delete with `201 Created` rather than `200`/`204`. |
| 5 | Low | `POST /auth` reports bad credentials as `200` with `{"reason":"Bad credentials"}` instead of `401`. |

Findings 1-3 are documented behaviour of this service, so they are design
defects rather than regressions. Enforcement on `PUT`, `PATCH` and `DELETE` is
genuine: those verbs reject a forged token, not merely a missing one.

## What I Would Add With More Time

- **Contract testing** against the OpenAPI/GraphQL schemas, so a breaking change
  is caught as a schema diff rather than as a broken assertion.
- **A mock layer** (WireMock) so the suite can run offline and in CI without
  depending on third-party uptime. The brief permits mocking when a service is
  down; a recorded-cassette mode would make that automatic.
- **Cross-browser coverage.** The factory already selects browser by config, so
  this is a CI matrix, not a code change.
- **Multi-page scenarios.** The `Page` resolver is already keyed by
  qualifier/index, so `void t(@Actor("alice") Page a, @Actor("bob") Page b)`
  works without framework changes; nothing in this assignment needed it.
- **Allure history and trends** published to GitHub Pages, so flakiness is
  visible over time rather than per-run.
- **Visual regression** on the UI pages.
- **A test-data cleanup sweep** that deletes orphaned bookings left by
  interrupted runs.

## Test Report

![Test run](docs/test-run.png)

Allure results, Surefire reports, screenshots and traces are uploaded as
artifacts on every CI run, including failures.
````

- [ ] **Step 3: Verify every command in the README actually works**

Run each one in a clean checkout:

```bash
./mvnw clean test
./mvnw test -Dgroups="api"
./mvnw test -Dgroups="ui"
./mvnw allure:report
```

A README command that does not run is a documentation defect. Fix the README or
the build until all four succeed.

- [ ] **Step 4: Commit and push**

```bash
git add README.md docs/
git commit -m "Add README and test run report"
git push
```

---

## Final verification

- [ ] `./mvnw clean test` passes: 32 methods (23 scenario + 9 framework), 0 failures
- [ ] `./mvnw test -Dgroups="api"` runs 16 tagged methods / 19 executions
- [ ] `./mvnw test -Dgroups="ui"` runs 8 tagged methods
- [ ] A deliberately failed UI test produces a screenshot **and** a trace
- [ ] `./mvnw allure:report` generates a report with request/response attachments
- [ ] GitHub Actions run is green
- [ ] README commands all verified against a clean checkout
- [ ] No Claude/Anthropic attribution in any commit message
- [ ] `.env` is gitignored and absent from `git ls-files`
- [ ] `.env.example` is committed and contains placeholders only
- [ ] No endpoint or credential anywhere in history — verify with:
      `git log -p --all | grep -nEi '<your-booker-host>|<your-password>'` using the
      real values, and confirm zero matches. Run it from a shell whose history is
      not persisted, or clear the entry afterwards.
- [ ] `gh secret list` shows all five secrets registered
- [ ] CI logs never print a secret value (no `cat .env`, no `echo`)
- [ ] `git log --oneline` shows incremental, meaningful commits
