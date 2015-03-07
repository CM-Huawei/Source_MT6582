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

#undef DEBUG_HDCP

//#define LOG_NDEBUG 0
#ifndef ANDROID_DEFAULT_CODE
// for INT64_MAX
#undef __STRICT_ANSI__
#define __STDINT_LIMITS
#define __STDC_LIMIT_MACROS
#include <stdint.h>
#endif // #ifndef ANDROID_DEFAULT_CODE
#define LOG_TAG "AwesomePlayer"
#define ATRACE_TAG ATRACE_TAG_VIDEO
#include <utils/Log.h>
#include <utils/Trace.h>

#include <dlfcn.h>

#include "include/AwesomePlayer.h"
#include "include/DRMExtractor.h"
#include "include/SoftwareRenderer.h"
#include "include/NuCachedSource2.h"
#include "include/ThrottledSource.h"
#include "include/MPEG2TSExtractor.h"
#include "include/WVMExtractor.h"
#ifndef ANDROID_DEFAULT_CODE
// for http streaming non-interleave
#include "include/MPEG4Extractor.h"
#include "include/NuCachedWrapperSource.h"
#endif
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
#include <drm/DrmMtkUtil.h>
#include <drm/DrmMtkDef.h>
#endif
#endif // #ifndef ANDROID_DEFAULT_CODE

#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <media/IMediaPlayerService.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/timedtext/TimedTextDriver.h>
#include <media/stagefright/AudioPlayer.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/FileSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/OMXCodec.h>
#include <media/stagefright/Utils.h>

#include <gui/IGraphicBufferProducer.h>
#include <gui/Surface.h>

#include <media/stagefright/foundation/AMessage.h>

#include <cutils/properties.h>
#ifndef ANDROID_DEFAULT_CODE
#include <utils/String8.h>
#include <linux/rtpm_prio.h>
#include <cutils/xlog.h>
#endif

#ifndef ANDROID_DEFAULT_CODE
int64_t getTickCountMs()
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (int64_t)(tv.tv_sec*1000LL + tv.tv_usec/1000);
}

int64_t getTickCountUs()
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (int64_t)(tv.tv_sec*1000000LL + tv.tv_usec);
}
#define SF_SHOW_FPS (1 << 0)
#define SF_POST_BUFFER_PROFILING (1 << 1)
#endif
#define USE_SURFACE_ALLOC 1
#define FRAME_DROP_FREQ 0

namespace android {

static int64_t kLowWaterMarkUs = 2000000ll;  // 2secs
#ifndef ANDROID_DEFAULT_CODE
static int64_t kHighWaterMarkUs = 10000000ll;  // 10secs
#else
static int64_t kHighWaterMarkUs = 5000000ll;  // 5secs
#endif
static const size_t kLowWaterMarkBytes = 40000;
static const size_t kHighWaterMarkBytes = 200000;

// maximum time in paused state when offloading audio decompression. When elapsed, the AudioPlayer
// is destroyed to allow the audio DSP to power down.
static int64_t kOffloadPauseMaxUs = 60000000ll;


struct AwesomeEvent : public TimedEventQueue::Event {
    AwesomeEvent(
            AwesomePlayer *player,
            void (AwesomePlayer::*method)())
        : mPlayer(player),
          mMethod(method) {
    }

protected:
    virtual ~AwesomeEvent() {}

    virtual void fire(TimedEventQueue *queue, int64_t /* now_us */) {
        (mPlayer->*mMethod)();
    }

private:
    AwesomePlayer *mPlayer;
    void (AwesomePlayer::*mMethod)();

    AwesomeEvent(const AwesomeEvent &);
    AwesomeEvent &operator=(const AwesomeEvent &);
};

struct AwesomeLocalRenderer : public AwesomeRenderer {
    AwesomeLocalRenderer(
            const sp<ANativeWindow> &nativeWindow, const sp<MetaData> &meta)
        : mTarget(new SoftwareRenderer(nativeWindow, meta)) {
    }

    virtual void render(MediaBuffer *buffer) {
        render((const uint8_t *)buffer->data() + buffer->range_offset(),
               buffer->range_length());
    }

    void render(const void *data, size_t size) {
        mTarget->render(data, size, NULL);
    }

protected:
    virtual ~AwesomeLocalRenderer() {
        delete mTarget;
        mTarget = NULL;
    }

private:
    SoftwareRenderer *mTarget;

    AwesomeLocalRenderer(const AwesomeLocalRenderer &);
    AwesomeLocalRenderer &operator=(const AwesomeLocalRenderer &);;
};

struct AwesomeNativeWindowRenderer : public AwesomeRenderer {
    AwesomeNativeWindowRenderer(
            const sp<ANativeWindow> &nativeWindow,
            int32_t rotationDegrees)
        : mNativeWindow(nativeWindow) {
        applyRotation(rotationDegrees);
#ifndef ANDROID_DEFAULT_CODE
        mDbgFlags = 0;
        mFrameCount = 0;
        mFirstPostBufferTime = 0;
        mLastPostBufferTime = 0;
        mQueueBufferInTs = 0;
        char value[PROPERTY_VALUE_MAX];
        property_get("sf.showfps", value, "1");	// enable by default temporarily
        bool _res = atoi(value);
        if (_res) mDbgFlags |= SF_SHOW_FPS;

        property_get("sf.postbuffer.prof", value, "0");	// disable by default
        _res = atoi(value);
        if (_res) mDbgFlags |= SF_POST_BUFFER_PROFILING;
#endif
    }

    virtual void render(MediaBuffer *buffer) {
        ATRACE_CALL();
#ifndef ANDROID_DEFAULT_CODE
        if (mDbgFlags & SF_SHOW_FPS) {
            if (0 == mFrameCount) {
                mLastPostBufferTime = mFirstPostBufferTime = getTickCountMs();
            }
            else {
                if (0 == (mFrameCount % 60)) {
					int64_t _i8CurrentMs = getTickCountMs();
                                        int64_t _diff                = _i8CurrentMs - mFirstPostBufferTime;
					int64_t _60Framediff = _i8CurrentMs - mLastPostBufferTime;

					mLastPostBufferTime = _i8CurrentMs;
                                        double fps = (double)1000*mFrameCount/_diff;
					double slotfps = (double)1000*60/_60Framediff;
                    ALOGD ("FPS = %.2f", fps);
					ALOGD ("Slot FPS = %.2f", slotfps);
                }                
            }
            mFrameCount++;
        }
#endif        
        int64_t timeUs;
        CHECK(buffer->meta_data()->findInt64(kKeyTime, &timeUs));
        native_window_set_buffers_timestamp(mNativeWindow.get(), timeUs * 1000);
#ifndef ANDROID_DEFAULT_CODE
        if (mDbgFlags & SF_POST_BUFFER_PROFILING) {
            mQueueBufferInTs = getTickCountUs();
            ALOGD ("+queueBuffer [%d]", mFrameCount);
        }
#endif        
        status_t err = mNativeWindow->queueBuffer(
                mNativeWindow.get(), buffer->graphicBuffer().get(), -1);
        if (err != 0) {
            ALOGE("queueBuffer failed with error %s (%d)", strerror(-err),
                    -err);
            return;
        }

#ifndef ANDROID_DEFAULT_CODE
    if (mDbgFlags & SF_POST_BUFFER_PROFILING) {
        int64_t _out = getTickCountUs() - mQueueBufferInTs;
        ALOGD ("-queueBuffer (%lld)", _out);
    }
#endif
        sp<MetaData> metaData = buffer->meta_data();
        metaData->setInt32(kKeyRendered, 1);
    }

protected:
    virtual ~AwesomeNativeWindowRenderer() {}

private:
    sp<ANativeWindow> mNativeWindow;

#ifndef ANDROID_DEFAULT_CODE
    uint32_t mDbgFlags;
    uint32_t mFrameCount;
    int64_t mFirstPostBufferTime;
    int64_t mLastPostBufferTime;
    int64_t mQueueBufferInTs;
#endif
    void applyRotation(int32_t rotationDegrees) {
        uint32_t transform;
        switch (rotationDegrees) {
            case 0: transform = 0; break;
            case 90: transform = HAL_TRANSFORM_ROT_90; break;
            case 180: transform = HAL_TRANSFORM_ROT_180; break;
            case 270: transform = HAL_TRANSFORM_ROT_270; break;
            default: transform = 0; break;
        }

        if (transform) {
            CHECK_EQ(0, native_window_set_buffers_transform(
                        mNativeWindow.get(), transform));
        }
    }

    AwesomeNativeWindowRenderer(const AwesomeNativeWindowRenderer &);
    AwesomeNativeWindowRenderer &operator=(
            const AwesomeNativeWindowRenderer &);
};

// To collect the decoder usage
void addBatteryData(uint32_t params) {
    sp<IBinder> binder =
        defaultServiceManager()->getService(String16("media.player"));
    sp<IMediaPlayerService> service = interface_cast<IMediaPlayerService>(binder);
    CHECK(service.get() != NULL);

    service->addBatteryData(params);
}

////////////////////////////////////////////////////////////////////////////////
AwesomePlayer::AwesomePlayer()
    : mQueueStarted(false),
      mUIDValid(false),
      mTimeSource(NULL),
      mVideoRenderingStarted(false),
      mVideoRendererIsPreview(false),
      mMediaRenderingStartGeneration(0),
      mStartGeneration(0),
      mAudioPlayer(NULL),
      mDisplayWidth(0),
      mDisplayHeight(0),
      mVideoScalingMode(NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW),
      mFlags(0),
      mExtractorFlags(0),
      mVideoBuffer(NULL),
      mDecryptHandle(NULL),
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
      mIsCurrentComplete(false),
#endif
#endif
      mLastVideoTimeUs(-1),
      mTextDriver(NULL),
      mOffloadAudio(false),
#ifdef ANDROID_DEFAULT_CODE
      mAudioTearDown(false) {
#else
      mAudioTearDown(false), 
      mAVSyncTimeUs(-1),
      mAVSyncThreshold(500000ll),
      mFRAME_DROP_FREQ(0),
      mLateMargin(250000ll),
      mPrerollEnable(true),
      mLastPositionUs(-1),
      mAdjustPos(0),
      mFirstSubmit(true),
      mVdecQuirks(0),
      mAudioPadEnable(false),
      mHighWaterMarkUs(5000000ll),
#ifdef MTK_CLEARMOTION_SUPPORT
      mMJCPauseDelay(false),
#endif  
      mFinalStopFlag(0) {
#endif
    CHECK_EQ(mClient.connect(), (status_t)OK);

    DataSource::RegisterDefaultSniffers();

    mVideoEvent = new AwesomeEvent(this, &AwesomePlayer::onVideoEvent);
    mVideoEventPending = false;
    mStreamDoneEvent = new AwesomeEvent(this, &AwesomePlayer::onStreamDone);
    mStreamDoneEventPending = false;
    mBufferingEvent = new AwesomeEvent(this, &AwesomePlayer::onBufferingUpdate);
    mBufferingEventPending = false;
    mVideoLagEvent = new AwesomeEvent(this, &AwesomePlayer::onVideoLagUpdate);
    mVideoLagEventPending = false;

    mCheckAudioStatusEvent = new AwesomeEvent(
            this, &AwesomePlayer::onCheckAudioStatus);
#ifndef ANDROID_DEFAULT_CODE
	mDurationUpdateEvent = new AwesomeEvent(this, &AwesomePlayer::OnDurationUpdate);
	mDurationUpdateEventPending = false;
	mEnAudST = 0;
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT	
	mSync_time = 0;
	mSMSpeed = 0;
	mSpeedValue = 0;
#endif	
#ifdef MTK_CLEARMOTION_SUPPORT            
        mEnClearMotion = 1;
#endif
       mIsLargeMetaData = false;
       char throttleValue[PROPERTY_VALUE_MAX];
       property_get("onvideoevent.buf.rel", throttleValue, "6000");
       mThrottleVideoBufRel = atoi(throttleValue);
       ALOGI("mThrottleVideoBufRel:%d us", mThrottleVideoBufRel);
#endif

    mAudioStatusEventPending = false;

    mAudioTearDownEvent = new AwesomeEvent(this,
                              &AwesomePlayer::onAudioTearDownEvent);
    mAudioTearDownEventPending = false;

#ifndef ANDROID_DEFAULT_CODE 
	char jumpvalue[PROPERTY_VALUE_MAX];
    property_get("sf.video.late.jump.key.ms", jumpvalue, "-1");
    mAVSyncThreshold = atol(jumpvalue);
	if(mAVSyncThreshold>0)
	{
		mAVSyncThreshold = mAVSyncThreshold*1000;
		ALOGD("@@[SF_PROPERTY]sf.video.jump.key.ms =%lld",mAVSyncThreshold/1000);
	}
	else
	{
		ALOGD("@@[SF_PROPERTY]sf.video.jump.key.ms =%lld",mAVSyncThreshold);
	}

	char forcevalue[PROPERTY_VALUE_MAX];
	property_get("sf.video.force.display.cnt", forcevalue, "0");
	mFRAME_DROP_FREQ = atol(forcevalue);
	ALOGD("@@[SF_PROPERTY]sf.video.force.display.cnt=%d",mFRAME_DROP_FREQ);


   
    char mLateMargin_value[PROPERTY_VALUE_MAX];
    property_get("sf.video.late.margin.ms", mLateMargin_value, "250");	
    mLateMargin = atoi(mLateMargin_value);
    
	if(mLateMargin>0)
	{
		mLateMargin = mLateMargin*1000;
		ALOGD ("@@[SF_PROPERTY]sf.video.late.margin.ms = %d", mLateMargin/1000);
	}
	else
	{
		ALOGD ("@@[SF_PROPERTY]sf.video.late.margin.ms = %d", mLateMargin);
	}

#endif
    reset();
}

AwesomePlayer::~AwesomePlayer() {
    if (mQueueStarted) {
        mQueue.stop();
    }

    reset();

    mClient.disconnect();
}

void AwesomePlayer::cancelPlayerEvents(bool keepNotifications) {
    mQueue.cancelEvent(mVideoEvent->eventID());
    mVideoEventPending = false;
    mQueue.cancelEvent(mVideoLagEvent->eventID());
    mVideoLagEventPending = false;

    if (mOffloadAudio) {
        mQueue.cancelEvent(mAudioTearDownEvent->eventID());
        mAudioTearDownEventPending = false;
    }

    if (!keepNotifications) {
        mQueue.cancelEvent(mStreamDoneEvent->eventID());
        mStreamDoneEventPending = false;
        mQueue.cancelEvent(mCheckAudioStatusEvent->eventID());
        mAudioStatusEventPending = false;

        mQueue.cancelEvent(mBufferingEvent->eventID());
        mBufferingEventPending = false;
        mAudioTearDown = false;
    }
}

void AwesomePlayer::setListener(const wp<MediaPlayerBase> &listener) {
    Mutex::Autolock autoLock(mLock);
    mListener = listener;
}

void AwesomePlayer::setUID(uid_t uid) {
    ALOGV("AwesomePlayer running on behalf of uid %d", uid);

    mUID = uid;
    mUIDValid = true;
}

status_t AwesomePlayer::setDataSource(
        const char *uri, const KeyedVector<String8, String8> *headers) {
    Mutex::Autolock autoLock(mLock);
    return setDataSource_l(uri, headers);
}

status_t AwesomePlayer::setDataSource_l(
        const char *uri, const KeyedVector<String8, String8> *headers) {
    ATRACE_CALL();
    reset_l();

    mUri = uri;

    if (headers) {
#ifndef ANDROID_DEFAULT_CODE
       ALOGD("setDataSource headers:\n");
        for (size_t i = 0; i < headers->size(); i++) {
           ALOGD("\t\t%s: %s", headers->keyAt(i).string(), headers->valueAt(i).string());
        } 
#endif
        mUriHeaders = *headers;

        ssize_t index = mUriHeaders.indexOfKey(String8("x-hide-urls-from-log"));
        if (index >= 0) {
            // Browser is in "incognito" mode, suppress logging URLs.

            // This isn't something that should be passed to the server.
            mUriHeaders.removeItemsAt(index);

            modifyFlags(INCOGNITO, SET);
        }
    }

    ALOGI("setDataSource_l(URL suppressed)");

    // The actual work will be done during preparation in the call to
    // ::finishSetDataSource_l to avoid blocking the calling thread in
    // setDataSource for any significant time.

    {
        Mutex::Autolock autoLock(mStatsLock);
        mStats.mFd = -1;
        mStats.mURI = mUri;
    }
	
    return OK;
}

status_t AwesomePlayer::setDataSource(
        int fd, int64_t offset, int64_t length) {
    Mutex::Autolock autoLock(mLock);

    reset_l();

    sp<DataSource> dataSource = new FileSource(fd, offset, length);

    status_t err = dataSource->initCheck();

    if (err != OK) {
        return err;
    }

    mFileSource = dataSource;

#ifndef ANDROID_DEFAULT_CODE
    String8 tmp;
    if( mFileSource->fastsniff(fd, &tmp))
    {
        const char *sniffedMIME = tmp.string();
        sp<MediaExtractor> extractor = MediaExtractor::Create(dataSource, sniffedMIME);

        if (extractor == NULL) {
            return UNKNOWN_ERROR;
        }

        if (extractor->getDrmFlag()) {
            checkDrmStatus(dataSource);
        }

        return setDataSource_l(extractor);
    }
#endif
    {
        Mutex::Autolock autoLock(mStatsLock);
        mStats.mFd = fd;
        mStats.mURI = String8();
    }

    return setDataSource_l(dataSource);
}

status_t AwesomePlayer::setDataSource(const sp<IStreamSource> &source) {
    return INVALID_OPERATION;
}

status_t AwesomePlayer::setDataSource_l(
        const sp<DataSource> &dataSource) {
    sp<MediaExtractor> extractor = MediaExtractor::Create(dataSource);

    if (extractor == NULL) {
        return UNKNOWN_ERROR;
    }

    if (extractor->getDrmFlag()) {
        checkDrmStatus(dataSource);
    }

    return setDataSource_l(extractor);
}

void AwesomePlayer::checkDrmStatus(const sp<DataSource>& dataSource) {
    dataSource->getDrmInfo(mDecryptHandle, &mDrmManagerClient);
    if (mDecryptHandle != NULL) {
        CHECK(mDrmManagerClient);
        if (RightsStatus::RIGHTS_VALID != mDecryptHandle->status) {
            notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, ERROR_DRM_NO_LICENSE);
        }
    }
}

status_t AwesomePlayer::setDataSource_l(const sp<MediaExtractor> &extractor) {
    // Attempt to approximate overall stream bitrate by summing all
    // tracks' individual bitrates, if not all of them advertise bitrate,
    // we have to fail.

    int64_t totalBitRate = 0;

#ifndef ANDROID_DEFAULT_CODE
    if (mCachedSource != NULL && mIsLargeMetaData) {
	mLock.unlock();
	ALOGI("Http Streaming unlock  when getMetaData()");
	mMetaData = extractor->getMetaData();
	ALOGI("Http Streaming lock after getMetaData()");
	mLock.lock();
    } else {
	mMetaData = extractor->getMetaData();
    } 
#endif
    mExtractor = extractor;
    for (size_t i = 0; i < extractor->countTracks(); ++i) {
        sp<MetaData> meta = extractor->getTrackMetaData(i);

        int32_t bitrate;
        if (!meta->findInt32(kKeyBitRate, &bitrate)) {
            const char *mime;
            CHECK(meta->findCString(kKeyMIMEType, &mime));
            ALOGV("track of type '%s' does not publish bitrate", mime);

            totalBitRate = -1;
            break;
        }

        totalBitRate += bitrate;
    }

    mBitrate = totalBitRate;

    ALOGV("mBitrate = %lld bits/sec", mBitrate);

    {
        Mutex::Autolock autoLock(mStatsLock);
        mStats.mBitrate = mBitrate;
        mStats.mTracks.clear();
        mStats.mAudioTrackIndex = -1;
        mStats.mVideoTrackIndex = -1;
    }

    bool haveAudio = false;
    bool haveVideo = false;
    for (size_t i = 0; i < extractor->countTracks(); ++i) {
#ifndef ANDROID_DEFAULT_CODE
       sp<MetaData> meta = extractor->getTrackMetaData(i,MediaExtractor::kIncludeInterleaveInfo);
        meta->setPointer(kKeyDataSourceObserver,this); //save awesomeplayer pointer
#else
	sp<MetaData> meta = extractor->getTrackMetaData(i);
#endif
        const char *_mime;
        CHECK(meta->findCString(kKeyMIMEType, &_mime));

        String8 mime = String8(_mime);

        if (!haveVideo && !strncasecmp(mime.string(), "video/", 6)) {
            setVideoSource(extractor->getTrack(i));
            haveVideo = true;

            // Set the presentation/display size
            int32_t displayWidth, displayHeight;
            bool success = meta->findInt32(kKeyDisplayWidth, &displayWidth);
            if (success) {
                success = meta->findInt32(kKeyDisplayHeight, &displayHeight);
            }
            if (success) {
                mDisplayWidth = displayWidth;
                mDisplayHeight = displayHeight;
            }

            {
                Mutex::Autolock autoLock(mStatsLock);
                mStats.mVideoTrackIndex = mStats.mTracks.size();
                mStats.mTracks.push();
                TrackStat *stat =
                    &mStats.mTracks.editItemAt(mStats.mVideoTrackIndex);
                stat->mMIME = mime.string();
            }
        } else if (!haveAudio && !strncasecmp(mime.string(), "audio/", 6)) {
            setAudioSource(extractor->getTrack(i));
            haveAudio = true;
            mActiveAudioTrackIndex = i;

            {
                Mutex::Autolock autoLock(mStatsLock);
                mStats.mAudioTrackIndex = mStats.mTracks.size();
                mStats.mTracks.push();
                TrackStat *stat =
                    &mStats.mTracks.editItemAt(mStats.mAudioTrackIndex);
                stat->mMIME = mime.string();
            }

            if (!strcasecmp(mime.string(), MEDIA_MIMETYPE_AUDIO_VORBIS)) {
                // Only do this for vorbis audio, none of the other audio
                // formats even support this ringtone specific hack and
                // retrieving the metadata on some extractors may turn out
                // to be very expensive.
                sp<MetaData> fileMeta = extractor->getMetaData();
                int32_t loop;
                if (fileMeta != NULL
                        && fileMeta->findInt32(kKeyAutoLoop, &loop) && loop != 0) {
                    modifyFlags(AUTO_LOOPING, SET);
                }
            }
        #ifdef MTK_SUBTITLE_SUPPORT
        } else if (!strncasecmp(mime.string(), "text/", 5)) {
            ALOGE("[PANDA] Find Text Track in index %d, type %s\n", i, mime.string());
            addTextSource_l(i, extractor->getTrack(i));
        }
        #else
        } else if (!strcasecmp(mime.string(), MEDIA_MIMETYPE_TEXT_3GPP)) {
            addTextSource_l(i, extractor->getTrack(i));
        }
        #endif
    }
#ifndef ANDROID_DEFAULT_CODE
#if 0  //for   89 phase1 qc
		 if ( haveVideo) {
		 	 notifyListener_l(MEDIA_ERROR,MEDIA_ERROR_UNKNOWN);
			 ALOGE("not support video playback now");
			  return UNKNOWN_ERROR;			
		 }
#endif	
#endif


