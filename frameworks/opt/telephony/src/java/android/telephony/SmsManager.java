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

package android.telephony;

import android.app.ActivityThread;
import android.app.PendingIntent;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;

import com.android.internal.telephony.ISms;
import com.android.internal.telephony.SmsRawData;
import com.android.internal.telephony.uicc.IccConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// MTK-START
import android.app.PendingIntent.CanceledException;
import android.os.Bundle;
import android.os.SystemProperties;
import com.android.internal.telephony.PhoneConstants;
import android.telephony.Rlog;
import android.telephony.SimSmsInsertStatus;
import android.telephony.SmsParameters;
import android.telephony.PhoneNumberUtils;
import com.mediatek.common.telephony.IccSmsStorageStatus;
/// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.telephony.IOnlyOwnerSimSupport;
/// @}
// MTK-END

/*
 * TODO(code review): Curious question... Why are a lot of these
 * methods not declared as static, since they do not seem to require
 * any local object state?  Presumably this cannot be changed without
 * interfering with the API...
 */

/**
 * Manages SMS operations such as sending data, text, and pdu SMS messages.
 * Get this object by calling the static method {@link #getDefault()}.
 *
 * <p>For information about how to behave as the default SMS app on Android 4.4 (API level 19)
 * and higher, see {@link android.provider.Telephony}.
 */
public final class SmsManager {
    /** Singleton object constructed during class initialization. */
    private static final SmsManager sInstance = new SmsManager();
    // MTK-START
    /* mtk added by mtk80589 in 2011.11.16 */
    private static final String TAG = "SMS";

    private static int lastReceivedSmsSimId = PhoneConstants.GEMINI_SIM_1;

    private static final int TEST_MODE_CTA = 1;
    private static final int TEST_MODE_FTA = 2;
    private static final int TEST_MODE_IOT = 3;
    private static final int TEST_MODE_UNKNOWN = -1;

    private static final String TEST_MODE_PROPERTY_KEY = "gsm.gcf.testmode";
    private static final String TEST_MODE_PROPERTY_KEY2 = "gsm.gcf.testmode2";

    private int testMode = 0;
    /* mtk added by mtk80589 in 2011.11.16 */

    /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT 
    private IOnlyOwnerSimSupport mOnlyOwnerSimSupport = null;
    // MTK-END

    /**
     * Send a text based SMS.
     *
     * <p class="note"><strong>Note:</strong> Using this method requires that your app has the
     * {@link android.Manifest.permission#SEND_SMS} permission.</p>
     *
     * <p class="note"><strong>Note:</strong> Beginning with Android 4.4 (API level 19), if
     * <em>and only if</em> an app is not selected as the default SMS app, the system automatically
     * writes messages sent using this method to the SMS Provider (the default SMS app is always
     * responsible for writing its sent messages to the SMS Provider). For information about
     * how to behave as the default SMS app, see {@link android.provider.Telephony}.</p>
     *
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *  the current default SMSC
     * @param text the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK</code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     *
     * @throws IllegalArgumentException if destinationAddress or text are empty
     */
    public void sendTextMessage(
            String destinationAddress, String scAddress, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }

        // MTK-START, Support empty content
        //if (TextUtils.isEmpty(text)) {
        //    throw new IllegalArgumentException("Invalid message body");
        //}
        // MTK-END

        // MTK-START
        Rlog.d(TAG, "sendTextMessage, text="+text+", destinationAddress="+destinationAddress);
        String isms = getSmsServiceName(getDefaultSim());
        
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            mOnlyOwnerSimSupport.intercept(sentIntent,RESULT_ERROR_GENERIC_FAILURE);
            Rlog.d(TAG, "sendTextMessage return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return ;
        }
        /// @}

