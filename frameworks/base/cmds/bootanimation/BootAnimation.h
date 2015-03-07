/*
 * Copyright (C) 2007 The Android Open Source Project
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

#ifndef ANDROID_BOOTANIMATION_H
#define ANDROID_BOOTANIMATION_H

#include <stdint.h>
#include <sys/types.h>

#include <androidfw/AssetManager.h>
#include <utils/threads.h>

#include <EGL/egl.h>
#include <GLES/gl.h>
#include <GLES/glext.h>
#include <EGL/eglext.h>
#include <semaphore.h>
#include <cutils/xlog.h>
#include <core/SkBitmap.h>
#include <core/SkCanvas.h>
#include <ui/GraphicBuffer.h>

/** MTK multi-thread decode and direct texture */
#define BOOTANIMATION_IMPROVE

class SkBitmap;

namespace android {

/** Jelly Bean Added */ 
class Surface;
class SurfaceComposerClient;
class SurfaceControl;

// ---------------------------------------------------------------------------

class BootAnimation : public Thread, public IBinder::DeathRecipient
{
public:
                BootAnimation();
				BootAnimation(bool bSetBootOrShutDown,bool bSetPlayMP3,bool bSetRotated);
    virtual     ~BootAnimation();

    sp<SurfaceComposerClient> session() const;

private:
    virtual bool        threadLoop();
    virtual status_t    initSurface(int bgColor);
    virtual status_t    readyToRun();
    virtual void        onFirstRef();
    virtual void        binderDied(const wp<IBinder>& who);

    struct Texture {
        GLint   w;
        GLint   h;
        GLuint  name;
    };

    struct Animation {
        struct Frame {
            String8 name;
            FileMap* map;
            mutable GLuint tid;
            bool operator < (const Frame& rhs) const {
                return name < rhs.name;
            }
        };
        struct Part {
            int count;
            int pause;
            String8 path;
            SortedVector<Frame> frames;
            bool playUntilComplete;
        };
        int fps;
        int width;
        int height;
        Vector<Part> parts;
    };
#ifdef BOOTANIMATION_IMPROVE
#define BUFFER_SIZE 6
#define THREAD_SIZE 4
    struct BitmapPack {
            bool ready;
            String8 name;
            SkBitmap bitmap;
            FileMap* filemap;
            bool operator < (const BitmapPack& rhs) const {
                return name < rhs.name;
            }
        };
    static void * decode_func(void *arg);
    void startDecodeThread(unsigned int number);
    SortedVector<BitmapPack> glBitmapPacks;
    sem_t mSemBuffer;
    int mBitmapIndex;
    Mutex mLock;
    void showBitmap(unsigned int index, bool cleanCache);
    void initDirectTexture(int width, int height);
    void deinitDirectTexture();
    void* beginDraw();
    void endDraw();
    sp<GraphicBuffer> mGraphicBuffer;
    int mTexWidth, mTexHeight;
    int mViewWidth, mViewHeight;
    GLuint mFboTex;
    EGLImageKHR pEGLImage;
#endif

    status_t initTexture(Texture* texture, AssetManager& asset, const char* name);
    status_t initTexture(void* buffer, size_t len);
    bool android();
    bool movie();

    void checkExit();
    void getResourceFile(int index, void * path);
    void updateResourceFilePath();
    sp<SurfaceComposerClient>       mSession;
    AssetManager mAssets;
    Texture     mAndroid[2];
    int         mWidth;
    int         mHeight;
    unsigned char *glTextureBackground;
    int mBackgroundLength;
    EGLDisplay  mDisplay;
    EGLDisplay  mContext;
    EGLDisplay  mSurface;
    sp<SurfaceControl> mFlingerSurfaceControl;
    sp<Surface> mFlingerSurface;
    bool        mAndroidAnimation;
	bool bBootOrShutDown;
	bool bShutRotate;
	bool bPlayMP3;
    ZipFileRO   mZip;
};

// ---------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_BOOTANIMATION_H
