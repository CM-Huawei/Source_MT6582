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


//extern struct nfc_jni_callback_data *g_working_cb_data;
extern struct nfc_jni_callback_data* g_llcp_cb_data[NFC_LLCP_SERVICE_NUM][NFC_LLCP_ACT_END];
extern unsigned int  g_llcp_service[NFC_LLCP_SERVICE_NUM];
extern unsigned char g_llcp_sertype[NFC_LLCP_SERVICE_NUM];
extern unsigned char g_idx_service[NFC_LLCP_ACT_END];
extern unsigned int g_llcp_remiu[NFC_LLCP_SERVICE_NUM];
extern unsigned char g_llcp_remrw[NFC_LLCP_SERVICE_NUM];
extern unsigned char device_connected_flag;

//struct nfc_jni_callback_data g_cb_llcpless_data;
extern unsigned char* sReceiveBuffer[NFC_LLCP_SERVICE_NUM];
extern unsigned int sReceiveLength[NFC_LLCP_SERVICE_NUM];


namespace android {

/*
 * Callbacks
 */

/*
* Methods
*/
static jboolean com_android_nfc_NativeLlcpConnectionlessSocket_doSendTo(JNIEnv *e, jobject o, jint nsap, jbyteArray data)
{
   int ret;
   unsigned char idx = 0;
   struct timespec ts;
   unsigned int hRemoteDevice;
   unsigned int hLlcpSocket;
   unsigned char* sSendBuffer = NULL;
   unsigned int sSendLength = 0;
   unsigned int TotalLength = 0;
   struct nfc_jni_callback_data cb_data;
   jboolean result = JNI_FALSE;
   MTK_NFC_LLCP_SEND_DATA* pData = NULL;      


   /* Retrieve handles */
   hRemoteDevice = nfc_jni_get_p2p_device_handle(e,o);
   hLlcpSocket = nfc_jni_get_nfc_socket_handle(e,o);

   if ((hLlcpSocket != 0)&&(device_connected_flag == 1))
   {

       CONCURRENCY_LOCK();
       for (idx = 0; idx < NFC_LLCP_SERVICE_NUM ; idx++)
       {
           if (hLlcpSocket == g_llcp_service[idx])
           {
               g_idx_service[NFC_LLCP_SEND] = idx;
               break;
           }
       }
       ALOGD("idx,%d,handle,0x%x,0x%x", g_idx_service[NFC_LLCP_SEND], g_llcp_service[g_idx_service[NFC_LLCP_SEND]], hLlcpSocket);

       if (g_idx_service[NFC_LLCP_SEND] < NFC_LLCP_SERVICE_NUM)
       {
           /* Create the local semaphore */
           if (!nfc_cb_data_init(&cb_data, NULL))
           {
              goto clean_and_return;
           }
        
           g_llcp_cb_data[g_idx_service[NFC_LLCP_SEND]][NFC_LLCP_SEND] = &cb_data;
        
           sSendBuffer = (unsigned char*)e->GetByteArrayElements(data, NULL);
           sSendLength = (unsigned int)e->GetArrayLength(data);   
        
           if (sSendBuffer != NULL)
           {
    
              char* DbgBuf = NULL;
              unsigned int Index = 0, DbgLth = 0;
    
              DbgBuf = (char*)malloc(sSendLength*10);
              memset(DbgBuf, 0x00, sSendLength*10);
    
              TotalLength = sizeof(MTK_NFC_LLCP_SEND_DATA) + sSendLength;
              pData = (MTK_NFC_LLCP_SEND_DATA*)malloc(TotalLength);
              memset(pData, 0x00, TotalLength);
              pData->remote_dev_handle= hRemoteDevice;
              pData->service_handle = hLlcpSocket;
              pData->sap = (unsigned char)nsap;
              pData->llcp_data_send_buf.length = sSendLength;
              memcpy(pData->llcp_data_send_buf.buffer, sSendBuffer, sSendLength);
    
              if (DbgBuf != NULL)
              {
                  for (Index = 0; (Index < sSendLength) && (DbgBuf+DbgLth != NULL); Index++)
                  {
                      sprintf(DbgBuf+DbgLth, "0x%02x,", pData->llcp_data_send_buf.buffer[Index]);
                      DbgLth = strlen(DbgBuf);
                      if (((Index+1)%16) == 0)
                      {
                          sprintf(DbgBuf+DbgLth, "\n");
                      }
                      DbgLth = strlen(DbgBuf);                  
                  }
                  ALOGD("Raw Data,%d \n%s", sSendLength, DbgBuf);
                  free(DbgBuf);
              }
                
           }
           else
           {
              goto clean_and_return;
           }
        
           ALOGD("com_android_nfc_NativeLlcpConnectionlessSocket_doSendTo(),SendCmd");
            //MTK_NFC_P2P_SEND_DATA_REQ
           ret = android_nfc_jni_send_msg(MTK_NFC_P2P_SEND_DATA_REQ, TotalLength, (void *)pData);         
           if (pData != NULL)
           {
               free(pData);
           }
            if (ret == FALSE)
            {
                ALOGE("send doSendTo cmd fail\n");
                result = JNI_FALSE;
                goto clean_and_return;
            }               
           // wait MTK_NFC_P2P_SEND_DATA_RSP
           if (sem_wait(&g_llcp_cb_data[g_idx_service[NFC_LLCP_SEND]][NFC_LLCP_SEND]->sem)) {
        	   ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        	   goto clean_and_return;
           }   	
        
           ret = g_llcp_cb_data[g_idx_service[NFC_LLCP_SEND]][NFC_LLCP_SEND]->status;   
           if(ret != 0)
           {
              result = JNI_FALSE;
           }
           else
           {
              result = JNI_TRUE;
           }
           ALOGD("com_android_nfc_NativeLlcpConnectionlessSocket_doSendTo returned 0x%04x", ret);
    
        clean_and_return:
           if (sSendBuffer != NULL)
           {
              e->ReleaseByteArrayElements(data, (jbyte*)sSendBuffer, JNI_ABORT);
           }
           nfc_cb_data_deinit(&cb_data);
           g_llcp_cb_data[g_idx_service[NFC_LLCP_SEND]][NFC_LLCP_SEND] = NULL;
       }
       else
       {
           ALOGE("hLlcpSocket not find");
           result = JNI_FALSE;
       }
       CONCURRENCY_UNLOCK();       
   }
   else
   {
       ALOGE("hLlcpSocket is not ready");
       result = JNI_FALSE;
   }

   return result;
}

static jobject com_android_nfc_NativeLlcpConnectionlessSocket_doReceiveFrom(JNIEnv *e, jobject o, jint linkMiu)
{
   int ret;
   struct timespec ts;
   unsigned char ssap, idx = 0;
   jobject llcpPacket = NULL;
   unsigned int hRemoteDevice;
   unsigned int hLlcpSocket;
   unsigned int TotalLength = 0;
   jclass clsLlcpPacket;
   jfieldID f;
   jbyteArray receivedData = NULL;
   struct nfc_jni_callback_data cb_data;
   unsigned char CurrentIdx = 0;
   MTK_NFC_LLCP_SOKCET Socket; //hRemoteDevice, hLlcpSocket

   ALOGD("com_android_nfc_NativeLlcpConnectionlessSocket_doReceiveFrom");



   /* Retrieve handles */
   hRemoteDevice = nfc_jni_get_p2p_device_handle(e,o);
   hLlcpSocket = nfc_jni_get_nfc_socket_handle(e,o);

   if ((hLlcpSocket != 0)&&(device_connected_flag == 1))
   {
       for (idx = 0; idx < NFC_LLCP_SERVICE_NUM ; idx++)
       {
           if (hLlcpSocket == g_llcp_service[idx])
           {
               CurrentIdx = idx;
               break;
           }
       }
       ALOGD("idx,%d,handle,0x%x,0x%x", CurrentIdx, g_llcp_service[CurrentIdx], hLlcpSocket);

       if (CurrentIdx < NFC_LLCP_SERVICE_NUM)
       {
           /* Create the local semaphore */
           if (!nfc_cb_data_init(&cb_data, NULL))
           {
              goto clean_and_return;
           }
        
           /* Create new LlcpPacket object */
           if(nfc_jni_cache_object(e,"com/android/nfc/LlcpPacket",&(llcpPacket)) == -1)
           {
              ALOGE("Find LlcpPacket class error");
              goto clean_and_return;
           }
        
           /* Get NativeConnectionless class object */
           clsLlcpPacket = e->GetObjectClass(llcpPacket);
           if(e->ExceptionCheck())
           {
              ALOGE("Get Object class error");
              goto clean_and_return;
           } 
        
        
           TRACE("phLibNfc_Llcp_RecvFrom(), Socket Handle = 0x%02x, Link LIU = %d", hLlcpSocket, linkMiu);
        
           TotalLength = sizeof(MTK_NFC_LLCP_SOKCET);
           Socket.llcp_service_handle = hLlcpSocket;
           Socket.remote_dev_handle = hRemoteDevice;  
           
           g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]  = &cb_data;
           g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]->pContext = malloc(sizeof(unsigned char));
           sReceiveBuffer[CurrentIdx] = (unsigned char*)malloc(linkMiu);
           sReceiveLength[CurrentIdx] = linkMiu;

        // MTK_NFC_P2P_RECV_DATA_REQ
           ret = android_nfc_jni_send_msg(MTK_NFC_P2P_RECV_DATA_REQ, TotalLength, (void *)&Socket);
        
         // wait MTK_NFC_P2P_RECV_DATA_RSP
           if (ret == FALSE)
           {
               ALOGE("send doReceiveFrom cmd fail\n");
               llcpPacket = NULL;
               goto clean_and_return;
           }  
           for (idx = 0; idx < NFC_LLCP_SERVICE_NUM ; idx++)
           {
              if (hLlcpSocket == g_llcp_service[idx])
              {
                  CurrentIdx = idx;
                  break;
              }
           }
           ALOGD("Wait,%d", CurrentIdx);                            
           if (sem_wait(&g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]->sem)) {
               ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
               goto clean_and_return;
           }  

