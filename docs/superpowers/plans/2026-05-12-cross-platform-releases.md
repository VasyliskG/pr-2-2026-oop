# Cross-Platform Native Installer Releases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce native installers (.exe / .dmg / .deb) for Student Collab Platform via GitHub Actions triggered on git tags, with Supabase credentials and SSL cert bundled at build time.

**Architecture:** GitHub Actions matrix runs `mvn package -DskipTests` on three native runners (windows-latest, macos-latest, ubuntu-latest). Maven builds a platform-specific fat jar containing the correct JavaFX native libraries for each OS. `jpackage` wraps the fat jar with a bundled JRE 21 and creates a native installer. Supabase credentials and the SSL cert path are injected as JVM system properties via `--java-options` in jpackage — Spring Boot system properties override `application.yml` values at runtime. All three installers are uploaded to a GitHub Release automatically.

**Tech Stack:** Java 21, Maven 3, spring-boot-maven-plugin, jpackage (bundled in JDK 14+), GitHub Actions, Supabase PostgreSQL + SSL (prod-ca-2021.crt)

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `src/main/resources/application.yml` | Modify | SSL cert path: hardcoded local path → Spring property with local fallback |
| `.github/workflows/release.yml` | Create | Full CI/CD pipeline: matrix build + publish GitHub Release |

No `pom.xml` changes needed. Credentials stay as `${DB_URL}` etc. in `application.yml` (Spring resolves them at runtime from JVM system properties set by jpackage).

---

### Task 1: Fix SSL cert path in application.yml

**Files:**
- Modify: `src/main/resources/application.yml` (line with `sslrootcert`)

The current value `/home/g/.postgresql/root.crt` is a hardcoded local path. End-user machines will not have this file at this path. Replace with a Spring property that falls back to the same local path for development, and is overridden by jpackage at runtime for packaged builds.

- [ ] **Step 1: Update sslrootcert**

In `src/main/resources/application.yml`, find and replace:
```yaml
        sslrootcert: /home/g/.postgresql/root.crt
```
With:
```yaml
        sslrootcert: ${app.ssl.cert:${user.home}/.postgresql/root.crt}
```

- [ ] **Step 2: Verify local dev still works**

```bash
mvn spring-boot:run
```

Expected: App starts, connects to Supabase. `${user.home}/.postgresql/root.crt` resolves to `/home/g/.postgresql/root.crt` at runtime — no behavior change for local dev.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "config: use dynamic SSL cert path with local fallback"
```

---

### Task 2: Create GitHub Actions release workflow

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] **Step 1: Create the workflow file**

```bash
mkdir -p .github/workflows
```

Create `.github/workflows/release.yml` with this exact content:

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build-windows:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Extract version from tag
        id: version
        shell: bash
        run: echo "VERSION=${GITHUB_REF_NAME#v}" >> $GITHUB_OUTPUT

      - name: Build fat jar
        shell: bash
        run: mvn package -DskipTests -B

      - name: Prepare jpackage input
        shell: bash
        run: |
          mkdir -p target/app-input
          cp target/student-collab-platform-*.jar target/app-input/
          cp prod-ca-2021.crt target/app-input/

      - name: Create Windows installer
        shell: bash
        run: |
          JAR=$(ls target/app-input/*.jar | head -1 | xargs basename)
          jpackage \
            --input target/app-input \
            --main-jar "$JAR" \
            --name "StudentCollab" \
            --app-version "${{ steps.version.outputs.VERSION }}" \
            --vendor "UZHNU" \
            --java-options "--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" \
            --java-options "--add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED" \
            --java-options "--add-exports javafx.graphics/com.sun.glass.ui=ALL-UNNAMED" \
            --java-options "-Dspring.datasource.url=${{ secrets.DB_URL }}" \
            --java-options "-Dspring.datasource.username=${{ secrets.DB_USER }}" \
            --java-options "-Dspring.datasource.password=${{ secrets.DB_PASSWORD }}" \
            --java-options "-Dapp.ssl.cert=\$APPDIR/prod-ca-2021.crt" \
            --type exe \
            --dest target/dist

      - name: Upload Windows artifact
        uses: actions/upload-artifact@v4
        with:
          name: installer-windows
          path: target/dist/*.exe

  build-macos:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Extract version from tag
        id: version
        run: echo "VERSION=${GITHUB_REF_NAME#v}" >> $GITHUB_OUTPUT

      - name: Build fat jar
        run: mvn package -DskipTests -B

      - name: Prepare jpackage input
        run: |
          mkdir -p target/app-input
          cp target/student-collab-platform-*.jar target/app-input/
          cp prod-ca-2021.crt target/app-input/

      - name: Create macOS installer
        run: |
          JAR=$(ls target/app-input/*.jar | head -1 | xargs basename)
          jpackage \
            --input target/app-input \
            --main-jar "$JAR" \
            --name "StudentCollab" \
            --app-version "${{ steps.version.outputs.VERSION }}" \
            --vendor "UZHNU" \
            --java-options "--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" \
            --java-options "--add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED" \
            --java-options "--add-exports javafx.graphics/com.sun.glass.ui=ALL-UNNAMED" \
            --java-options "-Dspring.datasource.url=${{ secrets.DB_URL }}" \
            --java-options "-Dspring.datasource.username=${{ secrets.DB_USER }}" \
            --java-options "-Dspring.datasource.password=${{ secrets.DB_PASSWORD }}" \
            --java-options "-Dapp.ssl.cert=\$APPDIR/prod-ca-2021.crt" \
            --type dmg \
            --dest target/dist

      - name: Upload macOS artifact
        uses: actions/upload-artifact@v4
        with:
          name: installer-macos
          path: target/dist/*.dmg

  build-linux:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Extract version from tag
        id: version
        run: echo "VERSION=${GITHUB_REF_NAME#v}" >> $GITHUB_OUTPUT

      - name: Build fat jar
        run: mvn package -DskipTests -B

      - name: Prepare jpackage input
        run: |
          mkdir -p target/app-input
          cp target/student-collab-platform-*.jar target/app-input/
          cp prod-ca-2021.crt target/app-input/

      - name: Create Linux installer
        run: |
          JAR=$(ls target/app-input/*.jar | head -1 | xargs basename)
          jpackage \
            --input target/app-input \
            --main-jar "$JAR" \
            --name "StudentCollab" \
            --app-version "${{ steps.version.outputs.VERSION }}" \
            --vendor "UZHNU" \
            --java-options "--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" \
            --java-options "--add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED" \
            --java-options "--add-exports javafx.graphics/com.sun.glass.ui=ALL-UNNAMED" \
            --java-options "-Dspring.datasource.url=${{ secrets.DB_URL }}" \
            --java-options "-Dspring.datasource.username=${{ secrets.DB_USER }}" \
            --java-options "-Dspring.datasource.password=${{ secrets.DB_PASSWORD }}" \
            --java-options "-Dapp.ssl.cert=\$APPDIR/prod-ca-2021.crt" \
            --type deb \
            --dest target/dist

      - name: Upload Linux artifact
        uses: actions/upload-artifact@v4
        with:
          name: installer-linux
          path: target/dist/*.deb

  create-release:
    needs: [build-windows, build-macos, build-linux]
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - uses: actions/checkout@v4

      - name: Download all installers
        uses: actions/download-artifact@v4
        with:
          path: dist/
          merge-multiple: true

      - name: Publish GitHub Release
        run: |
          gh release create "$GITHUB_REF_NAME" \
            --title "Release $GITHUB_REF_NAME" \
            --generate-notes \
            dist/*
        env:
          GH_TOKEN: ${{ github.token }}
```

