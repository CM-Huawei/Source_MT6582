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

#include <stdlib.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>

#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>

#include <netinet/in.h>
#include <arpa/inet.h>

#define LOG_TAG "TetherController"
#include <cutils/log.h>
#include <cutils/properties.h>
#include <logwrap/logwrap.h>

#include "TetherController.h"

// M: Hotspot Manager
#include "NetdConstants.h"

TetherController::TetherController() {
    mInterfaces = new InterfaceCollection();
    mDnsForwarders = new NetAddressCollection();
    mDaemonFd = -1;
    mDaemonPid = 0;
}

TetherController::~TetherController() {
    InterfaceCollection::iterator it;

    for (it = mInterfaces->begin(); it != mInterfaces->end(); ++it) {
        free(*it);
    }
    mInterfaces->clear();

    mDnsForwarders->clear();
}

int TetherController::setIpFwdEnabled(bool enable) {

    ALOGD("Setting IP forward enable = %d", enable);

    // In BP tools mode, do not disable IP forwarding
    char bootmode[PROPERTY_VALUE_MAX] = {0};
    property_get("ro.bootmode", bootmode, "unknown");
    if ((enable == false) && (0 == strcmp("bp-tools", bootmode))) {
        return 0;
    }

    int fd = open("/proc/sys/net/ipv4/ip_forward", O_WRONLY);
    if (fd < 0) {
        ALOGE("Failed to open ip_forward (%s)", strerror(errno));
        return -1;
    }

    if (write(fd, (enable ? "1" : "0"), 1) != 1) {
        ALOGE("Failed to write ip_forward (%s)", strerror(errno));
        close(fd);
        return -1;
    }
    close(fd);
    return 0;
}

bool TetherController::getIpFwdEnabled() {
    int fd = open("/proc/sys/net/ipv4/ip_forward", O_RDONLY);

    if (fd < 0) {
        ALOGE("Failed to open ip_forward (%s)", strerror(errno));
        return false;
    }

    char enabled;
    if (read(fd, &enabled, 1) != 1) {
        ALOGE("Failed to read ip_forward (%s)", strerror(errno));
        close(fd);
        return -1;
    }

    close(fd);
    return (enabled  == '1' ? true : false);
}

int TetherController::startTethering(int num_addrs, struct in_addr* addrs) {
    if (mDaemonPid != 0) {
        ALOGW("Tethering already started");
        return 0;
    }

    ALOGD("Starting tethering services");

    pid_t pid;
    int pipefd[2];

    if (pipe(pipefd) < 0) {
        ALOGE("pipe failed (%s)", strerror(errno));
        return -1;
    }

    /*
     * TODO: Create a monitoring thread to handle and restart
     * the daemon if it exits prematurely
     */
    if ((pid = fork()) < 0) {
        ALOGE("fork failed (%s)", strerror(errno));
        close(pipefd[0]);
        close(pipefd[1]);
        return -1;
    }

    if (!pid) {
        close(pipefd[1]);
        if (pipefd[0] != STDIN_FILENO) {
            if (dup2(pipefd[0], STDIN_FILENO) != STDIN_FILENO) {
                ALOGE("dup2 failed (%s)", strerror(errno));
                return -1;
            }
            close(pipefd[0]);
        }

        int num_processed_args = 8 + (num_addrs/2) + 1; // 1 null for termination
        char **args = (char **)malloc(sizeof(char *) * num_processed_args);
        args[num_processed_args - 1] = NULL;
        args[0] = (char *)"/system/bin/dnsmasq";
        args[1] = (char *)"--keep-in-foreground";
        args[2] = (char *)"--no-resolv";
        args[3] = (char *)"--no-poll";
        // TODO: pipe through metered status from ConnService
        args[4] = (char *)"--dhcp-option-force=43,ANDROID_METERED";
        args[5] = (char *)"--dhcp-authoritative";
        args[6] = (char *)"--pid-file";
        args[7] = (char *)"";

        int nextArg = 8;
        for (int addrIndex=0; addrIndex < num_addrs;) {
            char *start = strdup(inet_ntoa(addrs[addrIndex++]));
            char *end = strdup(inet_ntoa(addrs[addrIndex++]));
            asprintf(&(args[nextArg++]),"--dhcp-range=%s,%s,12h", start, end);
        }

        if (execv(args[0], args)) {
            ALOGE("execl failed (%s)", strerror(errno));
        }
        ALOGE("Should never get here!");
        _exit(-1);
    } else {
        close(pipefd[0]);
        mDaemonPid = pid;
        mDaemonFd = pipefd[1];
        applyDnsInterfaces();
        ALOGD("Tethering services running");
    }

    return 0;
}

int TetherController::stopTethering() {

    if (mDaemonPid == 0) {
        ALOGE("Tethering already stopped");
        return 0;
    }

    ALOGD("Stopping tethering services");

    kill(mDaemonPid, SIGTERM);
    waitpid(mDaemonPid, NULL, 0);
    mDaemonPid = 0;
    close(mDaemonFd);
    mDaemonFd = -1;
    ALOGD("Tethering services stopped");
    return 0;
}

