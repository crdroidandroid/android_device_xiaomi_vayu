/*
 * Copyright (C) 2021 Chaldeaprjkt
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

package org.lineageos.settings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import org.lineageos.settings.refreshrate.RefreshActivity;

public class TileEntryActivity extends Activity {

    private static final String TAG = "TileEntryActivity";

    private static final String REFRESH_TILE = "org.lineageos.settings.refreshrate.RefreshTileService";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ComponentName source = getIntent().getParcelableExtra(
                Intent.EXTRA_COMPONENT_NAME, ComponentName.class);
        if (source == null) {
            finish();
            return;
        }

        if (REFRESH_TILE.equals(source.getClassName())) {
            openActivitySafely(new Intent(this, RefreshActivity.class));
        } else {
            finish();
        }
    }

    private void openActivitySafely(Intent dest) {
        dest.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        try {
            startActivity(dest);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No activity found for " + dest, e);
        }
        finish();
    }
}
