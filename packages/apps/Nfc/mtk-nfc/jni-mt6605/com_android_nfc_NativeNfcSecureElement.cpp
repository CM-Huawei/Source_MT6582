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

#include <errno.h>
#include <semaphore.h>
#include <string.h>

#include "com_android_nfc.h"

#define  MTK_NFC_SIM1_Id    (1)
#define  MTK_NFC_SIM2_Id    (2)
#define  MTK_NFC_ESE_Id     (3)
#define  MTK_NFC_NOESE_Id   (4)

unsigned  int   SeConnId = 0;
unsigned  int   SeRecDataLen = 0;
unsigned  char  SeRecData[MTK_NFC_SE_SEND_DATA_MAX_LEN];

extern unsigned int g_current_seid;
extern nfc_jni_callback_data *g_working_cb_data;

namespace android {

unsigned int nfc_jni_eSE_id_check()
{
    unsigned int u4SeId = MTK_NFC_NOESE_Id;
	FILE *fp;
  
	fp = fopen("/system/etc/nfcse.cfg","r");
	
	if (fp == NULL)
	{
		ALOGE("%s: can not open nfcse.cfg", __FUNCTION__);
	}
	else
	{
	    char *line = NULL;
		size_t len;
		ssize_t read;
		
		while ((read = getline(&line, &len, fp)) != -1) 
	    {
	        //TRACE("nfc_jni_eSE_id_check,%d,%d,%s",read, line);

			if (read >= 6 )
			{
                if (!strncmp(line, "SWP1:ESE", 8))
                {
                    u4SeId = MTK_NFC_SIM1_Id;
                    break;
    			}
                else if (!strncmp(line, "SWP2:ESE", 8))
                {
                    u4SeId = MTK_NFC_SIM2_Id;
                    break;
    			}
    			else if (!strncmp(line, "SD:YES", 6))
    			{
    			    u4SeId = MTK_NFC_NOESE_Id;
                    break;
    			}
    			else if (!strncmp(line, "ESE:YES", 7))
    			{
    			    u4SeId = MTK_NFC_ESE_Id;
                    break;
    			}
    			else
    			{
    			}
			}
		}
		
		if (line)
			free(line);
		
		fclose(fp);
	}
	
	TRACE("nfc_jni_eSE_id_check, %d",u4SeId);

	return u4SeId;
}

void nfc_jni_se_open_conn_callback(void *pContext, NFCSTATUS status, unsigned int conn_id)
{
	struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;
	
	LOG_CALLBACK("nfc_jni_se_open_conn_callback", status);
	
	pContextData->status = status;
	SeConnId = conn_id;
	sem_post(&pContextData->sem);

}
void nfc_jni_se_close_conn_callback(void *pContext, NFCSTATUS status)
{
	struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;
	
	LOG_CALLBACK("nfc_jni_se_close_conn_callback", status);
	
	pContextData->status = status;
	sem_post(&pContextData->sem);

}
void nfc_jni_se_send_data_callback(void *pContext, s_mtk_nfc_jni_se_send_data_rsp *SeReceiveData)
{
	struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;
	
	LOG_CALLBACK("nfc_jni_se_send_data_callback", SeReceiveData->status);

    SeRecDataLen = SeReceiveData->length ;
	memcpy(SeRecData, SeReceiveData->data, SeRecDataLen);
	
	pContextData->status = SeReceiveData->status;
	sem_post(&pContextData->sem);

}



static jint com_android_nfc_NativeNfcSecureElement_doOpenSecureElementConnection(JNIEnv *e, jobject o)
{

    //struct nfc_jni_native_data *nat;
    NFCSTATUS ret;
	jint ConnId = (-1);
    struct nfc_jni_callback_data cb_data;
	s_mtk_nfc_jni_se_open_conn_req_t rOpenSeConn;

    CONCURRENCY_LOCK();

    /* Retrieve native structure address */
    //nat = nfc_jni_get_nat(e, o);

	//check that eSE is enable ?
    if (g_current_seid != nfc_jni_eSE_id_check())
    {
        goto clean_and_return;
    } 
	
    /* Create the local semaphore */
    if (!nfc_cb_data_init(&cb_data, NULL)) {
        goto clean_and_return;
    }

	g_working_cb_data = &cb_data;

    memset(&rOpenSeConn,0,sizeof(rOpenSeConn));

    rOpenSeConn.seid= MTK_NFC_ESE_Id;
	rOpenSeConn.pContext = (void *)&cb_data;

	ret = android_nfc_jni_send_msg(MTK_NFC_JNI_SE_OPEN_CONN_REQ, sizeof(rOpenSeConn), &rOpenSeConn);

	if (ret == FALSE)
    {
       ALOGE("send MTK_NFC_JNI_SE_OPEN_CONN_REQ fail\n");
       goto clean_and_return;
    }

    /* Wait for callback response */
    if (sem_wait(&cb_data.sem)) {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }

    ConnId = (jint)SeConnId;

    clean_and_return:
	
	/* clear working callback data pointer */
	g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

	CONCURRENCY_UNLOCK();

	TRACE("com_android_nfc_NativeNfcSecureElement_doOpenSecureElementConnection, conn id ,%d",ConnId);
	
	return ConnId;
}

static jboolean com_android_nfc_NativeNfcSecureElement_doDisconnect(JNIEnv *e, jobject o, jint handle)
{

    //struct nfc_jni_native_data *nat;
    NFCSTATUS ret;
	jboolean result = FALSE;
    struct nfc_jni_callback_data cb_data;
	s_mtk_nfc_jni_se_close_conn_req_t rCloseSeConn;

    CONCURRENCY_LOCK();

	TRACE("com_android_nfc_NativeNfcSecureElement_doDisconnect, conn id ,%d",handle);

    /* Retrieve native structure address */
    //nat = nfc_jni_get_nat(e, o);
    if (g_current_seid == 0)
    {
        goto clean_and_return;
    }
	
    /* Create the local semaphore */
    if (!nfc_cb_data_init(&cb_data, NULL)) {
        goto clean_and_return;
    }

	g_working_cb_data = &cb_data;


    memset(&rCloseSeConn,0,sizeof(rCloseSeConn));

    rCloseSeConn.conn_id= handle;
	rCloseSeConn.pContext = (void *)&cb_data;

	ret = android_nfc_jni_send_msg(MTK_NFC_JNI_SE_CLOSE_CONN_REQ, sizeof(rCloseSeConn), &rCloseSeConn);

	if (ret == FALSE)
    {
       ALOGE("send MTK_NFC_JNI_SE_CLOSE_CONN_REQ fail\n");
       goto clean_and_return;
    }

    /* Wait for callback response */
    if (sem_wait(&cb_data.sem)) {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
}

    result = (jboolean)g_working_cb_data->status;
	
    clean_and_return:
	
	/* clear working callback data pointer */
	g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

	CONCURRENCY_UNLOCK();

	return result;
}


static jbyteArray com_android_nfc_NativeNfcSecureElement_doTransceive(JNIEnv *e,
   jobject o,jint handle, jbyteArray data)
{

	//struct nfc_jni_native_data *nat;
	NFCSTATUS ret;
	jbyteArray result = NULL;
	struct nfc_jni_callback_data cb_data;
	s_mtk_nfc_jni_se_send_data_req_t rSeTransData;
	uint8_t *buf;
	uint32_t buflen;

	TRACE("com_android_nfc_NativeNfcSecureElement_doTransceive, conn id,%d ; pdata,%x",handle,data);

	CONCURRENCY_LOCK();

	/* Retrieve native structure address */
	//nat = nfc_jni_get_nat(e, o);
	
	//check that eSE is enable ?
    if (g_current_seid != nfc_jni_eSE_id_check())
    {
        goto clean_and_return;
    }
	
	/* Create the local semaphore */
	if (!nfc_cb_data_init(&cb_data, NULL)) {
		goto clean_and_return;
	}

	g_working_cb_data = &cb_data;

    if (data == NULL)
    {
       goto clean_and_return;
    }

	buf = (uint8_t *)e->GetByteArrayElements(data, NULL);
	buflen = (uint32_t)e->GetArrayLength(data);

	memset(&rSeTransData,0,sizeof(rSeTransData));

	rSeTransData.conn_id = handle;
	memcpy(rSeTransData.data, buf, buflen);
	rSeTransData.length= buflen;
	rSeTransData.pContext = (void *)&cb_data;

	ret = android_nfc_jni_send_msg(MTK_NFC_JNI_SE_SEND_DATA_REQ, sizeof(rSeTransData), &rSeTransData);

	if (ret == FALSE)
	{
	   ALOGE("send MTK_NFC_JNI_SE_SEND_DATA_REQ fail\n");
	   goto clean_and_return;
	}

	/* Wait for callback response */
	if (sem_wait(&cb_data.sem)) {
		ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
		goto clean_and_return;
	}

	/* Copy results back to Java */
	buf = SeRecData;
	buflen = SeRecDataLen;
	
	result = e->NewByteArray(buflen);
	if(result != NULL)
	{
	   e->SetByteArrayRegion(result, 0, buflen, (jbyte *)buf);
	}

	
	clean_and_return:
		
	e->ReleaseByteArrayElements(data, (jbyte*)buf, JNI_ABORT);

	
	/* clear working callback data pointer */
	g_working_cb_data = NULL;

    memset(SeRecData,0,SeRecDataLen);
    SeRecDataLen = 0;
	
	nfc_cb_data_deinit(&cb_data);

	CONCURRENCY_UNLOCK();

	return result;
}


static jbyteArray com_android_nfc_NativeNfcSecureElement_doGetUid(JNIEnv *e, jobject o, jint handle)
{
   ALOGD("com_android_nfc_NativeNfcSecureElement_doGetUid()");
   return NULL;
}

static jintArray com_android_nfc_NativeNfcSecureElement_doGetTechList(JNIEnv *e, jobject o, jint handle)
{
   ALOGD("com_android_nfc_NativeNfcSecureElement_doGetTechList()");
   return NULL;
}


/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] =
{
   {"doNativeOpenSecureElementConnection", "()I",
      (void *)com_android_nfc_NativeNfcSecureElement_doOpenSecureElementConnection},
   {"doNativeDisconnectSecureElementConnection", "(I)Z",
      (void *)com_android_nfc_NativeNfcSecureElement_doDisconnect},
   {"doTransceive", "(I[B)[B",
      (void *)com_android_nfc_NativeNfcSecureElement_doTransceive},
   {"doGetUid", "(I)[B",
      (void *)com_android_nfc_NativeNfcSecureElement_doGetUid},
   {"doGetTechList", "(I)[I",
      (void *)com_android_nfc_NativeNfcSecureElement_doGetTechList},
};

int register_com_android_nfc_NativeNfcSecureElement(JNIEnv *e)
{
   return jniRegisterNativeMethods(e,
      "com/android/nfc/dhimpl/NativeNfcSecureElement",
      gMethods, NELEM(gMethods));
}

} // namespace android
