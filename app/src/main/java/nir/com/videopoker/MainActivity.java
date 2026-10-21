package nir.com.videopoker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MainActivity extends Activity implements PokerView.Listener, RemoveAdsManager.Listener {
    private static final String PREFS = "videopoker", KEY = "state";
    private static final int MENU_REMOVE_ADS = 1;
    private PokerView game;
    private AdsManager ads;
    private RemoveAdsManager removeAds;
    private String removeAdsPrice;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        game = new PokerView(this);
        game.setListener(this);
        root.addView(game, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout bannerBox = new LinearLayout(this);
        root.addView(bannerBox, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String s = sp.getString(KEY, null);
        if (s != null) game.restore(s);

        ads = new AdsManager(this);
        removeAds = new RemoveAdsManager(this, sp, this);
        ads.setAdsRemoved(removeAds.isAdsRemoved());
        removeAds.start();
        ads.initWithConsent(() -> ads.attachBanner(bannerBox));
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        if (!removeAds.isAdsRemoved()) menu.add(0, MENU_REMOVE_ADS, 0, "Remove ads");
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() != MENU_REMOVE_ADS) return super.onOptionsItemSelected(item);
        String price = removeAdsPrice != null ? removeAdsPrice : removeAds.getPrice();
        if (price == null) {
            Toast.makeText(this, "Store not ready yet - try again in a moment.", Toast.LENGTH_SHORT).show();
            removeAds.buy();
            return true;
        }
        new AlertDialog.Builder(this)
            .setTitle("Remove ads")
            .setMessage("Remove all ads forever for " + price + "? Coins and gameplay stay free.")
            .setPositiveButton("Buy", (d, w) -> removeAds.buy())
            .setNegativeButton("Cancel", null)
            .show();
        return true;
    }

    @Override public void onAdsRemoved() {
        ads.setAdsRemoved(true);
        invalidateOptionsMenu();
        Toast.makeText(this, "Ads removed. Enjoy!", Toast.LENGTH_LONG).show();
    }

    @Override public void onPrice(String price) { removeAdsPrice = price; }

    @Override public void onOutOfCoins() {
        if (removeAds.isAdsRemoved()) {
            new AlertDialog.Builder(this)
                .setTitle("Out of coins")
                .setMessage("Take +300 free coins? Coins also refill slowly over time. All coins are virtual and free.")
                .setPositiveButton("+300 coins", (d, w) -> game.addCoins(300))
                .setNegativeButton("Not now", null)
                .show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Out of coins")
            .setMessage("Watch a short ad for +300 free coins? Coins also refill slowly over time. All coins are virtual and free.")
            .setPositiveButton("Watch ad", (d, w) -> ads.showRewarded(() -> game.addCoins(300)))
            .setNegativeButton("Not now", null)
            .show();
    }

    @Override public void onSpinComplete(int spinsDone) {
        ads.maybeShowInterstitial(spinsDone);
    }

    @Override public void saveState() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, game.serialize()).apply();
    }

    @Override protected void onPause() { super.onPause(); saveState(); }
}
