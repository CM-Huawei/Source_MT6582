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

#ifdef MTK_SUBTITLE_SUPPORT

//#define LOG_NDEBUG 0
#define LOG_TAG "TimedTextVOBSUBSource"
#include <utils/Log.h>

#include <binder/Parcel.h>
#include <media/stagefright/foundation/ADebug.h>  // CHECK_XX macro
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDefs.h>  // for MEDIA_MIMETYPE_xxx
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>

#include "TimedTextVOBSUBSource.h"
#include "TextDescriptions.h"

#define FILE_BUFFER_SIZE (1024 * 768)

namespace android
{

TimedTextVOBSUBSource::TimedTextVOBSUBSource(const sp<MediaSource>& mediaSource)
    : mSource(mediaSource)
{
    mSubParser = NULL;
}

TimedTextVOBSUBSource::~TimedTextVOBSUBSource()
{
}

status_t TimedTextVOBSUBSource::read(
    int64_t *startTimeUs, int64_t *endTimeUs, Parcel *parcel,
    const MediaSource::ReadOptions *options)
{
    MediaBuffer *textBuffer = NULL;

    status_t err = mSource->read(&textBuffer, options);
    if (err != OK)
    {
        ALOGE("mSource->read() failed, error code %d\n", err);
        return err;
    }

    CHECK(textBuffer != NULL);
    textBuffer->meta_data()->findInt64(kKeyTime, startTimeUs);

    char * content = (char *)textBuffer->data();
    size_t size = textBuffer->size();

    CHECK_GE(*startTimeUs, 0);

    mSubParser->stInit(content, size);

    do
    {
        err = mSubParser->stParseControlPacket();

        if (err != OK)
            break;

        if (mSubParser->m_iDataPacketSize <= 4)
            break;

        if (err != OK)
            break;


        err = mSubParser->stParseDataPacket(NULL, 0);
        if (err != OK)
            break;

        //*startTimeUs = (int64_t)(mSubParser->m_iBeginTime);
        ALOGE("Call extractAndAppendLocalDescriptions, send data to \n");
        extractAndAppendLocalDescriptions(*startTimeUs, textBuffer, parcel);

    }
    while (false);

    textBuffer->release();
    *endTimeUs = -1;
    ALOGE("read() finished\n");
    return OK;
}

// Each text sample consists of a string of text, optionally with sample
// modifier description. The modifier description could specify a new
// text style for the string of text. These descriptions are present only
// if they are needed. This method is used to extract the modifier
// description and append it at the end of the text.
status_t TimedTextVOBSUBSource::extractAndAppendLocalDescriptions(
    int64_t timeUs, const MediaBuffer *textBuffer, Parcel *parcel)
{
    const void *data;
    size_t size = 0;

    const char *mime;
    CHECK(mSource->getFormat()->findCString(kKeyMIMEType, &mime));
    CHECK(strcasecmp(mime, MEDIA_MIMETYPE_TEXT_VOBSUB) == 0);

    data = textBuffer->data();
    size = textBuffer->size();
    int fd = mSubParser->m_iFd;
    int timeMs = (mSubParser->m_iBeginTime)/1000;
    int width = mSubParser->m_iBitmapWidth;
    int height = mSubParser->m_iBitmapHeight;

    int flag = TextDescriptions::LOCAL_DESCRIPTIONS |
               TextDescriptions::IN_BAND_TEXT_VOBSUB;


    if (size > 0)
    {
        parcel->freeData();
        flag |= TextDescriptions::IN_BAND_TEXT_VOBSUB;
        return TextDescriptions::getParcelOfDescriptions(
                   fd, width, height, flag, timeMs, parcel);
    }

    return OK;
}

// To extract and send the global text descriptions for all the text samples
// in the text track or text file.
// TODO: send error message to application via notifyListener()...?
status_t TimedTextVOBSUBSource::extractGlobalDescriptions(Parcel *parcel)
{
    const void *data;
    size_t size = 0;
    int32_t flag = TextDescriptions::GLOBAL_DESCRIPTIONS;


    ALOGE("[RY] here TimedTextVOBSUBSource::extractGlobalDescriptions() called\n");

    const char *mime;
    CHECK(mSource->getFormat()->findCString(kKeyMIMEType, &mime));
    CHECK(strcasecmp(mime, MEDIA_MIMETYPE_TEXT_VOBSUB) == 0);

    uint32_t type;
    // get the 'tx3g' box content. This box contains the text descriptions
    // used to render the text track
    if (!mSource->getFormat()->findData(
                kKeyTextFormatData, &type, &data, &size))
    {
        ALOGE("[RY] Can't find kKeyTextFormatData, return ERROR_MALFORMED\n");
        return ERROR_MALFORMED;
    }

    if (size > 0)
    {
        flag |= TextDescriptions::IN_BAND_TEXT_VOBSUB;
        return TextDescriptions::getParcelOfDescriptions(
                   (const uint8_t *)data, size, flag, 0, parcel);
    }
    return OK;
}

sp<MetaData> TimedTextVOBSUBSource::getFormat()
{
    ALOGE("[RY] here TimedTextVOBSUBSource::getFormat() called\n");
    return mSource->getFormat();
}

status_t TimedTextVOBSUBSource::start()
{
    ALOGE("[RY] here TimedTextVOBSUBSource::start() called\n");
    if (mSubParser != NULL)
    {
        delete mSubParser;
        mSubParser = NULL;
    }
    mSubParser = new VOBSubtitleParser();
    mSubParser->stPrepareBitmapBuffer();
    return mSource->start();
}

status_t TimedTextVOBSUBSource::stop()
{
    ALOGE("[RY] here TimedTextVOBSUBSource::stop() called\n");

    if (mSubParser != NULL)
    {
        mSubParser->stUnmapBitmapBuffer();
        mSubParser->vUnInit();
        delete mSubParser;
        mSubParser = NULL;
    }
    return mSource->stop();
}
}  // namespace android

#endif

