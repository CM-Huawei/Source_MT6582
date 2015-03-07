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

//#define LOG_NDEBUG 0
#define LOG_TAG "MPEG4Extractor"
#include <utils/Log.h>

#include "include/MPEG4Extractor.h"
#include "include/SampleTable.h"
#include "include/ESDS.h"

#ifdef MTK_PLAYREADY_SUPPORT
#include <arpa/inet.h>
#include <media/stagefright/foundation/base64.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/Utils.h>
#endif
#include <ctype.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <media/stagefright/foundation/ABitReader.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <utils/String8.h>
#ifndef ANDROID_DEFAULT_CODE
#include <sys/sysconf.h>
#include <asm/page.h>


//#include <m4v_config_parser.h>
#include "include/avc_utils.h"

#include "include/ESDS.h"
#define QUICKTIME_SUPPORT
#endif
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_STAGEFRIGHT_USE_XLOG
#include <cutils/xlog.h>
#undef ALOGV
#define ALOGV XLOGV
#endif
#endif
namespace android {
#ifndef ANDROID_DEFAULT_CODE
//static int32_t VIDEO_MAX_FPS = 120;
static int32_t AVC_MAX_MACRO_PER_SECOND = 108000;//LEVEL 3.1

#endif


#ifndef ANDROID_DEFAULT_CODE
const static int64_t kZeroBufTimeOutUs = 3000000LL;    //  handle the zero data
#endif
class MPEG4Source : public MediaSource {
public:
    // Caller retains ownership of both "dataSource" and "sampleTable".
    MPEG4Source(const sp<MetaData> &format,
                const sp<DataSource> &dataSource,
                int32_t timeScale,
                const sp<SampleTable> &sampleTable,
                Vector<SidxEntry> &sidx,
#ifdef MTK_PLAYREADY_SUPPORT
                Vector<MfraEntry> &mfra,
#endif
                off64_t firstMoofOffset);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();

    virtual sp<MetaData> getFormat();

    virtual status_t read(MediaBuffer **buffer, const ReadOptions *options = NULL);
    virtual status_t fragmentedRead(MediaBuffer **buffer, const ReadOptions *options = NULL);

#ifndef ANDROID_DEFAULT_CODE
	uint64_t getStartTimeOffsetUs()
	{
		return (uint64_t)mSampleTable->getStartTimeOffset()*mTimescale*1000000;
	}

    virtual bool setDataSource(const sp<DataSource> &dataSource);
#endif

protected:
    virtual ~MPEG4Source();

private:
    Mutex mLock;

    sp<MetaData> mFormat;
    sp<DataSource> mDataSource;
    int32_t mTimescale;
    sp<SampleTable> mSampleTable;
    uint32_t mCurrentSampleIndex;
    uint32_t mCurrentFragmentIndex;
    Vector<SidxEntry> &mSegments;
#ifdef MTK_PLAYREADY_SUPPORT
    Vector<MfraEntry> &mMovieFragments;
#endif
    off64_t mFirstMoofOffset;
    off64_t mCurrentMoofOffset;
    off64_t mNextMoofOffset;
    uint32_t mCurrentTime;
    int32_t mLastParsedTrackId;
    int32_t mTrackId;

    int32_t mCryptoMode;    // passed in from extractor
    int32_t mDefaultIVSize; // passed in from extractor
    uint8_t mCryptoKey[16]; // passed in from extractor
    uint32_t mCurrentAuxInfoType;
    uint32_t mCurrentAuxInfoTypeParameter;
    int32_t mCurrentDefaultSampleInfoSize;
    uint32_t mCurrentSampleInfoCount;
    uint32_t mCurrentSampleInfoAllocSize;
    uint8_t* mCurrentSampleInfoSizes;
    uint32_t mCurrentSampleInfoOffsetCount;
    uint32_t mCurrentSampleInfoOffsetsAllocSize;
    uint64_t* mCurrentSampleInfoOffsets;

    bool mIsAVC;
    size_t mNALLengthSize;

    bool mStarted;

    MediaBufferGroup *mGroup;

    MediaBuffer *mBuffer;

    bool mWantsNALFragments;
#ifndef ANDROID_DEFAULT_CODE
	int64_t mZeroBufStart;
#endif

    uint8_t *mSrcBuffer;

#ifdef MTK_PLAYREADY_SUPPORT
    struct CodecSpecificData {
        size_t mSize;
        uint8_t mData[1];
    };
    Vector<CodecSpecificData *> mCodecSpecificData;
    size_t mCodecSpecificDataIndex;
#endif
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_RAW_SUPPORT
    status_t pcmread(MediaBuffer **out); 
#endif
#endif
    size_t parseNALSize(const uint8_t *data) const;
#ifdef MTK_PLAYREADY_SUPPORT
    status_t parseChunk(off64_t *offset, MfraEntry *mfra = NULL);
#else
    status_t parseChunk(off64_t *offset);
#endif
    status_t parseTrackFragmentHeader(off64_t offset, off64_t size);
#ifdef MTK_PLAYREADY_SUPPORT
    status_t parseTrackFragmentRun(off64_t offset, off64_t size, uint32_t sampleNum=0);
#else
    status_t parseTrackFragmentRun(off64_t offset, off64_t size);
#endif
    status_t parseSampleAuxiliaryInformationSizes(off64_t offset, off64_t size);
    status_t parseSampleAuxiliaryInformationOffsets(off64_t offset, off64_t size);

#ifdef MTK_PLAYREADY_SUPPORT
    status_t parseSampleUUid(off64_t offset, off64_t size);
    status_t parseAVCCodecSpecificData(
            const void *data, size_t size,
            unsigned *profile, unsigned *level);
    void addCodecSpecificData(const void *data, size_t size);
    void clearCodecSpecificData();
#endif
    struct TrackFragmentHeaderInfo {
        enum Flags {
            kBaseDataOffsetPresent         = 0x01,
            kSampleDescriptionIndexPresent = 0x02,
            kDefaultSampleDurationPresent  = 0x08,
            kDefaultSampleSizePresent      = 0x10,
            kDefaultSampleFlagsPresent     = 0x20,
            kDurationIsEmpty               = 0x10000,
        };

        uint32_t mTrackID;
        uint32_t mFlags;
        uint64_t mBaseDataOffset;
        uint32_t mSampleDescriptionIndex;
        uint32_t mDefaultSampleDuration;
        uint32_t mDefaultSampleSize;
        uint32_t mDefaultSampleFlags;

        uint64_t mDataOffset;
    };
    TrackFragmentHeaderInfo mTrackFragmentHeaderInfo;

    struct Sample {
        off64_t offset;
        size_t size;
        uint32_t duration;
#ifdef MTK_PLAYREADY_SUPPORT
        uint8_t *playReadyIV;        // playready iv would contain more info, e.g. clear data info 
        size_t IVSize;
#endif
        uint8_t iv[16];
        Vector<size_t> clearsizes;
        Vector<size_t> encryptedsizes;
    };
    Vector<Sample> mCurrentSamples;

    MPEG4Source(const MPEG4Source &);
    MPEG4Source &operator=(const MPEG4Source &);
};

// This custom data source wraps an existing one and satisfies requests
// falling entirely within a cached range from the cache while forwarding
// all remaining requests to the wrapped datasource.
// This is used to cache the full sampletable metadata for a single track,
// possibly wrapping multiple times to cover all tracks, i.e.
// Each MPEG4DataSource caches the sampletable metadata for a single track.

struct MPEG4DataSource : public DataSource {
    MPEG4DataSource(const sp<DataSource> &source);

    virtual status_t initCheck() const;
    virtual ssize_t readAt(off64_t offset, void *data, size_t size);
    virtual status_t getSize(off64_t *size);
    virtual uint32_t flags();

    status_t setCachedRange(off64_t offset, size_t size);

protected:
    virtual ~MPEG4DataSource();

private:
    Mutex mLock;

    sp<DataSource> mSource;
    off64_t mCachedOffset;
    size_t mCachedSize;
    uint8_t *mCache;

    void clearCache();

    MPEG4DataSource(const MPEG4DataSource &);
    MPEG4DataSource &operator=(const MPEG4DataSource &);
};

MPEG4DataSource::MPEG4DataSource(const sp<DataSource> &source)
    : mSource(source),
      mCachedOffset(0),
      mCachedSize(0),
      mCache(NULL) {
}

MPEG4DataSource::~MPEG4DataSource() {
    clearCache();
}

void MPEG4DataSource::clearCache() {
    if (mCache) {
        free(mCache);
        mCache = NULL;
    }

    mCachedOffset = 0;
    mCachedSize = 0;
}

status_t MPEG4DataSource::initCheck() const {
    return mSource->initCheck();
}

ssize_t MPEG4DataSource::readAt(off64_t offset, void *data, size_t size) {
    Mutex::Autolock autoLock(mLock);

    if (offset >= mCachedOffset
            && offset + size <= mCachedOffset + mCachedSize) {
        memcpy(data, &mCache[offset - mCachedOffset], size);
        return size;
    }

    return mSource->readAt(offset, data, size);
}

status_t MPEG4DataSource::getSize(off64_t *size) {
    return mSource->getSize(size);
}

uint32_t MPEG4DataSource::flags() {
    return mSource->flags();
}

status_t MPEG4DataSource::setCachedRange(off64_t offset, size_t size) {
    Mutex::Autolock autoLock(mLock);

    clearCache();

    mCache = (uint8_t *)malloc(size);

    if (mCache == NULL) {
        return -ENOMEM;
    }

    mCachedOffset = offset;
    mCachedSize = size;

    ssize_t err = mSource->readAt(mCachedOffset, mCache, mCachedSize);

    if (err < (ssize_t)size) {
        clearCache();

        return ERROR_IO;
    }

    return OK;
}

////////////////////////////////////////////////////////////////////////////////

static void hexdump(const void *_data, size_t size) {
    const uint8_t *data = (const uint8_t *)_data;
    size_t offset = 0;
    while (offset < size) {
        printf("0x%04x  ", offset);

        size_t n = size - offset;
        if (n > 16) {
            n = 16;
        }

        for (size_t i = 0; i < 16; ++i) {
            if (i == 8) {
                printf(" ");
            }

            if (offset + i < size) {
                printf("%02x ", data[offset + i]);
            } else {
                printf("   ");
            }
        }

        printf(" ");

        for (size_t i = 0; i < n; ++i) {
            if (isprint(data[offset + i])) {
                printf("%c", data[offset + i]);
            } else {
                printf(".");
            }
        }

        printf("\n");

        offset += 16;
    }
}

static const char *FourCC2MIME(uint32_t fourcc) {
    switch (fourcc) {
        case FOURCC('m', 'p', '4', 'a'):
            return MEDIA_MIMETYPE_AUDIO_AAC;

        case FOURCC('s', 'a', 'm', 'r'):
            return MEDIA_MIMETYPE_AUDIO_AMR_NB;

        case FOURCC('s', 'a', 'w', 'b'):
            return MEDIA_MIMETYPE_AUDIO_AMR_WB;

        case FOURCC('m', 'p', '4', 'v'):
            return MEDIA_MIMETYPE_VIDEO_MPEG4;

        case FOURCC('s', '2', '6', '3'):
        case FOURCC('h', '2', '6', '3'):
        case FOURCC('H', '2', '6', '3'):
            return MEDIA_MIMETYPE_VIDEO_H263;

        case FOURCC('a', 'v', 'c', '1'):
            return MEDIA_MIMETYPE_VIDEO_AVC;
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_VIDEO_HEVC_SUPPORT
		case FOURCC('h', 'v', 'c', '1'):
			return MEDIA_MIMETYPE_VIDEO_HEVC;
#endif
        case FOURCC('D', 'X', '5', '0'):
            return MEDIA_MIMETYPE_VIDEO_DIVX;
        case FOURCC('j', 'p', 'e', 'g'):
            return MEDIA_MIMETYPE_VIDEO_MJPEG;
		case FOURCC('.', 'm', 'p', '3'):
		case 0x6D730055:
			return MEDIA_MIMETYPE_AUDIO_MPEG;
#ifdef MTK_AUDIO_RAW_SUPPORT
		case FOURCC('r', 'a', 'w', ' '):
		case FOURCC('t', 'w', 'o', 's'):
		case FOURCC('i', 'n', '2', '4'):
		case FOURCC('i', 'n', '3', '2'):
		case FOURCC('s', 'o', 'w', 't'):
            return MEDIA_MIMETYPE_AUDIO_RAW;
#endif
#ifdef MTK_AUDIO_DDPLUS_SUPPORT
        case FOURCC('a', 'c', '-', '3'):
            return MEDIA_MIMETYPE_AUDIO_AC3;
        case FOURCC('e', 'c', '-', '3'):
            return MEDIA_MIMETYPE_AUDIO_EC3;
#endif
#endif

        default:
            CHECK(!"should not be here.");
            return NULL;
    }
}

static bool AdjustChannelsAndRate(uint32_t fourcc, uint32_t *channels, uint32_t *rate) {
    if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_AMR_NB, FourCC2MIME(fourcc))) {
        // AMR NB audio is always mono, 8kHz
        *channels = 1;
        *rate = 8000;
        return true;
    } else if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_AMR_WB, FourCC2MIME(fourcc))) {
        // AMR WB audio is always mono, 16kHz
        *channels = 1;
        *rate = 16000;
        return true;
    }
    return false;
}

MPEG4Extractor::MPEG4Extractor(const sp<DataSource> &source)
    : mSidxDuration(0),
      mMoofOffset(0),
      mDataSource(source),
      mInitCheck(NO_INIT),
      mHasVideo(false),
      mHeaderTimescale(0),
#ifndef ANDROID_DEFAULT_CODE
      mHasAudio(false),
#ifdef MTK_PLAYREADY_SUPPORT
      mIsPlayReady(false),
#endif
#endif
      mFirstTrack(NULL),
      mLastTrack(NULL),
      mFileMetaData(new MetaData),
      mFirstSINF(NULL),
      mIsDrm(false) {
#ifndef ANDROID_DEFAULT_CODE
	ALOGD("=====================================\n"); 
   ALOGD("[MP4 Playback capability info]£º\n"); 
    ALOGD("=====================================\n"); 
    ALOGD("Resolution = \"[(8,8) ~ (864£¬480)]\" \n"); 
    ALOGD("Support Codec = \"Video:MPEG4, H263, H264 ; Audio: AAC, AMR-NB/WB\" \n"); 
    ALOGD("Profile_Level = \"MPEG4: Simple Profile ; H263: Baseline ; H264: Baseline/3.1, Main/3.1\" \n"); 
    ALOGD("Max frameRate =  120fps \n"); 
    ALOGD("Max Bitrate  = H264: 6Mbps  (720*480@30fps) ; MPEG4/H263: 20Mbps (864*480@30fps)\n"); 
    ALOGD("=====================================\n"); 
#endif
}

MPEG4Extractor::~MPEG4Extractor() {
    Track *track = mFirstTrack;
    while (track) {
        Track *next = track->next;

        delete track;
        track = next;
    }
    mFirstTrack = mLastTrack = NULL;

    SINF *sinf = mFirstSINF;
    while (sinf) {
        SINF *next = sinf->next;
        delete sinf->IPMPData;
        delete sinf;
        sinf = next;
    }
    mFirstSINF = NULL;

    for (size_t i = 0; i < mPssh.size(); i++) {
        delete [] mPssh[i].data;
    }
}

uint32_t MPEG4Extractor::flags() const {
#ifdef MTK_PLAYREADY_SUPPORT
    return CAN_PAUSE |
            ((mMoofOffset == 0 || mSidxEntries.size() != 0 || mMfraEntries.size() != 0) ?
                    (CAN_SEEK_BACKWARD | CAN_SEEK_FORWARD | CAN_SEEK) : 0);
#else
    return CAN_PAUSE |
            ((mMoofOffset == 0 || mSidxEntries.size() != 0) ?
                    (CAN_SEEK_BACKWARD | CAN_SEEK_FORWARD | CAN_SEEK) : 0);
#endif
}
#ifndef ANDROID_DEFAULT_CODE
// set new dataSource for mpeg4Source 
void MPEG4Extractor::changeDataSource(const sp<MediaSource> &mp4Source, const sp<DataSource> &dataSource) {
    reinterpret_cast<MPEG4Source *>(mp4Source.get())->setDataSource(dataSource);
}
#endif

sp<MetaData> MPEG4Extractor::getMetaData() {
    status_t err;
    if ((err = readMetaData()) != OK) {
        return new MetaData;
    }
#ifndef ANDROID_DEFAULT_CODE
    // set flag for handle the case: video too long to audio
    mFileMetaData->setInt32(kKeyVideoPreCheck, 1);
#endif
    return mFileMetaData;
}

size_t MPEG4Extractor::countTracks() {
    status_t err;
    if ((err = readMetaData()) != OK) {
        ALOGV("MPEG4Extractor::countTracks: no tracks");
        return 0;
    }

    size_t n = 0;
#ifndef ANDROID_DEFAULT_CODE
		size_t timeOffsetTrackNum = 0;
		Track *timeOffsetTrack1 = NULL;
		Track *timeOffsetTrack2 = NULL;
#endif
    Track *track = mFirstTrack;
    while (track) {
        ++n;
#ifndef ANDROID_DEFAULT_CODE
				if (track->mStartTimeOffset != 0)
				{
					timeOffsetTrackNum++;
					if (timeOffsetTrackNum > 2)
					{
						ALOGW("Unsupport edts list, %d tracks have time offset!!", timeOffsetTrackNum);
						track->mStartTimeOffset = 0;
						timeOffsetTrack1->mStartTimeOffset = 0;
						timeOffsetTrack2->mStartTimeOffset = 0;
					}
					else
					{
						if (timeOffsetTrack1 == NULL)
							timeOffsetTrack1 = track;
						else
						{
							timeOffsetTrack2 = track;
							if (timeOffsetTrack1->mStartTimeOffset > track->mStartTimeOffset)
							{
								timeOffsetTrack1->mStartTimeOffset -= track->mStartTimeOffset;
								track->mStartTimeOffset = 0;
							}
							else
							{
								track->mStartTimeOffset -= timeOffsetTrack1->mStartTimeOffset;
								timeOffsetTrack1->mStartTimeOffset = 0;
							}
						}
					}
				}
#endif
        track = track->next;
    }

    ALOGV("MPEG4Extractor::countTracks: %d tracks", n);
    return n;
}

sp<MetaData> MPEG4Extractor::getTrackMetaData(
        size_t index, uint32_t flags) {
    status_t err;
    if ((err = readMetaData()) != OK) {
        return NULL;
    }

    Track *track = mFirstTrack;
    while (index > 0) {
        if (track == NULL) {
            return NULL;
        }

        track = track->next;
        --index;
    }

    if (track == NULL) {
        return NULL;
    }

    if ((flags & kIncludeExtensiveMetaData)
            && !track->includes_expensive_metadata) {
        track->includes_expensive_metadata = true;

        const char *mime;
        CHECK(track->meta->findCString(kKeyMIMEType, &mime));
        if (!strncasecmp("video/", mime, 6)) {
            if (mMoofOffset > 0) {
                int64_t duration;
                if (track->meta->findInt64(kKeyDuration, &duration)) {
                    // nothing fancy, just pick a frame near 1/4th of the duration
                    track->meta->setInt64(
                            kKeyThumbnailTime, duration / 4);
                }
            } else {
                uint32_t sampleIndex;
                uint32_t sampleTime;
                if (track->sampleTable->findThumbnailSample(&sampleIndex) == OK
                        && track->sampleTable->getMetaDataForSample(
                            sampleIndex, NULL /* offset */, NULL /* size */,
                            &sampleTime) == OK) {
#ifndef ANDROID_DEFAULT_CODE//hai.li for Issue: ALPS32414
					if (mHeaderTimescale != 0)
						track->sampleTable->setStartTimeOffset((track->mStartTimeOffset/mHeaderTimescale)*track->timescale);
						track->meta->setInt64(
							kKeyThumbnailTime,((int64_t)sampleTime * 1000000 + (track->timescale >> 1)) / track->timescale 
							+ ((int64_t)track->sampleTable->getStartTimeOffset())*1000000/track->timescale);
#else
                track->meta->setInt64(
                        kKeyThumbnailTime,
                        ((int64_t)sampleTime * 1000000) / track->timescale);
#endif
                }
            }
        }
    }
#ifndef ANDROID_DEFAULT_CODE
	if (flags & kIncludeInterleaveInfo)
	{
		off64_t offset = 0;
		track->sampleTable->getMetaDataForSample(0, &offset, NULL, NULL, NULL);
		track->meta->setInt64(kKeyFirstSampleOffset, offset);
		ALOGD("First sample offset in %s track is %lld", track->mIsVideo?"Video":"Audio", (int64_t)offset);

		offset = 0;
		track->sampleTable->getMetaDataForSample(track->sampleTable->getSampleCount()-1, &offset, NULL, NULL, NULL);
		track->meta->setInt64(kKeyLastSampleOffset, offset);
		ALOGD("Last sample offset in %s track is %lld", track->mIsVideo?"Video":"Audio", (int64_t)offset);
	}
#endif

    return track->meta;
}

static void MakeFourCCString(uint32_t x, char *s) {
    s[0] = x >> 24;
    s[1] = (x >> 16) & 0xff;
    s[2] = (x >> 8) & 0xff;
    s[3] = x & 0xff;
    s[4] = '\0';
}

status_t MPEG4Extractor::readMetaData() {
    if (mInitCheck != NO_INIT) {
        return mInitCheck;
    }

    off64_t offset = 0;
    status_t err;
    while (true) {
        err = parseChunk(&offset, 0);
        if (err == OK) {
            continue;
        }

        uint32_t hdr[2];
        if (mDataSource->readAt(offset, hdr, 8) < 8) {
            break;
        }
        uint32_t chunk_type = ntohl(hdr[1]);
        if (chunk_type == FOURCC('s', 'i', 'd', 'x')) {
            // parse the sidx box too
            continue;
        } else if (chunk_type == FOURCC('m', 'o', 'o', 'f')) {
            // store the offset of the first segment
            mMoofOffset = offset;
        }
        break;
    }

#ifdef MTK_PLAYREADY_SUPPORT
    // if is PlayReady, set video track use secure buffer
    const char *mime = NULL;
    if (mIsPlayReady) {
	Track *track = mFirstTrack;
	while (true) {
	    if (track == NULL) {
		break;
	    } else {
#if defined(TRUSTONIC_TEE_SUPPORT) && defined(MTK_SEC_VIDEO_PATH_SUPPORT) 
		CHECK(track->meta->findCString(kKeyMIMEType, &mime));
		if (!strncasecmp("video/", mime, 6)) {
		    ALOGI("playReady video track set kKeyRequiresSecureBuffers");
		    track->meta->setInt32(kKeyRequiresSecureBuffers, true);
		}
#endif
                track->meta->setInt32(kKeyIsPlayReady, mIsPlayReady);
	    }
	    track = track->next;
	}
    }
   
    // parse mfra box
    int64_t fileSize = 0;
    status_t ret = mDataSource->getSize(&fileSize);
    if(ret != OK) {
        ALOGE("getSize fail ret:%d", ret);
        return ret;
    }

    uint32_t hdr[2];
    if (mDataSource->readAt(fileSize-16, hdr, 8) < 8) {       //According to spec, mfro box is here
	return ERROR_IO;
    }

    uint32_t chunk_type = ntohl(hdr[1]);
    uint32_t mfraSize = 0;
    if (chunk_type == FOURCC('m', 'f', 'r', 'o')) {
	mDataSource->getUInt32(fileSize-4, &mfraSize);
	ALOGI("mfraSize:%d", mfraSize);

	// parser mfra box, set seek table
	offset = fileSize - mfraSize;
	parseChunk(&offset, 0);           // only parser mfra box
    }
#endif

    if (mInitCheck == OK) {
#ifndef ANDROID_DEFAULT_CODE//hai.li
		if (mHasAudio && !mHasVideo) {
			int32_t isOtherBrand = 0;
			if (mFileMetaData->findInt32(kKeyIs3gpBrand, &isOtherBrand) && isOtherBrand)
			{				
				ALOGD("File Type is audio/3gpp");
				mFileMetaData->setCString(kKeyMIMEType, "audio/3gpp");
			}
#ifdef QUICKTIME_SUPPORT
			else if (mFileMetaData->findInt32(kKeyIsQTBrand, &isOtherBrand) && isOtherBrand)
			{				
				ALOGD("File Type is audio/quicktime");
				mFileMetaData->setCString(kKeyMIMEType, "audio/quicktime");
			}
#endif
			else
			{
				ALOGD("File Type is audio/mp4");
				mFileMetaData->setCString(kKeyMIMEType, "audio/mp4");
			}
		} else {
			int32_t isOtherBrand = 0;
			if (mHasVideo && mFileMetaData->findInt32(kKeyIs3gpBrand, &isOtherBrand) && isOtherBrand)
			{				
				ALOGD("File Type is video/3gpp");
				mFileMetaData->setCString(kKeyMIMEType, "video/3gpp");
			}
#ifdef QUICKTIME_SUPPORT
			else if (mFileMetaData->findInt32(kKeyIsQTBrand, &isOtherBrand) && isOtherBrand)
			{				
				ALOGD("File Type is video/quicktime");
				mFileMetaData->setCString(kKeyMIMEType, "video/quicktime");
			}
#endif
			else
			{
				ALOGD("File Type is video/mp4");
				mFileMetaData->setCString(kKeyMIMEType, "video/mp4");
			}
		}

	     ALOGD("mHasVideo:%d, mHasAudio:%d", mHasVideo, mHasAudio );
#else
        if (mHasVideo) {
            mFileMetaData->setCString(
                    kKeyMIMEType, MEDIA_MIMETYPE_CONTAINER_MPEG4);
        } else {
            mFileMetaData->setCString(kKeyMIMEType, "audio/mp4");
        }
#endif
        mInitCheck = OK;
    } else {
        mInitCheck = err;
    }

    CHECK_NE(err, (status_t)NO_INIT);

    // copy pssh data into file metadata
    int psshsize = 0;
    for (size_t i = 0; i < mPssh.size(); i++) {
        psshsize += 20 + mPssh[i].datalen;
    }
    if (psshsize) {
        char *buf = (char*)malloc(psshsize);
        char *ptr = buf;
        for (size_t i = 0; i < mPssh.size(); i++) {
            memcpy(ptr, mPssh[i].uuid, 20); // uuid + length
            memcpy(ptr + 20, mPssh[i].data, mPssh[i].datalen);
            ptr += (20 + mPssh[i].datalen);
        }
        mFileMetaData->setData(kKeyPssh, 'pssh', buf, psshsize);
        free(buf);
    }
    return mInitCheck;
}

char* MPEG4Extractor::getDrmTrackInfo(size_t trackID, int *len) {
    if (mFirstSINF == NULL) {
        return NULL;
    }

    SINF *sinf = mFirstSINF;
#ifdef MTK_PLAYREADY_SUPPORT
    if (mIsPlayReady) {
         ALOGI("PlayReady use moov IMPMData, not track IPMData");
    } else {
	// playready use moov drm info
	while (sinf && (trackID != sinf->trackID)) {
	    sinf = sinf->next;
	}

	if (sinf == NULL) {
	    return NULL;
	}
    }
#else
    // playready use moov drm info
    while (sinf && (trackID != sinf->trackID)) {
	sinf = sinf->next;
    }

    if (sinf == NULL) {
	return NULL;
    }
#endif
    *len = sinf->len;
    return sinf->IPMPData;
}

// Reads an encoded integer 7 bits at a time until it encounters the high bit clear.
static int32_t readSize(off64_t offset,
        const sp<DataSource> DataSource, uint8_t *numOfBytes) {
    uint32_t size = 0;
    uint8_t data;
    bool moreData = true;
    *numOfBytes = 0;

    while (moreData) {
        if (DataSource->readAt(offset, &data, 1) < 1) {
            return -1;
        }
        offset ++;
        moreData = (data >= 128) ? true : false;
        size = (size << 7) | (data & 0x7f); // Take last 7 bits
        (*numOfBytes) ++;
    }

    return size;
}

