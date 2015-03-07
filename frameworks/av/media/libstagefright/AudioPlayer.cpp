/*
 * Copyright (C) 2009 The Android Open Source Project
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
#define LOG_TAG "AudioPlayer"
#ifndef ANDROID_DEFAULT_CODE
// for INT64_MAX
#undef __STRICT_ANSI__
#define __STDINT_LIMITS
#define __STDC_LIMIT_MACROS
#include <stdint.h>
#include <cutils/xlog.h>
#include <media/AudioSystem.h>
#endif

#define ATRACE_TAG ATRACE_TAG_VIDEO
#include <utils/Log.h>
#include <cutils/compiler.h>
#include <utils/Trace.h>

#include <binder/IPCThreadState.h>
#include <media/AudioTrack.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/ALooper.h>
#include <media/stagefright/AudioPlayer.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>

#include "include/AwesomePlayer.h"


#ifndef ANDROID_DEFAULT_CODE
#include <media/AudioTrackCenter.h>
#endif

namespace android {

#ifndef ANDROID_DEFAULT_CODE
extern AudioTrackCenter gAudioTrackCenter;
#endif


#ifdef MTK_AUDIO_DDPLUS_SUPPORT
struct AudioPlayerEvent : public TimedEventQueue::Event {
    AudioPlayerEvent(
            AudioPlayer *player,
            void (AudioPlayer::*method)())
        : mAudioPlayer(player),
          mMethod(method) {
    }

protected:
    virtual ~AudioPlayerEvent() {}
    virtual void fire(TimedEventQueue *queue, int64_t /* now_us */) {
        (mAudioPlayer->*mMethod)();
    }

private:
    AudioPlayer *mAudioPlayer;
    void (AudioPlayer::*mMethod)();
    AudioPlayerEvent(const AudioPlayerEvent &);
    AudioPlayerEvent &operator=(const AudioPlayerEvent &);
};
#endif
AudioPlayer::AudioPlayer(
        const sp<MediaPlayerBase::AudioSink> &audioSink,
        uint32_t flags,
        AwesomePlayer *observer)
    : mInputBuffer(NULL),
      mSampleRate(0),
      mLatencyUs(0),
      mFrameSize(0),
      mNumFramesPlayed(0),
      mNumFramesPlayedSysTimeUs(ALooper::GetNowUs()),
      mPositionTimeMediaUs(-1),
      mPositionTimeRealUs(-1),
      mSeeking(false),
      mReachedEOS(false),
      mFinalStatus(OK),
#ifdef ANDROID_DEFAULT_CODE
      mSeekTimeUs(0),
#else
      mSeekTimeUs(-1),
#endif
      mStarted(false),
      mIsFirstBuffer(false),
      mFirstBufferResult(OK),
      mFirstBuffer(NULL),
      mAudioSink(audioSink),
      mObserver(observer),
      mPinnedTimeUs(-1ll),
      mPlaying(false),
      mStartPosUs(0),
