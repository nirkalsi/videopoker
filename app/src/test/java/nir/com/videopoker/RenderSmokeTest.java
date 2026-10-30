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

}
