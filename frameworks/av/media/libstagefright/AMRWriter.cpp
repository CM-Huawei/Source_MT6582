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

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/AMRWriter.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/mediarecorder.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#ifndef ANDROID_DEFAULT_CODE
#include <linux/rtpm_prio.h>

#define LOG_TAG "AMRWriter"
#define AMRWRITER_USE_XLOG
#ifdef AMRWRITER_USE_XLOG
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

AMRWriter::AMRWriter(const char *filename)
    : mFd(-1),
      mInitCheck(NO_INIT),
      mStarted(false),
      mPaused(false),
      mResumed(false) {

    mFd = open(filename, O_CREAT | O_LARGEFILE | O_TRUNC | O_RDWR, S_IRUSR | S_IWUSR);
    if (mFd >= 0) {
        mInitCheck = OK;
    }
#ifndef  ANDROID_DEFAULT_CODE
ALOGI("AMRWriter Constructor,file ( %s )(%d)",filename,mFd);
#endif

}

AMRWriter::AMRWriter(int fd)
    : mFd(dup(fd)),
      mInitCheck(mFd < 0? NO_INIT: OK),
      mStarted(false),
      mPaused(false),
      mResumed(false) {
#ifndef ANDROID_DEFAULT_CODE
ALOGI("AMRWriter Constructor,file fd(%d)",mFd);
#endif
}

AMRWriter::~AMRWriter() {
#ifndef ANDROID_DEFAULT_CODE
ALOGI("~AMRWriter destructor");
#endif
    if (mStarted) {
        reset();
    }

    if (mFd != -1) {
        close(mFd);
        mFd = -1;
    }
}

status_t AMRWriter::initCheck() const {
    return mInitCheck;
}

status_t AMRWriter::addSource(const sp<MediaSource> &source) {
    if (mInitCheck != OK) {
        return mInitCheck;
    }

    if (mSource != NULL) {
        // AMR files only support a single track of audio.
        return UNKNOWN_ERROR;
    }

    sp<MetaData> meta = source->getFormat();

    const char *mime;
    CHECK(meta->findCString(kKeyMIMEType, &mime));

    bool isWide = false;
    if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AMR_WB)) {
        isWide = true;
    } else if (strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AMR_NB)) {
        return ERROR_UNSUPPORTED;
    }

    int32_t channelCount;
    int32_t sampleRate;
    CHECK(meta->findInt32(kKeyChannelCount, &channelCount));
    CHECK_EQ(channelCount, 1);
    CHECK(meta->findInt32(kKeySampleRate, &sampleRate));
    CHECK_EQ(sampleRate, (isWide ? 16000 : 8000));
#ifndef ANDROID_DEFAULT_CODE
ALOGI("addSource,channelCount=%d,sampleRate=%d",channelCount,sampleRate);
#endif
    mSource = source;

    const char *kHeader = isWide ? "#!AMR-WB\n" : "#!AMR\n";
    ssize_t n = strlen(kHeader);
    if (write(mFd, kHeader, n) != n) {
        return ERROR_IO;
    }

    return OK;
}

status_t AMRWriter::start(MetaData *params) {
    if (mInitCheck != OK) {
        return mInitCheck;
    }

    if (mSource == NULL) {
        return UNKNOWN_ERROR;
    }

    if (mStarted && mPaused) {
        mPaused = false;
        mResumed = true;
        return OK;
    } else if (mStarted) {
        // Already started, does nothing
        return OK;
    }
#ifndef ANDROID_DEFAULT_CODE
ALOGI("start,call source start+++");
#endif
    status_t err = mSource->start();
#ifndef ANDROID_DEFAULT_CODE
ALOGI("start,call source start---,err = %d",err);
#endif
    if (err != OK) {
        return err;
    }

    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

    mReachedEOS = false;
    mDone = false;

    pthread_create(&mThread, &attr, ThreadWrapper, this);
    pthread_attr_destroy(&attr);

    mStarted = true;

    return OK;
}

status_t AMRWriter::pause() {
#ifndef ANDROID_DEFAULT_CODE
//no lock protected
ALOGI("pause");
#endif
    if (!mStarted) {
        return OK;
    }
    mPaused = true;
    return OK;
}

status_t AMRWriter::reset() {
#ifndef ANDROID_DEFAULT_CODE
ALOGI("reset");
#endif
    if (!mStarted) {
        return OK;
    }

    mDone = true;

    void *dummy;
    pthread_join(mThread, &dummy);

    status_t err = (status_t) dummy;
#ifndef ANDROID_DEFAULT_CODE
ALOGD("reset,writer thread exit,return %d",err);
#endif
    {
        status_t status = mSource->stop();
        if (err == OK &&
            (status != OK && status != ERROR_END_OF_STREAM)) {
            err = status;
        }
#ifndef ANDROID_DEFAULT_CODE
ALOGI("reset,call source stop---,status=%d",status);
#endif
    }

    mStarted = false;
    return err;
}

