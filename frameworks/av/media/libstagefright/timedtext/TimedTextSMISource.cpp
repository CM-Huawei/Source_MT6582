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
#define LOG_TAG "TimedTextSMISource"
#include <utils/Log.h>

#include <binder/Parcel.h>
#include <media/stagefright/foundation/ADebug.h>  // for CHECK_xx
#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaDefs.h>  // for MEDIA_MIMETYPE_xxx
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>

#include "TimedTextSMISource.h"
#include "TextDescriptions.h"
#include "FileCacheManager.h"


namespace android {

const int32_t DEFAULT_FRAME_RATE = 30;


TimedTextSMISource::TimedTextSMISource(const sp<DataSource>& dataSource)
        : mSource(dataSource),
          mMetaData(new MetaData),
          mIndex(0),
          totalLang(0),
          selectedLangIndex(0),
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
	int st[] = {888, 11111, 22000, 18888, 38000, 54800, 76000, 88888}; //ms
	for(int i =0; i< len; i++){
		int64_t temp = st[i] * 1000ll; //us
		options.setSeekTo(temp); 
		err = read(&startTimeUs, &endTimeUs, &parcel, &options);
		ALOGE("[--SELF_TEST--] seekTime=%lld, getStartTime=%lld, getEndTime=%lld, isReadSuccessfully=%d",  temp, startTimeUs, endTimeUs, err);
	}

#endif 
}

TimedTextSMISource::~TimedTextSMISource() {
}

status_t TimedTextSMISource::start() {
    status_t err = scanFile();
    if (err != OK) {
        reset();
    }
    // TODO: Need to detect the language, because SMI doesn't give language
    // information explicitly.
    mMetaData->setCString(kKeyMediaLanguage, "");
    return err;
}

void TimedTextSMISource::reset() {
    mMetaData->clear();
    mTextVector.clear();
    mIndex = 0;
	totalLang = 0;
	selectedLangIndex = 0;
}

status_t TimedTextSMISource::stop() {
    reset();
    return OK;
}

