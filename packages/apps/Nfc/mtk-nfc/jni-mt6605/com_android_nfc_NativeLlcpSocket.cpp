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

extern unsigned char* sReceiveBuffer[NFC_LLCP_SERVICE_NUM];
extern unsigned int sReceiveLength[NFC_LLCP_SERVICE_NUM];
extern unsigned char device_connected_flag;

namespace android {

/*
 * Callbacks
 */
 
void nfc_jni_disconnect_callback(MTK_NFC_LLCP_RSP*   Result)
{

   ALOGD("nfc_jni_llcp_disconnect_callback,%d,%d,%x", g_idx_service[NFC_LLCP_CLOSE] ,Result->ret, Result->service_handle);
   /* Report the callback status and wake up the caller */
   if ( (g_idx_service[NFC_LLCP_CLOSE] <NFC_LLCP_SERVICE_NUM) &&( g_llcp_cb_data[g_idx_service[NFC_LLCP_CLOSE]][NFC_LLCP_CLOSE] != NULL))
   { 
       g_llcp_cb_data[g_idx_service[NFC_LLCP_CLOSE]][NFC_LLCP_CLOSE]->status = Result->ret;
       sem_post(&g_llcp_cb_data[g_idx_service[NFC_LLCP_CLOSE]][NFC_LLCP_CLOSE]->sem);
   }
   else
   {
       ALOGD("cb_data NULL");
   }
}

 
void nfc_jni_connect_callback(unsigned char nErrCode, int status)
{
   ALOGD("nfc_jni_llcp_connect_callback,%d,%d",g_idx_service[NFC_LLCP_CONNECT], status);
   // Handle MTK_NFC_P2P_CONNECTION_NTF
   if(status == 0)
   {
      TRACE("Socket connected\n");
      if (g_llcp_cb_data[g_idx_service[NFC_LLCP_CONNECT]][NFC_LLCP_CONNECT] != NULL)
      {
          g_llcp_cb_data[g_idx_service[NFC_LLCP_CONNECT]][NFC_LLCP_CONNECT]->status = status;
          sem_post(&g_llcp_cb_data[g_idx_service[NFC_LLCP_CONNECT]][NFC_LLCP_CONNECT]->sem); 
      }
      else
      {
          ALOGE("> Connect Cb Check\n");
      }
   }
   else
   {
      ALOGD("Socket not connected:");
      switch(nErrCode)
      {
         case MTK_P2P_LLCP_DM_OPCODE_DISCONNECTED:
         {
             ALOGD("> DISC\n");
         }break;
         case MTK_P2P_LLCP_DM_OPCODE_SAP_NOT_ACTIVE:
         {
             ALOGE("> SAP NOT ACTIVE\n");
         }break;

         case MTK_P2P_LLCP_DM_OPCODE_SAP_NOT_FOUND:
         {
             ALOGE("> SAP NOT FOUND\n");
         }break;

         case MTK_P2P_LLCP_DM_OPCODE_CONNECT_REJECTED:
         {
             ALOGD("> CONNECT REJECTED\n");
         }break;

         case MTK_P2P_LLCP_DM_OPCODE_CONNECT_NOT_ACCEPTED:
         {
             ALOGE("> CONNECT NOT ACCEPTED\n");
         }break;

         case MTK_P2P_LLCP_DM_OPCODE_SOCKET_NOT_AVAILABLE:
         {
             ALOGE("> SOCKET NOT AVAILABLE\n");
         }break;
      }

      if (g_llcp_cb_data[g_idx_service[NFC_LLCP_CONNECT]][NFC_LLCP_CONNECT] != NULL)
      {
          g_llcp_cb_data[g_idx_service[NFC_LLCP_CONNECT]][NFC_LLCP_CONNECT]->status = status;
          sem_post(&g_llcp_cb_data[g_idx_service[NFC_LLCP_CONNECT]][NFC_LLCP_CONNECT]->sem); 
      }
   }
    /* Report the callback status and wake up the caller */
} 



 
void nfc_jni_llcp_receive_callback(int  status, unsigned char sap, unsigned char* Buf, unsigned int Length, unsigned int service)
{
   unsigned char idx = 0;
   // Handle

   for (idx = 0; idx < NFC_LLCP_SERVICE_NUM ; idx++)
   {
       if (service == g_llcp_service[idx])
       {
           break;
       }
   }
   ALOGD("nfc_jni_llcp_doReceive_callback,%d,%d,%d,0x%x",idx, status, Length, service);

   // Update sReceiveBuffer/ sReceiveLength
   /* Report the callback status and wake up the caller */ 
   if ( (idx < NFC_LLCP_SERVICE_NUM ) && (g_llcp_cb_data[idx][NFC_LLCP_RECEIVE] != NULL))
   {
       g_llcp_cb_data[idx][NFC_LLCP_RECEIVE]->status = status;
       if (g_llcp_cb_data[idx][NFC_LLCP_RECEIVE]->pContext != NULL)
       {
          memcpy((unsigned char*)g_llcp_cb_data[idx][NFC_LLCP_RECEIVE]->pContext, &sap, sizeof(unsigned char));
       }
       if (status == 0)
       {
          if ((sReceiveBuffer[idx] != NULL) && (Buf != NULL) && (Length != 0))
          {
              memcpy(sReceiveBuffer[idx], Buf, Length);
              sReceiveLength[idx] = Length;
          }
          else
          {
              sReceiveLength[idx] = 0;
              ALOGE("nfc_jni_llcp_doReceive_callback,%p,%p,%d", sReceiveBuffer[idx] , Buf , Length);
          }
       }
       ALOGD("nfc_jni_llcp_doReceive_callback,OK,%d", idx);
       sem_post(&g_llcp_cb_data[idx][NFC_LLCP_RECEIVE]->sem);       
   }
   else
   {
       ALOGD("cb_data_doReceive NULL");
   }
}

void nfc_jni_llcp_send_callback(MTK_NFC_LLCP_RSP* Result)
{
    // Handle MTK_NFC_P2P_SEND_DATA_RSP 

   ALOGD("nfc_jni_llcp_send_callback,%d,%d,0x%x",g_idx_service[NFC_LLCP_SEND], Result->ret, Result->service_handle );        
   /* Report the callback status and wake up the caller */
   if ( (g_idx_service[NFC_LLCP_SEND] < NFC_LLCP_SERVICE_NUM ) && (g_llcp_cb_data[g_idx_service[NFC_LLCP_SEND]][NFC_LLCP_SEND] != NULL))
   {
       g_llcp_cb_data[g_idx_service[NFC_LLCP_SEND]][NFC_LLCP_SEND]->status = Result->ret;
       sem_post(&g_llcp_cb_data[g_idx_service[NFC_LLCP_SEND]][NFC_LLCP_SEND]->sem);
   }
   else
   {
       ALOGD("cb_data NULL");
   }
}


void nfc_jni_llcp_remote_setting_callback(MTK_NFC_GET_REM_SOCKET_RSP* Result)
{

    if (Result != NULL)
    {
        ALOGD("nfc_jni_llcp_remote_setting_callback,%d,%d,%d,%d,%x", g_idx_service[NFC_LLCP_GET_PARA], Result->ret, Result->buffer_options.miu, Result->buffer_options.rw, Result->service_handle );        
        if ((g_idx_service[NFC_LLCP_GET_PARA] < NFC_LLCP_SERVICE_NUM ) && ( g_llcp_cb_data[g_idx_service[NFC_LLCP_GET_PARA]][NFC_LLCP_GET_PARA] != NULL))
        {
            if ( g_llcp_cb_data[g_idx_service[NFC_LLCP_GET_PARA]][NFC_LLCP_GET_PARA]->pContext != NULL)
            {
                memcpy((MTK_NFC_BUF_OPTION*)g_llcp_cb_data[g_idx_service[NFC_LLCP_GET_PARA]][NFC_LLCP_GET_PARA]->pContext, &Result->buffer_options, sizeof(MTK_NFC_BUF_OPTION));
            }
            g_llcp_cb_data[g_idx_service[NFC_LLCP_GET_PARA]][NFC_LLCP_GET_PARA]->status = Result->ret;
            sem_post(& g_llcp_cb_data[g_idx_service[NFC_LLCP_GET_PARA]][NFC_LLCP_GET_PARA]->sem);
        }
        else
        {
            ALOGD("cb_data NULL");
        }
    }
    else
    {
        ALOGE("rsp NULL");
    }
}

/*
 * Methods
 */
static jboolean com_android_nfc_NativeLlcpSocket_doConnect(JNIEnv *e, jobject o, jint nSap)
{
   int ret = 0;
   unsigned char idx = 0;
   struct timespec ts;
   unsigned int hRemoteDevice;
   unsigned int hLlcpSocket;
   unsigned int TotalLength = 0;
   struct nfc_jni_callback_data cb_data;
   jboolean result = JNI_FALSE;

   MTK_NFC_LLCP_CONN_SERVICE ConnSer;
   unsigned char CurrentIdx = 0;   


   /* Retrieve handles */
   hRemoteDevice = nfc_jni_get_p2p_device_handle(e,o);
   hLlcpSocket = nfc_jni_get_nfc_socket_handle(e,o);
   if ((hLlcpSocket != 0) && (device_connected_flag ==1))
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
       ALOGD("com_android_nfc_NativeLlcpSocket_doConnect,%d,0x%x,0x%x", CurrentIdx, g_llcp_service[CurrentIdx], hLlcpSocket);

       if (CurrentIdx < NFC_LLCP_SERVICE_NUM)
       {
           /* Create the local semaphore */
           if (!nfc_cb_data_init(&cb_data, NULL))
           {
              result = JNI_FALSE;   
              goto clean_and_return;
           }
           g_llcp_cb_data[CurrentIdx][NFC_LLCP_CONNECT] = &cb_data;

           {
               memset(&ConnSer, 0x00, sizeof(MTK_NFC_LLCP_CONN_SERVICE));
               ConnSer.client_handle = hLlcpSocket;
               ConnSer.llcp_sn.length = 0;
               ConnSer.remote_device_handle = hRemoteDevice;
               ConnSer.sap = nSap;
            
               TotalLength = sizeof(MTK_NFC_LLCP_CONN_SERVICE);
            
               ALOGD("com_android_nfc_NativeLlcpSocket_doConnect(%d),0x%x", nSap, ConnSer.client_handle);

               g_idx_service[NFC_LLCP_CONNECT] = CurrentIdx;  

               // MTK_NFC_P2P_CONNECT_CLIENT_REQ
               ret = android_nfc_jni_send_msg(MTK_NFC_P2P_CONNECT_CLIENT_REQ, TotalLength, (void *)&ConnSer);
            
               if (ret == FALSE)
               {
                  ALOGE("send doConnect cmd fail\n");
                  result = JNI_FALSE;     
                  goto clean_and_return;
               }
            
               // Wait MTK_NFC_P2P_CONNECT_CLIENT_RSP
               if (sem_wait(&g_llcp_cb_data[CurrentIdx][NFC_LLCP_CONNECT]->sem)) {
                  ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
                  result = JNI_FALSE;
                  goto clean_and_return;
               }   
            
               ret = g_llcp_cb_data[CurrentIdx][NFC_LLCP_CONNECT]->status;
            
               ALOGD("com_android_nfc_NativeLlcpSocket_doConnect(%d) returned %d", nSap, ret);

           }
           if(ret != 0)
           {
               ALOGW("LLCP Connect request failed");
               result = JNI_FALSE;
           }
           else 
           {
               result = JNI_TRUE;
           }        
        clean_and_return:
           nfc_cb_data_deinit(&cb_data);
           g_llcp_cb_data[CurrentIdx][NFC_LLCP_CONNECT] = NULL;
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
       ALOGE("doConnect Not ready,%d,%d", hLlcpSocket, device_connected_flag);
       result = JNI_FALSE;
   }

   return result;
}

static jboolean com_android_nfc_NativeLlcpSocket_doConnectBy(JNIEnv *e, jobject o, jstring sn)
{
   int ret = 0;
   unsigned char idx = 0;  
   struct timespec ts;
   unsigned int hRemoteDevice = 0;
   unsigned int hLlcpSocket = 0;
   unsigned int TotalLength = 0;   
   unsigned char* ServiceNameBuf = NULL;
   unsigned int ServiceNameLgh = 0;
   struct nfc_jni_callback_data cb_data;
   jboolean result = JNI_FALSE;

   MTK_NFC_LLCP_CONN_SERVICE* pConnSer = NULL;   
   unsigned char CurrentIdx = 0;      

   /* Retrieve handles */
   hRemoteDevice = nfc_jni_get_p2p_device_handle(e,o);
   hLlcpSocket = nfc_jni_get_nfc_socket_handle(e,o);
   
   if ((hLlcpSocket != 0) && (device_connected_flag == 1))
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
       ALOGD("com_android_nfc_NativeLlcpSocket_doConnectBy,%d,0x%x,0x%x", CurrentIdx, g_llcp_service[CurrentIdx], hLlcpSocket);

       if (CurrentIdx < NFC_LLCP_SERVICE_NUM)
       {
           /* Create the local semaphore */
           if (!nfc_cb_data_init(&cb_data, NULL))
           {
              goto clean_and_return;
           }
           g_llcp_cb_data[CurrentIdx][NFC_LLCP_CONNECT] = &cb_data;
        
           /* Service socket */
           ServiceNameBuf = (unsigned char*)e->GetStringUTFChars(sn, NULL);
           ServiceNameLgh = (unsigned int)e->GetStringUTFLength(sn);
        
           TotalLength = sizeof(MTK_NFC_LLCP_CONN_SERVICE) + ServiceNameLgh;
           pConnSer = (MTK_NFC_LLCP_CONN_SERVICE*)malloc(TotalLength);   
        
           if (ServiceNameBuf != NULL)
           {
               memcpy(pConnSer->llcp_sn.buffer, ServiceNameBuf, ServiceNameLgh);
               pConnSer->llcp_sn.length = ServiceNameLgh;
               pConnSer->client_handle = hLlcpSocket;
               pConnSer->sap = 0x01;
               pConnSer->remote_device_handle = hRemoteDevice;
           }
           else
           {
               result = JNI_FALSE;
               goto clean_and_return;
           }
           {           
               TRACE("com_android_nfc_NativeLlcpSocket_doConnectBy,0x%x,%d", pConnSer->client_handle, pConnSer->llcp_sn.length);

               g_idx_service[NFC_LLCP_CONNECT] = CurrentIdx;

               // MTK_NFC_P2P_CONNECT_CLIENT_REQ
               ret = android_nfc_jni_send_msg(MTK_NFC_P2P_CONNECT_CLIENT_REQ, TotalLength, (void *)pConnSer);
            
               if (ret == FALSE)
               {
                  ALOGE("send doConnectBy cmd fail\n");
                  result = JNI_FALSE;     
                  goto clean_and_return;
               }           
     
               // Wait MTK_NFC_P2P_CONNECT_CLIENT_RSP   
               if (sem_wait(&g_llcp_cb_data[CurrentIdx][NFC_LLCP_CONNECT]->sem)) {
                   ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
                   result = JNI_FALSE; 
                   goto clean_and_return;
               }   
            
               ret = g_llcp_cb_data[CurrentIdx][NFC_LLCP_CONNECT]->status;
    
               ALOGD("com_android_nfc_NativeLlcpSocket_doConnectBy returned %d", ret);
           }            
           if(ret != 0)
           {
               ALOGW("LLCP ConnectBy request failed,0x%x",ret);
               result = JNI_FALSE;
           }
           else 
           {
               result = JNI_TRUE;
           }  
        clean_and_return:        
           if ((ServiceNameBuf != NULL) && (e != NULL)) {
              e->ReleaseStringUTFChars(sn, (const char *)ServiceNameBuf);
           }
           nfc_cb_data_deinit(&cb_data);    
           g_llcp_cb_data[CurrentIdx][NFC_LLCP_CONNECT] = NULL;
           if (pConnSer != NULL)
           {
               free(pConnSer);
               pConnSer = NULL;
           }   
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
   	   ALOGE("doConnectby Not ready,%d,%d", hLlcpSocket, device_connected_flag);
	   result = JNI_FALSE;
   }
   
   return result;
}

static jboolean com_android_nfc_NativeLlcpSocket_doClose(JNIEnv *e, jobject o)
{
   int ret = 0;
   unsigned char idx = 0;
   jboolean result = TRUE;
   unsigned int hLlcpSocket;
   unsigned int TotalLength = 0;
   struct nfc_jni_callback_data cb_data;
 
   unsigned char CurrentIdx = 0;  

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
       ALOGD("com_android_nfc_NativeLlcpSocket_doClose,%d,0x%x,0x%x", CurrentIdx, g_llcp_service[CurrentIdx], hLlcpSocket);

       if (CurrentIdx < NFC_LLCP_SERVICE_NUM)
       {

           TotalLength = sizeof(unsigned int);
        
           if (!nfc_cb_data_init(&cb_data, NULL))
           {
              goto clean_and_return;
           }
           g_llcp_cb_data[CurrentIdx][NFC_LLCP_CLOSE] = &cb_data;

           g_idx_service[NFC_LLCP_CLOSE] = CurrentIdx;        
           // MTK_NFC_P2P_DISC_REQ
           ret = android_nfc_jni_send_msg(MTK_NFC_P2P_DISC_REQ, TotalLength, (void *)&hLlcpSocket);   
           if (ret == FALSE)
           {
              ALOGE("send doClose cmd fail\n");
              result = FALSE;
              goto clean_and_return;
           }             
           // wait MTK_NFC_P2P_DISC_RSP

           if (sem_wait(&g_llcp_cb_data[CurrentIdx][NFC_LLCP_CLOSE]->sem)) {
               ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
               result = FALSE;	   
               goto clean_and_return;
           }     
        
           ret = g_llcp_cb_data[CurrentIdx][NFC_LLCP_CLOSE]->status;
           
           if(ret != 0)
           {
               result = FALSE;
           }
           else
           {
               result = TRUE;
           }
           TRACE("com_android_nfc_NativeLlcpSocket_doClose returned,%d,0x%04x,%d",CurrentIdx, hLlcpSocket, result);

        clean_and_return:        
           nfc_cb_data_deinit(&cb_data);
           g_llcp_remrw[CurrentIdx] = 0; 
           g_llcp_remiu[CurrentIdx] = 0; 
           if (g_llcp_sertype[CurrentIdx] == 2)
           {
                g_llcp_service[CurrentIdx] = 0;
                g_llcp_sertype[CurrentIdx] = 0;
                TRACE("com_android_nfc_NativeLlcpSocket_doClose Client mode");                
           }
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
       ALOGE("doCloase C Not ready,%d", hLlcpSocket);
       result = FALSE;
   }

   return result;
}

static jboolean com_android_nfc_NativeLlcpSocket_doSend(JNIEnv *e, jobject o, jbyteArray  data)
{
   int ret;
   unsigned char idx = 0;   
   struct timespec ts;
   unsigned int hRemoteDevice;
   unsigned int hLlcpSocket;
   unsigned int TotalLength = 0;   
   unsigned char* sSendBuffer = NULL;
   unsigned int sSendLength = 0;
   struct nfc_jni_callback_data cb_data;
   jboolean result = JNI_FALSE;
   MTK_NFC_LLCP_SEND_DATA* pData = NULL;  
   unsigned char CurrentIdx = 0;

   /* Retrieve handles */
   hRemoteDevice = nfc_jni_get_p2p_device_handle(e,o);
   hLlcpSocket = nfc_jni_get_nfc_socket_handle(e,o);

   if ((hLlcpSocket != 0) && (device_connected_flag == 1))
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
       ALOGD("com_android_nfc_NativeLlcpSocket_doSend,%d,0x%x,0x%x", CurrentIdx, g_llcp_service[CurrentIdx], hLlcpSocket);

       if (CurrentIdx < NFC_LLCP_SERVICE_NUM)
       {   
            /* Create the local semaphore */
            if (!nfc_cb_data_init(&cb_data, NULL))
            {
               goto clean_and_return;
            }
            g_llcp_cb_data[CurrentIdx][NFC_LLCP_SEND] = &cb_data;
         
            sSendBuffer = (unsigned char*)e->GetByteArrayElements(data, NULL);
            sSendLength = (unsigned int)e->GetArrayLength(data);
         
            if ((sSendBuffer != NULL) && (sSendLength != 0))
            {
                char* DbgBuf = NULL;
                unsigned int Index = 0, DbgLth = 0;

                DbgBuf = (char*)malloc(sSendLength*6);
                memset(DbgBuf, 0x00, sSendLength*6);    
            
                TotalLength = sizeof(MTK_NFC_LLCP_SEND_DATA) + sSendLength;
                pData = (MTK_NFC_LLCP_SEND_DATA*)malloc(TotalLength);
                memset(pData, 0x00, TotalLength);
                pData->remote_dev_handle = hRemoteDevice;
                pData->service_handle = hLlcpSocket;
                pData->sap = 0;
                pData->llcp_data_send_buf.length = sSendLength;
                memcpy(pData->llcp_data_send_buf.buffer, sSendBuffer, sSendLength);
                
                if (DbgBuf != NULL)
                {
                    for (Index = 0; (Index < sSendLength) && (DbgBuf+DbgLth != NULL); Index++)
                    {
                        sprintf(DbgBuf+DbgLth, "%02x ", pData->llcp_data_send_buf.buffer[Index]);
                        DbgLth = strlen(DbgBuf);          
                    }
                    ALOGD("%d\n", sSendLength);
                    if (sSendLength != 0)
                    {
                        ALOGD("%s\n", DbgBuf);
                    }
                    free(DbgBuf);
                    DbgBuf = NULL;
                }           
            }
            else
            {
                result = JNI_FALSE;
                goto clean_and_return;
            }

            g_idx_service[NFC_LLCP_SEND] = CurrentIdx;
            
            //MTK_NFC_P2P_SEND_DATA_REQ 
            ret = android_nfc_jni_send_msg(MTK_NFC_P2P_SEND_DATA_REQ, TotalLength, (void *)pData);
            if (pData != NULL)
            {
                free(pData);
            }
            
            if (ret == FALSE)
            {
                ALOGE("send doSend cmd fail\n");
                result = JNI_FALSE;
                goto clean_and_return;
            }    
          
            // wait MTK_NFC_P2P_SEND_DATA_RSP
            if (sem_wait(&g_llcp_cb_data[CurrentIdx][NFC_LLCP_SEND]->sem)) {
                ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
                result = JNI_FALSE;
                goto clean_and_return;
            }   
            ret = g_llcp_cb_data[CurrentIdx][NFC_LLCP_SEND]->status;
            if(ret != 0)
            {
               result = JNI_FALSE;
            }
            else
            {
               result = JNI_TRUE;
            }
            ALOGD("com_android_nfc_NativeLlcpSocket_doSend returned,%d,0x%04x,%d",CurrentIdx, hLlcpSocket, ret);
            
         clean_and_return:
            if (sSendBuffer != NULL)
            {
               e->ReleaseByteArrayElements(data, (jbyte*)sSendBuffer, JNI_ABORT);
            }
            nfc_cb_data_deinit(&cb_data);           
            g_llcp_cb_data[CurrentIdx][NFC_LLCP_SEND] = NULL;  
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
	   ALOGE("doSend Not ready,%d,%d", hLlcpSocket, device_connected_flag);
	   result = JNI_FALSE;
   }

   return result;
}

static jint com_android_nfc_NativeLlcpSocket_doReceive(JNIEnv *e, jobject o, jbyteArray  buffer)
{
   int ret;
   unsigned char idx = 0;   
   struct timespec ts;
   unsigned int hRemoteDevice;
   unsigned int hLlcpSocket;
   unsigned int TotalLength = 0;      
   struct nfc_jni_callback_data cb_data;
   jint result = -1;
   MTK_NFC_LLCP_SOKCET Socket; //hRemoteDevice, hLlcpSocket
   unsigned char CurrentIdx = 0;


   /* Retrieve handles */
   hRemoteDevice = nfc_jni_get_p2p_device_handle(e,o);
   hLlcpSocket = nfc_jni_get_nfc_socket_handle(e,o);

   if ((hLlcpSocket != 0)&&(device_connected_flag ==1))
   {
       for (idx = 0; idx < NFC_LLCP_SERVICE_NUM ; idx++)
       {
           if (hLlcpSocket == g_llcp_service[idx])
           {
               CurrentIdx = idx;
               break;
           }
       }
       ALOGD("com_android_nfc_NativeLlcpSocket_doReceive,%d,0x%x,0x%x", CurrentIdx, g_llcp_service[CurrentIdx], hLlcpSocket);

       if (CurrentIdx < NFC_LLCP_SERVICE_NUM)
       {   
           /* Create the local semaphore */
           if (!nfc_cb_data_init(&cb_data, NULL))
           {
              goto clean_and_return;
           }
           
           TotalLength = sizeof(MTK_NFC_LLCP_SOKCET);
           Socket.llcp_service_handle = hLlcpSocket;
           Socket.remote_dev_handle = hRemoteDevice;
                      
           g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE] = &cb_data;
           g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]->pContext = malloc(sizeof(unsigned char));
           sReceiveBuffer[CurrentIdx] = (unsigned char*)e->GetByteArrayElements(buffer, NULL);
           sReceiveLength[CurrentIdx] = (unsigned int)e->GetArrayLength(buffer);
           ALOGD("com_android_nfc_NativeLlcpSocket_doReceive,%d", sReceiveLength[CurrentIdx]);  

          // MTK_NFC_P2P_RECV_DATA_REQ
           ret = android_nfc_jni_send_msg(MTK_NFC_P2P_RECV_DATA_REQ, TotalLength, (void *)&Socket);
           
           if (ret == FALSE)
           {
               ALOGE("send doReceive cmd fail\n");
               result = -1;
               goto clean_and_return;
           }         

           // wait MTK_NFC_P2P_RECV_DATA_RSP
           for (idx = 0; idx < NFC_LLCP_SERVICE_NUM ; idx++)
           {
              if (hLlcpSocket == g_llcp_service[idx])
              {
                  CurrentIdx = idx;
                  break;
              }
           }         
    
           if (sem_wait(&g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]->sem)) {
               ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
               result = -1;
               goto clean_and_return;
           }

