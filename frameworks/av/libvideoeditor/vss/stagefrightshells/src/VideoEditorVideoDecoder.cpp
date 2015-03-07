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
/**
*************************************************************************
* @file   VideoEditorVideoDecoder.cpp
* @brief  StageFright shell video decoder
*************************************************************************
*/
#define LOG_NDEBUG 0
#define LOG_TAG "VIDEOEDITOR_VIDEODECODER"
/*******************
 *     HEADERS     *
 *******************/

#include "VideoEditorVideoDecoder_internal.h"
#include "VideoEditorUtils.h"
#include "M4VD_Tools.h"

#ifndef ANDROID_DEFAULT_CODE
#include "M4OSA_Thread.h"
#endif

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/MediaDefs.h>
/********************
 *   DEFINITIONS    *
 ********************/
#define MAX_DEC_BUFFERS 10

/********************
 *   SOURCE CLASS   *
 ********************/
using namespace android;
#ifndef ANDROID_DEFAULT_CODE
#define VE_MVA_MODE 1
#define ROUND_16(X)     ((X + 0xF) & (~0xF))
#if VE_MVA_MODE
#define MEM_ALIGN_32 32
#define ROUND_32(X)     ((X + 0x1F) & (~0x1F))
#define YUV_SIZE(W,H)   (W * H * 3 >> 1)
#endif //VE_MVA_MODE

#ifdef USE_VIDEO_ION
#include <linux/ion_drv.h>
#include <ion/ion.h>
#include <fcntl.h>
#endif //USE_VIDEO_ION
#include "DpBlitStream.h"
#endif //ANDROID_DEFAULT_CODE

static M4OSA_ERR copyBufferToQueue(
    VideoEditorVideoDecoder_Context* pDecShellContext,
    MediaBuffer* pDecodedBuffer);

class VideoEditorVideoDecoderSource : public MediaSource {
    public:

        VideoEditorVideoDecoderSource(
            const sp<MetaData> &format,
            VIDEOEDITOR_CodecType codecType,
            void *decoderShellContext);

        virtual status_t start(MetaData *params = NULL);
        virtual status_t stop();
        virtual sp<MetaData> getFormat();
        virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

    protected :
        virtual ~VideoEditorVideoDecoderSource();

    private:
        sp<MetaData> mFormat;
        MediaBuffer* mBuffer;
        MediaBufferGroup* mGroup;
        Mutex mLock;
        VideoEditorVideoDecoder_Context* mpDecShellContext;
        int32_t mMaxAUSize;
        bool mStarted;
        VIDEOEDITOR_CodecType mCodecType;

        // Don't call me
        VideoEditorVideoDecoderSource(const VideoEditorVideoDecoderSource &);
        VideoEditorVideoDecoderSource &operator=(
            const VideoEditorVideoDecoderSource &);
};

VideoEditorVideoDecoderSource::VideoEditorVideoDecoderSource(
        const sp<MetaData> &format, VIDEOEDITOR_CodecType codecType,
        void *decoderShellContext) :
        mFormat(format),
        mBuffer(NULL),
        mGroup(NULL),
        mStarted(false),
        mCodecType(codecType) {
    mpDecShellContext = (VideoEditorVideoDecoder_Context*) decoderShellContext;
}

VideoEditorVideoDecoderSource::~VideoEditorVideoDecoderSource() {
    if (mStarted == true) {
        stop();
    }
}

status_t VideoEditorVideoDecoderSource::start(
        MetaData *params) {

    if (!mStarted) {
        if (mFormat->findInt32(kKeyMaxInputSize, &mMaxAUSize) == false) {
            ALOGE("Could not find kKeyMaxInputSize");
            return ERROR_MALFORMED;
        }

        mGroup = new MediaBufferGroup;
        if (mGroup == NULL) {
            ALOGE("FATAL: memory limitation ! ");
            return NO_MEMORY;
        }

        mGroup->add_buffer(new MediaBuffer(mMaxAUSize));

        mStarted = true;
    }
    return OK;
}

status_t VideoEditorVideoDecoderSource::stop() {
    if (mStarted) {
        if (mBuffer != NULL) {

            // FIXME:
            // Why do we need to check on the ref count?
            int ref_count = mBuffer->refcount();
            ALOGV("MediaBuffer refcount is %d",ref_count);
            for (int i = 0; i < ref_count; ++i) {
                mBuffer->release();
            }

            mBuffer = NULL;
        }
        delete mGroup;
        mGroup = NULL;
        mStarted = false;
    }
    return OK;
}

sp<MetaData> VideoEditorVideoDecoderSource::getFormat() {
    Mutex::Autolock autolock(mLock);

    return mFormat;
}

status_t VideoEditorVideoDecoderSource::read(MediaBuffer** buffer_out,
        const ReadOptions *options) {

    Mutex::Autolock autolock(mLock);
    if (options != NULL) {
        int64_t time_us;
        MediaSource::ReadOptions::SeekMode mode;
        options->getSeekTo(&time_us, &mode);
        if (mode != MediaSource::ReadOptions::SEEK_PREVIOUS_SYNC) {
            ALOGE("Unexpected read options");
            return BAD_VALUE;
        }

        M4OSA_ERR err;
        M4OSA_Int32 rapTime = time_us / 1000;

        /*--- Retrieve the previous RAP time ---*/
        err = mpDecShellContext->m_pReaderGlobal->m_pFctGetPrevRapTime(
                  mpDecShellContext->m_pReader->m_readerContext,
                  (M4_StreamHandler*)mpDecShellContext->m_pVideoStreamhandler,
                  &rapTime);

        if (err == M4WAR_READER_INFORMATION_NOT_PRESENT) {
            /* No RAP table, jump backward and predecode */
            rapTime -= 40000;
            if(rapTime < 0) rapTime = 0;
        } else if (err != OK) {
            ALOGE("get rap time error = 0x%x\n", (uint32_t)err);
            return UNKNOWN_ERROR;
        }

        err = mpDecShellContext->m_pReaderGlobal->m_pFctJump(
                   mpDecShellContext->m_pReader->m_readerContext,
                   (M4_StreamHandler*)mpDecShellContext->m_pVideoStreamhandler,
                   &rapTime);

        if (err != OK) {
            ALOGE("jump err = 0x%x\n", (uint32_t)err);
            return BAD_VALUE;
        }
    }

    *buffer_out = NULL;

    M4OSA_ERR lerr = mGroup->acquire_buffer(&mBuffer);
    if (lerr != OK) {
        return lerr;
    }
    mBuffer->meta_data()->clear();  // clear all the meta data

    if (mStarted) {
        //getNext AU from reader.
        M4_AccessUnit* pAccessUnit = mpDecShellContext->m_pNextAccessUnitToDecode;
        lerr = mpDecShellContext->m_pReader->m_pFctGetNextAu(
                   mpDecShellContext->m_pReader->m_readerContext,
                   (M4_StreamHandler*)mpDecShellContext->m_pVideoStreamhandler,
                   pAccessUnit);
        if (lerr == M4WAR_NO_DATA_YET || lerr == M4WAR_NO_MORE_AU) {
            *buffer_out = NULL;
            return ERROR_END_OF_STREAM;
        }

        //copy the reader AU buffer to mBuffer
        M4OSA_UInt32 lSize  = (pAccessUnit->m_size > (M4OSA_UInt32)mMaxAUSize)\
            ? (M4OSA_UInt32)mMaxAUSize : pAccessUnit->m_size;
        memcpy((void *)mBuffer->data(),(void *)pAccessUnit->m_dataAddress,
            lSize);
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("VideoEditorVideoDecoderSource::read m_size %d", pAccessUnit->m_size);
#endif //ANDROID_DEFAULT_CODE
        mBuffer->set_range(0, lSize);
        int64_t frameTimeUs = (int64_t) (pAccessUnit->m_CTS * 1000);
        mBuffer->meta_data()->setInt64(kKeyTime, frameTimeUs);

#ifdef ANDROID_DEFAULT_CODE // Morris Yang 20120320 remove NAL start code
        // Replace the AU start code for H264
        if (VIDEOEDITOR_kH264VideoDec == mCodecType) {
            uint8_t *data =(uint8_t *)mBuffer->data() + mBuffer->range_offset();
            ALOGD ("@@ ORI (0x%02X, 0x%02X, 0x%02X, 0x%02X, 0x%02X, 0x%02X, 0x%02X, 0x%02X)", data[0], data[1], data[2], data[3], data[4], data[5], data[6], data[7]);
            data[0]=0;
            data[1]=0;
            data[2]=0;
            data[3]=1;
        }
#endif
        mBuffer->meta_data()->setInt32(kKeyIsSyncFrame,
            (pAccessUnit->m_attribute == 0x04)? 1 : 0);
        *buffer_out = mBuffer;
    }
    return OK;
}

#ifdef USE_VIDEO_ION
M4OSA_ERR VideoEditorVideoDecoder_initPoolIONBuffers(M4OSA_Context pContext, VIDEOEDITOR_BUFFER_Pool* pool,
    M4OSA_UInt32 lSize)
{
    M4OSA_ERR     err = M4NO_ERROR;
    M4OSA_UInt32  index, j;
    VideoEditorVideoDecoder_Context* pDecShellContext = M4OSA_NULL;
    //VIDEOEDITOR_CHECK(M4OSA_NULL != pContext, M4ERR_PARAMETER);
    ALOGV("VideoEditorVideoDecoder_initPoolIONBuffers begin");

    pDecShellContext = (VideoEditorVideoDecoder_Context*)pContext;

    struct ion_handle* handle;
    unsigned char* pIonBuffer = 0;
    int share_fd = -1;

    /**
     * Initialize all the buffers in the pool */
    for(index = 0; index < pool->NB; index++)
    {
        int ret = ion_alloc_mm(pDecShellContext->mIonDevFd, lSize, 512, 0, &handle);
        if (0 != ret) {
            ALOGE ("[ERROR] ion_alloc_mm failed (%d), LINE:%d", ret, __LINE__);
            return UNKNOWN_ERROR;
        }
        if (ion_share(pDecShellContext->mIonDevFd, handle, &share_fd)) {
            ALOGE ("[ERROR] ion_share failed, LINE:%d", __LINE__);
        }
        // map virtual address
        pIonBuffer = (OMX_U8*) ion_mmap(pDecShellContext->mIonDevFd, NULL, lSize, PROT_READ | PROT_WRITE, MAP_SHARED, share_fd, 0);
        if ((pIonBuffer == NULL) || (pIonBuffer == (void*)-1)) {
            ALOGE ("[ERROR] ion_mmap failed, LINE:%d", __LINE__);
            return UNKNOWN_ERROR;
        }

        pool->pNXPBuffer[index].pData = (M4OSA_Void*)pIonBuffer;
        pool->pNXPBuffer[index].size = 0;
        pool->pNXPBuffer[index].state = VIDEOEDITOR_BUFFER_kEmpty;
        pool->pNXPBuffer[index].idx = index;
        pool->pNXPBuffer[index].buffCTS = -1;

        pDecShellContext->mIonBufInfo[index].IonFd = share_fd;
        pDecShellContext->mIonBufInfo[index].u4BufSize = lSize;
        pDecShellContext->mIonBufInfo[index].u4BufVA = (uint32_t)pIonBuffer;
        pDecShellContext->mIonBufInfo[index].u4IonBufHandle = (uint32_t)handle;
        ALOGD("VIDEOEDITOR_BUFFER_initPoolBuffers: index %d, pData = %p, lSize %d, handle %x, share_fd %x", index, pool->pNXPBuffer[index].pData, lSize, handle, share_fd);
    }

    return err;
}

M4OSA_ERR VideoEditorVideoDecoder_freePoolION(M4OSA_Context pContext, VIDEOEDITOR_BUFFER_Pool* ppool)
{
    M4OSA_ERR err;
    M4OSA_UInt32  j = 0;

    ALOGV("VideoEditorVideoDecoder_freePoolION : ppool = 0x%x", ppool);

    err = M4NO_ERROR;

    for (j = 0; j < ppool->NB; j++)
    {
        if(M4OSA_NULL != ppool->pNXPBuffer[j].pData)
        {
            ppool->pNXPBuffer[j].pData = M4OSA_NULL;
        }
    }

    if(ppool != M4OSA_NULL)
    {
        SAFE_FREE(ppool->pNXPBuffer);
        SAFE_FREE(ppool->poolName);
        SAFE_FREE(ppool);
    }

    return(err);
}

#endif //USE_VIDEO_ION

static M4OSA_UInt32 VideoEditorVideoDecoder_GetBitsFromMemory(
        VIDEOEDITOR_VIDEO_Bitstream_ctxt* parsingCtxt, M4OSA_UInt32 nb_bits) {
    return (M4VD_Tools_GetBitsFromMemory((M4VS_Bitstream_ctxt*) parsingCtxt,
            nb_bits));
}

