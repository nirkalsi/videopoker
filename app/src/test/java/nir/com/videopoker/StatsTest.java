package nir.com.videopoker;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Lifetime stats: persistence + panel render. Local Robolectric render, not a device capture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class StatsTest {

    @SuppressWarnings("unchecked")
    private static void forceHand(PokerView v, int... cards) throws Exception {
        Method deal = PokerView.class.getDeclaredMethod("deal");
        deal.setAccessible(true);
        deal.invoke(v);
        Field handF = PokerView.class.getDeclaredField("hand");
        handF.setAccessible(true);
        List<Integer> hnd = (List<Integer>) handF.get(v);
        hnd.clear();
        for (int c : cards) hnd.add(c);
        Method settle = PokerView.class.getDeclaredMethod("settle");
        settle.setAccessible(true);
        settle.invoke(v);
    }

    @Before public void clearPrefs() {
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("videopoker", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void statsAccumulateAndPersist() throws Exception {
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        Field betIdxF = PokerView.class.getDeclaredField("betIdx"); betIdxF.setAccessible(true);
        int[] bets = {5, 10, 25, 50};
        long bet = bets[betIdxF.getInt(v)];
        // full house: three 7s + two queens
        forceHand(v, 6, 19, 32, 11, 50);
        // garbage: 3c 5d 4h 8s 9c
        forceHand(v, 2, 17, 29, 46, 8);
        SharedPreferences sp = RuntimeEnvironment.getApplication()
                .getSharedPreferences("videopoker", Context.MODE_PRIVATE);
        assertEquals(2, sp.getLong("st_hands", 0));
        assertEquals(1, sp.getLong("st_wins", 0));
        assertEquals(3, sp.getInt("st_best_hand", -1));       // Full House row
        assertEquals(bet * 9, sp.getLong("st_biggest", 0));   // 9x multiplier
        assertEquals(0, sp.getLong("st_streak", 0));          // reset by the loss
        // a fresh view loads the persisted values
        PokerView v2 = new PokerView(RuntimeEnvironment.getApplication());
        Field stHandsF = PokerView.class.getDeclaredField("stHands"); stHandsF.setAccessible(true);
        assertEquals(2, stHandsF.getLong(v2));
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderStatsPanel() throws Exception {
        new File("/tmp/frames-vp-stats").mkdirs();
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        int W = 1080, H = 2243;
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        forceHand(v, 6, 19, 32, 11, 50);
        forceHand(v, 2, 17, 29, 46, 8);
        Field so = PokerView.class.getDeclaredField("statsOpen"); so.setAccessible(true);
        so.setBoolean(v, true);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        FileOutputStream fos = new FileOutputStream("/tmp/frames-vp-stats/panel.png");
        bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
        fos.close();
    }
}
