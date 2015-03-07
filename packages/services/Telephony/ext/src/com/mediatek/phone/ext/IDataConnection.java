package com.mediatek.phone.ext;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceActivity.Header;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import com.mediatek.xlog.Xlog;

import java.util.List;


/**
 * @deprecated not used in Host APP
 */
public interface IDataConnection {

    /**
     * Whether show this SIM slot's data connection item
     * @param context Used to query CT main SIM selection from database
     * @param bRoaming Whether Phone is in roaming state
     * @param simSlot Target SIM slot
     *
     * @return True if show this SIM slot's data connection item
     */
    boolean bShowDataConn(Context context, boolean bRoaming, int simSlot);

    /**
     *  TODO: Need comment later
     */
    int getGprsRadioInPreferenceProperty(int commonPosition, int simSlot) ;
}