bool AMRWriter::exceedsFileSizeLimit() {
    if (mMaxFileSizeLimitBytes == 0) {
        return false;
    }
    return mEstimatedSizeBytes >= mMaxFileSizeLimitBytes;
}

bool AMRWriter::exceedsFileDurationLimit() {
    if (mMaxFileDurationLimitUs == 0) {
        return false;
    }
    return mEstimatedDurationUs >= mMaxFileDurationLimitUs;
}

// static
void *AMRWriter::ThreadWrapper(void *me) {
    return (void *) static_cast<AMRWriter *>(me)->threadFunc();
}

status_t AMRWriter::threadFunc() {
    mEstimatedDurationUs = 0;
    mEstimatedSizeBytes = 0;
    bool stoppedPrematurely = true;
    int64_t previousPausedDurationUs = 0;
    int64_t maxTimestampUs = 0;
    status_t err = OK;

    prctl(PR_SET_NAME, (unsigned long)"AMRWriter", 0, 0, 0);
#ifndef ANDROID_DEFAULT_CODE
androidSetThreadPriority(0, ANDROID_PRIORITY_AUDIO);
while (!mDone || mEstimatedSizeBytes == 0) {
#else
    while (!mDone) {
#endif
        MediaBuffer *buffer;
        err = mSource->read(&buffer);

        if (err != OK) {
            break;
        }

        if (mPaused) {
            buffer->release();
            buffer = NULL;
#ifndef ANDROID_DEFAULT_CODE
	    if (stoppedPrematurely) {
	    ALOGD("pause set stoppedPrematurely false");
            stoppedPrematurely = false;
            }
            if (mDone)
            {
                ALOGD("in pause state but stop,mEstimatedSizeBytes=%lld",mEstimatedSizeBytes);
                break;
            }
#endif
            continue;
        }

        mEstimatedSizeBytes += buffer->range_length();
        if (exceedsFileSizeLimit()) {
            buffer->release();
            buffer = NULL;
#ifndef ANDROID_DEFAULT_CODE
ALOGW("reach max file size limit,mMaxFileSizeLimitBytes=%lld",mMaxFileSizeLimitBytes);
#endif
            notify(MEDIA_RECORDER_EVENT_INFO, MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED, 0);
            break;
        }

        int64_t timestampUs;
        CHECK(buffer->meta_data()->findInt64(kKeyTime, &timestampUs));
        if (timestampUs > mEstimatedDurationUs) {
            mEstimatedDurationUs = timestampUs;
        }
        if (mResumed) {
            previousPausedDurationUs += (timestampUs - maxTimestampUs - 20000);
            mResumed = false;
        }
        timestampUs -= previousPausedDurationUs;
        ALOGV("time stamp: %lld, previous paused duration: %lld",
                timestampUs, previousPausedDurationUs);
        if (timestampUs > maxTimestampUs) {
            maxTimestampUs = timestampUs;
        }

        if (exceedsFileDurationLimit()) {
            buffer->release();
            buffer = NULL;
#ifndef ANDROID_DEFAULT_CODE
ALOGW("reach max file duration limit,mMaxFileDurationLimitUs=%lld",mMaxFileDurationLimitUs);
#endif
            notify(MEDIA_RECORDER_EVENT_INFO, MEDIA_RECORDER_INFO_MAX_DURATION_REACHED, 0);
            break;
        }
        ssize_t n = write(mFd,
                        (const uint8_t *)buffer->data() + buffer->range_offset(),
                        buffer->range_length());

        if (n < (ssize_t)buffer->range_length()) {
            buffer->release();
            buffer = NULL;
            err = ERROR_IO;
            break;
        }

        if (err != OK) {
            break;
        }

        if (stoppedPrematurely) {
            stoppedPrematurely = false;
        }

        buffer->release();
        buffer = NULL;
    }

    if ((err == OK || err == ERROR_END_OF_STREAM) && stoppedPrematurely) {
#ifndef ANDROID_DEFAULT_CODE
ALOGE("threadFunc,no frame writen to file");
#endif
        err = ERROR_MALFORMED;
    }

    close(mFd);
    mFd = -1;
    mReachedEOS = true;
    if (err == ERROR_END_OF_STREAM) {
        return OK;
    }
    return err;
}

bool AMRWriter::reachedEOS() {
    return mReachedEOS;
}

}  // namespace android
