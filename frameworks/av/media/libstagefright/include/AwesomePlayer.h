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

#ifndef AWESOME_PLAYER_H_

#define AWESOME_PLAYER_H_

#include "HTTPBase.h"
#include "TimedEventQueue.h"

#include <media/MediaPlayerInterface.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/OMXClient.h>
#include <media/stagefright/TimeSource.h>
#include <media/stagefright/MetaData.h>
#include <utils/threads.h>
#include <drm/DrmManagerClient.h>

#ifndef ANDROID_DEFAULT_CODE
#include <media/stagefright/MetaData.h>
#endif
namespace android {

struct AudioPlayer;
struct DataSource;
struct MediaBuffer;
struct MediaExtractor;
struct MediaSource;
struct NuCachedSource2;
struct IGraphicBufferProducer;

class DrmManagerClinet;
class DecryptHandle;

class TimedTextDriver;
struct WVMExtractor;

#ifndef ANDROID_DEFAULT_CODE 
struct ALooper;
struct MetaData;
#endif
struct AwesomeRenderer : public RefBase {
    AwesomeRenderer() {}

    virtual void render(MediaBuffer *buffer) = 0;

private:
    AwesomeRenderer(const AwesomeRenderer &);
    AwesomeRenderer &operator=(const AwesomeRenderer &);
};

struct AwesomePlayer {
    AwesomePlayer();
    ~AwesomePlayer();

    void setListener(const wp<MediaPlayerBase> &listener);
    void setUID(uid_t uid);

    status_t setDataSource(
            const char *uri,
            const KeyedVector<String8, String8> *headers = NULL);

    status_t setDataSource(int fd, int64_t offset, int64_t length);

    status_t setDataSource(const sp<IStreamSource> &source);

    void reset();

    status_t prepare();
    status_t prepare_l();
    status_t prepareAsync();
    status_t prepareAsync_l();

    status_t play();
#ifndef ANDROID_DEFAULT_CODE
    status_t pause(bool stop = false);
	
	bool isPlaying_l() const;
#else
    status_t pause();
#endif // #ifndef ANDROID_DEFAULT_CODE

    bool isPlaying() const;

    status_t setSurfaceTexture(const sp<IGraphicBufferProducer> &bufferProducer);
    void setAudioSink(const sp<MediaPlayerBase::AudioSink> &audioSink);
    status_t setLooping(bool shouldLoop);

    status_t getDuration(int64_t *durationUs);
    status_t getPosition(int64_t *positionUs);

    status_t setParameter(int key, const Parcel &request);
    status_t getParameter(int key, Parcel *reply);
    status_t invoke(const Parcel &request, Parcel *reply);
    status_t setCacheStatCollectFreq(const Parcel &request);

    status_t seekTo(int64_t timeUs);

    // This is a mask of MediaExtractor::Flags.
    uint32_t flags() const;

    void postAudioEOS(int64_t delayUs = 0ll);
    void postAudioSeekComplete();
    void postAudioTearDown();
    status_t dump(int fd, const Vector<String16> &args) const;
#ifndef ANDROID_DEFAULT_CODE 
    void postDurationUpdateEvent(int64_t duration);
    void postDurationUpdateEvent_l(int64_t duration);
    bool isNotifyDuration();
	//qian  
	void mtk_omx_get_current_time(int64_t* pReal_time);
    sp<MetaData> getMetaData() const;
    status_t getVideoDimensions( int32_t *width, int32_t *height) const;
#endif 

private:
    friend struct AwesomeEvent;
    friend struct PreviewPlayer;

    enum {
        PLAYING             = 0x01,
        LOOPING             = 0x02,
        FIRST_FRAME         = 0x04,
        PREPARING           = 0x08,
        PREPARED            = 0x10,
        AT_EOS              = 0x20,
        PREPARE_CANCELLED   = 0x40,
        CACHE_UNDERRUN      = 0x80,
        AUDIO_AT_EOS        = 0x0100,
        VIDEO_AT_EOS        = 0x0200,
        AUTO_LOOPING        = 0x0400,

        // We are basically done preparing but are currently buffering
        // sufficient data to begin playback and finish the preparation phase
        // for good.
        PREPARING_CONNECTED = 0x0800,

        // We're triggering a single video event to display the first frame
        // after the seekpoint.
        SEEK_PREVIEW        = 0x1000,

        AUDIO_RUNNING       = 0x2000,
        AUDIOPLAYER_STARTED = 0x4000,

        INCOGNITO           = 0x8000,

        TEXT_RUNNING        = 0x10000,
        TEXTPLAYER_INITIALIZED  = 0x20000,

        SLOW_DECODER_HACK   = 0x40000,
#ifndef ANDROID_DEFAULT_CODE
        PGDL_NONINTERLEAVE = 0x01000000,    
        PAUSING          = 0x02000000,
	    EOS_HANDLING     = 0x04000000,
        CACHE_MISSING    = 0x80000000,
#endif
    };

    mutable Mutex mLock;
    Mutex mMiscStateLock;
    mutable Mutex mStatsLock;
    Mutex mAudioLock;

