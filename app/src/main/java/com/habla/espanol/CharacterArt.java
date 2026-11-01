package com.habla.espanol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Original cartoon character family & collectible sticker illustrations for Spanish 1000.
 * 100% original geometry & styling — zero external network reliance or third-party mascots.
 */
final class CharacterArt {
    private CharacterArt() {}

    enum Cast {
        SOL("سول", "مرشدك في تعلم الإسبانية", 0xFFF2A922, 0xFF0E8A63, 0xFFFFF4D6, 0xFF1F3B2E),
        ESTRELLA("إستريا", "حارسة المكافآت والإنجازات", 0xFFFFCA3A, 0xFFDE5B43, 0xFFFFF6DE, 0xFF3A2A14),
        ECO("إيكو", "مدرب الاستماع والنطق الإسباني", 0xFF0E7888, 0xFF0E8A63, 0xFFE3F4F7, 0xFF132D33),
        MATEO("ماتيو", "رفيق المواقف اليومية والمحادثات", 0xFF0E8A63, 0xFFE59B14, 0xFFE6F6EF, 0xFF173128),
        RAYO("رايو", "بطل التحديات والاختبارات", 0xFFDE5B43, 0xFFF2A922, 0xFFFDEEEB, 0xFF391E19);

        final String arabicName;
        final String role;
        final int bodyColor;
        final int accentColor;
        final int haloLight;
        final int haloDark;

        Cast(String arabicName, String role, int bodyColor, int accentColor, int haloLight, int haloDark) {
            this.arabicName = arabicName;
            this.role = role;
            this.bodyColor = bodyColor;
            this.accentColor = accentColor;
            this.haloLight = haloLight;
            this.haloDark = haloDark;
        }
    }

    enum Mood {
        WELCOME("ترحيب"),
        HAPPY("سعادة"),
        ENCOURAGE("تشجيع"),
        THINKING("تفكير"),
        SUCCESS("نجاح"),
        MISTAKE("دعم عند الخطأ"),
        SURPRISED("مفاجأة"),
        CELEBRATE("احتفال"),
        STREAK_MISS("اشتياق لطيف لاستعادة السلسلة");

        final String arabicLabel;
        Mood(String arabicLabel) { this.arabicLabel = arabicLabel; }
    }

    static final class CharacterView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF oval = new RectF();
        private Cast cast;
        private Mood mood;
        private boolean darkMode;

