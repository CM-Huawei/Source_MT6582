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

package com.mediatek.settings;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;

import static com.android.phone.TimeConsumingPreferenceActivity.EXCEPTION_ERROR;
import static com.android.phone.TimeConsumingPreferenceActivity.RESPONSE_ERROR;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.phone.PhoneGlobals;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.TimeConsumingPreferenceListener;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.phone.wrapper.TelephonyManagerWrapper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.ArrayList;

public class CellBroadcastCheckBox extends CheckBoxPreference {
    private static final String LOG_TAG = "Settings/CellBroadcastCheckBox";
    private static final boolean DBG = true; //(PhoneGlobals.DBG_LEVEL >= 2);
    private static final int MESSAGE_GET_STATE = 100;
    private static final int MESSAGE_SET_STATE = 101;

    private static final String CB_SLOT1_TO_SIMID = "cellbroadcast_slot1_map_soltid";
    private static final String CB_SLOT2_TO_SIMID = "cellbroadcast_slot2_map_soltid";
    private static final String CB_SLOT3_TO_SIMID = "cellbroadcast_slot3_map_soltid";
    private static final String CB_SLOT4_TO_SIMID = "cellbroadcast_slot4_map_soltid";

    private static final String[] mSimIds = {
        CB_SLOT1_TO_SIMID,
        CB_SLOT2_TO_SIMID,
        CB_SLOT3_TO_SIMID,
        CB_SLOT4_TO_SIMID
    };

    private TimeConsumingPreferenceListener mListener;
    private MyHandler mHandler = new MyHandler();
    private Phone mPhone;
    private boolean mLastCheckStatus;
    int mSlotId;

