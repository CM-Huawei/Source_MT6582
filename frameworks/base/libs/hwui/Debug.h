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

#ifndef ANDROID_HWUI_DEBUG_H
#define ANDROID_HWUI_DEBUG_H

#if !defined(MTK_ENG_BUILD)  //Keep Usr build

// Turn on to check for OpenGL errors on each frame
#define DEBUG_OPENGL 1

// Turn on to display informations about the GPU
#define DEBUG_EXTENSIONS 0

// Turn on to enable initialization information
#define DEBUG_INIT 0

// Turn on to enable memory usage summary on each frame
#define DEBUG_MEMORY_USAGE 0

// Turn on to enable debugging of cache flushes
#define DEBUG_CACHE_FLUSH 1

// Turn on to enable layers debugging when rendered as regions
#define DEBUG_LAYERS_AS_REGIONS 0

// Turn on to enable debugging when the clip is not a rect
#define DEBUG_CLIP_REGIONS 0

// Turn on to display debug info about vertex/fragment shaders
#define DEBUG_PROGRAMS 0

// Turn on to display info about layers
#define DEBUG_LAYERS 0

// Turn on to display info about render buffers
#define DEBUG_RENDER_BUFFERS 0

// Turn on to make stencil operations easier to debug
// (writes 255 instead of 1 in the buffer, forces 8 bit stencil)
#define DEBUG_STENCIL 0

// Turn on to display debug info about 9patch objects
#define DEBUG_PATCHES 0
// Turn on to display vertex and tex coords data about 9patch objects
// This flag requires DEBUG_PATCHES to be turned on
#define DEBUG_PATCHES_VERTICES 0
// Turn on to display vertex and tex coords data used by empty quads
// in 9patch objects
// This flag requires DEBUG_PATCHES to be turned on
#define DEBUG_PATCHES_EMPTY_VERTICES 0

// Turn on to display debug info about shapes
#define DEBUG_PATHS 0

// Turn on to display debug info about textures
#define DEBUG_TEXTURES 0

// Turn on to display debug info about the layer renderer
#define DEBUG_LAYER_RENDERER 0

// Turn on to enable additional debugging in the font renderers
#define DEBUG_FONT_RENDERER 0

// Turn on to log draw operation batching and deferral information
#define DEBUG_DEFER 0

// Turn on to dump display list state
#define DEBUG_DISPLAY_LIST 0

// Turn on to insert an event marker for each display list op
#define DEBUG_DISPLAY_LIST_OPS_AS_EVENTS 0

// Turn on to highlight drawing batches and merged batches with different colors
#define DEBUG_MERGE_BEHAVIOR 0

#else  //Eng Build All define options as 1

// Turn on to check for OpenGL errors on each frame
#define DEBUG_OPENGL 1
// Turn on to display informations about the GPU
#define DEBUG_EXTENSIONS 1
// Turn on to enable initialization information
#define DEBUG_INIT 1
// Turn on to enable memory usage summary on each frame
#define DEBUG_MEMORY_USAGE 1
// Turn on to enable debugging of cache flushes
#define DEBUG_CACHE_FLUSH 1
// Turn on to enable layers debugging when rendered as regions
#define DEBUG_LAYERS_AS_REGIONS 1
// Turn on to enable debugging when the clip is not a rect
#define DEBUG_CLIP_REGIONS 1
// Turn on to display debug info about vertex/fragment shaders
#define DEBUG_PROGRAMS 1
// Turn on to display info about layers
#define DEBUG_LAYERS 1
// Turn on to display info about render buffers
#define DEBUG_RENDER_BUFFERS 1
// Turn on to make stencil operations easier to debug
// (writes 255 instead of 1 in the buffer, forces 8 bit stencil)
#define DEBUG_STENCIL 1
// Turn on to display debug info about 9patch objects
#define DEBUG_PATCHES 1
// Turn on to display vertex and tex coords data about 9patch objects
// This flag requires DEBUG_PATCHES to be turned on
#define DEBUG_PATCHES_VERTICES 1
// Turn on to display vertex and tex coords data used by empty quads
// in 9patch objects
// This flag requires DEBUG_PATCHES to be turned on
#define DEBUG_PATCHES_EMPTY_VERTICES 1
// Turn on to display debug info about shapes
#define DEBUG_PATHS 1
// Turn on to display debug info about textures
#define DEBUG_TEXTURES 1
// Turn on to display debug info about the layer renderer
#define DEBUG_LAYER_RENDERER 1
// Turn on to enable additional debugging in the font renderers
#define DEBUG_FONT_RENDERER 1
// Turn on to log draw operation batching and deferral information
#define DEBUG_DEFER 1
// Turn on to dump display list state
#define DEBUG_DISPLAY_LIST 1
// Turn on to insert an event marker for each display list op
#define DEBUG_DISPLAY_LIST_OPS_AS_EVENTS 1
// Turn on to highlight drawing batches and merged batches with different colors
#define DEBUG_MERGE_BEHAVIOR 1

