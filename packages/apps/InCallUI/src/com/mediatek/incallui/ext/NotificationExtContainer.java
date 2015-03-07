
package com.mediatek.incallui.ext;

import android.content.Context;

import java.util.Iterator;
import java.util.LinkedList;

import com.android.incallui.Log;
import com.android.services.telephony.common.Call;

public class NotificationExtContainer extends NotificationExtension {
    private static final String TAG = "NotificationExtContainer";

    private LinkedList<NotificationExtension> mSubExtensionList;

    /**
     * @param extension
     */
    public void add(NotificationExtension extension) {
        if (null == mSubExtensionList) {
            Log.d(TAG, "create sub extension list");
            mSubExtensionList = new LinkedList<NotificationExtension>();
        }

        Log.d(TAG, "add extension, extension is " + extension);
        mSubExtensionList.add(extension);
    }

    /**
     * @param extension SettingsExtension
     */
    public void remove(NotificationExtension extension) {
        if (null == mSubExtensionList) {
            Log.d(TAG, "remove extension, sub extension list is null, just return");
            return;
        }

        Log.d(TAG, "remove extension, extension is " + extension);
        mSubExtensionList.remove(extension);
    }

    public boolean shouldSuppressNotification(boolean hostDefaultValue) {
        if (null == mSubExtensionList) {
            Log.d(TAG, "shouldSuppressNotification(), sub extension list is null");
            return hostDefaultValue;
        }

        Iterator<NotificationExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            NotificationExtension extension = iterator.next();
            final boolean suppressNotification = extension
                    .shouldSuppressNotification(hostDefaultValue);
            if (suppressNotification != hostDefaultValue) {
                Log.d(TAG, "shouldSuppressNotification(), plug-in:" + suppressNotification);
                return suppressNotification;
            }
        }
        Log.d(TAG, "shouldSuppressNotification(), default:" + hostDefaultValue);
        return hostDefaultValue;
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
        if (null == mSubExtensionList) {
            Log.d(TAG, "getInCallResId(), sub extension list is null");
            return defResId;
        }

        Iterator<NotificationExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            NotificationExtension extension = iterator.next();
            final int pluginResId = extension.getInCallResId(call, voicePrivacy, defResId, pluginResIds);
            if (pluginResId != defResId) {
                Log.d(TAG, "getInCallResId(), plug-in:" + pluginResId);
                return pluginResId;
            }
        }
        Log.d(TAG, "getInCallResId(), default:" + defResId);
        return defResId;
    }
}