           ALOGD("Go_doReceive,%d ", CurrentIdx);    
           ret = g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]->status;
           if((ret == 0) && (sReceiveLength[CurrentIdx] != 0))
           {
              char* DbgBuf = NULL;
              unsigned int Index = 0, DbgLth = 0;
              
              DbgBuf = (char*)malloc(sReceiveLength[CurrentIdx]*6);
              memset(DbgBuf, 0x00, sReceiveLength[CurrentIdx]*6);    

              if (DbgBuf != NULL)
              {
                  for (Index = 0; (Index < sReceiveLength[CurrentIdx]) && (DbgBuf+DbgLth != NULL); Index++)
                  {
                      sprintf(DbgBuf+DbgLth, "%02x ", sReceiveBuffer[CurrentIdx][Index]);
                      DbgLth = strlen(DbgBuf);          
                  }
                  ALOGD("%d\n", sReceiveLength[CurrentIdx]);
                  if (sReceiveLength[CurrentIdx] != 0)
                  {
                     ALOGD("%s\n", DbgBuf);
                  }
                  free(DbgBuf);
                  DbgBuf = NULL;
              }  
              result = sReceiveLength[CurrentIdx];              
           }
           else
           {
              /* Return status should be either SUCCESS or PENDING */
              result = -1;
           }
           ALOGD("com_android_nfc_NativeLlcpSocket_doReceive returned,%d,0x%04x,%d",CurrentIdx, hLlcpSocket, result);
        clean_and_return:
           //e->ReleaseByteArrayElements(buffer, (jbyte*)sReceiveBuffer[CurrentIdx], 0);            
           
           e->ReleaseByteArrayElements(buffer, (jbyte*)sReceiveBuffer[CurrentIdx], JNI_COMMIT);           
           if (sReceiveBuffer[CurrentIdx] != NULL)
           {
               e->ReleaseByteArrayElements(buffer, (jbyte*)sReceiveBuffer[CurrentIdx], JNI_ABORT);
               sReceiveBuffer[CurrentIdx] = NULL;
           }
           
           if (g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]->pContext != NULL)
           {
              free(g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE]->pContext);
           }              
           nfc_cb_data_deinit(&cb_data);        
           g_llcp_cb_data[CurrentIdx][NFC_LLCP_RECEIVE] = NULL; 
       }
       else
       {
           ALOGE("doReceive hLlcpSocket not find");
           result = 0;
       }             
   }
   else
   {
	   ALOGE("doReceive Not ready,%d,%d", hLlcpSocket, device_connected_flag);
	   result = 0;
   }   
   return result;   
}

