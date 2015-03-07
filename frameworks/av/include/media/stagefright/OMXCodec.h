/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef OMX_CODEC_H_

#define OMX_CODEC_H_

#include <android/native_window.h>
#include <media/IOMX.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaSource.h>
#include <utils/threads.h>

#include <OMX_Audio.h>
#include <OMX_VideoExt.h>


#ifndef ANDROID_DEFAULT_CODE
#include <binder/MemoryHeapBase.h>
#define USE_VIDEO_M4U

// TODO: Turn on this to enable ION input/output buffer allocation
//#define USE_VIDEO_ION
#ifdef USE_VIDEO_ION
#define MAX_ION_BUF_COUNT 50
typedef struct
{
    uint32_t u4BufVA;
    uint32_t u4BufSize;
    int IonFd;
   uint32_t u4IonBufHandle;
} VideoIonBufferInfo;
#endif


// adb shell property flags
#define OMXCODEC_ENABLE_VIDEO_INPUT_ERROR_PATTERNS  (1 << 0)
#define OMXCODEC_THUMBNAIL_MODE                     (1 << 1)

#endif

namespace android {

struct MediaCodecList;
class MemoryDealer;
struct OMXCodecObserver;
struct CodecProfileLevel;
class SkipCutBuffer;

struct OMXCodec : public MediaSource,
                  public MediaBufferObserver {
    enum CreationFlags {
        kPreferSoftwareCodecs    = 1,
        kIgnoreCodecSpecificData = 2,

        // The client wants to access the output buffer's video
        // data for example for thumbnail extraction.
        kClientNeedsFramebuffer  = 4,

        // Request for software or hardware codecs. If request
        // can not be fullfilled, Create() returns NULL.
        kSoftwareCodecsOnly      = 8,
        kHardwareCodecsOnly      = 16,

        // Store meta data in video buffers
        kStoreMetaDataInVideoBuffers = 32,

        // Only submit one input buffer at one time.
        kOnlySubmitOneInputBufferAtOneTime = 64,

        // Enable GRALLOC_USAGE_PROTECTED for output buffers from native window
        kEnableGrallocUsageProtected = 128,

        // Secure decoding mode
        kUseSecureInputBuffers = 256,
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_ENABLE_VIDEO_EDITOR
		kAudUseOMXForVE = 512,
		kMtkAudDecForVE = 1024,
#endif //MTK_ENABLE_VIDEO_EDITOR
        kUseMaxOutputBuffers = 2048,
#ifdef MTK_CLEARMOTION_SUPPORT
        kUseClearMotion = 4096,
#endif //MTK_CLEARMOTION_SUPPORT
#ifdef MTK_ENABLE_VIDEO_EDITOR
        kVideoUseOMXMVAForVE = 8192,
#endif //MTK_ENABLE_VIDEO_EDITOR
#endif //ANDROID_DEFAULT_CODE
    };
    static sp<MediaSource> Create(
            const sp<IOMX> &omx,
            const sp<MetaData> &meta, bool createEncoder,
            const sp<MediaSource> &source,
            const char *matchComponentName = NULL,
            uint32_t flags = 0,
            const sp<ANativeWindow> &nativeWindow = NULL);

    static void setComponentRole(
            const sp<IOMX> &omx, IOMX::node_id node, bool isEncoder,
            const char *mime);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();

    virtual sp<MetaData> getFormat();

#ifndef ANDROID_DEFAULT_CODE  // Morris Yang for Camera recording
    virtual sp<MetaData> getCameraMeta();
    status_t vEncSetForceIframe(bool enable);
    status_t vDecSwitchBwTVout(bool enable);

    status_t vEncSetFrameRate(unsigned int u4FrameRate);
#ifndef ANDROID_DEFAULT_CODE
    status_t vEncSetBitRate(unsigned int u4BitRate);
    sp<MediaSource> getSource() { return mSource; }
#endif //ANDROID_DEFAULT_CODE

    enum MtkEncScenario {
        kCameraRecord = 1,
        kLivePhotoCapture = 2,
        kLivePhotoEffect = 4,
    };
    status_t vEncSetSenario(MtkEncScenario eScenario);
#endif

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

    virtual status_t pause();

    // from MediaBufferObserver
    virtual void signalBufferReturned(MediaBuffer *buffer);

    enum Quirks {
        kNeedsFlushBeforeDisable              = 1,
        kWantsNALFragments                    = 2,
        kRequiresLoadedToIdleAfterAllocation  = 4,
        kRequiresAllocateBufferOnInputPorts   = 8,
        kRequiresFlushCompleteEmulation       = 16,
        kRequiresAllocateBufferOnOutputPorts  = 32,
        kRequiresFlushBeforeShutdown          = 64,
        kDefersOutputBufferAllocation         = 128,
        kDecoderLiesAboutNumberOfChannels     = 256,
        kInputBufferSizesAreBogus             = 512,
        kSupportsMultipleFramesPerInputBuffer = 1024,
        kRequiresLargerEncoderOutputBuffer    = 2048,
        kOutputBuffersAreUnreadable           = 4096,
#ifndef ANDROID_DEFAULT_CODE
        kAvoidMemcopyInputRecordingFrames     = 8192,
        kDecoderNeedPrebuffer                 = 16384,        
#endif //ANDROID_DEFAULT_CODE
    };

    struct CodecNameAndQuirks {
        String8 mName;
        uint32_t mQuirks;
    };

    // for use by ACodec
    static void findMatchingCodecs(
            const char *mime,
            bool createEncoder, const char *matchComponentName,
            uint32_t flags,
            Vector<CodecNameAndQuirks> *matchingCodecNamesAndQuirks);

    static uint32_t getComponentQuirks(
            const MediaCodecList *list, size_t index);

    static bool findCodecQuirks(const char *componentName, uint32_t *quirks);

#ifndef ANDROID_DEFAULT_CODE
    	size_t buffersOwn();
	void resume();

    //for videoeditor MVA mode
    void *findInputBufferByDataNumber(OMX_U32 portIndex, uint32_t number);

    // Added for video editor to query the number of empty input buffers.
    uint32_t getEmptyInputBufferCount();
#endif //ANDROID_DEFAULT_CODE

protected:
    virtual ~OMXCodec();

private:

    // Make sure mLock is accessible to OMXCodecObserver
    friend class OMXCodecObserver;

    // Call this with mLock hold
    void on_message(const omx_message &msg);

    enum State {
        DEAD,
        LOADED,
        LOADED_TO_IDLE,
        IDLE_TO_EXECUTING,
        EXECUTING,
        EXECUTING_TO_IDLE,
        IDLE_TO_LOADED,
        RECONFIGURING,
        ERROR
    };

    enum {
        kPortIndexInput  = 0,
        kPortIndexOutput = 1
    };

    enum PortStatus {
        ENABLED,
        DISABLING,
        DISABLED,
        ENABLING,
        SHUTTING_DOWN,
    };

    enum BufferStatus {
        OWNED_BY_US,
        OWNED_BY_COMPONENT,
        OWNED_BY_NATIVE_WINDOW,
        OWNED_BY_CLIENT,
    };

    struct BufferInfo {
        IOMX::buffer_id mBuffer;
        BufferStatus mStatus;
        sp<IMemory> mMem;
        size_t mSize;
        void *mData;
        MediaBuffer *mMediaBuffer;
    };

    struct CodecSpecificData {
        size_t mSize;
        uint8_t mData[1];
    };

    sp<IOMX> mOMX;
    bool mOMXLivesLocally;
    IOMX::node_id mNode;
    uint32_t mQuirks;

    // Flags specified in the creation of the codec.
    uint32_t mFlags;

    bool mIsEncoder;
    bool mIsVideo;
    char *mMIME;
    char *mComponentName;
    sp<MetaData> mOutputFormat;
#ifndef ANDROID_DEFAULT_CODE  // Morris Yang for Camera recording
    sp<MetaData> mCameraMeta;
#endif

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_CMMB_ENABLE
    //IsCMMB
    bool IsCMMBFlag;
#endif
    bool mIsHttpStreaming;
#endif //ANDROID_DEFAULT_CODE
    sp<MediaSource> mSource;
    Vector<CodecSpecificData *> mCodecSpecificData;
    size_t mCodecSpecificDataIndex;

    sp<MemoryDealer> mDealer[2];

    State mState;
    Vector<BufferInfo> mPortBuffers[2];
    PortStatus mPortStatus[2];
    bool mInitialBufferSubmit;
    bool mSignalledEOS;
    status_t mFinalStatus;
    bool mNoMoreOutputData;
    bool mOutputPortSettingsHaveChanged;
    int64_t mSeekTimeUs;
    ReadOptions::SeekMode mSeekMode;
    int64_t mTargetTimeUs;
    bool mOutputPortSettingsChangedPending;
    sp<SkipCutBuffer> mSkipCutBuffer;

    MediaBuffer *mLeftOverBuffer;

    Mutex mLock;
    Condition mAsyncCompletion;

    bool mPaused;

#ifndef ANDROID_DEFAULT_CODE
    bool mIsVideoDecoder;
    bool mIsVideoEncoder;
    unsigned char* mInputBufferPoolMemBase;
    unsigned char* mOutputBufferPoolMemBase;
#ifdef USE_VIDEO_M4U
    sp<MemoryHeapBase> mMemHeapBase;
#endif

#ifdef USE_VIDEO_ION
    int mIonDevFd;
    VideoIonBufferInfo mIonInputBufInfo[MAX_ION_BUF_COUNT];
    VideoIonBufferInfo mIonOutputBufInfo[MAX_ION_BUF_COUNT];

#endif

    uint32_t mPropFlags;
    float mVideoInputErrorRate;
    Condition mBufferSent;
    // set this by calling start with kKeyMaxQueueBuffer in meta
    size_t mMaxQueueBufferNum;
    bool mQueueWaiting;
    bool mSupportsPartialFrames;
    MediaBufferSimpleObserver mOMXPartialBufferOwner;
    int32_t mVideoAspectRatioWidth;
    int32_t mVideoAspectRatioHeight;
    bool mIsVENCTimelapseMode;
    int64_t mRTSPOutputTimeoutUS;
    int64_t mHTTPOutputTimeoutUS;
#endif //ANDROID_DEFAULT_CODE

    sp<ANativeWindow> mNativeWindow;

    // The index in each of the mPortBuffers arrays of the buffer that will be
    // submitted to OMX next.  This only applies when using buffers from a
    // native window.
    size_t mNextNativeBufferIndex[2];

    // A list of indices into mPortStatus[kPortIndexOutput] filled with data.
    List<size_t> mFilledBuffers;
    Condition mBufferFilled;

    // Used to record the decoding time for an output picture from
    // a video encoder.
    List<int64_t> mDecodingTimeList;

    OMXCodec(const sp<IOMX> &omx, IOMX::node_id node,
             uint32_t quirks, uint32_t flags,
             bool isEncoder, const char *mime, const char *componentName,
             const sp<MediaSource> &source,
             const sp<ANativeWindow> &nativeWindow);

    void addCodecSpecificData(const void *data, size_t size);
    void clearCodecSpecificData();

    void setComponentRole();

#ifndef ANDROID_DEFAULT_CODE
	int32_t getAACProfile();
    void setVORBISFormat(int32_t numChannels, int32_t sampleRate, int32_t bitRate);
    status_t setAMRFormat(bool isWAMR, int32_t bitRate);
#else
    void setAMRFormat(bool isWAMR, int32_t bitRate);
#endif //ANDROID_DEFAULT_CODE

    status_t setAACFormat(
            int32_t numChannels, int32_t sampleRate, int32_t bitRate,
            int32_t aacProfile, bool isADTS);

    void setG711Format(int32_t numChannels);

    status_t setVideoPortFormatType(
            OMX_U32 portIndex,
            OMX_VIDEO_CODINGTYPE compressionFormat,
            OMX_COLOR_FORMATTYPE colorFormat);

    void setVideoInputFormat(
            const char *mime, const sp<MetaData>& meta);

    status_t setupBitRate(int32_t bitRate);
    status_t setupErrorCorrectionParameters();
    status_t setupH263EncoderParameters(const sp<MetaData>& meta);
    status_t setupMPEG4EncoderParameters(const sp<MetaData>& meta);
    status_t setupAVCEncoderParameters(const sp<MetaData>& meta);
#ifndef ANDROID_DEFAULT_CODE
    status_t setupVP8EncoderParameters(const sp<MetaData>& meta);
#endif //ANDROID_DEFAULT_CODE
    status_t findTargetColorFormat(
            const sp<MetaData>& meta, OMX_COLOR_FORMATTYPE *colorFormat);

    status_t isColorFormatSupported(
            OMX_COLOR_FORMATTYPE colorFormat, int portIndex);

    // If profile/level is set in the meta data, its value in the meta
    // data will be used; otherwise, the default value will be used.
    status_t getVideoProfileLevel(const sp<MetaData>& meta,
            const CodecProfileLevel& defaultProfileLevel,
            CodecProfileLevel& profileLevel);

    status_t setVideoOutputFormat(
            const char *mime, const sp<MetaData>& meta);

    void setImageOutputFormat(
            OMX_COLOR_FORMATTYPE format, OMX_U32 width, OMX_U32 height);

    void setJPEGInputFormat(
            OMX_U32 width, OMX_U32 height, OMX_U32 compressedSize);

    void setMinBufferSize(OMX_U32 portIndex, OMX_U32 size);

    void setRawAudioFormat(
            OMX_U32 portIndex, int32_t sampleRate, int32_t numChannels);

    status_t allocateBuffers();
    status_t allocateBuffersOnPort(OMX_U32 portIndex);

#ifndef ANDROID_DEFAULT_CODE
    status_t allocateBuffersOnInputPort();
    status_t allocateBuffersOnOutputPort();
#endif

    status_t allocateOutputBuffersFromNativeWindow();

    status_t queueBufferToNativeWindow(BufferInfo *info);
    status_t cancelBufferToNativeWindow(BufferInfo *info);
    BufferInfo* dequeueBufferFromNativeWindow();
    status_t pushBlankBuffersToNativeWindow();

    status_t freeBuffersOnPort(
            OMX_U32 portIndex, bool onlyThoseWeOwn = false);

    status_t freeBuffer(OMX_U32 portIndex, size_t bufIndex);

#ifndef ANDROID_DEFAULT_CODE
    void PutErrorPatterns(uint8_t *pBuffer, uint32_t length);
#endif //ANDROID_DEFAULT_CODE

    bool drainInputBuffer(IOMX::buffer_id buffer);
    void fillOutputBuffer(IOMX::buffer_id buffer);
#ifndef ANDROID_DEFAULT_CODE
    bool drainInputBuffer(BufferInfo *info, bool init = false);
#else
    bool drainInputBuffer(BufferInfo *info);
#endif // #ifndef ANDROID_DEFAULT_CODE
    void fillOutputBuffer(BufferInfo *info);

    void drainInputBuffers();
    void fillOutputBuffers();

#ifndef ANDROID_DEFAULT_CODE
    bool drainAnyInputBuffer(bool init = false);
#else
    bool drainAnyInputBuffer();
#endif //ANDROID_DEFAULT_CODE
    BufferInfo *findInputBufferByDataPointer(void *ptr);
    BufferInfo *findEmptyInputBuffer();

    // Returns true iff a flush was initiated and a completion event is
    // upcoming, false otherwise (A flush was not necessary as we own all
    // the buffers on that port).
    // This method will ONLY ever return false for a component with quirk
    // "kRequiresFlushCompleteEmulation".
    bool flushPortAsync(OMX_U32 portIndex);

    void disablePortAsync(OMX_U32 portIndex);
    status_t enablePortAsync(OMX_U32 portIndex);

    static size_t countBuffersWeOwn(const Vector<BufferInfo> &buffers);
    static bool isIntermediateState(State state);

    void onEvent(OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2);
    void onCmdComplete(OMX_COMMANDTYPE cmd, OMX_U32 data);
    void onStateChange(OMX_STATETYPE newState);
    void onPortSettingsChanged(OMX_U32 portIndex);

    void setState(State newState);

    status_t init();
    void initOutputFormat(const sp<MetaData> &inputFormat);
    status_t initNativeWindow();

    void initNativeWindowCrop();

    void dumpPortStatus(OMX_U32 portIndex);

    status_t configureCodec(const sp<MetaData> &meta);
#ifndef ANDROID_DEFAULT_CODE
    void restorePatchedDataPointer(BufferInfo *info);
#ifdef MTK_VIDEO_HEVC_SUPPORT
    status_t parseHVCCodecSpecificData(
            const void *data, size_t size,
            unsigned *profile, unsigned *level);
#endif
#endif //ANDROID_DEFAULT_CODE
    status_t applyRotation();
    status_t waitForBufferFilled_l();

    int64_t getDecodingTimeUs();

    status_t parseAVCCodecSpecificData(
            const void *data, size_t size,
            unsigned *profile, unsigned *level);

    status_t stopOmxComponent_l();

    OMXCodec(const OMXCodec &);
    OMXCodec &operator=(const OMXCodec &);
#ifndef ANDROID_DEFAULT_CODE
private:
	//int32_t mPreRollTimes;
	int64_t mPreRollStartTime;
#endif //ANDROID_DEFAULT_CODE
};

struct CodecCapabilities {
    enum {
        kFlagSupportsAdaptivePlayback = 1 << 0,
    };

