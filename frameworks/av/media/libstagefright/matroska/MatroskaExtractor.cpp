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
#define LOG_TAG "MatroskaExtractor"
#include <utils/Log.h>

#include "MatroskaExtractor.h"

#include "mkvparser.hpp"

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include <utils/String8.h>

#ifndef ANDROID_DEFAULT_CODE
#include "../include/avc_utils.h"
#include <media/stagefright/foundation/ABuffer.h>

// big endian fourcc
#define BFOURCC(c1, c2, c3, c4) \
    (c4 << 24 | c3 << 16 | c2 << 8 | c1)

#define MATROSKAEXTRACTOR_USE_XLOG
#ifdef MATROSKAEXTRACTOR_USE_XLOG
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

#ifndef ANDROID_DEFAULT_CODE
#define MKV_RIFF_WAVE_FORMAT_PCM            (0x0001)
#define MKV_RIFF_WAVE_FORMAT_ALAW           (0x0006)
#define MKV_RIFF_WAVE_FORMAT_ADPCM_ms       (0x0002)
#define MKV_RIFF_WAVE_FORMAT_ADPCM_ima_wav  (0x0011)
#define MKV_RIFF_WAVE_FORMAT_MULAW          (0x0007)
#define MKV_RIFF_WAVE_FORMAT_MPEGL12        (0x0050)
#define MKV_RIFF_WAVE_FORMAT_MPEGL3         (0x0055)
#define MKV_RIFF_WAVE_FORMAT_AMR_NB         (0x0057)
#define MKV_RIFF_WAVE_FORMAT_AMR_WB         (0x0058)
#define MKV_RIFF_WAVE_FORMAT_AAC            (0x00ff)
#define MKV_RIFF_IBM_FORMAT_MULAW           (0x0101)
#define MKV_RIFF_IBM_FORMAT_ALAW            (0x0102)
#define MKV_RIFF_WAVE_FORMAT_WMAV1          (0x0160)
#define MKV_RIFF_WAVE_FORMAT_WMAV2          (0x0161)
#define MKV_RIFF_WAVE_FORMAT_WMAV3          (0x0162)
#define MKV_RIFF_WAVE_FORMAT_WMAV3_L        (0x0163)
#define MKV_RIFF_WAVE_FORMAT_AAC_AC         (0x4143)
#define MKV_RIFF_WAVE_FORMAT_VORBIS         (0x566f)
#define MKV_RIFF_WAVE_FORMAT_VORBIS1        (0x674f)
#define MKV_RIFF_WAVE_FORMAT_VORBIS2        (0x6750)
#define MKV_RIFF_WAVE_FORMAT_VORBIS3        (0x6751)
#define MKV_RIFF_WAVE_FORMAT_VORBIS1PLUS    (0x676f)
#define MKV_RIFF_WAVE_FORMAT_VORBIS2PLUS    (0x6770)
#define MKV_RIFF_WAVE_FORMAT_VORBIS3PLUS    (0x6771)
#define MKV_RIFF_WAVE_FORMAT_AAC_pm         (0x706d)
#define MKV_RIFF_WAVE_FORMAT_GSM_AMR_CBR    (0x7A21)
#define MKV_RIFF_WAVE_FORMAT_GSM_AMR_VBR    (0x7A22)

static const uint32_t kMP3HeaderMask = 0xfffe0c00;//0xfffe0cc0 add by zhihui zhang no consider channel mode
static const char *MKVwave2MIME(uint16_t id) {
    switch (id) {
        case  MKV_RIFF_WAVE_FORMAT_AMR_NB:
        case  MKV_RIFF_WAVE_FORMAT_GSM_AMR_CBR:
        case  MKV_RIFF_WAVE_FORMAT_GSM_AMR_VBR:
            return MEDIA_MIMETYPE_AUDIO_AMR_NB;

        case  MKV_RIFF_WAVE_FORMAT_AMR_WB:
            return MEDIA_MIMETYPE_AUDIO_AMR_WB;

        case  MKV_RIFF_WAVE_FORMAT_AAC:
        case  MKV_RIFF_WAVE_FORMAT_AAC_AC:
        case  MKV_RIFF_WAVE_FORMAT_AAC_pm:       
            return MEDIA_MIMETYPE_AUDIO_AAC;

        case  MKV_RIFF_WAVE_FORMAT_VORBIS:
        case  MKV_RIFF_WAVE_FORMAT_VORBIS1:
        case  MKV_RIFF_WAVE_FORMAT_VORBIS2:        
        case  MKV_RIFF_WAVE_FORMAT_VORBIS3:
        case  MKV_RIFF_WAVE_FORMAT_VORBIS1PLUS:
        case  MKV_RIFF_WAVE_FORMAT_VORBIS2PLUS:
        case  MKV_RIFF_WAVE_FORMAT_VORBIS3PLUS:
            return MEDIA_MIMETYPE_AUDIO_VORBIS;

        case  MKV_RIFF_WAVE_FORMAT_MPEGL12:
        case  MKV_RIFF_WAVE_FORMAT_MPEGL3:
            return MEDIA_MIMETYPE_AUDIO_MPEG;

        case MKV_RIFF_WAVE_FORMAT_MULAW:
        case MKV_RIFF_IBM_FORMAT_MULAW:
            return MEDIA_MIMETYPE_AUDIO_G711_MLAW;

        case MKV_RIFF_WAVE_FORMAT_ALAW:
        case MKV_RIFF_IBM_FORMAT_ALAW:
            return MEDIA_MIMETYPE_AUDIO_G711_ALAW;

        case MKV_RIFF_WAVE_FORMAT_PCM:
            return MEDIA_MIMETYPE_AUDIO_RAW;
#if defined(MTK_AUDIO_ADPCM_SUPPORT) || defined(HAVE_ADPCMENCODE_FEATURE)  			
		case MKV_RIFF_WAVE_FORMAT_ADPCM_ms:
			return MEDIA_MIMETYPE_AUDIO_MS_ADPCM;
		case MKV_RIFF_WAVE_FORMAT_ADPCM_ima_wav:
			return MEDIA_MIMETYPE_AUDIO_DVI_IMA_ADPCM;
#endif

		case MKV_RIFF_WAVE_FORMAT_WMAV1:
			return MEDIA_MIMETYPE_AUDIO_WMA;	
		case MKV_RIFF_WAVE_FORMAT_WMAV2:
			return MEDIA_MIMETYPE_AUDIO_WMA;
        default:
            ALOGW("unknown wave %x", id);
            return "";
    };
}

static const uint32_t AACSampleFreqTable[16] =
{
    96000, /* 96000 Hz */
    88200, /* 88200 Hz */
    64000, /* 64000 Hz */
    48000, /* 48000 Hz */
    44100, /* 44100 Hz */
    32000, /* 32000 Hz */
    24000, /* 24000 Hz */
    22050, /* 22050 Hz */
    16000, /* 16000 Hz */
    12000, /* 12000 Hz */
    11025, /* 11025 Hz */
    8000, /*  8000 Hz */
    -1, /* future use */
    -1, /* future use */
    -1, /* future use */
    -1  /* escape value */
};

static bool findAACSampleFreqIndex(uint32_t freq, uint8_t &index)
{
	uint8_t i;
	uint8_t num = sizeof(AACSampleFreqTable)/sizeof(AACSampleFreqTable[0]);
	for (i=0; i < num; i++) {
		if (freq == AACSampleFreqTable[i])
			break;
	}
	if (i > 11)
		return false;

	index = i;
	return true;
}


static const char *BMKVFourCC2MIME(uint32_t fourcc) {
    switch (fourcc) {
        case BFOURCC('m', 'p', '4', 'a'):
	 case BFOURCC('M', 'P', '4', 'A'):
            return MEDIA_MIMETYPE_AUDIO_AAC;

		case BFOURCC('s', 'a', 'm', 'r'):
			return MEDIA_MIMETYPE_AUDIO_AMR_NB;
		
		case BFOURCC('s', 'a', 'w', 'b'):
			return MEDIA_MIMETYPE_AUDIO_AMR_WB;
		
		case BFOURCC('x', 'v', 'i', 'd'):
		case BFOURCC('X', 'V', 'I', 'D'):
			return MEDIA_MIMETYPE_VIDEO_XVID;
		case BFOURCC('d', 'i', 'v', 'x'):
		case BFOURCC('D', 'I', 'V', 'X'):
			return MEDIA_MIMETYPE_VIDEO_DIVX;
		case BFOURCC('D', 'X', '5', '0'):
		case BFOURCC('m', 'p', '4', 'v'):
		case BFOURCC('M', 'P', '4', 'V'):
			return MEDIA_MIMETYPE_VIDEO_MPEG4;
					
		case BFOURCC('D', 'I', 'V', '3'):
		case BFOURCC('d', 'i', 'v', '3'):
		case BFOURCC('D', 'I', 'V', '4'):
		case BFOURCC('d', 'i', 'v', '4'):
			return MEDIA_MIMETYPE_VIDEO_DIVX3;
		
		case BFOURCC('s', '2', '6', '3'):
		case BFOURCC('H', '2', '6', '3'):
		case BFOURCC('h', '2', '6', '3'):
			return MEDIA_MIMETYPE_VIDEO_H263;

		case BFOURCC('a', 'v', 'c', '1'):
		case BFOURCC('A', 'V', 'C', '1'):
		case BFOURCC('H', '2', '6', '4'):
		case BFOURCC('h', '2', '6', '4'):
			return MEDIA_MIMETYPE_VIDEO_AVC;
		
		case BFOURCC('M', 'P', 'G', '2'):
		case BFOURCC('m', 'p', 'g', '2'):
			return MEDIA_MIMETYPE_VIDEO_MPEG2;
		case BFOURCC('M', 'J', 'P', 'G'):
		case BFOURCC('m', 'p', 'p', 'g'):
			return MEDIA_MIMETYPE_VIDEO_MJPEG;
#ifdef MTK_VIDEO_HEVC_SUPPORT			
		case BFOURCC('H', 'E', 'V', 'C'):
		case BFOURCC('h', 'e', 'v', 'c'):
			return MEDIA_MIMETYPE_VIDEO_HEVC;
#endif


        default:
            ALOGW("unknown fourcc 0x%8.8x", fourcc);
            return "";
    }
}
#ifdef MTK_SUBTITLE_SUPPORT
static const char *BMapCodecId2SubTT(const char *const codecID)
{
    if (!strcmp("S_TEXT/SSA", codecID))
    {
        return MEDIA_MIMETYPE_TEXT_SSA;
    }
    else if (!strcmp("S_TEXT/ASS", codecID))
    {
        return MEDIA_MIMETYPE_TEXT_ASS;
    }
    else if (!strcmp("S_TEXT/UTF8", codecID))
    {
        return MEDIA_MIMETYPE_TEXT_TXT;//need confirm, from BD code, it is SRT
    }
    else if (!strcmp("S_VOBSUB", codecID))
    {
        return MEDIA_MIMETYPE_TEXT_VOBSUB;
    }
    else
    {
        ALOGE("unknown subtitle codecId");
        return "";
    }
}
#endif

