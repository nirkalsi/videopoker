package nir.com.videopoker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Captures a scripted gameplay sequence as PNG frames for a synthetic preview video.
 *  NOT a device capture: rendered locally via Robolectric native graphics. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class GameplayCaptureTest {

    private static void call(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(o);
    }

    private static void holdCard(PokerView v, int i) throws Exception {
        Field held = PokerView.class.getDeclaredField("held");
        held.setAccessible(true);
        ((boolean[]) held.get(v))[i] = true;
        Field hf = PokerView.class.getDeclaredField("heldFlash");
        hf.setAccessible(true);
        hf.setInt(v, i);
        Field hfa = PokerView.class.getDeclaredField("heldFlashAt");
        hfa.setAccessible(true);
        hfa.setLong(v, System.currentTimeMillis());
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void capturePokerRound() throws Exception {
        new File("/tmp/frames-vp").mkdirs();
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        int W = 1080, H = 2243;
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        long t0 = System.currentTimeMillis();
        int frame = 0;
        boolean dealt = false, h0 = false, h1 = false, drew = false;
        while (frame < 330) {                          // ~11s at 30fps
            long el = System.currentTimeMillis() - t0;
            if (!dealt && el > 500) { call(v, "deal"); dealt = true; }
            else if (dealt && !h0 && el > 3000) { holdCard(v, 0); h0 = true; }
            else if (h0 && !h1 && el > 3500) { holdCard(v, 1); h1 = true; }
            else if (h1 && !drew && el > 5200) { call(v, "draw"); drew = true; }
            v.draw(c);
            FileOutputStream fos = new FileOutputStream(String.format("/tmp/frames-vp/f%04d.png", frame++));
            bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
            fos.close();
            Thread.sleep(33);
        }
    }
}