    if (!haveAudio && !haveVideo) {
        if (mWVMExtractor != NULL) {
            return mWVMExtractor->getError();
        } else {
#ifndef ANDROID_DEFAULT_CODE
        // report unsupport for new Gallery[3D]
        notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_TYPE_NOT_SUPPORTED);
#endif // ifndef ANDROID_DEFAULT_CODE
        return UNKNOWN_ERROR;
    }
    }

#ifndef ANDROID_DEFAULT_CODE
	if (!haveVideo) {
		int32_t hasUnsupportVideo = 0;
		sp<MetaData> fileMeta = extractor->getMetaData();
		if (fileMeta != NULL && fileMeta->findInt32(kKeyHasUnsupportVideo, &hasUnsupportVideo)
			&& hasUnsupportVideo != 0) {
			notifyListener_l(MEDIA_INFO, MEDIA_INFO_HAS_UNSUPPORT_VIDEO);
			ALOGD("Notify APP that file has unsupportted video");
		}
			
	}
#endif

#ifndef ANDROID_DEFAULT_CODE
	 
		if (haveVideo && haveAudio && mCachedSource!=NULL) {
			char  poorvalue[PROPERTY_VALUE_MAX];
			uint32_t poorvalue1=0;
		       property_get("sf.poor.interlace.size", poorvalue, "500");
			poorvalue1 = atol(poorvalue);
	
			const char *_mimeX;
			sp<MetaData> fileMeta = extractor->getMetaData();
			//if(fileMeta->findCString(kKeyMIMEType, &_mimeX) &&
			//  (!strcasecmp(_mimeX, "video/mp4") ||!strcasecmp(_mimeX, "video/3gp") ))
			{
				int64_t   voff=0;
				int64_t   aoff=0;
				int64_t   diff=0;
				int64_t   voffl=0;
				int64_t   aoffl=0;                
				
 				if( mVideoTrack->getFormat()->findInt64(kKeyFirstSampleOffset, &voff)
				&& mAudioTrack->getFormat()->findInt64(kKeyFirstSampleOffset, &aoff))
  				{
	 				diff=(voff>aoff?voff:aoff)-(voff<aoff?voff:aoff);
					ALOGD("***file interlace info****:voff=%lld,aoff=%lld,diff=%lld",voff,aoff,diff);
					if(diff>poorvalue1*1024)
					{
						notifyListener_l(MEDIA_INFO, MEDIA_INFO_BAD_INTERLEAVING,diff);	
					}
 				}

                property_get("sf.non.interlace.disable", poorvalue, "0");
				bool nonInterDisable = atol(poorvalue);
              
				if (!nonInterDisable && fileMeta != NULL && fileMeta->findCString(kKeyMIMEType, &_mimeX) &&
					(!strcasecmp(_mimeX, "video/mp4") ||!strcasecmp(_mimeX, "video/3gp") ) &&
                    mVideoTrack->getFormat()->findInt64(kKeyLastSampleOffset, &voffl) &&
				    mAudioTrack->getFormat()->findInt64(kKeyLastSampleOffset, &aoffl))
                {
                    bool isNonInterleave = (aoff > voffl || voff > aoffl);
                    if (isNonInterleave)
                    {
                        ALOGD("***file non-interleave****");
                        
                        bool isAudioLarge = false;
                        off64_t large_off;
                        double track1Size, track2Size, bothTrackSize;
                        sp<MediaSource> Track2;

                        if (aoff > voff) // Audio is large
                        {
                            isAudioLarge = true;
                            large_off = aoff;
                            Track2 = mAudioTrack;
                            track1Size = voffl-voff;
                            track2Size = aoffl-aoff;
                        }
                        else //  Video is large
                        {
                            large_off = voff;
                            Track2 = mVideoTrack;
                            track1Size = aoffl-aoff;
                            track2Size = voffl-voff;
                        }

                        bothTrackSize = track1Size + track2Size;
                        /* create connection for second track */
                        mConnectingDataSource2 = HTTPBase::Create(
                        (mFlags & INCOGNITO) ? HTTPBase::kFlagIncognito : 0);

                        if (mUIDValid)
                            mConnectingDataSource2->setUID(mUID);

                        mLock.unlock();
                        ALOGI("mConnectingDataSource2->connect");
                        status_t err = mConnectingDataSource2->connect(mUri, &mUriHeaders, large_off);
                        ALOGI("mConnectingDataSource2->connect done");
                        mLock.lock();

                        ALOGD("get lock");

                        if (err != OK) {
                            Mutex::Autolock autoLock(mMiscStateLock);
                            if (mConnectingDataSource2 != NULL) {
                                mConnectingDataSource2.clear();
                            }
                            ALOGE("mConnectingDataSource2->connect() returned %d", err);
			                return ERROR_CANNOT_CONNECT;//notify this when connect fail whatever DataSource returned
                        }

                        /* create second NuCachedSource2 */
                        ALOGD("new NuCachedSource2(config=%s, disconn@highwater=%d, aoff=%lld", mCachedSource->mConfigStr.isEmpty() ? "NULL" : mCachedSource->mConfigStr.string(), mCachedSource->mDisconnectAtHighwatermark, aoff);
                        mCachedSourceSecond = new NuCachedSource2(
                            mConnectingDataSource2,
                            mCachedSource->mConfigStr.isEmpty() ? NULL : mCachedSource->mConfigStr.string(),
                            mCachedSource->mDisconnectAtHighwatermark,
                            large_off);
                        
					// should mpeg4 source 
					reinterpret_cast<MPEG4Extractor *>(mExtractor.get())->changeDataSource(Track2, mCachedSourceSecond);
					//Track2->setDataSource(mCachedSourceSecond);
                        mCachedSourceMain = mCachedSource;                                                                    

                        mCachedSourceMain->setInterleaveMode(false, track1Size/bothTrackSize);
                        mCachedSourceMain->setOffsetLimit(large_off);
                        mCachedSourceSecond->setInterleaveMode(false, track2Size/bothTrackSize);                        
                            
                        //create wrapper class
                        mCachedSource = new NuCachedWrapperSource(mCachedSourceMain, mCachedSourceSecond, large_off);

                        modifyFlags(PGDL_NONINTERLEAVE, SET);
                    }
                }
			}
	  }
#endif
    mExtractorFlags = extractor->flags();

#ifndef ANDROID_DEFAULT_CODE
    if ((extractor->flags() & MediaExtractor::MAY_PARSE_TOO_LONG)) {
        Mutex::Autolock autoLock(mMiscStateLock);
        if (mStopped) {
            ALOGI("user has already stopped");
            extractor->stopParsing();
        } else {
            ALOGI("this extractor may take long time to parse, record for stopping");
            mExtractor = extractor;
        }
    }
#endif 
/*  let app can check whether a/v processing is drm file */
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
    bool drmFlag = extractor->getDrmFlag();
    int32_t isDrm = 0;

	if(mMetaData->findInt32(kKeyIsDRM, &isDrm)){
		ALOGD("mMetaData->findInt32(kKeyIsDRM, &isDrm) scuess, isDrm is %d", isDrm);
	}else{
	    ALOGD("mMetaData->findInt32(kKeyIsDRM, &isDrm) fail, then set is %d", drmFlag);
		mMetaData->setInt32(kKeyIsDRM, drmFlag);
	}
#endif //MTK_DRM_APP
#endif //ANDROID_DEFAULT_CODE
    return OK;
}

void AwesomePlayer::reset() {
#ifndef ANDROID_DEFAULT_CODE        //for HTTP Streaming
	 if (mExtractor != NULL) {
            ALOGI("stop extractor in reset");
            mExtractor->stopParsing();
        }
#endif // #ifndef ANDROID_DEFAULT_CODE
#ifndef ANDROID_DEFAULT_CODE //for HTTP Streaming
    disconnectSafeIfNeccesary();
//    if (mCachedSource != NULL) {
//        mCachedSource->stop();
//    }
#endif

#ifndef ANDROID_DEFAULT_CODE
    {
        Mutex::Autolock autoLock(mMiscStateLock);
		#if 0
        if (mExtractor != NULL) {
            ALOGI("stop extractor in reset");
            mExtractor->stopParsing();
        } else 
        #endif
		if (mExtractor == NULL)
        {
            ALOGI("set flag for stopped");
            mStopped = true;
        }
    }
#endif // #ifndef ANDROID_DEFAULT_CODE
    Mutex::Autolock autoLock(mLock);
    reset_l();
}

void AwesomePlayer::reset_l() {
#ifndef ANDROID_DEFAULT_CODE	
	ALOGD("reset_l");
#endif
    mVideoRenderingStarted = false;
    mActiveAudioTrackIndex = -1;
    mDisplayWidth = 0;
    mDisplayHeight = 0;

    notifyListener_l(MEDIA_STOPPED);

    if (mDecryptHandle != NULL) {
            mDrmManagerClient->setPlaybackStatus(mDecryptHandle,
                    Playback::STOP, 0);
            mDecryptHandle = NULL;
            mDrmManagerClient = NULL;
    }

    if (mFlags & PLAYING) {
        uint32_t params = IMediaPlayerService::kBatteryDataTrackDecoder;
        if ((mAudioSource != NULL) && (mAudioSource != mAudioTrack)) {
            params |= IMediaPlayerService::kBatteryDataTrackAudio;
        }
        if (mVideoSource != NULL) {
            params |= IMediaPlayerService::kBatteryDataTrackVideo;
        }
        addBatteryData(params);
    }

    if (mFlags & PREPARING) {
        modifyFlags(PREPARE_CANCELLED, SET);
        if (mConnectingDataSource != NULL) {
            ALOGI("interrupting the connection process");
#ifndef ANDROID_DEFAULT_CODE
			if (mCachedSource != NULL) {
				mCachedSource->finishCache();
			}
#endif
            mConnectingDataSource->disconnect();
        }
#ifndef ANDROID_DEFAULT_CODE
        if (mConnectingDataSource2 != NULL) {
            mConnectingDataSource2->disconnect();
        }
#endif
        if (mFlags & PREPARING_CONNECTED) {
            // We are basically done preparing, we're just buffering
            // enough data to start playback, we can safely interrupt that.
            finishAsyncPrepare_l();
        }
    }

    while (mFlags & PREPARING) {
#ifndef ANDROID_DEFAULT_CODE
        ALOGI("wait prepare +++");
#endif
        mPreparedCondition.wait(mLock);
#ifndef ANDROID_DEFAULT_CODE
        ALOGI("wait prepare ---");
#endif
    }

#ifndef ANDROID_DEFAULT_CODE
    ALOGI("reset cancelPlayEvents");
#endif
    cancelPlayerEvents();
#ifndef ANDROID_DEFAULT_CODE
    mQueue.cancelEvent(mDurationUpdateEvent->eventID());
    mDurationUpdateEventPending = false;
    if (mFlags & PGDL_NONINTERLEAVE){
        mCachedSourceMain.clear();
        mCachedSourceSecond.clear();
        mConnectingDataSource2.clear();
    }
#endif
    mWVMExtractor.clear();
    mCachedSource.clear();
    mAudioTrack.clear();
    mVideoTrack.clear();
    mExtractor.clear();

    // Shutdown audio first, so that the response to the reset request
    // appears to happen instantaneously as far as the user is concerned
    // If we did this later, audio would continue playing while we
    // shutdown the video-related resources and the player appear to
    // not be as responsive to a reset request.
    if ((mAudioPlayer == NULL || !(mFlags & AUDIOPLAYER_STARTED))
            && mAudioSource != NULL) {
        // If we had an audio player, it would have effectively
        // taken possession of the audio source and stopped it when
        // _it_ is stopped. Otherwise this is still our responsibility.
        mAudioSource->stop();
    }
    mAudioSource.clear();
    mOmxSource.clear();

    mTimeSource = NULL;

    delete mAudioPlayer;
    mAudioPlayer = NULL;

    if (mTextDriver != NULL) {
        delete mTextDriver;
        mTextDriver = NULL;
    }

    mVideoRenderer.clear();

    if (mVideoSource != NULL) {
        shutdownVideoDecoder_l();
    }

    mDurationUs = -1;
    modifyFlags(0, ASSIGN);
    mExtractorFlags = 0;
    mTimeSourceDeltaUs = 0;
    mVideoTimeUs = 0;

#ifndef ANDROID_DEFAULT_CODE
    mFinishAgain = false;
    mMetaData.clear();
    mWatchForAudioSeekComplete = false;
    mAudioNormalEOS = false;
    mLastAudioSeekUs = 0;
    mExtractor.clear();
    mStopped = false;
    mLatencyUs = 0;
    mFirstVideoBuffer = NULL;
    mFirstVideoBufferStatus = OK;
    mInRescheding = false;
#endif // #ifndef ANDROID_DEFAULT_CODE
#ifndef ANDROID_DEFAULT_CODE //for HTTP Streaming
    mCachedSourcePauseResponseState = 0;
#endif
    mSeeking = NO_SEEK;
    mSeekNotificationSent = true;
    mSeekTimeUs = 0;

    mUri.setTo("");
    mUriHeaders.clear();

    mFileSource.clear();

    mBitrate = -1;
    mLastVideoTimeUs = -1;

    {
        Mutex::Autolock autoLock(mStatsLock);
        mStats.mFd = -1;
        mStats.mURI = String8();
        mStats.mBitrate = -1;
        mStats.mAudioTrackIndex = -1;
        mStats.mVideoTrackIndex = -1;
        mStats.mNumVideoFramesDecoded = 0;
        mStats.mNumVideoFramesDropped = 0;
        mStats.mVideoWidth = -1;
        mStats.mVideoHeight = -1;
        mStats.mFlags = 0;
        mStats.mTracks.clear();
    }

    mWatchForAudioSeekComplete = false;
    mWatchForAudioEOS = false;

    mMediaRenderingStartGeneration = 0;
    mStartGeneration = 0;
}

void AwesomePlayer::notifyListener_l(int msg, int ext1, int ext2) {
#ifndef ANDROID_DEFAULT_CODE
	if ((mCachedSource != NULL) && !mAudioTearDown && (msg == MEDIA_ERROR)) {
		 status_t cache_stat = mCachedSource->getRealFinalStatus();
		 bool bCacheSuccess = (cache_stat == OK || cache_stat == ERROR_END_OF_STREAM);
	
		 if (!bCacheSuccess) {
             if (cache_stat == -ECANCELED) {
                 ALOGD("this error triggered by user's stopping, would not report");
                 return;
             } else if (cache_stat == ERROR_FORBIDDEN) {
				 ext1 = MEDIA_ERROR_INVALID_CONNECTION;//httpstatus = 403
			 } else if (cache_stat == ERROR_POOR_INTERLACE) {
				 ext1 = MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK;
			 } else {
				 ext1 = MEDIA_ERROR_CANNOT_CONNECT_TO_SERVER;
			 }
			ext2 = cache_stat;
			ALOGE("report 'cannot connect' to app, cache_stat = %d", cache_stat);
		 }
	 } 
    // try to report a more meaningful error
    if (msg == MEDIA_ERROR && ext1 == MEDIA_ERROR_UNKNOWN) {
        switch(ext2) {
            case ERROR_MALFORMED:
                //http streaming don't report "bad file", because MALFORMED maybe caused by network
                if (mCachedSource == NULL) {
                    ext1 = MEDIA_ERROR_BAD_FILE;
                } 
                break;
            case ERROR_CANNOT_CONNECT:
                ext1 = MEDIA_ERROR_CANNOT_CONNECT_TO_SERVER;
                break;
            case ERROR_UNSUPPORTED:
                ext1 = MEDIA_ERROR_TYPE_NOT_SUPPORTED;
                break;
            case ERROR_FORBIDDEN:
                ext1 = MEDIA_ERROR_INVALID_CONNECTION;
                break;
        }
    }
#endif // ifndef ANDROID_DEFAULT_CODE
    if ((mListener != NULL) && !mAudioTearDown) {
        sp<MediaPlayerBase> listener = mListener.promote();

        if (listener != NULL) {
            listener->sendEvent(msg, ext1, ext2);
        }
    }
}

bool AwesomePlayer::getBitrate(int64_t *bitrate) {
    off64_t size;
    if (mDurationUs > 0 && mCachedSource != NULL
            && mCachedSource->getSize(&size) == OK) {
        *bitrate = size * 8000000ll / mDurationUs;  // in bits/sec
        return true;
    }

    if (mBitrate >= 0) {
        *bitrate = mBitrate;
        return true;
    }

    *bitrate = 0;

    return false;
}

// Returns true iff cached duration is available/applicable.
bool AwesomePlayer::getCachedDuration_l(int64_t *durationUs, bool *eos) {
    int64_t bitrate;

    if (mCachedSource != NULL && getBitrate(&bitrate) && (bitrate > 0)) {
        status_t finalStatus;
        size_t cachedDataRemaining = mCachedSource->approxDataRemaining(&finalStatus);
        *durationUs = cachedDataRemaining * 8000000ll / bitrate;
        *eos = (finalStatus != OK);
        return true;
    } else if (mWVMExtractor != NULL) {
        status_t finalStatus;
        *durationUs = mWVMExtractor->getCachedDurationUs(&finalStatus);
        *eos = (finalStatus != OK);
        return true;
    }

    return false;
}

void AwesomePlayer::ensureCacheIsFetching_l() {
    if (mCachedSource != NULL) {
        mCachedSource->resumeFetchingIfNecessary();
    }
}

void AwesomePlayer::onVideoLagUpdate() {
    Mutex::Autolock autoLock(mLock);
    if (!mVideoLagEventPending) {
        return;
    }
    
#ifdef MTB_SUPPORT
    ATRACE_ONESHOT(ATRACE_ONESHOT_EVENT, "onVideoLagUpdate");
#endif

    mVideoLagEventPending = false;

    int64_t audioTimeUs = mAudioPlayer->getMediaTimeUs();
    int64_t videoLateByUs = audioTimeUs - mVideoTimeUs;

    if (!(mFlags & VIDEO_AT_EOS) && videoLateByUs > 300000ll) {
        ALOGV("video late by %lld ms.", videoLateByUs / 1000ll);

        notifyListener_l(
                MEDIA_INFO,
                MEDIA_INFO_VIDEO_TRACK_LAGGING,
                videoLateByUs / 1000ll);
    }

    postVideoLagEvent_l();
}

void AwesomePlayer::onBufferingUpdate() {
    Mutex::Autolock autoLock(mLock);
    if (!mBufferingEventPending) {
        return;
    }

#ifdef MTB_SUPPORT
    ATRACE_ONESHOT(ATRACE_ONESHOT_SPECIAL, "onBufferingUpdate");
#endif

    mBufferingEventPending = false;
#ifndef ANDROID_DEFAULT_CODE
    return onBufferingUpdate_l();
}
#endif

#ifndef ANDROID_DEFAULT_CODE
void AwesomePlayer::onBufferingUpdate_l() {
#endif // #ifndef ANDROID_DEFAULT_CODE

    if (mCachedSource != NULL) {
#ifndef ANDROID_DEFAULT_CODE
        return onBufferingUpdateCachedSource_l();
#endif
        status_t finalStatus;
        size_t cachedDataRemaining = mCachedSource->approxDataRemaining(&finalStatus);
        bool eos = (finalStatus != OK);

        if (eos) {
            if (finalStatus == ERROR_END_OF_STREAM) {
                notifyListener_l(MEDIA_BUFFERING_UPDATE, 100);
            }
            if (mFlags & PREPARING) {
                ALOGV("cache has reached EOS, prepare is done.");
                finishAsyncPrepare_l();
            }
        } else {
            int64_t bitrate;
            if (getBitrate(&bitrate)) {
                size_t cachedSize = mCachedSource->cachedSize();
                int64_t cachedDurationUs = cachedSize * 8000000ll / bitrate;

                int percentage = 100.0 * (double)cachedDurationUs / mDurationUs;
                if (percentage > 100) {
                    percentage = 100;
                }

                notifyListener_l(MEDIA_BUFFERING_UPDATE, percentage);
            } else {
                // We don't know the bitrate of the stream, use absolute size
                // limits to maintain the cache.

                if ((mFlags & PLAYING) && !eos
                        && (cachedDataRemaining < kLowWaterMarkBytes)) {
                    ALOGI("cache is running low (< %d) , pausing.",
                         kLowWaterMarkBytes);
                    modifyFlags(CACHE_UNDERRUN, SET);
                    pause_l();
                    ensureCacheIsFetching_l();
                    sendCacheStats();
                    notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_START);
                } else if (eos || cachedDataRemaining > kHighWaterMarkBytes) {
                    if (mFlags & CACHE_UNDERRUN) {
                        ALOGI("cache has filled up (> %d), resuming.",
                             kHighWaterMarkBytes);
                        modifyFlags(CACHE_UNDERRUN, CLEAR);
                        play_l();
                    } else if (mFlags & PREPARING) {
                        ALOGV("cache has filled up (> %d), prepare is done",
                             kHighWaterMarkBytes);
                        finishAsyncPrepare_l();
                    }
                }
            }
        }
    } else if (mWVMExtractor != NULL) {
        status_t finalStatus;

        int64_t cachedDurationUs
            = mWVMExtractor->getCachedDurationUs(&finalStatus);

        bool eos = (finalStatus != OK);

        if (eos) {
            if (finalStatus == ERROR_END_OF_STREAM) {
                notifyListener_l(MEDIA_BUFFERING_UPDATE, 100);
            }
            if (mFlags & PREPARING) {
                ALOGV("cache has reached EOS, prepare is done.");
                finishAsyncPrepare_l();
            }
        } else {
            int percentage = 100.0 * (double)cachedDurationUs / mDurationUs;
            if (percentage > 100) {
                percentage = 100;
            }

            notifyListener_l(MEDIA_BUFFERING_UPDATE, percentage);
        }
    }

    int64_t cachedDurationUs;
    bool eos;
    if (getCachedDuration_l(&cachedDurationUs, &eos)) {
        ALOGV("cachedDurationUs = %.2f secs, eos=%d",
             cachedDurationUs / 1E6, eos);

        if ((mFlags & PLAYING) && !eos
                && (cachedDurationUs < kLowWaterMarkUs)) {
            modifyFlags(CACHE_UNDERRUN, SET);
                ALOGI("cache is running low (%.2f secs) , pausing.",
                      cachedDurationUs / 1E6);
                pause_l();
                ensureCacheIsFetching_l();
                sendCacheStats();
            notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_START);
        } else if (eos || cachedDurationUs > kHighWaterMarkUs) {
            if (mFlags & CACHE_UNDERRUN) {
                modifyFlags(CACHE_UNDERRUN, CLEAR);
                    ALOGI("cache has filled up (%.2f secs), resuming.",
                          cachedDurationUs / 1E6);
                    play_l();
            } else if (mFlags & PREPARING) {
                ALOGV("cache has filled up (%.2f secs), prepare is done",
                     cachedDurationUs / 1E6);
                finishAsyncPrepare_l();
            }
        }
            }
            
    if (mFlags & (PLAYING | PREPARING | CACHE_UNDERRUN)) {
        postBufferingEvent_l();
        }
    }