static bool get_mp3_info(
        uint32_t header, size_t *frame_size,
        int *out_sampling_rate = NULL, int *out_channels = NULL,
        int *out_bitrate = NULL) {
    *frame_size = 0;

    if (out_sampling_rate) {
        *out_sampling_rate = 0;
    }

    if (out_channels) {
        *out_channels = 0;
    }

    if (out_bitrate) {
        *out_bitrate = 0;
    }

    if ((header & 0xffe00000) != 0xffe00000) {
		ALOGD("line=%d", __LINE__);
        return false;
    }

    unsigned version = (header >> 19) & 3;

    if (version == 0x01) {
		ALOGD("line=%d", __LINE__);
        return false;
    }

    unsigned layer = (header >> 17) & 3;

    if (layer == 0x00) {
		ALOGD("line=%d", __LINE__);
        return false;
    }

    unsigned protection = (header >> 16) & 1;

    unsigned bitrate_index = (header >> 12) & 0x0f;

    if (bitrate_index == 0 || bitrate_index == 0x0f) {
        // Disallow "free" bitrate.
        
		ALOGD("line=%d", __LINE__);
        return false;
    }

    unsigned sampling_rate_index = (header >> 10) & 3;

    if (sampling_rate_index == 3) {
		
		ALOGD("line=%d", __LINE__);
        return false;
    }

    static const int kSamplingRateV1[] = { 44100, 48000, 32000 };
    int sampling_rate = kSamplingRateV1[sampling_rate_index];
    if (version == 2 /* V2 */) {
        sampling_rate /= 2;
    } else if (version == 0 /* V2.5 */) {
        sampling_rate /= 4;
    }

    unsigned padding = (header >> 9) & 1;

    if (layer == 3) {
        // layer I

        static const int kBitrateV1[] = {
            32, 64, 96, 128, 160, 192, 224, 256,
            288, 320, 352, 384, 416, 448
        };

        static const int kBitrateV2[] = {
            32, 48, 56, 64, 80, 96, 112, 128,
            144, 160, 176, 192, 224, 256
        };

        int bitrate =
            (version == 3 /* V1 */)
                ? kBitrateV1[bitrate_index - 1]
                : kBitrateV2[bitrate_index - 1];

        if (out_bitrate) {
            *out_bitrate = bitrate;
        }

        *frame_size = (12000 * bitrate / sampling_rate + padding) * 4;
    } else {
        // layer II or III

        static const int kBitrateV1L2[] = {
            32, 48, 56, 64, 80, 96, 112, 128,
            160, 192, 224, 256, 320, 384
        };

        static const int kBitrateV1L3[] = {
            32, 40, 48, 56, 64, 80, 96, 112,
            128, 160, 192, 224, 256, 320
        };

        static const int kBitrateV2[] = {
            8, 16, 24, 32, 40, 48, 56, 64,
            80, 96, 112, 128, 144, 160
        };

        int bitrate;
        if (version == 3 /* V1 */) {
            bitrate = (layer == 2 /* L2 */)
                ? kBitrateV1L2[bitrate_index - 1]
                : kBitrateV1L3[bitrate_index - 1];
        } else {
            // V2 (or 2.5)

            bitrate = kBitrateV2[bitrate_index - 1];
        }

        if (out_bitrate) {
            *out_bitrate = bitrate;
        }

        if (version == 3 /* V1 */) {
            *frame_size = 144000 * bitrate / sampling_rate + padding;
        } else {
            // V2 or V2.5          
#ifndef ANDROID_DEFAULT_CODE
			if(layer == 2 /* L2 */){
					*frame_size = 144000 * bitrate / sampling_rate + padding;
			}else{
#endif
				*frame_size = 72000 * bitrate / sampling_rate + padding;
#ifndef ANDROID_DEFAULT_CODE
			}
#endif
        }
    }

    if (out_sampling_rate) {
        *out_sampling_rate = sampling_rate;
    }

    if (out_channels) {
        int channel_mode = (header >> 6) & 3;

        *out_channels = (channel_mode == 3) ? 1 : 2;
    }

    return true;
}

static int mkv_mp3HeaderStartAt(const uint8_t *start, int length, uint32_t header) {
    uint32_t code = 0;
    int i = 0;

    for(i=0; i<length; i++){
		//ALOGD("start[%d]=%x", i, start[i]);
        code = (code<<8) + start[i];
		//ALOGD("code=0x%8.8x, mask=0x%8.8x", code, kMP3HeaderMask);
        if ((code & kMP3HeaderMask) == (header & kMP3HeaderMask)) {
            // some files has no seq start code
            return i - 3;
        }
    }

    return -1;
}
#endif

struct DataSourceReader : public mkvparser::IMkvReader {
    DataSourceReader(const sp<DataSource> &source)
        : mSource(source) {
    }

    virtual int Read(long long position, long length, unsigned char* buffer) {
        CHECK(position >= 0);
        CHECK(length >= 0);

        if (length == 0) {
            return 0;
        }

        ssize_t n = mSource->readAt(position, buffer, length);

        if (n <= 0) {
#ifndef ANDROID_DEFAULT_CODE
			ALOGE("readAt %d bytes, Read return -1", n);
			ALOGE("position= %d, length= %d", position, length);
#endif
            return -1;
        }

        return 0;
    }

    virtual int Length(long long* total, long long* available) {
        off64_t size;
        if (mSource->getSize(&size) != OK) {
            *total = -1;
            *available = (long long)((1ull << 63) - 1);

            return 0;
        }

        if (total) {
            *total = size;
        }

        if (available) {
            *available = size;
        }

        return 0;
    }

private:
    sp<DataSource> mSource;

    DataSourceReader(const DataSourceReader &);
    DataSourceReader &operator=(const DataSourceReader &);
};

////////////////////////////////////////////////////////////////////////////////

struct BlockIterator {
    BlockIterator(MatroskaExtractor *extractor, unsigned long trackNum);

    bool eos() const;

    void advance();
    void reset();

    void seek(
            int64_t seekTimeUs, bool isAudio,
            int64_t *actualFrameTimeUs);

    const mkvparser::Block *block() const;
    int64_t blockTimeUs() const;
#ifdef MTK_SUBTITLE_SUPPORT
    int64_t blockEndTimeUs() const;
#endif

private:
    MatroskaExtractor *mExtractor;
    unsigned long mTrackNum;
#ifndef ANDROID_DEFAULT_CODE
	unsigned long mTrackType;
#endif
    const mkvparser::Cluster *mCluster;
    const mkvparser::BlockEntry *mBlockEntry;
    long mBlockEntryIndex;
#ifndef ANDROID_DEFAULT_CODE
	void backward();
	bool backward_eos(const mkvparser::Cluster*, const mkvparser::BlockEntry*);
	void seekwithoutcue(int64_t seekTimeUs);
#endif

    void advance_l();

    BlockIterator(const BlockIterator &);
    BlockIterator &operator=(const BlockIterator &);
};

struct MatroskaSource : public MediaSource {
    MatroskaSource(
            const sp<MatroskaExtractor> &extractor, size_t index);

    virtual status_t start(MetaData *params);
    virtual status_t stop();

    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options);

protected:
    virtual ~MatroskaSource();

private:
    enum Type {
        AVC,
        AAC,
#ifndef ANDROID_DEFAULT_CODE
		VP8,
		VP9,
		VORBIS,
		MPEG4,
		MPEG2,
		RV,
		MP2_3,
		COOK,
		MJPEG,	
		HEVC,

#ifdef MTK_AUDIO_DDPLUS_SUPPORT
        AC3,
        EAC3,
#endif
#endif
        OTHER
    };

    sp<MatroskaExtractor> mExtractor;
    size_t mTrackIndex;
    Type mType;
    bool mIsAudio;
#ifndef ANDROID_DEFAULT_CODE
	bool mWantsNALFragments;
#endif
    BlockIterator mBlockIter;
    size_t mNALSizeLen;  // for type AVC

    List<MediaBuffer *> mPendingFrames;

    status_t advance();

    status_t readBlock();
    void clearPendingFrames();

    MatroskaSource(const MatroskaSource &);
    MatroskaSource &operator=(const MatroskaSource &);

#ifndef ANDROID_DEFAULT_CODE
	status_t findMP3Header(uint32_t *header);
	unsigned char* mTrackContentAddData;
	size_t mTrackContentAddDataSize;
	bool mNewFrame;
	int64_t	mCurrentTS;
	bool mFirstFrame;
	uint32_t mMP3Header;
	bool mIsFromFFmpeg;
public:
	void setCodecInfoFromFirstFrame();
#endif

};

MatroskaSource::MatroskaSource(
        const sp<MatroskaExtractor> &extractor, size_t index)
    : mExtractor(extractor),
      mTrackIndex(index),
      mType(OTHER),
      mIsAudio(false),
#ifndef ANDROID_DEFAULT_CODE
      mWantsNALFragments(false), 
#endif
      mBlockIter(mExtractor.get(),
                 mExtractor->mTracks.itemAt(index).mTrackNum),
      mNALSizeLen(0) {

#ifndef ANDROID_DEFAULT_CODE
		mCurrentTS = 0;
		mFirstFrame = true;
		(mExtractor->mTracks.itemAt(index)).mTrack->GetContentAddInfo(&mTrackContentAddData, &mTrackContentAddDataSize);
		//check whether is ffmeg video with codecID of  V_MS/VFW/FOURCC
		mIsFromFFmpeg = false;
		const char * CodecId = (mExtractor->mTracks.itemAt(index)).mTrack->GetCodecId();
		ALOGI("MatroskaSource contructor mCodecId = %s",CodecId);
		if (!strcmp("V_MS/VFW/FOURCC", CodecId))
				mIsFromFFmpeg = true;
#endif
	    sp<MetaData> meta = mExtractor->mTracks.itemAt(index).mMeta;

	    const char *mime;
	    CHECK(meta->findCString(kKeyMIMEType, &mime));

	    mIsAudio = !strncasecmp("audio/", mime, 6);

	    if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC)) {
	        mType = AVC;

	        uint32_t dummy;
	        const uint8_t *avcc;
	        size_t avccSize;
#ifndef ANDROID_DEFAULT_CODE
		if (!meta->findData(kKeyAVCC, &dummy, (const void **)&avcc, &avccSize))
		{
			sp<MetaData> metadata = NULL;
			while (metadata == NULL)
			{
				clearPendingFrames();
                            while (mPendingFrames.empty()) 
                            {
                                status_t err = readBlock();
                                
                                if (err != OK) 
                                {
                                    clearPendingFrames();
                                    break;
                                }
                            }				
				if(!mPendingFrames.empty())
				{
					MediaBuffer *buffer = *mPendingFrames.begin();
					sp < ABuffer >  accessUnit = new ABuffer(buffer->range_length());
					ALOGD("bigbuf->range_length() = %d",buffer->range_length());
					memcpy(accessUnit->data(),buffer->data(),buffer->range_length());
					metadata = MakeAVCCodecSpecificData(accessUnit);
				}
			}
			CHECK(metadata->findData(kKeyAVCC, &dummy, (const void **)&avcc, &avccSize));
			ALOGD("avccSize = %d ",avccSize);
			CHECK_GE(avccSize, 5u);
			meta->setData(kKeyAVCC, 0, avcc, avccSize);
			mBlockIter.reset();
			clearPendingFrames();
		}
#endif

        CHECK(meta->findData(
                    kKeyAVCC, &dummy, (const void **)&avcc, &avccSize));

        CHECK_GE(avccSize, 5u);

        mNALSizeLen = 1 + (avcc[4] & 3);
        ALOGV("mNALSizeLen = %d", mNALSizeLen);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AAC)) {
        mType = AAC;
    }
#ifndef ANDROID_DEFAULT_CODE
	else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_VPX))
	{
		mType = VP8;
	}
	else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_VP9))
	{
		mType = VP9;
	}
	else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_VORBIS))
	{
		mType = VORBIS;
	}
	else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_MPEG4))
	{
		mType = MPEG4;
	}	
	else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_XVID))
	{
		mType = MPEG4;
	}
	else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_DIVX))
	{
		mType = MPEG4;
	}
	else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_DIVX3))
	{
		mType = MPEG4;
	}
	else if(!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_MPEG2))
	{
		mType = MPEG2;
	}
	else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG))
	{
		mType = MP2_3;
		
		if (findMP3Header(&mMP3Header) != OK)
		{
			ALOGW("No mp3 header found");
		}
		ALOGD("mMP3Header=0x%8.8x", mMP3Header);
	}
	else if(!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_MJPEG))
	{
		mType=MJPEG;
	}
#ifdef MTK_VIDEO_HEVC_SUPPORT	
	else if(!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_HEVC))
	{
		uint32_t dummy;
        const uint8_t *hvcc;
        size_t hvccSize;
		mType=HEVC;
		ALOGI("MatroskaSource, is HEVC");

		if (!meta->findData(kKeyHVCC, &dummy, (const void **)&hvcc, &hvccSize))
		{
			sp<MetaData> metadata = NULL;
			while (metadata == NULL)
			{
				clearPendingFrames();
                while (mPendingFrames.empty()) 
                {
                    status_t err = readBlock();
                    
                    if (err != OK) 
                    {
                        clearPendingFrames();
                        break;
                    }
                }				
				if(!mPendingFrames.empty())
				{
					MediaBuffer *buffer = *mPendingFrames.begin();
					sp < ABuffer >  accessUnit = new ABuffer(buffer->range_length());
					ALOGD("firstBuffer->range_length() = %d",buffer->range_length());
					memcpy(accessUnit->data(),buffer->data(),buffer->range_length());
					metadata = MakeHEVCCodecSpecificData(accessUnit);
				}
			}
	        CHECK(metadata->findData(kKeyHVCC, &dummy, (const void **)&hvcc, &hvccSize));
			ALOGD("avccSize = %d ",hvccSize);
	        CHECK_GE(hvccSize, 5u);
			meta->setData(kKeyHVCC, 0, hvcc, hvccSize);
			mBlockIter.reset();
			clearPendingFrames();
		}

		CHECK(meta->findData(kKeyHVCC, &dummy, (const void **)&hvcc, &hvccSize));
		
		CHECK_GE(hvccSize, 5u);

        mNALSizeLen = 1 + (hvcc[21] & 3);
        ALOGI("hevc mNALSizeLen = %d", mNALSizeLen);	
		
	}
