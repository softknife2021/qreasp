# Changelog

## 0.2.0

Release notes: `docs/release-notes/0.2.0.md`. The full list of findings and their status is in
`IMPROVEMENTS.md`.

### Breaking

- **Package and Maven group renamed from `com.restbusters` to `com.softknife`.** The dependency is now
  `com.softknife:qreasp`, and imports change from `com.restbusters.*` to `com.softknife.*`. Class names
  are unchanged. The project now lives at `softknife2021/qreasp`.
- **Java 21 is required** at build time and at runtime (class file version 65). Projects on Java 17
  must stay on `0.1.8`.
- **A request that gets no response is `ExecutionState.FAILED`**, not `SKIPPED`. Refused connections,
  timeouts and unknown hosts all land here. `SKIPPED` now means "not attempted".
- **`DriverManager` no longer sets an implicit wait.** `ActionExecutor` waits explicitly with each
  step's `timeoutInSeconds`. Code that drives the raw `WebDriver` and relied on the old 10-second
  implicit wait must wait explicitly.
- **`WebDriverHelper.createWebDriver` refuses an unknown browser name** instead of starting Chrome.
- **Removed `RestClientHelper.addHeader`, `addHeaders`, `removeHeader`.** They were deprecated and did
  nothing: each built a client and discarded it.
- **Removed `TemplateTransform.main`**, a demo entry point in library code.
- **Logback, TestNG and MockWebServer are no longer transitive dependencies.** Add your own logging
  backend and TestNG version. `GenericDataProvider` and `JsonDataProvider` still need TestNG on the
  test classpath.
- **Stricter validation** — each now throws `IllegalArgumentException` naming the problem:
  `TemplateManager` with a blank name *or* version, `GenericUtils.generateJsonPatch` with *either* input
  invalid, `GenericUtils.splitToMap` with a malformed segment, `GenericUtils.substituteVariables` with an
  empty template, `RBFileUtils.getFileOnClassPath` for a missing resource (was a `NullPointerException`),
  `RestClientHelper.addQueryParams` for a malformed URL, `JsonDataProviderHelper.initializeJsonData`
  for unparseable JSON (was a `NullPointerException`).
- **`TCBuildExecutor` reports FAILURE** when a build could not be triggered, was still running when the
  wait ran out, or when a task produced no results. These used to come back SUCCESS.

### Fixed

- Basic and Bearer credentials, cookies, and token-like headers and query parameters were logged in
  clear text on every request. They are now logged as `***`. Request bodies are logged at DEBUG, capped.
- A non-JSON request body (form data, XML, text) threw before the request was sent.
- `HEAD` and `OPTIONS` requests were sent as `GET`.
- Clients built with default headers wiped the request's own headers, including `Content-Type`.
  Defaults are now added only where the request does not set the header.
- `Content-Type` could be sent twice.
- `WebDriverState.withLocator` wrote locator keys the executor never read, so such states were refused.
- `RetryStrategy`, `timeoutInSeconds` and `pollingIntervalMs` were accepted and ignored. All three now
  apply. A step missing a required input is not retried.
- 23 of the 47 `ActionType`s threw "not implemented". All are implemented; `BrowserContext` gains
  facade methods for frames, windows, drag and drop, scrolling, JavaScript, uploads, waits and validations.
- An unclosed `{` in an OpenAPI path hung the JVM.
- `OpenApiV3Manager.getSwaggerApiResources` parsed each URL as if it were a spec and always returned
  an empty list.
- Request-body schemas nested deeper than `#/components/schemas/X` were not found; the example serializer
  was re-registered on swagger's global mapper on every call.
- Stash v2 calls sent `{workSpace}` literally; the basic-auth constructor never checked the password.
- Stash and TeamCity request templates were mutated per call, so one call's parameters leaked into the
  next and threads corrupted each other.
- `PerformanceTestUtil`: an empty delay range killed every virtual user, a timed-out iteration was
  reported as complete (now `truncated: true`), requests that threw vanished from the count, and the
  thread ramp used integer division.
- `GenericUtils.substituteVariables` never substituted the URL-encoded `%7Bname%7D` form.
- `getInstance(json)` on `FreeMarkerPayloadManager`, `PayloadManager` and `WireMockManager` ignored
  the argument after the first call.
- `Context` claimed to be thread-safe and was a plain `HashMap`.
- Leaked OkHttp responses (WireMock stub loading, TeamCity polling), an unclosed directory stream,
  a thread pool created per async TeamCity call and never shut down, and a parallel TeamCity pool shut
  down after the first build.
- Lazy singletons used `synchronized` accessors; they now use the holder idiom.
- Remaining `printStackTrace` / `System.out` calls replaced with logging.

### Publishing

- **Published to GitHub Packages only**: `https://maven.pkg.github.com/softknife2021/qreasp`. The Sonatype
  Central Portal plugin, the OSSRH repository (shut down by Sonatype in 2025), the nexus-staging plugin
  and signing were removed. `./gradlew publish` builds, tests and uploads; a `v*` tag does the same in
  GitHub Actions. Consumers need a token with `read:packages` (see the README).
- The package now carries sources and javadoc jars, declared explicitly with `withSourcesJar()` /
  `withJavadocJar()`.

### Added

- SpotBugs static analysis on every build (`build/reports/spotbugs/main.html`); the build fails on a
  new finding.
- CI and publish workflows under `.github/workflows`.
- Test tasks: `test` (no browser, no internet), `networkTest`, `browserTest`. Every test has a default
  timeout.
