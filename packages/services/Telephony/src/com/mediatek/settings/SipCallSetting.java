/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.settings;

import android.app.ActionBar;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.view.MenuItem;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;
import com.android.phone.SipUtil;
import com.android.phone.sip.SipProfileDb;
import com.android.phone.sip.SipSharedPreferences;

import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.io.IOException;
import java.util.List;

public class SipCallSetting extends android.app.Activity { 

    private static final String BUTTON_SIP_CALL_OPTIONS =
        "sip_call_options_key";
    private static final String BUTTON_SIP_CALL_OPTIONS_WIFI_ONLY =
        "sip_call_options_wifi_only_key";
    private static final String SIP_SETTINGS_CATEGORY_KEY =
        "sip_settings_category_key";
    
    private static final String TAG = "Settings/SipCallSetting";

    private SipManager mSipManager;
    private SipProfileDb mProfileDb;
    private SipSharedPreferences mSipSharedPreferences;
    private ListPreference mListSipCallOptions;
    private CheckBoxPreference mButtonSipCallOptions;
    private Preference mAccountPreference;
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;
    @Override
    protected void onCreate(Bundle icicle) {
        ///M: for adjust setting UI on VXGA device. @{
        mFragment = new SipCallSettingFragment();
        super.onCreate(icicle);
        getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, mFragment).commit();
        /// @}
    }

    ///M: for adjust setting UI on VXGA device.
    public class SipCallSettingFragment extends PreferenceFragment implements 
            Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (PhoneUtils.isVoipSupported()) {
                mSipManager = SipManager.newInstance(getActivity());
                mSipSharedPreferences = new SipSharedPreferences(getActivity());
                addPreferencesFromResource(R.xml.sip_settings_category);
                mButtonSipCallOptions = (CheckBoxPreference)this.findPreference("open_sip_call_option_key");
                mButtonSipCallOptions.setOnPreferenceClickListener(this);
                mListSipCallOptions = getSipCallOptionPreference();
                mAccountPreference = this.findPreference("sip_account_settings_key");
                mProfileDb = new SipProfileDb(getActivity());
                /// M: ALPS00507618 @{
                // SIM manager control this logic for consistent UI, so remove it.
                /*if (GeminiUtils.isGeminiSupport()) {
                    this.getPreferenceScreen().removePreference(mListSipCallOptions);
                    mListSipCallOptions = null;
                } else {
                    mListSipCallOptions.setOnPreferenceChangeListener(this);
                    mListSipCallOptions.setValueIndex(
                            mListSipCallOptions.findIndexOfValue(
                                    mSipSharedPreferences.getSipCallOption()));
                    mListSipCallOptions.setSummary(mListSipCallOptions.getEntry());
                }*/
                getPreferenceScreen().removePreference(mListSipCallOptions);
                mListSipCallOptions = null;
                /// @}
            }
            ActionBar actionBar = getActivity().getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        public boolean onPreferenceClick(Preference preference) {
            if (preference == mButtonSipCallOptions) {
                //handleSipCallOptionsChange(objValue);
                CheckBoxPreference cp = (CheckBoxPreference)mButtonSipCallOptions;
                final int intEnable = cp.isChecked() ? 1 : 0;
                android.provider.Settings.System.putInt(PhoneGlobals.getInstance().getContentResolver(),
                         android.provider.Settings.System.ENABLE_INTERNET_CALL,
                         intEnable);
                new Thread(new Runnable() {
                    public void run() {
                        handleSipReceiveCallsOption(intEnable == 1);
                    }
                }).start();
                if (intEnable == 1) {
                    mAccountPreference.setEnabled(true);
                    if (mListSipCallOptions != null) {
                        mListSipCallOptions.setEnabled(true);
                    }
                    
                } else {
                    if (GeminiUtils.isGeminiSupport()) {
                        checkAndSetDefaultSim();
                    }
                    mAccountPreference.setEnabled(false);
                    if (mListSipCallOptions != null) {
                        mListSipCallOptions.setEnabled(false);
                    }
                }
            }
            return false;
        }

        public boolean onPreferenceChange(Preference preference, Object newValue) {
            // TODO Auto-generated method stub
            if (preference == mListSipCallOptions) {
                handleSipCallOptionsChange(newValue);
            }
            return true;
        }
    }

    protected void onResume() {
        super.onResume();
        /// For ALPS00736907, JE Happen, when phone don't support SIP Call
        //  and SipCallSetting resume. @{
        if (PhoneUtils.isVoipSupported()) {
            int enable = android.provider.Settings.System.getInt(PhoneGlobals.getInstance().getContentResolver(),
                    android.provider.Settings.System.ENABLE_INTERNET_CALL, 0);
            if (CallManager.getInstance().getState() != PhoneConstants.State.IDLE) {
                mAccountPreference.setEnabled(false);
                mButtonSipCallOptions.setEnabled(false);
                if (mListSipCallOptions != null) {
                    mListSipCallOptions.setEnabled(false);
                }
            } else {
                mAccountPreference.setEnabled(enable == 1);
                mButtonSipCallOptions.setEnabled(true);
                if (mListSipCallOptions != null) {
                    mListSipCallOptions.setEnabled(enable == 1);
                }
            }
            if (enable == 1 && !mButtonSipCallOptions.isChecked()) {
                mButtonSipCallOptions.setChecked(true);
            } else if (enable == 0 && mButtonSipCallOptions.isChecked()) {
                mButtonSipCallOptions.setChecked(false);
            }
        } else {
            PhoneLog.d(TAG, "in onResume, don't support sip call");
        }
        /// @}
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    // Gets the call options for SIP depending on whether SIP is allowed only
    // on Wi-Fi only; also make the other options preference invisible.
    private ListPreference getSipCallOptionPreference() {
        ListPreference wifiAnd3G = (ListPreference)mFragment.
                findPreference(BUTTON_SIP_CALL_OPTIONS);
        ListPreference wifiOnly = (ListPreference)mFragment.
                findPreference(BUTTON_SIP_CALL_OPTIONS_WIFI_ONLY);
        PreferenceScreen prefSet = mFragment.getPreferenceScreen();
        if (SipManager.isSipWifiOnly(this)) {
            prefSet.removePreference(wifiAnd3G);
            return wifiOnly;
        } else {
            prefSet.removePreference(wifiOnly);
            return wifiAnd3G;
        }
    }
    
    //Handle the special case for Gemini enhancement
    //The defaul sim is internet call &&
    //User diable the internet call
    //1. No sim card insert: set default sim to Settings.System.DEFAULT_SIM_NOT_SET
    //2. One sim card insert:set default sim to this sim
    //3. Two sim card insert: set default sim to the first
    private void checkAndSetDefaultSim() {
        long defaultSim = Settings.System.getLong(getContentResolver(), 
                Settings.System.VOICE_CALL_SIM_SETTING,
                Settings.System.DEFAULT_SIM_NOT_SET);
        
        if (defaultSim != Settings.System.VOICE_CALL_SIM_SETTING_INTERNET) {
            if (defaultSim == Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK) {
                List<SimInfoRecord> sims = SimInfoManager.getInsertedSimInfoList(this);
                if (sims != null && sims.size() == 1) {
                    Settings.System.putLong(getContentResolver(), 
                            Settings.System.VOICE_CALL_SIM_SETTING,
                            sims.get(0).mSimInfoId);
                }
            }
            //do nothing
            return ;
        } else { //default sim is internet call and now internet call is disable
            List<SimInfoRecord> sims = SimInfoManager.getInsertedSimInfoList(this);
            if (sims == null || sims.size() == 0) {
                Settings.System.putLong(getContentResolver(), 
                        Settings.System.VOICE_CALL_SIM_SETTING,
                        Settings.System.DEFAULT_SIM_NOT_SET);
            } else if (sims.size() == 1) {
                Settings.System.putLong(getContentResolver(), 
                        Settings.System.VOICE_CALL_SIM_SETTING,
                        sims.get(0).mSimInfoId);
            } else {
                if (sims.get(0).mSimSlotId == 0) {
                    Settings.System.putLong(getContentResolver(), 
                            Settings.System.VOICE_CALL_SIM_SETTING,
                            sims.get(0).mSimInfoId);
                } else {
                    Settings.System.putLong(getContentResolver(), 
                            Settings.System.VOICE_CALL_SIM_SETTING,
                            sims.get(1).mSimInfoId);
                }
            }
        }
    }

    private synchronized void handleSipReceiveCallsOption(boolean enabled) {
        boolean isReceiveCall = mSipSharedPreferences.getReceivingCallsEnabled();
        if ((enabled && !isReceiveCall) || (!enabled && !isReceiveCall)) {
            return ;
        }
        List<SipProfile> sipProfileList = mProfileDb.retrieveSipProfileList();
        for (SipProfile p : sipProfileList) {
            String sipUri = p.getUriString();
            p = updateAutoRegistrationFlag(p, enabled);
            try {
                if (enabled) {
                    mSipManager.open(p,
                            SipUtil.createIncomingCallPendingIntent(), null);
                } else {
                    mSipManager.close(sipUri);
                    if (mSipSharedPreferences.isPrimaryAccount(sipUri)) {
                        // re-open in order to make calls
                        mSipManager.open(p);
                    }
                }
            } catch (SipException e) {
                PhoneLog.e(TAG, "register failed", e);
            }
        }
    }

    private SipProfile updateAutoRegistrationFlag(
            SipProfile p, boolean enabled) {
        SipProfile newProfile = new SipProfile.Builder(p)
                .setAutoRegistration(enabled)
                .build();
        try {
            mProfileDb.deleteProfile(p);
            mProfileDb.saveProfile(newProfile);
        } catch (IOException e) {
            PhoneLog.e(TAG, "updateAutoRegistrationFlag error", e);
        }
        return newProfile;
    }
    
    private void handleSipCallOptionsChange(Object objValue) {
        String option = objValue.toString();
        mSipSharedPreferences.setSipCallOption(option);
        mListSipCallOptions.setValueIndex(
                mListSipCallOptions.findIndexOfValue(option));
        mListSipCallOptions.setSummary(mListSipCallOptions.getEntry());
    }
}
