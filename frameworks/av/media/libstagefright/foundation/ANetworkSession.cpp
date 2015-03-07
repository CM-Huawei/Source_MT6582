/*
 * Copyright 2012, The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "NetworkSession"
#include <utils/Log.h>

#include "ANetworkSession.h"
#include "ParsedMessage.h"

#include <arpa/inet.h>
#include <fcntl.h>
#include <net/if.h>
#include <linux/tcp.h>
#include <netdb.h>
#include <netinet/in.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <unistd.h>

#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/foundation/hexdump.h>

///Add by MTK @{
#include <cutils/properties.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include "WifiDisplayUibcType.h"
///@}
namespace android {

static uint16_t U16_AT(const uint8_t *ptr) {
    return ptr[0] << 8 | ptr[1];
}

static uint32_t U32_AT(const uint8_t *ptr) {
    return ptr[0] << 24 | ptr[1] << 16 | ptr[2] << 8 | ptr[3];
}

static uint64_t U64_AT(const uint8_t *ptr) {
    return ((uint64_t)U32_AT(ptr)) << 32 | U32_AT(ptr + 4);
}

static const size_t kMaxUDPSize = 1500;
static const int32_t kMaxUDPRetries = 200;

#define MAX_BUFFER_SIZE         1024
#define THRESHOLD_BUFFER_SIZE   50
#define WARN_BUFFER_SIZE        400

struct ANetworkSession::NetworkThread : public Thread {
    NetworkThread(ANetworkSession *session);

protected:
    virtual ~NetworkThread();

private:
    ANetworkSession *mSession;

    virtual bool threadLoop();

    DISALLOW_EVIL_CONSTRUCTORS(NetworkThread);
};

struct ANetworkSession::Session : public RefBase {
    enum Mode {
        MODE_RTSP,
        MODE_DATAGRAM,
        MODE_WEBSOCKET,
    };

    enum State {
        CONNECTING,
        CONNECTED,
        LISTENING_RTSP,
        LISTENING_TCP_DGRAMS,
        DATAGRAM,
///Add by MTK @{
        LISTENING_TCP_TEXT,
        LISTENING_TCP_UIBC,
        SOCKET_ERROR,
///@}
    };
///Add by MTK @{
    enum TCPType {
        TCP_DATAGRAM = 0,
        TCP_TEXTDATA,
        TCP_UIBC
#ifdef WFD_HDCP_TX_SUPPORT
       ,TCP_BINDATA
#endif
    };
///@}

    Session(int32_t sessionID,
            State state,
            int s,
            const sp<AMessage> &notify);

    int32_t sessionID() const;
    int socket() const;
    sp<AMessage> getNotificationMessage() const;

    bool isRTSPServer() const;
    bool isTCPDatagramServer() const;

    bool wantsToRead();
    bool wantsToWrite();

    status_t readMore();
    status_t writeMore();

    status_t sendRequest(
            const void *data, ssize_t size, bool timeValid, int64_t timeUs);

    void setMode(Mode mode);

    status_t switchToWebSocketMode();

    ///Add by MTK @{
    bool isTCPServer() const;
    void setTCPConnectionType(int yesno);
    int  getTCPConnectionType() const;
    void closeSocket();
    status_t writeDirectRequest(const void *data, ssize_t size);
    /// @}
protected:
    virtual ~Session();

private:
    enum {
        FRAGMENT_FLAG_TIME_VALID = 1,
    };
    struct Fragment {
        uint32_t mFlags;
        int64_t mTimeUs;
        sp<ABuffer> mBuffer;
    };

    int32_t mSessionID;
    State mState;
    Mode mMode;
///Add by MTK @{
    int mTcpType;
///@}
    int mSocket;
    sp<AMessage> mNotify;
    bool mSawReceiveFailure, mSawSendFailure;
    int32_t mUDPRetries;

    List<Fragment> mOutFragments;

    AString mInBuffer;

    int64_t mLastStallReportUs;

    int     mThresholdCount;


    void notifyError(bool send, status_t err, const char *detail);
    void notify(NotificationReason reason);

    void dumpFragmentStats(const Fragment &frag);

    DISALLOW_EVIL_CONSTRUCTORS(Session);
};
////////////////////////////////////////////////////////////////////////////////

ANetworkSession::NetworkThread::NetworkThread(ANetworkSession *session)
    : mSession(session) {
}

ANetworkSession::NetworkThread::~NetworkThread() {
}

bool ANetworkSession::NetworkThread::threadLoop() {
    mSession->threadLoop();

    return true;
}

////////////////////////////////////////////////////////////////////////////////

ANetworkSession::Session::Session(
        int32_t sessionID,
        State state,
        int s,
        const sp<AMessage> &notify)
    : mSessionID(sessionID),
      mState(state),
      mMode(MODE_DATAGRAM),
///Add by MTK @{
      mTcpType(TCP_DATAGRAM),
///@}
      mSocket(s),
      mNotify(notify),
      mSawReceiveFailure(false),
      mSawSendFailure(false),
      mUDPRetries(kMaxUDPRetries),
      mLastStallReportUs(-1ll) {
    ALOGI("A new session:%d-%d", sessionID, state);
    if (mState == CONNECTED) {
        struct sockaddr_in localAddr;
        socklen_t localAddrLen = sizeof(localAddr);

        int res = getsockname(
                mSocket, (struct sockaddr *)&localAddr, &localAddrLen);
        CHECK_GE(res, 0);

        struct sockaddr_in remoteAddr;
        socklen_t remoteAddrLen = sizeof(remoteAddr);

        res = getpeername(
                mSocket, (struct sockaddr *)&remoteAddr, &remoteAddrLen);
        CHECK_GE(res, 0);

        in_addr_t addr = ntohl(localAddr.sin_addr.s_addr);
        AString localAddrString = StringPrintf(
                "%d.%d.%d.%d",
                (addr >> 24),
                (addr >> 16) & 0xff,
                (addr >> 8) & 0xff,
                addr & 0xff);

        addr = ntohl(remoteAddr.sin_addr.s_addr);
        AString remoteAddrString = StringPrintf(
                "%d.%d.%d.%d",
                (addr >> 24),
                (addr >> 16) & 0xff,
                (addr >> 8) & 0xff,
                addr & 0xff);

        sp<AMessage> msg = mNotify->dup();
        msg->setInt32("sessionID", mSessionID);
        msg->setInt32("reason", kWhatClientConnected);
        msg->setString("server-ip", localAddrString.c_str());
        msg->setInt32("server-port", ntohs(localAddr.sin_port));
        msg->setString("client-ip", remoteAddrString.c_str());
        msg->setInt32("client-port", ntohs(remoteAddr.sin_port));
        msg->post();
    }


    char val[PROPERTY_VALUE_MAX];
    if (property_get("media.wfd.threshold", val, NULL)) {        
        mThresholdCount = atoi(val);
    }else{
        mThresholdCount = THRESHOLD_BUFFER_SIZE;
    }

    ALOGI("mThresholdCount:%d", mThresholdCount);
}

ANetworkSession::Session::~Session() {
    ALOGI("Session %d gone", mSessionID);

    ///M: Modify to close socket @{
    if(mSocket != -1){
        close(mSocket);
        mSocket = -1;
    }
    /// @}    
}

int32_t ANetworkSession::Session::sessionID() const {
    return mSessionID;
}

int ANetworkSession::Session::socket() const {
    return mSocket;
}

void ANetworkSession::Session::setMode(Mode mode) {
    mMode = mode;
}

status_t ANetworkSession::Session::switchToWebSocketMode() {
    if (mState != CONNECTED || mMode != MODE_RTSP) {
        return INVALID_OPERATION;
    }

    mMode = MODE_WEBSOCKET;

    return OK;
}

sp<AMessage> ANetworkSession::Session::getNotificationMessage() const {
    return mNotify;
}

bool ANetworkSession::Session::isRTSPServer() const {
    return mState == LISTENING_RTSP;
}

bool ANetworkSession::Session::isTCPDatagramServer() const {
    return mState == LISTENING_TCP_DGRAMS;
}

bool ANetworkSession::Session::wantsToRead() {
    return !mSawReceiveFailure && mState != CONNECTING;
}

bool ANetworkSession::Session::wantsToWrite() {
    return !mSawSendFailure
        && (mState == CONNECTING
            || (mState == CONNECTED && !mOutFragments.empty())
            || (mState == DATAGRAM && !mOutFragments.empty()));
}

status_t ANetworkSession::Session::readMore() {
    if (mState == DATAGRAM) {
        CHECK_EQ(mMode, MODE_DATAGRAM);

        status_t err;
        do {
            sp<ABuffer> buf = new ABuffer(kMaxUDPSize);

            struct sockaddr_in remoteAddr;
            socklen_t remoteAddrLen = sizeof(remoteAddr);

            ssize_t n;
            do {
                n = recvfrom(
                        mSocket, buf->data(), buf->capacity(), 0,
                        (struct sockaddr *)&remoteAddr, &remoteAddrLen);
            } while (n < 0 && errno == EINTR);

            err = OK;
            if (n < 0) {
                err = -errno;
            } else if (n == 0) {
                err = -ECONNRESET;
            } else {
                buf->setRange(0, n);

                int64_t nowUs = ALooper::GetNowUs();
                buf->meta()->setInt64("arrivalTimeUs", nowUs);

                sp<AMessage> notify = mNotify->dup();
                notify->setInt32("sessionID", mSessionID);
                notify->setInt32("reason", kWhatDatagram);

                uint32_t ip = ntohl(remoteAddr.sin_addr.s_addr);
                notify->setString(
                        "fromAddr",
                        StringPrintf(
                            "%u.%u.%u.%u",
                            ip >> 24,
                            (ip >> 16) & 0xff,
                            (ip >> 8) & 0xff,
                            ip & 0xff).c_str());

                notify->setInt32("fromPort", ntohs(remoteAddr.sin_port));

                notify->setBuffer("data", buf);
                notify->post();
            }
        } while (err == OK);

        if (err == -EAGAIN) {
            err = OK;
        }

        if (err != OK) {
            if (!mUDPRetries) {
                notifyError(false /* send */, err, "Recvfrom failed.");
                mSawReceiveFailure = true;
            } else {
                mUDPRetries--;
                ALOGE("Recvfrom failed, %d/%d retries left",
                        mUDPRetries, kMaxUDPRetries);
                err = OK;
            }
        } else {
            mUDPRetries = kMaxUDPRetries;
        }

        return err;
    }

