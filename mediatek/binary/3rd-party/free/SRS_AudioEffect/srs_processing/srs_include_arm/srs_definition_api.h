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
 *  SRS Definition public APIs header file
 *
 *	Author: Oscar Huang
 *	
 *	(RCS keywords below, do not edit)
 *  $Id: //srstech/srs_common/std_fxp/include/srs_definition_api.h#4 $
 *  $Author: oscarh $
 *  $Date: 2010/09/21 $
********************************************************************************/

#ifndef __SRS_DEFINITION_API_H__
#define __SRS_DEFINITION_API_H__

#include "srs_typedefs.h"
#include "srs_fxp.h"
#include "srs_definition_def.h"


#ifdef __cplusplus
extern "C"{
#endif /*__cplusplus*/

SRSDefinitionObj	SRS_CreateDefinitionObj(void *pBuf);
void		SRS_InitDefinitionObj8k(SRSDefinitionObj focusObj);	
void		SRS_InitDefinitionObj11k(SRSDefinitionObj focusObj);
void		SRS_InitDefinitionObj16k(SRSDefinitionObj focusObj);
void		SRS_InitDefinitionObj22k(SRSDefinitionObj focusObj);
void		SRS_InitDefinitionObj24k(SRSDefinitionObj focusObj);
void		SRS_InitDefinitionObj32k(SRSDefinitionObj focusObj);
void		SRS_InitDefinitionObj44k(SRSDefinitionObj focusObj);
void		SRS_InitDefinitionObj48k(SRSDefinitionObj focusObj);
void		SRS_InitDefinitionObj88k(SRSDefinitionObj focusObj);
void		SRS_InitDefinitionObj96k(SRSDefinitionObj focusObj);

void		SRS_SetDefinitionControlDefaults(SRSDefinitionObj defObj);

void		SRS_DefinitionProcess(SRSDefinitionObj defObj, srs_int32 *audioIO, int blockSize);
void		SRS_Definition(SRSDefinitionObj defObj, srs_int32 *audioIO, int blockSize, void *ws);


void		SRS_SetDefinitionEnable(SRSDefinitionObj defObj, int enable);
int			SRS_GetDefinitionEnable(SRSDefinitionObj defObj);
SRSResult	SRS_SetDefinitionInputGain(SRSDefinitionObj defObj, srs_int16 gain);
srs_int16	SRS_GetDefinitionInputGain(SRSDefinitionObj defObj);
SRSResult	SRS_SetDefinitionOutputGain(SRSDefinitionObj defObj, srs_int16 gain);
srs_int16	SRS_GetDefinitionOutputGain(SRSDefinitionObj defObj);
SRSResult	SRS_SetDefinitionBypassGain(SRSDefinitionObj defObj, srs_int16 gain);
srs_int16	SRS_GetDefinitionBypassGain(SRSDefinitionObj defObj);
SRSResult	SRS_SetDefinitionFactor(SRSDefinitionObj defObj, srs_int16 factor);
srs_int16	SRS_GetDefinitionFactor(SRSDefinitionObj defObj);

#ifdef __cplusplus
}
#endif /*__cplusplus*/


////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////



#endif //__SRS_DEFINITION_API_H__
