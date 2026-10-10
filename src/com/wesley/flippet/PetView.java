package com.wesley.flippet;

import android.content.Context;
import android.graphics.Canvas;
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
 *
 * Six phenotypes come out of the one egg. Whichever hatches, the rules are the
 * same: a body that breathes, more limbs and openings each stage, and the mood
 * shifts (sick, dead, asleep) that recolor it and slow it down.
 */
final class PetView extends View {
    private static final int BG = 0xFF07040C;
    private static final int EYE_WHITE = 0xFFF3E8FF;
    private static final int EYE_PUPIL = 0xFF120018;

    /** One palette per phenotype, each with its own sick and dead shifts. */
    private static final class Palette {
        final int base, lit, sick, sickLit, dead, deadLit, accent;
        Palette(int base, int lit, int sick, int sickLit, int dead, int deadLit, int accent) {
            this.base = base;
            this.lit = lit;
            this.sick = sick;
            this.sickLit = sickLit;
            this.dead = dead;
            this.deadLit = deadLit;
            this.accent = accent;
        }
    }

    private static final Palette[] PALETTES = {
        // 0 Amber Child: the first one, a purple tentacle ball.
        new Palette(0xFF2E1247, 0xFF4A1E6B, 0xFF3E6B2A, 0xFF5C8C3E, 0xFF231733, 0xFF33244A, 0xFFB388FF),
        // 1 Pale Host: a swaying column of bone on many legs, with a ringed maw.
        new Palette(0xFF8A8391, 0xFFA79FB4, 0xFF6E8A5A, 0xFF8FA96F, 0xFF56525C, 0xFF6F6A78, 0xFFD9CFB0),
        // 2 Sleet: a dim core attended by a host of eyes that will not hold still.
        new Palette(0xFF16343A, 0xFF21505C, 0xFF3E5A2C, 0xFF55783F, 0xFF122229, 0xFF1A3238, 0xFF7FD9C8),
        // 3 Red Crown: a round body with a ring of small mouths up top that all sing.
        new Palette(0xFF3A1428, 0xFF5A1E3E, 0xFF3F5A1E, 0xFF5C7A2E, 0xFF26101B, 0xFF341524, 0xFFE0708A),
        // 4 Drowned Lamp: a low wet sac, two huge eyes, and nothing but patience.
        new Palette(0xFF14243E, 0xFF1E3760, 0xFF2E4A1E, 0xFF45692E, 0xFF0E1726, 0xFF14202E, 0xFF7FACFF),
        // 5 Old Harvest: a shaggy star of knuckled points, claws at every tip.
        new Palette(0xFF3A2E14, 0xFF5C4A1E, 0xFF2E3E14, 0xFF47591E, 0xFF26230F, 0xFF332E12, 0xFFE0B070),
    };

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private int stage;
    private int phenotype;
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

