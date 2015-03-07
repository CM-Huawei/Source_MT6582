/*
 * Copyright (C) 2006, 2012 The Android Open Source Project
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

import static android.Manifest.permission.READ_PHONE_STATE;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.Rlog;
import android.view.WindowManager;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.DefaultSIMSettings;
import com.mediatek.common.featureoption.FeatureOption;
import com.android.internal.telephony.gemini.GeminiPhone;
import com.android.internal.telephony.PhoneFactory;

import android.os.SystemProperties;

import com.android.internal.R;
import java.util.ArrayList;
import com.mediatek.common.telephony.internationalroaming.InternationalRoamingConstants;
import android.text.TextUtils;
import java.util.Locale;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * {@hide}
 */
public class UiccCard {
    protected static final String LOG_TAG = "UiccCard";
    protected static final boolean DBG = true;

    //[02772] start
    
    static final String[] UICCCARD_PROPERTY_RIL_UICC_TYPE = {
        "gsm.ril.uicctype",
        "gsm.ril.uicctype.2",
        "gsm.ril.uicctype.3",
        "gsm.ril.uicctype.4",
    };

    private String mIccType = null; /* Add for USIM detect */
    private Phone mPhone;
    //[02772] end

    private final Object mLock = new Object();
    private CardState mCardState;
    private PinState mUniversalPinState;
    private int mGsmUmtsSubscriptionAppIndex;
    private int mCdmaSubscriptionAppIndex;
    private int mImsSubscriptionAppIndex;
    private UiccCardApplication[] mUiccApplications =
            new UiccCardApplication[IccCardStatus.CARD_MAX_APPS];
    private Context mContext;
    private CommandsInterface mCi;
    private CatService mCatService;
    private boolean mDestroyed = false; //set to true once this card is commanded to be disposed of.
    private RadioState mLastRadioState =  RadioState.RADIO_UNAVAILABLE;

    private RegistrantList mAbsentRegistrants = new RegistrantList();
    private RegistrantList mDualImsiReadyRegistrants = new RegistrantList();
    //private ArrayList<IccRecords> mIccRecordsList = new ArrayList<IccRecords>();
    //private ArrayList<IccFileHandler> mIccFileHanderList = new ArrayList<IccFileHandler>();
    private IccRecords mIccRecords = null;
    private IccFileHandler mIccFileHandler = null;
    private String mGsmImsi = null;
    private String mCdmaImsi = null;
    private boolean mGetImsiDoneFlag = false;

    private static final int EVENT_CARD_REMOVED = 13;
    private static final int EVENT_CARD_ADDED = 14;
    private static final int EVENT_IMSI_READY = 15;
    private static final int EVENT_GET_GSM_IMSI_DONE = 16;
    private static final int EVENT_GET_CDMA_IMSI_DONE = 17;
    
    // NFC SEEK start
    private static final int EVENT_EXCHANGE_APDU_DONE = 100;
    private static final int EVENT_OPEN_CHANNEL_DONE = 101;
    private static final int EVENT_CLOSE_CHANNEL_DONE = 102;
    private static final int EVENT_SIM_IO_DONE = 103;
    private static final int EVENT_GET_ATR_DONE = 104;
    private static final int EVENT_OPEN_CHANNEL_WITH_SW_DONE = 105;
    // NFC SEEK end

    private int mSimId;
    
    public UiccCard(Context c, CommandsInterface ci, IccCardStatus ics) {
        if (DBG) log("Creating");
        mCardState = ics.mCardState;
        update(c, ci, ics, false);

        if(FeatureOption.MTK_GEMINI_SUPPORT) {
            mPhone = ((GeminiPhone)(PhoneFactory.getDefaultPhone())).getPhonebyId(mSimId);
        } else {
            mPhone = PhoneFactory.getDefaultPhone();
        }
    }

    public UiccCard(Context c, CommandsInterface ci, IccCardStatus ics, int simId) {
        if (DBG) log("Creating simId " + simId);
        mCardState = ics.mCardState;
        mSimId = simId;
        update(c, ci, ics, false);
        
        if(FeatureOption.MTK_GEMINI_SUPPORT) {
            mPhone = ((GeminiPhone)(PhoneFactory.getDefaultPhone())).getPhonebyId(mSimId);
        } else {
            mPhone = PhoneFactory.getDefaultPhone();
        }
    }

