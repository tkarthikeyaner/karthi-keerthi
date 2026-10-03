# Changelog

All notable changes to Smart Cleaner. Follows [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-10-03

### Added
- Full internal-storage scan covering photos, videos, audio, documents and every other file type; app packages (`.apk`, `.xapk`, …) and app containers (`Android/data`, `Android/obb`) are excluded.
- Large file detection with a configurable threshold (5 MB – 4 GB).
- Duplicate detection:
  - Identical content via size → head/tail fingerprint → full SHA-256.
  - Similar names such as `name (1).ext`, `name - Copy.ext`.
- Daily scheduled scan at a user-chosen time (WorkManager, survives reboots) with a summary notification (total size, new files, large files, duplicates).
- Material 3 dashboard grouped by Photos, Videos, Audio, Documents and Other; dark/light/system theme and Material You dynamic color.
- Review & cleanup: multi-select lists, keep/delete chips per duplicate, and a mandatory confirmation dialog where each file can still be kept.
- Xiaomi (MIUI / HyperOS) background reliability: battery-optimisation exemption prompt and Autostart shortcut so the daily scan isn't killed.
- GitHub Actions pipeline producing installable APKs on every push and a GitHub Release on `v*` tags.
