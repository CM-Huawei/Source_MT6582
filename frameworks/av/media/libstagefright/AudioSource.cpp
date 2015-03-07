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
#define LOG_TAG "AudioSource"
#include <utils/Log.h>

#include <media/AudioRecord.h>
#include <media/stagefright/AudioSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/ALooper.h>
#include <cutils/properties.h>
#include <stdlib.h>

#ifndef ANDROID_DEFAULT_CODE
#define AUDIOSOURCE_USE_XLOG
#ifdef AUDIOSOURCE_USE_XLOG
#include <cutils/xlog.h>
#undef ALOGE
#undef ALOGW
#undef ALOGI
#undef ALOGD
#undef ALOGV
#define ALOGE XLOGE
#define ALOGW XLOGW
#define ALOGI XLOGI
#define ALOGD XLOGD
#define ALOGV XLOGV
#endif
#endif
namespace android {

static void AudioRecordCallbackFunction(int event, void *user, void *info) {
    AudioSource *source = (AudioSource *) user;
    switch (event) {
        case AudioRecord::EVENT_MORE_DATA: {
            source->dataCallback(*((AudioRecord::Buffer *) info));
            break;
        }
        case AudioRecord::EVENT_OVERRUN: {
            ALOGW("AudioRecord reported overrun!");
            break;
        }
#ifndef ANDROID_DEFAULT_CODE 
//deal time out MTK80721 2012-03-29
        case AudioRecord::EVENT_WAIT_TIEMOUT:{
            ALOGE("audio record wait time out");
            AudioRecord::Buffer pbuffer;
	        pbuffer.raw = NULL;
            source->dataCallback(pbuffer);
            break;
        }
#endif
        default:
            // does nothing
            break;
    }
}

AudioSource::AudioSource(
        audio_source_t inputSource, uint32_t sampleRate, uint32_t channelCount)
    : mStarted(false),
      mSampleRate(sampleRate),
      mPrevSampleTimeUs(0),
      mNumFramesReceived(0),
      mNumClientOwnedBuffers(0) {
#ifndef ANDROID_DEFAULT_CODE
ALOGI("AudioSource constructor sampleRate: %d, channelCount: %d", sampleRate, channelCount);
#else
    ALOGV("sampleRate: %d, channelCount: %d", sampleRate, channelCount);
#endif
    CHECK(channelCount == 1 || channelCount == 2);

    size_t minFrameCount;
    status_t status = AudioRecord::getMinFrameCount(&minFrameCount,
                                           sampleRate,
                                           AUDIO_FORMAT_PCM_16_BIT,
                                           audio_channel_in_mask_from_count(channelCount));
    if (status == OK) {
        // make sure that the AudioRecord callback never returns more than the maximum
        // buffer size
        int frameCount = kMaxBufferSize / sizeof(int16_t) / channelCount;

        // make sure that the AudioRecord total buffer size is large enough
        int bufCount = 2;
        while ((bufCount * frameCount) < minFrameCount) {
            bufCount++;
        }

        mRecord = new AudioRecord(
                    inputSource, sampleRate, AUDIO_FORMAT_PCM_16_BIT,
                    audio_channel_in_mask_from_count(channelCount),
                    bufCount * frameCount,
                    AudioRecordCallbackFunction,
                    this,
                    frameCount);
        mInitCheck = mRecord->initCheck();
#ifndef ANDROID_DEFAULT_CODE
ALOGI("AudioSource constructor, getMinFrameCount return minFrameCount =%d",minFrameCount);
ALOGI("AudioSource constructor, buffer requirment: frameCount,=%d,bufCount =%d,mInitCheck=%d",\
frameCount,bufCount,mInitCheck);
#endif
    } else {
        mInitCheck = status;
#ifndef ANDROID_DEFAULT_CODE
ALOGI("AudioSource constructor, getMinFrameCount fail !!!,mInitCheck=%d",mInitCheck);
#endif
    }
}

//MTK80721 HDRecord 2011-12-23
//#ifdef MTK_AUDIO_HD_REC_SUPPORT
#ifndef ANDROID_DEFAULT_CODE
AudioSource::AudioSource(
        audio_source_t inputSource, uint32_t sampleRate, String8 Params, 
        uint32_t channelCount)
    : mRecord(NULL),
      mStarted(false),
      mSampleRate(sampleRate),
      mPrevSampleTimeUs(0),
      mNumFramesReceived(0),
      mNumClientOwnedBuffers(0) {

    ALOGI("sampleRate: %d, channelCount: %d", sampleRate, channelCount);
    CHECK(channelCount == 1 || channelCount == 2);

    size_t minFrameCount;
    status_t status = AudioRecord::getMinFrameCount(&minFrameCount,
                                           sampleRate,
                                           AUDIO_FORMAT_PCM_16_BIT,
                                           audio_channel_in_mask_from_count(channelCount));
    if (status == OK) {
        // make sure that the AudioRecord callback never returns more than the maximum
        // buffer size
        int frameCount = kMaxBufferSize / sizeof(int16_t) / channelCount;

        // make sure that the AudioRecord total buffer size is large enough
        int bufCount = 2;
        while ((bufCount * frameCount) < minFrameCount) {
            bufCount++;
        }        
        int iframecount = bufCount * frameCount;
        iframecount >>=1;
        
        ALOGD("minFrameCount=%d,iframecount=%d,total framecount=%d,notify framecount=%d",
            minFrameCount,iframecount,iframecount*3,iframecount>>1);

        mRecord = new AudioRecord(
                    inputSource, Params, sampleRate, AUDIO_FORMAT_PCM_16_BIT,
                    audio_channel_in_mask_from_count(channelCount),
                    3*iframecount,
                    AudioRecordCallbackFunction,
                    this,
                    iframecount);

        mInitCheck = mRecord->initCheck();
ALOGI("AudioSource constructor, getMinFrameCount return minFrameCount =%d",minFrameCount);
ALOGI("AudioSource constructor, buffer requirment: frameCount,=%d,bufCount =%d,mInitCheck=%d",\
frameCount,bufCount,mInitCheck);
    } else {
        mInitCheck = status;
ALOGI("AudioSource constructor, getMinFrameCount fail !!!,mInitCheck=%d",mInitCheck);
    }
}
#endif

AudioSource::~AudioSource() {
#ifndef  ANDROID_DEFAULT_CODE
ALOGI("~AudioSource destructor");
#endif
    if (mStarted) {
        reset();
    }
}

status_t AudioSource::initCheck() const {
    return mInitCheck;
}

status_t AudioSource::start(MetaData *params) {
    Mutex::Autolock autoLock(mLock);
    if (mStarted) {
        return UNKNOWN_ERROR;
    }

    if (mInitCheck != OK) {
        return NO_INIT;
    }

    mTrackMaxAmplitude = false;
    mMaxAmplitude = 0;
    mInitialReadTimeUs = 0;
    mStartTimeUs = 0;
    int64_t startTimeUs;
    if (params && params->findInt64(kKeyTime, &startTimeUs)) {
        mStartTimeUs = startTimeUs;
    }
#ifndef ANDROID_DEFAULT_CODE
ALOGI("start, call AudioRecord start+++,mStartTimeUs=%lld",mStartTimeUs);
#endif
    status_t err = mRecord->start();
#ifndef ANDROID_DEFAULT_CODE
ALOGI("start, call AudioRecord start ---,err=%d",err);
#endif
    if (err == OK) {
        mStarted = true;
    } else {
        mRecord.clear();
    }


    return err;
}

void AudioSource::releaseQueuedFrames_l() {
    ALOGV("releaseQueuedFrames_l");
    List<MediaBuffer *>::iterator it;
    while (!mBuffersReceived.empty()) {
        it = mBuffersReceived.begin();
        (*it)->release();
        mBuffersReceived.erase(it);
    }
}

void AudioSource::waitOutstandingEncodingFrames_l() {
    ALOGV("waitOutstandingEncodingFrames_l: %lld", mNumClientOwnedBuffers);
    while (mNumClientOwnedBuffers > 0) {
        mFrameEncodingCompletionCondition.wait(mLock);
    }
}

status_t AudioSource::reset() {
    Mutex::Autolock autoLock(mLock);
#ifndef ANDROID_DEFAULT_CODE
ALOGI("reset");
#endif
    if (!mStarted) {
        return UNKNOWN_ERROR;
    }

    if (mInitCheck != OK) {
        return NO_INIT;
    }

    mStarted = false;
#ifndef ANDROID_DEFAULT_CODE
ALOGI("reset, call AudioRecord stop+++");
#endif
    mRecord->stop();
#ifndef ANDROID_DEFAULT_CODE
ALOGI("reset, call AudioRecord stop---");
#endif
    waitOutstandingEncodingFrames_l();
    releaseQueuedFrames_l();

    return OK;
}

sp<MetaData> AudioSource::getFormat() {
    Mutex::Autolock autoLock(mLock);
    if (mInitCheck != OK) {
        return 0;
    }

    sp<MetaData> meta = new MetaData;
    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);
    meta->setInt32(kKeySampleRate, mSampleRate);
    meta->setInt32(kKeyChannelCount, mRecord->channelCount());
    meta->setInt32(kKeyMaxInputSize, kMaxBufferSize);