#ifdef WFD_HDCP_TX_SUPPORT
    char tmp[530]; // for AKE_Send_Cert len = 524 bytes + AKE_Receiver_info  = 6 bytes
#else
    char tmp[512];
#endif    
    ssize_t n;
    do {
        n = recv(mSocket, tmp, sizeof(tmp), 0);
    } while (n < 0 && errno == EINTR);

    status_t err = OK;

    if (n > 0) {
        mInBuffer.append(tmp, n);

#ifdef WFD_HDCP_TX_SUPPORT
    if (mTcpType == Session::TCP_BINDATA)
    {
        char v[PROPERTY_VALUE_MAX];
        if (property_get("media.stagefright_wfd.hdcp.dump", v, NULL) 
                 && (!strcmp(v, "1") ))
        {                 
            ALOGD("in:");
            hexdump(tmp, n);
        }            
    }        
#endif
    } else if (n < 0) {
        err = -errno;
    } else {
        err = -ECONNRESET;
    }

    if (mMode == MODE_DATAGRAM) {
        // TCP stream carrying 16-bit length-prefixed datagrams.
///Add by MTK @{
        if(mTcpType == Session::TCP_DATAGRAM){
///@}
        while (mInBuffer.size() >= 2) {
            size_t packetSize = U16_AT((const uint8_t *)mInBuffer.c_str());

            if (mInBuffer.size() < packetSize + 2) {
                break;
            }

            sp<ABuffer> packet = new ABuffer(packetSize);
            memcpy(packet->data(), mInBuffer.c_str() + 2, packetSize);

            int64_t nowUs = ALooper::GetNowUs();
            packet->meta()->setInt64("arrivalTimeUs", nowUs);

            sp<AMessage> notify = mNotify->dup();
            notify->setInt32("sessionID", mSessionID);
            notify->setInt32("reason", kWhatDatagram);
            notify->setBuffer("data", packet);
            notify->post();

            mInBuffer.erase(0, packetSize + 2);
            }
        }
#ifdef WFD_HDCP_TX_SUPPORT
        else if(mTcpType == Session::TCP_BINDATA)
        {
            sp<ABuffer> packet = new ABuffer(mInBuffer.size());
            memcpy(packet->data(), mInBuffer.c_str(), mInBuffer.size());
        
            sp<AMessage> notify = mNotify->dup();
            notify->setInt32("sessionID", mSessionID);
            notify->setInt32("reason", kWhatBinaryData);
            notify->setBuffer("data", packet);
            notify->post();
            mInBuffer.clear();
        }
#endif
        else if(mTcpType == Session::TCP_TEXTDATA){
            sp<AMessage> notify = mNotify->dup();

            notify->setInt32("sessionID", mSessionID);
            notify->setInt32("reason", kWhatTextData);
            notify->setString("data", mInBuffer.c_str());
            notify->post();

            mInBuffer.clear();
///Add by MTK @{
        }else if(mTcpType == Session::TCP_UIBC){
            
            ALOGD("Get A TCP_UIBC --------- Total buffer size=%d",mInBuffer.size());
            while (mInBuffer.size() >= 4) {
                const char* pBuffer = mInBuffer.c_str();
                ALOGD("Buffer:0x%02x:0x%02x:0x%02x:0x%02x", pBuffer[0], pBuffer[1], pBuffer[2], pBuffer[3]);

                size_t packetSize = (pBuffer[2]<<16) + pBuffer[3];
                ALOGD("Read packet size:%d", packetSize);

                if(packetSize<=0 ){
                    ALOGD("packet size error :%d", packetSize);  
                    mInBuffer.clear();
                    break;
                }
                if (mInBuffer.size() < packetSize) {
                    break;
                }else{

                    sp<ABuffer> packet = new ABuffer(packetSize);
                    memcpy(packet->data(), mInBuffer.c_str(), packetSize);

                    sp<AMessage> notify = mNotify->dup();
                    notify->setInt32("sessionID", mSessionID);
                    notify->setInt32("reason", kWhatUibcData);
                    notify->setBuffer("data", packet);
                    notify->post();

                    mInBuffer.erase(0, packetSize);
                    ALOGD("buffer size:%d", mInBuffer.size());
                }
            }
            ALOGD("Get A TCP_UIBC END---------");
        }
///@}
    } else if (mMode == MODE_RTSP) {
        for (;;) {
            size_t length;

            if (mInBuffer.size() > 0 && mInBuffer.c_str()[0] == '$') {
                if (mInBuffer.size() < 4) {
                    break;
                }

                length = U16_AT((const uint8_t *)mInBuffer.c_str() + 2);

                if (mInBuffer.size() < 4 + length) {
                    break;
                }

                sp<AMessage> notify = mNotify->dup();
                notify->setInt32("sessionID", mSessionID);
                notify->setInt32("reason", kWhatBinaryData);
                notify->setInt32("channel", mInBuffer.c_str()[1]);

                sp<ABuffer> data = new ABuffer(length);
                memcpy(data->data(), mInBuffer.c_str() + 4, length);

                int64_t nowUs = ALooper::GetNowUs();
                data->meta()->setInt64("arrivalTimeUs", nowUs);

                notify->setBuffer("data", data);
                notify->post();

                mInBuffer.erase(0, 4 + length);
                continue;
            }

            sp<ParsedMessage> msg =
                ParsedMessage::Parse(
                        mInBuffer.c_str(), mInBuffer.size(), err != OK, &length);

            if (msg == NULL) {
                break;
            }

            sp<AMessage> notify = mNotify->dup();
            notify->setInt32("sessionID", mSessionID);
            notify->setInt32("reason", kWhatData);
            notify->setObject("data", msg);
            notify->post();

#if 1
            // XXX The (old) dongle sends the wrong content length header on a
            // SET_PARAMETER request that signals a "wfd_idr_request".
            // (17 instead of 19).
            const char *content = msg->getContent();
            if (content
                    && !memcmp(content, "wfd_idr_request\r\n", 17)
                    && length >= 19
                    && mInBuffer.c_str()[length] == '\r'
                    && mInBuffer.c_str()[length + 1] == '\n') {
                length += 2;
            }
#endif

            mInBuffer.erase(0, length);

            if (err != OK) {
                break;
            }
        }
    } else {
        CHECK_EQ(mMode, MODE_WEBSOCKET);

        const uint8_t *data = (const uint8_t *)mInBuffer.c_str();
        // hexdump(data, mInBuffer.size());

        while (mInBuffer.size() >= 2) {
            size_t offset = 2;

            unsigned payloadLen = data[1] & 0x7f;
            if (payloadLen == 126) {
                if (offset + 2 > mInBuffer.size()) {
                    break;
                }

                payloadLen = U16_AT(&data[offset]);
                offset += 2;
            } else if (payloadLen == 127) {
                if (offset + 8 > mInBuffer.size()) {
                    break;
                }

                payloadLen = U64_AT(&data[offset]);
                offset += 8;
            }

            uint32_t mask = 0;
            if (data[1] & 0x80) {
                // MASK==1
                if (offset + 4 > mInBuffer.size()) {
                    break;
                }

                mask = U32_AT(&data[offset]);
                offset += 4;
            }

            if (offset + payloadLen > mInBuffer.size()) {
                break;
            }

            // We have the full message.

            sp<ABuffer> packet = new ABuffer(payloadLen);
            memcpy(packet->data(), &data[offset], payloadLen);

            if (mask != 0) {
                for (size_t i = 0; i < payloadLen; ++i) {
                    packet->data()[i] =
                        data[offset + i]
                            ^ ((mask >> (8 * (3 - (i % 4)))) & 0xff);
                }
            }

            sp<AMessage> notify = mNotify->dup();
            notify->setInt32("sessionID", mSessionID);
            notify->setInt32("reason", kWhatWebSocketMessage);
            notify->setBuffer("data", packet);
            notify->setInt32("headerByte", data[0]);
            notify->post();

            mInBuffer.erase(0, offset + payloadLen);
        }
    }

    if (err != OK) {
        notifyError(false /* send */, err, "Recv failed.");
        mSawReceiveFailure = true;
    }

    return err;
}

