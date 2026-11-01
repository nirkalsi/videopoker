package nir.com.videopoker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Temporary: forces a win state and captures frames to verify the glass result plaque + rays.
 *  Local Robolectric render, not a device capture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class WinBannerCaptureTest {

    private static void set(Object o, String name, Object val) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        if (val instanceof Long) f.setLong(o, (Long) val);
        else if (val instanceof Integer) f.setInt(o, (Integer) val);
        else f.set(o, val);
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void captureWin() throws Exception {
        new File("/tmp/frames-vp-win").mkdirs();
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        int W = 1080, H = 2243;
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        // deal so cards are visible
        java.lang.reflect.Method m = PokerView.class.getDeclaredMethod("deal");
        m.setAccessible(true);
        m.invoke(v);
        set(v, "phase", 2);
        set(v, "message", "FULL HOUSE!");
        set(v, "lastWin", 90L);
        set(v, "winRow", 4);
        set(v, "resultAt", System.currentTimeMillis() - 600L);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        for (int i = 0; i < 40; i++) {
            set(v, "resultAt", System.currentTimeMillis() - 500L - i * 30L);
            v.draw(c);
            FileOutputStream fos = new FileOutputStream(String.format("/tmp/frames-vp-win/w%03d.png", i));
            bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
            fos.close();
            Thread.sleep(40);
        }
    }
}
