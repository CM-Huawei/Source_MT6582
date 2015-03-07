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

package com.android.internal.telephony;

import android.content.pm.PackageManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;
import android.os.SystemProperties;

import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccRecords;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mediatek.common.telephony.AlphaTag;
import com.mediatek.common.telephony.UsimGroup;
import com.mediatek.common.telephony.gsm.UsimPBMemInfo;

/**
 * SimPhoneBookInterfaceManager to provide an inter-process communication to
 * access ADN-like SIM records.
 */
public abstract class IccPhoneBookInterfaceManager extends IIccPhoneBook.Stub {
    protected static final boolean DBG = true;

    protected PhoneBase mPhone;
    protected AdnRecordCache mAdnCache;
    protected final Object mLock = new Object();
    protected int mRecordSize[];
    protected boolean mSuccess;
    protected List<AdnRecord> mRecords;
    protected int mErrorCause;

    protected static final boolean ALLOW_SIM_OP_IN_UI_THREAD = false;

    protected static final int EVENT_GET_SIZE_DONE = 1;
    protected static final int EVENT_LOAD_DONE = 2;
    protected static final int EVENT_UPDATE_DONE = 3;

    private static int sTimes = 1;

    protected Handler mBaseHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch (msg.what) {
                case EVENT_GET_SIZE_DONE:
                    ar = (AsyncResult) msg.obj;
                    synchronized (mLock) {
                        if (ar.exception == null) {
                            mRecordSize = (int[])ar.result;
                            // mRecordSize[0]  is the record length
                            // mRecordSize[1]  is the total length of the EF file
                            // mRecordSize[2]  is the number of records in the EF file
                            logd("GET_RECORD_SIZE Size " + mRecordSize[0] +
                                    " total " + mRecordSize[1] +
                                    " #record " + mRecordSize[2]);
                        }
                        notifyPending(ar);
                    }
                    break;
                case EVENT_UPDATE_DONE:
                    logd("EVENT_UPDATE_DONE");
                    ar = (AsyncResult) msg.obj;
                    synchronized (mLock) {
                        mSuccess = (ar.exception == null);
                        logd("EVENT_UPDATE_DONE" + "mSuccess:" + mSuccess);
                        // MTK-START [mtk80601][111215][ALPS00093395]
                        if (!mSuccess) {
                            mErrorCause = getErrorCauseFromException(
                                    (CommandException) ar.exception);
                        } else {
                            mErrorCause = IccProvider.ERROR_ICC_PROVIDER_NO_ERROR;
                        }
                        logd("update done result: " + mErrorCause);
                        // MTK-END [mtk80601][111215][ALPS00093395]
                        notifyPending(ar);
                    }
                    break;
                case EVENT_LOAD_DONE:
                    ar = (AsyncResult)msg.obj;
                    synchronized (mLock) {
                        if (ar.exception == null) {
                            mRecords = (List<AdnRecord>) ar.result;
                        } else {
                            if(DBG) logd("Cannot load ADN mRecords");
                            mRecords = null;
                        }
                        notifyPending(ar);
                    }
                    break;
                default:
                    break;
            }
        }

        private void notifyPending(AsyncResult ar) {
            if (ar.userObj == null) {
                return;
            }
            try {
                AtomicBoolean status = (AtomicBoolean) ar.userObj;
                status.set(true);
                mLock.notifyAll();
            } catch (ClassCastException e) {
                //this may be caused by Duplicated notify,just ignore
                loge("notifyPending " + e.getMessage());
            }
        }
    };

    public IccPhoneBookInterfaceManager(PhoneBase phone) {
        this.mPhone = phone;
        IccRecords r = phone.mIccRecords.get();
        if (r != null) {
            mAdnCache = r.getAdnCache();
        }
    }

    public void dispose() {
    }

    public void updateIccRecords(IccRecords iccRecords) {
        if (iccRecords != null) {
            mAdnCache = iccRecords.getAdnCache();
            logd("[updateIccRecords] Set mAdnCache value");
        } else {
            mAdnCache = null;
            logd("[updateIccRecords] Set mAdnCache value to null");
        }
    }

    protected void publish() {
        //NOTE service "simphonebook" added by IccSmsInterfaceManagerProxy
        ServiceManager.addService("simphonebook", this);
    }

    protected abstract void logd(String msg);

    protected abstract void loge(String msg);

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * getAdnRecordsInEf must be called at least once before this function,
     * otherwise an error will be returned. Currently the email field
     * if set in the ADN record is ignored.
     * throws SecurityException if no WRITE_CONTACTS permission
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param oldTag adn tag to be replaced
     * @param oldPhoneNumber adn number to be replaced
     *        Set both oldTag and oldPhoneNubmer to "" means to replace an
     *        empty record, aka, insert new record
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number ot be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
    @Override
    public boolean
    updateAdnRecordsInEfBySearch (int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber, String pin2) {
        // MTK-START [mtk80601][111215][ALPS00093395]
        int result;

        result = updateAdnRecordsInEfBySearchWithError(
                efid, oldTag, oldPhoneNumber,
                newTag, newPhoneNumber, pin2);

        return result == IccProvider.ERROR_ICC_PROVIDER_NO_ERROR;
    }

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF getAdnRecordsInEf
     * must be called at least once before this function, otherwise an error
     * will be returned. Currently the email field if set in the ADN record is
     * ignored. throws SecurityException if no WRITE_CONTACTS permission This
     * method will return why the error occurs.
     * 
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param oldTag adn tag to be replaced
     * @param oldPhoneNumber adn number to be replaced Set both oldTag and
     *            oldPhoneNubmer to "" means to replace an empty record, aka,
     *            insert new record
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number ot be stored Set both newTag and
     *            newPhoneNubmer to "" means to replace the old record with
     *            empty one, aka, delete old record
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return ERROR_ICC_PROVIDER_* defined in the IccProvider
     */
    public synchronized int updateAdnRecordsInEfBySearchWithError(int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber, String pin2) {

        int index = -1;
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (mAdnCache == null)
        {
            logd("updateAdnRecordsInEfBySearchWithError mAdnCache is null");
            return IccProvider.ERROR_ICC_PROVIDER_UNKNOWN;            
        }

        if (DBG) {
            logd("updateAdnRecordsInEfBySearch: efid=" + efid +
                    " (" + oldTag + "," + oldPhoneNumber + ")" + "==>" +
                    " (" + newTag + " (" + newTag.length() + ")," + newPhoneNumber + ")" + " pin2="
                    + pin2);
        }
        efid = updateEfForIccType(efid);

        synchronized(mLock) {
            checkThread();
            mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, status);
            sTimes = (sTimes + 2) % 20000;
            response.arg1 = sTimes;
            AdnRecord oldAdn = new AdnRecord(oldTag, oldPhoneNumber);
            if (null == newPhoneNumber) {
                newPhoneNumber = "";
            }
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber);
            if (mAdnCache != null) {
                index = mAdnCache.updateAdnBySearch(efid, oldAdn, newAdn, pin2, response);
                waitForResult(status);
            } else {
                loge("Failure while trying to update by search due to uninitialised adncache");
            }
        }
        if (mErrorCause == IccProvider.ERROR_ICC_PROVIDER_NO_ERROR) {
            logd("updateAdnRecordsInEfBySearchWithError success index is " + index);
            return index;
        }
        return mErrorCause;
    }

    public synchronized int updateUsimPBRecordsInEfBySearchWithError(int efid,
            String oldTag, String oldPhoneNumber, String oldAnr, String oldGrpIds,
            String[] oldEmails,
            String newTag, String newPhoneNumber, String newAnr, String newGrpIds,
            String[] newEmails) {

        int index = -1;
        AtomicBoolean status = new AtomicBoolean(false);
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (mAdnCache == null)
        {
            logd("updateUsimPBRecordsInEfBySearchWithError mAdnCache is null");
            return IccProvider.ERROR_ICC_PROVIDER_UNKNOWN;            
        }

        if (DBG) {
            logd("updateUsimPBRecordsInEfBySearchWithError: efid=" + efid +
                    " (" + oldTag + "," + oldPhoneNumber + "oldAnr" + oldAnr + " oldGrpIds "
                    + oldGrpIds + ")" + "==>" +
                    "(" + newTag + "," + newPhoneNumber + ")" + " newAnr= " + newAnr
                    + " newGrpIds = " + newGrpIds + " newEmails = " + newEmails);
        }
        synchronized (mLock) {
            checkThread();
            mSuccess = false;
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, status);
            sTimes = (sTimes + 2) % 20000;
            response.arg1 = sTimes;
            AdnRecord oldAdn = new AdnRecord(oldTag, oldPhoneNumber);
            if (null == newPhoneNumber) {
                newPhoneNumber = "";
            }
            AdnRecord newAdn = new AdnRecord(0, 0, newTag, newPhoneNumber, newAnr, newEmails,
                    newGrpIds);
            index = mAdnCache.updateAdnBySearch(efid, oldAdn, newAdn, null, response);
            waitForResult(status);
        }
        if (mErrorCause == IccProvider.ERROR_ICC_PROVIDER_NO_ERROR) {
            logd("updateUsimPBRecordsInEfBySearchWithError success index is " + index);
            return index;
        }
        return mErrorCause;
    }

    public synchronized int updateUsimPBRecordsBySearchWithError(int efid, AdnRecord oldAdn,
            AdnRecord newAdn) {
        int index = -1;
        AtomicBoolean status = new AtomicBoolean(false);
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (mAdnCache == null)
        {
            logd("updateUsimPBRecordsBySearchWithError mAdnCache is null");
            return IccProvider.ERROR_ICC_PROVIDER_UNKNOWN;            
        }

        if (DBG) {
            logd("updateUsimPBRecordsBySearchWithError: efid=" + efid +
                    " (" + oldAdn + ")" + "==>" + "(" + newAdn + ")");
        }
        synchronized (mLock) {
            checkThread();
            mSuccess = false;
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, status);
            sTimes = (sTimes + 2) % 20000;
            response.arg1 = sTimes;
            if (newAdn.getNumber() == null) {
                newAdn.setNumber("");
            }
            index = mAdnCache.updateAdnBySearch(efid, oldAdn, newAdn, null, response);
            waitForResult(status);
        }
        if (mErrorCause == IccProvider.ERROR_ICC_PROVIDER_NO_ERROR) {
            logd("updateUsimPBRecordsBySearchWithError success index is " + index);
            return index;
        }
        return mErrorCause;
    }

    // MTK-END [mtk80601][111215][ALPS00093395]

    /**
     * Update an ADN-like EF record by record index
     *
     * This is useful for iteration the whole ADN file, such as write the whole
     * phone book or erase/format the whole phonebook. Currently the email field
     * if set in the ADN record is ignored.
     * throws SecurityException if no WRITE_CONTACTS permission
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number to be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param index is 1-based adn record index to be updated
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
    @Override
    public boolean
    updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, int index, String pin2) {

        int result;

        result = updateAdnRecordsInEfByIndexWithError(
                efid, newTag,
                newPhoneNumber, index, pin2);

        return result == IccProvider.ERROR_ICC_PROVIDER_NO_ERROR;
    }

    /**
     * Update an ADN-like EF record by record index This is useful for iteration
     * the whole ADN file, such as write the whole phone book or erase/format
     * the whole phonebook. Currently the email field if set in the ADN record
     * is ignored. throws SecurityException if no WRITE_CONTACTS permission This
     * method will return why the error occurs
     * 
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number to be stored Set both newTag and
     *            newPhoneNubmer to "" means to replace the old record with
     *            empty one, aka, delete old record
     * @param index is 1-based adn record index to be updated
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return ERROR_ICC_PROVIDER_* defined in the IccProvider
     */
    // MTK-START [mtk80601][111215][ALPS00093395]
    public synchronized int updateAdnRecordsInEfByIndexWithError(int efid, String newTag,
            String newPhoneNumber, int index, String pin2) {

        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (mAdnCache == null)
        {
            logd("updateAdnRecordsInEfByIndex mAdnCache is null");
            return IccProvider.ERROR_ICC_PROVIDER_UNKNOWN;            
        }

        if (DBG) {
            logd("updateAdnRecordsInEfByIndex: efid=" + efid +
                    " Index=" + index + " ==> " +
                    "(" + newTag + "," + newPhoneNumber + ")" + " pin2=" + pin2);
        }
        synchronized (mLock) {
            checkThread();
            mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, status);
            if (null == newPhoneNumber) {
                newPhoneNumber = "";
            }
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber);
            if (mAdnCache != null) {
                mAdnCache.updateAdnByIndex(efid, newAdn, index, pin2, response);
                waitForResult(status);
            } else {
                loge("Failure while trying to update by index due to uninitialised adncache");
            }
        }
        return mErrorCause;
    }

    public synchronized int updateUsimPBRecordsInEfByIndexWithError(int efid, String newTag,
            String newPhoneNumber, String newAnr, String newGrpIds, String[] newEmails, int index) {

        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (mAdnCache == null)
        {
            logd("updateUsimPBRecordsInEfByIndexWithError mAdnCache is null");
            return IccProvider.ERROR_ICC_PROVIDER_UNKNOWN;            
        }

        if (DBG) {
            logd("updateUsimPBRecordsInEfByIndexWithError: efid=" + efid +
                    " Index=" + index + " ==> " +
                    "(" + newTag + "," + newPhoneNumber + ")" + " newAnr= " + newAnr
                    + " newGrpIds = " + newGrpIds + " newEmails = " + newEmails);
        }
        synchronized (mLock) {
            checkThread();
            mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, status);
            if (null == newPhoneNumber) {
                newPhoneNumber = "";
            }
            AdnRecord newAdn = new AdnRecord(efid, index, newTag, newPhoneNumber, newAnr,
                    newEmails, newGrpIds);
            mAdnCache.updateAdnByIndex(efid, newAdn, index, null, response);
            waitForResult(status);
        }
        return mErrorCause;

    }

    public synchronized int updateUsimPBRecordsByIndexWithError(int efid, AdnRecord record,
            int index) {

        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (mAdnCache == null)
        {
            logd("updateUsimPBRecordsByIndexWithError mAdnCache is null");
            return IccProvider.ERROR_ICC_PROVIDER_UNKNOWN;            
        }

        if (DBG) {
            logd("updateUsimPBRecordsByIndexWithError: efid=" + efid +
                    " Index=" + index + " ==> " + record);
        }
        synchronized (mLock) {
            checkThread();
            mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, status);
            mAdnCache.updateAdnByIndex(efid, record, index, null, response);
            waitForResult(status);
        }
        return mErrorCause;

    }

    // MTK-END [mtk80601][111215][ALPS00093395]

    /**
     * Get the capacity of records in efid
     *
     * @param efid the EF id of a ADN-like ICC
     * @return  int[3] array
     *            mRecordSizes[0]  is the single record length
     *            mRecordSizes[1]  is the total length of the EF file
     *            mRecordSizes[2]  is the number of records in the EF file
     */
    @Override
    public abstract int[] getAdnRecordsSize(int efid);

    /**
     * Loads the AdnRecords in efid and returns them as a
     * List of AdnRecords
     *
     * throws SecurityException if no READ_CONTACTS permission
     *
     * @param efid the EF id of a ADN-like ICC
     * @return List of AdnRecord
     */
    @Override
    public synchronized List<AdnRecord> getAdnRecordsInEf(int efid) {

        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.READ_CONTACTS permission");
        }

        efid = updateEfForIccType(efid);

        if (mAdnCache == null)
        {
            logd("getAdnRecordsInEF mAdnCache is null");
            return null;            
        }

        if (DBG) {
            logd("getAdnRecordsInEF: efid=" + efid);
        }
        synchronized (mLock) {
            checkThread();
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = mBaseHandler.obtainMessage(EVENT_LOAD_DONE, status);
            if (mAdnCache != null) {
                mAdnCache.requestLoadAllAdnLike(efid, mAdnCache.extensionEfForEf(efid), response);
                waitForResult(status);
            } else {
                loge("Failure while trying to load from SIM due to uninitialised adncache");
            }
        }
        return mRecords;
    }

    protected void checkThread() {
        if (!ALLOW_SIM_OP_IN_UI_THREAD) {
            // Make sure this isn't the UI thread, since it will block
            if (mBaseHandler.getLooper().equals(Looper.myLooper())) {
                loge("query() called on the main UI thread!");
                throw new IllegalStateException(
                        "You cannot call query on this provder from the main UI thread.");
            }
        }
    }

    protected void waitForResult(AtomicBoolean status) {
        while (!status.get()) {
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                logd("interrupted while trying to update by search");
            }
        }
    }

    private int updateEfForIccType(int efid) {
        // Check if we are trying to read ADN records
        if (efid == IccConstants.EF_ADN) {
            if (mPhone.getCurrentUiccAppType() == AppType.APPTYPE_USIM) {
                return IccConstants.EF_PBR;
            }
        }
        return efid;
    }

    // MTK-START [mtk80601][111215][ALPS00093395]

    private int getErrorCauseFromException(CommandException e) {

        int ret;

        if (e == null) {
            return IccProvider.ERROR_ICC_PROVIDER_NO_ERROR;
        }

        switch (e.getCommandError()) {
            case GENERIC_FAILURE: /* occurs when Extension file is full(?) */
                ret = IccProvider.ERROR_ICC_PROVIDER_GENERIC_FAILURE;
                break;
            case DIAL_STRING_TOO_LONG:
                ret = IccProvider.ERROR_ICC_PROVIDER_NUMBER_TOO_LONG;
                break;
            case SIM_PUK2:
            case PASSWORD_INCORRECT:
                ret = IccProvider.ERROR_ICC_PROVIDER_PASSWORD_ERROR;
                break;
            case TEXT_STRING_TOO_LONG:
                ret = IccProvider.ERROR_ICC_PROVIDER_TEXT_TOO_LONG;
                break;
            case SIM_MEM_FULL:
                ret = IccProvider.ERROR_ICC_PROVIDER_STORAGE_FULL;
                break;
            case NOT_READY:
                ret = IccProvider.ERROR_ICC_PROVIDER_NOT_READY;
                break;
            case ADDITIONAL_NUMBER_STRING_TOO_LONG:
                ret = IccProvider.ERROR_ICC_PROVIDER_ANR_TOO_LONG;
                break;
            case ADN_LIST_NOT_EXIST:
                ret = IccProvider.ERROR_ICC_PROVIDER_ADN_LIST_NOT_EXIST;
                break;
            case EMAIL_SIZE_LIMIT:
                ret = IccProvider.ERROR_ICC_PROVIDER_EMAIL_FULL;
                break;
            case EMAIL_NAME_TOOLONG:
                ret = IccProvider.ERROR_ICC_PROVIDER_EMAIL_TOOLONG;
                break;
            default:
                ret = IccProvider.ERROR_ICC_PROVIDER_UNKNOWN;
                break;
        }

        return ret;
    }

    public void onPhbReady() {
        if (mAdnCache != null)
        {
            mAdnCache.requestLoadAllAdnLike(IccConstants.EF_ADN,
                    mAdnCache.extensionEfForEf(IccConstants.EF_ADN), null);
        }
    }

    public boolean isPhbReady() {
        String strPhbReady = "false";
        if (PhoneConstants.GEMINI_SIM_2 == mPhone.getMySimId()) {
            strPhbReady = SystemProperties.get("gsm.sim.ril.phbready.2", "false");
        } else if (PhoneConstants.GEMINI_SIM_3 == mPhone.getMySimId()) {
            strPhbReady = SystemProperties.get("gsm.sim.ril.phbready.3", "false");
        } else if (PhoneConstants.GEMINI_SIM_4 == mPhone.getMySimId()) {
            strPhbReady = SystemProperties.get("gsm.sim.ril.phbready.4", "false");
        } else {
            strPhbReady = SystemProperties.get("gsm.sim.ril.phbready", "false");
        }   

        logd("[isPhbReady] sim id:" + mPhone.getMySimId() + ", isPhbReady: " + strPhbReady);

        return strPhbReady.equals("true");
    }

    public List<UsimGroup> getUsimGroups() {
        return ((mAdnCache == null) ? null : mAdnCache.getUsimGroups());
    }

    public String getUsimGroupById(int nGasId) {
        return ((mAdnCache == null) ? null : mAdnCache.getUsimGroupById(nGasId));
    }

    public boolean removeUsimGroupById(int nGasId) {
        return ((mAdnCache == null) ? false : mAdnCache.removeUsimGroupById(nGasId));
    }

    public int insertUsimGroup(String grpName) {
        return ((mAdnCache == null) ? -1 : mAdnCache.insertUsimGroup(grpName));
    }

    public int updateUsimGroup(int nGasId, String grpName) {
        return ((mAdnCache == null) ? -1 : mAdnCache.updateUsimGroup(nGasId, grpName));
    }

    public boolean addContactToGroup(int adnIndex, int grpIndex) {
        return ((mAdnCache == null) ? false : mAdnCache.addContactToGroup(adnIndex, grpIndex));
    }

    public boolean removeContactFromGroup(int adnIndex, int grpIndex) {
        return ((mAdnCache == null) ? false : mAdnCache.removeContactFromGroup(adnIndex, grpIndex));
    }

    public boolean updateContactToGroups(int adnIndex, int[] grpIdList) {
        return ((mAdnCache == null) ? false : mAdnCache.updateContactToGroups(adnIndex, grpIdList));
    }

    public boolean moveContactFromGroupsToGroups(int adnIndex, int[] fromGrpIdList, int[] toGrpIdList) {
        return ((mAdnCache == null) ? false : mAdnCache.moveContactFromGroupsToGroups(adnIndex, fromGrpIdList, toGrpIdList));
    }

    public int hasExistGroup(String grpName) {
        return ((mAdnCache == null) ? -1 : mAdnCache.hasExistGroup(grpName));
    }

    public int getUsimGrpMaxNameLen() {
        return ((mAdnCache == null) ? -1 : mAdnCache.getUsimGrpMaxNameLen());
    }

    public int getUsimGrpMaxCount() {
        return ((mAdnCache == null) ? -1 : mAdnCache.getUsimGrpMaxCount());
    }

    public List<AlphaTag> getUsimAasList() {
        return ((mAdnCache == null) ? null : mAdnCache.getUsimAasList());
    }

    public String getUsimAasById(int index) {
        return ((mAdnCache == null) ? null : mAdnCache.getUsimAasById(index));
    }

    public boolean removeUsimAasById(int index, int pbrIndex) {
        return ((mAdnCache == null) ? false : mAdnCache.removeUsimAasById(index, pbrIndex));
    }

    public int insertUsimAas(String aasName) {
        return ((mAdnCache == null) ? -1 : mAdnCache.insertUsimAas(aasName));
    }

    public boolean updateUsimAas(int index, int pbrIndex, String aasName) {
        return ((mAdnCache == null) ? false : mAdnCache.updateUsimAas(index, pbrIndex, aasName));
    }

    public boolean updateAdnAas(int adnIndex, int aasIndex) {
        return ((mAdnCache == null) ? false : mAdnCache.updateAdnAas(adnIndex, aasIndex));
    }

    public int getAnrCount() {
        return ((mAdnCache == null) ? 0 : mAdnCache.getAnrCount());
    }

    public int getEmailCount() {
        return ((mAdnCache == null) ? 0 : mAdnCache.getEmailCount());
    }
    
    public int getUsimAasMaxCount() {
        return ((mAdnCache == null) ? -1 : mAdnCache.getUsimAasMaxCount());
    }

    public int getUsimAasMaxNameLen() {
        return ((mAdnCache == null) ? -1 : mAdnCache.getUsimAasMaxNameLen());
    }

    public boolean hasSne() {
        return ((mAdnCache == null) ? false : mAdnCache.hasSne());
    }

    public int getSneRecordLen() {
        return ((mAdnCache == null) ? -1 : mAdnCache.getSneRecordLen());
    }

    /**
     * Judge if the PHB ADN is accessible or not
     *
     * @return  true for ready
     */
    public boolean isAdnAccessible() {
        return ((mAdnCache == null) ? false : mAdnCache.isAdnAccessible());
    }

    // M for LGE
    public synchronized UsimPBMemInfo[] getPhonebookMemStorageExt() {
        return ((mAdnCache == null) ? null : mAdnCache.getPhonebookMemStorageExt());
    }
    // MTK-END [mtk80601][111215][ALPS00093395]

}
