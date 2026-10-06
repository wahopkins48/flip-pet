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
 * since the last touch into the saved numbers, so the creature keeps suffering
 * while the phone is switched off or in a pocket. Its condition is always a
 * function of how long it has been ignored.
 *
 * State is one JSON file in the app's private storage. Nothing leaves the phone.
 */
final class LocalPet {
    /** The four meters, all "higher is better", matching the four buttons. */
    private static final String[] STATS = {"fed", "joy", "vitality", "wards"};

    /**
     * Points lost per hour of neglect. Needs bite slowly enough that a few hours
     * of real life is survivable, fast enough that a forgotten pet actually suffers.
     * Vitality rises while asleep, which is what makes sleep worth doing.
     */
    private static final double[] DECAY_AWAKE = {14.0, 10.0, 8.0, 9.0};
    private static final double[] DECAY_ASLEEP = {7.0, 3.0, -13.0, 5.0};

    /** Hours of age at which the creature stops being what it was. */
    private static final double[] STAGE_HOURS = {0.0, 1.0, 4.0, 12.0, 30.0, 72.0};
    private static final String[] STAGE_NAMES = {
        "Egg, unthought", "Hatchling", "Writhing thing",
        "Choir of small mouths", "Abyssal archon", "Elder god",
    };

    /**
     * Sickness only lifts once the salt has been laid on properly, which is above
     * the level a fresh pet starts with, so it has to be earned with the Ward button.
     */
    private static final double WARD_CURE = 95.0;
    /** How long any meter may sit at zero before the pet is gone. */
    private static final int NEGLECT_GRACE = 15 * 60;
    /**
     * A single request may not fold in more than this much time, so that reopening
     * the app after a week does not skip straight past a hundred hours of misery.
     */
    private static final int MAX_ELAPSED = 48 * 3600;

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

    LocalPet(Context c) {
        file = new File(c.getFilesDir(), "pet.json");
        state = read();
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
        put(s, "cause", "");
        put(s, "neglect", 0);
        put(s, "last", now);
        put(s, "generation", 1);
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
            return;
        }
        if (!tmp.renameTo(file)) tmp.delete();
    }

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
        long elapsed = Math.max(0, Math.min(MAX_ELAPSED, now - state.optLong("last", now)));
        boolean changed = false;
        boolean asleep = state.optBoolean("asleep");

        if (elapsed > 0) {
            double hours = elapsed / 3600.0;
            double[] rates = asleep ? DECAY_ASLEEP : DECAY_AWAKE;
            // Sickness doubles the slow bleed on vitality and wards.
            double strain = state.optBoolean("sick") ? 2.0 : 1.0;
            for (int i = 0; i < STATS.length; i++) {
                double rate = rates[i];
                if ((STATS[i].equals("vitality") || STATS[i].equals("wards")) && rate > 0) {
                    rate *= strain;
                }
                put(state, STATS[i], clamp(state.optDouble(STATS[i], 0.0) - rate * hours));
            }
            put(state, "last", now);
            changed = true;
        }

        boolean wasSick = state.optBoolean("sick");
        boolean dead = state.optBoolean("dead");
        String starved = "";
        for (String stat : STATS) {
            if (state.optDouble(stat, 0.0) <= 0.0) {
                starved = stat;
                break;
            }
        }
        if (!dead && starved.isEmpty()) {
            if (state.optInt("neglect") != 0) {
                put(state, "neglect", 0);
                changed = true;
            }
        } else if (!dead) {
            int neglect = state.optInt("neglect") + (int) elapsed;
            put(state, "neglect", neglect);
            if (neglect >= NEGLECT_GRACE) {
                put(state, "dead", true);
                put(state, "cause", causeFor(starved));
                put(state, "asleep", false);
                changed = true;
            }
        }
        // Only a sickness that was already there can be cured, otherwise
        // overfeeding a ward-healthy pet would leave no trace at all.
        if (wasSick && !dead && state.optDouble("wards", 0.0) >= WARD_CURE) {
            put(state, "sick", false);
            changed = true;
        }
        if (changed) write();
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
        double ageHours = (nowSeconds() - state.optLong("born")) / 3600.0;
        int stage = stageFor(ageHours);
        boolean dead = state.optBoolean("dead");
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
        put(v, "generation", state.optInt("generation", 1));
        put(v, "mood", message);
        put(v, "message", message);
        return v;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // --------------------------------------------------------------- actions

    JSONObject act(String action) {
        tick();

        if (state.optBoolean("dead")) {
            return withMessage("there is nothing left to " + action);
        }

        String said;
        if ("feed".equals(action)) {
            if (state.optDouble("fed", 0.0) > 85) {
                // Overfeeding is the one mistake that creates a problem rather
                // than solving one.
                put(state, "sick", true);
                said = "too much. it is keeping the extra";
            } else {
                put(state, "fed", clamp(state.optDouble("fed", 0.0) + 38));
                put(state, "joy", clamp(state.optDouble("joy", 0.0) + 4));
                said = "it eats. the room gets quieter";
            }
        } else if ("play".equals(action)) {
            put(state, "joy", clamp(state.optDouble("joy", 0.0) + 30));
            put(state, "fed", clamp(state.optDouble("fed", 0.0) - 8));
            put(state, "vitality", clamp(state.optDouble("vitality", 0.0) - 12));
            said = "it plays. you regret starting this";
        } else if ("ward".equals(action)) {
            put(state, "wards", clamp(state.optDouble("wards", 0.0) + 35));
            put(state, "joy", clamp(state.optDouble("joy", 0.0) - 4));
            said = "the salt holds. for now";
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
        state = fresh(nowSeconds());
        put(state, "generation", generation);
        write();
        return withMessage("something is in the egg");
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