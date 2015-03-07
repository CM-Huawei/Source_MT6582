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

#ifndef NU_CACHED_WRAPPER_SOURCE_H_

#define NU_CACHED_WRAPPER_SOURCE_H_

#include "include/NuCachedSource2.h"

namespace android {

struct NuCachedWrapperSource : public NuCachedSource2 {
    NuCachedWrapperSource(
            const sp<NuCachedSource2> &Source1,
            const sp<NuCachedSource2> &Source2,
            off64_t offset
            );

    virtual status_t initCheck() const;

    virtual ssize_t readAt(off64_t offset, void *data, size_t size);

    virtual status_t getSize(off64_t *size);
    virtual uint32_t flags();

    virtual sp<DecryptHandle> DrmInitialization(const char* mime);
    virtual void getDrmInfo(sp<DecryptHandle> &handle, DrmManagerClient **client);
    virtual String8 getUri();

    virtual String8 getMIMEType() const;

    virtual size_t cachedSize();
    virtual size_t approxDataRemaining(status_t *finalStatus) const;

    virtual void resumeFetchingIfNecessary();

    virtual status_t getEstimatedBandwidthKbps(int32_t *kbps);
    virtual status_t setCacheStatCollectFreq(int32_t freqMs);

    virtual int64_t getMaxCacheSize();
    virtual status_t getRealFinalStatus(); 
	virtual void finishCache();		
    

protected:
    virtual ~NuCachedWrapperSource();

private:
	sp<NuCachedSource2> mMainSource;
	sp<NuCachedSource2> mSecondSource;

	off64_t mSecondOffset;

	status_t getMixedStatus(status_t ret1, status_t ret2) const;

    DISALLOW_EVIL_CONSTRUCTORS(NuCachedWrapperSource);
};

}  // namespace android

#endif  // NU_CACHED_SOURCE_2_H_

