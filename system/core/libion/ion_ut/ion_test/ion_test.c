#include <sys/mman.h>
#include <dlfcn.h>
#include <cutils/log.h>
#include <cutils/atomic.h>
#include <hardware/hardware.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <string.h>
#include <stdlib.h>
#include <sched.h>
#include <sys/resource.h>
#include <linux/fb.h>
#include <wchar.h>
#include <pthread.h>
#include <linux/mmprofile.h>
#include <linux/ion.h>
#include <linux/ion_drv.h>
#include <ion/ion.h>
#include <unistd.h>
//#pragma GCC optimize ("O0")
#ifdef LOG_TAG
#undef LOG_TAG
#endif
//#define LOG_TAG "ION_TEST"
//#define LogPrint(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, ## __VA_ARGS__)

#define ion_carveout_heap_test

unsigned int bufsize=1024*1024*8+256;

#ifdef __fdleakage_debug__

int main()
{
    int i;
    int ion_fd;
    int ion_test_fd;
    struct ion_handle* handle;
    int share_fd;
    volatile char* pBuf;
    pid_t pid;

    ion_fd = ion_open();
    if (ion_fd < 0)
    {
        printf("Cannot open ion device.\n");
        return 0;
    }
    if (ion_alloc_mm(ion_fd, bufsize, 4, 0, &handle))
    {
        printf("IOCTL[ION_IOC_ALLOC] failed!\n");
        return 0;
    }
    printf("begine!!\n");
    
    for(i=0; i<1000; i++)
    {
        printf("%d\n", i);
        if (ion_share(ion_fd, handle, &share_fd))
        {
            printf("IOCTL[ION_IOC_SHARE] failed!\n");
        }
    }

    printf("ION test done!\n");
    while(1);
    return 0;
}

#endif

