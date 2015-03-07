
package com.mediatek.incallui.ext;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.View;

import com.android.incallui.Log;
import com.android.services.telephony.common.Call;

import java.util.HashMap;

public class CallButtonExtContainer extends CallButtonExtension {

    private List<CallButtonExtension> mSubList = null;

    public CallButtonExtContainer() {
        mSubList = new ArrayList<CallButtonExtension>();
    }

    public void add(CallButtonExtension item) {
        if (mSubList == null) {
            mSubList = new ArrayList<CallButtonExtension>();
        }

        mSubList.add(item);
    }

    public void onViewCreated(Context context, View rootView) {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "onViewCreated, but no plug-ins.");
            return;
        }

        Log.w(this, "onViewCreated.");
        for (CallButtonExtension item : mSubList) {
            item.onViewCreated(context, rootView);
        }
    }

    public void onStateChange(Call call, HashMap<Integer, Call> callMap) {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "setCallState, but no plug-ins.");
            return;
        }

        Log.d(this, "setCallState, call: " + call);
        for (CallButtonExtension item : mSubList) {
            item.onStateChange(call, callMap);
        }
    }
}
