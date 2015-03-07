package com.mediatek.phone.ext;

import android.app.Activity;
import android.preference.PreferenceScreen;

public interface ICallSettingsConnection {

	/**
     * Finish current Activity and start CT customized Activity instead
     * 
     * @param activity Default CallSettings activity
     * 
     * Used in CallSettings.java
     */
    void startCallSettingsActivity(Activity activity);

    /**
     * Replace Preference title according to CT spec
     * @param prefSet The PreferenceScreen contains the to be modify preference
     * 
     * Used in CdmaCallForwardOptions.java
     */
    void setCallForwardPrefsTitle(PreferenceScreen prefSet);

    /**
     * CT uses com.mediatek.phone.plugin.CdmaAdditionalCallOptions instead of
     * com.mediatek.settings.CdmaCallWaitingOptions
     * 
     * Used in MultipleSimActivity.java
     */
    String getCdmaCallOptionClassName();
}
