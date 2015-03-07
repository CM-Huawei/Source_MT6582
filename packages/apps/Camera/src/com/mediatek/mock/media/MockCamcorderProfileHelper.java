/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.mediatek.mock.media;

import android.media.CamcorderProfile;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

public class MockCamcorderProfileHelper {
    private static final String TAG = "MockCamcorderProfileHelper";

    public static CamcorderProfile getMtkCamcorderProfile(int cameraId, int quality) {
        CamcorderProfile camcorderProfile = null;
        // currently this function only returns a fixed fake CamcorderProfile
        try {
            Class c = Class.forName("android.media.CamcorderProfile");
            Constructor ctor = c.getDeclaredConstructor(new Class[] {
                int.class, int.class, int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class, int.class, int.class});
            ctor.setAccessible(true);
            camcorderProfile = (CamcorderProfile) ctor.newInstance(new Object[] {
                30, 10, 2, 2, 9000000, 30,
                1280, 720, 0, 128000, 44100, 1});
        } catch (ClassNotFoundException e) {
            Log.w(TAG, " " + e);
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            Log.w(TAG, " " + e);
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            Log.w(TAG, " " + e);
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            Log.w(TAG, " " + e);
            e.printStackTrace();
        } catch (InstantiationException e) {
            Log.w(TAG, " " + e);
            e.printStackTrace();
        }

        return camcorderProfile;
    }
}
