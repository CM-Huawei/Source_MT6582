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
#define LOG_TAG "NuPlayerRenderer"
#include <utils/Log.h>

#include "NuPlayerRenderer.h"

#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#ifndef ANDROID_DEFAULT_CODE
#define DUMP_PROFILE 0
#define MAX_VIDEO_EARLY_POST_US		30000ll
#include <media/AudioTrackCenter.h>
#endif

namespace android {

// static
const int64_t NuPlayer::Renderer::kMinPositionUpdateDelayUs = 100000ll;

NuPlayer::Renderer::Renderer(
        const sp<MediaPlayerBase::AudioSink> &sink,
        const sp<AMessage> &notify,
        uint32_t flags)
    : mAudioSink(sink),
      mNotify(notify),
      mFlags(flags),
      mNumFramesWritten(0),
      mDrainAudioQueuePending(false),
      mDrainVideoQueuePending(false),
      mAudioQueueGeneration(0),
      mVideoQueueGeneration(0),
      mAnchorTimeMediaUs(-1),
      mAnchorTimeRealUs(-1),
      mFlushingAudio(false),
      mFlushingVideo(false),
      mHasAudio(false),
      mHasVideo(false),
      mSyncQueues(false),
      mPaused(false),
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_CLEARMOTION_SUPPORT
      mPausing(false),
      mMJCPauseDelay(false),
#endif
      mNeedNewAudioAnchorTime(false),
#endif
      mVideoRenderingStarted(false),
      mVideoRenderingStartGeneration(0),
      mAudioRenderingStartGeneration(0),
      mLastPositionUpdateUs(-1ll),
      mVideoLateByUs(0ll) {
}

NuPlayer::Renderer::~Renderer() {
}

void NuPlayer::Renderer::queueBuffer(
        bool audio,
        const sp<ABuffer> &buffer,
        const sp<AMessage> &notifyConsumed) {
    sp<AMessage> msg = new AMessage(kWhatQueueBuffer, id());
    msg->setInt32("audio", static_cast<int32_t>(audio));
    msg->setBuffer("buffer", buffer);
    msg->setMessage("notifyConsumed", notifyConsumed);
    msg->post();
}

void NuPlayer::Renderer::queueEOS(bool audio, status_t finalResult) {
    CHECK_NE(finalResult, (status_t)OK);

    sp<AMessage> msg = new AMessage(kWhatQueueEOS, id());
    msg->setInt32("audio", static_cast<int32_t>(audio));
    msg->setInt32("finalResult", finalResult);
    msg->post();
}

void NuPlayer::Renderer::flush(bool audio) {
    {
        Mutex::Autolock autoLock(mFlushLock);
#ifndef ANDROID_DEFAULT_CODE
        if (audio) {
            if(true == mFlushingAudio)
               return;
        } else {
            if(true == mFlushingVideo)
               return;
        }
#else
        if (audio) {
            CHECK(!mFlushingAudio);
            mFlushingAudio = true;
        } else {
            CHECK(!mFlushingVideo);
            mFlushingVideo = true;
        }
#endif
    }

    sp<AMessage> msg = new AMessage(kWhatFlush, id());
    msg->setInt32("audio", static_cast<int32_t>(audio));
    msg->post();
}

void NuPlayer::Renderer::signalTimeDiscontinuity() {
#ifndef ANDROID_DEFAULT_CODE
    if (!mAudioQueue.empty()) {
        ALOGE("------signalTimeDiscontinuity (audio size=%d)----", (int)mAudioQueue.size());
        dumpQueue(&mAudioQueue, true);
        ALOGE("-----------------------------");
    }
    if (!mVideoQueue.empty()) {
        ALOGE("------signalTimeDiscontinuity (video size=%d)----", (int)mVideoQueue.size());
        dumpQueue(&mVideoQueue, false);
        ALOGE("-----------------------------");
    }
#endif
    // CHECK(mAudioQueue.empty());
    // CHECK(mVideoQueue.empty());
    mAnchorTimeMediaUs = -1;
    mAnchorTimeRealUs = -1;
    mSyncQueues = false;
}

void NuPlayer::Renderer::pause() {
    (new AMessage(kWhatPause, id()))->post();
}

void NuPlayer::Renderer::resume() {
    (new AMessage(kWhatResume, id()))->post();
}

void NuPlayer::Renderer::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatDrainAudioQueue:
        {
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));
            if (generation != mAudioQueueGeneration) {
                break;
            }

            mDrainAudioQueuePending = false;

            if (onDrainAudioQueue()) {
                uint32_t numFramesPlayed;
                CHECK_EQ(mAudioSink->getPosition(&numFramesPlayed),
                         (status_t)OK);

                uint32_t numFramesPendingPlayout =
                    mNumFramesWritten - numFramesPlayed;

                // This is how long the audio sink will have data to
                // play back.
                int64_t delayUs =
                    mAudioSink->msecsPerFrame()
                        * numFramesPendingPlayout * 1000ll;

                // Let's give it more data after about half that time
                // has elapsed.
#ifndef ANDROID_DEFAULT_CODE
      			// half that is to large, set it to 20ms
      			if (numFramesPlayed > mNumFramesWritten) 
		           ALOGW("numFramesPlayed(%d) > mNumFramesWritten(%d), no reset @ kWhatDrainAudioQueue", 
                            numFramesPlayed, mNumFramesWritten);
      			if (delayUs > 40000)
					delayUs = 40000;
#endif
                postDrainAudioQueue(delayUs / 2);
            }
            break;
        }

        case kWhatDrainVideoQueue:
        {
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));
            if (generation != mVideoQueueGeneration) {
                break;
            }

            mDrainVideoQueuePending = false;

            onDrainVideoQueue();

            postDrainVideoQueue();
            break;
        }

        case kWhatQueueBuffer:
        {
            onQueueBuffer(msg);
            break;
        }

        case kWhatQueueEOS:
        {
            onQueueEOS(msg);
            break;
        }

        case kWhatFlush:
        {
            onFlush(msg);
            break;
        }

        case kWhatAudioSinkChanged:
        {
            onAudioSinkChanged();
            break;
        }

        case kWhatPause:
        {
			
#if !defined(ANDROID_DEFAULT_CODE) && defined(MTK_CLEARMOTION_SUPPORT)  
            int32_t forcePause = 0;
            if(msg->findInt32("force-pause", &forcePause) && forcePause == 1) {
                if(!mPausing)
                    break;
                ALOGI("delay time arrived, try to force pause");
                mPausing = false;
                mMJCPauseDelay = false;

            }

            if (mPaused || mPausing) {
                ALOGW("NuPlayer::Renderer::onPause already paused or in pausing(%d) state", mPausing);
                break;
            }

            if( mMJCPauseDelay && !mPausing) {
                mPausing = true;
                ALOGI("need pause delay for MJC");
                msg->setInt32("force-pause", 1);
                msg->post(300000);
                break;
            }
#endif
			
            onPause();
            break;
        }

        case kWhatResume:
        {
            onResume();
            break;
        }

        default:
            TRESPASS();
            break;
    }
}

