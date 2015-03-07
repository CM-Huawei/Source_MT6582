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

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <linux/kd.h>
#include <errno.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <linux/if.h>
#include <arpa/inet.h>
#include <stdlib.h>
#include <sys/mount.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <linux/loop.h>
#include <cutils/partition_utils.h>
#include <cutils/android_reboot.h>
#include <sys/system_properties.h>
#include <fs_mgr.h>
#include <stdarg.h>

#include <selinux/selinux.h>
#include <selinux/label.h>

#include "init.h"
#include "keywords.h"
#include "property_service.h"
#include "devices.h"
#include "init_parser.h"
#include "util.h"
#include "log.h"

#include <private/android_filesystem_config.h>
#ifdef INIT_ENG_BUILD
#define printf(x...) NOTICE(x)
#endif

#define ENV_MAGIC	 'e'
#define ENV_READ		_IOW(ENV_MAGIC, 1, int)
#define ENV_WRITE 		_IOW(ENV_MAGIC, 2, int)

struct env_ioctl
{
	char *name;
	int name_len;
	char *value;
	int value_len;	
};

void add_environment(const char *name, const char *value);

extern int init_module(void *, unsigned long, const char *);

static int write_file(const char *path, const char *value)
{
    int fd, ret, len;

    fd = open(path, O_WRONLY|O_CREAT|O_NOFOLLOW, 0600);

    if (fd < 0)
        return -errno;

    len = strlen(value);

    do {
        ret = write(fd, value, len);
    } while (ret < 0 && errno == EINTR);

    close(fd);
    if (ret < 0) {
        return -errno;
    } else {
        return 0;
    }
}

static int _open(const char *path)
{
    int fd;

    fd = open(path, O_RDONLY | O_NOFOLLOW);
    if (fd < 0)
        fd = open(path, O_WRONLY | O_NOFOLLOW);

    return fd;
}
#ifdef MTK_INIT
static int _chown(const char *path, unsigned int uid, unsigned int gid)
{
    int ret;

    struct stat p_statbuf;

    ret = lstat(path, &p_statbuf);
    if (ret < 0) {
        return -1;
    }

    if (S_ISLNK(p_statbuf.st_mode) == 1) {
        errno = EINVAL;
        return -1;
    }

    ret = chown(path, uid, gid);

    return ret;
}

static int _chmod(const char *path, mode_t mode)
{
    int ret;

    struct stat p_statbuf;

    ret = lstat(path, &p_statbuf);
    if( ret < 0) {
        return -1;
    }

    if (S_ISLNK(p_statbuf.st_mode) == 1) {
        errno = EINVAL;
        return -1;
    }

    ret = chmod(path, mode);

    return ret;
}
#else
static int _chown(const char *path, unsigned int uid, unsigned int gid)
{
    int fd;
    int ret;

    fd = _open(path);
    if (fd < 0) {
        return -1;
    }

    ret = fchown(fd, uid, gid);
    if (ret < 0) {
        int errno_copy = errno;
        close(fd);
        errno = errno_copy;
        return -1;
    }

    close(fd);

    return 0;
}

static int _chmod(const char *path, mode_t mode)
{
    int fd;
    int ret;

    fd = _open(path);
    if (fd < 0) {
        return -1;
    }

    ret = fchmod(fd, mode);
    if (ret < 0) {
        int errno_copy = errno;
        close(fd);
        errno = errno_copy;
        return -1;
    }

    close(fd);

    return 0;
}
#endif
static int insmod(const char *filename, char *options)
{
    void *module;
    unsigned size;
    int ret;

    module = read_file(filename, &size);
    if (!module)
        return -1;

    ret = init_module(module, size, options);

    free(module);

    return ret;
}

static int setkey(struct kbentry *kbe)
{
    int fd, ret;

    fd = open("/dev/tty0", O_RDWR | O_SYNC);
    if (fd < 0)
        return -1;

    ret = ioctl(fd, KDSKBENT, kbe);

    close(fd);
    return ret;
}

static int __ifupdown(const char *interface, int up)
{
    struct ifreq ifr;
    int s, ret;

    strlcpy(ifr.ifr_name, interface, IFNAMSIZ);

    s = socket(AF_INET, SOCK_DGRAM, 0);
    if (s < 0)
        return -1;

    ret = ioctl(s, SIOCGIFFLAGS, &ifr);
    if (ret < 0) {
        goto done;
    }

    if (up)
        ifr.ifr_flags |= IFF_UP;
    else
        ifr.ifr_flags &= ~IFF_UP;

    ret = ioctl(s, SIOCSIFFLAGS, &ifr);
    
done:
    close(s);
    return ret;
}

static void service_start_if_not_disabled(struct service *svc)
{
    if (!(svc->flags & SVC_DISABLED)) {
        service_start(svc, NULL);
    }
}

int do_chdir(int nargs, char **args)
{
    chdir(args[1]);
    return 0;
}

int do_chroot(int nargs, char **args)
{
    chroot(args[1]);
    return 0;
}

int do_class_start(int nargs, char **args)
{
        /* Starting a class does not start services
         * which are explicitly disabled.  They must
         * be started individually.
         */
    service_for_each_class(args[1], service_start_if_not_disabled);
    return 0;
}

int do_class_stop(int nargs, char **args)
{
    service_for_each_class(args[1], service_stop);
    return 0;
}

int do_class_reset(int nargs, char **args)
{
    service_for_each_class(args[1], service_reset);
    return 0;
}

int do_domainname(int nargs, char **args)
{
    return write_file("/proc/sys/kernel/domainname", args[1]);
}

