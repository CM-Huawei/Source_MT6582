/*
 * Copyright 2008, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "wifi"

#include "jni.h"
#include <ScopedUtfChars.h>
#include <utils/misc.h>
#include <android_runtime/AndroidRuntime.h>
#include <utils/Log.h>
#include <utils/String16.h>



#include <utils/String8.h>
#include <cutils/xlog.h>
#include <pthread.h>
#include <unicode/ucnv.h>
#include <unicode/ucsdet.h>
#include <net/if.h>
#include <sys/socket.h>
#include <linux/wireless.h>
#include <cutils/properties.h>

#include "wifi.h"

#define REPLY_BUF_SIZE 4096 // wpa_supplicant's maximum size.
#define EVENT_BUF_SIZE 2048

///M: add the following
#define BUF_SIZE 256
#define SSID_LEN 500
#define LINE_LEN 1024
#define CONVERT_LINE_LEN 2048
#define REPLY_LEN 4096
#define CHARSET_CN ("gbk")
#define WIFI_INTERFACE "wlan0"

#define IOCTL_SET_INTS                   (SIOCIWFIRSTPRIV + 12)
#define PRIV_CMD_SET_TX_POWER            25

namespace android {

static jint DBG = false;

struct accessPointObjectItem {
    String8 *ssid;
    String8 *bssid;
    bool    isCh;
    struct  accessPointObjectItem *pNext;
};

struct accessPointObjectItem *g_pItemList = NULL;
struct accessPointObjectItem *g_pLastNode = NULL;
pthread_mutex_t *g_pItemListMutex = NULL;
String8 *g_pCurrentSSID = NULL;
bool g_isChSSID = false;

static void addAPObjectItem(const char *ssid, const char *bssid, bool isCh)
{
    if (NULL == ssid || NULL == bssid) {
        XLOGE("ssid or bssid is NULL");
        return;
    }

    struct accessPointObjectItem *pTmpItemNode = NULL;
    struct accessPointObjectItem *pItemNode = NULL;
    bool foundItem = false;
    pthread_mutex_lock(g_pItemListMutex);
    pTmpItemNode = g_pItemList;
    while (pTmpItemNode) {
        if (pTmpItemNode->bssid && (*(pTmpItemNode->bssid) == bssid)) {
            foundItem = true;
            break;
        }
        pTmpItemNode = pTmpItemNode->pNext;
    }
    if (foundItem) {
        *(pTmpItemNode->ssid) = ssid;
        pTmpItemNode->isCh = isCh;
        if (DBG) {
            XLOGD("Found AP %s", pTmpItemNode->ssid->string());
        }
    } else {
        pItemNode = new struct accessPointObjectItem();
        if (NULL == pItemNode) {
            XLOGE("Failed to allocate memory for new item!");
            goto EXIT;
        }
        memset(pItemNode, 0, sizeof(accessPointObjectItem));
        pItemNode->bssid = new String8(bssid);
        if (NULL == pItemNode->bssid) {
            XLOGE("Failed to allocate memory for new bssid!");
            delete pItemNode;
            goto EXIT;
        }
        pItemNode->ssid = new String8(ssid);
        if (NULL == pItemNode->ssid) {
            XLOGE("Failed to allocate memory for new ssid!");
            delete pItemNode->bssid;
            delete pItemNode;
            goto EXIT;
        }
        pItemNode->isCh = isCh;
        pItemNode->pNext = NULL;
        if (DBG) {
            XLOGD("AP doesn't exist, new one for %s", ssid);
        }

        if (NULL == g_pItemList) {
            g_pItemList = pItemNode;
            g_pLastNode = g_pItemList;
        } else {
            g_pLastNode->pNext = pItemNode;
            g_pLastNode = pItemNode;
        }
    }

EXIT:
    pthread_mutex_unlock(g_pItemListMutex);
}

static bool isUTF8String(const char* str, long length)
{
    unsigned int nBytes = 0;
    unsigned char chr;
    bool bAllAscii = true;
    for (int i = 0; i < length; i++) {
        chr = *(str+i);
        if ((chr & 0x80) != 0) {
            bAllAscii = false;
        }
        if (0 == nBytes) {
            if (chr >= 0x80) {
                if (chr >= 0xFC && chr <= 0xFD) {
                    nBytes = 6;
                } else if (chr >= 0xF8) {
                    nBytes = 5;
                } else if (chr >= 0xF0) {
                    nBytes = 4;
                } else if (chr >= 0xE0) {
                    nBytes = 3;
                } else if (chr >= 0xC0) {
                    nBytes = 2;
                } else {
                    return false;
                }
                nBytes--;
            }
        } else {
            if ((chr & 0xC0) != 0x80) {
                return false;
            }
            nBytes--;
        }
    }

    if (nBytes > 0 || bAllAscii) {
        return false;
    }
    return true;
}

static void parseScanResults(String16& str, const char *reply)
{
    unsigned int lineBeg = 0, lineEnd = 0;
    size_t  replyLen = strlen(reply);
    char    *pos = NULL;
    char    bssid[BUF_SIZE] = {0};
    char    ssid[BUF_SIZE] = {0};
    String8 line;
    bool    isUTF8 = true;
    bool    isCh = false;

    UChar dest[CONVERT_LINE_LEN] = {0};
    UErrorCode err = U_ZERO_ERROR;
    UConverter* pConverter = ucnv_open(CHARSET_CN, &err);
    if (U_FAILURE(err)) {
        XLOGE("ucnv_open error");
        return;
    }
    //Parse every line of the reply to construct accessPointObjectItem list
    for (lineBeg = 0, lineEnd = 0; lineEnd <= replyLen; ++lineEnd) {
        if (lineEnd == replyLen || '\n' == reply[lineEnd]) {
            line.setTo(reply + lineBeg, lineEnd - lineBeg + 1);
            /*if (DBG) {
                XLOGD("%s, line:%s", __FUNCTION__, line.string());
            }*/
            if (strncmp(line.string(), "bssid=", 6) == 0) {
                sscanf(line.string() + 6, "%[^\n]", bssid);
            } else if (strncmp(line.string(), "ssid=", 5) == 0) {
                sscanf(line.string() + 5, "%[^\n]", ssid);
                isUTF8 = isUTF8String(ssid, strlen(ssid));
            } else if (strncmp(line.string(), "====", 4) == 0) {
                isCh = false;
                for (pos = ssid; '\0' != *pos; pos++) {
                    if (0x80 == (*pos & 0x80)) {
                        isCh = true;
                        break;
                    }
                }
                if (DBG) {
                    XLOGD("After sscanf, bssid:%s, ssid:%s, isCh:%d, isUTF8:%d", bssid, ssid, isCh, isUTF8);
                    for (int i = 0; i < strlen(ssid); i++) {
                        XLOGD("ssid[%d]:%c, 0x%x", i, ssid[i], ssid[i]);
                    }
                }
                addAPObjectItem(ssid, bssid, isCh);
            }
            if (!isUTF8) {
                ucnv_toUChars(pConverter, dest, CONVERT_LINE_LEN, line.string(), line.size(), &err);
                if (U_FAILURE(err)) {
                    XLOGE("ucnv_toUChars error");
                    goto EXIT;
                }
                str += String16(dest);
                memset(dest, 0, CONVERT_LINE_LEN);
            } else {
                str += String16(line.string());
            }
            lineBeg = lineEnd + 1;
        }
    }

