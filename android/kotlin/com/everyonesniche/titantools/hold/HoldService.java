package com.everyonesniche.titantools.hold;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.InputMethod;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import com.everyonesniche.titantools.core.Installed;
import com.everyonesniche.titantools.core.OldApps;
import com.everyonesniche.titantools.core.Prefs;
import com.everyonesniche.titantools.core.Status;

/**
 * Hold a letter to type its symbol, from Hold Symbols 0.3.1. An eligible plain letter from the built-in keyboard is held back
 * until it's released (the letter), another key comes (the letter first, then that key), or the hold time passes (the
 * symbol). Everything else passes through untouched. No text is read and nothing is stored.
 *
 * It steps aside, and letters pass through, while: its switch is off, TitanKeys is the keyboard (TitanKeys does holds
 * itself), the old Hold Symbols is still on, Shizuku isn't ready, the screen is off or locked, or no text field is focused.
 */
public final class HoldService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener, HoldEngine.Output {
    public static volatile HoldService live;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HoldEngine engine = new HoldEngine(this);
    private SharedPreferences prefs;
    private InputMethod input;
    private final SymbolMap map = SymbolMap.defaults();
    private boolean enabled, passwords, haptic, dead, titanKeysSelected, oldAppOn;
    private boolean editorActive, editorPassword, screenUsable = true;
    private int threshold = Prefs.DEFAULT_HOLD_MILLIS;