           ret = g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]->status;

           TRACE("com_android_nfc_NativeLlcpConnectionlessSocket_doReceiveFrom,%d,returned 0x%04x", CurrentIdx, ret);
           if(ret != 0 )
           {
              ALOGE("com_android_nfc_NativeLlcpConnectionlessSocket_doReceiveFrom Err");
              goto clean_and_return;
           } 
         
           ssap = *(unsigned char*)g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]->pContext;
           TRACE("Data Received From SSAP = %d\n, length = %d", ssap,sReceiveLength[CurrentIdx] );
        
           /* Set Llcp Packet remote SAP */
           f = e->GetFieldID(clsLlcpPacket, "mRemoteSap", "I");
           e->SetIntField(llcpPacket, f,(jbyte)ssap);
        
           /* Set Llcp Packet Buffer */
           ALOGD("Set LlcpPacket Data Buffer\n");
           f = e->GetFieldID(clsLlcpPacket, "mDataBuffer", "[B");
           receivedData = e->NewByteArray(sReceiveLength[CurrentIdx]);
           e->SetByteArrayRegion(receivedData, 0, sReceiveLength[CurrentIdx],(jbyte *)sReceiveBuffer[CurrentIdx]);
           e->SetObjectField(llcpPacket, f, receivedData);
        
        clean_and_return:
           if (g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]->pContext != NULL)
           {
              free(g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]->pContext);
           }               
           nfc_cb_data_deinit(&cb_data);
           if (sReceiveBuffer[CurrentIdx] != NULL)
           {
               free(sReceiveBuffer[CurrentIdx]);
           }
           g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]= NULL;
       }
       else
       {
           ALOGE("hLlcpSocket not find");
           llcpPacket = NULL;
       }
   }
   else
   {
       ALOGE("hLlcpSocket is not ready");
       llcpPacket = NULL;
   }
   return llcpPacket;
}