EXIT:
    ucnv_close(pConverter);
}

static void constructReply(String16& str, const char *cmd, const char *reply)
{
    if (DBG) {
        XLOGD("%s, cmd = %s, reply = %s", __FUNCTION__, cmd, reply);
    }
    size_t 	replyLen = strlen(reply);
    unsigned int lineBeg = 0, lineEnd = 0;
    String8 line;
    bool isUTF8 = true;
    UChar dest[CONVERT_LINE_LEN] = {0};
    UErrorCode err = U_ZERO_ERROR;
    UConverter* pConverter = ucnv_open(CHARSET_CN, &err);
    if (U_FAILURE(err)) {
        XLOGE("ucnv_open error");
        return;
    }

    for (lineBeg = 0, lineEnd = 0; lineEnd <= replyLen; ++lineEnd) {
        if (lineEnd == replyLen || '\n' == reply[lineEnd]) {
            line.setTo(reply + lineBeg, lineEnd - lineBeg + 1);
            isUTF8 = isUTF8String(line.string(), line.size());
            if (DBG) {
                XLOGD("%s, line=%s, isUTF8=%d", __FUNCTION__, line.string(), isUTF8);
            }
            if (!isUTF8) {
                ucnv_toUChars(pConverter, dest, CONVERT_LINE_LEN, line.string(), line.size(), &err);
                if (U_FAILURE(err)) {
                    XLOGE("ucnv_toUChars error");
                    goto EXIT;
                }
                str += String16(dest);
                memset(dest, 0, CONVERT_LINE_LEN);
            } else {
                str += String16(line.string());
            }
            lineBeg = lineEnd + 1;
        }
    }

EXIT:
    ucnv_close(pConverter);
}