//#define FSCK_TUNE
#ifdef MTK_FSCK_TUNE
#define MT_NORMAL_BOOT 0
#define DATA_PATH "/emmc@usrdata" 
#define NORMAL_UMOUNT_FLAG_LEN 4
#define NORMAL_UMOUNT_BLOCK_LEN 1024
#define NORMAL_UMOUNT_FLAG_POSTION NORMAL_UMOUNT_BLOCK_LEN - NORMAL_UMOUNT_FLAG_LEN
unsigned char NORMAL_UMOUNT_FLAG_ARRAY[5] = {0x11,0x22,0x33,0x44};
#endif
static int get_boot_mode(void)
{
  int fd;
  size_t s;
  char boot_mode;
  fd = open("/sys/class/BOOT/BOOT/boot/boot_mode", O_RDWR);
  if (fd < 0)
  {
    ERROR("fail to open: %s\n", "/sys/class/BOOT/BOOT/boot/boot_mode");
    return 0;
  }
  s = read(fd, (void *)&boot_mode, sizeof(boot_mode));
  close(fd);
  if(s <= 0)
  {
	ERROR("could not read boot mode sys file\n");
    return 0;
  }
  return atoi(&boot_mode);
}
#ifdef MTK_FSCK_TUNE
static char shutdown_flag[]="shutdown_flag";
static char shutdown_value_normal[]="normal";
static char shutdown_value_exception[]="exception";
#define FLAG_SHUTDOWN 0x1
#define FLAG_POWER_CUT 0x0
#define MAX_SHUTDOWN_VALUE_LEN (sizeof(shutdown_value_exception)>sizeof(shutdown_value_normal)? sizeof(shutdown_value_exception):sizeof(shutdown_value_normal) )
#define SHUTDOWN_FLAG_LEN (sizeof(shutdown_flag))
static int check_shutdown_flag(void)
{
    struct env_ioctl shutdown;
    int env_fd;
    int ret;

    if((env_fd = open("/proc/lk_env", O_RDWR)) < 0) {
        ERROR("Open env for format check fail.\n");
        goto FAIL_RUTURN;
    }
    if(!(shutdown.name = calloc(SHUTDOWN_FLAG_LEN, sizeof(char)))) {
        ERROR("Allocate Memory for env name fail.\n");
        goto FREE_FD;
    }
    if(!(shutdown.value = calloc(MAX_SHUTDOWN_VALUE_LEN, sizeof(char)))) {
        ERROR("Allocate Memory for env value fail.\n");
        goto FREE_ALLOCATE_NAME;
    }
    shutdown.name_len = SHUTDOWN_FLAG_LEN;
    shutdown.value_len = MAX_SHUTDOWN_VALUE_LEN;
    memcpy(shutdown.name, shutdown_flag, SHUTDOWN_FLAG_LEN);
    if(ret = ioctl(env_fd, ENV_READ, &shutdown)) {
        ERROR("Get env for shutdown check fail ret = %d, errno = %d.\n", ret, errno);
        goto FREE_ALLOCATE_VALUE;
    }
    if(shutdown.value) {
        ERROR("shutdown_flag stats = %s\n", shutdown.value);
    } else {
        ERROR("shutdown_flag stats is not be set.\n");
		goto FREE_ALLOCATE_VALUE; 		
    }
    if(strncmp(shutdown.value, shutdown_value_normal, strlen(shutdown_value_normal))) {
        ERROR("The shutdown flag show that this device is not shutdown properly.\n");
        goto FREE_ALLOCATE_VALUE; 
    }else {
        ERROR("The shutdown flag show that this device is shutdown properly.\n");
    }

    free(shutdown.name);
    free(shutdown.value);
    close(env_fd);
    return FLAG_SHUTDOWN; 
FREE_ALLOCATE_VALUE:
    free(shutdown.value);
FREE_ALLOCATE_NAME:
    free(shutdown.name);
FREE_FD:
    close(env_fd);
FAIL_RUTURN:
    return FLAG_POWER_CUT;
}

static int clear_shutdown_flag(void)
{
    struct env_ioctl shutdown;
    int env_fd;
    int ret;

    if((env_fd = open("/proc/lk_env", O_RDWR)) < 0) {
        ERROR("Open env for format check fail.\n");
        goto FAIL_RUTURN;
    }
    if(!(shutdown.name = calloc(SHUTDOWN_FLAG_LEN, sizeof(char)))) {
        ERROR("Allocate Memory for env name fail.\n");
        goto FREE_FD;
    }
    if(!(shutdown.value = calloc(MAX_SHUTDOWN_VALUE_LEN, sizeof(char)))) {
        ERROR("Allocate Memory for env value fail.\n");
        goto FREE_ALLOCATE_NAME;
    }
    shutdown.name_len = SHUTDOWN_FLAG_LEN;
    shutdown.value_len = MAX_SHUTDOWN_VALUE_LEN;
    memcpy(shutdown.name, shutdown_flag, sizeof(shutdown_flag));
    memcpy(shutdown.value, shutdown_value_exception, sizeof(shutdown_value_exception));
    if(ret = ioctl(env_fd, ENV_WRITE, &shutdown)) {
        ERROR("write env for shutdown flag fail.ret = %d, errno = %d\n", ret, errno);
        goto FREE_ALLOCATE_VALUE;
    }

    ERROR("Successfully clear shut down flag.\n");
    free(shutdown.name);
    free(shutdown.value);
    close(env_fd);
    return 0; 
FREE_ALLOCATE_VALUE:
    free(shutdown.value);
FREE_ALLOCATE_NAME:
    free(shutdown.name);
FREE_FD:
    close(env_fd);
FAIL_RUTURN:
    return 1;
}

