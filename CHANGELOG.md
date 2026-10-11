# Changelog

## 1.1 — Elderflip (unreleased)

- Rename the app from Flip Pet to Elderflip; keep the app ID for save-compatible upgrades.
- Let players choose the phone's outer-screen interface (the default) or a pet view with a clock.
- Make Gentle the default: quarter-speed meter changes and no death from neglect.
- Add optional Survival mode with explicit confirmation and an on-screen grace warning.
- Fix elapsed-time deaths: the 15-minute grace starts when a meter empties, not at the beginning of the entire absence.
- Freeze a dead creature's age and meters at its death time.
- Require a fresh Ward action to cure overfeeding, even if SALT was already high.
- Add number-key shortcuts, creature notes, and care-mode labels.
- Fit meter bars and status text to the 240 × 320 display.
- Pause hidden pet animation and avoid saving to disk on every five-second refresh.
- Persist the first egg immediately and show save failures on the main screen.
- Handle backward clock corrections without double-charging elapsed time.
- Add separate preview builds, unsigned builds, care-rule tests, and preview-only device checks.
