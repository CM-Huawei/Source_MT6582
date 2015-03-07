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

#ifndef MPEG4_EXTRACTOR_H_

#define MPEG4_EXTRACTOR_H_

#include <arpa/inet.h>

#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/Utils.h>
#include <utils/List.h>
#include <utils/Vector.h>
#include <utils/String8.h>
#ifndef ANDROID_DEFAULT_CODE
#include <include/SampleTable.h>
#include <media/stagefright/MetaData.h>
#endif

namespace android {

struct AMessage;
class DataSource;
class SampleTable;
class String8;

struct SidxEntry {
    size_t mSize;
    uint32_t mDurationUs;
};
#ifdef MTK_PLAYREADY_SUPPORT
struct MfraEntry{
    uint32_t trackid;
    uint64_t mTime;
    uint64_t mMoofOffset;
    uint32_t mTrafNumber;
    uint32_t mTrunNumber;
    uint32_t mSampleNumber;
};
#endif

class MPEG4Extractor : public MediaExtractor {
public:
    // Extractor assumes ownership of "source".
    MPEG4Extractor(const sp<DataSource> &source);

    virtual size_t countTracks();
    virtual sp<MediaSource> getTrack(size_t index);
    virtual sp<MetaData> getTrackMetaData(size_t index, uint32_t flags);

    virtual sp<MetaData> getMetaData();
    virtual uint32_t flags() const;

    // for DRM
    virtual char* getDrmTrackInfo(size_t trackID, int *len);
#ifndef ANDROID_DEFAULT_CODE
		// set new dataSource for mpeg4source, used by http streaming
		void changeDataSource(const sp<MediaSource> &mp4Source, const sp<DataSource> &dataSource);
#endif

protected:
    virtual ~MPEG4Extractor();

private:
	    struct PsshInfo {
        uint8_t uuid[16];
        uint32_t datalen;
        uint8_t *data;
    };
    struct Track {
        Track *next;
        sp<MetaData> meta;
        uint32_t timescale;
        sp<SampleTable> sampleTable;
        bool includes_expensive_metadata;
        bool skipTrack;
#ifndef ANDROID_DEFAULT_CODE

                uint32_t timescaleFactor; // check timescale too large
		uint32_t sampleCount;//added by hai.li to check unsupport video
		int64_t durationUs;
		bool mIsVideo;
		bool mIsAudio;
		bool mIsH263;
                size_t mMaxSize;
		
		struct ElstEntry {//added by hai.li to support time offset
			uint64_t SegDuration;
			int64_t MediaTime;
			int16_t MediaRateInt;
			int16_t MediaRateFrac;
		};
		ElstEntry *mElstEntries;
		uint32_t mElstEntryCount;
		uint32_t mStartTimeOffset;//in movie time scale

		Track()
		{
			mElstEntries = NULL; 
			mElstEntryCount = 0;
			sampleCount = 0;
			durationUs = 0;
			mStartTimeOffset = 0;
			mIsVideo = false;
			mIsAudio = false;
			mIsH263 = false;
                        mMaxSize = 0;
                        timescaleFactor = 0; 
		}
		
	//protected:
		virtual ~Track()
		{
			if (mElstEntries && mElstEntryCount)
				delete mElstEntries;
		}
	
#endif
    };

    Vector<SidxEntry> mSidxEntries;
    uint64_t mSidxDuration;
#ifdef MTK_PLAYREADY_SUPPORT
    Vector<MfraEntry> mMfraEntries;
    bool mIsPlayReady;	
#endif
    off64_t mMoofOffset;

    Vector<PsshInfo> mPssh;

    sp<DataSource> mDataSource;
    status_t mInitCheck;
    bool mHasVideo;
	uint32_t mHeaderTimescale;//track time offset
#ifndef ANDROID_DEFAULT_CODE
	bool mHasAudio;
#endif
    Track *mFirstTrack, *mLastTrack;

    sp<MetaData> mFileMetaData;

    Vector<uint32_t> mPath;
    String8 mLastCommentMean;
    String8 mLastCommentName;
    String8 mLastCommentData;

    status_t readMetaData();
    status_t parseChunk(off64_t *offset, int depth);
    status_t parseMetaData(off64_t offset, size_t size);

    status_t updateAudioTrackInfoFromESDS_MPEG4Audio(
            const void *esds_data, size_t esds_size);

    static status_t verifyTrack(Track *track);

    struct SINF {
        SINF *next;
        uint16_t trackID;
        uint8_t IPMPDescriptorID;
        ssize_t len;
        char *IPMPData;
    };

    SINF *mFirstSINF;

    bool mIsDrm;
    status_t parseDrmSINF(off64_t *offset, off64_t data_offset);

    status_t parseTrackHeader(off64_t data_offset, off64_t data_size);
    
    status_t parseSegmentIndex(off64_t data_offset, size_t data_size);
#ifdef MTK_PLAYREADY_SUPPORT
    status_t parseDrmSINFTrack(off64_t *offset, off64_t data_offset);
    status_t parseDrmSINFMOOV(off64_t *offset, off64_t data_offset);
    status_t parseMovieFragment(off64_t data_offset, size_t data_size);
#endif

    Track *findTrackByMimePrefix(const char *mimePrefix);

    MPEG4Extractor(const MPEG4Extractor &);
    MPEG4Extractor &operator=(const MPEG4Extractor &);
#ifndef ANDROID_DEFAULT_CODE
	status_t setCodecInfoFromFirstFrame(Track *track);
#ifdef MTK_S3D_SUPPORT
	status_t getFirstNal(Track *track, size_t *nal_offset, size_t *nal_size);
#endif
#endif
};

bool SniffMPEG4(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *);

}  // namespace android

#endif  // MPEG4_EXTRACTOR_H_