#ifdef MTK_PLAYREADY_SUPPORT
void utf8_16(uint8_t* utf8, int utf8Size, uint16_t* utf16, size_t& utf16Size)
{
      // to do
}

status_t MPEG4Extractor::parseDrmSINFMOOV(off64_t *offset, off64_t data_offset) {
    uint8_t buf[40];        //extend type 16 + full version 4 + 
    if (mDataSource->readAt(
		data_offset, buf, sizeof(buf))
	    < (ssize_t)sizeof(buf)) {
	return ERROR_IO;
    }
    const uint8_t PlayReadySystemID[16] = {
	0x9A, 0x04, 0xF0, 0x79, 
	0x98, 0x40, 
	0x42, 0x86, 
	0xAB, 0x92, 
	0xE6, 0x5B, 0xE0, 0x88, 0x5F, 0x95};
    if (!strncasecmp((char *)PlayReadySystemID, (char *)&buf[20], 16)) {
        ALOGI("Is playready video");
	mIsPlayReady = true;
    }

    ALOGI("MOOV UUid extend_type:%02x %02x %02x %02x %02x %02x %02x %02x", buf[20], buf[21], buf[22], buf[23],
	    buf[24], buf[25], buf[26], buf[27]);

    uint32_t size = U32_AT(&buf[36]);
    uint8_t *data = new uint8_t[size];
    memset(data, 0, size);
    if (mDataSource->readAt(data_offset+40, data, size) != size) {
	return ERROR_IO;
    }

    // write play ready header
    AString header((char *)data, size);

    SINF *sinf = new SINF;
    int32_t trackid = 0;
    //CHECK(mLastTrack->meta->findInt32(kKeyTrackID, &trackid));
    sinf->trackID = 0;
    sinf->IPMPDescriptorID = 0;
     
    uint32_t playreadyLen = header.size();
    sinf->IPMPData = (char *)data;
    sinf->len = header.size();

/*
    ALOGI("header:%s\n len:%d\n", header.c_str(), header.size());
    FILE* fd = fopen("/sdcard/playread.txt","wb+");
    fwrite((void *)(sinf->IPMPData), 1, playreadyLen, fd);
    fclose(fd);
*/

    sinf->next = mFirstSINF;
    mFirstSINF = sinf;

    return OK;
}
status_t MPEG4Extractor::parseDrmSINFTrack(off64_t *offset, off64_t data_offset) {
    uint8_t buf[40];
    if (mDataSource->readAt(
		data_offset, buf, sizeof(buf))
	    < (ssize_t)sizeof(buf)) {
	return ERROR_IO;
    }
    ALOGI("extend_type:%02x %02x %02x %02x %02x %02x %02x %02x", buf[0], buf[1], buf[2], buf[4],
	    buf[12], buf[13], buf[14], buf[15]);

    uint32_t Algor = U32_AT(&buf[20]) >> 8;
    uint8_t IV_Size = buf[23];
    ALOGI("Algor:%d, IV_Size:%d", Algor, IV_Size);
    mLastTrack->meta->setInt32(kKeyCryptoDefaultIVSize, IV_Size);

    // encode KID for base64            
    uint8_t* tmp = &buf[24];
    ALOGI("KID:%02x %02x %02x %02x %02x %02x %02x %02x", tmp[0], tmp[1], tmp[2], tmp[4],
	    tmp[12], tmp[13], tmp[14], tmp[15]);
    AString out;
    encodeBase64(tmp, 16, &out);
    ALOGI("KID base64:%s", out.c_str());
    return OK;

    // write play ready header
#if 1
    AString header( 
	    "<WRMHEADER xmlns=\"http://schemas.microsoft.com/DRM/2007/03/PlayReadyHeader\" version=\"4.0.0.0\" >\n"
	    "<DATA>\n"
	    "      <PROTECTINFO>\n"
	    "        <KEYLEN>16</KEYLEN>\n"
	    "        <ALGID>AESCTR</ALGID>\n"
	    "      </PROTECTINFO>\n" 
	    "    <KID></KID>\n"
	    "  </DATA>\n"
	    "</WRMHEADER>\n");
    ALOGI("header:%s \n", header.c_str());
    uint32_t pos = header.find("<KID>");
    header.insert(out, pos+5);
    ALOGI("header:%s len:%d\n", header.c_str(), header.size());
#else
    AString header( 
"<WRMHEADER xmlns=\"http://schemas.microsoft.com/DRM/2007/03/PlayReadyHeader\" "
        "version=\"4.0.0.0\"><DATA><PROTECTINFO><KEYLEN>16</KEYLEN><ALGID>AESCTR</ALGID></PROTECTINFO>"
        "<KID>AmfjCTOPbEOl3WD/5mcecA==</KID><CHECKSUM>BGw1aYZ1YXM=</CHECKSUM><CUSTOMATTRIBUTES>"
        "<IIS_DRM_VERSION>7.1.1064.0</IIS_DRM_VERSION></CUSTOMATTRIBUTES>"
        "<LA_URL>http://playready.directtaps.net/pr/svc/rightsmanager.asmx</LA_URL>"
        "<DS_ID>AH+03juKbUGbHl1V/QIwRA==</DS_ID></DATA></WRMHEADER>");
#endif

    // header convert to utf16
    uint8_t *utf16 = new uint8_t[header.size()*2];
    size_t utf16Size = 0;
    utf8_16((uint8_t *)header.c_str(), header.size(), (uint16_t *)utf16, utf16Size);
    AString newHeader((char *)utf16, utf16Size);
    

    SINF *sinf = new SINF;
    int32_t trackid = 0;
    CHECK(mLastTrack->meta->findInt32(kKeyTrackID, &trackid));
    sinf->trackID = trackid;
    sinf->IPMPDescriptorID = 0;
     
    uint32_t playreadyLen = newHeader.size() + 4 + 2 + 2 + 2;
    sinf->IPMPData = new char[playreadyLen];
    sinf->len = playreadyLen;

    uint8_t *ptr = (uint8_t *)sinf->IPMPData;
#if 0
    *ptr++ = (playreadyLen >> 24) & 0xff;
    *ptr++ = (playreadyLen >> 16) & 0xff;
    *ptr++ = (playreadyLen >> 8) & 0xff;
    *ptr++ = playreadyLen & 0xff;
    *ptr++ = 0;
    *ptr++ = 1;           // Record Count
    *ptr++ = 0;
    *ptr++ = 1;           // type
    *ptr++ = (header.size() >> 8) & 0xff;
    *ptr++ = (header.size()) & 0xff;
#else
    *ptr++ = (playreadyLen ) & 0xff;
    *ptr++ = (playreadyLen >> 8) & 0xff;
    *ptr++ = (playreadyLen >> 16) & 0xff;
    *ptr++ = (playreadyLen >> 24) & 0xff;
    *ptr++ = 1;
    *ptr++ = 0;           // Record Count
    *ptr++ = 1;
    *ptr++ = 0;           // type
    *ptr++ = (newHeader.size() >> 0) & 0xff;
    *ptr++ = (newHeader.size() >> 8) & 0xff;
#endif
    memcpy(ptr, newHeader.c_str(), newHeader.size());
/*
    FILE* fd = fopen("/sdcard/playread.txt","wb+");
    fwrite((void *)(sinf->IPMPData), 1, playreadyLen, fd);
    fclose(fd);
*/


    sinf->next = mFirstSINF;
    mFirstSINF = sinf;

    return OK;
}
#endif

status_t MPEG4Extractor::parseDrmSINF(off64_t *offset, off64_t data_offset) {
    uint8_t updateIdTag;
    if (mDataSource->readAt(data_offset, &updateIdTag, 1) < 1) {
        return ERROR_IO;
    }
    data_offset ++;

    if (0x01/*OBJECT_DESCRIPTOR_UPDATE_ID_TAG*/ != updateIdTag) {
        return ERROR_MALFORMED;
    }

    uint8_t numOfBytes;
    int32_t size = readSize(data_offset, mDataSource, &numOfBytes);
    if (size < 0) {
        return ERROR_IO;
    }
    int32_t classSize = size;
    data_offset += numOfBytes;

    while(size >= 11 ) {
        uint8_t descriptorTag;
        if (mDataSource->readAt(data_offset, &descriptorTag, 1) < 1) {
            return ERROR_IO;
        }
        data_offset ++;

        if (0x11/*OBJECT_DESCRIPTOR_ID_TAG*/ != descriptorTag) {
            return ERROR_MALFORMED;
        }

        uint8_t buffer[8];
        //ObjectDescriptorID and ObjectDescriptor url flag
        if (mDataSource->readAt(data_offset, buffer, 2) < 2) {
            return ERROR_IO;
        }
        data_offset += 2;

        if ((buffer[1] >> 5) & 0x0001) { //url flag is set
            return ERROR_MALFORMED;
        }

        if (mDataSource->readAt(data_offset, buffer, 8) < 8) {
            return ERROR_IO;
        }
        data_offset += 8;

        if ((0x0F/*ES_ID_REF_TAG*/ != buffer[1])
                || ( 0x0A/*IPMP_DESCRIPTOR_POINTER_ID_TAG*/ != buffer[5])) {
            return ERROR_MALFORMED;
        }

        SINF *sinf = new SINF;
        sinf->trackID = U16_AT(&buffer[3]);
        sinf->IPMPDescriptorID = buffer[7];
        sinf->next = mFirstSINF;
        mFirstSINF = sinf;

        size -= (8 + 2 + 1);
    }

    if (size != 0) {
        return ERROR_MALFORMED;
    }

    if (mDataSource->readAt(data_offset, &updateIdTag, 1) < 1) {
        return ERROR_IO;
    }
    data_offset ++;

    if(0x05/*IPMP_DESCRIPTOR_UPDATE_ID_TAG*/ != updateIdTag) {
        return ERROR_MALFORMED;
    }

    size = readSize(data_offset, mDataSource, &numOfBytes);
    if (size < 0) {
        return ERROR_IO;
    }
    classSize = size;
    data_offset += numOfBytes;

    while (size > 0) {
        uint8_t tag;
        int32_t dataLen;
        if (mDataSource->readAt(data_offset, &tag, 1) < 1) {
            return ERROR_IO;
        }
        data_offset ++;

        if (0x0B/*IPMP_DESCRIPTOR_ID_TAG*/ == tag) {
            uint8_t id;
            dataLen = readSize(data_offset, mDataSource, &numOfBytes);
            if (dataLen < 0) {
                return ERROR_IO;
            } else if (dataLen < 4) {
                return ERROR_MALFORMED;
            }
            data_offset += numOfBytes;

            if (mDataSource->readAt(data_offset, &id, 1) < 1) {
                return ERROR_IO;
            }
            data_offset ++;

            SINF *sinf = mFirstSINF;
            while (sinf && (sinf->IPMPDescriptorID != id)) {
                sinf = sinf->next;
            }
            if (sinf == NULL) {
                return ERROR_MALFORMED;
            }
            sinf->len = dataLen - 3;
            sinf->IPMPData = new char[sinf->len];

            if (mDataSource->readAt(data_offset + 2, sinf->IPMPData, sinf->len) < sinf->len) {
                return ERROR_IO;
            }
            data_offset += sinf->len;

            size -= (dataLen + numOfBytes + 1);
        }
    }

    if (size != 0) {
        return ERROR_MALFORMED;
    }

    return UNKNOWN_ERROR;  // Return a dummy error.
}

struct PathAdder {
    PathAdder(Vector<uint32_t> *path, uint32_t chunkType)
        : mPath(path) {
        mPath->push(chunkType);
    }

    ~PathAdder() {
        mPath->pop();
    }

private:
    Vector<uint32_t> *mPath;

    PathAdder(const PathAdder &);
    PathAdder &operator=(const PathAdder &);
};

static bool underMetaDataPath(const Vector<uint32_t> &path) {
    return path.size() >= 5
        && path[0] == FOURCC('m', 'o', 'o', 'v')
        && path[1] == FOURCC('u', 'd', 't', 'a')
        && path[2] == FOURCC('m', 'e', 't', 'a')
        && path[3] == FOURCC('i', 'l', 's', 't');
}

// Given a time in seconds since Jan 1 1904, produce a human-readable string.
static void convertTimeToDate(int64_t time_1904, String8 *s) {
    time_t time_1970 = time_1904 - (((66 * 365 + 17) * 24) * 3600);

    char tmp[32];
    strftime(tmp, sizeof(tmp), "%Y%m%dT%H%M%S.000Z", gmtime(&time_1970));

    s->setTo(tmp);
}

status_t MPEG4Extractor::parseChunk(off64_t *offset, int depth) {
    ALOGV("entering parseChunk %lld/%d", *offset, depth);
    uint32_t hdr[2];
    if (mDataSource->readAt(*offset, hdr, 8) < 8) {
        return ERROR_IO;
    }
    uint64_t chunk_size = ntohl(hdr[0]);
    uint32_t chunk_type = ntohl(hdr[1]);
    off64_t data_offset = *offset + 8;

    if (chunk_size == 1) {
        if (mDataSource->readAt(*offset + 8, &chunk_size, 8) < 8) {
            return ERROR_IO;
        }
        chunk_size = ntoh64(chunk_size);
        data_offset += 8;

        if (chunk_size < 16) {
            // The smallest valid chunk is 16 bytes long in this case.
            return ERROR_MALFORMED;
        }
    } else if (chunk_size < 8) {
        // The smallest valid chunk is 8 bytes long.
        return ERROR_MALFORMED;
    }

    char chunk[5];
    MakeFourCCString(chunk_type, chunk);
    ALOGD("chunk: %s @ %lld, %d", chunk, *offset, depth);

#if 0
    static const char kWhitespace[] = "                                        ";
    const char *indent = &kWhitespace[sizeof(kWhitespace) - 1 - 2 * depth];
    printf("%sfound chunk '%s' of size %lld\n", indent, chunk, chunk_size);

    char buffer[256];
    size_t n = chunk_size;
    if (n > sizeof(buffer)) {
        n = sizeof(buffer);
    }
    if (mDataSource->readAt(*offset, buffer, n)
            < (ssize_t)n) {
        return ERROR_IO;
    }

    hexdump(buffer, n);
#endif

    PathAdder autoAdder(&mPath, chunk_type);

    off64_t chunk_data_size = *offset + chunk_size - data_offset;

    if (chunk_type != FOURCC('c', 'p', 'r', 't')
            && chunk_type != FOURCC('c', 'o', 'v', 'r')
            && mPath.size() == 5 && underMetaDataPath(mPath)) {
        off64_t stop_offset = *offset + chunk_size;
        *offset = data_offset;
        while (*offset < stop_offset) {
            status_t err = parseChunk(offset, depth + 1);
            if (err != OK) {
                return err;
            }
        }

        if (*offset != stop_offset) {
            return ERROR_MALFORMED;
        }

        return OK;
    }

    switch(chunk_type) {
#ifndef ANDROID_DEFAULT_CODE
		case FOURCC('f', 't', 'y', 'p'):
		{
			uint8_t header[4];
            if (mDataSource->readAt(
                        data_offset, header, 4)
                    < 4) {
                return ERROR_IO;
            }
			//ALOGD("HEADER=%x,%x,%x,%x", header[0], header[1], header[2], header[3]);
			if (!memcmp(header, "3gp", 3))
			{
				ALOGD("3GPP is true");
				mFileMetaData->setInt32(kKeyIs3gpBrand, true);
			}
#ifdef QUICKTIME_SUPPORT
			else if (!memcmp(header, "qt", 2))
			{
				mFileMetaData->setInt32(kKeyIsQTBrand, true);
			}
#endif
			
			
            *offset += chunk_size;
            break;
		}
#endif
        case FOURCC('m', 'o', 'o', 'v'):
        case FOURCC('t', 'r', 'a', 'k'):
        case FOURCC('m', 'd', 'i', 'a'):
        case FOURCC('m', 'i', 'n', 'f'):
        case FOURCC('d', 'i', 'n', 'f'):
        case FOURCC('s', 't', 'b', 'l'):
        case FOURCC('m', 'v', 'e', 'x'):
        case FOURCC('m', 'o', 'o', 'f'):
        case FOURCC('t', 'r', 'a', 'f'):
        case FOURCC('m', 'f', 'r', 'a'):
        case FOURCC('u', 'd', 't', 'a'):
        case FOURCC('i', 'l', 's', 't'):
        case FOURCC('s', 'i', 'n', 'f'):
        case FOURCC('s', 'c', 'h', 'i'):
		case FOURCC('e', 'd', 't', 's'): 
#ifndef ANDROID_DEFAULT_CODE			
#ifdef QUICKTIME_SUPPORT
		case FOURCC('w', 'a', 'v', 'e'): //for .mov
#endif
#endif
        {
            if (chunk_type == FOURCC('s', 't', 'b', 'l')) {
                ALOGV("sampleTable chunk is %d bytes long.", (size_t)chunk_size);

                if (mDataSource->flags()
                        & (DataSource::kWantsPrefetching
                            | DataSource::kIsCachingDataSource)) {
                    sp<MPEG4DataSource> cachedSource =
                        new MPEG4DataSource(mDataSource);

                    if (cachedSource->setCachedRange(*offset, chunk_size) == OK) {
                        mDataSource = cachedSource;
                    }
                }

                mLastTrack->sampleTable = new SampleTable(mDataSource);
            }

            bool isTrack = false;
            if (chunk_type == FOURCC('t', 'r', 'a', 'k')) {
                isTrack = true;

                Track *track = new Track;
                track->next = NULL;
                if (mLastTrack) {
                    mLastTrack->next = track;
                } else {
                    mFirstTrack = track;
                }
                mLastTrack = track;

                track->meta = new MetaData;
                track->includes_expensive_metadata = false;
                track->skipTrack = false;
                track->timescale = 0;
                track->meta->setCString(kKeyMIMEType, "application/octet-stream");
            }

            off64_t stop_offset = *offset + chunk_size;
            *offset = data_offset;
            while (*offset < stop_offset) {
#if !defined(ANDROID_DEFAULT_CODE) && defined(QUICKTIME_SUPPORT)//for .mov file
				if (stop_offset - *offset == 4
						&& chunk_type == FOURCC('u', 'd', 't', 'a')) {
					uint32_t terminate_code;
					mDataSource->readAt(*offset, &terminate_code, 4);
					if (0 == terminate_code)
					{
						*offset += 4;//terminate code 0x00000000
						ALOGD("Terminal code for 0x%8.8x", chunk_type);
					}
				}
				else {
					status_t err = parseChunk(offset, depth + 1);
					if (err != OK) {
						return err;
					}
				}
#else
                status_t err = parseChunk(offset, depth + 1);
                if (err != OK) {
                    return err;
                }
#endif
            }

            if (*offset != stop_offset) {
                return ERROR_MALFORMED;
            }

            if (isTrack) {
#ifndef ANDROID_DEFAULT_CODE//hai.li
				if (mLastTrack->durationUs == 0)
				{
					ALOGE("%s track duration is 0", mLastTrack->mIsVideo?"Video": "Audio");
					mLastTrack->skipTrack = true;
					//return UNKNOWN_ERROR;
				}
				if(mLastTrack->sampleCount ==0)
				{
					ALOGE("%s track sampleCount is 0", mLastTrack->mIsVideo?"Video": "Audio");
//					mLastTrack->skipTrack = true;
				}
				if (mLastTrack->mIsVideo)
				{

					int32_t max_size = 0;
					if (!mLastTrack->meta->findInt32(kKeyMaxInputSize, &max_size)) {
					    int32_t width, height;
					    mLastTrack->meta->findInt32(kKeyWidth, &width);
					    mLastTrack->meta->findInt32(kKeyHeight, &height);
					    mLastTrack->meta->setInt32(kKeyMaxInputSize, width*height*3/2);
					    ALOGI("video max_size:%d", width*height*3/2);
					}
					//CHECK(mLastTrack->durationUs != 0);
					if (mLastTrack->durationUs != 0)
					{
						int64_t frame_rate = mLastTrack->sampleCount * 1000000LL / mLastTrack->durationUs;
						const char* mime;
						if (mLastTrack->meta->findCString(kKeyMIMEType, &mime) &&
								!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC))
						{
							int32_t width, height;
							mLastTrack->meta->findInt32(kKeyWidth, &width);
							mLastTrack->meta->findInt32(kKeyHeight, &height);
							if (((frame_rate*width*height+128)/256 > AVC_MAX_MACRO_PER_SECOND) && (mLastTrack->sampleCount > 1))
							{
								ALOGW("[h264 capability warning]h264 real level!!!fps = %lld, width=%d, height=%d", frame_rate, width, height);

							}							
#ifdef MTK_S3D_SUPPORT
							if (mLastTrack->skipTrack != true && mHasVideo) {
								ALOGD("Parse sei");
								size_t size;
								size_t offset;
								void *data;
								if (OK == getFirstNal(mLastTrack, &offset, &size)) {
									data = malloc(size);
									if (NULL == data)
										return UNKNOWN_ERROR;
									if (mDataSource->readAt(
												offset, data, size)
											< size) {
										ALOGE("read first nal fail!!");
										return ERROR_IO;
									}

									struct SEIInfo sei;
									video_stereo_mode mode;
									sei.payload_type = 45;//3d info
									sei.pvalue = (void*)&mode;
									if (OK == ParseSpecificSEI((uint8_t*)data, size, &sei)) {
										ALOGD("Video stereo mode=%d", mode);
										mLastTrack->meta->setInt32(kKeyVideoStereoMode, mode);
									}
								}
								
							}
#endif
						}
						else if (mLastTrack->meta->findCString(kKeyMIMEType, &mime) &&
								!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_MPEG4))
						{
							int32_t isCodecInfoInFirstFrame;
							if (mLastTrack->meta->findInt32(kKeyCodecInfoIsInFirstFrame, &isCodecInfoInFirstFrame)
								&& (isCodecInfoInFirstFrame != 0))
							{
								status_t err = setCodecInfoFromFirstFrame(mLastTrack);
								if (err != OK) {
									ALOGE("setCodecInfoFromFirstFrame error %d", err);
									return err;
								}
							}
						}
						else if (mLastTrack->meta->findCString(kKeyMIMEType, &mime) &&
								(!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_DIVX)))
                        {    
                           uint32_t type;
                           const void *data;
                           size_t size;
                           
                           if (!mLastTrack->meta->findData(kKeyMPEG4VOS, &type, &data, &size) || type != 0) 
                             {
                       		   mLastTrack->meta->setData(kKeyMPEG4VOS, 0, 0, 0);
                             }
                        }
					
					}
					if (mLastTrack->skipTrack)
					{
						mFileMetaData->setInt32(kKeyHasUnsupportVideo, true);
						ALOGD("MP4 has unsupport video track");
					}
				}
		 	// <--- Morris Yang check audio
				else if (mLastTrack->mIsAudio){
					int32_t max_size = 0;
					const char *mime;
					if (!mLastTrack->meta->findInt32(kKeyMaxInputSize, &max_size) ||
                                            (mLastTrack->meta->findCString(kKeyMIMEType, &mime) && !strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_RAW))) {
					    mLastTrack->meta->setInt32(kKeyMaxInputSize, 20000);
					    ALOGI("audio max_size:20k");
					}
				    if (mLastTrack->meta->findCString(kKeyMIMEType, &mime) &&
						!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG))
					{
					    int32_t isCodecInfoInFirstFrame;
					    if (mLastTrack->meta->findInt32(kKeyCodecInfoIsInFirstFrame, &isCodecInfoInFirstFrame)
						    && (isCodecInfoInFirstFrame != 0))
					    {
						status_t err = setCodecInfoFromFirstFrame(mLastTrack);
						if (err != OK) {
						    ALOGE("setCodecInfoFromFirstFrame error %d", err);
						    return err;
						}
					    }
					}

					if (mLastTrack->skipTrack)
					{
						mHasAudio = false;
					}
				}

				else {//not video or audio track
					//mLastTrack->skipTrack = true;
                                      ALOGI("Is not video and audio track");
				}

			// --->
#endif//#ifndef ANDROID_DEFAULT_CODE
                if (mLastTrack->skipTrack) {
                    Track *cur = mFirstTrack;

                    if (cur == mLastTrack) {
                        delete cur;
                        mFirstTrack = mLastTrack = NULL;
                    } else {
                        while (cur && cur->next != mLastTrack) {
                            cur = cur->next;
                        }
                        cur->next = NULL;
                        delete mLastTrack;
                        mLastTrack = cur;
                    }

                    return OK;
                }

                status_t err = verifyTrack(mLastTrack);

                if (err != OK) {
                    return err;
                }
            } else if (chunk_type == FOURCC('m', 'o', 'o', 'v')) {
                mInitCheck = OK;

                if (!mIsDrm) {
                    return UNKNOWN_ERROR;  // Return a dummy error.
                } else {
                    return OK;
                }
            }
            break;
        }

        case FOURCC('f', 'r', 'm', 'a'):
        {
            uint32_t original_fourcc;
            if (mDataSource->readAt(data_offset, &original_fourcc, 4) < 4) {
                return ERROR_IO;
            }
            original_fourcc = ntohl(original_fourcc);
            ALOGV("read original format: %d", original_fourcc);
            mLastTrack->meta->setCString(kKeyMIMEType, FourCC2MIME(original_fourcc));
            uint32_t num_channels = 0;
            uint32_t sample_rate = 0;
            if (AdjustChannelsAndRate(original_fourcc, &num_channels, &sample_rate)) {
                mLastTrack->meta->setInt32(kKeyChannelCount, num_channels);
                mLastTrack->meta->setInt32(kKeySampleRate, sample_rate);
            }
            *offset += chunk_size;
            break;
        }

        case FOURCC('t', 'e', 'n', 'c'):
        {
            if (chunk_size < 32) {
                return ERROR_MALFORMED;
            }

            // tenc box contains 1 byte version, 3 byte flags, 3 byte default algorithm id, one byte
            // default IV size, 16 bytes default KeyID
            // (ISO 23001-7)
            char buf[4];
            memset(buf, 0, 4);
            if (mDataSource->readAt(data_offset + 4, buf + 1, 3) < 3) {
                return ERROR_IO;
            }
            uint32_t defaultAlgorithmId = ntohl(*((int32_t*)buf));
            if (defaultAlgorithmId > 1) {
                // only 0 (clear) and 1 (AES-128) are valid
                return ERROR_MALFORMED;
            }

            memset(buf, 0, 4);
            if (mDataSource->readAt(data_offset + 7, buf + 3, 1) < 1) {
                return ERROR_IO;
            }
            uint32_t defaultIVSize = ntohl(*((int32_t*)buf));

            if ((defaultAlgorithmId == 0 && defaultIVSize != 0) ||
                    (defaultAlgorithmId != 0 && defaultIVSize == 0)) {
                // only unencrypted data must have 0 IV size
                return ERROR_MALFORMED;
            } else if (defaultIVSize != 0 &&
                    defaultIVSize != 8 &&
                    defaultIVSize != 16) {
                // only supported sizes are 0, 8 and 16
                return ERROR_MALFORMED;
            }

            uint8_t defaultKeyId[16];

            if (mDataSource->readAt(data_offset + 8, &defaultKeyId, 16) < 16) {
                return ERROR_IO;
            }

            mLastTrack->meta->setInt32(kKeyCryptoMode, defaultAlgorithmId);
            mLastTrack->meta->setInt32(kKeyCryptoDefaultIVSize, defaultIVSize);
            mLastTrack->meta->setData(kKeyCryptoKey, 'tenc', defaultKeyId, 16);
            *offset += chunk_size;
            break;
        }

        case FOURCC('t', 'k', 'h', 'd'):
        {
            status_t err;
            if ((err = parseTrackHeader(data_offset, chunk_data_size)) != OK) {
                return err;
            }

            *offset += chunk_size;
            break;
        }

        case FOURCC('p', 's', 's', 'h'):
        {
            PsshInfo pssh;

            if (mDataSource->readAt(data_offset + 4, &pssh.uuid, 16) < 16) {
                return ERROR_IO;
            }

            uint32_t psshdatalen = 0;
            if (mDataSource->readAt(data_offset + 20, &psshdatalen, 4) < 4) {
                return ERROR_IO;
            }
            pssh.datalen = ntohl(psshdatalen);
            ALOGV("pssh data size: %d", pssh.datalen);
            if (pssh.datalen + 20 > chunk_size) {
                // pssh data length exceeds size of containing box
                return ERROR_MALFORMED;
            }

            pssh.data = new uint8_t[pssh.datalen];
            ALOGV("allocated pssh @ %p", pssh.data);
            ssize_t requested = (ssize_t) pssh.datalen;
            if (mDataSource->readAt(data_offset + 24, pssh.data, requested) < requested) {
                return ERROR_IO;
            }
            mPssh.push_back(pssh);

            *offset += chunk_size;
            break;
        }
