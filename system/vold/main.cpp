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
#include <sys/types.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <fs_mgr.h>
#include <unistd.h>
#ifndef MTK_EMULATOR_SUPPORT
  #include "libnvram.h"
#endif
#include "CFG_OMADMUSB_File.h"

#include "cutils/klog.h"
#include "cutils/log.h"
#include "cutils/properties.h"

#include "VolumeManager.h"
#include "CommandListener.h"
#include "NetlinkManager.h"
#include "DirectVolume.h"
#include "cryptfs.h"
#include "fat_on_nand.h"
#include <semaphore.h>
#define LOG_TAG "Vold"


extern int iFileOMADMUSBLID;

static int process_config(VolumeManager *vm);
static void coldboot(const char *path);
int coldboot_sent_uevent_count=0;
static bool coldboot_sent_uevent_count_only = true;
sem_t coldboot_sem;

#define FSTAB_PREFIX "/fstab."
struct fstab *fstab;


#define MAX_NVRAM_RESTRORE_READY_RETRY_NUM	(20)

int is_meta_boot(void)
{
  int fd;
  size_t s;
  char boot_mode;
  
  fd = open("/sys/class/BOOT/BOOT/boot/boot_mode", O_RDWR);
  if (fd < 0) 
  {
    printf("fail to open: %s\n", "/sys/class/BOOT/BOOT/boot/boot_mode");
    return 0;
  }
  
  s = read(fd, (void *)&boot_mode, sizeof(boot_mode));
  close(fd);
  
  if(s <= 0)
  {
    return 0;
  }
  
  if ((boot_mode != '1'))
  {
	  printf("Current boot_mode is Not meta mode\n");
    return 0;
  }

  printf("META Mode Booting.....\n");
  return 1;  
}