void NuPlayer::Renderer::postDrainAudioQueue(int64_t delayUs) {
    if (mDrainAudioQueuePending || mSyncQueues || mPaused) {
        return;
    }

    if (mAudioQueue.empty()) {
        return;
    }

    mDrainAudioQueuePending = true;
    sp<AMessage> msg = new AMessage(kWhatDrainAudioQueue, id());
    msg->setInt32("generation", mAudioQueueGeneration);
    msg->post(delayUs);
}

void NuPlayer::Renderer::signalAudioSinkChanged() {
    (new AMessage(kWhatAudioSinkChanged, id()))->post();
}

void NuPlayer::Renderer::prepareForMediaRenderingStart() {
    mAudioRenderingStartGeneration = mAudioQueueGeneration;
    mVideoRenderingStartGeneration = mVideoQueueGeneration;
}

void NuPlayer::Renderer::notifyIfMediaRenderingStarted() {
    if (mVideoRenderingStartGeneration == mVideoQueueGeneration &&
        mAudioRenderingStartGeneration == mAudioQueueGeneration) {
        mVideoRenderingStartGeneration = -1;
        mAudioRenderingStartGeneration = -1;

        sp<AMessage> notify = mNotify->dup();
        notify->setInt32("what", kWhatMediaRenderingStart);
        notify->post();
    }
}