#ifdef ANDROID_DEFAULT_CODE
      mCreateFlags(flags) {
#else
      mCreateFlags(flags),
      mLastBufferTimeUs(0),
      mLastBufferSize(0),
      mAVSyncLastRealTime(-1ll),
      mIsVorbisORApe(false),
      mPadEnable(false) {
#endif
#ifdef MTK_AUDIO_DDPLUS_SUPPORT
    mPortSettingsChangedEvent = new AudioPlayerEvent(this, &AudioPlayer::onPortSettingsChangedEvent);
    mPortSettingsChangedEventPending = false;
    mQueueStarted = false;
#endif
}

AudioPlayer::~AudioPlayer() {
    if (mStarted) {
        reset();
    }
#ifdef MTK_AUDIO_DDPLUS_SUPPORT
    if (mQueueStarted) {
        mQueue.stop();
    }
#endif
}

void AudioPlayer::setSource(const sp<MediaSource> &source) {
    CHECK(mSource == NULL);
    mSource = source;
}

status_t AudioPlayer::start(bool sourceAlreadyStarted) {
    CHECK(!mStarted);
    CHECK(mSource != NULL);

    status_t err;
    if (!sourceAlreadyStarted) {
        err = mSource->start();

        if (err != OK) {
            return err;
        }
    }

#ifdef MTK_AUDIO_DDPLUS_SUPPORT
    if (!mQueueStarted) {
        mQueue.start();
        mQueueStarted = true;
    }
#endif

    // We allow an optional INFO_FORMAT_CHANGED at the very beginning
    // of playback, if there is one, getFormat below will retrieve the
    // updated format, if there isn't, we'll stash away the valid buffer
    // of data to be used on the first audio callback.

    CHECK(mFirstBuffer == NULL);

#ifndef ANDROID_DEFAULT_CODE
    bool wasSeeking = false;
#endif
    MediaSource::ReadOptions options;
    if (mSeeking) {
        options.setSeekTo(mSeekTimeUs);
        mSeeking = false;
#ifndef ANDROID_DEFAULT_CODE
        wasSeeking = true;
#endif
    }

    mFirstBufferResult = mSource->read(&mFirstBuffer, &options);
    if (mFirstBufferResult == INFO_FORMAT_CHANGED) {
        ALOGV("INFO_FORMAT_CHANGED!!!");

        CHECK(mFirstBuffer == NULL);
        mFirstBufferResult = OK;
        mIsFirstBuffer = false;
    } else {
        mIsFirstBuffer = true;
    }

    sp<MetaData> format = mSource->getFormat();
    const char *mime;
    bool success = format->findCString(kKeyMIMEType, &mime);
    CHECK(success);
    CHECK(useOffload() || !strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_RAW));

    success = format->findInt32(kKeySampleRate, &mSampleRate);
    CHECK(success);

    int32_t numChannels, channelMask;
    success = format->findInt32(kKeyChannelCount, &numChannels);
    CHECK(success);

    if(!format->findInt32(kKeyChannelMask, &channelMask)) {
        // log only when there's a risk of ambiguity of channel mask selection
        ALOGI_IF(numChannels > 2,
                "source format didn't specify channel mask, using (%d) channel order", numChannels);
        channelMask = CHANNEL_MASK_USE_CHANNEL_ORDER;
    }

    audio_format_t audioFormat = AUDIO_FORMAT_PCM_16_BIT;

#ifndef ANDROID_DEFAULT_CODE         // get bitWidth from metadata
    const char *audiomime;
#ifdef MTK_AUDIO_APE_SUPPORT
    mIsVorbisORApe = (format->findCString(kKeyApeFlag, &audiomime) && !strcasecmp(audiomime, MEDIA_MIMETYPE_AUDIO_APE)) || (format->findCString(kKeyVorbisFlag, &audiomime) && !strcasecmp(audiomime, MEDIA_MIMETYPE_AUDIO_VORBIS));
#else
    mIsVorbisORApe = format->findCString(kKeyVorbisFlag, &audiomime) && !strcasecmp(audiomime, MEDIA_MIMETYPE_AUDIO_VORBIS);
#endif
#ifdef MTK_HIGH_RESOLUTION_AUDIO_SUPPORT
    int32_t bitWidth = 0;
    if (format->findInt32(kKeyBitWidth, &bitWidth) && bitWidth == 24) {
        ALOGI("audio player use 24 bit");
	audioFormat = AUDIO_FORMAT_PCM_8_24_BIT;
    }
    else {
        audioFormat = AUDIO_FORMAT_PCM_16_BIT;
    }
#endif
#endif
    if (useOffload()) {
        if (mapMimeToAudioFormat(audioFormat, mime) != OK) {
            ALOGE("Couldn't map mime type \"%s\" to a valid AudioSystem::audio_format", mime);
            audioFormat = AUDIO_FORMAT_INVALID;
        } else {
            ALOGV("Mime type \"%s\" mapped to audio_format 0x%x", mime, audioFormat);
        }
    }

    int avgBitRate = -1;
    format->findInt32(kKeyBitRate, &avgBitRate);

    if (mAudioSink.get() != NULL) {

        uint32_t flags = AUDIO_OUTPUT_FLAG_NONE;
        audio_offload_info_t offloadInfo = AUDIO_INFO_INITIALIZER;

        if (allowDeepBuffering()) {
            flags |= AUDIO_OUTPUT_FLAG_DEEP_BUFFER;
        }
        if (useOffload()) {
            flags |= AUDIO_OUTPUT_FLAG_COMPRESS_OFFLOAD;

            int64_t durationUs;
            if (format->findInt64(kKeyDuration, &durationUs)) {
                offloadInfo.duration_us = durationUs;
            } else {
                offloadInfo.duration_us = -1;
            }

            offloadInfo.sample_rate = mSampleRate;
            offloadInfo.channel_mask = channelMask;
            offloadInfo.format = audioFormat;
            offloadInfo.stream_type = AUDIO_STREAM_MUSIC;
            offloadInfo.bit_rate = avgBitRate;
            offloadInfo.has_video = ((mCreateFlags & HAS_VIDEO) != 0);
            offloadInfo.is_streaming = ((mCreateFlags & IS_STREAMING) != 0);
        }

        status_t err = mAudioSink->open(
                mSampleRate, numChannels, channelMask, audioFormat,
                DEFAULT_AUDIOSINK_BUFFERCOUNT,
                &AudioPlayer::AudioSinkCallback,
                this,
                (audio_output_flags_t)flags,
                useOffload() ? &offloadInfo : NULL);

        if (err == OK) {
            mLatencyUs = (int64_t)mAudioSink->latency() * 1000;
            mFrameSize = mAudioSink->frameSize();

            if (useOffload()) {
                // If the playback is offloaded to h/w we pass the
                // HAL some metadata information
                // We don't want to do this for PCM because it will be going
                // through the AudioFlinger mixer before reaching the hardware
                sendMetaDataToHal(mAudioSink, format);
            }

            err = mAudioSink->start();
            // do not alter behavior for non offloaded tracks: ignore start status.
            if (!useOffload()) {
                err = OK;
            }
        }

        if (err != OK) {
            if (mFirstBuffer != NULL) {
                mFirstBuffer->release();
                mFirstBuffer = NULL;
            }

            if (!sourceAlreadyStarted) {
                mSource->stop();
            }

            return err;
        }

    } else {
        // playing to an AudioTrack, set up mask if necessary
        audio_channel_mask_t audioMask = channelMask == CHANNEL_MASK_USE_CHANNEL_ORDER ?
                audio_channel_out_mask_from_count(numChannels) : channelMask;
        if (0 == audioMask) {
            return BAD_VALUE;
        }

#if !defined(ANDROID_DEFAULT_CODE) && defined(MTK_HIGH_RESOLUTION_AUDIO_SUPPORT)    // for 24 bit audio
        mAudioTrack = new AudioTrack(
                AUDIO_STREAM_MUSIC, mSampleRate, audioFormat, audioMask,
                0, AUDIO_OUTPUT_FLAG_NONE, &AudioCallback, this, 0);
#else
        mAudioTrack = new AudioTrack(
                AUDIO_STREAM_MUSIC, mSampleRate, AUDIO_FORMAT_PCM_16_BIT, audioMask,
                0, AUDIO_OUTPUT_FLAG_NONE, &AudioCallback, this, 0);
#endif

        if ((err = mAudioTrack->initCheck()) != OK) {
            mAudioTrack.clear();

            if (mFirstBuffer != NULL) {
                mFirstBuffer->release();
                mFirstBuffer = NULL;
            }

            if (!sourceAlreadyStarted) {
                mSource->stop();
            }

            return err;
        }

        mLatencyUs = (int64_t)mAudioTrack->latency() * 1000;
        mFrameSize = mAudioTrack->frameSize();

        mAudioTrack->start();
    }

    mStarted = true;
    mPlaying = true;
    mPinnedTimeUs = -1ll;

#ifndef ANDROID_DEFAULT_CODE
      if (wasSeeking) {
          SXLOGI("start with seeking");
          mPositionTimeRealUs = 0;
          mPositionTimeMediaUs = mSeekTimeUs;
      }
#endif
    return OK;
}

