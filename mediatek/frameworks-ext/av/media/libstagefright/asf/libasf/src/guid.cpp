/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

/*  libasf - An Advanced Systems Format media file parser   
*  Copyright (C) 2006-2010 Juho Vähä-Herttua   
*   
*  This library is free software; you can redistribute it and/or   
*  modify it under the terms of the GNU Lesser General Public   
*  License as published by the Free Software Foundation; either   
*  version 2.1 of the License, or (at your option) any later version.   
*   *  This library is distributed in the hope that it will be useful,   
*  but WITHOUT ANY WARRANTY; without even the implied warranty of 
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU   
*  Lesser General Public License for more details.   
*   
*  You should have received a copy of the GNU Lesser General Public   
*  License along with this library; if not, write to the Free Software   
*  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA   */  

#include <string.h>

#ifndef ASFPARSER_H_INCLUDED
#include "asfparser.h"
#endif

static const asf_guid_t asf_guid_null =
{0x00000000, 0x0000, 0x0000, {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}};

/* top level object guids */

static const asf_guid_t asf_guid_header =
{0x75B22630, 0x668E, 0x11CF, {0xA6, 0xD9, 0x00, 0xAA, 0x00, 0x62, 0xCE, 0x6C}};

static const asf_guid_t asf_guid_data =
{0x75B22636, 0x668E, 0x11CF, {0xA6, 0xD9, 0x00, 0xAA, 0x00, 0x62, 0xCE, 0x6C}};

static const asf_guid_t asf_guid_simple_index =
{0x33000890, 0xE5B1, 0x11CF, {0x89, 0xF4, 0x00, 0xA0, 0xC9, 0x03, 0x49, 0xCB}};

static const asf_guid_t asf_guid_index =
{0xD6E229D3, 0x35DA, 0x11D1, {0x90, 0x34, 0x00, 0xA0, 0xC9, 0x03, 0x49, 0xBE}};


/* header level object guids */

static const asf_guid_t asf_guid_file_properties =
{0x8cabdca1, 0xa947, 0x11cf, {0x8E, 0xe4, 0x00, 0xC0, 0x0C, 0x20, 0x53, 0x65}};

static const asf_guid_t asf_guid_stream_properties =
{0xB7DC0791, 0xA9B7, 0x11CF, {0x8E, 0xE6, 0x00, 0xC0, 0x0C, 0x20, 0x53, 0x65}};

static const asf_guid_t asf_guid_content_description =
{0x75B22633, 0x668E, 0x11CF, {0xA6, 0xD9, 0x00, 0xAA, 0x00, 0x62, 0xCE, 0x6C}};

static const asf_guid_t asf_guid_header_extension =
{0x5FBF03B5, 0xA92E, 0x11CF, {0x8E, 0xE3, 0x00, 0xC0, 0x0C, 0x20, 0x53, 0x65}};

static const asf_guid_t asf_guid_marker =
{0xF487CD01, 0xA951, 0x11CF, {0x8E, 0xE6, 0x00, 0xC0, 0x0C, 0x20, 0x53, 0x65}};

static const asf_guid_t asf_guid_codec_list =
{0x86D15240, 0x311D, 0x11D0, {0xA3, 0xA4, 0x00, 0xA0, 0xC9, 0x03, 0x48, 0xF6}};

//add by qian
static const asf_guid_t asf_guid_content_branding=
{0x2211B3FA, 0xBD23, 0x11D2,{0xB4,0xB7,0x00,0xA0,0xC9,0x55,0xFC,0x6E}};

//add by qian Content Encryption Object  
static const asf_guid_t asf_guid_content_encription=
{0x2211B3FB, 0xBD23, 0x11D2,{0xB4,0xB7,0x00,0xA0,0xC9,0x55,0xFC,0x6E}};


/* TODO */
static const asf_guid_t asf_guid_stream_bitrate_properties =
{0x7BF875CE, 0x468D, 0x11D1, {0x8D, 0x82, 0x00, 0x60, 0x97, 0xC9, 0xA2, 0xB2}};

