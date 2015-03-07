
/*
 * Copyright (C) 2010 The Android Open Source Project
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

#include <semaphore.h>
#include <errno.h>

#include "com_android_nfc.h"

extern uint8_t device_connected_flag;
extern nfc_jni_callback_data *g_working_cb_data;


namespace android {


static jboolean com_android_nfc_NativeP2pDevice_doConnect(JNIEnv *e, jobject o)
{
   ALOGD("com_android_nfc_NativeP2pDevice_doConnect()");
   return JNI_TRUE;
}

static jboolean com_android_nfc_NativeP2pDevice_doDisconnect(JNIEnv *e, jobject o)
{
    ALOGD ("%s: enter", "com_android_nfc_NativeP2pDevice_doDisconnect");
    mtk_nfc_service_dev_deactivate_req rDevDeactivate;
    unsigned int PayloadSize = sizeof(mtk_nfc_service_dev_deactivate_req);
    int result = FALSE;
    int ret = -1;
    struct nfc_jni_callback_data cb_data;
    struct nfc_jni_native_data *nat = NULL;

    CONCURRENCY_LOCK();

    /* Create the local semaphore */
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
       ALOGD(" CB INIT FAIL");
       goto clean_and_return;
    }

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat_ext(e);//nfc_jni_get_nat (e, o);
    if (!nat)
    {
       ALOGD("Invalid java native");
        goto clean_and_return;
    }

    if (nat->u1DevActivated == 0)
    {
        ALOGD("No Activated Device");
        result = TRUE;
        goto clean_and_return;
    }

    /* assign working callback data pointer */
    g_working_cb_data = &cb_data;
 
    /* Reset device connected handle */
    device_connected_flag = 0;
 
    /* ***************************************** */
    /* Enable Polling Loop                       */
    /* ***************************************** */
    // send set discover req msg to nfc daemon
    memset(&rDevDeactivate,0,sizeof(mtk_nfc_service_dev_deactivate_req));
    rDevDeactivate.u1DeactType = 3; // restart polling
    ALOGD ("com_android_nfc_NativeP2pDevice_doDisconnect,u1DeactType,%d", rDevDeactivate.u1DeactType);

    ret = android_nfc_jni_send_msg(MTK_NFC_DEV_DEACTIVATE_REQ, PayloadSize, &rDevDeactivate);

    if (ret == FALSE)
    {
       ALOGE("send MTK_NFC_DEV_DEACTIVATE_REQ fail\n");
       goto clean_and_return;
    }
    
    // Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
       ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
       goto clean_and_return;
    }
    nat->fgPollingEnabled = TRUE;
    nat->u1DevActivated = 0;
    nat->fgChangeToFrameRfInterface = FALSE;
    ALOGD("com_android_nfc_NativeP2pDevice_doDisconnect(), done");
    // Initialization Status (0: success, 1: fail)
    if(cb_data.status != 0)
    {
       ALOGE("Initialization fail, stauts: %d\n", cb_data.status);
       goto clean_and_return;
    }
 
    result = TRUE;
    
clean_and_return:
    
    /* clear working callback data pointer */
    g_working_cb_data = NULL;
    ALOGD("com_android_nfc_NativeP2pDevice_doDisconnect,%d\n", result);
    nfc_cb_data_deinit(&cb_data);
    #if 0
    if (result != TRUE)
    {
       if(nat)
       {
          kill_mtk_client(nat);
       }
    }   
    #endif
    CONCURRENCY_UNLOCK();
    ALOGD ("%s: exit, %d", "com_android_nfc_NativeP2pDevice_doDisconnect", result);

    return (result) ? JNI_TRUE : JNI_FALSE;
}


static jbyteArray com_android_nfc_NativeP2pDevice_doTransceive(JNIEnv *e,
   jobject o, jbyteArray data)
{
   ALOGD("com_android_nfc_NativeP2pDevice_doTransceive()");
   return NULL;
}


static jbyteArray com_android_nfc_NativeP2pDevice_doReceive(
   JNIEnv *e, jobject o)
{
   ALOGD("com_android_nfc_NativeP2pDevice_doReceive()");
   return NULL;
}

static jboolean com_android_nfc_NativeP2pDevice_doSend(
   JNIEnv *e, jobject o, jbyteArray buf)
{
   ALOGD("com_android_nfc_NativeP2pDevice_doSend()");
   return JNI_TRUE;
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] =
{
   {"doConnect", "()Z",
      (void *)com_android_nfc_NativeP2pDevice_doConnect},
   {"doDisconnect", "()Z",
      (void *)com_android_nfc_NativeP2pDevice_doDisconnect},
   {"doTransceive", "([B)[B",
      (void *)com_android_nfc_NativeP2pDevice_doTransceive},
   {"doReceive", "()[B",
      (void *)com_android_nfc_NativeP2pDevice_doReceive},
   {"doSend", "([B)Z",
      (void *)com_android_nfc_NativeP2pDevice_doSend},
};

int register_com_android_nfc_NativeP2pDevice(JNIEnv *e)
{
   return jniRegisterNativeMethods(e,
      "com/android/nfc/dhimpl/NativeP2pDevice",
      gMethods, NELEM(gMethods));
}

} // namepspace android