void ANetworkSession::Session::dumpFragmentStats(const Fragment &frag) {
#if 0
    int64_t nowUs = ALooper::GetNowUs();
    int64_t delayMs = (nowUs - frag.mTimeUs) / 1000ll;

    static const int64_t kMinDelayMs = 0;
    static const int64_t kMaxDelayMs = 300;

    const char *kPattern = "########################################";
    size_t kPatternSize = strlen(kPattern);

    int n = (kPatternSize * (delayMs - kMinDelayMs))
                / (kMaxDelayMs - kMinDelayMs);

    if (n < 0) {
        n = 0;
    } else if ((size_t)n > kPatternSize) {
        n = kPatternSize;
    }

    ALOGI("[%lld]: (%4lld ms) %s\n",
          frag.mTimeUs / 1000,
          delayMs,
          kPattern + kPatternSize - n);
#endif
}

status_t ANetworkSession::Session::writeMore() {
    if (mState == DATAGRAM) {
        CHECK(!mOutFragments.empty());

        status_t err;
        do {
            const Fragment &frag = *mOutFragments.begin();
            const sp<ABuffer> &datagram = frag.mBuffer;

            int n;

#ifndef ANDROID_DEFAULT_CODE
            int64_t startTimeUs = ALooper::GetNowUs();
		
            do {
                n = send(mSocket, datagram->data(), datagram->size(), 0);
            } while (n < 0 && errno == EINTR);

	   int64_t endTimeUs = ALooper::GetNowUs();

	   if(endTimeUs - startTimeUs > 10000ll){
	   	ALOGI("[nettime]Send a datagram more than %lld ms,left %d frags",(endTimeUs - startTimeUs)/1000,mOutFragments.size());
	   }

#endif
            err = OK;

            if (n > 0) {
                if (frag.mFlags & FRAGMENT_FLAG_TIME_VALID) {
                    dumpFragmentStats(frag);
                }

                mOutFragments.erase(mOutFragments.begin());
            } else if (n < 0) {
                err = -errno;
            } else if (n == 0) {
                err = -ECONNRESET;
            }
        } while (err == OK && !mOutFragments.empty());

        if (err == -EAGAIN) {
            if (!mOutFragments.empty()) {
                ALOGI("%d datagrams remain queued.", mOutFragments.size());
            }
            err = OK;
        }

        if (err != OK) {
            if (!mUDPRetries) {
                notifyError(true /* send */, err, "Send datagram failed.");
                mSawSendFailure = true;
            } else {
                mUDPRetries--;
                ALOGE("Send datagram failed, %d/%d retries left",
                        mUDPRetries, kMaxUDPRetries);
                err = OK;
            }
        } else {
            mUDPRetries = kMaxUDPRetries;
        }

        return err;
    }

    if (mState == CONNECTING) {
        int err;
        socklen_t optionLen = sizeof(err);
        CHECK_EQ(getsockopt(mSocket, SOL_SOCKET, SO_ERROR, &err, &optionLen), 0);
        CHECK_EQ(optionLen, (socklen_t)sizeof(err));

        if (err != 0) {
            notifyError(kWhatError, -err, "Connection failed");
            mSawSendFailure = true;

            return -err;
        }

        mState = CONNECTED;
        notify(kWhatConnected);

        return OK;
    }

    CHECK_EQ(mState, CONNECTED);
    CHECK(!mOutFragments.empty());

    ssize_t n;
    while (!mOutFragments.empty()) {
        const Fragment &frag = *mOutFragments.begin();


#ifndef ANDROID_DEFAULT_CODE
            int64_t startTimeUs = ALooper::GetNowUs();

        do {
            n = send(mSocket, frag.mBuffer->data(), frag.mBuffer->size(), 0);
        } while (n < 0 && errno == EINTR);

         int64_t endTimeUs = ALooper::GetNowUs();
	   if(endTimeUs - startTimeUs > 10000ll){
	   	ALOGI("[nettimeus]Send %d more than %lld ms",n,(endTimeUs - startTimeUs)/1000);
	   }

#endif		

        if (n <= 0) {
            break;
        }

        frag.mBuffer->setRange(
                frag.mBuffer->offset() + n, frag.mBuffer->size() - n);

        if (frag.mBuffer->size() > 0) {
            break;
        }

        if (frag.mFlags & FRAGMENT_FLAG_TIME_VALID) {
            dumpFragmentStats(frag);
        }

        mOutFragments.erase(mOutFragments.begin());
    }

    status_t err = OK;

    if (n < 0) {
        err = -errno;
    } else if (n == 0) {
        err = -ECONNRESET;
    }

    if (err != OK) {
        notifyError(true /* send */, err, "Send failed.");
        mSawSendFailure = true;
    }

#if 0
    int numBytesQueued;
    int res = ioctl(mSocket, SIOCOUTQ, &numBytesQueued);
    if (res == 0 && numBytesQueued > 50 * 1024) {
        if (numBytesQueued > 409600) {
            ALOGW("!!! numBytesQueued = %d", numBytesQueued);
        }

        int64_t nowUs = ALooper::GetNowUs();

        if (mLastStallReportUs < 0ll
                || nowUs > mLastStallReportUs + 100000ll) {
            sp<AMessage> msg = mNotify->dup();
            msg->setInt32("sessionID", mSessionID);
            msg->setInt32("reason", kWhatNetworkStall);
            msg->setSize("numBytesQueued", numBytesQueued);
            msg->post();

            mLastStallReportUs = nowUs;
        }
    }
#endif

    return err;
}