void create_link_in_meta(const char *ext_sd_path)
{
	SLOGD("%s(ext_sd_path = %s)", __func__, ext_sd_path);		

	if(is_meta_boot()) {
		SLOGD("This is meta mode boot.");		

		int ret = -1, round = 3 ;
		while(1) {
			ret = symlink(ext_sd_path, EXT_SDCARD_TOOL); 
			round-- ;

			if(ret) {
				if((round > 0) && (errno == EEXIST)) {
					SLOGE("The link already exists!");		
					SLOGE("Try again! round : %d", round);		
					unlink(EXT_SDCARD_TOOL) ;
				}
				else {
					SLOGE("Create symlink failed.");		
					break ;
				}
			}
			else {
				SLOGD("The link is created successfully!");		
				break ;
			}
		} 
	}
	else {
		SLOGD("This is not meta mode boot.");		
		unlink(EXT_SDCARD_TOOL) ;
	}

	return ;
}
int main() {

    VolumeManager *vm;
    CommandListener *cl;
    NetlinkManager *nm;

#ifndef MTK_EMULATOR_SUPPORT
//M{
    int fd = 0;
    char value[20];
    int count = 0;
    int Ret = 0;
    int rec_size;
    int rec_num;
    int file_lid = iFileOMADMUSBLID;
    OMADMUSB_CFG_Struct mStGet;

    int nvram_restore_ready_retry=0;
    char nvram_init_val[PROPERTY_VALUE_MAX] = {0};
    memset(&mStGet, 0, sizeof(OMADMUSB_CFG_Struct));
    SLOGD("Check whether nvram restore ready!\n");
    while(nvram_restore_ready_retry < MAX_NVRAM_RESTRORE_READY_RETRY_NUM){
        nvram_restore_ready_retry++;
        property_get("nvram_init", nvram_init_val, "");
        if(strcmp(nvram_init_val, "Ready") == 0){
            SLOGD("nvram restore ready!\n");
            break;
        }else{
            usleep(500*1000);
        }
    }

    if(nvram_restore_ready_retry >= MAX_NVRAM_RESTRORE_READY_RETRY_NUM){
        SLOGD("Get nvram restore ready fail!\n");
    }


    Ret = vm->NvramAccessForOMADM(&mStGet, true);
    SLOGD("OMADM NVRAM read  Ret=%d, IsEnable=%d, Usb=%d, Adb=%d, Rndis=%d", Ret, mStGet.iIsEnable, mStGet.iUsb, mStGet.iAdb, mStGet.iRndis);
    if (Ret < 0) {
        SLOGE("vold main read NVRAM failed!");
    } else {
        if (1 == mStGet.iIsEnable) {//usb enable
            //do nothing        
        } else {//usb disable

            if ((fd = open("/sys/devices/platform/mt_usb/cmode", O_WRONLY)) < 0) {
                SLOGE("Unable to open /sys/devices/platform/mt_usb/cmode");
                return -1;
            }

            count = snprintf(value, sizeof(value), "%d\n", mStGet.iIsEnable);
            Ret = write(fd, value, count);
            close(fd);
            if (Ret < 0) {
                SLOGE("Unable to write /sys/devices/platform/mt_usb/cmode");;
            }
        }
    }
//}M
#endif

    SLOGI("Vold 2.1 (the revenge) firing up");

    mkdir("/dev/block/vold", 0755);

    /* For when cryptfs checks and mounts an encrypted filesystem */
    klog_set_level(6);

    /* Create our singleton managers */
    if (!(vm = VolumeManager::Instance())) {
        SLOGE("Unable to create VolumeManager");
        exit(1);
    };

    if (!(nm = NetlinkManager::Instance())) {
        SLOGE("Unable to create NetlinkManager");
        exit(1);
    };


    cl = new CommandListener();
    vm->setBroadcaster((SocketListener *) cl);
    nm->setBroadcaster((SocketListener *) cl);

    if (vm->start()) {
        SLOGE("Unable to start VolumeManager (%s)", strerror(errno));
        exit(1);
    }

    if (process_config(vm)) {
        SLOGE("Error reading configuration (%s)... continuing anyways", strerror(errno));
    }

    if (nm->start()) {
        SLOGE("Unable to start NetlinkManager (%s)", strerror(errno));
        exit(1);
    }

    if (sem_init(&coldboot_sem, 0, 0) == -1) {
        SLOGE("Unable to sem_init (%s)", strerror(errno));
        exit(1);       
    }

    coldboot_sent_uevent_count_only = true;
    coldboot("/sys/block");
    coldboot_sent_uevent_count_only = false;
    coldboot("/sys/block");
//    coldboot("/sys/class/switch");

    SLOGI("Coldboot: try to wait for uevents, timeout 5s");
    struct timespec ts;
    ts.tv_sec=time(NULL)+5;
    ts.tv_nsec=0;   
    if (sem_timedwait(&coldboot_sem, &ts) == -1) {
       SLOGE("Coldboot: fail for sem_timedwait(%s)", strerror(errno));       
    }
    else {
       SLOGI("Coldboot: all uevent has handled");
    }

    /* give the default value to false for property, vold_swap_state */
    property_set("vold_swap_state", "0");

	// We should unlink the EXT_SDCARD_TOOL.
	// This can avoid the link exists in normal boot.
	// If exists, it will confuse the tool team.

#ifdef MTK_2SDCARD_SWAP
	vm->swap2Sdcard();
#else
    vm->setStoragePathProperty();
#endif

#ifdef MTK_SHARED_SDCARD
    vm->setSharedSdState(Volume::State_Mounted);
#endif
    if(is_meta_boot())
    {
       vm->mountallVolumes();
    }  
    /*
     * Now that we're up, we can respond to commands
     */
    if (cl->startListener()) {
        SLOGE("Unable to start CommandListener (%s)", strerror(errno));
        exit(1);
    }

    // Eventually we'll become the monitoring thread
    while(1) {
        sleep(1000);
    }

    SLOGI("Vold exiting");
    exit(0);
}

