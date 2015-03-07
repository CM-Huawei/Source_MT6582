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

package com.android.internal.telephony.gsm;

import android.telephony.PhoneNumberUtils;
import android.text.format.Time;
import android.telephony.Rlog;

import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsMessageBase;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;

import static com.android.internal.telephony.SmsConstants.MessageClass;
import static com.android.internal.telephony.SmsConstants.ENCODING_UNKNOWN;
import static com.android.internal.telephony.SmsConstants.ENCODING_7BIT;
import static com.android.internal.telephony.SmsConstants.ENCODING_8BIT;
import static com.android.internal.telephony.SmsConstants.ENCODING_16BIT;
import static com.android.internal.telephony.SmsConstants.ENCODING_KSC5601;
import static com.android.internal.telephony.SmsConstants.MAX_USER_DATA_SEPTETS;
import static com.android.internal.telephony.SmsConstants.MAX_USER_DATA_BYTES;
import static com.android.internal.telephony.SmsConstants.MAX_USER_DATA_BYTES_WITH_HEADER;

// MTK-START
import static com.android.internal.telephony.SmsConstants.MAX_USER_DATA_SEPTETS_WITH_HEADER;
import android.os.SystemProperties;
import android.util.Config;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import static android.telephony.SmsMessage.MWI_VOICEMAIL;
import static android.telephony.SmsMessage.MWI_FAX;
import static android.telephony.SmsMessage.MWI_EMAIL;
import static android.telephony.SmsMessage.MWI_OTHER;
import static android.telephony.SmsMessage.MWI_VIDEO;
// MTK-END

/**
 * A Short Message Service message.
 *
 */
public class SmsMessage extends SmsMessageBase {
    static final String LOG_TAG = "SmsMessage";
    private static final boolean VDBG = false;

    private MessageClass messageClass;

    /**
     * TP-Message-Type-Indicator
     * 9.2.3
     */
    private int mMti;

    /** TP-Protocol-Identifier (TP-PID) */
    private int mProtocolIdentifier;

    // TP-Data-Coding-Scheme
    // see TS 23.038
    private int mDataCodingScheme;

    // TP-Reply-Path
    // e.g. 23.040 9.2.2.1
    private boolean mReplyPathPresent = false;

    /** The address of the receiver. */
    private GsmSmsAddress mRecipientAddress;

    /**
     *  TP-Status - status of a previously submitted SMS.
     *  This field applies to SMS-STATUS-REPORT messages.  0 indicates success;
     *  see TS 23.040, 9.2.3.15 for description of other possible values.
     */
    private int mStatus;

    /**
     *  TP-Status - status of a previously submitted SMS.
     *  This field is true iff the message is a SMS-STATUS-REPORT message.
     */
    private boolean mIsStatusReportMessage = false;

    // MTK-START
    public static final int ENCODING_7BIT_SINGLE = 11;
    public static final int ENCODING_7BIT_LOCKING = 12;
    public static final int ENCODING_7BIT_LOCKING_SINGLE = 13;

    public static final int MASK_MESSAGE_TYPE_INDICATOR     = 0x03;
    public static final int MASK_VALIDITY_PERIOD_FORMAT     = 0x18;
    public static final int MASK_USER_DATA_HEADER_INDICATOR = 0x40;

    public static final int MASK_VALIDITY_PERIOD_FORMAT_NONE = 0x00;
    public static final int MASK_VALIDITY_PERIOD_FORMAT_RELATIVE = 0x10;
    public static final int MASK_VALIDITY_PERIOD_FORMAT_ENHANCED = 0x08;
    public static final int MASK_VALIDITY_PERIOD_FORMAT_ABSOLUTE = 0x18;
    // MTK-END

    public static class SubmitPdu extends SubmitPduBase {
    }
    // MTK-START
    public static class DeliverPdu extends DeliverPduBase {}
    // MTK-END