M4OSA_ERR VideoEditorVideoDecoder_internalParseVideoDSI(M4OSA_UInt8* pVol,
        M4OSA_Int32 aVolSize, M4DECODER_MPEG4_DecoderConfigInfo* pDci,
        M4DECODER_VideoSize* pVideoSize) {

    VIDEOEDITOR_VIDEO_Bitstream_ctxt parsingCtxt;
    M4OSA_UInt32 code, j;
    M4OSA_MemAddr8 start;
    M4OSA_UInt8 i;
    M4OSA_UInt32 time_incr_length;
    M4OSA_UInt8 vol_verid=0, b_hierarchy_type;

    /* Parsing variables */
    M4OSA_UInt8 video_object_layer_shape = 0;
    M4OSA_UInt8 sprite_enable = 0;
    M4OSA_UInt8 reduced_resolution_vop_enable = 0;
    M4OSA_UInt8 scalability = 0;
    M4OSA_UInt8 enhancement_type = 0;
    M4OSA_UInt8 complexity_estimation_disable = 0;
    M4OSA_UInt8 interlaced = 0;
    M4OSA_UInt8 sprite_warping_points = 0;
    M4OSA_UInt8 sprite_brightness_change = 0;
    M4OSA_UInt8 quant_precision = 0;

    /* Fill the structure with default parameters */
    pVideoSize->m_uiWidth      = 0;
    pVideoSize->m_uiHeight     = 0;

    pDci->uiTimeScale          = 0;
    pDci->uiProfile            = 0;
    pDci->uiUseOfResynchMarker = 0;
    pDci->bDataPartition       = M4OSA_FALSE;
    pDci->bUseOfRVLC           = M4OSA_FALSE;

    /* Reset the bitstream context */
    parsingCtxt.stream_byte = 0;
    parsingCtxt.stream_index = 8;
    parsingCtxt.in = (M4OSA_MemAddr8) pVol;

    start = (M4OSA_MemAddr8) pVol;

    /* Start parsing */
    while (parsingCtxt.in - start < aVolSize) {
        code = VideoEditorVideoDecoder_GetBitsFromMemory(&parsingCtxt, 8);
        if (code == 0) {
            code = VideoEditorVideoDecoder_GetBitsFromMemory(&parsingCtxt, 8);
            if (code == 0) {
                code = VideoEditorVideoDecoder_GetBitsFromMemory(&parsingCtxt,8);
                if (code == 1) {
                    /* start code found */
                    code = VideoEditorVideoDecoder_GetBitsFromMemory(
                        &parsingCtxt, 8);

                    /* ----- 0x20..0x2F : video_object_layer_start_code ----- */

                    if ((code > 0x1F) && (code < 0x30)) {
                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);
                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 8);
                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);
                        if (code == 1) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 4);
                            vol_verid = (M4OSA_UInt8)code;
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 3);
                        }
                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 4);
                        if (code == 15) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 16);
                        }
                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);
                        if (code == 1) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 3);
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 1);
                            if (code == 1) {
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 32);
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 31);
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 16);
                            }
                        }
                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 2);
                        /* Need to save it for vop parsing */
                        video_object_layer_shape = (M4OSA_UInt8)code;

                        if (code != 0) {
                            return 0;    /* only rectangular case supported */
                        }

                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);
                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 16);
                        pDci->uiTimeScale = code;

                        /* Computes time increment length */
                        j    = code - 1;
                        for (i = 0; (i < 32) && (j != 0); j >>=1) {
                            i++;
                        }
                        time_incr_length = (i == 0) ? 1 : i;

                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);
                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);
                        if (code == 1) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, time_incr_length);
                        }

                        if(video_object_layer_shape != 1) { /* 1 = Binary */
                            if(video_object_layer_shape == 0) {
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 1);/* Marker bit */
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 13);/* Width */
                                pVideoSize->m_uiWidth = code;
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 1);/* Marker bit */
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 13);/* Height */
                                pVideoSize->m_uiHeight = code;
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 1);/* Marker bit */
                            }
                        }

                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);/* interlaced */
                        interlaced = (M4OSA_UInt8)code;
                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);/* OBMC disable */

                        if(vol_verid == 1) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 1);/* sprite enable */
                            sprite_enable = (M4OSA_UInt8)code;
                        } else {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 2);/* sprite enable */
                            sprite_enable = (M4OSA_UInt8)code;
                        }
                        if ((sprite_enable == 1) || (sprite_enable == 2)) {
                            if (sprite_enable != 2) {

                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 13);/* sprite width */
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 1);/* Marker bit */
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 13);/* sprite height */
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 1);/* Marker bit */
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 13);/* sprite l coordinate */
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 1);/* Marker bit */
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 13);/* sprite top coordinate */
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 1);/* Marker bit */
                            }

                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 6);/* sprite warping points */
                            sprite_warping_points = (M4OSA_UInt8)code;
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 2);/* sprite warping accuracy */
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 1);/* sprite brightness change */
                            sprite_brightness_change = (M4OSA_UInt8)code;
                            if (sprite_enable != 2) {
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 1);
                            }
                        }
                        if ((vol_verid != 1) && (video_object_layer_shape != 0)){
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);/* sadct disable */
                        }

                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1); /* not 8 bits */
                        if (code) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 4);/* quant precision */
                            quant_precision = (M4OSA_UInt8)code;
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 4);/* bits per pixel */
                        }

                        /* greyscale not supported */
                        if(video_object_layer_shape == 3) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 3);
                        }

                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);/* quant type */
                        if (code) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 1);/* load intra quant mat */
                            if (code) {
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 8);/* */
                                i    = 1;
                                while (i < 64) {
                                    code =
                                        VideoEditorVideoDecoder_GetBitsFromMemory(
                                            &parsingCtxt, 8);
                                    if (code == 0) {
                                        break;
                                    }
                                    i++;
                                }
                            }

                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 1);/* load non intra quant mat */
                            if (code) {
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 8);/* */
                                i    = 1;
                                while (i < 64) {
                                    code =
                                        VideoEditorVideoDecoder_GetBitsFromMemory(
                                        &parsingCtxt, 8);
                                    if (code == 0) {
                                        break;
                                    }
                                    i++;
                                }
                            }
                        }

                        if (vol_verid != 1) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 1);/* quarter sample */
                        }

                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);/* complexity estimation disable */
                        complexity_estimation_disable = (M4OSA_UInt8)code;
                        if (!code) {
                            //return M4ERR_NOT_IMPLEMENTED;
                        }

                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);/* resync marker disable */
                        pDci->uiUseOfResynchMarker = (code) ? 0 : 1;

                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);/* data partitionned */
                        pDci->bDataPartition = (code) ? M4OSA_TRUE : M4OSA_FALSE;
                        if (code) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 1);/* reversible VLC */
                            pDci->bUseOfRVLC = (code) ? M4OSA_TRUE : M4OSA_FALSE;
                        }

                        if (vol_verid != 1) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 1);/* newpred */
                            if (code) {
                                //return M4ERR_PARAMETER;
                            }

                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 1);
                            reduced_resolution_vop_enable = (M4OSA_UInt8)code;
                        }

                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);/* scalability */
                        scalability = (M4OSA_UInt8)code;
                        if (code) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 1);/* hierarchy type */
                            b_hierarchy_type = (M4OSA_UInt8)code;
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 4);/* ref layer id */
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 1);/* ref sampling direct */
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 5);/* hor sampling factor N */
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 5);/* hor sampling factor M */
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 5);/* vert sampling factor N */
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 5);/* vert sampling factor M */
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 1);/* enhancement type */
                            enhancement_type = (M4OSA_UInt8)code;
                            if ((!b_hierarchy_type) &&
                                    (video_object_layer_shape == 1)) {
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 1);/* use ref shape */
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 1);/* use ref texture */
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 5);
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 5);
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 5);
                                code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                    &parsingCtxt, 5);
                            }
                        }
                        break;
                    }

                    /* ----- 0xB0 : visual_object_sequence_start_code ----- */

                    else if(code == 0xB0) {
                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 8);/* profile_and_level_indication */
                        pDci->uiProfile = (M4OSA_UInt8)code;
                    }

                    /* ----- 0xB5 : visual_object_start_code ----- */

                    else if(code == 0xB5) {
                        code = VideoEditorVideoDecoder_GetBitsFromMemory(
                            &parsingCtxt, 1);/* is object layer identifier */
                        if (code == 1) {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 4); /* visual object verid */
                            vol_verid = (M4OSA_UInt8)code;
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 3);
                        } else {
                            code = VideoEditorVideoDecoder_GetBitsFromMemory(
                                &parsingCtxt, 7); /* Realign on byte */
                            vol_verid = 1;
                        }
                    }

                    /* ----- end ----- */
                } else {
                    if ((code >> 2) == 0x20) {
                        /* H263 ...-> wrong*/
                        break;
                    }
                }
            }
        }
    }
    return M4NO_ERROR;
}

M4VIFI_UInt8 M4VIFI_SemiplanarYVU420toYUV420(void *user_data,
        M4VIFI_UInt8 *inyuv, M4VIFI_ImagePlane *PlaneOut ) {
    M4VIFI_UInt8 return_code = M4VIFI_OK;
    M4VIFI_UInt8 *outyuv =
        ((M4VIFI_UInt8*)&(PlaneOut[0].pac_data[PlaneOut[0].u_topleft]));
    int32_t width = PlaneOut[0].u_width;
    int32_t height = PlaneOut[0].u_height;

    int32_t outYsize = width * height;
    uint32_t *outy =  (uint32_t *) outyuv;
    uint16_t *outcb =
        (uint16_t *) &(PlaneOut[1].pac_data[PlaneOut[1].u_topleft]);
    uint16_t *outcr =
        (uint16_t *) &(PlaneOut[2].pac_data[PlaneOut[2].u_topleft]);

    /* Y copying */
    memcpy((void *)outy, (void *)inyuv, outYsize);

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
    return return_code;
}
void logSupportDecodersAndCapabilities(M4DECODER_VideoDecoders* decoders) {
    VideoDecoder *pDecoder;
    VideoComponentCapabilities *pOmxComponents = NULL;
    VideoProfileLevel *pProfileLevel = NULL;
    pDecoder = decoders->decoder;
    for (size_t i = 0; i< decoders->decoderNumber; i++) {
        ALOGV("Supported Codec[%d] :%d", i, pDecoder->codec);
        pOmxComponents = pDecoder->component;
        for(size_t j = 0; j <  pDecoder->componentNumber; j++) {
           pProfileLevel = pOmxComponents->profileLevel;
           ALOGV("-->component %d", j);
           for(size_t k = 0; k < pOmxComponents->profileNumber; k++) {
               ALOGV("-->profile:%ld maxLevel:%ld", pProfileLevel->mProfile,
                   pProfileLevel->mLevel);
               pProfileLevel++;
           }
           pOmxComponents++;
        }
        pDecoder++;
    }
}

M4OSA_ERR queryVideoDecoderCapabilities
    (M4DECODER_VideoDecoders** decoders) {
    M4OSA_ERR err = M4NO_ERROR;
    const char *kMimeTypes[] = {
        MEDIA_MIMETYPE_VIDEO_AVC, MEDIA_MIMETYPE_VIDEO_MPEG4,
        MEDIA_MIMETYPE_VIDEO_H263
    };

    int32_t supportFormats = sizeof(kMimeTypes) / sizeof(kMimeTypes[0]);
    M4DECODER_VideoDecoders *pDecoders;
    VideoDecoder *pDecoder;
    VideoComponentCapabilities *pOmxComponents = NULL;
    VideoProfileLevel *pProfileLevel = NULL;
    OMXClient client;
    status_t status = OK;
    SAFE_MALLOC(pDecoders, M4DECODER_VideoDecoders, 1, "VideoDecoders");
    SAFE_MALLOC(pDecoder, VideoDecoder, supportFormats,
        "VideoDecoder");
    pDecoders->decoder = pDecoder;

    pDecoders->decoderNumber= supportFormats;
    status = client.connect();
    CHECK(status == OK);
    for (size_t k = 0; k < sizeof(kMimeTypes) / sizeof(kMimeTypes[0]);
             ++k) {
            Vector<CodecCapabilities> results;
            CHECK_EQ(QueryCodecs(client.interface(), kMimeTypes[k],
                                 true, // queryDecoders
                                 &results), (status_t)OK);

            if (results.size()) {
                SAFE_MALLOC(pOmxComponents, VideoComponentCapabilities,
                    results.size(), "VideoComponentCapabilities");
                ALOGV("K=%d",k);
                pDecoder->component = pOmxComponents;
                pDecoder->componentNumber = results.size();
            }

            for (size_t i = 0; i < results.size(); ++i) {
                ALOGV("  decoder '%s' supports ",
                       results[i].mComponentName.string());

                if (results[i].mProfileLevels.size() == 0) {
                    ALOGV("NOTHING.\n");
                    continue;
                }

#if 0
                // FIXME:
                // We should ignore the software codecs and make IsSoftwareCodec()
                // part of pubic API from OMXCodec.cpp
                if (IsSoftwareCodec(results[i].mComponentName.string())) {
                    ALOGV("Ignore software codec %s", results[i].mComponentName.string());
                    continue;
                }
#endif

                // Count the supported profiles
                int32_t profileNumber = 0;
                int32_t profile = -1;
                for (size_t j = 0; j < results[i].mProfileLevels.size(); ++j) {
                    const CodecProfileLevel &profileLevel =
                        results[i].mProfileLevels[j];
                    // FIXME: assume that the profiles are ordered
                    if (profileLevel.mProfile != profile) {
                        profile = profileLevel.mProfile;
                        profileNumber++;
                    }
                }
                SAFE_MALLOC(pProfileLevel, VideoProfileLevel,
                    profileNumber, "VideoProfileLevel");
                pOmxComponents->profileLevel = pProfileLevel;
                pOmxComponents->profileNumber = profileNumber;

                // Get the max Level for each profile.
                int32_t maxLevel = -1;
                profile = -1;
                profileNumber = 0;
                for (size_t j = 0; j < results[i].mProfileLevels.size(); ++j) {
                    const CodecProfileLevel &profileLevel =
                        results[i].mProfileLevels[j];
                    if (profile == -1 && maxLevel == -1) {
                        profile = profileLevel.mProfile;
                        maxLevel = profileLevel.mLevel;
                        pProfileLevel->mProfile = profile;
                        pProfileLevel->mLevel = maxLevel;
                        ALOGV("%d profile: %ld, max level: %ld",
                            __LINE__, pProfileLevel->mProfile, pProfileLevel->mLevel);
                    }
                    if (profileLevel.mProfile != profile) {
                        profile = profileLevel.mProfile;
                        maxLevel = profileLevel.mLevel;
                        profileNumber++;
                        pProfileLevel++;
                        pProfileLevel->mProfile = profile;
                        pProfileLevel->mLevel = maxLevel;
                        ALOGV("%d profile: %ld, max level: %ld",
                            __LINE__, pProfileLevel->mProfile, pProfileLevel->mLevel);
                    } else if (profileLevel.mLevel > maxLevel) {
                        maxLevel = profileLevel.mLevel;
                        pProfileLevel->mLevel = maxLevel;
                        ALOGV("%d profile: %ld, max level: %ld",
                            __LINE__, pProfileLevel->mProfile, pProfileLevel->mLevel);
                    }

                }
                pOmxComponents++;
            }
            if (!strcmp(MEDIA_MIMETYPE_VIDEO_AVC, kMimeTypes[k]))
                pDecoder->codec = M4DA_StreamTypeVideoMpeg4Avc;
            if (!strcmp(MEDIA_MIMETYPE_VIDEO_MPEG4, kMimeTypes[k]))
                pDecoder->codec = M4DA_StreamTypeVideoMpeg4;
            if (!strcmp(MEDIA_MIMETYPE_VIDEO_H263, kMimeTypes[k]))
                pDecoder->codec = M4DA_StreamTypeVideoH263;

            pDecoder++;
    }

    logSupportDecodersAndCapabilities(pDecoders);
    *decoders = pDecoders;
cleanUp:
    return err;
}
/********************
 * ENGINE INTERFACE *
 ********************/