#endif	
#ifdef MTK_AUDIO_DDPLUS_SUPPORT
    else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AC3))
    {
        mType = AC3;
    }
    else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_EC3))
    {
        mType = EAC3;
    }
#endif
	ALOGI("MatroskaSource constructor mType=%d",mType);
#endif
}

MatroskaSource::~MatroskaSource() {
#ifndef ANDROID_DEFAULT_CODE
	ALOGI("~MatroskaSource destructor");
#endif
    clearPendingFrames();
}

#ifndef ANDROID_DEFAULT_CODE
status_t MatroskaSource::findMP3Header(uint32_t * header)
{
	if (header != NULL)
		*header = 0;
	
	uint32_t code = 0;
	while (0 == *header) {
		while (mPendingFrames.empty()) 
		{
			status_t err = readBlock();
		
			if (err != OK) 
			{
				clearPendingFrames();
				return err;
			}
		}
		MediaBuffer *frame = *mPendingFrames.begin();
		size_t size = frame->range_length();
		size_t offset = frame->range_offset();
		size_t i;
		size_t frame_size;
		for (i=0; i<size; i++) {
			ALOGD("data[%d]=%x", i, *((uint8_t*)frame->data()+offset+i));
			code = (code<<8) + *((uint8_t*)frame->data()+offset+i);
			if (get_mp3_info(code, &frame_size, NULL, NULL, NULL)) {
				*header = code;
				mBlockIter.reset();
				clearPendingFrames();
				return OK;
			}
		}
	}

	return ERROR_END_OF_STREAM;
}
#endif

status_t MatroskaSource::start(MetaData *params) {
    mBlockIter.reset();
#ifndef ANDROID_DEFAULT_CODE
	mNewFrame = true;

	int32_t val;
    if (params && params->findInt32(kKeyWantsNALFragments, &val)
        && val != 0) {
        mWantsNALFragments = true;
    } else {
        mWantsNALFragments = false;
    }
	ALOGD("MatroskaSource start,mWantsNALFragments=%s",mWantsNALFragments?"true":"false");
#endif

    return OK;
}

status_t MatroskaSource::stop() {
    clearPendingFrames();

    return OK;
}

sp<MetaData> MatroskaSource::getFormat() {
    return mExtractor->mTracks.itemAt(mTrackIndex).mMeta;
}

////////////////////////////////////////////////////////////////////////////////

BlockIterator::BlockIterator(
        MatroskaExtractor *extractor, unsigned long trackNum)
    : mExtractor(extractor),
      mTrackNum(trackNum),
      mCluster(NULL),
      mBlockEntry(NULL),
      mBlockEntryIndex(0) {
#ifndef ANDROID_DEFAULT_CODE
		mTrackType = mExtractor->mSegment->GetTracks()->GetTrackByNumber(trackNum)->GetType();
#endif

    reset();
}

bool BlockIterator::eos() const {
    return mCluster == NULL || mCluster->EOS();
}

void BlockIterator::advance() {
    Mutex::Autolock autoLock(mExtractor->mLock);
    advance_l();
}

void BlockIterator::advance_l() {
    for (;;) {
        long res = mCluster->GetEntry(mBlockEntryIndex, mBlockEntry);
        ALOGV("GetEntry returned %ld", res);

        long long pos;
        long len;
        if (res < 0) {
            // Need to parse this cluster some more

            CHECK_EQ(res, mkvparser::E_BUFFER_NOT_FULL);

            res = mCluster->Parse(pos, len);
            ALOGV("Parse returned %ld", res);

            if (res < 0) {
                // I/O error

                ALOGE("Cluster::Parse returned result %ld", res);

                mCluster = NULL;
                break;
            }

            continue;
        } else if (res == 0) {
            // We're done with this cluster

            const mkvparser::Cluster *nextCluster;
            res = mExtractor->mSegment->ParseNext(
                    mCluster, nextCluster, pos, len);
            ALOGV("ParseNext returned %ld", res);

            if (res != 0) {
                // EOF or error
#ifndef ANDROID_DEFAULT_CODE
				ALOGI("BlockIterator::advance_l,no more cluter found in file, res %ld",res);
#endif
                mCluster = NULL;
                break;
            }

            CHECK_EQ(res, 0);
            CHECK(nextCluster != NULL);
            CHECK(!nextCluster->EOS());

            mCluster = nextCluster;

            res = mCluster->Parse(pos, len);
#ifndef ANDROID_DEFAULT_CODE
	        if (res < 0) {
				// I/O error
				ALOGE("Cluster::Parse(2) returned result %ld", res);

				mCluster = NULL;
				break;
			}
#endif
            ALOGV("Parse (2) returned %ld", res);
            CHECK_GE(res, 0);

            mBlockEntryIndex = 0;
            continue;
        }

        CHECK(mBlockEntry != NULL);
        CHECK(mBlockEntry->GetBlock() != NULL);
        ++mBlockEntryIndex;

        if (mBlockEntry->GetBlock()->GetTrackNumber() == mTrackNum) {
            break;
        }
    }
}

void BlockIterator::reset() {
    Mutex::Autolock autoLock(mExtractor->mLock);

    mCluster = mExtractor->mSegment->GetFirst();
    mBlockEntry = NULL;
    mBlockEntryIndex = 0;

    do {
        advance_l();
    } while (!eos() && block()->GetTrackNumber() != mTrackNum);
}

#ifndef ANDROID_DEFAULT_CODE
//added by vend_am00033 start for seeking backward
void BlockIterator::backward()
{
	while ((mCluster != NULL) && (mCluster != &mExtractor->mSegment->m_eos))
	{
        if (mBlockEntry != NULL) {
            mBlockEntry = mCluster->GetPrev(mBlockEntry);
        } else if (mCluster != NULL) {
            mCluster = mExtractor->mSegment->GetPrev(mCluster);

            if (mCluster == &mExtractor->mSegment->m_eos) {
                break;
            }

		//mBlockEntry = mCluster->GetLast(mBlockEntry);
		const long status = mCluster->GetLast(mBlockEntry);
			if (status < 0){  //error
			ALOGE("get last blockenry failed!");
			mCluster=NULL;
				return ;
			}

        }

        if (mBlockEntry != NULL
                && mBlockEntry->GetBlock()->GetTrackNumber() == mTrackNum) {
            break;
        }

	}
}

bool BlockIterator::backward_eos(const mkvparser::Cluster* oldCluster, const mkvparser::BlockEntry* oldBlock)
{
	if (mCluster == &mExtractor->mSegment->m_eos)
	{
		//cannot seek I frame backward, so we seek I frame forward again
		mCluster = oldCluster;
		mBlockEntry = oldBlock;
		mBlockEntryIndex = oldBlock->GetIndex()+1;		
		while (!eos() && (mTrackType != 2) && !block()->IsKey())
		{
			advance();
		}

		return true;
	}
	return false;
}
//added by vend_am00033 end

void BlockIterator::seekwithoutcue(int64_t seekTimeUs) {
	//    Mutex::Autolock autoLock(mExtractor->mLock);

    mCluster = mExtractor->mSegment->FindCluster(seekTimeUs * 1000ll); 
	const long status = mCluster->GetFirst(mBlockEntry);
    if (status < 0){  //error
    ALOGE("get last blockenry failed!");
		mCluster=NULL;
        return ;
    	}
	//    mBlockEntry = mCluster != NULL ? mCluster->GetFirst(mBlockEntry): NULL;
	//    mBlockEntry = NULL;
    mBlockEntryIndex = 0;

	//added by vend_am00033 start for seeking backward
	const mkvparser::Cluster* startCluster = mCluster;//cannot be null
	const mkvparser::Cluster* iframe_cluster = NULL;
	const mkvparser::BlockEntry* iframe_block = NULL;
	bool find_iframe = false;
	assert(startCluster != NULL);
	if (mBlockEntry)
	{
		if ((mTrackType != 2) && (block()->GetTrackNumber() == mTrackNum) && (block()->IsKey()))
		{
			find_iframe = true;
			iframe_cluster = mCluster;
			iframe_block = mBlockEntry;
		}
	}
	//added by vend_am00033 end
	while (!eos() && ((block()->GetTrackNumber() != mTrackNum) || (blockTimeUs() < seekTimeUs))) 

	{
        	advance_l();
	//added by vend_am00033 start for seeking backward
		if (mBlockEntry)
		{
			if ((mTrackType != 2) && (block()->GetTrackNumber() == mTrackNum) && (block()->IsKey()))
			{
				find_iframe = true;
				iframe_cluster = mCluster;
				iframe_block = mBlockEntry;
			}

		}
	//added by vend_am00033 end
    }

	//added by vend_am00033 start for seeking backward

	if (!eos() && (mTrackType != 2) && (!block()->IsKey()))
	{
		if (!find_iframe)
		{
			const mkvparser::Cluster* oldCluster = mCluster;
			const mkvparser::BlockEntry* oldBlock = mBlockEntry;
			mCluster = mExtractor->mSegment->GetPrev(startCluster);

			if (backward_eos(oldCluster, oldBlock))
				return;
			
			//mBlockEntry = mCluster != NULL ? mCluster->GetLast(mBlockEntry): NULL;
			const long status = mCluster->GetLast(mBlockEntry);
   			 if (status < 0){  //error
    				ALOGE("get last blockenry failed!");
					mCluster=NULL;
       				 return ;
    		}

			while ((mCluster != &mExtractor->mSegment->m_eos) && 
				((block()->GetTrackNumber() != mTrackNum) || (!block()->IsKey())))
			{
				backward();
			}
			mBlockEntryIndex=mBlockEntry->GetIndex()+1;
			if (backward_eos(oldCluster, oldBlock))
				return;

		}
		else
		{
			mCluster = iframe_cluster;
			mBlockEntry = iframe_block;
			mBlockEntryIndex = iframe_block->GetIndex()+1;			
		}
	}

	//added by vend_am00033 end

	while (!eos() && !mBlockEntry->GetBlock()->IsKey() && (mTrackType != 2)/*Audio*/)//hai.li

	{
        advance_l();
       }
}

#endif

