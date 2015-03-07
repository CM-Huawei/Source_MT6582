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

#ifdef MTK_SUBTITLE_SUPPORT

//#define LOG_NDEBUG 0
#define LOG_TAG "TimedTextMPLSource"
#include <utils/Log.h>

#include <binder/Parcel.h>
#include <media/stagefright/foundation/ADebug.h>  // for CHECK_xx
#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaDefs.h>  // for MEDIA_MIMETYPE_xxx
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>

#include "TimedTextMPLSource.h"
#include "TextDescriptions.h"
#include "FileCacheManager.h"

namespace android {

const int32_t TimedTextMPLSource::DEFAULT_FRAME_RATE = 30;


TimedTextMPLSource::TimedTextMPLSource(const sp<DataSource>& dataSource): 
	mSource(dataSource),
	mMetaData(new MetaData),
	mIndex(0),
	mFrameRate(TimedTextUtil::DEFAULT_FRAME_RATE){
	mFileEncodeType = ENCODE_TYPE_NORMAL;
#ifdef SELF_TEST
	scanFile();
	int64_t startTimeUs = 0;
	int64_t endTimeUs = 0;
	Parcel parcel;
	status_t err =OK;
	MediaSource::ReadOptions options;
	int len =7;
	int st[] = {888, 1111, 2000, 1888, 18000, 24800, 26000};   //ms
	for(int i =0; i< len; i++){
		int64_t temp = st[i] * 1000ll;  //us
		options.setSeekTo(temp); 
		err = read(&startTimeUs, &endTimeUs, &parcel, &options);
		ALOGE("[--SELF_TEST--] seekTime=%lld, getStartTime=%lld, getEndTime=%lld, isReadSuccessfully=%d",  temp, startTimeUs, endTimeUs, err);
	}
#endif 
}

TimedTextMPLSource::~TimedTextMPLSource() {
}

void  TimedTextMPLSource::setFrameRate(int32_t frameRate){
	mFrameRate = frameRate;
}


status_t TimedTextMPLSource::start() {
    status_t err = scanFile();
    if (err != OK) {
        reset();
    }
    // TODO: Need to detect the language, because MPL doesn't give language
    // information explicitly.
    mMetaData->setCString(kKeyMediaLanguage, "");
    return err;
}

void TimedTextMPLSource::reset() {
    mMetaData->clear();
    mTextVector.clear();
    mIndex = 0;
}

status_t TimedTextMPLSource::stop() {
    reset();
    return OK;
}

status_t TimedTextMPLSource::read(
        int64_t *startTimeUs,
        int64_t *endTimeUs,
        Parcel *parcel,
        const MediaSource::ReadOptions *options) {
    MagicString text("", mFileEncodeType);
    status_t err = getText(options, &text, startTimeUs, endTimeUs);
    if (err != OK) {
        return err;
    }

    //CHECK_GE(*startTimeUs, 0);
    extractAndAppendLocalDescriptions(*startTimeUs, &text, parcel);
    return OK;
}

sp<MetaData> TimedTextMPLSource::getFormat() {
    return mMetaData;
}

status_t TimedTextMPLSource::scanFile() {
    off64_t offset = 0;
    int64_t startTimeUs;
    bool endOfFile = false;

	mFileEncodeType = TimedTextUtil::getFileEncodeType(mSource, &offset);	//*offset must be zero
	ALOGE("[MPL Parser] Scan mFileEncodeType = %d", mFileEncodeType);

    while (!endOfFile) {
        TextInfoUtil info;
        status_t err = getNextSubtitleInfo(&offset, &startTimeUs, &info);
        switch (err) {
            case OK:
                mTextVector.add(startTimeUs, info);
                break;
            case ERROR_END_OF_STREAM:
                endOfFile = true;
                break;
            default:
                return err;
        }
    }
    if (mTextVector.isEmpty()) {
        return ERROR_MALFORMED;
    }
    return OK;
}

/*  MPL format:
 *   [StartFrame][EndFrame]Subtitle Infomation
 *
 * [1][30]I have so many surprises
 * [374][403]Maybe he's not so bad.|I need the phone.
 * [404][453]Get out of here. Go!
 * [528][542]We're not giving up on you.
 */
status_t TimedTextMPLSource::getNextSubtitleInfo(
          off64_t *offset, int64_t *startTimeUs, TextInfoUtil *info) {
	MagicString data("", mFileEncodeType);
	status_t err;
    int64_t lineBeginIndex = 0;
	// To skip blank lines.
    do {
		lineBeginIndex = *offset;
        if ((err = TimedTextUtil::readNextLine(mSource, offset, &data, mFileEncodeType)) != OK) {
			ALOGE("Reading Error Return Here!");
			return err;
        }
		ALOGE("[Get Line] data size before=%d", data.length());
        data.trim();
		ALOGE("[Get Line] data size after trim=%d", data.length());
		MagicString::print("[Get Line]", data);

		if(data.empty()){	//To skip blank lines
			ALOGE("[MPL Parser] data is empty! continue;");
			continue;
		}else{		//Parse startFrame, endFrame & subtitle information
			int8_t typeSize = MagicString::sizeOfType(mFileEncodeType);
			int64_t index = data.indexOf(MagicString::MAGIC_STRING_MID_BRACKETS);
			if(-1 == index){	//skip invalid line
				ALOGE("[MPL Parser] Can Not Found MID_BRACKETS ! continue;");
				continue;
			}
			int64_t startIndex = index;
			index = data.indexOf(MagicString::MAGIC_STRING_ANTI_MID_BRACKETS, startIndex);
			if(-1 == index){	//skip invalid line
				ALOGE("[MPL Parser] Can Not Found ANTI_MID_BRACKETS ! continue;");
				continue;
			}
			ALOGE("[MPL Parser] index=%lld, sIndex=%lld", index, startIndex );
			sp<MagicString> startTimeMStr = data.subString(startIndex + typeSize, index - typeSize);
			startTimeMStr->trim();
			MagicString::print("[StartTime]", startTimeMStr);

			startIndex = index;
			index = data.indexOf(MagicString::MAGIC_STRING_MID_BRACKETS, startIndex);
			if(-1 == index){	//skip invalid line
				continue;
			}
			startIndex = index;
			index = data.indexOf(MagicString::MAGIC_STRING_ANTI_MID_BRACKETS, startIndex);
			if(-1 == index){	//skip invalid line
				continue;
			}
			sp<MagicString> endTimeMStr = data.subString(startIndex + typeSize, index - typeSize);
			endTimeMStr->trim();
			MagicString::print("[EndTime]", endTimeMStr);

			
			StructTime startTime = StructTime(startTimeMStr, mFrameRate); 
			StructTime endTime = StructTime(endTimeMStr, mFrameRate);
			
			*startTimeUs = startTime.calcTimeUs();
			info->endTimeUs = endTime.calcTimeUs();

			if (info->endTimeUs <= *startTimeUs) {	//skip invalid line
        		data.clear();
				continue;
    		}

			sp<MagicString> subtitle = data.subString(index + typeSize);
			MagicString::print("[Get Subtitle]", subtitle);

			info->offset = lineBeginIndex + index + typeSize;
			info->textLen= subtitle->length();
		    return OK;
		}
    } while (true);
}

status_t TimedTextMPLSource::getText(
        const MediaSource::ReadOptions *options,
        MagicString *text, int64_t *startTimeUs, int64_t *endTimeUs) {
    if (mTextVector.size() == 0) {
        return ERROR_END_OF_STREAM;
    }
    text->clear();
    int64_t seekTimeUs;
    MediaSource::ReadOptions::SeekMode mode;
    if (options != NULL && options->getSeekTo(&seekTimeUs, &mode)) {
        int64_t lastEndTimeUs =
                mTextVector.valueAt(mTextVector.size() - 1).endTimeUs;
        if (seekTimeUs < 0) {
            return ERROR_OUT_OF_RANGE;
        } else if (seekTimeUs >= lastEndTimeUs) {
            return ERROR_END_OF_STREAM;
        } else {
            // binary search
            size_t low = 0;
            size_t high = mTextVector.size() - 1;
            size_t mid = 0;

            while (low <= high) {
                mid = low + (high - low)/2;
                int diff = compareExtendedRangeAndTime(mid, seekTimeUs);
				/*if (diff == 0xFF){ 	//Can not found subtitle information 
					ALOGE("[MPL GetText] Can Not Found Any Subtitle Information At Time:%lld", seekTimeUs);
					return ERROR_OUT_OF_RANGE;
				}*/
				if (diff == 0) {
                    break;
                } else if (diff < 0) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            mIndex = mid;
        }
    }

    if (mIndex >= mTextVector.size()) {
        return ERROR_END_OF_STREAM;
    }

    const TextInfoUtil &info = mTextVector.valueAt(mIndex);
    *startTimeUs = mTextVector.keyAt(mIndex);
    *endTimeUs = info.endTimeUs;
	ALOGE("[MPL GetText] Information seekTime=%lld, endTime=%lld, index=%d", *startTimeUs, *endTimeUs, mIndex);

    mIndex++;

    char *str = new char[info.textLen];
    if (FileCacheManager::getInstance().readFromCache(mSource, info.offset, str, info.textLen) < info.textLen) {
        delete[] str;
        return ERROR_IO;
    }
    text->append(str, 0, info.textLen, mFileEncodeType);
	TimedTextUtil::removeStyleInfo(text);
	MagicString::print("[GetText]", *text);
    delete[] str;
    return OK;
}

status_t TimedTextMPLSource::extractAndAppendLocalDescriptions(
        int64_t timeUs, MagicString* text, Parcel *parcel) {
    const void *data = text->c_str();
    size_t size = text->length();
    int32_t flag = TextDescriptions::LOCAL_DESCRIPTIONS |
                   TextDescriptions::OUT_OF_BAND_TEXT_MPL;

    if (size > 0) {
        return TextDescriptions::getParcelOfDescriptions(
                (const uint8_t *)data, size, flag, timeUs / 1000, parcel);
    }
    return OK;
}

int TimedTextMPLSource::compareExtendedRangeAndTime(size_t index, int64_t timeUs) {
    CHECK_LT(index, mTextVector.size());
    int64_t endTimeUs = mTextVector.valueAt(index).endTimeUs;
    int64_t startTimeUs = (index > 0) ?
            mTextVector.valueAt(index - 1).endTimeUs : 0;
	ALOGE("compareTime: time=%lld, sTime=%lld, eTime=%lld", timeUs, startTimeUs, endTimeUs);
    if (timeUs >= startTimeUs && timeUs < endTimeUs) {
		/*int64_t nextStartTime = mTextVector.keyAt(index);
		if(timeUs < nextStartTime){   
			//Deal with such case. The previous subtiltle is from 1000ms to 2000ms.
			//And the next subtitle is from 2888ms to 3888ms. In this condition, app 
			//querys the subtitle infomation at 2222ms. We should return Null. 
			return 0xFF;
		}*/
        return 0;
    } else if (endTimeUs <= timeUs) {
        return -1;
    } else {
        return 1;
    }
}

}  // namespace android

#endif

