package com.mediatek.settings.ext;

import android.preference.Preference;
import android.preference.PreferenceScreen;
public interface IStatusGeminiExt {
    /**
     * Initialize the preference, it will be called in oncreate method
     * @param screen The screen of device info
     * @param preference The status preference 
     */
    void initUI(PreferenceScreen screen, Preference preference);
    
    /**
    * cusotmize network type name, it will be called when update Network Type
    * @param netWorkTypeName: the name of network type
    */
    String customizeNetworkTypeName(String netWorkTypeName);

}
