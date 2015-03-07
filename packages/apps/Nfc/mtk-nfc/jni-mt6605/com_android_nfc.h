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

#ifndef __COM_ANDROID_NFC_JNI_H__
#define __COM_ANDROID_NFC_JNI_H__

#define LOG_TAG "NFCJNI"

#include <JNIHelp.h>
#include <jni.h>

#include <pthread.h>
#include <sys/queue.h>

extern "C" {
#include <cutils/log.h>
#include <com_android_nfc_list.h>
#include <semaphore.h>
#if 1 // Android NFC JNI implementation by Hiki 
#include "mtk_nfc_sys_type.h"
#include "mtk_nfc_sys.h"
#include "mtk_nfc_sys_type_ext.h"
#define NFC_STATUS_FAIL                 (0x1)
#endif
}
#include <cutils/properties.h> // for property_get

#define DTA_SUPPORT

#define NFC_OPMODE_READER  0x01 
#define NFC_OPMODE_CARD    0x02 
#define NFC_OPMODE_P2P     0x04

/* Discovery modes -- keep in sync with NFCManager.DISCOVERY_MODE_* */
#define DISCOVERY_MODE_TAG_READER         0
#define DISCOVERY_MODE_NFCIP1             1
#define DISCOVERY_MODE_CARD_EMULATION     2

#define DISCOVERY_MODE_TABLE_SIZE         3

#define DISCOVERY_MODE_DISABLED           0
#define DISCOVERY_MODE_ENABLED            1

#define MODE_P2P_TARGET                   0
#define MODE_P2P_INITIATOR                1

#define NUM_MAX_TECH 10 //max number of supported technologies


/* Properties values */
#define PROPERTY_LLCP_LTO                 0
#define PROPERTY_LLCP_MIU                 1
#define PROPERTY_LLCP_WKS                 2
#define PROPERTY_LLCP_OPT                 3
#define PROPERTY_NFC_DISCOVERY_A          4
#define PROPERTY_NFC_DISCOVERY_B          5  
#define PROPERTY_NFC_DISCOVERY_F          6
#define PROPERTY_NFC_DISCOVERY_15693      7
#define PROPERTY_NFC_DISCOVERY_NCFIP      8                     

/* Error codes */
#define ERROR_BUFFER_TOO_SMALL            -12
#define ERROR_INSUFFICIENT_RESOURCES      -9

/* Pre-defined card read/write state values. These must match the values in
 * Ndef.java in the framework.
 */

#define NDEF_UNKNOWN_TYPE                -1
#define NDEF_TYPE1_TAG                   1
#define NDEF_TYPE2_TAG                   2
#define NDEF_TYPE3_TAG                   3
#define NDEF_TYPE4_TAG                   4
#define NDEF_MIFARE_CLASSIC_TAG          101
#define NDEF_ICODE_SLI_TAG               102

/* Pre-defined tag type values. These must match the values in
 * Ndef.java in the framework.
 */

#define NDEF_MODE_READ_ONLY              1
#define NDEF_MODE_READ_WRITE             2
#define NDEF_MODE_UNKNOWN                3


/* Name strings for target types. These *must* match the values in TagTechnology.java */
#define TARGET_TYPE_UNKNOWN               -1
#define TARGET_TYPE_ISO14443_3A           1
#define TARGET_TYPE_ISO14443_3B           2
#define TARGET_TYPE_ISO14443_4            3
#define TARGET_TYPE_FELICA                4
#define TARGET_TYPE_ISO15693              5
#define TARGET_TYPE_NDEF                  6
#define TARGET_TYPE_NDEF_FORMATABLE       7
#define TARGET_TYPE_MIFARE_CLASSIC        8
#define TARGET_TYPE_MIFARE_UL             9
#define TARGET_TYPE_KOVIO_BAR_CODE        10

#define SMX_SECURE_ELEMENT_ID   11259375

/* Maximum byte length of an AID. */
#define AID_MAXLEN                        16

// mtk implementation with NCI spec

/* RF Field Technologies */
#define NFC_RF_TECHNOLOGY_A     0
#define NFC_RF_TECHNOLOGY_B     1
#define NFC_RF_TECHNOLOGY_F     2
#define NFC_RF_TECHNOLOGY_15693 3
typedef UINT8 MTK_NFC_DEV_RF_TECH_T;