M4OSA_ERR VideoEditorVideoDecoder_configureFromMetadata(M4OSA_Context pContext,
        MetaData* meta) {
    M4OSA_ERR err = M4NO_ERROR;
    VideoEditorVideoDecoder_Context* pDecShellContext = M4OSA_NULL;
    bool success = OK;
    int32_t width = 0;
    int32_t height = 0;
    int32_t frameSize = 0;
    int32_t vWidth, vHeight;
    int32_t cropLeft, cropTop, cropRight, cropBottom;

    VIDEOEDITOR_CHECK(M4OSA_NULL != pContext, M4ERR_PARAMETER);
    VIDEOEDITOR_CHECK(M4OSA_NULL != meta,     M4ERR_PARAMETER);

    ALOGV("VideoEditorVideoDecoder_configureFromMetadata begin");

    pDecShellContext = (VideoEditorVideoDecoder_Context*)pContext;

    success = meta->findInt32(kKeyWidth, &vWidth);
    VIDEOEDITOR_CHECK(TRUE == success, M4ERR_PARAMETER);
    success = meta->findInt32(kKeyHeight, &vHeight);
    VIDEOEDITOR_CHECK(TRUE == success, M4ERR_PARAMETER);

    ALOGV("vWidth = %d, vHeight = %d", vWidth, vHeight);

    pDecShellContext->mGivenWidth = vWidth;
    pDecShellContext->mGivenHeight = vHeight;

    if (!meta->findRect(
                kKeyCropRect, &cropLeft, &cropTop, &cropRight, &cropBottom)) {

        cropLeft = cropTop = 0;
        cropRight = vWidth - 1;
        cropBottom = vHeight - 1;

        ALOGV("got dimensions only %d x %d", width, height);
    } else {
        ALOGV("got crop rect %d, %d, %d, %d",
             cropLeft, cropTop, cropRight, cropBottom);
    }

    pDecShellContext->mCropRect.left = cropLeft;
    pDecShellContext->mCropRect.right = cropRight;
    pDecShellContext->mCropRect.top = cropTop;
    pDecShellContext->mCropRect.bottom = cropBottom;

    width = cropRight - cropLeft + 1;
    height = cropBottom - cropTop + 1;

    ALOGV("VideoDecoder_configureFromMetadata : W=%d H=%d", width, height);
    VIDEOEDITOR_CHECK((0 != width) && (0 != height), M4ERR_PARAMETER);

    if( (M4OSA_NULL != pDecShellContext->m_pDecBufferPool) &&
        (pDecShellContext->m_pVideoStreamhandler->m_videoWidth  == \
            (uint32_t)width) &&
        (pDecShellContext->m_pVideoStreamhandler->m_videoHeight == \
            (uint32_t)height) ) {
        // No need to reconfigure
        goto cleanUp;
    }
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("VideoDecoder_configureFromMetadata reset: W=%d H=%d, %d, %d", width, height, pDecShellContext->mGivenWidth, pDecShellContext->mGivenHeight);
#else
    ALOGV("VideoDecoder_configureFromMetadata  reset: W=%d H=%d", width, height);
#endif //ANDROID_DEFAULT_CODE
    // Update the stream handler parameters
    pDecShellContext->m_pVideoStreamhandler->m_videoWidth  = width;
    pDecShellContext->m_pVideoStreamhandler->m_videoHeight = height;

#ifndef ANDROID_DEFAULT_CODE  // Morris Yang, for non 16x width video
    frameSize = (ROUND_16(width)* ROUND_16(height)* 3) / 2;
#else
    frameSize = (width * height * 3) / 2;
#endif

    // Configure the buffer pool
    if( M4OSA_NULL != pDecShellContext->m_pDecBufferPool ) {
        ALOGV("VideoDecoder_configureFromMetadata : reset the buffer pool");
        VIDEOEDITOR_BUFFER_freePool(pDecShellContext->m_pDecBufferPool);
        pDecShellContext->m_pDecBufferPool = M4OSA_NULL;
    }
    err =  VIDEOEDITOR_BUFFER_allocatePool(&pDecShellContext->m_pDecBufferPool,
        MAX_DEC_BUFFERS, (M4OSA_Char*)"VIDEOEDITOR_DecodedBufferPool");
    VIDEOEDITOR_CHECK(M4NO_ERROR == err, err);
#ifdef USE_VIDEO_ION
    err = VideoEditorVideoDecoder_initPoolIONBuffers(pContext, pDecShellContext->m_pDecBufferPool,
                frameSize);
#else //USE_VIDEO_ION
    err = VIDEOEDITOR_BUFFER_initPoolBuffers(pDecShellContext->m_pDecBufferPool,
                frameSize + pDecShellContext->mGivenWidth * 2);
#endif //USE_VIDEO_ION
    VIDEOEDITOR_CHECK(M4NO_ERROR == err, err);

cleanUp:
    if( M4NO_ERROR == err ) {
        ALOGV("VideoEditorVideoDecoder_configureFromMetadata no error");
    } else {
        if( M4OSA_NULL != pDecShellContext->m_pDecBufferPool ) {
            VIDEOEDITOR_BUFFER_freePool(pDecShellContext->m_pDecBufferPool);
            pDecShellContext->m_pDecBufferPool = M4OSA_NULL;
        }
        ALOGE("VideoEditorVideoDecoder_configureFromMetadata ERROR 0x%X", err);
    }
    ALOGV("VideoEditorVideoDecoder_configureFromMetadata end");
    return err;
}

M4OSA_ERR VideoEditorVideoDecoder_destroy(M4OSA_Context pContext) {
    M4OSA_ERR err = M4NO_ERROR;
    VideoEditorVideoDecoder_Context* pDecShellContext =
        (VideoEditorVideoDecoder_Context*)pContext;

    // Input parameters check
    ALOGD("VideoEditorVideoDecoder_destroy begin");
    VIDEOEDITOR_CHECK(M4OSA_NULL != pContext, M4ERR_PARAMETER);
#ifndef ANDROID_DEFAULT_CODE
    if (pDecShellContext->mI420ColorConverter) {
#ifdef USE_VIDEO_ION
        for (int i = 0 ; i < MAX_ION_BUF_COUNT ; i++) {
            if (pDecShellContext->mIonBufInfo[i].IonFd  != 0xFFFFFFFF) {
                 ALOGD ("[free ION buffer fd] i:%d, u4BufVA %x, u4BufSize %d, IonFd %x", 
                 i, pDecShellContext->mIonBufInfo[i].u4BufVA, pDecShellContext->mIonBufInfo[i].u4BufSize, pDecShellContext->mIonBufInfo[i].IonFd);
                 ion_munmap(pDecShellContext->mIonDevFd, (void*)pDecShellContext->mIonBufInfo[i].u4BufVA, pDecShellContext->mIonBufInfo[i].u4BufSize);  
                
                // free ION buffer fd
                if (ion_share_close (pDecShellContext->mIonDevFd, pDecShellContext->mIonBufInfo[i].IonFd)) {
                    ALOGE ("[ERROR] ion_share_close failed, LINE:%d", __LINE__);
                }

                // free ION buffer handle
                //MTK_OMX_LOGD ("@@ calling ion_free mIonDevFd(0x%08X)", mIonDevFd);
                if (ion_free(pDecShellContext->mIonDevFd, (struct ion_handle *)pDecShellContext->mIonBufInfo[i].u4IonBufHandle)) {
                    ALOGE ("[ERROR] ion_free failed in FreeBuffer, LINE:%d", __LINE__);
                }
            }
        }
        if (-1 != pDecShellContext->mIonDevFd) {
            close(pDecShellContext->mIonDevFd);
        }
#if VE_MVA_MODE
        #if 0
        ALOGV("mMVAParamSrc ownNum %d", pDecShellContext->mMVAParamSrc.ownNum);
        for(int i=0; i<pDecShellContext->mMVAParamSrc.ownNum; i++)
        {
#if 0
            pDecShellContext->mI420ColorConverter->freeVideoAllocMVA(pDecShellContext->mI420ColorConverter, 
                pDecShellContext->mMVAParamSrc.mkM4UBufferHandle, pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferVaA, 
                pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferPaB, pDecShellContext->mMVAParamSrc.mM4UBufferSize, NULL );
#endif
            ALOGD("Free srcYUVbuf V: %p, P: %p", pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferVaA, pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferPaB);
            pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferVaA = 0xffffffff;
            pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferPaB = 0xffffffff;
        }
#endif
        ALOGV("mMVAParamDst ownNum %d", pDecShellContext->mMVAParamDst.ownNum);
        for(int i=0; i<pDecShellContext->mMVAParamDst.ownNum; i++)
        {
            pDecShellContext->mI420ColorConverter->freeVideoAllocMVA(pDecShellContext->mI420ColorConverter, 
                pDecShellContext->mMVAParamDst.mkM4UBufferHandle, pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferVaA, 
                pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferPaB, pDecShellContext->mMVAParamDst.mM4UBufferSize, NULL);

            ALOGD("Free dstYUVbuf V: %p, P: %p", pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferVaA, pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferPaB);
            pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferVaA = 0xffffffff;
            pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferPaB = 0xffffffff;
        }

        pDecShellContext->mI420ColorConverter->deinitACodecColorConverter(pDecShellContext->mI420ColorConverter, pDecShellContext->mMVAParamSrc.mkM4UBufferHandle);
        pDecShellContext->mI420ColorConverter->deinitACodecColorConverter(pDecShellContext->mI420ColorConverter, pDecShellContext->mMVAParamDst.mkM4UBufferHandle);
#endif //VE_MVA_MODE
#else  //USE_VIDEO_ION
#if VE_MVA_MODE
        #if 0
        ALOGV("mMVAParamSrc ownNum %d", pDecShellContext->mMVAParamSrc.ownNum);
        for(int i=0; i<pDecShellContext->mMVAParamSrc.ownNum; i++)
        {
            #if 0
            pDecShellContext->mI420ColorConverter->freeVideoAllocMVA(pDecShellContext->mI420ColorConverter, 
                pDecShellContext->mMVAParamSrc.mkM4UBufferHandle, pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferVaA, 
                pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferPaB, pDecShellContext->mMVAParamSrc.mM4UBufferSize, NULL );
            #endif
            ALOGD("Free srcYUVbuf V: %p, P: %p", pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferVaA, pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferPaB);
            pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferVaA = 0xffffffff;
            pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferPaB = 0xffffffff;
        }
        #endif
        ALOGV("mMVAParamDst ownNum %d", pDecShellContext->mMVAParamDst.ownNum);
        for(int i=0; i<pDecShellContext->mMVAParamDst.ownNum; i++)
        {
            pDecShellContext->mI420ColorConverter->freeVideoAllocMVA(pDecShellContext->mI420ColorConverter,
                pDecShellContext->mMVAParamDst.mkM4UBufferHandle, pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferVaA,
                pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferPaB, pDecShellContext->mMVAParamDst.mM4UBufferSize, NULL);

            ALOGD("Free dstYUVbuf V: %p, P: %p", pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferVaA, pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferPaB);
            pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferVaA = 0xffffffff;
            pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferPaB = 0xffffffff;
        }

        pDecShellContext->mI420ColorConverter->deinitACodecColorConverter(pDecShellContext->mI420ColorConverter, pDecShellContext->mMVAParamSrc.mkM4UBufferHandle);
        pDecShellContext->mI420ColorConverter->deinitACodecColorConverter(pDecShellContext->mI420ColorConverter, pDecShellContext->mMVAParamDst.mkM4UBufferHandle);
#endif //VE_MVA_MODE
#endif //USE_VIDEO_ION

        // Release the color converter
        delete pDecShellContext->mI420ColorConverter;
        pDecShellContext->mI420ColorConverter = NULL;
    }
#else
    // Release the color converter
    delete pDecShellContext->mI420ColorConverter;
#endif //ANDROID_DEFAULT_CODE

    // Destroy the graph
    if( pDecShellContext->mVideoDecoder != NULL ) {
        ALOGV("### VideoEditorVideoDecoder_destroy : releasing decoder");
        pDecShellContext->mVideoDecoder->stop();
        pDecShellContext->mVideoDecoder.clear();
    }
    pDecShellContext->mClient.disconnect();
    pDecShellContext->mReaderSource.clear();

    // Release memory
    if( pDecShellContext->m_pDecBufferPool != M4OSA_NULL ) {
#ifndef ANDROID_DEFAULT_CODE
#ifdef USE_VIDEO_ION
        VideoEditorVideoDecoder_freePoolION(pDecShellContext, pDecShellContext->m_pDecBufferPool);
#else  //USE_VIDEO_ION
        VIDEOEDITOR_BUFFER_freePool(pDecShellContext->m_pDecBufferPool);
#endif //USE_VIDEO_ION
#else //ANDROID_DEFAULT_CODE
        VIDEOEDITOR_BUFFER_freePool(pDecShellContext->m_pDecBufferPool);
#endif //ANDROID_DEFAULT_CODE
        pDecShellContext->m_pDecBufferPool = M4OSA_NULL;
    }
    SAFE_FREE(pDecShellContext);
    pContext = NULL;

cleanUp:
    if( M4NO_ERROR == err ) {
        ALOGV("VideoEditorVideoDecoder_destroy no error");
    } else {
        ALOGE("VideoEditorVideoDecoder_destroy ERROR 0x%X", err);
    }
    ALOGV("VideoEditorVideoDecoder_destroy end");
    return err;
}

