package nir.com.videopoker;

import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/** Verifies the adaptive banner actually LOADS (AdMob SDK onAdLoaded, sample unit ID)
 *  and becomes visible after a real played round. No ad content is clicked.
 *
 *  Driver note: taps use the game view's own reported action-button bounds
 *  (PokerView.debugActionBtn, written by onDraw) plus the view's on-screen
 *  location - never hardcoded screen fractions, so the wrapper top bar,
 *  device aspect and nav bar cannot shift the target. Round progress is
 *  asserted via PokerView.debugPhase BEFORE any ad wait. */
@RunWith(AndroidJUnit4.class)
public class BannerLoadScriptTest {
    private static final String TAG = "BannerCheck";
    private UiDevice d;
    private ActivityScenario<MainActivity> sc;
    private PokerView pv;
    private final int[] loc = new int[2];
    private final Rect[] btn = new Rect[1];

    private static PokerView findPokerView(View v) {
        if (v instanceof PokerView) return (PokerView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                PokerView r = findPokerView(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    private boolean waitPhase(int want, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (PokerView.debugPhase == want) return true;
            Thread.sleep(250);
        }
        return false;
    }

    /** Tap the center of the view-reported DEAL/DRAW button, in real screen coords. */
    private void tapActionButton(String what) throws Exception {
        long end = System.currentTimeMillis() + 10000;
        btn[0] = null;
        while (System.currentTimeMillis() < end) {
            sc.onActivity(a -> {
                if (pv == null) pv = findPokerView(a.findViewById(android.R.id.content));
                if (pv != null) {
                    pv.getLocationOnScreen(loc);
                    Rect r = PokerView.debugActionBtn;
                    btn[0] = r == null ? null : new Rect(r);
                }
            });
            if (pv != null && btn[0] != null) break;
            Thread.sleep(300);
        }
        if (pv == null) throw new AssertionError("PokerView not found in content hierarchy");
        if (btn[0] == null) throw new AssertionError("view never reported " + what + " button bounds");
        int x = loc[0] + btn[0].centerX(), y = loc[1] + btn[0].centerY();
        Log.i(TAG, what + " tap at screen " + x + "," + y + " (view at " + loc[0] + "," + loc[1] + ", btn=" + btn[0] + ")");
        d.click(x, y);
    }

    @Test public void bannerLoadsAfterFirstRound() throws Exception {
        d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        sc = ActivityScenario.launch(MainActivity.class);
        if (d.wait(Until.findObject(By.pkg("nir.com.videopoker")), 20000) == null)
            throw new AssertionError("app did not come to foreground");

        // UMP consent form: accept if shown (gdprApplies=0 devices skip it)
        long umpDeadline = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < umpDeadline) {
            UiObject2 consent = d.findObject(By.textContains("Consent"));
            if (consent == null) consent = d.findObject(By.textContains("Agree"));
            if (consent == null) consent = d.findObject(By.textContains("Accept"));
            if (consent != null) { Log.i(TAG, "UMP form shown - accepting"); consent.click(); break; }
            UiObject2 any = d.findObject(By.pkg("nir.com.videopoker").clazz("android.widget.Button"));
            if (any != null) break; // game UI up, no form
            Thread.sleep(500);
        }
        Thread.sleep(2000);

        // betting screen must be drawn and reporting its hooks
        if (!waitPhase(0, 20000))
            throw new AssertionError("betting screen never reported by view (phase=" + PokerView.debugPhase + ")");

        // play one real round with view-reported bounds: DEAL -> cards -> DRAW -> result
        tapActionButton("DEAL");
        if (!waitPhase(1, 15000))
            throw new AssertionError("DEAL tap did not deal cards (phase=" + PokerView.debugPhase + ")");
        Log.i(TAG, "DEAL accepted, cards dealt");
        Thread.sleep(1500);
        tapActionButton("DRAW");
        if (!waitPhase(2, 20000))
            throw new AssertionError("DRAW tap did not complete the round (phase=" + PokerView.debugPhase + ")");
        Log.i(TAG, "round completed (DEAL -> DRAW -> result); waiting for banner load");

        // bounded wait for the actual SDK load callback (fires after first round attach)
        long deadline = System.currentTimeMillis() + 90000;
        while (System.currentTimeMillis() < deadline) {
            if (AdsManager.lastBannerLoadedAt != 0) break;
            if (AdsManager.lastBannerError != null) Log.w(TAG, "load attempt failed: " + AdsManager.lastBannerError);
            Thread.sleep(2000);
        }
        if (AdsManager.lastBannerLoadedAt == 0)
            throw new AssertionError("banner did not load within 90s; lastError=" + AdsManager.lastBannerError);
        Log.i(TAG, "onAdLoaded fired, unit=" + AdsManager.lastBannerUnit);
        if (!"ca-app-pub-3940256099942544/9214589741".equals(AdsManager.lastBannerUnit))
            throw new AssertionError("unexpected banner unit: " + AdsManager.lastBannerUnit);

        // full visible banner bounds: an ad WebView with real height in the app window
        Thread.sleep(3000); // let it render
        UiObject2 web = d.wait(Until.findObject(By.pkg("nir.com.videopoker").clazz("android.webkit.WebView")), 10000);
        if (web == null) throw new AssertionError("no rendered ad WebView in app window after onAdLoaded");
        Rect b = web.getVisibleBounds();
        Log.i(TAG, "ad WebView bounds=" + b);
        if (b.height() < 40) throw new AssertionError("ad view has no visible height: " + b);

        // dismiss possible daily-bonus dialog (do not click ad content) and screenshot
        UiObject2 close = d.findObject(By.text("Close"));
        if (close != null) close.click();
        Thread.sleep(1000);
        java.io.File shot = new java.io.File("/sdcard/Download/banner_loaded.png");
        d.takeScreenshot(shot);
        Log.i(TAG, "screenshot=" + shot + " exists=" + shot.exists());

        String pkg = d.getCurrentPackageName();
        if (!"nir.com.videopoker".equals(pkg))
            throw new AssertionError("foreground=" + pkg + " (possible ad takeover - should not happen without clicks)");
        Log.i(TAG, "BANNER LOAD VERIFIED: loaded callback + rendered bounds " + b);
        sc.close();
    }
}