/* Supported Protocols */
#define NFC_DEV_PROTOCOL_UNKNOWN    0x00  // Unknown
#define NFC_DEV_PROTOCOL_T1T        0x01  // T1T NFC-A
#define NFC_DEV_PROTOCOL_T2T        0x02  // T2T NFC-A
#define NFC_DEV_PROTOCOL_T3T        0x03  // T3T NFC-F
#define NFC_DEV_PROTOCOL_ISO_DEP    0x04  // 4A,4B can be NFC-A or NFC-B
#define NFC_DEV_PROTOCOL_NFC_DEP    0x05  // NFCDEP or LLCP can be NFC-A or NFC-F       */
#define NFC_DEV_PROTOCOL_15693      0x81  // NCI_RF_PROTOCOL_V
#define NFC_DEV_PROTOCOL_BPRIME     0x82  // NCI_RF_PROTOCOL_BP
#define NFC_DEV_PROTOCOL_KOVIO      0x83  // NCI_RF_PROTOCOL_K
typedef UINT8 MTK_NFC_DEV_PROTOCOL_T;

/* Discovery Types/Detected Technology and Mode */
#define NFC_DEV_DISCOVERY_TYPE_POLL_A           0x00 // NCI_NFC_A_PASSIVE_POLL_MODE
#define NFC_DEV_DISCOVERY_TYPE_POLL_B           0x01 // NCI_NFC_B_PASSIVE_POLL_MODE
#define NFC_DEV_DISCOVERY_TYPE_POLL_F           0x02 // NCI_NFC_F_PASSIVE_POLL_MODE
#define NFC_DEV_DISCOVERY_TYPE_POLL_A_ACTIVE    0x03 // NCI_NFC_A_ACTIVE_POLL_MODE
#define NFC_DEV_DISCOVERY_TYPE_POLL_F_ACTIVE    0x05 // NCI_NFC_F_ACTIVE_POLL_MODE
#define NFC_DEV_DISCOVERY_TYPE_POLL_ISO15693    0x06 // NCI_NFC_15693_PASSIVE_POLL_MODE
#define NFC_DEV_DISCOVERY_TYPE_POLL_BP_PASSIVE  0x70 // NCI_NFC_BP_PASSIVE_POLL_MODE
#define NFC_DEV_DISCOVERY_TYPE_POLL_K_PASSIVE   0x71 // NCI_NFC_KOVIO_PASSIVE_POLL_MODE
#define NFC_DEV_DISCOVERY_TYPE_LISTEN_A         0x80 // NCI_NFC_A_PASSIVE_LISTEN_MODE
#define NFC_DEV_DISCOVERY_TYPE_LISTEN_B         0x81 // NCI_NFC_B_PASSIVE_LISTEN_MODE
#define NFC_DEV_DISCOVERY_TYPE_LISTEN_F         0x82 // NCI_NFC_F_PASSIVE_LISTEN_MODE
#define NFC_DEV_DISCOVERY_TYPE_LISTEN_A_ACTIVE  0x83 // NCI_NFC_A_ACTIVE_LISTEN_MODE
#define NFC_DEV_DISCOVERY_TYPE_LISTEN_F_ACTIVE  0x85 // NCI_NFC_F_ACTIVE_LISTEN_MODE
#define NFC_DEV_DISCOVERY_TYPE_LISTEN_ISO15693  0x86 // NCI_NFC_15693_PASSIVE_LISTEN_MODE

typedef UINT8 MTK_NFC_DEV_DISCOVERY_TYPE_T;

/* Bit Rates */
#define NFC_DEV_BIT_RATE_106        0x00 // NCI_NFC_BIT_RATE_106
#define NFC_DEV_BIT_RATE_212        0x01 // NCI_NFC_BIT_RATE_212
#define NFC_DEV_BIT_RATE_424        0x02 // NCI_NFC_BIT_RATE_424
#define NFC_DEV_BIT_RATE_848        0x03 // NCI_NFC_BIT_RATE_848
#define NFC_DEV_BIT_RATE_1695       0x04 // NCI_NFC_BIT_RATE_1695
#define NFC_DEV_BIT_RATE_3390       0x05 // NCI_NFC_BIT_RATE_3390
#define NFC_DEV_BIT_RATE_6780       0x06 // NCI_NFC_BIT_RATE_6780
typedef UINT8 MTK_NFC_DEV_BIT_RATE_T;

