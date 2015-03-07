/*
 * Copyright (C) 2010 The Android Open Source Project
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
#define LOG_TAG "avc_utils"
#include <utils/Log.h>

#include "include/avc_utils.h"

#include <media/stagefright/foundation/ABitReader.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>

namespace android {
#ifndef ANDROID_DEFAULT_CODE 	
signed parseSE(ABitReader *br) {
	unsigned numZeroes = 0;
	while (br->getBits(1) == 0) {
		++numZeroes;
	}

	unsigned x = br->getBits(numZeroes);

	x += (1 << numZeroes);

	return (x & 1) ? -(signed)(x >> 1) : (signed)(x >> 1);
}

void scaling_list(int32_t sizeOfScalingList, ABitReader *br) {
	int lastScale = 8;
	int nextScale = 8;
	for( int j = 0; j < sizeOfScalingList; j++ ) {
	if( nextScale != 0 ) {
		int32_t delta_scale = parseSE(br);
		nextScale = ( lastScale + delta_scale + 256 ) % 256;
	}
	lastScale = (nextScale == 0) ? lastScale : nextScale;
	}
}
   
void parse_seq_scaling_matrix_present(ABitReader *br) {
	for(int i = 0; i < 8; i++ ) {
	uint32_t seq_scaling_list_present_flag =  br->getBits(1);
		ALOGV("seq_scaling_list_presetn_flag :%d", seq_scaling_list_present_flag);
	if(seq_scaling_list_present_flag) {
		if(i < 6)
		scaling_list(16, br);
		else
		scaling_list(64, br);
	}
	}
}

status_t FindAVCSPSInfo(
		uint8_t *seqParamSet, size_t size, struct SPSInfo *pSPSInfo) {

	if (pSPSInfo == NULL)
	{
	ALOGE("pSPSInfo == NULL");
		return -EINVAL;
	}

	ABitReader br(seqParamSet + 1, size - 1);

	unsigned profile_idc = br.getBits(8);
	pSPSInfo->profile = profile_idc;
	br.skipBits(8);

	pSPSInfo->level = br.getBits(8);
	parseUE(&br);  // seq_parameter_set_id

	unsigned chroma_format_idc = 1;  // 4:2:0 chroma format

	if (profile_idc == 100 || profile_idc == 110
			|| profile_idc == 122 || profile_idc == 244
			|| profile_idc == 44 || profile_idc == 83 || profile_idc == 86) {
		chroma_format_idc = parseUE(&br);
		if (chroma_format_idc == 3) {
			br.skipBits(1);  // residual_colour_transform_flag
		}
		parseUE(&br);  // bit_depth_luma_minus8
		parseUE(&br);  // bit_depth_chroma_minus8
		br.skipBits(1);  // qpprime_y_zero_transform_bypass_flag
		//CHECK_EQ(br.getBits(1), 0u);	// seq_scaling_matrix_present_flag
	if (br.getBits(1) != 0)
	{
		ALOGW("seq_scaling_matrix_present_flag != 0");
			parse_seq_scaling_matrix_present(&br);
	}
	}

	parseUE(&br);  // log2_max_frame_num_minus4
	unsigned pic_order_cnt_type = parseUE(&br);

	if (pic_order_cnt_type == 0) {
		parseUE(&br);  // log2_max_pic_order_cnt_lsb_minus4
	} else if (pic_order_cnt_type == 1) {
		// offset_for_non_ref_pic, offset_for_top_to_bottom_field and
		// offset_for_ref_frame are technically se(v), but since we are
		// just skipping over them the midpoint does not matter.

		br.getBits(1);	// delta_pic_order_always_zero_flag
		parseUE(&br);  // offset_for_non_ref_pic
		parseUE(&br);  // offset_for_top_to_bottom_field

		unsigned num_ref_frames_in_pic_order_cnt_cycle = parseUE(&br);
		for (unsigned i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; ++i) {
			parseUE(&br);  // offset_for_ref_frame
		}
	}

	parseUE(&br);  // num_ref_frames
	br.getBits(1);	// gaps_in_frame_num_value_allowed_flag

	unsigned pic_width_in_mbs_minus1 = parseUE(&br);
	unsigned pic_height_in_map_units_minus1 = parseUE(&br);
	unsigned frame_mbs_only_flag = br.getBits(1);

	pSPSInfo->width = pic_width_in_mbs_minus1 * 16 + 16;

	pSPSInfo->height = (2 - frame_mbs_only_flag)
		* (pic_height_in_map_units_minus1 * 16 + 16);

	if (!frame_mbs_only_flag) {
		br.getBits(1);	// mb_adaptive_frame_field_flag
	}

	br.getBits(1);	// direct_8x8_inference_flag

	if (br.getBits(1)) {  // frame_cropping_flag
		unsigned frame_crop_left_offset = parseUE(&br);
		unsigned frame_crop_right_offset = parseUE(&br);
		unsigned frame_crop_top_offset = parseUE(&br);
		unsigned frame_crop_bottom_offset = parseUE(&br);

		unsigned cropUnitX, cropUnitY;
		if (chroma_format_idc == 0	/* monochrome */) {
			cropUnitX = 1;
			cropUnitY = 2 - frame_mbs_only_flag;
		} else {
			unsigned subWidthC = (chroma_format_idc == 3) ? 1 : 2;
			unsigned subHeightC = (chroma_format_idc == 1) ? 2 : 1;

			cropUnitX = subWidthC;
			cropUnitY = subHeightC * (2 - frame_mbs_only_flag);
		}

	   ALOGV("frame_crop = (%u, %u, %u, %u), cropUnitX = %u, cropUnitY = %u",
			 frame_crop_left_offset, frame_crop_right_offset,
			 frame_crop_top_offset, frame_crop_bottom_offset,
			 cropUnitX, cropUnitY);

		pSPSInfo->width -=
			(frame_crop_left_offset + frame_crop_right_offset) * cropUnitX;
		pSPSInfo->height -=
			(frame_crop_top_offset + frame_crop_bottom_offset) * cropUnitY;
	}

	return OK;
}
#ifdef MTK_VIDEO_HEVC_SUPPORT
// Determine video dimensions from the sequence parameterset.
void FindHEVCDimensions(
        const sp<ABuffer> &seqParamSet, int32_t *width, int32_t *height) {
	ALOGI("FindHEVCDimensions ++");
    ABitReader br(seqParamSet->data() + 2, seqParamSet->size() - 1);

	 br.skipBits(4); //sps_video_parameter_set_id;
	 unsigned sps_max_sub_layers_minus1 = br.getBits(3);
	 ALOGI("sps_max_sub_layers_minus1 =%d",sps_max_sub_layers_minus1);
	 br.skipBits(1); //sps_temporal_id_nesting_flag;

	 /*-----------profile_tier_level start-----------------------*/
	 
	 br.skipBits(3);//general_profile_spac, general_tier_flag
	 
	 unsigned general_profile_idc = br.getBits(5);
	 ALOGI("general_profile_idc =%d",general_profile_idc);
	 br.skipBits(32);//general_profile_compatibility_flag

	 br.skipBits(48); //general_constraint_indicator_flags

	 unsigned general_level_idc = br.getBits(8);
	 ALOGI("general_level_idc =%d",general_level_idc);

	 uint8_t sub_layer_profile_present_flag[sps_max_sub_layers_minus1]; 
	 uint8_t sub_layer_level_present_flag[sps_max_sub_layers_minus1]; 
	 for(int i = 0; i < sps_max_sub_layers_minus1; i++){
	 	sub_layer_profile_present_flag[i] = br.getBits(1);
		sub_layer_level_present_flag[i] = br.getBits(1);
	 }

	 if(sps_max_sub_layers_minus1 > 0) {
		for(int j = sps_max_sub_layers_minus1; j < 8; j++){
			br.skipBits(2);
		}	
	 }
	 for(int i = 0; i < sps_max_sub_layers_minus1; i++){
		if(sub_layer_profile_present_flag[i]){
			br.skipBits(2); //sub_layer_profile_space
			br.skipBits(1); //sub_layer_tier_flag
			br.skipBits(5); //sub_layer_profile_idc
			br.skipBits(32); //sub_layer_profile_compatibility_flag
			br.skipBits(48); //sub_layer_constraint_indicator_flags
		}
		if(sub_layer_level_present_flag[i]){
			br.skipBits(8); //sub_layer_level_idc
		}
		
	 }
	 /*-----------profile_tier_level done-----------------------*/
	
	 parseUE(&br); // sps_seq_parameter_set_id
	 unsigned chroma_format_idc,separate_colour_plane_flag;
	 chroma_format_idc = parseUE(&br);
	 ALOGI("chroma_format_idc=%d",chroma_format_idc);
	 
	if(chroma_format_idc == 3){
		separate_colour_plane_flag = br.getBits(1);
	}

	int32_t pic_width_in_luma_samples = parseUE(&br);
	int32_t pic_height_in_luma_samples = parseUE(&br);
	ALOGI("pic_width_in_luma_samples =%d",pic_width_in_luma_samples);
	ALOGI("pic_height_in_luma_samples =%d",pic_height_in_luma_samples);

	*width = pic_width_in_luma_samples;
	*height = pic_height_in_luma_samples;
	uint8_t conformance_window_flag = br.getBits(1);
	ALOGI("conformance_window_flag =%d",conformance_window_flag);
	if(conformance_window_flag){
		unsigned conf_win_left_offset = parseUE(&br);
		unsigned conf_win_right_offset = parseUE(&br);
		unsigned conf_win_top_offset = parseUE(&br);
		unsigned conf_win_bottom_offset = parseUE(&br);

		*width -= conf_win_left_offset + conf_win_right_offset;
		*height -= conf_win_top_offset + conf_win_bottom_offset;
		
		ALOGI("frame_crop = (%u, %u, %u, %u)",
				 conf_win_left_offset, conf_win_right_offset,
				 conf_win_top_offset, conf_win_bottom_offset);
	}

	unsigned bit_depth_luma_minus8 = parseUE(&br);
	unsigned bit_depth_chroma_minus8 = parseUE(&br);
	ALOGI("bit_depth_luma_minus8 =%u,bit_depth_chroma_minus8 =%u",bit_depth_luma_minus8,bit_depth_chroma_minus8);	
	ALOGI("FindHEVCDimensions --");

}
static sp<ABuffer> FindHEVCNAL(
        const uint8_t *data, size_t size, unsigned nalType,
        size_t *stopOffset) {
    const uint8_t *nalStart;
    size_t nalSize;
    while (getNextNALUnit(&data, &size, &nalStart, &nalSize, true) == OK) {
        if (((nalStart[0] >> 1) & 0x3f) == nalType) {
            sp<ABuffer> buffer = new ABuffer(nalSize);
            memcpy(buffer->data(), nalStart, nalSize);
            return buffer;
        }
    }

    return NULL;
}
const char *HEVCProfileToString(uint8_t profile) {
    switch (profile) {
        case kHEVCProfileMain:
            return "Main Profile";
        case kHEVCProfileMain10:
            return "Main 10 profile";
        case kHEVCProfileMainStillPicture:
            return "Main Still Picture profile";
        default:   
			return "Unknown";
    }
}
#endif
#endif

