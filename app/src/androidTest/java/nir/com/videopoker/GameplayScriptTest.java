package nir.com.videopoker;

import android.graphics.Rect;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/** Scripted real gameplay for device acceptance video: taps derived from the game view's own layout math. */
@RunWith(AndroidJUnit4.class)
public class GameplayScriptTest {
    private UiDevice d;
    private Rect v;

    private void tap(float fx, float fy) {
        d.click((int) (v.left + v.width() * fx), (int) (v.top + v.height() * fy));
    }

    private void awaitView(String pkg) {
        android.content.Context c = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        c.startActivity(i);
        UiObject2 first = d.wait(Until.findObject(By.pkg(pkg).clazz("android.widget.Button")), 30000);
        if (first == null) throw new AssertionError("top buttons not found for " + pkg);
        int dh = d.getDisplayHeight(), dw = d.getDisplayWidth(), top = 0;
        for (UiObject2 b : d.findObjects(By.pkg(pkg).clazz("android.widget.Button"))) {
            Rect r = b.getVisibleBounds();
            if (r.centerY() < dh / 4) top = Math.max(top, r.bottom);
        }
        int navId = c.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int nav = navId > 0 ? c.getResources().getDimensionPixelSize(navId) : 0;
        v = new Rect(0, top, dw, dh - nav);
    }

    @Test public void playVideoPoker() throws Exception {
        d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        awaitView("nir.com.videopoker");
        Thread.sleep(1500);
        float w = v.width(), h = v.height();
        float cw = w * 0.165f, gap = w * 0.028f, total = 5 * cw + 4 * gap;
        float x0 = (w - total) / 2f, ch = cw * 1.42f;
        tap(0.5f, 0.828f); Thread.sleep(3000);                 // DEAL
        tap((x0 + cw / 2f) / w, (h * 0.345f + ch / 2f) / h);   // HOLD first card
        Thread.sleep(1200);
        tap((x0 + 2 * (cw + gap) + cw / 2f) / w, (h * 0.345f + ch / 2f) / h); // HOLD third card
        Thread.sleep(1200);
        tap(0.5f, 0.808f); Thread.sleep(3500);                 // DRAW, result
        tap(0.5f, 0.646f); Thread.sleep(1500);                 // dismiss level-up overlay if shown
        d.pressHome(); Thread.sleep(2000);
        android.content.SharedPreferences sp = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSharedPreferences("videopoker", 0);
        String state = sp.getString("state", "");
        if (state.isEmpty()) throw new AssertionError("no saved state after scripted hand");
        long handsDone = Long.parseLong(state.split(";")[2]);
        if (handsDone < 1) throw new AssertionError("hand did not complete, state=" + state);
    }
}
