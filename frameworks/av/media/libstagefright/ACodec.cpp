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
#ifndef ANDROID_DEFAULT_CODE
    #define LOG_TAG "ACodec"
    #include <cutils/xlog.h>
    #undef LOGE
    #undef LOGW
    #undef LOGI
    #undef LOGD
    #undef LOGV
    #define LOGE XLOGE
    #define LOGW XLOGW
    #define LOGI XLOGI
    #define LOGD XLOGD
    #define LOGV XLOGD
    #define ALOGV XLOGD
#else
//    #define LOG_NDEBUG 0
    #define LOG_TAG "ACodec"
    #include <utils/Log.h>
#endif
#define ATRACE_TAG ATRACE_TAG_VIDEO
#include <utils/Trace.h>

#include <media/stagefright/ACodec.h>

#include <binder/MemoryDealer.h>

#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>

#include <media/stagefright/BufferProducerWrapper.h>
#include <media/stagefright/MediaCodecList.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/NativeWindowWrapper.h>
#include <media/stagefright/OMXClient.h>
#include <media/stagefright/OMXCodec.h>

#include <media/hardware/HardwareAPI.h>

#include <OMX_Component.h>

#include "include/avc_utils.h"

#ifndef ANDROID_DEFAULT_CODE
#define DUMP_PROFILE 0
#include <ctype.h>
#include "II420ColorConverter.h"
#include "I420ColorConverter.h"
//#include "DpBlitStream.h"
#include <linux/rtpm_prio.h>
#include <utils/threads.h>
#include <cutils/properties.h>
#include "DpBlitStream.h"

#define USE_COLOR_CONVERT_MVA
//#define DUMP_BITSTREAM
//#define DUMP_RAW
//#define DUMP_PERFORMANCE
#define ENABLE_MTK_BUF_ADDR_ALIGNMENT
#define MTK_BUF_ADDR_ALIGNMENT_VALUE 512

#define MEM_ALIGN_32 32
#define ROUND_16(X)     ((X + 0xF) & (~0xF))
#define ROUND_32(X)     ((X + 0x1F) & (~0x1F))
#define YUV_SIZE(W,H)   (W * H * 3 >> 1)
#endif

#ifndef ANDROID_DEFAULT_CODE
#ifdef USE_VIDEO_ION
#include <linux/ion_drv.h>
#include <ion/ion.h>
#endif
#endif

namespace android {
#ifndef ANDROID_DEFAULT_CODE
#if COLOR_CONVERT_PROFILING
int64_t getACodecTickCountMicros()
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (int64_t)(tv.tv_sec*1000000LL + tv.tv_usec);
}
#endif //COLOR_CONVERT_PROFILING
#ifdef USE_VIDEO_ION
OMX_BOOL ConfigIonBuffer(int ion_fd, struct ion_handle* handle) {

    struct ion_mm_data mm_data;
    mm_data.mm_cmd = ION_MM_CONFIG_BUFFER;
    mm_data.config_buffer_param.handle = handle;
    mm_data.config_buffer_param.eModuleID = 9;//hardcode temporary as eVideoGetM4UModuleID(VAL_MEM_CODEC_FOR_VDEC);
    mm_data.config_buffer_param.security = 0;
    mm_data.config_buffer_param.coherent = 0;

    if (ion_custom_ioctl(ion_fd, ION_CMD_MULTIMEDIA, &mm_data)) {
        ALOGE ("[ERROR] cannot configure buffer");
        return OMX_FALSE;
    }
    return OMX_TRUE;
}

OMX_U32 GetIonPhysicalAddress(int ion_fd, struct ion_handle* handle) {
    // query physical address
    struct ion_sys_data sys_data;
    sys_data.sys_cmd = ION_SYS_GET_PHYS;
    sys_data.get_phys_param.handle = handle;
    if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data)) {
        ALOGE ("[ERROR] cannot get buffer physical address");
        return 0;
    }
    return (OMX_U32)sys_data.get_phys_param.phy_addr;
}
#endif //USE_VIDEO_ION
#endif //ANDROID_DEFAULT_CODE

template<class T>
static void InitOMXParams(T *params) {
    params->nSize = sizeof(T);
    params->nVersion.s.nVersionMajor = 1;
    params->nVersion.s.nVersionMinor = 0;
    params->nVersion.s.nRevision = 0;
    params->nVersion.s.nStep = 0;
}

struct CodecObserver : public BnOMXObserver {
    CodecObserver() {}

    void setNotificationMessage(const sp<AMessage> &msg) {
        mNotify = msg;
    }

    // from IOMXObserver
    virtual void onMessage(const omx_message &omx_msg) {
        sp<AMessage> msg = mNotify->dup();

        msg->setInt32("type", omx_msg.type);
        msg->setPointer("node", omx_msg.node);

        switch (omx_msg.type) {
            case omx_message::EVENT:
            {
                msg->setInt32("event", omx_msg.u.event_data.event);
                msg->setInt32("data1", omx_msg.u.event_data.data1);
                msg->setInt32("data2", omx_msg.u.event_data.data2);
                break;
            }

            case omx_message::EMPTY_BUFFER_DONE:
            {
                msg->setPointer("buffer", omx_msg.u.buffer_data.buffer);
                break;
            }

            case omx_message::FILL_BUFFER_DONE:
            {
                msg->setPointer(
                        "buffer", omx_msg.u.extended_buffer_data.buffer);
                msg->setInt32(
                        "range_offset",
                        omx_msg.u.extended_buffer_data.range_offset);
                msg->setInt32(
                        "range_length",
                        omx_msg.u.extended_buffer_data.range_length);
                msg->setInt32(
                        "flags",
                        omx_msg.u.extended_buffer_data.flags);
                msg->setInt64(
                        "timestamp",
                        omx_msg.u.extended_buffer_data.timestamp);
#ifndef ANDROID_DEFAULT_CODE
                if( (OMX_ACODEC_COLOR_CONVERT & omx_msg.u.extended_buffer_data.flags) == OMX_ACODEC_COLOR_CONVERT )
                {
#ifdef USE_COLOR_CONVERT_MVA
                    MTK_PLATFORM_PRIVATE *mplatform_private = (MTK_PLATFORM_PRIVATE *)omx_msg.u.extended_buffer_data.platform_private;
                    ALOGD("Tid %d, mM4UMVABufferPa %p, mM4UVABufferVa %p", androidGetTid(), mplatform_private->mM4UMVABufferPa, mplatform_private->mM4UVABufferVa);
                    //assign platform_private for color convert
                    msg->setPointer(
                        "platform_private", (void *)mplatform_private->mM4UMVABufferPa);
                    msg->setPointer(
                        "platform_privateVa", (void *)mplatform_private->mM4UVABufferVa);
#else
                    msg->setPointer(
                            "platform_private",
                            omx_msg.u.extended_buffer_data.platform_private);
#endif //USE_COLOR_CONVERT_MVA
                }
                else
                {
                    msg->setPointer(
                            "platform_private",
                            omx_msg.u.extended_buffer_data.platform_private);
                }
#else
                msg->setPointer(
                        "platform_private",
                        omx_msg.u.extended_buffer_data.platform_private);
#endif //ANDROID_DEFAULT_CODE
                msg->setPointer(
                        "data_ptr",
                        omx_msg.u.extended_buffer_data.data_ptr);
                break;
            }

            default:
                TRESPASS();
                break;
        }

        msg->post();
    }

protected:
    virtual ~CodecObserver() {}

private:
    sp<AMessage> mNotify;

    DISALLOW_EVIL_CONSTRUCTORS(CodecObserver);
};

////////////////////////////////////////////////////////////////////////////////

struct ACodec::BaseState : public AState {
    BaseState(ACodec *codec, const sp<AState> &parentState = NULL);

protected:
    enum PortMode {
        KEEP_BUFFERS,
        RESUBMIT_BUFFERS,
        FREE_BUFFERS,
    };

    ACodec *mCodec;

    virtual PortMode getPortMode(OMX_U32 portIndex);

    virtual bool onMessageReceived(const sp<AMessage> &msg);

    virtual bool onOMXEvent(OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2);

    virtual void onOutputBufferDrained(const sp<AMessage> &msg);
    virtual void onInputBufferFilled(const sp<AMessage> &msg);

    void postFillThisBuffer(BufferInfo *info);

private:
    bool onOMXMessage(const sp<AMessage> &msg);

    bool onOMXEmptyBufferDone(IOMX::buffer_id bufferID);

    bool onOMXFillBufferDone(
            IOMX::buffer_id bufferID,
            size_t rangeOffset, size_t rangeLength,
            OMX_U32 flags,
            int64_t timeUs,
            void *platformPrivate,
            void *dataPtr);
#ifndef ANDROID_DEFAULT_CODE
    static void dumpProfile(const char* tag, const char* szName, int64_t timeUs);
    static bool isAudio(const char* strName);
    int Mtk_ACodecConvertDecoderOutputToI420(
        void* srcBits, int srcWidth, int srcHeight, ARect srcRect, void* dstBits, OMX_U32 srcFormat);
#endif

    void getMoreInputDataIfPossible();

    DISALLOW_EVIL_CONSTRUCTORS(BaseState);
};

////////////////////////////////////////////////////////////////////////////////

struct ACodec::DeathNotifier : public IBinder::DeathRecipient {
    DeathNotifier(const sp<AMessage> &notify)
        : mNotify(notify) {
    }

    virtual void binderDied(const wp<IBinder> &) {
        mNotify->post();
    }

protected:
    virtual ~DeathNotifier() {}

private:
    sp<AMessage> mNotify;

    DISALLOW_EVIL_CONSTRUCTORS(DeathNotifier);
};

struct ACodec::UninitializedState : public ACodec::BaseState {
    UninitializedState(ACodec *codec);

protected:
    virtual bool onMessageReceived(const sp<AMessage> &msg);
    virtual void stateEntered();

private:
    void onSetup(const sp<AMessage> &msg);
    bool onAllocateComponent(const sp<AMessage> &msg);

    sp<DeathNotifier> mDeathNotifier;

    DISALLOW_EVIL_CONSTRUCTORS(UninitializedState);
};

////////////////////////////////////////////////////////////////////////////////

struct ACodec::LoadedState : public ACodec::BaseState {
    LoadedState(ACodec *codec);

protected:
    virtual bool onMessageReceived(const sp<AMessage> &msg);
    virtual void stateEntered();

private:
    friend struct ACodec::UninitializedState;

    bool onConfigureComponent(const sp<AMessage> &msg);
    void onCreateInputSurface(const sp<AMessage> &msg);
    void onStart();
    void onShutdown(bool keepComponentAllocated);

    DISALLOW_EVIL_CONSTRUCTORS(LoadedState);
};

////////////////////////////////////////////////////////////////////////////////

struct ACodec::LoadedToIdleState : public ACodec::BaseState {
    LoadedToIdleState(ACodec *codec);

protected:
    virtual bool onMessageReceived(const sp<AMessage> &msg);
    virtual bool onOMXEvent(OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2);
    virtual void stateEntered();

private:
    status_t allocateBuffers();

    DISALLOW_EVIL_CONSTRUCTORS(LoadedToIdleState);
};

////////////////////////////////////////////////////////////////////////////////

struct ACodec::IdleToExecutingState : public ACodec::BaseState {
    IdleToExecutingState(ACodec *codec);

protected:
    virtual bool onMessageReceived(const sp<AMessage> &msg);
    virtual bool onOMXEvent(OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2);
    virtual void stateEntered();

private:
    DISALLOW_EVIL_CONSTRUCTORS(IdleToExecutingState);
};

////////////////////////////////////////////////////////////////////////////////

struct ACodec::ExecutingState : public ACodec::BaseState {
    ExecutingState(ACodec *codec);
    void submitRegularOutputBuffers();
    void submitOutputMetaBuffers();
    void submitOutputBuffers();

    // Submit output buffers to the decoder, submit input buffers to client
    // to fill with data.
    void resume();

    // Returns true iff input and output buffers are in play.
    bool active() const { return mActive; }

protected:
    virtual PortMode getPortMode(OMX_U32 portIndex);
    virtual bool onMessageReceived(const sp<AMessage> &msg);
    virtual void stateEntered();

    virtual bool onOMXEvent(OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2);

private:
    bool mActive;

    DISALLOW_EVIL_CONSTRUCTORS(ExecutingState);
};

////////////////////////////////////////////////////////////////////////////////

struct ACodec::OutputPortSettingsChangedState : public ACodec::BaseState {
    OutputPortSettingsChangedState(ACodec *codec);

protected:
    virtual PortMode getPortMode(OMX_U32 portIndex);
    virtual bool onMessageReceived(const sp<AMessage> &msg);
    virtual void stateEntered();

    virtual bool onOMXEvent(OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2);

private:
    DISALLOW_EVIL_CONSTRUCTORS(OutputPortSettingsChangedState);
};

////////////////////////////////////////////////////////////////////////////////

struct ACodec::ExecutingToIdleState : public ACodec::BaseState {
    ExecutingToIdleState(ACodec *codec);

protected:
    virtual bool onMessageReceived(const sp<AMessage> &msg);
    virtual void stateEntered();

    virtual bool onOMXEvent(OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2);

    virtual void onOutputBufferDrained(const sp<AMessage> &msg);
    virtual void onInputBufferFilled(const sp<AMessage> &msg);

private:
    void changeStateIfWeOwnAllBuffers();

    bool mComponentNowIdle;

    DISALLOW_EVIL_CONSTRUCTORS(ExecutingToIdleState);
};

////////////////////////////////////////////////////////////////////////////////

struct ACodec::IdleToLoadedState : public ACodec::BaseState {
    IdleToLoadedState(ACodec *codec);

protected:
    virtual bool onMessageReceived(const sp<AMessage> &msg);
    virtual void stateEntered();

    virtual bool onOMXEvent(OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2);

private:
    DISALLOW_EVIL_CONSTRUCTORS(IdleToLoadedState);
};

////////////////////////////////////////////////////////////////////////////////

struct ACodec::FlushingState : public ACodec::BaseState {
    FlushingState(ACodec *codec);

protected:
    virtual bool onMessageReceived(const sp<AMessage> &msg);
    virtual void stateEntered();

    virtual bool onOMXEvent(OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2);

    virtual void onOutputBufferDrained(const sp<AMessage> &msg);
    virtual void onInputBufferFilled(const sp<AMessage> &msg);

private:
    bool mFlushComplete[2];

    void changeStateIfWeOwnAllBuffers();

    DISALLOW_EVIL_CONSTRUCTORS(FlushingState);
};

////////////////////////////////////////////////////////////////////////////////

ACodec::ACodec()
    : mQuirks(0),
      mNode(NULL),
#ifndef ANDROID_DEFAULT_CODE
      mSupportsPartialFrames(false),
      mLeftOverBuffer(NULL),
      mMaxQueueBufferNum(-1),
      mDumpFile(NULL),
      mIsVideoDecoder(false),
      mIsVideoEncoder(false),
      mVideoAspectRatioWidth(1),
      mVideoAspectRatioHeight(1),
      mDumpRawFile(NULL),
      mIsDemandNormalYUV(false),
      mAcodecCConvertMode(0),
      mAcodecEncCConvertMode(0),
      mACodecColorConverter(NULL),
      mAlignedSize(0),
      mM4UBufferHandle(NULL),
      mM4UBufferCount(0),
#ifdef USE_VIDEO_ION
      mIonDevFd(-1),
      mCCIonDevFd(-1),
#endif //USE_VIDEO_ION
#endif //ANDROID_DEFAULT_CODE
      mSentFormat(false),
      mIsEncoder(false),
      mUseMetadataOnEncoderOutput(false),
      mShutdownInProgress(false),
      mEncoderDelay(0),
      mEncoderPadding(0),
      mChannelMaskPresent(false),
      mChannelMask(0),
      mDequeueCounter(0),
      mStoreMetaDataInOutputBuffers(false),
      mMetaDataBuffersToSubmit(0),
      mRepeatFrameDelayUs(-1ll) {
    mUninitializedState = new UninitializedState(this);
    mLoadedState = new LoadedState(this);
    mLoadedToIdleState = new LoadedToIdleState(this);
    mIdleToExecutingState = new IdleToExecutingState(this);
    mExecutingState = new ExecutingState(this);

    mOutputPortSettingsChangedState =
        new OutputPortSettingsChangedState(this);

    mExecutingToIdleState = new ExecutingToIdleState(this);
    mIdleToLoadedState = new IdleToLoadedState(this);
    mFlushingState = new FlushingState(this);

    mPortEOS[kPortIndexInput] = mPortEOS[kPortIndexOutput] = false;
    mInputEOSResult = OK;

#ifndef ANDROID_DEFAULT_CODE
#ifdef USE_VIDEO_ION
    for (int i = 0 ; i < MAX_ION_BUF_COUNT ; i++) {
        mIonInputBufInfo[i].IonFd = 0xFFFFFFFF;
        mIonInputBufInfo[i].u4BufSize = 0xFFFFFFFF;
        mIonInputBufInfo[i].u4BufVA = 0xFFFFFFFF;
        mIonInputBufInfo[i].u4IonBufHandle = 0xFFFFFFFF;

        mIonOutputBufInfo[i].IonFd = 0xFFFFFFFF;
        mIonOutputBufInfo[i].u4BufSize = 0xFFFFFFFF;
        mIonOutputBufInfo[i].u4BufVA = 0xFFFFFFFF;
        mIonOutputBufInfo[i].u4IonBufHandle = 0xFFFFFFFF;
    }
    //for color convert
    for (int i = 0 ; i < MAX_ION_BUF_COUNT ; i++) {
        mCCIonBufInfo[i].IonFd = 0xFFFFFFFF;
        mCCIonBufInfo[i].u4BufSize = 0xFFFFFFFF;
        mCCIonBufInfo[i].u4BufVA = 0xFFFFFFFF;
        mCCIonBufInfo[i].u4IonBufHandle = 0xFFFFFFFF;
    }
#endif
#endif

    changeState(mUninitializedState);

#ifndef ANDROID_DEFAULT_CODE
#if COLOR_CONVERT_PROFILING
    mProfileColorConvCnt = 0;
    mProfileColorConvout_timeMin = 0x10000;
    mProfileColorConvout_timeMax = 0;
    mProfileColorConvout_timeAvg = 0;
#endif //COLOR_CONVERT_PROFILING

#endif //ANDROID_DEFAULT_CODE
}

ACodec::~ACodec() {
#ifndef ANDROID_DEFAULT_CODE    
    ALOGD("~ACodec");
   if (mDumpFile != NULL) {
        fclose(mDumpFile);
        mDumpFile = NULL;
        ALOGD("dump file closed");
    }
    if (mDumpRawFile != NULL) {
        fclose(mDumpRawFile);
        mDumpRawFile = NULL;
        ALOGD("dump raw file closed");
    }

#ifdef USE_VIDEO_ION
    for (int i = 0 ; i < MAX_ION_BUF_COUNT ; i++) {
        if (mIonInputBufInfo[i].IonFd  != 0xFFFFFFFF) {
             ion_munmap(mIonDevFd, (void*)mIonInputBufInfo[i].u4BufVA, mIonInputBufInfo[i].u4BufSize); 	
            // free ION buffer fd
            if (ion_share_close (mIonDevFd, mIonInputBufInfo[i].IonFd)) {
                ALOGE ("[ERROR] ion_share_close failed, LINE:%d", __LINE__);
            }

            // free ION buffer handle
            //MTK_OMX_LOGD ("@@ calling ion_free mIonDevFd(0x%08X)", mIonDevFd);
            if (ion_free(mIonDevFd, (struct ion_handle *)mIonInputBufInfo[i].u4IonBufHandle)) {
                ALOGE ("[ERROR] ion_free failed in FreeBuffer, LINE:%d", __LINE__);
            }
        }
    }

    for (int i = 0 ; i < MAX_ION_BUF_COUNT ; i++) {
        if (mIonOutputBufInfo[i].IonFd  != 0xFFFFFFFF) {
             ion_munmap(mIonDevFd, (void*)mIonOutputBufInfo[i].u4BufVA, mIonOutputBufInfo[i].u4BufSize);
            // free ION buffer fd
            if (ion_share_close (mIonDevFd, mIonOutputBufInfo[i].IonFd)) {
                ALOGE ("[ERROR] ion_share_close failed, LINE:%d", __LINE__);
            }

            // free ION buffer handle
            //MTK_OMX_LOGD ("@@ calling ion_free mIonDevFd(0x%08X)", mIonDevFd);
            if (ion_free(mIonDevFd, (struct ion_handle *)mIonOutputBufInfo[i].u4IonBufHandle)) {
                ALOGE ("[ERROR] ion_free failed in FreeBuffer, LINE:%d", __LINE__);
            }
        }
    }

    for (int i = 0 ; i < MAX_ION_BUF_COUNT ; i++) {
        if (mCCIonBufInfo[i].IonFd  != 0xFFFFFFFF) {
             ion_munmap(mCCIonDevFd, (void*)mCCIonBufInfo[i].u4BufVA, mCCIonBufInfo[i].u4BufSize);    
            // free ION buffer fd
            if (ion_share_close (mCCIonDevFd, mCCIonBufInfo[i].IonFd)) {
                ALOGE ("[ERROR] ion_share_close failed, LINE:%d", __LINE__);
            }
    
            // free ION buffer handle
            //MTK_OMX_LOGD ("@@ calling ion_free mIonDevFd(0x%08X)", mIonDevFd);
            if (ion_free(mCCIonDevFd, (struct ion_handle *)mCCIonBufInfo[i].u4IonBufHandle)) {
                ALOGE ("[ERROR] ion_free failed in FreeBuffer, LINE:%d", __LINE__);
            }
        }
    }

    if (-1 != mIonDevFd) {
        close(mIonDevFd);
    }

    if (-1 != mCCIonDevFd) {
        close(mCCIonDevFd);
    }

#endif //USE_VIDEO_ION
    if(mACodecColorConverter)
    {
        mACodecColorConverter->deinitACodecColorConverter(mACodecColorConverter, mM4UBufferHandle);
        // Release the color converter
        delete mACodecColorConverter;
        mACodecColorConverter = NULL;
    }
#endif //ANDROID_DEFAULT_CODE
}

void ACodec::setNotificationMessage(const sp<AMessage> &msg) {
    mNotify = msg;
}

void ACodec::initiateSetup(const sp<AMessage> &msg) {
    msg->setWhat(kWhatSetup);
    msg->setTarget(id());
    msg->post();
}

void ACodec::signalSetParameters(const sp<AMessage> &params) {
    sp<AMessage> msg = new AMessage(kWhatSetParameters, id());
    msg->setMessage("params", params);
    msg->post();
}

void ACodec::initiateAllocateComponent(const sp<AMessage> &msg) {
    msg->setWhat(kWhatAllocateComponent);
    msg->setTarget(id());
    msg->post();
}

void ACodec::initiateConfigureComponent(const sp<AMessage> &msg) {
    msg->setWhat(kWhatConfigureComponent);
    msg->setTarget(id());
    msg->post();
}

void ACodec::initiateCreateInputSurface() {
    (new AMessage(kWhatCreateInputSurface, id()))->post();
}

void ACodec::signalEndOfInputStream() {
    (new AMessage(kWhatSignalEndOfInputStream, id()))->post();
}

void ACodec::initiateStart() {
    (new AMessage(kWhatStart, id()))->post();
}

void ACodec::signalFlush() {
    ALOGD("[%s] signalFlush", mComponentName.c_str());
    (new AMessage(kWhatFlush, id()))->post();
}

void ACodec::signalResume() {
    (new AMessage(kWhatResume, id()))->post();
}

void ACodec::initiateShutdown(bool keepComponentAllocated) {
    sp<AMessage> msg = new AMessage(kWhatShutdown, id());
    msg->setInt32("keepComponentAllocated", keepComponentAllocated);
    msg->post();
}

void ACodec::signalRequestIDRFrame() {
    (new AMessage(kWhatRequestIDRFrame, id()))->post();
}

// *** NOTE: THE FOLLOWING WORKAROUND WILL BE REMOVED ***
// Some codecs may return input buffers before having them processed.
// This causes a halt if we already signaled an EOS on the input
// port.  For now keep submitting an output buffer if there was an
// EOS on the input port, but not yet on the output port.
void ACodec::signalSubmitOutputMetaDataBufferIfEOS_workaround() {
    if (mPortEOS[kPortIndexInput] && !mPortEOS[kPortIndexOutput] &&
            mMetaDataBuffersToSubmit > 0) {
        (new AMessage(kWhatSubmitOutputMetaDataBufferIfEOS, id()))->post();
    }
}

#ifndef ANDROID_DEFAULT_CODE
void ACodec::signalVEncIInterval(int seconds) {
    sp<AMessage> msg = new AMessage(kWhatMtkVEncIFrameInterval, id());
    msg->setInt32("MtkVEncIRate", seconds);
    msg->post();
}
void ACodec::signalVEncSkipFrame() {
    (new AMessage(kWhatMtkVEncSkipFrame, id()))->post();
}
void ACodec::signalVEncDrawBlack(int enable) {//for Miracast test case SIGMA 5.1.11 workaround
    sp<AMessage> msg = new AMessage(kWhatMtkVEncDrawBlack, id());
    msg->setInt32("enable", enable);
    msg->post();
    ALOGE("cccccccccccccc");
}
void ACodec::signalVEncBitRate(int bitrate) {
    sp<AMessage> msg = new AMessage(kWhatMtkVEncBitRate, id());
    msg->setInt32("MtkVEncBitRate", bitrate);
    msg->post();
}
void ACodec::signalVEncFrameRate(int framerate) {
    sp<AMessage> msg = new AMessage(kWhatMtkVEncFrameRate, id());
    msg->setInt32("MtkVEncFrameRate", framerate);
    msg->post();
}
#endif//ANDROID_DEFAULT_CODE