        CharacterView(Context context, Cast cast, Mood mood, boolean darkMode, boolean reducedMotion) {
            super(context);
            this.cast = cast;
            this.mood = mood;
            this.darkMode = darkMode;
            updateDescription();
            if (!reducedMotion && (mood == Mood.CELEBRATE || mood == Mood.SUCCESS || mood == Mood.WELCOME)) {
                setScaleX(0.88f);
                setScaleY(0.88f);
                setAlpha(0.75f);
                animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).start();
            }
        }

        void setState(Cast cast, Mood mood, boolean darkMode) {
            this.cast = cast;
            this.mood = mood;
            this.darkMode = darkMode;
            updateDescription();
            invalidate();
        }

        private void updateDescription() {
            setContentDescription("شخصية " + cast.arabicName + " — " + cast.role + " (" + mood.arabicLabel + ")");
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float s = Math.min(w, h) / 100f;
            c.save();
            c.translate((w - 100f * s) / 2f, (h - 100f * s) / 2f);
            c.scale(s, s);

            // 1. Soft background halo
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(darkMode ? cast.haloDark : cast.haloLight);
            c.drawCircle(50f, 52f, 45f, paint);

            // Subtle outer ring
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor((cast.bodyColor & 0x00FFFFFF) | 0x44000000);
            c.drawCircle(50f, 52f, 45f, paint);
            paint.setStyle(Paint.Style.FILL);

            // 2. Character Silhouette (consistent proportions: center 50,53, face radius ~26)
            drawCharacterBody(c);

            // 3. Expressive Face (9 distinct moods)
            drawExpression(c);

            // 4. Mood Aura / Props (sparkles, wave hand, thought bubble, gentle tear, confetti)
            drawMoodOverlay(c);

            c.restore();
        }

        private void drawCharacterBody(Canvas c) {
            if (cast == Cast.SOL) {
                // Warm Iberian Sun-Compass Explorer with Emerald Beret
                paint.setColor(0xFFE58E16);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6.0;
                    float rx = 50f + (float) Math.cos(a) * 32f;
                    float ry = 54f + (float) Math.sin(a) * 32f;
                    c.drawCircle(rx, ry, 6.2f, paint);
                }
                paint.setColor(cast.bodyColor);
                c.drawCircle(50f, 54f, 28f, paint);
                // Cream inner face warmth
                paint.setColor(0xFFFFE599);
                c.drawCircle(50f, 55f, 23.5f, paint);
                // Emerald Explorer Beret
                paint.setColor(cast.accentColor);
                oval.set(27f, 21f, 75f, 38f);
                c.drawOval(oval, paint);
                c.drawCircle(52f, 20f, 3.5f, paint);
            } else if (cast == Cast.ESTRELLA) {
                // Radiant Five-Pointed Lantern Star with Coral Crown Ribbon
                path.reset();
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2.0 + i * Math.PI / 5.0;
                    float r = (i % 2 == 0) ? 37f : 22f;
                    float px = 50f + (float) Math.cos(a) * r;
                    float py = 54f + (float) Math.sin(a) * r;
                    if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                path.close();
                paint.setColor(cast.bodyColor);
                c.drawPath(path, paint);
                paint.setColor(0xFFFFEEAA);
                c.drawCircle(50f, 55f, 21f, paint);
                // Little coral ribbon bow at top-right
                paint.setColor(cast.accentColor);
                c.drawCircle(63f, 29f, 5f, paint);
                c.drawCircle(71f, 33f, 5f, paint);
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(67f, 31f, 2.3f, paint);
            } else if (cast == Cast.ECO) {
                // Musical Soundwave Buddy with Studio Headphones
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(5.5f);
                paint.setColor(cast.accentColor);
                oval.set(20f, 20f, 80f, 76f);
                c.drawArc(oval, 195f, 150f, false, paint);
                paint.setStyle(Paint.Style.FILL);
                // Ear cups
                oval.set(14f, 44f, 25f, 66f);
                c.drawRoundRect(oval, 6f, 6f, paint);
                oval.set(75f, 44f, 86f, 66f);
                c.drawRoundRect(oval, 6f, 6f, paint);
                // Rounded turquoise body
                paint.setColor(cast.bodyColor);
                oval.set(24f, 30f, 76f, 80f);
                c.drawRoundRect(oval, 24f, 24f, paint);
                paint.setColor(0xFFD6F4F8);
                oval.set(29f, 36f, 71f, 75f);
                c.drawRoundRect(oval, 19f, 19f, paint);
            } else if (cast == Cast.MATEO) {
                // Friendly Café & Daily Life Companion with Saffron Scarf
                paint.setColor(cast.bodyColor);
                oval.set(24f, 28f, 76f, 78f);
                c.drawOval(oval, paint);
                paint.setColor(0xFFDDF5E9);
                oval.set(29f, 34f, 71f, 73f);
                c.drawOval(oval, paint);
                // Cozy Madrid Saffron Scarf at bottom
                paint.setColor(cast.accentColor);
                oval.set(28f, 68f, 72f, 80f);
                c.drawRoundRect(oval, 8f, 8f, paint);
                oval.set(56f, 72f, 66f, 89f);
                c.drawRoundRect(oval, 5f, 5f, paint);
                // Curly hair tuft on top
                paint.setColor(0xFF096347);
                c.drawCircle(45f, 28f, 6f, paint);
                c.drawCircle(54f, 26f, 7f, paint);
            } else {
                // RAYO: Energetic Challenge Shield Mascot with Lightning Crest
                path.reset();
                path.moveTo(50f, 18f);
                path.lineTo(78f, 28f);
                path.lineTo(74f, 61f);
                path.quadTo(50f, 86f, 50f, 86f);
                path.quadTo(50f, 86f, 26f, 61f);
                path.lineTo(22f, 28f);
                path.close();
                paint.setColor(cast.bodyColor);
                c.drawPath(path, paint);
                paint.setColor(0xFFFFE6DF);
                c.drawCircle(50f, 53f, 21f, paint);
                // Golden lightning crest on forehead
                path.reset();
                path.moveTo(52f, 14f);
                path.lineTo(44f, 28f);
                path.lineTo(51f, 28f);
                path.lineTo(47f, 38f);
                path.lineTo(58f, 24f);
                path.lineTo(51f, 24f);
                path.close();
                paint.setColor(cast.accentColor);
                c.drawPath(path, paint);
            }
        }

        private void drawExpression(Canvas c) {
            int ink = 0xFF1A2B27;
            // Cheek blush
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(mood == Mood.STREAK_MISS ? 0x557EA8BE : 0x66FF8E72);
            c.drawCircle(35f, 58f, 4.2f, paint);
            c.drawCircle(65f, 58f, 4.2f, paint);

            // Eyes
            if (mood == Mood.HAPPY || mood == Mood.CELEBRATE) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.8f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(ink);
                oval.set(35f, 45f, 45f, 54f);
                c.drawArc(oval, 190f, 160f, false, paint);
                oval.set(55f, 45f, 65f, 54f);
                c.drawArc(oval, 190f, 160f, false, paint);
            } else if (mood == Mood.SUCCESS) {
                // Left eye open, right eye cheerful wink
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(40f, 49f, 5.2f, paint);
                paint.setColor(ink);
                c.drawCircle(40.8f, 49f, 2.7f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.8f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                oval.set(55f, 45f, 65f, 54f);
                c.drawArc(oval, 190f, 160f, false, paint);
            } else if (mood == Mood.SURPRISED) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(40f, 48f, 6.2f, paint);
                c.drawCircle(60f, 48f, 6.2f, paint);
                paint.setColor(ink);
                c.drawCircle(40f, 48f, 3.1f, paint);
                c.drawCircle(60f, 48f, 3.1f, paint);
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(38.8f, 46.8f, 1.1f, paint);
                c.drawCircle(58.8f, 46.8f, 1.1f, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(40f, 49f, 5.2f, paint);
                c.drawCircle(60f, 49f, 5.2f, paint);
                float lookX = mood == Mood.THINKING ? 1.4f : 0.5f;
                float lookY = mood == Mood.THINKING ? -1.4f : (mood == Mood.STREAK_MISS ? 1.2f : 0f);
                paint.setColor(ink);
                c.drawCircle(40f + lookX, 49f + lookY, 2.6f, paint);
                c.drawCircle(60f + lookX, 49f + lookY, 2.6f, paint);
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(39f + lookX, 47.8f + lookY, 1f, paint);
                c.drawCircle(59f + lookX, 47.8f + lookY, 1f, paint);
            }

            // Eyebrows for thinking / mistake / streak_miss / encourage
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.3f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(ink);
            if (mood == Mood.THINKING) {
                c.drawLine(35f, 41f, 44f, 42f, paint);
                c.drawLine(56f, 39f, 65f, 37f, paint);
            } else if (mood == Mood.MISTAKE || mood == Mood.STREAK_MISS) {
                c.drawLine(35f, 42f, 44f, 40f, paint);
                c.drawLine(56f, 40f, 65f, 42f, paint);
            } else if (mood == Mood.ENCOURAGE) {
                c.drawLine(35f, 40f, 44f, 41.5f, paint);
                c.drawLine(56f, 41.5f, 65f, 40f, paint);
            }

            // Mouth
            if (mood == Mood.CELEBRATE || mood == Mood.HAPPY || mood == Mood.WELCOME) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ink);
                oval.set(42f, 55f, 58f, 67f);
                c.drawArc(oval, 0f, 180f, true, paint);
                paint.setColor(0xFFFF8B7B);
                oval.set(45f, 60f, 55f, 66f);
                c.drawOval(oval, paint);
            } else if (mood == Mood.SUCCESS || mood == Mood.ENCOURAGE) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.8f);
                paint.setColor(ink);
                oval.set(42f, 53f, 58f, 65f);
                c.drawArc(oval, 15f, 150f, false, paint);
            } else if (mood == Mood.SURPRISED) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ink);
                oval.set(46.5f, 57f, 53.5f, 65f);
                c.drawOval(oval, paint);
            } else if (mood == Mood.THINKING) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.6f);
                paint.setColor(ink);
                c.drawLine(45f, 61f, 55f, 59.5f, paint);
            } else if (mood == Mood.MISTAKE) {
                // Gentle supportive half-smile ("no pasa nada!")
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f);
                paint.setColor(ink);
                oval.set(44f, 57f, 57f, 64f);
                c.drawArc(oval, 20f, 120f, false, paint);
            } else if (mood == Mood.STREAK_MISS) {
                // Soft gentle pout, never harsh
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f);
                paint.setColor(ink);
                oval.set(43f, 60f, 57f, 68f);
                c.drawArc(oval, 200f, 140f, false, paint);
            }
        }

        private void drawMoodOverlay(Canvas c) {
            paint.setStyle(Paint.Style.FILL);
            if (mood == Mood.WELCOME) {
                // Friendly waving paw/hand at right
                paint.setColor(cast.bodyColor);
                c.drawCircle(83f, 56f, 6.5f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f);
                paint.setColor(cast.accentColor);
                oval.set(84f, 44f, 94f, 56f);
                c.drawArc(oval, -60f, 110f, false, paint);
            } else if (mood == Mood.THINKING) {
                // Thought bubbles top-right
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(78f, 28f, 3f, paint);
                c.drawCircle(85f, 20f, 5f, paint);
                paint.setColor(cast.accentColor);
                c.drawCircle(85f, 20f, 2f, paint);
            } else if (mood == Mood.CELEBRATE || mood == Mood.SUCCESS) {
                // Sparkle stars
                drawStarSpark(c, 16f, 24f, 5.5f, 0xFFFFCA3A);
                drawStarSpark(c, 85f, 24f, 6f, 0xFF0E8A63);
                if (mood == Mood.CELEBRATE) {
                    drawStarSpark(c, 18f, 78f, 4.8f, 0xFFDE5B43);
                    drawStarSpark(c, 84f, 76f, 5f, 0xFF0E7888);
                }
            } else if (mood == Mood.STREAK_MISS) {
                // Single cute blue droplet + hopeful sprout
                paint.setColor(0xFF5AD5E6);
                c.drawCircle(69f, 54f, 2.8f, paint);
            } else if (mood == Mood.ENCOURAGE) {
                drawStarSpark(c, 84f, 28f, 5.2f, 0xFFF2A922);
            }
        }

        private void drawStarSpark(Canvas c, float cx, float cy, float r, int color) {
            path.reset();
            path.moveTo(cx, cy - r);
            path.lineTo(cx + r * 0.32f, cy - r * 0.32f);
            path.lineTo(cx + r, cy);
            path.lineTo(cx + r * 0.32f, cy + r * 0.32f);
            path.lineTo(cx, cy + r);
            path.lineTo(cx - r * 0.32f, cy + r * 0.32f);
            path.lineTo(cx - r, cy);
            path.lineTo(cx - r * 0.32f, cy - r * 0.32f);
            path.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            c.drawPath(path, paint);
        }
    }

    /** Collectible Sticker / Achievement Shield rendered cleanly with custom emblem artwork. */
    static final class StickerBadgeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int badgeIndex;
        private final boolean unlocked;
        private final boolean darkMode;

        StickerBadgeView(Context context, int badgeIndex, boolean unlocked, boolean darkMode, String label) {
            super(context);
            this.badgeIndex = badgeIndex;
            this.unlocked = unlocked;
            this.darkMode = darkMode;
            setContentDescription(label + (unlocked ? " — ملصق مفتوح" : " — ملصق مقفل"));
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float s = Math.min(w, h) / 80f;
            c.save();
            c.translate((w - 80f * s) / 2f, (h - 80f * s) / 2f);
            c.scale(s, s);

            int[] primaries = {0xFF0E8A63, 0xFFE59B14, 0xFF0E7888, 0xFFDE5B43};
            int[] softs = {0xFFE6F6EF, 0xFFFFF5DF, 0xFFE4F4F7, 0xFFFDEEEB};
            int main = unlocked ? primaries[badgeIndex % primaries.length] : (darkMode ? 0xFF2D443B : 0xFFD2DDD8);
            int bg = unlocked ? (darkMode ? 0xFF1F352E : softs[badgeIndex % softs.length]) : (darkMode ? 0xFF192621 : 0xFFF0F2EE);

            // Hexagonal medal shield
            path.reset();
            path.moveTo(40f, 5f);
            path.lineTo(70f, 21f);
            path.lineTo(70f, 57f);
            path.lineTo(40f, 75f);
            path.lineTo(10f, 57f);
            path.lineTo(10f, 21f);
            path.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(bg);
            c.drawPath(path, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.2f);
            paint.setColor(main);
            c.drawPath(path, paint);

            paint.setStyle(Paint.Style.FILL);
            if (!unlocked) {
                // Padlock silhouette
                paint.setColor(darkMode ? 0xFF6A857C : 0xFF8CA099);
                c.drawRoundRect(new RectF(28f, 38f, 52f, 56f), 5f, 5f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3.5f);
                c.drawArc(new RectF(32f, 26f, 48f, 44f), 180f, 180f, false, paint);
            } else {
                // Starburst + jewel center
                path.reset();
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2.0 + i * Math.PI / 5.0;
                    float r = (i % 2 == 0) ? 20f : 9.5f;
                    float px = 40f + (float) Math.cos(a) * r;
                    float py = 40f + (float) Math.sin(a) * r;
                    if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                path.close();
                paint.setColor(main);
                c.drawPath(path, paint);
                paint.setColor(0xFFFFD166);
                c.drawCircle(40f, 40f, 7.5f, paint);
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(38f, 38f, 2.3f, paint);
            }
            c.restore();
        }
    }
}
