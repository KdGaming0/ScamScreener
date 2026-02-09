# Changelog (Branch `gui`)
**Date:** February 9, 2026

## Added
- New GUI system exposed via `/scamscreener settings` (`SettingsCommand` + `MainSettingsScreen`).
- Dedicated submenus for `Rules`, `Debug`, `Messages`, `Blacklist`, and `AI Update`.
- New shared GUI base class (`GUI.java`) to centralize common screen behavior.
- AI update menu actions: `Accept`, `Merge`, `Ignore`, plus `Force Check`.
- New `MessageBuilder` base class for centralized message/text styling helpers.

## Changed
- Blacklist GUI expanded with paging, selection, score `+10/-10`, remove, reason editing, rule dropdown, and save action.
- Blacklist GUI row styling aligned with chat blacklist display colors.
- Colored ON/OFF display in settings (`ON` light green, `OFF` light red).
- Scam warning and blacklist warning output/sound are now independently configurable via rules config.
- Auto-leave info message can now be toggled independently.
- `ModelUpdateService` now exposes a pending snapshot for GUI status (`latestPendingSnapshot`).

## Removed
- Keybind system fully removed (`Keybinds`, `FlaggingController`, legacy keybind language keys).
- `ErrorMessages` removed; error construction consolidated into `MessageBuilder`.

## Fixed
- Screen rendering stabilized (prevents blur crash: “Can only blur once per frame” via custom GUI background rendering).
- Improved null/state handling in AI update and blacklist UI flows.

## Refactor
- Shared screen logic centralized (layout, footer buttons, toggle line formatting, screen navigation).
- `Messages` and `DebugMessages` migrated to shared builder helpers (less duplication, no bridges).
- `ClientTickController` simplified and moved to a settings-open request flow.

## Version
- `mod_version` bumped to **`0.14.7`** (`gradle.properties`).
