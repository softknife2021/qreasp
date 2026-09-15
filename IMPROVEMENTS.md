# qreasp — review findings and improvement plan

**Reviewed:** `master` at `8214e5d` (0.1.8), all of `src/main/java`, `build.gradle`, the test suite.
**Date:** 2026-09-14. Findings are ordered by what they cost a user of the library. Every item
names the file and line so it can be fixed without re-reading the review.

## How to use this file — for the agent doing the fixes

This is the work list. The review is done; nothing below has been fixed except the build changes
listed under "What changed in this pass".

- **Work in the order of section 7.** One pull request per numbered item (or per small group);
  do not bundle a design change (section 4) with a bug fix (section 2).
- **Every fix ships the test named under it**, in the same commit, and the test must fail before
  the fix. Where an item has no test named, write one that enters through the public API the
  way a user would (`BrowserContext`, `RestClientHelper`, `HttpRequestHelper`), not by calling a
  private method.
- **Do not change a public signature without a `@Deprecated` shim** for one release; this is a
  published library with consumers.
- **Run `./gradlew build` (which includes `spotbugsMain`) before calling anything done**, and
  read `build/reports/spotbugs/main.html` — a fix must not add a finding. Once section 7 step 3
  is complete, set `spotbugs.ignoreFailures = false` in `build.gradle`.
- **Keep the bug's line references** in the commit message so the reviewer can tick this file.
- When a finding turns out to be wrong, say so in the PR and strike it here; do not silently
  skip it.

## Status after the fix pass (2026-09-14, branch `improvements/review-fixes`)

Every item in section 2 is fixed, with the tests named below. Sections 3, 5 and 6 are done except
where marked. Section 4 is partly done; the parts left are design decisions, listed at the end.

**Evidence.** `./gradlew clean build` on Java 21 passes: 348 unit tests passed, 0 failed.
SpotBugs reports **0 findings**, down from 48, and now fails the build on any new one
(`ignoreFailures = false`). `./gradlew networkTest`: 18 passed, 0 failed. No Gradle deprecation
warnings remain.

`./gradlew browserTest` (headless Chrome, public sites): 19 passed, then `testSelectByValue` timed out
loading w3schools.com. That is the same page load that hung the baseline run before any change; the new
default timeout turned the hang into a failure after two minutes. The Chrome session did not recover,
so the run was stopped. The retry-timing browser tests did not run. Retry behaviour is covered by the
browserless `ActionExecutorContractTest`. That test should move to a page served locally.

| item | status | proven by |
|---|---|---|
| 2.1 credentials in the log | fixed | `RestClientRequestShapeTest.bearerTokenIsRedactedInTheRequestLog`, `headerRedactionIsSelective`, `queryParameterRedaction` |
| 2.2 non-JSON body threw | fixed | `nonJsonBodyIsSent` |
| 2.3 HEAD/OPTIONS sent as GET | fixed | `headIsSentAsHead`, `optionsIsSentAsOptions`, `everyDeclaredMethodIsSentAsItself` |
| 2.4 two locator schemas | fixed | `ActionExecutorContractTest.withLocatorProducesAStateTheExecutorAccepts` |
| 2.5 retry/timeout/polling ignored | fixed; implicit wait removed so it cannot multiply | `retryStrategyIsApplied`, `noRetryFailsOnFirstMiss`, `missingInputIsNotRetried`, `backoffDelays` |
| 2.6 unimplemented actions | fixed. **Correction:** 47 constants, 23 missing (not 48/24) | `everyActionTypeIsImplemented` |
| 2.7 infinite loop | fixed | `SwaggerPathParamsTest` |
| 2.8 Stash v2 / password | fixed, plus `resetWorkSpaceAndRepo` | `StashRequestIsolationTest` |
| 2.9 shared request templates | fixed (`HttpRequest.copyOf`) | `pagingParametersDoNotLeakBetweenCalls` |
| 2.10 FAILED vs SKIPPED | fixed | `refusedConnectionIsFailed` |
| 2.11 PerformanceTestUtil | fixed; requests that throw are now counted | `PerformanceTestUtilFixesTest` |
| 2.12 json patch `&&` | fixed | `GenericUtilsFixesTest.oneInvalidJsonIsRefused` |
| 2.13 encoded placeholders | fixed | `encodedPlaceholdersAreSubstituted` |
| 2.14 TemplateManager `&&` | fixed | `TemplateManagerValidationTest` |
| 2.15 singletons ignoring arguments | fixed for both payload managers and `WireMockManager` | `PayloadManagerInstanceTest`. **No test for `WireMockManager`'s stub replacement:** it shares port 8090 with five other test classes, and a test that swaps its stubs could break them mid-run |
| 2.16 `Context` not thread-safe | fixed | `ContextConcurrencyTest` |
| 2.17 no-op header methods | removed, and both internal callers | the build |
| 2.18 resource leaks | fixed | `TCBuildExecutorOutcomeTest` runs the closed paths; there is no leak detector |
| 2.19 smaller items | fixed | `OpenApiV3ManagerFixesTest`, `RBFileUtilsClasspathTest`, `WebDriverHelperTest`. **Not verified:** the Jira Cloud search endpoint, which needs a live Jira |