/**********************************************
 * Interface Types
 **********************************************/
#define NFC_DEV_INTERFACE_EE_DIRECT_RF  0x00 // NCI_RF_INTERFACE_NFCEE_DIRECT
#define NFC_DEV_INTERFACE_FRAME         0x01 // NCI_RF_INTERFACE_FRAME
#define NFC_DEV_INTERFACE_ISO_DEP       0x02 // NCI_RF_INTERFACE_ISO_DEP
#define NFC_DEV_INTERFACE_NFC_DEP       0x03 // NCI_RF_INTERFACE_NFC_DEP
#define NFC_DEV_INTERFACE_NFC_UNKNOWN   0xFE // NCI_RF_INTERFACE_UNDETERMINED


typedef UINT8 MTK_NFC_DEV_INTERFACE_TYPE_T;

/**********************************************
 *  Deactivation Type
 **********************************************/
#define NFC_DEV_DEACTIVATE_TYPE_IDLE        0x00 // NCI_DEACTIVATE_TYPE_IDLE_MODE
#define NFC_DEV_DEACTIVATE_TYPE_SLEEP       0x01 // NCI_DEACTIVATE_TYPE_SLEEP_MODE
#define NFC_DEV_DEACTIVATE_TYPE_SLEEP_AF    0x02 // NCI_DEACTIVATE_TYPE_SLEEP_AF_MODE
#define NFC_DEV_DEACTIVATE_TYPE_DISCOVERY   0x03 // NCI_DEACTIVATE_TYPE_DISCOVERY
typedef UINT8 MTK_NFC_DEV_DEACTIVATE_TYPE_T;

/**********************************************
 *  Deactivation Reasons
 **********************************************/
#if 0
#define NFC_DEACTIVATE_REASON_DH_REQ        NCI_DEACTIVATE_REASON_DH_REQ
#define NFC_DEACTIVATE_REASON_ENDPOINT_REQ  NCI_DEACTIVATE_REASON_ENDPOINT_REQ
#define NFC_DEACTIVATE_REASON_RF_LINK_LOSS  NCI_DEACTIVATE_REASON_RF_LINK_LOSS
#define NFC_DEACTIVATE_REASON_NFCB_BAD_AFI  NCI_DEACTIVATE_REASON_NFCB_BAD_AFI
typedef UINT8 tNFC_DEACT_REASON;
#endif


/*LLCP Actions*/
#define NFC_LLCP_CONNECT     0x00 // 
#define NFC_LLCP_SEND        0x01 // 
#define NFC_LLCP_RECEIVE     0x02 // 
#define NFC_LLCP_CLOSE       0x03 // 
#define NFC_LLCP_GET_PARA    0x04 // 
#define NFC_LLCP_ACCEPT      0x05 // 
#define NFC_LLCP_ACT_END     0x06 // 
typedef UINT8 MTK_NFC_NFC_LLCP_ACT_T;
/**********************************************
 *  LLCP Actions
 **********************************************/
#define NFC_LLCP_SERVICE_NUM  (9)

/* Utility macros for logging */
#define GET_LEVEL(status) ((status)==NFCSTATUS_SUCCESS)?ANDROID_LOG_DEBUG:ANDROID_LOG_WARN

#if 0
  #define LOG_CALLBACK(funcName, status)  LOG_PRI(GET_LEVEL(status), LOG_TAG, "Callback: %s() - status=0x%04x[%s]", funcName, status, nfc_jni_get_status_name(status));
  #define TRACE(...) ALOG(LOG_DEBUG, LOG_TAG, __VA_ARGS__)
  #define TRACE_ENABLED 1
#else
  #define LOG_CALLBACK(...)
  #define TRACE(...) ALOGD(__VA_ARGS__)
  #define TRACE_ENABLED 0
#endif

struct nfc_jni_native_data
{
   /* Thread handle */
   pthread_t thread;
   int running;