#if 1
		case FOURCC('e', 'l', 's', 't'):
		{
			// See 14496-12 8.6.6
			uint8_t version;
			if (mDataSource->readAt(data_offset, &version, 1) < 1) {
				return ERROR_IO;
			}

			uint32_t entry_count;
			if (!mDataSource->getUInt32(data_offset + 4, &entry_count)) {
				return ERROR_IO;
			}

			if (entry_count != 1) {
				// we only support a single entry at the moment, for gapless playback
				ALOGW("ignoring edit list with %d entries", entry_count);
			} else if (mHeaderTimescale == 0) {
				ALOGW("ignoring edit list because timescale is 0");
			} else {
				off64_t entriesoffset = data_offset + 8;
				uint64_t segment_duration;
				int64_t media_time;

				if (version == 1) {
					if (!mDataSource->getUInt64(entriesoffset, &segment_duration) ||
							!mDataSource->getUInt64(entriesoffset + 8, (uint64_t*)&media_time)) {
						return ERROR_IO;
					}
				} else if (version == 0) {
					uint32_t sd;
					int32_t mt;
					if (!mDataSource->getUInt32(entriesoffset, &sd) ||
							!mDataSource->getUInt32(entriesoffset + 4, (uint32_t*)&mt)) {
						return ERROR_IO;
					}
					segment_duration = sd;
					media_time = mt;
				} else {
					return ERROR_IO;
				}

				uint64_t halfscale = mHeaderTimescale / 2;
				segment_duration = (segment_duration * 1000000 + halfscale)/ mHeaderTimescale;
				media_time = (media_time * 1000000 + halfscale) / mHeaderTimescale;

				int64_t duration;
				int32_t samplerate;
				if (mLastTrack->meta->findInt64(kKeyDuration, &duration) &&
						mLastTrack->meta->findInt32(kKeySampleRate, &samplerate)) {

					int64_t delay = (media_time  * samplerate + 500000) / 1000000;
					mLastTrack->meta->setInt32(kKeyEncoderDelay, delay);

					int64_t paddingus = duration - (segment_duration + media_time);
					int64_t paddingsamples = (paddingus * samplerate + 500000) / 1000000;
#ifndef ANDROID_DEFAULT_CODE					
					if(paddingsamples >=0)
#endif					
					mLastTrack->meta->setInt32(kKeyEncoderPadding, paddingsamples);
				}
			}
			*offset += chunk_size;
			break;
		}
#else
#ifndef ANDROID_DEFAULT_CODE
		case FOURCC('e', 'l', 's', 't')://added by hai.li to support track time offset
		{
			if (chunk_data_size < 4) {
				ALOGE("ERROR_MALFORMED, LINE=%d", __LINE__);
                return ERROR_MALFORMED;
            }
			uint8_t header[8];
			uint8_t version;
			uint32_t entry_count;
            if (mDataSource->readAt(
                        data_offset, header, sizeof(header))
                    < (ssize_t)sizeof(header)) {
                return ERROR_IO;
            }
			version = header[0];
			entry_count = U32_AT(&header[4]);

			//ALOGE("header high=%d, header low=%d, entry_count=%d", *((uint32_t*)header), *((uint32_t*)(header+4)), entry_count);
			if (entry_count > 2)
			{
				ALOGW("Unsupported edit list, entry_count=%d > 2", entry_count);//The second entry is assumed as the duration of the track and normal play
				entry_count = 2;
			}
			else if (entry_count == 2)
				ALOGW("edit list entry_count=2, Assume the second entry is the duration of the track and normal play");

			mLastTrack->mElstEntryCount = entry_count;
			mLastTrack->mElstEntries = new Track::ElstEntry[entry_count];
			if (version == 1) {
				for (uint32_t i = 0; i < entry_count; i++)
				{
					uint8_t buffer[20];
					if (mDataSource->readAt(
							data_offset+4+4+i*20, buffer, sizeof(buffer))
							< (ssize_t)sizeof(buffer)){
						return ERROR_IO;
					}
					mLastTrack->mElstEntries[i].SegDuration = U64_AT(buffer);
					mLastTrack->mElstEntries[i].MediaTime = (int64_t)U64_AT(&buffer[8]);
					mLastTrack->mElstEntries[i].MediaRateInt = (int16_t)U16_AT(&buffer[16]);
					mLastTrack->mElstEntries[i].MediaRateFrac = (int16_t)U16_AT(&buffer[18]);
				}
			} else {
				for (uint32_t i = 0; i < entry_count; i++)
				{
					uint8_t buffer[12];
					if (mDataSource->readAt(
							data_offset+4+4+i*12, buffer, sizeof(buffer))
							< (ssize_t)sizeof(buffer)){
						return ERROR_IO;
					}
					mLastTrack->mElstEntries[i].SegDuration = U32_AT(buffer);
					if (0xffffffff == U32_AT(&buffer[4]))
						mLastTrack->mElstEntries[i].MediaTime = -1LL;
					else
						mLastTrack->mElstEntries[i].MediaTime = (int64_t)U32_AT(&buffer[4]);
					ALOGD("MediaTime=%d, entry.mediatime=%lld, i=%d", U32_AT(&buffer[4]), mLastTrack->mElstEntries[i].MediaTime, i);
					ALOGD("SegDuration=%d, entry.SegDuration=%lld, i=%d", U32_AT(&buffer[0]), mLastTrack->mElstEntries[i].SegDuration, i);
					mLastTrack->mElstEntries[i].MediaRateInt = (int16_t)U16_AT(&buffer[8]);
					mLastTrack->mElstEntries[i].MediaRateFrac = (int16_t)U16_AT(&buffer[10]);
				}
			}
			if ((mLastTrack->mElstEntries[0].MediaRateInt != 1) ||
					(mLastTrack->mElstEntries[0].MediaRateFrac != 0)){
					ALOGW("Unsupported edit list, MediaRate=%d.%d != 1", 
							mLastTrack->mElstEntries[0].MediaRateInt, mLastTrack->mElstEntries[0].MediaRateFrac);
			}
			 
			if (mLastTrack->mElstEntries[0].SegDuration > (uint64_t)((1LL>>32)-1))
			{
				ALOGW("Unsupported edit list, TimeOffset=%lld", mLastTrack->mElstEntries[0].SegDuration);
				mLastTrack->mStartTimeOffset = 0;
			}
			else if (mLastTrack->mElstEntries[0].MediaTime != -1){//added by vend_am00032
				mLastTrack->mStartTimeOffset = 0;
				ALOGW("Unsupported edit list, MediaTime=%lld", mLastTrack->mElstEntries[0].MediaTime);
			}
			else {
				mLastTrack->mStartTimeOffset = (uint32_t)mLastTrack->mElstEntries[0].SegDuration;
			}
			*offset += chunk_size;
			break;			
		}
#endif	
#endif
#ifndef ANDROID_DEFAULT_CODE	
//live photo box
		case FOURCC('l', 'v', 'p', 'o'):
		{
			if (mPath.size() >= 3
				&& mPath[mPath.size() - 2] == FOURCC('u', 'd', 't', 'a')
				&& mPath[mPath.size() - 3] == FOURCC('t', 'r', 'a', 'k')) {
				if (chunk_data_size >= 26) {
					uint8_t buffer[26];
					if (mDataSource->readAt(data_offset, buffer, 26) < 26) {
						return ERROR_IO;
					}
					const char* mtk_livephoto_tag = "MTK-live-photo:";
					if (!memcmp(mtk_livephoto_tag, buffer+6, 16)) {
						int32_t mtk_livephoto_mode = U32_AT(&buffer[22]);
						ALOGD("mtk live photo mode = %d", mtk_livephoto_mode);
						mLastTrack->meta->setInt32(kKeyIsLivePhoto, mtk_livephoto_mode);
					}
				}
			}

			*offset += chunk_size;
			break;
		}	
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT		
//slow motion box
		case FOURCC('s', 'm', 's', 'v'):
		{
			if (mPath.size() >= 3
				&& mPath[mPath.size() - 2] == FOURCC('u', 'd', 't', 'a')
				&& mPath[mPath.size() - 3] == FOURCC('t', 'r', 'a', 'k')) {
				if (chunk_data_size >= 27) {
					uint8_t buffer[27];
					if (mDataSource->readAt(data_offset, buffer, 27) < 27) {
						return ERROR_IO;
					}
					const char* mtk_slowmotion_tag = "MTK-slow-motion:";
					if (!memcmp(mtk_slowmotion_tag, buffer+6, 17)) {
						int32_t mtk_slowmotion_speed = U32_AT(&buffer[23]);
						ALOGD("mtk slowmotion speed value = %d", mtk_slowmotion_speed);
						mLastTrack->meta->setInt32(kKeySlowMotionSpeedValue, mtk_slowmotion_speed);
					}
				}
			}

			*offset += chunk_size;
			break;
		}
#endif		
#ifdef MTK_S3D_SUPPORT
	//live photo box
			case FOURCC('l', 'v', 'p', 'o'):
			{
				if (mPath.size() >= 3
					&& mPath[mPath.size() - 2] == FOURCC('u', 'd', 't', 'a')
					&& mPath[mPath.size() - 3] == FOURCC('t', 'r', 'a', 'k')) {
					if (chunk_data_size >= 26) {
						uint8_t buffer[26];
						if (mDataSource->readAt(data_offset, buffer, 26) < 26) {
							return ERROR_IO;
						}
						const char* mtk_livephoto_tag = "MTK-live-photo:";
						if (!memcmp(mtk_livephoto_tag, buffer+6, 16)) {
							int32_t mtk_livephoto_mode = U32_AT(&buffer[22]);
							ALOGD("mtk live photo mode = %d", mtk_livephoto_mode);
							mLastTrack->meta->setInt32(kKeyIsLivePhoto, mtk_livephoto_mode);
						}
					}
				}
	
				*offset += chunk_size;
				break;
			}
		case FOURCC('c', 'p', 'r', 't'):
		{
			if (mPath.size() >= 3
				&& mPath[mPath.size() - 2] == FOURCC('u', 'd', 't', 'a')
				&& mPath[mPath.size() - 3] == FOURCC('t', 'r', 'a', 'k')) {
				if (chunk_data_size >= 29) {
					uint8_t buffer[29];
					if (mDataSource->readAt(data_offset, buffer, 29) < 29) {
						return ERROR_IO;
					}
					const char* mtk_3d_tag = "MTK-3d-video-mode:";
					if (!memcmp(mtk_3d_tag, buffer+6, 19)) {
						int32_t mtk_3d_mode = U32_AT(&buffer[25]);
						ALOGD("mtk 3d mode = %d", mtk_3d_mode);
						mLastTrack->meta->setInt32(kKeyVideoStereoMode, mtk_3d_mode);
					}
				}
			}

			*offset += chunk_size;
			break;
		}
#endif
#ifdef MTK_VIDEO_HEVC_SUPPORT
		case FOURCC('k', 'y', 'w', 'd'):
		{
		    ALOGI("kywd");
		    if (mPath.size() >= 3
			    && mPath[mPath.size() - 2] == FOURCC('u', 'd', 't', 'a')
			    && mPath[mPath.size() - 3] == FOURCC('t', 'r', 'a', 'k')) {
			  if (chunk_data_size >= 16) {
			    uint8_t buffer[16];
			    if (mDataSource->readAt(data_offset, buffer, 16) < 16) {
				    return ERROR_IO;
			    }
			    const char* mtk_hevc_tag = "HEVC_MTK";
			    if (!memcmp(mtk_hevc_tag, buffer+8, 8)) {
				    ALOGI("hevc_mtk");
				    mLastTrack->meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_HEVC);
			     }
			   }
		    }
		    *offset += chunk_size;
		    break;
		}
#endif
#endif
        case FOURCC('m', 'd', 'h', 'd'):
        {
            if (chunk_data_size < 4) {
                return ERROR_MALFORMED;
            }

            uint8_t version;
            if (mDataSource->readAt(
                        data_offset, &version, sizeof(version))
                    < (ssize_t)sizeof(version)) {
                return ERROR_IO;
            }

            off64_t timescale_offset;

            if (version == 1) {
                timescale_offset = data_offset + 4 + 16;
            } else if (version == 0) {
                timescale_offset = data_offset + 4 + 8;
            } else {
                return ERROR_IO;
            }

            uint32_t timescale;
            if (mDataSource->readAt(
                        timescale_offset, &timescale, sizeof(timescale))
                    < (ssize_t)sizeof(timescale)) {
                return ERROR_IO;
            }

            mLastTrack->timescale = ntohl(timescale);

            int64_t duration = 0;
            if (version == 1) {
                if (mDataSource->readAt(
                            timescale_offset + 4, &duration, sizeof(duration))
                        < (ssize_t)sizeof(duration)) {
                    return ERROR_IO;
                }
                duration = ntoh64(duration);
            } else {
                uint32_t duration32;
                if (mDataSource->readAt(
                            timescale_offset + 4, &duration32, sizeof(duration32))
                        < (ssize_t)sizeof(duration32)) {
                    return ERROR_IO;
                }
                // ffmpeg sets duration to -1, which is incorrect.
                if (duration32 != 0xffffffff) {
                    duration = ntohl(duration32);
                }
            }
#ifndef ANDROID_DEFAULT_CODE//hai.li
	    if (mLastTrack->timescale == 0) {
		ALOGW("%d:timescale is 0,not set duration", __LINE__);
	    }
	    else {
                mLastTrack->durationUs = (duration * 1000000) / mLastTrack->timescale;
                mLastTrack->meta->setInt64(
                kKeyDuration, mLastTrack->durationUs);
                //added by gary.wu to check final AU duration ALPS00613110
                bool returnA = mLastTrack->meta->setInt32(kKeyTimeScaleOptional, mLastTrack->timescale);
                //ALOGI("returnA %d, timescale %d", returnA, mLastTrack->timescale);
                //added by gary.wu to check final AU duration ALPS00613110 end
	    }

        int64_t int32Max = 0xFFFFFFFF;
	    if (duration > int32Max && mLastTrack->timescale != 0) {
                 mLastTrack->timescaleFactor = (duration + int32Max - 1)/int32Max;
                 mLastTrack->timescale /= mLastTrack->timescaleFactor;
                 ALOGI("New timescale:%d", mLastTrack->timescale);
	    }
#else			
            mLastTrack->meta->setInt64(
                    kKeyDuration, (duration * 1000000) / mLastTrack->timescale);
#endif
            uint8_t lang[2];
            off64_t lang_offset;
            if (version == 1) {
                lang_offset = timescale_offset + 4 + 8;
            } else if (version == 0) {
                lang_offset = timescale_offset + 4 + 4;
            } else {
                return ERROR_IO;
            }

            if (mDataSource->readAt(lang_offset, &lang, sizeof(lang))
                    < (ssize_t)sizeof(lang)) {
                return ERROR_IO;
            }

            // To get the ISO-639-2/T three character language code
            // 1 bit pad followed by 3 5-bits characters. Each character
            // is packed as the difference between its ASCII value and 0x60.
            char lang_code[4];
            lang_code[0] = ((lang[0] >> 2) & 0x1f) + 0x60;
            lang_code[1] = ((lang[0] & 0x3) << 3 | (lang[1] >> 5)) + 0x60;
            lang_code[2] = (lang[1] & 0x1f) + 0x60;
            lang_code[3] = '\0';

            mLastTrack->meta->setCString(
                    kKeyMediaLanguage, lang_code);

            *offset += chunk_size;
            break;
        }

        case FOURCC('s', 't', 's', 'd'):
        {
            if (chunk_data_size < 8) {
                return ERROR_MALFORMED;
            }

            uint8_t buffer[8];
            if (chunk_data_size < (off64_t)sizeof(buffer)) {
                return ERROR_MALFORMED;
            }

            if (mDataSource->readAt(
                        data_offset, buffer, 8) < 8) {
                return ERROR_IO;
            }

            if (U32_AT(buffer) != 0) {
                // Should be version 0, flags 0.
                return ERROR_MALFORMED;
            }

            uint32_t entry_count = U32_AT(&buffer[4]);

            if (entry_count > 1) {
                // For 3GPP timed text, there could be multiple tx3g boxes contain
                // multiple text display formats. These formats will be used to
                // display the timed text.
                // For encrypted files, there may also be more than one entry.
                const char *mime;
                CHECK(mLastTrack->meta->findCString(kKeyMIMEType, &mime));
#ifdef MTK_SUBTITLE_SUPPORT
                if(strcasecmp(mime, MEDIA_MIMETYPE_TEXT_3GPP) && strcasecmp(mime, "application/octet-stream") && strcasecmp(mime, MEDIA_MIMETYPE_TEXT_VOBSUB))
#else
                if (strcasecmp(mime, MEDIA_MIMETYPE_TEXT_3GPP) &&
                        strcasecmp(mime, "application/octet-stream")) 
#endif
                {
                    // For now we only support a single type of media per track.
                    mLastTrack->skipTrack = true;
                    *offset += chunk_size;
                    break;
                }
            }
            off64_t stop_offset = *offset + chunk_size;
            *offset = data_offset + 8;
            for (uint32_t i = 0; i < entry_count; ++i) {
                status_t err = parseChunk(offset, depth + 1);
                if (err != OK) {
                    return err;
                }
            }

            if (*offset != stop_offset) {
                return ERROR_MALFORMED;
            }
            break;
        }

        case FOURCC('m', 'p', '4', 'a'):
        case FOURCC('e', 'n', 'c', 'a'):
        case FOURCC('s', 'a', 'm', 'r'):
        case FOURCC('s', 'a', 'w', 'b'):
#ifndef ANDROID_DEFAULT_CODE
		case FOURCC('.', 'm', 'p', '3'):
		case 0x6D730055:                     // like mp3
#ifdef MTK_AUDIO_RAW_SUPPORT
		case FOURCC('r', 'a', 'w', ' '):
		case FOURCC('t', 'w', 'o', 's'):
		case FOURCC('i', 'n', '2', '4'):
		case FOURCC('i', 'n', '3', '2'):
		case FOURCC('s', 'o', 'w', 't'):
#endif
#ifdef MTK_AUDIO_DDPLUS_SUPPORT
        case FOURCC('a', 'c', '-', '3'):
        case FOURCC('e', 'c', '-', '3'):
#endif
#endif
        {
#ifndef ANDROID_DEFAULT_CODE//hai.li
			mHasAudio = true;
			mLastTrack->mIsAudio = true;
#endif
            uint8_t buffer[8 + 20];
            if (chunk_data_size < (ssize_t)sizeof(buffer)) {
#if !defined(ANDROID_DEFAULT_CODE) && defined(QUICKTIME_SUPPORT)//for .mov file		
				
				if (mPath.size() >= 2
						&& mPath[mPath.size() - 2] == FOURCC('w', 'a', 'v', 'e')) {
                                        ALOGI("wave sub box %x", chunk_type);
					*offset += chunk_size;
					return OK;
				}
				else {
					ALOGE("ERROR_MALFORMED, LINE=%d", __LINE__);
					return ERROR_MALFORMED;
				}
#else
                // Basic AudioSampleEntry size.
                return ERROR_MALFORMED;
#endif
            }

            if (mDataSource->readAt(
                        data_offset, buffer, sizeof(buffer)) < (ssize_t)sizeof(buffer)) {
                return ERROR_IO;
            }

            uint16_t data_ref_index = U16_AT(&buffer[6]);
            uint32_t num_channels = U16_AT(&buffer[16]);

            uint16_t sample_size = U16_AT(&buffer[18]);
            uint32_t sample_rate = U32_AT(&buffer[24]) >> 16;
#ifndef ANDROID_DEFAULT_CODE
            uint16_t versions = U16_AT(&buffer[8]);
#ifdef MTK_AUDIO_RAW_SUPPORT
	    if (versions == 1) {
		uint8_t buffer2[16];
		if (mDataSource->readAt(
			    data_offset+28, buffer2, sizeof(buffer2)) < (ssize_t)sizeof(buffer2)) {
		    return ERROR_IO;
		}
		sample_size = U32_AT(&buffer2[4]) * 8;
                ALOGI("version 1 sample size:%d", sample_size);
	    }
#endif
#ifdef QUICKTIME_SUPPORT
            // quick time version 2 configure  ALPS00490872
	    if (chunk_type == FOURCC('m', 'p', '4', 'a') && versions == 2 ) {
		uint8_t buffer2[64];
		if (mDataSource->readAt(
			    data_offset, buffer2, sizeof(buffer2)) < (ssize_t)sizeof(buffer2)) {
		    return ERROR_IO;
		}
                // sample rate is 64-bit float, should convert
                uint64_t tmp = U64_AT(&buffer2[32]);
                double *pSampleRate = (double *)&tmp;
                sample_rate = uint32_t(*pSampleRate);
                ALOGI("sampleRate:%f %d", *pSampleRate, sample_rate);
                num_channels = U32_AT(&buffer2[40]);
	    }
#endif
#endif

            if (chunk_type != FOURCC('e', 'n', 'c', 'a')) {
                // if the chunk type is enca, we'll get the type from the sinf/frma box later
                mLastTrack->meta->setCString(kKeyMIMEType, FourCC2MIME(chunk_type));
                AdjustChannelsAndRate(chunk_type, &num_channels, &sample_rate);
#ifndef ANDROID_DEFAULT_CODE
           		if(!strcasecmp(MEDIA_MIMETYPE_AUDIO_MPEG,FourCC2MIME(chunk_type))){
					mLastTrack->meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
	    		}
#endif
            }


            ALOGV("*** coding='%s' %d channels, size %d, rate %d\n",
                   chunk, num_channels, sample_size, sample_rate);
            mLastTrack->meta->setInt32(kKeyChannelCount, num_channels);
            mLastTrack->meta->setInt32(kKeySampleRate, sample_rate);
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_RAW_SUPPORT
	    mLastTrack->meta->setInt32(kKeyBitWidth, sample_size);
	    mLastTrack->meta->setInt32(kKeyPCMType, 1);              //pcm_wave
            ALOGI("sample_size:%d", sample_size);
	    if (chunk_type == FOURCC('r', 'a', 'w', ' ')) {
                ALOGI("raw box, unsigned");
		mLastTrack->meta->setInt32(kKeyNumericalType, 2);              // 1:signed 2:unsigned
	    }
	    else if (chunk_type == FOURCC('s', 'o', 'w', 't')) {
                ALOGI("sowt box");
		mLastTrack->meta->setInt32(kKeyEndian, 2);
	    }
	    else if (chunk_type == FOURCC('t', 'w', 'o', 's')) {
                ALOGI("twos box");
		mLastTrack->meta->setInt32(kKeyEndian, 1);
	    }
	    else if (chunk_type == FOURCC('i', 'n', '2', '4')) {
                ALOGI("in24 box");
		mLastTrack->meta->setInt32(kKeyEndian, 2);
		if (versions == 0) {
		    mLastTrack->skipTrack = true;
		    ALOGD("warning:box in24 version 0 skip it");
		}
	    }
	    else if (chunk_type == FOURCC('i', 'n', '3', '2')) {
                ALOGI("in32 box");
		mLastTrack->meta->setInt32(kKeyEndian, 2);
		if (versions == 0) {
		    mLastTrack->skipTrack = true;
		    ALOGD("warning:box in32 version 0 skip it");
		}
	    }
#endif
#endif

            off64_t stop_offset = *offset + chunk_size;
            *offset = data_offset + sizeof(buffer);
#ifndef ANDROID_DEFAULT_CODE
#ifdef QUICKTIME_SUPPORT//hai.li for .mov
			if (1 == U16_AT(&buffer[8]))//sound media version == 1
				*offset += 16;//4*4byte
                        else if (2 == U16_AT(&buffer[8]))
                                *offset += 36;
#endif
#endif
            while (*offset < stop_offset) {
                status_t err = parseChunk(offset, depth + 1);
                if (err != OK) {
                    return err;
                }
            }

            if (*offset != stop_offset) {
                return ERROR_MALFORMED;
            }
            break;
        }

        case FOURCC('m', 'p', '4', 'v'):
        case FOURCC('e', 'n', 'c', 'v'):
        case FOURCC('s', '2', '6', '3'):
        case FOURCC('H', '2', '6', '3'):
        case FOURCC('h', '2', '6', '3'):
        case FOURCC('a', 'v', 'c', '1'):
#ifndef ANDROID_DEFAULT_CODE		
#ifdef MTK_VIDEO_HEVC_SUPPORT
		case FOURCC('h', 'v', 'c', '1'):	
#endif
        case FOURCC('D', 'X', '5', '0'):
        case FOURCC('j', 'p', 'e', 'g'):