int fsck_handle(int nargs,char **args)
{
	int mt_boot_mode = 0;
	int dev = -1;
	int len = 0;
	int normal_umount_matched = 0;
	char *p;
	char *s;
	int i;
	mt_boot_mode = get_boot_mode();
	printf("fsck_handle start!\n");
	if (!strncmp(args[1], "/sbin/e2fsck", 12) && strchr(args[2],'f') && !strncmp(args[3], DATA_PATH, strlen(DATA_PATH)))
	{
	 	normal_umount_matched = check_shutdown_flag();
		printf("fsck_handle matched=%d, boot_mode=%d\n",normal_umount_matched,mt_boot_mode);				
		if (normal_umount_matched == FLAG_SHUTDOWN)
		{
			s = args[2];
			for(p=s;*s;s++)
				if (*s!='f')
					*p++=*s;
			*p =0;
			if (mt_boot_mode == MT_NORMAL_BOOT)
			{
				if(clear_shutdown_flag()){
					ERROR("clear shut down flag fail \n");
					return 1;
				}
		 	}
		}
			
	}
	return 0;  
}
#endif
int do_exec(int nargs, char **args)
{
    int i;
    int status;
    int child_return_val;
    pid_t pid;
    pid_t pid_super;
    pid_t pid_manual;

    char *arg_p[64] = {0};
    memcpy(arg_p, args, sizeof(char*) * nargs);
    for(i = 0; i < nargs; i++)
    {
        printf("argv[%d]= %s\n", i, arg_p[i]);
    }
    arg_p[nargs] = NULL;
#ifdef MTK_FSCK_TUNE
	fsck_handle(nargs, args);
#endif
    pid = fork();
    switch (pid){
        case -1:
            printf("fork system call failed\n");
            break;
        case 0:  /* child */
            execv(arg_p[1], (arg_p + 1));
            printf("cannot run %s\n", args[1]);
            _exit(127);
            break;
        default: /* parent */
            /* wait child process end(for e2fsck complete) */
            while (pid != wait(&status));

            if (WIFEXITED(status)){
                child_return_val = WEXITSTATUS(status);
                if (strncmp(args[1], "/sbin/e2fsck", 12)){
                    if (child_return_val != 0){
                        printf("%s run failed\n", args[1]);
                    }
                    //printf("child_return_val = %s\n", child_return_val);
                } else {
                    switch (child_return_val){
                        case 127:
                            printf("execl error\n");
                            break;
                        case 0:
                        case 1:
                            /* e2fsck run ok */
                            printf("e2fsck run ok!\n");
                            break;
                        case 2:
                            /* e2fsck run ok, but need reboot the system */
                            printf("e2fsck need reboot!\n");
                            system("reboot");
                            break;
                        case 4:
                            printf("e2fsck: all say yes!\n");
                            pid_manual = fork();
                            switch (pid_manual){
                                case -1:
                                    printf("manual check:fork system call failed\n");
                                    break;
                                case 0: /* child */
                                    execl(args[1], args[1], "-y", args[3], NULL);
                                    printf("manual check:cannot run %s\n", args[1]);
                                    _exit(127);
                                    break;
                                default: /* parent */
                                    /* wait child process end(for e2fsck complete) */
                                    while (pid_manual != wait(&status));

                                    if (WIFEXITED(status)){
                                        child_return_val = WEXITSTATUS(status);
                                        if (child_return_val != 0 && child_return_val != 1){
                                            printf("superblock check:canot correct the bad fs\n");
                                        }
                                    }
                                    break;
                            } 
                            break;
                        case 8:
                        case 16:
                        case 32:
                        case 128:
                            printf("begin superblock check:child_return_val = %d\n", child_return_val);
                        default:
                            /* e2fsck need furture work */
                            pid_super = fork();
                            switch (pid_super){
                                case -1:
                                    printf("superblock check:fork system call failed\n");
                                    break;
                                case 0:  /* child */
                                    /* running /system/bin/e2fsck -p -b 16384 /dev/block/~ */
                                    execl(args[1], args[1], args[2], "-b 32768", args[3], NULL);
                                    printf("superblock check:cannot run %s\n", args[1]);
                                    _exit(127);
                                    break;
                                default: /* parent */
                                    /* wait child process end(for e2fsck complete) */
                                    while (pid_super != wait(&status));

                                    if (WIFEXITED(status)){
                                        child_return_val = WEXITSTATUS(status);
                                        if (child_return_val != 0 && child_return_val != 1){
                                            printf("superblock check:canot correct the bad fs\n");
                                        }
                                    }
                                    break;
                            }
                            break;
                    }
                }
            }
            break;
    }

    return 0;

}

int do_export(int nargs, char **args)
{
    add_environment(args[1], args[2]);
    return 0;
}

int do_hostname(int nargs, char **args)
{
    return write_file("/proc/sys/kernel/hostname", args[1]);
}

int do_ifup(int nargs, char **args)
{
    return __ifupdown(args[1], 1);
}


static int do_insmod_inner(int nargs, char **args, int opt_len)
{
    char options[opt_len + 1];
    int i;

    options[0] = '\0';
    if (nargs > 2) {
        strcpy(options, args[2]);
        for (i = 3; i < nargs; ++i) {
            strcat(options, " ");
            strcat(options, args[i]);
        }
    }

    return insmod(args[1], options);
}

int do_insmod(int nargs, char **args)
{
    int i;
    int size = 0;

    if (nargs > 2) {
        for (i = 2; i < nargs; ++i)
            size += strlen(args[i]) + 1;
    }

    return do_insmod_inner(nargs, args, size);
}

int do_mkdir(int nargs, char **args)
{
    mode_t mode = 0755;
    int ret;

    /* mkdir <path> [mode] [owner] [group] */

    if (nargs >= 3) {
        mode = strtoul(args[2], 0, 8);
    }

    ret = make_dir(args[1], mode);
    /* chmod in case the directory already exists */
    if (ret == -1 && errno == EEXIST) {
        ret = _chmod(args[1], mode);
    }
    if (ret == -1) {
        return -errno;
    }

    if (nargs >= 4) {
        uid_t uid = decode_uid(args[3]);
        gid_t gid = -1;

        if (nargs == 5) {
            gid = decode_uid(args[4]);
        }

        if (_chown(args[1], uid, gid) < 0) {
            return -errno;
        }

        /* chown may have cleared S_ISUID and S_ISGID, chmod again */
        if (mode & (S_ISUID | S_ISGID)) {
            ret = _chmod(args[1], mode);
            if (ret == -1) {
                return -errno;
            }
        }
    }

    return 0;
}

int do_mknod(int nargs, char **args)
{
    dev_t dev;
    int major;
    int minor;
    int mode;

    /* mknod <path> <type> <major> <minor> */

    if (nargs != 5) {
        return -1;
    }

    major = strtoul(args[3], 0, 0);
    minor = strtoul(args[4], 0, 0);
    dev = (major << 8) | minor;

    if (strcmp(args[2], "c") == 0) {
        mode = S_IFCHR;
    } else {
        mode = S_IFBLK;
    }

    if (mknod(args[1], mode, dev)) {
        ERROR("init: mknod failed");
        return -1;
    }

    return 0;
}


