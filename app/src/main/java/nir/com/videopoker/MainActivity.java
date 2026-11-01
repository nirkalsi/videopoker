package nir.com.videopoker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity implements PokerView.Listener, BillingManager.Listener {
    private static final String PREFS = "videopoker", KEY = "state";
    private static final long[] DAILY_BONUS = {200, 300, 400, 500, 700, 900, 1200};

    private PokerView game;
    private AdsManager ads;
    private BillingManager billing;
    private MissionsManager missions;
    private SharedPreferences sp;
    private LinearLayout bannerBox;
    private final Handler handler = new Handler();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF120826);
        root.addView(buildButtonRow());
        game = new PokerView(this);
        game.setListener(this);
        root.addView(game, new LinearLayout.LayoutParams(-1, 0, 1f));
        bannerBox = new LinearLayout(this);
        root.addView(bannerBox, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        applySystemBarInsets(root);

        String s = sp.getString(KEY, null);
        if (s != null) game.restore(s);

        missions = new MissionsManager(this, null);

        ads = new AdsManager(this);
        boolean removed = sp.getBoolean("ads_removed", false) || sp.getBoolean("remove_ads", false);
        if (removed) sp.edit().putBoolean("ads_removed", true).apply();
        ads.setAdsRemoved(removed);
        if (removed) applyAdsRemoved();
        ads.initWithConsent(() -> {
            if (!removed && sp.getInt("rounds_done", 0) > 0) ads.attachBanner(bannerBox);
        });

        billing = new BillingManager(this, this);

        if (sp.getString("first_run", null) == null)
            sp.edit().putString("first_run", today()).apply();

        if (sp.getInt("rounds_done", 0) > 0) handler.postDelayed(this::maybeDailyBonus, 900);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    // Edge-to-edge is enforced on API 35+ (targetSdk 36): pad the content below the status bar and above the nav bar.
    private void applySystemBarInsets(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
    }

    private GradientDrawable pill(int color, int radiusDp) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] * 1.38f);
        int lighter = Color.HSVToColor(hsv);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{lighter, color});
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), 0x66FFFFFF);
        return g;
    }

    private View buildButtonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(6), dp(6), dp(6), dp(2));
        addRowButton(row, "\u25B6 WATCH AD", v -> freeCoins(), 0xFF2E7D32);
        addRowButton(row, "\uD83C\uDFAF MISSIONS", v -> showMissions(), 0xFF1565C0);
        addRowButton(row, "\uD83D\uDED2 SHOP", v -> showShop(), 0xFFF9A825);
        return row;
    }

    private Button addRowButton(LinearLayout row, String label, View.OnClickListener l, int color) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(12.5f);
        btn.setSingleLine(true);
        btn.setPadding(0, 0, 0, 0);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setLetterSpacing(0.06f);
        btn.setElevation(dp(4));
        btn.setBackground(pill(color, 22));
        btn.setMinHeight(0); btn.setMinimumHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(btn, lp);
        btn.setOnClickListener(l);
        return btn;
    }

    // ---------- daily bonus ----------
    private String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
    private String yesterday() {
        Calendar c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    private void maybeDailyBonus() {
        String last = sp.getString("daily_last", "");
        if (today().equals(last)) return;
        int streak = yesterday().equals(last) ? sp.getInt("daily_streak", 0) + 1 : 1;
        long bonus = DAILY_BONUS[Math.min(streak, DAILY_BONUS.length) - 1];

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(12), dp(16), dp(12), dp(4));

        TextView gift = new TextView(this);
        gift.setText("\uD83C\uDF81");
        gift.setTextSize(44f);
        gift.setGravity(Gravity.CENTER);
        box.addView(gift);

        TextView title = new TextView(this);
        title.setText("Daily Bonus - Day " + streak);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(6), 0, dp(10));
        box.addView(title);

        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setGravity(Gravity.CENTER);
        for (int d = 1; d <= 7; d++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            boolean isToday = d == Math.min(streak, 7);
            boolean claimed = d < streak;
            boolean isDay7 = d == 7;
            cell.setBackground(pill(isToday || isDay7 ? 0xFFFFD54F : (claimed ? 0xFFC8E6C9 : 0xFFECEFF1), 10));
            cell.setPadding(dp(2), dp(6), dp(2), dp(6));
            TextView dl = new TextView(this);
            dl.setText("Day " + d);
            dl.setTextSize(9f);
            dl.setTextColor(0xFF37474F);
            dl.setGravity(Gravity.CENTER);
            cell.addView(dl);
            TextView dv = new TextView(this);
            dv.setText(claimed ? "\u2714" : (d == 7 ? "\uD83C\uDFC6 " + DAILY_BONUS[6] : "" + DAILY_BONUS[d - 1]));
            dv.setTextSize(isDay7 ? 12f : 10f);
            dv.setTypeface(Typeface.DEFAULT_BOLD);
            dv.setTextColor(isToday ? 0xFF6D4C00 : 0xFF37474F);
            dv.setGravity(Gravity.CENTER);
            cell.addView(dv);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            clp.setMargins(dp(2), 0, dp(2), 0);
            track.addView(cell, clp);
        }
        box.addView(track, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("Today's gift: +" + bonus + " coins. Coins are virtual only - no cash value, no prizes.");
        sub.setTextSize(13f);
        sub.setTextColor(0xFFB0BEC5);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(10), 0, dp(4));
        box.addView(sub);

        final int fStreak = streak;
        AlertDialog dlg = new AlertDialog.Builder(this)
            .setView(box)
            .setCancelable(false)
            .create();

        // 2X ad option is the visual primary (polish doc A7); claim is secondary text
        TextView ad2x = new TextView(this);
        ad2x.setText("\u25B6 WATCH AD FOR 2X \u2728");
        ad2x.setTextSize(16f);
        ad2x.setTypeface(Typeface.DEFAULT_BOLD);
        ad2x.setTextColor(0xFFFFFFFF);
        ad2x.setGravity(Gravity.CENTER);
        ad2x.setBackground(pill(0xFF7B1FA2, 22));
        ad2x.setPadding(dp(10), dp(12), dp(10), dp(12));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.setMargins(dp(8), dp(10), dp(8), 0);
        box.addView(ad2x, alp);
        ad2x.setOnClickListener(v -> { dlg.dismiss(); ads.showRewarded(() -> grantDaily(fStreak, bonus * 2)); });

        TextView claim = new TextView(this);
        claim.setText("Claim +" + bonus);
        claim.setTextSize(13f);
        claim.setTextColor(0xFFB0BEC5);
        claim.setGravity(Gravity.CENTER);
        claim.setPadding(dp(10), dp(8), dp(10), dp(10));
        box.addView(claim);
        claim.setOnClickListener(v -> { dlg.dismiss(); grantDaily(fStreak, bonus); });

        dlg.show();
        animateDialogIn(box);
    }


    /** Pop-in entrance animation for modal dialogs (daily bonus etc.). */
    private void animateDialogIn(android.view.View content) {
        content.setAlpha(0f);
        content.setScaleX(0.8f);
        content.setScaleY(0.8f);
        content.post(() -> content.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(280)
            .setInterpolator(new android.view.animation.OvershootInterpolator())
            .start());
    }

    private void grantDaily(int streak, long coins) {
        sp.edit().putString("daily_last", today()).putInt("daily_streak", streak).apply();
        missions.onDailyClaimed(streak);
        game.addCoins(coins);
        game.coinBurst();
        Toast.makeText(this, "+" + coins + " daily coins!", Toast.LENGTH_SHORT).show();
    }

    // ---------- rewarded free coins ----------
    private void freeCoins() {
        long last = sp.getLong("last_free_ad", 0);
        long wait = 60000 - (System.currentTimeMillis() - last);
        if (wait > 0) {
            Toast.makeText(this, "Next free-coin ad in " + (wait / 1000 + 1) + "s", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ads.hasRewarded()) {
            Toast.makeText(this, "Ad not ready yet - try again in a moment", Toast.LENGTH_SHORT).show();
            return;
        }
        sp.edit().putLong("last_free_ad", System.currentTimeMillis()).apply();
        ads.showRewarded(() -> {
            game.addCoins(300);
            Toast.makeText(this, "+300 coins!", Toast.LENGTH_SHORT).show();
        });
    }

    // ---------- missions & achievements ----------
    private void showMissions() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, pad / 2);
        box.addView(MissionViews.title(this, "Missions"));
        MissionViews.Claimer claimer = (item, btn) -> {
            long reward = missions.claim(item);
            if (reward > 0) {
                game.addCoins(reward);
                Toast.makeText(this, "+" + reward + " coins!", Toast.LENGTH_SHORT).show();
            }
            btn.setEnabled(false);
            btn.setText("Done");
        };
        box.addView(MissionViews.header(this, "DAILY MISSIONS"));
        for (MissionsManager.Item i : missions.dailyMissions()) box.addView(MissionViews.row(this, i, claimer));
        box.addView(MissionViews.header(this, "ACHIEVEMENTS"));
        for (MissionsManager.Item i : missions.achievements(game.getLevel(), game.getSpinsDone()))
            box.addView(MissionViews.row(this, i, claimer));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog d = new AlertDialog.Builder(this).setView(sv)
            .setNegativeButton("Close", null).create();
        d.show();
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(MissionViews.dialogBg());
    }

    // ---------- shop & remove ads ----------
    private void showShop() {
        if (!billing.hasProducts()) {
            new AlertDialog.Builder(this).setTitle("Coin shop")
                .setMessage("The coin shop is not available yet. Coins are virtual only - no cash value, no prizes.")
                .setPositiveButton("OK", null).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView note = new TextView(this);
        note.setText("Coins are virtual only - no cash value, no prizes, 18+.");
        note.setPadding(0, 0, 0, 12);
        box.addView(note);
        for (int i = 0; i < BillingManager.PACK_IDS.length; i++) {
            String id = BillingManager.PACK_IDS[i];
            String price = billing.priceOf(id);
            if (price == null) continue;
            Button b = new Button(this);
            b.setText(String.format("%,d", BillingManager.PACK_COINS[i]) + " coins - " + price);
            b.setOnClickListener(v -> billing.buy(this, id));
            box.addView(b);
        }
        new AlertDialog.Builder(this).setTitle("Coin shop").setView(box)
            .setNegativeButton("Close", null).show();
    }

    private void applyAdsRemoved() {
        bannerBox.removeAllViews();
    }

    // ---------- PokerView.Listener ----------
    @Override public void onOutOfCoins() {
        AlertDialog.Builder b = new AlertDialog.Builder(this)
            .setTitle("Out of coins")
            .setMessage("Watch a short ad for +300 free coins? Coins also refill slowly over time. Coins are virtual only - no cash value, no prizes.")
            .setPositiveButton("Watch ad", (d, w) -> ads.showRewarded(() -> game.addCoins(300)))
            .setNegativeButton("Not now", null);
        b.show();
    }

    @Override public void onSpinComplete(int spinsDone, long win, long bet, int hands, int winHands, int bestRow) {
        missions.onSpinN(win, bet, hands, winHands, bestRow, game.getLevel(), spinsDone);
        if (sp.getInt("rounds_done", 0) == 0) {
            sp.edit().putInt("rounds_done", 1).apply();
            if (!sp.getBoolean("ads_removed", false)) ads.attachBanner(bannerBox);
            handler.postDelayed(this::maybeDailyBonus, 1200);
        }
    }

    @Override public void onBigWin(long win, long bet) {
        if (sp.getBoolean("review_prompted", false)) return;
        sp.edit().putBoolean("review_prompted", true).apply();
        handler.postDelayed(() -> {
            ReviewManager manager = ReviewManagerFactory.create(this);
            manager.requestReviewFlow().addOnCompleteListener(task -> {
                if (task.isSuccessful())
                    manager.launchReviewFlow(this, task.getResult());
            });
        }, 1800);
    }

    @Override public void onLevelUp(int level, long reward) { /* in-game celebration overlay handles this */ }

    @Override public void saveState() {
        sp.edit().putString(KEY, game.serialize()).apply();
    }

    // ---------- BillingManager.Listener ----------
    @Override public void onAdsRemovedOwned() {
        if (sp.getBoolean("ads_removed", false)) return;
        sp.edit().putBoolean("ads_removed", true).apply();
        runOnUiThread(() -> {
            ads.setAdsRemoved(true);
            applyAdsRemoved();
            Toast.makeText(this, "Ads removed - thank you!", Toast.LENGTH_LONG).show();
        });
    }

    @Override public void onCoinsPurchased(long coins) {
        runOnUiThread(() -> {
            game.addCoins(coins);
            Toast.makeText(this, String.format("+%,d coins - thank you!", coins), Toast.LENGTH_LONG).show();
        });
    }

    @Override public void onProductsReady() { }

    @Override protected void onPause() { GameSounds.stopMusic(); GameSounds.onAppPause(); super.onPause(); saveState(); }

    @Override protected void onResume() {
        super.onResume();
        GameSounds.onAppResume();
        GameSounds.startMusic(this);
    }

    @Override protected void onDestroy() {
        GameSounds.release();
        super.onDestroy();
    }
}
