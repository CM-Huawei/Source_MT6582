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

#ifndef A_CODEC_H_

#define A_CODEC_H_

#include <stdint.h>
#include <android/native_window.h>
#include <media/IOMX.h>
#include <media/stagefright/foundation/AHierarchicalStateMachine.h>
#include <media/stagefright/SkipCutBuffer.h>
#include <OMX_Audio.h>

#define TRACK_BUFFER_TIMING     0

#ifndef ANDROID_DEFAULT_CODE
#include "I420ColorConverter.h"
#define COLOR_CONVERT_PROFILING 1
#define VIDEO_M4U_MAX_BUFFER 100
#endif //ANDROID_DEFAULT_CODE

#ifndef ANDROID_DEFAULT_CODE
#include <media/stagefright/OMXCodec.h>
#endif //ANDROID_DEFAULT_CODE

namespace android {

struct ABuffer;
struct MemoryDealer;

struct ACodec : public AHierarchicalStateMachine {
    enum {
        kWhatFillThisBuffer      = 'fill',
        kWhatDrainThisBuffer     = 'drai',
        kWhatEOS                 = 'eos ',
        kWhatShutdownCompleted   = 'scom',
        kWhatFlushCompleted      = 'fcom',
        kWhatOutputFormatChanged = 'outC',
        kWhatError               = 'erro',
        kWhatComponentAllocated  = 'cAll',
        kWhatComponentConfigured = 'cCon',
        kWhatInputSurfaceCreated = 'isfc',
        kWhatSignaledInputEOS    = 'seos',
        kWhatBuffersAllocated    = 'allc',
        kWhatOMXDied             = 'OMXd',
    };

    ACodec();

    void setNotificationMessage(const sp<AMessage> &msg);
    void initiateSetup(const sp<AMessage> &msg);
    void signalFlush();
    void signalResume();
    void initiateShutdown(bool keepComponentAllocated = false);

    void signalSetParameters(const sp<AMessage> &msg);
    void signalEndOfInputStream();

    void initiateAllocateComponent(const sp<AMessage> &msg);
    void initiateConfigureComponent(const sp<AMessage> &msg);
    void initiateCreateInputSurface();
    void initiateStart();

    void signalRequestIDRFrame();
#ifndef ANDROID_DEFAULT_CODE
    void signalVEncIInterval(int seconds);
    status_t setVEncIInterval(int seconds);     //should be private
    void signalVEncSkipFrame();
    status_t setVEncSkipFrame();
    void signalVEncDrawBlack(int enable); //for Miracast test case SIGMA 5.1.11 workaround
    status_t setVEncDrawBlack(int enable);//for Miracast test case SIGMA 5.1.11 workaround
    void signalVEncBitRate(int bitrate);
    status_t setVEncBitRate(int bitrate);       //should be private
    void signalVEncFrameRate(int framerate);
    status_t setVEncFrameRate(int framerate);   //should be private
#endif//ANDROID_DEFAULT_CODE
    struct PortDescription : public RefBase {
        size_t countBuffers();
        IOMX::buffer_id bufferIDAt(size_t index) const;
        sp<ABuffer> bufferAt(size_t index) const;

    private:
        friend struct ACodec;

        Vector<IOMX::buffer_id> mBufferIDs;
        Vector<sp<ABuffer> > mBuffers;

        PortDescription();
        void addBuffer(IOMX::buffer_id id, const sp<ABuffer> &buffer);

        DISALLOW_EVIL_CONSTRUCTORS(PortDescription);
    };

protected:
    virtual ~ACodec();

private:
    struct BaseState;
    struct UninitializedState;
    struct LoadedState;
    struct LoadedToIdleState;
    struct IdleToExecutingState;
    struct ExecutingState;
    struct OutputPortSettingsChangedState;
    struct ExecutingToIdleState;
    struct IdleToLoadedState;
    struct FlushingState;
    struct DeathNotifier;

