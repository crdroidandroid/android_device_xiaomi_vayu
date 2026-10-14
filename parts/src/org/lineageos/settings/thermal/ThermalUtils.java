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

package org.lineageos.settings.thermal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

import java.util.HashMap;
import java.util.Map;

public final class ThermalUtils {

    private static final String THERMAL_ENABLED = "thermal_master_enabled";
    private static final String THERMAL_CONTROL = "thermal_control";

    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_BENCHMARK = 1;
    protected static final int STATE_BROWSER = 2;
    protected static final int STATE_CAMERA = 3;
    protected static final int STATE_DIALER = 4;
    protected static final int STATE_GAMING = 5;
    protected static final int STATE_STREAMING = 6;

    private static final String[] THERMAL_PREFIXES = {
            null,
            "thermal.benchmark=",
            "thermal.browser=",
            "thermal.camera=",
            "thermal.dialer=",
            "thermal.gaming=",
            "thermal.streaming=",
    };

    private static final String[] THERMAL_SCONFIG_VALUES = {
            "0",    // default
            "10",   // benchmark
            "11",   // browser
            "12",   // camera
            "8",    // dialer
            "9",    // gaming
            "14",   // streaming
    };

    private static final String THERMAL_SCONFIG = "/sys/class/thermal/thermal_message/sconfig";

    private static volatile ThermalUtils sInstance;

    private final SharedPreferences mSharedPrefs;

    private final Map<String, Integer> mPackageStates = new HashMap<>();
    private boolean mLoaded;

    private ThermalUtils(Context context) {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static ThermalUtils getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ThermalUtils.class) {
                if (sInstance == null) {
                    sInstance = new ThermalUtils(context.getApplicationContext());
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
            setDefaultThermalProfile();
        }
    }

    protected static void startService(Context context) {
        context.startServiceAsUser(new Intent(context, ThermalService.class),
                UserHandle.CURRENT);
    }

    protected static void stopService(Context context) {
        context.stopService(new Intent(context, ThermalService.class));
    }

    protected static void setServiceEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putBoolean(THERMAL_ENABLED, enabled).apply();
        initialize(context);
    }

    protected static boolean isServiceEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(THERMAL_ENABLED, false);
    }

    private void ensureLoadedLocked() {
        if (mLoaded) {
            return;
        }
        mPackageStates.clear();

        final String value = mSharedPrefs.getString(THERMAL_CONTROL, null);
        if (!TextUtils.isEmpty(value)) {
            for (String segment : value.split(":")) {
                for (int state = STATE_BENCHMARK; state < THERMAL_PREFIXES.length; state++) {
                    final String prefix = THERMAL_PREFIXES[state];
                    if (!segment.startsWith(prefix)) {
                        continue;
                    }
                    for (String pkg : segment.substring(prefix.length()).split(",")) {
                        pkg = pkg.trim();
                        if (!pkg.isEmpty()) {
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
        final StringBuilder[] lists = new StringBuilder[THERMAL_PREFIXES.length];
        for (int state = STATE_BENCHMARK; state < lists.length; state++) {
            lists[state] = new StringBuilder(THERMAL_PREFIXES[state]);
        }
        for (Map.Entry<String, Integer> e : mPackageStates.entrySet()) {
            lists[e.getValue()].append(e.getKey()).append(',');
        }

        final StringBuilder out = new StringBuilder();
        for (int state = STATE_BENCHMARK; state < lists.length; state++) {
            if (state > STATE_BENCHMARK) {
                out.append(':');
            }
            out.append(lists[state]);
        }
        mSharedPrefs.edit().putString(THERMAL_CONTROL, out.toString()).apply();
    }

    protected synchronized void writePackage(String packageName, int mode) {
        if (TextUtils.isEmpty(packageName) || mode < STATE_DEFAULT
                || mode >= THERMAL_PREFIXES.length) {
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

    protected static boolean writeState(int state) {
        if (state < STATE_DEFAULT || state >= THERMAL_SCONFIG_VALUES.length) {
            state = STATE_DEFAULT;
        }
        return FileUtils.writeLine(THERMAL_SCONFIG, THERMAL_SCONFIG_VALUES[state]);
    }

    protected static void setDefaultThermalProfile() {
        writeState(STATE_DEFAULT);
    }
}
