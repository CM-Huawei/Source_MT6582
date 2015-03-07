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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.obex.HeaderSet;
import javax.obex.Operation;
import javax.obex.ResponseCodes;
import javax.obex.ServerRequestHandler;

import com.android.bluetooth.map.BluetoothMapUtils;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.bluetooth.map.BluetoothMapbMessage.vCard;
import com.android.bluetooth.R;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.telephony.SimInfoManager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class BluetoothMapObexServer extends ServerRequestHandler {

    private static final String TAG = "[MAP]BluetoothMapObexServer";

    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;

    private static final int UUID_LENGTH = 16;

    // 128 bit UUID for MAP
    private static final byte[] MAP_TARGET = new byte[] {
             (byte)0xBB, (byte)0x58, (byte)0x2B, (byte)0x40,
             (byte)0x42, (byte)0x0C, (byte)0x11, (byte)0xDB,
             (byte)0xB0, (byte)0xDE, (byte)0x08, (byte)0x00,
             (byte)0x20, (byte)0x0C, (byte)0x9A, (byte)0x66
             };

    /* Message types */
    private static final String TYPE_GET_FOLDER_LISTING  = "x-obex/folder-listing";
    private static final String TYPE_GET_MESSAGE_LISTING = "x-bt/MAP-msg-listing";
    private static final String TYPE_MESSAGE             = "x-bt/message";
    private static final String TYPE_SET_MESSAGE_STATUS  = "x-bt/messageStatus";
    private static final String TYPE_SET_NOTIFICATION_REGISTRATION = "x-bt/MAP-NotificationRegistration";
    private static final String TYPE_MESSAGE_UPDATE      = "x-bt/MAP-messageUpdate";

    private BluetoothMapFolderElement mCurrentFolder;

    private BluetoothMnsObexClient mMnsClient;

    private Handler mCallback = null;

    private Context mContext;

    public static boolean sIsAborted = false;

    BluetoothMapContent mOutContent;

    private BluetoothMapSimManager mSimManager;

    public BluetoothMapObexServer(Handler callback, Context context,
                                  BluetoothMnsObexClient mns) {
        super();
        mCallback = callback;
        mContext = context;
        mOutContent = new BluetoothMapContent(mContext);
        mMnsClient = mns;
        buildFolderStructure(); /* Build the default folder structure, and set
                                   mCurrentFolder to root folder */
    }

    /**
     * Build the default minimal folder structure, as defined in the MAP specification.
     */
    private void buildFolderStructure(){
        mCurrentFolder = new BluetoothMapFolderElement("root", null); // This will be the root element
        BluetoothMapFolderElement tmpFolder;
        tmpFolder = mCurrentFolder.addFolder("telecom"); // root/telecom
        tmpFolder = tmpFolder.addFolder("msg");          // root/telecom/msg
        tmpFolder.addFolder("inbox");                    // root/telecom/msg/inbox
        tmpFolder.addFolder("outbox");
        tmpFolder.addFolder("sent");
        tmpFolder.addFolder("deleted");
        tmpFolder.addFolder("draft");
    }

    @Override
    public int onConnect(final HeaderSet request, HeaderSet reply) {
        if (D) Log.d(TAG, "[onConnect]: begin");
        /// M: Map Gemini Feature @{
        mSimManager = new BluetoothMapSimManager();
        mSimManager.init(mContext);
        /// @}
        if (V) logHeader(request);
        try {
            byte[] uuid = (byte[])request.getHeader(HeaderSet.TARGET);
            if (uuid == null) {
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }
            if (D) Log.d(TAG, "[onConnect]: uuid=" + Arrays.toString(uuid));

            if (uuid.length != UUID_LENGTH) {
                Log.w(TAG, "[onConnect] Wrong UUID length");
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }
            for (int i = 0; i < UUID_LENGTH; i++) {
                if (uuid[i] != MAP_TARGET[i]) {
                    Log.w(TAG, "[onConnect] Wrong UUID");
                    return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
                }
            }
            reply.setHeader(HeaderSet.WHO, uuid);
        } catch (IOException e) {
            Log.e(TAG, "[onConnect] " + e.toString());
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        try {
            byte[] remote = (byte[])request.getHeader(HeaderSet.WHO);
            if (remote != null) {
                if (D) Log.d(TAG, "[onConnect]: remote=" + Arrays.toString(remote));
                reply.setHeader(HeaderSet.TARGET, remote);
            }
        } catch (IOException e) {
            Log.e(TAG, "[onConnect] " + e.toString());
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        if (V) Log.v(TAG, "[onConnect]: uuid is ok, will send out " +
                "MSG_SESSION_ESTABLISHED msg.");


        Message msg = Message.obtain(mCallback);
        msg.what = BluetoothMapService.MSG_SESSION_ESTABLISHED;
        msg.sendToTarget();

        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public void onDisconnect(final HeaderSet req, final HeaderSet resp) {
        if (D) Log.d(TAG, "[onDisconnect]: enter");
        mSimManager.unregisterReceiver();
        if (V) logHeader(req);

        resp.responseCode = ResponseCodes.OBEX_HTTP_OK;
        if (mCallback != null) {
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothMapService.MSG_SESSION_DISCONNECTED;
            msg.sendToTarget();
            if (V) Log.v(TAG, "[onDisconnect]: msg MSG_SESSION_DISCONNECTED sent out.");
        }
    }

    @Override
    public int onAbort(HeaderSet request, HeaderSet reply) {
        if (D) Log.d(TAG, "[onAbort]: enter.");
        sIsAborted = true;
        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public int onPut(final Operation op) {
        if (D) Log.d(TAG, "[onPut]: enter");
        HeaderSet request = null;
        String type, name;
        byte[] appParamRaw;
        BluetoothMapAppParams appParams = null;

        try {
            request = op.getReceivedHeader();
            type = (String)request.getHeader(HeaderSet.TYPE);
            name = (String)request.getHeader(HeaderSet.NAME);
            appParamRaw = (byte[])request.getHeader(HeaderSet.APPLICATION_PARAMETER);
            if(appParamRaw != null) {
                appParams = new BluetoothMapAppParams(appParamRaw);
            } else {
                Log.e(TAG, "[onPut] appParamRaw = null");
            }
        } catch (Exception e) {
            Log.e(TAG, "[onPut] request headers error");
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        if(D) Log.d(TAG,"[onPut] type = " + type + ", name = " + name);
        if (type.equals(TYPE_MESSAGE_UPDATE)) {
            if(V) {
                Log.d(TAG,"[onPut] TYPE_MESSAGE_UPDATE:");
            }
            return ResponseCodes.OBEX_HTTP_OK;
        }else if(type.equals(TYPE_SET_NOTIFICATION_REGISTRATION)) {
            if(V) {
                Log.d(TAG,"[onPut] TYPE_SET_NOTIFICATION_REGISTRATION: NotificationStatus: " + appParams.getNotificationStatus());
            }
            return setNotificationRegistration(appParams);
        }else if(type.equals(TYPE_SET_MESSAGE_STATUS)) {
            if(V) {
                Log.d(TAG,"[onPut] TYPE_SET_MESSAGE_STATUS: StatusIndicator: " + appParams.getStatusIndicator() + ", StatusValue: " + appParams.getStatusValue());
            }
            return setMessageStatus(name, appParams);
        } else if (type.equals(TYPE_MESSAGE)) {
            if(V) {
                Log.d(TAG,"[onPut] TYPE_MESSAGE: Transparet: " + appParams.getTransparent() +  ", Retry: " + appParams.getRetry());
                Log.d(TAG,"[onPut]              charset: " + appParams.getCharset());
            }
            /// M: Map Gemini Feature, INVALID_SIMID(-1) when SimCount==0 @{
            long simId;
            if (mSimManager.getSimCount() == 0) {
                simId = BluetoothMapSimManager.INVALID_SIMID;
            } else if (mSimManager.getSimCount() == 1) {
                simId = mSimManager.getSingleSimId();
            } else {
                long messageSimId = Settings.System.getLong(mContext.getContentResolver(),
                    Settings.System.SMS_SIM_SETTING, Settings.System.DEFAULT_SIM_NOT_SET);
                Log.d(TAG, "[onPut] Settings messageSimId = " + messageSimId);
                if (messageSimId == Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK
                        || messageSimId == Settings.System.SMS_SIM_SETTING_AUTO) {
                    // always ask, show SIM selection dialog
                    return pushMessage(op, name, appParams, messageSimId == Settings.System.SMS_SIM_SETTING_AUTO);
                } else {
                    simId = messageSimId;
                }
            }
            /// @}
            Log.d(TAG, "[onPut] simId = " + simId);
            return pushMessageGemini(op, name, appParams, simId);
        }

        return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
    }

    private int setNotificationRegistration(BluetoothMapAppParams appParams) {
        // Forward the request to the MNS thread as a message - including the MAS instance ID.
        Handler mns = mMnsClient.getMessageHandler();
        if(mns != null) {
            Message msg = Message.obtain(mns);
            msg.what = BluetoothMnsObexClient.MSG_MNS_NOTIFICATION_REGISTRATION;
            msg.arg1 = 0; // TODO: Add correct MAS ID, as specified in the SDP record.
            msg.arg2 = appParams.getNotificationStatus();
            msg.sendToTarget();
            if(D) Log.d(TAG, "[setNotificationRegistration] MSG_MNS_NOTIFICATION_REGISTRATION");
            return ResponseCodes.OBEX_HTTP_OK;
        } else {
            return ResponseCodes.OBEX_HTTP_UNAVAILABLE; // This should not happen.
        }
    }

    private int pushMessageGemini(final Operation op, String folderName,
            BluetoothMapAppParams appParams, long simId) {
        if(appParams.getCharset() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER) {
            if(D) Log.d(TAG, "[pushMessageGemini] Missing charset - unable to decode message content. appParams.getCharset() = " + appParams.getCharset());
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }
        try {
            if(folderName == null || folderName.equals("")) {
                folderName = mCurrentFolder.getName();
            }
            if(!folderName.equals("outbox") && !folderName.equals("draft")) {
                if(D) Log.d(TAG, "[pushMessageGemini] Push message only allowed to outbox and draft. folderName: " + folderName);
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }
            /*  - Read out the message
             *  - Decode into a bMessage
             *  - send it.
             */
            InputStream bMsgStream;
            BluetoothMapbMessage message;
            bMsgStream = op.openInputStream();
            message = BluetoothMapbMessage.parse(bMsgStream, appParams.getCharset()); // Decode the messageBody
            Log.d(TAG, "[pushMessage] BluetoothMapbMessage.parse success");
            // Send message
            BluetoothMapContentObserver observer = mMnsClient.getContentObserver();
            if (observer == null) {
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE; // Should not happen.
            }

            long handle = observer.pushMessage(message, folderName, appParams, simId);
            if (D) Log.d(TAG, "[pushMessageGemini] handle: " + handle);
            if (handle < 0) {
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE; // Should not happen.
            }
            HeaderSet replyHeaders = new HeaderSet();
            String handleStr = BluetoothMapUtils.getMapHandle(handle, message.getType());
            if (D) Log.d(TAG, "[pushMessageGemini] handleStr: " + handleStr + " message.getType(): " + message.getType());
            replyHeaders.setHeader(HeaderSet.NAME, handleStr);
            op.sendHeaders(replyHeaders);

            bMsgStream.close();
        } catch (IllegalArgumentException e) {
            if(D) Log.w(TAG, "[pushMessageGemini] Wrongly formatted bMessage received", e);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (Exception e) {
            // TODO: Change to IOException after debug
            Log.e(TAG, "[pushMessageGemini] Exception occured: ", e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    private int setMessageStatus(String msgHandle, BluetoothMapAppParams appParams) {
        int indicator = appParams.getStatusIndicator();
        int value = appParams.getStatusValue();
        Log.d(TAG, "[setMessageStatus] indicator = " + indicator + " value = " + value);
        long handle;
        BluetoothMapUtils.TYPE msgType;

        if(indicator == BluetoothMapAppParams.INVALID_VALUE_PARAMETER ||
           value == BluetoothMapAppParams.INVALID_VALUE_PARAMETER ||
           msgHandle == null) {
            Log.d(TAG, "[setMessageStatus] INVALID_VALUE_PARAMETER || msgHandle == null");
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }
        BluetoothMapContentObserver observer = mMnsClient.getContentObserver();
        if (observer == null) {
            Log.d(TAG, "[setMessageStatus] observer == null");
            return ResponseCodes.OBEX_HTTP_UNAVAILABLE; // Should not happen.
        }

        try {
            handle = BluetoothMapUtils.getCpHandle(msgHandle);
            msgType = BluetoothMapUtils.getMsgTypeFromHandle(msgHandle);
            Log.d(TAG, "[setMessageStatus] msgId = " + handle + " msgType = " + msgType);
        } catch (NumberFormatException e) {
            Log.w(TAG, "[setMessageStatus] Wrongly formatted message handle: " + msgHandle);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }

        if( indicator == BluetoothMapAppParams.STATUS_INDICATOR_DELETED) {
            if (!observer.setMessageStatusDeleted(handle, msgType, value)) {
                Log.d(TAG, "[setMessageStatus] deleted fail: " + handle);
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
            }
        } else /* BluetoothMapAppParams.STATUS_INDICATOR_READE */ {
            if (!observer.setMessageStatusRead(handle, msgType, value)) {
                Log.d(TAG, "[setMessageStatus] setRead fail: " + handle);
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
            }
        }
        Log.d(TAG, "[setMessageStatus] success");
        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public int onSetPath(final HeaderSet request, final HeaderSet reply, final boolean backup,
            final boolean create) {
        String folderName;
        BluetoothMapFolderElement folder;
        try {
            folderName = (String)request.getHeader(HeaderSet.NAME);
        } catch (Exception e) {
            Log.e(TAG, "[onSetPath] request headers error");
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        if (V) logHeader(request);
        if (D) Log.d(TAG, "[onSetPath] name is " + folderName + " backup: " + backup
                     + "create: " + create);

        if(backup == true){
            if(mCurrentFolder.getParent() != null) {
                mCurrentFolder = mCurrentFolder.getParent();
            } else {
                Log.d(TAG, "[onSetPath] mCurrentFolder.getParent() == null");
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        }

        if (folderName == null || folderName == "") {
            if(backup == false)
                mCurrentFolder = mCurrentFolder.getRoot();
        }
        else {
            folder = mCurrentFolder.getSubFolder(folderName);
            if(folder != null) {
                mCurrentFolder = folder;
            } else {
                Log.d(TAG, "[onSetPath] folder == null");
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        }
        if (V) Log.d(TAG, "[onSetPath] Current Folder: " + mCurrentFolder.getName());
        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public void onClose() {
        if (mCallback != null) {
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothMapService.MSG_SERVERSESSION_CLOSE;
            msg.sendToTarget();
            if (D) Log.d(TAG, "[onClose]: msg MSG_SERVERSESSION_CLOSE sent out.");
        }
    }

    @Override
    public int onGet(Operation op) {
        if (D) Log.d(TAG, "[onGet]: enter");
        sIsAborted = false;
        HeaderSet request;
        String type;
        String name;
        byte[] appParamRaw = null;
        BluetoothMapAppParams appParams = null;
        try {
            request = op.getReceivedHeader();
            type = (String)request.getHeader(HeaderSet.TYPE);
            name = (String)request.getHeader(HeaderSet.NAME);
            appParamRaw = (byte[])request.getHeader(HeaderSet.APPLICATION_PARAMETER);
            if(appParamRaw != null)
                appParams = new BluetoothMapAppParams(appParamRaw);

            if (V) logHeader(request);
            if (D) Log.d(TAG, "[OnGet] type is " + type + " name is " + name);

            if (type == null) {
                if (V) Log.d(TAG, "[onGet] type is null?" + type);
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }

            if (type.equals(TYPE_GET_FOLDER_LISTING)) {
                if (V && appParams != null) {
                    Log.d(TAG,"[onGet] TYPE_GET_FOLDER_LISTING: MaxListCount = " + appParams.getMaxListCount() +
                              ", ListStartOffset = " + appParams.getStartOffset());
                }
                return sendFolderListingRsp(op, appParams); // Block until all packets have been send.
            }
            else if (type.equals(TYPE_GET_MESSAGE_LISTING)){
                if (V && appParams != null) {
                    Log.d(TAG,"[onGet] TYPE_GET_MESSAGE_LISTING: MaxListCount = " + appParams.getMaxListCount() +
                              ", ListStartOffset = " + appParams.getStartOffset());
                    Log.d(TAG,"[onGet] SubjectLength = " + appParams.getSubjectLength() + ", ParameterMask = " +
                              appParams.getParameterMask());
                    Log.d(TAG,"[onGet] FilterMessageType = " + appParams.getFilterMessageType() +
                              ", FilterPeriodBegin = " + appParams.getFilterPeriodBegin());
                    Log.d(TAG,"[onGet] FilterPeriodEnd = " + appParams.getFilterPeriodBegin() +
                              ", FilterReadStatus = " + appParams.getFilterReadStatus());
                    Log.d(TAG,"[onGet] FilterRecipient = " + appParams.getFilterRecipient() +
                              ", FilterOriginator = " + appParams.getFilterOriginator());
                    Log.d(TAG,"[onGet] FilterPriority = " + appParams.getFilterPriority());
                }
                if (name == null) {
                    name = mCurrentFolder.getName();
                }
                if (name.equalsIgnoreCase("telecom") || name.equalsIgnoreCase("msg")) {
                    Log.d(TAG, "[onGet] TYPE_GET_MESSAGE_LISTING invalid folder " + name);
                    return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
                }
                return sendMessageListingRsp(op, appParams, name); // Block until all packets have been send.
            }
            else if (type.equals(TYPE_MESSAGE)){
                if(V && appParams != null) {
                    Log.d(TAG,"[onGet] TYPE_MESSAGE (GET): Attachment = " + appParams.getAttachment() + ", Charset = " + appParams.getCharset() +
                        ", FractionRequest = " + appParams.getFractionRequest());
                }
                return sendGetMessageRsp(op, name, appParams); // Block until all packets have been send.
            }
            else {
                Log.w(TAG, "[onGet] unknown type request: " + type);
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }
        } catch (Exception e) {
            // TODO: Move to the part that actually throws exceptions, and change to the correat exception type
            Log.e(TAG, "[onGet] request headers error, Exception:", e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }
    }

    /**
     * Generate and send the message listing response based on an application
     * parameter header. This function call will block until complete or aborted
     * by the peer. Fragmentation of packets larger than the obex packet size
     * will be handled by this function.
     *
     * @param op
     *            The OBEX operation.
     * @param appParams
     *            The application parameter header
     * @return {@link ResponseCodes.OBEX_HTTP_OK} on success or
     *         {@link ResponseCodes.OBEX_HTTP_BAD_REQUEST} on error.
     */
    private int sendMessageListingRsp(Operation op, BluetoothMapAppParams appParams, String folderName){
        OutputStream outStream = null;
        byte[] outBytes = null;
        int maxChunkSize, bytesToWrite, bytesWritten = 0, listSize;
        boolean hasUnread = false;
        HeaderSet replyHeaders = new HeaderSet();
        BluetoothMapAppParams outAppParams = new BluetoothMapAppParams();
        BluetoothMapMessageListing outList;
        if(folderName == null) {
            folderName = mCurrentFolder.getName();
        }
        if(appParams == null){
            appParams = new BluetoothMapAppParams();
            appParams.setMaxListCount(1024);
            appParams.setStartOffset(0);
        }

        // Check to see if we only need to send the size - hence no need to encode.
        try {
            // Open the OBEX body stream
            outStream = op.openOutputStream();

            if(appParams.getMaxListCount() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                appParams.setMaxListCount(1024);

            if(appParams.getStartOffset() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                appParams.setStartOffset(0);

            Log.d(TAG, "[sendMessageListingRsp]: MaxListCount = " + appParams.getMaxListCount()
                    + "folderName = " + folderName);
            if(appParams.getMaxListCount() != 0) {
                outList = mOutContent.msgListing(folderName, appParams);
                // Generate the byte stream
                outAppParams.setMessageListingSize(outList.getCount());
                outBytes = outList.encode();
                hasUnread = outList.hasUnread();
                Log.d(TAG, "[sendMessageListingRsp]: outList count = "
                        + outList.getCount() + " outList read = " + hasUnread);
            }
            else {
                listSize = mOutContent.msgListingSize(folderName, appParams);
                hasUnread = mOutContent.msgListingHasUnread(folderName, appParams);
                outAppParams.setMessageListingSize(listSize);
                op.noBodyHeader();
                Log.d(TAG, "[sendMessageListingRsp]: listSize = "
                        + listSize + " hasUnread = " + hasUnread);
            }

            // Build the application parameter header

            // let the peer know if there are unread messages in the list
            if(hasUnread)
            {
                outAppParams.setNewMessage(1);
            }else{
                outAppParams.setNewMessage(0);
            }

            outAppParams.setMseTime(Calendar.getInstance().getTime().getTime());
            replyHeaders.setHeader(HeaderSet.APPLICATION_PARAMETER, outAppParams.EncodeParams());
            op.sendHeaders(replyHeaders);

        } catch (IOException e) {
            Log.w(TAG,"[sendMessageListingRsp]: IOException - sending OBEX_HTTP_BAD_REQUEST", e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        } catch (IllegalArgumentException e) {
            Log.w(TAG,"[sendMessageListingRsp]: IllegalArgumentException - sending OBEX_HTTP_BAD_REQUEST", e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        maxChunkSize = op.getMaxPacketSize(); // This must be called after setting the headers.
        if(outBytes != null) {
            try {
                while (bytesWritten < outBytes.length && sIsAborted == false) {
                    bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            } catch (IOException e) {
                if(V) Log.w(TAG, "[sendMessageListingRsp] " + e);
                // We were probably aborted or disconnected
            } finally {
                if(outStream != null) {
                    try {
                        outStream.close();
                    } catch (IOException e) {
                        // If an error occurs during close, there is no more cleanup to do
                        Log.w(TAG, "[sendMessageListingRsp] outBytes != null IOException = " + e);
                    }
                }
            }
            Log.d(TAG, "[sendMessageListingRsp] bytesWritten= " + bytesWritten
                    + " outBytes.length= " + outBytes.length);
            if(bytesWritten != outBytes.length) {
                Log.w(TAG, "[sendMessageListingRsp] bytesWritten != outBytes.length");
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } else {
            try {
                outStream.close();
            } catch (IOException e) {
                // If an error occurs during close, there is no more cleanup to do
                Log.w(TAG, "[sendMessageListingRsp] outBytes == null IOException = " + e);
            }
        }
        Log.d(TAG, "[sendMessageListingRsp]: success");
        return ResponseCodes.OBEX_HTTP_OK;
    }

    /**
     * Generate and send the Folder listing response based on an application
     * parameter header. This function call will block until complete or aborted
     * by the peer. Fragmentation of packets larger than the obex packet size
     * will be handled by this function.
     *
     * @param op
     *            The OBEX operation.
     * @param appParams
     *            The application parameter header
     * @return {@link ResponseCodes.OBEX_HTTP_OK} on success or
     *         {@link ResponseCodes.OBEX_HTTP_BAD_REQUEST} on error.
     */
    private int sendFolderListingRsp(Operation op, BluetoothMapAppParams appParams){
        OutputStream outStream = null;
        byte[] outBytes = null;
        BluetoothMapAppParams outAppParams = new BluetoothMapAppParams();
        int maxChunkSize, bytesWritten = 0;
        HeaderSet replyHeaders = new HeaderSet();
        int bytesToWrite, maxListCount, listStartOffset;
        if(appParams == null){
            appParams = new BluetoothMapAppParams();
            appParams.setMaxListCount(1024);
        }

        if(V)
            Log.v(TAG,"[sendFolderListingRsp] for " + mCurrentFolder.getName());

        try {
            maxListCount = appParams.getMaxListCount();
            listStartOffset = appParams.getStartOffset();

            if(listStartOffset == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                listStartOffset = 0;

            if(maxListCount == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                maxListCount = 1024;

            if(maxListCount != 0)
            {
                outBytes = mCurrentFolder.encode(listStartOffset, maxListCount);
                outStream = op.openOutputStream();
            }

            // Build and set the application parameter header
            outAppParams.setFolderListingSize(mCurrentFolder.getSubFolderCount());
            replyHeaders.setHeader(HeaderSet.APPLICATION_PARAMETER, outAppParams.EncodeParams());
            op.sendHeaders(replyHeaders);

        } catch (IOException e1) {
            Log.w(TAG,"[sendFolderListingRsp]: IOException - sending OBEX_HTTP_BAD_REQUEST Exception:", e1);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        } catch (IllegalArgumentException e1) {
            Log.w(TAG,"[sendFolderListingRsp]: IllegalArgumentException - sending OBEX_HTTP_BAD_REQUEST Exception:", e1);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        maxChunkSize = op.getMaxPacketSize(); // This must be called after setting the headers.

        if(outBytes != null) {
            try {
                while (bytesWritten < outBytes.length && sIsAborted == false) {
                    bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            } catch (IOException e) {
                // We were probably aborted or disconnected
            } finally {
                if(outStream != null) {
                    try {
                        outStream.close();
                    } catch (IOException e) {
                        // If an error occurs during close, there is no more cleanup to do
                    }
                }
            }
            if(V)
                Log.v(TAG,"[sendFolderListingRsp] sent " + bytesWritten + " bytes out of "+ outBytes.length);
            if(bytesWritten == outBytes.length)
                return ResponseCodes.OBEX_HTTP_OK;
            else
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        return ResponseCodes.OBEX_HTTP_OK;
    }

    /**
     * Generate and send the get message response based on an application
     * parameter header and a handle.
     *
     * @param op
     *            The OBEX operation.
     * @param appParams
     *            The application parameter header
     * @param handle
     *            The handle of the requested message
     * @return {@link ResponseCodes.OBEX_HTTP_OK} on success or
     *         {@link ResponseCodes.OBEX_HTTP_BAD_REQUEST} on error.
     */
    private int sendGetMessageRsp(Operation op, String handle, BluetoothMapAppParams appParams){
        OutputStream outStream ;
        byte[] outBytes;
        int maxChunkSize, bytesToWrite, bytesWritten = 0;
        long msgHandle;

        try {
            outBytes = mOutContent.getMessage(handle, appParams);
            Log.d(TAG,"[sendGetMessageRsp]: mOutContent.getMessage success");
            outStream = op.openOutputStream();

        } catch (IOException e) {
            Log.w(TAG,"[sendGetMessageRsp]: IOException - sending OBEX_HTTP_BAD_REQUEST", e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        } catch (IllegalArgumentException e) {
            Log.w(TAG,"[sendGetMessageRsp]: IllegalArgumentException (e.g. invalid handle) - sending OBEX_HTTP_BAD_REQUEST", e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        maxChunkSize = op.getMaxPacketSize(); // This must be called after setting the headers.

        if(outBytes != null) {
            try {
                while (bytesWritten < outBytes.length && sIsAborted == false) {
                    bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            } catch (IOException e) {
                // We were probably aborted or disconnected
            } finally {
                if(outStream != null) {
                    try {
                        outStream.close();
                    } catch (IOException e) {
                        // If an error occurs during close, there is no more cleanup to do
                    }
                }
            }
            Log.d(TAG, "[sendGetMessageRsp] bytesWritten= " + bytesWritten
                    + " outBytes.length= " + outBytes.length);
            if(bytesWritten == outBytes.length)
                return ResponseCodes.OBEX_HTTP_OK;
            else
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }
        Log.d(TAG,"[sendGetMessageRsp]: success");
        return ResponseCodes.OBEX_HTTP_OK;
    }


    private static final void logHeader(HeaderSet hs) {
        Log.v(TAG, "[logHeader] Dumping HeaderSet " + hs.toString());
        try {
            Log.v(TAG, "[logHeader] CONNECTION_ID : " + hs.getHeader(HeaderSet.CONNECTION_ID));
            Log.v(TAG, "[logHeader] NAME : " + hs.getHeader(HeaderSet.NAME));
            Log.v(TAG, "[logHeader] TYPE : " + hs.getHeader(HeaderSet.TYPE));
            Log.v(TAG, "[logHeader] TARGET : " + hs.getHeader(HeaderSet.TARGET));
            Log.v(TAG, "[logHeader] WHO : " + hs.getHeader(HeaderSet.WHO));
            Log.v(TAG, "[logHeader] APPLICATION_PARAMETER : " + hs.getHeader(HeaderSet.APPLICATION_PARAMETER));
        } catch (IOException e) {
            Log.e(TAG, "[logHeader] dump HeaderSet error " + e);
        }
        Log.v(TAG, "[logHeader] NEW!!! Dumping HeaderSet END");
    }

    /// M: Map Gemini Feature @{
    private int pushMessage(final Operation op, String folderName,
            BluetoothMapAppParams appParams, boolean isAutoSelectSim) {
        if(appParams.getCharset() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER) {
            if(D) Log.d(TAG, "[pushMessage] Missing charset - unable to decode message content. appParams.getCharset() = " + appParams.getCharset());
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }
        try {
            if(folderName == null || folderName.equals("")) {
                folderName = mCurrentFolder.getName();
            }
            if(!folderName.equals("outbox") && !folderName.equals("draft")) {
                if(D) Log.d(TAG, "[pushMessage] Push message only allowed to outbox and draft. folderName: " + folderName);
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }
            /*  - Read out the message
             *  - Decode into a bMessage
             *  - send it.
             */
            InputStream bMsgStream;
            BluetoothMapbMessage message;
            bMsgStream = op.openInputStream();
            message = BluetoothMapbMessage.parse(bMsgStream, appParams.getCharset()); // Decode the messageBody
            Log.d(TAG, "[pushMessage] BluetoothMapbMessage.parse success");
            // Send message
            BluetoothMapContentObserver observer = mMnsClient.getContentObserver();
            if (observer == null) {
                Log.d(TAG, "[pushMessage] observer == null");
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE; // Should not happen.
            }

            long simId = BluetoothMapSimManager.INVALID_SIMID;
            /// M: send direcly if message Originator == sim card number
            /*
            ArrayList<vCard> cards = message.getOriginators();
            if (cards != null && cards.size() == 1) {
                String[] origNumber = cards.get(0).getPhoneNumber();
                if (origNumber != null && origNumber.length == 1) {
                    simId = mSimManager.getSimIdFromOriginator(origNumber[0]);
                }
            }
            */

            /// M: ALPS01374857, MAP new feature: support auto-select-sim @{
            if (isAutoSelectSim && message != null) {
                /// M: only have one recipient when CE4A uploads message
                String number = message.getSingleRecipient();
                if (!TextUtils.isEmpty(number)) {
                    long threadId = mOutContent.getThreadIdByNumber(number);
                    if (threadId > 0) {
                        simId = mOutContent.getSimIdByThread(threadId);
                    } else {
                        simId = BluetoothMapSimManager.INVALID_SIMID;
                    }
                } else {
                    simId = BluetoothMapSimManager.INVALID_SIMID;
                }
            }
            /// @}

            if (simId <= BluetoothMapSimManager.INVALID_SIMID) {
                final String finalFolderName = folderName;
                final BluetoothMapAppParams finalAppParams = appParams;
                final BluetoothMapbMessage finalMessage = message;
                final BluetoothMapContentObserver finalObserver = observer;
                final InputStream finalMsgStream = bMsgStream;
                mCallback.post(new Runnable() {
                    @Override
                    public void run() {
                        showSimSelectDialog(op, finalFolderName, finalAppParams, finalMessage, finalObserver, finalMsgStream);
                    }
                });
            } else {
                /// M: VALID Originators simId
                return sendMessage(op, folderName, appParams, message, observer, bMsgStream, simId);
            }
        } catch (IllegalArgumentException e) {
            if(D) Log.w(TAG, "[pushMessage] Wrongly formatted bMessage received", e);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (Exception e) {
            // TODO: Change to IOException after debug
            Log.e(TAG, "[pushMessage] Exception occured: ", e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    private int sendMessage(final Operation op, final String folderName,
            final BluetoothMapAppParams appParams, final BluetoothMapbMessage message,
            final BluetoothMapContentObserver observer, final InputStream bMsgStream, long simId) {
        try {
            long handle = observer.pushMessage(message, folderName, appParams, simId);
            if (D)
                Log.d(TAG, "[sendMessage] handle: " + handle);
            if (handle < 0) {
                Log.d(TAG, "[sendMessage] handle < 0");
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE; // Should not happen.
            }
            HeaderSet replyHeaders = new HeaderSet();
            String handleStr = BluetoothMapUtils.getMapHandle(handle, message.getType());
            if (D)
                Log.d(TAG, "[sendMessage] handleStr: " + handleStr + " message.getType(): " + message.getType());
            replyHeaders.setHeader(HeaderSet.NAME, handleStr);
            op.sendHeaders(replyHeaders);

            bMsgStream.close();
        } catch (IllegalArgumentException e) {
            if(D) Log.w(TAG, "[sendMessage] Wrongly formatted bMessage received", e);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (Exception e) {
            // TODO: Change to IOException after debug
            Log.e(TAG, "[sendMessage] Exception occured: ", e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    public void showSimSelectDialog(final Operation op, final String folderName,
           final BluetoothMapAppParams appParams, final  BluetoothMapbMessage message,
           final BluetoothMapContentObserver observer, final InputStream bMsgStream) {
        Log.d(TAG, "[showSimSelectDialog] enter");

        boolean isSingleRecipient = false;
        String recipentNumber = "";
        ArrayList<vCard> cards = message.getRecipients();
        if (cards != null && cards.size() == 1) {
            isSingleRecipient = true;
            String[] origNumber = cards.get(0).getPhoneNumber();
            if (origNumber != null && origNumber.length == 1) {
                recipentNumber = origNumber[0];
            }
        }
        Log.d(TAG, "[showSimSelectDialog] isSingleRecipient = " + isSingleRecipient
                + " recipentNumber = " + recipentNumber);

        List<Map<String, ?>> entries = new ArrayList<Map<String, ?>>();
        final List<SimInfoManager.SimInfoRecord> tempSimInfoList = new ArrayList<SimInfoManager.SimInfoRecord>();
        for (int slotId = 0; slotId < PhoneConstants.GEMINI_SIM_NUM; slotId++) {
            for (int i = 0; i < mSimManager.getSimList().size(); i++) {
                SimInfoManager.SimInfoRecord simInfo = mSimManager.getSimList().get(i);
                if (simInfo == null || slotId != simInfo.mSimSlotId) {
                    continue;
                }
                tempSimInfoList.add(simInfo);
                HashMap<String, Object> entry = new HashMap<String, Object>();

                entry.put("simIcon", simInfo.mSimBackgroundLightRes);
                int state = mSimManager.getSimStatus(i);
                entry.put("simStatus", mSimManager.getSimStatusResource(state));

                String simNumber = "";
                if (!TextUtils.isEmpty(simInfo.mNumber)) {
                    switch (simInfo.mDispalyNumberFormat) {
                        // case android.provider.Telephony.SimInfo.DISPLAY_NUMBER_DEFAULT:
                        case SimInfoManager.DISPLAY_NUMBER_FIRST:
                            if (simInfo.mNumber.length() <= 4) {
                                simNumber = simInfo.mNumber;
                            } else {
                                simNumber = simInfo.mNumber.substring(0, 4);
                            }
                            break;
                        case SimInfoManager.DISPLAY_NUMBER_LAST:
                            if (simInfo.mNumber.length() <= 4) {
                                simNumber = simInfo.mNumber;
                            } else {
                                simNumber = simInfo.mNumber.substring(simInfo.mNumber.length() - 4);
                            }
                            break;
                        case 0:// android.provider.Telephony.SimInfo.DISPLAY_NUMBER_NONE:
                            simNumber = "";
                            break;
                        default:
                            break;
                    }
                }
                if (TextUtils.isEmpty(simNumber)) {
                    entry.put("simNumberShort", "");
                } else {
                    entry.put("simNumberShort", simNumber);
                }

                entry.put("simName", simInfo.mDisplayName);
                if (TextUtils.isEmpty(simInfo.mNumber)) {
                    entry.put("simNumber", "");
                } else {
                    entry.put("simNumber", simInfo.mNumber);
                }
                long associatedSimId = getContactSIM(recipentNumber);
                if (associatedSimId == (int) simInfo.mSimInfoId && isSingleRecipient) {
                    // if this SIM is contact SIM, set "Suggested"
                    entry.put("suggested", mContext.getString(R.string.suggested));
                } else {
                    entry.put("suggested", "");// not suggested
                }
                entries.add(entry);
            }
        }
        final SimpleAdapter a = createSimpleAdapter(entries, mContext);
        AlertDialog.Builder b = new AlertDialog.Builder(mContext);
        b.setTitle(mContext.getString(R.string.sim_selected_dialog_title));
        b.setCancelable(true);
        b.setAdapter(a, new DialogInterface.OnClickListener() {
            @SuppressWarnings("unchecked")
            public final void onClick(DialogInterface dialog, int which) {
                final long simId = tempSimInfoList.get(which).mSimInfoId;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "[showSimSelectDialog] sendMessage enter: simId " + simId);
                        sendMessage(op, folderName, appParams, message, observer, bMsgStream, simId);
                    }
                }).start();
                dialog.dismiss();
            }
        });
        AlertDialog simSelectDialog = b.create();
        simSelectDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        simSelectDialog.show();
    }

    public static SimpleAdapter createSimpleAdapter(
            List<Map<String, ?>> entries, final Context context) {
        final SimpleAdapter a = new SimpleAdapter(context, entries,
                R.layout.sim_selector, new String[] {"simIcon", "simStatus",
                        "simNumberShort", "simName", "simNumber", "suggested"},
                        new int[] {R.id.sim_icon, R.id.sim_status, R.id.sim_number_short,
                        R.id.sim_name, R.id.sim_number, R.id.sim_suggested});
        SimpleAdapter.ViewBinder viewBinder = new SimpleAdapter.ViewBinder() {
            public boolean setViewValue(View view, Object data,
                    String textRepresentation) {
                if (view instanceof ImageView) {
                    if (view.getId() == R.id.sim_icon) {
                        ImageView simicon = (ImageView) view
                                .findViewById(R.id.sim_icon);
                        simicon.setBackgroundResource((Integer) data);
                    } else if (view.getId() == R.id.sim_status) {
                        ImageView simstatus = (ImageView) view
                                .findViewById(R.id.sim_status);
                        if ((Integer) data != PhoneConstants.SIM_INDICATOR_UNKNOWN
                                && (Integer) data != PhoneConstants.SIM_INDICATOR_NORMAL) {
                            simstatus.setVisibility(View.VISIBLE);
                            simstatus.setImageResource((Integer) data);
                        } else {
                            simstatus.setVisibility(View.GONE);
                        }
                    }
                    return true;
                } else if (view instanceof TextView) {
                     if (view.getId() == R.id.sim_number) {
                         TextView text = (TextView) view.findViewById(R.id.sim_number);
                         String number = (String) data;
                         if (number == null || number.isEmpty()) {
                             text.setVisibility(View.GONE);
                         } else {
                             text.setVisibility(View.VISIBLE);
                         }
                     }
                }
                return false;
            }
        };
        a.setViewBinder(viewBinder);
        return a;
    }
    /// @}

    private long getContactSIM(final String number) {
        long simId = -1;
        String formatNumber = BluetoothMapUtils.formatNumber(number, mContext);
        String TrimFormatNumber = formatNumber;
        if (formatNumber != null) {
            TrimFormatNumber = formatNumber.replace(" ", "");
        }
        Cursor associateSIMCursor = mContext.getContentResolver().query(
                Data.CONTENT_URI, new String[] {ContactsContract.Data.SIM_ASSOCIATION_ID},
                Data.MIMETYPE + "='" + CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "' AND ("
                        + Data.DATA1 + "=?" + " OR " + Data.DATA1 + "=?" + " OR " + Data.DATA4
                        + "=?" + ") AND (" + ContactsContract.Data.SIM_ASSOCIATION_ID
                        + "!= -1)", new String[] {
                        number, formatNumber, TrimFormatNumber
                }, null);

        if ((null != associateSIMCursor) && (associateSIMCursor.getCount() == 1)) {
            if (associateSIMCursor.moveToFirst()) {
                // Get only one record is OK
                simId = (long) associateSIMCursor.getLong(0);
            }
        }
        if (associateSIMCursor != null) {
            associateSIMCursor.close();
        }
        return simId;
    }
}
