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

//#define LOG_NDEBUG 0
#define LOG_TAG "StagefrightMetadataRetriever"
#include <utils/Log.h>

#include "include/StagefrightMetadataRetriever.h"

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/ColorConverter.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/FileSource.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/OMXCodec.h>
#include <media/stagefright/MediaDefs.h>

#ifndef ANDROID_DEFAULT_CODE
#undef ALOGV
#define ALOGV ALOGD
#endif  //#ifndef ANDROID_DEFAULT_CODE

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
#include <drm/DrmMtkDef.h>
#include <drm/DrmMtkUtil.h>
#include <utils/String8.h>
#endif
#endif //#ifndef ANDROID_DEFAULT_CODE

namespace android {

StagefrightMetadataRetriever::StagefrightMetadataRetriever()
    : mParsedMetaData(false),
      mAlbumArt(NULL) {
    ALOGV("StagefrightMetadataRetriever()");

    DataSource::RegisterDefaultSniffers();
    CHECK_EQ(mClient.connect(), (status_t)OK);
}

StagefrightMetadataRetriever::~StagefrightMetadataRetriever() {
    ALOGV("~StagefrightMetadataRetriever()");

    delete mAlbumArt;
    mAlbumArt = NULL;

    mClient.disconnect();
}

status_t StagefrightMetadataRetriever::setDataSource(
        const char *uri, const KeyedVector<String8, String8> *headers) {
    ALOGV("setDataSource(%s)", uri);

    mParsedMetaData = false;
    mMetaData.clear();
    delete mAlbumArt;
    mAlbumArt = NULL;

    mSource = DataSource::CreateFromURI(uri, headers);

    if (mSource == NULL) {
        ALOGE("Unable to create data source for '%s'.", uri);
        return UNKNOWN_ERROR;
    }

#ifndef ANDROID_DEFAULT_CODE
    const char * sniffedMIME = NULL;
    if ((!strncasecmp("/system/media/audio/", uri, 20)) && (strcasestr(uri,".ogg") != NULL))
    {
         sniffedMIME = MEDIA_MIMETYPE_CONTAINER_OGG;
    }
    mExtractor = MediaExtractor::Create(mSource, sniffedMIME);
#else
    mExtractor = MediaExtractor::Create(mSource);
#endif

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
    // after it attempts to create extractor: for .dcf file with invalid rights,
    //   the mExtractor will be NULL. We need to return OK here directly.
    if ((mSource->flags() & OMADrmFlag) != 0
        || (mExtractor == NULL && DrmMtkUtil::isDcf(String8(uri)))) {
        // we assume it's file path name - for OMA DRM v1
        ALOGD("setDataSource() : it is a OMA DRM v1 .dcf file. return OK");
        return OK;
    }
#endif
#endif //#ifndef ANDROID_DEFAULT_CODE

    if (mExtractor == NULL) {
	        ALOGE("Unable to instantiate an extractor for '%s'.", uri);

	        mSource.clear();
	        return UNKNOWN_ERROR;
    }
#ifndef ANDROID_DEFAULT_CODE
    if (mExtractor->countTracks() == 0) {
	    	ALOGW("Track number is 0");
	        return UNKNOWN_ERROR;
    }
#endif

    return OK;
}

// Warning caller retains ownership of the filedescriptor! Dup it if necessary.
status_t StagefrightMetadataRetriever::setDataSource(
        int fd, int64_t offset, int64_t length) {
    fd = dup(fd);

    ALOGV("setDataSource(%d, %lld, %lld)", fd, offset, length);
#ifndef ANDROID_DEFAULT_CODE
	char buffer[256];
	char linkto[256];
	memset(buffer, 0, 256);
	memset(linkto, 0, 256);
	sprintf(buffer, "/proc/%d/fd/%d", gettid(), fd);
	int len = 0;
	len = readlink(buffer, linkto, sizeof(linkto)-1);
	if(len >= 5)
	{
		linkto[len]=0;
		ALOGD("fd=%d,path=%s",fd,linkto);
	}

#endif

    mParsedMetaData = false;
    mMetaData.clear();
    delete mAlbumArt;
    mAlbumArt = NULL;

    mSource = new FileSource(fd, offset, length);

    status_t err;
    if ((err = mSource->initCheck()) != OK) {
        mSource.clear();
#ifndef ANDROID_DEFAULT_CODE
				ALOGW("mSource initCheck fail err=%d",err);
#endif
        return err;
    }

#ifndef ANDROID_DEFAULT_CODE
		String8 tmp;
		if( mSource->fastsniff(fd, &tmp))
		{
			const char *sniffedMIME = tmp.string();
			mExtractor = MediaExtractor::Create(mSource, sniffedMIME);
		}
		else
#endif
		mExtractor = MediaExtractor::Create(mSource);


#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
	// OMA DRM v1 implementation:
    // after it attempts to create extractor: for .dcf file with invalid rights,
    //   the mExtractor will be NULL. We need to return OK here directly.
    if ((mSource->flags() & OMADrmFlag) != 0
        || (mExtractor == NULL && DrmMtkUtil::isDcf(fd))) {
        ALOGD("setDataSource() : it is a OMA DRM v1 .dcf file. return OK");
        return OK;
    }
#endif
#endif //#ifndef ANDROID_DEFAULT_CODE

    if (mExtractor == NULL) {
        mSource.clear();
#ifndef ANDROID_DEFAULT_CODE
				 ALOGE("Unable to instantiate an extractor for '%d'.", fd);
#endif
        return UNKNOWN_ERROR;
    }

#ifndef ANDROID_DEFAULT_CODE
    if (mExtractor->countTracks() == 0) {
    	ALOGW("Track number is 0");
        return UNKNOWN_ERROR;
    }
#endif

    return OK;
}

static bool isYUV420PlanarSupported(
            OMXClient *client,
            const sp<MetaData> &trackMeta) {

    const char *mime;
    CHECK(trackMeta->findCString(kKeyMIMEType, &mime));

    Vector<CodecCapabilities> caps;
    if (QueryCodecs(client->interface(), mime,
                    true, /* queryDecoders */
                    true, /* hwCodecOnly */
                    &caps) == OK) {

        for (size_t j = 0; j < caps.size(); ++j) {
            CodecCapabilities cap = caps[j];
            for (size_t i = 0; i < cap.mColorFormats.size(); ++i) {
                if (cap.mColorFormats[i] == OMX_COLOR_FormatYUV420Planar) {
                    return true;
                }
            }
        }
    }
    return false;
}

static VideoFrame *extractVideoFrameWithCodecFlags(
        OMXClient *client,
        const sp<MetaData> &trackMeta,
        const sp<MediaSource> &source,
        uint32_t flags,
        int64_t frameTimeUs,
        int seekMode) {
        sp<MetaData> format = source->getFormat();
#ifndef ANDROID_DEFAULT_CODE
#else
    // XXX:
    // Once all vendors support OMX_COLOR_FormatYUV420Planar, we can
    // remove this check and always set the decoder output color format
    if (isYUV420PlanarSupported(client, trackMeta)) {
        format->setInt32(kKeyColorFormat, OMX_COLOR_FormatYUV420Planar);
    }
#endif
    sp<MediaSource> decoder =
        OMXCodec::Create(
                client->interface(), format, false, source,
                NULL, flags | OMXCodec::kClientNeedsFramebuffer);

    if (decoder.get() == NULL) {
        ALOGV("unable to instantiate video decoder.");

        return NULL;
    }

    status_t err = decoder->start();
    if (err != OK) {
        ALOGW("OMXCodec::start returned error %d (0x%08x)\n", err, err);
        return NULL;
    }

    // Read one output buffer, ignore format change notifications
    // and spurious empty buffers.

    MediaSource::ReadOptions options;
    if (seekMode < MediaSource::ReadOptions::SEEK_PREVIOUS_SYNC ||
        seekMode > MediaSource::ReadOptions::SEEK_CLOSEST) {

        ALOGE("Unknown seek mode: %d", seekMode);
        return NULL;
    }

    MediaSource::ReadOptions::SeekMode mode =
            static_cast<MediaSource::ReadOptions::SeekMode>(seekMode);

    int64_t thumbNailTime;
    if (frameTimeUs < 0) {
        if (!trackMeta->findInt64(kKeyThumbnailTime, &thumbNailTime)
                || thumbNailTime < 0) {
            thumbNailTime = 0;
        }
        options.setSeekTo(thumbNailTime, mode);
    } else {
        thumbNailTime = -1;
        options.setSeekTo(frameTimeUs, mode);
    }

    MediaBuffer *buffer = NULL;
    do {
        if (buffer != NULL) {
            buffer->release();
            buffer = NULL;
        }
        err = decoder->read(&buffer, &options);
        options.clearSeekTo();
    } while (err == INFO_FORMAT_CHANGED
             || (buffer != NULL && buffer->range_length() == 0));

    if (err != OK) {
        CHECK(buffer == NULL);

        ALOGV("decoding frame failed.");
        decoder->stop();

        return NULL;
    }

    ALOGV("successfully decoded video frame.");

    int32_t unreadable;
    if (buffer->meta_data()->findInt32(kKeyIsUnreadable, &unreadable)
            && unreadable != 0) {
        ALOGV("video frame is unreadable, decoder does not give us access "
             "to the video data.");

        buffer->release();
        buffer = NULL;

        decoder->stop();

        return NULL;
    }

    int64_t timeUs;
    CHECK(buffer->meta_data()->findInt64(kKeyTime, &timeUs));
    if (thumbNailTime >= 0) {
        if (timeUs != thumbNailTime) {
            const char *mime;
            CHECK(trackMeta->findCString(kKeyMIMEType, &mime));

            ALOGV("thumbNailTime = %lld us, timeUs = %lld us, mime = %s",
                 thumbNailTime, timeUs, mime);
        }
    }

    sp<MetaData> meta = decoder->getFormat();

    int32_t width, height;
    CHECK(meta->findInt32(kKeyWidth, &width));
    CHECK(meta->findInt32(kKeyHeight, &height));
#ifndef ANDROID_DEFAULT_CODE
	int32_t Stridewidth,SliceHeight;
	CHECK(meta->findInt32(kKeyStride, &Stridewidth));
    CHECK(meta->findInt32(kKeySliceHeight, &SliceHeight));
	ALOGD("kKeyWidth=%d,kKeyHeight=%d",width,height);
	ALOGD("Stridewidth=%d,SliceHeight=%d",Stridewidth,SliceHeight);
#endif
/*
#ifndef ANDROID_DEFAULT_CODE
    {
        sp<MetaData> inputFormat = source->getFormat();
        const char *mime;
        if (inputFormat->findCString(kKeyMIMEType, &mime)) {
            ALOGD("width=%d, height=%d", width, height);
            if (strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_VPX)) {//VP8 is software decode, no need to 16 byte align
                width = (width+15) & (~(0xf));
                height = (height+15) & (~(0xf));
            } else { // VP8
#if defined(MT6575)  || defined(MT6577)
                width = (width+15) & (~(0xf));
                height = (height+15) & (~(0xf));
#endif
            }


            ALOGD("Video frame width=%d, height=%d, mime=%s", width, height, mime);
        }
    }
#endif
*/
    int32_t crop_left, crop_top, crop_right, crop_bottom;
    if (!meta->findRect(
                kKeyCropRect,
                &crop_left, &crop_top, &crop_right, &crop_bottom)) {
        crop_left = crop_top = 0;
        crop_right = width - 1;
        crop_bottom = height - 1;
    }

    int32_t rotationAngle;
    if (!trackMeta->findInt32(kKeyRotation, &rotationAngle)) {
        rotationAngle = 0;  // By default, no rotation
    }

    VideoFrame *frame = new VideoFrame;
    frame->mWidth = crop_right - crop_left + 1;
    frame->mHeight = crop_bottom - crop_top + 1;
    frame->mDisplayWidth = frame->mWidth;
    frame->mDisplayHeight = frame->mHeight;

#ifdef MTK_HIGH_QUALITY_THUMBNAIL
    frame->mSize = frame->mWidth * frame->mHeight * 4;
#else
    frame->mSize = frame->mWidth * frame->mHeight * 2;
#endif

    frame->mData = new uint8_t[frame->mSize];
    frame->mRotationAngle = rotationAngle;

    int32_t displayWidth, displayHeight;
    if (meta->findInt32(kKeyDisplayWidth, &displayWidth)) {
        frame->mDisplayWidth = displayWidth;
    }
    if (meta->findInt32(kKeyDisplayHeight, &displayHeight)) {
        frame->mDisplayHeight = displayHeight;
    }

    int32_t srcFormat;
    CHECK(meta->findInt32(kKeyColorFormat, &srcFormat));

#ifndef ANDROID_DEFAULT_CODE
		width=Stridewidth;
		height=SliceHeight;
		crop_right = width - 1;
		crop_bottom = height - 1;
#endif

    int32_t crop_padding_left, crop_padding_top, crop_padding_right, crop_padding_bottom;
    if (!meta->findRect(
        kKeyCropPaddingRect,
        &crop_padding_left, &crop_padding_top, &crop_padding_right, &crop_padding_bottom)) {
        ALOGE("kKeyCropPaddingRect not found\n");
        crop_padding_left = crop_padding_top = 0;
        crop_padding_right = width - 1;
        crop_padding_bottom = height - 1;
    }
    sp<MetaData> inputFormat = source->getFormat();
    const char *mime;
    if (inputFormat->findCString(kKeyMIMEType, &mime)) {
        ALOGD("width=%d, height=%d", width, height);
        if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_VP9)) {
            crop_left = crop_padding_left;
            crop_right = crop_padding_right;
            crop_top = crop_padding_top;
            crop_bottom = crop_padding_bottom;
        }
    }