static void printLongReply(const char *reply) {
    if (DBG) {
        for (int i = 0; i < strlen(reply); i++) {
            XLOGD("reply[%d]:%c, 0x%x", i, reply[i], reply[i]);
        }
    }
    unsigned int lineBeg = 0, lineEnd = 0;
    size_t replyLen = strlen(reply);
    String8 line;
    for (lineBeg = 0, lineEnd = 0; lineEnd <= replyLen; ++lineEnd) {
        if (lineEnd == replyLen || '\n' == reply[lineEnd]) {
            line.setTo(reply + lineBeg, lineEnd - lineBeg + 1);
            XLOGI("%s", line.string());
            lineBeg = lineEnd + 1;
        }
    }
}

static bool doCommand(JNIEnv* env, jstring javaCommand,
                      char* reply, size_t reply_len) {
    ScopedUtfChars command(env, javaCommand);
    if (command.c_str() == NULL) {
        return false; // ScopedUtfChars already threw on error.
    }

    if (DBG) {
        ALOGD("doCommand: %s", command.c_str());
    }

    --reply_len; // Ensure we have room to add NUL termination.
    if (::wifi_command(command.c_str(), reply, &reply_len) != 0) {
        return false;
    }

    // Strip off trailing newline.
    if (reply_len > 0 && reply[reply_len-1] == '\n') {
        reply[reply_len-1] = '\0';
    } else {
        reply[reply_len] = '\0';
    }
    return true;
}

static jint doIntCommand(JNIEnv* env, jstring javaCommand) {
    char reply[REPLY_BUF_SIZE]= {0};
    if (!doCommand(env, javaCommand, reply, sizeof(reply))) {
        return -1;
    }
    return static_cast<jint>(atoi(reply));
}

static jboolean doBooleanCommand(JNIEnv* env, jstring javaCommand) {
    char reply[REPLY_BUF_SIZE]= {0};
    ScopedUtfChars jcmd(env, javaCommand);
    XLOGD("doBooleanCommand %s", jcmd.c_str());
    if (!doCommand(env, javaCommand, reply, sizeof(reply))) {
        return JNI_FALSE;
    }
    return (strcmp(reply, "OK") == 0);
}





static int doCommandTranslated(const char *cmd, char *replybuf, int replybuflen)
{
    size_t reply_len = replybuflen - 1;

    if (DBG) {
        ALOGD("doCommand: %s", cmd);
    }
    if (::wifi_command(cmd, replybuf, &reply_len) != 0)
        return -1;
    else {
        // Strip off trailing newline
        if (reply_len > 0 && replybuf[reply_len-1] == '\n')
            replybuf[reply_len-1] = '\0';
        else
            replybuf[reply_len] = '\0';
        return 0;
    }
}

static jboolean doBooleanCommandTranslated(const char* expect, const char* fmt, ...)
{
    char buf[BUF_SIZE] = {0};
    va_list args;
    va_start(args, fmt);
    int byteCount = vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    if (byteCount < 0 || byteCount >= BUF_SIZE) {
        return JNI_FALSE;
    }
    char reply[BUF_SIZE] = {0};
    if (doCommandTranslated(buf, reply, sizeof(reply)) != 0) {
        return JNI_FALSE;
    }
    return (strcmp(reply, expect) == 0);
}