#ifndef ANDROID_DEFAULT_CODE
void AwesomePlayer::onBufferingUpdateCachedSource_l() {
    status_t finalStatus;
    size_t cachedDataRemaining = mCachedSource->approxDataRemaining(&finalStatus);
    bool eos = (finalStatus != OK);

#ifndef ANDROID_DEFAULT_CODE //for HTTP Streaming
	if (mStopped) {
		ALOGD("I'm stopped, exit on buffering");
		if (mFlags & PREPARING) {
		    ALOGI("I'm stopped reset and finish Prepare");
		    finishAsyncPrepare_l();
		}
		return;
	}

    if (eos && (finalStatus != ERROR_END_OF_STREAM))
    {
      ALOGD("Notify, onBufferingUpdateCachedSource_l, finalStatus=%d", finalStatus);
    	notifyListener_l(MEDIA_ERROR, finalStatus, 0);
    }

   if (mFlags & CACHE_MISSING) {
        //TODO: to update buffer in current seek time
        if (cachedDataRemaining > 0) {
           ALOGI("cache is shot again, mSeeking = %d", (int)mSeeking);
            if (mVideoSource != NULL) {
                //recover omxcodec
				//mVideoSource->start();
			  ALOGD("video resume");
			  reinterpret_cast<OMXCodec *>(mVideoSource.get())->resume(); 

                if (mSeeking != NO_SEEK) {
		      ALOGD("set SEEK_PREVIEW when cache miss");
                    modifyFlags(SEEK_PREVIEW, SET);
                }

                if (mFlags & PLAYING) {
                    //the CACHE_MISSING flag will reset in video event
                    //the reason is that the video event may complete the pending seek
                    postVideoEvent_l();
                    //buffering event will be activated after CACHE_MISSING reset
                } else {
                    modifyFlags(CACHE_MISSING, CLEAR);
                    postBufferingEvent_l();
                   ALOGD("CACHE_MISSING reset in BufferingEvent");
                }
                return;
            }
       }

        postBufferingEvent_l();
        return;
    }
#endif

    int64_t bitrate = 0;
    bool bitrateAvailable = false;

    //
    //update percent
    if (eos) {
        //check if network failed
        if (finalStatus == ERROR_END_OF_STREAM) {
            notifyListener_l(MEDIA_BUFFERING_UPDATE, 100);
        }

        if (mFlags & PREPARING) {
           ALOGD("cache has reached EOS, prepare is done.");
            finishAsyncPrepare_l();
        }
    } else {
        bitrateAvailable = getBitrate(&bitrate);
        if (bitrateAvailable) {
            size_t cachedSize = mCachedSource->cachedSize();
            int64_t cachedDurationUs = cachedSize * 8000000ll / bitrate;

            int percentage = 100.0 * (double)cachedDurationUs / mDurationUs;
            if (percentage > 100) {
                percentage = 100;
            }

            notifyListener_l(MEDIA_BUFFERING_UPDATE, percentage);
        } else {
            // We don't know the bitrate of the stream, use absolute size
            // limits to maintain the cache.

            if ((mFlags & PLAYING) && !eos
                    && (cachedDataRemaining < kLowWaterMarkBytes)) {
               ALOGI("cache is running low (< %d) , pausing.",
                        kLowWaterMarkBytes);
                modifyFlags(CACHE_UNDERRUN, SET);
                pause_l();
                ensureCacheIsFetching_l();
                sendCacheStats();
                notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_START);
            } else if (eos || cachedDataRemaining > kHighWaterMarkBytes) {
                if (mFlags & CACHE_UNDERRUN) {
                   ALOGI("cache has filled up (> %d), resuming.",
                            kHighWaterMarkBytes);
                    modifyFlags(CACHE_UNDERRUN, CLEAR);
                    play_l();
                    notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_END);
                } else if (mFlags & PREPARING) {
                   ALOGV("cache has filled up (> %d), prepare is done",
                            kHighWaterMarkBytes);
                    finishAsyncPrepare_l();
                }
            }
        }
    }
    int64_t cachedDurationUs;
    if (getCachedDuration_l(&cachedDurationUs, &eos)) {
       ALOGV("cachedDurationUs = %.2f secs, eos=%d",
                cachedDurationUs / 1E6, eos);

        int64_t highWaterMarkUs = mHighWaterMarkUs;
        /*if (!mSeekNotificationSent) {
            highWaterMarkUs = kLowWaterMarkUs + 100000ll;
            //the seek complete is only done in Audio, 
            //so the if the seek is not complete, we should complete the seek asap:
            //1. trigger audio to complete the seek asap (set highWaterMark a little more than lowWaterMark)
            //2. don't auto-pause until seek completed (not impletemented yet)
        } else {*/
            if (bitrateAvailable) {
                CHECK(mCachedSource.get() != NULL);
                int64_t nMaxCacheDuration = mCachedSource->getMaxCacheSize() * 8000000ll / bitrate;
                if (nMaxCacheDuration < highWaterMarkUs) {
                    //ALOGV("highwatermark = %lld, cache maxduration = %lld", highWaterMarkUs, nMaxCacheDuration);
                    highWaterMarkUs = nMaxCacheDuration;
                }
            }
        //}

        if ((mFlags & PLAYING) && !eos
                && (cachedDurationUs < kLowWaterMarkUs)) {
           ALOGI("cache is running low (%.2f secs), pausing.",
                    cachedDurationUs / 1E6);
            modifyFlags(CACHE_UNDERRUN, SET);
            pause_l();
            ensureCacheIsFetching_l();
            sendCacheStats();
            notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_START);
        } else if (eos || cachedDurationUs > highWaterMarkUs) {
            if (mFlags & CACHE_UNDERRUN) {
               ALOGI("cache has filled up (%.2f secs), resuming.",
                        cachedDurationUs / 1E6);
                modifyFlags(CACHE_UNDERRUN, CLEAR);
                play_l();
                notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_END);
            } else if (mFlags & PREPARING) {
               ALOGV("cache has filled up (%.2f secs), prepare is done",
                        cachedDurationUs / 1E6);
                finishAsyncPrepare_l();
            }
        }
    }

    postBufferingEvent_l();

}
#endif
void AwesomePlayer::sendCacheStats() {
    sp<MediaPlayerBase> listener = mListener.promote();
    if (listener != NULL) {
        int32_t kbps = 0;
        status_t err = UNKNOWN_ERROR;
        if (mCachedSource != NULL) {
            err = mCachedSource->getEstimatedBandwidthKbps(&kbps);
        } else if (mWVMExtractor != NULL) {
            err = mWVMExtractor->getEstimatedBandwidthKbps(&kbps);
        }
        if (err == OK) {
            listener->sendEvent(
                MEDIA_INFO, MEDIA_INFO_NETWORK_BANDWIDTH, kbps);
        }
    }
}

void AwesomePlayer::onStreamDone() {
    // Posted whenever any stream finishes playing.
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("onStreamDone:mStreamDoneStatus =%d,video EOS=%d,audio EOS=%d",
    			mStreamDoneStatus, mFlags & VIDEO_AT_EOS,mFlags & AUDIO_AT_EOS);
#endif
    ATRACE_CALL();

    Mutex::Autolock autoLock(mLock);
    if (!mStreamDoneEventPending) {
        return;
    }

#ifdef MTB_SUPPORT
    ATRACE_ONESHOT(ATRACE_ONESHOT_EVENT, "onStreamDone");
#endif

    mStreamDoneEventPending = false;

    if (mStreamDoneStatus != ERROR_END_OF_STREAM) {
        ALOGV("MEDIA_ERROR %d", mStreamDoneStatus);

#ifndef ANDROID_DEFAULT_CODE
        if (mStreamDoneStatus == ERROR_UNSUPPORTED) {
            // report unsupport for new Gallery[3D]
            notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_TYPE_NOT_SUPPORTED);
        } else if ((mStreamDoneStatus == ERROR_CANNOT_CONNECT) || (mStreamDoneStatus == ERROR_CONNECTION_LOST)) {
            notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_CANNOT_CONNECT_TO_SERVER);
        } else {
            // report bad file for new Gallery[3D] if error occurs
            // FIXME there may be other errors than bad file
            if(mVideoSource == NULL)
			{
				if(mStreamDoneStatus==ERROR_UNSUPPORTED_AUDIO)
				{
					notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_TYPE_NOT_SUPPORTED, mStreamDoneStatus);
				}
				else
                	notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, mStreamDoneStatus);
            }
            else
                notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_BAD_FILE, mStreamDoneStatus);
        }
#else
        notifyListener_l(
                MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, mStreamDoneStatus);
#endif // ifndef ANDROID_DEFAULT_CODE

        pause_l(true /* at eos */);

        modifyFlags(AT_EOS, SET);
        return;
    }

    const bool allDone =
        (mVideoSource == NULL || (mFlags & VIDEO_AT_EOS))
            && (mAudioSource == NULL || (mFlags & AUDIO_AT_EOS));

    if (!allDone) {
        return;
    }
#ifndef ANDROID_DEFAULT_CODE
	if(allDone &&  mFinalStopFlag ==(FINAL_HAS_UNSUPPORT_VIDEO|FINAL_HAS_UNSUPPORT_AUDIO))
      {
		notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_TYPE_NOT_SUPPORTED);
	      pause_l(true /* at eos */);		  
            modifyFlags(AT_EOS, SET);
	      mFinalStopFlag=0;
		ALOGE("AT_EOS mFinalStopFlag=3");
            return;
	}
#endif
	

    if ((mFlags & LOOPING)
            || ((mFlags & AUTO_LOOPING)
                && (mAudioSink == NULL || mAudioSink->realtime()))) {
        // Don't AUTO_LOOP if we're being recorded, since that cannot be
        // turned off and recording would go on indefinitely.

        seekTo_l(0);

        if (mVideoSource != NULL) {
            postVideoEvent_l();
        }
    } else {
        ALOGV("MEDIA_PLAYBACK_COMPLETE");
#ifndef ANDROID_DEFAULT_CODE
	 modifyFlags(EOS_HANDLING, SET);//CR:ALPS00405840
#endif
        notifyListener_l(MEDIA_PLAYBACK_COMPLETE);

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
        mIsCurrentComplete = true;
#endif
	 modifyFlags(CACHE_UNDERRUN, CLEAR);
        modifyFlags(CACHE_MISSING, CLEAR);//as the buffering thread is 200ms update once. when stream done. the cache status not chage, casue isPlaying return error

#endif // #ifndef ANDROID_DEFAULT_CODE
        pause_l(true /* at eos */);

        // If audio hasn't completed MEDIA_SEEK_COMPLETE yet,
        // notify MEDIA_SEEK_COMPLETE to observer immediately for state persistence.
        if (mWatchForAudioSeekComplete) {
            notifyListener_l(MEDIA_SEEK_COMPLETE);
            mWatchForAudioSeekComplete = false;
        }

        modifyFlags(AT_EOS, SET);
#ifndef ANDROID_DEFAULT_CODE
	 modifyFlags(EOS_HANDLING, CLEAR);
#endif
    }
}

status_t AwesomePlayer::play() {
    ATRACE_CALL();
#ifndef ANDROID_DEFAULT_CODE //for HTTP Streaming
	ALOGI("play ");
    if ((mCachedSource != NULL) && (mCachedSourcePauseResponseState & PausePending))
    {
    
        mCachedSourcePauseResponseState &= ~PausePending;
       ALOGD("play return because mCachedSource PausePending %x", mCachedSourcePauseResponseState);
        return OK;
    }
#endif
    Mutex::Autolock autoLock(mLock);

    modifyFlags(CACHE_UNDERRUN, CLEAR);

    return play_l();
}

status_t AwesomePlayer::play_l() {
   ALOGI("play_l:mFlags=0x%x",mFlags);
    modifyFlags(SEEK_PREVIEW, CLEAR);
#ifdef MTK_CLEARMOTION_SUPPORT
    modifyFlags(PAUSING, CLEAR);
    mMJCPauseDelay = false;
#endif     

    if (mFlags & PLAYING) {
        return OK;
    }

    mMediaRenderingStartGeneration = ++mStartGeneration;

    if (!(mFlags & PREPARED)) {
        status_t err = prepare_l();

        if (err != OK) {
            return err;
        }
    }

    modifyFlags(PLAYING, SET);
    modifyFlags(FIRST_FRAME, SET);

#ifndef ANDROID_DEFAULT_CODE
    if (mCachedSource != NULL) {
        // check cache before playing
        onBufferingUpdate_l();
        if ((mFlags & CACHE_UNDERRUN) || (mFlags & CACHE_MISSING)) {
            return OK;
        }
    }
#endif // #ifndef ANDROID_DEFAULT_CODE
    if (mDecryptHandle != NULL) {
        int64_t position;
        getPosition(&position);
        mDrmManagerClient->setPlaybackStatus(mDecryptHandle,
                Playback::START, position / 1000);
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
        // OMA DRM v1 implementation, when the playback is done and position comes to 0, consume rights.
        if (mIsCurrentComplete && position == 0) { // single recursive mode
            ALOGD("AwesomePlayer, consumeRights @play_l()");
            // in some cases, the mFileSource may be NULL (E.g. play audio directly in File Manager)
            // We don't know, but we assume it's a OMA DRM v1 case (DecryptApiType::CONTAINER_BASED)
            if ((mFileSource.get() != NULL && (mFileSource->flags() & OMADrmFlag) != 0)
                || (DecryptApiType::CONTAINER_BASED == mDecryptHandle->decryptApiType)) {
                if (!DrmMtkUtil::isTrustedVideoClient(mDrmValue)) {
                    mDrmManagerClient->consumeRights(mDecryptHandle, Action::PLAY, false);
                }
            }
            mIsCurrentComplete = false;
        }
#endif
#endif // #ifndef ANDROID_DEFAULT_CODE
    }

    if (mAudioSource != NULL) {
        if (mAudioPlayer == NULL) {
            createAudioPlayer_l();
        }

        CHECK(!(mFlags & AUDIO_RUNNING));

        if (mVideoSource == NULL) {

            // We don't want to post an error notification at this point,
            // the error returned from MediaPlayer::start() will suffice.
#ifndef ANDROID_DEFAULT_CODE
	    ALOGI("play_l:startAudioPlayer_l");
#endif
            status_t err = startAudioPlayer_l(
                    false /* sendErrorNotification */);

            if ((err != OK) && mOffloadAudio) {
                ALOGI("play_l() cannot create offload output, fallback to sw decode");
                int64_t curTimeUs;
                getPosition(&curTimeUs);

                delete mAudioPlayer;
                mAudioPlayer = NULL;
                // if the player was started it will take care of stopping the source when destroyed
                if (!(mFlags & AUDIOPLAYER_STARTED)) {
                    mAudioSource->stop();
                }
                modifyFlags((AUDIO_RUNNING | AUDIOPLAYER_STARTED), CLEAR);
                mOffloadAudio = false;
                mAudioSource = mOmxSource;
                if (mAudioSource != NULL) {
                    err = mAudioSource->start();

            if (err != OK) {
                        mAudioSource.clear();
                    } else {
                        mSeekNotificationSent = true;
                        if (mExtractorFlags & MediaExtractor::CAN_SEEK) {
                            seekTo_l(curTimeUs);
                        }
                        createAudioPlayer_l();
                        err = startAudioPlayer_l(false);
                    }
                }
            }

            if (err != OK) {
                delete mAudioPlayer;
                mAudioPlayer = NULL;

                modifyFlags((PLAYING | FIRST_FRAME), CLEAR);

                if (mDecryptHandle != NULL) {
                    mDrmManagerClient->setPlaybackStatus(
                            mDecryptHandle, Playback::STOP, 0);
                }

                return err;
            }
        }
    }

    if (mTimeSource == NULL && mAudioPlayer == NULL) {
        mTimeSource = &mSystemTimeSource;
    }

    if (mVideoSource != NULL) {
        // Kick off video playback
        postVideoEvent_l();

        if (mAudioSource != NULL && mVideoSource != NULL) {
            postVideoLagEvent_l();
        }
    }

    if (mFlags & AT_EOS) {
        // Legacy behaviour, if a stream finishes playing and then
        // is started again, we play from the start...
        seekTo_l(0);
    }

    uint32_t params = IMediaPlayerService::kBatteryDataCodecStarted
        | IMediaPlayerService::kBatteryDataTrackDecoder;
    if ((mAudioSource != NULL) && (mAudioSource != mAudioTrack)) {
        params |= IMediaPlayerService::kBatteryDataTrackAudio;
    }
    if (mVideoSource != NULL) {
        params |= IMediaPlayerService::kBatteryDataTrackVideo;
#ifndef ANDROID_DEFAULT_CODE
    	{
    	      if (reinterpret_cast<OMXCodec *>(mVideoSource.get())->
				vDecSwitchBwTVout(false) != OK)
			ALOGE("play:set vDecSwitchBwTVout error");
	}		
#endif
    }
    addBatteryData(params);

    if (isStreamingHTTP()) {
        postBufferingEvent_l();
    }

    return OK;
}

void AwesomePlayer::createAudioPlayer_l()
{
    uint32_t flags = 0;
    int64_t cachedDurationUs;
    bool eos;

    if (mOffloadAudio) {
        flags |= AudioPlayer::USE_OFFLOAD;
    } else if (mVideoSource == NULL
            && (mDurationUs > AUDIO_SINK_MIN_DEEP_BUFFER_DURATION_US ||
            (getCachedDuration_l(&cachedDurationUs, &eos) &&
            cachedDurationUs > AUDIO_SINK_MIN_DEEP_BUFFER_DURATION_US))) {
        flags |= AudioPlayer::ALLOW_DEEP_BUFFERING;
    }
    if (isStreamingHTTP()) {
        flags |= AudioPlayer::IS_STREAMING;
    }
    if (mVideoSource != NULL) {
        flags |= AudioPlayer::HAS_VIDEO;
    }

    mAudioPlayer = new AudioPlayer(mAudioSink, flags, this);
    mAudioPlayer->setSource(mAudioSource);

#ifndef ANDROID_DEFAULT_CODE
                // set before seekAudioIfNecessary_l, or seek will not callback
                mWatchForAudioSeekComplete = false;
#endif // #ifndef ANDROID_DEFAULT_CODE
    mTimeSource = mAudioPlayer;

    // If there was a seek request before we ever started,
    // honor the request now.
    // Make sure to do this before starting the audio player
    // to avoid a race condition.
    seekAudioIfNecessary_l();
}

void AwesomePlayer::notifyIfMediaStarted_l() {
    if (mMediaRenderingStartGeneration == mStartGeneration) {
        mMediaRenderingStartGeneration = -1;
        notifyListener_l(MEDIA_STARTED);
    }
}

status_t AwesomePlayer::startAudioPlayer_l(bool sendErrorNotification) {
    CHECK(!(mFlags & AUDIO_RUNNING));
    status_t err = OK;

    if (mAudioSource == NULL || mAudioPlayer == NULL) {
        return OK;
    }

    if (mOffloadAudio) {
        mQueue.cancelEvent(mAudioTearDownEvent->eventID());
        mAudioTearDownEventPending = false;
    }

    if (!(mFlags & AUDIOPLAYER_STARTED)) {
        bool wasSeeking = mAudioPlayer->isSeeking();

        // We've already started the MediaSource in order to enable
        // the prefetcher to read its data.
        err = mAudioPlayer->start(
                true /* sourceAlreadyStarted */);

        if (err != OK) {
            if (sendErrorNotification) {
                notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, err);
            }

            return err;
        }

        modifyFlags(AUDIOPLAYER_STARTED, SET);
#ifndef ANDROID_DEFAULT_CODE
        mLatencyUs = -mAudioPlayer->getRealTimeUs();
        if (mVideoSource == NULL || mLatencyUs < 0)
            mLatencyUs = 0;
        ALOGI("AudioPlayer mLatencyUs %lld", mLatencyUs);
#endif

        if (wasSeeking) {
            CHECK(!mAudioPlayer->isSeeking());

            // We will have finished the seek while starting the audio player.
            postAudioSeekComplete();
        } else {
            notifyIfMediaStarted_l();
        }
    } else {
        err = mAudioPlayer->resume();
    }

    if (err == OK) {
    modifyFlags(AUDIO_RUNNING, SET);

    mWatchForAudioEOS = true;
    }

    return err;
}

void AwesomePlayer::notifyVideoSize_l() {
    ATRACE_CALL();
    sp<MetaData> meta = mVideoSource->getFormat();

    int32_t cropLeft, cropTop, cropRight, cropBottom;
    if (!meta->findRect(
                kKeyCropRect, &cropLeft, &cropTop, &cropRight, &cropBottom)) {
        int32_t width, height;
        CHECK(meta->findInt32(kKeyWidth, &width));
        CHECK(meta->findInt32(kKeyHeight, &height));

        cropLeft = cropTop = 0;
        cropRight = width - 1;
        cropBottom = height - 1;

        ALOGV("got dimensions only %d x %d", width, height);
    } else {
        ALOGV("got crop rect %d, %d, %d, %d",
             cropLeft, cropTop, cropRight, cropBottom);
    }

    int32_t displayWidth;
    if (meta->findInt32(kKeyDisplayWidth, &displayWidth)) {
        ALOGV("Display width changed (%d=>%d)", mDisplayWidth, displayWidth);
        mDisplayWidth = displayWidth;
    }
    int32_t displayHeight;
    if (meta->findInt32(kKeyDisplayHeight, &displayHeight)) {
        ALOGV("Display height changed (%d=>%d)", mDisplayHeight, displayHeight);
        mDisplayHeight = displayHeight;
    }

    int32_t usableWidth = cropRight - cropLeft + 1;
    int32_t usableHeight = cropBottom - cropTop + 1;
    if (mDisplayWidth != 0) {
        usableWidth = mDisplayWidth;
    }
    if (mDisplayHeight != 0) {
        usableHeight = mDisplayHeight;
    }

#ifndef ANDROID_DEFAULT_CODE
    if (mDisplayWidth == 0 || mDisplayHeight == 0) { // in case there is no resolution info in the container
        int32_t videoAspectRatioWidth;
        int32_t videoAspectRatioHeight;
        if (!meta->findInt32(kKeyAspectRatioWidth, &videoAspectRatioWidth)) {
            ALOGE ("Cannot find kKeyAspectRatioWidth");
            videoAspectRatioWidth = 1;
        }
        if (!meta->findInt32(kKeyAspectRatioHeight, &videoAspectRatioHeight)) {
            ALOGE ("Cannot find kKeyAspectRatioHeight");
            videoAspectRatioHeight = 1;
        }
        usableWidth = usableWidth * videoAspectRatioWidth;
        usableHeight = usableHeight * videoAspectRatioHeight;
        ALOGI ("videoAspectRatioWidth(%d), videoAspectRatioHeight(%d), usableWidth(%d), usableHeight(%d)", videoAspectRatioWidth, videoAspectRatioHeight, usableWidth, usableHeight);
   }
#endif
    {
        Mutex::Autolock autoLock(mStatsLock);
        mStats.mVideoWidth = usableWidth;
        mStats.mVideoHeight = usableHeight;
    }

    int32_t rotationDegrees;
    if (!mVideoTrack->getFormat()->findInt32(
                kKeyRotation, &rotationDegrees)) {
        rotationDegrees = 0;
    }

    if (rotationDegrees == 90 || rotationDegrees == 270) {
        notifyListener_l(
                MEDIA_SET_VIDEO_SIZE, usableHeight, usableWidth);
    } else {
        notifyListener_l(
                MEDIA_SET_VIDEO_SIZE, usableWidth, usableHeight);
    }
}

