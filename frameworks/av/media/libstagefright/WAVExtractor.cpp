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
#define LOG_TAG "WAVExtractor"
#include <utils/Log.h>
#include <cutils/xlog.h>


#include "include/WAVExtractor.h"

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <utils/String8.h>
#include <cutils/bitops.h>

#define CHANNEL_MASK_USE_CHANNEL_ORDER 0

namespace android {

enum {
    WAVE_FORMAT_PCM        = 0x0001,
    WAVE_FORMAT_ALAW       = 0x0006,
    WAVE_FORMAT_MULAW      = 0x0007,
    WAVE_FORMAT_MSGSM      = 0x0031,
    WAVE_FORMAT_EXTENSIBLE = 0xFFFE
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
	,
	WAVE_FORMAT_MSADPCM    = 0x0002,
	WAVE_FORMAT_DVI_IMAADCPM = 0x0011
#endif
#endif
};

static const char* WAVEEXT_SUBFORMAT = "\x00\x00\x00\x00\x10\x00\x80\x00\x00\xAA\x00\x38\x9B\x71";


static uint32_t U32_LE_AT(const uint8_t *ptr) {
    return ptr[3] << 24 | ptr[2] << 16 | ptr[1] << 8 | ptr[0];
}

static uint16_t U16_LE_AT(const uint8_t *ptr) {
    return ptr[1] << 8 | ptr[0];
}

struct WAVSource : public MediaSource {
    WAVSource(
            const sp<DataSource> &dataSource,
            const sp<MetaData> &meta,
            uint16_t waveFormat,
            int32_t bitsPerSample,
            off64_t offset, size_t size);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

protected:
    virtual ~WAVSource();

private:
    static const size_t kMaxFrameSize;

    sp<DataSource> mDataSource;
    sp<MetaData> mMeta;
    uint16_t mWaveFormat;
    int32_t mSampleRate;
    int32_t mNumChannels;
    int32_t mBitsPerSample;
    off64_t mOffset;
    size_t mSize;
    bool mStarted;
    MediaBufferGroup *mGroup;
    off64_t mCurrentPos;
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
	int64_t mBlockDurationUs;
	int32_t mBlockAlign;
#endif
#endif

    WAVSource(const WAVSource &);
    WAVSource &operator=(const WAVSource &);
};

WAVExtractor::WAVExtractor(const sp<DataSource> &source)
    : mDataSource(source),
      mValidFormat(false),
      mChannelMask(CHANNEL_MASK_USE_CHANNEL_ORDER)
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
	  ,
	  mAvgBytesPerSec(0),
	  mBlockAlign(0),
	  mExtraDataSize(0),
	  mpExtraData(NULL),
	  mSamplesPerBlock(0),
	  mSamplesNumberPerChannel(0),
	  mBlockDurationUs(0)
#endif
#endif
{
    mInitCheck = init();
}

WAVExtractor::~WAVExtractor() {
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
		if(NULL != mpExtraData)
		{
			free(mpExtraData);
			mpExtraData = NULL;
		}
#endif
#endif
}
sp<MetaData> WAVExtractor::getMetaData() {
    sp<MetaData> meta = new MetaData;

    if (mInitCheck != OK) {
        return meta;
    }

    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_CONTAINER_WAV);

    return meta;
}

size_t WAVExtractor::countTracks() {
    return mInitCheck == OK ? 1 : 0;
}

sp<MediaSource> WAVExtractor::getTrack(size_t index) {
    if (mInitCheck != OK || index > 0) {
        return NULL;
    }

    return new WAVSource(
            mDataSource, mTrackMeta,
            mWaveFormat, mBitsPerSample, mDataOffset, mDataSize);
}

sp<MetaData> WAVExtractor::getTrackMetaData(
        size_t index, uint32_t flags) {
    if (mInitCheck != OK || index > 0) {
        return NULL;
    }

    return mTrackMeta;
}

