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
#define LOG_TAG "NuPlayerDecoder"
#include <utils/Log.h>

#include "NuPlayerDecoder.h"

#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/ACodec.h>
#include <media/stagefright/MediaDefs.h>

#include <media/stagefright/MediaErrors.h>

namespace android {

NuPlayer::Decoder::Decoder(
        const sp<AMessage> &notify,
        const sp<NativeWindowWrapper> &nativeWindow)
    : mNotify(notify),
#ifndef ANDROID_DEFAULT_CODE
      mFlushing(false),
    mFillBufferToNulGeneration(0),
    mFillBufferToNulPending(false),
    mNulCodecActivated(false),
#endif
      mNativeWindow(nativeWindow) {
}

NuPlayer::Decoder::~Decoder() {
#ifndef ANDROID_DEFAULT_CODE
    if (mCodecLooper !=NULL) {
        mCodecLooper->stop();
        mCodecLooper->unregisterHandler(mCodec->id());
    } else {
        looper()->unregisterHandler(mCodec->id());
    }
#endif
}

void NuPlayer::Decoder::configure(const sp<AMessage> &format) {
    CHECK(mCodec == NULL);

    AString mime;
    CHECK(format->findString("mime", &mime));
    ALOGD("config mime: %s", mime.c_str());

    sp<AMessage> notifyMsg =
        new AMessage(kWhatCodecNotify, id());

    mCSDIndex = 0;
    for (size_t i = 0;; ++i) {
        sp<ABuffer> csd;
        if (!format->findBuffer(StringPrintf("csd-%d", i).c_str(), &csd)) {
            break;
        }
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("@debug: %s received csd %d", mime.c_str(), i);
#endif
        mCSD.push(csd);
    }

    if (mNativeWindow != NULL) {
        format->setObject("native-window", mNativeWindow);
    }

    // Current video decoders do not return from OMX_FillThisBuffer
    // quickly, violating the OpenMAX specs, until that is remedied
    // we need to invest in an extra looper to free the main event
    // queue.
    bool needDedicatedLooper = !strncasecmp(mime.c_str(), "video/", 6);

    mCodec = new ACodec;

    if (needDedicatedLooper && mCodecLooper == NULL) {
        mCodecLooper = new ALooper;
        mCodecLooper->setName("NuPlayerDecoder");
        mCodecLooper->start(false, false, ANDROID_PRIORITY_AUDIO);
    }

    (needDedicatedLooper ? mCodecLooper : looper())->registerHandler(mCodec);

    mCodec->setNotificationMessage(notifyMsg);
    mCodec->initiateSetup(format);
}

void NuPlayer::Decoder::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatCodecNotify:
        {
            int32_t what;
            CHECK(msg->findInt32("what", &what));

            if (what == ACodec::kWhatFillThisBuffer) {
                onFillThisBuffer(msg);
            } else {
#ifndef ANDROID_DEFAULT_CODE
                if (what == ACodec::kWhatFlushCompleted) {
                    ALOGD("@debug: FlushCompleted.");
                    mFlushing = false;
                }
#endif
                sp<AMessage> notify = mNotify->dup();
                notify->setMessage("codec-request", msg);
                notify->post();
            }
            break;
        }
#ifndef ANDROID_DEFAULT_CODE
        case kWhatFillBufferToNul:
        {
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));
            if (generation != mFillBufferToNulGeneration) {
                break;
            }
            
            if (mFillBufferToNulPending)
                break;

            mFillBufferToNulPending = true;

            sp<AMessage> notifyMsg = new AMessage(kWhatCodecNotify, id());
            notifyMsg->setInt32("what", ACodec::kWhatFillThisBuffer);

            sp<AMessage> reply = new AMessage(kWhatNullCodec, id());
            reply->setInt32("generation", mFillBufferToNulGeneration);
            notifyMsg->setMessage("reply", reply);
            notifyMsg->post();
            break;
        }
        case kWhatNullCodec:
        {
            sp<ABuffer> buffer;
            int32_t err = OK;
            bool eos = false;
            int32_t tmp;
            mFillBufferToNulPending = false;

            if (!msg->findBuffer("buffer", &buffer)) {
                CHECK(msg->findInt32("err", &err));
                buffer.clear();
                eos = true;
            }

            ALOGD("buffer received in NullCodec (err = %d)", err);
            if (buffer != NULL && buffer->meta()->findInt32("eos", &tmp) && tmp) {
                eos = true;
                err = ERROR_END_OF_STREAM;
            }


            //eos, info to  nuplayer
            if ((eos) && (mCodec != NULL)) {
                mFillBufferToNulGeneration ++;
                
                sp<AMessage> notifyMsg = new AMessage(kWhatCodecNotify, id());
                notifyMsg->setInt32("what", ACodec::kWhatEOS);
                if (err != INFO_DISCONTINUITY)
                    notifyMsg->setInt32("err", err);
                else
                  	notifyMsg->setInt32("err", FAKE_INFO_DISCONTINUITY);
                notifyMsg->post();
                                    
                break;
            }

            if (err == INFO_DISCONTINUITY) {
                //discontinuity happens, real codec would handle it later
                mFillBufferToNulGeneration ++;
            } else {
                signalFillBufferToNul();
            }
            

            break;
        }
