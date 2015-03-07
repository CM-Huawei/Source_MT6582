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
#include <sys/socket.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <string.h>
#include <cutils/properties.h>
#include <netutils/ifc.h>
#include <sys/wait.h>

#define LOG_TAG "IPv6TetherController"
#include <cutils/log.h>
#include <logwrap/logwrap.h>

#include "IPv6TetherController.h"
#include "NetdConstants.h"

//static char IP_PATH[] = "/system/bin/ip";

static const int IPv6_TETHER_BASE = 50;

IPv6TetherController::IPv6TetherController() {
	int i = 0;
    mPid = 0;
    while(i < TETHER_IFACE_NUM){
		mPid_dhcp6s[i] = 0;
		i++;
	}
    setDefaults();
}

IPv6TetherController::~IPv6TetherController() {
}

int IPv6TetherController::runCmd(const char *path, const char *cmd) {
    char *buffer;
    size_t len = strnlen(cmd, 255);
    int res;

    if (len == 255) {
        ALOGE("command too long");
        errno = E2BIG;
        return -1;
    }

    asprintf(&buffer, "%s %s", path, cmd);
    res = system_nosh(buffer);   /*todo: verify it*/
    free(buffer);
    return res;
}

int IPv6TetherController::setDefaults() {

    runCmd(IP_PATH, "-6 rule flush");
    runCmd(IP_PATH, "-6 rule add from all lookup default prio 32767");
    runCmd(IP_PATH, "-6 rule add from all lookup main prio 32766");
    runCmd(IP_PATH, "-6 route flush cache");

    mCount = 0;

    return 0;
}

bool IPv6TetherController::checkInterface(const char *iface) {
    if (strlen(iface) > IFNAMSIZ) return false;
    return true;
}

// each interface has two tables, one for subnet route and other for source route
int IPv6TetherController::findTableNumber(const char *iface){
    if(iface == NULL)
		return -1;
    if(!strncmp(iface, "ap", 2)){

		return IPv6_TETHER_BASE;
    }
    if(!strncmp(iface, "rndis", 5)){

		return IPv6_TETHER_BASE + 2;
	}
    if(!strncmp(iface, "bt", 2)){

		return IPv6_TETHER_BASE + 4;
    }
    return -1;
}

int IPv6TetherController::setTetherFlag(const char *iface, bool set){
    if(iface == NULL)
		return -1;
    if(!strncmp(iface, "ap", 2)){
        if(set)
			mCount |= WIFI_FLAG;
		else
			mCount &= (~WIFI_FLAG);
		return 0;
    }
    if(!strncmp(iface, "rndis", 5)){
        if(set)
			mCount |= USB_FLAG;
		else
			mCount &= (~USB_FLAG);
		return 0;
	}
    if(!strncmp(iface, "bt", 2)){
        if(set)
			mCount |= BT_FLAG;
		else
			mCount &= (~BT_FLAG);
		return 0;
    }
    return -1;
}


int IPv6TetherController::setAllMulticast(bool set, const char *intIface, const char *extIface){
    if(intIface == NULL || extIface == NULL)
		return -1;

    int ret = 0;
    if(set){
        ret = ifc_enable_allmc(extIface);
    if (ret != 0)
        return ret;
    ret = ifc_enable_allmc(intIface);
    if (ret != 0)
        return ret;
    } else{
        ifc_disable_allmc(intIface);
    if(mCount == 0)
        ifc_disable_allmc(extIface);
    }
    return ret;
}

int IPv6TetherController::accept_RA_defrtr(bool set, const char *intIface){
    char proc[64];
	snprintf(proc, sizeof(proc), "/proc/sys/net/ipv6/conf/%s/accept_ra_defrtr", intIface);

	int fd = open(proc, O_WRONLY);
    if (fd < 0) {
        ALOGE("Failed to open ipv6 accept_ra_defrtr (%s)", strerror(errno));
        return -1;
    }

    if (write(fd, (set ? "1" : "0"), 1) != 1) {
        ALOGE("Failed to write ipv6 accept_ra_defrtr (%s)", strerror(errno));
        close(fd);
        return -1;
    }
    close(fd);
	return 0;
}

int IPv6TetherController::accept_RA(bool set, const char *intIface){
    char proc[64];
	snprintf(proc, sizeof(proc), "/proc/sys/net/ipv6/conf/%s/accept_ra", intIface);

	int fd = open(proc, O_WRONLY);
    if (fd < 0) {
        ALOGE("Failed to open ipv6 accept_ra (%s)", strerror(errno));
        return -1;
    }

    if (write(fd, (set ? "2" : "0"), 1) != 1) {
        ALOGE("Failed to write ipv6 accept_ra (%s)", strerror(errno));
        close(fd);
        return -1;
    }
    close(fd);
	return 0;
}

