# Dunta SDK in Rulivo

The integration is ported from `APP/MapShots`, using the same
`app/libs/dunta_sdk-release.aar` (SHA-256
`df60bb623186ed8e9c1b0ed286a6ce399ef476f7244d3a1e53fdf979d358a374`).

## Identity

Configure provider-issued identifiers locally in `local.properties`:

```properties
DUNTA_PARTNER_ID=<provider-issued partner ID>
DUNTA_APPLICATION_ID=<provider-issued application ID>
```

Replace the placeholders with the identifiers allocated to this application.
Provider-side allocation or acceptance of the local integration configuration
has not been verified. The Android application ID remains `com.ilya.rulivo`.

As in MapShots, Gradle properties (`-P`) override `local.properties`. Missing
values default to zero, disabling SDK activation. Invalid or negative values
fail Gradle configuration. CI must supply both IDs explicitly.

## Behavior and adaptation

- The same optional consent sheet appears before activation, with Rulivo
  branding and English, Indonesian, Urdu, Hausa, and Hindi resources.
- Accepting requests Android notification permission where required. Permission
  denial preserves the prior SDK choice. Accepting with permission sets PEER
  and starts the SDK using the application context.
- The main menu's **Optional support mode** button opens the sharing switch,
  privacy link, and optional Android battery settings. Switching off stops the
  service, persists NOT_PEER, and cancels the recovery jobs.
- A declined user can receive one reminder on the third subsequent launch;
  explicit disabling suppresses reminders, matching MapShots.
- Consent visibility and pending permission requests survive Activity recreation.
- Recovery uses the same two unique 15-minute WorkManager jobs (unmetered
  network, or connected network while charging), only for configured, consenting
  users who have disabled battery optimization for the app.
- `specialUse` service configuration and removal of the SDK's legacy storage
  and `dataSync` permissions match MapShots. Existing backup rules exclude
  SharedPreferences, so consent is not transferred to a new installation.
- Debug diagnostics retain the `SupportModeDiag` log tag.

Rulivo has no Hilt container: `RulivoApplication` owns one controller, and the
worker accesses that instance. SDK strings use Rulivo's Android-selected locale
instead of MapShots' language setting. The existing `ComponentActivity` and AR
screen behavior are retained; the Compose consent sheet needs no AppCompat
Activity. SDK dependencies and desugaring versions match the reference project.

## Verification commands

```sh
./gradlew :app:checkLocalization
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ilya.rulivo.core.dunta.SupportModeConsentTest,com.ilya.rulivo.core.dunta.SupportModeUiTest,com.ilya.rulivo.LocaleResourcesTest,com.ilya.rulivo.RulivoLaunchTest
```

Consent tests exercise real service startup/stop, decline persistence, Activity
recreation, notification-denial behavior, and reminder suppression. The UI test
opens settings from the menu, enables through consent, and switches off again.
These checks do not establish provider registration, revenue, or long-running
background behavior on physical devices.

Verified on 2026-09-23:

- Debug and unsigned release APKs built successfully with locally configured provider identifiers.
- All 126 JVM tests and all 9 selected Android tests passed on an Android 16
  Pixel 7 emulator. The UI flow also passed separately with `ur-PK` selected.
- Localization check: 498 strings in each of the five languages.
- Lint completed with 0 errors and 60 warnings, including four inherited
  `SharedPreferences.edit` style suggestions in the ported controller.
- English consent and Urdu consent/settings screenshots were visually checked.
  Logs and screenshots are in `build/dunta-integration/`. The test mode was
  disabled afterward and the emulator app locale was restored to system default.

JVM resource fixtures and the localization checker now read all resource XML
files so they include the separate `dunta_strings.xml` catalogs. The existing
launch test explicitly dismisses support onboarding before checking measurements.
