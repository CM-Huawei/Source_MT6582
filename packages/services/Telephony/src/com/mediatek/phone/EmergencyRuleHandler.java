package com.mediatek.phone;

import java.util.List;
import java.util.ArrayList;
import android.util.Log;
import com.android.phone.PhoneGlobals;

import android.provider.Telephony.SIMInfo;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.PhoneConstants;
import com.mediatek.phone.wrapper.TelephonyManagerWrapper;


public class EmergencyRuleHandler {
    static final String TAG = "EmergencyRuleHandler";
    static final boolean DBG = true;
    public static final int ECC_INVALIDE_SLOT = -1;
    public static final int ECC_SLOT_1 = 0;
    public static final int ECC_SLOT_2 = 1;
    
    private int mPreSlot = -1;
    
    List<SIMInfo> mSimList;
    List<RuleHandler> mRuleList;
    private String mNumber;
    
    void log(String s) {
        Log.d(TAG, s);
    }
    
    /**
     * The common interface for ECC rule
     *
     */
    public interface RuleHandler {
        public int handleRequest(String number);
    }
    
    public EmergencyRuleHandler(String number) {
        mNumber = number;
        mSimList = SIMInfo.getInsertedSIMList(PhoneGlobals.getInstance());
    }
    
    private void generateHandlerList() {
        if (mRuleList != null) {
            mRuleList.clear();
        }
        
        mRuleList = new ArrayList<RuleHandler>();
        if (mSimList != null && mSimList.size() == 0) {
            mRuleList.add(new GCNoSimRule());
        } else if (mSimList.size() == 1) {
            mRuleList.add(new GSMOnlyRule());
            mRuleList.add(new CDMAOnlyRule());
        } else if (mSimList.size() == 2) {
            mRuleList.add(new CdmaAndGsmLocked());
            mRuleList.add(new CdmaLockedAndGsmReady());
            mRuleList.add(new CdmaReadyAndGsmLocked());
            mRuleList.add(new CdmaAndGsmReady());
        }
        
        mRuleList.add(new DefaultHandler());
    }
    
    private void handleRequest() {
        for (RuleHandler rule : mRuleList) {
            int slot = rule.handleRequest(mNumber);
            if (ECC_INVALIDE_SLOT != slot) {
                mPreSlot = slot;
                log("handleRequest find prefered slot = " + mPreSlot);
                break;
            }
        }
    }
    
    public int getPreferedSlot() {
        generateHandlerList();
        handleRequest();
        return mPreSlot;
    }
    
    /**
     * DualTalk G+C no sim insert rule
     *
     */
    class GCNoSimRule implements RuleHandler {
        
        public int handleRequest(String number) {
            if (mSimList == null || mSimList.size() > 0) {
                log("GCNoSimRule: there are/is sim insert.");
                return ECC_INVALIDE_SLOT;
            }
            
            //According to the spec: when no sim insert, 120/110/119 dialed out from cdma
            if ("120".equals(number) || ("110".equals(number)) || "119".equals(number)) {
                log("met '120/110/119' when no sim, so dial from cdma.");
                return getProperSlot(PhoneConstants.PHONE_TYPE_CDMA);
            }
            log("met numbers not in '120/110/119' when no sim, so dial from gsm.");
            return getProperSlot(PhoneConstants.PHONE_TYPE_GSM);
        }
    }
    
    class GSMOnlyRule implements RuleHandler {
        public int handleRequest(String number) {
            log("GSMOnlyRule: handleRequest...");
            if (mSimList == null || mSimList.size() != 1) {
                return ECC_INVALIDE_SLOT;
            }
            int slot = mSimList.get(0).mSlot;
            if (slot != getProperSlot(PhoneConstants.PHONE_TYPE_GSM)) {
                return ECC_INVALIDE_SLOT;
            }
            int state = TelephonyManagerWrapper.getSimState(slot);
            log("GSMOnlyRule: simState = " + simStateToString(state));
            
            if (state == TelephonyManager.SIM_STATE_PIN_REQUIRED 
                    || state == TelephonyManager.SIM_STATE_PUK_REQUIRED 
                    || state == TelephonyManager.SIM_STATE_NETWORK_LOCKED
                    || state == TelephonyManager.SIM_STATE_UNKNOWN) {
                if ("112".equals(number) || "911".equals(number)) {
                    return getProperSlot(PhoneConstants.PHONE_TYPE_GSM);
                } else {
                    return getProperSlot(PhoneConstants.PHONE_TYPE_CDMA);
                }
            } else if (state == TelephonyManager.SIM_STATE_READY) {
                return getProperSlot(PhoneConstants.PHONE_TYPE_GSM);
            }
            
            return ECC_INVALIDE_SLOT;
        }
    }
    
