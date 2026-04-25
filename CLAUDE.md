# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

OpenTagViewer is an Android app (Java) that lets users view and track their official Apple AirTags from Android. It is a UI wrapper around the Python [FindMy.py](https://github.com/malmeloo/FindMy.py) library, embedded into the Android app via [Chaquopy](https://chaquo.com/chaquopy/). A separate Python `tkinter` desktop wizard under [python/](python/) is used (once, on a Mac) to extract AirTag `.plist` data into a `.zip` that the Android app then imports.

## Build and run

The Android app lives under [app/](app/). Gradle is the build system (Kotlin DSL).

- Build debug APK: `./gradlew assembleDebug` (artifact at `app/build/outputs/apk/debug/`)
- Build release APK: `./gradlew assembleRelease` (requires `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env vars)
- Unit tests: `./gradlew test` (or just debug variant: `./gradlew testDebugUnitTest`)
- Single test class: `./gradlew :app:testDebugUnitTest --tests "dev.wander.android.opentagviewer.ExampleUnitTest"`
- Instrumented tests (require device/emulator): `./gradlew connectedAndroidTest`

JDK 17 is required for the build (per CI). `compileSdk`/`targetSdk` = 35, `minSdk` = 24, `sourceCompatibility` = Java 11.

### Secrets / API keys

Two API keys are injected via the `secrets-gradle-plugin` from a `secrets.properties` file at the repo root (not committed; defaults come from `local.defaults.properties`):

- `MAPS_API_KEY` — Google Maps
- `AMAP_API_KEY` — AMap (Gaode, 高德地图), alternative map provider for users in China

The keys are surfaced into `AndroidManifest.xml` as `${MAPS_API_KEY}` / `${AMAP_API_KEY}` meta-data and consumed by the respective SDKs. CI injects placeholder values when secrets aren't set.

## Architecture

### Java + Python hybrid via Chaquopy

The app extends `com.chaquo.python.android.PyApplication` ([OpenAirTagApplication.java](app/src/main/java/dev/wander/android/opentagviewer/OpenAirTagApplication.java)). `chaquopy` is configured in [app/build.gradle.kts](app/build.gradle.kts) to install `FindMy==0.7.6` and `NSKeyedUnArchiver==1.5` via pip into the APK; the entry-point Python module is [app/src/main/python/main.py](app/src/main/python/main.py).

NDK ABIs are restricted to `arm64-v8a` and `x86_64` to keep the bundled Python runtime size manageable.

All Java→Python interop goes through the `python` package:
- [PythonAppleService.java](app/src/main/java/dev/wander/android/opentagviewer/python/PythonAppleService.java) — singleton wrapping `main.py` calls (`getLastReports`, `getReports`, etc.); returns `Observable<…>` on `Schedulers.io()`.
- [PythonAuthService.java](app/src/main/java/dev/wander/android/opentagviewer/python/PythonAuthService.java) — wraps Apple login (incl. 2FA flow via `loginSync`).
- [PythonAppleAccount.java](app/src/main/java/dev/wander/android/opentagviewer/python/PythonAppleAccount.java) — holds a serialized `AppleAccount` (`PyObject`) that's the authentication anchor for all subsequent calls.

`PythonAppleService` and `PythonAppleAccount` are explicit singletons set up after login; if you're touching code that fetches reports, expect to read/restore the account via `main.py:getAccount` first.

### Data layer

Two persistence mechanisms, repos in [db/repo/](app/src/main/java/dev/wander/android/opentagviewer/db/repo/):

1. **Room database** ([OpenTagViewerDatabase.java](app/src/main/java/dev/wander/android/opentagviewer/db/room/OpenTagViewerDatabase.java)) — singleton `opentagviewer-db`. Entities: `Import`, `BeaconNamingRecord`, `OwnedBeacon`, `LocationReport`, `DailyHistoryFetchRecord`, `UserBeaconOptions`. The `BeaconRepository` aggregates beacon state + history.
2. **DataStore preferences** in [db/datastore/](app/src/main/java/dev/wander/android/opentagviewer/db/datastore/): `UserAuthDataStore` (encrypted Apple-account blob), `UserCacheDataStore`, `UserSettingsDataStore`. Uses RxJava3 bindings.

The `.zip` import path: [util/parse/AppleZipImporterUtil.java](app/src/main/java/dev/wander/android/opentagviewer/util/parse/AppleZipImporterUtil.java) parses XML/YAML and `BeaconNamingRecord` plist data — note that the plist `cloudKitMetadata` blob is in `NSKeyedArchiver` format, which has no Java parser, so it's bounced through Python via `main.py:decodeBeaconNamingRecordCloudKitMetadata` using the `NSKeyedUnArchiver` pip package. See [BeaconNamingRecordInnerParser.java](app/src/main/java/dev/wander/android/opentagviewer/util/parse/BeaconNamingRecordInnerParser.java).

### UI layer

Multi-activity, no Jetpack Compose. ViewBinding + DataBinding are enabled. `AppleLoginActivity` is the launcher. The main screen is `MapsActivity`. RxJava3 (`Observable`/`Schedulers.io()`) is used pervasively for async work; ViewModels live under [viewmodel/](app/src/main/java/dev/wander/android/opentagviewer/viewmodel/).

#### Map provider abstraction

The app supports two map providers, switched at runtime per user setting:

- [IMapProvider.java](app/src/main/java/dev/wander/android/opentagviewer/ui/maps/IMapProvider.java) — common interface
- [GoogleMapProvider.java](app/src/main/java/dev/wander/android/opentagviewer/ui/maps/GoogleMapProvider.java) — `play-services-maps`
- [AMapProvider.java](app/src/main/java/dev/wander/android/opentagviewer/ui/maps/AMapProvider.java) — Gaode/AMap 3D SDK (China-friendly)
- [MapProviderFactory.java](app/src/main/java/dev/wander/android/opentagviewer/ui/maps/MapProviderFactory.java) — picks one based on the `"google"`/`"amap"` setting
- [CoordinateConverter.java](app/src/main/java/dev/wander/android/opentagviewer/ui/maps/CoordinateConverter.java) — WGS-84 ↔ GCJ-02 conversion when using AMap

The AMap SDK requires a privacy-compliance pre-init invoked via reflection in `OpenAirTagApplication.initAMapPrivacyCompliance()` *before* any AMap API call. There is also a workaround there for the AMap manifest meta-data being parsed as an Integer instead of a String when the key happens to look numeric — keep that reflection block intact.

### Networking

Retrofit + Jackson + Google Cronet (the Cronet engine is set up in [service/web/CronetProvider.java](app/src/main/java/dev/wander/android/opentagviewer/service/web/CronetProvider.java)). External services include Anisette servers (for Apple auth — `AnisetteServerTesterService`), GitHub (for releases/utility files), and SideStore. See [service/web/](app/src/main/java/dev/wander/android/opentagviewer/service/web/).

### Background

`AppNavigationService` is a foreground service (`foregroundServiceType="location"`) for live location updates while the app is in use.

## Lombok

Lombok is used (`gradle-lombok` plugin + `compileOnly`/`annotationProcessor` deps). `@Getter`, `@Builder`, `@RequiredArgsConstructor`, etc. are common. `var` requires Java 11 source compatibility — already configured.

## Localization

Translations live under [app/src/main/res/values-<locale>/strings.xml](app/src/main/res/). Locales are listed in [app/src/main/res/xml/locales_config.xml](app/src/main/res/xml/locales_config.xml). When adding a string, add it to `values/strings.xml` (default English) and ideally to other locales.

## CI

GitHub Actions ([.github/workflows/](.github/workflows/)):
- `build-debug.yml` — runs `./gradlew test` then `./gradlew testDebugUnitTest assembleDebug`, uploads APK artifact (triggers only on Android-related path changes).
- `build-release.yml` — release builds (signed via repo secrets).
- `macos-exporter-python.yml` / `macos-scripts-python.yml` — for the `python/` desktop wizard, not the Android Python.
