package com.mediatek.settings.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.preference.PreferenceGroup;

import com.mediatek.xlog.Xlog;

public class DefaultMdmPermControlExt extends ContextWrapper implements IMdmPermissionControlExt {
    private static final String TAG = "DefaultMdmPermControlExt";
    public DefaultMdmPermControlExt(Context context) {
        super(context);
    }

    public void addMdmPermCtrlPrf(PreferenceGroup prefGroup) {
        Xlog.d(TAG,"will not add mdm permission control");
    }

    public void enablerResume() {
        Xlog.d(TAG,"enablerResume() default");
    }

    public void enablerPause() {
        Xlog.d(TAG,"enablerPause() default");
    }
}
