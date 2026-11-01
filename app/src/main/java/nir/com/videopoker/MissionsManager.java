package nir.com.videopoker;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.*;

/** Daily missions + lifetime achievements. Progress tracked in prefs, claimed via dialog. */
public class MissionsManager {
    public static class Item {
        public String id, title;
        public long goal, reward, progress;
        public boolean claimed;
        public boolean done() { return progress >= goal; }
    }

    private final SharedPreferences sp;
    private final Runnable onChanged;

    public MissionsManager(Context c, Runnable onChanged) {
        sp = c.getSharedPreferences("videopoker", Context.MODE_PRIVATE);
        this.onChanged = onChanged;
        rollDailyIfNeeded();
    }

    private String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    private void rollDailyIfNeeded() {
        if (!today().equals(sp.getString("missions_day", ""))) {
            sp.edit()
                .putString("missions_day", today())
                .putLong("dm1", 0).putLong("dm2", 0).putLong("dm3", 0)
                .putBoolean("dm1c", false).putBoolean("dm2c", false).putBoolean("dm3c", false)
                .apply();
        }
    }

    /** Call after each completed spin. */
    public void onSpin(long win, long bet, int level, long totalSpins) {
        rollDailyIfNeeded();
        SharedPreferences.Editor e = sp.edit();
        e.putLong("dm1", sp.getLong("dm1", 0) + 1);
        if (win > 0) {
            e.putLong("dm2", sp.getLong("dm2", 0) + 1);
            e.putLong("dm3", sp.getLong("dm3", 0) + win);
        }
        if (win >= 25 * bet) e.putBoolean("ach4_done", true);
        e.apply();
        if (onChanged != null) onChanged.run();
    }

    public void onDailyClaimed(int streak) {
        if (streak >= 3) sp.edit().putBoolean("ach6_done", true).apply();
    }

    public List<Item> dailyMissions() {
        rollDailyIfNeeded();
        List<Item> out = new ArrayList<>();
        out.add(mk("dm1", "Play 15 hands today", 15, 300, sp.getLong("dm1", 0), sp.getBoolean("dm1c", false)));
        out.add(mk("dm2", "Win 6 hands today", 6, 250, sp.getLong("dm2", 0), sp.getBoolean("dm2c", false)));
        out.add(mk("dm3", "Win 1,000 coins today", 1000, 400, sp.getLong("dm3", 0), sp.getBoolean("dm3c", false)));
        return out;
    }

    public List<Item> achievements(int level, long totalSpins) {
        List<Item> out = new ArrayList<>();
        out.add(mkA("ach1", "First hand", 1, 100, totalSpins, sp.getBoolean("ach1c", false)));
        out.add(mkA("ach2", "100 hands", 100, 500, totalSpins, sp.getBoolean("ach2c", false)));
        out.add(mkA("ach3", "1,000 hands", 1000, 2000, totalSpins, sp.getBoolean("ach3c", false)));
        out.add(mkA("ach4", "Four of a kind or better", 1, 1000,
            sp.getBoolean("ach4_done", false) ? 1 : 0, sp.getBoolean("ach4c", false)));
        out.add(mkA("ach5", "Reach level 5", 5, 1500, level, sp.getBoolean("ach5c", false)));
        out.add(mkA("ach6", "3-day daily bonus streak", 1, 800,
            sp.getBoolean("ach6_done", false) ? 1 : 0, sp.getBoolean("ach6c", false)));
        out.add(mkA("ach7", "Hot streak: win 5 in a row", 5, 800,
            sp.getLong("st_best_streak", 0), sp.getBoolean("ach7c", false)));
        out.add(mkA("ach8", "Straight Flush or better", 1, 1500,
            sp.getInt("st_best_hand", -1) >= 0 && sp.getInt("st_best_hand", -1) <= 1 ? 1 : 0,
            sp.getBoolean("ach8c", false)));
        return out;
    }

    private Item mk(String id, String title, long goal, long reward, long progress, boolean claimed) {
        Item i = new Item(); i.id = id; i.title = title; i.goal = goal; i.reward = reward;
        i.progress = Math.min(progress, goal); i.claimed = claimed; return i;
    }
    private Item mkA(String id, String title, long goal, long reward, long progress, boolean claimed) {
        return mk(id, title, goal, reward, progress, claimed);
    }

    /** Marks claimed; caller grants the coins. Returns reward or 0. */
    public long claim(Item i) {
        if (i.claimed || !i.done()) return 0;
        sp.edit().putBoolean(i.id + "c", true).apply();
        if (onChanged != null) onChanged.run();
        return i.reward;
    }

    public boolean hasClaimable(int level, long totalSpins) {
        for (Item i : dailyMissions()) if (i.done() && !i.claimed) return true;
        for (Item i : achievements(level, totalSpins)) if (i.done() && !i.claimed) return true;
        return false;
    }
}
