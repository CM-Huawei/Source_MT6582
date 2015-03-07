/*
 * Copyright (C) 2011 The Android Open Source Project
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

#define LOG_NDEBUG 0
#define LOG_TAG "PreviewController"
#include <utils/Log.h>

#include <gui/Surface.h>

#include "VideoEditorAudioPlayer.h"
#include "PreviewRenderer.h"
#include "M4OSA_Semaphore.h"
#include "M4OSA_Thread.h"
#include "VideoEditorPreviewController.h"

namespace android {


VideoEditorPreviewController::VideoEditorPreviewController()
    : mCurrentPlayer(0),
      mThreadContext(NULL),
      mPlayerState(VePlayerIdle),
      mPrepareReqest(M4OSA_FALSE),
      mClipList(NULL),
      mNumberClipsInStoryBoard(0),
      mNumberClipsToPreview(0),
      mStartingClipIndex(0),
      mPreviewLooping(M4OSA_FALSE),
      mCallBackAfterFrameCnt(0),
      mEffectsSettings(NULL),
      mNumberEffects(0),
      mCurrentClipNumber(-1),
      mClipTotalDuration(0),
      mCurrentVideoEffect(VIDEO_EFFECT_NONE),
      mBackgroundAudioSetting(NULL),
      mAudioMixPCMFileHandle(NULL),
      mTarget(NULL),
      mJniCookie(NULL),
      mJniCallback(NULL),
      mCurrentPlayedDuration(0),
      mCurrentClipDuration(0),
      mVideoStoryBoardTimeMsUptoFirstPreviewClip(0),
      mOverlayState(OVERLAY_CLEAR),
      mActivePlayerIndex(0),
      mOutputVideoWidth(0),
      mOutputVideoHeight(0),
      bStopThreadInProgress(false),
#ifndef ANDROID_DEFAULT_CODE
      mVEAudioPlayer(NULL),
      mNativeWindowRenderer(NULL),
      mOuterClipList(NULL),
#endif 
      mSemThreadWait(NULL) {
    ALOGV("VideoEditorPreviewController");
    mRenderingMode = M4xVSS_kBlackBorders;
    mIsFiftiesEffectStarted = false;

    for (int i = 0; i < kTotalNumPlayerInstances; ++i) {
        mVePlayer[i] = NULL;
    }
#ifndef ANDROID_DEFAULT_CODE
    // Demon init as NULL
    mFrameStr.pBuffer = M4OSA_NULL;
#endif
}

VideoEditorPreviewController::~VideoEditorPreviewController() {
    ALOGV("~VideoEditorPreviewController");
    M4OSA_UInt32 i = 0;
    M4OSA_ERR err = M4NO_ERROR;

    // Stop the thread if its still running
    if(mThreadContext != NULL) {
        err = M4OSA_threadSyncStop(mThreadContext);
        if(err != M4NO_ERROR) {
            ALOGV("~VideoEditorPreviewController: error 0x%x \
            in trying to stop thread", err);
            // Continue even if error
        }

        err = M4OSA_threadSyncClose(mThreadContext);
        if(err != M4NO_ERROR) {
            ALOGE("~VideoEditorPreviewController: error 0x%x \
            in trying to close thread", (unsigned int) err);
            // Continue even if error
        }

        mThreadContext = NULL;
    }

    for (int playerInst=0; playerInst<kTotalNumPlayerInstances;
         playerInst++) {
        if(mVePlayer[playerInst] != NULL) {
            ALOGV("clearing mVePlayer %d", playerInst);
            mVePlayer[playerInst].clear();
        }
    }

    if(mClipList != NULL) {
        // Clean up
        for(i=0;i<mNumberClipsInStoryBoard;i++)
        {
            if(mClipList[i]->pFile != NULL) {
                free(mClipList[i]->pFile);
                mClipList[i]->pFile = NULL;
            }

            free(mClipList[i]);
        }
        free(mClipList);
        mClipList = NULL;
    }

#ifndef ANDROID_DEFAULT_CODE //CR583138
    if(mOuterClipList != NULL) {
        free(mOuterClipList);
        mOuterClipList = NULL;
    }
#endif //ANDROID_DEFAULT_CODE

    if(mEffectsSettings) {
        for(i=0;i<mNumberEffects;i++) {
            if(mEffectsSettings[i].xVSS.pFramingBuffer != NULL) {
                free(mEffectsSettings[i].xVSS.pFramingBuffer->pac_data);

                free(mEffectsSettings[i].xVSS.pFramingBuffer);

                mEffectsSettings[i].xVSS.pFramingBuffer = NULL;
            }
        }
        free(mEffectsSettings);
        mEffectsSettings = NULL;
    }

    if (mAudioMixPCMFileHandle) {
        err = M4OSA_fileReadClose (mAudioMixPCMFileHandle);
        mAudioMixPCMFileHandle = M4OSA_NULL;
    }

    if (mBackgroundAudioSetting != NULL) {
        free(mBackgroundAudioSetting);
        mBackgroundAudioSetting = NULL;
    }

    if(mTarget != NULL) {
        delete mTarget;
        mTarget = NULL;
    }

    mOverlayState = OVERLAY_CLEAR;

    ALOGV("~VideoEditorPreviewController returns");
}

M4OSA_ERR VideoEditorPreviewController::loadEditSettings(
    M4VSS3GPP_EditSettings* pSettings,M4xVSS_AudioMixingSettings* bgmSettings) {

    M4OSA_UInt32 i = 0, iClipDuration = 0, rgbSize = 0;
    M4VIFI_UInt8 *tmp = NULL;
    M4OSA_ERR err = M4NO_ERROR;

    ALOGV("loadEditSettings");
    ALOGV("loadEditSettings Channels = %d, sampling Freq %d",
          bgmSettings->uiNbChannels, bgmSettings->uiSamplingFrequency  );
          bgmSettings->uiSamplingFrequency = 32000;

    ALOGV("loadEditSettings Channels = %d, sampling Freq %d",
          bgmSettings->uiNbChannels, bgmSettings->uiSamplingFrequency  );
    Mutex::Autolock autoLock(mLock);

    // Clean up any previous Edit settings before loading new ones
    mCurrentVideoEffect = VIDEO_EFFECT_NONE;

    if(mAudioMixPCMFileHandle) {
        err = M4OSA_fileReadClose (mAudioMixPCMFileHandle);
        mAudioMixPCMFileHandle = M4OSA_NULL;
    }

    if(mBackgroundAudioSetting != NULL) {
        free(mBackgroundAudioSetting);
        mBackgroundAudioSetting = NULL;
    }

    if(mClipList != NULL) {
        // Clean up
        for(i=0;i<mNumberClipsInStoryBoard;i++)
        {
            if(mClipList[i]->pFile != NULL) {
                free(mClipList[i]->pFile);
                mClipList[i]->pFile = NULL;
            }

            free(mClipList[i]);
        }
        free(mClipList);
        mClipList = NULL;
    }

    if(mEffectsSettings) {
        for(i=0;i<mNumberEffects;i++) {
            if(mEffectsSettings[i].xVSS.pFramingBuffer != NULL) {
                free(mEffectsSettings[i].xVSS.pFramingBuffer->pac_data);

                free(mEffectsSettings[i].xVSS.pFramingBuffer);

                mEffectsSettings[i].xVSS.pFramingBuffer = NULL;
            }
        }
        free(mEffectsSettings);
        mEffectsSettings = NULL;
    }

    if(mClipList == NULL) {
        mNumberClipsInStoryBoard = pSettings->uiClipNumber;
#ifndef ANDROID_DEFAULT_CODE
        ALOGD("loadEditSettings: # of Clips = %d", mNumberClipsInStoryBoard);
#else
        ALOGV("loadEditSettings: # of Clips = %d", mNumberClipsInStoryBoard);
#endif //ANDROID_DEFAULT_CODE
        mClipList = (M4VSS3GPP_ClipSettings**)M4OSA_32bitAlignedMalloc(
         sizeof(M4VSS3GPP_ClipSettings*)*pSettings->uiClipNumber, M4VS,
         (M4OSA_Char*)"LvPP, copy of pClipList");

        if(NULL == mClipList) {
            ALOGE("loadEditSettings: Malloc error");
            return M4ERR_ALLOC;
        }
        memset((void *)mClipList,0,
         sizeof(M4VSS3GPP_ClipSettings*)*pSettings->uiClipNumber);

        for(i=0;i<pSettings->uiClipNumber;i++) {

            // Allocate current clip
            mClipList[i] =
             (M4VSS3GPP_ClipSettings*)M4OSA_32bitAlignedMalloc(
              sizeof(M4VSS3GPP_ClipSettings),M4VS,(M4OSA_Char*)"clip settings");

            if(mClipList[i] == NULL) {

                ALOGE("loadEditSettings: Allocation error for mClipList[%d]", (int)i);
                return M4ERR_ALLOC;
            }
            // Copy plain structure
            memcpy((void *)mClipList[i],
             (void *)pSettings->pClipList[i],
             sizeof(M4VSS3GPP_ClipSettings));

            if(NULL != pSettings->pClipList[i]->pFile) {
                mClipList[i]->pFile = (M4OSA_Char*)M4OSA_32bitAlignedMalloc(
                pSettings->pClipList[i]->filePathSize, M4VS,
                (M4OSA_Char*)"pClipSettingsDest->pFile");

                if(NULL == mClipList[i]->pFile)
                {
                    ALOGE("loadEditSettings : ERROR allocating filename");
                    return M4ERR_ALLOC;
                }

                memcpy((void *)mClipList[i]->pFile,
                 (void *)pSettings->pClipList[i]->pFile,
                 pSettings->pClipList[i]->filePathSize);
            }
            else {
                ALOGE("NULL file path");
                return M4ERR_PARAMETER;
            }

            // Calculate total duration of all clips
            iClipDuration = pSettings->pClipList[i]->uiEndCutTime -
             pSettings->pClipList[i]->uiBeginCutTime;

            mClipTotalDuration = mClipTotalDuration+iClipDuration;
        }
    }
#ifndef ANDROID_DEFAULT_CODE //CR583138
    if(mOuterClipList != NULL) {
        free(mOuterClipList);
        mOuterClipList = NULL;
    }

    if(mOuterClipList == NULL) {
        ALOGV("loadEditSettings: # of mOuterClipList ");
        mOuterClipList = (M4VSS3GPP_ClipSettings**)M4OSA_32bitAlignedMalloc(
         sizeof(M4VSS3GPP_ClipSettings*)*pSettings->uiClipNumber, M4VS,
         (M4OSA_Char*)"LvPP, 2nd copy of pClipList");
        
        if(NULL == mOuterClipList) {
            ALOGE("loadEditSettings: Malloc error");
            return M4ERR_ALLOC;
        }
        memset((void *)mOuterClipList,0,
         sizeof(M4VSS3GPP_ClipSettings*)*pSettings->uiClipNumber);
        
        for(i=0;i<pSettings->uiClipNumber;i++) {
            ALOGV("pSettings->uiClipNumber %d", pSettings->uiClipNumber);
            // Allocate current clip
            mOuterClipList[i] =
             (M4VSS3GPP_ClipSettings*)M4OSA_32bitAlignedMalloc(
              sizeof(M4VSS3GPP_ClipSettings),M4VS,(M4OSA_Char*)"clip settings");
        
            if(mOuterClipList[i] == NULL) {
        
                ALOGE("loadEditSettings: Allocation error for mClipList[%d]", (int)i);
                return M4ERR_ALLOC;
            }
            ALOGV("mOuterClipList %p", mOuterClipList[i]);
            // Copy plain structure
            memcpy((void *)mOuterClipList[i],
             (void *)pSettings->pClipList[i],
             sizeof(M4VSS3GPP_ClipSettings));
            ALOGD("Clip %d, uiClipAudioDuration %d", i, mOuterClipList[i]->ClipProperties.uiClipAudioDuration);     
        }
    }
#endif //ANDROID_DEFAULT_CODE

    if(mEffectsSettings == NULL) {
        mNumberEffects = pSettings->nbEffects;
        ALOGV("loadEditSettings: mNumberEffects = %d", mNumberEffects);

        if(mNumberEffects != 0) {
            mEffectsSettings = (M4VSS3GPP_EffectSettings*)M4OSA_32bitAlignedMalloc(
             mNumberEffects*sizeof(M4VSS3GPP_EffectSettings),
             M4VS, (M4OSA_Char*)"effects settings");

            if(mEffectsSettings == NULL) {
                ALOGE("loadEffectsSettings: Allocation error");
                return M4ERR_ALLOC;
            }

            memset((void *)mEffectsSettings,0,
             mNumberEffects*sizeof(M4VSS3GPP_EffectSettings));

            for(i=0;i<mNumberEffects;i++) {

                mEffectsSettings[i].xVSS.pFramingFilePath = NULL;
                mEffectsSettings[i].xVSS.pFramingBuffer = NULL;
                mEffectsSettings[i].xVSS.pTextBuffer = NULL;

                memcpy((void *)&(mEffectsSettings[i]),
                 (void *)&(pSettings->Effects[i]),
                 sizeof(M4VSS3GPP_EffectSettings));

                if(pSettings->Effects[i].VideoEffectType ==
                 (M4VSS3GPP_VideoEffectType)M4xVSS_kVideoEffectType_Framing) {
                    // Allocate the pFraming RGB buffer
                    mEffectsSettings[i].xVSS.pFramingBuffer =
                    (M4VIFI_ImagePlane *)M4OSA_32bitAlignedMalloc(sizeof(M4VIFI_ImagePlane),
                     M4VS, (M4OSA_Char*)"lvpp framing buffer");

                    if(mEffectsSettings[i].xVSS.pFramingBuffer == NULL) {
                        ALOGE("loadEffectsSettings:Alloc error for pFramingBuf");
                        free(mEffectsSettings);
                        mEffectsSettings = NULL;
                        return M4ERR_ALLOC;
                    }

                    // Allocate the pac_data (RGB)
                    if(pSettings->Effects[i].xVSS.rgbType == M4VSS3GPP_kRGB565){
                        rgbSize =
                         pSettings->Effects[i].xVSS.pFramingBuffer->u_width *
                         pSettings->Effects[i].xVSS.pFramingBuffer->u_height*2;
                    }
                    else if(
                     pSettings->Effects[i].xVSS.rgbType == M4VSS3GPP_kRGB888) {
                        rgbSize =
                         pSettings->Effects[i].xVSS.pFramingBuffer->u_width *
                         pSettings->Effects[i].xVSS.pFramingBuffer->u_height*3;
                    }
                    else {
                        ALOGE("loadEffectsSettings: wrong RGB type");
                        free(mEffectsSettings);
                        mEffectsSettings = NULL;
                        return M4ERR_PARAMETER;
                    }

                    tmp = (M4VIFI_UInt8 *)M4OSA_32bitAlignedMalloc(rgbSize, M4VS,
                     (M4OSA_Char*)"framing buffer pac_data");

                    if(tmp == NULL) {
                        ALOGE("loadEffectsSettings:Alloc error pFramingBuf pac");
                        free(mEffectsSettings);
                        mEffectsSettings = NULL;
                        free(mEffectsSettings[i].xVSS.pFramingBuffer);

                        mEffectsSettings[i].xVSS.pFramingBuffer = NULL;
                        return M4ERR_ALLOC;
                    }
                    /* Initialize the pFramingBuffer*/
                    mEffectsSettings[i].xVSS.pFramingBuffer->pac_data = tmp;
                    mEffectsSettings[i].xVSS.pFramingBuffer->u_height =
                     pSettings->Effects[i].xVSS.pFramingBuffer->u_height;

                    mEffectsSettings[i].xVSS.pFramingBuffer->u_width =
                     pSettings->Effects[i].xVSS.pFramingBuffer->u_width;

                    mEffectsSettings[i].xVSS.pFramingBuffer->u_stride =
                     pSettings->Effects[i].xVSS.pFramingBuffer->u_stride;

                    mEffectsSettings[i].xVSS.pFramingBuffer->u_topleft =
                     pSettings->Effects[i].xVSS.pFramingBuffer->u_topleft;

                    mEffectsSettings[i].xVSS.uialphaBlendingStart =
                     pSettings->Effects[i].xVSS.uialphaBlendingStart;

                    mEffectsSettings[i].xVSS.uialphaBlendingMiddle =
                     pSettings->Effects[i].xVSS.uialphaBlendingMiddle;

                    mEffectsSettings[i].xVSS.uialphaBlendingEnd =
                     pSettings->Effects[i].xVSS.uialphaBlendingEnd;

                    mEffectsSettings[i].xVSS.uialphaBlendingFadeInTime =
                     pSettings->Effects[i].xVSS.uialphaBlendingFadeInTime;
                    mEffectsSettings[i].xVSS.uialphaBlendingFadeOutTime =
                     pSettings->Effects[i].xVSS.uialphaBlendingFadeOutTime;

                    // Copy the pFraming data
                    memcpy((void *)
                    mEffectsSettings[i].xVSS.pFramingBuffer->pac_data,
                    (void *)pSettings->Effects[i].xVSS.pFramingBuffer->pac_data,
                    rgbSize);

                    mEffectsSettings[i].xVSS.rgbType =
                     pSettings->Effects[i].xVSS.rgbType;
                }
            }
        }
    }

    if (mBackgroundAudioSetting == NULL) {

        mBackgroundAudioSetting = (M4xVSS_AudioMixingSettings*)M4OSA_32bitAlignedMalloc(
        sizeof(M4xVSS_AudioMixingSettings), M4VS,
        (M4OSA_Char*)"LvPP, copy of bgmSettings");

        if(NULL == mBackgroundAudioSetting) {
            ALOGE("loadEditSettings: mBackgroundAudioSetting Malloc failed");
            return M4ERR_ALLOC;
        }

        memset((void *)mBackgroundAudioSetting, 0,sizeof(M4xVSS_AudioMixingSettings*));
        memcpy((void *)mBackgroundAudioSetting, (void *)bgmSettings, sizeof(M4xVSS_AudioMixingSettings));

        if ( mBackgroundAudioSetting->pFile != M4OSA_NULL ) {

            mBackgroundAudioSetting->pFile = (M4OSA_Void*) bgmSettings->pPCMFilePath;
            mBackgroundAudioSetting->uiNbChannels = 2;
            mBackgroundAudioSetting->uiSamplingFrequency = 32000;
        }

        // Open the BG file
        if ( mBackgroundAudioSetting->pFile != M4OSA_NULL ) {
            err = M4OSA_fileReadOpen(&mAudioMixPCMFileHandle,
             mBackgroundAudioSetting->pFile, M4OSA_kFileRead);

            if (err != M4NO_ERROR) {
                ALOGE("loadEditSettings: mBackgroundAudio PCM File open failed");
                return M4ERR_PARAMETER;
            }
        }
    }

    mOutputVideoSize = pSettings->xVSS.outputVideoSize;
    mFrameStr.pBuffer = M4OSA_NULL;
    return M4NO_ERROR;
}