unsigned parseUE(ABitReader *br) {
    unsigned numZeroes = 0;
    while (br->getBits(1) == 0) {
        ++numZeroes;
    }

    unsigned x = br->getBits(numZeroes);

    return x + (1u << numZeroes) - 1;
}

// Determine video dimensions from the sequence parameterset.
void FindAVCDimensions(
        const sp<ABuffer> &seqParamSet,
        int32_t *width, int32_t *height,
        int32_t *sarWidth, int32_t *sarHeight) {
    ABitReader br(seqParamSet->data() + 1, seqParamSet->size() - 1);

    unsigned profile_idc = br.getBits(8);
    br.skipBits(16);
    parseUE(&br);  // seq_parameter_set_id

    unsigned chroma_format_idc = 1;  // 4:2:0 chroma format

    if (profile_idc == 100 || profile_idc == 110
            || profile_idc == 122 || profile_idc == 244
            || profile_idc == 44 || profile_idc == 83 || profile_idc == 86) {
        chroma_format_idc = parseUE(&br);
        if (chroma_format_idc == 3) {
            br.skipBits(1);  // residual_colour_transform_flag
        }
        parseUE(&br);  // bit_depth_luma_minus8
        parseUE(&br);  // bit_depth_chroma_minus8
        br.skipBits(1);  // qpprime_y_zero_transform_bypass_flag
#ifndef ANDROID_DEFAULT_CODE
	if (br.getBits(1) != 0)
	{
	    ALOGW("seq_scaling_matrix_present_flag != 0");
            parse_seq_scaling_matrix_present(&br);
	}
#else
        CHECK_EQ(br.getBits(1), 0u);  // seq_scaling_matrix_present_flag
#endif
    }

    parseUE(&br);  // log2_max_frame_num_minus4
    unsigned pic_order_cnt_type = parseUE(&br);

    if (pic_order_cnt_type == 0) {
        parseUE(&br);  // log2_max_pic_order_cnt_lsb_minus4
    } else if (pic_order_cnt_type == 1) {
        // offset_for_non_ref_pic, offset_for_top_to_bottom_field and
        // offset_for_ref_frame are technically se(v), but since we are
        // just skipping over them the midpoint does not matter.

        br.getBits(1);  // delta_pic_order_always_zero_flag
        parseUE(&br);  // offset_for_non_ref_pic
        parseUE(&br);  // offset_for_top_to_bottom_field

        unsigned num_ref_frames_in_pic_order_cnt_cycle = parseUE(&br);
        for (unsigned i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; ++i) {
            parseUE(&br);  // offset_for_ref_frame
        }
    }

    parseUE(&br);  // num_ref_frames
    br.getBits(1);  // gaps_in_frame_num_value_allowed_flag

    unsigned pic_width_in_mbs_minus1 = parseUE(&br);
    unsigned pic_height_in_map_units_minus1 = parseUE(&br);
    unsigned frame_mbs_only_flag = br.getBits(1);

    *width = pic_width_in_mbs_minus1 * 16 + 16;

    *height = (2 - frame_mbs_only_flag)
        * (pic_height_in_map_units_minus1 * 16 + 16);

    if (!frame_mbs_only_flag) {
        br.getBits(1);  // mb_adaptive_frame_field_flag
    }

    br.getBits(1);  // direct_8x8_inference_flag

    if (br.getBits(1)) {  // frame_cropping_flag
        unsigned frame_crop_left_offset = parseUE(&br);
        unsigned frame_crop_right_offset = parseUE(&br);
        unsigned frame_crop_top_offset = parseUE(&br);
        unsigned frame_crop_bottom_offset = parseUE(&br);

        unsigned cropUnitX, cropUnitY;
        if (chroma_format_idc == 0  /* monochrome */) {
            cropUnitX = 1;
            cropUnitY = 2 - frame_mbs_only_flag;
        } else {
            unsigned subWidthC = (chroma_format_idc == 3) ? 1 : 2;
            unsigned subHeightC = (chroma_format_idc == 1) ? 2 : 1;

            cropUnitX = subWidthC;
            cropUnitY = subHeightC * (2 - frame_mbs_only_flag);
        }

        ALOGV("frame_crop = (%u, %u, %u, %u), cropUnitX = %u, cropUnitY = %u",
             frame_crop_left_offset, frame_crop_right_offset,
             frame_crop_top_offset, frame_crop_bottom_offset,
             cropUnitX, cropUnitY);

        *width -=
            (frame_crop_left_offset + frame_crop_right_offset) * cropUnitX;
        *height -=
            (frame_crop_top_offset + frame_crop_bottom_offset) * cropUnitY;
    }

    if (sarWidth != NULL) {
        *sarWidth = 0;
    }

    if (sarHeight != NULL) {
        *sarHeight = 0;
    }

    if (br.getBits(1)) {  // vui_parameters_present_flag
        unsigned sar_width = 0, sar_height = 0;

        if (br.getBits(1)) {  // aspect_ratio_info_present_flag
            unsigned aspect_ratio_idc = br.getBits(8);

            if (aspect_ratio_idc == 255 /* extendedSAR */) {
                sar_width = br.getBits(16);
                sar_height = br.getBits(16);
            } else if (aspect_ratio_idc > 0 && aspect_ratio_idc < 14) {
                static const int32_t kFixedSARWidth[] = {
                    1, 12, 10, 16, 40, 24, 20, 32, 80, 18, 15, 64, 160
                };

                static const int32_t kFixedSARHeight[] = {
                    1, 11, 11, 11, 33, 11, 11, 11, 33, 11, 11, 33, 99
                };

                sar_width = kFixedSARWidth[aspect_ratio_idc - 1];
                sar_height = kFixedSARHeight[aspect_ratio_idc - 1];
            }
        }

        ALOGV("sample aspect ratio = %u : %u", sar_width, sar_height);

        if (sarWidth != NULL) {
            *sarWidth = sar_width;
        }

        if (sarHeight != NULL) {
            *sarHeight = sar_height;
        }
    }
}