    public void dispose() {
        synchronized (mLock) {
            if (DBG) log("Disposing card");
            if (mCatService != null) mCatService.dispose();
            
            for (UiccCardApplication app : mUiccApplications) {
                if (app != null) {
                    app.dispose();
                }
            }
            mCatService = null;
            mUiccApplications = null;
            mGetImsiDoneFlag = false;
            mIccRecords.unregisterForImsiReady(mHandler);
            mIccRecords = null;
            mIccFileHandler = null;
        }
    }

    public void update(Context c, CommandsInterface ci, IccCardStatus ics) {
        synchronized (mLock) {
            if (mDestroyed) {
                loge("Updated after destroyed! Fix me!");
                return;
            }
            CardState oldState = mCardState;
            mCardState = ics.mCardState;
            mUniversalPinState = ics.mUniversalPinState;
            mGsmUmtsSubscriptionAppIndex = ics.mGsmUmtsSubscriptionAppIndex;
            mCdmaSubscriptionAppIndex = ics.mCdmaSubscriptionAppIndex;
            mImsSubscriptionAppIndex = ics.mImsSubscriptionAppIndex;
            mContext = c;
            mCi = ci;
            //update applications
            if (DBG) log(ics.mApplications.length + " applications");
            for ( int i = 0; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] == null) {
                    //Create newly added Applications
                    if (i < ics.mApplications.length) {
                        mUiccApplications[i] = new UiccCardApplication(this,
                                ics.mApplications[i], mContext, mCi);
                        mIccRecords = mUiccApplications[i].getIccRecords();
                        //mIccRecordsList.add(mIccRecords);
                        mIccFileHandler = mUiccApplications[i].getIccFileHandler();
                    }
                } else if (i >= ics.mApplications.length) {
                    //Delete removed applications
                    if (DBG) log("update mUiccApplications[" + i + "] dispose");
                    mUiccApplications[i].dispose();
                    mUiccApplications[i] = null;
                } else {
                    //Update the rest
                    if (DBG) log("update mUiccApplications[" + i + "] update");
                    mUiccApplications[i].update(ics.mApplications[i], mContext, mCi);
                }
            }
            if (mIccRecords != null)
                mIccRecords.registerForImsiReady(mHandler, EVENT_IMSI_READY, null);
            if (DBG) log("update mUiccApplications.length: " + mUiccApplications.length);
            if (mUiccApplications.length > 0 && mUiccApplications[0] != null) {
                 // Initialize or Reinitialize CatService
                 mCatService = CatService.getInstance(mCi,
                                                      mContext,
                                                      this);
            } else {
                if (mCatService != null) {
                   mCatService.dispose();
                }
                mCatService = null;
            }

            sanitizeApplicationIndexes();

