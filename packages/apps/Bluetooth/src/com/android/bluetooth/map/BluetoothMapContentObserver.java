/*
* Copyright (C) 2013 Samsung System LSI
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
package com.android.bluetooth.map;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xmlpull.v1.XmlSerializer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Inbox;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Xml;

import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.bluetooth.map.BluetoothMapbMessageMmsEmail.MimePart;
import com.google.android.mms.pdu.PduHeaders;
import com.mediatek.telephony.SmsManagerEx;

public class BluetoothMapContentObserver {
    private static final String TAG = "[MAP]BluetoothMapContentObserver";

    private static final boolean D = true;
    private static final boolean V = true;

    private Context mContext;
    private ContentResolver mResolver;
    private BluetoothMnsObexClient mMnsClient;
    private int mMasId;

    public static final int DELETED_THREAD_ID = -1;

    /* X-Mms-Message-Type field types. These are from PduHeaders.java */
    public static final int MESSAGE_TYPE_RETRIEVE_CONF = 0x84;

    private TYPE mSmsType;

    static final String[] SMS_PROJECTION = new String[] {
        BaseColumns._ID,
        Sms.THREAD_ID,
        Sms.ADDRESS,
        Sms.BODY,
        Sms.DATE,
        Sms.READ,
        Sms.TYPE,
        Sms.STATUS,
        Sms.LOCKED,
        Sms.ERROR_CODE,
        Sms.SIM_ID,
    };

    static final String[] MMS_PROJECTION = new String[] {
        BaseColumns._ID,
        Mms.THREAD_ID,
        Mms.MESSAGE_ID,
        Mms.MESSAGE_SIZE,
        Mms.SUBJECT,
        Mms.CONTENT_TYPE,
        Mms.TEXT_ONLY,
        Mms.DATE,
        Mms.DATE_SENT,
        Mms.READ,
        Mms.MESSAGE_BOX,
        Mms.MESSAGE_TYPE,
        Mms.STATUS,
    };

    public BluetoothMapContentObserver(final Context context) {
        mContext = context;
        mResolver = mContext.getContentResolver();

        mSmsType = getSmsType();
    }

    private TYPE getSmsType() {
        TYPE smsType = null;
        TelephonyManager tm = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);

        if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            smsType = TYPE.SMS_GSM;
        } else if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            smsType = TYPE.SMS_CDMA;
        }

        return smsType;
    }

    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (V) Log.d(TAG, "[mObserver.onChange] on thread: " + Thread.currentThread().getId()
                + " Uri: " + uri.toString() + " selfchange: " + selfChange);

            handleMsgListChanges();
        }
    };

    private static final String folderSms[] = {
        "",
        "inbox",
        "sent",
        "draft",
        "outbox",
        "outbox",
        "outbox",
        "inbox",
        "inbox",
    };

    private static final String folderMms[] = {
        "",
        "inbox",
        "sent",
        "draft",
        "outbox",
    };

    private class Event {
        String eventType;
        long handle;
        String folder;
        String oldFolder;
        TYPE msgType;

        public Event(String eventType, long handle, String folder,
            String oldFolder, TYPE msgType) {
            String PATH = "telecom/msg/";
            this.eventType = eventType;
            this.handle = handle;
            if (folder != null) {
                this.folder = PATH + folder;
            } else {
                this.folder = null;
            }
            if (oldFolder != null) {
                this.oldFolder = PATH + oldFolder;
            } else {
                this.oldFolder = null;
            }
            this.msgType = msgType;
        }

        public byte[] encode() throws UnsupportedEncodingException {
            StringWriter sw = new StringWriter();
            XmlSerializer xmlEvtReport = Xml.newSerializer();
            try {
                xmlEvtReport.setOutput(sw);
                xmlEvtReport.startDocument(null, null);
                xmlEvtReport.text("\n");
                xmlEvtReport.startTag("", "MAP-event-report");
                xmlEvtReport.attribute("", "version", "1.0");

                xmlEvtReport.startTag("", "event");
                xmlEvtReport.attribute("", "type", eventType);
                xmlEvtReport.attribute("", "handle", BluetoothMapUtils.getMapHandle(handle, msgType));
                if (folder != null) {
                    xmlEvtReport.attribute("", "folder", folder);
                }
                if (oldFolder != null) {
                    xmlEvtReport.attribute("", "old_folder", oldFolder);
                }
                xmlEvtReport.attribute("", "msg_type", msgType.name());
                xmlEvtReport.endTag("", "event");

                xmlEvtReport.endTag("", "MAP-event-report");
                xmlEvtReport.endDocument();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (V) System.out.println(sw.toString());

            return sw.toString().getBytes("UTF-8");
        }
    }

    private class Msg {
        long id;
        int type;

        public Msg(long id, int type) {
            this.id = id;
            this.type = type;
        }
    }

    private Map<Long, Msg> mMsgListSms =
        Collections.synchronizedMap(new HashMap<Long, Msg>());

    private Map<Long, Msg> mMsgListMms =
        Collections.synchronizedMap(new HashMap<Long, Msg>());

    public void registerObserver(BluetoothMnsObexClient mns, int masId) {
        if (V) Log.d(TAG, "[registerObserver]");
        /* Use MmsSms Uri since the Sms Uri is not notified on deletes */
        mMasId = masId;
        mMnsClient = mns;
        mResolver.registerContentObserver(MmsSms.CONTENT_URI, false, mObserver);
        initMsgList();
    }

    public void unregisterObserver() {
        if (V) Log.d(TAG, "[unregisterObserver]");
        mResolver.unregisterContentObserver(mObserver);
        mMnsClient = null;
    }

    private void sendEvent(Event evt) {
        Log.d(TAG, "[sendEvent]: " + evt.eventType + " " + evt.handle + " "
        + evt.folder + " " + evt.oldFolder + " " + evt.msgType.name());

        if (mMnsClient == null) {
            Log.d(TAG, "[sendEvent]: No MNS client registered - don't send event");
            return;
        }

        try {
            mMnsClient.sendEvent(evt.encode(), mMasId);
        } catch (UnsupportedEncodingException ex) {
            /* do nothing */
            Log.d(TAG, "[sendEvent]: UnsupportedEncodingException = " + ex);
        }
    }

    private void initMsgList() {
        if (V) Log.d(TAG, "[initMsgList]");

        mMsgListSms.clear();
        mMsgListMms.clear();

        HashMap<Long, Msg> msgListSms = new HashMap<Long, Msg>();

        Cursor c = mResolver.query(Sms.CONTENT_URI,
            SMS_PROJECTION, null, null, null);

        if (c != null && c.moveToFirst()) {
            do {
                long id = c.getLong(c.getColumnIndex(BaseColumns._ID));
                int type = c.getInt(c.getColumnIndex(Sms.TYPE));

                Msg msg = new Msg(id, type);
                msgListSms.put(id, msg);
            } while (c.moveToNext());
        }
        if (c != null) {
            c.close();
        }

        mMsgListSms = msgListSms;

        HashMap<Long, Msg> msgListMms = new HashMap<Long, Msg>();

        c = mResolver.query(Mms.CONTENT_URI,
            MMS_PROJECTION, null, null, null);

        if (c != null && c.moveToFirst()) {
            do {
                long id = c.getLong(c.getColumnIndex(BaseColumns._ID));
                int type = c.getInt(c.getColumnIndex(Mms.MESSAGE_BOX));

                Msg msg = new Msg(id, type);
                msgListMms.put(id, msg);
            } while (c.moveToNext());
        }
        if (c != null) {
            c.close();
        }

        mMsgListMms = msgListMms;
    }

    private void handleMsgListChangesSms() {
        if (V) Log.d(TAG, "[handleMsgListChangesSms] enter");

        HashMap<Long, Msg> msgListSms = new HashMap<Long, Msg>();

        Cursor c = mResolver.query(Sms.CONTENT_URI,
            SMS_PROJECTION, null, null, null);

        synchronized(mMsgListSms) {
            if (c != null && c.moveToFirst()) {
                do {
                    long id = c.getLong(c.getColumnIndex(BaseColumns._ID));
                    int type = c.getInt(c.getColumnIndex(Sms.TYPE));

                    Msg msg = mMsgListSms.remove(id);

                    if (msg == null) {
                        /* New message */
                        msg = new Msg(id, type);
                        msgListSms.put(id, msg);

                        if (folderSms[type].equals("inbox")) {
                            Log.d(TAG, "[handleMsgListChangesSms] NewMessage");
                            Event evt = new Event("NewMessage", id, folderSms[type],
                                null, mSmsType);
                            sendEvent(evt);
                        }
                    } else {
                        /* Existing message */
                        if (type != msg.type) {
                            Log.d(TAG, "[handleMsgListChangesSms] MessageShift new type: "
                                    + type + " old type: " + msg.type);
                            Event evt = new Event("MessageShift", id, folderSms[type],
                                folderSms[msg.type], mSmsType);
                            sendEvent(evt);
                            msg.type = type;
                        }
                        msgListSms.put(id, msg);
                    }
                } while (c.moveToNext());
            }
            if (c != null) {
                c.close();
            }

            for (Msg msg : mMsgListSms.values()) {
                Log.d(TAG, "[handleMsgListChangesSms] MessageDeleted");
                Event evt = new Event("MessageDeleted", msg.id, "deleted",
                    folderSms[msg.type], mSmsType);
                sendEvent(evt);
            }

            mMsgListSms = msgListSms;
        }
    }

    private void handleMsgListChangesMms() {
        if (V) Log.d(TAG, "[handleMsgListChangesMms] enter");

        HashMap<Long, Msg> msgListMms = new HashMap<Long, Msg>();

        Cursor c = mResolver.query(Mms.CONTENT_URI,
            MMS_PROJECTION, null, null, null);

        synchronized(mMsgListMms) {
            if (c != null && c.moveToFirst()) {
                do {
                    long id = c.getLong(c.getColumnIndex(BaseColumns._ID));
                    int type = c.getInt(c.getColumnIndex(Mms.MESSAGE_BOX));
                    int mtype = c.getInt(c.getColumnIndex(Mms.MESSAGE_TYPE));

                    Msg msg = mMsgListMms.remove(id);

                    if (msg == null) {
                        /* New message - only notify on retrieve conf */
                        if (folderMms[type].equals("inbox") &&
                            mtype != MESSAGE_TYPE_RETRIEVE_CONF) {
                            Log.d(TAG, "[handleMsgListChangesMms] continue");
                                continue;
                        }

                        msg = new Msg(id, type);
                        msgListMms.put(id, msg);

                        if (folderMms[type].equals("inbox")) {
                            Log.d(TAG, "[handleMsgListChangesMms] NewMessage");
                            Event evt = new Event("NewMessage", id, folderMms[type],
                                null, TYPE.MMS);
                            sendEvent(evt);
                        }
                    } else {
                        /* Existing message */
                        if (type != msg.type) {
                            Log.d(TAG, "[handleMsgListChangesMms] MessageShift new type: "
                                    + type + " old type: " + msg.type);
                            Event evt = new Event("MessageShift", id, folderMms[type],
                                folderMms[msg.type], TYPE.MMS);
                            sendEvent(evt);
                            msg.type = type;

                            if (folderMms[type].equals("sent")) {
                                Log.d(TAG, "[handleMsgListChangesMms] SendingSuccess");
                                evt = new Event("SendingSuccess", id,
                                    folderSms[type], null, TYPE.MMS);
                                sendEvent(evt);
                            }
                        }
                        msgListMms.put(id, msg);
                    }
                } while (c.moveToNext());
            }
            if (c != null) {
                c.close();
            }

            for (Msg msg : mMsgListMms.values()) {
                Log.d(TAG, "[handleMsgListChangesMms] MessageDeleted = " + msg.id);
                Event evt = new Event("MessageDeleted", msg.id, "deleted",
                    folderMms[msg.type], TYPE.MMS);
                sendEvent(evt);
            }

            mMsgListMms = msgListMms;
        }
    }

    private void handleMsgListChanges() {
        handleMsgListChangesSms();
        handleMsgListChangesMms();
    }

    private boolean deleteMessageMms(long handle) {
        boolean res = false;
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, handle);
        Cursor c = mResolver.query(uri, null, null, null, null);
        if (c != null && c.moveToFirst()) {
            /* Move to deleted folder, or delete if already in deleted folder */
            int threadId = c.getInt(c.getColumnIndex(Mms.THREAD_ID));
            Log.d(TAG, "[deleteMessageMms] threadId = " + threadId);
            if (threadId != DELETED_THREAD_ID) {
                /* Set deleted thread id */
                ContentValues contentValues = new ContentValues();
                contentValues.put(Mms.THREAD_ID, DELETED_THREAD_ID);
                mResolver.update(uri, contentValues, null, null);
            } else {
                /* Delete from observer message list to avoid delete notifications */
                mMsgListMms.remove(handle);
                /* Delete message */
                mResolver.delete(uri, null, null);
            }
            res = true;
        }
        if (c != null) {
            c.close();
        }
        return res;
    }

    private void updateThreadIdMms(Uri uri, long threadId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Mms.THREAD_ID, threadId);
        mResolver.update(uri, contentValues, null, null);
    }

    private boolean unDeleteMessageMms(long handle) {
        boolean res = false;
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, handle);
        Cursor c = mResolver.query(uri, null, null, null, null);

        if (c != null && c.moveToFirst()) {
            int threadId = c.getInt(c.getColumnIndex(Mms.THREAD_ID));
            Log.d(TAG, "[unDeleteMessageMms] threadId = " + threadId);
            if (threadId == DELETED_THREAD_ID) {
                /* Restore thread id from address, or if no thread for address
                 * create new thread by insert and remove of fake message */
                String address;
                long id = c.getLong(c.getColumnIndex(BaseColumns._ID));
                int msgBox = c.getInt(c.getColumnIndex(Mms.MESSAGE_BOX));
                if (msgBox == Mms.MESSAGE_BOX_INBOX) {
                    address = BluetoothMapContent.getAddressMms(mResolver, id,
                        BluetoothMapContent.MMS_FROM);
                } else {
                    address = BluetoothMapContent.getAddressMms(mResolver, id,
                        BluetoothMapContent.MMS_TO);
                }
                Set<String> recipients = new HashSet<String>();
                recipients.addAll(Arrays.asList(address));
                updateThreadIdMms(uri, Telephony.Threads.getOrCreateThreadId(mContext, recipients));
            } else {
                Log.d(TAG, "[unDeleteMessageMms] Message not in deleted folder: handle " + handle
                    + " threadId " + threadId);
            }
            res = true;
        }
        if (c != null) {
            c.close();
        }
        return res;
    }

    private boolean deleteMessageSms(long handle) {
        boolean res = false;
        Uri uri = ContentUris.withAppendedId(Sms.CONTENT_URI, handle);
        Cursor c = mResolver.query(uri, null, null, null, null);

        if (c != null && c.moveToFirst()) {
            /* Move to deleted folder, or delete if already in deleted folder */
            int threadId = c.getInt(c.getColumnIndex(Sms.THREAD_ID));
            Log.d(TAG, "[deleteMessageSms] threadId = " + threadId);
            if (threadId != DELETED_THREAD_ID) {
                /* Set deleted thread id */
                ContentValues contentValues = new ContentValues();
                contentValues.put(Sms.THREAD_ID, DELETED_THREAD_ID);
                mResolver.update(uri, contentValues, null, null);
            } else {
                /* Delete from observer message list to avoid delete notifications */
                mMsgListSms.remove(handle);
                /* Delete message */
                mResolver.delete(uri, null, null);
            }
            res = true;
        }
        if (c != null) {
            c.close();
        }
        return res;
    }

    private void updateThreadIdSms(Uri uri, long threadId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Sms.THREAD_ID, threadId);
        mResolver.update(uri, contentValues, null, null);
    }

    private boolean unDeleteMessageSms(long handle) {
        boolean res = false;
        Uri uri = ContentUris.withAppendedId(Sms.CONTENT_URI, handle);
        Cursor c = mResolver.query(uri, null, null, null, null);

        if (c != null && c.moveToFirst()) {
            int threadId = c.getInt(c.getColumnIndex(Sms.THREAD_ID));
            Log.d(TAG, "[unDeleteMessageSms] threadId = " + threadId);
            if (threadId == DELETED_THREAD_ID) {
                String address = c.getString(c.getColumnIndex(Sms.ADDRESS));
                Set<String> recipients = new HashSet<String>();
                recipients.addAll(Arrays.asList(address));
                updateThreadIdSms(uri, Telephony.Threads.getOrCreateThreadId(mContext, recipients));
            } else {
                Log.d(TAG, "[unDeleteMessageSms] Message not in deleted folder: handle " + handle
                    + " threadId " + threadId);
            }
            res = true;
        }
        if (c != null) {
            c.close();
        }
        return res;
    }

    public boolean setMessageStatusDeleted(long handle, TYPE type, int statusValue) {
        boolean res = false;
        if (D) Log.d(TAG, "[setMessageStatusDeleted]: msgId = " + handle
            + " type = " + type + " value = " + statusValue);

        if (statusValue == BluetoothMapAppParams.STATUS_VALUE_YES) {
            if (type == TYPE.SMS_GSM || type == TYPE.SMS_CDMA) {
                res = deleteMessageSms(handle);
            } else if (type == TYPE.MMS) {
                res = deleteMessageMms(handle);
            }
        } else if (statusValue == BluetoothMapAppParams.STATUS_VALUE_NO) {
            if (type == TYPE.SMS_GSM || type == TYPE.SMS_CDMA) {
                res = unDeleteMessageSms(handle);
            } else if (type == TYPE.MMS) {
                res = unDeleteMessageMms(handle);
            }
        }
        return res;
    }

    public boolean setMessageStatusRead(long handle, TYPE type, int statusValue) {
        boolean res = true;

        if (D) Log.d(TAG, "[setMessageStatusRead]: handle " + handle
            + " type " + type + " value " + statusValue);

        /* Approved MAP spec errata 3445 states that read status initiated */
        /* by the MCE shall change the MSE read status. */

        if (type == TYPE.SMS_GSM || type == TYPE.SMS_CDMA) {
            Uri uri = ContentUris.withAppendedId(Sms.CONTENT_URI, handle);

            ContentValues contentValues = new ContentValues();
            contentValues.put(Sms.READ, statusValue);
            mResolver.update(uri, contentValues, null, null);
        } else if (type == TYPE.MMS) {
            Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, handle);

            ContentValues contentValues = new ContentValues();
            contentValues.put(Mms.READ, statusValue);
            mResolver.update(uri, contentValues, null, null);
        }

        return res;
    }

    private class PushMsgInfo {
        long id;
        int transparent;
        int retry;
        String phone;
        Uri uri;
        int parts;
        int partsSent;
        int partsDelivered;
        boolean resend;

        public PushMsgInfo(long id, int transparent,
            int retry, String phone, Uri uri) {
            this.id = id;
            this.transparent = transparent;
            this.retry = retry;
            this.phone = phone;
            this.uri = uri;
            this.resend = false;
        };
    }

    private Map<Long, PushMsgInfo> mPushMsgList =
        Collections.synchronizedMap(new HashMap<Long, PushMsgInfo>());

    public long pushMessage(BluetoothMapbMessage msg, String folder,
        BluetoothMapAppParams ap, long simId) throws IllegalArgumentException {
        if (D) Log.d(TAG, "[pushMessage] enter");
        ArrayList<BluetoothMapbMessage.vCard> recipientList = msg.getRecipients();
        int transparent = (ap.getTransparent() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER) ?
                0 : ap.getTransparent();
        int retry = ap.getRetry();
        int charset = ap.getCharset();
        long handle = -1;

        if (recipientList == null) {
            Log.d(TAG, "[pushMessage] empty recipient list");
            return -1;
        }

        for (BluetoothMapbMessage.vCard recipient : recipientList) {
            if(recipient.getEnvLevel() == 0) // Only send the message to the top level recipient
            {
                /* Only send to first address */
                String phone = recipient.getFirstPhoneNumber();
                boolean read = false;
                boolean deliveryReport = true;

                switch(msg.getType()){
                    case MMS:
                    {
                        /* Send message if folder is outbox */
                        /* to do, support MMS in the future */
                        Log.d(TAG, "[pushMessage] mms to folder : " + folder);
                        if (folder.equals("outbox")) {
                            handle = sendMmsMessage(folder, phone,
                                (BluetoothMapbMessageMmsEmail)msg, simId);
                        } else if (folder.equals("draft")) {
                            handle = pushMmsToFolder(Mms.MESSAGE_BOX_DRAFTS, 
                                phone, (BluetoothMapbMessageMmsEmail)msg, simId);
                        } else {
                            Log.d(TAG, "[pushMessage] wrong folder to push");
                        }

                        break;
                    }
                    case SMS_GSM: //fall-through
                    case SMS_CDMA:
                    {
                        /* Add the message to the database */
                        String msgBody = ((BluetoothMapbMessageSms) msg).getSmsBody();

                        Uri contentUri;
                        /// M: Map Gemini Feature, Mms Draft simId = -1
                        if (simId == BluetoothMapSimManager.INVALID_SIMID) {
                            contentUri = Uri.parse("content://sms/draft");
                            simId = -1;
                        } else {
                            contentUri = Uri.parse("content://sms/" + folder);
                        }

                        Log.d(TAG, "[pushMessage] contentUri: " + contentUri + " simId: " + simId);
                        // String defaultSms = Sms.getDefaultSmsPackage(mContext);
                        Uri uri = Sms.addMessageToUri(mResolver, contentUri, phone, msgBody, "", null,
                                System.currentTimeMillis(), read, deliveryReport, -1, (int) simId);
                        if (uri == null) {
                            Log.d(TAG, "[pushMessage] - failure on add to uri " + contentUri);
                            return -1;
                        }

                        handle = Long.parseLong(uri.getLastPathSegment());

                        /* Send message if folder is outbox */
                        if (folder.equals("outbox") && simId > BluetoothMapSimManager.INVALID_SIMID) {
                            PushMsgInfo msgInfo = new PushMsgInfo(handle, transparent,
                                retry, phone, uri);
                            mPushMsgList.put(handle, msgInfo);
                            sendMessage(msgInfo, msgBody, simId);
                        }
                        break;
                    }
                    case EMAIL:
                    {
                        break;
                    }
                }

            }
        }

        /* If multiple recipients return handle of last */
        return handle;
    }



    public long sendMmsMessage(String folder,String to_address,
            BluetoothMapbMessageMmsEmail msg, long simId) {
        /*
         *strategy:
         *1) parse message into parts
         *if folder is outbox/drafts:
         *2) push message to draft
         *if folder is outbox:
         *3) move message to outbox (to trigger the mms app to add msg to pending_messages list)
         *4) send intent to mms app in order to wake it up.
         *else if folder !outbox:
         *1) push message to folder
         * */
        if (folder != null && (folder.equalsIgnoreCase("outbox")||  folder.equalsIgnoreCase("drafts"))) {
            long handle = pushMmsToFolder(Mms.MESSAGE_BOX_DRAFTS, to_address, msg, simId);
            /* if invalid handle (-1) then just return the handle - else continue sending (if folder is outbox) */
            if (BluetoothMapAppParams.INVALID_VALUE_PARAMETER != handle
                    && folder.equalsIgnoreCase("outbox")
                    && simId > BluetoothMapSimManager.INVALID_SIMID) {
                moveDraftToOutbox(handle, simId);

                Intent sendIntent = new Intent("android.intent.action.MMS_SEND_OUTBOX_MSG");
                Log.d(TAG, "[sendMmsMessage] broadcasting intent: "+sendIntent.toString());
                mContext.sendBroadcast(sendIntent);
            }
            return handle;
        } else {
            /* not allowed to push mms to anything but outbox/drafts */
            throw  new IllegalArgumentException("Cannot push message to other folders than outbox/drafts");
        }

    }


    private void moveDraftToOutbox(long handle, long simId) {
        ContentResolver contentResolver = mContext.getContentResolver();
        /*Move message by changing the msg_box value in the content provider database */
        if (handle != -1) {
            String whereClause = " _id= " + handle;
            Uri uri = Uri.parse("content://mms");
            Cursor queryResult = contentResolver.query(uri, null, whereClause, null, null);
            if (queryResult != null) {
                if (queryResult.getCount() > 0) {
                    queryResult.moveToFirst();
                    ContentValues data = new ContentValues();
                    /* set folder to be outbox */
                    data.put("msg_box", Mms.MESSAGE_BOX_OUTBOX);
                    int count = contentResolver.update(uri, data, whereClause, null);
                    Log.d(TAG, "[moveDraftToOutbox] moved draft MMS to outbox Uri = "
                        + uri + " update count : " + count);
                    if (count == 1) {
                        ContentValues value = new ContentValues();
                        value.put(MmsSms.PendingMessages.SIM_ID, simId);
                        count = contentResolver.update(MmsSms.PendingMessages.CONTENT_URI, value,
                            MmsSms.PendingMessages.MSG_ID + "=" + handle, null);
                        Log.d(TAG, "[moveDraftToOutbox] setPendingMessa count = " + count);
                    }
                }
                queryResult.close();
            }else {
                Log.d(TAG, "[moveDraftToOutbox] Could not move draft to outbox ");
            }
        }
    }
    private long pushMmsToFolder(int folder, String to_address, BluetoothMapbMessageMmsEmail msg, long simId) {
        /**
         * strategy:
         * 1) parse msg into parts + header
         * 2) create thread id (abuse the ease of adding an SMS to get id for thread)
         * 3) push parts into content://mms/parts/ table
         * 3)
         */

        ContentValues values = new ContentValues();
        values.put("msg_box", folder);

        values.put("read", 0);
        values.put("seen", 0);
        values.put("sub", msg.getSubject());
        values.put("sub_cs", 106);
        values.put("ct_t", "application/vnd.wap.multipart.related");
        values.put("exp", 604800);
        values.put("m_cls", PduHeaders.MESSAGE_CLASS_PERSONAL_STR);
        values.put("m_type", PduHeaders.MESSAGE_TYPE_SEND_REQ);
        values.put("v", PduHeaders.CURRENT_MMS_VERSION);
        values.put("pri", PduHeaders.PRIORITY_NORMAL);
        values.put("rr", PduHeaders.VALUE_NO);
        values.put("tr_id", "T"+ Long.toHexString(System.currentTimeMillis()));
        values.put("d_rpt", PduHeaders.VALUE_NO);
        values.put("locked", 0);
        /// M: Map Gemini Feature, Mms Draft simId = -1
        if (simId == BluetoothMapSimManager.INVALID_SIMID) {
            simId = -1;
        }
        values.put("sim_id", simId);
        if(msg.getTextOnly() == true)
            values.put("text_only", true);

        values.put("m_size", msg.getSize());

     // Get thread id
        Set<String> recipients = new HashSet<String>();
        recipients.addAll(Arrays.asList(to_address));
        values.put("thread_id", Telephony.Threads.getOrCreateThreadId(mContext, recipients));
        Uri uri = Uri.parse("content://mms");

        ContentResolver cr = mContext.getContentResolver();
        uri = cr.insert(uri, values);

        if (uri == null) {
            // unable to insert MMS
            Log.e(TAG, "[pushMmsToFolder] Unabled to insert MMS " + values + "Uri: " + uri);
            return -1;
        }

        long handle = Long.parseLong(uri.getLastPathSegment());
        if (V){
            Log.v(TAG, "[pushMmsToFolder] NEW URI " + uri.toString() + ";" + values);
        }
        try {
            if(V) Log.v(TAG, "[pushMmsToFolder] Adding " + msg.getMimeParts().size() + " parts to the data base.");
        for(MimePart part : msg.getMimeParts()) {
            int count = 0;
            count++;
            values.clear();
            if(part.contentType != null &&  part.contentType.toUpperCase().contains("TEXT")) {
                values.put("ct", "text/plain");
                values.put("chset", 106);
                if(part.partName != null) {
                    values.put("fn", part.partName);
                    values.put("name", part.partName);
                } else if(part.contentId == null && part.contentLocation == null) {
                    /* We must set at least one part identifier */
                    values.put("fn", "text_" + count +".txt");
                    values.put("name", "text_" + count +".txt");
                }
                if(part.contentId != null) {
                    values.put("cid", part.contentId);
                }
                if(part.contentLocation != null)
                    values.put("cl", part.contentLocation);
                if(part.contentDisposition != null)
                    values.put("cd", part.contentDisposition);
                values.put("text", new String(part.data, "UTF-8"));
                uri = Uri.parse("content://mms/" + handle + "/part");
                uri = cr.insert(uri, values);
                if(V) Log.v(TAG, "[pushMmsToFolder] Added TEXT part");

            } else if (part.contentType != null &&  part.contentType.toUpperCase().contains("SMIL")){

                values.put("seq", -1);
                values.put("ct", "application/smil");
                if(part.contentId != null)
                    values.put("cid", part.contentId);
                if(part.contentLocation != null)
                    values.put("cl", part.contentLocation);
                if(part.contentDisposition != null)
                    values.put("cd", part.contentDisposition);
                values.put("fn", "smil.xml");
                values.put("name", "smil.xml");
                values.put("text", new String(part.data, "UTF-8"));

                uri = Uri.parse("content://mms/" + handle + "/part");
                uri = cr.insert(uri, values);
                if(V) Log.v(TAG, "[pushMmsToFolder] Added SMIL part");

            }else /*VIDEO/AUDIO/IMAGE*/ {
                writeMmsDataPart(handle, part, count);
                if(V) Log.v(TAG, "[pushMmsToFolder] Added OTHER part");
            }
            if (uri != null && V){
                Log.v(TAG, "[pushMmsToFolder] Added part with content-type: "+ part.contentType + " to Uri: " + uri.toString());
            }
        }
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "[pushMmsToFolder] " + e);
        } catch (IOException e) {
            Log.w(TAG, "[pushMmsToFolder] " + e);
        }

        values.clear();
        values.put("contact_id", "null");
        values.put("address", "insert-address-token");
        values.put("type", BluetoothMapContent.MMS_FROM);
        values.put("charset", 106);

        uri = Uri.parse("content://mms/" + handle + "/addr");
        uri = cr.insert(uri, values);
        if (uri != null && V){
            Log.v(TAG, "[pushMmsToFolder] NEW URI " + uri.toString());
        }

        values.clear();
        values.put("contact_id", "null");
        values.put("address", to_address);
        values.put("type", BluetoothMapContent.MMS_TO);
        values.put("charset", 106);

        uri = Uri.parse("content://mms/" + handle + "/addr");
        uri = cr.insert(uri, values);
        if (uri != null && V){
            Log.v(TAG, "[pushMmsToFolder] NEW URI " + uri.toString());
        }
        return handle;
    }


    private void writeMmsDataPart(long handle, MimePart part, int count) throws IOException{
        ContentValues values = new ContentValues();
        values.put("mid", handle);
        if(part.contentType != null)
            values.put("ct", part.contentType);
        if(part.contentId != null)
            values.put("cid", part.contentId);
        if(part.contentLocation != null)
            values.put("cl", part.contentLocation);
        if(part.contentDisposition != null)
            values.put("cd", part.contentDisposition);
        if(part.partName != null) {
            values.put("fn", part.partName);
            values.put("name", part.partName);
        } else if(part.contentId == null && part.contentLocation == null) {
            /* We must set at least one part identifier */
            values.put("fn", "part_" + count + ".dat");
            values.put("name", "part_" + count + ".dat");
        }
        Uri partUri = Uri.parse("content://mms/" + handle + "/part");
        Uri res = mResolver.insert(partUri, values);

        // Add data to part
        OutputStream os = mResolver.openOutputStream(res);
        os.write(part.data);
        os.close();
    }


    public void sendMessage(PushMsgInfo msgInfo, String msgBody, long simId) {

        SmsManagerEx smsMng = SmsManagerEx.getDefault();
        ArrayList<String> parts = smsMng.divideMessage(msgBody);
        msgInfo.parts = parts.size();

        ArrayList<PendingIntent> deliveryIntents = new ArrayList<PendingIntent>(msgInfo.parts);
        ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>(msgInfo.parts);

        for (int i = 0; i < msgInfo.parts; i++) {
            Intent intent;
            intent = new Intent(ACTION_MESSAGE_DELIVERY, null);
            intent.putExtra("HANDLE", msgInfo.id);
            deliveryIntents.add(PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT));

            intent = new Intent(ACTION_MESSAGE_SENT, null);
            intent.putExtra("HANDLE", msgInfo.id);
            sentIntents.add(PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT));
        }

        Log.d(TAG, "[sendMessage] to " + msgInfo.phone);

        int slotId = BluetoothMapSimManager.getSoltBySimId(mContext, simId);

        smsMng.sendMultipartTextMessage(msgInfo.phone, null, parts, sentIntents,
            deliveryIntents, slotId);
    }

    private static final String ACTION_MESSAGE_DELIVERY =
        "com.android.bluetooth.BluetoothMapContentObserver.action.MESSAGE_DELIVERY";
    private static final String ACTION_MESSAGE_SENT =
        "com.android.bluetooth.BluetoothMapContentObserver.action.MESSAGE_SENT";

    private SmsBroadcastReceiver mSmsBroadcastReceiver = new SmsBroadcastReceiver();

    private class SmsBroadcastReceiver extends BroadcastReceiver {
        private final String[] ID_PROJECTION = new String[] { Sms._ID };
        private final Uri UPDATE_STATUS_URI = Uri.parse("content://sms/status");

        public void register() {
            Handler handler = new Handler();

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_MESSAGE_DELIVERY);
            intentFilter.addAction(ACTION_MESSAGE_SENT);
            mContext.registerReceiver(this, intentFilter, null, handler);
        }

        public void unregister() {
            try {
                mContext.unregisterReceiver(this);
            } catch (IllegalArgumentException e) {
                /* do nothing */
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            long handle = intent.getLongExtra("HANDLE", -1);
            PushMsgInfo msgInfo = mPushMsgList.get(handle);

            Log.d(TAG, "[onReceive]: action"  + action);

            if (msgInfo == null) {
                Log.d(TAG, "[onReceive]: no msgInfo found for handle " + handle);
                return;
            }

            if (action.equals(ACTION_MESSAGE_SENT)) {
                msgInfo.partsSent++;
                if (msgInfo.partsSent == msgInfo.parts) {
                    actionMessageSent(context, intent, msgInfo);
                }
            } else if (action.equals(ACTION_MESSAGE_DELIVERY)) {
                msgInfo.partsDelivered++;
                if (msgInfo.partsDelivered == msgInfo.parts) {
                    actionMessageDelivery(context, intent, msgInfo);
                }
            } else {
                Log.d(TAG, "[onReceive]: Unknown action " + action);
            }
        }

        private void actionMessageSent(Context context, Intent intent,
            PushMsgInfo msgInfo) {
            int result = getResultCode();
            boolean delete = false;

            if (result == Activity.RESULT_OK) {
                Log.d(TAG, "[actionMessageSent]: result OK");
                if (msgInfo.transparent == 0) {
                    if (!Sms.moveMessageToFolder(context, msgInfo.uri,
                            Sms.MESSAGE_TYPE_SENT, 0)) {
                        Log.d(TAG, "[actionMessageSent] Failed to move " + msgInfo.uri + " to SENT");
                    }
                } else {
                    delete = true;
                }

                Event evt = new Event("SendingSuccess", msgInfo.id,
                    folderSms[Sms.MESSAGE_TYPE_SENT], null, mSmsType);
                sendEvent(evt);

            } else {
                if (msgInfo.retry == 1) {
                    /* Notify failure, but keep message in outbox for resending */
                    msgInfo.resend = true;
                    Event evt = new Event("SendingFailure", msgInfo.id,
                        folderSms[Sms.MESSAGE_TYPE_OUTBOX], null, mSmsType);
                    sendEvent(evt);
                } else {
                    if (msgInfo.transparent == 0) {
                        if (!Sms.moveMessageToFolder(context, msgInfo.uri,
                                Sms.MESSAGE_TYPE_FAILED, 0)) {
                            Log.d(TAG, "[actionMessageSent] Failed to move " + msgInfo.uri + " to FAILED");
                        }
                    } else {
                        delete = true;
                    }

                    Event evt = new Event("SendingFailure", msgInfo.id,
                        folderSms[Sms.MESSAGE_TYPE_FAILED], null, mSmsType);
                    sendEvent(evt);
                }
            }

            if (delete == true) {
                /* Delete from Observer message list to avoid delete notifications */
                mMsgListSms.remove(msgInfo.id);

                /* Delete from DB */
                mResolver.delete(msgInfo.uri, null, null);
            }
        }

        private void actionMessageDelivery(Context context, Intent intent,
            PushMsgInfo msgInfo) {
            Uri messageUri = intent.getData();
            byte[] pdu = intent.getByteArrayExtra("pdu");
            String format = intent.getStringExtra("format");

            SmsMessage message = SmsMessage.createFromPdu(pdu, format);
            if (message == null) {
                Log.d(TAG, "[actionMessageDelivery]: Can't get message from pdu");
                return;
            }
            int status = message.getStatus();

            Cursor cursor = mResolver.query(msgInfo.uri, ID_PROJECTION, null, null, null);

            try {
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int messageId = cursor.getInt(0);

                        Uri updateUri = ContentUris.withAppendedId(UPDATE_STATUS_URI, messageId);
                        boolean isStatusReport = message.isStatusReportMessage();

                        Log.d(TAG, "[actionMessageDelivery]: uri=" + messageUri + ", status=" + status +
                                    ", isStatusReport=" + isStatusReport);

                        ContentValues contentValues = new ContentValues(2);

                        contentValues.put(Sms.STATUS, status);
                        contentValues.put(Inbox.DATE_SENT, System.currentTimeMillis());
                        mResolver.update(updateUri, contentValues, null, null);
                    } else {
                        Log.d(TAG, "[actionMessageDelivery] Can't find message for status update: " + messageUri);
                    }
                } else {
                    Log.d(TAG, "[actionMessageDelivery] Can't find message cause of cursor is null");
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            if (status == 0) {
                Event evt = new Event("DeliverySuccess", msgInfo.id,
                    folderSms[Sms.MESSAGE_TYPE_SENT], null, mSmsType);
                sendEvent(evt);
            } else {
                Event evt = new Event("DeliveryFailure", msgInfo.id,
                    folderSms[Sms.MESSAGE_TYPE_SENT], null, mSmsType);
                sendEvent(evt);
            }

            mPushMsgList.remove(msgInfo.id);
        }
    }

    private void registerPhoneServiceStateListener() {
        TelephonyManager tm = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_SERVICE_STATE);
    }

    private void unRegisterPhoneServiceStateListener() {
        TelephonyManager tm = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
    }

    private void resendPendingMessages() {
        /* Send pending messages in outbox */
        String where = "type = " + Sms.MESSAGE_TYPE_OUTBOX;
        Cursor c = mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION, where, null,
            null);

        if (c != null && c.moveToFirst()) {
            do {
                long id = c.getLong(c.getColumnIndex(BaseColumns._ID));
                String msgBody = c.getString(c.getColumnIndex(Sms.BODY));
                long simId = c.getLong(c.getColumnIndex(Sms.SIM_ID));
                PushMsgInfo msgInfo = mPushMsgList.get(id);
                if (msgInfo == null || msgInfo.resend == false) {
                    continue;
                }
                sendMessage(msgInfo, msgBody, simId);
            } while (c.moveToNext());
        }
        if (c != null) {
            c.close();
        }
    }

    private void failPendingMessages() {
        /* Move pending messages from outbox to failed */
        String where = "type = " + Sms.MESSAGE_TYPE_OUTBOX;
        Cursor c = mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION, where, null,
            null);

        if (c != null && c.moveToFirst()) {
            do {
                long id = c.getLong(c.getColumnIndex(BaseColumns._ID));
                String msgBody = c.getString(c.getColumnIndex(Sms.BODY));
                PushMsgInfo msgInfo = mPushMsgList.get(id);
                if (msgInfo == null || msgInfo.resend == false) {
                    continue;
                }
                Sms.moveMessageToFolder(mContext, msgInfo.uri,
                    Sms.MESSAGE_TYPE_FAILED, 0);
            } while (c.moveToNext());
        }
        if (c != null) c.close();
    }

    private void removeDeletedMessages() {
        /* Remove messages from virtual "deleted" folder (thread_id -1) */
        mResolver.delete(Uri.parse("content://sms/"),
                "thread_id = " + DELETED_THREAD_ID, null);
    }

    private PhoneStateListener mPhoneListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            Log.d(TAG, "[onServiceStateChanged] Phone service state change: " + serviceState.getState());
            if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
                resendPendingMessages();
            }
        }
    };

    public void init() {
        mSmsBroadcastReceiver.register();
        registerPhoneServiceStateListener();
    }

    public void deinit() {
        mSmsBroadcastReceiver.unregister();
        unRegisterPhoneServiceStateListener();
        failPendingMessages();
        removeDeletedMessages();
    }
}
