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

#include <limits.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/ioctl.h>

#include <cutils/ashmem.h>
#include <cutils/log.h>
#include <cutils/atomic.h>

#include <hardware/hardware.h>
#include <hardware/gralloc.h>

#ifndef MTK_DEFAULT_AOSP
#include <hardware/gralloc_extra.h>
#endif

#include "gralloc_priv.h"
#include "gr.h"

/*****************************************************************************/
#ifndef MTK_DEFAULT_AOSP
#define GRALLOC_ALIGN( value, base ) (((value) + ((base) - 1)) & ~((base) - 1)) /// Add by Robin
#endif

struct gralloc_context_t {
    alloc_device_t  device;
    /* our private data here */
#ifndef MTK_DEFAULT_AOSP
#ifdef MTK_ION_SUPPORT
    int ion_dev_fd;
#endif
#endif
};

#ifndef MTK_DEFAULT_AOSP
struct gralloc_extra_t {
    extra_device_t device;
};
#endif

static int gralloc_alloc_buffer(alloc_device_t* dev,
        size_t size, int usage, buffer_handle_t* pHandle);

/*****************************************************************************/

int fb_device_open(const hw_module_t* module, const char* name,
        hw_device_t** device);

static int gralloc_device_open(const hw_module_t* module, const char* name,
        hw_device_t** device);

extern int gralloc_lock(gralloc_module_t const* module,
        buffer_handle_t handle, int usage,
        int l, int t, int w, int h,
        void** vaddr);

extern int gralloc_unlock(gralloc_module_t const* module,
        buffer_handle_t handle);

extern int gralloc_register_buffer(gralloc_module_t const* module,
        buffer_handle_t handle);

extern int gralloc_unregister_buffer(gralloc_module_t const* module,
        buffer_handle_t handle);

/*****************************************************************************/

static struct hw_module_methods_t gralloc_module_methods = {
        open: gralloc_device_open
};

struct private_module_t HAL_MODULE_INFO_SYM = {
    base: {
        common: {
            tag: HARDWARE_MODULE_TAG,
            version_major: 1,
            version_minor: 0,
            id: GRALLOC_HARDWARE_MODULE_ID,
            name: "Graphics Memory Allocator Module",
            author: "The Android Open Source Project",
            methods: &gralloc_module_methods
        },
        registerBuffer: gralloc_register_buffer,
        unregisterBuffer: gralloc_unregister_buffer,
        lock: gralloc_lock,
        unlock: gralloc_unlock,
    },
    framebuffer: 0,
    flags: 0,
    numBuffers: 0,
    bufferMask: 0,
    lock: PTHREAD_MUTEX_INITIALIZER,
    currentBuffer: 0,
};

/*****************************************************************************/

static int gralloc_alloc_framebuffer_locked(alloc_device_t* dev,
        size_t size, int usage, buffer_handle_t* pHandle)
{
    private_module_t* m = reinterpret_cast<private_module_t*>(
            dev->common.module);

    // allocate the framebuffer
    if (m->framebuffer == NULL) {
        // initialize the framebuffer, the framebuffer is mapped once
        // and forever.
        int err = mapFrameBufferLocked(m);
        if (err < 0) {
            return err;
        }
    }

    const uint32_t bufferMask = m->bufferMask;
    const uint32_t numBuffers = m->numBuffers;
    const size_t bufferSize = m->finfo.line_length * m->info.yres;
    if (numBuffers == 1) {
        // If we have only one buffer, we never use page-flipping. Instead,
        // we return a regular buffer which will be memcpy'ed to the main
        // screen when post is called.
        int newUsage = (usage & ~GRALLOC_USAGE_HW_FB) | GRALLOC_USAGE_HW_2D;
        return gralloc_alloc_buffer(dev, bufferSize, newUsage, pHandle);
    }

    if (bufferMask >= ((1LU<<numBuffers)-1)) {
        // We ran out of buffers.
        return -ENOMEM;
    }

    // create a "fake" handles for it
    intptr_t vaddr = intptr_t(m->framebuffer->base);
    private_handle_t* hnd = new private_handle_t(dup(m->framebuffer->fd), size,
            private_handle_t::PRIV_FLAGS_FRAMEBUFFER);

    // find a free slot
    for (uint32_t i=0 ; i<numBuffers ; i++) {
        if ((bufferMask & (1LU<<i)) == 0) {
            m->bufferMask |= (1LU<<i);
            break;
        }
        vaddr += bufferSize;
    }

    hnd->base = vaddr;
    hnd->offset = vaddr - intptr_t(m->framebuffer->base);

#ifndef MTK_DEFAULT_AOSP
    hnd->mva = (m->framebuffer->mva != -1) ?
        (m->framebuffer->mva + hnd->offset) : -1;

    ALOGI( "alloc_framebuffer va: 0x%x mva: 0x%x", hnd->base, hnd->mva );
#endif

    *pHandle = hnd;

    return 0;
}

