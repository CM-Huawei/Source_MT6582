package com.mediatek.settings;

import android.content.Context;
import android.content.Intent;

import com.android.internal.telephony.PhoneConstants;

import com.mediatek.CellConnService.CellConnMgr;
import com.mediatek.phone.wrapper.ITelephonyWrapper;
import com.mediatek.xlog.Xlog;

public class PreCheckForRunning {
    private CellConnMgr mCellConnMgr;
    private ServiceComplete mServiceComplete;
    private Context mContext;
    private Intent mIntent;
    private static final String TAG = "Settings/PreCheckForRunning";
    public boolean mByPass = false;
    public static final int PIN1_REQUEST_CODE = 302;
    
    public PreCheckForRunning(Context ctx) {
        mContext = ctx;
        mServiceComplete = new ServiceComplete();
        mCellConnMgr = new CellConnMgr(mServiceComplete);
        mCellConnMgr.register(mContext.getApplicationContext());
    }
    class ServiceComplete implements Runnable {
        public void run() {
            int result = mCellConnMgr.getResult();
            Xlog.d(TAG, "ServiceComplete with the result = " + CellConnMgr.resultToString(result));
            if (mIntent != null &&
                    (CellConnMgr.RESULT_OK == result || CellConnMgr.RESULT_STATE_NORMAL == result)) {
                mContext.startActivity(mIntent);
            }
        }
    }

    public void checkToRun(Intent intent, int slotId, int req) {
        if (mByPass) {
            mContext.startActivity(intent);
            return ;
        }
        unLock(intent, slotId, req);
    }

    public void unLock(Intent intent, int slotId, int req) {
        setIntent(intent);
        int r = mCellConnMgr.handleCellConn(slotId, req);
        Xlog.d(TAG, "The result of handleCellConn = " + CellConnMgr.resultToString(r));
    }
    
    public boolean isSimLocked(int slot) {
        return ITelephonyWrapper.getSimIndicatorState(slot) == PhoneConstants.SIM_INDICATOR_LOCKED;
    }

    public void setIntent(Intent it) {
        mIntent = it;
    }

    public void deRegister() {
        mCellConnMgr.unregister();
    }
}
