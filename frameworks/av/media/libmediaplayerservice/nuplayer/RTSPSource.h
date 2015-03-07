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

#ifndef RTSP_SOURCE_H_

#define RTSP_SOURCE_H_

#include "NuPlayerSource.h"

#include "ATSParser.h"

#include <media/stagefright/foundation/AHandlerReflector.h>

namespace android {

struct ALooper;
struct AnotherPacketSource;
struct MyHandler;
struct SDPLoader;

struct NuPlayer::RTSPSource : public NuPlayer::Source {
    RTSPSource(
            const sp<AMessage> &notify,
            const char *url,
            const KeyedVector<String8, String8> *headers,
            bool uidValid = false,
            uid_t uid = 0,
            bool isSDP = false);

    virtual void prepareAsync();
    virtual void start();
    virtual void stop();
    virtual void pause();
    virtual void resume();

    virtual status_t feedMoreTSData();

    virtual status_t dequeueAccessUnit(bool audio, sp<ABuffer> *accessUnit);

    virtual status_t getDuration(int64_t *durationUs);
    virtual status_t seekTo(int64_t seekTimeUs);
	
#ifndef ANDROID_DEFAULT_CODE
    virtual void setParams(const sp<MetaData>& meta);
	
	void setSDP(sp<RefBase> &sdp);
	sp<RefBase> msdp;
    void stopTrack(bool audio);
#endif

    void onMessageReceived(const sp<AMessage> &msg);

protected:
    virtual ~RTSPSource();

    virtual sp<MetaData> getFormatMeta(bool audio);

private:
    enum {
        kWhatNotify          = 'noti',
        kWhatDisconnect      = 'disc',
        kWhatPerformSeek     = 'seek',
#ifndef ANDROID_DEFAULT_CODE
        kWhatSendPlay        = 'play',
        kWhatSendPause	     = 'paus',
        kWhatBufferingUpdate = 'buff',
        kWhatStopTrack       = 'rmtk'
#endif
    };

    enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        SEEKING,
    };

    enum Flags {
        // Don't log any URLs.
        kFlagIncognito = 1,
    };

    struct TrackInfo {
        sp<AnotherPacketSource> mSource;

        int32_t mTimeScale;
        uint32_t mRTPTime;
        int64_t mNormalPlaytimeUs;
        bool mNPTMappingValid;
    };

    AString mURL;
    KeyedVector<String8, String8> mExtraHeaders;
    bool mUIDValid;
    uid_t mUID;
    uint32_t mFlags;
    bool mIsSDP;
    State mState;
    status_t mFinalResult;
    uint32_t mDisconnectReplyID;
    bool mBuffering;

    sp<ALooper> mLooper;
    sp<AHandlerReflector<RTSPSource> > mReflector;
    sp<MyHandler> mHandler;
    sp<SDPLoader> mSDPLoader;

    Vector<TrackInfo> mTracks;
    sp<AnotherPacketSource> mAudioTrack;
    sp<AnotherPacketSource> mVideoTrack;

    sp<ATSParser> mTSParser;

#ifndef ANDROID_DEFAULT_CODE
    status_t generalBufferedDurationUs(int64_t *durationUs);
    void notifyAsyncDone(uint32_t notify, status_t err = OK);
	bool removeSpecificHeaders(const String8 MyKey, KeyedVector<String8, String8> *headers, String8 *pMyHeader);
	int64_t mHighWaterMarkUs;
    bool mQuitRightNow;
    //The following are for sync call
    // >>> 
    Mutex mLock;
    Condition mCondition;
    status_t mSyncCallResult;
    bool mSyncCallDone;
    void prepareSyncCall();
    void completeSyncCall(const sp<AMessage>& msg);
    status_t finishSyncCall();
    // <<<
   
    //The following are sync call method, using prepareSyncCall+finishSyncCall
    status_t preSeekSync(int64_t timeUs);
    int64_t mLastSeekCompletedTimeUs;

	//for bitrate adaptation
    size_t m_BufQueSize; //Whole Buffer queue size 
    size_t m_TargetTime;  // target protected time of buffer queue duration for interrupt-free playback 
    // mtk80902: standalone looper for MyHander
    sp<ALooper> mHandlerLooper;
#endif
    int32_t mSeekGeneration;

    int64_t mEOSTimeoutAudio;
    int64_t mEOSTimeoutVideo;

    sp<AnotherPacketSource> getSource(bool audio);

    void onConnected();
    void onSDPLoaded(const sp<AMessage> &msg);
    void onDisconnected(const sp<AMessage> &msg);
    void finishDisconnectIfPossible();

    void performSeek(int64_t seekTimeUs);

    bool haveSufficientDataOnAllTracks();

    void setEOSTimeout(bool audio, int64_t timeout);

    DISALLOW_EVIL_CONSTRUCTORS(RTSPSource);
};

}  // namespace android

#endif  // RTSP_SOURCE_H_