void BlockIterator::seek(
        int64_t seekTimeUs, bool isAudio,
        int64_t *actualFrameTimeUs) {
    Mutex::Autolock autoLock(mExtractor->mLock);

    *actualFrameTimeUs = -1ll;

    const int64_t seekTimeNs = seekTimeUs * 1000ll;

    mkvparser::Segment* const pSegment = mExtractor->mSegment;

    // Special case the 0 seek to avoid loading Cues when the application
    // extraneously seeks to 0 before playing.
    if (seekTimeNs <= 0) {
        ALOGV("Seek to beginning: %lld", seekTimeUs);
        mCluster = pSegment->GetFirst();
        mBlockEntryIndex = 0;
        do {
            advance_l();
        } while (!eos() && block()->GetTrackNumber() != mTrackNum);
        return;
    }

    ALOGV("Seeking to: %lld", seekTimeUs);

    // If the Cues have not been located then find them.
    const mkvparser::Cues* pCues = pSegment->GetCues();
    const mkvparser::SeekHead* pSH = pSegment->GetSeekHead();
    if (!pCues && pSH) {
        const size_t count = pSH->GetCount();
        const mkvparser::SeekHead::Entry* pEntry;
        ALOGV("No Cues yet");

        for (size_t index = 0; index < count; index++) {
            pEntry = pSH->GetEntry(index);

            if (pEntry->id == 0x0C53BB6B) { // Cues ID
                long len; long long pos;
                pSegment->ParseCues(pEntry->pos, pos, len);
                pCues = pSegment->GetCues();
                ALOGV("Cues found");
                break;
            }
        }

        if (!pCues) {
            ALOGE("No Cues in file");
#ifndef ANDROID_DEFAULT_CODE
			ALOGI("no cue data,seek without cue data");	
			seekwithoutcue(seekTimeUs);            	
#endif
            return;
        }
    }
    else if (!pSH) {
        ALOGE("No SeekHead");
#ifndef ANDROID_DEFAULT_CODE
		ALOGD("no seekhead, seek without cue data");	
		seekwithoutcue(seekTimeUs); 	
#endif			
        return;
    }

    const mkvparser::CuePoint* pCP;
    while (!pCues->DoneParsing()) {
        pCues->LoadCuePoint();
        pCP = pCues->GetLast();
#ifndef ANDROID_DEFAULT_CODE		
		ALOGV("pCP= %s",pCP==NULL?"NULL":"not NULL");
		if(pCP==NULL)
			continue;
#endif
        if (pCP->GetTime(pSegment) >= seekTimeNs) {
            ALOGV("Parsed past relevant Cue");
            break;
        }
    }

    // The Cue index is built around video keyframes
    mkvparser::Tracks const *pTracks = pSegment->GetTracks();
    const mkvparser::Track *pTrack = NULL;
    for (size_t index = 0; index < pTracks->GetTracksCount(); ++index) {
        pTrack = pTracks->GetTrackByIndex(index);
        if (pTrack && pTrack->GetType() == 1) { // VIDEO_TRACK
            ALOGV("Video track located at %d", index);
            break;
        }
    }

    // Always *search* based on the video track, but finalize based on mTrackNum
    const mkvparser::CuePoint::TrackPosition* pTP;
#ifndef ANDROID_DEFAULT_CODE
	if (pTrack && pTrack->GetType() == 1) {
		bool CuesFind=false;
		CuesFind=pCues->Find(seekTimeNs, pTrack, pCP, pTP);
		if(!CuesFind){
			ALOGD("CuesFind fail,seek without cue data");
			seekwithoutcue(seekTimeUs);
			return;
		}
		//pCues->Find(seekTimeNs, pTrack, pCP, pTP);

	} else {
		ALOGD("Audio track for seeking,seek without cue data");
		seekwithoutcue(seekTimeUs);
		return;
	}
#else
    if (pTrack && pTrack->GetType() == 1) {
        pCues->Find(seekTimeNs, pTrack, pCP, pTP);
    } else {
        ALOGE("Did not locate the video track for seeking");
        return;
    }
#endif


    mCluster = pSegment->FindOrPreloadCluster(pTP->m_pos);
#ifndef ANDROID_DEFAULT_CODE
	ALOGV("Cluster num=%ld",pSegment->GetCount());
#endif 

    CHECK(mCluster);
    CHECK(!mCluster->EOS());

    // mBlockEntryIndex starts at 0 but m_block starts at 1
    CHECK_GT(pTP->m_block, 0);
    mBlockEntryIndex = pTP->m_block - 1;

    for (;;) {
        advance_l();

        if (eos()) break;

        if (isAudio || block()->IsKey()) {
            // Accept the first key frame
            *actualFrameTimeUs = (block()->GetTime(mCluster) + 500LL) / 1000LL;
            ALOGV("Requested seek point: %lld actual: %lld",
                  seekTimeUs, actualFrameTimeUs);
            break;
        }
    }
}

const mkvparser::Block *BlockIterator::block() const {
    CHECK(!eos());

    return mBlockEntry->GetBlock();
}

int64_t BlockIterator::blockTimeUs() const {
    return (mBlockEntry->GetBlock()->GetTime(mCluster) + 500ll) / 1000ll;
}

#ifdef MTK_SUBTITLE_SUPPORT
int64_t BlockIterator::blockEndTimeUs() const {
    if (mBlockEntry->GetKind() == mkvparser::BlockEntry::kBlockGroup){
        mkvparser::BlockGroup* p = (mkvparser::BlockGroup*)(mBlockEntry);
        ALOGD("p->GetDuration() %lld ",p->GetDuration());
        if (p->GetDuration())
        {
            mkvparser::Segment* const pSegment = mExtractor->mSegment;
            long long timeScale = pSegment->GetInfo()->GetTimeCodeScale();
            ALOGD("p->timeScale() %lld ",timeScale);
            return (p->GetDuration() * timeScale + 500ll) / 1000ll + blockTimeUs() ;
        }
        else{
            return -1;// unvalue
        }
    }
    else{
        return -1;// unvalue
    }
}
#endif
////////////////////////////////////////////////////////////////////////////////

static unsigned U24_AT(const uint8_t *ptr) {
    return ptr[0] << 16 | ptr[1] << 8 | ptr[2];
}

static size_t clz(uint8_t x) {
    size_t numLeadingZeroes = 0;

    while (!(x & 0x80)) {
        ++numLeadingZeroes;
        x = x << 1;
    }

    return numLeadingZeroes;
}

void MatroskaSource::clearPendingFrames() {
    while (!mPendingFrames.empty()) {
        MediaBuffer *frame = *mPendingFrames.begin();
        mPendingFrames.erase(mPendingFrames.begin());

        frame->release();
        frame = NULL;
    }
}

status_t MatroskaSource::readBlock() {
    CHECK(mPendingFrames.empty());

    if (mBlockIter.eos()) {
#ifndef ANDROID_DEFAULT_CODE
		ALOGI("MatroskaSource::readBlock, end of stream");
#endif 
        return ERROR_END_OF_STREAM;
    }

    const mkvparser::Block *block = mBlockIter.block();

    int64_t timeUs = mBlockIter.blockTimeUs();
#ifdef MTK_SUBTITLE_SUPPORT
    int64_t timeUsEnd = mBlockIter.blockEndTimeUs();
#endif
#ifndef ANDROID_DEFAULT_CODE	
	int frameCount = block->GetFrameCount();
	ALOGV("readBlock,Block frameCount =%d,block timeUs =%lld",frameCount,timeUs);
	if (mType == AAC || mType == MP2_3){
		int64_t size = block->GetDataSize();
		MediaBuffer *bigbuf= new MediaBuffer(size+frameCount*mTrackContentAddDataSize);
    	int64_t buf_offset=0;
		for (int i = 0; i < frameCount; ++i) {
			const mkvparser::Block::Frame &frame = block->GetFrame(i);
			MediaBuffer *mbuf = new MediaBuffer(frame.len);
			long n = frame.Read(mExtractor->mReader, (unsigned char *)mbuf->data());
			if (n != 0) {
            	mPendingFrames.clear();
         		mBlockIter.advance();
            	return ERROR_IO;
      		}
			if(mTrackContentAddDataSize != 0){		
				memcpy((unsigned char *)bigbuf->data()+buf_offset, mTrackContentAddData, mTrackContentAddDataSize);
				buf_offset+=mTrackContentAddDataSize;
			}
			memcpy((unsigned char *)bigbuf->data()+buf_offset,mbuf->data(),frame.len);
			buf_offset += frame.len;
			mbuf->release();
		}
		if(buf_offset!=(size+frameCount*mTrackContentAddDataSize)){
			ALOGD("mp3 data count failed,we lost %lld number data ",buf_offset-(size+frameCount*mTrackContentAddDataSize));
			return ERROR_IO;
		}
#ifdef MTK_SUBTITLE_SUPPORT
        if (timeUsEnd && mType == OTHER){
            bigbuf->meta_data()->setInt64(kKeyDriftTime, timeUsEnd);
            ALOGD("subtitle mType = %d timeUsBegin: %lld timeUsEnd %lld ",mType,timeUs,timeUsEnd);
        }
#endif
		bigbuf->meta_data()->setInt64(kKeyTime, timeUs);
    	bigbuf->meta_data()->setInt32(kKeyIsSyncFrame, block->IsKey());
		mPendingFrames.push_back(bigbuf);			
	}else{
		 for (int i = 0; i < frameCount; ++i) {
        	const mkvparser::Block::Frame &frame = block->GetFrame(i);
			ALOGV("readBlock,frame.len=%ld,mTrackContentAddDataSize=%d",frame.len,mTrackContentAddDataSize);
        	MediaBuffer *mbuf = new MediaBuffer(frame.len+mTrackContentAddDataSize);
	    	if (mTrackContentAddDataSize != 0)
				memcpy(mbuf->data(), mTrackContentAddData, mTrackContentAddDataSize);
	    	long n = frame.Read(mExtractor->mReader, (unsigned char *)mbuf->data()+mTrackContentAddDataSize);
			mbuf->meta_data()->setInt64(kKeyTime, timeUs);
#ifdef MTK_SUBTITLE_SUPPORT
            if (timeUsEnd && mType == OTHER){
                mbuf->meta_data()->setInt64(kKeyDriftTime, timeUsEnd);
                ALOGD("subtitle mType = %d timeUsBegin: %lld timeUsEnd %lld ",mType,timeUs,timeUsEnd);
            }
#endif
    		mbuf->meta_data()->setInt32(kKeyIsSyncFrame, block->IsKey());

			if (n != 0) {
            	mPendingFrames.clear();

           		 mBlockIter.advance();
            	return ERROR_IO;
        	}

        	mPendingFrames.push_back(mbuf);
 		}		
	}

#else
    for (int i = 0; i < block->GetFrameCount(); ++i) {
        const mkvparser::Block::Frame &frame = block->GetFrame(i);

        MediaBuffer *mbuf = new MediaBuffer(frame.len);
        mbuf->meta_data()->setInt64(kKeyTime, timeUs);
        mbuf->meta_data()->setInt32(kKeyIsSyncFrame, block->IsKey());

        long n = frame.Read(mExtractor->mReader, (unsigned char *)mbuf->data());
        if (n != 0) {
            mPendingFrames.clear();

            mBlockIter.advance();
            return ERROR_IO;
        }

        mPendingFrames.push_back(mbuf);
    }
#endif
    mBlockIter.advance();

    return OK;
}

status_t MatroskaSource::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;
#ifndef ANDROID_DEFAULT_CODE
    ALOGV("%s mType=%d,MatroskaSource::read--> ",mIsAudio? "audio":"video",mType);
#endif 
    int64_t targetSampleTimeUs = -1ll;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
#ifndef ANDROID_DEFAULT_CODE
	bool seeking = false;
#endif
    if (options && options->getSeekTo(&seekTimeUs, &mode)
            && !mExtractor->isLiveStreaming()) {
        clearPendingFrames();

        // The audio we want is located by using the Cues to seek the video
        // stream to find the target Cluster then iterating to finalize for
        // audio.
        int64_t actualFrameTimeUs;
        mBlockIter.seek(seekTimeUs, mIsAudio, &actualFrameTimeUs);

#ifndef ANDROID_DEFAULT_CODE
		ALOGD("read, seeking mode=%d,seekTimeUs=%lld,%s mType=%d,actualFrameTimeUs=%lld",\
		mode,seekTimeUs,mIsAudio? "audio":"video",mType,actualFrameTimeUs);

		if (mIsAudio||mode == ReadOptions::SEEK_CLOSEST) {
			ALOGD("mIsAudio=%d or mode=%d,need set targetSampleTimeUs=seekTimeUs",mIsAudio,mode);
			targetSampleTimeUs = seekTimeUs; 
			seeking = true;
		}
#else
        if (mode == ReadOptions::SEEK_CLOSEST) {
            targetSampleTimeUs = actualFrameTimeUs;
        }

#endif

    }
    while (mPendingFrames.empty()) {
        status_t err = readBlock();
        if (err != OK) {
#ifndef ANDROID_DEFAULT_CODE
		ALOGW("%s,mType=%d,MatroskaSource::readBlock fail err=%d",mIsAudio? "audio":"video",err);
#endif 
           clearPendingFrames();

            return err;
        }
    }
    MediaBuffer *frame = *mPendingFrames.begin();
