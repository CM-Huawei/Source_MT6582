
package com.android.bluetooth.map;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.widget.SimpleAdapter;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.internal.R;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.common.telephony.ITelephonyEx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BluetoothMapSimManager {

    private static final String TAG = "BluetoothMapSimManager";

    private Context mContext;

    private int mSimCount;

    public final static int INVALID_SIMID = 0;

    private List<SimInfoManager.SimInfoRecord> mSimInfoList;

    private static ITelephonyEx sTelephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService("phoneEx"));

    public void init(Context context) {
        mContext = context;
        mSimInfoList = SimInfoManager.getInsertedSimInfoList(mContext);
        mSimCount = mSimInfoList.isEmpty() ? 0 : mSimInfoList.size();

        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        mContext.registerReceiver(mSimReceiver, intentFilter);
    }

    public void unregisterReceiver() {
        mContext.unregisterReceiver(mSimReceiver);
    }

    private BroadcastReceiver mSimReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED)) {
                mSimInfoList = SimInfoManager.getInsertedSimInfoList(mContext);
                mSimCount = mSimInfoList.isEmpty() ? 0: mSimInfoList.size();
            }
        }
    };

    public int getSimCount() {
        return mSimCount;
    }

    public long getSingleSimId() {
        return mSimInfoList.get(0).mSimInfoId;
    }

    public List<SimInfoManager.SimInfoRecord> getSimList() {
        return mSimInfoList;
    }

    public long getSimIdFromOriginator(String origNumber) {
        if (mSimCount < 2) {
            return 0;
        } else {
            for (int i = 0; i < mSimInfoList.size(); i++) {
                if (PhoneNumberUtils.compareLoosely(mSimInfoList.get(i).mNumber, origNumber)) {
                    return mSimInfoList.get(i).mSimInfoId;
                }
            }
            return INVALID_SIMID;
        }
    }

    public static int getSoltBySimId(Context ctx, long simId) {
        SimInfoManager.SimInfoRecord siminfo = SimInfoManager.getSimInfoById(ctx, simId);
        if (siminfo == null) {
            return SimInfoManager.SLOT_NONE;
        } else {
            return siminfo.mSimSlotId;
        }
    }

    public int getSimStatusResource(int state) {
        switch (state) {
            /** 1, RADIOOFF : has SIM/USIM inserted but not in use . */
            case PhoneConstants.SIM_INDICATOR_RADIOOFF:
                return R.drawable.sim_radio_off;

            /** 2, LOCKED : has SIM/USIM inserted and the SIM/USIM has been locked. */
            case PhoneConstants.SIM_INDICATOR_LOCKED:
                return R.drawable.sim_locked;

            /** 3, INVALID : has SIM/USIM inserted and not be locked but failed to register to the network. */
            case PhoneConstants.SIM_INDICATOR_INVALID:
                return R.drawable.sim_invalid;

            /** 4, SEARCHING : has SIM/USIM inserted and SIM/USIM state is Ready and is searching for network. */
            case PhoneConstants.SIM_INDICATOR_SEARCHING:
                return R.drawable.sim_searching;

            /** 6, ROAMING : has SIM/USIM inserted and in roaming service(has no data connection). */
            case PhoneConstants.SIM_INDICATOR_ROAMING:
                return R.drawable.sim_roaming;

            /** 7, CONNECTED : has SIM/USIM inserted and in normal service(not roaming) and data connected. */
            case PhoneConstants.SIM_INDICATOR_CONNECTED:
                return R.drawable.sim_connected;

            /** 8, ROAMINGCONNECTED = has SIM/USIM inserted and in roaming service(not roaming) and data connected.*/
            case PhoneConstants.SIM_INDICATOR_ROAMINGCONNECTED:
                return R.drawable.sim_roaming_connected;

            /** -1, UNKNOWN : invalid value */
            case PhoneConstants.SIM_INDICATOR_UNKNOWN:

            /** 0, ABSENT, no SIM/USIM card inserted for this phone */
            case PhoneConstants.SIM_INDICATOR_ABSENT:

            /** 5, NORMAL = has SIM/USIM inserted and in normal service(not roaming and has no data connection). */
            case PhoneConstants.SIM_INDICATOR_NORMAL:
            default:
                return PhoneConstants.SIM_INDICATOR_UNKNOWN;
        }
    }

    public int getSimStatus(int id) {
        int slotId = mSimInfoList.get(id).mSimSlotId;
        if (slotId != -1) {
            if (sTelephonyEx == null) {
                return PhoneConstants.SIM_INDICATOR_UNKNOWN;
            }
            try {
                return sTelephonyEx.getSimIndicatorState(slotId);
            } catch (RemoteException ex) {
                return PhoneConstants.SIM_INDICATOR_UNKNOWN;
            }
        }
        return -1;
    }
}
