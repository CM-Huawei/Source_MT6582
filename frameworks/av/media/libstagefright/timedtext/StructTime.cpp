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

#include "StructTime.h"
#include <utils/Log.h>


namespace android{
		
	StructTime::StructTime(sp<MagicString>& mStr):
		hour(0), 
		minute(0), 
		second(0), 
		mSecond(0){
		parseTime(mStr);
	}

	StructTime::StructTime(uint32_t timeUs){
		mSecond = timeUs % 1000;
		uint32_t left = (timeUs - 1000 * mSecond) / 1000;
		hour = left / (60 * 60);
		left = left - (60 * 60 * hour);
		minute = left / 60;
		left = left - 60 * minute;
		second = left;
		//ALOGE("[Struct Time] Parser result (h:%d, m:%d, s:%d, ms:%d) from (timeUs:%d)",
		//	hour, minute, second, mSecond, timeUs);
	}

	StructTime::StructTime(sp<MagicString>& mFrame, int32_t frameRate):hour(0), 
		minute(0), 
		second(0), 
		mSecond(0){
		MagicString::print("[Frame Number]", mFrame);
		uint32_t frameNum = MagicString::getIntValue(mFrame);
		mSecond = ((frameNum % frameRate) * 1000) / frameRate;
		uint32_t left = frameNum / frameRate;
		hour = left / (60 * 60);
		left = left - (60 * 60 * hour);
		minute = left / 60;
		left = left - 60 * minute;
		second = left;
		//ALOGE("[Struct Time] Parser result (h:%d, m:%d, s:%d, ms:%d) from (mFrame:%d, fRate: %d)",
		//	hour, minute, second, mSecond, frameNum, frameRate);
	}

	StructTime::~StructTime(){

	}

	void StructTime::parseTime(sp<MagicString>& mStr){
		int index = mStr->indexOf(MagicString::MAGIC_STRING_COLON);
		int lastIndex = 0;
		int8_t typeSize = MagicString::sizeOfType(mStr->getType());
		sp<MagicString> temp = mStr->subString(0, index - typeSize);
		//MagicString::print("[StrucTime: hour]", temp);
		hour = MagicString::getIntValue(temp);
		
		temp = mStr->subString(index + typeSize);
		lastIndex += index + typeSize;
		index = temp->indexOf(MagicString::MAGIC_STRING_COLON);
		temp = temp->subString(0, index - typeSize);
		//MagicString::print("[StrucTime: minute]", temp);
		minute = MagicString::getIntValue(temp);
		
		temp = mStr->subString(lastIndex + index + typeSize);
		lastIndex += index + typeSize;
		index = temp->indexOf(MagicString::MAGIC_STRING_COMMA);		
		if( -1 == index ){	
			index = temp->indexOf(MagicString::MAGIC_STRING_PERIOD);
		}
		if( -1 == index ){	
			index = temp->indexOf(MagicString::MAGIC_STRING_SEMICOLON);
		}	
		if( -1 == index ){	
			index = temp->indexOf(MagicString::MAGIC_STRING_COLON);
		}	
		if( -1 != index ){
			temp = temp->subString(0, index - typeSize);
			//MagicString::print("[StrucTime: second]", temp);
			second = MagicString::getIntValue(temp);
			
			temp = mStr->subString(lastIndex + index + typeSize);
			//MagicString::print("[StrucTime: mSecond]", temp);
			mSecond = MagicString::getIntValue(temp);
		}else{
			//MagicString::print("[StrucTime: second]", temp);
			second = MagicString::getIntValue(temp);
		}
		//ALOGE("[Struct Time] Parser result (hour:%d, minute:%d, second:%d, mSecond:%d)", hour, minute, second, mSecond);
	}

	int64_t StructTime::calcTimeUs(){
		return (((((hour * 60) + minute) * 60) + second)* 1000 + mSecond) * 1000ll;
	}
}
#endif

