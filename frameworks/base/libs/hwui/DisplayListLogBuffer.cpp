/*
 * Copyright (C) 2011 The Android Open Source Project
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

#define LOG_TAG "DisplayListLogBuffer"
#include "DisplayListLogBuffer.h"

/// M: performance index enhancements
#include <cutils/properties.h>

// BUFFER_SIZE size must be one more than a multiple of COMMAND_SIZE to ensure
// that mStart always points at the next command, not just the next item
#define NUM_COMMANDS 50
#define BUFFER_SIZE ((NUM_COMMANDS) + 1)

/**
 * DisplayListLogBuffer is a utility class which logs the most recent display
 * list operations in a circular buffer. The log is process-wide, because we
 * only care about the most recent operations, not the operations on a per-window
 * basis for a given activity. The purpose of the log is to provide more debugging
 * information in a bug report, by telling us not just where a process hung (which
 * generally is just reported as a stack trace at the Java level) or crashed, but
 * also what happened immediately before that hang or crash. This may help track down
 * problems in the native rendering code or driver interaction related to the display
 * list operations that led up to the hang or crash.
 *
 * The log is implemented as a circular buffer for both space and performance
 * reasons - we only care about the last several operations to give us context
 * leading up to the problem, and we don't want to constantly copy data around or do
 * additional mallocs to keep the most recent operations logged. Only numbers are
 * logged to make the operation fast. If and when the log is output, we process this
 * data into meaningful strings.
 *
 * There is an assumption about the format of the command (currently 2 ints: the
 * opcode and the nesting level). If the type of information logged changes (for example,
 * we may want to save a timestamp), then the size of the buffer and the way the
 * information is recorded in writeCommand() should change to suit.
 */

namespace android {

#ifdef USE_OPENGL_RENDERER
using namespace uirenderer;
ANDROID_SINGLETON_STATIC_INSTANCE(DisplayListLogBuffer);
#endif

namespace uirenderer {


/**
 * M: performance index enhancements, dump execution time of each operation every frame
 * "1" and "0". The default value is "0".
 */
#define PROPERTY_DEBUG_COMMANDS_DURATION "debug.hwui.log.duration"

DisplayListLogBuffer::DisplayListLogBuffer() {
    mBufferFirst = (OpLog*) malloc(BUFFER_SIZE * sizeof(OpLog));
    mStart = mBufferFirst;
    mBufferLast = mBufferFirst + BUFFER_SIZE - 1;
    mEnd = mStart;
}

DisplayListLogBuffer::~DisplayListLogBuffer() {
    free(mBufferFirst);
}

/**
 * Called from DisplayListRenderer to output the current buffer into the
 * specified FILE. This only happens in a dumpsys/bugreport operation.
 */
void DisplayListLogBuffer::outputCommands(FILE *file)
{
    OpLog* tmpBufferPtr = mStart;
    while (true) {
        if (tmpBufferPtr == mEnd) {
            break;
        }

        fprintf(file, "%*s%s\n", 2 * tmpBufferPtr->level, "", tmpBufferPtr->label);

        OpLog* nextOp = tmpBufferPtr++;
        if (tmpBufferPtr > mBufferLast) {
            tmpBufferPtr = mBufferFirst;
        }
    }

    outputCommandsInternal(file);
}

/**
 * Store the given level and label in the buffer and increment/wrap the mEnd
 * and mStart values as appropriate. Label should point to static memory.
 */
void DisplayListLogBuffer::writeCommand(int level, const char* label) {
    mEnd->level = level;
    mEnd->label = label;

    if (mEnd == mBufferLast) {
        mEnd = mBufferFirst;
    } else {
        mEnd++;
    }
    if (mEnd == mStart) {
        mStart++;
        if (mStart > mBufferLast) {
            mStart = mBufferFirst;
        }
    }
}


/// M: performance index enhancements, recording executing time of every operation @{

int DisplayListLogBuffer::OpEntry::compare(const OpEntry& lhs, const OpEntry& rhs) {
   if (lhs.mName == rhs.mName) {
       return 0;
   } else {
       int diff = lhs.mTotalDuration - rhs.mTotalDuration;
       return  lhs.mName < rhs.mName ? -1 : 1;
   }
}

void DisplayListLogBuffer::writeCommand(int level, const char* label, int duration) {
    OpEntry entry(String8(label), 1, duration, duration);

    ssize_t index = mOpBuffer.indexOf(entry);
    if (index >= 0) {
        OpEntry& item = mOpBuffer.editItemAt(index);
        item.mCount++;
        item.mMaxDuration = duration > item.mMaxDuration ? duration : item.mMaxDuration;
        item.mTotalDuration += duration;
    } else {
        mOpBuffer.add(entry);
    }

    index = mOpTmpBuffer.indexOf(entry);
    if (index >= 0) {
        OpEntry& item = mOpTmpBuffer.editItemAt(index);
        item.mCount++;
        item.mMaxDuration = duration > item.mMaxDuration ? duration : item.mMaxDuration;
        item.mTotalDuration += duration;
    } else {
        mOpTmpBuffer.add(entry);
    }

    writeCommand(level, label);
}


void DisplayListLogBuffer::outputCommandsInternal(FILE *file) {
    if (mIsLogCommands || file) {
        SortedList<OpEntry> &ops = file == NULL ? mOpTmpBuffer : mOpBuffer;

        size_t count = ops.size();
        if (count == 0) return;

        if (file)
            fprintf(file, "\n%-18s  %10s  %10s  %10s  %10s\n", "(ms)", "total", "count", "average", "max");
        else
            ALOGD("%-18s  %10s  %10s  %10s  %10s", "(ms)", "total", "count", "average", "max");

        Vector<OpEntry> list;
        list.add(ops.itemAt(0));

        for (size_t i = 1; i < count; i++) {
            OpEntry entry = ops.itemAt(i);
            size_t index = list.size();
            size_t size = list.size();
            for (size_t j = 0; j < size; j++) {
                OpEntry e = list.itemAt(j);
                if (entry.mTotalDuration > e.mTotalDuration) {
                    index = j;
                    break;
                }
           }
           list.insertAt(entry, index);
        }

        for (size_t i = 0; i < count; i++) {
            OpEntry entry = list.itemAt(i);
            const String8& current = entry.mName;
            float total = entry.mTotalDuration / 1000.0f;
            int count = entry.mCount;
            float max = entry.mMaxDuration / 1000.0f;
            float average = total / count;
            if (file)
                fprintf(file, "%-18s  %10.2f  %10d  %10.2f  %10.2f\n", current.string(), total, count, average, max);
            else
                ALOGD("%-18s  %10.2f  %10d  %10.2f  %10.2f", current.string(), total, count, average, max);

        }

    }
}

void DisplayListLogBuffer::preFlush() {
    char value[PROPERTY_VALUE_MAX];
    property_get(PROPERTY_DEBUG_COMMANDS_DURATION, value, "");
    mIsLogCommands = (strcmp(value, "1") == 0) ? true : false;
    if(mIsLogCommands) {
        mOpTmpBuffer.clear();
    }
}

void DisplayListLogBuffer::postFlush() {
    outputCommandsInternal();
}

/// @}

}; // namespace uirenderer
}; // namespace android