static int gralloc_alloc_framebuffer(alloc_device_t* dev,
        size_t size, int usage, buffer_handle_t* pHandle)
{
    private_module_t* m = reinterpret_cast<private_module_t*>(
            dev->common.module);
    pthread_mutex_lock(&m->lock);
    int err = gralloc_alloc_framebuffer_locked(dev, size, usage, pHandle);
    pthread_mutex_unlock(&m->lock);
    return err;
}

static int gralloc_alloc_buffer(alloc_device_t* dev,
        size_t size, int usage, buffer_handle_t* pHandle)
{
#ifndef MTK_DEFAULT_AOSP
#ifdef MTK_ION_SUPPORT
    private_module_t* m = reinterpret_cast<private_module_t*>(dev->common.module);
    struct ion_handle *ion_hnd;
    unsigned char *cpu_ptr;
    int shared_fd;
    int ret;

    ret = ion_alloc_mm( m->ion_client, size, 0,  0, &ion_hnd );

    if (ret != 0){
        ALOGE("Failed to ion_alloc from ion_client:%d", m->ion_client);
        return -1;
    }

    ret = ion_share( m->ion_client, ion_hnd, &shared_fd );
    if (ret != 0) {
        ALOGE( "ion_share( %d ) failed", m->ion_client );
        if ( 0 != ion_free( m->ion_client, ion_hnd ) ) ALOGE( "ion_free( %d ) failed", m->ion_client );
        return -1;
    }
    cpu_ptr = (unsigned char*)mmap( NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, shared_fd, 0 );

    if (MAP_FAILED == cpu_ptr) {
        ALOGE( "ion_map( %d ) failed", m->ion_client );
        if ( 0 != ion_free( m->ion_client, ion_hnd ) ) ALOGE( "ion_free( %d ) failed", m->ion_client );
        close( shared_fd );
        return -1;
    }

    private_handle_t* hnd = new private_handle_t(shared_fd, size, 0);

    if (NULL != hnd) {

#ifdef MTK_GRALLOC_ION_DBG
        ALOGI("[+]hnd(%p), fd(%d)", hnd, shared_fd);
#endif

        hnd->base = (int)cpu_ptr;
        hnd->ion_hnd = ion_hnd;

        *pHandle = hnd;
        return 0;
    } else {
        ALOGE( "Gralloc out of mem for ion_client:%d", m->ion_client );
    }

    close( shared_fd );
    ret = munmap( cpu_ptr, size );
    if ( 0 != ret ) ALOGE( "munmap failed for base:%p size: %d", cpu_ptr, size );
    ret = ion_free( m->ion_client, ion_hnd );
    if ( 0 != ret ) ALOGE( "ion_free( %d ) failed", m->ion_client );

    return -1;

#endif // MTK_ION_SUPPORT
#endif // MTK_DEFAULT_AOSP

    int err = 0;
    int fd = -1;

    size = roundUpToPageSize(size);

    fd = ashmem_create_region("gralloc-buffer", size);
    if (fd < 0) {
        ALOGE("couldn't create ashmem (%s)", strerror(-errno));
        err = -errno;
    }

    if (err == 0) {
        private_handle_t* hnd = new private_handle_t(fd, size, 0);
        gralloc_module_t* module = reinterpret_cast<gralloc_module_t*>(
                dev->common.module);
        err = mapBuffer(module, hnd);
        if (err == 0) {
            *pHandle = hnd;
        }
    }

    ALOGE_IF(err, "gralloc failed err=%s", strerror(-err));

    return err;
}

/*****************************************************************************/