M4OSA_ERR VideoEditorPreviewController::setSurface(const sp<Surface> &surface) {
    ALOGV("setSurface");
    Mutex::Autolock autoLock(mLock);

    mSurface = surface;
    return M4NO_ERROR;
}

M4OSA_ERR VideoEditorPreviewController::startPreview(
    M4OSA_UInt32 fromMS, M4OSA_Int32 toMs, M4OSA_UInt16 callBackAfterFrameCount,
    M4OSA_Bool loop) {

    M4OSA_ERR err = M4NO_ERROR;
    M4OSA_UInt32 i = 0, iIncrementedDuration = 0;
    ALOGV("startPreview");

    if(fromMS > (M4OSA_UInt32)toMs) {
        ALOGE("startPreview: fromMS > toMs");
        return M4ERR_PARAMETER;
    }

    if(toMs == 0) {
        ALOGE("startPreview: toMs is 0");
        return M4ERR_PARAMETER;
    }

    // If already started, then stop preview first
    for(int playerInst=0; playerInst<kTotalNumPlayerInstances; playerInst++) {
        if(mVePlayer[playerInst] != NULL) {
            ALOGV("startPreview: stopping previously started preview playback");
            stopPreview();
            break;
        }
    }

    // If renderPreview was called previously, then delete Renderer object first
    if(mTarget != NULL) {
        ALOGV("startPreview: delete previous PreviewRenderer");
        delete mTarget;
        mTarget = NULL;
    }

    // Create Audio player to be used for entire
    // storyboard duration
    mVEAudioSink = new VideoEditorPlayer::VeAudioOutput();
    mVEAudioPlayer = new VideoEditorAudioPlayer(mVEAudioSink);
    mVEAudioPlayer->setAudioMixSettings(mBackgroundAudioSetting);
    mVEAudioPlayer->setAudioMixPCMFileHandle(mAudioMixPCMFileHandle);

    // Create Video Renderer to be used for the entire storyboard duration.
    uint32_t width, height;
    getVideoSizeByResolution(mOutputVideoSize, &width, &height);
    mNativeWindowRenderer = new NativeWindowRenderer(mSurface, width, height);

    ALOGV("startPreview: loop = %d", loop);
    mPreviewLooping = loop;

    ALOGV("startPreview: callBackAfterFrameCount = %d", callBackAfterFrameCount);
    mCallBackAfterFrameCnt = callBackAfterFrameCount;

    for (int playerInst=0; playerInst<kTotalNumPlayerInstances; playerInst++) {
        mVePlayer[playerInst] = new VideoEditorPlayer(mNativeWindowRenderer);
        if(mVePlayer[playerInst] == NULL) {
            ALOGE("startPreview:Error creating VideoEditorPlayer %d",playerInst);
            return M4ERR_ALLOC;
        }
        ALOGV("startPreview: object created");

        mVePlayer[playerInst]->setNotifyCallback(this,(notify_callback_f)notify);
        ALOGV("startPreview: notify callback set");

        mVePlayer[playerInst]->loadEffectsSettings(mEffectsSettings,
         mNumberEffects);
        ALOGV("startPreview: effects settings loaded");

        mVePlayer[playerInst]->loadAudioMixSettings(mBackgroundAudioSetting);
        ALOGV("startPreview: AudioMixSettings settings loaded");

        mVePlayer[playerInst]->setAudioMixPCMFileHandle(mAudioMixPCMFileHandle);
        ALOGV("startPreview: AudioMixPCMFileHandle set");

        mVePlayer[playerInst]->setProgressCallbackInterval(
         mCallBackAfterFrameCnt);
        ALOGV("startPreview: setProgressCallBackInterval");
    }

    mPlayerState = VePlayerIdle;
    mPrepareReqest = M4OSA_FALSE;

    if(fromMS == 0) {
        mCurrentClipNumber = -1;
        // Save original value
        mFirstPreviewClipBeginTime = mClipList[0]->uiBeginCutTime;
        mVideoStoryBoardTimeMsUptoFirstPreviewClip = 0;
    }
    else {
        ALOGV("startPreview: fromMS=%d", fromMS);
        if(fromMS >= mClipTotalDuration) {
            ALOGE("startPreview: fromMS >= mClipTotalDuration");
            return M4ERR_PARAMETER;
        }
        for(i=0;i<mNumberClipsInStoryBoard;i++) {
#ifndef ANDROID_DEFAULT_CODE
            ALOGV("uiEndCutTime=%d, uiBeginCutTime=%d", mClipList[i]->uiEndCutTime, mClipList[i]->uiBeginCutTime);
#endif //ANDROID_DEFAULT_CODE
            if(fromMS < (iIncrementedDuration + (mClipList[i]->uiEndCutTime -
             mClipList[i]->uiBeginCutTime))) {
                // Set to 1 index below,
                // as threadProcess first increments the clip index
                // and then processes clip in thread loop
#ifndef ANDROID_DEFAULT_CODE //CR583138
                if( (NULL == mOuterClipList[i]) ||( NULL == (&(mOuterClipList[i]->ClipProperties))) )
                {
                    ALOGW("mOuterClipList[%d] or mOuterClipList[i]->ClipProperties is null", i);
                }
                else
                {
                    ALOGV("a %d, v %d", mOuterClipList[i]->ClipProperties.uiClipAudioDuration, 
                        mOuterClipList[i]->ClipProperties.uiClipVideoDuration);
                    if( (0 != mOuterClipList[i]->ClipProperties.uiClipAudioDuration) && (fromMS > (iIncrementedDuration+mOuterClipList[i]->ClipProperties.uiClipAudioDuration)) && 
                        (mOuterClipList[i]->ClipProperties.uiClipAudioDuration < mOuterClipList[i]->ClipProperties.uiClipVideoDuration) )
                    {
                        ALOGW("current clip audio duration(%d) < fromMS(%d) < video duration(%d) that may cause reader return EOS ",
                            iIncrementedDuration+mOuterClipList[i]->ClipProperties.uiClipAudioDuration, fromMS, iIncrementedDuration+mOuterClipList[i]->ClipProperties.uiClipVideoDuration);
                        if( i < (mNumberClipsInStoryBoard-1))
                        {
                            iIncrementedDuration = iIncrementedDuration +(mClipList[i]->uiEndCutTime - mClipList[i]->uiBeginCutTime);
                            continue;
                        }
                    }
                }
#endif //ANDROID_DEFAULT_CODE

                mCurrentClipNumber = i-1;
                ALOGD("startPreview:mCurrentClipNumber = %d fromMS=%d",i,fromMS);

                // Save original value
                mFirstPreviewClipBeginTime = mClipList[i]->uiBeginCutTime;

                // Set correct begin time to start playback
                if((fromMS+mClipList[i]->uiBeginCutTime) >
                (iIncrementedDuration+mClipList[i]->uiBeginCutTime)) {

                    mClipList[i]->uiBeginCutTime =
                     mClipList[i]->uiBeginCutTime +
                     (fromMS - iIncrementedDuration);
                }
                break;
            }
            else {
                iIncrementedDuration = iIncrementedDuration +
                 (mClipList[i]->uiEndCutTime - mClipList[i]->uiBeginCutTime);
            }
        }
        mVideoStoryBoardTimeMsUptoFirstPreviewClip = iIncrementedDuration;
    }

    for (int playerInst=0; playerInst<kTotalNumPlayerInstances; playerInst++) {
        mVePlayer[playerInst]->setAudioMixStoryBoardParam(fromMS,
         mFirstPreviewClipBeginTime,
         mClipList[i]->ClipProperties.uiClipAudioVolumePercentage);

        ALOGV("startPreview:setAudioMixStoryBoardSkimTimeStamp set %d cuttime \
         %d", fromMS, mFirstPreviewClipBeginTime);
    }

    mStartingClipIndex = mCurrentClipNumber+1;

    // Start playing with player instance 0
    mCurrentPlayer = 0;
    mActivePlayerIndex = 0;

    if(toMs == -1) {
        ALOGV("startPreview: Preview till end of storyboard");
        mNumberClipsToPreview = mNumberClipsInStoryBoard;
        // Save original value
        mLastPreviewClipEndTime =
         mClipList[mNumberClipsToPreview-1]->uiEndCutTime;
    }
    else {
        ALOGV("startPreview: toMs=%d", toMs);
        if((M4OSA_UInt32)toMs > mClipTotalDuration) {
            ALOGE("startPreview: toMs > mClipTotalDuration");
            return M4ERR_PARAMETER;
        }

        iIncrementedDuration = 0;

        for(i=0;i<mNumberClipsInStoryBoard;i++) {
            if((M4OSA_UInt32)toMs <= (iIncrementedDuration +
             (mClipList[i]->uiEndCutTime - mClipList[i]->uiBeginCutTime))) {
                // Save original value
                mLastPreviewClipEndTime = mClipList[i]->uiEndCutTime;
                // Set the end cut time of clip index i to toMs
                mClipList[i]->uiEndCutTime = toMs;

                // Number of clips to be previewed is from index 0 to i
                // increment by 1 as i starts from 0
                mNumberClipsToPreview = i+1;
                break;
            }
            else {
                iIncrementedDuration = iIncrementedDuration +
                 (mClipList[i]->uiEndCutTime - mClipList[i]->uiBeginCutTime);
            }
        }
    }

    // Open the thread semaphore
    M4OSA_semaphoreOpen(&mSemThreadWait, 1);

    // Open the preview process thread
    err = M4OSA_threadSyncOpen(&mThreadContext, (M4OSA_ThreadDoIt)threadProc);
    if (M4NO_ERROR != err) {
        ALOGE("VideoEditorPreviewController:M4OSA_threadSyncOpen error %d", (int) err);
        return err;
    }

    // Set the stacksize
    err = M4OSA_threadSyncSetOption(mThreadContext, M4OSA_ThreadStackSize,
     (M4OSA_DataOption) kPreviewThreadStackSize);

    if (M4NO_ERROR != err) {
        ALOGE("VideoEditorPreviewController: threadSyncSetOption error %d", (int) err);
        M4OSA_threadSyncClose(mThreadContext);
        mThreadContext = NULL;
        return err;
    }

     // Start the thread
     err = M4OSA_threadSyncStart(mThreadContext, (M4OSA_Void*)this);
     if (M4NO_ERROR != err) {
        ALOGE("VideoEditorPreviewController: threadSyncStart error %d", (int) err);
        M4OSA_threadSyncClose(mThreadContext);
        mThreadContext = NULL;
        return err;
    }
    bStopThreadInProgress = false;

    ALOGV("startPreview: process thread started");
    return M4NO_ERROR;
}

