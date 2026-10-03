# Smart Cleaner (Android)

Find and clean large files and duplicates across photos, videos, audio and documents. Daily scheduled scans with notifications, Material 3 UI, and Xiaomi-ready background handling.

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

## Build & launch on your Xiaomi phone (no PC)

The project lives on the phone at `/storage/emulated/0/Apps/Mobile-Application`.

1. Install **Termux** from F-Droid (https://f-droid.org/packages/com.termux/) or its GitHub releases. The Play Store version is outdated and can't install packages.
2. Open Termux and run:
   ```sh
   termux-setup-storage        # tap "Allow" so Termux can read /storage/emulated/0
   bash /storage/emulated/0/Apps/Mobile-Application/scripts/build-on-phone.sh
   ```
   The script installs Java 17 and an ARM `aapt2`, downloads the Android SDK, builds the app, copies `SmartCleaner-debug.apk` to **Download**, and opens the installer.
   The first run takes 10–20 minutes and downloads about 400 MB, so stay on Wi-Fi. Later runs only rebuild what changed.
3. When asked, allow **Termux → Install unknown apps**, then tap **Install**. On MIUI's "risky app" screen, tap **Install anyway**.
4. **Launch**: open **Smart Cleaner** (the blue/teal broom icon) from the home screen or app drawer.
5. **First launch**:
   - Tap **Grant access** and turn on *Allow access to manage all files*.
   - Allow notifications.
   - Go to **Settings → Background reliability** and tap **Allow** (battery) and **Open** (Autostart → enable Smart Cleaner).
6. After editing code in the folder, run the script again. The new build installs over the old one.

If the build stops with an `aapt2` error, Termux's `aapt2` is too old for this Gradle plugin. Use the cloud build below instead.

## Other ways to build

### Option A — cloud build, install on the phone (no PC)
1. Push to any branch of `tkarthikeyaner/Mobile-Application`; GitHub Actions runs **Smart Cleaner APK**.
2. On the phone: GitHub app / browser → **Actions** → latest run → **Artifacts** → `SmartCleaner-x.y.z` → open the zip in **File Manager** → tap the APK.
   For a direct APK link, publish a release instead: **Releases → Draft a new release → tag `v1.0.0` → Publish**; the APK is attached automatically.
3. MIUI shows *"Install blocked / risky app"*: tap **Settings → allow from this source**, then **Install anyway**. (Turn off *MIUI optimisation* only if installs keep failing.)

### Option B — PC + Android Studio, deploy to the Xiaomi over USB or Wi-Fi
1. **Developer options**: Settings → About phone → tap **MIUI version** / **OS version** 7 times.
2. Settings → Additional settings → **Developer options**, enable:
   - **USB debugging**
   - **Install via USB** (needs a signed-in Mi account and, on some models, a SIM card)
   - **USB debugging (Security settings)** (lets Android Studio grant permissions / tap dialogs)
3. Connect USB, accept the RSA prompt on the phone, then in the repo:
   ```sh
   ./gradlew installDebug          # or press Run in Android Studio
   adb logcat -s ScanWorker WM-WorkerWrapper
   ```
   Wireless: Developer options → **Wireless debugging** → *Pair device with pairing code* → `adb pair <ip:port>` then `adb connect <ip:port>`.

### Required Xiaomi settings for the daily scan
MIUI/HyperOS kills background jobs aggressively. In the app open **Settings → Background reliability** (shown automatically on Xiaomi/Redmi/POCO):
- **Battery optimisation → Allow** (system prompt), and
- **Autostart → Open** → enable Smart Cleaner.
- Also: phone Settings → Apps → Manage apps → Smart Cleaner → **Battery saver → No restrictions**, and keep **Notifications** on.

### Testing checklist on the phone
| Check | How |
|---|---|
| Storage access | Grant access → toggle *Allow access to manage all files* → dashboard scans automatically |
| Large files | Settings → threshold 5 MB → *Large files* count rises without a rescan |
| Duplicates | Copy a photo in File Manager (creates `name(1).jpg`) → Rescan → appears under *Identical content* |
| Delete flow | Select files → *Delete* → untick one in the dialog → only ticked files are removed; Gallery updates |
| Daily scan | Set scan time 2–3 min ahead, lock the phone → summary notification arrives |
| Inspect the schedule (USB) | `adb shell am broadcast -a androidx.work.diagnostics.REQUEST_DIAGNOSTICS -p com.karthi.smartcleaner.debug` then `adb logcat -s WM-DiagnosticsWrkr` |
| Theme | Settings → Light / Dark / System, *Dynamic color* (HyperOS / MIUI 13+ on Android 12+) |

Builds are signed with the fixed key in `app/ci-debug.keystore`, so new versions install over old ones (debug build id: `com.karthi.smartcleaner.debug`, release: `com.karthi.smartcleaner`). Use a private key from GitHub secrets before publishing to Play Store.

## Local development

Open the project root in Android Studio (Ladybug or newer, JDK 17), or run `./gradlew testDebugUnitTest assembleDebug`.
