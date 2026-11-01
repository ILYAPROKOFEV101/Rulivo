# Localization

Rulivo follows Android's system language list by default. The compact Language dropdown in the Optional support mode sheet lets users select any supported language or return to Device default. English is the complete fallback when no supported language matches. The app does not infer a language from a country, SIM card or location, and does not change the user's system language.

| Language | Intended audience | Android resources |
| --- | --- | --- |
| English (`en`) | Default and English-speaking users in any region | `values`, `values-en` |
| Indonesian (`id`) | Indonesia | `values-in` (Android's legacy qualifier) |
| Urdu (`ur`) | Pakistan | `values-ur` |
| Hausa (`ha`) | Hausa-speaking users in Nigeria | `values-ha` |
| Hindi (`hi`) | Hindi-speaking users in India | `values-hi` |

Pakistan, Nigeria and India each have multiple languages. Urdu, Hausa and Hindi are the language choices implemented for this request, not a claim that each country has only one language. The system language picker on Android 13+ lists these languages through `res/xml/locales_config.xml`; `in` is Android's Indonesian alias. Older devices select resources from their language settings too.

## Implementation

- The in-app dropdown reads the same `locales_config.xml` as Android settings and displays each language in its own language using Android's locale names. Labels are string resources. Selecting a language uses `AppCompatDelegate.setApplicationLocales`; `MainActivity` extends `AppCompatActivity`, and `autoStoreLocales` enables persistence on Android 12 and earlier. Android 13+ synchronizes with the system per-app setting. The open sheet and draft survive activity recreation.
- Language splits are disabled in the app bundle so all supported translations are available offline after a Play install, including languages not present in the device language list.
- Background resource initialization uses `ContextCompat.getContextForLanguage`; activity resources are applied before view models are created. SDK notification preferences, existing notification text and the SDK's otherwise hardcoded channel name are refreshed in the chosen language. No service restart or consent change is required to relabel an existing notification.
- `androidResources.localeFilters` excludes unrelated library translations so Android can choose a supported secondary language from the device list. Keep it in sync with `locales_config.xml`.
- All UI labels, camera guidance, accessibility descriptions, validation errors, result notes and share text use Android string resources. `Rulivo` is a non-translatable brand.
- `RulivoApplication` initializes `AppStrings` before view models or camera controllers run. The resolver keeps resources, not cached translations. `RulivoTheme` observes `LocalResources` for configuration changes and previews. Its layout direction follows the selected translation; Urdu uses RTL, and an unsupported RTL device language falls back to English with LTR layout.
- Enum labels resolve resource IDs on access. Retained toolkit messages resolve on display. Saved result titles/notes are derived again from the draft in the current language. Explicit user names are preserved.
- History schema 2 persists stable inputs, enum names and numeric results without translated result titles or notes. Version 1 files still load; stored presentation text is replaced by current translations. Existing user names are not guessed or rewritten.
- Numbers and dates use the selected language's regional locale. Numeric input accepts Latin, Devanagari and Arabic digits, a decimal point/comma or Arabic decimal separator. Grouped dimension input is rejected to avoid ambiguous values. SI symbols remain international.
- The translations are bundled and work offline. Initial translations were machine-assisted and edited for measurement terminology. A native-speaker editorial review of the four new languages remains advisable before a public release.

## Editing text

Add a named string to `values/strings.xml`, `values-en/strings.xml`, and each of the four translated catalogs. Keep positional arguments such as `%1$s` consistent. Translate complete phrases; never construct a message by replacing a translated word. Do not cache enum labels, force `Locale.US`/`ru-RU`, or use a translated string as a storage key. Keep user-entered text unchanged.

Run:

```sh
./gradlew :app:checkLocalization :app:testLocalizationCheck
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

`LocalizationTest` checks numeric input/formatting and saved presentation across languages. `LocaleResourcesTest` checks Android locale-list matching, English fallback, format placeholders and both history schemas. `LocaleUiTest` checks live recomposition and RTL at a larger font size. The existing measurement/UI tests use resource labels and locale-aware expected values.

`checkLocalization` also runs automatically before Android builds. The checker and its regression tests live in `tools/localization/` and use only the JDK already running Gradle; Python and Xcode Command Line Tools are not required. `testLocalizationCheck` is included in Gradle's `check` task. The checker rejects missing translations, punctuation-only translations, copied English text (with explicit exceptions for shared Indonesian vocabulary), inconsistent format arguments, common hardcoded Compose text, fixed locales and language split regressions. Static checks do not replace editorial review or device testing.

Android references: [Language and locale resolution](https://developer.android.com/guide/topics/resources/multilingual-support), [Per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages).

## Verification on 2026-09-24

- Build portability follow-up: replaced the external Python checker with a JDK-only implementation. Reproduced the reported exit-72 failure with a failing `python3` on `PATH`; the same environment now passes `:app:assembleDebug :app:bundleRelease :app:testDebugUnitTest :app:testLocalizationCheck :app:lintDebug`. The new checker passes 22 regression cases, and its command-line entry point exits with code 1 for an incomplete catalog. Evidence is in `build/localization-runtime-qa/` (`before.log`, `after-build.log`, `regression.log`, `rejected-catalog.log`).
- Audited all 514 strings per language. Repaired missing sentences, copied English and obvious terminology errors in Hausa.
- Added `LanguageSelectionTest` to exercise every language from the real dropdown, the selected marker, automatic sheet restoration, retained user draft data, and returning to Device default.
- Added `SupportModeNotificationLocaleTest` to update notification text and the channel name in every language without enabling SDK network sharing.
- Added four checker regression tests, including broken translations, changed placeholders and multiline hardcoded UI strings.
- All 146 JVM tests and the four checker tests passed. The device run covered 21 tests: 20 passed together, and the notification test passed on a focused rerun after waiting for Android's asynchronous notification update. Debug APK, release AAB and lint completed successfully (no lint errors).
- Confirmed Hindi survives a force-stop and cold launch using the system per-app locale setting, then restored Device default. Real dropdown screenshots cover the collapsed row, expanded choices and Urdu RTL.
- QA screenshots and command logs are in `build/localization-audit/` and `build/localization-audit-*.log`. The device checks use an API 36 emulator; pre-Android-13 storage relies on AppCompat and has not been exercised on an older emulator in this audit.

## Verification on 2026-09-23

- 126 JVM tests passed.
- The emulator suite exercised 49 tests. After updating the old Russian expectations, 48 passed; the remaining language-list priority regression was fixed by filtering library locales. A focused rerun of all four locale resource/UI tests passed after that fix.
- `lintDebug` completed with no errors. Existing SDK/dependency notices and non-blocking typography/unused-resource warnings remain.
- The resource checker passed for 474 strings per language, including positional arguments and consistency between the language picker and packaged locales.
- Urdu and Hindi menus were visually inspected on the API 36 emulator. The per-app test locale was restored to the system default afterward.

Local QA output is in `build/localization/`; the installable debug build is `app/build/outputs/apk/debug/app-debug.apk`.
