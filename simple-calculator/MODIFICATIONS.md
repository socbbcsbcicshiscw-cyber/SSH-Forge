# Changes compared to upstream 1.0.2

Base: [`hobby-projects-lab/simple-calculator`](https://github.com/hobby-projects-lab/simple-calculator)
v1.0.2 (`c3d6355`). Everything below is additive — no behaviour of the calculator engine,
the converter math, or the keypad layout was changed.

## Persistence (the biggest real-world annoyance: everything was lost on exit)

| What | Where |
| --- | --- |
| Calculation history is stored in DataStore and restored on the next launch, capped at 100 entries | `data/UserPreferencesRepository.kt`, `ui/screens/calculator/ViewModel.kt` |
| Long-pressing `C` (and the new *Clear history* action) wipes both the in-memory and the stored history | `ui/screens/calculator/ViewModel.kt`, `ui/screens/settings/*` |
| The angle unit (DEG / RAD) is remembered | `ui/screens/calculator/ViewModel.kt` |
| The converter remembers the selected dimension and both units, and reopens on them | `ui/screens/converter/ViewModel.kt` |

## Settings screen

Four new rows, all wired to the same preferences repository:

* **Appearance → Theme**: *Follow system* / *Light* / *Dark*, applied immediately (no restart).
* **Appearance → Match system colours**: switch for the Material You / wallpaper palette
  (Android 12+; disabled with an explanatory summary on older versions). Previously dynamic
  colour was always on and there was no way to opt out.
* **Calculation → Precision**: same dialog as before, now shows the current value as a summary.
* **Calculation → Clear history**: with the number of stored calculations.
* The version number is shown at the bottom of the screen.

## Under the hood

* `CalculatorTheme` takes a `themeMode` + `dynamicColor` instead of a `darkTheme` boolean, and is
  driven by the new `ui/theme/ThemeViewModel.kt` collected in `MainActivity`.
* `UserPreferencesRepository` now exposes a single shared `preferences` flow instead of reading the
  DataStore once per preference.
* `MeasurementUnit.key` / `Dimension.key` helpers (`utils/Utils.kt`) give the enum names used as
  stable storage keys.
* Version bumped to 1.0.3 / 3, new `CHANGELOG.md` and Fastlane changelog entry.

## Deliberately *not* changed

* The expression parser, `Keval`/`BigDecimal` precision loop, unit conversion factors and their
  error handling.
* App id, permissions (there still are none), offline-only behaviour, GPL-3.0 licensing.
