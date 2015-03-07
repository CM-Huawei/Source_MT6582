package com.mediatek.settings.ext;

import android.content.Context;
import android.os.SystemProperties;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

public class DefaultWifiApDialogExt implements IWifiApDialogExt {
    private static final String TAG = "DefaultWifiApDialogExt";
    private static final String KEY_PROP_WPS2_SUPPORT = "persist.radio.wifi.wps2support";
    private static final int WPA2_INDEX = 2;
    private boolean mWps2Test;

    public void setAdapter(Context context, Spinner spinner, int arrayId) {
        mWps2Test = "true".equals(SystemProperties.get(KEY_PROP_WPS2_SUPPORT, "true"));
        if (mWps2Test) {
            String[] setUpArray = context.getResources().getStringArray(arrayId);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            adapter.add(setUpArray[0]);
            adapter.add(setUpArray[2]);
            spinner.setAdapter(adapter);
        }
    }
    public int getSelection(int index) {
        int selection = index;
        if (mWps2Test && index == WPA2_INDEX) {
            selection = index - 1;
        }
        return selection;
    }
    public int getSecurityType(int position) {
        int security = position;
        if (mWps2Test && position == 1) {
            security = position + 1;
        }
        return security;
    }
}
