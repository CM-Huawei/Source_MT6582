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

#define LOG_TAG "MtkCam/DisplayClient"
//
#include <utils/List.h>
#include <cutils/properties.h>
//
#include <mtkcam/v1/PriorityDefs.h>
#include "CamUtils.h"
#include "DisplayThread.h"
//
#include <sys/prctl.h>
//
using namespace android;
using namespace NSDisplayClient;


/******************************************************************************
*
*******************************************************************************/
#define MY_LOGV(fmt, arg...)        CAM_LOGV("(%d)[DisplayThread::%s] "fmt, ::gettid(), __FUNCTION__, ##arg)
#define MY_LOGD(fmt, arg...)        CAM_LOGD("(%d)[DisplayThread::%s] "fmt, ::gettid(), __FUNCTION__, ##arg)
#define MY_LOGI(fmt, arg...)        CAM_LOGI("(%d)[DisplayThread::%s] "fmt, ::gettid(), __FUNCTION__, ##arg)
#define MY_LOGW(fmt, arg...)        CAM_LOGW("(%d)[DisplayThread::%s] "fmt, ::gettid(), __FUNCTION__, ##arg)
#define MY_LOGE(fmt, arg...)        CAM_LOGE("(%d)[DisplayThread::%s] "fmt, ::gettid(), __FUNCTION__, ##arg)
#define MY_LOGA(fmt, arg...)        CAM_LOGA("(%d)[DisplayThread::%s] "fmt, ::gettid(), __FUNCTION__, ##arg)
#define MY_LOGF(fmt, arg...)        CAM_LOGF("(%d)[DisplayThread::%s] "fmt, ::gettid(), __FUNCTION__, ##arg)
//
#define MY_LOGV_IF(cond, ...)       do { if ( (cond) ) { MY_LOGV(__VA_ARGS__); } }while(0)
#define MY_LOGD_IF(cond, ...)       do { if ( (cond) ) { MY_LOGD(__VA_ARGS__); } }while(0)
#define MY_LOGI_IF(cond, ...)       do { if ( (cond) ) { MY_LOGI(__VA_ARGS__); } }while(0)
#define MY_LOGW_IF(cond, ...)       do { if ( (cond) ) { MY_LOGW(__VA_ARGS__); } }while(0)
#define MY_LOGE_IF(cond, ...)       do { if ( (cond) ) { MY_LOGE(__VA_ARGS__); } }while(0)
#define MY_LOGA_IF(cond, ...)       do { if ( (cond) ) { MY_LOGA(__VA_ARGS__); } }while(0)
#define MY_LOGF_IF(cond, ...)       do { if ( (cond) ) { MY_LOGF(__VA_ARGS__); } }while(0)


/*******************************************************************************
*   DisplayThread Class
*******************************************************************************/
class DisplayThread : public IDisplayThread
{
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//  Operations in base class Thread
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
public:     ////
    // Ask this object's thread to exit. This function is asynchronous, when the
    // function returns the thread might still be running. Of course, this
    // function can be called from a different thread.
    virtual void                requestExit();

    // Good place to do one-time initializations
    virtual status_t            readyToRun();

private:
    // Derived class must implement threadLoop(). The thread starts its life
    // here. There are two ways of using the Thread object:
    // 1) loop: if threadLoop() returns true, it will be called again if
    //          requestExit() wasn't called.
    // 2) once: if threadLoop() returns false, the thread will exit upon return.
    virtual bool                threadLoop();

//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//  Operations in IDisplayThread
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
public:     ////                Attributes.
    virtual int32_t             getTid() const          { return mi4Tid; }
    virtual bool                isExitPending() const   { return exitPending(); }

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
public:     ////                Interfaces.
    virtual void                postCommand(Command const& rCmd);

//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//  Implementation.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
public:     ////                Instantiation.
                                DisplayThread(IDisplayThreadHandler*const pHandler);

protected:  ////                Data Members.
    sp<IDisplayThreadHandler>   mpThreadHandler;
    int32_t                     mi4Tid;
    int32_t                     miLogLevel;

//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//  Commands.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
public:     ////                Operations.
    virtual bool                getCommand(Command& rCmd);

protected:  ////                Data Members.
    List<Command>               mCmdQue;
    Mutex                       mCmdQueMtx;
    Condition                   mCmdQueCond;    //  Condition to wait: [ ! exitPending() && mCmdQue.empty() ]
};


/******************************************************************************
*
*******************************************************************************/
char const*
Command::
getName(EID const _eId)
{
#define CMD_NAME(x) case x: return #x
    switch  (_eId)
    {
    CMD_NAME(eID_EXIT);
    CMD_NAME(eID_WAKEUP);
    default:
        break;
    }
#undef  CMD_NAME
    return  "";
}


