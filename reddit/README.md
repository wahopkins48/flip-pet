# Elderflip

**A small cosmic horror that a whole subreddit raises together.**

Elderflip puts one strange creature in a post. Anyone in the community can
feed it, play with it, lay down salt, or put it to sleep, and everyone sees
the same creature change. It starts as an unthought egg, hatches after an
hour, and if the community keeps it well, it becomes an Elder god after three
days. Then it ascends, and a new egg appears in a new post.

It is built for communities that want a light, shared ritual: something to
check on with your morning coffee, argue about in the comments ("STOP FEEDING
IT, IT'S AT 92"), and watch grow into something unknowable together.

**Who it is for:** any subreddit that wants a gentle, recurring community
game. Moderators install it once; it lays its own eggs after that.

**Important to know before installing:**

- Elderflip creates posts on its own. On install it posts the first egg, and
  each time a creature ascends or dies it posts the next one (about once every
  three days). Moderators can turn automatic eggs off.
- Members can only press four buttons. There is no text entry, chat, or
  uploaded content, so there is nothing new to moderate.
- Gentle care is the default: needs drain slowly, and the creature dies only if
  a need stays empty for 6 hours. Survival care is an optional moderator setting
  for communities that want higher stakes.
- Elderflip stores no personal information. See [Data and privacy](#data-and-privacy).

## How to play

Open an Elderflip post. The creature is drawn and animated right in the post.
Below it are four meters and four buttons.

| Meter | Means | Raise it with |
| --- | --- | --- |
| **FED** | Hunger | **Feed** (+FED, a little JOY) |
| **JOY** | Happiness | **Play** (+JOY, but costs FED and LIFE) |
| **LIFE** | Rest | **Sleep** (LIFE recovers while it sleeps; other needs drain more slowly) |
| **SALT** | Wards against what lives outside | **Ward** (+SALT, costs a little JOY) |

- **One turn at a time.** Each member gets one action, then waits a short
  while (10 minutes by default) before their next turn. The post shows when
  your next turn is ready.
- **Too much food makes it sick.** Feeding above 85 FED sickens it instead of
  feeding it, and the Feed button warns you when it is full. A sick creature
  loses LIFE and SALT twice as fast.
- **Cure sickness with fresh salt.** Ward until SALT reaches 95. Salt that was
  already down does not count.
- **Feed and Play wake it.** Ward lets it keep sleeping. Wake it on purpose
  with the Wake button.
- **It grows on a clock.** Egg, Hatchling (1 hour), Writhing thing (4 hours),
  Choir of small mouths (12 hours), Abyssal archon (30 hours), and Elder god
  (3 days). Six different species can hatch from the same egg.
- **Everyone sees the same creature.** When someone acts, everyone viewing
  the post sees it happen within a moment. The post also shows a short,
  anonymous history, like "fed 3 min ago · warded 10 min ago".
- **Logged out?** You can watch. Tapping a button asks you to log in.

When the creature becomes an Elder god, the post stays up as a record of
what the community raised, with a button to visit the newest egg.

## For moderators

### Install

1. Install Elderflip from the Reddit App Directory into your subreddit.
2. The first egg is posted automatically. That's all the setup there is.

### Settings

Find these in your subreddit's app settings for Elderflip.

| Setting | Default | What it does |
| --- | --- | --- |
| **Care rules for new eggs** | Gentle | **Gentle:** needs drain slowly, and the creature dies 6 hours after any meter empties. **Survival:** needs drain four times faster, and the creature dies 15 minutes after any meter empties. Applies from the next egg. |
| **Minutes between turns for each member** | 10 | How long each member waits between actions (1 to 240). Longer waits make each choice matter more in large communities. |
| **Lay the next egg automatically** | On | When a creature ascends or dies, post the next egg. Turn off to lay eggs only by hand. |

### Moderator tools

These appear in the moderator menu.

- **Elderflip: lay a new egg** (subreddit menu). Posts a new egg and makes it
  the community's creature. If the current creature is still alive, you are
  asked to confirm. The old creature "goes back to the Others": its post stays,
  but nobody can tend it.
- **Elderflip: give it a new name** (on an Elderflip post). Picks a new name
  from Elderflip's built-in list. Names never come from free text.

To remove a creature, remove or delete its post as usual. Deleting a post
deletes that creature's data.

### Survival mode

In Survival, an empty meter starts a 15-minute grace period. The post shows a
warning with the minutes left. If nobody helps in time, the creature dies, its
post becomes a memorial, and (if automatic eggs are on) a new egg is laid.

## Data and privacy

Elderflip keeps the minimum it needs and nothing about who you are.

- **Stored per post:** the creature's name, species, meters, age, how many
  times it has been cared for in total, and the last six actions (action and
  time only, no usernames).
- **Stored per member:** only a short "your next turn" timer, keyed to your
  account ID so you cannot act twice. It deletes itself when your wait ends
  (at most four hours).
- **Not stored:** usernames, avatars, comments, votes, or anything you type.
  Live updates sent to viewers contain only the creature's state.
- **No outside services.** Elderflip makes no network requests outside Reddit,
  uses no AI, and shows no ads.
- **Deletions:** deleting an Elderflip post deletes its creature's data.

## Support

Questions, bugs, or ideas: message the developer, u/Wesley-from-Crawled.

## Changelog

### First release

- First release: one community creature per post, four care actions with a
  per-member turn timer, six species and six stages over three days, live
  updates for everyone watching, Gentle and Survival care, automatic next
  eggs, and moderator tools to lay a new egg or rename the creature.

## For developers

Elderflip is a [Devvit Web](https://developers.reddit.com/docs) app: a plain
Node server (`src/server`), a canvas client with no framework (`src/client`),
and pure care rules shared by both (`src/shared`).

- `npm test` checks types, lint and `devvit.json`, runs the rule and server
  tests, and builds.
- `npm run playtest [r/yoursub]` uploads and installs to a test subreddit.
- `npm run publish` builds, uploads and submits for app review.

The care rules in `src/shared/pet.ts` are pure functions of a saved state
and a clock: there is no tick loop. Every read folds the time since the last
change into the meters, so checking once a minute or once a day gives the
same creature. A scheduled task every five minutes notices a creature that
ascends or dies while nobody is looking.