    return meta;
}

void AudioSource::rampVolume(
        int32_t startFrame, int32_t rampDurationFrames,
        uint8_t *data,   size_t bytes) {

    const int32_t kShift = 14;
    int32_t fixedMultiplier = (startFrame << kShift) / rampDurationFrames;
    const int32_t nChannels = mRecord->channelCount();
    int32_t stopFrame = startFrame + bytes / sizeof(int16_t);
    int16_t *frame = (int16_t *) data;
    if (stopFrame > rampDurationFrames) {
        stopFrame = rampDurationFrames;
    }

    while (startFrame < stopFrame) {
        if (nChannels == 1) {  // mono
            frame[0] = (frame[0] * fixedMultiplier) >> kShift;
            ++frame;
            ++startFrame;
        } else {               // stereo
            frame[0] = (frame[0] * fixedMultiplier) >> kShift;
            frame[1] = (frame[1] * fixedMultiplier) >> kShift;
            frame += 2;
            startFrame += 2;
        }

        // Update the multiplier every 4 frames
        if ((startFrame & 3) == 0) {
            fixedMultiplier = (startFrame << kShift) / rampDurationFrames;
        }
    }
}

status_t AudioSource::read(
        MediaBuffer **out, const ReadOptions *options) {
    Mutex::Autolock autoLock(mLock);
    *out = NULL;

    if (mInitCheck != OK) {
        return NO_INIT;
    }
#ifndef ANDROID_DEFAULT_CODE
ALOGV("read");
#endif
    while (mStarted && mBuffersReceived.empty()) {
        mFrameAvailableCondition.wait(mLock);
    }
    if (!mStarted) {
        return OK;
    }
    MediaBuffer *buffer = *mBuffersReceived.begin();
    mBuffersReceived.erase(mBuffersReceived.begin());
    ++mNumClientOwnedBuffers;
    buffer->setObserver(this);
    buffer->add_ref();

    // Mute/suppress the recording sound
    int64_t timeUs;
    CHECK(buffer->meta_data()->findInt64(kKeyTime, &timeUs));
#ifndef ANDROID_DEFAULT_CODE
ALOGV("read, buffer kKeyTime timeUs=%lld",timeUs);
//deal time out MTK80721 2012-03-29
    if (timeUs < 0)
    {
        ALOGE("AudioSource::read: timeout");
        //buffer->release();
        *out = buffer;
        return TIMED_OUT;
    }
#endif
    int64_t elapsedTimeUs = timeUs - mStartTimeUs;
    if (elapsedTimeUs < kAutoRampStartUs) {
        memset((uint8_t *) buffer->data(), 0, buffer->range_length());
    } else if (elapsedTimeUs < kAutoRampStartUs + kAutoRampDurationUs) {
        int32_t autoRampDurationFrames =
                    ((int64_t)kAutoRampDurationUs * mSampleRate + 500000LL) / 1000000LL; //Need type casting

        int32_t autoRampStartFrames =
                    ((int64_t)kAutoRampStartUs * mSampleRate + 500000LL) / 1000000LL; //Need type casting

        int32_t nFrames = mNumFramesReceived - autoRampStartFrames;
        rampVolume(nFrames, autoRampDurationFrames,
                (uint8_t *) buffer->data(), buffer->range_length());
    }

    // Track the max recording signal amplitude.
    if (mTrackMaxAmplitude) {
        trackMaxAmplitude(
            (int16_t *) buffer->data(), buffer->range_length() >> 1);
    }

    *out = buffer;
    return OK;
}

