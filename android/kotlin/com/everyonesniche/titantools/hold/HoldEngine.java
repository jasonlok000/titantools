package com.everyonesniche.titantools.hold;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Main-thread key-ordering state machine. Pure Java so it can be tested on the host.
 *
 * An eligible, unmodified letter DOWN is held back. The hold timer types its symbol
 * at the threshold while the key is still down. Before that, its release, the next
 * key's DOWN or reset() resolves it from the events' own timestamps (held >= threshold
 * -> symbol, else the plain letter), always BEFORE the next key continues.
 * The matching UP (and any driver auto-repeat DOWNs) of a held-back key is always
 * swallowed, so no app sees half a key and Android never starts its own repeat.
 */
public final class HoldEngine {
    public static final int KEYCODE_A = 29, KEYCODE_Z = 54;
    public interface Output { void tap(int keyCode); void hold(int keyCode); }
    public static final class Press {
        public final long id, downTime;
        public final int key, device;
        Press(long id, int key, int device, long downTime) {
            this.id = id; this.key = key; this.device = device; this.downTime = downTime;
        }
        long slot() { return HoldEngine.slot(device, key); }
    }
    private final Output out;
    private final Map<Long, Long> swallowed = new HashMap<>(); // device/key -> downTime
    private final Set<Integer> modifiers = new HashSet<>();
    private Press pending;
    private long nextId;
    private int threshold = 400;
    private boolean modifierUsed, oneShotFence;
    public HoldEngine(Output out) { this.out = out; }
    public static boolean isLetter(int key) { return key >= KEYCODE_A && key <= KEYCODE_Z; }
    private static long slot(int device, int key) { return ((long) device << 32) | (key & 0xffffffffL); }
    public Press pending() { return pending; }
    public int swallowing() { return swallowed.size(); }
    public void threshold(int ms) { threshold = ms; }

    /** @return true to consume this physical DOWN. Output callbacks run before returning. */
    public boolean down(int key, int device, long downTime, boolean modifierKey,
            boolean modified, boolean eligible) {
        long s = slot(device, key);
        Long held = swallowed.get(s);
        if (held != null) {
            if (held == downTime) return true; // driver auto-repeat of a held-back key
            swallowed.remove(s);                // fresh press: the old release was lost
        }
        resolve(downTime); // a held-back key always lands before the next key continues
        if (modifierKey) {
            if (modifiers.isEmpty()) modifierUsed = false;
            modifiers.add(key);
            return false;
        }
        boolean fence = oneShotFence;
        oneShotFence = false;
        if (!modifiers.isEmpty()) modifierUsed = true;
        if (!isLetter(key) || !eligible || modified || !modifiers.isEmpty() || fence) return false;
        pending = new Press(++nextId, key, device, downTime);
        swallowed.put(s, downTime);
        return true;
    }

    /** @return true to consume this physical UP (only ever the UP of a consumed DOWN). */
    public boolean up(int key, int device, long eventTime, boolean modifierKey) {
        long s = slot(device, key);
        if (swallowed.remove(s) != null) {
            if (pending != null && pending.slot() == s) resolve(eventTime);
            return true;
        }
        if (modifierKey && modifiers.remove(key) && modifiers.isEmpty()) {
            // A tapped Shift/Alt is sticky for the next key in many editors: leave that key native.
            if (!modifierUsed) oneShotFence = true;
            modifierUsed = false;
        }
        return false;
    }

    /**
     * Hold timer at downTime + threshold: types the symbol while the key is still down.
     * The UP stays swallowed. A release that arrives later than the timer is still judged
     * by resolve() from its own timestamp, so a delayed main thread cannot lose a symbol.
     */
    public boolean holdDue(long id) {
        if (pending == null || pending.id != id) return false;
        Press p = pending; pending = null;
        out.hold(p.key);
        return true;
    }

    /** Resolve the held-back key as if released at time `at` (same clock as event times). */
    public void resolve(long at) {
        if (pending == null) return;
        Press p = pending; pending = null;
        if (at - p.downTime >= threshold) out.hold(p.key); else out.tap(p.key);
    }

    /** Resolves at `now`, then forgets modifier state. UPs of delivered keys stay swallowed. */
    public void reset(long now) {
        resolve(now); modifiers.clear(); modifierUsed = false; oneShotFence = false;
        if (swallowed.size() > 64) swallowed.clear();
    }
}