// Send a command to the supplicant, and return the reply as a String.
static jstring doStringCommand(JNIEnv* env, jstring javaCommand) {

    char reply[REPLY_BUF_SIZE]= {0};
    char buf[BUF_SIZE] = {0};
    
    if (!doCommand(env, javaCommand, reply, sizeof(reply))) {
        return NULL;
    }
    ///M:@{
    const char *nativeString = env->GetStringUTFChars(javaCommand, 0);
    int isWlanInterafce = strncmp("IFNAME=wlan",nativeString,strlen("IFNAME=wlan"));
    if(nativeString!=NULL)strncpy(buf,nativeString,BUF_SIZE);
    env->ReleaseStringUTFChars(javaCommand, nativeString);
    XLOGD("%s, buf %s", __FUNCTION__, buf);
    if (0 == isWlanInterafce) {
        String16 str;
        if (strstr(buf, "MASK=0x21987") ||
            (strstr(buf, "GET_NETWORK") && strstr(buf, "ssid")) ||
            (0 == strcmp(buf, "STATUS")) ||
            (0 == strcmp(buf, "LIST_NETWORKS")) ||
            (0 == strcmp(buf,"DRIVER WLS_BATCHING GET")) ){
            if (strstr(buf, "MASK=0x21987")) {
                printLongReply(reply);
                parseScanResults(str, reply);
            } else {
                if (DBG) {
                    printLongReply(reply);
                }
                constructReply(str, buf, reply);
            }
        } else {
            if (DBG) {
                printLongReply(reply);
            }
            str += String16((char *)reply);
        }
        return env->NewString((const jchar *)str.string(), str.size());
    } else {
        if (DBG) {
            printLongReply(reply);
        }
        jclass stringClass;
        jmethodID methodId;
        jbyteArray byteArray;
        stringClass = env->FindClass("java/lang/String");
        if (NULL == stringClass) {
            XLOGE("Failed to find String class!");
            return NULL;
        }
        methodId = env->GetMethodID(stringClass,"<init>", "([B)V");
        if (NULL == methodId) {
            XLOGE("Failed to get constructor methodId!");
            env->DeleteLocalRef(stringClass);
            return NULL;
        }
        byteArray = env->NewByteArray(strlen(reply));
        if (NULL == byteArray) {
            XLOGE("Failed to new byte array!");
            env->DeleteLocalRef(stringClass);
            return NULL;
        }
        env->SetByteArrayRegion(byteArray, 0, strlen(reply), (jbyte*)reply);
        jstring result = (jstring)(env->NewObject(stringClass, methodId, byteArray));
        env->DeleteLocalRef(byteArray);
        env->DeleteLocalRef(stringClass);
        return result;
    }
    ///@}
}

static jboolean android_net_wifi_isDriverLoaded(JNIEnv* env, jobject)
{
    return (::is_wifi_driver_loaded() == 1);
}

static jboolean android_net_wifi_loadDriver(JNIEnv* env, jobject)
{
    ///M:@{
    g_pItemListMutex = new pthread_mutex_t;
    if (NULL == g_pItemListMutex) {
        XLOGE("Failed to allocate memory for g_pItemListMutex!");
        return JNI_FALSE;
    }
    pthread_mutex_init(g_pItemListMutex, NULL);
    g_pCurrentSSID = new String8();
    if (NULL == g_pCurrentSSID) {
        XLOGE("Failed to allocate memory for g_pCurrentSSID!");
        return JNI_FALSE;
    }
    char dbg[PROPERTY_VALUE_MAX] = {0};
    if (property_get("wifi.jni.dbg", dbg, NULL) && strcmp(dbg, "true") == 0) {
        DBG = true;
    } else {
        DBG = false;
    }
    ///@}
    return (::wifi_load_driver() == 0);
}

