# Contributing Guide

Thanks for considering a contribution! This project welcomes issues, pull requests, and ideas.

> By contributing, you agree that your changes are licensed under the project’s license (Apache-2.0).

---

## Table of Contents
- [Code of Conduct](#code-of-conduct)
- [How to Propose Changes](#how-to-propose-changes)
- [Development Setup](#development-setup)
- [Style Guide](#style-guide)
- [Testing](#testing)
- [Pull Request Checklist](#pull-request-checklist)
- [Review & Merge Process](#review--merge-process)
- [Security](#security)
- [Contact](#contact)

---

## Code of Conduct
Please read and follow **CODE_OF_CONDUCT.md**. Be kind, constructive, and respectful.

---

## How to Propose Changes

### 1) Discuss first (recommended)
Open a **GitHub Issue** to describe a bug, proposal, or question. Provide context, use cases, and a minimal reproduction if applicable.

### 2) Fork & branch
Create a feature branch from the latest `main` of the upstream repository.

```
# fork the repository on GitHub, then:
git clone https://github.com/<your-username>/<repo>.git
cd <repo>
git remote add upstream https://github.com/<owner>/<repo>.git
git fetch upstream
git checkout -b feat/<short-description> upstream/main
# examples: feat/add-retry-logic, fix/npe-in-clock, docs/update-readme
```

### 3) Keep your branch up to date
```
git fetch upstream
git rebase upstream/main
# resolve conflicts if any, then:
git push --force-with-lease
```

### 4) Open a Pull Request
From your fork, open a PR to `main`. Use a clear title and description (see the checklist below).

---

## Development Setup

**Prerequisites**
- Java **21**
- Maven **3.9+**
- Git
- (Optional) IntelliJ IDEA

**Build & verify**
```
mvn -B -ntp clean verify
```

**Run a single test class**
```
mvn -B -ntp -Dtest=SomeClassTest test
```

**Skip tests (not recommended for PRs)**
```
mvn -B -ntp -DskipTests clean package
```

**Integration tests (if present)**  
Some tests may call external APIs (e.g., Alpaca). Set environment variables:

```
# Linux/macOS
export APCA_API_KEY_ID=xxxx
export APCA_API_SECRET_KEY=yyyy

# Windows PowerShell
$env:APCA_API_KEY_ID="xxxx"
$env:APCA_API_SECRET_KEY="yyyy"
```

Never commit secrets. Use a local `.env` and ensure it’s in `.gitignore`.

---

## Style Guide

### Commits — Conventional Commits
Use **Conventional Commits** for clear history and auto-changelogs:

```
<type>(optional scope): <short summary>

[optional body]

[optional footer(s)]
```

Common types: `feat`, `fix`, `docs`, `test`, `refactor`, `build`, `ci`, `chore`.

Examples:
- `feat(rest): add orders endpoint client`
- `fix(json): handle null Instant in parser`
- `test: add unit tests for AccountRestService`

### Java code style
- Target **Java 21**.
- Keep code small, cohesive, and well-named.
- Prefer immutability where practical.
- Validate inputs early; use `Objects.requireNonNull` where needed.
- Logging: do not log secrets; keep messages actionable.
- Public APIs: add Javadoc to public types/methods users will call.
- Tests: one logical concern per test; clear names; avoid network in unit tests (use mocks).

If formatting/lint plugins are configured (e.g., Spotless, Checkstyle, google-java-format), run them before committing:
```
mvn spotless:apply
mvn checkstyle:check
```

---

## Testing

### What we expect
- **Unit tests** for new logic and for bug fixes (to prevent regressions).
- **Integration tests** (if applicable) behind a profile or requiring explicit env vars.
- Keep tests fast and deterministic; avoid real network in unit tests—use mocks (e.g., Mockito).

### Run all tests
```
mvn -B -ntp verify
```

### Focused runs
```
# single class
mvn -B -ntp -Dtest=ClassNameTest test

# single method (Surefire 3.x)
mvn -B -ntp -Dtest=ClassNameTest#methodName test
```

---

## Pull Request Checklist

Before opening your PR, please ensure:

- [ ] **Small & focused**: one logical change per PR.
- [ ] **Linked issue** (if exists) and a clear description of *what* and *why*.
- [ ] **Tests**: added/updated unit tests; integration tests if needed.
- [ ] **Build is green** locally: `mvn -B -ntp clean verify`.
- [ ] **No secrets** in code, tests, or history.
- [ ] **Docs updated** (README, Javadoc, examples) when user-facing behavior changes.
- [ ] **Changelog note** if requested by maintainers (SemVer friendly).
- [ ] **Rebased** on latest `main` (no merge commits) and resolves cleanly.
- [ ] **PR title** follows Conventional Commits.

Good PR titles:
- `feat: add bars endpoint client`
- `fix: prevent NPE in clock service`
- `docs: improve getting started section`

---

## Review & Merge Process
- A maintainer will review for correctness, scope, tests, and style.
- You may be asked to adjust tests, improve naming/structure, or split large PRs.
- All CI checks must pass before merge.
- Maintainers merge using **squash or rebase** for a clean history.

---

## Security
If you find a security issue, **do not** open a public issue.  
Follow **SECURITY.md** for responsible disclosure.

---

## Contact
- Questions or ideas? Open a **Discussion** or an **Issue**.
- Thanks for helping improve this project! 🙌
