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
#define LOG_TAG "NuPlayerDriver"
#include <utils/Log.h>

#include "NuPlayerDriver.h"

#include "NuPlayer.h"
#include "NuPlayerSource.h"

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/ALooper.h>
#include <media/stagefright/MetaData.h>

#ifndef ANDROID_DEFAULT_CODE
// for ALPS00595180
#include <media/stagefright/MediaErrors.h>
#endif

namespace android {

NuPlayerDriver::NuPlayerDriver()
    : mState(STATE_IDLE),
      mIsAsyncPrepare(false),
      mAsyncResult(UNKNOWN_ERROR),
      mSetSurfaceInProgress(false),
      mDurationUs(-1),
      mPositionUs(-1),
      mNumFramesTotal(0),
      mNumFramesDropped(0),
      mLooper(new ALooper),
      mPlayerFlags(0),
      mAtEOS(false),
      mStartupSeekTimeUs(-1) {
#ifndef ANDROID_DEFAULT_CODE
      mSeekTimeUs = -1;
#endif
    mLooper->setName("NuPlayerDriver Looper");

    mLooper->start(
            false, /* runOnCallingThread */
            true,  /* canCallJava */
            PRIORITY_AUDIO);

    mPlayer = new NuPlayer;
    mLooper->registerHandler(mPlayer);

    mPlayer->setDriver(this);
}

NuPlayerDriver::~NuPlayerDriver() {
    mLooper->stop();
#ifndef ANDROID_DEFAULT_CODE
    mLooper->unregisterHandler(mPlayer->id());
#endif
}

status_t NuPlayerDriver::initCheck() {
    return OK;
}

status_t NuPlayerDriver::setUID(uid_t uid) {
    mPlayer->setUID(uid);

    return OK;
}

status_t NuPlayerDriver::setDataSource(
        const char *url, const KeyedVector<String8, String8> *headers) {
    Mutex::Autolock autoLock(mLock);

    if (mState != STATE_IDLE) {
        return INVALID_OPERATION;
    }

    mState = STATE_SET_DATASOURCE_PENDING;

    mPlayer->setDataSourceAsync(url, headers);

    while (mState == STATE_SET_DATASOURCE_PENDING) {
        mCondition.wait(mLock);
    }

    return mAsyncResult;
}

status_t NuPlayerDriver::setDataSource(int fd, int64_t offset, int64_t length) {
    Mutex::Autolock autoLock(mLock);

    if (mState != STATE_IDLE) {
        return INVALID_OPERATION;
    }

    mState = STATE_SET_DATASOURCE_PENDING;

    mPlayer->setDataSourceAsync(fd, offset, length);

    while (mState == STATE_SET_DATASOURCE_PENDING) {
        mCondition.wait(mLock);
    }

    return mAsyncResult;
}

status_t NuPlayerDriver::setDataSource(const sp<IStreamSource> &source) {
    Mutex::Autolock autoLock(mLock);

    if (mState != STATE_IDLE) {
        return INVALID_OPERATION;
    }

    mState = STATE_SET_DATASOURCE_PENDING;

    mPlayer->setDataSourceAsync(source);

    while (mState == STATE_SET_DATASOURCE_PENDING) {
        mCondition.wait(mLock);
    }

    return mAsyncResult;
}

status_t NuPlayerDriver::setVideoSurfaceTexture(
        const sp<IGraphicBufferProducer> &bufferProducer) {
    Mutex::Autolock autoLock(mLock);

    if (mSetSurfaceInProgress) {
        return INVALID_OPERATION;
    }

    switch (mState) {
        case STATE_SET_DATASOURCE_PENDING:
        case STATE_RESET_IN_PROGRESS:
            return INVALID_OPERATION;

        default:
            break;
    }

    mSetSurfaceInProgress = true;

    mPlayer->setVideoSurfaceTextureAsync(bufferProducer);

    while (mSetSurfaceInProgress) {
        mCondition.wait(mLock);
    }

    return OK;
}

status_t NuPlayerDriver::prepare() {
    Mutex::Autolock autoLock(mLock);
    return prepare_l();
}

status_t NuPlayerDriver::prepare_l() {
    switch (mState) {
        case STATE_UNPREPARED:
            mState = STATE_PREPARING;

            // Make sure we're not posting any notifications, success or
            // failure information is only communicated through our result
            // code.
            mIsAsyncPrepare = false;
            mPlayer->prepareAsync();
            while (mState == STATE_PREPARING) {
                mCondition.wait(mLock);
            }
            return (mState == STATE_PREPARED) ? OK : UNKNOWN_ERROR;
        default:
            return INVALID_OPERATION;
    };
}

status_t NuPlayerDriver::prepareAsync() {
    Mutex::Autolock autoLock(mLock);

    switch (mState) {
        case STATE_UNPREPARED:
            mState = STATE_PREPARING;
            mIsAsyncPrepare = true;
            mPlayer->prepareAsync();
            return OK;
        default:
            return INVALID_OPERATION;
    };
}

status_t NuPlayerDriver::start() {
    ALOGD("start, mState = %d", (int)mState);
    Mutex::Autolock autoLock(mLock);

    switch (mState) {
        case STATE_UNPREPARED:
        {
            status_t err = prepare_l();

            if (err != OK) {
                return err;
            }

            CHECK_EQ(mState, STATE_PREPARED);

            // fall through
        }
#ifndef ANDROID_DEFAULT_CODE
        case STATE_PREPARING:   //the start will serialized after prepare
#endif

        case STATE_PREPARED:
        {
            mAtEOS = false;
            mPlayer->start();

            if (mStartupSeekTimeUs >= 0) {
                if (mStartupSeekTimeUs == 0) {
#ifndef ANDROID_DEFAULT_CODE
                    // mtk80902: ALPS01266507 - browser seekTo then start
                    // double lock in seek complete
                    mSeekTimeUs = -1;
                    notifyListener(MEDIA_SEEK_COMPLETE);
#else
                    notifySeekComplete();
#endif
                } else {
                    mPlayer->seekToAsync(mStartupSeekTimeUs);
                }

                mStartupSeekTimeUs = -1;
            }
            break;
        }

        case STATE_RUNNING:
            break;

        case STATE_PAUSED:
        {
            mPlayer->resume();
            break;
        }

        default:
            return INVALID_OPERATION;
    }

    mState = STATE_RUNNING;

    return OK;
}

status_t NuPlayerDriver::stop() {
#ifndef ANDROID_DEFAULT_CODE
    Mutex::Autolock autoLock(mLock);

    switch (mState) {
        case STATE_PAUSED:
        case STATE_PREPARED:
            return OK;

        case STATE_RUNNING:
            mPlayer->stop();
            break;

        default:
            return INVALID_OPERATION;
    }

    mState = STATE_PAUSED;
    return OK;
#endif
    return pause();
}

status_t NuPlayerDriver::pause() {
    Mutex::Autolock autoLock(mLock);

    switch (mState) {
        case STATE_PAUSED:
        case STATE_PREPARED:
            return OK;

        case STATE_RUNNING:
            notifyListener(MEDIA_PAUSED);
            mPlayer->pause();
            break;

        default:
            return INVALID_OPERATION;
    }

    mState = STATE_PAUSED;

    return OK;
}

bool NuPlayerDriver::isPlaying() {
    return mState == STATE_RUNNING && !mAtEOS;
}

status_t NuPlayerDriver::seekTo(int msec) {
    Mutex::Autolock autoLock(mLock);

    int64_t seekTimeUs = msec * 1000ll;
#ifndef ANDROID_DEFAULT_CODE 
    //it's live streaming, assume it's ok to seek, because some 3rd party don't get info of live
    if (mDurationUs <= 0 ) {
        notifyListener(MEDIA_SEEK_COMPLETE);
        ALOGE("cannot seek without duration, assume to seek complete");
        return OK;
    }
    ALOGD("seekTo(%d ms) mState = %d", msec, (int)mState);
#endif 

    switch (mState) {
        case STATE_PREPARED:
        {
#ifndef ANDROID_DEFAULT_CODE
            mPositionUs = seekTimeUs;
#endif
            mStartupSeekTimeUs = seekTimeUs;
            break;
        }

        case STATE_RUNNING:
        case STATE_PAUSED:
        {
            mAtEOS = false;
#ifndef ANDROID_DEFAULT_CODE
            mSeekTimeUs = seekTimeUs;
            mPositionUs = seekTimeUs;
            CHECK(mSeekTimeUs != -1);
#endif
            // seeks can take a while, so we essentially paused
            notifyListener(MEDIA_PAUSED);
            mPlayer->seekToAsync(seekTimeUs);
            break;
        }

        default:
            return INVALID_OPERATION;
    }

    return OK;
}

status_t NuPlayerDriver::getCurrentPosition(int *msec) {
    Mutex::Autolock autoLock(mLock);

    if (mPositionUs < 0) {
        *msec = 0;
    } else {
        *msec = (mPositionUs + 500ll) / 1000;
    }

    return OK;
}

status_t NuPlayerDriver::getDuration(int *msec) {
    Mutex::Autolock autoLock(mLock);

    if (mDurationUs < 0) {
#ifndef ANDROID_DEFAULT_CODE
        *msec = 0;
        return OK;
#else
        return UNKNOWN_ERROR;
#endif
    } 

#ifndef ANDROID_DEFAULT_CODE
	if (mDurationUs/1000 > 0x7fffffff) {
		ALOGI("Duration(%llxms) > 0x7fffffff ms, reset it to %xms", mDurationUs/1000,  0x7fffffff);
		mDurationUs = 0x7fffffffLL*1000;
	}
#endif
	
    *msec = (mDurationUs + 500ll) / 1000;

    return OK;
}

status_t NuPlayerDriver::reset() {
    Mutex::Autolock autoLock(mLock);
    ALOGD("reset when mState = %d", mState);

    switch (mState) {
        case STATE_IDLE:
            return OK;

        case STATE_SET_DATASOURCE_PENDING:
        case STATE_RESET_IN_PROGRESS:
            return INVALID_OPERATION;

        case STATE_PREPARING:
        {
            CHECK(mIsAsyncPrepare);

            notifyListener(MEDIA_PREPARED);
            break;
        }

        default:
            break;
    }

    notifyListener(MEDIA_STOPPED);

    mState = STATE_RESET_IN_PROGRESS;
    mPlayer->resetAsync();

    while (mState == STATE_RESET_IN_PROGRESS) {
        mCondition.wait(mLock);
    }

    mDurationUs = -1;
    mPositionUs = -1;
    mStartupSeekTimeUs = -1;
#ifndef ANDROID_DEFAULT_CODE
    mSeekTimeUs = -1;
#endif

    return OK;
}

status_t NuPlayerDriver::setLooping(int loop) {
    return INVALID_OPERATION;
}

player_type NuPlayerDriver::playerType() {
    return NU_PLAYER;
}

status_t NuPlayerDriver::invoke(const Parcel &request, Parcel *reply) {
    if (reply == NULL) {
        ALOGE("reply is a NULL pointer");
        return BAD_VALUE;
    }

    int32_t methodId;
    status_t ret = request.readInt32(&methodId);
    if (ret != OK) {
        ALOGE("Failed to retrieve the requested method to invoke");
        return ret;
    }

    switch (methodId) {
        case INVOKE_ID_SET_VIDEO_SCALING_MODE:
        {
            int mode = request.readInt32();
            return mPlayer->setVideoScalingMode(mode);
        }

        case INVOKE_ID_GET_TRACK_INFO:
        {
            return mPlayer->getTrackInfo(reply);
        }

        case INVOKE_ID_SELECT_TRACK:
        {
            int trackIndex = request.readInt32();
            return mPlayer->selectTrack(trackIndex, true /* select */);
        }

        case INVOKE_ID_UNSELECT_TRACK:
        {
            int trackIndex = request.readInt32();
            return mPlayer->selectTrack(trackIndex, false /* select */);
        }

        default:
        {
            return INVALID_OPERATION;
        }
    }
}

void NuPlayerDriver::setAudioSink(const sp<AudioSink> &audioSink) {
    mPlayer->setAudioSink(audioSink);
}

status_t NuPlayerDriver::setParameter(int key, const Parcel &request) {
#if !defined(ANDROID_DEFAULT_CODE) && defined(MTK_CLEARMOTION_SUPPORT)  
	switch (key) {
		case KEY_PARAMETER_CLEARMOTION_DISABLE:
		{
			int32_t disClearMotion;
			request.readInt32(&disClearMotion);
			ALOGI("setParameter enClearMotion %d",disClearMotion);
			if(disClearMotion)
				mPlayer->enableClearMotion(0);
			else
				mPlayer->enableClearMotion(1);				
		}	
        default: 
		{
            return INVALID_OPERATION;
        }
	}
#else
	return INVALID_OPERATION;
#endif
}

status_t NuPlayerDriver::getParameter(int key, Parcel *reply) {
    return INVALID_OPERATION;
}

status_t NuPlayerDriver::getMetadata(
        const media::Metadata::Filter& ids, Parcel *records) {
    Mutex::Autolock autoLock(mLock);

    using media::Metadata;

    Metadata meta(records);

#ifndef ANDROID_DEFAULT_CODE
    // mtk80902: try android default's kXXXAvailable

    // mtk80902: ALPS00448589
    // porting from Stagefright
    sp<MetaData> player_meta = mPlayer->getMetaData();
    if (player_meta != NULL) {
        int timeout = 0;
        if (player_meta->findInt32(kKeyServerTimeout, &timeout) && timeout > 0) {
            meta.appendInt32(Metadata::kServerTimeout, timeout);
        }

        const char *val;
        if (player_meta->findCString(kKeyTitle, &val)) {
            ALOGI("meta title %s ", val);
            meta.appendString(Metadata::kTitle, val);
        }
        if (player_meta->findCString(kKeyAuthor, &val)) {
            ALOGI("meta author %s ", val);
            meta.appendString(Metadata::kAuthor, val);
        }
        if(player_meta->findCString(kKeyAlbumArtMIME, &val))
        {
            meta.appendString(Metadata::kMimeType, val);
            ALOGI("meta kKeyAlbumArtMIME %s ", val);
        }

        uint32_t type;
        size_t dataSize;
        const void *data;
        if(player_meta->findData(kKeyAlbumArt, &type, &data, &dataSize))
        {
            const char *val2 = (const char *)data;
            meta.appendByteArray(Metadata::kAlbumArt, val2, dataSize);
            ALOGI("meta kKeyAlbumArt 0x%X0x%X0x%X0x%X, Size(%d)", val2[0], val2[1], val2[2], val2[3], dataSize);
        }
    }
#endif
    meta.appendBool(
            Metadata::kPauseAvailable,
            mPlayerFlags & NuPlayer::Source::FLAG_CAN_PAUSE);

    meta.appendBool(
            Metadata::kSeekBackwardAvailable,
            mPlayerFlags & NuPlayer::Source::FLAG_CAN_SEEK_BACKWARD);

    meta.appendBool(
            Metadata::kSeekForwardAvailable,
            mPlayerFlags & NuPlayer::Source::FLAG_CAN_SEEK_FORWARD);

    meta.appendBool(
            Metadata::kSeekAvailable,
            mPlayerFlags & NuPlayer::Source::FLAG_CAN_SEEK);

    return OK;
}

void NuPlayerDriver::notifyResetComplete() {
    Mutex::Autolock autoLock(mLock);

    CHECK_EQ(mState, STATE_RESET_IN_PROGRESS);
    mState = STATE_IDLE;
    mCondition.broadcast();
}

void NuPlayerDriver::notifySetSurfaceComplete() {
    Mutex::Autolock autoLock(mLock);

    CHECK(mSetSurfaceInProgress);
    mSetSurfaceInProgress = false;

    mCondition.broadcast();
}

void NuPlayerDriver::notifyDuration(int64_t durationUs) {
    Mutex::Autolock autoLock(mLock);
    mDurationUs = durationUs;
}

void NuPlayerDriver::notifyPosition(int64_t positionUs) {
    Mutex::Autolock autoLock(mLock);
#ifndef ANDROID_DEFAULT_CODE
    if (mSeekTimeUs != -1) {
        ALOGV("position don't update because seeking %.2f", positionUs / 1E6);
    } else {
        mPositionUs = positionUs;
    }
#else
    mPositionUs = positionUs;
#endif
}

void NuPlayerDriver::notifySeekComplete() {
#ifndef ANDROID_DEFAULT_CODE
    Mutex::Autolock autoLock(mLock);
    mSeekTimeUs = -1;
#endif
    notifyListener(MEDIA_SEEK_COMPLETE);
}

void NuPlayerDriver::notifyFrameStats(
        int64_t numFramesTotal, int64_t numFramesDropped) {
    Mutex::Autolock autoLock(mLock);
    mNumFramesTotal = numFramesTotal;
    mNumFramesDropped = numFramesDropped;
}

status_t NuPlayerDriver::dump(int fd, const Vector<String16> &args) const {
    Mutex::Autolock autoLock(mLock);

    FILE *out = fdopen(dup(fd), "w");

    fprintf(out, " NuPlayer\n");
    fprintf(out, "  numFramesTotal(%lld), numFramesDropped(%lld), "
                 "percentageDropped(%.2f)\n",
                 mNumFramesTotal,
                 mNumFramesDropped,
                 mNumFramesTotal == 0
                    ? 0.0 : (double)mNumFramesDropped / mNumFramesTotal);

    fclose(out);
    out = NULL;

    return OK;
}

void NuPlayerDriver::notifyListener(
        int msg, int ext1, int ext2, const Parcel *in) {
    if (msg == MEDIA_PLAYBACK_COMPLETE || msg == MEDIA_ERROR) {
        mAtEOS = true;
    }

    sendEvent(msg, ext1, ext2, in);
}

void NuPlayerDriver::notifySetDataSourceCompleted(status_t err) {
    Mutex::Autolock autoLock(mLock);

    CHECK_EQ(mState, STATE_SET_DATASOURCE_PENDING);

    mAsyncResult = err;
    mState = (err == OK) ? STATE_UNPREPARED : STATE_IDLE;
#ifndef ANDROID_DEFAULT_CODE
	ALOGD("after notifySetDataSourceCompleted mState=%d", mState);
#endif
    mCondition.broadcast();
}

void NuPlayerDriver::notifyPrepareCompleted(status_t err) {
    Mutex::Autolock autoLock(mLock);

    if (mState != STATE_PREPARING) {
        // We were preparing asynchronously when the client called
        // reset(), we sent a premature "prepared" notification and
        // then initiated the reset. This notification is stale.
        CHECK(mState == STATE_RESET_IN_PROGRESS || mState == STATE_IDLE);
        return;
    }

    CHECK_EQ(mState, STATE_PREPARING);

    mAsyncResult = err;

    if (err == OK) {
        if (mIsAsyncPrepare) {
            notifyListener(MEDIA_PREPARED);
        }
        mState = STATE_PREPARED;
    } else {
        if (mIsAsyncPrepare) {
#ifndef ANDROID_DEFAULT_CODE
            // ALPS00595180 - try to report a more meaningful error
            int ext1 = MEDIA_ERROR_UNKNOWN;
            switch(err) {
                case ERROR_MALFORMED:   // -1007
                    ext1 = MEDIA_ERROR_BAD_FILE;
                    break;
                case ERROR_CANNOT_CONNECT:  // -1003
                    ext1 = MEDIA_ERROR_CANNOT_CONNECT_TO_SERVER;
                    break;
                case ERROR_UNSUPPORTED: // -1010
                    ext1 = MEDIA_ERROR_TYPE_NOT_SUPPORTED;
                    break;
                case ERROR_FORBIDDEN:   // -1100
                    ext1 = MEDIA_ERROR_INVALID_CONNECTION;
                    break;
                default:
                    break;
            }
            notifyListener(MEDIA_ERROR, ext1, err);
#else
            notifyListener(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, err);
#endif
        }
        mState = STATE_UNPREPARED;
    }

    mCondition.broadcast();
}

void NuPlayerDriver::notifyFlagsChanged(uint32_t flags) {
    Mutex::Autolock autoLock(mLock);

    mPlayerFlags = flags;
}

}  // namespace android
