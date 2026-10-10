# Elderflip

**A small cosmic horror for your dumbphone.**

An offline virtual pet for Android flip phones. Hatch an unknowable little thing,
feed it, play with it, lay down salt, and let it sleep. Six species grow through
six stages, from an unthought egg to an elder god, over three days.

Gentle care is the default. Your pet can wait while you put the phone away.
For those who want consequences, Survival mode restores the original stakes.

No account, ads, notifications, server, or network permission. All creatures are
drawn and animated on the phone. No AI runs in the app. The project was built
with help from OpenCode and refined with Codex.

## Which phones?

An **Android 8.0 or later** phone that supports APK sideloading. Designed around
physical keys and a small screen; the 1.1 preview was checked on a TCL 4058W
running Android 11 at 240 × 320. Other models still need testing.

This is an Android app, not a KaiOS or Series 30+ app. A phone being a flip phone
does not by itself make it compatible.

## Play

Use the D-pad to choose an action and OK to select it, or use the number keys:

| Key | Action | Effect |
| --- | --- | --- |
| 1 | Feed | Raises FED and a little JOY |
| 2 | Play | Raises JOY, costs FED and LIFE |
| 3 | Ward | Raises SALT, costs a little JOY |
| 4 | Sleep / Wake | Sleep restores LIFE while other needs drain more slowly |
| BACK / MENU | Menu | Name, care mode, creature notes, new egg, and rules |

Feeding above 85 FED makes it sick. Sickness doubles LIFE and SALT drain when
those meters are draining. Use Ward to reach at least 95 SALT to cure it; salt
already on the ground does not cure a new bout of sickness automatically.

### Gentle and Survival

**Gentle** runs meter changes at one quarter of Survival speed. Empty meters
make for an unhappy creature, but neglect cannot kill it. There are no reminders
asking you to return. Sleep still restores LIFE, at the gentler pace.

**Survival** uses the original faster rates. Once a draining meter reaches zero,
you have 15 minutes to restore it before permanent death. Time passes while the
app is closed. From a fresh, awake egg, hunger reaches zero after five hours;
death follows at five hours fifteen minutes if you do nothing. Sleeping changes
that timing, but food, joy, and salt still drain.

Choose the mode in **BACK → Care**. Survival requires confirmation. Switching
modes keeps the creature and its stats, and does not revive a dead pet. A new
egg keeps the selected mode. Existing saves upgrade into Gentle mode.

## What hatches?

| Age | Stage |
| --- | --- |
| Under 1 hour | Egg, unthought |
| 1 hour | Hatchling |
| 4 hours | Writhing thing |
| 12 hours | Choir of small mouths |
| 30 hours | Abyssal archon |
| 72 hours | Elder god |

Each egg secretly chooses one of six species:

| Species | Appearance |
| --- | --- |
| Amber Child | Tentacles and scattered eyes |
| Pale Host | A tower of bone on many legs, with a ringed maw |
| The Sleet | A dim core and a restless swarm of eyes |
| Red Crown | A round body ringed with singing mouths |
| Drowned Lamp | A low wet sac with two huge eyes |
| Old Harvest | A shaggy star of knuckled points and claws |

**Creature notes** reveals the species after hatching, along with age,
generation, and mood. A dead creature stops aging.

## Cover screen

On supported phones, the secondary display shows the same pet and its meters.
It is read-only and shares the main screen's save. Android firmware controls
whether third-party apps may draw there, so cover support is not guaranteed.
The app works without a cover display.

## Build and install

This project uses plain Android SDK tools, Bash, Java 17, and `zip`; no Gradle.
Defaults are SDK build tools **34.0.0** and platform **android-30**, under
`$ANDROID_HOME` or `~/Android/Sdk`. Override with `BUILD_TOOLS_VERSION` and
`ANDROID_PLATFORM` if needed.

For a separate test pet, with no release signing credentials:

```sh
./test.sh
./build.sh preview
adb install -r build/preview/elderflip-preview.apk
```

The launcher calls this **Elderflip Preview**. It has its own app ID and save,
and cannot overwrite the normal app. It is a debuggable development build.
`./build.sh preview install` also installs it over USB.

For a release:

```sh
./build.sh unsigned  # build/unsigned/elderflip-unsigned.apk; needs signing
./build.sh           # build/release/elderflip.apk; prompts for signing password
adb install -r build/release/elderflip.apk
```

Release signing defaults to `~/.android/flipplayer.keystore`, alias `flipplayer`.
Set `PET_KEYSTORE` and `PET_KEY_ALIAS` for another key. The build accepts
`PET_KS_PASS` from your environment or prompts in a terminal; never commit it.
A missing keystore is generated for a new installation. Updates to an existing
installation must use its original signing key.

### Upgrading from Flip Pet

The name is now Elderflip, but the release app ID remains
`com.wesley.flippet` so a correctly signed update retains your pet. Install with
`adb install -r`; **do not uninstall the old app to upgrade**. The rename alone
does not require starting a new egg.

The save is a JSON file in private app storage. Uninstalling or clearing storage
removes it. There is no cloud backup or export.

## Checks

`./test.sh` runs deterministic care simulation tests, including long absences,
the exact death boundary, sleeping recovery, sickness, and equivalence between
frequent updates and one catch-up update.

With the preview installed and the phone open/unlocked,
`python3 tests/device_smoke.py` checks actual Android save migration, persistence,
and keypad actions. It temporarily replaces the preview save and restores it
when finished. It never accesses the release app's save or changes the clock.

See [the changelog](CHANGELOG.md) for 1.1 changes. The Reddit drafts in `docs/`
are launch materials, not posts that have already been published.