void AudioSource::signalBufferReturned(MediaBuffer *buffer) {
    ALOGV("signalBufferReturned: %p", buffer->data());
    Mutex::Autolock autoLock(mLock);
    --mNumClientOwnedBuffers;
    buffer->setObserver(0);
    buffer->release();
    mFrameEncodingCompletionCondition.signal();
    return;
}

status_t AudioSource::dataCallback(const AudioRecord::Buffer& audioBuffer) {
    int64_t timeUs = systemTime() / 1000ll;

    ALOGV("dataCallbackTimestamp: %lld us", timeUs);
    Mutex::Autolock autoLock(mLock);
    if (!mStarted) {
        ALOGW("Spurious callback from AudioRecord. Drop the audio data.");
        return OK;
    }

#ifndef ANDROID_DEFAULT_CODE
//deal time out MTK80721 2012-03-29
    if (audioBuffer.raw == NULL)
    {
        MediaBuffer *buffer = new MediaBuffer(0);
	    timeUs = -1;
        buffer->meta_data()->setInt64(kKeyTime, timeUs);
        mBuffersReceived.push_back(buffer);
        mFrameAvailableCondition.signal();
        ALOGE("AudioSource::dataCallbackTimestamp:deal audio record time out");
        return TIMED_OUT;
    }
#endif

    // Drop retrieved and previously lost audio data.
    if (mNumFramesReceived == 0 && timeUs < mStartTimeUs) {
        mRecord->getInputFramesLost();
#ifndef ANDROID_DEFAULT_CODE
 ALOGD("dataCallback,Drop audio data at %lld/%lld us", timeUs, mStartTimeUs);
#else
        ALOGV("Drop audio data at %lld/%lld us", timeUs, mStartTimeUs);
#endif
        return OK;
    }

    if (mNumFramesReceived == 0 && mPrevSampleTimeUs == 0) {
        mInitialReadTimeUs = timeUs;
#ifndef ANDROID_DEFAULT_CODE
ALOGD("dataCallback, receiving time of the first frame mInitialReadTimeUs =%lld",mInitialReadTimeUs);
ALOGD("mStartTimeUs = %lld",mStartTimeUs);
#endif
        // Initial delay
        if (mStartTimeUs > 0) {
            mStartTimeUs = timeUs - mStartTimeUs;
        } else {
            // Assume latency is constant.
            mStartTimeUs += mRecord->latency() * 1000;
        }

        mPrevSampleTimeUs = mStartTimeUs;
#ifndef ANDROID_DEFAULT_CODE
ALOGD("dataCallback, the revised mStartTimeUs =%lld",mStartTimeUs);
#endif 
    }

    size_t numLostBytes = 0;
    if (mNumFramesReceived > 0) {  // Ignore earlier frame lost
        // getInputFramesLost() returns the number of lost frames.
        // Convert number of frames lost to number of bytes lost.
        numLostBytes = mRecord->getInputFramesLost() * mRecord->frameSize();
    }

    CHECK_EQ(numLostBytes & 1, 0u);
    CHECK_EQ(audioBuffer.size & 1, 0u);
    if (numLostBytes > 0) {
        // Loss of audio frames should happen rarely; thus the LOGW should
        // not cause a logging spam
        ALOGW("Lost audio record data: %d bytes", numLostBytes);
    }

    while (numLostBytes > 0) {
        size_t bufferSize = numLostBytes;
        if (numLostBytes > kMaxBufferSize) {
            numLostBytes -= kMaxBufferSize;
            bufferSize = kMaxBufferSize;
        } else {
            numLostBytes = 0;
        }
        MediaBuffer *lostAudioBuffer = new MediaBuffer(bufferSize);
        memset(lostAudioBuffer->data(), 0, bufferSize);
        lostAudioBuffer->set_range(0, bufferSize);
        queueInputBuffer_l(lostAudioBuffer, timeUs);
#ifndef ANDROID_DEFAULT_CODE
ALOGD("dataCallback,queue one input buffer with all 0 data for lost data");
#endif
    }

    if (audioBuffer.size == 0) {
        ALOGW("Nothing is available from AudioRecord callback buffer");
        return OK;
    }

    const size_t bufferSize = audioBuffer.size;
    MediaBuffer *buffer = new MediaBuffer(bufferSize);
    memcpy((uint8_t *) buffer->data(),
            audioBuffer.i16, audioBuffer.size);
    buffer->set_range(0, bufferSize);
    queueInputBuffer_l(buffer, timeUs);
#ifndef ANDROID_DEFAULT_CODE
ALOGV("dataCallback,receive one audio buffer (size =%d,timeUs=%lld)",bufferSize,timeUs);
#endif
    return OK;
}