status_t ACodec::allocateBuffersOnPort(OMX_U32 portIndex) {
    CHECK(portIndex == kPortIndexInput || portIndex == kPortIndexOutput);

    CHECK(mDealer[portIndex] == NULL);
    CHECK(mBuffers[portIndex].isEmpty());

    status_t err;
    if (mNativeWindow != NULL && portIndex == kPortIndexOutput) {
        if (mStoreMetaDataInOutputBuffers) {
            err = allocateOutputMetaDataBuffers();
        } else {
            err = allocateOutputBuffersFromNativeWindow();
        }
    } else {
        OMX_PARAM_PORTDEFINITIONTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = portIndex;

        err = mOMX->getParameter(
                mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

        if (err == OK) {
#ifndef ANDROID_DEFAULT_CODE
#ifdef ENABLE_MTK_BUF_ADDR_ALIGNMENT
            // mtk80902: ALPS00569949 - porting buf align from OMXCodec
            def.nBufferSize = ((def.nBufferSize + MTK_BUF_ADDR_ALIGNMENT_VALUE-1) & ~(MTK_BUF_ADDR_ALIGNMENT_VALUE-1));
#endif  // ENABLE_MTK_BUF_ADDR_ALIGNMENT

#ifdef USE_VIDEO_ION  // Morris Yang 20130709 ION
            if (mIsVideoDecoder || mIsVideoEncoder) {
                def.nBufferSize = ((def.nBufferSize + 32-1) & ~(32-1));
            }
#endif  // USE_VIDEO_ION
#endif  // ANDROID_DEFAULT_CODE

            ALOGV("[%s] Allocating %lu buffers of size %lu on %s port",
                    mComponentName.c_str(),
                    def.nBufferCountActual, def.nBufferSize,
                    portIndex == kPortIndexInput ? "input" : "output");
#ifndef ANDROID_DEFAULT_CODE
            size_t totalSize;
            //TODO: get alignment info from omx
        #ifdef ENABLE_MTK_BUF_ADDR_ALIGNMENT
            //if((portIndex == kPortIndexOutput) && (!strncmp("OMX.MTK.VIDEO.DECODER.", mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mIsDemandNormalYUV==true))
            if((portIndex == kPortIndexOutput) && (mIsVideoDecoder) && (mIsDemandNormalYUV==true))                
            {
                if( mM4UBufferCount > 0 )
                {
                    ALOGW("mM4UBufferCount: %d larger than init value that will do reallocate buffers", mM4UBufferCount );
                    freeBuffersOnPort(kPortIndexOutput);
                }
                mAlignedSize = (((def.nBufferSize + MTK_BUF_ADDR_ALIGNMENT_VALUE-1) & ~(MTK_BUF_ADDR_ALIGNMENT_VALUE-1)));
                ALOGD("def.nBufferSize %d, mAlignedSize %d", def.nBufferSize, mAlignedSize);
                totalSize = def.nBufferCountActual * (MTK_BUF_ADDR_ALIGNMENT_VALUE + mAlignedSize);
            }
            else
                totalSize = def.nBufferCountActual * (((def.nBufferSize + MTK_BUF_ADDR_ALIGNMENT_VALUE-1) & ~(MTK_BUF_ADDR_ALIGNMENT_VALUE-1)) + MTK_BUF_ADDR_ALIGNMENT_VALUE);
        #else
            totalSize = def.nBufferCountActual * def.nBufferSize;
        #endif

#ifdef USE_VIDEO_ION  // Morris Yang 20130709 ION
        if (mIsVideoDecoder || mIsVideoEncoder) {
            totalSize = def.nBufferCountActual * ((def.nBufferSize + 32-1) & ~(32-1));
        }
#endif  // USE_VIDEO_ION
#else
            size_t totalSize = def.nBufferCountActual * def.nBufferSize;
#endif //ANDROID_DEFAULT_CODE

#ifndef ANDROID_DEFAULT_CODE
#ifdef USE_VIDEO_ION  // Morris Yang 20130709 ION
        if (mIsVideoDecoder || mIsVideoEncoder) {
            if (-1 == mIonDevFd) {
                mIonDevFd = ion_open();
                if (mIonDevFd < 0) {
                    ALOGE ("[ERROR] cannot open ION device.");
                    return UNKNOWN_ERROR;
                 }
             }
        }
        else {
            mDealer[portIndex] = new MemoryDealer(totalSize, "ACodec");
        }
#else
            mDealer[portIndex] = new MemoryDealer(totalSize, "ACodec");
#endif  // USE_VIDEO_ION
#endif  // ANDROID_DEFAULT_CODE

#ifndef ANDROID_DEFAULT_CODE
            if((portIndex == kPortIndexOutput) && (!strncmp("OMX.MTK.VIDEO.DECODER.", mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mIsDemandNormalYUV==true))
            {
#ifdef USE_VIDEO_ION
                if (-1 == mCCIonDevFd) {
                    mCCIonDevFd = ion_open();
                    if (mCCIonDevFd < 0) {
                        ALOGE ("[ERROR] cannot open ION device.");
                        return UNKNOWN_ERROR;
                    }
                }
#else
                //new another memory dealer for handling color converted buffer
                mDealerForColorConvert[kPortIndexOutputForColorConvert] = new MemoryDealer(totalSize, "ACodec-2");
                ALOGV("@debug: mDealerForColorConvert done, mNode: %x", mNode);
                //memset(&mMVAParamSrc, 0x0, sizeof(mMVAParamSrc));
#endif
                memset(&mMVAParamDst, 0x0, sizeof(mMVAParamDst));
            }
#endif //ANDROID_DEFAULT_CODE
            for (OMX_U32 i = 0; i < def.nBufferCountActual; ++i) {
                sp<IMemory> mem = NULL;
#ifndef ANDROID_DEFAULT_CODE
#ifdef USE_VIDEO_ION
                struct ion_handle* handle;
                unsigned char* pIonBuffer = 0;
                int share_fd = -1;

                struct ion_handle* handleCC;
                unsigned char* pCCIonBuffer = 0;
                int share_fdCC = -1;

                if (mIsVideoDecoder || mIsVideoEncoder) {
                    int ret = ion_alloc_mm(mIonDevFd, def.nBufferSize, 512, 0, &handle);
                    if (0 != ret) {
                        ALOGE ("[ERROR] ion_alloc_mm failed (%d), LINE:%d", ret, __LINE__);
                        return UNKNOWN_ERROR;
                    }
                    if (ion_share(mIonDevFd, handle, &share_fd)) {
                        ALOGE ("[ERROR] ion_share failed, LINE:%d", __LINE__);
                    }
                    // map virtual address
                    pIonBuffer = (OMX_U8*) ion_mmap(mIonDevFd, NULL, def.nBufferSize, PROT_READ | PROT_WRITE, MAP_SHARED, share_fd, 0);
                    if ((pIonBuffer == NULL) || (pIonBuffer == (void*)-1)) {
                        ALOGE ("[ERROR] ion_mmap failed, LINE:%d", __LINE__);
                        return UNKNOWN_ERROR;
                    }

                    if (portIndex == kPortIndexInput) {
                        mIonInputBufInfo[i].IonFd = share_fd;
                        mIonInputBufInfo[i].u4BufSize = def.nBufferSize;
                        mIonInputBufInfo[i].u4BufVA = (uint32_t)pIonBuffer;
                        mIonInputBufInfo[i].u4IonBufHandle = (uint32_t)handle;
                    }
                    else {
                        mIonOutputBufInfo[i].IonFd = share_fd;
                        mIonOutputBufInfo[i].u4BufSize = def.nBufferSize;
                        mIonOutputBufInfo[i].u4BufVA = (uint32_t)pIonBuffer;
                        mIonOutputBufInfo[i].u4IonBufHandle = (uint32_t)handle;
                    }
                    ALOGD ("@@ [%s] ion buffer (0x%08X), handle %x, share_fd %x", portIndex == kPortIndexInput ? "input" : "output" ,pIonBuffer, handle, share_fd);
                }
                else {
                        mem = mDealer[portIndex]->allocate(def.nBufferSize);
                    if (mem.get() == NULL) {
                        ALOGE("[%s] cannot allocate %s port(i=%d) buffer(%lu)", 
                        mComponentName.c_str(), portIndex == kPortIndexInput ? "input" : "output",
                        i, def.nBufferSize);
                        return NO_MEMORY;
                    }
                }
#else
                    mem = mDealer[portIndex]->allocate(def.nBufferSize);

                //        CHECK(mem.get() != NULL);
                if (mem.get() == NULL) {
                    ALOGE("[%s] cannot allocate %s port(i=%d) buffer(%lu)", 
                            mComponentName.c_str(), portIndex == kPortIndexInput ? "input" : "output",
                            i, def.nBufferSize);
                    return NO_MEMORY;
                }
#endif  // USE_VIDEO_ION

#else   //ANDROID_DEFAULT_CODE     
                 mem = mDealer[portIndex]->allocate(def.nBufferSize);
#ifdef ANDROID_DEFAULT_CODE
                CHECK(mem.get() != NULL);
#else //ANDROID_DEFAULT_CODE
                //error handling instead of NE
                if (mem.get() == NULL) {
                    ALOGE("[%s] cannot allocate %s port(i=%d) buffer(%lu)", 
                            mComponentName.c_str(), portIndex == kPortIndexInput ? "input" : "output",
                            i, def.nBufferSize);
                    return NO_MEMORY;
                }
#endif //ANDROID_DEFAULT_CODE
#endif  // ANDROID_DEFAULT_CODE

#ifndef ANDROID_DEFAULT_CODE
                sp<IMemory> memforcolorconvert = NULL;

                if((portIndex == kPortIndexOutput) && (!strncmp("OMX.MTK.VIDEO.DECODER.", mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mIsDemandNormalYUV==true))
                {
#ifdef USE_VIDEO_ION
                    int ret = ion_alloc_mm(mCCIonDevFd, def.nBufferSize, 512, 0, &handleCC);
                    if (0 != ret) {
                     ALOGE ("[ERROR] ion_alloc_mm failed (%d), LINE:%d", ret, __LINE__);
                     return UNKNOWN_ERROR;
                    }
                    if (ion_share(mCCIonDevFd, handleCC, &share_fdCC)) {
                     ALOGE ("[ERROR] ion_share failed, LINE:%d", __LINE__);
                    }
                    // map virtual address
                    pCCIonBuffer = (OMX_U8*) ion_mmap(mCCIonDevFd, NULL, def.nBufferSize, PROT_READ | PROT_WRITE, MAP_SHARED, share_fdCC, 0);
                    if ((pCCIonBuffer == NULL) || (pCCIonBuffer == (void*)-1)) {
                     ALOGE ("[ERROR] ion_mmap failed, LINE:%d", __LINE__);
                     return UNKNOWN_ERROR;
                    }
                    mCCIonBufInfo[i].IonFd = share_fdCC;
                    mCCIonBufInfo[i].u4BufSize = def.nBufferSize;
                    mCCIonBufInfo[i].u4BufVA = (uint32_t)pCCIonBuffer;
                    mCCIonBufInfo[i].u4IonBufHandle = (uint32_t)handleCC;
                    ALOGD ("@@ [%s] CC ion buffer (0x%08X), handleCC %x, share_fdCC %x", portIndex == kPortIndexInput ? "input" : "output" ,pCCIonBuffer, handleCC, share_fdCC);
#else
                    memforcolorconvert = mDealerForColorConvert[kPortIndexOutputForColorConvert]->allocate((MTK_BUF_ADDR_ALIGNMENT_VALUE + mAlignedSize));
 
                    if (memforcolorconvert.get() == NULL) {
                        ALOGE("[%s] memforcolorconvert cannot allocate %s port(i=%d) buffer(%lu)", 
                                mComponentName.c_str(), portIndex == kPortIndexInput ? "input" : "output",
                                i, mAlignedSize);
                        return NO_MEMORY;
                    }
#endif
                }
#endif //ANDROID_DEFAULT_CODE

                BufferInfo info;
                info.mStatus = BufferInfo::OWNED_BY_US;
#ifndef ANDROID_DEFAULT_CODE
                BufferInfo infoForOMX;
                if((portIndex == kPortIndexOutput) && (!strncmp("OMX.MTK.VIDEO.DECODER.", mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mIsDemandNormalYUV==true))
                {
                    infoForOMX.mStatus = BufferInfo::OWNED_BY_US;
                }
#endif //ANDROID_DEFAULT_CODE

                uint32_t requiresAllocateBufferBit =
                    (portIndex == kPortIndexInput)
                        ? OMXCodec::kRequiresAllocateBufferOnInputPorts
                        : OMXCodec::kRequiresAllocateBufferOnOutputPorts;
                if ((portIndex == kPortIndexInput && (mFlags & kFlagIsSecure))
                        || mUseMetadataOnEncoderOutput) {
                    mem.clear();

                    void *ptr;
                    err = mOMX->allocateBuffer(
                            mNode, portIndex, def.nBufferSize, &info.mBufferID,
                            &ptr);
                    int32_t bufSize = mUseMetadataOnEncoderOutput ?
                            (4 + sizeof(buffer_handle_t)) : def.nBufferSize;
                    ALOGD("@debug: bufSize %d, %x, %d", bufSize, mFlags, mUseMetadataOnEncoderOutput);
                    info.mData = new ABuffer(ptr, bufSize);
                } else if (mQuirks & requiresAllocateBufferBit) {
#ifndef ANDROID_DEFAULT_CODE
                if((portIndex == kPortIndexOutput) && (!strncmp("OMX.MTK.VIDEO.DECODER.", mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mIsDemandNormalYUV==true))
                {
                    err = mOMX->allocateBufferWithBackup(
                    mNode, portIndex, memforcolorconvert, &infoForOMX.mBufferID);
                    ALOGD("@debug: allocateBufferWithBackup[%d], infoForOMX.mBufferID(%p)", i, infoForOMX.mBufferID);
                }
                else
                {
                    err = mOMX->allocateBufferWithBackup(
                    mNode, portIndex, mem, &info.mBufferID);
                    ALOGD("@debug: allocateBufferWithBackup[%d], mBufferID(%p)", i, info.mBufferID);
                }
#else
                    err = mOMX->allocateBufferWithBackup(
                            mNode, portIndex, mem, &info.mBufferID);
                    ALOGD("@debug: allocateBufferWithBackup[%d], mBufferID(%p)", (int)i, info.mBufferID);
#endif //ANDROID_DEFAULT_CODE
                } 
                else 
                {
#ifndef ANDROID_DEFAULT_CODE
                    if((portIndex == kPortIndexOutput) && (!strncmp("OMX.MTK.VIDEO.DECODER.", mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mIsDemandNormalYUV==true))
                    {
#ifdef USE_VIDEO_ION
                        err = mOMX->useIonBuffer(mNode, portIndex, (unsigned char*)pCCIonBuffer, share_fdCC, def.nBufferSize, &infoForOMX.mBufferID);
                        ALOGD("@debug: after useIonBuffer[%d], infoForOMX.mBufferID(%p)", i, infoForOMX.mBufferID);
#else
                        if(memforcolorconvert==NULL)
                            return NO_MEMORY;
                        err = mOMX->useBuffer(mNode, portIndex, memforcolorconvert, &infoForOMX.mBufferID);
                        ALOGD("@debug: after useBuffer[%d], infoForOMX.mBufferID(%p)", i, infoForOMX.mBufferID);
#endif //USE_VIDEO_ION
                    }
                    else
                    {
#ifdef USE_VIDEO_ION  // Morris Yang 20130709 ION
                        if (mIsVideoDecoder || mIsVideoEncoder) {
                            ALOGD("@debug: share_fd %x, def.nBufferSize %d", share_fd, def.nBufferSize);
                            err = mOMX->useIonBuffer(mNode, portIndex, (unsigned char*)pIonBuffer, share_fd, def.nBufferSize, &info.mBufferID);
                        }
                        else {
                            err = mOMX->useBuffer(mNode, portIndex, mem, &info.mBufferID);
                        }
                        ALOGD("@debug: useBuffer[%d], mBufferID(%p)", i, info.mBufferID);
#else
                        err = mOMX->useBuffer(mNode, portIndex, mem, &info.mBufferID);
                        ALOGD("@debug: useBuffer[%d], mBufferID(%p)", i, info.mBufferID);
#endif  // USE_VIDEO_ION
                    }
#else
                    err = mOMX->useBuffer(mNode, portIndex, mem, &info.mBufferID);
                    ALOGD("@debug: useBuffer[%d], mBufferID(%p)", (int)i, info.mBufferID);
#endif //ANDROID_DEFAULT_CODE
                }
#ifndef ANDROID_DEFAULT_CODE
                if((portIndex == kPortIndexOutput) && (!strncmp("OMX.MTK.VIDEO.DECODER.", mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mIsDemandNormalYUV==true))
                {
                    ssize_t index = matchingBufferIndex.add();
                    CodecBufferIndex *entry = &matchingBufferIndex.editItemAt(index);
                    //do mapping index for two output buffers 
                    entry->mBufferIdxForOriginal = info.mBufferID = infoForOMX.mBufferID;
                    entry->mIndex = index;
                    //Note: need someone reference this allocated dealer memory or it will be deconstructed after its life cycle.
#ifdef USE_VIDEO_ION
                    entry->mBufferMemory = mIonOutputBufInfo[i].u4BufVA;

                    info.mData = new ABuffer(pIonBuffer, def.nBufferSize);
                    ALOGD("@debug: info.Ion.Buffer[%d], info[%p] d: %p, index %d, info.mBufferID(%p)", i, &info, info.mData->data(), index, info.mBufferID);

                    mM4UBufferVa[i] = mIonOutputBufInfo[i].u4BufVA;
                    mM4UBufferSize[i] = mIonOutputBufInfo[i].u4BufSize;
                    #if 0
                    ConfigIonBuffer(mIonDevFd, (struct ion_handle *)mIonBufInfo[i].u4IonBufHandle);
                    mM4UBufferPa[i] = (unsigned long)GetIonPhysicalAddress(mIonDevFd, (struct ion_handle *)mIonOutputBufInfo[i].u4IonBufHandle);
                    #else
                    if(mACodecColorConverter)
                        if(OK!=mACodecColorConverter->getVideoAllocMVA(mACodecColorConverter, (struct ion_handle *)mIonOutputBufInfo[i].u4IonBufHandle, mM4UBufferVa[i], &mM4UBufferPa[i], mM4UBufferSize[i], (void *)&mIonDevFd))
                        {
                            ALOGW("[ERROR][M4U][Mtk_getVideoAllocMVA]");
                            //err = BAD_VALUE;
                            //return err;
                        }
                    #endif

                    ALOGD("[ION][Output][UseBuffer] mM4UBufferVa = 0x%x, mM4UBufferPa = 0x%x, mM4UBufferSize = 0x%x, mM4UBufferCount = %d\n",
                        mM4UBufferVa[i], mM4UBufferPa[i], mM4UBufferSize[i], i);

                    mMVAParamDst.mM4UBufferVaA[i] = (OMX_U32)mM4UBufferVa[i];
                    mMVAParamDst.mM4UBufferPaB[i] = (OMX_U32)mM4UBufferPa[i];
                    mMVAParamDst.mOutputBufferPopulatedCnt = def.nBufferCountActual;
                    mM4UBufferCount++;

                    infoForOMX.mData = new ABuffer(pCCIonBuffer, def.nBufferSize);
                    ALOGD("@debug: infoForOMX.Ion.Buffer[%d], info[%p], d: %p, index %d, OMX.mBufferID(%p)", i, &infoForOMX, infoForOMX.mData->data(), 
                        index, infoForOMX.mBufferID);
#else //USE_VIDEO_ION
                    entry->mBufferMemory = mem;

                    if (mem != NULL) 
                    {
                        info.mData = new ABuffer(mem->pointer(), (MTK_BUF_ADDR_ALIGNMENT_VALUE + mAlignedSize));
                        ALOGD("@debug: info.Buffer[%d], info[%p] d: %p, index %d, info.mBufferID(%p)", i, &info, info.mData->data(), index, info.mBufferID);
 
#ifdef USE_COLOR_CONVERT_MVA
                        {
                            OMX_U8 *ptr = static_cast<OMX_U8 *>(mem->pointer());
                            OMX_U32 pBuffer = ((reinterpret_cast<OMX_U32>(ptr)+(MTK_BUF_ADDR_ALIGNMENT_VALUE-1))&~(MTK_BUF_ADDR_ALIGNMENT_VALUE-1));

                            mM4UBufferVa[i] = (OMX_U32)pBuffer;
                            mM4UBufferSize[i] = (MTK_BUF_ADDR_ALIGNMENT_VALUE + mAlignedSize);
                            //mM4UBufferHdr[mM4UBufferCount] = (VAL_UINT32_T)(*ppBufferHdr);
                            #if 1
                            if(mACodecColorConverter)
                                if(OK!=mACodecColorConverter->getVideoAllocMVA(mACodecColorConverter, mM4UBufferHandle, mM4UBufferVa[i], &mM4UBufferPa[i], mM4UBufferSize[i], NULL))
                                {
                                    ALOGW("[ERROR][M4U][Mtk_getVideoAllocMVA]");
                                    //err = BAD_VALUE;
                                    //return err;
                                }
                            #else
                            if (0xffffffff == eVideoAllocMVA(mM4UBufferHandle, mM4UBufferVa[i], &mM4UBufferPa[i], mM4UBufferSize[i], NULL))
                            {
                                ALOGE("[ERROR][M4U][eVideoAllocMVA]");
                                err = BAD_VALUE;
                                return err;
                            }
                            #endif
                            ALOGD("[M4U][Output][UseBuffer] mM4UBufferVa = 0x%x, mM4UBufferPa = 0x%x, mM4UBufferSize = 0x%x, mM4UBufferCount = %d\n",
                                mM4UBufferVa[i], mM4UBufferPa[i], mM4UBufferSize[i], i);
                            mMVAParamDst.mM4UBufferVaA[i] = (OMX_U32)mM4UBufferVa[i];
                            mMVAParamDst.mM4UBufferPaB[i] = (OMX_U32)mM4UBufferPa[i];
                            mMVAParamDst.mOutputBufferPopulatedCnt = def.nBufferCountActual;
                            mM4UBufferCount++;
                        }
#endif //USE_COLOR_CONVERT_MVA
                    }
                    if (memforcolorconvert != NULL) {
                        //TODO: get alignment info from omx
                        OMX_U8 *ptr = static_cast<OMX_U8 *>(memforcolorconvert->pointer());
                        ALOGD("@debug: infoForOMX ptr(%p)", ptr);
                        OMX_U32 pBuffer = ((reinterpret_cast<OMX_U32>(ptr)+(MTK_BUF_ADDR_ALIGNMENT_VALUE-1))&~(MTK_BUF_ADDR_ALIGNMENT_VALUE-1));
                        infoForOMX.mData = new ABuffer((void*)pBuffer, (MTK_BUF_ADDR_ALIGNMENT_VALUE + mAlignedSize));
                        ALOGD("@debug: infoForOMX.Buffer[%d], info[%p], d: %p, index %d, OMX.mBufferID(%p)", i, &infoForOMX, infoForOMX.mData->data(), index, infoForOMX.mBufferID);
                        //mMVAParamSrc.mM4UBufferPaC[i] = (OMX_U32)infoForOMX.mData->data();
                    }
#endif //USE_VIDEO_ION
                    mBuffers[portIndex].push(info);
                    mBuffersForColorConvert[kPortIndexOutputForColorConvert].push(infoForOMX);
                }
                else
                {
                    if (mem != NULL) {
                        //TODO: get alignment info from omx

#ifdef ENABLE_MTK_BUF_ADDR_ALIGNMENT
                        OMX_U8 *ptr = static_cast<OMX_U8 *>(mem->pointer());
                        OMX_U32 pBuffer = ((reinterpret_cast<OMX_U32>(ptr)+(MTK_BUF_ADDR_ALIGNMENT_VALUE-1))&~(MTK_BUF_ADDR_ALIGNMENT_VALUE-1));
                        info.mData = new ABuffer((void*)pBuffer, def.nBufferSize);
                        ALOGD("@debug: Buffer[%d], %p(%p)", i, info.mData->data(), ptr);
#else //ENABLE_MTK_BUF_ADDR_ALIGNMENT
                        info.mData = new ABuffer(mem->pointer(), def.nBufferSize);
#endif //ENABLE_MTK_BUF_ADDR_ALIGNMENT
                    }

#ifdef USE_VIDEO_ION  // Morris Yang 20130709 ION
                    if (mIsVideoDecoder || mIsVideoEncoder) {
                        info.mData = new ABuffer(pIonBuffer, def.nBufferSize);
                    }
#endif //USE_VIDEO_ION
                    mBuffers[portIndex].push(info);
                }
#else
                if (mem != NULL) {
#ifndef ANDROID_DEFAULT_CODE
                    //TODO: get alignment info from omx
#ifdef ENABLE_MTK_BUF_ADDR_ALIGNMENT
                    OMX_U8 *ptr = static_cast<OMX_U8 *>(mem->pointer());
                    OMX_U32 pBuffer = ((reinterpret_cast<OMX_U32>(ptr)+(MTK_BUF_ADDR_ALIGNMENT_VALUE-1))&~(MTK_BUF_ADDR_ALIGNMENT_VALUE-1));
                    info.mData = new ABuffer((void*)pBuffer, def.nBufferSize);
                    ALOGD("@debug: Buffer[%d], %p(%p)", i, info.mData->data(), ptr);
#else
                    info.mData = new ABuffer(mem->pointer(), def.nBufferSize);
#endif //ENABLE_MTK_BUF_ADDR_ALIGNMENT
#else
                    info.mData = new ABuffer(mem->pointer(), def.nBufferSize);
#endif //ANDROID_DEFAULT_CODE
                }

#ifdef USE_VIDEO_ION  // Morris Yang 20130709 ION
                if (mIsVideoDecoder || mIsVideoEncoder) {
                    info.mData = new ABuffer(pIonBuffer, def.nBufferSize);
                }
#endif
                mBuffers[portIndex].push(info);
#endif //ANDROID_DEFAULT_CODE
            }
        }
    }
#ifndef ANDROID_DEFAULT_CODE
    /*{
        if((portIndex == kPortIndexOutput) && (!strncmp("OMX.MTK.VIDEO.DECODER.", mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mIsDemandNormalYUV==true))
        {
            //memset(&mMVAParamSrc, 0x0, sizeof(mMVAParamSrc));
            err = mOMX->getParameter(
                    mNode, OMX_IndexVendorMtkOmxVdecACodecColorConvertGetMVAAddr, &mMVAParamSrc, sizeof(mMVAParamSrc));
            ALOGD("@debug: mMVAParamSrc %d, %d", mMVAParamSrc.mM4UBufferCount, mMVAParamSrc.mOutputBufferPopulatedCnt);
            mMVAParamDst.mOutputBufferPopulatedCnt = mMVAParamSrc.mOutputBufferPopulatedCnt;
            for(int mcounter=0; mcounter<VIDEO_M4U_MAX_BUFFER; mcounter++)
            {
                if(mMVAParamSrc.mM4UBufferPaA[mcounter]!=0)
                {
                    ALOGD("@debug: GetMVAAddr mM4UBufferPaA[%d](0x%x) mM4UBufferPaB:(0x%x), mM4UBufferPaC:(0x%x)", 
                        mcounter, mMVAParamSrc.mM4UBufferPaA[mcounter], mMVAParamSrc.mM4UBufferPaB[mcounter], mMVAParamSrc.mM4UBufferPaC[mcounter]);
                }
            }
        }
    }*/
#endif //ANDROID_DEFAULT_CODE
    if (err != OK) {
        return err;
    }

    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", ACodec::kWhatBuffersAllocated);

    notify->setInt32("portIndex", portIndex);

    sp<PortDescription> desc = new PortDescription;

    for (size_t i = 0; i < mBuffers[portIndex].size(); ++i) {
        const BufferInfo &info = mBuffers[portIndex][i];

        desc->addBuffer(info.mBufferID, info.mData);
    }

    notify->setObject("portDesc", desc);
    notify->post();

    return OK;
}
status_t ACodec::configureOutputBuffersFromNativeWindow(
        OMX_U32 *bufferCount, OMX_U32 *bufferSize,
        OMX_U32 *minUndequeuedBuffers) {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

#ifndef ANDROID_DEFAULT_CODE
    if (!strncmp("OMX.MTK.", mComponentName.c_str(), 8)) {
        uint32_t eHalColorFormat = HAL_PIXEL_FORMAT_YV12;//tmp for build pass
        switch (def.format.video.eColorFormat) {
            case OMX_COLOR_FormatYUV420Planar:
#ifdef MTK_CLEARMOTION_SUPPORT
                eHalColorFormat = HAL_PIXEL_FORMAT_YUV_PRIVATE;
                ALOGD ("[MJC][OMX_COLOR_FormatYUV420Planar] eHalColorFormat = HAL_PIXEL_FORMAT_YUV_PRIVATE;");
#else                
                //eHalColorFormat = HAL_PIXEL_FORMAT_YV12;
                eHalColorFormat = HAL_PIXEL_FORMAT_I420;
#endif
                break;
            case OMX_MTK_COLOR_FormatYV12:
#ifdef MTK_CLEARMOTION_SUPPORT
                eHalColorFormat = HAL_PIXEL_FORMAT_YUV_PRIVATE;
                ALOGD ("[MJC][OMX_MTK_COLOR_FormatYV12] eHalColorFormat = HAL_PIXEL_FORMAT_YUV_PRIVATE;");
#else                   
                eHalColorFormat = HAL_PIXEL_FORMAT_YV12;
#endif
                break;
            case OMX_COLOR_FormatVendorMTKYUV:
#ifdef MTK_CLEARMOTION_SUPPORT
                eHalColorFormat = HAL_PIXEL_FORMAT_YUV_PRIVATE;
                ALOGD ("[MJC][OMX_COLOR_FormatVendorMTKYUV] eHalColorFormat = HAL_PIXEL_FORMAT_YUV_PRIVATE;");
#else                                
                eHalColorFormat = HAL_PIXEL_FORMAT_NV12_BLK;
#endif
                break;
            default:
#ifdef MTK_CLEARMOTION_SUPPORT
                eHalColorFormat = HAL_PIXEL_FORMAT_YUV_PRIVATE;
                ALOGD ("[MJC][default] eHalColorFormat = HAL_PIXEL_FORMAT_YUV_PRIVATE;");
#else                   
                //eHalColorFormat = HAL_PIXEL_FORMAT_YV12;
                eHalColorFormat = HAL_PIXEL_FORMAT_I420;
#endif
                break;           
        }
        err = native_window_set_buffers_geometry(
                mNativeWindow.get(),
                def.format.video.nStride,
                def.format.video.nSliceHeight,
                eHalColorFormat);

        ALOGD ("native_window_set_buffers_geometry err(%x), W(%d), H(%d), Stride(%d), SliceH(%d)", err, def.format.video.nFrameWidth, def.format.video.nFrameHeight, def.format.video.nStride, def.format.video.nSliceHeight);

    } else {
#endif

        ALOGD ("native_window_set_buffers_geometry W(%d), H(%d), %d", (int)def.format.video.nFrameWidth, (int)def.format.video.nFrameHeight, (int)def.format.video.eColorFormat);
    err = native_window_set_buffers_geometry(
            mNativeWindow.get(),
            def.format.video.nFrameWidth,
            def.format.video.nFrameHeight,
            def.format.video.eColorFormat);
#ifndef ANDROID_DEFAULT_CODE
    }
#endif

    if (err != 0) {
        ALOGE("native_window_set_buffers_geometry failed: %s (%d)",
                strerror(-err), -err);
        return err;
    }

    // Set up the native window.
    OMX_U32 usage = 0;
    err = mOMX->getGraphicBufferUsage(mNode, kPortIndexOutput, &usage);
    if (err != 0) {
        ALOGW("querying usage flags from OMX IL component failed: %d", err);
        // XXX: Currently this error is logged, but not fatal.
        usage = 0;
    }

    if (mFlags & kFlagIsSecure) {
        usage |= GRALLOC_USAGE_PROTECTED;
    }

#ifndef ANDROID_DEFAULT_CODE

    if (mFlags & kFlagIsProtect) {
        usage |= GRALLOC_USAGE_PROTECTED;
        ALOGD("mFlags & kFlagIsProtect: %d, usage %x", kFlagIsProtect, usage);
    }

#ifdef MTK_SEC_VIDEO_PATH_SUPPORT
    /* 
        use secure buffer for secure video path
        Note:
            1. GTS1.3 and WVL3 case, kFlagIsSecure will not use.
    */
	if (mFlags & kFlagIsSecure) {
		usage |=  GRALLOC_USAGE_SECURE;
        ALOGW("ACODEC: use GRALLOC_USAGE_SECURE\n");				
	}
#endif	
#endif

#ifndef ANDROID_DEFAULT_CODE
    usage |= (GRALLOC_USAGE_SW_WRITE_OFTEN | GRALLOC_USAGE_SW_READ_OFTEN);
#endif //ANDROID_DEFAULT_CODE

    // Make sure to check whether either Stagefright or the video decoder
    // requested protected buffers.
    if (usage & GRALLOC_USAGE_PROTECTED) {
        // Verify that the ANativeWindow sends images directly to
        // SurfaceFlinger.
        int queuesToNativeWindow = 0;
        err = mNativeWindow->query(
                mNativeWindow.get(), NATIVE_WINDOW_QUEUES_TO_WINDOW_COMPOSER,
                &queuesToNativeWindow);
        if (err != 0) {
            ALOGE("error authenticating native window: %d", err);
            return err;
        }
        if (queuesToNativeWindow != 1) {
            ALOGE("native window could not be authenticated");
            return PERMISSION_DENIED;
        }
    }

    err = native_window_set_usage(
            mNativeWindow.get(),
            usage | GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_EXTERNAL_DISP);

    if (err != 0) {
        ALOGE("native_window_set_usage failed: %s (%d)", strerror(-err), -err);
        return err;
    }
    *minUndequeuedBuffers = 0;
    err = mNativeWindow->query(
            mNativeWindow.get(), NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS,
            (int *)minUndequeuedBuffers);

    if (err != 0) {
        ALOGE("NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS query failed: %s (%d)",
                strerror(-err), -err);
        return err;
    }

#ifndef ANDROID_DEFAULT_CODE
    //(*minUndequeuedBuffers) += 1;  // TODO: REMOVE ME, temp workaround for ALPS01309644
#ifdef MTK_CLEARMOTION_SUPPORT
    int32_t mUseClearMotionMode = 0;
    {
        mOMX->setParameter(mNode, OMX_IndexVendorMtkOmxVdecGetMinUndequeuedBufs, minUndequeuedBuffers, sizeof(void*));
    }
#endif
#endif

    // XXX: Is this the right logic to use?  It's not clear to me what the OMX
    // buffer counts refer to - how do they account for the renderer holding on
    // to buffers?
    if (def.nBufferCountActual < def.nBufferCountMin + *minUndequeuedBuffers) {
        OMX_U32 newBufferCount = def.nBufferCountMin + *minUndequeuedBuffers;
        def.nBufferCountActual = newBufferCount;
        err = mOMX->setParameter(
                mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

        if (err != OK) {
            ALOGE("[%s] setting nBufferCountActual to %lu failed: %d",
                    mComponentName.c_str(), newBufferCount, err);
            return err;
        }
    }

    err = native_window_set_buffer_count(
            mNativeWindow.get(), def.nBufferCountActual);

    if (err != 0) {
        ALOGE("native_window_set_buffer_count failed: %s (%d)", strerror(-err),
                -err);
        return err;
    }
    *bufferCount = def.nBufferCountActual;
    *bufferSize =  def.nBufferSize;
    return err;
}

status_t ACodec::allocateOutputBuffersFromNativeWindow() {
    OMX_U32 bufferCount, bufferSize, minUndequeuedBuffers;
    status_t err = configureOutputBuffersFromNativeWindow(
            &bufferCount, &bufferSize, &minUndequeuedBuffers);
    if (err != 0)
        return err;

    ALOGV("[%s] Allocating %lu buffers from a native window of size %lu on "
         "output port, minUndequeuedBuffers %d",
         mComponentName.c_str(), bufferCount, bufferSize, minUndequeuedBuffers);

    // Dequeue buffers and send them to OMX
    for (OMX_U32 i = 0; i < bufferCount; i++) {
        ANativeWindowBuffer *buf;
        err = native_window_dequeue_buffer_and_wait(mNativeWindow.get(), &buf);
        if (err != 0) {
            ALOGE("dequeueBuffer failed: %s (%d)", strerror(-err), -err);
            break;
        }

        sp<GraphicBuffer> graphicBuffer(new GraphicBuffer(buf, false));
        BufferInfo info;
        info.mStatus = BufferInfo::OWNED_BY_US;
        info.mData = new ABuffer(NULL /* data */, bufferSize /* capacity */);
        info.mGraphicBuffer = graphicBuffer;
        mBuffers[kPortIndexOutput].push(info);

        IOMX::buffer_id bufferId;
        err = mOMX->useGraphicBuffer(mNode, kPortIndexOutput, graphicBuffer,
                &bufferId);
        if (err != 0) {
            ALOGE("registering GraphicBuffer %lu with OMX IL component failed: "
                 "%d", i, err);
            break;
        }

        mBuffers[kPortIndexOutput].editItemAt(i).mBufferID = bufferId;

        ALOGV("[%s] Registered graphic buffer with ID %p (pointer = %p)",
             mComponentName.c_str(),
             bufferId, graphicBuffer.get());
    }

    OMX_U32 cancelStart;
    OMX_U32 cancelEnd;

    if (err != 0) {
        // If an error occurred while dequeuing we need to cancel any buffers
        // that were dequeued.
        cancelStart = 0;
        cancelEnd = mBuffers[kPortIndexOutput].size();
    } else {
        // Return the required minimum undequeued buffers to the native window.
        cancelStart = bufferCount - minUndequeuedBuffers;
        cancelEnd = bufferCount;
    }

    for (OMX_U32 i = cancelStart; i < cancelEnd; i++) {
        BufferInfo *info = &mBuffers[kPortIndexOutput].editItemAt(i);
        cancelBufferToNativeWindow(info);
    }

    return err;
}

status_t ACodec::allocateOutputMetaDataBuffers() {
    OMX_U32 bufferCount, bufferSize, minUndequeuedBuffers;
    status_t err = configureOutputBuffersFromNativeWindow(
            &bufferCount, &bufferSize, &minUndequeuedBuffers);
    if (err != 0)
        return err;

    ALOGD("[%s] Allocating %lu meta buffers on output port",
         mComponentName.c_str(), bufferCount);

    size_t totalSize = bufferCount * 8;
    mDealer[kPortIndexOutput] = new MemoryDealer(totalSize, "ACodec");

    // Dequeue buffers and send them to OMX
    for (OMX_U32 i = 0; i < bufferCount; i++) {
        BufferInfo info;
        info.mStatus = BufferInfo::OWNED_BY_NATIVE_WINDOW;
        info.mGraphicBuffer = NULL;
        info.mDequeuedAt = mDequeueCounter;

        sp<IMemory> mem = mDealer[kPortIndexOutput]->allocate(
                sizeof(struct VideoDecoderOutputMetaData));
        CHECK(mem.get() != NULL);
        info.mData = new ABuffer(mem->pointer(), mem->size());

        // we use useBuffer for metadata regardless of quirks
        err = mOMX->useBuffer(
                mNode, kPortIndexOutput, mem, &info.mBufferID);

        mBuffers[kPortIndexOutput].push(info);

        ALOGD("[%s] allocated meta buffer with ID %p (pointer = %p)",
             mComponentName.c_str(), info.mBufferID, mem->pointer());
    }

    mMetaDataBuffersToSubmit = bufferCount - minUndequeuedBuffers;
    return err;
}

status_t ACodec::submitOutputMetaDataBuffer() {
    CHECK(mStoreMetaDataInOutputBuffers);
    if (mMetaDataBuffersToSubmit == 0)
        return OK;

    BufferInfo *info = dequeueBufferFromNativeWindow();
    if (info == NULL)
        return ERROR_IO;

    ALOGD("[%s] submitting output meta buffer ID %p for graphic buffer %p",
          mComponentName.c_str(), info->mBufferID, info->mGraphicBuffer.get());

    --mMetaDataBuffersToSubmit;
    CHECK_EQ(mOMX->fillBuffer(mNode, info->mBufferID),
             (status_t)OK);

    info->mStatus = BufferInfo::OWNED_BY_COMPONENT;
    return OK;
}

status_t ACodec::cancelBufferToNativeWindow(BufferInfo *info) {
    CHECK_EQ((int)info->mStatus, (int)BufferInfo::OWNED_BY_US);

    ALOGD("[%s] Calling cancelBuffer on buffer %p",
         mComponentName.c_str(), info->mBufferID);

    int err = mNativeWindow->cancelBuffer(
        mNativeWindow.get(), info->mGraphicBuffer.get(), -1);

#ifndef ANDROID_DEFAULT_CODE
    if (err != 0) {
        LOGE("failed to cancel buffer from native window: %p, err = %d", mNativeWindow.get(), err);
        info->mStatus = BufferInfo::OWNED_BY_UNEXPECTED;
    } else {
#endif
    CHECK_EQ(err, 0);

    info->mStatus = BufferInfo::OWNED_BY_NATIVE_WINDOW;

#ifndef ANDROID_DEFAULT_CODE
    }
#endif
    return OK;
}

ACodec::BufferInfo *ACodec::dequeueBufferFromNativeWindow() {
    ANativeWindowBuffer *buf;
    int fenceFd = -1;
    CHECK(mNativeWindow.get() != NULL);
    if (native_window_dequeue_buffer_and_wait(mNativeWindow.get(), &buf) != 0) {
        ALOGE("dequeueBuffer failed.");
        return NULL;
    }
    BufferInfo *oldest = NULL;
    for (size_t i = mBuffers[kPortIndexOutput].size(); i-- > 0;) {
        BufferInfo *info =
            &mBuffers[kPortIndexOutput].editItemAt(i);
        if (info->mGraphicBuffer != NULL &&
            info->mGraphicBuffer->handle == buf->handle) {
            CHECK_EQ((int)info->mStatus,
                     (int)BufferInfo::OWNED_BY_NATIVE_WINDOW);

            info->mStatus = BufferInfo::OWNED_BY_US;

            return info;
        }
        if (info->mStatus == BufferInfo::OWNED_BY_NATIVE_WINDOW &&
            (oldest == NULL ||
             // avoid potential issues from counter rolling over
             mDequeueCounter - info->mDequeuedAt >
                    mDequeueCounter - oldest->mDequeuedAt)) {
            oldest = info;
        }
    }

    if (oldest) {
        CHECK(mStoreMetaDataInOutputBuffers);

        // discard buffer in LRU info and replace with new buffer
        oldest->mGraphicBuffer = new GraphicBuffer(buf, false);
        oldest->mStatus = BufferInfo::OWNED_BY_US;

        mOMX->updateGraphicBufferInMeta(
                mNode, kPortIndexOutput, oldest->mGraphicBuffer,
                oldest->mBufferID);

        VideoDecoderOutputMetaData *metaData =
            reinterpret_cast<VideoDecoderOutputMetaData *>(
                    oldest->mData->base());
        CHECK_EQ(metaData->eType, kMetadataBufferTypeGrallocSource);

        ALOGV("replaced oldest buffer #%u with age %u (%p/%p stored in %p)",
                oldest - &mBuffers[kPortIndexOutput][0],
                mDequeueCounter - oldest->mDequeuedAt,
                metaData->pHandle,
                oldest->mGraphicBuffer->handle, oldest->mData->base());

        return oldest;
    }

#ifndef ANDROID_DEFAULT_CODE
    ALOGI("dequeue buffer from native window (%p), but not matched in %d output buffers",
           mNativeWindow.get(), mBuffers[kPortIndexOutput].size(), mNativeWindow.get());
    int err = mNativeWindow->cancelBuffer(mNativeWindow.get(), buf, -1); 
    ALOGI("\t\tcancel this unexpected buffer from native window, err = %d", err);
#else
    TRESPASS();
#endif

    return NULL;
}

status_t ACodec::freeBuffersOnPort(OMX_U32 portIndex) {
    for (size_t i = mBuffers[portIndex].size(); i-- > 0;) {
        CHECK_EQ((status_t)OK, freeBuffer(portIndex, i));
    }
    ALOGI("freeBuffersOnPort");
#ifndef ANDROID_DEFAULT_CODE
    if((portIndex == kPortIndexOutput) && (!strncmp("OMX.MTK.VIDEO.DECODER.", mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mIsDemandNormalYUV==true))
    {

#ifdef USE_VIDEO_ION
        for (int i = 0 ; i < MAX_ION_BUF_COUNT ; i++) {
            if (mIonInputBufInfo[i].IonFd  != 0xFFFFFFFF) {
                 ion_munmap(mIonDevFd, (void*)mIonInputBufInfo[i].u4BufVA, mIonInputBufInfo[i].u4BufSize);    
                // free ION buffer fd
                if (ion_share_close (mIonDevFd, mIonInputBufInfo[i].IonFd)) {
                    ALOGE ("[ERROR] ion_share_close failed, LINE:%d", __LINE__);
                }
                else
                    mIonInputBufInfo[i].IonFd = 0xFFFFFFFF;

                // free ION buffer handle
                //MTK_OMX_LOGD ("@@ calling ion_free mIonDevFd(0x%08X)", mIonDevFd);
                if (ion_free(mIonDevFd, (struct ion_handle *)mIonInputBufInfo[i].u4IonBufHandle)) {
                    ALOGE ("[ERROR] ion_free failed in FreeBuffer, LINE:%d", __LINE__);
                }
                else
                    mIonInputBufInfo[i].u4IonBufHandle = 0xFFFFFFFF;
            }
        }

        for (int i = 0 ; i < MAX_ION_BUF_COUNT ; i++) {
            if (mIonOutputBufInfo[i].IonFd  != 0xFFFFFFFF) {
                 ion_munmap(mIonDevFd, (void*)mIonOutputBufInfo[i].u4BufVA, mIonOutputBufInfo[i].u4BufSize);    
                // free ION buffer fd
                if (ion_share_close (mIonDevFd, mIonOutputBufInfo[i].IonFd)) {
                    ALOGE ("[ERROR] ion_share_close failed, LINE:%d", __LINE__);
                }
                else
                    mIonOutputBufInfo[i].IonFd = 0xFFFFFFFF;

                // free ION buffer handle
                //MTK_OMX_LOGD ("@@ calling ion_free mIonDevFd(0x%08X)", mIonDevFd);
                if (ion_free(mIonDevFd, (struct ion_handle *)mIonOutputBufInfo[i].u4IonBufHandle)) {
                    ALOGE ("[ERROR] ion_free failed in FreeBuffer, LINE:%d", __LINE__);
                }
                else
                    mIonOutputBufInfo[i].u4IonBufHandle = 0xFFFFFFFF;
            }
        }

        for (int i = 0 ; i < MAX_ION_BUF_COUNT ; i++) {
            if (mCCIonBufInfo[i].IonFd  != 0xFFFFFFFF) {
                 ion_munmap(mCCIonDevFd, (void*)mCCIonBufInfo[i].u4BufVA, mCCIonBufInfo[i].u4BufSize);    
                // free ION buffer fd
                if (ion_share_close (mCCIonDevFd, mCCIonBufInfo[i].IonFd)) {
                    ALOGE ("[ERROR] ion_share_close failed, LINE:%d", __LINE__);
                }
                else
                    mCCIonBufInfo[i].IonFd = 0xFFFFFFFF;

                // free ION buffer handle
                //MTK_OMX_LOGD ("@@ calling ion_free mIonDevFd(0x%08X)", mIonDevFd);
                if (ion_free(mCCIonDevFd, (struct ion_handle *)mCCIonBufInfo[i].u4IonBufHandle)) {
                    ALOGE ("[ERROR] ion_free failed in FreeBuffer, LINE:%d", __LINE__);
                }
                else
                    mCCIonBufInfo[i].u4IonBufHandle = 0xFFFFFFFF;
            }
        }

        for(int i=0; i<mM4UBufferCount; i++)
        {
            mM4UBufferVa[i] = 0xffffffff;
            mM4UBufferPa[i] = 0xffffffff;
        }
        mM4UBufferCount = 0;
#else   //USE_VIDEO_ION
        mDealerForColorConvert[kPortIndexOutputForColorConvert].clear();
#ifdef USE_COLOR_CONVERT_MVA
        for(int i=0; i<mM4UBufferCount; i++)
        //if (mM4UBufferHdr[u4I] == (VAL_UINT32_T)pBuffHead)
        {
#if 1
            if(mACodecColorConverter)
                if(OK!=mACodecColorConverter->freeVideoAllocMVA(mACodecColorConverter, mM4UBufferHandle, mM4UBufferVa[i], mM4UBufferPa[i], mM4UBufferSize[i], NULL));
                {
                    ALOGE("[ERROR][M4U][Mtk_freeVideoAllocMVA]");
                } 
#else
            eVideoFreeMVA(mM4UBufferHandle, mM4UBufferVa[i], mM4UBufferPa[i], mM4UBufferSize[i], NULL);
#endif
            ALOGI("[M4U][Output][FreeBuffer] mM4UBufferVa = 0x%x, mM4UBufferPa = 0x%x, mM4UBufferSize = 0x%x, mM4UBufferCount = %d\n",
                mM4UBufferVa[i], mM4UBufferPa[i], mM4UBufferSize[i], i);
            //mM4UBufferHdr[i] = 0xffffffff;
            mM4UBufferVa[i] = 0xffffffff;
            mM4UBufferPa[i] = 0xffffffff;
        }
        mM4UBufferCount = 0;
#endif //USE_COLOR_CONVERT_MVA
#endif //USE_VIDEO_ION
        mDealer[portIndex].clear();

    }
    else
        mDealer[portIndex].clear();
#else //ANDROID_DEFAULT_CODE
    mDealer[portIndex].clear();
#endif //ANDROID_DEFAULT_CODE
    return OK;
}

status_t ACodec::freeOutputBuffersNotOwnedByComponent() {
    for (size_t i = mBuffers[kPortIndexOutput].size(); i-- > 0;) {
        BufferInfo *info =
            &mBuffers[kPortIndexOutput].editItemAt(i);

        // At this time some buffers may still be with the component
        // or being drained.
        if (info->mStatus != BufferInfo::OWNED_BY_COMPONENT &&
            info->mStatus != BufferInfo::OWNED_BY_DOWNSTREAM) {
            CHECK_EQ((status_t)OK, freeBuffer(kPortIndexOutput, i));
        }
    }

    return OK;
}

status_t ACodec::freeBuffer(OMX_U32 portIndex, size_t i) {
    BufferInfo *info = &mBuffers[portIndex].editItemAt(i);

    CHECK(info->mStatus == BufferInfo::OWNED_BY_US
            || info->mStatus == BufferInfo::OWNED_BY_NATIVE_WINDOW);

    if (portIndex == kPortIndexOutput && mNativeWindow != NULL
            && info->mStatus == BufferInfo::OWNED_BY_US) {
        CHECK_EQ((status_t)OK, cancelBufferToNativeWindow(info));
    }

    CHECK_EQ(mOMX->freeBuffer(
                mNode, portIndex, info->mBufferID),
             (status_t)OK);

    mBuffers[portIndex].removeAt(i);

    return OK;
}

ACodec::BufferInfo *ACodec::findBufferByID(
        uint32_t portIndex, IOMX::buffer_id bufferID,
        ssize_t *index) {
    for (size_t i = 0; i < mBuffers[portIndex].size(); ++i) {
        BufferInfo *info = &mBuffers[portIndex].editItemAt(i);

        if (info->mBufferID == bufferID) {
            if (index != NULL) {
                *index = i;
            }
            return info;
        }
    }

    TRESPASS();

    return NULL;
}

#ifndef ANDROID_DEFAULT_CODE
ACodec::BufferInfo *ACodec::findBufferByIDForColorConvert(
        uint32_t portIndex, IOMX::buffer_id bufferID,
        ssize_t *index) {
    for (size_t i = 0; i < mBuffersForColorConvert[portIndex].size(); ++i) {
        BufferInfo *info = &mBuffersForColorConvert[portIndex].editItemAt(i);

        if (info->mBufferID == bufferID) {
            if (index != NULL) {
                *index = i;
            }
            return info;
        }
    }

    TRESPASS();

    return NULL;
}
#endif //ANDROID_DEFAULT_CODE

status_t ACodec::setComponentRole(
        bool isEncoder, const char *mime) {
    struct MimeToRole {
        const char *mime;
        const char *decoderRole;
        const char *encoderRole;
    };

    static const MimeToRole kMimeToRole[] = {
        { MEDIA_MIMETYPE_AUDIO_MPEG,
            "audio_decoder.mp3", "audio_encoder.mp3" },
        { MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_I,
            "audio_decoder.mp1", "audio_encoder.mp1" },
        { MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_II,
            "audio_decoder.mp2", "audio_encoder.mp2" },
        { MEDIA_MIMETYPE_AUDIO_AMR_NB,
            "audio_decoder.amrnb", "audio_encoder.amrnb" },
        { MEDIA_MIMETYPE_AUDIO_AMR_WB,
            "audio_decoder.amrwb", "audio_encoder.amrwb" },
        { MEDIA_MIMETYPE_AUDIO_AAC,
            "audio_decoder.aac", "audio_encoder.aac" },
        { MEDIA_MIMETYPE_AUDIO_VORBIS,
            "audio_decoder.vorbis", "audio_encoder.vorbis" },
        { MEDIA_MIMETYPE_AUDIO_G711_MLAW,
            "audio_decoder.g711mlaw", "audio_encoder.g711mlaw" },
        { MEDIA_MIMETYPE_AUDIO_G711_ALAW,
            "audio_decoder.g711alaw", "audio_encoder.g711alaw" },
        { MEDIA_MIMETYPE_VIDEO_AVC,
            "video_decoder.avc", "video_encoder.avc" },
        { MEDIA_MIMETYPE_VIDEO_MPEG4,
            "video_decoder.mpeg4", "video_encoder.mpeg4" },
        { MEDIA_MIMETYPE_VIDEO_H263,
            "video_decoder.h263", "video_encoder.h263" },
        { MEDIA_MIMETYPE_VIDEO_VP8,
            "video_decoder.vp8", "video_encoder.vp8" },
        { MEDIA_MIMETYPE_VIDEO_VP9,
            "video_decoder.vp9", "video_encoder.vp9" },
#ifdef MTK_VIDEO_HEVC_SUPPORT
        { MEDIA_MIMETYPE_VIDEO_HEVC,
            "video_decoder.hevc", "video_encoder.hevc" },
#endif //MTK_VIDEO_HEVC_SUPPORT
        { MEDIA_MIMETYPE_AUDIO_RAW,
            "audio_decoder.raw", "audio_encoder.raw" },
        { MEDIA_MIMETYPE_AUDIO_FLAC,
            "audio_decoder.flac", "audio_encoder.flac" },
        { MEDIA_MIMETYPE_AUDIO_MSGSM,
            "audio_decoder.gsm", "audio_encoder.gsm" },
#ifdef MTK_AUDIO_DDPLUS_SUPPORT
        { MEDIA_MIMETYPE_AUDIO_AC3,
            "audio_decoder.ac3", "audio_encoder.ac3" },
        { MEDIA_MIMETYPE_AUDIO_EC3,
            "audio_decoder.ec3", "audio_encoder.ec3" },
#endif
    };

    static const size_t kNumMimeToRole =
        sizeof(kMimeToRole) / sizeof(kMimeToRole[0]);

    size_t i;
    for (i = 0; i < kNumMimeToRole; ++i) {
        if (!strcasecmp(mime, kMimeToRole[i].mime)) {
            break;
        }
    }

    if (i == kNumMimeToRole) {
        return ERROR_UNSUPPORTED;
    }

    const char *role =
        isEncoder ? kMimeToRole[i].encoderRole
                  : kMimeToRole[i].decoderRole;

    if (role != NULL) {
        OMX_PARAM_COMPONENTROLETYPE roleParams;
        InitOMXParams(&roleParams);

        strncpy((char *)roleParams.cRole,
                role, OMX_MAX_STRINGNAME_SIZE - 1);

        roleParams.cRole[OMX_MAX_STRINGNAME_SIZE - 1] = '\0';

        status_t err = mOMX->setParameter(
                mNode, OMX_IndexParamStandardComponentRole,
                &roleParams, sizeof(roleParams));

        if (err != OK) {
            ALOGW("[%s] Failed to set standard component role '%s'.",
                 mComponentName.c_str(), role);

            return err;
        }
    }

    return OK;
}

status_t ACodec::configureCodec(
        const char *mime, const sp<AMessage> &msg) {
    int32_t encoder;
    if (!msg->findInt32("encoder", &encoder)) {
        encoder = false;
    }

    mIsEncoder = encoder;

#ifndef ANDROID_DEFAULT_CODE
    int32_t demandNormalYUV;
    if (!msg->findInt32("outputNormalYUV", &demandNormalYUV)) {
        demandNormalYUV = false;
    }
    mIsDemandNormalYUV = demandNormalYUV;
#endif //ANDROID_DEFAULT_CODE

    status_t err = setComponentRole(encoder /* isEncoder */, mime);
    ALOGD("setComponentRole err %x", err);
    if (err != OK) {
        return err;
    }

    int32_t bitRate = 0;
    // FLAC encoder doesn't need a bitrate, other encoders do
    if (encoder && strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_FLAC)
            && !msg->findInt32("bitrate", &bitRate)) {
        return INVALID_OPERATION;
    }

    int32_t storeMeta;
    if (encoder
            && msg->findInt32("store-metadata-in-buffers", &storeMeta)
            && storeMeta != 0) {
        err = mOMX->storeMetaDataInBuffers(mNode, kPortIndexInput, OMX_TRUE);
        if (err != OK) {
              ALOGE("[%s] storeMetaDataInBuffers (input) failed w/ err %x",
                    mComponentName.c_str(), err);

              return err;
          }
      }

    int32_t prependSPSPPS = 0;
    if (encoder
            && msg->findInt32("prepend-sps-pps-to-idr-frames", &prependSPSPPS)
            && prependSPSPPS != 0) {
        OMX_INDEXTYPE index;
        err = mOMX->getExtensionIndex(
                mNode,
                "OMX.google.android.index.prependSPSPPSToIDRFrames",
                &index);

        if (err == OK) {
            PrependSPSPPSToIDRFramesParams params;
            InitOMXParams(&params);
            params.bEnable = OMX_TRUE;

            err = mOMX->setParameter(
                    mNode, index, &params, sizeof(params));
        }

        if (err != OK) {
            ALOGE("Encoder could not be configured to emit SPS/PPS before "
                  "IDR frames. (err %d)", err);

            return err;
        }
    }
    // Only enable metadata mode on encoder output if encoder can prepend
    // sps/pps to idr frames, since in metadata mode the bitstream is in an
    // opaque handle, to which we don't have access.
    int32_t video = !strncasecmp(mime, "video/", 6);
    if (encoder && video) {
        OMX_BOOL enable = (OMX_BOOL) (prependSPSPPS
            && msg->findInt32("store-metadata-in-buffers-output", &storeMeta)
            && storeMeta != 0);

        err = mOMX->storeMetaDataInBuffers(mNode, kPortIndexOutput, enable);

        if (err != OK) {
            ALOGE("[%s] storeMetaDataInBuffers (output) failed w/ err %d",
                mComponentName.c_str(), err);
            mUseMetadataOnEncoderOutput = 0;
        } else {
            mUseMetadataOnEncoderOutput = enable;
        }

        if (!msg->findInt64(
                    "repeat-previous-frame-after",
                    &mRepeatFrameDelayUs)) {
            mRepeatFrameDelayUs = -1ll;
        }
    }

    // Always try to enable dynamic output buffers on native surface
    sp<RefBase> obj;
    int32_t haveNativeWindow = msg->findObject("native-window", &obj) &&
            obj != NULL;
    mStoreMetaDataInOutputBuffers = false;
    if (!encoder && video && haveNativeWindow) {
        err = mOMX->storeMetaDataInBuffers(mNode, kPortIndexOutput, OMX_TRUE);
        if (err != OK) {
            ALOGE("[%s] storeMetaDataInBuffers failed w/ err %x",
                  mComponentName.c_str(), err);

            // if adaptive playback has been requested, try JB fallback
            // NOTE: THIS FALLBACK MECHANISM WILL BE REMOVED DUE TO ITS
            // LARGE MEMORY REQUIREMENT

            // we will not do adaptive playback on software accessed
            // surfaces as they never had to respond to changes in the
            // crop window, and we don't trust that they will be able to.
            int usageBits = 0;
            bool canDoAdaptivePlayback;

            sp<NativeWindowWrapper> windowWrapper(
                    static_cast<NativeWindowWrapper *>(obj.get()));
            sp<ANativeWindow> nativeWindow = windowWrapper->getNativeWindow();

            if (nativeWindow->query(
                    nativeWindow.get(),
                    NATIVE_WINDOW_CONSUMER_USAGE_BITS,
                    &usageBits) != OK) {
                canDoAdaptivePlayback = false;
            } else {
                canDoAdaptivePlayback =
                    (usageBits &
                            (GRALLOC_USAGE_SW_READ_MASK |
                             GRALLOC_USAGE_SW_WRITE_MASK)) == 0;
            }

            int32_t maxWidth = 0, maxHeight = 0;
            if (canDoAdaptivePlayback &&
                msg->findInt32("max-width", &maxWidth) &&
                msg->findInt32("max-height", &maxHeight)) {
                ALOGD("[%s] prepareForAdaptivePlayback(%ldx%ld)",
                      mComponentName.c_str(), maxWidth, maxHeight);

                err = mOMX->prepareForAdaptivePlayback(
                        mNode, kPortIndexOutput, OMX_TRUE, maxWidth, maxHeight);
                ALOGW_IF(err != OK,
                        "[%s] prepareForAdaptivePlayback failed w/ err %d",
                        mComponentName.c_str(), err);
            }
            // allow failure
            err = OK;
        } else {
            ALOGV("[%s] storeMetaDataInBuffers succeeded", mComponentName.c_str());
            mStoreMetaDataInOutputBuffers = true;
        }

        int32_t push;
        if (msg->findInt32("push-blank-buffers-on-shutdown", &push)
                && push != 0) {
            mFlags |= kFlagPushBlankBuffersToNativeWindowOnShutdown;
        }
    }
    if (video) {
        if (encoder) {
            err = setupVideoEncoder(mime, msg);
        } else {
            int32_t width, height;
            if (!msg->findInt32("width", &width)
                    || !msg->findInt32("height", &height)) {
                err = INVALID_OPERATION;
            } else {
                err = setupVideoDecoder(mime, width, height);
            }

#ifndef ANDROID_DEFAULT_CODE
            // Get the color converter instance only in video dec
            int decoderOutput = 0;
            mACodecColorConverter = new I420ColorConverter;
            if (mACodecColorConverter->isLoaded()) {
                decoderOutput = mACodecColorConverter->getDecoderOutputFormat();
            }
            else
                mACodecColorConverter = NULL;
        
            if (decoderOutput == OMX_COLOR_FormatYUV420Planar) {
                delete mACodecColorConverter;
                mACodecColorConverter = NULL;
            }
        
            ALOGI("decoder err %x, output format = 0x%X, width %d, height %d\n", err, decoderOutput, width, height);
            if(mACodecColorConverter)
            {
    
                for (mM4UBufferCount = 0; mM4UBufferCount < VIDEO_M4U_MAX_BUFFER; mM4UBufferCount++)
                {
                    mM4UBufferSize[mM4UBufferCount] = 0xffffffff;
                    mM4UBufferVa[mM4UBufferCount] = 0xffffffff; 
                    mM4UBufferPa[mM4UBufferCount] = 0xffffffff;
                    mM4UBufferHdr[mM4UBufferCount] = 0xffffffff;
                }
                mM4UBufferCount = 0;
#ifdef USE_VIDEO_ION
                mACodecColorConverter->mMtkColorConvertInfo->mEnableION = OMX_TRUE;
                mACodecColorConverter->mMtkColorConvertInfo->mDstFormat = eYUV_420_3P;
#else
                mACodecColorConverter->mMtkColorConvertInfo->mEnableION = OMX_FALSE;
                mACodecColorConverter->mMtkColorConvertInfo->mDstFormat = eYUV_420_3P;
#endif //USE_VIDEO_ION
                //MVA mode move to init function
#ifdef USE_COLOR_CONVERT_MVA
                mACodecColorConverter->initACodecColorConverter(mACodecColorConverter, (void**)&mM4UBufferHandle, 1);
#else
                mACodecColorConverter->initACodecColorConverter(mACodecColorConverter, (void**)&mM4UBufferHandle, 0);
#endif //USE_COLOR_CONVERT_MVA
            }
#endif //ANDROID_DEFAULT_CODE
        }// if encoder
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG)) {
        int32_t numChannels, sampleRate;
        if (!msg->findInt32("channel-count", &numChannels)
                || !msg->findInt32("sample-rate", &sampleRate)) {
            // Since we did not always check for these, leave them optional
            // and have the decoder figure it all out.
            err = OK;
        } else {
            err = setupRawAudioFormat(
                    encoder ? kPortIndexInput : kPortIndexOutput,
                    sampleRate,
                    numChannels);
        }
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AAC)) {
        int32_t numChannels, sampleRate;
        if (!msg->findInt32("channel-count", &numChannels)
                || !msg->findInt32("sample-rate", &sampleRate)) {
            err = INVALID_OPERATION;
        } else {
            int32_t isADTS, aacProfile;
            if (!msg->findInt32("is-adts", &isADTS)) {
                isADTS = 0;
            }
            if (!msg->findInt32("aac-profile", &aacProfile)) {
                aacProfile = OMX_AUDIO_AACObjectNull;
            }
#ifndef ANDROID_DEFAULT_CODE
            if (!msg->findInt32("bitrate", &bitRate)) {
                bitRate = 0;
                ALOGE("cannot find aac bit rate");
            }
#endif //ANDROID_DEFAULT_CODE
            err = setupAACCodec(
                    encoder, numChannels, sampleRate, bitRate, aacProfile,
                    isADTS != 0);
        }
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AMR_NB)) {
        err = setupAMRCodec(encoder, false /* isWAMR */, bitRate);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AMR_WB)) {
        err = setupAMRCodec(encoder, true /* isWAMR */, bitRate);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_G711_ALAW)
            || !strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_G711_MLAW)) {
        // These are PCM-like formats with a fixed sample rate but
        // a variable number of channels.

        int32_t numChannels;
        if (!msg->findInt32("channel-count", &numChannels)) {
            err = INVALID_OPERATION;
        } else {
            err = setupG711Codec(encoder, numChannels);
        }
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_FLAC)) {
        int32_t numChannels, sampleRate, compressionLevel = -1;
        if (encoder &&
                (!msg->findInt32("channel-count", &numChannels)
                        || !msg->findInt32("sample-rate", &sampleRate))) {
            ALOGE("missing channel count or sample rate for FLAC encoder");
            err = INVALID_OPERATION;
        } else {
            if (encoder) {
                if (!msg->findInt32(
                            "flac-compression-level", &compressionLevel)) {
                    compressionLevel = 5;// default FLAC compression level
                } else if (compressionLevel < 0) {
                    ALOGW("compression level %d outside [0..8] range, "
                          "using 0",
                          compressionLevel);
                    compressionLevel = 0;
                } else if (compressionLevel > 8) {
                    ALOGW("compression level %d outside [0..8] range, "
                          "using 8",
                          compressionLevel);
                    compressionLevel = 8;
                }
            }
#ifndef ANDROID_DEFAULT_CODE            
            else
            {
                sp<ABuffer> buffer;
                if(msg->findBuffer("flacinfo", &buffer))
                {
                    ALOGW("acodec buffer size, %d", buffer->size()); ///buffer->data();
                    uint32_t type;        
                    typedef struct {
                        unsigned min_blocksize, max_blocksize;
                        unsigned min_framesize, max_framesize;
                        unsigned sample_rate;
                        unsigned channels;
                        unsigned bits_per_sample;
                        uint64_t total_samples;
                        unsigned char md5sum[16];
                        unsigned int mMaxBufferSize;
                        bool      has_stream_info;
                    } FLAC__StreamMetadata_Info_;
                    FLAC__StreamMetadata_Info_ data;
                    memcpy(&data, buffer->data(), buffer->size());

                    OMX_AUDIO_PARAM_FLACTYPE profile;
                    InitOMXParams(&profile);
                    profile.nPortIndex = OMX_DirInput;

                    status_t err = mOMX->getParameter(
                                            mNode, OMX_IndexParamAudioFlac, &profile, sizeof(profile));
                    CHECK_EQ((status_t)OK, err);

                    profile.channel_assignment =  OMX_AUDIO_FLAC__CHANNEL_ASSIGNMENT_LEFT_SIDE;
                    profile.total_samples = data.total_samples;     
                    profile.min_framesize = data.min_framesize;    
                    profile.max_framesize = data.max_framesize;        
                    profile.nSampleRate = data.sample_rate;           
                    profile.min_blocksize = data.min_blocksize;
                    profile.max_blocksize = data.max_blocksize;        
                    profile.nChannels = data.channels;    
                    profile.bits_per_sample = data.bits_per_sample;   
                    memcpy(profile.md5sum, data.md5sum, 16*sizeof(OMX_U8)); 

                    if(data.has_stream_info == true)
                        profile.has_stream_info = OMX_TRUE;
                    else
                        profile.has_stream_info = OMX_FALSE;
                   

                    ALOGD("kKeyFlacMetaInfo = %lld, %d, %d, %d, %d, %d, %d, %d",profile.total_samples, profile.min_framesize, profile.max_framesize, 
                    profile.nSampleRate, profile.min_blocksize, profile.max_blocksize, profile.nChannels, profile.bits_per_sample);
                    err = mOMX->setParameter(
                                    mNode, OMX_IndexParamAudioFlac, &profile, sizeof(profile));
                                    OMX_PARAM_PORTDEFINITIONTYPE def;
                                    InitOMXParams(&def);
                    def.nPortIndex = OMX_DirInput;

                    err = mOMX->getParameter(
                    mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
                    CHECK_EQ((status_t)OK, err);

                    def.nBufferSize =profile.max_framesize+16;;
                    err = mOMX->setParameter(
                    mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

                    OMX_PARAM_PORTDEFINITIONTYPE outputdef;
                    InitOMXParams(&outputdef);
                    outputdef.nPortIndex = OMX_DirOutput;

                    err = mOMX->getParameter(
                    mNode, OMX_IndexParamPortDefinition, &outputdef, sizeof(outputdef));
                    CHECK_EQ((status_t)OK, err);

                    if(profile.bits_per_sample/8 < 2)       //default output 16 bit pcm.
                        outputdef.nBufferSize = profile.max_blocksize * profile.nChannels * 2;
                    else
                        outputdef.nBufferSize = profile.max_blocksize * profile.nChannels * profile.bits_per_sample/8;
                    err = mOMX->setParameter(
                                    mNode, OMX_IndexParamPortDefinition, &outputdef, sizeof(outputdef));
                }
                
            }
#endif         
            err = setupFlacCodec(encoder, numChannels, sampleRate, compressionLevel);
        }
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_RAW)) {
        int32_t numChannels, sampleRate;
        if (encoder
                || !msg->findInt32("channel-count", &numChannels)
                || !msg->findInt32("sample-rate", &sampleRate)) {
            err = INVALID_OPERATION;
        } else {
            err = setupRawAudioFormat(kPortIndexInput, sampleRate, numChannels);
        }
    }

    if (err != OK) {
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("     err %d ", err);
#endif //ANDROID_DEFAULT_CODE
        return err;
    }

    if (!msg->findInt32("encoder-delay", &mEncoderDelay)) {
        mEncoderDelay = 0;
    }

    if (!msg->findInt32("encoder-padding", &mEncoderPadding)) {
        mEncoderPadding = 0;
    }

    if (msg->findInt32("channel-mask", &mChannelMask)) {
        mChannelMaskPresent = true;
    } else {
        mChannelMaskPresent = false;
    }

    int32_t maxInputSize;
    if (msg->findInt32("max-input-size", &maxInputSize)) {
        err = setMinBufferSize(kPortIndexInput, (size_t)maxInputSize);
    } else if (!strcmp("OMX.Nvidia.aac.decoder", mComponentName.c_str())) {
        err = setMinBufferSize(kPortIndexInput, 8192);  // XXX
    }

#ifndef ANDROID_DEFAULT_CODE
    if ((!strncmp("OMX.MTK.", mComponentName.c_str(), 8)) && (!mIsEncoder)) {
        OMX_BOOL value;
        // check if codec supports partial frames input
        status_t err = mOMX->getParameter(mNode, 
                (OMX_INDEXTYPE)OMX_IndexVendorMtkOmxPartialFrameQuerySupported, 
                &value, sizeof(value));
        mSupportsPartialFrames = value;
        if (err != OK) {
            mSupportsPartialFrames = false;
        }
        ALOGI("mSupportsPartialFrames %d err %d ", mSupportsPartialFrames, err);
    }
#ifdef DUMP_BITSTREAM
    struct timeval tv;
    struct tm *tm;
    gettimeofday(&tv, NULL);
    tm = localtime(&tv.tv_sec); 
    AString sName = StringPrintf("/sdcard/ACodec.%s.%02d%02d", mComponentName.c_str(), tm->tm_hour, tm->tm_min);
    mDumpFile = fopen(sName.c_str(), "wb");
    if (mDumpFile == NULL) {
        ALOGE("dump file cannot create %s", sName.c_str());
    }
#endif //DUMP_BITSTREAM

    if( (mIsDemandNormalYUV == true) && (!strncmp("OMX.MTK.VIDEO.DECODER.", mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))))
    {

        {
            //check video otuput format to prevent error usage from JAVA framework
            OMX_PARAM_PORTDEFINITIONTYPE outputdef;
            InitOMXParams(&outputdef);
            outputdef.nPortIndex = kPortIndexOutput;
            
            status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &outputdef, sizeof(outputdef));
            CHECK_EQ((status_t)OK, err);
            
            switch (outputdef.format.video.eColorFormat) {
                case OMX_MTK_COLOR_FormatYV12:
                case OMX_COLOR_FormatVendorMTKYUV:
                    break;
                default:
                    //if( true == mIsDemandNormalYUV )
                    {
                    mIsDemandNormalYUV = false;
                    ALOGD("video output format %x no necessary to do color convert", outputdef.format.video.eColorFormat);
                    }
                    break;           
            }
        }
        if(mIsDemandNormalYUV == true)
        {

#ifdef DUMP_RAW
            struct timeval tv;
            struct tm *tm;
            gettimeofday(&tv, NULL);
            tm = localtime(&tv.tv_sec); 
            AString sName = StringPrintf("/storage/sdcard1/ACodecRaw.%02d%02d%02d.yuv", tm->tm_hour, tm->tm_min, tm->tm_sec);
            mDumpRawFile = fopen(sName.c_str(), "wb");
            if (mDumpRawFile == NULL) {
                ALOGE("dump raw file cannot create %s", sName.c_str());
            }
            else
                ALOGI("open file %s done", sName.c_str());
#endif //DUMP_RAW

            //if( NULL != mM4UBufferHandle )
            {
                mAcodecCConvertMode = true;
                err = mOMX->setParameter(
                        mNode, OMX_IndexVendorMtkOmxVdecACodecColorConvertMode, &mAcodecCConvertMode, sizeof(uint32_t));
            }
        }
    }

#ifdef MTK_CLEARMOTION_SUPPORT
    int32_t mUseClearMotionMode = 0;
    //if (mFlags & kUseClearMotion) {
    if (msg->findInt32("use-clearmotion-mode", &mUseClearMotionMode) && mUseClearMotionMode != 0) {
        ALOGD("set use-clearmotion-mode");
        mOMX->setParameter(mNode, OMX_IndexVendorMtkOmxVdecUseClearMotion, &mUseClearMotionMode, sizeof(int32_t));
    }
#endif //MTK_CLEARMOTION_SUPPORT

    // mtk80902: porting rtsp settings from OMXCodec
    int32_t mode;
    if (msg->findInt32("rtsp-seek-mode", &mode) && mode != 0) {
        status_t err2 = OK;
        OMX_INDEXTYPE index = OMX_IndexMax;
        status_t err = mOMX->getExtensionIndex(mNode, "OMX.MTK.index.param.video.StreamingMode", &index);
        if (err == OK) {
            OMX_BOOL m = OMX_TRUE; 
            err2 = mOMX->setParameter(mNode, index, &m, sizeof(m));
        }
        ALOGI("set StreamingMode, index = %x, err = %x, err2 = %x", index, err, err2);
    }
    int32_t number = -1;
    if (msg->findInt32("max-queue-buffer", &number) && number > 0) {
        mMaxQueueBufferNum = number;
    }
    if (msg->findInt32("input-buffer-number", &number) && number > 0) {
        OMX_PARAM_PORTDEFINITIONTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexInput;

        status_t err = mOMX->getParameter(
        mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
        CHECK_EQ((int)err, (int)OK);

        def.nBufferCountActual = number > (int32_t)def.nBufferCountMin 
            ? number : def.nBufferCountMin;

        err = mOMX->setParameter(
        mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
        CHECK_EQ((int)err, (int)OK);

        err = mOMX->getParameter(
        mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
        CHECK_EQ((int)err, (int)OK);
    }
// mtk80902: porting from OMXCodec - is video enc/dec
    if (false == mIsEncoder) {
        if ((!strcasecmp(MEDIA_MIMETYPE_VIDEO_AVC, mime)) ||
            (!strcasecmp(MEDIA_MIMETYPE_VIDEO_MPEG4, mime)) ||
            (!strcasecmp(MEDIA_MIMETYPE_VIDEO_H263, mime)) ||
            (!strcasecmp(MEDIA_MIMETYPE_VIDEO_WMV, mime)) ||        // Morris Yang add for ASF
            (!strcasecmp(MEDIA_MIMETYPE_VIDEO_MPEG2, mime)) ||
#ifndef ANDROID_DEFAULT_CODE
            (!strcasecmp(MEDIA_MIMETYPE_VIDEO_VPX, mime)) ||
            (!strcasecmp(MEDIA_MIMETYPE_VIDEO_VP9, mime))
#endif //ANDROID_DEFAULT_CODE
        ) { 
            mIsVideoDecoder = true;
/*
            char value[PROPERTY_VALUE_MAX];
            property_get("omxcodec.video.input.error.rate", value, "0.0");	
            mVideoInputErrorRate = atof(value);
            if (mVideoInputErrorRate > 0) {
                mPropFlags |= OMXCODEC_ENABLE_VIDEO_INPUT_ERROR_PATTERNS;
            }
            ALOGD ("mVideoInputErrorRate(%f)", mVideoInputErrorRate);*/

            /*if (mOMXLivesLocally) {
                OMX_BOOL bIsLocally = OMX_TRUE;
                status_t err2 = mOMX->setParameter(mNode, OMX_IndexVendorMtkOmxVideoSetClientLocally, &bIsLocally, sizeof(bIsLocally));
            //CHECK_EQ((int)err2, (int)OK);
            }
            else */{
                OMX_BOOL bIsLocally = OMX_FALSE;
                status_t err2 = mOMX->setParameter(mNode, OMX_IndexVendorMtkOmxVideoSetClientLocally, &bIsLocally, sizeof(bIsLocally));
            }
        }
    }
    else {
        if ((!strcasecmp(MEDIA_MIMETYPE_VIDEO_AVC, mime)) ||
            (!strcasecmp(MEDIA_MIMETYPE_VIDEO_MPEG4, mime)) ||
            (!strcasecmp(MEDIA_MIMETYPE_VIDEO_H263, mime))) {
            mIsVideoEncoder = true;

            {
                int32_t tmp;
                if (!msg->findInt32("color-format", &tmp)) {
                    tmp = 0;
                    ALOGW ("colorFormat can not found");
                }
                
                OMX_COLOR_FORMATTYPE colorFormat =
                    static_cast<OMX_COLOR_FORMATTYPE>(tmp);

                ALOGD ("colorFormat %x", colorFormat);
                if( (colorFormat == OMX_COLOR_Format16bitRGB565) || (colorFormat == OMX_COLOR_Format24bitRGB888)
                    || (colorFormat == OMX_COLOR_Format32bitARGB8888) || (colorFormat == OMX_COLOR_FormatYUV420Planar) )
                {
                    mAcodecEncCConvertMode = true;
                    ALOGD ("mAcodecEncCConvertMode addr %x", &mAcodecEncCConvertMode);
                    err = mOMX->setParameter(
                            mNode, OMX_IndexVendorMtkOmxVdecACodecEncodeRGB2YUVMode, &mAcodecEncCConvertMode, sizeof(uint32_t));
                }
            }

/*
            mCameraMeta = new MetaData;

            if (!mOMXLivesLocally) {
                mQuirks &= ~kAvoidMemcopyInputRecordingFrames;
            }*/
            /*if (mOMXLivesLocally) {
                OMX_BOOL bIsLocally = OMX_TRUE;
                status_t err2 = mOMX->setParameter(mNode, OMX_IndexVendorMtkOmxVideoSetClientLocally, &bIsLocally, sizeof(bIsLocally));
            //CHECK_EQ((int)err2, (int)OK);
            }
            else */{
                OMX_BOOL bIsLocally = OMX_FALSE;
                status_t err2 = mOMX->setParameter(mNode, OMX_IndexVendorMtkOmxVideoSetClientLocally, &bIsLocally, sizeof(bIsLocally));
            }
        }
    }
/*
    ALOGD ("!@@!>> create tid (%d) OMXCodec mOMXLivesLocally=%d, mIsVideoDecoder(%d), mIsVideoEncoder(%d), mime(%s)", 
        gettid(), mOMXLivesLocally, mIsVideoDecoder, mIsVideoEncoder, mime);*/
#endif

    return err;
}

status_t ACodec::setMinBufferSize(OMX_U32 portIndex, size_t size) {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = portIndex;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

    if (def.nBufferSize >= size) {
        return OK;
    }

    def.nBufferSize = size;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

    err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

    CHECK(def.nBufferSize >= size);

    return OK;
}

status_t ACodec::selectAudioPortFormat(
        OMX_U32 portIndex, OMX_AUDIO_CODINGTYPE desiredFormat) {
    OMX_AUDIO_PARAM_PORTFORMATTYPE format;
    InitOMXParams(&format);

    format.nPortIndex = portIndex;
    for (OMX_U32 index = 0;; ++index) {
        format.nIndex = index;

        status_t err = mOMX->getParameter(
                mNode, OMX_IndexParamAudioPortFormat,
                &format, sizeof(format));

        if (err != OK) {
            return err;
        }

        if (format.eEncoding == desiredFormat) {
            break;
        }
    }

    return mOMX->setParameter(
            mNode, OMX_IndexParamAudioPortFormat, &format, sizeof(format));
}

status_t ACodec::setupAACCodec(
        bool encoder, int32_t numChannels, int32_t sampleRate,
        int32_t bitRate, int32_t aacProfile, bool isADTS) {
    if (encoder && isADTS) {
        return -EINVAL;
    }

    status_t err = setupRawAudioFormat(
            encoder ? kPortIndexInput : kPortIndexOutput,
            sampleRate,
            numChannels);

    if (err != OK) {
        return err;
    }

    if (encoder) {
        err = selectAudioPortFormat(kPortIndexOutput, OMX_AUDIO_CodingAAC);

        if (err != OK) {
            return err;
        }

        OMX_PARAM_PORTDEFINITIONTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexOutput;

        err = mOMX->getParameter(
                mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

        if (err != OK) {
            return err;
        }

        def.format.audio.bFlagErrorConcealment = OMX_TRUE;
        def.format.audio.eEncoding = OMX_AUDIO_CodingAAC;

        err = mOMX->setParameter(
                mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

        if (err != OK) {
            return err;
        }

        OMX_AUDIO_PARAM_AACPROFILETYPE profile;
        InitOMXParams(&profile);
        profile.nPortIndex = kPortIndexOutput;

        err = mOMX->getParameter(
                mNode, OMX_IndexParamAudioAac, &profile, sizeof(profile));

        if (err != OK) {
            return err;
        }

        profile.nChannels = numChannels;

        profile.eChannelMode =
            (numChannels == 1)
                ? OMX_AUDIO_ChannelModeMono: OMX_AUDIO_ChannelModeStereo;

        profile.nSampleRate = sampleRate;
        profile.nBitRate = bitRate;
        profile.nAudioBandWidth = 0;
        profile.nFrameLength = 0;
        profile.nAACtools = OMX_AUDIO_AACToolAll;
        profile.nAACERtools = OMX_AUDIO_AACERNone;
        profile.eAACProfile = (OMX_AUDIO_AACPROFILETYPE) aacProfile;
        profile.eAACStreamFormat = OMX_AUDIO_AACStreamFormatMP4FF;

        err = mOMX->setParameter(
                mNode, OMX_IndexParamAudioAac, &profile, sizeof(profile));

        if (err != OK) {
            return err;
        }

        return err;
    }

    OMX_AUDIO_PARAM_AACPROFILETYPE profile;
    InitOMXParams(&profile);
    profile.nPortIndex = kPortIndexInput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamAudioAac, &profile, sizeof(profile));

    if (err != OK) {
        return err;
    }

    profile.nChannels = numChannels;
    profile.nSampleRate = sampleRate;

    profile.eAACStreamFormat =
        isADTS
            ? OMX_AUDIO_AACStreamFormatMP4ADTS
            : OMX_AUDIO_AACStreamFormatMP4FF;

    return mOMX->setParameter(
            mNode, OMX_IndexParamAudioAac, &profile, sizeof(profile));
}

static OMX_AUDIO_AMRBANDMODETYPE pickModeFromBitRate(
        bool isAMRWB, int32_t bps) {
    if (isAMRWB) {
        if (bps <= 6600) {
            return OMX_AUDIO_AMRBandModeWB0;
        } else if (bps <= 8850) {
            return OMX_AUDIO_AMRBandModeWB1;
        } else if (bps <= 12650) {
            return OMX_AUDIO_AMRBandModeWB2;
        } else if (bps <= 14250) {
            return OMX_AUDIO_AMRBandModeWB3;
        } else if (bps <= 15850) {
            return OMX_AUDIO_AMRBandModeWB4;
        } else if (bps <= 18250) {
            return OMX_AUDIO_AMRBandModeWB5;
        } else if (bps <= 19850) {
            return OMX_AUDIO_AMRBandModeWB6;
        } else if (bps <= 23050) {
            return OMX_AUDIO_AMRBandModeWB7;
        }

        // 23850 bps
        return OMX_AUDIO_AMRBandModeWB8;
    } else {  // AMRNB
        if (bps <= 4750) {
            return OMX_AUDIO_AMRBandModeNB0;
        } else if (bps <= 5150) {
            return OMX_AUDIO_AMRBandModeNB1;
        } else if (bps <= 5900) {
            return OMX_AUDIO_AMRBandModeNB2;
        } else if (bps <= 6700) {
            return OMX_AUDIO_AMRBandModeNB3;
        } else if (bps <= 7400) {
            return OMX_AUDIO_AMRBandModeNB4;
        } else if (bps <= 7950) {
            return OMX_AUDIO_AMRBandModeNB5;
        } else if (bps <= 10200) {
            return OMX_AUDIO_AMRBandModeNB6;
        }

        // 12200 bps
        return OMX_AUDIO_AMRBandModeNB7;
    }
}

status_t ACodec::setupAMRCodec(bool encoder, bool isWAMR, int32_t bitrate) {
    OMX_AUDIO_PARAM_AMRTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = encoder ? kPortIndexOutput : kPortIndexInput;

    status_t err =
        mOMX->getParameter(mNode, OMX_IndexParamAudioAmr, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

    def.eAMRFrameFormat = OMX_AUDIO_AMRFrameFormatFSF;
    def.eAMRBandMode = pickModeFromBitRate(isWAMR, bitrate);

    err = mOMX->setParameter(
            mNode, OMX_IndexParamAudioAmr, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

    return setupRawAudioFormat(
            encoder ? kPortIndexInput : kPortIndexOutput,
            isWAMR ? 16000 : 8000 /* sampleRate */,
            1 /* numChannels */);
}

status_t ACodec::setupG711Codec(bool encoder, int32_t numChannels) {
    CHECK(!encoder);  // XXX TODO

    return setupRawAudioFormat(
            kPortIndexInput, 8000 /* sampleRate */, numChannels);
}

status_t ACodec::setupFlacCodec(
        bool encoder, int32_t numChannels, int32_t sampleRate, int32_t compressionLevel) {

    if (encoder) {
        OMX_AUDIO_PARAM_FLACTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexOutput;

        // configure compression level
        status_t err = mOMX->getParameter(mNode, OMX_IndexParamAudioFlac, &def, sizeof(def));
        if (err != OK) {
            ALOGE("setupFlacCodec(): Error %d getting OMX_IndexParamAudioFlac parameter", err);
            return err;
        }
        def.nCompressionLevel = compressionLevel;
        err = mOMX->setParameter(mNode, OMX_IndexParamAudioFlac, &def, sizeof(def));
        if (err != OK) {
            ALOGE("setupFlacCodec(): Error %d setting OMX_IndexParamAudioFlac parameter", err);
            return err;
        }
    }

    return setupRawAudioFormat(
            encoder ? kPortIndexInput : kPortIndexOutput,
            sampleRate,
            numChannels);
}

status_t ACodec::setupRawAudioFormat(
        OMX_U32 portIndex, int32_t sampleRate, int32_t numChannels) {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = portIndex;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

    def.format.audio.eEncoding = OMX_AUDIO_CodingPCM;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

    OMX_AUDIO_PARAM_PCMMODETYPE pcmParams;
    InitOMXParams(&pcmParams);
    pcmParams.nPortIndex = portIndex;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamAudioPcm, &pcmParams, sizeof(pcmParams));

    if (err != OK) {
        return err;
    }

    pcmParams.nChannels = numChannels;
    pcmParams.eNumData = OMX_NumericalDataSigned;
    pcmParams.bInterleaved = OMX_TRUE;
    pcmParams.nBitPerSample = 16;
    pcmParams.nSamplingRate = sampleRate;
    pcmParams.ePCMMode = OMX_AUDIO_PCMModeLinear;

    if (getOMXChannelMapping(numChannels, pcmParams.eChannelMapping) != OK) {
        return OMX_ErrorNone;
    }

    return mOMX->setParameter(
            mNode, OMX_IndexParamAudioPcm, &pcmParams, sizeof(pcmParams));
}

status_t ACodec::setVideoPortFormatType(
        OMX_U32 portIndex,
        OMX_VIDEO_CODINGTYPE compressionFormat,
        OMX_COLOR_FORMATTYPE colorFormat) {
    OMX_VIDEO_PARAM_PORTFORMATTYPE format;
    InitOMXParams(&format);
    format.nPortIndex = portIndex;
    format.nIndex = 0;
    bool found = false;

    OMX_U32 index = 0;
    for (;;) {
        format.nIndex = index;
        status_t err = mOMX->getParameter(
                mNode, OMX_IndexParamVideoPortFormat,
                &format, sizeof(format));

        if (err != OK) {
            return err;
        }

        // The following assertion is violated by TI's video decoder.
        // CHECK_EQ(format.nIndex, index);

        if (!strcmp("OMX.TI.Video.encoder", mComponentName.c_str())) {
            if (portIndex == kPortIndexInput
                    && colorFormat == format.eColorFormat) {
                // eCompressionFormat does not seem right.
                found = true;
                break;
            }
            if (portIndex == kPortIndexOutput
                    && compressionFormat == format.eCompressionFormat) {
                // eColorFormat does not seem right.
                found = true;
                break;
            }
        }

        if (format.eCompressionFormat == compressionFormat
            && format.eColorFormat == colorFormat) {
            found = true;
            break;
        }
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("target compressionFormat %x, colorFormat %x", compressionFormat, colorFormat);
        ALOGD("setVideoPortFormatType index %d, portIndex %d, eColorFormat %x, eCompressionFormat %x", index, portIndex, format.eColorFormat, format.eCompressionFormat);
#endif //ANDROID_DEFAULT_CODE
        ++index;
    }

    if (!found) {
        return UNKNOWN_ERROR;
    }

    status_t err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoPortFormat,
            &format, sizeof(format));

    return err;
}

status_t ACodec::setSupportedOutputFormat() {
    OMX_VIDEO_PARAM_PORTFORMATTYPE format;
    InitOMXParams(&format);
    format.nPortIndex = kPortIndexOutput;
    format.nIndex = 0;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoPortFormat,
            &format, sizeof(format));
    CHECK_EQ(err, (status_t)OK);
    CHECK_EQ((int)format.eCompressionFormat, (int)OMX_VIDEO_CodingUnused);
#if 0 //KitKat remove this check
    CHECK(format.eColorFormat == OMX_COLOR_FormatYUV420Planar
           || format.eColorFormat == OMX_COLOR_FormatYUV420SemiPlanar
           || format.eColorFormat == OMX_COLOR_FormatCbYCrY
           || format.eColorFormat == OMX_TI_COLOR_FormatYUV420PackedSemiPlanar
//           || format.eColorFormat == OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka
#ifndef ANDROID_DEFAULT_CODE
           || format.eColorFormat == OMX_MTK_COLOR_FormatYV12
           || format.eColorFormat == OMX_COLOR_FormatVendorMTKYUV
#endif
           || format.eColorFormat == OMX_QCOM_COLOR_FormatYVU420SemiPlanar);
#endif //
    return mOMX->setParameter(
            mNode, OMX_IndexParamVideoPortFormat,
            &format, sizeof(format));
}

static const struct VideoCodingMapEntry {
    const char *mMime;
    OMX_VIDEO_CODINGTYPE mVideoCodingType;
} kVideoCodingMapEntry[] = {
    { MEDIA_MIMETYPE_VIDEO_AVC, OMX_VIDEO_CodingAVC },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, OMX_VIDEO_CodingMPEG4 },
    { MEDIA_MIMETYPE_VIDEO_H263, OMX_VIDEO_CodingH263 },
    { MEDIA_MIMETYPE_VIDEO_MPEG2, OMX_VIDEO_CodingMPEG2 },
    { MEDIA_MIMETYPE_VIDEO_VP8, OMX_VIDEO_CodingVP8 },
    { MEDIA_MIMETYPE_VIDEO_VP9, OMX_VIDEO_CodingVP9 },
#ifndef ANDROID_DEFAULT_CODE
    { MEDIA_MIMETYPE_VIDEO_VPX, OMX_VIDEO_CodingVP8 },
#ifdef MTK_VIDEO_HEVC_SUPPORT
    { MEDIA_MIMETYPE_VIDEO_HEVC, OMX_VIDEO_CodingHEVC },
#endif //MTK_VIDEO_HEVC_SUPPORT
#endif //ANDROID_DEFAULT_CODE
};

static status_t GetVideoCodingTypeFromMime(
        const char *mime, OMX_VIDEO_CODINGTYPE *codingType) {
    for (size_t i = 0;
         i < sizeof(kVideoCodingMapEntry) / sizeof(kVideoCodingMapEntry[0]);
         ++i) {
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("GetVideoCodingTypeFromMime %s, %s", mime, kVideoCodingMapEntry[i].mMime );
#endif //ANDROID_DEFAULT_CODE
        if (!strcasecmp(mime, kVideoCodingMapEntry[i].mMime)) {
            *codingType = kVideoCodingMapEntry[i].mVideoCodingType;
            return OK;
        }
    }

    *codingType = OMX_VIDEO_CodingUnused;

    return ERROR_UNSUPPORTED;
}

static status_t GetMimeTypeForVideoCoding(
        OMX_VIDEO_CODINGTYPE codingType, AString *mime) {
    for (size_t i = 0;
         i < sizeof(kVideoCodingMapEntry) / sizeof(kVideoCodingMapEntry[0]);
         ++i) {
        if (codingType == kVideoCodingMapEntry[i].mVideoCodingType) {
            *mime = kVideoCodingMapEntry[i].mMime;
            return OK;
        }
    }

    mime->clear();

    return ERROR_UNSUPPORTED;
}

status_t ACodec::setupVideoDecoder(
        const char *mime, int32_t width, int32_t height) {
    OMX_VIDEO_CODINGTYPE compressionFormat;
    status_t err = GetVideoCodingTypeFromMime(mime, &compressionFormat);

    if (err != OK) {
        return err;
    }

    err = setVideoPortFormatType(
            kPortIndexInput, compressionFormat, OMX_COLOR_FormatUnused);

    if (err != OK) {
        return err;
    }

    err = setSupportedOutputFormat();

    if (err != OK) {
        return err;
    }

    err = setVideoFormatOnPort(
            kPortIndexInput, width, height, compressionFormat);

    if (err != OK) {
        return err;
    }

    err = setVideoFormatOnPort(
            kPortIndexOutput, width, height, OMX_VIDEO_CodingUnused);

    if (err != OK) {
        return err;
    }

    return OK;
}

status_t ACodec::setupVideoEncoder(const char *mime, const sp<AMessage> &msg) {
    int32_t tmp;
    if (!msg->findInt32("color-format", &tmp)) {
        return INVALID_OPERATION;
    }

    OMX_COLOR_FORMATTYPE colorFormat =
        static_cast<OMX_COLOR_FORMATTYPE>(tmp);

    status_t err = setVideoPortFormatType(
            kPortIndexInput, OMX_VIDEO_CodingUnused, colorFormat);

    if (err != OK) {
        ALOGE("[%s] does not support color format %d",
              mComponentName.c_str(), colorFormat);

        return err;
    }

    /* Input port configuration */

    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);

    OMX_VIDEO_PORTDEFINITIONTYPE *video_def = &def.format.video;

    def.nPortIndex = kPortIndexInput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

    int32_t width, height, bitrate;
    if (!msg->findInt32("width", &width)
            || !msg->findInt32("height", &height)
            || !msg->findInt32("bitrate", &bitrate)) {
        return INVALID_OPERATION;
    }

    video_def->nFrameWidth = width;
    video_def->nFrameHeight = height;

    int32_t stride;
    if (!msg->findInt32("stride", &stride)) {
        stride = width;
    }
/*it can be remove after ap set this parameters
    @hide
    public static final String KEY_STRIDE = "stride";
*/
#ifndef ANDROID_DEFAULT_CODE
    video_def->nStride = ROUND_16(stride);
#else
    video_def->nStride = stride;
#endif //#ifndef ANDROID_DEFAULT_CODE

    int32_t sliceHeight;
    if (!msg->findInt32("slice-height", &sliceHeight)) {
        sliceHeight = height;
    }
/*it can be remove after ap set this parameters
    @hide
    public static final String KEY_SLICE_HEIGHT = "slice-height";
*/
#ifndef ANDROID_DEFAULT_CODE
    video_def->nSliceHeight = ROUND_16(sliceHeight);
#else
    video_def->nSliceHeight = sliceHeight;
#endif //#ifndef ANDROID_DEFAULT_CODE

#ifndef ANDROID_DEFAULT_CODE
    ALOGD("nStride %d, nSliceHeight %d", video_def->nStride, video_def->nSliceHeight);
    //support RGB565 and RGB888 size
    if( colorFormat == OMX_COLOR_Format16bitRGB565 )
        def.nBufferSize = (video_def->nStride * video_def->nSliceHeight * 2);
    else if( colorFormat == OMX_COLOR_Format24bitRGB888 )
        def.nBufferSize = (video_def->nStride * video_def->nSliceHeight * 3);
    else if( colorFormat == OMX_COLOR_Format32bitARGB8888 )
        def.nBufferSize = (video_def->nStride * video_def->nSliceHeight * 4);
    else
        def.nBufferSize = (video_def->nStride * video_def->nSliceHeight * 3) / 2;
#else
    def.nBufferSize = (video_def->nStride * video_def->nSliceHeight * 3) / 2;
#endif

#ifndef ANDROID_DEFAULT_CODE
     {
         int32_t  inputbufferCnt;
         if (msg->findInt32("inputbuffercnt", &inputbufferCnt)) {
            def.nBufferCountActual  = inputbufferCnt;
            ALOGI("input buffer count is %d", inputbufferCnt);
         }
     }
#endif

    float frameRate;
    if (!msg->findFloat("frame-rate", &frameRate)) {
        int32_t tmp;
        if (!msg->findInt32("frame-rate", &tmp)) {
            return INVALID_OPERATION;
        }
        frameRate = (float)tmp;
    }

    video_def->xFramerate = (OMX_U32)(frameRate * 65536.0f);
    video_def->eCompressionFormat = OMX_VIDEO_CodingUnused;
    video_def->eColorFormat = colorFormat;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        ALOGE("[%s] failed to set input port definition parameters.",
              mComponentName.c_str());

        return err;
    }

    /* Output port configuration */

    OMX_VIDEO_CODINGTYPE compressionFormat;
    err = GetVideoCodingTypeFromMime(mime, &compressionFormat);

    if (err != OK) {
        return err;
    }

    err = setVideoPortFormatType(
            kPortIndexOutput, compressionFormat, OMX_COLOR_FormatUnused);

    if (err != OK) {
        ALOGE("[%s] does not support compression format %d",
             mComponentName.c_str(), compressionFormat);

        return err;
    }

    def.nPortIndex = kPortIndexOutput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

    video_def->nFrameWidth = width;
    video_def->nFrameHeight = height;
    video_def->xFramerate = 0;
    video_def->nBitrate = bitrate;
    video_def->eCompressionFormat = compressionFormat;
    video_def->eColorFormat = OMX_COLOR_FormatUnused;


#ifndef ANDROID_DEFAULT_CODE
     {
         int32_t  outputbuffersize;
         if (msg->findInt32("outputbuffersize", &outputbuffersize)) {
            def.nBufferSize  = outputbuffersize;
            ALOGI("output buffer size is %d", outputbuffersize);
         }
     }
#endif //ANDROID_DEFAULT_CODE

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        ALOGE("[%s] failed to set output port definition parameters.",
              mComponentName.c_str());

        return err;
    }

    switch (compressionFormat) {
        case OMX_VIDEO_CodingMPEG4:
            err = setupMPEG4EncoderParameters(msg);
            break;

        case OMX_VIDEO_CodingH263:
            err = setupH263EncoderParameters(msg);
            break;

        case OMX_VIDEO_CodingAVC:
            err = setupAVCEncoderParameters(msg);
            break;

        case OMX_VIDEO_CodingVP8:
        case OMX_VIDEO_CodingVP9:
            err = setupVPXEncoderParameters(msg);
            break;

        default:
            break;
    }

    ALOGI("setupVideoEncoder succeeded");

    return err;
}