#ifdef MTK_HIGH_QUALITY_THUMBNAIL
    ColorConverter converter((OMX_COLOR_FORMATTYPE)srcFormat, OMX_COLOR_Format32bitARGB8888);
#else
    ColorConverter converter((OMX_COLOR_FORMATTYPE)srcFormat, OMX_COLOR_Format16bitRGB565);
#endif

    if (converter.isValid()) {
        err = converter.convert(
            (const uint8_t *)buffer->data() + buffer->range_offset(),
            width, height,
            crop_left, crop_top, crop_right, crop_bottom,
            frame->mData,
            frame->mWidth,
            frame->mHeight,
            0, 0, frame->mWidth - 1, frame->mHeight - 1);
    } else {
        ALOGE("Unable to instantiate color conversion from format 0x%08x to "
              "RGB565",
              srcFormat);

        err = ERROR_UNSUPPORTED;
    }

    buffer->release();
    buffer = NULL;

    decoder->stop();

    if (err != OK) {
        ALOGE("Colorconverter failed to convert frame.");

        delete frame;
        frame = NULL;
    }

    return frame;
}

VideoFrame *StagefrightMetadataRetriever::getFrameAtTime(
        int64_t timeUs, int option) {

    ALOGV("getFrameAtTime: %lld us option: %d", timeUs, option);

    if (mExtractor.get() == NULL) {
        ALOGV("no extractor.");
        return NULL;
    }

    sp<MetaData> fileMeta = mExtractor->getMetaData();

    if (fileMeta == NULL) {
        ALOGV("extractor doesn't publish metadata, failed to initialize?");
        return NULL;
    }

    int32_t drm = 0;
    if (fileMeta->findInt32(kKeyIsDRM, &drm) && drm != 0) {
        ALOGE("frame grab not allowed.");
        return NULL;
    }

    size_t n = mExtractor->countTracks();
    size_t i;
    for (i = 0; i < n; ++i) {
        sp<MetaData> meta = mExtractor->getTrackMetaData(i);
#ifndef ANDROID_DEFAULT_CODE
        if(meta.get() == NULL) return NULL;
#endif // #ifndef ANDROID_DEFAULT_CODE
        const char *mime;
#ifndef ANDROID_DEFAULT_CODE
         /* temp workaround, will back to CHECK */
        if(!meta->findCString(kKeyMIMEType, &mime)){
			ALOGE("kKeyMIMEType is not setted");
			return NULL;
        }
#else
        CHECK(meta->findCString(kKeyMIMEType, &mime));
#endif
        if (!strncasecmp(mime, "video/", 6)) {
            break;
        }
    }

    if (i == n) {
        ALOGV("no video track found.");
        return NULL;
    }

    sp<MetaData> trackMeta = mExtractor->getTrackMetaData(
            i, MediaExtractor::kIncludeExtensiveMetaData);

    sp<MediaSource> source = mExtractor->getTrack(i);

    if (source.get() == NULL) {
        ALOGV("unable to instantiate video track.");
        return NULL;
    }

    const void *data;
    uint32_t type;
    size_t dataSize;
    if (fileMeta->findData(kKeyAlbumArt, &type, &data, &dataSize)
            && mAlbumArt == NULL) {
        mAlbumArt = new MediaAlbumArt;
        mAlbumArt->mSize = dataSize;
        mAlbumArt->mData = new uint8_t[dataSize];
        memcpy(mAlbumArt->mData, data, dataSize);
    }

    VideoFrame *frame =
        extractVideoFrameWithCodecFlags(
                &mClient, trackMeta, source, OMXCodec::kPreferSoftwareCodecs,
                timeUs, option);

    if (frame == NULL) {
        ALOGV("Software decoder failed to extract thumbnail, "
             "trying hardware decoder.");

        frame = extractVideoFrameWithCodecFlags(&mClient, trackMeta, source, 0,
                        timeUs, option);
    }

    return frame;
}

