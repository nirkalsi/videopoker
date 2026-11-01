package nir.com.videopoker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Captures a REAL settled win (full house driven through the actual evaluate()/settle()
 *  path) so the banner, paytable highlight, payout and cards are mutually consistent.
 *  Local Robolectric render, not a device capture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class WinBannerCaptureTest {

    @SuppressWarnings("unchecked")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void captureWin() throws Exception {
        new File("/tmp/frames-vp-win").mkdirs();
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        int W = 1080, H = 2243;
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);

        Method deal = PokerView.class.getDeclaredMethod("deal");
        deal.setAccessible(true);
        deal.invoke(v);

        // force a real full house: three 7s + two Queens (rank = card%13, suit = card/13)
        Field handF = PokerView.class.getDeclaredField("hand");
        handF.setAccessible(true);
        List<Integer> hnd = (List<Integer>) handF.get(v);
        hnd.clear();
        hnd.add(6);   // 7 of suit 0
        hnd.add(19);  // 7 of suit 1
        hnd.add(32);  // 7 of suit 2
        hnd.add(11);  // Q of suit 0
        hnd.add(50);  // Q of suit 3

        // run the real settlement path: evaluate -> message/winRow/lastWin
        Method settle = PokerView.class.getDeclaredMethod("settle");
        settle.setAccessible(true);
        settle.invoke(v);

        // verify evaluator / result / paytable-row alignment
        Field winRowF = PokerView.class.getDeclaredField("winRow"); winRowF.setAccessible(true);
        Field msgF = PokerView.class.getDeclaredField("message"); msgF.setAccessible(true);
        Field lastWinF = PokerView.class.getDeclaredField("lastWin"); lastWinF.setAccessible(true);
        assertEquals(3, winRowF.getInt(v));                    // PAY_NAMES[3] == "Full House"
        assertEquals("Full House!", msgF.get(v));
        assertTrue(lastWinF.getLong(v) > 0);

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Field resultAtF = PokerView.class.getDeclaredField("resultAt"); resultAtF.setAccessible(true);
        for (int i = 0; i < 40; i++) {
            resultAtF.setLong(v, System.currentTimeMillis() - 400L - i * 30L);
            v.draw(c);
            FileOutputStream fos = new FileOutputStream(String.format("/tmp/frames-vp-win/w%03d.png", i));
            bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
            fos.close();
            Thread.sleep(40);
        }
    }
}
