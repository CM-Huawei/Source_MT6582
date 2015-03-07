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
#define PRINT_DATA_DEBUG 0
#if PRINT_DATA_DEBUG
#define SUB_DATA_LOG ALOGE
#else
#define SUB_DATA_LOG
#endif

#define LOG_TAG "TimedTextIDXSource"
#include <utils/Log.h>

#include <binder/Parcel.h>
#include <media/stagefright/foundation/ADebug.h>  // for CHECK_xx
#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaDefs.h>  // for MEDIA_MIMETYPE_xxx
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>

#include "TimedTextIDXSource.h"
#include "TextDescriptions.h"
#include "FileCacheManager.h"


namespace android
{

const int32_t DEFAULT_FRAME_RATE = 30;
extern const MagicString* MagicString::MAGIC_STRING_WELL_MARK;

MutiLangKey::MutiLangKey(): langIdx(0), startTimeUs(0)
{
}


MutiLangKey::MutiLangKey(int8_t& id, int64_t& startTimeUs)
{
    langIdx = id;
    this->startTimeUs = startTimeUs;
}

int MutiLangKey::operator<(const MutiLangKey& another) const
{
    if (langIdx < another.langIdx)
    {
        return 1;
    }
    else if (langIdx > another.langIdx)
    {
        return 0;
    }
    else
    {
        return ((startTimeUs < another.startTimeUs) ? 1 : 0);
    }
}

int MutiLangKey::operator>(const MutiLangKey& another)const
{
    if (langIdx > another.langIdx)
    {
        return 1;
    }
    else if (langIdx < another.langIdx)
    {
        return 0;
    }
    else
    {
        return ((startTimeUs > another.startTimeUs)? 1 : 0);
    }
}

int MutiLangKey::operator==(const MutiLangKey& another)const
{
    return ((langIdx == another.langIdx) && (startTimeUs == another.startTimeUs));
}


TimedTextIDXSource::TimedTextIDXSource(const sp<DataSource>& dataSource)
    : mSource(dataSource),
      mMetaData(new MetaData),
      mIndex(0),
      mFrameRate(TimedTextUtil::DEFAULT_FRAME_RATE)
{
    mFileEncodeType = ENCODE_TYPE_NORMAL;
#ifdef SELF_TEST
    scanFile();
    int64_t startTimeUs = 0;
    int64_t endTimeUs = 0;
    Parcel parcel;
    MediaSource::ReadOptions options;
    status_t err =OK;
    int len =7;
    int st[] = {888, 11111, 22000, 18888, 38000, 54800, 76000, 88888, 188888, 3456789}; //ms
    for (int i =0; i< len; i++)
    {
        int64_t temp = st[i] * 1000ll; //us
        options.setSeekTo(temp);
        err = read(&startTimeUs, &endTimeUs, &parcel, &options);
        ALOGE("[--SELF_TEST--] seekTime=%lld, getStartTime=%lld, getEndTime=%lld, isReadSuccessfully=%d",  temp, startTimeUs, endTimeUs, err);
    }

#endif
}

TimedTextIDXSource::~TimedTextIDXSource()
{
}

status_t TimedTextIDXSource::start()
{
    status_t err = scanFile();
    if (err != OK)
    {
        reset();
        return -1;
    }

    err = prepareSUBParser();
    vSetVOBPalette();


    // TODO: Need to detect the language, because IDX doesn't give language
    // information explicitly.
    mMetaData->setCString(kKeyMediaLanguage, "");
    return err;
}

void TimedTextIDXSource::reset()
{
    mMetaData->clear();
    mTextVector.clear();
    mIndex = 0;
}

status_t TimedTextIDXSource::stop()
{
    reset();
    return OK;
}

status_t TimedTextIDXSource::read(
    int64_t *startTimeUs,
    int64_t *endTimeUs,
    Parcel *parcel,
    const MediaSource::ReadOptions *options)
{

    //MagicString text;
    MultiTextInfo multiTextInfo;
    status_t err = getText(options, &multiTextInfo, startTimeUs, endTimeUs);
    if (err != OK)
    {
        return err;
    }

    
    err = mSUBParser->parse((int)multiTextInfo.offset);
    if (-1 != mSUBParser->iGetEndTime())
    {
        *endTimeUs = *startTimeUs + mSUBParser->iGetEndTime() * 10 * 1000;
    }
    ALOGE("[MY] --- after  got text for %lld-%lld: %lld", *startTimeUs, *endTimeUs, multiTextInfo.offset);

    //CHECK_GE(*startTimeUs, 0);
    if (OK == err)
    {       
        extractAndAppendLocalDescriptions(*startTimeUs, parcel);
    }
    return OK;
}

sp<MetaData> TimedTextIDXSource::getFormat()
{
    return mMetaData;
}

status_t TimedTextIDXSource::parseIDXHeadInfo(int64_t *offset)
{
    status_t err;
    MagicString data;
    int8_t typeSize = MagicString::sizeOfType(mFileEncodeType);
    do
    {
        if ((err = TimedTextUtil::readNextLine(mSource, offset, &data, mFileEncodeType)) != OK)
        {
            ALOGE("Reading Error Return Here!");
            return err;
        }
        data.trim();
        MagicString::print("[Get Head Line]", data);

        if (data.startWith(MagicString::MAGIC_STRING_WELL_MARK))
        {
            continue;
        }

        // parse width*height
        // sample "size: 720x576"
        MagicString resolution("size:", mFileEncodeType);
        int32_t index = data.indexOf(&resolution);
        if ( -1 != index )
        {
            data.trim();
            ALOGE("TimedTextIDXSource.parseIDXHeadInfo, reached resolution: %s", data.c_str());
            // remove the heading "size: " (including a space at the end)
            sp<MagicString> pResolution = data.subString(6);
            if ( OK != parseResolutionPair(pResolution->c_str()))
            {
                return UNKNOWN_ERROR;
            }
            continue;
        }

        MagicString palette("palette:", mFileEncodeType);
        index = data.indexOf(&palette);
        if ( -1 != index )
        {
            data.trim();
            ALOGE("TimedTextIDXSource.parseIDXHeadInfo, reached palette: %s", data.c_str());
            // remove the heading "size: " (including a space at the end)
            sp<MagicString> pPalette = data.subString(0);
            if ( OK != parsePalette(pPalette->c_str()))
            {
                return UNKNOWN_ERROR;
            }
            continue;
        }

        MagicString endHead("langidx:", mFileEncodeType);
        index = data.indexOf(&endHead);
        if ( -1 != index )
        {
            MagicString::print("[Head Line End]", data);
            return OK;
        }
    }
    while (true);
    return OK;
}

status_t TimedTextIDXSource::parseResolutionPair(const char *resolution)
{
    // sample: "720x576"
#if 0
    char pWidth[5], pHeight[5];
    char *pX = strchr(resolution, 'x');
    if ( NULL == pX )
    {
#if 0
        mWidth = -1;
        mHeight = -1;
        ALOGE("TimedTextIDXSource.parseResolutionPair, FATAL ERROR! could not parse width or height");
        return UNKNOWN_ERROR;
#endif
    }

    // copy width string
    int widthStrLen = pX - resolution;
    strncpy(pWidth, resolution, widthStrLen);
    *(pWidth + widthStrLen) = '\0';

    // copy height string
    strcpy(pHeight, (pX + 1));
    //ALOGE("[MY] --- str width = %s, height = %s", pWidth, pHeight);

    // transform to int
    mWidth = atoi(pWidth);
    mHeight = atoi(pHeight);
    //ALOGE("[MY] --- int width = %d, height = %d", mWidth, mHeight);
#endif
    return OK;
}


status_t TimedTextIDXSource::parsePalette(const char * palette)
{
    char acColor[7];         //6 for color content, 1 for '\0'
    int i = 0;
    char * temp = (char *)palette;
    temp += 9;               //skip "palette:"
    int idex = 0;
    ALOGE("temp is %s\n", temp);
    while (*temp != '\0')
    {
        if (' ' == *temp|| ',' == *temp)
        {
        }
        else
        {
            acColor[i] = *temp;
            
            if (i == 5)
            {
                acColor[6] = '\0';
                ahextoi(acColor, mPalette[idex]);
                ALOGE("mPalette[%d] is 0x%x", idex, mPalette[idex]);
                idex ++;
                if (idex == VOB_PALETTE_SIZE)
                {
                    return OK;
                }
                i = 0;
            }
            else
            {
                i ++;
            }
        }
        temp ++;
    }
    ALOGE("out of while loop");
    return OK;
}
void TimedTextIDXSource::selectLangIndex(int8_t index)
{
    if (index >= 0)
    {
        mSelectedLangIndex = index;
    }
}

int8_t TimedTextIDXSource::getSelectedLangIndex()
{
    return mSelectedLangIndex;
}

void TimedTextIDXSource::adjustPreSubtitleInfo(MutiLangKey& key, MultiTextInfo info)
{
    int32_t vSize = mTextVector.size();
    if ( 0 == vSize ) //Will use the first lang as default one.
    {
        selectLangIndex(key.langIdx);
    }
    if ( 1 <= vSize )
    {
        MultiTextInfo temp = mTextVector.valueAt(vSize -1);
        if (info.langIndex == temp.langIndex)
        {
            temp.endTimeUs = key.startTimeUs;
            //use textLen to record data length in sub file
            //temp.textLen = (int)(info.offset - temp.offset);
            //ALOGE("[MY] --- adjust data len = %d", temp.textLen);
            mTextVector.add(mTextVector.keyAt(vSize -1), temp);
            //ALOGE("[IDX Parser] Adjust item %d, sTime=%lld, eTime=%lld, offset=%lld, len=%d,", (vSize - 1), mTextVector.keyAt(vSize -1).startTimeUs
            //temp.endTimeUs, temp.offset, temp.textLen);
        }
    }
}

void TimedTextIDXSource::vSetVOBPalette()
{
    mSUBParser->vSetVOBPalette(mPalette);
}

status_t TimedTextIDXSource::scanFile()
{
    off64_t offset = 0;
    int64_t startTimeUs =0;
    bool endOfFile = false;
    status_t err;

    mFileEncodeType = TimedTextUtil::getFileEncodeType(mSource, &offset);   //*offset must be zero
    ALOGE("[IDX Parser] Scan mFileEncodeType = %d", mFileEncodeType);

    err = parseIDXHeadInfo(&offset);

    ALOGE("[IDX Parser] parseIDXHeadInfo Successfully!");
    while (!endOfFile)
    {
        MultiTextInfo info;
        //int32_t vSize =0;

        err = getNextSubtitleInfo(&offset, &startTimeUs, &info);
        MutiLangKey key(info.langIndex, startTimeUs);
        switch (err)
        {
        case OK:
            adjustPreSubtitleInfo(key, info);
            /*  vSize = mTextVector.size() ;
                if( vSize > 0 && vSize < 2000){
                    MutiLangKey preKey = mTextVector.keyAt(vSize -1);
                    MultiTextInfo preInfo = mTextVector.valueAt(vSize -1);
                    ALOGE("PreInfo(index:%d,[%lld,%lld,%d,%lld]), Current(index:%d, [%lld,%lld,%d,%lld])",
                        vSize-1, preKey.startTimeUs, preInfo.endTimeUs, preInfo.langIndex, preInfo.offset,
                        vSize, key.startTimeUs, info.endTimeUs, info.langIndex, info.offset);
                }*/
            mTextVector.add(key, info);
            //ALOGE("[MY] --- scan file, info offset = 0x%x", info.offset);
            break;
        case ERROR_END_OF_STREAM:
            endOfFile = true;
            break;
        default:
            return err;
        }
    }
    if (mTextVector.isEmpty())
    {
        return ERROR_MALFORMED;
    }
    return OK;
}


//Sample file is as below:
//# Custom colors (transp idxs and the four colors)
//custom colors: OFF, tridx: 0001, colors: 030000, 000628, 030000, 07e2c0
//
//# Language index in use
//langidx: 0
//
//# Chinese
//id: zh, index: 1
//# Decomment next line to activate alternative name in DirectVobSub / Windows Media Player 6.x
//# alt: Chinese
//# Vob/Cell ID: 1, 2 (PTS: 1154600)
//timestamp: 00:00:02:680, filepos: 000000000
//timestamp: 00:00:11:680, filepos: 000002000

status_t TimedTextIDXSource::getNextSubtitleInfo(
    off64_t *offset, int64_t *startTimeUs, MultiTextInfo *info)
{
    MagicString data("", mFileEncodeType);
    status_t err;
    int64_t lineBeginIndex = 0;
    int8_t typeSize = MagicString::sizeOfType(mFileEncodeType);
    int8_t langIndex = 0;
    int32_t dataLen = 0;
    int32_t startIndex =0;

    // To skip blank lines.
    do
    {
        lineBeginIndex = *offset;
        if ((err = TimedTextUtil::readNextLine(mSource, offset, &data, mFileEncodeType)) != OK)
        {
            ALOGE("[IDX Parser]Reading Over Return Here! (EOS)");
            return err;
        }
        dataLen = data.length();
        data.trim();
        MagicString::print("[Get Line]", data);

        if (data.empty() || data.startWith(MagicString::MAGIC_STRING_WELL_MARK))    //To skip blank lines
        {
            continue;
        }
        else
        {
            MagicString langId("id:", ENCODE_TYPE_NORMAL);
            int64_t index = data.indexOf(&langId);
            if (-1 == index)    //parse timestamp: 00:00:02:680, filepos: 000000000
            {
                MagicString timeStamp("timestamp:", ENCODE_TYPE_NORMAL);
                index = data.indexOf(&timeStamp);
                if ( -1 == index )
                {
                    continue;
                }
                else
                {
                    startIndex = index;
                    index = data.indexOf(MagicString::MAGIC_STRING_COMMA, index);
                    if ( -1 == index )
                    {
                        continue;
                    }
                    sp<MagicString> sTimeMStr = data.subString(startIndex + timeStamp.length() * typeSize + typeSize, index - typeSize);
                    sTimeMStr->trim();
                    StructTime sTime(sTimeMStr);
                    *startTimeUs = sTime.calcTimeUs();
                    info->endTimeUs = 0x0FFFFFFFFFFFFFFF;   //will be fixed by right value when getting next subtitle infomation

                    MagicString pos("filepos:", ENCODE_TYPE_NORMAL);
                    startIndex = index;
                    index = data.indexOf(&pos, index);
                    if ( -1 == index )
                    {
                        continue;
                    }
                    sp<MagicString> sOffset = data.subString(index + pos.length() * typeSize + typeSize);
                    sOffset->trim();
                    info->offset = MagicString::getIntValue(sOffset, 16);
                    info->textLen = 0;
                    //ALOGE("Parsing Subtitle index:%d, offset=%lld, len=%d, sTime=%lld, eTime=%lld, offset=%lld",
                    //  info->langIndex, info->offset, info->textLen, *startTimeUs, info->endTimeUs, info->offset);
                    return OK;
                }
            }
            else            //Parse id: zh, index: 1
            {
                startIndex = index;
                index = data.indexOf(MagicString::MAGIC_STRING_COMMA, index);
                if ( -1 == index )
                {
                    continue;
                }
                sp<MagicString> langName = data.subString(startIndex + langId.length()* typeSize + typeSize, index - typeSize);
                langName->trim();
                MagicString::print("[Lang Name]", langName);

                startIndex = index;
                MagicString indexMStr("index:", ENCODE_TYPE_NORMAL);
                index = data.indexOf(MagicString::MAGIC_STRING_COMMA, index);
                if ( -1 == index)
                {
                    continue;
                }
                sp<MagicString> langIndexMStr = data.subString(index + indexMStr.length() * typeSize + typeSize);
                langIndexMStr->trim();
                int8_t langIndex = MagicString::getIntValue(langIndexMStr);
                info->langIndex = langIndex;
                ALOGE("Get Lang Description. LangName:%s, LangIndex:%d", langName->c_str(), langIndex);
                //mLangVector.add(langIndex, langName);
            }
        }
    }
    while (true);
    return OK;
}

status_t TimedTextIDXSource::prepareSUBParser()
{
    String8 uri = mSource->getUri();
    const char *pCharUri;
    if (!uri.isEmpty())
    {
        pCharUri = uri;
        ALOGE("[MY] --- uri 1 = %s", pCharUri);
        char *p = strrchr(pCharUri, '.');
        char *pSub = "sub";
        for (int i = 0; i < 3; ++i)
        {
            *(++p) = *(pSub + i);
        }
        ALOGE("[MY] --- uri 2 = %s", pCharUri);
    }
    else
    {
        //ALOGE("[MY] --- uri is empty, maybe created from fd");
    }

    mSUBParser = new SUBParser(pCharUri);

    return OK;
}



#if 0
status_t TimedTextIDXSource::getText(
    const MediaSource::ReadOptions *options,
    MagicString *text, int64_t *startTimeUs, int64_t *endTimeUs)
{
    return getText(options, text, startTimeUs, endTimeUs, mSelectedLangIndex);
}


status_t TimedTextIDXSource::getText(
    const MediaSource::ReadOptions *options,
    MagicString *text, int64_t *startTimeUs, int64_t *endTimeUs, int8_t selectedLangIndex)
{
    if (mTextVector.size() == 0)
    {
        return ERROR_END_OF_STREAM;
    }


    text->clear();
    int64_t seekTimeUs;
    MediaSource::ReadOptions::SeekMode mode;
    if (options != NULL && options->getSeekTo(&seekTimeUs, &mode))
    {
        int64_t lastEndTimeUs =
            mTextVector.valueAt(mTextVector.size() - 1).endTimeUs;
        if (seekTimeUs < 0)
        {
            return ERROR_OUT_OF_RANGE;
        }
        else if (seekTimeUs >= lastEndTimeUs)
        {
            return ERROR_END_OF_STREAM;
        }
        else
        {
            mIndex = 0;  //should reset the mIndex value, otherwise there will be some problem in Muti-Lang case
            // binary search
            size_t low = 0;
            size_t high = mTextVector.size() - 1;
            size_t mid = 0;

            while (low <= high)
            {
                mid = low + (high - low)/2;
                int diff = compareExtendedRangeAndTime(mid, seekTimeUs, selectedLangIndex);
                if (diff == 0)
                {
                    break;
                }
                else if (diff < 0)
                {
                    low = mid + 1;
                }
                else
                {
                    high = mid - 1;
                }
            }
            mIndex = mid;
        }
    }

    if (mIndex >= mTextVector.size())
    {
        return ERROR_END_OF_STREAM;
    }

    const MultiTextInfo &info = mTextVector.valueAt(mIndex);
    *startTimeUs = mTextVector.keyAt(mIndex).startTimeUs;
    *endTimeUs = info.endTimeUs;
    ALOGE("[IDX GetText] Information sTime=%lld, endTime=%lld, langIndex=%d, index=%d, offset=%lld, selectedIndex = %d",
          *startTimeUs, *endTimeUs, info.langIndex, mIndex, info.offset, selectedLangIndex);
    mIndex++;

//TODO: will get the real data from .sub file according to the relative information
    /*  char *str = new char[info.textLen];
      if (FileCacheManager::getInstance().readFromCache(mSource, info.offset, str, info.textLen) < info.textLen) {
          delete[] str;
          return ERROR_IO;
      }
      text->append(str, 0, info.textLen, mFileEncodeType);
    MagicString::print("[GetText]", *text);
      delete[] str;*/
    return OK;
}
#else
status_t TimedTextIDXSource::getText(
    const MediaSource::ReadOptions *options,
    MultiTextInfo *multiTextInfo, int64_t *startTimeUs, int64_t *endTimeUs)
{
    return getText(options, multiTextInfo, startTimeUs, endTimeUs, mSelectedLangIndex);
}


status_t TimedTextIDXSource::getText(
    const MediaSource::ReadOptions *options,
    MultiTextInfo *multiTextInfo, int64_t *startTimeUs, int64_t *endTimeUs, int8_t selectedLangIndex)
{
    if (mTextVector.size() == 0)
    {
        return ERROR_END_OF_STREAM;
    }


    //text->clear();
    int64_t seekTimeUs;
    MediaSource::ReadOptions::SeekMode mode;
    if (options != NULL && options->getSeekTo(&seekTimeUs, &mode))
    {
        //ALOGE("[MY] --- seek time is %d", seekTimeUs);
        int64_t lastEndTimeUs =
            mTextVector.valueAt(mTextVector.size() - 1).endTimeUs;
        if (seekTimeUs < 0)
        {
            return ERROR_OUT_OF_RANGE;
        }
        else if (seekTimeUs >= lastEndTimeUs)
        {
            return ERROR_END_OF_STREAM;
        }
        else
        {
            mIndex = 0;  //should reset the mIndex value, otherwise there will be some problem in Muti-Lang case
            // binary search
            size_t low = 0;
            size_t high = mTextVector.size() - 1;
            size_t mid = 0;

            while (low <= high)
            {
                mid = low + (high - low)/2;
                int diff = compareExtendedRangeAndTime(mid, seekTimeUs, selectedLangIndex);
                if (diff == 0)
                {
                    break;
                }
                else if (diff < 0)
                {
                    low = mid + 1;
                }
                else
                {
                    high = mid - 1;
                }
            }
            mIndex = mid;
        }
    }

    if (mIndex >= mTextVector.size())
    {
        return ERROR_END_OF_STREAM;
    }

    //const MultiTextInfo &info = mTextVector.valueAt(mIndex);
    *multiTextInfo = mTextVector.valueAt(mIndex);
    *startTimeUs = mTextVector.keyAt(mIndex).startTimeUs;
    *endTimeUs = multiTextInfo->endTimeUs;
    ALOGE("[IDX GetText] Information sTime=%lld, endTime=%lld, langIndex=%d, index=%d, offset=%lld, dataLength = %d, selectedIndex = %d",
          *startTimeUs, *endTimeUs, multiTextInfo->langIndex, mIndex, multiTextInfo->offset, multiTextInfo->textLen, selectedLangIndex);
    mIndex++;


//TODO: will get the real data from .sub file according to the relative information
    /*  char *str = new char[info.textLen];
      if (FileCacheManager::getInstance().readFromCache(mSource, info.offset, str, info.textLen) < info.textLen) {
          delete[] str;
          return ERROR_IO;
      }
      text->append(str, 0, info.textLen, mFileEncodeType);
    MagicString::print("[GetText]", *text);
      delete[] str;*/
    return OK;
}

#endif

status_t TimedTextIDXSource::extractAndAppendLocalDescriptions(
    int64_t timeUs, Parcel *parcel)
{
    //const void *data = mBitmapData;
    int32_t fd = mSUBParser->iGetFileHandle();
    int32_t width = mSUBParser->iGetSubtitleWidth();
    int32_t height = mSUBParser->iGetSubtitleHeight();
    int32_t flag = TextDescriptions::LOCAL_DESCRIPTIONS |
                   TextDescriptions::OUT_OF_BAND_TEXT_IDX;

    if (fd > 0 && width > 0 && height > 0)
    {
        return TextDescriptions::getParcelOfDescriptions(
                   fd, width, height, flag, 1 + timeUs / 1000, parcel);
    }
    return -1;
}

int TimedTextIDXSource::compareExtendedRangeAndTime(size_t index, int64_t timeUs, int8_t selectedLangIndex)
{
    CHECK_LT(index, mTextVector.size());
    int64_t endTimeUs = mTextVector.valueAt(index).endTimeUs;
    int64_t startTimeUs = (index > 0) ?
                          mTextVector.valueAt(index - 1).endTimeUs : 0;
    int8_t infoIndex = mTextVector.valueAt(index).langIndex;
    //ALOGE("[IDX Parser] compare found info-index:%d langIndex=%d, sIndex=%d, sTimesUs=%lld, eTimeUs=%lld, timeUs=%lld",
    //  index , infoIndex, selectedLangIndex, startTimeUs, endTimeUs, timeUs);
    if ( infoIndex < selectedLangIndex)
    {
        return -1;
    }
    else if ( infoIndex > selectedLangIndex )
    {
        return 1;
    }
    if (timeUs >= startTimeUs && timeUs < endTimeUs)
    {
        return 0;
    }
    else if (endTimeUs <= timeUs)
    {
        return -1;
    }
    else
    {
        return 1;
    }
}


bool TimedTextIDXSource::isBufferReady()
{
    if(mSUBParser == NULL)
    {
        return true;
    }
    
    return mSUBParser->fgIsBufferReady();
}


bool ahextoi(const char * cstr, int & i)
{
    bool result = true;
    i = 0;
    int num = 0;
    while (*cstr != '\0')
    {
        char c = *cstr;
        if (c >= 'a' && c <= 'f')
            num = 10 + c - 'a';
        else if (c >= 'A' && c <= 'F')
            num = 10 + c - 'A';
        else if (c >= '0' && c <= '9')
            num = c - '0';
        else
        {
            result = false;
            break;
        }
        cstr ++;
        i *= 16;
        i += num;
        printf("i is %x\n", i);
    }

    return result;

}


TimedTextIDXSource::SUBParser::
SUBParser()
{
    //mSUBFile = fopen("1.sub", "r");
}

TimedTextIDXSource::SUBParser::
SUBParser(const char * uri)
{
    if (NULL == uri)
    {
        return;
    }
    mSUBFile= fopen(uri, "r");
    char * ptemp = (char *)malloc(20);
    fread(ptemp, 1, 20, mSUBFile); 

    m_SpParser = new VOBSubtitleParser();
    m_SpParser->stPrepareBitmapBuffer();
}

TimedTextIDXSource::SUBParser::
~SUBParser()
{
    ALOGE("mSUBFile CLosed!");
    fclose(mSUBFile);
    m_SpParser->stUnmapBitmapBuffer();
    delete m_SpParser;
}

status_t TimedTextIDXSource::SUBParser::
parse(int offset)
{
    ALOGE("[RY] parse from offset %d", offset);
    //1. generate VOB Subtitle Data Packet
    SUB_PARSE_STATE_E eState;
    int i = 0;
    status_t err = OK;

    do
    {
        eState = mParse(offset);
        i ++;
    }

    while (eState == SUB_PARSE_NEXTLOOP);

    if (SUB_PARSE_DONE != eState)
    {
        return 1;
    }

    ALOGE("Parsing Done! Length is %d", m_rSpData.m_iLength);

    //2. parsing VOB Subtitle Data Packet
    m_SpParser->stInit(m_rSpData.m_pvSubtitlePacketBuffer, m_rSpData.m_iLength);

    do
    {
        m_SpParser->m_fgParseFlag = false;
        err = m_SpParser->stParseControlPacket();

        if (err != OK)
            break;

        if (m_SpParser->m_iDataPacketSize <= 4)
            break;

        if (err != OK)
            break;


        err = m_SpParser->stParseDataPacket(NULL, 0);
    }
    while (false);

    m_SpParser->m_fgParseFlag = true;
    
    if (OK != err)
    {
        m_SpParser->vUnInit();
    }

    return err;
    //


}


int TimedTextIDXSource::SUBParser::iGetFileHandle()
{
    if (NULL != m_SpParser)
    {
        return m_SpParser->m_iFd;
    }
    else
    {
        return -1;
    }
}
int TimedTextIDXSource::SUBParser::iGetStartTime()
{
    if (NULL != m_SpParser)
    {
        return m_SpParser->m_iBeginTime;
    }
    else
    {
        return -1;
    }
}
int TimedTextIDXSource::SUBParser::iGetSubtitleWidth()
{
    if (NULL != m_SpParser)
    {
        return m_SpParser->m_iBitmapWidth;
    }\
    else
    {
        return -1;
    }
}
int TimedTextIDXSource::SUBParser::iGetSubtitleHeight()
{
    if (NULL != m_SpParser)
    {
        return m_SpParser->m_iBitmapHeight;
    }
    else
    {
        return -1;
    }
}


int TimedTextIDXSource::SUBParser::iGetBeginTime()
{
    return m_SpParser->m_iBeginTime;
}

int TimedTextIDXSource::SUBParser::iGetEndTime()
{
    return m_SpParser->m_iEndTime;
}



void TimedTextIDXSource::SUBParser::vSetVOBPalette(const VOB_SUB_PALETTE palette)
{
    m_SpParser->vSetPalette(palette);
}



TimedTextIDXSource::SUBParser::SUB_PARSE_STATE_E TimedTextIDXSource::SUBParser::
mParse(int & i4Offset)
{
    ALOGE("i4offset is %d\n", i4Offset);
    ALOGE("2 here mSUBFile is 0x%x\n", mSUBFile);
    void * pvTempBuf = NULL;
    pvTempBuf = malloc(20);
    SUB_PARSE_STATE_E eState = SUB_PARSE_FAIL;
    if (NULL == pvTempBuf)
    {
        return eState;
    }

    do
    {
        fseek(mSUBFile, i4Offset, SEEK_SET);
        /************STEP 1 Skip 14 bytes (MPEG2 Packet Start Code) directly*****************/
        // SKIP first 14 bytes as PS heading, gonna reach PES heading
        // 00 00 01 BA XX XX ... XX
        fread(pvTempBuf, 1, MPEG2_PACKET_START_CODE_LENGTH, mSUBFile);
        ALOGE("[RY] first 4 %x %x %x %x", *((char *)pvTempBuf + 1), *((char *)pvTempBuf + 2), *((char *)pvTempBuf + 3), *((char *)pvTempBuf + 4));

        /************STEP 2 Skip 4 bytes (PES Start Code 3 bytes plus PES Stream ID 1 byte)*******************/
        // PES header
        // 00 00 01 BD
        fread(pvTempBuf, 1, MPEG2_PES_START_CODE_LENGTH + MPEG2_PES_STREAM_ID_LENGTH, mSUBFile);
        //ALOGE("[MY] --- PES 4 bytes : 0x%x", *((int *)pTempBuf));


        /************STEP 3 Get PES Packet Length************************/
        // 0x5906 -> 0x0659  Little Endian -> Big Endian
        // It seems the tablet system is running on Little Endian, but the .sub file should be read on Big Endian
        fread(pvTempBuf, 1, 2, mSUBFile);
        int iPESPacketTotalLength = readBigEndian((char *)pvTempBuf, MPEG2_PES_SIZE_FLAG_LENGTH);
        ALOGE("[RY] --- iPESPacketTotalLength : 0x%x\n", iPESPacketTotalLength);


        /****NOTE****/
        /****Use the same method as BDP ****/
        /****A new PES Packet must have PTS present ****/

        /************STEP 4  Judge if current PES packet is new**********/
        // sample 0x 81 80
        fread(pvTempBuf, 1, 2, mSUBFile);
        char cFlag = *((char *)pvTempBuf + 1);
        bool fgIsNewPESPacket = 0x02 == (cFlag >> 6 & 0x03) ?
                                true : false;
        ALOGE("[RY] fgIsNewPESPacket is %d\n", fgIsNewPESPacket);

        /************STEP 5************************/
        fread(pvTempBuf, 1, 1, mSUBFile);
        char * pcRemainHeaderLength = (char *)pvTempBuf;

        unsigned short uRemainHeaderLength = (unsigned short)(* pcRemainHeaderLength);
        ALOGE("uRemainHeaderLength %d\n", uRemainHeaderLength);
        //reamin length plus 1 as PES header is followed by a PES stream id flag, occupying 1 byte
        fseek(mSUBFile, uRemainHeaderLength + 1, SEEK_CUR);

        /************STEP 6************************/
        if (fgIsNewPESPacket)
        {
            fread(pvTempBuf, 1, 2, mSUBFile);
            fseek(mSUBFile, -2, SEEK_CUR);

            int iSubtitlePacketLength = readBigEndian((char *)pvTempBuf, 2);
            ALOGE("subtitlePacketLength is %d\n", iSubtitlePacketLength);
            if (iSubtitlePacketLength > 0)
            {
                m_rSpData.m_pvSubtitlePacketBuffer = malloc(iSubtitlePacketLength);
                if (m_rSpData.m_pvSubtitlePacketBuffer != NULL)
                {
                    m_rSpData.m_iLength = (unsigned int)iSubtitlePacketLength;
                    m_rSpData.m_iCurrentOffset = 0;
                }
                else
                {
                    eState = SUB_PARSE_FAIL;
                    break;
                }

            }
            else
            {
                ALOGE("SubtitlePacket Length %d is invalid\n", iSubtitlePacketLength);
                eState = SUB_PARSE_FAIL;
                break;
            }
        }
        // Not a new one
        else
        {
            ALOGE("Not a new one, iPESPacketTotalLength is %d\n", iPESPacketTotalLength);
            ALOGE("Sp Data Length is %d, current offset is %d\n", m_rSpData.m_iLength, m_rSpData.m_iCurrentOffset);
        }

        /**********STEP 7*************************/

        int iReadSize = 0;
        bool fgIsFinished = false;

        //2, fixed part of optional PES header, 2 bytes
        //1, reamin header length flag, 1 byte
        //1, stream language flag, 1 byte
        //the calculate result is the size of subtitle data and padding data saved in current MPEG packet
        if (iPESPacketTotalLength - 2 - 1 - uRemainHeaderLength - 1 >= m_rSpData.m_iLength - m_rSpData.m_iCurrentOffset)
        {
            //we can finish filling sp data buffer
            iReadSize = m_rSpData.m_iLength - m_rSpData.m_iCurrentOffset;
            fgIsFinished = true;
            ALOGE("iReadSize is %d\n", iReadSize);
        }
        else
        {
            iReadSize = iPESPacketTotalLength - 2 - 1 - uRemainHeaderLength - 1;
            fgIsFinished = false;
            ALOGE("iReadSize is %d, 2\n", iReadSize);
        }



        char * temp = m_rSpData.m_iCurrentOffset + (char *)m_rSpData.m_pvSubtitlePacketBuffer;
        fread(temp, 1, iReadSize, mSUBFile);

        ALOGE("m_iCurrentOffset %d, temp[0] %x, temp[1] %x\n", m_rSpData.m_iCurrentOffset, *temp, *(temp + 1));
        m_rSpData.m_iCurrentOffset += iReadSize;

        if (fgIsFinished)
        {
            eState = SUB_PARSE_DONE;
        }
        else
        {
            eState = SUB_PARSE_NEXTLOOP;
        }


        i4Offset = (int)ftell(mSUBFile);

    }
    while (false);

    free(pvTempBuf);

    ALOGE("eState is %d\n", eState);
    return eState;

}

unsigned int TimedTextIDXSource::SUBParser::
readBigEndian(char *pByte, int bytesCount)
{
    char *pTemp = pByte;
    unsigned int sum = 0;
    while (bytesCount-- > 0)
    {
        sum <<= 8; // move 8 bits to left
        sum += *((unsigned char *)(pTemp));

        ++pTemp;

    }
    return sum;
}
bool TimedTextIDXSource::SUBParser::
fgIsBufferReady()
{
    return m_SpParser->fgIsBufferReady();
}

}  // namespace android

#endif

