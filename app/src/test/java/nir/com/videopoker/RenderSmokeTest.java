package nir.com.videopoker;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;

/** Pixel-render smoke: game view + full screen + reward/daily/missions/shop overlays. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class RenderSmokeTest {

    private void renderView(View v, int w, int h, String path) throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        FileOutputStream fos = new FileOutputStream(path);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
        assertTrue(path + " too small", new File(path).length() > 5000);
    }

    private MainActivity activity() {
        return Robolectric.buildActivity(MainActivity.class).create().start().resume().visible().get();
    }

    private void callPrivate(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(o);
    }

    private static void resetAnimationState(android.view.View v) {
        v.setAlpha(1f);
        v.setScaleX(1f);
        v.setScaleY(1f);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) resetAnimationState(g.getChildAt(i));
        }
    }

    private void renderLatestDialog(String tag) throws Exception {
        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("no dialog for " + tag, d);
        android.view.View content = d.getWindow().getDecorView();
        resetAnimationState(content); // dialogs enter with alpha/scale animation; render post-animation state
        content.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(1700, View.MeasureSpec.AT_MOST));
        renderView(content, Math.max(content.getMeasuredWidth(), 400),
                   Math.max(content.getMeasuredHeight(), 200) + 80, "/tmp/renders/" + tag + ".png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderGameView1080() throws Exception {
        renderView(new PokerView(RuntimeEnvironment.getApplication()), 1080, 2243, "/tmp/renders/videopoker-game-1080x2243.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderGameView720() throws Exception {
        renderView(new PokerView(RuntimeEnvironment.getApplication()), 720, 1480, "/tmp/renders/videopoker-game-720x1480.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderFullScreen1080() throws Exception {
        MainActivity a = activity();
        renderView(a.findViewById(android.R.id.content), 1080, 2243, "/tmp/renders/videopoker-full-1080x2243.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderDailyBonus() throws Exception {
        MainActivity a = activity();
        callPrivate(a, "maybeDailyBonus");
        renderLatestDialog("videopoker-daily");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderMissions() throws Exception {
        MainActivity a = activity();
        callPrivate(a, "showMissions");
        renderLatestDialog("videopoker-missions");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderShop() throws Exception {
        MainActivity a = activity();
        callPrivate(a, "showShop");
        renderLatestDialog("videopoker-shop");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderOutOfCoinsRewardPrompt() throws Exception {
        MainActivity a = activity();
        a.onOutOfCoins();
        renderLatestDialog("videopoker-outofcoins");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void coinBurstMidFlight() throws Exception {
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        v.coinBurst();
        java.lang.reflect.Field f = PokerView.class.getDeclaredField("coinBurstAt");
        f.setAccessible(true);
        f.setLong(v, System.currentTimeMillis() - 350);
        renderView(v, 1080, 2243, "/tmp/renders/videopoker-coinburst.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderDealtCards() throws Exception {
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(2243, View.MeasureSpec.EXACTLY));
        callPrivate(v, "deal");
        java.lang.reflect.Field fl = PokerView.class.getDeclaredField("cardT");
        fl.setAccessible(true);
        long[] ts = (long[]) fl.get(v);
        java.util.Arrays.fill(ts, 0L);
        renderView(v, 1080, 2243, "/tmp/renders/videopoker-dealt.png");
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void winResultBanner() throws Exception {
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        callPrivate(v, "deal");
        // coherent settled state: real two-pair hand (Ks Kh 9d 9c 4s) paying x2 on the default bet 10
        java.lang.reflect.Field fh = PokerView.class.getDeclaredField("hand"); fh.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<Integer> hand = (java.util.List<Integer>) fh.get(v);
        hand.clear();
        for (int c : new int[]{12, 25, 34, 47, 3}) hand.add(c);
        java.lang.reflect.Field fc = PokerView.class.getDeclaredField("cardT");
        fc.setAccessible(true);
        java.util.Arrays.fill((long[]) fc.get(v), 0L);
        java.lang.reflect.Field fm = PokerView.class.getDeclaredField("message"); fm.setAccessible(true); fm.set(v, "Two Pair!");
        java.lang.reflect.Field fp = PokerView.class.getDeclaredField("phase"); fp.setAccessible(true); fp.setInt(v, 2);
        java.lang.reflect.Field fw = PokerView.class.getDeclaredField("lastWin"); fw.setAccessible(true); fw.setLong(v, 20L);
        java.lang.reflect.Field fwr = PokerView.class.getDeclaredField("winRow"); fwr.setAccessible(true); fwr.setInt(v, 7);
        java.lang.reflect.Field fr = PokerView.class.getDeclaredField("resultAt"); fr.setAccessible(true); fr.setLong(v, System.currentTimeMillis() - 1000);
        renderView(v, 1080, 2243, "/tmp/renders/videopoker-win.png");
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void levelUpOverlay() throws Exception {
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        java.lang.reflect.Field fp = PokerView.class.getDeclaredField("levelUpPending"); fp.setAccessible(true); fp.setInt(v, 4);
        java.lang.reflect.Field fr = PokerView.class.getDeclaredField("levelUpReward"); fr.setAccessible(true); fr.setLong(v, 200L);
        java.lang.reflect.Field fs = PokerView.class.getDeclaredField("levelUpShowing"); fs.setAccessible(true); fs.setBoolean(v, true);
        java.lang.reflect.Field fa = PokerView.class.getDeclaredField("levelUpAt"); fa.setAccessible(true); fa.setLong(v, System.currentTimeMillis() - 1000);
        renderView(v, 1080, 2243, "/tmp/renders/videopoker-levelup.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderGameViewSmall() throws Exception {
        renderView(new PokerView(RuntimeEnvironment.getApplication()), 960, 1600, "/tmp/renders/videopoker-game-960x1600.png");
    }


    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void holdPhaseSmall() throws Exception {
        PokerView v = new PokerView(RuntimeEnvironment.getApplication());
        v.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        callPrivate(v, "deal");
        java.lang.reflect.Field fc = PokerView.class.getDeclaredField("cardT");
        fc.setAccessible(true);
        java.util.Arrays.fill((long[]) fc.get(v), 0L);
        java.lang.reflect.Field fh = PokerView.class.getDeclaredField("held");
        fh.setAccessible(true);
        boolean[] held = (boolean[]) fh.get(v);
        held[0] = true; held[4] = true;
        renderView(v, 960, 1600, "/tmp/renders/videopoker-hold-960x1600.png");
    }

}
