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

#define LOG_TAG "OpenGLRenderer"
#define ATRACE_TAG ATRACE_TAG_HWUI
#include <utils/Trace.h>

#include <GLES2/gl2.h>

#include <SkCanvas.h>

#include <utils/Mutex.h>

#include "Caches.h"
#include "TextureCache.h"
#include "Properties.h"

namespace android {
namespace uirenderer {

///////////////////////////////////////////////////////////////////////////////
// Constructors/destructor
///////////////////////////////////////////////////////////////////////////////

TextureCache::TextureCache():
        mCache(LruCache<SkBitmap*, Texture*>::kUnlimitedCapacity),
        mSize(0), mMaxSize(MB(DEFAULT_TEXTURE_CACHE_SIZE)),
        mFlushRate(DEFAULT_TEXTURE_CACHE_FLUSH_RATE) {
    bool setSizeByProperty = false;
    char property[PROPERTY_VALUE_MAX];
    if (property_get(PROPERTY_TEXTURE_CACHE_SIZE, property, NULL) > 0) {
        INIT_LOGD("  Setting texture cache size to %sMB", property);
        setMaxSize(MB(atof(property)));
        setSizeByProperty = true;
    } else {
        INIT_LOGD("  Using default texture cache size of %.2fMB", DEFAULT_TEXTURE_CACHE_SIZE);
    }

    if (property_get(PROPERTY_TEXTURE_CACHE_FLUSH_RATE, property, NULL) > 0) {
        float flushRate = atof(property);
        INIT_LOGD("  Setting texture cache flush rate to %.2f%%", flushRate * 100.0f);
        setFlushRate(flushRate);
    } else {
        INIT_LOGD("  Using default texture cache flush rate of %.2f%%",
                DEFAULT_TEXTURE_CACHE_FLUSH_RATE * 100.0f);
    }

    /// M: Expand texture cache size for project with large resolution.
    if (!setSizeByProperty) {
        if (DEFAULT_TEXTURE_CACHE_SCREEN_BASED_SIZE > DEFAULT_TEXTURE_CACHE_SIZE) {
            INIT_LOGD("  Setting texture cache size to %.2fMB by screen based size", DEFAULT_TEXTURE_CACHE_SCREEN_BASED_SIZE);
            setMaxSize(MB(DEFAULT_TEXTURE_CACHE_SCREEN_BASED_SIZE));
        }
    }

    init();
}

TextureCache::TextureCache(uint32_t maxByteSize):
        mCache(LruCache<SkBitmap*, Texture*>::kUnlimitedCapacity),
        mSize(0), mMaxSize(maxByteSize) {
    init();
}

TextureCache::~TextureCache() {
    mCache.clear();
}

void TextureCache::init() {
    mCache.setOnEntryRemovedListener(this);

    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &mMaxTextureSize);
    INIT_LOGD("    Maximum texture dimension is %d pixels", mMaxTextureSize);