static struct {
    const char *name;
    unsigned flag;
} mount_flags[] = {
    { "noatime",    MS_NOATIME },
    { "noexec",     MS_NOEXEC },
    { "nosuid",     MS_NOSUID },
    { "nodev",      MS_NODEV },
    { "nodiratime", MS_NODIRATIME },
    { "ro",         MS_RDONLY },
    { "rw",         0 },
    { "remount",    MS_REMOUNT },
    { "bind",       MS_BIND },
    { "rec",        MS_REC },
    { "unbindable", MS_UNBINDABLE },
    { "private",    MS_PRIVATE },
    { "slave",      MS_SLAVE },
    { "shared",     MS_SHARED },
    { "defaults",   0 },
    { 0,            0 },
};

#define UNLOCK_ERASE_COMPLETE 0x1
#define UNLOCK_ERASE_NOT_COMPLETE 0x0
static int check_unlock_earse_complete_stats(void)
{
    struct env_ioctl unlock_erase;
    int env_fd;
    int ret;

    if((env_fd = open("/proc/lk_env", O_RDWR)) < 0) {
        ERROR("Open env for format check fail.\n");
        goto FAIL_RUTURN;
    }
    if(!(unlock_erase.name = calloc(13, sizeof(char)))) {
        ERROR("Allocate Memory for env name fail.\n");
        goto FREE_FD;
    }
    if(!(unlock_erase.value = calloc(10, sizeof(char)))) {
        ERROR("Allocate Memory for env value fail.\n");
        goto FREE_ALLOCATE_NAME;
    }
    unlock_erase.name_len = 13;
    unlock_erase.value_len = 10;
    memcpy(unlock_erase.name, "unlock_erase", 12);
    if(ret = ioctl(env_fd, ENV_READ, &unlock_erase)) {
        ERROR("Get env for format check fail ret = %d, errno = %d.\n", ret, errno);
        goto FREE_ALLOCATE_VALUE;
    }
    if(unlock_erase.value) {
        ERROR("unlock_erase stats = %s\n", unlock_erase.value);
    } else {
        ERROR("unlock_erase stats is not be set.\n");
    }
    if(strncmp(unlock_erase.value, "pass", 4)) {
        ERROR("There is not a sucessfully unlock erase(May be not excute unlock erase or excute fail).\n");
        goto FREE_ALLOCATE_VALUE; 
    }else {
        ERROR("There is a sucessfully unlock erase, we musk format data soon.\n");
    }

    free(unlock_erase.name);
    free(unlock_erase.value);
    close(env_fd);
    return UNLOCK_ERASE_COMPLETE; 
FREE_ALLOCATE_VALUE:
    free(unlock_erase.value);
FREE_ALLOCATE_NAME:
    free(unlock_erase.name);
FREE_FD:
    close(env_fd);
FAIL_RUTURN:
    return UNLOCK_ERASE_NOT_COMPLETE;
}

static int clear_unlock_erase_stats(void)
{
    struct env_ioctl unlock_erase;
    int env_fd;
    int ret;

    if((env_fd = open("/proc/lk_env", O_RDWR)) < 0) {
        ERROR("Open env for format check fail.\n");
        goto FAIL_RUTURN;
    }
    if(!(unlock_erase.name = calloc(13, sizeof(char)))) {
        ERROR("Allocate Memory for env name fail.\n");
        goto FREE_FD;
    }
    if(!(unlock_erase.value = calloc(10, sizeof(char)))) {
        ERROR("Allocate Memory for env value fail.\n");
        goto FREE_ALLOCATE_NAME;
    }
    unlock_erase.name_len = 13;
    unlock_erase.value_len = 10;
    memcpy(unlock_erase.name, "unlock_erase", 12);
    memcpy(unlock_erase.value, "clear", 5);
    if(ret = ioctl(env_fd, ENV_WRITE, &unlock_erase)) {
        ERROR("Get env for format check fail.ret = %d, errno = %d\n", ret, errno);
        goto FREE_ALLOCATE_VALUE;
    }

    ERROR("Successfully clear env.\n");
    free(unlock_erase.name);
    free(unlock_erase.value);
    close(env_fd);
    return UNLOCK_ERASE_COMPLETE; 
FREE_ALLOCATE_VALUE:
    free(unlock_erase.value);
FREE_ALLOCATE_NAME:
    free(unlock_erase.name);
FREE_FD:
    close(env_fd);
FAIL_RUTURN:
    return UNLOCK_ERASE_NOT_COMPLETE;
}
#define DATA_MNT_POINT "/data"
#if defined(MTK_CACHE_MERGE_SUPPORT)
#define OLD_CACHE_MNT_POINT "/cache"
#define NEW_CACHE_MNT_POINT "/.cache"
#define CACHE_LINK_TARGET   "/data/.cache"
#endif

