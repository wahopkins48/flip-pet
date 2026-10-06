package com.wesley.flippet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.view.View;

import java.util.Random;

/**
 * Draws the pet.
 *
 * There are no image assets: the creature is Canvas primitives so that it can
 * wobble, blink and sprout more eyes as it ages, and so it fits whatever box it
 * is given on a 240x320 screen. Everything is sized off min(width, height) so
 * the same code works at any resolution.
 */
final class PetView extends View {
    private static final int BG = 0xFF07040C;
    private static final int FLESH = 0xFF2E1247;
    private static final int FLESH_LIT = 0xFF4A1E6B;
    private static final int FLESH_DEAD = 0xFF231733;
    private static final int FLESH_DEAD_LIT = 0xFF33244A;
    private static final int SICK = 0xFF3E6B2A;
    private static final int SICK_LIT = 0xFF5C8C3E;
    private static final int EYE_WHITE = 0xFFF3E8FF;
    private static final int EYE_PUPIL = 0xFF120018;
    private static final int ACCENT = 0xFFB388FF;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private int stage;
    private boolean asleep;
    private boolean dead;
    private boolean sick;
    /** 0 at birth, 1 when the egg is due to hatch, so the cracks can widen. */
    private float hatchProgress;

    private float phase;
    private final Handler tick = new Handler();
    private final Runnable beat = new Runnable() {
        @Override public void run() {
            phase += 0.16f;
            invalidate();
            if (isAttachedToWindow()) tick.postDelayed(this, 130);
        }
    };

