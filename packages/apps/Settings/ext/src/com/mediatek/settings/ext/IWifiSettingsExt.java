package com.mediatek.settings.ext;

import android.content.ContentResolver;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import java.util.List;

public interface IWifiSettingsExt {
    /**
     * Whether add forget menu item for the access point 
     * @param ssid SSID of the access point
     * @param ssid security of the access point
     */
    boolean shouldAddForgetMenu(String ssid, int security);
    /**
     * Called when register priority observer
     * @param contentResolver The parent contentResolver
     */
    void registerPriorityObserver(ContentResolver contentResolver);
    /**
     * Called when unregister priority observer
     * @param contentResolver The parent contentResolver
     */
    void unregisterPriorityObserver(ContentResolver contentResolver);
    /**
     * Remember the configration of last connected access point
     * @param config The configration of last connected access point
     */
    void setLastConnectedConfig(WifiConfiguration config);
    /**
     * Remember the priority of last connected access point
     * @param priority The priority of last connected access point
     */
    void setLastPriority(int priority);
    /**
     * Update priority for access point
     */
    void updatePriority();
    /**
     * Whether add disconnect menu for the current selected access point
     */
    boolean shouldAddDisconnectMenu();
    /**
     * Whether access point catogory exist
     */
    boolean isCatogoryExist();
    /**
     * Initialize catogory for access points
     */
    void setCategory(PreferenceCategory trustPref, PreferenceCategory configedPref, 
            PreferenceCategory newPref);
    /**
     * Remove all prefereces in every catogory
     * @param screen The parent screen
     */
    void emptyCategory(PreferenceScreen screen);
    /**
     * Remove all prefereces in the screen
     * @param screen The parent screen
     */
    void emptyScreen(PreferenceScreen screen);
    /**
     * Whether the access point is tructed
     * @param ssid ssid of the access point
     * @param security security of the access point
     */
    boolean isTustAP(String ssid, int security);
    /**
     * Refresh the category
     * @param screen The parent screen
     */
    void refreshCategory(PreferenceScreen screen);
    /**
     * get count of access points in the screen
     * @param screen The parent screen
     */
    int getAccessPointsCount(PreferenceScreen screen);
    /**
     * Reorder priority of all the access points
     */
    void adjustPriority();
    /**
     * Record priority of the selected access points
     * @param selectPriority The priority of the selected access points
     */
    void recordPriority(int selectPriority);
    /**
     * update priority of access points
     * @param config The configuration of the latest connect access point
     */
    void setNewPriority(WifiConfiguration config);
    /**
     * update priority of access points after click submit button
     * @param config The configuration of the wifi dialog
     */
    void updatePriorityAfterSubmit(WifiConfiguration config);
    /**
     * Disconnect current connected access point
     * @param networkId The network id of the access point
     */
    void disconnect(int networkId);
    /**
     * update priority of access points after the access point is connected
     * @param networkId The network id of the access point
     */
    void updatePriorityAfterConnect(int networkId);
    
    
    static int CONFIGED_AP = 0;
    static int NEW_AP = 1;
    static int COMMON_AP = 2;
    /**
     * add all accessPoints to screen
     * @param screen The current screen
     * @param preference the current AccessPoint
     * @param flag 0:trustCategory or configedCategory; 1: trustCategory or  newCategory; 2: add to common screen  
     * @param ssid ssid of the access point
     * @param security security of the access point  
     */
    void addPreference(PreferenceScreen screen, Preference preference, int flag, String ssid, int security);
    
    /**
     * add all Category to screen
     * @param screen The current screen
     */
    void addCategories(PreferenceScreen screen);
    
    /**
     * get all category
     * @return all category
     */
    List<PreferenceGroup> getPreferenceCategory(PreferenceScreen screen);
}
