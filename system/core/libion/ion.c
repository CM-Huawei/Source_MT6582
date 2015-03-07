/*
 *  ion.c
 *
 * Memory Allocator functions for ion
 *
 *   Copyright 2011 Google, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
#define LOG_TAG "ion"

#include <cutils/log.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <dlfcn.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <linux/ion.h>
#include <ion/ion.h>
#include <linux/ion_drv.h>
#include <pthread.h>
#include "ion_debug.h"
#if ION_DEBUGGER
extern struct map_info_t *g_milist;
extern pthread_mutex_t ion_debug_lock;
unsigned int ion_debug_lock_init = 0; 
unsigned int ion_debugger = 1;
#endif

int ion_open()
{
        int fd = open("/dev/ion", O_RDONLY);
        if (fd < 0)
                ALOGE("open /dev/ion failed!\n");
	else
	{
#if ION_DEBUGGER
		ion_debugger = ion_record_debug_info(fd,NULL,NULL,0,ION_FUNCTION_CHECK_ENABLE);
		if(ion_debugger == 0)
		{
			if((g_milist!= NULL)&&ion_debug_lock_init) 
			{
				release_my_map_info_list(g_milist);
	                	if(g_milist == NULL)
       		        	{
                	        	dl_unregister_notify_function();
                       			ion_debug_lock_init = 0;
                		}
			}
		}
		
		if(ion_debugger)
		{
		if((!ion_debug_lock_init)&&(g_milist == NULL))
		{
			ion_debug_lock_init = 1;
			pthread_mutex_init(&ion_debug_lock,NULL);
                        acquire_my_map_info_list();
                        dl_register_notify_function(add_system_map_entry,remove_system_map_entry);
		}
		else
		{
		        acquire_my_map_info_list();
		}
		ion_record_debug_info(fd,NULL,NULL,0,ION_FUNCTION_OPEN);	
		}
#endif
	}
        return fd;
}

int ion_close(int fd)
{
	int ret;
#if ION_DEBUGGER
	if(ion_debugger)
	{
	ion_record_debug_info(fd,NULL,NULL,0,ION_FUNCTION_CLOSE);
	release_my_map_info_list(g_milist);
	if(g_milist == NULL)
	{
		dl_unregister_notify_function();
                ion_debug_lock_init = 0;
	}
	}
#endif
        ret = close(fd);
        return ret;
}

static int ion_ioctl(int fd, int req, void *arg)
{
        int ret = ioctl(fd, req, arg);
        if (ret < 0) {
                ALOGE("ioctl %x failed with code %d: %s\n", req,
                       ret, strerror(errno));
                return -errno;
        }
        return ret;
}

int ion_alloc(int fd, size_t len, size_t align, unsigned int heap_mask,
	      unsigned int flags, struct ion_handle **handle)
{
        int ret;
        struct ion_allocation_data data = {
                .len = len,
                .align = align,
		.heap_mask = heap_mask,
                .flags = flags,
        };

        ret = ion_ioctl(fd, ION_IOC_ALLOC, &data);
        if (ret < 0)
                return ret;
        *handle = data.handle;

#if ION_RUNTIME_DEBUGGER
        ion_set_handle_backtrace(fd, data.handle);
#endif
        
#if ION_DEBUGGER
	if(ion_debugger)
	{
	ion_record_debug_info(fd,data.handle,NULL,0,ION_FUNCTION_ALLOC);
	}
#endif
        return ret;
}

int ion_alloc_mm(int fd, size_t len, size_t align, unsigned int flags,
              struct ion_handle **handle)
{
        int ret;
        struct ion_allocation_data data = {
                .len = len,
                .align = align,
                .flags = flags,
                .heap_mask = ION_HEAP_MULTIMEDIA_MASK
        };

        ret = ion_ioctl(fd, ION_IOC_ALLOC, &data);
        if (ret < 0)
                return ret;
        *handle = data.handle;
#if 0
    //sample code for debug info
    {
        int ion_fd = fd;
        struct ion_mm_data mm_data;
        
        mm_data.mm_cmd = ION_MM_SET_DEBUG_INFO;
        mm_data.config_buffer_param.handle = *handle;
        strncpy(mm_data.buf_debug_info_param.dbg_name, "buf_for_k", ION_MM_DBG_NAME_LEN);
        mm_data.buf_debug_info_param.value1 = 1;//getpid();
        mm_data.buf_debug_info_param.value2 = 2;
        mm_data.buf_debug_info_param.value3 = 3;
        mm_data.buf_debug_info_param.value4 = 4;
        if(ion_custom_ioctl(ion_fd, ION_CMD_MULTIMEDIA, &mm_data))
        {
            //config error
            ALOGE("[ion_dbg] config debug info error\n");
            return -1;
        }

    }
 #endif
#if ION_RUNTIME_DEBUGGER
        ion_set_handle_backtrace(fd, data.handle);
#endif

#if ION_DEBUGGER
	if(ion_debugger)
	{
	ion_record_debug_info(fd,data.handle,NULL,0,ION_FUNCTION_ALLOC_MM);
	}
#endif
        return ret;
}

int ion_alloc_syscontig(int fd, size_t len, size_t align, unsigned int flags, struct ion_handle **handle)
{
        int ret;
        struct ion_allocation_data data = {
                .len = len,
                .align = align,
                .flags = flags,
                .heap_mask = ION_HEAP_SYSTEM_CONTIG_MASK
        };

        ret = ion_ioctl(fd, ION_IOC_ALLOC, &data);
        if (ret < 0)
                return ret;
        *handle = data.handle;

#if ION_RUNTIME_DEBUGGER
        ion_set_handle_backtrace(fd, data.handle);
#endif
#if ION_DEBUGGER
	if(ion_debugger)
	{
	ion_record_debug_info(fd,data.handle,NULL,0,ION_FUNCTION_ALLOC_CONT);
	}
#endif 
        return ret;
}

int ion_free(int fd, struct ion_handle *handle)
{
        struct ion_handle_data data = {
                .handle = handle,
        };
#if ION_DEBUGGER
        if(ion_debugger)
        {
	ion_record_debug_info(fd,handle,NULL,0,ION_FUNCTION_FREE);
	}
#endif
        return ion_ioctl(fd, ION_IOC_FREE, &data);
}

int ion_map(int fd, struct ion_handle *handle, size_t length, int prot,
            int flags, off_t offset, unsigned char **ptr, int *map_fd)
{
        struct ion_fd_data data = {
                .handle = handle,
        };

        int ret = ion_ioctl(fd, ION_IOC_SHARE, &data);
        if (ret < 0)
                return ret;
        *map_fd = data.fd;
        if (*map_fd < 0) {
                ALOGE("map ioctl returned negative fd\n");
                return -EINVAL;
        }
        *ptr = mmap(NULL, length, prot, flags, *map_fd, offset);
        if (*ptr == MAP_FAILED) {
                ALOGE("mmap failed: %s\n", strerror(errno));
                return -errno;
        }
        return ret;
}

void* ion_mmap(int fd, void *addr, size_t length, int prot, int flags, int share_fd, off_t offset)
{
    void *mapping_address = NULL; 

    mapping_address =  mmap(addr, length, prot, flags, share_fd, offset);
#if ION_DEBUGGER
        if(ion_debugger)
    {
	int tmp_fd;
	tmp_fd = fd | share_fd << 16;
    	ion_record_debug_info(tmp_fd,NULL,mapping_address,length,ION_FUNCTION_MMAP);
    }
#endif
    return mapping_address;
}

int ion_munmap(int fd, void *addr, size_t length)
{
#if ION_DEBUGGER
	if(ion_debugger)
	{
    ion_record_debug_info(fd,NULL,addr,length,ION_FUNCTION_MUNMAP);
	}
#endif
    return munmap(addr, length);
}

int ion_share(int fd, struct ion_handle *handle, int *share_fd)
{
        int map_fd;
        int i;
        struct ion_fd_data data = {
                .handle = handle,
        };

        int ret = ion_ioctl(fd, ION_IOC_SHARE, &data);
        if (ret < 0)
                return ret;
        *share_fd = data.fd;
        if (*share_fd < 0) {
                ALOGE("share ioctl returned negative fd\n");
                return -EINVAL;
        }

//#ifdef _FDLEAK_DEBUG_
#if 0
extern void (*fdleak_record_backtrace)(int);
    if(fdleak_record_backtrace)
    {
        fdleak_record_backtrace(*share_fd);
    }

#endif
        
#if ION_DEBUGGER
	if(ion_debugger)
	{
		int tmp_fd;
		tmp_fd = fd | *share_fd << 16;
		ion_record_debug_info(tmp_fd,handle,NULL,0,ION_FUNCTION_SHARE);
	}
#endif
        return ret;
}

int ion_alloc_fd(int fd, size_t len, size_t align, unsigned int heap_mask,
		 unsigned int flags, int *handle_fd) {
	struct ion_handle *handle;
	int ret;

	ret = ion_alloc(fd, len, align, heap_mask, flags, &handle);
	if (ret < 0)
		return ret;
	ret = ion_share(fd, handle, handle_fd);
	ion_free(fd, handle);
	return ret;
}

int ion_share_close(int fd, int share_fd)
{
    return close(share_fd);
}

int ion_import(int fd, int share_fd, struct ion_handle **handle)
{
        struct ion_fd_data data = {
                .fd = share_fd,
        };

        int ret = ion_ioctl(fd, ION_IOC_IMPORT, &data);
        if (ret < 0)
                return ret;
        *handle = data.handle;

#if ION_RUNTIME_DEBUGGER
        ion_set_handle_backtrace(fd, data.handle);
#endif

#if ION_DEBUGGER 
	if(ion_debugger)
	{
	ion_record_debug_info(fd,data.handle,NULL,0,ION_FUNCTION_IMPORT);
	}
#endif
        return ret;
}

int ion_set_handle_backtrace(int fd, struct ion_handle *handle)
{
    ion_sys_data_t arg;
    int ret;
    arg.record_param.backtrace_num = \
        get_backtrace(__builtin_frame_address(0), arg.record_param.backtrace, BACKTRACE_SIZE);

    arg.record_param.handle = handle;
    arg.sys_cmd = ION_SYS_SET_HANDLE_BACKTRACE;
    ret = ion_custom_ioctl(fd,ION_CMD_SYSTEM,(void *)&arg);
    return ret;
}

int ion_custom_ioctl(int fd, unsigned int cmd, void* arg)
{
    struct ion_custom_data custom_data;
    custom_data.cmd = cmd;
    custom_data.arg = (unsigned long) arg;
    return ioctl(fd, ION_IOC_CUSTOM, &custom_data);
}

int ion_sync_fd(int fd, int handle_fd)
{
    struct ion_fd_data data = {
        .fd = handle_fd,
    };
    return ion_ioctl(fd, ION_IOC_SYNC, &data);
}