#ifdef orginal_test 
int main(int argc, char **argv)
{
    int i;
    int ion_fd;
    int ion_test_fd;
    struct ion_handle* handle;
    int share_fd;
    volatile char* pBuf;
    pid_t pid;

    MMP_Event MMP_ION_DEBUG;
    MMP_ION_DEBUG = MMProfileRegisterEvent(MMP_RootEvent, "ION Debug");
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagPulse, "Test Start");
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagStart, "Open ION device");
    ion_fd = ion_open();
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEnd, "Open ION device");
    if (ion_fd < 0)
    {
        printf("Cannot open ion device.\n");
        return 0;
    }
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagStart, "ioctl:ION_IOC_ALLOC");
    if (ion_alloc_mm(ion_fd, bufsize, 4, 0, &handle))
    {
        printf("IOCTL[ION_IOC_ALLOC] failed!\n");
        return 0;
    }
    MMProfileLogMetaStringEx(MMP_ION_DEBUG, MMProfileFlagEnd, (unsigned int)(handle), 0, "ioctl:ION_IOC_ALLOC");

    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagStart, "ioctl:ION_IOC_SHARE");
    if (ion_share(ion_fd, handle, &share_fd))
    {
        printf("IOCTL[ION_IOC_SHARE] failed!\n");
        return 0;
    }
    MMProfileLogMetaStringEx(MMP_ION_DEBUG, MMProfileFlagEnd, share_fd, 0, "ioctl:ION_IOC_SHARE");

    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagStart, "mmap");
    pBuf = ion_mmap(ion_fd, NULL, bufsize, PROT_READ|PROT_WRITE, MAP_SHARED, share_fd, 0);
    printf("ion_map: pBuf = 0x%x\n", pBuf);
    MMProfileLogMetaStringEx(MMP_ION_DEBUG, MMProfileFlagEnd, (unsigned int)pBuf, 0, "mmap");
    if (!pBuf)
    {
        printf("Cannot map ion buffer.\n");
        return 0;
    }


    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagStart, "user write");
    for (i=0; i<bufsize; i+=4)
    {
        *(volatile unsigned int*)(pBuf+i) = i;
    }
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEnd, "user write");
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagStart, "user read");
    for (i=0; i<bufsize; i+=4)
    {
        if(*(volatile unsigned int*)(pBuf+i) != i)
        {
            printf("ion_test: owner read error !!\n");
        }
    }
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEnd, "user read");

    printf("share buffer to child!!\n");
    {

        MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagPulse, "fork");
        pid = fork();
        if (pid == 0)
        {   //child
            struct ion_handle *handle;
            ion_fd = open("/dev/ion", O_RDONLY);
            ion_import(ion_fd, share_fd, &handle);
            pBuf = ion_mmap(ion_fd, NULL, bufsize, PROT_READ|PROT_WRITE, MAP_SHARED, share_fd, 0);
            printf("ion_test: map child pBuf = 0x%x\n", pBuf);

            for (i=0; i<bufsize; i+=4)
            {
                if(*(volatile unsigned int*)(pBuf+i) != i)
                {
                    printf("ion_test: child read error 0x%x!=0x%x!!\n", *(volatile unsigned int*)(pBuf+i),i);
                }
            }

            {
                struct ion_mm_data mm_data;
                mm_data.mm_cmd = ION_MM_CONFIG_BUFFER;
                mm_data.config_buffer_param.handle = handle;
                mm_data.config_buffer_param.eModuleID = 1;
                mm_data.config_buffer_param.security = 0;
                mm_data.config_buffer_param.coherent = 1;
                if (ion_custom_ioctl(ion_fd, ION_CMD_MULTIMEDIA, &mm_data))
                {
                    printf("IOCTL[ION_IOC_CUSTOM] Config Buffer failed!\n");
                    return 0;
                }
            }
            {
                struct ion_sys_data sys_data;
                sys_data.sys_cmd = ION_SYS_GET_PHYS;
                sys_data.get_phys_param.handle = handle;
                if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
                {
                    printf("IOCTL[ION_IOC_CUSTOM] Get Phys failed!\n");
                    return 0;
                }
                printf("child Physical address=0x%08X len=0x%X\n", sys_data.get_phys_param.phy_addr, sys_data.get_phys_param.len);
            }

            ion_munmap(ion_fd, pBuf, bufsize);
            ion_share_close(ion_fd, share_fd);
            close(ion_fd);

            printf("ion_test: child exit\n");
            exit(0);
        }

        printf("parent process goes...\n");

    }

    
    printf("Pass buffer to kernel.\n");
    ion_test_fd = open("/dev/ion_test", O_RDONLY);
    if (ion_test_fd < 0)
    {
        printf("Cannot open ion_test device.\n");
        return 0;
    }
    if (ioctl(ion_test_fd, 1, share_fd))
    {
        printf("ION_TEST_DRV ioctl failed.\n");
        return 0;
    }

    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagStart, "Config Buffer");
    {
        struct ion_mm_data mm_data;
        mm_data.mm_cmd = ION_MM_CONFIG_BUFFER;
        mm_data.config_buffer_param.handle = handle;
        mm_data.config_buffer_param.eModuleID = 1;
        mm_data.config_buffer_param.security = 0;
        mm_data.config_buffer_param.coherent = 1;
        if (ion_custom_ioctl(ion_fd, ION_CMD_MULTIMEDIA, &mm_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Config Buffer failed!\n");
            return 0;
        }
    }
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEnd, "Config Buffer");
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagStart, "Get Phys");
    {
        struct ion_sys_data sys_data;
        sys_data.sys_cmd = ION_SYS_GET_PHYS;
        sys_data.get_phys_param.handle = handle;
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Get Phys failed!\n");
            return 0;
        }
        printf("Physical address=0x%08X len=0x%X\n", sys_data.get_phys_param.phy_addr, sys_data.get_phys_param.len);
    }
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEnd, "Get Phys");

    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagStart, "Cache Sync");
    {
        struct ion_sys_data sys_data;
        sys_data.sys_cmd = ION_SYS_CACHE_SYNC;
        sys_data.cache_sync_param.handle = handle;

        sys_data.cache_sync_param.sync_type = ION_CACHE_CLEAN_BY_RANGE;
        MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEventSeparator, "Clean by range");
        printf("Clean by range.\n");
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Cache sync failed!\n");
            return 0;
        }
        MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEventSeparator, "Invalid by range");
        printf("Invalid by range.\n");
        sys_data.cache_sync_param.sync_type = ION_CACHE_INVALID_BY_RANGE;
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Cache sync failed!\n");
            return 0;
        }
        MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEventSeparator, "Flush by range");
        printf("Flush by range.\n");
        sys_data.cache_sync_param.sync_type = ION_CACHE_FLUSH_BY_RANGE;
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Cache sync failed!\n");
            return 0;
        }


        MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEventSeparator, "Clean all");
        printf("Clean all.\n");
        sys_data.cache_sync_param.sync_type = ION_CACHE_CLEAN_ALL;
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Cache sync failed!\n");
            return 0;
        }

