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

#ifndef _MTK_HARDWARE_INCLUDE_MTKCAM_LOG_H_
#define _MTK_HARDWARE_INCLUDE_MTKCAM_LOG_H_


/*******************************************************************************
*
********************************************************************************/

#ifndef USING_MTK_LDVT

#if 1
//
#include <cutils/xlog.h>
#define CAM_LOGV(fmt, arg...)       XLOGV(fmt"\r\n", ##arg)
#define CAM_LOGD(fmt, arg...)       XLOGD(fmt"\r\n", ##arg)
#define CAM_LOGI(fmt, arg...)       XLOGI(fmt"\r\n", ##arg)
#define CAM_LOGW(fmt, arg...)       XLOGW(fmt"\r\n", ##arg)
#define CAM_LOGE(fmt, arg...)       XLOGE(fmt" (%s){#%d:%s}""\r\n", ##arg, __FUNCTION__, __LINE__, __FILE__)
//
#else
//
#include <cutils/log.h>
#define CAM_LOGV(fmt, arg...)       ALOGV(fmt"\r\n", ##arg)
#define CAM_LOGD(fmt, arg...)       ALOGD(fmt"\r\n", ##arg)
#define CAM_LOGI(fmt, arg...)       ALOGI(fmt"\r\n", ##arg)
#define CAM_LOGW(fmt, arg...)       ALOGW(fmt"\r\n", ##arg)
#define CAM_LOGE(fmt, arg...)       ALOGE(fmt" (%s){#%d:%s}""\r\n", ##arg, __FUNCTION__, __LINE__, __FILE__)
//
#endif

#else //using LDVT

#ifndef DBG_LOG_TAG
#define DBG_LOG_TAG
#endif

#include "uvvf.h"
#define NEW_LINE_CHAR   "\n"

#define CAM_LOGV(fmt, arg...)        VV_MSG(DBG_LOG_TAG "[%s] " fmt NEW_LINE_CHAR, __func__, ##arg) // <Verbose>: Show more detail debug information. E.g. Entry/exit of private function; contain of local variable in function or code block; return value of system function/API...
#define CAM_LOGD(fmt, arg...)        VV_MSG(DBG_LOG_TAG "[%s] " fmt NEW_LINE_CHAR, __func__, ##arg) // <Debug>: Show general debug information. E.g. Change of state machine; entry point or parameters of Public function or OS callback; Start/end of process thread...
#define CAM_LOGI(fmt, arg...)        VV_MSG(DBG_LOG_TAG "[%s] " fmt NEW_LINE_CHAR, __func__, ##arg) // <Info>: Show general system information. Like OS version, start/end of Service...
#define CAM_LOGW(fmt, arg...)        VV_MSG(DBG_LOG_TAG "[%s] WARNING: " fmt NEW_LINE_CHAR, __func__, ##arg)    // <Warning>: Some errors are encountered, but after exception handling, user won't notice there were errors happened.
#define CAM_LOGE(fmt, arg...)        VV_ERRMSG(DBG_LOG_TAG "[%s, %s, line%04d] ERROR: " fmt NEW_LINE_CHAR, __FILE__, __func__, __LINE__, ##arg) // When MP, will only show log of this level. // <Fatal>: Serious error that cause program can not execute. <Error>: Some error that causes some part of the functionality can not operate normally.
#define BASE_LOG_AST(cond, fmt, arg...)     \
    do {        \
        if (!(cond))        \
            VV_ERRMSG("[%s, %s, line%04d] ASSERTION FAILED! : " fmt NEW_LINE_CHAR, __FILE__, __func__, __LINE__, ##arg);        \
    } while (0)

#endif
//
//  ASSERT
#define CAM_LOGA(...) \
    do { \
        CAM_LOGE("[Assert] "__VA_ARGS__); \
        while(1) { ::usleep(500 * 1000); } \
    } while (0)
//
//
//  FATAL
#define CAM_LOGF(...) \
    do { \
        CAM_LOGE("[Fatal] "__VA_ARGS__); \
        LOG_ALWAYS_FATAL_IF(1, "(%s){#%d:%s}""\r\n", __FUNCTION__, __LINE__, __FILE__); \
    } while (0)


/*******************************************************************************
*
********************************************************************************/
#define CAM_LOGV_IF(cond, ...)      do { if ( (cond) ) { CAM_LOGV(__VA_ARGS__); } }while(0)
#define CAM_LOGD_IF(cond, ...)      do { if ( (cond) ) { CAM_LOGD(__VA_ARGS__); } }while(0)
#define CAM_LOGI_IF(cond, ...)      do { if ( (cond) ) { CAM_LOGI(__VA_ARGS__); } }while(0)
#define CAM_LOGW_IF(cond, ...)      do { if ( (cond) ) { CAM_LOGW(__VA_ARGS__); } }while(0)
#define CAM_LOGE_IF(cond, ...)      do { if ( (cond) ) { CAM_LOGE(__VA_ARGS__); } }while(0)
#define CAM_LOGA_IF(cond, ...)      do { if ( (cond) ) { CAM_LOGA(__VA_ARGS__); } }while(0)
#define CAM_LOGF_IF(cond, ...)      do { if ( (cond) ) { CAM_LOGF(__VA_ARGS__); } }while(0)


/*******************************************************************************
*
********************************************************************************/
#endif  //_MTK_HARDWARE_INCLUDE_MTKCAM_LOG_H_

