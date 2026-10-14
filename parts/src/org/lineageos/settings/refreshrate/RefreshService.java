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

import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

public class RefreshService extends Service {

    private static final String TAG = "RefreshService";
    private static final boolean DEBUG = false;

    private static final long UPDATE_DELAY_MS = 100;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private RefreshUtils mRefreshUtils;
    private IActivityTaskManager mActivityTaskManager;
    private KeyguardManager mKeyguardManager;
    private ContentObserver mSettingsObserver;

    private boolean mScreenOn = true;
    private String mLastPackage;

    private final Runnable mUpdateRunnable = this::updateForegroundProfile;

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            scheduleUpdate(UPDATE_DELAY_MS);
        }
    };

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenOn = false;
                mHandler.removeCallbacks(mUpdateRunnable);
                mLastPackage = null;
                mRefreshUtils.restoreUserRefreshRate();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOn = true;
                if (mKeyguardManager == null || !mKeyguardManager.isKeyguardLocked()) {
                    scheduleUpdate(0);
                }
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                mScreenOn = true;
                scheduleUpdate(0);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "Creating service");

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mRefreshUtils = RefreshUtils.getInstance(this);
        mKeyguardManager = getSystemService(KeyguardManager.class);

        final PowerManager pm = getSystemService(PowerManager.class);
        mScreenOn = pm == null || pm.isInteractive();

        mHandler.post(mRefreshUtils::restoreUserRefreshRate);

        mSettingsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                if (mRefreshUtils.onSystemRefreshRateChanged() && DEBUG) {
                    Log.d(TAG, "User changed refresh rate, adopted as default");
                }
            }
        };
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(RefreshUtils.KEY_PEAK_REFRESH_RATE),
                false, mSettingsObserver);
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(RefreshUtils.KEY_MIN_REFRESH_RATE),
                false, mSettingsObserver);

        mActivityTaskManager = ActivityTaskManager.getService();
        try {
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to register task stack listener", e);
        }

        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mScreenReceiver, filter, null, mHandler,
                Context.RECEIVER_NOT_EXPORTED);

        scheduleUpdate(0);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");

        try {
            unregisterReceiver(mScreenReceiver);
        } catch (IllegalArgumentException e) {
            // Not registered
        }

        if (mSettingsObserver != null) {
            getContentResolver().unregisterContentObserver(mSettingsObserver);
        }

        if (mActivityTaskManager != null) {
            try {
                mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to unregister task stack listener", e);
            }
        }

        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(mRefreshUtils::restoreUserRefreshRate);
        mHandlerThread.quitSafely();

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void scheduleUpdate(long delayMs) {
        final Handler handler = mHandler;
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(mUpdateRunnable);
        handler.postDelayed(mUpdateRunnable, delayMs);
    }

    private void updateForegroundProfile() {
        if (!mScreenOn || mActivityTaskManager == null) {
            return;
        }

        final RootTaskInfo info;
        try {
            info = mActivityTaskManager.getFocusedRootTaskInfo();
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to get focused task", e);
            return;
        }
        if (info == null || info.topActivity == null) {
            return;
        }

        final String foregroundApp = info.topActivity.getPackageName();
        if (foregroundApp.equals(mLastPackage)) {
            return;
        }
        mLastPackage = foregroundApp;

        final int state = mRefreshUtils.getStateForPackage(foregroundApp);
        if (DEBUG) Log.d(TAG, foregroundApp + " -> state " + state);
        mRefreshUtils.applyState(state);
    }
}