M4OSA_ERR VideoEditorVideoDecoder_create(M4OSA_Context *pContext,
        M4_StreamHandler *pStreamHandler,
        M4READER_GlobalInterface *pReaderGlobalInterface,
        M4READER_DataInterface *pReaderDataInterface,
        M4_AccessUnit *pAccessUnit, M4OSA_Void *pUserData) {
    M4OSA_ERR err = M4NO_ERROR;
    VideoEditorVideoDecoder_Context* pDecShellContext = M4OSA_NULL;
    status_t status = OK;
    bool success = TRUE;
    int32_t colorFormat = 0;
    M4OSA_UInt32 size = 0;
    sp<MetaData> decoderMetadata = NULL;
    int decoderOutput = OMX_COLOR_FormatYUV420Planar;
#ifndef ANDROID_DEFAULT_CODE
    uint32_t codecFlags = 0;
#endif //ANDROID_DEFAULT_CODE

    ALOGV("VideoEditorVideoDecoder_create begin");
    // Input parameters check
    VIDEOEDITOR_CHECK(M4OSA_NULL != pContext,             M4ERR_PARAMETER);
    VIDEOEDITOR_CHECK(M4OSA_NULL != pStreamHandler,       M4ERR_PARAMETER);
    VIDEOEDITOR_CHECK(M4OSA_NULL != pReaderDataInterface, M4ERR_PARAMETER);

    // Context allocation & initialization
    SAFE_MALLOC(pDecShellContext, VideoEditorVideoDecoder_Context, 1,
        "VideoEditorVideoDecoder");
    pDecShellContext->m_pVideoStreamhandler =
        (M4_VideoStreamHandler*)pStreamHandler;
    pDecShellContext->m_pNextAccessUnitToDecode = pAccessUnit;
    pDecShellContext->m_pReaderGlobal = pReaderGlobalInterface;
    pDecShellContext->m_pReader = pReaderDataInterface;
    pDecShellContext->m_lastDecodedCTS = -1;
    pDecShellContext->m_lastRenderCts = -1;
#ifndef ANDROID_DEFAULT_CODE
    pDecShellContext->mAllocatedMVABUffer = false;
#endif //ANDROID_DEFAULT_CODE

    switch( pStreamHandler->m_streamType ) {
        case M4DA_StreamTypeVideoH263:
            pDecShellContext->mDecoderType = VIDEOEDITOR_kH263VideoDec;
            break;
        case M4DA_StreamTypeVideoMpeg4:
            pDecShellContext->mDecoderType = VIDEOEDITOR_kMpeg4VideoDec;
            // Parse the VOL header
            err = VideoEditorVideoDecoder_internalParseVideoDSI(
                (M4OSA_UInt8*)pDecShellContext->m_pVideoStreamhandler->\
                    m_basicProperties.m_pDecoderSpecificInfo,
                pDecShellContext->m_pVideoStreamhandler->\
                    m_basicProperties.m_decoderSpecificInfoSize,
                &pDecShellContext->m_Dci, &pDecShellContext->m_VideoSize);
            VIDEOEDITOR_CHECK(M4NO_ERROR == err, err);
            break;
        case M4DA_StreamTypeVideoMpeg4Avc:
            pDecShellContext->mDecoderType = VIDEOEDITOR_kH264VideoDec;
            break;
        default:
            VIDEOEDITOR_CHECK(!"VideoDecoder_create : incorrect stream type",
                M4ERR_PARAMETER);
            break;
    }

    pDecShellContext->mNbInputFrames     = 0;
    pDecShellContext->mFirstInputCts     = -1.0;
    pDecShellContext->mLastInputCts      = -1.0;
    pDecShellContext->mNbRenderedFrames  = 0;
    pDecShellContext->mFirstRenderedCts  = -1.0;
    pDecShellContext->mLastRenderedCts   = -1.0;
    pDecShellContext->mNbOutputFrames    = 0;
    pDecShellContext->mFirstOutputCts    = -1;
    pDecShellContext->mLastOutputCts     = -1;
    pDecShellContext->m_pDecBufferPool   = M4OSA_NULL;

    // Calculate the interval between two video frames.
    CHECK(pDecShellContext->m_pVideoStreamhandler->m_averageFrameRate > 0);
    pDecShellContext->mFrameIntervalMs =
            1000.0 / pDecShellContext->m_pVideoStreamhandler->m_averageFrameRate;

    /**
     * StageFright graph building
     */
    decoderMetadata = new MetaData;
    switch( pDecShellContext->mDecoderType ) {
        case VIDEOEDITOR_kH263VideoDec:
            decoderMetadata->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_H263);
            break;
        case VIDEOEDITOR_kMpeg4VideoDec:
            decoderMetadata->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MPEG4);
            decoderMetadata->setData(kKeyESDS, kTypeESDS,
                pStreamHandler->m_pESDSInfo,
                pStreamHandler->m_ESDSInfoSize);
            break;
        case VIDEOEDITOR_kH264VideoDec:
            decoderMetadata->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_AVC);
            decoderMetadata->setData(kKeyAVCC, kTypeAVCC,
                pStreamHandler->m_pH264DecoderSpecificInfo,
                pStreamHandler->m_H264decoderSpecificInfoSize);
            break;
        default:
            VIDEOEDITOR_CHECK(!"VideoDecoder_create : incorrect stream type",
                M4ERR_PARAMETER);
            break;
    }

    decoderMetadata->setInt32(kKeyMaxInputSize, pStreamHandler->m_maxAUSize);
    decoderMetadata->setInt32(kKeyWidth,
        pDecShellContext->m_pVideoStreamhandler->m_videoWidth);
    decoderMetadata->setInt32(kKeyHeight,
        pDecShellContext->m_pVideoStreamhandler->m_videoHeight);

    // Create the decoder source
    pDecShellContext->mReaderSource = new VideoEditorVideoDecoderSource(
        decoderMetadata, pDecShellContext->mDecoderType,
        (void *)pDecShellContext);
    VIDEOEDITOR_CHECK(NULL != pDecShellContext->mReaderSource.get(),
        M4ERR_SF_DECODER_RSRC_FAIL);

    // Connect to the OMX client
    status = pDecShellContext->mClient.connect();
    VIDEOEDITOR_CHECK(OK == status, M4ERR_SF_DECODER_RSRC_FAIL);

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_ENABLE_VIDEO_EDITOR
#if VE_MVA_MODE
    codecFlags = OMXCodec::kVideoUseOMXMVAForVE;
#else //VE_MVA_MODE
    codecFlags = 0;
#endif //VE_MVA_MODE
#else //MTK_ENABLE_VIDEO_EDITOR
    codecFlags = 0;
#endif //MTK_ENABLE_VIDEO_EDITOR
    // Create the decoder
    pDecShellContext->mVideoDecoder = OMXCodec::Create(
        pDecShellContext->mClient.interface(),
        decoderMetadata, false, pDecShellContext->mReaderSource, NULL, codecFlags);
#else //ANDROID_DEFAULT_CODE
    // Create the decoder
    pDecShellContext->mVideoDecoder = OMXCodec::Create(
        pDecShellContext->mClient.interface(),
        decoderMetadata, false, pDecShellContext->mReaderSource);
#endif
    VIDEOEDITOR_CHECK(NULL != pDecShellContext->mVideoDecoder.get(),
        M4ERR_SF_DECODER_RSRC_FAIL);


    // Get the output color format
    success = pDecShellContext->mVideoDecoder->getFormat()->findInt32(
        kKeyColorFormat, &colorFormat);
    VIDEOEDITOR_CHECK(TRUE == success, M4ERR_PARAMETER);
    pDecShellContext->decOuputColorFormat = (OMX_COLOR_FORMATTYPE)colorFormat;

    pDecShellContext->mVideoDecoder->getFormat()->setInt32(kKeyWidth,
        pDecShellContext->m_pVideoStreamhandler->m_videoWidth);
    pDecShellContext->mVideoDecoder->getFormat()->setInt32(kKeyHeight,
        pDecShellContext->m_pVideoStreamhandler->m_videoHeight);

    // Get the color converter
    pDecShellContext->mI420ColorConverter = new I420ColorConverter;
    if (pDecShellContext->mI420ColorConverter->isLoaded()) {
        decoderOutput = pDecShellContext->mI420ColorConverter->getDecoderOutputFormat();
    }

    if (decoderOutput == OMX_COLOR_FormatYUV420Planar) {
        delete pDecShellContext->mI420ColorConverter;
        pDecShellContext->mI420ColorConverter = NULL;
    }

    ALOGI("decoder output format = 0x%X, pDecShellContext->decOuputColorFormat = 0x%X", decoderOutput, pDecShellContext->decOuputColorFormat);

#ifdef USE_VIDEO_ION
    pDecShellContext->mIonDevFd = -1;
    for (int i = 0 ; i < MAX_ION_BUF_COUNT ; i++) {
        pDecShellContext->mIonBufInfo[i].IonFd = 0xFFFFFFFF;
        pDecShellContext->mIonBufInfo[i].u4BufSize = 0xFFFFFFFF;
        pDecShellContext->mIonBufInfo[i].u4BufVA = 0xFFFFFFFF;
        pDecShellContext->mIonBufInfo[i].u4IonBufHandle = 0xFFFFFFFF;
    }
    if (-1 == pDecShellContext->mIonDevFd) {
        pDecShellContext->mIonDevFd = ion_open();
        if (pDecShellContext->mIonDevFd < 0) {
            ALOGE ("[ERROR] cannot open ION device.");
            return UNKNOWN_ERROR;
         }
    }
#endif //USE_VIDEO_ION

    // Configure the buffer pool from the metadata
    err = VideoEditorVideoDecoder_configureFromMetadata(pDecShellContext,
        pDecShellContext->mVideoDecoder->getFormat().get());
    VIDEOEDITOR_CHECK(M4NO_ERROR == err, err);

    // Start the graph
    status = pDecShellContext->mVideoDecoder->start();
    VIDEOEDITOR_CHECK(OK == status, M4ERR_SF_DECODER_RSRC_FAIL);

    *pContext = (M4OSA_Context)pDecShellContext;

