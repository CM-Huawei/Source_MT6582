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

#ifndef TIMED_TEXT_UTIL_SOURCE_H_
#define TIMED_TEXT_UTIL_SOURCE_H_

#include <DataSource.h>
#include <utils/Compat.h>  // off64_t
#include "MagicString.h"

namespace android {

struct TextInfoUtil {
	int64_t endTimeUs;
	// The offset of the text in the original file.
	off64_t offset;
	int32_t textLen;
};

struct MultiTextInfo{
	int8_t langIndex;
	int64_t endTimeUs;
	// The offset of the text in the original file.
	off64_t offset;
	int32_t textLen;
};


class AString;
class DataSource;

class TimedTextUtil{
public:
	/*
	*	We can check the first three bytes of file for judgeing the encode type of file
	*	[EF][BB][BF]: indicate UTF-8
	*	[FE][FF]: 	indicate UTF-16/UCS-2, little endian
	*	[FF][FE]:	indicate UTF-16/UCS-2, big endian
	*	Otherwise: default use NORMAL
	*/
	static EncodeType getFileEncodeType(const sp<DataSource> mSource, off64_t *offset);

	/*
	*	This method will remove Font Style control relative infomation from the text
	*	text is both the input para and the output para
	*/
	static void removeStyleInfo(MagicString* text);

	/*
	*	This method can handle reading next line from the different encode type file
	*/
	static status_t readNextLine(const sp<DataSource>& mSource, off64_t *offset, MagicString* data, EncodeType type);

	//default frame rate value
	const static int32_t DEFAULT_FRAME_RATE;

	static void unitTest();
};

};
#endif

#endif