int IPv6TetherController::enableProxy_ndp(bool set){
    char proc[64];
	snprintf(proc, sizeof(proc), "/proc/sys/net/ipv6/conf/all/proxy_ndp");

	int fd = open(proc, O_WRONLY);
    if (fd < 0) {
        ALOGE("Failed to open ipv6 proxy_ndp (%s)", strerror(errno));
        return -1;
    }

    if (write(fd, (set ? "1" : "0"), 1) != 1) {
        ALOGE("Failed to write ipv6 proxy_ndp (%s)", strerror(errno));
        close(fd);
        return -1;
    }
    close(fd);
	return 0;
}
int IPv6TetherController::findInfaceNumber(const char *iface){
	const char* iface_list[] = {
		"ap0",
		"rndis0",
		"btn0",
		0
	};
	int index = 0;
	if (iface == NULL)
		return -1;
	while(iface_list[index] != 0){
		if (0 == strcmp(iface, iface_list[index]))
			return index;
		else
			index++;
	}
	return -1;
}

int IPv6TetherController::startDhcp6s(const char *intIface) {
    pid_t pid;
    int i;

	i = findInfaceNumber(intIface);
	if (i == -1 || i >= TETHER_IFACE_NUM){
        ALOGE("findInfaceNumber for %s fail, i = %d", intIface, i);
        return -1;
	}
    if (mPid_dhcp6s[i] != 0) {
        ALOGE("dhcp6s already started, pid = %d", mPid_dhcp6s[i]);
        errno = EBUSY;
        return -1;
    }

   if ((pid = fork()) < 0) {
        ALOGE("fork failed (%s)", strerror(errno));
        return -1;
    }

    if (!pid) {
        if (execl("/system/bin/dhcp6s", "/system/bin/dhcp6s",
                  "-Df", intIface, (char *) NULL)) {
            ALOGE("execl failed (%s)", strerror(errno));
        }
        ALOGE("Should never get here!");
        return 0;
    } else {
        mPid_dhcp6s[i] = pid;
        ALOGD("dhcp6s pid = %d ", mPid_dhcp6s[i]);
    }
    return 0;

}

int IPv6TetherController::stopDhcp6s(const char *intIface) {
	int i;

	i = findInfaceNumber(intIface);
	if (i == -1 || i >= TETHER_IFACE_NUM){
        ALOGE("findInfaceNumber for %s fail, i = %d", intIface, i);
        return -1;
	}
    if (mPid_dhcp6s[i] == 0) {
        ALOGE("dhcp6s already stopped");
        return 0;
    }

    ALOGD("Stopping dhcp6s services");
    kill(mPid_dhcp6s[i], SIGTERM);
    waitpid(mPid_dhcp6s[i], NULL, 0);
    mPid_dhcp6s[i] = 0;
    ALOGD("dhcp6s services stopped");
    return 0;
}

int IPv6TetherController::startRadvd() {
    pid_t pid;

    if (mPid) {
        ALOGE("radvd already started");
        errno = EBUSY;
        return -1;
    }

   if ((pid = fork()) < 0) {
        ALOGE("fork failed (%s)", strerror(errno));
        return -1;
    }

    if (!pid) {
        if (execl("/system/bin/radvd", "/system/bin/radvd",
                  "-n", "-d 5", (char *) NULL)) {
            ALOGE("execl failed (%s)", strerror(errno));
        }
        ALOGE("Should never get here!");
        return 0;
    } else {
        mPid = pid;
        ALOGD("radvd pid = %d ", mPid);
    }
    return 0;

}

int IPv6TetherController::stopRadvd() {
    if (mPid == 0) {
        ALOGE("radvd already stopped");
        return 0;
    }

    ALOGD("Stopping radvd services");
    kill(mPid, SIGTERM);
    waitpid(mPid, NULL, 0);
    mPid = 0;
    ALOGD("radvd services stopped");
    return 0;
}

int IPv6TetherController::updateRadvdConf() {
    if (mPid == 0) {
        ALOGE("radvd already stopped");
        return 0;
    }

    ALOGD("update radvd config");
    kill(mPid, SIGHUP);
    ALOGD("radvd config is updated");
    return 0;
}