bool NuPlayer::Renderer::onDrainAudioQueue() {
    uint32_t numFramesPlayed;
	
#ifndef ANDROID_DEFAULT_CODE   //flush mutex
     Mutex::Autolock autoLock(mFlushLock);
#endif

    if (mAudioSink->getPosition(&numFramesPlayed) != OK) {
#ifndef ANDROID_DEFAULT_CODE
        // mtk80902: ALPS00504270
        // ACodec got an error at the beginning, if here returned
        // false directly then NuPlayer would never receive 
        // audio renderer EOS anymore.
        ALOGD("audio sink get position false.");
        if (!mAudioQueue.empty()) {
            QueueEntry *entry = &*mAudioQueue.begin();
            if (entry->mBuffer == NULL) {
                // EOS
                notifyEOS(true /* audio */, entry->mFinalResult);
                mAudioQueue.erase(mAudioQueue.begin());
                entry = NULL;
				mNeedNewAudioAnchorTime = false;
                mHasAudio = false;
                ALOGD("audio position EOS at the beginning.");
            }
        }
#endif
        return false;
    }

    ssize_t numFramesAvailableToWrite =
        mAudioSink->frameCount() - (mNumFramesWritten - numFramesPlayed);

#ifndef ANDROID_DEFAULT_CODE
	if (numFramesPlayed > mNumFramesWritten) 
		ALOGW("numFramesPlayed(%d) > mNumFramesWritten(%d), no reset @ onDrainAudioQueue", numFramesPlayed, mNumFramesWritten);

	if (numFramesAvailableToWrite > mAudioSink->frameCount()*3/4 || mAudioQueue.size() <= 3)
		ALOGW("numFramesAvailableToWrite(%d), mAudioSink frameCount(%d), mAudioQueue size(%d), numFramesPlayed(%d), mNumFramesWritten(%d)", 
			numFramesAvailableToWrite, mAudioSink->frameCount(), mAudioQueue.size(), numFramesPlayed, mNumFramesWritten);
#endif
#if 0
    if (numFramesAvailableToWrite == mAudioSink->frameCount()) {
        ALOGI("audio sink underrun");
    } else {
        ALOGV("audio queue has %d frames left to play",
             mAudioSink->frameCount() - numFramesAvailableToWrite);
    }
#endif

    size_t numBytesAvailableToWrite =
        numFramesAvailableToWrite * mAudioSink->frameSize();

    while (numBytesAvailableToWrite > 0 && !mAudioQueue.empty()) {
        QueueEntry *entry = &*mAudioQueue.begin();

        if (entry->mBuffer == NULL) {
            // EOS

            notifyEOS(true /* audio */, entry->mFinalResult);

            mAudioQueue.erase(mAudioQueue.begin());
            entry = NULL;
#ifndef ANDROID_DEFAULT_CODE
			mNeedNewAudioAnchorTime	= false;
         // mtk80902: i cant find out who set the last damn timeUs to 0..
            mHasAudio = false;
            ALOGD("audio position EOS");
#endif
            return false;
        }

        if (entry->mOffset == 0) {
            int64_t mediaTimeUs;
            CHECK(entry->mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));

            ALOGV("rendering audio at media time %.2f secs", mediaTimeUs / 1E6);
#if DUMP_PROFILE
            dumpProfile("render", mediaTimeUs);
#endif

#ifndef ANDROID_DEFAULT_CODE
            // mtk80902: for ALPS00456468 & avoid ALPS00457437 CTS fail
            if (entry->mBuffer->size() != 0) {
                mNeedNewAudioAnchorTime = false;
                mAnchorTimeMediaUs = mediaTimeUs;
            }
#else
            mAnchorTimeMediaUs = mediaTimeUs;
#endif


            uint32_t numFramesPlayed;
          
#ifndef ANDROID_DEFAULT_CODE 
            // after flush several secs. numFramesPlayed is not zero ??
            extern AudioTrackCenter gAudioTrackCenter;
            int32_t trackId = 0;

            trackId = gAudioTrackCenter.getTrackId(mAudioSink.get());
            if (trackId) {
                int64_t framePlayed = 0;
                CHECK_EQ(gAudioTrackCenter.getRealTimePosition(trackId, &framePlayed), (status_t)OK);
                if (framePlayed > 0xffffffff) 
                    ALOGW("warning!!!, getRealTimePosition framePlayed = %lld", framePlayed);
                numFramesPlayed = (uint32_t)framePlayed;
            } else { 
                CHECK_EQ(mAudioSink->getPosition(&numFramesPlayed), (status_t)OK);
            }

            ALOGD("audio played time(%lldus), system time(%lldus)",  
                    (int64_t)(((int64_t)numFramesPlayed)* mAudioSink->msecsPerFrame()*1000ll), 
                    ALooper::GetNowUs());

            if (numFramesPlayed > mNumFramesWritten) {
                numFramesPlayed = mNumFramesWritten;
                ALOGW("numFramesPlayed(%dus) > mNumFramesWritten(%dus), reset numFramesPlayed",	
                        numFramesPlayed, mNumFramesWritten);
            }
#else
			CHECK_EQ(mAudioSink->getPosition(&numFramesPlayed), (status_t)OK);
#endif

            uint32_t numFramesPendingPlayout =
                mNumFramesWritten - numFramesPlayed;

#ifndef ANDROID_DEFAULT_CODE
            int64_t realTimeOffsetUs =
                   (numFramesPendingPlayout
                        * mAudioSink->msecsPerFrame()) * 1000ll;
#else
            int64_t realTimeOffsetUs =
                (mAudioSink->latency() / 2  /* XXX */
                    + numFramesPendingPlayout
                        * mAudioSink->msecsPerFrame()) * 1000ll;
#endif

            // ALOGI("realTimeOffsetUs = %lld us", realTimeOffsetUs);

#ifndef ANDROID_DEFAULT_CODE
            // mtk80902: for ALPS00456468 & avoid ALPS00457437 CTS fail
            if (entry->mBuffer->size() != 0)
#endif
                mAnchorTimeRealUs =
                    ALooper::GetNowUs() + realTimeOffsetUs;
        }

        size_t copy = entry->mBuffer->size() - entry->mOffset;
        if (copy > numBytesAvailableToWrite) {
            copy = numBytesAvailableToWrite;
        }
#ifndef ANDROID_DEFAULT_CODE
        if ((ssize_t)copy != mAudioSink->write(
                    entry->mBuffer->data() + entry->mOffset, copy)){
            ALOGE("NuPlayer::Renderer::onDrainAudioQueue audio sink write maybe fail");
            break;
        }

#else
        CHECK_EQ(mAudioSink->write(
                    entry->mBuffer->data() + entry->mOffset, copy),
                 (ssize_t)copy);
#endif
        entry->mOffset += copy;
        if (entry->mOffset == entry->mBuffer->size()) {
            entry->mNotifyConsumed->post();
            mAudioQueue.erase(mAudioQueue.begin());

            entry = NULL;
        }

        numBytesAvailableToWrite -= copy;
        size_t copiedFrames = copy / mAudioSink->frameSize();
        mNumFramesWritten += copiedFrames;

        notifyIfMediaRenderingStarted();
    }