#endif
        {
            mHasVideo = true;

            uint8_t buffer[78];
            if (chunk_data_size < (ssize_t)sizeof(buffer)) {
                // Basic VideoSampleEntry size.
                return ERROR_MALFORMED;
            }

            if (mDataSource->readAt(
                        data_offset, buffer, sizeof(buffer)) < (ssize_t)sizeof(buffer)) {
                return ERROR_IO;
            }

            uint16_t data_ref_index = U16_AT(&buffer[6]);
            uint16_t width = U16_AT(&buffer[6 + 18]);
            uint16_t height = U16_AT(&buffer[6 + 20]);

            // The video sample is not standard-compliant if it has invalid dimension.
            // Use some default width and height value, and
            // let the decoder figure out the actual width and height (and thus
            // be prepared for INFO_FOMRAT_CHANGED event).
            if (width == 0)  width  = 352;
            if (height == 0) height = 288;

            // printf("*** coding='%s' width=%d height=%d\n",
            //        chunk, width, height);
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_VIDEO_HEVC_SUPPORT
	      const char *mime;
	      if (chunk_type == FOURCC('a', 'v', 'c', '1') &&
		     mLastTrack->meta->findCString(kKeyMIMEType, &mime) &&
		     !strcasecmp(MEDIA_MIMETYPE_VIDEO_HEVC, mime)) {
		     ALOGI("Is hevc not avc");
	      }
	      else
#endif
#endif
            if (chunk_type != FOURCC('e', 'n', 'c', 'v')) {
                // if the chunk type is encv, we'll get the type from the sinf/frma box later
                mLastTrack->meta->setCString(kKeyMIMEType, FourCC2MIME(chunk_type));
            }
            mLastTrack->meta->setInt32(kKeyWidth, width);
            mLastTrack->meta->setInt32(kKeyHeight, height);
#ifndef ANDROID_DEFAULT_CODE			
			mLastTrack->mIsVideo = true;
#endif
            off64_t stop_offset = *offset + chunk_size;
            *offset = data_offset + sizeof(buffer);
            while (*offset < stop_offset) {
#if !defined(ANDROID_DEFAULT_CODE) && defined(QUICKTIME_SUPPORT)//for .mov file
				if (stop_offset - *offset < 8)
					*offset = stop_offset;//Maybe terminate box? 0x00000000
				else {
					status_t err = parseChunk(offset, depth + 1);
					if (err != OK) {
						return err;
					}
				}
#else
                status_t err = parseChunk(offset, depth + 1);
                if (err != OK) {
                    return err;
                }
#endif
            }

            if (*offset != stop_offset) {
                return ERROR_MALFORMED;
            }
            break;
        }

        case FOURCC('s', 't', 'c', 'o'):
        case FOURCC('c', 'o', '6', '4'):
        {
#ifndef ANDROID_DEFAULT_CODE
			if(mLastTrack->sampleTable == NULL){
				ALOGD("stco or co64 before stbl,sampleTable == NULL,break");
				*offset += chunk_size;
				break;
			}
#endif			
            status_t err =
                mLastTrack->sampleTable->setChunkOffsetParams(
                        chunk_type, data_offset, chunk_data_size);

            if (err != OK) {
                return err;
            }

            *offset += chunk_size;
            break;
        }

        case FOURCC('s', 't', 's', 'c'):
        {
#ifndef ANDROID_DEFAULT_CODE
			if(mLastTrack->sampleTable == NULL){
				ALOGD("stsc  before stbl,sampleTable == NULL,break");
				*offset += chunk_size;
				break;
			}
#endif				
            status_t err =
                mLastTrack->sampleTable->setSampleToChunkParams(
                        data_offset, chunk_data_size);

            if (err != OK) {
                return err;
            }

            *offset += chunk_size;
            break;
        }

        case FOURCC('s', 't', 's', 'z'):
        case FOURCC('s', 't', 'z', '2'):
        {
#ifndef ANDROID_DEFAULT_CODE
			if(mLastTrack->sampleTable == NULL){
				ALOGD("stsz or stz2 before stbl,sampleTable == NULL,break");
				*offset += chunk_size;
				break;
			}
#endif			
            status_t err =
                mLastTrack->sampleTable->setSampleSizeParams(
                        chunk_type, data_offset, chunk_data_size);

            if (err != OK) {
                return err;
            }
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_RAW_SUPPORT
       int32_t pcmType=0,chanCount=0,bitWidth=0;
       const char *mimeStr;

       if(mLastTrack->meta->findInt32(kKeyPCMType, &pcmType) && (pcmType == 1) && mLastTrack->meta->findCString(kKeyMIMEType, &mimeStr) && !strcasecmp(mimeStr, MEDIA_MIMETYPE_AUDIO_RAW))
       {
           if(mLastTrack->meta->findInt32(kKeyChannelCount, &chanCount) && mLastTrack->meta->findInt32(kKeyBitWidth, &bitWidth))
		      mLastTrack->sampleTable->setPredictSampleSize(chanCount*bitWidth/8);
       }
#endif
#endif
#ifndef ANDROID_DEFAULT_CODE//hai.li to check unsupport video
            mLastTrack->sampleCount = mLastTrack->sampleTable->getSampleCount();
            //added by gary.wu to check final AU duration ALPS00613110
            mLastTrack->meta->setInt32(kKeySampleCount, (int32_t)mLastTrack->sampleTable->getSampleCount());
#endif

            size_t max_size;
#ifndef ANDROID_DEFAULT_CODE
            max_size=0;
            const uint32_t maxSampleCount = 100000;
            if (mLastTrack->sampleCount <= maxSampleCount) {
#endif
            err = mLastTrack->sampleTable->getMaxSampleSize(&max_size);
#ifndef ANDROID_DEFAULT_CODE
            }
#endif

#ifndef ANDROID_DEFAULT_CODE//hai.li for ISSUE: ALPS35871
      if (max_size > 1920*1088*3/2)        // maybe some abnormal size
	    {
		     //mLastTrack->skipTrack = true;
          max_size=1920*1088*3/2;
		      ALOGE("ERROR: Sample size may be wrong!set maxSize as:%d",max_size);
	    }
#endif
            if (err != OK) {
                return err;
            }

            if (max_size != 0) {
                // Assume that a given buffer only contains at most 10 chunks,
                // each chunk originally prefixed with a 2 byte length will
                // have a 4 byte header (0x00 0x00 0x00 0x01) after conversion,
                // and thus will grow by 2 bytes per chunk.
#ifndef ANDROID_DEFAULT_CODE
            if (mLastTrack->sampleCount <= maxSampleCount) {
#endif
                mLastTrack->meta->setInt32(kKeyMaxInputSize, max_size + 10 * 2);
#ifndef ANDROID_DEFAULT_CODE
            }
#endif
            } else {
                // No size was specified. Pick a conservatively large size.
                int32_t width, height;
                if (!mLastTrack->meta->findInt32(kKeyWidth, &width) ||
                    !mLastTrack->meta->findInt32(kKeyHeight, &height)) {
                    ALOGE("No width or height, assuming worst case 1080p");
                    width = 1920;
                    height = 1080;
                }

                const char *mime;
                CHECK(mLastTrack->meta->findCString(kKeyMIMEType, &mime));
                if (!strcmp(mime, MEDIA_MIMETYPE_VIDEO_AVC)) {
                    // AVC requires compression ratio of at least 2, and uses
                    // macroblocks
                    max_size = ((width + 15) / 16) * ((height + 15) / 16) * 192;
                } else {
                    // For all other formats there is no minimum compression
                    // ratio. Use compression ratio of 1.
                    max_size = width * height * 3 / 2;
                }
                mLastTrack->meta->setInt32(kKeyMaxInputSize, max_size);
            }
            *offset += chunk_size;

            // NOTE: setting another piece of metadata invalidates any pointers (such as the
            // mimetype) previously obtained, so don't cache them.
            const char *mime;
            CHECK(mLastTrack->meta->findCString(kKeyMIMEType, &mime));
            // Calculate average frame rate.
            if (!strncasecmp("video/", mime, 6)) {
                size_t nSamples = mLastTrack->sampleTable->countSamples();
                int64_t durationUs;
                if (mLastTrack->meta->findInt64(kKeyDuration, &durationUs)) {
                    if (durationUs > 0) {
                        int32_t frameRate = (nSamples * 1000000LL +
                                    (durationUs >> 1)) / durationUs;
                        mLastTrack->meta->setInt32(kKeyFrameRate, frameRate);
                    }
                }
            }

            break;
        }

        case FOURCC('s', 't', 't', 's'):
        {
#ifndef ANDROID_DEFAULT_CODE
			if(mLastTrack->sampleTable == NULL){
				ALOGD("stts before stbl ,sampleTable == NULL,break");
				*offset += chunk_size;
				break;
			}
            status_t err =
                mLastTrack->sampleTable->setTimeToSampleParams(
                        data_offset, chunk_data_size, mLastTrack->timescaleFactor);
            //added by gary.wu to check final AU duration ALPS00613110
            uint32_t mTimeToSampleCount = mLastTrack->sampleTable->getTimeToSampleCount();
            uint32_t *mTimeToSampleTable = mLastTrack->sampleTable->getTimeToSample();
            if( NULL != mTimeToSampleTable )
            {
                //ALOGD("sampleCount= %d, duration= %d", mTimeToSampleTable[0], mTimeToSampleTable[1]);
                mLastTrack->meta->setPointer(kKeyTimeToSampleTable, mTimeToSampleTable);
            }
            bool returnA =mLastTrack->meta->setInt32(kKeyTimeToSampleNumberEntry, (int32_t)mTimeToSampleCount);
            //ALOGI("return %d, timescaleFactor %d, mTimeToSampleCount=%d", returnA, mLastTrack->timescaleFactor, mTimeToSampleCount);
            //added by gary.wu to check final AU duration ALPS00613110 end
#else
            status_t err =
                mLastTrack->sampleTable->setTimeToSampleParams(
                        data_offset, chunk_data_size);
#endif

            if (err != OK) {
                return err;
            }

            *offset += chunk_size;
            break;
        }

        case FOURCC('c', 't', 't', 's'):
        {
#ifndef ANDROID_DEFAULT_CODE
			if(mLastTrack->sampleTable == NULL){
				ALOGD("ctsc before stbl ,sampleTable == NULL,break");
				*offset += chunk_size;
				break;
			}
            status_t err =
                mLastTrack->sampleTable->setCompositionTimeToSampleParams(
                        data_offset, chunk_data_size, mLastTrack->timescaleFactor);
#else
            status_t err =
                mLastTrack->sampleTable->setCompositionTimeToSampleParams(
                        data_offset, chunk_data_size);
#endif
            if (err != OK) {
                return err;
            }

            *offset += chunk_size;
            break;
        }

        case FOURCC('s', 't', 's', 's'):
        {
#ifndef ANDROID_DEFAULT_CODE   // ALPS00779876: Audio track doesn't need 'stss' table, each frame is sync frame
		if(mLastTrack->sampleTable == NULL){
			ALOGD("stss before stbl ,sampleTable == NULL,break");
			*offset += chunk_size;
			break;
		}
	    const char *mime;
	    mLastTrack->meta->findCString(kKeyMIMEType, &mime);

	    if(strncasecmp("audio/", mime, 6))
#endif
	    {
            status_t err =
                mLastTrack->sampleTable->setSyncSampleParams(
                        data_offset, chunk_data_size);

            if (err != OK) {
                return err;
            }
	    }
            *offset += chunk_size;
            break;
        }

        // @xyz
        case FOURCC('\xA9', 'x', 'y', 'z'):
        {
            // Best case the total data length inside "@xyz" box
            // would be 8, for instance "@xyz" + "\x00\x04\x15\xc7" + "0+0/",
            // where "\x00\x04" is the text string length with value = 4,
            // "\0x15\xc7" is the language code = en, and "0+0" is a
            // location (string) value with longitude = 0 and latitude = 0.
            if (chunk_data_size < 8) {
                return ERROR_MALFORMED;
            }

            // Worst case the location string length would be 18,
            // for instance +90.0000-180.0000, without the trailing "/" and
            // the string length + language code.
#ifndef ANDROID_DEFAULT_CODE
			char buffer[30];
#else
			char buffer[18];
#endif


            // Substracting 5 from the data size is because the text string length +
            // language code takes 4 bytes, and the trailing slash "/" takes 1 byte.
            off64_t location_length = chunk_data_size - 5;
            if (location_length >= (off64_t) sizeof(buffer)) {
                return ERROR_MALFORMED;
            }

            if (mDataSource->readAt(
                        data_offset + 4, buffer, location_length) < location_length) {
                return ERROR_IO;
            }

            buffer[location_length] = '\0';
            mFileMetaData->setCString(kKeyLocation, buffer);
            *offset += chunk_size;
            break;
        }

#ifndef ANDROID_DEFAULT_CODE			
        case FOURCC('g', 'l', 'b', 'l'):
        {
            if (chunk_data_size < 4) {
                return ERROR_MALFORMED;
            }

            uint8_t  *buffer = (uint8_t *)malloc(chunk_data_size);
            if (buffer == NULL) {
              return -ENOMEM;
            }
            
            if (mDataSource->readAt(
                        data_offset, buffer, chunk_data_size) < chunk_data_size) {
				free(buffer);
				ALOGE("ERROR_IO, LINE=%d", __LINE__);
                return ERROR_IO;
            }
			mLastTrack->meta->setData(kKeyMPEG4VOS, 0,buffer,chunk_data_size);
            *offset += chunk_size;
			free(buffer);
            break;
        }
#endif
        case FOURCC('e', 's', 'd', 's'):
        {
            if (chunk_data_size < 4) {
                return ERROR_MALFORMED;
            }
#ifndef ANDROID_DEFAULT_CODE			
            if (chunk_data_size > 4000){
                     return ERROR_BUFFER_TOO_SMALL;
            }
                      
            uint8_t  *buffer = (uint8_t *)malloc(chunk_data_size);

            if (buffer == NULL) {
              return -ENOMEM;
            }


            if (mDataSource->readAt(
                        data_offset, buffer, chunk_data_size) < chunk_data_size) {
				free(buffer);
				ALOGE("ERROR_IO, LINE=%d", __LINE__);
                return ERROR_IO;
            }

            if (U32_AT(buffer) != 0) {
                // Should be version 0, flags 0.
				free(buffer);
				ALOGE("ERROR_MALFORMED, LINE=%d", __LINE__);
                return ERROR_MALFORMED;
            }
#else
            uint8_t buffer[256];
            if (chunk_data_size > (off64_t)sizeof(buffer)) {
                return ERROR_BUFFER_TOO_SMALL;
            }

            if (mDataSource->readAt(
                        data_offset, buffer, chunk_data_size) < chunk_data_size) {
                return ERROR_IO;
            }

            if (U32_AT(buffer) != 0) {
                // Should be version 0, flags 0.
                return ERROR_MALFORMED;
            }
#endif
            mLastTrack->meta->setData(
                    kKeyESDS, kTypeESDS, &buffer[4], chunk_data_size - 4);

            if (mPath.size() >= 2
                    && mPath[mPath.size() - 2] == FOURCC('m', 'p', '4', 'a')) {
                // Information from the ESDS must be relied on for proper
                // setup of sample rate and channel count for MPEG4 Audio.
                // The generic header appears to only contain generic
                // information...

                status_t err = updateAudioTrackInfoFromESDS_MPEG4Audio(
                        &buffer[4], chunk_data_size - 4);
#ifndef ANDROID_DEFAULT_CODE			
                if (err != OK) {
					mLastTrack->skipTrack = true;
                }

				const char* mime;
				if (mLastTrack->meta->findCString(kKeyMIMEType, &mime) &&
					(!strcmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG))) {
					ALOGE("Is MP3 Audio, remove esds codec info");
					mLastTrack->meta->remove(kKeyESDS);
				}
#else
                if (err != OK) {
                    return err;
                }
#endif
            }

#ifndef ANDROID_DEFAULT_CODE
			if (mPath.size() >= 2
					&& mPath[mPath.size() - 2] == FOURCC('m', 'p', '4', 'v')) {
				
				//mLastTrack->meta->remove(kKeyESDS);//We should send esds to decoder for 3rd party applications, e.x. VideoEditor.
				ESDS esds(&buffer[4], chunk_data_size - 4);
				if (esds.InitCheck() == OK) {
					const void *codec_specific_data;
					size_t codec_specific_data_size;
					esds.getCodecSpecificInfo(
							&codec_specific_data, &codec_specific_data_size);
					mLastTrack->meta->setData(kKeyMPEG4VOS, 0, codec_specific_data, codec_specific_data_size);
				}
				else if (ERROR_UNSUPPORTED == esds.InitCheck())
				{
					ALOGW("Get vos from the first frame");
					mLastTrack->meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
				}
				else {
					ALOGE("Parse esds error, skip video track");
					mLastTrack->skipTrack = true;
				}

			}
#endif
            *offset += chunk_size;
#ifndef ANDROID_DEFAULT_CODE			
			free(buffer);
#endif  // ANDROID_DEFAULT_CODE
            break;
        }
#ifndef ANDROID_DEFAULT_CODE		
#ifdef MTK_VIDEO_HEVC_SUPPORT
		case FOURCC('h', 'v', 'c', 'C'):
		{						
			if (chunk_data_size > 1792){
				return ERROR_BUFFER_TOO_SMALL;
			}			  
			uint8_t *buffer = (uint8_t *)malloc(chunk_data_size);
			if ((buffer == NULL)) {
				return -ENOMEM;
			}
			if (mDataSource->readAt( data_offset, buffer, chunk_data_size) < chunk_data_size) {
			   free(buffer);
			   ALOGE("ERROR_IO, LINE=%d", __LINE__);
				return ERROR_IO;
			}
			if(buffer[0] != 1) {
				ALOGD("configurationVersion != 1");
				mLastTrack->skipTrack = true;
			}
			mLastTrack->meta->setData( kKeyHVCC, kTypeHVCC, buffer, chunk_data_size);
			if (chunk_data_size < 23) {
				ALOGW("Warning: HVCC size:%d < 23", chunk_data_size);
			 	mLastTrack->skipTrack = true;
			}
			*offset += chunk_size;
			free(buffer);
			break;
		}
#endif
#endif
        case FOURCC('a', 'v', 'c', 'C'):
        {
            sp<ABuffer> buffer = new ABuffer(chunk_data_size);

            if (mDataSource->readAt(
                        data_offset, buffer->data(), chunk_data_size) < chunk_data_size) {
                return ERROR_IO;
            }
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_VIDEO_HEVC_SUPPORT         
        uint8_t *ptr1 = (uint8_t *)buffer->data();
	      if (ptr1[1] == 0xFF) {       // profile 0xFF, is HEVC. PC HEVC assign
		     ALOGI("PC hevc_mtk");
		     mLastTrack->meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_HEVC);
	      }
#endif
         mLastTrack->meta->setData( kKeyAVCC, kTypeAVCC, buffer->data(), chunk_data_size);
		 if (chunk_data_size < 7) {
                     ALOGW("Warning: AVCC size:%lld < 7", chunk_data_size);
		     mLastTrack->skipTrack = true;
		 }
            *offset += chunk_size;
#else
            mLastTrack->meta->setData( kKeyAVCC, kTypeAVCC, buffer->data(), chunk_data_size);

            *offset += chunk_size;
#endif	
            break;
        }

        case FOURCC('d', '2', '6', '3'):
        {
            /*
             * d263 contains a fixed 7 bytes part:
             *   vendor - 4 bytes
             *   version - 1 byte
             *   level - 1 byte
             *   profile - 1 byte
             * optionally, "d263" box itself may contain a 16-byte
             * bit rate box (bitr)
             *   average bit rate - 4 bytes
             *   max bit rate - 4 bytes
             */
            char buffer[23];
#ifndef ANDROID_DEFAULT_CODE//Some files do not comply this rule
			if (chunk_data_size > 23){
#else
            if (chunk_data_size != 7 &&
                chunk_data_size != 23) {
#endif
                ALOGE("Incorrect D263 box size %lld", chunk_data_size);
                return ERROR_MALFORMED;
            }

            if (mDataSource->readAt(
                    data_offset, buffer, chunk_data_size) < chunk_data_size) {
                return ERROR_IO;
            }

            mLastTrack->meta->setData(kKeyD263, kTypeD263, buffer, chunk_data_size);

            *offset += chunk_size;
            break;
        }

        case FOURCC('m', 'e', 't', 'a'):
        {
            uint8_t buffer[4];
            if (chunk_data_size < (off64_t)sizeof(buffer)) {
                return ERROR_MALFORMED;
            }

            if (mDataSource->readAt(
                        data_offset, buffer, 4) < 4) {
                return ERROR_IO;
            }

            if (U32_AT(buffer) != 0) {
                // Should be version 0, flags 0.

                // If it's not, let's assume this is one of those
                // apparently malformed chunks that don't have flags
                // and completely different semantics than what's
                // in the MPEG4 specs and skip it.
                *offset += chunk_size;
                return OK;
            }

            off64_t stop_offset = *offset + chunk_size;
            *offset = data_offset + sizeof(buffer);
            while (*offset < stop_offset) {
                status_t err = parseChunk(offset, depth + 1);
                if (err != OK) {
                    return err;
                }
            }

            if (*offset != stop_offset) {
                return ERROR_MALFORMED;
            }
            break;
        }
#ifdef MTK_PLAYREADY_SUPPORT
        case FOURCC('u', 'u', 'i', 'd'):
        {
	    if (mPath.size() >= 3
		    && mPath[mPath.size() - 2] == FOURCC('s', 'c', 'h', 'i')) {
                    parseDrmSINFTrack(offset, data_offset);
	    }
            else if (mPath.size() >= 2
		    && mPath[mPath.size() - 2] == FOURCC('m', 'o', 'o', 'v')) {
                    parseDrmSINFMOOV(offset, data_offset);
	    }
            *offset += chunk_size;
	    break;
        }
#endif
        case FOURCC('m', 'e', 'a', 'n'):
        case FOURCC('n', 'a', 'm', 'e'):
        case FOURCC('d', 'a', 't', 'a'):
        {
            if (mPath.size() == 6 && underMetaDataPath(mPath)) {
                status_t err = parseMetaData(data_offset, chunk_data_size);

                if (err != OK) {
                    return err;
                }
            }

            *offset += chunk_size;
            break;
        }

        case FOURCC('m', 'v', 'h', 'd'):
        {
            if (chunk_data_size < 24) {
                return ERROR_MALFORMED;
            }

			uint8_t header[24];
            if (mDataSource->readAt(
                        data_offset, header, sizeof(header))
                    < (ssize_t)sizeof(header)) {
                return ERROR_IO;
            }

            uint64_t creationTime;
            if (header[0] == 1) {
                creationTime = U64_AT(&header[4]);
                mHeaderTimescale = U32_AT(&header[20]);
            } else if (header[0] != 0) {
                return ERROR_MALFORMED;
            } else {
                creationTime = U32_AT(&header[4]);
                mHeaderTimescale = U32_AT(&header[12]);

            }

            String8 s;
            convertTimeToDate(creationTime, &s);

            mFileMetaData->setCString(kKeyDate, s.string());

            *offset += chunk_size;
            break;
        }

        case FOURCC('m', 'd', 'a', 't'):
        {
            ALOGV("mdat chunk, drm: %d", mIsDrm);
            if (!mIsDrm) {
                *offset += chunk_size;
                break;
            }

            if (chunk_size < 8) {
                return ERROR_MALFORMED;
            }

            return parseDrmSINF(offset, data_offset);
        }

        case FOURCC('h', 'd', 'l', 'r'):
        {
            uint32_t buffer;
            if (mDataSource->readAt(
                        data_offset + 8, &buffer, 4) < 4) {
                return ERROR_IO;
            }

            uint32_t type = ntohl(buffer);
            // For the 3GPP file format, the handler-type within the 'hdlr' box
            // shall be 'text'. We also want to support 'sbtl' handler type
            // for a practical reason as various MPEG4 containers use it.
            if (type == FOURCC('t', 'e', 'x', 't') || type == FOURCC('s', 'b', 't', 'l')) {
                mLastTrack->meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_TEXT_3GPP);
            }
			
#ifdef MTK_SUBTITLE_SUPPORT
            if (type == FOURCC('s', 'u', 'b', 'p')) {
                mLastTrack->meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_TEXT_VOBSUB);
            }
#endif

            *offset += chunk_size;
            break;
        }

#ifdef MTK_SUBTITLE_SUPPORT
        case FOURCC('m', 'p', '4', 's'):
#endif
        case FOURCC('t', 'x', '3', 'g'):
        {
            uint32_t type;
            const void *data;
            size_t size = 0;
            if (!mLastTrack->meta->findData(
                    kKeyTextFormatData, &type, &data, &size)) {
                size = 0;
            }

            uint8_t *buffer = new uint8_t[size + chunk_size];

            if (size > 0) {
                memcpy(buffer, data, size);
            }

            if ((size_t)(mDataSource->readAt(*offset, buffer + size, chunk_size))
                    < chunk_size) {
                delete[] buffer;
                buffer = NULL;

                return ERROR_IO;
            }

            mLastTrack->meta->setData(
                    kKeyTextFormatData, 0, buffer, size + chunk_size);

            delete[] buffer;

            *offset += chunk_size;
            break;
        }

        case FOURCC('c', 'o', 'v', 'r'):
        {
            if (mFileMetaData != NULL) {
                ALOGV("chunk_data_size = %lld and data_offset = %lld",
                        chunk_data_size, data_offset);
                sp<ABuffer> buffer = new ABuffer(chunk_data_size + 1);
                if (mDataSource->readAt(
                    data_offset, buffer->data(), chunk_data_size) != (ssize_t)chunk_data_size) {
                    return ERROR_IO;
                }
                const int kSkipBytesOfDataBox = 16;
                mFileMetaData->setData(
                    kKeyAlbumArt, MetaData::TYPE_NONE,
                    buffer->data() + kSkipBytesOfDataBox, chunk_data_size - kSkipBytesOfDataBox);
            }

            *offset += chunk_size;
            break;
        }

        case FOURCC('-', '-', '-', '-'):
        {
            mLastCommentMean.clear();
            mLastCommentName.clear();
            mLastCommentData.clear();
            *offset += chunk_size;
            break;
        }

        case FOURCC('s', 'i', 'd', 'x'):
        {
            parseSegmentIndex(data_offset, chunk_data_size);
            *offset += chunk_size;
            return UNKNOWN_ERROR; // stop parsing after sidx
        }
#ifdef MTK_PLAYREADY_SUPPORT
        case FOURCC('t', 'f', 'r', 'a'):
        {
            parseMovieFragment(data_offset, chunk_data_size);
            *offset += chunk_size;
            break;
        }
        case FOURCC('m', 'f', 'r', 'o'):
        {
            *offset += chunk_size;
            return UNKNOWN_ERROR; // stop parsing after mfro, here is end of file
        }
#endif
#if !defined(ANDROID_DEFAULT_CODE) && defined(QUICKTIME_SUPPORT)//for .mov file
        case FOURCC('c', 'h', 'a', 'n'):
        {
            // this box for channel layout, current not use
            ALOGI("chan box for channle layout");
            *offset += chunk_size;
            break;
        }
#endif
        default:
        {
            *offset += chunk_size;
            break;
        }
    }

    return OK;
}
#ifdef MTK_PLAYREADY_SUPPORT
/*
aligned(8) class TrackFragmentRandomAccessBox
unsigned int(32) track_ID;
const unsigned int(26) reserved = 0;
unsigned int(2) length_size_of_traf_num;
unsigned int(2) length_size_of_trun_num;
unsigned int(2) length_size_of_sample_num;
unsigned int(32) number_of_entry;
for(i=1; i <= number_of_entry; i++){
if(version==1){
unsigned int(64) time;
unsigned int(64) moof_offset;
}else{
unsigned int(32) time;
unsigned int(32) moof_offset;
}
unsigned int((length_size_of_traf_num+1) * 8) traf_number;
unsigned int((length_size_of_trun_num+1) * 8) trun_number;
unsigned int((length_size_of_sample_num+1) * 8) sample_number;
}
}
struct MfraEntry{
    uint32_t trackid;
    uint64_t mTime;
    uint64_t mMoofOffset;
    uint64_t mTrafNumber;
    uint64_t mTrunNumber;
    uint64_t mSampleNumber;
};
*/
uint32_t getUInt(uint8_t *data, int size) {
    switch (size) {
	case 1:
            return data[0];         
	case 2:
            return U16_AT(data); 
	case 3:
	    return data[0] << 16 | data[1] << 8 | data[2];
	case 4:
            return U32_AT(data);
	default:
	    CHECK(!"Should not be here.");
	    break;
    }
    return 0;
}
status_t MPEG4Extractor::parseMovieFragment(off64_t offset, size_t size) {
    ALOGD("MPEG4Extractor::parseMovieFragment");

    if (size < 12) {
      return -EINVAL;
    }

    uint32_t flags;
    if (!mDataSource->getUInt32(offset, &flags)) {
        return ERROR_MALFORMED;
    }

    uint32_t version = flags >> 24;
    flags &= 0xffffff;

    ALOGI("sidx version %d", version);

    //get track id
    uint32_t trackid;
    if (!mDataSource->getUInt32(offset+4, &trackid)) {
	return ERROR_MALFORMED;
    }
    ALOGI("trackid :%d", trackid);

    // get length size
    uint32_t tmpLen;
    if (!mDataSource->getUInt32(offset+8, &tmpLen)) {
        return ERROR_MALFORMED;
    }
    uint8_t length_size_of_traf_num = (tmpLen & 0x3F) >> 4;
    uint8_t length_size_of_trun_num = (tmpLen & 0x0C) >> 2;
    uint8_t length_size_of_sample_num = (tmpLen & 0x03);

    // get entry
    uint32_t number_of_entry;
    if (!mDataSource->getUInt32(offset+12, &number_of_entry)) {
        return ERROR_MALFORMED;
    }
    offset += 16;
    ALOGI("mfra mumber_of_entry:%d", number_of_entry);
    for(int i=0; i<number_of_entry; i++) {
        MfraEntry me;
        me.trackid = trackid;
	uint32_t size = (length_size_of_traf_num+1) + (length_size_of_trun_num+1) + (length_size_of_sample_num+1); 
        uint8_t *data = NULL;
        uint8_t *buf = NULL;
	if(version == 1){
            data = new uint8_t[size+16];
	    if (!mDataSource->readAt(offset, data, size+16)) {
		delete [] data;
		return ERROR_MALFORMED;
	    }
            offset += (size+16);
            me.mTime = U64_AT(&data[0]);
	    ALOGV("mfra add, mTime:%lld", me.mTime);
            me.mMoofOffset = U64_AT(&data[8]);
            buf = data + 16;
	} else {
            data = new uint8_t[size+8];
	    if (!mDataSource->readAt(offset, data, size+8)) {
		delete [] data;
		return ERROR_MALFORMED;
	    }
            offset += (size+8);
            me.mTime = U32_AT(&data[0]);
	    ALOGV("mfra add, mTime:%d", me.mTime);
            me.mMoofOffset = U32_AT(&data[4]);
            buf = data + 8;
	}
	me.mTrafNumber = getUInt(buf, length_size_of_traf_num+1);
        buf += (length_size_of_traf_num+1);
	me.mTrunNumber = getUInt(buf, (length_size_of_trun_num+1));
        buf += (length_size_of_trun_num+1);
	me.mSampleNumber = getUInt(buf, (length_size_of_sample_num+1));
        buf += (length_size_of_sample_num+1);
        mMfraEntries.add(me);
        delete [] data;
    }

    return OK;
}
#endif
status_t MPEG4Extractor::parseSegmentIndex(off64_t offset, size_t size) {
  ALOGV("MPEG4Extractor::parseSegmentIndex");

    if (size < 12) {
      return -EINVAL;
    }

    uint32_t flags;
    if (!mDataSource->getUInt32(offset, &flags)) {
        return ERROR_MALFORMED;
    }

    uint32_t version = flags >> 24;
    flags &= 0xffffff;

    ALOGV("sidx version %d", version);

    uint32_t referenceId;
    if (!mDataSource->getUInt32(offset + 4, &referenceId)) {
        return ERROR_MALFORMED;
    }

    uint32_t timeScale;
    if (!mDataSource->getUInt32(offset + 8, &timeScale)) {
        return ERROR_MALFORMED;
    }
    ALOGV("sidx refid/timescale: %d/%d", referenceId, timeScale);

    uint64_t earliestPresentationTime;
    uint64_t firstOffset;

    offset += 12;
    size -= 12;

    if (version == 0) {
        if (size < 8) {
            return -EINVAL;
        }
        uint32_t tmp;
        if (!mDataSource->getUInt32(offset, &tmp)) {
            return ERROR_MALFORMED;
        }
        earliestPresentationTime = tmp;
        if (!mDataSource->getUInt32(offset + 4, &tmp)) {
            return ERROR_MALFORMED;
        }
        firstOffset = tmp;
        offset += 8;
        size -= 8;
    } else {
        if (size < 16) {
            return -EINVAL;
        }
        if (!mDataSource->getUInt64(offset, &earliestPresentationTime)) {
            return ERROR_MALFORMED;
        }
        if (!mDataSource->getUInt64(offset + 8, &firstOffset)) {
            return ERROR_MALFORMED;
        }
        offset += 16;
        size -= 16;
    }
    ALOGV("sidx pres/off: %Ld/%Ld", earliestPresentationTime, firstOffset);

    if (size < 4) {
        return -EINVAL;
    }

    uint16_t referenceCount;
    if (!mDataSource->getUInt16(offset + 2, &referenceCount)) {
        return ERROR_MALFORMED;
    }
    offset += 4;
    size -= 4;
    ALOGV("refcount: %d", referenceCount);

    if (size < referenceCount * 12) {
        return -EINVAL;
    }

    uint64_t total_duration = 0;
    for (unsigned int i = 0; i < referenceCount; i++) {
        uint32_t d1, d2, d3;

        if (!mDataSource->getUInt32(offset, &d1) ||     // size
            !mDataSource->getUInt32(offset + 4, &d2) || // duration
            !mDataSource->getUInt32(offset + 8, &d3)) { // flags
            return ERROR_MALFORMED;
        }

        if (d1 & 0x80000000) {
            ALOGW("sub-sidx boxes not supported yet");
        }
        bool sap = d3 & 0x80000000;
        bool saptype = d3 >> 28;
        if (!sap || saptype > 2) {
            ALOGW("not a stream access point, or unsupported type");
        }
        total_duration += d2;
        offset += 12;
        ALOGV(" item %d, %08x %08x %08x", i, d1, d2, d3);
        SidxEntry se;
        se.mSize = d1 & 0x7fffffff;
        se.mDurationUs = 1000000LL * d2 / timeScale;
        mSidxEntries.add(se);
    }

    mSidxDuration = total_duration * 1000000 / timeScale;
    ALOGV("duration: %lld", mSidxDuration);

    int64_t metaDuration;
    if (!mLastTrack->meta->findInt64(kKeyDuration, &metaDuration) || metaDuration == 0) {
        mLastTrack->meta->setInt64(kKeyDuration, mSidxDuration);
    }
    return OK;
}