//  0         1       2       3   
// IPv6tether setroute intface extface
int IPv6TetherController::setIPv6SecondaryRoute(const int argc, char **argv) {
    char cmd[255];
    int ret = 0;
    const char *intIface = argv[2];
    const char *extIface = argv[3];
    int tableNumber;
    char prefix_prop_name[PROPERTY_KEY_MAX];
    char prefix_prop_value[PROPERTY_VALUE_MAX] = {'\0'};
    char plen_prop_name[PROPERTY_KEY_MAX];
    char plen_prop_value[PROPERTY_VALUE_MAX] = {'\0'};
    char subnet[64] = {'\0'};
  
    if (!checkInterface(intIface) || !checkInterface(extIface)) {
        ALOGE("Invalid interface specified");
        errno = ENODEV;
        return -1;
    }

    if (argc < 4) {
        ALOGE("Missing Argument");
        errno = EINVAL;
        return -1;
    }
    
#ifdef PD_MODE 
    snprintf(prefix_prop_name, sizeof(prefix_prop_name), "net.pd.%s.prefix", extIface);
#else
    snprintf(prefix_prop_name, sizeof(prefix_prop_name), "net.ipv6.%s.prefix", extIface);
#endif
    if (!property_get(prefix_prop_name, prefix_prop_value, NULL)) {
        ALOGE("get prefix failed, please check your IPv6 config!");
        return -1; 
    }
    
#ifdef PD_MODE  
    snprintf(plen_prop_name, sizeof(plen_prop_name), "net.pd.%s.plen", extIface);
#else
    snprintf(plen_prop_name, sizeof(plen_prop_name), "net.ipv6.%s.plen", extIface);
#endif
    if (!property_get(plen_prop_name, plen_prop_value, NULL)) {
        ALOGE("get prefix len failed, please check your IPv6 config!");
        return -1;
    }

    property_set("net.ipv6.tether", extIface);

#ifdef PD_MODE
	ALOGD("skip setting secondary route for PD mode !");
#else  
    snprintf(subnet, sizeof(subnet), "%s/%s", prefix_prop_value, plen_prop_value);
    ALOGD("add route for subnet: %s", subnet);
  
    tableNumber = findTableNumber(intIface);
    ALOGD("add table %d for dev: %s", tableNumber, intIface);

    if (tableNumber != -1) {
        snprintf(cmd, sizeof(cmd), "route del %s dev %s table %d", subnet, intIface,
                   tableNumber);
        runCmd(IP_PATH, cmd); 
        snprintf(cmd, sizeof(cmd), "-6 rule del table %d", tableNumber);
        runCmd(IP_PATH, cmd);

        snprintf(cmd, sizeof(cmd), "-6 rule add table %d prio %d", 
                    tableNumber , tableNumber);
        ret |= runCmd(IP_PATH, cmd);
        if (ret) ALOGE("IP rule %s got %d", cmd, ret);
        snprintf(cmd, sizeof(cmd), "route add %s dev %s table %d", subnet, intIface,
                    tableNumber);
        ret |= runCmd(IP_PATH, cmd);
        if (ret) ALOGE("IP route %s got %d error = %d : %s", cmd, ret, errno, strerror(errno));

        runCmd(IP_PATH, "route flush cache");
    }

    if (ret != 0) {
        if (tableNumber != -1) {
            snprintf(cmd, sizeof(cmd), "route del %s dev %s table %d", subnet, intIface,
                    tableNumber);
            runCmd(IP_PATH, cmd);

            snprintf(cmd, sizeof(cmd), "-6 rule del table %d", tableNumber);
            runCmd(IP_PATH, cmd);

            runCmd(IP_PATH, "route flush cache");
        }
        ALOGE("Error setting forward rules");
        errno = ENODEV;
        return -1;
    }
#endif

    return 0;
}

//  0         1       2       3   
// IPv6tether clearroute intface extface
int IPv6TetherController::clearIPv6SecondaryRoute(const int argc, char **argv) {
    char cmd[255];
    int ret = 0;
    const char *intIface = argv[2];
    const char *extIface = argv[3];
    int tableNumber;
  
    if (!checkInterface(intIface) || !checkInterface(extIface)) {
        ALOGE("Invalid interface specified");
        errno = ENODEV;
        return -1;
    }

    if (argc < 4) {
        ALOGE("Missing Argument");
        errno = EINVAL;
        return -1;
    }
#ifdef PD_MODE
	ALOGD("skip clearing secondary route for PD mode !");
#else  
    tableNumber = findTableNumber(intIface);
    if (tableNumber != -1) {
        snprintf(cmd, sizeof(cmd), "-6 route flush table %d", tableNumber);
        runCmd(IP_PATH, cmd);
  
        snprintf(cmd, sizeof(cmd), "-6 rule del table %d", tableNumber);
        runCmd(IP_PATH, cmd);
  
        runCmd(IP_PATH, "route flush cache");
  	}
    ALOGI("clearIPv6SecondaryRoute done, tableNumber = %d", tableNumber);
#endif
    
    return 0;
}

