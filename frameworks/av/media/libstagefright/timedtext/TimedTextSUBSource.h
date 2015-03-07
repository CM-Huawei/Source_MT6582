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

#ifndef TIMED_TEXT_SUB_SOURCE_H_
#define TIMED_TEXT_SUB_SOURCE_H_

#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <utils/Compat.h>  // off64_t

#include "TimedTextSource.h"


#include "TimedTextUtil.h"
#include "StructTime.h"


namespace android {

class AString;
class DataSource;
class MediaBuffer;
class Parcel;

class StructTime;
class MagicString;
class TimedTextUtil;

class TimedTextSUBSource : public TimedTextSource {
public:
	TimedTextSUBSource(const sp<DataSource>& dataSource);
	virtual status_t start();
	virtual status_t stop();
	virtual status_t read(
			int64_t *startTimeUs,
			int64_t *endTimeUs,
			Parcel *parcel,
			const MediaSource::ReadOptions *options = NULL);
	virtual sp<MetaData> getFormat();

protected:
	virtual ~TimedTextSUBSource();
	
private:
	sp<DataSource> mSource;
	sp<MetaData> mMetaData;

	EncodeType mFileEncodeType;
	int64_t mFrameRate;
	
	size_t mIndex;
	KeyedVector<int64_t, TextInfoUtil> mTextVector;

	void reset();
	status_t scanFile();
	status_t getNormalNextSubInfo(
			off64_t *offset, int64_t *startTimeUs, TextInfoUtil *info);
	status_t getSpecailNextSubInfo(
				off64_t *offset, int64_t *startTimeUs, TextInfoUtil *info);
	status_t getText(
			const MediaSource::ReadOptions *options,
			MagicString *text, int64_t *startTimeUs, int64_t *endTimeUs);
	status_t extractAndAppendLocalDescriptions(
			int64_t timeUs, MagicString *text, Parcel *parcel);

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
	// 0: [100, 200)	  ----> [0, 200)
	// 1: [300, 400)	  ----> [200, 400)
	// 2: [500, 600)	  ----> [400, 600)
	// If the 'index' parameter contains 1, this function
	// returns 0, if timeUs is in [200, 400)
	// returns -1, if timeUs >= 400,
	// returns 1, if timeUs < 200.
	int compareExtendedRangeAndTime(size_t index, int64_t timeUs);
	
	DISALLOW_EVIL_CONSTRUCTORS(TimedTextSUBSource);

};
};

#endif
#endif