#ifndef ANDROID_DEFAULT_CODE
    notifyPosition(true/*audio*/);
#else
    notifyPosition();
#endif

    return !mAudioQueue.empty();
}

void NuPlayer::Renderer::postDrainVideoQueue() {
    if (mDrainVideoQueuePending || mSyncQueues || mPaused) {
        return;
    }

    if (mVideoQueue.empty()) {
        return;
    }

    QueueEntry &entry = *mVideoQueue.begin();

    sp<AMessage> msg = new AMessage(kWhatDrainVideoQueue, id());
    msg->setInt32("generation", mVideoQueueGeneration);

    int64_t delayUs;

    if (entry.mBuffer == NULL) {
        // EOS doesn't carry a timestamp.
        delayUs = 0;
    } else if (mFlags & FLAG_REAL_TIME) {
        int64_t mediaTimeUs;
        CHECK(entry.mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));

        delayUs = mediaTimeUs - ALooper::GetNowUs();
    } else {
        int64_t mediaTimeUs;
        CHECK(entry.mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));

        if (mAnchorTimeMediaUs < 0) {
            delayUs = 0;

#ifndef ANDROID_DEFAULT_CODE
            int32_t flags;
            CHECK(entry.mBuffer->meta()->findInt32("flags", &flags));
            if (!mHasAudio && !(flags & 0x00000001/*OMX_BUFFERFLAG_EOS*/)) {
                ALOGI("no audio, use video timestamp as anchor");
                mAnchorTimeMediaUs = mediaTimeUs;
                mAnchorTimeRealUs = ALooper::GetNowUs();
            }
#else
            if (!mHasAudio) {
                mAnchorTimeMediaUs = mediaTimeUs;
                mAnchorTimeRealUs = ALooper::GetNowUs();
            }
#endif
        } else {
            int64_t realTimeUs =
                (mediaTimeUs - mAnchorTimeMediaUs) + mAnchorTimeRealUs;

            delayUs = realTimeUs - ALooper::GetNowUs();
        }
    }
	
