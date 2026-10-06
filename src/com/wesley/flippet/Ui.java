package com.wesley.flippet;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the screens in code. Sizes are in raw pixels because the phone
 * is 240x320 with a density override, so dp-based sizes come out tiny.
 *
 * This phone has no touchscreen, only a D-pad, an OK key, BACK and a numeric
 * keypad, so everything actionable is focusable and carries a visible focus
 * block. Nothing depends on a tap.
 */
final class Ui {
    static final int ACCENT = 0xFFF4743B;
    static final int HIGHLIGHT = 0xFF6B2A10;
    static final int DIM = 0xFFB0B0B0;
    static final int MINE = 0xFF2E5C8A;

    private Ui() {}

    /** One row in a list: a label, optional detail line, and what to do on select. */
    static final class Row {
        final String label;
        final String detail;
        final Runnable action;

        Row(String label, String detail, Runnable action) {
            this.label = label;
            this.detail = detail;
            this.action = action;
        }

        Row(String label, Runnable action) {
            this(label, null, action);
        }
    }

    static TextView text(Context c, String s, int px, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        return t;
    }

    static TextView label(Context c, String s) {
        TextView t = text(c, s, 15, DIM);
        t.setPadding(8, 8, 8, 0);
        return t;
    }

    static TextView header(Context c, String s) {
        TextView t = text(c, s, 19, Color.BLACK);
        t.setBackgroundColor(ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(6, 2, 6, 2);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static EditText field(Context c, String value, String hint) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(0xFF707070);
        e.setTextColor(Color.WHITE);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setPadding(4, 4, 4, 4);
        return e;
    }

    static EditText body(Context c, String value, boolean multiline) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18);
        if (multiline) {
            e.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setHorizontalScrollBarEnabled(false);
        } else {
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        e.setPadding(4, 4, 4, 4);
        return e;
    }

    /** A footer telling the player which keys do what, since there is no touch. */
    static TextView keys(Context c, String what) {
        TextView t = text(c, what, 13, 0xFF909090);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(2, 3, 2, 3);
        return t;
    }

    /** Background that lights up when the view holds D-pad focus. */
    private static StateListDrawable focusBackground() {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(HIGHLIGHT));
        d.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(HIGHLIGHT));
        d.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(HIGHLIGHT));
        d.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return d;
    }

    /**
     * Makes any view reachable with the D-pad and activatable with OK, showing
     * a focus block so the player can see where they are.
     */
    static <T extends View> T keyable(T v, View.OnClickListener onClick) {
        v.setBackground(focusBackground());
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        v.setClickable(true);
        if (onClick != null) v.setOnClickListener(onClick);
        return v;
    }

    /** A chunky full-width button sized for the D-pad. */
    static android.widget.Button button(Context c, String label, View.OnClickListener onClick) {
        android.widget.Button b = new android.widget.Button(c);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, 19);
        b.setAllCaps(false);
        b.setPadding(0, 2, 0, 2);
        b.setMinHeight(48);
        keyable(b, onClick);
        return b;
    }

    /** A full-screen header + list. Returns the ListView; rows are set via {@link #setRows}. */
    static ListView listScreen(android.app.Activity a, String title) {
        return listScreen(a, title, null);
    }

    static ListView listScreen(android.app.Activity a, String title, String keyHint) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.addView(header(a, title));

        ListView list = new ListView(a);
        StateListDrawable sel = new StateListDrawable();
        sel.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(HIGHLIGHT));
        sel.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(HIGHLIGHT));
        sel.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        list.setSelector(sel);
        list.setDrawSelectorOnTop(false);
        list.setDivider(new ColorDrawable(0xFF303030));
        list.setDividerHeight(1);
        list.setItemsCanFocus(true);
        list.setFocusable(false);
        root.addView(list, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (keyHint != null) root.addView(keys(a, keyHint));

        a.setContentView(root);
        return list;
    }

    interface Render<T> {
        View view(Context c, T item);
    }

    interface OnPick<T> {
        void pick(T item);
    }

    static <T> void setRows(ListView list, List<T> items, Render<T> render, OnPick<T> onPick) {
        final List<T> data = new ArrayList<>(items);
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return data.size(); }
            @Override public Object getItem(int i) { return data.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override public View getView(int i, View convert, ViewGroup parent) {
                View row = render.view(parent.getContext(), data.get(i));
                // Rows handle OK themselves as well as through the list, because a
                // D-pad hands focus to the row rather than the list.
                keyable(row, v -> onPick.pick(data.get(i)));
                return row;
            }
        });
        list.setOnItemClickListener((p, v, pos, id) -> onPick.pick(data.get(pos)));
        if (!data.isEmpty()) list.setSelection(0);
        focusFirstRow(list);
    }

    /**
     * Puts the focus block on row one as soon as the list is laid out. The rows
     * hold the focus rather than the list, because a focused ListView eats the
     * first D-pad press and then leaves nothing clickable under the block.
     * Retries, since the first child does not exist until the list is measured.
     */
    static void focusFirstRow(ListView list) {
        new Handler().postDelayed(new Runnable() {
            int tries = 15;
            @Override public void run() {
                if (list.getChildCount() > 0) {
                    list.getChildAt(0).requestFocus();
                    return;
                }
                if (--tries > 0) list.postDelayed(this, 100);
            }
        }, 120);
    }

    static void setRows(ListView list, List<Row> rows) {
        setRows(list, rows, (Render<Row>) (c, r) -> {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(8, 8, 8, 8);
            TextView label = text(c, r.label, 22, Color.WHITE);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(label);
            if (r.detail != null) {
                TextView d = text(c, r.detail, 17, DIM);
                d.setSingleLine(true);
                d.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(d);
            }
            return row;
        }, r -> {
            if (r.action != null) r.action.run();
        });
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams grow() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    static TextView centered(Context c, String s, int px, int color) {
        TextView t = text(c, s, px, color);
        t.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        return t;
    }

    /** Logs keys and focus so keypad behaviour can be checked without a screen. */
    static boolean traceKey(android.app.Activity a, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) return false;
        View focused = a.getCurrentFocus();
        String where = focused == null ? "none"
            : focused.getClass().getSimpleName()
                + (focused instanceof TextView ? " '" + ((TextView) focused).getText() + "'" : "");
        android.util.Log.d("flipkeys", event.getKeyCode() + " focus=" + where);
        return false;
    }

    static void toast(Context c, String s) {
        android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_SHORT).show();
    }
}