/*
 * Copyright (C) 2009 The Android Open Source Project
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

#define LOG_TAG "VibratorService"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"
//#if defined(MTK_VIBSPK_SUPPORT)
#if defined(MTK_VIBSPK_OPTION_SUPPORT)
#include "VibSpkAudioPlayer.h"
#include <cutils/properties.h>
#endif
//#endif

#include <utils/misc.h>
#include <utils/Log.h>
#include <hardware_legacy/vibrator.h>

#include <stdio.h>


namespace android
{

#if defined(MTK_VIBSPK_OPTION_SUPPORT)

const char PROPERTY_KEY_VIBSPK_ON_JNI[PROPERTY_KEY_MAX] = "persist.af.feature.vibspk";

static bool IsJNISupportVibSpk(void)
{
    bool bSupportFlg = false;
    char stForFeatureUsage[PROPERTY_VALUE_MAX];

#if defined(MTK_VIBSPK_SUPPORT)
    property_get(PROPERTY_KEY_VIBSPK_ON_JNI, stForFeatureUsage, "1"); //"1": default on
#else
    property_get(PROPERTY_KEY_VIBSPK_ON_JNI, stForFeatureUsage, "0"); //"0": default off
#endif
    bSupportFlg = (stForFeatureUsage[0] == '0') ? false : true;

    return bSupportFlg;
}
#endif

static jboolean vibratorExists(JNIEnv *env, jobject clazz)
{
#if defined(MTK_VIBSPK_OPTION_SUPPORT)
    if(IsJNISupportVibSpk())
        return JNI_TRUE;
    else
        return vibrator_exists() > 0 ? JNI_TRUE : JNI_FALSE;
#else
    return vibrator_exists() > 0 ? JNI_TRUE : JNI_FALSE;
#endif

}

static void vibratorOn(JNIEnv *env, jobject clazz, jlong timeout_ms)
{
#if defined(MTK_VIBSPK_OPTION_SUPPORT)

    // ALOGI("vibratorOn\n");
    if(IsJNISupportVibSpk())
    {
        if(timeout_ms == 0)
            VIBRATOR_SPKOFF();
        else
            VIBRATOR_SPKON((unsigned int)timeout_ms);
    }
    else
    {
        vibrator_on(timeout_ms);
    }
#else    
    vibrator_on(timeout_ms);
#endif
}

static void vibratorOff(JNIEnv *env, jobject clazz)
{
#if defined(MTK_VIBSPK_OPTION_SUPPORT)

    if(IsJNISupportVibSpk())
        VIBRATOR_SPKOFF();
    else
        vibrator_off();
#else
    vibrator_off();
#endif
}

static JNINativeMethod method_table[] = {
    { "vibratorExists", "()Z", (void*)vibratorExists },
    { "vibratorOn", "(J)V", (void*)vibratorOn },
    { "vibratorOff", "()V", (void*)vibratorOff }
};

int register_android_server_VibratorService(JNIEnv *env)
{
    return jniRegisterNativeMethods(env, "com/android/server/VibratorService",
            method_table, NELEM(method_table));
}

};
