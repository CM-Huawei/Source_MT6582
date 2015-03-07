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

package com.android.internal.telephony;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;

import com.android.internal.telephony.uicc.IccUtils;

// MTK-START
import android.provider.Telephony;
import android.util.Config;
import android.os.Bundle;
import com.mediatek.common.featureoption.FeatureOption;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Iterator;

// Tablet multi-user feature 
import com.mediatek.common.telephony.IOnlyOwnerSimSupport;
import com.mediatek.common.MediatekClassFactory;
// MTK-END

/**
 * WAP push handler class.
 *
 * @hide
 */
public class WapPushOverSms implements ServiceConnection {
    private static final String TAG = "WAP PUSH";
    private static final boolean DBG = true;

    private final Context mContext;

    /** Assigned from ServiceConnection callback on main threaad. */
    private volatile IWapPushManager mWapPushManager;

    // MTK-START
    /** Tablet Multi-user feature */
    private IOnlyOwnerSimSupport mOnlyOwnerSimSupport = null;
    // MTK-END

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mWapPushManager = IWapPushManager.Stub.asInterface(service);
        if (DBG) Rlog.v(TAG, "wappush manager connected to " + hashCode());
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mWapPushManager = null;
        if (DBG) Rlog.v(TAG, "wappush manager disconnected.");
    }

    public WapPushOverSms(Context context) {
        mContext = context;
        Intent intent = new Intent(IWapPushManager.class.getName());
        ComponentName comp = intent.resolveSystemService(context.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !context.bindService(intent, this, Context.BIND_AUTO_CREATE)) {
            Rlog.e(TAG, "bindService() for wappush manager failed");
        } else {
            if (DBG) Rlog.v(TAG, "bindService() for wappush manager succeeded");
        }

        // MTK-START
        // Get the multi-user plug-in class
        mOnlyOwnerSimSupport = MediatekClassFactory.createInstance(IOnlyOwnerSimSupport.class,mContext,true);
        if (mOnlyOwnerSimSupport != null) {
            String actualClassName = mOnlyOwnerSimSupport.getClass().getName();
            Rlog.d(TAG, "initial mOnlyOwnerSimSupport done, actual class name is " + actualClassName);
        } else {
            Rlog.e(TAG, "FAIL! intial mOnlyOwnerSimSupport");
        }
        // MTK-END
    }

    void dispose() {
        if (mWapPushManager != null) {
            if (DBG) Rlog.v(TAG, "dispose: unbind wappush manager");
            mContext.unbindService(this);
        } else {
            Rlog.e(TAG, "dispose: not bound to a wappush manager");
        }
    }

    /**
     * Dispatches inbound messages that are in the WAP PDU format. See
     * wap-230-wsp-20010705-a section 8 for details on the WAP PDU format.
     *
     * @param pdu The WAP PDU, made up of one or more SMS PDUs
     * @return a result code from {@link android.provider.Telephony.Sms.Intents}, or
     *         {@link Activity#RESULT_OK} if the message has been broadcast
     *         to applications
     */
    public int dispatchWapPdu(byte[] pdu, BroadcastReceiver receiver, InboundSmsHandler handler) {

        if (DBG) Rlog.d(TAG, "Rx: " + IccUtils.bytesToHexString(pdu));

        try {
            int index = 0;
            int transactionId = pdu[index++] & 0xFF;
            int pduType = pdu[index++] & 0xFF;

            if ((pduType != WspTypeDecoder.PDU_TYPE_PUSH) &&
                    (pduType != WspTypeDecoder.PDU_TYPE_CONFIRMED_PUSH)) {
                index = mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_valid_wappush_index);
                if (index != -1) {
                    transactionId = pdu[index++] & 0xff;
                    pduType = pdu[index++] & 0xff;
                    if (DBG)
                        Rlog.d(TAG, "index = " + index + " PDU Type = " + pduType +
                                " transactionID = " + transactionId);

                    // recheck wap push pduType
                    if ((pduType != WspTypeDecoder.PDU_TYPE_PUSH)
                            && (pduType != WspTypeDecoder.PDU_TYPE_CONFIRMED_PUSH)) {
                        if (DBG) Rlog.w(TAG, "Received non-PUSH WAP PDU. Type = " + pduType);
                        return Intents.RESULT_SMS_HANDLED;
                    }
                } else {
                    if (DBG) Rlog.w(TAG, "Received non-PUSH WAP PDU. Type = " + pduType);
                    return Intents.RESULT_SMS_HANDLED;
                }
            }

            WspTypeDecoder pduDecoder = new WspTypeDecoder(pdu);

            /**
             * Parse HeaderLen(unsigned integer).
             * From wap-230-wsp-20010705-a section 8.1.2
             * The maximum size of a uintvar is 32 bits.
             * So it will be encoded in no more than 5 octets.
             */
            if (pduDecoder.decodeUintvarInteger(index) == false) {
                if (DBG) Rlog.w(TAG, "Received PDU. Header Length error.");
                return Intents.RESULT_SMS_GENERIC_ERROR;
            }
            int headerLength = (int) pduDecoder.getValue32();
            index += pduDecoder.getDecodedDataLength();

            int headerStartIndex = index;

            /**
             * Parse Content-Type.
             * From wap-230-wsp-20010705-a section 8.4.2.24
             *
             * Content-type-value = Constrained-media | Content-general-form
             * Content-general-form = Value-length Media-type
             * Media-type = (Well-known-media | Extension-Media) *(Parameter)
             * Value-length = Short-length | (Length-quote Length)
             * Short-length = <Any octet 0-30>   (octet <= WAP_PDU_SHORT_LENGTH_MAX)
             * Length-quote = <Octet 31>         (WAP_PDU_LENGTH_QUOTE)
             * Length = Uintvar-integer
             */
            if (pduDecoder.decodeContentType(index) == false) {
                if (DBG) Rlog.w(TAG, "Received PDU. Header Content-Type error.");
                return Intents.RESULT_SMS_GENERIC_ERROR;
            }

            String mimeType = pduDecoder.getValueString();
            long binaryContentType = pduDecoder.getValue32();
            index += pduDecoder.getDecodedDataLength();

            byte[] header = new byte[headerLength];
            System.arraycopy(pdu, headerStartIndex, header, 0, header.length);
            // MTK-START
            pduDecoder.decodeHeaders(index, headerLength - index + headerStartIndex);
            // MTK-END

            byte[] intentData;

            if (mimeType != null && mimeType.equals(WspTypeDecoder.CONTENT_TYPE_B_PUSH_CO)) {
                intentData = pdu;
            } else {
                int dataIndex = headerStartIndex + headerLength;
                intentData = new byte[pdu.length - dataIndex];
                System.arraycopy(pdu, dataIndex, intentData, 0, intentData.length);
            }

            /**
             * Seek for application ID field in WSP header.
             * If application ID is found, WapPushManager substitute the message
             * processing. Since WapPushManager is optional module, if WapPushManager
             * is not found, legacy message processing will be continued.
             */
            if (pduDecoder.seekXWapApplicationId(index, index + headerLength - 1)) {
                index = (int) pduDecoder.getValue32();
                pduDecoder.decodeXWapApplicationId(index);
                String wapAppId = pduDecoder.getValueString();
                if (wapAppId == null) {
                    wapAppId = Integer.toString((int) pduDecoder.getValue32());
                }

                String contentType = ((mimeType == null) ?
                        Long.toString(binaryContentType) : mimeType);
                if (DBG) Rlog.v(TAG, "appid found: " + wapAppId + ":" + contentType);

                try {
                    boolean processFurther = true;
                    IWapPushManager wapPushMan = mWapPushManager;

                    if (wapPushMan == null) {
                        if (DBG) Rlog.w(TAG, "wap push manager not found!");
                    } else {
                        Intent intent = new Intent();
                        intent.putExtra("transactionId", transactionId);
                        intent.putExtra("pduType", pduType);
                        intent.putExtra("header", header);
                        intent.putExtra("data", intentData);
                        intent.putExtra("contentTypeParameters",
                                pduDecoder.getContentParameters());
                        // MTK-START
                        intent.putExtra("wspHeaders",pduDecoder.getHeaders());

                        if(bundle != null) {
                            Rlog.d(TAG, "put addr info into intent 1");
                            intent.putExtra(Telephony.WapPush.ADDR, bundle.getString(Telephony.WapPush.ADDR));
                            intent.putExtra(Telephony.WapPush.SERVICE_ADDR, bundle.getString(Telephony.WapPush.SERVICE_ADDR));
                        }
                        // MTK-END

                        int procRet = wapPushMan.processMessage(wapAppId, contentType, intent);
                        if (DBG) Rlog.v(TAG, "procRet:" + procRet);
                        if ((procRet & WapPushManagerParams.MESSAGE_HANDLED) > 0
                                && (procRet & WapPushManagerParams.FURTHER_PROCESSING) == 0) {
                            processFurther = false;
                        }
                    }
                    if (!processFurther) {
                        return Intents.RESULT_SMS_HANDLED;
                    }
                } catch (RemoteException e) {
                    if (DBG) Rlog.w(TAG, "remote func failed...");
                }
            }
            if (DBG) Rlog.v(TAG, "fall back to existing handler");

            if (mimeType == null) {
                if (DBG) Rlog.w(TAG, "Header Content-Type error.");
                return Intents.RESULT_SMS_GENERIC_ERROR;
            }

            String permission;
            int appOp;

            if (mimeType.equals(WspTypeDecoder.CONTENT_TYPE_B_MMS)) {
                Rlog.d(TAG, "WapPush set permission for RECEIVE_MMS");
                permission = android.Manifest.permission.RECEIVE_MMS;
                appOp = AppOpsManager.OP_RECEIVE_MMS;
            } else {
                Rlog.d(TAG, "WapPush set permission for RECEIVE_WAP_PUSH");
                permission = android.Manifest.permission.RECEIVE_WAP_PUSH;
                appOp = AppOpsManager.OP_RECEIVE_WAP_PUSH;
            }

            Intent intent = new Intent(Intents.WAP_PUSH_DELIVER_ACTION);
            intent.setType(mimeType);
            intent.putExtra("transactionId", transactionId);
            intent.putExtra("pduType", pduType);
            intent.putExtra("header", header);
            intent.putExtra("data", intentData);
            intent.putExtra("contentTypeParameters", pduDecoder.getContentParameters());
            // MTK-START
            intent.putExtra("wspHeaders",pduDecoder.getHeaders());

            if(bundle != null) {
                Rlog.d(TAG, "put addr info into intent 2");
                intent.putExtra(Telephony.WapPush.ADDR, bundle.getString(Telephony.WapPush.ADDR));
                intent.putExtra(Telephony.WapPush.SERVICE_ADDR, bundle.getString(Telephony.WapPush.SERVICE_ADDR));
            }
            // MTK-END

            // Direct the intent to only the default MMS app. If we can't find a default MMS app
            // then sent it to all broadcast receivers.
            ComponentName componentName = SmsApplication.getDefaultMmsApplication(mContext, true);
            if (componentName != null) {
                // Deliver MMS message only to this receiver
                intent.setComponent(componentName);
                if (DBG) Rlog.v(TAG, "Delivering MMS to: " + componentName.getPackageName() +
                        " " + componentName.getClassName());
            }

            // MTK-START
            // If it is not the current owner, dispatch to multi-user module to cache        
            if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
                intent.setAction(mOnlyOwnerSimSupport.MTK_NORMALUSER_MMS_ACTION);
                intent.setComponent(null);
            }
            // MTK-END

            handler.dispatchIntent(intent, permission, appOp, receiver);
            return Activity.RESULT_OK;
        } catch (ArrayIndexOutOfBoundsException aie) {
            // 0-byte WAP PDU or other unexpected WAP PDU contents can easily throw this;
            // log exception string without stack trace and return false.
            Rlog.e(TAG, "ignoring dispatchWapPdu() array index exception: " + aie);
            return Intents.RESULT_SMS_GENERIC_ERROR;
        }
    }

    // MTK-START
    /*
     * Add for wappush to get address and service address.
     * Address and service address will be stored in bundle
     * dispatchWapPdu(byte[] pdu, Bundle extra) will be called by framework
     */
    private Bundle bundle;
    public int dispatchWapPdu(byte[] pdu,  BroadcastReceiver receiver, InboundSmsHandler handler, Bundle extra){
        if (DBG) Rlog.i(TAG, "dispathchWapPdu!"
            + extra.getString(Telephony.WapPush.ADDR) + " "
            + extra.getString(Telephony.WapPush.SERVICE_ADDR));

        bundle = extra;
        return dispatchWapPdu(pdu, receiver, handler);
    }
    // MTK-END
}