//  0         1       2       3   
// IPv6tether setsroute intface extface
int IPv6TetherController::setSourceRoute(const int argc, char **argv) {
    char cmd[255];
    int ret = 0;
    const char *intIface = argv[2];
    const char *extIface = argv[3];
    int tableNumber;
  
    if (!checkInterface(intIface) || !checkInterface(extIface)) {
        ALOGE("Invalid interface specified");
        errno = ENODEV;
        return -1;
    }

    if (argc < 4) {
        ALOGE("Missing Argument");
        errno = EINVAL;
        return -1;
    }
    
    ALOGD("add source route for %s -> %s", intIface, extIface);
  
    if (accept_RA_defrtr(false, extIface) != 0){
    	ALOGE("clear accept_RA_defrtr failed");
        errno = ENODEV;
        return -1;
    }  	
  
    tableNumber = findTableNumber(intIface);
    // +1 for source route table
    tableNumber += 1;
    ALOGD("add source route table %d", tableNumber);

    if (tableNumber != -1) {
    	//delete default route to extIface if it exists.
    	snprintf(cmd, sizeof(cmd), "-6 route del default dev %s", extIface);
        runCmd(IP_PATH, cmd); 
        snprintf(cmd, sizeof(cmd), "-6 route flush table %d", tableNumber);
        runCmd(IP_PATH, cmd); 
        snprintf(cmd, sizeof(cmd), "-6 rule del table %d", tableNumber);
        runCmd(IP_PATH, cmd);

        snprintf(cmd, sizeof(cmd), "-6 rule add iif %s table %d prio %d", 
                    intIface, tableNumber , tableNumber);
        ret |= runCmd(IP_PATH, cmd);
        if (ret) ALOGE("IP rule %s got %d", cmd, ret);
        snprintf(cmd, sizeof(cmd), "-6 route add default dev %s table %d", extIface, tableNumber);
        ret |= runCmd(IP_PATH, cmd);
        if (ret) ALOGE("IP route %s got %d error = %d : %s", cmd, ret, errno, strerror(errno));

    } else {
    	ALOGE("Error finding table number");
    	accept_RA_defrtr(true, extIface);   	
    	errno = ENODEV;
    	ret = -1;	
    }

    if (ret != 0) {
        if (tableNumber != -1) {
            snprintf(cmd, sizeof(cmd), "-6 route flush table %d", tableNumber);
            runCmd(IP_PATH, cmd);

            snprintf(cmd, sizeof(cmd), "-6 rule del table %d", tableNumber);
            runCmd(IP_PATH, cmd);

            runCmd(IP_PATH, "route flush cache");
        }
        ALOGE("Error setting source route");
        accept_RA_defrtr(true, extIface);   	
        errno = ENODEV;
        return -1;
    }

    return 0;
}

//  0         1       2       3   
// IPv6tether clearsroute intface extface
int IPv6TetherController::clearSourceRoute(const int argc, char **argv) {
    char cmd[255];
    int ret = 0;
    const char *intIface = argv[2];
    const char *extIface = argv[3];
    int tableNumber;
  
    if (!checkInterface(intIface) || !checkInterface(extIface)) {
        ALOGE("Invalid interface specified");
        errno = ENODEV;
        return -1;
    }

    if (argc < 4) {
        ALOGE("Missing Argument");
        errno = EINVAL;
        return -1;
    }
    
    if (accept_RA_defrtr(true, extIface) != 0){
    	ALOGE("set accept_RA_defrtr failed");
    }  
	
    tableNumber = findTableNumber(intIface);
    // +1 for source route table
    tableNumber += 1;
    if (tableNumber != -1) {
        snprintf(cmd, sizeof(cmd), "-6 route flush table %d", tableNumber);
        runCmd(IP_PATH, cmd);
  
        snprintf(cmd, sizeof(cmd), "-6 rule del table %d", tableNumber);
        runCmd(IP_PATH, cmd);
  
        runCmd(IP_PATH, "route flush cache");
  	}
    ALOGI("clear source Route done, tableNumber = %d", tableNumber);
    
    return 0;
}

