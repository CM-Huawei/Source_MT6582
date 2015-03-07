package com.mediatek.settings.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.preference.PreferenceGroup;

import com.mediatek.xlog.Xlog;

public class DefaultPplSettingsEntryExt extends ContextWrapper implements IPplSettingsEntryExt {
    private static final String TAG = "PPL/PplSettingsEntryExt";
    public DefaultPplSettingsEntryExt(Context context) {
        super(context);
    }

    public void addPplPrf(PreferenceGroup prefGroup) {
        Xlog.d(TAG,"addPplPrf() default");
    }

    public void enablerResume() {
        Xlog.d(TAG,"enablerResume() default");
    }

    public void enablerPause() {
        Xlog.d(TAG,"enablerPause() default");
    }
}