M4OSA_UInt32 VideoEditorPreviewController::stopPreview() {
    M4OSA_ERR err = M4NO_ERROR;
    uint32_t lastRenderedFrameTimeMs = 0;
    ALOGV("stopPreview");

    // Stop the thread
    if(mThreadContext != NULL) {
        bStopThreadInProgress = true;
        {
            Mutex::Autolock autoLock(mLockSem);
            if (mSemThreadWait != NULL) {
                err = M4OSA_semaphorePost(mSemThreadWait);
            }
        }

        err = M4OSA_threadSyncStop(mThreadContext);
        if(err != M4NO_ERROR) {
            ALOGV("stopPreview: error 0x%x in trying to stop thread", err);
            // Continue even if error
        }

        err = M4OSA_threadSyncClose(mThreadContext);
        if(err != M4NO_ERROR) {
            ALOGE("stopPreview: error 0x%x in trying to close thread", (unsigned int)err);
            // Continue even if error
        }

        mThreadContext = NULL;
    }

    // Close the semaphore first
    {
        Mutex::Autolock autoLock(mLockSem);
        if(mSemThreadWait != NULL) {
            err = M4OSA_semaphoreClose(mSemThreadWait);
            ALOGV("stopPreview: close semaphore returns 0x%x", err);
            mSemThreadWait = NULL;
        }
    }

    for (int playerInst=0; playerInst<kTotalNumPlayerInstances; playerInst++) {
        if(mVePlayer[playerInst] != NULL) {
            if(mVePlayer[playerInst]->isPlaying()) {
                ALOGD("stop the player first");
#ifndef ANDROID_DEFAULT_CODE
                mVePlayer[playerInst]->stopProgressCbEvent();	
#endif				
                mVePlayer[playerInst]->stop();
            }
            if (playerInst == mActivePlayerIndex) {
                // Return the last rendered frame time stamp
                ALOGD("getLastRenderedTimeMs");
                mVePlayer[mActivePlayerIndex]->getLastRenderedTimeMs(&lastRenderedFrameTimeMs);
            }

            //This is used to syncronize onStreamDone() in PreviewPlayer and
            //stopPreview() in PreviewController
            sp<VideoEditorPlayer> temp = mVePlayer[playerInst];
            temp->acquireLock();
            ALOGD("stopPreview: clearing mVePlayer[%d]",playerInst);
            mVePlayer[playerInst].clear();
            mVePlayer[playerInst] = NULL;
            temp->releaseLock();
            usleep(5*1000);			
        }
    }
    ALOGD("stopPreview: clear audioSink and audioPlayer");
    mVEAudioSink.clear();
    if (mVEAudioPlayer) {
        delete mVEAudioPlayer;
        mVEAudioPlayer = NULL;
    }

#ifndef ANDROID_DEFAULT_CODE
    if (mNativeWindowRenderer != NULL) {
#endif
    delete mNativeWindowRenderer;
    mNativeWindowRenderer = NULL;
#ifndef ANDROID_DEFAULT_CODE
    }
#endif

    // If image file playing, then free the buffer pointer
    if(mFrameStr.pBuffer != M4OSA_NULL) {
        free(mFrameStr.pBuffer);
        mFrameStr.pBuffer = M4OSA_NULL;
    }

#ifndef ANDROID_DEFAULT_CODE
    // Demon don't NE when startPreview failed or is not called
    if (mClipList != NULL && mNumberClipsToPreview > 0) {
#endif
    // Reset original begin cuttime of first previewed clip*/
    mClipList[mStartingClipIndex]->uiBeginCutTime = mFirstPreviewClipBeginTime;
    // Reset original end cuttime of last previewed clip*/
    mClipList[mNumberClipsToPreview-1]->uiEndCutTime = mLastPreviewClipEndTime;
#ifndef ANDROID_DEFAULT_CODE
    }
#endif

    mPlayerState = VePlayerIdle;
    mPrepareReqest = M4OSA_FALSE;

    mCurrentPlayedDuration = 0;
    mCurrentClipDuration = 0;
    mRenderingMode = M4xVSS_kBlackBorders;
    mOutputVideoWidth = 0;
    mOutputVideoHeight = 0;

    ALOGV("stopPreview() lastRenderedFrameTimeMs %ld", lastRenderedFrameTimeMs);
    return lastRenderedFrameTimeMs;
}