/* mount <type> <device> <path> <flags ...> <options> */
int do_mount(int nargs, char **args)
{
    char tmp[64];
    char *source, *target, *type;
    char *options = NULL;
    unsigned flags = 0;
    int n, i;
    int wait = 0;
    /* add for power loss test */
    struct stat stbuf; 

    for (n = 4; n < nargs; n++) {
        for (i = 0; mount_flags[i].name; i++) {
            if (!strcmp(args[n], mount_flags[i].name)) {
                flags |= mount_flags[i].flag;
                break;
            }
        }

        if (!mount_flags[i].name) {
            if (!strcmp(args[n], "wait"))
                wait = 1;
            /* if our last argument isn't a flag, wolf it up as an option string */
            else if (n + 1 == nargs)
                options = args[n];
        }
    }

    type = args[1];
    source = args[2];
    target = args[3];

#if defined(MTK_CACHE_MERGE_SUPPORT)
    if (!strcmp(target, OLD_CACHE_MNT_POINT)) {
        strcpy(target, NEW_CACHE_MNT_POINT);
        if (mkdir(NEW_CACHE_MNT_POINT, 0770) != 0) {
            if (errno != EEXIST) {
                printf("Can't mkdir %s (%s)\n", NEW_CACHE_MNT_POINT, strerror(errno));
                return -1;
            }
        }        
        _chown(NEW_CACHE_MNT_POINT, decode_uid("system"), decode_uid("cache"));
    }
#endif

    if (!strncmp(source, "mtd@", 4)) {
        n = mtd_name_to_number(source + 4);
        if (n < 0) {
            return -1;
        }

        sprintf(tmp, "/dev/block/mtdblock%d", n);

        if (wait)
            wait_for_file(tmp, COMMAND_RETRY_TIMEOUT);
        if (mount(tmp, target, type, flags, options) < 0) {
            return -1;
        }

        goto exit_success;
#ifdef MTK_NAND_UBIFS_SUPPORT
    } else if (!strncmp(source, "ubi@", 4)) {
        n = ubi_attach_mtd(source + 4);
        if (n < 0) {
            return -1;
        }

        sprintf(tmp, "/dev/ubi%d_0", n);

        if (wait)
            wait_for_file(tmp, COMMAND_RETRY_TIMEOUT);
        if (mount(tmp, target, type, flags, options) < 0) {
            ubi_detach_dev(n);
            return -1;
        }
        goto exit_success;
#endif
    } else if (!strncmp(source, "loop@", 5)) {
        int mode, loop, fd;
        struct loop_info info;

        mode = (flags & MS_RDONLY) ? O_RDONLY : O_RDWR;
        fd = open(source + 5, mode);
        if (fd < 0) {
            return -1;
        }

        for (n = 0; ; n++) {
            sprintf(tmp, "/dev/block/loop%d", n);
            loop = open(tmp, mode);
            if (loop < 0) {
                return -1;
            }

            /* if it is a blank loop device */
            if (ioctl(loop, LOOP_GET_STATUS, &info) < 0 && errno == ENXIO) {
                /* if it becomes our loop device */
                if (ioctl(loop, LOOP_SET_FD, fd) >= 0) {
                    close(fd);

                    if (mount(tmp, target, type, flags, options) < 0) {
                        ioctl(loop, LOOP_CLR_FD, 0);
                        close(loop);
                        return -1;
                    }

                    close(loop);
                    goto exit_success;
                }
            }

            close(loop);
        }

        close(fd);
        ERROR("out of loopback devices");
        return -1;
    } else {

#ifdef MTK_EMMC_SUPPORT
         struct phone_encrypt_state ps;  
         if (!strcmp(target, DATA_MNT_POINT)) {
             if (misc_get_phone_encrypt_state(&ps) < 0) {
                 printf("Failed to get encrypted status in MISC\n");
             }
             else {
                 printf("Success: get encrypted status: 0x%x in MISC\n", ps.state);
             }  
         }
#endif
orignal_mount:

        if (wait)
            wait_for_file(source, COMMAND_RETRY_TIMEOUT);

        char prop[PROP_VALUE_MAX];

        property_get("ro.crypto.state", prop);

        NOTICE("ro.crypto.state='%s'\n", prop);

        if(!strcmp(prop,"encrypted") && (!strcmp(target,"/protect_f") || !strcmp(target,"/protect_s")) ) {
             NOTICE("/data is encrypted. Need to mount '%s' as tmpfs\n", target);
             if (fs_mgr_do_tmpfs_mount(target)) {
                 ERROR("Mount '%s' to tmpfs fail. \n", target);
                 return -1;
             }          
             NOTICE("Cp md folder from emmc to the tmpfs of '%s'\n", target);
             char cmd[256];
             char partition_name[256];
             strcpy(partition_name, target+1);
             sprintf(cmd, "/system/bin/mkdir /mnt/%s", partition_name);
             system(cmd);
             sprintf(cmd, "/system/bin/mount -t ext4 /emmc@%s /mnt/%s", partition_name, partition_name);
             system(cmd); 
             sprintf(cmd, "/system/bin/cp -R -p /mnt/%s/md /%s", partition_name, partition_name);
             system(cmd);     
             sprintf(cmd, "/system/bin/umount /mnt/%s", partition_name);
             system(cmd); 
             sprintf(cmd, "/system/bin/rm -rf /mnt/%s", partition_name);
             system(cmd);       
        }        
        else if (mount(source, target, type, flags, options) < 0) {

		/*auto-format the partition when the partition is empty or mount fail*/
		if(strcmp(target,"/cache")&& strcmp(target,"/system")&& strcmp(target,"/system/secro") && !strncmp(type,"ext4",4)){
			int ret,status;
			pid_t pid;
			printf("mount  %s  to %s fail, it may be a empty partition\n",source,target);
                //Check if usrdata is correctly clear by fastboot. If clear complete correctly, make ext4 image.
                if(!strcmp(target, DATA_MNT_POINT)) {
                    if(check_unlock_earse_complete_stats() == UNLOCK_ERASE_COMPLETE) {
                        ERROR("Usrdata will format soon.\n");
                    } else {
                        ERROR("Skip format usrdata.\n");
                        goto SKIP_AUTO_FORMAT; 
                    }
                }
        				pid = fork();
        				if(pid<0){
        						printf("create process fail\n");	
        						return -1;
        				}else if(pid ==0){
        						printf("create process to generate image\n");
        						execl("/system/bin/make_ext4fs","-w",source,NULL);
        						printf("can not run /system/bin/make_ext4fs\n");
        						//return -1;
        						exit(-1);	
        				}else{
        					 while (pid != waitpid(pid,&status,0));

                    if(status!=0){
                        printf("make_ext4fs failed on %s\n",target);
                        return -1;
                    }else{
                        printf("make_ext4fs on %s sucess!!\n",target);	
                    }
                    if (mount(source, target, type, flags, options) < 0){
                        printf("re-mount %s fail\n",target);
                        return -1;	
                    } else {
                        goto SUCCESS_MOUNT_HANDLE;
                    }
                }
            }
SKIP_AUTO_FORMAT:
            if (!strcmp(target, DATA_MNT_POINT)) {
                int fd;
                if ((fd = open(source, O_RDONLY)) < 0) {
                     printf("Mount /data fail because source(%s) doesn't exist.", source);
                     return -1;
                }
            }

            if (!strcmp(target, DATA_MNT_POINT) && !partition_wiped(source)) {
                if (fs_mgr_do_tmpfs_mount(DATA_MNT_POINT)) {
                    return -1;
                }

                /* Set the property that triggers the framework to do a minimal
               * startup and ask the user for a password
               */
                property_set("ro.crypto.state", "encrypted");
                property_set("vold.decrypt", "1");
            } else {
                return -1;
            }
        }
#ifdef MTK_EMMC_SUPPORT
        else {
SUCCESS_MOUNT_HANDLE:
             if (!strcmp(target, DATA_MNT_POINT)) {
                    if (ps.state == PHONE_ENCRYPTED) {
                        ps.state = PHONE_UNCRYPTED;
                        if (misc_set_phone_encrypt_state(&ps) < 0) {
                            printf("Failed to set encrypted status to 0x%x in MISC\n", ps.state);
                        }
                        else {
                            printf("Success: Set encrypted status to 0x%x in MISC\n", ps.state);
                        }
                    }
                if(check_unlock_earse_complete_stats() == UNLOCK_ERASE_COMPLETE) {
                    ERROR("Format usrdata successfully, then begin to clear env.\n");
                    clear_unlock_erase_stats();
                }
            }
        }
#else
SUCCESS_MOUNT_HANDLE:
#endif
        if (!strcmp(target, DATA_MNT_POINT)) {
            char fs_flags[32];

            /* Save the original mount options */
            property_set("ro.crypto.fs_type", type);
            property_set("ro.crypto.fs_real_blkdev", source);
            property_set("ro.crypto.fs_mnt_point", target);
            if (options) {
                property_set("ro.crypto.fs_options", options);
            }
            snprintf(fs_flags, sizeof(fs_flags), "0x%8.8x", flags);
            property_set("ro.crypto.fs_flags", fs_flags);
        }
        if (!strncmp(type, "ext4", 4)){
            if (!strncmp(target, "/data", 5)){
               printf("delete lost-found in data dir\n");
               system("/system/bin/rm -r /data/lost+found/*");
               
               if (stat("/data/data", &stbuf) < 0){
                   printf("stat syscall fail\n");
               }
               if (S_ISREG(stbuf.st_mode)){
                   printf("delete /data/data file\n");
                   system("/system/bin/rm -r /data/data");    
               }

               if (stat("/data/system", &stbuf) < 0){
                   printf("stat syscall fail\n");
               }
               if (S_ISREG(stbuf.st_mode)){
                   printf("delete /data/system file\n");
                   system("/system/bin/rm -r /data/system");    
               }

               if (stat("/data/misc", &stbuf) < 0){
                   printf("stat syscall fail\n");
               }
               if (S_ISREG(stbuf.st_mode)){
                   printf("delete /data/misc file\n");
                   system("/system/bin/rm -r /data/misc");    
               }

               if (stat("/data/local", &stbuf) < 0){
                   printf("stat syscall fail\n");
               }
               if (S_ISREG(stbuf.st_mode)){
                   printf("delete /data/local file\n");
                   system("/system/bin/rm -r /data/local");    
               }

               if (stat("/data/app-private", &stbuf) < 0){
                   printf("stat syscall fail\n");
               }
               if (S_ISREG(stbuf.st_mode)){
                   printf("delete /data/app-private file\n");
                   system("/system/bin/rm -r /data/app-private");    
               }

               if (stat("/data/dalvik-cache", &stbuf) < 0){
                   printf("stat syscall fail\n");
               }
               if (S_ISREG(stbuf.st_mode)){
                   printf("delete /data/dalvik-cache file\n");
                   system("/system/bin/rm -r /data/dalvik-cache");    
               }


               if (stat("/data/property", &stbuf) < 0){
                   printf("stat syscall fail\n");
               }
               if (S_ISREG(stbuf.st_mode)){
                   printf("delete /data/property file\n");
                   system("/system/bin/rm -r /data/property");    
               } 

               if (stat("/data/mvg_root", &stbuf) < 0){
                   printf("stat syscall fail\n");
               }
               if (S_ISREG(stbuf.st_mode)){
                   printf("delete /data/mvg_root file\n");
                   system("/system/bin/rm -r /data/mvg_root");    
               }

               if (stat("/data/anr", &stbuf) < 0){
                   printf("stat syscall fail\n");
               }
               if (S_ISREG(stbuf.st_mode)){
                   printf("delete /data/anr file\n");
                   system("/system/bin/rm -r /data/anr");    
               }

               if (stat("/data/app", &stbuf) < 0){
                   printf("stat syscall fail\n");
               }
               if (S_ISREG(stbuf.st_mode)){
                   printf("delete /data/app file\n");
                   system("/system/bin/rm -r /data/app");    
               }

               if (stat("/data/nvram", &stbuf) < 0){
                   printf("stat syscall fail\n");
               }
               if (S_ISREG(stbuf.st_mode)){
                   printf("delete /data/nvram file\n");
                   system("/system/bin/rm -r /data/nvram");    
               }

               if (stat("/data/secure", &stbuf) < 0){
                   printf("stat syscall fail\n");
               }
               if (S_ISREG(stbuf.st_mode)){
                   printf("delete /data/secure file\n");
                   system("/system/bin/rm -r /data/secure");    
               }
            }

            if (!strncmp(target, "/cache", 6)){
               printf("delete lost-found in cache dir\n");
               system("/system/bin/rm -r /cache/lost+found/*");
            }
        }        
    }

exit_success:

    /* If not running encrypted, then set the property saying we are
     * unencrypted, and also trigger the action for a nonencrypted system.
     */
    if (!strcmp(target, DATA_MNT_POINT)) {
        char prop[PROP_VALUE_MAX];

        property_get("ro.crypto.state", prop);

        if (strcmp(prop, "encrypted")) {
            property_set("ro.crypto.state", "unencrypted");
            action_for_each_trigger("nonencrypted", action_add_queue_tail);
        }
    }

#if defined(MTK_CACHE_MERGE_SUPPORT)
    if (!strcmp(target, DATA_MNT_POINT)) {
        if (mkdir(CACHE_LINK_TARGET, 0770) != 0) {
            if (errno != EEXIST) {
                printf("Can't mkdir %s (%s)\n", CACHE_LINK_TARGET, strerror(errno));
                return -1;
            }
        }        
        _chown(CACHE_LINK_TARGET, decode_uid("system"), decode_uid("cache"));
        
        rmdir(OLD_CACHE_MNT_POINT);
        if (symlink(CACHE_LINK_TARGET, OLD_CACHE_MNT_POINT) != 0) {
            if (errno != EEXIST) {
                printf("create symlink from %s to %s failed(%s)\n", 
                                CACHE_LINK_TARGET, OLD_CACHE_MNT_POINT, strerror(errno));
                return -1;
            }
        }

    }
    if (!strcmp(target, NEW_CACHE_MNT_POINT)) { 
        _chown(NEW_CACHE_MNT_POINT, decode_uid("system"), decode_uid("cache"));
        _chmod(NEW_CACHE_MNT_POINT, 0770);
    }
#endif
    return 0;

}

