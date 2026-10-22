package nir.com.videopoker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
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
        ads.initWithConsent(() -> { if (!removed) ads.attachBanner(bannerBox); });

        billing = new BillingManager(this, this);

        if (sp.getString("first_run", null) == null)
            sp.edit().putString("first_run", today()).apply();

        handler.postDelayed(this::maybeDailyBonus, 900);
        handler.postDelayed(this::maybeDay2Offer, 2000);
    }

    private View buildButtonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(4, 6, 4, 0);
        addRowButton(row, "FREE COINS", v -> freeCoins());
        addRowButton(row, "MISSIONS", v -> showMissions());
        addRowButton(row, "SHOP", v -> showShop());
        addRowButton(row, "MORE GAMES", v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.DEV_PAGE_URL))));
        removeAdsBtn = addRowButton(row, "NO ADS", v -> buyRemoveAds());
        return row;
    }

    private Button addRowButton(LinearLayout row, String label, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(11f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(0xFF3A1F66);
        btn.setMinHeight(0); btn.setMinimumHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(4, 0, 4, 0);
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
        new AlertDialog.Builder(this)
            .setTitle("Daily bonus - day " + streak)
            .setMessage("Your day " + streak + " gift: +" + bonus + " coins. Come back tomorrow to grow your streak!\n\nAll coins are virtual and free.")
            .setPositiveButton("Claim +" + bonus, (d, w) -> grantDaily(streak, bonus))
            .setNeutralButton("Watch ad for 2X", (d, w) ->
                ads.showRewarded(() -> grantDaily(streak, bonus * 2)))
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
        if (!today().equals(first)) {
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
            .setMessage("Watch a short ad for +300 free coins? Coins also refill slowly over time. All coins are virtual and free.")
            .setPositiveButton("Watch ad", (d, w) -> ads.showRewarded(() -> game.addCoins(300)))
            .setNegativeButton("Not now", null);
        if (!sp.getBoolean("ads_removed", false))
            b.setNeutralButton("Remove all ads", (d, w) -> offerRemoveAds("outofcoins"));
        b.show();
    }

    @Override public void onSpinComplete(int spinsDone, long win, long bet) {
        missions.onSpin(win, bet, game.getLevel(), spinsDone);
        ads.maybeShowInterstitial(spinsDone);
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

    @Override protected void onPause() { super.onPause(); saveState(); }
}
