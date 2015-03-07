package com.mediatek.phone.ext;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceActivity.Header;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;


import com.mediatek.xlog.Xlog;

import java.util.List;


public class DefaultDataConnection implements IDataConnection {

    private static final String TAG = "DefaultDataConnection";    

    public boolean bShowDataConn(Context context, boolean bRoaming, int simSlot) {
        Xlog.d(TAG,"bShowDataConn:" + true);
        return true;
    }

    public int getGprsRadioInPreferenceProperty(int commonPosition, int simSlot) {
         Xlog.i(TAG, "TEST:getGprsRadioInPreferenceProperty:" + commonPosition);
         return commonPosition;
    }
}

