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
#define LOG_TAG "NuPlayer"
#include <utils/Log.h>

#include "NuPlayer.h"

#include "HTTPLiveSource.h"
#include "NuPlayerDecoder.h"
#include "NuPlayerDriver.h"
#include "NuPlayerRenderer.h"
#include "NuPlayerSource.h"
#include "RTSPSource.h"
#include "StreamingSource.h"
#include "GenericSource.h"
#include "mp4/MP4Source.h"

#include "ATSParser.h"

#include <cutils/properties.h> // for property_get
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/ACodec.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <gui/IGraphicBufferProducer.h>

#include "avc_utils.h"

#include "ESDS.h"
#include <media/stagefright/Utils.h>

#ifndef ANDROID_DEFAULT_CODE
#include <media/stagefright/OMXCodec.h>
#endif

namespace android {

struct NuPlayer::Action : public RefBase {
    Action() {}

    virtual void execute(NuPlayer *player) = 0;

private:
    DISALLOW_EVIL_CONSTRUCTORS(Action);
};

struct NuPlayer::SeekAction : public Action {
    SeekAction(int64_t seekTimeUs)
        : mSeekTimeUs(seekTimeUs) {
    }

    virtual void execute(NuPlayer *player) {
        player->performSeek(mSeekTimeUs);
    }

private:
    int64_t mSeekTimeUs;

    DISALLOW_EVIL_CONSTRUCTORS(SeekAction);
};

struct NuPlayer::SetSurfaceAction : public Action {
    SetSurfaceAction(const sp<NativeWindowWrapper> &wrapper)
        : mWrapper(wrapper) {
    }

    virtual void execute(NuPlayer *player) {
        player->performSetSurface(mWrapper);
    }

private:
    sp<NativeWindowWrapper> mWrapper;

    DISALLOW_EVIL_CONSTRUCTORS(SetSurfaceAction);
};

struct NuPlayer::ShutdownDecoderAction : public Action {
    ShutdownDecoderAction(bool audio, bool video)
        : mAudio(audio),
          mVideo(video) {
    }

    virtual void execute(NuPlayer *player) {
        player->performDecoderShutdown(mAudio, mVideo);
    }

private:
    bool mAudio;
    bool mVideo;

    DISALLOW_EVIL_CONSTRUCTORS(ShutdownDecoderAction);
};

struct NuPlayer::PostMessageAction : public Action {
    PostMessageAction(const sp<AMessage> &msg)
        : mMessage(msg) {
    }

    virtual void execute(NuPlayer *) {
        mMessage->post();
    }

private:
    sp<AMessage> mMessage;

    DISALLOW_EVIL_CONSTRUCTORS(PostMessageAction);
};

// Use this if there's no state necessary to save in order to execute
// the action.
struct NuPlayer::SimpleAction : public Action {
    typedef void (NuPlayer::*ActionFunc)();

    SimpleAction(ActionFunc func)
        : mFunc(func) {
    }

    virtual void execute(NuPlayer *player) {
        (player->*mFunc)();
    }

private:
    ActionFunc mFunc;

    DISALLOW_EVIL_CONSTRUCTORS(SimpleAction);
};

////////////////////////////////////////////////////////////////////////////////
#ifndef ANDROID_DEFAULT_CODE
static int64_t kRTSPEarlyEndTimeUs = 3000000ll; // 3secs
#endif // #ifndef ANDROID_DEFAULT_CODE

NuPlayer::NuPlayer()
    : mUIDValid(false),
      mSourceFlags(0),
      mVideoIsAVC(false),
      mAudioEOS(false),
      mVideoEOS(false),
      mScanSourcesPending(false),
      mScanSourcesGeneration(0),
      mPollDurationGeneration(0),
      mTimeDiscontinuityPending(false),
      mFlushingAudio(NONE),
      mFlushingVideo(NONE),
      mSkipRenderingAudioUntilMediaTimeUs(-1ll),
      mSkipRenderingVideoUntilMediaTimeUs(-1ll),
      mVideoLateByUs(0ll),
      mNumFramesTotal(0ll),
#ifndef ANDROID_DEFAULT_CODE
      mSeekTimeUs(-1),
      mPositionUs(-1),
      mPrepare(UNPREPARED),
      mPlayState(STOPPED),
      mVideoWidth(320),
      mVideoHeight(240),
      mRenderer(NULL),
      mVideoDecoder(NULL),
      mAudioDecoder(NULL),
      mDataSourceType(SOURCE_Default),
      mFlags(0),
#ifdef MTK_CLEARMOTION_SUPPORT
	  mEnClearMotion(1),
#endif
      mAudioAbsent(false),
      mVideoAbsent(false),
      mConsumingAudio(Consume_NONE),
      mConsumingVideo(Consume_NONE),
      mStopWhileConsume(false),
      mPauseWhileConsume(false),
#endif
      mNumFramesDropped(0ll),
      mVideoScalingMode(NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW),
      mStarted(false) {
}

NuPlayer::~NuPlayer() {
    ALOGD("~NuPlayer");
}

void NuPlayer::setUID(uid_t uid) {
    mUIDValid = true;
    mUID = uid;
}

void NuPlayer::setDriver(const wp<NuPlayerDriver> &driver) {
    mDriver = driver;
}

void NuPlayer::setDataSourceAsync(const sp<IStreamSource> &source) {
    sp<AMessage> msg = new AMessage(kWhatSetDataSource, id());

    sp<AMessage> notify = new AMessage(kWhatSourceNotify, id());

    char prop[PROPERTY_VALUE_MAX];
    if (property_get("media.stagefright.use-mp4source", prop, NULL)
            && (!strcmp(prop, "1") || !strcasecmp(prop, "true"))) {
        msg->setObject("source", new MP4Source(notify, source));
    } else {
        msg->setObject("source", new StreamingSource(notify, source));
    }

    msg->post();
}

static bool IsHTTPLiveURL(const char *url) {
    if (!strncasecmp("http://", url, 7)
            || !strncasecmp("https://", url, 8)
            || !strncasecmp("file://", url, 7)) {
        size_t len = strlen(url);
        if (len >= 5 && !strcasecmp(".m3u8", &url[len - 5])) {
            return true;
        }

        if (strstr(url,"m3u8")) {
            return true;
        }
    }

    return false;
}

void NuPlayer::setDataSourceAsync(
        const char *url, const KeyedVector<String8, String8> *headers) {
    sp<AMessage> msg = new AMessage(kWhatSetDataSource, id());
    size_t len = strlen(url);

    sp<AMessage> notify = new AMessage(kWhatSourceNotify, id());

    sp<Source> source;
    if (IsHTTPLiveURL(url)) {
        source = new HTTPLiveSource(notify, url, headers, mUIDValid, mUID);
#ifndef ANDROID_DEFAULT_CODE
        mDataSourceType = SOURCE_HttpLive;
#endif
    } else if (!strncasecmp(url, "rtsp://", 7)) {
        source = new RTSPSource(notify, url, headers, mUIDValid, mUID);
#ifndef ANDROID_DEFAULT_CODE
        mDataSourceType = SOURCE_Rtsp;
#endif
    } else if ((!strncasecmp(url, "http://", 7)
                || !strncasecmp(url, "https://", 8))
                    && ((len >= 4 && !strcasecmp(".sdp", &url[len - 4]))
                    || strstr(url, ".sdp?"))) {
        source = new RTSPSource(notify, url, headers, mUIDValid, mUID, true);
#ifndef ANDROID_DEFAULT_CODE
        mDataSourceType = SOURCE_Rtsp;
#endif
    } else {
        source = new GenericSource(notify, url, headers, mUIDValid, mUID);
    }

    msg->setObject("source", source);
    msg->post();
}

void NuPlayer::setDataSourceAsync(int fd, int64_t offset, int64_t length) {
    sp<AMessage> msg = new AMessage(kWhatSetDataSource, id());

    sp<AMessage> notify = new AMessage(kWhatSourceNotify, id());

    sp<Source> source = new GenericSource(notify, fd, offset, length);
#ifndef ANDROID_DEFAULT_CODE
	mDataSourceType = SOURCE_Local;	

	status_t err = source->initCheck();
	if(err != OK){
		notifyListener(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, err);
		ALOGW("setDataSource source init check fail err=%d",err);
		source = NULL;
		msg->setObject("source", source);
		msg->setInt32("result", err);
		msg->post();
		return;
	}
	
	sp<AMessage> format = source->getFormat(false);
	if(format.get()){
		AString newUrl;
		sp<RefBase> sdp;
		if(format->findString("rtsp-uri", &newUrl) &&
			format->findObject("rtsp-sdp", &sdp)) {
			//is sdp--need re-setDataSource
			source = new RTSPSource(notify, newUrl.c_str(), NULL, mUIDValid, mUID);
			((RTSPSource*)(source.get()))->setSDP(sdp);
			mDataSourceType = SOURCE_Rtsp;
		}
	}	
				
#endif
    msg->setObject("source", source);
    msg->post();
}

void NuPlayer::prepareAsync() {
    (new AMessage(kWhatPrepare, id()))->post();
}

void NuPlayer::setVideoSurfaceTextureAsync(
        const sp<IGraphicBufferProducer> &bufferProducer) {
    sp<AMessage> msg = new AMessage(kWhatSetVideoNativeWindow, id());

    if (bufferProducer == NULL) {
        msg->setObject("native-window", NULL);
    } else {
        msg->setObject(
                "native-window",
                new NativeWindowWrapper(
                    new Surface(bufferProducer)));
    }

    msg->post();
}

void NuPlayer::setAudioSink(const sp<MediaPlayerBase::AudioSink> &sink) {
    sp<AMessage> msg = new AMessage(kWhatSetAudioSink, id());
    msg->setObject("sink", sink);
    msg->post();
}

void NuPlayer::start() {
    (new AMessage(kWhatStart, id()))->post();
}

void NuPlayer::pause() {
    (new AMessage(kWhatPause, id()))->post();
}

void NuPlayer::resume() {
    (new AMessage(kWhatResume, id()))->post();
}

void NuPlayer::resetAsync() {
    (new AMessage(kWhatReset, id()))->post();
}

#ifndef ANDROID_DEFAULT_CODE
void NuPlayer::stop() {
    (new AMessage(kWhatStop, id()))->post();
}
#ifdef MTK_CLEARMOTION_SUPPORT
void NuPlayer::enableClearMotion(int32_t enable) {
	mEnClearMotion = enable;
}
#endif

#endif

void NuPlayer::seekToAsync(int64_t seekTimeUs) {
#ifndef ANDROID_DEFAULT_CODE
    CHECK(seekTimeUs != -1);
    Mutex::Autolock autoLock(mLock); 
    // To trigger spinner display
    mSeekTimeUs = seekTimeUs;   //seek complete later
    mPositionUs = seekTimeUs;   // mtk80902: for kRTSPEarlyEndTimeUs vs seek to end 
    if (mRenderer != NULL) {
        if (mPlayState == PLAYING) {
            mRenderer->pause();
        }
    }
#endif
    sp<AMessage> msg = new AMessage(kWhatSeek, id());
    msg->setInt64("seekTimeUs", seekTimeUs);
    msg->post();
}

// static
bool NuPlayer::IsFlushingState(FlushStatus state, bool *needShutdown) {
    switch (state) {
        case FLUSHING_DECODER:
            if (needShutdown != NULL) {
                *needShutdown = false;
            }
            return true;

        case FLUSHING_DECODER_SHUTDOWN:
#ifndef ANDROID_DEFAULT_CODE
        case SHUTTING_DOWN_DECODER:
#endif
            if (needShutdown != NULL) {
                *needShutdown = true;
            }
            return true;

        default:
            return false;
    }
}

void NuPlayer::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatSetDataSource:
        {
            ALOGD("kWhatSetDataSource");

#ifndef ANDROID_DEFAULT_CODE
			if(mSource == NULL) {
				ALOGE("kWhatSetDataSource, mSource == NULL");
				int32_t result;
				if(msg->findInt32("result", &result)) {
					ALOGW("kWhatSetDataSource, notify driver result");
					sp<NuPlayerDriver> driver = mDriver.promote();
					driver->notifySetDataSourceCompleted(result);
					break;
				}
			}
#endif

            CHECK(mSource == NULL);

            sp<RefBase> obj;
            CHECK(msg->findObject("source", &obj));

            mSource = static_cast<Source *>(obj.get());

            looper()->registerHandler(mSource);

            CHECK(mDriver != NULL);
            sp<NuPlayerDriver> driver = mDriver.promote();
            if (driver != NULL) {
                driver->notifySetDataSourceCompleted(OK);
            }
            break;
        }

