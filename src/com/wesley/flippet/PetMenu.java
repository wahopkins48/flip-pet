package com.wesley.flippet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
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
        list = Ui.listScreen(this, "Flip Pet", "OK picks  -  BACK returns");
        show();
    }

    private void show() {
        List<Ui.Row> rows = new ArrayList<>();
        rows.add(new Ui.Row("Name it", "or let it choose one", () -> askName()));
        rows.add(new Ui.Row("Begin a new egg", "the old one is not consulted", this::confirmNew));
        rows.add(new Ui.Row("How this works", null, this::showRules));
        Ui.setRows(list, rows);
    }

    private void askName() {
        final EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint("blank lets it choose");
        field.setTextColor(Color.WHITE);
        new AlertDialog.Builder(this)
            .setTitle("Name it")
            .setView(field)
            .setPositiveButton("OK", (d, w) -> name(text(field)))
            .setNegativeButton("Never mind", null)
            .show();
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
            .setMessage("Four meters drain with real time, so the pet keeps "
                + "suffering while the phone is off.\n\n"
                + "Feed it, play with it, lay down salt, let it sleep. Overfeed it "
                + "and it turns sick, and only fresh salt will cure that.\n\n"
                + "Leave a meter empty for fifteen minutes and it is gone. After an "
                + "hour it hatches, and it keeps growing for three days.")
            .setPositiveButton("Close", null)
            .show();
    }

    private static String text(EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    /** Names it, then returns to the pet so the new name is visible at once. */
    private void name(String name) {
        if (local == null) local = new LocalPet(this);
        local.name(name);
        finish();
    }

    private void newEgg() {
        if (local == null) local = new LocalPet(this);
        local.newEgg();
        finish();
    }
}