void AwesomePlayer::initRenderer_l() {
    ATRACE_CALL();

    if (mNativeWindow == NULL) {
        return;
    }

    sp<MetaData> meta = mVideoSource->getFormat();

    int32_t format;
    const char *component;
    int32_t decodedWidth, decodedHeight;
    CHECK(meta->findInt32(kKeyColorFormat, &format));
    CHECK(meta->findCString(kKeyDecoderComponent, &component));
    CHECK(meta->findInt32(kKeyWidth, &decodedWidth));
    CHECK(meta->findInt32(kKeyHeight, &decodedHeight));

    int32_t rotationDegrees;
    if (!mVideoTrack->getFormat()->findInt32(
                kKeyRotation, &rotationDegrees)) {
        rotationDegrees = 0;
    }

    mVideoRenderer.clear();

    // Must ensure that mVideoRenderer's destructor is actually executed
    // before creating a new one.
    IPCThreadState::self()->flushCommands();

    // Even if set scaling mode fails, we will continue anyway
    setVideoScalingMode_l(mVideoScalingMode);
    if (USE_SURFACE_ALLOC
            && !strncmp(component, "OMX.", 4)
            && strncmp(component, "OMX.google.", 11)) {
        // Hardware decoders avoid the CPU color conversion by decoding
        // directly to ANativeBuffers, so we must use a renderer that
        // just pushes those buffers to the ANativeWindow.
        mVideoRenderer =
            new AwesomeNativeWindowRenderer(mNativeWindow, rotationDegrees);
    } else {
        // Other decoders are instantiated locally and as a consequence
        // allocate their buffers in local address space.  This renderer
        // then performs a color conversion and copy to get the data
        // into the ANativeBuffer.
        mVideoRenderer = new AwesomeLocalRenderer(mNativeWindow, meta);
    }
}

#ifndef ANDROID_DEFAULT_CODE
status_t AwesomePlayer::pause(bool stop) {
#else
status_t AwesomePlayer::pause() {
#endif // #ifndef ANDROID_DEFAULT_CODE
    ATRACE_CALL();
#ifndef ANDROID_DEFAULT_CODE //for HTTP Streaming
    //in http streaming, the mLock may be busy in onVideoEvent, before aquire it:
    //1. stop the cachedsource
    //or 2. aquire it with timeout    
    if (mCachedSource != NULL) { 
        if (stop) {
            disconnectSafeIfNeccesary();
//            mCachedSource->stop();//stop the cache and aquire lock in normal
           ALOGD("pause: stop cachedsource");
        } else {
            //work around alps00072030: if Pause is already timeout, wait only 1 ms to avoid ANR
            uint32_t nWaitTime = (mCachedSourcePauseResponseState & PauseTimeOut) ? 1 : 6000; 
            //status_t status = mLock.timedlock(nWaitTime);
            
            status_t status=EBUSY;
            uint32_t sleep_scale=(nWaitTime==1)?500:10000;  //500us or 10ms
            uint32_t sleep_time=0;
            bool timeout=false;
            while(!timeout)
            {
            		status = mLock.tryLock(); 
            		if(status==OK)
            		{
            				break;		
            		}
            		else
            		{
            				usleep (sleep_scale);
            				sleep_time += sleep_scale;
            				if(sleep_time>nWaitTime*1000) timeout=true;
            		}
            			
            }
            
            if (status != OK) {
                mCachedSourcePauseResponseState = (PauseTimeOut | PausePending);
               ALOGI("pause: aquire lock failed(%d), set pause pending flag %x,sleep_time=%d", status, mCachedSourcePauseResponseState,sleep_time);
                return OK;
            }
            else if (mFlags & CACHE_MISSING) {
                mCachedSourcePauseResponseState = PausePending;
               ALOGD("pause: pending because CACHE_MISSING");
                mLock.unlock();
                return OK;
            }
            else {
                mCachedSourcePauseResponseState = 0;
                modifyFlags(CACHE_UNDERRUN, CLEAR);
               ALOGD("pause: aquire lock success");
                status = pause_l();
                mLock.unlock();
                return status;
            }
        }
    }
#endif 

#ifndef ANDROID_DEFAULT_CODE
    // give a chance to let APacketSource return from read
    if (stop) {
        Mutex::Autolock autoLock(mMiscStateLock);
       if (mExtractor != NULL) {
            ALOGI("stop extractor in reset");
            mExtractor->stopParsing();
        } else {
            ALOGI("set flag for stopped");
            mStopped = true;
        }
            }
#endif // #ifndef ANDROID_DEFAULT_CODE

    Mutex::Autolock autoLock(mLock);
#ifndef ANDROID_DEFAULT_CODE
    if ((mFlags & CACHE_UNDERRUN)) {
        ALOGI("pausing when buffering, notify 100 for AP");
        notifyListener_l(MEDIA_BUFFERING_UPDATE, 100);
        notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_END);
	// mtk80902: ALPS00428038
	// ap send a pause before buffering's pause, now ap is
	// waiting for pause response.
	    }
#endif

    modifyFlags(CACHE_UNDERRUN, CLEAR);

    return pause_l();
}

status_t AwesomePlayer::pause_l(bool at_eos) {
#ifndef ANDROID_DEFAULT_CODE	
	ALOGD("pause_l :at_eos=%d",at_eos);
#endif		
    if (!(mFlags & PLAYING)) {
        if (mAudioTearDown && mAudioTearDownWasPlaying) {
            ALOGV("pause_l() during teardown and finishSetDataSource_l() mFlags %x" , mFlags);
            mAudioTearDownWasPlaying = false;
            notifyListener_l(MEDIA_PAUSED);
            mMediaRenderingStartGeneration = ++mStartGeneration;
        }
        return OK;
    }

#ifdef MTK_CLEARMOTION_SUPPORT
    modifyFlags(PAUSING, SET);
	ALOGD("[MJC]AwesomePlayer pause_l : MJC delay pause begin");
	if (mVideoRenderingStarted && mMJCPauseDelay) {
    	mMJCCondition.waitRelative(mLock, 300*1000*1000ll); //300ms
	}
	ALOGD("[MJC]AwesomePlayer pause_l : MJC delay pause end");
#endif   

    notifyListener_l(MEDIA_PAUSED);
    mMediaRenderingStartGeneration = ++mStartGeneration;

    cancelPlayerEvents(true /* keepNotifications */);

    if (mAudioPlayer != NULL && (mFlags & AUDIO_RUNNING)) {
            // If we played the audio stream to completion we
            // want to make sure that all samples remaining in the audio
            // track's queue are played out.
        mAudioPlayer->pause(at_eos /* playPendingSamples */);
        // send us a reminder to tear down the AudioPlayer if paused for too long.
        if (mOffloadAudio) {
            postAudioTearDownEvent(kOffloadPauseMaxUs);
        }
        modifyFlags(AUDIO_RUNNING, CLEAR);
    }

    if (mFlags & TEXTPLAYER_INITIALIZED) {
        mTextDriver->pause();
        modifyFlags(TEXT_RUNNING, CLEAR);
    }

    modifyFlags(PLAYING, CLEAR);

    if (mDecryptHandle != NULL) {
        mDrmManagerClient->setPlaybackStatus(mDecryptHandle,
                Playback::PAUSE, 0);
    }

    uint32_t params = IMediaPlayerService::kBatteryDataTrackDecoder;
    if ((mAudioSource != NULL) && (mAudioSource != mAudioTrack)) {
        params |= IMediaPlayerService::kBatteryDataTrackAudio;
    }
    if (mVideoSource != NULL) {
        params |= IMediaPlayerService::kBatteryDataTrackVideo;
#ifndef ANDROID_DEFAULT_CODE
    	{
    	      
		if (reinterpret_cast<OMXCodec *>(mVideoSource.get())->
				vDecSwitchBwTVout(true) != OK)
			ALOGE("pasue:reset vDecSwitchBwTVout error");
	}
#endif
    }

    addBatteryData(params);

    return OK;
}

bool AwesomePlayer::isPlaying() const {
#ifndef ANDROID_DEFAULT_CODE //for HTTP Streaming
    if (mCachedSourcePauseResponseState & PausePending) {
        return false;
    }
#endif
#ifndef ANDROID_DEFAULT_CODE
     //should  wait mLock also in streaming ,else the status will be wrong
      //work around CR ALPS00405840: 
      //onStreamDone thread: EOS-->notify app playback complete--pause_l(true) (in this clear playing flag)
       //               APP thread:             APP recevie notify and call onComplete function-->check isPlaying(), the flag maybe still playing now,error, musci app icon keep pause
     

	if (mCachedSource != NULL ) {//DO NOT use lock in streaming
		//return (mFlags & PLAYING) || (mFlags & CACHE_UNDERRUN);
		return ((mFlags & EOS_HANDLING)? false: ((mFlags & PLAYING) || (mFlags & CACHE_UNDERRUN)));
	}

	Mutex::Autolock autoLock(mLock);
#endif
    return (mFlags & PLAYING) || (mFlags & CACHE_UNDERRUN);
}
#ifndef ANDROID_DEFAULT_CODE
bool AwesomePlayer::isPlaying_l() const {
    if (mCachedSourcePauseResponseState & PausePending) {
        return false;
    }
    return (mFlags & PLAYING) || (mFlags & CACHE_UNDERRUN);
}
#endif

status_t AwesomePlayer::setSurfaceTexture(const sp<IGraphicBufferProducer> &bufferProducer) {
    Mutex::Autolock autoLock(mLock);

    status_t err;
    if (bufferProducer != NULL) {
        err = setNativeWindow_l(new Surface(bufferProducer));
    } else {
        err = setNativeWindow_l(NULL);
    }

    return err;
}

void AwesomePlayer::shutdownVideoDecoder_l() {
#ifndef ANDROID_DEFAULT_CODE
    if (mFirstVideoBuffer) {
        mFirstVideoBuffer->release();
        mFirstVideoBuffer = NULL;
        mFirstVideoBufferStatus = OK;
    }
#endif
    if (mVideoBuffer) {
        mVideoBuffer->release();
        mVideoBuffer = NULL;
    }

    mVideoSource->stop();

    // The following hack is necessary to ensure that the OMX
    // component is completely released by the time we may try
    // to instantiate it again.
    wp<MediaSource> tmp = mVideoSource;
    mVideoSource.clear();
    while (tmp.promote() != NULL) {
        usleep(1000);
    }
    IPCThreadState::self()->flushCommands();
    ALOGV("video decoder shutdown completed");
}

status_t AwesomePlayer::setNativeWindow_l(const sp<ANativeWindow> &native) {
    mNativeWindow = native;

    if (mVideoSource == NULL) {
        return OK;
    }

    ALOGV("attempting to reconfigure to use new surface");

    bool wasPlaying = (mFlags & PLAYING) != 0;

    pause_l();
    mVideoRenderer.clear();

    shutdownVideoDecoder_l();

    status_t err = initVideoDecoder();

    if (err != OK) {
        ALOGE("failed to reinstantiate video decoder after surface change.");
        return err;
    }

    if (mLastVideoTimeUs >= 0) {
//ALPS00108664, using audioTimeUs to replace videoTimeus
#ifndef ANDROID_DEFAULT_CODE
	int64_t position;
    int64_t lastPositionUs = mLastPositionUs;
	getPosition(&position);
	ALOGD("lastPositionUs =%lld, position=%lld",lastPositionUs,position);
	// second getpositon > first getpostion, should seek to first postion,or else CTS fail
	if(lastPositionUs != -1 && position - lastPositionUs < 200*1000
       && position - lastPositionUs >0 ){
                      mSeekTimeUs = lastPositionUs ;			 	
	}	
	else		
                      mSeekTimeUs = position;
        mSeeking = SEEK;
#else  //ANDROID_DEFAULT_CODE
        mSeeking = SEEK;
	mSeekTimeUs = mLastVideoTimeUs;
#endif
        modifyFlags((AT_EOS | AUDIO_AT_EOS | VIDEO_AT_EOS), CLEAR);
    }

    if (wasPlaying) {
        play_l();
    }

    return OK;
}

void AwesomePlayer::setAudioSink(
        const sp<MediaPlayerBase::AudioSink> &audioSink) {
    Mutex::Autolock autoLock(mLock);

    mAudioSink = audioSink;
}

status_t AwesomePlayer::setLooping(bool shouldLoop) {
#ifndef ANDROID_DEFAULT_CODE
    //work around for alps00068192, 
    //the LOOPING flag seems to need not to lock
    bool bLoop = mFlags & LOOPING;
    if (bLoop == shouldLoop)
        return OK;
#endif
    Mutex::Autolock autoLock(mLock);

    modifyFlags(LOOPING, CLEAR);

    if (shouldLoop) {
        modifyFlags(LOOPING, SET);
    }

    return OK;
}

status_t AwesomePlayer::getDuration(int64_t *durationUs) {
    Mutex::Autolock autoLock(mMiscStateLock);

    if (mDurationUs < 0) {
        return UNKNOWN_ERROR;
    }

    *durationUs = mDurationUs;

    return OK;
}

#ifndef ANDROID_DEFAULT_CODE
status_t AwesomePlayer::getPosition(int64_t *positionUs) {
    XLOGV("getPosition seek %d seektime %lld flag %x video %lld", 
            mSeeking, mSeekTimeUs, mFlags, mVideoTimeUs);
    if (mSeeking != NO_SEEK || mWatchForAudioSeekComplete) {
        *positionUs = mSeekTimeUs;
         mLastPositionUs = *positionUs;
        return OK;
    } else if (mVideoSource != NULL
            && (mAudioPlayer == NULL || !(mFlags & VIDEO_AT_EOS))) {
        Mutex::Autolock autoLock(mMiscStateLock);
        *positionUs = mVideoTimeUs;      //+ mLatencyUs;  ALPS00334993
    } else if (mAudioPlayer != NULL) {
        *positionUs = mAudioPlayer->getMediaTimeUs();// + mLatencyUs;
	XLOGV("getPosition  audio %lld", *positionUs);
         if(mFlags&(VIDEO_AT_EOS|AUDIO_AT_EOS)){
			if(mVideoTimeUs>*positionUs){
				*positionUs = mVideoTimeUs;
				ALOGD("audio & video eos");
			}
        }
    } else {
        *positionUs = 0;
    }

    mLastPositionUs = *positionUs;
    return OK;
}
#else
status_t AwesomePlayer::getPosition(int64_t *positionUs) {
    if (mSeeking != NO_SEEK) {
        *positionUs = mSeekTimeUs;
    } else if (mVideoSource != NULL
            && (mAudioPlayer == NULL || !(mFlags & VIDEO_AT_EOS))) {
        Mutex::Autolock autoLock(mMiscStateLock);
        *positionUs = mVideoTimeUs;
    } else if (mAudioPlayer != NULL) {
        *positionUs = mAudioPlayer->getMediaTimeUs();
    } else {
        *positionUs = 0;
    }
    return OK;
}
#endif // ANDROID_DEFAULT_CODE

status_t AwesomePlayer::seekTo(int64_t timeUs) {
    ATRACE_CALL();

    if (mExtractorFlags & MediaExtractor::CAN_SEEK) {
        Mutex::Autolock autoLock(mLock);
        return seekTo_l(timeUs);
    }
#ifndef ANDROID_DEFAULT_CODE
    {
        Mutex::Autolock autoLock(mLock);
        notifyListener_l(MEDIA_SEEK_COMPLETE);
    }
#endif  //#ifndef ANDROID_DEFAULT_CODE
    return OK;
}

status_t AwesomePlayer::seekTo_l(int64_t timeUs) {
    ATRACE_CALL();
#ifndef ANDROID_DEFAULT_CODE
	ALOGD("seekTo_l");
    mInRescheding = false;
#endif // #ifndef ANDROID_DEFAULT_CODE
    if (mFlags & CACHE_UNDERRUN) {
        modifyFlags(CACHE_UNDERRUN, CLEAR);
		ALOGD("play_l in underrun");
        play_l();
    }

    if ((mFlags & PLAYING) && mVideoSource != NULL && (mFlags & VIDEO_AT_EOS)) {
        // Video playback completed before, there's no pending
        // video event right now. In order for this new seek
        // to be honored, we need to post one.

		ALOGD("Video at eos when seek");
        postVideoEvent_l();
    }

    mSeeking = SEEK;
    mSeekNotificationSent = false;
    mSeekTimeUs = timeUs;
    modifyFlags((AT_EOS | AUDIO_AT_EOS | VIDEO_AT_EOS), CLEAR);
#ifndef ANDROID_DEFAULT_CODE //for HTTP Streaming

    if (INFO_TRY_READ_FAIL == tryReadIfNeccessary_l()) {
       ALOGI("try read fail, cache is missing (flag = 0x%x | MISSING)", mFlags);
        modifyFlags(CACHE_MISSING, SET);
        if (mVideoSource != NULL) {
            mVideoSource->pause();  //pause the omxcodec
        }
        if (mFlags & PLAYING) {
             ALOGD("trying read: mFlags = 0x%x", mFlags); 
//            pause_l();
            cancelPlayerEvents(true);
            if (mAudioPlayer != NULL && (mFlags & AUDIOPLAYER_STARTED)) {
				ALOGD("mAudioPlayer->pause()");
				modifyFlags(AUDIO_RUNNING, CLEAR);//should clear here ALPS399981
				mAudioPlayer->pause();
            }            
            if (mAudioSource != NULL) {
                // to avoid jumps in the DataSource offset caused by
                // the audio codec prefretching data from the old locations
                mAudioSource->pause();
            }
        }
    }
#endif

#ifndef ANDROID_DEFAULT_CODE
//for cr:332947, only for local playback!
    	if(mVideoSource != NULL && mAudioSource != NULL 
			&& !isStreamingHTTP() && (mFlags & PLAYING)){
               ALOGD("has video&audio, pause when seeking!"); 
              if (mAudioPlayer != NULL && (mFlags & AUDIOPLAYER_STARTED)) {
    				ALOGD("mAudioPlayer->pause()");
    				modifyFlags(AUDIO_RUNNING, CLEAR);
    				mAudioPlayer->pause();				
              }            
       }
#endif
    if (mFlags & PLAYING) {
        notifyListener_l(MEDIA_PAUSED);
        mMediaRenderingStartGeneration = ++mStartGeneration;
    }

    seekAudioIfNecessary_l();

    if (mFlags & TEXTPLAYER_INITIALIZED) {
        mTextDriver->seekToAsync(mSeekTimeUs);
    }

    if (!(mFlags & PLAYING)) {
        ALOGI("seeking while paused, sending SEEK_COMPLETE notification"
             " immediately.");

#ifndef ANDROID_DEFAULT_CODE
        ALOGI("seeking while paused, sending SEEK_COMPLETE notification"
             " immediately.");
#endif
        notifyListener_l(MEDIA_SEEK_COMPLETE);
        mSeekNotificationSent = true;

#ifndef ANDROID_DEFAULT_CODE //for HTTP Streaming
        if ((mFlags & PREPARED) && mVideoSource != NULL && !(mFlags & CACHE_MISSING)) {
#else
        if ((mFlags & PREPARED) && mVideoSource != NULL) {
#endif
            modifyFlags(SEEK_PREVIEW, SET);
            postVideoEvent_l();
        }
    }

    return OK;
}

void AwesomePlayer::seekAudioIfNecessary_l() {
    if (mSeeking != NO_SEEK && mVideoSource == NULL && mAudioPlayer != NULL) {
#ifndef ANDROID_DEFAULT_CODE
        // reset/set variables before async call
        mWatchForAudioSeekComplete = true;
        mWatchForAudioEOS = true;
        mSeekNotificationSent = false;
       
#endif // #ifndef ANDROID_DEFAULT_CODE
        mAudioPlayer->seekTo(mSeekTimeUs);

#ifdef ANDROID_DEFAULT_CODE
        mWatchForAudioSeekComplete = true;
        mWatchForAudioEOS = true;
#endif // #ifndef ANDROID_DEFAULT_CODE

        if (mDecryptHandle != NULL) {
            mDrmManagerClient->setPlaybackStatus(mDecryptHandle,
                    Playback::PAUSE, 0);
            mDrmManagerClient->setPlaybackStatus(mDecryptHandle,
                    Playback::START, mSeekTimeUs / 1000);
        }
    }
}

void AwesomePlayer::setAudioSource(sp<MediaSource> source) {
    CHECK(source != NULL);

    mAudioTrack = source;
}

void AwesomePlayer::addTextSource_l(size_t trackIndex, const sp<MediaSource>& source) {
    CHECK(source != NULL);

    ALOGE("[PANDA] addTextSource_l, trackindex = %d\n", trackIndex);
    
    if (mTextDriver == NULL) {
        mTextDriver = new TimedTextDriver(mListener);
    }

    mTextDriver->addInBandTextSource(trackIndex, source);
}

status_t AwesomePlayer::initAudioDecoder() {
    ATRACE_CALL();

    sp<MetaData> meta = mAudioTrack->getFormat();

    const char *mime;
    CHECK(meta->findCString(kKeyMIMEType, &mime));
    // Check whether there is a hardware codec for this stream
    // This doesn't guarantee that the hardware has a free stream
    // but it avoids us attempting to open (and re-open) an offload
    // stream to hardware that doesn't have the necessary codec
    audio_stream_type_t streamType = AUDIO_STREAM_MUSIC;
    if (mAudioSink != NULL) {
        streamType = mAudioSink->getAudioStreamType();
    }

    mOffloadAudio = canOffloadStream(meta, (mVideoSource != NULL),
                                     isStreamingHTTP(), streamType);

#ifndef ANDROID_DEFAULT_CODE 
#ifdef MTK_AUDIO_RAW_SUPPORT
			// If offloading we still create a OMX decoder as a fall-back
			// but we don't start it
			mOmxSource = OMXCodec::Create(
                mClient.interface(), mAudioTrack->getFormat(),
                false, // createEncoder
                mAudioTrack);
		
			if (mOffloadAudio) {
				ALOGV("createAudioPlayer: bypass OMX (offload)");
				mAudioSource = mAudioTrack;
			} else {
				mAudioSource = mOmxSource;
			}
#else
	if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_RAW)) {
			ALOGV("createAudioPlayer: bypass OMX (raw)");
		mAudioSource = mAudioTrack;
	} else {
			// If offloading we still create a OMX decoder as a fall-back
			// but we don't start it
			mOmxSource = OMXCodec::Create(
			mClient.interface(), mAudioTrack->getFormat(),
			false, // createEncoder
			mAudioTrack);
		
			if (mOffloadAudio) {
				ALOGV("createAudioPlayer: bypass OMX (offload)");
				mAudioSource = mAudioTrack;
			} else {
				mAudioSource = mOmxSource;
			}
	}
