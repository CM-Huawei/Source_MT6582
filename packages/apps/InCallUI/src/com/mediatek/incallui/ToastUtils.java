
package com.mediatek.incallui;

import android.content.Context;

public class ToastUtils {
    private static ToastUtils sInstance = null;
    private Context mContext = null;

    private ToastUtils() {
    }

    public static ToastUtils getInstance() {
        if (sInstance == null) {
            sInstance = new ToastUtils();
        }
        return sInstance;
    }

    public void initContext(Context applicationContext) {
        mContext = applicationContext;
    }
}