#ifndef ANDROID_DEFAULT_CODE
    delayUs -= MAX_VIDEO_EARLY_POST_US;
    if (entry.mBuffer != NULL) {
        int64_t mediaTimeUs;
        CHECK(entry.mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));
        ALOGD("Drain delay time(%lldus), video media time(%lldus), mAnchorTimeMediaUs(%lldus)", delayUs, mediaTimeUs, mAnchorTimeMediaUs);
    }
#endif

    msg->post(delayUs);

    mDrainVideoQueuePending = true;
}

void NuPlayer::Renderer::onDrainVideoQueue() {
    if (mVideoQueue.empty()) {
        return;
    }

    QueueEntry *entry = &*mVideoQueue.begin();

    if (entry->mBuffer == NULL) {
        // EOS

        notifyEOS(false /* audio */, entry->mFinalResult);

        mVideoQueue.erase(mVideoQueue.begin());
        entry = NULL;

        mVideoLateByUs = 0ll;

#ifndef ANDROID_DEFAULT_CODE
        notifyPosition(false/*video*/);
#else
        notifyPosition();
#endif
        ALOGD("video position EOS");
        return;
    }

    int64_t realTimeUs;
#ifndef ANDROID_DEFAULT_CODE
	// mtk80902: shame google..mediaTimeUs may be used below
    int64_t mediaTimeUs = 0;
#endif
    if (mFlags & FLAG_REAL_TIME) {
        CHECK(entry->mBuffer->meta()->findInt64("timeUs", &realTimeUs));
    } else {
#ifdef ANDROID_DEFAULT_CODE
        int64_t mediaTimeUs;
#endif        
        CHECK(entry->mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));

        realTimeUs = mediaTimeUs - mAnchorTimeMediaUs + mAnchorTimeRealUs;
    }

    mVideoLateByUs = ALooper::GetNowUs() - realTimeUs;
#ifndef ANDROID_DEFAULT_CODE
    bool tooLate = (mVideoLateByUs > 250000);
#else
    bool tooLate = (mVideoLateByUs > 40000);
#endif

    if (tooLate) {
        ALOGD("video (%.2f) late by %lld us (%.2f secs)",
            realTimeUs / 1E6, mVideoLateByUs, mVideoLateByUs / 1E6);
    } else {
        ALOGV("rendering video at media time %.2f secs", mediaTimeUs / 1E6);
    }

#ifndef ANDROID_DEFAULT_CODE
    // if preformance not ok, show one ,then drop one
    static int32_t SinceLastDropped = 0;
    if(tooLate)
    {
        if (SinceLastDropped > 0)
        {
            //drop
            ALOGE("we're late dropping one after %d frames",SinceLastDropped);
            SinceLastDropped = 0;
        }else{
            //not drop
            tooLate = false;
            SinceLastDropped ++;
        }
    }else{
        SinceLastDropped ++;
    }

    entry->mNotifyConsumed->setInt64("realtimeus", realTimeUs);
    entry->mNotifyConsumed->setInt64("delaytimeus", -mVideoLateByUs);
    ALOGD("ACodec delay time(%lldus), video media time(%lldus), mAnchorTimeMediaUs(%lldus)", -mVideoLateByUs, mediaTimeUs, mAnchorTimeMediaUs);
#endif
    entry->mNotifyConsumed->setInt32("render", !tooLate);
    entry->mNotifyConsumed->post();
    
