package com.mediatek.keyguard.ext;

import android.content.Context;
import android.view.View;

/**
 * Interface that used for dual clock plug in feature
 * {@hide}
 */
public interface IDualClock {
    /**
     * create domestic clock
     */
    View createClockView(Context context, View container); 

    /**
     * get the status view layout
     */
    int getStatusViewLayout();

    /**
     * Reset the phone listener.
     */
    void resetPhonelistener();

    /**
     * The domestic clock view's last line text need to align to
     * the phone setting clock time whose text is large and baseline is big.
     * Update the domestic clock's layout to align.
     */
    void updateClockLayout();
}
