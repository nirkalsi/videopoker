package nir.com.videopoker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.LinearLayout;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Missions dialog styling render. Local render only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class MissionsDialogTest {

    @Before public void prefs() {
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("videopoker", Context.MODE_PRIVATE).edit().clear()
                // mixed states: dm1 done+unclaimed, dm2 partial, dm3 zero; ach1 claimed, ach7 partial badge
                .putLong("dm1", 15).putLong("dm2", 3).putLong("dm3", 0)
                .putBoolean("ach1c", true)
                .putLong("st_best_streak", 3)
                .commit();
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderDialogContent() throws Exception {
        new File("/tmp/frames-vp-missions").mkdirs();
        Context c = RuntimeEnvironment.getApplication();
        MissionsManager missions = new MissionsManager(c, null);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * c.getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, pad / 2);
        box.setBackground(MissionViews.dialogBg());
        box.addView(MissionViews.title(c, "Missions"));
        box.addView(MissionViews.header(c, "DAILY MISSIONS"));
        for (MissionsManager.Item i : missions.dailyMissions()) box.addView(MissionViews.row(c, i, null));
        box.addView(MissionViews.header(c, "ACHIEVEMENTS"));
        for (MissionsManager.Item i : missions.achievements(2, 120)) box.addView(MissionViews.row(c, i, null));
        int W = 980;
        box.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int H = box.getMeasuredHeight();
        box.layout(0, 0, W, H);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        box.draw(new Canvas(bmp));
        FileOutputStream fos = new FileOutputStream("/tmp/frames-vp-missions/dialog.png");
        bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
        fos.close();
    }
}
