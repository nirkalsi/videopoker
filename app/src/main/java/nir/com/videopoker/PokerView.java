package nir.com.videopoker;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Video Poker (Jacks or Better) - free social casino game. Virtual coins only, no real money, no prizes. */
public class PokerView extends View {
    public interface Listener {
        void onOutOfCoins();
        void onSpinComplete(int handsDone, long win, long bet);
        void onBigWin(long win, long bet);
        void onLevelUp(int level, long reward);
        void saveState();
    }

    private static final int[] BETS = {5, 10, 25, 50};
    private static final String[] SUITS = {"\u2660", "\u2665", "\u2666", "\u2663"};
    private static final String[] RANKS = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};
    private static final String[] PAY_NAMES = {"Royal flush","Straight flush","Four of a kind","Full house","Flush","Straight","Three of a kind","Two pair","Jacks or better"};
    private static final int[] PAY_MULT = {250, 50, 25, 9, 6, 4, 3, 2, 1};

    private final Random rng = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private Listener listener;

    private long balance = 500;
    private long lastWin = 0;
    private int betIdx = 1;
    private long handsDone = 0;
    private long xp = 0;
    private int level = 1;
    private long levelFlashUntil = 0;
    private long lastRegenAt = System.currentTimeMillis();

    // phases: 0=betting, 1=hold/draw, 2=result
    private int phase = 0;
    private final List<Integer> hand = new ArrayList<>();   // card = suit*13 + rank (rank 0=A..12=K)
    private final boolean[] held = new boolean[5];
    private String message = "Place your bet";

    public PokerView(Context c) { super(c); }

    public void setListener(Listener l) { listener = l; }

    public void addCoins(long n) { balance += n; invalidate(); if (listener != null) listener.saveState(); }

    public int getLevel() { return level; }
    public long getSpinsDone() { return handsDone; }
    private long xpNeeded() { return level * 500L; }

    public String serialize() { return balance + ";" + betIdx + ";" + handsDone + ";" + lastRegenAt + ";" + xp + ";" + level; }

    public void restore(String s) {
        try {
            String[] a = s.split(";");
            balance = Long.parseLong(a[0]);
            betIdx = Integer.parseInt(a[1]);
            handsDone = Long.parseLong(a[2]);
            lastRegenAt = Long.parseLong(a[3]);
            if (a.length > 4) xp = Long.parseLong(a[4]);
            if (a.length > 5) level = Math.max(1, Integer.parseInt(a[5]));
        } catch (Exception ignored) {}
    }

    private void regen() {
        long now = System.currentTimeMillis();
        long ticks = (now - lastRegenAt) / 60000;
        if (ticks > 0) { lastRegenAt += ticks * 60000; if (balance < 100) balance = Math.min(100, balance + ticks * 5); }
    }

    private void deal() {
        long bet = BETS[betIdx];
        if (balance < bet) { if (listener != null) listener.onOutOfCoins(); return; }
        balance -= bet;
        xp += bet;
        lastWin = 0;
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 52; i++) deck.add(i);
        Collections.shuffle(deck, rng);
        hand.clear();
        for (int i = 0; i < 5; i++) hand.add(deck.get(i));
        for (int i = 0; i < 5; i++) held[i] = false;
        message = "Tap cards to hold, then DRAW";
        phase = 1;
        invalidate(); if (listener != null) listener.saveState();
    }

    private void draw() {
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 52; i++) if (!hand.contains(i)) deck.add(i);
        Collections.shuffle(deck, rng);
        int di = 0;
        for (int i = 0; i < 5; i++) if (!held[i]) hand.set(i, deck.get(di++));
        settle();
    }

    // returns pay-table index 0..8, or -1 for no win
    private int evaluate(List<Integer> cards) {
        int[] rankCount = new int[13];
        int[] suitCount = new int[4];
        boolean[] present = new boolean[13];
        for (int c : cards) { rankCount[c % 13]++; suitCount[c / 13]++; present[c % 13] = true; }
        boolean flush = false;
        for (int s : suitCount) if (s == 5) flush = true;
        // straight: 5 consecutive present; wheel A-2-3-4-5; broadway 10-J-Q-K-A
        int run = 0, maxRun = 0;
        for (int i = 0; i < 13; i++) { run = present[i] ? run + 1 : 0; maxRun = Math.max(maxRun, run); }
        boolean wheel = present[0] && present[1] && present[2] && present[3] && present[4];
        boolean broadway = present[0] && present[9] && present[10] && present[11] && present[12];
        boolean straight = maxRun >= 5 || wheel || broadway;
        int pairs = 0, trips = 0, quads = 0; boolean pairJacksUp = false;
        for (int i = 0; i < 13; i++) {
            if (rankCount[i] == 2) { pairs++; if (i == 0 || i >= 10) pairJacksUp = true; }
            if (rankCount[i] == 3) trips++;
            if (rankCount[i] == 4) quads++;
        }
        if (straight && flush && broadway) return 0; // royal
        if (straight && flush) return 1;
        if (quads > 0) return 2;
        if (trips > 0 && pairs > 0) return 3;
        if (flush) return 4;
        if (straight) return 5;
        if (trips > 0) return 6;
        if (pairs == 2) return 7;
        if (pairJacksUp) return 8;
        return -1;
    }

    private void settle() {
        long bet = BETS[betIdx];
        int idx = evaluate(hand);
        if (idx >= 0) {
            long w = bet * PAY_MULT[idx];
            balance += w;
            lastWin = w;
            message = PAY_NAMES[idx] + "! +" + w;
        } else {
            lastWin = 0;
            message = "No win - try again";
        }
        phase = 2;
        handsDone++;
        while (xp >= xpNeeded()) {
            xp -= xpNeeded();
            level++;
            long reward = 150L * level;
            balance += reward;
            levelFlashUntil = System.currentTimeMillis() + 2000;
            if (listener != null) listener.onLevelUp(level, reward);
        }
        if (listener != null) {
            if (idx <= 2) listener.onBigWin(lastWin, bet);
            listener.onSpinComplete((int) handsDone, lastWin, bet);
            listener.saveState();
        }
        if (balance < BETS[0] && listener != null) listener.onOutOfCoins();
        invalidate();
    }

    private void drawCard(Canvas cv, int card, float x, float y, float w, float h, boolean isHeld) {
        rf.set(x, y, x + w, y + h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        cv.drawRoundRect(rf, w * 0.08f, w * 0.08f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(isHeld ? 5 : 2);
        p.setColor(isHeld ? Color.rgb(255, 215, 0) : Color.rgb(200, 200, 200));
        cv.drawRoundRect(rf, w * 0.08f, w * 0.08f, p);
        p.setStyle(Paint.Style.FILL);
        int rank = card % 13, suit = card / 13;
        boolean red = suit == 1 || suit == 2;
        p.setColor(red ? Color.rgb(198, 40, 40) : Color.rgb(27, 27, 27));
        p.setTextAlign(Paint.Align.LEFT); p.setFakeBoldText(true);
        p.setTextSize(w * 0.3f);
        cv.drawText(RANKS[rank], x + w * 0.08f, y + h * 0.32f, p);
        p.setTextSize(w * 0.36f);
        cv.drawText(SUITS[suit], x + w * 0.08f, y + h * 0.68f, p);
        p.setFakeBoldText(false);
        if (isHeld) {
            p.setColor(Color.rgb(255, 215, 0));
            p.setTextSize(w * 0.22f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText("HELD", x + w / 2, y - 6, p);
            p.setFakeBoldText(false);
        }
    }

    private void drawButton(Canvas cv, float x, float y, float w, float h, String label, int bg, boolean enabled) {
        rf.set(x, y, x + w, y + h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(enabled ? bg : Color.rgb(45, 40, 60));
        cv.drawRoundRect(rf, h * 0.3f, h * 0.3f, p);
        p.setColor(enabled ? Color.WHITE : Color.rgb(125, 120, 140));
        p.setTextSize(h * 0.42f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText(label, x + w / 2, y + h * 0.64f, p);
        p.setFakeBoldText(false);
    }

    @Override protected void onDraw(Canvas cv) {
        regen();
        int w = getWidth(), h = getHeight();
        cv.drawColor(Color.rgb(45, 27, 78));
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setColor(Color.rgb(255, 215, 0));
        p.setTextSize(h * 0.042f);
        cv.drawText("VIDEO POKER", w / 2, h * 0.06f, p);
        p.setFakeBoldText(false);
        p.setColor(Color.WHITE);
        p.setTextSize(h * 0.030f);
        cv.drawText("Jacks or Better    COINS  " + balance, w / 2, h * 0.10f, p);
        // level + xp bar
        long nowL = System.currentTimeMillis();
        p.setTextSize(h * 0.020f);
        p.setColor(Color.rgb(200, 200, 220));
        cv.drawText("LV " + level, w * 0.14f, h * 0.128f, p);
        float barL = w * 0.24f, barR = w * 0.76f, barY = h * 0.117f, barH = w * 0.020f;
        rf.set(barL, barY, barR, barY + barH);
        p.setColor(Color.rgb(40, 40, 70)); cv.drawRoundRect(rf, 8, 8, p);
        float frac = Math.min(1f, xp / (float) xpNeeded());
        rf.set(barL, barY, barL + (barR - barL) * frac, barY + barH);
        p.setColor(Color.rgb(255, 215, 0)); cv.drawRoundRect(rf, 8, 8, p);
        if (nowL < levelFlashUntil) {
            p.setTextSize(h * 0.032f);
            p.setColor(Color.rgb(255, 215, 0));
            p.setFakeBoldText(true);
            cv.drawText("LEVEL UP!", w / 2, h * 0.165f, p);
            p.setFakeBoldText(false);
        }

        // pay table (compact, two columns of rows 0-4 / 5-8)
        p.setTextSize(h * 0.020f);
        p.setTextAlign(Paint.Align.LEFT);
        float ty = h * 0.135f;
        for (int i = 0; i < 5; i++) {
            p.setColor(Color.rgb(200, 190, 220));
            cv.drawText(PAY_NAMES[i], w * 0.06f, ty + i * h * 0.026f, p);
            cv.drawText("x" + PAY_MULT[i], w * 0.40f, ty + i * h * 0.026f, p);
        }
        for (int i = 5; i < 9; i++) {
            p.setColor(Color.rgb(200, 190, 220));
            cv.drawText(PAY_NAMES[i], w * 0.54f, ty + (i - 5) * h * 0.026f, p);
            cv.drawText("x" + PAY_MULT[i], w * 0.88f, ty + (i - 5) * h * 0.026f, p);
        }

        // hand row
        float cw = w * 0.16f, ch = cw * 1.45f, gap = w * 0.03f;
        float total = 5 * cw + 4 * gap;
        float x0 = (w - total) / 2f, y0 = h * 0.34f;
        for (int i = 0; i < hand.size(); i++) drawCard(cv, hand.get(i), x0 + i * (cw + gap), y0, cw, ch, held[i]);
        if (hand.isEmpty()) {
            p.setColor(Color.rgb(200, 190, 220)); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(h * 0.03f);
            cv.drawText("Press DEAL to start", w / 2, y0 + ch / 2, p);
        }

        // message
        p.setColor(lastWin > 0 ? Color.rgb(124, 252, 0) : Color.WHITE);
        p.setTextSize(h * 0.034f); p.setFakeBoldText(true); p.setTextAlign(Paint.Align.CENTER);
        cv.drawText(message, w / 2, h * 0.68f, p);
        p.setFakeBoldText(false);

        float bh = h * 0.062f, bw = w * 0.28f, bg = w * 0.04f;
        if (phase == 0 || phase == 2) {
            p.setColor(Color.WHITE); p.setTextSize(h * 0.03f);
            cv.drawText("BET  " + BETS[betIdx], w / 2, h * 0.75f, p);
            drawButton(cv, w / 2 - bw - bg, h * 0.72f, bw, bh, "- BET", Color.rgb(70, 50, 120), true);
            drawButton(cv, w / 2 + bg, h * 0.72f, bw, bh, "+ BET", Color.rgb(70, 50, 120), true);
            drawButton(cv, w / 2 - bw * 1.2f, h * 0.80f, bw * 2.4f, bh, "DEAL", Color.rgb(198, 40, 40), true);
        } else {
            drawButton(cv, w / 2 - bw * 1.2f, h * 0.76f, bw * 2.4f, bh, "DRAW", Color.rgb(198, 40, 40), true);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
        int w = getWidth(), h = getHeight();
        float x = e.getX(), y = e.getY();
        float bh = h * 0.062f, bw = w * 0.28f, bg = w * 0.04f;
        if (phase == 1) {
            // card taps toggle hold
            float cw = w * 0.16f, ch = cw * 1.45f, gap = w * 0.03f;
            float total = 5 * cw + 4 * gap;
            float x0 = (w - total) / 2f, y0 = h * 0.34f;
            if (y >= y0 - h * 0.04f && y <= y0 + ch) {
                for (int i = 0; i < hand.size(); i++) {
                    if (x >= x0 + i * (cw + gap) && x <= x0 + i * (cw + gap) + cw) { held[i] = !held[i]; invalidate(); return true; }
                }
            }
            if (y >= h * 0.76f && y <= h * 0.76f + bh && x >= w / 2 - bw * 1.2f && x <= w / 2 + bw * 1.2f) draw();
            return true;
        }
        if (y >= h * 0.72f && y <= h * 0.72f + bh) {
            if (x >= w / 2 - bw - bg && x <= w / 2 - bg && betIdx > 0) { betIdx--; invalidate(); if (listener != null) listener.saveState(); }
            if (x >= w / 2 + bg && x <= w / 2 + bg + bw && betIdx < BETS.length - 1) { betIdx++; invalidate(); if (listener != null) listener.saveState(); }
        }
        if (y >= h * 0.80f && y <= h * 0.80f + bh && x >= w / 2 - bw * 1.2f && x <= w / 2 + bw * 1.2f) deal();
        return true;
    }
}
