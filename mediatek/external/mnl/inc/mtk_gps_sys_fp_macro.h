/*****************************************************************************
*  Copyright Statement:
*  --------------------
*  This software is protected by Copyright and the information contained
*  herein is confidential. The software may not be copied and the information
*  contained herein may not be used or disclosed except with the written
*  permission of MediaTek Inc. (C) 2008
*
*  BY OPENING THIS FILE, BUYER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
*  THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
*  RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO BUYER ON
*  AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
*  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
*  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
*  NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
*  SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
*  SUPPLIED WITH THE MEDIATEK SOFTWARE, AND BUYER AGREES TO LOOK ONLY TO SUCH
*  THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. MEDIATEK SHALL ALSO
*  NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE RELEASES MADE TO BUYER'S
*  SPECIFICATION OR TO CONFORM TO A PARTICULAR STANDARD OR OPEN FORUM.
*
*  BUYER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND CUMULATIVE
*  LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
*  AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
*  OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY BUYER TO
*  MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
*
*  THE TRANSACTION CONTEMPLATED HEREUNDER SHALL BE CONSTRUED IN ACCORDANCE
*  WITH THE LAWS OF THE STATE OF CALIFORNIA, USA, EXCLUDING ITS CONFLICT OF
*  LAWS PRINCIPLES.  ANY DISPUTES, CONTROVERSIES OR CLAIMS ARISING THEREOF AND
*  RELATED THERETO SHALL BE SETTLED BY ARBITRATION IN SAN FRANCISCO, CA, UNDER
*  THE RULES OF THE INTERNATIONAL CHAMBER OF COMMERCE (ICC).
*
*****************************************************************************/

/*****************************************************************************
 *
 * Filename:
 * ---------
 *   mtk_gps_sys_fp.h
 *
 * Description:
 * ------------
 *   Marco porting layer APT to Function pointer using by MTK navigation library
 *
 ****************************************************************************/

#ifndef MTK_GPS_SYS_FP_MACRO_H
#define MTK_GPS_SYS_FP_MACRO_H


#ifdef __cplusplus
  extern "C" {
#endif


#define  mtk_gps_sys_gps_mnl_callback                    gfpmtk_gps_sys_gps_mnl_callback
#define  mtk_gps_sys_nmea_output_to_app                  gfpmtk_gps_sys_nmea_output_to_app
#define  mtk_gps_sys_frame_sync_enable_sleep_mode        gfpmtk_gps_sys_frame_sync_enable_sleep_mode
#define  mtk_gps_sys_frame_sync_meas_req_by_network      gfpmtk_gps_sys_frame_sync_meas_req_by_network
#define  mtk_gps_sys_frame_sync_meas_req                 gfpmtk_gps_sys_frame_sync_meas_req
#define  mtk_gps_sys_agps_disaptcher_callback            gfpmtk_gps_sys_agps_disaptcher_callback
#define  mtk_gps_sys_pmtk_cmd_cb                         gfpmtk_gps_sys_pmtk_cmd_cb
#define  SUPL_encrypt                                    gfpSUPL_encrypt
#define  SUPL_decrypt                                    gfpSUPL_decrypt





#ifdef __cplusplus
   }
#endif

#endif /* MTK_GPS_SYS_FP_MACRO_H */