M4OSA_ERR VideoEditorPreviewController::clearSurface(
    const sp<Surface> &surface, VideoEditor_renderPreviewFrameStr* pFrameInfo) {

    M4OSA_ERR err = M4NO_ERROR;
    VideoEditor_renderPreviewFrameStr* pFrameStr = pFrameInfo;
    M4OSA_UInt32 outputBufferWidth =0, outputBufferHeight=0;
    M4VIFI_ImagePlane planeOut[3];
    ALOGV("Inside preview clear frame");

    Mutex::Autolock autoLock(mLock);

    // Delete previous renderer instance
    if(mTarget != NULL) {
        delete mTarget;
        mTarget = NULL;
    }

    outputBufferWidth = pFrameStr->uiFrameWidth;
    outputBufferHeight = pFrameStr->uiFrameHeight;

    // Initialize the renderer
    if(mTarget == NULL) {

        mTarget = PreviewRenderer::CreatePreviewRenderer(
            surface,
            outputBufferWidth, outputBufferHeight);

        if(mTarget == NULL) {
            ALOGE("renderPreviewFrame: cannot create PreviewRenderer");
            return M4ERR_ALLOC;
        }
    }

    // Out plane
    uint8_t* outBuffer;
    size_t outBufferStride = 0;

    ALOGV("doMediaRendering CALL getBuffer()");
    mTarget->getBufferYV12(&outBuffer, &outBufferStride);

    // Set the output YUV420 plane to be compatible with YV12 format
    //In YV12 format, sizes must be even
    M4OSA_UInt32 yv12PlaneWidth = ((outputBufferWidth +1)>>1)<<1;
    M4OSA_UInt32 yv12PlaneHeight = ((outputBufferHeight+1)>>1)<<1;

    prepareYV12ImagePlane(planeOut, yv12PlaneWidth, yv12PlaneHeight,
     (M4OSA_UInt32)outBufferStride, (M4VIFI_UInt8 *)outBuffer);

    /* Fill the surface with black frame */
    memset((void *)planeOut[0].pac_data,0x00,planeOut[0].u_width *
                            planeOut[0].u_height * 1.5);
    memset((void *)planeOut[1].pac_data,128,planeOut[1].u_width *
                            planeOut[1].u_height);
    memset((void *)planeOut[2].pac_data,128,planeOut[2].u_width *
                             planeOut[2].u_height);

    mTarget->renderYV12();
    return err;
}

