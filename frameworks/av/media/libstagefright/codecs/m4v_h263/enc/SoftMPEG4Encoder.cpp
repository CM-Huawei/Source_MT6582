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
#define LOG_TAG "SoftMPEG4Encoder"
#include <utils/Log.h>

#include "mp4enc_api.h"
#include "OMX_Video.h"

#include <HardwareAPI.h>
#include <MetadataBufferType.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include <ui/Rect.h>
#include <ui/GraphicBufferMapper.h>

#include "SoftMPEG4Encoder.h"

namespace android {

template<class T>
static void InitOMXParams(T *params) {
    params->nSize = sizeof(T);
    params->nVersion.s.nVersionMajor = 1;
    params->nVersion.s.nVersionMinor = 0;
    params->nVersion.s.nRevision = 0;
    params->nVersion.s.nStep = 0;
}

inline static void ConvertYUV420SemiPlanarToYUV420Planar(
        uint8_t *inyuv, uint8_t* outyuv,
        int32_t width, int32_t height) {

    int32_t outYsize = width * height;
    uint32_t *outy =  (uint32_t *) outyuv;
    uint16_t *outcb = (uint16_t *) (outyuv + outYsize);
    uint16_t *outcr = (uint16_t *) (outyuv + outYsize + (outYsize >> 2));

    /* Y copying */
    memcpy(outy, inyuv, outYsize);

    /* U & V copying */
    uint32_t *inyuv_4 = (uint32_t *) (inyuv + outYsize);
    for (int32_t i = height >> 1; i > 0; --i) {
        for (int32_t j = width >> 2; j > 0; --j) {
            uint32_t temp = *inyuv_4++;
            uint32_t tempU = temp & 0xFF;
            tempU = tempU | ((temp >> 8) & 0xFF00);

            uint32_t tempV = (temp >> 8) & 0xFF;
            tempV = tempV | ((temp >> 16) & 0xFF00);

            // Flip U and V
            *outcb++ = tempV;
            *outcr++ = tempU;
        }
    }
}

SoftMPEG4Encoder::SoftMPEG4Encoder(
            const char *name,
            const OMX_CALLBACKTYPE *callbacks,
            OMX_PTR appData,
            OMX_COMPONENTTYPE **component)
    : SimpleSoftOMXComponent(name, callbacks, appData, component),
      mEncodeMode(COMBINE_MODE_WITH_ERR_RES),
      mVideoWidth(176),
      mVideoHeight(144),
      mVideoFrameRate(30),
      mVideoBitRate(192000),
      mVideoColorFormat(OMX_COLOR_FormatYUV420Planar),
      mStoreMetaDataInBuffers(false),
      mIDRFrameRefreshIntervalInSec(1),
      mNumInputFrames(-1),
      mStarted(false),
      mSawInputEOS(false),
      mSignalledError(false),
      mHandle(new tagvideoEncControls),
      mEncParams(new tagvideoEncOptions),
      mInputFrameData(NULL) {

   if (!strcmp(name, "OMX.google.h263.encoder")) {
        mEncodeMode = H263_MODE;
    } else {
        CHECK(!strcmp(name, "OMX.google.mpeg4.encoder"));
    }

    initPorts();
    ALOGI("Construct SoftMPEG4Encoder");
}

SoftMPEG4Encoder::~SoftMPEG4Encoder() {
    ALOGV("Destruct SoftMPEG4Encoder");
    releaseEncoder();
    List<BufferInfo *> &outQueue = getPortQueue(1);
    List<BufferInfo *> &inQueue = getPortQueue(0);
    CHECK(outQueue.empty());
    CHECK(inQueue.empty());
}

OMX_ERRORTYPE SoftMPEG4Encoder::initEncParams() {
    CHECK(mHandle != NULL);
    memset(mHandle, 0, sizeof(tagvideoEncControls));

    CHECK(mEncParams != NULL);
    memset(mEncParams, 0, sizeof(tagvideoEncOptions));
    if (!PVGetDefaultEncOption(mEncParams, 0)) {
        ALOGE("Failed to get default encoding parameters");
        return OMX_ErrorUndefined;
    }
    mEncParams->encMode = mEncodeMode;
    mEncParams->encWidth[0] = mVideoWidth;
    mEncParams->encHeight[0] = mVideoHeight;
    mEncParams->encFrameRate[0] = mVideoFrameRate;
    mEncParams->rcType = VBR_1;
    mEncParams->vbvDelay = 5.0f;

    // FIXME:
    // Add more profile and level support for MPEG4 encoder
    mEncParams->profile_level = CORE_PROFILE_LEVEL2;
    mEncParams->packetSize = 32;
    mEncParams->rvlcEnable = PV_OFF;
    mEncParams->numLayers = 1;
    mEncParams->timeIncRes = 1000;
    mEncParams->tickPerSrc = mEncParams->timeIncRes / mVideoFrameRate;

    mEncParams->bitRate[0] = mVideoBitRate;
    mEncParams->iQuant[0] = 15;
    mEncParams->pQuant[0] = 12;
    mEncParams->quantType[0] = 0;
    mEncParams->noFrameSkipped = PV_OFF;

    if (mVideoColorFormat == OMX_COLOR_FormatYUV420SemiPlanar) {
        // Color conversion is needed.
        CHECK(mInputFrameData == NULL);
        mInputFrameData =
            (uint8_t *) malloc((mVideoWidth * mVideoHeight * 3 ) >> 1);
        CHECK(mInputFrameData != NULL);
    }

    // PV's MPEG4 encoder requires the video dimension of multiple
    if (mVideoWidth % 16 != 0 || mVideoHeight % 16 != 0) {
        ALOGE("Video frame size %dx%d must be a multiple of 16",
            mVideoWidth, mVideoHeight);
        return OMX_ErrorBadParameter;
    }

    // Set IDR frame refresh interval
    if (mIDRFrameRefreshIntervalInSec < 0) {
        mEncParams->intraPeriod = -1;
    } else if (mIDRFrameRefreshIntervalInSec == 0) {
        mEncParams->intraPeriod = 1;  // All I frames
    } else {
        mEncParams->intraPeriod =
            (mIDRFrameRefreshIntervalInSec * mVideoFrameRate);
    }

    mEncParams->numIntraMB = 0;
    mEncParams->sceneDetect = PV_ON;
    mEncParams->searchRange = 16;
    mEncParams->mv8x8Enable = PV_OFF;
    mEncParams->gobHeaderInterval = 0;
    mEncParams->useACPred = PV_ON;
    mEncParams->intraDCVlcTh = 0;

    return OMX_ErrorNone;
}

OMX_ERRORTYPE SoftMPEG4Encoder::initEncoder() {
    CHECK(!mStarted);

    OMX_ERRORTYPE errType = OMX_ErrorNone;
    if (OMX_ErrorNone != (errType = initEncParams())) {
        ALOGE("Failed to initialized encoder params");
        mSignalledError = true;
        notify(OMX_EventError, OMX_ErrorUndefined, 0, 0);
        return errType;
    }

    if (!PVInitVideoEncoder(mHandle, mEncParams)) {
        ALOGE("Failed to initialize the encoder");
        mSignalledError = true;
        notify(OMX_EventError, OMX_ErrorUndefined, 0, 0);
        return OMX_ErrorUndefined;
    }

    mNumInputFrames = -1;  // 1st buffer for codec specific data
    mStarted = true;

    return OMX_ErrorNone;
}

OMX_ERRORTYPE SoftMPEG4Encoder::releaseEncoder() {
    if (!mStarted) {
        return OMX_ErrorNone;
    }

    PVCleanUpVideoEncoder(mHandle);

    delete mInputFrameData;
    mInputFrameData = NULL;

    delete mEncParams;
    mEncParams = NULL;

    delete mHandle;
    mHandle = NULL;

    mStarted = false;

    return OMX_ErrorNone;
}

void SoftMPEG4Encoder::initPorts() {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);

