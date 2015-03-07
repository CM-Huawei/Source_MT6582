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

/**  libasf - An Advanced Systems Format media file parser   
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

#ifndef FILEIO_H
#include "byteio.h"
#endif

uint16_t ASFByteIO::asf_byteio_getWLE(uint8_t *data)
{
        uint16_t ret;
        int i;

        for (i=1, ret=0; i>=0; i--) {
                ret <<= 8;
                ret |= data[i];
        }

        return ret;
}

uint32_t ASFByteIO::asf_byteio_getDWLE(uint8_t *data)
{
        uint32_t ret;
        int i;

        for (i=3, ret=0; i>=0; i--) {
                ret <<= 8;
                ret |= data[i];
        }

        return ret;
}

uint64_t ASFByteIO::asf_byteio_getQWLE(uint8_t *data)
{
        uint64_t ret;
        int i;

        for (i=7, ret=0; i>=0; i--) {
                ret <<= 8;
                ret |= data[i];
        }

        return ret;
}

void ASFByteIO::asf_byteio_getGUID(asf_guid_t *guid, uint8_t *data)
{
        guid->v1 = asf_byteio_getDWLE(data);
        guid->v2 = asf_byteio_getWLE(data + 4);
        guid->v3 = asf_byteio_getWLE(data + 6);
        memcpy(guid->v4, data + 8, 8);
}

void ASFByteIO::asf_byteio_get_string(uint16_t *string, uint16_t strlen, uint8_t *data)
{
        int i;

        if (!data || !string)
                return;

        for (i=0; i<strlen; i++) {
                string[i] = asf_byteio_getWLE(data + i*2);
        }
}

int ASFByteIO::asf_byteio_read(uint8_t *data, int size, asf_iostream_t *iostream)
{
        int read = 0, tmp;

        if (!iostream || !data || !iostream->read || !iostream->source || (size < 0)) {
                return ASF_ERROR_INTERNAL;
        }

        while ((tmp = iostream->read(iostream->source, data+read, size-read)) > 0) {
                read += tmp;

                if (read == size) {
                        return read;
                }
        }
        
        return (tmp == 0) ? ASF_ERROR_EOF : ASF_ERROR_IO;
}