#ifndef ANDROID_DEFAULT_CODE
	if (seeking || mFirstFrame)
	{
		ALOGD("MatroskaSource::read,%s mType=%d,seeking =%d or mFirstFrame=%d",mIsAudio? "audio":"video",mType,seeking,mFirstFrame);
		mFirstFrame = false;
		frame->meta_data()->findInt64(kKeyTime, &mCurrentTS);
		if(mCurrentTS >= 0)
			ALOGD("frame mCurrentTS=%lld", mCurrentTS);
		else{
			ALOGE("frame mCurrentTS=%lld, set ts = 0", mCurrentTS);
			mCurrentTS =0;
			frame->meta_data()->setInt64(kKeyTime, mCurrentTS);
		}
	}
	size_t size = frame->range_length();
	ALOGV("%s mType=%d,mType=%d,frame size =%d",mIsAudio? "audio":"video",mType,size);

	if (seeking && (mType == VP8||mType == VP9||mType == MPEG4||mType==MJPEG||mType==MPEG2||mType==HEVC))
	{
		frame->meta_data()->setInt64(kKeyTargetTime, (targetSampleTimeUs>= 0ll?targetSampleTimeUs:seekTimeUs));
	}

	if ((mType != AVC) && (mType != HEVC)) {

		if (MP2_3 == mType) {
			ALOGV("MatroskaSource::read MP2_3-->");
			int32_t start = -1;
			while (start < 0) {
				start = mkv_mp3HeaderStartAt((const uint8_t*)frame->data()+frame->range_offset(), frame->range_length(), mMP3Header);
				ALOGV("start=%d, frame->range_length() = %d, frame->range_offset() =%d", start, frame->range_length(),frame->range_offset());
				if (start >= 0)
					break;
				frame->release();
				mPendingFrames.erase(mPendingFrames.begin());
				while (mPendingFrames.empty()) {
					status_t err = readBlock();			
					if (err != OK) {
						clearPendingFrames();
			               //      ALOGE("tianread MatroskaSource::read-----<");
						return err;
					}
				}
				frame = *mPendingFrames.begin();				
				frame->meta_data()->findInt64(kKeyTime, &mCurrentTS);
				//ALOGD("mCurrentTS1=%lld", mCurrentTS);
			} 

			frame->set_range(frame->range_offset()+start, frame->range_length()-start);

			
			uint32_t header = *(uint32_t*)((uint8_t*)frame->data()+frame->range_offset());
			header = ((header >> 24) & 0xff) | ((header >> 8) & 0xff00) | ((header << 8) & 0xff0000) | ((header << 24) & 0xff000000); 
			//ALOGD("HEADER=%8.8x", header);
			size_t frame_size;
			int out_sampling_rate;
			int out_channels;
			int out_bitrate;
			if (!get_mp3_info(header, &frame_size, &out_sampling_rate, &out_channels, &out_bitrate)) {
				ALOGE("MP3 Header read fail!!");
				return ERROR_UNSUPPORTED;
			}
			MediaBuffer *buffer = new MediaBuffer(frame_size);
			ALOGV("MP3 frame %d frame->range_length() %d",frame_size, frame->range_length() );

			if (frame_size > frame->range_length()) {				
				memcpy(buffer->data(), (uint8_t*)(frame->data())+frame->range_offset(), frame->range_length());
				size_t sumSize =0;
				sumSize += frame->range_length();
				size_t needSize = frame_size - frame->range_length();
				frame->release();
				mPendingFrames.erase(mPendingFrames.begin());
				while (mPendingFrames.empty()) {
					status_t err = readBlock();
			
					if (err != OK) {
						clearPendingFrames();		
						return err;
					}
				}
				frame = *mPendingFrames.begin();
				size_t offset = frame->range_offset();
				size_t size = frame->range_length();

				while(size < needSize){//the next buffer frame is not enough to fullfill mp3 frame, we have read until mp3 frame is completed.
					memcpy((uint8_t*)(buffer->data())+sumSize, (uint8_t*)(frame->data())+offset, size);
					needSize -= size;
					sumSize+=size;
					frame->release();
					mPendingFrames.erase(mPendingFrames.begin());
					while (mPendingFrames.empty()) {
						status_t err = readBlock();

						if (err != OK) {
							clearPendingFrames();	                           
							return err;
						}
					}
					frame = *mPendingFrames.begin();
					offset = frame->range_offset();
					size = frame->range_length();				
				}
				memcpy((uint8_t*)(buffer->data())+sumSize, (uint8_t*)(frame->data())+offset, needSize);
				frame->set_range(offset+needSize, size-needSize);
			
		     }
			else { 
				size_t offset = frame->range_offset();
				size_t size = frame->range_length();
				memcpy(buffer->data(), (uint8_t*)(frame->data())+offset, frame_size);
				frame->set_range(offset+frame_size, size-frame_size);
			}
			if (frame->range_length() < 4) {
				frame->release();
				frame = NULL;
				mPendingFrames.erase(mPendingFrames.begin());
			}
			ALOGV("MatroskaSource::read MP2_3 frame kKeyTime=%lld,kKeyTargetTime=%lld",mCurrentTS,targetSampleTimeUs);
			buffer->meta_data()->setInt64(kKeyTime, mCurrentTS);						
			mCurrentTS += (int64_t)frame_size*8000ll/out_bitrate;
			
		    if (targetSampleTimeUs >= 0ll) 
            	buffer->meta_data()->setInt64(kKeyTargetTime, targetSampleTimeUs);
			*out = buffer;	
			ALOGV("MatroskaSource::read MP2_3--<, keyTime=%lld for next frame",mCurrentTS);
			return OK;
			
		}else {
		ALOGV("MatroskaSource::read,not AVC,HEVC,mp3,return frame directly,kKeyTargetTime=%lld",targetSampleTimeUs);
#else
			mPendingFrames.erase(mPendingFrames.begin());		
			if (mType != AVC) {
#endif
	        if (targetSampleTimeUs >= 0ll) {
	            frame->meta_data()->setInt64(
	                    kKeyTargetTime, targetSampleTimeUs);
	        }
	        *out = frame;
#ifndef ANDROID_DEFAULT_CODE
			mPendingFrames.erase(mPendingFrames.begin());
			return OK;
		}
#else
        return OK;
#endif
	
	}

#ifndef ANDROID_DEFAULT_CODE
	//is AVC or HEVC
	if (size < mNALSizeLen) {
			*out = frame;
			//frame->release();
			frame = NULL;
			mPendingFrames.erase(mPendingFrames.begin());
			ALOGE("[Warning]size:%d < mNALSizeLen:%d", size, mNALSizeLen);
			return OK;
	}

	ALOGV("MatroskaSource::read,mType is %d(AVC/HEVC),mWantsNALFragments=%d",mType,mWantsNALFragments);
	
	if(!mWantsNALFragments) {
#endif
	    // Each input frame contains one or more NAL fragments, each fragment
	    // is prefixed by mNALSizeLen bytes giving the fragment length,
	    // followed by a corresponding number of bytes containing the fragment.
	    // We output all these fragments into a single large buffer separated
	    // by startcodes (0x00 0x00 0x00 0x01).
	    const uint8_t *srcPtr =
	        (const uint8_t *)frame->data() + frame->range_offset();

	    size_t srcSize = frame->range_length();
		
#ifndef ANDROID_DEFAULT_CODE
		if ((srcSize >=4 && *(srcPtr+0) == 0x00 && *(srcPtr+1) == 0x00 && *(srcPtr+2) == 0x00 && (*(srcPtr+3) == 0x01) || (*(srcPtr+3) == 0x00)) ||
			(mIsFromFFmpeg && frame->range_length() >= 3 && *(srcPtr+0) == 0x00 && *(srcPtr+1) == 0x00 && *(srcPtr+2) == 0x01)){
			//already nal start code+nal data
			ALOGV("MatroskaSource::read,frame is already nal start code + nal data,return frame directly, isFromFFMpeg=%d",mIsFromFFmpeg);
			 if (targetSampleTimeUs >= 0ll) {
		            frame->meta_data()->setInt64(
		                    kKeyTargetTime, targetSampleTimeUs);
		     }
	        *out = frame;
			mPendingFrames.erase(mPendingFrames.begin());
			return OK;
		}

#endif
	    size_t dstSize = 0;
	    MediaBuffer *buffer = NULL;
	    uint8_t *dstPtr = NULL;

	    for (int32_t pass = 0; pass < 2; ++pass) {
	        size_t srcOffset = 0;
	        size_t dstOffset = 0;
	        while (srcOffset + mNALSizeLen <= srcSize) {
	            size_t NALsize;
	            switch (mNALSizeLen) {
					
	                case 1: NALsize = srcPtr[srcOffset]; break;
	                case 2: NALsize = U16_AT(srcPtr + srcOffset); break;
	                case 3: NALsize = U24_AT(srcPtr + srcOffset); break;
	                case 4: NALsize = U32_AT(srcPtr + srcOffset); break;
	                default:
	                    TRESPASS();
	            }				
	            if (srcOffset + mNALSizeLen + NALsize > srcSize) {
	                break;
	            }

	            if (pass == 1) {
	                memcpy(&dstPtr[dstOffset], "\x00\x00\x00\x01", 4);
					
#ifndef ANDROID_DEFAULT_CODE
					 size_t NALtype = (srcPtr[srcOffset + mNALSizeLen] & 0x7E) >> 1;
					ALOGV("read,pass =%d,memcpy one NAL (type=%d,NALsize=%d) to buffer",pass,NALtype,NALsize);
#endif
	                memcpy(&dstPtr[dstOffset + 4],
	                       &srcPtr[srcOffset + mNALSizeLen],
	                       NALsize);
	            }

	            dstOffset += 4;  // 0x00 00 00 01
	            dstOffset += NALsize;

	            srcOffset += mNALSizeLen + NALsize;
	        }

	        if (srcOffset < srcSize) {
	            // There were trailing bytes or not enough data to complete
	            // a fragment.

	            frame->release();
	            frame = NULL;           
	            return ERROR_MALFORMED;
	        }

	        if (pass == 0) {
	            dstSize = dstOffset;

	            buffer = new MediaBuffer(dstSize);

	            int64_t timeUs;
	            CHECK(frame->meta_data()->findInt64(kKeyTime, &timeUs));
	            int32_t isSync;
	            CHECK(frame->meta_data()->findInt32(kKeyIsSyncFrame, &isSync));

	            buffer->meta_data()->setInt64(kKeyTime, timeUs);
	            buffer->meta_data()->setInt32(kKeyIsSyncFrame, isSync);

	            dstPtr = (uint8_t *)buffer->data();
	        }
	    }

	    frame->release();
	    frame = NULL;

	    if (targetSampleTimeUs >= 0ll) {
	        buffer->meta_data()->setInt64(
	                kKeyTargetTime, targetSampleTimeUs);
	    }
	//#endif

	    *out = buffer;
#ifndef ANDROID_DEFAULT_CODE
		ALOGV("read return,buffer range_length=%d,buffer offset=%d",buffer->range_length(),buffer->range_offset());
		mPendingFrames.erase(mPendingFrames.begin());
#endif

	    return OK;
#ifndef ANDROID_DEFAULT_CODE
	}else{
		//1. Maybe more than one NALs in one sample, so we should parse and send
		//these NALs to decoder one by one, other than skip data.
		//2. Just send the pure NAL data to decoder. (No NAL size field or start code)
	    uint8_t *data = (uint8_t *)frame->data() + frame->range_offset();
	  	
		size_t NALsize = 0;
		if (frame->range_length() >=4 && *(data+0) == 0x00 && *(data+1) == 0x00 && *(data+2) == 0x00 && *(data+3) == 0x01)
		{
			mNALSizeLen = 4;
			MediaBuffer *tmpbuffer = *mPendingFrames.begin();

			uint8_t * data = (uint8_t*)tmpbuffer->data() + tmpbuffer->range_offset();
			int size = tmpbuffer->range_length();
			//ALOGD("accessunit size = %d",size);
			int tail = 4;
			while(tail <= size - 4)
			{
				if((*(data+tail+0) == 0x00 && *(data+tail+1) == 0x00 && *(data+tail+2) == 0x00 && *(data+tail+3) == 0x01) || tail == size -4)
				{
					int nalsize = 0;
					if(tail == size -4)
					{
						nalsize = size;
					}
					else
					{
						nalsize = tail;
					}
					NALsize = nalsize - 4;
					break;
				}
				tail++;
			}
		}
		//add by mtk80691 to support NAL start code of V_MS/VFW/FOURCC track with 00 00 01  
		else if(mIsFromFFmpeg && frame->range_length() >= 3 && *(data+0) == 0x00 && *(data+1) == 0x00 && *(data+2) == 0x01){
			ALOGV("MatroskaSource read,NAL start with 00 00 01");
			mNALSizeLen = 3;
			MediaBuffer *tmpbuffer = *mPendingFrames.begin();

			uint8_t * data = (uint8_t*)tmpbuffer->data() + tmpbuffer->range_offset();
			int size = tmpbuffer->range_length();
			int tail = 3;
			while(tail <= size - 3)
			{
				if((*(data+tail+0) == 0x00 && *(data+tail+1) == 0x00 && *(data+tail+2) == 0x01) || tail == size -3)
				{
					int nalsize = 0;
					if(tail == size -3)
					{
						nalsize = size;
					}
					else
					{
						nalsize = tail;
					}
					NALsize = nalsize - 3;
					break;
				}
				tail++;
			}
		}
		else		
		{   		
	        switch (mNALSizeLen) {
	            case 1: NALsize = data[0]; break;
	            case 2: NALsize = U16_AT(&data[0]); break;
	            case 3: NALsize = U24_AT(&data[0]); break;
	            case 4: NALsize = U32_AT(&data[0]); break;
	            default:
	            	TRESPASS();
	        }			

		   //  ALOGE("MatroskaSource, size =%d, NALsize =%d, mNALSizeLen=%d", size, NALsize, mNALSizeLen);
	  
	      }
	    if (size < NALsize + mNALSizeLen) {
			//frame->release();
			frame->set_range(frame->range_offset() + mNALSizeLen
							,frame->range_length() - mNALSizeLen);
		    *out = frame;
			frame = NULL;
			mPendingFrames.erase(mPendingFrames.begin());                       
			ALOGV("MatroskaSource read, size:%d, NALsize:%d, mNALSizeLen:%d", size, NALsize, mNALSizeLen);
			return OK;			              
	    }		
	    MediaBuffer *buffer = new MediaBuffer(NALsize);
	    int64_t timeUs;
	    CHECK(frame->meta_data()->findInt64(kKeyTime, &timeUs));
	    int32_t isSync;
	    CHECK(frame->meta_data()->findInt32(kKeyIsSyncFrame, &isSync));

	    buffer->meta_data()->setInt64(kKeyTime, timeUs);
	    buffer->meta_data()->setInt32(kKeyIsSyncFrame, isSync);
	    memcpy((uint8_t *)buffer->data(),
	           (const uint8_t *)frame->data() + frame->range_offset() + mNALSizeLen,
	           NALsize);
		frame->set_range(frame->range_offset() + mNALSizeLen + NALsize
						,frame->range_length() - mNALSizeLen - NALsize);
		
		ALOGV("read,one NAL return,buffer range_length=%d,buffer offset=%d,timeUs=%lld,sync=%d",\
			buffer->range_length(),buffer->range_offset(),timeUs,isSync);

		if (frame->range_length() == 0)
		{
			frame->release();
			frame = NULL;
			mPendingFrames.erase(mPendingFrames.begin());
			ALOGV("all NAL parsed of the frame ");
			//mNewFrame = true;
		}
		
		if (seeking)
		{
			buffer->meta_data()->setInt64(kKeyTargetTime, targetSampleTimeUs >= 0ll?targetSampleTimeUs:seekTimeUs);
			ALOGI("read,seeking,kKeyTargetTime=%lld",targetSampleTimeUs >= 0ll?targetSampleTimeUs:seekTimeUs);
		}
		
		*out = buffer;

		return OK;
	}
	
#endif
}