M4OSA_ERR VideoEditorPreviewController::renderPreviewFrame(
            const sp<Surface> &surface,
            VideoEditor_renderPreviewFrameStr* pFrameInfo,
            VideoEditorCurretEditInfo *pCurrEditInfo) {

    M4OSA_ERR err = M4NO_ERROR;
    M4OSA_UInt32 i = 0, iIncrementedDuration = 0, tnTimeMs=0, framesize =0;
    VideoEditor_renderPreviewFrameStr* pFrameStr = pFrameInfo;
    M4VIFI_UInt8 *pixelArray = NULL;
    Mutex::Autolock autoLock(mLock);

    if (pCurrEditInfo != NULL) {
        pCurrEditInfo->overlaySettingsIndex = -1;
    }
    // Delete previous renderer instance
    if(mTarget != NULL) {
        delete mTarget;
        mTarget = NULL;
    }

    if(mOutputVideoWidth == 0) {
        mOutputVideoWidth = pFrameStr->uiFrameWidth;
    }

    if(mOutputVideoHeight == 0) {
        mOutputVideoHeight = pFrameStr->uiFrameHeight;
    }

    // Initialize the renderer
    if(mTarget == NULL) {
         mTarget = PreviewRenderer::CreatePreviewRenderer(
            surface,
            mOutputVideoWidth, mOutputVideoHeight);

        if(mTarget == NULL) {
            ALOGE("renderPreviewFrame: cannot create PreviewRenderer");
            return M4ERR_ALLOC;
        }
    }

    pixelArray = NULL;

    // Apply rotation if required
    if (pFrameStr->videoRotationDegree != 0) {
        err = applyVideoRotation((M4OSA_Void *)pFrameStr->pBuffer,
                  pFrameStr->uiFrameWidth, pFrameStr->uiFrameHeight,
                  pFrameStr->videoRotationDegree);
        if (M4NO_ERROR != err) {
            ALOGE("renderPreviewFrame: cannot rotate video, err 0x%x", (unsigned int)err);
            delete mTarget;
            mTarget = NULL;
            return err;
        } else {
           // Video rotation done.
           // Swap width and height if 90 or 270 degrees
           if (pFrameStr->videoRotationDegree != 180) {
               int32_t temp = pFrameStr->uiFrameWidth;
               pFrameStr->uiFrameWidth = pFrameStr->uiFrameHeight;
               pFrameStr->uiFrameHeight = temp;
           }
        }
    }
    // Postprocessing (apply video effect)
    if(pFrameStr->bApplyEffect == M4OSA_TRUE) {

        for(i=0;i<mNumberEffects;i++) {
            // First check if effect starttime matches the clip being previewed
            if((mEffectsSettings[i].uiStartTime < pFrameStr->clipBeginCutTime)
             ||(mEffectsSettings[i].uiStartTime >= pFrameStr->clipEndCutTime)) {
                // This effect doesn't belong to this clip, check next one
                continue;
            }
            if((mEffectsSettings[i].uiStartTime <= pFrameStr->timeMs) &&
#ifndef ANDROID_DEFAULT_CODE
            ((mEffectsSettings[i].uiStartTime+mEffectsSettings[i].uiDuration) >
#else
            ((mEffectsSettings[i].uiStartTime+mEffectsSettings[i].uiDuration) >=
#endif
             pFrameStr->timeMs) && (mEffectsSettings[i].uiDuration != 0)) {
                setVideoEffectType(mEffectsSettings[i].VideoEffectType, TRUE);
            }
            else {
                setVideoEffectType(mEffectsSettings[i].VideoEffectType, FALSE);
            }
        }

        //Provide the overlay Update indication when there is an overlay effect
        if (mCurrentVideoEffect & VIDEO_EFFECT_FRAMING) {
            M4OSA_UInt32 index;
            mCurrentVideoEffect &= ~VIDEO_EFFECT_FRAMING; //never apply framing here.

            // Find the effect in effectSettings array
            for (index = 0; index < mNumberEffects; index++) {
                if(mEffectsSettings[index].VideoEffectType ==
                    (M4VSS3GPP_VideoEffectType)M4xVSS_kVideoEffectType_Framing) {

                    if((mEffectsSettings[index].uiStartTime <= pFrameInfo->timeMs) &&
                        ((mEffectsSettings[index].uiStartTime+
#ifndef ANDROID_DEFAULT_CODE
                          // Demon, check effect as [a, b) range, not [a, b]
                        mEffectsSettings[index].uiDuration) > pFrameInfo->timeMs))
#else
                        mEffectsSettings[index].uiDuration) >= pFrameInfo->timeMs))
#endif
                    {
                        break;
                    }
                }
            }
            if ((index < mNumberEffects) && (pCurrEditInfo != NULL)) {
                pCurrEditInfo->overlaySettingsIndex = index;
                ALOGV("Framing index = %d", index);
            } else {
                ALOGV("No framing effects found");
            }
        }

        if(mCurrentVideoEffect != VIDEO_EFFECT_NONE) {
            err = applyVideoEffect((M4OSA_Void *)pFrameStr->pBuffer,
             OMX_COLOR_FormatYUV420Planar, pFrameStr->uiFrameWidth,
             pFrameStr->uiFrameHeight, pFrameStr->timeMs,
             (M4OSA_Void *)pixelArray);

            if(err != M4NO_ERROR) {
                ALOGE("renderPreviewFrame: applyVideoEffect error 0x%x", (unsigned int)err);
                delete mTarget;
                mTarget = NULL;
                free(pixelArray);
                pixelArray = NULL;
                return err;
           }
           mCurrentVideoEffect = VIDEO_EFFECT_NONE;
        }
        else {
            // Apply the rendering mode
            err = doImageRenderingMode((M4OSA_Void *)pFrameStr->pBuffer,
             OMX_COLOR_FormatYUV420Planar, pFrameStr->uiFrameWidth,
             pFrameStr->uiFrameHeight, (M4OSA_Void *)pixelArray);

            if(err != M4NO_ERROR) {
                ALOGE("renderPreviewFrame:doImageRenderingMode error 0x%x", (unsigned int)err);
                delete mTarget;
                mTarget = NULL;
                free(pixelArray);
                pixelArray = NULL;
                return err;
            }
        }
    }
    else {
        // Apply the rendering mode
        err = doImageRenderingMode((M4OSA_Void *)pFrameStr->pBuffer,
         OMX_COLOR_FormatYUV420Planar, pFrameStr->uiFrameWidth,
         pFrameStr->uiFrameHeight, (M4OSA_Void *)pixelArray);

        if(err != M4NO_ERROR) {
            ALOGE("renderPreviewFrame: doImageRenderingMode error 0x%x", (unsigned int)err);
            delete mTarget;
            mTarget = NULL;
            free(pixelArray);
            pixelArray = NULL;
            return err;
        }
    }

    mTarget->renderYV12();
    return err;
}

