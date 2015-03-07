/*
  * Copyright (C) 2012 The Android Open Source Project
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * 	 http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

 //#define LOG_NDEBUG 0
#ifdef MTK_SUBTITLE_SUPPORT

#define LOG_TAG "TimedTextSSASource"
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

#include "TimedTextSSASource.h"
#include "TextDescriptions.h"
#include "FileCacheManager.h"
#include "StructTime.h"

 namespace android {


 TimedTextSSASource::~TimedTextSSASource() {
 }

 //==============================Internal SP Case===============================
 TimedTextSSASource::TimedTextSSASource(const sp<MediaSource>& mediaSource)
	 : mInSource(mediaSource) {

		 mSSAFlag = TextDescriptions::IN_BAND_TEXT_SSA;
 }

 status_t TimedTextSSASource::in_read(
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
     ALOGE("[--dbg--] ssa internal subtitle in_read sTime=%lld, eTime=%lld", *startTimeUs, *endTimeUs);
	 extractAndAppendLocalDescriptions(*startTimeUs, textBuffer, parcel);
	 textBuffer->release();
	 return OK;
 }

 status_t TimedTextSSASource::in_start()
 {
	 return mInSource->start();
 }

 status_t TimedTextSSASource::in_stop()
 {
	 return mInSource->stop();
 }

 MagicString* TimedTextSSASource::parseText(const char* data, size_t size){
	 MagicString dataMStr(data, 0, size, ENCODE_TYPE_NORMAL);
	 MagicString::print("[Internal SSA] ", dataMStr);
	 int8_t loop = 8;
	 int32_t index = 0;
	 int32_t sIndex = index;
	 int8_t typeSize = 1;

	 for(int i= 0; i<loop ; i++){
		 index = dataMStr.indexOf(MagicString::MAGIC_STRING_COMMA, sIndex);
		 if(-1 == index){
			 return NULL;
		 }
		 sIndex = index + typeSize;
	 }

	MagicString* finalSubInfo = new MagicString(data, index + typeSize, size - index - typeSize, ENCODE_TYPE_NORMAL); 
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
 status_t TimedTextSSASource::in_extractAndAppendLocalDescriptions(
		 int64_t timeUs, const MediaBuffer *textBuffer, Parcel *parcel) {
	 const void *data;
	 size_t size = 0;
	 int32_t flag = TextDescriptions::LOCAL_DESCRIPTIONS;

	 const char *mime;
	 CHECK(mInSource->getFormat()->findCString(kKeyMIMEType, &mime));
	 CHECK(strcasecmp(mime, MEDIA_MIMETYPE_TEXT_SSA) == 0);

	 data = textBuffer->data();
	 size = textBuffer->size();
	
 	 MagicString * textData = parseText((const char *) data, size);
	 MagicString::print("[Internal SSA Subtitle] ", *textData);
	 if(NULL != textData){
		 if (size > 0) {
		   parcel->freeData();
		   flag |= TextDescriptions::IN_BAND_TEXT_SSA;
		   return TextDescriptions::getParcelOfDescriptions(
			   (const uint8_t *)textData->c_str(), textData->length(), flag, timeUs / 1000, parcel);
		 }
		 free(textData);
	 }
	 return OK;
 }

 status_t TimedTextSSASource::in_extractGlobalDescriptions(Parcel *parcel) {
	 const void *data;
	 size_t size = 0;
	 int32_t flag = TextDescriptions::GLOBAL_DESCRIPTIONS;

	 const char *mime;
	 CHECK(mInSource->getFormat()->findCString(kKeyMIMEType, &mime));
	 CHECK(strcasecmp(mime, MEDIA_MIMETYPE_TEXT_SSA) == 0);

	 uint32_t type;
	 if (!mInSource->getFormat()->findData(
			 kKeyTextFormatData, &type, &data, &size)) {
		 return ERROR_MALFORMED;
	 }

	 if (size > 0) {
		 flag |= TextDescriptions::IN_BAND_TEXT_SSA;
		 return TextDescriptions::getParcelOfDescriptions(
				 (const uint8_t *)data, size, flag, 0, parcel);
	 }
	 return OK;
 }
 //==============================External SP Case===============================
 TimedTextSSASource::TimedTextSSASource(const sp<DataSource>& dataSource)
	 : mExSource(dataSource),
	  mExMetaData(new MetaData),
	  mExIndex(0) {

	 mSSAFlag = TextDescriptions::OUT_OF_BAND_TEXT_SSA;


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
			 ALOGE("[--SELF_TEST--] seekTime=%lld, getStartTime=%lld, getEndTime=%lld, isReadSuccessfully:%d",	temp, startTimeUs, endTimeUs, err);
		 }
#endif
 }

 status_t TimedTextSSASource::skipHeadInfor(int64_t* offset){
	 status_t err = OK;
	 MagicString data("", mFileEncodeType);
	 MagicString keyword("[Events]", ENCODE_TYPE_NORMAL);
	 data.clear();
	 do{
		 if ((err = TimedTextUtil::readNextLine(mExSource, offset, &data, mFileEncodeType)) != OK) {
			 ALOGE("Reading Error Return Here!");
			 return err;
		 }

		 data.trim();
		 MagicString::print("[Skip Line]", data);

		 int32_t index = data.indexOf(&keyword);
		 if( 0 == index ){
			 ALOGE("[SSA Parser] Parse Head Info Over!");
			 return OK;
		 }

	 }while(true);
 }


 void TimedTextSSASource::selectLangIndex(int8_t index){
	 if(index >= 0) {
		 mExIndex = index;
	 }
 }

 int8_t TimedTextSSASource::getSelectedLangIndex(){
	 return mExIndex;
 }


 status_t TimedTextSSASource::scanFile(){
	 off64_t offset = 0;
	 int64_t startTimeUs =0;
	 bool endOfFile = false;
	 status_t err;

	 mFileEncodeType = TimedTextUtil::getFileEncodeType(mExSource, &offset); //*offset must be zero
	 ALOGE("[SSA Parser] Scan mFileEncodeType = %d", mFileEncodeType);

	 err = skipHeadInfor(&offset);
	 if(OK != err){
		 ALOGE("[SSA Parser] SSA File Format Error!");
		 return err;
	 }

	 while (!endOfFile) {
		 MultiTextInfo info;

		 err = getNextSubtitleInfo(&offset, &startTimeUs, &info);
		 switch (err) {
			 case OK:
				 if(0 == mTextVector.size()){	 //Select the first subtitle lang index as default value;
					 selectLangIndex(info.langIndex);
				 }
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


 //File Format Like Below:
 //[Events]
 //Format: Marked, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
 //Dialogue: Marked=0,0:00:00.00,0:00:20.10,Default,NTP,0000,0000,0000,!Effect,4items subtitle test. 0-20seconds SSA subtitle test.
 //Dialogue: Marked=0,0:00:20.11,0:00:40.10,Default,NTP,0000,0000,0000,!Effect,the subtitle is very closed with firest one and the gap is only 0:00:00.01. 20-40seconds SSA subtitle test.
 status_t TimedTextSSASource::getNextSubtitleInfo(
		 off64_t *offset, int64_t *startTimeUs, MultiTextInfo *info){
	 MagicString data("", mFileEncodeType);
	 MagicString keyword("Dialogue:", ENCODE_TYPE_NORMAL);
	 MagicString markMStr("Marked=", ENCODE_TYPE_NORMAL);
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

		 if(data.empty()){	 //To skip blank lines
			 ALOGE("[SSA Parser] data is empty! continue;");
			 continue;
		 }else{ 	 //Parse startFrame, endFrame & subtitle information
			 int8_t typeSize = MagicString::sizeOfType(mFileEncodeType);
			 int64_t index = data.indexOf(&keyword);
			 if(-1 == index){	 //skip invalid line
				 ALOGE("[SSA Parser] Can Not Found The keyword! Skip To Parse Next One.");
				 continue;
			 }
			 int64_t startIndex = index + keyword.length() * typeSize;
			 index = data.indexOf(MagicString::MAGIC_STRING_COMMA, startIndex + typeSize);
			 if(-1 == index){	 //skip invalid line
				 ALOGE("[SSA Parser] Format Error! Skip To Parse Next One.");
				 continue;
			 }

			 //Parse "Marked=0"
			 sp<MagicString> markInfo = data.subString(startIndex + typeSize, index - typeSize);
			 markInfo->trim();
			 MagicString::print("[Lang Index]", markInfo);
			 int mIndex = markInfo->indexOf(&markMStr);
			 if( -1 == mIndex ){ //maybe it does not contain "Marked=", but only contain "0".
				 info->langIndex = MagicString::getIntValue(markInfo);
			 }else{
			 	 sp<MagicString> markTemp = markInfo->subString(mIndex + markMStr.length()* typeSize);
				 info->langIndex = MagicString::getIntValue(markTemp);
			 }

			 startIndex = index;
			 index = data.indexOf(MagicString::MAGIC_STRING_COMMA, startIndex + typeSize);
			 if(-1 == index){	 //skip invalid line
				 ALOGE("[SSA Parser] Format Error! Skip To Parse Next One.");
				 continue;
			 }

			 //Parse Start Time
			 sp<MagicString> startTimeMStr = data.subString(startIndex + typeSize,	index - typeSize);
			 startTimeMStr->trim();
			 MagicString::print("[StartTime]", startTimeMStr);
			 StructTime startTime = StructTime(startTimeMStr);
			 *startTimeUs = startTime.calcTimeUs();

			 startIndex = index;
			 index = data.indexOf(MagicString::MAGIC_STRING_COMMA, startIndex + typeSize);
			 if(-1 == index){	 //skip invalid line
				 ALOGE("[SSA Parser] Format Error! Skip To Parse Next One.");
				 continue;
			 }

			 //Parse End Time
			 sp<MagicString> endTimeMStr = data.subString(startIndex + typeSize,  index - typeSize);
			 endTimeMStr->trim();
			 MagicString::print("[EndTime]", endTimeMStr);
			 StructTime endTime = StructTime(endTimeMStr);
			 info->endTimeUs = endTime.calcTimeUs();

			 //Skip Parse Style, Name, MarginL, MarginR, MarginV, Effect
			 for(int i=0; i<6 ; i++){
				 startIndex = index;
				 index = data.indexOf(MagicString::MAGIC_STRING_COMMA, startIndex + typeSize);
				 if(-1 == index){	 //skip invalid line
					 ALOGE("[SSA Parser] Format Error! Skip To Parse Next One.");
					 continue;
				 }
			 }

			 sp<MagicString> subtitle = data.subString(index + typeSize);
			 MagicString::print("[Get Subtitle]", subtitle);

			 info->offset = lineBeginIndex + noneSpaceStartPos + index + typeSize;
			 info->textLen= subtitle->length();
			 return OK;
		 }

	 } while (true);
 }


 status_t TimedTextSSASource::getText(
			 const MediaSource::ReadOptions *options,
			 MagicString *text, int64_t *startTimeUs, int64_t *endTimeUs){
	 return getText(options, text, startTimeUs, endTimeUs, mExIndex);
 }

 status_t TimedTextSSASource::getText(
			 const MediaSource::ReadOptions *options,
			 MagicString *text, int64_t *startTimeUs, int64_t *endTimeUs, int8_t selectedLangIndex){
 //  ALOGE("[TXT Parser] getText vector size=%d, sLangIndex=%d", mTextVector.size(), selectedLangIndex);
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
				 /*if (diff == 0xFF){  //Can not found subtitle information
					 ALOGE("[SSA GetText] Can Not Found Any Subtitle Information At Time:%lld", seekTimeUs);
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

 status_t TimedTextSSASource::ex_read(
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

 void TimedTextSSASource::ex_reset() {
	 mExMetaData->clear();
	 mTextVector.clear();
	 mExIndex = 0;
 }

 status_t TimedTextSSASource::ex_start() {
	 status_t err = scanFile();
	 if (err != OK) {
		 ex_reset();
	 }
	 // information explicitly.
	 mExMetaData->setCString(kKeyMediaLanguage, "");
	 return OK;
 }

 status_t TimedTextSSASource::ex_stop() {
	 ex_reset();
	 return OK;
 }

 status_t TimedTextSSASource::ex_extractAndAppendLocalDescriptions(
		 int64_t timeUs, MagicString *text, Parcel *parcel) {
	 const void *data = text->c_str();
	 size_t size = text->length();
	 int32_t flag = TextDescriptions::LOCAL_DESCRIPTIONS |
					TextDescriptions::OUT_OF_BAND_TEXT_SSA;

	 if (size > 0) {
		 return TextDescriptions::getParcelOfDescriptions(
				 (const uint8_t *)data, size, flag, timeUs / 1000, parcel);
	 }
	 return OK;
 }

 int TimedTextSSASource::compareExtendedRangeAndTime(size_t index, int64_t timeUs, int8_t selectedLangIndex) {
	 CHECK_LT(index, mTextVector.size());
	 int64_t endTimeUs = mTextVector.valueAt(index).endTimeUs;
	 int64_t startTimeUs = (index > 0) ?
			 mTextVector.valueAt(index - 1).endTimeUs : 0;
	 //ALOGE("[SSA Parser]compareTime: time=%lld, sTime=%lld, eTime=%lld, index=%d", timeUs, startTimeUs, endTimeUs,index);
	 if (timeUs >= startTimeUs && timeUs < endTimeUs) {
		 int8_t infoIndex = mTextVector.valueAt(index).langIndex;
		 if( infoIndex < selectedLangIndex){
			 return -1;
		 }else if( infoIndex > selectedLangIndex ){
			 return 1;
		 }

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

 status_t TimedTextSSASource::ex_extractGlobalDescriptions(Parcel *parcel) {
	 /*
	 const void *data;
	 size_t size = 0;
	 int32_t flag = TextDescriptions::GLOBAL_DESCRIPTIONS | TextDescriptions::OUT_OF_BAND_TEXT_SSA;
	 return TextDescriptions::getParcelOfDescriptions((const uint8_t *)data, size, flag, 0, parcel);
	 */
	 return OK;
 }


 //==============================Interface function===============================
 status_t TimedTextSSASource::read(
		 int64_t *startTimeUs, int64_t *endTimeUs, Parcel *parcel,
		 const MediaSource::ReadOptions *options) {

	 if(mSSAFlag == TextDescriptions::IN_BAND_TEXT_SSA)
	 {
		 return in_read(startTimeUs, endTimeUs, parcel, options);
	 }
	 else// if(mSSAFlag == TextDescriptions::OUT_OF_BAND_TEXT_SSA)
	 {
		 return ex_read(startTimeUs, endTimeUs, parcel, options);
	 }
 }

 status_t TimedTextSSASource::start()
 {
	 if(mSSAFlag == TextDescriptions::IN_BAND_TEXT_SSA)
	 {
		 return in_start();
	 }
	 else// if(mSSAFlag == TextDescriptions::OUT_OF_BAND_TEXT_SSA)
	 {
		 return ex_start();
	 }
 }

 status_t TimedTextSSASource::stop()
 {
	 if(mSSAFlag == TextDescriptions::IN_BAND_TEXT_SSA)
	 {
		 return in_stop();
	 }
	 else// if(mSSAFlag == TextDescriptions::OUT_OF_BAND_TEXT_SSA)
	 {
		 return ex_stop();
	 }
 }

 status_t TimedTextSSASource::extractAndAppendLocalDescriptions(
		 int64_t timeUs, const MediaBuffer *textBuffer, Parcel *parcel) {
	 return in_extractAndAppendLocalDescriptions(timeUs, textBuffer, parcel);
 }

 status_t TimedTextSSASource::extractGlobalDescriptions(Parcel *parcel) {
	 if(mSSAFlag == TextDescriptions::IN_BAND_TEXT_SSA)
	 {
	 	return in_extractGlobalDescriptions(parcel);
	 }
	 return OK;
 }

 sp<MetaData> TimedTextSSASource::getFormat() {
	 if(mSSAFlag == TextDescriptions::IN_BAND_TEXT_SSA)
	 {
		 return mInSource->getFormat();
	 }
	 else// if(mSSAFlag == TextDescriptions::OUT_OF_BAND_TEXT_SSA)
	 {
		 return mExMetaData;
	 }
 }

 }	// namespace android


#endif