#endif
#else
	if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_RAW)) {
			ALOGV("createAudioPlayer: bypass OMX (raw)");
        mAudioSource = mAudioTrack;
    } else {
			// If offloading we still create a OMX decoder as a fall-back
			// but we don't start it
			mOmxSource = OMXCodec::Create(
                mClient.interface(), mAudioTrack->getFormat(),
                false, // createEncoder
                mAudioTrack);
		
			if (mOffloadAudio) {
				ALOGV("createAudioPlayer: bypass OMX (offload)");
				mAudioSource = mAudioTrack;
			} else {
				mAudioSource = mOmxSource;
    }
		}
#endif


    if (mAudioSource != NULL) {
        int64_t durationUs;
        if (mAudioTrack->getFormat()->findInt64(kKeyDuration, &durationUs)) {
            Mutex::Autolock autoLock(mMiscStateLock);
            if (mDurationUs < 0 || durationUs > mDurationUs) {
                mDurationUs = durationUs;
            }
        }

#if !defined(ANDROID_DEFAULT_CODE)
        status_t err;
	  if (isStreamingHTTP()) {
		 sp<MetaData> meta = new MetaData;
	    	 meta->setInt64(kKeyHTTPOutputTimeoutUS, 6000000000); 
	    	 meta->setInt32(kKeyIsHTTPStreaming, 1);       // for omxcode use 
		 if (mExtractor != NULL) {
		     sp<MetaData> fileMeta = mExtractor->getMetaData();
		     const char *mime = NULL;			
		     if (fileMeta != NULL && fileMeta->findCString(kKeyMIMEType, &mime) && !strcasecmp(mime, "audio/x-wav")) { 
			 ALOGI("x-wav max queueBuffer 2");
			 meta->setInt32(kKeyInputBufferNum, 4);
			 meta->setInt32(kKeyMaxQueueBuffer, 2);
		     }
		 }
	    	  err = mAudioSource->start(meta.get()); 
	      			
		} else {
            err = mAudioSource->start();
        }
#else
        status_t err = mAudioSource->start();
#endif // #ifndef ANDROID_DEFAULT_CODE

        if (err != OK) {
            mAudioSource.clear();
            mOmxSource.clear();
            return err;
        }
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_QCELP)) {
        // For legacy reasons we're simply going to ignore the absence
        // of an audio decoder for QCELP instead of aborting playback
        // altogether.
        return OK;
    }

    if (mAudioSource != NULL) {
        Mutex::Autolock autoLock(mStatsLock);
        TrackStat *stat = &mStats.mTracks.editItemAt(mStats.mAudioTrackIndex);
        const char *component;
        if (!mAudioSource->getFormat()
                ->findCString(kKeyDecoderComponent, &component)) {
            component = "none";
        }

        stat->mDecoderName = component;
    }

    return mAudioSource != NULL ? OK : UNKNOWN_ERROR;
}

void AwesomePlayer::setVideoSource(sp<MediaSource> source) {
    CHECK(source != NULL);

    mVideoTrack = source;
}

#ifndef ANDROID_DEFAULT_CODE 
void AwesomePlayer::mtk_omx_get_current_time(int64_t* pReal_time)
{

	//if((mFlags & FIRST_FRAME) || mSeeking!=NO_SEEK )//disable seek video only
	if((mFlags & FIRST_FRAME) || mSeeking == SEEK )
	{
		*pReal_time = -1;
	}
	else
	{
		*pReal_time = mAVSyncTimeUs;
	}
 	//ALOGE("###*pReal_time=%lld us",(*pReal_time));
	
}
#endif 
status_t AwesomePlayer::initVideoDecoder(uint32_t flags) {
    ATRACE_CALL();

    // Either the application or the DRM system can independently say
    // that there must be a hardware-protected path to an external video sink.
    // For now we always require a hardware-protected path to external video sink
    // if content is DRMed, but eventually this could be optional per DRM agent.
    // When the application wants protection, then
    //   (USE_SURFACE_ALLOC && (mSurface != 0) &&
    //   (mSurface->getFlags() & ISurfaceComposer::eProtectedByApp))
    // will be true, but that part is already handled by SurfaceFlinger.

#ifndef ANDROID_DEFAULT_CODE 
	 sp<MetaData> meta = mVideoTrack->getFormat();
	 meta->setPointer(kkeyOmxTimeSource, this);
#endif	

#ifdef DEBUG_HDCP
    // For debugging, we allow a system property to control the protected usage.
    // In case of uninitialized or unexpected property, we default to "DRM only".
    bool setProtectionBit = false;
    char value[PROPERTY_VALUE_MAX];
    if (property_get("persist.sys.hdcp_checking", value, NULL)) {
        if (!strcmp(value, "never")) {
            // nop
        } else if (!strcmp(value, "always")) {
            setProtectionBit = true;
        } else if (!strcmp(value, "drm-only")) {
            if (mDecryptHandle != NULL) {
                setProtectionBit = true;
            }
        // property value is empty, or unexpected value
        } else {
            if (mDecryptHandle != NULL) {
                setProtectionBit = true;
            }
        }
    // can' read property value
    } else {
        if (mDecryptHandle != NULL) {
            setProtectionBit = true;
        }
    }
    // note that usage bit is already cleared, so no need to clear it in the "else" case
    if (setProtectionBit) {
        flags |= OMXCodec::kEnableGrallocUsageProtected;
    }
#else
    if (mDecryptHandle != NULL) {
            flags |= OMXCodec::kEnableGrallocUsageProtected;
    }
#endif
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_CLEARMOTION_SUPPORT        
    if (mEnClearMotion == 1) {
	    flags |= OMXCodec::kUseClearMotion;
		if (mNativeWindow != NULL) {
            mNativeWindow->setSwapInterval(mNativeWindow.get(), 1);
        }
    }  
#endif
#endif
    ALOGD("initVideoDecoder flags=0x%x", flags);
    mVideoSource = OMXCodec::Create(
            mClient.interface(), mVideoTrack->getFormat(),
            false, // createEncoder
            mVideoTrack,
            NULL, flags, USE_SURFACE_ALLOC ? mNativeWindow : NULL);

    if (mVideoSource != NULL) {
        int64_t durationUs;
#ifdef ANDROID_DEFAULT_CODE
        // set when video is ok
        if (mVideoTrack->getFormat()->findInt64(kKeyDuration, &durationUs)) {
            Mutex::Autolock autoLock(mMiscStateLock);
            if (mDurationUs < 0 || durationUs > mDurationUs) {
                mDurationUs = durationUs;
            }
        }
#endif

#ifndef ANDROID_DEFAULT_CODE
        status_t err;
        if ( mCachedSource !=NULL) {
            sp<MetaData> meta = new MetaData;
            //meta->setInt32(kKeyInputBufferNum, 4);
		  if (isStreamingHTTP()) {
	   // 				meta->setInt64(kKeyHTTPOutputTimeoutUS, 6000000000); // mtk80366:  
	    	 meta->setInt32(kKeyIsHTTPStreaming, 1);       // for omxcode use 
	    			}
            meta->setInt32(kKeyMaxQueueBuffer, 1);
            err = mVideoSource->start(meta.get());
        } else {
            err = mVideoSource->start();
        }
        if (mCachedSource == NULL && err == OK && mMetaData != NULL) {
            int check = false;
            if (mMetaData->findInt32(kKeyVideoPreCheck, &check) && check) {
                err = mVideoSource->read(&mFirstVideoBuffer);
                ALOGI("detect video capability by decoder %d %d", err, mFirstVideoBuffer != NULL);
                mFirstVideoBufferStatus = err;
                if (err == INFO_FORMAT_CHANGED || err == ERROR_END_OF_STREAM) {
                    err = OK;
                } else if (err != OK) {
                    shutdownVideoDecoder_l();
                }
            }
        }
#else
        status_t err = mVideoSource->start();
#endif // #ifndef ANDROID_DEFAULT_CODE

        if (err != OK) {
            ALOGE("failed to start video source");
            mVideoSource.clear();
            return err;
        }
#ifndef ANDROID_DEFAULT_CODE
        if (mVideoTrack->getFormat()->findInt64(kKeyDuration, &durationUs)) {
            Mutex::Autolock autoLock(mMiscStateLock);
            if (mDurationUs < 0 || durationUs > mDurationUs) {
                mDurationUs = durationUs;
            }
    }
#endif
    }

    if (mVideoSource != NULL) {
        const char *componentName;
        CHECK(mVideoSource->getFormat()
                ->findCString(kKeyDecoderComponent, &componentName));

        {
            Mutex::Autolock autoLock(mStatsLock);
            TrackStat *stat = &mStats.mTracks.editItemAt(mStats.mVideoTrackIndex);

            stat->mDecoderName = componentName;
        }
#ifndef ANDROID_DEFAULT_CODE
        OMXCodec::findCodecQuirks(componentName, &mVdecQuirks);
#endif

        static const char *kPrefix = "OMX.Nvidia.";
        static const char *kSuffix = ".decode";
        static const size_t kSuffixLength = strlen(kSuffix);

        size_t componentNameLength = strlen(componentName);

        if (!strncmp(componentName, kPrefix, strlen(kPrefix))
                && componentNameLength >= kSuffixLength
                && !strcmp(&componentName[
                    componentNameLength - kSuffixLength], kSuffix)) {
            modifyFlags(SLOW_DECODER_HACK, SET);
        }
#ifndef ANDROID_DEFAULT_CODE		
		modifyFlags(SLOW_DECODER_HACK, SET);
#endif
    }
	
    return mVideoSource != NULL ? OK : UNKNOWN_ERROR;
}

void AwesomePlayer::finishSeekIfNecessary(int64_t videoTimeUs) {
    ATRACE_CALL();

    if (mSeeking == SEEK_VIDEO_ONLY) {
        mSeeking = NO_SEEK;
        return;
    }

    if (mSeeking == NO_SEEK || (mFlags & SEEK_PREVIEW)) {
        return;
    }

    // If we paused, then seeked, then resumed, it is possible that we have
    // signaled SEEK_COMPLETE at a copmletely different media time than where
    // we are now resuming.  Signal new position to media time provider.
    // Cannot signal another SEEK_COMPLETE, as existing clients may not expect
    // multiple SEEK_COMPLETE responses to a single seek() request.
    if (mSeekNotificationSent && abs(mSeekTimeUs - videoTimeUs) > 10000) {
        // notify if we are resuming more than 10ms away from desired seek time
        notifyListener_l(MEDIA_SKIPPED);
    }

    if (mAudioPlayer != NULL) {
        ALOGI("seeking audio to %lld us (%.2f secs).", videoTimeUs, videoTimeUs / 1E6);

        // If we don't have a video time, seek audio to the originally
        // requested seek time instead.

#ifndef ANDROID_DEFAULT_CODE
        // reset/set variables before async call
        mWatchForAudioSeekComplete = true;
        mWatchForAudioEOS = true;
#endif // #ifndef ANDROID_DEFAULT_CODE
        mAudioPlayer->seekTo(videoTimeUs < 0 ? mSeekTimeUs : videoTimeUs);
#ifdef ANDROID_DEFAULT_CODE
        mWatchForAudioSeekComplete = true;
        mWatchForAudioEOS = true;
#endif // #ifndef ANDROID_DEFAULT_CODE
    } else if (!mSeekNotificationSent) {
        // If we're playing video only, report seek complete now,
        // otherwise audio player will notify us later.
        notifyListener_l(MEDIA_SEEK_COMPLETE);
        mSeekNotificationSent = true;
    }

    modifyFlags(FIRST_FRAME, SET);
    mSeeking = NO_SEEK;
    ALOGD("finishSeekIfNecessary:mSeeking =0 ");

    if (mDecryptHandle != NULL) {
        mDrmManagerClient->setPlaybackStatus(mDecryptHandle,
                Playback::PAUSE, 0);
        mDrmManagerClient->setPlaybackStatus(mDecryptHandle,
                Playback::START, videoTimeUs / 1000);
    }
}

void AwesomePlayer::onVideoEvent() {
    ATRACE_CALL();
#ifndef ANDROID_DEFAULT_CODE
    // Avoid of onVideoEvent occpuy mLock all the time. 
    // It would cause that pause() reset() .etc can not get the mLock, and even result in ANR
    usleep(0);
#endif
    Mutex::Autolock autoLock(mLock);
    if (!mVideoEventPending) {
        // The event has been cancelled in reset_l() but had already
        // been scheduled for execution at that time.
        return;
    }

#ifdef MTB_SUPPORT
    ATRACE_ONESHOT(ATRACE_ONESHOT_EVENT, "onVideoEvent");
#endif

    mVideoEventPending = false;

    if (mSeeking != NO_SEEK) {
#ifndef ANDROID_DEFAULT_CODE
        mAudioNormalEOS = false;
        if (mFirstVideoBuffer) {
            mFirstVideoBuffer->release();
            mFirstVideoBuffer = NULL;
        }
        mFirstVideoBufferStatus = OK;
#endif // #ifndef ANDROID_DEFAULT_CODE
        if (mVideoBuffer) {
            mVideoBuffer->release();
            mVideoBuffer = NULL;
        }
	
	ALOGI("Now is SEEK_PREVIEW? %s",(mFlags & SEEK_PREVIEW)?"YES":"No" );
        if (mSeeking == SEEK && isStreamingHTTP() && mAudioSource != NULL
                && !(mFlags & SEEK_PREVIEW)) {
            // We're going to seek the video source first, followed by
            // the audio source.
            // In order to avoid jumps in the DataSource offset caused by
            // the audio codec prefetching data from the old locations
            // while the video codec is already reading data from the new
            // locations, we'll "pause" the audio source, causing it to
            // stop reading input data until a subsequent seek.
		ALOGI("We're going to seek the video source firs,then audio mFlags=0x%x",mFlags);
            if (mAudioPlayer != NULL && (mFlags & AUDIO_RUNNING)) {
                mAudioPlayer->pause();

                modifyFlags(AUDIO_RUNNING, CLEAR);
            }
            mAudioSource->pause();
        }
    }

    if (!mVideoBuffer) {
		MediaSource::ReadOptions options;
#ifndef ANDROID_DEFAULT_CODE 
        bool bSEEK_VIDEO_ONLY=false;
        if (mCachedSource == NULL)
        {
            const char *mime;			
            CHECK(mMetaData->findCString(kKeyMIMEType, &mime));
            if((mSeeking == SEEK_VIDEO_ONLY)&& (!strcasecmp("video/mp4", mime)))
            {
                ALOGD("### mime=%s======SEEK_VIDEO_ONLY now====",mime);
				bSEEK_VIDEO_ONLY =true;
            }
        }

#endif //#ifndef ENABLE_PERF_JUMP_KEY_MECHANISM
#ifndef ANDROID_DEFAULT_CODE
        if (mInRescheding) {
            ALOGI("rescheding reenter");
            mInRescheding = false;
        } else
#endif
        if (mSeeking != NO_SEEK) {
            ALOGI("seeking to %lld us (%.2f secs)", mSeekTimeUs, mSeekTimeUs / 1E6);

            options.setSeekTo(
                    mSeekTimeUs,
#ifndef ANDROID_DEFAULT_CODE
					bSEEK_VIDEO_ONLY
#else
                    mSeeking == SEEK_VIDEO_ONLY
#endif                    
                        ? MediaSource::ReadOptions::SEEK_NEXT_SYNC
#ifndef ANDROID_DEFAULT_CODE
						: (mPrerollEnable ? (MediaSource::ReadOptions::SEEK_CLOSEST) : (MediaSource::ReadOptions::SEEK_CLOSEST_SYNC)));                        
#else
						: MediaSource::ReadOptions::SEEK_CLOSEST_SYNC);
#endif
        }
        for (;;) {
#ifndef ANDROID_DEFAULT_CODE
            status_t err = OK;
            if (mFirstVideoBuffer != NULL) {
                mVideoBuffer = mFirstVideoBuffer;
                err = mFirstVideoBufferStatus;
                mFirstVideoBuffer = NULL;
                mFirstVideoBufferStatus = OK;
                ALOGI("using first video buffer and status %d", mFirstVideoBufferStatus);
            } else {
                err = mVideoSource->read(&mVideoBuffer, &options);
            }
#else
            status_t err = mVideoSource->read(&mVideoBuffer, &options);
#endif
            options.clearSeekTo();

#ifndef ANDROID_DEFAULT_CODE
//wait for 			
            if (mFirstSubmit && (mVdecQuirks & OMXCodec::kDecoderNeedPrebuffer)) 
            {
                char value[PROPERTY_VALUE_MAX];
                sp<MetaData> _meta = mVideoSource->getFormat();
                int32_t _videowidth;
                int32_t _videoheight;
                CHECK(_meta->findInt32(kKeyWidth, &_videowidth));
                CHECK(_meta->findInt32(kKeyHeight, &_videoheight));
                if ((_videowidth <= 864) && (_videoheight <= 480)) {
                    property_get("sf.video.prebuffer.cnt", value, "1");
                } 
                else {
                    property_get("sf.video.prebuffer.cnt", value, "5");
                }
                size_t prebufferCount = atoi(value);
                ALOGD("@@[SF_PROPERTY]sf.video.prebuffer.cnt=%d, VideoWidth(%d), VideoHeight(%d)", prebufferCount, _videowidth, _videoheight);

                size_t buffersOwn;
                int loopCount = 0;
                while ((buffersOwn = ((OMXCodec*)mVideoSource.get())->buffersOwn()) < prebufferCount) {
                //	            ALOGD ("@@buffersOwn = %d", buffersOwn);
                    usleep (10*1000);
                    loopCount++;
                    if (loopCount == 100) {
                        ALOGE ("Oops, prebuffer time > 1s");
                        break;
                    }
                }
                mFirstSubmit = false;
            }
#endif
            if (err != OK) {
                CHECK(mVideoBuffer == NULL);

                if (err == INFO_FORMAT_CHANGED) {
                    ALOGV("VideoSource signalled format change.");

                    notifyVideoSize_l();

                    if (mVideoRenderer != NULL) {
                        mVideoRendererIsPreview = false;
                        initRenderer_l();
                    }
                    continue;
                }

                // So video playback is complete, but we may still have
                // a seek request pending that needs to be applied
                // to the audio track.
                if (mSeeking != NO_SEEK) {
#ifndef ANDROID_DEFAULT_CODE
                    ALOGI("video stream ended while seeking!,mFlags=0x%x",mFlags);
                    //when video EOS, set mVideoTimeUs to avoid of getposition error
                        ALOGI("mSeekTimeUs=%lld,mDurationUs=%lld",mSeekTimeUs,mDurationUs);
                        if (mSeekTimeUs > mDurationUs)
                            mVideoTimeUs = mDurationUs;
                        else
                            mVideoTimeUs = mSeekTimeUs;
#endif
                    ALOGV("video stream ended while seeking!");
                }
                finishSeekIfNecessary(-1);

		if (mAudioPlayer != NULL
                        && !(mFlags & (AUDIO_RUNNING | SEEK_PREVIEW))) {
                        ALOGI("videoEOS after seek, startAudioPlayer_l now");
                    startAudioPlayer_l();
                }

                modifyFlags(VIDEO_AT_EOS, SET);
#ifndef ANDROID_DEFAULT_CODE
                if (err == ERROR_UNSUPPORTED_VIDEO) {
                    ALOGW("unsupportted video detected");
                    if (mAudioTrack != NULL || mAudioSource != NULL) {
                        notifyListener_l(MEDIA_SET_VIDEO_SIZE, 0, 0);
                        notifyListener_l(MEDIA_INFO, MEDIA_INFO_HAS_UNSUPPORT_VIDEO);
                        postStreamDoneEvent_l(ERROR_END_OF_STREAM);
                        mFinalStopFlag |= FINAL_HAS_UNSUPPORT_VIDEO;
                        const char *mime;			
                        //CHECK(mMetaData->findCString(kKeyMIMEType, &mime));
                        if( mMetaData.get()!=NULL && mMetaData->findCString(kKeyMIMEType, &mime)){
	                        if(!strcasecmp(MEDIA_MIMETYPE_CONTAINER_MPEG2TS, mime) || !strcasecmp(MEDIA_MIMETYPE_VIDEO_WMV, mime))
	                        {
	                            mVideoTrack->stop();
	                            ALOGE("stop video track");
	                        }
                      }
                    } 
                    else {
                        postStreamDoneEvent_l(ERROR_UNSUPPORTED);
                    }
                } 
                else {
                    postStreamDoneEvent_l(err);
                }
#else
                postStreamDoneEvent_l(err);
#endif
#ifndef ANDROID_DEFAULT_CODE //for HTTP Streaming
	
		//when seekto end ,and Try read fail. we pasue audio player in seek_l, and set SEEK_PREVIEW in
		//onBufferingUpdateCachedSource_l when cache is shot,
		//when goto here, video is EOS and the audioplayer will never start again. audio has not 
		// chance to execut the seek opration. player won't quit ALPS399981
				


            if (mFlags & CACHE_MISSING) {

                //the cache is shot again
                modifyFlags(CACHE_MISSING, CLEAR);
                if (isPlaying_l()) {
                   ALOGD("EOS CACHE_MISSING --> CACHE_UNDERRUN in VideoEvent,mFlags=0x%x",mFlags);
                    modifyFlags(CACHE_UNDERRUN, SET);
                    pause_l();
					modifyFlags(SEEK_PREVIEW, SET);
                    notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_START);
                } else {
                   ALOGD("CACHE_MISSING reset");
                }
                postBufferingEvent_l();
            }

#endif
                return;
            }

            if (mVideoBuffer->range_length() == 0) {
                // Some decoders, notably the PV AVC software decoder
                // return spurious empty buffers that we just want to ignore.

                mVideoBuffer->release();
                mVideoBuffer = NULL;
                continue;
            }
#ifndef ANDROID_DEFAULT_CODE //for HTTP Streaming
            if (mFlags & CACHE_MISSING) {
                //the cache is shot again
                modifyFlags(CACHE_MISSING, CLEAR);
                if (isPlaying_l()) {
                   ALOGD("CACHE_MISSING --> CACHE_UNDERRUN in VideoEvent,mFlags=0x%x",mFlags);
                    modifyFlags(CACHE_UNDERRUN, SET);
                    pause_l();
                    if (mSeeking == NO_SEEK &&  mCachedSource != NULL) {
			ALOGI("should seek here ,set to SEEK");
			mSeeking = SEEK;
		    }
					modifyFlags(SEEK_PREVIEW, SET);
                    notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_START);
                } else {
                   ALOGD("CACHE_MISSING reset");
                }
                postBufferingEvent_l();
            }