MediaAlbumArt *StagefrightMetadataRetriever::extractAlbumArt() {
    ALOGV("extractAlbumArt (extractor: %s)", mExtractor.get() != NULL ? "YES" : "NO");

    if (mExtractor == NULL) {
        return NULL;
    }

    if (!mParsedMetaData) {
        parseMetaData();

        mParsedMetaData = true;
    }

    if (mAlbumArt) {
        return new MediaAlbumArt(*mAlbumArt);
    }

    return NULL;
}

const char *StagefrightMetadataRetriever::extractMetadata(int keyCode) {
    if (mExtractor == NULL) {
        return NULL;
    }

    if (!mParsedMetaData) {
        parseMetaData();

        mParsedMetaData = true;
    }

    ssize_t index = mMetaData.indexOfKey(keyCode);

    if (index < 0) {
        return NULL;
    }

    // const char, return strdup will cause a memory leak
    return mMetaData.valueAt(index).string();

}

void StagefrightMetadataRetriever::parseMetaData() {
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_DRM_APP
    // OMA DRM v1 implementation: NULL extractor means .dcf without valid rights
    if (mExtractor.get() == NULL) {
        ALOGD("Invalid rights for OMA DRM v1 file. NULL extractor and cannot parse meta data.");
        return;
    }
#endif
#endif //#ifndef ANDROID_DEFAULT_CODE

    sp<MetaData> meta = mExtractor->getMetaData();

    if (meta == NULL) {
        ALOGV("extractor doesn't publish metadata, failed to initialize?");
        return;
    }

    struct Map {
        int from;
        int to;
    };
    static const Map kMap[] = {
        { kKeyMIMEType, METADATA_KEY_MIMETYPE },
        { kKeyCDTrackNumber, METADATA_KEY_CD_TRACK_NUMBER },
        { kKeyDiscNumber, METADATA_KEY_DISC_NUMBER },
        { kKeyAlbum, METADATA_KEY_ALBUM },
        { kKeyArtist, METADATA_KEY_ARTIST },
        { kKeyAlbumArtist, METADATA_KEY_ALBUMARTIST },
        { kKeyAuthor, METADATA_KEY_AUTHOR },
        { kKeyComposer, METADATA_KEY_COMPOSER },
        { kKeyDate, METADATA_KEY_DATE },
        { kKeyGenre, METADATA_KEY_GENRE },
        { kKeyTitle, METADATA_KEY_TITLE },
        { kKeyYear, METADATA_KEY_YEAR },
        { kKeyWriter, METADATA_KEY_WRITER },
        { kKeyCompilation, METADATA_KEY_COMPILATION },
        { kKeyLocation, METADATA_KEY_LOCATION },
    };
    static const size_t kNumMapEntries = sizeof(kMap) / sizeof(kMap[0]);

    for (size_t i = 0; i < kNumMapEntries; ++i) {
        const char *value;
        if (meta->findCString(kMap[i].from, &value)) {
            mMetaData.add(kMap[i].to, String8(value));
        }
    }

    const void *data;
    uint32_t type;
    size_t dataSize;
    if (meta->findData(kKeyAlbumArt, &type, &data, &dataSize)
            && mAlbumArt == NULL) {
        mAlbumArt = new MediaAlbumArt;
        mAlbumArt->mSize = dataSize;
        mAlbumArt->mData = new uint8_t[dataSize];
        memcpy(mAlbumArt->mData, data, dataSize);
    }

    size_t numTracks = mExtractor->countTracks();

    char tmp[32];
    sprintf(tmp, "%d", numTracks);

    mMetaData.add(METADATA_KEY_NUM_TRACKS, String8(tmp));

    bool hasAudio = false;
    bool hasVideo = false;
    int32_t videoWidth = -1;
    int32_t videoHeight = -1;
    int32_t audioBitrate = -1;
	int32_t rotationAngle = -1;

#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_S3D_SUPPORT
	//add for check stereo 3D
	int32_t video_3d_mode = 	VIDEO_STEREO_2D;
	int32_t is_livephoto = 0;	
#endif
	int32_t slowmotion_speed = 0;
#endif

    // The overall duration is the duration of the longest track.
    int64_t maxDurationUs = 0;
    String8 timedTextLang;
    for (size_t i = 0; i < numTracks; ++i) {
        sp<MetaData> trackMeta = mExtractor->getTrackMetaData(i);

#ifndef ANDROID_DEFAULT_CODE
        if (trackMeta.get() == NULL) return ;
#endif // #ifndef ANDROID_DEFAULT_CODE
        int64_t durationUs;
        if (trackMeta->findInt64(kKeyDuration, &durationUs)) {
            if (durationUs > maxDurationUs) {
                maxDurationUs = durationUs;
            }
        }

        const char *mime;
        if (trackMeta->findCString(kKeyMIMEType, &mime)) {
            if (!hasAudio && !strncasecmp("audio/", mime, 6)) {
                hasAudio = true;

                if (!trackMeta->findInt32(kKeyBitRate, &audioBitrate)) {
                    audioBitrate = -1;
                }
            } else if (!hasVideo && !strncasecmp("video/", mime, 6)) {
                hasVideo = true;

                CHECK(trackMeta->findInt32(kKeyWidth, &videoWidth));
                CHECK(trackMeta->findInt32(kKeyHeight, &videoHeight));
				 if (!trackMeta->findInt32(kKeyRotation, &rotationAngle)) {
                    rotationAngle = 0;
                }
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_S3D_SUPPORT
				//check whether video is 3D from parser
				trackMeta->findInt32(kKeyVideoStereoMode,&video_3d_mode);
				ALOGD("parseMetaData: video 3d mode=%d",video_3d_mode);
				trackMeta->findInt32(kKeyIsLivePhoto,&is_livephoto);
				ALOGD("parseMetaData: kKeyIsLivePhoto =%s",is_livephoto==false?"false":"true");
#endif
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT			
				trackMeta->findInt32(kKeySlowMotionSpeedValue,&slowmotion_speed);
				ALOGD("parseMetaData: slowmotion_speed=%d",slowmotion_speed);
#endif

#endif
            } else if (!strcasecmp(mime, MEDIA_MIMETYPE_TEXT_3GPP)) {
                const char *lang;
                trackMeta->findCString(kKeyMediaLanguage, &lang);
                timedTextLang.append(String8(lang));
                timedTextLang.append(String8(":"));
            }
        }
    }

    // To save the language codes for all timed text tracks
    // If multiple text tracks present, the format will look
    // like "eng:chi"
    if (!timedTextLang.isEmpty()) {
        mMetaData.add(METADATA_KEY_TIMED_TEXT_LANGUAGES, timedTextLang);
    }

    // The duration value is a string representing the duration in ms.
    sprintf(tmp, "%lld", (maxDurationUs + 500) / 1000);
    mMetaData.add(METADATA_KEY_DURATION, String8(tmp));

    if (hasAudio) {
        mMetaData.add(METADATA_KEY_HAS_AUDIO, String8("yes"));
    }

    if (hasVideo) {
        mMetaData.add(METADATA_KEY_HAS_VIDEO, String8("yes"));

        sprintf(tmp, "%d", videoWidth);
        mMetaData.add(METADATA_KEY_VIDEO_WIDTH, String8(tmp));

        sprintf(tmp, "%d", videoHeight);
        mMetaData.add(METADATA_KEY_VIDEO_HEIGHT, String8(tmp));

		sprintf(tmp, "%d", rotationAngle);
        mMetaData.add(METADATA_KEY_VIDEO_ROTATION, String8(tmp));
#ifndef ANDROID_DEFAULT_CODE
#ifdef MTK_S3D_SUPPORT
		sprintf(tmp,"%d",video_3d_mode);
		mMetaData.add(METADATA_KEY_STEREO_3D, String8(tmp));
		sprintf(tmp,"%d",is_livephoto);
		mMetaData.add(METADATA_KEY_Is_LivePhoto, String8(tmp));
#endif
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT		
//		sprintf(tmp,"%d",slowmotion_speed);
		if(slowmotion_speed > 0)
			sprintf(tmp,"%d",4);
		else
			sprintf(tmp,"%d",0);
		mMetaData.add(METADATA_KEY_SlowMotion_SpeedValue, String8(tmp));	
#endif
#endif
    }

    if (numTracks == 1 && hasAudio && audioBitrate >= 0) {
        sprintf(tmp, "%d", audioBitrate);
        mMetaData.add(METADATA_KEY_BITRATE, String8(tmp));
    } else {
        off64_t sourceSize;
        if (mSource->getSize(&sourceSize) == OK) {
            int64_t avgBitRate = (int64_t)(sourceSize * 8E6 / maxDurationUs);

            sprintf(tmp, "%lld", avgBitRate);
            mMetaData.add(METADATA_KEY_BITRATE, String8(tmp));
        }
    }

    if (numTracks == 1) {
        const char *fileMIME;
        CHECK(meta->findCString(kKeyMIMEType, &fileMIME));

        if (!strcasecmp(fileMIME, "video/x-matroska")) {
            sp<MetaData> trackMeta = mExtractor->getTrackMetaData(0);
#ifndef ANDROID_DEFAULT_CODE
            if (trackMeta.get() == NULL) return ;
#endif // #ifndef ANDROID_DEFAULT_CODE
            const char *trackMIME;
            CHECK(trackMeta->findCString(kKeyMIMEType, &trackMIME));

            if (!strncasecmp("audio/", trackMIME, 6)) {
                // The matroska file only contains a single audio track,
                // rewrite its mime type.
                mMetaData.add(
                        METADATA_KEY_MIMETYPE, String8("audio/x-matroska"));
            }
        }
    }

    // To check whether the media file is drm-protected
    if (mExtractor->getDrmFlag()) {
        mMetaData.add(METADATA_KEY_IS_DRM, String8("1"));
    }
}

}  // namespace android
