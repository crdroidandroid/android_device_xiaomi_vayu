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
package org.lineageos.settings.refreshrate;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RefreshSettingsFragment extends SettingsBasePreferenceFragment
        implements ApplicationsState.Callbacks {

    private static final String KEY_MASTER = "refresh_master_enabled";

    private Context mAppContext;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ActivityFilter mActivityFilter;
    private AllPackagesAdapter mAllPackagesAdapter;
    private RefreshUtils mRefreshUtils;
    private RecyclerView mAppsRecyclerView;

    private float[] mSupportedRates = new float[0];
    private int[] mSupportedStates = { RefreshUtils.STATE_DEFAULT };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.refresh_settings, rootKey);

        final MainSwitchPreference master = findPreference(KEY_MASTER);
        if (master != null) {
            final Context appContext = requireContext().getApplicationContext();
            master.setChecked(RefreshUtils.isServiceEnabled(appContext));
            master.setOnPreferenceChangeListener((pref, newValue) -> {
                final boolean enabled = (Boolean) newValue;
                RefreshUtils.setServiceEnabled(appContext, enabled);
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
        mRefreshUtils = RefreshUtils.getInstance(mAppContext);

        mSupportedRates = mRefreshUtils.getSupportedRates();
        mSupportedStates = RefreshUtils.getSupportedStates(mSupportedRates);

        mApplicationsState = ApplicationsState.getInstance(requireActivity().getApplication());
        mSession = mApplicationsState.newSession(this);
        mActivityFilter = new ActivityFilter(context.getPackageManager());
        mAllPackagesAdapter = new AllPackagesAdapter(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View wrapper = inflater.inflate(R.layout.refresh_container, container, false);

        final View prefs = super.onCreateView(inflater, container, savedInstanceState);
        final ViewGroup prefsContainer = wrapper.findViewById(R.id.prefs_container);
        prefsContainer.addView(prefs, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final RecyclerView prefsList = getListView();
        if (prefsList != null) {
            prefsList.setNestedScrollingEnabled(false);
        }

        mAppsRecyclerView = wrapper.findViewById(R.id.refresh_rv_view);
        mAppsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAppsRecyclerView.setAdapter(mAllPackagesAdapter);

        updateAppListState(RefreshUtils.isServiceEnabled(mAppContext));
        return wrapper;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSupportedRates();
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
        if (entries == null || !RefreshUtils.isServiceEnabled(mAppContext)) {
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

    private void refreshSupportedRates() {
        final float[] rates = mRefreshUtils.getSupportedRates();
        final int[] states = RefreshUtils.getSupportedStates(rates);
        mSupportedRates = rates;
        if (!Arrays.equals(states, mSupportedStates)) {
            mSupportedStates = states;
            mAllPackagesAdapter.onSupportedStatesChanged();
        }
    }

    private void rebuild() {
        if (mSession == null || !RefreshUtils.isServiceEnabled(mAppContext)) {
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

    private int getEffectiveStateForPackage(String packageName) {
        return RefreshUtils.getEffectiveState(
                mRefreshUtils.getStateForPackage(packageName), mSupportedRates);
    }

    private int positionForState(int state) {
        for (int i = 0; i < mSupportedStates.length; i++) {
            if (mSupportedStates[i] == state) {
                return i;
            }
        }
        return 0; // Default
    }

    private static int getStateDrawable(int state) {
        switch (state) {
            case RefreshUtils.STATE_LOW:
                return R.drawable.ic_refresh_30;
            case RefreshUtils.STATE_MODERATE:
                return R.drawable.ic_refresh_50;
            case RefreshUtils.STATE_STANDARD:
                return R.drawable.ic_refresh_60;
            case RefreshUtils.STATE_HIGH:
                return R.drawable.ic_refresh_90;
            case RefreshUtils.STATE_EXTREME:
                return R.drawable.ic_refresh_120;
            case RefreshUtils.STATE_DEFAULT:
            default:
                return R.drawable.ic_refresh_default;
        }
    }

    private static int getStateLabel(int state) {
        switch (state) {
            case RefreshUtils.STATE_LOW:
                return R.string.refresh_low;
            case RefreshUtils.STATE_MODERATE:
                return R.string.refresh_moderate;
            case RefreshUtils.STATE_STANDARD:
                return R.string.refresh_standard;
            case RefreshUtils.STATE_HIGH:
                return R.string.refresh_high;
            case RefreshUtils.STATE_EXTREME:
                return R.string.refresh_extreme;
            case RefreshUtils.STATE_DEFAULT:
            default:
                return R.string.refresh_default;
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

    private class ModeAdapter extends BaseAdapter {

        private final LayoutInflater mInflater;

        private ModeAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return mSupportedStates.length;
        }

        @Override
        public Object getItem(int position) {
            return mSupportedStates[position];
        }

        @Override
        public long getItemId(int position) {
            return mSupportedStates[position];
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
            view.setText(getStateLabel(mSupportedStates[position]));
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

        private void onSupportedStatesChanged() {
            mModeAdapter.notifyDataSetChanged();
            notifyDataSetChanged();
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
                    .inflate(R.layout.refresh_list_item, parent, false));

            holder.mode.setAdapter(mModeAdapter);

            final View.OnClickListener openSpinner = v -> holder.mode.performClick();
            holder.title.setOnClickListener(openSpinner);
            holder.stateIcon.setOnClickListener(openSpinner);

            holder.mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position,
                        long id) {
                    final Object tag = parent.getTag();
                    if (!(tag instanceof ApplicationsState.AppEntry)
                            || position < 0 || position >= mSupportedStates.length) {
                        return;
                    }
                    final String pkg = ((ApplicationsState.AppEntry) tag).info.packageName;
                    final int selected = mSupportedStates[position];
                    if (getEffectiveStateForPackage(pkg) != selected) {
                        mRefreshUtils.writePackage(pkg, selected);
                    }
                    holder.stateIcon.setImageResource(getStateDrawable(selected));
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

            final int state = getEffectiveStateForPackage(entry.info.packageName);
            // Tag before selection so any deferred onItemSelected() from the
            // Spinner sees the entry this row is now bound to.
            holder.mode.setTag(entry);
            holder.mode.setSelection(positionForState(state), false);
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
            // Called from ApplicationsState's background thread.
            synchronized (mLauncherPackages) {
                return mLauncherPackages.contains(entry.info.packageName);
            }
        }
    }
}