   /* Our VM */
   JavaVM *vm;
   int env_version;

   /* Reference to the NFCManager instance */
   jobject manager;

   /* Cached objects */
   jobject cached_NfcTag;
   jobject cached_P2pDevice;

   /* Target discovery configuration */
//   int discovery_modes_state[DISCOVERY_MODE_TABLE_SIZE];
//   phLibNfc_sADD_Cfg_t discovery_cfg;
//   phLibNfc_Registry_Info_t registry_info;
   
   /* Secure Element selected */
   int seId;
   
   /* LLCP params */
   int lto;
   int miu;
   int wks;
   int opt;

   /* Tag detected */
   jobject tag;

   /* Lib Status */
//   NFCSTATUS status;

   /* p2p modes */
   int p2p_initiator_modes;
   int p2p_target_modes;

   int p2p_role;
// discover config
   
   s_mtk_nfc_service_set_discover_req_t rDiscoverConfig;
   unsigned char fgPollingEnabled;
   unsigned char u1DevActivated; // 0 deactivated, 1: Sleep, 2: activated
   unsigned char fgChangeToFrameRfInterface;
   unsigned char fgHostRequestDeactivate;
   unsigned int u4NumDicoverDev;
   s_mtk_discover_device_ntf_t rDiscoverDev[DISCOVERY_DEV_MAX_NUM];
   s_mtk_nfc_service_activate_param_t rDevActivateParam;
   s_mtk_nfc_service_tag_param_t rDevTagParam;

   unsigned int au4TechList[NUM_MAX_TECH]; //technology of the device
   unsigned int au4TechIndex[NUM_MAX_TECH]; //Index of the device
   MTK_NFC_HANDLER apTechHandles[NUM_MAX_TECH]; //the device handle count
   unsigned int au4DevId[NUM_MAX_TECH]; //the device discover id
   unsigned int au4TechProtocols[NUM_MAX_TECH]; //protocol of the device
   unsigned int u4NumDevTech; //Number of discovered technology
   MTK_NFC_DEV_INTERFACE_TYPE_T CurrentRfInterface; // the interface used now 
   
};

typedef struct nfc_jni_native_monitor
{
   /* Mutex protecting native library against reentrance */
   pthread_mutex_t reentrance_mutex;

   /* Mutex protecting native library against concurrency */
   pthread_mutex_t concurrency_mutex;

   /* List used to track pending semaphores waiting for callback */
   struct listHead sem_list;

   /* List used to track incoming socket requests (and associated sync variables) */
   LIST_HEAD(, nfc_jni_listen_data) incoming_socket_head;
   pthread_mutex_t incoming_socket_mutex[NFC_LLCP_SERVICE_NUM];
   pthread_cond_t  incoming_socket_cond[NFC_LLCP_SERVICE_NUM];

} nfc_jni_native_monitor_t;

typedef struct nfc_jni_callback_data
{
   /* Semaphore used to wait for callback */
   sem_t sem;

   /* Used to store the status sent by the callback */
   NFCSTATUS status;

   /* Used to provide a local context to the callback */
   void* pContext;

} nfc_jni_callback_data_t;

typedef struct nfc_jni_listen_data
{
   /* LLCP server socket receiving the connection request */
   MTK_NFC_HANDLER pServerSocket;

   /* LLCP socket created from the connection request */
   MTK_NFC_HANDLER pIncomingSocket;

   /* List entries */
   LIST_ENTRY(nfc_jni_listen_data) entries;

} nfc_jni_listen_data_t;

/* TODO: treat errors and add traces */
#define REENTRANCE_LOCK()        pthread_mutex_lock(&nfc_jni_get_monitor()->reentrance_mutex)
#define REENTRANCE_UNLOCK()      pthread_mutex_unlock(&nfc_jni_get_monitor()->reentrance_mutex)
#define CONCURRENCY_LOCK()       pthread_mutex_lock(&nfc_jni_get_monitor()->concurrency_mutex)
#define CONCURRENCY_UNLOCK()     pthread_mutex_unlock(&nfc_jni_get_monitor()->concurrency_mutex)