**Found while fixing, not in the original review — all fixed and tested:**

- Clients built with default headers wiped the request's own headers, because the interceptor called
  `Request.Builder.headers(...)`. Six clients had it. Proven by `clientDefaultsDoNotWipeRequestHeaders`.
- `OpenApiV3Manager.getSwaggerApiResources` parsed each URL as though it were a spec, so it always
  returned an empty list. Proven by `urlListIsFetched`.
- `TCBuildExecutor` reported SUCCESS for a build that could not be triggered, for a build still running
  when the wait ran out, and for a task with no builds. Proven by `TCBuildExecutorOutcomeTest`.
- The build's Javadoc task read raw Lombok sources, so any public signature using a generated builder
  broke it.

**Left, and why:**

- **Section 3.** No version catalog and no dependency locking, which only reorganise the build
  without fixing anything. JRJC, its Spring 5.3 dependency, and swagger-inflector are still in: each
  removal replaces a feature and deserves its own change. Jackson is pinned at 2.19.2, the version
  already resolving; 2.22.2 is the latest and a separate upgrade.
- **Section 4.** These change public API or need a decision:
  - turning the singletons into plain objects (they are now thread-safe, but still singletons);
  - the `RestClientHelper` builder API;
  - renaming the `integraton` package.
  Also not done: `FreeMarkerPayloadManager` keeps its own FreeMarker 2.3.30 configuration, because
  unifying the version could change rendered output.
- **Section 5.** The httpbin and petstore tests were moved to `networkTest`, not rewritten against
  MockWebServer.
- **Section 6.** Copyright headers were not touched.

**What changed in this pass (build only, no source changes):**

- Build moved to **Java 21** via a Gradle toolchain (`build.gradle`, `settings.gradle`). The JDK
  is auto-provisioned by the foojay resolver, so nobody has to install one. Version bumped to
  **0.2.0** because the published jar now needs a Java 21 runtime.
- **SpotBugs** static analysis wired in (`./gradlew spotbugsMain`, report at
  `build/reports/spotbugs/main.html`, exclusions in `config/spotbugs/exclude.xml`). It runs with
  `build`/`check`. Failures are ignored for now — the numbers below are the baseline.
- **GitHub Packages** publishing repository added, plus two workflows: `.github/workflows/ci.yml`
  (build, SpotBugs, tests on every push/PR) and `.github/workflows/publish-github-packages.yml`
  (publish on a `v*` tag). Instructions in section 8.
- `CLAUDE.md` corrected: it said "Java 11+ (Java 8 compatible)"; the code already used Java 9+ APIs
  (`Map.of` in `LocatorUtils.java:107`, `WebDriverState.java:136`) and Selenium 4.39 needs 11+.

Everything else in this file is a **to-do**, not done.

---

## 1. Consumers to warn before releasing 0.2.0

Any project still on **Java 17** cannot load a 0.2.0 jar compiled for 21
(`UnsupportedClassVersionError`, class file version 65). Such projects either stay on 0.1.8 or move to
Java 21 first. The release notes say so.

---

## 2. Bugs — wrong behaviour a user will hit

### 2.1 Credentials are written to the log on every request

`LoggingInterceptor.java:26` logs `request.headers()` at INFO. It is registered as a **network**
interceptor (`RestClientHelper.java:54`, `:66`, `:76`, `:113`, `:128`), which runs after the
application interceptors that add `Authorization` (`BasicAuthInterceptor.java:27`,
`BearerAuthInterceptor.java:26`). So every Basic and Bearer credential lands in the log in clear
text. `HttpRequestHelper.httpLogger` (`:75-80`) additionally logs the whole request body at INFO —
login payloads included.

**Fix:** redact `Authorization`, `Cookie`, `Set-Cookie` and any header named `*token*`/`*key*`
before logging; log bodies at DEBUG only and cap their length. Test: a request with a Bearer
token produces a log line containing `Authorization: ***` and not the token.

### 2.2 A non-JSON request body crashes the request before it is sent

`HttpRequestHelper.java:36-38` calls `httpLogger`, which parses the body as JSON and throws
`RuntimeException` on failure (`:79`). A form-encoded, XML or plain-text body — all of which
`RestClientHelper.buildRequest` supports through `contentType` — never reaches the server.

