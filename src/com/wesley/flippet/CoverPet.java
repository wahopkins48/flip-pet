package com.wesley.flippet;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * The pet on the phone's cover screen: a read-only glance of the same creature
 * the main screen draws, so the thing is still visible while the phone is shut.
 *
 * The cover panel takes no touch input, so nothing here is interactive. Every
 * control stays on the main screen, and this only ever reads from the one
 * LocalPet the main screen owns, which keeps the two from disagreeing.
 *
 * A Presentation is the only thing that can draw on this panel. An Activity
 * asked to launch on it is silently sent to the main display instead.
 */
final class CoverPet extends Presentation {
    private static final long POLL_MS = 5000;
    private static final int BG = 0xFF07040C;
    private static final int ACCENT = 0xFFB388FF;

    private final LocalPet local;
    private final TextView name;
    private final TextView meters;
    private final PetView pet;

    private final Handler poll = new Handler();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            poll.postDelayed(this, POLL_MS);
        }
    };

    CoverPet(Context host, Display display, LocalPet local) {
        super(host, display);
        this.local = local;

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(3, 3, 3, 3);

        name = new TextView(host);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTextColor(ACCENT);
        name.setTextSize(10f);
        root.addView(name, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pet = new PetView(host);
        root.addView(pet, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        meters = new TextView(host);
        meters.setGravity(Gravity.CENTER);
        meters.setSingleLine(true);
        meters.setTypeface(Typeface.MONOSPACE);
        meters.setTextColor(Color.WHITE);
        meters.setTextSize(7f);
        root.addView(meters, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override protected void onStart() {
        super.onStart();
        render();
        poll.removeCallbacks(tick);
        poll.postDelayed(tick, POLL_MS);
    }

    @Override protected void onStop() {
        poll.removeCallbacks(tick);
        super.onStop();
    }

    private void render() {
        JSONObject p = local.view();
        int stage = p.optInt("stage", 0);
        boolean dead = p.optBoolean("dead");
        pet.setPet(stage, p.optBoolean("asleep"), dead, p.optBoolean("sick"), p.optInt("phenotype", 0));
        pet.setHatchProgress(stage == 0
            ? (float) Math.min(1.0, p.optDouble("age_hours", 0.0) / MainActivity.HATCH_HOURS)
            : 1f);
        name.setText(p.optBoolean("named") ? p.optString("name")
            : (stage <= 0 ? "an unthought egg" : p.optString("species", "Hatchling")));
        meters.setText(compact(p));
        meters.setTextColor(dead ? 0xFFD08080
            : (p.optBoolean("sick") ? 0xFFD0E070 : Color.WHITE));
    }

    /** All four meters in the space of one short line: F70 J70 L80 S75. */
    private static String compact(JSONObject p) {
        return "F" + whole(p.optDouble("fed"))
            + " J" + whole(p.optDouble("joy"))
            + " L" + whole(p.optDouble("vitality"))
            + " S" + whole(p.optDouble("wards"));
    }

    private static String whole(double v) {
        return String.valueOf((int) Math.round(v));
    }
}