/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.telephony.cdma.utk;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.android.internal.telephony.cdma.utk.LocalInfo;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.RuimFileHandler;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.cdma.SmsMessage;
import com.android.internal.telephony.uicc.UiccController;

import android.util.Config;

import java.io.ByteArrayOutputStream;
import android.util.Log;

class RilMessage {
    int mId;
    Object mData;
    ResultCode mResCode;

    RilMessage(int msgId, String rawData) {
        mId = msgId;
        mData = rawData;
    }

    RilMessage(RilMessage other) {
        this.mId = other.mId;
        this.mData = other.mData;
        this.mResCode = other.mResCode;
    }
}

/**
 * Class that implements RUIM Toolkit Telephony Service. Interacts with the RIL
 * and application.
 *
 * {@hide}
 */
public class UtkService extends Handler implements AppInterface {

    // Class members
    private static RuimRecords mRuimRecords;
    private static UiccCardApplication mUiccApplication;
    private UiccController mUiccController = null;
    private int mSimId;

    private static final Object sInstanceLock = new Object();
    
    // Service members.
    private static UtkService sInstance;
    private CommandsInterface mCmdIf;
    private Context mContext;
    private UtkCmdMessage mCurrntCmd = null;
    private UtkCmdMessage mMenuCmd = null;

    private RilMessageDecoder mMsgDecoder = null;

    private LocalInfo mLocalInfo = new LocalInfo();
    // Service constants.
    static final int MSG_ID_SESSION_END              = 1;
    static final int MSG_ID_PROACTIVE_COMMAND        = 2;
    static final int MSG_ID_EVENT_NOTIFY             = 3;
    static final int MSG_ID_CALL_SETUP               = 4;
    static final int MSG_ID_REFRESH                  = 5;
    static final int MSG_ID_RESPONSE                 = 6;
    static final int MSG_ID_RUIM_READY               = 7;
    static final int MSG_ID_RIL_MSG_DECODED          = 10;
    static final int MSG_ID_ICC_CHANGED              = 11;

    static final int MSG_ID_RIL_LOCAL_INFO           = 12;
    static final int MSG_ID_RIL_REFRESH_RESULT       = 13;

    private static final int DEV_ID_KEYPAD      = 0x01;
    private static final int DEV_ID_DISPLAY     = 0x02;
    private static final int DEV_ID_EARPIECE    = 0x03;
    private static final int DEV_ID_UICC        = 0x81;
    private static final int DEV_ID_TERMINAL    = 0x82;
    private static final int DEV_ID_NETWORK     = 0x83;

    static final String UTK_DEFAULT = "Defualt Message";

    /* Intentionally private for singleton */
    private UtkService(CommandsInterface ci, UiccCardApplication ca, RuimRecords ir,
            Context context, RuimFileHandler fh, UiccCard ic) {

        Log.d("UtkService", " ci" + ci +" ca " + ca + " ir " + ir +" fh " + fh + " ic " + ic);
        
        if (ci == null || ca == null || ir == null || context == null || fh == null
                || ic == null) {
            throw new NullPointerException(
                    "Service: Input parameters must not be null");
        }

        mCmdIf = ci;
        mContext = context;
        mRuimRecords = ir;
        mSimId = ca.getMySimId();

        // Get the RilMessagesDecoder for decoding the messages.
        mMsgDecoder = RilMessageDecoder.getInstance(this, fh);

        // Register ril events handling.
        mUiccController = UiccController.getInstance(mSimId);
        if (mUiccController != null) {
            mUiccController.registerForIccChanged(this, MSG_ID_ICC_CHANGED, null);
            UtkLog.d(this, "mUiccController != null, register for icc change successly");
        } else {
            UtkLog.d(this, "mUiccController = null, cant register for icc change");
        }

        mUiccApplication = ca;
        mUiccApplication.registerForReady(this, MSG_ID_RUIM_READY, null);
        
        mCmdIf.setOnUtkSessionEnd(this, MSG_ID_SESSION_END, null);
        mCmdIf.setOnUtkProactiveCmd(this, MSG_ID_PROACTIVE_COMMAND, null);
        mCmdIf.setOnUtkEvent(this, MSG_ID_EVENT_NOTIFY, null);
        //mCmdIf.setOnSimRefresh(this, MSG_ID_REFRESH, null);        

        mCmdIf.reportUtkServiceIsRunning(null);
        UtkLog.d(this, "UtkService v1.2.0 is running");
    }