        case kWhatPrepare:
        {
#ifndef ANDROID_DEFAULT_CODE
            ALOGD("kWhatPrepare, source type = %d", (int)mDataSourceType);
            if (mPrepare == PREPARING)
                break;
            mPrepare = PREPARING;

            if (mSource == NULL) {
                ALOGW("prepare error: source is not ready");
                finishPrepare(UNKNOWN_ERROR);
                break;
            }
#endif
            mSource->prepareAsync();
            break;
        }

        case kWhatGetTrackInfo:
        {
            uint32_t replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            status_t err = INVALID_OPERATION;
            if (mSource != NULL) {
                Parcel* reply;
                CHECK(msg->findPointer("reply", (void**)&reply));
                err = mSource->getTrackInfo(reply);
            }

            sp<AMessage> response = new AMessage;
            response->setInt32("err", err);

            response->postReply(replyID);
            break;
        }

        case kWhatSelectTrack:
        {
            uint32_t replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            status_t err = INVALID_OPERATION;
            if (mSource != NULL) {
                size_t trackIndex;
                int32_t select;
                CHECK(msg->findSize("trackIndex", &trackIndex));
                CHECK(msg->findInt32("select", &select));
                err = mSource->selectTrack(trackIndex, select);
            }

            sp<AMessage> response = new AMessage;
            response->setInt32("err", err);

            response->postReply(replyID);
            break;
        }

        case kWhatPollDuration:
        {
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));

            if (generation != mPollDurationGeneration) {
                // stale
                break;
            }

            int64_t durationUs;
            if (mDriver != NULL && mSource->getDuration(&durationUs) == OK) {
                sp<NuPlayerDriver> driver = mDriver.promote();
                if (driver != NULL) {
                    driver->notifyDuration(durationUs);
                }
            }

            msg->post(1000000ll);  // poll again in a second.
            break;
        }

        case kWhatSetVideoNativeWindow:
        {
            ALOGD("kWhatSetVideoNativeWindow");

            mDeferredActions.push_back(
                    new ShutdownDecoderAction(
                        false /* audio */, true /* video */));

            sp<RefBase> obj;
            CHECK(msg->findObject("native-window", &obj));

            mDeferredActions.push_back(
                    new SetSurfaceAction(
                        static_cast<NativeWindowWrapper *>(obj.get())));

            if (obj != NULL) {
                // If there is a new surface texture, instantiate decoders
                // again if possible.
                mDeferredActions.push_back(
                        new SimpleAction(&NuPlayer::performScanSources));
            }

            processDeferredActions();
            break;
        }

        case kWhatSetAudioSink:
        {
            ALOGD("kWhatSetAudioSink");

            sp<RefBase> obj;
            CHECK(msg->findObject("sink", &obj));

            mAudioSink = static_cast<MediaPlayerBase::AudioSink *>(obj.get());
            ALOGD("\t\taudio sink: %p", mAudioSink.get());
            break;
        }

        case kWhatStart:
        {
            ALOGD("kWhatStart");

            mVideoIsAVC = false;
            mAudioEOS = false;
            mVideoEOS = false;
            mSkipRenderingAudioUntilMediaTimeUs = -1;
            mSkipRenderingVideoUntilMediaTimeUs = -1;
            mVideoLateByUs = 0;
            mNumFramesTotal = 0;
            mNumFramesDropped = 0;
            mStarted = true;

#ifndef ANDROID_DEFAULT_CODE
            if (mPlayState == PLAYING) {
                break;
            }

            if (mSource != NULL) {
                mSource->start();
            }
#else
            mSource->start();
#endif

            uint32_t flags = 0;

            if (mSource->isRealTime()) {
                flags |= Renderer::FLAG_REAL_TIME;
            }

            mRenderer = new Renderer(
                    mAudioSink,
                    new AMessage(kWhatRendererNotify, id()),
                    flags);

            looper()->registerHandler(mRenderer);
            postScanSources();
#ifndef ANDROID_DEFAULT_CODE
            mPlayState = PLAYING;
#endif
            break;
        }

        case kWhatScanSources:
        {
            ALOGD("kWhatScanSources");
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));
            if (generation != mScanSourcesGeneration) {
                // Drop obsolete msg.
                break;
            }

            mScanSourcesPending = false;
#ifndef ANDROID_DEFAULT_CODE
            bool needScanAgain = onScanSources();
            //TODO: to handle audio only file, finisPrepare should be sent
            if (needScanAgain) {     //scanning source is not completed, continue
                msg->post(100000ll);
                mScanSourcesPending = true;
            } else {
            	  mAudioAbsent = (mAudioDecoder == NULL);
                  mVideoAbsent = (mVideoDecoder == NULL);
                ALOGD("scanning sources done ! haveAudio=%d, haveVideo=%d",
                        mAudioDecoder != NULL, mVideoDecoder != NULL);
                    if (mVideoAbsent) {
                        notifyListener(MEDIA_SET_VIDEO_SIZE, 0,0);
                       }
					
				if (mVideoAbsent && mAudioAbsent) {
					ALOGD("notify error to AP when there is no audio and video!");
					notifyListener(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, 0);
					break;
				}
            }
#else
            ALOGV("scanning sources haveAudio=%d, haveVideo=%d",
                 mAudioDecoder != NULL, mVideoDecoder != NULL);

            bool mHadAnySourcesBefore =
                (mAudioDecoder != NULL) || (mVideoDecoder != NULL);

            if (mNativeWindow != NULL) {
                instantiateDecoder(false, &mVideoDecoder);
            }

            if (mAudioSink != NULL) {
                instantiateDecoder(true, &mAudioDecoder);
            }

            if (!mHadAnySourcesBefore
                    && (mAudioDecoder != NULL || mVideoDecoder != NULL)) {
                // This is the first time we've found anything playable.

                if (mSourceFlags & Source::FLAG_DYNAMIC_DURATION) {
                    schedulePollDuration();
                }
            }

            status_t err;
            if ((err = mSource->feedMoreTSData()) != OK) {
                if (mAudioDecoder == NULL && mVideoDecoder == NULL) {
                    // We're not currently decoding anything (no audio or
                    // video tracks found) and we just ran out of input data.

                    if (err == ERROR_END_OF_STREAM) {
                        notifyListener(MEDIA_PLAYBACK_COMPLETE, 0, 0);
                    } else {
                        notifyListener(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, err);
                    }
                }
                break;
            }

            if ((mAudioDecoder == NULL && mAudioSink != NULL)
                    || (mVideoDecoder == NULL && mNativeWindow != NULL)) {
                msg->post(100000ll);
                mScanSourcesPending = true;
            }
