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
extern unsigned char g_idx_service[NFC_LLCP_ACT_END];
extern unsigned int g_llcp_remiu[NFC_LLCP_SERVICE_NUM];
extern unsigned char g_llcp_remrw[NFC_LLCP_SERVICE_NUM];


namespace android {

//extern  void nfc_jni_llcp_transport_socket_err_callback( unsigned char nErrCode);
/*
 * Callbacks
 */
void nfc_jni_llcp_accept_socket_callback(int    status)
{
   ALOGD("nfc_jni_llcp_doAccept_socket_callback,%d,%d",g_idx_service[NFC_LLCP_ACCEPT], status);
   if (g_llcp_cb_data[g_idx_service[NFC_LLCP_ACCEPT]][NFC_LLCP_ACCEPT] != NULL)
   {
       /* Report the callback status and wake up the caller */
       g_llcp_cb_data[g_idx_service[NFC_LLCP_ACCEPT]][NFC_LLCP_ACCEPT]->status = status;
       sem_post(&g_llcp_cb_data[g_idx_service[NFC_LLCP_ACCEPT]][NFC_LLCP_ACCEPT]->sem);
   }
   else
   {
       ALOGD("cb_data_doAccept NULL");
   }
}


/*
 * Utils
 */

static unsigned int getIncomingSocket(nfc_jni_native_monitor_t * pMonitor,
                                                 unsigned int hServerSocket)
{
   nfc_jni_listen_data_t * pListenData;
   unsigned int pIncomingSocket = 0;

 //  ALOGD("getIncomingSocket,0x%x", hServerSocket);

   /* Look for a pending incoming connection on the current server */
   LIST_FOREACH(pListenData, &pMonitor->incoming_socket_head, entries)
   {
      if (pListenData->pServerSocket == hServerSocket)
      {
         pIncomingSocket = pListenData->pIncomingSocket;
        // ALOGD("getIncomingSocket,pIncomingSocket,0x%x,hServerSocket,0x%x", pIncomingSocket, hServerSocket);
         if (pListenData != NULL)
         {
            LIST_REMOVE(pListenData, entries);
            free(pListenData);
         }
         break;
      }
   }

   return pIncomingSocket;
}

/*
 * Methods
 */
static jobject com_NativeLlcpServiceSocket_doAccept(JNIEnv *e, jobject o, jint miu, jint rw, jint linearBufferLength)
{
   int ret = -1;
   unsigned char idx = 0;
   struct timespec ts;

   jfieldID f;
   jclass clsNativeLlcpSocket;
   jobject clientSocket = NULL;
   struct nfc_jni_callback_data cb_data;
   unsigned int hIncomingSocket = 0, hServerSocket = 0, hRemoteDevice = 0;
   unsigned int TotalLength = 0;
   MTK_NFC_LLCP_ACCEPT_SERVICE Accept;
   unsigned char CurrentIdx = 0;
   nfc_jni_native_monitor_t * pMonitor = nfc_jni_get_monitor();


   /* Get server socket */
   hServerSocket = nfc_jni_get_nfc_socket_handle(e,o);
   hRemoteDevice = nfc_jni_get_p2p_device_handle(e,o);

   if (hServerSocket != 0)
   {
       memset(&Accept, 0x00, sizeof(MTK_NFC_LLCP_ACCEPT_SERVICE));
       TotalLength = sizeof(MTK_NFC_LLCP_ACCEPT_SERVICE);

       /* Set socket options with the socket options of the service */

       Accept.buffer_options.miu = miu;
       Accept.buffer_options.rw = rw;

       /* Allocate Working buffer length */
       for (idx = 0; idx < NFC_LLCP_SERVICE_NUM ; idx++)
       {
           if (hServerSocket == g_llcp_service[idx])
           {
               CurrentIdx = idx;
               break;
           }
       }
       ALOGD("com_NativeLlcpServiceSocket_doAccept,%d,0x%x,0x%x", CurrentIdx, g_llcp_service[CurrentIdx], hServerSocket);

       if (CurrentIdx < NFC_LLCP_SERVICE_NUM)
       {
          /* Wait for tag Notification */
          // Wait for MTK_NFC_P2P_CREATE_SERVER_NTF

          pthread_cond_wait(&pMonitor->incoming_socket_cond[CurrentIdx], &pMonitor->incoming_socket_mutex[CurrentIdx]);
          hIncomingSocket = getIncomingSocket(pMonitor, hServerSocket);

          if (hIncomingSocket != 0)
          {
               Accept.incoming_handle = hIncomingSocket;
               Accept.remote_device_handle = hRemoteDevice;

              /* Accept the incomming socket */
               TRACE("com_NativeLlcpServiceSocket_doAccept,miu,%d,rw,%d,H,0x%x", Accept.buffer_options.miu ,Accept.buffer_options.rw, Accept.incoming_handle);
              // MTK_NFC_P2P_ACCEPT_SERVER_REQ
           /* Create the local semaphore */
               if (!nfc_cb_data_init(&cb_data, NULL))
               {
                   clientSocket = NULL;
                   goto clean_and_return;
               }

               g_llcp_cb_data[CurrentIdx][NFC_LLCP_ACCEPT] = &cb_data;

               g_idx_service[NFC_LLCP_ACCEPT] = CurrentIdx;

               ret = android_nfc_jni_send_msg(MTK_NFC_P2P_ACCEPT_SERVER_REQ, TotalLength, (void *)&Accept);

               if (ret == FALSE)
               {
                   ALOGE("send doAccept cmd fail\n");
                   clientSocket = NULL;
                   goto clean_and_return;
               }

               ALOGD("Wait_doAccept,%d", CurrentIdx);
               // Wait for MTK_NFC_P2P_ACCEPT_SERVER_RSP
               if (sem_wait(&g_llcp_cb_data[CurrentIdx][NFC_LLCP_ACCEPT]->sem)) {
                   ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
                   clientSocket = NULL;
                   goto clean_and_return;
               }
               ret = g_llcp_cb_data[CurrentIdx][NFC_LLCP_ACCEPT]->status;

               ALOGD("com_NativeLlcpServiceSocket_doAccept() returned 0x%04x,%d", ret, CurrentIdx);

               if(ret != 0)
               {
                   clientSocket = NULL;
                   goto clean_and_return;
               }
               else
               {

                   /* Create new LlcpSocket object */
                   if(nfc_jni_cache_object(e,"com/android/nfc/dhimpl/NativeLlcpSocket",&(clientSocket)) == -1)
                   {
                       ALOGD("LLCP Socket creation error");
                       goto clean_and_return;
                   }

                   /* Get NativeConnectionOriented class object */
                   clsNativeLlcpSocket = e->GetObjectClass(clientSocket);
                   if(e->ExceptionCheck())
                   {
                      clientSocket = NULL;
                      ret = 1;
                      ALOGE("LLCP Socket get class object error");
                      goto clean_and_return;
                   }

                   /* Set socket handle */
                   f = e->GetFieldID(clsNativeLlcpSocket, "mHandle", "I");
                   e->SetIntField(clientSocket, f,(jint)hIncomingSocket);

                   /* Set socket MIU */
                   f = e->GetFieldID(clsNativeLlcpSocket, "mLocalMiu", "I");
                   e->SetIntField(clientSocket, f,(jint)miu);

                   /* Set socket RW */
                   f = e->GetFieldID(clsNativeLlcpSocket, "mLocalRw", "I");
                   e->SetIntField(clientSocket, f,(jint)rw);

                   TRACE("com_NativeLlcpServiceSocket_doAccept, 0x%02x: MIU = %d, RW = %d\n",hIncomingSocket, miu, rw);
               }
           clean_and_return:
               nfc_cb_data_deinit(&cb_data);
               //g_idx_service[NFC_LLCP_ACCEPT] = NFC_LLCP_SERVICE_NUM; //remove for NE ALPS01283036
               g_llcp_cb_data[CurrentIdx][NFC_LLCP_ACCEPT] = NULL;
           }
           else
           {
               clientSocket = NULL;
               ret = 1;
               ALOGE("com_NativeLlcpServiceSocket_doAccept,IncomingSocket not find");
           }
       }
       else
       {
           clientSocket = NULL;
           ret = 1;
           ALOGE("ServerSocket not find");
       }
   }
   else
   {
       ALOGE("ServerSocket is not ready");
       clientSocket = NULL;
   }
   return clientSocket;
}

static jboolean com_NativeLlcpServiceSocket_doClose(JNIEnv *e, jobject o)
{
   int ret = -1;
   unsigned char idx = 0;
   unsigned int hLlcpSocket = 0;
   unsigned int TotalLength = 0;
   struct nfc_jni_callback_data cb_data;
   jboolean result = TRUE;
   nfc_jni_native_monitor_t * pMonitor = nfc_jni_get_monitor();
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

       ALOGD("com_NativeLlcpServiceSocket_doClose,%d,0x%x,0x%x", CurrentIdx, g_llcp_service[CurrentIdx], hLlcpSocket);

       if (CurrentIdx < NFC_LLCP_SERVICE_NUM)
       {
           TotalLength = sizeof(unsigned int);
           if (!nfc_cb_data_init(&cb_data, NULL))
           {
               result = FALSE;
               goto clean_and_return;
           }

           g_llcp_cb_data[CurrentIdx][NFC_LLCP_CLOSE] = &cb_data;

           {
               pthread_cond_broadcast(&pMonitor->incoming_socket_cond[CurrentIdx]);
               ALOGD("unlock,com_NativeLlcpServiceSocket_doClose");
           }

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

           if (sem_wait(& g_llcp_cb_data[CurrentIdx][NFC_LLCP_CLOSE]->sem)) {
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
           ALOGD("com_NativeLlcpServiceSocket_doClose,%d,0x%x,%d",CurrentIdx, ret, result);
        clean_and_return:
           nfc_cb_data_deinit(&cb_data);
           g_llcp_remrw[CurrentIdx] = 0;
           g_llcp_remiu[CurrentIdx] = 0;
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
   {"doAccept", "(III)Lcom/android/nfc/dhimpl/NativeLlcpSocket;",
      (void *)com_NativeLlcpServiceSocket_doAccept},

   {"doClose", "()Z",
      (void *)com_NativeLlcpServiceSocket_doClose},
};


int register_com_android_nfc_NativeLlcpServiceSocket(JNIEnv *e)
{
   return jniRegisterNativeMethods(e,
      "com/android/nfc/dhimpl/NativeLlcpServiceSocket",
      gMethods, NELEM(gMethods));
}

} // namespace android