    enum {
        kWhatSetup                   = 'setu',
        kWhatOMXMessage              = 'omx ',
        kWhatInputBufferFilled       = 'inpF',
        kWhatOutputBufferDrained     = 'outD',
        kWhatShutdown                = 'shut',
        kWhatFlush                   = 'flus',
        kWhatResume                  = 'resm',
        kWhatDrainDeferredMessages   = 'drai',
        kWhatAllocateComponent       = 'allo',
        kWhatConfigureComponent      = 'conf',
        kWhatCreateInputSurface      = 'cisf',
        kWhatSignalEndOfInputStream  = 'eois',
        kWhatStart                   = 'star',
        kWhatRequestIDRFrame         = 'ridr',
        kWhatSetParameters           = 'setP',
        kWhatSubmitOutputMetaDataBufferIfEOS = 'subm',
#ifndef ANDROID_DEFAULT_CODE
        kWhatMtkVEncIFrameInterval   = 'MVeI',
        kWhatMtkVEncSkipFrame        = 'MVeS',
        kWhatMtkVEncDrawBlack        = 'MVeD',
        kWhatMtkVEncBitRate          = 'MVeB',
        kWhatMtkVEncFrameRate        = 'MVeF',
#endif//ANDROID_DEFAULT_CODE
    };

    enum {
        kPortIndexInput  = 0,
        kPortIndexOutput = 1
    };
#ifndef ANDROID_DEFAULT_CODE
    enum {
        kPortIndexOutputForColorConvert  = 0,
    };
#endif//ANDROID_DEFAULT_CODE

    enum {
        kFlagIsSecure                                 = 1,
        kFlagPushBlankBuffersToNativeWindowOnShutdown = 2,
#ifndef  ANDROID_DEFAULT_CODE
        kFlagIsProtect                                = 4,
#endif // ANDROID_DEFAULT_CODE
    };

    struct BufferInfo {
        enum Status {
            OWNED_BY_US,
            OWNED_BY_COMPONENT,
            OWNED_BY_UPSTREAM,
            OWNED_BY_DOWNSTREAM,
            OWNED_BY_NATIVE_WINDOW,
#ifndef ANDROID_DEFAULT_CODE
            OWNED_BY_UNEXPECTED
#endif //ANDROID_DEFAULT_CODE
        };

        IOMX::buffer_id mBufferID;
        Status mStatus;
        unsigned mDequeuedAt;

        sp<ABuffer> mData;
        sp<GraphicBuffer> mGraphicBuffer;
    };

#if TRACK_BUFFER_TIMING
    struct BufferStats {
        int64_t mEmptyBufferTimeUs;
        int64_t mFillBufferDoneTimeUs;
    };

    KeyedVector<int64_t, BufferStats> mBufferStats;
#endif

    sp<AMessage> mNotify;

    sp<UninitializedState> mUninitializedState;
    sp<LoadedState> mLoadedState;
    sp<LoadedToIdleState> mLoadedToIdleState;
    sp<IdleToExecutingState> mIdleToExecutingState;
    sp<ExecutingState> mExecutingState;
    sp<OutputPortSettingsChangedState> mOutputPortSettingsChangedState;
    sp<ExecutingToIdleState> mExecutingToIdleState;
    sp<IdleToLoadedState> mIdleToLoadedState;
    sp<FlushingState> mFlushingState;
    sp<SkipCutBuffer> mSkipCutBuffer;

    AString mComponentName;
    uint32_t mFlags;
    uint32_t mQuirks;
    sp<IOMX> mOMX;
    IOMX::node_id mNode;
    sp<MemoryDealer> mDealer[2];

#ifndef ANDROID_DEFAULT_CODE
    sp<MemoryDealer> mDealerForColorConvert[1];

    struct CodecBufferIndex {
        IOMX::buffer_id mBufferIdxForOriginal;
#ifdef USE_VIDEO_ION
        uint32_t mBufferMemory;
#else
        sp<IMemory> mBufferMemory;
#endif //USE_VIDEO_ION
        uint32_t mIndex;
    };
    Vector<CodecBufferIndex> matchingBufferIndex;
#endif //ANDROID_DEFAULT_CODE

    sp<ANativeWindow> mNativeWindow;

    Vector<BufferInfo> mBuffers[2];
#ifndef ANDROID_DEFAULT_CODE