#endif

        default:
            TRESPASS();
            break;
    }
}

void NuPlayer::Decoder::onFillThisBuffer(const sp<AMessage> &msg) {
    sp<AMessage> reply;
    CHECK(msg->findMessage("reply", &reply));

#if 0
    sp<ABuffer> outBuffer;
    CHECK(msg->findBuffer("buffer", &outBuffer));
#else
    sp<ABuffer> outBuffer;
#endif

    if (mCSDIndex < mCSD.size()) {
#ifndef ANDROID_DEFAULT_CODE
        // mtk80902: solution for ALPS00448158
        // if flushing then just feed back
        // INFO_DISCONTINUITY as what NuPlayer does.
        ALOGD("@debug: fill csd %d, mFlushing %d", mCSDIndex, mFlushing);
        if (mFlushing) {
            reply->setInt32("err", -1013);  // MediaErrors.h not included..
            reply->post();
            return;
        }
#endif
        outBuffer = mCSD.editItemAt(mCSDIndex++);
        outBuffer->meta()->setInt64("timeUs", 0);

        reply->setBuffer("buffer", outBuffer);
        reply->post();
        return;
    }

    sp<AMessage> notify = mNotify->dup();
    notify->setMessage("codec-request", msg);
    notify->post();
}

void NuPlayer::Decoder::signalFlush() {
#ifndef ANDROID_DEFAULT_CODE
//TODO: this value need be protected by mutex
    mFillBufferToNulGeneration ++;
#endif
   if (mCodec != NULL) {
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("@debug: signalFlush!");
        mFlushing = true;
#endif
        mCodec->signalFlush();
    }
}

void NuPlayer::Decoder::signalResume() {
    if (mCodec != NULL) {
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("@debug: signalResume!");
        mFlushing = false;
#endif
        mCodec->signalResume();
    }
}

#ifndef ANDROID_DEFAULT_CODE
void NuPlayer::Decoder::initiateStart() {
    ALOGD("@debug: initiateStart");
    if (!mNulCodecActivated &&(mCodec != NULL)) {
        mCodec->initiateStart();
    }
}

void NuPlayer::Decoder::signalFillBufferToNul() {
    mNulCodecActivated = true;
    ALOGD("Nul Codec is activated");
    sp<AMessage> msg = new AMessage(kWhatFillBufferToNul, id());
    msg->setInt32("generation", mFillBufferToNulGeneration);
    msg->post();

}
#endif

void NuPlayer::Decoder::initiateShutdown() {
    if (mCodec != NULL) {
        mCodec->initiateShutdown();
    }
}

}  // namespace android

