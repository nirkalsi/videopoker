package nir.com.videopoker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Renders every rank at the real hand-row card size to audit pip layouts and index clear zones.
 *  Local Robolectric render, not a device capture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class AllRanksCaptureTest {

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void captureAllRanks() throws Exception {
        new File("/tmp/frames-vp-ranks").mkdirs();
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        int W = 1080, H = 2243;
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        float cw = W * 0.165f;
        // find real card height: hand row uses cw*? - read via reflection-free probe: use aspect from drawCard callers
        float ch = cw * 1.45f;
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF1B3F8F);
        Method dc = PokerView.class.getDeclaredMethod("drawCard", Canvas.class, int.class,
                float.class, float.class, float.class, float.class, boolean.class, float.class, float.class);
        dc.setAccessible(true);
        // ranks 2..10 (rank index 1..9), suits rotate; then A/J/Q/K
        int[] order = {1,2,3,4,5,6,7,8,9};
        for (int i = 0; i < order.length; i++) {
            float x = 20 + (i % 5) * (cw + 12);
            float y = 40 + (i / 5) * (ch + 30);
            dc.invoke(v, c, order[i] + (i % 4) * 13, x, y, cw, ch, false, 1f, 0f);
        }
        int[] faces = {0, 10, 11, 12};
        for (int i = 0; i < faces.length; i++) {
            float x = 20 + i * (cw + 12);
            float y = 40 + 2 * (ch + 30);
            dc.invoke(v, c, faces[i] + (i % 4) * 13, x, y, cw, ch, false, 1f, 0f);
        }
        FileOutputStream fos = new FileOutputStream("/tmp/frames-vp-ranks/all.png");
        bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
        fos.close();
    }
}