    class CDMAOnlyRule implements RuleHandler {
        
        public int handleRequest(String number) {
            log("CDMAOnlyRule: handleRequest...");
            if (mSimList == null || mSimList.size() != 1) {
                return ECC_INVALIDE_SLOT;
            }
            int slot = mSimList.get(0).mSlot;
            //For c+g, the ECC_SLOT_2 means cdma only
            if (slot != getProperSlot(PhoneConstants.PHONE_TYPE_CDMA)) {
                return ECC_INVALIDE_SLOT;
            }
            
            int state = TelephonyManagerWrapper.getSimState(slot);
            log("CDMAOnlyRule: simState = " + simStateToString(state));
            
            //CDMA registered to network, so place every ecc via CDMA
            if (state == TelephonyManager.SIM_STATE_READY) {
                return getProperSlot(PhoneConstants.PHONE_TYPE_CDMA);
            } else if ("120".equals(number) || ("110".equals(number)) || "119".equals(number)
                    || "122".equals(number)) {
                    //Add the 122 by requested from ALPS00476621
                    return getProperSlot(PhoneConstants.PHONE_TYPE_CDMA);
            } else if ("112".equals(number) || "911".equals(number) || "999".equals(number)) {
                //112,911,999
                //Other numbers will handled by other handles 
                return getProperSlot(PhoneConstants.PHONE_TYPE_GSM);
            }
            
            return ECC_INVALIDE_SLOT;
        }
    }
    
    class CdmaAndGsmLocked implements RuleHandler {
        
        public int handleRequest(String number) {
            log("CdmaAndGsmLocked: handleRequest...");
            if (mSimList == null || mSimList.size() != 2) {
                return ECC_INVALIDE_SLOT;
            }
            
            int gsmState = TelephonyManagerWrapper.getSimState(getProperSlot(PhoneConstants.PHONE_TYPE_GSM));
            int cdmaState = TelephonyManagerWrapper.getSimState(getProperSlot(PhoneConstants.PHONE_TYPE_CDMA));
            log("CdmaAndGsmLocked: gsmState = " + simStateToString(gsmState));
            log("CdmaAndGsmLocked: cdmaState = " + simStateToString(cdmaState));
            
            if (gsmState == TelephonyManager.SIM_STATE_READY || cdmaState == TelephonyManager.SIM_STATE_READY) {
                return ECC_INVALIDE_SLOT;
            } else if ("120".equals(number) || "110".equals(number) || "119".equals(number) || "999".equals(number)) {
                return getProperSlot(PhoneConstants.PHONE_TYPE_CDMA);
            } else if ("112".equals(number) || "911".equals(number)) {
                return getProperSlot(PhoneConstants.PHONE_TYPE_GSM);
            }
            
            return ECC_INVALIDE_SLOT;
        }
    }
    
    class CdmaLockedAndGsmReady implements RuleHandler {
        
        public int handleRequest(String number) {
            log("CdmaLockedAndGsmReady: handleRequest...");
            if (mSimList == null || mSimList.size() != 2) {
                return ECC_INVALIDE_SLOT;
            }
            
            int gsmState = TelephonyManagerWrapper.getSimState(getProperSlot(PhoneConstants.PHONE_TYPE_GSM));
            int cdmaState = TelephonyManagerWrapper.getSimState(getProperSlot(PhoneConstants.PHONE_TYPE_CDMA));
            log("CdmaLockedAndGsmReady: gsmState = " + simStateToString(gsmState));
            log("CdmaLockedAndGsmReady: cdmaState = " + simStateToString(cdmaState));
            
            if (gsmState != TelephonyManager.SIM_STATE_READY || cdmaState == TelephonyManager.SIM_STATE_READY) {
                return ECC_INVALIDE_SLOT;
            }
            
            if (gsmState == TelephonyManager.SIM_STATE_READY) {
                return getProperSlot(PhoneConstants.PHONE_TYPE_GSM);
            }
            
            return ECC_INVALIDE_SLOT;
        }
    }
    
    class CdmaReadyAndGsmLocked implements RuleHandler {
        
