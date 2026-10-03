---
name: mobile-app-release-manager
description: Manages versioning, feature scaling, and release tracking for the Smart Cleaner Android app in this repository. Use when the user asks to cut a release ("Release 1.0.0", "next patch", "minor release", "bump version"), update the changelog, or prepare a build for testing on a phone.
---

# Mobile App Release Manager

Project: repository root (Kotlin, Compose). Version lives in `app/build.gradle.kts` (`versionCode`, `versionName`). Changelog: `CHANGELOG.md`. CI: `.github/workflows/build.yml` (builds on push, publishes a GitHub Release for `v*` tags).

## 1. Version increment
- Read the current `versionName`; apply semver:
  - patch (default, "next patch"): bug fixes / perf only → `1.0.0 → 1.0.1`
  - minor: new user-facing features, backward compatible → `1.0.1 → 1.1.0`
  - major: breaking changes (data/settings migration, min SDK raise) → `1.1.0 → 2.0.0`
  - explicit ("Release 1.2.0"): use as given; refuse if not greater than the current version.
- Always increment `versionCode` by exactly 1 alongside `versionName`. Android refuses to install an update whose versionCode is not higher.

## 2. Changelog
- Collect changes since the last release: `git log --oneline <last-tag-or-version-bump-commit>..HEAD`.
- Prepend a `## [x.y.z] - YYYY-MM-DD` section to `CHANGELOG.md` with `### Added`, `### Fixed`, `### Performance`, `### Changed` (omit empty headings). Write user-facing language, not commit messages.

## 3. Code optimization review (before bumping)
Check and fix, or list as follow-ups:
- **Scanning memory/leaks**: `FileScanner` must stay iterative and cancellable (`ensureActive`); no `Context`/`Activity` held in long-lived objects (repositories take `applicationContext` only); `ScanResult` lists not duplicated unnecessarily; hashing streams closed via `use {}`.
- **Background work**: `ScanWorker` re-arms the schedule in `finally`; no `REPLACE` policy from inside the worker.
- **UI**: Lazy lists use stable `key`s and `contentType`; expensive derivations wrapped in `remember(keys)`; state collected with `collectAsStateWithLifecycle`.
- Run `./gradlew testDebugUnitTest lintDebug` from the repository root when an Android SDK is available; otherwise rely on CI.

## 4. Deployment guide
- Commit: `release: vX.Y.Z` (version bump + changelog), push the working branch, and confirm the CI run is green.
- Tagging publishes a public release. Ask the user before pushing a `vX.Y.Z` tag (`git tag vX.Y.Z && git push origin vX.Y.Z`), or tell them how to create the release from GitHub mobile (Releases → Draft new release → tag `vX.Y.Z`).
- Reply with: new version, the changelog section, any optimization findings, and the phone test steps: download the APK from the Release or Actions artifact → install over the existing app → verify the version under Settings → run a manual scan → set the daily time 2–3 minutes ahead to check the notification.