status_t MPEG4Extractor::parseTrackHeader(
        off64_t data_offset, off64_t data_size) {
    if (data_size < 4) {
        return ERROR_MALFORMED;
    }

    uint8_t version;
    if (mDataSource->readAt(data_offset, &version, 1) < 1) {
        return ERROR_IO;
    }

    size_t dynSize = (version == 1) ? 36 : 24;

    uint8_t buffer[36 + 60];

    if (data_size != (off64_t)dynSize + 60) {
        return ERROR_MALFORMED;
    }

    if (mDataSource->readAt(
                data_offset, buffer, data_size) < (ssize_t)data_size) {
        return ERROR_IO;
    }

    uint64_t ctime, mtime, duration;
    int32_t id;

    if (version == 1) {
        ctime = U64_AT(&buffer[4]);
        mtime = U64_AT(&buffer[12]);
        id = U32_AT(&buffer[20]);
        duration = U64_AT(&buffer[28]);
		} else if (version == 0) {
        ctime = U32_AT(&buffer[4]);
        mtime = U32_AT(&buffer[8]);
        id = U32_AT(&buffer[12]);
        duration = U32_AT(&buffer[20]);
	} else {
        return ERROR_UNSUPPORTED;
    }

    mLastTrack->meta->setInt32(kKeyTrackID, id);

    size_t matrixOffset = dynSize + 16;
    int32_t a00 = U32_AT(&buffer[matrixOffset]);
    int32_t a01 = U32_AT(&buffer[matrixOffset + 4]);
    int32_t dx = U32_AT(&buffer[matrixOffset + 8]);
    int32_t a10 = U32_AT(&buffer[matrixOffset + 12]);
    int32_t a11 = U32_AT(&buffer[matrixOffset + 16]);
    int32_t dy = U32_AT(&buffer[matrixOffset + 20]);

#if 0
    ALOGI("x' = %.2f * x + %.2f * y + %.2f",
         a00 / 65536.0f, a01 / 65536.0f, dx / 65536.0f);
    ALOGI("y' = %.2f * x + %.2f * y + %.2f",
         a10 / 65536.0f, a11 / 65536.0f, dy / 65536.0f);
#endif

    uint32_t rotationDegrees;

    static const int32_t kFixedOne = 0x10000;
    if (a00 == kFixedOne && a01 == 0 && a10 == 0 && a11 == kFixedOne) {
        // Identity, no rotation
        rotationDegrees = 0;
    } else if (a00 == 0 && a01 == kFixedOne && a10 == -kFixedOne && a11 == 0) {
        rotationDegrees = 90;
    } else if (a00 == 0 && a01 == -kFixedOne && a10 == kFixedOne && a11 == 0) {
        rotationDegrees = 270;
    } else if (a00 == -kFixedOne && a01 == 0 && a10 == 0 && a11 == -kFixedOne) {
        rotationDegrees = 180;
    } else {
        ALOGW("We only support 0,90,180,270 degree rotation matrices");
        rotationDegrees = 0;
    }

    if (rotationDegrees != 0) {
        mLastTrack->meta->setInt32(kKeyRotation, rotationDegrees);
    }

    // Handle presentation display size, which could be different
    // from the image size indicated by kKeyWidth and kKeyHeight.
    uint32_t width = U32_AT(&buffer[dynSize + 52]);
    uint32_t height = U32_AT(&buffer[dynSize + 56]);
    mLastTrack->meta->setInt32(kKeyDisplayWidth, width >> 16);
    mLastTrack->meta->setInt32(kKeyDisplayHeight, height >> 16);

    return OK;
}

status_t MPEG4Extractor::parseMetaData(off64_t offset, size_t size) {
    if (size < 4) {
        return ERROR_MALFORMED;
    }

    uint8_t *buffer = new uint8_t[size + 1];
    if (mDataSource->readAt(
                offset, buffer, size) != (ssize_t)size) {
        delete[] buffer;
        buffer = NULL;

        return ERROR_IO;
    }

    uint32_t flags = U32_AT(buffer);

    uint32_t metadataKey = 0;
    char chunk[5];
    MakeFourCCString(mPath[4], chunk);
    ALOGV("meta: %s @ %lld", chunk, offset);
    switch (mPath[4]) {
        case FOURCC(0xa9, 'a', 'l', 'b'):
        {
            metadataKey = kKeyAlbum;
            break;
        }
        case FOURCC(0xa9, 'A', 'R', 'T'):
        {
            metadataKey = kKeyArtist;
            break;
        }
        case FOURCC('a', 'A', 'R', 'T'):
        {
            metadataKey = kKeyAlbumArtist;
            break;
        }
        case FOURCC(0xa9, 'd', 'a', 'y'):
        {
            metadataKey = kKeyYear;
            break;
        }
        case FOURCC(0xa9, 'n', 'a', 'm'):
        {
            metadataKey = kKeyTitle;
            break;
        }
        case FOURCC(0xa9, 'w', 'r', 't'):
        {
            metadataKey = kKeyWriter;
            break;
        }
        case FOURCC('c', 'o', 'v', 'r'):
        {
            metadataKey = kKeyAlbumArt;
            break;
        }
        case FOURCC('g', 'n', 'r', 'e'):
        {
            metadataKey = kKeyGenre;
            break;
        }
        case FOURCC(0xa9, 'g', 'e', 'n'):
        {
            metadataKey = kKeyGenre;
            break;
        }
        case FOURCC('c', 'p', 'i', 'l'):
        {
            if (size == 9 && flags == 21) {
                char tmp[16];
                sprintf(tmp, "%d",
                        (int)buffer[size - 1]);

                mFileMetaData->setCString(kKeyCompilation, tmp);
            }
            break;
        }
        case FOURCC('t', 'r', 'k', 'n'):
        {
            if (size == 16 && flags == 0) {
                char tmp[16];
                uint16_t* pTrack = (uint16_t*)&buffer[10];
                uint16_t* pTotalTracks = (uint16_t*)&buffer[12];
                sprintf(tmp, "%d/%d", ntohs(*pTrack), ntohs(*pTotalTracks));

                mFileMetaData->setCString(kKeyCDTrackNumber, tmp);
            }
            break;
        }
        case FOURCC('d', 'i', 's', 'k'):
        {
            if ((size == 14 || size == 16) && flags == 0) {
                char tmp[16];
                uint16_t* pDisc = (uint16_t*)&buffer[10];
                uint16_t* pTotalDiscs = (uint16_t*)&buffer[12];
                sprintf(tmp, "%d/%d", ntohs(*pDisc), ntohs(*pTotalDiscs));

                mFileMetaData->setCString(kKeyDiscNumber, tmp);
            }
            break;
        }
        case FOURCC('-', '-', '-', '-'):
        {
            buffer[size] = '\0';
            switch (mPath[5]) {
                case FOURCC('m', 'e', 'a', 'n'):
                    mLastCommentMean.setTo((const char *)buffer + 4);
                    break;
                case FOURCC('n', 'a', 'm', 'e'):
                    mLastCommentName.setTo((const char *)buffer + 4);
                    break;
                case FOURCC('d', 'a', 't', 'a'):
                    mLastCommentData.setTo((const char *)buffer + 8);
                    break;
            }

            // Once we have a set of mean/name/data info, go ahead and process
            // it to see if its something we are interested in.  Whether or not
            // were are interested in the specific tag, make sure to clear out
            // the set so we can be ready to process another tuple should one
            // show up later in the file.
            if ((mLastCommentMean.length() != 0) &&
                (mLastCommentName.length() != 0) &&
                (mLastCommentData.length() != 0)) {

            if (mLastCommentMean == "com.apple.iTunes"
                        && mLastCommentName == "iTunSMPB") {
                int32_t delay, padding;
                if (sscanf(mLastCommentData,
                           " %*x %x %x %*x", &delay, &padding) == 2) {
                    mLastTrack->meta->setInt32(kKeyEncoderDelay, delay);
                    mLastTrack->meta->setInt32(kKeyEncoderPadding, padding);
                }
                }

                mLastCommentMean.clear();
                mLastCommentName.clear();
                mLastCommentData.clear();
            }
            break;
        }

        default:
            break;
    }

    if (size >= 8 && metadataKey) {
        if (metadataKey == kKeyAlbumArt) {
            mFileMetaData->setData(
                    kKeyAlbumArt, MetaData::TYPE_NONE,
                    buffer + 8, size - 8);
        } else if (metadataKey == kKeyGenre) {
            if (flags == 0) {
                // uint8_t genre code, iTunes genre codes are
                // the standard id3 codes, except they start
                // at 1 instead of 0 (e.g. Pop is 14, not 13)
                // We use standard id3 numbering, so subtract 1.
                int genrecode = (int)buffer[size - 1];
                genrecode--;
                if (genrecode < 0) {
                    genrecode = 255; // reserved for 'unknown genre'
                }
                char genre[10];
                sprintf(genre, "%d", genrecode);

                mFileMetaData->setCString(metadataKey, genre);
            } else if (flags == 1) {
                // custom genre string
                buffer[size] = '\0';

                mFileMetaData->setCString(
                        metadataKey, (const char *)buffer + 8);
            }
        } else {
            buffer[size] = '\0';

            mFileMetaData->setCString(
                    metadataKey, (const char *)buffer + 8);
        }
    }

    delete[] buffer;
    buffer = NULL;

    return OK;
}

sp<MediaSource> MPEG4Extractor::getTrack(size_t index) {
    status_t err;
    if ((err = readMetaData()) != OK) {
        return NULL;
    }

    Track *track = mFirstTrack;
    while (index > 0) {
        if (track == NULL) {
            return NULL;
        }

        track = track->next;
        --index;
    }

    if (track == NULL) {
        return NULL;
    }

    ALOGV("getTrack called, pssh: %d", mPssh.size());

#ifndef ANDROID_DEFAULT_CODE
		if ((track->mElstEntries != NULL) && 
			(track->mStartTimeOffset != 0) &&
			(mHeaderTimescale != 0))
		{	
			track->sampleTable->setStartTimeOffset(track->mStartTimeOffset*track->timescale/mHeaderTimescale);
			ALOGD("track->mStartTimeOffset=%d, track->timescale=%d, mHeaderTimescale=%d", track->mStartTimeOffset, track->timescale, mHeaderTimescale);
			
			const char *mime;
			CHECK(track->meta->findCString(kKeyMIMEType, &mime));
			if (!strncasecmp("audio/", mime, 6)) {
				uint32_t PadSampleNum = track->sampleTable->getStartTimeOffset();
				if (PadSampleNum >= 512*1024)
				{
					ALOGW("Unsupported too large audio time offset: %d samples!!", PadSampleNum);
					track->sampleTable->setStartTimeOffset(0);
					track->mStartTimeOffset = 0;
				}
				ALOGE("audio time offset=%d", track->sampleTable->getStartTimeOffset());
				if (track->sampleTable->getStartTimeOffset() != 0)
					track->meta->setInt32(kKeyAudioPadEnable, true);
			}
		}//added by hai.li to support track time offset
    track->meta->setInt32(kKeySupportTryRead, 1);
#endif

#ifdef MTK_PLAYREADY_SUPPORT
    return new MPEG4Source(
            track->meta, mDataSource, track->timescale, track->sampleTable,
            mSidxEntries, mMfraEntries, mMoofOffset);
#else
    return new MPEG4Source(
            track->meta, mDataSource, track->timescale, track->sampleTable,
            mSidxEntries, mMoofOffset);
#endif
}

// static
status_t MPEG4Extractor::verifyTrack(Track *track) {
    const char *mime;
    CHECK(track->meta->findCString(kKeyMIMEType, &mime));

    uint32_t type;
    const void *data;
    size_t size;
    if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC)) {
        if (!track->meta->findData(kKeyAVCC, &type, &data, &size)
                || type != kTypeAVCC) {
            return ERROR_MALFORMED;
        }
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_MPEG4)
            || !strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AAC)) {
        if (!track->meta->findData(kKeyESDS, &type, &data, &size)
                || type != kTypeESDS) {
            return ERROR_MALFORMED;
        }
    }

    if (!track->sampleTable->isValid()) {
        // Make sure we have all the metadata we need.
        return ERROR_MALFORMED;
    }

    return OK;
}

status_t MPEG4Extractor::updateAudioTrackInfoFromESDS_MPEG4Audio(
        const void *esds_data, size_t esds_size) {
    ESDS esds(esds_data, esds_size);

    uint8_t objectTypeIndication;
    if (esds.getObjectTypeIndication(&objectTypeIndication) != OK) {
        return ERROR_MALFORMED;
    }

    if (objectTypeIndication == 0xe1) {
        // This isn't MPEG4 audio at all, it's QCELP 14k...
        mLastTrack->meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_QCELP);
#ifndef ANDROID_DEFAULT_CODE
		mLastTrack->skipTrack = true;
		ALOGD("Skip qcelp audio track");
#endif
        return OK;
    }
#ifndef ANDROID_DEFAULT_CODE                                        //xingyu.zhou
	if (objectTypeIndication == 0x6B || objectTypeIndication == 0x69) {
		mLastTrack->meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG);
		mLastTrack->meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
		return OK;
	}
#else
    if (objectTypeIndication  == 0x6b) {
        // The media subtype is MP3 audio
        // Our software MP3 audio decoder may not be able to handle
        // packetized MP3 audio; for now, lets just return ERROR_UNSUPPORTED
        ALOGE("MP3 track in MP4/3GPP file is not supported");
        return ERROR_UNSUPPORTED;
    }
#endif
    const uint8_t *csd;
    size_t csd_size;
    if (esds.getCodecSpecificInfo(
                (const void **)&csd, &csd_size) != OK) {
        return ERROR_MALFORMED;
    }

#if 0
    printf("ESD of size %d\n", csd_size);
    hexdump(csd, csd_size);
#endif

    if (csd_size == 0) {
        // There's no further information, i.e. no codec specific data
        // Let's assume that the information provided in the mpeg4 headers
        // is accurate and hope for the best.

        return OK;
    }

    if (csd_size < 2) {
        return ERROR_MALFORMED;
    }

    ABitReader br(csd, csd_size);
    uint32_t objectType = br.getBits(5);

    if (objectType == 31) {  // AAC-ELD => additional 6 bits
        objectType = 32 + br.getBits(6);
    }
#ifndef ANDROID_DEFAULT_CODE
    ALOGD("objectType:%d", objectType);
   mLastTrack->meta->setInt32(kKeyAacObjType, objectType);
#endif
    uint32_t freqIndex = br.getBits(4);

    int32_t sampleRate = 0;
    int32_t numChannels = 0;
    if (freqIndex == 15) {
        if (csd_size < 5) {
            return ERROR_MALFORMED;
        }
        sampleRate = br.getBits(24);
        numChannels = br.getBits(4);
    } else {
        numChannels = br.getBits(4);
        if (objectType == 5) {
            // SBR specific config per 14496-3 table 1.13
            freqIndex = br.getBits(4);
            if (freqIndex == 15) {
                if (csd_size < 8) {
                    return ERROR_MALFORMED;
                }
                sampleRate = br.getBits(24);
            }
        }

        if (sampleRate == 0) {
            static uint32_t kSamplingRate[] = {
                96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
                16000, 12000, 11025, 8000, 7350
            };

            if (freqIndex == 13 || freqIndex == 14) {
                return ERROR_MALFORMED;
            }

            sampleRate = kSamplingRate[freqIndex];
        }
    }

    if (numChannels == 0) {
        return ERROR_UNSUPPORTED;
    }

    int32_t prevSampleRate;
    CHECK(mLastTrack->meta->findInt32(kKeySampleRate, &prevSampleRate));

    if (prevSampleRate != sampleRate) {
        ALOGV("mpeg4 audio sample rate different from previous setting. "
             "was: %d, now: %d", prevSampleRate, sampleRate);
    }

    mLastTrack->meta->setInt32(kKeySampleRate, sampleRate);

    int32_t prevChannelCount;
    CHECK(mLastTrack->meta->findInt32(kKeyChannelCount, &prevChannelCount));

    if (prevChannelCount != numChannels) {
        ALOGV("mpeg4 audio channel count different from previous setting. "
             "was: %d, now: %d", prevChannelCount, numChannels);
    }

    mLastTrack->meta->setInt32(kKeyChannelCount, numChannels);

    return OK;
}
#ifndef ANDROID_DEFAULT_CODE
static const uint32_t kMP3HeaderMask = 0xfffe0c00;//0xfffe0cc0 add by zhihui zhang no consider channel mode
static bool IsSeeminglyValidMPEGAudioHeader(const uint8_t *ptr, size_t size) {
    if (size < 3) {
        // Not enough data to verify header.
        return false;
    }

    if (ptr[0] != 0xff || (ptr[1] >> 5) != 0x07) {
        return false;
    }

    unsigned ID = (ptr[1] >> 3) & 3;

    if (ID == 1) {
        return false;  // reserved
    }

    unsigned layer = (ptr[1] >> 1) & 3;

    if (layer == 0) {
        return false;  // reserved
    }

    unsigned bitrateIndex = (ptr[2] >> 4);

    if (bitrateIndex == 0x0f) {
        return false;  // reserved
    }

    unsigned samplingRateIndex = (ptr[2] >> 2) & 3;

    if (samplingRateIndex == 3) {
        return false;  // reserved
    }

    return true;
}
static status_t findMP3Header(const uint8_t* buf, ssize_t size, ssize_t *offset, int *pHeader) {
    uint32_t header1 = 0, header2 = 0;
    size_t frameSize = 0, frameSize2 = 0;
    bool retb = false;
    //header1 = U32_AT(buf+*offset);
    while (*offset+4 < size) {
	//bool retb = GetMPEGAudioFrameSize(header1, &frameSize,NULL,NULL,NULL,NULL); 
	//if(!retb)
	{
	    //find 1st header and verify	
	    for (ssize_t i = *offset; i < size - 4; i++) {
		if (IsSeeminglyValidMPEGAudioHeader(&buf[i], size-i)) {
		    *offset = i;
		    header1 = U32_AT(buf+*offset);
		    retb = GetMPEGAudioFrameSize(header1, &frameSize,NULL,NULL,NULL,NULL); 
		    if(!retb || (frameSize == 0))
		    {
			ALOGV("1.%s err 0x%x, ofst/retb/fSz=%d/%d/%d\n", __FUNCTION__, header1, *offset, retb, frameSize);
			continue;
		    }
		    else
		    {
			ALOGV("2.%s 0x%x, ofst/retb/fSz=%d/%d/%d\n", __FUNCTION__, header1, *offset, retb, frameSize);
			break;
		    }
		}
	    }
	    if(!retb || (frameSize == 0)){
		break;
	    }
	}
	//find 2nd header and verify
	if (*offset+ssize_t(frameSize) < size)
	{
	    *offset += frameSize;
	    header2 = U32_AT(buf+*offset);
            ALOGI("header1:%x, header2:%x, off:%d, framesize:%d", header1, header2, *offset, frameSize);
	    if ((header2 & kMP3HeaderMask) == (header1 & kMP3HeaderMask)) {
		*pHeader = header1;
                *offset -= frameSize;
		return OK;
	    }
	    else if(GetMPEGAudioFrameSize(header2, &frameSize2,NULL,NULL,NULL,NULL) && (frameSize2 > 0)){
		header1 = header2;
		//ALOGI("3.%s 2nd 0x%x, ofst/fSz/Sz %d/%d/%d\n", __FUNCTION__, header2, *offset, frameSize2, size);	
	    }
	    else //header1's frameSize has problem, re-find header1
	    {
		*offset -= (frameSize - 1);
		//ALOGI("4.%s 2nd err 0x%x, new ofst/fSz/sz %d/%d/%d\n", __FUNCTION__, header2, *offset, frameSize2, size);	
	    }
	}
	else {
	    ALOGI("frame overflow buffer");
	    break;
	}
    }
    ALOGI("%s():size:%d,buf:%2x %2x %2x %2x %2x %2x %2x %2x",__FUNCTION__, size,
	    buf[0],buf[1],buf[2],buf[3],buf[4],buf[5],buf[6],buf[7]);
    return UNKNOWN_ERROR;
}
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
            *frame_size = 72000 * bitrate / sampling_rate + padding;
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

status_t MPEG4Extractor::setCodecInfoFromFirstFrame(Track *track)
{
	off64_t frame_offset = 0;
	size_t  frame_size = 0;
	void*   frame_data = NULL;
	track->sampleTable->getMetaDataForSample(0, &frame_offset, &frame_size, NULL, NULL);
	frame_data = malloc(frame_size);
	if (NULL == frame_data){
		ALOGE("malloc first frame data buffer fail!");
		return ERROR_BUFFER_TOO_SMALL;
	}
	
	if (mDataSource->readAt(
				frame_offset, frame_data, frame_size)
			< (int32_t)frame_size) {
		ALOGE("read first frame fail!!");
		return ERROR_IO;
	}
	
	const char* mime;
	if (!track->meta->findCString(kKeyMIMEType, &mime))
	{
		ALOGE("No mime type track!!");
		return UNKNOWN_ERROR;
	}

	if (!strcmp(mime, MEDIA_MIMETYPE_VIDEO_MPEG4))
	{
		size_t vosend;
		for (vosend=0; (vosend < 200) && (vosend < frame_size-4); vosend++)
		{
			if (0xB6010000 == *(uint32_t*)((uint8_t*)frame_data + vosend))
			{
				break;//Send VOS until VOP
			}
		}
		track->meta->setData(kKeyMPEG4VOS, 0, frame_data, vosend);
		for (uint32_t i=0; i<vosend; i++)
			ALOGD("VOS[%d] = 0x%x", i, *((uint8_t *)frame_data + i));
	}

	if (!strcmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG))
	{
	    uint32_t header = *(uint32_t*)(frame_data);
	    header = ((header >> 24) & 0xff) | ((header >> 8) & 0xff00) | ((header << 8) & 0xff0000) | ((header << 24) & 0xff000000); 
	    ALOGD("HEADER=0x%x", header);

	    // check MP3Header, avoid firat Header is uncorrect?
	    int sampleIndex = 0;
	    uint8_t *data = (uint8_t *)frame_data;
	    size_t size = frame_size;
	    ssize_t offset = 0;
            size_t sampleSize[10];
            sampleSize[0] = frame_size;
            int mp3Header = (int)header;
	    while (sampleIndex < 10) {
		if (findMP3Header(data, size, &offset, &mp3Header) == OK){
		    ALOGI("mp3 header:0x%x, off:%d", mp3Header, offset);
                    // check the offset
		    ssize_t tmpOff = offset;
                    bool found = false;
		    for(int i=0; i<=sampleIndex; i++) {
			if (tmpOff < 0) {
			    if ( i >= 2) {
				track->sampleTable->setSkipSample((int32_t(i-2)));
				ALOGI("skipNum:%d", i-2);
			    }
			    if (tmpOff+sampleSize[i-1] > 0) {
				ALOGI("off:%d", tmpOff+sampleSize[i-1]);
				track->sampleTable->setSkipOff(uint32_t(tmpOff+sampleSize[i-1]));
			    }
			    header = (uint32_t)mp3Header;
			    found = true;
			    break;
			}
                        ALOGV("sampleSize:%d, tmpOff:%d, i:%d", sampleSize[i], tmpOff, i);
			tmpOff -= sampleSize[i]; 
		    }
                    if (found)
                        break;
		}
                offset = 0;

                // get metadata info for sampleIndex
		sampleIndex++;
		track->sampleTable->getMetaDataForSample(sampleIndex, &frame_offset, &frame_size, NULL, NULL);
                sampleSize[sampleIndex] = frame_size;

                // new a large buffer, copy the old data to it
		uint8_t *dataNew = (uint8_t *)malloc(frame_size + size);
		if (NULL == dataNew){
		    ALOGE("malloc frame data buffer fail!");
		    return ERROR_BUFFER_TOO_SMALL;
		}
                memcpy(dataNew, data, size);
                free(data);
                data = dataNew;

                // read new sample 
		frame_data = data + size; 
		size += frame_size;
		if (mDataSource->readAt(
			    frame_offset, frame_data, frame_size)
			< (int32_t)frame_size) {
		    ALOGE("read first frame fail!!");
		    free(data);
		    return ERROR_IO;
		}
	    }
	    free(data);
            data = NULL;
            frame_data = NULL;

	    size_t  out_framesize;
	    int32_t out_sampling_rate;
	    int32_t out_channels;
	    int32_t out_bitrate;
	    if(get_mp3_info(header, &out_framesize, &out_sampling_rate, &out_channels, &out_bitrate))
	    {
		ALOGD("mp3: out_framesize=%d, sample_rate=%d, channel_count=%d, out_bitrate=%d", 
			out_framesize, out_sampling_rate, out_channels, out_bitrate);
		track->meta->setInt32(kKeySampleRate, out_sampling_rate);
		track->meta->setInt32(kKeyChannelCount, out_channels);
	    }
	    else
	    {
		ALOGE("Get mp3 info fail");   // should not return error, or else the whole file can not play. 
	    }
	}
	if (frame_data != NULL)
	    free(frame_data);
	return OK;
}