status_t WAVExtractor::init() {
    uint8_t header[12];
    if (mDataSource->readAt(
                0, header, sizeof(header)) < (ssize_t)sizeof(header)) {
        return NO_INIT;
    }

    if (memcmp(header, "RIFF", 4) || memcmp(&header[8], "WAVE", 4)) {
        return NO_INIT;
    }

    size_t totalSize = U32_LE_AT(&header[4]);

    off64_t offset = 12;
    size_t remainingSize = totalSize;
    while (remainingSize >= 8) {
        uint8_t chunkHeader[8];
        if (mDataSource->readAt(offset, chunkHeader, 8) < 8) {
            return NO_INIT;
        }

        remainingSize -= 8;
        offset += 8;

        uint32_t chunkSize = U32_LE_AT(&chunkHeader[4]);

        if (chunkSize > remainingSize) {
            return NO_INIT;
        }

        if (!memcmp(chunkHeader, "fmt ", 4)) {
            if (chunkSize < 16) {
                return NO_INIT;
            }

            uint8_t formatSpec[40];
            if (mDataSource->readAt(offset, formatSpec, 2) < 2) {
                return NO_INIT;
            }

            mWaveFormat = U16_LE_AT(formatSpec);
			if (mWaveFormat != WAVE_FORMAT_PCM
					&& mWaveFormat != WAVE_FORMAT_ALAW
					&& mWaveFormat != WAVE_FORMAT_MULAW
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
					&& mWaveFormat != WAVE_FORMAT_MSADPCM
					&& mWaveFormat != WAVE_FORMAT_DVI_IMAADCPM
#endif
#endif
					&& mWaveFormat != WAVE_FORMAT_MSGSM
					&& mWaveFormat != WAVE_FORMAT_EXTENSIBLE) {
				return ERROR_UNSUPPORTED;
			}


            uint8_t fmtSize = 16;
            if (mWaveFormat == WAVE_FORMAT_EXTENSIBLE) {
                fmtSize = 40;
            }
            if (mDataSource->readAt(offset, formatSpec, fmtSize) < fmtSize) {
                return NO_INIT;
            }

            mNumChannels = U16_LE_AT(&formatSpec[2]);
            if (mWaveFormat != WAVE_FORMAT_EXTENSIBLE) {
                if (mNumChannels != 1 && mNumChannels != 2) {
                    ALOGW("More than 2 channels (%d) in non-WAVE_EXT, unknown channel mask",
                            mNumChannels);
#ifndef ANDROID_DEFAULT_CODE
					if(mNumChannels == 0){
						return ERROR_UNSUPPORTED;
					}
#endif
                }
            } else {
                if (mNumChannels < 1 && mNumChannels > 8) {
                    return ERROR_UNSUPPORTED;
                }
#ifndef ANDROID_DEFAULT_CODE
				if (mNumChannels < 1 || mNumChannels > 8) {
						ALOGW("channel count is %d", mNumChannels); 				
						return ERROR_UNSUPPORTED;
				}
#endif
            }
			
            mSampleRate = U32_LE_AT(&formatSpec[4]);

            if (mSampleRate == 0) {
                return ERROR_MALFORMED;
            }
			SXLOGD("mNumChannels is %d, mSampleRate is %d", mNumChannels, mSampleRate);
#ifndef ANDROID_DEFAULT_CODE
			mAvgBytesPerSec = U32_LE_AT(&formatSpec[8]);
			if(mAvgBytesPerSec <= 0)
			{
				return ERROR_MALFORMED;
			}

			mBlockAlign = U16_LE_AT(&formatSpec[12]);
			if(mBlockAlign <= 0)
			{
				return ERROR_MALFORMED;
			}

			if(mWaveFormat == WAVE_FORMAT_MSADPCM || mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM || mWaveFormat == WAVE_FORMAT_MSGSM)
				SXLOGD("mBlockAlign is %d", mBlockAlign);
#endif

            mBitsPerSample = U16_LE_AT(&formatSpec[14]);

			if (mWaveFormat == WAVE_FORMAT_PCM
					|| mWaveFormat == WAVE_FORMAT_EXTENSIBLE) {
				if (mBitsPerSample != 8 && mBitsPerSample != 16
					&& mBitsPerSample != 24
#ifndef ANDROID_DEFAULT_CODE 
#ifdef MTK_AUDIO_RAW_SUPPORT
					&& mBitsPerSample != 32
#endif
#endif
				) {
					return ERROR_UNSUPPORTED;
				}
			} 
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
			else if(mWaveFormat == WAVE_FORMAT_MSADPCM || mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM)
			{
				if(mBitsPerSample != 4)
				{
					return ERROR_UNSUPPORTED;
				}
			}
#endif
#endif

			else if(mWaveFormat == WAVE_FORMAT_MSGSM)
			{
				if(mBitsPerSample != 0)
					return ERROR_UNSUPPORTED;
			}						
			else {
				CHECK(mWaveFormat == WAVE_FORMAT_MULAW
						|| mWaveFormat == WAVE_FORMAT_ALAW);
				if (mBitsPerSample != 8) {
					return ERROR_UNSUPPORTED;
				}
			}

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
			if(mWaveFormat == WAVE_FORMAT_MSADPCM || mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM)
			{
				uint8_t extraData[2];
				if (mDataSource->readAt(offset+16, extraData, 2) < 2) 
				{
               		return NO_INIT;
            	}

				mExtraDataSize = U16_LE_AT(extraData);
				if(mExtraDataSize < 2)
				{
					return ERROR_MALFORMED;

				}
				mpExtraData = (uint8_t*)malloc(mExtraDataSize);
				if(NULL == mpExtraData)
				{
					SXLOGE("ADPCM malloc extraDataSize failed !!!");
					return ERROR_IO;
				}
				else
				{
					SXLOGD("ADPCM mExtraDataSize is %d", mExtraDataSize);
					if(mDataSource->readAt(offset+18, mpExtraData, mExtraDataSize) < mExtraDataSize)
					{
						return ERROR_MALFORMED;
					}
				}	
				mSamplesPerBlock = U16_LE_AT(mpExtraData);
				if(mExtraDataSize < 0)
				{
					return ERROR_MALFORMED;
				}
		
			}
#endif
#endif
            if (mWaveFormat == WAVE_FORMAT_EXTENSIBLE) {
                uint16_t validBitsPerSample = U16_LE_AT(&formatSpec[18]);
                if (validBitsPerSample != mBitsPerSample) {
                    if (validBitsPerSample != 0) {
                        ALOGE("validBits(%d) != bitsPerSample(%d) are not supported",
                                validBitsPerSample, mBitsPerSample);
                        return ERROR_UNSUPPORTED;
                    } else {
                        // we only support valitBitsPerSample == bitsPerSample but some WAV_EXT
                        // writers don't correctly set the valid bits value, and leave it at 0.
                        ALOGW("WAVE_EXT has 0 valid bits per sample, ignoring");
                    }
                }

                mChannelMask = U32_LE_AT(&formatSpec[20]);
                ALOGV("numChannels=%d channelMask=0x%x", mNumChannels, mChannelMask);
                if ((mChannelMask >> 18) != 0) {
                    ALOGE("invalid channel mask 0x%x", mChannelMask);
                    return ERROR_MALFORMED;
                }

                if ((mChannelMask != CHANNEL_MASK_USE_CHANNEL_ORDER)
                        && (popcount(mChannelMask) != mNumChannels)) {
                    ALOGE("invalid number of channels (%d) in channel mask (0x%x)",
                            popcount(mChannelMask), mChannelMask);
                    return ERROR_MALFORMED;
                }

                // In a WAVE_EXT header, the first two bytes of the GUID stored at byte 24 contain
                // the sample format, using the same definitions as a regular WAV header
                mWaveFormat = U16_LE_AT(&formatSpec[24]);
                if (mWaveFormat != WAVE_FORMAT_PCM
                        && mWaveFormat != WAVE_FORMAT_ALAW
                        && mWaveFormat != WAVE_FORMAT_MULAW
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
						&& mWaveFormat != WAVE_FORMAT_MSADPCM
						&& mWaveFormat != WAVE_FORMAT_DVI_IMAADCPM
#endif
#endif
						&& mWaveFormat != WAVE_FORMAT_MSGSM			
				)
                        {
                    return ERROR_UNSUPPORTED;
                }
                if (memcmp(&formatSpec[26], WAVEEXT_SUBFORMAT, 14)) {
                    ALOGE("unsupported GUID");
                    return ERROR_UNSUPPORTED;
                }
            }

            mValidFormat = true;
        }
#ifndef ANDROID_DEFAULT_CODE
		else if(!memcmp(chunkHeader, "fact", 4))
		{
			if(chunkSize != 4)
			{
				SXLOGD("fact chunk size is invailed, chunkSize is %d !!!", chunkSize);
//				return ERROR_MALFORMED;
			}
			uint8_t factChunkData[4];
			if(mDataSource->readAt(offset, factChunkData, 4) < 4)
			{
//				return ERROR_MALFORMED;
			}
			mSamplesNumberPerChannel = U32_LE_AT(factChunkData);
			if(mSamplesNumberPerChannel < 0)
			{
//				return ERROR_MALFORMED;
			}
			SXLOGD("fact chunk mChannelCount is %d, mSamplesNumberPerChannel is %d, mSamplesPerBlock is %d", mNumChannels, mSamplesNumberPerChannel, mSamplesPerBlock);
		}
#endif
		else if (!memcmp(chunkHeader, "data", 4)) {
            if (mValidFormat) {
                mDataOffset = offset;
                mDataSize = chunkSize;

                mTrackMeta = new MetaData;

				switch (mWaveFormat) {
					case WAVE_FORMAT_PCM:
						mTrackMeta->setCString(
								kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);
						break;
					case WAVE_FORMAT_ALAW:
						mTrackMeta->setCString(
								kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_G711_ALAW);
						break;
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
					case WAVE_FORMAT_MSADPCM:
						mTrackMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MS_ADPCM);
						break;
					case WAVE_FORMAT_DVI_IMAADCPM:
						mTrackMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_DVI_IMA_ADPCM);
						break;
#endif
#endif
					case WAVE_FORMAT_MSGSM:
						mTrackMeta->setCString(
								kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MSGSM);
						break;
					default:
						CHECK_EQ(mWaveFormat, (uint16_t)WAVE_FORMAT_MULAW);
						mTrackMeta->setCString(
								kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_G711_MLAW);
						break;
				}

                mTrackMeta->setInt32(kKeyChannelCount, mNumChannels);
                mTrackMeta->setInt32(kKeyChannelMask, mChannelMask);
                mTrackMeta->setInt32(kKeySampleRate, mSampleRate);
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
				SXLOGV("set value for metaData !!!");
				mTrackMeta->setInt32(kKeyBlockAlign, mBlockAlign);
				mTrackMeta->setInt32(kKeyBitsPerSample, (uint32_t)mBitsPerSample);
//				mTrackMeta->setInt32(kKeyExtraDataSize, mExtraDataSize);
				if(NULL != mpExtraData)
				{
					mTrackMeta->setData(kKeyExtraDataPointer, 0, mpExtraData, mExtraDataSize);
				}
				else
					SXLOGV("mpExtraData pointer is NULL !!!");
#endif
#endif
				size_t bytesPerSample = mBitsPerSample >> 3;
				int64_t durationUs = 0;
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
				SXLOGV("set duration value for metaData !!!");
				if(mWaveFormat == WAVE_FORMAT_MSADPCM || mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM)
				{
					SXLOGV("mSamplesPerBlock %d, mSampleRate %d, mDataSize %d, mBlockAlign %d", mSamplesPerBlock, mSampleRate, mDataSize, mBlockAlign);
					mBlockDurationUs = 1000000LL * mSamplesPerBlock / mSampleRate;
					durationUs = (mDataSize / mBlockAlign) * mBlockDurationUs;
					SXLOGV("mBlockDurationUs is %.2f secs, durationUs is %.2f secs", mBlockDurationUs / 1E6, durationUs / 1E6);

					/*
					int64_t durationUs = mDataSize / (mAvgBytesPerSec / 1000000LL);
					*/
				}
				else
#endif
#endif
				if(mWaveFormat == WAVE_FORMAT_MSGSM)
				{
					// 65 bytes decode to 320 8kHz samples
					durationUs = 1000000LL * (mDataSize / 65 * 320) / 8000;
					SXLOGD("mDataSize is %d, duration is %0.2f s", mDataSize, durationUs / 1E6);
				}
				else
                	durationUs =
                    	1000000LL * (mDataSize / (mNumChannels * bytesPerSample))
                        / mSampleRate;

				mTrackMeta->setInt64(kKeyDuration, durationUs);
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
				mTrackMeta->setInt64(kKeyBlockDurationUs, mBlockDurationUs);
#endif
#endif


#ifndef ANDROID_DEFAULT_CODE 
#ifdef MTK_AUDIO_RAW_SUPPORT
				mTrackMeta->setInt32(kKeyEndian, 2);               //little
				mTrackMeta->setInt32(kKeyBitWidth, mBitsPerSample);
				mTrackMeta->setInt32(kKeyPCMType, 1); 			   //pcm_wave
				if(mBitsPerSample == 8)
					mTrackMeta->setInt32(kKeyNumericalType, 2);        //unsigned 
				else
					mTrackMeta->setInt32(kKeyNumericalType, 1);        //signed 
#endif
#endif
                return OK;
            }
        }

        offset += chunkSize;
    }

    return NO_INIT;
}