namespace android {

extern JavaVM *vm;

JNIEnv *nfc_get_env();

bool nfc_cb_data_init(nfc_jni_callback_data* pCallbackData, void* pContext);
void nfc_cb_data_deinit(nfc_jni_callback_data* pCallbackData);
void nfc_cb_data_releaseAll();

int nfc_jni_cache_object(JNIEnv *e, const char *clsname,
   jobject *cached_obj);
struct nfc_jni_native_data* nfc_jni_get_nat(JNIEnv *e, jobject o);
struct nfc_jni_native_data* nfc_jni_get_nat_ext(JNIEnv *e);
nfc_jni_native_monitor_t* nfc_jni_init_monitor(void);
nfc_jni_native_monitor_t* nfc_jni_get_monitor(void);

#if 0
int get_technology_type(phNfc_eRemDevType_t type, uint8_t sak);
void nfc_jni_get_technology_tree(JNIEnv* e, phLibNfc_RemoteDevList_t* devList,
                        uint8_t count, jintArray* techList, jintArray* handleList,
                        jintArray* typeList);
#endif

int android_nfc_jni_send_msg(unsigned int type, unsigned int len, void *payload);
void kill_mtk_client(nfc_jni_native_data *nat);


/* P2P */
MTK_NFC_HANDLER nfc_jni_get_p2p_device_handle(JNIEnv *e, jobject o);
jshort nfc_jni_get_p2p_device_mode(JNIEnv *e, jobject o);
#ifdef DTA_SUPPORT
void nfc_jni_doDepExchange_callback(void *pContext, NFCSTATUS status);
#endif

/* TAG */
jint nfc_jni_get_connected_technology(JNIEnv *e, jobject o);
jint nfc_jni_get_connected_technology_libnfc_type(JNIEnv *e, jobject o);
MTK_NFC_HANDLER nfc_jni_get_connected_handle(JNIEnv *e, jobject o);
jintArray nfc_jni_get_nfc_tag_type(JNIEnv *e, jobject o);
//
void nfc_jni_ckeck_ndef_callback(void *pContext, NFCSTATUS status);
void nfc_jni_doTransceive_callback(void *pContext, NFCSTATUS status);
void nfc_jni_doRead_callback(void *pContext, NFCSTATUS status);
void nfc_jni_doWrite_callback(void *pContext, NFCSTATUS status);
void nfc_jni_doPresenceCK_callback(void *pContext, NFCSTATUS status);
void nfc_jni_doFormat_callback(void *pContext, NFCSTATUS status);
void nfc_jni_doMakereadOnly_callback(void *pContext, NFCSTATUS status);

//


/* LLCP */
MTK_NFC_HANDLER nfc_jni_get_nfc_socket_handle(JNIEnv *e, jobject o);
void nfc_jni_disconnect_callback(MTK_NFC_LLCP_RSP* Result);
void nfc_jni_connect_callback(unsigned char nErrCode, int status);
void nfc_jni_llcp_receive_callback(int  status, unsigned char sap, unsigned char* Buf, unsigned int Length, unsigned int service);
void nfc_jni_llcp_send_callback(MTK_NFC_LLCP_RSP* Result);
void nfc_jni_llcp_accept_socket_callback(int  status);
void nfc_jni_llcp_remote_setting_callback(MTK_NFC_GET_REM_SOCKET_RSP* Result);

int register_com_android_nfc_NativeNfcManager(JNIEnv *e);
int register_com_android_nfc_NativeNfcTag(JNIEnv *e);
int register_com_android_nfc_NativeP2pDevice(JNIEnv *e);
int register_com_android_nfc_NativeLlcpConnectionlessSocket(JNIEnv *e);
int register_com_android_nfc_NativeLlcpServiceSocket(JNIEnv *e);
int register_com_android_nfc_NativeLlcpSocket(JNIEnv *e);
int register_com_android_nfc_NativeNfcSecureElement(JNIEnv *e);


void nfc_jni_se_open_conn_callback(void *pContext, NFCSTATUS status, unsigned int conn_id);
void nfc_jni_se_close_conn_callback(void *pContext, NFCSTATUS status);
void nfc_jni_se_send_data_callback(void *pContext, s_mtk_nfc_jni_se_send_data_rsp *SeReceiveData);

} // namespace android

#endif
