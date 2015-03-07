/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.badservicesysserver;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

public class BadService extends Service {
    final Binder mBinder = new Binder();
    private static final String TAG = "ANR_DEBUG";

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
         // TODO Auto-generated method stub
         Log.i(TAG, "Badservice on SystemServer is starting -- about to hang");
         Log.i(TAG, "Process ID:" + Process.myPid()+ " Thread ID: " + Process.myTid());
         try { 
                 Thread.sleep(25*1000);
         } 
         catch (InterruptedException e) {
             Log.wtf(TAG, e);
         }
         Log.i(TAG, "service hang finished -- stopping and returning");
         stopSelf();
         return super.onStartCommand(intent, flags, startId);
    }
}