status_t ANetworkSession::Session::sendRequest(
        const void *data, ssize_t size, bool timeValid, int64_t timeUs) {
    CHECK(mState == CONNECTED || mState == DATAGRAM);

    if (size < 0) {
        size = strlen((const char *)data);
    }

    if (size == 0) {
        return OK;
    }

    sp<ABuffer> buffer;
    if (mState == CONNECTED && mMode == MODE_DATAGRAM) {
///Add by MTK @{
        if(mTcpType == TCP_DATAGRAM){
///@}
           CHECK_LE(size, 65535);

            buffer = new ABuffer(size + 2);
            buffer->data()[0] = size >> 8;
            buffer->data()[1] = size & 0xff;
            memcpy(buffer->data() + 2, data, size);
        }
    } else if (mState == CONNECTED && mMode == MODE_WEBSOCKET) {
        static const bool kUseMask = false;  // Chromium doesn't like it.

        size_t numHeaderBytes = 2 + (kUseMask ? 4 : 0);
        if (size > 65535) {
            numHeaderBytes += 8;
        } else if (size > 125) {
            numHeaderBytes += 2;
        }

        buffer = new ABuffer(numHeaderBytes + size);
        buffer->data()[0] = 0x81;  // FIN==1 | opcode=1 (text)
        buffer->data()[1] = kUseMask ? 0x80 : 0x00;

        if (size > 65535) {
            buffer->data()[1] |= 127;
            buffer->data()[2] = 0x00;
            buffer->data()[3] = 0x00;
            buffer->data()[4] = 0x00;
            buffer->data()[5] = 0x00;
            buffer->data()[6] = (size >> 24) & 0xff;
            buffer->data()[7] = (size >> 16) & 0xff;
            buffer->data()[8] = (size >> 8) & 0xff;
            buffer->data()[9] = size & 0xff;
        } else if (size > 125) {
            buffer->data()[1] |= 126;
            buffer->data()[2] = (size >> 8) & 0xff;
            buffer->data()[3] = size & 0xff;
        } else {
            buffer->data()[1] |= size;
        }

        if (kUseMask) {
            uint32_t mask = rand();

            buffer->data()[numHeaderBytes - 4] = (mask >> 24) & 0xff;
            buffer->data()[numHeaderBytes - 3] = (mask >> 16) & 0xff;
            buffer->data()[numHeaderBytes - 2] = (mask >> 8) & 0xff;
            buffer->data()[numHeaderBytes - 1] = mask & 0xff;

            for (size_t i = 0; i < (size_t)size; ++i) {
                buffer->data()[numHeaderBytes + i] =
                    ((const uint8_t *)data)[i]
                        ^ ((mask >> (8 * (3 - (i % 4)))) & 0xff);
            }
        } else {
            memcpy(buffer->data() + numHeaderBytes, data, size);
        }
    } else {
        buffer = new ABuffer(size);
        memcpy(buffer->data(), data, size);
    }

    Fragment frag;

    frag.mFlags = 0;
    if (timeValid) {
        frag.mFlags = FRAGMENT_FLAG_TIME_VALID;
        frag.mTimeUs = timeUs;
    }

    frag.mBuffer = buffer;

    mOutFragments.push_back(frag);

    return OK;
}