    public CellBroadcastCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPhone = PhoneGlobals.getPhone();
    }

    @Override
    protected void onClick() {
        super.onClick();
        boolean state = isChecked();
        mLastCheckStatus = !state;
        setCBState(state ? 0 : 1);
        setChecked(!state);
    }

    void init(TimeConsumingPreferenceListener listener, boolean skipReading, int slotId) {
        PhoneLog.d(LOG_TAG,"init, slotId = " + slotId);
        mListener = listener;
        mSlotId = slotId;

        if (!skipReading) {
            boolean hasIccCard;
            hasIccCard = TelephonyManagerWrapper.hasIccCard(mSlotId);
            PhoneLog.d(LOG_TAG, "hasIccCard = " + hasIccCard);
            if (hasIccCard) {
                /// M: for ALPS00907516 @{
                // when sim card changed, we should set checkbox false in order to
                // open some opened channels with current sim card.
                if (isSimChanged(slotId)) {
                    setChecked(false);
                    saveSimIdBySlot(slotId);
                    return;
                } else {
                    getCBState(true);
                }
                /// @}
            } else {
                setChecked(false);
                setEnabled(false);
            }
        }
    }

    private void getCBState(boolean reason) {
        Message msg;
        if (reason) {
            msg = mHandler.obtainMessage(MESSAGE_GET_STATE, 0,MESSAGE_GET_STATE, null);
        } else {
            msg = mHandler.obtainMessage(MESSAGE_GET_STATE, 0,MESSAGE_SET_STATE, null);
        }
        PhoneWrapper.queryCellBroadcastSmsActivation(mPhone, msg, mSlotId);

        if (reason) {
            if (mListener != null && msg.arg2 == MESSAGE_SET_STATE) {
                mListener.onStarted(CellBroadcastCheckBox.this, reason);
            }
        }
    }

    private void setCBState(int state) {
        Message msg;
        msg = mHandler.obtainMessage(MESSAGE_SET_STATE, 0, MESSAGE_SET_STATE,null);
        PhoneWrapper.activateCellBroadcastSms(mPhone, state, msg, mSlotId);

        if (mListener != null) {
            mListener.onStarted(CellBroadcastCheckBox.this, false);
        }
    }

    /**
     * save simId .firstly, get simId that base on slotId, then save it
     * @param slotId
     */
    private void saveSimIdBySlot(int slotId) {
        SimInfoRecord simInfoRecord = SimInfoManager.getSimInfoBySlot(getContext(), slotId);
        if (simInfoRecord == null) {
            PhoneLog.i(LOG_TAG, "[saveSimIdBySlot] simInfoRecord ==null with slotId=" + slotId);
            return;
        }
        long simId = simInfoRecord.mSimInfoId;
        SharedPreferences.Editor editor = getEditor();
        if (editor != null) {
            editor.putLong(mSimIds[slotId], simId);
            editor.commit();
        }
    }

    /**
     * whether the simId with the same slotId is changed or not.
     * @param slotId
     * @return
     */
    private boolean isSimChanged(int slotId) {
        SimInfoRecord simInfoRecord = SimInfoManager.getSimInfoBySlot(getContext(), slotId);
        if (simInfoRecord == null) {
            PhoneLog.i(LOG_TAG, "[isSimChanged] simInfoRecord ==null with slotId=" + slotId);
            return false;
        }

        long currentSimId = simInfoRecord.mSimInfoId;
        // if the simId with index of slotId don't be saved, return -1.
        long originalSimId = getSharedPreferences().getLong(mSimIds[slotId], GeminiUtils.UNDEFINED_SIM_ID);
        PhoneLog.d(LOG_TAG, "currentSimId: " + currentSimId + " originalSimId: " + originalSimId);
        return currentSimId != originalSimId;
    }

    private class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_GET_STATE:
                handleGetStateResponse(msg);
                break;
            case MESSAGE_SET_STATE:
                handleSetStateResponse(msg);
                break;
            default:
                break;
            }
        }

        private void handleGetStateResponse(Message msg) {
            if (msg.arg2 == MESSAGE_GET_STATE) {
                if (mListener != null) {
                    /// Open this listener for ALPS00848484, which had been closed for
                    //  resolving issue ALPS00131155. This will removeDialog(BUSY_SAVING_DIALOG).
                    mListener.onFinished(CellBroadcastCheckBox.this, true);
                    if (DBG) {
                        PhoneLog.d(LOG_TAG, "For init query, there's no reading dialog!");
                    }
                }
            } else {
                if (mListener != null) {
                    mListener.onFinished(CellBroadcastCheckBox.this, false);
                    if (!mLastCheckStatus) {
                        RecoverChannelSettings setting = 
                            new RecoverChannelSettings(mSlotId, getContext().getContentResolver());
                        setting.updateChannelStatus();
                    }
                }
            }
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar == null) {
                PhoneLog.i(LOG_TAG, "handleGetStateResponse,ar is null");
                if (msg.arg2 == MESSAGE_GET_STATE) {
                    CellBroadcastCheckBox.this.setChecked(false);
                    CellBroadcastCheckBox.this.setEnabled(false);
                } else {
                    if (mListener != null) {
                        mListener.onError(CellBroadcastCheckBox.this,EXCEPTION_ERROR);
                    }
                }
                return;
            }
            if (ar.exception != null) {
                if (DBG) {
                    PhoneLog.d(LOG_TAG, "handleGetStateResponse: ar.exception=" + ar.exception);
                }
                if (msg.arg2 == MESSAGE_GET_STATE) {
                    CellBroadcastCheckBox.this.setChecked(false);
                    CellBroadcastCheckBox.this.setEnabled(false);
                } else {
                      if (mListener != null) {
                          mListener.onError(CellBroadcastCheckBox.this,EXCEPTION_ERROR);
                      }
                }
                return;
            } else {
                if (ar.userObj instanceof Throwable) {
                    if (msg.arg2 == MESSAGE_GET_STATE) {
                        CellBroadcastCheckBox.this.setChecked(false);
                        CellBroadcastCheckBox.this.setEnabled(false);
                    } else {
                        if (mListener != null) {
                            mListener.onError(CellBroadcastCheckBox.this,RESPONSE_ERROR);
                        }
                    }
                    return;
                } else {
                    if (ar.result != null) {
                        Boolean state = (Boolean) ar.result;
                        CellBroadcastCheckBox.this.setChecked(state.booleanValue());
                    } else {
                        if (msg.arg2 == MESSAGE_GET_STATE) {
                            CellBroadcastCheckBox.this.setChecked(false);
                            CellBroadcastCheckBox.this.setEnabled(false);
                        } else {
                            if (mListener != null) {
                                mListener.onError(CellBroadcastCheckBox.this,RESPONSE_ERROR);
                            }
                        }
                        return;
                    }
                }
            }
        }

        private void handleSetStateResponse(Message msg) {
            if (msg.arg2 == MESSAGE_SET_STATE) {
                AsyncResult ar = (AsyncResult) msg.obj;
                if (mListener != null) {
                    mListener.onFinished(CellBroadcastCheckBox.this, false);
                }
                if (ar == null) {
                    PhoneLog.i(LOG_TAG, "handleSetStateResponse,ar is null");
                    mListener.onError(CellBroadcastCheckBox.this,EXCEPTION_ERROR);
                    return;
                }
                if (ar.exception != null) {
                    if (DBG) {
                        PhoneLog.d(LOG_TAG, "handleSetStateResponse: ar.exception=" + ar.exception);
                    }
                    if (mListener != null) {
                        mListener.onError(CellBroadcastCheckBox.this,EXCEPTION_ERROR);
                    }
                } else {
                        PhoneLog.i(LOG_TAG, "handleSetStateResponse: re get ok");
                        getCBState(false);
                }
            }
        }
    }
}

