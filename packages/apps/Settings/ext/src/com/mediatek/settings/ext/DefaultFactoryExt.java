package com.mediatek.settings.ext;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.app.Activity;

import android.content.Intent;
import com.android.internal.os.storage.ExternalStorageFormatter;
import java.util.List;

public class DefaultFactoryExt implements IFactoryExt {
    private static final String MASTER_CLEAR = "android.intent.action.MASTER_CLEAR";

    public DefaultFactoryExt(Context context) {
    }

    @Override
    public int getCheckBoxStatus() {
        return 0;
    }

    @Override
	public void setLayout(List<View> lists) {
    }

    @Override
    public boolean onClick(int eraseInternalData) {
        return true;
    }

    @Override
    public void updateContentViewLayout(ViewGroup container, int siberViewId) {
    }

    /**
     * CT Factory reset feature refactory
     * @param activity which will startService or sendBroadcast
     * @param eraseInternalData: data | app | media
     * @param eraseSdCard: use in DefaultFactoryExt.java
     */
    @Override
    public void onResetPhone(Activity activity, int eraseInternalData, boolean eraseSdCard) {
        if (eraseSdCard) {
            Intent intent = new Intent(ExternalStorageFormatter.FORMAT_AND_FACTORY_RESET);
            intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
            activity.startService(intent);
        } else {
            activity.sendBroadcast(new Intent(MASTER_CLEAR));
        }
    }
}
