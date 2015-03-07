package com.mediatek.settings.ext;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceScreen;
import android.preference.ListPreference;


public class DefaultDateTimeSettingsExt implements IDateTimeSettingsExt {

    @Override
    public void customizeDateTimePreferenceStatus(Context context,
        ListPreference listPreference,
        CheckBoxPreference checkBoxPreference){
    }

    @Override
    public void customizePreferenceScreen(Context context, PreferenceScreen pref) {
    }
}