void AudioPlayer::pause(bool playPendingSamples) {
    CHECK(mStarted);

    if (playPendingSamples) {
        if (mAudioSink.get() != NULL) {
            mAudioSink->stop();
        } else {
            mAudioTrack->stop();
        }

        mNumFramesPlayed = 0;
        mNumFramesPlayedSysTimeUs = ALooper::GetNowUs();
#ifndef ANDROID_DEFAULT_CODE
        mAVSyncLastRealTime = -1ll;
#endif
    } else {
        if (mAudioSink.get() != NULL) {
            mAudioSink->pause();
        } else {
            mAudioTrack->pause();
        }

        mPinnedTimeUs = ALooper::GetNowUs();
    }

    mPlaying = false;
}

status_t AudioPlayer::resume() {
    CHECK(mStarted);
    status_t err;

    if (mAudioSink.get() != NULL) {
        err = mAudioSink->start();
    } else {
        err = mAudioTrack->start();
    }

    if (err == OK) {
        mPlaying = true;
    }

    return err;
}

void AudioPlayer::reset() {
    CHECK(mStarted);

    ALOGV("reset: mPlaying=%d mReachedEOS=%d useOffload=%d",
                                mPlaying, mReachedEOS, useOffload() );

    if (mAudioSink.get() != NULL) {
        mAudioSink->stop();
        // If we're closing and have reached EOS, we don't want to flush
        // the track because if it is offloaded there could be a small
        // amount of residual data in the hardware buffer which we must
        // play to give gapless playback.
        // But if we're resetting when paused or before we've reached EOS
        // we can't be doing a gapless playback and there could be a large
        // amount of data queued in the hardware if the track is offloaded,
        // so we must flush to prevent a track switch being delayed playing
        // the buffered data that we don't want now
        if (!mPlaying || !mReachedEOS) {
            mAudioSink->flush();
        }

        mAudioSink->close();
    } else {
        mAudioTrack->stop();

        if (!mPlaying || !mReachedEOS) {
            mAudioTrack->flush();
        }

        mAudioTrack.clear();
    }

    // Make sure to release any buffer we hold onto so that the
    // source is able to stop().

    if (mFirstBuffer != NULL) {
        mFirstBuffer->release();
        mFirstBuffer = NULL;
    }

    if (mInputBuffer != NULL) {
        ALOGV("AudioPlayer releasing input buffer.");

        mInputBuffer->release();
        mInputBuffer = NULL;
    }

    mSource->stop();

    // The following hack is necessary to ensure that the OMX
    // component is completely released by the time we may try
    // to instantiate it again.
    // When offloading, the OMX component is not used so this hack
    // is not needed
    if (!useOffload()) {
        wp<MediaSource> tmp = mSource;
        mSource.clear();
        while (tmp.promote() != NULL) {
            usleep(1000);
        }
    } else {
        mSource.clear();
    }
    IPCThreadState::self()->flushCommands();

    mNumFramesPlayed = 0;
    mNumFramesPlayedSysTimeUs = ALooper::GetNowUs();
    mPositionTimeMediaUs = -1;
    mPositionTimeRealUs = -1;
#ifndef ANDROID_DEFAULT_CODE
    mAVSyncLastRealTime = -1ll;
#endif
    mSeeking = false;
    mSeekTimeUs = 0;
    mReachedEOS = false;
    mFinalStatus = OK;
    mStarted = false;
    mPlaying = false;
    mStartPosUs = 0;
}

// static
void AudioPlayer::AudioCallback(int event, void *user, void *info) {
    static_cast<AudioPlayer *>(user)->AudioCallback(event, info);
}

bool AudioPlayer::isSeeking() {
    Mutex::Autolock autoLock(mLock);
    return mSeeking;
}

bool AudioPlayer::reachedEOS(status_t *finalStatus) {
    *finalStatus = OK;

    Mutex::Autolock autoLock(mLock);
    *finalStatus = mFinalStatus;
    return mReachedEOS;
}

void AudioPlayer::notifyAudioEOS() {
    ALOGV("AudioPlayer@0x%p notifyAudioEOS", this);

    if (mObserver != NULL) {
        mObserver->postAudioEOS(0);
        ALOGV("Notified observer of EOS!");
    }
}

status_t AudioPlayer::setPlaybackRatePermille(int32_t ratePermille) {
    if (mAudioSink.get() != NULL) {
        return mAudioSink->setPlaybackRatePermille(ratePermille);
    } else if (mAudioTrack != 0){
        return mAudioTrack->setSampleRate(ratePermille * mSampleRate / 1000);
    } else {
        return NO_INIT;
    }
}

// static
size_t AudioPlayer::AudioSinkCallback(
        MediaPlayerBase::AudioSink *audioSink,
        void *buffer, size_t size, void *cookie,
        MediaPlayerBase::AudioSink::cb_event_t event) {
    AudioPlayer *me = (AudioPlayer *)cookie;

#ifdef  MTB_SUPPORT
    ATRACE_ONESHOT(ATRACE_ONESHOT_SPECIAL,"ASB,buffer: %p, size: %d" , buffer, size);      
#endif	
    switch(event) {
    case MediaPlayerBase::AudioSink::CB_EVENT_FILL_BUFFER:
        return me->fillBuffer(buffer, size);

    case MediaPlayerBase::AudioSink::CB_EVENT_STREAM_END:
        ALOGV("AudioSinkCallback: stream end");
        me->mReachedEOS = true;
        me->notifyAudioEOS();
        break;

    case MediaPlayerBase::AudioSink::CB_EVENT_TEAR_DOWN:
        ALOGV("AudioSinkCallback: Tear down event");
        me->mObserver->postAudioTearDown();
        break;
    }

    return 0;
}