////////////////////////////////////////////////////////////////////////////////

MatroskaExtractor::MatroskaExtractor(const sp<DataSource> &source)
    : mDataSource(source),
      mReader(new DataSourceReader(mDataSource)),
      mSegment(NULL),
      mExtractedThumbnails(false),
      mIsWebm(false) {
    off64_t size;
    mIsLiveStreaming =
        (mDataSource->flags()
            & (DataSource::kWantsPrefetching
                | DataSource::kIsCachingDataSource))
        && mDataSource->getSize(&size) != OK;

    mkvparser::EBMLHeader ebmlHeader;
    long long pos;
#ifndef ANDROID_DEFAULT_CODE
	ALOGD("=====================================\n"); 
    ALOGD("MatroskaExtractor constructor +++ \n"); 
    ALOGD("[MKV Playback capability info]£º\n"); 
    ALOGD("Support Codec = \"Video:AVC,H263,MPEG4,HEVC,VP8,VP9\" \n"); 
	ALOGD("Support Codec = \"Audio: VORBIS,AAC,AMR,MP3\" \n"); 
    ALOGD("=====================================\n"); 
#endif 
    if (ebmlHeader.Parse(mReader, pos) < 0) {
        return;
    }

    if (ebmlHeader.m_docType && !strcmp("webm", ebmlHeader.m_docType)) {
        mIsWebm = true;
    }

    long long ret =
        mkvparser::Segment::CreateInstance(mReader, pos, mSegment);

    if (ret) {
        CHECK(mSegment == NULL);
        return;
    }

#ifndef ANDROID_DEFAULT_CODE

	if (isLiveStreaming()) {
		ALOGI("MatroskaExtractor is live streaming");
		ret = mSegment->ParseHeaders();
		CHECK_EQ(ret, 0);
		long len;
		ret = mSegment->LoadCluster(pos, len);
		CHECK_EQ(ret, 0);
	} 
	else {
		ret = mSegment->ParseHeaders();
        if(ret<0){
			ALOGE("MatroskaExtractor,Segment parse header return fail %lld", ret);
			delete mSegment;
			mSegment = NULL;
			return;
        }
	    const mkvparser::Cues* mCues= mSegment->GetCues();
        const mkvparser::SeekHead* mSH = mSegment->GetSeekHead();
		if((mCues==NULL)&&(mSH!=NULL)){
			size_t count = mSH->GetCount();
        	const mkvparser::SeekHead::Entry* mEntry;
        	for (size_t index = 0; index < count; index++) {
            	mEntry = mSH->GetEntry(index);
           		if (mEntry->id == 0x0C53BB6B) { // Cues ID
                	long len; 
					long long pos;
                	mSegment->ParseCues(mEntry->pos, pos, len);
               		mCues = mSegment->GetCues();
                	ALOGD("find cue data by seekhead");
                	break;
            	}	
			}
		}
		
		if(mCues){
			ALOGI("has Cue data");
			long len;
			ret = mSegment->LoadCluster(pos, len);
			ALOGD("Cluster num=%ld",mSegment->GetCount());
			ALOGD("LoadCluster done");
		}
		else  {
			ALOGW("no Cue data");	
			ret = mSegment->Load();
		}
	}
    
#else
    ret = mSegment->ParseHeaders();
    CHECK_EQ(ret, 0);

    long len;
    ret = mSegment->LoadCluster(pos, len);
    CHECK_EQ(ret, 0);
#endif

    if (ret < 0) {
        delete mSegment;
        mSegment = NULL;
        return;
    }

#if 0
    const mkvparser::SegmentInfo *info = mSegment->GetInfo();
    ALOGI("muxing app: %s, writing app: %s",
         info->GetMuxingAppAsUTF8(),
         info->GetWritingAppAsUTF8());
#endif
#ifndef ANDROID_DEFAULT_CODE
    mFileMetaData = new MetaData;   
    mFileMetaData->setInt32(kKeyVideoPreCheck, 1);
#endif
    addTracks();
#ifndef ANDROID_DEFAULT_CODE
	ALOGD("MatroskaExtractor constructor ---"); 
#endif
}

MatroskaExtractor::~MatroskaExtractor() {
#ifndef ANDROID_DEFAULT_CODE
	ALOGI("~MatroskaExtractor destructor");
#endif
    delete mSegment;
    mSegment = NULL;

    delete mReader;
    mReader = NULL;
}

size_t MatroskaExtractor::countTracks() {
    return mTracks.size();
}

sp<MediaSource> MatroskaExtractor::getTrack(size_t index) {
    if (index >= mTracks.size()) {
        return NULL;
    }

#ifndef ANDROID_DEFAULT_CODE
	sp<MediaSource> matroskasource = new MatroskaSource(this, index);
	int32_t isinfristframe = false;
	ALOGI("getTrack,index=%d", index);
	if (mTracks.itemAt(index).mMeta->findInt32(kKeyCodecInfoIsInFirstFrame, &isinfristframe)
		&& isinfristframe) {
		ALOGD("Codec info is in first frame");;
		(static_cast<MatroskaSource*>(matroskasource.get()))->setCodecInfoFromFirstFrame(); 
	}
	return matroskasource;
#else
    return new MatroskaSource(this, index);
#endif
}

sp<MetaData> MatroskaExtractor::getTrackMetaData(
        size_t index, uint32_t flags) {
    if (index >= mTracks.size()) {
        return NULL;
    }

    if ((flags & kIncludeExtensiveMetaData) && !mExtractedThumbnails
            && !isLiveStreaming()) {
        findThumbnails();
        mExtractedThumbnails = true;
    }

    return mTracks.itemAt(index).mMeta;
}

bool MatroskaExtractor::isLiveStreaming() const {
    return mIsLiveStreaming;
}

static void addESDSFromCodecPrivate(
        const sp<MetaData> &meta,
        bool isAudio, const void *priv, size_t privSize) {
    static const uint8_t kStaticESDS[] = {
        0x03, 22,
        0x00, 0x00,     // ES_ID
        0x00,           // streamDependenceFlag, URL_Flag, OCRstreamFlag

        0x04, 17,
        0x40,           // ObjectTypeIndication
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,

        0x05,
        // CodecSpecificInfo (with size prefix) follows
    };

    // Make sure all sizes can be coded in a single byte.
#ifndef ANDROID_DEFAULT_CODE
	// there are files CodecPrivate datasize is very big, we donot need that many 
	if(!(privSize + 22 - 2 < 128))
	{
		privSize = 128 -20-1;
	}
#else
    CHECK(privSize + 22 - 2 < 128);
#endif
    size_t esdsSize = sizeof(kStaticESDS) + privSize + 1;
    uint8_t *esds = new uint8_t[esdsSize];
    memcpy(esds, kStaticESDS, sizeof(kStaticESDS));
    uint8_t *ptr = esds + sizeof(kStaticESDS);
    *ptr++ = privSize;
    memcpy(ptr, priv, privSize);

    // Increment by codecPrivateSize less 2 bytes that are accounted for
    // already in lengths of 22/17
    esds[1] += privSize - 2;
    esds[6] += privSize - 2;

    // Set ObjectTypeIndication.
    esds[7] = isAudio ? 0x40   // Audio ISO/IEC 14496-3
                      : 0x20;  // Visual ISO/IEC 14496-2

    meta->setData(kKeyESDS, 0, esds, esdsSize);

    delete[] esds;
    esds = NULL;
}

#ifndef ANDROID_DEFAULT_CODE
void MatroskaSource::setCodecInfoFromFirstFrame() {
	ALOGD("setCodecInfoFromFirstFrame");
	clearPendingFrames();
	int64_t actualFrameTimeUs;
        mBlockIter.seek(0, mIsAudio, &actualFrameTimeUs);
	//mBlockIter.seek(0);
	
	status_t err = readBlock();
	if (err != OK) {
		ALOGE("read codec info from first block fail!");
		mBlockIter.reset();
		clearPendingFrames();
		return;
	}
	if (mPendingFrames.empty()) {
		return;
	}
	if (MPEG4 == mType) {
		size_t vosend;
		for (vosend=0; (vosend<200) && (vosend<(*mPendingFrames.begin())->range_length()-4); vosend++)
		{
			if (0xB6010000 == *(uint32_t*)((uint8_t*)((*mPendingFrames.begin())->data()) + vosend))
			{
				break;//Send VOS until VOP
			}
		}
		getFormat()->setData(kKeyMPEG4VOS, 0, (*mPendingFrames.begin())->data(), vosend);
		//for (int32_t i=0; i<vosend; i++)
		//	ALOGD("VOS[%d] = 0x%x", i, *((uint8_t *)((*mPendingFrames.begin())->data())+i));
	}
	else if(MPEG2== mType){
		size_t header_start = 0;
		size_t header_lenth = 0;
		for (header_start=0; (header_start<200) && (header_start<(*mPendingFrames.begin())->range_length()-4); header_start++)
		{
			if (0xB3010000 == *(uint32_t*)((uint8_t*)((*mPendingFrames.begin())->data()) + header_start))
			{
				break;
			}
		}
		for (header_lenth=0; (header_lenth<200) && (header_lenth<(*mPendingFrames.begin())->range_length()-4-header_start); header_lenth++)
		{
			if (0xB8010000 == *(uint32_t*)((uint8_t*)((*mPendingFrames.begin())->data()) +header_start + header_lenth))
			{
				break;
			}
		}
		for (size_t i=0; i< header_lenth; i++)
			ALOGD("MPEG2info[%d] = 0x%x", i, *((uint8_t *)((*mPendingFrames.begin())->data())+i+header_start));
		addESDSFromCodecPrivate(getFormat(), false,(uint8_t*)((*mPendingFrames.begin())->data())+header_start, header_lenth);
	}
	else if (MP2_3 == mType) {
		uint32_t header = *(uint32_t*)((uint8_t*)(*mPendingFrames.begin())->data()+(*mPendingFrames.begin())->range_offset());
		header = ((header >> 24) & 0xff) | ((header >> 8) & 0xff00) | ((header << 8) & 0xff0000) | ((header << 24) & 0xff000000); 
		ALOGD("HEADER=0x%x", header);
		size_t frame_size;
		int32_t out_sampling_rate;
		int32_t out_channels;
		int32_t out_bitrate;
		if(!get_mp3_info(header, &frame_size, &out_sampling_rate, &out_channels, &out_bitrate))
		{
			ALOGE("Get mp3 info fail");
			return;
		}
		ALOGD("mp3: frame_size=%d, sample_rate=%d, channel_count=%d, out_bitrate=%d", 
			frame_size, out_sampling_rate, out_channels, out_bitrate);
		if (out_channels > 2)
		{
			ALOGE("Unsupport mp3 channel count %d", out_channels);
			return;
		}
		getFormat()->setInt32(kKeySampleRate, out_sampling_rate);
        getFormat()->setInt32(kKeyChannelCount, out_channels);
		
		
	}
	
	mBlockIter.reset();
	clearPendingFrames();
}
#endif

