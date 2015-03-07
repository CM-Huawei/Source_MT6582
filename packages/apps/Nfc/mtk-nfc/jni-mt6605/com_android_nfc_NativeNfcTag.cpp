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

static mtk_nfc_ndef_data_t nfc_jni_ndef_data;
unsigned char *nfc_jni_ndef_buf = NULL;
unsigned int   nfc_jni_ndef_buf_len = 0;
unsigned int   nfc_jni_ndef_buf_size = 0;

extern uint8_t device_connected_flag;
extern nfc_jni_callback_data *g_working_cb_data;

extern nfc_jni_callback_data *g_working_cb_data_tag;


namespace android {

extern bool dta_nfc_jni_get_dta_mode (void); //for dta mode

static jboolean com_android_nfc_NativeNfcTag_doPresenceCheck(JNIEnv *e, jobject o);
static jbyteArray com_android_nfc_NativeNfcTag_doTransceive(JNIEnv *e,
   jobject o, jbyteArray data, jboolean raw, jintArray statusTargetLost);


static uint16_t
crc_16_ccitt1( uint8_t* msg, size_t len, uint16_t init )
{
    uint16_t b, crc = init;

    do {
        b = *msg++ ^ (crc & 0xFF);
        b ^= (b << 4) & 0xFF;
        crc = (crc >> 8) ^ (b << 8) ^ (b << 3) ^ (b >> 4);
    } while( --len );

    return crc;
}

static void
nfc_insert_crc_a( uint8_t* msg, size_t len )
{
    uint16_t crc;
	ALOGE("nfc_insert_crc_a run");
    crc = crc_16_ccitt1( msg, len, 0x6363 );
    msg[len] = crc & 0xFF;
    msg[len + 1] = (crc >> 8) & 0xFF;
}

static void
nfc_get_crc_a( uint8_t* msg, size_t len, uint8_t* byte1, uint8_t* byte2)
{
    uint16_t crc;

    crc = crc_16_ccitt1( msg, len, 0x6363 );
    *byte1 = crc & 0xFF;
    *byte2 = (crc >> 8) & 0xFF;
}

static bool
crc_valid( uint8_t* nfc_jni_transceive_buffer,uint8_t* msg, size_t len)
{
    uint8_t crcByte1, crcByte2;

    nfc_get_crc_a(nfc_jni_transceive_buffer,
          len - 2, &crcByte1, &crcByte2);

    if (msg[len - 2] == crcByte1 &&
          msg[len - 1] == crcByte2) {
        return true;
    }
    else {
        return false;
    }

}

static uint8_t
char_2_ascii( unsigned char tmpchr )
{
	if (tmpchr >= 0 && tmpchr <= 9)
	{
		return tmpchr + 48;
	}
	else if (tmpchr >= 10 && tmpchr <= 15)
	{
		return tmpchr + 87;
	}
	else
	{
		return -1;
	}
}

/* Functions */
static jbyteArray com_android_nfc_NativeNfcTag_doRead(JNIEnv *e,
   jobject o)
{
   int result = FALSE;
   jbyteArray buf = NULL;
   struct nfc_jni_callback_data cb_data;

   ALOGD("com_android_nfc_NativeNfcTag_doRead()");

   CONCURRENCY_LOCK();
   /* Create the local semaphore */
   if (nfc_cb_data_init(&cb_data, NULL))
   {
      mtk_nfc_ndef_data_t *p_data=NULL;
      // assign working callback data pointer
      g_working_cb_data_tag = &cb_data;
      p_data = (mtk_nfc_ndef_data_t*)malloc(sizeof(mtk_nfc_ndef_data_t));

      p_data->datalen = nfc_jni_ndef_buf_len;
      //nfc_jni_ndef_data.databuffer = &nfc_jni_ndef_buf;
      #if 0
      if(0)
      {
        int j =0;
        for(j=0;j<17;j++)
        {
            nfc_jni_ndef_data.databuffer[j] = 0x00;
        }
      }
      #endif

      ALOGE("databuffer[%x],datalen[%x]\n",nfc_jni_ndef_buf , nfc_jni_ndef_buf_len);
      ALOGE("databuffer[%x],datalen[%x]\n", nfc_jni_ndef_buf,nfc_jni_ndef_buf_len);

      // send doRead req msg to nfc daemon
      //result = android_nfc_jni_send_msg(MTK_NFC_JNI_TAG_READ_REQ, 0, NULL);
      result = android_nfc_jni_send_msg(MTK_NFC_JNI_TAG_READ_REQ, sizeof(mtk_nfc_ndef_data_t), p_data);

      // Wait for callback response
      if(sem_wait(&cb_data.sem))
      {
          ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
          //nfc_cb_data_deinit(&cb_data);
          result = FALSE;
          //return buf;
      }

      if (result == TRUE)
      {
         /* doRead Status (0: success, 1: fail) */
         if(cb_data.status != 0)
         {
            ALOGD("Failed to doRead the stack\n");
            result = FALSE;
         }
      }
      else
      {
          ALOGD("send MTK_NFC_TAG_READ_REQ fail\n");
            result = FALSE;
      }

      if(p_data) free(p_data);

      if(result == TRUE)
      {
      //nfc_jni_ndef_data.datalen = nfc_jni_ndef_buf_len;
      if(0)
      {
          int l;
          unsigned char *p = nfc_jni_ndef_buf;
          ALOGE("datalen[%d]\n",nfc_jni_ndef_buf_len);
          for(l = 0; l <nfc_jni_ndef_buf_len;l++)
          {
              ALOGE("[%02x][%c]\n",*p, *p);
              p++;
          }
      }
      buf = e->NewByteArray(nfc_jni_ndef_buf_len);
      e->SetByteArrayRegion(buf, 0, nfc_jni_ndef_buf_len,
         (jbyte *) nfc_jni_ndef_buf);
      }
      else
      {
          ALOGE("doRead,result = FALSE");

      }
      // clear working callback data pointer
      g_working_cb_data_tag = NULL;

      nfc_cb_data_deinit(&cb_data);
   }
   else
   {
       ALOGE("Failed to create semaphore (errno=0x%08x)\n", errno);
   }

   CONCURRENCY_UNLOCK();
   return buf;
}

static jboolean com_android_nfc_NativeNfcTag_doWrite(JNIEnv *e,
   jobject o, jbyteArray buf)
{
   int result = FALSE;
   struct nfc_jni_callback_data cb_data;
   jboolean status = JNI_TRUE;

   ALOGD("com_android_nfc_NativeNfcTag_doWrite()");
   CONCURRENCY_LOCK();
   /* Create the local semaphore */
   if (nfc_cb_data_init(&cb_data, NULL))
   {
      unsigned int len = 0;

      unsigned char *plocBuf = NULL;
      unsigned int   plocBufLen = 0;

      // assign working callback data pointer
      mtk_nfc_ndef_data_t *p_data=NULL;



      plocBufLen = (unsigned int)e->GetArrayLength(buf);
      plocBuf = (unsigned char *)e->GetByteArrayElements(buf, NULL);


      g_working_cb_data_tag = &cb_data;
      len = plocBufLen + sizeof(mtk_nfc_ndef_data_t);

      ALOGD("len,%d",len);
      p_data = (mtk_nfc_ndef_data_t*)malloc(len);

      p_data->datalen = plocBufLen;
      memcpy(&p_data->databuffer, plocBuf, plocBufLen);

      // assign working callback data pointer
      g_working_cb_data_tag = &cb_data;

      ALOGD("Tag_Write,len(%d)",plocBufLen);
      {
         int l =0;
         unsigned char *p=plocBuf;
         for (l=0; l< plocBufLen;l++)
         {
             ALOGD("WR,%d[%02x][%c]",l,*p,*p);
             p++;
         }
      }

      // send doRead req msg to nfc daemon
      //result = android_nfc_jni_send_msg(MTK_NFC_JNI_TAG_WRITE_REQ, 0, NULL);
      result = android_nfc_jni_send_msg(MTK_NFC_JNI_TAG_WRITE_REQ, len, p_data);

      // Wait for callback response
      if(sem_wait(&cb_data.sem))
      {
          ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
          //e->ReleaseByteArrayElements(buf, (jbyte *)plocBuf, JNI_ABORT);
          //nfc_cb_data_deinit(&cb_data);
          result = FALSE;
          //return JNI_FALSE;
          status = JNI_FALSE;
      }

      if (result == TRUE)
      {
         /* doWrite Status (0: success, 1: fail) */
         if(cb_data.status != 0)
         {
            ALOGE("Failed to doRead the stack,status(%d)\n",cb_data.status);
            status = JNI_FALSE;
         }
      }
      else
      {
          ALOGE("send MTK_NFC_TAG_READ_REQ fail\n");
          status = JNI_FALSE;
      }

      if(p_data) free(p_data);

      e->ReleaseByteArrayElements(buf, (jbyte *)plocBuf, JNI_ABORT);
      // clear working callback data pointer
      g_working_cb_data_tag = NULL;

      nfc_cb_data_deinit(&cb_data);
   }
   else
   {
       ALOGE("Failed to create semaphore (errno=0x%08x)\n", errno);
       status = JNI_FALSE;
   }
   CONCURRENCY_UNLOCK();
   return status;
}


static jint com_android_nfc_NativeNfcTag_doConnect (JNIEnv *e, jobject o, MTK_NFC_HANDLER targetHandle)
{
    ALOGD ("%s: targetHandle = %d", "com_android_nfc_NativeNfcTag_doConnect", targetHandle);
    int i = targetHandle;
    struct nfc_jni_native_data *nat = nfc_jni_get_nat_ext(e);//nfc_jni_get_nat (e, o);
    int retCode = 0;

	if (!nat)
    {
        ALOGD("Fail to get Java Nat");
        ALOGE ("%s: Fail to get Java Nat", "com_android_nfc_NativeNfcTag_doConnect");
        retCode = 2;
        goto TheEnd;
    }

    nat->fgChangeToFrameRfInterface = FALSE;
    if (i >= NUM_MAX_TECH)
    {
        ALOGE ("%s: Handle not found", "com_android_nfc_NativeNfcTag_doConnect");
        retCode = 2;
        goto TheEnd;
    }

    if (nat->u1DevActivated != 2)
    {
        ALOGD("Fail to do Connect because of no activated tag");
        ALOGE ("%s: no activated device", "com_android_nfc_NativeNfcTag_doConnect");
        retCode = 2;
        goto TheEnd;
    }

    if (nat->au4TechProtocols[i] != NFC_DEV_PROTOCOL_ISO_DEP)
    {
        ALOGD ("%s() Nfc PROTOCOL = %d, no action because it's not ISO_DEP", "com_android_nfc_NativeNfcTag_doConnect", nat->au4TechProtocols[i]);
        retCode = 0;
        goto TheEnd;
    }

    if ((nat->au4TechList[i] == TARGET_TYPE_ISO14443_3A) ||
		(nat->au4TechList[i] == TARGET_TYPE_ISO14443_3B))
    {
        ALOGD ("%s: switching to tech: %d need to switch rf intf to frame", "com_android_nfc_NativeNfcTag_doConnect", nat->au4TechList[i]);
        // connecting to NfcA or NfcB don't actually switch until/unless we get a transceive
        nat->fgChangeToFrameRfInterface = TRUE;
    }
    else
    {
        // connecting back to IsoDep or NDEF
        //return (switchRfInterface (NFA_INTERFACE_ISO_DEP) ? NFCSTATUS_SUCCESS : NFCSTATUS_FAILED);
        // TODO: add flow to swithc Rf Interface
        return 2;
    }

TheEnd:
    ALOGD ("%s: exit 0x%X", "com_android_nfc_NativeNfcTag_doConnect", retCode);
    return retCode;
}



static int i4ReActivateDevice (
	nfc_jni_native_data* nat,
	MTK_NFC_DEV_INTERFACE_TYPE_T RfInterface)
{
    int rVal = 1;

    ALOGD ("%s: enter; rf intf = %d", "i4ReActivateDevice", RfInterface);

    // deactivate Tag
    {
    	struct nfc_jni_callback_data cb_data;
        int result = FALSE;
        int ret = -1;
    	s_mtk_nfc_service_dev_deactivate_req_t rDevDeactivate;
        uint16_t PayloadSize = sizeof(s_mtk_nfc_service_dev_deactivate_req_t);

        CONCURRENCY_LOCK();

        ALOGD("deactivate device()");

    	/* Create the local semaphore */
        if (!nfc_cb_data_init(&cb_data, NULL))
        {
           goto clean_and_return1;
        }

        /* assign working callback data pointer */
        g_working_cb_data = &cb_data;
        /* Reset device connected handle */
        device_connected_flag = 0;

        /* ***************************************** */
        /* Enable Polling Loop                       */
        /* ***************************************** */
        // send set discover req msg to nfc daemon
        ALOGD("cb data:[0x%X]",g_working_cb_data);

    	rDevDeactivate.u1DeactType = 1; // sleep
        ret = android_nfc_jni_send_msg(MTK_NFC_DEV_DEACTIVATE_REQ, PayloadSize, &rDevDeactivate);
	nat->fgHostRequestDeactivate = TRUE;

        if (ret == FALSE)
        {
           ALOGE("send MTK_NFC_DEV_DEACTIVATE_REQ fail\n");
           goto clean_and_return1;
        }
        ALOGD("wait sem [0x%X]",&cb_data.sem);
        // Wait for callback response
        if(sem_wait(&cb_data.sem))
        {
           ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
           goto clean_and_return1;
        }
		ALOGD("get sem released");
		nat->u1DevActivated = 1; // sleep

        // Initialization Status (0: success, 1: fail)
        if(cb_data.status != 0)
        {
           ALOGE("Initialization fail, stauts: %d\n", cb_data.status);
           goto clean_and_return1;
        }
        ALOGD("deactivate success");
        result = TRUE;


clean_and_return1:

        /* clear working callback data pointer */
        g_working_cb_data = NULL;

        nfc_cb_data_deinit(&cb_data);
		if (result != TRUE)
        {
           #if 0 // don't kill nfcstackp
           if(nat)
           {
              kill_mtk_client(nat);
           }
           #endif
		   return 2;
        }
        CONCURRENCY_UNLOCK();
    }

    if (nat->u1DevActivated != 1) // not in sleep
    {
        ALOGD ("%s: tag is not in sleep", "i4ReActivateDevice");
        return 3;
    }


    // activate device
    {
    	struct nfc_jni_callback_data cb_data;
        int result = FALSE;
        int ret = -1;
    	s_mtk_nfc_service_dev_select_req_t rDevSelect;
		struct timespec ts;
        uint16_t PayloadSize = sizeof(s_mtk_nfc_service_dev_select_req_t);

        CONCURRENCY_LOCK();

        ALOGD("SELECT DEVICE");

    	/* Create the local semaphore */
        if (!nfc_cb_data_init(&cb_data, NULL))
        {
           goto clean_and_return2;
        }

        /* assign working callback data pointer */
        g_working_cb_data = &cb_data;
        /* Reset device connected handle */
        device_connected_flag = 0;

        /* ***************************************** */
        /* Enable Polling Loop                       */
        /* ***************************************** */
        // send set discover req msg to nfc daemon


    	rDevSelect.u1ID = nat->au4DevId[0]; // the first Tag
    	rDevSelect.u1Protocol = nat->au4TechProtocols[0];
		rDevSelect.u1Interface = RfInterface;

        ret = android_nfc_jni_send_msg(MTK_NFC_DEV_SELECT_REQ, PayloadSize, &rDevSelect);

        if (ret == FALSE)
        {
           ALOGE("send MTK_NFC_DEV_DEACTIVATE_REQ fail\n");
           goto clean_and_return2;
        }

        /* Wait for ACTIVATION NOTIFY (2s timeout) */
        clock_gettime(CLOCK_REALTIME, &ts);
        ts.tv_sec += 2;
        if(sem_timedwait(&cb_data.sem, &ts) == -1)
        {
            ALOGW("Operation timed out\n");
        }



        // Initialization Status (0: success, 1: fail)
        if(cb_data.status != 0)
        {
           ALOGE("Initialization fail, stauts: %d\n", cb_data.status);
           goto clean_and_return2;
        }

        result = TRUE;


clean_and_return2:

        /* clear working callback data pointer */
        g_working_cb_data = NULL;

        nfc_cb_data_deinit(&cb_data);
		if (result != TRUE)
        {
           #if 0 // don't kill nfcstackp
           if(nat)
           {
              kill_mtk_client(nat);
           }
           #endif
		   return 2;
        }
        CONCURRENCY_UNLOCK();
}

    if (nat->u1DevActivated != 2)
    {
        ALOGD("%s: tag is not active", "i4ReActivateDevice");
        rVal = 3;
    }
	else
    {
        rVal = 0;
    }

    ALOGD ("%s: exit; status=%d", "i4ReActivateDevice", rVal);
    return rVal;
}

static jint com_android_nfc_NativeNfcTag_doReconnect (JNIEnv *e, jobject o)
{
    ALOGD ("%s: enter", "com_android_nfc_NativeNfcTag_doReconnect");

    int retCode = 0;
    struct nfc_jni_native_data *nat = NULL;

    #if 0
    // this is only supported for type 2 or 4 (ISO_DEP) tags
    if (nat->au4TechProtocols[0] == NFC_DEV_PROTOCOL_ISO_DEP)
        retCode = i4ReActivateDevice(nat,NFC_DEV_INTERFACE_ISO_DEP);
    else if (nat->au4TechProtocols[0] == NFC_DEV_PROTOCOL_T2T)
        retCode = i4ReActivateDevice(nat,NFC_DEV_INTERFACE_FRAME);
    #else
    retCode = 0;
    #endif

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat_ext(e);//nfc_jni_get_nat (e, o);
    if (!nat)
    {
        ALOGD("Invalid java native");
        retCode = 1;
        goto TheEnd;
    }

    if (nat->u1DevActivated != 2)
    {
        ALOGE ("%s: tag already deactivated", "com_android_nfc_NativeNfcTag_doReconnect");
        retCode = 1;
        goto TheEnd;
    }

    if (nat->u1DevActivated == 0)
    {
        ALOGD("No Activated Device");
        retCode = 1;
        goto TheEnd;
    }

    if(dta_nfc_jni_get_dta_mode())
    {
        ALOGD ("%s: in dta_mode", "com_android_nfc_NativeNfcTag_doReconnect"); //for dta mode debug
        retCode = 0;
        goto TheEnd;
    }

    if(nat->rDevActivateParam.u1Protocol == NFC_DEV_PROTOCOL_T2T)
    {
        ALOGD ("%s: T2T do check presence", "com_android_nfc_NativeNfcTag_doReconnect");

        if(JNI_TRUE == com_android_nfc_NativeNfcTag_doPresenceCheck(e, o) )
        {
            retCode = 0;
        }
        else
        {
            retCode = 1;
        }

        goto TheEnd;
    }

    if(nat->rDevActivateParam.u1Protocol == NFC_DEV_PROTOCOL_15693)
    {
        jbyteArray  SendBuf = NULL;
        jbyteArray  RcvBuf = NULL;
        jintArray   statusTargetLost;
        uint8_t     cmd[2+NFC_ISO15693_UID_MAX_LEN];   // flag + cmd + UID
        int i;

        ALOGD ("%s: 15693 doTransceive", "com_android_nfc_NativeNfcTag_doReconnect");

        SendBuf = e->NewByteArray(2+NFC_ISO15693_UID_MAX_LEN);
        if(SendBuf == NULL)
        {
            retCode = 1;
            goto TheEnd;
        }

        cmd[0] = 0x20;  //flag: address mode on
        cmd[1] = 0x26;  //reset to ready

        //uid
        for(i=0; i<NFC_ISO15693_UID_MAX_LEN; i++)
        {
            cmd[2+i] = nat->rDevActivateParam.rRfTechParam.TechParam.rTechParamPollV.au1Uid[NFC_ISO15693_UID_MAX_LEN-i-1];
        }

        e->SetByteArrayRegion (SendBuf, 0, 2+NFC_ISO15693_UID_MAX_LEN, (jbyte *)cmd);
        RcvBuf = com_android_nfc_NativeNfcTag_doTransceive(e, o, SendBuf, 0, statusTargetLost);

        if(RcvBuf == NULL)
        {
            retCode = 0;    //always set success
            goto TheEnd;
        }

        retCode = 0;    //no check response flag, just check interface error
        goto TheEnd;
    }

    ALOGD ("%s: Other Tags", "com_android_nfc_NativeNfcTag_doReconnect");
    retCode = 0;

TheEnd:

    ALOGD ("%s: exit 0x%X", "com_android_nfc_NativeNfcTag_doReconnect", retCode);
    return retCode;
}

static jint com_android_nfc_NativeNfcTag_doHandleReconnect (JNIEnv *e, jobject o, MTK_NFC_HANDLER targetHandle)
{
    ALOGD ("%s: targetHandle = %d", "com_android_nfc_NativeNfcTag_doHandleReconnect", targetHandle);
    return com_android_nfc_NativeNfcTag_doConnect (e, o, targetHandle);
}


static jboolean com_android_nfc_NativeNfcTag_doDisconnect(JNIEnv *e, jobject o)
{
    ALOGD ("%s: enter", "com_android_nfc_NativeNfcTag_doDisconnect");
    mtk_nfc_service_dev_deactivate_req rDevDeactivate;
    unsigned int PayloadSize = sizeof(mtk_nfc_service_dev_deactivate_req);
    int result = FALSE;
    int ret = -1;
    struct nfc_jni_callback_data cb_data;
    struct nfc_jni_native_data *nat = NULL;

    if(nfc_jni_ndef_buf !=NULL)
    {
       free(nfc_jni_ndef_buf);
       nfc_jni_ndef_buf = NULL;
       nfc_jni_ndef_buf_len = 0x00;
       nfc_jni_ndef_buf_size = 0x00;
       ALOGD ("Free Tag alloc memory");
    }

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
    ALOGD("com_android_nfc_NativeNfcTag_doDisconnect(), done");
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

    nfc_cb_data_deinit(&cb_data);
    if (result != TRUE)
    {
       #if 0 // don't kill nfcstackp
       if(nat)
       {
          kill_mtk_client(nat);
       }
       #endif
    }
    CONCURRENCY_UNLOCK();
    ALOGD ("%s: exit, %d", "com_android_nfc_NativeNfcTag_doDisconnect", result);

    return (result) ? JNI_TRUE : JNI_FALSE;
}




static jbyteArray com_android_nfc_NativeNfcTag_doTransceive(JNIEnv *e,
   jobject o, jbyteArray data, jboolean raw, jintArray statusTargetLost)
{

    jint *targetLost = NULL;
    bool checkResponseCrc = false;
    unsigned int len=0;
    int result = true;
    struct nfc_jni_native_data *nat = nfc_jni_get_nat_ext(e);
    jbyteArray RecBuf = NULL;
    struct nfc_jni_callback_data cb_data;

    uint32_t     sTransceiveDataLen = 0;
    uint8_t*     sTransceiveData = NULL;
    uint32_t     pnfc_jni_ndef_buf_len = 0;
    uint8_t*     pnfc_jni_ndef_buf = NULL;
    uint8_t*	 outbuf = NULL;
    uint32_t 	 outlen;

    uint32_t    intI= 0;

    if(nat)
    {
        ALOGD("u1DevActivated,%d",nat->u1DevActivated);
        if(nat->u1DevActivated == 0)
        {
            return RecBuf;
        }
    }
    else
    {
        ALOGD("NULL NAT");
        return RecBuf;
    }

    if (statusTargetLost != NULL)
    {
        targetLost = e->GetIntArrayElements(statusTargetLost, 0);
        if (targetLost != NULL)
        {
            *targetLost = 0;
            ALOGD("statusTargetLost, targetLost=0");
        }
    }
    else
    {
        targetLost = NULL;
    }

    ALOGD("com_android_nfc_NativeNfcTag_doTransceive()raw(%d)",raw);

    ALOGD("com_android_nfc_NativeNfcTag_doTransceive(),u4NumDevTech,%d,raw(%d)",
    nat->u4NumDevTech,raw);
    if(1)
    {
    	 int i;
       for( i =0 ; i < nat->u4NumDevTech; i++)
       {
       	ALOGD("com_android_nfc_NativeNfcTag_doTransceive(),idx(%d),u4NumDevTech,%d",i,nat->au4TechList[i]);
       }
    }

    //nfc_jni_ndef_data.databuffer= nfc_jni_ndef_buf;

    if((nat->au4TechList[0] == TARGET_TYPE_FELICA) && (!dta_nfc_jni_get_dta_mode()))
    {

        outlen = pnfc_jni_ndef_buf_len = (unsigned int)e->GetArrayLength(data) - 1;
        pnfc_jni_ndef_buf = (unsigned char *)e->GetByteArrayElements(data, NULL);
        ALOGD("doTransceive, Felica len changing. %d",pnfc_jni_ndef_buf[0]);

        pnfc_jni_ndef_buf ++;
    }
    else
    {
        outlen = pnfc_jni_ndef_buf_len = (unsigned int)e->GetArrayLength(data);
        pnfc_jni_ndef_buf = (unsigned char *)e->GetByteArrayElements(data, NULL);
    }

    outbuf = (uint8_t*)malloc(pnfc_jni_ndef_buf_len + 2);

    if(!dta_nfc_jni_get_dta_mode())
    {
        if(nat->au4TechList[0] == TARGET_TYPE_MIFARE_CLASSIC ||
            nat->au4TechList[0] == TARGET_TYPE_MIFARE_UL)
        {
            if(raw)
            {
                checkResponseCrc = true;
                //outbuf = (uint8_t*)malloc(pnfc_jni_ndef_buf_len + 2);
                outlen += 2;
                memcpy(outbuf, pnfc_jni_ndef_buf, pnfc_jni_ndef_buf_len);
                nfc_insert_crc_a(outbuf,pnfc_jni_ndef_buf_len);
                ALOGD("com_android_nfc_NativeNfcTag_doTransceive(),added 2 bytes CRC(%x,%x)",outbuf[outlen-2],outbuf[outlen-1]);
            }
        }
    }

    ALOGD("doTransceive,len(%d,%d)", pnfc_jni_ndef_buf_len,(unsigned int)e->GetArrayLength(data));
    if(0)
    {
        int i;
        unsigned char *p = pnfc_jni_ndef_buf;
        for(i=0;i<pnfc_jni_ndef_buf_len;i++)
        {
              ALOGD("doTransceive,%d,[%02x][%c]\n",i,*p,*p);
              p++;
        }
    }

    CONCURRENCY_LOCK();
    if (nfc_cb_data_init(&cb_data, NULL))
    {
        // assign working callback data pointer
        s_mtk_nfc_jni_transceive_data_t *p_data=NULL;

        g_working_cb_data_tag = &cb_data;
        len = pnfc_jni_ndef_buf_len + sizeof(s_mtk_nfc_jni_transceive_data_t);
        if(checkResponseCrc)
        {
            len += 2;
        }

        ALOGD("doTransceive,len,%d",len);
        p_data = (s_mtk_nfc_jni_transceive_data_t*)malloc(len);

        if(dta_nfc_jni_get_dta_mode())
        {
            ALOGD("doTransceive, DTA no use CRC");
            p_data->raw = 0;
        }
        else if(nat->au4TechList[0] == TARGET_TYPE_MIFARE_CLASSIC ||
                    nat->au4TechList[0] == TARGET_TYPE_MIFARE_UL)
        {
            p_data->raw = raw;
        }
        else
        {
            ALOGD("doTransceive, raw reset 0, no use CRC");
            p_data->raw = 0;
        }

        p_data->result = 0x00;

        if(!checkResponseCrc)
        {
            p_data->datalen = pnfc_jni_ndef_buf_len;
            memcpy(&p_data->databuffer, pnfc_jni_ndef_buf, pnfc_jni_ndef_buf_len);
        }
        else
        {
            p_data->datalen = outlen;
            memcpy(&p_data->databuffer, outbuf, outlen);
        }

        if(0)
        {
            int i;
            unsigned char *p = &p_data->databuffer;
            for(i=0;i<p_data->datalen;i++)
            {
                ALOGD("doTransceive,[LC]%d,[%02x][%c]\n",i,*p,*p);
                p++;
            }
        }

        result = android_nfc_jni_send_msg(MTK_NFC_JNI_TAG_TRANSCEIVE_REQ, len, p_data);

        // Wait for callback response
        if(sem_wait(&cb_data.sem))
        {
            ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
            e->ReleaseByteArrayElements(data, (jbyte *)pnfc_jni_ndef_buf, JNI_ABORT);
            //nfc_cb_data_deinit(&cb_data);
            result = FALSE;
            //return RecBuf;
        }

        if (result == TRUE)
        {
           /* doWrite Status (0: success, 1: fail) */
           if(cb_data.status != 0)
           {
              ALOGE("Failed to doTransceive the stack,%d\n",cb_data.status);
              nfc_jni_ndef_buf_len = 0;
              //result = false;
           }
        }
        else
        {
            ALOGE("send MTK_NFC_JNI_TAG_TRANSCEIVE_REQ fail\n");
        }

        if(p_data) free(p_data);
		if(outbuf) free(outbuf);

        if( result == TRUE)
        {
            //ALOGD("doTransceive2,len(%d,%d)", nfc_jni_ndef_data.datalen,(unsigned int)e->GetArrayLength(data));
            if((nat->au4TechList[0] == TARGET_TYPE_FELICA) && (!dta_nfc_jni_get_dta_mode()))
            {
                for(intI=nfc_jni_ndef_buf_len; intI>0; intI--)   //shift  1 byte right-hande side
                {
                    nfc_jni_ndef_buf[intI] = nfc_jni_ndef_buf[intI-1];
                }

                nfc_jni_ndef_buf_len ++;
                nfc_jni_ndef_buf[0] = nfc_jni_ndef_buf_len;

                ALOGD("felica add len at byte 0 [0x%0X]", nfc_jni_ndef_buf_len);
            }

            pnfc_jni_ndef_buf_len = nfc_jni_ndef_buf_len;

            pnfc_jni_ndef_buf = nfc_jni_ndef_buf;

           // memcpy(nfc_jni_ndef_buf, nfc_jni_ndef_buf, nfc_jni_ndef_data.datalen);

            sTransceiveDataLen = pnfc_jni_ndef_buf_len;

            sTransceiveData = pnfc_jni_ndef_buf;

            ALOGD("doTransceive,len(%d,%d)", sTransceiveDataLen,(unsigned int)e->GetArrayLength(data));
            if(0)
            {
                int i;
                unsigned char *p = sTransceiveData;
                for(i=0;i<sTransceiveDataLen;i++)
                {
                    ALOGD("doTransceive,%d,[%02x][%c]\n",i,*p,*p);
                    p++;
                }
            }

            if ((sTransceiveDataLen) > 0)
            {
               //ALOGD("sTransceiveDataLen_a,%d",(sTransceiveDataLen-2));

#if 0
               if (sTransceiveDataLen > 0x0A)
               {
                   int length = sTransceiveDataLen;
                   sTransceiveDataLen = 2;
                   RecBuf = e->NewByteArray(sTransceiveDataLen);
                   if (RecBuf != NULL)
                   {
                       e->SetByteArrayRegion (RecBuf, 0, (sTransceiveDataLen), (jbyte *) (sTransceiveData-length+2));
                       ALOGD("sTransceiveDataLen_a,%d",(sTransceiveDataLen));
                   }
                   //free(sTransceiveData);
                   sTransceiveData = NULL;
                   sTransceiveDataLen = 0;
               }
               else
               {
                   int length = sTransceiveDataLen;
                   sTransceiveDataLen = 2;
                   RecBuf = e->NewByteArray(sTransceiveDataLen);
                   if (RecBuf != NULL)
                   {
                       e->SetByteArrayRegion (RecBuf, 0, (sTransceiveDataLen), (jbyte *) (sTransceiveData-length+2));
                       ALOGD("sTransceiveDataLen_a,%d",(sTransceiveDataLen));
                   }
                   //free(sTransceiveData);
                   sTransceiveData = NULL;
                   sTransceiveDataLen = 0;
               }
#else
                ALOGD("checkResponseCrc,%d,sTransceiveDataLen,%d",checkResponseCrc,sTransceiveDataLen);
                if(checkResponseCrc)
                {
                    if(sTransceiveDataLen < 2 )
                    {
                        result = false;
                        ALOGD("ERROR");
                    }
                }

                if((sTransceiveDataLen < 2) && (nat->rDevActivateParam.u1Protocol == NFC_DEV_PROTOCOL_T2T))
                {
                    if(sTransceiveData[0]==0x00 || sTransceiveData[0]==0x01 || sTransceiveData[0]==0x04 || sTransceiveData[0]==0x05)
                    {
                        result = false;
                        ALOGD("ERROR");
                    }
                    else
                    {
                        ;//keep states
                    }
                }
                else if(sTransceiveDataLen < 1) //change from 2 for 15693
                {
                    result = false;
                    ALOGD("ERROR");
                }
                else
                {
                    ;//keep states
                }

                if((result != false) && checkResponseCrc)
                {
                    if(crc_valid(pnfc_jni_ndef_buf,sTransceiveData,sTransceiveDataLen))
                    {

                        RecBuf = e->NewByteArray(sTransceiveDataLen - 2);
                        if ((RecBuf != NULL))
                        {
                            e->SetByteArrayRegion (RecBuf, 0, (sTransceiveDataLen - 2), (jbyte *) (sTransceiveData));
                            ALOGD("sTransceiveDataLen_a,%d",(sTransceiveDataLen));
                        }
                        else
                        {
                            ALOGE("RecBuf is NULL");
                        }
                    }
                    else
                    {
                        ALOGE("sTransceiveData invalid CRC");
                    }
               }
               else if(result != false)
               {
                    RecBuf = e->NewByteArray(sTransceiveDataLen);
                    if ((RecBuf != NULL))
                    {
                        e->SetByteArrayRegion (RecBuf, 0, (sTransceiveDataLen), (jbyte *) (sTransceiveData));
                        ALOGD("sTransceiveDataLen_a,%d",(sTransceiveDataLen));
                    }
               }
               else
               {
                    //do nothing
                    ALOGD("sTransceiveDataLen_0");
                    RecBuf = NULL;
               }
               //free(sTransceiveData);
               sTransceiveData = NULL;
               sTransceiveDataLen = 0;
#endif

           }
           else
           {
                ALOGD("sTransceiveDataLen_0");
                RecBuf = NULL;
           }

           e->ReleaseByteArrayElements (data, (jbyte *) pnfc_jni_ndef_buf, JNI_ABORT);

           if (targetLost != NULL)
           {
               e->ReleaseIntArrayElements(statusTargetLost, targetLost, 0);
           }
        }
        else
        {
            ALOGE("result == FALSE");
        }

        nfc_cb_data_deinit(&cb_data);
        g_working_cb_data_tag = NULL;

    }
    else
    {
        ALOGE("Failed to create semaphore (errno=0x%08x)\n", errno);
    }

   CONCURRENCY_UNLOCK();
   ALOGE("RecBuf(%x)\n", RecBuf);
   return RecBuf;
}

static jint com_android_nfc_NativeNfcTag_doGetNdefType(JNIEnv *e, jobject o,
        jint libnfcType, jint javaType)
{
    jint ndefType = NDEF_UNKNOWN_TYPE;

    ALOGD("com_android_nfc_NativeNfcTag_doGetNdefType(%d)(%d)",libnfcType, javaType);

    switch (libnfcType)
    {
        case NFC_DEV_PROTOCOL_T1T:
            ndefType = NDEF_TYPE1_TAG;
            break;
        case NFC_DEV_PROTOCOL_T2T:
            ndefType = NDEF_TYPE2_TAG;;
            break;
        case NFC_DEV_PROTOCOL_T3T:
            ndefType = NDEF_TYPE3_TAG;
            break;
        case NFC_DEV_PROTOCOL_ISO_DEP:
            ndefType = NDEF_TYPE4_TAG;
            break;
        case NFC_DEV_PROTOCOL_15693:
            ndefType = NDEF_ICODE_SLI_TAG;
            break;
        default:
            ndefType = NDEF_UNKNOWN_TYPE;
            break;
    }

    ALOGD ("ndef_type(%d)",ndefType);
    return ndefType;

}

static jint com_android_nfc_NativeNfcTag_doCheckNdef(JNIEnv *e, jobject o, jintArray ndefinfo)
{
   int result = FALSE;
   mtk_nfc_CkNdef_CB_t cb_NdefInfo;
   jint *ndef = e->GetIntArrayElements(ndefinfo, 0);
   int apiCardState = NDEF_MODE_UNKNOWN;

   struct nfc_jni_native_data *nat = NULL;
   struct nfc_jni_callback_data cb_data;

   /* Retrieve native structure address */
   nat = nfc_jni_get_nat_ext(e);

   ALOGD("com_android_nfc_NativeNfcTag_doCheckNdef()");

   if(nat)
   {
   ALOGD("u1Protocol[%d],[%d]",
         nat->rDevActivateParam.u1Protocol,
         nat->rDevTagParam.u1TagType);

   //nat->rDevTagParam.u1TagType = TAG_INFOTYPE4; // TDB
   ALOGD("u1DevActivated,%d",nat->u1DevActivated);
   if(nat->u1DevActivated != 2)
   {
       return result;
   }
   }
   else
   {
       ALOGD("NULL NAT");
       return result;
   }
   CONCURRENCY_LOCK();
   /* Create the local semaphore */
   if (nfc_cb_data_init(&cb_data, NULL))
   {
      s_mtk_nfc_service_tag_param_t tag_info;

      memcpy (&tag_info, &(nat->rDevTagParam), sizeof(s_mtk_nfc_service_tag_param_t));


      if (nat->rDevActivateParam.u1Protocol == NFC_DEV_PROTOCOL_T1T) {tag_info.u1TagType = TAG_INFOTYPE1;}
      else if (nat->rDevActivateParam.u1Protocol == NFC_DEV_PROTOCOL_T2T)
      {
         int i;
         tag_info.u1TagType = TAG_INFOTYPE2;
	       for(i=0;i<nat->u4NumDevTech;i++)
	       {
            ALOGD("LC-JNI,i(%d),au4TechList(%d)",i,nat->au4TechList[i]);
	          if(nat->au4TechList[i] == TARGET_TYPE_MIFARE_CLASSIC)
	          {
	             tag_info.u1TagType = TAG_INFOTYPE_MIFARE_CLASSIC;
	             break;
	          }
         }
      }
      else if (nat->rDevActivateParam.u1Protocol == NFC_DEV_PROTOCOL_T3T) {tag_info.u1TagType = TAG_INFOTYPE3;}
      else if (nat->rDevActivateParam.u1Protocol == NFC_DEV_PROTOCOL_ISO_DEP) {tag_info.u1TagType = TAG_INFOTYPE4;}
      else if (nat->rDevActivateParam.u1Protocol == NFC_DEV_PROTOCOL_15693) {tag_info.u1TagType = TAG_INFOTYPEV;}
      else if (nat->rDevActivateParam.u1Protocol == NFC_DEV_PROTOCOL_KOVIO) {tag_info.u1TagType = TAG_INFOTYPEK;}
      else  {tag_info.u1TagType = TAG_INFOTYPE_UNKNOWN;}

      ALOGD("tag_info[%d]", tag_info.u1TagType);

      // assign working callback data pointer
      cb_data.pContext = &cb_NdefInfo;
      g_working_cb_data_tag = &cb_data;

      // send check ndef req msg to nfc daemon
      result = android_nfc_jni_send_msg(MTK_NFC_JNI_CHECK_NDEF_REQ, sizeof(s_mtk_nfc_service_tag_param_t), &tag_info);

      // Wait for callback response
      if(sem_wait(&cb_data.sem))
      {
          ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
          //nfc_cb_data_deinit(&cb_data);
          result = FALSE;
          //return FALSE;
      }

      if (result == TRUE)
      {
         /* check ndef Status (0: success, 1: fail) */
         if(cb_data.status != 0)
         {
            ALOGE("Failed to check ndef the stack\n");
            if (cb_data.status == 1){result = FALSE;}
            else if(cb_data.status == 0x20){result = TRUE;} //NFC_STATUS_INVALID_NDEF_FORMAT
            else{result = cb_data.status;}
         }
      }
      else
      {
          ALOGE("send MTK_NFC_CHECK_NDEF_REQ fail\n");
          result = FALSE;
      }

      if(result == TRUE)
      {
         ndef[0] = cb_NdefInfo.MaxNdefMsgLength;
         // Translate the card state to know values for the NFC API
         switch (cb_NdefInfo.Cardinformation)
         {
             case MTK_NFC_TAG_LCSM_INITIALIZE:
                 apiCardState = NDEF_MODE_READ_WRITE;
                 break;
             case MTK_NFC_TAG_LCSM_READONLY:
                 apiCardState = NDEF_MODE_READ_ONLY;
                 break;
             case MTK_NFC_TAG_LCSM_READWRITE:
                 apiCardState = NDEF_MODE_READ_WRITE;
                 break;
             case MTK_NFC_TAG_LCSM_INVAILD:
                 apiCardState = NDEF_MODE_UNKNOWN;
                 break;
             default:
                 apiCardState = NDEF_MODE_UNKNOWN;
                 break;
         }
         ndef[1] = apiCardState;

         ALOGE("apiCardState,%d,%d",ndef[0],ndef[1]);

         e->ReleaseIntArrayElements(ndefinfo, ndef, 0);
         // Allocate memory for access Tag.
         if(g_working_cb_data_tag->status == MTK_NFC_SUCCESS)
         {
            if(NULL != nfc_jni_ndef_buf)
            {
               free(nfc_jni_ndef_buf);
               nfc_jni_ndef_buf = NULL;
            }
            if (cb_NdefInfo.MaxNdefMsgLength == 0)
            {
                nfc_jni_ndef_buf_len = 0xFF;
                nfc_jni_ndef_buf = (uint8_t*)malloc(nfc_jni_ndef_buf_len);
                nfc_jni_ndef_buf_size = nfc_jni_ndef_buf_len;
                ALOGE("Check NDEF Done, WARNING LENGTH is ZERO,Automatic allomate 0xFF bytes for Tag operation!!");
            }
            else
            {
                nfc_jni_ndef_buf_len = cb_NdefInfo.MaxNdefMsgLength;
                nfc_jni_ndef_buf = (uint8_t*)malloc(nfc_jni_ndef_buf_len);
                nfc_jni_ndef_buf_size = nfc_jni_ndef_buf_len;
            }
            ALOGE("Check NDEF Done, len(%d)", nfc_jni_ndef_buf_len);
            result = 0x00;
         }
         else
         {
            ALOGD("Failed to check NDEF");
            result = 0x01;
         }

      }
      else
      {
         ALOGE("apiCardState,%d,%d",ndef[0],ndef[1]);

         e->ReleaseIntArrayElements(ndefinfo, ndef, 0);
      }
      // clear working callback data pointer
      g_working_cb_data_tag = NULL;

      nfc_cb_data_deinit(&cb_data);
   }
   else
   {
       ALOGE("Failed to create semaphore (errno=0x%08x)\n", errno);
   }

   if(nfc_jni_ndef_buf == NULL)
   {
      nfc_jni_ndef_buf_len = 0xFF;
      nfc_jni_ndef_buf = (uint8_t*)malloc(nfc_jni_ndef_buf_len);
      nfc_jni_ndef_buf_size = nfc_jni_ndef_buf_len;
      ALOGE("doCheckNdef, nfc_jni_ndef_buf_len(%d)",nfc_jni_ndef_buf_len);
   }

   CONCURRENCY_UNLOCK();
   return result;
}


static jboolean com_android_nfc_NativeNfcTag_doPresenceCheck(JNIEnv *e, jobject o)
{
   int result = FALSE;
   jboolean status =JNI_TRUE;
   struct nfc_jni_callback_data cb_data;
   struct nfc_jni_native_data *nat = NULL;

   ALOGD("com_android_nfc_NativeNfcTag_doPresenceCheck()");

   nat = nfc_jni_get_nat_ext(e);

   if(nat)
   {
   ALOGD("u1DevActivated,%d",nat->u1DevActivated);
   if(nat->u1DevActivated != 2)
   {
       return result;
   }
   }
   else
   {
       ALOGD("NULL NAT");
       return result;
   }

   CONCURRENCY_LOCK();

   #if 0
   return JNI_TRUE;
   #else

   /* Create the local semaphore */
   if (nfc_cb_data_init(&cb_data, NULL))
   {
      // assign working callback data pointer
      g_working_cb_data_tag = &cb_data;

      // send doRead req msg to nfc daemon
      result = android_nfc_jni_send_msg(MTK_NFC_JNI_TAG_PRESENCE_CK_REQ, 0, NULL);

      // Wait for callback response
      if(sem_wait(&cb_data.sem))
      {
          ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
          //nfc_cb_data_deinit(&cb_data);
          //g_working_cb_data_tag = NULL;
          result = FALSE;
          //return JNI_FALSE;
      }

      ALOGE("doPresenceCheck(%d,%d)\n",result, cb_data.status);
      if (result == TRUE)
      {
         /* doWrite Status (0: success, 1: fail) */
         if(cb_data.status != 0)
         {
            ALOGE("Failed to doPresenceCheck the stack\n");
            status = JNI_FALSE;
         }
         else
         {
            ;//
         }
      }
      else
      {
          ALOGE("send MTK_NFC_JNI_TAG_PRESENCE_CK_REQ fail\n");
      }

      // clear working callback data pointer
      g_working_cb_data_tag = NULL;

      nfc_cb_data_deinit(&cb_data);
   }
   else
   {
       ALOGE("Failed to create semaphore (errno=0x%08x)\n", errno);
   }

   CONCURRENCY_UNLOCK();
   ALOGE("CheckProesence,Result(%d)\n", status);
   return status;//JNI_TRUE;
   #endif
}


static jboolean com_android_nfc_NativeNfcTag_doIsIsoDepNdefFormatable(JNIEnv *e,
        jobject o, jbyteArray pollBytes, jbyteArray actBytes)
{

    struct nfc_jni_native_data *nat = NULL;
    jboolean issoformatable = JNI_FALSE;
    ALOGD("com_android_nfc_NativeNfcTag_doIsIsoDepNdefFormatable()");

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat_ext(e);//nfc_jni_get_nat (e, o);

    if (nat)
    {

        switch (nat->rDevActivateParam.u1Protocol)
        {
            case NFC_DEV_PROTOCOL_T1T:
            case NFC_DEV_PROTOCOL_T2T:
            case NFC_DEV_PROTOCOL_T3T:
            case NFC_DEV_PROTOCOL_ISO_DEP:
            case NFC_DEV_PROTOCOL_NFC_DEP:
            case NFC_DEV_PROTOCOL_15693:
            case NFC_DEV_PROTOCOL_BPRIME:
            case NFC_DEV_PROTOCOL_KOVIO:
                ALOGD("[issoformatable][%d]",nat->rDevActivateParam.u1Protocol);
                issoformatable = JNI_TRUE;
                break;
            default:
                break;
        }
    }
    else
    {
        ALOGD("NULL NAT");
        return JNI_FALSE;
    }

    return issoformatable;

}

static jboolean com_android_nfc_NativeNfcTag_doNdefFormat(JNIEnv *e, jobject o, jbyteArray key)
{
   int result = FALSE;
   struct nfc_jni_callback_data cb_data;
   struct nfc_jni_native_data *nat = NULL;

   ALOGD("com_android_nfc_NativeNfcTag_doNdefFormat()");


   nat = nfc_jni_get_nat_ext(e);

   if(nat)
   {
   ALOGD("u1DevActivated,%d",nat->u1DevActivated);
   if(nat->u1DevActivated != 2)
   {
       return result;
   }
   }
   else
   {
       ALOGD("NULL NAT");
       return result;
   }

   CONCURRENCY_LOCK();
   /* Create the local semaphore */
   if (nfc_cb_data_init(&cb_data, NULL))
   {
      // assign working callback data pointer
      g_working_cb_data_tag = &cb_data;

      // send doRead req msg to nfc daemon
      result = android_nfc_jni_send_msg(MTK_NFC_JNI_TAG_FORMAT_REQ, 0, NULL);

      // Wait for callback response
      if(sem_wait(&cb_data.sem))
      {
          ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
          //nfc_cb_data_deinit(&cb_data);
          result = FALSE;
          //return JNI_FALSE;
      }

      if (result == TRUE)
      {
         /* doNdefFormat Status (0: success, 1: fail) */
         if(cb_data.status != 0)
         {
            ALOGE("Failed to doNdefFormat the stack\n");
         }
      }
      else
      {
          ALOGE("send MTK_NFC_JNI_TAG_PRESENCE_CK_REQ fail\n");
      }

      // clear working callback data pointer
      g_working_cb_data_tag = NULL;

      nfc_cb_data_deinit(&cb_data);
   }
   else
   {
       ALOGE("Failed to create semaphore (errno=0x%08x)\n", errno);
   }

   CONCURRENCY_UNLOCK();
   return JNI_TRUE;
}


static jboolean com_android_nfc_NativeNfcTag_doMakeReadonly(JNIEnv *e, jobject o, jbyteArray key)
{
   int result = FALSE;
   struct nfc_jni_callback_data cb_data;

   ALOGD("com_android_nfc_NativeNfcTag_doMakeReadonly()");

   CONCURRENCY_LOCK();
   /* Create the local semaphore */
   if (nfc_cb_data_init(&cb_data, NULL))
   {
      // assign working callback data pointer
      g_working_cb_data_tag = &cb_data;

      // send doRead req msg to nfc daemon
      result = android_nfc_jni_send_msg(MTK_NFC_JNI_TAG_MAKEREADONLY_REQ, 0, NULL);

      // Wait for callback response
      if(sem_wait(&cb_data.sem))
      {
          ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
          //nfc_cb_data_deinit(&cb_data);
          result = FALSE;
          //return JNI_FALSE;
      }

      if (result == TRUE)
      {
         /* doNdefFormat Status (0: success, 1: fail) */
         if(cb_data.status != 0)
         {
            ALOGE("Failed to doMakeReadonly the stack\n");
         }
      }
      else
      {
          ALOGE("send MTK_NFC_JNI_TAG_MAKEREADONLY_REQ fail\n");
      }

      // clear working callback data pointer
      g_working_cb_data_tag = NULL;

      nfc_cb_data_deinit(&cb_data);
   }
   else
   {
       ALOGE("Failed to create semaphore (errno=0x%08x)\n", errno);
   }

   CONCURRENCY_UNLOCK();
   return JNI_TRUE;
}


/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] =
{
   {"doConnect", "(I)I",
      (void *)com_android_nfc_NativeNfcTag_doConnect},
   {"doDisconnect", "()Z",
      (void *)com_android_nfc_NativeNfcTag_doDisconnect},
   {"doReconnect", "()I",
      (void *)com_android_nfc_NativeNfcTag_doReconnect},
   {"doHandleReconnect", "(I)I",
      (void *)com_android_nfc_NativeNfcTag_doHandleReconnect},
   {"doTransceive", "([BZ[I)[B",
      (void *)com_android_nfc_NativeNfcTag_doTransceive},
   {"doGetNdefType", "(II)I",
      (void *)com_android_nfc_NativeNfcTag_doGetNdefType},
   {"doCheckNdef", "([I)I",
      (void *)com_android_nfc_NativeNfcTag_doCheckNdef},
   {"doRead", "()[B",
      (void *)com_android_nfc_NativeNfcTag_doRead},
   {"doWrite", "([B)Z",
      (void *)com_android_nfc_NativeNfcTag_doWrite},
   {"doPresenceCheck", "()Z",
      (void *)com_android_nfc_NativeNfcTag_doPresenceCheck},
   {"doIsIsoDepNdefFormatable", "([B[B)Z",
      (void *)com_android_nfc_NativeNfcTag_doIsIsoDepNdefFormatable},
   {"doNdefFormat", "([B)Z",
      (void *)com_android_nfc_NativeNfcTag_doNdefFormat},
   {"doMakeReadonly", "([B)Z",
      (void *)com_android_nfc_NativeNfcTag_doMakeReadonly},
};

int register_com_android_nfc_NativeNfcTag(JNIEnv *e)
{
   return jniRegisterNativeMethods(e,
      "com/android/nfc/dhimpl/NativeNfcTag",
      gMethods, NELEM(gMethods));
}

} // namespace android
