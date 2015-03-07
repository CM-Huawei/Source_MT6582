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
#define LOG_TAG "TimedTextSUBSource"
#include <utils/Log.h>

#include <binder/Parcel.h>
#include <media/stagefright/foundation/ADebug.h>  // for CHECK_xx
#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaDefs.h>  // for MEDIA_MIMETYPE_xxx
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>

#include "TimedTextSUBSource.h"
#include "TextDescriptions.h"
#include "FileCacheManager.h"

namespace android {

const int32_t DEFAULT_FRAME_RATE = 30;


TimedTextSUBSource::TimedTextSUBSource(const sp<DataSource>& dataSource)
        : mSource(dataSource),
          mMetaData(new MetaData),
          mIndex(0),
          mFrameRate(TimedTextUtil::DEFAULT_FRAME_RATE){
	mFileEncodeType = ENCODE_TYPE_NORMAL;

#ifdef SELF_TEST
	scanFile();
	int64_t startTimeUs = 0;
	int64_t endTimeUs = 0;
	Parcel parcel;
	MediaSource::ReadOptions options;
	status_t err =OK;
	int len =7;
	int st[] = {888, 18111, 22000, 28888, 38000, 54800, 76000};  //ms
	for(int i =0; i< len; i++){
		int64_t temp = st[i] * 1000ll;  //us
		options.setSeekTo(temp);  
		err = read(&startTimeUs, &endTimeUs, &parcel, &options);
		ALOGE("[--SELF_TEST--] seekTime=%lld, getStartTime=%lld, getEndTime=%lld, isReadSuccessfully:%d",  temp, startTimeUs, endTimeUs, err);
	}

#endif 
}

TimedTextSUBSource::~TimedTextSUBSource() {
}

status_t TimedTextSUBSource::start() {
    status_t err = scanFile();
    if (err != OK) {
        reset();
    }
    // TODO: Need to detect the language, because SUB doesn't give language
    // information explicitly.
    mMetaData->setCString(kKeyMediaLanguage, "");
    return err;
}

void TimedTextSUBSource::reset() {
    mMetaData->clear();
    mTextVector.clear();
    mIndex = 0;
}

status_t TimedTextSUBSource::stop() {
    reset();
    return OK;
}

status_t TimedTextSUBSource::read(
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

sp<MetaData> TimedTextSUBSource::getFormat() {
    return mMetaData;
}

status_t TimedTextSUBSource::scanFile() {
    off64_t offset = 0;
    int64_t startTimeUs;
    bool endOfFile = false;
	status_t err;
	MagicString line;
	off64_t fileStart = 0;
	bool isSpecailStyle = false;
	
	mFileEncodeType = TimedTextUtil::getFileEncodeType(mSource, &offset);	//*offset must be zero
	ALOGE("[SUB Parser] Scan mFileEncodeType = %d", mFileEncodeType);
	fileStart = offset;

	//Check format information 
	do {
		if ((err = TimedTextUtil::readNextLine(mSource, &offset, &line, mFileEncodeType)) != OK) {
			ALOGE("[SUB Parser]Reading Over Return Here! (EOS)");
			return err;
		}
		line.trim();
		MagicString::print("[First Line]", line);
	}while(line.empty());

	int64_t index = line.indexOf(MagicString::MAGIC_STRING_LARGE_BRACKETS);
	if(-1 == index){	//skip invalid line
		isSpecailStyle = true;		//The first line does not contain '{' in specail style
	}else{
		isSpecailStyle = false;		//The first line should contain '{' in normal style
	}


	offset = fileStart;

    while (!endOfFile) {
        TextInfoUtil info;
		if(isSpecailStyle){
			err = getSpecailNextSubInfo(&offset, &startTimeUs, &info);
		}else{
        	err = getNormalNextSubInfo(&offset, &startTimeUs, &info);
		}
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

/*  SUB format:
 *   There are two formats for describing SUB subtitle information
 *
 *   Format 1 (Normal Style):
 *   {StartFrame}{EndFrame}Subtitle Infomation
 *
 *  {12}{595}4items subtitle test.1-20seconds SUB subtitle test.
 *  {599}{1195}20-40seconds SUB subtitle test. 
 *  {1198}{1793}40seconds-1min SUB subtitle test. 
 *  {1798}{2397}1min-1min20seconds SUB subtitle test.
 *
 */
status_t TimedTextSUBSource::getNormalNextSubInfo(
          off64_t *offset, int64_t *startTimeUs, TextInfoUtil *info) {
	MagicString data("",mFileEncodeType);
	status_t err;
	int64_t lineBeginIndex = 0;
	int8_t typeSize = MagicString::sizeOfType(data.getType());

	// To skip blank lines.
    do {
		lineBeginIndex = *offset;
        if ((err = TimedTextUtil::readNextLine(mSource, offset, &data, mFileEncodeType)) != OK) {
			ALOGE("[SUB Parser]Reading Over Return Here! (EOS)");
			return err;
        }
        data.trim();
		MagicString::print("[Get Line]", data);

		if(data.empty()){	//To skip blank lines
			continue;
		}else{		//Parse startFrame, endFrame & subtitle information
			int64_t index = data.indexOf(MagicString::MAGIC_STRING_LARGE_BRACKETS);
			if(-1 == index){	//skip invalid line
				data.clear();
				continue;
			}
			int64_t startIndex = index;
			index = data.indexOf(MagicString::MAGIC_STRING_ANTI_LARGE_BRACKETS, startIndex);
			if(-1 == index){	//skip invalid line
				data.clear();
				continue;
			}
			sp<MagicString> startTimeMStr = data.subString(startIndex + typeSize, index - typeSize);
			startTimeMStr->trim();
			MagicString::print("[StartTime]", startTimeMStr);

			startIndex = index;
			index = data.indexOf(MagicString::MAGIC_STRING_LARGE_BRACKETS, startIndex);
			if(-1 == index){	//skip invalid line
				data.clear();
				continue;
			}
			startIndex = index;
			index = data.indexOf(MagicString::MAGIC_STRING_ANTI_LARGE_BRACKETS, startIndex);
			if(-1 == index){	//skip invalid line
				data.clear();
				continue;
			}
			sp<MagicString> endTimeMStr = data.subString(startIndex + typeSize, index - typeSize);
			endTimeMStr->trim();
			MagicString::print("[EndTime]",endTimeMStr);

			
			StructTime startTime = StructTime(startTimeMStr, mFrameRate); 
			StructTime endTime = StructTime(endTimeMStr, mFrameRate);
			
			*startTimeUs = startTime.calcTimeUs();
			info->endTimeUs = endTime.calcTimeUs();

			if (info->endTimeUs <= *startTimeUs) {	//skip invalid line
        		data.clear();
				continue;
    		}

			sp<MagicString> subtitle = data.subString(index + typeSize);
			MagicString::print("[Subtitle]", subtitle);

			info->offset = lineBeginIndex + index + typeSize;
			info->textLen = subtitle->length();
		    return OK;
		}
    } while (true);
}

/*
 *  Format 2 (Special Style):
 *  [INFORMATION]
 *  [TITLE]
 *  [AUTHOR]
 *  [SOURCE]
 *  [PRG]
 *  [FILEPATH]
 *  [DELAY] 0
 *  [CD TRACK] 0
 *  [COMMENT]
 *  [END INFORMATION]
 *  [SUBTITLE]
 *  [COLF] &HFFFFFF, [STYLE]bd,[SIZE]24,[FONT]Tahoma
 *  00:00:01.00,00:00:49.16
 *  012345678987654321
 *  
 *  00:00:49.60,00:00:55.84
 *  abcdefgabcdefg
 *  
 *  00:01:03.76,00:01:10.04
 *  aoeiuvbpmfdtnl
 */
status_t TimedTextSUBSource::getSpecailNextSubInfo(
          off64_t *offset, int64_t *startTimeUs, TextInfoUtil *info) {
	MagicString data("", mFileEncodeType);
	status_t err;
	int64_t lineBeginIndex = 0;
	int8_t typeSize = MagicString::sizeOfType(data.getType());

	// To skip blank lines.
    do {
		lineBeginIndex = *offset;
        if ((err = TimedTextUtil::readNextLine(mSource, offset, &data, mFileEncodeType)) != OK) {
			ALOGE("[SUB_SP Parser]Reading Over Return Here! (EOS)");
			return err;
        }
        data.trim();
		MagicString::print("[Get Line]", data);

		if(data.empty()){	//To skip blank lines
			continue;
		}else{		//Parse startFrame, endFrame & subtitle information
			int32_t index = data.indexOf(MagicString::MAGIC_STRING_MID_BRACKETS);
			if(-1 != index){   //skip [INFORMATION], [AUTHOR], [DELAY] etc...
				continue;
			}else{	
				//parsing 00:00:01.00,00:00:49.16
				int32_t index = data.indexOf(MagicString::MAGIC_STRING_COMMA);
				if(-1 == index || 0 == index){
					continue;
				}
				
				sp<MagicString> startTimeMStr = data.subString(0, index - typeSize);
				startTimeMStr->trim();
				MagicString::print("[StartTime]", startTimeMStr);

				sp<MagicString> endTimeMStr = data.subString(index + typeSize);
				endTimeMStr->trim();
				MagicString::print("[EndTime]", endTimeMStr);


				StructTime startTime = StructTime(startTimeMStr); 
				StructTime endTime = StructTime(endTimeMStr);
			
				*startTimeUs = startTime.calcTimeUs();
				info->endTimeUs = endTime.calcTimeUs();

				if (info->endTimeUs <= *startTimeUs) {	//skip invalid line
					continue;
	    		}
					
				info->offset = *offset;
				if ((err = TimedTextUtil::readNextLine(mSource, offset, &data, mFileEncodeType)) != OK) {
					ALOGE("[SUB_SP Parser]Reading Over Return Here! (EOS)");
					return err;
				}
				MagicString::print("[Get Subtitle]", data);
				info->textLen = data.length();
			    return OK;
			}
		}
    } while (true);
}

status_t TimedTextSUBSource::getText(
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
					ALOGE("[SUB GetText] Can Not Found Any Subtitle Information At Time:%lld", seekTimeUs);
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
    ALOGE("[SUB GetText] Information sTime=%lld, endTime=%lld, index=%d", *startTimeUs, *endTimeUs, mIndex);
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

status_t TimedTextSUBSource::extractAndAppendLocalDescriptions(
        int64_t timeUs, MagicString* text, Parcel *parcel) {
    const void *data = text->c_str();
    size_t size = text->length();
    int32_t flag = TextDescriptions::LOCAL_DESCRIPTIONS |
                   TextDescriptions::OUT_OF_BAND_TEXT_SUB;

    if (size > 0) {
        return TextDescriptions::getParcelOfDescriptions(
                (const uint8_t *)data, size, flag, timeUs / 1000, parcel);
    }
    return OK;
}

int TimedTextSUBSource::compareExtendedRangeAndTime(size_t index, int64_t timeUs) {
    CHECK_LT(index, mTextVector.size());
    int64_t endTimeUs = mTextVector.valueAt(index).endTimeUs;
    int64_t startTimeUs = (index > 0) ?
            mTextVector.valueAt(index - 1).endTimeUs : 0;
	//ALOGE("[SUB Parser]compareTime: time=%lld, sTime=%lld, eTime=%lld", timeUs, startTimeUs, endTimeUs);
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

