# Repository Guidelines

## Project Structure & Module Organization

This is a Java 8 Maven multi-module project. The root `pom.xml` aggregates `platform/`, whose modules are:

- `platform/platform-core`: public API contracts, core workflow code, persistence, REST adapters, and local mocks.
- `platform/platform-starter`: Spring Boot auto-configuration that depends on `platform-core`.

Production code lives under `src/main/java`; tests mirror package paths under `src/test/java`. Runtime resources are under `src/main/resources`, including SQLite schema material. Project requirements and design notes live in `doc/`; PowerShell utilities live in `scripts/`. There is currently no frontend or static asset module. Follow the nearest nested `AGENTS.md` for module-specific rules.

## Build, Test, and Development Commands

- `.\scripts\check-env.ps1`: verify the local Java and Maven environment.
- `mvn -q test`: compile and test the full reactor.
- `mvn -q -pl platform/platform-core -am test`: run focused core tests plus required upstream modules.
- `mvn -q -DskipTests package`: build module artifacts without executing tests.

No standalone entry point is committed; validate changes through module builds and tests.

## Coding Style & Naming Conventions

Use UTF-8, four-space indentation, Java 8 syntax, and Spring Boot 2.7 APIs. Do not use Java 9+ APIs, records, Lombok, or Jakarta/Spring Boot 3 dependencies. Packages start with `com.flowmind.platform`. Use PascalCase for classes, camelCase for methods and fields, and UPPER_SNAKE_CASE for enum constants. Name contracts `XxxDTO`, `XxxRequest`, `XxxResult`, or `XxxQuery`. Keep controllers as adapters; workflow rules belong in core services. No formatter is configured, so match surrounding code.

## Testing Guidelines

Tests use JUnit 5 and Spring Boot Test. Name test classes `*Test` and place them in the matching package. Add tests for every behavior or public contract change, including failure and concurrency cases when relevant. No numeric coverage threshold is configured; changed behavior must be meaningfully exercised. Run the focused module command before the full reactor test.

## Commit & Pull Request Guidelines

Recent history uses short conventional subjects such as `feat: add ...`, `test: verify ...`, and `docs: record ...`. Keep commits scoped to one logical change. Create feature branches from `develop` and open PRs back to `develop`. PRs should summarize scope, list validation commands and results, note cross-module impacts, link relevant issues, and include screenshots only for visible UI changes.

## Security & Repository Hygiene

Do not commit secrets, `target/`, SQLite database files, logs, or generated artifacts. Keep concrete business rules outside the workflow platform. Store attachment content through the file-storage SPI rather than SQLite.