#endif
            break;
        }

        case kWhatVideoNotify:
        case kWhatAudioNotify:
        {
            bool audio = msg->what() == kWhatAudioNotify;

            sp<AMessage> codecRequest;
            CHECK(msg->findMessage("codec-request", &codecRequest));

            int32_t what;
            CHECK(codecRequest->findInt32("what", &what));

            if (what == ACodec::kWhatFillThisBuffer) {
                status_t err = feedDecoderInputData(
                        audio, codecRequest);

                if (err == -EWOULDBLOCK) {
                    if (mSource->feedMoreTSData() == OK) {
                        msg->post(10000ll);
                    }
                }
            } else if (what == ACodec::kWhatEOS) {
                int32_t err;
                CHECK(codecRequest->findInt32("err", &err));

                if (err == ERROR_END_OF_STREAM) {
                    ALOGD("got %s decoder EOS", audio ? "audio" : "video");
                } 
#ifndef ANDROID_DEFAULT_CODE
                else if (err == INFO_DISCONTINUITY || err == FAKE_INFO_DISCONTINUITY) {
                    ALOGD("got %s decoder discontinuity, (consume status: %d, %d)",
                            audio ? "audio" : "video", mConsumingAudio, mConsumingVideo);
                    bool bQueueEOS = false;
                    if (audio) {
                        if (mConsumingAudio == Consume_AWAITING_DECODER_EOS)
                        	{
                            mConsumingAudio = Consume_AWAITING_RENDER_EOS;
                            bQueueEOS = true;
                          }
                    } else {
                        if (mConsumingVideo == Consume_AWAITING_DECODER_EOS)
                        	{
                            mConsumingVideo = Consume_AWAITING_RENDER_EOS;
                            bQueueEOS = true;
                          }
                    }
                    if(bQueueEOS)
                    	mRenderer->queueEOS(audio, err);
                    break;
                }
#endif                
                else {
                    ALOGD("got %s decoder EOS w/ error %d",
                         audio ? "audio" : "video",
                         err);
                }

                mRenderer->queueEOS(audio, err);
            } else if (what == ACodec::kWhatFlushCompleted) {
                bool needShutdown;

                if (audio) {
                    CHECK(IsFlushingState(mFlushingAudio, &needShutdown));
                    mFlushingAudio = FLUSHED;
                } else {
                    CHECK(IsFlushingState(mFlushingVideo, &needShutdown));
                    mFlushingVideo = FLUSHED;

                    mVideoLateByUs = 0;
                }

                ALOGD("decoder %s flush completed", audio ? "audio" : "video");

                if (needShutdown) {
                    ALOGD("initiating %s decoder shutdown",
                         audio ? "audio" : "video");

                    (audio ? mAudioDecoder : mVideoDecoder)->initiateShutdown();

                    if (audio) {
                        mFlushingAudio = SHUTTING_DOWN_DECODER;
                    } else {
                        mFlushingVideo = SHUTTING_DOWN_DECODER;
                    }
                }

                finishFlushIfPossible();
            } else if (what == ACodec::kWhatOutputFormatChanged) {
                if (audio) {
                    int32_t numChannels;
                    CHECK(codecRequest->findInt32(
                                "channel-count", &numChannels));

                    int32_t sampleRate;
                    CHECK(codecRequest->findInt32("sample-rate", &sampleRate));

                    ALOGI("Audio output format changed to %d Hz, %d channels",
                         sampleRate, numChannels);

                    mAudioSink->close();

                    audio_output_flags_t flags;
                    int64_t durationUs;
                    // FIXME: we should handle the case where the video decoder
                    // is created after we receive the format change indication.
                    // Current code will just make that we select deep buffer
                    // with video which should not be a problem as it should
                    // not prevent from keeping A/V sync.
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DOLBY_DAP_SUPPORT
                    // DS Effect is attached only to the Non-Deep Buffered Output
                    // And we want all audio to flow through DS Effect.
                    // As such, we force both Music and Movie Playbacks to take the Non-Deep Buffered Output
                    flags = AUDIO_OUTPUT_FLAG_NONE;
#else   // DOLBY_END
                    if (mVideoDecoder == NULL &&
                            mSource->getDuration(&durationUs) == OK &&
                            durationUs
                                > AUDIO_SINK_MIN_DEEP_BUFFER_DURATION_US) {
                        flags = AUDIO_OUTPUT_FLAG_DEEP_BUFFER;
                    } else {
                        flags = AUDIO_OUTPUT_FLAG_NONE;
                    }
#endif
#else //ANDROID_DEFAULT_CODE
                    if (mVideoDecoder == NULL &&
                            mSource->getDuration(&durationUs) == OK &&
                            durationUs
                                > AUDIO_SINK_MIN_DEEP_BUFFER_DURATION_US) {
                        flags = AUDIO_OUTPUT_FLAG_DEEP_BUFFER;
                    } else {
                        flags = AUDIO_OUTPUT_FLAG_NONE;
                    }
#endif //ANDROID_DEFAULT_CODE
                    int32_t channelMask;
                    if (!codecRequest->findInt32("channel-mask", &channelMask)) {
                        channelMask = CHANNEL_MASK_USE_CHANNEL_ORDER;
                    }

                    CHECK_EQ(mAudioSink->open(
                                sampleRate,
                                numChannels,
                                (audio_channel_mask_t)channelMask,
                                AUDIO_FORMAT_PCM_16_BIT,
                                8 /* bufferCount */,
                                NULL,
                                NULL,
                                flags),
                             (status_t)OK);
                    mAudioSink->start();

                    mRenderer->signalAudioSinkChanged();
                } else {
                    // video

                    int32_t width, height;
                    CHECK(codecRequest->findInt32("width", &width));
                    CHECK(codecRequest->findInt32("height", &height));
#ifndef ANDROID_DEFAULT_CODE
                    int32_t WRatio, HRatio;
                    if (!codecRequest->findInt32("width-ratio", &WRatio)) {
                        WRatio = 1;
                    }
                    if (!codecRequest->findInt32("height-ratio", &HRatio)) {
                        HRatio = 1;
                    }
                    width /= WRatio;
                    height /= HRatio;
#endif

                    int32_t cropLeft, cropTop, cropRight, cropBottom;
                    CHECK(codecRequest->findRect(
                                "crop",
                                &cropLeft, &cropTop, &cropRight, &cropBottom));

                    int32_t displayWidth = cropRight - cropLeft + 1;
                    int32_t displayHeight = cropBottom - cropTop + 1;

                    ALOGI("Video output format changed to %d x %d "
                         "(crop: %d x %d @ (%d, %d))",
                         width, height,
                         displayWidth,
                         displayHeight,
                         cropLeft, cropTop);

                    sp<AMessage> videoInputFormat =
                        mSource->getFormat(false /* audio */);
#ifndef ANDROID_DEFAULT_CODE
                    if(videoInputFormat == NULL)
                        break;
#endif                    
                    // Take into account sample aspect ratio if necessary:
                    int32_t sarWidth, sarHeight;
                    if (videoInputFormat->findInt32("sar-width", &sarWidth)
                            && videoInputFormat->findInt32(
                                "sar-height", &sarHeight)) {
                        ALOGV("Sample aspect ratio %d : %d",
                              sarWidth, sarHeight);

                        displayWidth = (displayWidth * sarWidth) / sarHeight;

                        ALOGV("display dimensions %d x %d",
                              displayWidth, displayHeight);
                    }

                    notifyListener(
                            MEDIA_SET_VIDEO_SIZE, displayWidth, displayHeight);
                }
            } else if (what == ACodec::kWhatShutdownCompleted) {
                ALOGD("%s shutdown completed", audio ? "audio" : "video");
#ifndef ANDROID_DEFAULT_CODE
                bool bConsuming = false;
                if (audio && mConsumingAudio == Consume_AWAITING_DECODER_SHUTDOWN) {
                    bConsuming = true;
                    mConsumingAudio = Consume_DONE;
                    mAudioDecoder.clear();
                }
                if (!audio && mConsumingVideo == Consume_AWAITING_DECODER_SHUTDOWN) {
                    bConsuming = true;
                    mConsumingVideo = Consume_DONE;
                    mVideoDecoder.clear();
                }
                if (bConsuming) {
                    finishConsumeIfPossible();
                    break;
                }
#endif	
                if (audio) {
                    mAudioDecoder.clear();

                    CHECK_EQ((int)mFlushingAudio, (int)SHUTTING_DOWN_DECODER);
                    mFlushingAudio = SHUT_DOWN;
                } else {
                    mVideoDecoder.clear();

                    CHECK_EQ((int)mFlushingVideo, (int)SHUTTING_DOWN_DECODER);
                    mFlushingVideo = SHUT_DOWN;
                }

                finishFlushIfPossible();
            } else if (what == ACodec::kWhatError) {
#ifndef ANDROID_DEFAULT_CODE
                if (!(IsFlushingState(audio ? mFlushingAudio : mFlushingVideo)) && !(IsConsumingState(audio ? mConsumingAudio : mConsumingVideo))) {
                    ALOGE("Received error from %s decoder.",
                            audio ? "audio" : "video");

					if (mDataSourceType == SOURCE_HttpLive) {
                        ALOGD("%s decoder signalFillBufferToNul",audio ? "audio" : "video");
                        (audio ? mAudioDecoder : mVideoDecoder)->signalFillBufferToNul(); 
					} else {

						int32_t err;
						CHECK(codecRequest->findInt32("err", &err));
						// mtk80902: ALPS00490726 - in fact, prepare complete should
						// after acodec return onConfigureComponent done. if acodec 
						// notify error before it, we should judge wether a/v both
						// got errors.
						if (mRenderer != NULL)
						    mRenderer->queueEOS(audio, err);
						else {
						    (audio ? mAudioDecoder : mVideoDecoder)->initiateShutdown();

						    if (audio) {
						        mFlushingAudio = SHUTTING_DOWN_DECODER;
						        notifyListener(MEDIA_INFO, MEDIA_INFO_HAS_UNSUPPORT_AUDIO, 0);
							} else {
						        mFlushingVideo = SHUTTING_DOWN_DECODER;
						        notifyListener(MEDIA_INFO, MEDIA_INFO_HAS_UNSUPPORT_VIDEO, 0);
							}
                            // ALPS00775449
                            if (mDataSourceType == SOURCE_Rtsp && mSource != NULL) {
                                (static_cast<RTSPSource *>(mSource.get()))->stopTrack(audio);
                            }
						}
					
					}
                } else {
                    ALOGD("Ignore error from %s decoder when flushing", audio ? "audio" : "video");
                }
#else
                ALOGE("Received error from %s decoder, aborting playback.",
                     audio ? "audio" : "video");

                mRenderer->queueEOS(audio, UNKNOWN_ERROR);
#endif
            } else if (what == ACodec::kWhatDrainThisBuffer) {
                renderBuffer(audio, codecRequest);
#ifndef ANDROID_DEFAULT_CODE
            } else if (what == ACodec::kWhatComponentAllocated) {
                int32_t quirks;
                CHECK(codecRequest->findInt32("quirks", &quirks));
                // mtk80902: must tell APacketSource quirks settings 
                // thru this way..
                ALOGD("Component Alloc: quirks (%u)", quirks);
                sp<MetaData> params = new MetaData;
                if (quirks & OMXCodec::kWantsNALFragments) {
                    params->setInt32(kKeyWantsNALFragments, true);
                    if (mSource != NULL)
                        mSource->setParams(params);
                }
#endif
            } else if (what != ACodec::kWhatComponentAllocated
                    && what != ACodec::kWhatComponentConfigured
                    && what != ACodec::kWhatBuffersAllocated) {
                ALOGV("Unhandled codec notification %d '%c%c%c%c'.",
                      what,
                      what >> 24,
                      (what >> 16) & 0xff,
                      (what >> 8) & 0xff,
                      what & 0xff);
            }

            break;
        }

        case kWhatRendererNotify:
        {
            int32_t what;
            CHECK(msg->findInt32("what", &what));

            if (what == Renderer::kWhatEOS) {
                int32_t audio;
                CHECK(msg->findInt32("audio", &audio));

                int32_t finalResult;
                CHECK(msg->findInt32("finalResult", &finalResult));
#ifndef ANDROID_DEFAULT_CODE
                if (mDataSourceType != SOURCE_Rtsp) {
                if (((finalResult == INFO_DISCONTINUITY) || (finalResult == FAKE_INFO_DISCONTINUITY)) && 
                	((audio && IsConsumingState(mConsumingAudio)) || (!audio && IsConsumingState(mConsumingVideo)))) {                	               	
                    if (audio) {
                        if (mAudioDecoder != NULL && mConsumingAudio == Consume_AWAITING_RENDER_EOS) {
                            mConsumingAudio = Consume_AWAITING_DECODER_SHUTDOWN;
                            mAudioDecoder->initiateShutdown();
                        } else if (mAudioDecoder == NULL){
                            mConsumingAudio = Consume_DONE;
                        }
                    } else {
                        if (mVideoDecoder != NULL && mConsumingVideo == Consume_AWAITING_RENDER_EOS) {
                            mConsumingVideo = Consume_AWAITING_DECODER_SHUTDOWN;
                            mVideoDecoder->initiateShutdown();
                        } else if (mVideoDecoder == NULL){
                            mConsumingVideo = Consume_DONE;
                        }
                    }
                    ALOGD("finish consume %s render", audio ? "audio" : "video");
                    finishConsumeIfPossible();
                    break;
                }
                else if (finalResult == FAKE_INFO_DISCONTINUITY)
                	break;
                }
#endif
                if (audio) {
                    mAudioEOS = true;
                } else {
                    mVideoEOS = true;
                }

                if (finalResult == ERROR_END_OF_STREAM) {
                    ALOGD("reached %s EOS", audio ? "audio" : "video");
                } else {
                    ALOGE("%s track encountered an error (%d)",
                            audio ? "audio" : "video", finalResult);
#ifndef ANDROID_DEFAULT_CODE
                    // mtk80902: ALPS00436989
                    if (audio) {
                        notifyListener(MEDIA_INFO, MEDIA_INFO_HAS_UNSUPPORT_AUDIO, finalResult);
                    } else {
                        notifyListener(MEDIA_INFO, MEDIA_INFO_HAS_UNSUPPORT_VIDEO, finalResult);
                    }
                    // ALPS00775449
                    if (mDataSourceType == SOURCE_Rtsp && mSource != NULL) {
                        (static_cast<RTSPSource *>(mSource.get()))->stopTrack(audio);
                    }
#else
                    notifyListener(
                            MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, finalResult);
#endif
                }

                if ((mAudioEOS || mAudioDecoder == NULL)
                 && (mVideoEOS || mVideoDecoder == NULL)) {
#ifndef ANDROID_DEFAULT_CODE
                    // mtk80902: for ALPS00557536 - dont trigger AP's retry if it's codec's error?
                    if (finalResult == ERROR_END_OF_STREAM || finalResult == ERROR_CANNOT_CONNECT) {
                        // mtk80902: ALPS00434239 - porting from AwesomePlayer
                        // diff our AP & rtsp type by RTSP CACHE SIZE header
                        int64_t durationUs;
                        if (mDataSourceType == SOURCE_Rtsp && 
                            mSource != NULL && 
                            mSource->getDuration(&durationUs) == OK && 
                            (durationUs == 0 || durationUs - mPositionUs > kRTSPEarlyEndTimeUs)) {
                            ALOGE("RTSP play end before duration %lld, current pos %lld", durationUs, mPositionUs);
                            notifyListener(MEDIA_ERROR, MEDIA_ERROR_CANNOT_CONNECT_TO_SERVER, 0);
                            break;
                        }
                    }
#endif
                    notifyListener(MEDIA_PLAYBACK_COMPLETE, 0, 0);
                }
            } else if (what == Renderer::kWhatPosition) {
                int64_t positionUs;
                CHECK(msg->findInt64("positionUs", &positionUs));
#ifndef ANDROID_DEFAULT_CODE
                // mtk80902: add earlyEndTime for rtsp
                // dont update when seeking
                if (mSeekTimeUs == -1)
                    mPositionUs = positionUs;
#endif

                CHECK(msg->findInt64("videoLateByUs", &mVideoLateByUs));

                if (mDriver != NULL) {
                    sp<NuPlayerDriver> driver = mDriver.promote();
                    if (driver != NULL) {
                        driver->notifyPosition(positionUs);

                        driver->notifyFrameStats(
                                mNumFramesTotal, mNumFramesDropped);
                    }
                }
            } else if (what == Renderer::kWhatFlushComplete) {
                int32_t audio;
                CHECK(msg->findInt32("audio", &audio));

                ALOGD("renderer %s flush completed.", audio ? "audio" : "video");
            } else if (what == Renderer::kWhatVideoRenderingStart) {
                notifyListener(MEDIA_INFO, MEDIA_INFO_RENDERING_START, 0);
            } else if (what == Renderer::kWhatMediaRenderingStart) {
                ALOGV("media rendering started");
                notifyListener(MEDIA_STARTED, 0, 0);
            }
            break;
        }
