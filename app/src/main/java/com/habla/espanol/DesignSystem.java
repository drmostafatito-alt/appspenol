package com.habla.espanol;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Unified Design System for Spanish 1000.
 * ProvidesLight & Dark color palettes, Cairo + Nunito typography scale,
 * spacing & radius tokens, accessible buttons, cards, badges, dialogs,
 * charts, and Empty / Loading / Error states.
 */
final class DesignSystem {
    // Spacing tokens (dp)
    static final int SP_XS = 4, SP_SM = 8, SP_MD = 12, SP_LG = 16, SP_XL = 20, SP_XXL = 24, SP_XXXL = 32;
    // Border radius tokens (dp)
    static final int RAD_SM = 10, RAD_MD = 14, RAD_LG = 18, RAD_XL = 24, RAD_PILL = 999;

    // Semantic Light Palette (Andalusian Emerald + Mediterranean Turquoise + Warm Saffron + Creamy Alabaster)
    static final int L_BG = 0xFFFAF8F2;
    static final int L_CARD = 0xFFFFFFFF;
    static final int L_SUBTLE = 0xFFF3EFE6;
    static final int L_BORDER = 0xFFDCE6E0;
    static final int L_INK = 0xFF1A2B27;
    static final int L_MUTED = 0xFF556B65;
    static final int L_PRIMARY = 0xFF0E8A63;
    static final int L_PRIMARY_DARK = 0xFF096347;
    static final int L_PRIMARY_SOFT = 0xFFE6F6EF;
    static final int L_SECONDARY = 0xFF0E7888;
    static final int L_SECONDARY_SOFT = 0xFFE4F4F7;
    static final int L_ACCENT = 0xFFE59B14;
    static final int L_ACCENT_SOFT = 0xFFFFF5DF;
    static final int L_CORAL = 0xFFDE5B43;
    static final int L_CORAL_SOFT = 0xFFFDEEEB;
    static final int L_ERROR = 0xFFC03546;
    static final int L_ERROR_SOFT = 0xFFFCEBEC;

    private final Context ctx;
    private final Typeface cairoRegular, cairoBold, nunitoRegular, nunitoBold;
    private boolean darkMode;
    private boolean reducedMotion;

    DesignSystem(Context ctx, Typeface cairoRegular, Typeface cairoBold, Typeface nunitoRegular, Typeface nunitoBold) {
        this.ctx = ctx;
        this.cairoRegular = cairoRegular;
        this.cairoBold = cairoBold;
        this.nunitoRegular = nunitoRegular;
        this.nunitoBold = nunitoBold;
    }

    void configure(boolean darkMode, boolean reducedMotion) {
        this.darkMode = darkMode;
        this.reducedMotion = reducedMotion;
    }

    boolean isDark() { return darkMode; }
    boolean isReducedMotion() { return reducedMotion; }

    int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    int bgColor() { return darkMode ? 0xFF0E1714 : L_BG; }
    int cardColor() { return darkMode ? 0xFF172520 : L_CARD; }
    int subtleColor() { return darkMode ? 0xFF20322B : L_SUBTLE; }
    int borderColor() { return darkMode ? 0xFF2D443B : L_BORDER; }
    int inkColor() { return darkMode ? 0xFFF1F7F4 : L_INK; }
    int mutedColor() { return darkMode ? 0xFFA7BEB6 : L_MUTED; }
    int primaryColor() { return darkMode ? 0xFF2FBF8F : L_PRIMARY; }
    int primaryDarkColor() { return darkMode ? 0xFF1E9B72 : L_PRIMARY_DARK; }
    int primarySoftColor() { return darkMode ? 0xFF17362B : L_PRIMARY_SOFT; }
    int secondaryColor() { return darkMode ? 0xFF4EC8DA : L_SECONDARY; }
    int secondarySoftColor() { return darkMode ? 0xFF16333A : L_SECONDARY_SOFT; }
    int accentColor() { return darkMode ? 0xFFFFCA4B : L_ACCENT; }
    int accentSoftColor() { return darkMode ? 0xFF3A2E14 : L_ACCENT_SOFT; }
    int coralColor() { return darkMode ? 0xFFFF866E : L_CORAL; }
    int coralSoftColor() { return darkMode ? 0xFF3D211C : L_CORAL_SOFT; }
    int errorColor() { return darkMode ? 0xFFFF8E9B : L_ERROR; }
    int errorSoftColor() { return darkMode ? 0xFF3F1F25 : L_ERROR_SOFT; }