status_t ANetworkSession::Session::writeDirectRequest(const void *data_in, ssize_t size) {
    CHECK(mState == CONNECTED || mState == DATAGRAM || mState == SOCKET_ERROR);
    
    if (mState == DATAGRAM || mState == CONNECTED) {
        CHECK_GE(size, 0);
        
        ssize_t n;
        status_t err = OK;
        int    retry = 0;

        uint8_t *data = (uint8_t*) data_in;
        /*
        if (data[0] == 0x80 && (data[1] & 0x7f) == 33) {
            int64_t nowUs = ALooper::GetNowUs();

            uint32_t prevRtpTime = U32_AT(&data[4]);

            // 90kHz time scale
            uint32_t rtpTime = (nowUs * 9ll) / 100ll;

            //ALOGV("correcting rtpTime by %.0f ms", diffTime / 90.0);

            data[4] = rtpTime >> 24;
            data[5] = (rtpTime >> 16) & 0xff;
            data[6] = (rtpTime >> 8) & 0xff;
            data[7] = rtpTime & 0xff;
        }
        */

        int numBytesQueued;
        int res = ioctl(mSocket, SIOCOUTQ, &numBytesQueued);

        
        if (res == 0 && numBytesQueued > mThresholdCount * 1024) {
                if (numBytesQueued > WARN_BUFFER_SIZE * 1024) {
                    ALOGW("!!! numBytesQueued = %d", numBytesQueued);
                }
        
                int64_t nowUs = ALooper::GetNowUs();
        
                if (mLastStallReportUs < 0ll
                        || nowUs > mLastStallReportUs + 100000ll) {
                    sp<AMessage> msg = mNotify->dup();
                    msg->setInt32("sessionID", mSessionID);
                    msg->setInt32("reason", kWhatNetworkStall);
                    msg->setSize("numBytesQueued", numBytesQueued);
                    msg->post();
        
                    mLastStallReportUs = nowUs;
                }
        }
        
        do {            
            n = send(mSocket, data, size, 0);
            retry++;
            if((retry % 10) == 0){
                ALOGI("retry:%d", retry);
                if(retry > 10 * 1000){
                    ALOGE("Fail to send");
                    break;
                }
                usleep(10 * 1000);
            }
        } while (n < 0 && (errno == EINTR || errno == EAGAIN || errno == ECONNREFUSED));
                
        if (n < 0) {
            err = -errno;
        } else if (n == 0) {
            err = -ECONNRESET;
        }

        if (err != OK) {
            if(mState == DATAGRAM){
                notifyError(true /* send */, err, "Send datagram failed.");
            }else if(mState == CONNECTED){
                notifyError(true /* send */, err, "Send failed.");
            }
            mState = SOCKET_ERROR;
            mSawSendFailure = true;
        }                

        return err;
    }    

    return OK;
}
void ANetworkSession::Session::notifyError(
        bool send, status_t err, const char *detail) {
    sp<AMessage> msg = mNotify->dup();
    msg->setInt32("sessionID", mSessionID);
    msg->setInt32("reason", kWhatError);
    msg->setInt32("send", send);
    msg->setInt32("err", err);
    msg->setString("detail", detail);
    msg->post();
}

void ANetworkSession::Session::notify(NotificationReason reason) {
    sp<AMessage> msg = mNotify->dup();
    msg->setInt32("sessionID", mSessionID);
    msg->setInt32("reason", reason);
    msg->post();
}

////////////////////////////////////////////////////////////////////////////////

ANetworkSession::ANetworkSession()
    : mNextSessionID(1) {
    mPipeFd[0] = mPipeFd[1] = -1;
}

ANetworkSession::~ANetworkSession() {
    stop();
}

status_t ANetworkSession::start() {
    if (mThread != NULL) {
        return INVALID_OPERATION;
    }

    int res = pipe(mPipeFd);
    if (res != 0) {
        mPipeFd[0] = mPipeFd[1] = -1;
        return -errno;
    }

    mThread = new NetworkThread(this);

    status_t err = mThread->run("ANetworkSession", ANDROID_PRIORITY_AUDIO);

    if (err != OK) {
        mThread.clear();

        close(mPipeFd[0]);
        close(mPipeFd[1]);
        mPipeFd[0] = mPipeFd[1] = -1;

        return err;
    }

    return OK;
}

status_t ANetworkSession::stop() {
    if (mThread == NULL) {
        return INVALID_OPERATION;
    }

    mThread->requestExit();
    interrupt();
    mThread->requestExitAndWait();

    mThread.clear();

    close(mPipeFd[0]);
    close(mPipeFd[1]);
    mPipeFd[0] = mPipeFd[1] = -1;

    return OK;
}

status_t ANetworkSession::createRTSPClient(
        const char *host, unsigned port, const sp<AMessage> &notify,
        int32_t *sessionID) {
    return createClientOrServer(
            kModeCreateRTSPClient,
            NULL /* addr */,
            0 /* port */,
            host,
            port,
            notify,
            sessionID);
}

status_t ANetworkSession::createRTSPServer(
        const struct in_addr &addr, unsigned port,
        const sp<AMessage> &notify, int32_t *sessionID) {
    return createClientOrServer(
            kModeCreateRTSPServer,
            &addr,
            port,
            NULL /* remoteHost */,
            0 /* remotePort */,
            notify,
            sessionID);
}

status_t ANetworkSession::createUDPSession(
        unsigned localPort, const sp<AMessage> &notify, int32_t *sessionID) {
    return createUDPSession(localPort, NULL, 0, notify, sessionID);
}

status_t ANetworkSession::createUDPSession(
        unsigned localPort,
        const char *remoteHost,
        unsigned remotePort,
        const sp<AMessage> &notify,
        int32_t *sessionID) {
    return createClientOrServer(
            kModeCreateUDPSession,
            NULL /* addr */,
            localPort,
            remoteHost,
            remotePort,
            notify,
            sessionID);
}

