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

#ifndef MAGIC_STRING_SOURCE_H
#define MAGIC_STRING_SOURCE_H

#ifdef MTK_SUBTITLE_SUPPORT
#include <sys/types.h>
#include <stdlib.h>
#include <utils/Compat.h>  // off64_t
#include <utils/RefBase.h>


namespace android{

typedef enum _EncodeType {
	ENCODE_TYPE_NORMAL			= 0,
	ENCODE_TYPE_UTF16_BIG 		= 1,
	ENCODE_TYPE_UTF16_LITTLE	= 2,
	ENCODE_TYPE_UTF8			= 3,
	ENCODE_TYPE_UNDEFINED 		= -1
}EncodeType;


class MagicString: public RefBase{

public:
	MagicString();
    MagicString(MagicString& another);
	MagicString(const char src, EncodeType type);
	MagicString(const char *src, EncodeType type);
	MagicString(const char *src, int32_t from, int32_t size, EncodeType type);
	~MagicString();
	
	
	int32_t indexOf(const char * str, int32_t startIndex, const EncodeType type);
	int32_t indexOf(const MagicString* another, int32_t startIndex);
	int32_t indexOf(const MagicString* another);

	MagicString& append(const char* ch, int32_t from, EncodeType type = ENCODE_TYPE_NORMAL);
	MagicString& append(const char* ch, int32_t from, int32_t size, EncodeType type);
	MagicString& append(MagicString* another, int32_t from = 0);
	MagicString& append(MagicString* another, int32_t from, int32_t size);
	MagicString& append(sp<MagicString>& another , int32_t from, int32_t size);
	MagicString& append(sp<MagicString>& another, int32_t from = 0);

	sp<MagicString> subString(int32_t startIndex, int32_t endIndex);
	sp<MagicString> subString(int32_t startIndex);

	bool startWith(const char* ch, int32_t size, EncodeType chEncodeType);
	bool startWith(const MagicString* str);

	void remove(MagicString& str);
	void remove(int32_t from, int32_t size);
	void remove(const char* ch, EncodeType chEncodeType = ENCODE_TYPE_NORMAL);
	void patternRemove(const char* begin, EncodeType beginEncodeType, const char* end , EncodeType endEncodeType);

	void replace(const char* src, EncodeType srcEncodeType, const char* dest, EncodeType destEncodeType);
	void replace(MagicString& src, MagicString& dest);
		
	static uint32_t getIntValue(MagicString& str, int8_t hex = 10);
	//static uint32_t getIntValue(sp<MagicString> str, int8_t hex = 10);
	static uint32_t getIntValue(sp<MagicString>& str, int8_t hex = 10);

	bool equal(MagicString& another);
	MagicString operator=(const MagicString& from);
	MagicString operator=(const sp<MagicString>& from);
	void clone(const char* data, int32_t from, int32_t size, EncodeType type);
	int32_t length() const;

	const EncodeType getType() const;

	void clear();
	
	void trim();
	void trim(int32_t *noneSpaceStartPos);
	void trim(int32_t *noneSpaceStartPos, int32_t *noneSpaceEndPos);

	bool empty();

	const char *c_str() const;

//static useful method
	static const MagicString* MAGIC_STRING_CR; 							//indicates '/r'
	static const MagicString* MAGIC_STRING_LF;							//indicates '/n'
	static const MagicString* MAGIC_STRING_STR_END;						//indicates '/0'
	static const MagicString* MAGIC_STRING_EQUAL; 						//indicates '='
	static const MagicString* MAGIC_STRING_COLON;						//indicates ':'
	static const MagicString* MAGIC_STRING_SEMICOLON;					//indicates ';'
	static const MagicString* MAGIC_STRING_LARGE_BRACKETS;				//indicates '{'
	static const MagicString* MAGIC_STRING_ANTI_LARGE_BRACKETS;			//indicates '}'
	static const MagicString* MAGIC_STRING_MID_BRACKETS;				//indicates '['
	static const MagicString* MAGIC_STRING_ANTI_MID_BRACKETS;			//indicates ']'
	static const MagicString* MAGIC_STRING_SMALL_BRACKETS;				//indicates '('
	static const MagicString* MAGIC_STRING_ANTI_SMALL_BRACKETS;			//indicates ')'
	static const MagicString* MAGIC_STRING_ANGLE_BRACKETS;				//indicates '<'	
	static const MagicString* MAGIC_STRING_ANTI_ANGLE_BRACKETS;			//indicates '>'
	static const MagicString* MAGIC_STRING_PERIOD; 						//indicates '.'
	static const MagicString* MAGIC_STRING_COMMA; 						//indicates ','
	static const MagicString* MAGIC_STRING_SINGLE_QUOTE; 				//indicates '/''
	static const MagicString* MAGIC_STRING_DOUBLE_QUOTE; 				//indicates '/"'
	static const MagicString* MAGIC_STRING_WELL_MARK; 					//indicates '#'
	
	static int8_t sizeOfType(EncodeType type);
	
	static int32_t strLen(const char* str, EncodeType type);
	
	static char* encodeChar(char ch, EncodeType type);

	static int32_t memorySearch(const char* src, int32_t srcSize, const char* dest, int32_t destSize);

	static void print(MagicString& str);
	//static void print(sp<MagicString>& str);
	static void print(const char * tag, MagicString& str);
	static void print(const char * tag, sp<MagicString>& str);

	static void unitTest();
	
private:

	void setTo(const char * ch);

	void setTo(const char * ch, int32_t from, int32_t size);

	void makeMutable();
	static uint8_t getIntValue(char ch);

	
	//The return object is created in this method. 
	//Therefore, user should destroy return object when it is not valid
	static MagicString* encodeString(const char* ch, EncodeType fromType, EncodeType toType); 
	static MagicString* encodeString(const char* ch, int32_t from, int32_t size, EncodeType fromType, EncodeType toType);
	
	EncodeType mType;

	char *mData;
    size_t mSize;
    size_t mAllocSize;
	
	static const char *kEmptyString;

};

}

#endif
#endif