const size_t WAVSource::kMaxFrameSize = 32768;

WAVSource::WAVSource(
        const sp<DataSource> &dataSource,
        const sp<MetaData> &meta,
        uint16_t waveFormat,
        int32_t bitsPerSample,
        off64_t offset, size_t size)
    : mDataSource(dataSource),
      mMeta(meta),
      mWaveFormat(waveFormat),
      mSampleRate(0),
      mNumChannels(0),
      mBitsPerSample(bitsPerSample),
      mOffset(offset),
      mSize(size),
      mStarted(false),
      mGroup(NULL)
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
	  ,
	  mBlockDurationUs(0),
	  mBlockAlign(0)
#endif
#endif
{
	SXLOGV("mSize is %d", mSize);
    CHECK(mMeta->findInt32(kKeySampleRate, &mSampleRate));
    CHECK(mMeta->findInt32(kKeyChannelCount, &mNumChannels));
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
	CHECK(mMeta->findInt64(kKeyBlockDurationUs, &mBlockDurationUs));
	CHECK(mMeta->findInt32(kKeyBlockAlign, &mBlockAlign));
	SXLOGV("mSize is %d, mBlockDurationUs %lld, mBlockAlign %d", mSize, mBlockDurationUs, mBlockAlign);
#endif
#endif

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
	if(mWaveFormat == WAVE_FORMAT_MSADPCM || mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM)
	{
		mMeta->setInt32(kKeyMaxInputSize, kMaxFrameSize / 4);
	}
	else
		mMeta->setInt32(kKeyMaxInputSize, kMaxFrameSize);
#else
	    mMeta->setInt32(kKeyMaxInputSize, kMaxFrameSize);
#endif
#else
	    mMeta->setInt32(kKeyMaxInputSize, kMaxFrameSize);
#endif

}