    const size_t kInputBufferSize = (mVideoWidth * mVideoHeight * 3) >> 1;

    // 256 * 1024 is a magic number for PV's encoder, not sure why
    const size_t kOutputBufferSize =
        (kInputBufferSize > 256 * 1024)
            ? kInputBufferSize: 256 * 1024;

    def.nPortIndex = 0;
    def.eDir = OMX_DirInput;
    def.nBufferCountMin = kNumBuffers;
    def.nBufferCountActual = def.nBufferCountMin;
    def.nBufferSize = kInputBufferSize;
    def.bEnabled = OMX_TRUE;
    def.bPopulated = OMX_FALSE;
    def.eDomain = OMX_PortDomainVideo;
    def.bBuffersContiguous = OMX_FALSE;
    def.nBufferAlignment = 1;

    def.format.video.cMIMEType = const_cast<char *>("video/raw");

    def.format.video.eCompressionFormat = OMX_VIDEO_CodingUnused;
    def.format.video.eColorFormat = OMX_COLOR_FormatYUV420Planar;
    def.format.video.xFramerate = (mVideoFrameRate << 16);  // Q16 format
    def.format.video.nBitrate = mVideoBitRate;
    def.format.video.nFrameWidth = mVideoWidth;
    def.format.video.nFrameHeight = mVideoHeight;
    def.format.video.nStride = mVideoWidth;
    def.format.video.nSliceHeight = mVideoHeight;

