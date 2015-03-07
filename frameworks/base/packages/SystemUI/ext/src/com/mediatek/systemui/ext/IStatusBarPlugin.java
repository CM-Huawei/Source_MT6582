package com.mediatek.systemui.ext;

import android.content.res.Resources;

/**
 * M: the interface for Plug-in definition of Status bar.
 */
public interface IStatusBarPlugin {

    /** Signal strength interfaces. @{ */

    /**
     * Get the description string of signal strength according to the level.
     * 
     * @param level The signal level value.
     * @return The description string of signal strength.
     */
    String getSignalStrengthDescription(int level);

    /**
     * Get the signal strength icon when has not service or null.
     * 
     * @param slotId The slot index for GEMINI.
     * @return The signal strength icon when has not service or null.
     */
    int getSignalStrengthNullIconGemini(int slotId);

    /**
     * Get the signal indicator icon.
     * 
     * @param slotId The slot index for GEMINI.
     * @return The signal indicator icon.
     */
    int getSignalIndicatorIconGemini(int slotId);

    /** Signal strength interfaces. @} */

    /** Data connection interfaces. @{ */

    /**
     * Get the data connection type icon list.
     * 
     * @param roaming Whether at roaming state.
     * @param dataType The data connection type.
     * @return The data connection type icon list.
     */
    int[] getDataTypeIconListGemini(boolean roaming, DataType dataType);

    /**
     * Get the data connection type for HSPA.
     * 
     * @return Whether he data connection type 3G instead of H.
     */
    boolean isHspaDataDistinguishable();
    
    /**
     * Get the data connection network type icon.
     * 
     * @param networkType The network type.
     * @param simColorId The SIM color index of GEMINI.
     * @return The data connection network type icon.
     */
    int getDataNetworkTypeIconGemini(NetworkType networkType, int simColorId);

    /**
     * @param simColor The color of SIM.
     * @param showSimIndicator whether show SIM indicator or not.
     * @return Return the data connection activity icon list.
     */
    int[] getDataActivityIconList(int simColor, boolean showSimIndicator);

    /**
     * @return Return if data type icon always display once opened.
     */
    boolean supportDataTypeAlwaysDisplayWhileOn();

    /**
     * @return Return whether show data connect in the front.
     */
    boolean supportDataConnectInTheFront();

    /** Data connection interfaces. @} */

    /** WIFI interfaces. @{ */

    /**
     * @return Return WIFI signal strength icon list.
     */
    int[] getWifiSignalStrengthIconList();

    /**
     * @return Return WIFI signal strength icon list.
     */
    int[] getWifiInOutIconList();

    /**
     * @return Return if disable WIFI when at airplane mode.
     */
    boolean supportDisableWifiAtAirplaneMode();

    /** WIFI interfaces. @} */

    /** Bluetooth interfaces. @{ */

    /**
     * @return Return if disable Bluetooth when at airplane mode.
     */
    boolean supportDisableBluetoothAtAirplaneMode();

    /** Bluetooth interfaces. @} */

    /** Resource interfaces. @{ */

    /**
     * @return Return the resources object of plug-in package.
     */
    Resources getPluginResources();

    /**
     * @return Return the string resource of 3g disabled warning.
     */
    String get3gDisabledWarningString();

    /** Resource interfaces. @} */

    /**
     * Get the mobile group should visible
     * 
     * @return true if mobile group should show
     */
    boolean getMobileGroupVisible();

    /**
     * Get the mobile group should visible
     * 
     * @return icon id for 3g+
     */
    public boolean get3GPlusResources(boolean roaming, int dataType);

   /**
     * Get that 4g should be shown for 3g network
     * UMTS/HSPA/HSDPA/HSUPA/HSPAP
     * @return true if mobile group should show
     */

    boolean showHspapDistinguishable();
}