WAVSource::~WAVSource() {
    if (mStarted) {
        stop();
    }
}

status_t WAVSource::start(MetaData *params) {
    ALOGV("WAVSource::start");

    CHECK(!mStarted);

    mGroup = new MediaBufferGroup;
    mGroup->add_buffer(new MediaBuffer(kMaxFrameSize));

    if (mBitsPerSample == 8) {
        // As a temporary buffer for 8->16 bit conversion.
        mGroup->add_buffer(new MediaBuffer(kMaxFrameSize));
    }

    mCurrentPos = mOffset;

    mStarted = true;

    return OK;
}

status_t WAVSource::stop() {
    ALOGV("WAVSource::stop");

    CHECK(mStarted);

    delete mGroup;
    mGroup = NULL;

    mStarted = false;

    return OK;
}

sp<MetaData> WAVSource::getFormat() {
    ALOGV("WAVSource::getFormat");

    return mMeta;
}

status_t WAVSource::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;

    int64_t seekTimeUs;
	int64_t pos = 0;
    ReadOptions::SeekMode mode;
    if (options != NULL && options->getSeekTo(&seekTimeUs, &mode)) {
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
		if(mWaveFormat == WAVE_FORMAT_MSADPCM || mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM)
		{
			pos = (seekTimeUs + (int64_t)(mBlockDurationUs >> 1)) / mBlockDurationUs * mBlockAlign;
			SXLOGD("ADPCM seekTimeUs is %.2f secs", seekTimeUs / 1E6);
			SXLOGD("ADPCM mOffset %lld, pos %lld", mOffset, pos);
		}
		else
#endif
#endif
		if(mWaveFormat == WAVE_FORMAT_MSGSM)
		{
            // 65 bytes decode to 320 8kHz samples
            int64_t samplenumber = (seekTimeUs * mSampleRate) / 1000000;
            int64_t framenumber = samplenumber / 320;
            pos = framenumber * 65;
			SXLOGD("samplenumber is %d, framenumber is %d", samplenumber, framenumber);
		}
		else
        	pos = (seekTimeUs * mSampleRate) / 1000000 * mNumChannels * (mBitsPerSample >> 3);
        if (pos > mSize) {
            pos = mSize;
        }
        mCurrentPos = pos + mOffset;
    }

    MediaBuffer *buffer;
    status_t err = mGroup->acquire_buffer(&buffer);
    if (err != OK) {
        return err;
    }