status_t getNextNALUnit(
        const uint8_t **_data, size_t *_size,
        const uint8_t **nalStart, size_t *nalSize,
        bool startCodeFollows) {
    const uint8_t *data = *_data;
    size_t size = *_size;

    *nalStart = NULL;
    *nalSize = 0;

    if (size == 0) {
        return -EAGAIN;
    }

    // Skip any number of leading 0x00.

    size_t offset = 0;
    while (offset < size && data[offset] == 0x00) {
        ++offset;
    }

    if (offset == size) {
        return -EAGAIN;
    }

    // A valid startcode consists of at least two 0x00 bytes followed by 0x01.

    if (offset < 2 || data[offset] != 0x01) {
        return ERROR_MALFORMED;
    }

    ++offset;

    size_t startOffset = offset;

    for (;;) {
        while (offset < size && data[offset] != 0x01) {
            ++offset;
        }

        if (offset == size) {
            if (startCodeFollows) {
                offset = size + 2;
                break;
            }

            return -EAGAIN;
        }

        if (data[offset - 1] == 0x00 && data[offset - 2] == 0x00) {
            break;
        }

        ++offset;
    }

    size_t endOffset = offset - 2;
    while (endOffset > startOffset + 1 && data[endOffset - 1] == 0x00) {
        --endOffset;
    }

    *nalStart = &data[startOffset];
    *nalSize = endOffset - startOffset;

    if (offset + 2 < size) {
        *_data = &data[offset - 2];
        *_size = size - offset + 2;
    } else {
        *_data = NULL;
        *_size = 0;
    }

    return OK;
}

static sp<ABuffer> FindNAL(
        const uint8_t *data, size_t size, unsigned nalType,
        size_t *stopOffset) {
    const uint8_t *nalStart;
    size_t nalSize;
    while (getNextNALUnit(&data, &size, &nalStart, &nalSize, true) == OK) {
        if ((nalStart[0] & 0x1f) == nalType) {
            sp<ABuffer> buffer = new ABuffer(nalSize);
            memcpy(buffer->data(), nalStart, nalSize);
            return buffer;
        }
    }

    return NULL;
}

const char *AVCProfileToString(uint8_t profile) {
    switch (profile) {
        case kAVCProfileBaseline:
            return "Baseline";
        case kAVCProfileMain:
            return "Main";
        case kAVCProfileExtended:
            return "Extended";
        case kAVCProfileHigh:
            return "High";
        case kAVCProfileHigh10:
            return "High 10";
        case kAVCProfileHigh422:
            return "High 422";
        case kAVCProfileHigh444:
            return "High 444";
        case kAVCProfileCAVLC444Intra:
            return "CAVLC 444 Intra";
        default:   return "Unknown";
    }
}

