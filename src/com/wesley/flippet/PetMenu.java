package com.wesley.flippet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/** BACK from the pet: name it, start a new egg, or read what the rules are. */
public class PetMenu extends Activity {
    private ListView list;
    private LocalPet local;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        local = LocalPet.shared(this);
        list = Ui.listScreen(this, getString(R.string.app_name), "OK picks  -  BACK returns");
        show();
    }

    private void show() {
        List<Ui.Row> rows = new ArrayList<>();
        rows.add(new Ui.Row("Name it", "or let it choose one", () -> askName()));
        boolean survival = local.view().optBoolean("survival");
        rows.add(new Ui.Row("Care: " + (survival ? "Survival" : "Gentle"),
            survival ? "empty meters can be fatal" : "slower needs, no death", this::chooseMode));
        boolean showCoverPet = CoverSettings.showPet(this);
        rows.add(new Ui.Row("Cover: " + (showCoverPet ? "Pet + clock" : "Phone clock"),
            showCoverPet ? "pet and time when closed" : "use the phone's own screen", this::chooseCover));
        rows.add(new Ui.Row("Creature notes", "what has come through", this::showCreature));
        rows.add(new Ui.Row("Begin a new egg", "the old one is not consulted", this::confirmNew));
        rows.add(new Ui.Row("How this works", null, this::showRules));
        Ui.setRows(list, rows);
    }

    private void chooseCover() {
        new AlertDialog.Builder(this)
            .setTitle("Outer screen")
            .setSingleChoiceItems(new String[]{"Phone clock", "Pet + clock"},
                CoverSettings.showPet(this) ? 1 : 0, (dialog, which) -> {
                    dialog.dismiss();
                    CoverSettings.setShowPet(this, which == 1);
                    show();
                })
            .setNegativeButton("Cancel", null).show();
    }

    private void askName() {
        final EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(18)});
        field.setHint("blank lets it choose");
        field.setTextColor(Color.WHITE);
        new AlertDialog.Builder(this)
            .setTitle("Name it")
            .setView(field)
            .setPositiveButton("OK", (d, w) -> name(text(field)))
            .setNegativeButton("Never mind", null)
            .show();
    }

    private void chooseMode() {
        new AlertDialog.Builder(this)
            .setTitle("Care mode")
            .setSingleChoiceItems(new String[]{"Gentle", "Survival"},
                local.view().optBoolean("survival") ? 1 : 0, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) { local.setSurvival(false); show(); }
                    else new AlertDialog.Builder(this)
                        .setTitle("Enable Survival?")
                        .setMessage("Original decay rates. Any meter empty for 15 minutes means permanent death, even while the app is closed. A fresh egg needs care within about five hours. Switching modes does not revive a dead pet.")
                        .setPositiveButton("Enable", (d, w) -> { local.setSurvival(true); show(); })
                        .setNegativeButton("Stay as is", null).show();
                })
            .setNegativeButton("Cancel", null).show();
    }

    private void showCreature() {
        org.json.JSONObject p = local.view();
        String species = p.optInt("stage") == 0 ? "The shell keeps its secret." : p.optString("species");
        new AlertDialog.Builder(this).setTitle("Creature notes")
            .setMessage(species + "\n\n" + p.optString("stage_name") + "\nAge: "
                + p.optString("age_label") + "\nGeneration: " + p.optInt("generation")
                + "\n\n" + p.optString("mood"))
            .setPositiveButton("Close", null).show();
    }

    private void confirmNew() {
        new AlertDialog.Builder(this)
            .setTitle("Begin a new egg?")
            .setMessage("Whatever was here goes back to the Others.")
            .setPositiveButton("Begin", (d, w) -> newEgg())
            .setNegativeButton("Never mind", null)
            .show();
    }

    private void showRules() {
        new AlertDialog.Builder(this)
            .setTitle("How this works")
            .setMessage("Gentle is the default: slower needs and no permanent death from neglect. You can put the phone away. Survival uses the original, faster decay and a 15-minute grace period once any meter is empty.\n\n"
                + "Feed it, play with it, lay down salt, let it sleep. Overfeed it "
                + "above 85 FED and it turns sick. Ward until SALT reaches 95 to cure it. Sleep restores LIFE. Feed and Play wake it; Ward lets it sleep.\n\n"
                + "Use the D-pad and OK, or keys 1 Feed, 2 Play, 3 Ward, 4 Sleep/Wake. BACK opens this menu.\n\n"
                + "It hatches after an hour and grows through six stages over three days. No network, notifications or account.")
            .setPositiveButton("Close", null)
            .show();
    }

    private static String text(EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    @Override protected void onPause() {
        local.flush();
        super.onPause();
    }

    /** Names it, then returns to the pet so the new name is visible at once. */
    private void name(String name) {
        if (local == null) local = LocalPet.shared(this);
        local.name(name);
        finish();
    }

    private void newEgg() {
        if (local == null) local = LocalPet.shared(this);
        local.newEgg();
        finish();
    }
}
