# Smart Cleaner (Android)

Kotlin · Jetpack Compose · Material 3 · WorkManager · DataStore · Coil. Min Android 8.0 (API 26), target API 35.

## Architecture

```
app/src/main/java/com/karthi/smartcleaner/
├── CleanerApp.kt / AppContainer.kt   # App entry, manual DI, Coil image loader (video thumbnails)
├── MainActivity.kt                   # Permissions + Compose navigation
├── data/                             # Models, ScanRepository (single source of truth), DataStore settings
├── scanner/                          # FileScanner (iterative walk), DuplicateDetector, SHA-256 hasher
├── work/                             # ScanWorker, exact-time ScanScheduler, notifications
└── ui/                               # MainViewModel, theme, Dashboard / FileList / Duplicates / Settings
```

- Scanning runs on `Dispatchers.IO`, is cancellable, and walks directories with an explicit stack (no recursion, no symlink loops).
- Duplicate hashing is staged (size → 32 KB fingerprint → full SHA-256), so large videos are read in full only when they are very likely identical.
- A `Mutex` keeps the scheduled scan and a manual scan from running at the same time.
- Nothing is deleted without the confirmation dialog; the last copy of a duplicate group can't be marked for deletion.

## Build & install from your phone (no PC needed)

1. **Get the APK built in the cloud**: every push that touches `android-cleaner/` runs the *Smart Cleaner APK* workflow on GitHub Actions.
   - In the GitHub app or mobile browser, open the repo → **Actions** → latest *Smart Cleaner APK* run → **Artifacts** → download `SmartCleaner-x.y.z` (a zip; open it in your Files app).
   - Or **publish a release**: on github.com (mobile browser) → **Releases** → **Draft a new release** → tag `v1.0.0` → **Publish**. The workflow attaches `SmartCleaner-1.0.0.apk` to the release page, and you can download it directly.
2. **Install**: tap the APK and allow *Install unknown apps* for your browser/Files app when prompted.
3. **First run**: tap **Grant access** and enable *Allow access to manage all files*, then allow notifications.
4. **Test the daily scan**: in Settings, set the scan time 2–3 minutes ahead and lock the phone. You should get a summary notification shortly after that time (Android may delay it by a few minutes in battery saver/Doze).

Builds are signed with the fixed key in `app/ci-debug.keystore`, so new versions install over old ones. Use a private key (GitHub secrets) before publishing to the Play Store.

### Optional: build on the phone itself (Termux)

On-device Gradle builds need an ARM `aapt2` and are fragile; the cloud build above is recommended. If you still want to try:

```sh
pkg install openjdk-17 git
git clone https://github.com/tkarthikeyaner/karthi-keerthi && cd karthi-keerthi/android-cleaner
./gradlew assembleDebug   # requires an Android SDK set up for Termux (sdk.dir in local.properties)
```

## Local development

Open `android-cleaner/` in Android Studio, or run `./gradlew testDebugUnitTest assembleDebug`.