void AudioPlayer::AudioCallback(int event, void *info) {
    switch (event) {
    case AudioTrack::EVENT_MORE_DATA:
        {
        AudioTrack::Buffer *buffer = (AudioTrack::Buffer *)info;
        size_t numBytesWritten = fillBuffer(buffer->raw, buffer->size);
        buffer->size = numBytesWritten;
        }
        break;

    case AudioTrack::EVENT_STREAM_END:
        mReachedEOS = true;
        notifyAudioEOS();
        break;
    }
}

#ifdef MTK_AUDIO_DDPLUS_SUPPORT
void AudioPlayer::onPortSettingsChangedEvent() {
    ALOGV("Port Settings Changed!");

    status_t err = OK;
	bool success;
	sp<MetaData> format;
    Mutex::Autolock autoLock(mLock);

    if (!mPortSettingsChangedEventPending) {
        goto onPortSettingsChangedEvent_exit;
    }

    // close exisiting playback
    if (mAudioSink.get() != NULL) {
        mAudioSink->stop();
        mAudioSink->close();
    } else {
        mAudioTrack->stop();
        mAudioTrack.clear();
        mAudioTrack = NULL;
    }

    // open new
    format = mSource->getFormat();
    const char *mime;
    success = format->findCString(kKeyMIMEType, &mime);
    CHECK(success);
    CHECK(!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_RAW));

    success = format->findInt32(kKeySampleRate, &mSampleRate);
    CHECK(success);

    int32_t numChannels, channelMask;
    success = format->findInt32(kKeyChannelCount, &numChannels);
    CHECK(success);

    if(!format->findInt32(kKeyChannelMask, &channelMask)) {
        // log only when there's a risk of ambiguity of channel mask selection
        ALOGI_IF(numChannels > 2,
                "source format didn't specify channel mask, using (%d) channel order", numChannels);
        channelMask = CHANNEL_MASK_USE_CHANNEL_ORDER;
    }

    ALOGV("AudioPlayer::New sample rate %d channels %d channelMask %d", mSampleRate, numChannels, channelMask);

    err = reOpenSink(numChannels, channelMask);
    if (err == OK)
    {
        if (mAudioSink.get() != NULL)
        {
            mAudioSink->start();
        }
        else
        {
            mAudioTrack->start();
        }
    }

onPortSettingsChangedEvent_exit:

    mPortSettingsChangedEventPending = false;

    if (err != OK) {
        if (mObserver && !mReachedEOS) {
            mObserver->postAudioEOS();
        }

        mReachedEOS = true;
        mFinalStatus = err;
    }
}

status_t AudioPlayer::reOpenSink(int numChannels, int channelMask)
{
    status_t err;

    sp<MetaData> format;
    format = mSource->getFormat();
    audio_format_t audioFormat = AUDIO_FORMAT_PCM_16_BIT;
#ifdef MTK_HIGH_RESOLUTION_AUDIO_SUPPORT
    int32_t bitWidth = 0;
    if (format->findInt32(kKeyBitWidth, &bitWidth) && bitWidth == 24) {
	ALOGI("audio player use 24 bit");
	audioFormat = AUDIO_FORMAT_PCM_8_24_BIT;
    }
    else {
	audioFormat = AUDIO_FORMAT_PCM_16_BIT;
    }
#endif


    const char *mime;
    bool success = format->findCString(kKeyMIMEType, &mime);
    if (useOffload()) {
	if (mapMimeToAudioFormat(audioFormat, mime) != OK) {
	    ALOGE("Couldn't map mime type \"%s\" to a valid AudioSystem::audio_format", mime);
	    audioFormat = AUDIO_FORMAT_INVALID;
	} else {
	    ALOGV("Mime type \"%s\" mapped to audio_format 0x%x", mime, audioFormat);
	}
    }

    int avgBitRate = -1;
    format->findInt32(kKeyBitRate, &avgBitRate);
    if (mAudioSink.get() != NULL) {
	uint32_t flags = AUDIO_OUTPUT_FLAG_NONE;
	audio_offload_info_t offloadInfo = AUDIO_INFO_INITIALIZER;

	if (allowDeepBuffering()) {
	    flags |= AUDIO_OUTPUT_FLAG_DEEP_BUFFER;
	}

	if (useOffload()) {
	    flags |= AUDIO_OUTPUT_FLAG_COMPRESS_OFFLOAD;

	    int64_t durationUs;
	    if (format->findInt64(kKeyDuration, &durationUs)) {
		offloadInfo.duration_us = durationUs;
	    } else {
		offloadInfo.duration_us = -1;
	    }

	    offloadInfo.sample_rate = mSampleRate;
	    offloadInfo.channel_mask = channelMask;
	    offloadInfo.format = audioFormat;
	    offloadInfo.stream_type = AUDIO_STREAM_MUSIC;
	    offloadInfo.bit_rate = avgBitRate;
	    offloadInfo.has_video = ((mCreateFlags & HAS_VIDEO) != 0);
	    offloadInfo.is_streaming = ((mCreateFlags & IS_STREAMING) != 0);
	}
	err = mAudioSink->open(
		mSampleRate, numChannels, channelMask, audioFormat,
		DEFAULT_AUDIOSINK_BUFFERCOUNT,
		&AudioPlayer::AudioSinkCallback,
		this,
		(audio_output_flags_t)flags,
		useOffload() ? &offloadInfo : NULL);
        if (err != OK) {
            ALOGE("mAudioSink->open error : %d", err);
            return err;
        }

        mLatencyUs = (int64_t)mAudioSink->latency() * 1000;
        mFrameSize = mAudioSink->frameSize();
    } else {
        int channels = 0;
        int outputflag = 0;
        // playing to an AudioTrack, set up mask if necessary
        audio_channel_mask_t audioMask = channelMask == CHANNEL_MASK_USE_CHANNEL_ORDER ?
                audio_channel_out_mask_from_count(numChannels) : channelMask;
        if (0 == audioMask) {
            return BAD_VALUE;
        }

#ifdef MTK_HIGH_RESOLUTION_AUDIO_SUPPORT    // for 24 bit audio
        mAudioTrack = new AudioTrack(
                AUDIO_STREAM_MUSIC, mSampleRate, audioFormat, audioMask,
                0, AUDIO_OUTPUT_FLAG_NONE, &AudioCallback, this, 0);
#else
        mAudioTrack = new AudioTrack(
                AUDIO_STREAM_MUSIC, mSampleRate, AUDIO_FORMAT_PCM_16_BIT, audioMask,
                0, AUDIO_OUTPUT_FLAG_NONE, &AudioCallback, this, 0);
#endif
        if ((err = mAudioTrack->initCheck()) != OK) {
            ALOGE("AudioTrack error : %d", err);
            mAudioTrack.clear();
            mAudioTrack = NULL;
            return err;
        }

        mLatencyUs = (int64_t)mAudioTrack->latency() * 1000;
        mFrameSize = mAudioTrack->frameSize();
    }
    return OK;
}
#endif