        public int handleRequest(String number) {
            log("CdmaReadyAndGsmLocked: handleRequest...");
            if (mSimList == null || mSimList.size() != 2) {
                return ECC_INVALIDE_SLOT;
            }
            
            int gsmState = TelephonyManagerWrapper.getSimState(getProperSlot(PhoneConstants.PHONE_TYPE_GSM));
            int cdmaState = TelephonyManagerWrapper.getSimState(getProperSlot(PhoneConstants.PHONE_TYPE_CDMA));
            log("CdmaReadyAndGsmLocked: gsmState = " + simStateToString(gsmState));
            log("CdmaReadyAndGsmLocked: cdmaState = " + simStateToString(cdmaState));
            
            if (cdmaState != TelephonyManager.SIM_STATE_READY || gsmState == TelephonyManager.SIM_STATE_READY) {
                return ECC_INVALIDE_SLOT;
            }
            
            if (cdmaState == TelephonyManager.SIM_STATE_READY) {
                return getProperSlot(PhoneConstants.PHONE_TYPE_CDMA);
            }
            
            return ECC_INVALIDE_SLOT;
        }
    }
    
    class CdmaAndGsmReady implements RuleHandler {
        
        public int handleRequest(String number) {
            log("CdmaAndGsmReady: handleRequest...");
            if (mSimList == null || mSimList.size() != 2) {
                return ECC_INVALIDE_SLOT;
            }
            
            int gsmState = TelephonyManagerWrapper.getSimState(getProperSlot(PhoneConstants.PHONE_TYPE_GSM));
            int cdmaState = TelephonyManagerWrapper.getSimState(getProperSlot(PhoneConstants.PHONE_TYPE_CDMA));
            log("CdmaAndGsmReady: gsmState = " + simStateToString(gsmState));
            log("CdmaAndGsmReady: cdmaState = " + simStateToString(cdmaState));
            
            if (gsmState == TelephonyManager.SIM_STATE_READY && cdmaState == TelephonyManager.SIM_STATE_READY) {
                return getProperSlot(PhoneConstants.PHONE_TYPE_CDMA);
            }
            
            return ECC_INVALIDE_SLOT;
        }
    }
    
    class DefaultHandler implements RuleHandler {
        public int handleRequest(String number) {
            log("Can't got here! something is wrong!");
            return getProperSlot(PhoneConstants.PHONE_TYPE_GSM);
        }
    }
    
    String simStateToString(int state) {
        String s = null;
        if (state < TelephonyManager.SIM_STATE_UNKNOWN
                || state > TelephonyManager.SIM_STATE_READY) {
            log("simStateToString: invalid state = " + state);
            s = "INVALIDE_STATE";
        }
        
        switch (state) {
            case TelephonyManager.SIM_STATE_UNKNOWN:
                s = "SIM_STATE_UNKNOWN";
                break;
                
            case TelephonyManager.SIM_STATE_ABSENT:
                s = "SIM_STATE_ABSENT";
                break;
                
            case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                s = "SIM_STATE_PIN_REQUIRED";
                break;
                
            case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                s = "SIM_STATE_PUK_REQUIRED";
                break;
                
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                s = "SIM_STATE_NETWORK_LOCKED";
                break;
                
            case TelephonyManager.SIM_STATE_READY:
                s = "SIM_STATE_READY";
                break;
        }
        
        return s;
    }
    
    int getProperSlot(int radioType) {
        int slot = ECC_INVALIDE_SLOT;
        if (radioType == PhoneConstants.PHONE_TYPE_CDMA) {
            if (TelephonyManagerWrapper.getPhoneType(ECC_SLOT_1) == PhoneConstants.PHONE_TYPE_CDMA) {
                slot = ECC_SLOT_1;
            } else if (TelephonyManagerWrapper.getPhoneType(ECC_SLOT_2) == PhoneConstants.PHONE_TYPE_CDMA) {
                slot = ECC_SLOT_2;
            }
        } else if (radioType == PhoneConstants.PHONE_TYPE_GSM) {
            if (TelephonyManagerWrapper.getPhoneType(ECC_SLOT_1) == PhoneConstants.PHONE_TYPE_GSM) {
                slot = ECC_SLOT_1;
            } else if (TelephonyManagerWrapper.getPhoneType(ECC_SLOT_2) == PhoneConstants.PHONE_TYPE_GSM) {
                slot = ECC_SLOT_2;
            }
        }
        log("getProperSlot with radioType = " + radioType + " and return slot = " + slot);
        return slot;
    }
}