    OMXClient mClient;
    TimedEventQueue mQueue;
    bool mQueueStarted;
    wp<MediaPlayerBase> mListener;
    bool mUIDValid;
    uid_t mUID;

    sp<ANativeWindow> mNativeWindow;
    sp<MediaPlayerBase::AudioSink> mAudioSink;

    SystemTimeSource mSystemTimeSource;
    TimeSource *mTimeSource;

    String8 mUri;
    KeyedVector<String8, String8> mUriHeaders;

    sp<DataSource> mFileSource;

    sp<MediaSource> mVideoTrack;
    sp<MediaSource> mVideoSource;
    sp<AwesomeRenderer> mVideoRenderer;
    bool mVideoRenderingStarted;
    bool mVideoRendererIsPreview;
    int32_t mMediaRenderingStartGeneration;
    int32_t mStartGeneration;

    ssize_t mActiveAudioTrackIndex;
    sp<MediaSource> mAudioTrack;
    sp<MediaSource> mOmxSource;
    sp<MediaSource> mAudioSource;
    AudioPlayer *mAudioPlayer;
    int64_t mDurationUs;

    int32_t mDisplayWidth;
    int32_t mDisplayHeight;
    int32_t mVideoScalingMode;

    uint32_t mFlags;
    uint32_t mExtractorFlags;
    uint32_t mSinceLastDropped;

    int64_t mTimeSourceDeltaUs;
    int64_t mVideoTimeUs;

    enum SeekType {
        NO_SEEK,
        SEEK,
        SEEK_VIDEO_ONLY
    };
    SeekType mSeeking;

    bool mSeekNotificationSent;
    int64_t mSeekTimeUs;

    int64_t mBitrate;  // total bitrate of the file (in bps) or -1 if unknown.

    bool mWatchForAudioSeekComplete;
    bool mWatchForAudioEOS;

    sp<TimedEventQueue::Event> mVideoEvent;
    bool mVideoEventPending;
    sp<TimedEventQueue::Event> mStreamDoneEvent;
    bool mStreamDoneEventPending;
    sp<TimedEventQueue::Event> mBufferingEvent;
    bool mBufferingEventPending;
    sp<TimedEventQueue::Event> mCheckAudioStatusEvent;
    bool mAudioStatusEventPending;
    sp<TimedEventQueue::Event> mVideoLagEvent;
    bool mVideoLagEventPending;
    sp<TimedEventQueue::Event> mAudioTearDownEvent;
    bool mAudioTearDownEventPending;
    sp<TimedEventQueue::Event> mAsyncPrepareEvent;
#ifndef ANDROID_DEFAULT_CODE
    sp<TimedEventQueue::Event> mDurationUpdateEvent;
    bool mDurationUpdateEventPending;
#endif
#ifndef ANDROID_DEFAULT_CODE //for HTTP Streaming
    //work around alps00072030: ANR when continously pause/play
    int32_t mCachedSourcePauseResponseState;
    enum {
        PauseTimeOut = 1,    //call pause is timeout
        PausePending = 2,    //pause is pending
    };
#endif

    Condition mPreparedCondition;
    bool mIsAsyncPrepare;
    status_t mPrepareResult;
    status_t mStreamDoneStatus;

    void postVideoEvent_l(int64_t delayUs = -1);
    void postBufferingEvent_l();
    void postStreamDoneEvent_l(status_t status);
    void postCheckAudioStatusEvent(int64_t delayUs);
    void postVideoLagEvent_l();
    void postAudioTearDownEvent(int64_t delayUs);

    status_t play_l();

    MediaBuffer *mVideoBuffer;

    sp<HTTPBase> mConnectingDataSource;
    sp<NuCachedSource2> mCachedSource;

    DrmManagerClient *mDrmManagerClient;
    sp<DecryptHandle> mDecryptHandle;
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
    bool mIsCurrentComplete; // OMA DRM v1 implementation
    String8 mDrmValue;
#endif
    // http non-interlace
    sp<HTTPBase> mConnectingDataSource2;
    sp<NuCachedSource2> mCachedSourceSecond;
    sp<NuCachedSource2> mCachedSourceMain;
#endif

    int64_t mLastVideoTimeUs;
    TimedTextDriver *mTextDriver;

    sp<WVMExtractor> mWVMExtractor;
    sp<MediaExtractor> mExtractor;
    
#ifdef MTK_CLEARMOTION_SUPPORT
    bool mMJCPauseDelay;
	Condition mMJCCondition;    
#endif    

    status_t setDataSource_l(
            const char *uri,
            const KeyedVector<String8, String8> *headers = NULL);

    status_t setDataSource_l(const sp<DataSource> &dataSource);
    status_t setDataSource_l(const sp<MediaExtractor> &extractor);
    void reset_l();
    status_t seekTo_l(int64_t timeUs);
    status_t pause_l(bool at_eos = false);
    void initRenderer_l();
    void notifyVideoSize_l();
    void seekAudioIfNecessary_l();

    void cancelPlayerEvents(bool keepNotifications = false);

    void setAudioSource(sp<MediaSource> source);
    status_t initAudioDecoder();


