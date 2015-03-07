package com.mediatek.keyguard.ext;

import android.content.Context;

import com.mediatek.pluginmanager.PluginManager;
import com.mediatek.pluginmanager.Plugin;
import com.mediatek.pluginmanager.Plugin.ObjectCreationException;
import com.mediatek.xlog.Xlog;

/**
 * M: Plug-in helper class as the facade for accessing related add-ons.
 */
public class KeyguardPluginFactory {
    private static final String TAG = "KeyguardPluginFactory";

    public static synchronized ICardInfoExt getCardInfoExt(Context context) {
        ICardInfoExt cardInfoExt;
        try {
            cardInfoExt = (ICardInfoExt) PluginManager.createPluginObject(
                    context, ICardInfoExt.class.getName(), "1.0.0", Plugin.DEFAULT_HANDLER_NAME);
            Xlog.d(TAG, "getCardInfoExt cardInfoExt= "+ cardInfoExt);
        } catch (ObjectCreationException e) {
            cardInfoExt = new DefaultCardInfoExt();
            Xlog.d(TAG, "getCardInfoExt get DefaultCardInfoExt = "+ cardInfoExt);
        }

        return cardInfoExt;
    }

    public static synchronized IDualClock getDualClock(Context context) {
        IDualClock dualClock;
        try {
            dualClock = (IDualClock) PluginManager.createPluginObject(
                    context, IDualClock.class.getName(), "1.0.0", Plugin.DEFAULT_HANDLER_NAME);
            Xlog.d(TAG, "getDualClock dualClock= "+ dualClock);
        } catch (ObjectCreationException e) {
            dualClock = new DefaultDualClock();
            Xlog.d(TAG, "getDualClock get DefaultDualClock = "+ dualClock);
        }

        return dualClock;
    }

    public static synchronized IEmergencyButtonExt getEmergencyButtonExt(Context context) {
        IEmergencyButtonExt emergencyButtonExt;
        try {
            emergencyButtonExt = (IEmergencyButtonExt) PluginManager.createPluginObject(
                    context, IEmergencyButtonExt.class.getName(), "1.0.0", Plugin.DEFAULT_HANDLER_NAME);
            Xlog.d(TAG, "getEmergencyButtonExt emergencyButtonExt= "+ emergencyButtonExt);
        } catch (ObjectCreationException e) {
            emergencyButtonExt = new DefaultEmergencyButtonExt();
            Xlog.d(TAG, "getEmergencyButtonExt get DefaultEmergencyButtonExt = "+ emergencyButtonExt);
        }

        return emergencyButtonExt;
    }

    public static synchronized ICarrierTextExt getCarrierTextExt(Context context) {
        ICarrierTextExt carrierTextExt;
        try {
            carrierTextExt = (ICarrierTextExt) PluginManager.createPluginObject(
                    context, ICarrierTextExt.class.getName(), "1.0.0", Plugin.DEFAULT_HANDLER_NAME);
            Xlog.d(TAG, "getCarrierTextExt carrierTextExt= "+ carrierTextExt);
        } catch (ObjectCreationException e) {
            carrierTextExt = new DefaultCarrierTextExt();
            Xlog.d(TAG, "getCarrierTextExt get DefaultCarrierTextExt = "+ carrierTextExt);
        }

        return carrierTextExt;
    }

    public static synchronized IKeyguardUtilExt getKeyguardUtilExt(Context context) {
        IKeyguardUtilExt keyguardUtilExt;
        try {
            keyguardUtilExt = (IKeyguardUtilExt) PluginManager.createPluginObject(
                    context, IKeyguardUtilExt.class.getName(), "1.0.0", Plugin.DEFAULT_HANDLER_NAME);
            Xlog.d(TAG, "getKeyguardUtilExt keyguardUtilExt= "+ keyguardUtilExt);
        } catch (ObjectCreationException e) {
            keyguardUtilExt = new DefaultKeyguardUtilExt();
            Xlog.d(TAG, "getKeyguardUtilExt get DefaultKeyguardUtilExt = "+ keyguardUtilExt);
        }

        return keyguardUtilExt;
    }

    public static synchronized IOperatorSIMString getOperatorSIMString(Context context) {
        IOperatorSIMString operatorSIMString;
        try {
            operatorSIMString = (IOperatorSIMString) PluginManager.createPluginObject(
                    context, IOperatorSIMString.class.getName(), "1.0.0", Plugin.DEFAULT_HANDLER_NAME);
            Xlog.d(TAG, "getOperatorSIMString operatorSIMString= "+ operatorSIMString);
        } catch (ObjectCreationException e) {
            operatorSIMString = new DefaultOperatorSIMString();
            Xlog.d(TAG, "getOperatorSIMString get DefaultOperatorSIMString = "+ operatorSIMString);
        }

        return operatorSIMString;
    }

    public static synchronized ILockScreenExt getLockScreenExt(Context context) {
        ILockScreenExt lockScreenExt;
        try {
            lockScreenExt = (ILockScreenExt) PluginManager.createPluginObject(
                    context, ILockScreenExt.class.getName(), "1.0.0", Plugin.DEFAULT_HANDLER_NAME);
            Xlog.d(TAG, "getLockScreenExt lockScreenExt= "+ lockScreenExt);
        } catch (ObjectCreationException e) {
            lockScreenExt = new DefaultLockScreenExt();
            Xlog.d(TAG, "getLockScreenExt get DefaultLockScreenExt = "+ lockScreenExt);
        }

        return lockScreenExt;
    }
}