    /**
     * Create an SmsMessage from a raw PDU.
     */
    public static SmsMessage createFromPdu(byte[] pdu) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.parsePdu(pdu);
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        } catch (OutOfMemoryError e) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed with out of memory: ", e);
            return null;
        }
    }

    /**
     * 3GPP TS 23.040 9.2.3.9 specifies that Type Zero messages are indicated
     * by TP_PID field set to value 0x40
     */
    public boolean isTypeZero() {
        return (mProtocolIdentifier == 0x40);
    }

    /**
     * TS 27.005 3.4.1 lines[0] and lines[1] are the two lines read from the
     * +CMT unsolicited response (PDU mode, of course)
     *  +CMT: [&lt;alpha>],<length><CR><LF><pdu>
     *
     * Only public for debugging
     *
     * {@hide}
     */
    public static SmsMessage newFromCMT(String[] lines) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.parsePdu(IccUtils.hexStringToBytes(lines[1]));
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    /** @hide */
    public static SmsMessage newFromCDS(String line) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.parsePdu(IccUtils.hexStringToBytes(line));
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "CDS SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    /**
     * Create an SmsMessage from an SMS EF record.
     *
     * @param index Index of SMS record. This should be index in ArrayList
     *              returned by SmsManager.getAllMessagesFromSim + 1.
     * @param data Record data.
     * @return An SmsMessage representing the record.
     *
     * @hide
     */
    public static SmsMessage createFromEfRecord(int index, byte[] data) {
        try {
            SmsMessage msg = new SmsMessage();

            msg.mIndexOnIcc = index;

            // First byte is status: RECEIVED_READ, RECEIVED_UNREAD, STORED_SENT,
            // or STORED_UNSENT
            // See TS 51.011 10.5.3
            if ((data[0] & 1) == 0) {
                Rlog.w(LOG_TAG,
                        "SMS parsing failed: Trying to parse a free record");
                return null;
            } else {
                msg.mStatusOnIcc = data[0] & 0x07;
            }

            int size = data.length - 1;

            // Note: Data may include trailing FF's.  That's OK; message
            // should still parse correctly.
            byte[] pdu = new byte[size];
            System.arraycopy(data, 1, pdu, 0, size);
            msg.parsePdu(pdu);
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    /**
     * Get the TP-Layer-Length for the given SMS-SUBMIT PDU Basically, the
     * length in bytes (not hex chars) less the SMSC header
     */
    public static int getTPLayerLengthForPDU(String pdu) {
        int len = pdu.length() / 2;
        int smscLen = Integer.parseInt(pdu.substring(0, 2), 16);

        return len - smscLen - 1;
    }

    /**
     * Get an SMS-SUBMIT PDU for a destination address and a message
     *
     * @param scAddress Service Centre address.  Null means use default.
     * @return a <code>SubmitPdu</code> containing the encoded SC
     *         address, if applicable, and the encoded message.
     *         Returns null on encode error.
     * @hide
     */
    public static SubmitPdu getSubmitPdu(String scAddress,
            String destinationAddress, String message,
            boolean statusReportRequested, byte[] header) {
        return getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, header,
                ENCODING_UNKNOWN, 0, 0);
    }


    /**
     * Get an SMS-SUBMIT PDU for a destination address and a message using the
     * specified encoding.
     *
     * @param scAddress Service Centre address.  Null means use default.
     * @param encoding Encoding defined by constants in
     *        com.android.internal.telephony.SmsConstants.ENCODING_*
     * @param languageTable
     * @param languageShiftTable
     * @return a <code>SubmitPdu</code> containing the encoded SC
     *         address, if applicable, and the encoded message.
     *         Returns null on encode error.
     * @hide
     */
    public static SubmitPdu getSubmitPdu(String scAddress,
            String destinationAddress, String message,
            boolean statusReportRequested, byte[] header, int encoding,
            int languageTable, int languageShiftTable) {

        // Perform null parameter checks.
        if (message == null || destinationAddress == null) {
            return null;
        }

        if (encoding == ENCODING_UNKNOWN) {
            // Find the best encoding to use
            TextEncodingDetails ted = calculateLength(message, false);
            encoding = ted.codeUnitSize;
            languageTable = ted.languageTable;
            languageShiftTable = ted.languageShiftTable;

            if (encoding == ENCODING_7BIT &&
                    (languageTable != 0 || languageShiftTable != 0)) {
                if (header != null) {
                    SmsHeader smsHeader = SmsHeader.fromByteArray(header);
                    if (smsHeader.languageTable != languageTable
                            || smsHeader.languageShiftTable != languageShiftTable) {
                        Rlog.w(LOG_TAG, "Updating language table in SMS header: "
                                + smsHeader.languageTable + " -> " + languageTable + ", "
                                + smsHeader.languageShiftTable + " -> " + languageShiftTable);
                        smsHeader.languageTable = languageTable;
                        smsHeader.languageShiftTable = languageShiftTable;
                        header = SmsHeader.toByteArray(smsHeader);
                    }
                } else {
                    SmsHeader smsHeader = new SmsHeader();
                    smsHeader.languageTable = languageTable;
                    smsHeader.languageShiftTable = languageShiftTable;
                    header = SmsHeader.toByteArray(smsHeader);
                }
            }
        }

        SubmitPdu ret = new SubmitPdu();
        // MTI = SMS-SUBMIT, UDHI = header != null
        byte mtiByte = (byte)(0x01 | (header != null ? 0x40 : 0x00));
        ByteArrayOutputStream bo = getSubmitPduHead(
                scAddress, destinationAddress, mtiByte,
                statusReportRequested, ret);

        // User Data (and length)
        byte[] userData;
        try {
            if (encoding == ENCODING_7BIT) {
                userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header,
                        languageTable, languageShiftTable);
            } else { //assume UCS-2
                try {
                    userData = encodeUCS2(message, header);
                } catch(UnsupportedEncodingException uex) {
                    Rlog.e(LOG_TAG,
                            "Implausible UnsupportedEncodingException ",
                            uex);
                    return null;
                }
            }
        } catch (EncodeException ex) {
            // Encoding to the 7-bit alphabet failed. Let's see if we can
            // send it as a UCS-2 encoded message
            try {
                userData = encodeUCS2(message, header);
                encoding = ENCODING_16BIT;
            } catch(UnsupportedEncodingException uex) {
                Rlog.e(LOG_TAG,
                        "Implausible UnsupportedEncodingException ",
                        uex);
                return null;
            }
        }

        if (encoding == ENCODING_7BIT) {
            if ((0xff & userData[0]) > MAX_USER_DATA_SEPTETS) {
                // Message too long
                Rlog.e(LOG_TAG, "Message too long (" + (0xff & userData[0]) + " septets)");
                return null;
            }
            // TP-Data-Coding-Scheme
            // Default encoding, uncompressed
            // To test writing messages to the SIM card, change this value 0x00
            // to 0x12, which means "bits 1 and 0 contain message class, and the
            // class is 2". Note that this takes effect for the sender. In other
            // words, messages sent by the phone with this change will end up on
            // the receiver's SIM card. You can then send messages to yourself
            // (on a phone with this change) and they'll end up on the SIM card.
            bo.write(0x00);
        } else { // assume UCS-2
            if ((0xff & userData[0]) > MAX_USER_DATA_BYTES) {
                // Message too long
                Rlog.e(LOG_TAG, "Message too long (" + (0xff & userData[0]) + " bytes)");
                return null;
            }
            // TP-Data-Coding-Scheme
            // UCS-2 encoding, uncompressed
            bo.write(0x08);
        }

        // (no TP-Validity-Period)
        bo.write(userData, 0, userData.length);
        ret.encodedMessage = bo.toByteArray();
        return ret;
    }

    /**
     * Packs header and UCS-2 encoded message. Includes TP-UDL & TP-UDHL if necessary
     *
     * @return encoded message as UCS2
     * @throws UnsupportedEncodingException
     */
    private static byte[] encodeUCS2(String message, byte[] header)
        throws UnsupportedEncodingException {
        byte[] userData, textPart;
        textPart = message.getBytes("utf-16be");

        if (header != null) {
            // Need 1 byte for UDHL
            userData = new byte[header.length + textPart.length + 1];

            userData[0] = (byte)header.length;
            System.arraycopy(header, 0, userData, 1, header.length);
            System.arraycopy(textPart, 0, userData, header.length + 1, textPart.length);
        }
        else {
            userData = textPart;
        }
        byte[] ret = new byte[userData.length+1];
        ret[0] = (byte) (userData.length & 0xff );
        System.arraycopy(userData, 0, ret, 1, userData.length);
        return ret;
    }

    /**
     * Get an SMS-SUBMIT PDU for a destination address and a message
     *
     * @param scAddress Service Centre address.  Null means use default.
     * @return a <code>SubmitPdu</code> containing the encoded SC
     *         address, if applicable, and the encoded message.
     *         Returns null on encode error.
     */
    public static SubmitPdu getSubmitPdu(String scAddress,
            String destinationAddress, String message,
            boolean statusReportRequested) {

        return getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, null);
    }

    /**
     * Get an SMS-SUBMIT PDU for a data message to a destination address &amp; port
     *
     * @param scAddress Service Centre address. null == use default
     * @param destinationAddress the address of the destination for the message
     * @param destinationPort the port to deliver the message to at the
     *        destination
     * @param data the data for the message
     * @return a <code>SubmitPdu</code> containing the encoded SC
     *         address, if applicable, and the encoded message.
     *         Returns null on encode error.
     */
    public static SubmitPdu getSubmitPdu(String scAddress,
            String destinationAddress, int destinationPort, byte[] data,
            boolean statusReportRequested) {

        SmsHeader.PortAddrs portAddrs = new SmsHeader.PortAddrs();
        portAddrs.destPort = destinationPort;
        portAddrs.origPort = 0;
        portAddrs.areEightBits = false;

        SmsHeader smsHeader = new SmsHeader();
        smsHeader.portAddrs = portAddrs;

        byte[] smsHeaderData = SmsHeader.toByteArray(smsHeader);

        if ((data.length + smsHeaderData.length + 1) > MAX_USER_DATA_BYTES) {
            Rlog.e(LOG_TAG, "SMS data message may only contain "
                    + (MAX_USER_DATA_BYTES - smsHeaderData.length - 1) + " bytes");
            return null;
        }

        SubmitPdu ret = new SubmitPdu();
        ByteArrayOutputStream bo = getSubmitPduHead(
                scAddress, destinationAddress, (byte) 0x41, // MTI = SMS-SUBMIT,
                                                            // TP-UDHI = true
                statusReportRequested, ret);

        // TP-Data-Coding-Scheme
        // No class, 8 bit data
        bo.write(0x04);

        // (no TP-Validity-Period)

        // Total size
        bo.write(data.length + smsHeaderData.length + 1);

        // User data header
        bo.write(smsHeaderData.length);
        bo.write(smsHeaderData, 0, smsHeaderData.length);

        // User data
        bo.write(data, 0, data.length);

        ret.encodedMessage = bo.toByteArray();
        return ret;
    }

    /**
     * Create the beginning of a SUBMIT PDU.  This is the part of the
     * SUBMIT PDU that is common to the two versions of {@link #getSubmitPdu},
     * one of which takes a byte array and the other of which takes a
     * <code>String</code>.
     *
     * @param scAddress Service Centre address. null == use default
     * @param destinationAddress the address of the destination for the message
     * @param mtiByte
     * @param ret <code>SubmitPdu</code> containing the encoded SC
     *        address, if applicable, and the encoded message
     */
    private static ByteArrayOutputStream getSubmitPduHead(
            String scAddress, String destinationAddress, byte mtiByte,
            boolean statusReportRequested, SubmitPdu ret) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(
                MAX_USER_DATA_BYTES + 40);

        // SMSC address with length octet, or 0
        if (scAddress == null) {
            ret.encodedScAddress = null;
        } else {
            ret.encodedScAddress = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(
                    scAddress);
        }

        // TP-Message-Type-Indicator (and friends)
        if (statusReportRequested) {
            // Set TP-Status-Report-Request bit.
            mtiByte |= 0x20;
            if (VDBG) Rlog.d(LOG_TAG, "SMS status report requested");
        }
        bo.write(mtiByte);

        // space for TP-Message-Reference
        bo.write(0);

        byte[] daBytes;

        daBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(destinationAddress);

        // destination address length in BCD digits, ignoring TON byte and pad
        // TODO Should be better.
        // MTK-START
        if (daBytes != null) {
        // MTK-END
            bo.write((daBytes.length - 1) * 2
                     - ((daBytes[daBytes.length - 1] & 0xf0) == 0xf0 ? 1 : 0));

            // destination address
            bo.write(daBytes, 0, daBytes.length);
        // MTK-START
        } else {
            // TP-Protocol-Identifier
            Rlog.d(LOG_TAG, "write an empty address for submit pdu");
            bo.write(0);
            bo.write(PhoneNumberUtils.TOA_Unknown);
        }
        // MTK-END

        // TP-Protocol-Identifier
        bo.write(0);
        return bo;
    }

    private static class PduParser {
        byte mPdu[];
        int mCur;
        SmsHeader mUserDataHeader;
        byte[] mUserData;
        int mUserDataSeptetPadding;

        PduParser(byte[] pdu) {
            mPdu = pdu;
            mCur = 0;
            mUserDataSeptetPadding = 0;
        }

        /**
         * Parse and return the SC address prepended to SMS messages coming via
         * the TS 27.005 / AT interface.  Returns null on invalid address
         */
        String getSCAddress() {
            int len;
            String ret;

            // length of SC Address
            len = getByte();

            if (len == 0) {
                // no SC address
                ret = null;
            } else {
                // SC address
                try {
                    ret = PhoneNumberUtils
                            .calledPartyBCDToString(mPdu, mCur, len);
                } catch (RuntimeException tr) {
                    Rlog.d(LOG_TAG, "invalid SC address: ", tr);
                    ret = null;
                }
            }

            mCur += len;

            return ret;
        }

        /**
         * returns non-sign-extended byte value
         */
        int getByte() {
            return mPdu[mCur++] & 0xff;
        }

        /**
         * Any address except the SC address (eg, originating address) See TS
         * 23.040 9.1.2.5
         */
        GsmSmsAddress getAddress() {
            GsmSmsAddress ret;

            // "The Address-Length field is an integer representation of
            // the number field, i.e. excludes any semi-octet containing only
            // fill bits."
            // The TOA field is not included as part of this
            int addressLength = mPdu[mCur] & 0xff;
            int lengthBytes = 2 + (addressLength + 1) / 2;

            try {
                ret = new GsmSmsAddress(mPdu, mCur, lengthBytes);
            } catch (ParseException e) {
                ret = null;
                //This is caught by createFromPdu(byte[] pdu)
                throw new RuntimeException(e.getMessage());
            }

            mCur += lengthBytes;

            return ret;
        }

        /**
         * Parses an SC timestamp and returns a currentTimeMillis()-style
         * timestamp
         */

        long getSCTimestampMillis() {
            // TP-Service-Centre-Time-Stamp
            int year = IccUtils.gsmBcdByteToInt(mPdu[mCur++]);
            int month = IccUtils.gsmBcdByteToInt(mPdu[mCur++]);
            int day = IccUtils.gsmBcdByteToInt(mPdu[mCur++]);
            int hour = IccUtils.gsmBcdByteToInt(mPdu[mCur++]);
            int minute = IccUtils.gsmBcdByteToInt(mPdu[mCur++]);
            int second = IccUtils.gsmBcdByteToInt(mPdu[mCur++]);

            // For the timezone, the most significant bit of the
            // least significant nibble is the sign byte
            // (meaning the max range of this field is 79 quarter-hours,
            // which is more than enough)

            byte tzByte = mPdu[mCur++];

            // Mask out sign bit.
            int timezoneOffset = IccUtils.gsmBcdByteToInt((byte) (tzByte & (~0x08)));

            timezoneOffset = ((tzByte & 0x08) == 0) ? timezoneOffset : -timezoneOffset;

            Time time = new Time(Time.TIMEZONE_UTC);

            // It's 2006.  Should I really support years < 2000?
            time.year = year >= 90 ? year + 1900 : year + 2000;
            time.month = month - 1;
            time.monthDay = day;
            time.hour = hour;
            time.minute = minute;
            time.second = second;

            // Timezone offset is in quarter hours.
            return time.toMillis(true) - (timezoneOffset * 15 * 60 * 1000);
        }

        /**
         * Pulls the user data out of the PDU, and separates the payload from
         * the header if there is one.
         *
         * @param hasUserDataHeader true if there is a user data header
         * @param dataInSeptets true if the data payload is in septets instead
         *  of octets
         * @return the number of septets or octets in the user data payload
         */
        int constructUserData(boolean hasUserDataHeader, boolean dataInSeptets) {
            int offset = mCur;
            int userDataLength = mPdu[offset++] & 0xff;
            int headerSeptets = 0;
            int userDataHeaderLength = 0;

            if (hasUserDataHeader) {
                userDataHeaderLength = mPdu[offset++] & 0xff;

                byte[] udh = new byte[userDataHeaderLength];
                System.arraycopy(mPdu, offset, udh, 0, userDataHeaderLength);
                mUserDataHeader = SmsHeader.fromByteArray(udh);
                offset += userDataHeaderLength;

                int headerBits = (userDataHeaderLength + 1) * 8;
                headerSeptets = headerBits / 7;
                headerSeptets += (headerBits % 7) > 0 ? 1 : 0;
                mUserDataSeptetPadding = (headerSeptets * 7) - headerBits;
            }

            int bufferLen;
            if (dataInSeptets) {
                /*
                 * Here we just create the user data length to be the remainder of
                 * the pdu minus the user data header, since userDataLength means
                 * the number of uncompressed septets.
                 */
                bufferLen = mPdu.length - offset;
            } else {
                /*
                 * userDataLength is the count of octets, so just subtract the
                 * user data header.
                 */
                bufferLen = userDataLength - (hasUserDataHeader ? (userDataHeaderLength + 1) : 0);
                if (bufferLen < 0) {
                    bufferLen = 0;
                }
            }

            mUserData = new byte[bufferLen];
            System.arraycopy(mPdu, offset, mUserData, 0, mUserData.length);
            mCur = offset;

            if (dataInSeptets) {
                // Return the number of septets
                int count = userDataLength - headerSeptets;
                // If count < 0, return 0 (means UDL was probably incorrect)
                return count < 0 ? 0 : count;
            } else {
                // Return the number of octets
                return mUserData.length;
            }
        }

        /**
         * Returns the user data payload, not including the headers
         *
         * @return the user data payload, not including the headers
         */
        byte[] getUserData() {
            return mUserData;
        }

        /**
         * Returns an object representing the user data headers
         *
         * {@hide}
         */
        SmsHeader getUserDataHeader() {
            return mUserDataHeader;
        }

        /**
         * Interprets the user data payload as packed GSM 7bit characters, and
         * decodes them into a String.
         *
         * @param septetCount the number of septets in the user data payload
         * @return a String with the decoded characters
         */
        String getUserDataGSM7Bit(int septetCount, int languageTable,
                int languageShiftTable) {
            String ret;

            ret = GsmAlphabet.gsm7BitPackedToString(mPdu, mCur, septetCount,
                    mUserDataSeptetPadding, languageTable, languageShiftTable);

            mCur += (septetCount * 7) / 8;

            return ret;
        }

        /**
         * Interprets the user data payload as UCS2 characters, and
         * decodes them into a String.
         *
         * @param byteCount the number of bytes in the user data payload
         * @return a String with the decoded characters
         */
        String getUserDataUCS2(int byteCount) {
            String ret;

            try {
                ret = new String(mPdu, mCur, byteCount, "utf-16");
            } catch (UnsupportedEncodingException ex) {
                ret = "";
                Rlog.e(LOG_TAG, "implausible UnsupportedEncodingException", ex);
            }

            mCur += byteCount;
            return ret;
        }

        /**
         * Interprets the user data payload as KSC-5601 characters, and
         * decodes them into a String.
         *
         * @param byteCount the number of bytes in the user data payload
         * @return a String with the decoded characters
         */
        String getUserDataKSC5601(int byteCount) {
            String ret;

            try {
                ret = new String(mPdu, mCur, byteCount, "KSC5601");
            } catch (UnsupportedEncodingException ex) {
                ret = "";
                Rlog.e(LOG_TAG, "implausible UnsupportedEncodingException", ex);
            }

            mCur += byteCount;
            return ret;
        }

        boolean moreDataPresent() {
            return (mPdu.length > mCur);
        }
    }

    /**
     * Calculate the number of septets needed to encode the message.
     *
     * @param msgBody the message to encode
     * @param use7bitOnly ignore (but still count) illegal characters if true
     * @return TextEncodingDetails
     */
    public static TextEncodingDetails calculateLength(CharSequence msgBody,
            boolean use7bitOnly) {
        TextEncodingDetails ted = GsmAlphabet.countGsmSeptets(msgBody, use7bitOnly);
        if (ted == null) {
            ted = new TextEncodingDetails();
            int octets = msgBody.length() * 2;
            ted.codeUnitCount = msgBody.length();
            if (octets > MAX_USER_DATA_BYTES) {
                ted.msgCount = (octets + (MAX_USER_DATA_BYTES_WITH_HEADER - 1)) /
                        MAX_USER_DATA_BYTES_WITH_HEADER;
                ted.codeUnitsRemaining = ((ted.msgCount *
                        MAX_USER_DATA_BYTES_WITH_HEADER) - octets) / 2;
            } else {
                ted.msgCount = 1;
                ted.codeUnitsRemaining = (MAX_USER_DATA_BYTES - octets)/2;
            }
            ted.codeUnitSize = ENCODING_16BIT;
        }
        return ted;
    }

    /** {@inheritDoc} */
    @Override
    public int getProtocolIdentifier() {
        return mProtocolIdentifier;
    }

    /**
     * Returns the TP-Data-Coding-Scheme byte, for acknowledgement of SMS-PP download messages.
     * @return the TP-DCS field of the SMS header
     */
    int getDataCodingScheme() {
        return mDataCodingScheme;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isReplace() {
        return (mProtocolIdentifier & 0xc0) == 0x40
                && (mProtocolIdentifier & 0x3f) > 0
                && (mProtocolIdentifier & 0x3f) < 8;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCphsMwiMessage() {
        return ((GsmSmsAddress) mOriginatingAddress).isCphsVoiceMessageClear()
                || ((GsmSmsAddress) mOriginatingAddress).isCphsVoiceMessageSet();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isMWIClearMessage() {
        // MTK-START
        if (mUserDataHeader != null) {
            if (mUserDataHeader.getVoiceMailCount() >= 0 && mUserDataHeader.getVoiceMailCount() == 0) {
                return true;
            }
        }
        // MTK-END

        if (mIsMwi && !mMwiSense) {
            return true;
        }

        return mOriginatingAddress != null
                && ((GsmSmsAddress) mOriginatingAddress).isCphsVoiceMessageClear();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isMWISetMessage() {
        // MTK-START
        if (mUserDataHeader != null) {
            if (mUserDataHeader.getVoiceMailCount() >= 0 && mUserDataHeader.getVoiceMailCount() > 0) {
                return true;
            }
        }
        // MTK-END

        if (mIsMwi && mMwiSense) {
            return true;
        }

        return mOriginatingAddress != null
                && ((GsmSmsAddress) mOriginatingAddress).isCphsVoiceMessageSet();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isMwiDontStore() {
        if (mIsMwi && mMwiDontStore) {
            return true;
        }

        if (isCphsMwiMessage()) {
            // See CPHS 4.2 Section B.4.2.1
            // If the user data is a single space char, do not store
            // the message. Otherwise, store and display as usual
            if (" ".equals(getMessageBody())) {
                return true;
            }
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override
    public int getStatus() {
        return mStatus;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isStatusReportMessage() {
        return mIsStatusReportMessage;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isReplyPathPresent() {
        return mReplyPathPresent;
    }

    /**
     * TS 27.005 3.1, &lt;pdu&gt; definition "In the case of SMS: 3GPP TS 24.011 [6]
     * SC address followed by 3GPP TS 23.040 [3] TPDU in hexadecimal format:
     * ME/TA converts each octet of TP data unit into two IRA character long
     * hex number (e.g. octet with integer value 42 is presented to TE as two
     * characters 2A (IRA 50 and 65))" ...in the case of cell broadcast,
     * something else...
     */
    private void parsePdu(byte[] pdu) {
        mPdu = pdu;
        // Rlog.d(LOG_TAG, "raw sms message:");
        // Rlog.d(LOG_TAG, s);

        PduParser p = new PduParser(pdu);

        mScAddress = p.getSCAddress();

        if (mScAddress != null) {
            if (VDBG) Rlog.d(LOG_TAG, "SMS SC address: " + mScAddress);
        }

        // TODO(mkf) support reply path, user data header indicator

        // TP-Message-Type-Indicator
        // 9.2.3
        int firstByte = p.getByte();

        mMti = firstByte & 0x3;
        switch (mMti) {
        // TP-Message-Type-Indicator
        // 9.2.3
        case 0:
        case 3: //GSM 03.40 9.2.3.1: MTI == 3 is Reserved.
                //This should be processed in the same way as MTI == 0 (Deliver)
            parseSmsDeliver(p, firstByte);
            break;
        case 1:
            parseSmsSubmit(p, firstByte);
            break;
        case 2:
            parseSmsStatusReport(p, firstByte);
            break;
        default:
            // TODO(mkf) the rest of these
            throw new RuntimeException("Unsupported message type");
        }
    }

    /**
     * Parses a SMS-STATUS-REPORT message.
     *
     * @param p A PduParser, cued past the first byte.
     * @param firstByte The first byte of the PDU, which contains MTI, etc.
     */
    private void parseSmsStatusReport(PduParser p, int firstByte) {
        mIsStatusReportMessage = true;

        // TP-Message-Reference
        mMessageRef = p.getByte();
        // TP-Recipient-Address
        mRecipientAddress = p.getAddress();
        // TP-Service-Centre-Time-Stamp
        mScTimeMillis = p.getSCTimestampMillis();
        p.getSCTimestampMillis();
        // TP-Status
        mStatus = p.getByte();

        // MTK-START
        mMessageBody = "";
        // MTK-END

        // The following are optional fields that may or may not be present.
        if (p.moreDataPresent()) {
            // TP-Parameter-Indicator
            int extraParams = p.getByte();
            int moreExtraParams = extraParams;
            // MTK-START
            while (((moreExtraParams & 0x80) != 0) && (p.moreDataPresent() == true)) {
            // MTK-END
                // We only know how to parse a few extra parameters, all
                // indicated in the first TP-PI octet, so skip over any
                // additional TP-PI octets.
                moreExtraParams = p.getByte();
            }
            // As per 3GPP 23.040 section 9.2.3.27 TP-Parameter-Indicator,
            // only process the byte if the reserved bits (bits3 to 6) are zero.
            if ((extraParams & 0x78) == 0) {
                // TP-Protocol-Identifier
                if ((extraParams & 0x01) != 0) {
                    mProtocolIdentifier = p.getByte();
                }
                // TP-Data-Coding-Scheme
                if ((extraParams & 0x02) != 0) {
                    mDataCodingScheme = p.getByte();
                }
                // TP-User-Data-Length (implies existence of TP-User-Data)
                if ((extraParams & 0x04) != 0) {
                    boolean hasUserDataHeader = (firstByte & 0x40) == 0x40;
                    parseUserData(p, hasUserDataHeader);
                }
            }
        }
    }

    private void parseSmsDeliver(PduParser p, int firstByte) {
        mReplyPathPresent = (firstByte & 0x80) == 0x80;

        mOriginatingAddress = p.getAddress();

        if (mOriginatingAddress != null) {
            if (VDBG) Rlog.v(LOG_TAG, "SMS originating address: "
                    + mOriginatingAddress.address);
        }

        // TP-Protocol-Identifier (TP-PID)
        // TS 23.040 9.2.3.9
        mProtocolIdentifier = p.getByte();

        // TP-Data-Coding-Scheme
        // see TS 23.038
        mDataCodingScheme = p.getByte();

        if (VDBG) {
            Rlog.v(LOG_TAG, "SMS TP-PID:" + mProtocolIdentifier
                    + " data coding scheme: " + mDataCodingScheme);
        }

        mScTimeMillis = p.getSCTimestampMillis();

        if (VDBG) Rlog.d(LOG_TAG, "SMS SC timestamp: " + mScTimeMillis);

        boolean hasUserDataHeader = (firstByte & 0x40) == 0x40;

        parseUserData(p, hasUserDataHeader);
    }

    /**
     * Parses a SMS-SUBMIT message.
     *
     * @param p A PduParser, cued past the first byte.
     * @param firstByte The first byte of the PDU, which contains MTI, etc.
     */
    private void parseSmsSubmit(PduParser p, int firstByte) {
        mReplyPathPresent = (firstByte & 0x80) == 0x80;

        // TP-MR (TP-Message Reference)
        mMessageRef = p.getByte();

        mRecipientAddress = p.getAddress();
        // MTK-START
        destinationAddress = mRecipientAddress; //for SmsMessageBase, and can getDestinationAddress by AP
        // MTK-END

        if (mRecipientAddress != null) {
            if (VDBG) Rlog.v(LOG_TAG, "SMS recipient address: " + mRecipientAddress.address);
        }

        // TP-Protocol-Identifier (TP-PID)
        // TS 23.040 9.2.3.9
        mProtocolIdentifier = p.getByte();

        // TP-Data-Coding-Scheme
        // see TS 23.038
        mDataCodingScheme = p.getByte();

        if (VDBG) {
            Rlog.v(LOG_TAG, "SMS TP-PID:" + mProtocolIdentifier
                    + " data coding scheme: " + mDataCodingScheme);
        }

        // TP-Validity-Period-Format
        int validityPeriodLength = 0;
        int validityPeriodFormat = ((firstByte>>3) & 0x3);
        if (0x0 == validityPeriodFormat) /* 00, TP-VP field not present*/
        {
            validityPeriodLength = 0;
        }
        else if (0x2 == validityPeriodFormat) /* 10, TP-VP: relative format*/
        {
            validityPeriodLength = 1;
        }
        else /* other case, 11 or 01, TP-VP: absolute or enhanced format*/
        {
            validityPeriodLength = 7;
        }

        // TP-Validity-Period is not used on phone, so just ignore it for now.
        while (validityPeriodLength-- > 0)
        {
            p.getByte();
        }

        boolean hasUserDataHeader = (firstByte & 0x40) == 0x40;

        parseUserData(p, hasUserDataHeader);
    }

    /**
     * Parses the User Data of an SMS.
     *
     * @param p The current PduParser.
     * @param hasUserDataHeader Indicates whether a header is present in the
     *                          User Data.
     */
    private void parseUserData(PduParser p, boolean hasUserDataHeader) {
        boolean hasMessageClass = false;
        boolean userDataCompressed = false;

        int encodingType = ENCODING_UNKNOWN;

        // Look up the data encoding scheme
        if ((mDataCodingScheme & 0x80) == 0) {
            userDataCompressed = (0 != (mDataCodingScheme & 0x20));
            hasMessageClass = (0 != (mDataCodingScheme & 0x10));

            if (userDataCompressed) {
                Rlog.w(LOG_TAG, "4 - Unsupported SMS data coding scheme "
                        + "(compression) " + (mDataCodingScheme & 0xff));
            } else {
                switch ((mDataCodingScheme >> 2) & 0x3) {
                case 0: // GSM 7 bit default alphabet
                    encodingType = ENCODING_7BIT;
                    break;

                case 2: // UCS 2 (16bit)
                    encodingType = ENCODING_16BIT;
                    break;

                case 1: // 8 bit data
                case 3: // reserved
                    Rlog.w(LOG_TAG, "1 - Unsupported SMS data coding scheme "
                            + (mDataCodingScheme & 0xff));
                    encodingType = ENCODING_8BIT;
                    break;
                }
            }
        } else if ((mDataCodingScheme & 0xf0) == 0xf0) {
            hasMessageClass = true;
            userDataCompressed = false;

            if (0 == (mDataCodingScheme & 0x04)) {
                // GSM 7 bit default alphabet
                encodingType = ENCODING_7BIT;
            } else {
                // 8 bit data
                encodingType = ENCODING_8BIT;
            }
        } else if ((mDataCodingScheme & 0xF0) == 0xC0
                || (mDataCodingScheme & 0xF0) == 0xD0
                || (mDataCodingScheme & 0xF0) == 0xE0) {
            // 3GPP TS 23.038 V7.0.0 (2006-03) section 4

            // 0xC0 == 7 bit, don't store
            // 0xD0 == 7 bit, store
            // 0xE0 == UCS-2, store

            if ((mDataCodingScheme & 0xF0) == 0xE0) {
                encodingType = ENCODING_16BIT;
            } else {
                encodingType = ENCODING_7BIT;
            }

            userDataCompressed = false;
            boolean active = ((mDataCodingScheme & 0x08) == 0x08);

            // bit 0x04 reserved

            if ((mDataCodingScheme & 0x03) == 0x00) {
                mIsMwi = true;
                mMwiSense = active;
                mMwiDontStore = ((mDataCodingScheme & 0xF0) == 0xC0);
            } else {
                mIsMwi = false;

                Rlog.w(LOG_TAG, "MWI for fax, email, or other "
                        + (mDataCodingScheme & 0xff));
            }
        } else if ((mDataCodingScheme & 0xC0) == 0x80) {
            // 3GPP TS 23.038 V7.0.0 (2006-03) section 4
            // 0x80..0xBF == Reserved coding groups
            if (mDataCodingScheme == 0x84) {
                // This value used for KSC5601 by carriers in Korea.
                encodingType = ENCODING_KSC5601;
            } else {
                Rlog.w(LOG_TAG, "5 - Unsupported SMS data coding scheme "
                        + (mDataCodingScheme & 0xff));
            }
        } else {
            Rlog.w(LOG_TAG, "3 - Unsupported SMS data coding scheme "
                    + (mDataCodingScheme & 0xff));
        }

        // set both the user data and the user data header.
        int count = p.constructUserData(hasUserDataHeader,
                encodingType == ENCODING_7BIT);
        this.mUserData = p.getUserData();
        this.mUserDataHeader = p.getUserDataHeader();

        switch (encodingType) {
        case ENCODING_UNKNOWN:
        case ENCODING_8BIT:
            mMessageBody = null;
            break;

        case ENCODING_7BIT:
            mMessageBody = p.getUserDataGSM7Bit(count,
                    hasUserDataHeader ? mUserDataHeader.languageTable : 0,
                    hasUserDataHeader ? mUserDataHeader.languageShiftTable : 0);
            break;

        case ENCODING_16BIT:
            mMessageBody = p.getUserDataUCS2(count);
            break;

        case ENCODING_KSC5601:
            mMessageBody = p.getUserDataKSC5601(count);
            break;
        }

        if (VDBG) Rlog.v(LOG_TAG, "SMS message body (raw): '" + mMessageBody + "'");

        if (mMessageBody != null) {
            parseMessageBody();
        }

        if (!hasMessageClass) {
            messageClass = MessageClass.UNKNOWN;
        } else {
            switch (mDataCodingScheme & 0x3) {
            case 0:
                messageClass = MessageClass.CLASS_0;
                break;
            case 1:
                messageClass = MessageClass.CLASS_1;
                break;
            case 2:
                messageClass = MessageClass.CLASS_2;
                break;
            case 3:
                messageClass = MessageClass.CLASS_3;
                break;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageClass getMessageClass() {
        return messageClass;
    }

    // Add by VIA start, HANDROID#1950
    /**
     * {@inheritDoc}
     */
    @Override
    public int getValidityPeriodRelative() {
        Rlog.w(LOG_TAG, "getValidityPeriodRelative: is not supported in GSM mode.");
        return -1;
    }
    // Add by VIA end, HANDROID#1950

    /**
     * Returns true if this is a (U)SIM data download type SM.
     * See 3GPP TS 31.111 section 9.1 and TS 23.040 section 9.2.3.9.
     *
     * @return true if this is a USIM data download message; false otherwise
     */
    boolean isUsimDataDownload() {
        return messageClass == MessageClass.CLASS_2 &&
                (mProtocolIdentifier == 0x7f || mProtocolIdentifier == 0x7c);
    }

    // MTK-START
    /**
     * Get an SMS-SUBMIT PDU for a data message to a destination address &amp; port
     *
     * @param scAddress Service Centre address. null == use default
     * @param destinationAddress the address of the destination for the message
     * @param destinationPort the port to deliver the message to at the
     *        destination
     * @param originalPort the port to deliver the message from
     * @param data the dat for the message
     * @return a <code>SubmitPdu</code> containing the encoded SC
     *         address, if applicable, and the encoded message.
     *         Returns null on encode error.
     */
    public static SubmitPdu getSubmitPdu(String scAddress,
            String destinationAddress, int destinationPort, int originalPort, byte[] data,
            boolean statusReportRequested) {

        byte[] smsHeaderData = SmsHeader.getSubmitPduHeader(destinationPort, originalPort);
        if (smsHeaderData == null) {
            return null;
        }
        return getSubmitPdu(scAddress, destinationAddress,
                data, smsHeaderData, statusReportRequested);
    }

    /**
     * Get an SMS-SUBMIT PDU for a destination address and a message
     * which are sent to a specified application port
     *
     * @param scAddress Service Centre address.  Null means use default.
     * @return a <code>SubmitPdu</code> containing the encoded SC
     *         address, if applicable, and the encoded message.
     *         Returns null on encode error.
     */
    public static SubmitPdu getSubmitPdu(String scAddress,
            String destinationAddress, String message,
            int destPort, boolean statusReportRequested) {

        int language = getCurrentSysLanguage();
        int singleId = -1;
        int lockingId = -1;
        int encoding = ENCODING_UNKNOWN;
        TextEncodingDetails ted = new TextEncodingDetails();

        if (encodeStringWithSpecialLang(message, language, ted)) {
            if (ted.useLockingShift && ted.useSingleShift) {
                encoding = ENCODING_7BIT_LOCKING_SINGLE;
                singleId = lockingId = language;
            } else if (ted.useLockingShift) {
                encoding = ENCODING_7BIT_LOCKING;
                lockingId = language;
            } else if (ted.useSingleShift) {
                encoding = ENCODING_7BIT_SINGLE;
                singleId = language;
            } else {
                encoding = ENCODING_7BIT;
                language = -1;
            }
        } else {
            encoding = ENCODING_16BIT;
        }

        byte[] smsHeaderData = SmsHeader.getSubmitPduHeaderWithLang(destPort, singleId, lockingId);

        return getSubmitPduWithLang(scAddress, destinationAddress,
                message, statusReportRequested, smsHeaderData, encoding, language);
    }

    /**
     * Get an SMS-SUBMIT PDU for a data message with data header
     * to a destination address
     *
     * @param scAddress Service Centre address. null == use default
     * @param destinationAddress the address of the destination for the message
     * @param data the data for the message
     * @param header the pdu header for the message
     * @return a <code>SubmitPdu</code> containing the encoded SC
     *         address, if applicable, and the encoded message.
     *         Returns null on encode error.
     */
    public static SubmitPdu getSubmitPdu(String scAddress,
            String destinationAddress, byte[] data, byte[] smsHeaderData,
            boolean statusReportRequested) {

        if ((data.length + smsHeaderData.length + 1) > MAX_USER_DATA_BYTES) {
            Rlog.e(LOG_TAG, "SMS data message may only contain "
                    + (MAX_USER_DATA_BYTES - smsHeaderData.length - 1) + " bytes");
            return null;
        }

        SubmitPdu ret = new SubmitPdu();
        ByteArrayOutputStream bo = getSubmitPduHead(
                scAddress, destinationAddress, (byte) 0x41, // MTI = SMS-SUBMIT,
                                                            // TP-UDHI = true
                statusReportRequested, ret);

        // TP-Data-Coding-Scheme
        // No class, 8 bit data
        bo.write(0x04);

        // (no TP-Validity-Period)

        // Total size
        bo.write(data.length + smsHeaderData.length + 1);

        // User data header
        bo.write(smsHeaderData.length);
        bo.write(smsHeaderData, 0, smsHeaderData.length);

        // User data
        bo.write(data, 0, data.length);

        ret.encodedMessage = bo.toByteArray();
        return ret;
    }

    /**
     * Get an SMS-SUBMIT PDU for a destination address and a message using the
     * specified encoding.
     *
     * @param scAddress Service Centre address.  Null means use default.
     * @param encoding Encoding defined by constants in android.telephony.SmsMessage.ENCODING_*
     * @return a <code>SubmitPdu</code> containing the encoded SC
     *         address, if applicable, and the encoded message.
     *         Returns null on encode error.
     * @hide
     */
    public static SubmitPdu getSubmitPduWithLang(String scAddress,
            String destinationAddress, String message,
            boolean statusReportRequested, byte[] header, int encoding, int language) {

        Rlog.d(LOG_TAG, "SmsMessage: get submit pdu");
        // Perform null parameter checks.
        if (message == null || destinationAddress == null) {
            return null;
        }

        SubmitPdu ret = new SubmitPdu();
        // MTI = SMS-SUBMIT, UDHI = header != null
        Rlog.d(LOG_TAG, "SmsMessage: UDHI = " + (header != null));
        byte mtiByte = (byte) (0x01 | (header != null ? 0x40 : 0x00));
        ByteArrayOutputStream bo = getSubmitPduHead(
                scAddress, destinationAddress, mtiByte,
                statusReportRequested, ret);
        // User Data (and length)
        byte[] userData;
        if (encoding == ENCODING_UNKNOWN) {
            // First, try encoding it with the GSM alphabet
            encoding = ENCODING_7BIT;
        }
        try {
            Rlog.d(LOG_TAG, "Get SubmitPdu with Lang " + encoding + " " + language);
            if (encoding == ENCODING_7BIT) {
                //userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header);
                userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, 0, 0);
            } else if (language > 0 && encoding != ENCODING_16BIT) {
                if (encoding == ENCODING_7BIT_LOCKING) {
                    //userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, -1, language);
                    userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, 0, language);
                } else if (encoding == ENCODING_7BIT_SINGLE) {
                    //userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, language, -1);
                    userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, language, 0);
                } else if (encoding == ENCODING_7BIT_LOCKING_SINGLE) {
                    userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, language, language);
                } else {
                    //userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header);
                    userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, 0, 0);
                }
                encoding = ENCODING_7BIT;
            } else { // assume UCS-2
                try {
                    userData = encodeUCS2(message, header);
                } catch (UnsupportedEncodingException uex) {
                    Rlog.e(LOG_TAG,
                            "Implausible UnsupportedEncodingException ",
                            uex);
                    return null;
                }
            }
        } catch (EncodeException ex) {
            // Encoding to the 7-bit alphabet failed. Let's see if we can
            // send it as a UCS-2 encoded message
            try {
                userData = encodeUCS2(message, header);
                encoding = ENCODING_16BIT;
            } catch (UnsupportedEncodingException uex) {
                Rlog.e(LOG_TAG,
                        "Implausible UnsupportedEncodingException ",
                        uex);
                return null;
            }
        }

        if (encoding == ENCODING_7BIT) {
            if ((0xff & userData[0]) > MAX_USER_DATA_SEPTETS) {
                // Message too long
                return null;
            }
            // TP-Data-Coding-Scheme
            // Default encoding, uncompressed
            // To test writing messages to the SIM card, change this value 0x00
            // to 0x12, which means "bits 1 and 0 contain message class, and the
            // class is 2". Note that this takes effect for the sender. In other
            // words, messages sent by the phone with this change will end up on
            // the receiver's SIM card. You can then send messages to yourself
            // (on a phone with this change) and they'll end up on the SIM card.
            bo.write(0x00);
        } else { // assume UCS-2
            if ((0xff & userData[0]) > MAX_USER_DATA_BYTES) {
                // Message too long
                return null;
            }
            // TP-Data-Coding-Scheme
            // Class 3, UCS-2 encoding, uncompressed

            // modified by mtk80611
            // bo.write(0x0b);
            bo.write(0x08);
            // modified by mtk80611
        }

        // (no TP-Validity-Period)
        bo.write(userData, 0, userData.length);
        ret.encodedMessage = bo.toByteArray();
        return ret;
    }

    public static DeliverPdu getDeliverPduWithLang(String scAddress, String originalAddress, String message,
        byte[] header, long timestamp, int encoding, int language) {
        Rlog.d(LOG_TAG, "SmsMessage: get deliver pdu");

        if (message == null || originalAddress == null) {
            return null;
        }

        DeliverPdu ret = new DeliverPdu();

        Rlog.d(LOG_TAG, "SmsMessage: UDHI = " + (header != null));
        byte mtiByte = (byte) (0x00 | (header != null ? 0x40 : 0x00));

        ByteArrayOutputStream bo = getDeliverPduHead(scAddress, originalAddress, mtiByte, ret);

        // encode User Data (and length)
        byte[] userData;
        if (encoding == ENCODING_UNKNOWN) {
            // First, try encoding it with the GSM alphabet
            encoding = ENCODING_7BIT;
        }
        try {
            Rlog.d(LOG_TAG, "Get SubmitPdu with Lang " + encoding + " " + language);
            if (encoding == ENCODING_7BIT) {
                //userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header);
                userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, 0, 0);
            } else if (language > 0 && encoding != ENCODING_16BIT) {
                if (encoding == ENCODING_7BIT_LOCKING) {
                    //userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, -1, language);
                    userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, 0, language);
                } else if (encoding == ENCODING_7BIT_SINGLE) {
                    //userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, language, -1);
                    userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, language, 0);
                } else if (encoding == ENCODING_7BIT_LOCKING_SINGLE) {
                    userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, language, language);
                } else {
                    //userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header);
                    userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, 0, 0);
                }
                encoding = ENCODING_7BIT;
            } else { // assume UCS-2
                try {
                    userData = encodeUCS2(message, header);
                } catch (UnsupportedEncodingException uex) {
                    Rlog.e(LOG_TAG,
                            "Implausible UnsupportedEncodingException ",
                            uex);
                    return null;
                }
            }
        } catch (EncodeException ex) {
            // Encoding to the 7-bit alphabet failed. Let's see if we can
            // send it as a UCS-2 encoded message
            try {
                userData = encodeUCS2(message, header);
                encoding = ENCODING_16BIT;
            } catch (UnsupportedEncodingException uex) {
                Rlog.e(LOG_TAG,
                        "Implausible UnsupportedEncodingException ",
                        uex);
                return null;
            }
        }

        if (userData != null && (0xff & userData[0]) > MAX_USER_DATA_SEPTETS) {
            Rlog.d(LOG_TAG, "SmsMessage: message is too long");
            return null;
        }

        // write dcs type
        if (encoding == ENCODING_7BIT) {
            bo.write(0x00);
        } else { // assume UCS-2
            bo.write(0x08);
        }

        // write timestamp
        // Because we can't get invalid timestamp which indicate the message arrival service center,
        // we just write 7 0x00 into the pdu

        byte[] scts = parseSCTimestamp(timestamp);
        if (scts != null) {
            bo.write(scts, 0, scts.length);
        } else {
            for (int i = 0; i < 7; ++i) {
                bo.write(0x00);
            }
        }

        bo.write(userData, 0, userData.length);
        ret.encodedMessage = bo.toByteArray();

        return ret;
    }

    static private byte[] parseSCTimestamp(long millis) {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.set(millis);

        byte[] scts = new byte[7];
        scts[0] = intToGsmBCDByte(t.year);
        scts[1] = intToGsmBCDByte(t.month + 1);
        scts[2] = intToGsmBCDByte(t.monthDay);
        scts[3] = intToGsmBCDByte(t.hour);
        scts[4] = intToGsmBCDByte(t.minute);
        scts[5] = intToGsmBCDByte(t.second);
        scts[6] = intToGsmBCDByte(0);

        return scts;
    }

    static private byte intToGsmBCDByte(int value) {
        if (value < 0) {
            Rlog.d(LOG_TAG, "[time invalid value: " + value);
            return (byte) 0;
        }
        value %= 100;
        Rlog.d(LOG_TAG, "[time value: " + value);

        // byte b = (byte)(((value / 10) << 4) + (value % 10));
        byte b = (byte) (((value / 10) & 0x0f) | (((value % 10) << 4) & 0xf0));
        Rlog.d(LOG_TAG, "[time bcd value: " + b);
        return b;
    }

    private static ByteArrayOutputStream getDeliverPduHead(
            String scAddress, String originalAddress, byte mtiByte, DeliverPdu ret) {

        ByteArrayOutputStream bo = new ByteArrayOutputStream(
                MAX_USER_DATA_BYTES + 40);

        if (scAddress == null) {
            ret.encodedScAddress = null;
        } else {
            ret.encodedScAddress = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(
                    scAddress);
        }

        // write mti byte
        bo.write(mtiByte);

        // write original bytes
        byte[] oaBytes;
        oaBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(originalAddress);
        if (oaBytes != null) {
            bo.write((oaBytes.length - 1) * 2
                    - ((oaBytes[oaBytes.length - 1] & 0xf0) == 0xf0 ? 1 : 0));
            bo.write(oaBytes, 0, oaBytes.length);
        } else {
            Rlog.d(LOG_TAG, "write a empty address for deliver pdu");
            bo.write(0);
            bo.write(PhoneNumberUtils.TOA_International);
        }

        // write PID
        bo.write(0);

        return bo;
    }

    private static boolean encodeStringWithSpecialLang(
            CharSequence msgBody,
            int language,
            TextEncodingDetails ted) {

        int septets;

        //1st, try default GSM
        //septets = GsmAlphabet.countGsmSeptetsWithTable(
        //        msgBody, -1, -1);
        septets = GsmAlphabet.countGsmSeptetsUsingTables(
                msgBody, true, 0, 0);
        if (septets != -1) {

            ted.codeUnitCount = septets;
            if (septets > MAX_USER_DATA_SEPTETS) {
                ted.msgCount = (septets / MAX_USER_DATA_SEPTETS_WITH_HEADER) + 1;
                ted.codeUnitsRemaining = MAX_USER_DATA_SEPTETS_WITH_HEADER
                        - (septets % MAX_USER_DATA_SEPTETS_WITH_HEADER);
            } else {
                ted.msgCount = 1;
                ted.codeUnitsRemaining = MAX_USER_DATA_SEPTETS - septets;
            }
            ted.codeUnitSize = ENCODING_7BIT;
            ted.shiftLangId = -1;
            Rlog.d(LOG_TAG, "Try Default: " + language + " " + ted);
            return true;
        }

        //2nd, try locking shift
        //septets = GsmAlphabet.countGsmSeptetsWithTable(
        //        msgBody, -1, language);
        septets = GsmAlphabet.countGsmSeptetsUsingTables(
                msgBody, true, 0, language);
        if (septets != -1) {

            int headerElt[] = {SmsHeader.ELT_ID_NATIONAL_LANGUAGE_LOCKING_SHIFT, 0xffff};
            int maxLength = computeRemainUserDataLength(true, headerElt);

            ted.codeUnitCount = septets;
            if (septets > maxLength) {
                headerElt[1] = SmsHeader.ELT_ID_CONCATENATED_8_BIT_REFERENCE;
                maxLength = computeRemainUserDataLength(true, headerElt);

                ted.msgCount = (septets / maxLength) + 1;
                ted.codeUnitsRemaining = maxLength - (septets % maxLength);
            } else {
                ted.msgCount = 1;
                ted.codeUnitsRemaining = maxLength - septets;
            }
            ted.codeUnitSize = ENCODING_7BIT;
            ted.useLockingShift = true;
            ted.shiftLangId = language;
            Rlog.d(LOG_TAG, "Try Locking Shift: " + language + " " + ted);
            return true;
        }

        //3rd, try single shift
        //septets = GsmAlphabet.countGsmSeptetsWithTable(
        //        msgBody, language, -1);
        septets = GsmAlphabet.countGsmSeptetsUsingTables(
                msgBody, true, language, 0);
        if (septets != -1) {

            int headerElt[] = {SmsHeader.ELT_ID_NATIONAL_LANGUAGE_SINGLE_SHIFT, 0xffff};
            int maxLength = computeRemainUserDataLength(true, headerElt);

            ted.codeUnitCount = septets;
            if (septets > maxLength) {
                headerElt[1] = SmsHeader.ELT_ID_CONCATENATED_8_BIT_REFERENCE;
                maxLength = computeRemainUserDataLength(true, headerElt);

                ted.msgCount = (septets / maxLength) + 1;
                ted.codeUnitsRemaining = maxLength - (septets % maxLength);
            } else {
                ted.msgCount = 1;
                ted.codeUnitsRemaining = maxLength - septets;
            }
            ted.codeUnitSize = ENCODING_7BIT;
            ted.useSingleShift = true;
            ted.shiftLangId = language;
            Rlog.d(LOG_TAG, "Try Single Shift: " + language + " " + ted);
            return true;
        }

        //4th, try locking and single shift
        //septets = GsmAlphabet.countGsmSeptetsWithTable(
        //        msgBody, language, language);
        septets = GsmAlphabet.countGsmSeptetsUsingTables(
                msgBody, true, language, language);
        if (septets != -1) {
            int headerElt[] = {
                    SmsHeader.ELT_ID_NATIONAL_LANGUAGE_LOCKING_SHIFT,
                    SmsHeader.ELT_ID_NATIONAL_LANGUAGE_SINGLE_SHIFT,
                    0xffff};
            int maxLength = computeRemainUserDataLength(true, headerElt);

            ted.codeUnitCount = septets;
            if (septets > maxLength) {
                headerElt[2] = SmsHeader.ELT_ID_CONCATENATED_8_BIT_REFERENCE;
                maxLength = computeRemainUserDataLength(true, headerElt);

                ted.msgCount = (septets / maxLength) + 1;
                ted.codeUnitsRemaining = maxLength - (septets % maxLength);
            } else {
                ted.msgCount = 1;
                ted.codeUnitsRemaining = maxLength - septets;
            }
            ted.codeUnitSize = ENCODING_7BIT;
            ted.useLockingShift = true;
            ted.useSingleShift = true;
            ted.shiftLangId = language;
            Rlog.d(LOG_TAG, "Try Locking & Single Shift: " + language + " " + ted);
            return true;
        }

        Rlog.d(LOG_TAG, "Use UCS2" + language + " " + ted);
        return false;
    }

    private static int getCurrentSysLanguage() {
        int ret;
        String language;

        language = SystemProperties.get("persist.sys.language", null);
        if (language == null) {
            language = SystemProperties.get("ro.product.locale.language", null);
        }

        if (language.equals("tr")) {
            // ret = GsmAlphabet.SHIFT_ID_TURKISH;
            ret = -1;
        } else {
            ret = -1;
        }

        return ret;
    }

    public static int computeRemainUserDataLength(boolean inSeptets, int headerElt[]) {
        int headerBytes = 0;
        int count;
        for (int i = 0; i < headerElt.length; i++) {
            switch (headerElt[i]) {
                case SmsHeader.ELT_ID_CONCATENATED_8_BIT_REFERENCE:
                    headerBytes += SmsHeader.CONCATENATED_8_BIT_REFERENCE_LENGTH;
                    break;
                case SmsHeader.ELT_ID_NATIONAL_LANGUAGE_SINGLE_SHIFT:
                    headerBytes += SmsHeader.NATIONAL_LANGUAGE_SINGLE_SHIFT_LENGTH;
                    break;
                case SmsHeader.ELT_ID_NATIONAL_LANGUAGE_LOCKING_SHIFT:
                    headerBytes += SmsHeader.NATIONAL_LANGUAGE_LOCKING_SHIFT_LENGTH;
                    break;
                default:
                    break;
            }
        }

        if (headerBytes != 0) {
            headerBytes++; // header length
        }

        count = MAX_USER_DATA_BYTES - headerBytes;
        if (inSeptets) {
            count = count * 8 / 7;
        }

        //Log.d(LOG_TAG, "computeRemainUserDataLength: inSeptets: "+ inSeptets +
        //        " , max: "+ count + " header:" + headerBytes);

        return count;
    }

    // MTK-START [ALPS00094531] Orange feature SMS Encoding Type Setting by mtk80589 in 2011.11.22
    /**
     * Calculate the number of septets needed to encode the message.
     *
     * @param msgBody the message to encode
     * @param use7bitOnly ignore (but still count) illegal characters if true
     * @param encodingType text encoding type(7-bit, 16-bit or automatic)
     * @return TextEncodingDetails
     */
    public static TextEncodingDetails calculateLength(CharSequence msgBody,
            boolean use7bitOnly, int encodingType) {
        TextEncodingDetails ted = GsmAlphabet.countGsmSeptets(msgBody, use7bitOnly);

        if (encodingType == ENCODING_16BIT) {
            Rlog.d(LOG_TAG, "input mode is unicode");
            ted = null;
        }
        if (ted == null) {
            Rlog.d(LOG_TAG, "7-bit encoding fail");
            ted = new TextEncodingDetails();
            int octets = msgBody.length() * 2;
            ted.codeUnitCount = msgBody.length();
            if (octets > MAX_USER_DATA_BYTES) {
                ted.msgCount = (octets + (MAX_USER_DATA_BYTES_WITH_HEADER - 1)) /
                        MAX_USER_DATA_BYTES_WITH_HEADER;
                ted.codeUnitsRemaining = ((ted.msgCount *
                        MAX_USER_DATA_BYTES_WITH_HEADER) - octets) / 2;
            } else {
                ted.msgCount = 1;
                ted.codeUnitsRemaining = (MAX_USER_DATA_BYTES - octets) / 2;
            }
            ted.codeUnitSize = ENCODING_16BIT;
        }
        return ted;
    }
    // MTK-END [ALPS00094531] Orange feature SMS Encoding Type Setting by mtk80589 in 2011.11.22

    /**
     * Get an SMS-SUBMIT PDU for a destination address and a message using the
     * specified encoding.
     *
     * @param scAddress Service Centre address.  Null means use default.
     * @param encoding Encoding defined by constants in android.telephony.SmsMessage.ENCODING_*
     * @param languageTable
     * @param languageShiftTable
     * @return a <code>SubmitPdu</code> containing the encoded SC
     *         address, if applicable, and the encoded message.
     *         Returns null on encode error.
     * @hide
     */
    public static SubmitPdu getSubmitPdu(String scAddress,
            String destinationAddress, String message,
            boolean statusReportRequested, byte[] header, int encoding,
            int languageTable, int languageShiftTable, int validityPeriod) {

        // Perform null parameter checks.
        if (message == null || destinationAddress == null) {
            return null;
        }

        if (encoding == ENCODING_UNKNOWN) {
            // Find the best encoding to use
            TextEncodingDetails ted = calculateLength(message, false);
            encoding = ted.codeUnitSize;
            languageTable = ted.languageTable;
            languageShiftTable = ted.languageShiftTable;

            if (encoding == ENCODING_7BIT && (languageTable != 0 || languageShiftTable != 0)) {
                if (header != null) {
                    SmsHeader smsHeader = SmsHeader.fromByteArray(header);
                    if (smsHeader.languageTable != languageTable
                            || smsHeader.languageShiftTable != languageShiftTable) {
                        Rlog.w(LOG_TAG, "Updating language table in SMS header: "
                                + smsHeader.languageTable + " -> " + languageTable + ", "
                                + smsHeader.languageShiftTable + " -> " + languageShiftTable);
                        smsHeader.languageTable = languageTable;
                        smsHeader.languageShiftTable = languageShiftTable;
                        header = SmsHeader.toByteArray(smsHeader);
                    }
                } else {
                    SmsHeader smsHeader = new SmsHeader();
                    smsHeader.languageTable = languageTable;
                    smsHeader.languageShiftTable = languageShiftTable;
                    header = SmsHeader.toByteArray(smsHeader);
                }
            }
        }

        SubmitPdu ret = new SubmitPdu();
        // MTI = SMS-SUBMIT, UDHI = header != null
        byte mtiByte = (byte) (0x01 | (header != null ? 0x40 : 0x00));
        if (validityPeriod >= 0 && validityPeriod <= 255) {
            mtiByte |= 0x10;
        } else {
            Rlog.d(LOG_TAG, "invalid VP: " + validityPeriod);
        }
        ByteArrayOutputStream bo = getSubmitPduHead(
                scAddress, destinationAddress, mtiByte,
                statusReportRequested, ret);

        // User Data (and length)
        byte[] userData;
        try {
            if (encoding == ENCODING_7BIT) {
                userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header,
                        languageTable, languageShiftTable);
            } else { // assume UCS-2
                try {
                    userData = encodeUCS2(message, header);
                } catch (UnsupportedEncodingException uex) {
                    Rlog.e(LOG_TAG,
                            "Implausible UnsupportedEncodingException ",
                            uex);
                    return null;
                }
            }
        } catch (EncodeException ex) {
            // Encoding to the 7-bit alphabet failed. Let's see if we can
            // send it as a UCS-2 encoded message
            try {
                userData = encodeUCS2(message, header);
                encoding = ENCODING_16BIT;
            } catch (UnsupportedEncodingException uex) {
                Rlog.e(LOG_TAG,
                        "Implausible UnsupportedEncodingException ",
                        uex);
                return null;
            }
        }

        if (encoding == ENCODING_7BIT) {
            if ((0xff & userData[0]) > MAX_USER_DATA_SEPTETS) {
                // Message too long
                // MTK-START [mtk04070][111223][ALPS00106134]Merge to ICS 4.0.3
                Rlog.e(LOG_TAG, "Message too long (" + (0xff & userData[0]) + " septets)");
                // MTK-END [mtk04070][111223][ALPS00106134]Merge to ICS 4.0.3
                return null;
            }
            // TP-Data-Coding-Scheme
            // Default encoding, uncompressed
            // To test writing messages to the SIM card, change this value 0x00
            // to 0x12, which means "bits 1 and 0 contain message class, and the
            // class is 2". Note that this takes effect for the sender. In other
            // words, messages sent by the phone with this change will end up on
            // the receiver's SIM card. You can then send messages to yourself
            // (on a phone with this change) and they'll end up on the SIM card.
            bo.write(0x00);
        } else { // assume UCS-2
            if ((0xff & userData[0]) > MAX_USER_DATA_BYTES) {
                // Message too long
                // MTK-START [mtk04070][111223][ALPS00106134]Merge to ICS 4.0.3
                Rlog.e(LOG_TAG, "Message too long (" + (0xff & userData[0]) + " bytes)");
                // MTK-END [mtk04070][111223][ALPS00106134]Merge to ICS 4.0.3
                return null;
            }
            // TP-Data-Coding-Scheme
            // UCS-2 encoding, uncompressed
            bo.write(0x08);
        }

        if (validityPeriod >= 0 && validityPeriod <= 255) {
            Rlog.d(LOG_TAG, "write validity period into pdu: " + validityPeriod);
            bo.write(validityPeriod);
        }

        // (no TP-Validity-Period)
        bo.write(userData, 0, userData.length);
        ret.encodedMessage = bo.toByteArray();
        return ret;
    }
}
