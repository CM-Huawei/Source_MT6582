/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.internal.telephony.sip;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;

/// M: Used in writePhbEntry method.
import com.android.internal.telephony.PhbEntry;

/**
 * SIP doesn't need CommandsInterface. The class does nothing but made to work
 * with PhoneBase's constructor.
 */
class SipCommandInterface extends BaseCommands implements CommandsInterface {
    SipCommandInterface(Context context) {
        super(context);
    }

    @Override public void setOnNITZTime(Handler h, int what, Object obj) {
    }

    @Override
    public void getIccCardStatus(Message result) {
    }

    @Override
    public void supplyIccPin(String pin, Message result) {
    }

    @Override
    public void supplyIccPuk(String puk, String newPin, Message result) {
    }

    @Override
    public void supplyIccPin2(String pin, Message result) {
    }

    @Override
    public void supplyIccPuk2(String puk, String newPin2, Message result) {
    }

    @Override
    public void changeIccPin(String oldPin, String newPin, Message result) {
    }

    @Override
    public void changeIccPin2(String oldPin2, String newPin2, Message result) {
    }

    @Override
    public void changeBarringPassword(String facility, String oldPwd,
            String newPwd, Message result) {
    }

    @Override
    public void supplyNetworkDepersonalization(String netpin, Message result) {
    }

    @Override
    public void getCurrentCalls(Message result) {
    }

    @Override
    @Deprecated public void getPDPContextList(Message result) {
    }

    @Override
    public void getDataCallList(Message result) {
    }

    @Override
    public void dial(String address, int clirMode, Message result) {
    }

    @Override
    public void dial(String address, int clirMode, UUSInfo uusInfo,
            Message result) {
    }

    @Override
    public void getIMSI(Message result) {
    }

    @Override
    public void getIMSIForApp(String aid, Message result) {
    }

    @Override
    public void getIMEI(Message result) {
    }

    @Override
    public void getIMEISV(Message result) {
    }


    @Override
    public void hangupConnection (int gsmIndex, Message result) {
    }

    @Override
    public void hangupWaitingOrBackground (Message result) {
    }

    @Override
    public void hangupForegroundResumeBackground (Message result) {
    }

    @Override
    public void switchWaitingOrHoldingAndActive (Message result) {
    }

    @Override
    public void conference (Message result) {
    }


    @Override
    public void setPreferredVoicePrivacy(boolean enable, Message result) {
    }

    @Override
    public void getPreferredVoicePrivacy(Message result) {
    }

    @Override
    public void separateConnection (int gsmIndex, Message result) {
    }

    @Override
    public void acceptCall (Message result) {
    }

    @Override
    public void rejectCall (Message result) {
    }

    @Override
    public void explicitCallTransfer (Message result) {
    }

    @Override
    public void getLastCallFailCause (Message result) {
    }

    @Deprecated
    @Override
    public void getLastPdpFailCause (Message result) {
    }

    @Override
    public void getLastDataCallFailCause (Message result) {
    }

    @Override
    public void setMute (boolean enableMute, Message response) {
    }

    @Override
    public void getMute (Message response) {
    }

    @Override
    public void getSignalStrength (Message result) {
    }

    @Override
    public void getVoiceRegistrationState (Message result) {
    }

    @Override
    public void getDataRegistrationState (Message result) {
    }

    @Override
    public void getOperator(Message result) {
    }

    @Override
    public void sendDtmf(char c, Message result) {
    }

    @Override
    public void startDtmf(char c, Message result) {
    }

    @Override
    public void stopDtmf(Message result) {
    }

    @Override
    public void sendBurstDtmf(String dtmfString, int on, int off,
            Message result) {
    }

    @Override
    public void sendSMS (String smscPDU, String pdu, Message result) {
    }

    @Override
    public void sendCdmaSms(byte[] pdu, Message result) {
    }

    @Override
    public void sendImsGsmSms (String smscPDU, String pdu,
            int retry, int messageRef, Message response) {
    }