#endif
            break;
        }

        {
            Mutex::Autolock autoLock(mStatsLock);
            ++mStats.mNumVideoFramesDecoded;
        }
    }

#ifndef ANDROID_DEFAULT_CODE //for HTTP Streaming
    if (mCachedSourcePauseResponseState & PausePending) {
        mCachedSourcePauseResponseState = 0; 
        pause_l();
       ALOGI("pending pause done");
        return;
    }
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
	bool SpeedValueChanged = false;
	if((mSMSpeed == 1)||(mSMSpeed == 2)||(mSMSpeed == 4)){
		if(mSpeedValue != mSMSpeed){
			mSpeedValue = mSMSpeed;
			SpeedValueChanged = true;
			ALOGD("mSpeedValue = %d",mSpeedValue);
		}
	}

	if(mSpeedValue ==1){
	int32_t interpolateFrame_check;
		CHECK(mVideoBuffer->meta_data()->findInt32(kKeyInterpolateFrame, &interpolateFrame_check));
		if(interpolateFrame_check == 1){
			XLOGV("Skip mjc frame");
			mSinceLastDropped = 0;
			mVideoBuffer->release();
			mVideoBuffer = NULL;
	
			{
				Mutex::Autolock autoLock(mStatsLock);
				++mStats.mNumVideoFramesDropped;
			}
	
				 // fast skip late frames
			postVideoEvent_l(0);
			return; 	
		}
	}
#endif	
#endif
    int64_t timeUs;
    CHECK(mVideoBuffer->meta_data()->findInt64(kKeyTime, &timeUs));

#ifdef  MTB_SUPPORT
    ATRACE_ONESHOT(ATRACE_ONESHOT_VDATA,"VB: %p,TS: %lld", mVideoBuffer, timeUs);      
#endif

    mLastVideoTimeUs = timeUs;

    if (mSeeking == SEEK_VIDEO_ONLY) {
        if (mSeekTimeUs > timeUs) {
            ALOGI("XXX mSeekTimeUs = %lld us, timeUs = %lld us",
                 mSeekTimeUs, timeUs);
        }
    }

#ifdef ANDROID_DEFAULT_CODE
    // set in the below
    {
        Mutex::Autolock autoLock(mMiscStateLock);
        mVideoTimeUs = timeUs;
    }
#endif // #ifndef ANDROID_DEFAULT_CODE

    SeekType wasSeeking = mSeeking;
//#ifndef ANDROID_DEFAULT_CODE
//    finishSeekIfNecessary(mPrerollEnable ? -1 : timeUs);
//#else
	finishSeekIfNecessary(timeUs);//Pre-roll may be time out, seek audio to the video timestamp
//#endif

    if (mAudioPlayer != NULL && !(mFlags & (AUDIO_RUNNING | SEEK_PREVIEW))) {
#ifndef ANDROID_DEFAULT_CODE
		if (wasSeeking == SEEK && isStreamingHTTP() && mAudioSource != NULL) {
			ALOGD("onVideoEvent: audio resume,mFlags=0x%x",mFlags);
		 reinterpret_cast<OMXCodec *>(mAudioSource.get())->resume(); 
		}
       ALOGD("onVideoEvent: startAudioPlayer_l,mFlags=0x%x",mFlags);
#endif
        status_t err = startAudioPlayer_l();
        if (err != OK) {
            ALOGE("Starting the audio player failed w/ err %d", err);
            return;
        }
    }

    if ((mFlags & TEXTPLAYER_INITIALIZED)
            && !(mFlags & (TEXT_RUNNING | SEEK_PREVIEW))) {
        mTextDriver->start();
        modifyFlags(TEXT_RUNNING, SET);
    }

    TimeSource *ts =
        ((mFlags & AUDIO_AT_EOS) || !(mFlags & AUDIOPLAYER_STARTED))
            ? &mSystemTimeSource : mTimeSource;

    if (mFlags & FIRST_FRAME) {
        modifyFlags(FIRST_FRAME, CLEAR);
        mSinceLastDropped = 0;
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
		mSync_time = timeUs;
#endif
        mTimeSourceDeltaUs = mSystemTimeSource.getRealTimeUs() - timeUs; 
	    if (mAudioNormalEOS && mAdjustPos!=0) { // audio EOS and video late, ajust it avoid of position back ALPS00404749
                 mTimeSourceDeltaUs -= mAdjustPos;
	    }
        ALOGI("first frame delta %lld = real %lld - timeUs %lld", mTimeSourceDeltaUs, mSystemTimeSource.getRealTimeUs(), timeUs);
#else // #ifndef ANDROID_DEFAULT_CODE
        mTimeSourceDeltaUs = ts->getRealTimeUs() - timeUs;
#endif
    }

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
	if(SpeedValueChanged){
		mSync_time = timeUs;
		SpeedValueChanged = false;
		mTimeSourceDeltaUs = mSystemTimeSource.getRealTimeUs() - timeUs; 
		ALOGD("mSync_time = %lld,mTimeSourceDeltaUs = %lld",mSync_time,mTimeSourceDeltaUs);

	}
#endif
    ts = &mSystemTimeSource;
    int64_t realTimeUs, mediaTimeUs;
    if (!mAudioNormalEOS && mAudioPlayer != NULL && !(mFlags & SEEK_PREVIEW)) {
        status_t finalStatus;
        bool mapping = mAudioPlayer->getMediaTimeMapping(&realTimeUs, &mediaTimeUs);
        if (mWatchForAudioSeekComplete) {       
            ALOGI("audio is seeking, seek time %lld", mSeekTimeUs);
            //if the audio seek is not complete, mediaTimeUs from AudioPlayer is wrong, so use "mSeekTimeUs"
            mediaTimeUs = mSeekTimeUs;
        }

        if (mAudioPlayer->reachedEOS(&finalStatus)) {
            ALOGI("audio eos detected");
            int64_t mediaTimeNowUs = mAudioPlayer->getMediaTimeUs();
            if (mediaTimeNowUs > mLastAudioSeekUs && mapping) {
                mTimeSourceDeltaUs = mSystemTimeSource.getRealTimeUs() - mAudioPlayer->getRealTimeUs()
                    + realTimeUs - mediaTimeUs;
	        mAdjustPos = ts->getRealTimeUs() - mTimeSourceDeltaUs - timeUs;
                ALOGI("audio is normal EOS delta %lld now %lld real %lld media %lld", 
                        mTimeSourceDeltaUs, mediaTimeNowUs, realTimeUs, mediaTimeUs);
            }
            mAudioNormalEOS = true;
        } else if (mapping) {
            ts = mAudioPlayer;
            mTimeSourceDeltaUs = realTimeUs - mediaTimeUs;
        }
        else{
            mediaTimeUs = mSeekTimeUs;
        	realTimeUs = ts->getRealTimeUs();
        	mTimeSourceDeltaUs = realTimeUs - mediaTimeUs;
        	ALOGW("AudioPlayer no mapping, set media = %lld, real = %lld", mediaTimeUs, realTimeUs);
        }
    }
    
    {
        Mutex::Autolock autoLock(mMiscStateLock);
        int64_t realTimeUs = ts->getRealTimeUs();
        if (realTimeUs < 0) {
            ALOGW("realTimeUs %lld", realTimeUs);
            realTimeUs = 0;
        }		
        mVideoTimeUs = realTimeUs - mTimeSourceDeltaUs;
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT		
		if(mSpeedValue==1)
			mVideoTimeUs = mSync_time + (mVideoTimeUs-mSync_time)*2;
		else if(mSpeedValue == 4)
			mVideoTimeUs = mSync_time + (mVideoTimeUs-mSync_time)/2;
#endif		
        if (mVideoTimeUs < 0) {
            ALOGW("mVideoTimeUs %lld", mVideoTimeUs);
            mVideoTimeUs = 0;
        }
    }
#else
    int64_t realTimeUs, mediaTimeUs;
    if (!(mFlags & AUDIO_AT_EOS) && mAudioPlayer != NULL
        && mAudioPlayer->getMediaTimeMapping(&realTimeUs, &mediaTimeUs)) {
        mTimeSourceDeltaUs = realTimeUs - mediaTimeUs;
    }
#endif

    if (wasSeeking == SEEK_VIDEO_ONLY) {
        int64_t nowUs = ts->getRealTimeUs() - mTimeSourceDeltaUs;
#ifndef ANDROID_DEFAULT_CODE		
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
		if(mSpeedValue==1)
			nowUs = mSync_time + (nowUs-mSync_time)*2;
		else if(mSpeedValue == 4)
			nowUs = mSync_time + (nowUs-mSync_time)/2;
#endif
#endif
        int64_t latenessUs = nowUs - timeUs;

        ATRACE_INT("Video Lateness (ms)", latenessUs / 1E3);

        if (latenessUs > 0) {
            ALOGI("after SEEK_VIDEO_ONLY we're late by %.2f secs", latenessUs / 1E6);
        }
    }

    if (wasSeeking == NO_SEEK) {
        // Let's display the first frame after seeking right away.

        int64_t nowUs = ts->getRealTimeUs() - mTimeSourceDeltaUs;
#ifndef ANDROID_DEFAULT_CODE		
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
		if(mSpeedValue==1)
			nowUs = mSync_time + (nowUs-mSync_time)*2;
		else if(mSpeedValue == 4)
			nowUs = mSync_time + (nowUs-mSync_time)/2;
#endif
#endif

        int64_t latenessUs = nowUs - timeUs;

        ATRACE_INT("Video Lateness (ms)", latenessUs / 1E3);
#ifndef ANDROID_DEFAULT_CODE
	  mAVSyncTimeUs = nowUs;
	XLOGV("realTimeUs:%lld,nowUs:%lld,mediaTimeUs:%lld, latenessUs:%lld", ts->getRealTimeUs(), 
		nowUs, mediaTimeUs, latenessUs);
#endif

#ifndef ANDROID_DEFAULT_CODE 
	    if((latenessUs > mAVSyncThreshold && mAVSyncThreshold >0) 
#else
        if (latenessUs > 500000ll
#endif 	
                && mAudioPlayer != NULL
                && mAudioPlayer->getMediaTimeMapping(
                    &realTimeUs, &mediaTimeUs)) {
            if (mWVMExtractor == NULL) {
            ALOGI("we're much too late (%.2f secs), video skipping ahead",
                 latenessUs / 1E6);

            mVideoBuffer->release();
            mVideoBuffer = NULL;

            mSeeking = SEEK_VIDEO_ONLY;
            mSeekTimeUs = mediaTimeUs;

#ifndef ANDROID_DEFAULT_CODE 
        // fast skip late frames
        	postVideoEvent_l(0);
#else
        	postVideoEvent_l();
#endif // #ifndef ANDROID_DEFAULT_CODE
            return;
            } else {
                // The widevine extractor doesn't deal well with seeking
                // audio and video independently. We'll just have to wait
                // until the decoder catches up, which won't be long at all.
                ALOGI("we're very late (%.2f secs)", latenessUs / 1E6);
            }
        }

#ifndef ANDROID_DEFAULT_CODE 
		if (latenessUs > mLateMargin) {
#else
        if (latenessUs > 40000){ 
#endif
            // We're more than 40ms late.
#ifndef ANDROID_DEFAULT_CODE 
            ALOGD("we're late by %lld us (%.2f secs)",
                 latenessUs, latenessUs / 1E6);
#else
            ALOGV("we're late by %lld us (%.2f secs)",
                 latenessUs, latenessUs / 1E6);
#endif

            if (!(mFlags & SLOW_DECODER_HACK)
#ifndef ANDROID_DEFAULT_CODE 
                     || (mSinceLastDropped > mFRAME_DROP_FREQ))//force Consective display Frames,can adjust,default =6
#else
        		|| mSinceLastDropped > FRAME_DROP_FREQ)
#endif // #ifndef ANDROID_DEFAULT_CODE
            {
#ifndef ANDROID_DEFAULT_CODE 
                ALOGD("we're late by %lld us (%.2f secs) dropping "
                     "one after %d frames",
                     latenessUs, latenessUs / 1E6, mSinceLastDropped);
#else
                ALOGV("we're late by %lld us (%.2f secs) dropping "
                     "one after %d frames",
                     latenessUs, latenessUs / 1E6, mSinceLastDropped);
#endif

                mSinceLastDropped = 0;
                mVideoBuffer->release();
                mVideoBuffer = NULL;

                {
                    Mutex::Autolock autoLock(mStatsLock);
                    ++mStats.mNumVideoFramesDropped;
                }

                postVideoEvent_l(0);
                return;
            }
        }

#ifdef ANDROID_DEFAULT_CODE// for av sync improvement
        if (latenessUs < -10000) {
            // We're more than 10ms early.
            postVideoEvent_l(10000);
			return;
		}
#else
if (latenessUs < -1000) {   // 1ms
            // We're more than 10ms early.
	if (latenessUs > -10000) {
	    postVideoEvent_l(-latenessUs);
	}
	else {
            postVideoEvent_l(10000);
	}
            return;
        }
#endif

    }

    if ((mNativeWindow != NULL)
            && (mVideoRendererIsPreview || mVideoRenderer == NULL)) {
        mVideoRendererIsPreview = false;

        initRenderer_l();
    }

    if (mVideoRenderer != NULL) {
        mSinceLastDropped++;
#ifdef MTK_CLEARMOTION_SUPPORT
		int32_t interpolateFrame;
		CHECK(mVideoBuffer->meta_data()->findInt32(kKeyInterpolateFrame, &interpolateFrame));
		ALOGV("[MJC]onVideoEvent : interpolateFrame = %d, pausing = %d", interpolateFrame, (bool)(mFlags & PAUSING));
        if (!interpolateFrame || !(mFlags & PAUSING)) {
        	mVideoRenderer->render(mVideoBuffer);
			mMJCPauseDelay = interpolateFrame ? (true):(false);
			if ((mFlags & PAUSING) && !interpolateFrame) {
				ALOGD("[MJC]onVideoEvent : mMJCPauseDelay.signal()");
				mMJCCondition.signal();
            }
			}
#else
		mVideoRenderer->render(mVideoBuffer);
#endif
        if (!mVideoRenderingStarted) {
            mVideoRenderingStarted = true;
            notifyListener_l(MEDIA_INFO, MEDIA_INFO_RENDERING_START);
    }

        if (mFlags & PLAYING) {
            notifyIfMediaStarted_l();
        }
    }

#ifndef ANDROID_DEFAULT_CODE
    int64_t bufReleaseStart = systemTime() / 1000;
#endif
    mVideoBuffer->release();
#ifndef ANDROID_DEFAULT_CODE
    int64_t releaseElapse = systemTime() / 1000 - bufReleaseStart;
    if (releaseElapse > mThrottleVideoBufRel)  {              // default 6ms
	XLOGV("mVideoBuffer release elapse(us):%lld", releaseElapse);
    }
#endif
    mVideoBuffer = NULL;

    if (wasSeeking != NO_SEEK && (mFlags & SEEK_PREVIEW)) {
	 ALOGD("onVideoEvent: SEEK_PREVIEW,CLEAR mFlags=0x%x",mFlags);
        modifyFlags(SEEK_PREVIEW, CLEAR);
        return;
    }

    /* get next frame time */
    if (wasSeeking == NO_SEEK) {
        MediaSource::ReadOptions options;
        for (;;) {
            status_t err = mVideoSource->read(&mVideoBuffer, &options);
            if (err != OK) {
                // deal with any errors next time
                CHECK(mVideoBuffer == NULL);
                postVideoEvent_l(0);
#ifndef ANDROID_DEFAULT_CODE
		ALOGD("get next Frame:err:%d, post 0", err);
#endif
                return;
            }
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
			if(mSpeedValue ==1){
			int32_t interpolateFrame_check;
				CHECK(mVideoBuffer->meta_data()->findInt32(kKeyInterpolateFrame, &interpolateFrame_check));
				if(interpolateFrame_check == 1){
					XLOGV("get next Frame:skip mjc frame");
					mSinceLastDropped = 0;
					mVideoBuffer->release();
					mVideoBuffer = NULL;
					{
						Mutex::Autolock autoLock(mStatsLock);
						++mStats.mNumVideoFramesDropped;
					}
					continue;
				}
			}
#endif			
#endif
            if (mVideoBuffer->range_length() != 0) {
                break;
            }

            // Some decoders, notably the PV AVC software decoder
            // return spurious empty buffers that we just want to ignore.

            mVideoBuffer->release();
            mVideoBuffer = NULL;
        }

        {
            Mutex::Autolock autoLock(mStatsLock);
            ++mStats.mNumVideoFramesDecoded;
        }

        int64_t nextTimeUs;
        CHECK(mVideoBuffer->meta_data()->findInt64(kKeyTime, &nextTimeUs));
        int64_t delayUs = nextTimeUs - ts->getRealTimeUs() + mTimeSourceDeltaUs;
        postVideoEvent_l(delayUs > 10000 ? 10000 : delayUs < 0 ? 0 : delayUs);
#ifndef ANDROID_DEFAULT_CODE
	if (delayUs < 0) {
	    ALOGD("next Frame: delayUs:%lld post 0", delayUs);
	}
#endif
        return;
    }

    postVideoEvent_l();
}

void AwesomePlayer::postVideoEvent_l(int64_t delayUs) {
    ATRACE_CALL();

    if (mVideoEventPending) {
        return;
    }

    mVideoEventPending = true;
    mQueue.postEventWithDelay(mVideoEvent, delayUs < 0 ? 10000 : delayUs);
}

void AwesomePlayer::postStreamDoneEvent_l(status_t status) {
    if (mStreamDoneEventPending) {
        return;
    }
    mStreamDoneEventPending = true;

    mStreamDoneStatus = status;
    mQueue.postEvent(mStreamDoneEvent);
}

void AwesomePlayer::postBufferingEvent_l() {
    if (mBufferingEventPending) {
        return;
    }
    mBufferingEventPending = true;
#ifndef ANDROID_DEFAULT_CODE
    // reduce interval to 200ms as opencore
    mQueue.postEventWithDelay(mBufferingEvent, 200000ll);
#else
    mQueue.postEventWithDelay(mBufferingEvent, 1000000ll);
#endif // #ifndef ANDROID_DEFAULT_CODE
}

void AwesomePlayer::postVideoLagEvent_l() {
    if (mVideoLagEventPending) {
        return;
    }
    mVideoLagEventPending = true;
    mQueue.postEventWithDelay(mVideoLagEvent, 1000000ll);
}

void AwesomePlayer::postCheckAudioStatusEvent(int64_t delayUs) {
    Mutex::Autolock autoLock(mAudioLock);
    if (mAudioStatusEventPending) {
        return;
    }
    mAudioStatusEventPending = true;
    // Do not honor delay when looping in order to limit audio gap
    if (mFlags & (LOOPING | AUTO_LOOPING)) {
        delayUs = 0;
    }
#ifndef ANDROID_DEFAULT_CODE
    // AudioCache needn't delay post EOS, just AudioOutput which with a valid UID needed
    if(!mUIDValid){
        ALOGI("AudioCache needn't delay post EOS!!!");
        delayUs = 0;
    }
#endif
    mQueue.postEventWithDelay(mCheckAudioStatusEvent, delayUs);
}

void AwesomePlayer::postAudioTearDownEvent(int64_t delayUs) {
    Mutex::Autolock autoLock(mAudioLock);
    if (mAudioTearDownEventPending) {
        return;
    }
    mAudioTearDownEventPending = true;
    mQueue.postEventWithDelay(mAudioTearDownEvent, delayUs);
}

void AwesomePlayer::onCheckAudioStatus() {
    {
        Mutex::Autolock autoLock(mAudioLock);
        if (!mAudioStatusEventPending) {
            // Event was dispatched and while we were blocking on the mutex,
            // has already been cancelled.
            return;
        }

        mAudioStatusEventPending = false;
    }

    Mutex::Autolock autoLock(mLock);

    if (mWatchForAudioSeekComplete && !mAudioPlayer->isSeeking()) {
        mWatchForAudioSeekComplete = false;

#ifndef ANDROID_DEFAULT_CODE
        // this is used to detect a EOS right after seek
        mLastAudioSeekUs = mAudioPlayer->getMediaTimeUs();
#endif // #ifndef ANDROID_DEFAULT_CODE
        if (!mSeekNotificationSent) {
            notifyListener_l(MEDIA_SEEK_COMPLETE);
            mSeekNotificationSent = true;
        }

#ifndef ANDROID_DEFAULT_CODE
        // when has video and audio, NO_SEEK flag would be set by finishSeekIfNecessary;
        // Here set NO_SEEK, it would casue new seek invalid
	if (mVideoSource == NULL) {    
        mSeeking = NO_SEEK;
	 ALOGI("onCheckAudioStatus:mSeeking = NO_SEEK;");
    }
	else {
	    ALOGI("onCheckAudioStatus:Should not set NO_SEEK again,mSeeking:%d", mSeeking);
	}
#else
	mSeeking = NO_SEEK;
#endif
    }

    status_t finalStatus;
    if (mWatchForAudioEOS && mAudioPlayer->reachedEOS(&finalStatus)) {
        mWatchForAudioEOS = false;
        modifyFlags(AUDIO_AT_EOS, SET);
        modifyFlags(FIRST_FRAME, SET);
#ifndef ANDROID_DEFAULT_CODE
		if(finalStatus == ERROR_UNSUPPORTED_AUDIO)
		{
			if((mVideoSource == NULL) && (mAudioSource != NULL))
			{
			    ALOGD("finalStatus %d",finalStatus);
				postStreamDoneEvent_l(finalStatus);
			}
			else
			{			
				notifyListener_l(MEDIA_INFO, MEDIA_INFO_HAS_UNSUPPORT_AUDIO);
				mFinalStopFlag |=FINAL_HAS_UNSUPPORT_AUDIO;
				postStreamDoneEvent_l(ERROR_END_OF_STREAM);
			}
		}else{
#endif		
			postStreamDoneEvent_l(finalStatus);
#ifndef ANDROID_DEFAULT_CODE
		}
#endif
    }
}

status_t AwesomePlayer::prepare() {
    ATRACE_CALL();
    Mutex::Autolock autoLock(mLock);
    return prepare_l();
}

