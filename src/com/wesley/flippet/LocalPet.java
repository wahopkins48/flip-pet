package com.wesley.flippet;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * The pet itself: four meters, six stages, and a mood line.
 *
 * There is no server and no tick loop. Every read folds however long it has been
 * since the last touch into the saved numbers, including time in a pocket.
 * Gentle care lets it wait safely; Survival makes that absence matter.
 *
 * State is one JSON file in the app's private storage. Nothing leaves the phone.
 */
final class LocalPet {
    /** The four meters, all "higher is better", matching the four buttons. */
    private static final String[] STATS = {"fed", "joy", "vitality", "wards"};

    /** Hours of age at which the creature stops being what it was. */
    private static final double[] STAGE_HOURS = {0.0, 1.0, 4.0, 12.0, 30.0, 72.0};
    private static final String[] STAGE_NAMES = {
        "Egg, unthought", "Hatchling", "Writhing thing",
        "Choir of small mouths", "Abyssal archon", "Elder god",
    };

    /** What hatches out. All six come from the same egg, chosen the moment it begins. */
    private static final String[] SPECIES = {
        "Amber Child", "Pale Host", "The Sleet", "Red Crown", "Drowned Lamp", "Old Harvest",
    };

    /**
     * Sickness only lifts once the salt has been laid on properly, which is above
     * the level a fresh pet starts with, so it has to be earned with the Ward button.
     */
    private static final double WARD_CURE = 95.0;
    private static final String[] NAME_HEADS = {
        "Vur", "Xoth", "Gla", "Ny", "Zer", "Mor", "Ith", "Qua",
        "Bra", "Sel", "Kha", "Vex", "Oth", "Ygg", "Ur", "Tz",
    };
    private static final String[] NAME_TAILS = {
        "ith", "or", "un", "ax", "esh", "og", "ir", "um", "al", "oth",
    };

    private static final String[] CAUSE_FED = {
        "starved. It forgot what mouths were for.",
    };
    private static final String[] CAUSE_JOY = {
        "unhappy. It stopped pretending, and then stopped.",
    };
    private static final String[] CAUSE_VITALITY = {
        "exhausted. It lay down and became architecture.",
    };
    private static final String[] CAUSE_WARDS = {
        "unwarded. Something else came through the gap first.",
    };

    private static final String[] MOOD_GOOD = {
        "it watches you, and permits it",
        "content, in the way a trap is content",
        "it purrs in a register you feel in your teeth",
        "it is being good at you",
    };
    private static final String[] MOOD_LONELY = {
        "it has not seen a face in some time",
        "it is rehearsing your name badly",
        "the loneliness has developed opinions",
    };
    private static final String[] MOOD_HUNGRY = {
        "it is thinking about the moon, or something under the moon",
        "it has started chewing the floor",
        "it wants to be fed and it wants it now",
    };
    private static final String[] MOOD_DIRTY = {
        "the salt has gone clotted",
        "there is a smell coming off it that has a direction",
        "it is attracting the small attentions",
    };
    private static final String[] MOOD_TIRED = {
        "it is running on the memory of sleep",
        "it keeps sitting down halfway through standing up",
        "it needs to lie down in the dark for a while",
    };
    private static final String[] MOOD_SICK = {
        "it is accruing paradoxes",
        "two of it are arguing about which one of them is real",
        "it has a wound that opens when it is looked at",
    };
    private static final String[] MOOD_ASLEEP = {
        "it dreams, which is a kind of risk",
        "sleeping. quietly. so far",
        "it is elsewhere and not being quiet about it",
    };
    private static final String[] MOOD_DEAD = {
        "it is gone to the Others", "there is nothing here now", "at rest, at last",
    };

    private final File file;
    private JSONObject state;
    private boolean saveFailed;
    private long lastWrite;

    private static LocalPet shared;

    /**
     * The one pet per process. New egg, naming, the cover panel and the main
     * screen all have to see the same creature, and they do only if it is one
     * object. A second LocalPet would hold a stale copy of the save and, when it
     * next ticked, write that stale copy straight back over the real one.
     */
    static LocalPet shared(Context c) {
        if (shared == null) shared = new LocalPet(c);
        return shared;
    }

