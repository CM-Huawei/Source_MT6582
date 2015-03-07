
package com.mediatek.incallui.ext;

import android.content.Context;
import android.view.View;

import com.android.services.telephony.common.Call;

import com.android.internal.telephony.PhoneConstants;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

public class CallCardExtension {

    public void onViewCreated(Context context, View rootView) {
        InCallUIPluginDefault.log("CallCardExtension onViewCreated DEFAULT");
    }

    public void onStateChange(Call call) {
        InCallUIPluginDefault.log("CallCardExtension onStateChange DEFAULT");
    }

    public boolean updateCallInfoLayout(PhoneConstants.State state) {
        return false;
    }

    public void updatePrimaryDisplayInfo(Call call, SimInfoRecord simInfo) {
    }
}
