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

#include "TimedTextUtil.h"
#include "FileCacheManager.h"

#define READ_BLOCK_SIZE 128
//extern int32_t readCount;

#define MID_NUM 2
#define LOW_NUM 15

namespace android
{

static const char replacePattern[][MID_NUM][LOW_NUM]=
{
    {"&lt;",    "<"},
    {"&gt;",    ">"},
    {"&amp;",   "&"},
    {"&nbsp;",  " "},
    {"&quot;",  "\""},
    {"<i>",     NULL},
    {"</i>",    NULL},
    {"<b>",     NULL},
    {"</b>",    NULL},
    {"<u>",     NULL},
    {"</u>",    NULL},
    {"</font>", NULL},
    {"</FONT>", NULL},
    {"<f>",     NULL},
    {"</f>",    NULL},
    {"<u>",     NULL},
    {"</u>",    NULL},
    {"|",       "\r\n"},
    {"[br]",    "\r\n"},
    {"<br>",    "\r\n"},
    {"<BR>",    "\r\n"},
    {"//",      "\r\n"},
    {"\x5C\x4E", "\r\n"},   //{"\N",        "\r\n"},
    {"\x5C\x5C\x4E", "\r\n"},   //{"\\N",   "\r\n"},
    {"\r\n\r\n","\r\n"},
    {"\\h",     " "},
};

static const char removePattern[][MID_NUM][LOW_NUM]=
{
    {"{/",      "}"},
    {"{\\",     "}"},
    {"{\\",     "}"},
    {"<font",   ">"},
    {"<FONT",   ">"},
    {"<span",   ">"},
};


static int32_t readNextCount = 0;

const int32_t TimedTextUtil::DEFAULT_FRAME_RATE = 30;
/*
*   We can check the first three bytes of file for judgeing the encode type of file
*   [EF][BB][BF]: indicate UTF-8
*   [FE][FF]:   indicate UTF-16/UCS-2, little endian
*   [FF][FE]:   indicate UTF-16/UCS-2, big endian
*   Otherwise: default use NORMAL
*/
EncodeType TimedTextUtil::getFileEncodeType(const sp<DataSource> source, off64_t *offset)
{
    if (*offset != 0)
    {
        *offset =0;
    }

    ssize_t readSize;
    char * ch = new char[3];
    if ((readSize = FileCacheManager::getInstance().readFromCache(source, *offset, ch, 3)) < 3)
    {
        return ENCODE_TYPE_NORMAL;
    }

    ALOGE("[TimedTextUtil] getEncodeType read from offset=%lld, ch=[%d,%d,%d] readSize=%d!", *offset, ch[0], ch[1], ch[2], (int32_t)readSize);
    if ((0xEF == ch[0]) && (0xBB == ch[1]) && (0xBF == ch[2]))
    {
        *offset += 3;
        return ENCODE_TYPE_UTF8;
    }
    else if ((0xFE == ch[0]) && (0xFF == ch[1]))
    {
        *offset += 2;
        return ENCODE_TYPE_UTF16_LITTLE;
    }
    else if ((0xFF == ch[0]) && (0xFE == ch[1]))
    {
        *offset += 2;
        return ENCODE_TYPE_UTF16_BIG;
    }
    free(ch);
    return ENCODE_TYPE_NORMAL;
}

void TimedTextUtil::removeStyleInfo(MagicString* text)
{
    if (NULL == text)
    {
        return;
    }
    //MagicString::print("[Previous Sub]", *text);
    int32_t removeLen = sizeof(removePattern) / ( MID_NUM * LOW_NUM );
    for (int i=0; i<removeLen; i++)
    {
        const char* begin = removePattern[i][0];
        const char* end = removePattern[i][1];
        //ALOGE("[TimedTextUtil] Try to patternRemove index=%d, begin=%s, end=%s", i, begin, end);
        text->patternRemove(begin, ENCODE_TYPE_NORMAL, end, ENCODE_TYPE_NORMAL);
    }

    int32_t replaceLen = sizeof(replacePattern) / ( MID_NUM * LOW_NUM );
    for (int i=0; i<replaceLen; i++)
    {
        const char* src = replacePattern[i][0];
        const char* dest = replacePattern[i][1];
        //ALOGE("[TimedTextUtil] Try to patternReplace index=%d, begin=%s, end=%s", i, src, dest);
        if ( (NULL != dest) && (0 != strlen(dest)))
        {
            text->replace(src, ENCODE_TYPE_NORMAL, dest, ENCODE_TYPE_NORMAL);
        }
        else
        {
            text->remove(src, ENCODE_TYPE_NORMAL);
        }
        //ALOGE("[TimedTextUtil] modified text =%s, size=%d", text->c_str(), text->length());
    }
}

status_t TimedTextUtil::readNextLine(const sp<DataSource>& mSource, off64_t *offset, MagicString* data, EncodeType type)
{
    readNextCount ++;
    //ALOGE("[TimedTextUtil] readNextLine offset=%lld, type=%d! readCount =%d", *offset, type, (int)readNextCount);
    data->clear();
    while (true)
    {
        ssize_t readSize = 0;
        char* ch = new char[READ_BLOCK_SIZE];
        if ((readSize = FileCacheManager::getInstance().readFromCache(mSource, *offset, ch, READ_BLOCK_SIZE)) < READ_BLOCK_SIZE)
        {
            //ALOGE("[TimedTextUtil] leakage read offset=%lld, type=%d, readSize=%d, dataSize=!", *offset, type, (int32_t)readSize, data->length());
            //MagicString::print("[Last Line]", *data);
            if (readSize <= 0)
            {
                if ( data->length() > 0 )  //maybe this data is the last line
                {
                    return OK;
                }
                return ERROR_END_OF_STREAM;
            }
        }
        //ALOGE("[TimedTextUtil] normal read offset=%lld, type=%d, readSize=%d!", *offset, type, (int32_t)readSize);
        sp<MagicString> line = new MagicString(ch, 0, readSize, type);
        int32_t index = line->indexOf(MagicString::MAGIC_STRING_CR);
        int32_t lineEndPos= index;
        int32_t zero = 0;
        int8_t typeSize = MagicString::sizeOfType(type);
        //ALOGE("[TimedTextUtil] Get Next Line: CR is in position(%d)!", lineEndPos);
        if (index != -1) //a line could end with CR or CR + LF
        {
            lineEndPos = index;
            index = line->indexOf(MagicString::MAGIC_STRING_LF, lineEndPos);
            if (-1 == index)
            {
                if (0 == lineEndPos)    //end of the stream
                {
                    return ERROR_END_OF_STREAM;
                }
                else
                {
                    data->append(line, zero, lineEndPos);
                    (*offset) += (lineEndPos + typeSize);
                    return OK;
                }
            }
            else
            {
                data->append(line, zero, lineEndPos);
                (*offset) += (index + typeSize);
                //ALOGE("[TimedTextUtil] Get Line=%s, lineSize=%d offset=%lld!", data->c_str(), data->length(), *offset);
                return OK;
            }
        }
        else   //a line could end with LF
        {
            index = line->indexOf(MagicString::MAGIC_STRING_LF);
            if (index < 0)  //indicate ch does not contain LF or CR
            {
                data->append(line);
                (*offset) += readSize;
                continue;
            }
            else        //indicate ch contain LF
            {
                data->append(line, 0, index);
                (*offset) += (index + typeSize);
                return OK;
            }
        }
    }
    return OK;
}


void TimedTextUtil::unitTest()
{
#ifdef SELF_TEST
    const char* src ="<font color=\"#FF0000\">For more new Episodes visit</font>\r\n<font color=\"#FFFF00\">GlowGaze.Com</font>";
    MagicString *changed = removeStyleInfo(new MagicString(src, ENCODE_TYPE_NORMAL));
    //ALOGE("[MagicString] UnitTest2 changed string:%s, size:%d", changed->c_str(), changed->length());
    free(changed);

    src = "<u>100:<font color=\"#FFss00\"00:20,>000 &amp;--&gt; 00:00:24,400</f>Altocumulus&nbsp;clouds occur between six thousand\N<br>200:00:24,600 --&gt; 00:00:27,800and &quot;twenty thousand&quot; feet above ground level.<br></u>";
    changed = removeStyleInfo(new MagicString(src, ENCODE_TYPE_NORMAL));
    //ALOGE("[MagicString] UnitTest2 changed string:%s, size:%d", changed->c_str(), changed->length());
    free(changed);
#endif
}

}

#endif

