package com.mediatek.gemini;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;

import com.android.internal.telephony.ITelephony;
import com.android.settings.R;
import com.android.settings.Utils;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.gemini.simui.SimCardInfoPreference;
import com.mediatek.gemini.simui.SimInfoViewUtil.WidgetType;
import com.mediatek.settings.ext.ISimRoamingExt;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.xlog.Xlog;

public class SimDataRoamingSettings extends SimInfoPrefFragment implements OnClickListener {

    private static final String TAG = "SimDataRoamingSettings";

    private ITelephony mTelephony;
    private TelephonyManagerEx mTelephonyManagerEx;
    private int mCurrentSimSlot;
    private long mCurrentSimID;
    private ISimRoamingExt mExt;
    private SimCardInfoPreference mSimInfoPref;
    private static final int DLG_ROAMING_WARNING = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // set the custom Widget.
        setWidgetViewType(WidgetType.CheckBox);
        mTelephony = ITelephony.Stub.asInterface(ServiceManager
                .getService("phone"));
        mTelephonyManagerEx = TelephonyManagerEx.getDefault();

        mExt = Utils.getSimRoamingExtPlugin(this.getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        // set the clickable of checkbox as false.
        updateSimItemStatus();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        mSimInfoPref = (SimCardInfoPreference) preference;
        long simID = mSimInfoPref.getSimInfoId();
        SimInfoRecord simInfo = SimInfoManager.getSimInfoById(getActivity(), simID);
        if (simInfo != null) {
            int dataRoaming;
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                dataRoaming = simInfo.mDataRoaming;
            } else {
                if (getDataRoamingState()) {
                    dataRoaming = SimInfoManager.DATA_ROAMING_ENABLE;
                } else {
                    dataRoaming = SimInfoManager.DATA_ROAMING_DISABLE;
                }
            }
            mCurrentSimSlot = simInfo.mSimSlotId;
            mCurrentSimID = simInfo.mSimInfoId;
            if (dataRoaming == SimInfoManager.DATA_ROAMING_DISABLE) {
                showDialog(DLG_ROAMING_WARNING);
            } else {
                setDataRoaming(false);
                mSimInfoPref.setChecked(false);
            }
            return true;
        }
        return false;
    }

    
    @Override
    public Dialog onCreateDialog(int dialogId) {
        if (DLG_ROAMING_WARNING == dialogId) {
            Context context = getActivity();
            String msg = mExt.getRoamingWarningMsg(context,R.string.roaming_warning);
            Xlog.d(TAG, "msg=" + msg);
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(msg)
                   .setTitle(android.R.string.dialog_alert_title)
                   .setIcon(android.R.drawable.ic_dialog_alert)
                   .setPositiveButton(android.R.string.yes,this)
                   .setNegativeButton(android.R.string.no,this);
            return builder.create();
        }
        return super.onCreateDialog(dialogId);
    }

    /**
     * 
     * @return true data is roaming otherwise false
     */
    private boolean getDataRoamingState() {
        return Settings.Secure.getInt(getActivity().getContentResolver(), 
                                      Settings.Secure.DATA_ROAMING, 0) != 0;
    }
    private void setDataRoaming(boolean enable) {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            //M: solve CR ALPS00609944 when hot swap the target simId has been plug out so do nothing
            if (SimInfoManager.getSimInfoBySlot(getActivity(), mCurrentSimSlot) != null) {
                try {
                    if (mTelephonyManagerEx != null) {
                        mTelephonyManagerEx.setDataRoamingEnabled(enable,mCurrentSimSlot);
                    }
                } catch (RemoteException e) {
                    Xlog.e(TAG, "mTelephony exception");
                    return;
                }
                int roamingState;
                if (enable) {
                    roamingState = SimInfoManager.DATA_ROAMING_ENABLE;
                } else {
                    roamingState = SimInfoManager.DATA_ROAMING_DISABLE;
                }
                SimInfoManager.setDataRoaming(getActivity(),roamingState,mCurrentSimID);
            } else {
                Xlog.d(TAG,"sim slot " + mCurrentSimSlot + " has been plug out");
            }
        } else {
            Settings.Secure.putInt(getActivity().getContentResolver(), Settings.Secure.DATA_ROAMING, enable ? 1 : 0);
        }
    }
    
    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            setDataRoaming(true);
            mSimInfoPref.setChecked(true);
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            mSimInfoPref.setChecked(false);
        }
    }
    
    /**
     * update item enable status and checkable status.
     */
    private void updateSimItemStatus() {
        for (SimInfoRecord simInfo: mSimInfoList) {
            // update item checkable status.
            SimCardInfoPreference preference = (SimCardInfoPreference) getPreferenceBySlot(simInfo.mSimSlotId);
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                preference.setChecked(simInfo.mDataRoaming == SimInfoManager.DATA_ROAMING_ENABLE);
            } else {
                preference.setChecked(getDataRoamingState());
            }
        }
    }
    
    public void dealNoSimCardIn() {
        if (this.isResumed()) {
            Intent intent = new Intent(this.getActivity(), com.android.settings.Settings.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }
    }
    
}