    LocalPet(Context c) {
        file = new File(c.getFilesDir(), "pet.json");
        state = read();
        // Persist the very first egg before the process can be reclaimed.
        write();
    }

    // ---------------------------------------------------------------- state

    private static JSONObject fresh(long now) {
        JSONObject s = new JSONObject();
        put(s, "name", "");
        put(s, "born", now);
        put(s, "fed", 70.0);
        put(s, "joy", 70.0);
        put(s, "vitality", 80.0);
        put(s, "wards", 75.0);
        put(s, "asleep", false);
        put(s, "sick", false);
        put(s, "dead", false);
        put(s, "dead_at", 0L);
        put(s, "survival", false);
        put(s, "cause", "");
        put(s, "neglect", 0);
        put(s, "last", now);
        put(s, "generation", 1);
        put(s, "phenotype", new Random(now).nextInt(SPECIES.length));
        return s;
    }

    private JSONObject read() {
        if (!file.exists()) return fresh(nowSeconds());
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            byte[] bytes = new byte[(int) in.length()];
            in.readFully(bytes);
            JSONObject s = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (!s.has("born")) return fresh(nowSeconds());
            // Any field added by a later version gets its starting value here,
            // so an old save never breaks the app.
            JSONObject blank = fresh(nowSeconds());
            for (java.util.Iterator<String> it = blank.keys(); it.hasNext(); ) {
                String key = it.next();
                if (!s.has(key)) put(s, key, blank.get(key));
            }
            return s;
        } catch (IOException | JSONException e) {
            // A save we cannot read is a save we start over from. Losing a pet is
            // better than refusing to open the app.
            return fresh(nowSeconds());
        }
    }

    /** Writes via a temporary file, so a crash mid-write cannot leave half a pet. */
    private void write() {
        File tmp = new File(file.getParentFile(), "pet.json.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(json(state).getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            saveFailed = true;
            return;
        }
        saveFailed = !tmp.renameTo(file);
        if (saveFailed) tmp.delete();
        else lastWrite = nowSeconds();
    }

    void flush() { write(); }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    // ----------------------------------------------------------------- rules

    /** Android's org.json throws on put, and none of these keys can fail. */
    private static void put(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String json(JSONObject target) {
        try {
            return target.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    /**
     * Folds elapsed wall-clock time into the meters, then applies the threshold
     * rules. Runs on every read, not only when the clock has moved, or a pet
     * sitting at zero would not be noticed until the next minute boundary.
     */
    private void tick() {
        long now = nowSeconds();
        if (state.optBoolean("dead")) return;
        long last = state.optLong("last", now);
        // A clock correction backwards must not double-charge time later or de-age the pet.
        if (now < last) {
            put(state, "born", state.optLong("born", now) + now - last);
            put(state, "last", now);
            write();
            return;
        }
        double[] meters = new double[STATS.length];
        for (int i = 0; i < meters.length; i++) meters[i] = state.optDouble(STATS[i], 0);
        PetRules.Result result = PetRules.advance(meters, state.optBoolean("asleep"),
            state.optBoolean("sick"), state.optBoolean("survival"),
            state.optDouble("neglect", 0), now - last);
        for (int i = 0; i < meters.length; i++) put(state, STATS[i], result.meters[i]);
        put(state, "neglect", result.neglect);
        put(state, "last", now);
        if (result.dead) {
            put(state, "dead", true);
            put(state, "dead_at", last + (long) Math.ceil(result.advanced));
            put(state, "cause", causeFor(STATS[result.cause]));
            put(state, "asleep", false);
        }
        // Screens poll every five seconds. Avoid fsync on every glance; actions and
        // lifecycle exits still save immediately, and elapsed time can be replayed.
        if (result.dead || saveFailed || now - lastWrite >= 60) write();
    }

    private static String causeFor(String stat) {
        String[] lines = CAUSE_FED;
        if ("joy".equals(stat)) lines = CAUSE_JOY;
        else if ("vitality".equals(stat)) lines = CAUSE_VITALITY;
        else if ("wards".equals(stat)) lines = CAUSE_WARDS;
        return lines.length > 0 ? lines[0] : "ended, for reasons unrecorded";
    }

    private int stageFor(double ageHours) {
        int stage = 0;
        for (int i = 0; i < STAGE_HOURS.length; i++) {
            if (ageHours >= STAGE_HOURS[i]) stage = i;
        }
        return stage;
    }

    private static String ageLabel(double ageHours) {
        if (ageHours < 1) return Math.max(0, (int) (ageHours * 60)) + " min";
        if (ageHours < 48) return (int) ageHours + " h";
        return (int) (ageHours / 24.0) + " d";
    }

    private String[] moodLines() {
        if (state.optBoolean("dead")) return MOOD_DEAD;
        if (state.optBoolean("asleep")) return MOOD_ASLEEP;
        if (state.optBoolean("sick")) return MOOD_SICK;
        if (state.optDouble("fed", 0.0) <= 20) return MOOD_HUNGRY;
        if (state.optDouble("wards", 0.0) <= 20) return MOOD_DIRTY;
        if (state.optDouble("vitality", 0.0) <= 20) return MOOD_TIRED;
        if (state.optDouble("joy", 0.0) <= 20) return MOOD_LONELY;
        return MOOD_GOOD;
    }

    private String mood() {
        String[] options = moodLines();
        // Stable per pet per hour, so repeated refreshes do not shuffle the line.
        long seed = state.optLong("born") / 3600 + nowSeconds() / 3600;
        return options[new Random(seed).nextInt(options.length)];
    }

    // ------------------------------------------------------------------ view

    /** The whole pet as the screens want it. Named to match the old bridge view. */
    JSONObject view() {
        tick();
        boolean dead = state.optBoolean("dead");
        long at = dead ? state.optLong("dead_at", 0) : nowSeconds();
        if (at <= 0) at = state.optLong("last", nowSeconds());
        double ageHours = Math.max(0, (at - state.optLong("born")) / 3600.0);
        int stage = stageFor(ageHours);
        String message = dead ? state.optString("cause") : mood();

        String name = state.optString("name");
        JSONObject v = new JSONObject();
        put(v, "name", name.isEmpty() ? "unnamed" : name);
        put(v, "named", !name.isEmpty());
        put(v, "stage", stage);
        put(v, "stage_name", STAGE_NAMES[stage]);
        put(v, "age_hours", Math.round(ageHours * 100.0) / 100.0);
        put(v, "age_label", ageLabel(ageHours));
        put(v, "fed", round(state.optDouble("fed", 0)));
        put(v, "joy", round(state.optDouble("joy", 0)));
        put(v, "vitality", round(state.optDouble("vitality", 0)));
        put(v, "wards", round(state.optDouble("wards", 0)));
        put(v, "asleep", state.optBoolean("asleep"));
        put(v, "sick", state.optBoolean("sick"));
        put(v, "dead", dead);
        put(v, "cause", state.optString("cause"));
        put(v, "neglect", state.optInt("neglect"));
        put(v, "survival", state.optBoolean("survival"));
        put(v, "save_failed", saveFailed);
        put(v, "danger", !dead && state.optBoolean("survival") && anyEmpty());
        put(v, "grace_minutes", Math.max(0, (int) Math.ceil(
            (PetRules.GRACE_SECONDS - state.optDouble("neglect", 0)) / 60.0)));
        put(v, "generation", state.optInt("generation", 1));
        put(v, "phenotype", phenotype());
        put(v, "species", SPECIES[phenotype()]);
        put(v, "mood", message);
        put(v, "message", message);
        return v;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** The hatched species, kept stable and in range even against an old save. */
    private int phenotype() {
        int given = state.optInt("phenotype", 0);
        return ((given % SPECIES.length) + SPECIES.length) % SPECIES.length;
    }

    // --------------------------------------------------------------- actions

    JSONObject act(String action) {
        tick();

        if (state.optBoolean("dead")) {
            return withMessage("there is nothing left to " + action);
        }

        boolean woke = state.optBoolean("asleep")
            && ("feed".equals(action) || "play".equals(action));
        if (woke) put(state, "asleep", false);

        String said;
        if ("feed".equals(action)) {
            if (state.optDouble("fed", 0.0) > 85) {
                // Overfeeding is the one mistake that creates a problem rather
                // than solving one.
                put(state, "sick", true);
                said = woke ? "it wakes, overeats, and sickens"
                    : "too much. it is keeping the extra";
            } else {
                put(state, "fed", clamp(state.optDouble("fed", 0.0) + 38));
                put(state, "joy", clamp(state.optDouble("joy", 0.0) + 4));
                said = woke ? "it wakes to eat. the room quiets"
                    : "it eats. the room gets quieter";
            }
        } else if ("play".equals(action)) {
            put(state, "joy", clamp(state.optDouble("joy", 0.0) + 30));
            put(state, "fed", clamp(state.optDouble("fed", 0.0) - 8));
            put(state, "vitality", clamp(state.optDouble("vitality", 0.0) - 12));
            said = woke ? "it wakes to play. you regret it"
                : "it plays. you regret starting this";
        } else if ("ward".equals(action)) {
            put(state, "wards", clamp(state.optDouble("wards", 0.0) + 35));
            put(state, "joy", clamp(state.optDouble("joy", 0.0) - 4));
            said = "the salt holds. for now";
            if (state.optBoolean("sick") && state.optDouble("wards", 0) >= WARD_CURE) {
                put(state, "sick", false);
                said = "fresh salt. only one of it remains";
            }
        } else if ("sleep".equals(action)) {
            if (state.optBoolean("asleep")) {
                said = "it is already asleep";
            } else {
                put(state, "asleep", true);
                said = "it curls up and stops looking at you";
            }
        } else if ("wake".equals(action)) {
            if (!state.optBoolean("asleep")) {
                said = "it is already awake and watching";
            } else {
                put(state, "asleep", false);
                said = "it opens every eye at once";
            }
        } else {
            return withMessage("the pet does not understand " + action);
        }

        put(state, "last", nowSeconds());
        if (!anyEmpty()) put(state, "neglect", 0);
        write();
        return withMessage(said);
    }

    /**
     * Names the pet. A blank name lets it choose one, because being asked to
     * decide is a kindness and naming things is the whole point.
     */
    JSONObject name(String name) {
        tick();
        String trimmed = name == null ? "" : name.trim();
        String said;
        if (trimmed.isEmpty()) {
            Random rng = new Random(nowSeconds());
            trimmed = NAME_HEADS[rng.nextInt(NAME_HEADS.length)]
                + NAME_TAILS[rng.nextInt(NAME_TAILS.length)];
            said = "it will answer to " + trimmed;
        } else {
            said = "it accepts the name " + trimmed;
        }
        put(state, "name", trimmed.length() > 18 ? trimmed.substring(0, 18) : trimmed);
        write();
        return withMessage(said);
    }

    /** Begins a new egg, keeping the generation count from the last one. */
    JSONObject newEgg() {
        int generation = state.optInt("generation", 1) + 1;
        boolean survival = state.optBoolean("survival");
        state = fresh(nowSeconds());
        put(state, "survival", survival);
        put(state, "generation", generation);
        write();
        return withMessage("something is in the egg");
    }

    JSONObject setSurvival(boolean survival) {
        tick(); // Account for time under the old rules before changing modes.
        put(state, "survival", survival);
        put(state, "neglect", 0);
        write();
        return withMessage(survival ? "the stakes are now real" : "it can wait for you");
    }

    private boolean anyEmpty() {
        for (String stat : STATS) if (state.optDouble(stat, 0) <= 0) return true;
        return false;
    }

    private JSONObject withMessage(String message) {
        try {
            JSONObject v = view();
            v.put("message", message);
            return v;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
