package nir.com.videopoker;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Casino-styled rows for the missions/achievements dialog. Visual only - claim logic stays in MainActivity. */
final class MissionViews {
    interface Claimer { void onClaim(MissionsManager.Item i, Button btn); }

    static final int GOLD = 0xFFFFD700;
    private static final int TEXT = 0xFFE8ECF4;
    private static final int DIM = 0xFF93A1C4;

    private MissionViews() {}

    private static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Rounded dark-glass dialog background with a thin gold frame. */
    static GradientDrawable dialogBg() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF2142450, 0xF20A1230});
        g.setCornerRadius(48f);
        g.setStroke(4, 0x88FFD700);
        return g;
    }

    static TextView title(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(GOLD);
        t.setTextSize(21f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(c, 16), 0, dp(c, 4));
        return t;
    }

    static TextView header(Context c, String text) {
        TextView h = new TextView(c);
        h.setText(text);
        h.setTextColor(0xFFFFE082);
        h.setTextSize(13f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setLetterSpacing(0.12f);
        h.setPadding(0, dp(c, 14), 0, dp(c, 6));
        return h;
    }

    private static GradientDrawable medal(Context c, MissionsManager.Item i) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (i.claimed) { d.setColor(0xFFFFD700); d.setStroke(dp(c, 1), 0xFFFFF3C4); }
        else if (i.done()) { d.setColor(0xFF4FC3F7); d.setStroke(dp(c, 1), 0xFFB3E5FC); }
        else { d.setColor(0xFF2A3A66); d.setStroke(dp(c, 1), 0xFF46598C); }
        return d;
    }

    /** Thin progress track; fill width is the item's progress fraction. */
    private static View progressBar(Context c, MissionsManager.Item i) {
        LinearLayout track = new LinearLayout(c);
        track.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1B2A52); bg.setCornerRadius(dp(c, 2));
        track.setBackground(bg);
        float frac = i.goal > 0 ? Math.min(1f, (float) i.progress / (float) i.goal) : 1f;
        View fill = new View(c);
        GradientDrawable fg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFFFE082, 0xFFFFB300});
        fg.setCornerRadius(dp(c, 2));
        fill.setBackground(fg);
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(c, 4), Math.max(frac, 0.001f)));
        View rest = new View(c);
        track.addView(rest, new LinearLayout.LayoutParams(0, dp(c, 4), Math.max(1f - frac, 0.001f)));
        return track;
    }

    private static TextView rewardPill(Context c, MissionsManager.Item i) {
        TextView p = new TextView(c);
        p.setText(i.reward > 0 ? "+" + i.reward : "badge");
        p.setTextSize(12f);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextColor(i.reward > 0 ? GOLD : 0xFFB0BEC5);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x33141414);
        g.setCornerRadius(dp(c, 10));
        g.setStroke(dp(c, 1), i.reward > 0 ? 0x66FFD700 : 0x44FFFFFF);
        p.setBackground(g);
        p.setGravity(Gravity.CENTER);
        int ph = dp(c, 3), pv = dp(c, 2);
        p.setPadding(ph * 2, pv, ph * 2, pv);
        return p;
    }

    static View row(Context c, MissionsManager.Item i, Claimer claimer) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 7), 0, dp(c, 7));

        View m = new View(c);
        int ms = dp(c, 14);
        m.setBackground(medal(c, i));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ms, ms);
        mlp.setMargins(0, 0, dp(c, 10), 0);
        row.addView(m, mlp);

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(c);
        t.setText(i.title);
        t.setTextColor(i.claimed ? DIM : TEXT);
        t.setTextSize(14f);
        if (i.claimed) t.setTypeface(Typeface.DEFAULT);
        TextView s = new TextView(c);
        s.setText(i.claimed ? "claimed" : i.progress + " / " + i.goal);
        s.setTextColor(DIM);
        s.setTextSize(11f);
        mid.addView(t);
        mid.addView(s);
        if (!i.claimed) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.setMargins(0, dp(c, 3), dp(c, 8), 0);
            mid.addView(progressBar(c, i), blp);
        }
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

        if (i.claimed) {
            row.addView(rewardPill(c, i));
        } else if (i.done() && claimer != null) {
            Button b = new Button(c);
            b.setText("Claim");
            b.setTextColor(0xFF1A1206);
            b.setTextSize(12f);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setAllCaps(false);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFFFFE082, 0xFFFFB300});
            g.setCornerRadius(dp(c, 14));
            b.setBackground(g);
            b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
            b.setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
            b.setOnClickListener(v -> claimer.onClaim(i, b));
            row.addView(b);
        } else {
            row.addView(rewardPill(c, i));
        }
        return row;
    }
}
