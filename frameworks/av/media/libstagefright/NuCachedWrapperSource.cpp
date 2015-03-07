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

//#define LOG_NDEBUG 0
#define LOG_TAG "NuCachedWrapperSource"
#include <utils/Log.h>

#include "include/NuCachedWrapperSource.h"

#include <cutils/properties.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/MediaErrors.h>

#define TMPLOG(str,...) ALOGE("[%s]"str, __FUNCTION__, ##__VA_ARGS__);

namespace android {

NuCachedWrapperSource::NuCachedWrapperSource(
        const sp<NuCachedSource2> &mainSource,
        const sp<NuCachedSource2> &secondSource,
        off64_t offset
    )
    : mMainSource(mainSource),
      mSecondSource(secondSource),
      mSecondOffset(offset) {
    ALOGD("NuCachedWrapperSource created");
    }

NuCachedWrapperSource::~NuCachedWrapperSource() {
    ALOGD("~NuCachedWrapperSource");
}

status_t NuCachedWrapperSource::getEstimatedBandwidthKbps(int32_t *kbps) {
    return mMainSource->getEstimatedBandwidthKbps(kbps);
}

status_t NuCachedWrapperSource::setCacheStatCollectFreq(int32_t freqMs) {
    status_t err;
    err = mMainSource->setCacheStatCollectFreq(freqMs);
    if (err != OK)
        return err;
    return mSecondSource->setCacheStatCollectFreq(freqMs);
}

status_t NuCachedWrapperSource::initCheck() const {
    return mMainSource->initCheck();
}

status_t NuCachedWrapperSource::getSize(off64_t *size) {
    return mMainSource->getSize(size);    
}

uint32_t NuCachedWrapperSource::flags() {
    return mMainSource->flags();
}

ssize_t NuCachedWrapperSource::readAt(off64_t offset, void *data, size_t size) {
    ALOGE("Should NOT be here");
    return 0;
}

size_t NuCachedWrapperSource::cachedSize() {
    off64_t totalSize = 0;
    size_t ret;
    double ratio1, ratio2;
    getSize(&totalSize);
    ratio1 = (double)mMainSource->cachedSize()/(double)mSecondOffset;
    ratio2 = (double)(mSecondSource->cachedSize()-mSecondOffset)/(double)(totalSize-mSecondOffset);
    ret = ((double)totalSize*((ratio1 > ratio2)? ratio2 : ratio1));
    TMPLOG("return size=%d", ret);
    return ret;       
}

void NuCachedWrapperSource::resumeFetchingIfNecessary() {
    ALOGD("resumeFetchingIfNecessary");
    mMainSource->resumeFetchingIfNecessary();
    mSecondSource->resumeFetchingIfNecessary();
}

sp<DecryptHandle> NuCachedWrapperSource::DrmInitialization(const char* mime) {
    return mMainSource->DrmInitialization(mime);
}

void NuCachedWrapperSource::getDrmInfo(sp<DecryptHandle> &handle, DrmManagerClient **client) {
    mMainSource->getDrmInfo(handle, client);
}

String8 NuCachedWrapperSource::getUri() {
    ALOGD("getUri");
    return mMainSource->getUri();
}

String8 NuCachedWrapperSource::getMIMEType() const {
    ALOGD("getMIMEType");
    return mMainSource->getMIMEType();
}

void NuCachedWrapperSource::finishCache() {
    ALOGD("finishCache");
    mMainSource->finishCache();
    mSecondSource->finishCache();
}

size_t NuCachedWrapperSource::approxDataRemaining(status_t *finalStatus) const {
    status_t stat1, stat2;
    size_t size1, size2, ret;
    off64_t totalSize;
    double ratio1, ratio2;
    
    mMainSource->getSize(&totalSize);

    size1 = mMainSource->approxDataRemaining(&stat1);
    size2 = mSecondSource->approxDataRemaining(&stat2);

    ratio1 = (double)size1/(double)mSecondOffset;
    ratio2 = (double)size2/(double)(totalSize-mSecondOffset);
    ret = (size_t)((double)totalSize*((ratio1 > ratio2)? ratio2 : ratio1));
    *finalStatus = getMixedStatus(stat1, stat2);
    TMPLOG("finalStatus=%d, return size=%d", *finalStatus, ret);
    return ret;
}

int64_t NuCachedWrapperSource::getMaxCacheSize() {
    ALOGD("getMaxCacheSize");
    return mMainSource->getMaxCacheSize() + mSecondSource->getMaxCacheSize();
}

status_t NuCachedWrapperSource::getRealFinalStatus() {
    status_t ret1 = mMainSource->getRealFinalStatus();
    status_t ret2 = mSecondSource->getRealFinalStatus();
    
    return getMixedStatus(ret1, ret2);
}

status_t NuCachedWrapperSource::getMixedStatus(status_t ret1, status_t ret2) const
{ 
    if (ret1 == OK && ret2 == OK)
        return OK;
    if (ret1 == ERROR_END_OF_STREAM && ret2 == ERROR_END_OF_STREAM)
        return ERROR_END_OF_STREAM;
    if (ret1 == ERROR_END_OF_STREAM || ret2 == ERROR_END_OF_STREAM)
        if (ret1 == OK || ret2 == OK)
            return OK;

    if (ret1 != OK)
        return ret1;
    return ret2;
}



}  // namespace android

