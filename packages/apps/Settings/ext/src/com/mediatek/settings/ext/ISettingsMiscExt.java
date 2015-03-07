package com.mediatek.settings.ext;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceActivity.Header;
import android.preference.PreferenceScreen;
import android.widget.ImageView;

import java.util.List;

public interface ISettingsMiscExt {

    /**
     *  Customize whether wifi toggle could be disabled.
     * @param ctx
     * @return if wifi toggle button could be disabled. 
     */
    boolean isWifiToggleCouldDisabled(Context ctx); 

    /**
     * Custimize the SSID of wifi tethering
     * @param ctx: parent context
     * @return tether wifi string.
     */
    String getTetherWifiSSID(Context ctx);
    
    /**
     * Custimize the title of screen timeout preference 
     * @param pref the screen timeout preference
     */
    void setTimeoutPrefTitle(Preference pref);

    /**
     *Customize the title of factory reset settings
     * @param obj header or activity
     * @return factory reset title.
     */
    void setFactoryResetTitle(Object obj);


    /**
    * Add customize headers in settings, lick International Roaming header
    * @param target: header list in settings
    * @param index: position of customized item to be added
    */
    void addCustomizedItem(List<Header> target, int index);

    /**
    * Customize strings which contains 'SIM', replace 'SIM' by 'UIM/SIM','UIM','card' etc.
    * @param simString : the strings which contains SIM
    * @param soltId : 1 , slot1 0, slot0 , -1 means always.
    */
    String customizeSimDisplayString(String simString, int slotId);

    /**
     * Add the operator customize settings in Settings->Location 
     * @param pref: the root preferenceScreen
     * @param order: the customize settings preference order
     */
    void initCustomizedLocationSettings(PreferenceScreen root, int order);

    /**
     * Update customize settings when location mode changed
     */
    void updateCustomizedLocationSettings();

    /**
     * Customize the display UI when click Settings->Location access
     * @param header: the location access header
     */
    void customizeLocationHeaderClick(Header header);

    /**
     * Whether we need to customized the header icon of operator requiement.
     * 
     * @param header
     * @return
     */
    boolean needCustomizeHeaderIcon(Header header);

    /**
     * Customize head icon using operator drawable resource.
     * 
     * @param iconView
     * @param header
     */
    void customizeHeaderIcon(ImageView iconView, Header header);
}
