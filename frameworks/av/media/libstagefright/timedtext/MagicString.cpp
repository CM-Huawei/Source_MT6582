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

#include "MagicString.h"
#include <utils/Log.h>


namespace android{

	// static
	const char* MagicString::kEmptyString = "";

	const MagicString* MagicString::MAGIC_STRING_EQUAL					= new MagicString('=', 	ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_COLON 					= new MagicString(':', 	ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_SEMICOLON 				= new MagicString(';', 	ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_LARGE_BRACKETS 		= new MagicString('{', 	ENCODE_TYPE_NORMAL);		
	const MagicString* MagicString::MAGIC_STRING_ANTI_LARGE_BRACKETS 	= new MagicString('}', 	ENCODE_TYPE_NORMAL);		
	const MagicString* MagicString::MAGIC_STRING_MID_BRACKETS 			= new MagicString('[', 	ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_ANTI_MID_BRACKETS 		= new MagicString(']', 	ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_SMALL_BRACKETS 		= new MagicString('(', 	ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_ANTI_SMALL_BRACKETS 	= new MagicString(')', 	ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_ANGLE_BRACKETS			= new MagicString('<', 	ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_ANTI_ANGLE_BRACKETS	= new MagicString('>', 	ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_PERIOD 				= new MagicString('.', 	ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_COMMA 					= new MagicString(',', 	ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_SINGLE_QUOTE			= new MagicString('\'', ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_DOUBLE_QUOTE			= new MagicString('\"', ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_CR 					= new MagicString('\r', ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_LF 					= new MagicString('\n', ENCODE_TYPE_NORMAL);
	const MagicString* MagicString::MAGIC_STRING_STR_END 				= new MagicString('\0', ENCODE_TYPE_NORMAL);

	extern int8_t sizeOfType(EncodeType type);	
	extern int32_t strLen(const char* str, EncodeType type);
	extern char* encodeChar(char ch, EncodeType type);
	extern MagicString* encodeString(const char* ch, EncodeType fromType, EncodeType toType); 
	extern MagicString* encodeString(const char* ch, int32_t from, int32_t size, EncodeType fromType, EncodeType toType);
	extern int32_t memorySearch(const char* src, int32_t srcSize, const char* dest, int32_t destSize);

	MagicString::MagicString(MagicString& another):
		mData((char *)kEmptyString),
      	mSize(0),
      	mAllocSize(1),
      	mType(ENCODE_TYPE_NORMAL){
		//ALOGE("[MagicString] Copy Construction is invoked. this=0x%x, another=0x%x, length=%d",this, another.c_str(), another.length());
		MagicString::clone(another.c_str(), 0, another.length(), another.getType());
	}
	
	MagicString::MagicString(): 
		mData((char *)kEmptyString),
      	mSize(0),
      	mAllocSize(1),
      	mType(ENCODE_TYPE_NORMAL){
	}

	MagicString::MagicString(const char ch, EncodeType type): 
		mData((char *)kEmptyString),
      	mSize(0),
      	mAllocSize(1){
		mType = type;
		char * src =(char *)malloc( 2 * sizeof(char));
		*src = ch;
		*(src + 1) = '\0';
		setTo(src, 0, 1);
		free(src);
	}

	MagicString::MagicString(const char *src, EncodeType type): 
		mData((char *)kEmptyString),
      	mSize(0),
      	mAllocSize(1){
		mType = type;
		setTo(src);
	}

	MagicString::MagicString(const char *src, int32_t from, int32_t size, EncodeType type): 
		mData((char *)kEmptyString),
      	mSize(0),
      	mAllocSize(1){
		mType = type;

		setTo(src, from, size);

		//MagicString::print("[Created New MagicString]", *this);
	}
	

	MagicString::~MagicString(){
		//ALOGE("[MagicString] ~Copy DeConstruction is invoked. this=0x%x data=0x%x, length=%d", this, c_str(),  length());
		clear();
	}

	int32_t MagicString::indexOf(const char * str, int32_t startIndex, const EncodeType type){
		if( NULL == str){
			ALOGE("[MagicString] indexOf return -1 because str is NULL!");
			return -1;
		}
		int32_t len = length();
		if(startIndex >= len){
			ALOGE("[MagicString] indexOf return -1 because sIndex:%d is smaller than len=%d", startIndex, len);
			return -1;
		}
		const char * src = c_str();
		MagicString* dest =  encodeString(str, type, mType);
		int32_t ret = memorySearch( src + startIndex, len - startIndex, dest->c_str(), dest->length());
		ret = ( -1 == ret) ? -1 : (startIndex + ret);
		//ALOGE("Outof IndexOf: searched ret=%d, startIndex=%d", ret, startIndex);
		free(dest);
		return ret;
	}

	int32_t MagicString::indexOf(const MagicString* another, int32_t startIndex){
		if( NULL == another ){
			ALOGE("[MagicString] indexOf return -1 because another is Null!");
			return -1;
		}
		return indexOf(another->c_str(), startIndex, another->getType());
	}

	int32_t MagicString::indexOf(const MagicString* another){
		return indexOf(another, 0);
	}

	MagicString MagicString::operator=(const MagicString& from){
		mType = from.getType();
		setTo(from.c_str(), 0, from.length());	
		return *this;
	}

	MagicString MagicString::operator=(const sp<MagicString>& from){
		mType = from->getType();
		setTo(from->c_str(), 0, from->length());	
		return *this;
	}

	void MagicString::clone(const char* data, int32_t from, int32_t size, EncodeType type){
		mType = type;
		setTo((const char*)data, from, size);
	}

	bool MagicString::equal(MagicString& another){
		if(&another == this){
			return true;
		}

		if(mSize != another.length()){
			return false;
		}
		
		return !memcmp(mData, another.c_str(), mSize);
	}


	MagicString& MagicString::append(const char* ch , int32_t size, EncodeType type){
			return append(ch, 0, size, type);
	}

	MagicString& MagicString::append(MagicString* another , int32_t from){
		if(NULL == another){
			return *this;
		} 
		return append(another->c_str(), from, strLen(another->c_str(), another->getType())- from , another->getType());
	}

	MagicString& MagicString::append(MagicString* another, int32_t from, int32_t size){
		if(NULL == another){
			return *this;
		}
		return append(another->c_str(), from, size , another->getType());
	}

	MagicString& MagicString::append(sp<MagicString>& another , int32_t from, int32_t size){
		return append(another->c_str(), from, size , another->getType());
	}

	MagicString& MagicString::append(sp<MagicString>& another , int32_t from){
		return append(another->c_str(), from, strLen(another->c_str(), another->getType())- from , another->getType());
	}


	MagicString& MagicString::append(const char* ch , int32_t from, int32_t size, EncodeType type){
		if(NULL == ch){
			return *this;
		}

		makeMutable();

		int8_t typeSize = sizeOfType(type);
		if(size <= 0){
			return *this;
		}

		if(type == mType){	//same type. Don't need translate ch
			if (mSize + size + typeSize > mAllocSize) {
				mAllocSize = (mAllocSize + size + (32 - typeSize)) & -32;
				mData = (char *)realloc(mData, mAllocSize);
				if(mData == NULL){
					return *this;
				}
			}
	
			memcpy(&mData[mSize], &ch[from], size);
			mSize += size;
			char* enChar = encodeChar('\0', mType);
			memcpy(&mData[mSize], enChar, typeSize);
			//print(this);
			free(enChar);
		}else{
			//ALOGE("[MagicString] Append: type different! Encode Firstly!!!");
			MagicString* mStr = encodeString(ch, from, size, type, mType);
			//MagicString::print("[Encoded String]", *mStr);
			append(mStr);
			free(mStr);
		}		
		return *this;
	}

	MagicString* MagicString::encodeString(const char* ch, EncodeType fromType, EncodeType toType){
		return encodeString(ch, 0, strLen(ch, fromType), fromType, toType);
	}


	MagicString* MagicString::encodeString(const char* ch, int32_t from, int32_t size, EncodeType fromType, EncodeType toType){
		//ALOGE("[MagicString] encodeString ch[0]=0x%x, ch[1]=0x%x, ch[2] = 0x%x, from=%d, size=%d, fType=%d, tType=%d", 
		//					ch[0], ch[1], ch[2], from, size, fromType, toType);
		if( fromType == toType ){
			return new MagicString(ch, from, size, toType);
		}
		MagicString* retMagic = NULL;
		char * temp = NULL;
		int8_t fromTypeSize = sizeOfType(fromType);
		int8_t toTypeSize = sizeOfType(toType);
		if(1 == fromTypeSize){
			if(1 == toTypeSize){		//Normal -> UTF8  or UTF8 -> Normal
				return new MagicString(ch, from, size, toType);
			}else{		// (Normal or UTF8) -> (UTF16_Big or UTF16_Little)
				char* temp = new char[size * toTypeSize + toTypeSize];	
				for(int32_t i = 0; i< size * toTypeSize; i+=toTypeSize){
					if(ENCODE_TYPE_UTF16_BIG == toType){
						temp[i] = *(ch + from + i/toTypeSize);
						temp[i + 1] = 0;
					}else if(ENCODE_TYPE_UTF16_LITTLE == toType){
						temp[i] = 0;
						temp[i + 1] = *(ch + from + i/toTypeSize);
					}
				}
				temp[size * toTypeSize] = '\0';
				temp[size * toTypeSize + 1] = '\0';
				retMagic = new MagicString(temp, 0, size * toTypeSize, toType);
				free(temp);
				return retMagic;
			}
		}else{
			if(1 == toTypeSize){
				temp = new char[size + 1];
				for(int32_t i = 0; i< size * toTypeSize; i++){
					if(fromType == ENCODE_TYPE_UTF16_BIG){
						temp[i] = *(ch + from + i*2);
					}else if(fromType == ENCODE_TYPE_UTF16_LITTLE){
						temp[i] = *(ch + from + i*2 + 1);
					}
				}
				temp[size] = '\0';
				retMagic = new MagicString(temp, 0, size, toType);
				free(temp);
				return retMagic;
			}else{
				temp = new char[size * toTypeSize + 2];	
				for(int32_t i = 0; i< size * toTypeSize; i+=2){
					temp[i] = *(ch + from + i + 1);
					temp[i + 1] = *(ch + from + i);
				}
				temp[size * toTypeSize] = '\0';
				temp[size * toTypeSize + 1] = '\0';
				retMagic = new MagicString(temp, 0, size * toTypeSize, toType);
				free(temp);
				return retMagic;
			}
		}
		return NULL;
	}

	sp<MagicString> MagicString::subString(int32_t startIndex, int32_t endIndex){
		//ALOGE("[--db--] subString startIndex= %d, endIndex=%d", startIndex, endIndex);
		if(endIndex < startIndex){
			return NULL;
		}

		//TODO: There has memory leak issue here. Will be fixed later.
		//MagicString *temp= new MagicString(mData, startIndex, (endIndex - startIndex + sizeOfType(mType)), mType);
		//return *temp;

		//Use sp<> machanism to avoid meomry leakage issue
		sp<MagicString> ret = new MagicString(mData, startIndex, (endIndex - startIndex + sizeOfType(mType)), mType);
		return ret;
	}

	sp<MagicString> MagicString::subString(int32_t startIndex){
		return subString(startIndex, length() - sizeOfType(mType));
	}


	bool MagicString::startWith(const char* ch, int32_t size, EncodeType chEncodeType){
		if(size > length()){
			return false;
		}
		MagicString *mStr = encodeString(ch, 0, size, chEncodeType, mType);
		if( (NULL == mStr) || (mStr->length() > length())){
			return false;
		}
		bool ret = (0 == memcmp(mData, mStr->c_str(), mStr->length()));
		free(mStr);
		return ret;
	}

	bool MagicString::startWith(const MagicString* str){
		if(NULL == str){
			return false;
		}
		return startWith(str->c_str(), str->length(), str->getType());
	}

 	void MagicString::remove(int32_t from, int32_t size){
		//ALOGE("[MagicString] Try to remove from=%d,size=%d, mDataSize=%d", from, size, mSize);
		if((from + size) > mSize){
			return;
		}
		int8_t typeSize = sizeOfType(mType);
		memmove( &mData[from], &mData[from + size], mSize - from - size);
		mSize -= size;

		char* enChar = encodeChar('\0', mType);
		memcpy(&mData[mSize], enChar, typeSize);
		free(enChar);
		//ALOGE("[MagicString] removed data =%s, size=%d", mData, mSize);
 	}

	void MagicString::remove(const char* ch, EncodeType chEncodeType){
		makeMutable();
		int32_t start = 0;
		int32_t index = 0;  
		int32_t len = MagicString::strLen(ch, chEncodeType);
		int8_t typeSize = sizeOfType(mType);
		do{
			start = index;
			index = indexOf(ch, start, chEncodeType);
			if(-1 == index){
				return;
			}
			remove(index, len * typeSize);
		}while(true);
	}

	void MagicString::remove(MagicString& str){
		remove(str.c_str(), str.getType());
	}

	void MagicString::patternRemove(const char* begin, EncodeType beginEncodeType, 
		const char* end , EncodeType endEncodeType){
		makeMutable();

		int32_t index = 0;  
		int32_t start = 0;
		int32_t bLen = MagicString::strLen(begin, beginEncodeType);
		int32_t eLen = MagicString::strLen(end, endEncodeType);
		int8_t typeSize = sizeOfType(mType);
		do{
			index = indexOf(begin, start, beginEncodeType);
			if(-1 == index){
				return;
			}

			start = index;
			index = index + bLen * typeSize + typeSize;
			index = indexOf(end, index, endEncodeType);
			if(-1 == index){
				return;
			}
			int32_t size = index + eLen * typeSize - start;
			remove(start, size); 
			index = start;
		}while(true);
	}

	void MagicString::replace(const char* src,  EncodeType srcEncodeType, 
		const char* dest, EncodeType destEncodeType){
		makeMutable();
		int32_t start = 0;
		int32_t index = 0;  
		int32_t srcLen = MagicString::strLen(src, srcEncodeType);
		int32_t destLen = MagicString::strLen(dest, destEncodeType);
		int8_t typeSize = sizeOfType(mType);

		if(destLen == 0){
			remove(src, srcEncodeType);
			return;
		}
		do{
			start = index;
			index = indexOf(src, start, srcEncodeType);
			if(-1 == index){
				return;
			}

			MagicString * temp = encodeString(dest, destEncodeType, mType);
			const char * tempData = temp->c_str();
			int32_t tempLen = temp->length();


			if (mSize + destLen - srcLen + typeSize > mAllocSize) {
				mAllocSize = (mAllocSize + destLen - srcLen + (32 - typeSize)) & -32;
				mData = (char *)realloc(mData, mAllocSize);
				if(mData == NULL){
					free(temp);
					return;
				}
			}

			//ALOGE("[MagicString] replace index=%d, destLen=%d, srcLen=%d, mSize=%d", index , destLen, srcLen, mSize);
			if(srcLen == destLen){
				memmove(&mData[index], tempData, destLen);
			}else{
				memmove(&mData[index + destLen], &mData[index + srcLen], mSize - index - srcLen);
				memmove(&mData[index], tempData, destLen);
			}
			mSize += destLen - srcLen;
			char* enChar = encodeChar('\0', mType);
			memcpy(&mData[mSize], enChar, typeSize);
			free(enChar);
			free(temp);
				
			index += destLen;
		}while(true);
	}

	void MagicString::replace(MagicString& src, MagicString& dest){
		 replace(src.c_str(), src.getType(), dest.c_str(), dest.getType());
	}


	void MagicString::setTo(const char* ch){
		setTo(ch, 0, strLen(ch, mType));
	}


	void MagicString::setTo(const char* ch , int32_t from, int32_t size){
		clear();
    	append(ch, from, size, mType);
	}

	int32_t MagicString::memorySearch(const char* src, int32_t srcSize, const char* dest, int32_t destSize){
		//ALOGE(" [MagicString] memorySearch src[%d,%d,%d,%d,%d], sSize=%d, dest[%d,%d,%d], dSize=%d", src[0],src[1],src[2],src[3],src[4], 
		//	srcSize, dest[0], dest[1], dest[2],  destSize);
		if((srcSize <= 0) || (destSize <= 0) || (destSize > srcSize)){
			ALOGE("[MagicString] [The Argument Is Invalid] (srcSize:%d, destSize:%d) Return -1", srcSize, destSize);
			return -1;
		}
		int32_t index = 0;
		do{
			if( index > (srcSize - destSize)){
				//ALOGE("[MagicString] [Can Not Searched] Return -1");
				return -1;
			}
			if( dest[0] == src[index] ){
				if(0 == memcmp((src + index), dest, destSize)){
					return index;
				}
			}
			index++;
		}while(true);
	}

	int32_t MagicString::strLen(const char* str, EncodeType type){
		const char* enChar = encodeChar('\0', type);
		int32_t enLen = sizeOfType(type);
		int32_t len = 0;
		do{
			char ch = enChar[0];
			if( ch == *(str + len)){
				for(int i= 1; i< enLen; i++){
					if( enChar[i] != *(str + len +i)){
						break;
					}
				}
				return len;
			}

			len += enLen;
		}while(true);
	}

	void MagicString::print(MagicString& str){
		print(NULL, str);
	}

	void MagicString::print(const char * tag, MagicString& str){
		sp<MagicString> strSP = new MagicString(str);
		print(tag, strSP);
	}

	/*static void print(sp<MagicString>& str){
		print("", str);
	}*/

	void MagicString::print(const char * tag, sp<MagicString>& str){
		EncodeType type = str->getType();
		const char* chs = str->c_str();
		switch(type){
			case ENCODE_TYPE_NORMAL:
			case ENCODE_TYPE_UTF8:
			case ENCODE_TYPE_UNDEFINED:
				if(NULL == tag){
					//ALOGE("[MString] %s\t[size:%ld]", chs, str->length());
				}else{
					//ALOGE("[MString] %s:%s\t[size:%ld]", tag, chs, str->length());	
				}
				break;
			case ENCODE_TYPE_UTF16_BIG:
			case ENCODE_TYPE_UTF16_LITTLE:
				//todo. will print byte array
				//ALOGE("[MString] %s:%s\t[size:%ld]", tag, chs, str.length());
				break;
			default:
				break;
		}
	}


	int8_t MagicString::sizeOfType(EncodeType type){
		switch(type){
			case ENCODE_TYPE_NORMAL:	//same as ENCODE_TYPE_UTF8
			case ENCODE_TYPE_UTF8:
				return 1;
			case ENCODE_TYPE_UTF16_BIG:	//same as ENCODE_TYPE_UTF16_BIG
			case ENCODE_TYPE_UTF16_LITTLE:	
				return 2;
			default:
				return 1;
		}
	}

	char* MagicString::encodeChar(char ch, EncodeType type){
		int8_t typeSize = sizeOfType(type);
		char * ret= new char[typeSize]; 
		switch(type){
			case ENCODE_TYPE_NORMAL:	//same as ENCODE_TYPE_UTF8
			case ENCODE_TYPE_UTF8:
				ret[0] = ch;
				return ret;
			case ENCODE_TYPE_UTF16_BIG:	
				ret[0] = ch;
				ret[1] = '\0';
				return ret;
			case ENCODE_TYPE_UTF16_LITTLE:
				ret[0] = '\0';
				ret[1] = ch;
				return ret;
			default:
				ret[0] = ch;
				return ret;
		}
	}

	void MagicString::clear(){
		if ((mAllocSize != 0) && mData && mData != kEmptyString) {
		  	free(mData);
        	mData = NULL;
    	}
		mData = (char *)kEmptyString;
      	mSize = 0;
      	mAllocSize = 1;
	}

	bool MagicString::empty(){
		return mSize == 0;
	}
	
	void MagicString::trim(){
		int32_t startPos = 0;
		int32_t endPos = mSize; 
		trim(&startPos, &endPos);
	}

	void MagicString::trim(int32_t *noneSpaceStartPos ){
		int32_t endPos = mSize; 
		trim(noneSpaceStartPos, &endPos);
	}


	void MagicString::trim(int32_t *noneSpaceStartPos, int32_t *noneSpaceEndPos){
		makeMutable();			
				
		char* space = encodeChar(' ',  mType);
		int32_t enLen = sizeOfType(mType);
		int32_t startIndex = 0;
		int32_t endIndex = mSize; 
		do{   //try to find the none-space char from the beginning
			if( space[0] == *(mData + startIndex)){
				for(int i= 1; i< enLen; i++){
					if( space[i] != *(mData + startIndex + i)){
						break;
					}
				}		
			}else{
				break;
			}
			startIndex += enLen;
		}while(true);

		do{ //try to find the none-space char from the end
			if( space[0] == *(mData + endIndex - enLen)){
				for(int i= 1; i< enLen; i++){
					if( space[i] != *(mData + endIndex - enLen + i)){
						break;
					}
				}		
			}else{
				break;
			}
			endIndex -= enLen;
		}while(true);

		*noneSpaceStartPos = startIndex;
		*noneSpaceEndPos = endIndex;

		memmove(mData, &mData[startIndex], endIndex - startIndex);
		mSize = endIndex - startIndex;
		//ALOGE("[MagicString] trim getSIndex=%d, getEIndex=%d, finalSize=%d", startIndex, endIndex, mSize);
		const char* enChar = encodeChar('\0', mType);
		memcpy(&mData[mSize], enChar, sizeOfType(mType));
		free(space);
	}

	const char* MagicString::c_str() const {
    	return mData;
	}

	int32_t MagicString::length() const{
		return mSize;
	}

	void MagicString::makeMutable() {
		if ((mData == kEmptyString)) {
			mData = strdup(kEmptyString);
		}
	}


	const EncodeType MagicString::getType() const{
		return mType;
	}

	uint8_t MagicString::getIntValue(char ch){
		if(ch >= '0' && ch<='9'){
			return (ch - '0');
		}
		if(ch >= 'a' && ch<='z'){
			return (ch - 'a' + 10);
		}
		if(ch >= 'A' && ch<='Z'){
			return (ch - 'A' + 10);
		}
		return 0;
	}


	/*uint32_t MagicString::getIntValue(sp<MagicString> str, int8_t hex){
		getIntValue((sp<MagicString>&)str, hex);
	}*/

	uint32_t MagicString::getIntValue(sp<MagicString>& str, int8_t hex){
		const char* ch = str->c_str();
		int32_t len = str->length();
		uint32_t sum = 0;
		EncodeType type = str->getType();
		switch(type){
			case ENCODE_TYPE_UTF16_BIG: 		//FF FE
				for(int i = 0; i < len; i += 2){
					sum = sum * hex + getIntValue(ch[i]);
				}
				break;
			case ENCODE_TYPE_UTF16_LITTLE:		//FE FF
				for(int i = 0; i < len; i += 2){
					sum = sum * hex + getIntValue(ch[i + 1]);
				}
				break;
			case ENCODE_TYPE_NORMAL:
			case ENCODE_TYPE_UTF8:
			case ENCODE_TYPE_UNDEFINED:
			default:
				for(int i = 0; i < len; i++){
					sum = sum * hex + getIntValue(ch[i]);
				}
				break;
		}
		//ALOGE("[MagicString] Parse IntValue From:%s, Len=%d, sum=%d", ch, len, sum);
		return sum;
	}

	void MagicString::unitTest(){
		#ifdef SELF_TEST

		#endif
	}
}

#endif