#ifndef ANDROID_DEFAULT_CODE
status_t addVorbisCodecInfo(
        const sp<MetaData> &meta,
        const void *_codecPrivate, size_t codecPrivateSize) {

    CHECK(codecPrivateSize >= 3);
    const uint8_t *codecPrivate = (const uint8_t *)_codecPrivate;
	size_t len1=0,len2=0;
    CHECK(*(codecPrivate++) == 0x02);
    while(*codecPrivate==0xFF){
		len1+=*codecPrivate;
		codecPrivate++;
		codecPrivateSize--;
	}
    len1 += *(codecPrivate++);
	while(*codecPrivate==0xFF){
		len2+=*codecPrivate;
		codecPrivate++;
		codecPrivateSize--;
	}
    len2 += *(codecPrivate++);
    CHECK(codecPrivateSize > 3 + len1 + len2);
    CHECK(*codecPrivate == 0x01);
    meta->setData(kKeyVorbisInfo, 0, codecPrivate, len1);
    CHECK(codecPrivate[len1] == 0x03);
    CHECK(codecPrivate[len1 + len2 ] == 0x05);
    meta->setData(
        kKeyVorbisBooks, 0, &codecPrivate[len1 + len2 ],
        codecPrivateSize - len1 - len2  -3);
	return OK;
}
#else
status_t addVorbisCodecInfo(
        const sp<MetaData> &meta,
        const void *_codecPrivate, size_t codecPrivateSize) {
    // hexdump(_codecPrivate, codecPrivateSize);

    if (codecPrivateSize < 1) {
        return ERROR_MALFORMED;
    }

    const uint8_t *codecPrivate = (const uint8_t *)_codecPrivate;

    if (codecPrivate[0] != 0x02) {
        return ERROR_MALFORMED;
    }

    // codecInfo starts with two lengths, len1 and len2, that are
    // "Xiph-style-lacing encoded"...

    size_t offset = 1;
    size_t len1 = 0;
    while (offset < codecPrivateSize && codecPrivate[offset] == 0xff) {
        len1 += 0xff;
        ++offset;
    }
    if (offset >= codecPrivateSize) {
        return ERROR_MALFORMED;
    }
    len1 += codecPrivate[offset++];

    size_t len2 = 0;
    while (offset < codecPrivateSize && codecPrivate[offset] == 0xff) {
        len2 += 0xff;
        ++offset;
    }
    if (offset >= codecPrivateSize) {
        return ERROR_MALFORMED;
    }
    len2 += codecPrivate[offset++];

    if (codecPrivateSize < offset + len1 + len2) {
        return ERROR_MALFORMED;
    }

    if (codecPrivate[offset] != 0x01) {
        return ERROR_MALFORMED;
    }
    meta->setData(kKeyVorbisInfo, 0, &codecPrivate[offset], len1);

    offset += len1;
    if (codecPrivate[offset] != 0x03) {
        return ERROR_MALFORMED;
    }

    offset += len2;
    if (codecPrivate[offset] != 0x05) {
        return ERROR_MALFORMED;
    }

    meta->setData(
            kKeyVorbisBooks, 0, &codecPrivate[offset],
            codecPrivateSize - offset);

    return OK;
}
#endif

void MatroskaExtractor::addTracks() {
    const mkvparser::Tracks *tracks = mSegment->GetTracks();
#ifndef ANDROID_DEFAULT_CODE
	bool hasVideo = false;
	bool hasAudio = false;
#endif

    for (size_t index = 0; index < tracks->GetTracksCount(); ++index) {
        const mkvparser::Track *track = tracks->GetTrackByIndex(index);

        if (track == NULL) {
            // Apparently this is currently valid (if unexpected) behaviour
            // of the mkv parser lib.
#ifndef ANDROID_DEFAULT_CODE
			ALOGW("MatroskaExtractor::addTracks,Unsupport track type");
#endif
            continue;
        }

        const char *const codecID = track->GetCodecId();
#ifndef ANDROID_DEFAULT_CODE
 		ALOGI("MatroskaExtractor::addTracks,codec id = %s", codecID);
        ALOGI("MatroskaExtractor::addTracks,codec name = %s", track->GetCodecNameAsUTF8());
#if defined(MTK_AUDIO_CHANGE_SUPPORT) || defined(MTK_SUBTITLE_SUPPORT)
        if(track->GetLanguageAsUTF8()!=NULL)
		    ALOGE("language = %s", track->GetLanguageAsUTF8());
#endif

#else
        ALOGV("codec id = %s", codecID);
        ALOGV("codec name = %s", track->GetCodecNameAsUTF8());
#endif


        size_t codecPrivateSize;
        const unsigned char *codecPrivate =
            track->GetCodecPrivate(codecPrivateSize);
#ifdef MTK_SUBTITLE_SUPPORT
			enum { VIDEO_TRACK = 1, AUDIO_TRACK = 2, SUBTT_TRACK = 17 };
#else
        enum { VIDEO_TRACK = 1, AUDIO_TRACK = 2 };
#endif
        sp<MetaData> meta = new MetaData;

        status_t err = OK;

        switch (track->GetType()) {
            case VIDEO_TRACK:
            {
                const mkvparser::VideoTrack *vtrack =
                    static_cast<const mkvparser::VideoTrack *>(track);
#ifndef ANDROID_DEFAULT_CODE
				long long width = vtrack->GetWidth();
				long long height = vtrack->GetHeight();
				meta->setInt32(kKeyWidth, width);
		        meta->setInt32(kKeyHeight, height);
				ALOGD("video track width=%lld, height=%lld",width,height);
#endif    
                if (!strcmp("V_MPEG4/ISO/AVC", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_AVC);
#ifndef ANDROID_DEFAULT_CODE
					ALOGI("Video Codec: AVC");
					if (NULL == codecPrivate){
						ALOGE("Unsupport AVC Video: No codec info");
						mFileMetaData->setInt32(kKeyHasUnsupportVideo,true);
						continue;
					}

					unsigned char level = codecPrivate[3];
#endif
                    meta->setData(kKeyAVCC, 0, codecPrivate, codecPrivateSize);
                }
#ifndef ANDROID_DEFAULT_CODE
				else if ((!strcmp("V_MPEG4/ISO/ASP", codecID)) ||
						 (!strcmp("V_MPEG4/ISO/SP", codecID))) {
					meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MPEG4);
					ALOGD("Video Codec: MPEG4");
					if (codecPrivate != NULL)
						meta->setData(kKeyMPEG4VOS, 0, codecPrivate, codecPrivateSize);
					else {
						ALOGW("No specific codec private data, find it from the first frame");
						meta->setInt32(kKeyCodecInfoIsInFirstFrame,true);
					}
				}
#else 
				else if (!strcmp("V_MPEG4/ISO/ASP", codecID)) {
                    if (codecPrivateSize > 0) {
                        meta->setCString(
                                kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MPEG4);
                        addESDSFromCodecPrivate(
                                meta, false, codecPrivate, codecPrivateSize);
                    } else {
                        ALOGW("%s is detected, but does not have configuration.",codecID);
                        continue;
                    }
                }
#endif 
				else if (!strcmp("V_VP8", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_VP8);
#ifndef ANDROID_DEFAULT_CODE
					ALOGI("Video Codec: VP8");
#endif
                } else if (!strcmp("V_VP9", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_VP9);
#ifndef ANDROID_DEFAULT_CODE
					ALOGI("Video Codec: VP9");
#endif
                } 
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_VIDEO_HEVC_SUPPORT
				else if(!strcmp("V_MPEGH/ISO/HEVC", codecID)){
						meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_HEVC);
						if (NULL == codecPrivate){
							ALOGE("Unsupport HEVC Video: No codec info");
							mFileMetaData->setInt32(kKeyHasUnsupportVideo,true);
							continue;
						}
						else {
						meta->setData(kKeyHVCC, 0, codecPrivate, codecPrivateSize);
						ALOGD("Video Codec: HEVC,codecPrivateSize =%d",codecPrivateSize); 
}		
				}
#endif	
        		else if(!strcmp("V_MPEG4/MS/V3", codecID)){
						meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_DIVX3);
                   		meta->setData(kKeyMPEG4VOS, 0, NULL, 0); 
						
				}
				else if((!strcmp("V_MPEG2", codecID))||(!strcmp("V_MPEG1", codecID))){
                        meta->setCString(
                                kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MPEG2);
						if (codecPrivate != NULL)
                        	addESDSFromCodecPrivate(meta, false, codecPrivate, codecPrivateSize);
						else{
							ALOGW("No specific codec private data, find it from the first frame");
							meta->setInt32(kKeyCodecInfoIsInFirstFrame,true);
						}
				}
				else if(!strcmp("V_MJPEG",codecID)){
                        meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MJPEG);						
				}
				else if (!strcmp("V_MS/VFW/FOURCC", codecID)) {
					ALOGD("Video CodecID: V_MS/VFW/FOURCC");
					if ((NULL == codecPrivate) || (codecPrivateSize < 20)) {
						ALOGE("Unsupport video: V_MS/VFW/FOURCC has no invalid private data, codecPrivate=%p, codecPrivateSize=%d", codecPrivate, codecPrivateSize);
						mFileMetaData->setInt32(kKeyHasUnsupportVideo,true);
						continue;
					} else {
						uint32_t fourcc = *(uint32_t *)(codecPrivate + 16);
						//uint32_t j=0;
						//for(j; j<codecPrivateSize; j++){
						//	ALOGD("dump codecPrivate[%d]  %02x",j,*(uint8_t*)(codecPrivate+j));
						//}
						const char* mime = BMKVFourCC2MIME(fourcc);
						ALOGD("V_MS/VFW/FOURCC type is %s", mime);
						if (!strncasecmp("video/", mime, 6)) {
							meta->setCString(kKeyMIMEType, mime);
						} else {
							ALOGE("V_MS/VFW/FOURCC continue,unsupport video type");
							mFileMetaData->setInt32(kKeyHasUnsupportVideo,true);
							continue;
						}
						if(!strcmp(mime, MEDIA_MIMETYPE_VIDEO_DIVX)){
							meta->setInt32(kKeyCodecInfoIsInFirstFrame,true);
						}
						else if(!strcmp(mime, MEDIA_MIMETYPE_VIDEO_DIVX3)){
							meta->setData(kKeyMPEG4VOS, 0, codecPrivate, 0);
							uint16_t divx3_width,divx3_height;
							divx3_width= *(uint16_t *)(codecPrivate + 4);
							divx3_height=*(uint16_t *)(codecPrivate + 8);
							meta->setInt32(kKeyWidth, width);
        					meta->setInt32(kKeyHeight, height);
							ALOGD("divx3_width=%d,divx3_height=%d",divx3_width,divx3_height);
						}
						else if(!strcmp(mime, MEDIA_MIMETYPE_VIDEO_XVID)){
							meta->setInt32(kKeyCodecInfoIsInFirstFrame,true);
						}
						else if (!strcmp(mime, MEDIA_MIMETYPE_VIDEO_MPEG4)) {
							meta->setInt32(kKeyCodecInfoIsInFirstFrame,true);
						} else if(!strcmp(mime, MEDIA_MIMETYPE_VIDEO_MPEG2)){
							meta->setInt32(kKeyCodecInfoIsInFirstFrame,true);
						}else if(!strcmp(mime, MEDIA_MIMETYPE_VIDEO_AVC)){
							meta->setInt32(kKeyCodecInfoIsInFirstFrame,true);
						}
#ifdef MTK_VIDEO_HEVC_SUPPORT
						else if(!strcmp(mime, MEDIA_MIMETYPE_VIDEO_HEVC)){
							meta->setInt32(kKeyCodecInfoIsInFirstFrame,true);
						}
#endif					
					}
				}