//  0         1       2       3   
// IPv6tether enable intface extface
int IPv6TetherController::enableIPv6Tether(const int argc, char **argv) {
    char cmd[255];
    int ret = 0;
    const char *intIface = argv[2];
    const char *extIface = argv[3];
  
    if (!checkInterface(intIface) || !checkInterface(extIface)) {
        ALOGE("Invalid interface specified");
        errno = ENODEV;
        return -1;
    }

    if (argc < 4) {
        ALOGE("Missing Argument");
        errno = EINVAL;
        return -1;
    }

    setTetherFlag(intIface, true);

#ifdef RADVD_MODE
    if(mCount == 0 && mPid !=0 ){
        ALOGW("radvd should not be running, stop it");
        stopRadvd();
    }
  
    ALOGI("enable IPv6 tethering: radvd mode");
    
#ifdef PD_MODE
    if(accept_RA(true, intIface) != 0){
        ALOGE("Error setting accept_ra!");
     
        setDefaults();
        return -1;
	}
#else
    if(accept_RA(false, intIface) != 0){
        ALOGE("Error clearing accept_ra!");
      
        setDefaults();
        return -1;
	}
#endif

    if (startRadvd() != 0) {
        if(errno == EBUSY){
            ALOGW("radvd is already running");
			updateRadvdConf();
			return 0;
        } else {
            ALOGE("Error starting radvd");
      
            setDefaults();
            return -1;
		}
    }
#else   /* NDP_MODE */

    ALOGI("enable IPv6 tethering: NDP mode");

    if (enableProxy_ndp(true) != 0) {
        ALOGE("Error enable proxy ndp!");
		
        setDefaults();
        setAllMulticast(false, intIface, extIface);

        return -1;
    }
    if (setAllMulticast(true, intIface, extIface) != 0) {
        ALOGW("Error seting all multicast mode");
    }

#endif

    ALOGI("enable IPv6 tethering: mCount = %d", mCount);
    return 0;
}

// ipv6 tether disable intface extface
//  0    1       2       3 
// ipv6tether enable intface extface
int IPv6TetherController::disableIPv6Tether(const int argc, char **argv) {
    char cmd[255];
    const char *intIface = argv[2];
    const char *extIface = argv[3];
    int tableNumber;

    if (!checkInterface(intIface) || !checkInterface(extIface)) {
        ALOGE("Invalid interface specified");
        errno = ENODEV;
        return -1;
    }

    if (argc < 4) {
        ALOGE("Missing Argument");
        errno = EINVAL;
        return -1;
    }

	setTetherFlag(intIface, false);
	
#ifdef RADVD_MODE
    if (mCount == 0) {
        // handle decrement to 0 case (do reset to defaults) and erroneous dec below 0
        stopRadvd();
        setDefaults();
     }
#else /*NDP mode*/
    if (mCount == 0) {
        enableProxy_ndp(false);
		setAllMulticast(false, intIface, extIface);
		setDefaults();
     }
#endif

    ALOGI("disable IPv6 tethering: mCount = %d", mCount);
    return 0;
}

int IPv6TetherController::setIpv6FwdEnabled(bool enable) {

    ALOGD("Setting IPv6 forward enable = %d", enable);

    // In BP tools mode, do not disable IP forwarding
    char bootmode[PROPERTY_VALUE_MAX] = {0};
    property_get("ro.bootmode", bootmode, "unknown");
    if ((enable == false) && (0 == strcmp("bp-tools", bootmode))) {
        return 0;
    }

    int fd = open("/proc/sys/net/ipv6/conf/all/forwarding", O_WRONLY);
    if (fd < 0) {
        ALOGE("Failed to open ipv6_forward (%s)", strerror(errno));
        return -1;
    }

    if (write(fd, (enable ? "1" : "0"), 1) != 1) {
        ALOGE("Failed to write ipv6_forward (%s)", strerror(errno));
        close(fd);
        return -1;
    }
    close(fd);
    return 0;
}

bool IPv6TetherController::getIpv6FwdEnabled() {
    int fd = open("/proc/sys/net/ipv6/conf/all/forwarding", O_RDONLY);

    if (fd < 0) {
        ALOGE("Failed to open ipv6_forward (%s)", strerror(errno));
        return false;
    }

    char enabled;
    if (read(fd, &enabled, 1) != 1) {
        ALOGE("Failed to read ipv6_forward (%s)", strerror(errno));
        close(fd);
        return -1;
    }

    close(fd);
    return (enabled  == '1' ? true : false);
}