static jboolean android_net_wifi_unloadDriver(JNIEnv* env, jobject)
{
    ///M:@{
    if (g_pCurrentSSID != NULL) {
        delete g_pCurrentSSID;
        g_pCurrentSSID = NULL;
    }
    if (g_pItemListMutex != NULL) {
        pthread_mutex_lock(g_pItemListMutex);
        struct accessPointObjectItem *pCurrentNode = g_pItemList;
        struct accessPointObjectItem *pNextNode = NULL;
        while (pCurrentNode) {
            pNextNode = pCurrentNode->pNext;
            if (NULL != pCurrentNode->ssid) {
                delete pCurrentNode->ssid;
                pCurrentNode->ssid = NULL;
            }
            if (NULL != pCurrentNode->bssid) {
                delete pCurrentNode->bssid;
                pCurrentNode->bssid = NULL;
            }
            delete pCurrentNode;
            pCurrentNode = pNextNode;
        }
        g_pItemList = NULL;
        g_pLastNode = NULL;
        pthread_mutex_unlock(g_pItemListMutex);
        pthread_mutex_destroy(g_pItemListMutex);
        delete g_pItemListMutex;
        g_pItemListMutex = NULL;
    }
    ///@}
    return (::wifi_unload_driver() == 0);
}

static jboolean android_net_wifi_startSupplicant(JNIEnv* env, jobject, jboolean p2pSupported)
{
    return (::wifi_start_supplicant(p2pSupported) == 0);
}

static jboolean android_net_wifi_killSupplicant(JNIEnv* env, jobject, jboolean p2pSupported)
{
    return (::wifi_stop_supplicant(p2pSupported) == 0);
}

static jboolean android_net_wifi_connectToSupplicant(JNIEnv* env, jobject)
{
    return (::wifi_connect_to_supplicant() == 0);
}

static void android_net_wifi_closeSupplicantConnection(JNIEnv* env, jobject)
{
    ::wifi_close_supplicant_connection();
}

static jstring android_net_wifi_waitForEvent(JNIEnv* env, jobject, jstring jIface)
{
    char buf[EVENT_BUF_SIZE]= {0};
    ScopedUtfChars ifname(env, jIface);
    int nread = ::wifi_wait_for_event(buf, sizeof buf);
    if (nread > 0) {
        ///M:@{
        //return env->NewStringUTF(buf);
        if (DBG) {
            XLOGD("android_net_wifi_waitForEvent ifname.c_str()= %s, WIFI_INTERFACE = %s",ifname.c_str(),WIFI_INTERFACE);
        }
        if (DBG) {
            XLOGD("cmd = %s", buf);
        }
  ///M:@{

        int isWlanInterafce = strncmp("IFNAME=wlan",buf,strlen("IFNAME=wlan"));
      

        if (isWlanInterafce==0) {
        //if (0 == strcmp(ifname.c_str(), WIFI_INTERFACE)) {
            if (DBG) {
                XLOGD("wlan0 case");
            }
            String16 str;
            constructReply(str, NULL, buf);
            return env->NewString((const jchar *)str.string(), str.size());
        } else {
            if (DBG) {
                XLOGD("non wlan0 case");
            }
            jclass stringClass;
            jmethodID methodId;
            jbyteArray byteArray;
            stringClass = env->FindClass("java/lang/String");
            if (NULL == stringClass) {
                XLOGE("Failed to find String class!");
                return NULL;
            }
            methodId = env->GetMethodID(stringClass,"<init>", "([B)V");
            if (NULL == methodId) {
                XLOGE("Failed to get constructor methodId!");
                env->DeleteLocalRef(stringClass);
                return NULL;
            }
            byteArray = env->NewByteArray(strlen(buf));
            if (NULL == byteArray) {
                XLOGE("Failed to new byte array!");
                env->DeleteLocalRef(stringClass);
                return NULL;
            }
            env->SetByteArrayRegion(byteArray, 0, strlen(buf), (jbyte*)buf);
            jstring result = (jstring)(env->NewObject(stringClass, methodId, byteArray));
            env->DeleteLocalRef(byteArray);
            env->DeleteLocalRef(stringClass);
            return result;
        }
        ///@}
    } else {
        return NULL;
    }
}

static jboolean android_net_wifi_doBooleanCommand(JNIEnv* env, jobject, jstring javaCommand) {
    return doBooleanCommand(env, javaCommand);
}