    Typeface fontFor(String text, boolean bold) {
        boolean arabic = text == null || text.isEmpty() || text.matches("(?s).*[\\u0600-\\u06ff\\u0750-\\u077f\\u08a0-\\u08ff].*");
        return arabic ? (bold ? cairoBold : cairoRegular) : (bold ? nunitoBold : nunitoRegular);
    }

    GradientDrawable shape(int fillArgb, int strokeArgb, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillArgb);
        d.setCornerRadius(dp(radiusDp));
        if (strokeArgb != 0) d.setStroke(dp(1.5f), strokeArgb);
        return d;
    }

    GradientDrawable gradientShape(int startArgb, int endArgb, int radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{startArgb, endArgb});
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    TextView text(String value, int sizeSp, int colorArgb, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setText(value);
        tv.setTextSize(sizeSp);
        tv.setTextColor(colorArgb);
        tv.setTypeface(fontFor(value, bold));
        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        tv.setLineSpacing(dp(3), 1f);
        return tv;
    }

    TextView spanishText(String value, int sizeSp, int colorArgb, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setText(value);
        tv.setTextSize(sizeSp);
        tv.setTextColor(colorArgb);
        tv.setTypeface(bold ? nunitoBold : nunitoRegular);
        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        tv.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        tv.setLineSpacing(dp(3), 1f);
        return tv;
    }

    Button styledButton(String label, int fillArgb, int textArgb, int strokeArgb, int radiusDp) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15.5f);
        b.setTextColor(textArgb);
        b.setTypeface(fontFor(label, true));
        b.setMinHeight(dp(52));
        b.setMinimumHeight(dp(52));
        b.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable base = shape(fillArgb, strokeArgb, radiusDp);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(darkMode ? 0x33FFFFFF : 0x22000000),
                base,
                null
        );
        b.setBackground(ripple);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }

    Button primaryButton(String label) {
        return styledButton(label, darkMode ? 0xFF1D9D73 : L_PRIMARY, Color.WHITE, 0, RAD_LG);
    }

    Button secondaryButton(String label) {
        return styledButton(label, cardColor(), secondaryColor(), borderColor(), RAD_LG);
    }

    Button turquoiseButton(String label) {
        return styledButton(label, darkMode ? 0xFF167E8E : L_SECONDARY, Color.WHITE, 0, RAD_LG);
    }

    Button accentButton(String label) {
        return styledButton(label, accentSoftColor(), darkMode ? 0xFFFFD875 : 0xFF9A6300, accentColor(), RAD_LG);
    }

    Button ghostButton(String label) {
        return styledButton(label, subtleColor(), mutedColor(), 0, RAD_LG);
    }

    Button iconButton(String symbol, String contentDesc, int fillArgb, int fgArgb) {
        Button b = styledButton(symbol, fillArgb, fgArgb, fillArgb == cardColor() ? borderColor() : 0, RAD_MD);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(dp(48));
        b.setMinimumWidth(dp(48));
        b.setGravity(Gravity.CENTER);
        b.setContentDescription(contentDesc);
        return b;
    }

    EditText inputField(String hint, boolean spanishLtr) {
        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setTypeface(spanishLtr ? nunitoRegular : cairoRegular);
        et.setTextSize(17);
        et.setTextColor(inkColor());
        et.setHintTextColor(mutedColor());
        et.setTextDirection(spanishLtr ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_FIRST_STRONG);
        et.setPadding(dp(16), dp(13), dp(16), dp(13));
        et.setBackground(shape(cardColor(), borderColor(), RAD_MD));
        return et;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(SP_XL), dp(SP_LG), dp(SP_XL), dp(SP_LG));
        c.setBackground(shape(cardColor(), borderColor(), RAD_XL));
        if (Build.VERSION.SDK_INT >= 21 && !darkMode) c.setElevation(dp(2));
        return c;
    }

    LinearLayout tintedCard(int fillArgb, int strokeArgb) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(SP_XL), dp(SP_LG), dp(SP_XL), dp(SP_LG));
        c.setBackground(shape(fillArgb, strokeArgb, RAD_XL));
        return c;
    }

    LinearLayout heroCard(int startArgb, int endArgb) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(SP_XL), dp(SP_XL), dp(SP_XL), dp(SP_XL));
        c.setBackground(gradientShape(startArgb, endArgb, RAD_XL));
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(4));
        return c;
    }

    TextView pillBadge(String label, int bgArgb, int fgArgb) {
        TextView tv = text(label, 12, fgArgb, true);
        tv.setPadding(dp(11), dp(4), dp(11), dp(4));
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(shape(bgArgb, 0, RAD_PILL));
        return tv;
    }

    ProgressBar progressBar(int value, int max, int tintArgb) {
        ProgressBar p = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        int safeMax = Math.max(1, max);
        int clamped = Math.max(0, Math.min(value, safeMax));
        p.setMax(safeMax);
        p.setProgress(clamped);
        p.setProgressTintList(ColorStateList.valueOf(tintArgb));
        p.setProgressBackgroundTintList(ColorStateList.valueOf(borderColor()));
        p.setMinimumHeight(dp(10));
        p.setContentDescription("التقدم " + clamped + " من " + safeMax);
        return p;
    }

    LinearLayout mascotBanner(CharacterArt.Cast cast, CharacterArt.Mood mood, String title, String message) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(shape(cardColor(), borderColor(), RAD_XL));

        CharacterArt.CharacterView cv = new CharacterArt.CharacterView(ctx, cast, mood, darkMode, reducedMotion);
        row.addView(cv, new LinearLayout.LayoutParams(dp(72), dp(72)));

        LinearLayout copy = new LinearLayout(ctx);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMargins(dp(12), 0, dp(12), 0);
        if (title != null && !title.isEmpty()) {
            copy.addView(text(title, 15, primaryColor(), true));
        }
        copy.addView(text(message, 14, inkColor(), false));
        row.addView(copy, cp);
        return row;
    }

    LinearLayout emptyState(CharacterArt.Cast cast, String title, String subtitle, String actionLabel, Runnable onAction) {
        LinearLayout box = card();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        CharacterArt.CharacterView cv = new CharacterArt.CharacterView(ctx, cast, CharacterArt.Mood.ENCOURAGE, darkMode, reducedMotion);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(92), dp(92));
        lp.bottomMargin = dp(12);
        box.addView(cv, lp);
        TextView t = text(title, 19, inkColor(), true);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        TextView s = text(subtitle, 14, mutedColor(), false);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(6);
        sp.bottomMargin = dp(14);
        box.addView(s, sp);
        if (actionLabel != null && onAction != null) {
            Button btn = primaryButton(actionLabel);
            btn.setOnClickListener(v -> onAction.run());
            box.addView(btn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return box;
    }

    LinearLayout loadingState(String message) {
        LinearLayout box = card();
        box.setGravity(Gravity.CENTER);
        ProgressBar spinner = new ProgressBar(ctx);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(primaryColor()));
        box.addView(spinner, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView tv = text(message, 15, mutedColor(), true);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        box.addView(tv, lp);
        return box;
    }

    LinearLayout errorState(String title, String detail, String retryLabel, Runnable onRetry) {
        LinearLayout box = tintedCard(errorSoftColor(), errorColor());
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        CharacterArt.CharacterView cv = new CharacterArt.CharacterView(ctx, CharacterArt.Cast.SOL, CharacterArt.Mood.MISTAKE, darkMode, reducedMotion);
        box.addView(cv, new LinearLayout.LayoutParams(dp(80), dp(80)));
        TextView t = text(title, 18, errorColor(), true);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        TextView d = text(detail, 14, inkColor(), false);
        d.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dpLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dpLp.topMargin = dp(6);
        dpLp.bottomMargin = dp(12);
        box.addView(d, dpLp);
        if (retryLabel != null && onRetry != null) {
            Button b = primaryButton(retryLabel);
            b.setOnClickListener(v -> onRetry.run());
            box.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return box;
    }

    void showThemedDialog(String title, View customBody, String positiveLabel, Runnable onPositive, String negativeLabel) {
        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        wrapper.setPadding(dp(20), dp(18), dp(20), dp(16));
        wrapper.setBackground(shape(cardColor(), borderColor(), RAD_XL));

        TextView head = text(title, 19, inkColor(), true);
        wrapper.addView(head);
        if (customBody != null) {
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = dp(12);
            bp.bottomMargin = dp(14);
            wrapper.addView(customBody, bp);
        }

        AlertDialog dialog = new AlertDialog.Builder(ctx).setView(wrapper).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (negativeLabel != null) {
            Button neg = ghostButton(negativeLabel);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            np.leftMargin = dp(8);
            neg.setOnClickListener(v -> dialog.dismiss());
            actions.addView(neg, np);
        }
        Button pos = primaryButton(positiveLabel != null ? positiveLabel : "حسنًا");
        pos.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPositive != null) onPositive.run();
        });
        actions.addView(pos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        wrapper.addView(actions);
        dialog.show();
    }

    /** Crisp Canvas circular ring progress indicator for Dashboard & Results. */
    static final class RingProgressView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private final int percent;
        private final String centerTop;
        private final String centerSub;
        private final int ringColor, trackColor, inkColor, mutedColor;
        private final Typeface boldFont, regFont;

        RingProgressView(Context ctx, int percent, String centerTop, String centerSub, int ringColor, int trackColor, int inkColor, int mutedColor, Typeface boldFont, Typeface regFont) {
            super(ctx);
            this.percent = Math.max(0, Math.min(100, percent));
            this.centerTop = centerTop;
            this.centerSub = centerSub;
            this.ringColor = ringColor;
            this.trackColor = trackColor;
            this.inkColor = inkColor;
            this.mutedColor = mutedColor;
            this.boldFont = boldFont;
            this.regFont = regFont;
            setContentDescription(centerSub + ": " + centerTop);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            float size = Math.min(w, h);
            float stroke = size * 0.11f;
            float pad = stroke * 0.8f;
            arc.set((w - size) / 2f + pad, (h - size) / 2f + pad, (w + size) / 2f - pad, (h + size) / 2f - pad);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(trackColor);
            c.drawOval(arc, paint);

            paint.setColor(ringColor);
            c.drawArc(arc, -90f, 360f * percent / 100f, false, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(boldFont);
            paint.setColor(inkColor);
            paint.setTextSize(size * 0.21f);
            c.drawText(centerTop, w / 2f, h / 2f + size * 0.02f, paint);

            if (centerSub != null && !centerSub.isEmpty()) {
                paint.setTypeface(regFont);
                paint.setColor(mutedColor);
                paint.setTextSize(size * 0.11f);
                c.drawText(centerSub, w / 2f, h / 2f + size * 0.18f, paint);
            }
        }
    }

    /** Clean 7-bar Canvas chart backed strictly by real daily data. */
    static final class BarChartView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bar = new RectF();
        private final String[] labels;
        private final int[] values;
        private final int barColor, emptyColor, inkColor, mutedColor;
        private final Typeface font;

        BarChartView(Context ctx, String[] labels, int[] values, int barColor, int emptyColor, int inkColor, int mutedColor, Typeface font) {
            super(ctx);
            this.labels = labels;
            this.values = values;
            this.barColor = barColor;
            this.emptyColor = emptyColor;
            this.inkColor = inkColor;
            this.mutedColor = mutedColor;
            this.font = font;
            setContentDescription("مخطط الأداء الأسبوعي");
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0 || values.length == 0) return;
            int maxV = 1;
            for (int v : values) if (v > maxV) maxV = v;
            float d = getResources().getDisplayMetrics().density;
            float topPad = 22f * d, bottomPad = 26f * d;
            float chartH = Math.max(20f, h - topPad - bottomPad);
            float slot = w / values.length;
            float barW = Math.min(28f * d, slot * 0.54f);

            paint.setTypeface(font);
            paint.setTextAlign(Paint.Align.CENTER);

            for (int i = 0; i < values.length; i++) {
                float cx = slot * i + slot / 2f;
                float ratio = values[i] <= 0 ? 0.06f : (float) values[i] / maxV;
                float bh = Math.max(6f * d, chartH * ratio);
                bar.set(cx - barW / 2f, topPad + chartH - bh, cx + barW / 2f, topPad + chartH);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(values[i] > 0 ? barColor : emptyColor);
                c.drawRoundRect(bar, 8f * d, 8f * d, paint);

                if (values[i] > 0) {
                    paint.setColor(inkColor);
                    paint.setTextSize(10.5f * d);
                    c.drawText(String.valueOf(values[i]), cx, bar.top - 5f * d, paint);
                }
                paint.setColor(mutedColor);
                paint.setTextSize(10.5f * d);
                c.drawText(labels[i], cx, h - 7f * d, paint);
            }
        }
    }
}