#endif
				else {
                    ALOGW("%s is not supported.", codecID);
#ifndef ANDROID_DEFAULT_CODE
					mFileMetaData->setInt32(kKeyHasUnsupportVideo,true);
#endif
                    continue;
                }

                meta->setInt32(kKeyWidth, vtrack->GetWidth());
                meta->setInt32(kKeyHeight, vtrack->GetHeight());
                break;
            }

            case AUDIO_TRACK:
            {
                const mkvparser::AudioTrack *atrack =
                static_cast<const mkvparser::AudioTrack *>(track);
#ifndef ANDROID_DEFAULT_CODE
                if (!strncasecmp("A_AAC", codecID, 5)) {
					unsigned char aacCodecInfo[2]={0, 0};
					if (codecPrivateSize >= 2) {

					} else if (NULL == codecPrivate) {
					
						if (!strcasecmp("A_AAC", codecID)) {
							ALOGW("Unspport AAC: No profile");
							continue;
						}
						else  {
							uint8_t freq_index=-1;
							uint8_t profile;
							if (!findAACSampleFreqIndex((uint32_t)atrack->GetSamplingRate(), freq_index)) {
								ALOGE("Unsupport AAC freq");
								continue;
							}

							if (!strcasecmp("A_AAC/MPEG4/LC", codecID) ||
								!strcasecmp("A_AAC/MPEG2/LC", codecID))
								profile = 2;
							else if (!strcasecmp("A_AAC/MPEG4/LC/SBR", codecID) ||
								!strcasecmp("A_AAC/MPEG2/LC/SBR", codecID))
								profile = 5;
							else if (!strcasecmp("A_AAC/MPEG4/LTP", codecID))
								profile = 4;
							else {
								ALOGE("Unsupport AAC Codec profile %s", codecID);
								continue;
							}

							codecPrivate = aacCodecInfo;
							codecPrivateSize = 2;
							aacCodecInfo[0] |= (profile << 3) & 0xf1;   // put it into the highest 5 bits
							aacCodecInfo[0] |= ((freq_index >> 1) & 0x07);	   // put 3 bits		
							aacCodecInfo[1] |= ((freq_index << 7) & 0x80); // put 1 bit	  
							aacCodecInfo[1] |= ((unsigned char)atrack->GetChannels()<< 3);
							ALOGD("Make codec info 0x%x, 0x%x", aacCodecInfo[0], aacCodecInfo[1]);
							
						}
					}else {
						ALOGE("Incomplete AAC Codec Info %d byte", codecPrivateSize);
						continue;
					}
					addESDSFromCodecPrivate(meta, true, codecPrivate, codecPrivateSize);

					meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AAC);
					ALOGD("Audio Codec: %s", codecID);
        		}
#else
                if (!strcmp("A_AAC", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AAC);
                    CHECK(codecPrivateSize >= 2);

                    addESDSFromCodecPrivate(
                            meta, true, codecPrivate, codecPrivateSize);
                }
#endif 
				else if (!strcmp("A_VORBIS", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_VORBIS);

                    err = addVorbisCodecInfo(
                            meta, codecPrivate, codecPrivateSize);
#ifndef ANDROID_DEFAULT_CODE
					ALOGD("Audio Codec: VORBIS,addVorbisCodecInfo return err=%d",err);
#endif
                } 
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_DDPLUS_SUPPORT
                else if (!strcmp("A_AC3", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AC3);
                }
                else if (!strcmp("A_EAC3", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_EC3);
                }
#endif
#ifdef MTK_AUDIO_RAW_SUPPORT
				else if(!strcmp("A_PCM/INT/LIT", codecID)){
					meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);
					meta->setInt32(kKeyEndian, 2);
					meta->setInt32(kKeyBitWidth, atrack->GetBitDepth());
					meta->setInt32(kKeyPCMType, 1);
					if(atrack->GetBitDepth()==8)
						meta->setInt32(kKeyNumericalType, 2);
				}
				else if(!strcmp("A_PCM/INT/BIG", codecID)){
					meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);
					meta->setInt32(kKeyEndian, 1);
					meta->setInt32(kKeyBitWidth, atrack->GetBitDepth());
					meta->setInt32(kKeyPCMType, 1);
					if(atrack->GetBitDepth()==8)
						meta->setInt32(kKeyNumericalType, 2);
				}
#endif
				else if ((!strcmp("A_MPEG/L1", codecID)) ||
						 (!strcmp("A_MPEG/L2", codecID)) ||
						(!strcmp("A_MPEG/L3", codecID))){
					meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG);
					ALOGD("Audio Codec: MPEG");
					if (atrack->GetChannels() > 2) {
						ALOGE("Unsupport MP3 Channel count=%lld", atrack->GetChannels());
						continue;
					}
					if ((atrack->GetSamplingRate() < 0.1) || (atrack->GetChannels() == 0))
					{
						meta->setInt32(kKeyCodecInfoIsInFirstFrame,true);
					}
				}
				else if ((!strcmp("A_MS/ACM", codecID))) {
					if ((NULL == codecPrivate) || (codecPrivateSize < 8)) {
						ALOGE("Unsupport audio: A_MS/ACM has no invalid private data, codecPrivate=%p, codecPrivateSize=%d", codecPrivate, codecPrivateSize);
						continue;
					}
					
					else {
						uint16_t ID = *(uint16_t *)codecPrivate;
						const char* mime = MKVwave2MIME(ID);
						ALOGD("A_MS/ACM type is %s", mime);
						if (!strncasecmp("audio/", mime, 6)) {
							meta->setCString(kKeyMIMEType, mime);
						} else {
							ALOGE("A_MS/ACM continue");
							continue;
						}
#if defined(MTK_AUDIO_ADPCM_SUPPORT) || defined(HAVE_ADPCMENCODE_FEATURE) 
						if((!strcmp(mime, MEDIA_MIMETYPE_AUDIO_DVI_IMA_ADPCM))||(!strcmp(mime, MEDIA_MIMETYPE_AUDIO_MS_ADPCM))){
							uint32_t channel_count = *(uint16_t*)(codecPrivate+2);
							uint32_t sample_rate = *(uint32_t*)(codecPrivate+4);
							uint32_t BlockAlign= *(uint16_t*)(codecPrivate+12);
							uint32_t BitesPerSample=*(uint16_t*)(codecPrivate+14);
							uint32_t cbSize=*(uint16_t*)(codecPrivate+16);
							ALOGD("channel_count=%d,sample_rate=%lld,BlockAlign=%d,BitesPerSampe=%d,cbSize=%d",
								channel_count,sample_rate,BlockAlign,BitesPerSample,cbSize);
							meta->setInt32(kKeySampleRate, sample_rate);
							meta->setInt32(kKeyChannelCount, channel_count);
							meta->setInt32(kKeyBlockAlign, BlockAlign);
							meta->setInt32(kKeyBitsPerSample, BitesPerSample);
							meta->setData(kKeyExtraDataPointer, 0, codecPrivate+18, cbSize);
						}
#endif
						if(!strcmp(mime, MEDIA_MIMETYPE_AUDIO_WMA)){
						meta->setData(kKeyWMAC, 0, codecPrivate, codecPrivateSize);
						}

#ifdef MTK_AUDIO_RAW_SUPPORT
						if(!strcmp(mime, MEDIA_MIMETYPE_AUDIO_RAW)){
						uint32_t BitesPerSample=*(uint16_t*)(codecPrivate+14);
						meta->setInt32(kKeyBitWidth, BitesPerSample);
						meta->setInt32(kKeyEndian, 2);
						meta->setInt32(kKeyPCMType, 1);	
						if(BitesPerSample==8)
							meta->setInt32(kKeyNumericalType, 2);
						}	
#endif
					}

				}
#else 
				else if (!strcmp("A_MPEG/L3", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG);
                } 
#endif 
				else {
                    ALOGW("%s is not supported.", codecID);
                    continue;
                }

                meta->setInt32(kKeySampleRate, atrack->GetSamplingRate());
                meta->setInt32(kKeyChannelCount, atrack->GetChannels());
#ifndef ANDROID_DEFAULT_CODE
				ALOGD("Samplerate=%f, channelcount=%lld", atrack->GetSamplingRate(), atrack->GetChannels());
				meta->setInt32(kKeyMaxInputSize, 16384);
				hasAudio = true;
#if defined(MTK_AUDIO_CHANGE_SUPPORT) || defined(MTK_SUBTITLE_SUPPORT)
                if(track->GetLanguageAsUTF8()!=NULL)
                    meta->setCString(kKeyMediaLanguage, track->GetLanguageAsUTF8());
#endif
#endif
                break;
            }
#ifdef MTK_SUBTITLE_SUPPORT
				case SUBTT_TRACK:
				{
					uint32_t j=0;
					for(j = 0; j<codecPrivateSize; j++)
					{
						ALOGE("dump codecPrivate[%d]  %02x",j,*(uint8_t*)(codecPrivate+j));
					}
			
					const char* mimeSubTT = BMapCodecId2SubTT(codecID);
					
					meta->setCString(kKeyMIMEType, mimeSubTT);
					meta->setData(kKeyTextFormatData, 0, codecPrivate, codecPrivateSize);
					break;
				}
#endif

            default:
                continue;
        }

        if (err != OK) {
            ALOGE("skipping track, codec specific data was malformed.");
            continue;
        }

        long long durationNs = mSegment->GetDuration();
        meta->setInt64(kKeyDuration, (durationNs + 500) / 1000);

        mTracks.push();
        TrackInfo *trackInfo = &mTracks.editItemAt(mTracks.size() - 1);
        trackInfo->mTrackNum = track->GetNumber();
        trackInfo->mMeta = meta;
#ifndef ANDROID_DEFAULT_CODE
		trackInfo->mTrack = track;
		if (!hasVideo && hasAudio){
			//mFileMetaData->setCString(kKeyMIMEType, "audio/x-matroska");
			mFileMetaData->setCString(
					            kKeyMIMEType,
					            mIsWebm ? "audio/webm" : "audio/x-matroska");
			ALOGI("MatroskaExtractor::addTracks,only audio,is %s",mIsWebm ? "audio/webm" : "audio/x-matroska");
		}
		else{
			//mFileMetaData->setCString(kKeyMIMEType, "video/x-matroska");
			mFileMetaData->setCString(
					            kKeyMIMEType,
					            mIsWebm ? "video/webm" : "video/x-matroska");
			ALOGI("MatroskaExtractor::addTracks,has video and audio,is %s",mIsWebm ? "video/webm" : "video/x-matroska");
	
	}
#endif
    }
}

void MatroskaExtractor::findThumbnails() {
    for (size_t i = 0; i < mTracks.size(); ++i) {
        TrackInfo *info = &mTracks.editItemAt(i);

        const char *mime;
        CHECK(info->mMeta->findCString(kKeyMIMEType, &mime));

        if (strncasecmp(mime, "video/", 6)) {
            continue;
        }

        BlockIterator iter(this, info->mTrackNum);
        int32_t j = 0;
        int64_t thumbnailTimeUs = 0;
        size_t maxBlockSize = 0;
        while (!iter.eos() && j < 20) {
            if (iter.block()->IsKey()) {
                ++j;

                size_t blockSize = 0;
                for (int k = 0; k < iter.block()->GetFrameCount(); ++k) {
                    blockSize += iter.block()->GetFrame(k).len;
                }

                if (blockSize > maxBlockSize) {
                    maxBlockSize = blockSize;
                    thumbnailTimeUs = iter.blockTimeUs();
                }
            }
            iter.advance();
        }
        info->mMeta->setInt64(kKeyThumbnailTime, thumbnailTimeUs);
    }
}

sp<MetaData> MatroskaExtractor::getMetaData() {
#ifndef ANDROID_DEFAULT_CODE
	if(mFileMetaData != NULL){
		mFileMetaData->setCString(
			kKeyMIMEType,
			mIsWebm ? "video/webm" : "video/x-matroska");
		ALOGE("getMetaData , %s",mIsWebm ? "video/webm" : "video/x-matroska");
	}
	return mFileMetaData;
#else
    sp<MetaData> meta = new MetaData;

    meta->setCString(
            kKeyMIMEType,
            mIsWebm ? "video/webm" : MEDIA_MIMETYPE_CONTAINER_MATROSKA);

    return meta;
#endif
}

uint32_t MatroskaExtractor::flags() const {
    uint32_t x = CAN_PAUSE;
    if (!isLiveStreaming()) {
        x |= CAN_SEEK_BACKWARD | CAN_SEEK_FORWARD | CAN_SEEK;
    }

    return x;
}

bool SniffMatroska(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *) {
    DataSourceReader reader(source);
    mkvparser::EBMLHeader ebmlHeader;
    long long pos;
    if (ebmlHeader.Parse(&reader, pos) < 0) {
        return false;
    }

    mimeType->setTo(MEDIA_MIMETYPE_CONTAINER_MATROSKA);
    *confidence = 0.6;

    return true;
}

}  // namespace android