#ifndef ANDROID_DEFAULT_CODE
        case kWhatStop:
        {
            // mtk80902: substitute of calling pause in NuPlayerDriver's stop
            // most for the rtsp
            ALOGD("kWhatStop, %d", (int32_t)mPlayState);
            if (mPlayState == PAUSING || mPlayState == PAUSED)
                return;
            if ((mDataSourceType == SOURCE_HttpLive) && (IsConsumingState(mConsumingAudio) || IsConsumingState(mConsumingVideo)))
            {
                mStopWhileConsume = true;
                break;
            }
            mPlayState = PAUSED;
            CHECK(mRenderer != NULL);
            mRenderer->pause();
            break;
        }
#endif

        case kWhatMoreDataQueued:
        {
            break;
        }

        case kWhatReset:
        {
            ALOGD("kWhatReset");

            mDeferredActions.push_back(
                    new ShutdownDecoderAction(
                        true /* audio */, true /* video */));

            mDeferredActions.push_back(
                    new SimpleAction(&NuPlayer::performReset));

            processDeferredActions();
            break;
        }

        case kWhatSeek:
        {
            int64_t seekTimeUs;
            CHECK(msg->findInt64("seekTimeUs", &seekTimeUs));

            ALOGD("kWhatSeek seekTimeUs=%lld us", seekTimeUs);

#ifndef ANDROID_DEFAULT_CODE
            //no need to flush decoder because it's already done in performSeek
#else
            mDeferredActions.push_back(
                    new SimpleAction(&NuPlayer::performDecoderFlush));
#endif

            mDeferredActions.push_back(new SeekAction(seekTimeUs));

            processDeferredActions();
            break;
        }

        case kWhatPause:
        {
#ifndef ANDROID_DEFAULT_CODE
            ALOGD("kWhatPause, %d", (int32_t)mPlayState);
            if (mPlayState == STOPPED || mPlayState == PAUSED || mPlayState == PLAYSENDING) {
                notifyListener(MEDIA_PAUSE_COMPLETE, INVALID_OPERATION, 0);
                break;
            }
            if (mPlayState == PAUSING) {
                notifyListener(MEDIA_PAUSE_COMPLETE, ALREADY_EXISTS, 0);
                break;
            }
            
            mPlayState = PAUSING;
            if ((mDataSourceType == SOURCE_HttpLive) && (IsConsumingState(mConsumingAudio) || IsConsumingState(mConsumingVideo)))
            {
                mPauseWhileConsume = true;
                break;
            }
           // if (mSource->pause() == OK)
           //     mPlayState = PAUSED;
#endif
            CHECK(mRenderer != NULL);
            mSource->pause();
            mRenderer->pause();
            break;
        }

        case kWhatResume:
        {
#ifndef ANDROID_DEFAULT_CODE
            ALOGD("kWhatResume, %d", (int32_t)mPlayState);
           
            if ((mDataSourceType == SOURCE_HttpLive) && mPauseWhileConsume)
            {
                mPauseWhileConsume = false;
                mPlayState = PLAYING;
                notifyListener(MEDIA_PAUSE_COMPLETE, 0, 0);
                notifyListener(MEDIA_PLAY_COMPLETE, 0, 0);
                break;
            }

            if (mPlayState == PLAYING || mPlayState == PAUSING) {
                notifyListener(MEDIA_PLAY_COMPLETE, INVALID_OPERATION, 0);
                break;
            }
            if (mPlayState == PLAYSENDING) {
                notifyListener(MEDIA_PLAY_COMPLETE, ALREADY_EXISTS, 0);
                break;
            }
            // mtk80902: ALPS00451531 - bad server. response error
            // if received PLAY without range when playing
            // if play before seek complete then dont send PLAY cmd
            // just set PLAYING state
            if (mSeekTimeUs != -1) {
                notifyListener(MEDIA_PLAY_COMPLETE, OK, 0);
                mPlayState = PLAYING;
                break;
            }
            mPlayState = PLAYSENDING;
            //if (mSource->play() == OK)
            //    mPlayState = PLAYING;
#endif
            CHECK(mRenderer != NULL);
            mSource->resume();
            mRenderer->resume();
            break;
        }

        case kWhatSourceNotify:
        {
            onSourceNotify(msg);
            break;
        }

        default:
            TRESPASS();
            break;
    }
}

