/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.connectivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class CdsInfoReceiver extends BroadcastReceiver {
    private static final String TAG = "CdsInfoReceiver";
    private static final String BOOT = "boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(action != null) {
            Log.d(TAG, "action:" + action);
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || action.equals("android.intent.action.ACTION_BOOT_IPO")) {
            Intent i = new Intent(context, CdsInfoService.class);
            i.putExtra(BOOT, true);
            context.startService(i);
        } else if (action.equals("android.intent.action.ACTION_SHUTDOWN_IPO")) {
            Intent i = new Intent(context, CdsInfoService.class);
            i.putExtra(BOOT, false);
            context.startService(i);
        } else {
            Log.e(TAG, "Received unknown intent: " + action);
        }
    }

}