#ifdef WFD_HDCP_TX_SUPPORT
status_t ANetworkSession::createTCPBinaryDataSessionActive(
        unsigned localPort,
        const char *remoteHost,
        unsigned remotePort,
        const sp<AMessage> &notify,
        int32_t *sessionID) {

   ALOGI("%s", __FUNCTION__);
        
   return createClientOrServer(
           kModeCreateTCPBinaryDataSessionActive,
           NULL /* addr */,
           localPort,
           remoteHost,
           remotePort,
           notify,
           sessionID);
}
#endif

status_t ANetworkSession::createTCPDatagramSession(
        const struct in_addr &addr, unsigned port,
        const sp<AMessage> &notify, int32_t *sessionID) {
    return createClientOrServer(
            kModeCreateTCPDatagramSessionPassive,
            &addr,
            port,
            NULL /* remoteHost */,
            0 /* remotePort */,
            notify,
            sessionID);
}

status_t ANetworkSession::createTCPDatagramSession(
        unsigned localPort,
        const char *remoteHost,
        unsigned remotePort,
        const sp<AMessage> &notify,
        int32_t *sessionID) {
    return createClientOrServer(
            kModeCreateTCPDatagramSessionActive,
            NULL /* addr */,
            localPort,
            remoteHost,
            remotePort,
            notify,
            sessionID);
}

status_t ANetworkSession::destroySession(int32_t sessionID) {
    Mutex::Autolock autoLock(mLock);

    ssize_t index = mSessions.indexOfKey(sessionID);

    if (index < 0) {
        return -ENOENT;
    }

    ///M: Close the socekt immediately @{
    const sp<Session> session = mSessions.valueAt(index);
    session->closeSocket();
    /// @}
    mSessions.removeItemsAt(index);

    interrupt();

    return OK;
}

// static
status_t ANetworkSession::MakeSocketNonBlocking(int s) {
    int flags = fcntl(s, F_GETFL, 0);
    if (flags < 0) {
        flags = 0;
    }

    int res = fcntl(s, F_SETFL, flags | O_NONBLOCK);
    if (res < 0) {
        return -errno;
    }

    return OK;
}

status_t ANetworkSession::createClientOrServer(
        Mode mode,
        const struct in_addr *localAddr,
        unsigned port,
        const char *remoteHost,
        unsigned remotePort,
        const sp<AMessage> &notify,
        int32_t *sessionID) {
    Mutex::Autolock autoLock(mLock);

    *sessionID = 0;
    status_t err = OK;
    int s, res;
    sp<Session> session;

    ALOGI("createClientOrServer: mode:%d", mode);
    s = socket(
            AF_INET,
            (mode == kModeCreateUDPSession) ? SOCK_DGRAM : SOCK_STREAM,
            0);

    if (s < 0) {
        err = -errno;
        ALOGE("Error in createClientOrServer:%d", err);
        goto bail;
    }

    if (mode == kModeCreateRTSPServer
///Add by MTK @{
            || mode == kModeCreateTCPDatagramSessionPassive
            || mode == kModeCreateTCPTextDataSessionPassive
            || mode == kModeCreateUIBCServer
            || mode == kModeCreateUDPSession) {
///@}
        const int yes = 1;
        ALOGD("Set socket resue:%d", yes);
        res = setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));

        if (res < 0) {
            err = -errno;
            goto bail2;
        }
///Add by MTK @{
        struct linger so_linger;        
        so_linger.l_onoff = true;
        so_linger.l_linger = 0;
        ALOGD("Set socket linger:%d", so_linger.l_onoff);
        res = setsockopt(s, SOL_SOCKET, SO_LINGER, &so_linger, sizeof(so_linger));
        
        int flag = 1;
        res = setsockopt(s, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));
                
