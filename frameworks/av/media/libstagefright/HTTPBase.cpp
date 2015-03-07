/*
 * Copyright (C) 2011 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "HTTPBase"
#include <utils/Log.h>

#include "include/HTTPBase.h"

#if CHROMIUM_AVAILABLE
#include "include/chromium_http_stub.h"
#endif

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/ALooper.h>

#include <cutils/properties.h>
#include <cutils/qtaguid.h>

#include <ConnectivityManager.h>

namespace android {

HTTPBase::HTTPBase()
    : mNumBandwidthHistoryItems(0),
      mTotalTransferTimeUs(0),
      mTotalTransferBytes(0),
      mPrevBandwidthMeasureTimeUs(0),
      mPrevEstimatedBandWidthKbps(0),
      mBandWidthCollectFreqMs(5000),
      mUIDValid(false),
      mUID(0) {
}

// static
sp<HTTPBase> HTTPBase::Create(uint32_t flags) {
#if CHROMIUM_AVAILABLE
        HTTPBase *dataSource = createChromiumHTTPDataSource(flags);
        if (dataSource) {
           return dataSource;
        }
#endif
    {
        TRESPASS();

        return NULL;
    }
}

// static
status_t HTTPBase::UpdateProxyConfig(
        const char *host, int32_t port, const char *exclusionList) {
#if CHROMIUM_AVAILABLE
    return UpdateChromiumHTTPDataSourceProxyConfig(host, port, exclusionList);
#else
    return INVALID_OPERATION;
#endif
}

void HTTPBase::addBandwidthMeasurement(
        size_t numBytes, int64_t delayUs) {
    Mutex::Autolock autoLock(mLock);

    BandwidthEntry entry;
    entry.mDelayUs = delayUs;
    entry.mNumBytes = numBytes;
    mTotalTransferTimeUs += delayUs;
    mTotalTransferBytes += numBytes;

#ifndef ANDROID_DEFAULT_CODE
    if(numBytes == 0)
    {
        // Store byte == 0 reading seemed nonsense...
        return;
    }
#endif

    mBandwidthHistory.push_back(entry);
#ifndef ANDROID_DEFAULT_CODE
    if (++mNumBandwidthHistoryItems > 20) {
#else
    if (++mNumBandwidthHistoryItems > 100) {
#endif        
        BandwidthEntry *entry = &*mBandwidthHistory.begin();
        mTotalTransferTimeUs -= entry->mDelayUs;
        mTotalTransferBytes -= entry->mNumBytes;
        mBandwidthHistory.erase(mBandwidthHistory.begin());
        --mNumBandwidthHistoryItems;

        int64_t timeNowUs = ALooper::GetNowUs();
        if (timeNowUs - mPrevBandwidthMeasureTimeUs >=
                mBandWidthCollectFreqMs * 1000LL) {

            if (mPrevBandwidthMeasureTimeUs != 0) {
                mPrevEstimatedBandWidthKbps =
                    (mTotalTransferBytes * 8E3 / mTotalTransferTimeUs);
            }
            mPrevBandwidthMeasureTimeUs = timeNowUs;
        }
    }

}

bool HTTPBase::estimateBandwidth(int32_t *bandwidth_bps) {
    Mutex::Autolock autoLock(mLock);

    if (mNumBandwidthHistoryItems < 2) {
        return false;
    }

    *bandwidth_bps = ((double)mTotalTransferBytes * 8E6 / mTotalTransferTimeUs);

    return true;
}

status_t HTTPBase::getEstimatedBandwidthKbps(int32_t *kbps) {
    Mutex::Autolock autoLock(mLock);
    *kbps = mPrevEstimatedBandWidthKbps;
    return OK;
}

status_t HTTPBase::setBandwidthStatCollectFreq(int32_t freqMs) {
    Mutex::Autolock autoLock(mLock);

    if (freqMs < kMinBandwidthCollectFreqMs
            || freqMs > kMaxBandwidthCollectFreqMs) {

        ALOGE("frequency (%d ms) is out of range [1000, 60000]", freqMs);
        return BAD_VALUE;
    }

    ALOGI("frequency set to %d ms", freqMs);
    mBandWidthCollectFreqMs = freqMs;
    return OK;
}

void HTTPBase::setUID(uid_t uid) {
    mUIDValid = true;
    mUID = uid;
}

bool HTTPBase::getUID(uid_t *uid) const {
    if (!mUIDValid) {
        return false;
    }

    *uid = mUID;

    return true;
}

// static
void HTTPBase::RegisterSocketUserTag(int sockfd, uid_t uid, uint32_t kTag) {
    int res = qtaguid_tagSocket(sockfd, kTag, uid);
    if (res != 0) {
        ALOGE("Failed tagging socket %d for uid %d (My UID=%d)", sockfd, uid, geteuid());
    }
}

// static
void HTTPBase::UnRegisterSocketUserTag(int sockfd) {
    int res = qtaguid_untagSocket(sockfd);
    if (res != 0) {
        ALOGE("Failed untagging socket %d (My UID=%d)", sockfd, geteuid());
    }
}

// static
void HTTPBase::RegisterSocketUserMark(int sockfd, uid_t uid) {
    ConnectivityManager::markSocketAsUser(sockfd, uid);
}

// static
void HTTPBase::UnRegisterSocketUserMark(int sockfd) {
    RegisterSocketUserMark(sockfd, geteuid());
}

}  // namespace android
