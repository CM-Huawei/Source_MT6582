/*****************************************************************************
*  Copyright Statement:
*  --------------------
*  This software is protected by Copyright and the information contained
*  herein is confidential. The software may not be copied and the information
*  contained herein may not be used or disclosed except with the written
*  permission of MediaTek Inc. (C) 2012
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
/*******************************************************************************
 * Filename:
 * ---------
 *  mtk_nfc_dynamic_load.h
 *
 * Project:
 * --------
 *  
 *
 * Description:
 * ------------
 *  Operation System Abstration Layer Implementation
 *
 * Author:
 * -------
 *  LiangChi Huang, ext 25609, liangchi.huang@mediatek.com, 2012-12-17
 * 
 *******************************************************************************/

#ifndef MTK_NFC_DYNAMIC_LOAD_H
#define MTK_NFC_DYNAMIC_LOAD_H

#ifdef __cplusplus
extern "C" {
#endif

/***************************************************************************** 
 * Include
 *****************************************************************************/ 
#include <cutils/log.h>

/***************************************************************************** 
 * Define
 *****************************************************************************/
  
//macro for /dev/msr3110
#define MSR3110_DEV_MAGIC_ID 0xCD
#define MSR3110_IOCTL_SET_VEN _IOW( MSR3110_DEV_MAGIC_ID, 0x01, int)
#define MSR3110_IOCTL_SET_RST _IOW( MSR3110_DEV_MAGIC_ID, 0x02, int)
#define MSR3110_IOCTL_IRQ_REG _IOW( MSR3110_DEV_MAGIC_ID, 0x05, int)
#define MSR3110_IOCTL_CHIP_DETECT  _IOW( MSR3110_DEV_MAGIC_ID, 0x06, int)
#define MSR3110_IOCTL_DRIVER_INIT    _IOW( MSR3110_DEV_MAGIC_ID, 0x07, int)

/***************************************************************************** 
 * NFC Return Value for APIs
 *****************************************************************************/


/***************************************************************************** 
 * NFC specific types
 *****************************************************************************/


/***************************************************************************** 
 * Enum
 *****************************************************************************/
typedef enum
{
  MTK_NFC_CHIP_TYPE_UNKNOW,
  MTK_NFC_CHIP_TYPE_MSR3110,
  MTK_NFC_CHIP_TYPE_MT6605, 	
  MTK_NFC_CHIP_TYPE_LIST_END
} MTK_NFC_CHIP_TYPE_E;

/***************************************************************************** 
 * Data Structure
 *****************************************************************************/


/***************************************************************************** 
 * Extern Area
 *****************************************************************************/ 

/***************************************************************************** 
 * Function Prototypes
 *****************************************************************************/
MTK_NFC_CHIP_TYPE_E mtk_nfc_get_chip_type (void);

MTK_NFC_CHIP_TYPE_E msr_nfc_get_chip_type(void);

int query_nfc_chip(void);
void update_nfc_chip(int type);

int NativeDynamicLoad_queryVersion(void);

#ifdef __cplusplus
   }  /* extern "C" */
#endif

#endif