///@}
    }

    if (mode == kModeCreateUDPSession) {
#ifndef ANDROID_DEFAULT_CODE
        int size = MAX_BUFFER_SIZE * 1024;
#else
        int size = 256 * 1024;
#endif
        res = setsockopt(s, SOL_SOCKET, SO_RCVBUF, &size, sizeof(size));

        if (res < 0) {
            err = -errno;
            goto bail2;
        }

        res = setsockopt(s, SOL_SOCKET, SO_SNDBUF, &size, sizeof(size));

        if (res < 0) {
            err = -errno;
            goto bail2;
        }

        //Configure QoS priority for UDP/RTP packets
        int opt;
        int priority;
        priority = 5; /* 5: VI 7: VO */
        opt = priority << 5;
        
        res = setsockopt(s, SOL_IP, IP_TOS, &opt, sizeof(opt));
        if (res < 0) {
            err = -errno;
            ALOGD("Socket IP_TOS option:%d", err);
        }
        
        opt = priority;
        res = setsockopt(s, SOL_SOCKET, SO_PRIORITY, &opt, sizeof(opt));
        if (res < 0) {
            err = -errno;
            ALOGD("Socket SO_PRIORITY option:%d", err);
        }        
    } else if (mode == kModeCreateTCPDatagramSessionActive) {
        int flag = 1;
        res = setsockopt(s, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));

        if (res < 0) {
            err = -errno;
            goto bail2;
        }

        int tos = 224;  // VOICE
        res = setsockopt(s, IPPROTO_IP, IP_TOS, &tos, sizeof(tos));

        if (res < 0) {
            err = -errno;
            goto bail2;
        }
    }

    err = MakeSocketNonBlocking(s);

    if (err != OK) {
        goto bail2;
    }

    struct sockaddr_in addr;
    memset(addr.sin_zero, 0, sizeof(addr.sin_zero));
    addr.sin_family = AF_INET;

    if (mode == kModeCreateRTSPClient
     || mode == kModeCreateTCPDatagramSessionActive
     || mode == kModeCreateUIBCClient
#ifdef WFD_HDCP_TX_SUPPORT
     || mode == kModeCreateTCPBinaryDataSessionActive
#endif
     )
     {
        struct hostent *ent= gethostbyname(remoteHost);
        if (ent == NULL) {
            err = -h_errno;
            goto bail2;
        }

        addr.sin_addr.s_addr = *(in_addr_t *)ent->h_addr;
        addr.sin_port = htons(remotePort);
///Add by MTK @{
    } else if (localAddr != NULL && (mode != kModeCreateRTSPServer && 
                                     mode != kModeCreateTCPTextDataSessionPassive && 
                                     mode != kModeCreateUIBCServer)) {
///@}
        addr.sin_addr = *localAddr;
        addr.sin_port = htons(port);
        ALOGI("Host info %s:%d\n", inet_ntoa(addr.sin_addr), ntohs(addr.sin_port));
    } else {
        addr.sin_addr.s_addr = htonl(INADDR_ANY);
        addr.sin_port = htons(port);
    }

    if (mode == kModeCreateRTSPClient
     || mode == kModeCreateTCPDatagramSessionActive
     || mode == kModeCreateUIBCClient
#ifdef WFD_HDCP_TX_SUPPORT
     || mode == kModeCreateTCPBinaryDataSessionActive
#endif
     ) 
     {
        in_addr_t x = ntohl(addr.sin_addr.s_addr);
        ALOGI("connecting socket %d to %d.%d.%d.%d:%d",
              s,
              (x >> 24),
              (x >> 16) & 0xff,
              (x >> 8) & 0xff,
              x & 0xff,
              ntohs(addr.sin_port));

        res = connect(s, (const struct sockaddr *)&addr, sizeof(addr));

        CHECK_LT(res, 0);
        if (errno == EINPROGRESS) {
            res = 0;
        }
    } else {
        res = bind(s, (const struct sockaddr *)&addr, sizeof(addr));
        ALOGI("Bind is Done");

        if (res == 0) {
///Add by MTK @{
            if (mode == kModeCreateRTSPServer
                    || mode == kModeCreateTCPDatagramSessionPassive
                    || mode == kModeCreateTCPTextDataSessionPassive
                    || mode == kModeCreateUIBCServer) {
                ALOGI("socket listen");
///@}
                res = listen(s, 4);
            } else {
                CHECK_EQ(mode, kModeCreateUDPSession);

                if (remoteHost != NULL) {
                    struct sockaddr_in remoteAddr;
                    memset(remoteAddr.sin_zero, 0, sizeof(remoteAddr.sin_zero));
                    remoteAddr.sin_family = AF_INET;
                    remoteAddr.sin_port = htons(remotePort);

                    struct hostent *ent= gethostbyname(remoteHost);
                    if (ent == NULL) {
                        err = -h_errno;
                        goto bail2;
                    }

                    remoteAddr.sin_addr.s_addr = *(in_addr_t *)ent->h_addr;

                    res = connect(
                            s,
                            (const struct sockaddr *)&remoteAddr,
                            sizeof(remoteAddr));
                }
            }
        }
    }

    if (res < 0) {
        err = -errno;
        goto bail2;
    }

    Session::State state;
    switch (mode) {
        case kModeCreateRTSPClient:
        case kModeCreateUIBCClient:
            state = Session::CONNECTING;
            break;

        case kModeCreateTCPDatagramSessionActive:
#ifdef WFD_HDCP_TX_SUPPORT
        case kModeCreateTCPBinaryDataSessionActive:
#endif
            state = Session::CONNECTING;
            break;

        case kModeCreateTCPDatagramSessionPassive:
            state = Session::LISTENING_TCP_DGRAMS;
            break;

        case kModeCreateRTSPServer:
            state = Session::LISTENING_RTSP;
            break;

        ///Add by MTK @{
        case kModeCreateTCPTextDataSessionPassive:
            state = Session::LISTENING_TCP_TEXT;
            break;

        case kModeCreateUIBCServer:
            state = Session::LISTENING_TCP_UIBC;
            break;
        ///@}
        default:
            CHECK_EQ(mode, kModeCreateUDPSession);
            state = Session::DATAGRAM;
            break;
    }

    session = new Session(
            mNextSessionID++,
            state,
            s,
            notify);

    if (mode == kModeCreateTCPDatagramSessionActive) {
        session->setMode(Session::MODE_DATAGRAM);
    } else if (mode == kModeCreateRTSPClient) {
        session->setMode(Session::MODE_RTSP);
///Add by MTK @{
    } else if (mode == kModeCreateTCPTextDataSessionPassive) {
        session->setTCPConnectionType(Session::TCP_TEXTDATA);
    } else if (mode == kModeCreateUIBCServer) {
        session->setTCPConnectionType(Session::TCP_UIBC);
    }
///@}
#ifdef WFD_HDCP_TX_SUPPORT
    else if (mode == kModeCreateTCPBinaryDataSessionActive) {
        session->setTCPConnectionType(Session::TCP_BINDATA);
        session->setMode(Session::MODE_DATAGRAM);
    }
#endif

    mSessions.add(session->sessionID(), session);

    interrupt();

    *sessionID = session->sessionID();

    goto bail;

bail2:
    ALOGE("Error in createClientOrServer:%d", err);
    close(s);
    s = -1;

bail:
    return err;
}

status_t ANetworkSession::connectUDPSession(
        int32_t sessionID, const char *remoteHost, unsigned remotePort) {
    Mutex::Autolock autoLock(mLock);

    ssize_t index = mSessions.indexOfKey(sessionID);

    if (index < 0) {
        return -ENOENT;
    }

    const sp<Session> session = mSessions.valueAt(index);
    int s = session->socket();

    struct sockaddr_in remoteAddr;
    memset(remoteAddr.sin_zero, 0, sizeof(remoteAddr.sin_zero));
    remoteAddr.sin_family = AF_INET;
    remoteAddr.sin_port = htons(remotePort);

    status_t err = OK;
    struct hostent *ent = gethostbyname(remoteHost);
    if (ent == NULL) {
        err = -h_errno;
    } else {
        remoteAddr.sin_addr.s_addr = *(in_addr_t *)ent->h_addr;

        int res = connect(
                s,
                (const struct sockaddr *)&remoteAddr,
                sizeof(remoteAddr));

        if (res < 0) {
            err = -errno;
        }
    }

    return err;
}

status_t ANetworkSession::sendRequest(
        int32_t sessionID, const void *data, ssize_t size,
        bool timeValid, int64_t timeUs) {
    Mutex::Autolock autoLock(mLock);

    ssize_t index = mSessions.indexOfKey(sessionID);

    if (index < 0) {
        return -ENOENT;
    }

    const sp<Session> session = mSessions.valueAt(index);

    status_t err = session->sendRequest(data, size, timeValid, timeUs);

    interrupt();

    return err;
}

status_t ANetworkSession::switchToWebSocketMode(int32_t sessionID) {
    Mutex::Autolock autoLock(mLock);

    ssize_t index = mSessions.indexOfKey(sessionID);

    if (index < 0) {
        return -ENOENT;
    }

    const sp<Session> session = mSessions.valueAt(index);
    return session->switchToWebSocketMode();
}

void ANetworkSession::interrupt() {
    static const char dummy = 0;

    ssize_t n;
    do {
        n = write(mPipeFd[1], &dummy, 1);
    } while (n < 0 && errno == EINTR);

    if (n < 0) {
        ALOGW("Error writing to pipe (%s)", strerror(errno));
    }
}

