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
#define LOG_TAG "TimedTextTXTSource"
#include <utils/Log.h>

#include <binder/Parcel.h>
#include <media/stagefright/foundation/ADebug.h>  // CHECK_XX macro
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDefs.h>  // for MEDIA_MIMETYPE_xxx
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>

#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/DataSource.h>

#include "TimedTextTXTSource.h"
#include "TextDescriptions.h"
#include "StructTime.h"
#include "FileCacheManager.h"

namespace android {


TimedTextTXTSource::~TimedTextTXTSource() {
}

//==============================Internal SP Case===============================
TimedTextTXTSource::TimedTextTXTSource(const sp<MediaSource>& mediaSource)
    : mInSource(mediaSource) {

        mTXTFlag = TextDescriptions::IN_BAND_TEXT_TXT;
}

status_t TimedTextTXTSource::in_read(
        int64_t *startTimeUs, int64_t *endTimeUs, Parcel *parcel,
        const MediaSource::ReadOptions *options) {
    MediaBuffer *textBuffer = NULL;
    status_t err = mInSource->read(&textBuffer, options);
    if (err != OK) {
        return err;
    }
    CHECK(textBuffer != NULL);
    textBuffer->meta_data()->findInt64(kKeyTime, startTimeUs);
    textBuffer->meta_data()->findInt64(kKeyDriftTime, endTimeUs);
    //CHECK_GE(*startTimeUs, 0);
    ALOGE("[--dbg--] txt internal subtitle in_read sTime=%lld, eTime=%lld", *startTimeUs, *endTimeUs);
    extractAndAppendLocalDescriptions(*startTimeUs, textBuffer, parcel);
    textBuffer->release();
    return OK;
}

status_t TimedTextTXTSource::in_start()
{
    return mInSource->start();
}

status_t TimedTextTXTSource::in_stop()
{
    return mInSource->stop();
}

MagicString* TimedTextTXTSource::parseText(const char* data, size_t size){
	MagicString * finalSubInfo = new MagicString(data, 0, size, ENCODE_TYPE_NORMAL);
	if(NULL != finalSubInfo){ 
		TimedTextUtil::removeStyleInfo(finalSubInfo);
		return finalSubInfo;
	}else{
		return NULL;
	}
}


// Each text sample consists of a string of text, optionally with sample
// modifier description. The modifier description could specify a new
// text style for the string of text. These descriptions are present only
// if they are needed. This method is used to extract the modifier
// description and append it at the end of the text.
status_t TimedTextTXTSource::in_extractAndAppendLocalDescriptions(
        int64_t timeUs, const MediaBuffer *textBuffer, Parcel *parcel) {
    const void *data;
    size_t size = 0;
    int32_t flag = TextDescriptions::LOCAL_DESCRIPTIONS;

    const char *mime;
    CHECK(mInSource->getFormat()->findCString(kKeyMIMEType, &mime));
    CHECK(strcasecmp(mime, MEDIA_MIMETYPE_TEXT_TXT) == 0);

    data = textBuffer->data();
    size = textBuffer->size();
		
	MagicString* textData = parseText((const char*) data, size);
	//MagicString::print("[Internal TXT] ", *textData);
	

	if(NULL != textData){
	    if (size > 0) {
	      parcel->freeData();
	      flag |= TextDescriptions::IN_BAND_TEXT_TXT;
	      return TextDescriptions::getParcelOfDescriptions(
	          (const uint8_t *)textData->c_str(), size, flag, timeUs / 1000, parcel);
	    }
		free(textData);
	}
    return OK;
}

status_t TimedTextTXTSource::in_extractGlobalDescriptions(Parcel *parcel) {
    const void *data;
    size_t size = 0;
    int32_t flag = TextDescriptions::GLOBAL_DESCRIPTIONS;

    const char *mime;
    CHECK(mInSource->getFormat()->findCString(kKeyMIMEType, &mime));
    CHECK(strcasecmp(mime, MEDIA_MIMETYPE_TEXT_TXT) == 0);

    uint32_t type;
    if (!mInSource->getFormat()->findData(
            kKeyTextFormatData, &type, &data, &size)) {
        return ERROR_MALFORMED;
    }

    if (size > 0) {
        flag |= TextDescriptions::IN_BAND_TEXT_TXT;
        return TextDescriptions::getParcelOfDescriptions(
                (const uint8_t *)data, size, flag, 0, parcel);
    }
    return OK;
}
//==============================External SP Case===============================
TimedTextTXTSource::TimedTextTXTSource(const sp<DataSource>& dataSource)
    : mExSource(dataSource),
     mExMetaData(new MetaData),
     mExIndex(0) {

    mTXTFlag = TextDescriptions::OUT_OF_BAND_TEXT_TXT;

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

void TimedTextTXTSource::selectLangIndex(int8_t index){
	if(index >= 0) {
		mExIndex = index;
	}
}

int8_t TimedTextTXTSource::getSelectedLangIndex(){
	return mExIndex;
}


status_t TimedTextTXTSource::ex_read(
        int64_t *startTimeUs, int64_t *endTimeUs, Parcel *parcel,
        const MediaSource::ReadOptions *options) {
    MagicString text("", mFileEncodeType);
    status_t err = getText(options, &text, startTimeUs, endTimeUs);
    if (err != OK) {
        return err;
    }

    //CHECK_GE(*startTimeUs, 0);
    ex_extractAndAppendLocalDescriptions(*startTimeUs, &text, parcel);
    return OK;
}

void TimedTextTXTSource::ex_reset() {
    mExMetaData->clear();
    mTextVector.clear();
    mExIndex = 0;
}

status_t TimedTextTXTSource::ex_start() {
    status_t err = scanFile();
    if (err != OK) {
        ex_reset();
    }
    // information explicitly.
    mExMetaData->setCString(kKeyMediaLanguage, "");
    return OK;
}

status_t TimedTextTXTSource::ex_stop() {
    ex_reset();
    return OK;
}

void TimedTextTXTSource::adjustPreSubEndTime(int64_t curStartTimeUs, MultiTextInfo info){
	int vSize = mTextVector.size();
	if( 0 == vSize ){ //Will use the first lang as default one.
		selectLangIndex(info.langIndex);
	}
	for(int i= vSize -1 ; i >= 0 ; i--){
		MultiTextInfo temp = mTextVector.valueAt(i);
		if( temp.langIndex == info.langIndex ){
			temp.endTimeUs = curStartTimeUs;
			mTextVector.add(mTextVector.keyAt(i), temp);
			ALOGE("[TXT Parser] Adjust item %d, eTime=%lld, offset=%lld, len=%d, sTime=%lld", i,
				temp.endTimeUs, temp.offset, temp.textLen, mTextVector.keyAt(i));
			break;
		}
	}
}

status_t TimedTextTXTSource::scanFile(){
	off64_t offset = 0;
	int64_t startTimeUs =0;
	bool endOfFile = false;
	status_t err;

	mFileEncodeType = TimedTextUtil::getFileEncodeType(mExSource, &offset);	//*offset must be zero
	ALOGE("[TXT Parser] Scan mFileEncodeType = %d", mFileEncodeType);

	while (!endOfFile) {
		MultiTextInfo info;

		err = getNextSubtitleInfo(&offset, &startTimeUs, &info);
		switch (err) {
			case OK:
				adjustPreSubEndTime(startTimeUs, info);
				mTextVector.add(startTimeUs, info);
			#ifdef SELF_TEST
				ALOGE("TXT langIndex:%d, startTime:%lld, endTime:%lld", info.langIndex, startTimeUs, info.endTimeUs);
			#endif
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

//There are two styles in txt subtitle file.
//Simple Style:
//0:00:01:4items subtitle test.1-20seconds TXT subtitle test.
//Specail Style:
//0:00:01,1=[Lang 1]4items subtitle test.1-20seconds TXT subtitle test.
//0:00:01,1=[Lang 2]4items subtitle test.1-20seconds TXT subtitle test.

status_t TimedTextTXTSource::getNextSubtitleInfo(
		off64_t *offset, int64_t *startTimeUs, MultiTextInfo *info){
	MagicString data("", mFileEncodeType);
	status_t err;
    int64_t lineBeginIndex = 0;
	int32_t noneSpaceStartPos = 0;
	// To skip blank lines.
    do {
		lineBeginIndex = *offset;
        if ((err = TimedTextUtil::readNextLine(mExSource, offset, &data, mFileEncodeType)) != OK) {
			ALOGE("Reading Error Return Here!");
			return err;
        }
        data.trim(&noneSpaceStartPos);
		MagicString::print("[Get Line]", data);

		if(data.empty()){	//To skip blank lines
			ALOGE("[TXT Parser] data is empty! continue;");
			continue;
		}else{		//Parse startFrame, endFrame & subtitle information
			int8_t typeSize = MagicString::sizeOfType(mFileEncodeType);
			int64_t index = data.indexOf(MagicString::MAGIC_STRING_COLON);
			if(-1 == index){	//skip invalid line
				ALOGE("[TXT Parser] Can Not Found The First COLON! Skip To Parse Next One.");
				continue;
			}
			int64_t startIndex = index;
			index = data.indexOf(MagicString::MAGIC_STRING_COLON, startIndex + typeSize);
			if(-1 == index){	//skip invalid line
				ALOGE("[TXT Parser] Can Not Found The Second COLON! Skip To Parse Next One.");
				continue;
			}
			startIndex = index;
			index = data.indexOf(MagicString::MAGIC_STRING_COLON, startIndex + typeSize);
			if(-1 == index){	//skip invalid line
				//Deal with specail style
				//0:00:01,1=[Lang 1]4items subtitle test.1-20seconds TXT subtitle test.
				ALOGE("[TXT Parser] Can Not Found The Third COLON! Maybe it is muti-lang case!");
				index = data.indexOf(MagicString::MAGIC_STRING_COMMA, startIndex + typeSize);
				if(-1 == index){
					ALOGE("[TXT Parser] Format Error! Skip To Parse Next One");
					continue;
				}else{
					ALOGE("[TXT Parser] Muti Lang Flow. index=%lld", index);
					startIndex = index;
					index =  data.indexOf(MagicString::MAGIC_STRING_EQUAL, startIndex + typeSize);
					if(-1 == index){
						ALOGE("[TXT Parser] Format Error! Skip To Parse Next One");
						continue;
					}else{
						sp<MagicString> startTimeMStr = data.subString(0,  startIndex - typeSize);
						startTimeMStr->trim();
						MagicString::print("[StartTime]", startTimeMStr);
						StructTime startTime = StructTime(startTimeMStr);

						*startTimeUs = startTime.calcTimeUs();
						info->endTimeUs = 0x0FFFFFFFFFFFFFFF;	//will be wrote with right value when parsing over next subtitle infomation

						sp<MagicString> langIndexMStr = data.subString(startIndex + typeSize, index - typeSize);
						MagicString::print("[LangIndex]", langIndexMStr);
						int8_t langIndex = MagicString::getIntValue(langIndexMStr);
						info->langIndex = langIndex;

						sp<MagicString> subtitle = data.subString(index + typeSize);
						MagicString::print("[Get Subtitle]", subtitle);

						info->offset = lineBeginIndex + noneSpaceStartPos + index + typeSize;
						info->textLen= subtitle->length();
						return OK;
					}
				}

			}else{
				//Deal with simple style
				//0:00:01:4items subtitle test.1-20seconds TXT subtitle test.
				ALOGE("[TXT Parser] index=%lld", index);
				info->langIndex = 0;
				sp<MagicString> startTimeMStr = data.subString(0,  index - typeSize);
				startTimeMStr->trim();
				MagicString::print("[StartTime]", startTimeMStr);

				StructTime startTime = StructTime(startTimeMStr);

				*startTimeUs = startTime.calcTimeUs();
				info->endTimeUs = 0x0FFFFFFFFFFFFFFF;	//will be wrote with right value when parsing over next subtitle infomation

				sp<MagicString> subtitle = data.subString(index + typeSize);
				MagicString::print("[Get Subtitle]", subtitle);

				info->offset = lineBeginIndex + noneSpaceStartPos + index + typeSize;
				info->textLen= subtitle->length();
				return OK;
			}
		}
    } while (true);
}


status_t TimedTextTXTSource::getText(
			const MediaSource::ReadOptions *options,
			MagicString *text, int64_t *startTimeUs, int64_t *endTimeUs){
	return getText(options, text, startTimeUs, endTimeUs, mExIndex);
}

status_t TimedTextTXTSource::getText(
			const MediaSource::ReadOptions *options,
			MagicString *text, int64_t *startTimeUs, int64_t *endTimeUs, int8_t selectedLangIndex){
//	ALOGE("[TXT Parser] getText vector size=%d, sLangIndex=%d", mTextVector.size(), selectedLangIndex);
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
				int diff = compareExtendedRangeAndTime(mid, seekTimeUs, selectedLangIndex);
				if (diff == 0) {
					break;
				} else if (diff < 0) {
					low = mid + 1;
				} else {
					high = mid - 1;
				}
			}
			mCurIndex = mid;
		}
	}

	if (mCurIndex >= mTextVector.size()) {
		return ERROR_END_OF_STREAM;
	}

	const MultiTextInfo &info = mTextVector.valueAt(mCurIndex);
	*startTimeUs = mTextVector.keyAt(mCurIndex);
	*endTimeUs = info.endTimeUs;
	ALOGE("[TXT GetText] Info index=%d, sTime=%lld, endTime=%lld, langIndex=%d ", mCurIndex, *startTimeUs, *endTimeUs,  info.langIndex);
	mCurIndex++;

	char *str = new char[info.textLen];
	if (FileCacheManager::getInstance().readFromCache(mExSource, info.offset, str, info.textLen) < info.textLen) {
		delete[] str;
		return ERROR_IO;
	}
	text->append(str, 0, info.textLen, mFileEncodeType);
	TimedTextUtil::removeStyleInfo(text);
	MagicString::print("[GetText]", *text);
	delete[] str;
	return OK;
}


int TimedTextTXTSource::compareExtendedRangeAndTime(size_t index, int64_t timeUs, int8_t selectedLangIndex) {
    CHECK_LT(index, mTextVector.size());
    int64_t endTimeUs = mTextVector.valueAt(index).endTimeUs;
    int64_t startTimeUs = (index > 0) ?
            mTextVector.valueAt(index - 1).endTimeUs : 0;
	//ALOGE("[TXT Parser]compareTime: time=%lld, sTime=%lld, eTime=%lld, index=%d", timeUs, startTimeUs, endTimeUs,index);
    if (timeUs >= startTimeUs && timeUs < endTimeUs) {
		int8_t infoIndex = mTextVector.valueAt(index).langIndex;
		if( infoIndex < selectedLangIndex){
			return -1;
		}else if( infoIndex > selectedLangIndex ){
			return 1;
		}
        return 0;
    } else if (endTimeUs <= timeUs) {
        return -1;
    } else {
        return 1;
    }
}


status_t TimedTextTXTSource::ex_extractAndAppendLocalDescriptions(
        int64_t timeUs, MagicString *text, Parcel *parcel) {
    const void *data = text->c_str();
    size_t size = text->length();
    int32_t flag = TextDescriptions::LOCAL_DESCRIPTIONS |
                   TextDescriptions::OUT_OF_BAND_TEXT_TXT;

    if (size > 0) {
        return TextDescriptions::getParcelOfDescriptions(
                (const uint8_t *)data, size, flag, timeUs / 1000, parcel);
    }
    return OK;
}

status_t TimedTextTXTSource::ex_extractGlobalDescriptions(Parcel *parcel) {
       return OK;
}


//==============================Interface function===============================
status_t TimedTextTXTSource::read(
        int64_t *startTimeUs, int64_t *endTimeUs, Parcel *parcel,
        const MediaSource::ReadOptions *options) {

    if(mTXTFlag == TextDescriptions::IN_BAND_TEXT_TXT)
    {
        return in_read(startTimeUs, endTimeUs, parcel, options);
    }
    else// if(mTXTFlag == TextDescriptions::OUT_OF_BAND_TEXT_TXT)
    {
        return ex_read(startTimeUs, endTimeUs, parcel, options);
    }
}

status_t TimedTextTXTSource::start()
{
    if(mTXTFlag == TextDescriptions::IN_BAND_TEXT_TXT)
    {
        return in_start();
    }
    else// if(mTXTFlag == TextDescriptions::OUT_OF_BAND_TEXT_TXT)
    {
        return ex_start();
    }
}

status_t TimedTextTXTSource::stop()
{
    if(mTXTFlag == TextDescriptions::IN_BAND_TEXT_TXT)
    {
        return in_stop();
    }
    else// if(mTXTFlag == TextDescriptions::OUT_OF_BAND_TEXT_TXT)
    {
        return ex_stop();
    }
}

status_t TimedTextTXTSource::extractAndAppendLocalDescriptions(
        int64_t timeUs, const MediaBuffer *textBuffer, Parcel *parcel) {
	return in_extractAndAppendLocalDescriptions(timeUs, textBuffer, parcel);
}

status_t TimedTextTXTSource::extractGlobalDescriptions(Parcel *parcel) {
	if(mTXTFlag == TextDescriptions::IN_BAND_TEXT_TXT)
	{
		return in_extractGlobalDescriptions(parcel);
	}
	return OK;
}

sp<MetaData> TimedTextTXTSource::getFormat() {
    if(mTXTFlag == TextDescriptions::IN_BAND_TEXT_TXT)
    {
        return mInSource->getFormat();
    }
    else// if(mTXTFlag == TextDescriptions::OUT_OF_BAND_TEXT_TXT)
    {
        return mExMetaData;
    }
}

}  // namespace android

#endif

