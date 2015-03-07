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

#ifndef _DEVICEVOLUME_H
#define _DEVICEVOLUME_H

#include <utils/List.h>

#include "Volume.h"

/* Comment this define cause it's not used */
// #define MAX_PARTS 4

typedef android::List<char *> PathCollection;

class DirectVolume : public Volume {
public:
    static const int MAX_PARTITIONS = MAX_SUP_PART;
protected:
    const char* mMountpoint;
    const char* mFuseMountpoint;

    PathCollection *mPaths;
    int            mDiskMajor;
    int            mDiskMinor;
    int            mPartMinors[MAX_PARTITIONS];
    int            mOrigDiskMajor;
    int            mOrigDiskMinor;
    int            mOrigPartMinors[MAX_PARTITIONS];
    int            mDiskNumParts;
    unsigned int   mPendingPartMap;
    int            mIsDecrypted;

public:
    DirectVolume(VolumeManager *vm, const fstab_rec* rec, int flags);
#ifdef MTK_MULTI_PARTITION_MOUNT_ONLY_SUPPORT
    DirectVolume(VolumeManager *vm, const char *label, const char *mount_point, const char *description, const char *node_path, int partIdx);
#endif

    virtual ~DirectVolume();

    int addPath(const char *path);

    void setMountpoint(char* newMountPoint) {
        char *ptr= (char *)mMountpoint;
        mMountpoint = strdup(newMountPoint); 
        free(ptr);

        char storage_id[64];
        strcpy(storage_id, mMountpoint+strlen(Volume::MEDIA_DIR)+1);

        char mount[PATH_MAX];
        snprintf(mount, PATH_MAX, "%s/%s", Volume::FUSE_DIR, storage_id);
        ptr= (char *)mFuseMountpoint;
        mFuseMountpoint = strdup(mount);
        free(ptr);

        SLOGD("setMountpoint: newMountPoint='%s', mMountpoint='%s', mFuseMountpoint='%s'", newMountPoint, mMountpoint, mFuseMountpoint);
    }
    const char *getMountpoint() { return mMountpoint; }
    const char *getFuseMountpoint() { return mFuseMountpoint; }

    int handleBlockEvent(NetlinkEvent *evt);
    dev_t getDiskDevice();
    dev_t getShareDevice();
    void handleVolumeShared();
    void handleVolumeUnshared();
    int getVolInfo(struct volume_info *v);
    void updatePullOutState(NetlinkEvent *evt);

protected:
    int getDeviceNodes(dev_t *devs, int max);
    int getDeviceNumParts();
    int updateDeviceInfo(char *new_path, int new_major, int new_minor);
    virtual void revertDeviceInfo(void);
    int isDecrypted() { return mIsDecrypted; }

private:
    void handleDiskAdded(const char *devpath, NetlinkEvent *evt);
    void handleDiskRemoved(const char *devpath, NetlinkEvent *evt);
    void handleDiskChanged(const char *devpath, NetlinkEvent *evt);
    void handlePartitionAdded(const char *devpath, NetlinkEvent *evt);
    void handlePartitionRemoved(const char *devpath, NetlinkEvent *evt);
    void handlePartitionChanged(const char *devpath, NetlinkEvent *evt);

#ifdef MTK_MULTI_PARTITION_MOUNT_ONLY_SUPPORT
    void handleUsbOtgDiskAdded(const char *devpath, NetlinkEvent *evt);
    void handleUsbOtgDiskRemoved(const char *devpath, NetlinkEvent *evt);
    void handleUsbOtgDiskChanged(const char *devpath, NetlinkEvent *evt);
    void handleUsbOtgPartitionAdded(const char *devpath, NetlinkEvent *evt);
    void handleUsbOtgPartitionRemoved(const char *devpath, NetlinkEvent *evt);
    void handleUsbOtgPartitionChanged(const char *devpath, NetlinkEvent *evt);

    bool isPathUsbOtg(const char *path);
    //bool getMountPointFromPath(const char *path, char *mountPoint);
    int UnmountPath(const char *path, bool force);
    bool getDescription(const char *path, char *description);
#endif
    int doMountVfat(const char *deviceNode, const char *mountPoint);

};

typedef android::List<DirectVolume *> DirectVolumeCollection;

#endif
