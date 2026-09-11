# GitHub setup, first commit, and rolling ARM release

This project includes `.github/workflows/android-latest.yml`.

On every pull request to `main`, GitHub Actions runs unit tests, Android lint, builds the app, and verifies that Gradle produced exactly two APKs:

- `arm64-v8a` — ARMv8 / 64-bit Android
- `armeabi-v7a` — ARMv7 / 32-bit Android

On every successful push to `main`, a second protected job rebuilds the same commit and replaces the single rolling GitHub Release/tag named **`latest`**. No x86, x86_64, or universal APK is published.

> The current app does not yet ship native OpenCV/TFLite libraries, so most Kotlin/Java bytecode is ABI-independent. ABI splitting is nevertheless configured now so future native vision libraries cannot accidentally create x86/universal release artifacts.

## 1. Requirements on your computer

Install:

- Git
- JDK 17
- Android Studio (recommended) or Android SDK command-line tools
- `curl` or `wget` and `unzip` if you use the included lightweight `./gradlew` bootstrap
- Optional: GitHub CLI (`gh`) if you want to create the remote repository from the terminal

The project uses Android Gradle Plugin 9.4.0, Gradle 9.6.0, JDK 17, compile SDK 37, and Build Tools 36.0.0.

## 2. Unpack the downloaded source archive

Linux/macOS:

```bash
mkdir -p ~/src/coin-forensics
cd ~/src/coin-forensics
unzip ~/Downloads/CoinForensics-v0.4-source.zip
cd CoinForensics-v0.4
```

If the ZIP extracts one additional parent directory, `cd` into the directory containing `settings.gradle.kts`.

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force "$HOME\src\coin-forensics" | Out-Null
Set-Location "$HOME\src\coin-forensics"
Expand-Archive "$HOME\Downloads\CoinForensics-v0.4-source.zip" -DestinationPath .
Set-Location .\CoinForensics-v0.4
```

Confirm you are in the project root:

```bash
ls settings.gradle.kts app/build.gradle.kts .github/workflows/android-latest.yml
```

## 3. Test locally before the first commit

Make the helper scripts executable on Linux/macOS:

```bash
chmod +x gradlew scripts/verify-abi-apks.sh
```

Then run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./scripts/verify-abi-apks.sh app/build/outputs/apk/debug dist
```

The expected final files are:

```text
dist/CoinForensics-arm64-v8a.apk
dist/CoinForensics-armeabi-v7a.apk
dist/SHA256SUMS.txt
```

There should be no x86, x86_64, or universal APK.

If you use Android Studio instead, open the directory containing `settings.gradle.kts`, allow Gradle sync to finish, then run the same Gradle tasks from the terminal or Gradle tool window.

## 4. Create the local Git repository

From the project root:

```bash
git init
git branch -M main
git config user.name "YOUR NAME"
git config user.email "YOUR_GITHUB_EMAIL@example.com"
```

Check what will be committed:

```bash
git status
git add .
git status
```

Create the first commit:

```bash
git commit -m "Initial Coin Forensics Android app with ARM CI"
```

## 5A. Create the GitHub remote using the GitHub website

1. Sign in to GitHub.
2. Choose **New repository**.
3. Give it a name, for example `coin-forensics-android`.
4. Choose Public or Private.
5. **Do not** initialize it with README, `.gitignore`, or license because the local project already contains files.
6. Create the repository.
7. Copy the repository HTTPS or SSH URL.

Add it locally. HTTPS example:

```bash
git remote add origin https://github.com/YOUR_GITHUB_USER/coin-forensics-android.git
git remote -v
git push -u origin main
```

SSH example:

```bash
git remote add origin git@github.com:YOUR_GITHUB_USER/coin-forensics-android.git
git remote -v
git push -u origin main
```

## 5B. Or create the remote with GitHub CLI

Authenticate once:

```bash
gh auth login
```

From the project root, create a private repository and push immediately:

```bash
gh repo create coin-forensics-android --private --source=. --remote=origin --push
```

For a public repository use `--public` instead of `--private`.

## 6. What happens after the first push

Open **GitHub → repository → Actions**.

The `Android CI and Latest ARM APKs` workflow will:

1. Check out the commit.
2. Install JDK 17.
3. Install Android SDK platform 37 and Build Tools 36.0.0.
4. Use Gradle 9.6.0.
5. Run `testDebugUnitTest`.
6. Run `lintDebug`.
7. Build `assembleDebug`.
8. Fail if anything other than the two requested ARM APK variants is produced.
9. Rebuild the exact verified commit in the release job.
10. Replace the rolling `latest` tag/release and attach only the two ARM APKs plus checksums.

The release job receives `contents: write`; the pull-request validation job receives only `contents: read`.

## 7. Download the latest build

After Actions succeeds, open:

**Repository → Releases → Coin Forensics — Latest ARM Build**

Choose:

- Modern 64-bit Android phone/tablet: `CoinForensics-arm64-v8a.apk`
- Older 32-bit ARM Android device: `CoinForensics-armeabi-v7a.apk`

The same release contains `SHA256SUMS.txt` for integrity verification.

Linux/macOS example:

```bash
sha256sum -c SHA256SUMS.txt
```

## 8. Normal edit / commit / push cycle

After changing code:

```bash
git status
git add .
git commit -m "Describe the change"
git push
```

A successful push to `main` replaces the existing rolling `latest` release instead of creating another versioned release.

## 9. Recommended branch workflow

For larger changes:

```bash
git switch -c feature/reference-engine-improvement
# edit files
git add .
git commit -m "Improve reference engine"
git push -u origin feature/reference-engine-improvement
```

Open a pull request into `main`. The workflow tests and builds the branch with a read-only token, but it does **not** publish a release. After the pull request is merged, the `main` push updates the rolling `latest` release.

## 10. Important GitHub repository setting

The workflow uses the repository `GITHUB_TOKEN` to replace the `latest` tag and release. Normally the per-job `contents: write` permission in the workflow is sufficient. If an organization policy overrides it, check:

**Repository → Settings → Actions → General → Workflow permissions**

and make sure repository/organization policy allows Actions to obtain write permission when the workflow declares it.

If your repository enables **immutable releases**, a rolling release cannot be deleted/replaced. Disable immutable releases for this rolling-`latest` design, or change the workflow to publish versioned releases instead.

## 11. Local clean rebuild

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
./scripts/verify-abi-apks.sh app/build/outputs/apk/debug dist
```

## 12. Inspect the exact workflow

The CI/release definition is here:

```text
.github/workflows/android-latest.yml
```

The ARM-output guard is here:

```text
scripts/verify-abi-apks.sh
```