static jint com_android_nfc_NativeLlcpSocket_doGetRemoteSocketMIU(JNIEnv *e, jobject o)
{
   int ret = 0;
   unsigned char idx = 0;
   unsigned int hRemoteDevice = 1;
   unsigned int hLlcpSocket = 0;
   unsigned int miu = 0;   
   unsigned int TotalLength = 0;         
   MTK_NFC_LLCP_SOKCET Socket; //hRemoteDevice, hLlcpSocket
   struct nfc_jni_callback_data cb_data;
   MTK_NFC_BUF_OPTION* pRemoteSocket = NULL;
   jint result = -1;
   unsigned char CurrentIdx = 0;
   
   /* Retrieve handles */
   hRemoteDevice = nfc_jni_get_p2p_device_handle(e,o);
   hLlcpSocket = nfc_jni_get_nfc_socket_handle(e,o); 
   /* Create the local semaphore */

   if ((hLlcpSocket != 0)&&(device_connected_flag == 1))
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
        
       if (g_llcp_remiu[CurrentIdx]  == 0)
       {
           ALOGD("com_android_nfc_NativeLlcpSocket_doGetRemoteSocketMIU,%d,0x%x,0x%x", CurrentIdx, g_llcp_service[CurrentIdx], hLlcpSocket);
    
           if (CurrentIdx < NFC_LLCP_SERVICE_NUM)
           {  
               if (!nfc_cb_data_init(&cb_data, NULL))
               {
                  goto clean_and_return;
               }
               g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA] = &cb_data;
               g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA]->pContext = malloc(sizeof(MTK_NFC_BUF_OPTION));
               
               TotalLength = sizeof(MTK_NFC_LLCP_SOKCET);
               Socket.llcp_service_handle = hLlcpSocket;
               Socket.remote_dev_handle = hRemoteDevice;   
               
               g_idx_service[NFC_LLCP_GET_PARA] = CurrentIdx;

               ret = android_nfc_jni_send_msg(MTK_NFC_P2P_GET_REM_SETTING_REQ, TotalLength, (void *)&Socket);	 
               // MTK_NFC_P2P_GET_REM_SETTING_REQ
    
               if (ret == FALSE)
               {
                   ALOGE("send doGetMIU cmd fail\n");
                   result = 0;
                   goto clean_and_return;
               }                
 
               // Wait MTK_NFC_P2P_GET_REM_SETTING_RSP  mesage and update miu/rw and ret
               if (sem_wait(&g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA]->sem)) 
               {
                  ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
                  result = 0;
                  goto clean_and_return;
               }  
         
               pRemoteSocket = (MTK_NFC_BUF_OPTION*)g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA]->pContext;
               ret = g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA]->status;
               if((ret == 0 )&& (pRemoteSocket != NULL))
               {
                  g_llcp_remiu[CurrentIdx] = pRemoteSocket->miu;
                  g_llcp_remrw[CurrentIdx] = pRemoteSocket->rw;              
                  result = pRemoteSocket->miu;
               }
               else
               {
                  result = 0;
               }
            clean_and_return:             
               if (g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA]->pContext != NULL)
               {
                  free(g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA]->pContext);
               }       
               nfc_cb_data_deinit(&cb_data);         
               g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA] = NULL; 
           }
           else
           {
               result = 0; 
           }
       }
       else
       {
           result = g_llcp_remiu[CurrentIdx];
       }
       TRACE("com_android_nfc_NativeLlcpSocket_doGetRemoteSocket(MIU),%d,0x%x,%d,%d,%d",CurrentIdx, g_llcp_service[CurrentIdx],ret, g_llcp_remiu[CurrentIdx] , g_llcp_remrw[CurrentIdx] );
           
       CONCURRENCY_UNLOCK();
   }
   else
   {
	  ALOGE("GetRemoteM Not ready,%d,%d", hLlcpSocket, device_connected_flag);   
      result = 0; 
   }   
   return result;    
}

