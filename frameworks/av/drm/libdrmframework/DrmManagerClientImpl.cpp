/*
 * Copyright (C) 2010 The Android Open Source Project
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
#define LOG_TAG "DrmManagerClientImpl(Native)"
#include <utils/Log.h>

#include <utils/String8.h>
#include <utils/Vector.h>
#include <binder/IServiceManager.h>

#include "DrmManagerClientImpl.h"

using namespace android;

#define INVALID_VALUE -1

Mutex DrmManagerClientImpl::sMutex;
sp<IDrmManagerService> DrmManagerClientImpl::sDrmManagerService;
sp<DrmManagerClientImpl::DeathNotifier> DrmManagerClientImpl::sDeathNotifier;
const String8 DrmManagerClientImpl::EMPTY_STRING("");

// M:
KeyedVector<int, DrmManagerClientImpl*> DrmManagerClientImpl::sClientVector;

DrmManagerClientImpl* DrmManagerClientImpl::create(
        int* pUniqueId, bool isNative) {
    *pUniqueId = getDrmManagerService()->addUniqueId(isNative);

    // M: modified for OMA DRM v1.0 implementation
    //return new DrmManagerClientImpl();
    // OMA DRM v1.0 implementation: save unique id for deathnotifier
    Mutex::Autolock lock(sMutex);
    DrmManagerClientImpl* impl = new DrmManagerClientImpl();
    sClientVector.add(*pUniqueId, impl);
    return impl;
}

void DrmManagerClientImpl::remove(int uniqueId) {
    getDrmManagerService()->removeUniqueId(uniqueId);

    // M: modified for OMA DRM v1.0 implementation: remove unique id saved for deathnotifier
    Mutex::Autolock lock(sMutex);
    sClientVector.removeItem(uniqueId);
}

const sp<IDrmManagerService>& DrmManagerClientImpl::getDrmManagerService() {
    Mutex::Autolock lock(sMutex);
    if (NULL == sDrmManagerService.get()) {
        sp<IServiceManager> sm = defaultServiceManager();
        sp<IBinder> binder;
        do {
            binder = sm->getService(String16("drm.drmManager"));
            if (binder != 0) {
                break;
            }
            ALOGW("DrmManagerService not published, waiting...");
            struct timespec reqt;
            reqt.tv_sec  = 0;
            reqt.tv_nsec = 500000000; //0.5 sec
            nanosleep(&reqt, NULL);
        } while (true);
        if (NULL == sDeathNotifier.get()) {
            sDeathNotifier = new DeathNotifier();
        }
        binder->linkToDeath(sDeathNotifier);
        sDrmManagerService = interface_cast<IDrmManagerService>(binder);
    }
    return sDrmManagerService;
}

void DrmManagerClientImpl::addClient(int uniqueId) {
    getDrmManagerService()->addClient(uniqueId);
}

void DrmManagerClientImpl::removeClient(int uniqueId) {
    getDrmManagerService()->removeClient(uniqueId);
}

status_t DrmManagerClientImpl::setOnInfoListener(
            int uniqueId,
            const sp<DrmManagerClient::OnInfoListener>& infoListener) {
    Mutex::Autolock _l(mLock);
    mOnInfoListener = infoListener;
    return getDrmManagerService()->setDrmServiceListener(uniqueId,
            (NULL != infoListener.get()) ? this : NULL);
}

DrmConstraints* DrmManagerClientImpl::getConstraints(
        int uniqueId, const String8* path, const int action) {
    DrmConstraints *drmConstraints = NULL;
    if ((NULL != path) && (EMPTY_STRING != *path)) {
        drmConstraints =
            getDrmManagerService()->getConstraints(uniqueId, path, action);
    }
    return drmConstraints;
}

DrmMetadata* DrmManagerClientImpl::getMetadata(int uniqueId, const String8* path) {
    DrmMetadata *drmMetadata = NULL;
    if ((NULL != path) && (EMPTY_STRING != *path)) {
        drmMetadata = getDrmManagerService()->getMetadata(uniqueId, path);
    }
    return drmMetadata;
}

bool DrmManagerClientImpl::canHandle(
        int uniqueId, const String8& path, const String8& mimeType) {
    bool retCode = false;
    if ((EMPTY_STRING != path) || (EMPTY_STRING != mimeType)) {
        retCode = getDrmManagerService()->canHandle(uniqueId, path, mimeType);
    }
    return retCode;
}

DrmInfoStatus* DrmManagerClientImpl::processDrmInfo(
        int uniqueId, const DrmInfo* drmInfo) {
    DrmInfoStatus *drmInfoStatus = NULL;
    if (NULL != drmInfo) {
        drmInfoStatus = getDrmManagerService()->processDrmInfo(uniqueId, drmInfo);
    }
    return drmInfoStatus;
}

DrmInfo* DrmManagerClientImpl::acquireDrmInfo(
        int uniqueId, const DrmInfoRequest* drmInfoRequest) {
    DrmInfo* drmInfo = NULL;
    if (NULL != drmInfoRequest) {
        drmInfo = getDrmManagerService()->acquireDrmInfo(uniqueId, drmInfoRequest);
    }
    return drmInfo;
}

status_t DrmManagerClientImpl::saveRights(int uniqueId, const DrmRights& drmRights,
            const String8& rightsPath, const String8& contentPath) {
    return getDrmManagerService()->saveRights(
                uniqueId, drmRights, rightsPath, contentPath);
}

String8 DrmManagerClientImpl::getOriginalMimeType(
        int uniqueId, const String8& path, int fd) {
    String8 mimeType = EMPTY_STRING;
    if (EMPTY_STRING != path) {
        mimeType = getDrmManagerService()->getOriginalMimeType(uniqueId, path, fd);
    }
    return mimeType;
}

int DrmManagerClientImpl::getDrmObjectType(
            int uniqueId, const String8& path, const String8& mimeType) {
    int drmOjectType = DrmObjectType::UNKNOWN;
    if ((EMPTY_STRING != path) || (EMPTY_STRING != mimeType)) {
         drmOjectType =
             getDrmManagerService()->getDrmObjectType(uniqueId, path, mimeType);
    }
    return drmOjectType;
}

int DrmManagerClientImpl::checkRightsStatus(
            int uniqueId, const String8& path, int action) {
    int rightsStatus = RightsStatus::RIGHTS_INVALID;
    if (EMPTY_STRING != path) {
        rightsStatus =
            getDrmManagerService()->checkRightsStatus(uniqueId, path, action);
    }
    return rightsStatus;
}

status_t DrmManagerClientImpl::consumeRights(
            int uniqueId, sp<DecryptHandle> &decryptHandle,
            int action, bool reserve) {
    status_t status = DRM_ERROR_UNKNOWN;
    if (NULL != decryptHandle.get()) {
        status = getDrmManagerService()->consumeRights(
                uniqueId, decryptHandle.get(), action, reserve);
    }
    return status;
}

status_t DrmManagerClientImpl::setPlaybackStatus(
            int uniqueId, sp<DecryptHandle> &decryptHandle,
            int playbackStatus, int64_t position) {
    status_t status = DRM_ERROR_UNKNOWN;
    if (NULL != decryptHandle.get()) {
        status = getDrmManagerService()->setPlaybackStatus(
                uniqueId, decryptHandle.get(), playbackStatus, position);
    }
    return status;
}

bool DrmManagerClientImpl::validateAction(
            int uniqueId, const String8& path,
            int action, const ActionDescription& description) {
    bool retCode = false;
    if (EMPTY_STRING != path) {
        retCode = getDrmManagerService()->validateAction(
                uniqueId, path, action, description);
    }
    return retCode;
}

status_t DrmManagerClientImpl::removeRights(int uniqueId, const String8& path) {
    status_t status = DRM_ERROR_UNKNOWN;
    if (EMPTY_STRING != path) {
        status = getDrmManagerService()->removeRights(uniqueId, path);
    }
    return status;
}

status_t DrmManagerClientImpl::removeAllRights(int uniqueId) {
    return getDrmManagerService()->removeAllRights(uniqueId);
}

int DrmManagerClientImpl::openConvertSession(
        int uniqueId, const String8& mimeType) {
    int retCode = INVALID_VALUE;
    if (EMPTY_STRING != mimeType) {
        retCode = getDrmManagerService()->openConvertSession(uniqueId, mimeType);
    }
    return retCode;
}

DrmConvertedStatus* DrmManagerClientImpl::convertData(
            int uniqueId, int convertId, const DrmBuffer* inputData) {
    DrmConvertedStatus* drmConvertedStatus = NULL;
    if (NULL != inputData) {
         drmConvertedStatus =
             getDrmManagerService()->convertData(uniqueId, convertId, inputData);
    }
    return drmConvertedStatus;
}

DrmConvertedStatus* DrmManagerClientImpl::closeConvertSession(
        int uniqueId, int convertId) {
    return getDrmManagerService()->closeConvertSession(uniqueId, convertId);
}

status_t DrmManagerClientImpl::getAllSupportInfo(
            int uniqueId, int* length, DrmSupportInfo** drmSupportInfoArray) {
    status_t status = DRM_ERROR_UNKNOWN;
    if ((NULL != drmSupportInfoArray) && (NULL != length)) {
        status = getDrmManagerService()->getAllSupportInfo(
                uniqueId, length, drmSupportInfoArray);
    }
    return status;
}

sp<DecryptHandle> DrmManagerClientImpl::openDecryptSession(
            int uniqueId, int fd, off64_t offset,
            off64_t length, const char* mime) {

    return getDrmManagerService()->openDecryptSession(
                uniqueId, fd, offset, length, mime);
}

sp<DecryptHandle> DrmManagerClientImpl::openDecryptSession(
        int uniqueId, const char* uri, const char* mime) {

    DecryptHandle* handle = NULL;
    if (NULL != uri) {
        handle = getDrmManagerService()->openDecryptSession(uniqueId, uri, mime);
    }
    return handle;
}

sp<DecryptHandle> DrmManagerClientImpl::openDecryptSession(
            int uniqueId, const DrmBuffer& buf, const String8& mimeType) {
    return getDrmManagerService()->openDecryptSession(uniqueId, buf, mimeType);
}

status_t DrmManagerClientImpl::closeDecryptSession(
        int uniqueId, sp<DecryptHandle> &decryptHandle) {
    status_t status = DRM_ERROR_UNKNOWN;
    if (NULL != decryptHandle.get()) {
        status = getDrmManagerService()->closeDecryptSession(
                uniqueId, decryptHandle.get());
    }
    return status;
}

status_t DrmManagerClientImpl::initializeDecryptUnit(
        int uniqueId, sp<DecryptHandle> &decryptHandle,
        int decryptUnitId, const DrmBuffer* headerInfo) {
    status_t status = DRM_ERROR_UNKNOWN;
    if ((NULL != decryptHandle.get()) && (NULL != headerInfo)) {
        status = getDrmManagerService()->initializeDecryptUnit(
                uniqueId, decryptHandle.get(), decryptUnitId, headerInfo);
    }
    return status;
}

status_t DrmManagerClientImpl::decrypt(
        int uniqueId, sp<DecryptHandle> &decryptHandle,
        int decryptUnitId, const DrmBuffer* encBuffer,
        DrmBuffer** decBuffer, DrmBuffer* IV) {
    status_t status = DRM_ERROR_UNKNOWN;
    if ((NULL != decryptHandle.get()) && (NULL != encBuffer)
        && (NULL != decBuffer) && (NULL != *decBuffer)) {
        status = getDrmManagerService()->decrypt(
                uniqueId, decryptHandle.get(), decryptUnitId,
                encBuffer, decBuffer, IV);
    }
    return status;
}

status_t DrmManagerClientImpl::finalizeDecryptUnit(
            int uniqueId, sp<DecryptHandle> &decryptHandle, int decryptUnitId) {
    status_t status = DRM_ERROR_UNKNOWN;
    if (NULL != decryptHandle.get()) {
        status = getDrmManagerService()->finalizeDecryptUnit(
                    uniqueId, decryptHandle.get(), decryptUnitId);
    }
    return status;
}

ssize_t DrmManagerClientImpl::pread(int uniqueId, sp<DecryptHandle> &decryptHandle,
            void* buffer, ssize_t numBytes, off64_t offset) {
    ssize_t retCode = INVALID_VALUE;
    if ((NULL != decryptHandle.get()) && (NULL != buffer) && (0 < numBytes)) {
        retCode = getDrmManagerService()->pread(
                uniqueId, decryptHandle.get(), buffer, numBytes, offset);
    }
    return retCode;
}

status_t DrmManagerClientImpl::notify(const DrmInfoEvent& event) {
    if (NULL != mOnInfoListener.get()) {
        Mutex::Autolock _l(mLock);
        sp<DrmManagerClient::OnInfoListener> listener = mOnInfoListener;
        listener->onInfo(event);
    }
    return DRM_NO_ERROR;
}

DrmManagerClientImpl::DeathNotifier::~DeathNotifier() {
    Mutex::Autolock lock(sMutex);
    if (NULL != sDrmManagerService.get()) {
        sDrmManagerService->asBinder()->unlinkToDeath(this);
    }
}

void DrmManagerClientImpl::DeathNotifier::binderDied(const wp<IBinder>& who) {
    // M: for OMA DRM v1.0 implementation
    { // limit the effect range of Autolock -sMutex
    Mutex::Autolock lock(sMutex);
    DrmManagerClientImpl::sDrmManagerService.clear();
    ALOGW("DrmManager server died!");
    }

    // M: for OMA DRM v1.0 implementation
    // re-add client to server
    // Note: the DrmManagerClientImpl::addClient() function will also locks the sMutex! (by calling getDrmManagerService())
    int size = sClientVector.size();
    for (int i = 0; i < size; i++) {
        sClientVector.valueAt(i)->addClient(sClientVector.keyAt(i));
    }
}