class RecoverChannelSettings extends Handler {

    private static final int MESSAGE_SET_CONFIG = 101;
    private static final String LOG_TAG = "RecoverChannelSettings";
    private static final String KEYID = "_id";
    private static final String NAME = "name";
    private static final String NUMBER = "number";
    private static final String ENABLE = "enable";
    private static final Uri CHANNEL_URI = Uri.parse("content://cb/channel");
    private static final Uri CHANNEL_URI1 = Uri.parse("content://cb/channel1");
    ///M: add for Gemini+
    private static final Uri CHANNEL_URI2 = Uri.parse("content://cb/channel2");
    private static final Uri CHANNEL_URI3 = Uri.parse("content://cb/channel3");
    
    private Uri mUri = CHANNEL_URI;
    private int mSimId;    
    Phone mPhone = null;
    private ContentResolver mResolver = null;
    
    public RecoverChannelSettings(int simId, ContentResolver resolver) {
        mSimId = simId;
        mPhone = PhoneGlobals.getPhone();
        this.mResolver = resolver;
        
        if (GeminiUtils.isGeminiSupport()) {
            switch (mSimId) {
                case PhoneConstants.GEMINI_SIM_1:
                mUri = CHANNEL_URI;
                break;
                case PhoneConstants.GEMINI_SIM_2:
                mUri = CHANNEL_URI1;
                break;
                case PhoneConstants.GEMINI_SIM_3:
                mUri = CHANNEL_URI2;
                break;
                case PhoneConstants.GEMINI_SIM_4:
                mUri = CHANNEL_URI3;
                break;
                default:
                PhoneLog.d(LOG_TAG,"error with simid = " + mSimId);
                break;
            }
        }
    }

    private ArrayList<CellBroadcastChannel> mChannelArray = new ArrayList<CellBroadcastChannel>();

    private boolean updateChannelToDatabase(int index) {
        final CellBroadcastChannel channel = mChannelArray.get(index);
        final int id = channel.getKeyId();
        final String name = channel.getChannelName();
        final boolean enable = false;
        final int number = channel.getChannelId();
        ContentValues values = new ContentValues();
        values.put(KEYID, id);
        values.put(NAME, name);
        values.put(NUMBER, number);
        values.put(ENABLE, Integer.valueOf(enable ? 1 : 0));
        String where = KEYID + "=" + channel.getKeyId();
        final int rows = mResolver.update(mUri, values,where, null);
        return rows > 0;
    }