/******************************************************************************
*
*******************************************************************************/
DisplayThread::
DisplayThread(IDisplayThreadHandler*const pHandler)
    : IDisplayThread()
    //
    , mpThreadHandler(pHandler)
    , mi4Tid(0)
    , miLogLevel(1)
    //
    , mCmdQue()
    , mCmdQueMtx()
    , mCmdQueCond()
{
    char cLogLevel[PROPERTY_VALUE_MAX] = {'\0'};
    ::property_get("debug.camera.display.loglevel", cLogLevel, "1");
    miLogLevel = ::atoi(cLogLevel);
}


/******************************************************************************
*
*******************************************************************************/
// Ask this object's thread to exit. This function is asynchronous, when the
// function returns the thread might still be running. Of course, this
// function can be called from a different thread.
void
DisplayThread::
requestExit()
{
    MY_LOGD("+");
    Thread::requestExit();
    //
    postCommand(Command(Command::eID_EXIT));
    //
    MY_LOGD("-");
}


/******************************************************************************
*
*******************************************************************************/
// Good place to do one-time initializations
status_t
DisplayThread::
readyToRun()
{
    ::prctl(PR_SET_NAME,(unsigned long)"Camera@Display", 0, 0, 0);
    //
    mi4Tid = ::gettid();

    //  thread policy & priority
    //  Notes:
    //      Even if pthread_create() with SCHED_OTHER policy, a newly-created thread 
    //      may inherit the non-SCHED_OTHER policy & priority of the thread creator.
    //      And thus, we must set the expected policy & priority after a thread creation.
    int const policy    = SCHED_RR;
    int const priority  = PRIO_RT_CAMERA_DISPLAY_CLIENT;
    //
    struct sched_param sched_p;
    ::sched_getparam(0, &sched_p);
    //
    //  set
    sched_p.sched_priority = priority;  //  Note: "priority" is real-time priority.
    ::sched_setscheduler(0, policy, &sched_p);
    //
    //  get
    ::sched_getparam(0, &sched_p);
    //
    MY_LOGD(
        "policy:(expect, result)=(%d, %d), priority:(expect, result)=(%d, %d)"
        , policy, ::sched_getscheduler(0)
        , priority, sched_p.sched_priority
    );
    return NO_ERROR;
}


/******************************************************************************
*
*******************************************************************************/
void
DisplayThread::
postCommand(Command const& rCmd)
{
    Mutex::Autolock _lock(mCmdQueMtx);
    //
    if  ( ! mCmdQue.empty() )
    {
        Command const& rBegCmd = *mCmdQue.begin();
        MY_LOGW("que size:%d > 0 with begin cmd::%s", mCmdQue.size(), rBegCmd.name());
    }
    //
    mCmdQue.push_back(rCmd);
    mCmdQueCond.broadcast();
    //
    MY_LOGD("- new command::%s", rCmd.name());
}


/******************************************************************************
*
*******************************************************************************/
bool
DisplayThread::
getCommand(Command& rCmd)
{
    bool ret = false;
    //
    Mutex::Autolock _lock(mCmdQueMtx);
    //
    MY_LOGD_IF((1<=miLogLevel), "+ que size(%d)", mCmdQue.size());
    //
    //  Wait until the queue is not empty or this thread will exit.
    while   ( mCmdQue.empty() && ! exitPending() )
    {
        status_t status = mCmdQueCond.wait(mCmdQueMtx);
        if  ( NO_ERROR != status )
        {
            MY_LOGW("wait status(%d), que size(%d), exitPending(%d)", status, mCmdQue.size(), exitPending());
        }
    }
    //
    if  ( ! mCmdQue.empty() )
    {
        //  If the queue is not empty, take the first command from the queue.
        ret = true;
        rCmd = *mCmdQue.begin();
        mCmdQue.erase(mCmdQue.begin());
        MY_LOGD("command:%s", rCmd.name());
    }
    //
    MY_LOGD_IF((1<=miLogLevel), "- que size(%d), ret(%d)", mCmdQue.size(), ret);
    return  ret;
}


/******************************************************************************
*
*******************************************************************************/
bool
DisplayThread::
threadLoop()
{
    Command cmd;
    if  ( getCommand(cmd) )
    {
        switch  (cmd.eId)
        {
        case Command::eID_EXIT:
            MY_LOGD("Command::%s", cmd.name());
            break;
        //
        case Command::eID_WAKEUP:
        default:
            if  ( mpThreadHandler != 0 )
            {
                mpThreadHandler->onThreadLoop(cmd);
            }
            else
            {
                MY_LOGE("cannot handle cmd(%s) due to mpThreadHandler==NULL", cmd.name());
            }
            break;
        }
    }
    //
    MY_LOGD("- mpThreadHandler.get(%p)", mpThreadHandler.get());
    return  true;
}


/******************************************************************************
*
*******************************************************************************/
IDisplayThread*
IDisplayThread::
createInstance(IDisplayThreadHandler*const pHandler)
{
    if  ( ! pHandler ) {
        MY_LOGE("pHandler==NULL");
        return  NULL;
    }
    return  new DisplayThread(pHandler);
}