    mDebugEnabled = readDebugLevel() & kDebugCaches;
}

///////////////////////////////////////////////////////////////////////////////
// Size management
///////////////////////////////////////////////////////////////////////////////

uint32_t TextureCache::getSize() {
    return mSize;
}

uint32_t TextureCache::getMaxSize() {
    return mMaxSize;
}

void TextureCache::setMaxSize(uint32_t maxSize) {
    mMaxSize = maxSize;
    while (mSize > mMaxSize) {
        mCache.removeOldest();
    }
}

void TextureCache::setFlushRate(float flushRate) {
    mFlushRate = fmaxf(0.0f, fminf(1.0f, flushRate));
}

///////////////////////////////////////////////////////////////////////////////
// Callbacks
///////////////////////////////////////////////////////////////////////////////

void TextureCache::operator()(SkBitmap*& bitmap, Texture*& texture) {
    // This will be called already locked
    if (texture) {
        mSize -= texture->bitmapSize;
        TEXTURE_LOGD("TextureCache::callback: name, removed size, mSize = %d, %d, %d",
                texture->id, texture->bitmapSize, mSize);
        if (mDebugEnabled) {
            ALOGD("Texture deleted, size = %d", texture->bitmapSize);
        }
        texture->deleteTexture();
        TT_REMOVE(texture->id, "[TextureCache.cpp] operator -");
        delete texture;
    }
}

///////////////////////////////////////////////////////////////////////////////
// Caching
///////////////////////////////////////////////////////////////////////////////

Texture* TextureCache::get(SkBitmap* bitmap) {
    Texture* texture = mCache.get(bitmap);

    if (!texture) {
        if (bitmap->width() > mMaxTextureSize || bitmap->height() > mMaxTextureSize) {
            ALOGW("Bitmap too large to be uploaded into a texture (%dx%d, max=%dx%d)",
                    bitmap->width(), bitmap->height(), mMaxTextureSize, mMaxTextureSize);
            return NULL;
        }

        const uint32_t size = bitmap->rowBytes() * bitmap->height();
        // Don't even try to cache a bitmap that's bigger than the cache
        if (size < mMaxSize) {
            if (size > MB(DEFAULT_TEXTURE_CACHE_EXPANSION_THRESHOLD) &&
                    mMaxSize == MB(DEFAULT_TEXTURE_CACHE_SIZE)) {
                mMaxSize = MB(DEFAULT_TEXTURE_CACHE_EXPANSION_SIZE);
            } else {
                while (mSize + size > mMaxSize) {
                    mCache.removeOldest();
                }
           }
        }

        texture = new Texture();
        texture->bitmapSize = size;
        generateTexture(bitmap, texture, false);

        if (size < mMaxSize) {
            mSize += size;
            TEXTURE_LOGD("TextureCache::get: create texture(%p): name, size, mSize = %d, %d, %d",
                     bitmap, texture->id, size, mSize);
            if (mDebugEnabled) {
                ALOGD("Texture created, size = %d", size);
            }
            mCache.put(bitmap, texture);
        } else {
            texture->cleanup = true;
        }
    } else if (bitmap->getGenerationID() != texture->generation) {
        generateTexture(bitmap, texture, true);
    }

    return texture;
}

Texture* TextureCache::getTransient(SkBitmap* bitmap) {
    Texture* texture = new Texture();
    texture->bitmapSize = bitmap->rowBytes() * bitmap->height();
    texture->cleanup = true;

    generateTexture(bitmap, texture, false);

    return texture;
}

void TextureCache::remove(SkBitmap* bitmap) {
    mCache.remove(bitmap);
}

void TextureCache::removeDeferred(SkBitmap* bitmap) {
    Mutex::Autolock _l(mLock);
    mGarbage.push(bitmap);
}

void TextureCache::clearGarbage() {
    Mutex::Autolock _l(mLock);
    size_t count = mGarbage.size();
    for (size_t i = 0; i < count; i++) {
        mCache.remove(mGarbage.itemAt(i));
    }
    mGarbage.clear();
}

void TextureCache::clear() {
    mCache.clear();
    TEXTURE_LOGD("TextureCache:clear(), mSize = %d", mSize);
}

void TextureCache::flush() {
    if (mFlushRate >= 1.0f || mCache.size() == 0) return;
    if (mFlushRate <= 0.0f) {
        clear();
        return;
    }

    uint32_t targetSize = uint32_t(mSize * mFlushRate);
    TEXTURE_LOGD("TextureCache::flush: target size: %d", targetSize);

    while (mSize > targetSize) {
        mCache.removeOldest();
    }
}

void TextureCache::generateTexture(SkBitmap* bitmap, Texture* texture, bool regenerate) {
    SkAutoLockPixels alp(*bitmap);

    if (!bitmap->readyToDraw()) {
        ALOGE("Cannot generate texture from bitmap");
        return;
    }

    // We could also enable mipmapping if both bitmap dimensions are powers
    // of 2 but we'd have to deal with size changes. Let's keep this simple
    const bool canMipMap = Extensions::getInstance().hasNPot();

    // If the texture had mipmap enabled but not anymore,
    // force a glTexImage2D to discard the mipmap levels
    const bool resize = !regenerate || bitmap->width() != int(texture->width) ||
            bitmap->height() != int(texture->height) ||
            (regenerate && canMipMap && texture->mipMap && !bitmap->hasHardwareMipMap());

    if (!regenerate) {
        glGenTextures(1, &texture->id);
    }

    texture->generation = bitmap->getGenerationID();
    texture->width = bitmap->width();
    texture->height = bitmap->height();

    Caches::getInstance().bindTexture(texture->id);

    switch (bitmap->getConfig()) {
    case SkBitmap::kA8_Config:
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        uploadToTexture(texture->id, resize, GL_ALPHA, bitmap->rowBytesAsPixels(),
                texture->width, texture->height, GL_UNSIGNED_BYTE, bitmap);
        texture->blend = true;
        break;
    case SkBitmap::kRGB_565_Config:
        glPixelStorei(GL_UNPACK_ALIGNMENT, bitmap->bytesPerPixel());
        uploadToTexture(texture->id, resize, GL_RGB, bitmap->rowBytesAsPixels(),
                texture->width, texture->height, GL_UNSIGNED_SHORT_5_6_5, bitmap);
        texture->blend = false;
        DUMP_TEXTURE(texture->id, bitmap->rowBytesAsPixels(), texture->height, bitmap);
        break;
    case SkBitmap::kARGB_8888_Config:
        glPixelStorei(GL_UNPACK_ALIGNMENT, bitmap->bytesPerPixel());
        uploadToTexture(texture->id, resize, GL_RGBA, bitmap->rowBytesAsPixels(),
                texture->width, texture->height, GL_UNSIGNED_BYTE, bitmap);
        // Do this after calling getPixels() to make sure Skia's deferred
        // decoding happened
        texture->blend = !bitmap->isOpaque();

        DUMP_TEXTURE(texture->id, bitmap->rowBytesAsPixels(), texture->height, bitmap);
        break;
    case SkBitmap::kARGB_4444_Config:
    case SkBitmap::kIndex8_Config:
        glPixelStorei(GL_UNPACK_ALIGNMENT, bitmap->bytesPerPixel());
        uploadLoFiTexture(texture->id, resize, bitmap, texture->width, texture->height);
        texture->blend = !bitmap->isOpaque();
        break;
    default:
        ALOGW("Unsupported bitmap config: %d", bitmap->getConfig());
        break;
    }

    if (canMipMap) {
        texture->mipMap = bitmap->hasHardwareMipMap();
        if (texture->mipMap) {
            glGenerateMipmap(GL_TEXTURE_2D);
        }
    }

    if (!regenerate) {
        texture->setFilter(GL_NEAREST);
        texture->setWrap(GL_CLAMP_TO_EDGE);
    }
}

void TextureCache::uploadLoFiTexture(GLint id, bool resize, SkBitmap* bitmap,
        uint32_t width, uint32_t height) {
    SkBitmap rgbaBitmap;
    rgbaBitmap.setConfig(SkBitmap::kARGB_8888_Config, width, height);
    rgbaBitmap.allocPixels();
    rgbaBitmap.eraseColor(0);
    rgbaBitmap.setIsOpaque(bitmap->isOpaque());

    SkCanvas canvas(rgbaBitmap);
    canvas.drawBitmap(*bitmap, 0.0f, 0.0f, NULL);

    uploadToTexture(id, resize, GL_RGBA, rgbaBitmap.rowBytesAsPixels(), width, height,
            GL_UNSIGNED_BYTE, &rgbaBitmap);
}

void TextureCache::uploadToTexture(GLint id, bool resize, GLenum format, GLsizei stride,
        GLsizei width, GLsizei height, GLenum type, SkBitmap* bitmap) {
    ATRACE_CALL();

    // TODO: With OpenGL ES 2.0 we need to copy the bitmap in a temporary buffer
    //       if the stride doesn't match the width
    const bool useStride = stride != width && Extensions::getInstance().hasUnpackRowLength();
    TEXTURE_LOGD("uploadToTexture id %d, resize %d, format 0x%x, stride %d, width %d, height %d, type 0x%x, ext %d, useStride %d, bitmap %p",
        id, resize, format, stride, width, height, type, Extensions::getInstance().hasUnpackRowLength(), useStride, bitmap);

    const GLvoid *data = bitmap->getPixels();
    uint8_t *rawData = NULL;

    if (useStride) {
        glPixelStorei(GL_UNPACK_ROW_LENGTH, stride);
    } else if (stride != width) {
        /// M: [ALPS01250989] With OpenGL ES 2.0 we need to copy the bitmap
        //  in a temporary buffer if the stride doesn't match the width
        int bytesPerPixel = 0;
        switch (type) {
            case GL_UNSIGNED_BYTE:
                switch (format) {
                    case GL_RGBA:
                        bytesPerPixel = 4;
                        break;
                    case GL_ALPHA:
                        bytesPerPixel = 1;
                        break;
                    default:
                        TEXTURE_LOGD("Format Error!! type:0x%x, format:0x%x", type, format);
                        break;
                }
                break;
            case GL_UNSIGNED_SHORT_5_6_5:   // GL_RGB
                bytesPerPixel = 2;
                break;
            default:
                TEXTURE_LOGD("Tpye Error!! type:0x%x, format:0x%x", type, format);
                break;
       }
       if (bytesPerPixel != 0) {
           int size = width * height * bytesPerPixel;
           rawData = new uint8_t[size];
           TEXTURE_LOGD("copyPixelsTo bytesPerPixel %d, dst %p, dstSize %d, dstRowBytes %d ", bytesPerPixel, rawData, size, width * bytesPerPixel);
           ATRACE_BEGIN("copyPixelsTo");
           if(bitmap != NULL && bitmap->copyPixelsTo(rawData, size, width * bytesPerPixel, true)) {
               TEXTURE_LOGD("copyPixelsTo good!");
               data = rawData;
           } else {
               TEXTURE_LOGD("copyPixelsTo fail!");
           }
           ATRACE_END();
       }
    }

    if (resize) {
        glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, format, type, data);
        TT_ADD(id, width, height, format, type, String8("texture"), "[TextureCache.cpp] uploadToTexture +");
    } else {
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, format, type, data);
    }

    if (rawData != NULL) {
        delete[] rawData;
        rawData = NULL;
    }

    if (useStride) {
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
    }
}

}; // namespace uirenderer
}; // namespace android