#endif

extern int g_HWUI_debug_opengl;
extern int g_HWUI_debug_extensions;
extern int g_HWUI_debug_init;
extern int g_HWUI_debug_memory_usage;
extern int g_HWUI_debug_cache_flush;
extern int g_HWUI_debug_layers_as_regions;
extern int g_HWUI_debug_clip_regions;
extern int g_HWUI_debug_programs;
extern int g_HWUI_debug_layers;
extern int g_HWUI_debug_render_buffers;
extern int g_HWUI_debug_stencil;
extern int g_HWUI_debug_patches;
extern int g_HWUI_debug_patches_vertices;
extern int g_HWUI_debug_patches_empty_vertices;
extern int g_HWUI_debug_paths;
extern int g_HWUI_debug_textures;
extern int g_HWUI_debug_layer_renderer;
extern int g_HWUI_debug_font_renderer;
extern int g_HWUI_debug_defer;
extern int g_HWUI_debug_display_list;
extern int g_HWUI_debug_display_ops_as_events;
extern int g_HWUI_debug_merge_behavior;

//MTK debug dump functions
extern int g_HWUI_debug_texture_tracker;
extern int g_HWUI_debug_duration;
extern int g_HWUI_debug_dumpDisplayList;
extern int g_HWUI_debug_dumpDraw;
extern int g_HWUI_debug_dumpTexture;
extern int g_HWUI_debug_dumpAlphaTexture;
extern int g_HWUI_debug_layer;
extern int g_HWUI_debug_enhancement;

//MTK sync with egl trace
extern int g_HWUI_debug_egl_trace;

#if defined(MTK_DEBUG_RENDERER)

#include "MTKDebug.h"

#define DUMP_DISPLAY_LIST(...) \
{                          \
    if (g_HWUI_debug_dumpDisplayList) \
        dumpDisplayList(__VA_ARGS__); \
}

#define DUMP_DRAW(...) \
{                          \
    if (g_HWUI_debug_dumpDraw) \
        dumpDraw(__VA_ARGS__); \
}

#define DUMP_TEXTURE(...) \
{                          \
    if (g_HWUI_debug_dumpTexture) \
        dumpTexture(__VA_ARGS__); \
}

#define DUMP_ALPHA_TEXTURE(...) \
{                          \
    if (g_HWUI_debug_dumpAlphaTexture) \
        dumpAlphaTexture(__VA_ARGS__); \
}

#define DUMP_LAYER(...) \
{                          \
    if (g_HWUI_debug_layer) \
        dumpLayer(__VA_ARGS__); \
}

#define TT_DUMP_MEMORY_USAGE(...) TextureTracker::getInstance().dumpMemoryUsage(__VA_ARGS__);
#define TT_ADD(...) TextureTracker::getInstance().add(__VA_ARGS__);
#define TT_UPDATE(...) TextureTracker::getInstance().update(__VA_ARGS__);
#define TT_REMOVE(...) TextureTracker::getInstance().remove(__VA_ARGS__);
#define TT_START_MARK(...) TextureTracker::getInstance().startMark(__VA_ARGS__);
#define TT_END_MARK(...) TextureTracker::getInstance().endMark(__VA_ARGS__);

#else

#define DUMP_DISPLAY_LIST(...)
#define DUMP_DRAW(...)
#define DUMP_TEXTURE(...)
#define DUMP_ALPHA_TEXTURE(...)
#define DUMP_LAYER(...)

#define TT_DUMP_MEMORY_USAGE(...)
#define TT_ADD(...)
#define TT_UPDATE(...)
#define TT_REMOVE(...)
#define TT_START_MARK(...)
#define TT_END_MARK(...)

#endif

#define MTK_DEBUG_ERROR_CHECK 1

#if DEBUG_INIT
    #define INIT_LOGD(...) \
    {                          \
        if (g_HWUI_debug_init) \
            ALOGD(__VA_ARGS__); \
    }
#else
    #define INIT_LOGD(...)
#endif

#endif // ANDROID_HWUI_DEBUG_H
