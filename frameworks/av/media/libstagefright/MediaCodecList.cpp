/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaCodecList"
#include <utils/Log.h>

#include <media/stagefright/MediaCodecList.h>

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/OMXClient.h>
#include <media/stagefright/OMXCodec.h>
#include <utils/threads.h>

#include <libexpat/expat.h>
#ifndef ANDROID_DEFAULT_CODE
#include <sys/sysconf.h>
#endif //ANDROID_DEFAULT_CODE

namespace android {

static Mutex sInitMutex;

// static
MediaCodecList *MediaCodecList::sCodecList;

// static
const MediaCodecList *MediaCodecList::getInstance() {
    Mutex::Autolock autoLock(sInitMutex);

    if (sCodecList == NULL) {
        sCodecList = new MediaCodecList;
    }

    return sCodecList->initCheck() == OK ? sCodecList : NULL;
}

MediaCodecList::MediaCodecList()
    : mInitCheck(NO_INIT) {
    FILE *file = fopen("/etc/media_codecs.xml", "r");

    if (file == NULL) {
        ALOGW("unable to open media codecs configuration xml file.");
        return;
    }

    parseXMLFile(file);

    if (mInitCheck == OK) {
        // These are currently still used by the video editing suite.

        addMediaCodec(true /* encoder */, "AACEncoder", "audio/mp4a-latm");

        addMediaCodec(
                false /* encoder */, "OMX.google.raw.decoder", "audio/raw");
    }
	
	// for CTS 
#ifndef ANDROID_DEFAULT_CODE
#ifndef MTK_WMA_PLAYBACK_SUPPORT
		ALOGD("WMA BUILD OPTION IS CLOSED");
		const char* wma_name2 = "OMX.MTK.AUDIO.DECODER.WMA";
		size_t indexForWma2 = findCodecByName(wma_name2);
		if(indexForWma2 >= 0)
			mCodecInfos.removeAt(indexForWma2);
#endif
#ifndef MTK_WMA_PLAYBACK_SUPPORT
		ALOGD("ASF BUILD OPTION IS CLOSED");
		const char* wma_name = "OMX.MTK.AUDIO.DECODER.WMA";
		size_t indexForWma = findCodecByName(wma_name);
		if(indexForWma >= 0)
			mCodecInfos.removeAt(indexForWma);
#endif
#ifndef MTK_SWIP_WMAPRO
		ALOGD("WMA PRO BUILD OPTION IS CLOSED");
		const char* wmapro_name = "OMX.MTK.AUDIO.DECODER.WMAPRO";
		size_t indexForWmapro = findCodecByName(wmapro_name);
		if(indexForWmapro >= 0)
			mCodecInfos.removeAt(indexForWmapro);
#endif
#ifndef MTK_AUDIO_RAW_SUPPORT
		ALOGD("PCM Component BUILD OPTION IS CLOSED");
		const char* pcm_name = "OMX.MTK.AUDIO.DECODER.RAW";
		size_t indexForRaw = findCodecByName(pcm_name);
		if(indexForRaw >= 0)
			mCodecInfos.removeAt(indexForRaw);
#endif
#ifndef MTK_AUDIO_DDPLUS_SUPPORT
        ALOGD("DDPLUS Component BUILD OPTION IS CLOSED");
        for (;;)
        {
            ssize_t indexForDDPus = findCodecByName("OMX.MTK.AUDIO.DECODER.DDPLUS");
            if (indexForDDPus < 0)
                break;
            mCodecInfos.removeAt(indexForDDPus);
        }
#endif
#ifndef MTK_AUDIO_APE_SUPPORT
	ALOGD("APE Component BUILD OPTION IS CLOSED");
	const char* ape_name = "OMX.MTK.AUDIO.DECODER.APE";
	size_t indexForApe = findCodecByName(ape_name);
	if(indexForApe >= 0)
		mCodecInfos.removeAt(indexForApe);
#endif
#endif

#if 0
    for (size_t i = 0; i < mCodecInfos.size(); ++i) {
        const CodecInfo &info = mCodecInfos.itemAt(i);

        AString line = info.mName;
        line.append(" supports ");
        for (size_t j = 0; j < mTypes.size(); ++j) {
            uint32_t value = mTypes.valueAt(j);

            if (info.mTypes & (1ul << value)) {
                line.append(mTypes.keyAt(j));
                line.append(" ");
            }
        }

        ALOGI("%s", line.c_str());
    }
#endif

    fclose(file);
    file = NULL;
}

MediaCodecList::~MediaCodecList() {
}

status_t MediaCodecList::initCheck() const {
    return mInitCheck;
}

void MediaCodecList::parseXMLFile(FILE *file) {
    mInitCheck = OK;
    mCurrentSection = SECTION_TOPLEVEL;
    mDepth = 0;

    XML_Parser parser = ::XML_ParserCreate(NULL);
    CHECK(parser != NULL);

    ::XML_SetUserData(parser, this);
    ::XML_SetElementHandler(
            parser, StartElementHandlerWrapper, EndElementHandlerWrapper);

    const int BUFF_SIZE = 512;
    while (mInitCheck == OK) {
        void *buff = ::XML_GetBuffer(parser, BUFF_SIZE);
        if (buff == NULL) {
            ALOGE("failed to in call to XML_GetBuffer()");
            mInitCheck = UNKNOWN_ERROR;
            break;
        }

        int bytes_read = ::fread(buff, 1, BUFF_SIZE, file);
        if (bytes_read < 0) {
            ALOGE("failed in call to read");
            mInitCheck = ERROR_IO;
            break;
        }

        if (::XML_ParseBuffer(parser, bytes_read, bytes_read == 0)
                != XML_STATUS_OK) {
            mInitCheck = ERROR_MALFORMED;
            break;
        }

        if (bytes_read == 0) {
            break;
        }
    }

    ::XML_ParserFree(parser);

    if (mInitCheck == OK) {
        for (size_t i = mCodecInfos.size(); i-- > 0;) {
            CodecInfo *info = &mCodecInfos.editItemAt(i);

            if (info->mTypes == 0) {
                // No types supported by this component???

                ALOGW("Component %s does not support any type of media?",
                      info->mName.c_str());

                mCodecInfos.removeAt(i);
            }
        }
    }

    if (mInitCheck != OK) {
        mCodecInfos.clear();
        mCodecQuirks.clear();
    }
}

// static
void MediaCodecList::StartElementHandlerWrapper(
        void *me, const char *name, const char **attrs) {
    static_cast<MediaCodecList *>(me)->startElementHandler(name, attrs);
}

// static
void MediaCodecList::EndElementHandlerWrapper(void *me, const char *name) {
    static_cast<MediaCodecList *>(me)->endElementHandler(name);
}

void MediaCodecList::startElementHandler(
        const char *name, const char **attrs) {
    if (mInitCheck != OK) {
        return;
    }

    switch (mCurrentSection) {
        case SECTION_TOPLEVEL:
        {
            if (!strcmp(name, "Decoders")) {
                mCurrentSection = SECTION_DECODERS;
            } else if (!strcmp(name, "Encoders")) {
                mCurrentSection = SECTION_ENCODERS;
            }
            break;
        }

        case SECTION_DECODERS:
        {
            if (!strcmp(name, "MediaCodec")) {
                mInitCheck =
                    addMediaCodecFromAttributes(false /* encoder */, attrs);

                mCurrentSection = SECTION_DECODER;
            }
            break;
        }

        case SECTION_ENCODERS:
        {
            if (!strcmp(name, "MediaCodec")) {
                mInitCheck =
                    addMediaCodecFromAttributes(true /* encoder */, attrs);

                mCurrentSection = SECTION_ENCODER;
            }
            break;
        }

        case SECTION_DECODER:
        case SECTION_ENCODER:
        {
            if (!strcmp(name, "Quirk")) {
                mInitCheck = addQuirk(attrs);
            } else if (!strcmp(name, "Type")) {
                mInitCheck = addTypeFromAttributes(attrs);
            }
            break;
        }

        default:
            break;
    }

    ++mDepth;
}

void MediaCodecList::endElementHandler(const char *name) {
    if (mInitCheck != OK) {
        return;
    }

    switch (mCurrentSection) {
        case SECTION_DECODERS:
        {
            if (!strcmp(name, "Decoders")) {
                mCurrentSection = SECTION_TOPLEVEL;
            }
            break;
        }

        case SECTION_ENCODERS:
        {
            if (!strcmp(name, "Encoders")) {
                mCurrentSection = SECTION_TOPLEVEL;
            }
            break;
        }

        case SECTION_DECODER:
        {
            if (!strcmp(name, "MediaCodec")) {
                mCurrentSection = SECTION_DECODERS;
            }
            break;
        }

        case SECTION_ENCODER:
        {
            if (!strcmp(name, "MediaCodec")) {
                mCurrentSection = SECTION_ENCODERS;
            }
            break;
        }

        default:
            break;
    }

    --mDepth;
}

status_t MediaCodecList::addMediaCodecFromAttributes(
        bool encoder, const char **attrs) {
    const char *name = NULL;
    const char *type = NULL;

    size_t i = 0;
    while (attrs[i] != NULL) {
        if (!strcmp(attrs[i], "name")) {
            if (attrs[i + 1] == NULL) {
                return -EINVAL;
            }
            name = attrs[i + 1];
            ++i;
        } else if (!strcmp(attrs[i], "type")) {
            if (attrs[i + 1] == NULL) {
                return -EINVAL;
            }
            type = attrs[i + 1];
            ++i;
        } else {
            return -EINVAL;
        }

        ++i;
    }

    if (name == NULL) {
        return -EINVAL;
    }

    addMediaCodec(encoder, name, type);

    return OK;
}

void MediaCodecList::addMediaCodec(
        bool encoder, const char *name, const char *type) {
    mCodecInfos.push();
    CodecInfo *info = &mCodecInfos.editItemAt(mCodecInfos.size() - 1);
    info->mName = name;
    info->mIsEncoder = encoder;
    info->mTypes = 0;
    info->mQuirks = 0;

    if (type != NULL) {
        addType(type);
    }
}

status_t MediaCodecList::addQuirk(const char **attrs) {
    const char *name = NULL;

    size_t i = 0;
    while (attrs[i] != NULL) {
        if (!strcmp(attrs[i], "name")) {
            if (attrs[i + 1] == NULL) {
                return -EINVAL;
            }
            name = attrs[i + 1];
            ++i;
        } else {
            return -EINVAL;
        }

        ++i;
    }

    if (name == NULL) {
        return -EINVAL;
    }

    uint32_t bit;
    ssize_t index = mCodecQuirks.indexOfKey(name);
    if (index < 0) {
        bit = mCodecQuirks.size();

        if (bit == 32) {
            ALOGW("Too many distinct quirk names in configuration.");
            return OK;
        }

        mCodecQuirks.add(name, bit);
    } else {
        bit = mCodecQuirks.valueAt(index);
    }

    CodecInfo *info = &mCodecInfos.editItemAt(mCodecInfos.size() - 1);
    info->mQuirks |= 1ul << bit;

    return OK;
}

status_t MediaCodecList::addTypeFromAttributes(const char **attrs) {
    const char *name = NULL;

    size_t i = 0;
    while (attrs[i] != NULL) {
        if (!strcmp(attrs[i], "name")) {
            if (attrs[i + 1] == NULL) {
                return -EINVAL;
            }
            name = attrs[i + 1];
            ++i;
        } else {
            return -EINVAL;
        }

        ++i;
    }

    if (name == NULL) {
        return -EINVAL;
    }

    addType(name);

    return OK;
}

void MediaCodecList::addType(const char *name) {
    uint32_t bit;
    ssize_t index = mTypes.indexOfKey(name);
    if (index < 0) {
        bit = mTypes.size();

        if (bit == 32) {
            ALOGW("Too many distinct type names in configuration.");
            return;
        }

        mTypes.add(name, bit);
    } else {
        bit = mTypes.valueAt(index);
    }

    CodecInfo *info = &mCodecInfos.editItemAt(mCodecInfos.size() - 1);
    info->mTypes |= 1ul << bit;
}

ssize_t MediaCodecList::findCodecByType(
        const char *type, bool encoder, size_t startIndex) const {
    ssize_t typeIndex = mTypes.indexOfKey(type);

    if (typeIndex < 0) {
        return -ENOENT;
    }

    uint32_t typeMask = 1ul << mTypes.valueAt(typeIndex);

    while (startIndex < mCodecInfos.size()) {
        const CodecInfo &info = mCodecInfos.itemAt(startIndex);

        if (info.mIsEncoder == encoder && (info.mTypes & typeMask)) {
            return startIndex;
        }

        ++startIndex;
    }

    return -ENOENT;
}

ssize_t MediaCodecList::findCodecByName(const char *name) const {
    for (size_t i = 0; i < mCodecInfos.size(); ++i) {
        const CodecInfo &info = mCodecInfos.itemAt(i);

        if (info.mName == name) {
            return i;
        }
    }

    return -ENOENT;
}

size_t MediaCodecList::countCodecs() const {
    return mCodecInfos.size();
}

const char *MediaCodecList::getCodecName(size_t index) const {
    if (index >= mCodecInfos.size()) {
        return NULL;
    }

    const CodecInfo &info = mCodecInfos.itemAt(index);
    return info.mName.c_str();
}

bool MediaCodecList::isEncoder(size_t index) const {
    if (index >= mCodecInfos.size()) {
        return NULL;
    }

    const CodecInfo &info = mCodecInfos.itemAt(index);
    return info.mIsEncoder;
}

bool MediaCodecList::codecHasQuirk(
        size_t index, const char *quirkName) const {
    if (index >= mCodecInfos.size()) {
        return NULL;
    }

    const CodecInfo &info = mCodecInfos.itemAt(index);

    if (info.mQuirks != 0) {
        ssize_t index = mCodecQuirks.indexOfKey(quirkName);
        if (index >= 0 && info.mQuirks & (1ul << mCodecQuirks.valueAt(index))) {
            return true;
        }
    }

    return false;
}

status_t MediaCodecList::getSupportedTypes(
        size_t index, Vector<AString> *types) const {
    types->clear();

    if (index >= mCodecInfos.size()) {
        return -ERANGE;
    }

    const CodecInfo &info = mCodecInfos.itemAt(index);

    for (size_t i = 0; i < mTypes.size(); ++i) {
        uint32_t typeMask = 1ul << mTypes.valueAt(i);

        if (info.mTypes & typeMask) {
            types->push(mTypes.keyAt(i));
        }
    }

    return OK;
}

status_t MediaCodecList::getCodecCapabilities(
        size_t index, const char *type,
        Vector<ProfileLevel> *profileLevels,
        Vector<uint32_t> *colorFormats,
        uint32_t *flags) const {
    profileLevels->clear();
    colorFormats->clear();

#ifndef ANDROID_DEFAULT_CODE
	ALOGI("[%s][ index=%d, mCodecInfos.size()=%d] ",__FUNCTION__,index,mCodecInfos.size() );
#endif	

    if (index >= mCodecInfos.size()) {
        return -ERANGE;
    }

    const CodecInfo &info = mCodecInfos.itemAt(index);

    OMXClient client;
    status_t err = client.connect();
    if (err != OK) {
#ifndef ANDROID_DEFAULT_CODE
	ALOGI("[%s][ err1=%d  ] ",__FUNCTION__,err );
#endif			
        return err;
    }

#ifndef ANDROID_DEFAULT_CODE
	ALOGI("[%s][ connect OK ] ",__FUNCTION__ );
#endif

    CodecCapabilities caps;
    err = QueryCodec(
            client.interface(),
            info.mName.c_str(), type, info.mIsEncoder, &caps);

    if (err != OK) {
#ifndef ANDROID_DEFAULT_CODE
    ALOGI("[%s][ err2=%d ] ",__FUNCTION__,err );
#endif
        return err;
    }

#ifndef ANDROID_DEFAULT_CODE
    ALOGI("[%s][ QueryCodec OK ] ",__FUNCTION__ );
    int memTotalBytes = sysconf(_SC_PHYS_PAGES) * PAGE_SIZE;
    ALOGD("native_get_videoeditor_profile: mIsEncoder %d, memTotalBytes %d bytes", info.mIsEncoder, memTotalBytes);
#endif //ANDROID_DEFAULT_CODE

    for (size_t i = 0; i < caps.mProfileLevels.size(); ++i) {
        const CodecProfileLevel &src = caps.mProfileLevels.itemAt(i);

        ProfileLevel profileLevel;
        profileLevel.mProfile = src.mProfile;
        profileLevel.mLevel = src.mLevel;
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("mProfile %d mLevel %d", src.mProfile, src.mLevel);
        //for CTS case "EncodeVirtualDisplayWithCompositionTest "
        // Limit max recording resolution to 1280x720, if phone's ram <= 512MB
        if (memTotalBytes <= (512*1024*1024)) {
            if( (1==info.mIsEncoder)&& (OMX_VIDEO_AVCLevel4<=src.mLevel) )
            {
                ALOGD("skip once, memory may no be enough during large size video recording", src.mProfile, src.mLevel);
                continue;
            }
        }
#endif //ANDROID_DEFAULT_CODE
        profileLevels->push(profileLevel);
    }

    for (size_t i = 0; i < caps.mColorFormats.size(); ++i) {
#ifndef ANDROID_DEFAULT_CODE
    //for CTS case "com.android.cts.videoperf.VideoEncoderDecoderTest "
    //push one more format(YUV420) if there is under the decoding and with MTKBLK or MTKYV12 format
    if( (0==info.mIsEncoder)&&( OMX_COLOR_FormatVendorMTKYUV==caps.mColorFormats.itemAt(i) || 
        OMX_MTK_COLOR_FormatYV12==caps.mColorFormats.itemAt(i) || OMX_COLOR_FormatVendorMTKYUV_FCM==caps.mColorFormats.itemAt(i) ) )
    {
        colorFormats->push(OMX_COLOR_FormatYUV420Planar);
        ALOGI("itemAt(i) %x, isEncoder %d ", caps.mColorFormats.itemAt(i), info.mIsEncoder );
    }
#endif //ANDROID_DEFAULT_CODE
        colorFormats->push(caps.mColorFormats.itemAt(i));
    }

    *flags = caps.mFlags;
#ifndef ANDROID_DEFAULT_CODE
    ALOGI("[%s][ OK ] ",__FUNCTION__  );
#endif
    return OK;
}

}  // namespace android
