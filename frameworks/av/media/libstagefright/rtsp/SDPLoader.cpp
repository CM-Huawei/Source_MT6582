/*
 * Copyright (C) 2012 The Android Open Source Project
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
#define LOG_TAG "SDPLoader"
#include <utils/Log.h>

#include "SDPLoader.h"

#include "ASessionDescription.h"
#include "HTTPBase.h"

#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>

#define DEFAULT_SDP_SIZE 100000

namespace android {

SDPLoader::SDPLoader(const sp<AMessage> &notify, uint32_t flags, bool uidValid, uid_t uid)
    : mNotify(notify),
      mFlags(flags),
      mUIDValid(uidValid),
      mUID(uid),
      mNetLooper(new ALooper),
      mCancelled(false),
      mHTTPDataSource(
              HTTPBase::Create(
                  (mFlags & kFlagIncognito)
                    ? HTTPBase::kFlagIncognito
                    : 0)) {
    if (mUIDValid) {
        mHTTPDataSource->setUID(mUID);
    }

    mNetLooper->setName("sdp net");
    mNetLooper->start(false /* runOnCallingThread */,
                      false /* canCallJava */,
                      PRIORITY_HIGHEST);
}

void SDPLoader::load(const char *url, const KeyedVector<String8, String8> *headers) {
    mNetLooper->registerHandler(this);

    sp<AMessage> msg = new AMessage(kWhatLoad, id());
    msg->setString("url", url);

    if (headers != NULL) {
        msg->setPointer(
                "headers",
                new KeyedVector<String8, String8>(*headers));
    }

    msg->post();
}

void SDPLoader::cancel() {
    mCancelled = true;
    sp<HTTPBase> HTTPDataSource = mHTTPDataSource;
    HTTPDataSource->disconnect();
}

void SDPLoader::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatLoad:
            onLoad(msg);
            break;

        default:
            TRESPASS();
            break;
    }
}

void SDPLoader::onLoad(const sp<AMessage> &msg) {
    status_t err = OK;
    sp<ASessionDescription> desc = NULL;
    AString url;
    CHECK(msg->findString("url", &url));

    KeyedVector<String8, String8> *headers = NULL;
    msg->findPointer("headers", (void **)&headers);

    if (!(mFlags & kFlagIncognito)) {
        ALOGI("onLoad '%s'", url.c_str());
    } else {
        ALOGI("onLoad <URL suppressed>");
    }

    if (!mCancelled) {
        err = mHTTPDataSource->connect(url.c_str(), headers);

        if (err != OK) {
            ALOGE("connect() returned %d", err);
#ifndef ANDROID_DEFAULT_CODE
            if (err == ERROR_IO) {
                err = ERROR_CANNOT_CONNECT;
                ALOGE("reset err id from %d to %d for connect retry", ERROR_IO, ERROR_CANNOT_CONNECT);
            }	
#endif		
            
        }
    }

    if (headers != NULL) {
        delete headers;
        headers = NULL;
    }

    off64_t sdpSize;
    if (err == OK && !mCancelled) {
        err = mHTTPDataSource->getSize(&sdpSize);

        if (err != OK) {
            //We did not get the size of the sdp file, default to a large value
            sdpSize = DEFAULT_SDP_SIZE;
            err = OK;
        }
    }

    sp<ABuffer> buffer = new ABuffer(sdpSize);

    if (err == OK && !mCancelled) {
        ssize_t readSize = mHTTPDataSource->readAt(0, buffer->data(), sdpSize);

        if (readSize < 0) {
            ALOGE("Failed to read SDP, error code = %ld", readSize);
            err = UNKNOWN_ERROR;
        } else {
            desc = new ASessionDescription;

            if (desc == NULL || !desc->setTo(buffer->data(), (size_t)readSize)) {
                err = UNKNOWN_ERROR;
                ALOGE("Failed to parse SDP");
            }
        }
    }

    mHTTPDataSource.clear();

    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", kWhatSDPLoaded);
    notify->setInt32("result", err);
    notify->setObject("description", desc);
    notify->post();
}

}  // namespace android