        if (!isValidParameters(destinationAddress, text, sentIntent)) {
            return;
        }
        // MTK-END

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                iccISms.sendText(ActivityThread.currentPackageName(), destinationAddress,
                        scAddress, text, sentIntent, deliveryIntent);
            }
        } catch (RemoteException ex) {
            Rlog.d(TAG, "sendTextMessage, RemoteException!");
        }
    }

    /**
     * Divide a message text into several fragments, none bigger than
     * the maximum SMS message size.
     *
     * @param text the original message.  Must not be null.
     * @return an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     *
     * @throws IllegalArgumentException if text is null
     */
    public ArrayList<String> divideMessage(String text) {
        if (null == text) {
            throw new IllegalArgumentException("text is null");
        }
        return SmsMessage.fragmentText(text);
    }

    /**
     * Send a multi-part text based SMS.  The callee should have already
     * divided the message into correctly sized parts by calling
     * <code>divideMessage</code>.
     *
     * <p class="note"><strong>Note:</strong> Using this method requires that your app has the
     * {@link android.Manifest.permission#SEND_SMS} permission.</p>
     *
     * <p class="note"><strong>Note:</strong> Beginning with Android 4.4 (API level 19), if
     * <em>and only if</em> an app is not selected as the default SMS app, the system automatically
     * writes messages sent using this method to the SMS Provider (the default SMS app is always
     * responsible for writing its sent messages to the SMS Provider). For information about
     * how to behave as the default SMS app, see {@link android.provider.Telephony}.</p>
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK</code> for success,
     *   or one of these errors:<br>
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *   <code>RESULT_ERROR_RADIO_OFF</code><br>
     *   <code>RESULT_ERROR_NULL_PDU</code><br>
     *   For <code>RESULT_ERROR_GENERIC_FAILURE</code> each sentIntent may include
     *   the extra "errorCode" containing a radio technology specific value,
     *   generally only useful for troubleshooting.<br>
     *   The per-application based SMS control checks sentIntent. If sentIntent
     *   is NULL the caller will be checked against all unknown applications,
     *   which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     *
     * @throws IllegalArgumentException if destinationAddress or data are empty
     */
    public void sendMultipartTextMessage(
            String destinationAddress, String scAddress, ArrayList<String> parts,
            ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        // MTK-START, Support empty content
        //if (parts == null || parts.size() < 1) {
        //    throw new IllegalArgumentException("Invalid message body");
        //}
        // MTK-END

        // MTK-START
        Rlog.d(TAG, "sendMultipartTextMessage, destinationAddress="+destinationAddress);
        String isms = getSmsServiceName(getDefaultSim());
        
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            mOnlyOwnerSimSupport.intercept(sentIntents,RESULT_ERROR_GENERIC_FAILURE);
            Rlog.d(TAG, "sendMultipartTextMessage return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return ;
        }
        /// @}

        if (!isValidParameters(destinationAddress, parts, sentIntents)) {
            return;
        }
        // MTK-END

        if (parts.size() > 1) {
            try {
                ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
                if (iccISms != null) {
                    iccISms.sendMultipartText(ActivityThread.currentPackageName(),
                            destinationAddress, scAddress, parts,
                            sentIntents, deliveryIntents);
                }
            } catch (RemoteException ex) {
                Rlog.d(TAG, "sendMultipartTextMessage, RemoteException!");
            }
        } else {
            PendingIntent sentIntent = null;
            PendingIntent deliveryIntent = null;
            if (sentIntents != null && sentIntents.size() > 0) {
                sentIntent = sentIntents.get(0);
            }
            if (deliveryIntents != null && deliveryIntents.size() > 0) {
                deliveryIntent = deliveryIntents.get(0);
            }
            String text = (parts == null || parts.size() == 0) ? "" : parts.get(0);
            sendTextMessage(destinationAddress, scAddress, text,
                    sentIntent, deliveryIntent);
        }
    }

    /**
     * Send a data based SMS to a specific application port.
     *
     * <p class="note"><strong>Note:</strong> Using this method requires that your app has the
     * {@link android.Manifest.permission#SEND_SMS} permission.</p>
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *  the current default SMSC
     * @param destinationPort the port to deliver the message to
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK</code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     *
     * @throws IllegalArgumentException if destinationAddress or data are empty
     */
    public void sendDataMessage(
            String destinationAddress, String scAddress, short destinationPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }

        // MTK-START, Support empty content
        //if (data == null || data.length == 0) {
        //    throw new IllegalArgumentException("Invalid message data");
        //}
        // MTK-END

        // MTK-START
        Rlog.d(TAG, "sendDataMessage, destinationAddress="+destinationAddress);
        String isms = getSmsServiceName(getDefaultSim());

        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            mOnlyOwnerSimSupport.intercept(sentIntent,RESULT_ERROR_GENERIC_FAILURE);
            Rlog.d(TAG, "sendDataMessage return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return ;
        }
        /// @}

        if (!isValidParameters(destinationAddress, "send_data", sentIntent)) {
            return;
        }
        // MTK-END

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        }

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                iccISms.sendData(ActivityThread.currentPackageName(),
                        destinationAddress, scAddress, destinationPort & 0xFFFF,
                        data, sentIntent, deliveryIntent);
            }
        } catch (RemoteException ex) {
            Rlog.d(TAG, "sendDataMessage, RemoteException!");
        }
    }

    /**
     * Get the default instance of the SmsManager
     *
     * @return the default instance of the SmsManager
     */
    public static SmsManager getDefault() {
        return sInstance;
    }

    private SmsManager() {
        // MTK-START
        /* mtk added by mtk80589 in 2011.11.16 */
        // get test mode from SystemProperties
        try {
            if(getDefaultSim() == PhoneConstants.GEMINI_SIM_1) {
                Rlog.d(TAG, "SM-constructor: get test mode from SIM 1");
                testMode = Integer.valueOf(SystemProperties.get(TEST_MODE_PROPERTY_KEY)).intValue();
            } else {
                Rlog.d(TAG, "SM-constructor: get test mode from SIM 2");
                // testMode = Integer.valueOf(SystemProperties.get(TEST_MODE_PROPERTY_KEY2)).intValue();
                testMode = Integer.valueOf(SystemProperties.get(TEST_MODE_PROPERTY_KEY)).intValue();
            }
        } catch(NumberFormatException e) {
            Rlog.d(TAG, "SM-constructor: invalid property value");
            testMode = TEST_MODE_UNKNOWN;
        }
        Rlog.d(TAG, "SM-constructor: test mode is " + testMode);
        /* mtk added by mtk80589 in 2011.11.16 */
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        mOnlyOwnerSimSupport = MediatekClassFactory.createInstance(IOnlyOwnerSimSupport.class);
        if (mOnlyOwnerSimSupport != null) {
            String actualClassName = mOnlyOwnerSimSupport.getClass().getName();
            Rlog.d(TAG, "initial mOnlyOwnerSimSupport done, actual class name is " + actualClassName);
        } else {
            Rlog.e(TAG, "FAIL! intial mOnlyOwnerSimSupport");
        }
        /// @}
        // MTK-END
    }

    /**
     * Copy a raw SMS PDU to the ICC.
     * ICC (Integrated Circuit Card) is the card of the device.
     * For example, this can be the SIM or USIM for GSM.
     *
     * @param smsc the SMSC for this message, or NULL for the default SMSC
     * @param pdu the raw PDU to store
     * @param status message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *               STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @return true for success
     *
     * @throws IllegalArgumentException if pdu is NULL
     * {@hide}
     */
    public boolean copyMessageToIcc(byte[] smsc, byte[] pdu, int status) {
        Rlog.d(TAG, "copyMessageToIcc");
        boolean success = false;
        // MTK-START
        String isms = getSmsServiceName(getDefaultSim());
        SimSmsInsertStatus smsStatus = null;
        // MTK-END

        if (null == pdu) {
            throw new IllegalArgumentException("pdu is NULL");
        }

        // MTK-START
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            Rlog.d(TAG, "copyMessageToIcc return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return false ;
        }
        /// @}
        // MTK-END

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                //success = iccISms.copyMessageToIccEf(ActivityThread.currentPackageName(),
                //        status, pdu, smsc);
                // MTK-START
                smsStatus = iccISms.insertRawMessageToIccCard(ActivityThread.currentPackageName(), status, pdu, smsc);
                // MTK-END
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        // MTK-START
        Rlog.d(TAG, (smsStatus != null) ? "insert Raw " + smsStatus.indexInIcc : "insert Raw null");
        
        if(smsStatus != null) {
            int[] index = smsStatus.getIndex();

            if (index != null && index.length > 0) {
                success = true;/*index[0];*/
            }
        }
        // MTK-END

        return success;
    }

    /**
     * Delete the specified message from the ICC.
     * ICC (Integrated Circuit Card) is the card of the device.
     * For example, this can be the SIM or USIM for GSM.
     *
     * @param messageIndex is the record index of the message on ICC
     * @return true for success
     *
     * {@hide}
     */
    public boolean
    deleteMessageFromIcc(int messageIndex) {
        Rlog.d(TAG, "deleteMessageFromIcc, messageIndex="+messageIndex);
        boolean success = false;
        // MTK-START
        String isms = getSmsServiceName(getDefaultSim());
    
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            Rlog.d(TAG, "deleteMessageFromIcc return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return false ;
        }
        /// @}
        // MTK-END

        byte[] pdu = new byte[IccConstants.SMS_RECORD_LENGTH-1];
        Arrays.fill(pdu, (byte)0xff);

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                success = iccISms.updateMessageOnIccEf(ActivityThread.currentPackageName(),
                        messageIndex, STATUS_ON_ICC_FREE, pdu);
            }
        } catch (RemoteException ex) {
            Rlog.d(TAG, "deleteMessageFromIcc, RemoteException!");
        }

        return success;
    }

    /**
     * Update the specified message on the ICC.
     * ICC (Integrated Circuit Card) is the card of the device.
     * For example, this can be the SIM or USIM for GSM.
     *
     * @param messageIndex record index of message to update
     * @param newStatus new message status (STATUS_ON_ICC_READ,
     *                  STATUS_ON_ICC_UNREAD, STATUS_ON_ICC_SENT,
     *                  STATUS_ON_ICC_UNSENT, STATUS_ON_ICC_FREE)
     * @param pdu the raw PDU to store
     * @return true for success
     *
     * {@hide}
     */
    public boolean updateMessageOnIcc(int messageIndex, int newStatus, byte[] pdu) {
        Rlog.d(TAG, "updateMessageOnIcc, messageIndex="+messageIndex);
        boolean success = false;
        // MTK-START
        String isms = getSmsServiceName(getDefaultSim());

        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            Rlog.d(TAG, "updateMessageOnIcc return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return false ;
        }
        /// @}
        // MTK-END

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                success = iccISms.updateMessageOnIccEf(ActivityThread.currentPackageName(),
                        messageIndex, newStatus, pdu);
            }
        } catch (RemoteException ex) {
            Rlog.d(TAG, "updateMessageOnIcc, RemoteException!");
        }

        return success;
    }

    /**
     * Retrieves all messages currently stored on ICC.
     * ICC (Integrated Circuit Card) is the card of the device.
     * For example, this can be the SIM or USIM for GSM.
     *
     * @return <code>ArrayList</code> of <code>SmsMessage</code> objects
     *
     * {@hide}
     */
    public static ArrayList<SmsMessage> getAllMessagesFromIcc() {
        Rlog.d(TAG, "getAllMessagesFromIcc");
        List<SmsRawData> records = null;
        // MTK-START
        String isms = getSmsServiceName(getDefault().getDefaultSim());
        // MTK-END

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                records = iccISms.getAllMessagesFromIccEf(ActivityThread.currentPackageName());
            }
        } catch (RemoteException ex) {
            Rlog.d(TAG, "getAllMessagesFromIcc, RemoteException!");
        }

        return createMessageListFromRawRecords(records);
    }

    /**
     * Enable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier. Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages. All received messages will be broadcast in an
     * intent with the action "android.provider.Telephony.SMS_CB_RECEIVED".
     * Note: This call is blocking, callers may want to avoid calling it from
     * the main thread of an application.
     *
     * @param messageIdentifier Message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     * @see #disableCellBroadcast(int)
     *
     * {@hide}
     */
    public boolean enableCellBroadcast(int messageIdentifier) {
        boolean success = false;
        // MTK-START
        String isms = getSmsServiceName(getDefaultSim());
        // MTK-END

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                success = iccISms.enableCellBroadcast(messageIdentifier);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Disable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier. Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages.
     * Note: This call is blocking, callers may want to avoid calling it from
     * the main thread of an application.
     *
     * @param messageIdentifier Message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     *
     * @see #enableCellBroadcast(int)
     *
     * {@hide}
     */
    public boolean disableCellBroadcast(int messageIdentifier) {
        boolean success = false;
        // MTK-START
        String isms = getSmsServiceName(getDefaultSim());
        // MTK-END

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                success = iccISms.disableCellBroadcast(messageIdentifier);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Enable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier range. Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages. All received messages will be broadcast in an
     * intent with the action "android.provider.Telephony.SMS_CB_RECEIVED".
     * Note: This call is blocking, callers may want to avoid calling it from
     * the main thread of an application.
     *
     * @param startMessageId first message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @param endMessageId last message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     * @see #disableCellBroadcastRange(int, int)
     *
     * @throws IllegalArgumentException if endMessageId < startMessageId
     * {@hide}
     */
    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId) {
        Rlog.d(TAG, "enableCellBroadcastRange, " + startMessageId + "-" + endMessageId);
        // MTK-START
        boolean success = false;
        String isms = getSmsServiceName(getDefaultSim());
        // MTK-END

        if (endMessageId < startMessageId) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                success = iccISms.enableCellBroadcastRange(startMessageId, endMessageId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Disable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier range. Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages.
     * Note: This call is blocking, callers may want to avoid calling it from
     * the main thread of an application.
     *
     * @param startMessageId first message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @param endMessageId last message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     *
     * @see #enableCellBroadcastRange(int, int)
     *
     * @throws IllegalArgumentException if endMessageId < startMessageId
     * {@hide}
     */
    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId) {
        Rlog.d(TAG, "disableCellBroadcastRange, " + startMessageId + "-" + endMessageId);
        boolean success = false;
        // MTK-START
        String isms = getSmsServiceName(getDefaultSim());
        // MTK-END

        if (endMessageId < startMessageId) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                success = iccISms.disableCellBroadcastRange(startMessageId, endMessageId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Create a list of <code>SmsMessage</code>s from a list of RawSmsData
     * records returned by <code>getAllMessagesFromIcc()</code>
     *
     * @param records SMS EF records, returned by
     *   <code>getAllMessagesFromIcc</code>
     * @return <code>ArrayList</code> of <code>SmsMessage</code> objects.
     */
    private static ArrayList<SmsMessage> createMessageListFromRawRecords(List<SmsRawData> records) {
        ArrayList<SmsMessage> messages = new ArrayList<SmsMessage>();
        Rlog.d(TAG, "createMessageListFromRawRecords");

        if (records != null) {
            int count = records.size();
            for (int i = 0; i < count; i++) {
                SmsRawData data = records.get(i);
                // List contains all records, including "free" records (null)
                if (data != null) {
                    SmsMessage sms = SmsMessage.createFromEfRecord(i+1, data.getBytes());
                    if (sms != null) {
                        messages.add(sms);
                    }
                }
            }
        }
        return messages;
    }

    /**
     * SMS over IMS is supported if IMS is registered and SMS is supported
     * on IMS.
     *
     * @return true if SMS over IMS is supported, false otherwise
     *
     * @see #getImsSmsFormat()
     *
     * @hide
     */
    boolean isImsSmsSupported() {
        boolean boSupported = false;
        // MTK-START
        String isms = getSmsServiceName(getDefaultSim());
        // MTK-END
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                boSupported = iccISms.isImsSmsSupported();
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return boSupported;
    }

    /**
     * Gets SMS format supported on IMS.  SMS over IMS format is
     * either 3GPP or 3GPP2.
     *
     * @return SmsMessage.FORMAT_3GPP,
     *         SmsMessage.FORMAT_3GPP2
     *      or SmsMessage.FORMAT_UNKNOWN
     *
     * @see #isImsSmsSupported()
     *
     * @hide
     */
    String getImsSmsFormat() {
        String format = com.android.internal.telephony.SmsConstants.FORMAT_UNKNOWN;
        // MTK-START
        String isms = getSmsServiceName(getDefaultSim());
        // MTK-END
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                format = iccISms.getImsSmsFormat();
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return format;
    }

    // see SmsMessage.getStatusOnIcc

    /** Free space (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_FREE      = 0;

    /** Received and read (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_READ      = 1;

    /** Received and unread (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_UNREAD    = 3;

    /** Stored and sent (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_SENT      = 5;

    /** Stored and unsent (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_UNSENT    = 7;

    // SMS send failure result codes

    /** Generic failure cause */
    static public final int RESULT_ERROR_GENERIC_FAILURE    = 1;
    /** Failed because radio was explicitly turned off */
    static public final int RESULT_ERROR_RADIO_OFF          = 2;
    /** Failed because no pdu provided */
    static public final int RESULT_ERROR_NULL_PDU           = 3;
    /** Failed because service is currently unavailable */
    static public final int RESULT_ERROR_NO_SERVICE         = 4;
    /** Failed because we reached the sending queue limit.  {@hide} */
    static public final int RESULT_ERROR_LIMIT_EXCEEDED     = 5;
    /** Failed because FDN is enabled. {@hide} */
    static public final int RESULT_ERROR_FDN_CHECK_FAILURE  = 6;

    // MTK-START
    // mtk added by mtk80589 in 2012.07.16
    /**
    * @hide
    */
    static public final int RESULT_ERROR_SIM_MEM_FULL = 7;
    /**
    * @hide
    */
    static public final int RESULT_ERROR_SUCCESS = 0;
    /**
    * @hide
    */
    static public final int RESULT_ERROR_INVALID_ADDRESS = 8;
    // mtk added end

    // mtk added by mtk80589 in 2012.07.16
    // for SMS validity period feature
    /**
    * @hide
    */
    public static final String EXTRA_PARAMS_VALIDITY_PERIOD = "validity_period";

    /**
    * @hide
    */
    public static final String EXTRA_PARAMS_ENCODING_TYPE = "encoding_type";

    /**
    * @hide
    */
    public static final int VALIDITY_PERIOD_NO_DURATION = -1;

    /**
    * @hide
    */
    public static final int VALIDITY_PERIOD_ONE_HOUR = 11; // (VP + 1) * 5 = 60 Mins

    /**
    * @hide
    */
    public static final int VALIDITY_PERIOD_SIX_HOURS = 71; // (VP + 1) * 5 = 6 * 60 Mins

    /**
    * @hide
    */
    public static final int VALIDITY_PERIOD_TWELVE_HOURS = 143; // (VP + 1) * 5 = 12 * 60 Mins

    /**
    * @hide
    */
    public static final int VALIDITY_PERIOD_ONE_DAY = 167; // 12 + (VP - 143) * 30 Mins = 24 Hours

    /**
    * @hide
    */
    public static final int VALIDITY_PERIOD_MAX_DURATION = 255; // (VP - 192) Weeks

    /**
     * Send a data based SMS to a specific application port.
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *  the current default SMSC
     * @param destinationPort the port to deliver the message to
     * @param originalPort the port to deliver the message from
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applicaitons,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     *
     * @throws IllegalArgumentException if destinationAddress or data are empty
     *
     * @hide
     */
    public void sendDataMessage(
        String destinationAddress, String scAddress, short destinationPort, 
        short originalPort, byte[] data, PendingIntent sentIntent,
        PendingIntent deliveryIntent) {
        Rlog.d(TAG, "sendDataMessage, destinationAddress="+destinationAddress);
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        String isms = getSmsServiceName(getDefaultSim());

        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            mOnlyOwnerSimSupport.intercept(sentIntent,RESULT_ERROR_GENERIC_FAILURE);
            Rlog.d(TAG, "sendDataMessage return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return ;
        }
        /// @}

        if (!isValidParameters(destinationAddress, "send_data", sentIntent)) {
            return;
        }

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        }

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                iccISms.sendDataWithOriginalPort(ActivityThread.currentPackageName(),
                        destinationAddress, scAddress, destinationPort & 0xFFFF,
                        originalPort & 0xFFFF, data, sentIntent, deliveryIntent);
            }
        } catch (RemoteException ex) {
            Rlog.d(TAG, "sendDataMessage, RemoteException!");
        }

    }

    /**
     * Copy a text SMS to the ICC.
     *
     * @param scAddress Service center address
     * @param address   Destination address or original address
     * @param text      List of message text
     * @param status    message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *                  STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @param timestamp Timestamp when service center receive the message
     * @return success or not
     *
     * @hide
     */
    public int copyTextMessageToIccCard(String scAddress, String address, List<String> text,
            int status, long timestamp) {
        Rlog.d(TAG, "copyTextMessageToIccCard");
        int result = SmsManager.RESULT_ERROR_GENERIC_FAILURE;
        String isms = getSmsServiceName(getDefaultSim());

        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            Rlog.d(TAG, "copyTextMessageToIccCard return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return result;
        }
        /// @}

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                result = iccISms.copyTextMessageToIccCard(
                    ActivityThread.currentPackageName(), scAddress, address, text, status, timestamp);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return result;

    }

    /**
     * Get the default SIM id
     */
    private int getDefaultSim() {
        return TelephonyManager.getDefault().getSmsDefaultSim();
    }

    /**
     * Send a text based SMS.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *  the current default SMSC
     * @param text the body of the message to send
     * @param encodingType the encoding type of message(gsm 7-bit, unicode or automatic)
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     *
     * @throws IllegalArgumentException if destinationAddress or text are empty
     * @hide
     */
    public void sendTextMessageWithEncodingType(
            String destAddr,
            String scAddr,
            String text,
            int encodingType,
            PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
        Rlog.d(TAG, "sendTextMessageWithEncodingType, text="+text+", encoding="+encodingType);
        if (TextUtils.isEmpty(destAddr)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        String isms = getSmsServiceName(getDefaultSim());
        
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            mOnlyOwnerSimSupport.intercept(sentIntent,RESULT_ERROR_GENERIC_FAILURE);
            Rlog.d(TAG, "sendTextMessageWithEncodingType return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return ;
        }
        /// @}

        if (!isValidParameters(destAddr, text, sentIntent)) {
            Rlog.d(TAG, "the parameters are invalid");
            return;
        }

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                Rlog.d(TAG, "call ISms interface to send text message");
                iccISms.sendTextWithEncodingType(ActivityThread.currentPackageName(),
                    destAddr, scAddr, text, encodingType, sentIntent, deliveryIntent);
            } else {
                Rlog.d(TAG, "iccISms is null");
            }
        } catch (RemoteException ex) {
            // ignore it
            Rlog.d(TAG, "fail to get ISms");
        }

    }

    /**
     * Send a multi-part text based SMS.  The callee should have already
     * divided the message into correctly sized parts by calling
     * <code>divideMessage</code>.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param encodingType the encoding type of message(gsm 7-bit, unicode or automatic)
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK<code> for success,
     *   or one of these errors:<br>
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *   <code>RESULT_ERROR_RADIO_OFF</code><br>
     *   <code>RESULT_ERROR_NULL_PDU</code><br>
     *   For <code>RESULT_ERROR_GENERIC_FAILURE</code> each sentIntent may include
     *   the extra "errorCode" containing a radio technology specific value,
     *   generally only useful for troubleshooting.<br>
     *   The per-application based SMS control checks sentIntent. If sentIntent
     *   is NULL the caller will be checked against all unknown applicaitons,
     *   which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     *
     * @throws IllegalArgumentException if destinationAddress or data are empty
     * @hide
     */
    public void sendMultipartTextMessageWithEncodingType(
            String destAddr,
            String scAddr,
            ArrayList<String> parts,
            int encodingType,
            ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
        Rlog.d(TAG, "sendMultipartTextMessageWithEncodingType, encoding="+encodingType); 
        if (TextUtils.isEmpty(destAddr)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        String isms = getSmsServiceName(getDefaultSim());

        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            mOnlyOwnerSimSupport.intercept(sentIntents,RESULT_ERROR_GENERIC_FAILURE);
            Rlog.d(TAG, "sendMultipartTextMessageWithEncodingType return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return ;
        }
        /// @}

        if (!isValidParameters(destAddr, parts, sentIntents)) {
            Rlog.d(TAG, "invalid parameters for multipart message");
            return;
        }

        if (parts.size() > 1) {
            try {
                ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
                if (iccISms != null) {
                    Rlog.d(TAG, "call ISms.sendMultipartText");
                    iccISms.sendMultipartTextWithEncodingType(ActivityThread.currentPackageName(),
                            destAddr, scAddr, parts, encodingType, sentIntents, deliveryIntents);
                }
            } catch (RemoteException ex) {
                // ignore it
            }
        } else {
            PendingIntent sentIntent = null;
            PendingIntent deliveryIntent = null;
            if (sentIntents != null && sentIntents.size() > 0) {
                sentIntent = sentIntents.get(0);
            }
            Rlog.d(TAG, "get sentIntent: " + sentIntent);
            if (deliveryIntents != null && deliveryIntents.size() > 0) {
                deliveryIntent = deliveryIntents.get(0);
            }
            Rlog.d(TAG, "send single message");
            if (parts != null) {
                Rlog.d(TAG, "parts.size = " + parts.size());
            }
            String text = (parts == null || parts.size() == 0) ? "" : parts.get(0);
            Rlog.d(TAG, "pass encoding type " + encodingType);
            sendTextMessageWithEncodingType(destAddr, scAddr, text, encodingType, 
                sentIntent, deliveryIntent);
        }

    }

    /**
     * Divide a message text into several fragments, none bigger than
     * the maximum SMS message size.
     *
     * @param text the original message.  Must not be null.
     * @param encodingType text encoding type(7-bit, 16-bit or automatic)
     * @return an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @hide
     */
    public ArrayList<String> divideMessage(String text, int encodingType) {
        Rlog.d(TAG, "divideMessage, encoding = " + encodingType);
        ArrayList<String> ret = SmsMessage.fragmentText(text, encodingType);
        Rlog.d(TAG, "divideMessage: size = " + ret.size());
        return ret;
    }

    /**
     * insert a text SMS to the ICC.
     *
     * @param scAddress Service center address
     * @param address   Destination address or original address
     * @param text      List of message text
     * @param status    message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *                  STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @param timestamp Timestamp when service center receive the message
     * @return SimSmsInsertStatus
     * @hide
     */
    public SimSmsInsertStatus insertTextMessageToIccCard(String scAddress, String address, List<String> text,
            int status, long timestamp) {
        Rlog.d(TAG, "insertTextMessageToIccCard"); 
        SimSmsInsertStatus ret = null;
        String isms = getSmsServiceName(getDefaultSim()); 
        
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            Rlog.d(TAG, "insertTextMessageToIccCard return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return null;
        }
        /// @}

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if(iccISms != null) {
                ret = iccISms.insertTextMessageToIccCard(ActivityThread.currentPackageName(), scAddress, address, text, status, timestamp);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        Rlog.d(TAG, (ret != null) ? "insert Text " + ret.indexInIcc : "insert Text null");
        return ret;
        
    }

    /**
     * Copy a raw SMS PDU to the ICC.
     *
     * @param status message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *               STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @param pdu the raw PDU to store
     * @param smsc encoded smsc service center
     * @return SimSmsInsertStatus
     * @hide
     */
    public SimSmsInsertStatus insertRawMessageToIccCard(int status, byte[] pdu, byte[] smsc) {
        Rlog.d(TAG, "insertRawMessageToIccCard");
        SimSmsInsertStatus ret = null;
        String isms = getSmsServiceName(getDefaultSim());

        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            Rlog.d(TAG, "insertRawMessageToIccCard return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return null;
        }
        /// @}

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                ret = iccISms.insertRawMessageToIccCard(ActivityThread.currentPackageName(), status, pdu, smsc);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        Rlog.d(TAG, (ret != null) ? "insert Raw " + ret.indexInIcc : "insert Raw null");
        return ret;
    }

    /**
     * Send an SMS with specified encoding type.
     *
     * @param destAddr the address to send the message to
     * @param scAddr the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param text the body of the message to send
     * @param extraParams extra parameters, such as validity period, encoding type
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @hide
     */
    public void sendTextMessageWithExtraParams(
            String destAddr, String scAddr, String text, Bundle extraParams,
            PendingIntent sentIntent, PendingIntent deliveryIntent) {
        Rlog.d(TAG, "sendTextMessageWithExtraParams, text="+text);
        if (TextUtils.isEmpty(destAddr)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        String serviceName = getSmsServiceName(getDefaultSim());

        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            mOnlyOwnerSimSupport.intercept(sentIntent,RESULT_ERROR_GENERIC_FAILURE);
            Rlog.d(TAG, "sendTextMessageWithExtraParams return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return ;
        }
        /// @}

        if (!isValidParameters(destAddr, text, sentIntent)) {
            return;
        }

        if (extraParams == null) {
            Rlog.d(TAG, "bundle is null");
            return;
        }

        try {
            ISms service = ISms.Stub.asInterface(ServiceManager.getService(serviceName));
            if (service != null) {
                service.sendTextWithExtraParams(ActivityThread.currentPackageName(), 
                        destAddr, scAddr, text, extraParams, sentIntent, deliveryIntent);
            }
        } catch (RemoteException e) {
            Rlog.d(TAG, "fail to call sendTextWithExtraParams: " + e);
        }

    }

     /**
     * Send a multi-part text based SMS with specified encoding type.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param extraParams extra parameters, such as validity period, encoding type
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     * @hide
     */
    public void sendMultipartTextMessageWithExtraParams(String destAddr,
            String scAddr, ArrayList<String> parts, Bundle extraParams,
            ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
        Rlog.d(TAG, "sendMultipartTextMessageWithExtraParams");
        if (TextUtils.isEmpty(destAddr)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        String serviceName = getSmsServiceName(getDefaultSim());

        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            mOnlyOwnerSimSupport.intercept(sentIntents,RESULT_ERROR_GENERIC_FAILURE);
            Rlog.d(TAG, "sendMultipartTextMessageWithExtraParams return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return ;
        }
        /// @}

        if (!isValidParameters(destAddr, parts, sentIntents)) {
            return;
        }

        if (extraParams == null) {
            Rlog.d(TAG, "bundle is null");
            return;
        }

        if (parts.size() > 1) {
            try {
                ISms service = ISms.Stub.asInterface(ServiceManager.getService(serviceName));
                if (service != null) {
                    service.sendMultipartTextWithExtraParams(ActivityThread.currentPackageName(),
                        destAddr, scAddr, parts, extraParams, sentIntents, deliveryIntents);
                }
            } catch (RemoteException e) {
                Rlog.d(TAG, "fail to call sendMultipartTextWithExtraParams: " + e);
            }
        } else {
            PendingIntent sentIntent = null;
            PendingIntent deliveryIntent = null;
            if (sentIntents != null && sentIntents.size() > 0) {
                sentIntent = sentIntents.get(0);
            }
            if (deliveryIntents != null && deliveryIntents.size() > 0) {
                deliveryIntent = deliveryIntents.get(0);
            }

            sendTextMessageWithExtraParams(destAddr, scAddr, parts.get(0),
                    extraParams, sentIntent, deliveryIntent);
        }
        
    }

    /**
    * @hide
    */
    public SmsParameters getSmsParameters() {
        Rlog.d(TAG, "getSmsParameters");
        String isms = getSmsServiceName(getDefaultSim());

        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            Rlog.d(TAG, "getSmsParameters return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return null;
        }
        /// @}

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                return iccISms.getSmsParameters(ActivityThread.currentPackageName());
            } else {
                return null;
            }
        } catch (RemoteException ex) {
            Rlog.d(TAG, "fail because of RemoteException");
        }

        Rlog.d(TAG, "fail to get SmsParameters");
        return null;

    }

    /**
    * @hide
    */
    public boolean setSmsParameters(SmsParameters params) {
        Rlog.d(TAG, "setSmsParameters");

        String isms = getSmsServiceName(getDefaultSim());

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                return iccISms.setSmsParameters(ActivityThread.currentPackageName(), params);
            } else {
                return false;
            }
        } catch (RemoteException ex) {
            Rlog.d(TAG, "[EFsmsp fail because of RemoteException");
        }

        return false;

    }

    /**
    * @hide
    */
    public int copySmsToIcc (byte[] smsc, byte[] pdu, int status){
        Rlog.d(TAG, "copySmsToIcc");
        
        SimSmsInsertStatus smsStatus = insertRawMessageToIccCard(status, pdu, smsc);
        if (smsStatus == null) {
            return -1;
        }
        int[] index = smsStatus.getIndex();

        if (index != null && index.length > 0) {
            return index[0];
        }

        return -1;
    }

    /**
    * @hide
    */
    public boolean updateSmsOnSimReadStatus(int index, boolean read) {
        Rlog.d(TAG, "updateSmsOnSimReadStatus");
        SmsRawData record = null;
        String svcName = getSmsServiceName(getDefaultSim());

        try {
            ISms smsSvc = ISms.Stub.asInterface(ServiceManager.getService(svcName));
            if (smsSvc != null) {
                record = smsSvc.getMessageFromIccEf(ActivityThread.currentPackageName(), index);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        if (record != null) {
            byte[] rawData = record.getBytes();
            int status = rawData[0] & 0xff;
            Rlog.d(TAG, "sms status is " + status);
            if (status != SmsManager.STATUS_ON_ICC_UNREAD &&
                    status != SmsManager.STATUS_ON_ICC_READ) {
                Rlog.d(TAG, "non-delivery sms " + status);
                return false;
            } else {
                if ((status == SmsManager.STATUS_ON_ICC_UNREAD && read == false)
                        || (status == SmsManager.STATUS_ON_ICC_READ && read == true)) {
                    Rlog.d(TAG, "no need to update status");
                    return true;
                } else {
                    Rlog.d(TAG, "update sms status as " + read);
                    int newStatus = ((read == true) ? SmsManager.STATUS_ON_ICC_READ
                            : SmsManager.STATUS_ON_ICC_UNREAD);
                    return updateMessageOnIcc(index, newStatus, rawData);
                }
            }
        } // end if(record != null)

        Rlog.d(TAG, "record is null");
        return false;
        
    }

    /**
    * @hide
    */
    public boolean setEtwsConfig(int mode) {
        Rlog.d(TAG, "setEtwsConfig, mode="+mode);
        boolean ret = false;
        String isms = getSmsServiceName(getDefaultSim());

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService(isms));
            if (iccISms != null) {
                ret = iccISms.setEtwsConfig(mode);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return ret;

    }

    /**
     * judge if the input destination address is a valid SMS address or not
     *
     * @param da the input destination address
     * @return true for success
     *
     */
    private static boolean isValidSmsDestinationAddress(String da) {
        String encodeAddress = PhoneNumberUtils.extractNetworkPortion(da);
        if (encodeAddress == null)
            return true;

        int spaceCount = 0;
        for (int i = 0; i < da.length(); ++i) {
            if (da.charAt(i) == ' ' || da.charAt(i) == '-') {
                spaceCount++;
            }
        }

        return encodeAddress.length() == (da.length() - spaceCount);
    }

    /**
     * Judge if the destination address is a valid SMS address or not, and if
     * the text is null or not
     *
     * @destinationAddress the destination address to which the message be sent
     * @text the content of shorm message
     * @sentIntent will be broadcast if the address or the text is invalid
     * @return true for valid parameters
     */
    private static boolean isValidParameters(
            String destinationAddress, String text, PendingIntent sentIntent) {
        // impl
        ArrayList<PendingIntent> sentIntents =
                new ArrayList<PendingIntent>();
        ArrayList<String> parts =
                new ArrayList<String>();

        sentIntents.add(sentIntent);
        parts.add(text);

        // if (TextUtils.isEmpty(text)) {
        // throw new IllegalArgumentException("Invalid message body");
        // }

        return isValidParameters(destinationAddress, parts, sentIntents);
    }

    /**
     * Judges if the destination address is a valid SMS address or not, and if
     * the text is null or not.
     *
     * @param destinationAddress The destination address to which the message be sent
     * @param parts The content of shorm message
     * @param sentIntent will be broadcast if the address or the text is invalid
     * @return True for valid parameters
     */
    private static boolean isValidParameters(
            String destinationAddress, ArrayList<String> parts,
            ArrayList<PendingIntent> sentIntents) {
        if (parts == null || parts.size() == 0) {
            return true;
        }

        if (!isValidSmsDestinationAddress(destinationAddress)) {
            for (int i = 0; i < sentIntents.size(); i++) {
                PendingIntent sentIntent = sentIntents.get(i);
                if (sentIntent != null) {
                    try {
                        sentIntent.send(SmsManager.RESULT_ERROR_GENERIC_FAILURE);
                    } catch (CanceledException ex) {}
                }
            }

            Rlog.d(TAG, "Invalid destinationAddress: " + destinationAddress);
            return false;
        }

        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (parts == null || parts.size() < 1) {
            throw new IllegalArgumentException("Invalid message body");
        }

        return true;
    }

    /**
     * Gets the SMS service name by specific SIM ID.
     *
     * @param slotId SIM card the user would like to access
     * @return The SMS service name
     */
    private static String getSmsServiceName(int slotId) {
        if (slotId == PhoneConstants.GEMINI_SIM_1) {
            return "isms";
        } else if (slotId == PhoneConstants.GEMINI_SIM_2) {
            return "isms2";
        } else if (slotId == PhoneConstants.GEMINI_SIM_3) {
            return "isms3";
        } else if (slotId == PhoneConstants.GEMINI_SIM_4) {
            return "isms4";
        } else {
            return null;
        }
    }
    // MTK-END
}
