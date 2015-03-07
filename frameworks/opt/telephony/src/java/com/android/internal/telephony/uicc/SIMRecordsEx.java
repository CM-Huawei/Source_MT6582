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
/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.TextUtils;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.gsm.SimTlv;
import com.android.internal.telephony.gsm.SmsMessage;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

//MTK-START [mtk80601][111215][ALPS00093395]
import static android.Manifest.permission.READ_PHONE_STATE;
import android.app.ActivityManagerNative;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA_2;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY_2;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC_2;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_DEFAULT_NAME;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_DEFAULT_NAME_2;
import android.provider.Telephony.Sms.Intents;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Telephony.SIMInfo;
import android.provider.Telephony.SimInfo;
import android.content.ContentUris;
import android.content.ContentValues;
import android.provider.Settings;
import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.RIL;
import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.common.featureoption.FeatureOption;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.gemini.GeminiPhone;
import com.android.internal.telephony.PhoneFactory;
//MTK-END [mtk80601][111215][ALPS00093395]

import static android.provider.Telephony.Intents.ACTION_REMOVE_IDLE_TEXT;
import static android.provider.Telephony.Intents.ACTION_REMOVE_IDLE_TEXT_2;

import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.telephony.ITelephonyExt;
import com.mediatek.common.telephony.ISimInfoUpdate;
import com.android.internal.telephony.DefaultSIMSettings; 


/**
 * {@hide}
 */
public class SIMRecordsEx extends Handler implements IccConstants {
    protected static final String LOG_TAG = "SIMRecords";
    static public final String INTENT_KEY_SIM_COUNT = "simCount";
    private static final boolean CRASH_RIL = false;

    protected static final boolean DBG = true;

    private ITelephonyExt mTelephonyExt;

    private int mSimId;
    public String mIccId;
    
    private Phone mPhone;
    protected Context mContext;
    protected CommandsInterface mCi;
    protected IccFileHandler mFh;
    protected UiccCardApplication mParentApp;

    private int iccIdQueryState = -1; // -1: init, 0: query error, 1: query successful
    private boolean hasQueryIccId;

    // ***** Event Constants
    private static final int EVENT_RADIO_STATE_CHANGED = 1;
    private static final int EVENT_QUERY_ICCID_DONE = 2;
    