static jboolean com_android_nfc_NativeLlcpConnectionlessSocket_doClose(JNIEnv *e, jobject o)
{
   int ret = 0;
   unsigned char idx =0;
   unsigned int hLlcpSocket = 0;
   unsigned int TotalLength = 0;
   struct nfc_jni_callback_data cb_data;
   jboolean result = TRUE;
 
   unsigned char CurrentIdx = 0; 
   
   ALOGD("Close Connectionless socket");


   /* Retrieve socket handle */
   hLlcpSocket = nfc_jni_get_nfc_socket_handle(e,o);

   if (hLlcpSocket != 0)
   {
       CONCURRENCY_LOCK(); 

       for (idx = 0; idx < NFC_LLCP_SERVICE_NUM ; idx++)
       {
           if (hLlcpSocket == g_llcp_service[idx])
           {
               CurrentIdx = idx;
               break;
           }
       }
       ALOGD("idx,%d,handle,0x%x,0x%x", CurrentIdx , g_llcp_service[CurrentIdx], hLlcpSocket);

       if (CurrentIdx < NFC_LLCP_SERVICE_NUM)
       {   
           TotalLength = sizeof(unsigned int);
        
           if (!nfc_cb_data_init(&cb_data, NULL))
           {
        	  result = FALSE;
        	  goto clean_and_return;
           }
            g_llcp_cb_data[CurrentIdx][NFC_LLCP_CLOSE] = &cb_data;
            g_idx_service[NFC_LLCP_CLOSE] = CurrentIdx;        
           ALOGD("com_android_nfc_NativeLlcpConnectionlessSocket_doClose");
    

           // MTK_NFC_P2P_DISC_REQ
           ret = android_nfc_jni_send_msg(MTK_NFC_P2P_DISC_REQ, TotalLength, (void *)&hLlcpSocket); 

           // wait MTK_NFC_P2P_DISC_RSP
           if (ret == FALSE)
           {
               ALOGE("send ConnectionlessSocket_doClose cmd fail\n");
               result = JNI_FALSE;
               goto clean_and_return;
           }             
           if (sem_wait(& g_llcp_cb_data[CurrentIdx][NFC_LLCP_CLOSE]->sem)) 
           {
               ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
               result = FALSE;
               goto clean_and_return;
           }    
           ret =  g_llcp_cb_data[CurrentIdx][NFC_LLCP_CLOSE]->status;
           if(ret == 0)
           {
              result = TRUE;
           }
           else
           {
              result = FALSE;
           }
           ALOGD("com_android_nfc_NativeLlcpConnectionlessSocket_doClose returned 0x%04x,%d", ret, result);
        clean_and_return:	
           nfc_cb_data_deinit(&cb_data);
 
           g_llcp_remrw[CurrentIdx] = 0; 
           g_llcp_remiu[CurrentIdx] = 0; 

           g_llcp_service[CurrentIdx] = 0;
           g_llcp_sertype[CurrentIdx] = 0;
           g_llcp_cb_data[CurrentIdx][NFC_LLCP_CLOSE] = NULL;       
       }
       else
       {
           ALOGE("hLlcpSocket not find");
           result = FALSE;
       }
       CONCURRENCY_UNLOCK(); 
   }
   else
   {
	   ALOGE("hLlcpSocket is not ready");
	   result = FALSE;
   }

   return result;  
}


/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] =
{
   {"doSendTo", "(I[B)Z", (void *)com_android_nfc_NativeLlcpConnectionlessSocket_doSendTo},
      
   {"doReceiveFrom", "(I)Lcom/android/nfc/LlcpPacket;", (void *)com_android_nfc_NativeLlcpConnectionlessSocket_doReceiveFrom},
      
   {"doClose", "()Z", (void *)com_android_nfc_NativeLlcpConnectionlessSocket_doClose},
};


int register_com_android_nfc_NativeLlcpConnectionlessSocket(JNIEnv *e)
{
   return jniRegisterNativeMethods(e,
      "com/android/nfc/dhimpl/NativeLlcpConnectionlessSocket",
      gMethods, NELEM(gMethods));
}

} // android namespace