    public void dispose() {
        UtkLog.d(this, "dispose");
        mCmdIf.unSetOnUtkSessionEnd(this);
        mCmdIf.unSetOnUtkProactiveCmd(this);
        mCmdIf.unSetOnUtkEvent(this);

        if (mUiccController != null) {
            mUiccController.unregisterForIccChanged(this);
            mUiccController = null;
        }
        if (mUiccApplication != null) {
            mUiccApplication.unregisterForReady(this);
            mUiccApplication = null;
        }
        this.removeCallbacksAndMessages(null);
    }

    protected void finalize() {
        UtkLog.d(this, "Service finalized");
    }
    
    private void updateIccStatus() {
        UtkLog.d(this, "updateIccStatus");
        if (mUiccController == null) {
            UtkLog.d(this, "mUiccController == null, cant do nothing");
            return;
        }

        UiccCardApplication newUiccApplication =
                mUiccController.getUiccCardApplication(UiccController.APP_FAM_3GPP2);
        UtkLog.d(this, "newUiccApplication "+newUiccApplication);

        if(mUiccApplication != newUiccApplication) {
            UtkLog.d(this, "mUiccApplication have changed!");
            if (mUiccApplication != null) {
                UtkLog.d(this, "mUiccApplication unregisterForReady!");
                mUiccApplication.unregisterForReady(this);
                mUiccApplication = null;
            }
            if (newUiccApplication != null) {
                UtkLog.d(this, "mUiccApplication registerForReady successly");
                mUiccApplication = newUiccApplication;
                mUiccApplication.registerForReady(this, MSG_ID_RUIM_READY, null);                
            }
        }
    }

    private void handleRilMsg(RilMessage rilMsg) {
        if (rilMsg == null) {
            return;
        }

        // dispatch messages
        CommandParams cmdParams = null;
        
        UtkLog.d(this, "handleRilMsg " + rilMsg.mId);
        
        switch (rilMsg.mId) {
        case MSG_ID_EVENT_NOTIFY:
            if (rilMsg.mResCode == ResultCode.OK) {
                cmdParams = (CommandParams) rilMsg.mData;
                if (cmdParams != null) {
                    handleProactiveCommand(cmdParams);
                }
            }
            break;
        case MSG_ID_PROACTIVE_COMMAND:
            try {
                cmdParams = (CommandParams) rilMsg.mData;
            } catch (ClassCastException e) {
                // for error handling : cast exception
                UtkLog.d(this, "Fail to parse proactive command");
                // Don't send Terminal Resp if command detail is not available
                if (mCurrntCmd != null) {
                    sendTerminalResponse(mCurrntCmd.mCmdDet, ResultCode.CMD_DATA_NOT_UNDERSTOOD,
                                     false, 0x00, null);
                }
                break;
            }
            UtkLog.d(this, "handleRilMsg cmdParams!=null =" + (cmdParams!=null) +" rilMsg.mResCode = " + rilMsg.mResCode);
            if (cmdParams != null) {
                if (rilMsg.mResCode == ResultCode.OK) {
                    handleProactiveCommand(cmdParams);
                } else {
                    // for proactive commands that couldn't be decoded
                    // successfully respond with the code generated by the
                    // message decoder.
                    sendTerminalResponse(cmdParams.cmdDet, rilMsg.mResCode,
                            false, 0, null);
                }
            }
            break;
        case MSG_ID_REFRESH:
            cmdParams = (CommandParams) rilMsg.mData;
            if (cmdParams != null) {
                handleProactiveCommand(cmdParams);
            }
            break;
        case MSG_ID_SESSION_END:
            handleSessionEnd();
            break;
        case MSG_ID_CALL_SETUP:
            // prior event notify command supplied all the information
            // needed for set up call processing.
            break;
        }
    }

