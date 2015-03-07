package com.mediatek.settings.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.preference.Preference;
import android.preference.PreferenceActivity.Header;
import android.preference.PreferenceScreen;
import android.widget.ImageView;

import com.mediatek.xlog.Xlog;

import java.util.List;


/* Dummy implmentation , do nothing */
public class DefaultSettingsMiscExt extends ContextWrapper implements ISettingsMiscExt {
    
    private static final String TAG = "DefaultSettingsMiscExt";

    public DefaultSettingsMiscExt(Context base) {
        super(base);
    }

    public boolean isWifiToggleCouldDisabled(Context context) {
        return true;
    }

    public String getTetherWifiSSID(Context ctx) {
        return ctx.getString(
                com.android.internal.R.string.wifi_tether_configure_ssid_default);
    }

    public void setTimeoutPrefTitle(Preference pref) {
    }
    
    public void setFactoryResetTitle(Object obj) {
    }

    public void addCustomizedItem(List<Header> target, int index) {
    }

    public String customizeSimDisplayString(String simString, int slotId) {
        return simString;
    }

    public void customizeLocationHeaderClick(Header header) {
    }

    public void initCustomizedLocationSettings(PreferenceScreen root, int order) {
    }

    public void updateCustomizedLocationSettings() {
    }

    public boolean needCustomizeHeaderIcon(Header header) {
        return false;
    }

    public void customizeHeaderIcon(ImageView iconView, Header header) {
    }
}

