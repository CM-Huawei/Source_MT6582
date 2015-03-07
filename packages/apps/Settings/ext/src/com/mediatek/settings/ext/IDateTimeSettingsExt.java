package com.mediatek.settings.ext;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceScreen;
import android.preference.ListPreference;

public interface IDateTimeSettingsExt {

    /**
    * Customize the Date/time preference status based on plugin's specific logic.
    * It will be called onRume life cycle callback, to ensure the status is always 
    * right
    * @param Context : The application contect
    * @param listPreference : The time preference
    * @param checkBoxPreference : The date preference, 
    */
    void customizeDateTimePreferenceStatus(Context context,
            ListPreference listPreference,
            CheckBoxPreference checkBoxPreference);

	/**
    * M: customize Preference Screen, add or remove preference.
    * @param context: the application context
    * @param pref: preference screen in data and time
    */
    void customizePreferenceScreen(Context context, PreferenceScreen pref);

}