    typedef struct MTK_VDEC_ACODEC_MVA_ADDR_MAPPING {
        OMX_U32 mM4UBufferVaA[VIDEO_M4U_MAX_BUFFER];
        OMX_U32 mM4UBufferPaB[VIDEO_M4U_MAX_BUFFER];
        OMX_U32 mM4UBufferCount;
        OMX_U32 mInputBufferPopulatedCnt;
        OMX_U32 mOutputBufferPopulatedCnt;
    } MTK_VDEC_ACODEC_MVA_ADDR_MAPPING;
    Vector<BufferInfo> mBuffersForColorConvert[1];
    MTK_VDEC_ACODEC_MVA_ADDR_MAPPING mMVAParamDst;
#if COLOR_CONVERT_PROFILING
    int64_t mProfileColorConvout_timeMin;
    int64_t mProfileColorConvout_timeMax;
    int64_t mProfileColorConvout_timeAvg;
    int64_t mProfileColorConvCnt;
#endif //COLOR_CONVERT_PROFILING    
#endif //ANDROID_DEFAULT_CODE

    bool mPortEOS[2];
    status_t mInputEOSResult;

    List<sp<AMessage> > mDeferredQueue;

    bool mSentFormat;
#ifndef ANDROID_DEFAULT_CODE
    bool mSupportsPartialFrames;
    bool mIsVideoDecoder;
    bool mIsVideoEncoder;
    sp<ABuffer> mLeftOverBuffer;
    int32_t mMaxQueueBufferNum;
    FILE* mDumpFile;
    int32_t mVideoAspectRatioWidth;
    int32_t mVideoAspectRatioHeight;
    FILE* mDumpRawFile;
    bool mIsDemandNormalYUV;
    uint32_t mAcodecCConvertMode;
    uint32_t mAcodecEncCConvertMode;
    size_t mAlignedSize;
    //ACodecColorConverter* mACodecColorConverter;
    I420ColorConverter*     mACodecColorConverter;
    void*  mM4UBufferHandle;
#ifdef USE_VIDEO_M4U
    unsigned long mM4UBufferCount;
    unsigned long mM4UBufferSize[VIDEO_M4U_MAX_BUFFER];
    unsigned long mM4UBufferVa[VIDEO_M4U_MAX_BUFFER]; 
    unsigned long mM4UBufferPa[VIDEO_M4U_MAX_BUFFER];
    unsigned long mM4UBufferHdr[VIDEO_M4U_MAX_BUFFER];
#endif //USE_VIDEO_M4U

#ifdef USE_VIDEO_ION
    int mIonDevFd;
    VideoIonBufferInfo mIonInputBufInfo[MAX_ION_BUF_COUNT];
    VideoIonBufferInfo mIonOutputBufInfo[MAX_ION_BUF_COUNT];
    //for color convert
    int mCCIonDevFd;
    VideoIonBufferInfo mCCIonBufInfo[MAX_ION_BUF_COUNT];
#endif //USE_VIDEO_ION
#endif //ANDROID_DEFAULT_CODE
    bool mIsEncoder;
    bool mUseMetadataOnEncoderOutput;
    bool mShutdownInProgress;

    // If "mKeepComponentAllocated" we only transition back to Loaded state
    // and do not release the component instance.
    bool mKeepComponentAllocated;

    int32_t mEncoderDelay;
    int32_t mEncoderPadding;

    bool mChannelMaskPresent;
    int32_t mChannelMask;
    unsigned mDequeueCounter;
    bool mStoreMetaDataInOutputBuffers;
    int32_t mMetaDataBuffersToSubmit;

    int64_t mRepeatFrameDelayUs;

    status_t setCyclicIntraMacroblockRefresh(const sp<AMessage> &msg, int32_t mode);
    status_t allocateBuffersOnPort(OMX_U32 portIndex);
    status_t freeBuffersOnPort(OMX_U32 portIndex);
    status_t freeBuffer(OMX_U32 portIndex, size_t i);

