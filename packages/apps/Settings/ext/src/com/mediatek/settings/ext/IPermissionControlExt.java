package com.mediatek.settings.ext;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import android.preference.PreferenceGroup;

public interface IPermissionControlExt {
    /**
     * to add a permission contorl button
     * @return
     */

    public void addPermSwitchPrf(PreferenceGroup prefGroup);
    
    public void enablerResume();
    
    public void enablerPause();
    
    public void addAutoBootPrf(PreferenceGroup prefGroup);
}