    boolean queryChannelFromDatabase() {
        String[] projection = new String[] { KEYID, NAME, NUMBER, ENABLE };
        Cursor cursor = null;
        try {
            cursor = mResolver.query(mUri,projection, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    CellBroadcastChannel channel = new CellBroadcastChannel();
                    channel.setChannelId(cursor.getInt(2));
                    channel.setKeyId(cursor.getInt(0));// keyid for delete or edit
                    channel.setChannelName(cursor.getString(1));
                    channel.setChannelState(cursor.getInt(3) == 1);
                    mChannelArray.add(channel);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return true;
    }

    /**
     * when enable channels, we set once that these channels which are enable
     * and channelId neighboring in the DB to reduce times to reduce API.
     * eg: the channel id maybe is 1(true),2(true),3(false) ,4(true), 5(false),6(true)
     * we send three times (1,2; 4; 6)
      */
    public void updateChannelStatus() {
        if (!queryChannelFromDatabase()) {
            PhoneLog.d(LOG_TAG, "queryChannelFromDatabase failure!");
            return ;
        }
        int length = mChannelArray.size();
        PhoneLog.d(LOG_TAG, "updateChannelStatus length: " + length);
        SmsBroadcastConfigInfo infoList = null;
        int channelId = -1;
        boolean channelState;
        for (int i = 0; i < length; i++) {
            channelId = mChannelArray.get(i).getChannelId();
            channelState = mChannelArray.get(i).getChannelState();
            if (channelState) {
                if (infoList == null) {
                    infoList = new SmsBroadcastConfigInfo(channelId, channelId, -1, -1, true);
                } else if (infoList.getToServiceId() != (channelId - 1)) {
                    SmsBroadcastConfigInfo[] info = new SmsBroadcastConfigInfo[1];
                    info[0] = infoList;
                    setCellBroadcastConfig(info, infoList.getFromServiceId(),infoList.getToServiceId());
                    infoList = new SmsBroadcastConfigInfo(channelId, channelId, -1, -1, true);
                } else {
                    infoList.setToServiceId(channelId);
                }
            }
        }
        if (infoList != null) {
            PhoneLog.d(LOG_TAG, "updateChannelStatus last times");
            SmsBroadcastConfigInfo[] info = new SmsBroadcastConfigInfo[1];
            info[0] = infoList;
            setCellBroadcastConfig(info, infoList.getFromServiceId(), infoList.getToServiceId());
        }
    }

    private void setCellBroadcastConfig(SmsBroadcastConfigInfo[] objectList, int fromId, int toId) {
        Message msg = obtainMessage(MESSAGE_SET_CONFIG, fromId, toId, null);
        PhoneWrapper.setCellBroadcastSmsConfig(mPhone, objectList, objectList, msg, mSimId);
    }

    public void handleMessage(Message msg) {
        switch (msg.what) {
        case MESSAGE_SET_CONFIG:
            handleSetCellBroadcastConfigResponse(msg);
            break;
        default:
            break;
        }
    }

    private void handleSetCellBroadcastConfigResponse(Message msg) {
        //if (msg.arg2 == MESSAGE_SET_CONFIG) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar == null) {
                PhoneLog.i(LOG_TAG,"handleSetCellBroadcastConfigResponse,ar is null");
                //onError(mLanguagePreference, RESPONSE_ERROR);
            }
            if (ar.exception != null) {
                PhoneLog.d(LOG_TAG,"handleSetCellBroadcastConfigResponse: ar.exception=" + ar.exception);
                int fromIndex = -1;
                int toIndex = -1;
                int length = mChannelArray.size();
                int channelId;
                boolean channelState;
                // find the exception team begin channel id index and last channel id index
                for (int i = 0; i < length; i++) {
                    channelId = mChannelArray.get(i).getChannelId();
                    if (channelId == msg.arg2) {
                        toIndex = i;
                    }
                    if (channelId == msg.arg1) {
                        fromIndex = i;
                    }
                }
                if (fromIndex == -1 || toIndex == -1) {
                    return;
                }
                for (int i = toIndex; i >= fromIndex; i--) {
                    this.updateChannelToDatabase(i);
                }
            }
    }
}