uint32_t AudioPlayer::getNumFramesPendingPlayout() const {
    uint32_t numFramesPlayedOut;
    status_t err;

    if (mAudioSink != NULL) {
        err = mAudioSink->getPosition(&numFramesPlayedOut);
    } else {
        err = mAudioTrack->getPosition(&numFramesPlayedOut);
    }

    if (err != OK || mNumFramesPlayed < numFramesPlayedOut) {
        return 0;
    }

    // mNumFramesPlayed is the number of frames submitted
    // to the audio sink for playback, but not all of them
    // may have played out by now.
    return mNumFramesPlayed - numFramesPlayedOut;
}

#ifndef ANDROID_DEFAULT_CODE //weiguo.li
uint32_t AudioPlayer::getNumFramesPlayout() const {

    uint32_t numFramesPlayedOut=0;
    status_t err;

    if (mAudioSink != NULL) {
    	err = mAudioSink->getPosition(&numFramesPlayedOut);
    } else {
    	err = mAudioTrack->getPosition(&numFramesPlayedOut);
    }

    if (err != OK ) {
    	return 0;
    }
    SXLOGV("getNumFramesPlayout:: numFramesPlayedOut =%u",numFramesPlayedOut);
    //add latency only if two frames have been played out.
    //this accurate time is for camera burst shot . now audiosink callback will send mute
    // data to audiotrack if no data is available before eos is send out. if extra latency is add
    // more mute data will be send to audiotrack. the interval of burstshot will has  more
    // mute data.
    int64_t playedUs =(1000000ll * numFramesPlayedOut) / mSampleRate;
    uint32_t afLatency = 0;
    AudioSystem::getOutputLatency(&afLatency);
    if(playedUs > (2* afLatency))
        return numFramesPlayedOut;
    return 0;
}
#endif
	