M4OSA_Void VideoEditorPreviewController::setJniCallback(void* cookie,
    jni_progress_callback_fct callbackFct) {
    //ALOGV("setJniCallback");
    mJniCookie = cookie;
    mJniCallback = callbackFct;
}

M4OSA_ERR VideoEditorPreviewController::preparePlayer(
    void* param, int playerInstance, int index) {

    M4OSA_ERR err = M4NO_ERROR;
    VideoEditorPreviewController *pController =
     (VideoEditorPreviewController *)param;

    ALOGV("preparePlayer: instance %d file %d", playerInstance, index);

    const char* fileName = (const char*) pController->mClipList[index]->pFile;
    pController->mVePlayer[playerInstance]->setDataSource(fileName, NULL);

    ALOGV("preparePlayer: setDataSource instance %s",
     (const char *)pController->mClipList[index]->pFile);

    pController->mVePlayer[playerInstance]->setVideoSurface(
     pController->mSurface);
    ALOGV("preparePlayer: setVideoSurface");

    pController->mVePlayer[playerInstance]->setMediaRenderingMode(
     pController->mClipList[index]->xVSS.MediaRendering,
     pController->mOutputVideoSize);
    ALOGV("preparePlayer: setMediaRenderingMode");

    if((M4OSA_UInt32)index == pController->mStartingClipIndex) {
        pController->mVePlayer[playerInstance]->setPlaybackBeginTime(
        pController->mFirstPreviewClipBeginTime);
    }
    else {
        pController->mVePlayer[playerInstance]->setPlaybackBeginTime(
        pController->mClipList[index]->uiBeginCutTime);
    }
    ALOGV("preparePlayer: setPlaybackBeginTime(%d)",
     pController->mClipList[index]->uiBeginCutTime);

    pController->mVePlayer[playerInstance]->setPlaybackEndTime(
     pController->mClipList[index]->uiEndCutTime);
    ALOGV("preparePlayer: setPlaybackEndTime(%d)",
     pController->mClipList[index]->uiEndCutTime);

    if(pController->mClipList[index]->FileType == M4VIDEOEDITING_kFileType_ARGB8888) {
        pController->mVePlayer[playerInstance]->setImageClipProperties(
                 pController->mClipList[index]->ClipProperties.uiVideoWidth,
                 pController->mClipList[index]->ClipProperties.uiVideoHeight);
        ALOGV("preparePlayer: setImageClipProperties");
    }

#ifndef ANDROID_DEFAULT_CODE
    err = pController->mVePlayer[playerInstance]->prepare();
    ALOGD("prepare return %d", err);
    if (err != OK) {
        return err;
    }
#else
    pController->mVePlayer[playerInstance]->prepare();
#endif
    ALOGV("preparePlayer: prepared");

    if(pController->mClipList[index]->uiBeginCutTime > 0) {
        pController->mVePlayer[playerInstance]->seekTo(
         pController->mClipList[index]->uiBeginCutTime);

        ALOGV("preparePlayer: seekTo(%d)",
         pController->mClipList[index]->uiBeginCutTime);
    }
    pController->mVePlayer[pController->mCurrentPlayer]->setAudioPlayer(pController->mVEAudioPlayer);

#ifndef ANDROID_DEFAULT_CODE
    err = pController->mVePlayer[playerInstance]->readFirstVideoFrame();
#else
    pController->mVePlayer[playerInstance]->readFirstVideoFrame();
#endif
    ALOGV("preparePlayer: readFirstVideoFrame of clip");

    return err;
}

M4OSA_ERR VideoEditorPreviewController::threadProc(M4OSA_Void* param) {
    M4OSA_ERR err = M4NO_ERROR;
    M4OSA_Int32 index = 0;
    VideoEditorPreviewController *pController =
     (VideoEditorPreviewController *)param;

    ALOGV("inside threadProc");
    if(pController->mPlayerState == VePlayerIdle) {
        (pController->mCurrentClipNumber)++;

        ALOGD("threadProc: playing file index %d total clips %d",
         pController->mCurrentClipNumber, pController->mNumberClipsToPreview);

        if((M4OSA_UInt32)pController->mCurrentClipNumber >=
         pController->mNumberClipsToPreview) {

            ALOGD("All clips previewed");

            pController->mCurrentPlayedDuration = 0;
            pController->mCurrentClipDuration = 0;
            pController->mCurrentPlayer = 0;

            if(pController->mPreviewLooping == M4OSA_TRUE) {
                pController->mCurrentClipNumber =
                 pController->mStartingClipIndex;

                ALOGD("Preview looping TRUE, restarting from clip index %d",
                 pController->mCurrentClipNumber);

                // Reset the story board timestamp inside the player
                for (int playerInst=0; playerInst<kTotalNumPlayerInstances;
                 playerInst++) {
                    pController->mVePlayer[playerInst]->resetJniCallbackTimeStamp();
                }
            }
            else {
                M4OSA_UInt32 endArgs = 0;
                if(pController->mJniCallback != NULL) {
                    pController->mJniCallback(
                     pController->mJniCookie, MSG_TYPE_PREVIEW_END, &endArgs);
                }
                pController->mPlayerState = VePlayerAutoStop;

                // Reset original begin cuttime of first previewed clip
                pController->mClipList[pController->mStartingClipIndex]->uiBeginCutTime =
                 pController->mFirstPreviewClipBeginTime;
                // Reset original end cuttime of last previewed clip
                pController->mClipList[pController->mNumberClipsToPreview-1]->uiEndCutTime =
                 pController->mLastPreviewClipEndTime;

                // Return a warning to M4OSA thread handler
                // so that thread is moved from executing state to open state
                return M4WAR_NO_MORE_STREAM;
            }
        }

        index=pController->mCurrentClipNumber;
        if((M4OSA_UInt32)pController->mCurrentClipNumber == pController->mStartingClipIndex) {
            pController->mCurrentPlayedDuration +=
             pController->mVideoStoryBoardTimeMsUptoFirstPreviewClip;

            pController->mCurrentClipDuration =
             pController->mClipList[pController->mCurrentClipNumber]->uiEndCutTime
              - pController->mFirstPreviewClipBeginTime;

#ifndef ANDROID_DEFAULT_CODE
            M4OSA_ERR err = preparePlayer((void*)pController, pController->mCurrentPlayer, index);
            if (err != M4NO_ERROR) {
                ALOGE("preparePlayer: %d", err);
            if(pController->mJniCallback != NULL) {
                pController->mJniCallback(pController->mJniCookie,
                MSG_TYPE_PLAYER_ERROR, &err);
            }
                return err;
            }
#else
            preparePlayer((void*)pController, pController->mCurrentPlayer, index);
#endif
        }
        else {
            pController->mCurrentPlayedDuration +=
             pController->mCurrentClipDuration;

            pController->mCurrentClipDuration =
             pController->mClipList[pController->mCurrentClipNumber]->uiEndCutTime -
             pController->mClipList[pController->mCurrentClipNumber]->uiBeginCutTime;
        }

        pController->mVePlayer[pController->mCurrentPlayer]->setStoryboardStartTime(
         pController->mCurrentPlayedDuration);
        ALOGV("threadProc: setStoryboardStartTime");

        // Set the next clip duration for Audio mix here
        if((M4OSA_UInt32)pController->mCurrentClipNumber != pController->mStartingClipIndex) {

            pController->mVePlayer[pController->mCurrentPlayer]->setAudioMixStoryBoardParam(
             pController->mCurrentPlayedDuration,
             pController->mClipList[index]->uiBeginCutTime,
             pController->mClipList[index]->ClipProperties.uiClipAudioVolumePercentage);

            ALOGV("threadProc: setAudioMixStoryBoardParam fromMS %d \
             ClipBeginTime %d", pController->mCurrentPlayedDuration +
             pController->mClipList[index]->uiBeginCutTime,
             pController->mClipList[index]->uiBeginCutTime,
             pController->mClipList[index]->ClipProperties.uiClipAudioVolumePercentage);
        }
        // Capture the active player being used
        pController->mActivePlayerIndex = pController->mCurrentPlayer;

        pController->mVePlayer[pController->mCurrentPlayer]->start();
        ALOGV("threadProc: started");

        pController->mPlayerState = VePlayerBusy;

    } else if(pController->mPlayerState == VePlayerAutoStop) {
        ALOGV("Preview completed..auto stop the player");
    } else if ((pController->mPlayerState == VePlayerBusy) && (pController->mPrepareReqest)) {
        // Prepare the player here
        pController->mPrepareReqest = M4OSA_FALSE;
#ifndef ANDROID_DEFAULT_CODE
        M4OSA_ERR err = preparePlayer((void*)pController, pController->mCurrentPlayer,
            pController->mCurrentClipNumber+1);
        if (err != M4NO_ERROR) {
            ALOGE("preparePlayer %d", err);
        if(pController->mJniCallback != NULL) {
            pController->mJniCallback(pController->mJniCookie,
            MSG_TYPE_PLAYER_ERROR, &err);
        }
            return err;
        }
#else
        preparePlayer((void*)pController, pController->mCurrentPlayer,
            pController->mCurrentClipNumber+1);
#endif
        if (pController->mSemThreadWait != NULL) {
            err = M4OSA_semaphoreWait(pController->mSemThreadWait,
                M4OSA_WAIT_FOREVER);
        }
    } else {
        if (!pController->bStopThreadInProgress) {
            ALOGV("threadProc: state busy...wait for sem");
            if (pController->mSemThreadWait != NULL) {
                err = M4OSA_semaphoreWait(pController->mSemThreadWait,
                 M4OSA_WAIT_FOREVER);
             }
        }
        ALOGV("threadProc: sem wait returned err = 0x%x", err);
    }

    //Always return M4NO_ERROR to ensure the thread keeps running
    return M4NO_ERROR;
}

