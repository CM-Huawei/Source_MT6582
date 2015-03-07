/*
 * Copyright (C) 2011 The Android Open Source Project
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
#define LOG_TAG "SurfaceMediaSource"

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/SurfaceMediaSource.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MetaData.h>
#include <OMX_IVCommon.h>
#include <media/hardware/MetadataBufferType.h>

#include <ui/GraphicBuffer.h>
#include <gui/ISurfaceComposer.h>
#include <gui/IGraphicBufferAlloc.h>
#include <OMX_Component.h>

#include <utils/Log.h>
#include <utils/String8.h>

#include <private/gui/ComposerService.h>

#ifndef ANDROID_DEFAULT_CODE
#include <cutils/properties.h>
#include <SkImageEncoder.h>
#include <SkBitmap.h>

#define ATRACE_TAG ATRACE_TAG_GRAPHICS
#include <utils/Trace.h>

#define SURFACEMEDIASOURCE_USE_XLOG
#ifdef SURFACEMEDIASOURCE_USE_XLOG
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
#define ALOGV XLOGD
#endif

#endif

namespace android {

SurfaceMediaSource::SurfaceMediaSource(uint32_t bufferWidth, uint32_t bufferHeight) :
    mWidth(bufferWidth),
    mHeight(bufferHeight),
    mCurrentSlot(BufferQueue::INVALID_BUFFER_SLOT),
    mNumPendingBuffers(0),
    mCurrentTimestamp(0),
    mFrameRate(30),
    mStarted(false),
    mNumFramesReceived(0),
    mNumFramesEncoded(0),
    mFirstFrameTimestamp(0),
    mMaxAcquiredBufferCount(4),  // XXX double-check the default
    mUseAbsoluteTimestamps(false) {
    ALOGV("SurfaceMediaSource");

#ifndef ANDROID_DEFAULT_CODE
    mBackupBufsIndex = 0;
    mBackupBufsMax = 0;
    
    mIsBinderDied = false;
#endif

    if (bufferWidth == 0 || bufferHeight == 0) {
        ALOGE("Invalid dimensions %dx%d", bufferWidth, bufferHeight);
    }

    mBufferQueue = new BufferQueue();
    mBufferQueue->setDefaultBufferSize(bufferWidth, bufferHeight);
    mBufferQueue->setConsumerUsageBits(GRALLOC_USAGE_HW_VIDEO_ENCODER |
            GRALLOC_USAGE_HW_TEXTURE);

    sp<ISurfaceComposer> composer(ComposerService::getComposerService());

    // Note that we can't create an sp<...>(this) in a ctor that will not keep a
    // reference once the ctor ends, as that would cause the refcount of 'this'
    // dropping to 0 at the end of the ctor.  Since all we need is a wp<...>
    // that's what we create.
    wp<ConsumerListener> listener = static_cast<ConsumerListener*>(this);
    sp<BufferQueue::ProxyConsumerListener> proxy = new BufferQueue::ProxyConsumerListener(listener);

    status_t err = mBufferQueue->consumerConnect(proxy, false);
    if (err != NO_ERROR) {
        ALOGE("SurfaceMediaSource: error connecting to BufferQueue: %s (%d)",
                strerror(-err), err);
    }
}

SurfaceMediaSource::~SurfaceMediaSource() {
    ALOGV("~SurfaceMediaSource");
    CHECK(!mStarted);
}

nsecs_t SurfaceMediaSource::getTimestamp() {
    ALOGV("getTimestamp");
    Mutex::Autolock lock(mMutex);
    return mCurrentTimestamp;
}

void SurfaceMediaSource::setFrameAvailableListener(
        const sp<FrameAvailableListener>& listener) {
    ALOGV("setFrameAvailableListener");
    Mutex::Autolock lock(mMutex);
    mFrameAvailableListener = listener;
}

void SurfaceMediaSource::dump(String8& result) const
{
    char buffer[1024];
    dump(result, "", buffer, 1024);
}

void SurfaceMediaSource::dump(String8& result, const char* prefix,
        char* buffer, size_t SIZE) const
{
    Mutex::Autolock lock(mMutex);

    result.append(buffer);
    mBufferQueue->dump(result, "");
}

status_t SurfaceMediaSource::setFrameRate(int32_t fps)
{
    ALOGV("setFrameRate");
    Mutex::Autolock lock(mMutex);
    const int MAX_FRAME_RATE = 60;
    if (fps < 0 || fps > MAX_FRAME_RATE) {
        return BAD_VALUE;
    }
    mFrameRate = fps;
    return OK;
}

bool SurfaceMediaSource::isMetaDataStoredInVideoBuffers() const {
    ALOGV("isMetaDataStoredInVideoBuffers");
    return true;
}

int32_t SurfaceMediaSource::getFrameRate( ) const {
    ALOGV("getFrameRate");
    Mutex::Autolock lock(mMutex);
    return mFrameRate;
}

status_t SurfaceMediaSource::start(MetaData *params)
{
    ALOGV("start");

    Mutex::Autolock lock(mMutex);

    CHECK(!mStarted);

    mStartTimeNs = 0;
    int64_t startTimeUs;
    int32_t bufferCount = 0;
    if (params) {
        if (params->findInt64(kKeyTime, &startTimeUs)) {
            mStartTimeNs = startTimeUs * 1000;
        }

        if (!params->findInt32(kKeyNumBuffers, &bufferCount)) {
            ALOGE("Failed to find the advertised buffer count");
            return UNKNOWN_ERROR;
        }

        if (bufferCount <= 1) {
            ALOGE("bufferCount %d is too small", bufferCount);
            return BAD_VALUE;
        }

        mMaxAcquiredBufferCount = bufferCount;
    }

    CHECK_GT(mMaxAcquiredBufferCount, 1);

    status_t err =
        mBufferQueue->setMaxAcquiredBufferCount(mMaxAcquiredBufferCount);

    if (err != OK) {
        return err;
    }

    mNumPendingBuffers = 0;
    mStarted = true;

    return OK;
}

status_t SurfaceMediaSource::setMaxAcquiredBufferCount(size_t count) {
    ALOGV("setMaxAcquiredBufferCount(%d)", count);
    Mutex::Autolock lock(mMutex);

    CHECK_GT(count, 1);
    mMaxAcquiredBufferCount = count;

    return OK;
}

status_t SurfaceMediaSource::setUseAbsoluteTimestamps() {
    ALOGV("setUseAbsoluteTimestamps");
    Mutex::Autolock lock(mMutex);
    mUseAbsoluteTimestamps = true;

    return OK;
}

status_t SurfaceMediaSource::stop()
{
#ifndef ANDROID_DEFAULT_CODE
    ATRACE_CALL();
#endif
    ALOGV("stop");
    Mutex::Autolock lock(mMutex);

    if (!mStarted) {
        return OK;
    }

    while (mNumPendingBuffers > 0) {
        ALOGI("Still waiting for %d buffers to be returned.",
                mNumPendingBuffers);

#if DEBUG_PENDING_BUFFERS
        for (size_t i = 0; i < mPendingBuffers.size(); ++i) {
            ALOGI("%d: %p", i, mPendingBuffers.itemAt(i));
        }
#endif

        mMediaBuffersAvailableCondition.wait(mMutex);
    }

    mStarted = false;
    mFrameAvailableCondition.signal();
    mMediaBuffersAvailableCondition.signal();

    return mBufferQueue->consumerDisconnect();
}

sp<MetaData> SurfaceMediaSource::getFormat()
{
    ALOGV("getFormat");

    Mutex::Autolock lock(mMutex);
    sp<MetaData> meta = new MetaData;

    meta->setInt32(kKeyWidth, mWidth);
    meta->setInt32(kKeyHeight, mHeight);
    // The encoder format is set as an opaque colorformat
    // The encoder will later find out the actual colorformat
    // from the GL Frames itself.
    meta->setInt32(kKeyColorFormat, OMX_COLOR_FormatAndroidOpaque);
    meta->setInt32(kKeyStride, mWidth);
    meta->setInt32(kKeySliceHeight, mHeight);
    meta->setInt32(kKeyFrameRate, mFrameRate);
    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_RAW);
    return meta;
}

// Pass the data to the MediaBuffer. Pass in only the metadata
// The metadata passed consists of two parts:
// 1. First, there is an integer indicating that it is a GRAlloc
// source (kMetadataBufferTypeGrallocSource)
// 2. This is followed by the buffer_handle_t that is a handle to the
// GRalloc buffer. The encoder needs to interpret this GRalloc handle
// and encode the frames.
// --------------------------------------------------------------
// |  kMetadataBufferTypeGrallocSource | sizeof(buffer_handle_t) |
// --------------------------------------------------------------
// Note: Call only when you have the lock
static void passMetadataBuffer(MediaBuffer **buffer,
        buffer_handle_t bufferHandle) {
    *buffer = new MediaBuffer(4 + sizeof(buffer_handle_t));
    char *data = (char *)(*buffer)->data();
    if (data == NULL) {
        ALOGE("Cannot allocate memory for metadata buffer!");
        return;
    }
    OMX_U32 type = kMetadataBufferTypeGrallocSource;
    memcpy(data, &type, 4);
    memcpy(data + 4, &bufferHandle, sizeof(buffer_handle_t));

    ALOGV("handle = %p, , offset = %d, length = %d",
            bufferHandle, (*buffer)->range_length(), (*buffer)->range_offset());
}

status_t SurfaceMediaSource::read( MediaBuffer **buffer,
                                    const ReadOptions *options)
{
#ifndef ANDROID_DEFAULT_CODE
    ATRACE_CALL();

    sp<Fence> bufferFence = NULL;
    {
#endif 

    ALOGV("read");
    Mutex::Autolock lock(mMutex);

    *buffer = NULL;

    while (mStarted && mNumPendingBuffers == mMaxAcquiredBufferCount) {
#ifndef ANDROID_DEFAULT_CODE
		ALOGD("read,waiting mMediaBuffersAvailableCondition!");
#endif
        mMediaBuffersAvailableCondition.wait(mMutex);
    }

    // Update the current buffer info
    // TODO: mCurrentSlot can be made a bufferstate since there
    // can be more than one "current" slots.

    BufferQueue::BufferItem item;
    // If the recording has started and the queue is empty, then just
    // wait here till the frames come in from the client side
    while (mStarted) {

        status_t err = mBufferQueue->acquireBuffer(&item, 0);
        if (err == BufferQueue::NO_BUFFER_AVAILABLE) {
            // wait for a buffer to be queued
#ifndef ANDROID_DEFAULT_CODE
#if 0
            //mark for WFD case
            if (mBufferQueue->getConnectedApi() == BufferQueue::NO_CONNECTED_API)
        	{
                ALOGD("read,no connected api!");
                mStarted = false;

                break;
            }
            else
#endif
            if (mIsBinderDied)
            {
                ALOGD("read, binder is died");
                return ERROR_END_OF_STREAM;
            }
            
			ALOGD("read, no buffer available!");
#endif 
            mFrameAvailableCondition.wait(mMutex);
        } else if (err == OK) {
#ifndef ANDROID_DEFAULT_CODE
            bufferFence = item.mFence;			
#endif
            // First time seeing the buffer?  Added it to the SMS slot
            if (item.mGraphicBuffer != NULL) {
                mSlots[item.mBuf].mGraphicBuffer = item.mGraphicBuffer;
            }
            mSlots[item.mBuf].mFrameNumber = item.mFrameNumber;

            // check for the timing of this buffer
            if (mNumFramesReceived == 0 && !mUseAbsoluteTimestamps) {
                mFirstFrameTimestamp = item.mTimestamp;
                // Initial delay
                if (mStartTimeNs > 0) {
                    if (item.mTimestamp < mStartTimeNs) {
                        // This frame predates start of record, discard
                        mBufferQueue->releaseBuffer(
                                item.mBuf, item.mFrameNumber, EGL_NO_DISPLAY,
                                EGL_NO_SYNC_KHR, Fence::NO_FENCE);
                        continue;
                    }
                    mStartTimeNs = item.mTimestamp - mStartTimeNs;
                }
            }
            item.mTimestamp = mStartTimeNs + (item.mTimestamp - mFirstFrameTimestamp);

            mNumFramesReceived++;

            break;
        } else {
            ALOGE("read: acquire failed with error code %d", err);
            return ERROR_END_OF_STREAM;
        }

    }

    // If the loop was exited as a result of stopping the recording,
    // it is OK
    if (!mStarted) {
        ALOGV("Read: SurfaceMediaSource is stopped. Returning ERROR_END_OF_STREAM.");
        return ERROR_END_OF_STREAM;
    }

    mCurrentSlot = item.mBuf;

    // First time seeing the buffer?  Added it to the SMS slot
    if (item.mGraphicBuffer != NULL) {
        mSlots[item.mBuf].mGraphicBuffer = item.mGraphicBuffer;
    }
    mSlots[item.mBuf].mFrameNumber = item.mFrameNumber;

    mCurrentBuffers.push_back(mSlots[mCurrentSlot].mGraphicBuffer);
    int64_t prevTimeStamp = mCurrentTimestamp;
    mCurrentTimestamp = item.mTimestamp;

    mNumFramesEncoded++;
    // Pass the data to the MediaBuffer. Pass in only the metadata

    passMetadataBuffer(buffer, mSlots[mCurrentSlot].mGraphicBuffer->handle);

    (*buffer)->setObserver(this);
    (*buffer)->add_ref();
    (*buffer)->meta_data()->setInt64(kKeyTime, mCurrentTimestamp / 1000);
    ALOGV("Frames encoded = %d, timestamp = %lld, time diff = %lld",
            mNumFramesEncoded, mCurrentTimestamp / 1000,
            mCurrentTimestamp / 1000 - prevTimeStamp / 1000);

    ++mNumPendingBuffers;

#if DEBUG_PENDING_BUFFERS
    mPendingBuffers.push_back(*buffer);
#endif

#ifdef ANDROID_DEFAULT_CODE
    ALOGV("returning mbuf %p", *buffer);
#endif

#ifndef ANDROID_DEFAULT_CODE
    }

    if (bufferFence != NULL) {
        ATRACE_NAME("SMS wait buffer fence");
        bufferFence->waitForever("SMS aquire buffer");
    }

    dumpBuffer();
#endif

    return OK;
}

static buffer_handle_t getMediaBufferHandle(MediaBuffer *buffer) {
    // need to convert to char* for pointer arithmetic and then
    // copy the byte stream into our handle
    buffer_handle_t bufferHandle;
    memcpy(&bufferHandle, (char*)(buffer->data()) + 4, sizeof(buffer_handle_t));
    return bufferHandle;
}

void SurfaceMediaSource::signalBufferReturned(MediaBuffer *buffer) {
    ALOGV("signalBufferReturned");

    bool foundBuffer = false;

    Mutex::Autolock lock(mMutex);

    buffer_handle_t bufferHandle = getMediaBufferHandle(buffer);

    for (size_t i = 0; i < mCurrentBuffers.size(); i++) {
        if (mCurrentBuffers[i]->handle == bufferHandle) {
            mCurrentBuffers.removeAt(i);
            foundBuffer = true;
            break;
        }
    }

    if (!foundBuffer) {
        ALOGW("returned buffer was not found in the current buffer list");
    }

    for (int id = 0; id < BufferQueue::NUM_BUFFER_SLOTS; id++) {
        if (mSlots[id].mGraphicBuffer == NULL) {
            continue;
        }

        if (bufferHandle == mSlots[id].mGraphicBuffer->handle) {
            ALOGV("Slot %d returned, matches handle = %p", id,
                    mSlots[id].mGraphicBuffer->handle);

            mBufferQueue->releaseBuffer(id, mSlots[id].mFrameNumber,
                                        EGL_NO_DISPLAY, EGL_NO_SYNC_KHR,
                    Fence::NO_FENCE);

            buffer->setObserver(0);
            buffer->release();

            foundBuffer = true;
            break;
        }
    }

    if (!foundBuffer) {
        CHECK(!"signalBufferReturned: bogus buffer");
    }

#if DEBUG_PENDING_BUFFERS
    for (size_t i = 0; i < mPendingBuffers.size(); ++i) {
        if (mPendingBuffers.itemAt(i) == buffer) {
            mPendingBuffers.removeAt(i);
            break;
        }
    }
#endif

    --mNumPendingBuffers;
    mMediaBuffersAvailableCondition.broadcast();
}

// Part of the BufferQueue::ConsumerListener
void SurfaceMediaSource::onFrameAvailable() {
    ALOGV("onFrameAvailable");

    sp<FrameAvailableListener> listener;
    { // scope for the lock
        Mutex::Autolock lock(mMutex);
        mFrameAvailableCondition.broadcast();
        listener = mFrameAvailableListener;
    }

    if (listener != NULL) {
        ALOGV("actually calling onFrameAvailable");
        listener->onFrameAvailable();
    }
}

// SurfaceMediaSource hijacks this event to assume
// the prodcuer is disconnecting from the BufferQueue
// and that it should stop the recording
void SurfaceMediaSource::onBuffersReleased() {
#ifndef ANDROID_DEFAULT_CODE
    ATRACE_CALL();
#endif
    ALOGV("onBuffersReleased, mNumFramesReceived=%d, connectedapi=%d", mNumFramesReceived, mBufferQueue->getConnectedApi());

    Mutex::Autolock lock(mMutex);
	
#ifndef ANDROID_DEFAULT_CODE
    //mark for WFD case
    if (mBufferQueue->getConnectedApi() == BufferQueue::NO_CONNECTED_API)
	    mStarted = false;
#endif
    mFrameAvailableCondition.signal();

    for (int i = 0; i < BufferQueue::NUM_BUFFER_SLOTS; i++) {
       mSlots[i].mGraphicBuffer = 0;
    }
}

#ifndef ANDROID_DEFAULT_CODE
status_t SurfaceMediaSource::binderDied()
{
    if (!mStarted) {
        return OK;
    }

    XLOGW("[SMS] binder is dead, broadcast");
    mIsBinderDied = true;    
    mFrameAvailableCondition.broadcast();

    return OK;
}

void SurfaceMediaSource::drainBufferQueue()
{
    status_t err = OK;
    BufferQueue::BufferItem item;
    
    while (err == OK) {
//        mBufferQueue->acquireBuffer(&item);
//        mBufferQueue->releaseBuffer(item.mBuf, EGL_NO_DISPLAY, EGL_NO_SYNC_KHR, Fence::NO_FENCE);
    }
}

static uint32_t single_count = 0;

void SurfaceMediaSource::dumpBuffer(){
    char     value[PROPERTY_VALUE_MAX];
    property_get("debug.sms.layerdump", value, "0");

    int param = atoi(value);

    if (param == 1)
        single_count ++;
    else
        single_count = 0;

    switch (param)
    {
    case -1:
        system("mkdir /data/SMS_dump");
        activeBufferBackup();
        break;

    case 0:
        dumpContinuousBuffer();
        break;

    case 1:
        system("mkdir /data/SMS_dump");
        dumpSingleBuffer();
        break;

    default:
        return;
    }
}

void SurfaceMediaSource::dumpSingleBuffer()
{
    sp<GraphicBuffer> graphicBuffer = mSlots[mCurrentSlot].mGraphicBuffer;
    if (graphicBuffer != NULL) {
        bool     raw = false;
        uint32_t identity;

        char             fname[128];
        void*            ptr;
        float            bpp;
        SkBitmap         b;
        SkBitmap::Config c;

        bpp = 1.0f;
        c = SkBitmap::kNo_Config;
        switch (graphicBuffer->format) {
            case PIXEL_FORMAT_RGBA_8888:
            case PIXEL_FORMAT_RGBX_8888:
                if (false == raw) {
                    c = SkBitmap::kARGB_8888_Config;
                    sprintf(fname, "/data/SMS_dump/sms_%03d.png", identity);
                } else {
                    bpp = 4.0;
                    sprintf(fname, "/data/SMS_dump/sms_%03d.RGBA", identity);
                }
                break;
            case PIXEL_FORMAT_BGRA_8888:
            case 0x1ff:                     // tricky format for SGX_COLOR_FORMAT_BGRX_8888 in fact
                if (false == raw) {
                    c = SkBitmap::kARGB_8888_Config;
                    sprintf(fname, "/data/SMS_dump/sms_%03d(RBswapped).png", identity);
                } else {
                    bpp = 4.0;
                    sprintf(fname, "/data/SMS_dump/sms_%03d.BGRA", identity);
                }
                break;
            case PIXEL_FORMAT_RGB_565:
                if (false == raw) {
                    c = SkBitmap::kRGB_565_Config;
                    sprintf(fname, "/data/SMS_dump/sms_%03d.png", identity);
                } else {
                    bpp = 2.0;
                    sprintf(fname, "/data/SMS_dump/sms_%03d.RGB565", identity);
                }
                break;
            case HAL_PIXEL_FORMAT_I420:
                bpp = 1.5;
                sprintf(fname, "/data/SMS_dump/sms_%03d.i420", identity);
                break;
            case HAL_PIXEL_FORMAT_NV12_BLK:
                bpp = 1.5;
                sprintf(fname, "/data/SMS_dump/sms_%03d.nv12_blk", identity);
                break;
            case HAL_PIXEL_FORMAT_NV12_BLK_FCM:
                bpp = 1.5;
                sprintf(fname, "/data/SMS_dump/sms_%03d.nv12_blk_fcm", identity);
                break;
            case HAL_PIXEL_FORMAT_YV12:
                bpp = 1.5;
                sprintf(fname, "/data/SMS_dump/sms_%03d.yv12", identity);
                break;
            default:
                XLOGE("[%s] cannot dump format:%d for identity:%d",
                      __func__, graphicBuffer->format, identity);
                return;
        }

        {
            //Mutex::Autolock _l(mDumpLock);
            graphicBuffer->lock(GraphicBuffer::USAGE_SW_READ_OFTEN, &ptr);
            {
                XLOGI("    %s (config:%d, stride:%d, height:%d, ptr:%p)",
                    fname, c, graphicBuffer->stride, graphicBuffer->height, ptr);

                if (SkBitmap::kNo_Config != c) {
                    b.setConfig(c, graphicBuffer->stride, graphicBuffer->height);
                    b.setPixels(ptr);
                    SkImageEncoder::EncodeFile(fname, b, SkImageEncoder::kPNG_Type,
                                               SkImageEncoder::kDefaultQuality);
                } else {
                    uint32_t size = graphicBuffer->stride * graphicBuffer->height * bpp;
                    FILE *f = fopen(fname, "wb");
                    fwrite(ptr, size, 1, f);
                    fclose(f);
                }
            }
            graphicBuffer->unlock();
        }
    }
}

void SurfaceMediaSource::activeBufferBackup() {

    sp<GraphicBuffer> graphicBuffer = mSlots[mCurrentSlot].mGraphicBuffer;
    
    if (graphicBuffer == NULL) {
        XLOGW("[SMS::activeBufferBackup] graphicBuffer=%p not initialized", graphicBuffer.get());
        return;
    }
    
    if (true) {
        XLOGV("[SMS::activeBufferBackup] +");
        // check bpp
        float bpp = 0.0f;
        uint32_t width  = graphicBuffer->width;
        uint32_t height = graphicBuffer->height;
        uint32_t format = graphicBuffer->format;
        uint32_t usage  = graphicBuffer->usage;
        uint32_t stride = graphicBuffer->stride;
        status_t err;
        
        switch (graphicBuffer->format) {
            case PIXEL_FORMAT_RGBA_8888:
            case PIXEL_FORMAT_BGRA_8888:
            case PIXEL_FORMAT_RGBX_8888:
            case 0x1ff:
                // tricky format for SGX_COLOR_FORMAT_BGRX_8888 in fact
                bpp = 4.0;
                break;
            case PIXEL_FORMAT_RGB_565:
                bpp = 2.0;
                break;
            case HAL_PIXEL_FORMAT_I420:
                bpp = 1.5;
                break;
            case HAL_PIXEL_FORMAT_YV12:
                bpp = 1.5;
                break;
            default:
                XLOGE("[%s] SMS cannot dump format:%d for identity", __func__, graphicBuffer->format);
                break;
        }

#define MAX_DEFAULT_BUFFERS 10
        // initialize backup buffer max size
        char value[PROPERTY_VALUE_MAX];

        property_get("debug.sf.contbufsmax", value, "0");
        uint32_t max = atoi(value);
        if (max <= 0)
            max = MAX_DEFAULT_BUFFERS;

        if (mBackupBufsMax != max) {
            mBackupBufsMax = max;
            XLOGI("==>  ring buffer max size, max = %d", max);

            mBackBufs.clear();
            mBackupBufsIndex = 0;
        }

        // create new GraphicBuffer
        if (mBackBufs.size() < mBackupBufsMax) {
            sp<GraphicBuffer> buf;
            buf = new GraphicBuffer(width, height, format, usage);
            mBackBufs.push(buf);

            XLOGI("[SMS] new buffer for cont. dump, size = %d", mBackBufs.size());
        }

        // detect geometry changed
        if (mBackBufs[mBackupBufsIndex]->width != graphicBuffer->width || 
            mBackBufs[mBackupBufsIndex]->height != graphicBuffer->height ||
            mBackBufs[mBackupBufsIndex]->format != graphicBuffer->format) {
            XLOGI("[SMS] geometry changed, backup=(%d, %d, %d) ==> active=(%d, %d, %d)",
                mBackBufs[mBackupBufsIndex]->width,
                mBackBufs[mBackupBufsIndex]->height,
                mBackBufs[mBackupBufsIndex]->format,
                graphicBuffer->width,
                graphicBuffer->height,
                graphicBuffer->format);

            sp<GraphicBuffer> buf;
            buf = new GraphicBuffer(width, height, format, usage);
            mBackBufs.replaceAt(buf, mBackupBufsIndex);
        }

        if (graphicBuffer.get() == NULL || mBackBufs[mBackupBufsIndex] == NULL) {
            XLOGW("[SMS::activeBufferBackup] backup fail, graphicBuffer=%p, mBackBufs[%d]=%p",
                graphicBuffer.get(), mBackupBufsIndex, mBackBufs[mBackupBufsIndex].get());
            return;
        }
        
        // backup
        nsecs_t now = systemTime();        

        void* src;
        void* dst;
        err = graphicBuffer->lock(GraphicBuffer::USAGE_SW_READ_OFTEN, &src);
        if (err != NO_ERROR) {
            XLOGW("[SMS::activeBufferBackup] lock active buffer failed");
            return;
        }

        XLOGV("[SMS::activeBufferBackup] lock +, req=%d");
        err = mBackBufs[mBackupBufsIndex]->lock(GraphicBuffer::USAGE_SW_READ_OFTEN | GraphicBuffer::USAGE_SW_WRITE_OFTEN, &dst);
        if (err != NO_ERROR) {
            XLOGW("[SMS::activeBufferBackup] lock backup buffer failed");
            return;
        }

        backupProcess(dst, src, stride*height*bpp);

        mBackBufs[mBackupBufsIndex]->unlock();
        graphicBuffer->unlock();

        XLOGI("[SMS::activeBufferBackup] buf=%d, time=%lld", mBackupBufsIndex, ns2ms(systemTime() - now));

        mBackupBufsIndex ++;
        if (mBackupBufsIndex >= mBackupBufsMax)
            mBackupBufsIndex = 0;
    }
}

void SurfaceMediaSource::backupProcess(void* dst, void* src, size_t size) {
    XLOGV("[SMS::backupProcess] +");

    // backup 
    memcpy(dst, src, size);

    XLOGV("[SMS::backupProcess] -");
}

void SurfaceMediaSource::dumpContinuousBuffer() {
    char tmp[PROPERTY_VALUE_MAX];

    if (0 == mBackBufs.size())
        return;

    if (mBackupBufsMax <= 0) {
        XLOGW("[SMS::dumpContinuousBuffer] mBackupBufsMax not updated");
        return;
    }

    XLOGD("[SMS::dumpContinuousBuffer] +, size=%d", mBackBufs.size());

    if (true) {
        int start = (mBackupBufsIndex + mBackupBufsMax - 1) % mBackupBufsMax;
        int size = mBackBufs.size();
        for (uint32_t i = 0; i < mBackupBufsMax; i++) {
            if (i >= mBackBufs.size()) {
                XLOGW("[SMS::dumpContinuousBuffer] overflow i=%d, max=%d", i, mBackBufs.size());
                break;
            }

            int index = (start + mBackupBufsMax - i) % mBackupBufsMax;
            XLOGD("[SMS::dumpContinuousBuffer] i=%d, index=%d", mBackupBufsMax - i, index);
            sp<GraphicBuffer> buf = mBackBufs[index];
            dumpGraphicBuffer(buf, size - i);
        }
    }

    mBackBufs.clear();
    property_set("debug.sms.layerdump", "0");

    XLOGD("[SMS::dumpContinuousBuffer] -, size=%d", mBackBufs.size());
}

void SurfaceMediaSource::dumpGraphicBuffer(sp<GraphicBuffer> buf, int index) {
    char             fname[128];
    void*            ptr;
    SkBitmap         b;
    SkBitmap::Config c;
    float            bpp;
    
    
    c = SkBitmap::kNo_Config;
    switch (buf->format) {
        case PIXEL_FORMAT_RGBA_8888:
        case PIXEL_FORMAT_BGRA_8888:
        case PIXEL_FORMAT_RGBX_8888:
        case 0x1ff:                     // tricky format for SGX_COLOR_FORMAT_BGRX_8888 in fact
            c = SkBitmap::kARGB_8888_Config;
            sprintf(fname, "/data/SMS_dump/sms_%03d.png", index);
            break;
        case PIXEL_FORMAT_RGB_565:
            c = SkBitmap::kRGB_565_Config;
            sprintf(fname, "/data/SMS_dump/sms_%03d.png", index);
            break;
        case HAL_PIXEL_FORMAT_I420:
            bpp = 1.5;
            sprintf(fname, "/data/SMS_dump/sms_%03d.i420", index);
            break;
        case HAL_PIXEL_FORMAT_YV12:
            bpp = 1.5;
            sprintf(fname, "/data/SMS_dump/sms_%03d.yv12", index);
            break;
        default:
            XLOGE("[%s] SMS cannot dump format:%d", __func__, buf->format);
            return;
    }

    buf->lock(GraphicBuffer::USAGE_SW_READ_OFTEN, &ptr);
    {
        XLOGI("[SMS::dumpGraphicBuffer]");
        XLOGI("    %s (config:%d, stride:%d, height:%d, ptr:%p)",
            fname, c, buf->stride, buf->height, ptr);

        if (SkBitmap::kNo_Config != c) {
            b.setConfig(c, buf->stride, buf->height);
            b.setPixels(ptr);
            SkImageEncoder::EncodeFile(
                fname, b, SkImageEncoder::kPNG_Type, SkImageEncoder::kDefaultQuality);
        } else {
            uint32_t size = buf->stride * buf->height * bpp;
            FILE *f = fopen(fname, "wb");
            fwrite(ptr, size, 1, f);
            fclose(f);
        }
    }
    buf->unlock();
}

#endif

} // end of namespace android