status_t AwesomePlayer::prepare_l() {
    if (mFlags & PREPARED) {
        return OK;
    }
    ATRACE_CALL();

    if (mFlags & PREPARING) {
        return UNKNOWN_ERROR;
    }

    mIsAsyncPrepare = false;
    status_t err = prepareAsync_l();

    if (err != OK) {
        return err;
    }

    while (mFlags & PREPARING) {
        mPreparedCondition.wait(mLock);
    }

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
    // OMA DRM v1 implementation: consume rights.
    mIsCurrentComplete = false;
    if (mDecryptHandle != NULL) {
        ALOGD("AwesomePlayer, consumeRights @prepare_l()");
        // in some cases, the mFileSource may be NULL (E.g. play audio directly in File Manager)
        // We don't know, but we assume it's a OMA DRM v1 case (DecryptApiType::CONTAINER_BASED)
        if ((mFileSource.get() != NULL && (mFileSource->flags() & OMADrmFlag) != 0)
            || (DecryptApiType::CONTAINER_BASED == mDecryptHandle->decryptApiType)) {
            if (!DrmMtkUtil::isTrustedVideoClient(mDrmValue)) {
                mDrmManagerClient->consumeRights(mDecryptHandle, Action::PLAY, false);
            }
        }
    }
#endif
#endif // #ifndef ANDROID_DEFAULT_CODE

    return mPrepareResult;
}

status_t AwesomePlayer::prepareAsync() {
    ATRACE_CALL();
    Mutex::Autolock autoLock(mLock);

    if (mFlags & PREPARING) {
        return UNKNOWN_ERROR;  // async prepare already pending
    }

    mIsAsyncPrepare = true;
    return prepareAsync_l();
}

status_t AwesomePlayer::prepareAsync_l() {
    if (mFlags & PREPARING) {
        return UNKNOWN_ERROR;  // async prepare already pending
    }

    if (!mQueueStarted) {
        mQueue.start();
        mQueueStarted = true;
    }

    modifyFlags(PREPARING, SET);
    mAsyncPrepareEvent = new AwesomeEvent(
            this, &AwesomePlayer::onPrepareAsyncEvent);

    mQueue.postEvent(mAsyncPrepareEvent);

    return OK;
}

status_t AwesomePlayer::finishSetDataSource_l() {
    ATRACE_CALL();
    sp<DataSource> dataSource;
#ifndef ANDROID_DEFAULT_CODE
    mFinishAgain = false;
    mIsLargeMetaData = false;         //should init it here, avoid of datasource is changed
#endif // #ifndef ANDROID_DEFAULT_CODE

    bool isWidevineStreaming = false;
    if (!strncasecmp("widevine://", mUri.string(), 11)) {
        isWidevineStreaming = true;

        String8 newURI = String8("http://");
        newURI.append(mUri.string() + 11);

        mUri = newURI;
    }

    AString sniffedMIME;

    if (!strncasecmp("http://", mUri.string(), 7)
            || !strncasecmp("https://", mUri.string(), 8)
            || isWidevineStreaming) {
        mConnectingDataSource = HTTPBase::Create(
                (mFlags & INCOGNITO)
                    ? HTTPBase::kFlagIncognito
                    : 0);

        if (mUIDValid) {
            mConnectingDataSource->setUID(mUID);
        }
#ifndef ANDROID_DEFAULT_CODE 
        String8 cacheSize;
        if (removeSpecificHeaders(String8("MTK-HTTP-CACHE-SIZE"), &mUriHeaders, &cacheSize)) {
            mHighWaterMarkUs = (int64_t)atoi(cacheSize.string()) * 1000000ll;
        } else {
            mHighWaterMarkUs = kHighWaterMarkUs;
        }
       ALOGD("cache size = %lld", mHighWaterMarkUs);
#endif
        String8 cacheConfig;
        bool disconnectAtHighwatermark;
        NuCachedSource2::RemoveCacheSpecificHeaders(
                &mUriHeaders, &cacheConfig, &disconnectAtHighwatermark);

        mLock.unlock();
        status_t err = mConnectingDataSource->connect(mUri, &mUriHeaders);
        mLock.lock();
		
        if (err != OK) {
#ifndef ANDROID_DEFAULT_CODE
            Mutex::Autolock autoLock(mMiscStateLock);
            if (mConnectingDataSource != NULL) {
                mConnectingDataSource.clear();
            }
           ALOGI("mConnectingDataSource->connect() returned %d", err);
			err = ERROR_CANNOT_CONNECT;//notify this when connect fail whatever DataSource returned
#else
            mConnectingDataSource.clear();

           ALOGI("mConnectingDataSource->connect() returned %d", err);
#endif
            return err;
        }

        if (!isWidevineStreaming) {
            // The widevine extractor does its own caching.

#if 0
            mCachedSource = new NuCachedSource2(
                    new ThrottledSource(
                        mConnectingDataSource, 50 * 1024 /* bytes/sec */));
#else
            mCachedSource = new NuCachedSource2(
                    mConnectingDataSource,
                    cacheConfig.isEmpty() ? NULL : cacheConfig.string(),
                    disconnectAtHighwatermark);
#ifndef ANDROID_DEFAULT_CODE
        	mPrerollEnable = false;
#endif
#endif

            dataSource = mCachedSource;
        } else {
            dataSource = mConnectingDataSource;
        }
#ifdef ANDROID_DEFAULT_CODE
        mConnectingDataSource.clear();
#endif

        String8 contentType = dataSource->getMIMEType();

        if (strncasecmp(contentType.string(), "audio/", 6)) {
            // We're not doing this for streams that appear to be audio-only
            // streams to ensure that even low bandwidth streams start
            // playing back fairly instantly.

            // We're going to prefill the cache before trying to instantiate
            // the extractor below, as the latter is an operation that otherwise
            // could block on the datasource for a significant amount of time.
            // During that time we'd be unable to abort the preparation phase
            // without this prefill.
            if (mCachedSource != NULL) {
                // We're going to prefill the cache before trying to instantiate
                // the extractor below, as the latter is an operation that otherwise
                // could block on the datasource for a significant amount of time.
                // During that time we'd be unable to abort the preparation phase
                // without this prefill.

                mLock.unlock();

                // Initially make sure we have at least 192 KB for the sniff
                // to complete without blocking.
                static const size_t kMinBytesForSniffing = 192 * 1024;

                off64_t metaDataSize = -1ll;
                for (;;) {
                    status_t finalStatus;
                    size_t cachedDataRemaining =
                        mCachedSource->approxDataRemaining(&finalStatus);

                    if (finalStatus != OK
                            || (metaDataSize >= 0
                                && cachedDataRemaining >= metaDataSize)
                            || (mFlags & PREPARE_CANCELLED)) {
                        break;
                    }

                    ALOGV("now cached %d bytes of data", cachedDataRemaining);

                    if (metaDataSize < 0
                            && cachedDataRemaining >= kMinBytesForSniffing) {
                        String8 tmp;
                        float confidence;
                        ATRACE_BEGIN("sniff");
                        sp<AMessage> meta;
						ALOGD("content type=%s", contentType.string());
						ALOGD("SNIFF+");
                        if (!dataSource->sniff(&tmp, &confidence, &meta)) {
                            mLock.lock();
                            return UNKNOWN_ERROR;
                        }
						ALOGD("SNIFF-");
                        ATRACE_END();

                        // We successfully identified the file's extractor to
                        // be, remember this mime type so we don't have to
                        // sniff it again when we call MediaExtractor::Create()
                        // below.
                        sniffedMIME = tmp.string();

                        if (meta == NULL
                                || !meta->findInt64(
                                    "meta-data-size", &metaDataSize)) {
                            metaDataSize = kHighWaterMarkBytes;
                        }
#ifndef ANDROID_DEFAULT_CODE
			   if(metaDataSize > kHighWaterMarkBytes){
			   	    ALOGD("metaDataSize is large =%lld bytes", metaDataSize);
					 metaDataSize = kHighWaterMarkBytes;
                                    mIsLargeMetaData = true;
			   }
#endif

                        CHECK_GE(metaDataSize, 0ll);
                        ALOGD("metaDataSize = %lld bytes", metaDataSize);
                    }

                    usleep(200000);
                }

                mLock.lock();
            }

            if (mFlags & PREPARE_CANCELLED) {
                ALOGI("Prepare cancelled while waiting for initial cache fill.");
                return UNKNOWN_ERROR;
            }
        }
#ifndef ANDROID_DEFAULT_CODE
        else {
            //sniffedMIME = contentType.string();
            //use the mime type from contentType reported by DataSource

            if (mCachedSource != NULL) {
                mLock.unlock();
                for (;;) {
                    status_t finalStatus;
                    size_t cachedDataRemaining = mCachedSource->approxDataRemaining(&finalStatus);

                    //if (finalStatus != OK || (cachedDataRemaining >= mHighWaterMarkUs)
					if (finalStatus != OK || (cachedDataRemaining >= 192 * 1024)//High water mark may be changed according to header. 192k is enough to sniff audio-only media content..
                            || (mFlags & PREPARE_CANCELLED)) {
                        break;
                    }
                   ALOGV("cached %d bytes for %s", cachedDataRemaining, contentType.string());
                    usleep(200000);
                }

                mLock.lock();
            }

            if (mFlags & PREPARE_CANCELLED) {
               ALOGI("Prepare cancelled while waiting for initial cache fill.");
                return UNKNOWN_ERROR;
            }
        }
#endif
    } else {
#ifndef ANDROID_DEFAULT_CODE
        if ((!strncasecmp("/system/media/audio/", mUri.string(), 20)) && (strcasestr(mUri.string(),".ogg") != NULL))
           sniffedMIME = MEDIA_MIMETYPE_CONTAINER_OGG;
#endif
        dataSource = DataSource::CreateFromURI(mUri.string(), &mUriHeaders);
    }

    if (dataSource == NULL) {
        return UNKNOWN_ERROR;
    }

    sp<MediaExtractor> extractor;

    if (isWidevineStreaming) {
        String8 mimeType;
        float confidence;
        sp<AMessage> dummy;
        bool success;

        // SniffWVM is potentially blocking since it may require network access.
        // Do not call it with mLock held.
        mLock.unlock();
        success = SniffWVM(dataSource, &mimeType, &confidence, &dummy);
        mLock.lock();

        if (!success
                || strcasecmp(
                    mimeType.string(), MEDIA_MIMETYPE_CONTAINER_WVM)) {
            return ERROR_UNSUPPORTED;
        }

        mWVMExtractor = new WVMExtractor(dataSource);
        mWVMExtractor->setAdaptiveStreamingMode(true);
        if (mUIDValid)
            mWVMExtractor->setUID(mUID);
        extractor = mWVMExtractor;
    } else {
#ifndef ANDROID_DEFAULT_CODE
//[ALPS01031188] When use duomi music apk and it would call setloop api, it would cause ANR if prepare cost too much time
	if (mCachedSource != NULL) {
            ALOGI("Http Streaming unlock before MediaExtractor::Cretae");
	    mLock.unlock();
	} 
#endif
        extractor = MediaExtractor::Create(
                dataSource, sniffedMIME.empty() ? NULL : sniffedMIME.c_str());
#ifndef ANDROID_DEFAULT_CODE
	if (mCachedSource != NULL) {
            ALOGI("Http Streaming lock after MediaExtractor::Cretae");
	    mLock.lock();

            if (mFlags & PREPARE_CANCELLED) {
                ALOGI("Prepare cancelled while waiting for MediaExtractor Create");
                return UNKNOWN_ERROR;
            }

	} 
#endif

        if (extractor == NULL) {
            return UNKNOWN_ERROR;
        }
    }

#ifndef ANDROID_DEFAULT_CODE
    if ((extractor->flags() & MediaExtractor::MAY_PARSE_TOO_LONG)) {
        Mutex::Autolock autoLock(mMiscStateLock);
        if (mStopped) {
            ALOGI("user has already stopped");
            extractor->stopParsing();
        } else {
            ALOGI("this extractor may take long time to parse, record for stopping");
            mExtractor = extractor;
        }
    }
#endif // #ifndef ANDROID_DEFAULT_CODE
    if (extractor->getDrmFlag()) {
        checkDrmStatus(dataSource);
    }

    status_t err = setDataSource_l(extractor);
#ifndef ANDROID_DEFAULT_CODE
    if (mCachedSource != NULL && mIsLargeMetaData && mFlags & PREPARE_CANCELLED) {
	ALOGI("Prepare cancelled while waiting setDataSource_l");
	return UNKNOWN_ERROR;
    }
#endif

    if (err != OK) {
        mWVMExtractor.clear();

        return err;
    }

    return OK;
}

void AwesomePlayer::abortPrepare(status_t err) {
    CHECK(err != OK);

    if (mIsAsyncPrepare) {
        notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, err);
    }

    mPrepareResult = err;
    modifyFlags((PREPARING|PREPARE_CANCELLED|PREPARING_CONNECTED), CLEAR);
    mAsyncPrepareEvent = NULL;
    mPreparedCondition.broadcast();
    mAudioTearDown = false;
}

// static
bool AwesomePlayer::ContinuePreparation(void *cookie) {
    AwesomePlayer *me = static_cast<AwesomePlayer *>(cookie);

    return (me->mFlags & PREPARE_CANCELLED) == 0;
}

void AwesomePlayer::onPrepareAsyncEvent() {
    Mutex::Autolock autoLock(mLock);
    beginPrepareAsync_l();
}

void AwesomePlayer::beginPrepareAsync_l() {
    if (mFlags & PREPARE_CANCELLED) {
        ALOGI("prepare was cancelled before doing anything");
        abortPrepare(UNKNOWN_ERROR);
        return;
    }

    if (mUri.size() > 0) {
        status_t err = finishSetDataSource_l();

#ifndef ANDROID_DEFAULT_CODE
        if (mFinishAgain){
        	ALOGI("RTSP is SDP over http mode");
            err = finishSetDataSource_l();
		}
#endif // #ifndef ANDROID_DEFAULT_CODE
        if (err != OK) {
            abortPrepare(err);
            return;
        }
    }
#ifndef ANDROID_DEFAULT_CODE
	if((mExtractorFlags & MediaExtractor::MAY_PARSE_TOO_LONG))
	{
		Mutex::Autolock autoLock(mMiscStateLock);
		int err;
		if (mExtractor != NULL) {
			ALOGE("parsing index of avi file!");
			if(mStopped){
				err=mExtractor->stopParsing();
			}else{
				err=mExtractor->finishParsing();
			}
            #ifndef MTK_AUDIO_CHANGE_SUPPORT
			mExtractor.clear();
            #endif
			if (err != OK) {          
				abortPrepare(err);
	            		return;
	        	}			
		}
	}
#endif

#ifndef ANDROID_DEFAULT_CODE
//ALPS00427501
    if (mVideoTrack == NULL && mVideoSource == NULL && mExtractor != NULL) {
        int32_t hasUnsupportVideo = 0;
        sp<MetaData> fileMeta = mExtractor->getMetaData();
        if (fileMeta != NULL && fileMeta->findInt32(kKeyHasUnsupportVideo, &hasUnsupportVideo)
            && hasUnsupportVideo != 0) {
            notifyListener_l(MEDIA_INFO, MEDIA_INFO_HAS_UNSUPPORT_VIDEO);
            ALOGD("Notify APP that file has unsupportted video");
        }
            
    }
#endif

    if (mVideoTrack != NULL && mVideoSource == NULL) {
        status_t err = initVideoDecoder();

#ifndef ANDROID_DEFAULT_CODE
        if (err == ERROR_UNSUPPORTED_VIDEO || mVideoSource == NULL ) {
            ALOGW("unsupportted video detected, has audio = %d %d", mAudioTrack != NULL, mAudioSource != NULL);
            if (mAudioTrack != NULL || mAudioSource != NULL) {
                notifyListener_l(MEDIA_INFO, MEDIA_INFO_HAS_UNSUPPORT_VIDEO);
                err = OK;
	          mFinalStopFlag |=FINAL_HAS_UNSUPPORT_VIDEO;
            } else {
                err = MEDIA_ERROR_TYPE_NOT_SUPPORTED;
                notifyListener_l(MEDIA_ERROR, err);
            }
            const char *mime;			
            CHECK(mMetaData->findCString(kKeyMIMEType, &mime));
        #ifdef MTK_OGM_PLAYBACK_SUPPORT
            if(!strcasecmp(MEDIA_MIMETYPE_CONTAINER_MPEG2TS, mime) || !strcasecmp(MEDIA_MIMETYPE_VIDEO_WMV, mime) || !strcasecmp(MEDIA_MIMETYPE_CONTAINER_OGM, mime))
        #else
            if(!strcasecmp(MEDIA_MIMETYPE_CONTAINER_MPEG2TS, mime) || !strcasecmp(MEDIA_MIMETYPE_VIDEO_WMV, mime))
        #endif
            {

                mVideoTrack->stop();
                ALOGE("onPrepareAsyncEvent stop video track");
            }
        }
#endif
        if (err != OK) {
            abortPrepare(err);
            return;
        }
    }

    if (mAudioTrack != NULL && mAudioSource == NULL) {
        status_t err = initAudioDecoder();
#ifndef ANDROID_DEFAULT_CODE
        if (err == ERROR_UNSUPPORTED_AUDIO || mAudioSource == NULL ) {
            ALOGW("unsupportted audio detected, has video = %d %d", mVideoTrack != NULL, mVideoSource != NULL);
            if (mVideoSource != NULL) {
                 notifyListener_l(MEDIA_INFO, MEDIA_INFO_HAS_UNSUPPORT_AUDIO);
	          mFinalStopFlag |=FINAL_HAS_UNSUPPORT_AUDIO;
		   err = OK;
            }else  {                   
	            err = MEDIA_ERROR_TYPE_NOT_SUPPORTED;
                    notifyListener_l(MEDIA_ERROR, err);
            }
        }
#endif 	       
        if (err != OK) {
            abortPrepare(err);
            return;
        }
    }

    modifyFlags(PREPARING_CONNECTED, SET);

    if (isStreamingHTTP()) {
        postBufferingEvent_l();
    } else {
        finishAsyncPrepare_l();
    }
	
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
		if (mDecryptHandle != NULL) {
			ALOGD("AwesomePlayer, consumeRights @onPrepareAsyncEvent()");
			// in some cases, the mFileSource may be NULL (E.g. play audio directly in File Manager)
			// We don't know, but we assume it's a OMA DRM v1 case (DecryptApiType::CONTAINER_BASED)
			if ((mFileSource.get() != NULL && (mFileSource->flags() & OMADrmFlag) != 0)
				|| (DecryptApiType::CONTAINER_BASED == mDecryptHandle->decryptApiType)) {
				if (!DrmMtkUtil::isTrustedVideoClient(mDrmValue)) {
					mDrmManagerClient->consumeRights(mDecryptHandle, Action::PLAY, false);
				}
			}
		}
#endif
#endif // #ifndef ANDROID_DEFAULT_CODE

#ifndef ANDROID_DEFAULT_CODE 
    struct sched_param sched_p;
    // Change the scheduling policy to SCHED_RR
    sched_getparam(0, &sched_p);
    sched_p.sched_priority = RTPM_PRIO_VIDEO_PLAYBACK_THREAD;

    if (0 != sched_setscheduler(0, SCHED_RR, &sched_p)) {
        ALOGE("@@[SF_PROPERTY]sched_setscheduler fail...");
    }
    else {
        sched_p.sched_priority = 0;
        sched_getparam(0, &sched_p);
        ALOGD("@@[SF_PROPERTY]sched_setscheduler ok..., priority:%d", sched_p.sched_priority);
    } 
#endif
}

void AwesomePlayer::finishAsyncPrepare_l() {
    if (mIsAsyncPrepare) {
        if (mVideoSource == NULL) {
            notifyListener_l(MEDIA_SET_VIDEO_SIZE, 0, 0);
        } else {
            notifyVideoSize_l();
        }

        notifyListener_l(MEDIA_PREPARED);
#ifndef ANDROID_DEFAULT_CODE
        // notify check live to AP to start playing
        if (mCachedSource != NULL) {
            notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_DATA);
        }
#endif // #ifndef ANDROID_DEFAULT_CODE
    }

    mPrepareResult = OK;
    modifyFlags((PREPARING|PREPARE_CANCELLED|PREPARING_CONNECTED), CLEAR);
    modifyFlags(PREPARED, SET);
    mAsyncPrepareEvent = NULL;
    mPreparedCondition.broadcast();

    if (mAudioTearDown) {
        if (mPrepareResult == OK) {
            if (mExtractorFlags & MediaExtractor::CAN_SEEK) {
                seekTo_l(mAudioTearDownPosition);
}

            if (mAudioTearDownWasPlaying) {
                modifyFlags(CACHE_UNDERRUN, CLEAR);
                play_l();
            }
        }
        mAudioTearDown = false;
    }
}

uint32_t AwesomePlayer::flags() const {
    return mExtractorFlags;
}

void AwesomePlayer::postAudioEOS(int64_t delayUs) {
    postCheckAudioStatusEvent(delayUs);
}

void AwesomePlayer::postAudioSeekComplete() {
    postCheckAudioStatusEvent(0);
}

void AwesomePlayer::postAudioTearDown() {
    postAudioTearDownEvent(0);
}

status_t AwesomePlayer::setParameter(int key, const Parcel &request) {
    switch (key) {
        case KEY_PARAMETER_CACHE_STAT_COLLECT_FREQ_MS:
        {
            return setCacheStatCollectFreq(request);
        }
        case KEY_PARAMETER_PLAYBACK_RATE_PERMILLE:
        {
            if (mAudioPlayer != NULL) {
                return mAudioPlayer->setPlaybackRatePermille(request.readInt32());
            } else {
                return NO_INIT;
            }
        }
#ifndef ANDROID_DEFAULT_CODE
		case KEY_PARAMETER_AUDIO_SEEKTABLE:
		{
			request.readInt32(&mEnAudST);
			ALOGV("setParameter mEnAudST %d",mEnAudST);
                        return OK;
		}			
#ifdef MTK_CLEARMOTION_SUPPORT            
		case KEY_PARAMETER_CLEARMOTION_DISABLE:
        {
            int32_t mDisable;			
			request.readInt32(&mDisable);
			if(mDisable)
                mEnClearMotion = 0;
			ALOGI("setParameter mEnClearMotion %d",mEnClearMotion);
            return OK;
        }
#endif
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT		
		case KEY_PARAMETER_SlowMotion_Speed_value:
		{
			request.readInt32(&mSMSpeed);
			ALOGD("setParameter mSMSpeed %d",mSMSpeed);
            return OK;
		}
#endif	
#endif        
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
		case KEY_PARAMETER_DRM_CLIENT_PROC:
		{
			mDrmValue = request.readString8();
			ALOGD("setParameter mDrmValue %s", mDrmValue.string());
			return OK;
		}
#endif
#endif
        default:
        {
            return ERROR_UNSUPPORTED;
        }
    }
}

status_t AwesomePlayer::setCacheStatCollectFreq(const Parcel &request) {
    if (mCachedSource != NULL) {
        int32_t freqMs = request.readInt32();
        ALOGD("Request to keep cache stats in the past %d ms",
            freqMs);
        return mCachedSource->setCacheStatCollectFreq(freqMs);
    }
    return ERROR_UNSUPPORTED;
}