static int gralloc_alloc(alloc_device_t* dev,
        int w, int h, int format, int usage,
        buffer_handle_t* pHandle, int* pStride)
{
    if (!pHandle || !pStride)
        return -EINVAL;

    size_t size, stride;

    int align = 4;
    int bpp = 0;
#ifndef MTK_DEFAULT_AOSP
    size_t vertical_stride;

    if (format == HAL_PIXEL_FORMAT_YCrCb_420_SP ||
        format == HAL_PIXEL_FORMAT_YV12  ||
        format == HAL_PIXEL_FORMAT_I420  ||
        format == HAL_PIXEL_FORMAT_NV12_BLK ||
        format == HAL_PIXEL_FORMAT_YUV_PRIVATE)
    {
        /// height + 1 for all format. This is tricky workaround solution for MTK ALPS.
        /// MTK Smart Phone SW Video CODEC use NEON VLD Instruction to do acceleration.
        /// This might cause extra data read and might cause NE if readed address locate in the next PAGE.
        switch (format) {
            case HAL_PIXEL_FORMAT_YCrCb_420_SP:
            case HAL_PIXEL_FORMAT_YV12:
            case HAL_PIXEL_FORMAT_I420:         /// MTK I420
                stride = GRALLOC_ALIGN(w, 16);
                vertical_stride = h;
                size = ( vertical_stride + 1 ) * ( stride + GRALLOC_ALIGN(stride / 2, 16) );
                break;
            case HAL_PIXEL_FORMAT_NV12_BLK:     /// MTK NV12 block progressive mode
                stride = GRALLOC_ALIGN(w, 16);
                vertical_stride = GRALLOC_ALIGN(h, 32);
                size = stride * (vertical_stride + 1) * 1.5;
                break;
            case HAL_PIXEL_FORMAT_YUV_PRIVATE:  /// ClearMotion
                stride = GRALLOC_ALIGN(w, 16);
                vertical_stride = GRALLOC_ALIGN(h, 32);
                {
                size_t stride_uv = GRALLOC_ALIGN(stride/2, 16);
                size_t vertical_stride_uv = vertical_stride / 2;
                size = ( stride * ( vertical_stride + 1 ) ) + ( 2 * stride_uv * ( vertical_stride_uv + 1 ) );
                }
                break;
            default:
                return -EINVAL;
        }
        ALOGI( "format: 0x%x stride: %d vertical_stride: %d size: %d", format, stride, vertical_stride, size );
    } else {
        switch (format) {
            case HAL_PIXEL_FORMAT_RGBA_8888:
            case HAL_PIXEL_FORMAT_RGBX_8888:
            case HAL_PIXEL_FORMAT_BGRA_8888:
                bpp = 4;
                break;
            case HAL_PIXEL_FORMAT_RGB_888:
                bpp = 3;
                break;
            case HAL_PIXEL_FORMAT_RGB_565:
            case HAL_PIXEL_FORMAT_RAW_SENSOR:
            //case HAL_PIXEL_FORMAT_RGBA_5551:
            //case HAL_PIXEL_FORMAT_RGBA_4444:
                bpp = 2;
                break;
            default:
                return -EINVAL;
        }
#ifndef EMULATOR_SUPPORT
        size_t bpr = GRALLOC_ALIGN(w * bpp, 64);
#else
        size_t bpr = (w*bpp + (align-1)) & ~(align-1);
#endif
        size = bpr * h;
        stride = bpr / bpp;
        vertical_stride = h;
    }
#else
    switch (format) {
        case HAL_PIXEL_FORMAT_RGBA_8888:
        case HAL_PIXEL_FORMAT_RGBX_8888:
        case HAL_PIXEL_FORMAT_BGRA_8888:
            bpp = 4;
            break;
        case HAL_PIXEL_FORMAT_RGB_888:
            bpp = 3;
            break;
        case HAL_PIXEL_FORMAT_RGB_565:
        case HAL_PIXEL_FORMAT_RAW_SENSOR:
            bpp = 2;
            break;
        default:
            return -EINVAL;
    }
    size_t bpr = (w*bpp + (align-1)) & ~(align-1);
    size = bpr * h;
    stride = bpr / bpp;
#endif

    int err;
    if (usage & GRALLOC_USAGE_HW_FB) {
        err = gralloc_alloc_framebuffer(dev, size, usage, pHandle);
    } else {
        err = gralloc_alloc_buffer(dev, size, usage, pHandle);
    }

#ifndef MTK_DEFAULT_AOSP
    if (err == 0) {
        private_handle_t* hnd =  (private_handle_t*)((void*)*pHandle);
        hnd->width = w;
        hnd->height = h;
        hnd->format = format;
        hnd->stride = stride;
        hnd->vertical_stride = vertical_stride;
        hnd->usage = usage;
    }
#endif

    if (err < 0) {
        return err;
    }

    *pStride = stride;
    return 0;
}

