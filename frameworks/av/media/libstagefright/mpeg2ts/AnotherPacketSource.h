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

#ifndef ANOTHER_PACKET_SOURCE_H_

#define ANOTHER_PACKET_SOURCE_H_

#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/MediaSource.h>
#include <utils/threads.h>
#include <utils/List.h>

#include "ATSParser.h"

namespace android {

struct ABuffer;

struct AnotherPacketSource : public MediaSource {
    AnotherPacketSource(const sp<MetaData> &meta);

    void setFormat(const sp<MetaData> &meta);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

    void clear();

    bool hasBufferAvailable(status_t *finalResult);

    // Returns the difference between the last and the first queued
    // presentation timestamps since the last discontinuity (if any).
    int64_t getBufferedDurationUs(status_t *finalResult);

    status_t nextBufferTime(int64_t *timeUs);

    void queueAccessUnit(const sp<ABuffer> &buffer);

    void queueDiscontinuity(
            ATSParser::DiscontinuityType type, const sp<AMessage> &extra);

#ifndef ANDROID_DEFAULT_CODE
   status_t  isEOS();

	//for bitrate-adaptation
	//size_t getBufQueSize(){return m_BufQueSize;} //get Whole Buffer queue size 
	//size_t getTargetTime(){return m_TargetTime;}  //get target protected time of buffer queue duration for interrupt-free playback 
	void setBufQueSize(size_t iBufQueSize){m_BufQueSize = iBufQueSize;}
	void setTargetTime(size_t iTargetTime){m_TargetTime = iTargetTime;}
	bool getNSN(int32_t* uiNextSeqNum);
	size_t getFreeBufSpace();
    // mtk80902: porting from APacketSource
	void setScanForIDR(bool enable);
#endif
    void signalEOS(status_t result);

    status_t dequeueAccessUnit(sp<ABuffer> *buffer);

    bool isFinished(int64_t duration) const;

protected:
    virtual ~AnotherPacketSource();

private:
    Mutex mLock;
    Condition mCondition;

    bool mIsAudio;
    sp<MetaData> mFormat;
    int64_t mLastQueuedTimeUs;
    List<sp<ABuffer> > mBuffers;
    status_t mEOSResult;
#ifndef ANDROID_DEFAULT_CODE
    bool mIsEOS;

	//for bitrate-adaptation
	size_t m_BufQueSize; //Whole Buffer queue size 
    size_t m_TargetTime;  // target protected time of buffer queue duration for interrupt-free playback 
    int32_t m_uiNextAduSeqNum;

    // mtk80902: porting from APacketSource
    // wait IDR for 264
    bool mScanForIDR;
    bool mIsAVC;
    bool mNeedScanForIDR;
#endif	

    bool wasFormatChange(int32_t discontinuityType) const;

    DISALLOW_EVIL_CONSTRUCTORS(AnotherPacketSource);
};


}  // namespace android

#endif  // ANOTHER_PACKET_SOURCE_H_