static const asf_guid_t asf_guid_padding =
{0x1806D474, 0xCADF, 0x4509, {0xA4, 0xBA, 0x9A, 0xAB, 0xCB, 0x96, 0xAA, 0xE8}};

static const asf_guid_t asf_guid_extended_content_description =
{0xD2D0A440, 0xE307, 0x11D2, {0x97, 0xF0, 0x00, 0xA0, 0xC9, 0x5E, 0xA8, 0x50}};

/* header extension level object guids */

static const asf_guid_t asf_guid_metadata =
{0xC5F8CBEA, 0x5BAF, 0x4877, {0x84, 0x67, 0xAA, 0x8C, 0x44, 0xFA, 0x4C, 0xCA}};

//add by qian

static const asf_guid_t asf_guid_metadata_library =
{0x44231C94, 0x9498, 0x49D1, {0xA1, 0x41, 0x1D, 0x13, 0x4E, 0x45, 0x70, 0x54}};

/* TODO */
static const asf_guid_t asf_guid_language_list =
{0x7C4346A9, 0xEFE0, 0x4BFC, {0xB2, 0x29, 0x39, 0x3E, 0xDE, 0x41, 0x5C, 0x85}};

static const asf_guid_t asf_guid_extended_stream_properties =
{0x14E6A5CB, 0xC672, 0x4332, {0x83, 0x99, 0xA9, 0x69, 0x52, 0x06, 0x5B, 0x5A}};

static const asf_guid_t asf_guid_advanced_mutual_exclusion =
{0xA08649CF, 0x4775, 0x4670, {0x8A, 0x16, 0x6E, 0x35, 0x35, 0x75, 0x66, 0xCD}};

static const asf_guid_t asf_guid_stream_prioritization =
{0xD4FED15B, 0x88D3, 0x454F, {0x81, 0xF0, 0xED, 0x5C, 0x45, 0x99, 0x9E, 0x24}};


// add by qian:GUID_INDEX_PARAMETERS_OBJECT
static const asf_guid_t asf_guid_index_parameters =
{0xD6E229DF, 0x35DA, 0x11D1, {0x90, 0x34, 0x00, 0xA0, 0xC9, 0x03, 0x49, 0xBE}};

/* stream type guids */

static const asf_guid_t asf_guid_stream_type_audio =
{0xF8699E40, 0x5B4D, 0x11CF, {0xA8, 0xFD, 0x00, 0x80, 0x5F, 0x5C, 0x44, 0x2B}};

static const asf_guid_t asf_guid_stream_type_video =
{0xbc19efc0, 0x5B4D, 0x11CF, {0xA8, 0xFD, 0x00, 0x80, 0x5F, 0x5C, 0x44, 0x2B}};

static const asf_guid_t asf_guid_stream_type_command =
{0x59DACFC0, 0x59E6, 0x11D0, {0xA3, 0xAC, 0x00, 0xA0, 0xC9, 0x03, 0x48, 0xF6}};

static const asf_guid_t asf_guid_stream_type_extended =
{0x3AFB65E2, 0x47EF, 0x40F2, {0xAC, 0x2C, 0x70, 0xA9, 0x0D, 0x71, 0xD3, 0x43}};

static const asf_guid_t asf_guid_stream_type_extended_audio =
{0x31178C9D, 0x03E1, 0x4528, {0xB5, 0x82, 0x3D, 0xF9, 0xDB, 0x22, 0xF5, 0x03}};



int ASFParser::asf_guid_match(const asf_guid_t *guid1, const asf_guid_t *guid2)
{
        if((guid1->v1 != guid2->v1) ||
           (guid1->v2 != guid2->v2) ||
           (guid1->v3 != guid2->v3) ||
           (memcmp(guid1->v4, guid2->v4, 8))) {
                return 0;
        }

        return 1;
}