static int gralloc_free(alloc_device_t* dev,
        buffer_handle_t handle)
{
    if (private_handle_t::validate(handle) < 0)
        return -EINVAL;

    private_handle_t const* hnd = reinterpret_cast<private_handle_t const*>(handle);
    if (hnd->flags & private_handle_t::PRIV_FLAGS_FRAMEBUFFER) {
        // free this buffer
        private_module_t* m = reinterpret_cast<private_module_t*>(
                dev->common.module);
        const size_t bufferSize = m->finfo.line_length * m->info.yres;
        int index = (hnd->base - m->framebuffer->base) / bufferSize;
        m->bufferMask &= ~(1<<index);
    } else {
#ifndef MTK_DEFAULT_AOSP
#ifdef MTK_ION_SUPPORT
        private_module_t* m = reinterpret_cast<private_module_t*>(dev->common.module);
        if (0 != munmap((void*)hnd->base, hnd->size))
            ALOGE( "Failed to munmap handle 0x%x", (unsigned int)hnd );
        if (0 != ion_free(m->ion_client, hnd->ion_hnd))
            ALOGE( "Failed to ion_free( ion_client: %d ion_hnd: %p )", m->ion_client, hnd->ion_hnd );

#ifdef MTK_GRALLOC_ION_DBG
        ALOGI("[-]hnd(%p), fd(%d)", hnd, hnd->fd);
#endif

#else // MTK_ION_SUPPORT
        gralloc_module_t* module = reinterpret_cast<gralloc_module_t*>(
                dev->common.module);
        terminateBuffer(module, const_cast<private_handle_t*>(hnd));
#endif // MTK_ION_SUPPORT
#else
        gralloc_module_t* module = reinterpret_cast<gralloc_module_t*>(
                dev->common.module);
        terminateBuffer(module, const_cast<private_handle_t*>(hnd));
#endif // MTK_DEFAULT_AOSP
    }

    close(hnd->fd);
    delete hnd;
    return 0;

}

#ifndef MTK_DEFAULT_AOSP
static int gralloc_mtk_private_isIonBuffer(private_handle_t * hnd)
{
    return (hnd->fd >= 0) ? 1 : 0;
}

static int gralloc_getIonFd(extra_device_t* dev,
        buffer_handle_t handle, int *idx, int *num)
{
    private_handle_t* hnd;

    if (!handle) {
        ALOGE( "NULL handle: %p", handle );
        return -EINVAL;
    }

    hnd = (private_handle_t*)handle;

    if (gralloc_mtk_private_isIonBuffer(hnd)) {
        *idx = 0;
        *num = 1;
    } else {
        *idx = -1;
        *num = -1;
    }

    return 0;
}

static private_module_t * gralloc_mtk_private_getPrivateModule()
{
    static alloc_device_t *allDev = NULL;
    static private_module_t *m = NULL;

    if (m == NULL) {
        hw_module_t * pmodule = NULL;

        if (hw_get_module(GRALLOC_HARDWARE_MODULE_ID, (const hw_module_t**)&pmodule) != 0)
            return 0;

        // TODO: find some place to close it
        gralloc_open(pmodule, &allDev);

        m = reinterpret_cast<private_module_t *>(pmodule);
    }

    return m;
}

#ifdef MTK_ION_SUPPORT

#define GRALLOC_MTK_MAGIC 0xfeeddead

static int gralloc_mtk_private_setIonDebugInfo(private_handle_t* hnd, int value)
{
    private_module_t *m = NULL;
    struct ion_mm_data mm_data;
    int err = 0;

    if ((m = gralloc_mtk_private_getPrivateModule()) == 0) {
        ALOGE( "setIonDebugInfo gralloc_mtk_private_getAllocModule fail" );
        return -1;
    }

    mm_data.mm_cmd = ION_MM_SET_DEBUG_INFO;
    mm_data.config_buffer_param.handle = hnd->ion_hnd;
    strncpy(mm_data.buf_debug_info_param.dbg_name,
            "buf_info", ION_MM_DBG_NAME_LEN);
    mm_data.buf_debug_info_param.value3 = GRALLOC_MTK_MAGIC;
    mm_data.buf_debug_info_param.value4 = value;

    if (ion_custom_ioctl(m->ion_client, ION_CMD_MULTIMEDIA, &mm_data)) {
        ALOGE( "setIonDebugInfo ion_custom_ioctl fail" );
        err = -1;
        goto free_out;
    }

free_out:
    return err;
}