**Fix:** log the body as-is when it is not JSON. Test: `executeHttpRequest` with body `a=1&b=2`
returns a `COMPLETED` result.

### 2.3 `HEAD` and `OPTIONS` are silently sent as `GET`

`HttpMethods.java:13-14` accepts both, `RestClientHelper.convertToOkHttpRequest` validates them
(`:352`), and `buildRequest` (`:363-390`) has no branch for either, so OkHttp's default `GET`
goes out. A test that asserts a `HEAD` response has no body will pass for the wrong reason.

**Fix:** add `builder.head()` and `builder.method("OPTIONS", body)`. Test: MockWebServer records
the method.

### 2.4 Two incompatible locator-map schemas in one module

`LocatorUtils` reads `strategy`/`value` (`LocatorUtils.java:17-18`). `WebDriverState.withLocator`
writes `locatorType`/`locatorValue` (`WebDriverState.java:136`), and `getLocatorType`,
`getLocatorValue`, `getLocatorString` read those keys (`:170-179`). Consequences:

- A state built with `WebDriverState.create(driver).withAction(CLICK).withLocator(ID, "x")` fails
  `ActionExecutor.validateState` with "Valid locator is required" (`ActionExecutor.java:118`).
- A state built through `BrowserContext` logs `Executing action: CLICK with locator: null: null`
  (`ActionExecutor.java:39` → `getLocatorString`), which is what the test log shows today.
- `FormData.getLocatorType()` (`FormData.java:56`) upper-cases the strategy, so a map written with
  `"css"` throws `IllegalArgumentException` (`LocatorType` has `CSS_SELECTOR`).

**Fix:** one schema, one writer (`LocatorUtils.createLocatorMap`), and `WebDriverState` delegates
to `LocatorUtils` for reads. Test: `withLocator` followed by `executeAction` succeeds; the log
line names the locator.

### 2.5 `RetryStrategy`, `timeoutInSeconds` and `pollingIntervalMs` are accepted and ignored

`BrowserContext.withDefaultRetry` (`:57`) and `WebDriverState.retryStrategy` (`:98`) are never read
by `ActionExecutor` (grep: no `getRetryStrategy` outside the enum and the two declarations).
`timeoutInSeconds` is used only by the three `WAIT_FOR_*` actions; `CLICK`, `SEND_KEYS`,
`GET_TEXT`, `VALIDATE_TEXT` call `driver.findElement` directly (`ActionExecutor.java:406-408`) and
rely on the implicit wait set in `DriverManager.java:69`. `pollingIntervalMs` is read nowhere.
The README's "centralized waits, retries" is not true of the code.

**Fix:** either implement retry in `executeAction` (loop `maxAttempts`, sleep `baseDelayMs`,
exponential for `EXPONENTIAL_BACKOFF`) and use `WaitUtils` with the state's timeout in
`findElement`, or remove the fields. A field that is accepted and ignored is the worse option.
Test: an action against a selector that appears after 2 s passes with `timeoutInSeconds=5` and
fails with `1`.

### 2.6 `ActionType` advertises 48 actions; the executor implements 24

`ActionExecutor.performAction` (`:150-201`) dispatches 24 constants; the other 24 throw
"Action type not implemented" (`:200`). Missing: `WAIT_FOR_ELEMENT_INVISIBLE`,
`WAIT_FOR_TEXT_PRESENT`, `SWITCH_TO_FRAME`, `SWITCH_TO_WINDOW`, `CLOSE_WINDOW`, `MINIMIZE_WINDOW`,
`SET_WINDOW_SIZE`, `DRAG_AND_DROP`, `CLICK_AND_HOLD`, `RELEASE`, `SCROLL_UP/DOWN/TO_TOP/TO_BOTTOM`,
`HIGHLIGHT_ELEMENT`, `EXECUTE_JAVASCRIPT`, `UPLOAD_FILE`, `SELECT_BY_INDEX`, `SELECT_BY_VALUE`,
`VALIDATE_ATTRIBUTE`, `VALIDATE_ELEMENT_PRESENT`, `VALIDATE_ELEMENT_VISIBLE`,
`SWITCH_TO_DEFAULT_CONTENT`. `WaitUtils` already has the two missing waits (`:47`, `:55`).

**Fix:** implement or delete. Add a test that drives `ActionType.values()` through
`performAction` on a stub driver and asserts none throws "not implemented" — that keeps the enum
and the executor from drifting again.

### 2.7 Infinite loop on an unclosed path parameter

`SwaggerDescriptorHelper.getNormalizedPathParams` (`:79-95`): for a path such as
`/users/{id}/orders/{oid` the outer check passes (both braces present), the second `{` has no
`}`, `closeBracePos == -1` logs and does **not** advance `openBracePos`, and the
`do … while (openBracePos != -1)` loop never ends. A malformed spec hangs the JVM.