static jint android_net_wifi_doIntCommand(JNIEnv* env, jobject, jstring javaCommand) {
    return doIntCommand(env, javaCommand);
}

static jstring android_net_wifi_doStringCommand(JNIEnv* env, jobject, jstring javaCommand) {
    return doStringCommand(env,javaCommand);
}

static jboolean android_net_wifi_setNetworkVariableCommand(JNIEnv* env,
                                                           jobject,
                                                           jstring jIface,
                                                           jint netId,
                                                           jstring javaName,
                                                           jstring javaValue)
{
    char reply[REPLY_LEN] = {0};
    ScopedUtfChars name(env, javaName);
    if (name.c_str() == NULL) {
        return JNI_FALSE;
    }
    ScopedUtfChars value(env, javaValue);
    if (value.c_str() == NULL) {
        return JNI_FALSE;
    }
    ScopedUtfChars ifname(env, jIface);
    if (ifname.c_str() == NULL) {
        return JNI_FALSE;
    }
    XLOGD("setNetworkVariableCommand, name:%s, value:%s, netId:%d", name.c_str(), value.c_str(), netId);
    struct accessPointObjectItem *pTmpItemNode = NULL;
    if (0 == strcmp(name.c_str(), "bssid")) {
        if (NULL == g_pCurrentSSID) {
            XLOGE("g_pCurrentSSID is NULL");
            g_pCurrentSSID = new String8();
            if (NULL == g_pCurrentSSID) {
                XLOGE("Failed to allocate memory for g_pCurrentSSID!");
                return JNI_FALSE;
            }
        }
        pthread_mutex_lock(g_pItemListMutex);
        pTmpItemNode = g_pItemList;   
        if (NULL == pTmpItemNode) {
            XLOGD("g_pItemList is NULL");
        }
        while (pTmpItemNode) {
            if (pTmpItemNode->bssid && (0 == strcmp(pTmpItemNode->bssid->string(), value.c_str())) && pTmpItemNode->ssid) {
                *g_pCurrentSSID = *(pTmpItemNode->ssid);
                g_isChSSID = pTmpItemNode->isCh;
                XLOGD("Found bssid:%s, g_pCurrentSSID:%s, g_isChSSID:%d", pTmpItemNode->bssid->string(), g_pCurrentSSID->string(), g_isChSSID);
                break;
            }
            pTmpItemNode = pTmpItemNode->pNext;
        }
        pthread_mutex_unlock(g_pItemListMutex);
        return JNI_TRUE;
    }

    if (0 == strcmp(name.c_str(), "ssid") && g_isChSSID) {
        if (DBG) {
            ALOGD("g_pCurrentSSID->string: %s", g_pCurrentSSID->string());
        }    
        g_isChSSID = false;
        return doBooleanCommandTranslated("OK", "IFNAME=%s SET_NETWORK %d %s \"%s\"",ifname.c_str(),  netId, name.c_str(), g_pCurrentSSID->string());
    }else{
        return doBooleanCommandTranslated("OK", "IFNAME=%s SET_NETWORK %d %s %s", ifname.c_str(),netId, name.c_str(), value.c_str());
    }
}

static jboolean android_net_wifi_setTxPowerEnabledCommand(JNIEnv* env, jobject clazz, jboolean enable)
{
    jboolean result = JNI_FALSE;
    struct iwreq wrq;
    int skfd;
    int32_t au4TxPwr[2] = {4, 1};
    au4TxPwr[1] = enable ? 1 : 0;

    /* initialize socket */
    skfd = socket(PF_INET, SOCK_DGRAM, 0);
    if (skfd < 0) {
        XLOGE("Open socket failed");
        return result;
    }

    /* initliaze WEXT request */
    wrq.u.data.pointer = &(au4TxPwr[0]);
    wrq.u.data.length = 2;
    wrq.u.data.flags = PRIV_CMD_SET_TX_POWER;
    strncpy(wrq.ifr_name, "wlan0", IFNAMSIZ);

    /* do IOCTL */
    if (ioctl(skfd, IOCTL_SET_INTS, &wrq) < 0) {
        XLOGE("setTxPowerEnabledCommand failed");
    } else {
        XLOGD("setTxPowerEnabledCommand succeed");
        result = JNI_TRUE;
    }
    close(skfd);
    return result;
}