status_t ACodec::setCyclicIntraMacroblockRefresh(const sp<AMessage> &msg, int32_t mode) {
    OMX_VIDEO_PARAM_INTRAREFRESHTYPE params;
    InitOMXParams(&params);
    params.nPortIndex = kPortIndexOutput;

    params.eRefreshMode = static_cast<OMX_VIDEO_INTRAREFRESHTYPE>(mode);

    if (params.eRefreshMode == OMX_VIDEO_IntraRefreshCyclic ||
            params.eRefreshMode == OMX_VIDEO_IntraRefreshBoth) {
        int32_t mbs;
        if (!msg->findInt32("intra-refresh-CIR-mbs", &mbs)) {
            return INVALID_OPERATION;
        }
        params.nCirMBs = mbs;
    }

    if (params.eRefreshMode == OMX_VIDEO_IntraRefreshAdaptive ||
            params.eRefreshMode == OMX_VIDEO_IntraRefreshBoth) {
        int32_t mbs;
        if (!msg->findInt32("intra-refresh-AIR-mbs", &mbs)) {
            return INVALID_OPERATION;
        }
        params.nAirMBs = mbs;

        int32_t ref;
        if (!msg->findInt32("intra-refresh-AIR-ref", &ref)) {
            return INVALID_OPERATION;
        }
        params.nAirRef = ref;
    }

    status_t err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoIntraRefresh,
            &params, sizeof(params));
    return err;
}