    /**
     * Handles RIL_UNSOL_UTK_PROACTIVE_COMMAND unsolicited command from RIL.
     * Sends valid proactive command data to the application using intents.
     *
     */
    private void handleProactiveCommand(CommandParams cmdParams) {
        UtkLog.d(this, cmdParams.getCommandType().name());

        UtkCmdMessage cmdMsg = new UtkCmdMessage(cmdParams);

        UtkLog.d(this,"handleProactiveCommand " + cmdParams.getCommandType());
        
        switch (cmdParams.getCommandType()) {
        case SET_UP_MENU:
            if (removeMenu(cmdMsg.getMenu())) {
                mMenuCmd = null;
            } else {
                mMenuCmd = cmdMsg;
            }
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0,
                    null);
            break;
        case DISPLAY_TEXT:
            // when application is not required to respond, send an immediate
            // response.
            if (!cmdMsg.geTextMessage().responseNeeded) {
                sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                        0, null);
            }
            break;
        case REFRESH: {
            // ME side only handles refresh commands which meant to remove IDLE
            // MODE TEXT.
            UtkLog.d(this,"UtkService handleProactiveCommand Do refresh");
            int type = 1;
            if (cmdParams.cmdDet.commandQualifier == 0 || cmdParams.cmdDet.commandQualifier == 1
               ||cmdParams.cmdDet.commandQualifier == 2 || cmdParams.cmdDet.commandQualifier == 3) {
                type = 1;
               } else if (cmdParams.cmdDet.commandQualifier == 4) {
                type = 2;
               }
            mCmdIf.requestUtkRefresh(type, obtainMessage(MSG_ID_RIL_REFRESH_RESULT));
            mRuimRecords.handleRuimRefresh(cmdParams.cmdDet.commandQualifier);
            cmdParams.cmdDet.typeOfCommand = CommandType.SET_UP_IDLE_MODE_TEXT
                    .value();
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                    0, null);
            break;
            }
        case SET_UP_IDLE_MODE_TEXT:
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                    0, null);
            break;
        case LAUNCH_BROWSER:
        case SELECT_ITEM:
        case GET_INPUT:
        case GET_INKEY:
        case SEND_DTMF:
        case SEND_SS:
        case SEND_USSD:
        case PLAY_TONE:
        case SET_UP_CALL:
        case SEND_SMS:
            // nothing to do on telephony!
            break;
        case MORE_TIME:
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                    0, null);
            //There is no need to notify utkapp there is more time command
            //just send a respond is enougth
            return;
         case LOCAL_INFO:
            if (cmdParams.cmdDet.commandQualifier == 0 ||
                cmdParams.cmdDet.commandQualifier == 6){
                UtkLog.d(this, "Local information get AT data");
                mCmdIf.getUtkLocalInfo(obtainMessage(MSG_ID_RIL_LOCAL_INFO));
                mCurrntCmd = cmdMsg;
            }
            else{
                UtkLog.d(this, "handleCmdResponse Local info");
                sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                    0, new LocalInformationResponseData(cmdParams.cmdDet.commandQualifier, mLocalInfo));
                mCurrntCmd = null;
            }
            return;
            default:
            UtkLog.d(this, "Unsupported command");
            return;
        }
        mCurrntCmd = cmdMsg;
        Intent intent = new Intent(AppInterface.UTK_CMD_ACTION);
        intent.putExtra("UTK CMD", cmdMsg);
        mContext.sendBroadcast(intent);
    }

    /**
     * Handles RIL_UNSOL_UTK_SESSION_END unsolicited command from RIL.
     *
     */
    private void handleSessionEnd() {
        UtkLog.d(this, "SESSION END");

        mCurrntCmd = mMenuCmd;
        Intent intent = new Intent(AppInterface.UTK_SESSION_END_ACTION);
        mContext.sendBroadcast(intent);
    }

    private void sendTerminalResponse(CommandDetails cmdDet,
            ResultCode resultCode, boolean includeAdditionalInfo,
            int additionalInfo, ResponseData resp) {

        if (cmdDet == null) {
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // command details
        int tag = ComprehensionTlvTag.COMMAND_DETAILS.value();
        if (cmdDet.compRequired) {
            tag = 0x80 | ComprehensionTlvTag.COMMAND_DETAILS.value();
        }
        buf.write(tag);
        buf.write(0x03); // length
        buf.write(cmdDet.commandNumber);
        buf.write(cmdDet.typeOfCommand);
        buf.write(cmdDet.commandQualifier);

        // device identities
        tag = /*0x80 |*/ ComprehensionTlvTag.DEVICE_IDENTITIES.value();
        buf.write(tag);
        buf.write(0x02); // length
        buf.write(DEV_ID_TERMINAL); // source device id
        buf.write(DEV_ID_UICC); // destination device id

        // result
        tag = 0x80 | ComprehensionTlvTag.RESULT.value();
        buf.write(tag);
        int length = includeAdditionalInfo ? 2 : 1;
        buf.write(length);
        buf.write(resultCode.value());

        // additional info
        if (includeAdditionalInfo) {
            buf.write(additionalInfo);
        }

        // Fill optional data for each corresponding command
        if (resp != null) {
            resp.format(buf);
        }

        byte[] rawData = buf.toByteArray();
        String hexString = IccUtils.bytesToHexString(rawData);
        //if (Config.LOGD) {
            UtkLog.d(this, "TERMINAL RESPONSE: " + hexString);
        //}

        mCmdIf.sendTerminalResponse(hexString, null);
    }


    private void sendMenuSelection(int menuId, boolean helpRequired) {

        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // tag
        int tag = BerTlv.BER_MENU_SELECTION_TAG;
        buf.write(tag);

        // length
        buf.write(0x00); // place holder

        // device identities
        tag = /*0x80 |*/ComprehensionTlvTag.DEVICE_IDENTITIES.value();
        buf.write(tag);
        buf.write(0x02); // length
        buf.write(DEV_ID_KEYPAD); // source device id
        buf.write(DEV_ID_UICC); // destination device id

        // item identifier
        tag = /*80 |*/ ComprehensionTlvTag.ITEM_ID.value();
        buf.write(tag);
        buf.write(0x01); // length
        buf.write(menuId); // menu identifier chosen

        // help request
        if (helpRequired) {
            tag = ComprehensionTlvTag.HELP_REQUEST.value();
            buf.write(tag);
            buf.write(0x00); // length
        }

        byte[] rawData = buf.toByteArray();

        // write real length
        int len = rawData.length - 2; // minus (tag + length)
        rawData[1] = (byte) len;

        String hexString = IccUtils.bytesToHexString(rawData);

        mCmdIf.sendEnvelope(hexString, null);
    }

    private void eventDownload(int event, int sourceId, int destinationId,
            byte[] additionalInfo, boolean oneShot) {

        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // tag
        int tag = BerTlv.BER_EVENT_DOWNLOAD_TAG;
        buf.write(tag);

        // length
        buf.write(0x00); // place holder, assume length < 128.

        // event list
        tag = 0x80 | ComprehensionTlvTag.EVENT_LIST.value();
        buf.write(tag);
        buf.write(0x01); // length
        buf.write(event); // event value

        // device identities
        tag = 0x80 | ComprehensionTlvTag.DEVICE_IDENTITIES.value();
        buf.write(tag);
        buf.write(0x02); // length
        buf.write(sourceId); // source device id
        buf.write(destinationId); // destination device id

        // additional information
        if (additionalInfo != null) {
            for (byte b : additionalInfo) {
                buf.write(b);
            }
        }

        byte[] rawData = buf.toByteArray();

        // write real length
        int len = rawData.length - 2; // minus (tag + length)
        rawData[1] = (byte) len;

        String hexString = IccUtils.bytesToHexString(rawData);

        mCmdIf.sendEnvelope(hexString, null);
    }

    /**
     * Used for instantiating/updating the Service from the GsmPhone constructor.
     *
     * @param ci CommandsInterface object
     * @param ur RuimRecords object
     * @param context phone app context
     * @param fh Ruim file handler
     * @param uc CDMA SIM card
     * @return The only Service object in the system
     */
    public static UtkService getInstance(CommandsInterface ci,
            Context context, UiccCard ic) {

        Log.d("UtkService", " getInstance ci "+ci+" ic "+ic);
        
        UiccCardApplication ca = null;
        RuimFileHandler fh = null;
        RuimRecords ir = null;
        if (ic != null) {
            /* Since Cat is not tied to any application, but rather is Uicc application
             * in itself - just get first FileHandler and IccRecords object
             */
            ca = ic.getApplicationIndex(0);
            if (ca != null) {
                fh = (RuimFileHandler)ca.getIccFileHandler();
                ir = (RuimRecords)ca.getIccRecords();
            }
        }
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (ci == null || ca == null || ir == null || context == null || fh == null
                        || ic == null) {

                    Log.d("UtkService", " getInstance ca " + ca + " ir " + ir + " fh "+fh);
                    
                    return null;
                }
                HandlerThread thread = new HandlerThread("Utk Telephony service");
                thread.start();
                sInstance = new UtkService(ci, ca, ir, context, fh, ic);
                UtkLog.d(sInstance, "new sInstance");
            } else {
                if ((ca != null) && (mUiccApplication != ca)) {
                    if(mUiccApplication != null) {
                        mUiccApplication.unregisterForReady(sInstance);
                    }
                    mUiccApplication = ca;
                    mUiccApplication.registerForReady(sInstance, MSG_ID_RUIM_READY, null);
                    UtkLog.d(sInstance, "reinitialize with new ca");
                }

                if ((ir != null) && (mRuimRecords != ir)) {
                    mRuimRecords = ir;                
                    UtkLog.d(sInstance, "reinitialize with new ir");
                } 
                UtkLog.d(sInstance, "Return current sInstance");
            }
            return sInstance;
        }
    }

    /**
     * Used by application to get an AppInterface object.
     *
     * @return The only Service object in the system
     */
    public static AppInterface getInstance() {
        return getInstance(null, null, null);
    }

    @Override
    public void handleMessage(Message msg) {

        switch (msg.what) {
        case MSG_ID_SESSION_END:
        case MSG_ID_PROACTIVE_COMMAND:
        case MSG_ID_EVENT_NOTIFY:
        case MSG_ID_REFRESH:
            UtkLog.d(this, "ril message arrived");
            String data = null;
            if (msg.obj != null) {
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar != null && ar.result != null) {
                    try {
                        data = (String) ar.result;
                    } catch (ClassCastException e) {
                        break;
                    }
                }
            }
            mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, data));
            break;
        case MSG_ID_CALL_SETUP:
            mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, null));
            break;
        case MSG_ID_ICC_CHANGED:
            updateIccStatus();
            break;
        case MSG_ID_RUIM_READY:
            UtkLog.d(this, "utk profileDownload");
            mCmdIf.profileDownload("", null);     
            break;
        case MSG_ID_RIL_MSG_DECODED:
            handleRilMsg((RilMessage) msg.obj);
            break;
        case MSG_ID_RESPONSE:
            handleCmdResponse((UtkResponseMessage) msg.obj);
            break;          
        case MSG_ID_RIL_LOCAL_INFO:{
            AsyncResult aresult = (AsyncResult) msg.obj;

            if (aresult.result != null) {
            int info[] = (int[])aresult.result;

            if (info.length == 8) {
                mLocalInfo.Technology = info[0];
                mLocalInfo.MCC = info[1];
                mLocalInfo.IMSI_11_12 = info[2];
                mLocalInfo.SID = info[3];
                mLocalInfo.NID = info[4];
                mLocalInfo.BASE_ID = info[5];
                mLocalInfo.BASE_LAT = info[6];
                mLocalInfo.BASE_LONG = info[7];
            } else {
               UtkLog.d(sInstance, "MSG_ID_RIL_LOCAL_INFO error");
            }

           }
           sendTerminalResponse(mCurrntCmd.mCmdDet, ResultCode.OK, false,
            0, new LocalInformationResponseData(mCurrntCmd.mCmdDet.commandQualifier, mLocalInfo));
            mCurrntCmd = null;
        }
           break;
        case MSG_ID_RIL_REFRESH_RESULT:{
            UtkLog.d(this, "MSG_ID_RIL_REFRESH_RESULT  Complete! ");
            Intent intent = new Intent();
            intent.setAction("com.android.contacts.action.CONTACTS_INIT_RETRY_ACTION");
            mContext.sendBroadcast(intent);
            mCurrntCmd = null;
            break;
        }
        default:
            throw new AssertionError("Unrecognized UTK command: " + msg.what);
        }
    }

    public synchronized void onCmdResponse(UtkResponseMessage resMsg) {
        if (resMsg == null) {
            return;
        }
        // queue a response message.
        Message msg = this.obtainMessage(MSG_ID_RESPONSE, resMsg);
        msg.sendToTarget();
    }

    private boolean validateResponse(UtkResponseMessage resMsg) {
        if (mCurrntCmd != null) {
            return (resMsg.cmdDet.compareTo(mCurrntCmd.mCmdDet));
        }
        return false;
    }

    private boolean removeMenu(Menu menu) {
        try {
            if (menu.items.size() == 1 && menu.items.get(0) == null) {
                return true;
            }
        } catch (NullPointerException e) {
            UtkLog.d(this, "Unable to get Menu's items size");
            return true;
        }
        return false;
    }

    private void handleCmdResponse(UtkResponseMessage resMsg) {
        // Make sure the response details match the last valid command. An invalid
        // response is a one that doesn't have a corresponding proactive command
        // and sending it can "confuse" the baseband/ril.
        // One reason for out of order responses can be UI glitches. For example,
        // if the application launch an activity, and that activity is stored
        // by the framework inside the history stack. That activity will be
        // available for relaunch using the latest application dialog
        // (long press on the home button). Relaunching that activity can send
        // the same command's result again to the UtkService and can cause it to
        // get out of sync with the SIM.
        UtkLog.d(this, "handleCmdResponse");
        if (!validateResponse(resMsg)) {
                UtkLog.d(this, "handleCmdResponse:validateResponse");
            return;
        }
        ResponseData resp = null;
        boolean helpRequired = false;
        CommandDetails cmdDet = resMsg.getCmdDetails();
        UtkLog.d(this, "handleCmdResponse:resMsg.resCode = "+resMsg.resCode);
        switch (resMsg.resCode) {
        case HELP_INFO_REQUIRED:
            helpRequired = true;
            // fall through
        case OK:
        case PRFRMD_WITH_PARTIAL_COMPREHENSION:
        case PRFRMD_WITH_MISSING_INFO:
        case PRFRMD_WITH_ADDITIONAL_EFS_READ:
        case PRFRMD_ICON_NOT_DISPLAYED:
        case PRFRMD_MODIFIED_BY_NAA:
        case PRFRMD_LIMITED_SERVICE:
        case PRFRMD_WITH_MODIFICATION:
        case PRFRMD_NAA_NOT_ACTIVE:
        case PRFRMD_TONE_NOT_PLAYED:
            UtkLog.d(this, "handleCmdResponse cmd = "+AppInterface.CommandType.fromInt(cmdDet.typeOfCommand));
            switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
            case SET_UP_MENU:
                helpRequired = resMsg.resCode == ResultCode.HELP_INFO_REQUIRED;
                sendMenuSelection(resMsg.usersMenuSelection, helpRequired);
                return;
            case SELECT_ITEM:
                resp = new SelectItemResponseData(resMsg.usersMenuSelection);
                break;
            case GET_INPUT:
            case GET_INKEY:
                Input input = mCurrntCmd.geInput();
                if (!input.yesNo) {
                    // when help is requested there is no need to send the text
                    // string object.
                    if (!helpRequired) {
                        resp = new GetInkeyInputResponseData(resMsg.usersInput,
                                input.ucs2, input.packed);
                    }
                } else {
                    resp = new GetInkeyInputResponseData(
                            resMsg.usersYesNoSelection);
                }
                break;
            case DISPLAY_TEXT:
            case LAUNCH_BROWSER:
                break;
            case SET_UP_CALL:
                mCmdIf.handleCallSetupRequestFromUim(resMsg.usersConfirm, null);
                // No need to send terminal response for SET UP CALL. The user's
                // confirmation result is send back using a dedicated ril message
                // invoked by the CommandInterface call above.
                mCurrntCmd = null;
                return;
            }
            break;
        case NO_RESPONSE_FROM_USER:
        case UICC_SESSION_TERM_BY_USER:
        case BACKWARD_MOVE_BY_USER:
            resp = null;
            break;
        default:
            return;
        }
        sendTerminalResponse(cmdDet, resMsg.resCode, false, 0, resp);
        mCurrntCmd = null;
    }
}