    String8 mComponentName;
    Vector<CodecProfileLevel> mProfileLevels;
    Vector<OMX_U32> mColorFormats;
    uint32_t mFlags;
};

// Return a vector of componentNames with supported profile/level pairs
// supporting the given mime type, if queryDecoders==true, returns components
// that decode content of the given type, otherwise returns components
// that encode content of the given type.
// profile and level indications only make sense for h.263, mpeg4 and avc
// video.
// If hwCodecOnly==true, only returns hardware-based components, software and
// hardware otherwise.
// The profile/level values correspond to
// OMX_VIDEO_H263PROFILETYPE, OMX_VIDEO_MPEG4PROFILETYPE,
// OMX_VIDEO_AVCPROFILETYPE, OMX_VIDEO_H263LEVELTYPE, OMX_VIDEO_MPEG4LEVELTYPE
// and OMX_VIDEO_AVCLEVELTYPE respectively.

status_t QueryCodecs(
        const sp<IOMX> &omx,
        const char *mimeType, bool queryDecoders, bool hwCodecOnly,
        Vector<CodecCapabilities> *results);

status_t QueryCodecs(
        const sp<IOMX> &omx,
        const char *mimeType, bool queryDecoders,
        Vector<CodecCapabilities> *results);

status_t QueryCodec(
        const sp<IOMX> &omx,
        const char *componentName, const char *mime,
        bool isEncoder,
        CodecCapabilities *caps);

status_t getOMXChannelMapping(size_t numChannels, OMX_AUDIO_CHANNELTYPE map[]);

#ifndef ANDROID_DEFAULT_CODE    // Morris Yang for Camera recording
#define VDOBUFCOUNT_MAX 16
typedef struct
{
    uint32_t u4VdoBufCount;
    uint32_t u4VdoBufSize;
    uint32_t u4VdoBufVA[VDOBUFCOUNT_MAX];
} CamMemInfo_t;

typedef struct
{
    uint32_t u4VdoBufCount;
    uint32_t u4VdoBufSize;
    int          IonFd[VDOBUFCOUNT_MAX];
    uint32_t u4VdoBufVA[VDOBUFCOUNT_MAX];
} CamMemIonInfo_t;

typedef struct
{
    uint32_t u4Security;
    uint32_t u4Coherent;
} CamMCIMemInfo_t;

#endif //ANDROID_DEFAULT_CODE

}  // namespace android

#endif  // OMX_CODEC_H_
