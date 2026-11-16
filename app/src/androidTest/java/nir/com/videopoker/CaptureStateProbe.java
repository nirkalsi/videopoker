package nir.com.videopoker;

/** Read-only inspection of the current game View. All access runs on the UI thread. */
final class CaptureStateProbe {
    private static android.view.View find(android.view.View root, String name) {
        if (root.getClass().getSimpleName().equals(name)) return root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int k = 0; k < group.getChildCount(); k++) {
                android.view.View found = find(group.getChildAt(k), name);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static android.view.View game(String name) {
        for (android.app.Activity a : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
                .getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
            android.view.View v = find(a.getWindow().getDecorView(), name);
            if (v != null) return v;
        }
        throw new AssertionError("resumed game view not found: " + name);
    }
    static Object read(String name, String field) {
        Object[] value = new Object[1];
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                android.view.View v = game(name);
                java.lang.reflect.Field f = v.getClass().getDeclaredField(field);
                f.setAccessible(true); value[0] = f.get(v);
                if (value[0] instanceof boolean[]) value[0] = ((boolean[]) value[0]).clone();
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        return value[0];
    }
    static android.graphics.Rect bounds(String name) {
        android.graphics.Rect r = new android.graphics.Rect();
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            if (!game(name).getGlobalVisibleRect(r)) throw new AssertionError("game not visible");
        });
        return r;
    }
    static float layout(String name, String method) {
        float[] value = new float[1];
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                android.view.View v = game(name);
                java.lang.reflect.Method m = v.getClass().getDeclaredMethod(method, int.class, int.class);
                m.setAccessible(true); value[0] = ((Number)m.invoke(v, v.getWidth(), v.getHeight())).floatValue();
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        return value[0];
    }
}
