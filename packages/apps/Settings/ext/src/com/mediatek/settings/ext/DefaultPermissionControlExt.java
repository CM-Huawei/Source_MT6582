package com.mediatek.settings.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.preference.PreferenceGroup;

import com.mediatek.xlog.Xlog;

public class DefaultPermissionControlExt extends ContextWrapper implements IPermissionControlExt {
    private static final String TAG="DefaultPermissionControlExt";
    public DefaultPermissionControlExt(Context context) {
        super(context);
    }

    public void addPermSwitchPrf(PreferenceGroup prefGroup) {
        Xlog.d(TAG,"will not add permission preference");
    }

    public void enablerResume() {
        Xlog.d(TAG,"enablerResume() default");
    }

    public void enablerPause() {
        Xlog.d(TAG,"enablerPause() default");
    }
    
    public void addAutoBootPrf(PreferenceGroup prefGroup) {
        Xlog.d(TAG,"will not add auto boot entry preference");
    }
}