**Fix:** `break` (or advance) on `-1`. Test: that path returns `["id"]` and terminates.

### 2.8 Stash v2 calls use the v1 parameters

`StashRestClient.executeCall` (`:619-621`): the `API_V2` branch calls `setDefaultUrlParamsV1`, so
`workspace` is never substituted and every v2 request goes to a URL still containing
`{workspace}`. Also `resetWorkSpaceAndRepo` (`:652`) assigns `projectName` instead of its
argument, and the second constructor (`:481-486`) validates `userName` twice and `password` never.

### 2.9 Shared request objects are mutated per call

`StashRestClient.getCommitsInRangeV1` (`:521-526`), `getTagsV1`, `getFileContent`,
`getCommitByHash` and `TeamCityClient.getBuildById` (`:60-64`) mutate the `queryParams`/`urlParams`
maps of the single `HttpRequest` instance held in `stashRequests`/`tcRequests`. The second call
inherits the first call's parameters, and two threads corrupt each other. **Fix:** copy the
template request per call (`toBuilder()` or a fresh map).

### 2.10 A failed connection is reported as `SKIPPED`

`HttpRequestHelper.java:41-43`: an `IOException` (refused, timeout, DNS) sets
`ExecutionState.SKIPPED`. `ExecutionState` has no `FAILED`. Anything reading the state counts a
dead endpoint as "not run", and `HttpResultAnalyzer` only catches it because it also checks the
status code. **Fix:** add `FAILED`, use it, and make `analyzePerformanceResults` count it.

### 2.11 `PerformanceTestUtil` reports partial results as complete

`PerformanceTestUtil.java:66-68`: `latch.await(1, MINUTES)` return value is ignored, then
`shutdown()` without `awaitTermination`, so after a 60 s timeout the iteration is analysed while
threads are still writing to `iterationResults`. `nextLong(startTimeRange, endTimeRange)` (`:50`)
throws when the range is empty (`0, 0`), which kills every virtual user with only a log line.
`getThreadCountForIteration` (`:93-97`) uses integer division, so `start=1, end=10,
iterations=4` ramps 1, 4, 7, 10 but `start=1, end=3, iterations=4` gives 1, 1, 1, 3.

**Fix:** check the `await` result and mark the result `TRUNCATED`; validate the ranges; use
`Math.round` on a double step. Test over an empty range must not throw.

### 2.12 `generateJsonPatch` accepts one invalid JSON

`GenericUtils.java:107`: `if (!isJSONValid(json1) && !isJSONValid(json2))` throws only when
**both** are invalid; one invalid input reaches `mapper.readTree` and surfaces as a
`RuntimeException` with a Jackson message. Should be `||`.

### 2.13 `substituteVariables` never reaches its URL-encoded branch

`GenericUtils.java:45-52`: `matcher.groupCount()` is a property of the pattern (always 1 here),
not of whether anything matched, so the `%7B…%7D` branch is dead. `template.equalsIgnoreCase("")`
(`:41`) throws `NullPointerException` **from** a null template while the message says it must
not be null. Use `find()`/`reset()` or run both substitutions.

### 2.14 `TemplateManager` validation uses `&&` where `||` is meant

