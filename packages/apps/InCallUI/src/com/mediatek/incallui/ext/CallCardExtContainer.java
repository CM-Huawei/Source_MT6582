
package com.mediatek.incallui.ext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.view.View;

import com.android.incallui.Log;
import com.android.internal.telephony.PhoneConstants;
import com.android.services.telephony.common.Call;
import com.mediatek.incallui.ext.CallCardExtension;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

class CallCardExtContainer extends CallCardExtension {

    private List<CallCardExtension> mSubList = null;

    public CallCardExtContainer() {
        mSubList = new ArrayList<CallCardExtension>();
    }

    public void add(CallCardExtension item) {
        if (mSubList == null) {
            mSubList = new ArrayList<CallCardExtension>();
        }

        mSubList.add(item);
    }

    public void onViewCreated(Context context, View rootView) {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "onViewCreated, but no plug-ins.");
            return;
        }

        Log.w(this, "onViewCreated.");
        for (CallCardExtension item : mSubList) {
            item.onViewCreated(context, rootView);
        }
    }

    public void onStateChange(Call call) {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "setCallState, but no plug-ins.");
            return;
        }

        Log.d(this, "setCallState, call: " + call);
        for (CallCardExtension item : mSubList) {
            item.onStateChange(call);
        }
    }

    public boolean updateCallInfoLayout(PhoneConstants.State state) {
        if (null == mSubList) {
            Log.w(this, "updateCallInfoLayout(), sub extension list is null, just return");
            return false;
        }
        Log.w(this, "updateCallInfoLayout(), phone state is " + state);
        Iterator<CallCardExtension> iterator = mSubList.iterator();
        while (iterator.hasNext()) {
            CallCardExtension extension = iterator.next();
            if (extension.updateCallInfoLayout(state)) {
                return true;
            }
        }
        return false;
    }

    public void updatePrimaryDisplayInfo(Call call, SimInfoRecord simInfo) {
        if (null == mSubList) {
            Log.w(this, "displayMainCallStatus(), sub extension list is null, just return");
            return;
        }
        Log.w(this, "displayMainCallStatus(), call =" + call);
        Iterator<CallCardExtension> iterator = mSubList.iterator();
        while (iterator.hasNext()) {
            iterator.next().updatePrimaryDisplayInfo(call, simInfo);
        }
    }
}
