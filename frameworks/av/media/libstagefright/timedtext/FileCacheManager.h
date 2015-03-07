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

#ifndef FILE_CACHE_MANAGER_SOURCE_H_
#define FILE_CACHE_MANAGER_SOURCE_H_

#define CACHE_SIZE (4 * 1024)
#define WATER_LEVEL 0.05

#include <DataSource.h>
#include <utils/Compat.h>  // off64_t

namespace android{

//class AString;

class FileCacheManager{
public:
	static FileCacheManager& getInstance();

	~FileCacheManager();

	uint64_t readFromCache(const sp<DataSource>& mSource, uint64_t offset, char* data, uint64_t readSize);
	
protected:
	FileCacheManager();

private:
	static FileCacheManager* instance;
	sp<DataSource> cachedSource;
	char cachedData[CACHE_SIZE];
	uint64_t fileOffset;
	uint64_t cachedLen;

	Mutex mLock;

	bool isCached(const sp<DataSource>& source, uint64_t offset);

};
};
#endif
#endif


