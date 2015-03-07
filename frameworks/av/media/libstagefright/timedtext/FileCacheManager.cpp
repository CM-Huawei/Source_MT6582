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

#include "FileCacheManager.h"
#include <utils/Log.h>

namespace android{

FileCacheManager* FileCacheManager::instance = NULL;

FileCacheManager::FileCacheManager()
	:fileOffset(0),
	 cachedLen(0),
	 cachedSource(NULL){

}


FileCacheManager::~FileCacheManager(){
	cachedSource = NULL;
}

//static
FileCacheManager& FileCacheManager::getInstance(){
	if(NULL == instance){
		instance = new FileCacheManager();
	}	
	return *instance;
}

bool FileCacheManager::isCached(const sp<DataSource>& mSource, uint64_t offset){
	if((mSource == cachedSource) && (offset >= fileOffset) && (offset< (fileOffset + cachedLen))){
		if((fileOffset + cachedLen - offset) < (CACHE_SIZE * WATER_LEVEL)){
			//Here is WATER LEVEL control logic. 
			//Noted that CACHE_SIZE * WATER_LEVEL must be bigger than 2.
			//Otherwise, 0x0D & 0x0A may be separated. And then it affects the judgement logic of getNextLine
			return false;
		}	
		return true;
	}
	return false;
}


uint64_t FileCacheManager::readFromCache(const sp<DataSource>& mSource, uint64_t offset, char* data, uint64_t readSize){
	Mutex::Autolock autoLock(mLock);
	uint64_t readOffset;
	uint64_t readLen;
	if(isCached(mSource, offset)){
		readOffset = offset - fileOffset;
		if((offset + readSize) > (fileOffset + cachedLen)){
			readLen = (fileOffset + cachedLen - offset);
		}else{
			readLen = readSize;
		}
		memcpy(data, cachedData + readOffset, readLen);

		//ALOGE("[FileCacheManager] Read From Cache: cacheOffset=%lld, cacheLen=%lld, readOffset=%lld, readSize=%lld, retReadSize=%lld",
		//						fileOffset, cachedLen, readOffset, readSize, readLen);
		return readLen;
	}else{
		readOffset = offset;
		readLen = mSource->readAt(offset, cachedData, CACHE_SIZE);
		if(readLen <= 0){
			return readLen;
		}
		cachedSource = mSource;
		fileOffset = readOffset;
		cachedLen = readLen;

		if(readSize <= readLen){
			readLen = readSize;
		}

		memcpy(data, cachedData, readLen);
		
		//ALOGE("[FileCacheManager] Read From File: cacheOffset=%lld, cacheLen=%lld, readOffset=%lld, readSize=%lld, retReadSize=%lld",
		//						fileOffset, cachedLen, readOffset, readSize, readLen);
		return readLen;
	}
}


}
#endif 

