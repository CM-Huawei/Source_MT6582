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

#ifndef _MTK_HARDWARE_INCLUDE_MTKCAM_UTILS_IONIMAGEBUFFERHEAP_H_
#define _MTK_HARDWARE_INCLUDE_MTKCAM_UTILS_IONIMAGEBUFFERHEAP_H_
//
#include "BaseImageBufferHeap.h"
struct ion_handle;


/******************************************************************************
 *
 ******************************************************************************/
namespace NSCam {


/******************************************************************************
 *  Image Buffer Heap (ION).
 ******************************************************************************/
class IonImageBufferHeap : public NSImageBufferHeap::BaseImageBufferHeap
{
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//  Interface.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
public:     ////                    Params for Allocations.
    typedef IImageBufferAllocator::ImgParam AllocImgParam_t;

                                    struct AllocExtraParam
                                    {
                                    MINT32              nocache;
                                    MINT32              security;
                                    MINT32              coherence;
                                                        //
                                                        AllocExtraParam(
                                                            MINT32 _nocache = 0, 
                                                            MINT32 _security = 0, 
                                                            MINT32 _coherence = 0
                                                        )
                                                            : nocache(_nocache)
                                                            , security(_security)
                                                            , coherence(_coherence)
                                                        {
                                                        }
                                    };

public:     ////                    Creation.
    static  IonImageBufferHeap*     create(
                                        char const* szCallerName,
                                        AllocImgParam_t const& rImgParam, 
                                        AllocExtraParam const& rExtraParam = AllocExtraParam()
                                    );

public:     ////                    Attributes.
    static  char const*             magicName() { return "IonImageBufferHeap"; }

//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//  BaseImageBufferHeap Interface.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
protected:  ////
    virtual char const*             impGetMagicName()                   const   { return magicName(); }

    virtual HeapInfoVect_t const&   impGetHeapInfo()                    const   { return *(HeapInfoVect_t*)(&mvHeapInfo); }

    virtual MBOOL                   impInit(BufInfoVect_t const& rvBufInfo);
    virtual MBOOL                   impUninit(BufInfoVect_t const& rvBufInfo);

public:     ////
    virtual MBOOL                   impLockBuf(
                                        char const* szCallerName, 
                                        MINT usage, 
                                        BufInfoVect_t const& rvBufInfo
                                    );
    virtual MBOOL                   impUnlockBuf(
                                        char const* szCallerName, 
                                        MINT usage, 
                                        BufInfoVect_t const& rvBufInfo
                                    );

//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//  Definitions.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
protected:  ////                    Heap Info.
                                    struct MyHeapInfo : public HeapInfo
                                    {
                                    struct ion_handle*  pIonHandle;
                                                        //
                                                        MyHeapInfo()
                                                            : HeapInfo()
                                                            , pIonHandle(NULL)
                                                        {
                                                        }
                                    };
    typedef android::Vector<android::sp<MyHeapInfo> >   MyHeapInfoVect_t;

protected:  ////                    Buffer Info.
                                    struct MyBufInfo : public BufInfo
                                    {
                                    MINT32              iBoundaryInBytesToAlloc;
                                                        //
                                                        MyBufInfo()
                                                            : BufInfo()
                                                            , iBoundaryInBytesToAlloc(0)
                                                        {
                                                        }
                                    };
    typedef android::Vector<android::sp<MyBufInfo> >    MyBufInfoVect_t;

//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//  Implementations.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
protected:  ////
    virtual MBOOL                   doAllocIon(MyHeapInfo& rHeapInfo, MyBufInfo& rBufInfo);
    virtual MVOID                   doDeallocIon(MyHeapInfo& rHeapInfo, MyBufInfo& rBufInfo);

    virtual MBOOL                   doMapPhyAddr(char const* szCallerName, MyHeapInfo const& rHeapInfo, BufInfo& rBufInfo);
    virtual MBOOL                   doUnmapPhyAddr(char const* szCallerName, MyHeapInfo const& rHeapInfo, BufInfo& rBufInfo);

    virtual MBOOL                   doFlushCache();

//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//  Instantiation.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
protected:  ////                    Destructor/Constructors.

    /**
     * Disallowed to directly delete a raw pointer.
     */
    virtual                         ~IonImageBufferHeap() {}
                                    IonImageBufferHeap(
                                        char const* szCallerName,
                                        AllocImgParam_t const& rImgParam, 
                                        AllocExtraParam const& rExtraParam
                                    );

//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//  Data Members.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
protected:  ////                    Info to Allocate.
    AllocExtraParam                 mExtraParam;
    MSize                           mBufStridesInPixelsToAlloc[3];  // buffer strides in pixels.
    MINT32                          mBufBoundaryInBytesToAlloc[3];  // the address will be a multiple of boundary in bytes, which must be a power of two.

protected:  ////                    Info of Allocated Result.
    MINT32                          mIonDevFD;      //  ION Device FD.
    MyHeapInfoVect_t                mvHeapInfo;     //
    MyBufInfoVect_t                 mvBufInfo;      //

};


/******************************************************************************
 *
 ******************************************************************************/
};  // namespace NSCam
#endif  //_MTK_HARDWARE_INCLUDE_MTKCAM_UTILS_IONIMAGEBUFFERHEAP_H_

