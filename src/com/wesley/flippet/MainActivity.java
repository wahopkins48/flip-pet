package com.wesley.flippet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * The pet's home screen: the creature, four meters, and the four things you can
 * do about it. Only a D-pad and OK exist, so the actions are a row of focusable
 * buttons and the meters redraw themselves from local state every few seconds.
 */
public class MainActivity extends Activity {
    private static final long POLL_MS = 5000;
    /**
     * The first stage begins after this long, so the egg has something to do.
     * Package-visible because the cover screen draws the same hatch progress.
     */
    static final double HATCH_HOURS = 1.0;

    private TextView title;
    private TextView stageLine;
    private PetView pet;
    private TextView fedBar, joyBar, vitBar, wardBar;
    private TextView status;
    private TextView keys;
    private Button[] buttons;

    private final String[] actions = {"feed", "play", "ward", "sleep"};

    /**
     * The pet's brain. Shared per process so the menu, the cover, and this
     * screen always see the same creature; a private copy here would tick and
     * write a stale save back over whatever a new egg just wrote.
     */
    private LocalPet local;

    /** The cover panel, if this phone has one this app is allowed to draw on. */
    private DisplayManager displays;
    private DisplayManager.DisplayListener coverWatch;
    private CoverPet cover;

    private final Handler poll = new Handler();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh(false);
            poll.postDelayed(this, POLL_MS);
        }
    };

    /** How long an action's own line stays up before the mood line takes over again. */
    private static final long MESSAGE_MS = 6000;

    private boolean asleep;
    private boolean dead;
    private boolean busy;
    private long messageUntil;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        build();
        refresh(true);
        watchCover();
    }

    /**
     * The cover window can only be opened once this activity owns a real window
     * token. Doing it in onCreate fails with a BadTokenException every time.
     */
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) showCover();
    }

    @Override protected void onDestroy() {
        if (displays != null && coverWatch != null) displays.unregisterDisplayListener(coverWatch);
        hideCover();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        // Draw now rather than waiting for the next poll, so a change made from
        // the menu (a new egg, a new name) shows up the instant we come back.
        refresh(false);
        pet.setAnimating(true);
        poll.removeCallbacks(tick);
        poll.postDelayed(tick, POLL_MS);
    }

    @Override protected void onPause() {
        poll.removeCallbacks(tick);
        pet.setAnimating(false);
        if (local != null) local.flush();
        super.onPause();
    }

    /**
     * Watches for the cover panel coming up, which happens when the phone is
     * shut. The pet needs no work to appear on it beyond being there: the panel
     * is simply dark while the phone is open.
     */
    private void watchCover() {
        displays = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        coverWatch = new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int id) {
                runOnUiThread(() -> showCover());
            }

            @Override public void onDisplayRemoved(int id) {
                runOnUiThread(() -> hideCover());
            }

            @Override public void onDisplayChanged(int id) {
                runOnUiThread(() -> showCover());
            }
        };
        displays.registerDisplayListener(coverWatch, null);
    }

    private void showCover() {
        if (cover != null || displays == null) return;
        Display panel = findCover();
        if (panel == null) return;
        if (local == null) local = LocalPet.shared(this);
        try {
            CoverPet candidate = new CoverPet(this, panel, local);
            candidate.show();
            cover = candidate;
        } catch (RuntimeException e) {
            // Either this phone has no cover panel, or its ROM will not let an
            // ordinary app draw on one. The pet stays main-screen only, which is
            // the same thing it did before the cover screen existed.
            cover = null;
        }
    }

    private void hideCover() {
        if (cover == null) return;
        try {
            cover.dismiss();
        } catch (RuntimeException ignored) {
            // The panel went away underneath us; the window is already gone.
        }
        cover = null;
    }

    private Display findCover() {
        for (Display d : displays.getDisplays()) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY && d.isValid()) return d;
        }
        return null;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        title = Ui.header(this, "an unthought egg");
        root.addView(title);

        stageLine = Ui.centered(this, "", 12, Ui.DIM);
        stageLine.setSingleLine(true);
        stageLine.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(stageLine);

        pet = new PetView(this);
        root.addView(pet, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout meters = new LinearLayout(this);
        meters.setOrientation(LinearLayout.VERTICAL);
        meters.setPadding(6, 0, 6, 0);
        fedBar = meter();
        joyBar = meter();
        vitBar = meter();
        wardBar = meter();
        meters.addView(meterRow(fedBar, joyBar));
        meters.addView(meterRow(vitBar, wardBar));
        root.addView(meters);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        buttons = new Button[actions.length];
        for (int i = 0; i < actions.length; i++) {
            final String action = actions[i];
            Button b = Ui.button(this, label(action), v -> act(action));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(1, 0, 1, 0);
            bar.addView(b, p);
            buttons[i] = b;
        }
        root.addView(bar);

        status = Ui.centered(this, "waking something up", 14, Color.WHITE);
        status.setGravity(Gravity.CENTER);
        status.setPadding(4, 2, 4, 2);
        status.setLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(status);

        keys = Ui.keys(this, "Gentle | 1-4 act | BACK menu");
        root.addView(keys);
        setContentView(root);
        buttons[0].requestFocus();
    }

    private static String label(String action) {
        switch (action) {
            case "feed": return "Feed";
            case "play": return "Play";
            case "ward": return "Ward";
            default: return "Sleep";
        }
    }

    /** One meter: name, a block bar, and the number. Text stays legible where shapes would not. */
    private TextView meter() {
        TextView t = Ui.text(this, "", 12, Color.WHITE);
        t.setTypeface(Typeface.MONOSPACE);
        t.setSingleLine(true);
        t.setTextColor(Color.WHITE);
        return t;
    }

    /** Two meters side by side, to keep the stack short enough for the creature to be big. */
    private LinearLayout meterRow(TextView a, TextView b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private static String paint(String name, double value) {
        int filled = (int) Math.round(value * 6.0 / 100.0);
        StringBuilder sb = new StringBuilder(name).append(' ');
        for (int i = 0; i < 6; i++) sb.append(i < filled ? '#' : '.');
        return sb.append(String.format(" %3d", (int) Math.round(value))).toString();
    }

    private void show(JSONObject p) {
        asleep = p.optBoolean("asleep");
        dead = p.optBoolean("dead");
        int stage = p.optInt("stage", 0);

        String name = p.optString("name", "unnamed");
        title.setText(p.optBoolean("named") ? name
            : (stage <= 0 ? "an unthought egg" : p.optString("species", "Hatchling")));
        stageLine.setText(p.optString("stage_name", "") + "   " + p.optString("age_label", "")
            + (asleep ? "   asleep" : ""));
        keys.setText((p.optBoolean("survival") ? "Survival" : "Gentle") + " | 1-4 act | BACK menu");

        pet.setPet(stage, asleep, dead, p.optBoolean("sick"), p.optInt("phenotype", 0));
        pet.setHatchProgress(stage == 0
            ? (float) Math.min(1.0, p.optDouble("age_hours", 0.0) / HATCH_HOURS) : 1f);

        fedBar.setTextColor(0xFF7BD88F);
        joyBar.setTextColor(0xFFE0C060);
        vitBar.setTextColor(0xFF6BB0E8);
        wardBar.setTextColor(0xFFB388FF);
        fedBar.setText(paint("FED", p.optDouble("fed", 0)));
        joyBar.setText(paint("JOY", p.optDouble("joy", 0)));
        vitBar.setText(paint("LIFE", p.optDouble("vitality", 0)));
        wardBar.setText(paint("SALT", p.optDouble("wards", 0)));

        buttons[3].setText(asleep ? "Wake" : "Sleep");
        for (Button b : buttons) b.setEnabled(!dead);

        if (SystemClock.uptimeMillis() >= messageUntil) {
            status.setText(p.optString("mood", ""));
        }
        status.setTextColor(dead ? 0xFFD08080 : (p.optBoolean("sick") ? 0xFFD0E070 : Color.WHITE));
        if (p.optBoolean("danger")) {
            status.setText("Empty meter: " + p.optInt("grace_minutes") + " min left. Care now.");
            status.setTextColor(0xFFFFAA80);
        }
        if (p.optBoolean("save_failed")) {
            status.setText("Could not save. Check free storage.");
            status.setTextColor(0xFFFFAA80);
        }
    }

    private void refresh(boolean loud) {
        if (local == null) local = LocalPet.shared(this);
        show(local.view());
    }

    private void act(String action) {
        if (busy) return;
        // Sleep doubles as wake, matching the label on the button.
        final String wire = "sleep".equals(action) && asleep ? "wake" : action;
        busy = true;
        messageUntil = 0;
        status.setText("...");
        try {
            if (local == null) local = LocalPet.shared(this);
            JSONObject view = local.act(wire);
            show(view);
            // show() keeps the poll from overwriting this, for MESSAGE_MS.
            messageUntil = SystemClock.uptimeMillis() + MESSAGE_MS;
            String said = view.optString("message", "");
            if (!said.isEmpty() && !view.optBoolean("danger") && !view.optBoolean("save_failed")) status.setText(said);
        } catch (RuntimeException e) {
            status.setText("that did not work: " + e.getMessage());
        } finally {
            busy = false;
        }
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_4) {
            if (event.getRepeatCount() == 0 && !dead) act(actions[code - KeyEvent.KEYCODE_1]);
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (event.getRepeatCount() == 0) startActivity(new Intent(this, PetMenu.class));
            return true;
        }
        return super.onKeyDown(code, event);
    }
}
