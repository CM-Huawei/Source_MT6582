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

#define LOG_TAG "RemoteDisplay"

#include "jni.h"
#include "JNIHelp.h"

#include "android_os_Parcel.h"
#include "android_util_Binder.h"

#include <android_runtime/AndroidRuntime.h>
#include <android_runtime/android_view_Surface.h>
#include <android_runtime/Log.h>

#include <binder/IServiceManager.h>

#include <gui/IGraphicBufferProducer.h>

#include <media/IMediaPlayerService.h>
#include <media/IRemoteDisplay.h>
#include <media/IRemoteDisplayClient.h>

#include <utils/Log.h>

#include <ScopedUtfChars.h>

#include <media/IMediaDeathNotifier.h>


namespace android {

static struct {
    jmethodID notifyDisplayConnected;
    jmethodID notifyDisplayDisconnected;
    jmethodID notifyDisplayError;
#ifndef ANDROID_DEFAULT_CODE
    ///M:@{
    jmethodID notifyDisplayGenericMsgEvent;
    ///@}
#endif
} gRemoteDisplayClassInfo;

// ----------------------------------------------------------------------------

class NativeRemoteDisplayClient : public BnRemoteDisplayClient {
public:
    NativeRemoteDisplayClient(JNIEnv* env, jobject remoteDisplayObj) :
            mRemoteDisplayObjGlobal(env->NewGlobalRef(remoteDisplayObj)) {
    }

protected:
    ~NativeRemoteDisplayClient() {
        JNIEnv* env = AndroidRuntime::getJNIEnv();
        env->DeleteGlobalRef(mRemoteDisplayObjGlobal);
    }

public:
    virtual void onDisplayConnected(const sp<IGraphicBufferProducer>& bufferProducer,
            uint32_t width, uint32_t height, uint32_t flags, uint32_t session) {
        JNIEnv* env = AndroidRuntime::getJNIEnv();

        jobject surfaceObj = android_view_Surface_createFromIGraphicBufferProducer(env, bufferProducer);
        if (surfaceObj == NULL) {
            ALOGE("Could not create Surface from surface texture %p provided by media server.",
                  bufferProducer.get());
            return;
        }

        env->CallVoidMethod(mRemoteDisplayObjGlobal,
                gRemoteDisplayClassInfo.notifyDisplayConnected,
                surfaceObj, width, height, flags, session);
        env->DeleteLocalRef(surfaceObj);
        checkAndClearExceptionFromCallback(env, "notifyDisplayConnected");
    }

    virtual void onDisplayDisconnected() {
        JNIEnv* env = AndroidRuntime::getJNIEnv();

        env->CallVoidMethod(mRemoteDisplayObjGlobal,
                gRemoteDisplayClassInfo.notifyDisplayDisconnected);
        checkAndClearExceptionFromCallback(env, "notifyDisplayDisconnected");
    }

    virtual void onDisplayError(int32_t error) {
        JNIEnv* env = AndroidRuntime::getJNIEnv();

        env->CallVoidMethod(mRemoteDisplayObjGlobal,
                gRemoteDisplayClassInfo.notifyDisplayError, error);
        checkAndClearExceptionFromCallback(env, "notifyDisplayError");
    }
#ifndef ANDROID_DEFAULT_CODE
    virtual void onDisplayGenericMsgEvent(uint32_t event) {
        ALOGD("onDisplayGenericMsgEvent ENTRY");
        JNIEnv* env = AndroidRuntime::getJNIEnv();
        env->CallVoidMethod(mRemoteDisplayObjGlobal,
                gRemoteDisplayClassInfo.notifyDisplayGenericMsgEvent, event);
        ALOGD("onDisplayGenericMsgEvent EXIT");
    }
    

    ///@}    
#endif

private:
    jobject mRemoteDisplayObjGlobal;

    static void checkAndClearExceptionFromCallback(JNIEnv* env, const char* methodName) {
        if (env->ExceptionCheck()) {
            ALOGE("An exception was thrown by callback '%s'.", methodName);
            LOGE_EX(env);
            env->ExceptionClear();
        }
    }
};

class BinderNotififer : public IBinder::DeathRecipient {
public:
    BinderNotififer(const sp<NativeRemoteDisplayClient>& client):
            mClient(client){
    }

    void binderDied(const wp<IBinder>& who){
        ALOGE("IMediaPlayerService is died");
        mClient->onDisplayDisconnected();
    }

private:
    sp<NativeRemoteDisplayClient> mClient;
};

class NativeRemoteDisplay {
public:
    NativeRemoteDisplay(const sp<IRemoteDisplay>& display,
            const sp<NativeRemoteDisplayClient>& client,
            const sp<BinderNotififer>& notififer) :
            mDisplay(display), mClient(client), mNotifier(notififer){
    }

#ifndef ANDROID_DEFAULT_CODE
    void nativeSetWfdLevel(int level){
        mDisplay->setBitrateControl(level);
    }
    int nativeGetWfdParam(int paramType){
        return mDisplay->getWfdParam(paramType);
    }
#endif