#ifndef ANDROID_DEFAULT_CODE
	size_t maxBytesToRead;
	if(mWaveFormat == WAVE_FORMAT_PCM)
	{
		SXLOGV("PCM format, Read size kMaxFrameSize / 4");
		maxBytesToRead=kMaxFrameSize / 4;
	}
	else
	{
		maxBytesToRead=kMaxFrameSize;
	}
	
	if(mBitsPerSample == 8)
		maxBytesToRead = kMaxFrameSize / 2;
	else if(mBitsPerSample == 24) 
		maxBytesToRead = kMaxFrameSize/3*3;

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
	if(mWaveFormat == WAVE_FORMAT_MSADPCM || mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM)
	{
		maxBytesToRead = (kMaxFrameSize / 4 / mBlockAlign) * mBlockAlign; // divide 4 to decrease component output buffer size
	}
//	maxBytesToRead = mBlockAlign;
#endif
#endif
	if(mWaveFormat == WAVE_FORMAT_MSGSM)
	{
        // Microsoft packs 2 frames into 65 bytes, rather than using separate 33-byte frames,
        // so read multiples of 65, and use smaller buffers to account for ~10:1 expansion ratio
        if (maxBytesToRead > 1024) 
		{
            maxBytesToRead = 1024;
        }
        maxBytesToRead = (maxBytesToRead / 65) * 65;
		SXLOGD("maxBytesToRead is %d", maxBytesToRead);
	}
