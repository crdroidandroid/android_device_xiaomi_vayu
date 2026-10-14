/*
 * Copyright (C) 2020 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.refreshrate;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;

import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Map;

public final class RefreshUtils {

    private static final String TAG = "RefreshUtils";

    private static final String REFRESH_ENABLED = "refresh_master_enabled";
    private static final String REFRESH_CONTROL = "refresh_control";

    private static final String KEY_OVERRIDE_ACTIVE = "refresh_override_active";
    private static final String KEY_SAVED_PEAK = "refresh_saved_peak";
    private static final String KEY_SAVED_MIN = "refresh_saved_min";

    static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";
    static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";

    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_LOW = 1;
    protected static final int STATE_MODERATE = 2;
    protected static final int STATE_STANDARD = 3;
    protected static final int STATE_HIGH = 4;
    protected static final int STATE_EXTREME = 5;

    private static final String[] REFRESH_PREFIXES = {
            null,
            "refresh.low=",
            "refresh.moderate=",
            "refresh.standard=",
            "refresh.high=",
            "refresh.extreme=",
    };

    private static final float[] REFRESH_RATES = {
            0f,     // default: user's own setting
            30f,
            50f,
            60f,
            90f,
            120f,
    };

    private static volatile RefreshUtils sInstance;

    private final Context mContext;
    private final ContentResolver mResolver;
    private final SharedPreferences mSharedPrefs;

    private final Map<String, Integer> mPackageStates = new HashMap<>();
    private boolean mLoaded;

    private final Object mSettingsLock = new Object();
    private float mAppliedPeak = Float.NaN;
    private float mAppliedMin = Float.NaN;

    private RefreshUtils(Context context) {
        mContext = context;
        mResolver = context.getContentResolver();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static RefreshUtils getInstance(Context context) {
        if (sInstance == null) {
            synchronized (RefreshUtils.class) {
                if (sInstance == null) {
                    sInstance = new RefreshUtils(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    public static void initialize(Context context) {
        if (isServiceEnabled(context)) {
            startService(context);
        } else {
            stopService(context);
            getInstance(context).restoreUserRefreshRate();
        }
    }

    public static void startService(Context context) {
        context.startServiceAsUser(new Intent(context, RefreshService.class),
                UserHandle.CURRENT);
    }

    protected static void stopService(Context context) {
        context.stopService(new Intent(context, RefreshService.class));
    }

    protected static void setServiceEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putBoolean(REFRESH_ENABLED, enabled).apply();
        initialize(context);
    }

    protected static boolean isServiceEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(REFRESH_ENABLED, false);
    }

    protected static void setDefaultRefreshRate(Context context) {
        getInstance(context).restoreUserRefreshRate();
    }


    private void ensureLoadedLocked() {
        if (mLoaded) {
            return;
        }
        mPackageStates.clear();

        final String value = mSharedPrefs.getString(REFRESH_CONTROL, null);
        if (!TextUtils.isEmpty(value)) {
            for (String segment : value.split(":")) {
                for (int state = STATE_LOW; state < REFRESH_PREFIXES.length; state++) {
                    final String prefix = REFRESH_PREFIXES[state];
                    if (!segment.startsWith(prefix)) {
                        continue;
                    }
                    for (String pkg : segment.substring(prefix.length()).split(",")) {
                        pkg = pkg.trim();
                        if (!pkg.isEmpty()) {
                            // First match wins, same priority order as before.
                            mPackageStates.putIfAbsent(pkg, state);
                        }
                    }
                    break;
                }
            }
        }
        mLoaded = true;
    }

    private void persistLocked() {
        final StringBuilder[] lists = new StringBuilder[REFRESH_PREFIXES.length];
        for (int state = STATE_LOW; state < lists.length; state++) {
            lists[state] = new StringBuilder(REFRESH_PREFIXES[state]);
        }
        for (Map.Entry<String, Integer> e : mPackageStates.entrySet()) {
            lists[e.getValue()].append(e.getKey()).append(',');
        }

        final StringBuilder out = new StringBuilder();
        for (int state = STATE_LOW; state < lists.length; state++) {
            if (state > STATE_LOW) {
                out.append(':');
            }
            out.append(lists[state]);
        }
        mSharedPrefs.edit().putString(REFRESH_CONTROL, out.toString()).apply();
    }

    protected synchronized void writePackage(String packageName, int mode) {
        if (TextUtils.isEmpty(packageName) || mode < STATE_DEFAULT
                || mode >= REFRESH_PREFIXES.length) {
            return;
        }
        ensureLoadedLocked();

        final Integer previous = mode == STATE_DEFAULT
                ? mPackageStates.remove(packageName)
                : mPackageStates.put(packageName, mode);

        final int previousState = previous != null ? previous : STATE_DEFAULT;
        if (previousState != mode) {
            persistLocked();
        }
    }

    protected synchronized int getStateForPackage(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return STATE_DEFAULT;
        }
        ensureLoadedLocked();
        final Integer state = mPackageStates.get(packageName);
        return state != null ? state : STATE_DEFAULT;
    }


    protected void applyState(int state) {
        if (state <= STATE_DEFAULT || state >= REFRESH_RATES.length) {
            restoreUserRefreshRate();
            return;
        }

        synchronized (mSettingsLock) {
            if (!mSharedPrefs.getBoolean(KEY_OVERRIDE_ACTIVE, false)) {
                mSharedPrefs.edit()
                        .putString(KEY_SAVED_PEAK,
                                Settings.System.getString(mResolver, KEY_PEAK_REFRESH_RATE))
                        .putString(KEY_SAVED_MIN,
                                Settings.System.getString(mResolver, KEY_MIN_REFRESH_RATE))
                        .putBoolean(KEY_OVERRIDE_ACTIVE, true)
                        .commit();
            }

            final float peak = REFRESH_RATES[state];
            final float userMin = parseRate(mSharedPrefs.getString(KEY_SAVED_MIN, null), 0f);
            final float min = Math.min(userMin, peak);

            if (Float.compare(peak, mAppliedPeak) == 0 && Float.compare(min, mAppliedMin) == 0) {
                return; // Already applied, avoid a display mode re-evaluation.
            }

            mAppliedPeak = peak;
            mAppliedMin = min;
            Settings.System.putFloat(mResolver, KEY_PEAK_REFRESH_RATE, peak);
            Settings.System.putFloat(mResolver, KEY_MIN_REFRESH_RATE, min);
        }
    }

    protected void restoreUserRefreshRate() {
        synchronized (mSettingsLock) {
            mAppliedPeak = Float.NaN;
            mAppliedMin = Float.NaN;
            if (!mSharedPrefs.getBoolean(KEY_OVERRIDE_ACTIVE, false)) {
                return;
            }
            restoreSettingLocked(KEY_PEAK_REFRESH_RATE,
                    mSharedPrefs.getString(KEY_SAVED_PEAK, null));
            restoreSettingLocked(KEY_MIN_REFRESH_RATE,
                    mSharedPrefs.getString(KEY_SAVED_MIN, null));
            clearSnapshotLocked();
        }
    }

    protected boolean onSystemRefreshRateChanged() {
        synchronized (mSettingsLock) {
            if (!mSharedPrefs.getBoolean(KEY_OVERRIDE_ACTIVE, false)) {
                return false;
            }

            final float peak = Settings.System.getFloat(mResolver, KEY_PEAK_REFRESH_RATE,
                    Float.NaN);
            final float min = Settings.System.getFloat(mResolver, KEY_MIN_REFRESH_RATE,
                    Float.NaN);
            final boolean peakIsOurs = Float.compare(peak, mAppliedPeak) == 0;
            final boolean minIsOurs = Float.compare(min, mAppliedMin) == 0;
            if (peakIsOurs && minIsOurs) {
                return false;
            }

            if (peakIsOurs) {
                restoreSettingLocked(KEY_PEAK_REFRESH_RATE,
                        mSharedPrefs.getString(KEY_SAVED_PEAK, null));
            }
            if (minIsOurs) {
                restoreSettingLocked(KEY_MIN_REFRESH_RATE,
                        mSharedPrefs.getString(KEY_SAVED_MIN, null));
            }
            mAppliedPeak = Float.NaN;
            mAppliedMin = Float.NaN;
            clearSnapshotLocked();
            return true;
        }
    }

    private void restoreSettingLocked(String key, String savedValue) {
        if (savedValue != null) {
            Settings.System.putString(mResolver, key, savedValue);
            return;
        }

        int rows = 0;
        try {
            rows = mResolver.delete(Settings.System.CONTENT_URI,
                    Settings.NameValueTable.NAME + "=?", new String[] { key });
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to delete " + key, e);
        }
        if (rows == 0 && Settings.System.getString(mResolver, key) != null) {
            Settings.System.putFloat(mResolver, key,
                    KEY_MIN_REFRESH_RATE.equals(key) ? 0f : getMaxSupportedRate());
        }
    }

    private void clearSnapshotLocked() {
        mSharedPrefs.edit()
                .remove(KEY_SAVED_PEAK)
                .remove(KEY_SAVED_MIN)
                .putBoolean(KEY_OVERRIDE_ACTIVE, false)
                .commit();
    }

    private float getMaxSupportedRate() {
        float max = REFRESH_RATES[STATE_STANDARD];
        final DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        final Display display = dm != null ? dm.getDisplay(Display.DEFAULT_DISPLAY) : null;
        if (display != null) {
            for (Display.Mode mode : display.getSupportedModes()) {
                max = Math.max(max, mode.getRefreshRate());
            }
        }
        return max;
    }

    private static float parseRate(String value, float def) {
        if (value == null) {
            return def;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