size_t AudioPlayer::fillBuffer(void *data, size_t size) {
    if (mNumFramesPlayed == 0) {
        ALOGV("AudioCallback");
    }

    if (mReachedEOS) {
        return 0;
    }
#ifndef ANDROID_DEFAULT_CODE
    bool needSkipFrames = false;
#endif
    bool postSeekComplete = false;
    bool postEOS = false;
    int64_t postEOSDelayUs = 0;

#ifdef MTK_AUDIO_DDPLUS_SUPPORT
    if (true == mPortSettingsChangedEventPending) {
        // Waiting for reconfig to finish... filling zeros
        memset(data, 0, size);
        return size;
    }
#endif

    size_t size_done = 0;
    size_t size_remaining = size;
    while (size_remaining > 0) {
        MediaSource::ReadOptions options;
        bool refreshSeekTime = false;

        {
            Mutex::Autolock autoLock(mLock);

            if (mSeeking) {
                if (mIsFirstBuffer) {
#ifndef ANDROID_DEFAULT_CODE
					// sam for first frame err--->
					if (mFirstBufferResult != OK) {					
						mPositionTimeMediaUs = mSeekTimeUs;
						mSeeking = false;
						if (mObserver) {
							postSeekComplete = true;
						}
						
						if (mObserver && !mReachedEOS) {
							postEOS = true;;
						}
						SXLOGW("AudioPlayer::fillBuffer--first frame error(when seek)!!");
						mReachedEOS = true;
						mIsFirstBuffer = false;
						mFinalStatus = mFirstBufferResult;
						break;
					}
					// <---sam for first frame err
#endif  //#ifndef ANDROID_DEFAULT_CODE					
                    if (mFirstBuffer != NULL) {
                        mFirstBuffer->release();
                        mFirstBuffer = NULL;
                    }
                    mIsFirstBuffer = false;
                }

#ifndef ANDROID_DEFAULT_CODE
				if (mPadEnable) {
					mLastBufferSize = 0;//hai
					mLastBufferTimeUs = mSeekTimeUs;
				}
#endif
                options.setSeekTo(mSeekTimeUs);
                refreshSeekTime = true;

                if (mInputBuffer != NULL) {
                    mInputBuffer->release();
                    mInputBuffer = NULL;
                }

#ifndef ANDROID_DEFAULT_CODE
				SXLOGD("Release the data when seek, size_done is %d", size_done);
				size_remaining += size_done;
				size_done = 0;
				memset((char *)data, 0, size_remaining);
#endif

                mSeeking = false;
                if (mObserver) {
                    postSeekComplete = true;
                }
#ifndef ANDROID_DEFAULT_CODE
                // don't do this for RTSP
                if (mSeekTimeUs != INT64_MAX)
                {   
                    if(mIsVorbisORApe)                     
                        needSkipFrames = true;
                    else
                        needSkipFrames = false;
                }
#endif
            }
        }

        if (mInputBuffer == NULL) {
            status_t err;

            if (mIsFirstBuffer) {
                mInputBuffer = mFirstBuffer;
                mFirstBuffer = NULL;
                err = mFirstBufferResult;

                mIsFirstBuffer = false;
            } else {
                err = mSource->read(&mInputBuffer, &options);
#ifndef ANDROID_DEFAULT_CODE  
                if(needSkipFrames) {              
                    Mutex::Autolock autoLock(mLock);
                    int64_t positionTimeMediaUS = mPositionTimeMediaUs;
                    struct timeval ts,te;
                    gettimeofday(&ts,NULL);
				       
                    do{
                        gettimeofday(&te,NULL);
                        if((te.tv_sec - ts.tv_sec) > 3) {
                            SXLOGD("accurate read costs much time");
					 	                needSkipFrames = false;
                            break;
                        }
					          CHECK((err == OK && mInputBuffer != NULL)|| (err != OK && mInputBuffer == NULL));
					          if(err != OK)
					 	           needSkipFrames = false;
					          else {
					 	           CHECK(mInputBuffer->meta_data()->findInt64(kKeyTime, &positionTimeMediaUS));
                       //for ape extra high compress type, frame is more than 180s..
						           if(((mSeekTimeUs - positionTimeMediaUS) > 100000) && ((mSeekTimeUs - positionTimeMediaUS) < 200000000)) {
						              mInputBuffer->release();
  					              mInputBuffer = NULL;
						              err = mSource->read(&mInputBuffer);
						           }
						           else
  						            needSkipFrames = false;
					           }
				            }while(needSkipFrames);
				      }
#endif
            }
#ifdef MTK_AUDIO_DDPLUS_SUPPORT
            if (err == INFO_FORMAT_CHANGED) {
                ALOGD("INFO_FORMAT_CHANGED - pending port settings changed = true");
                mPortSettingsChangedEventPending = true;
                // Since we are breaking out of the loop, we must check for postSeekComplete.
                // Typical use-case is pause, change endpoint, seek and then resume.
                if (postSeekComplete) {
                    mObserver->postAudioSeekComplete();
                }
                break;
            }
#endif
            CHECK((err == OK && mInputBuffer != NULL)
                   || (err != OK && mInputBuffer == NULL));

            Mutex::Autolock autoLock(mLock);

            if (err != OK) {
                if (!mReachedEOS) {
                    if (useOffload()) {
                        // no more buffers to push - stop() and wait for STREAM_END
                        // don't set mReachedEOS until stream end received
                        if (mAudioSink != NULL) {
                            mAudioSink->stop();
                        } else {
                            mAudioTrack->stop();
                        }
                    } else {
                        if (mObserver) {
                            // We don't want to post EOS right away but only
                            // after all frames have actually been played out.

                            // These are the number of frames submitted to the
                            // AudioTrack that you haven't heard yet.
                            uint32_t numFramesPendingPlayout =
                                getNumFramesPendingPlayout();

                            // These are the number of frames we're going to
                            // submit to the AudioTrack by returning from this
                            // callback.
                            uint32_t numAdditionalFrames = size_done / mFrameSize;

                            numFramesPendingPlayout += numAdditionalFrames;

                            int64_t timeToCompletionUs =
                                (1000000ll * numFramesPendingPlayout) / mSampleRate;

                            ALOGV("total number of frames played: %lld (%lld us)",
                                    (mNumFramesPlayed + numAdditionalFrames),
                                    1000000ll * (mNumFramesPlayed + numAdditionalFrames)
                                        / mSampleRate);

                            ALOGV("%d frames left to play, %lld us (%.2f secs)",
                                 numFramesPendingPlayout,
                                 timeToCompletionUs, timeToCompletionUs / 1E6);

                            postEOS = true;
                            if (mAudioSink->needsTrailingPadding()) {
#ifndef ANDROID_DEFAULT_CODE
                        uint32_t afLatency = 0;
                        if(getNumFramesPlayout() >0) {
                            if (AudioSystem::getOutputLatency(&afLatency) != NO_ERROR) {
        								        afLatency = mLatencyUs/3;
                            }
                        }
                    
                        postEOSDelayUs = timeToCompletionUs + 2*1000*afLatency;
                        SXLOGD("postEOSDelayUs =%lld ms",postEOSDelayUs/1000);
#else
                        postEOSDelayUs = timeToCompletionUs + mLatencyUs;
#endif
                            } else {
                                postEOSDelayUs = 0;
                            }
                        }

                        mReachedEOS = true;
                    }
                }

                mFinalStatus = err;
                break;
            }

            if (mAudioSink != NULL) {
                mLatencyUs = (int64_t)mAudioSink->latency() * 1000;
            } else {
                mLatencyUs = (int64_t)mAudioTrack->latency() * 1000;
            }

            if(mInputBuffer->range_length() != 0) {
                CHECK(mInputBuffer->meta_data()->findInt64(
                        kKeyTime, &mPositionTimeMediaUs));
#ifdef  MTB_SUPPORT
                ATRACE_ONESHOT(ATRACE_ONESHOT_ADATA,"AB: %p,TS: %lld", mInputBuffer->data(), mPositionTimeMediaUs);      
#endif	
            }
#ifndef ANDROID_DEFAULT_CODE
			if (mPadEnable)
			{//hai
				if (mPositionTimeMediaUs < mLastBufferTimeUs) {
                    // Demon, skip the frame if all samples are late
                    // INT64_MAX is used for seeking
                    if (mLastBufferTimeUs != INT64_MAX) {
                        size_t size = (mLastBufferTimeUs - mPositionTimeMediaUs)
                            *mSampleRate*mFrameSize/1000000 + mLastBufferSize;
                        if (size > mInputBuffer->range_length()) {
                            SXLOGW("AudioPlayer: drop late buffer %lld", 
                                    mPositionTimeMediaUs);
                            mInputBuffer->release();
                            mInputBuffer = NULL;
                            continue;
                        }
                    }
					mLastBufferTimeUs = mPositionTimeMediaUs;
                }
				size_t NeedLastBufferSize = (mPositionTimeMediaUs - mLastBufferTimeUs)*mSampleRate*mFrameSize/1000000;
				if (NeedLastBufferSize - mLastBufferSize > mLastBufferSize)
				{
					SXLOGD("mPositionTimeMediaUs=%lld, mLastBufferTimeUs=%lld, mSampleRate=%d, mFrameSize=%d", mPositionTimeMediaUs, mLastBufferTimeUs, mSampleRate, mFrameSize);
					SXLOGD("NeedLastBufferSize=%d, mLastBufferSize=%lld", NeedLastBufferSize, mLastBufferSize);
					int64_t tempTimeUs;
					size_t NeedPaddingSize = NeedLastBufferSize - mLastBufferSize;
					NeedPaddingSize = NeedPaddingSize - NeedPaddingSize % mFrameSize;
					if (NeedPaddingSize < 2*1024*1024)//max padding size is 2M
					{
						MediaBuffer *tempBuf = new MediaBuffer(NeedPaddingSize + mInputBuffer->range_length());
						if (tempBuf != NULL)
						{
							memset(tempBuf->data(), 0, NeedPaddingSize);
							memcpy(((char *)tempBuf->data()) + NeedPaddingSize, 
								    ((const char*)mInputBuffer->data()) + mInputBuffer->range_offset(),
								    mInputBuffer->range_length());
							tempTimeUs = mPositionTimeMediaUs;
							mPositionTimeMediaUs = mLastBufferTimeUs + mLastBufferSize * 1000000 / (mFrameSize * mSampleRate);
							mLastBufferSize = mInputBuffer->range_length();
							mLastBufferTimeUs = tempTimeUs;
							mInputBuffer->release();
							mInputBuffer = tempBuf;
						}
						else
						{
							SXLOGW("Malloc audio pad buffer failed!");
							mLastBufferSize = mInputBuffer->range_length();
							mLastBufferTimeUs = mPositionTimeMediaUs;
						}
					}
					else
					{
						SXLOGW("Too large audio padding size(%d)!!!", NeedPaddingSize);
						mLastBufferSize = mInputBuffer->range_length();
						mLastBufferTimeUs = mPositionTimeMediaUs;
					}
				}
				else
				{
					mLastBufferSize = mInputBuffer->range_length();
					mLastBufferTimeUs = mPositionTimeMediaUs;
				}
				
			}
#endif
            // need to adjust the mStartPosUs for offload decoding since parser
            // might not be able to get the exact seek time requested.
            if (refreshSeekTime) {
                if (useOffload()) {
                    if (postSeekComplete) {
                        ALOGV("fillBuffer is going to post SEEK_COMPLETE");
                        mObserver->postAudioSeekComplete();
                        postSeekComplete = false;
                    }

                    mStartPosUs = mPositionTimeMediaUs;
                    ALOGV("adjust seek time to: %.2f", mStartPosUs/ 1E6);
                }
                // clear seek time with mLock locked and once we have valid mPositionTimeMediaUs
                // and mPositionTimeRealUs
                // before clearing mSeekTimeUs check if a new seek request has been received while
                // we were reading from the source with mLock released.
                if (!mSeeking) {
                    mSeekTimeUs = 0;
                }
            }

            if (!useOffload()) {
                mPositionTimeRealUs =
                    ((mNumFramesPlayed + size_done / mFrameSize) * 1000000)
                        / mSampleRate;
                ALOGV("buffer->size() = %d, "
                     "mPositionTimeMediaUs=%.2f mPositionTimeRealUs=%.2f",
                     mInputBuffer->range_length(),
                     mPositionTimeMediaUs / 1E6, mPositionTimeRealUs / 1E6);
            }

        }

        if (mInputBuffer->range_length() == 0) {
            mInputBuffer->release();
            mInputBuffer = NULL;

            continue;
        }

        size_t copy = size_remaining;
        if (copy > mInputBuffer->range_length()) {
            copy = mInputBuffer->range_length();
        }

        memcpy((char *)data + size_done,
               (const char *)mInputBuffer->data() + mInputBuffer->range_offset(),
               copy);

        mInputBuffer->set_range(mInputBuffer->range_offset() + copy,
                                mInputBuffer->range_length() - copy);

        size_done += copy;
        size_remaining -= copy;
    }

    if (useOffload()) {
        // We must ask the hardware what it has played
        mPositionTimeRealUs = getOutputPlayPositionUs_l();
        ALOGV("mPositionTimeMediaUs=%.2f mPositionTimeRealUs=%.2f",
             mPositionTimeMediaUs / 1E6, mPositionTimeRealUs / 1E6);
    }

    {
        Mutex::Autolock autoLock(mLock);
        mNumFramesPlayed += size_done / mFrameSize;
        mNumFramesPlayedSysTimeUs = ALooper::GetNowUs();

        if (mReachedEOS) {
            mPinnedTimeUs = mNumFramesPlayedSysTimeUs;
        } else {
            mPinnedTimeUs = -1ll;
        }
#ifdef MTK_AUDIO_DDPLUS_SUPPORT
        if(mPortSettingsChangedEventPending) {
            ALOGD("portsettingschangedevent received ... posting event");
            mQueue.postEvent(mPortSettingsChangedEvent);
            return size_done;
        }
#endif
    }

    if (postEOS) {
        mObserver->postAudioEOS(postEOSDelayUs);
    }

    if (postSeekComplete) {
        mObserver->postAudioSeekComplete();
    }
#ifndef ANDROID_DEFAULT_CODE
    int32_t trackId = 0;
    trackId = gAudioTrackCenter.getTrackId(mAudioSink.get());
    if (trackId) {
        gAudioTrackCenter.setTrackActive(trackId, !mReachedEOS);
    }
#endif

    return size_done;
}

