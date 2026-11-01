package nir.com.videopoker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Multi-hand (x3/x5) verification: per-hand replacement decks exclude ALL 5 initial cards,
 *  held cards are shared, payouts sum per hand, stats count hands, selector persists.
 *  Local Robolectric renders, not device captures. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class MultiHandTest {

    @SuppressWarnings("unchecked")
    private static PokerView newView() throws Exception {
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(2243, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 1080, 2243);
        return v;
    }
    private static void set(PokerView v, String field, Object val) throws Exception {
        Field f = PokerView.class.getDeclaredField(field); f.setAccessible(true); f.set(v, val);
    }
    @SuppressWarnings("unchecked")
    private static <T> T get(PokerView v, String field) throws Exception {
        Field f = PokerView.class.getDeclaredField(field); f.setAccessible(true); return (T) f.get(v);
    }
    private static long num(PokerView v, String field) throws Exception {
        return ((Number) get(v, field)).longValue();
    }
    private static void call(PokerView v, String m) throws Exception {
        Method d = PokerView.class.getDeclaredMethod(m); d.setAccessible(true); d.invoke(v);
    }

    @Test public void extraDecksExcludeInitialFiveAndShareHolds() throws Exception {
        for (int seed = 0; seed < 30; seed++) {
            PokerView v = newView();
            set(v, "rng", new Random(seed * 7919 + 1));
            set(v, "balance", 10_000L);
            set(v, "numHands", 5);
            call(v, "deal");
            int[] init = get(v, "initial5");
            List<Integer> primary = get(v, "hand");
            // hold positions 1 and 3
            boolean[] held = get(v, "held");
            held[1] = true; held[3] = true;
            call(v, "draw");
            List<List<Integer>> extras = get(v, "extraHands");
            assertEquals(4, extras.size());
            Set<Integer> initSet = new HashSet<>();
            for (int c : init) initSet.add(c);
            for (List<Integer> ex : extras) {
                assertEquals(5, ex.size());
                assertEquals(new HashSet<>(ex).size(), 5); // distinct within hand
                for (int i = 0; i < 5; i++) {
                    if (i == 1 || i == 3) {
                        assertEquals("held pos " + i + " must equal initial", (int) init[i], (int) ex.get(i));
                    } else {
                        assertFalse("replacement must not be an initial card", initSet.contains(ex.get(i)));
                    }
                }
            }
        }
    }

    @Test public void payoutIsSumAcrossHands() throws Exception {
        for (int seed = 0; seed < 25; seed++) {
            PokerView v = newView();
            set(v, "rng", new Random(seed * 104729 + 7));
            set(v, "balance", 100_000L);
            set(v, "numHands", 3);
            call(v, "deal");
            long before = num(v, "balance");
            int perBet = (int) num(v, "betIdx");
            call(v, "draw"); // all held=false by default after deal? verify: held reset in deal
            long after = num(v, "balance");
            List<Integer> primary = get(v, "hand");
            List<List<Integer>> extras = get(v, "extraHands");
            long bet = new long[]{5, 10, 25, 50}[perBet];
            int[] mult = {250, 50, 25, 9, 6, 4, 3, 2, 1};
            Method ev = PokerView.class.getDeclaredMethod("evaluate", List.class); ev.setAccessible(true);
            long expected = 0;
            int r0 = (int) ev.invoke(v, primary); if (r0 >= 0) expected += bet * mult[r0];
            for (List<Integer> ex : extras) { int r = (int) ev.invoke(v, ex); if (r >= 0) expected += bet * mult[r]; }
            assertEquals("balance delta must equal per-hand payout sum", expected, after - before);
            long lastWin = num(v, "lastWin");
            assertEquals(expected, lastWin);
        }
    }

    @Test public void statsCountHandsNotRounds() throws Exception {
        PokerView v = newView();
        set(v, "rng", new Random(42));
        set(v, "balance", 100_000L);
        set(v, "numHands", 5);
        long h0 = num(v, "stHands");
        call(v, "deal");
        call(v, "draw");
        long h1 = num(v, "stHands");
        assertEquals(h0 + 5, h1);
        long hd = num(v, "handsDone");
        assertTrue(hd >= 5);
    }

    @Test public void numHandsPersists() throws Exception {
        PokerView v = newView();
        set(v, "numHands", 3);
        String s = v.serialize();
        PokerView v2 = newView();
        v2.restore(s);
        int nh = (int) num(v2, "numHands");
        assertEquals(3, nh);
        // clamps junk
        PokerView v3 = newView();
        v3.restore("5000;1;0;0;0;1;4");
        assertEquals(1, (int) num(v3, "numHands"));
        // legacy save without field stays 1
        PokerView v4 = newView();
        v4.restore("5000;1;0;0;0;1");
        assertEquals(1, (int) num(v4, "numHands"));
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void captureFiveHandResult() throws Exception {
        new File("/tmp/frames-vp-multihand").mkdirs();
        capture(5, 11, 1080, 2243, "/tmp/frames-vp-multihand/five_hand.png");
        capture(3, 23, 1080, 2243, "/tmp/frames-vp-multihand/three_hand.png");
        capture(5, 11, 720, 1480, "/tmp/frames-vp-multihand/five_hand_small.png");
        capture(3, 23, 720, 1480, "/tmp/frames-vp-multihand/three_hand_small.png");
        capture(5, 11, 1080, 1920, "/tmp/frames-vp-multihand/five_hand_short.png");
        capture(3, 23, 1080, 1920, "/tmp/frames-vp-multihand/three_hand_short.png");
    }

    private void capture(int nh, int seed, int W, int H, String path) throws Exception {
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        set(v, "rng", new Random(seed));
        set(v, "balance", 50_000L);
        set(v, "betIdx", 2); // 25 per hand
        set(v, "numHands", nh);
        call(v, "deal");
        // hold first two cards for a realistic mid-game look
        boolean[] held = get(v, "held");
        held[0] = true; held[1] = true;
        // force all deal animations complete
        long[] cardT = get(v, "cardT");
        for (int i = 0; i < 5; i++) cardT[i] = 0;
        call(v, "draw");
        for (int i = 0; i < 5; i++) cardT[i] = 0;
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        v.draw(cv);
        FileOutputStream out = new FileOutputStream(path);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        out.close();
        assertTrue(new File(path).length() > 20_000);
    }
}
