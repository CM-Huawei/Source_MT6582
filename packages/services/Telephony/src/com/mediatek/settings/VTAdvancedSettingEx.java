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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.MenuItem;

import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.PhoneGlobals;
import com.android.phone.R;
import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.lang.ref.WeakReference;
import java.util.List;

public class VTAdvancedSettingEx extends android.app.Activity {

    private static final String BUTTON_VT_REPLACE_KEY     = "button_vt_replace_expand_key";
    private static final String BUTTON_VT_ENABLE_BACK_CAMERA_KEY     = "button_vt_enable_back_camera_key";
    private static final String BUTTON_VT_PEER_BIGGER_KEY     = "button_vt_peer_bigger_key";
    private static final String BUTTON_VT_MO_LOCAL_VIDEO_DISPLAY_KEY     = "button_vt_mo_local_video_display_key";
    private static final String BUTTON_VT_MT_LOCAL_VIDEO_DISPLAY_KEY     = "button_vt_mt_local_video_display_key";
    
    private static final String BUTTON_CALL_FWD_KEY    = "button_cf_expand_key";
    private static final String BUTTON_CALL_BAR_KEY    = "button_cb_expand_key";
    private static final String BUTTON_CALL_ADDITIONAL_KEY    = "button_more_expand_key";
    
    private static final String BUTTON_VT_PEER_REPLACE_KEY = "button_vt_replace_peer_expand_key";
    private static final String BUTTON_VT_ENABLE_PEER_REPLACE_KEY = "button_vt_enable_peer_replace_key";
    private static final String BUTTON_VT_AUTO_DROPBACK_KEY = "button_vt_auto_dropback_key";
    private static final String CHECKBOX_RING_ONLY_ONCE = "ring_only_once";
    private static final String BUTTON_VT_RINGTONE_KEY    = "button_vt_ringtone_key";
    private static final String SELECT_MY_PICTURE         = "2";
    
    private static final String SELECT_DEFAULT_PICTURE    = "0";
    
    private static final String SELECT_DEFAULT_PICTURE2    = "0";
    private static final String SELECT_MY_PICTURE2         = "1";

    /** The launch code when picking a photo and the raw data is returned */
    public static final int REQUESTCODE_PICTRUE_PICKED_WITH_DATA = 3021;
    public static final int REQUESTCODE_PICTRUE_CROP = 3022;
    public static final String ACTION_CROP = "com.android.camera.action.CROP";
    
    private Preference mButtonVTEnablebackCamer;
    private Preference mButtonVTReplace;
    private Preference mButtonVTPeerBigger;
    private Preference mButtonVTMoVideo;
    private Preference mButtonVTMtVideo;
    private Preference mButtonCallFwd;
    private Preference mButtonCallBar;
    private Preference mButtonCallAdditional;    
    private Preference mButtonRingOnlyOnce;
    
    private Preference mButtonVTPeerReplace;
    private Preference mButtonVTEnablePeerReplace;
    private Preference mButtonVTAutoDropBack;
    
    private Preference mTargetPreference;

    private List<SimInfoRecord> m3GSimList;

    // debug data
    private static final String LOG_TAG = "Settings/VTAdvancedSettingEx";
    private static final boolean DBG = true; // (PhoneApp.DBG_LEVEL >= 2);
    
    private PreCheckForRunning mPreCfr = null;
    