int do_mount_all(int nargs, char **args)
{
    pid_t pid;
    int ret = -1;
    int child_ret = -1;
    int status;
    const char *prop;
    struct fstab *fstab;

    if (nargs != 2) {
        return -1;
    }

    /*
     * Call fs_mgr_mount_all() to mount all filesystems.  We fork(2) and
     * do the call in the child to provide protection to the main init
     * process if anything goes wrong (crash or memory leak), and wait for
     * the child to finish in the parent.
     */
    pid = fork();
    if (pid > 0) {
        /* Parent.  Wait for the child to return */
        waitpid(pid, &status, 0);
        if (WIFEXITED(status)) {
            ret = WEXITSTATUS(status);
        } else {
            ret = -1;
        }
    } else if (pid == 0) {
        /* child, call fs_mgr_mount_all() */
        klog_set_level(6);  /* So we can see what fs_mgr_mount_all() does */
        fstab = fs_mgr_read_fstab(args[1]);
        child_ret = fs_mgr_mount_all(fstab);
        fs_mgr_free_fstab(fstab);
        if (child_ret == -1) {
            ERROR("fs_mgr_mount_all returned an error\n");
        }
        exit(child_ret);
    } else {
        /* fork failed, return an error */
        return -1;
    }

    /* ret is 1 if the device is encrypted, 0 if not, and -1 on error */
    if (ret == 1) {
        property_set("ro.crypto.state", "encrypted");
        property_set("vold.decrypt", "1");
    } else if (ret == 0) {
        property_set("ro.crypto.state", "unencrypted");
        /* If fs_mgr determined this is an unencrypted device, then trigger
         * that action.
         */
        action_for_each_trigger("nonencrypted", action_add_queue_tail);
    }

    return ret;
}

