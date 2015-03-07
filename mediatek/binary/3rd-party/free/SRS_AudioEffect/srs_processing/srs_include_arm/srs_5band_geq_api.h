/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2010. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

/********************************************************************************
 *	SRS Labs CONFIDENTIAL
 *	@Copyright 2010 by SRS Labs.
 *	All rights reserved.
 *
 *  Description:
 *  Exposes 5-band graphic EQ APIs
 *
 *  RCS keywords:
 *	$Id $
 *  $Author: oscarh $
 *  $Date: 2010/11/16 $
 *	
********************************************************************************/

#ifndef __SRS_5BAND_GRAPHICEQ_API_H__
#define __SRS_5BAND_GRAPHICEQ_API_H__

#include "srs_graphiceq_def.h"
#include "srs_graphiceq_ver_api.h"

#ifdef __cplusplus
extern "C"{
#endif /*__cplusplus*/

//API declaration here:
SRS5BandGraphicEqObj		SRS_Create5BandGraphicEqObj(void *pBuf);

void	SRS_Init5BandGraphicEqObj8k(SRS5BandGraphicEqObj geqObj);
void	SRS_Init5BandGraphicEqObj11k(SRS5BandGraphicEqObj geqObj);
void	SRS_Init5BandGraphicEqObj16k(SRS5BandGraphicEqObj geqObj);
void	SRS_Init5BandGraphicEqObj22k(SRS5BandGraphicEqObj geqObj);
void	SRS_Init5BandGraphicEqObj24k(SRS5BandGraphicEqObj geqObj);
void	SRS_Init5BandGraphicEqObj32k(SRS5BandGraphicEqObj geqObj);
void	SRS_Init5BandGraphicEqObj44k(SRS5BandGraphicEqObj geqObj);
void	SRS_Init5BandGraphicEqObj48k(SRS5BandGraphicEqObj geqObj);

void	SRS_Set5BandGraphicEqControlDefaults(SRS5BandGraphicEqObj geqObj);

void	SRS_Set5BandGraphicEqExtraBandBehavior(SRS5BandGraphicEqObj geqObj, SRS5BandGeqExtraBandBehavior behavior);
SRS5BandGeqExtraBandBehavior	SRS_Get5BandGraphicEqExtraBandBehavior(SRS5BandGraphicEqObj geqObj);

void	SRS_Set5BandGraphicEqEnable(SRS5BandGraphicEqObj geqObj, int enable);
int		SRS_Get5BandGraphicEqEnable(SRS5BandGraphicEqObj geqObj);

SRSResult	SRS_Set5BandGraphicEqBandGain(SRS5BandGraphicEqObj geqObj, int bandIndex, srs_int16 gain);
srs_int16	SRS_Get5BandGraphicEqBandGain(SRS5BandGraphicEqObj geqObj, int bandIndex);

void	SRS_Set5BandGraphicEqLimiterEnable(SRS5BandGraphicEqObj geqObj, int enable);
int		SRS_Get5BandGraphicEqLimiterEnable(SRS5BandGraphicEqObj geqObj);

void	SRS_5BandGraphicEqProcess(SRS5BandGraphicEqObj geqObj, srs_int32 *audioIO, int blockSize, void *ws);


#ifdef __cplusplus
}
#endif /*__cplusplus*/


/////////////////////////////////////////////////////////////////////////////////////////////////////////



#endif /*__SRS_5BAND_GRAPHICEQ_API_H__*/