#else
    size_t maxBytesToRead =
        mBitsPerSample == 8 ? kMaxFrameSize / 2 : 
        (mBitsPerSample == 24 ? 3*(kMaxFrameSize/3): kMaxFrameSize);
#endif
    size_t maxBytesAvailable =
        (mCurrentPos - mOffset >= (off64_t)mSize)
            ? 0 : mSize - (mCurrentPos - mOffset);

    if (maxBytesToRead > maxBytesAvailable) {
        maxBytesToRead = maxBytesAvailable;
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT	
		if(mWaveFormat == WAVE_FORMAT_MSADPCM || mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM)
		{
			maxBytesToRead = (maxBytesToRead / mBlockAlign) * mBlockAlign;
		}
#endif
#endif

		if(mWaveFormat == WAVE_FORMAT_MSGSM)
		{
			maxBytesToRead = (maxBytesToRead / 65) * 65;
			SXLOGD("maxBytesToRead 2 is %d", maxBytesToRead);
		}

    }
    ssize_t n = mDataSource->readAt(
            mCurrentPos, buffer->data(),
            maxBytesToRead);

    if (n <= 0) {
        buffer->release();
        buffer = NULL;

        return ERROR_END_OF_STREAM;
    }

    buffer->set_range(0, n);

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_RAW_SUPPORT
	SXLOGD("PCM Data pass PCM Component !");
#else
    if (mWaveFormat == WAVE_FORMAT_PCM || mWaveFormat == WAVE_FORMAT_EXTENSIBLE) {
		if (mBitsPerSample == 8) {
			// Convert 8-bit unsigned samples to 16-bit signed.

			MediaBuffer *tmp;
			CHECK_EQ(mGroup->acquire_buffer(&tmp), (status_t)OK);

			// The new buffer holds the sample number of samples, but each
			// one is 2 bytes wide.
			tmp->set_range(0, 2 * n);

			int16_t *dst = (int16_t *)tmp->data();
			const uint8_t *src = (const uint8_t *)buffer->data();
			ssize_t numBytes = n;

			while (numBytes-- > 0) {
				*dst++ = ((int16_t)(*src) - 128) * 256;
				++src;
			}

			buffer->release();
			buffer = tmp;
		} else if (mBitsPerSample == 24) {
			// Convert 24-bit signed samples to 16-bit signed.

			const uint8_t *src =
				(const uint8_t *)buffer->data() + buffer->range_offset();
			int16_t *dst = (int16_t *)src;

			size_t numSamples = buffer->range_length() / 3;
			for (size_t i = 0; i < numSamples; ++i) {
				int32_t x = (int32_t)(src[0] | src[1] << 8 | src[2] << 16);
				x = (x << 8) >> 8;	// sign extension

				x = x >> 8;
				*dst++ = (int16_t)x;
				src += 3;
			}

			buffer->set_range(buffer->range_offset(), 2 * numSamples);
		}
	}
