# Flip Pet

A small eldritch pet for a flip phone. Something lives in the egg, it grows
through six stages over three days, and it keeps suffering while the phone is
switched off.

Everything is on the phone. There is no server, no account, and no network
permission. Delete the app and the pet is gone for good.

## Install

You need an Android phone with a D-pad. Get `flip-pet.apk` onto it, then either
sideload it from the file, or install over USB:

```
adb install flip-pet.apk
```

Android will ask whether you trust the source. Tap through. The app asks for no
permissions at all.

## Playing

Four buttons, one for each thing the creature needs:

| Button | Does |
| --- | --- |
| **Feed** | raises FED, a little JOY |
| **Play** | raises JOY, costs FED and LIFE |
| **Ward** | lays down salt, raises SALT, a little cost to JOY |
| **Sleep** | sleeps, or wakes if already asleep |

**OK** presses the focused button. **BACK** opens the menu: name it, begin a new
egg, or read the rules.

Feeding a pet that is already full makes it **sick**, and sickness doubles how
fast LIFE and SALT drain. Only more salt cures it, so overfeeding is the one
mistake that creates a problem rather than solving one.

Any meter sitting at empty for fifteen minutes kills it. That is not a
punishment for being busy, it is the whole point: the thing is counting the
hours you are not looking at it.

## Cover screen

If the phone has a second screen, the same pet appears on it whenever you shut
the phone: name, creature, and the four meters as one short line.

It is a read-only window. The cover panel takes no touch input, so every control
stays on the main screen, and the cover is only ever a view onto the same save
rather than a second pet that could drift out of step.

This still needs no permissions. Phones without a second screen, or whose ROM
refuses to let an app draw on one, get no cover window and lose nothing else.

## How it grows

| Age | Stage |
| --- | --- |
| under an hour | Egg, unthought |
| 1 hour | Hatchling |
| 4 hours | Writhing thing |
| 12 hours | Choir of small mouths |
| a day and a half | Abyssal archon |
| 3 days | Elder god |

Six species come out of the one egg. Which one hatches is decided when the egg
begins, and all six are drawn by the app itself rather than from images.

| Species | Looks like |
| --- | --- |
| Amber Child | the original: a wobbling ball of tentacles and scattered eyes |
| Pale Host | a swaying tower of bone on many legs, with a ringed maw |
| The Sleet | a dim core with a swarm of eyes that will not hold still |
| Red Crown | a round body ringed with small mouths that all sing |
| Drowned Lamp | a low wet sac with two huge eyes and nothing but patience |
| Old Harvest | a shaggy star of knuckled points, claws at every tip |

Sleeping is worth it. Vitality rises while the pet sleeps and drains while it
is awake, so a creature that never rests will eventually die of exhaustion
rather than hunger.

## Naming it

BACK, then **Name it**. If you leave the field blank it will pick a name for
itself.

## Notes

The save is one JSON file in the app's private storage. Uninstalling removes it.
There is no cloud copy and no export.

Built with plain SDK tools, no Gradle: `./build.sh` writes
`build/flip-pet.apk`, and `./build.sh install` also pushes it over adb.

Signing uses a keystore that lives in your own home directory, never in this
repository. Export its passphrase before building (or let the build prompt for
it):

```
export PET_KS_PASS=<your keystore passphrase>
```