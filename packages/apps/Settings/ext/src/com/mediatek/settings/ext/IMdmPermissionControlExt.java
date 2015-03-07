package com.mediatek.settings.ext;

import android.preference.PreferenceGroup;

public interface IMdmPermissionControlExt {
    /**
     * to add a phone security lock button
     * @return
     */

    public void addMdmPermCtrlPrf(PreferenceGroup prefGroup);

    public void enablerResume();

    public void enablerPause();
}
