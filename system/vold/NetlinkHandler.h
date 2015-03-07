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

#ifndef _NETLINKHANDLER_H
#define _NETLINKHANDLER_H

#include <sysutils/NetlinkListener.h>
#include <sysutils/NetlinkEvent.h>

class NetlinkHandler: public NetlinkListener {

public:
    NetlinkHandler(int listenerSocket);
    virtual ~NetlinkHandler();

    int start(void);
    int stop(void);

protected:
    virtual void onEvent(NetlinkEvent *evt);
#ifdef MTK_MULTI_PARTITION_MOUNT_ONLY_SUPPORT
private:
    bool getDescription(const char *path, char *description);
    int getPartIdxFromPath(const char *path);
    bool isPathUsbOtg(const char *path);
    bool isSDcard0Mounted();
    void handleUsbOtgDiskChanged(NetlinkEvent *evt);
    int UnmountPath(const char *path, bool force);
#endif
};
#endif