#ifndef ANDROID_DEFAULT_CODE
#if VE_MVA_MODE
    if( false == pDecShellContext->mAllocatedMVABUffer )
    {
        enum BufferStatus {
            OWNED_BY_US,
            OWNED_BY_COMPONENT,
            OWNED_BY_NATIVE_WINDOW,
            OWNED_BY_CLIENT,
        };
        //referenc from omxcodec.h
        struct BufferInfo {
            IOMX::buffer_id mBuffer;
            BufferStatus mStatus;
            sp<IMemory> mMem;
            size_t mSize;
            void *mData;
            MediaBuffer *mMediaBuffer;
        };

        //int ownNum = 0;
        BufferInfo *info = NULL;
        uint32_t mVWidth = pDecShellContext->m_pVideoStreamhandler->m_videoWidth;
        uint32_t mVHeight = pDecShellContext->m_pVideoStreamhandler->m_videoHeight;

        pDecShellContext->mAllocatedMVABUffer = true;
        pDecShellContext->mMVAParamDst.mkM4UBufferHandle = NULL;
        pDecShellContext->mMVAParamSrc.mkM4UBufferHandle = NULL;
        for (pDecShellContext->mMVAParamSrc.mM4UBufferCount = 0; pDecShellContext->mMVAParamSrc.mM4UBufferCount < VIDEOEDITOR_VIDEC_SHELL_MAX_MVA_BUFFER_CNT; pDecShellContext->mMVAParamSrc.mM4UBufferCount++)
        {
            pDecShellContext->mMVAParamSrc.mM4UBuffers[pDecShellContext->mMVAParamSrc.mM4UBufferCount].mM4UBufferVaA = 0xffffffff; 
            pDecShellContext->mMVAParamSrc.mM4UBuffers[pDecShellContext->mMVAParamSrc.mM4UBufferCount].mM4UBufferPaB = 0xffffffff;
        }
        pDecShellContext->mMVAParamSrc.mInputBufferPopulatedCnt = 0xffffffff;
        pDecShellContext->mMVAParamSrc.mOutputBufferPopulatedCnt = 0xffffffff;
        pDecShellContext->mMVAParamSrc.mM4UBufferSize = ROUND_16(mVWidth) * ROUND_32(mVHeight) * 3 >> 1;

        for (pDecShellContext->mMVAParamDst.mM4UBufferCount = 0; pDecShellContext->mMVAParamDst.mM4UBufferCount < VIDEOEDITOR_VIDEC_SHELL_MAX_MVA_BUFFER_CNT; pDecShellContext->mMVAParamDst.mM4UBufferCount++)
        {
            pDecShellContext->mMVAParamDst.mM4UBuffers[pDecShellContext->mMVAParamDst.mM4UBufferCount].mM4UBufferVaA = 0xffffffff; 
            pDecShellContext->mMVAParamDst.mM4UBuffers[pDecShellContext->mMVAParamDst.mM4UBufferCount].mM4UBufferPaB = 0xffffffff;
        }
        pDecShellContext->mMVAParamDst.mInputBufferPopulatedCnt = 0xffffffff;
        pDecShellContext->mMVAParamDst.mOutputBufferPopulatedCnt = 0xffffffff;
#ifdef USE_VIDEO_ION
        pDecShellContext->mMVAParamDst.mM4UBufferSize = pDecShellContext->mIonBufInfo[0].u4BufSize;
#else  //USE_VIDEO_ION
        pDecShellContext->mMVAParamDst.mM4UBufferSize = ROUND_16(mVWidth) * ROUND_16(mVHeight) * 3 >> 1;
#endif //USE_VIDEO_ION
        if( pDecShellContext->mI420ColorConverter )
        {
#ifdef USE_VIDEO_ION
            pDecShellContext->mI420ColorConverter->mMtkColorConvertInfo->mEnableION = TRUE;
            pDecShellContext->mI420ColorConverter->mMtkColorConvertInfo->mDstFormat = eYUV_420_3P;
#else  //USE_VIDEO_ION
            pDecShellContext->mI420ColorConverter->mMtkColorConvertInfo->mEnableION = FALSE;
            pDecShellContext->mI420ColorConverter->mMtkColorConvertInfo->mDstFormat = eYUV_420_3P;
#endif //USE_VIDEO_ION

            int mUseMVA = 1;
            pDecShellContext->mI420ColorConverter->initACodecColorConverter(pDecShellContext->mI420ColorConverter, 
                (void**)&pDecShellContext->mMVAParamSrc.mkM4UBufferHandle, mUseMVA);
            pDecShellContext->mI420ColorConverter->initACodecColorConverter(pDecShellContext->mI420ColorConverter, 
                (void**)&pDecShellContext->mMVAParamDst.mkM4UBufferHandle, mUseMVA);
        #if 0
            pDecShellContext->mMVAParamSrc.ownNum = ((OMXCodec*)pDecShellContext->mVideoDecoder.get())->buffersOwn();
            ALOGI("mMVAParamSrc ownNum %d, mVWidth %d, mVHeight %d, src_size %d, dst_size %d ", pDecShellContext->mMVAParamSrc.ownNum, mVWidth, mVHeight,
                pDecShellContext->mMVAParamSrc.mM4UBufferSize, pDecShellContext->mMVAParamDst.mM4UBufferSize);

            for(int i=0; i<pDecShellContext->mMVAParamSrc.ownNum; i++)
            {
                info = (BufferInfo *)((OMXCodec*)pDecShellContext->mVideoDecoder.get())->findInputBufferByDataNumber(1, i);// 1 for outputport
                //ALOGI("ownNum %d, mSize %d, mBufferData %p", i, info->mSize, info->mMediaBuffer->data());
                pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferVaA = (unsigned long)info->mMediaBuffer->data();
                pDecShellContext->mMVAParamSrc.mM4UBufferSize = info->mSize;
            #if 0
                if ( (NULL!=pDecShellContext->mMVAParamSrc.mkM4UBufferHandle) && 
                    (0xffffffff == pDecShellContext->mI420ColorConverter->getVideoAllocMVA(pDecShellContext->mI420ColorConverter, 
                    pDecShellContext->mMVAParamSrc.mkM4UBufferHandle, (unsigned long)pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferVaA, 
                    (unsigned long *)&pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferPaB, pDecShellContext->mMVAParamSrc.mM4UBufferSize, NULL)) )
                {
                    ALOGE("[ERROR1][M4U][eVideoAllocMVA]");
                    //some platform can work without support MVA mode
                    //return M4WAR_BUFFER_FULL;
                }
            #endif
                ALOGI("srcYUVbuf V: %p, P: %p, S: %d", pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferVaA, 
                    pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferPaB, pDecShellContext->mMVAParamSrc.mM4UBufferSize);
            }
        #endif
            pDecShellContext->mMVAParamDst.ownNum = MAX_DEC_BUFFERS;
            for(int i=0; i<pDecShellContext->mMVAParamDst.ownNum; i++)
            {
                pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferVaA = (unsigned long)pDecShellContext->m_pDecBufferPool->pNXPBuffer[i].pData;
                //pDecShellContext->mMVAParamDst.mM4UBufferSize = (unsigned long)pDecShellContext->m_pDecBufferPool->pNXPBuffer[i].size;

                if ( (NULL!=pDecShellContext->mMVAParamDst.mkM4UBufferHandle) && 
#ifdef USE_VIDEO_ION
                    (0xffffffff == pDecShellContext->mI420ColorConverter->getVideoAllocMVA(pDecShellContext->mI420ColorConverter, 
                    (struct ion_handle *)pDecShellContext->mIonBufInfo[i].u4IonBufHandle, (unsigned long)pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferVaA, 
                    (unsigned long *)&pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferPaB, pDecShellContext->mMVAParamDst.mM4UBufferSize, 
                    (void *)&pDecShellContext->mIonDevFd)) 
#else  //USE_VIDEO_ION
                    (0xffffffff == pDecShellContext->mI420ColorConverter->getVideoAllocMVA(pDecShellContext->mI420ColorConverter, 
                    pDecShellContext->mMVAParamDst.mkM4UBufferHandle, (unsigned long)pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferVaA, 
                    (unsigned long *)&pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferPaB, pDecShellContext->mMVAParamDst.mM4UBufferSize, NULL)) 
#endif //USE_VIDEO_ION
                )
                {
                    ALOGE("[ERROR1][M4U][eVideoAllocMVA]");
                    //some platform can work without support MVA mode
                    //return M4WAR_BUFFER_FULL;
                }

                ALOGI("dstYUVbuf V: %p, P: %p, S: %d", pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferVaA, 
                    pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferPaB, pDecShellContext->mMVAParamDst.mM4UBufferSize);
            }
        }
    }
#endif //VE_MVA_MODE
#endif //ANDROID_DEFAULT_CODE

cleanUp:
    if( M4NO_ERROR == err ) {
        ALOGV("VideoEditorVideoDecoder_create no error");
    } else {
        VideoEditorVideoDecoder_destroy(pDecShellContext);
        *pContext = M4OSA_NULL;
        ALOGE("VideoEditorVideoDecoder_create ERROR 0x%X", err);
    }
    ALOGV("VideoEditorVideoDecoder_create : DONE");
    return err;
}

M4OSA_ERR VideoEditorVideoSoftwareDecoder_create(M4OSA_Context *pContext,
        M4_StreamHandler *pStreamHandler,
        M4READER_GlobalInterface *pReaderGlobalInterface,
        M4READER_DataInterface *pReaderDataInterface,
        M4_AccessUnit *pAccessUnit, M4OSA_Void *pUserData) {
    M4OSA_ERR err = M4NO_ERROR;
    VideoEditorVideoDecoder_Context* pDecShellContext = M4OSA_NULL;
    status_t status = OK;
    bool success = TRUE;
    int32_t colorFormat = 0;
    M4OSA_UInt32 size = 0;
    sp<MetaData> decoderMetadata = NULL;

    ALOGV("VideoEditorVideoDecoder_create begin");
    // Input parameters check
    VIDEOEDITOR_CHECK(M4OSA_NULL != pContext,             M4ERR_PARAMETER);
    VIDEOEDITOR_CHECK(M4OSA_NULL != pStreamHandler,       M4ERR_PARAMETER);
    VIDEOEDITOR_CHECK(M4OSA_NULL != pReaderDataInterface, M4ERR_PARAMETER);

    // Context allocation & initialization
    SAFE_MALLOC(pDecShellContext, VideoEditorVideoDecoder_Context, 1,
        "VideoEditorVideoDecoder");
    pDecShellContext->m_pVideoStreamhandler =
        (M4_VideoStreamHandler*)pStreamHandler;
    pDecShellContext->m_pNextAccessUnitToDecode = pAccessUnit;
    pDecShellContext->m_pReaderGlobal = pReaderGlobalInterface;
    pDecShellContext->m_pReader = pReaderDataInterface;
    pDecShellContext->m_lastDecodedCTS = -1;
    pDecShellContext->m_lastRenderCts = -1;
    switch( pStreamHandler->m_streamType ) {
        case M4DA_StreamTypeVideoH263:
            pDecShellContext->mDecoderType = VIDEOEDITOR_kH263VideoDec;
            break;
        case M4DA_StreamTypeVideoMpeg4:
            pDecShellContext->mDecoderType = VIDEOEDITOR_kMpeg4VideoDec;
            // Parse the VOL header
            err = VideoEditorVideoDecoder_internalParseVideoDSI(
                (M4OSA_UInt8*)pDecShellContext->m_pVideoStreamhandler->\
                    m_basicProperties.m_pDecoderSpecificInfo,
                pDecShellContext->m_pVideoStreamhandler->\
                    m_basicProperties.m_decoderSpecificInfoSize,
                &pDecShellContext->m_Dci, &pDecShellContext->m_VideoSize);
            VIDEOEDITOR_CHECK(M4NO_ERROR == err, err);
            break;
        case M4DA_StreamTypeVideoMpeg4Avc:
            pDecShellContext->mDecoderType = VIDEOEDITOR_kH264VideoDec;
            break;
        default:
            VIDEOEDITOR_CHECK(!"VideoDecoder_create : incorrect stream type",
                M4ERR_PARAMETER);
            break;
    }

    pDecShellContext->mNbInputFrames     = 0;
    pDecShellContext->mFirstInputCts     = -1.0;
    pDecShellContext->mLastInputCts      = -1.0;
    pDecShellContext->mNbRenderedFrames  = 0;
    pDecShellContext->mFirstRenderedCts  = -1.0;
    pDecShellContext->mLastRenderedCts   = -1.0;
    pDecShellContext->mNbOutputFrames    = 0;
    pDecShellContext->mFirstOutputCts    = -1;
    pDecShellContext->mLastOutputCts     = -1;
    pDecShellContext->m_pDecBufferPool   = M4OSA_NULL;

    /**
     * StageFright graph building
     */
    decoderMetadata = new MetaData;
    switch( pDecShellContext->mDecoderType ) {
        case VIDEOEDITOR_kH263VideoDec:
            decoderMetadata->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_H263);
            break;
        case VIDEOEDITOR_kMpeg4VideoDec:
            decoderMetadata->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MPEG4);
            decoderMetadata->setData(kKeyESDS, kTypeESDS,
                pStreamHandler->m_pESDSInfo,
                pStreamHandler->m_ESDSInfoSize);
            break;
        case VIDEOEDITOR_kH264VideoDec:
            decoderMetadata->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_AVC);
            decoderMetadata->setData(kKeyAVCC, kTypeAVCC,
                pStreamHandler->m_pH264DecoderSpecificInfo,
                pStreamHandler->m_H264decoderSpecificInfoSize);
            break;
        default:
            VIDEOEDITOR_CHECK(!"VideoDecoder_create : incorrect stream type",
                M4ERR_PARAMETER);
            break;
    }

    decoderMetadata->setInt32(kKeyMaxInputSize, pStreamHandler->m_maxAUSize);
    decoderMetadata->setInt32(kKeyWidth,
        pDecShellContext->m_pVideoStreamhandler->m_videoWidth);
    decoderMetadata->setInt32(kKeyHeight,
        pDecShellContext->m_pVideoStreamhandler->m_videoHeight);

    // Create the decoder source
    pDecShellContext->mReaderSource = new VideoEditorVideoDecoderSource(
        decoderMetadata, pDecShellContext->mDecoderType,
        (void *)pDecShellContext);
    VIDEOEDITOR_CHECK(NULL != pDecShellContext->mReaderSource.get(),
        M4ERR_SF_DECODER_RSRC_FAIL);

    // Connect to the OMX client
    status = pDecShellContext->mClient.connect();
    VIDEOEDITOR_CHECK(OK == status, M4ERR_SF_DECODER_RSRC_FAIL);

     ALOGI("Using software codecs only");
    // Create the decoder
    pDecShellContext->mVideoDecoder = OMXCodec::Create(
        pDecShellContext->mClient.interface(),
        decoderMetadata, false, pDecShellContext->mReaderSource,NULL,OMXCodec::kSoftwareCodecsOnly);
    VIDEOEDITOR_CHECK(NULL != pDecShellContext->mVideoDecoder.get(),
        M4ERR_SF_DECODER_RSRC_FAIL);

    // Get the output color format
    success = pDecShellContext->mVideoDecoder->getFormat()->findInt32(
        kKeyColorFormat, &colorFormat);
    VIDEOEDITOR_CHECK(TRUE == success, M4ERR_PARAMETER);
    pDecShellContext->decOuputColorFormat = (OMX_COLOR_FORMATTYPE)colorFormat;

    pDecShellContext->mVideoDecoder->getFormat()->setInt32(kKeyWidth,
        pDecShellContext->m_pVideoStreamhandler->m_videoWidth);
    pDecShellContext->mVideoDecoder->getFormat()->setInt32(kKeyHeight,
        pDecShellContext->m_pVideoStreamhandler->m_videoHeight);

    // Configure the buffer pool from the metadata
    err = VideoEditorVideoDecoder_configureFromMetadata(pDecShellContext,
        pDecShellContext->mVideoDecoder->getFormat().get());
    VIDEOEDITOR_CHECK(M4NO_ERROR == err, err);

    // Start the graph
    status = pDecShellContext->mVideoDecoder->start();
    VIDEOEDITOR_CHECK(OK == status, M4ERR_SF_DECODER_RSRC_FAIL);

    *pContext = (M4OSA_Context)pDecShellContext;

