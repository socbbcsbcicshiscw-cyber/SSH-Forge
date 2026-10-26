# Changelog

## v1.0.3
### Added
* The calculation history is saved and restored between sessions (long-press `C` still wipes it)
* The angle unit (DEG/RAD) is remembered
* The converter remembers the last dimension and the two units you used
* Settings: theme picker (follow system / light / dark)
* Settings: switch for wallpaper-based colours (Material You, Android 12+)
* Settings: "Clear history" action
* Version number is shown in Settings

## v1.0.2
### Changed
* Update how to display error messages
* Make precision dialog more user-friendly

### Fixed
* Handle expressions that blow to infinity like tan(π/2)
* Fix the bug with converter swap button
* Throw an error if temperature conversion results into a value below absolute zero
* Other minor fixes that ensure accuracy in the converter


## v1.0.1: Initial release