/*
        MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEventSeparator, "Invalid all");
        printf("Invalid all.\n");
        sys_data.cache_sync_param.sync_type = ION_CACHE_INVALID_ALL;
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Cache sync failed!\n");
            return 0;
        }
*/
        MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEventSeparator, "Flush all");
        printf("Flush all.\n");
        sys_data.cache_sync_param.sync_type = ION_CACHE_FLUSH_ALL;
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Cache sync failed!\n");
            return 0;
        }

    }
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEnd, "Cache Sync");

    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagStart, "munmap");
    ion_munmap(ion_fd, pBuf, 0x1000);
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEnd, "munmap");

    ion_share_close(ion_fd, share_fd);
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagStart, "ioctl:ION_IOC_FREE");
    if (ion_free(ion_fd, handle))
    {
        printf("IOCTL[ION_IOC_FREE] failed!\n");
        return 0;
    }
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEnd, "ioctl:ION_IOC_FREE");
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagStart, "Close ION Device");
    ion_close(ion_fd);
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagEnd, "Close ION Device");
    MMProfileLogMetaString(MMP_ION_DEBUG, MMProfileFlagPulse, "Test End");
    printf("ION test done!\n");
    return 0;
}
#endif

#ifdef ion_carveout_heap_test

int carveout_test()
{
    int i;
    int ion_fd;
    int ion_test_fd;
    struct ion_handle* handle;
    int share_fd;
    volatile char* pBuf;
    pid_t pid;

    ion_fd = ion_open();
    if (ion_fd < 0)
    {
        printf("Cannot open ion device.\n");
        return 0;
    }
    if (ion_alloc(ion_fd, bufsize, ION_HEAP_CARVEOUT_MASK, 4, 0, &handle))
    {
        printf("IOCTL[ION_IOC_ALLOC] failed!\n");
        return 0;
    }

    if (ion_share(ion_fd, handle, &share_fd))
    {
        printf("IOCTL[ION_IOC_SHARE] failed!\n");
        return 0;
    }

    pBuf = ion_mmap(ion_fd, NULL, bufsize, PROT_READ|PROT_WRITE, MAP_SHARED, share_fd, 0);
    printf("ion_map: pBuf = 0x%x\n", pBuf);
    if (!pBuf)
    {
        printf("Cannot map ion buffer.\n");
        return 0;
    }


    for (i=0; i<bufsize; i+=4)
    {
        *(volatile unsigned int*)(pBuf+i) = i;
    }
    for (i=0; i<bufsize; i+=4)
    {
        if(*(volatile unsigned int*)(pBuf+i) != i)
        {
            printf("ion_test: owner read error !!\n");
        }
    }

    printf("share buffer to child!!\n");
    {

        pid = fork();
        if (pid == 0)
        {   //child
            struct ion_handle *handle;
            ion_fd = open("/dev/ion", O_RDONLY);
            ion_import(ion_fd, share_fd, &handle);
            pBuf = ion_mmap(ion_fd, NULL, bufsize, PROT_READ|PROT_WRITE, MAP_SHARED, share_fd, 0);
            printf("ion_test: map child pBuf = 0x%x\n", pBuf);

            for (i=0; i<bufsize; i+=4)
            {
                if(*(volatile unsigned int*)(pBuf+i) != i)
                {
                    printf("ion_test: child read error 0x%x!=0x%x!!\n", *(volatile unsigned int*)(pBuf+i),i);
                }
            }
            printf("child verify done!\n");

            {
                struct ion_mm_data mm_data;
                mm_data.mm_cmd = ION_MM_CONFIG_BUFFER;
                mm_data.config_buffer_param.handle = handle;
                mm_data.config_buffer_param.eModuleID = 1;
                mm_data.config_buffer_param.security = 0;
                mm_data.config_buffer_param.coherent = 1;
                if (ion_custom_ioctl(ion_fd, ION_CMD_MULTIMEDIA, &mm_data))
                {
                    printf("IOCTL[ION_IOC_CUSTOM] Config Buffer failed!\n");
                    return 0;
                }
            }
            {
                struct ion_sys_data sys_data;
                sys_data.sys_cmd = ION_SYS_GET_PHYS;
                sys_data.get_phys_param.handle = handle;
                if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
                {
                    printf("IOCTL[ION_IOC_CUSTOM] Get Phys failed!\n");
                    return 0;
                }
                printf("child Physical address=0x%08X len=0x%X\n", sys_data.get_phys_param.phy_addr, sys_data.get_phys_param.len);
            }

            ion_munmap(ion_fd, pBuf, bufsize);
            ion_share_close(ion_fd, share_fd);
            ion_free(ion_fd, handle);
            close(ion_fd);

            printf("ion_test: child exit\n");
            exit(0);
        }

        sleep(2);
        printf("parent process goes...\n");

    }


    
    {
        struct ion_mm_data mm_data;
        mm_data.mm_cmd = ION_MM_CONFIG_BUFFER;
        mm_data.config_buffer_param.handle = handle;
        mm_data.config_buffer_param.eModuleID = 1;
        mm_data.config_buffer_param.security = 0;
        mm_data.config_buffer_param.coherent = 1;
        if (ion_custom_ioctl(ion_fd, ION_CMD_MULTIMEDIA, &mm_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Config Buffer failed!\n");
            return 0;
        }
    }
    {
        struct ion_sys_data sys_data;
        sys_data.sys_cmd = ION_SYS_GET_PHYS;
        sys_data.get_phys_param.handle = handle;
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Get Phys failed!\n");
            return 0;
        }
        printf("Physical address=0x%08X len=0x%X\n", sys_data.get_phys_param.phy_addr, sys_data.get_phys_param.len);
    }

    {
        struct ion_sys_data sys_data;
        sys_data.sys_cmd = ION_SYS_CACHE_SYNC;
        sys_data.cache_sync_param.handle = handle;

        sys_data.cache_sync_param.sync_type = ION_CACHE_CLEAN_BY_RANGE;
        printf("Clean by range.\n");
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Cache sync failed!\n");
            return 0;
        }
        printf("Invalid by range.\n");
        sys_data.cache_sync_param.sync_type = ION_CACHE_INVALID_BY_RANGE;
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Cache sync failed!\n");
            return 0;
        }
        printf("Flush by range.\n");
        sys_data.cache_sync_param.sync_type = ION_CACHE_FLUSH_BY_RANGE;
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Cache sync failed!\n");
            return 0;
        }


        printf("Clean all.\n");
        sys_data.cache_sync_param.sync_type = ION_CACHE_CLEAN_ALL;
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Cache sync failed!\n");
            return 0;
        }

        printf("Flush all.\n");
        sys_data.cache_sync_param.sync_type = ION_CACHE_FLUSH_ALL;
        if (ion_custom_ioctl(ion_fd, ION_CMD_SYSTEM, &sys_data))
        {
            printf("IOCTL[ION_IOC_CUSTOM] Cache sync failed!\n");
            return 0;
        }

    }

    ion_munmap(ion_fd, pBuf, bufsize);

    ion_share_close(ion_fd, share_fd);
    if (ion_free(ion_fd, handle))
    {
        printf("IOCTL[ION_IOC_FREE] failed!\n");
        return 0;
    }
    ion_close(ion_fd);
    printf("ION test done!\n");

    return 0;
}

int main()
{
    int i;

    for(i=0; i<100; i++)
    {
        carveout_test();
        printf("i=%d  =====================\n", i);
    }

    return 0;

}


#endif


