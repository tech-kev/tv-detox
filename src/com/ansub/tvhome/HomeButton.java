package com.ansub.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

// Opt-in (Settings > Accessibility > Home button): makes Home open this home screen on TVs where the
// TV's own home screen has to stay on, like Google TV and Chromecast. Nothing gets turned off, so
// switching it off puts Home straight back to the TV's own.
//
// Google TV acts on the Home key before accessibility services see it, so it can't be kept from opening.
// Instead this watches for Google TV's main screen or its dashboard (a held Home) to open and covers it
// at once. A Chromecast remote sends Home two or three times per press, and each one brings Google TV
// back, so a Google TV that shows again right after gets covered once more when the remote is done.
// Any other window opening in between means Home wasn't where the TV was going, and nothing happens.
// Settings > Open Google TV stays there until the next Home; Settings > Catch Home turns it all off.
public class HomeButton extends AccessibilityService {
    private static final ComponentName GOOGLE_TV_HOME = new ComponentName(
            "com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.home.HomeActivity");
    private static final String GOOGLE_TV_DASHBOARD = "com.google.android.apps.tv.launcherx.dashboard.";
    // Long enough for a remote's repeated Homes (about 330 ms apart at most on a Chromecast) to land.
    private static final long SETTLE_MS = 350;
    // Google TV reports its main screen twice per start, about 70 ms apart: one cover is enough.
    private static final long DEBOUNCE_MS = 300;
    private static long lastOpened = -DEBOUNCE_MS;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable cover = this::cover;
    private boolean googleTvOnTop;

    // On a Chromecast the system keeps Home and this never sees it; where a TV does pass it on, a press
    // while Google TV is already up (no new window) still comes back.
    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME && event.getAction() == KeyEvent.ACTION_UP
                && catchHome(this) && setupDone()) {
            prefs(this).edit().remove("google_tv_boot").remove("google_tv_boot_count").commit();
            googleTvOnTop = true;
            settle();
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = String.valueOf(event.getPackageName()), cls = String.valueOf(event.getClassName());
        // Toasts and the volume bar come from System UI and don't change what's on screen.
        if ("com.android.systemui".equals(pkg)) return;
        if (GOOGLE_TV_HOME.getPackageName().equals(pkg)) {
            // Google TV's other windows (its FrameLayout overlays, profile pages) say nothing about Home.
            if (!GOOGLE_TV_HOME.getClassName().equals(cls) && !cls.startsWith(GOOGLE_TV_DASHBOARD)) return;
            googleTvOnTop = true;
            // First sight: cover it straight away. Right after a cover: wait for the remote's last Home.
            if (SystemClock.elapsedRealtime() - lastOpened >= DEBOUNCE_MS) cover();
            else settle();
        } else {
            googleTvOnTop = false;
            handler.removeCallbacks(cover);
        }
    }

    private void settle() {
        handler.removeCallbacks(cover);
        handler.postDelayed(cover, SETTLE_MS);
    }

    private void cover() {
        if (!googleTvOnTop || !setupDone() || !catchHome(this)) return;
        SharedPreferences prefs = prefs(this);
        if (stayingOnGoogleTv(prefs)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastOpened < DEBOUNCE_MS) {
            handler.postDelayed(cover, DEBOUNCE_MS - (now - lastOpened));
            return;
        }

        // Give an unfinished launch time to resume, then look again. Successful launches don't delay the next Home.
        long pending = prefs.getLong("pending", -1);
        if (pending >= 0 && now >= pending && now - pending < 1000) {
            handler.postDelayed(cover, 1000 - (now - pending));
            return;
        }
        int failures = prefs.getInt("failures", 0);
        if (pending >= 0) failures++;
        if (failures >= 3) {
            prefs.edit().remove("pending").putInt("failures", failures).commit();
            return;
        }
        // Save before launching: a crash can take the service down with the activity.
        if (!prefs.edit().putLong("pending", now).putInt("failures", failures).commit()) return;
        lastOpened = now;
        try {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {}
    }

    @Override
    public void onInterrupt() {}

    // During the TV's first setup, Home is left alone.
    private boolean setupDone() {
        return Settings.Secure.getInt(getContentResolver(), "user_setup_complete", 1) == 1;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("home_button", 0);
    }

    // Settings > Catch Home. On unless turned off; off gives Home back to Google TV.
    static boolean catchHome(Context c) {
        return prefs(c).getBoolean("catch_home", true);
    }

    static void setCatchHome(Context c, boolean on) {
        prefs(c).edit().putBoolean("catch_home", on).commit();
    }

    // Shown in Settings only where there's a Google TV home screen to go to.
    static boolean hasGoogleTv(Context c) {
        try {
            c.getPackageManager().getActivityInfo(GOOGLE_TV_HOME, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Back on this home screen: Home comes here again.
    static void onHomeResumed(Context c) {
        prefs(c).edit().remove("google_tv_boot").remove("google_tv_boot_count").commit();
    }

    // This home screen opened and didn't crash. Also resets a stopped redirect loop.
    static void onHomeOk(Context c) {
        prefs(c).edit().remove("pending").remove("failures").commit();
        lastOpened = -500;
    }

    private static int bootCount(Context c) {
        return Build.VERSION.SDK_INT >= 24
                ? Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1) : -1;
    }

    private static long bootTime() {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    private boolean stayingOnGoogleTv(SharedPreferences prefs) {
        if (!prefs.contains("google_tv_boot")) return false;
        int boot = bootCount(this);
        int saved = prefs.getInt("google_tv_boot_count", -1);
        if (boot >= 0 && saved >= 0) return boot == saved;
        return Math.abs(bootTime() - prefs.getLong("google_tv_boot", 0)) < 5000;
    }

    // Opens Google TV's own home screen and keeps it there. False if this TV doesn't have it.
    static boolean openGoogleTv(Context c) {
        SharedPreferences prefs = prefs(c);
        // Keep the choice through process death and updates, but not a TV restart.
        if (!prefs.edit().putLong("google_tv_boot", bootTime())
                .putInt("google_tv_boot_count", bootCount(c)).commit()) return false;
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setComponent(GOOGLE_TV_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            prefs.edit().remove("google_tv_boot").remove("google_tv_boot_count").commit();
            return false;
        }
    }
}
