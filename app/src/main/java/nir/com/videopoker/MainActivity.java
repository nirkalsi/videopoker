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
    private Button removeAdsBtn;
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

        String s = sp.getString(KEY, null);
        if (s != null) game.restore(s);

        missions = new MissionsManager(this, null);

        ads = new AdsManager(this);
        ads.setAdEvents(this::onInterstitialShown);
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
        handler.postDelayed(this::maybeDay2Offer, 2000);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private GradientDrawable pill(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), 0x33FFFFFF);
        return g;
    }

    private View buildButtonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(6), dp(6), dp(6), dp(2));
        addRowButton(row, "\uD83C\uDF81 FREE", v -> freeCoins(), 0xFF2E7D32);
        addRowButton(row, "\uD83C\uDFAF MISSIONS", v -> showMissions(), 0xFF1565C0);
        addRowButton(row, "\uD83D\uDED2 SHOP", v -> showShop(), 0xFFF9A825);
        removeAdsBtn = addRowButton(row, "NO ADS", v -> buyRemoveAds(), 0xFF546E7A);
        return row;
    }

    private Button addRowButton(LinearLayout row, String label, View.OnClickListener l, int color) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(12f);
        btn.setSingleLine(true);
        btn.setPadding(0, 0, 0, 0);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
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
            cell.setBackground(pill(isToday ? 0xFFFFD54F : (claimed ? 0xFFC8E6C9 : 0xFFECEFF1), 10));
            cell.setPadding(dp(2), dp(6), dp(2), dp(6));
            TextView dl = new TextView(this);
            dl.setText("Day " + d);
            dl.setTextSize(9f);
            dl.setTextColor(0xFF37474F);
            dl.setGravity(Gravity.CENTER);
            cell.addView(dl);
            TextView dv = new TextView(this);
            dv.setText(claimed ? "\u2714" : (d == 7 ? "\uD83C\uDFC6 " + DAILY_BONUS[6] : "" + DAILY_BONUS[d - 1]));
            dv.setTextSize(10f);
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
        new AlertDialog.Builder(this)
            .setView(box)
            .setPositiveButton("Claim +" + bonus, (d, w) -> grantDaily(fStreak, bonus))
            .setNeutralButton("\u25B6 Watch ad for 2X", (d, w) ->
                ads.showRewarded(() -> grantDaily(fStreak, bonus * 2)))
            .setCancelable(false)
            .show();
    }

    private void grantDaily(int streak, long coins) {
        sp.edit().putString("daily_last", today()).putInt("daily_streak", streak).apply();
        missions.onDailyClaimed(streak);
        game.addCoins(coins);
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
        box.setPadding(pad, pad / 2, pad, 0);
        addSection(box, "Daily missions", missions.dailyMissions());
        addSection(box, "Achievements", missions.achievements(game.getLevel(), game.getSpinsDone()));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this).setTitle("Missions").setView(sv)
            .setNegativeButton("Close", null).show();
    }

    private void addSection(LinearLayout box, String title, List<MissionsManager.Item> items) {
        TextView h = new TextView(this);
        h.setText(title);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextSize(16f);
        h.setPadding(0, 12, 0, 6);
        box.addView(h);
        for (MissionsManager.Item i : items) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(this);
            String status = i.claimed ? " (claimed)" : " - " + i.progress + "/" + i.goal;
            t.setText(i.title + status + "  [+" + i.reward + "]");
            line.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            if (!i.claimed && i.done()) {
                Button c = new Button(this);
                c.setText("Claim");
                c.setOnClickListener(v -> {
                    long reward = missions.claim(i);
                    if (reward > 0) {
                        game.addCoins(reward);
                        Toast.makeText(this, "+" + reward + " coins!", Toast.LENGTH_SHORT).show();
                    }
                    c.setEnabled(false); c.setText("Done");
                    t.setText(i.title + " (claimed)  [+" + i.reward + "]");
                });
                line.addView(c);
            }
            box.addView(line);
        }
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

    private void buyRemoveAds() {
        if (billing.getDetails(BillingManager.REMOVE_ADS) != null) {
            billing.buy(this, BillingManager.REMOVE_ADS);
        } else {
            Toast.makeText(this, "Not available yet - try again soon", Toast.LENGTH_SHORT).show();
        }
    }

    private void offerRemoveAds(String why) {
        if (sp.getBoolean("ads_removed", false)) return;
        String price = billing.priceOf(BillingManager.REMOVE_ADS);
        String text = "Enjoying Video Poker? Remove banner and between-round ads forever with one small one-time purchase"
            + (price != null ? " (" + price + ")" : "") + ". Free rewarded ads for coins stay available either way.";
        new AlertDialog.Builder(this)
            .setTitle("Remove ads")
            .setMessage(text)
            .setPositiveButton(price != null ? "Remove ads - " + price : "Remove ads", (d, w) -> buyRemoveAds())
            .setNegativeButton("No thanks", null)
            .show();
    }

    private void onInterstitialShown() {
        int total = sp.getInt("inter_total", 0) + 1;
        sp.edit().putInt("inter_total", total).apply();
        if (total == 3 && !sp.getBoolean("offer3_done", false)) {
            sp.edit().putBoolean("offer3_done", true).apply();
            handler.postDelayed(() -> offerRemoveAds("after3"), 1500);
        }
    }

    private void maybeDay2Offer() {
        if (sp.getBoolean("ads_removed", false) || sp.getBoolean("offer_day2_done", false)) return;
        String first = sp.getString("first_run", today());
        if (!today().equals(first) && today().equals(sp.getString("daily_last", ""))) {
            sp.edit().putBoolean("offer_day2_done", true).apply();
            offerRemoveAds("day2");
        }
    }

    private void applyAdsRemoved() {
        bannerBox.removeAllViews();
        if (removeAdsBtn != null) removeAdsBtn.setVisibility(View.GONE);
    }

    // ---------- PokerView.Listener ----------
    @Override public void onOutOfCoins() {
        AlertDialog.Builder b = new AlertDialog.Builder(this)
            .setTitle("Out of coins")
            .setMessage("Watch a short ad for +300 free coins? Coins also refill slowly over time. Coins are virtual only - no cash value, no prizes.")
            .setPositiveButton("Watch ad", (d, w) -> ads.showRewarded(() -> game.addCoins(300)))
            .setNegativeButton("Not now", null);
        if (!sp.getBoolean("ads_removed", false))
            b.setNeutralButton("Remove all ads", (d, w) -> offerRemoveAds("outofcoins"));
        b.show();
    }

    @Override public void onSpinComplete(int spinsDone, long win, long bet) {
        missions.onSpin(win, bet, game.getLevel(), spinsDone);
        ads.maybeShowInterstitial(spinsDone);
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

    @Override public void onLevelUp(int level, long reward) {
        Toast.makeText(this, "Level " + level + "! +" + reward + " coins", Toast.LENGTH_SHORT).show();
    }

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

    @Override protected void onPause() { GameSounds.onAppPause(); super.onPause(); saveState(); }

    @Override protected void onResume() {
        super.onResume();
        GameSounds.onAppResume();
    }

    @Override protected void onDestroy() {
        GameSounds.release();
        super.onDestroy();
    }
}