static OMX_U32 setPFramesSpacing(int32_t iFramesInterval, int32_t frameRate) {
    if (iFramesInterval < 0) {
        return 0xFFFFFFFF;
    } else if (iFramesInterval == 0) {
        return 0;
    }
    OMX_U32 ret = frameRate * iFramesInterval;
    CHECK(ret > 1);
    return ret;
}

static OMX_VIDEO_CONTROLRATETYPE getBitrateMode(const sp<AMessage> &msg) {
    int32_t tmp;
    if (!msg->findInt32("bitrate-mode", &tmp)) {
        return OMX_Video_ControlRateVariable;
    }

    return static_cast<OMX_VIDEO_CONTROLRATETYPE>(tmp);
}

status_t ACodec::setupMPEG4EncoderParameters(const sp<AMessage> &msg) {
    int32_t bitrate, iFrameInterval;
    if (!msg->findInt32("bitrate", &bitrate)
            || !msg->findInt32("i-frame-interval", &iFrameInterval)) {
        return INVALID_OPERATION;
    }

    OMX_VIDEO_CONTROLRATETYPE bitrateMode = getBitrateMode(msg);

    float frameRate;
    if (!msg->findFloat("frame-rate", &frameRate)) {
        int32_t tmp;
        if (!msg->findInt32("frame-rate", &tmp)) {
            return INVALID_OPERATION;
        }
        frameRate = (float)tmp;
    }

    OMX_VIDEO_PARAM_MPEG4TYPE mpeg4type;
    InitOMXParams(&mpeg4type);
    mpeg4type.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoMpeg4, &mpeg4type, sizeof(mpeg4type));

    if (err != OK) {
        return err;
    }

    mpeg4type.nSliceHeaderSpacing = 0;
    mpeg4type.bSVH = OMX_FALSE;
    mpeg4type.bGov = OMX_FALSE;

    mpeg4type.nAllowedPictureTypes =
        OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP;

    mpeg4type.nPFrames = setPFramesSpacing(iFrameInterval, frameRate);
    if (mpeg4type.nPFrames == 0) {
        mpeg4type.nAllowedPictureTypes = OMX_VIDEO_PictureTypeI;
    }
    mpeg4type.nBFrames = 0;
    mpeg4type.nIDCVLCThreshold = 0;
    mpeg4type.bACPred = OMX_TRUE;
    mpeg4type.nMaxPacketSize = 256;
    mpeg4type.nTimeIncRes = 1000;
    mpeg4type.nHeaderExtension = 0;
    mpeg4type.bReversibleVLC = OMX_FALSE;

    int32_t profile;
    if (msg->findInt32("profile", &profile)) {
        int32_t level;
        if (!msg->findInt32("level", &level)) {
            return INVALID_OPERATION;
        }

        err = verifySupportForProfileAndLevel(profile, level);

        if (err != OK) {
            return err;
        }

        mpeg4type.eProfile = static_cast<OMX_VIDEO_MPEG4PROFILETYPE>(profile);
        mpeg4type.eLevel = static_cast<OMX_VIDEO_MPEG4LEVELTYPE>(level);
    }

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoMpeg4, &mpeg4type, sizeof(mpeg4type));

    if (err != OK) {
        return err;
    }

    err = configureBitrate(bitrate, bitrateMode);

    if (err != OK) {
        return err;
    }

    return setupErrorCorrectionParameters();
}