sp<MetaData> MakeAVCCodecSpecificData(const sp<ABuffer> &accessUnit) {
    const uint8_t *data = accessUnit->data();
    size_t size = accessUnit->size();

    sp<ABuffer> seqParamSet = FindNAL(data, size, 7, NULL);
    if (seqParamSet == NULL) {
        return NULL;
    }

    int32_t width, height;
    int32_t sarWidth, sarHeight;
    FindAVCDimensions(
            seqParamSet, &width, &height, &sarWidth, &sarHeight);

    size_t stopOffset;
    sp<ABuffer> picParamSet = FindNAL(data, size, 8, &stopOffset);
    CHECK(picParamSet != NULL);

    size_t csdSize =
        1 + 3 + 1 + 1
        + 2 * 1 + seqParamSet->size()
        + 1 + 2 * 1 + picParamSet->size();

    sp<ABuffer> csd = new ABuffer(csdSize);
    uint8_t *out = csd->data();

    *out++ = 0x01;  // configurationVersion
    memcpy(out, seqParamSet->data() + 1, 3);  // profile/level...

    uint8_t profile = out[0];
    uint8_t level = out[2];

    out += 3;
    *out++ = (0x3f << 2) | 1;  // lengthSize == 2 bytes
    *out++ = 0xe0 | 1;

    *out++ = seqParamSet->size() >> 8;
    *out++ = seqParamSet->size() & 0xff;
    memcpy(out, seqParamSet->data(), seqParamSet->size());
    out += seqParamSet->size();

    *out++ = 1;

    *out++ = picParamSet->size() >> 8;
    *out++ = picParamSet->size() & 0xff;
    memcpy(out, picParamSet->data(), picParamSet->size());

#if 0
    ALOGI("AVC seq param set");
    hexdump(seqParamSet->data(), seqParamSet->size());
#endif

    sp<MetaData> meta = new MetaData;
    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_AVC);

    meta->setData(kKeyAVCC, kTypeAVCC, csd->data(), csd->size());
    meta->setInt32(kKeyWidth, width);
    meta->setInt32(kKeyHeight, height);

    if (sarWidth > 1 || sarHeight > 1) {
        // We treat 0:0 (unspecified) as 1:1.

        meta->setInt32(kKeySARWidth, sarWidth);
        meta->setInt32(kKeySARHeight, sarHeight);

        ALOGI("found AVC codec config (%d x %d, %s-profile level %d.%d) "
              "SAR %d : %d",
             width,
             height,
             AVCProfileToString(profile),
             level / 10,
             level % 10,
             sarWidth,
             sarHeight);
    } else {
        ALOGI("found AVC codec config (%d x %d, %s-profile level %d.%d)",
             width,
             height,
             AVCProfileToString(profile),
             level / 10,
             level % 10);
    }

    return meta;
}

