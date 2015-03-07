package com.mediatek.settings.ext;


public class DefaultRcseOnlyApnExt implements IRcseOnlyApnExtension {
    
    /** is show the rcse type apn profile or not.
     * @return true show.
     * @return false hide.
     */
    public boolean isRcseOnlyApnEnabled() {
        return false;
    }

    /** to call back when the rcse state changed.
     * @return true show.
     * @return false hide.
     */

    /** add a call back listener.
     * @param listener the call back to add
     */
    public void addRcseOnlyApnStateChanged(OnRcseOnlyApnStateChangedListener listener) {
    }

    /** remove a call back listener.
     * @param listener the call back to remove 
     */
    public void removeRcseOnlyApnStateChanged(OnRcseOnlyApnStateChangedListener listener) {
    }
}