static jboolean android_net_wifi_setTxPowerCommand(JNIEnv* env, jobject clazz, jint offset)
{
    jboolean result = JNI_FALSE;
    struct iwreq wrq;
    int skfd;
    int32_t au4TxPwr[4] = {0, 0, 0, 0};
    au4TxPwr[3] = offset;

    /* initialize socket */
    skfd = socket(PF_INET, SOCK_DGRAM, 0);
    if (skfd < 0) {
        XLOGE("Open socket failed");
        return result;
    }

    /* initliaze WEXT request */
    wrq.u.data.pointer = &(au4TxPwr[0]);
    wrq.u.data.length = 4;
    wrq.u.data.flags = PRIV_CMD_SET_TX_POWER;
    strncpy(wrq.ifr_name, "wlan0", IFNAMSIZ);

    /* do IOCTL */
    if (ioctl(skfd, IOCTL_SET_INTS, &wrq) < 0) {
        XLOGE("setTxPowerCommand failed");
    } else {
        XLOGD("setTxPowerCommand succeed");
        result = JNI_TRUE;
    }
    close(skfd);
    return result;
}

static jstring android_net_wifi_getStringCredential(JNIEnv* env, jobject)
{
    char cred[256] = {0};
    size_t cred_len;
    int status = ::wifi_build_cred(cred, &cred_len);
    return env->NewStringUTF(cred);
}

static jstring android_net_wifi_getP2pDeviceAddress(JNIEnv* env, jobject)
{
    char own_addr[24] = {0};
    int status = ::wifi_get_own_addr(own_addr);
    return env->NewStringUTF(own_addr);
}

// ----------------------------------------------------------------------------

/*
 * JNI registration.
 */
static JNINativeMethod gWifiMethods[] = {
    /* name, signature, funcPtr */

    { "loadDriver", "()Z",  (void *)android_net_wifi_loadDriver },
    { "isDriverLoaded", "()Z",  (void *)android_net_wifi_isDriverLoaded },
    { "unloadDriver", "()Z",  (void *)android_net_wifi_unloadDriver },
    { "startSupplicant", "(Z)Z",  (void *)android_net_wifi_startSupplicant },
    { "killSupplicant", "(Z)Z",  (void *)android_net_wifi_killSupplicant },
    { "connectToSupplicantNative", "()Z", (void *)android_net_wifi_connectToSupplicant },
    { "closeSupplicantConnectionNative", "()V",
            (void *)android_net_wifi_closeSupplicantConnection },
    ///M: modify API
    { "waitForEventNative", "(Ljava/lang/String;)Ljava/lang/String;", (void*)android_net_wifi_waitForEvent },
    { "doBooleanCommandNative", "(Ljava/lang/String;)Z", (void*)android_net_wifi_doBooleanCommand },
    { "doIntCommandNative", "(Ljava/lang/String;)I", (void*)android_net_wifi_doIntCommand },
    { "doStringCommandNative", "(Ljava/lang/String;)Ljava/lang/String;",
            (void*) android_net_wifi_doStringCommand },
///M: add the following
    { "setNetworkVariableCommand", "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;)Z",
            (void*) android_net_wifi_setNetworkVariableCommand },
    { "setTxPowerEnabled", "(Z)Z", (void*) android_net_wifi_setTxPowerEnabledCommand },
    { "setTxPower", "(I)Z", (void*) android_net_wifi_setTxPowerCommand },
    { "getCredential", "()Ljava/lang/String;", (void*) android_net_wifi_getStringCredential },
    { "getP2pDeviceAddress", "()Ljava/lang/String;", (void*) android_net_wifi_getP2pDeviceAddress },
};

int register_android_net_wifi_WifiNative(JNIEnv* env) {
    return AndroidRuntime::registerNativeMethods(env,
            "android/net/wifi/WifiNative", gWifiMethods, NELEM(gWifiMethods));
}

}; // namespace android