- [ ] **Step 2: Add GitHub Secrets (manual — complete before pushing first tag)**

In GitHub repository:

1. **Settings → Secrets and variables → Actions → New repository secret**
2. Add three secrets:

| Secret name | Value |
|-------------|-------|
| `DB_URL` | Full JDBC URL, e.g. `jdbc:postgresql://db.xxxx.supabase.co:6543/postgres?ssl=true&sslmode=verify-ca` |
| `DB_USER` | `collab_app` |
| `DB_PASSWORD` | Password for `collab_app` Supabase role |

- [ ] **Step 3: Commit the workflow**

```bash
git add .github/workflows/release.yml
git commit -m "ci: add cross-platform release workflow via jpackage"
```

---

### Task 3: Trigger and verify first release

- [ ] **Step 1: Push commits to remote**

```bash
git push origin main
```

- [ ] **Step 2: Tag and push**

```bash
git tag v1.0.0
git push origin v1.0.0
```

- [ ] **Step 3: Monitor Actions**

Open `https://github.com/<your-org>/<repo>/actions`

Expected timeline:
- 3 parallel jobs start immediately (~5–10 min each)
- `create-release` job starts after all 3 complete
- Total: ~10–15 min

If any job fails, check its logs — common issues:
- `jpackage: command not found` → verify `actions/setup-java` step ran (it adds jpackage to PATH)
- `student-collab-platform-*.jar: no such file` → Maven build failed; check `mvn package` step logs
- SSL errors in app at runtime → verify `DB_URL` secret includes `ssl=true&sslmode=verify-ca`

- [ ] **Step 4: Verify GitHub Release**

Open `https://github.com/<your-org>/<repo>/releases`

Expected: Release `v1.0.0` with three assets attached:
- `StudentCollab-1.0.0.exe` (Windows)
- `StudentCollab-1.0.0.dmg` (macOS)
- `studentcollab_1.0.0_amd64.deb` (Linux)

---

## Notes

- **macOS "unidentified developer" warning:** Without Apple code signing, macOS blocks the app by default. Users must go to **System Settings → Privacy & Security → Open Anyway**. This is expected without an Apple Developer certificate.
- **Releasing a new version:** Simply tag and push — `git tag v1.2.0 && git push origin v1.2.0`. The workflow handles everything else.
- **Installer size:** ~200–250 MB (bundled JRE 21). Reducible later with `jlink` custom runtime — out of scope for this plan.
