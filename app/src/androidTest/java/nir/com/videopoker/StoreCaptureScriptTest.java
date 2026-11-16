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

/** Store-screenshot capture driver: plays one scripted 5-hand round on the live v6 design,
 *  pausing at photogenic states and saving full-screen PNGs to /sdcard/Download/.
 *  Taps reuse the proven GameplayScriptTest layout math. Sample ads only (debug build);
 *  no ad content is clicked. */
@RunWith(AndroidJUnit4.class)
public class StoreCaptureScriptTest {
    private static final String TAG = "StoreCaptureVP";
    private UiDevice d;
    private Rect v;
    private int shot = 0;

    private void tap(float fx, float fy) {
        d.click((int) (v.left + v.width() * fx), (int) (v.top + v.height() * fy));
    }

    private void shot(String name) throws Exception {
        shot++;
        java.io.File f = new java.io.File("/sdcard/Download/vp_store_" + shot + "_" + name + ".png");
        d.takeScreenshot(f);
        Log.i(TAG, "shot " + f + " exists=" + f.exists());
    }

    @Test public void captureStoreScreens() throws Exception {
        d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // deterministic state: healthy balance, 5-hand mode (serialize field 7), no prior rounds
        c.getSharedPreferences("videopoker", 0).edit().clear()
                .putString("state", "10000;2;0;0;0;1;5")
                .commit();

        android.content.Intent i = c.getPackageManager().getLaunchIntentForPackage("nir.com.videopoker");
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        c.startActivity(i);

        // --- UMP consent (real production form; handle it, never bypass) ---
        long consentDeadline = System.currentTimeMillis() + 75000;
        int cleanPolls = 0;
        boolean sawForm = false;
        while (System.currentTimeMillis() < consentDeadline) {
            UiObject2 doNot = d.findObject(By.text("Do not consent"));
            UiObject2 confirm = d.findObject(By.text("Confirm choices"));
            UiObject2 welcome = d.findObject(By.textContains("asks for your consent"));
            UiObject2 manageData = d.findObject(By.text("Manage your data"));
            UiObject2 acceptAll = d.findObject(By.text("Accept all"));
            if (doNot != null) { sawForm = true; doNot.click(); cleanPolls = 0; }
            else if (confirm != null) { sawForm = true; confirm.click(); cleanPolls = 0; }
            else if (welcome != null || manageData != null || acceptAll != null) {
                sawForm = true;
                UiObject2 opts = d.findObject(By.text("Manage options"));
                if (opts != null) opts.click();
                cleanPolls = 0;
            } else {
                cleanPolls++;
                if (cleanPolls >= (sawForm ? 4 : 12)) break;
            }
            Thread.sleep(750);
        }
        if (d.findObject(By.text("Do not consent")) != null
                || d.findObject(By.text("Confirm choices")) != null
                || d.findObject(By.text("Accept all")) != null
                || d.findObject(By.textContains("asks for your consent")) != null
                || d.findObject(By.text("Manage your data")) != null)
            throw new AssertionError("consent overlay still present before capture");

        // measure the game view area (same as GameplayScriptTest)
        UiObject2 first = d.wait(Until.findObject(By.pkg("nir.com.videopoker").clazz("android.widget.Button")), 30000);
        if (first == null) throw new AssertionError("top buttons not found");
        int dh = d.getDisplayHeight(), dw = d.getDisplayWidth(), top = 0;
        for (UiObject2 b : d.findObjects(By.pkg("nir.com.videopoker").clazz("android.widget.Button"))) {
            Rect r = b.getVisibleBounds();
            if (r.centerY() < dh / 4) top = Math.max(top, r.bottom);
        }
        int navId = c.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int nav = navId > 0 ? c.getResources().getDimensionPixelSize(navId) : 0;
        v = new Rect(0, top, dw, dh - nav);

        Thread.sleep(2500);
        shot("betting");                    // 1: betting screen, HANDS x5 selected, paytable

        tap(0.5f, 0.828f); Thread.sleep(3500);   // DEAL
        shot("dealt_5hand");                // 2: main hand dealt, 5-hand placeholders above

        // HOLD two cards (first and third) using the view's layout math
        float w = v.width(), h = v.height();
        float cw = w * 0.165f, gap = w * 0.028f, total = 5 * cw + 4 * gap;
        float x0 = (w - total) / 2f, ch = cw * 1.42f;
        tap((x0 + cw / 2f) / w, (h * 0.345f + ch / 2f) / h);
        Thread.sleep(1200);
        tap((x0 + 2 * (cw + gap) + cw / 2f) / w, (h * 0.345f + ch / 2f) / h);
        Thread.sleep(1200);
        shot("holds");                      // 3: HOLD markers on cards

        tap(0.5f, 0.808f);                  // DRAW
        Thread.sleep(2500);
        shot("drawing");                    // 4: extra hands filling in
        Thread.sleep(2500);
        shot("result_5hand");               // 5: all 5 hands settled, win message

        UiObject2 close = d.findObject(By.text("Close"));
        if (close != null) close.click();   // dismiss possible daily-bonus dialog (not ad content)

        String pkg = d.getCurrentPackageName();
        if (!"nir.com.videopoker".equals(pkg))
            throw new AssertionError("foreground=" + pkg + " (unexpected takeover)");
        Log.i(TAG, "capture complete: 5 shots");
    }
}
