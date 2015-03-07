/*
 * Copyright (C) 2009 The Android Open Source Project
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

#include "include/AMRExtractor.h"

#if CHROMIUM_AVAILABLE
#include "include/chromium_http_stub.h"
#endif

#include "include/AACExtractor.h"
#include "include/DRMExtractor.h"
#include "include/FLACExtractor.h"
#include "include/HTTPBase.h"
#include "include/MP3Extractor.h"
#include "include/MPEG2PSExtractor.h"
#include "include/MPEG2TSExtractor.h"
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_APE_SUPPORT
#include "include/APEExtractor.h"
#endif
#include <MtkRTSPController.h>
#endif  //#ifndef ANDROID_DEFAULT_CODE
#include "include/MPEG4Extractor.h"
#include "include/NuCachedSource2.h"
#include "include/OggExtractor.h"
#include "include/WAVExtractor.h"
#include "include/WVMExtractor.h"
#ifndef ANDROID_DEFAULT_CODE
#include "MtkAACExtractor.h"
#endif //#ifndef ANDROID_DEFAULT_CODE

#include "matroska/MatroskaExtractor.h"
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_FLV_PLAYBACK_SUPPORT
#include <MtkFLVExtractor.h>
#endif
#ifdef MTK_OGM_PLAYBACK_SUPPORT
#include <OgmExtractor.h>
#endif
#endif //#ifndef ANDROID_DEFAULT_CODE
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/FileSource.h>
#include <media/stagefright/MediaErrors.h>
#include <utils/String8.h>

#include <cutils/properties.h>
#define ATRACE_TAG ATRACE_TAG_VIDEO
#include <utils/Trace.h>

//for ASF playback!
#ifndef ANDROID_DEFAULT_CODE
#include <media/stagefright/MediaDefs.h>   
#include <dlfcn.h>
#ifdef MTK_AVI_PLAYBACK_SUPPORT
#include <MtkAVIExtractor.h>
#endif
#endif  //#ifndef ANDROID_DEFAULT_CODE
namespace android {
#ifndef ANDROID_DEFAULT_CODE  

#ifdef MTK_WMV_PLAYBACK_SUPPORT
#define MTK_ASF_EXTRACTOR_LIB_NAME			"libasfextractor.so"
#define MTK_ASF_EXTRACTOR_RECOGNIZER_NAME	"mtk_asf_extractor_recognize"
#define MTK_ASF_EXTRACTOR_FACTORY_NAME		"mtk_asf_extractor_create_instance"
typedef sp<MediaExtractor> AsfFactory_Ptr(const sp<DataSource> &source);
typedef bool AsfRecognizer_Ptr(const sp<DataSource> &source);
bool SniffASF(const sp<DataSource> &source, String8 *mimeType, float *confidence, sp<AMessage> *) {
	bool ret = false;
	void* pAsfLib = NULL;

	pAsfLib = dlopen(MTK_ASF_EXTRACTOR_LIB_NAME, RTLD_NOW);
	if (NULL == pAsfLib) {
		ALOGE ("%s", dlerror());
		return NULL;
	}

	AsfRecognizer_Ptr* asf_extractor_recognize = (AsfRecognizer_Ptr*) dlsym(pAsfLib, MTK_ASF_EXTRACTOR_RECOGNIZER_NAME);
	if (NULL == asf_extractor_recognize) {
		ALOGE ("%s", dlerror());
		ret = false;
	}

	if (asf_extractor_recognize(source)) {
		*mimeType = MEDIA_MIMETYPE_CONTAINER_ASF;
		*confidence = 0.8;
		ret = true;
	}
	else {
		ret = false;
	}

	if(pAsfLib!=NULL){
		dlclose(pAsfLib);
	}

	ALOGE ("SniffASF return %d", ret);
	return ret;
}
#endif
#endif
bool DataSource::getUInt16(off64_t offset, uint16_t *x) {
    *x = 0;

    uint8_t byte[2];
    if (readAt(offset, byte, 2) != 2) {
        return false;
    }

    *x = (byte[0] << 8) | byte[1];

    return true;
}

bool DataSource::getUInt24(off64_t offset, uint32_t *x) {
    *x = 0;

    uint8_t byte[3];
    if (readAt(offset, byte, 3) != 3) {
        return false;
    }

    *x = (byte[0] << 16) | (byte[1] << 8) | byte[2];

    return true;
}

bool DataSource::getUInt32(off64_t offset, uint32_t *x) {
    *x = 0;

    uint32_t tmp;
    if (readAt(offset, &tmp, 4) != 4) {
        return false;
    }

    *x = ntohl(tmp);

    return true;
}

bool DataSource::getUInt64(off64_t offset, uint64_t *x) {
    *x = 0;

    uint64_t tmp;
    if (readAt(offset, &tmp, 8) != 8) {
        return false;
    }

    *x = ntoh64(tmp);

    return true;
}

status_t DataSource::getSize(off64_t *size) {
    *size = 0;

    return ERROR_UNSUPPORTED;
}

////////////////////////////////////////////////////////////////////////////////

Mutex DataSource::gSnifferMutex;
List<DataSource::SnifferFunc> DataSource::gSniffers;
bool DataSource::gSniffersRegistered = false;

bool DataSource::sniff(
        String8 *mimeType, float *confidence, sp<AMessage> *meta) {
    ATRACE_CALL();
    *mimeType = "";
    *confidence = 0.0f;
    meta->clear();

    {
        Mutex::Autolock autoLock(gSnifferMutex);
        if (!gSniffersRegistered) {
            return false;
        }
    }

    for (List<SnifferFunc>::iterator it = gSniffers.begin();
         it != gSniffers.end(); ++it) {
        String8 newMimeType;
        float newConfidence;
        sp<AMessage> newMeta;
        if ((*it)(this, &newMimeType, &newConfidence, &newMeta)) {
            if (newConfidence > *confidence) {
                *mimeType = newMimeType;
                *confidence = newConfidence;
                *meta = newMeta;
            }
        }
    }

    return *confidence > 0.0;
}

#ifndef ANDROID_DEFAULT_CODE
///#define DISABLE_FAST_SNIFF
bool DataSource::fastsniff(
    int fd, String8 *mimeType)
{
#ifdef DISABLE_FAST_SNIFF
    return false;
#endif

    *mimeType ="";
    float confidence = 0.0f;
    sp<AMessage> *meta;
    String8 newMimeType ;
    sp<AMessage> newMeta;

    char buffer[256];
    char linkto[256];
    memset(buffer, 0, 256);
    memset(linkto, 0, 256);
    sprintf(buffer, "/proc/%d/fd/%d", gettid(), fd);
    int len = 0;
    len = readlink(buffer, linkto, sizeof(linkto));

    if(len <= 5)
    {
        return false;
    }

    ALOGV("fastsniff pid %d, fd %d, fd=%d", gettid(), fd, len);

    struct {
        unsigned FileextSize;
        const char *FileextName;
        bool (*Snifffun)(const sp<DataSource> &source, String8 *mimeType,
                        float *confidence, sp<AMessage> *meta);
    } snifftable[] = {
        { 4,  ".ogg", SniffOgg    },
        { 4,  ".mp3", FastSniffMP3},
        { 4,  ".aac", FastSniffAAC},
#ifdef MTK_AUDIO_APE_SUPPORT        
        { 4,  ".ape", SniffAPE    },
#endif        
        { 5,  ".flac",SniffFLAC   },
        { 4,  ".amr", SniffAMR    },
        { 4,  ".awb", SniffAMR    },
#ifdef MTK_WMV_PLAYBACK_SUPPORT        
        { 4,  ".wma", SniffASF    },
#endif
        { 7,  ".dthumb", SniffMPEG4}, 
        { 4,  ".wav", SniffWAV}
    };

    for (unsigned i = 0; i < sizeof(snifftable)/sizeof(snifftable[0]); ++i) 
    {
        if(strcasestr(linkto + (len - snifftable[i].FileextSize), snifftable[i].FileextName) != NULL) 
        {
            if((*snifftable[i].Snifffun)(this, &newMimeType, &confidence, &newMeta))
                ALOGD("fastsniff is %s", snifftable[i].FileextName);
            break;
        }
    }

    if(confidence > 0.0)
        *mimeType = newMimeType;
    return confidence > 0.0;
}
#endif
// static
void DataSource::RegisterSniffer_l(SnifferFunc func) {
    for (List<SnifferFunc>::iterator it = gSniffers.begin();
         it != gSniffers.end(); ++it) {
        if (*it == func) {
            return;
        }
    }

    gSniffers.push_back(func);
}

// static
void DataSource::RegisterDefaultSniffers() {
    Mutex::Autolock autoLock(gSnifferMutex);
    if (gSniffersRegistered) {
        return;
    }
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
		// OMA DRM v1 implementation: this need to be registered always, and as the first one.
		RegisterSniffer_l(SniffDRM);
#endif
#endif // ANDROID_DEFAULT_CODE
    RegisterSniffer_l(SniffMPEG4);
    RegisterSniffer_l(SniffMatroska);

    RegisterSniffer_l(SniffOgg);
    RegisterSniffer_l(SniffWAV);
    RegisterSniffer_l(SniffFLAC);
    RegisterSniffer_l(SniffAMR);
    RegisterSniffer_l(SniffMPEG2TS);
    RegisterSniffer_l(SniffMP3);
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AUDIO_APE_SUPPORT
    RegisterSniffer_l(SniffAPE);
#endif
	RegisterSniffer_l(SniffMtkAAC);
#endif // #ifndef ANDROID_DEFAULT_CODE
    RegisterSniffer_l(SniffAAC);
#ifndef ANDROID_DEFAULT_CODE 
#ifdef MTK_WMV_PLAYBACK_SUPPORT
    RegisterSniffer_l(SniffASF);    
#endif
    // register sdp sniff for rtsp play by local sdp file
    RegisterSniffer_l(SniffSDP);
#endif // #ifndef ANDROID_DEFAULT_CODE
    RegisterSniffer_l(SniffMPEG2PS);
    RegisterSniffer_l(SniffWVM);
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_AVI_PLAYBACK_SUPPORT
    RegisterSniffer_l(MtkSniffAVI);
#endif
#endif
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_FLV_PLAYBACK_SUPPORT
    RegisterSniffer_l(SniffFLV);
#endif  
#ifdef MTK_OGM_PLAYBACK_SUPPORT 
    RegisterSniffer_l(SniffOgm);
#endif
#endif
#ifdef ANDROID_DEFAULT_CODE
		// for android default code, the DRM sniffer should be registed here.
		char value[PROPERTY_VALUE_MAX];
		if (property_get("drm.service.enabled", value, NULL)
				&& (!strcmp(value, "1") || !strcasecmp(value, "true"))) {
			RegisterSniffer_l(SniffDRM);
		}
#else
		// not android default code, but OMA DRM v1 is disabled
#ifndef MTK_DRM_APP
		char value[PROPERTY_VALUE_MAX];
		if (property_get("drm.service.enabled", value, NULL)
				&& (!strcmp(value, "1") || !strcasecmp(value, "true"))) {
			RegisterSniffer_l(SniffDRM);
		}
#endif
#endif // ANDROID_DEFAULT_CODE
    gSniffersRegistered = true;
}

// static
sp<DataSource> DataSource::CreateFromURI(
        const char *uri, const KeyedVector<String8, String8> *headers) {
    bool isWidevine = !strncasecmp("widevine://", uri, 11);

    sp<DataSource> source;
    if (!strncasecmp("file://", uri, 7)) {
        source = new FileSource(uri + 7);
    } else if (!strncasecmp("http://", uri, 7)
            || !strncasecmp("https://", uri, 8)
            || isWidevine) {
        sp<HTTPBase> httpSource = HTTPBase::Create();

        String8 tmp;
        if (isWidevine) {
            tmp = String8("http://");
            tmp.append(uri + 11);

            uri = tmp.string();
        }

        if (httpSource->connect(uri, headers) != OK) {
            return NULL;
        }

        if (!isWidevine) {
            String8 cacheConfig;
            bool disconnectAtHighwatermark;
            if (headers != NULL) {
                KeyedVector<String8, String8> copy = *headers;
                NuCachedSource2::RemoveCacheSpecificHeaders(
                        &copy, &cacheConfig, &disconnectAtHighwatermark);
            }

            source = new NuCachedSource2(
                    httpSource,
                    cacheConfig.isEmpty() ? NULL : cacheConfig.string());
        } else {
            // We do not want that prefetching, caching, datasource wrapper
            // in the widevine:// case.
            source = httpSource;
        }

# if CHROMIUM_AVAILABLE
    } else if (!strncasecmp("data:", uri, 5)) {
        source = createDataUriSource(uri);
#endif
    } else {
        // Assume it's a filename.
        source = new FileSource(uri);
    }

    if (source == NULL || source->initCheck() != OK) {
        return NULL;
    }

    return source;
}

String8 DataSource::getMIMEType() const {
    return String8("application/octet-stream");
}

}  // namespace android