#ifndef ANDROID_DEFAULT_CODE
bool NuPlayer::onScanSources() {
    bool needScanAgain = false;
    bool mHadAnySourcesBefore =
        (mAudioDecoder != NULL) || (mVideoDecoder != NULL);

    // mtk80902: ALPS01413054
    // get format first, then init decoder
    if (mDataSourceType == SOURCE_HttpLive) {
        needScanAgain = (mSource->allTracksPresent() != OK);
    }    
    // mtk80902: for rtsp, if instantiateDecoder return EWOULDBLK
    // it means no track. no need to try again.
    status_t videoFmt = OK, audioFmt = OK;
    if (mNativeWindow != NULL) {
#ifdef MTK_CLEARMOTION_SUPPORT
        if (mEnClearMotion) {
		    sp<ANativeWindow> window = mNativeWindow->getNativeWindow();    
		    if (window != NULL) {
		        window->setSwapInterval(window.get(), 1);        
		    }
        }       
#endif
        videoFmt = instantiateDecoder(false, &mVideoDecoder);
    }

    if (mAudioSink != NULL) {
        audioFmt = instantiateDecoder(true, &mAudioDecoder);
    }

    if (!mHadAnySourcesBefore
            && (mAudioDecoder != NULL || mVideoDecoder != NULL)) {
        // This is the first time we've found anything playable.

        if (mSourceFlags & Source::FLAG_DYNAMIC_DURATION) {
            schedulePollDuration();
        }
    }

    if (mDataSourceType == SOURCE_Rtsp) {
        ALOGD("audio sink: %p, audio decoder: %p, native window: %p, video decoder: %p",
        mAudioSink.get(), mAudioDecoder.get(), mNativeWindow.get(), mVideoDecoder.get());
        needScanAgain = ((mAudioSink != NULL) && (audioFmt == OK) && (mAudioDecoder == NULL))
                     || ((mNativeWindow != NULL) && (videoFmt == OK) && (mVideoDecoder == NULL));
    } else if (mDataSourceType == SOURCE_HttpLive) {
        ALOGD("audio sink: %p, audio decoder: %p, native window: %p, video decoder: %p",
        mAudioSink.get(), mAudioDecoder.get(), mNativeWindow.get(), mVideoDecoder.get());
    } else {
         needScanAgain = ((mAudioSink != NULL) && (mAudioDecoder == NULL))
                      || ((mNativeWindow != NULL) && (mVideoDecoder == NULL));
    }
    
    status_t err;
    if ((err = mSource->feedMoreTSData()) != OK) {
        if (mAudioDecoder == NULL && mVideoDecoder == NULL) {
            // We're not currently decoding anything (no audio or
            // video tracks found) and we just ran out of input data.

            if (err == ERROR_END_OF_STREAM) {
                notifyListener(MEDIA_PLAYBACK_COMPLETE, 0, 0);
            } else {
                notifyListener(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, err);
            }
        }
        return false;
    } 

    return needScanAgain;
}

void NuPlayer::finishPrepare(int err /*= OK*/) {
    mPrepare = (err == OK)?PREPARED:UNPREPARED;
    if (mDriver == NULL)
        return;
    sp<NuPlayerDriver> driver = mDriver.promote();
    if (driver != NULL) {
        
        driver->notifyPrepareCompleted(err);

        int64_t durationUs;
        if (mSource != NULL && mSource->getDuration(&durationUs) == OK) {
            driver->notifyDuration(durationUs);
        }

        //if (mDataSourceType == SOURCE_Rtsp && err == OK) {
        //    notifyListener(MEDIA_INFO, MEDIA_INFO_CHECK_LIVE_STREAMING_COMPLETE, 0);
        //}
        ALOGD("complete prepare %s", (err == OK)?"success":"fail");
    }
}

void NuPlayer::finishConsumeIfPossible() {

    ALOGD("finishConsumeIfPossible, %d, %d (%d, %d)",
            (int)mAudioAbsent, (int)mVideoAbsent,
            (mAudioDecoder == NULL), (mVideoDecoder == NULL));

    if (!mAudioAbsent && mConsumingAudio != Consume_DONE) {
        return;
    }
    if (!mVideoAbsent && mConsumingVideo != Consume_DONE) {
        return;
    }

    if (mTimeDiscontinuityPending) {
        if (mRenderer != NULL) {
            mRenderer->signalTimeDiscontinuity();
        }
        mTimeDiscontinuityPending = false;
    }

    mConsumingVideo = Consume_NONE;
    mConsumingAudio = Consume_NONE;

    ALOGD("both audio and video are consumed now. (%d, %d)", mAudioAbsent, mVideoAbsent);

    if (mPauseWhileConsume || mStopWhileConsume) {
      ALOGD("Resume pause/stop after consumedecoder");
      CHECK(mRenderer != NULL);
      mRenderer->pause();

      if (mPauseWhileConsume) {
         mSource->pause();
         mPauseWhileConsume = false;
      }
      if (mStopWhileConsume) {
         mStopWhileConsume = false;
      }
    }

    processDeferredActions();
}

#endif

void NuPlayer::finishFlushIfPossible() {
#ifndef ANDROID_DEFAULT_CODE
    // mtk80902: ALPS00445127 - only one track's situation
    if (mAudioDecoder != NULL && mFlushingAudio != FLUSHED && mFlushingAudio != SHUT_DOWN) {
        ALOGD("not flushed, mFlushingAudio = %d", mFlushingAudio);
        return;
    }

    if (mVideoDecoder != NULL && mFlushingVideo != FLUSHED && mFlushingVideo != SHUT_DOWN) {
        ALOGD("not flushed, mFlushingVideo = %d", mFlushingVideo);
        return;
    }
#else
    if (mFlushingAudio != FLUSHED && mFlushingAudio != SHUT_DOWN) {
        ALOGD("not flushed, mFlushingAudio = %d", mFlushingAudio);
        return;
    }

    if (mFlushingVideo != FLUSHED && mFlushingVideo != SHUT_DOWN) {
        ALOGD("not flushed, mFlushingVideo = %d", mFlushingVideo);
        return;
    }
#endif

    ALOGD("both audio and video are flushed now.");

    if (mTimeDiscontinuityPending) {
    	if (mRenderer != NULL) {
	        mRenderer->signalTimeDiscontinuity();
	    } 
        mTimeDiscontinuityPending = false;
    } 

#ifndef ANDROID_DEFAULT_CODE
    {
        Mutex::Autolock autoLock(mLock);
        if (isSeeking_l()) {
        finishSeek();
      }
    }
#endif

    if (mAudioDecoder != NULL) {
        mAudioDecoder->signalResume();
    }

    if (mVideoDecoder != NULL) {
        mVideoDecoder->signalResume();
    }

    mFlushingAudio = NONE;
    mFlushingVideo = NONE;

    processDeferredActions();
}

void NuPlayer::postScanSources() {
    if (mScanSourcesPending) {
        return;
    }

    sp<AMessage> msg = new AMessage(kWhatScanSources, id());
    msg->setInt32("generation", mScanSourcesGeneration);
    msg->post();

    mScanSourcesPending = true;
}

status_t NuPlayer::instantiateDecoder(bool audio, sp<Decoder> *decoder) {
    if (*decoder != NULL) {
        ALOGD("%s decoder not NULL!", audio?"audio":"video");
        return OK;
    }

    sp<AMessage> format = mSource->getFormat(audio);

    if (format == NULL) {
        ALOGD("%s format is NULL!", audio?"audio":"video");
        return -EWOULDBLOCK;
    }

#ifndef ANDROID_DEFAULT_CODE
    if(!audio)
    {
#ifdef MTK_CLEARMOTION_SUPPORT
		format->setInt32("use-clearmotion-mode", mEnClearMotion);
        ALOGD("mEnClearMotion(%d).", mEnClearMotion);
#endif

        ALOGD("instantiate Video decoder.");
    }
    else
        ALOGD("instantiate Audio decoder.");
#endif

    if (!audio) {
        AString mime;
        CHECK(format->findString("mime", &mime));
        mVideoIsAVC = !strcasecmp(MEDIA_MIMETYPE_VIDEO_AVC, mime.c_str());
    }

    sp<AMessage> notify =
        new AMessage(audio ? kWhatAudioNotify : kWhatVideoNotify,
                     id());

    *decoder = audio ? new Decoder(notify) :
                       new Decoder(notify, mNativeWindow);
    looper()->registerHandler(*decoder);

    (*decoder)->configure(format);

    return OK;
}