#ifndef ANDROID_DEFAULT_CODE
void MakeAVCCodecSpecificData2(const sp<ABuffer> &accessUnit, sp<MetaData> meta){
    const uint8_t *data = accessUnit->data();
    size_t size = accessUnit->size();

    sp<ABuffer> seqParamSet = FindNAL(data, size, 7, NULL);
    if (seqParamSet == NULL) {
        return;
    }

    int32_t width, height;
    FindAVCDimensions(seqParamSet, &width, &height);

    size_t stopOffset;
    sp<ABuffer> picParamSet = FindNAL(data, size, 8, &stopOffset);
    CHECK(picParamSet != NULL);

    size_t csdSize =
        1 + 3 + 1 + 1
        + 2 * 1 + seqParamSet->size()
        + 1 + 2 * 1 + picParamSet->size();

    sp<ABuffer> csd = new ABuffer(csdSize);
    uint8_t *out = csd->data();

    *out++ = 0x01;  // configurationVersion
    memcpy(out, seqParamSet->data() + 1, 3);  // profile/level...

    uint8_t profile = out[0];
    uint8_t level = out[2];

    out += 3;
    *out++ = (0x3f << 2) | 1;  // lengthSize == 2 bytes
    *out++ = 0xe0 | 1;

    *out++ = seqParamSet->size() >> 8;
    *out++ = seqParamSet->size() & 0xff;
    memcpy(out, seqParamSet->data(), seqParamSet->size());
    out += seqParamSet->size();

    *out++ = 1;

    *out++ = picParamSet->size() >> 8;
    *out++ = picParamSet->size() & 0xff;
    memcpy(out, picParamSet->data(), picParamSet->size());

#if 0
    ALOGI("AVC seq param set");
    hexdump(seqParamSet->data(), seqParamSet->size());
#endif

    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_AVC);

    meta->setData(kKeyAVCC, kTypeAVCC, csd->data(), csd->size());
    meta->setInt32(kKeyWidth, width);
    meta->setInt32(kKeyHeight, height);

    ALOGI("found AVC codec config (%d x %d, %s-profile level %d.%d)",
         width, height, AVCProfileToString(profile), level / 10, level % 10);
	
} 
#ifdef MTK_VIDEO_HEVC_SUPPORT
sp<MetaData> MakeHEVCCodecSpecificData(const sp<ABuffer> &accessUnit){
	ALOGI("MakeHEVCCodecSpecificData ++");
	const uint8_t *data = accessUnit->data();
	size_t size = accessUnit->size();
	size_t numOfParamSets = 0;
	const uint8_t VPS_NAL_TYPE = 32;
	const uint8_t SPS_NAL_TYPE = 33;
	const uint8_t PPS_NAL_TYPE = 34;
	
	//find vps,only choose the first vps,
	//need check whether need sent all the vps to decoder
	sp<ABuffer> videoParamSet = FindHEVCNAL(data, size, VPS_NAL_TYPE, NULL);
	if (videoParamSet == NULL) {
		ALOGW("no vps found !!!");
		//return NULL;
	}else{
		numOfParamSets++;
		ALOGI("find vps, size =%d",videoParamSet->size());
	}
	
	//find sps,only choose the first sps,
	//need check whether need sent all the sps to decoder
	sp<ABuffer> seqParamSet = FindHEVCNAL(data, size, SPS_NAL_TYPE, NULL);
	if (seqParamSet == NULL) {
		ALOGW("no sps found !!!");
		return NULL;
	}else{
		numOfParamSets++;
		ALOGI("find sps, size =%d",seqParamSet->size());
	}

	
	int32_t width, height;
	FindHEVCDimensions(seqParamSet, &width, &height);
	
	//find pps,only choose the first pps,
	//need check whether need sent all the pps to decoder
	size_t stopOffset;
	sp<ABuffer> picParamSet = FindHEVCNAL(data, size, PPS_NAL_TYPE, &stopOffset);
	if (picParamSet == NULL) {
		ALOGW("no sps found !!!");
		return NULL;
	}else{	
		numOfParamSets++;
		ALOGI("find pps, size =%d",picParamSet->size());
	}
	
	int32_t numbOfArrays = numOfParamSets;
	int32_t paramSetSize = 0;

	//only save one vps,sps,pps in codecConfig data
	if(videoParamSet != NULL){
		paramSetSize += 1 + 2 + 2 + videoParamSet->size();
	}
	if(seqParamSet != NULL){
		paramSetSize += 1 + 2 + 2 + seqParamSet->size();
	}
	if(picParamSet != NULL){
		paramSetSize += 1 + 2 + 2 + picParamSet->size();
	}
	
	size_t csdSize =
		1 + 1 + 4 + 6 + 1 + 2 + 1 + 1 + 1 + 1 + 2 + 1 
		+ 1 + paramSetSize;
	ALOGI("MakeHEVCCodecSpecificData,codec config data size =%d",csdSize);
	sp<ABuffer> csd = new ABuffer(csdSize);
	uint8_t *out = csd->data();

	*out++ = 0x01;	// configurationVersion
	
	/*copy profile_tier_leve info in sps, containing
	1 byte:general_profile_space(2),general_tier_flag(1),general_profile_idc(5)
	4 bytes: general_profile_compatibility_flags, 6 bytes: general_constraint_indicator_flags
	1 byte:general_level_idc
	*/
	memcpy(out,seqParamSet->data() + 3, 1 + 4 + 6 + 1);

	uint8_t profile = out[0] & 0x1f;
	uint8_t level = out[11];
	
	out += 1 + 4 + 6 + 1;

	*out++ = 0xf0; //reserved(1111b) + min_spatial_segmentation_idc(4)
	*out++ = 0x00;// min_spatial_segmentation_idc(8) 
	*out++ = 0xfc; // reserved(6bits,111111b) + parallelismType(2)(0=unknow,1=slices,2=tiles,3=WPP)
	*out++ = 0xfd; //reserved(6bits,111111b)+chromaFormat(2)(0=monochrome, 1=4:2:0, 2=4:2:2, 3=4:4:4)

	*out++ = 0xf8;//reserved(5bits,11111b) + bitDepthLumaMinus8(3)
	*out++ = 0xf8;//reserved(5bits,11111b) + bitDepthChromaMinus8(3)
	
	uint16_t avgFrameRate = 0;
	*out++ = avgFrameRate >> 8; // avgFrameRate (16bits,in units of frames/256 seconds,0 indicates an unspecified average frame rate)
	*out++ = avgFrameRate & 0xff;

	*out++ = 0x03;//constantFrameRate(2bits,0=not be of constant frame rate),numTemporalLayers(3bits),temporalIdNested(1bits),
				 //lengthSizeMinusOne(2bits)

	*out++ = numbOfArrays;//numOfArrays

	if(videoParamSet != NULL){
		*out++ = 0x3f & VPS_NAL_TYPE; //array_completeness(1bit)+reserved(1bit,0)+NAL_unit_type(6bits)

		//num of vps
		uint16_t numNalus = 1;
		*out++ = numNalus >> 8;
		*out++ =  numNalus & 0xff;

		//vps nal length
		*out++ = videoParamSet->size() >> 8;
		*out++ = videoParamSet->size() & 0xff;

		memcpy(out,videoParamSet->data(),videoParamSet->size());
		out += videoParamSet->size();
	}

	if(seqParamSet != NULL){
		
		*out++ = 0x3f & SPS_NAL_TYPE; //array_completeness(1bit)+reserved(1bit,0)+NAL_unit_type(6bits)
		
		//num of sps
		uint16_t numNalus = 1;
		*out++ = numNalus >> 8;
		*out++ = numNalus & 0xff;

		//sps nal length
		*out++ = seqParamSet->size() >> 8;
		*out++ = seqParamSet->size() & 0xff;

		memcpy(out,seqParamSet->data(),seqParamSet->size());
		out += seqParamSet->size();

	}
	if(picParamSet != NULL){
	
		*out++ = 0x3f & PPS_NAL_TYPE; //array_completeness(1bit)+reserved(1bit,0)+NAL_unit_type(6bits)
		
		//num of pps
		uint16_t numNalus = 1;
		*out++ = numNalus >> 8;
		*out++ = numNalus & 0xff;

		//pps nal length
		*out++ = picParamSet->size() >> 8;
		*out++ = picParamSet->size() & 0xff;

		memcpy(out,picParamSet->data(),picParamSet->size());
		//no need add out offset
	}


	sp<MetaData> meta = new MetaData;
	meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_HEVC);

	meta->setData(kKeyHVCC, kTypeHVCC, csd->data(), csd->size());
	meta->setInt32(kKeyWidth, width);
	meta->setInt32(kKeyHeight, height);

	ALOGI("found HEVC codec config (%d x %d, %s-profile level %d.%d)",
		 width, height, HEVCProfileToString(profile), level / 10, level % 10);
	ALOGI("MakeHEVCCodecSpecificData --");
	return meta;
}
#endif
#endif
bool IsIDR(const sp<ABuffer> &buffer) {
    const uint8_t *data = buffer->data();
    size_t size = buffer->size();

    bool foundIDR = false;

    const uint8_t *nalStart;
    size_t nalSize;
    while (getNextNALUnit(&data, &size, &nalStart, &nalSize, true) == OK) {
        CHECK_GT(nalSize, 0u);

        unsigned nalType = nalStart[0] & 0x1f;

        if (nalType == 5) {
            foundIDR = true;
            break;
        }
    }

    return foundIDR;
}

bool IsAVCReferenceFrame(const sp<ABuffer> &accessUnit) {
    const uint8_t *data = accessUnit->data();
    size_t size = accessUnit->size();

    const uint8_t *nalStart;
    size_t nalSize;
    while (getNextNALUnit(&data, &size, &nalStart, &nalSize, true) == OK) {
        CHECK_GT(nalSize, 0u);

        unsigned nalType = nalStart[0] & 0x1f;

        if (nalType == 5) {
            return true;
        } else if (nalType == 1) {
            unsigned nal_ref_idc = (nalStart[0] >> 5) & 3;
            return nal_ref_idc != 0;
        }
    }

    return true;
}

