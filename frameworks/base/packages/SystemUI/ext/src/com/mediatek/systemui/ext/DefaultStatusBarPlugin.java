package com.mediatek.systemui.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;

/**
 * M: Default implementation of Plug-in definition of Status bar.
 */
public class DefaultStatusBarPlugin extends ContextWrapper implements IStatusBarPlugin {

    public DefaultStatusBarPlugin(Context context) {
        super(context);
    }
    
    public Resources getPluginResources() {
        return null;
    }

    public String getSignalStrengthDescription(int level) {
        return null;
    }

    public int getSignalStrengthNullIconGemini(int slotId) {
        return -1;
    }

    public int getSignalIndicatorIconGemini(int slotId) {
        return -1;
    }

    public int[] getDataTypeIconListGemini(boolean roaming, DataType dataType) {
        return null;
    }

    public boolean isHspaDataDistinguishable() {
        return true;
    }
    
    public int getDataNetworkTypeIconGemini(NetworkType networkType, int simColorId) {
        return -1;
    }

    public int[] getDataActivityIconList(int simColor, boolean showSimIndicator) {
        return null;
    }

    public int[] getWifiSignalStrengthIconList() {
        return null;
    }

    public int[] getWifiInOutIconList() {
        return null;
    }

    public boolean supportDataTypeAlwaysDisplayWhileOn() {
        return false;
    }

    public boolean supportDataConnectInTheFront() {
        return false;
    }

    public boolean supportDisableWifiAtAirplaneMode() {
        return false;
    }

    public boolean supportDisableBluetoothAtAirplaneMode() {
        return false;
    }

    public String get3gDisabledWarningString() {
        return null;
    }

    public boolean getMobileGroupVisible() {
        return false;
    }

    public boolean get3GPlusResources(boolean roaming, int dataType){
        return false;
    }

    public boolean showHspapDistinguishable(){
        return false;
    }
}