#if !defined(ANDROID_DEFAULT_CODE) && defined(MTK_CLEARMOTION_SUPPORT)  
    int32_t isInterpolateFrame = 0;
    if(!entry->mBuffer->meta()->findInt32("interpolateframe", &isInterpolateFrame)) {
        ALOGV("no key:interpolateframe in meta");
        isInterpolateFrame = 0;
    }

    if(mPausing) {
        ALOGD("rendering frame when pausing, tooLate(%s), InterpolateFrame(%s)", 
                tooLate?"true":"false", isInterpolateFrame?"true":"false");
    }

    if( !tooLate ) {
        if( isInterpolateFrame )
            mMJCPauseDelay = true;
        else {
            mMJCPauseDelay = false;
            if(mPausing) {
                mPausing = false;
                onPause();
                ALOGI("paused after rendering an uninterpolated frame");
            }
        }
    }
#endif	   
    mVideoQueue.erase(mVideoQueue.begin());
    entry = NULL;

    if (!mVideoRenderingStarted) {
        mVideoRenderingStarted = true;
        notifyVideoRenderingStart();
    }
 
    notifyIfMediaRenderingStarted();
#ifndef ANDROID_DEFAULT_CODE
    notifyPosition(false/*video*/);
#else
    notifyPosition();
#endif
}

void NuPlayer::Renderer::notifyVideoRenderingStart() {
    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", kWhatVideoRenderingStart);
    notify->post();
}

void NuPlayer::Renderer::notifyEOS(bool audio, status_t finalResult) {
    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", kWhatEOS);
    notify->setInt32("audio", static_cast<int32_t>(audio));
    notify->setInt32("finalResult", finalResult);
    notify->post();
}

void NuPlayer::Renderer::onQueueBuffer(const sp<AMessage> &msg) {
    int32_t audio;
    CHECK(msg->findInt32("audio", &audio));

    if (audio) {
        mHasAudio = true;
    } else {
        mHasVideo = true;
    }

    if (dropBufferWhileFlushing(audio, msg)) {
        return;
    }

    sp<ABuffer> buffer;
    CHECK(msg->findBuffer("buffer", &buffer));

    sp<AMessage> notifyConsumed;
    CHECK(msg->findMessage("notifyConsumed", &notifyConsumed));

    QueueEntry entry;
    entry.mBuffer = buffer;
    entry.mNotifyConsumed = notifyConsumed;
    entry.mOffset = 0;
    entry.mFinalResult = OK;

    if (audio) {
        mAudioQueue.push_back(entry);
        postDrainAudioQueue();
    } else {
        mVideoQueue.push_back(entry);
        postDrainVideoQueue();
    }

    if (!mSyncQueues || mAudioQueue.empty() || mVideoQueue.empty()) {
        return;
    }

    sp<ABuffer> firstAudioBuffer = (*mAudioQueue.begin()).mBuffer;
    sp<ABuffer> firstVideoBuffer = (*mVideoQueue.begin()).mBuffer;

    if (firstAudioBuffer == NULL || firstVideoBuffer == NULL) {
        // EOS signalled on either queue.
        syncQueuesDone();
        return;
    }

    int64_t firstAudioTimeUs;
    int64_t firstVideoTimeUs;
    CHECK(firstAudioBuffer->meta()
            ->findInt64("timeUs", &firstAudioTimeUs));
    CHECK(firstVideoBuffer->meta()
            ->findInt64("timeUs", &firstVideoTimeUs));

    int64_t diff = firstVideoTimeUs - firstAudioTimeUs;

    ALOGV("queueDiff = %.2f secs", diff / 1E6);

    if (diff > 100000ll) {
        // Audio data starts More than 0.1 secs before video.
        // Drop some audio.

        (*mAudioQueue.begin()).mNotifyConsumed->post();
        mAudioQueue.erase(mAudioQueue.begin());
        return;
    }
#ifndef ANDROID_DEFAULT_CODE
    if(diff <  -100000ll) {
        // video data starts More than 0.1 secs before audio.
        // Drop some video.
        ALOGE("before playback, video is early than audio drop diff = %.2f", diff / 1E6);
        (*mVideoQueue.begin()).mNotifyConsumed->post();
        mVideoQueue.erase(mVideoQueue.begin());
        return;
    }
#endif

    syncQueuesDone();
}