sp<MetaData> MakeAACCodecSpecificData(
        unsigned profile, unsigned sampling_freq_index,
        unsigned channel_configuration) {
    sp<MetaData> meta = new MetaData;
    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AAC);

    CHECK_LE(sampling_freq_index, 11u);
    static const int32_t kSamplingFreq[] = {
        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
        16000, 12000, 11025, 8000
    };
    meta->setInt32(kKeySampleRate, kSamplingFreq[sampling_freq_index]);
    meta->setInt32(kKeyChannelCount, channel_configuration);

    static const uint8_t kStaticESDS[] = {
        0x03, 22,
        0x00, 0x00,     // ES_ID
        0x00,           // streamDependenceFlag, URL_Flag, OCRstreamFlag

        0x04, 17,
        0x40,                       // Audio ISO/IEC 14496-3
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,

        0x05, 2,
        // AudioSpecificInfo follows

        // oooo offf fccc c000
        // o - audioObjectType
        // f - samplingFreqIndex
        // c - channelConfig
    };
    sp<ABuffer> csd = new ABuffer(sizeof(kStaticESDS) + 2);
    memcpy(csd->data(), kStaticESDS, sizeof(kStaticESDS));

    csd->data()[sizeof(kStaticESDS)] =
        ((profile + 1) << 3) | (sampling_freq_index >> 1);

    csd->data()[sizeof(kStaticESDS) + 1] =
        ((sampling_freq_index << 7) & 0x80) | (channel_configuration << 3);

    meta->setData(kKeyESDS, 0, csd->data(), csd->size());

    return meta;
}

bool ExtractDimensionsFromVOLHeader(
        const uint8_t *data, size_t size, int32_t *width, int32_t *height) {
    ABitReader br(&data[4], size - 4);
    br.skipBits(1);  // random_accessible_vol
    unsigned video_object_type_indication = br.getBits(8);

#ifndef ANDROID_DEFAULT_CODE 
    if (video_object_type_indication == 0x21)
        return false;
#else
    CHECK_NE(video_object_type_indication,
             0x21u /* Fine Granularity Scalable */);
#endif // #ifndef ANDROID_DEFAULT_CODE

    unsigned video_object_layer_verid;
    unsigned video_object_layer_priority;
    if (br.getBits(1)) {
        video_object_layer_verid = br.getBits(4);
        video_object_layer_priority = br.getBits(3);
    }
    unsigned aspect_ratio_info = br.getBits(4);
    if (aspect_ratio_info == 0x0f /* extended PAR */) {
        br.skipBits(8);  // par_width
        br.skipBits(8);  // par_height
    }
    if (br.getBits(1)) {  // vol_control_parameters
        br.skipBits(2);  // chroma_format
        br.skipBits(1);  // low_delay
        if (br.getBits(1)) {  // vbv_parameters
            br.skipBits(15);  // first_half_bit_rate
#ifndef ANDROID_DEFAULT_CODE
            br.skipBits(1);
#else
            CHECK(br.getBits(1));  // marker_bit
#endif // #ifndef ANDROID_DEFAULT_CODE
            br.skipBits(15);  // latter_half_bit_rate
#ifndef ANDROID_DEFAULT_CODE
            br.skipBits(1);
#else
            CHECK(br.getBits(1));  // marker_bit
#endif
            br.skipBits(15);  // first_half_vbv_buffer_size
#ifndef ANDROID_DEFAULT_CODE
            br.skipBits(1);
#else
            CHECK(br.getBits(1));  // marker_bit
#endif
            br.skipBits(3);  // latter_half_vbv_buffer_size
            br.skipBits(11);  // first_half_vbv_occupancy
#ifndef ANDROID_DEFAULT_CODE
            br.skipBits(1);
#else
            CHECK(br.getBits(1));  // marker_bit
#endif
            br.skipBits(15);  // latter_half_vbv_occupancy
#ifndef ANDROID_DEFAULT_CODE
            br.skipBits(1);
#else
            CHECK(br.getBits(1));  // marker_bit
#endif
        }
    }
    unsigned video_object_layer_shape = br.getBits(2);
#ifndef ANDROID_DEFAULT_CODE 
    if (video_object_layer_shape != 0x00)
        return false;
#else
    CHECK_EQ(video_object_layer_shape, 0x00u /* rectangular */);
#endif // #ifndef ANDROID_DEFAULT_CODE

#ifndef ANDROID_DEFAULT_CODE 
    if (!br.getBits(1))
        return false;
#else
    CHECK(br.getBits(1));  // marker_bit
#endif // #ifndef ANDROID_DEFAULT_CODE
    unsigned vop_time_increment_resolution = br.getBits(16);
#ifndef ANDROID_DEFAULT_CODE 
    if (!br.getBits(1))
        return false;
#else
    CHECK(br.getBits(1));  // marker_bit
#endif // #ifndef ANDROID_DEFAULT_CODE

    if (br.getBits(1)) {  // fixed_vop_rate
        // range [0..vop_time_increment_resolution)

        // vop_time_increment_resolution
        // 2 => 0..1, 1 bit
        // 3 => 0..2, 2 bits
        // 4 => 0..3, 2 bits
        // 5 => 0..4, 3 bits
        // ...

#ifndef ANDROID_DEFAULT_CODE 
        if (vop_time_increment_resolution <= 0)
            return false;
#else
        CHECK_GT(vop_time_increment_resolution, 0u);
#endif // #ifndef ANDROID_DEFAULT_CODE
        --vop_time_increment_resolution;

#ifndef ANDROID_DEFAULT_CODE 
        // fix bug when vop_time_increment_resolution = 1
        unsigned numBits = 1;
        while (vop_time_increment_resolution >>= 1) {
            ++numBits;
        }
#else
        unsigned numBits = 0;
        while (vop_time_increment_resolution > 0) {
            ++numBits;
            vop_time_increment_resolution >>= 1;
        }
#endif // #ifndef ANDROID_DEFAULT_CODE

        br.skipBits(numBits);  // fixed_vop_time_increment
    }

#ifndef ANDROID_DEFAULT_CODE 
    if (!br.getBits(1))
        return false;
#else
    CHECK(br.getBits(1));  // marker_bit
#endif // #ifndef ANDROID_DEFAULT_CODE
    unsigned video_object_layer_width = br.getBits(13);
#ifndef ANDROID_DEFAULT_CODE 
    if (!br.getBits(1))
        return false;
#else
    CHECK(br.getBits(1));  // marker_bit
#endif // #ifndef ANDROID_DEFAULT_CODE
    unsigned video_object_layer_height = br.getBits(13);
#ifndef ANDROID_DEFAULT_CODE 
    if (!br.getBits(1))
        return false;
#else
    CHECK(br.getBits(1));  // marker_bit
#endif // #ifndef ANDROID_DEFAULT_CODE

    unsigned interlaced = br.getBits(1);

    *width = video_object_layer_width;
    *height = video_object_layer_height;

    return true;
}

