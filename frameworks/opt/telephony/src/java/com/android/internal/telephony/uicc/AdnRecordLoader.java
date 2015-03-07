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

import java.util.ArrayList;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.telephony.IPhoneNumberExt;

import com.android.internal.telephony.PhbEntry;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.GsmAlphabet;

public class AdnRecordLoader extends Handler {
    final static String LOG_TAG = "AdnRecordLoader";
    final static boolean VDBG = false;

    //***** Instance Variables

    private IccFileHandler mFh;
    int mEf;
    int mExtensionEF;
    int mPendingExtLoads;
    Message mUserResponse;
    String mPin2;

    // For "load one"
    int mRecordNumber;

    // for "load all"
    ArrayList<AdnRecord> mAdns; // only valid after EVENT_ADN_LOAD_ALL_DONE
    int current_read;
    int used;
    int total;

    // Either an AdnRecord or a reference to adns depending
    // if this is a load one or load all operation
    Object mResult;

    //***** Event Constants

    static final int EVENT_ADN_LOAD_DONE = 1;
    static final int EVENT_EXT_RECORD_LOAD_DONE = 2;
    static final int EVENT_ADN_LOAD_ALL_DONE = 3;
    static final int EVENT_EF_LINEAR_RECORD_SIZE_DONE = 4;
    static final int EVENT_UPDATE_RECORD_DONE = 5;
    static final int EVENT_UPDATE_PHB_RECORD_DONE = 101;
    static final int EVENT_VERIFY_PIN2 = 102;
    static final int EVENT_PHB_LOAD_DONE = 103;
    static final int EVENT_PHB_LOAD_ALL_DONE = 104;
    static final int EVENT_PHB_QUERY_STAUTS = 105;