int64_t AudioPlayer::getRealTimeUs() {
    Mutex::Autolock autoLock(mLock);
    if (useOffload()) {
        if (mSeeking) {
            return mSeekTimeUs;
        }
        mPositionTimeRealUs = getOutputPlayPositionUs_l();
        return mPositionTimeRealUs;
    }

    return getRealTimeUsLocked();
}

int64_t AudioPlayer::getRealTimeUsLocked() const {
    CHECK(mStarted);
    CHECK_NE(mSampleRate, 0);
    
#ifndef ANDROID_DEFAULT_CODE
    int64_t numFramesPlayed = 0;
    int32_t trackId = 0;
    trackId = gAudioTrackCenter.getTrackId(mAudioSink.get());
    if (trackId) {
        gAudioTrackCenter.getRealTimePosition(trackId, &numFramesPlayed);
        int64_t realTimeUs = numFramesPlayed * mAudioSink->msecsPerFrame() * 1000ll;
        return realTimeUs;
    }
#endif    
    int64_t result = -mLatencyUs + (mNumFramesPlayed * 1000000) / mSampleRate;

    // Compensate for large audio buffers, updates of mNumFramesPlayed
    // are less frequent, therefore to get a "smoother" notion of time we
    // compensate using system time.
    int64_t diffUs;
    if (mPinnedTimeUs >= 0ll) {
        diffUs = mPinnedTimeUs;
    } else {
        diffUs = ALooper::GetNowUs();
    }

    diffUs -= mNumFramesPlayedSysTimeUs;

    return result + diffUs;
}

