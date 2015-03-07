package com.mediatek.settings.ext;

import android.preference.PreferenceGroup;

public interface IPplSettingsEntryExt {
    /**
     * to add a phone security lock button
     * @return
     */

    public void addPplPrf(PreferenceGroup prefGroup);

    public void enablerResume();

    public void enablerPause();
}