int do_swapon_all(int nargs, char **args)
{
    struct fstab *fstab;
    int ret;

    fstab = fs_mgr_read_fstab(args[1]);
    ret = fs_mgr_swapon_all(fstab);
    fs_mgr_free_fstab(fstab);

    return ret;
}

int do_setcon(int nargs, char **args) {
    if (is_selinux_enabled() <= 0)
        return 0;
    if (setcon(args[1]) < 0) {
        return -errno;
    }
    return 0;
}

int do_setenforce(int nargs, char **args) {
    if (is_selinux_enabled() <= 0)
        return 0;
    if (security_setenforce(atoi(args[1])) < 0) {
        return -errno;
    }
    return 0;
}

int do_setkey(int nargs, char **args)
{
    struct kbentry kbe;
    kbe.kb_table = strtoul(args[1], 0, 0);
    kbe.kb_index = strtoul(args[2], 0, 0);
    kbe.kb_value = strtoul(args[3], 0, 0);
    return setkey(&kbe);
}

int do_setprop(int nargs, char **args)
{
    const char *name = args[1];
    const char *value = args[2];
    char prop_val[PROP_VALUE_MAX];
    int ret;

    ret = expand_props(prop_val, value, sizeof(prop_val));
    if (ret) {
        ERROR("cannot expand '%s' while assigning to '%s'\n", value, name);
        return -EINVAL;
    }
    property_set(name, prop_val);
    return 0;
}

int do_setrlimit(int nargs, char **args)
{
    struct rlimit limit;
    int resource;
    resource = atoi(args[1]);
    limit.rlim_cur = atoi(args[2]);
    limit.rlim_max = atoi(args[3]);
    return setrlimit(resource, &limit);
}

int do_start(int nargs, char **args)
{
    struct service *svc;
    svc = service_find_by_name(args[1]);
    if (svc) {
        service_start(svc, NULL);
    }
    return 0;
}

int do_stop(int nargs, char **args)
{
    struct service *svc;
    svc = service_find_by_name(args[1]);
    if (svc) {
        service_stop(svc);
    }
    return 0;
}

int do_restart(int nargs, char **args)
{
    struct service *svc;
    svc = service_find_by_name(args[1]);
    if (svc) {
        service_restart(svc);
    }
    return 0;
}

int do_powerctl(int nargs, char **args)
{
    char command[PROP_VALUE_MAX];
    int res;
    int len = 0;
    int cmd = 0;
    char *reboot_target;

    res = expand_props(command, args[1], sizeof(command));
    if (res) {
        ERROR("powerctl: cannot expand '%s'\n", args[1]);
        return -EINVAL;
    }

    if (strncmp(command, "shutdown", 8) == 0) {
        cmd = ANDROID_RB_POWEROFF;
        len = 8;
    } else if (strncmp(command, "reboot", 6) == 0) {
        cmd = ANDROID_RB_RESTART2;
        len = 6;
    } else {
        ERROR("powerctl: unrecognized command '%s'\n", command);
        return -EINVAL;
    }

    if (command[len] == ',') {
        reboot_target = &command[len + 1];
    } else if (command[len] == '\0') {
        reboot_target = "";
    } else {
        ERROR("powerctl: unrecognized reboot target '%s'\n", &command[len]);
        return -EINVAL;
    }

    return android_reboot(cmd, 0, reboot_target);
}

int do_trigger(int nargs, char **args)
{
    action_for_each_trigger(args[1], action_add_queue_tail);
    return 0;
}

int do_symlink(int nargs, char **args)
{
    return symlink(args[1], args[2]);
}

int do_rm(int nargs, char **args)
{
    return unlink(args[1]);
}

