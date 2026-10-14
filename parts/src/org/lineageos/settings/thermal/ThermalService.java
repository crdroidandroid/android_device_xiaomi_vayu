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

import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

public class ThermalService extends Service {

    private static final String TAG = "ThermalService";
    private static final boolean DEBUG = false;

    private static final long UPDATE_DELAY_MS = 100;

    private static final int STATE_UNKNOWN = -1;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private ThermalUtils mThermalUtils;
    private IActivityTaskManager mActivityTaskManager;

    private boolean mScreenOn = true;
    private int mCurrentState = STATE_UNKNOWN;

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
                applyState(ThermalUtils.STATE_DEFAULT);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOn = true;
                mCurrentState = STATE_UNKNOWN;
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

        mThermalUtils = ThermalUtils.getInstance(this);

        final PowerManager pm = getSystemService(PowerManager.class);
        mScreenOn = pm == null || pm.isInteractive();

        mActivityTaskManager = ActivityTaskManager.getService();
        try {
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to register task stack listener", e);
        }

        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
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

        if (mActivityTaskManager != null) {
            try {
                mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to unregister task stack listener", e);
            }
        }

        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(ThermalUtils::setDefaultThermalProfile);
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
        applyState(mThermalUtils.getStateForPackage(foregroundApp));
    }

    private void applyState(int state) {
        if (state == mCurrentState) {
            return;
        }
        if (DEBUG) Log.d(TAG, "Applying thermal state " + state);
        mCurrentState = ThermalUtils.writeState(state) ? state : STATE_UNKNOWN;
    }
}
