package nir.com.videopoker;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Video Poker (Jacks or Better) - free social casino game. Virtual coins only, no real money, no prizes.
 *  Casino-cabinet redesign: paytable display, big cards, glowing holds, win celebrations. */
public class PokerView extends View {
    public interface Listener {
        void onOutOfCoins();
        void onSpinComplete(int handsDone, long win, long bet);
        void onBigWin(long win, long bet);
        void onLevelUp(int level, long reward);
        void saveState();
    }

    private static final int[] BETS = {5, 10, 25, 50};
    private static final int[] CHIP_COLORS = {0xFFC62828, 0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A};
    private static final String[] SUITS = {"\u2660", "\u2665", "\u2666", "\u2663"};
    private static final String[] RANKS = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};
    private static final String[] PAY_NAMES = {"ROYAL FLUSH","Straight Flush","4 of a Kind","Full House","Flush","Straight","3 of a Kind","Two Pair","Jacks or Better"};
    private static final int[] PAY_MULT = {250, 50, 25, 9, 6, 4, 3, 2, 1};

    private final Random rng = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private Listener listener;

    private long balance = 500;
    private long shownBalance = 500;
    private long lastWin = 0;
    private int betIdx = 1;
    private long handsDone = 0;
    private long xp = 0;
    private int level = 1;
    private long lastRegenAt = System.currentTimeMillis();

    // phases: 0=betting, 1=hold/draw, 2=result
    private int phase = 0;
    private final List<Integer> hand = new ArrayList<>();   // card = suit*13 + rank
    private final boolean[] held = new boolean[5];
    private final long[] cardT = new long[5];
    private final boolean[] cardNew = new boolean[5];
    private String message = "Place your bet";
    private int winRow = -1;
    private long resultAt = 0;
    private final float[] px = new float[16], py = new float[16], pvx = new float[16], pvy = new float[16];
    private boolean particlesOn = false;
    // coin burst: coins fly from screen center to the balance pill (daily bonus claim)
    private final float[] cbx = new float[14], cby = new float[14], cbvx = new float[14], cbvy = new float[14];
    private boolean coinBurstOn = false;
    private long coinBurstAt = 0;

    private int levelUpPending = 0;
    private long levelUpReward = 0;
    private boolean levelUpShowing = false;
    private long levelUpAt = 0;
    private int pressedBtn = -1;
    private long betBumpAt = 0;
    private long lastTouchAt = System.currentTimeMillis();
    private int heldFlash = -1;
    private long heldFlashAt = 0;

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
            shownBalance = balance;
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
        GameSounds.play(getContext(), GameSounds.CARD);
        long bet = BETS[betIdx];
        if (balance < bet) { if (listener != null) listener.onOutOfCoins(); return; }
        balance -= bet;
        xp += bet;
        lastWin = 0; winRow = -1; particlesOn = false; levelUpShowing = false;
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 52; i++) deck.add(i);
        Collections.shuffle(deck, rng);
        hand.clear();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            hand.add(deck.get(i));
            held[i] = false;
            cardT[i] = now + i * 110;
            cardNew[i] = true;
        }
        message = "Tap cards to HOLD, then DRAW";
        phase = 1;
        invalidate(); if (listener != null) listener.saveState();
    }

    private void draw() {
        GameSounds.play(getContext(), GameSounds.CARD);
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 52; i++) if (!hand.contains(i)) deck.add(i);
        Collections.shuffle(deck, rng);
        int di = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) if (!held[i]) {
            hand.set(i, deck.get(di++));
            cardT[i] = now + i * 70;
            cardNew[i] = true;
        }
        settle();
    }

    private int evaluate(List<Integer> cards) {
        int[] rankCount = new int[13];
        int[] suitCount = new int[4];
        boolean[] present = new boolean[13];
        for (int c : cards) { rankCount[c % 13]++; suitCount[c / 13]++; present[c % 13] = true; }
        boolean flush = false;
        for (int s : suitCount) if (s == 5) flush = true;
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
        if (straight && flush && broadway) return 0;
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
        resultAt = System.currentTimeMillis();
        if (idx >= 0) {
            long w = bet * PAY_MULT[idx];
            balance += w;
            GameSounds.play(idx >= 6 ? GameSounds.BIG_WIN : GameSounds.WIN);
            lastWin = w;
            message = PAY_NAMES[idx] + "!";
            winRow = idx;
            spawnParticles();
        } else {
            GameSounds.play(getContext(), GameSounds.LOSE);
            lastWin = 0;
            message = "No win - try again";
            winRow = -1;
        }
        phase = 2;
        handsDone++;
        while (xp >= xpNeeded()) {
            xp -= xpNeeded();
            level++;
            long reward = 150L * level;
            balance += reward;
            levelUpPending = level; levelUpReward += reward;
            if (listener != null) listener.onLevelUp(level, reward);
        }
        if (listener != null) {
            if (idx >= 0 && idx <= 2) listener.onBigWin(lastWin, bet);
            listener.onSpinComplete((int) handsDone, lastWin, bet);
            listener.saveState();
        }
        if (balance < BETS[0] && listener != null) listener.onOutOfCoins();
        invalidate();
    }


    /** Coins burst from center and fly to the balance pill (e.g. daily claim). */
    public void coinBurst() {
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < 14; i++) {
            cbx[i] = 0.5f + (r.nextFloat() - 0.5f) * 0.12f;
            cby[i] = 0.45f + (r.nextFloat() - 0.5f) * 0.08f;
            cbvx[i] = (r.nextFloat() - 0.5f) * 0.08f;
            cbvy[i] = (r.nextFloat() - 0.5f) * 0.08f;
        }
        coinBurstOn = true;
        coinBurstAt = System.currentTimeMillis();
        invalidate();
    }

    private void drawCoinBurst(Canvas cv, float w, float h, long now) {
        if (!coinBurstOn) return;
        float t = (now - coinBurstAt) / 750f;
        if (t >= 1f) { coinBurstOn = false; return; }
        for (int i = 0; i < 14; i++) {
            float local = Math.min(1f, Math.max(0f, t * 1.25f - i * 0.015f));
            float e = local * local;
            float x = (cbx[i] + cbvx[i] * local + (0.5f - cbx[i]) * e) * w;
            float y = (cby[i] + cbvy[i] * local + (0.045f - cby[i]) * e) * h;
            float rad = w * 0.014f * (1f - 0.4f * local);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFD54F);
            cv.drawCircle(x, y, rad, p);
            p.setColor(0xFFB8860B);
            cv.drawCircle(x, y, rad * 0.55f, p);
        }
        postInvalidateDelayed(28);
    }

    private void spawnParticles() {
        particlesOn = true;
        for (int i = 0; i < 16; i++) {
            px[i] = 0.5f + (rng.nextFloat() - 0.5f) * 0.2f;
            py[i] = 0.5f;
            pvx[i] = (rng.nextFloat() - 0.5f) * 0.0016f;
            pvy[i] = -(0.0006f + rng.nextFloat() * 0.0016f);
        }
    }

    private float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }
    private float easeOutBack(float t) { float c = 1.70158f; t -= 1f; return 1f + (c + 1f) * t * t * t + c * t * t; }

    private void drawCard(Canvas cv, int card, float x, float y, float w, float h, boolean isHeld, float scaleX, float glowT) {
        float cx = x + w / 2f;
        float dw = w * Math.abs(scaleX);
        if (dw < 2) return;
        float dx = cx - dw / 2f;
        boolean showBack = scaleX < 0;
        rf.set(dx + 3, y + 5, dx + dw + 3, y + h + 5);
        p.setStyle(Paint.Style.FILL); p.setColor(0x55000000);
        cv.drawRoundRect(rf, dw * 0.09f, dw * 0.09f, p);
        rf.set(dx, y, dx + dw, y + h);
        p.setColor(showBack ? 0xFF3E2723 : 0xFFFCFCFC);
        cv.drawRoundRect(rf, dw * 0.09f, dw * 0.09f, p);
        if (showBack) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2, dw * 0.02f)); p.setColor(0xFF8D6E63);
            rf.set(dx + dw * 0.08f, y + h * 0.05f, dx + dw * 0.92f, y + h * 0.95f);
            cv.drawRoundRect(rf, dw * 0.06f, dw * 0.06f, p);
            p.setStyle(Paint.Style.FILL);
            return;
        }
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
        p.setColor(isHeld ? 0xFFFFD700 : 0xFFB9B9B9);
        cv.drawRoundRect(rf, dw * 0.09f, dw * 0.09f, p);
        p.setStyle(Paint.Style.FILL);
        int rank = card % 13, suit = card / 13;
        boolean red = suit == 1 || suit == 2;
        p.setColor(red ? 0xFFC62828 : 0xFF1B1B1B);
        p.setTextAlign(Paint.Align.LEFT); p.setFakeBoldText(true);
        p.setTextSize(w * 0.26f);
        if (scaleX >= 0) {
            cv.drawText(RANKS[rank], dx + dw * 0.07f, y + h * 0.26f, p);
            p.setTextSize(w * 0.24f);
            cv.drawText(SUITS[suit], dx + dw * 0.07f, y + h * 0.47f, p);
            p.setTextSize(w * 0.55f); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText(SUITS[suit], cx, y + h * 0.92f, p);
        }
        p.setFakeBoldText(false);
        if (isHeld) {
            float pulse = 0.7f + 0.3f * (float) Math.sin(glowT / 250.0);
            p.setColor(Color.argb((int) (200 * pulse), 255, 215, 0));
            p.setTextSize(w * 0.20f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            p.setShadowLayer(8, 0, 0, 0xCCFFD700);
            cv.drawText("HELD", cx, y - 10, p);
            p.clearShadowLayer();
            p.setFakeBoldText(false);
        }
    }

    private void drawChip(Canvas cv, float cx, float cy, float r, int color, String label, boolean selected) {
        p.setStyle(Paint.Style.FILL); p.setColor(0x66000000);
        cv.drawCircle(cx + 2, cy + 4, r, p);
        p.setColor(color);
        cv.drawCircle(cx, cy, r, p);
        p.setColor(0xFFF5F5F5);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i + Math.PI / 6;
            p.setStrokeWidth(r * 0.16f);
            cv.drawLine(cx + (float) Math.cos(a) * r * 0.86f, cy + (float) Math.sin(a) * r * 0.86f,
                    cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, p);
        }
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.06f); p.setColor(0xCCFFFFFF);
        cv.drawCircle(cx, cy, r * 0.66f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFF5F5F5); cv.drawCircle(cx, cy, r * 0.55f, p);
        if (!label.isEmpty()) {
            p.setColor(0xFF202020); p.setTextSize(r * 0.52f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText(label, cx, cy + r * 0.19f, p);
            p.setFakeBoldText(false);
        }
        if (selected) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.09f); p.setColor(0xFFFFD700);
            cv.drawCircle(cx, cy, r * 1.12f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawPillButton(Canvas cv, float x, float y, float w, float h, String label,
                                int bgTop, int bgBottom, boolean enabled, boolean pressed, float pulse) {
        float sc = pressed ? 0.94f : 1f;
        float cx = x + w / 2f, cy = y + h / 2f;
        float dw = w * sc, dh = h * sc;
        rf.set(cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2);
        if (pulse > 0f && enabled) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(h * 0.06f);
            p.setColor(0x44FFD700);
            float grow = pulse * h * 0.22f;
            RectF glow = new RectF(rf.left - grow, rf.top - grow, rf.right + grow, rf.bottom + grow);
            cv.drawRoundRect(glow, dh * 0.5f + grow, dh * 0.5f + grow, p);
            p.setStyle(Paint.Style.FILL);
        }
        if (enabled) p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, bgTop, bgBottom, Shader.TileMode.CLAMP));
        else { p.setShader(null); p.setColor(0xFF37474F); }
        cv.drawRoundRect(rf, dh * 0.5f, dh * 0.5f, p);
        p.setShader(null);
        if (enabled) {
            p.setColor(0x33FFFFFF);
            rf.set(cx - dw / 2 + 6, cy - dh / 2 + 4, cx + dw / 2 - 6, cy - dh * 0.18f);
            cv.drawRoundRect(rf, dh * 0.4f, dh * 0.4f, p);
        }
        p.setColor(enabled ? 0xFFFFFFFF : 0xFF90A4AE);
        p.setTextSize(dh * 0.44f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        p.setShadowLayer(3, 0, 2, 0x66000000);
        cv.drawText(label, cx, cy + dh * 0.16f, p);
        p.clearShadowLayer();
        p.setFakeBoldText(false);
    }

    private void drawFancyText(Canvas cv, String text, float cx, float cy, float size, int topColor, int bottomColor) {
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(size); p.setFakeBoldText(true);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(size * 0.10f); p.setColor(0xAA000000);
        cv.drawText(text, cx, cy, p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, cy - size, 0, cy + size * 0.2f, topColor, bottomColor, Shader.TileMode.CLAMP));
        cv.drawText(text, cx, cy, p);
        p.setShader(null); p.setFakeBoldText(false);
    }

    @Override protected void onDraw(Canvas cv) {
        regen();
        long now = System.currentTimeMillis();
        int w = getWidth(), h = getHeight();
        boolean animating = false;

        // cabinet background: deep blue machine (genre-standard cabinet look)
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF1B34A8, 0xFF081040, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // ---- HUD: level badge + coin pill ----
        float lvR = w * 0.055f, lvX = w * 0.10f, lvY = h * 0.042f;
        p.setColor(0xFF6A1B9A); cv.drawCircle(lvX, lvY, lvR, p);
        float xpFrac = Math.min(1f, xp / (float) xpNeeded());
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(lvR * 0.18f); p.setColor(0x33FFFFFF);
        cv.drawCircle(lvX, lvY, lvR * 0.82f, p);
        p.setColor(0xFFFFD700);
        cv.drawArc(lvX - lvR * 0.82f, lvY - lvR * 0.82f, lvX + lvR * 0.82f, lvY + lvR * 0.82f, -90, xpFrac * 360f, false, p);
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF);
        p.setTextSize(lvR * 0.62f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText("" + level, lvX, lvY + lvR * 0.24f, p);
        p.setFakeBoldText(false);
        p.setTextSize(h * 0.010f); p.setColor(0x99FFFFFF);
        cv.drawText("LEVEL", lvX, lvY + lvR * 1.5f, p);
        // sound toggle
        float spX = w * 0.93f, spY = h * 0.042f, spR = w * 0.045f;
        p.setColor(0x66000000);
        cv.drawCircle(spX, spY, spR, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0x55FFFFFF);
        cv.drawCircle(spX, spY, spR, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(spR * 0.9f); p.setTextAlign(Paint.Align.CENTER);
        cv.drawText(GameSounds.isEnabled(getContext()) ? "\uD83D\uDD0A" : "\uD83D\uDD07", spX, spY + spR * 0.32f, p);


        float pillW = w * 0.34f, pillH = h * 0.032f, pillX = w / 2f - pillW / 2f + w * 0.06f, pillY = h * 0.026f;
        rf.set(pillX, pillY, pillX + pillW, pillY + pillH);
        p.setColor(0xCC000000);
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0x66FFD700);
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        p.setStyle(Paint.Style.FILL);
        float coinR = pillH * 0.36f, coinX = pillX + pillH * 0.55f, coinY = pillY + pillH / 2f;
        p.setColor(0xFFFFD700); cv.drawCircle(coinX, coinY, coinR, p);
        p.setColor(0xFFB8860B); p.setTextSize(coinR * 1.2f); p.setFakeBoldText(true);
        cv.drawText("$", coinX, coinY + coinR * 0.42f, p);
        p.setFakeBoldText(false);
        if (shownBalance != balance) {
            long diff = balance - shownBalance;
            long step = Math.max(1, Math.abs(diff) / 6);
            shownBalance += diff > 0 ? step : -step;
            if ((diff > 0 && shownBalance > balance) || (diff < 0 && shownBalance < balance)) shownBalance = balance;
            animating = true;
        }
        p.setColor(0xFFFFFFFF); p.setTextSize(pillH * 0.52f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText(String.format("%,d", shownBalance), pillX + pillW * 0.58f, pillY + pillH * 0.68f, p);
        p.setFakeBoldText(false);
        if (balance < 100) {
            long nextIn = 60000 - (now - lastRegenAt) % 60000;
            p.setTextSize(h * 0.010f); p.setColor(0x99FFFFFF);
            cv.drawText("+5 free in " + (nextIn / 1000 + 1) + "s", pillX + pillW / 2f, pillY + pillH + h * 0.015f, p);
            animating = true;
        }

        // ---- paytable panel (machine display) ----
        float ptX = w * 0.035f, ptY = h * 0.100f, ptW = w * 0.93f, ptH = h * 0.228f;
        rf.set(ptX, ptY, ptX + ptW, ptY + ptH);
        p.setShader(new LinearGradient(0, ptY, 0, ptY + ptH, 0xFF12278A, 0xFF0A1750, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, 14, 14, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(0xFFFFD54F);
        cv.drawRoundRect(rf, 14, 14, p);
        p.setStyle(Paint.Style.FILL);
        rf.set(ptX + 5, ptY + 4, ptX + ptW - 5, ptY + h * 0.027f);
        p.setColor(0xFF1B34A8);
        cv.drawRoundRect(rf, 10, 10, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(0x88FFD54F);
        cv.drawRoundRect(rf, 10, 10, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER); p.setColor(0xFFFFD54F); p.setTextSize(h * 0.014f); p.setFakeBoldText(true);
        p.setShadowLayer(6, 0, 0, 0x88FFD54F);
        cv.drawText("JACKS OR BETTER  \u2726  PAYTABLE", w / 2f, ptY + h * 0.021f, p);
        p.clearShadowLayer();
        p.setFakeBoldText(false);
        for (int b = 0; b < BETS.length; b++) {
            float hx = ptX + ptW * (0.52f + b * 0.12f);
            if (b == betIdx) { p.setColor(0xFFC62828); rf.set(hx - ptW * 0.055f, ptY + h * 0.0265f, hx + ptW * 0.055f, ptY + h * 0.0335f); cv.drawRoundRect(rf, 6, 6, p); }
            p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            p.setColor(b == betIdx ? 0xFFFFFFFF : 0xFFFFD54F); p.setTextSize(h * 0.011f);
            cv.drawText("" + BETS[b], hx, ptY + h * 0.0320f, p);
        }
        p.setFakeBoldText(false);
        float rowH = h * 0.0195f, rowY0 = ptY + h * 0.050f;
        for (int i = 0; i < 9; i++) {
            boolean hot = i == winRow && now - resultAt < 2400;
            float ry = rowY0 + i * rowH;
            if (i % 2 == 0) {
                rf.set(ptX + 6, ry - rowH * 0.78f, ptX + ptW - 6, ry + rowH * 0.22f);
                p.setColor(0x14FFFFFF);
                cv.drawRoundRect(rf, 8, 8, p);
            }
            if (hot) {
                rf.set(ptX + 6, ry - rowH * 0.78f, ptX + ptW - 6, ry + rowH * 0.22f);
                p.setColor(0x55FFD700);
                cv.drawRoundRect(rf, 8, 8, p);
                animating = true;
            }
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(hot ? 0xFFFFD54F : 0xFFD6E4FF);
            p.setTextSize(h * 0.0125f);
            p.setFakeBoldText(i == 0 || hot);
            cv.drawText(PAY_NAMES[i], ptX + ptW * 0.04f, ry, p);
            for (int b = 0; b < BETS.length; b++) {
                float cxR = ptX + ptW * (0.52f + b * 0.12f);
                if (b == betIdx) {
                    rf.set(cxR - ptW * 0.055f, ry - rowH * 0.78f, cxR + ptW * 0.055f, ry + rowH * 0.22f);
                    p.setColor(i == 0 ? 0xFFC62828 : 0x66C62828);
                    cv.drawRoundRect(rf, 6, 6, p);
                }
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(hot ? 0xFFFFD54F : (b == betIdx ? 0xFFFFFFFF : 0xFF9FB6E8));
                p.setFakeBoldText(b == betIdx || i == 0 || hot);
                cv.drawText("" + (PAY_MULT[i] * BETS[b]), cxR, ry, p);
            }
            p.setFakeBoldText(false);
            p.setTextAlign(Paint.Align.LEFT);
        }

        // ---- hand row ----
        float cw = w * 0.165f, ch = cw * 1.42f, gap = w * 0.028f;
        float total = 5 * cw + 4 * gap;
        float x0 = (w - total) / 2f, y0 = h * 0.358f;
        for (int i = 0; i < hand.size(); i++) {
            long t0 = cardT[i];
            float t = Math.min(1f, (now - t0) / 300f);
            if (t < 1f) animating = true;
            float scaleX = 1f;
            if (t < 1f) scaleX = (float) Math.cos(t * Math.PI / 2);
            float y = y0 + (held[i] ? -h * 0.008f : 0);
            drawCard(cv, hand.get(i), x0 + i * (cw + gap), y, cw, ch, held[i], scaleX, now);
            if (held[i]) animating = true;
        }
        if (hand.isEmpty()) {
            // empty card slots
            for (int i = 0; i < 5; i++) {
                rf.set(x0 + i * (cw + gap), y0, x0 + i * (cw + gap) + cw, y0 + ch);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(0x336FA8FF);
                cv.drawRoundRect(rf, cw * 0.09f, cw * 0.09f, p);
                p.setStyle(Paint.Style.FILL);
            }
            float bw = w * 0.52f, bhh = h * 0.038f;
            rf.set(w / 2f - bw / 2f, y0 + ch / 2f - bhh / 2f, w / 2f + bw / 2f, y0 + ch / 2f + bhh / 2f);
            p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xFF1B34A8, 0xFF0A1750, Shader.TileMode.CLAMP));
            cv.drawRoundRect(rf, bhh / 2, bhh / 2, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(0xFFFFD54F);
            cv.drawRoundRect(rf, bhh / 2, bhh / 2, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFD700); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(h * 0.017f); p.setFakeBoldText(true);
            cv.drawText("\u25B6  TAP DEAL TO PLAY", w / 2, y0 + ch / 2 + h * 0.006f, p);
            p.setFakeBoldText(false);
        }

        // ---- message / win banner ----
        if (phase == 2 && lastWin > 0 && now - resultAt < 2300 && !levelUpShowing) {
            float t = Math.min(1f, (now - resultAt) / 380f);
            float sc = easeOutBack(t);
            animating = true;
            cv.save();
            cv.scale(sc, sc, w / 2f, h * 0.590f);
            drawFancyText(cv, message, w / 2f, h * 0.590f, h * 0.036f, 0xFFFFE082, 0xFFFF8F00);
            drawFancyText(cv, "+" + lastWin, w / 2f, h * 0.635f, h * 0.030f, 0xFFA5D6A7, 0xFF2E7D32);
            cv.restore();
            if (particlesOn) {
                for (int i = 0; i < 16; i++) {
                    float xx = (px[i] + pvx[i] * (now - resultAt)) * w;
                    float yy = (py[i] + pvy[i] * (now - resultAt) + 0.0000009f * (now - resultAt) * (now - resultAt)) * h;
                    if ((now - resultAt) < 1200) drawChip(cv, xx, yy, w * 0.015f, CHIP_COLORS[i % 4], "", false);
                }
            }
        } else {
            p.setColor(0xFFE8F0FF); p.setTextSize(h * 0.022f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText(message, w / 2, h * 0.598f, p);
            p.setFakeBoldText(false);
        }

        drawCoinBurst(cv, w, h, now);

        // ---- controls ----
        float bh = h * 0.056f;
        long idleMs = now - Math.max(resultAt, lastTouchAt);
        float pulse = ((phase == 0 || phase == 2) && idleMs > 3000) ? (float) (0.5 + 0.5 * Math.sin(now / 450.0)) : 0f;
        if (pulse > 0) animating = true;
        if (phase == 0 || phase == 2) {
            float chipR = w * 0.060f, chipGap = w * 0.040f;
            float rowW = BETS.length * chipR * 2 + (BETS.length - 1) * chipGap;
            float cx0 = w / 2f - rowW / 2f + chipR;
            p.setColor(0xCCFFD54F); p.setTextSize(h * 0.0125f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            p.setShadowLayer(4, 0, 1, 0x66000000);
            cv.drawText("\u25BC  TAP A CHIP TO SET YOUR BET  \u25BC", w / 2f, h * 0.660f, p);
            p.clearShadowLayer();
            p.setFakeBoldText(false);
            for (int i = 0; i < BETS.length; i++) {
                boolean sel = i == betIdx;
                drawChip(cv, cx0 + i * (chipR * 2 + chipGap), h * 0.710f + (sel ? -h * 0.008f : 0), chipR, CHIP_COLORS[i], "" + BETS[i], sel);
            }

            // MAX BET shortcut
            float mx = w / 2f + rowW / 2f + chipGap * 0.6f + chipR * 0.8f;
            float bump = now - betBumpAt < 250 ? 1f + 0.25f * (1f - (now - betBumpAt) / 250f) : 1f;
            if (now - betBumpAt < 250) postInvalidateDelayed(28);
            p.setColor(0xFFFFD54F);
            cv.drawCircle(mx, h * 0.710f, chipR * 0.8f * bump, p);
            p.setColor(0xFF6D4C00); p.setTextSize(chipR * 0.4f * bump); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText("MAX", mx, h * 0.710f + chipR * 0.14f * bump, p);
            p.setFakeBoldText(false);
            drawPillButton(cv, w * 0.14f, h * 0.785f, w * 0.72f, bh, "DEAL   \u25B6", 0xFF43A047, 0xFF1B5E20,
                    balance >= BETS[betIdx], pressedBtn == 0, pulse);
        } else {
            drawPillButton(cv, w * 0.14f, h * 0.765f, w * 0.72f, bh, "DRAW", 0xFFE53935, 0xFF8E0000, true, pressedBtn == 1, pulse > 0 ? pulse : (float) (0.35 + 0.35 * Math.sin(now / 450.0)));
            animating = true;
        }

        // ---- level-up overlay ----
        if (levelUpPending > 0 && !levelUpShowing && now - resultAt > 1400) {
            levelUpShowing = true; levelUpAt = now;
        }
        if (levelUpShowing) {
            animating = true;
            float t = Math.min(1f, (now - levelUpAt) / 420f);
            cv.drawColor(0xB3000000);
            float sc = easeOutBack(t);
            cv.save();
            cv.scale(sc, sc, w / 2f, h * 0.42f);
            float badgeR = w * 0.15f;
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFD700);
            cv.drawCircle(w / 2f, h * 0.36f, badgeR, p);
            p.setColor(0xFF6D4C00); p.setTextSize(badgeR * 0.9f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText("" + levelUpPending, w / 2f, h * 0.36f + badgeR * 0.32f, p);
            p.setFakeBoldText(false);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w * 0.012f); p.setColor(0x55FFD700);
            for (int i = 0; i < 12; i++) {
                double a = Math.PI / 6 * i + now / 900.0;
                cv.drawLine(w / 2f + (float) Math.cos(a) * badgeR * 1.25f, h * 0.36f + (float) Math.sin(a) * badgeR * 1.25f,
                        w / 2f + (float) Math.cos(a) * badgeR * 1.6f, h * 0.36f + (float) Math.sin(a) * badgeR * 1.6f, p);
            }
            p.setStyle(Paint.Style.FILL);
            drawFancyText(cv, "LEVEL UP!", w / 2f, h * 0.53f, h * 0.045f, 0xFFFFE082, 0xFFFF8F00);
            p.setColor(0xFFFFFFFF); p.setTextSize(h * 0.022f); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("Reward: +" + levelUpReward + " coins", w / 2f, h * 0.575f, p);
            cv.restore();
            if (t >= 1f)
                drawPillButton(cv, w * 0.3f, h * 0.62f, w * 0.4f, h * 0.052f, "COLLECT", 0xFF8E24AA, 0xFF4A148C, true, pressedBtn == 9, 0);
        }

        if (animating) postInvalidateDelayed(32);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int w = getWidth(), h = getHeight();
        float x = e.getX(), y = e.getY();
        if (Math.hypot(x - w * 0.93f, y - getHeight() * 0.042f) <= w * 0.06f) {
            GameSounds.toggle(getContext());
            invalidate();
            return true;
        }

        float bh = h * 0.056f;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            lastTouchAt = System.currentTimeMillis();
            pressedBtn = hitButton(w, h, bh, x, y);
            if (pressedBtn >= 0) invalidate();
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int btn = hitButton(w, h, bh, x, y);
            int was = pressedBtn; pressedBtn = -1; invalidate();
            if (levelUpShowing) {
                if (btn == 9 && was == 9) { levelUpShowing = false; levelUpPending = 0; levelUpReward = 0; invalidate(); }
                return true;
            }
            if (phase == 1) {
                // card taps toggle hold
                float cw = w * 0.165f, ch = cw * 1.42f, gap = w * 0.028f;
                float total = 5 * cw + 4 * gap;
                float x0 = (w - total) / 2f, y0 = h * 0.358f;
                if (y >= y0 - h * 0.045f && y <= y0 + ch) {
                    for (int i = 0; i < hand.size(); i++) {
                        if (x >= x0 + i * (cw + gap) && x <= x0 + i * (cw + gap) + cw) {
                            held[i] = !held[i]; cardNew[i] = false;
                            heldFlash = i; heldFlashAt = System.currentTimeMillis();
                            invalidate();
                            return true;
                        }
                    }
                }
                if (btn == 1 && was == 1) draw();
                return true;
            }
            if (btn == 0 && was == 0 && balance >= BETS[betIdx]) { deal(); return true; }
            if ((phase == 0 || phase == 2) && btn < 0) selectChipAt(w, h, x, y);
            return true;
        }
        return true;
    }

    private int hitButton(int w, int h, float bh, float x, float y) {
        if (levelUpShowing) {
            if (x >= w * 0.3f && x <= w * 0.7f && y >= h * 0.62f && y <= h * 0.62f + h * 0.052f) return 9;
            return -1;
        }
        if (phase == 0 || phase == 2) {
            if (x >= w * 0.14f && x <= w * 0.86f && y >= h * 0.785f && y <= h * 0.785f + bh) return 0;
        } else if (phase == 1) {
            if (x >= w * 0.14f && x <= w * 0.86f && y >= h * 0.765f && y <= h * 0.765f + bh) return 1;
        }
        return -1;
    }


    private void maxBet() {
        for (int i = BETS.length - 1; i >= 0; i--) {
            if (balance >= BETS[i]) {
                if (i != betIdx) {
                    betIdx = i;
                    GameSounds.play(getContext(), GameSounds.CHIP);
                    betBumpAt = System.currentTimeMillis();
                }
                invalidate();
                if (listener != null) listener.saveState();
                return;
            }
        }
    }

    private void selectChipAt(int w, int h, float x, float y) {
        float chipR = w * 0.060f, chipGap = w * 0.040f;
        float rowW = BETS.length * chipR * 2 + (BETS.length - 1) * chipGap;
        float cx0 = w / 2f - rowW / 2f + chipR;
        float mx = w / 2f + rowW / 2f + chipGap * 0.6f + chipR * 0.8f;
        if (Math.hypot(x - mx, y - h * 0.710f) <= chipR * 1.2f) { maxBet(); return; }
        for (int i = 0; i < BETS.length; i++) {
            float cx = cx0 + i * (chipR * 2 + chipGap);
            float ddx = x - cx, ddy = y - h * 0.710f;
            if (ddx * ddx + ddy * ddy <= chipR * chipR * 1.44f && i != betIdx) {
                betIdx = i;
                invalidate();
                if (listener != null) listener.saveState();
                return;
            }
        }
    }
}
