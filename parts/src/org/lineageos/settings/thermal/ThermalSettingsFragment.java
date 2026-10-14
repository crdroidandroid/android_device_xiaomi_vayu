/**
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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ThermalSettingsFragment extends SettingsBasePreferenceFragment
        implements ApplicationsState.Callbacks {

    private static final String KEY_MASTER = "thermal_master_enabled";

    private Context mAppContext;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ActivityFilter mActivityFilter;
    private AllPackagesAdapter mAllPackagesAdapter;
    private ThermalUtils mThermalUtils;
    private RecyclerView mAppsRecyclerView;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.thermal_settings, rootKey);

        final MainSwitchPreference master = findPreference(KEY_MASTER);
        if (master != null) {
            final Context appContext = requireContext().getApplicationContext();
            master.setChecked(ThermalUtils.isServiceEnabled(appContext));
            master.setOnPreferenceChangeListener((pref, newValue) -> {
                final boolean enabled = (Boolean) newValue;
                ThermalUtils.setServiceEnabled(appContext, enabled);
                updateAppListState(enabled);
                if (enabled) rebuild();
                return true;
            });
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Context context = requireContext();
        mAppContext = context.getApplicationContext();
        mThermalUtils = ThermalUtils.getInstance(mAppContext);

        mApplicationsState = ApplicationsState.getInstance(requireActivity().getApplication());
        mSession = mApplicationsState.newSession(this);
        mActivityFilter = new ActivityFilter(context.getPackageManager());
        mAllPackagesAdapter = new AllPackagesAdapter(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View wrapper = inflater.inflate(R.layout.thermal_container, container, false);

        final View prefs = super.onCreateView(inflater, container, savedInstanceState);
        final ViewGroup prefsContainer = wrapper.findViewById(R.id.prefs_container);
        prefsContainer.addView(prefs, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final RecyclerView prefsList = getListView();
        if (prefsList != null) {
            prefsList.setNestedScrollingEnabled(false);
        }

        mAppsRecyclerView = wrapper.findViewById(R.id.thermal_rv_view);
        mAppsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAppsRecyclerView.setAdapter(mAllPackagesAdapter);

        updateAppListState(ThermalUtils.isServiceEnabled(mAppContext));
        return wrapper;
    }

    @Override
    public void onResume() {
        super.onResume();
        mSession.onResume();
        rebuild();
    }

    @Override
    public void onPause() {
        mSession.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (mAppsRecyclerView != null) {
            mAppsRecyclerView.setAdapter(null);
            mAppsRecyclerView = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        mSession.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onPackageListChanged() {
        mActivityFilter.updateLauncherInfoList();
        rebuild();
    }

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries == null || !ThermalUtils.isServiceEnabled(mAppContext)) {
            return;
        }
        mAllPackagesAdapter.setEntries(entries);
    }

    @Override
    public void onLoadEntriesCompleted() {
        rebuild();
    }

    @Override
    public void onAllSizesComputed() {
    }

    @Override
    public void onLauncherInfoChanged() {
    }

    @Override
    public void onPackageIconChanged() {
    }

    @Override
    public void onPackageSizeChanged(String packageName) {
    }

    @Override
    public void onRunningStateChanged(boolean running) {
    }

    private void rebuild() {
        if (mSession == null || !ThermalUtils.isServiceEnabled(mAppContext)) {
            return;
        }
        mSession.rebuild(mActivityFilter, ApplicationsState.ALPHA_COMPARATOR);
    }

    private void updateAppListState(boolean enabled) {
        if (mAppsRecyclerView != null) {
            mAppsRecyclerView.setEnabled(enabled);
            mAppsRecyclerView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    private static int getStateDrawable(int state) {
        switch (state) {
            case ThermalUtils.STATE_BENCHMARK:
                return R.drawable.ic_thermal_benchmark;
            case ThermalUtils.STATE_BROWSER:
                return R.drawable.ic_thermal_browser;
            case ThermalUtils.STATE_CAMERA:
                return R.drawable.ic_thermal_camera;
            case ThermalUtils.STATE_DIALER:
                return R.drawable.ic_thermal_dialer;
            case ThermalUtils.STATE_GAMING:
                return R.drawable.ic_thermal_gaming;
            case ThermalUtils.STATE_STREAMING:
                return R.drawable.ic_thermal_streaming;
            case ThermalUtils.STATE_DEFAULT:
            default:
                return R.drawable.ic_thermal_default;
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final Spinner mode;
        private final ImageView icon;
        private final ImageView stateIcon;

        private ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.app_name);
            mode = view.findViewById(R.id.app_mode);
            icon = view.findViewById(R.id.app_icon);
            stateIcon = view.findViewById(R.id.state);
        }
    }

    private static class ModeAdapter extends BaseAdapter {

        // Order must match ThermalUtils.STATE_*.
        private static final int[] ITEMS = {
                R.string.thermal_default,
                R.string.thermal_benchmark,
                R.string.thermal_browser,
                R.string.thermal_camera,
                R.string.thermal_dialer,
                R.string.thermal_gaming,
                R.string.thermal_streaming
        };

        private final LayoutInflater mInflater;

        private ModeAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return ITEMS.length;
        }

        @Override
        public Object getItem(int position) {
            return ITEMS[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final TextView view;
            if (convertView instanceof TextView) {
                view = (TextView) convertView;
            } else {
                view = (TextView) mInflater.inflate(
                        android.R.layout.simple_spinner_dropdown_item, parent, false);
            }
            view.setText(ITEMS[position]);
            view.setTextSize(14f);
            return view;
        }
    }

    private class AllPackagesAdapter extends RecyclerView.Adapter<ViewHolder> {

        private final ModeAdapter mModeAdapter;
        private List<ApplicationsState.AppEntry> mEntries = new ArrayList<>();

        private AllPackagesAdapter(Context context) {
            mModeAdapter = new ModeAdapter(context);
            setHasStableIds(true);
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        @Override
        public long getItemId(int position) {
            return mEntries.get(position).id;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final ViewHolder holder = new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.thermal_list_item, parent, false));

            holder.mode.setAdapter(mModeAdapter);

            final View.OnClickListener openSpinner = v -> holder.mode.performClick();
            holder.title.setOnClickListener(openSpinner);
            holder.stateIcon.setOnClickListener(openSpinner);

            holder.mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position,
                        long id) {
                    final Object tag = parent.getTag();
                    if (!(tag instanceof ApplicationsState.AppEntry)) {
                        return;
                    }
                    final String pkg = ((ApplicationsState.AppEntry) tag).info.packageName;
                    if (mThermalUtils.getStateForPackage(pkg) != position) {
                        mThermalUtils.writePackage(pkg, position);
                    }
                    holder.stateIcon.setImageResource(getStateDrawable(position));
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final ApplicationsState.AppEntry entry = mEntries.get(position);
            if (entry == null) {
                return;
            }

            holder.title.setText(entry.label);
            mApplicationsState.ensureIcon(entry);
            holder.icon.setImageDrawable(entry.icon);

            final int state = mThermalUtils.getStateForPackage(entry.info.packageName);
            holder.mode.setTag(entry);
            holder.mode.setSelection(state, false);
            holder.stateIcon.setImageResource(getStateDrawable(state));
        }

        private void setEntries(List<ApplicationsState.AppEntry> entries) {
            mEntries = new ArrayList<>(entries);
            notifyDataSetChanged();
        }
    }

    private static class ActivityFilter implements ApplicationsState.AppFilter {

        private final PackageManager mPackageManager;
        private final Set<String> mLauncherPackages = new HashSet<>();

        private ActivityFilter(PackageManager packageManager) {
            mPackageManager = packageManager;
            updateLauncherInfoList();
        }

        public void updateLauncherInfoList() {
            final Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            final List<ResolveInfo> resolveInfoList = mPackageManager.queryIntentActivities(i, 0);

            final Set<String> packages = new HashSet<>(resolveInfoList.size());
            for (ResolveInfo ri : resolveInfoList) {
                packages.add(ri.activityInfo.packageName);
            }

            synchronized (mLauncherPackages) {
                mLauncherPackages.clear();
                mLauncherPackages.addAll(packages);
            }
        }

        @Override
        public void init() {
        }

        @Override
        public boolean filterApp(ApplicationsState.AppEntry entry) {
            synchronized (mLauncherPackages) {
                return mLauncherPackages.contains(entry.info.packageName);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            requireActivity().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
