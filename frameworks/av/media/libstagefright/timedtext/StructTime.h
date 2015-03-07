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

#ifndef STRUCT_TIME_SOURCE_H_
#define STRUCT_TIME_SOURCE_H_
#include "MagicString.h"

namespace android {

class StructTime{
public:
	StructTime(sp<MagicString>& mStr);

	StructTime(sp<MagicString>& mFrameNum, int frameRate);

	StructTime(uint32_t timeUs);
	
	~StructTime();

	int64_t calcTimeUs();

private:
	uint16_t hour;
	uint16_t	minute;
	uint16_t second;
	uint16_t mSecond;

	void parseTime(sp<MagicString>& mStr);

};

};

#endif
#endif