int64_t AudioPlayer::getOutputPlayPositionUs_l() const
{
    uint32_t playedSamples = 0;
    if (mAudioSink != NULL) {
        mAudioSink->getPosition(&playedSamples);
    } else {
        mAudioTrack->getPosition(&playedSamples);
    }

    const int64_t playedUs = (static_cast<int64_t>(playedSamples) * 1000000 ) / mSampleRate;

    // HAL position is relative to the first buffer we sent at mStartPosUs
    const int64_t renderedDuration = mStartPosUs + playedUs;
    ALOGV("getOutputPlayPositionUs_l %lld", renderedDuration);
    return renderedDuration;
}

int64_t AudioPlayer::getMediaTimeUs() {
    Mutex::Autolock autoLock(mLock);

    if (useOffload()) {
        if (mSeeking) {
            return mSeekTimeUs;
        }
        mPositionTimeRealUs = getOutputPlayPositionUs_l();
        ALOGV("getMediaTimeUs getOutputPlayPositionUs_l() mPositionTimeRealUs %lld",
              mPositionTimeRealUs);
        return mPositionTimeRealUs;
    }


    if (mPositionTimeMediaUs < 0 || mPositionTimeRealUs < 0) {
        // mSeekTimeUs is either seek time while seeking or 0 if playback did not start.
        return mSeekTimeUs;
    }

    int64_t realTimeOffset = getRealTimeUsLocked() - mPositionTimeRealUs;
    if (realTimeOffset < 0) {
        realTimeOffset = 0;
    }

    return mPositionTimeMediaUs + realTimeOffset;
}

bool AudioPlayer::getMediaTimeMapping(
        int64_t *realtime_us, int64_t *mediatime_us) {
    Mutex::Autolock autoLock(mLock);

    if (useOffload()) {
        mPositionTimeRealUs = getOutputPlayPositionUs_l();
        *realtime_us = mPositionTimeRealUs;
        *mediatime_us = mPositionTimeRealUs;
    } else {
        *realtime_us = mPositionTimeRealUs;
        *mediatime_us = mPositionTimeMediaUs;
    }

    return mPositionTimeRealUs != -1 && mPositionTimeMediaUs != -1;
}

status_t AudioPlayer::seekTo(int64_t time_us) {
    Mutex::Autolock autoLock(mLock);

    ALOGV("seekTo( %lld )", time_us);

    mSeeking = true;
    mPositionTimeRealUs = mPositionTimeMediaUs = -1;
    mReachedEOS = false;
    mSeekTimeUs = time_us;
    mStartPosUs = time_us;

    // Flush resets the number of played frames
    mNumFramesPlayed = 0;
    mNumFramesPlayedSysTimeUs = ALooper::GetNowUs();
#ifndef ANDROID_DEFAULT_CODE
    mAVSyncLastRealTime = -1ll;
#endif
    if (mAudioSink != NULL) {
        if (mPlaying) {
            mAudioSink->pause();
        }
        mAudioSink->flush();
        if (mPlaying) {
            mAudioSink->start();
        }
    } else {
        if (mPlaying) {
            mAudioTrack->pause();
        }
        mAudioTrack->flush();
        if (mPlaying) {
            mAudioTrack->start();
        }
    }

    return OK;
}

#ifndef ANDROID_DEFAULT_CODE
void AudioPlayer::enableAudioPad()
{
	mPadEnable = true;
}
#endif
}
