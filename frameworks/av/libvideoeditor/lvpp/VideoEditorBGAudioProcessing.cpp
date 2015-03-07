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

//#define LOG_NDEBUG 0
#define LOG_TAG "VideoEditorBGAudioProcessing"
#include <utils/Log.h>
#include "VideoEditorBGAudioProcessing.h"

namespace android {

VideoEditorBGAudioProcessing::VideoEditorBGAudioProcessing() {
    ALOGV("Constructor");

    mAudVolArrIndex = 0;
    mDoDucking = 0;
    mDucking_enable = 0;
    mDucking_lowVolume = 0;
    mDucking_threshold = 0;
    mDuckingFactor = 0;

    mBTVolLevel = 0;
    mPTVolLevel = 0;

    mIsSSRCneeded = 0;
    mChannelConversion = 0;

    mBTFormat = MONO_16_BIT;

    mInSampleRate = 8000;
    mOutSampleRate = 16000;
    mPTChannelCount = 2;
    mBTChannelCount = 1;
}

M4OSA_Int32 VideoEditorBGAudioProcessing::mixAndDuck(
        void *primaryTrackBuffer,
        void *backgroundTrackBuffer,
        void *outBuffer) {

    ALOGV("mixAndDuck: track buffers (primary: 0x%x and background: 0x%x) "
            "and out buffer 0x%x",
            primaryTrackBuffer, backgroundTrackBuffer, outBuffer);

    M4AM_Buffer16* pPrimaryTrack   = (M4AM_Buffer16*)primaryTrackBuffer;
    M4AM_Buffer16* pBackgroundTrack = (M4AM_Buffer16*)backgroundTrackBuffer;
    M4AM_Buffer16* pMixedOutBuffer  = (M4AM_Buffer16*)outBuffer;

    // Output size if same as PT size
    pMixedOutBuffer->m_bufferSize = pPrimaryTrack->m_bufferSize;

    // Before mixing, we need to have only PT as out buffer
    memcpy((void *)pMixedOutBuffer->m_dataAddress,
        (void *)pPrimaryTrack->m_dataAddress, pMixedOutBuffer->m_bufferSize);

    // Initialize ducking variables
    // Initially contains the input primary track
    M4OSA_Int16 *pPTMdata2 = (M4OSA_Int16*)pMixedOutBuffer->m_dataAddress;

    // Contains BG track processed data(like channel conversion etc..
    M4OSA_Int16 *pBTMdata1 = (M4OSA_Int16*) pBackgroundTrack->m_dataAddress;

    // Since we need to give sample count and not buffer size
    M4OSA_UInt32 uiPCMsize = pMixedOutBuffer->m_bufferSize / 2 ;

    if ((mDucking_enable) && (mPTVolLevel != 0.0)) {
        M4OSA_Int32 peakDbValue = 0;
        M4OSA_Int32 previousDbValue = 0;
        M4OSA_Int16 *pPCM16Sample = (M4OSA_Int16*)pPrimaryTrack->m_dataAddress;
        const size_t n = pPrimaryTrack->m_bufferSize / sizeof(M4OSA_Int16);

        for (size_t loopIndex = 0; loopIndex < n; ++loopIndex) {
            if (pPCM16Sample[loopIndex] >= 0) {
                peakDbValue = previousDbValue > pPCM16Sample[loopIndex] ?
                        previousDbValue : pPCM16Sample[loopIndex];
                previousDbValue = peakDbValue;
            } else {
                peakDbValue = previousDbValue > -pPCM16Sample[loopIndex] ?
                        previousDbValue: -pPCM16Sample[loopIndex];
                previousDbValue = peakDbValue;
            }
        }

        mAudioVolumeArray[mAudVolArrIndex] = getDecibelSound(peakDbValue);

        // Check for threshold is done after kProcessingWindowSize cycles
        if (mAudVolArrIndex >= kProcessingWindowSize - 1) {
            mDoDucking = isThresholdBreached(
                    mAudioVolumeArray, mAudVolArrIndex, mDucking_threshold);

            mAudVolArrIndex = 0;
        } else {
            mAudVolArrIndex++;
        }

        //
        // Below logic controls the mixing weightage
        // for Background and Primary Tracks
        // for the duration of window under analysis,
        // to give fade-out for Background and fade-in for primary
        // Current fading factor is distributed in equal range over
        // the defined window size.
        // For a window size = 25
        // (500 ms (window under analysis) / 20 ms (sample duration))
        //

        if (mDoDucking) {
            if (mDuckingFactor > mDucking_lowVolume) {
                // FADE OUT BG Track
                // Increment ducking factor in total steps in factor
                // of low volume steps to reach low volume level
                mDuckingFactor -= mDucking_lowVolume;
            } else {
                mDuckingFactor = mDucking_lowVolume;
            }
        } else {
            if (mDuckingFactor < 1.0 ) {
                // FADE IN BG Track
                // Increment ducking factor in total steps of
                // low volume factor to reach orig.volume level
                mDuckingFactor += mDucking_lowVolume;
            } else {
                mDuckingFactor = 1.0;
            }
        }
    } // end if - mDucking_enable


    // Mixing logic
    ALOGV("Out of Ducking analysis uiPCMsize %d %f %f",
            mDoDucking, mDuckingFactor, mBTVolLevel);
    while (uiPCMsize-- > 0) {

        // Set vol factor for BT and PT
        *pBTMdata1 = (M4OSA_Int16)(*pBTMdata1*mBTVolLevel);
        *pPTMdata2 = (M4OSA_Int16)(*pPTMdata2*mPTVolLevel);

        // Mix the two samples
        if (mDoDucking) {

            // Duck the BG track to ducking factor value before mixing
            *pBTMdata1 = (M4OSA_Int16)((*pBTMdata1)*(mDuckingFactor));

            // mix as normal case
            *pBTMdata1 = (M4OSA_Int16)(*pBTMdata1 /2 + *pPTMdata2 /2);
        } else {

            *pBTMdata1 = (M4OSA_Int16)((*pBTMdata1)*(mDuckingFactor));
            *pBTMdata1 = (M4OSA_Int16)(*pBTMdata1 /2 + *pPTMdata2 /2);
        }

        M4OSA_Int32 temp;
        if (*pBTMdata1 < 0) {
            temp = -(*pBTMdata1) * 2; // bring to original Amplitude level

            if (temp > 32767) {
                *pBTMdata1 = -32766; // less then max allowed value
            } else {
                *pBTMdata1 = (M4OSA_Int16)(-temp);
            }
        } else {
            temp = (*pBTMdata1) * 2; // bring to original Amplitude level
            if ( temp > 32768) {
                *pBTMdata1 = 32767; // less than max allowed value
            } else {
                *pBTMdata1 = (M4OSA_Int16)temp;
            }
        }

        pBTMdata1++;
        pPTMdata2++;
    }

    memcpy((void *)pMixedOutBuffer->m_dataAddress,
        (void *)pBackgroundTrack->m_dataAddress,
        pBackgroundTrack->m_bufferSize);

    ALOGV("mixAndDuck: X");
    return M4NO_ERROR;
}

M4OSA_Int32 VideoEditorBGAudioProcessing::calculateOutResampleBufSize() {

    // This already takes care of channel count in mBTBuffer.m_bufferSize
    return (mOutSampleRate / mInSampleRate) * mBTBuffer.m_bufferSize;
}

void VideoEditorBGAudioProcessing::setMixParams(
        const AudioMixSettings& setting) {
    ALOGV("setMixParams");

    mDucking_enable       = setting.lvInDucking_enable;
    mDucking_lowVolume    = setting.lvInDucking_lowVolume;
    mDucking_threshold    = setting.lvInDucking_threshold;
    mPTVolLevel           = setting.lvPTVolLevel;
    mBTVolLevel           = setting.lvBTVolLevel ;
    mBTChannelCount       = setting.lvBTChannelCount;
    mPTChannelCount       = setting.lvPTChannelCount;
    mBTFormat             = setting.lvBTFormat;
    mInSampleRate         = setting.lvInSampleRate;
    mOutSampleRate        = setting.lvOutSampleRate;

    // Reset the following params to default values
    mAudVolArrIndex       = 0;
    mDoDucking            = 0;
    mDuckingFactor        = 1.0;

    ALOGV("ducking enable 0x%x lowVolume %f threshold %d "
            "fPTVolLevel %f BTVolLevel %f",
            mDucking_enable, mDucking_lowVolume, mDucking_threshold,
            mPTVolLevel, mPTVolLevel);

    // Decides if SSRC support is needed for this mixing
    mIsSSRCneeded = (setting.lvInSampleRate != setting.lvOutSampleRate);
    if (setting.lvBTChannelCount != setting.lvPTChannelCount){
        if (setting.lvBTChannelCount == 2){
            mChannelConversion = 1; // convert to MONO
        } else {
            mChannelConversion = 2; // Convert to STEREO
        }
    } else {
        mChannelConversion = 0;
    }
}

// Fast way to compute 10 * log(value)
M4OSA_Int32 VideoEditorBGAudioProcessing::getDecibelSound(M4OSA_UInt32 value) {
    ALOGV("getDecibelSound: %ld", value);

    if (value <= 0 || value > 0x8000) {
        return 0;
    } else if (value > 0x4000) { // 32768
        return 90;
    } else if (value > 0x2000) { // 16384
        return 84;
    } else if (value > 0x1000) { // 8192
        return 78;
    } else if (value > 0x0800) { // 4028
        return 72;
    } else if (value > 0x0400) { // 2048
        return 66;
    } else if (value > 0x0200) { // 1024
        return 60;
    } else if (value > 0x0100) { // 512
        return 54;
    } else if (value > 0x0080) { // 256
        return 48;
    } else if (value > 0x0040) { // 128
        return 42;
    } else if (value > 0x0020) { // 64
        return 36;
    } else if (value > 0x0010) { // 32
        return 30;
    } else if (value > 0x0008) { // 16
        return 24;
    } else if (value > 0x0007) { // 8
        return 24;
    } else if (value > 0x0003) { // 4
        return 18;
    } else if (value > 0x0001) { // 2
        return 12;
    } else  { // 1
        return 6;
    }
}

M4OSA_Bool VideoEditorBGAudioProcessing::isThresholdBreached(
        M4OSA_Int32* averageValue,
        M4OSA_Int32 storeCount,
        M4OSA_Int32 thresholdValue) {

    ALOGV("isThresholdBreached");

    int totalValue = 0;
    for (int i = 0; i < storeCount; ++i) {
        totalValue += averageValue[i];
    }
    return (totalValue / storeCount > thresholdValue);
}

}//namespace android