void NuPlayer::Renderer::syncQueuesDone() {
    if (!mSyncQueues) {
        return;
    }

    mSyncQueues = false;

    if (!mAudioQueue.empty()) {
        postDrainAudioQueue();
    }

    if (!mVideoQueue.empty()) {
        postDrainVideoQueue();
    }
}

void NuPlayer::Renderer::onQueueEOS(const sp<AMessage> &msg) {
    int32_t audio;
    CHECK(msg->findInt32("audio", &audio));

    if (dropBufferWhileFlushing(audio, msg)) {
        return;
    }

    int32_t finalResult;
    CHECK(msg->findInt32("finalResult", &finalResult));

    QueueEntry entry;
    entry.mOffset = 0;
    entry.mFinalResult = finalResult;

    if (audio) {
        if (mAudioQueue.empty() && mSyncQueues) {
            syncQueuesDone();
        }
        mAudioQueue.push_back(entry);
        postDrainAudioQueue();
    } else {
        if (mVideoQueue.empty() && mSyncQueues) {
            syncQueuesDone();
        }
        mVideoQueue.push_back(entry);
        postDrainVideoQueue();
    }
}

void NuPlayer::Renderer::onFlush(const sp<AMessage> &msg) {
    int32_t audio;
    CHECK(msg->findInt32("audio", &audio));

    // If we're currently syncing the queues, i.e. dropping audio while
    // aligning the first audio/video buffer times and only one of the
    // two queues has data, we may starve that queue by not requesting
    // more buffers from the decoder. If the other source then encounters
    // a discontinuity that leads to flushing, we'll never find the
    // corresponding discontinuity on the other queue.
    // Therefore we'll stop syncing the queues if at least one of them
    // is flushed.
    syncQueuesDone();

    ALOGV("flushing %s", audio ? "audio" : "video");
    if (audio) {
#ifndef ANDROID_DEFAULT_CODE
        //@debug
        dumpQueue(&mAudioQueue, audio);
        mHasAudio = false;
#endif
        flushQueue(&mAudioQueue);
#ifndef ANDROID_DEFAULT_CODE
        mAudioSink->pause();
        
        mAudioSink->flush();
	 	mNumFramesWritten = 0;
	 	mAudioSink->start();
#endif
        Mutex::Autolock autoLock(mFlushLock);
        mFlushingAudio = false;

        mDrainAudioQueuePending = false;
        ++mAudioQueueGeneration;

        prepareForMediaRenderingStart();
    } else {
#ifndef ANDROID_DEFAULT_CODE
        //@debug
        dumpQueue(&mVideoQueue, audio);
        mHasVideo = false;
#endif
        flushQueue(&mVideoQueue);

        Mutex::Autolock autoLock(mFlushLock);
        mFlushingVideo = false;

        mDrainVideoQueuePending = false;
        ++mVideoQueueGeneration;

        prepareForMediaRenderingStart();
    }

    notifyFlushComplete(audio);
}
#ifndef ANDROID_DEFAULT_CODE
void NuPlayer::Renderer::dumpQueue(List<QueueEntry> *queue, bool audio) {
    List<QueueEntry>::iterator it = queue->begin();
    ALOGD("dumping current %s queue(%d fs)", audio ? "audio" : "video", queue->size());
    while (it != queue->end()) {
        QueueEntry *entry = &*it;
        if (entry->mBuffer != NULL) {
            int64_t mediaTimeUs = 0;
            CHECK(entry->mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));
            ALOGD("\t\t (%.2f secs)",  mediaTimeUs / 1E6);
        } else {
            ALOGD("\t\t (null)");
        }
        it++;
    }

}

void NuPlayer::Renderer::dumpProfile(const char* tag, int64_t timeUs) {
    ALOGD("[dump] %s %s %.2f", "audio", tag, timeUs / 1E6);
}

void NuPlayer::Renderer::dumpBuffer(const char* fileName, char* p, size_t size) {
    FILE *fp;
    fp = fopen(fileName, "a+");
    if (fp == NULL) {
        ALOGE("error when create dump file %s", fileName);
        return;
    }
    fwrite(p, sizeof(char), size, fp);
    fclose(fp);

}
#endif