int do_rmdir(int nargs, char **args)
{
    return rmdir(args[1]);
}

int do_sysclktz(int nargs, char **args)
{
    struct timezone tz;

    if (nargs != 2)
        return -1;

    memset(&tz, 0, sizeof(tz));
    tz.tz_minuteswest = atoi(args[1]);   
    if (settimeofday(NULL, &tz))
        return -1;
    return 0;
}

int do_write(int nargs, char **args)
{
    const char *path = args[1];
    const char *value = args[2];
    char prop_val[PROP_VALUE_MAX];
    int ret;

    ret = expand_props(prop_val, value, sizeof(prop_val));
    if (ret) {
        ERROR("cannot expand '%s' while writing to '%s'\n", value, path);
        return -EINVAL;
    }
    return write_file(path, prop_val);
}

int do_copy(int nargs, char **args)
{
    char *buffer = NULL;
    int rc = 0;
    int fd1 = -1, fd2 = -1;
    struct stat info;
    int brtw, brtr;
    char *p;

    if (nargs != 3)
        return -1;

    if (stat(args[1], &info) < 0) 
        return -1;

    if ((fd1 = open(args[1], O_RDONLY)) < 0) 
        goto out_err;

    if ((fd2 = open(args[2], O_WRONLY|O_CREAT|O_TRUNC, 0660)) < 0)
        goto out_err;

    if (!(buffer = malloc(info.st_size)))
        goto out_err;

    p = buffer;
    brtr = info.st_size;
    while(brtr) {
        rc = read(fd1, p, brtr);
        if (rc < 0)
            goto out_err;
        if (rc == 0)
            break;
        p += rc;
        brtr -= rc;
    }

    p = buffer;
    brtw = info.st_size;
    while(brtw) {
        rc = write(fd2, p, brtw);
        if (rc < 0)
            goto out_err;
        if (rc == 0)
            break;
        p += rc;
        brtw -= rc;
    }

    rc = 0;
    goto out;
out_err:
    rc = -1;
out:
    if (buffer)
        free(buffer);
    if (fd1 >= 0)
        close(fd1);
    if (fd2 >= 0)
        close(fd2);
    return rc;
}

int do_chown(int nargs, char **args) {
#if 0
    //tempararily disable this code section
    /* GID is optional. */
    if (nargs == 3) {
        if (_chown(args[2], decode_uid(args[1]), -1) < 0)
            return -errno;
    } else if (nargs == 4) {
        if (_chown(args[3], decode_uid(args[1]), decode_uid(args[2])) < 0)
            return -errno;
    } else {
        return -1;
    }
    return 0;
#else
    char tmp[64];
    char *target;
    int n;
    switch (nargs){
        case 3:
            target = args[2];
            break;
        case 4:
            target = args[3];
            break;
        default:
            ERROR("invalid args num: %d, It should be 3 or 4\n", nargs);
            return -1;
    }

    if (!strncmp(target, "mtd@", 4)) {
        n = mtd_name_to_number(target + 4);
            if (n < 0) {
            return -1;
        }
        sprintf(tmp, "/dev/mtd/mtd%d", n);
        target = tmp;

    }

    //ERROR("do_chown debug: target:%s\n",target);
    /* GID is optional. */
    if (nargs == 3) {
    if (_chown(target, decode_uid(args[1]), -1) < 0)
            return -errno;
    } else if (nargs == 4) {
        if (_chown(target, decode_uid(args[1]), decode_uid(args[2])))
            return -errno;
    } else {
        return -1;
    }
    return 0;
#endif
}

static mode_t get_mode(const char *s) {
    mode_t mode = 0;
    while (*s) {
        if (*s >= '0' && *s <= '7') {
            mode = (mode<<3) | (*s-'0');
        } else {
            return -1;
        }
        s++;
    }
    return mode;
}

int do_chmod(int nargs, char **args) {
#if 0
    //tempararily disable this code section
    mode_t mode = get_mode(args[1]);
    if (_chmod(args[2], mode) < 0) {
        return -errno;
    }
    return 0;
#else
    char tmp[64];
    char *target;
    int n;
    target = args[2];

    if (!strncmp(target, "mtd@", 4)) {
        n = mtd_name_to_number(target + 4);
            if (n < 0) {
            return -1;
        }
        sprintf(tmp, "/dev/mtd/mtd%d", n);
        target = tmp;

    }

    //ERROR("do_chmod debug: target:%s\n",target);

    mode_t mode = get_mode(args[1]);
    if (_chmod(target, mode) < 0) {
        return -errno;
    }
    return 0;
#endif
}

int do_restorecon(int nargs, char **args) {
    int i;

    for (i = 1; i < nargs; i++) {
        if (restorecon(args[i]) < 0)
            return -errno;
    }
    return 0;
}

int do_setsebool(int nargs, char **args) {
    const char *name = args[1];
    const char *value = args[2];
    SELboolean b;
    int ret;

    if (is_selinux_enabled() <= 0)
        return 0;

    b.name = name;
    if (!strcmp(value, "1") || !strcasecmp(value, "true") || !strcasecmp(value, "on"))
        b.value = 1;
    else if (!strcmp(value, "0") || !strcasecmp(value, "false") || !strcasecmp(value, "off"))
        b.value = 0;
    else {
        ERROR("setsebool: invalid value %s\n", value);
        return -EINVAL;
    }

    if (security_set_boolean_list(1, &b, 0) < 0) {
        ret = -errno;
        ERROR("setsebool: could not set %s to %s\n", name, value);
        return ret;
    }

    return 0;
}

int do_loglevel(int nargs, char **args) {
    if (nargs == 2) {
        klog_set_level(atoi(args[1]));
        return 0;
    }
    return -1;
}

int do_load_persist_props(int nargs, char **args) {
    if (nargs == 1) {
        load_persist_props();
        return 0;
    }
    return -1;
}

int do_wait(int nargs, char **args)
{
    if (nargs == 2) {
        return wait_for_file(args[1], COMMAND_RETRY_TIMEOUT);
    } else if (nargs == 3) {
        return wait_for_file(args[1], atoi(args[2]));
    } else
        return -1;
}