cleanUp:
    if( M4NO_ERROR == err ) {
        ALOGV("VideoEditorVideoDecoder_create no error");
    } else {
        VideoEditorVideoDecoder_destroy(pDecShellContext);
        *pContext = M4OSA_NULL;
        ALOGE("VideoEditorVideoDecoder_create ERROR 0x%X", err);
    }
    ALOGV("VideoEditorVideoDecoder_create : DONE");
    return err;
}


M4OSA_ERR VideoEditorVideoDecoder_getOption(M4OSA_Context context,
        M4OSA_OptionID optionId, M4OSA_DataOption pValue) {
    M4OSA_ERR lerr = M4NO_ERROR;
    VideoEditorVideoDecoder_Context* pDecShellContext =
        (VideoEditorVideoDecoder_Context*) context;
    M4_VersionInfo* pVersionInfo;
    M4DECODER_VideoSize* pVideoSize;
    M4OSA_UInt32* pNextFrameCts;
    M4OSA_UInt32 *plastDecodedFrameCts;
    M4DECODER_AVCProfileLevel* profile;
    M4DECODER_MPEG4_DecoderConfigInfo* pDecConfInfo;

    ALOGV("VideoEditorVideoDecoder_getOption begin %x", optionId);

    switch (optionId) {
        case M4DECODER_kOptionID_AVCLastDecodedFrameCTS:
             plastDecodedFrameCts = (M4OSA_UInt32 *) pValue;
             *plastDecodedFrameCts = pDecShellContext->m_lastDecodedCTS;
             break;

        case M4DECODER_kOptionID_Version:
            pVersionInfo = (M4_VersionInfo*)pValue;

            pVersionInfo->m_major = VIDEOEDITOR_VIDEC_SHELL_VER_MAJOR;
            pVersionInfo->m_minor= VIDEOEDITOR_VIDEC_SHELL_VER_MINOR;
            pVersionInfo->m_revision = VIDEOEDITOR_VIDEC_SHELL_VER_REVISION;
            pVersionInfo->m_structSize=sizeof(M4_VersionInfo);
            break;

        case M4DECODER_kOptionID_VideoSize:
            /** Only VPS uses this Option ID. */
            pVideoSize = (M4DECODER_VideoSize*)pValue;
            pDecShellContext->mVideoDecoder->getFormat()->findInt32(kKeyWidth,
                (int32_t*)(&pVideoSize->m_uiWidth));
            pDecShellContext->mVideoDecoder->getFormat()->findInt32(kKeyHeight,
                (int32_t*)(&pVideoSize->m_uiHeight));
            ALOGV("VideoEditorVideoDecoder_getOption : W=%d H=%d",
                pVideoSize->m_uiWidth, pVideoSize->m_uiHeight);
            break;

        case M4DECODER_kOptionID_NextRenderedFrameCTS:
            /** How to get this information. SF decoder does not provide this. *
            ** Let us provide last decoded frame CTS as of now. *
            ** Only VPS uses this Option ID. */
            pNextFrameCts = (M4OSA_UInt32 *)pValue;
            *pNextFrameCts = pDecShellContext->m_lastDecodedCTS;
            break;
        case M4DECODER_MPEG4_kOptionID_DecoderConfigInfo:
            if(pDecShellContext->mDecoderType == VIDEOEDITOR_kMpeg4VideoDec) {
                (*(M4DECODER_MPEG4_DecoderConfigInfo*)pValue) =
                    pDecShellContext->m_Dci;
            }
            break;
        default:
            lerr = M4ERR_BAD_OPTION_ID;
            break;

    }

    ALOGV("VideoEditorVideoDecoder_getOption: end with err = 0x%x", lerr);
    return lerr;
}

M4OSA_ERR VideoEditorVideoDecoder_setOption(M4OSA_Context context,
        M4OSA_OptionID optionId, M4OSA_DataOption pValue) {
    M4OSA_ERR lerr = M4NO_ERROR;
    VideoEditorVideoDecoder_Context *pDecShellContext =
        (VideoEditorVideoDecoder_Context*) context;

    ALOGV("VideoEditorVideoDecoder_setOption begin %x", optionId);

    switch (optionId) {
        case M4DECODER_kOptionID_OutputFilter: {
                M4DECODER_OutputFilter* pOutputFilter =
                    (M4DECODER_OutputFilter*) pValue;
                pDecShellContext->m_pFilter =
                    (M4VIFI_PlanConverterFunctionType*)pOutputFilter->\
                    m_pFilterFunction;
                pDecShellContext->m_pFilterUserData =
                    pOutputFilter->m_pFilterUserData;
            }
            break;
        case M4DECODER_kOptionID_DeblockingFilter:
            break;

#ifndef ANDROID_DEFAULT_CODE
        /* ALPS01112948
           Wait until the decoder finishes reading the file source, avoiding the race
           condition in 3gp reader.
         */
        case M4DECODER_kOptionID_WaitReadingSource:

            if (pDecShellContext->mVideoDecoder != NULL ) {

                ALOGV("VideoEditorVideoDecoder_setOption: wait reading source, decoder 0x%X",
                    pDecShellContext->mVideoDecoder.get());

                M4OSA_UInt32 prevCount = 0;
                M4OSA_UInt32 currCount = 0;
                M4OSA_UInt32 deltaMs = 0;
                prevCount = ((OMXCodec*)pDecShellContext->mVideoDecoder.get())->getEmptyInputBufferCount();

                while (deltaMs < 1000) {
                    M4OSA_threadSleep(100); // Sleep 100 ms
                    currCount = ((OMXCodec*)pDecShellContext->mVideoDecoder.get())->getEmptyInputBufferCount();
                    if (currCount == prevCount) {
                        deltaMs += 100;
                    }
                    else {
                        prevCount = currCount;
                        deltaMs = 0;
                    }
                }

                ALOGV("VideoEditorVideoDecoder_setOption: prevCount 0x%X, currCount 0x%X, deltaMs 0x%X",
                    prevCount, currCount, deltaMs);
            }
            break;
#endif

        default:
            lerr = M4ERR_BAD_CONTEXT;
            break;
    }

    ALOGV("VideoEditorVideoDecoder_setOption: end with err = 0x%x", lerr);
    return lerr;
}

M4OSA_ERR VideoEditorVideoDecoder_decode(M4OSA_Context context,
        M4_MediaTime* pTime, M4OSA_Bool bJump, M4OSA_UInt32 tolerance) {
    M4OSA_ERR lerr = M4NO_ERROR;
    VideoEditorVideoDecoder_Context* pDecShellContext =
        (VideoEditorVideoDecoder_Context*) context;
    int64_t lFrameTime;
    MediaBuffer* pDecoderBuffer = NULL;
    MediaBuffer* pNextBuffer = NULL;
    status_t errStatus;
    bool needSeek = bJump;

    ALOGV("VideoEditorVideoDecoder_decode begin");

    if( M4OSA_TRUE == pDecShellContext->mReachedEOS ) {
        // Do not call read(), it could lead to a freeze
        ALOGV("VideoEditorVideoDecoder_decode : EOS already reached");
        lerr = M4WAR_NO_MORE_AU;
        goto VIDEOEDITOR_VideoDecode_cleanUP;
    }
    if(pDecShellContext->m_lastDecodedCTS >= *pTime) {
        ALOGV("VideoDecoder_decode: Already decoded up to this time CTS = %lf.",
            pDecShellContext->m_lastDecodedCTS);
        goto VIDEOEDITOR_VideoDecode_cleanUP;
    }
    if(M4OSA_TRUE == bJump) {
        ALOGV("VideoEditorVideoDecoder_decode: Jump called");
        pDecShellContext->m_lastDecodedCTS = -1;
        pDecShellContext->m_lastRenderCts = -1;
    }

    pDecShellContext->mNbInputFrames++;
    if (0 > pDecShellContext->mFirstInputCts){
        pDecShellContext->mFirstInputCts = *pTime;
    }
    pDecShellContext->mLastInputCts = *pTime;

    while (pDecoderBuffer == NULL || pDecShellContext->m_lastDecodedCTS + tolerance < *pTime) {
        ALOGV("VideoEditorVideoDecoder_decode, frameCTS = %lf, DecodeUpTo = %lf",
            pDecShellContext->m_lastDecodedCTS, *pTime);

        // Read the buffer from the stagefright decoder
        if (needSeek) {
            MediaSource::ReadOptions options;
            int64_t time_us = *pTime * 1000;
            options.setSeekTo(time_us, MediaSource::ReadOptions::SEEK_PREVIOUS_SYNC);
            errStatus = pDecShellContext->mVideoDecoder->read(&pNextBuffer, &options);
            needSeek = false;
        } else {
            errStatus = pDecShellContext->mVideoDecoder->read(&pNextBuffer);
        }

        // Handle EOS and format change
        if (errStatus == ERROR_END_OF_STREAM) {
            ALOGV("End of stream reached, returning M4WAR_NO_MORE_AU ");
            pDecShellContext->mReachedEOS = M4OSA_TRUE;
            lerr = M4WAR_NO_MORE_AU;
            // If we decoded a buffer before EOS, we still need to put it
            // into the queue.
            if (pDecoderBuffer && bJump) {
                copyBufferToQueue(pDecShellContext, pDecoderBuffer);
            }
            goto VIDEOEDITOR_VideoDecode_cleanUP;
        } else if (INFO_FORMAT_CHANGED == errStatus) {
            ALOGV("VideoDecoder_decode : source returns INFO_FORMAT_CHANGED");
            lerr = VideoEditorVideoDecoder_configureFromMetadata(
                pDecShellContext,
                pDecShellContext->mVideoDecoder->getFormat().get());
            if( M4NO_ERROR != lerr ) {
                ALOGV("!!! VideoEditorVideoDecoder_decode ERROR : "
                    "VideoDecoder_configureFromMetadata returns 0x%X", lerr);
                break;
            }
            continue;
        } else if (errStatus != OK) {
            ALOGE("VideoEditorVideoDecoder_decode ERROR:0x%x(%d)",
                errStatus,errStatus);
            lerr = errStatus;
            goto VIDEOEDITOR_VideoDecode_cleanUP;
        }

        // The OMXCodec client should expect to receive 0-length buffers
        // and drop the 0-length buffers.
        if (pNextBuffer->range_length() == 0) {
            pNextBuffer->release();
            continue;
        }

        // Now we have a good next buffer, release the previous one.
        if (pDecoderBuffer != NULL) {
            pDecoderBuffer->release();
            pDecoderBuffer = NULL;
        }
        pDecoderBuffer = pNextBuffer;

        // Record the timestamp of last decoded buffer
        pDecoderBuffer->meta_data()->findInt64(kKeyTime, &lFrameTime);
        pDecShellContext->m_lastDecodedCTS = (M4_MediaTime)(lFrameTime/1000);
        ALOGV("VideoEditorVideoDecoder_decode,decoded frametime = %lf,size = %d",
            (M4_MediaTime)lFrameTime, pDecoderBuffer->size() );

#ifndef ANDROID_DEFAULT_CODE
#if 0 // Morris Yang dump decoder output
        ALOGD ("@@ VideoEditorVideoDecoder_decode,decoded frametime = %lf, size = %d, bJump = %d", (M4_MediaTime)lFrameTime, pDecoderBuffer->size(), bJump);

        char buf[255];
        sprintf (buf, "/sdcard/out.yuv");
        FILE *fp = fopen(buf, "ab");
        if(fp)
        {
            fwrite((void *)pDecoderBuffer->data(), 1, pDecoderBuffer->range_length(), fp);
            fclose(fp);
        }
#endif
#endif

        /*
         * We need to save a buffer if bJump == false to a queue. These
         * buffers have a timestamp >= the target time, *pTime (for instance,
         * the transition between two videos, or a trimming postion inside
         * one video), since they are part of the transition clip or the
         * trimmed video.
         *
         * If *pTime does not have the same value as any of the existing
         * video frames, we would like to get the buffer right before *pTime
         * and in the transcoding phrase, this video frame will be encoded
         * as a key frame and becomes the first video frame for the transition or the
         * trimmed video to be generated. This buffer must also be queued.
         *
         */
        int64_t targetTimeMs =
                pDecShellContext->m_lastDecodedCTS +
                pDecShellContext->mFrameIntervalMs +
                tolerance;
        if (!bJump || targetTimeMs > *pTime) {
            lerr = copyBufferToQueue(pDecShellContext, pDecoderBuffer);
            if (lerr != M4NO_ERROR) {
                goto VIDEOEDITOR_VideoDecode_cleanUP;
            }
        }
    }

    pDecShellContext->mNbOutputFrames++;
    if ( 0 > pDecShellContext->mFirstOutputCts ) {
        pDecShellContext->mFirstOutputCts = *pTime;
    }
    pDecShellContext->mLastOutputCts = *pTime;

VIDEOEDITOR_VideoDecode_cleanUP:
    *pTime = pDecShellContext->m_lastDecodedCTS;
    if (pDecoderBuffer != NULL) {
        pDecoderBuffer->release();
        pDecoderBuffer = NULL;
    }

    ALOGV("VideoEditorVideoDecoder_decode: end with 0x%x", lerr);
    return lerr;
}