    status_t configureOutputBuffersFromNativeWindow(
            OMX_U32 *nBufferCount, OMX_U32 *nBufferSize,
            OMX_U32 *nMinUndequeuedBuffers);
    status_t allocateOutputMetaDataBuffers();
    status_t submitOutputMetaDataBuffer();
    void signalSubmitOutputMetaDataBufferIfEOS_workaround();
    status_t allocateOutputBuffersFromNativeWindow();
    status_t cancelBufferToNativeWindow(BufferInfo *info);
    status_t freeOutputBuffersNotOwnedByComponent();
    BufferInfo *dequeueBufferFromNativeWindow();

    BufferInfo *findBufferByID(
            uint32_t portIndex, IOMX::buffer_id bufferID,
            ssize_t *index = NULL);
#ifndef ANDROID_DEFAULT_CODE
    BufferInfo *findBufferByIDForColorConvert(
            uint32_t portIndex, IOMX::buffer_id bufferID,
            ssize_t *index = NULL);
#endif //ANDROID_DEFAULT_CODE

    status_t setComponentRole(bool isEncoder, const char *mime);
    status_t configureCodec(const char *mime, const sp<AMessage> &msg);

    status_t setVideoPortFormatType(
            OMX_U32 portIndex,
            OMX_VIDEO_CODINGTYPE compressionFormat,
            OMX_COLOR_FORMATTYPE colorFormat);

    status_t setSupportedOutputFormat();

    status_t setupVideoDecoder(
            const char *mime, int32_t width, int32_t height);

    status_t setupVideoEncoder(
            const char *mime, const sp<AMessage> &msg);

    status_t setVideoFormatOnPort(
            OMX_U32 portIndex,
            int32_t width, int32_t height,
            OMX_VIDEO_CODINGTYPE compressionFormat);

    status_t setupAACCodec(
            bool encoder,
            int32_t numChannels, int32_t sampleRate, int32_t bitRate,
            int32_t aacProfile, bool isADTS);

    status_t selectAudioPortFormat(
            OMX_U32 portIndex, OMX_AUDIO_CODINGTYPE desiredFormat);

    status_t setupAMRCodec(bool encoder, bool isWAMR, int32_t bitRate);
    status_t setupG711Codec(bool encoder, int32_t numChannels);

    status_t setupFlacCodec(
            bool encoder, int32_t numChannels, int32_t sampleRate, int32_t compressionLevel);

    status_t setupRawAudioFormat(
            OMX_U32 portIndex, int32_t sampleRate, int32_t numChannels);

    status_t setMinBufferSize(OMX_U32 portIndex, size_t size);

    status_t setupMPEG4EncoderParameters(const sp<AMessage> &msg);
    status_t setupH263EncoderParameters(const sp<AMessage> &msg);
    status_t setupAVCEncoderParameters(const sp<AMessage> &msg);
    status_t setupVPXEncoderParameters(const sp<AMessage> &msg);

    status_t verifySupportForProfileAndLevel(int32_t profile, int32_t level);

    status_t configureBitrate(
            int32_t bitrate, OMX_VIDEO_CONTROLRATETYPE bitrateMode);

    status_t setupErrorCorrectionParameters();

    status_t initNativeWindow();

    status_t pushBlankBuffersToNativeWindow();

    // Returns true iff all buffers on the given port have status
    // OWNED_BY_US or OWNED_BY_NATIVE_WINDOW.
    bool allYourBuffersAreBelongToUs(OMX_U32 portIndex);

    bool allYourBuffersAreBelongToUs();

    void waitUntilAllPossibleNativeWindowBuffersAreReturnedToUs();

    size_t countBuffersOwnedByComponent(OMX_U32 portIndex) const;
    size_t countBuffersOwnedByNativeWindow() const;

    void deferMessage(const sp<AMessage> &msg);
    void processDeferredMessages();

    void sendFormatChange(const sp<AMessage> &reply);

    void signalError(
            OMX_ERRORTYPE error = OMX_ErrorUndefined,
            status_t internalError = UNKNOWN_ERROR);

    status_t requestIDRFrame();
    status_t setParameters(const sp<AMessage> &params);

    // Send EOS on input stream.
    void onSignalEndOfInputStream();

    DISALLOW_EVIL_CONSTRUCTORS(ACodec);
};

}  // namespace android

#endif  // A_CODEC_H_