static void do_coldboot(DIR *d, int lvl)
{
    struct dirent *de;
    int dfd, fd;

    dfd = dirfd(d);

    fd = openat(dfd, "uevent", O_WRONLY);
    if(fd >= 0) {
        if (coldboot_sent_uevent_count_only) {
           coldboot_sent_uevent_count++;
        }
        else {
           write(fd, "add\n", 4);
        } 
        close(fd);
    }

    while((de = readdir(d))) {
        DIR *d2;

        if (de->d_name[0] == '.')
            continue;

        if (de->d_type != DT_DIR && lvl > 0)
            continue;

        fd = openat(dfd, de->d_name, O_RDONLY | O_DIRECTORY);
        if(fd < 0)
            continue;

        d2 = fdopendir(fd);
        if(d2 == 0)
            close(fd);
        else {
            do_coldboot(d2, lvl + 1);
            closedir(d2);
        }
    }
}

static void coldboot(const char *path)
{
    DIR *d = opendir(path);
    if(d) {
        do_coldboot(d, 0);
        closedir(d);
    }
}

static int process_config(VolumeManager *vm)
{
    char fstab_filename[PROPERTY_VALUE_MAX + sizeof(FSTAB_PREFIX)];
    char propbuf[PROPERTY_VALUE_MAX];
    int i;
    int ret = -1;
    int flags;
    FILE *fp;

#if defined(MTK_EMULATOR_SUPPORT)
    property_get("ro.hardware", propbuf, "");
    snprintf(fstab_filename, sizeof(fstab_filename), FSTAB_PREFIX"%s", propbuf);
#elif defined(MTK_EMMC_SUPPORT)
    sprintf(fstab_filename, "fstab");
#elif defined(MTK_FAT_ON_NAND)
	sprintf(fstab_filename, "fstab.fat.nand");		
#else
    sprintf(fstab_filename, "fstab.nand"); 	
#endif

    fstab = fs_mgr_read_fstab(fstab_filename);
    if (!fstab) {
        SLOGE("failed to open %s\n", fstab_filename);
        return -1;
    }

    /* Loop through entries looking for ones that vold manages */
    for (i = 0; i < fstab->num_entries; i++) {
        if (fs_mgr_is_voldmanaged(&fstab->recs[i])) {
            DirectVolume *dv = NULL;
            flags = 0;
 
            /* Set any flags that might be set for this volume */
            if (fs_mgr_is_nonremovable(&fstab->recs[i])) {
                flags |= VOL_NONREMOVABLE;
            }
            if (fs_mgr_is_encryptable(&fstab->recs[i])) {
                flags |= VOL_ENCRYPTABLE;
            }
            /* Only set this flag if there is not an emulated sd card */
            if (fs_mgr_is_noemulatedsd(&fstab->recs[i]) &&
                !strcmp(fstab->recs[i].fs_type, "vfat")) {
                flags |= VOL_PROVIDES_ASEC;
            }
            dv = new DirectVolume(vm, &(fstab->recs[i]), flags);

            SLOGI("fstab rec: '%s', '%s', %d, '%s', flag=0x%x",
                   fstab->recs[i].label, fstab->recs[i].mount_point, fstab->recs[i].partnum, fstab->recs[i].blk_device, flags);

            if (dv->addPath(fstab->recs[i].blk_device)) {
                SLOGE("Failed to add devpath %s to volume %s",
                      fstab->recs[i].blk_device, fstab->recs[i].label);
                goto out_fail;
            }

            #ifdef MTK_SHARED_SDCARD
            /* If it supports MTK_SHARED_SDCARD and the storage is the internal sd, set the state to State_Idle for swap2Sdcard() */
            if(dv->IsEmmcStorage())
                dv->setState(Volume::State_Idle);
            #endif

            vm->addVolume(dv);
        }
    }

    ret = 0;

out_fail:
    return ret;
}