void NuPlayer::Renderer::flushQueue(List<QueueEntry> *queue) {
    while (!queue->empty()) {
        QueueEntry *entry = &*queue->begin();

        if (entry->mBuffer != NULL) {
            entry->mNotifyConsumed->post();
        }

        queue->erase(queue->begin());
        entry = NULL;
    }
}

void NuPlayer::Renderer::notifyFlushComplete(bool audio) {
    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", kWhatFlushComplete);
    notify->setInt32("audio", static_cast<int32_t>(audio));
    notify->post();
}

bool NuPlayer::Renderer::dropBufferWhileFlushing(
        bool audio, const sp<AMessage> &msg) {
    bool flushing = false;

    {
        Mutex::Autolock autoLock(mFlushLock);
        if (audio) {
            flushing = mFlushingAudio;
        } else {
            flushing = mFlushingVideo;
        }
    }

    if (!flushing) {
        return false;
    }

    sp<AMessage> notifyConsumed;
    if (msg->findMessage("notifyConsumed", &notifyConsumed)) {
        notifyConsumed->post();
    }

    return true;
}

void NuPlayer::Renderer::onAudioSinkChanged() {
    CHECK(!mDrainAudioQueuePending);
    mNumFramesWritten = 0;
    uint32_t written;
    if (mAudioSink->getFramesWritten(&written) == OK) {
        mNumFramesWritten = written;
    }
}

#ifndef ANDROID_DEFAULT_CODE
void NuPlayer::Renderer::notifyPosition(bool audio) {
#else
void NuPlayer::Renderer::notifyPosition() {
#endif
    if (mAnchorTimeRealUs < 0 || mAnchorTimeMediaUs < 0) {
        return;
    }
	
#ifndef ANDROID_DEFAULT_CODE
    // only use audio position when there is audio
    if(mHasAudio && !audio || !mHasAudio && audio) {
        return;
    }

    if (mNeedNewAudioAnchorTime && mHasAudio) {
        ALOGW("need new audio anchor time for position");
        return;
    }
#endif

    int64_t nowUs = ALooper::GetNowUs();

    if (mLastPositionUpdateUs >= 0
            && nowUs < mLastPositionUpdateUs + kMinPositionUpdateDelayUs) {
        return;
    }
    mLastPositionUpdateUs = nowUs;

    int64_t positionUs = (nowUs - mAnchorTimeRealUs) + mAnchorTimeMediaUs;

    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", kWhatPosition);
    notify->setInt64("positionUs", positionUs);
    notify->setInt64("videoLateByUs", mVideoLateByUs);
    notify->post();
}

void NuPlayer::Renderer::onPause() {
#ifndef ANDROID_DEFAULT_CODE
    if (mPaused) {
        ALOGW("NuPlayer::Renderer::onPause already paused");
        return;
    }
#else
    CHECK(!mPaused);
#endif
    mDrainAudioQueuePending = false;
    ++mAudioQueueGeneration;

    mDrainVideoQueuePending = false;
    ++mVideoQueueGeneration;

    prepareForMediaRenderingStart();

    if (mHasAudio) {
        mAudioSink->pause();
    }

    ALOGV("now paused audio queue has %d entries, video has %d entries",
          mAudioQueue.size(), mVideoQueue.size());

    mPaused = true;
    
}

void NuPlayer::Renderer::onResume() {
#if !defined(ANDROID_DEFAULT_CODE) && defined(MTK_CLEARMOTION_SUPPORT)  
    if( mPausing) {
        mPausing = false;
        return;
    }
#endif
	
    if (!mPaused) {
        return;
    }

    if (mHasAudio) {
        mAudioSink->start();
    }

    mPaused = false;
#ifndef ANDROID_DEFAULT_CODE
    // mtk80902: ALPS00445484 - this should be renderer's bug
    // pure video's timestamp should be re-anchored after
    // renderer pause/play
    if (!mHasAudio) {
        mAnchorTimeMediaUs = -1;
        mAnchorTimeRealUs = -1;
    }

    if (mHasAudio) {
        mNeedNewAudioAnchorTime = true;
    }
#endif

    if (!mAudioQueue.empty()) {
        postDrainAudioQueue();
    }

    if (!mVideoQueue.empty()) {
        postDrainVideoQueue();
    }
}

}  // namespace android