static M4OSA_ERR copyBufferToQueue(
    VideoEditorVideoDecoder_Context* pDecShellContext,
    MediaBuffer* pDecoderBuffer) {

    M4OSA_ERR lerr = M4NO_ERROR;
    VIDEOEDITOR_BUFFER_Buffer* tmpDecBuffer;

    // Get a buffer from the queue
    lerr = VIDEOEDITOR_BUFFER_getBuffer(pDecShellContext->m_pDecBufferPool,
        VIDEOEDITOR_BUFFER_kEmpty, &tmpDecBuffer);
    if (lerr == (M4OSA_UInt32)M4ERR_NO_BUFFER_AVAILABLE) {
        lerr = VIDEOEDITOR_BUFFER_getOldestBuffer(
            pDecShellContext->m_pDecBufferPool,
            VIDEOEDITOR_BUFFER_kFilled, &tmpDecBuffer);
        tmpDecBuffer->state = VIDEOEDITOR_BUFFER_kEmpty;
        lerr = M4NO_ERROR;
    }

    if (lerr != M4NO_ERROR) return lerr;

    // Color convert or copy from the given MediaBuffer to our buffer
    if (pDecShellContext->mI420ColorConverter) {
#ifndef ANDROID_DEFAULT_CODE
#if VE_MVA_MODE
        int match_index = 0;
        int mSrcIdx = 0xff;
        int mDstIdx = 0xff;
        //MTK_PLATFORM_PRIVATE* mplatform_private = NULL;
        void *platformPrivatePa = NULL;
        void *platformPrivateVa = NULL;

        pDecoderBuffer->meta_data()->findPointer(kKeyVideoEditorVa, (void **)&platformPrivateVa);
        pDecoderBuffer->meta_data()->findPointer(kKeyVideoEditorPa, (void **)&platformPrivatePa);
        #if 0
        for(int i=0; i<pDecShellContext->mMVAParamSrc.ownNum ; i++)
        {
            if( pDecShellContext->mMVAParamSrc.mM4UBuffers[i].mM4UBufferVaA == (OMX_U32)pDecoderBuffer->data() )
            {
                mSrcIdx = i;
                match_index = 1;
                break;
            }
        }
        if(0==match_index)
            ALOGW("mapping MVA src fail that may cause some error");
        #else

        pDecShellContext->mI420ColorConverter->mMtkColorConvertInfo->mSrcFormat = pDecShellContext->decOuputColorFormat;//VE use YUV420

        if( (NULL != platformPrivateVa) && (NULL != platformPrivatePa) )
        {
            ALOGD("data %x, mM4UVABufferVa %x, mM4UVABufferPa %x", (OMX_U32)pDecoderBuffer->data(), platformPrivateVa, platformPrivatePa);
            mSrcIdx = 0;
            pDecShellContext->mMVAParamSrc.mM4UBuffers[mSrcIdx].mM4UBufferVaA = (unsigned long)platformPrivateVa;
            pDecShellContext->mMVAParamSrc.mM4UBuffers[mSrcIdx].mM4UBufferPaB = (unsigned long)platformPrivatePa;
        }
        else
            ALOGW("Error address mapping here");
        #endif
        match_index = 0;
        for(int i=0; i<pDecShellContext->mMVAParamDst.ownNum ; i++)
        {
            if( pDecShellContext->mMVAParamDst.mM4UBuffers[i].mM4UBufferVaA == (OMX_U32)tmpDecBuffer->pData )
            {
                mDstIdx = i;
                match_index = 1;
                break;
            }
        }
        if(0==match_index)
            ALOGW("mapping MVA dst fail that may cause some error");

        {
            uint32_t mColorFormat;
            switch (pDecShellContext->decOuputColorFormat) {
                case OMX_COLOR_FormatYUV420Planar:
                    //eHalColorFormat = HAL_PIXEL_FORMAT_YV12;
                    mColorFormat = eYUV_420_3P;
                    break;
                case OMX_MTK_COLOR_FormatYV12:
                    mColorFormat = eYUV_420_3P_YVU;
                    break;
                case OMX_COLOR_FormatVendorMTKYUV:
                    mColorFormat = eNV12_BLK;
                    break;
                default:
                    //eHalColorFormat = HAL_PIXEL_FORMAT_YV12;
                    mColorFormat = eYUV_420_3P;
                    break;           
            }
            pDecShellContext->mI420ColorConverter->mMtkColorConvertInfo->mSrcFormat = mColorFormat;//VE use YUV420
            ALOGD ("mColorFormat(%x)", mColorFormat);
        }
        
        //ALOGV("mapping MVA addr got idx src: %d, dst: %d ", mSrcIdx, mDstIdx);
        if( (0xFF == mSrcIdx) || (0xFF == mDstIdx))
        {
            ALOGE("mapping MVA addr failed");
            lerr = M4ERR_PARAMETER;
        }
        else if (pDecShellContext->mI420ColorConverter->secondConvertDecoderOutputToI420(pDecShellContext->mI420ColorConverter,
            (NULL==pDecShellContext->mMVAParamSrc.mkM4UBufferHandle)?(uint8_t *)pDecShellContext->mMVAParamSrc.mM4UBuffers[mSrcIdx].mM4UBufferVaA:
            (uint8_t *)&pDecShellContext->mMVAParamSrc.mM4UBuffers[mSrcIdx],// ?? + pDecoderBuffer->range_offset(),   // decoderBits
            pDecShellContext->mGivenWidth,  // decoderWidth
            pDecShellContext->mGivenHeight,  // decoderHeight
            pDecShellContext->mCropRect,  // decoderRect
            (uint8_t *)&pDecShellContext->mMVAParamDst.mM4UBuffers[mDstIdx] /* dstBits */) < 0) {
            ALOGE("convertDecoderOutputToI420 failed");
            lerr = M4ERR_NOT_IMPLEMENTED;
        }
#else //VE_MVA_MODE
        if (pDecShellContext->mI420ColorConverter->convertDecoderOutputToI420(
            (uint8_t *)pDecoderBuffer->data(),// ?? + pDecoderBuffer->range_offset(),   // decoderBits
            pDecShellContext->mGivenWidth,  // decoderWidth
            pDecShellContext->mGivenHeight,  // decoderHeight
            pDecShellContext->mCropRect,  // decoderRect
            tmpDecBuffer->pData /* dstBits */) < 0) {
            ALOGE("convertDecoderOutputToI420 failed");
            lerr = M4ERR_NOT_IMPLEMENTED;
        }
#endif //VE_MVA_MODE
#else //ANDROID_DEFAULT_CODE
        if (pDecShellContext->mI420ColorConverter->convertDecoderOutputToI420(
            (uint8_t *)pDecoderBuffer->data(),// ?? + pDecoderBuffer->range_offset(),   // decoderBits
            pDecShellContext->mGivenWidth,  // decoderWidth
            pDecShellContext->mGivenHeight,  // decoderHeight
            pDecShellContext->mCropRect,  // decoderRect
            tmpDecBuffer->pData /* dstBits */) < 0) {
            ALOGE("convertDecoderOutputToI420 failed");
            lerr = M4ERR_NOT_IMPLEMENTED;
        }
#endif //ANDROID_DEFAULT_CODE
    } else if (pDecShellContext->decOuputColorFormat == OMX_COLOR_FormatYUV420Planar) {
        int32_t width = pDecShellContext->m_pVideoStreamhandler->m_videoWidth;
        int32_t height = pDecShellContext->m_pVideoStreamhandler->m_videoHeight;
        int32_t yPlaneSize = width * height;
        int32_t uvPlaneSize = width * height / 4;
        int32_t offsetSrc = 0;

        if (( width == pDecShellContext->mGivenWidth )  &&
            ( height == pDecShellContext->mGivenHeight ))
        {
            M4OSA_MemAddr8 pTmpBuff = (M4OSA_MemAddr8)pDecoderBuffer->data() + pDecoderBuffer->range_offset();
#ifndef ANDROID_DEFAULT_CODE
            // Demon Deng, ... keep stride
            memcpy((void *)tmpDecBuffer->pData, (void *)pTmpBuff, pDecoderBuffer->range_length());
#else

            memcpy((void *)tmpDecBuffer->pData, (void *)pTmpBuff, yPlaneSize);

            offsetSrc += pDecShellContext->mGivenWidth * pDecShellContext->mGivenHeight;
            memcpy((void *)((M4OSA_MemAddr8)tmpDecBuffer->pData + yPlaneSize),
                (void *)(pTmpBuff + offsetSrc), uvPlaneSize);

            offsetSrc += (pDecShellContext->mGivenWidth >> 1) * (pDecShellContext->mGivenHeight >> 1);
            memcpy((void *)((M4OSA_MemAddr8)tmpDecBuffer->pData + yPlaneSize + uvPlaneSize),
                (void *)(pTmpBuff + offsetSrc), uvPlaneSize);
#endif
        }
        else
        {
            M4OSA_MemAddr8 pTmpBuff = (M4OSA_MemAddr8)pDecoderBuffer->data() + pDecoderBuffer->range_offset();
            M4OSA_MemAddr8 pTmpBuffDst = (M4OSA_MemAddr8)tmpDecBuffer->pData;
            int32_t index;

            for ( index = 0; index < height; index++)
            {
                memcpy((void *)pTmpBuffDst, (void *)pTmpBuff, width);
                pTmpBuffDst += width;
                pTmpBuff += pDecShellContext->mGivenWidth;
            }

            pTmpBuff += (pDecShellContext->mGivenWidth * ( pDecShellContext->mGivenHeight - height));
            for ( index = 0; index < height >> 1; index++)
            {
                memcpy((void *)pTmpBuffDst, (void *)pTmpBuff, width >> 1);
                pTmpBuffDst += width >> 1;
                pTmpBuff += pDecShellContext->mGivenWidth >> 1;
            }

            pTmpBuff += ((pDecShellContext->mGivenWidth * (pDecShellContext->mGivenHeight - height)) / 4);
            for ( index = 0; index < height >> 1; index++)
            {
                memcpy((void *)pTmpBuffDst, (void *)pTmpBuff, width >> 1);
                pTmpBuffDst += width >> 1;
                pTmpBuff += pDecShellContext->mGivenWidth >> 1;
            }
        }
    } else {
        ALOGE("VideoDecoder_decode: unexpected color format 0x%X",
            pDecShellContext->decOuputColorFormat);
        lerr = M4ERR_PARAMETER;
    }

    tmpDecBuffer->buffCTS = pDecShellContext->m_lastDecodedCTS;
    tmpDecBuffer->state = VIDEOEDITOR_BUFFER_kFilled;
    tmpDecBuffer->size = pDecoderBuffer->size();

    return lerr;
}