    @Override
    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef,
            Message response) {
    }

    @Override
    public void getImsRegistrationState (Message result) {
    }

    @Override
    public void deleteSmsOnSim(int index, Message response) {
    }

    @Override
    public void deleteSmsOnRuim(int index, Message response) {
    }

    @Override
    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
    }

    @Override
    public void writeSmsToRuim(int status, String pdu, Message response) {
    }

    @Override
    public void setupDataCall(String radioTechnology, String profile,
            String apn, String user, String password, String authType,
            String protocol, Message result) {
    }

    @Override
    public void deactivateDataCall(int cid, int reason, Message result) {
    }

    @Override
    public void setRadioPower(boolean on, Message result) {
    }

    @Override
    public void setSuppServiceNotifications(boolean enable, Message result) {
    }

    @Override
    public void acknowledgeLastIncomingGsmSms(boolean success, int cause,
            Message result) {
    }

    @Override
    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause,
            Message result) {
    }

    @Override
    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu,
            Message result) {
    }

    @Override
    public void iccIO (int command, int fileid, String path, int p1, int p2,
            int p3, String data, String pin2, Message result) {
    }
    @Override
    public void iccIOForApp (int command, int fileid, String path, int p1, int p2,
            int p3, String data, String pin2, String aid, Message result) {
    }

    @Override
    public void getCLIR(Message result) {
    }

    @Override
    public void setCLIR(int clirMode, Message result) {
    }

    @Override
    public void queryCallWaiting(int serviceClass, Message response) {
    }

    @Override
    public void setCallWaiting(boolean enable, int serviceClass,
            Message response) {
    }

    @Override
    public void setNetworkSelectionModeAutomatic(Message response) {
    }

    @Override
    public void setNetworkSelectionModeManual(
            String operatorNumeric, Message response) {
    }

    @Override
    public void getNetworkSelectionMode(Message response) {
    }

    @Override
    public void getAvailableNetworks(Message response) {
    }

    @Override
    public void cancelAvailableNetworks(Message response) {
    }

    @Override
    public void setCallForward(int action, int cfReason, int serviceClass,
                String number, int timeSeconds, Message response) {
    }

    @Override
    public void queryCallForwardStatus(int cfReason, int serviceClass,
            String number, Message response) {
    }

    @Override
    public void queryCLIP(Message response) {
    }

    @Override
    public void getBasebandVersion (Message response) {
    }

    @Override
    public void queryFacilityLock(String facility, String password,
            int serviceClass, Message response) {
    }

    @Override
    public void queryFacilityLockForApp(String facility, String password,
            int serviceClass, String appId, Message response) {
    }

    @Override
    public void setFacilityLock(String facility, boolean lockState,
            String password, int serviceClass, Message response) {
    }

    @Override
    public void setFacilityLockForApp(String facility, boolean lockState,
            String password, int serviceClass, String appId, Message response) {
    }

    @Override
    public void sendUSSD (String ussdString, Message response) {
    }

    @Override
    public void cancelPendingUssd (Message response) {
    }

    @Override
    public void resetRadio(Message result) {
    }

    @Override
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
    }

    @Override
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
    }

    @Override
    public void setBandMode (int bandMode, Message response) {
    }

    @Override
    public void queryAvailableBandMode (Message response) {
    }

    @Override
    public void sendTerminalResponse(String contents, Message response) {
    }

    @Override
    public void sendEnvelope(String contents, Message response) {
    }

    @Override
    public void sendEnvelopeWithStatus(String contents, Message response) {
    }

    @Override
    public void handleCallSetupRequestFromSim(boolean accept, int resCode ,Message response) {
    }

    @Override
    public void setPreferredNetworkType(int networkType , Message response) {
    }

    @Override
    public void getPreferredNetworkType(Message response) {
    }

    @Override
    public void getNeighboringCids(Message response) {
    }

    @Override
    public void setLocationUpdates(boolean enable, Message response) {
    }

    @Override
    public void getSmscAddress(Message result) {
    }

    @Override
    public void setSmscAddress(String address, Message result) {
    }

    @Override
    public void reportSmsMemoryStatus(boolean available, Message result) {
    }

    @Override
    public void reportStkServiceIsRunning(Message result) {
    }

    @Override
    public void setStkEvdlCallByAP(int enabled, Message response) {
    }

    @Override
    public void getCdmaSubscriptionSource(Message response) {
    }

    @Override
    public void getGsmBroadcastConfig(Message response) {
    }

    @Override
    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response) {
    }

    @Override
    public void setGsmBroadcastActivation(boolean activate, Message response) {
    }

    // ***** Methods for CDMA support
    @Override
    public void getDeviceIdentity(Message response) {
    }

    @Override
    public void getCDMASubscription(Message response) {
    }

    @Override
    public void setPhoneType(int phoneType) { //Set by CDMAPhone and GSMPhone constructor
    }

    @Override
    public void queryCdmaRoamingPreference(Message response) {
    }

    @Override
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
    }

    @Override
    public void setCdmaSubscriptionSource(int cdmaSubscription , Message response) {
    }

    @Override
    public void queryTTYMode(Message response) {
    }

    @Override
    public void setTTYMode(int ttyMode, Message response) {
    }

    @Override
    public void sendCDMAFeatureCode(String FeatureCode, Message response) {
    }

    @Override
    public void getCdmaBroadcastConfig(Message response) {
    }

    @Override
    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response) {
    }

    @Override
    public void setCdmaBroadcastActivation(boolean activate, Message response) {
    }

    @Override
    public void exitEmergencyCallbackMode(Message response) {
    }

    @Override
    public void supplyIccPinForApp(String pin, String aid, Message response) {
    }

    @Override
    public void supplyIccPukForApp(String puk, String newPin, String aid, Message response) {
    }

    @Override
    public void supplyIccPin2ForApp(String pin2, String aid, Message response) {
    }

    @Override
    public void supplyIccPuk2ForApp(String puk2, String newPin2, String aid, Message response) {
    }

    @Override
    public void changeIccPinForApp(String oldPin, String newPin, String aidPtr, Message response) {
    }

    @Override
    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aidPtr,
            Message response) {
    }

    @Override
    public void requestIsimAuthentication(String nonce, Message response) {
    }

    // NFC SEEK start
    public void iccExchangeAPDU(int cla, int command, int channel, int p1,
            int p2, int p3, String data, Message response) {
    }

    public void iccOpenChannel(String AID, Message response) {
    }

    public void iccCloseChannel(int channel, Message response) {
    }

    public void iccGetATR(Message result) {
    }

    public void iccOpenChannelWithSw(String AID,Message result){
    }
    // NFC SEEK end

    @Override
    public void getVoiceRadioTechnology(Message result) {
    }

    @Override
    public void getCellInfoList(Message result) {
    }

    @Override
    public void setCellInfoListRate(int rateInMillis, Message response) {
    }
    
    @Override
    public void setInitialAttachApn(String apn, String protocol, int authType, String username,
            String password, Message result) {
    }

    /// M: Proprietary methods. @{
    //Add functions by MTK
    public void getSmsSimMemoryStatus(Message result) {
    }

    public void queryPhbStorageInfo(int type, Message response) {
    }
        
    public void ReadPhbEntry(int type, int bIndex, int eIndex, Message response) {        
    }
    
    public void writePhbEntry(PhbEntry entry, Message result) {
    }
    
    public void setScri(boolean forceRelease, Message response) {
    }
    
    //[New R8 modem FD]
    public void setFDMode(int mode, int parameter1, int parameter2, Message response) {
    
    }	
    	
    public void setupDataCall(String radioTechnology, String profile, String apn, String user,
            String password, String authType, String protocol, String requestCid, Message result) {
    }

    public void queryNetworkLock (int category, Message result){
    }
    
    public void setNetworkLock (int catagory, int lockop, String password,
        String data_imsi, String gid1, String gid2, Message result) {
    }
    
    public void getCOLP(Message response) {}
    
    public void setCOLP(boolean enable, Message response) {}
    
    public void getCOLR(Message response) {}

    public void setRadioPowerOff(Message result) {}

    public void setRadioMode(int mode, Message result) {}

    public void setGprsConnType(int mode, Message response) {}

    /**
     * set GPRS transfer type: data prefer/call prefer
     */
    public void setGprsTransferType(int mode, Message response) {}
    
    public void getMobileRevisionAndImei(int type,Message result) {}
    
    public void setPpuAndCurrency(String currency, String ppu, String pin2, Message result) {}	
    
    public void setAccumulatedCallMeterMaximum(String acmmax, String pin2, Message result) {}
    
    public void resetAccumulatedCallMeter(String pin2, Message result) {}
    
    public void hangupAll(Message result) {}
    
    public void hangupAllEx(Message result) {}
    
    public void getCurrentCallMeter(Message result){}
    
    public void getAccumulatedCallMeter(Message result) {}
    
    public void getAccumulatedCallMeterMaximum(Message result) {}
    
    public void getPpuAndCurrency(Message result){}

    public void changeBarringPassword(String facility, String oldPwd, String newPwd, String newCfm, Message result) {}  

    /* vt start  */
    public void vtDial (String address, int clirMode, Message result) {}

    public void vtDial (String address, int clirMode, UUSInfo uusInfo, Message result) {}
    
    public void acceptVtCallWithVoiceOnly(int callId, Message result) {}
    /* vt end */

    public void emergencyDial(String address, int clirMode, UUSInfo uusInfo, Message result) {
    }

    public void forceReleaseCall(int index, Message result) { }

    public void setCallIndication(int mode, int callId, int seqNumber, Message result) { }

    public void replaceVtCall(int index, Message result) {}

    public void get3GCapabilitySIM(Message response) {}

    public void set3GCapabilitySIM(int simId, Message response) {}

    public String lookupOperatorName(String numeric, boolean desireLongName) {
        return null;
    }
    // via support to add this API
    public void getNitzTime(Message result) {}

    public void requestSwitchHPF (boolean enableHPF, Message response){}
    public void setAvoidSYS (boolean avoidSYS, Message response) {}
    public void getAvoidSYSList (Message response) {}
    public void queryCDMANetworkInfo (Message response){}
    //[ALPS00389658]
    public void setMeid(String meid, Message response){}
    //Add by gfzhu VIA end

    //VIA-START VIA AGPS
    public void requestAGPSTcpConnected(int connected, Message result){    }
    public void requestAGPSSetMpcIpPort(String ip, String port, Message result){    }
    public void requestAGPSGetMpcIpPort(Message result){    }
    //VIA-END VIA AGPS
    //VIA-START SET ETS
    public void requestSetEtsDev(int dev, Message result){}
    //VIA-END SET ETS
    // VIA add for China Telecom auto-register sms
    public void queryCDMASmsAndPBStatus (Message response){}
    public void queryCDMANetWorkRegistrationState (Message response){}
    // VIA add end for China Telecom auto-register sms
    // via support to end
    //MTK-START [mtk80950][120410][ALPS00266631] check whether download calibration data or not
    public void getCalibrationData(Message result) {}
    //MTK-END [mtk80950][120410][ALPS00266631] check whether download calibration data or not

    public boolean isGettingAvailableNetworks() {return false;}

    public void setRegistrationSuspendEnabled(int enabled, Message response) {}
    public void setResumeRegistration(int sessionId, Message response) {}
    public void storeModemType(int modemType, Message response) {}
    public void queryModemType(Message response) {}
    /// @}

    //UTK start
    public void getUtkLocalInfo(Message response) {
    }
    
    public void requestUtkRefresh(int type, Message response) {
    }
    
    public void reportUtkServiceIsRunning(Message result) {
    }
    
    public void profileDownload(String profile, Message response) {
    }    

    public void handleCallSetupRequestFromUim(boolean accept, Message response) {
    }
    //UTK end

    //8.2.11 TC-IRLAB-02011 start
    public void enableCdmaRegisterPause(boolean enable, Message result){}
    public void resumeCdmaRegister(Message result){}
    //8.2.11 TC-IRLAB-02011 end
    
    public void setRadioPowerCardSwitch(int powerOn, Message response) {}
    public void setSimInterfaceSwitch(int switchMode, Message response) {}    
    public void setTelephonyMode(int slot1Mode, int slot2Mode, boolean stoered, Message response) {}  
    public void switchRilSocket(int preferredNetworkType, int simId) {}    
}