            RadioState radioState = mCi.getRadioState();
            if (DBG) log("update: radioState=" + radioState + " mLastRadioState="
                    + mLastRadioState);
            // No notifications while radio is off or we just powering up
            //if (radioState == RadioState.RADIO_ON && mLastRadioState == RadioState.RADIO_ON) {
            if (radioState != RadioState.RADIO_UNAVAILABLE) {
                if (mCardState == CardState.CARDSTATE_ABSENT) {
                    if (DBG) log("update: notify card removed");
                    mAbsentRegistrants.notifyRegistrants();
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_CARD_REMOVED, null));
                } else if (oldState == CardState.CARDSTATE_ABSENT &&
                        mCardState != CardState.CARDSTATE_ABSENT) {
                    if (DBG) log("update: notify card added");
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_CARD_ADDED, null));
                }
            }
            mLastRadioState = radioState;
        }
    }

    public void update(Context c, CommandsInterface ci, IccCardStatus ics, boolean isUpdateSimInfo) {
        synchronized (mLock) {
            if (mDestroyed) {
                loge("Updated after destroyed! Fix me!");
                return;
            }
            CardState oldState = mCardState;
            mCardState = ics.mCardState;
            mUniversalPinState = ics.mUniversalPinState;
            mGsmUmtsSubscriptionAppIndex = ics.mGsmUmtsSubscriptionAppIndex;
            mCdmaSubscriptionAppIndex = ics.mCdmaSubscriptionAppIndex;
            mImsSubscriptionAppIndex = ics.mImsSubscriptionAppIndex;
            mContext = c;
            mCi = ci;
            //update applications
            if (DBG) log(ics.mApplications.length + " applications");
            for ( int i = 0; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] == null) {
                    //Create newly added Applications
                    if (i < ics.mApplications.length) {
                        mUiccApplications[i] = new UiccCardApplication(this,
                                ics.mApplications[i], mContext, mCi);
                        mIccRecords = mUiccApplications[i].getIccRecords();
                        mIccFileHandler = mUiccApplications[i].getIccFileHandler();
                    }
                } else if (i >= ics.mApplications.length) {
                    //Delete removed applications
                    if (DBG) log("update mUiccApplications[" + i + "] dispose");
                    mUiccApplications[i].dispose();
                    mUiccApplications[i] = null;
                } else {
                    //Update the rest
                    if (DBG) log("update mUiccApplications[" + i + "] update");
                    mUiccApplications[i].update(ics.mApplications[i], mContext, mCi);
                }
            }
            //if (!mIccRecordsList.isEmpty()){
           //    for (IccRecords mIccRecords: mIccRecordsList)
           if (mIccRecords != null)
               mIccRecords.registerForImsiReady(mHandler, EVENT_IMSI_READY, null);

            if (DBG) log("update mUiccApplications.length: " + mUiccApplications.length);
            if (mUiccApplications.length > 0 && mUiccApplications[0] != null) {
                // Initialize or Reinitialize CatService
                mCatService = CatService.getInstance(mCi,
                                                     mContext,
                                                     this);
            } else {
                if (mCatService != null) {
                    mCatService.dispose();
                }
                mCatService = null;
            }

            sanitizeApplicationIndexes();


            RadioState radioState = mCi.getRadioState();
            if (DBG) log("update: radioState=" + radioState + " mLastRadioState="
                    + mLastRadioState);
            if(isUpdateSimInfo) {
                // No notifications while radio is off or we just powering up
                //if (radioState == RadioState.RADIO_ON && mLastRadioState == RadioState.RADIO_ON) {
                if (radioState != RadioState.RADIO_UNAVAILABLE) {
                    if (mCardState == CardState.CARDSTATE_ABSENT) {
                        if (DBG) log("update: notify card removed");
                        mAbsentRegistrants.notifyRegistrants();
                        mHandler.sendMessage(mHandler.obtainMessage(EVENT_CARD_REMOVED, null));
                        //Update SIM inserted state
                        if (FeatureOption.MTK_GEMINI_SUPPORT) {
                            Phone defaultPhone = PhoneFactory.getDefaultPhone();
                            ((GeminiPhone)defaultPhone).setSimInsertedState(getMySimId(), false);
                        }
                    } else if (oldState == CardState.CARDSTATE_ABSENT &&
                            mCardState != CardState.CARDSTATE_ABSENT) {
                        if (DBG) log("update: notify card added");
                        mHandler.sendMessage(mHandler.obtainMessage(EVENT_CARD_ADDED, null));
                    }
                }
            }
            mLastRadioState = radioState;
        }
    }

    @Override
    protected void finalize() {
        if (DBG) log("UiccCard finalized");
    }

    /**
     * This function makes sure that application indexes are valid
     * and resets invalid indexes. (This should never happen, but in case
     * RIL misbehaves we need to manage situation gracefully)
     */
    private void sanitizeApplicationIndexes() {
        mGsmUmtsSubscriptionAppIndex =
                checkIndex(mGsmUmtsSubscriptionAppIndex, AppType.APPTYPE_SIM, AppType.APPTYPE_USIM);
        mCdmaSubscriptionAppIndex =
                checkIndex(mCdmaSubscriptionAppIndex, AppType.APPTYPE_RUIM, AppType.APPTYPE_CSIM);
        mImsSubscriptionAppIndex =
                checkIndex(mImsSubscriptionAppIndex, AppType.APPTYPE_ISIM, null);
    }

    private int checkIndex(int index, AppType expectedAppType, AppType altExpectedAppType) {
        if (mUiccApplications == null || index >= mUiccApplications.length) {
            loge("App index " + index + " is invalid since there are no applications");
            return -1;
        }

        if (index < 0) {
            // This is normal. (i.e. no application of this type)
            return -1;
        }

        if (mUiccApplications[index].getType() != expectedAppType &&
            mUiccApplications[index].getType() != altExpectedAppType) {
            loge("App index " + index + " is invalid since it's not " +
                    expectedAppType + " and not " + altExpectedAppType);
            return -1;
        }

        // Seems to be valid
        return index;
    }

    /**
     * Notifies handler of any transition into State.ABSENT
     */
    public void registerForAbsent(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);

            mAbsentRegistrants.add(r);

            if (mCardState == CardState.CARDSTATE_ABSENT) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForAbsent(Handler h) {
        synchronized (mLock) {
            mAbsentRegistrants.remove(h);
        }
    }

    public void registerForDualImsiReady(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant(h, what, obj);
            mDualImsiReadyRegistrants.add(r);
        }

    }

    public void unregisterForDualImsiReady(Handler h) {
        synchronized (mLock) {
            mDualImsiReadyRegistrants.remove(h);
        }
    }

    private void onIccSwap(boolean isAdded) {
        synchronized (mLock) {
            // TODO: Here we assume the device can't handle SIM hot-swap
            //      and has to reboot. We may want to add a property,
            //      e.g. REBOOT_ON_SIM_SWAP, to indicate if modem support
            //      hot-swap.
            DialogInterface.OnClickListener listener = null;


            // TODO: SimRecords is not reset while SIM ABSENT (only reset while
            //       Radio_off_or_not_available). Have to reset in both both
            //       added or removed situation.
            listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    synchronized (mLock) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            if (DBG) log("Reboot due to SIM swap");
                            PowerManager pm = (PowerManager) mContext
                                    .getSystemService(Context.POWER_SERVICE);
                            pm.reboot("SIM is added.");
                        }
                    }
                }

            };

            Resources r = Resources.getSystem();

            String title = (isAdded) ? r.getString(R.string.sim_added_title) :
                r.getString(R.string.sim_removed_title);
            String message = (isAdded) ? r.getString(R.string.sim_added_message) :
                r.getString(R.string.sim_removed_message);
            String buttonTxt = r.getString(R.string.sim_restart_button);

            AlertDialog dialog = new AlertDialog.Builder(mContext)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(buttonTxt, listener)
            .create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();
        }
    }

    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg){
            if (mDestroyed) {
                loge("Received message " + msg + "[" + msg.what
                        + "] while being destroyed. Ignoring.");
                return;
            }
            
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_CARD_REMOVED:
                    if (FeatureOption.MTK_GEMINI_SUPPORT) {
                        Phone defaultPhone = PhoneFactory.getDefaultPhone();
                        ((GeminiPhone)defaultPhone).onSimHotSwap(getMySimId(), false);
                    } else {
                        DefaultSIMSettings.onAllIccidQueryComplete(mContext, mPhone, null, null, null, null, false);
                    }
                    //onIccSwap(false);
                    break;
                case EVENT_CARD_ADDED:
                    //onIccSwap(true);
                    break;
                // NFC SEEK start
                case EVENT_EXCHANGE_APDU_DONE:
                case EVENT_OPEN_CHANNEL_DONE:
                case EVENT_CLOSE_CHANNEL_DONE:
                case EVENT_SIM_IO_DONE:
                case EVENT_GET_ATR_DONE:
                case EVENT_OPEN_CHANNEL_WITH_SW_DONE:
                    ar = (AsyncResult)msg.obj;
                    if(ar.exception != null) {
                        Rlog.e(LOG_TAG, "Error in SIM access with exception"
                            + ar.exception);
                    }
                    AsyncResult.forMessage(((Message)ar.userObj),
                            ar.result, ar.exception);
                    ((Message)ar.userObj).sendToTarget();
                    break;
                // NFC SEEK end
                case EVENT_IMSI_READY:
                    if (mIccRecords instanceof SIMRecords) {
                        mGsmImsi = mIccRecords.getIMSI();
                        log("mGsmImsi = " + mGsmImsi);
                        mIccFileHandler.loadEFTransparent(IccConstants.EF_CSIM_IMSIM, IccConstants.MF_SIM + IccConstants.DF_CDMA,
                                obtainMessage(EVENT_GET_CDMA_IMSI_DONE));
                    } else if (mIccRecords instanceof RuimRecords) {
                        mCdmaImsi = mIccRecords.getIMSI();
                        log("mCdmaImsi = " + mCdmaImsi);
                        mIccFileHandler.loadEFTransparent(IccConstants.EF_IMSI, IccConstants.MF_SIM + IccConstants.DF_GSM,
                                obtainMessage(EVENT_GET_GSM_IMSI_DONE));
                    }
                    break;
                case EVENT_GET_CDMA_IMSI_DONE:
                    if (DBG) log("handleMessage (EVENT_GET_CDMA_IMSI_DONE)");
                    mGetImsiDoneFlag = true;
                    ar = (AsyncResult)msg.obj;
                    if (ar.exception == null) {
                        byte[] mCdmaRawData = (byte[])ar.result;
                        mCdmaImsi = parseCdmaImsi(mCdmaRawData);
                        log("mCdmaImsi = " + mCdmaImsi);
                    } else {
                        log("exception" + ar.exception);
                    }
                    log("notify GET_CDMA_IMSI_DONE");
                    mDualImsiReadyRegistrants.notifyRegistrants();
                    break;
                case EVENT_GET_GSM_IMSI_DONE:
                    mGetImsiDoneFlag = true;
                    if (DBG) log("handleMessage (EVENT_GET_GSM_IMSI_DONE)");
                    ar = (AsyncResult)msg.obj;
                    byte[] mGsmRawData = (byte[])ar.result;
                    if (ar.exception == null) {
                        mGsmImsi = IccUtils.bcdToString(mGsmRawData, 1, mGsmRawData[0]);
                        mGsmImsi = mGsmImsi.substring(1,mGsmImsi.length());
                        log("mGsmImsi = " + mGsmImsi);
                    } else {
                        log("exception" + ar.exception);
                    }
                    log("notify GET_GSM_IMSI_DONE");
                    mDualImsiReadyRegistrants.notifyRegistrants();
                    break;
                default:
                    loge("Unknown Event " + msg.what);
            }
        }
    };

    public boolean isApplicationOnIcc(IccCardApplicationStatus.AppType type) {
        synchronized (mLock) {
            // [02772] start
            if (mUiccApplications.length == 0){ 
                if (mIccType == null || mIccType.equals("")) {
                    mIccType = SystemProperties.get(UICCCARD_PROPERTY_RIL_UICC_TYPE[getMySimId()]);
                }
                if (DBG) log("isApplicationOnIcc(): mIccType = " + mIccType);
    
                if ((mIccType != null) && (mIccType.equals("USIM"))) {
                    return true;
                } else {
                    return false;
                }
            }
            // [02772] end

            for (int i = 0 ; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] != null && mUiccApplications[i].getType() == type) {
                    return true;
                }
            }
            return false;
        }
    }

    public CardState getCardState() {
        synchronized (mLock) {
            return mCardState;
        }
    }

    public PinState getUniversalPinState() {
        synchronized (mLock) {
            return mUniversalPinState;
        }
    }

    public UiccCardApplication getApplication(int family) {
        synchronized (mLock) {
            int index = IccCardStatus.CARD_MAX_APPS;
            switch (family) {
                case UiccController.APP_FAM_3GPP:
                    index = mGsmUmtsSubscriptionAppIndex;
                    break;
                case UiccController.APP_FAM_3GPP2:
                    index = mCdmaSubscriptionAppIndex;
                    break;
                case UiccController.APP_FAM_IMS:
                    index = mImsSubscriptionAppIndex;
                    break;
            }
            if (index >= 0 && index < mUiccApplications.length) {
                return mUiccApplications[index];
            }
            return null;
        }
    }

    public UiccCardApplication getApplicationIndex(int index) {
        synchronized (mLock) {
            if (index >= 0 && index < mUiccApplications.length) {
                return mUiccApplications[index];
            }
            return null;
        }
    }

    public int getMySimId() {
        return mSimId;
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, "[SIM " + mSimId + "]" + msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, "[SIM " + mSimId + "]" + msg);
    }

    // NFC SEEK start
    public void exchangeApdu(int cla, int command, int channel, int p1, int p2,
            int p3, String data, Message onComplete) {
        mCi.iccExchangeAPDU(cla, command, channel, p1, p2, p3, data,
                mHandler.obtainMessage(EVENT_EXCHANGE_APDU_DONE, onComplete));
    }

    public void openLogicalChannel(String AID, Message onComplete) {
        mCi.iccOpenChannel(AID,
                mHandler.obtainMessage(EVENT_OPEN_CHANNEL_DONE, onComplete));
    }

    public void closeLogicalChannel(int channel, Message onComplete) {
        mCi.iccCloseChannel(channel,
                mHandler.obtainMessage(EVENT_CLOSE_CHANNEL_DONE, onComplete));
    }

    public void exchangeSimIo(int fileID, int command,
                                           int p1, int p2, int p3, String pathID, Message onComplete) {
        mCi.iccIO(command,fileID,pathID,p1,p2,p3,null,null,
               mHandler.obtainMessage(EVENT_SIM_IO_DONE, onComplete));
    }   

    public void exchangeSimIo(int fileID, int command,
                                           int p1, int p2, int p3, String pathID, String data, String pin2, Message onComplete) {
        mCi.iccIO(command,fileID,pathID,p1,p2,p3,data,pin2,
              mHandler.obtainMessage(EVENT_SIM_IO_DONE, onComplete));
    }

    public void iccGetAtr(Message onComplete) {
        mCi.iccGetATR(mHandler.obtainMessage(EVENT_GET_ATR_DONE, onComplete));
    }
    
    public void iccOpenChannelWithSw(String AID, Message onComplete) {
        mCi.iccOpenChannelWithSw(AID, 
				mHandler.obtainMessage(EVENT_OPEN_CHANNEL_WITH_SW_DONE, onComplete));
    }
    // NFC SEEK end

    // retrun usim property or use uicccardapplication app type
    public String getIccCardType() {
        mIccType = SystemProperties.get(UICCCARD_PROPERTY_RIL_UICC_TYPE[getMySimId()]);
        if (DBG) log("getIccCardType(): iccType = " + mIccType);
        return mIccType;
    }
    public int getInternationalCardType() {
        int cardType;
        if (mGetImsiDoneFlag == false || mIccRecords == null) {
            loge("mIccRecords has not been initialized or both IMSI have not been loaded");
            cardType = -1;;
        } else {
            log("mGsmImsi = " + mGsmImsi + "mCdmaImsi = " + mCdmaImsi);
            cardType = parseCardType(mCdmaImsi,mGsmImsi);
        }
        log("cardType is" + cardType);
        return cardType;
    }
    public String getGsmImsi () {
        log(this + " 's gsmImsi is" + mGsmImsi);
        return mGsmImsi;
    }
    public String getCdmaImsi () {
        log (this + " 's cdmaImsi is "+ mCdmaImsi);
        return mCdmaImsi;
    }

    private int parseCardType(String cdmaIMSI, String gsmIMSI) {
        if (TextUtils.isEmpty(cdmaIMSI) && !TextUtils.isEmpty(gsmIMSI)) {
            return InternationalRoamingConstants.CARD_TYPE_SINGLE_GSM;
        } else if (!TextUtils.isEmpty(cdmaIMSI) && TextUtils.isEmpty(gsmIMSI)) {
            return InternationalRoamingConstants.CARD_TYPE_SINGLE_CDMA;
        } else if (!TextUtils.isEmpty(cdmaIMSI) && !TextUtils.isEmpty(gsmIMSI)) {
            return InternationalRoamingConstants.CARD_TYPE_DUAL_MODE;
        } else {
            return InternationalRoamingConstants.CARD_TYPE_UNKNOWN;
        }
    }

    private String parseCdmaImsi (byte[] data) {
        String mCdmaImsiTmp= null;
        if (data == null) {
            return null;
        } else {
            boolean provisioned = ((data[7] & 0x80) == 0x80);
            log("provisioned = "+ provisioned);
            if (provisioned) {
                // Parse Mcc
                int Mcc3digits = ((0x03 & data[9]) << 8) + (0xFF & data[8]);
                // Parse Mnc
                int Mnc2digits = 0xFF & data[6];
                // Parse min partI
                int first3digits = ((0x03 & data[2]) << 8) + (0xFF & data[1]);
                // Parse min partII
                int second3digits = (((0xFF & data[5]) << 8) | (0xFF & data[4])) >> 6;
                // Parse min partIII
                int digit7 = 0x0F & (data[4] >> 2);
                if (digit7 > 0x09) digit7 = 0;
                // Parse min partIV
                int last3digits = ((0x03 & data[4]) << 8) | (0xFF & data[3]);

                Mcc3digits = adjstMinDigits(Mcc3digits,3);
                Mnc2digits = adjstMinDigits(Mnc2digits,2);
                first3digits = adjstMinDigits(first3digits,3);
                second3digits = adjstMinDigits(second3digits,3);
                last3digits = adjstMinDigits(last3digits,3);

                StringBuilder builderImsi = new StringBuilder();
                builderImsi.append(String.format(Locale.US, "%03d", Mcc3digits));
                builderImsi.append(String.format(Locale.US, "%02d", Mnc2digits));
                builderImsi.append(String.format(Locale.US, "%03d", first3digits));
                builderImsi.append(String.format(Locale.US, "%03d", second3digits));
                builderImsi.append(String.format(Locale.US, "%d", digit7));
                builderImsi.append(String.format(Locale.US, "%03d", last3digits));
                mCdmaImsiTmp = builderImsi.toString();
                if (DBG) log("mCdmaImsiTmp present=" + mCdmaImsiTmp);
                return mCdmaImsiTmp;
            } else {
                if (DBG) log("mCSimImsiM not present");
                return null;
            }
        }
    }

    private int adjstMinDigits (int digits, int length) {
        // Per C.S0005 section 2.3.1.
        if (length == 3) {
            digits += 111;
            digits = (digits % 10 == 0)?(digits - 10):digits;
            digits = ((digits / 10) % 10 == 0)?(digits - 100):digits;
            digits = ((digits / 100) % 10 == 0)?(digits - 1000):digits;
        } else if (length == 2) {
            digits += 11;
            digits = (digits % 10 == 0)?(digits - 10):digits;
            digits = ((digits / 10) % 10 == 0)?(digits - 100):digits;
        }
        return digits;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UiccCard:");
        pw.println(" mCi=" + mCi);
        pw.println(" mDestroyed=" + mDestroyed);
        pw.println(" mLastRadioState=" + mLastRadioState);
        pw.println(" mCatService=" + mCatService);
        pw.println(" mAbsentRegistrants: size=" + mAbsentRegistrants.size());
        for (int i = 0; i < mAbsentRegistrants.size(); i++) {
            pw.println("  mAbsentRegistrants[" + i + "]="
                    + ((Registrant)mAbsentRegistrants.get(i)).getHandler());
        }
        pw.println(" mCardState=" + mCardState);
        pw.println(" mUniversalPinState=" + mUniversalPinState);
        pw.println(" mGsmUmtsSubscriptionAppIndex=" + mGsmUmtsSubscriptionAppIndex);
        pw.println(" mCdmaSubscriptionAppIndex=" + mCdmaSubscriptionAppIndex);
        pw.println(" mImsSubscriptionAppIndex=" + mImsSubscriptionAppIndex);
        pw.println(" mImsSubscriptionAppIndex=" + mImsSubscriptionAppIndex);
        pw.println(" mUiccApplications: length=" + mUiccApplications.length);
        for (int i = 0; i < mUiccApplications.length; i++) {
            if (mUiccApplications[i] == null) {
                pw.println("  mUiccApplications[" + i + "]=" + null);
            } else {
                pw.println("  mUiccApplications[" + i + "]="
                        + mUiccApplications[i].getType() + " " + mUiccApplications[i]);
            }
        }
        pw.println();
        // Print details of all applications
        for (UiccCardApplication app : mUiccApplications) {
            if (app != null) {
                app.dump(fd, pw, args);
                pw.println();
            }
        }
        // Print details of all IccRecords
        for (UiccCardApplication app : mUiccApplications) {
            if (app != null) {
                IccRecords ir = app.getIccRecords();
                if (ir != null) {
                    ir.dump(fd, pw, args);
                    pw.println();
                }
            }
        }
        pw.flush();
    }
}