bool GetMPEGAudioFrameSize(
        uint32_t header, size_t *frame_size,
        int *out_sampling_rate, int *out_channels,
        int *out_bitrate, int *out_num_samples) {
    *frame_size = 0;

    if (out_sampling_rate) {
        *out_sampling_rate = 0;
    }

    if (out_channels) {
        *out_channels = 0;
    }

    if (out_bitrate) {
        *out_bitrate = 0;
    }

    if (out_num_samples) {
        *out_num_samples = 1152;
    }

    if ((header & 0xffe00000) != 0xffe00000) {
        return false;
    }

    unsigned version = (header >> 19) & 3;

    if (version == 0x01) {
        return false;
    }

    unsigned layer = (header >> 17) & 3;

    if (layer == 0x00) {
        return false;
    }

    unsigned protection = (header >> 16) & 1;

    unsigned bitrate_index = (header >> 12) & 0x0f;

    if (bitrate_index == 0 || bitrate_index == 0x0f) {
        // Disallow "free" bitrate.
        return false;
    }

    unsigned sampling_rate_index = (header >> 10) & 3;

    if (sampling_rate_index == 3) {
        return false;
    }

    static const int kSamplingRateV1[] = { 44100, 48000, 32000 };
    int sampling_rate = kSamplingRateV1[sampling_rate_index];
    if (version == 2 /* V2 */) {
        sampling_rate /= 2;
    } else if (version == 0 /* V2.5 */) {
        sampling_rate /= 4;
    }

    unsigned padding = (header >> 9) & 1;

    if (layer == 3) {
        // layer I

        static const int kBitrateV1[] = {
            32, 64, 96, 128, 160, 192, 224, 256,
            288, 320, 352, 384, 416, 448
        };

        static const int kBitrateV2[] = {
            32, 48, 56, 64, 80, 96, 112, 128,
            144, 160, 176, 192, 224, 256
        };

        int bitrate =
            (version == 3 /* V1 */)
                ? kBitrateV1[bitrate_index - 1]
                : kBitrateV2[bitrate_index - 1];

        if (out_bitrate) {
            *out_bitrate = bitrate;
        }

        *frame_size = (12000 * bitrate / sampling_rate + padding) * 4;

        if (out_num_samples) {
            *out_num_samples = 384;
        }
    } else {
        // layer II or III

        static const int kBitrateV1L2[] = {
            32, 48, 56, 64, 80, 96, 112, 128,
            160, 192, 224, 256, 320, 384
        };

        static const int kBitrateV1L3[] = {
            32, 40, 48, 56, 64, 80, 96, 112,
            128, 160, 192, 224, 256, 320
        };

        static const int kBitrateV2[] = {
            8, 16, 24, 32, 40, 48, 56, 64,
            80, 96, 112, 128, 144, 160
        };

        int bitrate;
        if (version == 3 /* V1 */) {
            bitrate = (layer == 2 /* L2 */)
                ? kBitrateV1L2[bitrate_index - 1]
                : kBitrateV1L3[bitrate_index - 1];

            if (out_num_samples) {
                *out_num_samples = 1152;
            }
        } else {
            // V2 (or 2.5)

            bitrate = kBitrateV2[bitrate_index - 1];
#ifndef ANDROID_DEFAULT_CODE
			if(layer == 2 /* L2 */){
				if (out_num_samples) {
					*out_num_samples = 1152;
				}				
			}else{/* L3 */
#endif	            
            if (out_num_samples) {
                *out_num_samples = (layer == 1 /* L3 */) ? 576 : 1152;
            }
#ifndef ANDROID_DEFAULT_CODE
			}
#endif
        }

        if (out_bitrate) {
            *out_bitrate = bitrate;
        }

        if (version == 3 /* V1 */) {
            *frame_size = 144000 * bitrate / sampling_rate + padding;
        } else {
            // V2 or V2.5
#ifndef ANDROID_DEFAULT_CODE
			if(layer == 2 /* L2 */){
            	*frame_size = 144000 * bitrate / sampling_rate + padding;
			}else{
#endif
            size_t tmp = (layer == 1 /* L3 */) ? 72000 : 144000;
            *frame_size = tmp * bitrate / sampling_rate + padding;
#ifndef ANDROID_DEFAULT_CODE
			}
#endif
        }
    }

    if (out_sampling_rate) {
        *out_sampling_rate = sampling_rate;
    }

    if (out_channels) {
        int channel_mode = (header >> 6) & 3;

        *out_channels = (channel_mode == 3) ? 1 : 2;
    }

    return true;
}

#ifndef ANDROID_DEFAULT_CODE
#define SHAPE_RECTANGULAR      0
#define SHAPE_BINARY           1
#define SHAPE_BINARY_ONLY      2
#define SHAPE_GRAY_SCALE       3

static int __log2__(unsigned int value) {
    int n = 0;
    static const int log2Table[16] = {
        0,0,1,1,2,2,2,2,3,3,3,3,3,3,3,3
    };

    if(value & 0xffff0000) {
        value >>= 16;
        n += 16;
    }

    if(value & 0xff00) {
        value >>= 8;
        n += 8;
    }

    if(value & 0xf0) {
        value >>= 4;
        n += 4;
    }

    n += log2Table[value];

    return n;
}

int findVOLHeader(const uint8_t *start, int length) {
    uint32_t code = -1;
    int i = 0;

    for(i=0; i<length; i++){
        code = (code<<8) + start[i];
        if ((code & 0xfffffff0) == 0x00000120) {
            // some files has no seq start code
            return i - 3;
        }
    }

    return -1;
}