status_t ACodec::setupH263EncoderParameters(const sp<AMessage> &msg) {
    int32_t bitrate, iFrameInterval;
    if (!msg->findInt32("bitrate", &bitrate)
            || !msg->findInt32("i-frame-interval", &iFrameInterval)) {
        return INVALID_OPERATION;
    }

    OMX_VIDEO_CONTROLRATETYPE bitrateMode = getBitrateMode(msg);

    float frameRate;
    if (!msg->findFloat("frame-rate", &frameRate)) {
        int32_t tmp;
        if (!msg->findInt32("frame-rate", &tmp)) {
            return INVALID_OPERATION;
        }
        frameRate = (float)tmp;
    }

    OMX_VIDEO_PARAM_H263TYPE h263type;
    InitOMXParams(&h263type);
    h263type.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoH263, &h263type, sizeof(h263type));
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("getParameter OMX_IndexParamVideoH263 %x", err);
#endif //ANDROID_DEFAULT_CODE
    if (err != OK) {
        return err;
    }

    h263type.nAllowedPictureTypes =
        OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP;

    h263type.nPFrames = setPFramesSpacing(iFrameInterval, frameRate);
    if (h263type.nPFrames == 0) {
        h263type.nAllowedPictureTypes = OMX_VIDEO_PictureTypeI;
    }
    h263type.nBFrames = 0;

    int32_t profile;
    if (msg->findInt32("profile", &profile)) {
        int32_t level;
        if (!msg->findInt32("level", &level)) {
            return INVALID_OPERATION;
        }

        err = verifySupportForProfileAndLevel(profile, level);
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("verifySupportForProfileAndLevel %x", err);
#endif //ANDROID_DEFAULT_CODE
        if (err != OK) {
            return err;
        }

        h263type.eProfile = static_cast<OMX_VIDEO_H263PROFILETYPE>(profile);
        h263type.eLevel = static_cast<OMX_VIDEO_H263LEVELTYPE>(level);
    }

    h263type.bPLUSPTYPEAllowed = OMX_FALSE;
    h263type.bForceRoundingTypeToZero = OMX_FALSE;
    h263type.nPictureHeaderRepetition = 0;
    h263type.nGOBHeaderInterval = 0;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoH263, &h263type, sizeof(h263type));
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("setParameter OMX_IndexParamVideoH263 %x", err);
#endif //ANDROID_DEFAULT_CODE
    if (err != OK) {
        return err;
    }

    err = configureBitrate(bitrate, bitrateMode);
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("configureBitrate %x", err);
#endif //ANDROID_DEFAULT_CODE
    if (err != OK) {
        return err;
    }

    return setupErrorCorrectionParameters();
}

status_t ACodec::setupAVCEncoderParameters(const sp<AMessage> &msg) {
    int32_t bitrate, iFrameInterval;
    if (!msg->findInt32("bitrate", &bitrate)
            || !msg->findInt32("i-frame-interval", &iFrameInterval)) {
        return INVALID_OPERATION;
    }

    OMX_VIDEO_CONTROLRATETYPE bitrateMode = getBitrateMode(msg);

    float frameRate;
    if (!msg->findFloat("frame-rate", &frameRate)) {
        int32_t tmp;
        if (!msg->findInt32("frame-rate", &tmp)) {
            return INVALID_OPERATION;
        }
        frameRate = (float)tmp;
    }

    status_t err = OK;
    int32_t intraRefreshMode = 0;
    if (msg->findInt32("intra-refresh-mode", &intraRefreshMode)) {
        err = setCyclicIntraMacroblockRefresh(msg, intraRefreshMode);
        if (err != OK) {
            ALOGE("Setting intra macroblock refresh mode (%d) failed: 0x%x",
                    err, intraRefreshMode);
            return err;
        }
    }

    OMX_VIDEO_PARAM_AVCTYPE h264type;
    InitOMXParams(&h264type);
    h264type.nPortIndex = kPortIndexOutput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoAvc, &h264type, sizeof(h264type));

    if (err != OK) {
        return err;
    }

    h264type.nAllowedPictureTypes =
        OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP;

    int32_t profile;
    if (msg->findInt32("profile", &profile)) {
        int32_t level;
        if (!msg->findInt32("level", &level)) {
            return INVALID_OPERATION;
        }

        err = verifySupportForProfileAndLevel(profile, level);

        if (err != OK) {
            return err;
        }

        h264type.eProfile = static_cast<OMX_VIDEO_AVCPROFILETYPE>(profile);
        h264type.eLevel = static_cast<OMX_VIDEO_AVCLEVELTYPE>(level);
    }

    // XXX
#ifdef ANDROID_DEFAULT_CODE
    //Bruce Hsu 2013/08/07 we hope use the platform default profile & level to keep the video quality
    if (h264type.eProfile != OMX_VIDEO_AVCProfileBaseline) {
        ALOGW("Use baseline profile instead of %d for AVC recording",
            h264type.eProfile);
        h264type.eProfile = OMX_VIDEO_AVCProfileBaseline;
    }
#endif//ANDROID_DEFAULT_CODE

    if (h264type.eProfile == OMX_VIDEO_AVCProfileBaseline) {
        h264type.nSliceHeaderSpacing = 0;
        h264type.bUseHadamard = OMX_TRUE;
        h264type.nRefFrames = 1;
        h264type.nBFrames = 0;
        h264type.nPFrames = setPFramesSpacing(iFrameInterval, frameRate);
        if (h264type.nPFrames == 0) {
            h264type.nAllowedPictureTypes = OMX_VIDEO_PictureTypeI;
        }
        h264type.nRefIdx10ActiveMinus1 = 0;
        h264type.nRefIdx11ActiveMinus1 = 0;
        h264type.bEntropyCodingCABAC = OMX_FALSE;
        h264type.bWeightedPPrediction = OMX_FALSE;
        h264type.bconstIpred = OMX_FALSE;
        h264type.bDirect8x8Inference = OMX_FALSE;
        h264type.bDirectSpatialTemporal = OMX_FALSE;
        h264type.nCabacInitIdc = 0;
    }

    if (h264type.nBFrames != 0) {
        h264type.nAllowedPictureTypes |= OMX_VIDEO_PictureTypeB;
    }

    h264type.bEnableUEP = OMX_FALSE;
    h264type.bEnableFMO = OMX_FALSE;
    h264type.bEnableASO = OMX_FALSE;
    h264type.bEnableRS = OMX_FALSE;
    h264type.bFrameMBsOnly = OMX_TRUE;
    h264type.bMBAFF = OMX_FALSE;
    h264type.eLoopFilterMode = OMX_VIDEO_AVCLoopFilterEnable;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoAvc, &h264type, sizeof(h264type));

    if (err != OK) {
        return err;
    }

#ifndef ANDROID_DEFAULT_CODE
    err = setVEncIInterval(iFrameInterval);
    if (err != OK) {
        return err;
    }
#endif//ANDROID_DEFAULT_CODE

    return configureBitrate(bitrate, bitrateMode);
}

status_t ACodec::setupVPXEncoderParameters(const sp<AMessage> &msg) {
    int32_t bitrate;
    if (!msg->findInt32("bitrate", &bitrate)) {
        return INVALID_OPERATION;
    }

    OMX_VIDEO_CONTROLRATETYPE bitrateMode = getBitrateMode(msg);

    return configureBitrate(bitrate, bitrateMode);
}

status_t ACodec::verifySupportForProfileAndLevel(
        int32_t profile, int32_t level) {
    OMX_VIDEO_PARAM_PROFILELEVELTYPE params;
    InitOMXParams(&params);
    params.nPortIndex = kPortIndexOutput;

    for (params.nProfileIndex = 0;; ++params.nProfileIndex) {
        status_t err = mOMX->getParameter(
                mNode,
                OMX_IndexParamVideoProfileLevelQuerySupported,
                &params,
                sizeof(params));

        if (err != OK) {
            return err;
        }

        int32_t supportedProfile = static_cast<int32_t>(params.eProfile);
        int32_t supportedLevel = static_cast<int32_t>(params.eLevel);

        if (profile == supportedProfile && level <= supportedLevel) {
            return OK;
        }
    }
}

status_t ACodec::configureBitrate(
        int32_t bitrate, OMX_VIDEO_CONTROLRATETYPE bitrateMode) {
    OMX_VIDEO_PARAM_BITRATETYPE bitrateType;
    InitOMXParams(&bitrateType);
    bitrateType.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoBitrate,
            &bitrateType, sizeof(bitrateType));

    if (err != OK) {
        return err;
    }

    bitrateType.eControlRate = bitrateMode;
    bitrateType.nTargetBitrate = bitrate;

    return mOMX->setParameter(
            mNode, OMX_IndexParamVideoBitrate,
            &bitrateType, sizeof(bitrateType));
}

status_t ACodec::setupErrorCorrectionParameters() {
    OMX_VIDEO_PARAM_ERRORCORRECTIONTYPE errorCorrectionType;
    InitOMXParams(&errorCorrectionType);
    errorCorrectionType.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoErrorCorrection,
            &errorCorrectionType, sizeof(errorCorrectionType));
    ALOGD("getParameter OMX_IndexParamVideoErrorCorrection %x", err);
    if (err != OK) {
        return OK;  // Optional feature. Ignore this failure
    }

    errorCorrectionType.bEnableHEC = OMX_FALSE;
    errorCorrectionType.bEnableResync = OMX_TRUE;
    errorCorrectionType.nResynchMarkerSpacing = 256;
    errorCorrectionType.bEnableDataPartitioning = OMX_FALSE;
    errorCorrectionType.bEnableRVLC = OMX_FALSE;

    return mOMX->setParameter(
            mNode, OMX_IndexParamVideoErrorCorrection,
            &errorCorrectionType, sizeof(errorCorrectionType));
}

status_t ACodec::setVideoFormatOnPort(
        OMX_U32 portIndex,
        int32_t width, int32_t height, OMX_VIDEO_CODINGTYPE compressionFormat) {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = portIndex;

    OMX_VIDEO_PORTDEFINITIONTYPE *video_def = &def.format.video;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    CHECK_EQ(err, (status_t)OK);

    if (portIndex == kPortIndexInput) {
        // XXX Need a (much) better heuristic to compute input buffer sizes.
        const size_t X = 64 * 1024;
        if (def.nBufferSize < X) {
            def.nBufferSize = X;
        }
    }

    CHECK_EQ((int)def.eDomain, (int)OMX_PortDomainVideo);

    video_def->nFrameWidth = width;
    video_def->nFrameHeight = height;

    if (portIndex == kPortIndexInput) {
        video_def->eCompressionFormat = compressionFormat;
        video_def->eColorFormat = OMX_COLOR_FormatUnused;
    }

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    return err;
}

status_t ACodec::initNativeWindow() {
    if (mNativeWindow != NULL) {
        return mOMX->enableGraphicBuffers(mNode, kPortIndexOutput, OMX_TRUE);
    }

    mOMX->enableGraphicBuffers(mNode, kPortIndexOutput, OMX_FALSE);
    return OK;
}

size_t ACodec::countBuffersOwnedByComponent(OMX_U32 portIndex) const {
    size_t n = 0;

    for (size_t i = 0; i < mBuffers[portIndex].size(); ++i) {
        const BufferInfo &info = mBuffers[portIndex].itemAt(i);

        if (info.mStatus == BufferInfo::OWNED_BY_COMPONENT) {
            ++n;
        }
    }

    return n;
}

size_t ACodec::countBuffersOwnedByNativeWindow() const {
    size_t n = 0;

    for (size_t i = 0; i < mBuffers[kPortIndexOutput].size(); ++i) {
        const BufferInfo &info = mBuffers[kPortIndexOutput].itemAt(i);

        if (info.mStatus == BufferInfo::OWNED_BY_NATIVE_WINDOW) {
            ++n;
        }
    }

    return n;
}

void ACodec::waitUntilAllPossibleNativeWindowBuffersAreReturnedToUs() {
    if (mNativeWindow == NULL) {
        return;
    }

    int minUndequeuedBufs = 0;
    status_t err = mNativeWindow->query(
            mNativeWindow.get(), NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS,
            &minUndequeuedBufs);

    if (err != OK) {
        ALOGE("[%s] NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS query failed: %s (%d)",
                mComponentName.c_str(), strerror(-err), -err);

        minUndequeuedBufs = 0;
    }
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("waitUntilAllPossibleNativeWindowBuffersAreReturnedToUs %d, %d, (%d)", 
        mStoreMetaDataInOutputBuffers, mMetaDataBuffersToSubmit, minUndequeuedBufs );
#endif //ANDROID_DEFAULT_CODE
    while (countBuffersOwnedByNativeWindow() > (size_t)minUndequeuedBufs
            && dequeueBufferFromNativeWindow() != NULL) {
        // these buffers will be submitted as regular buffers; account for this
        if (mStoreMetaDataInOutputBuffers && mMetaDataBuffersToSubmit > 0) {
            --mMetaDataBuffersToSubmit;
        }
    }
}

bool ACodec::allYourBuffersAreBelongToUs(
        OMX_U32 portIndex) {
    for (size_t i = 0; i < mBuffers[portIndex].size(); ++i) {
        BufferInfo *info = &mBuffers[portIndex].editItemAt(i);

        if (info->mStatus != BufferInfo::OWNED_BY_US
                && info->mStatus != BufferInfo::OWNED_BY_NATIVE_WINDOW) {
            ALOGD("[%s] Buffer %p on port %ld still has status %d",
                    mComponentName.c_str(),
                    info->mBufferID, portIndex, info->mStatus);
            return false;
        }
    }

    return true;
}

bool ACodec::allYourBuffersAreBelongToUs() {
    return allYourBuffersAreBelongToUs(kPortIndexInput)
        && allYourBuffersAreBelongToUs(kPortIndexOutput);
}

void ACodec::deferMessage(const sp<AMessage> &msg) {
    bool wasEmptyBefore = mDeferredQueue.empty();
    mDeferredQueue.push_back(msg);
}

void ACodec::processDeferredMessages() {
    List<sp<AMessage> > queue = mDeferredQueue;
    mDeferredQueue.clear();

    List<sp<AMessage> >::iterator it = queue.begin();
    while (it != queue.end()) {
        onMessageReceived(*it++);
    }
}
void ACodec::sendFormatChange(const sp<AMessage> &reply) {
    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", kWhatOutputFormatChanged);

    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexOutput;

    CHECK_EQ(mOMX->getParameter(
                mNode, OMX_IndexParamPortDefinition, &def, sizeof(def)),
             (status_t)OK);

    CHECK_EQ((int)def.eDir, (int)OMX_DirOutput);
    ALOGD("OMXCodec:: sendFormatChange %x", def.eDomain);
    switch (def.eDomain) {
        case OMX_PortDomainVideo:
        {
            OMX_VIDEO_PORTDEFINITIONTYPE *videoDef = &def.format.video;

            AString mime;
            if (!mIsEncoder) {
                notify->setString("mime", MEDIA_MIMETYPE_VIDEO_RAW);
            } else if (GetMimeTypeForVideoCoding(
                        videoDef->eCompressionFormat, &mime) != OK) {
                notify->setString("mime", "application/octet-stream");
            } else {
                notify->setString("mime", mime.c_str());
            }

            notify->setInt32("width", videoDef->nFrameWidth);
            notify->setInt32("height", videoDef->nFrameHeight);
#ifndef ANDROID_DEFAULT_CODE
            notify->setInt32("stride", videoDef->nStride);
            notify->setInt32("slice-height", videoDef->nSliceHeight);
            notify->setInt32("color-format", videoDef->eColorFormat);
            notify->setInt32("width-ratio", mVideoAspectRatioWidth);
            notify->setInt32("height-ratio", mVideoAspectRatioHeight);
            ALOGD("OMXCodec:: w %d, h %d, s %d, sh %d", videoDef->nFrameWidth, videoDef->nFrameHeight,
            videoDef->nStride, videoDef->nSliceHeight);
#endif //ANDROID_DEFAULT_CODE
            if (!mIsEncoder) {
                notify->setInt32("stride", videoDef->nStride);
                notify->setInt32("slice-height", videoDef->nSliceHeight);
                notify->setInt32("color-format", videoDef->eColorFormat);

                OMX_CONFIG_RECTTYPE rect;
                InitOMXParams(&rect);
                rect.nPortIndex = kPortIndexOutput;

                if (mOMX->getConfig(
                            mNode, OMX_IndexConfigCommonOutputCrop,
                            &rect, sizeof(rect)) != OK) {
                    rect.nLeft = 0;
                    rect.nTop = 0;
                    rect.nWidth = videoDef->nFrameWidth;
                    rect.nHeight = videoDef->nFrameHeight;
                }
#ifndef ANDROID_DEFAULT_CODE
                if (mOMX->getConfig(
                            mNode, OMX_IndexVendorMtkOmxVdecGetCropInfo,
                            &rect, sizeof(rect)) != OK) {
                    rect.nLeft = 0;
                    rect.nTop = 0;
                    rect.nWidth = videoDef->nFrameWidth;
                    rect.nHeight = videoDef->nFrameHeight;
                }
#endif
                CHECK_GE(rect.nLeft, 0);
                CHECK_GE(rect.nTop, 0);
                CHECK_GE(rect.nWidth, 0u);
                CHECK_GE(rect.nHeight, 0u);
                CHECK_LE(rect.nLeft + rect.nWidth - 1, videoDef->nFrameWidth);
                CHECK_LE(rect.nTop + rect.nHeight - 1, videoDef->nFrameHeight);

                notify->setRect(
                        "crop",
                        rect.nLeft,
                        rect.nTop,
                        rect.nLeft + rect.nWidth - 1,
                        rect.nTop + rect.nHeight - 1);

                if (mNativeWindow != NULL) {
                    reply->setRect(
                            "crop",
                            rect.nLeft,
                            rect.nTop,
                            rect.nLeft + rect.nWidth,
                            rect.nTop + rect.nHeight);
                }
            }
            break;
        }

        case OMX_PortDomainAudio:
        {
            OMX_AUDIO_PORTDEFINITIONTYPE *audioDef = &def.format.audio;

            switch (audioDef->eEncoding) {
                case OMX_AUDIO_CodingPCM:
                {
                    OMX_AUDIO_PARAM_PCMMODETYPE params;
                    InitOMXParams(&params);
                    params.nPortIndex = kPortIndexOutput;

                    CHECK_EQ(mOMX->getParameter(
                                mNode, OMX_IndexParamAudioPcm,
                                &params, sizeof(params)),
                             (status_t)OK);
                    CHECK_GT(params.nChannels, 0);
                    CHECK(params.nChannels == 1 || params.bInterleaved);
                    CHECK_EQ(params.nBitPerSample, 16u);

                    CHECK_EQ((int)params.eNumData,
                             (int)OMX_NumericalDataSigned);

                    CHECK_EQ((int)params.ePCMMode,
                             (int)OMX_AUDIO_PCMModeLinear);

                    notify->setString("mime", MEDIA_MIMETYPE_AUDIO_RAW);
                    notify->setInt32("channel-count", params.nChannels);
                    notify->setInt32("sample-rate", params.nSamplingRate);
                    if (mEncoderDelay + mEncoderPadding) {
                        size_t frameSize = params.nChannels * sizeof(int16_t);
                        if (mSkipCutBuffer != NULL) {
                            size_t prevbufsize = mSkipCutBuffer->size();
                            if (prevbufsize != 0) {
                                ALOGW("Replacing SkipCutBuffer holding %d "
                                      "bytes",
                                      prevbufsize);
                            }
                        }
                        mSkipCutBuffer = new SkipCutBuffer(
                                mEncoderDelay * frameSize,
                                mEncoderPadding * frameSize);
                    }

                    if (mChannelMaskPresent) {
                        notify->setInt32("channel-mask", mChannelMask);
                    }
                    break;
                }

                case OMX_AUDIO_CodingAAC:
                {
                    OMX_AUDIO_PARAM_AACPROFILETYPE params;
                    InitOMXParams(&params);
                    params.nPortIndex = kPortIndexOutput;

                    CHECK_EQ(mOMX->getParameter(
                                mNode, OMX_IndexParamAudioAac,
                                &params, sizeof(params)),
                             (status_t)OK);

                    notify->setString("mime", MEDIA_MIMETYPE_AUDIO_AAC);
                    notify->setInt32("channel-count", params.nChannels);
                    notify->setInt32("sample-rate", params.nSampleRate);
                    break;
                }

                case OMX_AUDIO_CodingAMR:
                {
                    OMX_AUDIO_PARAM_AMRTYPE params;
                    InitOMXParams(&params);
                    params.nPortIndex = kPortIndexOutput;

                    CHECK_EQ(mOMX->getParameter(
                                mNode, OMX_IndexParamAudioAmr,
                                &params, sizeof(params)),
                             (status_t)OK);

                    notify->setInt32("channel-count", 1);
                    if (params.eAMRBandMode >= OMX_AUDIO_AMRBandModeWB0) {
                        notify->setString(
                                "mime", MEDIA_MIMETYPE_AUDIO_AMR_WB);

                        notify->setInt32("sample-rate", 16000);
                    } else {
                        notify->setString(
                                "mime", MEDIA_MIMETYPE_AUDIO_AMR_NB);

                        notify->setInt32("sample-rate", 8000);
                    }
                    break;
                }

                case OMX_AUDIO_CodingFLAC:
                {
                    OMX_AUDIO_PARAM_FLACTYPE params;
                    InitOMXParams(&params);
                    params.nPortIndex = kPortIndexOutput;

                    CHECK_EQ(mOMX->getParameter(
                                mNode, OMX_IndexParamAudioFlac,
                                &params, sizeof(params)),
                             (status_t)OK);

                    notify->setString("mime", MEDIA_MIMETYPE_AUDIO_FLAC);
                    notify->setInt32("channel-count", params.nChannels);
                    notify->setInt32("sample-rate", params.nSampleRate);
                    break;
                }

                default:
                    TRESPASS();
            }
            break;
        }

        default:
            TRESPASS();
    }

    notify->post();

    mSentFormat = true;
}

void ACodec::signalError(OMX_ERRORTYPE error, status_t internalError) {
    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", ACodec::kWhatError);
    notify->setInt32("omx-error", error);
#ifndef ANDROID_DEFAULT_CODE
// mtk80902: ALPS00442417 - porting error handler from OMXCodec
    if(error == OMX_ErrorStreamCorrupt)
    {                        
        ALOGW("OMXCodec::onEvent--OMX Error Stream Corrupt!!");
#ifdef MTK_AUDIO_APE_SUPPORT                
        // for ape error state to exit playback start.
        if(internalError == OMX_AUDIO_CodingAPE) {
            notify->setInt32("err", internalError);
            notify->post();
        }
        // for ape error state to exit playback end.
#endif                
        if(mIsVideoEncoder) {
            ALOGW("OMXCodec::onEvent--Video encoder error");
            notify->setInt32("err", ERROR_UNSUPPORTED_VIDEO);
            notify->post();
        }
    } else if (mIsVideoDecoder && error == OMX_ErrorBadParameter) {
        ALOGW("OMXCodec::onEvent--OMX Bad Parameter!!");
        notify->setInt32("err", ERROR_UNSUPPORTED_VIDEO);
        notify->post();
    } else if (!mIsEncoder && !mIsVideoDecoder && error == OMX_ErrorBadParameter){
        ALOGW("OMXCodec::onEvent--Audio OMX Bad Parameter!!");
        notify->setInt32("err", ERROR_UNSUPPORTED_AUDIO);
        notify->post();
    } else {
        ALOGW("OMXCodec::onEvent internalError %d", internalError);
        notify->setInt32("err", internalError);
        notify->post();
    }
#else //ANDROID_DEFAULT_CODE
    notify->setInt32("err", internalError);
    notify->post();
#endif //ANDROID_DEFAULT_CODE
}

status_t ACodec::pushBlankBuffersToNativeWindow() {
    status_t err = NO_ERROR;
    ANativeWindowBuffer* anb = NULL;
    int numBufs = 0;
    int minUndequeuedBufs = 0;
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("pushBlankBuffersToNativeWindow");
#endif //ANDROID_DEFAULT_CODE
    // We need to reconnect to the ANativeWindow as a CPU client to ensure that
    // no frames get dropped by SurfaceFlinger assuming that these are video
    // frames.
    err = native_window_api_disconnect(mNativeWindow.get(),
            NATIVE_WINDOW_API_MEDIA);
    if (err != NO_ERROR) {
        ALOGE("error pushing blank frames: api_disconnect failed: %s (%d)",
                strerror(-err), -err);
        return err;
    }

    err = native_window_api_connect(mNativeWindow.get(),
            NATIVE_WINDOW_API_CPU);
    if (err != NO_ERROR) {
        ALOGE("error pushing blank frames: api_connect failed: %s (%d)",
                strerror(-err), -err);
        return err;
    }

    err = native_window_set_buffers_geometry(mNativeWindow.get(), 1, 1,
            HAL_PIXEL_FORMAT_RGBX_8888);
    if (err != NO_ERROR) {
        ALOGE("error pushing blank frames: set_buffers_geometry failed: %s (%d)",
                strerror(-err), -err);
        goto error;
    }
    err = native_window_set_scaling_mode(mNativeWindow.get(),
                NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW);
    if (err != NO_ERROR) {
        ALOGE("error pushing blank_frames: set_scaling_mode failed: %s (%d)",
              strerror(-err), -err);
        goto error;
    }
    err = native_window_set_usage(mNativeWindow.get(),
            GRALLOC_USAGE_SW_WRITE_OFTEN);
    if (err != NO_ERROR) {
        ALOGE("error pushing blank frames: set_usage failed: %s (%d)",
                strerror(-err), -err);
        goto error;
    }

    err = mNativeWindow->query(mNativeWindow.get(),
            NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS, &minUndequeuedBufs);
    if (err != NO_ERROR) {
        ALOGE("error pushing blank frames: MIN_UNDEQUEUED_BUFFERS query "
                "failed: %s (%d)", strerror(-err), -err);
        goto error;
    }

    numBufs = minUndequeuedBufs + 1;
    err = native_window_set_buffer_count(mNativeWindow.get(), numBufs);
    if (err != NO_ERROR) {
        ALOGE("error pushing blank frames: set_buffer_count failed: %s (%d)",
                strerror(-err), -err);
        goto error;
    }

    // We  push numBufs + 1 buffers to ensure that we've drawn into the same
    // buffer twice.  This should guarantee that the buffer has been displayed
    // on the screen and then been replaced, so an previous video frames are
    // guaranteed NOT to be currently displayed.
    for (int i = 0; i < numBufs + 1; i++) {
        int fenceFd = -1;
        err = native_window_dequeue_buffer_and_wait(mNativeWindow.get(), &anb);
        if (err != NO_ERROR) {
            ALOGE("error pushing blank frames: dequeueBuffer failed: %s (%d)",
                    strerror(-err), -err);
            goto error;
        }

        sp<GraphicBuffer> buf(new GraphicBuffer(anb, false));

        // Fill the buffer with the a 1x1 checkerboard pattern ;)
        uint32_t* img = NULL;
        err = buf->lock(GRALLOC_USAGE_SW_WRITE_OFTEN, (void**)(&img));
        if (err != NO_ERROR) {
            ALOGE("error pushing blank frames: lock failed: %s (%d)",
                    strerror(-err), -err);
            goto error;
        }

        *img = 0;

        err = buf->unlock();
        if (err != NO_ERROR) {
            ALOGE("error pushing blank frames: unlock failed: %s (%d)",
                    strerror(-err), -err);
            goto error;
        }

        err = mNativeWindow->queueBuffer(mNativeWindow.get(),
                buf->getNativeBuffer(), -1);
        if (err != NO_ERROR) {
            ALOGE("error pushing blank frames: queueBuffer failed: %s (%d)",
                    strerror(-err), -err);
            goto error;
        }

        anb = NULL;
    }

error:

    if (err != NO_ERROR) {
        // Clean up after an error.
        if (anb != NULL) {
            mNativeWindow->cancelBuffer(mNativeWindow.get(), anb, -1);
        }

        native_window_api_disconnect(mNativeWindow.get(),
                NATIVE_WINDOW_API_CPU);
        native_window_api_connect(mNativeWindow.get(),
                NATIVE_WINDOW_API_MEDIA);

        return err;
    } else {
        // Clean up after success.
        err = native_window_api_disconnect(mNativeWindow.get(),
                NATIVE_WINDOW_API_CPU);
        if (err != NO_ERROR) {
            ALOGE("error pushing blank frames: api_disconnect failed: %s (%d)",
                    strerror(-err), -err);
            return err;
        }

        err = native_window_api_connect(mNativeWindow.get(),
                NATIVE_WINDOW_API_MEDIA);
        if (err != NO_ERROR) {
            ALOGE("error pushing blank frames: api_connect failed: %s (%d)",
                    strerror(-err), -err);
            return err;
        }

        return NO_ERROR;
    }
}

////////////////////////////////////////////////////////////////////////////////

ACodec::PortDescription::PortDescription() {
}

status_t ACodec::requestIDRFrame() {
    if (!mIsEncoder) {
        return ERROR_UNSUPPORTED;
    }

#ifndef ANDROID_DEFAULT_CODE
        if (!strncmp(mComponentName.c_str(), "OMX.MTK.", 8)) {
	     ALOGI("request I frame");
            OMX_INDEXTYPE index;
            status_t err =
            mOMX->getExtensionIndex(
                    mNode,
                    "OMX.MTK.index.param.video.EncSetForceIframe",
                    &index);

            if (err != OK) {
                return err;
            }

            OMX_BOOL enable = OMX_TRUE;
            err = mOMX->setConfig(mNode, index, &enable, sizeof(enable));

            if (err != OK) {
                ALOGE("setConfig('OMX.MTK.index.param.video.EncSetForceIframe') returned error 0x%08x", err);
                return err;
            }

	    return OK;
	}
        else {
	     ALOGI("request I frame - non MTK codec index(0x%08X)", OMX_IndexConfigVideoIntraVOPRefresh);
             OMX_CONFIG_INTRAREFRESHVOPTYPE params;
	     InitOMXParams(&params);
			
	     params.nPortIndex = kPortIndexOutput;
	     params.IntraRefreshVOP = OMX_TRUE;
			
	    return mOMX->setConfig(
					    mNode,
				   	    OMX_IndexConfigVideoIntraVOPRefresh,
					    &params,
					    sizeof(params));
        }
#else
    OMX_CONFIG_INTRAREFRESHVOPTYPE params;
    InitOMXParams(&params);

    params.nPortIndex = kPortIndexOutput;
    params.IntraRefreshVOP = OMX_TRUE;

    return mOMX->setConfig(
            mNode,
            OMX_IndexConfigVideoIntraVOPRefresh,
            &params,
            sizeof(params));
#endif

}

