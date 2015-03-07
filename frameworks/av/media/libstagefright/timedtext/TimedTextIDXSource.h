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

#ifndef TIMED_TEXT_IDX_SOURCE_H_
#define TIMED_TEXT_IDX_SOURCE_H_

#include <sys/mman.h>
#include <fcntl.h>

#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <utils/Compat.h>  // off64_t

#include "TimedTextSource.h"
#include "TimedTextVOBSubtitleParser.h"

#include "TimedTextUtil.h"
#include "StructTime.h"
//#define MAX_SUPPORTED_MUTI_LANG_NUM 32;

namespace android
{

enum SUB_PARSE_STATE_E
{
    SUB_PARSE_DONE,
    SUB_PARSE_NEXTLOOP,
    SUB_PARSE_FAIL,
};
class AString;
class DataSource;
class MediaBuffer;
class Parcel;

class StructTime;
class MagicString;
class TimedTextUtil;

bool ahextoi(const char * cstr, int & i);

class MutiLangKey
{
public:
    int8_t langIdx;
    int64_t startTimeUs;
    MutiLangKey();
    MutiLangKey(int8_t& id, int64_t& startTimeUs);

    int operator <(const MutiLangKey& another)const;
    int operator>(const MutiLangKey& another)const;
    int operator==(const MutiLangKey& another)const;
};

class TimedTextIDXSource : public TimedTextSource
{
public:
    TimedTextIDXSource(const sp<DataSource>& dataSource);
    virtual status_t start();
    virtual status_t stop();
    virtual status_t read(
        int64_t *startTimeUs,
        int64_t *endTimeUs,
        Parcel *parcel,
        const MediaSource::ReadOptions *options = NULL);
    virtual sp<MetaData> getFormat();

    virtual bool isBufferReady();


    void selectLangIndex(int8_t index);
    int8_t getSelectedLangIndex();


    struct SUBTITLE_PACKET_DATA
    {
        void * m_pvSubtitlePacketBuffer;         //Data Buffer
        int m_iLength;                           //Buffer Length
        int m_iCurrentOffset;                    //Current Offset in Buffer
    };

    static const int MPEG2_PACKET_START_CODE_LENGTH = 14;
    static const int MPEG2_PES_START_CODE_LENGTH = 3;
    static const int MPEG2_PES_STREAM_ID_LENGTH = 1;
    static const int MPEG2_PES_SIZE_FLAG_LENGTH = 2;
    static const int MPEG2_PES_STRM_LANG_FLAG_LENGTH = 1;


    class SUBParser
    {
    public:
        
        enum SUB_PARSE_STATE_E
        {
            SUB_PARSE_DONE,
            SUB_PARSE_NEXTLOOP,
            SUB_PARSE_FAIL,
        };
    public:
        SUBParser();
        SUBParser(const char * uri);
        ~SUBParser();
        int parse(int offset);        
        SUB_PARSE_STATE_E mParse(int & i4Offset);
        unsigned int readBigEndian(char *pByte, int bytesCount);
        SUBTITLE_PACKET_DATA m_rSpData;
        int iGetFileHandle();
        int iGetStartTime();
        int iGetSubtitleWidth();
        int iGetSubtitleHeight();
        int iGetBeginTime();
        int iGetEndTime();
        void vSetVOBPalette(const VOB_SUB_PALETTE palette);
        bool fgIsBufferReady();
    private:
        FILE *mSUBFile;
        VOBSubtitleParser * m_SpParser;        
    };

protected:
    virtual ~TimedTextIDXSource();

private:

    sp<DataSource> mSource;
    //sp<DataSource> mSubSource;
    SUBParser * mSUBParser;

    VOB_SUB_PALETTE mPalette;    

#if 0    
    int mFd;
    void *mBitmapData;
#endif
    sp<MetaData> mMetaData;

    EncodeType mFileEncodeType;
    int64_t mFrameRate;
    const static int32_t DEFAULT_FRAME_RATE;
    #if 0
    int32_t mWidth;
    int32_t mHeight;
    #endif
    int8_t mSelectedLangIndex;

    size_t mIndex;
    KeyedVector<MutiLangKey, MultiTextInfo> mTextVector;
    //KeyedVector<int8_t, MagicString> mLangVector;

    void reset();
    status_t scanFile();
    status_t prepareSUBParser();
    status_t parseIDXHeadInfo(int64_t *offset);
    status_t parseResolutionPair(const char *resolution);
    status_t parsePalette(const char * palette);
    status_t getNextSubtitleInfo(
        off64_t *offset, int64_t *startTimeUs, MultiTextInfo *info);
    status_t getText(
        const MediaSource::ReadOptions *options,
        MultiTextInfo *multiTextInfo, int64_t *startTimeUs, int64_t *endTimeUs);
    status_t getText(
        const MediaSource::ReadOptions *options,
        MultiTextInfo *multiTextInfo, int64_t *startTimeUs, int64_t *endTimeUs, int8_t selectedLangIndex);
    status_t extractAndAppendLocalDescriptions(
        int64_t timeUs, Parcel *parcel);

    void adjustPreSubtitleInfo(MutiLangKey& key, MultiTextInfo info);

    void vSetVOBPalette();
    // Compares the time range of the subtitle at index to the given timeUs.
    // The time range of the subtitle to match with given timeUs is extended to
    // [endTimeUs of the previous subtitle, endTimeUs of current subtitle).
    //
    // This compare function is used to find a next subtitle when read() is
    // called with seek options. Note that timeUs within gap ranges, such as
    // [200, 300) in the below example, will be matched to the closest future
    // subtitle, [300, 400).
    //
    // For instance, assuming there are 3 subtitles in mTextVector,
    // 0: [100, 200)      ----> [0, 200)
    // 1: [300, 400)      ----> [200, 400)
    // 2: [500, 600)      ----> [400, 600)
    // If the 'index' parameter contains 1, this function
    // returns 0, if timeUs is in [200, 400)
    // returns -1, if timeUs >= 400,
    // returns 1, if timeUs < 200.
    int compareExtendedRangeAndTime(size_t index, int64_t timeUs, int8_t selectedLangIndex);

    DISALLOW_EVIL_CONSTRUCTORS(TimedTextIDXSource);

};
};

#endif
#endif


