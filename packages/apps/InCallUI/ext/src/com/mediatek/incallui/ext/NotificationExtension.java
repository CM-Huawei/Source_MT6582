
package com.mediatek.incallui.ext;

import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;

import com.android.services.telephony.common.Call;

public class NotificationExtension extends ContextWrapper {

    public NotificationExtension() {
        super(null);
    }

    public NotificationExtension(Context base) {
        super(base);
    }

    /**
     * @param voicePrivacy "voice privacy" mode is active for always show
     *            notification
     * @param defValue
     * @return
     */
    public boolean shouldSuppressNotification(boolean defValue) {
        return defValue;
    }

    /**
     * @param call
     * @param voicePrivacy "voice privacy" mode is active for always show
     *            notification
     * @param defResId
     * @param pluginResIds
     * @return
     */
    public int getInCallResId(Call call, boolean voicePrivacy, int defResId, int[][] pluginResIds) {
        return defResId;
    }
}
