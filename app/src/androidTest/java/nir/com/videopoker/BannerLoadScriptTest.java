package nir.com.videopoker;

import android.graphics.Rect;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/** Verifies the adaptive banner actually LOADS (AdMob SDK onAdLoaded, sample unit ID)
 *  and becomes visible after a real played round. No ad content is clicked. */
@RunWith(AndroidJUnit4.class)
public class BannerLoadScriptTest {
    private static final String TAG = "BannerCheck";
    private UiDevice d;
    private Rect v;

    private void tap(float fx, float fy) {
        d.click((int) (v.left + v.width() * fx), (int) (v.top + v.height() * fy));
    }

    @Test public void bannerLoadsAfterFirstRound() throws Exception {
        d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.Intent i = c.getPackageManager().getLaunchIntentForPackage("nir.com.videopoker");
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        c.startActivity(i);
        if (d.wait(Until.findObject(By.pkg("nir.com.videopoker")), 20000) == null)
            throw new AssertionError("app did not come to foreground");

        // UMP consent form: accept if shown (US test devices normally skip it)
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

        // locate game view region like GameplayScriptTest
        int dh = d.getDisplayHeight(), dw = d.getDisplayWidth(), top = 0;
        for (UiObject2 b : d.findObjects(By.pkg("nir.com.videopoker").clazz("android.widget.Button"))) {
            Rect r = b.getVisibleBounds();
            if (r.centerY() < dh / 4) top = Math.max(top, r.bottom);
        }
        int navId = c.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int nav = navId > 0 ? c.getResources().getDimensionPixelSize(navId) : 0;
        v = new Rect(0, top, dw, dh - nav);

        // play one real round: DEAL then DRAW (no holds)
        tap(0.5f, 0.785f);                    // DEAL
        Thread.sleep(4000);
        tap(0.5f, 0.765f);                    // DRAW
        Thread.sleep(6000);                   // settle + attachBanner fires on first round

        // bounded wait for actual SDK load callback
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
        Log.i(TAG, "ad WebView bounds=" + b + " display=" + dw + "x" + dh);
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
    }
}