    //***** MTK class factory
    private static IPhoneNumberExt mPhoneNumberExt;
    static {
        try{
            mPhoneNumberExt = MediatekClassFactory.createInstance(IPhoneNumberExt.class);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    //***** Constructor

    AdnRecordLoader(IccFileHandler fh) {
        // The telephony unit-test cases may create AdnRecords
        // in secondary threads
        super(Looper.getMainLooper());
        mFh = fh;
    }

    /**
     * Resulting AdnRecord is placed in response.obj.result
     * or response.obj.exception is set
     */
    public void
    loadFromEF(int ef, int extensionEF, int recordNumber,
                Message response) {
        mEf = ef;
        mExtensionEF = extensionEF;
        mRecordNumber = recordNumber;
        mUserResponse = response;

        int type = getPhbStorageType(ef);
        if (type != -1) {

            mFh.mCi.ReadPhbEntry(
                    type, recordNumber, recordNumber,
                    obtainMessage(EVENT_PHB_LOAD_DONE));
        } else {

            mFh.loadEFLinearFixed(
                    ef, recordNumber,
                    obtainMessage(EVENT_ADN_LOAD_DONE));
        }
    }


    /**
     * Resulting ArrayList&lt;adnRecord> is placed in response.obj.result
     * or response.obj.exception is set
     */
    public void
    loadAllFromEF(int ef, int extensionEF,
                Message response) {
        mEf = ef;
        mExtensionEF = extensionEF;
        mUserResponse = response;

        int type = getPhbStorageType(ef);
        if (type != -1) {
            mFh.mCi.queryPhbStorageInfo(
                    type,
                    obtainMessage(EVENT_PHB_QUERY_STAUTS));
        } else {
            mFh.loadEFLinearFixedAll(
                    ef,
                    obtainMessage(EVENT_ADN_LOAD_ALL_DONE));
        }
    }

    /**
     * Write adn to a EF SIM record
     * It will get the record size of EF record and compose hex adn array
     * then write the hex array to EF record
     *
     * @param adn is set with alphaTag and phone number
     * @param ef EF fileid
     * @param extensionEF extension EF fileid
     * @param recordNumber 1-based record index
     * @param pin2 for CHV2 operations, must be null if pin2 is not needed
     * @param response will be sent to its handler when completed
     */
    public void
    updateEF(AdnRecord adn, int ef, int extensionEF, int recordNumber,
            String pin2, Message response) {
        mEf = ef;
        mExtensionEF = extensionEF;
        mRecordNumber = recordNumber;
        mUserResponse = response;
        mPin2 = pin2;

        int type = getPhbStorageType(ef);
        if (type != -1) {

            updatePhb(adn, type);
        } else {

            mFh.getEFLinearRecordSize(ef,
                    obtainMessage(EVENT_EF_LINEAR_RECORD_SIZE_DONE, adn));
        }
    }

    //***** Overridden from Handler

    @Override
    public void
    handleMessage(Message msg) {
        AsyncResult ar;
        byte data[];
        AdnRecord adn;
        PhbEntry[] entries;
        int[] readInfo;
        int type;

        try {
            switch (msg.what) {
                case EVENT_EF_LINEAR_RECORD_SIZE_DONE:
                    ar = (AsyncResult)(msg.obj);
                    adn = (AdnRecord)(ar.userObj);

                    if (ar.exception != null) {
                        throw new RuntimeException("get EF record size failed",
                                ar.exception);
                    }

                    int[] recordSize = (int[])ar.result;
                    // recordSize is int[3] array
                    // int[0]  is the record length
                    // int[1]  is the total length of the EF file
                    // int[2]  is the number of records in the EF file
                    // So int[0] * int[2] = int[1]
                   if (recordSize.length != 3 || mRecordNumber > recordSize[2]) {
                        throw new RuntimeException("get wrong EF record size format",
                                ar.exception);
                    }

                    data = adn.buildAdnString(recordSize[0]);

                    if(data == null) {
                        throw new RuntimeException("wrong ADN format",
                                ar.exception);
                    }

                    mFh.updateEFLinearFixed(mEf, mRecordNumber,
                            data, mPin2, obtainMessage(EVENT_UPDATE_RECORD_DONE));

                    mPendingExtLoads = 1;

                    break;
                case EVENT_UPDATE_RECORD_DONE:
                    ar = (AsyncResult)(msg.obj);
                    if (ar.exception != null) {
                        throw new RuntimeException("update EF adn record failed",
                                ar.exception);
                    }
                    mPendingExtLoads = 0;
                    mResult = null;
                    break;
                case EVENT_ADN_LOAD_DONE:
                    ar = (AsyncResult)(msg.obj);
                    data = (byte[])(ar.result);

                    if (ar.exception != null) {
                        throw new RuntimeException("load failed", ar.exception);
                    }

                    if (VDBG) {
                        Rlog.d(LOG_TAG,"ADN EF: 0x"
                            + Integer.toHexString(mEf)
                            + ":" + mRecordNumber
                            + "\n" + IccUtils.bytesToHexString(data));
                    }

                    adn = new AdnRecord(mEf, mRecordNumber, data);
                    mResult = adn;

                    if (adn.hasExtendedRecord()) {
                        // If we have a valid value in the ext record field,
                        // we're not done yet: we need to read the corresponding
                        // ext record and append it

                        mPendingExtLoads = 1;

                        mFh.loadEFLinearFixed(
                            mExtensionEF, adn.mExtRecord,
                            obtainMessage(EVENT_EXT_RECORD_LOAD_DONE, adn));
                    }
                    break;

                case EVENT_EXT_RECORD_LOAD_DONE:
                    ar = (AsyncResult)(msg.obj);
                    data = (byte[])(ar.result);
                    adn = (AdnRecord)(ar.userObj);

                    if (ar.exception != null) {
                        throw new RuntimeException("load failed", ar.exception);
                    }

                    Rlog.d(LOG_TAG,"ADN extension EF: 0x"
                        + Integer.toHexString(mExtensionEF)
                        + ":" + adn.mExtRecord
                        + "\n" + IccUtils.bytesToHexString(data));

                    adn.appendExtRecord(data);

                    mPendingExtLoads--;
                    // result should have been set in
                    // EVENT_ADN_LOAD_DONE or EVENT_ADN_LOAD_ALL_DONE
                    break;

                case EVENT_ADN_LOAD_ALL_DONE:
                    ar = (AsyncResult)(msg.obj);
                    ArrayList<byte[]> datas = (ArrayList<byte[]>)(ar.result);

                    if (ar.exception != null) {
                        throw new RuntimeException("load failed", ar.exception);
                    }

                    mAdns = new ArrayList<AdnRecord>(datas.size());
                    mResult = mAdns;
                    mPendingExtLoads = 0;

                    for(int i = 0, s = datas.size() ; i < s ; i++) {
                        adn = new AdnRecord(mEf, 1 + i, datas.get(i));
                        mAdns.add(adn);

                        if (adn.hasExtendedRecord()) {
                            // If we have a valid value in the ext record field,
                            // we're not done yet: we need to read the corresponding
                            // ext record and append it

                            mPendingExtLoads++;

                            mFh.loadEFLinearFixed(
                                mExtensionEF, adn.mExtRecord,
                                obtainMessage(EVENT_EXT_RECORD_LOAD_DONE, adn));
                        }
                    }
                    break;
                // MTK-START [mtk80601][111215][ALPS00093395]
                case EVENT_UPDATE_PHB_RECORD_DONE:
                    ar = (AsyncResult) (msg.obj);
                    if (ar.exception != null) {
                        throw new RuntimeException("update PHB EF record failed",
                                ar.exception);
                    }
                    mPendingExtLoads = 0;
                    mResult = null;
                    break;

                case EVENT_VERIFY_PIN2:
                    ar = (AsyncResult) (msg.obj);
                    adn = (AdnRecord) (ar.userObj);

                    if (ar.exception != null) {
                        throw new RuntimeException("PHB Verify PIN2 error",
                                ar.exception);
                    }

                    writeEntryToModem(adn, getPhbStorageType(mEf));
                    mPendingExtLoads = 1;
                    break;

                case EVENT_PHB_LOAD_DONE:
                    ar = (AsyncResult) (msg.obj);
                    entries = (PhbEntry[]) (ar.result);

                    if (ar.exception != null) {
                        throw new RuntimeException("PHB Read an entry Error",
                                ar.exception);
                    }

                    adn = getAdnRecordFromPhbEntry(entries[0]);
                    mResult = adn;
                    mPendingExtLoads = 0;

                    break;

                case EVENT_PHB_QUERY_STAUTS:
                    /*
                     * response.obj.result[0] is number of current used entries
                     * response.obj.result[1] is number of total entries in the
                     * storage
                     */

                    ar = (AsyncResult) (msg.obj);
                    int[] info = (int[]) (ar.result);

                    if (ar.exception != null) {
                        throw new RuntimeException("PHB Query Info Error",
                                ar.exception);
                    }

                    type = getPhbStorageType(mEf);
                    readInfo = new int[3];
                    readInfo[0] = 1; // current_index;
                    readInfo[1] = info[0]; // # of remaining entries
                    readInfo[2] = info[1]; // # of total entries

                    mAdns = new ArrayList<AdnRecord>(readInfo[2]);
                    for (int i = 0; i < readInfo[2]; i++) {
                        // fillin empty entries to mAdns
                        adn = new AdnRecord(mEf, i + 1, "", "");
                        mAdns.add(i, adn);
                    }

                    readEntryFromModem(type, readInfo);
                    mPendingExtLoads = 1;
                    break;

                case EVENT_PHB_LOAD_ALL_DONE:
                    ar = (AsyncResult) (msg.obj);
                    readInfo = (int[]) (ar.userObj);
                    entries = (PhbEntry[]) (ar.result);

                    if (ar.exception != null) {
                        throw new RuntimeException("PHB Read Entries Error",
                                ar.exception);
                    }

                    for (int i = 0; i < entries.length; i++) {
                        adn = getAdnRecordFromPhbEntry(entries[i]);
                        if (adn != null) {
                            mAdns.set(adn.mRecordNumber - 1, adn);
                            readInfo[1]--;
                            Rlog.d(LOG_TAG, "Read entries: " + adn);

                        } else {
                            Rlog.e(LOG_TAG, "getAdnRecordFromPhbEntry return null");
                            throw new RuntimeException(
                                    "getAdnRecordFromPhbEntry return null",
                                    CommandException.fromRilErrno(
                                    RILConstants.GENERIC_FAILURE));
                        }
                    }
                    readInfo[0] += RILConstants.PHB_MAX_ENTRY;

                    if (readInfo[1] < 0) {
                        Rlog.e(LOG_TAG, "the read entries is not sync with query status: "
                                + readInfo[1]);
                        throw new RuntimeException(
                                "the read entries is not sync with query status: " + readInfo[1],
                                CommandException.fromRilErrno(
                                RILConstants.GENERIC_FAILURE));
                    }

                    if (readInfo[1] == 0 || readInfo[0] >= readInfo[2]) {

                        mResult = mAdns;
                        mPendingExtLoads = 0;
                    } else {
                        type = getPhbStorageType(mEf);
                        readEntryFromModem(type, readInfo);
                    }
                    break;
                // MTK-END [mtk80601][111215][ALPS00093395]
                    default:
                        break;
            }
        } catch (RuntimeException exc) {
            if (mUserResponse != null && mUserResponse.getTarget() != null) {
                AsyncResult.forMessage(mUserResponse).exception = exc.getCause();
                mUserResponse.sendToTarget();
                // Loading is all or nothing--either every load succeeds
                // or we fail the whole thing.
                mUserResponse = null;
            }
            return;
        }

        if (mUserResponse != null && mPendingExtLoads == 0 && mUserResponse.getTarget() != null) {
            AsyncResult.forMessage(mUserResponse).result = mResult;

            mUserResponse.sendToTarget();
            mUserResponse = null;
        }
    }

    // MTK-START [mtk80601][111215][ALPS00093395]
    private void updatePhb(AdnRecord adn, int type) {

        if (mPin2 != null) {
            mFh.mCi.supplyIccPin2(mPin2, obtainMessage(EVENT_VERIFY_PIN2, adn));
        } else {
            writeEntryToModem(adn, type);
        }

    }

    private boolean canUseGsm7Bit(String alphaId) {
        // try{
        // GsmAlphabet.countGsmSeptets(alphaId, true);
        // } catch(EncodeException ex)
        // {
        // return false;
        // }
        // return true;
        return (GsmAlphabet.countGsmSeptets(alphaId, true)) != null;
    }

    private String encodeATUCS(String input) {
        byte[] textPart;
        StringBuilder output;

        output = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            String hexInt = Integer.toHexString(input.charAt(i));
            for (int j = 0; j < (4 - hexInt.length()); j++)
                output.append("0");
            output.append(hexInt);
        }

        return output.toString();
    }

    private int getPhbStorageType(int ef) {
        int type = -1;
        switch (ef) {
            case IccConstants.EF_ADN:
                type = RILConstants.PHB_ADN;
                break;
            case IccConstants.EF_FDN:
                type = RILConstants.PHB_FDN;
                break;
            case IccConstants.EF_MSISDN:
                type = RILConstants.PHB_MSISDN;
                break;
            default:
                break;
        }
        return type;
    }

    private void writeEntryToModem(AdnRecord adn, int type) {
        int ton = 0x81;
        String number = adn.getNumber();
        String alphaId = adn.getAlphaTag();

        // eliminate '+' from number
        if (number.indexOf('+') != -1) {
            if (number.indexOf('+') != number.lastIndexOf('+')) {
                // there are multiple '+' in the String
                Rlog.d(LOG_TAG, "There are multiple '+' in the number: " + number);
            }
            ton = 0x91;

            number = number.replace("+", "");
        }
        // replace N with ?
        number = number.replace(PhoneNumberUtils.WILD, '?');
        // replace , with p
        number = number.replace(PhoneNumberUtils.PAUSE, 'p');
        // replace ; with w
        number = number.replace(PhoneNumberUtils.WAIT, 'w');

        // Add by mtk80995 replace \ to \5c and replace " to \22 for MTK modem
        // the order is very important! for "\\" is substring of "\\22"
        //alphaId = alphaId.replace("\\", "\\5c");
        //alphaId = alphaId.replace("\"", "\\22");
        // end Add by mtk80995

        // encode Alpha ID
        alphaId = encodeATUCS(alphaId);

        PhbEntry entry = new PhbEntry();
        if (!(number.equals("") && alphaId.equals("") && ton == 0x81)) {

            entry.type = type;
            entry.index = mRecordNumber;
            entry.number = number;
            entry.ton = ton;
            entry.alphaId = alphaId;
        } else {
            entry.type = type;
            entry.index = mRecordNumber;
            entry.number = null;
            entry.ton = ton;
            entry.alphaId = null;
        }

        // Rlog.d(LOG_TAG,"Update Entry: " + entry);

        mFh.mCi.writePhbEntry(entry,
                obtainMessage(EVENT_UPDATE_PHB_RECORD_DONE));

    }

    private void readEntryFromModem(int type, int[] readInfo) {

        if (readInfo.length != 3) {
            Rlog.e(LOG_TAG, "readEntryToModem, invalid paramters:" + readInfo.length);
            return;
        }

        // readInfo[0] : current_index;
        // readInfo[1] : # of remaining entries
        // readInfo[2] : # of total entries

        int eIndex;
        int count;

        eIndex = readInfo[0] + RILConstants.PHB_MAX_ENTRY - 1;
        if (eIndex > readInfo[2]) {
            eIndex = readInfo[2];
        }

        mFh.mCi.ReadPhbEntry(type, readInfo[0], eIndex,
                obtainMessage(EVENT_PHB_LOAD_ALL_DONE, readInfo));
    }

    private AdnRecord getAdnRecordFromPhbEntry(PhbEntry entry) {

        Rlog.d(LOG_TAG, "Parse Adn entry :" + entry);

        String alphaId;
        byte[] ba = IccUtils.hexStringToBytes(entry.alphaId);
        if (ba == null) {
            Rlog.e(LOG_TAG, "entry.alphaId is null");
            return null;
        }

        try {
            alphaId = new String(ba, 0, entry.alphaId.length() / 2, "utf-16be");
        } catch (UnsupportedEncodingException ex) {
            Rlog.e(LOG_TAG, "implausible UnsupportedEncodingException",
                    ex);
            return null;
        }
        // Rlog.d(LOG_TAG, "Decode ADN alphaId: " + alphaId);

        String number;
        if (entry.ton == PhoneNumberUtils.TOA_International) {
            number = ((mPhoneNumberExt == null) ? entry.number : mPhoneNumberExt.prependPlusToNumber(entry.number));
        } else {
            number = entry.number;
        }

        // replace ? with N
        number = number.replace('?', PhoneNumberUtils.WILD);
        // replace p with ,
        number = number.replace('p', PhoneNumberUtils.PAUSE);
        // replace w with ;
        number = number.replace('w', PhoneNumberUtils.WAIT);

        // Rlog.d(LOG_TAG, "Decode ADN number: " + number);

        return new AdnRecord(mEf, entry.index, alphaId, number);

    }
    // MTK-END [mtk80601][111215][ALPS00093395]
}
