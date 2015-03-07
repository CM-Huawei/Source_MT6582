package com.mediatek.settings.ext;

import android.content.Context;
import android.content.Intent;
import android.preference.DialogPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;

import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.List;

public interface ISimManagementExt {
    /**
     * Remove the Auto_wap push preference screen
     * 
     * @param parent parent preference to set
     */
    void updateSimManagementPref(PreferenceGroup parent);

    /**
     * Remove the Sim color preference
     * 
     * @param preference fragment
     */
    void updateSimEditorPref(PreferenceFragment pref);

    /**
     *Update change Data connect dialog state.
     * 
     * @Param preferenceFragment
     * @Param isResumed
     */
    void dealWithDataConnChanged(Intent intent, boolean isResumed);

    /**
     *update default SIM Summary.
     * 
     * @Param preferenceFragment
     * @Param simID
     */
    void updateDefaultSIMSummary(DialogPreference pref, Long simID);

    /**
     *Show change data connection dialog
     * 
     * @Param preferenceFragment
     * @Param isResumed
     */
    void showChangeDataConnDialog(PreferenceFragment prefFragment,
            boolean isResumed);

    /**
     *Set to close sim slot id
     * 
     * @param simSlot
     */
    void setToClosedSimSlot(int simSlot);

    /**
     * Dual sim indicator new design, remove sim color editor preference
     * 
     * @param pref
     *            : sim editor preference fragment
     * @param key
     *            : sim color editor preference key
     */
    void customizeSimColorEditPreference(PreferenceFragment pref, String key);

    /**
     * customize choice items for voice , such as "internet call" or
     * "always ask""
     * 
     * @param the
     *            list displays all the normal voice items
     */
    void customizeVoiceChoiceArray(List<String> voiceList, boolean voipAvailable);

    /**
     * customize choice items for SMS , such as "always ask" or "Auto"
     * 
     * @param smsList
     *            the list displays all the normal SMS items
     */
    void customizeSmsChoiceArray(List<String> smsList);

    /**
     * customize sms slection value.
     * 
     * @param smsValueList
     *            the list for sms selection value
     */
    void customizeSmsChoiceValueArray(List<Long> smsValueList);
}