    void setPet(int stage, boolean asleep, boolean dead, boolean sick, int phenotype) {
        this.stage = stage;
        this.asleep = asleep;
        this.dead = dead;
        this.sick = sick;
        this.phenotype = phenotype % PALETTES.length;
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

    // --------------------------------------------------------------- state

    private Palette pal() {
        return PALETTES[phenotype % PALETTES.length];
    }

    private int flesh() {
        Palette p = pal();
        if (dead) return p.dead;
        if (sick) return p.sick;
        return p.base;
    }

    private int fleshLit() {
        Palette p = pal();
        if (dead) return p.deadLit;
        if (sick) return p.sickLit;
        return p.lit;
    }

    /** Accent, muted once the creature is past caring whether it looks well. */
    private int accent() {
        if (dead) return 0xFF6A6270;
        if (sick) return 0xFF9FB57F;
        return pal().accent;
    }

    /** How fast the creature moves; sleep halves it, death stops the limbs only. */
    private float breathe() {
        return asleep ? 0.35f : 1f;
    }

    // -------------------------------------------------------------- amber

    /** The tentacle ball: the original creature, kept exactly as it was. */
    private void drawAmber(Canvas c, float cx, float cy, float r) {
        tentacles(c, cx, cy, r);
        bodyPath(c, cx, cy, r);
        eyes(c, cx, cy, r);
        mouth(c, cx, cy, r);
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

            boolean shut = asleep || dead || Math.sin(phase * 0.55f + i * 2.1f) > 0.99f;
            if (shut) {
                stroke.setColor(EYE_WHITE);
                stroke.setStrokeWidth(Math.max(1f, er * 0.28f));
                c.drawLine(ex - er, ey, ex + er, ey, stroke);
                continue;
            }
            fill.setColor(EYE_WHITE);
            c.drawCircle(ex, ey, er, fill);
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
        stroke.setColor(asleep || dead ? 0xFF6B4A80 : accent());
        stroke.setStrokeWidth(Math.max(1f, r * 0.07f));
        c.drawPath(path, stroke);
    }

    /** Two large forward-facing eyes, shared by the creatures that keep a face. */
    private void frontEyes(Canvas c, float ex, float ey, float er, float gap) {
        for (int s = -1; s <= 1; s += 2) {
            float x = ex + s * gap * er;
            boolean shut = asleep || dead
                || Math.sin(phase * 0.6f + (s > 0 ? 0f : 1.7f)) > 0.995f;
            if (shut) {
                stroke.setColor(EYE_WHITE);
                stroke.setStrokeWidth(Math.max(1f, er * 0.30f));
                c.drawLine(x - er, ey, x + er, ey, stroke);
                continue;
            }
            fill.setColor(EYE_WHITE);
            c.drawCircle(x, ey, er, fill);
            float px = x + (float) Math.sin(phase * 0.7f + s) * er * 0.28f;
            float py = ey + (float) Math.cos(phase * 0.5f + s * 1.3f) * er * 0.28f;
            fill.setColor(EYE_PUPIL);
            c.drawCircle(px, py, er * 0.55f, fill);
        }
    }

    /** A wide low mouth with a jagged tooth line. */
    private void wideMouth(Canvas c, float cx, float my, float w, float yaw) {
        int teeth = 4 + stage;
        path.reset();
        path.moveTo(cx - w, my);
        for (int i = 0; i <= teeth; i++) {
            path.lineTo(cx - w + 2 * w * i / teeth, my + (i % 2 == 0 ? 0f : yaw));
        }
        path.lineTo(cx + w, my);
        stroke.setColor(asleep || dead ? 0xFF6B4A80 : accent());
        stroke.setStrokeWidth(Math.max(1f, w * 0.10f));
        c.drawPath(path, stroke);
    }

    // -------------------------------------------------------- pale host

    /** A swaying spire of bone on many legs, its hood a single ringed mouth. */
    private void drawPaleHost(Canvas c, float cx, float cy, float r) {
        float sway = breathe() * (float) Math.sin(phase * 1.1f) * r * 0.06f;
        int legs = 6 + Math.min(stage, 5) * 2;
        Random rng = new Random(phenotype * 1013L + stage * 17L + 11L);
        for (int i = 0; i < legs; i++) {
            float t = (float) i / Math.max(1, legs - 1);
            float a = (float) Math.PI * (0.18f + 0.64f * t);
            float hipX = cx + (float) Math.cos(a) * r * 0.72f;
            float hipY = cy + (float) Math.sin(a) * r * 0.40f - r * 0.05f;
            float lift = dead ? 0f : 0.5f + 0.5f * (float) Math.sin(phase * 1.3f + i * 1.9f);
            float knY = hipY + r * (0.35f + 0.30f * lift);
            float footX = hipX + (float) Math.cos(a) * (r * 0.55f + 0.30f * r * lift);
            float footY = cy + r * (1.05f - 0.12f * lift);
            path.reset();
            path.moveTo(hipX, hipY);
            path.quadTo(hipX + (float) Math.cos(a) * r * 0.25f, knY, footX, footY);
            stroke.setColor(fleshLit());
            stroke.setStrokeWidth(Math.max(1f, r * 0.05f));
            c.drawPath(path, stroke);
            fill.setColor(flesh());
            c.drawCircle(footX, footY, r * 0.035f, fill);
        }

        // The column: a thick curved spine, then three ribs.
        float topY = cy - r * 1.15f;
        stroke.setColor(flesh());
        stroke.setStrokeWidth(Math.max(1f, r * 0.34f));
        path.reset();
        path.moveTo(cx + sway, topY + r * 0.4f);
        path.quadTo(cx + sway * 0.6f, cy, cx + sway * 0.4f, cy + r * 0.85f);
        c.drawPath(path, stroke);
        stroke.setColor(fleshLit());
        stroke.setStrokeWidth(Math.max(1f, r * 0.05f));
        for (int i = 0; i < 3; i++) {
            float t = i - 1f;
            float py = cy + t * r * 0.55f;
            float px = cx + sway * (0.35f + 0.3f * (1f - Math.abs(t)));
            float w = r * (0.34f - 0.10f * Math.abs(t));
            path.reset();
            path.moveTo(px - w, py);
            path.quadTo(px, py - r * 0.16f, px + w, py);
            c.drawPath(path, stroke);
        }

        // The hood: a head taller than the column, with the maw in it.
        float hy = topY + r * 0.05f;
        float hw = r * 0.46f;
        fill.setColor(flesh());
        c.drawOval(cx + sway - hw, hy - r * 0.60f, cx + sway + hw, hy + r * 0.60f, fill);
        stroke.setColor(fleshLit());
        stroke.setStrokeWidth(Math.max(1f, r * 0.05f));
        c.drawOval(cx + sway - hw, hy - r * 0.60f, cx + sway + hw, hy + r * 0.60f, stroke);

        // The maw opens with each breath, ringed by small pointed teeth.
        float open = dead || asleep ? 0.12f : 0.45f + 0.22f * breathe() * (float) Math.abs(Math.sin(phase * 1.1f));
        float mw = r * 0.10f;
        float mh = r * 0.45f * open;
        fill.setColor(EYE_PUPIL);
        c.drawOval(cx + sway - mw, hy - mh, cx + sway + mw, hy + mh, fill);
        int teeth = 4 + Math.min(stage, 5);
        stroke.setColor(asleep || dead ? 0xFF6B4A80 : accent());
        stroke.setStrokeWidth(Math.max(1f, r * 0.035f));
        for (int i = 0; i < teeth; i++) {
            float a = (float) Math.PI * (0.10f + 0.80f * i / Math.max(1, teeth - 1));
            path.reset();
            path.moveTo(cx + sway + (float) Math.cos(a) * r * 0.26f, hy + r * 0.34f);
            path.lineTo(cx + sway + (float) Math.cos(a) * r * 0.16f, hy + r * 0.40f);
            c.drawPath(path, stroke);
        }

        // Two slit eyes above the maw.
        for (int s = -1; s <= 1; s += 2) {
            float ex = cx + sway + s * r * 0.17f;
            float ey = hy - r * 0.34f;
            boolean shut = asleep || dead || Math.sin(phase * 0.5f + s) > 0.99f;
            if (shut) {
                stroke.setColor(EYE_WHITE);
                stroke.setStrokeWidth(Math.max(1f, r * 0.04f));
                c.drawLine(ex - r * 0.05f, ey, ex + r * 0.05f, ey, stroke);
            } else {
                fill.setColor(EYE_WHITE);
                c.drawOval(ex - r * 0.045f, ey - r * 0.11f, ex + r * 0.045f, ey + r * 0.11f, fill);
                fill.setColor(EYE_PUPIL);
                c.drawCircle(ex, ey - r * 0.03f, r * 0.028f, fill);
            }
        }
    }

    // ------------------------------------------------------- sleet

    /** A dim core with a host of separate eyes orbiting it, none of them staying. */
    private void drawSleet(Canvas c, float cx, float cy, float r) {
        int n = 6 + Math.min(stage, 5) * 3;
        Random rng = new Random(phenotype * 2039L + stage * 37L + 5L);
        float spin = dead ? 0f : (asleep ? 0.05f : 0.25f);
        for (int i = 0; i < n; i++) {
            float a = (float) (i / (double) n * Math.PI * 2) + phase * spin;
            float d = r * (1.30f + 0.08f * (float) Math.sin(i * 2.7f + phase * 0.6f));
            float mx = cx + (float) Math.cos(a) * d;
            float my = cy + (float) Math.sin(a) * d * 0.8f;
            float er = r * (0.085f + 0.012f * rng.nextFloat()) * (1f + 0.08f * Math.min(stage, 5));
            if (dead) {
                fill.setColor(0xFF2A3340);
                c.drawCircle(mx, cy + r * 1.1f, Math.max(1f, er * 0.4f), fill);
                continue;
            }
            boolean flit = Math.sin(phase * 0.8f + i * 1.77f) > 0.985f && !asleep;
            if (asleep || flit) {
                stroke.setColor(0xFF6E9AA0);
                stroke.setStrokeWidth(Math.max(1f, er * 0.3f));
                c.drawLine(mx - er, my, mx + er, my, stroke);
                continue;
            }
            fill.setColor(EYE_WHITE);
            c.drawCircle(mx, my, er, fill);
            float px = mx + (float) Math.cos(a) * er * 0.35f;
            float py = my + (float) Math.sin(a) * er * 0.35f;
            fill.setColor(EYE_PUPIL);
            c.drawCircle(px, py, er * 0.5f, fill);
        }

        fill.setColor(flesh());
        c.drawCircle(cx, cy, r * 0.42f, fill);
        stroke.setColor(fleshLit());
        stroke.setStrokeWidth(Math.max(1f, r * 0.05f));
        c.drawCircle(cx, cy, r * 0.42f, stroke);
        float coreOpen = dead ? 0.05f : (asleep ? 0.10f : 0.35f + 0.15f * breathe() * (float) Math.sin(phase * 1.3f));
        stroke.setColor(accent());
        stroke.setStrokeWidth(Math.max(1f, r * 0.05f));
        c.drawLine(cx, cy - r * 0.30f * coreOpen, cx, cy + r * 0.30f * coreOpen, stroke);
    }

    // -------------------------------------------------- red crown

    /** A round body whose top edge is a ring of openings, each one singing. */
    private void drawCrown(Canvas c, float cx, float cy, float r) {
        bodyPath(c, cx, cy, r * 0.94f);

        int K = 4 + Math.min(stage, 5) * 2;
        for (int i = 0; i < K; i++) {
            float frac = 0.04f + 0.92f * i / Math.max(1, K - 1);
            float a = (float) Math.PI * (1f - frac);
            float mx = cx + (float) Math.cos(a) * r * 1.02f;
            float my = cy + (float) Math.sin(a) * r * 0.92f;
            float open = dead || asleep
                ? 0.10f
                : 0.55f + 0.22f * breathe() * (float) Math.abs(Math.sin(phase * 1.3f + i * 1.1f));
            float mr = r * 0.13f * open;
            fill.setColor(EYE_PUPIL);
            c.drawCircle(mx, my, Math.max(0.5f, mr), fill);
            stroke.setColor(dead ? fleshLit() : accent());
            stroke.setStrokeWidth(Math.max(1f, r * 0.04f));
            c.drawCircle(mx, my, Math.max(0.5f, mr * 1.35f), stroke);
            if (open > 0.55f) {
                fill.setColor(accent());
                c.drawCircle(mx, my + mr * 0.5f, Math.max(0.5f, mr * 0.22f), fill);
            }
        }

        int W = 3 + Math.min(stage, 4);
        Random rng = new Random(phenotype * 101L + stage * 13L + 7L);
        for (int i = 0; i < W; i++) {
            float a = (float) Math.PI * (0.14f + 0.72f * rng.nextFloat());
            float wx = cx + (float) Math.cos(a) * r * 0.85f;
            float wy = cy + (float) Math.sin(a) * r * 0.80f;
            float sway = dead ? 0f : (float) Math.sin(phase * 1.1f + i * 1.9f) * r * 0.16f;
            float tipX = wx + sway;
            float tipY = wy + r * (0.90f + rng.nextFloat() * 0.5f);
            path.reset();
            path.moveTo(wx, wy);
            path.quadTo(wx + (float) Math.sin(phase * 0.9f + i) * r * 0.10f, wy + r * 0.45f,
                tipX, tipY);
            stroke.setColor(fleshLit());
            stroke.setStrokeWidth(Math.max(1f, r * 0.045f));
            c.drawPath(path, stroke);
        }

        frontEyes(c, cx, cy - r * 0.18f, r * 0.15f, 0.62f);
        mouth(c, cx, cy, r);
    }

    // -------------------------------------------------- drowned lamp

    /** A wide wet sac: two enormous eyes, one low wide mouth, and very long patience. */
    private void drawLamp(Canvas c, float cx, float cy, float r) {
        float sway = dead ? 0f : breathe() * 0.5f * (float) Math.sin(phase * 0.4f) * r * 0.05f;
        cx += sway;
        float rx = r * 1.02f;
        float ry = r * 0.60f;
        float bodyCx = cx;
        float bodyCy = cy + r * 0.08f;
        fill.setColor(flesh());
        c.drawOval(bodyCx - rx, bodyCy - ry, bodyCx + rx, bodyCy + ry, fill);
        stroke.setColor(fleshLit());
        stroke.setStrokeWidth(Math.max(1f, r * 0.05f));
        c.drawOval(bodyCx - rx, bodyCy - ry, bodyCx + rx, bodyCy + ry, stroke);
        stroke.setStrokeWidth(Math.max(1f, r * 0.055f));
        c.drawLine(bodyCx - rx * 0.55f, bodyCy - ry * 0.55f, bodyCx + rx * 0.55f, bodyCy - ry * 0.55f, stroke);

        for (int s = -1; s <= 1; s += 2) {
            float baseX = bodyCx + s * r * 0.60f;
            float baseY = bodyCy - ry * 0.60f;
            float tipX = baseX + s * r * 0.20f + (dead ? 0f : (float) Math.sin(phase * 0.8f + s) * r * 0.10f);
            float tipY = baseY - r * 0.55f;
            path.reset();
            path.moveTo(baseX, baseY);
            path.quadTo(baseX + s * r * 0.15f, baseY - r * 0.30f, tipX, tipY);
            stroke.setColor(fleshLit());
            stroke.setStrokeWidth(Math.max(1f, r * 0.035f));
            c.drawPath(path, stroke);
        }

        frontEyes(c, bodyCx, bodyCy - ry * 0.40f, r * 0.20f, 0.55f);

        float yn = r * (0.45f + 0.05f * Math.min(stage, 5));
        float yawn = dead || asleep ? r * 0.03f : r * (0.09f + 0.06f * breathe() * (float) Math.abs(Math.sin(phase * 0.9f)));
        wideMouth(c, bodyCx, bodyCy + ry * 0.55f, yn, Math.max(1f, yawn));
    }

    // --------------------------------------------------- old harvest

    /** A spiky star of knuckled points, claws at every tip, eyes close to the heart. */
    private void drawHarvest(Canvas c, float cx, float cy, float r) {
        int pts = 5 + Math.min(stage, 5);
        Random rng = new Random(phenotype * 607L + stage * 29L + 3L);
        float rot = dead ? 0f : phase * (asleep ? 0.03f : 0.10f);
        path.reset();
        for (int i = 0; i < pts * 2; i++) {
            boolean outer = (i % 2 == 0);
            float rad = outer ? r : r * 0.50f;
            float ang = rot + (float) (i * Math.PI / pts);
            rad *= 1f + 0.05f * (float) Math.sin(ang * 3f + phase * (dead ? 0f : 1.4f));
            float x = cx + (float) Math.cos(ang) * rad;
            float y = cy + (float) Math.sin(ang) * rad;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        fill.setColor(flesh());
        c.drawPath(path, fill);
        stroke.setColor(fleshLit());
        stroke.setStrokeWidth(Math.max(1f, r * 0.05f));
        c.drawPath(path, stroke);

        for (int i = 0; i < pts; i++) {
            float ang = rot + (float) (2 * i * Math.PI / pts);
            float tipX = cx + (float) Math.cos(ang) * r * 1.05f;
            float tipY = cy + (float) Math.sin(ang) * r * 1.05f;
            float shiver = dead ? 0f : (float) Math.sin(phase * 2.3f + i * 2.1f) * r * 0.03f;
            for (int k = -1; k <= 1; k += 2) {
                float ca = ang + k * 0.5f + shiver / Math.max(r, 1f);
                stroke.setColor(dead ? fleshLit() : accent());
                stroke.setStrokeWidth(Math.max(1f, r * 0.045f));
                c.drawLine(tipX, tipY,
                    tipX + (float) Math.cos(ca) * r * 0.18f, tipY + (float) Math.sin(ca) * r * 0.18f, stroke);
            }
        }

        for (int i = 0; i < 3; i++) {
            double ang = -Math.PI / 2 + i * 0.9;
            float ex = cx + (float) Math.cos(ang) * r * 0.22f;
            float ey = cy + (float) Math.sin(ang) * r * 0.22f;
            float er = r * 0.075f;
            boolean shut = asleep || dead || Math.sin(phase * 0.55f + i * 2.1f) > 0.985f;
            if (shut) {
                stroke.setColor(EYE_WHITE);
                stroke.setStrokeWidth(Math.max(1f, er * 0.3f));
                c.drawLine(ex - er, ey, ex + er, ey, stroke);
            } else {
                fill.setColor(EYE_WHITE);
                c.drawCircle(ex, ey, er, fill);
                float px = ex + (float) Math.sin(phase * 0.7f + i) * er * 0.3f;
                float py = ey + (float) Math.cos(phase * 0.5f + i * 1.3f) * er * 0.3f;
                fill.setColor(EYE_PUPIL);
                c.drawCircle(px, py, er * 0.5f, fill);
            }
        }

        float open = dead ? r * 0.02f : (asleep ? r * 0.06f
            : r * (0.18f + 0.10f * breathe() * (float) Math.abs(Math.sin(phase * 1.2f))));
        stroke.setColor(accent());
        stroke.setStrokeWidth(Math.max(1f, r * 0.05f));
        c.drawLine(cx, cy - open, cx, cy + open, stroke);
    }

    // ------------------------------------------------------------ body

    private void creature(Canvas c, float cx, float cy, float unit) {
        float r = unit * (0.15f + Math.min(stage, 5) * 0.022f);
        switch (phenotype % PALETTES.length) {
            case 5: drawHarvest(c, cx, cy, r); break;
            case 4: drawLamp(c, cx, cy, r); break;
            case 3: drawCrown(c, cx, cy, r); break;
            case 2: drawSleet(c, cx, cy, r); break;
            case 1: drawPaleHost(c, cx, cy, r); break;
            default: drawAmber(c, cx, cy, r);
        }
        if (asleep && !dead) drawSnore(c, cx, cy, r, unit);
    }

    /** Three drifting z's above a sleeping pet. */
    private void drawSnore(Canvas c, float cx, float cy, float r, float unit) {
        fill.setColor(accent());
        fill.setTextSize(unit * 0.075f);
        fill.setStyle(Paint.Style.FILL);
        float x = cx + r * 1.15f;
        float y = cy - r * 1.05f;
        for (int i = 0; i < 3; i++) {
            float drift = (float) Math.sin(phase * 0.5f + i) * unit * 0.03f;
            c.drawText("z", Math.max(0f, x + drift), y - i * unit * 0.07f, fill);
        }
    }

    /** Stage 0: an unthought egg, cracking as the hatchling nears. All six share it. */
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