bool TetherController::isTetheringStarted() {
    return (mDaemonPid == 0 ? false : true);
}

#define MAX_CMD_SIZE 1024

int TetherController::setDnsForwarders(char **servers, int numServers) {
    int i;
    char daemonCmd[MAX_CMD_SIZE];

    strcpy(daemonCmd, "update_dns");
    int cmdLen = strlen(daemonCmd);

    mDnsForwarders->clear();
    for (i = 0; i < numServers; i++) {
        ALOGD("setDnsForwarders(%d = '%s')", i, servers[i]);

        struct in_addr a;

        if (!inet_aton(servers[i], &a)) {
            ALOGE("Failed to parse DNS server '%s'", servers[i]);
            mDnsForwarders->clear();
            return -1;
        }

        cmdLen += (strlen(servers[i]) + 1);
        if (cmdLen + 1 >= MAX_CMD_SIZE) {
            ALOGD("Too many DNS servers listed");
            break;
        }

        strcat(daemonCmd, ":");
        strcat(daemonCmd, servers[i]);
        mDnsForwarders->push_back(a);
    }

    if (mDaemonFd != -1) {
        ALOGD("Sending update msg to dnsmasq [%s]", daemonCmd);
        if (write(mDaemonFd, daemonCmd, strlen(daemonCmd) +1) < 0) {
            ALOGE("Failed to send update command to dnsmasq (%s)", strerror(errno));
            mDnsForwarders->clear();
            return -1;
        }
    }
    return 0;
}

NetAddressCollection *TetherController::getDnsForwarders() {
    return mDnsForwarders;
}

int TetherController::applyDnsInterfaces() {
    int i;
    char daemonCmd[MAX_CMD_SIZE];

    strcpy(daemonCmd, "update_ifaces");
    int cmdLen = strlen(daemonCmd);
    InterfaceCollection::iterator it;
    bool haveInterfaces = false;

    for (it = mInterfaces->begin(); it != mInterfaces->end(); ++it) {
        cmdLen += (strlen(*it) + 1);
        if (cmdLen + 1 >= MAX_CMD_SIZE) {
            ALOGD("Too many DNS ifaces listed");
            break;
        }

        strcat(daemonCmd, ":");
        strcat(daemonCmd, *it);
        haveInterfaces = true;
    }

    if ((mDaemonFd != -1) && haveInterfaces) {
        ALOGD("Sending update msg to dnsmasq [%s]", daemonCmd);
        if (write(mDaemonFd, daemonCmd, strlen(daemonCmd) +1) < 0) {
            ALOGE("Failed to send update command to dnsmasq (%s)", strerror(errno));
            return -1;
        }
    }
    return 0;
}

int TetherController::tetherInterface(const char *interface) {
    ALOGD("tetherInterface(%s)", interface);
    mInterfaces->push_back(strdup(interface));

    if (applyDnsInterfaces()) {
        InterfaceCollection::iterator it;
        for (it = mInterfaces->begin(); it != mInterfaces->end(); ++it) {
            if (!strcmp(interface, *it)) {
                free(*it);
                mInterfaces->erase(it);
                break;
            }
        }
        return -1;
    } else {
        return 0;
    }
}

int TetherController::untetherInterface(const char *interface) {
    InterfaceCollection::iterator it;

    ALOGD("untetherInterface(%s)", interface);

    for (it = mInterfaces->begin(); it != mInterfaces->end(); ++it) {
        if (!strcmp(interface, *it)) {
            free(*it);
            mInterfaces->erase(it);

            return applyDnsInterfaces();
        }
    }
    errno = ENOENT;
    return -1;
}

InterfaceCollection *TetherController::getTetheredInterfaceList() {
    return mInterfaces;
}
/** M: Hotspot Manager @{ */
int TetherController::setUsbInternet(bool enable) {
	int res = 0;
	if (enable)
		 /*todo: fix it*/
        res = system_nosh("/system/bin/ip route add default via 192.168.0.1 dev rndis0");

	if (res) {
        ALOGE("run netcfg: failed res=%d", res);
    }
	return res;
}
int TetherController::setUsbInternet(bool enable, const char *address) {
	int res = 0;
	char daemonCmd[MAX_CMD_SIZE];
	if (enable && address!= NULL ){
		// "/system/bin/route add default gw 192.168.0.1 dev rndis0"
		strcpy(daemonCmd, "/system/bin/ip route add default via ");
		strcat(daemonCmd, address);
		strcat(daemonCmd, " dev rndis0");
		ALOGD("setUsbInternet(%s)", daemonCmd);
        res = system_nosh(daemonCmd);     /*todo: fix it*/
    }
	if (res) {
        ALOGE("run netcfg: failed res=%d", res);
    }
	return res;
/** @} */
}