#ifdef MTK_S3D_SUPPORT
status_t MPEG4Extractor::getFirstNal(Track *track, size_t *nal_offset, size_t *nal_size)
{
	
	off64_t frame_offset;
	size_t  frame_size;

	if (NULL == nal_offset || NULL == nal_size)
		return UNKNOWN_ERROR;
	
	//Get Nal length size-->
	uint32_t type;
	const void *data;
	size_t size;
	size_t nalLengthSize;
	CHECK(track->meta->findData(kKeyAVCC, &type, &data, &size));
	
	const uint8_t *ptr = (const uint8_t *)data;
	
	CHECK(size >= 7);
	CHECK_EQ((unsigned)ptr[0], 1u);  // configurationVersion == 1
	
	// The number of bytes used to encode the length of a NAL unit.
	nalLengthSize = 1 + (ptr[4] & 3);
	//Get Nal length size<--
	
	track->sampleTable->getMetaDataForSample(0, &frame_offset, &frame_size, NULL, NULL);

	uint8_t buffer[4];
	if (mDataSource->readAt(
				frame_offset, buffer, nalLengthSize)
			< nalLengthSize) {
		ALOGE("read first nal size fail!!");
		return ERROR_IO;
	}

    switch (nalLengthSize) {
        case 1:
            *nal_size = buffer[0];
			break;
        case 2:
            *nal_size = U16_AT(buffer);
			break;
        case 3:
            *nal_size = ((size_t)buffer[0] << 16) | U16_AT(&buffer[1]);
			break;
        case 4:
            *nal_size = U32_AT(buffer);
			break;
    }

	if (frame_size < nalLengthSize + *nal_size) {
		ALOGE("incomplete first NAL unit.frame_size=%d, nalLengthSize=%d, nal_size=%d", frame_size, nalLengthSize, *nal_size);
		return ERROR_MALFORMED;
	}
/*
	nal_data = malloc(nal_size);

	if (NULL == nal_data) {
		ALOGE("nal_data = malloc(nal_size) FAIL!!");
		return ERROR_BUFFER_TOO_SMALL;
	}
	
	if (mDataSource->readAt(
				frame_offset + nalLengthSize, nal_data, nal_size)
			< nalLengthSize) {
		ALOGE("read first nal fail!!");
		return ERROR_IO;
	}
*/

	*nal_offset = frame_offset + nalLengthSize;
	ALOGD("First Nal offset=%d, size=%d", *nal_offset, *nal_size);

	return OK;
	
	
}
#endif
#endif

////////////////////////////////////////////////////////////////////////////////

MPEG4Source::MPEG4Source(
        const sp<MetaData> &format,
        const sp<DataSource> &dataSource,
        int32_t timeScale,
        const sp<SampleTable> &sampleTable,
        Vector<SidxEntry> &sidx,
#ifdef MTK_PLAYREADY_SUPPORT
        Vector<MfraEntry> &mfra,
#endif
        off64_t firstMoofOffset)
    : mFormat(format),
      mDataSource(dataSource),
      mTimescale(timeScale),
      mSampleTable(sampleTable),
      mCurrentSampleIndex(0),
      mCurrentFragmentIndex(0),
      mSegments(sidx),
#ifdef MTK_PLAYREADY_SUPPORT
      mMovieFragments(mfra),
      mCodecSpecificDataIndex(0),
#endif
      mFirstMoofOffset(firstMoofOffset),
      mCurrentMoofOffset(firstMoofOffset),
      mCurrentTime(0),
      mCurrentSampleInfoAllocSize(0),
      mCurrentSampleInfoSizes(NULL),
      mCurrentSampleInfoOffsetsAllocSize(0),
      mCurrentSampleInfoOffsets(NULL),
      mIsAVC(false),
      mNALLengthSize(0),
      mStarted(false),
      mGroup(NULL),
      mBuffer(NULL),
      mWantsNALFragments(false),
#ifndef ANDROID_DEFAULT_CODE
      mZeroBufStart(-1),
#endif
      mSrcBuffer(NULL) {

    mFormat->findInt32(kKeyCryptoMode, &mCryptoMode);
    mDefaultIVSize = 0;
    mFormat->findInt32(kKeyCryptoDefaultIVSize, &mDefaultIVSize);
    uint32_t keytype;
    const void *key;
    size_t keysize;
    if (mFormat->findData(kKeyCryptoKey, &keytype, &key, &keysize)) {
        CHECK(keysize <= 16);
        memset(mCryptoKey, 0, 16);
        memcpy(mCryptoKey, key, keysize);
    }

    const char *mime;
    bool success = mFormat->findCString(kKeyMIMEType, &mime);
    CHECK(success);

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_VIDEO_HEVC_SUPPORT
    mIsAVC = !strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC) || !strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_HEVC);
#else
    mIsAVC = !strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC);
#endif
#else
    mIsAVC = !strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC);
#endif

    if (mIsAVC) {
        uint32_t type;
        const void *data;
        size_t size;
#ifndef ANDROID_DEFAULT_CODE		
		if(format->findData(kKeyAVCC, &type, &data, &size)){
			const uint8_t *ptr = (const uint8_t *)data;
			CHECK(size >= 7);
			CHECK_EQ((unsigned)ptr[0], 1u);  // configurationVersion == 1
			// The number of bytes used to encode the length of a NAL unit.
			mNALLengthSize = 1 + (ptr[4] & 3);
		}
#ifdef MTK_VIDEO_HEVC_SUPPORT		
		else if(format->findData(kKeyHVCC, &type, &data, &size)){
			const uint8_t *ptr = (const uint8_t *)data;
			CHECK(size >= 23);
			CHECK_EQ((unsigned)ptr[0], 1u);  // configurationVersion == 1

			// The number of bytes used to encode the length of a NAL unit.
			mNALLengthSize = 1 + (ptr[21] & 3);
		}
#endif		
#else		
		CHECK(format->findData(kKeyAVCC, &type, &data, &size));
		const uint8_t *ptr = (const uint8_t *)data;
		CHECK(size >= 7);
		CHECK_EQ((unsigned)ptr[0], 1u);  // configurationVersion == 1
		// The number of bytes used to encode the length of a NAL unit.
		mNALLengthSize = 1 + (ptr[4] & 3);
#endif	

#ifdef MTK_PLAYREADY_SUPPORT
	unsigned profile, level;
	status_t err;
	if ((err = parseAVCCodecSpecificData(
			data, size, &profile, &level)) != OK) {
	    ALOGE("Malformed AVC codec specific data.");
	}
#endif
		
    }

    CHECK(format->findInt32(kKeyTrackID, &mTrackId));

    if (mFirstMoofOffset != 0) {
        off64_t offset = mFirstMoofOffset;
        parseChunk(&offset);
    }
}
#ifdef MTK_PLAYREADY_SUPPORT
status_t MPEG4Source::parseAVCCodecSpecificData(
        const void *data, size_t size,
        unsigned *profile, unsigned *level) {
    const uint8_t *ptr = (const uint8_t *)data;

    // verify minimum size and configurationVersion == 1.
    if (size < 7 || ptr[0] != 1) {
        return ERROR_MALFORMED;
    }

    *profile = ptr[1];
    *level = ptr[3];

    // There is decodable content out there that fails the following
    // assertion, let's be lenient for now...
    // CHECK((ptr[4] >> 2) == 0x3f);  // reserved

    size_t lengthSize = 1 + (ptr[4] & 3);

    // commented out check below as H264_QVGA_500_NO_AUDIO.3gp
    // violates it...
    // CHECK((ptr[5] >> 5) == 7);  // reserved

    size_t numSeqParameterSets = ptr[5] & 31;

    ptr += 6;
    size -= 6;

    for (size_t i = 0; i < numSeqParameterSets; ++i) {
        if (size < 2) {
            return ERROR_MALFORMED;
        }

        size_t length = U16_AT(ptr);

        ptr += 2;
        size -= 2;

        if (size < length) {
            return ERROR_MALFORMED;
        }

        addCodecSpecificData(ptr, length);

        ptr += length;
        size -= length;
    }

    if (size < 1) {
        return ERROR_MALFORMED;
    }

    size_t numPictureParameterSets = *ptr;
    ++ptr;
    --size;

    for (size_t i = 0; i < numPictureParameterSets; ++i) {
        if (size < 2) {
            return ERROR_MALFORMED;
        }

        size_t length = U16_AT(ptr);

        ptr += 2;
        size -= 2;

        if (size < length) {
            return ERROR_MALFORMED;
        }

        addCodecSpecificData(ptr, length);

        ptr += length;
        size -= length;
    }

    return OK;
}

void MPEG4Source::addCodecSpecificData(const void *data, size_t size) {
    CodecSpecificData *specific =
        (CodecSpecificData *)malloc(sizeof(CodecSpecificData) + size - 1);

    specific->mSize = size;
    memcpy(specific->mData, data, size);

    mCodecSpecificData.push(specific);
}

void MPEG4Source::clearCodecSpecificData() {
    for (size_t i = 0; i < mCodecSpecificData.size(); ++i) {
        free(mCodecSpecificData.editItemAt(i));
    }
    mCodecSpecificData.clear();
    mCodecSpecificDataIndex = 0;
}
#endif

MPEG4Source::~MPEG4Source() {
#ifdef MTK_PLAYREADY_SUPPORT
    clearCodecSpecificData();
#endif
    if (mStarted) {
        stop();
    }
    free(mCurrentSampleInfoSizes);
    free(mCurrentSampleInfoOffsets);
}

status_t MPEG4Source::start(MetaData *params) {
    Mutex::Autolock autoLock(mLock);

    CHECK(!mStarted);

    int32_t val;
    if (params && params->findInt32(kKeyWantsNALFragments, &val)
        && val != 0) {
        mWantsNALFragments = true;
    } else {
        mWantsNALFragments = false;
    }
#ifdef MTK_PLAYREADY_SUPPORT
#if defined(TRUSTONIC_TEE_SUPPORT) && defined(MTK_SEC_VIDEO_PATH_SUPPORT) 
    mWantsNALFragments = false;
    ALOGI("SVP do not use nal fragments");
#endif
#endif

    mGroup = new MediaBufferGroup;

    int32_t max_size;
    CHECK(mFormat->findInt32(kKeyMaxInputSize, &max_size));

    mGroup->add_buffer(new MediaBuffer(max_size));
#ifndef ANDROID_DEFAULT_CODE
#ifdef NOT_USE_PCMOMX
    int32_t bitsPerSample = 0;
    if (mFormat->findInt32(kKeyBitWidth, &bitsPerSample) && bitsPerSample == 8) {
        // As a temporary buffer for 8->16 bit conversion.
        mGroup->add_buffer(new MediaBuffer(max_size));
    }
#endif
#endif

    mSrcBuffer = new uint8_t[max_size];

    mStarted = true;

    return OK;
}