    // ***** Constructor
    public SIMRecordsEx(UiccCardApplication app, Context c, CommandsInterface ci) {
        mContext = c;
        mCi = ci;
        mFh = app.getIccFileHandler();
        mParentApp = app;
         
        mSimId = app.getMySimId();
        if(DBG) log("SIMRecordsEx construct");

        if(FeatureOption.MTK_GEMINI_SUPPORT) {
            mPhone = ((GeminiPhone)(PhoneFactory.getDefaultPhone())).getPhonebyId(mSimId);
        } else {
            mPhone = PhoneFactory.getDefaultPhone();
        }
        
        hasQueryIccId = false;

        mCi.registerForRadioStateChanged(this, EVENT_RADIO_STATE_CHANGED, null);
        
        try{
            mTelephonyExt = MediatekClassFactory.createInstance(ITelephonyExt.class);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    // ***** Overridden from Handler
    public void handleMessage(Message msg) {
        AsyncResult ar;
        byte data[];

        if (DBG) log("Received message " + msg + "[" + msg.what + "] ");

        switch (msg.what) {
            case EVENT_RADIO_STATE_CHANGED:
                if (DBG) log("handleMessage (EVENT_RADIO_STATE_CHANGED)");  

                if (FeatureOption.MTK_3GDONGLE_SUPPORT) {
                    if(!mCi.getRadioState().isAvailable()) {
                        if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                            resetSimRecordsEx();
                            log("clear sim info when radio is not available for tablet");
                            String enCryState = SystemProperties.get("vold.decrypt");
                            if(enCryState == null || "".equals(enCryState) || "trigger_restart_framework".equals(enCryState)) {
                                DefaultSIMSettings.onAllIccidQueryComplete(mContext, mPhone, null, null, null, null, false);
                            }
                        }
                    }
                }

                /* Sometime RADIO_STATE_CHANGED is sent before SimRecordEx is disposed. So don't call getRadioState API after SimRecordEx is disposed */
                if(mCi != null && mCi.getRadioState().isAvailable()) {
                    // msg callback will be response slowly when dongle inserted
                    // registrants already notify, the msg callback will be come even de-registration
                    if(!hasQueryIccId && mFh != null){
                        if (FeatureOption.MTK_3GDONGLE_SUPPORT) {
                            mFh.loadEFTransparent(EF_ICCID, obtainMessage(EVENT_QUERY_ICCID_DONE));
                        } else {
                            mCi.queryIccId(obtainMessage(EVENT_QUERY_ICCID_DONE));
                        }
                        //hasQueryIccId = true; // disabled cause is that assuming first query is successful may be dangerous.
                    }
                }
                break;
            case EVENT_QUERY_ICCID_DONE:
                if (DBG) log("handleMessage (EVENT_QUERY_ICCID_DONE)");
                ar = (AsyncResult)msg.obj;

                int oldIccIdQueryState = iccIdQueryState;
                iccIdQueryState = (ar.exception == null) ? 1 : 0;
                
                if (ar.exception != null) {

                    if (FeatureOption.MTK_3GDONGLE_SUPPORT) {
                        mIccId = "00000000000000808080";
                        log(" iccid reset :"+mIccId);
                    } else {
                        iccIdQueryState = 0;
                        mIccId = null;
                        loge("query mIccId error");
                        break;
                    }
                } else {
                    if (FeatureOption.MTK_3GDONGLE_SUPPORT) {
                        data = (byte[])ar.result;
                        mIccId = IccUtils.parseIccIdToString(data, 0, data.length);
                    } else {
                        if ((ar.result != null) && !( ((String)ar.result).equals("") )) {
                            mIccId = (String)ar.result;
                        }
                    }
                }
                hasQueryIccId = true;
                
                log("iccid: " + mIccId);
                if (mParentApp != null && mParentApp.getIccRecords() != null) {
                    mParentApp.getIccRecords().mIccId = mIccId;
                }
                if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                    //boolean isSimInfoReady = SystemProperties.getBoolean(TelephonyProperties.PROPERTY_SIM_INFO_READY, false);
                    boolean isSimInfoReady = (oldIccIdQueryState == iccIdQueryState); // FALSE case: -1 -> 0, 0 -> 1
                    log("is SIMInfo ready [" + isSimInfoReady);
                    if (!isSimInfoReady) {
                        // ALPS00335578
                        String enCryState = SystemProperties.get("vold.decrypt");
                        if(enCryState == null || "".equals(enCryState) || "trigger_restart_framework".equals(enCryState)) {
                            DefaultSIMSettings.onAllIccidQueryComplete(mContext, mPhone, mIccId, null, null, null, false);
                        }
                    } else {
                        if (DBG) log("SIM INFO has been ready.");
                    }
                }
                break;
        }
    }
        
    public void dispose() {
        //Unregister for all events
        if(DBG) log("dispose");
        mCi.unregisterForRadioStateChanged(this);   
        
        mParentApp = null;
        mFh = null;
        mCi = null;
        mContext = null;
        mIccId = null;
    }

    protected void finalize() {
        if(DBG) log("finalized");
    }

    public void resetSimRecordsEx() {
        //Unregister for all events
        if(DBG) log("resetSimRecordsEx");
        mIccId = null;
        iccIdQueryState = -1;
        hasQueryIccId = false;
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[SIMRecordsEx] [SIM" + mSimId + "]" + s);
    }

    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[SIMRecordsEx] [SIM" + mSimId + "]"  + s);
    }

    protected void logw(String s, Throwable tr) {
        Rlog.w(LOG_TAG, "[SIMRecordsEx] [SIM" + mSimId + "]" + s, tr);
    }

    protected void logv(String s) {
        Rlog.v(LOG_TAG, "[SIMRecordsEx] [SIM" + mSimId + "]"  + s);
    }
}
