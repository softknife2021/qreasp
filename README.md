# QREASP
## Quality, Release and Automation Support

---

## What is QREASP?

**QREASP (Quality, Release and Automation Support)** is a **unified Java-based automation framework** for **Web UI (Selenium)** and **REST API test automation**.

It is designed for **enterprise-grade quality assurance**, providing:
- Advanced Selenium WebDriver wrapper logic
- A template-driven API testing framework
- Native integrations with **Jira**, **TeamCity**, **Swagger/OpenAPI**, and **Stash**
- Gradle-based builds suitable for CI/CD pipelines

QREASP helps teams reduce fragmentation, improve automation stability, and produce reliable quality signals for release decisions.

---

## Why QREASP?

Many teams maintain multiple automation frameworks that are:
- Hard to maintain
- Inconsistent across projects
- Poorly integrated into release pipelines

QREASP provides a **single, consistent automation foundation** that enables:
- Unified UI and API automation
- Reusable, standardized patterns
- Enterprise traceability and reporting
- CI/CD-ready execution from day one

---

## Core Capabilities

### Web UI Automation (Selenium)
- Selenium WebDriver wrappers to reduce flakiness
- Centralized waits, retries, and error handling
- Page Object best practices
- Cleaner and more readable test code

---

### API Test Automation (Java-Based)
- Robust and flexible API automation framework
- Template-driven request and assertion model
- Consistent structure for functional and regression tests
- Swagger/OpenAPI-aligned test generation and validation
- Built and executed using **Gradle**

---

### Integrations

| Tool | Purpose |
|------|---------|
| Jira | Automatic creation and updates of issues based on test results |
| TeamCity | CI/CD execution and reporting |
| Swagger / OpenAPI | API contract validation and test generation |
| Stash | Version control for test scripts and related assets |

---

### Quality & Release Support
- CI-friendly execution model
- Environment-aware testing (INT, Staging, Prod)
- Rich build, commit, and environment metadata
- Designed to support quality gates and release validation workflows

---

## Design Principles

- Java-first and enterprise-ready
- Framework over ad-hoc scripts
- Reusable, extensible, and maintainable
- CI/CD native
- Minimal configuration with sensible defaults

---

## Getting Started

### 1. Clone the Repository
Clone the repository to your local machine.

---

### 2. Prerequisites
- **Nothing but a shell.** The Gradle wrapper is in the repository, and the build downloads
  JDK 21 itself (Gradle toolchain + foojay resolver) when it is not installed.
- **Using the library** needs a **Java 21** runtime. Projects on Java 17 must stay on `0.1.8`.
- **Browser tests** need Chrome; the driver binary is fetched by WebDriverManager.

---

### 3. Build the Project

```bash
cd qreasp
./gradlew clean build
```

---

### 4. Run the Tests

```bash
./gradlew test          # unit tests: no browser, no internet
./gradlew networkTest   # tests that call public hosts (petstore.swagger.io, httpbin.org)
./gradlew browserTest   # WebDriver tests: headless Chrome and internet access
```

Every test has a default two-minute timeout, and every test task a fifteen-minute one, so a stuck
browser session fails instead of hanging the build.

---

### 5. Static Analysis

SpotBugs runs as part of `./gradlew build`. The report is written to
`build/reports/spotbugs/main.html`; deliberate exclusions, each with its reason, are in
`config/spotbugs/exclude.xml`.

---

### 6. Use It From Another Project

Published to GitHub Packages (`https://maven.pkg.github.com/softknife2021/qreasp`). GitHub Packages needs a
token with `read:packages` even for public packages:

```groovy
repositories {
    mavenCentral()
    maven {
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

To publish a new version: bump `version` in `build.gradle`, then either run
`./gradlew publish` with `gpr.user` / `gpr.key` in `~/.gradle/gradle.properties`, or push a tag
`v<version>` and let the "Publish to GitHub Packages" workflow do it. Full steps are in
`IMPROVEMENTS.md`, section "Publishing to GitHub Packages". Changes per release are in `CHANGELOG.md`.

---

### WebDriver behaviour worth knowing

- Each action waits up to its `timeoutInSeconds` for its element, polling every `pollingIntervalMs`.
  Drivers from `DriverManager` have no implicit wait, so the two never multiply.
- `RetryStrategy` is applied per action: `IMMEDIATE_RETRY` (3 attempts), `LINEAR_BACKOFF`
  (3 attempts, 1 s then 2 s apart), `EXPONENTIAL_BACKOFF` (5 attempts, 0.5 s doubling). A step missing
  a required input — a URL, an expected value, a file — fails once and is not retried.
- Every `ActionType` is implemented; a new constant without an implementation fails
  `ActionExecutorContractTest`.