status_t AwesomePlayer::getParameter(int key, Parcel *reply) {
    switch (key) {
    case KEY_PARAMETER_AUDIO_CHANNEL_COUNT:
        {
            int32_t channelCount;
            if (mAudioTrack == 0 ||
                    !mAudioTrack->getFormat()->findInt32(kKeyChannelCount, &channelCount)) {
                channelCount = 0;
            }
            reply->writeInt32(channelCount);
        }
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
	case KEY_PARAMETER_SlowMotion_Speed_value:
		{
			int32_t SpeedValue;
			if (mVideoTrack == 0 ||
                    !mVideoTrack->getFormat()->findInt32(kKeySlowMotionSpeedValue, &SpeedValue)) {
                SpeedValue = 0;
            }
			ALOGD("getparameter = %d",SpeedValue);
			if(SpeedValue > 0){
				reply->writeString16(String16("4,2,1"));
			}
			else {
				reply->writeString16(String16("0"));
			}

		}		
#endif
#endif		
        return OK;
    default:
        {
            return ERROR_UNSUPPORTED;
        }
    }
}

status_t AwesomePlayer::getTrackInfo(Parcel *reply) const {

	ALOGD("func:%s L=%d ",__func__,__LINE__);
    Mutex::Autolock autoLock(mLock);

	if (mExtractor != NULL) {
    size_t trackCount = mExtractor->countTracks();

		ALOGD("func:%s L=%d mTracks.size()=%d",__func__,__LINE__,trackCount);
    if (mTextDriver != NULL) {
        trackCount += mTextDriver->countExternalTracks();
    }
		ALOGD("func:%s L=%d mTracks.size()=%d",__func__,__LINE__,trackCount);

    reply->writeInt32(trackCount);

		ALOGD("func:%s L=%d mTracks.size()=%d",__func__,__LINE__,trackCount);

	    ALOGE("[PANDA] getTrackInfo, %d internal Tracks, %d Tracks\n", mExtractor->countTracks(), trackCount);
    for (size_t i = 0; i < mExtractor->countTracks(); ++i) {

			ALOGD("func:%s L=%d i = %d mExtractor->countTracks()()=%d ",__func__,__LINE__,i,mExtractor->countTracks());
        sp<MetaData> meta = mExtractor->getTrackMetaData(i);

        #ifndef ANDROID_DEFAULT_CODE
        if (meta != NULL) {
        #endif

        const char *_mime;

        CHECK(meta->findCString(kKeyMIMEType, &_mime));

        String8 mime = String8(_mime);
        reply->writeInt32(2); // 2 fields

        if (!strncasecmp(mime.string(), "video/", 6)) {
	            ALOGE("[PANDA] Video Track in Track[%d]\n", i);
            reply->writeInt32(MEDIA_TRACK_TYPE_VIDEO);
        } else if (!strncasecmp(mime.string(), "audio/", 6)) {
	            ALOGE("[PANDA] Audio Track in Track[%d]\n", i);
            reply->writeInt32(MEDIA_TRACK_TYPE_AUDIO);
	        #ifdef MTK_SUBTITLE_SUPPORT
	        } else if (!strncasecmp(mime.string(), "text/", 5)) {
	            ALOGE("[PANDA] Find Text Track in index %d, type %s\n", i, mime.string());
	            reply->writeInt32(MEDIA_TRACK_TYPE_TIMEDTEXT);
	        #else
        } else if (!strcasecmp(mime.string(), MEDIA_MIMETYPE_TEXT_3GPP)) {
            reply->writeInt32(MEDIA_TRACK_TYPE_TIMEDTEXT);
	        #endif
        } else {
            reply->writeInt32(MEDIA_TRACK_TYPE_UNKNOWN);
        }

        const char *lang;
        if (!meta->findCString(kKeyMediaLanguage, &lang)) {
            lang = "und";
        }
        reply->writeString16(String16(lang));
        #ifndef ANDROID_DEFAULT_CODE
        } else { /// Added by mtk40142, sometimes meta is NULL, reset the trackcount
        	ALOGE("meta is NULL ");
        	trackCount--;
        	reply->writeInt32(trackCount);
        }
        #endif
    }
		ALOGD("func:%s L=%d mTracks.size()=%d",__func__,__LINE__,trackCount);

    if (mTextDriver != NULL) {
        mTextDriver->getExternalTrackInfo(reply);
    }
		}
	ALOGD("func:%s L=%d",__func__,__LINE__);
    return OK;
}

status_t AwesomePlayer::selectAudioTrack_l(
        const sp<MediaSource>& source, size_t trackIndex) {

    ALOGI("selectAudioTrack_l: trackIndex=%d, mFlags=0x%x", trackIndex, mFlags);

    {
        Mutex::Autolock autoLock(mStatsLock);
        if ((ssize_t)trackIndex == mActiveAudioTrackIndex) {
            ALOGI("Track %d is active. Does nothing.", trackIndex);
            return OK;
        }
        //mStats.mFlags = mFlags;
    }

    if (mSeeking != NO_SEEK) {
        ALOGE("Selecting a track while seeking is not supported");
        return ERROR_UNSUPPORTED;
    }

    if ((mFlags & PREPARED) == 0) {
        ALOGE("Data source has not finished preparation");
        return ERROR_UNSUPPORTED;
    }

    CHECK(source != NULL);
    bool wasPlaying = (mFlags & PLAYING) != 0;

    pause_l();

    int64_t curTimeUs;
    CHECK_EQ(getPosition(&curTimeUs), (status_t)OK);

    if ((mAudioPlayer == NULL || !(mFlags & AUDIOPLAYER_STARTED))
            && mAudioSource != NULL) {
        // If we had an audio player, it would have effectively
        // taken possession of the audio source and stopped it when
        // _it_ is stopped. Otherwise this is still our responsibility.
        mAudioSource->stop();
    }
    mAudioSource.clear();
    mOmxSource.clear();

    mTimeSource = NULL;

    delete mAudioPlayer;
    mAudioPlayer = NULL;

    modifyFlags(AUDIOPLAYER_STARTED, CLEAR);

    setAudioSource(source);

    modifyFlags(AUDIO_AT_EOS, CLEAR);
    modifyFlags(AT_EOS, CLEAR);

    status_t err;
    if ((err = initAudioDecoder()) != OK) {
        ALOGE("Failed to init audio decoder: 0x%x", err);
        return err;
    }

    mSeekNotificationSent = true;
    seekTo_l(curTimeUs);

    if (wasPlaying) {
        play_l();
    }

    mActiveAudioTrackIndex = trackIndex;

    return OK;
}

status_t AwesomePlayer::selectTrack(size_t trackIndex, bool select) {
    ATRACE_CALL();
    ALOGV("selectTrack: trackIndex = %d and select=%d", trackIndex, select);
    Mutex::Autolock autoLock(mLock);
    size_t trackCount = mExtractor->countTracks();
    if (mTextDriver != NULL) {
        trackCount += mTextDriver->countExternalTracks();
    }
    if (trackIndex >= trackCount) {
        ALOGE("Track index (%d) is out of range [0, %d)", trackIndex, trackCount);
        return ERROR_OUT_OF_RANGE;
    }

    bool isAudioTrack = false;
    if (trackIndex < mExtractor->countTracks()) {
        sp<MetaData> meta = mExtractor->getTrackMetaData(trackIndex);
        const char *mime;
        CHECK(meta->findCString(kKeyMIMEType, &mime));
        isAudioTrack = !strncasecmp(mime, "audio/", 6);
        #ifdef MTK_SUBTITLE_SUPPORT
        if (!isAudioTrack && strncasecmp(mime, "text/", 5) != 0) {
            ALOGE("[PANDA] Track %d is not either audio or timed text", trackIndex);
            return ERROR_UNSUPPORTED;
        }
        #else
        if (!isAudioTrack && strcasecmp(mime, MEDIA_MIMETYPE_TEXT_3GPP) != 0) {
            ALOGE("Track %d is not either audio or timed text", trackIndex);
            return ERROR_UNSUPPORTED;
        }
        #endif
    }

    if (isAudioTrack) {
        if (!select) {
            ALOGE("Deselect an audio track (%d) is not supported", trackIndex);
            return ERROR_UNSUPPORTED;
        }
        return selectAudioTrack_l(mExtractor->getTrack(trackIndex), trackIndex);
    }

    // Timed text track handling
    if (mTextDriver == NULL) {
        return INVALID_OPERATION;
    }

    status_t err = OK;
    if (select) {
    	 ALOGE("[PANDA] Select Text Track, index = %d\n", trackIndex);
        err = mTextDriver->selectTrack(trackIndex);
        if (err == OK) {
            modifyFlags(TEXTPLAYER_INITIALIZED, SET);
            if (mFlags & PLAYING && !(mFlags & TEXT_RUNNING)) {
                mTextDriver->start();
                modifyFlags(TEXT_RUNNING, SET);
            }
        }
    } else {
        ALOGE("[PANDA] Un Select Text Track, index = %d\n", trackIndex);
        err = mTextDriver->unselectTrack(trackIndex);
        if (err == OK) {
            modifyFlags(TEXTPLAYER_INITIALIZED, CLEAR);
            modifyFlags(TEXT_RUNNING, CLEAR);
        }
    }
    return err;
}

size_t AwesomePlayer::countTracks() const {
    return mExtractor->countTracks() + mTextDriver->countExternalTracks();
}

status_t AwesomePlayer::setVideoScalingMode(int32_t mode) {
    Mutex::Autolock lock(mLock);
    return setVideoScalingMode_l(mode);
}

status_t AwesomePlayer::setVideoScalingMode_l(int32_t mode) {
    mVideoScalingMode = mode;
    if (mNativeWindow != NULL) {
        status_t err = native_window_set_scaling_mode(
                mNativeWindow.get(), mVideoScalingMode);
        if (err != OK) {
            ALOGW("Failed to set scaling mode: %d", err);
        }
        return err;
    }
    return OK;
}

status_t AwesomePlayer::invoke(const Parcel &request, Parcel *reply) {
    ATRACE_CALL();
    if (NULL == reply) {
        return android::BAD_VALUE;
    }
    int32_t methodId;
    status_t ret = request.readInt32(&methodId);
    if (ret != android::OK) {
        return ret;
    }
    switch(methodId) {
        case INVOKE_ID_SET_VIDEO_SCALING_MODE:
        {
            int mode = request.readInt32();
            return setVideoScalingMode(mode);
        }

        case INVOKE_ID_GET_TRACK_INFO:
        {
            return getTrackInfo(reply);
        }
        case INVOKE_ID_ADD_EXTERNAL_SOURCE:
        {
            Mutex::Autolock autoLock(mLock);
            if (mTextDriver == NULL) {
                mTextDriver = new TimedTextDriver(mListener);
            }
            // String values written in Parcel are UTF-16 values.
            String8 uri(request.readString16());
            String8 mimeType(request.readString16());
            size_t nTracks = countTracks();
            ALOGE("[PANDA] Add External Subtitle source, type = %s\n", mimeType.string());
            return mTextDriver->addOutOfBandTextSource(nTracks, uri, mimeType);
        }
        case INVOKE_ID_ADD_EXTERNAL_SOURCE_FD:
        {
            Mutex::Autolock autoLock(mLock);
            if (mTextDriver == NULL) {
                mTextDriver = new TimedTextDriver(mListener);
            }
            int fd         = request.readFileDescriptor();
            off64_t offset = request.readInt64();
            off64_t length  = request.readInt64();
            String8 mimeType(request.readString16());
            size_t nTracks = countTracks();
            ALOGE("[PANDA] Add External Subtitle source fd, type = %s\n", mimeType.string());
            return mTextDriver->addOutOfBandTextSource(
                    nTracks, fd, offset, length, mimeType);
        }
        case INVOKE_ID_SELECT_TRACK:
        {
            int trackIndex = request.readInt32();
            ALOGE("[PANDA] selectTrack, track index = %d\n", trackIndex);
            return selectTrack(trackIndex, true /* select */);
        }
        case INVOKE_ID_UNSELECT_TRACK:
        {
            int trackIndex = request.readInt32();
            ALOGE("[PANDA] un selectTrack, track index = %d\n", trackIndex);
            return selectTrack(trackIndex, false /* select */);
        }
        default:
        {
            return ERROR_UNSUPPORTED;
        }
    }
    // It will not reach here.
    return OK;
}

bool AwesomePlayer::isStreamingHTTP() const {
    return mCachedSource != NULL || mWVMExtractor != NULL;
}

status_t AwesomePlayer::dump(int fd, const Vector<String16> &args) const {
    Mutex::Autolock autoLock(mStatsLock);

    FILE *out = fdopen(dup(fd), "w");

    fprintf(out, " AwesomePlayer\n");
    if (mStats.mFd < 0) {
        fprintf(out, "  URI(suppressed)");
    } else {
        fprintf(out, "  fd(%d)", mStats.mFd);
    }

    fprintf(out, ", flags(0x%08x)", mStats.mFlags);

    if (mStats.mBitrate >= 0) {
        fprintf(out, ", bitrate(%lld bps)", mStats.mBitrate);
    }

    fprintf(out, "\n");

    for (size_t i = 0; i < mStats.mTracks.size(); ++i) {
        const TrackStat &stat = mStats.mTracks.itemAt(i);

        fprintf(out, "  Track %d\n", i + 1);
        fprintf(out, "   MIME(%s)", stat.mMIME.string());

        if (!stat.mDecoderName.isEmpty()) {
            fprintf(out, ", decoder(%s)", stat.mDecoderName.string());
        }

        fprintf(out, "\n");

        if ((ssize_t)i == mStats.mVideoTrackIndex) {
            fprintf(out,
                    "   videoDimensions(%d x %d), "
                    "numVideoFramesDecoded(%lld), "
                    "numVideoFramesDropped(%lld)\n",
                    mStats.mVideoWidth,
                    mStats.mVideoHeight,
                    mStats.mNumVideoFramesDecoded,
                    mStats.mNumVideoFramesDropped);
        }
    }

#if 0 
	TimedTextDriver *driver = NULL;
	if (mTextDriver == NULL) {
        driver= new TimedTextDriver(mListener);
		driver->addOutOfBandTextSource(0, "file://system/lib/test.srt", MEDIA_MIMETYPE_TEXT_SUBRIP);
		ALOGE("[--dbg--] Parsing test.srt file successfully!");
		driver->addOutOfBandTextSource(0, "file://system/lib/test_16.srt", MEDIA_MIMETYPE_TEXT_SUBRIP);
		ALOGE("[--dbg--] Parsing test_16.srt file successfully!");
		//driver->addOutOfBandTextSource(1, "file://system/lib/real.srt", MEDIA_MIMETYPE_TEXT_SUBRIP);
		//ALOGE("[--dbg--] Parsing real.srt file successfully!");
		driver->addOutOfBandTextSource(2, "file://system/lib/test.mpl", MEDIA_MIMETYPE_TEXT_SUBMPL);
		ALOGE("[--dbg--] Parsing test.mpl file successfully!");
		driver->addOutOfBandTextSource(2, "file://system/lib/test_16.mpl", MEDIA_MIMETYPE_TEXT_SUBMPL);
		ALOGE("[--dbg--] Parsing test_16.mpl file successfully!");
		driver->addOutOfBandTextSource(3, "file://system/lib/test.sub", MEDIA_MIMETYPE_TEXT_SUB);
		ALOGE("[--dbg--] Parsing test.sub file successfully!");
		driver->addOutOfBandTextSource(3, "file://system/lib/test_16.sub", MEDIA_MIMETYPE_TEXT_SUB);
		ALOGE("[--dbg--] Parsing test_16.sub file successfully!");
		driver->addOutOfBandTextSource(4, "file://system/lib/test_sp.sub", MEDIA_MIMETYPE_TEXT_SUB);
		ALOGE("[--dbg--] Parsing test_sp.txt file successfully!");
		driver->addOutOfBandTextSource(4, "file://system/lib/test_sp_16.sub", MEDIA_MIMETYPE_TEXT_SUB);
		ALOGE("[--dbg--] Parsing test_sp_16.txt file successfully!");
		//driver->addOutOfBandTextSource(5, "file://system/lib/test.smi", MEDIA_MIMETYPE_TEXT_SUBSMI);
		//ALOGE("[--dbg--] Parsing test.smi file successfully!");
		free(mTextDriver);
    }
#endif 
    fclose(out);
    out = NULL;

    return OK;
}

void AwesomePlayer::modifyFlags(unsigned value, FlagMode mode) {
    switch (mode) {
        case SET:
            mFlags |= value;
            break;
        case CLEAR:
            if ((value & CACHE_UNDERRUN) && (mFlags & CACHE_UNDERRUN)) {
                notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_END);
            }
            mFlags &= ~value;
            break;
        case ASSIGN:
            mFlags = value;
            break;
        default:
            TRESPASS();
    }

    {
        Mutex::Autolock autoLock(mStatsLock);
        mStats.mFlags = mFlags;
    }
}

void AwesomePlayer::onAudioTearDownEvent() {

    Mutex::Autolock autoLock(mLock);
    if (!mAudioTearDownEventPending) {
        return;
    }
    mAudioTearDownEventPending = false;

    ALOGV("onAudioTearDownEvent");

    // stream info is cleared by reset_l() so copy what we need
    mAudioTearDownWasPlaying = (mFlags & PLAYING);
    KeyedVector<String8, String8> uriHeaders(mUriHeaders);
    sp<DataSource> fileSource(mFileSource);

    mStatsLock.lock();
    String8 uri(mStats.mURI);
    mStatsLock.unlock();

    // get current position so we can start recreated stream from here
    getPosition(&mAudioTearDownPosition);

    // Reset and recreate
    reset_l();

    status_t err;

    if (fileSource != NULL) {
        mFileSource = fileSource;
        err = setDataSource_l(fileSource);
    } else {
        err = setDataSource_l(uri, &uriHeaders);
    }

    mFlags |= PREPARING;
    if ( err != OK ) {
        // This will force beingPrepareAsync_l() to notify
        // a MEDIA_ERROR to the client and abort the prepare
        mFlags |= PREPARE_CANCELLED;
    }

    mAudioTearDown = true;
    mIsAsyncPrepare = true;

    // Call prepare for the host decoding
    beginPrepareAsync_l();
}

#ifndef ANDROID_DEFAULT_CODE
bool AwesomePlayer::isNotifyDuration()
{
	if(mEnAudST==1)
	  return true;
	else
		return false;	
}
void AwesomePlayer::postDurationUpdateEvent(int64_t duration)
{
		postDurationUpdateEvent_l(duration);
}
void AwesomePlayer::postDurationUpdateEvent_l(int64_t duration)
{
	if(mDurationUpdateEventPending)
		return ;
	mDurationUpdateEventPending=true;
        mDurationUs = duration;
	mQueue.postEvent(mDurationUpdateEvent);
}

void AwesomePlayer::OnDurationUpdate(){
	Mutex::Autolock autoLock(mLock);
	//for MtkAACExtractor
	if(mAudioTrack != NULL)
	{
		sp<MetaData> meta = mAudioTrack->getFormat();
		const char *mime;
		CHECK(meta->findCString(kKeyMIMEType, &mime));
		if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_AAC, mime)) 
		{
			int32_t nIsAACADIF;
			if (meta->findInt32(kKeyIsAACADIF, &nIsAACADIF))
			{
				if(0 != nIsAACADIF)
				{
					mExtractorFlags |= (MediaExtractor::CAN_SEEK_BACKWARD | MediaExtractor::CAN_SEEK_FORWARD | MediaExtractor::CAN_SEEK);
					ALOGW("AwesomePlayer::OnDurationUpdate--ADIF seekable");
				}
			}
		}  
	}
	if(!mDurationUpdateEventPending)
		return ;
	mDurationUpdateEventPending=false;
	notifyListener_l(MEDIA_DURATION_UPDATE,mDurationUs/1000,0);
}

sp<MetaData> AwesomePlayer::getMetaData() const {
    return mMetaData;
}

status_t AwesomePlayer::getVideoDimensions(
        int32_t *width, int32_t *height) const {
    Mutex::Autolock autoLock(mStatsLock);

    if (mStats.mVideoWidth < 0 || mStats.mVideoHeight < 0) {
        return UNKNOWN_ERROR;
    }

    *width = mStats.mVideoWidth;
    *height = mStats.mVideoHeight;

    return OK;
}
status_t AwesomePlayer::tryReadIfNeccessary_l() {
    if ((mCachedSource == NULL) || (mVideoSource == NULL)) {
        return OK;
    }
    sp<MetaData> meta = mVideoTrack->getFormat();
    int32_t nSupported = 0;
    status_t tryReadResult = OK;
    if (meta->findInt32(kKeySupportTryRead, &nSupported) && (nSupported == 1)) {
    //if (1) {
        MediaSource::ReadOptions opt;
        opt.setSeekTo(mSeekTimeUs, MediaSource::ReadOptions::SEEK_TRY_READ);
        MediaBuffer *pBuffer;
        tryReadResult = mVideoTrack->read(&pBuffer, &opt);

    }
    ALOGD("the video track try read nSupported = %d, mFlags = 0x%x", nSupported, mFlags);
    if (!(mFlags & PGDL_NONINTERLEAVE)) 
        return tryReadResult;

    /* try read audio track */
    status_t tryReadResult2 = OK;
    meta = mAudioTrack->getFormat();
    if (meta->findInt32(kKeySupportTryRead, &nSupported) && (nSupported == 1)) {        
        MediaSource::ReadOptions opt;
        opt.setSeekTo(mSeekTimeUs, MediaSource::ReadOptions::SEEK_TRY_READ);
        MediaBuffer *pBuffer;
        tryReadResult2 = mAudioTrack->read(&pBuffer, &opt);
    }
        
    ALOGD("the audio track try read nSupported = %d, mFlags = 0x%x", nSupported, mFlags);
    if (tryReadResult == OK && tryReadResult2 == OK)
        return OK;
    return INFO_TRY_READ_FAIL;
}

void AwesomePlayer::disconnectSafeIfNeccesary() {
    Mutex::Autolock autoLock(mMiscStateLock);
    if (mConnectingDataSource != NULL) {
       ALOGD("reset: disconnect mConnectingDataSource");
		if (mCachedSource != NULL) {
			mCachedSource->finishCache();
		}
        mConnectingDataSource->disconnect();
    }
#ifndef ANDROID_DEFAULT_CODE
    if (mConnectingDataSource2 != NULL) {
        mConnectingDataSource2->disconnect();
    }
#endif
}


bool AwesomePlayer::removeSpecificHeaders(const String8 MyKey, KeyedVector<String8, String8> *headers, String8 *pMyHeader) {
   ALOGD("removeSpecificHeaders %s", MyKey.string());
    *pMyHeader = "";
    if (headers != NULL) {
        ssize_t index;
        if ((index = headers->indexOfKey(MyKey)) >= 0) {
            *pMyHeader = headers->valueAt(index);
            headers->removeItemsAt(index);
           ALOGD("special headers: %s = %s", MyKey.string(), pMyHeader->string());
            return true;
        }
    }
    return false;
}

#endif
}  // namespace android