    void setVideoSource(sp<MediaSource> source);
    status_t initVideoDecoder(uint32_t flags = 0);

    void addTextSource_l(size_t trackIndex, const sp<MediaSource>& source);

    void onStreamDone();
#ifndef ANDROID_DEFAULT_CODE
    void OnDurationUpdate();
#endif
    void notifyListener_l(int msg, int ext1 = 0, int ext2 = 0);

    void onVideoEvent();
    void onBufferingUpdate();
#ifndef ANDROID_DEFAULT_CODE
    void onBufferingUpdate_l();
    void onBufferingUpdateCachedSource_l();
    status_t tryReadIfNeccessary_l();
    void disconnectSafeIfNeccesary();
    bool removeSpecificHeaders(const String8 MyKey, KeyedVector<String8, String8> *headers, String8 *MyHeader);
#endif // #ifndef ANDROID_DEFAULT_CODE
    void onCheckAudioStatus();
    void onPrepareAsyncEvent();
    void abortPrepare(status_t err);
    void finishAsyncPrepare_l();
    void onVideoLagUpdate();
    void onAudioTearDownEvent();

    void beginPrepareAsync_l();

    bool getCachedDuration_l(int64_t *durationUs, bool *eos);

    status_t finishSetDataSource_l();

    static bool ContinuePreparation(void *cookie);

    bool getBitrate(int64_t *bitrate);

    void finishSeekIfNecessary(int64_t videoTimeUs);
    void ensureCacheIsFetching_l();

    void notifyIfMediaStarted_l();
    void createAudioPlayer_l();
    status_t startAudioPlayer_l(bool sendErrorNotification = true);

    void shutdownVideoDecoder_l();
    status_t setNativeWindow_l(const sp<ANativeWindow> &native);

    bool isStreamingHTTP() const;
    void sendCacheStats();
    void checkDrmStatus(const sp<DataSource>& dataSource);

    enum FlagMode {
        SET,
        CLEAR,
        ASSIGN
    };
    void modifyFlags(unsigned value, FlagMode mode);

    struct TrackStat {
        String8 mMIME;
        String8 mDecoderName;
    };

    // protected by mStatsLock
    struct Stats {
        int mFd;
        String8 mURI;
        int64_t mBitrate;

        // FIXME:
        // These two indices are just 0 or 1 for now
        // They are not representing the actual track
        // indices in the stream.
        ssize_t mAudioTrackIndex;
        ssize_t mVideoTrackIndex;

        int64_t mNumVideoFramesDecoded;
        int64_t mNumVideoFramesDropped;
        int32_t mVideoWidth;
        int32_t mVideoHeight;
        uint32_t mFlags;
        Vector<TrackStat> mTracks;
    } mStats;

    bool    mOffloadAudio;
    bool    mAudioTearDown;
    bool    mAudioTearDownWasPlaying;
    int64_t mAudioTearDownPosition;

    status_t setVideoScalingMode(int32_t mode);
    status_t setVideoScalingMode_l(int32_t mode);
    status_t getTrackInfo(Parcel* reply) const;

    status_t selectAudioTrack_l(const sp<MediaSource>& source, size_t trackIndex);

    // when select is true, the given track is selected.
    // otherwise, the given track is unselected.
    status_t selectTrack(size_t trackIndex, bool select);

    size_t countTracks() const;

    AwesomePlayer(const AwesomePlayer &);
    AwesomePlayer &operator=(const AwesomePlayer &);
#ifndef ANDROID_DEFAULT_CODE
    sp<ALooper> mLooper;
	int64_t mAVSyncTimeUs;	//qian  	
	int64_t mAVSyncThreshold;
	sp<MetaData> mMetaData;
	uint32_t  mFRAME_DROP_FREQ;
	uint32_t  mLateMargin;
	 bool mPrerollEnable;
	 int64_t mLastPositionUs;
         int64_t mAdjustPos;
    bool mFirstSubmit;
    uint32_t mVdecQuirks;
    bool mAudioPadEnable;
    bool mAudioNormalEOS;
    int64_t mLastAudioSeekUs;
    bool mFinishAgain;
    bool mStopped;
    int64_t mHighWaterMarkUs;
    int32_t mEnAudST;
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT	
	int64_t mSync_time;
	int32_t mSMSpeed;
	int32_t mSpeedValue;
#endif	
#ifdef MTK_CLEARMOTION_SUPPORT
    int32_t mEnClearMotion;
#endif
    int64_t mLatencyUs;
    MediaBuffer *mFirstVideoBuffer;
    status_t mFirstVideoBufferStatus;
    bool mInRescheding;
    enum{
	FINAL_OK=0,
	FINAL_HAS_UNSUPPORT_VIDEO=1,
	FINAL_HAS_UNSUPPORT_AUDIO=2,
	FINAL_UNKNOW=0xFF,
    } ;

    uint32_t  mFinalStopFlag;
    bool mIsLargeMetaData;
    uint32_t  mThrottleVideoBufRel;
#endif 
};

}  // namespace android

#endif  // AWESOME_PLAYER_H_