    private static void log(String msg) {
        PhoneLog.d(LOG_TAG, msg);
    }
    //M: add for hot swap {
    private IntentFilter mIntentFilter;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                int count = SimInfoManager.getInsertedSimCount(context);
                if (count <= 1) {
                    PhoneLog.d(LOG_TAG,"temp.size()=" + count + "Activity finished");
                    finish();
                } else {
                    m3GSimList = GeminiUtils.get3GSimCards(VTAdvancedSettingEx.this.getApplicationContext());
                    setScreenEnabled();
                }    
            }    
        }
    };
    ///@}
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;
     
    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        ///M: for adjust setting UI on VXGA device. @{
        mFragment = new VTAdvancedSettingExFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mFragment).commit();
        /// @}
    }
    ///M: for adjust setting UI on VXGA device.
    public static class VTAdvancedSettingExFragment extends PreferenceFragment{
        WeakReference<VTAdvancedSettingEx> activityRef = null;
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activityRef = new WeakReference<VTAdvancedSettingEx>(
                    (VTAdvancedSettingEx) getActivity());
            addPreferencesFromResource(R.xml.vt_advanced_setting_ex);

            activityRef.get().mPreCfr = new PreCheckForRunning(getActivity());

            activityRef.get().m3GSimList = GeminiUtils.get3GSimCards(PhoneGlobals.getInstance().getApplicationContext());

            activityRef.get().mButtonVTReplace = findPreference(BUTTON_VT_REPLACE_KEY);

            activityRef.get().mButtonVTEnablebackCamer = findPreference(BUTTON_VT_ENABLE_BACK_CAMERA_KEY);
            activityRef.get().mButtonVTPeerBigger = findPreference(BUTTON_VT_PEER_BIGGER_KEY);
            activityRef.get().mButtonVTMoVideo = findPreference(BUTTON_VT_MO_LOCAL_VIDEO_DISPLAY_KEY);
            activityRef.get().mButtonVTMtVideo = findPreference(BUTTON_VT_MT_LOCAL_VIDEO_DISPLAY_KEY);

            activityRef.get().mButtonCallAdditional = findPreference(BUTTON_CALL_ADDITIONAL_KEY);
            activityRef.get().mButtonCallFwd =  findPreference(BUTTON_CALL_FWD_KEY);
            activityRef.get().mButtonCallBar = findPreference(BUTTON_CALL_BAR_KEY);

            activityRef.get().mButtonVTPeerReplace = findPreference(BUTTON_VT_PEER_REPLACE_KEY);
            activityRef.get().mButtonVTEnablePeerReplace = findPreference(BUTTON_VT_ENABLE_PEER_REPLACE_KEY);
            activityRef.get().mButtonVTAutoDropBack = findPreference(BUTTON_VT_AUTO_DROPBACK_KEY);
            activityRef.get().mButtonRingOnlyOnce = findPreference(CHECKBOX_RING_ONLY_ONCE);
            PhoneLog.d("MyLog","FeatureOption.MTK_VT3G324M_SUPPORT=" + FeatureOption.MTK_VT3G324M_SUPPORT + ""
                    + "FeatureOption.MTK_PHONE_VT_VOICE_ANSWER=" + FeatureOption.MTK_PHONE_VT_VOICE_ANSWER);
            if (!(FeatureOption.MTK_VT3G324M_SUPPORT && FeatureOption.MTK_PHONE_VT_VOICE_ANSWER)) {
                getPreferenceScreen().removePreference(activityRef.get().mButtonRingOnlyOnce);
            } 
            ///M: add for hot swap {
            activityRef.get().mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
            getActivity().registerReceiver(activityRef.get().mReceiver, activityRef.get().mIntentFilter);
            ///@}
            ActionBar actionBar = getActivity().getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            Intent intent = new Intent(getActivity(), MultipleSimActivity.class);
            intent.putExtra(MultipleSimActivity.INIT_FEATURE_NAME, "VT");
            intent.putExtra(GeminiUtils.EXTRA_TITLE_NAME, preference.getTitleRes());
            intent.putExtra(GeminiUtils.EXTRA_3G_CARD_ONLY, true);
            intent.putExtra(MultipleSimActivity.INIT_BASE_KEY, preference.getKey() + "@");

            if (preference == activityRef.get().mButtonVTReplace) {
                intent.putExtra(MultipleSimActivity.LIST_TITLE, R.string.vt_local_video_rep);
                intent.putExtra(MultipleSimActivity.INIT_ARRAY, R.array.vt_replace_local_video_entries);
                if (activityRef.get().getKeyValue("button_vt_replace_expand_key") == null) {
                    activityRef.get().setKeyValue("button_vt_replace_expand_key", "0");
                }
                intent.putExtra(MultipleSimActivity.INTENT_KEY, "ListPreference");
                intent.putExtra(MultipleSimActivity.INIT_ARRAY_VALUE, R.array.vt_replace_local_video_values);
            } else if (preference == activityRef.get().mButtonVTMtVideo) {
                intent.putExtra(MultipleSimActivity.LIST_TITLE, R.string.vt_incoming_call);
                intent.putExtra(MultipleSimActivity.INIT_ARRAY, R.array.vt_mt_local_video_display_entries);
                if (activityRef.get().getKeyValue("button_vt_mt_local_video_display_key") == null) {
                    activityRef.get().setKeyValue("button_vt_mt_local_video_display_key", "0");
                }
                intent.putExtra(MultipleSimActivity.INIT_ARRAY_VALUE, R.array.vt_mt_local_video_display_values);
                intent.putExtra(MultipleSimActivity.INTENT_KEY, "ListPreference");
            } else if (preference == activityRef.get().mButtonVTPeerReplace) {
                intent.putExtra(MultipleSimActivity.LIST_TITLE, R.string.vt_peer_video_rep);
                intent.putExtra(MultipleSimActivity.INIT_ARRAY, R.array.vt_replace_local_video_entries2);
                if (activityRef.get().getKeyValue("button_vt_replace_peer_expand_key") == null) {
                    activityRef.get().setKeyValue("button_vt_replace_peer_expand_key", "0");
                }
                intent.putExtra(MultipleSimActivity.INIT_ARRAY_VALUE, R.array.vt_replace_local_video_values2);
                intent.putExtra(MultipleSimActivity.INTENT_KEY, "ListPreference");
            } else if (preference == activityRef.get().mButtonVTEnablebackCamer 
                    || preference == activityRef.get().mButtonVTPeerBigger
                    || preference == activityRef.get().mButtonVTMoVideo 
                    || preference == activityRef.get().mButtonVTEnablePeerReplace 
                    || preference == activityRef.get().mButtonVTAutoDropBack 
                    || preference == activityRef.get().mButtonRingOnlyOnce){
                intent.putExtra(MultipleSimActivity.INTENT_KEY, "CheckBoxPreference");
            }

            activityRef.get().mTargetPreference = preference;
            activityRef.get().startActivityForResult(intent, GeminiUtils.REQUEST_SIM_SELECT);
            return true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setScreenEnabled();
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

    private void setScreenEnabled() {
        boolean isEnable = m3GSimList.size() > 0;
        if ((mButtonVTReplace.isEnabled() && !isEnable) ||
                (!mButtonVTReplace.isEnabled() && isEnable)) {
            mButtonVTReplace.setEnabled(isEnable);
            mButtonVTEnablebackCamer.setEnabled(isEnable);
            mButtonVTPeerBigger.setEnabled(isEnable);
            mButtonVTMoVideo.setEnabled(isEnable);
            mButtonVTMtVideo.setEnabled(isEnable);
            mButtonCallAdditional.setEnabled(isEnable);
            mButtonCallFwd.setEnabled(isEnable);
            mButtonCallBar.setEnabled(isEnable);
            mButtonVTPeerReplace.setEnabled(isEnable);
            mButtonVTEnablePeerReplace.setEnabled(isEnable);
            mButtonVTAutoDropBack.setEnabled(isEnable); 
            mButtonRingOnlyOnce.setEnabled(isEnable);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        PhoneLog.d(LOG_TAG, "reqCode=" + requestCode + ",resCode=" + resultCode);
        int slotId = GeminiUtils.UNDEFINED_SLOT_ID;
        if (GeminiUtils.REQUEST_SIM_SELECT == requestCode) {
            if (RESULT_OK == resultCode) {
                slotId = data.getIntExtra(GeminiConstants.SLOT_ID_KEY, GeminiUtils.UNDEFINED_SLOT_ID);
                PhoneLog.d(LOG_TAG, "slotId = " + slotId);
                Intent intent = mTargetPreference.getIntent();
                intent.putExtra("ISVT", true);
                if (slotId != GeminiUtils.UNDEFINED_SLOT_ID) {
                    GeminiUtils.startActivity(slotId, mTargetPreference, mPreCfr);
                }
            }
        }
    }

    private String getKeyValue(String key) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        return sp.getString(key, null);
    }

    private void setKeyValue(String key, String value) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(key, value);
        editor.commit();
    }

    protected void onDestroy() {
        super.onDestroy();
        if (mPreCfr != null) {
            mPreCfr.deRegister();
        }
        ///M: add for hot swap{
        unregisterReceiver(mReceiver);
        ///@}
    }
}
