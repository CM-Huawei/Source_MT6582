/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.systemui.statusbar.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.R;
import com.google.android.collect.Lists;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.systemui.statusbar.util.SIMHelper;
import com.mediatek.xlog.Xlog;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class LteDcController {

    private static final String TAG = "LteDcController";
    private static boolean DEBUG = true;
    private static final boolean IS_LTEDC_SUPPORT = SIMHelper.isMediatekLteDcSupport();
    private static final int MAX_SIM_NUM = PhoneConstants.GEMINI_SIM_NUM;
    protected Object mSSTag[] = new Object[MAX_SIM_NUM];
    protected Object mNNTag[] = new Object[MAX_SIM_NUM];
    protected Object mTTTag[] = new Object[MAX_SIM_NUM];

    private static final String LTEDC_SPN_STRING_ACTION 
        = TelephonyIntents.SPN_STRINGS_UPDATED_ACTION;
    private static final String LTEDC_SERVICE_STATE_ACTION 
        = TelephonyIntents.ACTION_SERVICE_STATE_CHANGED;
    private static final String LTEDC_SIGANL_STRENGTH_ACTION 
        = TelephonyIntents.ACTION_SIGNAL_STRENGTH_CHANGED;

    private static LteDcController sInstance;
    private final Context mContext;

    ///private static ITelephonyRegistry mRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService("telephony.registry"));

    private ServiceState mServiceState;
    private SignalStrength mSignalStrength;
    private Intent spnIntent;

    private ArrayList<LtdDcStateChangeCallback> mCallbacks =
            new ArrayList<LtdDcStateChangeCallback>();

    public static final int INVALID_SLOT_ID = -1;

    public int getLteDcEnabledSlotId() {
        if (IS_LTEDC_SUPPORT) {
            if (hasService()) {
                return getSlotId();
            }
        }
        return INVALID_SLOT_ID;
    }

    public static LteDcController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LteDcController(context);
        }
        return sInstance;
    }

    public void registerCallback(LtdDcStateChangeCallback callback) {
        if (IS_LTEDC_SUPPORT) {
            Xlog.v(TAG, "*** register callback for " + callback);
            if (callback != null) {
                mCallbacks.add(callback);
            }
        } else {
            Xlog.e(TAG, "isMediatekLteDcSupport = false");
        }
    }

    public void setSignalStrengthTag(final Object tag, int slotId) {
        if (isValidSlotId(slotId)) {
            mSSTag[slotId] = tag;
        }
    }

    public Object getSignalStrengthTag(int slotId) {
        if (isValidSlotId(slotId)) {
            return mSSTag[slotId];
        } else {
            return null;
        }
    }

    public void setServiceStateTag(final Object tag, int slotId) {
        if (isValidSlotId(slotId)) {
            mTTTag[slotId] = tag;
        }
    }

    public Object getServiceStateTag(int slotId) {
        if (isValidSlotId(slotId)) {
            return mTTTag[slotId];
        } else {
            return null;
        }
    }

    public void setNetworkNameTag(final Object tag, int slotId) {
        if (isValidSlotId(slotId)) {
            mNNTag[slotId] = tag;
        }
    }

    public Object getNetworkNameTag(int slotId) {
        if (isValidSlotId(slotId)) {
            return mNNTag[slotId];
        } else {
            return null;
        }
    }

    private LteDcController(Context context) {
        mContext = context;

        if (IS_LTEDC_SUPPORT) {
            // Watch for interesting updates
            final IntentFilter filter = new IntentFilter();
            filter.addAction(LTEDC_SPN_STRING_ACTION);
            filter.addAction(LTEDC_SERVICE_STATE_ACTION);
            filter.addAction(LTEDC_SIGANL_STRENGTH_ACTION);
            context.registerReceiver(mBroadcastReceiver, filter);
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Xlog.d(TAG, "received broadcast " + action);
            if (LTEDC_SPN_STRING_ACTION.equals(action)) {
                if (hasService()) {
                    for (LtdDcStateChangeCallback cb : mCallbacks) {
                        if (cb != null) {
                            cb.onNetworkNameChanged(intent);
                        }
                    }
                }
            } else if (LTEDC_SERVICE_STATE_ACTION.equals(action)) {
                boolean isOldHasService = hasService();
                mServiceState = ServiceState.newFromBundle(intent.getExtras());
                for (LtdDcStateChangeCallback cb : mCallbacks) {
                    if (cb != null) {
                        cb.onServiceStateChanged(mServiceState);
                    }
                }
            } else if (LTEDC_SIGANL_STRENGTH_ACTION.equals(action)) {
                if (hasService()) {
                    mSignalStrength = SignalStrength.newFromBundle(intent.getExtras());
                    for (LtdDcStateChangeCallback cb : mCallbacks) {
                        if (cb != null) {
                            cb.onSignalStrengthChanged(mSignalStrength);
                        }
                    }
                }
            }
        }
    };

    private boolean isValidSlotId(int slotId) {
        if (0 <= slotId && slotId < MAX_SIM_NUM) {
            return true;
        } else {
            return false;
        }
    }

    private int getSlotId() {
        if (mServiceState != null) {
            return mServiceState.getMySimId();
        } else {
            return INVALID_SLOT_ID;
        }
    }

    private boolean hasService() {
        return SIMHelper.hasService(mServiceState);
    }
}
