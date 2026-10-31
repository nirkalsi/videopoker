package nir.com.videopoker;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.LinearLayout;
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

/** Renders the REAL screen (native top row + PokerView canvas) as PNGs for
 *  design preview. Local render, not a device capture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class DesignPreviewTest {

    private static Field fld(Class<?> c, String n) throws Exception {
        Field f = c.getDeclaredField(n); f.setAccessible(true); return f;
    }
    private static void setLong(Object o, String n, long v) throws Exception { fld(o.getClass(), n).setLong(o, v); }
    private static void setInt(Object o, String n, int v) throws Exception { fld(o.getClass(), n).setInt(o, v); }
    private static void setStr(Object o, String n, String v) throws Exception { fld(o.getClass(), n).set(o, v); }
    @SuppressWarnings("unchecked")
    private static List<Integer> intList(Object o, String n) throws Exception { return (List<Integer>) fld(o.getClass(), n).get(o); }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderStates() throws Exception {
        new File("/tmp/vp-design").mkdirs();
        int W = 1080, H = 2400;

        MainActivity act = new MainActivity();
        Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
        attach.setAccessible(true);
        attach.invoke(act, RuntimeEnvironment.getApplication());
        Method br = MainActivity.class.getDeclaredMethod("buildButtonRow");
        br.setAccessible(true);
        View row = (View) br.invoke(act);
        android.widget.LinearLayout rowL = (android.widget.LinearLayout) row;
        for (int i = 0; i < rowL.getChildCount(); i++)
            ((android.widget.Button) rowL.getChildAt(i)).setSingleLine(false);

        PokerView v = new PokerView(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF120826);
        root.addView(row);
        root.addView(v, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        setLong(v, "balance", 12480L);
        setLong(v, "shownBalance", 12480L);
        setInt(v, "betIdx", 2);
        setInt(v, "level", 7);
        setLong(v, "xp", 1600L);
        setLong(v, "handsDone", 41L);

        // ---- state 1: betting (empty hand, badge + chips) ----
        setInt(v, "phase", 0);
        setStr(v, "message", "Place your bet");
        setLong(v, "lastTouchAt", System.currentTimeMillis());
        root.draw(c);
        save(bmp, "/tmp/vp-design/preview-1-betting.png");

        // ---- state 2: dealt hand with two holds ----
        long old = System.currentTimeMillis() - 10000;
        intList(v, "hand").clear();
        int[] cards = {0, 12, 25, 38, 51};   // A-spades, K-spades, Q-hearts, K-diamonds, K-clubs
        for (int i = 0; i < 5; i++) { intList(v, "hand").add(cards[i]); }
        boolean[] held = (boolean[]) fld(v.getClass(), "held").get(v);
        held[0] = true; held[1] = true;
        long[] cardT = (long[]) fld(v.getClass(), "cardT").get(v);
        for (int i = 0; i < 5; i++) cardT[i] = old;
        setInt(v, "phase", 1);
        setStr(v, "message", "Tap cards to HOLD, then DRAW");
        root.draw(c);
        save(bmp, "/tmp/vp-design/preview-2-holds.png");
    }

    private static void save(Bitmap bmp, String path) throws Exception {
        FileOutputStream fos = new FileOutputStream(path);
        bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
        fos.close();
        System.out.println("WROTE " + path);
    }
}
