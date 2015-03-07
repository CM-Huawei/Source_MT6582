/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.R;

import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.systemui.statusbar.util.SIMHelper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.xlog.Xlog;

public class CsgIconView extends ImageView {
    private static final String TAG = "CsgIconView";
    private static final int[] ICON_ID = {
        R.drawable.stat_sys_csg_blue,
        R.drawable.stat_sys_csg_orange,
        R.drawable.stat_sys_csg_green,
        R.drawable.stat_sys_csg_purple,
        R.drawable.stat_sys_csg_white
    };
    private int mSlotId = 0;

    public CsgIconView(Context context) {
        this(context, null);
    }

    public CsgIconView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CsgIconView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerReceivers();
        showIndication(false);
    }
        
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregisterReceivers();
    }

    private void registerReceivers() {
        Context context = getContext();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);

        context.registerReceiver(mBroadcastReceiver, filter);
    }

    private void unregisterReceivers() {
        Context context = getContext();
        context.unregisterReceiver(mBroadcastReceiver);
    }

    private void setSlotId(int slotId) {
         this.mSlotId = slotId;
    }

    private int current3GSlotId() {
        int slot3G = -1;
        try {
            ITelephonyEx mTelephonyEx = SIMHelper.getITelephonyEx();
            if (mTelephonyEx != null) {
                slot3G = mTelephonyEx.get3GCapabilitySIM();
                Xlog.d(TAG, "current3GSlotId = " + slot3G);
            }
        } catch (RemoteException e) {
            Xlog.e(TAG, "current3GSlotId mTelephonyEx exception");
        }
        return slot3G;
    }

    private int getOptrColorIdBySlot() {
        if (this.mSlotId >= 0) {
            SimInfoManager.SimInfoRecord info = SimInfoManager.getSimInfoBySlot(mContext, this.mSlotId);
            if (null == info) {
                Xlog.d(TAG, "getOptrColorIdBySlot, return null");
            } else {
                return info.mColor;
            }
        }
        return -1;
    }

    private void updateResource() {
        if (this.getVisibility() == View.VISIBLE) {
            final int simColorId = getOptrColorIdBySlot();
            if (simColorId > -1 && simColorId < 4) {
                this.setBackgroundResource(ICON_ID[simColorId]);
            } else {
                Xlog.d(TAG, "apply, incorrect sim color id");
            }
        }
    }

    private void showIndication(boolean mShow) {
        Xlog.d(TAG, "showIndication, mShow = " + mShow);

        if (mShow) {
            this.setVisibility(View.VISIBLE);
            updateResource();
        } else {
            this.setVisibility(View.GONE);
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Xlog.d(TAG, "onReceive, action=" + action);
            if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                updateResource();
            } else if (TelephonyIntents.ACTION_SIM_INFO_UPDATE.equals(action)) {
                updateResource();
            } else if (TelephonyIntents.SPN_STRINGS_UPDATED_ACTION.equals(action)) {
                final int slotId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, 0);
                if (slotId == current3GSlotId()) {
                    /// We only have one 3G/4G slot now.
                    setSlotId(slotId);
                    showIndication((SIMHelper.getCsgInfo(intent) != null));
                }
            }
            Xlog.d(TAG, "onReceive, end");
        }
    };
}
