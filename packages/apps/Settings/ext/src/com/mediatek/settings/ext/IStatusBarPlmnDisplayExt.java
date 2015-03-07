package com.mediatek.settings.ext;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.view.View;

public interface IStatusBarPlmnDisplayExt {
    
    /**
     * set the roaming warning msg
     * @param context
     * @param res the res to used
     * 
     */
   
   void createCheckBox(PreferenceCategory pref, int order);
   
}
