/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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

package com.android.phone;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.ext.SettingsExtension;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class GsmUmtsCallForwardOptions extends TimeConsumingPreferenceActivity {
    private static final String LOG_TAG = "Settings/GsmUmtsCallForwardOptions";
    private static final boolean DBG = true;//(PhoneApp.DBG_LEVEL >= 2);

    private static final String NUM_PROJECTION[] = {Phone.NUMBER};

    private static final String BUTTON_CFU_KEY   = "button_cfu_key";
    private static final String BUTTON_CFB_KEY   = "button_cfb_key";
    private static final String BUTTON_CFNRY_KEY = "button_cfnry_key";
    private static final String BUTTON_CFNRC_KEY = "button_cfnrc_key";

    private static final String KEY_TOGGLE = "toggle";
    private static final String KEY_STATUS = "status";
    private static final String KEY_NUMBER = "number";

    private CallForwardEditPreference mButtonCFU;
    private CallForwardEditPreference mButtonCFB;
    private CallForwardEditPreference mButtonCFNRy;
    private CallForwardEditPreference mButtonCFNRc;

    private final ArrayList<CallForwardEditPreference> mPreferences =
        new ArrayList<CallForwardEditPreference> ();
    private int mInitIndex= 0;

    private boolean mFirstResume;
    private Bundle mIcicle;
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mIcicle = icicle;
        ///M: for adjust setting UI on VXGA device. @{        
        mFragment = new CallForwardFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mFragment).commit();
        /// @}
    }
    ///M: for adjust setting UI on VXGA device.
    public static class CallForwardFragment extends PreferenceFragment {
         WeakReference<GsmUmtsCallForwardOptions> activityRef = null;
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.callforward_options);
            activityRef = new WeakReference<GsmUmtsCallForwardOptions>((GsmUmtsCallForwardOptions)getActivity());
            PreferenceScreen prefSet = getPreferenceScreen();
            activityRef.get().mButtonCFU   = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFU_KEY);
            activityRef.get().mButtonCFB   = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFB_KEY);
            activityRef.get().mButtonCFNRy = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFNRY_KEY);
            activityRef.get().mButtonCFNRc = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFNRC_KEY);

            activityRef.get().mButtonCFU.setParentActivity(getActivity(), activityRef.get().mButtonCFU.reason);
            activityRef.get().mButtonCFB.setParentActivity(getActivity(), activityRef.get().mButtonCFB.reason);
            activityRef.get().mButtonCFNRy.setParentActivity(getActivity(), activityRef.get().mButtonCFNRy.reason);
            activityRef.get().mButtonCFNRc.setParentActivity(getActivity(), activityRef.get().mButtonCFNRc.reason);

            activityRef.get().mPreferences.add(activityRef.get().mButtonCFU);
            activityRef.get().mPreferences.add(activityRef.get().mButtonCFB);
            activityRef.get().mPreferences.add(activityRef.get().mButtonCFNRy);
            activityRef.get().mPreferences.add(activityRef.get().mButtonCFNRc);

            // we wait to do the initialization until onResume so that the
            // TimeConsumingPreferenceActivity dialog can display as it
            // relies on onResume / onPause to maintain its foreground state.

            activityRef.get().mFirstResume = true;

            ActionBar actionBar = getActivity().getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }

            /// M: initialize MTK values
            activityRef.get().onCreateMtk();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ///M: when press home key & return back refresh the setting
        if (mFirstResume) {
            mInitIndex = 0;
            PhoneLog.d(LOG_TAG, "START INIT(onResume1): mInitIndex is  " + mInitIndex);
            mPreferences.get(mInitIndex).init(this, false, mSlotId);
            mFirstResume = false;
        } else if (PhoneUtils.getMmiFinished()) {
            mInitIndex = 0;
            PhoneLog.d(LOG_TAG, "START INIT(onResume2): mInitIndex is  " + mInitIndex);
            mPreferences.get(mInitIndex).init(this, false, mSlotId);
            PhoneUtils.setMmiFinished(false);
        } else {
            PhoneLog.d(LOG_TAG, "No change, so don't query!");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        for (CallForwardEditPreference pref : mPreferences) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(KEY_TOGGLE, pref.isToggled());
            bundle.putBoolean(KEY_ITEM_STATUS, pref.isEnabled());
            if (pref.mCallForwardInfo != null) {
                bundle.putString(KEY_NUMBER, pref.mCallForwardInfo.number);
                bundle.putInt(KEY_STATUS, pref.mCallForwardInfo.status);
            }
            outState.putParcelable(pref.getKey(), bundle);
        }
    }

    @Override
    public void onFinished(Preference preference, boolean reading) {
        ///M: when the CF get fail, disable them
        if (mInitIndex < mPreferences.size() - 1 && !isFinishing()) {
            if (mPreferences.get(mInitIndex++).isSuccess()) {
                PhoneLog.d(LOG_TAG, "START INIT(onFinished): mInitIndex is  " + mInitIndex);
                mPreferences.get(mInitIndex).init(this, false, mSlotId);
            } else {
                for (int i = mInitIndex; i < mPreferences.size(); ++i) {
                    mPreferences.get(i).setEnabled(false);
                }
                mInitIndex = mPreferences.size();
            }
        }

        super.onFinished(preference, reading);
        removeDialog();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        PhoneLog.d(LOG_TAG, "onActivityResult: done");

        if (resultCode != RESULT_OK) {
            PhoneLog.d(LOG_TAG, "onActivityResult: contact picker result not OK.");
            return;
        }

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(data.getData(),
                    NUM_PROJECTION, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                switch (requestCode) {
                case CommandsInterface.CF_REASON_UNCONDITIONAL:
                    if (mButtonCFU != null) {
                        mButtonCFU.onPickActivityResult(cursor.getString(0));
                    }
                       break;
                case CommandsInterface.CF_REASON_BUSY:
                    if (mButtonCFB != null) {
                        mButtonCFB.onPickActivityResult(cursor.getString(0));
                    }
                    break;
                case CommandsInterface.CF_REASON_NO_REPLY:
                    if (mButtonCFNRy != null) {
                        mButtonCFNRy.onPickActivityResult(cursor.getString(0));
                    }
                    break;
                case CommandsInterface.CF_REASON_NOT_REACHABLE:
                    if (mButtonCFNRc != null) {
                        mButtonCFNRc.onPickActivityResult(cursor.getString(0));
                    }
                    break;
                default:
                    break;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /* M: ActionBard Home @{
     * Original code:
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            CallFeaturesSetting.goUpToTopLevelSetting(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
     */

    // --------------------------- MTK ------------------------------------
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

    /// M: support GEIMINI & broadcast @{
    private static final String KEY_ITEM_STATUS = "item_status";
    private boolean mIsFinished = false;
    private boolean mIsVtSetting = false;
    private int mSlotId;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                if (intent.getBooleanExtra("state", false)) {
                    finish();
                }
            } else if (Intent.ACTION_DUAL_SIM_MODE_CHANGED.equals(action)) {
                if (intent.getIntExtra(Intent.EXTRA_DUAL_SIM_MODE, -1) == 0) {
                    finish();
                }
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                /// M: add for hot swap {
                GeminiUtils.handleSimHotSwap(GsmUmtsCallForwardOptions.this, mSlotId);
                /// @}
            }
        }
    };
    /// @}

    private void onCreateMtk() {
        restoreSavedStatus();
        PhoneUtils.setMmiFinished(false);
        initSlotId();
        initVTSetting();
        registerCallBacks();
    }

    /// M: to confirm CF preference the activity is destroy
    public void onDestroy() {
        super.onDestroy();

        if (mButtonCFU != null) {
            mButtonCFU.setStatus(true);
        }
        if (mButtonCFB != null) {
            mButtonCFB.setStatus(true);
        }
        if (mButtonCFNRy != null) {
            mButtonCFNRy.setStatus(true);
        }
        if (mButtonCFNRc != null) {
            mButtonCFNRc.setStatus(true);
        }
        unregisterReceiver(mIntentReceiver);
    }

    private void restoreSavedStatus() {
        if (null != mIcicle) {
            for (CallForwardEditPreference pref : mPreferences) {
                if (null != pref) {
                    Bundle bundle = mIcicle.getParcelable(pref.getKey());
                    if (null != bundle) {
                        pref.setToggled(bundle.getBoolean(KEY_TOGGLE));
                    }
                }
            }
        }
    }

    private void registerCallBacks() {
        IntentFilter intentFilter = new IntentFilter(
                Intent.ACTION_AIRPLANE_MODE_CHANGED);
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            intentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
        }
        ///M: add for hot swap {
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        ///@}
        registerReceiver(mIntentReceiver, intentFilter);
    }

    /// M: Refresh the settings when disable CFU
    public void refreshSettings(boolean bNeed) {
        if (bNeed) {
            mInitIndex = 1;
            PhoneLog.d(LOG_TAG, "START INIT(refreshSettings): mInitIndex is  " + mInitIndex);
            mPreferences.get(mInitIndex).init(this, false, mSlotId);
        }
    }

    private void initVTSetting() {
        mIsVtSetting = getIntent().getBooleanExtra("ISVT", false);
        PhoneLog.d(LOG_TAG, "[GsmUmtsCallForwardOptions]ISVT = " + mIsVtSetting);
        if (mIsVtSetting) {
            mButtonCFU.setServiceClass(CommandsInterface.SERVICE_CLASS_VIDEO);
            mButtonCFB.setServiceClass(CommandsInterface.SERVICE_CLASS_VIDEO);
            mButtonCFNRy.setServiceClass(CommandsInterface.SERVICE_CLASS_VIDEO);
            mButtonCFNRc.setServiceClass(CommandsInterface.SERVICE_CLASS_VIDEO);
        }
    }

    private void initSlotId() {
        if (GeminiUtils.isGeminiSupport()) {
            mSlotId = getIntent().getIntExtra(GeminiConstants.SLOT_ID_KEY, GeminiUtils.UNDEFINED_SLOT_ID);
            PhoneLog.d(LOG_TAG,"[mSlotId = " + mSlotId + "]");
            SimInfoRecord siminfo = SimInfoManager.getSimInfoBySlot(this, mSlotId);
            if (siminfo != null) {
                setTitle(siminfo.mDisplayName);
            }
        }

        if (!GeminiUtils.isSimInService(mSlotId)) {
            /// M: CT SIM to SIM/UIM. @{
            SettingsExtension ext = ExtensionManager.getInstance().getSettingsExtension();
            String msgText = ext.replaceSimBySlot(getString(R.string.net_or_simcard_busy), mSlotId);
            Toast.makeText(this, msgText, Toast.LENGTH_SHORT).show();
            finish();
            /// @}
        }
    }
}
