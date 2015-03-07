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

#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/mount.h>
#include <semaphore.h>

#define LOG_TAG "Vold"

#include <cutils/log.h>

#include <sysutils/NetlinkEvent.h>
#include "NetlinkHandler.h"
#include "VolumeManager.h"
#include "DirectVolume.h"
#include "Process.h"
#include "ResponseCode.h"

extern int coldboot_sent_uevent_count;
extern sem_t coldboot_sem;

NetlinkHandler::NetlinkHandler(int listenerSocket) :
                NetlinkListener(listenerSocket) {
}

NetlinkHandler::~NetlinkHandler() {
}

int NetlinkHandler::start() {
    return this->startListener();
}

int NetlinkHandler::stop() {
    return this->stopListener();
}

//#define MOUNTPOINT_PREFIX   "/storage/sdcard0/usbotg-"
#define MOUNTPOINT_PREFIX   "/storage/usbotg/usbotg-"
#define NODEPATH_PREFIX "/dev/block/"

#ifdef MTK_MULTI_PARTITION_MOUNT_ONLY_SUPPORT

bool NetlinkHandler::getDescription(const char *path, char *description) {
    if (NULL == path || NULL == description) {
        return false;
    }

    char *str = strrchr(path, '/');
    if (NULL == str) {
        return false;
    }

    str++;              //pass the '/'
    char *dv_label = description;
    while ((*dv_label++ = *str++) != '\0');
    
    return true;
}

int NetlinkHandler::getPartIdxFromPath(const char *path) {
    if (NULL == path) {
        return -1;
    }

    char *str = strrchr(path, '/');
    if (NULL == str) {
        return false;
    }
    
    char partIdx[100] = {0};
    int i = 0;
    while(*str != '\0') {       
        if (*str <= '9' && *str >= '0') {
            partIdx[i] = *str;
            i++;
        }
        str++;
    }
    partIdx[i] = '\0';

    return atoi(partIdx);

}

bool NetlinkHandler::isPathUsbOtg(const char *path)
{
    if (NULL == path) {
        return false;
    }

    return (strstr(path, "mt_usb") != NULL);
}

int NetlinkHandler::UnmountPath(const char *path, bool force) {
    int retries = 3;

    SLOGD("usbotg: NetlinkHandler UnmountPath Unmounting {%s}, force = %d", path, force);

    while (retries--) {
        if (!umount(path) || errno == EINVAL || errno == ENOENT) {
            SLOGI("%s sucessfully unmounted", path);
            return 0;
        }

        int action = 0;

        if (force) {
            if (retries == 1) {
                action = 2; // SIGKILL
            } else if (retries == 2) {
                action = 1; // SIGHUP
            }
        }

        SLOGW("usbotg: NetlinkHandler UnmountPath Failed to unmount %s (%s, retries %d, action %d)", path, strerror(errno), retries, action);

        Process::killProcessesWithOpenFiles(path, action);
        if (retries > 0) {
            usleep(1000*1000);
        }
    }
    errno = EBUSY;
    SLOGE("usbotg: NetlinkHandler UnmountPath Giving up on unmount %s (%s)", path, strerror(errno));
    Process::FindProcessesWithOpenFiles(path);
    return -1;
}