    PetView(Context c) {
        super(c);
        setBackgroundColor(BG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        fill.setStrokeWidth(Math.max(1f, 12f));
    }

    void setPet(int stage, boolean asleep, boolean dead, boolean sick) {
        this.stage = stage;
        this.asleep = asleep;
        this.dead = dead;
        this.sick = sick;
        invalidate();
    }

    void setHatchProgress(float progress) {
        this.hatchProgress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        tick.postDelayed(beat, 130);
    }

    @Override protected void onDetachedFromWindow() {
        tick.removeCallbacks(beat);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float unit = Math.min(getWidth(), getHeight());
        if (stage <= 0) {
            egg(c, cx, cy, unit);
        } else {
            creature(c, cx, cy, unit);
        }
    }

    private int flesh() {
        if (dead) return FLESH_DEAD;
        if (sick) return SICK;
        return FLESH;
    }

    private int fleshLit() {
        if (dead) return FLESH_DEAD_LIT;
        if (sick) return SICK_LIT;
        return FLESH_LIT;
    }

    private void bodyPath(Canvas c, float cx, float cy, float r) {
        path.reset();
        int lobes = 3 + Math.min(stage, 5) * 2;
        int steps = 96;
        float breath = asleep ? 0.012f : 0.03f;
        for (int i = 0; i <= steps; i++) {
            float angle = (float) i / steps * (float) (Math.PI * 2);
            float wobble = 1f
                + breath * (float) Math.sin(angle * lobes + phase * (asleep ? 0.4f : 1.3f))
                + 0.02f * (float) Math.sin(angle * (lobes + 3) - phase * 0.8f);
            float x = cx + (float) Math.cos(angle) * r * wobble;
            float y = cy + (float) Math.sin(angle) * r * wobble * 0.92f;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        fill.setColor(flesh());
        c.drawPath(path, fill);
        stroke.setColor(fleshLit());
        stroke.setStrokeWidth(Math.max(1f, r * 0.06f));
        c.drawPath(path, stroke);
    }

    /** Tentacles: tapered limbs that trail the motion of the body. */
    private void tentacles(Canvas c, float cx, float cy, float r) {
        int count = 2 + Math.min(stage, 5) * 2;
        Random rng = new Random(stage * 977L + 13L);
        for (int i = 0; i < count; i++) {
            // Spread limbs around the lower half, leaving the face clear.
            float base = (float) Math.PI * (0.08f + 0.84f * i / Math.max(1, count - 1))
                + (rng.nextFloat() - 0.5f) * 0.16f;
            float len = r * (1.7f + rng.nextFloat() * 1.3f + Math.min(stage, 5) * 0.22f);
            float sway = (float) Math.sin(phase * (asleep ? 0.3f : 1.1f) + i * 1.7f);
            float tipX = cx + (float) Math.cos(base) * len * (0.9f + 0.1f * sway);
            float tipY = cy + (float) Math.sin(base) * len * 0.85f
                + (dead ? 0f : (float) Math.sin(phase * 0.9f + i) * r * 0.16f);
            float midX = cx + (float) Math.cos(base) * len * 0.55f
                + (float) Math.cos(base + 1.57f) * len * 0.30f * sway;
            float midY = cy + (float) Math.sin(base) * len * 0.55f
                + (float) Math.sin(base + 1.57f) * len * 0.30f * sway;

            path.reset();
            path.moveTo(cx + (float) Math.cos(base) * r * 0.75f,
                cy + (float) Math.sin(base) * r * 0.75f);
            path.cubicTo(midX, midY, midX, midY + r * 0.1f, tipX, tipY);
            stroke.setColor(fleshLit());
            stroke.setStrokeWidth(Math.max(1f, r * (dead ? 0.05f : 0.13f)));
            c.drawPath(path, stroke);
        }
    }

    /** Eyes scattered over the body, more each stage, and they do not blink in unison. */
    private void eyes(Canvas c, float cx, float cy, float r) {
        int count = Math.min(1 + stage * 2, 11);
        Random rng = new Random(stage * 31L + 7L);
        for (int i = 0; i < count; i++) {
            float ang = (float) (rng.nextDouble() * Math.PI * 2);
            float dist = r * (0.15f + rng.nextFloat() * 0.5f);
            float ex = cx + (float) Math.cos(ang) * dist;
            float ey = cy + (float) Math.sin(ang) * dist * 0.9f;
            float er = r * (0.17f + rng.nextFloat() * 0.09f);
            if (er < 1.6f) continue;

            // Closed as a line when asleep or gone, and blinking occasionally.
            boolean shut = asleep || dead || Math.sin(phase * 0.55f + i * 2.1f) > 0.99f;
            if (shut) {
                stroke.setColor(EYE_WHITE);
                stroke.setStrokeWidth(Math.max(1f, er * 0.28f));
                c.drawLine(ex - er, ey, ex + er, ey, stroke);
                continue;
            }
            fill.setColor(EYE_WHITE);
            c.drawCircle(ex, ey, er, fill);
            // The pupil drifts, as if tracking something on the far side of the glass.
            float px = ex + (float) Math.sin(phase * 0.7f + i) * er * 0.3f;
            float py = ey + (float) Math.cos(phase * 0.5f + i * 1.3f) * er * 0.3f;
            fill.setColor(EYE_PUPIL);
            c.drawCircle(px, py, er * 0.52f, fill);
        }
    }

    /** The mouth widens each stage, and by the end it has far too many teeth. */
    private void mouth(Canvas c, float cx, float cy, float r) {
        if (stage < 2) return;
        float w = r * (0.5f + 0.09f * (stage - 2));
        float h = r * (0.10f + 0.06f * (stage - 2));
        float my = cy + r * 0.62f;
        int teeth = Math.min(3 + stage, 12);
        path.reset();
        path.moveTo(cx - w, my);
        for (int i = 0; i <= teeth; i++) {
            path.lineTo(cx - w + 2 * w * i / teeth, my + (i % 2 == 0 ? 0f : h));
        }
        path.lineTo(cx + w, my);
        stroke.setColor(asleep || dead ? 0xFF6B4A80 : ACCENT);
        stroke.setStrokeWidth(Math.max(1f, r * 0.07f));
        c.drawPath(path, stroke);
    }

    private void creature(Canvas c, float cx, float cy, float unit) {
        float r = unit * (0.15f + Math.min(stage, 5) * 0.022f);
        tentacles(c, cx, cy, r);
        bodyPath(c, cx, cy, r);
        eyes(c, cx, cy, r);
        mouth(c, cx, cy, r);
        if (asleep && !dead) drawSnore(c, cx, cy, r, unit);
    }

    /** Three drifting z's above a sleeping pet. */
    private void drawSnore(Canvas c, float cx, float cy, float r, float unit) {
        fill.setColor(ACCENT);
        fill.setTextSize(unit * 0.075f);
        fill.setStyle(Paint.Style.FILL);
        float x = cx + r * 1.15f;
        float y = cy - r * 1.05f;
        for (int i = 0; i < 3; i++) {
            float drift = (float) Math.sin(phase * 0.5f + i) * unit * 0.03f;
            c.drawText("z", x + drift, y - i * unit * 0.07f, fill);
            x += unit * 0.035f;
        }
    }

    /** Stage 0: an unthought egg, cracking as the hatchling nears. */
    private void egg(Canvas c, float cx, float cy, float unit) {
        float r = unit * 0.17f;
        fill.setColor(0xFFD9CFB0);
        c.drawOval(cx - r * 0.8f, cy - r, cx + r * 0.8f, cy + r, fill);
        stroke.setColor(0xFF8A7A55);
        stroke.setStrokeWidth(Math.max(1f, r * 0.08f));
        c.drawOval(cx - r * 0.8f, cy - r, cx + r * 0.8f, cy + r, stroke);

        stroke.setColor(0xFF4A3B22);
        stroke.setStrokeWidth(Math.max(1f, r * 0.07f));
        Random rng = new Random(99L);
        for (int i = 0; i < 4; i++) {
            float a = -1.6f + i * 0.8f + rng.nextFloat() * 0.2f;
            path.reset();
            path.moveTo(cx + (float) Math.cos(a) * r * 0.7f, cy + (float) Math.sin(a) * r);
            for (int k = 1; k <= 4; k++) {
                float rr = r * (0.7f + k * 0.09f) * hatchProgress;
                path.lineTo(cx + (float) Math.cos(a + k * 0.25f) * rr,
                    cy + (float) Math.sin(a + k * 0.25f) * rr);
            }
            c.drawPath(path, stroke);
        }
        if (hatchProgress > 0.6f && !dead) {
            float ex = cx - r * 0.22f;
            float ey = cy + r * 0.1f;
            float er = r * 0.14f;
            fill.setColor(EYE_WHITE);
            c.drawCircle(ex, ey, er, fill);
            c.drawCircle(ex + r * 0.44f, ey, er, fill);
            fill.setColor(EYE_PUPIL);
            c.drawCircle(ex, ey, er * 0.5f, fill);
            c.drawCircle(ex + r * 0.44f, ey, er * 0.5f, fill);
        }
    }
}