status_t NuPlayer::feedDecoderInputData(bool audio, const sp<AMessage> &msg) {
    sp<AMessage> reply;
    CHECK(msg->findMessage("reply", &reply));

    if ((audio && IsFlushingState(mFlushingAudio))
            || (!audio && IsFlushingState(mFlushingVideo))) {
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("feed Decoder: %s is flushing.", audio?"audio":"video");
#endif
        reply->setInt32("err", INFO_DISCONTINUITY);
        reply->post();
        return OK;
    }

    sp<ABuffer> accessUnit;

#ifndef ANDROID_DEFAULT_CODE
    if ((audio && IsConsumingState(mConsumingAudio))
            || (!audio && IsConsumingState(mConsumingVideo)))
    {
        ALOGD("Consume Decoder: %s is consuming.", audio?"audio":"video");
        reply->setInt32("err", INFO_DISCONTINUITY);
        reply->post();
        return OK;
    }
#endif

    bool dropAccessUnit;
    do {
        status_t err = mSource->dequeueAccessUnit(audio, &accessUnit);

        if (err == -EWOULDBLOCK) {
            return err;
        } else if (err != OK) {
	    ALOGD("%s dequeue accu return %d, accu: %p.",audio?"audio":"video", err, accessUnit.get());
            if (err == INFO_DISCONTINUITY) {
                int32_t type;
                CHECK(accessUnit->meta()->findInt32("discontinuity", &type));

                bool formatChange =
                    (audio &&
                     (type & ATSParser::DISCONTINUITY_AUDIO_FORMAT))
                    || (!audio &&
                            (type & ATSParser::DISCONTINUITY_VIDEO_FORMAT));

                bool timeChange = (type & ATSParser::DISCONTINUITY_TIME) != 0;

                ALOGI("%s discontinuity (formatChange=%d, time=%d)",
                     audio ? "audio" : "video", formatChange, timeChange);

                if (audio) {
                    mSkipRenderingAudioUntilMediaTimeUs = -1;
                } else {
                    mSkipRenderingVideoUntilMediaTimeUs = -1;
                }

                if (timeChange) {
                    sp<AMessage> extra;
                    if (accessUnit->meta()->findMessage("extra", &extra)
                            && extra != NULL) {
                        int64_t resumeAtMediaTimeUs;
                        if (extra->findInt64(
                                    "resume-at-mediatimeUs", &resumeAtMediaTimeUs)) {
                            ALOGI("suppressing rendering of %s until %lld us",
                                    audio ? "audio" : "video", resumeAtMediaTimeUs);

                            if (audio) {
                                mSkipRenderingAudioUntilMediaTimeUs =
                                    resumeAtMediaTimeUs;
                            } else {
                                mSkipRenderingVideoUntilMediaTimeUs =
                                    resumeAtMediaTimeUs;
                            }
                        }
                    }
                }

                mTimeDiscontinuityPending =
                    mTimeDiscontinuityPending || timeChange;

#ifndef ANDROID_DEFAULT_CODE
                if(type == ATSParser::DISCONTINUITY_HTTPLIVE_BANDWIDTH_SWITCHING)
                {
                    ALOGD("ATSParser::DISCONTINUITY_HTTPLIVE_BANDWIDTH_SWITCHING");

                    if (mConsumingAudio == Consume_NONE && mConsumingVideo == Consume_NONE)
                       mDeferredActions.push_back(new SimpleAction(&NuPlayer::performScanSources));
                    
                    consumeDecoder(audio);
                }
                else if (formatChange) { //skip flush while seek/timechange
#else                
                if (formatChange || timeChange) { 
#endif                    
                    if (mFlushingAudio == NONE && mFlushingVideo == NONE) {
                        // And we'll resume scanning sources once we're done
                        // flushing.
                        mDeferredActions.push_front(
                                new SimpleAction(
                                    &NuPlayer::performScanSources));
                    }

                    ALOGD("flush decoder, formatChange = %d", formatChange);
                    flushDecoder(audio, formatChange);
                } else {
                    // This stream is unaffected by the discontinuity

                    if (audio) {
                        mFlushingAudio = FLUSHED;
                    } else {
                        mFlushingVideo = FLUSHED;
                    }

                    finishFlushIfPossible();

                    return -EWOULDBLOCK;
                }
            }

            reply->setInt32("err", err);
            reply->post();
            return OK;
        }

        if (!audio) {
            ++mNumFramesTotal;
        }

        dropAccessUnit = false;
        if (!audio
                && mVideoLateByUs > 100000ll
                && mVideoIsAVC
                && !IsAVCReferenceFrame(accessUnit)) {
            ALOGD("drop %lld / %lld", mNumFramesDropped, mNumFramesTotal);
            dropAccessUnit = true;
            ++mNumFramesDropped;
        }
    } while (dropAccessUnit);

    // ALOGV("returned a valid buffer of %s data", audio ? "audio" : "video");

#if 0
    int64_t mediaTimeUs;
    CHECK(accessUnit->meta()->findInt64("timeUs", &mediaTimeUs));
    ALOGV("feeding %s input buffer at media time %.2f secs",
         audio ? "audio" : "video",
         mediaTimeUs / 1E6);
#endif

    reply->setBuffer("buffer", accessUnit);
    reply->post();

    return OK;
}

void NuPlayer::renderBuffer(bool audio, const sp<AMessage> &msg) {
    // ALOGV("renderBuffer %s", audio ? "audio" : "video");

    sp<AMessage> reply;
    CHECK(msg->findMessage("reply", &reply));
#ifndef ANDROID_DEFAULT_CODE
    {
        Mutex::Autolock autoLock(mLock);
        if (mSeekTimeUs != -1) {
            sp<ABuffer> buffer0;
            CHECK(msg->findBuffer("buffer", &buffer0));
            int64_t mediaTimeUs;
            CHECK(buffer0->meta()->findInt64("timeUs", &mediaTimeUs));
            ALOGD("seeking, %s buffer (%.2f) drop", 
                    audio ? "audio" : "video", mediaTimeUs / 1E6);
            reply->post();
            return;
        }
    }
#endif
    if (IsFlushingState(audio ? mFlushingAudio : mFlushingVideo)) {
        // We're currently attempting to flush the decoder, in order
        // to complete this, the decoder wants all its buffers back,
        // so we don't want any output buffers it sent us (from before
        // we initiated the flush) to be stuck in the renderer's queue.

        ALOGD("we're still flushing the %s decoder, sending its output buffer"
             " right back.", audio ? "audio" : "video");

        reply->post();
        return;
    }

    sp<ABuffer> buffer;
    CHECK(msg->findBuffer("buffer", &buffer));

    int64_t &skipUntilMediaTimeUs =
        audio
            ? mSkipRenderingAudioUntilMediaTimeUs
            : mSkipRenderingVideoUntilMediaTimeUs;

#ifndef ANDROID_DEFAULT_CODE

    if ((skipUntilMediaTimeUs) >= 0) {
        int64_t mediaTimeUs;
        CHECK(buffer->meta()->findInt64("timeUs", &mediaTimeUs));

        if (mediaTimeUs < skipUntilMediaTimeUs) {
            ALOGD("dropping %s buffer at time %.2f s as requested(skipUntil %.2f).",
                 audio ? "audio" : "video",
                 mediaTimeUs / 1E6, skipUntilMediaTimeUs / 1E6);

            reply->post();
            return;
        }
        ALOGI("mediaTime > skipUntilMediaTimeUs ,skip done.mediaTimeUs = %.2f s, skiptime = %.2f", 
                                                         mediaTimeUs / 1E6, skipUntilMediaTimeUs / 1E6);
        skipUntilMediaTimeUs = -1;
    }
	
#else

    if (skipUntilMediaTimeUs >= 0) {
        int64_t mediaTimeUs;
        CHECK(buffer->meta()->findInt64("timeUs", &mediaTimeUs));

        if (mediaTimeUs < skipUntilMediaTimeUs) {
            ALOGV("dropping %s buffer at time %lld as requested.",
                 audio ? "audio" : "video",
                 mediaTimeUs);

            reply->post();
            return;
        }

        skipUntilMediaTimeUs = -1;
    }
#endif	

#ifndef ANDROID_DEFAULT_CODE
  int64_t mediaTimeUs;
  CHECK(buffer->meta()->findInt64("timeUs", &mediaTimeUs));
  ALOGD("queue buffer %s %.2f", audio ? "audio":"\t\tvideo", mediaTimeUs / 1E6);

	int32_t flags;
	CHECK(msg->findInt32("flags", &flags));
	buffer->meta()->setInt32("flags", flags);
#endif

    mRenderer->queueBuffer(audio, buffer, reply);
}

void NuPlayer::notifyListener(int msg, int ext1, int ext2, const Parcel *in) {
    if (mDriver == NULL) {
        return;
    }

    sp<NuPlayerDriver> driver = mDriver.promote();

    if (driver == NULL) {
        return;
    }

#ifndef ANDROID_DEFAULT_CODE
    //try to report a more meaningful error
    if (msg == MEDIA_ERROR && ext1 == MEDIA_ERROR_UNKNOWN) {
        switch(ext2) {
            case ERROR_MALFORMED:
                ext1 = MEDIA_ERROR_BAD_FILE;
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

#endif
    driver->notifyListener(msg, ext1, ext2, in);
}

#ifndef ANDROID_DEFAULT_CODE
void NuPlayer::consumeDecoder(bool audio) {

    ALOGD("++consumeDecoder[%s], mConsuming (%d, %d)", audio?"audio":"video",
            mConsumingAudio, mConsumingVideo);

    // Make sure we don't continue to scan sources until we finish flushing.
    ++mScanSourcesGeneration;
    mScanSourcesPending = false;

    if (audio) {
        CHECK(mConsumingAudio == Consume_NONE);
        mConsumingAudio = Consume_AWAITING_DECODER_EOS;
    } else {
        CHECK(mConsumingVideo == Consume_NONE);
        mConsumingVideo = Consume_AWAITING_DECODER_EOS;
    }

    ALOGD("--consumeDecoder[%s], mConsuming (%d, %d)", audio?"audio":"video",
            mConsumingAudio, mConsumingVideo);
}
#endif

void NuPlayer::flushDecoder(bool audio, bool needShutdown) {
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("++flushDecoder[%s], mFlushing %d, %d (%d, %d)", audio?"audio":"video",
            mFlushingAudio, mFlushingVideo,
            (int)mAudioAbsent, (int)mVideoAbsent);
#endif
    if ((audio && mAudioDecoder == NULL) || (!audio && mVideoDecoder == NULL)) {
        ALOGI("flushDecoder %s without decoder present",
             audio ? "audio" : "video");
    }

    // Make sure we don't continue to scan sources until we finish flushing.
    ++mScanSourcesGeneration;
    mScanSourcesPending = false;

    (audio ? mAudioDecoder : mVideoDecoder)->signalFlush();
#ifndef ANDROID_DEFAULT_CODE
    if (mRenderer != NULL)
#endif
    mRenderer->flush(audio);

    FlushStatus newStatus =
        needShutdown ? FLUSHING_DECODER_SHUTDOWN : FLUSHING_DECODER;

    if (audio) {
        CHECK(mFlushingAudio == NONE
                || mFlushingAudio == AWAITING_DISCONTINUITY);

        mFlushingAudio = newStatus;

        if (mFlushingVideo == NONE) {
            mFlushingVideo = (mVideoDecoder != NULL)
                ? AWAITING_DISCONTINUITY
                : FLUSHED;
        }
    } else {
        CHECK(mFlushingVideo == NONE
                || mFlushingVideo == AWAITING_DISCONTINUITY);

        mFlushingVideo = newStatus;

        if (mFlushingAudio == NONE) {
            mFlushingAudio = (mAudioDecoder != NULL)
                ? AWAITING_DISCONTINUITY
                : FLUSHED;
        }
    }
    ALOGD("--flushDecoder[%s] end, mFlushing %d, %d", audio?"audio":"video", mFlushingAudio, mFlushingVideo);
}

sp<AMessage> NuPlayer::Source::getFormat(bool audio) {
    sp<MetaData> meta = getFormatMeta(audio);

    if (meta == NULL) {
        return NULL;
    }

    sp<AMessage> msg = new AMessage;

    if(convertMetaDataToMessage(meta, &msg) == OK) {
        return msg;
    }
    return NULL;
}

status_t NuPlayer::setVideoScalingMode(int32_t mode) {
    mVideoScalingMode = mode;
    if (mNativeWindow != NULL
            && mNativeWindow->getNativeWindow() != NULL) {
        status_t ret = native_window_set_scaling_mode(
                mNativeWindow->getNativeWindow().get(), mVideoScalingMode);
        if (ret != OK) {
            ALOGE("Failed to set scaling mode (%d): %s",
                -ret, strerror(-ret));
            return ret;
        }
    }
    return OK;
}

status_t NuPlayer::getTrackInfo(Parcel* reply) const {
    sp<AMessage> msg = new AMessage(kWhatGetTrackInfo, id());
    msg->setPointer("reply", reply);

    sp<AMessage> response;
    status_t err = msg->postAndAwaitResponse(&response);
    return err;
}

status_t NuPlayer::selectTrack(size_t trackIndex, bool select) {
    sp<AMessage> msg = new AMessage(kWhatSelectTrack, id());
    msg->setSize("trackIndex", trackIndex);
    msg->setInt32("select", select);

    sp<AMessage> response;
    status_t err = msg->postAndAwaitResponse(&response);

    return err;
}

void NuPlayer::schedulePollDuration() {
    sp<AMessage> msg = new AMessage(kWhatPollDuration, id());
    msg->setInt32("generation", mPollDurationGeneration);
    msg->post();
}

void NuPlayer::cancelPollDuration() {
    ++mPollDurationGeneration;
}

void NuPlayer::processDeferredActions() {
    while (!mDeferredActions.empty()) {
        // We won't execute any deferred actions until we're no longer in
        // an intermediate state, i.e. one more more decoders are currently
        // flushing or shutting down.

        if (mRenderer != NULL) {
            // There's an edge case where the renderer owns all output
            // buffers and is paused, therefore the decoder will not read
            // more input data and will never encounter the matching
            // discontinuity. To avoid this, we resume the renderer.

            if (mFlushingAudio == AWAITING_DISCONTINUITY
                    || mFlushingVideo == AWAITING_DISCONTINUITY) {
                mRenderer->resume();
            }
        }

#ifndef ANDROID_DEFAULT_CODE
        // mtk80902: SHUT_DOWN also means not flushing
        if ((mFlushingVideo != NONE && mFlushingVideo != SHUT_DOWN) ||
            (mFlushingAudio != NONE && mFlushingAudio != SHUT_DOWN)  ||
            (mConsumingVideo != Consume_NONE) || (mConsumingAudio != Consume_NONE)) {
#else
        if (mFlushingAudio != NONE || mFlushingVideo != NONE) {
#endif
            // We're currently flushing, postpone the reset until that's
            // completed.

            ALOGV("postponing action mFlushingAudio=%d, mFlushingVideo=%d",
                  mFlushingAudio, mFlushingVideo);

            break;
        }

        sp<Action> action = *mDeferredActions.begin();
        mDeferredActions.erase(mDeferredActions.begin());

        action->execute(this);
    }
}

void NuPlayer::performSeek(int64_t seekTimeUs) {
    ALOGV("performSeek seekTimeUs=%lld us (%.2f secs)",
          seekTimeUs,
          seekTimeUs / 1E6);

#ifndef ANDROID_DEFAULT_CODE
    status_t err = mSource->seekTo(seekTimeUs);
    if (err == -EWOULDBLOCK) {  // finish seek when receive Source::kWhatSeekDone
        mTimeDiscontinuityPending = true;
        ALOGD("seek async, waiting Source seek done");
    } else if (err == OK) {
        if (flushAfterSeekIfNecessary()) {
            mTimeDiscontinuityPending = true;
        } else {
            finishSeek();
        }
    } else {
        ALOGE("seek error %d", (int)err);
        // add notify seek complete
        finishSeek();
    }
#else
    mSource->seekTo(seekTimeUs);

    if (mDriver != NULL) {
        sp<NuPlayerDriver> driver = mDriver.promote();
        if (driver != NULL) {
            driver->notifyPosition(seekTimeUs);
            driver->notifySeekComplete();
        }
    }

#endif
    // everything's flushed, continue playback.
}

void NuPlayer::performDecoderFlush() {
    ALOGV("performDecoderFlush");

    if (mAudioDecoder == NULL && mVideoDecoder == NULL) {
        return;
    }

    mTimeDiscontinuityPending = true;

    if (mAudioDecoder != NULL) {
        flushDecoder(true /* audio */, false /* needShutdown */);
    }

    if (mVideoDecoder != NULL) {
        flushDecoder(false /* audio */, false /* needShutdown */);
    }
}

void NuPlayer::performDecoderShutdown(bool audio, bool video) {
    ALOGV("performDecoderShutdown audio=%d, video=%d", audio, video);

    if ((!audio || mAudioDecoder == NULL)
            && (!video || mVideoDecoder == NULL)) {
        return;
    }

    mTimeDiscontinuityPending = true;

    if (mFlushingAudio == NONE && (!audio || mAudioDecoder == NULL)) {
        mFlushingAudio = FLUSHED;
    }

    if (mFlushingVideo == NONE && (!video || mVideoDecoder == NULL)) {
        mFlushingVideo = FLUSHED;
    }

    if (audio && mAudioDecoder != NULL) {
        flushDecoder(true /* audio */, true /* needShutdown */);
    }

    if (video && mVideoDecoder != NULL) {
        flushDecoder(false /* audio */, true /* needShutdown */);
    }
}

void NuPlayer::performReset() {
    ALOGD("performReset");

    CHECK(mAudioDecoder == NULL);
    CHECK(mVideoDecoder == NULL);

#ifndef ANDROID_DEFAULT_CODE
    mPlayState = STOPPED;
#endif
    cancelPollDuration();

    ++mScanSourcesGeneration;
    mScanSourcesPending = false;

    mRenderer.clear();

    if (mSource != NULL) {
        mSource->stop();

        looper()->unregisterHandler(mSource->id());

        mSource.clear();
    }

    if (mDriver != NULL) {
        sp<NuPlayerDriver> driver = mDriver.promote();
        if (driver != NULL) {
            driver->notifyResetComplete();
        }
    }

    mStarted = false;
}

void NuPlayer::performScanSources() {
    ALOGD("performScanSources");

    if (!mStarted) {
        return;
    }

    if (mAudioDecoder == NULL || mVideoDecoder == NULL) {
        postScanSources();
    }
}

void NuPlayer::performSetSurface(const sp<NativeWindowWrapper> &wrapper) {
    ALOGD("performSetSurface");

    mNativeWindow = wrapper;

#ifndef ANDROID_DEFAULT_CODE
    if (mNativeWindow != NULL) {
        ALOGD("\t\tset native window: %p", mNativeWindow->getNativeWindow().get());
        if (mNativeWindow->getNativeWindow().get() != NULL) {
            setVideoScalingMode(mVideoScalingMode);
        }
    }
#else
    // XXX - ignore error from setVideoScalingMode for now
    setVideoScalingMode(mVideoScalingMode);
#endif

    if (mDriver != NULL) {
        sp<NuPlayerDriver> driver = mDriver.promote();
        if (driver != NULL) {
            driver->notifySetSurfaceComplete();
        }
    }
}

void NuPlayer::onSourceNotify(const sp<AMessage> &msg) {
    int32_t what;
    CHECK(msg->findInt32("what", &what));

    switch (what) {
        case Source::kWhatPrepared:
        {
            if (mSource == NULL) {
                // This is a stale notification from a source that was
                // asynchronously preparing when the client called reset().
                // We handled the reset, the source is gone.
                break;
            }

            int32_t err;
            CHECK(msg->findInt32("err", &err));

#ifndef ANDROID_DEFAULT_CODE
            if (mPrepare == PREPARED) //TODO: this would would happen when MyHandler disconnect
                break;
            
            if (err != OK) {
                finishPrepare(err);
                break;
            } else if (mSource == NULL) {  // ALPS00779817
                ALOGW("prepare error: source is not ready");
                finishPrepare(UNKNOWN_ERROR);
                break;
            } 
            // if data source is streamingsource or local, the scan will be started in kWhatStart
            finishPrepare();
#else
            sp<NuPlayerDriver> driver = mDriver.promote();
            if (driver != NULL) {
                driver->notifyPrepareCompleted(err);
            }

            int64_t durationUs;
            if (mDriver != NULL && mSource->getDuration(&durationUs) == OK) {
                sp<NuPlayerDriver> driver = mDriver.promote();
                if (driver != NULL) {
                    driver->notifyDuration(durationUs);
                }
            }            
#endif
            break;
        }

        case Source::kWhatFlagsChanged:
        {
            uint32_t flags;
            CHECK(msg->findInt32("flags", (int32_t *)&flags));

            sp<NuPlayerDriver> driver = mDriver.promote();
            if (driver != NULL) {
                driver->notifyFlagsChanged(flags);
            }

            if ((mSourceFlags & Source::FLAG_DYNAMIC_DURATION)
                    && (!(flags & Source::FLAG_DYNAMIC_DURATION))) {
                cancelPollDuration();
            } else if (!(mSourceFlags & Source::FLAG_DYNAMIC_DURATION)
                    && (flags & Source::FLAG_DYNAMIC_DURATION)
                    && (mAudioDecoder != NULL || mVideoDecoder != NULL)) {
                schedulePollDuration();
            }

            mSourceFlags = flags;
            break;
        }

        case Source::kWhatVideoSizeChanged:
        {
            int32_t width, height;
            CHECK(msg->findInt32("width", &width));
            CHECK(msg->findInt32("height", &height));

            notifyListener(MEDIA_SET_VIDEO_SIZE, width, height);
            break;
        }

        case Source::kWhatBufferingStart:
        {
#ifndef ANDROID_DEFAULT_CODE
            // mtk80902: ALPS00436540
            // porting flags from AwesomePlayer - buffering can be interrupted
            // by pause - send by plugging out earphone.
            // here rate <= 0 to set CACHE_UNDERRUN:
            // 1 reduce buffering 0% notify
            // 2 the first buffering has no rate with -1.. see RTSPSource.
            if(mDataSourceType == SOURCE_Rtsp)
            {
            ALOGD("mFlags %d; mPlayState %d, buffering start", 
                    mFlags, mPlayState);
            if (!(mFlags & CACHE_UNDERRUN)) { 
                mFlags |= CACHE_UNDERRUN;
                if (mPlayState == PLAYING) {
                    notifyListener(MEDIA_INFO, MEDIA_INFO_BUFFERING_START, 0);
                    if (mRenderer != NULL)
                        mRenderer->pause();
                }
              }
            }
            else
#endif
            notifyListener(MEDIA_INFO, MEDIA_INFO_BUFFERING_START, 0);
            break;
        }

        case Source::kWhatBufferingEnd:
        {
#ifndef ANDROID_DEFAULT_CODE
            if(mDataSourceType == SOURCE_Rtsp)
            { 
            ALOGD("mFlags %d; mPlayState %d, buffering end", 
                    mFlags, mPlayState);
            if ((mFlags & CACHE_UNDERRUN)) {
                // mtk80902: ALPS00529472 - always notify buf end
                // racing condition: buffering - pause - buffer end - pause done
                // this case buffer end will never be notified if being placed
                // in mPlayStat == PLAYING {}
                notifyListener(MEDIA_BUFFERING_UPDATE, 100, 0);
                notifyListener(MEDIA_INFO, MEDIA_INFO_BUFFERING_END, 0);
                if (mPlayState == PLAYING) {
                    if (mRenderer != NULL)
                        mRenderer->resume();
                }
                mFlags &= ~CACHE_UNDERRUN;
             }
           }
           else
#endif
            notifyListener(MEDIA_INFO, MEDIA_INFO_BUFFERING_END, 0);
            break;
        }

        case Source::kWhatSubtitleData:
        {
            sp<ABuffer> buffer;
            CHECK(msg->findBuffer("buffer", &buffer));

            int32_t trackIndex;
            int64_t timeUs, durationUs;
            CHECK(buffer->meta()->findInt32("trackIndex", &trackIndex));
            CHECK(buffer->meta()->findInt64("timeUs", &timeUs));
            CHECK(buffer->meta()->findInt64("durationUs", &durationUs));

            Parcel in;
            in.writeInt32(trackIndex);
            in.writeInt64(timeUs);
            in.writeInt64(durationUs);
            in.writeInt32(buffer->size());
            in.writeInt32(buffer->size());
            in.write(buffer->data(), buffer->size());

            notifyListener(MEDIA_SUBTITLE_DATA, 0, 0, &in);
            break;
        }

        case Source::kWhatQueueDecoderShutdown:
        {
            int32_t audio, video;
            CHECK(msg->findInt32("audio", &audio));
            CHECK(msg->findInt32("video", &video));

            sp<AMessage> reply;
            CHECK(msg->findMessage("reply", &reply));
            queueDecoderShutdown(audio, video, reply);
            break;
        }
#ifndef ANDROID_DEFAULT_CODE
        case Source::kWhatBufferNotify: 
        {
            int32_t rate;
            CHECK(msg->findInt32("bufRate", &rate));
            ALOGD("mFlags %d; mPlayState %d, buffering rate %d", 
                    mFlags, mPlayState, rate);
            if (mPlayState == PLAYING && (mFlags & CACHE_UNDERRUN)) {
                notifyListener(MEDIA_BUFFERING_UPDATE, rate, 0);
            }
            break;
        }
        case Source::kWhatSeekDone:
        {
            if (!flushAfterSeekIfNecessary() && mSeekTimeUs != -1) {
                // restore default
                // result = ok means this seek has discontinuty
                // and should be completed by flush, otherwise
                // it's interrupted before send play, and should
                // be done here.
                //	int32_t ret;
                //	CHECK(msg->findInt32("result", &ret));
                //	if (ret != EINPROGRESS) {
                finishSeek();
            } 

            break;
        }
	    case NuPlayer::Source::kWhatPlayDone:
        {
            int32_t ret;
            CHECK(msg->findInt32("result", &ret));
            ALOGI("play done with result %d.", ret);
            notifyListener(MEDIA_PLAY_COMPLETE, ret, 0);
            if (ret == OK)
                mPlayState = PLAYING;
            // mtk80902: ALPS00439792
            // special case: pause -> seek -> resume ->
            //  seek complete -> resume complete
            // in this case render cant resume in SeekDone
            if (mRenderer != NULL)
                mRenderer->resume();
            break;
        }
        case NuPlayer::Source::kWhatPauseDone:
        {
            int32_t ret;
            CHECK(msg->findInt32("result", &ret));
            notifyListener(MEDIA_PAUSE_COMPLETE, ret, 0);
            // ALPS00567579 - an extra pause done?
            if (mPlayState != PAUSING) {
                ALOGW("what's up? an extra pause done?");
                break;
            }
            if (ret == OK)
                mPlayState = PAUSED;
            if (mFlags & CACHE_UNDERRUN) {
                notifyListener(MEDIA_BUFFERING_UPDATE, 100, 0);
                notifyListener(MEDIA_INFO, MEDIA_INFO_BUFFERING_END, 0);
                mFlags &= ~CACHE_UNDERRUN;
            }
            break;
        }
        case NuPlayer::Source::kWhatPicture:// orange compliance
        {
            // audio-only stream containing picture for display
            ALOGI("Notify picture existence");
            notifyListener(MEDIA_INFO, MEDIA_INFO_METADATA_UPDATE, 0);
            break;
        }
#endif

        default:
            TRESPASS();
    }
}

////////////////////////////////////////////////////////////////////////////////

void NuPlayer::Source::notifyFlagsChanged(uint32_t flags) {
    sp<AMessage> notify = dupNotify();
    notify->setInt32("what", kWhatFlagsChanged);
    notify->setInt32("flags", flags);
    notify->post();
}

void NuPlayer::Source::notifyVideoSizeChanged(int32_t width, int32_t height) {
    sp<AMessage> notify = dupNotify();
    notify->setInt32("what", kWhatVideoSizeChanged);
    notify->setInt32("width", width);
    notify->setInt32("height", height);
    notify->post();
}

void NuPlayer::Source::notifyPrepared(status_t err) {
    sp<AMessage> notify = dupNotify();
    notify->setInt32("what", kWhatPrepared);
    notify->setInt32("err", err);
    notify->post();
}

void NuPlayer::Source::onMessageReceived(const sp<AMessage> &msg) {
    TRESPASS();
}

void NuPlayer::queueDecoderShutdown(
        bool audio, bool video, const sp<AMessage> &reply) {
    ALOGI("queueDecoderShutdown audio=%d, video=%d", audio, video);

    mDeferredActions.push_back(
            new ShutdownDecoderAction(audio, video));

    mDeferredActions.push_back(
            new SimpleAction(&NuPlayer::performScanSources));

    mDeferredActions.push_back(new PostMessageAction(reply));

    processDeferredActions();
}

#ifndef ANDROID_DEFAULT_CODE
// static
bool NuPlayer::IsConsumingState(ConsumeStatus state) {
    switch (state) {
        case Consume_AWAITING_DECODER_EOS:
        case Consume_AWAITING_RENDER_EOS:
        case Consume_AWAITING_DECODER_SHUTDOWN:
        case Consume_DONE:
            return true;

        default:
            return false;
    }
}

bool NuPlayer::flushAfterSeekIfNecessary() {
    bool bWaitingFlush = false;
    bool bNeedShutdown = false;
    if(mDataSourceType == SOURCE_HttpLive) {
      if(IsConsumingState(mConsumingAudio) || IsConsumingState(mConsumingVideo)) {
          //terminate consume while seeking
          mConsumingVideo = Consume_NONE;
          mConsumingAudio = Consume_NONE;
      }
      else
          mDeferredActions.push_back(new SimpleAction(&NuPlayer::performScanSources));

      bNeedShutdown = true;
    }
    
    if (mAudioDecoder == NULL) {
            ALOGD("audio is not there, reset the flush flag");
            mFlushingAudio = NONE;
        } else if ( mFlushingAudio == NONE || mFlushingAudio == AWAITING_DISCONTINUITY)  {
            flushDecoder(true /* audio */, bNeedShutdown);
            bWaitingFlush = true;
        } else {
            //TODO: if there is many discontinuity, flush still is needed
            ALOGD("audio is already being flushed");
    }

    if (mVideoDecoder == NULL) {
            ALOGD("video is not there, reset the flush flag");
            mFlushingVideo = NONE;
        } else if (mFlushingVideo == NONE || mFlushingVideo == AWAITING_DISCONTINUITY) {
            flushDecoder(false /* video */, bNeedShutdown);
            bWaitingFlush = true;
        } else {
            //TODO: if there is many discontinuity, flush still is needed
            ALOGD("video is already being flushed");
    }

    return bWaitingFlush;
}

void NuPlayer::finishSeek() {
    if (mRenderer != NULL) {  //resume render
        if (mPlayState == PLAYING)
            mRenderer->resume();
    }
    if (mDriver != NULL) {
        sp<NuPlayerDriver> driver = mDriver.promote();
        if (driver != NULL) {
            driver->notifyPosition(mSeekTimeUs);
            driver->notifySeekComplete();
            ALOGI("seek(%.2f)  completed", mSeekTimeUs / 1E6);
        }
    }
    mSeekTimeUs = -1;    
}

sp<MetaData> NuPlayer::getMetaData() const {
    return mSource->getMetaData();
}
#endif

}  // namespace android
