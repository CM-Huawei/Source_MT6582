/*
 * Copyright (C) 2008 The Android Open Source Project
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

#ifndef _VOLUME_H
#define _VOLUME_H

#include <utils/List.h>
#include <fs_mgr.h>

#define MAX_SUP_PART 32

class NetlinkEvent;
class VolumeManager;

class Volume {
private:
    int mState;
    int mFlags;
    int mPreState;

public:
    static const int State_Init       = -1;
    static const int State_NoMedia    = 0;
    static const int State_Idle       = 1;
    static const int State_Pending    = 2;
    static const int State_Checking   = 3;
    static const int State_Mounted    = 4;
    static const int State_Unmounting = 5;
    static const int State_Formatting = 6;
    static const int State_Shared     = 7;
    static const int State_SharedMnt  = 8;

    static const char *MEDIA_DIR;
    static const char *FUSE_DIR;    

    static const char *SECDIR;
    static const char *SEC_STGDIR;
    static const char *SEC_STG_SECIMGDIR;
    static const char *SEC_ASECDIR_EXT;
    static const char *SEC_ASECDIR_INT;
    static const char *ASECDIR;
    static const char *LOOPDIR;
    static const char *BLKID_PATH;
    bool  mIsHwPullOut;

protected:
    char* mLabel;
    char* mUuid;
    char* mUserLabel;
    VolumeManager *mVm;
    bool mDebug;
    int mPartIdx;
    int mOrigPartIdx;
    bool mRetryMount;
    bool  mIsEmmcStorage;

    int mLoopDeviceIdx;

#ifdef MTK_MULTI_PARTITION_MOUNT_ONLY_SUPPORT
    char *mDescription;
    char *mOtgNodePath;
#endif

    /*
     * The major/minor tuple of the currently mounted filesystem.
     */
    dev_t mCurrentlyMountedKdev;

public:
    Volume(VolumeManager *vm, const fstab_rec* rec, int flags);
    virtual ~Volume();

    int mountVol();
    int unmountVol(bool force, bool revert);
    int formatVol(bool wipe);

    const char* getLabel() { return mLabel; }
    const char* getUuid() { return mUuid; }
    const char* getUserLabel() { return mUserLabel; }
#ifdef MTK_2SDCARD_SWAP
    bool isExternalSD(void) {
        return (!strcmp(getLabel(), "sdcard1")) ;
    }
    bool isPhoneStorage(void) {
        return (!strcmp(getLabel(), "sdcard0")) ;
    }
#endif
    int getState() { return mState; }
    void setState(int state);
    void setState(int state, bool bValue);
    int getFlags() { return mFlags; };

    /* Mountpoint of the raw volume */
    virtual const char *getMountpoint() = 0;
    virtual const char *getFuseMountpoint() = 0;

    virtual int handleBlockEvent(NetlinkEvent *evt);
    virtual dev_t getDiskDevice();
    virtual dev_t getShareDevice();
    virtual void handleVolumeShared();
    virtual void handleVolumeUnshared();

    void setDebug(bool enable);
    virtual int getVolInfo(struct volume_info *v) = 0;
    bool IsEmmcStorage() { return mIsEmmcStorage; }
    void setInternalStorage(bool flag) { mIsEmmcStorage = flag; }

    int doUnmount(const char *path, bool force);
    int unmountPath(const char *path, bool force);
#ifdef MTK_MULTI_PARTITION_MOUNT_ONLY_SUPPORT
    Volume(VolumeManager *vm, const char *label, const char *mount_point, const char *description, const char *node_path);
    char* getDescription() { return mDescription; }
    char* getOtgNodePath() { return mOtgNodePath; }
#endif
    virtual void updatePullOutState(NetlinkEvent *evt) = 0;
    int waitForServiceState(const char *name, const char *state);

protected:
    void setUuid(const char* uuid);
    void setUserLabel(const char* userLabel);

    virtual int getDeviceNodes(dev_t *devs, int max) = 0;
    virtual int getDeviceNumParts() = 0;
    virtual int updateDeviceInfo(char *new_path, int new_major, int new_minor) = 0;
    virtual void revertDeviceInfo(void) = 0;
    virtual int isDecrypted(void) = 0;

    int createDeviceNode(const char *path, int major, int minor);

private:
    int initializeMbr(const char *deviceNode);
    bool isMountpointMounted(const char *path);
    int mountAsecExternal(const char *target_path);
    int doMoveMount(const char *src, const char *dst, bool force);
    int extractMetadata(const char* devicePath);
};

typedef android::List<Volume *> VolumeCollection;

#endif