    private final BroadcastReceiver screens = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            refreshScreenState();
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) settle();
        }
    };
    private final ContentObserver settings = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { refreshKeyboard(); }
    };

    @Override public void onServiceConnected() {
        live = this;
        prefs = Prefs.INSTANCE.of(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        load();
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screens, f, Context.RECEIVER_NOT_EXPORTED);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, settings);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, settings);
        refreshKeyboard();
        refreshScreenState();
        Status.INSTANCE.changed();
    }

    private void load() {
        enabled = prefs.getBoolean(Prefs.HOLD_ON, false);
        passwords = prefs.getBoolean(Prefs.HOLD_PASSWORDS, false);
        haptic = prefs.getBoolean(Prefs.HOLD_VIBRATE, true);
        threshold = Prefs.INSTANCE.holdMillis(this);
        engine.threshold(threshold);
    }

    /** Whether TitanKeys is the keyboard and whether the old Hold Symbols is on, read again whenever either changes. */
    private void refreshKeyboard() {
        boolean wasPaused = titanKeysSelected || oldAppOn;
        titanKeysSelected = HoldPause.INSTANCE.pausedFor(Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD));
        oldAppOn = Installed.INSTANCE.oldRunning(this, OldApps.INSTANCE.of(OldApps.Tool.Hold));
        if (!wasPaused && (titanKeysSelected || oldAppOn)) settle();
        Status.INSTANCE.changed();
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        load();
        if (!enabled) settle();
        Status.INSTANCE.changed();
    }

    /** Delivers any held-back key now and forgets modifier state. Never drops a key. */
    public void settle() {
        main.removeCallbacksAndMessages(null);
        engine.reset(SystemClock.uptimeMillis());
    }

    @Override public InputMethod onCreateInputMethod() {
        input = new InputMethod(this) {
            @Override public void onStartInput(EditorInfo info, boolean restarting) {
                if (!restarting) engine.resolve(SystemClock.uptimeMillis());
                refreshScreenState();
                int kind = info.inputType & InputType.TYPE_MASK_CLASS;
                int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
                editorActive = kind == InputType.TYPE_CLASS_TEXT;
                editorPassword = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                        || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
            }
            @Override public void onFinishInput() {
                engine.resolve(SystemClock.uptimeMillis());
                editorActive = false;
                editorPassword = false;
            }
        };
        return input;
    }

    private void refreshScreenState() {
        PowerManager power = getSystemService(PowerManager.class);
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        screenUsable = power != null && power.isInteractive() && (keyguard == null || !keyguard.isKeyguardLocked());
    }

    /** Why the tool is stepping aside overall, for the home screen, or null when it's working. */
    public String pauseReason() {
        if (dead) return "Paused: turn Titan Tools on in Accessibility";
        if (titanKeysSelected) return "Paused: TitanKeys does holds";
        if (oldAppOn) return "Paused: the old Hold Symbols is still on";
        if (!KeyInjector.INSTANCE.ready()) return "Paused: Shizuku isn't ready";
        return null;
    }

    /** Why letters pass through right now, or null when holds are active. */
    private String currentBlock() {
        if (!enabled) return "off";
        String pause = pauseReason();
        if (pause != null) return pause;
        if (!screenUsable) return "screen";
        if (!editorActive || input == null || !input.getCurrentInputStarted()) return "no field";
        if (editorPassword && !passwords) return "password";
        return null;
    }

    private static boolean modified(KeyEvent e) {
        int mask = KeyEvent.META_SHIFT_ON | KeyEvent.META_ALT_ON | KeyEvent.META_CTRL_ON | KeyEvent.META_META_ON
                | KeyEvent.META_SYM_ON | KeyEvent.META_FUNCTION_ON | KeyEvent.META_CAPS_LOCK_ON;
        return (KeyEvent.normalizeMetaState(e.getMetaState()) & mask) != 0;
    }

    private static boolean modifierKey(int key) {
        return KeyEvent.isModifierKey(key) || key == KeyEvent.KEYCODE_CAPS_LOCK || key == KeyEvent.KEYCODE_SYM
                || key == KeyEvent.KEYCODE_NUM_LOCK || key == KeyEvent.KEYCODE_SCROLL_LOCK;
    }

    /** Only the built-in keyboard: not typed keys, not external keyboards. */
    private static boolean physical(KeyEvent e) {
        if (e.getDeviceId() == KeyCharacterMap.VIRTUAL_KEYBOARD) return false;
        InputDevice d = e.getDevice();
        return d != null && !d.isVirtual() && !d.isExternal() && e.isFromSource(InputDevice.SOURCE_KEYBOARD);
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        try {
            return handle(e);
        } catch (RuntimeException error) {
            try { engine.reset(SystemClock.uptimeMillis()); } catch (RuntimeException ignored) { }
            return false;
        }
    }

    private boolean handle(KeyEvent e) {
        int action = e.getAction(), key = e.getKeyCode();
        if (!physical(e) || (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP)) return false;
        if (action == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            // The keyboard light's log notes that a key went down (never which one).
            com.everyonesniche.titantools.light.LightService light = com.everyonesniche.titantools.light.LightService.Companion.getInstance();
            if (light != null) light.keyPressed();
        }
        long now = SystemClock.uptimeMillis();
        boolean mod = modifierKey(key);
        if (action == KeyEvent.ACTION_UP) return engine.up(key, e.getDeviceId(), e.getEventTime(), mod);
        // Decisions use the events' own times, so a late delivery is harmless; only an absurd delay passes through.
        boolean eligible = currentBlock() == null && map.forKeyCode(key) != 0
                && e.getRepeatCount() == 0 && now >= e.getDownTime() && now - e.getDownTime() < 2000;
        boolean consume = engine.down(key, e.getDeviceId(), e.getDownTime(), mod, modified(e), eligible);
        HoldEngine.Press p = engine.pending();
        if (consume && p != null && p.downTime == e.getDownTime() && p.key == key) {
            final long id = p.id;
            main.postAtTime(() -> engine.holdDue(id), p.downTime + threshold);
        }
        return consume;
    }

    // HoldEngine.Output: runs before the triggering event's answer returns.
    @Override public void tap(int keyCode) {
        if (!KeyInjector.INSTANCE.press(keyCode, 0)) insertDirectly(String.valueOf((char) ('a' + keyCode - HoldEngine.KEYCODE_A)));
    }

    @Override public void hold(int keyCode) {
        char c = map.forKeyCode(keyCode);
        if (c == 0) { tap(keyCode); return; }
        if (!KeyInjector.INSTANCE.type(c) && !insertDirectly(String.valueOf(c))) return;
        if (haptic) {
            Vibrator v = getSystemService(Vibrator.class);
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
        }
    }

    /** Commits straight into the focused field: the last resort when typing a key fails. */
    private boolean insertDirectly(String text) {
        InputMethod.AccessibilityInputConnection c = input == null ? null : input.getCurrentInputConnection();
        if (c == null) return false;
        try {
            c.commitText(text, 1, null);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) refreshScreenState();
    }

    @Override public void onInterrupt() { settle(); }

    @Override public boolean onUnbind(Intent intent) {
        settle();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        try { engine.resolve(SystemClock.uptimeMillis()); } catch (RuntimeException ignored) { }
        dead = true;
        main.removeCallbacksAndMessages(null);
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
        try { unregisterReceiver(screens); } catch (IllegalArgumentException ignored) { }
        getContentResolver().unregisterContentObserver(settings);
        if (live == this) live = null;
        Status.INSTANCE.changed();
        super.onDestroy();
    }
}