#ifndef ANDROID_DEFAULT_CODE
void AudioSource::queueInputBuffer_l(MediaBuffer *buffer, int64_t timeUs) {
    const size_t bufferSize = buffer->range_length();
    const size_t frameSize = mRecord->frameSize();

    if (mNumFramesReceived == 0) {
        buffer->meta_data()->setInt64(kKeyAnchorTime, mStartTimeUs);
ALOGD("queueInputBuffer_l,first fram kKeyAnchorTime =%lld",mStartTimeUs);
    }
    mNumFramesReceived += bufferSize / frameSize;
    const int64_t timestampUs =  mStartTimeUs + ((1000000LL * mNumFramesReceived) + (mSampleRate >> 1)) / mSampleRate;
ALOGV("queueInputBuffer_l,containing %d frams in this buffer,mPrevSampleTimeUs( %lld ),receiving drift timeUs( %lld ),new calculated timestampUs( %lld ),",\
mNumFramesReceived,mPrevSampleTimeUs,timeUs - mInitialReadTimeUs,timestampUs);    
buffer->meta_data()->setInt64(kKeyTime, mPrevSampleTimeUs);
    buffer->meta_data()->setInt64(kKeyDriftTime, timeUs - mInitialReadTimeUs);
    mPrevSampleTimeUs = timestampUs;
    
    mBuffersReceived.push_back(buffer);
    mFrameAvailableCondition.signal();
}
#else
void AudioSource::queueInputBuffer_l(MediaBuffer *buffer, int64_t timeUs) {
    const size_t bufferSize = buffer->range_length();
    const size_t frameSize = mRecord->frameSize();
    const int64_t timestampUs =
                mPrevSampleTimeUs +
                    ((1000000LL * (bufferSize / frameSize)) +
                        (mSampleRate >> 1)) / mSampleRate;

    if (mNumFramesReceived == 0) {
        buffer->meta_data()->setInt64(kKeyAnchorTime, mStartTimeUs);
    }

    buffer->meta_data()->setInt64(kKeyTime, mPrevSampleTimeUs);
    buffer->meta_data()->setInt64(kKeyDriftTime, timeUs - mInitialReadTimeUs);
    mPrevSampleTimeUs = timestampUs;
    mNumFramesReceived += bufferSize / frameSize;
    mBuffersReceived.push_back(buffer);
    mFrameAvailableCondition.signal();
}
#endif
void AudioSource::trackMaxAmplitude(int16_t *data, int nSamples) {
    for (int i = nSamples; i > 0; --i) {
        int16_t value = *data++;
        if (value < 0) {
            value = -value;
        }
        if (mMaxAmplitude < value) {
            mMaxAmplitude = value;
        }
    }
}

int16_t AudioSource::getMaxAmplitude() {
    // First call activates the tracking.
    if (!mTrackMaxAmplitude) {
        mTrackMaxAmplitude = true;
    }
    int16_t value = mMaxAmplitude;
    mMaxAmplitude = 0;
    ALOGV("max amplitude since last call: %d", value);
    return value;
}

}  // namespace android