M4OSA_ERR VideoEditorVideoDecoder_render(M4OSA_Context context,
        M4_MediaTime* pTime, M4VIFI_ImagePlane* pOutputPlane,
        M4OSA_Bool bForceRender) {
    M4OSA_ERR err = M4NO_ERROR;
    VideoEditorVideoDecoder_Context* pDecShellContext =
        (VideoEditorVideoDecoder_Context*) context;
    M4OSA_UInt32 lindex, i;
    M4OSA_UInt8* p_buf_src, *p_buf_dest;
    M4VIFI_ImagePlane tmpPlaneIn, tmpPlaneOut;
    VIDEOEDITOR_BUFFER_Buffer* pTmpVIDEOEDITORBuffer, *pRenderVIDEOEDITORBuffer
                                                                  = M4OSA_NULL;
    M4_MediaTime candidateTimeStamp = -1;
    M4OSA_Bool bFound = M4OSA_FALSE;

    ALOGV("VideoEditorVideoDecoder_render begin");
    // Input parameters check
    VIDEOEDITOR_CHECK(M4OSA_NULL != context, M4ERR_PARAMETER);
    VIDEOEDITOR_CHECK(M4OSA_NULL != pTime, M4ERR_PARAMETER);
    VIDEOEDITOR_CHECK(M4OSA_NULL != pOutputPlane, M4ERR_PARAMETER);

    // The output buffer is already allocated, just copy the data
    if ( (*pTime <= pDecShellContext->m_lastRenderCts) &&
            (M4OSA_FALSE == bForceRender) ) {
        ALOGV("VIDEOEDITOR_VIDEO_render Frame in the past");
        err = M4WAR_VIDEORENDERER_NO_NEW_FRAME;
        goto cleanUp;
    }
    ALOGV("VideoDecoder_render: lastRendered time = %lf,requested render time = "
        "%lf", pDecShellContext->m_lastRenderCts, *pTime);

    /**
     * Find the buffer appropriate for rendering.  */
    for (i=0; i < pDecShellContext->m_pDecBufferPool->NB; i++) {
        pTmpVIDEOEDITORBuffer = &pDecShellContext->m_pDecBufferPool\
            ->pNXPBuffer[i];
        if (pTmpVIDEOEDITORBuffer->state == VIDEOEDITOR_BUFFER_kFilled) {
            /** Free all those buffers older than last rendered frame. */
            if (pTmpVIDEOEDITORBuffer->buffCTS < pDecShellContext->\
                    m_lastRenderCts) {
                pTmpVIDEOEDITORBuffer->state = VIDEOEDITOR_BUFFER_kEmpty;
            }

            /** Get the buffer with appropriate timestamp  */
            if ( (pTmpVIDEOEDITORBuffer->buffCTS >= pDecShellContext->\
                    m_lastRenderCts) &&
                (pTmpVIDEOEDITORBuffer->buffCTS <= *pTime) &&
                (pTmpVIDEOEDITORBuffer->buffCTS > candidateTimeStamp)) {
                bFound = M4OSA_TRUE;
                pRenderVIDEOEDITORBuffer = pTmpVIDEOEDITORBuffer;
                candidateTimeStamp = pTmpVIDEOEDITORBuffer->buffCTS;
                ALOGV("VideoDecoder_render: found a buffer with timestamp = %lf",
                    candidateTimeStamp);
#ifndef ANDROID_DEFAULT_CODE
#if 0 //Morris Yang dump Decoder output
        ALOGD("@@ VideoDecoder_render (0x%08X): found a buffer with timestamp = %lf, size=%d", pDecShellContext->mVideoDecoder.get(), candidateTimeStamp, pRenderVIDEOEDITORBuffer->size);
        char buf[255];
        sprintf (buf, "/sdcard/VE_DecDump_0x%08X.yuv", pDecShellContext->mVideoDecoder.get());
        FILE *fp = fopen(buf, "ab");
        if(fp)
        {
            fwrite((void *)pRenderVIDEOEDITORBuffer->pData, 1, pRenderVIDEOEDITORBuffer->size-16, fp);
            fclose(fp);
        }
#endif
#endif
            }
        }
    }
    if (M4OSA_FALSE == bFound) {
        err = M4WAR_VIDEORENDERER_NO_NEW_FRAME;
        goto cleanUp;
    }

    ALOGD("VideoEditorVideoDecoder_render 3 ouput %d %d %d %d,  %d %d",
        pOutputPlane[0].u_width, pOutputPlane[0].u_height,
        pOutputPlane[0].u_topleft, pOutputPlane[0].u_stride, pDecShellContext->m_pVideoStreamhandler->m_videoWidth, pDecShellContext->m_pVideoStreamhandler->m_videoHeight);

    pDecShellContext->m_lastRenderCts = candidateTimeStamp;

    if( M4OSA_NULL != pDecShellContext->m_pFilter ) {
        // Filtering was requested
        M4VIFI_ImagePlane tmpPlane[3];
        // Prepare the output image for conversion
        tmpPlane[0].u_width   =
            pDecShellContext->m_pVideoStreamhandler->m_videoWidth;
        tmpPlane[0].u_height  =
            pDecShellContext->m_pVideoStreamhandler->m_videoHeight;
        tmpPlane[0].u_topleft = 0;
#ifndef ANDROID_DEFAULT_CODE  // Morris Yang, for non 16x width video
        //color convert keep original size now, example 120x160 decoder will output 128x160, after color converter get 120x160
        //176x144 decoder will output 176x160 after color convert get 176x144
        int tmpHeight = (tmpPlane[0].u_height+0xF)&~0xF;
        if (pDecShellContext->mI420ColorConverter) 
        {
             tmpPlane[0].u_stride  = tmpPlane[0].u_width;
        }
        else
        {
            tmpPlane[0].u_stride  = (tmpPlane[0].u_width+0xF)&~0xF;
        }
#else
        tmpPlane[0].u_stride  = tmpPlane[0].u_width;
#endif
        tmpPlane[0].pac_data  = (M4VIFI_UInt8*)pRenderVIDEOEDITORBuffer->pData;
        tmpPlane[1].u_width   = tmpPlane[0].u_width/2;
        tmpPlane[1].u_height  = tmpPlane[0].u_height/2;
        tmpPlane[1].u_topleft = 0;
        tmpPlane[1].u_stride  = tmpPlane[0].u_stride/2;
#ifndef ANDROID_DEFAULT_CODE
        if (pDecShellContext->mI420ColorConverter)
            tmpPlane[1].pac_data  = tmpPlane[0].pac_data + (tmpPlane[0].u_stride * tmpPlane[0].u_height);
        else
            tmpPlane[1].pac_data  = tmpPlane[0].pac_data + (tmpPlane[0].u_stride * tmpHeight);
#else
        tmpPlane[1].pac_data  = tmpPlane[0].pac_data + (tmpPlane[0].u_stride * tmpPlane[0].u_height);
#endif
        tmpPlane[2].u_width   = tmpPlane[1].u_width;
        tmpPlane[2].u_height  = tmpPlane[1].u_height;
        tmpPlane[2].u_topleft = 0;
        tmpPlane[2].u_stride  = tmpPlane[1].u_stride;
#ifndef ANDROID_DEFAULT_CODE
        if (pDecShellContext->mI420ColorConverter)
            tmpPlane[2].pac_data  = tmpPlane[1].pac_data +(tmpPlane[1].u_stride * tmpPlane[1].u_height);
        else
            tmpPlane[2].pac_data  = tmpPlane[1].pac_data + (tmpPlane[1].u_stride * tmpHeight / 2);
#else
        tmpPlane[2].pac_data  = tmpPlane[1].pac_data + (tmpPlane[1].u_stride * tmpPlane[1].u_height);
#endif

        ALOGD("VideoEditorVideoDecoder_render w = %d H = %d",
            tmpPlane[0].u_width,tmpPlane[0].u_height);
        pDecShellContext->m_pFilter(M4OSA_NULL, &tmpPlane[0], pOutputPlane);
    } else {
        // Just copy the YUV420P buffer
        M4OSA_MemAddr8 tempBuffPtr =
            (M4OSA_MemAddr8)pRenderVIDEOEDITORBuffer->pData;
#ifndef ANDROID_DEFAULT_CODE
        M4OSA_UInt32 tempWidth =
            (pDecShellContext->m_pVideoStreamhandler->m_videoWidth + 0xf) & (~0xf);
        if (pDecShellContext->mI420ColorConverter) 
        {
             tempWidth  = pDecShellContext->m_pVideoStreamhandler->m_videoWidth;
        }
#else
        M4OSA_UInt32 tempWidth =
            pDecShellContext->m_pVideoStreamhandler->m_videoWidth;
#endif //ANDROID_DEFAULT_CODE
        M4OSA_UInt32 tempHeight =
            pDecShellContext->m_pVideoStreamhandler->m_videoHeight;

        memcpy((void *) pOutputPlane[0].pac_data, (void *)tempBuffPtr,
            tempWidth * tempHeight);
#ifndef ANDROID_DEFAULT_CODE
        M4OSA_UInt32 planeHeight = (tempHeight + 0xf) & ~0xf;
        if (pDecShellContext->mI420ColorConverter) 
        {   //keep original height value
            planeHeight = tempHeight;
        }
        tempBuffPtr += (tempWidth * planeHeight);
        memcpy((void *) pOutputPlane[1].pac_data, (void *)tempBuffPtr,
            (tempWidth/2) * (tempHeight/2));
        tempBuffPtr += ((tempWidth/2) * (planeHeight/2));
#else
        tempBuffPtr += (tempWidth * tempHeight);
        memcpy((void *) pOutputPlane[1].pac_data, (void *)tempBuffPtr,
            (tempWidth/2) * (tempHeight/2));
        tempBuffPtr += ((tempWidth/2) * (tempHeight/2));
#endif //ANDROID_DEFAULT_CODE
        memcpy((void *) pOutputPlane[2].pac_data, (void *)tempBuffPtr,
            (tempWidth/2) * (tempHeight/2));
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("copy YUV buffer w = %d H = %d",
            tempWidth, tempHeight);
#endif //ANDROID_DEFAULT_CODE
    }

    pDecShellContext->mNbRenderedFrames++;
    if ( 0 > pDecShellContext->mFirstRenderedCts ) {
        pDecShellContext->mFirstRenderedCts = *pTime;
    }
    pDecShellContext->mLastRenderedCts = *pTime;

cleanUp:
    if( M4NO_ERROR == err ) {
        *pTime = pDecShellContext->m_lastRenderCts;
        ALOGV("VideoEditorVideoDecoder_render no error");
    } else {
        ALOGE("VideoEditorVideoDecoder_render ERROR 0x%X", err);
    }
    ALOGV("VideoEditorVideoDecoder_render end");
    return err;
}

M4OSA_ERR VideoEditorVideoDecoder_getInterface(M4DECODER_VideoType decoderType,
        M4DECODER_VideoType *pDecoderType, M4OSA_Context *pDecInterface) {
    M4DECODER_VideoInterface* pDecoderInterface = M4OSA_NULL;

    pDecoderInterface = (M4DECODER_VideoInterface*)M4OSA_32bitAlignedMalloc(
        sizeof(M4DECODER_VideoInterface), M4DECODER_EXTERNAL,
        (M4OSA_Char*)"VideoEditorVideoDecoder_getInterface" );
    if (M4OSA_NULL == pDecoderInterface) {
        return M4ERR_ALLOC;
    }

    *pDecoderType = decoderType;

    pDecoderInterface->m_pFctCreate    = VideoEditorVideoDecoder_create;
    pDecoderInterface->m_pFctDestroy   = VideoEditorVideoDecoder_destroy;
    pDecoderInterface->m_pFctGetOption = VideoEditorVideoDecoder_getOption;
    pDecoderInterface->m_pFctSetOption = VideoEditorVideoDecoder_setOption;
    pDecoderInterface->m_pFctDecode    = VideoEditorVideoDecoder_decode;
    pDecoderInterface->m_pFctRender    = VideoEditorVideoDecoder_render;

    *pDecInterface = (M4OSA_Context)pDecoderInterface;
    return M4NO_ERROR;
}

M4OSA_ERR VideoEditorVideoDecoder_getSoftwareInterface(M4DECODER_VideoType decoderType,
        M4DECODER_VideoType *pDecoderType, M4OSA_Context *pDecInterface) {
    M4DECODER_VideoInterface* pDecoderInterface = M4OSA_NULL;

    pDecoderInterface = (M4DECODER_VideoInterface*)M4OSA_32bitAlignedMalloc(
        sizeof(M4DECODER_VideoInterface), M4DECODER_EXTERNAL,
        (M4OSA_Char*)"VideoEditorVideoDecoder_getInterface" );
    if (M4OSA_NULL == pDecoderInterface) {
        return M4ERR_ALLOC;
    }

    *pDecoderType = decoderType;

    pDecoderInterface->m_pFctCreate    = VideoEditorVideoSoftwareDecoder_create;
    pDecoderInterface->m_pFctDestroy   = VideoEditorVideoDecoder_destroy;
    pDecoderInterface->m_pFctGetOption = VideoEditorVideoDecoder_getOption;
    pDecoderInterface->m_pFctSetOption = VideoEditorVideoDecoder_setOption;
    pDecoderInterface->m_pFctDecode    = VideoEditorVideoDecoder_decode;
    pDecoderInterface->m_pFctRender    = VideoEditorVideoDecoder_render;

    *pDecInterface = (M4OSA_Context)pDecoderInterface;
    return M4NO_ERROR;
}
extern "C" {

M4OSA_ERR VideoEditorVideoDecoder_getInterface_MPEG4(
        M4DECODER_VideoType *pDecoderType, M4OSA_Context *pDecInterface) {
    return VideoEditorVideoDecoder_getInterface(M4DECODER_kVideoTypeMPEG4,
        pDecoderType, pDecInterface);
}

M4OSA_ERR VideoEditorVideoDecoder_getInterface_H264(
        M4DECODER_VideoType *pDecoderType, M4OSA_Context *pDecInterface) {
    return VideoEditorVideoDecoder_getInterface(M4DECODER_kVideoTypeAVC,
        pDecoderType, pDecInterface);

}

M4OSA_ERR VideoEditorVideoDecoder_getSoftwareInterface_MPEG4(
        M4DECODER_VideoType *pDecoderType, M4OSA_Context *pDecInterface) {
    return VideoEditorVideoDecoder_getSoftwareInterface(M4DECODER_kVideoTypeMPEG4,
        pDecoderType, pDecInterface);
}

M4OSA_ERR VideoEditorVideoDecoder_getSoftwareInterface_H264(
        M4DECODER_VideoType *pDecoderType, M4OSA_Context *pDecInterface) {
    return VideoEditorVideoDecoder_getSoftwareInterface(M4DECODER_kVideoTypeAVC,
        pDecoderType, pDecInterface);

}

M4OSA_ERR VideoEditorVideoDecoder_getVideoDecodersAndCapabilities(
    M4DECODER_VideoDecoders** decoders) {
    return queryVideoDecoderCapabilities(decoders);
}

}  // extern "C"