#ifndef ANDROID_DEFAULT_CODE
status_t ACodec::setVEncIInterval(int seconds) {
    if (!mIsEncoder) {
        return ERROR_UNSUPPORTED;
    }
    if (!strncmp(mComponentName.c_str(), "OMX.MTK.VIDEO.ENCODER", 21)) {
        ALOGI("set I frame rate");
        OMX_INDEXTYPE index;
        status_t err =
        mOMX->getExtensionIndex(
                mNode,
                "OMX.MTK.index.param.video.EncSetIFrameRate",
                &index);

        if (err != OK) {
            return err;
        }

        OMX_BOOL enable = OMX_TRUE;
        err = mOMX->setConfig(mNode, index, &seconds, sizeof(seconds));

        if (err != OK) {
            ALOGE("setConfig('OMX.MTK.index.param.video.EncSetIFrameRate') returned error 0x%08x", err);
            return err;
        }
    }
    return OK;
}
status_t ACodec::setVEncSkipFrame() {
    if (!mIsEncoder) {
        return ERROR_UNSUPPORTED;
    }
    ALOGI("set Skip frame");
    OMX_INDEXTYPE index;
    status_t err = 
    mOMX->getExtensionIndex(
            mNode,
            "OMX.MTK.index.param.video.EncSetSkipFrame",
            &index);

    if (err != OK) {
        return err;
    }

    OMX_BOOL enable = OMX_TRUE;
    err = mOMX->setConfig(mNode, index, 0, 0);

    if (err != OK) {
        ALOGE("setConfig('OMX.MTK.index.param.video.EncSetSkipFrame') returned error 0x%08x", err);
        return err;
    }
    return OK;
}
status_t ACodec::setVEncDrawBlack(int enable) {//for Miracast test case SIGMA 5.1.11 workaround
    if (!mIsEncoder) {
        return ERROR_UNSUPPORTED;
    }
    ALOGI("set Draw Black %d", enable);
    status_t err = mOMX->setConfig(mNode, OMX_IndexVendorMtkOmxVencDrawBlack, &enable, 0);
    if (err != OK) {
        ALOGE("setConfig('OMX_IndexVendorMtkOmxVencDrawBlack') returned error 0x%08x", err);
        return err;
    }
    return OK;
}
status_t ACodec::setVEncBitRate(int bitrate) {
    if (!mIsEncoder) {
        return ERROR_UNSUPPORTED;
    }
    if (!strncmp(mComponentName.c_str(), "OMX.MTK.VIDEO.ENCODER", 21)) {
        ALOGI("set bitrate");

        OMX_BOOL enable = OMX_TRUE;
        OMX_VIDEO_CONFIG_BITRATETYPE    bitrateType;
        bitrateType.nEncodeBitrate = bitrate;
        status_t err = mOMX->setConfig(mNode, OMX_IndexConfigVideoBitrate, &bitrateType, sizeof(bitrateType));

        if (err != OK) {
            ALOGE("setConfig(OMX_IndexConfigVideoBitrate) returned error 0x%08x", err);
            return err;
        }
    }
    return OK;
}
status_t ACodec::setVEncFrameRate(int framerate) {
    if (!mIsEncoder) {
        return ERROR_UNSUPPORTED;
    }
    if (!strncmp(mComponentName.c_str(), "OMX.MTK.VIDEO.ENCODER", 21)) {
        ALOGI("set framerate");

        OMX_BOOL enable = OMX_TRUE;
        OMX_CONFIG_FRAMERATETYPE    framerateType;
        framerateType.xEncodeFramerate = framerate<<16;
        status_t err = mOMX->setConfig(mNode, OMX_IndexConfigVideoFramerate, &framerateType, sizeof(framerateType));

        if (err != OK) {
            ALOGE("setConfig(OMX_IndexConfigVideoFramerate) returned error 0x%08x", err);
            return err;
        }
    }
    return OK;
}
#endif//ANDROID_DEFAULT_CODE
void ACodec::PortDescription::addBuffer(
        IOMX::buffer_id id, const sp<ABuffer> &buffer) {
    mBufferIDs.push_back(id);
    mBuffers.push_back(buffer);
}

size_t ACodec::PortDescription::countBuffers() {
    return mBufferIDs.size();
}

IOMX::buffer_id ACodec::PortDescription::bufferIDAt(size_t index) const {
    return mBufferIDs.itemAt(index);
}

sp<ABuffer> ACodec::PortDescription::bufferAt(size_t index) const {
    return mBuffers.itemAt(index);
}

////////////////////////////////////////////////////////////////////////////////

ACodec::BaseState::BaseState(ACodec *codec, const sp<AState> &parentState)
    : AState(parentState),
      mCodec(codec) {
}

ACodec::BaseState::PortMode ACodec::BaseState::getPortMode(OMX_U32 portIndex) {
    return KEEP_BUFFERS;
}

bool ACodec::BaseState::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatInputBufferFilled:
        {
            onInputBufferFilled(msg);
            break;
        }

        case kWhatOutputBufferDrained:
        {
            onOutputBufferDrained(msg);
            break;
        }

        case ACodec::kWhatOMXMessage:
        {
            return onOMXMessage(msg);
        }

        case ACodec::kWhatCreateInputSurface:
        case ACodec::kWhatSignalEndOfInputStream:
        {
            ALOGE("Message 0x%x was not handled", msg->what());
            mCodec->signalError(OMX_ErrorUndefined, INVALID_OPERATION);
            return true;
        }

        case ACodec::kWhatOMXDied:
        {
            ALOGE("OMX/mediaserver died, signalling error!");
            mCodec->signalError(OMX_ErrorResourcesLost, DEAD_OBJECT);
            break;
        }

        default:
            return false;
    }

    return true;
}