status_t TimedTextSMISource::read(
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

sp<MetaData> TimedTextSMISource::getFormat() {
    return mMetaData;
}

status_t TimedTextSMISource::parseSMIHeadInfo(int64_t *offset){
	status_t err;
	MagicString data;
	int8_t typeSize = MagicString::sizeOfType(mFileEncodeType);
	do {
		if ((err = TimedTextUtil::readNextLine(mSource, offset, &data, mFileEncodeType)) != OK) {
			ALOGE("Reading Error Return Here!");
			return err;
		}
		data.trim();
		MagicString::print("[Get Head Line]", data);

		MagicString endHead("</HEAD>", mFileEncodeType);
		int32_t index = data.indexOf(&endHead);
		if( -1 != index ){
			MagicString::print("[Head Line End]", data);
			return OK;
		}

		MagicString fName("{Name:", mFileEncodeType); 
		index = data.indexOf(&fName);
		if( -1 == index ){
			continue;
		}

		int32_t sIndex = data.indexOf(MagicString::MAGIC_STRING_PERIOD);
		if( -1 == sIndex || sIndex > index){
			continue;
		}
		sp<MagicString> temp = data.subString(sIndex + typeSize, index - typeSize);
		mLangs[totalLang].alias = temp;
		mLangs[totalLang].alias.trim();
		MagicString::print("[Find Lang][Alias Name]", mLangs[totalLang].alias);

		sIndex = data.indexOf(MagicString::MAGIC_STRING_DOUBLE_QUOTE, index);
		if( -1 == sIndex ){
			continue;
		}
		index = data.indexOf(MagicString::MAGIC_STRING_DOUBLE_QUOTE, sIndex + typeSize);
		if( -1 == sIndex || sIndex > index){
			continue;
		}
		temp = data.subString(sIndex + typeSize, index - typeSize);
		mLangs[totalLang].fullName = temp;
		mLangs[totalLang].fullName.trim();
		MagicString::print("[Find Lang][Full Name]", mLangs[totalLang].fullName);

		MagicString lang("lang:", mFileEncodeType); 
		sIndex = data.indexOf(&lang, index);
		if( -1 == sIndex ){
			continue;
		}
		index = data.indexOf(MagicString::MAGIC_STRING_SEMICOLON, sIndex);

		temp = data.subString(sIndex + lang.length(), index - typeSize);
		mLangs[totalLang].shortName = temp;
		mLangs[totalLang].shortName.trim();
		MagicString::print("[Find Lang][Short Name]", mLangs[totalLang].shortName);	

		ALOGE("Parser Lang[%d] Successfully", totalLang);
		totalLang++;
	}while(true);
	return OK;
}

status_t TimedTextSMISource::scanFile() {
    off64_t offset = 0;
    int64_t startTimeUs =0;	 
    bool endOfFile = false;
	status_t err;
	
	mFileEncodeType = TimedTextUtil::getFileEncodeType(mSource, &offset);	//*offset must be zero
	ALOGE("[SMI Parser] Scan mFileEncodeType = %d", mFileEncodeType);

	err = parseSMIHeadInfo(&offset);

	ALOGE("[SMI Parser] parseSMIHeadInfo Successfully!");

	//skip info until found the first "<SYNC"
	MagicString data("", mFileEncodeType);
	MagicString sync("<SYNC", ENCODE_TYPE_NORMAL);
	MagicString tableEnd("</TABLE>", ENCODE_TYPE_NORMAL);
	MagicString bodyEnd("</BODY>", ENCODE_TYPE_NORMAL);
	SMITextInfo info;
	int64_t nextSTimeUs =0;	
	SMITextInfo temp;
	int8_t curLangIndex =0;
	int64_t lineStartPos = 0;
	while(true){
		lineStartPos = offset;
		if ((err = TimedTextUtil::readNextLine(mSource, &offset, &data, mFileEncodeType)) != OK) {
			ALOGE("[SMI Parser]Can not found the first \"<SYNC\"! (EOS)");
			return ERROR_MALFORMED;
		}
		if(data.startWith(&sync)){
			parseSubInfo(data, lineStartPos, &startTimeUs, &info, &curLangIndex);
			ALOGE("[SMI Parser]Had found the first \"<SYNC\"!  Begin to parse! sTime=%lld, eTime=%lld", startTimeUs, info.endTimeUs);
			break;
		}
	}

	
    while (!endOfFile) {  	
		lineStartPos = offset;
		if((err = TimedTextUtil::readNextLine(mSource, &offset, &data, mFileEncodeType)) == OK){
			if(data.startWith(&sync)){
				parseSubInfo(data, lineStartPos, &nextSTimeUs, &temp, &curLangIndex);
				if(-1 == info.endTimeUs){
					info.endTimeUs = nextSTimeUs;
				}
				info.subInfo[curLangIndex].textLen = lineStartPos - info.subInfo[curLangIndex].offset;
			}else{
				if(!data.startWith(&tableEnd) && !data.startWith(&bodyEnd)){
					parseSubInfo(data, lineStartPos, &nextSTimeUs, &info, &curLangIndex);
					continue;
				}else{
					//ALOGE("[SMI Parser] Complete Final Subtitle: startTime:%lld, endTime:%lld, offset=%lld, len=%lld, off2:%lld, len2=%lld", startTimeUs, info.endTimeUs,
					//info.subInfo[0].offset, info.subInfo[0].textLen, info.subInfo[1].offset, info.subInfo[1].textLen);
                	mTextVector.add(startTimeUs, info);
					break;	//parsing over because it meets "</TABLE>" or "</BODY>"
				}
			}
		}
        switch (err) {
            case OK:
				//ALOGE("[SMI Parser] Complete One Subtitle: startTime:%lld, endTime:%lld, offset=%lld, len=%lld, off2:%lld, len2=%lld", startTimeUs, info.endTimeUs,
				//	info.subInfo[0].offset, info.subInfo[0].textLen, info.subInfo[1].offset, info.subInfo[1].textLen);
                mTextVector.add(startTimeUs, info);
				#ifdef SELF_TEST
					ALOGE("SMI startTime:%lld, endTime:%lld", startTimeUs, info.endTimeUs);
					for(int i=0;i<totalLang;i++){
						uint64_t len = info.subInfo[i].textLen; 
						ALOGE("SMI index:%d, offset:%lld, len:%lld",info.subInfo[i].offset, len);
						char * subtitle = new char[len + 1];
						mSource->readAt(info.subInfo[i].offset, subtitle, len);
						subtitle[len] = '\0';
						ALOGE("index:%d, offset=%lld, len=%lld, subtitle: %s", i,info.subInfo[i].offset, len, subtitle);
					}
				#endif 
				startTimeUs = nextSTimeUs;
				info = temp;
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
	ALOGE("[SMI Parser] SMI Subtitle File Successfully Parse Over!");
    return OK;
}

int8_t TimedTextSMISource::getLangIndex(sp<MagicString> lang){
	if(0 == totalLang){
		mLangs[0].alias = lang;
		selectedLangIndex = 0;
		totalLang ++;
		return 0;
	}
	for(int i=0;(i<totalLang && i<MAX_SUPPORTED_MUTI_LANG_NUM);i++){
		if(lang->equal(mLangs[i].alias)){
			return i;
		}
	}
	return -1;
}


status_t TimedTextSMISource::setLang(const char* langName, int8_t nameSize){
	if(NULL == langName || nameSize == 0){
		selectedLangIndex = 0; 		//use default lang
	}
	
	for(int i=0;(i<totalLang && i<MAX_SUPPORTED_MUTI_LANG_NUM);i++){
		if( nameSize == mLangs[i].alias.length() &&
			(0 == memcmp(langName, mLangs[i].alias.c_str(), nameSize))){
			selectedLangIndex = i;
			return OK;
		}

		if( nameSize == mLangs[i].fullName.length() &&
			(0 == memcmp(langName, mLangs[i].fullName.c_str(), nameSize))){
			selectedLangIndex = i;
			return OK;
		}

		if( nameSize == mLangs[i].shortName.length() &&
			(0 == memcmp(langName, mLangs[i].shortName.c_str(), nameSize))){
			selectedLangIndex = i;
			return OK;
		}
	}

	return OK;
}


/*  SMI format(Sample File):
 *   
 * <SAMI>
 * <HEAD>
 * <TITLE>Chevaliers - SAMI Subtitle</TITLE>
 * <SAMIParam><!--
 * Length: 60300-->
 * </SAMIParam>
 * <STYLE TYPE="text/css"><!--
 * P {margin-left: 29pt; margin-right: 29pt; font-size: 14pt;
 * text-align: center; font-family: tahoma, arial, sans-serif;
 * font-weight: bold; color: white; background-color: black;}
 * TABLE {Width: "248pt";}
 * .ENCC {Name: "English"; lang: en;}
 * .FRCC {Name: "French(Standard)"; lang: fr;}
 * #Normal {Name: "Normal";}-->
 * </STYLE>
 * </HEAD>
 * <BODY><TABLE>
 * <SYNC Start=13>
 * <P Class=ENCC>4items subtitle test.
 * <BR>1-20seconds SMI subtitle test.
 * <P Class=FRCC>4items subtitle test.
 * <BR>1-20seconds SMI subtitle test.
 * <SYNC Start=20124>
 * <P Class=ENCC>&nbsp;
 * <P Class=FRCC>&nbsp;
 * <SYNC Start=20649>
 * <P Class=ENCC>20-40seconds SMI subtitle test. 
 * <P Class=FRCC>20-40seconds SMI subtitle test. 
 * <SYNC Start=40358>
 * <P Class=ENCC>&nbsp;
 * <P Class=FRCC>&nbsp;
 * </TABLE></BODY>
 * </SAMI>
 */
void TimedTextSMISource::parseSubInfo(
          MagicString &data, off64_t lineStartPos, int64_t *startTimeUs, SMITextInfo *info, int8_t *curLangIndex) {
	int8_t typeSize = MagicString::sizeOfType(data.getType());
	int64_t dealPos = 0;
	int64_t pOffset = 0;
	int8_t preLang = 0;
	//MagicString::print("[Get Line]", data);

	//Parse startFrame, endFrame & subtitle information
	MagicString sync("<SYNC", ENCODE_TYPE_NORMAL);
	int64_t index = data.indexOf(&sync);
	if(-1 != index){	//skip invalid line
		//parsing  "<SYNC Start=20649>"  or "<SYNC Start=1111 End=2222>"
		MagicString start("Start=", ENCODE_TYPE_NORMAL);
		index = data.indexOf(&start, index);
		if( -1 == index ){
			return;
		}

		MagicString end("End=", ENCODE_TYPE_NORMAL);
		int32_t endIndex = data.indexOf(&end, index);
		if(-1 == endIndex){
			endIndex = data.indexOf(MagicString::MAGIC_STRING_ANTI_ANGLE_BRACKETS, index);
			if( -1 == endIndex){
				return;
			}
			sp<MagicString> tempTime = data.subString(index + start.length() * typeSize, endIndex - typeSize); 
			*startTimeUs = MagicString::getIntValue(tempTime) * 1000ll;  //getIntValue is ***ms. Should convert it to ***us
			info->endTimeUs = 0x0FFFFFFFFFFFFFFF;
			//ALOGE("[SMI Parser] getStartTimeUs =%lld, getEndTimeUs=%lld", *startTimeUs, info->endTimeUs);
		}else{
			sp<MagicString> sTime = data.subString(index + start.length() * typeSize, endIndex - typeSize); 
			sTime->trim();
			*startTimeUs = MagicString::getIntValue(sTime) * 1000ll;  //getIntValue is ***ms. Should convert it to ***us

		    index = endIndex + end.length() * typeSize;
			endIndex = data.indexOf(MagicString::MAGIC_STRING_ANTI_ANGLE_BRACKETS, index);
			if( -1 == endIndex){
				return;
			}
			
			sp<MagicString> eTime = data.subString(index, endIndex - typeSize); 
			eTime->trim();
			info->endTimeUs = MagicString::getIntValue(eTime) * 1000ll;  //getIntValue is ***ms. Should convert it to ***us
			//ALOGE("[SMI Parser] getStartTimeUs =%lld, getEndTimeUs=%lld", *startTimeUs, info->endTimeUs);
		}

		dealPos = endIndex;
	}


	MagicString pMark("<P", ENCODE_TYPE_NORMAL);
	index = data.indexOf(&pMark, dealPos);
	if(-1 == index){
		return;
	}
	
	pOffset = index;
	
	MagicString clsMark("Class=", ENCODE_TYPE_NORMAL);
	int32_t sIndex = data.indexOf(&clsMark, index);
	if( -1 == index ){
		return;
	}else{	
		index = data.indexOf(MagicString::MAGIC_STRING_ANTI_ANGLE_BRACKETS, sIndex);
		//ALOGE("Get index=%lld, sIndex=%lld", index, sIndex);
		if( -1 == index ){
			return;
		}else{
			sp<MagicString> temp = data.subString(sIndex+ clsMark.length() * typeSize, index - typeSize);

			preLang = *curLangIndex;
			*curLangIndex = getLangIndex(temp);


			if((-1 != preLang) && (preLang != *curLangIndex)){
				info->subInfo[preLang].textLen = lineStartPos + pOffset - info->subInfo[preLang].offset; 
			}
			
			info->subInfo[*curLangIndex].offset = lineStartPos + index + typeSize;
			info->subInfo[*curLangIndex].textLen = data.length() - index - typeSize; 
			//ALOGE("Parsing Subtitle index:%d, offset=%lld, len=%lld",  *curLangIndex, info->subInfo[*curLangIndex].offset, info->subInfo[*curLangIndex].textLen);
		}
	}
}

status_t TimedTextSMISource::getText(
				const MediaSource::ReadOptions *options,
				MagicString *text, int64_t *startTimeUs, int64_t *endTimeUs){
	return getText(options, text, startTimeUs, endTimeUs, selectedLangIndex);
}


status_t TimedTextSMISource::getText(
				const MediaSource::ReadOptions *options,
				MagicString *text, int64_t *startTimeUs, int64_t *endTimeUs, int8_t selectedLangIndex){
	//ALOGE("[SMI Parser] Application try to read subtitle info sLangIndex=%d", selectedLangIndex);
	if (mTextVector.size() == 0) {
        return ERROR_END_OF_STREAM;
    }


	if( selectedLangIndex >= totalLang ){
		return ERROR_OUT_OF_RANGE;
	}

	if( NULL == text){
		return ERROR_MALFORMED;
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

    const SMITextInfo &info = mTextVector.valueAt(mIndex);
    *startTimeUs = mTextVector.keyAt(mIndex);
    *endTimeUs = info.endTimeUs;
	ALOGE("[SMI GetText] Information sTime=%lld, endTime=%lld, index=%d, sLangIndex=%d", *startTimeUs, *endTimeUs, mIndex, selectedLangIndex);
    mIndex++;

	SMISubInfo subInfo = info.subInfo[selectedLangIndex];
    char *str = new char[subInfo.textLen];
	//ALOGE("[SMI GetText] subOffset=%lld, subLen=%lld", subInfo.offset, subInfo.textLen);
    if (FileCacheManager::getInstance().readFromCache(mSource, subInfo.offset, str, subInfo.textLen) < subInfo.textLen) {
        delete[] str;
        return ERROR_IO;
    }
	text->append(str, 0, subInfo.textLen, mFileEncodeType); 
	TimedTextUtil::removeStyleInfo(text);
	MagicString::print("[GetText]", *text);
    delete[] str;
    return OK;
}

status_t TimedTextSMISource::extractAndAppendLocalDescriptions(
        int64_t timeUs, MagicString* text, Parcel *parcel) {
    const void *data = text->c_str();
    size_t size = text->length();
    int32_t flag = TextDescriptions::LOCAL_DESCRIPTIONS |
                   TextDescriptions::OUT_OF_BAND_TEXT_SMI;

    if (size > 0) {
        return TextDescriptions::getParcelOfDescriptions(
                (const uint8_t *)data, size, flag, timeUs / 1000, parcel);
    }
    return OK;
}

int TimedTextSMISource::compareExtendedRangeAndTime(size_t index, int64_t timeUs) {
    CHECK_LT(index, mTextVector.size());
    int64_t endTimeUs = mTextVector.valueAt(index).endTimeUs;
    int64_t startTimeUs = (index > 0) ?
            mTextVector.valueAt(index - 1).endTimeUs : 0;
    if (timeUs >= startTimeUs && timeUs < endTimeUs) {
        return 0;
    } else if (endTimeUs <= timeUs) {
        return -1;
    } else {
        return 1;
    }
}

}  // namespace android

#endif

