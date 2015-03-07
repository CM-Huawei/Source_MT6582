
package com.mediatek.phone;

import com.mediatek.xlog.Xlog;

import android.os.Trace;
import android.util.Log;

public final class PhoneLog {
    private static final String TAG = "Phone";

    /**
     * For test app launch time.
     */
    private final static String TRACE_APP_UPDATE = "AppUpdate";

    private static final boolean DEBUG = true;
    private static final boolean XLOG = true;

    private PhoneLog() {
    }

    public static void v(String tag, String msg) {
        if (DEBUG) {
            if (XLOG) {
                Xlog.v(TAG, tag + "/" + msg);
            } else {
                Log.v(TAG, tag + "/" + msg);
            }
        }
    }

    public static void d(String tag, String msg) {
        if (DEBUG) {
            if (XLOG) {
                Xlog.d(TAG, tag + "/" + msg);
            } else {
                Log.d(TAG, tag + "/" + msg);
            }
        }
    }

    public static void i(String tag, String msg) {
        if (DEBUG) {
            if (XLOG) {
                Xlog.i(TAG, tag + "/" + msg);
            } else {
                Log.i(TAG, tag + "/" + msg);
            }
        }
    }

    public static void e(String tag, String msg) {
        if (DEBUG) {
            if (XLOG) {
                Xlog.e(TAG, tag + "/" + msg);
            } else {
                Log.e(TAG, tag + "/" + msg);
            }
        }
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (DEBUG) {
            if (XLOG) {
                Xlog.e(tag, msg + "/", tr);
            } else {
                Log.e(TAG, tag + "/" + msg, tr);
            }
        }
    }

    public static void w(String tag, String msg) {
        if (DEBUG) {
            if (XLOG) {
                Xlog.w(TAG, tag + "/" + msg);
            } else {
                Log.w(TAG, tag + "/" + msg);
            }
        }
    }

    /**
     * Performance method: The method is used for trace activity launch time.
     *
     * @param duration
     */
    public static void trace(int duration) {
        Trace.traceCounter(Trace.TRACE_TAG_GRAPHICS, TRACE_APP_UPDATE, duration);
    }
}