bool ACodec::BaseState::onOMXMessage(const sp<AMessage> &msg) {
    int32_t type;
    CHECK(msg->findInt32("type", &type));

    IOMX::node_id nodeID;
    CHECK(msg->findPointer("node", &nodeID));
    CHECK_EQ(nodeID, mCodec->mNode);
#ifndef ANDROID_DEFAULT_CODE
    ALOGV("BaseState::onOMXMessage type %x", type);
#endif //ANDROID_DEFAULT_CODE
    switch (type) {
        case omx_message::EVENT:
        {
            int32_t event, data1, data2;
            CHECK(msg->findInt32("event", &event));
            CHECK(msg->findInt32("data1", &data1));
            CHECK(msg->findInt32("data2", &data2));

            if (event == OMX_EventCmdComplete
                    && data1 == OMX_CommandFlush
                    && data2 == (int32_t)OMX_ALL) {
                // Use of this notification is not consistent across
                // implementations. We'll drop this notification and rely
                // on flush-complete notifications on the individual port
                // indices instead.

                return true;
            }

            return onOMXEvent(
                    static_cast<OMX_EVENTTYPE>(event),
                    static_cast<OMX_U32>(data1),
                    static_cast<OMX_U32>(data2));
        }

        case omx_message::EMPTY_BUFFER_DONE:
        {
            IOMX::buffer_id bufferID;
            CHECK(msg->findPointer("buffer", &bufferID));

#ifdef MTB_SUPPORT
		    if (!strncmp(mCodec->mComponentName.c_str(), "OMX.MTK.VIDEO.ENCODER", 21)) {
    		    ATRACE_ONESHOT(ATRACE_ONESHOT_SPECIAL, "ACodecVEncEBD, bufferID: %p", bufferID);   
			
		    }
            else {
    		    ATRACE_ONESHOT(ATRACE_ONESHOT_SPECIAL, "ACodecAEncEBD, bufferID: %p", bufferID);   
	        }  
#endif			
            return onOMXEmptyBufferDone(bufferID);
        }

        case omx_message::FILL_BUFFER_DONE:
        {
            IOMX::buffer_id bufferID;
            CHECK(msg->findPointer("buffer", &bufferID));

            int32_t rangeOffset, rangeLength, flags;
            int64_t timeUs;
            void *platformPrivate;
            void *dataPtr;

            CHECK(msg->findInt32("range_offset", &rangeOffset));
            CHECK(msg->findInt32("range_length", &rangeLength));
            CHECK(msg->findInt32("flags", &flags));
            CHECK(msg->findInt64("timestamp", &timeUs));
            CHECK(msg->findPointer("platform_private", &platformPrivate));
            CHECK(msg->findPointer("data_ptr", &dataPtr));

#ifdef MTB_SUPPORT            
            if (!strncmp(mCodec->mComponentName.c_str(), "OMX.MTK.VIDEO.ENCODER", 21)) {
                ATRACE_ONESHOT(ATRACE_ONESHOT_SPECIAL, "ACodecVEncFBD, bufferID: %p,TS: %lld ms", bufferID, timeUs/1000);   
            }
            else {
                ATRACE_ONESHOT(ATRACE_ONESHOT_SPECIAL, "ACodecAEncFBD, bufferID: %p,TS: %lld ms", bufferID, timeUs/1000);   
            }
#endif

#ifndef ANDROID_DEFAULT_CODE
            if( (!strncmp("OMX.MTK.VIDEO.DECODER.", mCodec->mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mCodec->mIsDemandNormalYUV==true))
            {
#ifdef USE_COLOR_CONVERT_MVA
                void *platformPrivatePa;
                void *platformPrivateVa;
                MTK_PLATFORM_PRIVATE mplatform_privateK;

                CHECK(msg->findPointer("platform_privateVa", &platformPrivateVa));
                CHECK(msg->findPointer("platform_private", &platformPrivatePa));
                mplatform_privateK.mM4UVABufferVa = (unsigned long)platformPrivateVa;
                mplatform_privateK.mM4UMVABufferPa = (unsigned long)platformPrivatePa;
                ALOGD("Tid %d, mM4UMVABufferPa %p, mM4UVABufferVa %p", androidGetTid(), mplatform_privateK.mM4UMVABufferPa, mplatform_privateK.mM4UVABufferVa);
                //Note: For MediaCodec color convertion do ReAssign this parameter, platformPrivate. 
                //If somebody needs "platformPrivate" should refine this design.
                platformPrivate = &mplatform_privateK;
#endif //USE_COLOR_CONVERT_MVA
            }
#endif //ANDROID_DEFAULT_CODE
            return onOMXFillBufferDone(
                    bufferID,
                    (size_t)rangeOffset, (size_t)rangeLength,
                    (OMX_U32)flags,
                    timeUs,
                    platformPrivate,
                    dataPtr);
        }

        default:
            TRESPASS();
            break;
    }
}

bool ACodec::BaseState::onOMXEvent(
        OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2) {
    if (event != OMX_EventError) {
        ALOGV("[%s] EVENT(%d, 0x%08lx, 0x%08lx)",
             mCodec->mComponentName.c_str(), event, data1, data2);

        return false;
    }

    ALOGE("[%s] ERROR(0x%08lx)", mCodec->mComponentName.c_str(), data1);

    mCodec->signalError((OMX_ERRORTYPE)data1);

    return true;
}

bool ACodec::BaseState::onOMXEmptyBufferDone(IOMX::buffer_id bufferID) {
    ALOGV("[%s] onOMXEmptyBufferDone %p",
         mCodec->mComponentName.c_str(), bufferID);

    BufferInfo *info =
        mCodec->findBufferByID(kPortIndexInput, bufferID);

    CHECK_EQ((int)info->mStatus, (int)BufferInfo::OWNED_BY_COMPONENT);
    info->mStatus = BufferInfo::OWNED_BY_US;

    const sp<AMessage> &bufferMeta = info->mData->meta();
    void *mediaBuffer;
    if (bufferMeta->findPointer("mediaBuffer", &mediaBuffer)
            && mediaBuffer != NULL) {
        // We're in "store-metadata-in-buffers" mode, the underlying
        // OMX component had access to data that's implicitly refcounted
        // by this "mediaBuffer" object. Now that the OMX component has
        // told us that it's done with the input buffer, we can decrement
        // the mediaBuffer's reference count.

        ALOGV("releasing mbuf %p", mediaBuffer);

        ((MediaBuffer *)mediaBuffer)->release();
#ifndef ANDROID_DEFAULT_CODE
        if (!strncmp(mCodec->mComponentName.c_str(), "OMX.MTK.VIDEO.ENCODER", 21)) {
            ALOGI("[video buffer]onOMXEmptyBufferDone  :  releasing mediaBuffer %p,refcount after release=%d", 
                                      mediaBuffer,((MediaBuffer *)mediaBuffer)->refcount() );
        }
#endif //ANDROID_DEFAULT_CODE

        mediaBuffer = NULL;

        bufferMeta->setPointer("mediaBuffer", NULL);
    }

    PortMode mode = getPortMode(kPortIndexInput);

    switch (mode) {
        case KEEP_BUFFERS:
            break;

        case RESUBMIT_BUFFERS:
#ifndef ANDROID_DEFAULT_CODE
            // mtk80902: porting from AwesomePlayer: prevent buffering twice
            if (mCodec->mMaxQueueBufferNum > 0) {
                size_t n = mCodec->mBuffers[kPortIndexInput].size();
                size_t others = 0;
                for (size_t i = 0; i < n; ++i) {
                    BufferInfo *info = &mCodec->mBuffers[kPortIndexInput].editItemAt(i);
                    if (info->mStatus == BufferInfo::OWNED_BY_COMPONENT)
                        others++;
                }

                if (mCodec->mMaxQueueBufferNum < others) {
                    ALOGV("mMaxQueueBufferNum %d < component occupied %d, wait for next trigger.",
                    mCodec->mMaxQueueBufferNum, others);
                    break;
                }
            }
#endif //ANDROID_DEFAULT_CODE
            postFillThisBuffer(info);
            break;

        default:
        {
            CHECK_EQ((int)mode, (int)FREE_BUFFERS);
            TRESPASS();  // Not currently used
            break;
        }
    }

    return true;
}

void ACodec::BaseState::postFillThisBuffer(BufferInfo *info) {
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("ACodec::BaseState::postFillThisBuffer mPortEOS %d", mCodec->mPortEOS[kPortIndexInput]);
#endif //ANDROID_DEFAULT_CODE
    if (mCodec->mPortEOS[kPortIndexInput]) {
        return;
    }

    CHECK_EQ((int)info->mStatus, (int)BufferInfo::OWNED_BY_US);

#ifndef ANDROID_DEFAULT_CODE
    if (mCodec->mLeftOverBuffer != NULL) {
        ALOGD("[%s] left over buffer (id = %p)", 
               mCodec->mComponentName.c_str(), info->mBufferID);
        info->mData->meta()->clear();

        sp<AMessage> reply = new AMessage(kWhatInputBufferFilled, mCodec->id());
        reply->setPointer("buffer-id", info->mBufferID);
        reply->setBuffer("buffer", mCodec->mLeftOverBuffer);
        mCodec->mLeftOverBuffer = NULL;
//        reply->setInt32("partial", 1);
        reply->post();

        info->mStatus = BufferInfo::OWNED_BY_UPSTREAM;
        return;
    }
#endif
    sp<AMessage> notify = mCodec->mNotify->dup();
    notify->setInt32("what", ACodec::kWhatFillThisBuffer);
    notify->setPointer("buffer-id", info->mBufferID);


#ifndef ANDROID_DEFAULT_CODE
   {
            
         void *mediaBuffer;
         if(info->mData->meta()->findPointer("mediaBuffer", &mediaBuffer)
                 && mediaBuffer != NULL){
             //ALOGI("postFillThisBuffer release mediabuffer");
             ((MediaBuffer *)mediaBuffer)->release();
         }
        info->mData->meta()->clear();
    }
#else
    info->mData->meta()->clear();
#endif


    notify->setBuffer("buffer", info->mData);

    sp<AMessage> reply = new AMessage(kWhatInputBufferFilled, mCodec->id());
    reply->setPointer("buffer-id", info->mBufferID);

    notify->setMessage("reply", reply);

    notify->post();

    info->mStatus = BufferInfo::OWNED_BY_UPSTREAM;
}

void ACodec::BaseState::onInputBufferFilled(const sp<AMessage> &msg) {
    IOMX::buffer_id bufferID;
    CHECK(msg->findPointer("buffer-id", &bufferID));
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("onInputBufferFilled bufferID %x", bufferID);
#endif //ANDROID_DEFAULT_CODE
    sp<ABuffer> buffer;
    int32_t err = OK;
    bool eos = false;
    PortMode mode = getPortMode(kPortIndexInput);

    if (!msg->findBuffer("buffer", &buffer)) {
        /* these are unfilled buffers returned by client */
        CHECK(msg->findInt32("err", &err));

        if (err == OK) {
            /* buffers with no errors are returned on MediaCodec.flush */
            mode = KEEP_BUFFERS;
        } else {
#ifndef ANDROID_DEFAULT_CODE
            ALOGD("[%s] saw error %d instead of an input buffer",
                 mCodec->mComponentName.c_str(), err);
#else
            ALOGV("[%s] saw error %d instead of an input buffer",
                 mCodec->mComponentName.c_str(), err);
#endif //ANDROID_DEFAULT_CODE
            eos = true;
        }

        buffer.clear();
    }
    if (buffer != NULL )
        ALOGD("buffer->size() %d", buffer->size());

    
    int32_t tmp;
    if (buffer != NULL && buffer->meta()->findInt32("eos", &tmp) && tmp) {
        ALOGD("InputBuffer EOS");
        eos = true;
        err = ERROR_END_OF_STREAM;
    }

    BufferInfo *info = mCodec->findBufferByID(kPortIndexInput, bufferID);
    CHECK_EQ((int)info->mStatus, (int)BufferInfo::OWNED_BY_UPSTREAM);

   /*
    if (msg->findInt32("partial", &tmp)) {
        int64_t tt;
        buffer->meta()->findInt64("timeUs", &tt);
        ALOGD("partial frame filled, %lld, %p, size = %d", tt, buffer->data(), (int)buffer->size());
        ALOGD("\t\t%p (%d %p), capacity, size = %d", bufferID,  info->mData->offset(), info->mData->data(), info->mData->capacity());
    }
    */
    info->mStatus = BufferInfo::OWNED_BY_US;

#ifndef ANDROID_DEFAULT_CODE
#ifdef DUMP_BITSTREAM
    if (buffer != NULL) {
        int64_t tt;
        int32_t isCSD = false;
        buffer->meta()->findInt64("timeUs", &tt);
        ALOGD("[%s]buffer to be empty, %lld, %p, size = %d", mCodec->mComponentName.c_str(), tt, buffer->data(), (int)buffer->size());
        buffer->meta()->findInt32("csd", &isCSD) ;
        if (buffer->size() >= 4) {
            ALOGD("[%s]\t\t %s, %02x %02x %02x %02x", 
                    mCodec->mComponentName.c_str(), 
                    isCSD ? "codec_cfg":"", 
                    buffer->data()[0], buffer->data()[1] , buffer->data()[2] , buffer->data()[3]);
        }

        if ((mCodec->mDumpFile != NULL) && 
                (!strcmp(mCodec->mComponentName.c_str(), "OMX.MTK.VIDEO.DECODER.AVC"))) {
            if (!isCSD) {
                char nal_prefix[] = {0, 0, 0, 1};
                fwrite(nal_prefix, 1, 4, mCodec->mDumpFile);
            }
            size_t nWrite = fwrite(buffer->data(), 1, buffer->size(), mCodec->mDumpFile);
            ALOGD("written %d bytes, ftell = %d", nWrite, (int)ftell(mCodec->mDumpFile));
        }
    }
#endif
#endif
    switch (mode) {
        case KEEP_BUFFERS:
        {
            if (eos) {
                if (!mCodec->mPortEOS[kPortIndexInput]) {
                    mCodec->mPortEOS[kPortIndexInput] = true;
                    mCodec->mInputEOSResult = err;
                }
            }
            break;
        }

        case RESUBMIT_BUFFERS:
        {
            if (buffer != NULL && !mCodec->mPortEOS[kPortIndexInput]) {
                int64_t timeUs;
                CHECK(buffer->meta()->findInt64("timeUs", &timeUs));

                OMX_U32 flags = OMX_BUFFERFLAG_ENDOFFRAME;

                int32_t isCSD;
                if (buffer->meta()->findInt32("csd", &isCSD) && isCSD != 0) {
#ifndef ANDROID_DEFAULT_CODE
                    ALOGI("[%s] received csd settings.", mCodec->mComponentName.c_str());
#endif
                    flags |= OMX_BUFFERFLAG_CODECCONFIG;
                }

                if (eos) {
                    flags |= OMX_BUFFERFLAG_EOS;
                }

                if (buffer != info->mData) {
                    ALOGV("[%s] Needs to copy input data for buffer %p. (%p != %p)",
                         mCodec->mComponentName.c_str(),
                         bufferID,
                         buffer.get(), info->mData.get());
#ifndef ANDROID_DEFAULT_CODE
                    int capacity = info->mData->capacity();
                    if (buffer->size() > capacity) {
                        if (mCodec->mSupportsPartialFrames) {
                            sp<ABuffer> leftBuffer = new ABuffer(buffer->size() - capacity);
                            memcpy(leftBuffer->data(), buffer->data() + capacity, buffer->size() - capacity);
                            leftBuffer->meta()->setInt64("timeUs", timeUs);
                            if (isCSD) {
                                leftBuffer->meta()->setInt32("csd", isCSD);
                            }

                            ALOGI("[%s] split big input buffer %d to %d + %d",
                                    mCodec->mComponentName.c_str(),  buffer->size(), capacity, leftBuffer->size());

                            buffer->setRange(buffer->offset(), capacity);
                            flags &= ~OMX_BUFFERFLAG_ENDOFFRAME;

                            mCodec->mLeftOverBuffer = leftBuffer;
                        } else {
                            ALOGE("Codec's input buffers are too small to accomodate "
                                    " buffer read from source (info->mSize = %d, srcLength = %d)",
                                    info->mData->capacity(), buffer->size());
                            mCodec->signalError();
                            break;
                            //CHECK_LE(buffer->size(), info->mData->capacity());
                        }
                    }
#else
                    CHECK_LE(buffer->size(), info->mData->capacity());
#endif
                    memcpy(info->mData->data(), buffer->data(), buffer->size());
                }

                if (flags & OMX_BUFFERFLAG_CODECCONFIG) {
#ifndef ANDROID_DEFAULT_CODE
                    ALOGD("[%s] calling emptyBuffer %p w/ codec specific data",
                         mCodec->mComponentName.c_str(), bufferID);
#else
                    ALOGV("[%s] calling emptyBuffer %p w/ codec specific data",
                         mCodec->mComponentName.c_str(), bufferID);
#endif //ANDROID_DEFAULT_CODE
                } else if (flags & OMX_BUFFERFLAG_EOS) {
#ifndef ANDROID_DEFAULT_CODE
                    ALOGD("[%s] calling emptyBuffer %p w/ EOS",
                         mCodec->mComponentName.c_str(), bufferID);
#else
                    ALOGV("[%s] calling emptyBuffer %p w/ EOS",
                         mCodec->mComponentName.c_str(), bufferID);
#endif //ANDROID_DEFAULT_CODE
                } else {
#if TRACK_BUFFER_TIMING
                    ALOGI("[%s] calling emptyBuffer %p w/ time %lld us",
                         mCodec->mComponentName.c_str(), bufferID, timeUs);
#else
                    ALOGV("[%s] calling emptyBuffer %p w/ time %lld us",
                         mCodec->mComponentName.c_str(), bufferID, timeUs);
#endif
                }

#if TRACK_BUFFER_TIMING
                ACodec::BufferStats stats;
                stats.mEmptyBufferTimeUs = ALooper::GetNowUs();
                stats.mFillBufferDoneTimeUs = -1ll;
                mCodec->mBufferStats.add(timeUs, stats);
#endif
                if (mCodec->mStoreMetaDataInOutputBuffers) {
                    // try to submit an output buffer for each input buffer
                    PortMode outputMode = getPortMode(kPortIndexOutput);

                    ALOGD("MetaDataBuffersToSubmit=%u portMode=%s",
                            mCodec->mMetaDataBuffersToSubmit,
                            (outputMode == FREE_BUFFERS ? "FREE" :
                             outputMode == KEEP_BUFFERS ? "KEEP" : "RESUBMIT"));
                    if (outputMode == RESUBMIT_BUFFERS) {
                        CHECK_EQ(mCodec->submitOutputMetaDataBuffer(),
                                (status_t)OK);
                    }
                }
                CHECK_EQ(mCodec->mOMX->emptyBuffer(
                            mCodec->mNode,
                            bufferID,
                            0,
                            buffer->size(),
                            flags,
                            timeUs),
                         (status_t)OK);

                info->mStatus = BufferInfo::OWNED_BY_COMPONENT;

                if (!eos) {
                    getMoreInputDataIfPossible();
                } else {
                    ALOGD("[%s] Signalled EOS on the input port",
                         mCodec->mComponentName.c_str());

                    mCodec->mPortEOS[kPortIndexInput] = true;
                    mCodec->mInputEOSResult = err;
                }
            } else if (!mCodec->mPortEOS[kPortIndexInput]) {
                if (err != ERROR_END_OF_STREAM) {
                    ALOGV("[%s] Signalling EOS on the input port "
                         "due to error %d",
                         mCodec->mComponentName.c_str(), err);
                } else {
                    ALOGV("[%s] Signalling EOS on the input port",
                         mCodec->mComponentName.c_str());
                }

                ALOGV("[%s] calling emptyBuffer %p signalling EOS",
                     mCodec->mComponentName.c_str(), bufferID);

                CHECK_EQ(mCodec->mOMX->emptyBuffer(
                            mCodec->mNode,
                            bufferID,
                            0,
                            0,
                            OMX_BUFFERFLAG_EOS,
                            0),
                         (status_t)OK);

                info->mStatus = BufferInfo::OWNED_BY_COMPONENT;

                mCodec->mPortEOS[kPortIndexInput] = true;
                mCodec->mInputEOSResult = err;
            }
            break;

            default:
                CHECK_EQ((int)mode, (int)FREE_BUFFERS);
                break;
        }
    }
}

void ACodec::BaseState::getMoreInputDataIfPossible() {
    if (mCodec->mPortEOS[kPortIndexInput]) {
        return;
    }

    BufferInfo *eligible = NULL;

    for (size_t i = 0; i < mCodec->mBuffers[kPortIndexInput].size(); ++i) {
        BufferInfo *info = &mCodec->mBuffers[kPortIndexInput].editItemAt(i);

#if 0
        if (info->mStatus == BufferInfo::OWNED_BY_UPSTREAM) {
            // There's already a "read" pending.
            return;
        }
#endif

        if (info->mStatus == BufferInfo::OWNED_BY_US) {
            eligible = info;
        }
    }

    if (eligible == NULL) {
        return;
    }

    postFillThisBuffer(eligible);
}

bool ACodec::BaseState::onOMXFillBufferDone(
        IOMX::buffer_id bufferID,
        size_t rangeOffset, size_t rangeLength,
        OMX_U32 flags,
        int64_t timeUs,
        void *platformPrivate,
        void *dataPtr) {   
    ALOGV("[%s] onOMXFillBufferDone %p time %lld us, flags = 0x%08lx, rangeLength %d, platformPrivate %p",
         mCodec->mComponentName.c_str(), bufferID, timeUs, flags, rangeLength, platformPrivate );

    ssize_t index;

#if TRACK_BUFFER_TIMING
    index = mCodec->mBufferStats.indexOfKey(timeUs);
    if (index >= 0) {
        ACodec::BufferStats *stats = &mCodec->mBufferStats.editValueAt(index);
        stats->mFillBufferDoneTimeUs = ALooper::GetNowUs();

        ALOGI("frame PTS %lld: %lld",
                timeUs,
                stats->mFillBufferDoneTimeUs - stats->mEmptyBufferTimeUs);

        mCodec->mBufferStats.removeItemsAt(index);
        stats = NULL;
    }
#endif
#ifndef ANDROID_DEFAULT_CODE
    BufferInfo *infoOmx = NULL;
    BufferInfo *info = NULL;
    size_t mRangeLength = 0;

    //check OMX.MTK.VIDEO.DECODER. prefix for video decoder only
    if( (!strncmp("OMX.MTK.VIDEO.DECODER.", mCodec->mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mCodec->mIsDemandNormalYUV==true))
    {
        ssize_t index_tmp;
        uint32_t *outputportPtr = (uint32_t *)platformPrivate;
        //get bufferInfo from bufferID in OMX component
        infoOmx = mCodec->findBufferByIDForColorConvert(kPortIndexOutputForColorConvert, bufferID, &index_tmp);
        //get bufferInfo from bufferID in convert component for JAVA 
        info = mCodec->findBufferByID(kPortIndexOutput, bufferID, &index);
         
        //do Dpframeowork convert
        OMX_PARAM_PORTDEFINITIONTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexOutput;
        CHECK_EQ(mCodec->mOMX->getParameter(
                    mCodec->mNode, OMX_IndexParamPortDefinition, &def, sizeof(def)),
                 (status_t)OK);
        OMX_VIDEO_PORTDEFINITIONTYPE *videoDef = &def.format.video;
        LOGV("nFrameWidth %d, nFrameHeight %d, src %p, dts %p, outputportPtr(MVA) %p", videoDef->nFrameWidth, 
        videoDef->nFrameHeight, infoOmx->mData->data(), info->mData->data(), outputportPtr);
        //calculate this for normal YUV size that no need to align 32/16
        mRangeLength = YUV_SIZE(videoDef->nFrameWidth, videoDef->nFrameHeight);
        ARect mtmpRec;
        mtmpRec.top = mtmpRec.left = 0;
        mtmpRec.right = mtmpRec.left + videoDef->nFrameWidth-1;
        mtmpRec.bottom = mtmpRec.top + videoDef->nFrameHeight-1;
        OMX_U32 mSrcFormat = eYUV_420_3P;
        //TBD, tell video output format here to do color convert or not
        //if (!strncmp("OMX.MTK.", mComponentName.c_str(), 8)) 
        {
            uint32_t eHalColorFormat = HAL_PIXEL_FORMAT_YV12;//tmp for build pass
            switch (def.format.video.eColorFormat) {
                case OMX_COLOR_FormatYUV420Planar:
                    eHalColorFormat = HAL_PIXEL_FORMAT_YV12;
                    //eHalColorFormat = HAL_PIXEL_FORMAT_I420;
                    mSrcFormat = eYUV_420_3P;
                    break;
                case OMX_MTK_COLOR_FormatYV12:
                    eHalColorFormat = HAL_PIXEL_FORMAT_YV12;
                    mSrcFormat = eYUV_420_3P_YVU;
                    break;
                case OMX_COLOR_FormatVendorMTKYUV:
                    eHalColorFormat = HAL_PIXEL_FORMAT_NV12_BLK;
                    mSrcFormat = eNV12_BLK;
                    break;
                default:
                    //eHalColorFormat = HAL_PIXEL_FORMAT_YV12;
                    eHalColorFormat = HAL_PIXEL_FORMAT_I420;
                    mSrcFormat = eYUV_420_3P;
                    break;           
            }
            ALOGV ("eHalColorFormat(%d)", eHalColorFormat);
        }
        if( NULL != mCodec->mM4UBufferHandle )
            mRangeLength = Mtk_ACodecConvertDecoderOutputToI420( outputportPtr, videoDef->nFrameWidth, videoDef->nFrameHeight, mtmpRec, info->mData->data(), mSrcFormat );
        else//not support MVA mode
            mRangeLength = Mtk_ACodecConvertDecoderOutputToI420( infoOmx->mData->data(), videoDef->nFrameWidth, videoDef->nFrameHeight, mtmpRec, info->mData->data(), mSrcFormat );

#ifdef DUMP_RAW
        if (info->mData->data() != NULL) {
            //if dump MTK blk yuv, size: YUV_SIZE(ROUND_16(videoDef->nFrameWidth), ROUND_32(videoDef->nFrameHeight))
            if ( ( 0 != rangeLength ) && (mCodec->mDumpRawFile != NULL) && 
                    (!strncmp(mCodec->mComponentName.c_str(), "OMX.MTK.VIDEO.DECODER.", strlen("OMX.MTK.VIDEO.DECODER.")))) {
                size_t nWrite = fwrite(info->mData->data(), 1, mRangeLength,  mCodec->mDumpRawFile);
                ALOGD("Raw written %d bytes, mRangeLength = %d, ftell = %d", nWrite, mRangeLength, (int)ftell(mCodec->mDumpRawFile));
            }
        }
#endif //DUMP_RAW
#if COLOR_CONVERT_PROFILING
        if( (OMX_BUFFERFLAG_EOS & flags) == OMX_BUFFERFLAG_EOS )
        {
            ALOGI("color convert time during %lld ~ %lld, avg %lld micro sec", mCodec->mProfileColorConvout_timeMin, mCodec->mProfileColorConvout_timeMax, mCodec->mProfileColorConvout_timeAvg);
        }
#endif //COLOR_CONVERT_PROFILING
    }
    else
    {
        info =
            mCodec->findBufferByID(kPortIndexOutput, bufferID, &index);
    }
#else
    BufferInfo *info =
        mCodec->findBufferByID(kPortIndexOutput, bufferID, &index);
#endif //ANDROID_DEFAULT_CODE

    CHECK_EQ((int)info->mStatus, (int)BufferInfo::OWNED_BY_COMPONENT);
    info->mDequeuedAt = ++mCodec->mDequeueCounter;
    info->mStatus = BufferInfo::OWNED_BY_US;

    PortMode mode = getPortMode(kPortIndexOutput);

    switch (mode) {
        case KEEP_BUFFERS:
            break;

        case RESUBMIT_BUFFERS:
        {
            if (rangeLength == 0 && !(flags & OMX_BUFFERFLAG_EOS)) {

#ifndef ANDROID_DEFAULT_CODE
                //check OMX.MTK.VIDEO.DECODER. prefix for video decoder only
                if((!strncmp("OMX.MTK.VIDEO.DECODER.", mCodec->mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mCodec->mIsDemandNormalYUV==true))
                {
                    ALOGV("[%s] calling fillBuffer infoOmx %p",
                         mCodec->mComponentName.c_str(), infoOmx->mBufferID);
                    if (mCodec->mPortEOS[kPortIndexOutput])
                    {//Bruce 2013/01/21 if after eos, we don't send fill_this_buffer again, or it may cause busy loop on Mtk Omx component
                        ALOGE("Output already EOS!");
                        break;
                    }
                    CHECK_EQ(mCodec->mOMX->fillBuffer(
                                mCodec->mNode, infoOmx->mBufferID),
                             (status_t)OK);
                    
                    infoOmx->mStatus = BufferInfo::OWNED_BY_COMPONENT;
                    info->mStatus = BufferInfo::OWNED_BY_COMPONENT;

                }
                else
                {
                    ALOGV("[%s] calling fillBuffer %p",
                         mCodec->mComponentName.c_str(), info->mBufferID);
                    if (mCodec->mPortEOS[kPortIndexOutput])
                    {//Bruce 2013/01/21 if after eos, we don't send fill_this_buffer again, or it may cause busy loop on Mtk Omx component
                        ALOGE("Output already EOS!");
                        break;
                    }
                    CHECK_EQ(mCodec->mOMX->fillBuffer(
                                mCodec->mNode, info->mBufferID),
                             (status_t)OK);
                    
                    info->mStatus = BufferInfo::OWNED_BY_COMPONENT;

                }
#else
                ALOGV("[%s] calling fillBuffer %p",
                     mCodec->mComponentName.c_str(), info->mBufferID);
#ifndef ANDROID_DEFAULT_CODE
                if (mCodec->mPortEOS[kPortIndexOutput])
                {//Bruce 2013/01/21 if after eos, we don't send fill_this_buffer again, or it may cause busy loop on Mtk Omx component
                    ALOGE("Output already EOS!");
                    break;
                }
#endif//ANDROID_DEFAULT_CODE
                CHECK_EQ(mCodec->mOMX->fillBuffer(
                            mCodec->mNode, info->mBufferID),
                         (status_t)OK);

                info->mStatus = BufferInfo::OWNED_BY_COMPONENT;
#endif //ANDROID_DEFAULT_CODE
                break;
            }
            sp<AMessage> reply =
                new AMessage(kWhatOutputBufferDrained, mCodec->id());

            if (!mCodec->mSentFormat) {
                mCodec->sendFormatChange(reply);
            }

            if (mCodec->mUseMetadataOnEncoderOutput) {
                native_handle_t* handle =
                        *(native_handle_t**)(info->mData->data() + 4);
                info->mData->meta()->setPointer("handle", handle);
                info->mData->meta()->setInt32("rangeOffset", rangeOffset);
                info->mData->meta()->setInt32("rangeLength", rangeLength);
            } else {
#ifndef ANDROID_DEFAULT_CODE
            //check OMX.MTK.VIDEO.DECODER. prefix for video decoder only
            if( (!strncmp("OMX.MTK.VIDEO.DECODER.", mCodec->mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mCodec->mIsDemandNormalYUV==true))
            {
                //for normal YUV, set rangeLength without specific alighments and update 0 size from FBD msg
                if( rangeLength == 0 )
                    info->mData->setRange(rangeOffset, rangeLength);
                else
                    info->mData->setRange(rangeOffset, mRangeLength);
                ALOGV("set rangeLength %d, modified mRangeLength %d", rangeLength, mRangeLength);
            }
            else
                info->mData->setRange(rangeOffset, rangeLength);
#else
            info->mData->setRange(rangeOffset, rangeLength);

#endif //ANDROID_DEFAULT_CODE
            }
#if 0
            if (mCodec->mNativeWindow == NULL) {
                if (IsIDR(info->mData)) {
                    ALOGI("IDR frame");
                }
            }
#endif

            if (mCodec->mSkipCutBuffer != NULL) {
                mCodec->mSkipCutBuffer->submit(info->mData);
            }
            info->mData->meta()->setInt64("timeUs", timeUs);

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_CLEARMOTION_SUPPORT
            if (flags & OMX_BUFFERFLAG_INTERPOLATE_FRAME) {
	        info->mData->meta()->setInt32("interpolateframe", 1);
	    }
#endif
#endif

            sp<AMessage> notify = mCodec->mNotify->dup();
            notify->setInt32("what", ACodec::kWhatDrainThisBuffer);
            notify->setPointer("buffer-id", info->mBufferID);
            notify->setBuffer("buffer", info->mData);
            notify->setInt32("flags", flags);


            //sp<AMessage> reply =
            //    new AMessage(kWhatOutputBufferDrained, mCodec->id());

            reply->setPointer("buffer-id", info->mBufferID);

            notify->setMessage("reply", reply);

            notify->post();

#ifndef ANDROID_DEFAULT_CODE
            //check OMX.MTK.VIDEO.DECODER. prefix for video decoder only
            if( (!strncmp("OMX.MTK.VIDEO.DECODER.", mCodec->mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mCodec->mIsDemandNormalYUV==true))
            {
                infoOmx->mStatus = BufferInfo::OWNED_BY_DOWNSTREAM;
            }
#endif //ANDROID_DEFAULT_CODE
            info->mStatus = BufferInfo::OWNED_BY_DOWNSTREAM;

            if (flags & OMX_BUFFERFLAG_EOS) {
                ALOGV("[%s] saw output EOS", mCodec->mComponentName.c_str());

                sp<AMessage> notify = mCodec->mNotify->dup();
                notify->setInt32("what", ACodec::kWhatEOS);
                notify->setInt32("err", mCodec->mInputEOSResult);
                notify->post();

                mCodec->mPortEOS[kPortIndexOutput] = true;
            }
            break;
        }

        default:
        {
            CHECK_EQ((int)mode, (int)FREE_BUFFERS);

            CHECK_EQ((status_t)OK,
                     mCodec->freeBuffer(kPortIndexOutput, index));
            break;
        }
    }

    return true;
}

void ACodec::BaseState::onOutputBufferDrained(const sp<AMessage> &msg) {
    IOMX::buffer_id bufferID;
    CHECK(msg->findPointer("buffer-id", &bufferID));

#ifndef ANDROID_DEFAULT_CODE
    ALOGV("onOutputBufferDrained bufferID %x", bufferID);

    int64_t delayTimeUs; 
    int64_t realTimeUs;
    if( msg->findInt64("delaytimeus", &delayTimeUs) && msg->findInt64("realtimeus", &realTimeUs)) {
        int64_t realDelayTimeUs = realTimeUs - ALooper::GetNowUs();

        if (realDelayTimeUs > delayTimeUs) {
            ALOGW("realDelayTimeUs(%lldus) is latger than delayTimeUs(%lldus), reset it to delayTimeUs", realDelayTimeUs, delayTimeUs);
            realDelayTimeUs = delayTimeUs;
        }

        if(realDelayTimeUs > 0){
            if(realDelayTimeUs < 5000)
                ALOGW("realDelayTimeUs(%lld) is too small", realDelayTimeUs);
            else if( realDelayTimeUs > 50000 )

           {
                ALOGW("realDelayTimeUs(%lld) is too long, config to 30ms", realDelayTimeUs);
                realDelayTimeUs = 30000;
            }
            else
                ALOGD("realDelayTimeUs(%lld)", realDelayTimeUs);

            sp<AMessage> delay = new AMessage(kWhatOutputBufferDrained, mCodec->id());
            int32_t render = 0;
            android_native_rect_t mCrop;
            OMX_CONFIG_RECTTYPE mRect;

            msg->findInt32("render", &render);
            if (msg->findRect("crop",
                    &mCrop.left, &mCrop.top, &mCrop.right, &mCrop.bottom)) {

                ALOGD("send native_window_set_crop again");
                mRect.nLeft = mCrop.left;
                mRect.nTop = mCrop.top;
                mRect.nWidth = mCrop.right;
                mRect.nHeight = mCrop.bottom;

                delay->setRect(
                        "crop",
                        mRect.nLeft,
                        mRect.nTop,
                        mRect.nLeft + mRect.nWidth,
                        mRect.nTop + mRect.nHeight);
            }

            delay->setInt32("render", render);
            delay->setPointer("buffer-id", bufferID);
            delay->post(realDelayTimeUs);
            return;
        }
        else {
            ALOGW("video buffer late, no need delay");
        }
    }

    BufferInfo *infoOMX = NULL;
    BufferInfo *info = NULL;
    ssize_t index;
    //check OMX.MTK.VIDEO.DECODER. prefix for video decoder only
    if( (!strncmp("OMX.MTK.VIDEO.DECODER.", mCodec->mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mCodec->mIsDemandNormalYUV==true))
    {
        ssize_t index_tmp;
        //get bufferInfo from bufferID in convert component for JAVA 
        info = mCodec->findBufferByID(kPortIndexOutput, bufferID, &index_tmp);
        LOGV("findBufferByID index_tmp %x", index_tmp);
        //get bufferInfo from bufferID in OMX component
        infoOMX = mCodec->findBufferByIDForColorConvert(kPortIndexOutputForColorConvert, bufferID, &index);
        LOGV("findBufferByIDForColorConvert index %x", index);
        CHECK_EQ((int)info->mStatus, (int)BufferInfo::OWNED_BY_DOWNSTREAM);
        CHECK_EQ((int)infoOMX->mStatus, (int)BufferInfo::OWNED_BY_DOWNSTREAM);
    }
    else
    {
        info = mCodec->findBufferByID(kPortIndexOutput, bufferID, &index);
        CHECK_EQ((int)info->mStatus, (int)BufferInfo::OWNED_BY_DOWNSTREAM);
    }
#else
    ssize_t index;
    BufferInfo *info =
        mCodec->findBufferByID(kPortIndexOutput, bufferID, &index);
    CHECK_EQ((int)info->mStatus, (int)BufferInfo::OWNED_BY_DOWNSTREAM);
#endif //ANDROID_DEFAULT_CODE
    android_native_rect_t crop;
    if (msg->findRect("crop",
            &crop.left, &crop.top, &crop.right, &crop.bottom)) {
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("native_window_set_crop");
#endif //ANDROID_DEFAULT_CODE
        CHECK_EQ(0, native_window_set_crop(
                mCodec->mNativeWindow.get(), &crop));
    }
    int32_t render;
    if (mCodec->mNativeWindow != NULL
            && msg->findInt32("render", &render) && render != 0
            && (info->mData == NULL || info->mData->size() != 0)) {
        // The client wants this buffer to be rendered.
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("mNativeWindow->queueBuffer");
#endif //ANDROID_DEFAULT_CODE
        status_t err;
        if ((err = mCodec->mNativeWindow->queueBuffer(
                    mCodec->mNativeWindow.get(),
                    info->mGraphicBuffer.get(), -1)) == OK) {
            info->mStatus = BufferInfo::OWNED_BY_NATIVE_WINDOW;
        } else {
            mCodec->signalError(OMX_ErrorUndefined, err);
            info->mStatus = BufferInfo::OWNED_BY_US;
        }
    }else{
#ifndef ANDROID_DEFAULT_CODE
        //check OMX.MTK.VIDEO.DECODER. prefix for video decoder only
        if((!strncmp("OMX.MTK.VIDEO.DECODER.", mCodec->mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mCodec->mIsDemandNormalYUV==true))
        {
            if (infoOMX != NULL)
                infoOMX->mStatus = BufferInfo::OWNED_BY_US;
        }
#endif //ANDROID_DEFAULT_CODE
        info->mStatus = BufferInfo::OWNED_BY_US;
    }

    PortMode mode = getPortMode(kPortIndexOutput);

    switch (mode) {
        case KEEP_BUFFERS:
        {
            // XXX fishy, revisit!!! What about the FREE_BUFFERS case below?

            if (info->mStatus == BufferInfo::OWNED_BY_NATIVE_WINDOW) {
                // We cannot resubmit the buffer we just rendered, dequeue
                // the spare instead.

                info = mCodec->dequeueBufferFromNativeWindow();
            }
            break;
        }

        case RESUBMIT_BUFFERS:
        {
            if (!mCodec->mPortEOS[kPortIndexOutput]) {
                if (info->mStatus == BufferInfo::OWNED_BY_NATIVE_WINDOW) {
                    // We cannot resubmit the buffer we just rendered, dequeue
                    // the spare instead.

                    info = mCodec->dequeueBufferFromNativeWindow();
#ifndef ANDROID_DEFAULT_CODE
                    if( info != NULL )
                        ALOGD("dequeueBufferFromNativeWindow ", info->mBufferID);
#endif //ANDROID_DEFAULT_CODE
                }

#ifndef ANDROID_DEFAULT_CODE
                //check OMX.MTK.VIDEO.DECODER. prefix for video decoder only
                if((!strncmp("OMX.MTK.VIDEO.DECODER.", mCodec->mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mCodec->mIsDemandNormalYUV==true))
                {
                    if (info != NULL) {
                        ALOGV("[%s] calling fillBuffer %p",
                             mCodec->mComponentName.c_str(), info->mBufferID);

                        CHECK_EQ(mCodec->mOMX->fillBuffer(mCodec->mNode, info->mBufferID),
                                 (status_t)OK);

                        info->mStatus = BufferInfo::OWNED_BY_COMPONENT;
                        if (infoOMX != NULL)
                            infoOMX->mStatus = BufferInfo::OWNED_BY_COMPONENT;
                    }
                }
                else
                {
                    if (info != NULL) {
                        ALOGV("[%s] calling fillBuffer %p",
                             mCodec->mComponentName.c_str(), info->mBufferID);
#ifdef MTB_SUPPORT
                        if (!strncmp(mCodec->mComponentName.c_str(), "OMX.MTK.VIDEO.ENCODER", 21)) {
                            ATRACE_ONESHOT(ATRACE_ONESHOT_SPECIAL, "ACodecVEncFTB, bufferID: %p", info->mBufferID);   
                        }
                        else {
                            ATRACE_ONESHOT(ATRACE_ONESHOT_SPECIAL, "ACodecAEncFTB, bufferID: %p", info->mBufferID);   

                        }
#endif
                        CHECK_EQ(mCodec->mOMX->fillBuffer(mCodec->mNode, info->mBufferID),
                                 (status_t)OK);

                        info->mStatus = BufferInfo::OWNED_BY_COMPONENT;
                    }
                }
#else
                if (info != NULL) {
                    ALOGV("[%s] calling fillBuffer %p",
                         mCodec->mComponentName.c_str(), info->mBufferID);

                    CHECK_EQ(mCodec->mOMX->fillBuffer(mCodec->mNode, info->mBufferID),
                             (status_t)OK);

                    info->mStatus = BufferInfo::OWNED_BY_COMPONENT;
                }
#endif //ANDROID_DEFAULT_CODE
            }
            break;
        }

        default:
        {
            CHECK_EQ((int)mode, (int)FREE_BUFFERS);

            CHECK_EQ((status_t)OK,
                     mCodec->freeBuffer(kPortIndexOutput, index));
            break;
        }
    }
}

#ifndef ANDROID_DEFAULT_CODE
int ACodec::BaseState::Mtk_ACodecConvertDecoderOutputToI420(
    void* srcBits, int srcWidth, int srcHeight, ARect srcRect, void* dstBits, OMX_U32 srcFormat) {

    LOGV ("@@ convert srcWidth(%d), srcHeight(%d), srcBits(%p), dstBits(%p)", srcWidth, srcHeight, srcBits, dstBits);
    int mRetDstSize = -1;
#if COLOR_CONVERT_PROFILING
    static int64_t _out_time = 0;
    static int64_t _out_time1 = 0;
    static int64_t _out_timeTotal = 0;
    _out_time = getACodecTickCountMicros();
#endif //COLOR_CONVERT_PROFILING

#ifdef USE_COLOR_CONVERT_MVA
    MTK_PLATFORM_PRIVATE mplatform_privateDstAddr;
    uint8_t* dstYUVbufArray[3];
    unsigned int dstYUVbufSizeArray[3];
    uint8_t* dstYUVbufArray_MVA[3];
    unsigned int dstYUVbufSizeArray_MVA[3];
    bool match_cnt = false;

    for(int i=0; i<mCodec->mMVAParamDst.mOutputBufferPopulatedCnt; i++)
    {
        //LOGD ("@@ mMVAParamDst 0x%x =? 0x%x", mCodec->mMVAParamDst.mM4UBufferPaC[i], (OMX_U32)srcBits);
        if( mCodec->mMVAParamDst.mM4UBufferVaA[i] == (OMX_U32)dstBits )
        {
            mplatform_privateDstAddr.mM4UVABufferVa = mCodec->mMVAParamDst.mM4UBufferVaA[i];
            mplatform_privateDstAddr.mM4UMVABufferPa = mCodec->mMVAParamDst.mM4UBufferPaB[i];
            match_cnt = true;
            LOGD ("@@ mMVAParamDst.mM4UBufferVaA[%d] %p, PaB: %p", i, mCodec->mMVAParamDst.mM4UBufferVaA[i], mCodec->mMVAParamDst.mM4UBufferPaB[i]);
            break;
        }
    }
    if(match_cnt==false)
    {
        LOGW ("@@ mMVAParamDst map null");
        mplatform_privateDstAddr.mM4UVABufferVa = (OMX_U32)dstBits;
        mplatform_privateDstAddr.mM4UMVABufferPa = 0;
    }
#else //USE_COLOR_CONVERT_MVA    
    MTK_PLATFORM_PRIVATE mplatform_privateDstAddr;

    mplatform_privateDstAddr.mM4UVABufferVa = (OMX_U32)dstBits;
    mplatform_privateDstAddr.mM4UMVABufferPa = 0;
#endif //USE_COLOR_CONVERT_MVA
    if(NULL!=mCodec->mACodecColorConverter)
    {
        mCodec->mACodecColorConverter->mMtkColorConvertInfo->mSrcFormat = srcFormat;
        mRetDstSize = mCodec->mACodecColorConverter->secondConvertDecoderOutputToI420(mCodec->mACodecColorConverter, srcBits, srcWidth, srcHeight, srcRect, &mplatform_privateDstAddr);
    }

#if COLOR_CONVERT_PROFILING
        _out_time1 = getACodecTickCountMicros();
        int64_t diff = (_out_time1-_out_time);
        if( diff <= 0 )
        {
            diff = 0;
        }
        else if( diff > mCodec->mProfileColorConvout_timeMax )
            mCodec->mProfileColorConvout_timeMax = diff;
        else if( diff < mCodec->mProfileColorConvout_timeMin )
            mCodec->mProfileColorConvout_timeMin = diff;
        mCodec->mProfileColorConvCnt++;
        if(mCodec->mProfileColorConvCnt!=0)
        {
            _out_timeTotal += diff;
            mCodec->mProfileColorConvout_timeAvg = (_out_timeTotal)/(mCodec->mProfileColorConvCnt);
        }
        if( diff == 0  )
            ALOGD("_out_time1: = %lld _out_time %lld", _out_time1, _out_time);
        ALOGD("convert t: = %lld during %lld ~ %lld, avg %lld micro sec", diff, mCodec->mProfileColorConvout_timeMin, mCodec->mProfileColorConvout_timeMax, 
            mCodec->mProfileColorConvout_timeAvg);
#ifdef DUMP_PERFORMANCE
        if ( diff != 0 ) {
            //char tmpW[2] = "\n";
            //if dump MTK blk yuv, size: YUV_SIZE(ROUND_16(videoDef->nFrameWidth), ROUND_32(videoDef->nFrameHeight))
            if ((!strncmp(mCodec->mComponentName.c_str(), "OMX.MTK.VIDEO.DECODER.", strlen("OMX.MTK.VIDEO.DECODER.")))) {
                FILE* mDumpProflingFile;
                char ucStringyuv[100];
                sprintf(ucStringyuv, "//storage/sdcard1/Profiling%4d.txt",  gettid());
                mDumpProflingFile = fopen(ucStringyuv,"aw");
    
                if (mDumpProflingFile == NULL) {
                    ALOGE("dump Profling file cannot create ");
                }
                else
                {
                    fprintf(mDumpProflingFile, "%lld\n", diff);
                    fclose(mDumpProflingFile);
                }
            }
        }
#endif //DUMP_PERFORMANCE
#endif //COLOR_CONVERT_PROFILING

    return mRetDstSize;
}
#endif //ANDROID_DEFAULT_CODE


////////////////////////////////////////////////////////////////////////////////

ACodec::UninitializedState::UninitializedState(ACodec *codec)
    : BaseState(codec) {
}

void ACodec::UninitializedState::stateEntered() {
    ALOGV("Now uninitialized");

    if (mDeathNotifier != NULL) {
        mCodec->mOMX->asBinder()->unlinkToDeath(mDeathNotifier);
        mDeathNotifier.clear();
    }

    mCodec->mNativeWindow.clear();
    mCodec->mNode = NULL;
    mCodec->mOMX.clear();
    mCodec->mQuirks = 0;
    mCodec->mFlags = 0;
    mCodec->mUseMetadataOnEncoderOutput = 0;
    mCodec->mComponentName.clear();
}

bool ACodec::UninitializedState::onMessageReceived(const sp<AMessage> &msg) {
    bool handled = false;

    switch (msg->what()) {
        case ACodec::kWhatSetup:
        {
            onSetup(msg);

            handled = true;
            break;
        }

        case ACodec::kWhatAllocateComponent:
        {
            onAllocateComponent(msg);
            handled = true;
            break;
        }

        case ACodec::kWhatShutdown:
        {
            int32_t keepComponentAllocated;
            CHECK(msg->findInt32(
                        "keepComponentAllocated", &keepComponentAllocated));
            CHECK(!keepComponentAllocated);

            sp<AMessage> notify = mCodec->mNotify->dup();
            notify->setInt32("what", ACodec::kWhatShutdownCompleted);
            notify->post();

            handled = true;
            break;
        }

        case ACodec::kWhatFlush:
        {
            sp<AMessage> notify = mCodec->mNotify->dup();
            notify->setInt32("what", ACodec::kWhatFlushCompleted);
            notify->post();

            handled = true;
            break;
        }

        default:
            return BaseState::onMessageReceived(msg);
    }

    return handled;
}

void ACodec::UninitializedState::onSetup(
        const sp<AMessage> &msg) {
#ifndef ANDROID_DEFAULT_CODE
    int32_t bAutoRun = 1;
    if (!msg->findInt32("auto-run", &bAutoRun)) {
        bAutoRun = 1;        
    }
    ALOGD("auto run = %d", (int32_t)bAutoRun);
#endif
    if (onAllocateComponent(msg)
            && mCodec->mLoadedState->onConfigureComponent(msg)
#ifndef ANDROID_DEFAULT_CODE
            && (bAutoRun)
#endif
            ) {
        ALOGD("start immediately after config component ");
        mCodec->mLoadedState->onStart();
    }
}

bool ACodec::UninitializedState::onAllocateComponent(const sp<AMessage> &msg) {
    ALOGV("onAllocateComponent");

    CHECK(mCodec->mNode == NULL);

    OMXClient client;
    CHECK_EQ(client.connect(), (status_t)OK);

    sp<IOMX> omx = client.interface();

    sp<AMessage> notify = new AMessage(kWhatOMXDied, mCodec->id());

    mDeathNotifier = new DeathNotifier(notify);
    if (omx->asBinder()->linkToDeath(mDeathNotifier) != OK) {
        // This was a local binder, if it dies so do we, we won't care
        // about any notifications in the afterlife.
        mDeathNotifier.clear();
    }

    Vector<OMXCodec::CodecNameAndQuirks> matchingCodecs;

    AString mime;

    AString componentName;
    uint32_t quirks = 0;
    if (msg->findString("componentName", &componentName)) {
        ssize_t index = matchingCodecs.add();
        OMXCodec::CodecNameAndQuirks *entry = &matchingCodecs.editItemAt(index);
        entry->mName = String8(componentName.c_str());

        if (!OMXCodec::findCodecQuirks(
                    componentName.c_str(), &entry->mQuirks)) {
            entry->mQuirks = 0;
        }
    } else {
        CHECK(msg->findString("mime", &mime));

        int32_t encoder;
        if (!msg->findInt32("encoder", &encoder)) {
            encoder = false;
        }

        OMXCodec::findMatchingCodecs(
                mime.c_str(),
                encoder, // createEncoder
                NULL,  // matchComponentName
                0,     // flags
                &matchingCodecs);
    }

    sp<CodecObserver> observer = new CodecObserver;
    IOMX::node_id node = NULL;

    for (size_t matchIndex = 0; matchIndex < matchingCodecs.size();
            ++matchIndex) {
        componentName = matchingCodecs.itemAt(matchIndex).mName.string();
        quirks = matchingCodecs.itemAt(matchIndex).mQuirks;

        pid_t tid = androidGetTid();
        int prevPriority = androidGetThreadPriority(tid);
        androidSetThreadPriority(tid, ANDROID_PRIORITY_FOREGROUND);
        status_t err = omx->allocateNode(componentName.c_str(), observer, &node);
        androidSetThreadPriority(tid, prevPriority);

        if (err == OK) {
            break;
        }

        node = NULL;
    }

    if (node == NULL) {
        if (!mime.empty()) {
            ALOGE("Unable to instantiate a decoder for type '%s'.",
                 mime.c_str());
        } else {
            ALOGE("Unable to instantiate decoder '%s'.", componentName.c_str());
        }

        mCodec->signalError(OMX_ErrorComponentNotFound);
        return false;
    }

    notify = new AMessage(kWhatOMXMessage, mCodec->id());
    observer->setNotificationMessage(notify);

    mCodec->mComponentName = componentName;
    mCodec->mFlags = 0;

    if (componentName.endsWith(".secure")) {
        mCodec->mFlags |= kFlagIsSecure;
        mCodec->mFlags |= kFlagPushBlankBuffersToNativeWindowOnShutdown;
    }

    mCodec->mQuirks = quirks;
    mCodec->mOMX = omx;
    mCodec->mNode = node;

    {
        sp<AMessage> notify = mCodec->mNotify->dup();
        notify->setInt32("what", ACodec::kWhatComponentAllocated);
        notify->setString("componentName", mCodec->mComponentName.c_str());
#ifndef ANDROID_DEFAULT_CODE
	notify->setInt32("quirks", quirks);
#endif
        notify->post();
    }

    mCodec->changeState(mCodec->mLoadedState);

    return true;
}

////////////////////////////////////////////////////////////////////////////////

ACodec::LoadedState::LoadedState(ACodec *codec)
    : BaseState(codec) {
}

void ACodec::LoadedState::stateEntered() {
    ALOGD("[%s] Now Loaded", mCodec->mComponentName.c_str());

    mCodec->mPortEOS[kPortIndexInput] =
        mCodec->mPortEOS[kPortIndexOutput] = false;

    mCodec->mInputEOSResult = OK;
    mCodec->mDequeueCounter = 0;
    mCodec->mMetaDataBuffersToSubmit = 0;
    mCodec->mRepeatFrameDelayUs = -1ll;
    if (mCodec->mShutdownInProgress) {
        bool keepComponentAllocated = mCodec->mKeepComponentAllocated;

        mCodec->mShutdownInProgress = false;
        mCodec->mKeepComponentAllocated = false;

        onShutdown(keepComponentAllocated);
    }
}

void ACodec::LoadedState::onShutdown(bool keepComponentAllocated) {
    if (!keepComponentAllocated) {
        CHECK_EQ(mCodec->mOMX->freeNode(mCodec->mNode), (status_t)OK);

        mCodec->changeState(mCodec->mUninitializedState);
    }

    sp<AMessage> notify = mCodec->mNotify->dup();
    notify->setInt32("what", ACodec::kWhatShutdownCompleted);
    notify->post();
}

bool ACodec::LoadedState::onMessageReceived(const sp<AMessage> &msg) {
    bool handled = false;

    switch (msg->what()) {
        case ACodec::kWhatConfigureComponent:
        {
            onConfigureComponent(msg);
            handled = true;
            break;
        }

        case ACodec::kWhatCreateInputSurface:
        {
            onCreateInputSurface(msg);
            handled = true;
            break;
        }

        case ACodec::kWhatStart:
        {
            onStart();
            handled = true;
            break;
        }

        case ACodec::kWhatShutdown:
        {
            int32_t keepComponentAllocated;
            CHECK(msg->findInt32(
                        "keepComponentAllocated", &keepComponentAllocated));

            onShutdown(keepComponentAllocated);

            handled = true;
            break;
        }

        case ACodec::kWhatFlush:
        {
            sp<AMessage> notify = mCodec->mNotify->dup();
            notify->setInt32("what", ACodec::kWhatFlushCompleted);
            notify->post();

            handled = true;
            break;
        }

        default:
            return BaseState::onMessageReceived(msg);
    }

    return handled;
}

bool ACodec::LoadedState::onConfigureComponent(
        const sp<AMessage> &msg) {
    ALOGV("onConfigureComponent");

    CHECK(mCodec->mNode != NULL);

    AString mime;
    CHECK(msg->findString("mime", &mime));

    status_t err = mCodec->configureCodec(mime.c_str(), msg);

    if (err != OK) {
        ALOGE("[%s] configureCodec returning error %x",
              mCodec->mComponentName.c_str(), err);

        mCodec->signalError(OMX_ErrorUndefined, err);
        return false;
    }
#ifndef ANDROID_DEFAULT_CODE
    {
        int32_t dummy = 0;

        char value[PROPERTY_VALUE_MAX];
        property_get("acodec.video.isProtect", value, "0");
        dummy = atof(value);
        if (dummy > 0) {
            mCodec->mFlags |= kFlagIsProtect;
            ALOGD ("acodec.video.isProtect %x", dummy);
        }
        //ALOGD ("mCodec->mFlags %x", mCodec->mFlags);

        dummy = 0;
        if( msg->findInt32("IsSecureVideo", &dummy)&& (dummy == 1) )
        {
            mCodec->mFlags |= kFlagIsProtect;
           ALOGD("@debug: mCodec->mFlags |= kFlagIsProtect %x", mCodec->mFlags);
        }
    }
#endif //ANDROID_DEFAULT_CODE
    sp<RefBase> obj;
    if (msg->findObject("native-window", &obj)
            && strncmp("OMX.google.", mCodec->mComponentName.c_str(), 11)) {
        sp<NativeWindowWrapper> nativeWindow(
                static_cast<NativeWindowWrapper *>(obj.get()));
        CHECK(nativeWindow != NULL);
        mCodec->mNativeWindow = nativeWindow->getNativeWindow();
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("@debug: native windows to set %p", mCodec->mNativeWindow.get());
        if (mCodec->mNativeWindow.get() == NULL) {
            ALOGD("onConfigureComponent: fail because native window is null");
            return false;
        }
#endif //ANDROID_DEFAULT_CODE
        native_window_set_scaling_mode(
                mCodec->mNativeWindow.get(),
                NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW);
    }
    CHECK_EQ((status_t)OK, mCodec->initNativeWindow());

    {
        sp<AMessage> notify = mCodec->mNotify->dup();
        notify->setInt32("what", ACodec::kWhatComponentConfigured);
        notify->post();
    }

    return true;
}

void ACodec::LoadedState::onCreateInputSurface(
        const sp<AMessage> &msg) {
    ALOGV("onCreateInputSurface");

    sp<AMessage> notify = mCodec->mNotify->dup();
    notify->setInt32("what", ACodec::kWhatInputSurfaceCreated);

    sp<IGraphicBufferProducer> bufferProducer;
    status_t err;

    err = mCodec->mOMX->createInputSurface(mCodec->mNode, kPortIndexInput,
            &bufferProducer);
    if (err == OK && mCodec->mRepeatFrameDelayUs > 0ll) {
        err = mCodec->mOMX->setInternalOption(
                mCodec->mNode,
                kPortIndexInput,
                IOMX::INTERNAL_OPTION_REPEAT_PREVIOUS_FRAME_DELAY,
                &mCodec->mRepeatFrameDelayUs,
                sizeof(mCodec->mRepeatFrameDelayUs));

        if (err != OK) {
            ALOGE("[%s] Unable to configure option to repeat previous "
                  "frames (err %d)",
                  mCodec->mComponentName.c_str(),
                  err);
        }
    }
    if (err == OK) {
        notify->setObject("input-surface",
                new BufferProducerWrapper(bufferProducer));
    } else {
        // Can't use mCodec->signalError() here -- MediaCodec won't forward
        // the error through because it's in the "configured" state.  We
        // send a kWhatInputSurfaceCreated with an error value instead.
        ALOGE("[%s] onCreateInputSurface returning error %d",
                mCodec->mComponentName.c_str(), err);
        notify->setInt32("err", err);
    }
    notify->post();
}

void ACodec::LoadedState::onStart() {
    ALOGV("onStart");

    CHECK_EQ(mCodec->mOMX->sendCommand(
                mCodec->mNode, OMX_CommandStateSet, OMX_StateIdle),
             (status_t)OK);

    mCodec->changeState(mCodec->mLoadedToIdleState);
}

////////////////////////////////////////////////////////////////////////////////

ACodec::LoadedToIdleState::LoadedToIdleState(ACodec *codec)
    : BaseState(codec) {
}

void ACodec::LoadedToIdleState::stateEntered() {
    ALOGD("[%s] Now Loaded->Idle", mCodec->mComponentName.c_str());

    status_t err;
    if ((err = allocateBuffers()) != OK) {
        ALOGE("Failed to allocate buffers after transitioning to IDLE state "
             "(error 0x%08x)",
             err);

        mCodec->signalError(OMX_ErrorUndefined, err);

        mCodec->changeState(mCodec->mLoadedState);
    }
}

status_t ACodec::LoadedToIdleState::allocateBuffers() {
    status_t err = mCodec->allocateBuffersOnPort(kPortIndexInput);

    if (err != OK) {
        return err;
    }

    return mCodec->allocateBuffersOnPort(kPortIndexOutput);
}

bool ACodec::LoadedToIdleState::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatShutdown:
        {
            mCodec->deferMessage(msg);
            return true;
        }

        case kWhatSignalEndOfInputStream:
        {
            mCodec->onSignalEndOfInputStream();
            return true;
        }

        case kWhatResume:
        {
            // We'll be active soon enough.
            return true;
        }

        case kWhatFlush:
        {
            // We haven't even started yet, so we're flushed alright...
            sp<AMessage> notify = mCodec->mNotify->dup();
            notify->setInt32("what", ACodec::kWhatFlushCompleted);
            notify->post();
            return true;
        }

        default:
            return BaseState::onMessageReceived(msg);
    }
}

bool ACodec::LoadedToIdleState::onOMXEvent(
        OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2) {
    switch (event) {
        case OMX_EventCmdComplete:
        {
            CHECK_EQ(data1, (OMX_U32)OMX_CommandStateSet);
            CHECK_EQ(data2, (OMX_U32)OMX_StateIdle);

            CHECK_EQ(mCodec->mOMX->sendCommand(
                        mCodec->mNode, OMX_CommandStateSet, OMX_StateExecuting),
                     (status_t)OK);

            mCodec->changeState(mCodec->mIdleToExecutingState);

            return true;
        }

        default:
            return BaseState::onOMXEvent(event, data1, data2);
    }
}

////////////////////////////////////////////////////////////////////////////////

ACodec::IdleToExecutingState::IdleToExecutingState(ACodec *codec)
    : BaseState(codec) {
}

void ACodec::IdleToExecutingState::stateEntered() {
    ALOGD("[%s] Now Idle->Executing", mCodec->mComponentName.c_str());
}

bool ACodec::IdleToExecutingState::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatShutdown:
        {
            mCodec->deferMessage(msg);
            return true;
        }

        case kWhatResume:
        {
            // We'll be active soon enough.
            return true;
        }

        case kWhatFlush:
        {
            // We haven't even started yet, so we're flushed alright...
            sp<AMessage> notify = mCodec->mNotify->dup();
            notify->setInt32("what", ACodec::kWhatFlushCompleted);
            notify->post();

            return true;
        }

        case kWhatSignalEndOfInputStream:
        {
            mCodec->onSignalEndOfInputStream();
            return true;
        }

        default:
            return BaseState::onMessageReceived(msg);
    }
}

bool ACodec::IdleToExecutingState::onOMXEvent(
        OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2) {
    switch (event) {
        case OMX_EventCmdComplete:
        {
            CHECK_EQ(data1, (OMX_U32)OMX_CommandStateSet);
            CHECK_EQ(data2, (OMX_U32)OMX_StateExecuting);

            mCodec->mExecutingState->resume();
            mCodec->changeState(mCodec->mExecutingState);

            return true;
        }

        default:
            return BaseState::onOMXEvent(event, data1, data2);
    }
}

////////////////////////////////////////////////////////////////////////////////

ACodec::ExecutingState::ExecutingState(ACodec *codec)
    : BaseState(codec),
      mActive(false) {
}

ACodec::BaseState::PortMode ACodec::ExecutingState::getPortMode(
        OMX_U32 portIndex) {
    return RESUBMIT_BUFFERS;
}
void ACodec::ExecutingState::submitOutputMetaBuffers() {
    // submit as many buffers as there are input buffers with the codec
    // in case we are in port reconfiguring
    for (size_t i = 0; i < mCodec->mBuffers[kPortIndexInput].size(); ++i) {
        BufferInfo *info = &mCodec->mBuffers[kPortIndexInput].editItemAt(i);

        if (info->mStatus == BufferInfo::OWNED_BY_COMPONENT) {
            if (mCodec->submitOutputMetaDataBuffer() != OK)
                break;
        }
    }

    // *** NOTE: THE FOLLOWING WORKAROUND WILL BE REMOVED ***
    mCodec->signalSubmitOutputMetaDataBufferIfEOS_workaround();
}

void ACodec::ExecutingState::submitRegularOutputBuffers() {
    for (size_t i = 0; i < mCodec->mBuffers[kPortIndexOutput].size(); ++i) {
        BufferInfo *info = &mCodec->mBuffers[kPortIndexOutput].editItemAt(i);

        if (mCodec->mNativeWindow != NULL) {
            CHECK(info->mStatus == BufferInfo::OWNED_BY_US
                    || info->mStatus == BufferInfo::OWNED_BY_NATIVE_WINDOW);

            if (info->mStatus == BufferInfo::OWNED_BY_NATIVE_WINDOW) {
                continue;
            }
        } else {
            CHECK_EQ((int)info->mStatus, (int)BufferInfo::OWNED_BY_US);
        }

        ALOGV("submitRegularOutputBuffers [%s] calling fillBuffer %p",
             mCodec->mComponentName.c_str(), info->mBufferID);

        CHECK_EQ(mCodec->mOMX->fillBuffer(mCodec->mNode, info->mBufferID),
                 (status_t)OK);

        info->mStatus = BufferInfo::OWNED_BY_COMPONENT;
    }
}
void ACodec::ExecutingState::submitOutputBuffers() {
    submitRegularOutputBuffers();
    if (mCodec->mStoreMetaDataInOutputBuffers) {
        submitOutputMetaBuffers();
    }
}
void ACodec::ExecutingState::resume() {
    if (mActive) {
        ALOGD("[%s] We're already active, no need to resume.",
             mCodec->mComponentName.c_str());

        return;
    }

    submitOutputBuffers();

    // Post the first input buffer.
    CHECK_GT(mCodec->mBuffers[kPortIndexInput].size(), 0u);
    BufferInfo *info = &mCodec->mBuffers[kPortIndexInput].editItemAt(0);

    postFillThisBuffer(info);

    mActive = true;
}

void ACodec::ExecutingState::stateEntered() {
    ALOGD("[%s] Now Executing", mCodec->mComponentName.c_str());

    mCodec->processDeferredMessages();
}

bool ACodec::ExecutingState::onMessageReceived(const sp<AMessage> &msg) {
    bool handled = false;

    switch (msg->what()) {
        case kWhatShutdown:
        {
            int32_t keepComponentAllocated;
            CHECK(msg->findInt32(
                        "keepComponentAllocated", &keepComponentAllocated));

            mCodec->mShutdownInProgress = true;
            mCodec->mKeepComponentAllocated = keepComponentAllocated;

            mActive = false;

            CHECK_EQ(mCodec->mOMX->sendCommand(
                        mCodec->mNode, OMX_CommandStateSet, OMX_StateIdle),
                     (status_t)OK);

            mCodec->changeState(mCodec->mExecutingToIdleState);

            handled = true;
            break;
        }

        case kWhatFlush:
        {
            ALOGD("[%s] ExecutingState flushing now "
                 "(codec owns %d/%d input, %d/%d output).",
                    mCodec->mComponentName.c_str(),
                    mCodec->countBuffersOwnedByComponent(kPortIndexInput),
                    mCodec->mBuffers[kPortIndexInput].size(),
                    mCodec->countBuffersOwnedByComponent(kPortIndexOutput),
                    mCodec->mBuffers[kPortIndexOutput].size());

            mActive = false;

#ifndef ANDROID_DEFAULT_CODE
            if (mCodec->mLeftOverBuffer != NULL) {
                ALOGI("clear mLeftOverBuffer %x", mCodec->mLeftOverBuffer.get());
                mCodec->mLeftOverBuffer = NULL;
            }
#endif
            CHECK_EQ(mCodec->mOMX->sendCommand(
                        mCodec->mNode, OMX_CommandFlush, OMX_ALL),
                     (status_t)OK);

            mCodec->changeState(mCodec->mFlushingState);
            handled = true;
            break;
        }

        case kWhatResume:
        {
            resume();

            handled = true;
            break;
        }

        case kWhatRequestIDRFrame:
        {
            status_t err = mCodec->requestIDRFrame();
            if (err != OK) {
                ALOGW("Requesting an IDR frame failed.");
            }

            handled = true;
            break;
        }
#ifndef ANDROID_DEFAULT_CODE
        case kWhatMtkVEncIFrameInterval:
        {
            int seconds;
            msg->findInt32("MtkVEncIRate", &seconds);
            status_t err = mCodec->setVEncIInterval(seconds);
            break;
        }
        case kWhatMtkVEncSkipFrame:
        {
            status_t err = mCodec->setVEncSkipFrame();
            break;
        }
        case kWhatMtkVEncDrawBlack:
        {
            int enable;
            msg->findInt32("enable", &enable);
            status_t err = mCodec->setVEncDrawBlack(enable);
            break;
        }
        case kWhatMtkVEncBitRate:
        {
            int bitrate;
            msg->findInt32("MtkVEncBitRate", &bitrate);
            status_t err = mCodec->setVEncBitRate(bitrate);
            break;
        }
        case kWhatMtkVEncFrameRate:
        {
            int framerate;
            msg->findInt32("MtkVEncFrameRate", &framerate);
            status_t err = mCodec->setVEncFrameRate(framerate);
            break;
        }
#endif//ANDROID_DEFAULT_CODE

        case kWhatSetParameters:
        {
            sp<AMessage> params;
            CHECK(msg->findMessage("params", &params));

            status_t err = mCodec->setParameters(params);

            sp<AMessage> reply;
            if (msg->findMessage("reply", &reply)) {
                reply->setInt32("err", err);
                reply->post();
            }

            handled = true;
            break;
        }

        case ACodec::kWhatSignalEndOfInputStream:
        {
            mCodec->onSignalEndOfInputStream();
            handled = true;
            break;
        }

        // *** NOTE: THE FOLLOWING WORKAROUND WILL BE REMOVED ***
        case kWhatSubmitOutputMetaDataBufferIfEOS:
        {
            if (mCodec->mPortEOS[kPortIndexInput] &&
                    !mCodec->mPortEOS[kPortIndexOutput]) {
                status_t err = mCodec->submitOutputMetaDataBuffer();
                if (err == OK) {
                    mCodec->signalSubmitOutputMetaDataBufferIfEOS_workaround();
                }
            }
            return true;
        }

        default:
            handled = BaseState::onMessageReceived(msg);
            break;
    }

    return handled;
}

status_t ACodec::setParameters(const sp<AMessage> &params) {
    int32_t videoBitrate;

#ifndef ANDROID_DEFAULT_CODE
    int32_t mencSkip = 0;
    int32_t mdrawBlack = 0;
#endif //ANDROID_DEFAULT_CODE

    if (params->findInt32("video-bitrate", &videoBitrate)) {
        OMX_VIDEO_CONFIG_BITRATETYPE configParams;
        InitOMXParams(&configParams);
        configParams.nPortIndex = kPortIndexOutput;
        configParams.nEncodeBitrate = videoBitrate;

        status_t err = mOMX->setConfig(
                mNode,
                OMX_IndexConfigVideoBitrate,
                &configParams,
                sizeof(configParams));

        if (err != OK) {
            ALOGE("setConfig(OMX_IndexConfigVideoBitrate, %d) failed w/ err %d",
                   videoBitrate, err);

            return err;
        }
    }
    int32_t dropInputFrames;
    if (params->findInt32("drop-input-frames", &dropInputFrames)) {
        bool suspend = dropInputFrames != 0;

        status_t err =
            mOMX->setInternalOption(
                     mNode,
                     kPortIndexInput,
                     IOMX::INTERNAL_OPTION_SUSPEND,
                     &suspend,
                     sizeof(suspend));

        if (err != OK) {
            ALOGE("Failed to set parameter 'drop-input-frames' (err %d)", err);
            return err;
        }
    }

    int32_t dummy;
    if (params->findInt32("request-sync", &dummy)) {
        status_t err = requestIDRFrame();

        if (err != OK) {
            ALOGE("Requesting a sync frame failed w/ err %d", err);
            return err;
        }
    }
#ifndef ANDROID_DEFAULT_CODE
    if (params->findInt32("encSkip", &mencSkip)) {
        if( 0 == mencSkip )
            return ERROR_UNSUPPORTED;

        if (!mIsEncoder) {
            return ERROR_UNSUPPORTED;
        }

        ALOGI("set Skip frame");
        OMX_INDEXTYPE index;
        status_t err = 
        mOMX->getExtensionIndex(
                mNode,
                "OMX.MTK.index.param.video.EncSetSkipFrame",
                &index);

        if (err != OK) {
            return err;
        }

        OMX_BOOL enable = OMX_TRUE;
        err = mOMX->setConfig(mNode, index, 0, 0);

        if (err != OK) {
            ALOGE("setConfig('OMX.MTK.index.param.video.EncSetSkipFrame') returned error 0x%08x", err);
            return err;
        }
        return OK;
    }
    if (params->findInt32("drawBlack", &mdrawBlack)) {

        if (!mIsEncoder) {
            return ERROR_UNSUPPORTED;
        }
        ALOGI("set Draw Black %d", mdrawBlack);
        status_t err = mOMX->setConfig(mNode, OMX_IndexVendorMtkOmxVencDrawBlack, &mdrawBlack, 0);
        if (err != OK) {
            ALOGE("setConfig('OMX_IndexVendorMtkOmxVencDrawBlack') returned error 0x%08x", err);
            return err;
        }
        return OK;
    }/*
    else if {


    }*/
    //else
    //    ALOGW("setParameter without matched operation");


#endif //ANDROID_DEFAULT_CODE
    return OK;
}

void ACodec::onSignalEndOfInputStream() {
    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", ACodec::kWhatSignaledInputEOS);

    status_t err = mOMX->signalEndOfInputStream(mNode);
    if (err != OK) {
        notify->setInt32("err", err);
    }
    notify->post();
}

bool ACodec::ExecutingState::onOMXEvent(
        OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2) {
    switch (event) {
        case OMX_EventPortSettingsChanged:
        {
            CHECK_EQ(data1, (OMX_U32)kPortIndexOutput);

#ifndef ANDROID_DEFAULT_CODE
            if (data2 == 0 || data2 == OMX_IndexParamPortDefinition || data2 == OMX_IndexVendorMtkOmxVdecGetAspectRatio) {
#else
            if (data2 == 0 || data2 == OMX_IndexParamPortDefinition) {
#endif
                mCodec->mMetaDataBuffersToSubmit = 0;
                CHECK_EQ(mCodec->mOMX->sendCommand(
                            mCodec->mNode,
                            OMX_CommandPortDisable, kPortIndexOutput),
                         (status_t)OK);
#ifndef ANDROID_DEFAULT_CODE
                if (data2 == OMX_IndexVendorMtkOmxVdecGetAspectRatio) {
                    ALOGE ("@@ GOT OMX_IndexVendorMtkOmxVdecGetAspectRatio");
                    OMX_S32 aspectRatio = 0;
                    if (OK == mCodec->mOMX->getConfig(mCodec->mNode, OMX_IndexVendorMtkOmxVdecGetAspectRatio, &aspectRatio, sizeof(aspectRatio))) {
                        ALOGE ("@@ AspectRatioWidth (%d), AspectRatioHeight(%d)", (aspectRatio & 0xFFFF0000) >> 16, (aspectRatio & 0x0000FFFF));
                        mCodec->mVideoAspectRatioWidth = ((aspectRatio & 0xFFFF0000) >> 16);
                        mCodec->mVideoAspectRatioHeight = (aspectRatio & 0x0000FFFF);
                    }
                }
#endif
                mCodec->freeOutputBuffersNotOwnedByComponent();

                mCodec->changeState(mCodec->mOutputPortSettingsChangedState);

#ifndef ANDROID_DEFAULT_CODE
                if (data2 == OMX_IndexVendorMtkOmxVdecGetAspectRatio) {

                    sp<AMessage> reply =
                        new AMessage(kWhatOutputBufferDrained, mCodec->id());
                    mCodec->sendFormatChange(reply);
                }
#endif

            }
#ifndef ANDROID_DEFAULT_CODE
            else if (data2 == OMX_IndexVendorMtkOmxVdecGetCropInfo || data2 == OMX_IndexConfigCommonOutputCrop) {
                mCodec->mSentFormat = false;
            }
#else
            else if (data2 == OMX_IndexConfigCommonOutputCrop) {
                mCodec->mSentFormat = false;
            }
#endif
            else {
                ALOGV("[%s] OMX_EventPortSettingsChanged 0x%08lx",
                     mCodec->mComponentName.c_str(), data2);
            }

            return true;
        }

        case OMX_EventBufferFlag:
        {
            return true;
        }

        default:
            return BaseState::onOMXEvent(event, data1, data2);
    }
}

////////////////////////////////////////////////////////////////////////////////

ACodec::OutputPortSettingsChangedState::OutputPortSettingsChangedState(
        ACodec *codec)
    : BaseState(codec) {
}

ACodec::BaseState::PortMode ACodec::OutputPortSettingsChangedState::getPortMode(
        OMX_U32 portIndex) {
    if (portIndex == kPortIndexOutput) {
        return FREE_BUFFERS;
    }

    CHECK_EQ(portIndex, (OMX_U32)kPortIndexInput);

    return RESUBMIT_BUFFERS;
}

bool ACodec::OutputPortSettingsChangedState::onMessageReceived(
        const sp<AMessage> &msg) {
    bool handled = false;
#ifndef ANDROID_DEFAULT_CODE
    ALOGV("OutputPortSettingsChangedState::onMessageReceived msg->what() %x", msg->what());
#endif //ANDROID_DEFAULT_CODE
    switch (msg->what()) {
        case kWhatFlush:
        case kWhatShutdown:
        case kWhatResume:
        {
            if (msg->what() == kWhatResume) {
                ALOGV("[%s] Deferring resume", mCodec->mComponentName.c_str());
            }

            mCodec->deferMessage(msg);
            handled = true;
            break;
        }

        default:
            handled = BaseState::onMessageReceived(msg);
            break;
    }

    return handled;
}

void ACodec::OutputPortSettingsChangedState::stateEntered() {
    ALOGD("[%s] Now handling output port settings change",
         mCodec->mComponentName.c_str());
}

bool ACodec::OutputPortSettingsChangedState::onOMXEvent(
        OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2) {
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("OutputPortSettingsChangedState::onOMXEvent event %x, %x, %x", event, data1, data2);
#endif //ANDROID_DEFAULT_CODE
    switch (event) {
        case OMX_EventCmdComplete:
        {
            if (data1 == (OMX_U32)OMX_CommandPortDisable) {
                CHECK_EQ(data2, (OMX_U32)kPortIndexOutput);
#ifndef ANDROID_DEFAULT_CODE
                ALOGD("[%s] Output port now disabled.",
                        mCodec->mComponentName.c_str());
#else //ANDROID_DEFAULT_CODE
                ALOGV("[%s] Output port now disabled.",
                        mCodec->mComponentName.c_str());
#endif //ANDROID_DEFAULT_CODE
#ifndef ANDROID_DEFAULT_CODE
                if((!strncmp("OMX.MTK.VIDEO.DECODER.", mCodec->mComponentName.c_str(), strlen("OMX.MTK.VIDEO.DECODER."))) && (mCodec->mIsDemandNormalYUV == true))
                {
                    //CHECK(mCodec->mBuffersForColorConvert[kPortIndexOutputForColorConvert].isEmpty());
                    CHECK(mCodec->mBuffers[kPortIndexOutput].isEmpty());

                    //mCodec->mDealerForColorConvert[kPortIndexOutputForColorConvert].clear();
                    mCodec->mDealer[kPortIndexOutput].clear();
                }
                else
                {
                    CHECK(mCodec->mBuffers[kPortIndexOutput].isEmpty());
                    mCodec->mDealer[kPortIndexOutput].clear();
                }
#else
                CHECK(mCodec->mBuffers[kPortIndexOutput].isEmpty());
                mCodec->mDealer[kPortIndexOutput].clear();
#endif



                CHECK_EQ(mCodec->mOMX->sendCommand(
                            mCodec->mNode, OMX_CommandPortEnable, kPortIndexOutput),
                         (status_t)OK);

                status_t err;
                if ((err = mCodec->allocateBuffersOnPort(
                                kPortIndexOutput)) != OK) {
                    ALOGE("Failed to allocate output port buffers after "
                         "port reconfiguration (error 0x%08x)",
                         err);

                    mCodec->signalError(OMX_ErrorUndefined, err);

                    // This is technically not correct, but appears to be
                    // the only way to free the component instance.
                    // Controlled transitioning from excecuting->idle
                    // and idle->loaded seem impossible probably because
                    // the output port never finishes re-enabling.
                    mCodec->mShutdownInProgress = true;
                    mCodec->mKeepComponentAllocated = false;
                    mCodec->changeState(mCodec->mLoadedState);
                }

                return true;
            } else if (data1 == (OMX_U32)OMX_CommandPortEnable) {
                CHECK_EQ(data2, (OMX_U32)kPortIndexOutput);

                mCodec->mSentFormat = false;

                ALOGV("[%s] Output port now reenabled.",
                        mCodec->mComponentName.c_str());

                if (mCodec->mExecutingState->active()) {
                    mCodec->mExecutingState->submitOutputBuffers();
                }

                mCodec->changeState(mCodec->mExecutingState);

                return true;
            }

            return false;
        }

        default:
            return false;
    }
}

////////////////////////////////////////////////////////////////////////////////

ACodec::ExecutingToIdleState::ExecutingToIdleState(ACodec *codec)
    : BaseState(codec),
      mComponentNowIdle(false) {
}

bool ACodec::ExecutingToIdleState::onMessageReceived(const sp<AMessage> &msg) {
    bool handled = false;

    switch (msg->what()) {
        case kWhatFlush:
        {
            // Don't send me a flush request if you previously wanted me
            // to shutdown.
            TRESPASS();
            break;
        }

        case kWhatShutdown:
        {
            // We're already doing that...

            handled = true;
            break;
        }

        default:
            handled = BaseState::onMessageReceived(msg);
            break;
    }

    return handled;
}

void ACodec::ExecutingToIdleState::stateEntered() {
    ALOGD("[%s] Now Executing->Idle", mCodec->mComponentName.c_str());

    mComponentNowIdle = false;
    mCodec->mSentFormat = false;
}

bool ACodec::ExecutingToIdleState::onOMXEvent(
        OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2) {
    switch (event) {
        case OMX_EventCmdComplete:
        {
            CHECK_EQ(data1, (OMX_U32)OMX_CommandStateSet);
            CHECK_EQ(data2, (OMX_U32)OMX_StateIdle);

            mComponentNowIdle = true;

            changeStateIfWeOwnAllBuffers();

            return true;
        }

        case OMX_EventPortSettingsChanged:
        case OMX_EventBufferFlag:
        {
            // We're shutting down and don't care about this anymore.
            return true;
        }

        default:
            return BaseState::onOMXEvent(event, data1, data2);
    }
}

void ACodec::ExecutingToIdleState::changeStateIfWeOwnAllBuffers() {
    if (mComponentNowIdle && mCodec->allYourBuffersAreBelongToUs()) {
        CHECK_EQ(mCodec->mOMX->sendCommand(
                    mCodec->mNode, OMX_CommandStateSet, OMX_StateLoaded),
                 (status_t)OK);

        CHECK_EQ(mCodec->freeBuffersOnPort(kPortIndexInput), (status_t)OK);
        CHECK_EQ(mCodec->freeBuffersOnPort(kPortIndexOutput), (status_t)OK);
        if ((mCodec->mFlags & kFlagPushBlankBuffersToNativeWindowOnShutdown)
                && mCodec->mNativeWindow != NULL) {
           // We push enough 1x1 blank buffers to ensure that one of
            // them has made it to the display.  This allows the OMX
            // component teardown to zero out any protected buffers
            // without the risk of scanning out one of those buffers.
            mCodec->pushBlankBuffersToNativeWindow();
        }

        mCodec->changeState(mCodec->mIdleToLoadedState);
    }
}

void ACodec::ExecutingToIdleState::onInputBufferFilled(
        const sp<AMessage> &msg) {
    BaseState::onInputBufferFilled(msg);

    changeStateIfWeOwnAllBuffers();
}

void ACodec::ExecutingToIdleState::onOutputBufferDrained(
        const sp<AMessage> &msg) {
    BaseState::onOutputBufferDrained(msg);

    changeStateIfWeOwnAllBuffers();
}

////////////////////////////////////////////////////////////////////////////////

ACodec::IdleToLoadedState::IdleToLoadedState(ACodec *codec)
    : BaseState(codec) {
}

bool ACodec::IdleToLoadedState::onMessageReceived(const sp<AMessage> &msg) {
    bool handled = false;

    switch (msg->what()) {
        case kWhatShutdown:
        {
            // We're already doing that...

            handled = true;
            break;
        }

        case kWhatFlush:
        {
            // Don't send me a flush request if you previously wanted me
            // to shutdown.
            TRESPASS();
            break;
        }

        default:
            handled = BaseState::onMessageReceived(msg);
            break;
    }

    return handled;
}

void ACodec::IdleToLoadedState::stateEntered() {
    ALOGD("[%s] Now Idle->Loaded", mCodec->mComponentName.c_str());
}

bool ACodec::IdleToLoadedState::onOMXEvent(
        OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2) {
    switch (event) {
        case OMX_EventCmdComplete:
        {
            CHECK_EQ(data1, (OMX_U32)OMX_CommandStateSet);
            CHECK_EQ(data2, (OMX_U32)OMX_StateLoaded);

            mCodec->changeState(mCodec->mLoadedState);

            return true;
        }

        default:
            return BaseState::onOMXEvent(event, data1, data2);
    }
}

////////////////////////////////////////////////////////////////////////////////

ACodec::FlushingState::FlushingState(ACodec *codec)
    : BaseState(codec) {
}

void ACodec::FlushingState::stateEntered() {
    ALOGD("[%s] Now Flushing", mCodec->mComponentName.c_str());

    mFlushComplete[kPortIndexInput] = mFlushComplete[kPortIndexOutput] = false;
}

bool ACodec::FlushingState::onMessageReceived(const sp<AMessage> &msg) {
    bool handled = false;

    switch (msg->what()) {
        case kWhatShutdown:
        {
            mCodec->deferMessage(msg);
            break;
        }

        case kWhatFlush:
        {
            // We're already doing this right now.
            handled = true;
            break;
        }

        default:
            handled = BaseState::onMessageReceived(msg);
            break;
    }

    return handled;
}

bool ACodec::FlushingState::onOMXEvent(
        OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2) {
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("[%s] FlushingState onOMXEvent(%d, %ld, %ld)",
            mCodec->mComponentName.c_str(), event, data1, data2);
#else //ANDROID_DEFAULT_CODE
    ALOGV("[%s] FlushingState onOMXEvent(%d,%ld)",
            mCodec->mComponentName.c_str(), event, data1);
#endif //ANDROID_DEFAULT_CODE
    switch (event) {
        case OMX_EventCmdComplete:
        {
            CHECK_EQ(data1, (OMX_U32)OMX_CommandFlush);

            if (data2 == kPortIndexInput || data2 == kPortIndexOutput) {
                CHECK(!mFlushComplete[data2]);
                mFlushComplete[data2] = true;

                if (mFlushComplete[kPortIndexInput]
                        && mFlushComplete[kPortIndexOutput]) {
                    changeStateIfWeOwnAllBuffers();
                }
            } else {
                CHECK_EQ(data2, OMX_ALL);
                CHECK(mFlushComplete[kPortIndexInput]);
                CHECK(mFlushComplete[kPortIndexOutput]);

                changeStateIfWeOwnAllBuffers();
            }

            return true;
        }

        case OMX_EventPortSettingsChanged:
        {
            sp<AMessage> msg = new AMessage(kWhatOMXMessage, mCodec->id());
            msg->setInt32("type", omx_message::EVENT);
            msg->setPointer("node", mCodec->mNode);
            msg->setInt32("event", event);
            msg->setInt32("data1", data1);
            msg->setInt32("data2", data2);

            ALOGV("[%s] Deferring OMX_EventPortSettingsChanged",
                 mCodec->mComponentName.c_str());

            mCodec->deferMessage(msg);

            return true;
        }

        default:
            return BaseState::onOMXEvent(event, data1, data2);
    }

    return true;
}

void ACodec::FlushingState::onOutputBufferDrained(const sp<AMessage> &msg) {
    BaseState::onOutputBufferDrained(msg);

    changeStateIfWeOwnAllBuffers();
}

void ACodec::FlushingState::onInputBufferFilled(const sp<AMessage> &msg) {
    BaseState::onInputBufferFilled(msg);

    changeStateIfWeOwnAllBuffers();
}

void ACodec::FlushingState::changeStateIfWeOwnAllBuffers() {
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("FlushingState::changeStateIfWeOwnAllBuffers mFlushComplete in %d, out %d", mFlushComplete[kPortIndexInput], mFlushComplete[kPortIndexOutput]);
#endif //ANDROID_DEFAULT_CODE
    if (mFlushComplete[kPortIndexInput]
            && mFlushComplete[kPortIndexOutput]
            && mCodec->allYourBuffersAreBelongToUs()) {
        // We now own all buffers except possibly those still queued with
        // the native window for rendering. Let's get those back as well.
        mCodec->waitUntilAllPossibleNativeWindowBuffersAreReturnedToUs();

        sp<AMessage> notify = mCodec->mNotify->dup();
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("send kWhatFlushCompleted");
#endif //ANDROID_DEFAULT_CODE
        notify->setInt32("what", ACodec::kWhatFlushCompleted);
        notify->post();

        mCodec->mPortEOS[kPortIndexInput] =
            mCodec->mPortEOS[kPortIndexOutput] = false;

        mCodec->mInputEOSResult = OK;

        if (mCodec->mSkipCutBuffer != NULL) {
            mCodec->mSkipCutBuffer->clear();
        }

        mCodec->changeState(mCodec->mExecutingState);
    }
}

}  // namespace android