static int gralloc_mtk_private_getIonDebugInfo(private_handle_t* hnd, int *value)
{
    private_module_t *m = NULL;
    struct ion_mm_data mm_data;
    int err = 0;

    if ((m = gralloc_mtk_private_getPrivateModule()) == 0) {
        ALOGE( "getIonDebugInfo gralloc_mtk_private_getAllocModule fail" );
        return -1;
    }

    mm_data.mm_cmd = ION_MM_GET_DEBUG_INFO;
    mm_data.config_buffer_param.handle = hnd->ion_hnd;

    if (ion_custom_ioctl(m->ion_client, ION_CMD_MULTIMEDIA, &mm_data)) {
        ALOGE( "getIonDebugInfo ion_custom_ioctl fail" );
        err = -1;
        goto free_out;
    }

    if (mm_data.buf_debug_info_param.value3 != GRALLOC_MTK_MAGIC) {
        err = -1;
        goto free_out;
    }

    *value = mm_data.buf_debug_info_param.value4;

free_out:
    return err;
}

#else // MTK_ION_SUPPORT

static int gralloc_mtk_private_setIonDebugInfo(private_handle_t* hnd, int value)
{
    return -1;
}
static int gralloc_mtk_private_getIonDebugInfo(private_handle_t* hnd, int *value)
{
    return -1;
}

#endif

static int gralloc_getBufInfo(extra_device_t* dev,
        buffer_handle_t handle, gralloc_buffer_info_t* bufInfo)
{
    private_handle_t* hnd;
    int value = 0;

    if (!handle || !bufInfo) {
        ALOGE( "NULL handle: %p, bufInfo: %p", handle, bufInfo );
        return -EINVAL;
    }

    hnd = (private_handle_t*)handle;
    bufInfo->width = hnd->width;
    bufInfo->height= hnd->height;
    bufInfo->format= hnd->format;
    bufInfo->stride= hnd->stride;
    bufInfo->vertical_stride= hnd->vertical_stride;
    bufInfo->usage = hnd->usage;

    if (gralloc_mtk_private_isIonBuffer(hnd) &&
        gralloc_mtk_private_getIonDebugInfo(hnd, &value) == 0) {
        bufInfo->status = value;
    } else {
        // TODO: handle error
        bufInfo->status = 0;
    }

    if (bufInfo->format == HAL_PIXEL_FORMAT_YUV_PRIVATE) {
        // specify for ClearMotion format
        switch (bufInfo->status & GRALLOC_EXTRA_MASK_CM) {
        case GRALLOC_EXTRA_BIT_CM_YV12:
            bufInfo->stride= GRALLOC_ALIGN(hnd->width, 16);
            bufInfo->vertical_stride= GRALLOC_ALIGN(hnd->height, 16);
            break;
        case GRALLOC_EXTRA_BIT_CM_NV12_BLK:
        case GRALLOC_EXTRA_BIT_CM_NV12_BLK_FCM:
            bufInfo->stride= GRALLOC_ALIGN(hnd->width, 16);
            bufInfo->vertical_stride= GRALLOC_ALIGN(hnd->height, 32);
            break;
        default:
            break;
        }
    }

    return 0;
}

static int gralloc_setBufParameter( extra_device_t* dev,
            buffer_handle_t handle, int mask, int value)
{
    private_handle_t* hnd;
    int old_status = 0;
    int new_status = 0;

    if (!handle) {
        ALOGE( "NULL handle: %p", handle );
        return -EINVAL;
    }

    hnd = (private_handle_t*)handle;

    if (!gralloc_mtk_private_isIonBuffer(hnd)) {
        ALOGE( "Can't setBufParameter buffer 0x%x as it is NOT a ION buffer", (unsigned int)handle );
        return -EINVAL;
    }

    if (gralloc_mtk_private_getIonDebugInfo(hnd, &old_status) != 0) {
        // err, use default value
        old_status = 0;
    }

    new_status = ( old_status & (~mask));
    new_status |= ( value & mask );

    ALOGI("handle:%p mask:0x%x value:0x%x old:0x%x -> new:0x%x", handle, mask, value, old_status, new_status);

    if (new_status != old_status) {
        if (gralloc_mtk_private_setIonDebugInfo(hnd, new_status) != 0) {
            return -EINVAL;
        }
    }

    return 0;
}

