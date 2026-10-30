package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// The seasonal animations (Seasons' effects): a few dozen particles drawn by hand, so nothing is allocated
// while they run. It only draws while running; the home screen runs it with the lights off (or always, if
// picked), and stops it the moment an app opens or a key is pressed.
final class ParticleView extends View {
    private static final class P {
        int kind, color;
        float x, y, vx, vy, size, spin, angle, phase, life, wait;
        String glyph;
    }

    private static final int[] CONFETTI_COLOURS = {0xFFFF5A5A, 0xFFFFC94D, 0xFF5AD1FF, 0xFF8CE06A, 0xFFE07AFF, 0xFFFFFFFF};
    private static final int[] SPARK_COLOURS = {0xFFFFD27A, 0xFFFF7A7A, 0xFF7AD7FF, 0xFFB98CFF, 0xFFFFFFFF};

    private final List<P> parts = new ArrayList<>();
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path diamond = new Path();
    private int[] effects = {};
    private boolean running, seeded;
    private long last;
    private float flash, nextFlash, nextBurst, scale = 1f;

    ParticleView(Context c) {
        super(c);
        setFocusable(false);
        setClickable(false);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    void setEffects(int[] fx) {
        if (java.util.Arrays.equals(fx, effects)) return;
        effects = fx;
        parts.clear();
        seeded = false;
        invalidate();
    }

    void setRunning(boolean on) {
        if (on == running) return;
        running = on;
        last = 0;
        if (on) {
            seeded = false;
            parts.clear();
            postInvalidateOnAnimation();
        } else invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!running || effects.length == 0 || getWidth() == 0) return;
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        if (!seeded) seed();
        float w = getWidth(), h = getHeight();

        for (P p : parts) {
            if (p.wait > 0) { p.wait -= dt; continue; }
            p.phase += dt;
            switch (p.kind) {
                case Seasons.SNOW:
                    p.y += p.vy * dt;
                    p.x += (float) Math.sin(p.phase * 0.8f + p.angle) * 14 * scale * dt;
                    if (p.y > h + 10) { p.y = -10; p.x = rnd.nextFloat() * w; }
                    paint.setColor(0xD9FFFFFF);
                    c.drawCircle(p.x, p.y, p.size, paint);
                    break;
                case Seasons.RAIN:
                    p.y += p.vy * dt;
                    p.x += p.vx * dt;
                    if (p.y > h + 40) { p.y = -40; p.x = rnd.nextFloat() * (w + 200); }
                    paint.setColor(0x40BFD9FF);
                    paint.setStrokeWidth(2 * scale);
                    c.drawLine(p.x, p.y, p.x + p.vx * 0.04f, p.y + p.vy * 0.04f, paint);
                    break;
                case Seasons.CONFETTI:
                case Seasons.DIAMONDS:
                    p.y += p.vy * dt;
                    p.x += (float) Math.sin(p.phase + p.angle) * 30 * scale * dt;
                    p.angle += p.spin * dt;
                    if (p.y > h + 20) { p.y = -20; p.x = rnd.nextFloat() * w; }
                    paint.setColor(p.color);
                    c.save();
                    c.translate(p.x, p.y);
                    c.rotate(p.angle * 57.3f);
                    if (p.kind == Seasons.CONFETTI) {
                        c.scale(1f, (float) Math.abs(Math.cos(p.phase * 3 + p.angle)) * 0.8f + 0.2f);
                        c.drawRect(-p.size, -p.size * 0.5f, p.size, p.size * 0.5f, paint);
                    } else {
                        diamond.reset();
                        diamond.moveTo(0, -p.size);
                        diamond.lineTo(p.size * 0.6f, 0);
                        diamond.lineTo(0, p.size);
                        diamond.lineTo(-p.size * 0.6f, 0);
                        diamond.close();
                        c.drawPath(diamond, paint);
                    }
                    c.restore();
                    break;
                case Seasons.HEARTS:
                    p.y += p.vy * dt;
                    p.x += (float) Math.sin(p.phase * 1.3f + p.angle) * 20 * scale * dt;
                    if (p.y < -60) { p.y = h + 60; p.x = rnd.nextFloat() * w; }
                    drawGlyph(c, p, Math.max(0f, Math.min(1f, p.y / h)) * 0.8f);
                    break;
                case Seasons.EGGS:
                    p.x += p.vx * dt;
                    if (p.x > w + 80) { p.x = -80; p.wait = 1 + rnd.nextFloat() * 3; }
                    float hop = (float) Math.abs(Math.sin(p.phase * 3.2f + p.angle)) * 70 * scale;
                    float ground = p.y;
                    p.y = ground - hop;
                    drawGlyph(c, p, 0.9f);
                    p.y = ground;
                    break;
                case Seasons.BATS:
                    p.x += p.vx * dt;
                    float baseY = p.vy;
                    float wave = (float) Math.sin(p.phase * 2.5f + p.angle) * 40 * scale;
                    if (p.x > w + 80) { p.x = -80; p.vy = h * (0.12f + rnd.nextFloat() * 0.45f); p.wait = 2 + rnd.nextFloat() * 6; }
                    float keep = p.y;
                    p.y = baseY + wave;
                    drawGlyph(c, p, 0.85f);
                    p.y = keep;
                    break;
                case Seasons.SPARKLE:
                    float a = 0.5f + 0.5f * (float) Math.sin(p.phase * p.spin + p.angle);
                    paint.setColor(p.color);
                    paint.setAlpha(Math.round(40 * a));
                    c.drawCircle(p.x, p.y, p.size * 3, paint);
                    paint.setAlpha(Math.round(220 * a));
                    c.drawCircle(p.x, p.y, p.size, paint);
                    break;
                case Seasons.FIREWORKS:
                    if (p.life <= 0) break;
                    p.life -= dt;
                    p.vy += 90 * scale * dt;
                    p.x += p.vx * dt;
                    p.y += p.vy * dt;
                    paint.setColor(p.color);
                    paint.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, p.life / 1.2f))));
                    c.drawCircle(p.x, p.y, p.size, paint);
                    break;
            }
        }

        if (has(Seasons.FIREWORKS)) {
            nextBurst -= dt;
            if (nextBurst <= 0) { burst(w, h); nextBurst = 0.9f + rnd.nextFloat() * 0.8f; }
        }
        if (has(Seasons.LIGHTNING)) {
            nextFlash -= dt;
            if (nextFlash <= 0) { flash = 1f; nextFlash = 6 + rnd.nextFloat() * 9; }
            if (flash > 0) {
                c.drawColor(Color.argb(Math.round(46 * flash), 255, 255, 255));
                flash = Math.max(0f, flash - dt * 6);
            }
        }
        postInvalidateOnAnimation();
    }

    private void drawGlyph(Canvas c, P p, float alpha) {
        paint.setColor(Color.WHITE);
        paint.setAlpha(Math.round(255 * alpha));
        paint.setTextSize(p.size);
        c.drawText(p.glyph, p.x, p.y, paint);
    }

    private boolean has(int kind) {
        for (int e : effects) if (e == kind) return true;
        return false;
    }

    private void seed() {
        seeded = true;
        float w = getWidth(), h = getHeight();
        scale = w / 1920f;
        nextFlash = 3;
        nextBurst = 0;
        for (int fx : effects) {
            int n = fx == Seasons.SNOW ? 36 : fx == Seasons.RAIN ? 40 : fx == Seasons.CONFETTI ? 36 : fx == Seasons.DIAMONDS ? 18
                    : fx == Seasons.HEARTS ? 10 : fx == Seasons.EGGS ? 3 : fx == Seasons.BATS ? 5 : fx == Seasons.SPARKLE ? 28
                    : fx == Seasons.FIREWORKS ? 64 : 0;
            for (int i = 0; i < n; i++) {
                P p = new P();
                p.kind = fx;
                p.x = rnd.nextFloat() * w;
                p.y = rnd.nextFloat() * h;
                p.angle = rnd.nextFloat() * 6.28f;
                switch (fx) {
                    case Seasons.SNOW: p.vy = (40 + rnd.nextFloat() * 50) * scale; p.size = (2 + rnd.nextFloat() * 3) * scale; break;
                    case Seasons.RAIN: p.vy = (750 + rnd.nextFloat() * 200) * scale; p.vx = -120 * scale; break;
                    case Seasons.CONFETTI:
                        p.vy = (60 + rnd.nextFloat() * 60) * scale; p.size = (5 + rnd.nextFloat() * 4) * scale;
                        p.spin = -3 + rnd.nextFloat() * 6; p.color = CONFETTI_COLOURS[rnd.nextInt(CONFETTI_COLOURS.length)];
                        break;
                    case Seasons.DIAMONDS:
                        p.vy = (25 + rnd.nextFloat() * 20) * scale; p.size = (8 + rnd.nextFloat() * 6) * scale;
                        p.spin = -0.6f + rnd.nextFloat() * 1.2f; p.color = rnd.nextBoolean() ? 0x663D8BFF : 0x55FFFFFF;
                        break;
                    case Seasons.HEARTS: p.glyph = "❤️"; p.vy = -(30 + rnd.nextFloat() * 25) * scale; p.size = (30 + rnd.nextFloat() * 18) * scale; break;
                    case Seasons.EGGS:
                        p.glyph = i == 1 ? "🐣" : "🥚"; p.vx = 90 * scale; p.size = 56 * scale;
                        p.x = -80 - i * 140 * scale; p.y = h - 40 * scale; break;
                    case Seasons.BATS:
                        p.glyph = "🦇"; p.vx = (140 + rnd.nextFloat() * 110) * scale; p.size = (38 + rnd.nextFloat() * 20) * scale;
                        p.x = -80; p.vy = h * (0.12f + rnd.nextFloat() * 0.45f); p.wait = rnd.nextFloat() * 8; break;
                    case Seasons.SPARKLE:
                        p.size = (2 + rnd.nextFloat() * 2) * scale; p.spin = 1 + rnd.nextFloat() * 2;
                        p.color = rnd.nextBoolean() ? 0xFFFFD27A : 0xFFFFF1C9; break;
                    case Seasons.FIREWORKS: p.life = 0; p.size = 3 * scale; break;
                }
                parts.add(p);
            }
        }
    }

    // Reuses the burnt-out sparks for a new burst at a random place in the upper half.
    private void burst(float w, float h) {
        float cx = w * (0.15f + rnd.nextFloat() * 0.7f), cy = h * (0.12f + rnd.nextFloat() * 0.35f);
        int colour = SPARK_COLOURS[rnd.nextInt(SPARK_COLOURS.length)];
        int used = 0;
        for (P p : parts) {
            if (p.kind != Seasons.FIREWORKS || p.life > 0) continue;
            double a = rnd.nextDouble() * Math.PI * 2;
            float v = (120 + rnd.nextFloat() * 140) * scale;
            p.x = cx; p.y = cy;
            p.vx = (float) Math.cos(a) * v; p.vy = (float) Math.sin(a) * v;
            p.life = 1.2f + rnd.nextFloat() * 0.4f;
            p.color = colour;
            if (++used == 32) break;
        }
    }
}
