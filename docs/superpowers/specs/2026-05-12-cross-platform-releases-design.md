# Cross-Platform Native Installer Releases

**Date:** 2026-05-12
**Status:** Approved
**Scope:** GitHub Actions CI/CD pipeline producing native installers for Windows, macOS, Linux

---

## Overview

Student Collab Platform (JavaFX 21 + Spring Boot 3.3.5, Maven) requires native installers for distribution to end users who do not have Java installed. Releases are triggered by git tags (`v*`) and published automatically to GitHub Releases.

---

## Architecture

```
git tag v1.0.0 → push tag
        ↓
GitHub Actions: release.yml (trigger: on push tags v*)
        ↓ matrix: 3 parallel jobs
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  windows-latest  │  │  macos-latest   │  │  ubuntu-latest  │
│  mvn package     │  │  mvn package    │  │  mvn package    │
│  jpackage → .exe │  │  jpackage → .dmg│  │  jpackage → .deb│
│  upload-artifact │  │  upload-artifact│  │  upload-artifact│
└────────┬─────────┘  └────────┬────────┘  └────────┬────────┘
         └────────────────────-┼────────────────────-┘
                               ↓
                    create-release job
                    gh release create v1.0.0
                    attach all 3 installers
```

---

## Components

### 1. GitHub Actions Workflow (`.github/workflows/release.yml`)

**Trigger:** `on: push: tags: ['v*']`

**Matrix jobs** — one per platform:

| Job | Runner | jpackage `--type` | Output filename |
|-----|--------|-------------------|-----------------|
| `build-windows` | `windows-latest` | `exe` | `StudentCollab-1.0.0.exe` |
| `build-macos` | `macos-latest` | `dmg` | `StudentCollab-1.0.0.dmg` |
| `build-linux` | `ubuntu-latest` | `deb` | `studentcollab_1.0.0_amd64.deb` |

**Each job steps:**
1. `actions/checkout`
2. `actions/setup-java` — Temurin 21
3. `mvn package -DskipTests` with Maven properties from GitHub Secrets
4. Extract version from tag (`v1.0.0` → `1.0.0`)
5. `jpackage` with platform-specific `--type`
6. `actions/upload-artifact`

**Final job** (`create-release`, needs all 3 build jobs):
1. `actions/download-artifact` (all 3)
2. `gh release create $TAG` with all artifacts attached

### 2. jpackage Configuration

Shared arguments across all platforms:

```bash
jpackage \
  --input target/ \
  --main-jar student-collab-platform-*.jar \
  --name "StudentCollab" \
  --app-version "$VERSION" \
  --vendor "UZHNU" \
  --java-options "--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" \
  --java-options "--add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED" \
  --java-options "--add-exports javafx.graphics/com.sun.glass.ui=ALL-UNNAMED" \
  --type <exe|dmg|deb>
```

JavaFX native libraries are included automatically: Maven resolves platform-specific JavaFX classifiers (`linux`, `win`, `mac`) at build time and repackages them into the fat jar. No separate JavaFX SDK download needed.

### 3. Credentials — Maven Resource Filtering

Supabase production credentials are baked into the jar at build time via Maven filtering. Values come from GitHub Actions Secrets and are never stored in git.

**`application.yml` placeholders:**
```yaml
spring:
  datasource:
    url: @DB_URL@
    username: @DB_USERNAME@
    password: @DB_PASSWORD@
```

**`pom.xml` resource filtering** (added to `<build>`):
```xml
<resources>
  <resource>
    <directory>src/main/resources</directory>
    <filtering>true</filtering>
  </resource>
</resources>
```

**Maven invocation in CI:**
```bash
mvn package -DskipTests \
  -DDB_URL=${{ secrets.DB_URL }} \
  -DDB_USERNAME=${{ secrets.DB_USERNAME }} \
  -DDB_PASSWORD=${{ secrets.DB_PASSWORD }}
```

**Local development:** unaffected. Spring Boot reads `.env` at runtime via existing mechanism. Maven filtering only activates during `mvn package`.

### 4. GitHub Secrets Setup (one-time, manual)

```
Repo → Settings → Secrets and variables → Actions:
  DB_URL       = jdbc:postgresql://<supabase-host>:5432/postgres?ssl=...
  DB_USERNAME  = collab_app
  DB_PASSWORD  = <password>
```

---

## Data Flow

```
Developer pushes tag
    → GitHub Actions triggers release.yml
    → 3 parallel jobs (matrix)
        → each: checkout + setup-java-21 + mvn-package (credentials injected) + jpackage
        → each: upload installer as artifact
    → create-release job
        → download all 3 artifacts
        → create GitHub Release with tag + 3 installers attached
End user downloads installer for their platform
    → installs (bundled JRE 21 + fat jar with Supabase credentials)
    → runs app → connects to Supabase cloud DB
```

---

## Files Changed

| File | Action | Description |
|------|--------|-------------|
| `.github/workflows/release.yml` | Create | Main CI/CD workflow |
| `src/main/resources/application.yml` | Modify | Replace `${VAR}` with `@VAR@` for Maven filtering |
| `pom.xml` | Modify | Add `<resources>` block enabling filtering |

---

## Constraints and Known Limitations

- **macOS code signing:** Without Apple Developer certificate, macOS shows "unknown developer" warning. Users must manually allow in System Settings → Security. Signing can be added later via `--mac-sign` + certificate secret.
- **Installer size:** ~200–250 MB (bundled JRE 21). Can be reduced later with jlink custom runtime.
- **Windows MSI vs EXE:** Using `exe` (NSIS-based). Switch to `msi` if enterprise deployment needed.
- **ARM macOS:** `macos-latest` runner is x86_64. Apple Silicon users run via Rosetta 2. Native ARM build requires separate `macos-latest-xlarge` arm runner (paid).

---

## Out of Scope

- Auto-update mechanism
- Code signing / notarization
- ARM64 Linux / macOS builds
- Windows Store / Mac App Store distribution