int gralloc_getMVA(struct extra_device_t* dev,
           buffer_handle_t handle, void** mvaddr)
{
    private_handle_t* hnd;

    if (!handle) {
        ALOGE( "NULL handle: %p", handle );
        return -EINVAL;
    }

    hnd = (private_handle_t*)handle;

    if (!(!!(hnd->flags & private_handle_t::PRIV_FLAGS_FRAMEBUFFER))) {
        ALOGE( "Can't getMVA buffer 0x%x as it is NOT a framebuffer", (unsigned int)handle );
        return -EINVAL;
    }

    if (hnd->mva == -1) {
        ALOGI( "Can't getMVA buffer 0x%x as it is NOT a framebuffer, please try getIonFd", (unsigned int)handle );
        return -EINVAL;
    }

    *mvaddr = (void *)hnd->mva;

    return 0;
}

static int gralloc_extra_close(struct hw_device_t *dev)
{
    gralloc_extra_t* ctx = reinterpret_cast<gralloc_extra_t*>(dev);
    if (ctx) {
        free(ctx);
    }
    return 0;
}
#endif

/*****************************************************************************/

static int gralloc_close(struct hw_device_t *dev)
{
    gralloc_context_t* ctx = reinterpret_cast<gralloc_context_t*>(dev);
    if (ctx) {
        /* TODO: keep a list of all buffer_handle_t created, and free them
         * all here.
         */
#ifndef MTK_DEFAULT_AOSP
#ifdef MTK_ION_SUPPORT
        private_module_t *m = reinterpret_cast<private_module_t*>(dev);
        if (0 != ion_close(m->ion_client))
            ALOGE( "Failed to close ion_client: %d", m->ion_client );
        m->ion_client = 0;
#endif
#endif
        free(ctx);
    }
    return 0;
}

int gralloc_device_open(const hw_module_t* module, const char* name,
        hw_device_t** device)
{
    int status = -EINVAL;
    if (!strcmp(name, GRALLOC_HARDWARE_GPU0)) {
        gralloc_context_t *dev;
        dev = (gralloc_context_t*)malloc(sizeof(*dev));

        /* initialize our state here */
        memset(dev, 0, sizeof(*dev));

        /* initialize the procs */
        dev->device.common.tag = HARDWARE_DEVICE_TAG;
        dev->device.common.version = 0;
        dev->device.common.module = const_cast<hw_module_t*>(module);
        dev->device.common.close = gralloc_close;

        dev->device.alloc   = gralloc_alloc;
        dev->device.free    = gralloc_free;

        *device = &dev->device.common;
        status = 0;
#ifndef MTK_DEFAULT_AOSP
#ifdef MTK_ION_SUPPORT
        private_module_t *m = reinterpret_cast<private_module_t *>(dev->device.common.module);
        if ( m->ion_client <= 0 ) {
            m->ion_client = ion_open();
            if ( m->ion_client < 0 ) {
                ALOGE( "ion_open failed with %s", strerror(errno) );
                delete dev;
                return -1;
            }
        }
#endif
    } else if (!strcmp(name, GRALLOC_HARDWARE_EXTRA)) {
        gralloc_extra_t *dev;
        dev = (gralloc_extra_t*)malloc(sizeof(*dev));

        /* initialize our state here */
        memset(dev, 0, sizeof(*dev));

        /* initialize the procs */
        dev->device.common.tag = HARDWARE_DEVICE_TAG;
        dev->device.common.version = 0;
        dev->device.common.module  = const_cast<hw_module_t*>(module);
        dev->device.common.close   = gralloc_extra_close;

        dev->device.getIonFd = gralloc_getIonFd;
        dev->device.getBufInfo = gralloc_getBufInfo;
        dev->device.setBufParameter = gralloc_setBufParameter;
        dev->device.getMVA = gralloc_getMVA;
        *device = &dev->device.common;

        status = 0;
#endif // MTK_DEFAULT_AOSP
    } else {
        status = fb_device_open(module, name, device);
    }
    return status;
}