`TemplateManager.java:33` and `:66`: `isBlank(templateName) && isBlank(version)` rejects only when
both are blank; a blank version alone proceeds to `findTemplate` and fails with "not able to find
template" — a misleading message. Also `validateTemplateAndVersion` declares `throws
RecordNotFound` and never throws it.

### 2.15 Singletons that take arguments ignore them after the first call

`PayloadManager.getInstance(jsonPayloads)` (`:53-58`), `FreeMarkerPayloadManager.getInstance`
(`:43-48`), `WireMockManager.getInstance` (`:44-49`): the second call with different input
returns the instance built from the first. A test suite that loads two payload sets gets one.
`JsonDataProviderHelper` (`:19-102`) is a mutable singleton with a `HashMap` written from
`addJsonDataForMethod` — not usable from parallel TestNG methods. **Fix:** make these ordinary
objects; keep a static factory only where a process-wide instance is genuinely wanted.

### 2.16 `Context` is documented as thread-safe and is a `HashMap`

`CLAUDE.md` calls `Context` "thread-safe storage"; `Context.java:12` is a plain `HashMap`.
Use `ConcurrentHashMap` (and drop the four redundant `setValue` overloads — `Object` covers them).

### 2.17 Deprecated no-op methods are still called from inside the library

`RestClientHelper.addHeader/addHeaders/removeHeader` (`:214-247`) build a client and discard it.
`executeRequest` (`:290-293`) still calls `addHeaders`, and `TeamCityClient`'s constructor (`:48`)
calls `addHeader`. Harmless today only because `buildRequest` applies the headers again — delete
the three methods and the two calls.

### 2.18 Resource leaks

- `RBFileUtils.readFilesAsStringIntoMap` (`:92`): `Files.list` stream never closed.
- `WireMockManager.wireMockSetInitialState` (`:91`) and `waitForWireMockReady` (`:105`): OkHttp
  `Response` never closed — one leaked connection per stub.
- `TeamCityClient` and `StashRestClient` return raw `Response` objects to callers with no
  guidance to close them; `TCBuildExecutor.getBuildMetaData` (`:151-153`) reads the body and
  never closes.
- `TCBuildExecutor.executeBuildsAsync` (`:236`): `Executors.newCachedThreadPool()` is created per
  call and never shut down. `executeBuildsParallel` (`:186-199`) shuts the shared `ForkJoinPool`
  down inside the per-item `finally`, so the second item can hit a closed pool.

### 2.19 Smaller correctness items

- `OpenApiV3Manager.buildSchema` (`:425`, `:431`): `split("/")[3]` hard-codes the `$ref` depth;
  `keySet().toArray()[0]` throws on an empty `content`; `getSchema()` may be null.
- `OpenApiV3Manager.buildRequestBody` (`:413-414`) registers a new module on the global
  `Json.mapper()` on every call.
- `RestClientHelper.buildRequest` (`:369-375`): a `Content-Type` already in `headers` is sent
  twice. `buildTrustedHttpClient` (`:191`) uses `SSLContext.getInstance("SSL")`; use `"TLS"`.
- `RestClientHelper.getOAuth2Token` (`:415`) returns `null` on any failure, so a bad client secret
  and a network error look the same to the caller.
- `RBFileUtils.getFileAsFileFromClassPath` (`:79`): NPE when missing, and `getResource().getPath()`
  breaks for paths with spaces (`%20`) and inside jars.
- `TemplateLoaderHelper.bulkTemplateLoader` (`:78`): `contains(".ftl")` instead of `endsWith`;
  `FileUtils.readFileToString(file)` and `new String(Files.readAllBytes())` use the platform
  charset (`:38`, `:80`).
- `Constant.SPLIT_TO_MAP_VALIDATOR_REGEX` (`.*=.*;.*=.*`) requires **two** pairs, so a template
  with a single `key=value` in its metadata is rejected while the message says "if more than one".
- `JsonDataProviderHelper.initializeJsonData` (`:65-66`) throws `NullPointerException("Parameter
  Type cannot be null")` on a JSON parse error.
- `WebDriverHelper.createWebDriver` (`:43-46`): an unknown browser name silently becomes Chrome.
- `ActionExecutor.executeAction` (`:37`) dereferences `state` before `validateState`'s null check.
- `JiraHelper.getTransitionId` (`:143`) prints to `System.out`; `password` is kept as a field.
- `JiraHelper.searchJiraWithJQL` (`:154`) uses `searchJql`; Atlassian retired the `/search`
  endpoint on Jira Cloud in 2025 — verify JRJC 6.0.2 still works against Cloud before relying
  on it.

---

## 3. Dependencies and the build

| item | where | what to do |
|---|---|---|
| **Jackson 3 declared, Jackson 2 used** | `build.gradle` `tools.jackson.core:*:3.0.3`; every import is `com.fasterxml.jackson.*` | The declared artifacts are dead weight; the Jackson actually used (2.19.2) arrives transitively and unpinned. Declare `com.fasterxml.jackson.core:jackson-databind` + `jackson-dataformat-yaml` explicitly (or the BOM) and drop `tools.jackson`. |
| **logback as `implementation`** | `build.gradle` | A library must not force a logging backend on its consumers. Make it `testRuntimeOnly`; keep `slf4j-api`. |
| **TestNG as `implementation`** | `build.gradle` | It leaks into every consumer's compile classpath. `JsonDataProviderHelper` needs only the annotations at compile time → `compileOnly` (document that consumers bring their own TestNG). |
| **mockwebserver as `implementation`** | `build.gradle` | Test-only library shipped at runtime. `testImplementation` only. |
| **lombok declared twice** | `build.gradle` `implementation lombok` + freefair plugin | The plugin handles it; the `implementation` line ships Lombok's jar to consumers. Delete it. |
| **Spring 5.3.36 on the classpath** | via `jira-rest-java-client-core` 6.0.2; used directly in `SwaggerManager.java:14`, `OpenApiV3Manager.java:218` | Spring 5.3 is end-of-life. Replace `org.springframework.util.StringUtils` with `commons-lang3`, and check whether JRJC is worth its transitive tree (Spring, Guava, Fugue). |
| **swagger-inflector 2.0.14** | `build.gradle` | Used only for `ExampleBuilder`. Consider `io.swagger.codegen` or writing the example builder; inflector drags in Jersey/JAX-RS. |
| **`subprojects.each { childJars }` and the fat-jar `from zipTree`** | `build.gradle` | There are no subprojects. The block is dead and the `exclude META-INF/*.SF` lines exist for a shading that never happens. Delete. |
| **OSSRH `s01.oss.sonatype.org` repository** | `build.gradle` publishing | Sonatype shut OSSRH down in 2025; the URL will 404. Keep the Portal plugin (`sonatypePublish`) or GitHub Packages (section 8), delete the OSSRH block and the `nexus-staging` plugin. |
| **`model { tasks.publish … }` block** | `build.gradle` | Rule-based model DSL is removed in Gradle 9 (this is the deprecation the build prints). Replace with `tasks.named('publish') { dependsOn build }`. |
| **No dependency locking / version catalog** | — | Add `gradle/libs.versions.toml` and `./gradlew dependencies --write-locks` so a transitive bump cannot change the verdict of a test silently. |
| **Javadoc with `Xdoclint:none`** | `build.gradle` | Fine for now; the public API has almost no Javadoc, so consider a `-Xdoclint:missing` pass on the `webdriver.facade` and `rest.client` packages that consumers actually call. |

---

## 4. Design

1. **Two copies of the OpenAPI walker.** `SwaggerManager.buildSwaggerResources` (`:48-87`) and
   `OpenApiV3Manager.buildSwaggerResources` (`:267-308`) are the same 40 lines; the five
   `if (pathItem.getX() != null)` blocks are themselves one loop over
   `pathItem.readOperationsMap()`. One class, one loop.
2. **Four FreeMarker configurations, three version constants.** `TemplateLoader`,
   `BulkTemplateLoader`, `TemplateLoaderHelper.getFreeMarkerConfig`, `PayloadManager`
   (`VERSION_2_3_23`, `2_3_30`, `2_3_32`). One factory, one version, and `TemplateLoader` and
   `BulkTemplateLoader` are the same class with a different default directory.
3. **Test scaffolding shipped in `main`.** `TemplateTransform` has a `main()` and hard-codes
   `src/test/resources`; `TemplateLoader` defaults to `src/test/resources/`; `WireMockManager`
   hard-codes port 8090. Move to `src/test`, or make the paths and port constructor arguments.
4. **Eleven singletons.** `RestClientHelper`, `GlobalResourceManager`, `PayloadManager`,
   `FreeMarkerPayloadManager`, `JiraHelper`, `SwaggerManager`, `OpenApiV3Manager`,
   `SwaggerDescriptorHelper` (which also has a public constructor), `WireMockManager`,
   `JsonDataProviderHelper`, `RBFileUtils` (a singleton whose public API is all static). They make
   the library impossible to use for two targets in one JVM and force every test suite to run
   serially. Only `GlobalResourceManager` has a claim to being process-wide.
5. **`RestClientHelper` has eleven `build*Client` methods** that differ by which interceptors they
   add. One builder — `client().withBasicAuth(u, p).withHeaders(map).withLogging().build()` —
   replaces them and removes the `Map<String,String> headers = new HashMap<>()` empties passed
   around (`:156`, `:161`).
6. **The `integraton` package.** Documented as intentional legacy. A rename with a major version
   bump and a one-release `@Deprecated` shim is cheap; leaving a typo in the public API forever
   is not.

---

## 5. Tests

- **First: add timeouts.** One stuck chromedriver session hung the whole baseline run for over four
  minutes (section 9). Add `@Test(timeOut = …)` to the browser tests, or a TestNG suite-level
  timeout, and `test { timeout = Duration.ofMinutes(15) }` in `build.gradle`. Do this before
  anything else in this section.
- **The CI workflow runs every test, browser suites included.** `.github/workflows/ci.yml` runs
  a plain `./gradlew test`, even though its header says "tests that need no browser or network".
  Until the TestNG groups below exist, CI will hit Chrome, petstore.swagger.io and httpbin.org.
  Once the `unit` group exists, restrict that step to it.
- `./gradlew test` on Java 17 (baseline, before this change): see section 9 for the numbers.
- **Tests that need the internet:** `TestSwaggerManager` (petstore.swagger.io),
  `TestRestHelper` (httpbin.org). **Tests that need a local service:** `StashRestClientTest` and
  `TestRestHelper.getOAuth2Token` expect `localhost:8090`. **Tests that need Chrome:**
  `BrowserContextTest`, `BrowserContextAdditionalTest`, `BrowserContextSimpleTest`,
  `WebDriverTest` (headless, via WebDriverManager). None of these is tagged, so `./gradlew test`
  on a machine without Chrome or network fails for reasons unrelated to the code.
  **Fix:** TestNG groups `unit` / `integration` / `browser`; `test` runs `unit` by default; CI
  runs `browser` in a job with Chrome installed; replace httpbin/petstore with `MockWebServer`
  fixtures (already a test dependency).
- **What is not tested:** `RestClientHelper.buildRequest` per method (would have caught 2.3);
  `LoggingInterceptor` redaction (2.1); `HttpRequestHelper` with a non-JSON body (2.2);
  `SwaggerDescriptorHelper.getNormalizedPathParams` with a malformed path (2.7);
  `WebDriverState.withLocator` → `ActionExecutor` (2.4); any `RetryStrategy` behaviour (2.5).
- `ExecutionResultTest` and `WebDriverStateTest` are listed as browser tests by grep only
  because they contain a URL string; they are pure unit tests. Fine.
- The pending working-tree change (`TestSwaggerManager.java:27`, `public class    TestSwaggerManager`)
  is a stray whitespace edit; revert it.

---

## 6. Repository hygiene

- Seven Markdown files at the root (`ALL_DELIVERABLES_SUMMARY.md`, `FINAL_TEST_SUMMARY.md`,
  `RETRY_TEST_FIX.md`, `WEBDRIVER_COMPARISON_GUIDE.md`, `WEBDRIVER_EXAMPLES_README.md`,
  `WEBDRIVER_RAW_VS_WRAPPER_COMPARISON.md`, `CHANGELOG-feature-rest-refactor.md`) are session
  notes, not documentation. Fold anything still true into `README.md`/a `docs/` folder and
  delete the rest. `src/main/java/com/softknife/rest/REST_CLIENT.md` belongs in `docs/`.
- Copyright headers say "Last modified 6/5/21" on files edited in 2025. Drop the headers or let
  git carry the dates.
- `README.md` promises "centralized waits, retries, and error handling"; see 2.5 before shipping
  that sentence.
- No `CHANGELOG.md`. Start one at 0.2.0 with the Java 21 note.

---

## 7. Suggested order of work

| # | item | why first |
|---|---|---|
| 1 | 2.1 credential logging | a secret in every log line of every consumer |
| 2 | 2.2, 2.3, 2.10 HTTP correctness | wrong results on ordinary requests |
| 3 | 2.4, 2.5, 2.6 WebDriver contract | the module's advertised behaviour does not exist |
| 4 | section 3 dependency scopes (logback, TestNG, mockwebserver, Jackson) | every consumer pays for these; a one-line change each |
| 5 | 2.7, 2.8, 2.9 integrations | hang / wrong URL / cross-call state |
| 6 | section 5 test groups + MockWebServer | makes CI green without Chrome or internet |
| 7 | 2.11–2.19 | ordinary bug backlog |
| 8 | section 4 design | after the bugs, not instead of them |

Flip `spotbugs.ignoreFailures` to `false` once step 3 is done and the CORRECTNESS/SECURITY
categories in `build/reports/spotbugs/main.html` are empty; from then on the gate holds them.

---

## 8. Publishing to GitHub Packages

The library can be published to this repository's own Maven registry so other projects depend on
it as `com.softknife:qreasp:<version>` without going through Maven Central.

### One-time: nothing on the repository side

GitHub Packages is enabled for every repository. The registry URL is
`https://maven.pkg.github.com/softknife2021/qreasp` (already configured in `build.gradle` as the
`GitHubPackages` repository).

### Publish from GitHub Actions (recommended)

The Release workflow (`.github/workflows/publish-github-packages.yml`) runs on any tag starting
with `v`. It uses the `GITHUB_TOKEN` that Actions provides, so there is no secret to create. It does
four things, in order, and stops at the first failure:

1. Checks that the tag matches `version` in `build.gradle`, and that `docs/release-notes/<version>.md`
   exists.
2. Runs `./gradlew publish`, which builds, runs the unit tests and SpotBugs, and then uploads.
3. Creates the GitHub Release, using the release notes as its text and attaching the three jars.
4. Uploads the test and SpotBugs reports as a workflow artifact.

```bash
# bump `version` in build.gradle, add docs/release-notes/<version>.md, commit, then:
git tag v0.2.0
git push origin v0.2.0
```

The package then appears under the repository's **Packages** entry, and the release under **Releases**.

### Publish from a laptop

1. Create a classic personal access token with the `write:packages` scope (it implies `repo`).
2. Put it in `~/.gradle/gradle.properties` (never in the repository):

   ```properties
   gpr.user=<your-github-username>
   gpr.key=<the token>
   ```

3. Run:

   ```bash
   ./gradlew publishAllPublicationsToGitHubPackagesRepository
   ```

`build.gradle` reads `gpr.user`/`gpr.key`, falling back to the `GITHUB_ACTOR`/`GITHUB_TOKEN`
environment variables, so the same command works locally and in CI.

### Consume it from another project

GitHub Packages requires authentication even for public packages, so the consumer needs a token
with `read:packages`.

Gradle (`build.gradle`):

```groovy
repositories {
    mavenCentral()
    maven {
        name = 'GitHubPackages'
        url = uri('https://maven.pkg.github.com/softknife2021/qreasp')
        credentials {
            username = findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
            password = findProperty('gpr.key') ?: System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    testImplementation 'com.softknife:qreasp:0.2.0'
}
```

Maven (`~/.m2/settings.xml` gets the `<server id="github">` credentials; the `pom.xml` gets the
repository):

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/softknife2021/qreasp</url>
</repository>
```

In a consumer's GitHub Actions workflow the built-in `GITHUB_TOKEN` can read packages from
repositories in the same organisation; pass it as `GITHUB_TOKEN` to the Gradle step.

### Notes

- **GitHub Packages is the only publishing target** (owner decision, 2026-09-14). The Sonatype
  Central Portal plugin, the OSSRH repository, nexus-staging and signing are gone from the build.
  `./gradlew publish` builds, runs the unit tests and SpotBugs, then uploads.
- A version can be published to GitHub Packages only once. Re-publishing `0.2.0` fails; bump
  the version first.
- The package carries the main jar, `-sources.jar` and `-javadoc.jar` (declared with
  `withSourcesJar()` / `withJavadocJar()`), and a POM listing runtime dependencies only.
- The workflow runs on a tag push. It publishes whatever version `build.gradle` says, so bump the
  version and tag with the same number.

---

## 9. Evidence from this pass (2026-09-14)

### Java 21 build

`./gradlew clean build -x test spotbugsMain` — **BUILD SUCCESSFUL** on the toolchain-provisioned
JDK 21; `javap` on the compiled classes reports `major version: 65` (Java 21). The only source
warning is `TestJiraHelper.java:57-58` using `new Long(long)`, deprecated for removal — replace
with `Long.valueOf`.

Gradle deprecations printed with `--warning-mode all`, all in `build.gradle` and all removed in
Gradle 10: space-assignment syntax for `url`, `group`, `description`, `required` (write `url =
...`), and the `model { }` block noted in section 3.

### SpotBugs baseline (`build/reports/spotbugs/main.html`)

48 findings, all medium confidence, after excluding Lombok accessor exposure and the deliberate
trust-all client (`config/spotbugs/exclude.xml`):

| category | count | what it is |
|---|---|---|
| SECURITY | 24 | `USO_UNSAFE_*_SYNCHRONIZATION` — the twelve `synchronized static getInstance()` methods; goes away with section 4 item 4 |
| PERFORMANCE | 7 | unread fields (`TeamCityClient.authToken`, `PayloadManager.payloadTemplates`, `FreeMarkerPayloadManager.payloadTemplates`), instance constants that should be `static` |
| BAD_PRACTICE | 6 | constructors that throw (`StashRestClient`, `TeamCityClient`, `WireMockManager`), `TCBuildExecutor.java:240` ignoring `submit()`'s `Future`, `GenericUtils.RegexMatcher` naming |
| MALICIOUS_CODE | 5 | `getInstance()` exposing mutable singletons |
| STYLE | 4 | `RestClientHelper.addQueryParams:308` possible null from `HttpUrl.parse`; `ActionExecutor.validateState:104` null check after dereference (2.19); `RBFileUtils:112` no-op `forEach`; `TCHelper:34` useless object |
| CORRECTNESS | 2 | `PerformanceTestUtil.java:71` ignores `CountDownLatch.await` result (2.11); `SwaggerDescriptorHelper:26` singleton with a public constructor |

Two of these the hand review had not listed and are now part of the backlog:
`RestClientHelper.addQueryParams` (`:308`) dereferences `HttpUrl.parse(url)` which returns null for
a malformed URL, and `TCBuildExecutor.executeBuildsAsync` (`:240`) drops the `Future`, so an
exception inside the task is never seen.

### Tests

**Baseline on Java 17, before any change:** 329 tests passed, then
`BrowserContextAdditionalTest.testSelectByValue` hung on a chromedriver
`HttpTimeoutException` for over four minutes and had to be killed. There is no per-test timeout
(`@Test(timeOut=…)`) and no Gradle `test { timeout }`, so one stuck browser session blocks the
whole build. Add both (section 5).

**On Java 21, non-browser packages only** (`data`, `http`, `integration`, `rest`, `util`,
`webdriver.enums/models/utils`): **313 passed, 0 failed, BUILD SUCCESSFUL** in 44 s. The browser
suites (`BrowserContextTest`, `BrowserContextAdditionalTest`, `BrowserContextSimpleTest`,
`WebDriverTest`) were not re-run on 21 because of the hang above; run them once a per-test
timeout is in place.
