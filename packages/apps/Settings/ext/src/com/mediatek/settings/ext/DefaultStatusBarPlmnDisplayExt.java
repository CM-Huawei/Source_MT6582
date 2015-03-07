package com.mediatek.settings.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.preference.Preference; 
import android.preference.PreferenceCategory;
import android.view.View;
import com.mediatek.xlog.Xlog;
public class DefaultStatusBarPlmnDisplayExt extends ContextWrapper implements IStatusBarPlmnDisplayExt {
    static final String TAG = "DefaultStatusBarPlmnDisplayExt";
    public DefaultStatusBarPlmnDisplayExt(Context context) {
        super(context);
        Xlog.d(TAG, "Into DefaultStatusBarPlmnPlugin");
    }
     
     public void createCheckBox(PreferenceCategory pref,int j){
     Xlog.d(TAG, "Into Default createCheckBox");
     }
 
}