static jint com_android_nfc_NativeLlcpSocket_doGetRemoteSocketRW(JNIEnv *e, jobject o)
{
    int ret = 0;
    unsigned char idx = 0;
    unsigned int hRemoteDevice = 1;
    unsigned int hLlcpSocket = 0;
    unsigned int rw = 0;  
    unsigned int TotalLength = 0;
    MTK_NFC_LLCP_SOKCET Socket; //hRemoteDevice, hLlcpSocket
    struct nfc_jni_callback_data cb_data;
    MTK_NFC_BUF_OPTION* pRemoteSocket = NULL;
    int result = -1;
    unsigned char CurrentIdx = 0;    

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
               CurrentIdx = idx;
               break;
           }
       }
       if (g_llcp_remrw[CurrentIdx]  == 0)
       {
           ALOGD("com_android_nfc_NativeLlcpSocket_doGetRemoteSocketRW,%d,0x%x,0x%x", CurrentIdx, g_llcp_service[CurrentIdx], hLlcpSocket);
    
           if (CurrentIdx < NFC_LLCP_SERVICE_NUM)
           {       
    
               /* Create the local semaphore */
               if (!nfc_cb_data_init(&cb_data, NULL))
               {
                  goto clean_and_return;
               }
               g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA] = &cb_data;
               g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA]->pContext = malloc(sizeof(MTK_NFC_BUF_OPTION));
        
               TotalLength = sizeof(MTK_NFC_LLCP_SOKCET);
               Socket.llcp_service_handle = hLlcpSocket;
               Socket.remote_dev_handle = hRemoteDevice;   

               g_idx_service[NFC_LLCP_GET_PARA] = CurrentIdx;
               
               // MTK_NFC_P2P_GET_REM_SETTING_REQ
               ret = android_nfc_jni_send_msg(MTK_NFC_P2P_GET_REM_SETTING_REQ, TotalLength, (void *)&Socket);	 
            
               if (ret == FALSE)
               {
                   ALOGE("send doGetRW cmd fail\n");
                   result = 0;
                   goto clean_and_return;
               }  
               
               // Wait MTK_NFC_P2P_GET_REM_SETTING_RSP  mesage and update miu/rw and ret
               if (sem_wait(&g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA]->sem)) 
               {
                   ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
                   result = 0;
                   goto clean_and_return;
               }   
               pRemoteSocket = (MTK_NFC_BUF_OPTION*)g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA]->pContext;
               ret = g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA]->status;   
               if((ret == 0) && (pRemoteSocket != NULL))
               {
                  g_llcp_remiu[CurrentIdx] = pRemoteSocket->miu;
                  g_llcp_remrw[CurrentIdx] = pRemoteSocket->rw;                  
                  result = pRemoteSocket->rw;
               }
               else
               {
                  result = 0;
               }
            clean_and_return:           
               if (g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA]->pContext != NULL)
               {
                  free(g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA]->pContext);
               }        
               nfc_cb_data_deinit(&cb_data);
    
               g_llcp_cb_data[CurrentIdx][NFC_LLCP_GET_PARA] = NULL;   
           }
           else
           {
               result = 0; 
           }    
       }
       else
       {
           result = g_llcp_remrw[g_idx_service[NFC_LLCP_GET_PARA]];
       }       
       TRACE("com_android_nfc_NativeLlcpSocket_doGetRemoteSocket(RW),%d,0x%x,%d,%d,%d",g_idx_service[NFC_LLCP_GET_PARA], g_llcp_service[g_idx_service[NFC_LLCP_GET_PARA]],ret, g_llcp_remiu[g_idx_service[NFC_LLCP_GET_PARA]] , g_llcp_remrw[g_idx_service[NFC_LLCP_GET_PARA]] );
          
       CONCURRENCY_UNLOCK();       
   }
   else
   {
 	   ALOGE("GetRemoteR Not ready,%d,%d", hLlcpSocket, device_connected_flag);   
       result = 0; 
   }
   return result;
}


/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] =
{
   {"doConnect", "(I)Z",
      (void *)com_android_nfc_NativeLlcpSocket_doConnect},

   {"doConnectBy", "(Ljava/lang/String;)Z",
      (void *)com_android_nfc_NativeLlcpSocket_doConnectBy},
      
   {"doClose", "()Z",
      (void *)com_android_nfc_NativeLlcpSocket_doClose},
      
   {"doSend", "([B)Z",
      (void *)com_android_nfc_NativeLlcpSocket_doSend},

   {"doReceive", "([B)I",
      (void *)com_android_nfc_NativeLlcpSocket_doReceive},
      
   {"doGetRemoteSocketMiu", "()I",
      (void *)com_android_nfc_NativeLlcpSocket_doGetRemoteSocketMIU},
           
   {"doGetRemoteSocketRw", "()I",
      (void *)com_android_nfc_NativeLlcpSocket_doGetRemoteSocketRW},
};


int register_com_android_nfc_NativeLlcpSocket(JNIEnv *e)
{
   return jniRegisterNativeMethods(e,
      "com/android/nfc/dhimpl/NativeLlcpSocket",gMethods, NELEM(gMethods));
}

} // namespace android