    addPort(def);

    def.nPortIndex = 1;
    def.eDir = OMX_DirOutput;
    def.nBufferCountMin = kNumBuffers;
    def.nBufferCountActual = def.nBufferCountMin;
    def.nBufferSize = kOutputBufferSize;
    def.bEnabled = OMX_TRUE;
    def.bPopulated = OMX_FALSE;
    def.eDomain = OMX_PortDomainVideo;
    def.bBuffersContiguous = OMX_FALSE;
    def.nBufferAlignment = 2;

    def.format.video.cMIMEType =
        (mEncodeMode == COMBINE_MODE_WITH_ERR_RES)
            ? const_cast<char *>(MEDIA_MIMETYPE_VIDEO_MPEG4)
            : const_cast<char *>(MEDIA_MIMETYPE_VIDEO_H263);

    def.format.video.eCompressionFormat =
        (mEncodeMode == COMBINE_MODE_WITH_ERR_RES)
            ? OMX_VIDEO_CodingMPEG4
            : OMX_VIDEO_CodingH263;

    def.format.video.eColorFormat = OMX_COLOR_FormatUnused;
    def.format.video.xFramerate = (0 << 16);  // Q16 format
    def.format.video.nBitrate = mVideoBitRate;
    def.format.video.nFrameWidth = mVideoWidth;
    def.format.video.nFrameHeight = mVideoHeight;
    def.format.video.nStride = mVideoWidth;
    def.format.video.nSliceHeight = mVideoHeight;

    addPort(def);
}