guid_type_t ASFParser::asf_guid_get_object_type(const asf_guid_t *guid)
{
        guid_type_t ret = GUID_UNKNOWN;

        if (asf_guid_match(guid, &asf_guid_header))
                ret = GUID_HEADER;
        else if (asf_guid_match(guid, &asf_guid_data))
                ret = GUID_DATA;
        else if (asf_guid_match(guid, &asf_guid_simple_index))
                ret = GUID_SIMPLE_INDEX;
        else if (asf_guid_match(guid, &asf_guid_index))
                ret = GUID_INDEX;
        
        else if (asf_guid_match(guid, &asf_guid_file_properties))
                ret = GUID_FILE_PROPERTIES;
        else if (asf_guid_match(guid, &asf_guid_stream_properties))
                ret = GUID_STREAM_PROPERTIES;
        else if (asf_guid_match(guid, &asf_guid_content_description))
                ret = GUID_CONTENT_DESCRIPTION;
        else if (asf_guid_match(guid, &asf_guid_header_extension))
                ret = GUID_HEADER_EXTENSION;
        else if (asf_guid_match(guid, &asf_guid_marker))
                ret = GUID_MARKER;
        else if (asf_guid_match(guid, &asf_guid_codec_list))
                ret = GUID_CODEC_LIST;
        //add by qian-->
        
        else if (asf_guid_match(guid, &asf_guid_content_branding))  
                ret = GUID_CONTENT_BRANDING;
        else if (asf_guid_match(guid, &asf_guid_content_encription))
                ret = GUID_CONTENT_ENCRYPTION;
        //<--end        
        else if (asf_guid_match(guid, &asf_guid_stream_bitrate_properties))
                ret = GUID_STREAM_BITRATE_PROPERTIES;
        else if (asf_guid_match(guid, &asf_guid_padding))
                ret = GUID_PADDING;
        else if (asf_guid_match(guid, &asf_guid_extended_content_description))
                ret = GUID_EXTENDED_CONTENT_DESCRIPTION;

        else if (asf_guid_match(guid, &asf_guid_metadata))
                ret = GUID_METADATA;
        else if (asf_guid_match(guid, &asf_guid_language_list))
                ret = GUID_LANGUAGE_LIST;
        else if (asf_guid_match(guid, &asf_guid_extended_stream_properties))
                ret = GUID_EXTENDED_STREAM_PROPERTIES;
        else if (asf_guid_match(guid, &asf_guid_advanced_mutual_exclusion))
                ret = GUID_ADVANCED_MUTUAL_EXCLUSION;
        else if (asf_guid_match(guid, &asf_guid_stream_prioritization))
                ret = GUID_STREAM_PRIORITIZATION;

        else if(asf_guid_match(guid, &asf_guid_index_parameters))
                ret = GUID_INDEX_PARAMETERS_OBJECT;
        else if (asf_guid_match(guid, &asf_guid_metadata_library))
		  ret = GUID_METADATA_LIBRARY;

        return ret;
}

guid_type_t ASFParser::asf_guid_get_stream_type(const asf_guid_t *guid)
{
        guid_type_t ret = GUID_UNKNOWN;

        if (asf_guid_match(guid, &asf_guid_stream_type_audio))
                ret = GUID_STREAM_TYPE_AUDIO;
        else if (asf_guid_match(guid, &asf_guid_stream_type_video))
                ret = GUID_STREAM_TYPE_VIDEO;
        else if (asf_guid_match(guid, &asf_guid_stream_type_command))
                ret = GUID_STREAM_TYPE_COMMAND;
        else if (asf_guid_match(guid, &asf_guid_stream_type_extended))
                ret = GUID_STREAM_TYPE_EXTENDED;
        else if (asf_guid_match(guid, &asf_guid_stream_type_extended_audio))
                ret = GUID_STREAM_TYPE_EXTENDED_AUDIO;

        return ret;
}

guid_type_t ASFParser::asf_guid_get_type(const asf_guid_t *guid)
{
        guid_type_t ret;

        ret = asf_guid_get_object_type(guid);
        if (ret == GUID_UNKNOWN) {
                ret = asf_guid_get_stream_type(guid);
        }

        return ret;
}
