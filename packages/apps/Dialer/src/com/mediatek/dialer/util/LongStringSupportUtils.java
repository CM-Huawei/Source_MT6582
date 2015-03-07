package com.mediatek.dialer.util;

import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

public class LongStringSupportUtils {
    
    private final static String TAG = "LongStringSupportUtils";
    
    public static void setSupportSingleLineLongString(TextView textView) {
        if (textView == null) {
            Log.i(TAG, "setSupportSingleLineLongString textView is null");
            return;
        }
        textView.setSingleLine(true);
        textView.setEllipsize(null);
        textView.setHorizontalFadingEdgeEnabled(true);
    }

}