void VideoEditorPreviewController::notify(
    void* cookie, int msg, int ext1, int ext2)
{
    VideoEditorPreviewController *pController =
     (VideoEditorPreviewController *)cookie;

    M4OSA_ERR err = M4NO_ERROR;
    uint32_t clipDuration = 0;
    switch (msg) {
        case MEDIA_NOP: // interface test message
            ALOGV("MEDIA_NOP");
            break;
        case MEDIA_PREPARED:
            ALOGV("MEDIA_PREPARED");
            break;
        case MEDIA_PLAYBACK_COMPLETE:
        {
            ALOGD("notify:MEDIA_PLAYBACK_COMPLETE, mCurrentClipNumber = %d",
                    pController->mCurrentClipNumber);
            pController->mPlayerState = VePlayerIdle;

            //send progress callback with last frame timestamp
            if((M4OSA_UInt32)pController->mCurrentClipNumber ==
             pController->mStartingClipIndex) {
                clipDuration =
                 pController->mClipList[pController->mCurrentClipNumber]->uiEndCutTime
                  - pController->mFirstPreviewClipBeginTime;
            }
            else {
                clipDuration =
                 pController->mClipList[pController->mCurrentClipNumber]->uiEndCutTime
                  - pController->mClipList[pController->mCurrentClipNumber]->uiBeginCutTime;
            }

            M4OSA_UInt32 playedDuration = clipDuration+pController->mCurrentPlayedDuration;
            pController->mJniCallback(
                 pController->mJniCookie, MSG_TYPE_PROGRESS_INDICATION,
                 &playedDuration);

            if ((pController->mOverlayState == OVERLAY_UPDATE) &&
                ((M4OSA_UInt32)pController->mCurrentClipNumber !=
                (pController->mNumberClipsToPreview-1))) {
                VideoEditorCurretEditInfo *pEditInfo =
                    (VideoEditorCurretEditInfo*)M4OSA_32bitAlignedMalloc(sizeof(VideoEditorCurretEditInfo),
                    M4VS, (M4OSA_Char*)"Current Edit info");
                pEditInfo->overlaySettingsIndex = ext2;
                pEditInfo->clipIndex = pController->mCurrentClipNumber;
                pController->mOverlayState == OVERLAY_CLEAR;
                if (pController->mJniCallback != NULL) {
                        pController->mJniCallback(pController->mJniCookie,
                            MSG_TYPE_OVERLAY_CLEAR, pEditInfo);
                }
                free(pEditInfo);
            }
            {
                Mutex::Autolock autoLock(pController->mLockSem);
                if (pController->mSemThreadWait != NULL) {
                    M4OSA_semaphorePost(pController->mSemThreadWait);
                    return;
                }
            }

            break;
        }
        case MEDIA_ERROR:
        {
            int err_val = ext1;
          // Always log errors.
          // ext1: Media framework error code.
          // ext2: Implementation dependant error code.
            ALOGE("MEDIA_ERROR; error (%d, %d)", ext1, ext2);
            if(pController->mJniCallback != NULL) {
                pController->mJniCallback(pController->mJniCookie,
                 MSG_TYPE_PLAYER_ERROR, &err_val);
            }
            break;
        }
        case MEDIA_INFO:
        {
            int info_val = ext2;
            // ext1: Media framework error code.
            // ext2: Implementation dependant error code.
            //ALOGW("MEDIA_INFO; info/warning (%d, %d)", ext1, ext2);
            if(pController->mJniCallback != NULL) {
                pController->mJniCallback(pController->mJniCookie,
                 MSG_TYPE_PROGRESS_INDICATION, &info_val);
            }
            break;
        }
        case MEDIA_SEEK_COMPLETE:
            ALOGV("MEDIA_SEEK_COMPLETE; Received seek complete");
            break;
        case MEDIA_BUFFERING_UPDATE:
            ALOGV("MEDIA_BUFFERING_UPDATE; buffering %d", ext1);
            break;
        case MEDIA_SET_VIDEO_SIZE:
            ALOGV("MEDIA_SET_VIDEO_SIZE; New video size %d x %d", ext1, ext2);
            break;
        case 0xAAAAAAAA:
            ALOGV("VIDEO PLAYBACK ALMOST over, prepare next player");
            // Select next player and prepare it
            // If there is a clip after this one
            if ((M4OSA_UInt32)(pController->mCurrentClipNumber+1) <
             pController->mNumberClipsToPreview) {
                pController->mPrepareReqest = M4OSA_TRUE;
                pController->mCurrentPlayer++;
                if (pController->mCurrentPlayer >= kTotalNumPlayerInstances) {
                    pController->mCurrentPlayer = 0;
                }
                // Prepare the first clip to be played
                {
                    Mutex::Autolock autoLock(pController->mLockSem);
                    if (pController->mSemThreadWait != NULL) {
                        M4OSA_semaphorePost(pController->mSemThreadWait);
                    }
                }
            }
            break;
        case 0xBBBBBBBB:
        {
            ALOGV("VIDEO PLAYBACK, Update Overlay");
            int overlayIndex = ext2;
            VideoEditorCurretEditInfo *pEditInfo =
                    (VideoEditorCurretEditInfo*)M4OSA_32bitAlignedMalloc(sizeof(VideoEditorCurretEditInfo),
                    M4VS, (M4OSA_Char*)"Current Edit info");
            //ext1 = 1; start the overlay display
            //     = 2; Clear the overlay.
            pEditInfo->overlaySettingsIndex = ext2;
            pEditInfo->clipIndex = pController->mCurrentClipNumber;
            ALOGV("pController->mCurrentClipNumber = %d",pController->mCurrentClipNumber);
            if (pController->mJniCallback != NULL) {
                if (ext1 == 1) {
                    pController->mOverlayState = OVERLAY_UPDATE;
                    pController->mJniCallback(pController->mJniCookie,
                        MSG_TYPE_OVERLAY_UPDATE, pEditInfo);
                } else {
                    pController->mOverlayState = OVERLAY_CLEAR;
                    pController->mJniCallback(pController->mJniCookie,
                        MSG_TYPE_OVERLAY_CLEAR, pEditInfo);
                }
            }
            free(pEditInfo);
            break;
        }
        default:
            ALOGV("unrecognized message: (%d, %d, %d)", msg, ext1, ext2);
            break;
    }
}