void ANetworkSession::threadLoop() {
    fd_set rs, ws;
    FD_ZERO(&rs);
    FD_ZERO(&ws);

    FD_SET(mPipeFd[0], &rs);
    int maxFd = mPipeFd[0];

    {
        Mutex::Autolock autoLock(mLock);

        for (size_t i = 0; i < mSessions.size(); ++i) {
            const sp<Session> &session = mSessions.valueAt(i);

            int s = session->socket();

            if (s < 0) {
                continue;
            }

            if (session->wantsToRead()) {
                FD_SET(s, &rs);
                if (s > maxFd) {
                    maxFd = s;
                }
            }

            if (session->wantsToWrite()) {
                FD_SET(s, &ws);
                if (s > maxFd) {
                    maxFd = s;
                }
            }
        }
    }

    int res = select(maxFd + 1, &rs, &ws, NULL, NULL /* tv */);

    if (res == 0) {
        return;
    }

    if (res < 0) {
        if (errno == EINTR) {
            return;
        }

        ALOGE("select failed w/ error %d (%s)", errno, strerror(errno));
        return;
    }

    if (FD_ISSET(mPipeFd[0], &rs)) {
        char c;
        ssize_t n;
        do {
            n = read(mPipeFd[0], &c, 1);
        } while (n < 0 && errno == EINTR);

        if (n < 0) {
            ALOGW("Error reading from pipe (%s)", strerror(errno));
        }

        --res;
    }

    {
        Mutex::Autolock autoLock(mLock);

        List<sp<Session> > sessionsToAdd;

        for (size_t i = mSessions.size(); res > 0 && i-- > 0;) {
            const sp<Session> &session = mSessions.valueAt(i);

            int s = session->socket();

            if (s < 0) {
                continue;
            }

            if (FD_ISSET(s, &rs) || FD_ISSET(s, &ws)) {
                --res;
            }

            if (FD_ISSET(s, &rs)) {
///Add by MTK @{
                if (session->isRTSPServer() || session->isTCPDatagramServer() || session->isTCPServer()) {
///@}
                    struct sockaddr_in remoteAddr;
                    socklen_t remoteAddrLen = sizeof(remoteAddr);

                    int clientSocket = accept(
                            s, (struct sockaddr *)&remoteAddr, &remoteAddrLen);

                    if (clientSocket >= 0) {
                        status_t err = MakeSocketNonBlocking(clientSocket);

                        if (err != OK) {
                            ALOGE("Unable to make client socket non blocking, "
                                  "failed w/ error %d (%s)",
                                  err, strerror(-err));

                            close(clientSocket);
                            clientSocket = -1;
                        } else {
                            in_addr_t addr = ntohl(remoteAddr.sin_addr.s_addr);

                            ALOGI("incoming connection from %d.%d.%d.%d:%d "
                                  "(socket %d)",
                                  (addr >> 24),
                                  (addr >> 16) & 0xff,
                                  (addr >> 8) & 0xff,
                                  addr & 0xff,
                                  ntohs(remoteAddr.sin_port),
                                  clientSocket);

                            sp<Session> clientSession =
                                new Session(
                                        mNextSessionID++,
                                        Session::CONNECTED,
                                        clientSocket,
                                        session->getNotificationMessage());

                            clientSession->setMode(
                                    session->isRTSPServer()
                                        ? Session::MODE_RTSP
                                        : Session::MODE_DATAGRAM);
///Add by MTK @{
                            clientSession->setTCPConnectionType(
                                    session->getTCPConnectionType());
///@}
                            sessionsToAdd.push_back(clientSession);
                        }
                    } else {
///Add by MTK @{
                        ALOGE("accept returned error %d (%s)",
                              errno, strerror(errno));
///@}
                    }
                } else {
                    status_t err = session->readMore();
                    if (err != OK) {
///Add by MTK @{
                        ALOGI("readMore on socket %d failed w/ error %d (%s)",
                              s, err, strerror(-err));
///@}
                    }
                }
            }

            if (FD_ISSET(s, &ws)) {
                status_t err = session->writeMore();
                if (err != OK) {
                    ALOGI("writeMore on socket %d failed w/ error %d (%s)",
                          s, err, strerror(-err));
                }
            }
        }

        while (!sessionsToAdd.empty()) {
            sp<Session> session = *sessionsToAdd.begin();
            sessionsToAdd.erase(sessionsToAdd.begin());

            mSessions.add(session->sessionID(), session);

            ALOGI("added clientSession %d", session->sessionID());
        }
    }
}

/// @{

status_t ANetworkSession::sendDirectRequest(
        int32_t sessionID, const void *data, ssize_t size) {
    
    ssize_t index = mSessions.indexOfKey(sessionID);

    if (index < 0) {
        return -ENOENT;
    }

    const sp<Session> session = mSessions.valueAt(index);

    status_t err = session->writeDirectRequest(data, size);
    
    return err;
}


status_t ANetworkSession::createTCPTextDataSession(
        const struct in_addr &addr, unsigned port,
        const sp<AMessage> &notify, int32_t *sessionID) {
    return createClientOrServer(
            kModeCreateTCPTextDataSessionPassive,
            &addr,
            port,
            NULL /* remoteHost */,
            0 /* remotePort */,
            notify,
            sessionID);
}

status_t ANetworkSession::createUIBCClient(
        const char *host, unsigned port, const sp<AMessage> &notify,
        int32_t *sessionID) {
    return createClientOrServer(
            kModeCreateUIBCClient,
            NULL /* addr */,
            0 /* port */,
            host,
            port,
            notify,
            sessionID);
}

status_t ANetworkSession::createUIBCServer(
        const struct in_addr &addr, unsigned port,
        const sp<AMessage> &notify, int32_t *sessionID) {
    return createClientOrServer(
            kModeCreateUIBCServer,
            &addr,
            port,
            NULL /* remoteHost */,
            0 /* remotePort */,
            notify,
            sessionID);
}


bool ANetworkSession::Session::isTCPServer() const {
    return (mState == LISTENING_TCP_TEXT || mState == LISTENING_TCP_UIBC);
}

void ANetworkSession::Session::setTCPConnectionType(int tcpType) {
    ALOGD("setIsTCPDatagramConnection:%d", tcpType);
    mTcpType = tcpType;
}

int ANetworkSession::Session::getTCPConnectionType() const {
    ALOGD("getIsTCPDatagramConnection:%d", mTcpType);
    return mTcpType;
}

void ANetworkSession::Session::closeSocket() {

    if(mSocket >= 0){
        close(mSocket);
        mSocket = -1;
    }
    
}

/// @}
}  // namespace android