OMX_ERRORTYPE SoftMPEG4Encoder::internalGetParameter(
        OMX_INDEXTYPE index, OMX_PTR params) {
    switch (index) {
        case OMX_IndexParamVideoErrorCorrection:
        {
            return OMX_ErrorNotImplemented;
        }

        case OMX_IndexParamVideoBitrate:
        {
            OMX_VIDEO_PARAM_BITRATETYPE *bitRate =
                (OMX_VIDEO_PARAM_BITRATETYPE *) params;

            if (bitRate->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            bitRate->eControlRate = OMX_Video_ControlRateVariable;
            bitRate->nTargetBitrate = mVideoBitRate;
            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoPortFormat:
        {
            OMX_VIDEO_PARAM_PORTFORMATTYPE *formatParams =
                (OMX_VIDEO_PARAM_PORTFORMATTYPE *)params;

            if (formatParams->nPortIndex > 1) {
                return OMX_ErrorUndefined;
            }

            if (formatParams->nIndex > 2) {
                return OMX_ErrorNoMore;
            }

            if (formatParams->nPortIndex == 0) {
                formatParams->eCompressionFormat = OMX_VIDEO_CodingUnused;
                if (formatParams->nIndex == 0) {
                    formatParams->eColorFormat = OMX_COLOR_FormatYUV420Planar;
                } else if (formatParams->nIndex == 1) {
                    formatParams->eColorFormat = OMX_COLOR_FormatYUV420SemiPlanar;
                } else {
                    formatParams->eColorFormat = OMX_COLOR_FormatAndroidOpaque;
                }
            } else {
                formatParams->eCompressionFormat =
                    (mEncodeMode == COMBINE_MODE_WITH_ERR_RES)
                        ? OMX_VIDEO_CodingMPEG4
                        : OMX_VIDEO_CodingH263;

                formatParams->eColorFormat = OMX_COLOR_FormatUnused;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoH263:
        {
            OMX_VIDEO_PARAM_H263TYPE *h263type =
                (OMX_VIDEO_PARAM_H263TYPE *)params;

            if (h263type->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            h263type->nAllowedPictureTypes =
                (OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP);
            h263type->eProfile = OMX_VIDEO_H263ProfileBaseline;
            h263type->eLevel = OMX_VIDEO_H263Level45;
            h263type->bPLUSPTYPEAllowed = OMX_FALSE;
            h263type->bForceRoundingTypeToZero = OMX_FALSE;
            h263type->nPictureHeaderRepetition = 0;
            h263type->nGOBHeaderInterval = 0;

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoMpeg4:
        {
            OMX_VIDEO_PARAM_MPEG4TYPE *mpeg4type =
                (OMX_VIDEO_PARAM_MPEG4TYPE *)params;

            if (mpeg4type->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            mpeg4type->eProfile = OMX_VIDEO_MPEG4ProfileCore;
            mpeg4type->eLevel = OMX_VIDEO_MPEG4Level2;
            mpeg4type->nAllowedPictureTypes =
                (OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP);
            mpeg4type->nBFrames = 0;
            mpeg4type->nIDCVLCThreshold = 0;
            mpeg4type->bACPred = OMX_TRUE;
            mpeg4type->nMaxPacketSize = 256;
            mpeg4type->nTimeIncRes = 1000;
            mpeg4type->nHeaderExtension = 0;
            mpeg4type->bReversibleVLC = OMX_FALSE;

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoProfileLevelQuerySupported:
        {
            OMX_VIDEO_PARAM_PROFILELEVELTYPE *profileLevel =
                (OMX_VIDEO_PARAM_PROFILELEVELTYPE *)params;

            if (profileLevel->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            if (profileLevel->nProfileIndex > 0) {
                return OMX_ErrorNoMore;
            }

            if (mEncodeMode == H263_MODE) {
                profileLevel->eProfile = OMX_VIDEO_H263ProfileBaseline;
                profileLevel->eLevel = OMX_VIDEO_H263Level45;
            } else {
                profileLevel->eProfile = OMX_VIDEO_MPEG4ProfileCore;
                profileLevel->eLevel = OMX_VIDEO_MPEG4Level2;
            }

            return OMX_ErrorNone;
        }

        default:
            return SimpleSoftOMXComponent::internalGetParameter(index, params);
    }
}

OMX_ERRORTYPE SoftMPEG4Encoder::internalSetParameter(
        OMX_INDEXTYPE index, const OMX_PTR params) {
    int32_t indexFull = index;

    switch (indexFull) {
        case OMX_IndexParamVideoErrorCorrection:
        {
            return OMX_ErrorNotImplemented;
        }

        case OMX_IndexParamVideoBitrate:
        {
            OMX_VIDEO_PARAM_BITRATETYPE *bitRate =
                (OMX_VIDEO_PARAM_BITRATETYPE *) params;

            if (bitRate->nPortIndex != 1 ||
                bitRate->eControlRate != OMX_Video_ControlRateVariable) {
                return OMX_ErrorUndefined;
            }

            mVideoBitRate = bitRate->nTargetBitrate;
            return OMX_ErrorNone;
        }

        case OMX_IndexParamPortDefinition:
        {
            OMX_PARAM_PORTDEFINITIONTYPE *def =
                (OMX_PARAM_PORTDEFINITIONTYPE *)params;
            if (def->nPortIndex > 1) {
                return OMX_ErrorUndefined;
            }

            if (def->nPortIndex == 0) {
                if (def->format.video.eCompressionFormat != OMX_VIDEO_CodingUnused ||
                    (def->format.video.eColorFormat != OMX_COLOR_FormatYUV420Planar &&
                     def->format.video.eColorFormat != OMX_COLOR_FormatYUV420SemiPlanar &&
                     def->format.video.eColorFormat != OMX_COLOR_FormatAndroidOpaque)) {
                    return OMX_ErrorUndefined;
                }
            } else {
                if ((mEncodeMode == COMBINE_MODE_WITH_ERR_RES &&
                        def->format.video.eCompressionFormat != OMX_VIDEO_CodingMPEG4) ||
                    (mEncodeMode == H263_MODE &&
                        def->format.video.eCompressionFormat != OMX_VIDEO_CodingH263) ||
                    (def->format.video.eColorFormat != OMX_COLOR_FormatUnused)) {
                    return OMX_ErrorUndefined;
                }
            }

            OMX_ERRORTYPE err = SimpleSoftOMXComponent::internalSetParameter(index, params);
            if (OMX_ErrorNone != err) {
                return err;
            }

            if (def->nPortIndex == 0) {
                mVideoWidth = def->format.video.nFrameWidth;
                mVideoHeight = def->format.video.nFrameHeight;
                mVideoFrameRate = def->format.video.xFramerate >> 16;
                mVideoColorFormat = def->format.video.eColorFormat;
            } else {
                mVideoBitRate = def->format.video.nBitrate;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamStandardComponentRole:
        {
            const OMX_PARAM_COMPONENTROLETYPE *roleParams =
                (const OMX_PARAM_COMPONENTROLETYPE *)params;

            if (strncmp((const char *)roleParams->cRole,
                        (mEncodeMode == H263_MODE)
                            ? "video_encoder.h263": "video_encoder.mpeg4",
                        OMX_MAX_STRINGNAME_SIZE - 1)) {
                return OMX_ErrorUndefined;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoPortFormat:
        {
            const OMX_VIDEO_PARAM_PORTFORMATTYPE *formatParams =
                (const OMX_VIDEO_PARAM_PORTFORMATTYPE *)params;

            if (formatParams->nPortIndex > 1) {
                return OMX_ErrorUndefined;
            }

            if (formatParams->nIndex > 2) {
                return OMX_ErrorNoMore;
            }

            if (formatParams->nPortIndex == 0) {
                if (formatParams->eCompressionFormat != OMX_VIDEO_CodingUnused ||
                    ((formatParams->nIndex == 0 &&
                      formatParams->eColorFormat != OMX_COLOR_FormatYUV420Planar) ||
                    (formatParams->nIndex == 1 &&
                     formatParams->eColorFormat != OMX_COLOR_FormatYUV420SemiPlanar) ||
                    (formatParams->nIndex == 2 &&
                     formatParams->eColorFormat != OMX_COLOR_FormatAndroidOpaque) )) {
                    return OMX_ErrorUndefined;
                }
                mVideoColorFormat = formatParams->eColorFormat;
            } else {
                if ((mEncodeMode == H263_MODE &&
                        formatParams->eCompressionFormat != OMX_VIDEO_CodingH263) ||
                    (mEncodeMode == COMBINE_MODE_WITH_ERR_RES &&
                        formatParams->eCompressionFormat != OMX_VIDEO_CodingMPEG4) ||
                    formatParams->eColorFormat != OMX_COLOR_FormatUnused) {
                    return OMX_ErrorUndefined;
                }
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoH263:
        {
            OMX_VIDEO_PARAM_H263TYPE *h263type =
                (OMX_VIDEO_PARAM_H263TYPE *)params;

            if (h263type->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            if (h263type->eProfile != OMX_VIDEO_H263ProfileBaseline ||
                h263type->eLevel != OMX_VIDEO_H263Level45 ||
                (h263type->nAllowedPictureTypes & OMX_VIDEO_PictureTypeB) ||
                h263type->bPLUSPTYPEAllowed != OMX_FALSE ||
                h263type->bForceRoundingTypeToZero != OMX_FALSE ||
                h263type->nPictureHeaderRepetition != 0 ||
                h263type->nGOBHeaderInterval != 0) {
                return OMX_ErrorUndefined;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoMpeg4:
        {
            OMX_VIDEO_PARAM_MPEG4TYPE *mpeg4type =
                (OMX_VIDEO_PARAM_MPEG4TYPE *)params;

            if (mpeg4type->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            if (mpeg4type->eProfile != OMX_VIDEO_MPEG4ProfileCore ||
                mpeg4type->eLevel != OMX_VIDEO_MPEG4Level2 ||
                (mpeg4type->nAllowedPictureTypes & OMX_VIDEO_PictureTypeB) ||
                mpeg4type->nBFrames != 0 ||
                mpeg4type->nIDCVLCThreshold != 0 ||
                mpeg4type->bACPred != OMX_TRUE ||
                mpeg4type->nMaxPacketSize != 256 ||
                mpeg4type->nTimeIncRes != 1000 ||
                mpeg4type->nHeaderExtension != 0 ||
                mpeg4type->bReversibleVLC != OMX_FALSE) {
                return OMX_ErrorUndefined;
            }

            return OMX_ErrorNone;
        }

        case kStoreMetaDataExtensionIndex:
        {
            StoreMetaDataInBuffersParams *storeParams =
                    (StoreMetaDataInBuffersParams*)params;
            if (storeParams->nPortIndex != 0) {
                ALOGE("%s: StoreMetadataInBuffersParams.nPortIndex not zero!",
                        __FUNCTION__);
                return OMX_ErrorUndefined;
            }

            mStoreMetaDataInBuffers = storeParams->bStoreMetaData;
            ALOGV("StoreMetaDataInBuffers set to: %s",
                    mStoreMetaDataInBuffers ? " true" : "false");

            if (mStoreMetaDataInBuffers) {
                mVideoColorFormat == OMX_COLOR_FormatYUV420SemiPlanar;
                if (mInputFrameData == NULL) {
                    mInputFrameData =
                            (uint8_t *) malloc((mVideoWidth * mVideoHeight * 3 ) >> 1);
                }
            }

            return OMX_ErrorNone;
        }

        default:
            return SimpleSoftOMXComponent::internalSetParameter(index, params);
    }
}

void SoftMPEG4Encoder::onQueueFilled(OMX_U32 portIndex) {
    if (mSignalledError || mSawInputEOS) {
        return;
    }

    if (!mStarted) {
        if (OMX_ErrorNone != initEncoder()) {
            return;
        }
    }

    List<BufferInfo *> &inQueue = getPortQueue(0);
    List<BufferInfo *> &outQueue = getPortQueue(1);

    while (!mSawInputEOS && !inQueue.empty() && !outQueue.empty()) {
        BufferInfo *inInfo = *inQueue.begin();
        OMX_BUFFERHEADERTYPE *inHeader = inInfo->mHeader;
        BufferInfo *outInfo = *outQueue.begin();
        OMX_BUFFERHEADERTYPE *outHeader = outInfo->mHeader;

        outHeader->nTimeStamp = 0;
        outHeader->nFlags = 0;
        outHeader->nOffset = 0;
        outHeader->nFilledLen = 0;
        outHeader->nOffset = 0;

        uint8_t *outPtr = (uint8_t *) outHeader->pBuffer;
        int32_t dataLength = outHeader->nAllocLen;

        if (mNumInputFrames < 0) {
            if (!PVGetVolHeader(mHandle, outPtr, &dataLength, 0)) {
                ALOGE("Failed to get VOL header");
                mSignalledError = true;
                notify(OMX_EventError, OMX_ErrorUndefined, 0, 0);
                return;
            }
            ALOGV("Output VOL header: %d bytes", dataLength);
            ++mNumInputFrames;
            outHeader->nFlags |= OMX_BUFFERFLAG_CODECCONFIG;
            outHeader->nFilledLen = dataLength;
            outQueue.erase(outQueue.begin());
            outInfo->mOwnedByUs = false;
            notifyFillBufferDone(outHeader);
            return;
        }

        // Save the input buffer info so that it can be
        // passed to an output buffer
        InputBufferInfo info;
        info.mTimeUs = inHeader->nTimeStamp;
        info.mFlags = inHeader->nFlags;
        mInputBufferInfoVec.push(info);

        if (inHeader->nFlags & OMX_BUFFERFLAG_EOS) {
            mSawInputEOS = true;
        }

        buffer_handle_t srcBuffer; // for MetaDataMode only
        if (inHeader->nFilledLen > 0) {
            uint8_t *inputData = NULL;
            if (mStoreMetaDataInBuffers) {
                if (inHeader->nFilledLen != 8) {
                    ALOGE("MetaData buffer is wrong size! "
                            "(got %lu bytes, expected 8)", inHeader->nFilledLen);
                    mSignalledError = true;
                    notify(OMX_EventError, OMX_ErrorUndefined, 0, 0);
                    return;
                }
                inputData =
                        extractGrallocData(inHeader->pBuffer + inHeader->nOffset,
                                &srcBuffer);
                if (inputData == NULL) {
                    ALOGE("Unable to extract gralloc buffer in metadata mode");
                    mSignalledError = true;
                    notify(OMX_EventError, OMX_ErrorUndefined, 0, 0);
                        return;
                }
                // TODO: Verify/convert pixel format enum
            } else {
                inputData = (uint8_t *)inHeader->pBuffer + inHeader->nOffset;
            }

            if (mVideoColorFormat != OMX_COLOR_FormatYUV420Planar) {
                ConvertYUV420SemiPlanarToYUV420Planar(
                    inputData, mInputFrameData, mVideoWidth, mVideoHeight);
                inputData = mInputFrameData;
            }
            CHECK(inputData != NULL);

            VideoEncFrameIO vin, vout;
            memset(&vin, 0, sizeof(vin));
            memset(&vout, 0, sizeof(vout));
            vin.height = ((mVideoHeight  + 15) >> 4) << 4;
            vin.pitch = ((mVideoWidth + 15) >> 4) << 4;
            vin.timestamp = (inHeader->nTimeStamp + 500) / 1000;  // in ms
            vin.yChan = inputData;
            vin.uChan = vin.yChan + vin.height * vin.pitch;
            vin.vChan = vin.uChan + ((vin.height * vin.pitch) >> 2);

            unsigned long modTimeMs = 0;
            int32_t nLayer = 0;
            MP4HintTrack hintTrack;
            if (!PVEncodeVideoFrame(mHandle, &vin, &vout,
                    &modTimeMs, outPtr, &dataLength, &nLayer) ||
                !PVGetHintTrack(mHandle, &hintTrack)) {
                ALOGE("Failed to encode frame or get hink track at frame %lld",
                    mNumInputFrames);
                mSignalledError = true;
                notify(OMX_EventError, OMX_ErrorUndefined, 0, 0);
            }
            CHECK(NULL == PVGetOverrunBuffer(mHandle));
            if (hintTrack.CodeType == 0) {  // I-frame serves as sync frame
                outHeader->nFlags |= OMX_BUFFERFLAG_SYNCFRAME;
            }

            ++mNumInputFrames;
        } else {
            dataLength = 0;
        }

        inQueue.erase(inQueue.begin());
        inInfo->mOwnedByUs = false;
        releaseGrallocData(srcBuffer);
        notifyEmptyBufferDone(inHeader);

        outQueue.erase(outQueue.begin());
        CHECK(!mInputBufferInfoVec.empty());
        InputBufferInfo *inputBufInfo = mInputBufferInfoVec.begin();
        outHeader->nTimeStamp = inputBufInfo->mTimeUs;
        outHeader->nFlags |= (inputBufInfo->mFlags | OMX_BUFFERFLAG_ENDOFFRAME);
        outHeader->nFilledLen = dataLength;
        mInputBufferInfoVec.erase(mInputBufferInfoVec.begin());
        outInfo->mOwnedByUs = false;
        notifyFillBufferDone(outHeader);
    }
}

OMX_ERRORTYPE SoftMPEG4Encoder::getExtensionIndex(
        const char *name, OMX_INDEXTYPE *index) {
    if (!strcmp(name, "OMX.google.android.index.storeMetaDataInBuffers")) {
        *(int32_t*)index = kStoreMetaDataExtensionIndex;
        return OMX_ErrorNone;
    }
    return OMX_ErrorUndefined;
}

uint8_t *SoftMPEG4Encoder::extractGrallocData(void *data, buffer_handle_t *buffer) {
    OMX_U32 type = *(OMX_U32*)data;
    status_t res;
    if (type != kMetadataBufferTypeGrallocSource) {
        ALOGE("Data passed in with metadata mode does not have type "
                "kMetadataBufferTypeGrallocSource (%d), has type %ld instead",
                kMetadataBufferTypeGrallocSource, type);
        return NULL;
    }
    buffer_handle_t imgBuffer = *(buffer_handle_t*)((uint8_t*)data + 4);

    const Rect rect(mVideoWidth, mVideoHeight);
    uint8_t *img;
    res = GraphicBufferMapper::get().lock(imgBuffer,
            GRALLOC_USAGE_HW_VIDEO_ENCODER,
            rect, (void**)&img);
    if (res != OK) {
        ALOGE("%s: Unable to lock image buffer %p for access", __FUNCTION__,
                imgBuffer);
        return NULL;
    }

    *buffer = imgBuffer;
    return img;
}

void SoftMPEG4Encoder::releaseGrallocData(buffer_handle_t buffer) {
    if (mStoreMetaDataInBuffers) {
        GraphicBufferMapper::get().unlock(buffer);
    }
}

}  // namespace android

android::SoftOMXComponent *createSoftOMXComponent(
        const char *name, const OMX_CALLBACKTYPE *callbacks,
        OMX_PTR appData, OMX_COMPONENTTYPE **component) {
    return new android::SoftMPEG4Encoder(name, callbacks, appData, component);
}
