package com.mediatek.settings.ext;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

public class DefaultBatteryExt implements IBatteryExt {

    @Override
    public void loadPreference(Context context, PreferenceGroup listGroup) {

    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return false;
    }
}