void NetlinkHandler::handleUsbOtgDiskChanged(NetlinkEvent *evt) {
    char description[255] = {0}, mountPoint[255] = {0}, nodePath[255] = {0};
    int fd = 0, i = 0, partN = 0;
    
    if (NULL == evt) {
        SLOGE("usbotg: NetlinkHandler handleUsbOtgDiskChanged evt is NULL");
        return;
    }

    if (false == getDescription(evt->getPath(), description)) {
        SLOGE("usbotg: NetlinkHandler handleUsbOtgDiskChanged failed to getDescription");
        return;
    }

    partN = atoi(evt->findParam("NPARTS"));

    
    if (evt->findParam("DISK_EVENT_MEDIA_DISAPPEAR")) {
        SLOGD("usbotg: NetlinkHandler handleUsbOtgDiskChanged with DISK_EVENT_MEDIA_DISAPPEAR");
        for (i = 0; i < partN; i++) {
            sprintf(mountPoint, "%s%s%d", MOUNTPOINT_PREFIX, description, i+1);
            SLOGD("usbotg: NetlinkHandler handleUsbOtgDiskChanged partN = %d, mountPoint = %s", partN, mountPoint);

            //unmount and notify the framework
            if (0 == UnmountPath(mountPoint, true)) {
                char cmd[255] = {0}, msg[255] = {0};
                snprintf(msg, sizeof(msg), "Volume %s %s bad removal (%d:%d)", "usbotg", 
                    mountPoint, -1, -1);
                VolumeManager::Instance()->getBroadcaster()->sendBroadcast(ResponseCode::VolumeBadRemoval, msg, false);

                snprintf(cmd, sizeof(cmd), "/system/bin/sh -c \"rm -r %s\"", mountPoint);
                SLOGD("usbotg: NetlinkHandler handleUsbOtgDiskChanged cmd = %s", cmd);
                system(cmd);
            }
        }
    }

    if(evt->findParam("DISK_MEDIA_CHANGE")) {
        sprintf(nodePath, "%s%s", NODEPATH_PREFIX, description);
        SLOGD("usbotg: NetlinkHandler handleUsbOtgDiskChanged with DISK_MEDIA_CHANGE Check device node (%s)existence in (%s)!", nodePath, __func__);    

        if (-1 == (fd = open(nodePath, O_RDONLY, 0644)) ) {
            SLOGD("usbotg: NetlinkHandler handleUsbOtgDiskChanged Node Path (%s) open fail, do unmount!", nodePath);
            //UnmountPath(mountPoint, true);
        } else {
            SLOGD("usbotg: NetlinkHandler handleUsbOtgDiskChanged Node Path (%s) open success", nodePath);
            close(fd);
        }
    }
}

#endif
void NetlinkHandler::onEvent(NetlinkEvent *evt) {
    VolumeManager *vm = VolumeManager::Instance();
    const char *subsys = evt->getSubsystem();

    if (!subsys) {
        SLOGW("No subsystem found in netlink event");
        return;
    }

    if (!strcmp(subsys, "block")) {
        int action = evt->getAction();
#ifdef MTK_MULTI_PARTITION_MOUNT_ONLY_SUPPORT
    const char *devtype = evt->findParam("DEVTYPE");
    //int action = evt->getAction();
    const char *path = evt->getPath();
    char description[255] = {0};
    char dv_mountPoint[255] = {0}, dv_nodePath[255] = {0};
    int dv_partIdx = 0;
    const char *tmp = evt->findParam("NPARTS");
    int npart = 0;
    if (tmp) {
        npart = atoi(tmp);
    }

    SLOGD("usbotg: NetlinkHandler onEvent path = %s, action = %d, devtype = %s", path, action, devtype);

    // for usbotg hot-plug support, it has not success yet
    if ((isPathUsbOtg(path)== true) &&
        (action == NetlinkEvent::NlActionChange) &&
        (strcmp(devtype, "disk") == 0)) {
        SLOGD("usbotg: NetlinkHandler onEvent getinto handleUsbOtgDiskChanged");
        handleUsbOtgDiskChanged(evt);
    }

    if ((isPathUsbOtg(path) == true) &&
        (action == NetlinkEvent::NlActionAdd) &&
        (strcmp(devtype, "disk")|| (!strcmp(devtype, "disk") && npart == 0))) {       
        if (false == getDescription(path, description)) {
            SLOGE("usbotg: NetlinkHandler onEvent getDescription error");
            return;
        }
        sprintf(dv_mountPoint, "%s%s", MOUNTPOINT_PREFIX, description);
        sprintf(dv_nodePath, "%s%s", NODEPATH_PREFIX, description);
        dv_partIdx = getPartIdxFromPath(path);

        SLOGD("usbotg: NetlinkHandler onEvent mountPoint = %s, nodePath = %s partIdx = %d", 
                    dv_mountPoint, dv_nodePath, dv_partIdx);
        
        DirectVolume *dv = NULL;
        dv = new DirectVolume(vm, "usbotg", dv_mountPoint, description, dv_nodePath, dv_partIdx);
        dv->addPath(evt->getPath());
        vm->addVolume(dv);

        //while (!isSDcard0Mounted());
    }
#endif

        vm->updatePullOutState(evt);
        vm->setHotPlug(true);
        vm->handleBlockEvent(evt);
        vm->setHotPlug(false);

        if (action == NetlinkEvent::NlActionAdd) {
          if (coldboot_sent_uevent_count > 0 && (--coldboot_sent_uevent_count) <= 0) {
              SLOGI("Coldboot: sem_post() because all uevent has handled");
              sem_post(&coldboot_sem);
           }            
        }

    }
}