void VideoEditorPreviewController::setVideoEffectType(
    M4VSS3GPP_VideoEffectType type, M4OSA_Bool enable) {

    M4OSA_UInt32 effect = VIDEO_EFFECT_NONE;

    // map M4VSS3GPP_VideoEffectType to local enum
    switch(type) {
        case M4VSS3GPP_kVideoEffectType_FadeFromBlack:
            effect = VIDEO_EFFECT_FADEFROMBLACK;
            break;

        case M4VSS3GPP_kVideoEffectType_FadeToBlack:
            effect = VIDEO_EFFECT_FADETOBLACK;
            break;

        case M4xVSS_kVideoEffectType_BlackAndWhite:
            effect = VIDEO_EFFECT_BLACKANDWHITE;
            break;

        case M4xVSS_kVideoEffectType_Pink:
            effect = VIDEO_EFFECT_PINK;
            break;

        case M4xVSS_kVideoEffectType_Green:
            effect = VIDEO_EFFECT_GREEN;
            break;

        case M4xVSS_kVideoEffectType_Sepia:
            effect = VIDEO_EFFECT_SEPIA;
            break;

        case M4xVSS_kVideoEffectType_Negative:
            effect = VIDEO_EFFECT_NEGATIVE;
            break;

        case M4xVSS_kVideoEffectType_Framing:
            effect = VIDEO_EFFECT_FRAMING;
            break;

        case M4xVSS_kVideoEffectType_Fifties:
            effect = VIDEO_EFFECT_FIFTIES;
            break;

        case M4xVSS_kVideoEffectType_ColorRGB16:
            effect = VIDEO_EFFECT_COLOR_RGB16;
            break;

        case M4xVSS_kVideoEffectType_Gradient:
            effect = VIDEO_EFFECT_GRADIENT;
            break;

        default:
            effect = VIDEO_EFFECT_NONE;
            break;
    }

    if(enable == M4OSA_TRUE) {
        // If already set, then no need to set again
        if(!(mCurrentVideoEffect & effect))
            mCurrentVideoEffect |= effect;
            if(effect == VIDEO_EFFECT_FIFTIES) {
                mIsFiftiesEffectStarted = true;
            }
    }
    else  {
        // Reset only if already set
        if(mCurrentVideoEffect & effect)
            mCurrentVideoEffect &= ~effect;
    }

    return;
}


M4OSA_ERR VideoEditorPreviewController::applyVideoEffect(
    M4OSA_Void * dataPtr, M4OSA_UInt32 colorFormat, M4OSA_UInt32 videoWidth,
    M4OSA_UInt32 videoHeight, M4OSA_UInt32 timeMs, M4OSA_Void* outPtr) {

    M4OSA_ERR err = M4NO_ERROR;
    vePostProcessParams postProcessParams;

    postProcessParams.vidBuffer = (M4VIFI_UInt8*)dataPtr;
    postProcessParams.videoWidth = videoWidth;
    postProcessParams.videoHeight = videoHeight;
    postProcessParams.timeMs = timeMs;
    postProcessParams.timeOffset = 0; //Since timeMS already takes care of offset in this case
    postProcessParams.effectsSettings = mEffectsSettings;
    postProcessParams.numberEffects = mNumberEffects;
    postProcessParams.outVideoWidth = mOutputVideoWidth;
    postProcessParams.outVideoHeight = mOutputVideoHeight;
    postProcessParams.currentVideoEffect = mCurrentVideoEffect;
    postProcessParams.renderingMode = mRenderingMode;
    if(mIsFiftiesEffectStarted == M4OSA_TRUE) {
        postProcessParams.isFiftiesEffectStarted = M4OSA_TRUE;
        mIsFiftiesEffectStarted = M4OSA_FALSE;
    }
    else {
       postProcessParams.isFiftiesEffectStarted = M4OSA_FALSE;
    }
    //postProcessParams.renderer = mTarget;
    postProcessParams.overlayFrameRGBBuffer = NULL;
    postProcessParams.overlayFrameYUVBuffer = NULL;

    mTarget->getBufferYV12(&(postProcessParams.pOutBuffer), &(postProcessParams.outBufferStride));

#ifndef ANDROID_DEFAULT_CODE
    #ifdef MTK_YV12_16_STRIDE
    // Morris Yang 20120313 (force 16 alignment, due to 3D buffers for MTK video decoders are 32 alignment but NATIVE_WINDOW_API_CPU expect 16 alignment)
    postProcessParams.outBufferStride = ((mOutputVideoWidth + 0xF) & ~0xF);
    ALOGD ("@@ applyVideoEffect getBufferYV12 modified outBufferStride(%d)", postProcessParams.outBufferStride);
    #endif
#endif

    err = applyEffectsAndRenderingMode(&postProcessParams, videoWidth, videoHeight);
    return err;
}

status_t VideoEditorPreviewController::setPreviewFrameRenderingMode(
    M4xVSS_MediaRendering mode, M4VIDEOEDITING_VideoFrameSize outputVideoSize) {

    ALOGV("setMediaRenderingMode: outputVideoSize = %d", outputVideoSize);
    mRenderingMode = mode;

    status_t err = OK;
    /* get the video width and height by resolution */
    err = getVideoSizeByResolution(outputVideoSize,
              &mOutputVideoWidth, &mOutputVideoHeight);
#ifndef ANDROID_DEFAULT_CODE
    // prepare a larger resolution for thumbnail
    switch(outputVideoSize) {
        case M4VIDEOEDITING_kQCIF:
        case M4VIDEOEDITING_kCIF:
            mOutputVideoWidth = 352*2;
            mOutputVideoHeight = 288*2;
            break;
        case M4VIDEOEDITING_kSQCIF:
        case M4VIDEOEDITING_kQQVGA:
        case M4VIDEOEDITING_kQVGA:
            mOutputVideoWidth = 640;
            mOutputVideoHeight = 480;
            break;
    }
#endif

    return err;
}

M4OSA_ERR VideoEditorPreviewController::doImageRenderingMode(
    M4OSA_Void * dataPtr, M4OSA_UInt32 colorFormat, M4OSA_UInt32 videoWidth,
    M4OSA_UInt32 videoHeight, M4OSA_Void* outPtr) {

    M4OSA_ERR err = M4NO_ERROR;
    M4VIFI_ImagePlane planeIn[3], planeOut[3];
    M4VIFI_UInt8 *inBuffer = M4OSA_NULL;
    M4OSA_UInt32 outputBufferWidth =0, outputBufferHeight=0;

    //frameSize = (videoWidth*videoHeight*3) >> 1;
    inBuffer = (M4OSA_UInt8 *)dataPtr;

#ifndef ANDROID_DEFAULT_CODE
#if 0 // Morris Yang 20120503 dump decoder output
        int _w = ((videoWidth+0xF) & ~0xF);
        int _h = ((videoHeight+0xF) & ~0xF);
        char buf[255];
        sprintf (buf, "/sdcard/out_%d_%d.yuv", _w, _h);
        FILE *fp = fopen(buf, "ab");
        if(fp)
        {
            fwrite((void *)inBuffer, 1, _w*_h*1.5, fp);
            fclose(fp);
        }
#endif
#endif //ANDROID_DEFAULT_CODE

    // In plane
    prepareYUV420ImagePlane(planeIn, videoWidth,
      videoHeight, (M4VIFI_UInt8 *)inBuffer, videoWidth, videoHeight);

    outputBufferWidth = mOutputVideoWidth;
    outputBufferHeight = mOutputVideoHeight;

    // Out plane
    uint8_t* outBuffer;
    size_t outBufferStride = 0;

    ALOGV("doMediaRendering CALL getBuffer()");
    mTarget->getBufferYV12(&outBuffer, &outBufferStride);

#ifndef ANDROID_DEFAULT_CODE
    ALOGD ("@@ getBufferYV12 outBufferStride(%d)", outBufferStride);
    //ALOGD ("@@ mOutputVideoWidth(%d), videoWidth(%d)", mOutputVideoWidth, videoWidth);

    #ifdef MTK_YV12_16_STRIDE
    // Morris Yang 20120313 (force 16 alignment, due to 3D buffers for MTK video decoders are 32 alignment but NATIVE_WINDOW_API_CPU expect 16 alignment)
    outBufferStride = ((mOutputVideoWidth + 0xF) & ~0xF);
    ALOGD ("@@ doImageRenderingMode getBufferYV12 modified outBufferStride(%d)", outBufferStride);
    #endif
#endif //ANDROID_DEFAULT_CODE

    // Set the output YUV420 plane to be compatible with YV12 format
    //In YV12 format, sizes must be even
    M4OSA_UInt32 yv12PlaneWidth = ((mOutputVideoWidth +1)>>1)<<1;
    M4OSA_UInt32 yv12PlaneHeight = ((mOutputVideoHeight+1)>>1)<<1;

    prepareYV12ImagePlane(planeOut, yv12PlaneWidth, yv12PlaneHeight,
     (M4OSA_UInt32)outBufferStride, (M4VIFI_UInt8 *)outBuffer);

    err = applyRenderingMode(planeIn, planeOut, mRenderingMode);
    if(err != M4NO_ERROR) {
        ALOGE("doImageRenderingMode: applyRenderingMode returned err=0x%x", (unsigned int)err);
    }
    return err;
}

} //namespace android