int decodeVOLHeader(const uint8_t *vol, size_t size, struct MPEG4Info *s) {
    ABitReader br(vol, size);

    int video_object_layer_verid;

    if (br.numBitsLeft() < 40)
        return -1;

    br.skipBits(1); // random_accessible_vol
    br.skipBits(8); // video_object_type_indication
    if (br.getBits(1)) { // is_object_layer_identifier
        video_object_layer_verid = br.getBits(4);
        br.skipBits(3); // video_object_layer_priority
    } else {
        video_object_layer_verid = 1;
    }

    int aspect_ratio_info = br.getBits(4);
    if(aspect_ratio_info == 15 /* extended_PAR */) {
        br.skipBits(8); // par_width
        br.skipBits(8); // par_height
    }

    if (br.getBits(1)) { // vol_control_parameters
        if (br.numBitsLeft() < 21 + 4)
            return -1;

        br.skipBits(2);
        br.skipBits(1);
        if(br.getBits(1)) { // vbv_parameters
            if (br.numBitsLeft() < 21 + 79)
                return -1;

            br.skipBits(15); // first_half_bitrate
            br.skipBits(1);  // marker_bit
            br.skipBits(15); // latter_half_bitrate
            br.skipBits(1); // marker_bit
            br.skipBits(15); // first_half_vbv_buffer_size
            br.skipBits(1); // marker_bit
            br.skipBits(3); // latter_half_vbv_buffer_size
            br.skipBits(11); // first_half_vbv_occupancy
            br.skipBits(1); // marker_bit
            br.skipBits(15); // latter_half_vbv_occupancy
            br.skipBits(1); // marker_bit
        }
    }

    int video_object_layer_shape = br.getBits(2); // video_object_layer_shape
    if(video_object_layer_shape == SHAPE_GRAY_SCALE && video_object_layer_verid != 1){
        br.skipBits(4); //video_object_layer_shape_extension
    }

    if (!br.getBits(1)) {
       ALOGW("missing marker before vop_time_increment_resolution");
    }

    if (br.numBitsLeft() < 18)
        return -1;

    int vop_time_increment_resolution = br.getBits(16);
    if(!vop_time_increment_resolution) {
        return -1;
    }

    int vop_time_increment_bits = __log2__(vop_time_increment_resolution - 1) + 1;
    if (vop_time_increment_bits < 1)
        vop_time_increment_bits = 1;

    if (!br.getBits(1)) {
       ALOGW("missing marker before fixed_vop_rate");
    }

    if (br.getBits(1)) { // fixed_vop_rate
        br.skipBits(vop_time_increment_bits);
    }

    if (video_object_layer_shape != SHAPE_BINARY_ONLY) {
        if (br.numBitsLeft() < 1)
            return -1;

        if (video_object_layer_shape == SHAPE_RECTANGULAR) {
            if (br.numBitsLeft() < 30)
                return -1;
            br.skipBits(1); // marker_bit
            br.skipBits(13);
            br.skipBits(1); // marker_bit
            br.skipBits(13);
            br.skipBits(1); // marker_bit
        }

        s->progressive = br.getBits(1) ^ 1;
        // ... lots of other things to parse
    }
    return 0;
}

#define SHORT_VIDEO_START_MARKER         0x20
#define SHORT_VIDEO_START_MARKER_LENGTH  22
int decodeShortHeader(const uint8_t *vol, size_t size, struct MPEG4Info* s) {
    ABitReader br(vol, size);
    int status = 0;
    uint32_t tmpvar = 0;
    s->progressive = 0;
    s->cpcf = 0;

    int extended_PTYPE = false;
    int UFEP = 0, custom_PFMT = 0, custom_PCF = 0;

    tmpvar = br.getBits(SHORT_VIDEO_START_MARKER_LENGTH);

    if (tmpvar !=  SHORT_VIDEO_START_MARKER) {
       ALOGE("bad short header %x", tmpvar);
        return -1;
    }

    // Temporal Reference
    br.skipBits(8);

    /* Marker Bit */
    if (!br.getBits(1)) {
       ALOGE("bad market bit in PTYPE");
        return -1;
    }

    /* Zero Bit */
    if (br.getBits(1)) {
       ALOGE("bad zero bit in PTYPE");
        return -1;
    }

    /*split_screen_indicator*/
    br.skipBits(1);

    /*document_freeze_camera*/
    br.skipBits(1);

    /*freeze_picture_release*/
    br.skipBits(1);

    /* source format */
    switch (br.getBits(3)) {
        case 1:
           ALOGI("128 96");
            break;

        case 2:
           ALOGI("176 144");
            break;

        case 3:
           ALOGI("352 288");
            break;

        case 4:
           ALOGI("704 576");
            break;

        case 5:
           ALOGI("1408 1152");
            break;

        case 7:
           ALOGI("extended PTYPE signaled");
            extended_PTYPE = true;
            break;

        default:
           ALOGE("bad H.263 source format");
            return -1;
    }

    if (extended_PTYPE == false) {
        /* predictionType */
        br.skipBits(1);

        /* four_reserved_zero_bits */
        if (br.getBits(4)) {
           ALOGE("Reserved bits wrong");
            return -1;
        }
    } else {
        UFEP = br.getBits(3);
        if (UFEP == 1) {
            /* source format */
            switch (br.getBits(3)) {
                case 1:
                   ALOGI("128 96");
                    break;

                case 2:
                   ALOGI("176 144");
                    break;

                case 3:
                   ALOGI("352 288");
                    break;

                case 4:
                   ALOGI("704 576");
                    break;

                case 5:
                   ALOGI("1408 1152");
                    break;

                case 6:
                   ALOGI("custom PFMT signaled");
                    custom_PFMT = true;
                    break;

                default:
                   ALOGE("bad H.263 source format");
                    return -1;
            }

            /* Custom PCF */
            custom_PCF = br.getBits(1);
            s->cpcf = custom_PCF;
           ALOGI("cpcf %d", custom_PCF);
            /* unrestricted MV */
            br.skipBits(1);

            /* SAC */
            br.skipBits(1);

            /* AP */
            br.skipBits(1);

            /* advanced INTRA */
            br.skipBits(1);

            /* deblocking */
            br.skipBits(1);

            /* slice structure */
            br.skipBits(1);

            /* RPS, ISD, AIV */
            br.skipBits(3);

            /* modified quant */
            br.skipBits(1);

            /* Marker Bit and reserved*/
            if (br.getBits(4) != 8) {
               ALOGE("bad reserved 4 bits, not 0x1000");
                return -1;
            }
        }

        if (UFEP == 0 || UFEP == 1) {
            /* prediction type */
            tmpvar = br.getBits(3);
            if (tmpvar > 1) {
                return -1;
            }

            /* RPR */
            br.skipBits(1);

            /* RRU */
            br.skipBits(1);

            /* rounding type */
            br.skipBits(1);

            if (br.getBits(3) != 1) {
               ALOGE("bad reserved 3 bits, not 0x001");
                return -1;
            }
        } else {
           ALOGE("bad UFEP %d", UFEP);
            return -1;
        }

        /* CPM */
        br.skipBits(1);

        /* CPFMT */
        if (custom_PFMT == 1 && UFEP == 1) {
            /* aspect ratio */
            tmpvar = br.getBits(4);
            if (tmpvar == 0) {
               ALOGE("bad aspect ratio %d", tmpvar);
                return -1;
            }
            /* Extended PAR */
            if (tmpvar == 0xF) {
                /* par_width */
                br.skipBits(8);
                /* par_height */
                br.skipBits(8);
            }
            tmpvar = br.getBits(9);

            int width = (tmpvar + 1) << 2;

            /* marker bit */
            if (!br.getBits(1)) {
               ALOGE("bad marker bit after width");
                return -1;
            }
            tmpvar = br.getBits(9);
            if (tmpvar == 0) {
               ALOGE("bad height");
                return -1;
            }
            int height = tmpvar << 2;
           ALOGI("custom resolution %dx%d", width, height);
        }
    }

    /* TODO other parameters */

    return 0;
}
#endif // #ifndef ANDROID_DEFAULT_CODE
}  // namespace android