    ~NativeRemoteDisplay() {
        ALOGD("~NativeRemoteDisplay");
        mDisplay->dispose();
    }

    void pause() {
        mDisplay->pause();
    }

    void resume() {
        mDisplay->resume();
    }

private:
    sp<IRemoteDisplay> mDisplay;
    sp<NativeRemoteDisplayClient> mClient;
    sp<BinderNotififer> mNotifier;
};


// ----------------------------------------------------------------------------

static jint nativeListen(JNIEnv* env, jobject remoteDisplayObj, jstring ifaceStr) {
    ScopedUtfChars iface(env, ifaceStr);

    sp<IServiceManager> sm = defaultServiceManager();
    sp<IMediaPlayerService> service = interface_cast<IMediaPlayerService>(
            sm->getService(String16("media.player")));
    if (service == NULL) {
        ALOGE("Could not obtain IMediaPlayerService from service manager");
        return 0;
    }

    sp<NativeRemoteDisplayClient> client(new NativeRemoteDisplayClient(env, remoteDisplayObj));
    sp<IRemoteDisplay> display = service->listenForRemoteDisplay(
            client, String8(iface.c_str()));
    if (display == NULL) {
        ALOGE("Media player service rejected request to listen for remote display '%s'.",
                iface.c_str());
        return 0;
    }

    sp<BinderNotififer> notififer = new BinderNotififer(client);
    NativeRemoteDisplay* wrapper = new NativeRemoteDisplay(display, client, notififer);

    service->asBinder()->linkToDeath(notififer, 0);
    
    return reinterpret_cast<jint>(wrapper);
}

static void nativePause(JNIEnv* env, jobject remoteDisplayObj, jint ptr) {
    NativeRemoteDisplay* wrapper = reinterpret_cast<NativeRemoteDisplay*>(ptr);
    wrapper->pause();
}

static void nativeResume(JNIEnv* env, jobject remoteDisplayObj, jint ptr) {
    NativeRemoteDisplay* wrapper = reinterpret_cast<NativeRemoteDisplay*>(ptr);
    wrapper->resume();
}


static void nativeSetWfdLevel(JNIEnv* env, jobject remoteDisplayObj, jint ptr, jint level){
    NativeRemoteDisplay* wrapper = reinterpret_cast<NativeRemoteDisplay*>(ptr);
#ifndef ANDROID_DEFAULT_CODE
    wrapper->nativeSetWfdLevel(level);
#endif
}

static jint nativeGetWfdParam(JNIEnv* env, jobject remoteDisplayObj, jint ptr, jint paramType){
    NativeRemoteDisplay* wrapper = reinterpret_cast<NativeRemoteDisplay*>(ptr);
#ifndef ANDROID_DEFAULT_CODE
    int result;
    result = wrapper->nativeGetWfdParam(paramType);
    return (jint)result;;
#else
    return 0;
#endif

}


static void nativeDispose(JNIEnv* env, jobject remoteDisplayObj, jint ptr) {
    NativeRemoteDisplay* wrapper = reinterpret_cast<NativeRemoteDisplay*>(ptr);
    delete wrapper;
}

// ----------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
    {"nativeListen", "(Ljava/lang/String;)I",
            (void*)nativeListen },
    {"nativeDispose", "(I)V",
            (void*)nativeDispose },
    {"nativePause", "(I)V",
            (void*)nativePause },
    {"nativeResume", "(I)V",
            (void*)nativeResume },
    {"nativeSetWfdLevel", "(II)V",
            (void*)nativeSetWfdLevel },
    {"nativeGetWfdParam", "(II)I",
            (void*)nativeGetWfdParam },
};

int register_android_media_RemoteDisplay(JNIEnv* env)
{
    int err = AndroidRuntime::registerNativeMethods(env, "android/media/RemoteDisplay",
            gMethods, NELEM(gMethods));

    jclass clazz = env->FindClass("android/media/RemoteDisplay");
    gRemoteDisplayClassInfo.notifyDisplayConnected =
            env->GetMethodID(clazz, "notifyDisplayConnected",
                    "(Landroid/view/Surface;IIII)V");
    gRemoteDisplayClassInfo.notifyDisplayDisconnected =
            env->GetMethodID(clazz, "notifyDisplayDisconnected", "()V");
    gRemoteDisplayClassInfo.notifyDisplayError =
            env->GetMethodID(clazz, "notifyDisplayError", "(I)V");
#ifndef ANDROID_DEFAULT_CODE
    ///M:@{    
    gRemoteDisplayClassInfo.notifyDisplayGenericMsgEvent =
            env->GetMethodID(clazz, "notifyDisplayGenericMsgEvent", "(I)V");    
    ///@}
#endif
    return err;
}

};
