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
#define LOG_TAG "TimedTextSRTSource"
#include <utils/Log.h>

#include <binder/Parcel.h>
#include <media/stagefright/foundation/ADebug.h>  // for CHECK_xx
#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaDefs.h>  // for MEDIA_MIMETYPE_xxx
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>

#include "TimedTextSRTSource.h"
#include "TextDescriptions.h"

#ifdef MTK_SUBTITLE_SUPPORT
#include "FileCacheManager.h"
#endif


namespace android {

TimedTextSRTSource::TimedTextSRTSource(const sp<DataSource>& dataSource)
        : mSource(dataSource),
          mMetaData(new MetaData),
          mIndex(0) {
#ifdef MTK_SUBTITLE_SUPPORT
	mFileEncodeType = ENCODE_TYPE_NORMAL;
#endif

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


    // TODO: Need to detect the language, because SRT doesn't give language
    // information explicitly.
    mMetaData->setCString(kKeyMediaLanguage, "und");
}

TimedTextSRTSource::~TimedTextSRTSource() {
}

status_t TimedTextSRTSource::start() {
    status_t err = scanFile();
    if (err != OK) {
        reset();
    }
    return err;
}

void TimedTextSRTSource::reset() {
    mTextVector.clear();
    mIndex = 0;
}

status_t TimedTextSRTSource::stop() {
    reset();
    return OK;
}

status_t TimedTextSRTSource::read(
        int64_t *startTimeUs,
        int64_t *endTimeUs,
        Parcel *parcel,
        const MediaSource::ReadOptions *options) {
#ifdef MTK_SUBTITLE_SUPPORT
	MagicString text("", mFileEncodeType);
    status_t err = getText(options, &text, startTimeUs, endTimeUs);
    if (err != OK) {
        return err;
    }

    // CHECK_GE(*startTimeUs, 0);
    extractAndAppendLocalDescriptions(*startTimeUs, text, parcel);
    return OK;

#else
    AString text;
    status_t err = getText(options, &text, startTimeUs, endTimeUs);
    if (err != OK) {
        return err;
    }

    CHECK_GE(*startTimeUs, 0);
    extractAndAppendLocalDescriptions(*startTimeUs, text, parcel);
    return OK;
#endif
}

sp<MetaData> TimedTextSRTSource::getFormat() {
    return mMetaData;
}

status_t TimedTextSRTSource::scanFile() {
    off64_t offset = 0;
    int64_t startTimeUs;
    bool endOfFile = false;

#if 1
	int64_t endTimeUs;
	char *str;
#endif 

#ifdef MTK_SUBTITLE_SUPPORT
	mFileEncodeType = TimedTextUtil::getFileEncodeType(mSource, &offset);	//*offset must be zero
	ALOGE("[SRT Parser] Scan mFileEncodeType = %d", mFileEncodeType);
#endif
    while (!endOfFile) {
        TextInfo info;
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

/* SRT format:
 *   Subtitle number
 *   Start time --> End time
 *   Text of subtitle (one or more lines)
 *   Blank lines
 *
 * .srt file example:
 * 1
 * 00:00:20,000 --> 00:00:24,400
 * Altocumulus clouds occr between six thousand
 *
 * 2
 * 00:00:24,600 --> 00:00:27,800
 * and twenty thousand feet above ground level.
 */
status_t TimedTextSRTSource::getNextSubtitleInfo(
          off64_t *offset, int64_t *startTimeUs, TextInfo *info) {
#ifdef MTK_SUBTITLE_SUPPORT
	MagicString data("", mFileEncodeType);
	MagicString* spString = new MagicString("-->", ENCODE_TYPE_NORMAL);
	int8_t typeSize = MagicString::sizeOfType(data.getType());
	status_t err;

	// To skip blank lines.
    do {
        if ((err = TimedTextUtil::readNextLine(mSource, offset, &data, mFileEncodeType)) != OK) {
			ALOGE("[SRT Parser] Reading Over Return Here! (EOS)");
			return err;
        }
        data.trim();
		MagicString::print("[Get Line]", data);
    } while (data.empty());

	MagicString::print("[Subtitle Index]", data);
	// Just ignore the first non-blank line which is subtitle sequence number.
    if ((err = TimedTextUtil::readNextLine(mSource,	offset, &data, mFileEncodeType)) != OK) {
        return err;
    }

	MagicString::print("[Time Line]",data);
	int64_t index = data.indexOf(spString);
	if(-1 == index || 0 == index){
		return ERROR_MALFORMED;
	}	

	sp<MagicString> time1 = data.subString(0, index - typeSize);
	time1->trim();
	MagicString::print("[StartTime]", time1);
	sp<MagicString> time2 = data.subString(index + spString->length() * typeSize);
	time2->trim();
	MagicString::print("[EndTime]", time2);

	// the start time format is: hours:minutes:seconds,milliseconds
	// 00:00:24,600 --> 00:00:27,800
	StructTime startTime = StructTime(time1); 
	StructTime endTime = StructTime(time2);

	*startTimeUs = startTime.calcTimeUs();
	info->endTimeUs = endTime.calcTimeUs();

	free(spString);

	ALOGE("[SRTParser] getStartTime=%lld, getEndTime=%lld", *startTimeUs, info->endTimeUs);
    if (info->endTimeUs <= *startTimeUs) {
        return ERROR_MALFORMED;
    }

    info->offset = *offset;
    bool needMoreData = true;
	int64_t lineBeginIndex = 0;
	int64_t lineLen = 0;
    while (needMoreData) {
		lineBeginIndex = *offset;
        if ((err = TimedTextUtil::readNextLine(mSource, offset, &data, mFileEncodeType)) != OK) {
            if (err == ERROR_END_OF_STREAM) {
                break;
            } else {
                return err;
            }
        }
		lineLen = data.length();
        data.trim();
		MagicString::print("[Get Subtitle]", data);
        if (data.empty()) {
            // it's an empty line used to separate two subtitles
            needMoreData = false;
		}else{		
			info->textLen = lineBeginIndex + lineLen - info->offset;
			//ALOGE("[SRT Parser] read a subtitle line begin=%lld, lineLen =%lld, end=%d", lineBeginIndex, lineLen, info->textLen);
        }
    }
	//ALOGE("[SRT Parser] subtitle from=%lld, len=%d", info->offset, info->textLen);
    return OK;
#else
    AString data;
    status_t err;

    // To skip blank lines.
    do {
        if ((err = readNextLine(offset, &data)) != OK) {
            return err;
        }
        data.trim();
    } while (data.empty());

    // Just ignore the first non-blank line which is subtitle sequence number.
    if ((err = readNextLine(offset, &data)) != OK) {
        return err;
    }
    int hour1, hour2, min1, min2, sec1, sec2, msec1, msec2;
    // the start time format is: hours:minutes:seconds,milliseconds
    // 00:00:24,600 --> 00:00:27,800
    if (sscanf(data.c_str(), "%02d:%02d:%02d,%03d --> %02d:%02d:%02d,%03d",
               &hour1, &min1, &sec1, &msec1, &hour2, &min2, &sec2, &msec2) != 8) {
        return ERROR_MALFORMED;
    }

    *startTimeUs = ((hour1 * 3600 + min1 * 60 + sec1) * 1000 + msec1) * 1000ll;
    info->endTimeUs = ((hour2 * 3600 + min2 * 60 + sec2) * 1000 + msec2) * 1000ll;
    if (info->endTimeUs <= *startTimeUs) {
        return ERROR_MALFORMED;
    }

    info->offset = *offset;
    bool needMoreData = true;
    while (needMoreData) {
        if ((err = readNextLine(offset, &data)) != OK) {
            if (err == ERROR_END_OF_STREAM) {
                break;
            } else {
                return err;
            }
        }

        data.trim();
        if (data.empty()) {
            // it's an empty line used to separate two subtitles
            needMoreData = false;
        }
    }
    info->textLen = *offset - info->offset;
    return OK;
#endif
}

status_t TimedTextSRTSource::readNextLine(off64_t *offset, AString *data) {
    data->clear();
    while (true) {
        ssize_t readSize;
        char character;
        if ((readSize = mSource->readAt(*offset, &character, 1)) < 1) {
            if (readSize == 0) {
                return ERROR_END_OF_STREAM;
            }
            return ERROR_IO;
        }

        (*offset)++;

        // a line could end with CR, LF or CR + LF
        if (character == 10) {
            break;
        } else if (character == 13) {
            if ((readSize = mSource->readAt(*offset, &character, 1)) < 1) {
                if (readSize == 0) {  // end of the stream
                    return OK;
                }
                return ERROR_IO;
            }

            (*offset)++;
            if (character != 10) {
                (*offset)--;
            }
            break;
        }
        data->append(character);
    }
    return OK;
}

status_t TimedTextSRTSource::getText(
        const MediaSource::ReadOptions *options,
        AString *text, int64_t *startTimeUs, int64_t *endTimeUs) {
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
#ifndef ANDROID_DEFAULT_CODE
#if 0
    {
        int index = 0;
	while (index < mTextVector.size()) {
	    int64_t endTimeUs = mTextVector.valueAt(index).endTimeUs;
	    int64_t startTimeUs = (index > 0) ?
		mTextVector.valueAt(index - 1).endTimeUs : 0;
	    ALOGI("zxy dump %d: stat:%lld, end:%lld, timeUs:%lld", index, startTimeUs, endTimeUs, seekTimeUs);
            index++;
	}
    }
#endif
#endif

    const TextInfo &info = mTextVector.valueAt(mIndex);
    *startTimeUs = mTextVector.keyAt(mIndex);
    *endTimeUs = info.endTimeUs;
    mIndex++;

    char *str = new char[info.textLen];
    if (mSource->readAt(info.offset, str, info.textLen) < info.textLen) {
        delete[] str;
        return ERROR_IO;
    }
    text->append(str, info.textLen);
    delete[] str;
    return OK;
}


#ifdef MTK_SUBTITLE_SUPPORT
status_t TimedTextSRTSource::getText(
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
					ALOGE("[SRT GetText] Can Not Found Any Subtitle Information At Time:%lld", seekTimeUs);
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

    const TextInfo &info = mTextVector.valueAt(mIndex);
    *startTimeUs = mTextVector.keyAt(mIndex);
    *endTimeUs = info.endTimeUs;
	ALOGE("[SRT GetText] Information sTime=%lld, endTime=%lld, index=%d", *startTimeUs, *endTimeUs, mIndex);
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

status_t TimedTextSRTSource::extractAndAppendLocalDescriptions(
        int64_t timeUs, MagicString &text, Parcel *parcel) {
    const void *data = text.c_str();
    size_t size = text.length();
    int32_t flag = TextDescriptions::LOCAL_DESCRIPTIONS |
                   TextDescriptions::OUT_OF_BAND_TEXT_SRT;

    if (size > 0) {
        return TextDescriptions::getParcelOfDescriptions(
                (const uint8_t *)data, size, flag, timeUs / 1000, parcel);
    }
    return OK;
}


#endif


status_t TimedTextSRTSource::extractAndAppendLocalDescriptions(
        int64_t timeUs, const AString &text, Parcel *parcel) {
    const void *data = text.c_str();
    size_t size = text.size();
    int32_t flag = TextDescriptions::LOCAL_DESCRIPTIONS |
                   TextDescriptions::OUT_OF_BAND_TEXT_SRT;

    if (size > 0) {
        return TextDescriptions::getParcelOfDescriptions(
                (const uint8_t *)data, size, flag, timeUs / 1000, parcel);
    }
    return OK;
}

int TimedTextSRTSource::compareExtendedRangeAndTime(size_t index, int64_t timeUs) {
    CHECK_LT(index, mTextVector.size());
    int64_t endTimeUs = mTextVector.valueAt(index).endTimeUs;
    int64_t startTimeUs = (index > 0) ?
            mTextVector.valueAt(index - 1).endTimeUs : 0;
   //ALOGE("[SUB Parser]compareTime: time=%lld, sTime=%lld, eTime=%lld", timeUs, startTimeUs, endTimeUs);
    if (timeUs >= startTimeUs && timeUs < endTimeUs) {
/*
#ifdef MTK_SUBTITLE_SUPPORT
		int64_t nextStartTime = mTextVector.keyAt(index);
		if(timeUs < nextStartTime){   
			//Deal with such case. The previous subtiltle is from 1000ms to 2000ms.
			//And the next subtitle is from 2888ms to 3888ms. In this condition, app 
			//querys the subtitle infomation at 2222ms. We should return Null. 
			return 0xFF;
		}
#ifndef ANDROID_DEFAULT_CODE
	// last endtime is start time
	ALOGI("seek done, start:%lld, end:%lld, timeUs:%lld", startTimeUs, endTimeUs, timeUs);
#endif
*/
        return 0;
    } else if (endTimeUs <= timeUs) {
        return -1;
    } else {
        return 1;
    }
}

}  // namespace android