status_t MPEG4Source::stop() {
    Mutex::Autolock autoLock(mLock);

    CHECK(mStarted);

    if (mBuffer != NULL) {
        mBuffer->release();
        mBuffer = NULL;
    }

    delete[] mSrcBuffer;
    mSrcBuffer = NULL;

    delete mGroup;
    mGroup = NULL;

    mStarted = false;
    mCurrentSampleIndex = 0;

    return OK;
}
#ifdef MTK_PLAYREADY_SUPPORT
status_t MPEG4Source::parseChunk(off64_t *offset, MfraEntry *mfra) {
#else
status_t MPEG4Source::parseChunk(off64_t *offset) {
#endif
    uint32_t hdr[2];
    if (mDataSource->readAt(*offset, hdr, 8) < 8) {
        return ERROR_IO;
    }
    uint64_t chunk_size = ntohl(hdr[0]);
    uint32_t chunk_type = ntohl(hdr[1]);
    off64_t data_offset = *offset + 8;

    if (chunk_size == 1) {
        if (mDataSource->readAt(*offset + 8, &chunk_size, 8) < 8) {
            return ERROR_IO;
        }
        chunk_size = ntoh64(chunk_size);
        data_offset += 8;

        if (chunk_size < 16) {
            // The smallest valid chunk is 16 bytes long in this case.
            return ERROR_MALFORMED;
        }
    } else if (chunk_size < 8) {
        // The smallest valid chunk is 8 bytes long.
        return ERROR_MALFORMED;
    }

    char chunk[5];
    MakeFourCCString(chunk_type, chunk);
    ALOGV("MPEG4Source chunk %s @ %llx", chunk, *offset);

    off64_t chunk_data_size = *offset + chunk_size - data_offset;

    switch(chunk_type) {

        case FOURCC('t', 'r', 'a', 'f'):
        case FOURCC('m', 'o', 'o', 'f'): {
            off64_t stop_offset = *offset + chunk_size;
            *offset = data_offset;
#ifdef MTK_PLAYREADY_SUPPORT
            // if seek , should mTrafNumer == 1 
	    if (mfra != NULL && chunk_type == FOURCC('t', 'r', 'a', 'f') && mfra->mTrafNumber > 1) {
		mfra->mTrafNumber -= 1;
		*offset += chunk_size;
		break;
	    }
#endif
            while (*offset < stop_offset) {
#ifdef MTK_PLAYREADY_SUPPORT
                status_t err = parseChunk(offset, mfra);
#else
                status_t err = parseChunk(offset);
#endif
                if (err != OK) {
                    return err;
                }
            }
            if (chunk_type == FOURCC('m', 'o', 'o', 'f')) {
                // *offset points to the mdat box following this moof
                parseChunk(offset); // doesn't actually parse it, just updates offset
                mNextMoofOffset = *offset;
            }
            break;
        }

        case FOURCC('t', 'f', 'h', 'd'): {
                status_t err;
                if ((err = parseTrackFragmentHeader(data_offset, chunk_data_size)) != OK) {
                    return err;
                }
                *offset += chunk_size;
                break;
        }

        case FOURCC('t', 'r', 'u', 'n'): {
                status_t err;
#ifdef MTK_PLAYREADY_SUPPORT
                ALOGI("lastTrackId:%d mTrackId:%d", mLastParsedTrackId, mTrackId);
		if (mfra !=NULL && mfra->mTrafNumber == 1) {
		    if (mfra->mTrunNumber == 1) {
			if (mLastParsedTrackId == mTrackId) {
			    if (mfra->mSampleNumber > 1) {
				if ((err = parseTrackFragmentRun(data_offset, chunk_data_size, mfra->mSampleNumber)) != OK) {
				    return err;
				}
			    }
			    else {
				if ((err = parseTrackFragmentRun(data_offset, chunk_data_size)) != OK) {
				    return err;
				}
			    }
			}
		    }
		    else if (mfra->mTrunNumber > 1) {
			mfra->mTrunNumber -= 1;
		    }
		} else {
		    if (mLastParsedTrackId == mTrackId) {
			if ((err = parseTrackFragmentRun(data_offset, chunk_data_size)) != OK) {
			    return err;
			}
		    }
		}
#else
                if (mLastParsedTrackId == mTrackId) {
                    if ((err = parseTrackFragmentRun(data_offset, chunk_data_size)) != OK) {
                        return err;
                    }
                }
#endif
                *offset += chunk_size;
                break;
        }

        case FOURCC('s', 'a', 'i', 'z'): {
            status_t err;
            if ((err = parseSampleAuxiliaryInformationSizes(data_offset, chunk_data_size)) != OK) {
                return err;
            }
            *offset += chunk_size;
            break;
        }
        case FOURCC('s', 'a', 'i', 'o'): {
            status_t err;
            if ((err = parseSampleAuxiliaryInformationOffsets(data_offset, chunk_data_size)) != OK) {
                return err;
            }
            *offset += chunk_size;
            break;
        }
#ifdef MTK_PLAYREADY_SUPPORT
        case FOURCC('u', 'u', 'i', 'd'): {
            status_t err;
	    if (mLastParsedTrackId == mTrackId) {
		if ((err = parseSampleUUid(data_offset, chunk_data_size)) != OK) {
		    return err;
		}
	    }
            *offset += chunk_size;
            break;
        }
#endif

        case FOURCC('m', 'd', 'a', 't'): {
            // parse DRM info if present
            ALOGV("MPEG4Source::parseChunk mdat");
            // if saiz/saoi was previously observed, do something with the sampleinfos
            *offset += chunk_size;
            break;
        }

        default: {
            *offset += chunk_size;
            break;
        }
    }
    return OK;
}

status_t MPEG4Source::parseSampleAuxiliaryInformationSizes(off64_t offset, off64_t size) {
    ALOGV("parseSampleAuxiliaryInformationSizes");
    // 14496-12 8.7.12
    uint8_t version;
    if (mDataSource->readAt(
            offset, &version, sizeof(version))
            < (ssize_t)sizeof(version)) {
        return ERROR_IO;
    }

    if (version != 0) {
        return ERROR_UNSUPPORTED;
    }
    offset++;

    uint32_t flags;
    if (!mDataSource->getUInt24(offset, &flags)) {
        return ERROR_IO;
    }
    offset += 3;

    if (flags & 1) {
        uint32_t tmp;
        if (!mDataSource->getUInt32(offset, &tmp)) {
            return ERROR_MALFORMED;
        }
        mCurrentAuxInfoType = tmp;
        offset += 4;
        if (!mDataSource->getUInt32(offset, &tmp)) {
            return ERROR_MALFORMED;
        }
        mCurrentAuxInfoTypeParameter = tmp;
        offset += 4;
    }

    uint8_t defsize;
    if (mDataSource->readAt(offset, &defsize, 1) != 1) {
        return ERROR_MALFORMED;
    }
    mCurrentDefaultSampleInfoSize = defsize;
    offset++;

    uint32_t smplcnt;
    if (!mDataSource->getUInt32(offset, &smplcnt)) {
        return ERROR_MALFORMED;
    }
    mCurrentSampleInfoCount = smplcnt;
    offset += 4;

    if (mCurrentDefaultSampleInfoSize != 0) {
        ALOGV("@@@@ using default sample info size of %d", mCurrentDefaultSampleInfoSize);
        return OK;
    }
    if (smplcnt > mCurrentSampleInfoAllocSize) {
        mCurrentSampleInfoSizes = (uint8_t*) realloc(mCurrentSampleInfoSizes, smplcnt);
        mCurrentSampleInfoAllocSize = smplcnt;
    }

    mDataSource->readAt(offset, mCurrentSampleInfoSizes, smplcnt);
    return OK;
}

#ifdef MTK_PLAYREADY_SUPPORT
//! structures of type DxSubSample (their count indicated by dwSubSamplesNum) 
typedef struct __tagDxMultiSampleHeader
{
	uint32_t dwOutBufferOffset;       /*!< Offset into the output buffer (FD) */
	uint64_t qwInitializationVector;  /*!< The IV from the PIFF file, used for the decryption of all NAL units in this call  */
	uint32_t dwMediaOffset ;          /*!< Will be always zero. Will be non zero in case NAL units in the same frame will be passed in different calls. */
	uint32_t dwSubSamplesNum;         /*!< Number of structures of type DxSubSample present */
} DxMultiSampleHeader;

//! Describes the size of one encrypted NAL with its clear header 
typedef struct __tagDxSubSample
{
	uint32_t dwClearDataSize;           /*!< Size of the data to be copied from the encBuffer to the output FD (decBuffer) */
	uint32_t dwEncryptedDataSize;       /*!< Size of the data to be decrypted into the decBuffer */
} DxSubSample;

#define GET_IV_BUFFER_LEN(_dwSubSamplesNum) \
	(sizeof(DxMultiSampleHeader) + (sizeof(DxSubSample) * (_dwSubSamplesNum)))

#define GET_SAMPLE_ARR_PTR(_IV_BUFFER) \
	((DxSubSample*)(((char*)(_IV_BUFFER)) + sizeof(DxMultiSampleHeader)))
status_t MPEG4Source::parseSampleUUid(off64_t offset, off64_t size) {
    uint8_t header[20];
    if (mDataSource->readAt(
		offset, header, sizeof(header))
	    < (ssize_t)sizeof(header)) {
	return ERROR_IO;
    }
    ALOGI("extend_type:%02x %02x %02x %02x %02x %02x %02x %02x", header[0], header[1], header[2], header[4],
	    header[12], header[13], header[14], header[15]);
    offset += 20;

    uint8_t flags = header[19];
    if (flags & 0x000001)
    {
	uint8_t idBuf[20];
	if (mDataSource->readAt(
		    offset, idBuf, sizeof(idBuf))
		< (ssize_t)sizeof(idBuf)) {
	    return ERROR_IO;
	}
	/*
	   unsigned int(24) AlgorithmID;
	   unsigned int(8) IV_size;
	   unsigned int(8)[16] KID;
	 */
        offset += 20 + 3 + 1 + 16;
    }
    uint8_t tmp[4];
    if (mDataSource->readAt(
		offset, tmp, sizeof(tmp))
	    < (ssize_t)sizeof(tmp)) {
	return ERROR_IO;
    }
    uint32_t sampleCount = U32_AT(&tmp[0]);
    offset += 4;
#if 0
    for (int i=0; i < sampleCount; i++) {
	uint8_t *initVector = new uint8_t[mDefaultIVSize];    
	if (mDataSource->readAt(
		    offset, initVector, mDefaultIVSize)
		< (ssize_t)mDefaultIVSize) {
	    return ERROR_IO;
	}
        offset += mDefaultIVSize;


	if (flags & 0x000002)
	{
	    uint8_t tmp[2];
	    if (mDataSource->readAt(
			offset, tmp, sizeof(tmp))
		    < (ssize_t)sizeof(tmp)) {
		return ERROR_IO;
	    }
            uint32_t numberOfEntries = U16_AT(&tmp[0]);
	    /*
	       {
	       unsigned int(16) BytesOfClearData;
	       unsigned int(32) BytesOfEncryptedData;
	       } [ NumberOfEntries]
	     */
            offset += 2 + (4+2) * numberOfEntries;
	}
    }
#endif
    int ivlength;
    CHECK(mFormat->findInt32(kKeyCryptoDefaultIVSize, &ivlength));
    CHECK_EQ(ivlength, 8);

    // read CencSampleAuxiliaryDataFormats
    ALOGI("mCurrentSamples.size():%d, sampleCount:%d, flags:%d, ivlength:%d", mCurrentSamples.size(), sampleCount, flags, ivlength);
    for (size_t i = 0; i < sampleCount; i++) {
        Sample *smpl = &mCurrentSamples.editItemAt(i);

	if (smpl == NULL) {
            ALOGE("smpl is null, i:%d", i);
            continue;
	}
        // DrmBuffer* IV ¿byte and block offsets combined in the buffer - 
        // The first 4 bytes contains the mediaOffset. The following 8 bytes contains the initializationVector.
        uint8_t IV8[8];
        memset(IV8, 0, 8);
        if (mDataSource->readAt(offset, IV8, ivlength) != ivlength) {
            return ERROR_IO;
        }
        // reverse iv
	uint8_t *tmp = (uint8_t *)IV8;
        ALOGV("IV : %02x %02x %02x %02x %02x %02x %02x %02x", tmp[0],tmp[1],tmp[2],tmp[3],tmp[4],tmp[5],tmp[6],tmp[7]);
        uint8_t oldIV[8];
        memcpy(oldIV, tmp, 8);
	for (int i=0; i<8; i++) {
            *(tmp+i) = oldIV[7-i]; 
	}
        ALOGV("IV reverse: %02x %02x %02x %02x %02x %02x %02x %02x", tmp[0],tmp[1],tmp[2],tmp[3],tmp[4],tmp[5],tmp[6],tmp[7]);

        offset += ivlength;
        uint16_t numsubsamples = 0;
	if (flags & 0x000002) {
	    if (!mDataSource->getUInt16(offset, &numsubsamples)) {
		return ERROR_IO;
	    }
            ALOGI("sample size:%d, numsubsample:%d", smpl->size, numsubsamples);
            offset += 2;
	    if (mWantsNALFragments) {
		CHECK(numsubsamples == 1);
	    }
            for (size_t j = 0; j < numsubsamples; j++) {
                uint16_t numclear;
                uint32_t numencrypted;
		if (!mDataSource->getUInt16(offset, &numclear)) {
		    return ERROR_IO;
		}
                offset += 2;
		if (!mDataSource->getUInt32(offset, &numencrypted)) {
		    return ERROR_IO;
		}
                offset += 4;
		if (mWantsNALFragments) {
		    numclear -= 4;           // nal len should be cut before send to decode
		}
                smpl->clearsizes.add(numclear);
		ALOGI("samplecount:%d encryptedsizes:%d, numclear:%d ",i, numencrypted, numclear);
                smpl->encryptedsizes.add(numencrypted);
            }
        } else {
            smpl->clearsizes.add(0);
            smpl->encryptedsizes.add(smpl->size);
            ALOGI("encrypt add i:%d", i);
	}
	// set sample iv
	/*
	   typedef __packed struct {
	   uint32_t dwOutBufferOffset;
	   uint64_t qwInitializationVector;
	   uint32_t dwMediaOffset ;
	   uint32_t dwSubSamplesNum;
	   } DxMultiSampleHeader;

	   typedef __packed struct {
	   uint32_t dwClearDataSize;
	   uint32_t dwEncryptedDataSize;
	   } DxSubSample;
	 */

	if (numsubsamples == 0) {         // all is encrypt data
            smpl->IVSize = GET_IV_BUFFER_LEN(1);
	    smpl->playReadyIV = new uint8_t[smpl->IVSize];
	    DxMultiSampleHeader *ivSet = (DxMultiSampleHeader*)(smpl->playReadyIV);
	    ivSet->dwOutBufferOffset = 0;
	    memcpy((void *)&(ivSet->qwInitializationVector), IV8, 8);       
	    ivSet->dwMediaOffset = 0;
	    ivSet->dwSubSamplesNum = 1;

	    DxSubSample *sampleAdd  = GET_SAMPLE_ARR_PTR(smpl->playReadyIV);
	    size_t dwClearDataSize = smpl->clearsizes.editItemAt(0);
	    size_t dwEncryptedDataSize = smpl->encryptedsizes.editItemAt(0);
	    sampleAdd->dwClearDataSize = dwClearDataSize;
	    sampleAdd->dwEncryptedDataSize = dwEncryptedDataSize;
	}
	else { 
            smpl->IVSize = GET_IV_BUFFER_LEN(numsubsamples);
	    smpl->playReadyIV = new uint8_t[smpl->IVSize];
	    DxMultiSampleHeader *ivSet = (DxMultiSampleHeader*)(smpl->playReadyIV);

	    ivSet->dwOutBufferOffset = 0;
	    memcpy((void *)&(ivSet->qwInitializationVector), IV8, 8);       
	    ivSet->dwMediaOffset = 0;
	    ivSet->dwSubSamplesNum = numsubsamples;

	    DxSubSample *sampleAdd  = GET_SAMPLE_ARR_PTR(smpl->playReadyIV);
	    for (int i=0; i<numsubsamples; i++) {
		size_t dwClearDataSize = smpl->clearsizes.editItemAt(i);
		size_t dwEncryptedDataSize = smpl->encryptedsizes.editItemAt(i);
		sampleAdd->dwClearDataSize = dwClearDataSize;
		sampleAdd->dwEncryptedDataSize = dwEncryptedDataSize;
                sampleAdd++;
	    }
	}
	ALOGV("ivsize:%d", smpl->IVSize);
    }

    return OK;
}
#endif
status_t MPEG4Source::parseSampleAuxiliaryInformationOffsets(off64_t offset, off64_t size) {
    ALOGV("parseSampleAuxiliaryInformationOffsets");
    // 14496-12 8.7.13
    uint8_t version;
    if (mDataSource->readAt(offset, &version, sizeof(version)) != 1) {
        return ERROR_IO;
    }
    offset++;

    uint32_t flags;
    if (!mDataSource->getUInt24(offset, &flags)) {
        return ERROR_IO;
    }
    offset += 3;

    uint32_t entrycount;
    if (!mDataSource->getUInt32(offset, &entrycount)) {
        return ERROR_IO;
    }
    offset += 4;

    if (entrycount > mCurrentSampleInfoOffsetsAllocSize) {
        mCurrentSampleInfoOffsets = (uint64_t*) realloc(mCurrentSampleInfoOffsets, entrycount * 8);
        mCurrentSampleInfoOffsetsAllocSize = entrycount;
    }
    mCurrentSampleInfoOffsetCount = entrycount;

    for (size_t i = 0; i < entrycount; i++) {
        if (version == 0) {
            uint32_t tmp;
            if (!mDataSource->getUInt32(offset, &tmp)) {
                return ERROR_IO;
            }
            mCurrentSampleInfoOffsets[i] = tmp;
            offset += 4;
        } else {
            uint64_t tmp;
            if (!mDataSource->getUInt64(offset, &tmp)) {
                return ERROR_IO;
            }
            mCurrentSampleInfoOffsets[i] = tmp;
            offset += 8;
        }
    }

    // parse clear/encrypted data

    off64_t drmoffset = mCurrentSampleInfoOffsets[0]; // from moof

    drmoffset += mCurrentMoofOffset;
    int ivlength;
    CHECK(mFormat->findInt32(kKeyCryptoDefaultIVSize, &ivlength));

    // read CencSampleAuxiliaryDataFormats
    for (size_t i = 0; i < mCurrentSampleInfoCount; i++) {
        Sample *smpl = &mCurrentSamples.editItemAt(i);

        memset(smpl->iv, 0, 16);
        if (mDataSource->readAt(drmoffset, smpl->iv, ivlength) != ivlength) {
            return ERROR_IO;
        }

        drmoffset += ivlength;

        int32_t smplinfosize = mCurrentDefaultSampleInfoSize;
        if (smplinfosize == 0) {
            smplinfosize = mCurrentSampleInfoSizes[i];
        }
        if (smplinfosize > ivlength) {
            uint16_t numsubsamples;
            if (!mDataSource->getUInt16(drmoffset, &numsubsamples)) {
                return ERROR_IO;
            }
            drmoffset += 2;
            for (size_t j = 0; j < numsubsamples; j++) {
                uint16_t numclear;
                uint32_t numencrypted;
                if (!mDataSource->getUInt16(drmoffset, &numclear)) {
                    return ERROR_IO;
                }
                drmoffset += 2;
                if (!mDataSource->getUInt32(drmoffset, &numencrypted)) {
                    return ERROR_IO;
                }
                drmoffset += 4;
                smpl->clearsizes.add(numclear);
                smpl->encryptedsizes.add(numencrypted);
            }
        } else {
            smpl->clearsizes.add(0);
            smpl->encryptedsizes.add(smpl->size);
        }
    }


    return OK;
}

status_t MPEG4Source::parseTrackFragmentHeader(off64_t offset, off64_t size) {

    if (size < 8) {
        return -EINVAL;
    }

    uint32_t flags;
    if (!mDataSource->getUInt32(offset, &flags)) { // actually version + flags
        return ERROR_MALFORMED;
    }

    if (flags & 0xff000000) {
        return -EINVAL;
    }

    if (!mDataSource->getUInt32(offset + 4, (uint32_t*)&mLastParsedTrackId)) {
        return ERROR_MALFORMED;
    }

    if (mLastParsedTrackId != mTrackId) {
        // this is not the right track, skip it
        return OK;
    }

    mTrackFragmentHeaderInfo.mFlags = flags;
    mTrackFragmentHeaderInfo.mTrackID = mLastParsedTrackId;
    offset += 8;
    size -= 8;

    ALOGV("fragment header: %08x %08x", flags, mTrackFragmentHeaderInfo.mTrackID);

    if (flags & TrackFragmentHeaderInfo::kBaseDataOffsetPresent) {
        if (size < 8) {
            return -EINVAL;
        }

        if (!mDataSource->getUInt64(offset, &mTrackFragmentHeaderInfo.mBaseDataOffset)) {
            return ERROR_MALFORMED;
        }
        offset += 8;
        size -= 8;
    }

    if (flags & TrackFragmentHeaderInfo::kSampleDescriptionIndexPresent) {
        if (size < 4) {
            return -EINVAL;
        }

        if (!mDataSource->getUInt32(offset, &mTrackFragmentHeaderInfo.mSampleDescriptionIndex)) {
            return ERROR_MALFORMED;
        }
        offset += 4;
        size -= 4;
    }

    if (flags & TrackFragmentHeaderInfo::kDefaultSampleDurationPresent) {
        if (size < 4) {
            return -EINVAL;
        }

        if (!mDataSource->getUInt32(offset, &mTrackFragmentHeaderInfo.mDefaultSampleDuration)) {
            return ERROR_MALFORMED;
        }
        offset += 4;
        size -= 4;
    }

    if (flags & TrackFragmentHeaderInfo::kDefaultSampleSizePresent) {
        if (size < 4) {
            return -EINVAL;
        }

        if (!mDataSource->getUInt32(offset, &mTrackFragmentHeaderInfo.mDefaultSampleSize)) {
            return ERROR_MALFORMED;
        }
        offset += 4;
        size -= 4;
    }

    if (flags & TrackFragmentHeaderInfo::kDefaultSampleFlagsPresent) {
        if (size < 4) {
            return -EINVAL;
        }

        if (!mDataSource->getUInt32(offset, &mTrackFragmentHeaderInfo.mDefaultSampleFlags)) {
            return ERROR_MALFORMED;
        }
        offset += 4;
        size -= 4;
    }

    if (!(flags & TrackFragmentHeaderInfo::kBaseDataOffsetPresent)) {
        mTrackFragmentHeaderInfo.mBaseDataOffset = mCurrentMoofOffset;
    }

    mTrackFragmentHeaderInfo.mDataOffset = 0;
    return OK;
}
#ifdef MTK_PLAYREADY_SUPPORT
status_t MPEG4Source::parseTrackFragmentRun(off64_t offset, off64_t size, uint32_t sampleNum) {
#else
status_t MPEG4Source::parseTrackFragmentRun(off64_t offset, off64_t size) {
#endif

    ALOGV("MPEG4Extractor::parseTrackFragmentRun");
    if (size < 8) {
        return -EINVAL;
    }

    enum {
        kDataOffsetPresent                  = 0x01,
        kFirstSampleFlagsPresent            = 0x04,
        kSampleDurationPresent              = 0x100,
        kSampleSizePresent                  = 0x200,
        kSampleFlagsPresent                 = 0x400,
        kSampleCompositionTimeOffsetPresent = 0x800,
    };

    uint32_t flags;
    if (!mDataSource->getUInt32(offset, &flags)) {
        return ERROR_MALFORMED;
    }
    ALOGV("fragment run flags: %08x", flags);

    if (flags & 0xff000000) {
        return -EINVAL;
    }

    if ((flags & kFirstSampleFlagsPresent) && (flags & kSampleFlagsPresent)) {
        // These two shall not be used together.
        return -EINVAL;
    }

    uint32_t sampleCount;
    if (!mDataSource->getUInt32(offset + 4, &sampleCount)) {
        return ERROR_MALFORMED;
    }
    offset += 8;
    size -= 8;

    uint64_t dataOffset = mTrackFragmentHeaderInfo.mDataOffset;

    uint32_t firstSampleFlags = 0;

    if (flags & kDataOffsetPresent) {
        if (size < 4) {
            return -EINVAL;
        }

        int32_t dataOffsetDelta;
        if (!mDataSource->getUInt32(offset, (uint32_t*)&dataOffsetDelta)) {
            return ERROR_MALFORMED;
        }

        dataOffset = mTrackFragmentHeaderInfo.mBaseDataOffset + dataOffsetDelta;

        offset += 4;
        size -= 4;
    }

    if (flags & kFirstSampleFlagsPresent) {
        if (size < 4) {
            return -EINVAL;
        }

        if (!mDataSource->getUInt32(offset, &firstSampleFlags)) {
            return ERROR_MALFORMED;
        }
        offset += 4;
        size -= 4;
    }

    uint32_t sampleDuration = 0, sampleSize = 0, sampleFlags = 0,
             sampleCtsOffset = 0;

    size_t bytesPerSample = 0;
    if (flags & kSampleDurationPresent) {
        bytesPerSample += 4;
    } else if (mTrackFragmentHeaderInfo.mFlags
            & TrackFragmentHeaderInfo::kDefaultSampleDurationPresent) {
        sampleDuration = mTrackFragmentHeaderInfo.mDefaultSampleDuration;
    } else {
        sampleDuration = mTrackFragmentHeaderInfo.mDefaultSampleDuration;
    }

    if (flags & kSampleSizePresent) {
        bytesPerSample += 4;
    } else if (mTrackFragmentHeaderInfo.mFlags
            & TrackFragmentHeaderInfo::kDefaultSampleSizePresent) {
        sampleSize = mTrackFragmentHeaderInfo.mDefaultSampleSize;
    } else {
        sampleSize = mTrackFragmentHeaderInfo.mDefaultSampleSize;
    }

    if (flags & kSampleFlagsPresent) {
        bytesPerSample += 4;
    } else if (mTrackFragmentHeaderInfo.mFlags
            & TrackFragmentHeaderInfo::kDefaultSampleFlagsPresent) {
        sampleFlags = mTrackFragmentHeaderInfo.mDefaultSampleFlags;
    } else {
        sampleFlags = mTrackFragmentHeaderInfo.mDefaultSampleFlags;
    }

    if (flags & kSampleCompositionTimeOffsetPresent) {
        bytesPerSample += 4;
    } else {
        sampleCtsOffset = 0;
    }

    if (size < sampleCount * bytesPerSample) {
        return -EINVAL;
    }

    Sample tmp;
    for (uint32_t i = 0; i < sampleCount; ++i) {
        if (flags & kSampleDurationPresent) {
            if (!mDataSource->getUInt32(offset, &sampleDuration)) {
                return ERROR_MALFORMED;
            }
            offset += 4;
        }

        if (flags & kSampleSizePresent) {
            if (!mDataSource->getUInt32(offset, &sampleSize)) {
                return ERROR_MALFORMED;
            }
            offset += 4;
        }

        if (flags & kSampleFlagsPresent) {
            if (!mDataSource->getUInt32(offset, &sampleFlags)) {
                return ERROR_MALFORMED;
            }
            offset += 4;
        }

        if (flags & kSampleCompositionTimeOffsetPresent) {
            if (!mDataSource->getUInt32(offset, &sampleCtsOffset)) {
                return ERROR_MALFORMED;
            }
            offset += 4;
        }

        ALOGV("adding sample %d at offset 0x%08llx, size %u, duration %u, "
              " flags 0x%08x", i + 1,
                dataOffset, sampleSize, sampleDuration,
                (flags & kFirstSampleFlagsPresent) && i == 0
                    ? firstSampleFlags : sampleFlags);
#ifdef MTK_PLAYREADY_SUPPORT
	if (sampleNum == 0 || sampleNum - 1 <= i) {           // sampleNum index from 1, used for select sample
#endif
        tmp.offset = dataOffset;
        tmp.size = sampleSize;
        tmp.duration = sampleDuration;
        mCurrentSamples.add(tmp);
#ifdef MTK_PLAYREADY_SUPPORT
}
#endif

        dataOffset += sampleSize;
    }

    mTrackFragmentHeaderInfo.mDataOffset = dataOffset;

    return OK;
}

sp<MetaData> MPEG4Source::getFormat() {
    Mutex::Autolock autoLock(mLock);

    return mFormat;
}

size_t MPEG4Source::parseNALSize(const uint8_t *data) const {
    switch (mNALLengthSize) {
        case 1:
            return *data;
        case 2:
            return U16_AT(data);
        case 3:
            return ((size_t)data[0] << 16) | U16_AT(&data[1]);
        case 4:
            return U32_AT(data);
    }

    // This cannot happen, mNALLengthSize springs to life by adding 1 to
    // a 2-bit integer.
    CHECK(!"Should not be here.");

    return 0;
}
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_RAW_SUPPORT
/*
   [In]
       

   [Out]

*/
status_t MPEG4Source::pcmread(MediaBuffer **out) {
    off64_t offset,offset2;
    size_t sampleSize;
    uint32_t cts;
    uint32_t bufCts = 0;
    uint32_t bufOff = 0;
    static const uint32_t maxSize = 8192;

    status_t err = mGroup->acquire_buffer(&mBuffer);
    if (err != OK) {
	CHECK(mBuffer == NULL);
	return err;
    }

    while (bufOff < maxSize) {
	err = mSampleTable->getMetaDataForSample(
		    mCurrentSampleIndex, &offset, &sampleSize, &cts, NULL);
	if (err != OK) {
	    if (err == ERROR_OUT_OF_RANGE)
		    err = ERROR_END_OF_STREAM;//Awesomeplayer only can handle this as eos
	    return err;
	}
   
    //read 2048 bytes if samples(fetchSize/sampleSize) lie in the same trunk.
    size_t fetchSize = 2048;
    while(fetchSize > sampleSize)
    {
       err = mSampleTable->getMetaDataForSample(
		    mCurrentSampleIndex+fetchSize/sampleSize, &offset2, NULL,NULL,NULL);
       //ALOGD("mCurrentSampleIndex:%u,fetchSize:%u,offset:%lld,offset2:%lld",mCurrentSampleIndex,fetchSize,offset,offset2);
       if(err == OK && ((offset2-offset) == fetchSize))//in the same trunk
           break;
       else
           fetchSize /= 2;
    }
    
    if (bufCts == 0) {       // use first cts
	    bufCts = cts;
	}

	ssize_t num_bytes_read =
	    mDataSource->readAt(offset, (uint8_t *)mBuffer->data()+bufOff, fetchSize);
	if (num_bytes_read < ssize_t(sampleSize)) {//read error
	    mBuffer->release();
	    mBuffer = NULL;
	    return ERROR_IO;
	}

	mBuffer->set_range(0,bufOff+num_bytes_read);
    bufOff += num_bytes_read;

	mCurrentSampleIndex += num_bytes_read/sampleSize;
    }
#ifdef NOT_USE_PCMOMX
    int32_t bitsPerSample = 0;
    if (mFormat->findInt32(kKeyBitWidth, &bitsPerSample) && bitsPerSample == 8) {
	// Convert 8-bit unsigned samples to 16-bit signed.

	MediaBuffer *tmp;
	CHECK_EQ(mGroup->acquire_buffer(&tmp), (status_t)OK);

	// The new mBuffer holds the sample number of samples, but each
	// one is 2 bytes wide.
	tmp->set_range(0, 2 * bufOff);

	int16_t *dst = (int16_t *)tmp->data();
	const int8_t *src = (const int8_t *)mBuffer->data();
	ssize_t numBytes = bufOff;

	int32_t isSign = 0;
	int16_t delta = 128;
	if (mFormat->findInt32(kKeyNumericalType, &isSign) && isSign == 1) { 
	    delta = 0;
	}  
	while (numBytes-- > 0) {
	    *dst++ = ((int16_t)(*src) - delta) * 256;
	    ++src;
	}

	mBuffer->release();
	mBuffer = tmp;
    } else if (bitsPerSample == 24) {
	// Convert 24-bit signed samples to 16-bit signed.

	const uint8_t *src =
	    (const uint8_t *)mBuffer->data() + mBuffer->range_offset();
	int16_t *dst = (int16_t *)src;

	size_t numSamples = mBuffer->range_length() / 3;
	for (size_t i = 0; i < numSamples; ++i) {
	    int32_t x = (int32_t)(src[0] | src[1] << 8 | src[2] << 16);       //only support little endian of 24 bit
	    //int32_t x = (int32_t)(src[0] << 16 | src[1] << 8 | src[2]);
	    x = (x << 8) >> 8;  // sign extension

	    x = x >> 8;
	    *dst++ = (int16_t)x;
	    src += 3;
	}

	mBuffer->set_range(mBuffer->range_offset(), 2 * numSamples);
    } else if (bitsPerSample == 16) {
	int32_t isBigEndian = 0;
	if (mFormat->findInt32(kKeyEndian, &isBigEndian) && isBigEndian == 1) { 
	    // Convert 16-bit signed big-endian samples to 16-bit signed little-endian.

	    const uint8_t *src =
		(const uint8_t *)mBuffer->data() + mBuffer->range_offset();
	    int16_t *dst = (int16_t *)src;

	    size_t numSamples = mBuffer->range_length() / 2;
	    for (size_t i = 0; i < numSamples; ++i) {
		*dst++ = (int16_t)(src[0] << 8 | src[1]);
		src += 2;
	    }

	    mBuffer->set_range(mBuffer->range_offset(), 2 * numSamples);
	}
    }
#endif

    mBuffer->meta_data()->clear();
    mBuffer->meta_data()->setInt64(
	    kKeyTime, ((int64_t)bufCts * 1000000) / mTimescale);

    *out = mBuffer;
    mBuffer = NULL;

    return OK;
}
#endif
#endif
status_t MPEG4Source::read(
        MediaBuffer **out, const ReadOptions *options) {
    Mutex::Autolock autoLock(mLock);

    CHECK(mStarted);
#ifdef MTK_PLAYREADY_SUPPORT
#if defined(TRUSTONIC_TEE_SUPPORT) && defined(MTK_SEC_VIDEO_PATH_SUPPORT) 
    if (mCodecSpecificDataIndex < mCodecSpecificData.size()) {
        ALOGI("set codec info");
	const CodecSpecificData *specific =
	    mCodecSpecificData[mCodecSpecificDataIndex];

	mGroup->acquire_buffer(&mBuffer);

	CHECK(mBuffer->range_length() >= specific->mSize);

	if (!mWantsNALFragments) {          // add nal prefix
	    uint8_t *data = (uint8_t *) (mBuffer->data());
	    memcpy((uint8_t *)data, "\x00\x00\x00\x01", 4);
	    memcpy(data+4, specific->mData, specific->mSize);
	    mBuffer->set_range(0, specific->mSize+4);
	} else {
	    memcpy(mBuffer->data(), specific->mData, specific->mSize);
	    mBuffer->set_range(0, specific->mSize);
	}


	const sp<MetaData> bufmeta = mBuffer->meta_data();
	bufmeta->clear();
	bufmeta->setInt64(kKeyTime, 0);
	{
            size_t ivSize = GET_IV_BUFFER_LEN(1);
	    uint8_t *iv = new uint8_t[ivSize];
	    if (iv == NULL) {
                 ALOGE("new iv fail");
	    }
            ALOGI("ivSize:%d", ivSize);
	    DxMultiSampleHeader *ivSet = (DxMultiSampleHeader*)(iv);
	    ivSet->dwOutBufferOffset = 0;
	    memset(&(ivSet->qwInitializationVector), 0, 8);       
	    ivSet->dwMediaOffset = 0;
	    ivSet->dwSubSamplesNum = 1;

	    DxSubSample *sampleAdd  = GET_SAMPLE_ARR_PTR(iv);
	    sampleAdd->dwClearDataSize = specific->mSize;
	    if (!mWantsNALFragments) {          // add nal prefix
		sampleAdd->dwClearDataSize += 4;
	    }
	    sampleAdd->dwEncryptedDataSize = 0;

	    bufmeta->setData(kKeyCryptoIV, 0, iv, ivSize);
            ALOGI("set key IV");
	    delete [] iv;
	}

	*out = mBuffer;
	mBuffer = NULL;
        mCodecSpecificDataIndex++;
	return OK;
    }
#endif
#endif
    if (mFirstMoofOffset > 0) {
        return fragmentedRead(out, options);
    }

#ifndef ANDROID_DEFAULT_MODE
    if (out != NULL)
#endif

    *out = NULL;

    int64_t targetSampleTimeUs = -1;
#ifndef ANDROID_DEFAULT_CODE//added by hai.li to support track time offset
	int64_t startTimeOffsetUs = ((int64_t)mSampleTable->getStartTimeOffset())*1000000/mTimescale;
#endif	
    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
#ifndef ANDROID_DEFAULT_CODE
	bool isTryRead = false;
#endif
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
#ifndef ANDROID_DEFAULT_CODE//added by hai.li to support track time offset
//		ALOGE("SEEK TIME1=%lld", seekTimeUs);
		
		ALOGD("seekTimeUs=%lld, seekMode=%d", seekTimeUs, mode);

		if (ReadOptions::SEEK_TRY_READ == mode) {
			isTryRead = true;
			mode = ReadOptions::SEEK_CLOSEST_SYNC;
		}
		if (startTimeOffsetUs != 0)
		{
			if (seekTimeUs < startTimeOffsetUs)
			{
				seekTimeUs = 0;
			}
			else
			{
				seekTimeUs -= startTimeOffsetUs;
			}
					
		}
//		ALOGE("SEEK TIME2=%lld", seekTimeUs);
#endif
        uint32_t findFlags = 0;
        switch (mode) {
            case ReadOptions::SEEK_PREVIOUS_SYNC:
                findFlags = SampleTable::kFlagBefore;
                break;
            case ReadOptions::SEEK_NEXT_SYNC:
                findFlags = SampleTable::kFlagAfter;
                break;
            case ReadOptions::SEEK_CLOSEST_SYNC:
            case ReadOptions::SEEK_CLOSEST:
                findFlags = SampleTable::kFlagClosest;
                break;
            default:
                CHECK(!"Should not be here.");
                break;
        }

        uint32_t sampleIndex;
#ifdef ENABLE_PERF_JUMP_KEY_MECHANISM
		if (ReadOptions::SEEK_NEXT_SYNC == mode) {
			status_t err = mSampleTable->findSyncSampleNear(
					mCurrentSampleIndex, &sampleIndex, SampleTable::kFlagAfter);
			if (err != OK) {
				if (err == ERROR_OUT_OF_RANGE) {
					err = ERROR_END_OF_STREAM;
				}
				return err;
			}
			syncSampleIndex = sampleIndex;
			ALOGD("SEEK_JUMP_NEXT_KEY, mCurrentSampleIndex=%d, sampleIndex=%d", mCurrentSampleIndex, sampleIndex);
		}
		else {
#endif
#ifndef ANDROID_DEFAULT_CODE//hai.li for Issue: ALPS32414
        int64_t durationUs;
        if (mFormat->findInt64(kKeyDuration, &durationUs) && (seekTimeUs >= durationUs))
        {
            return ERROR_END_OF_STREAM;
        }
		status_t err = mSampleTable->findSampleAtTime(
				(seekTimeUs * mTimescale + 500000ll) / 1000000,
				&sampleIndex, findFlags);
#else
        status_t err = mSampleTable->findSampleAtTime(
                seekTimeUs * mTimescale / 1000000,
                &sampleIndex, findFlags);
#endif
        if (mode == ReadOptions::SEEK_CLOSEST) {
            // We found the closest sample already, now we want the sync
            // sample preceding it (or the sample itself of course), even
            // if the subsequent sync sample is closer.
            findFlags = SampleTable::kFlagBefore;
        }

        uint32_t syncSampleIndex;
        if (err == OK) {
            err = mSampleTable->findSyncSampleNear(
                    sampleIndex, &syncSampleIndex, findFlags);
        }

        uint32_t sampleTime;
        if (err == OK) {
            err = mSampleTable->getMetaDataForSample(
                    sampleIndex, NULL, NULL, &sampleTime);
        }

        if (err != OK) {
            if (err == ERROR_OUT_OF_RANGE) {
                // An attempt to seek past the end of the stream would
                // normally cause this ERROR_OUT_OF_RANGE error. Propagating
                // this all the way to the MediaPlayer would cause abnormal
                // termination. Legacy behaviour appears to be to behave as if
                // we had seeked to the end of stream, ending normally.
                err = ERROR_END_OF_STREAM;
            }
            ALOGV("end of stream");
            return err;
        }
#ifdef ENABLE_PERF_JUMP_KEY_MECHANISM
		}
#endif

#ifndef ANDROID_DEFAULT_CODE//hai.li
#ifdef ENABLE_PERF_JUMP_KEY_MECHANISM
		if (mode == ReadOptions::SEEK_CLOSEST ||
			mode == ReadOptions::SEEK_JUMP_NEXT_KEY) 
#else
        if (mode == ReadOptions::SEEK_CLOSEST) 
#endif
		{
	        uint32_t sampleTime;
	        CHECK_EQ((status_t)OK, mSampleTable->getMetaDataForSample(
                    sampleIndex, NULL, NULL, &sampleTime));
            targetSampleTimeUs = (sampleTime * 1000000ll) / mTimescale + startTimeOffsetUs;
			ALOGE("targetSampleTimeUs=%lld", targetSampleTimeUs);
        }
#else
        if (mode == ReadOptions::SEEK_CLOSEST) {
            targetSampleTimeUs = (sampleTime * 1000000ll) / mTimescale;
        }
#endif

#if 0
        uint32_t syncSampleTime;
        CHECK_EQ(OK, mSampleTable->getMetaDataForSample(
                    syncSampleIndex, NULL, NULL, &syncSampleTime));

        ALOGI("seek to time %lld us => sample at time %lld us, "
             "sync sample at time %lld us",
             seekTimeUs,
             sampleTime * 1000000ll / mTimescale,
             syncSampleTime * 1000000ll / mTimescale);
#endif

        mCurrentSampleIndex = syncSampleIndex;
        if (mBuffer != NULL) {
            mBuffer->release();
            mBuffer = NULL;
        }

        // fall through
    }
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_RAW_SUPPORT
    const char *mime;
    CHECK(mFormat->findCString(kKeyMIMEType, &mime));
    if (!strncasecmp("audio/raw", mime, 9)) {
         return pcmread(out);
    }
#endif
#endif
    off64_t offset;
    size_t size;
    uint32_t cts;
    bool isSyncSample;
    bool newBuffer = false;
    if (mBuffer == NULL) {
        newBuffer = true;

#ifndef ANDROID_DEFAULT_CODE
	const char *mime;
	CHECK(mFormat->findCString(kKeyMIMEType, &mime));
	if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_MPEG, mime)) {
	    int32_t skipNum = mSampleTable->getSkipSample();
	    if (skipNum != -1 &&
		    mCurrentSampleIndex <= uint32_t(skipNum)) {
                ALOGI("skip num:%d", skipNum);
		mCurrentSampleIndex = skipNum + 1;
	    }
	}
#endif

        status_t err =
            mSampleTable->getMetaDataForSample(
                    mCurrentSampleIndex, &offset, &size, &cts, &isSyncSample);

        if (err != OK) {
#ifndef ANDROID_DEFAULT_CODE//added by hai.li for Issue:ALPS34394
			if (err == ERROR_OUT_OF_RANGE)
				err = ERROR_END_OF_STREAM;//Awesomeplayer only can handle this as eos
#endif
            return err;
        }
#ifndef ANDROID_DEFAULT_CODE
        int32_t max_size = 0;
	if (mFormat->findInt32(kKeyMaxInputSize, &max_size) && int32_t(size) > max_size) {
             ALOGE("Warning: size:%d > max_size:%d", int32_t(size), max_size);
             return ERROR_END_OF_STREAM;
	}
#endif

#ifndef ANDROID_DEFAULT_CODE
			if (isTryRead) {
				ALOGD("Try read");
				ssize_t result =
					mDataSource->readAt(offset, NULL, size);
				if ((size_t)result == size) {
					ALOGD("Try read return ok");
					return OK;
				} else {
					ALOGD("Try read fail!");
					return INFO_TRY_READ_FAIL;
				}
			}
#endif
        err = mGroup->acquire_buffer(&mBuffer);

        if (err != OK) {
            CHECK(mBuffer == NULL);
            return err;
        }
    }

    if (!mIsAVC || mWantsNALFragments) {
        if (newBuffer) {
#ifndef ANDROID_DEFAULT_CODE
	    const char *mime;
	    CHECK(mFormat->findCString(kKeyMIMEType, &mime));
	    if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_MPEG, mime)) {
		int32_t skipNum = mSampleTable->getSkipSample();
		int32_t skipOff = mSampleTable->getSkipOff();
		if (skipOff != 0) {
		    // if skip num, do skipoff when read the index frame
		    if (skipNum != -1) {
			if (mCurrentSampleIndex == uint32_t(skipNum+1)) {
			    ALOGI("skip off:%d", skipOff);
			    offset += skipOff;
			}
		    }
		    else if (mCurrentSampleIndex == 0) {   // when not skip num, do skipoff when 0 frame
			ALOGI("skip off:%d", skipOff);
			offset += skipOff;
		    }
		}
	    }