#endif
#else
    if (mWaveFormat == WAVE_FORMAT_PCM || mWaveFormat == WAVE_FORMAT_EXTENSIBLE) {
        if (mBitsPerSample == 8) {
            // Convert 8-bit unsigned samples to 16-bit signed.

            MediaBuffer *tmp;
            CHECK_EQ(mGroup->acquire_buffer(&tmp), (status_t)OK);

            // The new buffer holds the sample number of samples, but each
            // one is 2 bytes wide.
            tmp->set_range(0, 2 * n);

            int16_t *dst = (int16_t *)tmp->data();
            const uint8_t *src = (const uint8_t *)buffer->data();
            ssize_t numBytes = n;

            while (numBytes-- > 0) {
                *dst++ = ((int16_t)(*src) - 128) * 256;
                ++src;
            }

            buffer->release();
            buffer = tmp;
        } else if (mBitsPerSample == 24) {
            // Convert 24-bit signed samples to 16-bit signed.

            const uint8_t *src =
                (const uint8_t *)buffer->data() + buffer->range_offset();
            int16_t *dst = (int16_t *)src;

            size_t numSamples = buffer->range_length() / 3;
            for (size_t i = 0; i < numSamples; ++i) {
                int32_t x = (int32_t)(src[0] | src[1] << 8 | src[2] << 16);
                x = (x << 8) >> 8;  // sign extension

                x = x >> 8;
                *dst++ = (int16_t)x;
                src += 3;
            }

            buffer->set_range(buffer->range_offset(), 2 * numSamples);
        }
    }
#endif

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
	if(mWaveFormat == WAVE_FORMAT_MSADPCM || mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM)
	{
		SXLOGV("ADPCM timestamp of this buffer, mBlockAlign is %d, mBlockDurationUs is %lld +++", mBlockAlign, mBlockDurationUs);
		int64_t keyTimeUs = ((mCurrentPos - mOffset) / mBlockAlign) * mBlockDurationUs;
		buffer->meta_data()->setInt64(kKeyTime, keyTimeUs);
		SXLOGV("ADPCM timestamp of this buffer is %.2f secs, buffer length is %d", keyTimeUs / 1E6, n);
	}
	else
	{
#endif
#endif
		int64_t timeStampUs = 0;
		if (mWaveFormat == WAVE_FORMAT_MSGSM) 
		{
			timeStampUs = 1000000LL * (mCurrentPos - mOffset) * 320 / 65 / mSampleRate;
			SXLOGD("timeStampUs is %0.2f", timeStampUs / 1E6);
		} 
		else 
		{
        size_t bytesPerSample = mBitsPerSample >> 3;
        timeStampUs = 1000000LL * (mCurrentPos - mOffset)
                / (mNumChannels * bytesPerSample) / mSampleRate;
    }

    buffer->meta_data()->setInt64(kKeyTime, timeStampUs);
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_ADPCM_SUPPORT
	}
#endif
#endif

    buffer->meta_data()->setInt32(kKeyIsSyncFrame, 1);
    mCurrentPos += n;

    *out = buffer;

    return OK;
}

////////////////////////////////////////////////////////////////////////////////

bool SniffWAV(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *) {
    char header[12];
    if (source->readAt(0, header, sizeof(header)) < (ssize_t)sizeof(header)) {
        return false;
    }

    if (memcmp(header, "RIFF", 4) || memcmp(&header[8], "WAVE", 4)) {
        return false;
    }

    sp<MediaExtractor> extractor = new WAVExtractor(source);
    if (extractor->countTracks() == 0) {
        return false;
    }

    *mimeType = MEDIA_MIMETYPE_CONTAINER_WAV;
    *confidence = 0.3f;

    return true;
}

}  // namespace android
