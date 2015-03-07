
package com.mediatek.incallui.ext;

import java.util.HashMap;
import android.content.Context;
import android.view.View;

import com.android.services.telephony.common.Call;

public class CallButtonExtension {

    public void onViewCreated(Context context, View rootView) {
        InCallUIPluginDefault.log("CallButtonExtension onViewCreated DEFAULT");
    }

    public void onStateChange(Call call, HashMap<Integer, Call> callMap) {
        InCallUIPluginDefault.log("CallButtonExtension onStateChange DEFAULT");
    }
}
