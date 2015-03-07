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
#ifndef TIMED_TEXT_ASS_SOURCE_H_
#define TIMED_TEXT_ASS_SOURCE_H_

#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include "TimedTextSource.h"
#include <utils/Compat.h>  // off64_t

#include "MagicString.h"
#include "TimedTextUtil.h"

namespace android {

class AString;
class DataSource;
class MediaBuffer;
class Parcel;

class TimedTextASSSource : public TimedTextSource {
public:
    TimedTextASSSource(const sp<MediaSource>& mediaSource);	//For internal SP
    TimedTextASSSource(const sp<DataSource>& dataSource);		//For external SP

    status_t in_read(int64_t *startTimeUs, int64_t *endTimeUs, Parcel *parcel, const MediaSource::ReadOptions *options);
    status_t ex_read(int64_t *startTimeUs, int64_t *endTimeUs, Parcel *parcel, const MediaSource::ReadOptions *options);
    status_t in_start();
    status_t ex_start();
    status_t in_stop();
    status_t ex_stop();
    void ex_reset();
    status_t in_extractAndAppendLocalDescriptions(int64_t timeUs, const MediaBuffer *textBuffer, Parcel *parcel);
    status_t ex_extractAndAppendLocalDescriptions(int64_t timeUs, MagicString *text, Parcel *parcel);
    status_t in_extractGlobalDescriptions(Parcel *parcel);
    status_t ex_extractGlobalDescriptions(Parcel *parcel);

	void selectLangIndex(int8_t index);
	int8_t getSelectedLangIndex();

    virtual status_t start();
    virtual status_t stop();
    virtual status_t read(int64_t *startTimeUs, int64_t *endTimeUs, Parcel *parcel, const MediaSource::ReadOptions *options = NULL);
    virtual status_t extractGlobalDescriptions(Parcel *parcel);
    virtual sp<MetaData> getFormat();

protected:
    virtual ~TimedTextASSSource();
	status_t scanFile();

	status_t getNextSubtitleInfo(
			off64_t *offset, int64_t *startTimeUs, MultiTextInfo *info);
	status_t getText(
			const MediaSource::ReadOptions *options,
			MagicString *text, int64_t *startTimeUs, int64_t *endTimeUs);
	status_t getText(
			const MediaSource::ReadOptions *options,
			MagicString *text, int64_t *startTimeUs, int64_t *endTimeUs, int8_t selectedLangIndex);

private:
    sp<MediaSource> mInSource;
    sp<DataSource> mExSource;
    sp<MetaData> mExMetaData;
    int32_t mASSFlag;
    size_t mExIndex;
	int32_t mCurIndex;
	EncodeType mFileEncodeType;
	KeyedVector<int64_t, MultiTextInfo> mTextVector;

	status_t skipHeadInfor(int64_t* offset);
	int compareExtendedRangeAndTime(size_t index, int64_t timeUs, int8_t selectedLangIndex);
    status_t extractAndAppendLocalDescriptions(int64_t timeUs, const MediaBuffer *textBuffer, Parcel *parcel);
	MagicString* parseText(const char* data, size_t size);

    DISALLOW_EVIL_CONSTRUCTORS(TimedTextASSSource);
};

}  // namespace android

#endif  // TIMED_TEXT_ASS_SOURCE_H_
#endif