#endif
            ssize_t num_bytes_read =
                mDataSource->readAt(offset, (uint8_t *)mBuffer->data(), size);

            if (num_bytes_read < (ssize_t)size) {
                mBuffer->release();
                mBuffer = NULL;

                return ERROR_IO;
            }

            CHECK(mBuffer != NULL);
            mBuffer->set_range(0, size);
            mBuffer->meta_data()->clear();
#ifndef ANDROID_DEFAULT_CODE//modified by hai.li to support track time offset
            	mBuffer->meta_data()->setInt64(
                	   kKeyTime, ((int64_t)(cts+mSampleTable->getStartTimeOffset())* 1000000) / mTimescale);
#else
            mBuffer->meta_data()->setInt64(
                    kKeyTime, ((int64_t)cts * 1000000) / mTimescale);
#endif
            if (targetSampleTimeUs >= 0) {
                mBuffer->meta_data()->setInt64(
                        kKeyTargetTime, targetSampleTimeUs);
            }

            if (isSyncSample) {
                mBuffer->meta_data()->setInt32(kKeyIsSyncFrame, 1);
            }

            ++mCurrentSampleIndex;
        }

        if (!mIsAVC) {
            *out = mBuffer;
            mBuffer = NULL;

            return OK;
        }

        // Each NAL unit is split up into its constituent fragments and
        // each one of them returned in its own buffer.
#ifdef ANDROID_DEFAULT_CODE  //ALPS00238811
        CHECK(mBuffer->range_length() >= mNALLengthSize);
#endif
        const uint8_t *src =
            (const uint8_t *)mBuffer->data() + mBuffer->range_offset();

        size_t nal_size = parseNALSize(src);
#ifndef ANDROID_DEFAULT_CODE
		if ((mBuffer->range_length() < mNALLengthSize + nal_size) ||
			(mNALLengthSize + nal_size < mNALLengthSize)) {//When uint type nal_size is very large, e.g. 0xffff or 0xffffffff, the summary is small. In this case, there are some problems in later flow.
#else
        if (mBuffer->range_length() < mNALLengthSize + nal_size) {
            ALOGE("incomplete NAL unit.");
#endif

#ifndef ANDROID_DEFAULT_CODE
			ALOGW("incomplete NAL unit.mBuffer->range_length()=%d, mNALLengthSize=%d, nal_size=0x%8.8x", mBuffer->range_length(), mNALLengthSize, nal_size);
			if (mBuffer->range_length() < mNALLengthSize) {
				*out = mBuffer;
				mBuffer = NULL;
				
				return OK;
			}
			else {
				mBuffer->set_range(mBuffer->range_offset() + mNALLengthSize, mBuffer->range_length() - mNALLengthSize);
				*out = mBuffer;
				mBuffer = NULL;
				return OK;
			}
#else
            mBuffer->release();
            mBuffer = NULL;

            return ERROR_MALFORMED;
#endif
        }

        MediaBuffer *clone = mBuffer->clone();
        CHECK(clone != NULL);
        clone->set_range(mBuffer->range_offset() + mNALLengthSize, nal_size);

        CHECK(mBuffer != NULL);
        mBuffer->set_range(
                mBuffer->range_offset() + mNALLengthSize + nal_size,
                mBuffer->range_length() - mNALLengthSize - nal_size);
#ifdef ANDROID_DEFAULT_CODE  //ALPS00238811
        if (mBuffer->range_length() == 0) {
#else
		if ((mBuffer->range_length() < mNALLengthSize) || (0 == nal_size)) {
#endif
            mBuffer->release();
            mBuffer = NULL;
        }

#ifndef ANDROID_DEFAULT_CODE
	//handle too much zero data 
	if (clone->range_length()==0) {
	    if (mZeroBufStart < 0) {
		mZeroBufStart = systemTime()/1000;
	    }
	    else {
		int64_t zeroBufDuration = systemTime()/1000 - mZeroBufStart;
		if (zeroBufDuration > kZeroBufTimeOutUs) {
		    ALOGD("SeekTimeOut ZeroBuf Line:%d,start time=%lld, duration=%lld", __LINE__, mZeroBufStart, zeroBufDuration);
		    mZeroBufStart = -1;
		    clone->release();
		    clone = NULL;
		    return UNKNOWN_ERROR;
		}
	    }
	}
	else if (mZeroBufStart>0 && clone->range_length()!=0) {
	    mZeroBufStart = -1;
	}
#endif
        *out = clone;

        return OK;
    } else {
        // Whole NAL units are returned but each fragment is prefixed by
        // the start code (0x00 00 00 01).
        ssize_t num_bytes_read = 0;
        int32_t drm = 0;
        bool usesDRM = (mFormat->findInt32(kKeyIsDRM, &drm) && drm != 0);
        if (usesDRM) {
            num_bytes_read =
                mDataSource->readAt(offset, (uint8_t*)mBuffer->data(), size);
        } else {
            num_bytes_read = mDataSource->readAt(offset, mSrcBuffer, size);
        }

        if (num_bytes_read < (ssize_t)size) {
            mBuffer->release();
            mBuffer = NULL;

            return ERROR_IO;
        }

        if (usesDRM) {
            CHECK(mBuffer != NULL);
            mBuffer->set_range(0, size);

        } else {
            uint8_t *dstData = (uint8_t *)mBuffer->data();
            size_t srcOffset = 0;
            size_t dstOffset = 0;

            while (srcOffset < size) {
                bool isMalFormed = (srcOffset + mNALLengthSize > size);
                size_t nalLength = 0;
                if (!isMalFormed) {
                    nalLength = parseNALSize(&mSrcBuffer[srcOffset]);
                    srcOffset += mNALLengthSize;
                    isMalFormed = srcOffset + nalLength > size;
                }

                if (isMalFormed) {
                    ALOGE("Video is malformed");
                    mBuffer->release();
                    mBuffer = NULL;
                    return ERROR_MALFORMED;
                }

                if (nalLength == 0) {
                    continue;
                }

                CHECK(dstOffset + 4 <= mBuffer->size());

                dstData[dstOffset++] = 0;
                dstData[dstOffset++] = 0;
                dstData[dstOffset++] = 0;
                dstData[dstOffset++] = 1;
                memcpy(&dstData[dstOffset], &mSrcBuffer[srcOffset], nalLength);
                srcOffset += nalLength;
                dstOffset += nalLength;
            }
            CHECK_EQ(srcOffset, size);
            CHECK(mBuffer != NULL);
            mBuffer->set_range(0, dstOffset);
        }

        mBuffer->meta_data()->clear();
#ifndef ANDROID_DEFAULT_CODE//modified by hai.li to support track time offset
        	mBuffer->meta_data()->setInt64(
            	    kKeyTime, ((int64_t)(cts+mSampleTable->getStartTimeOffset()) * 1000000) / mTimescale);
#else
        mBuffer->meta_data()->setInt64(
                kKeyTime, ((int64_t)cts * 1000000) / mTimescale);
#endif
        if (targetSampleTimeUs >= 0) {
            mBuffer->meta_data()->setInt64(
                    kKeyTargetTime, targetSampleTimeUs);
        }

        if (isSyncSample) {
            mBuffer->meta_data()->setInt32(kKeyIsSyncFrame, 1);
        }

        ++mCurrentSampleIndex;

        *out = mBuffer;
        mBuffer = NULL;

        return OK;
    }
}

#ifndef ANDROID_DEFAULT_CODE
bool MPEG4Source::setDataSource(const sp<DataSource> &dataSource)
{
    Mutex::Autolock autoLock(mLock);
    mDataSource = dataSource;
    return true;
}
#endif

status_t MPEG4Source::fragmentedRead(
        MediaBuffer **out, const ReadOptions *options) {

    ALOGV("MPEG4Source::fragmentedRead");

    CHECK(mStarted);

    *out = NULL;

    int64_t targetSampleTimeUs = -1;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {

        int numSidxEntries = mSegments.size();
        if (numSidxEntries != 0) {
            int64_t totalTime = 0;
            off64_t totalOffset = mFirstMoofOffset;
            for (int i = 0; i < numSidxEntries; i++) {
                const SidxEntry *se = &mSegments[i];
                if (totalTime + se->mDurationUs > seekTimeUs) {
                    // The requested time is somewhere in this segment
                    if ((mode == ReadOptions::SEEK_NEXT_SYNC) ||
                        (mode == ReadOptions::SEEK_CLOSEST_SYNC &&
                        (seekTimeUs - totalTime) > (totalTime + se->mDurationUs - seekTimeUs))) {
                        // requested next sync, or closest sync and it was closer to the end of
                        // this segment
                        totalTime += se->mDurationUs;
                        totalOffset += se->mSize;
                    }
                    break;
                }
                totalTime += se->mDurationUs;
                totalOffset += se->mSize;
            }
        mCurrentMoofOffset = totalOffset;
        mCurrentSamples.clear();
        mCurrentSampleIndex = 0;
        parseChunk(&totalOffset);
        mCurrentTime = totalTime * mTimescale / 1000000ll;
        }
#ifdef MTK_PLAYREADY_SUPPORT
	int numMfraEntries = mMovieFragments.size();
	if (numMfraEntries != 0) {
	    // seek the numMfraEntries to get entry
            int32_t seekIndex = -1;
	    for (int i = 0; i < numMfraEntries; i++) {
		if (mTrackId == mMovieFragments[i].trackid) {
		    const MfraEntry *se = &mMovieFragments[i];
		    if ((se->mTime * 1000000 / mTimescale) >= seekTimeUs) {
			seekIndex = (i > 0) ? i-1 : i;        // get the time before seekTimeUs
			break;
		    }
		}
	    }
	    if (seekIndex != -1) {
		mCurrentMoofOffset = mMovieFragments[seekIndex].mMoofOffset;
		mCurrentSamples.clear();
		mCurrentSampleIndex = 0;
		mCurrentTime = (uint32_t)(mMovieFragments[seekIndex].mTime);
                ALOGI("mCurrentTime:%d, seekTimeUs:%lld, mTimescale:%d", mCurrentTime, seekTimeUs, mTimescale);

	        off64_t moofOff = mCurrentMoofOffset;
                const MfraEntry *pMfraEntry = &mMovieFragments[seekIndex];
                MfraEntry tmpMfraEntry;
                memcpy(&tmpMfraEntry, pMfraEntry, sizeof(MfraEntry)); 
		parseChunk(&moofOff, &tmpMfraEntry);
	    }
	    else {
		ALOGI("seek eos");
		return ERROR_END_OF_STREAM;
	    }
	}
#endif
        if (mBuffer != NULL) {
            mBuffer->release();
            mBuffer = NULL;
        }

        // fall through
    }

    off64_t offset = 0;
    size_t size;
    uint32_t cts = 0;
    bool isSyncSample = false;
    bool newBuffer = false;
    if (mBuffer == NULL) {
        newBuffer = true;

        if (mCurrentSampleIndex >= mCurrentSamples.size()) {
            // move to next fragment
#ifdef MTK_PLAYREADY_SUPPORT
	    //Sample lastSample = mCurrentSamples[mCurrentSamples.size() - 1];
	    mCurrentSamples.clear();
	    mCurrentSampleIndex = 0;
	    off64_t nextMoof = mNextMoofOffset; // lastSample.offset + lastSample.size;
	    while (mCurrentSampleIndex >= mCurrentSamples.size()) {
		mCurrentMoofOffset = mNextMoofOffset;
		status_t err = parseChunk(&nextMoof);
		if (err != OK) {
		    if (err == ERROR_IO) {
			err = ERROR_END_OF_STREAM;
		    }
		    ALOGI("err:%d", err);
		    return err;
		}
	    }
#else
	Sample lastSample = mCurrentSamples[mCurrentSamples.size() - 1];
	off64_t nextMoof = mNextMoofOffset; // lastSample.offset + lastSample.size;
	mCurrentMoofOffset = nextMoof;
	mCurrentSamples.clear();
	mCurrentSampleIndex = 0;
	parseChunk(&nextMoof);
	if (mCurrentSampleIndex >= mCurrentSamples.size()) {
	    return ERROR_END_OF_STREAM;
	}
#endif
        }

        const Sample *smpl = &mCurrentSamples[mCurrentSampleIndex];
        offset = smpl->offset;
        size = smpl->size;
        cts = mCurrentTime;
        mCurrentTime += smpl->duration;
        isSyncSample = (mCurrentSampleIndex == 0); // XXX

        status_t err = mGroup->acquire_buffer(&mBuffer);

        if (err != OK) {
            CHECK(mBuffer == NULL);
            ALOGV("acquire_buffer returned %d", err);
            return err;
        }
    }

    const Sample *smpl = &mCurrentSamples[mCurrentSampleIndex];
    const sp<MetaData> bufmeta = mBuffer->meta_data();
    bufmeta->clear();
    if (smpl->encryptedsizes.size()) {
        // store clear/encrypted lengths in metadata
        bufmeta->setData(kKeyPlainSizes, 0,
                smpl->clearsizes.array(), smpl->clearsizes.size() * 4);
        bufmeta->setData(kKeyEncryptedSizes, 0,
                smpl->encryptedsizes.array(), smpl->encryptedsizes.size() * 4);
#ifdef MTK_PLAYREADY_SUPPORT
	int32_t IsPlayReady = 0;
	if (mFormat->findInt32(kKeyIsPlayReady, &IsPlayReady) && IsPlayReady) {
	    bufmeta->setData(kKeyCryptoIV, 0, smpl->playReadyIV, smpl->IVSize);
	    delete [] smpl->playReadyIV;
	} else {
	    bufmeta->setData(kKeyCryptoIV, 0, smpl->iv, 16); // use 16 or the actual size?
	}
#else
        bufmeta->setData(kKeyCryptoIV, 0, smpl->iv, 16); // use 16 or the actual size?
#endif
        bufmeta->setInt32(kKeyCryptoDefaultIVSize, mDefaultIVSize);
        bufmeta->setInt32(kKeyCryptoMode, mCryptoMode);
        bufmeta->setData(kKeyCryptoKey, 0, mCryptoKey, 16);
    }

    if (!mIsAVC || mWantsNALFragments) {
        if (newBuffer) {
            ssize_t num_bytes_read =
                mDataSource->readAt(offset, (uint8_t *)mBuffer->data(), size);

            if (num_bytes_read < (ssize_t)size) {
                mBuffer->release();
                mBuffer = NULL;

                ALOGV("i/o error");
                return ERROR_IO;
            }

            CHECK(mBuffer != NULL);
            mBuffer->set_range(0, size);
            mBuffer->meta_data()->setInt64(
                    kKeyTime, ((int64_t)cts * 1000000) / mTimescale);

            if (targetSampleTimeUs >= 0) {
                mBuffer->meta_data()->setInt64(
                        kKeyTargetTime, targetSampleTimeUs);
            }

            if (isSyncSample) {
                mBuffer->meta_data()->setInt32(kKeyIsSyncFrame, 1);
            }

            ++mCurrentSampleIndex;
        }

        if (!mIsAVC) {
            *out = mBuffer;
            mBuffer = NULL;

            return OK;
        }

        // Each NAL unit is split up into its constituent fragments and
        // each one of them returned in its own buffer.

        CHECK(mBuffer->range_length() >= mNALLengthSize);

        const uint8_t *src =
            (const uint8_t *)mBuffer->data() + mBuffer->range_offset();

        size_t nal_size = parseNALSize(src);
        if (mBuffer->range_length() < mNALLengthSize + nal_size) {
            ALOGE("incomplete NAL unit.");

            mBuffer->release();
            mBuffer = NULL;

            return ERROR_MALFORMED;
        }

        MediaBuffer *clone = mBuffer->clone();
        CHECK(clone != NULL);
        clone->set_range(mBuffer->range_offset() + mNALLengthSize, nal_size);

        CHECK(mBuffer != NULL);
        mBuffer->set_range(
                mBuffer->range_offset() + mNALLengthSize + nal_size,
                mBuffer->range_length() - mNALLengthSize - nal_size);

        if (mBuffer->range_length() == 0) {
            mBuffer->release();
            mBuffer = NULL;
        }

        *out = clone;

        return OK;
    } else {
        ALOGV("whole NAL");
        // Whole NAL units are returned but each fragment is prefixed by
        // the start code (0x00 00 00 01).
        ssize_t num_bytes_read = 0;
        int32_t drm = 0;
        bool usesDRM = (mFormat->findInt32(kKeyIsDRM, &drm) && drm != 0);
#ifdef MTK_PLAYREADY_SUPPORT
	int32_t IsPlayReady = 0;
	if (mFormat->findInt32(kKeyIsPlayReady, &IsPlayReady) && IsPlayReady) {
	    ALOGI("PlayReady do not set usesDRM, add nal prefix");
	    usesDRM = 0;
	}
#endif
        if (usesDRM) {
            num_bytes_read =
                mDataSource->readAt(offset, (uint8_t*)mBuffer->data(), size);
        } else {
            num_bytes_read = mDataSource->readAt(offset, mSrcBuffer, size);
        }

        if (num_bytes_read < (ssize_t)size) {
            mBuffer->release();
            mBuffer = NULL;

            ALOGV("i/o error");
            return ERROR_IO;
        }

        if (usesDRM) {
            CHECK(mBuffer != NULL);
            mBuffer->set_range(0, size);

        } else {
            uint8_t *dstData = (uint8_t *)mBuffer->data();
            size_t srcOffset = 0;
            size_t dstOffset = 0;

            while (srcOffset < size) {
                bool isMalFormed = (srcOffset + mNALLengthSize > size);
                size_t nalLength = 0;
                if (!isMalFormed) {
                    nalLength = parseNALSize(&mSrcBuffer[srcOffset]);
                    srcOffset += mNALLengthSize;
                    isMalFormed = srcOffset + nalLength > size;
                }

                if (isMalFormed) {
                    ALOGE("Video is malformed");
                    mBuffer->release();
                    mBuffer = NULL;
                    return ERROR_MALFORMED;
                }

                if (nalLength == 0) {
                    continue;
                }

                CHECK(dstOffset + 4 <= mBuffer->size());

                dstData[dstOffset++] = 0;
                dstData[dstOffset++] = 0;
                dstData[dstOffset++] = 0;
                dstData[dstOffset++] = 1;
                memcpy(&dstData[dstOffset], &mSrcBuffer[srcOffset], nalLength);
                srcOffset += nalLength;
                dstOffset += nalLength;
            }
            CHECK_EQ(srcOffset, size);
            CHECK(mBuffer != NULL);
            mBuffer->set_range(0, dstOffset);
        }

        mBuffer->meta_data()->setInt64(
                kKeyTime, ((int64_t)cts * 1000000) / mTimescale);

        if (targetSampleTimeUs >= 0) {
            mBuffer->meta_data()->setInt64(
                    kKeyTargetTime, targetSampleTimeUs);
        }

        if (isSyncSample) {
            mBuffer->meta_data()->setInt32(kKeyIsSyncFrame, 1);
        }

        ++mCurrentSampleIndex;

        *out = mBuffer;
        mBuffer = NULL;

        return OK;
    }
}

MPEG4Extractor::Track *MPEG4Extractor::findTrackByMimePrefix(
        const char *mimePrefix) {
    for (Track *track = mFirstTrack; track != NULL; track = track->next) {
        const char *mime;
        if (track->meta != NULL
                && track->meta->findCString(kKeyMIMEType, &mime)
                && !strncasecmp(mime, mimePrefix, strlen(mimePrefix))) {
            return track;
        }
    }

    return NULL;
}

static bool LegacySniffMPEG4(
        const sp<DataSource> &source, String8 *mimeType, float *confidence) {
    uint8_t header[8];

    ssize_t n = source->readAt(4, header, sizeof(header));
    if (n < (ssize_t)sizeof(header)) {
        return false;
    }

    if (!memcmp(header, "ftyp3gp", 7) || !memcmp(header, "ftypmp42", 8)
        || !memcmp(header, "ftyp3gr6", 8) || !memcmp(header, "ftyp3gs6", 8)
        || !memcmp(header, "ftyp3ge6", 8) || !memcmp(header, "ftyp3gg6", 8)
        || !memcmp(header, "ftypisom", 8) || !memcmp(header, "ftypM4V ", 8)
        || !memcmp(header, "ftypM4A ", 8) || !memcmp(header, "ftypf4v ", 8)
        || !memcmp(header, "ftypkddi", 8) || !memcmp(header, "ftypM4VP", 8)) {
        *mimeType = MEDIA_MIMETYPE_CONTAINER_MPEG4;
        *confidence = 0.4;

        return true;
    }

    return false;
}

static bool isCompatibleBrand(uint32_t fourcc) {
    static const uint32_t kCompatibleBrands[] = {
        FOURCC('i', 's', 'o', 'm'),
        FOURCC('i', 's', 'o', '2'),
        FOURCC('a', 'v', 'c', '1'),
        FOURCC('3', 'g', 'p', '4'),
        FOURCC('m', 'p', '4', '1'),
        FOURCC('m', 'p', '4', '2'),

        // Won't promise that the following file types can be played.
        // Just give these file types a chance.
        FOURCC('q', 't', ' ', ' '),  // Apple's QuickTime
        FOURCC('M', 'S', 'N', 'V'),  // Sony's PSP

        FOURCC('3', 'g', '2', 'a'),  // 3GPP2
        FOURCC('3', 'g', '2', 'b'),
    };

    for (size_t i = 0;
         i < sizeof(kCompatibleBrands) / sizeof(kCompatibleBrands[0]);
         ++i) {
        if (kCompatibleBrands[i] == fourcc) {
            return true;
        }
    }

    return false;
}

// Attempt to actually parse the 'ftyp' atom and determine if a suitable
// compatible brand is present.
// Also try to identify where this file's metadata ends
// (end of the 'moov' atom) and report it to the caller as part of
// the metadata.
static bool BetterSniffMPEG4(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *meta) {
    // We scan up to 128 bytes to identify this file as an MP4.
#ifndef ANDROID_DEFAULT_CODE     // ALPS00433708
    off64_t kMaxScanOffset = 128ll;
#else
    static const off64_t kMaxScanOffset = 128ll;
#endif

    off64_t offset = 0ll;
    bool foundGoodFileType = false;
    off64_t moovAtomEndOffset = -1ll;
    bool done = false;

#ifndef ANDROID_DEFAULT_CODE//hai.li: some files have no 'ftyp' atom, but they can be played in 2.2 version
    uint8_t header[12];
    // If type is not ftyp,mdata,moov or free, return false directly. Or else, it may be mpeg4 file.
    if (source->readAt(0, header, 12) != 12
		    || (memcmp("ftyp", &header[4], 4) && memcmp("mdat", &header[4], 4) 
			    && memcmp("moov", &header[4], 4) && memcmp("free", &header[4], 4)
                            && memcmp("wide", &header[4], 4))) {
	    //ALOGE("return false, type=0x%8.8x", *((uint32_t *)&header[4]));
	    return false;
    }
    *mimeType = MEDIA_MIMETYPE_CONTAINER_MPEG4;
    *confidence = 0.05f;
#endif  //ANDROID_DEFAULT_CODE
    while (!done && offset < kMaxScanOffset) {
        uint32_t hdr[2];
        if (source->readAt(offset, hdr, 8) < 8) {
            return false;
        }

        uint64_t chunkSize = ntohl(hdr[0]);
        uint32_t chunkType = ntohl(hdr[1]);
        off64_t chunkDataOffset = offset + 8;

        if (chunkSize == 1) {
            if (source->readAt(offset + 8, &chunkSize, 8) < 8) {
                return false;
            }

            chunkSize = ntoh64(chunkSize);
            chunkDataOffset += 8;

            if (chunkSize < 16) {
                // The smallest valid chunk is 16 bytes long in this case.
                return false;
            }
        } else if (chunkSize < 8) {
            // The smallest valid chunk is 8 bytes long.
            return false;
        }

        off64_t chunkDataSize = offset + chunkSize - chunkDataOffset;

        char chunkstring[5];
        MakeFourCCString(chunkType, chunkstring);
        ALOGV("saw chunk type %s, size %lld @ %lld", chunkstring, chunkSize, offset);
        switch (chunkType) {
            case FOURCC('f', 't', 'y', 'p'):
            {
#ifndef ANDROID_DEFAULT_CODE 
                *confidence = (*confidence<0.2) ? *confidence+0.05f : *confidence;
#endif
                if (chunkDataSize < 8) {
                    return false;
                }

                uint32_t numCompatibleBrands = (chunkDataSize - 8) / 4;
                for (size_t i = 0; i < numCompatibleBrands + 2; ++i) {
                    if (i == 1) {
                        // Skip this index, it refers to the minorVersion,
                        // not a brand.
                        continue;
                    }

                    uint32_t brand;
                    if (source->readAt(
                                chunkDataOffset + 4 * i, &brand, 4) < 4) {
                        return false;
                    }

                    brand = ntohl(brand);

                    if (isCompatibleBrand(brand)) {
                        foundGoodFileType = true;
                        break;
                    }
                }

                if (!foundGoodFileType) {
#ifndef ANDROID_DEFAULT_CODE  //ALPS00112506 Don't use isCompatibleBrand to judge whether play or not
	            ALOGW("Warning:ftyp brands is not isCompatibleBrand 1");
#else //ANDROID_DEFAULT_CODE
                    return false;
#endif
                }

                break;
            }

            case FOURCC('m', 'o', 'o', 'v'):
            {
                moovAtomEndOffset = offset + chunkSize;
#ifndef ANDROID_DEFAULT_CODE 
                *confidence = (*confidence<0.2) ? *confidence+0.05f : *confidence;
#endif

                done = true;
                break;
            }

#ifndef ANDROID_DEFAULT_CODE
            case FOURCC('f', 'r', 'e', 'e'):
            case FOURCC('m', 'd', 'a', 't'):
            case FOURCC('w', 'i', 'd', 'e'):
	    {
		char chunk[5];
		MakeFourCCString(chunkType, chunk);
		ALOGI("chunk: %s @ %lld, chunkSize:%lld", chunk, offset, chunkSize);
		kMaxScanOffset += chunkSize;
                *confidence = (*confidence<0.2) ? *confidence+0.05f : *confidence;
		break;
            }
#endif
            default:
                break;
        }

        offset += chunkSize;
    }

#ifndef ANDROID_DEFAULT_CODE  //ALPS00112506 Don't use isCompatibleBrand to judge whether play or not
    //If foundGoodFileType, set confidence from 0.1f to 0.4f. Or else confidence is 0.1f
    if (foundGoodFileType) {
	    *mimeType = MEDIA_MIMETYPE_CONTAINER_MPEG4;
	    *confidence = 0.4f;
    }
#else  // ANDROID_DEFAULT_CODE 
    if (!foundGoodFileType) {
        return false;
    }

    *mimeType = MEDIA_MIMETYPE_CONTAINER_MPEG4;
    *confidence = 0.4f;
#endif

    if (moovAtomEndOffset >= 0) {
        *meta = new AMessage;
        (*meta)->setInt64("meta-data-size", moovAtomEndOffset);

        ALOGV("found metadata size: %lld", moovAtomEndOffset);
    }

    return true;
}

bool SniffMPEG4(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *meta) {
    if (BetterSniffMPEG4(source, mimeType, confidence, meta)) {
        return true;
    }

    if (LegacySniffMPEG4(source, mimeType, confidence)) {
        ALOGW("Identified supported mpeg4 through LegacySniffMPEG4.");
        return true;
    }

    return false;
}

}  // namespace android
