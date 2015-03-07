/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "FirewallController"
#define LOG_NDEBUG 0

#include <cutils/log.h>

#include "NetdConstants.h"
#include "FirewallController.h"

const char* FirewallController::LOCAL_INPUT = "fw_INPUT";
const char* FirewallController::LOCAL_OUTPUT = "fw_OUTPUT";
const char* FirewallController::LOCAL_FORWARD = "fw_FORWARD";

// mtk03594: Support enhanced firewall @{
const char* FirewallController::FIREWALL = "firewall";
const char* FirewallController::FIREWALL_MOBILE = "mobile";
const char* FirewallController::FIREWALL_WIFI = "wifi";
//@}

FirewallController::FirewallController(void) {
}

int FirewallController::setupIptablesHooks(void) {

    // mtk03594: Support enhanced firewall @{
    int res = 0;
    res |= execIptables(V4V6, "-F", FIREWALL, NULL);
    res |= execIptables(V4V6, "-A", FIREWALL, "-o", "ppp+", "-j", FIREWALL_MOBILE, NULL);
    res |= execIptables(V4V6, "-A", FIREWALL, "-o", "ccmni+", "-j", FIREWALL_MOBILE, NULL);
    res |= execIptables(V4V6, "-A", FIREWALL, "-o", "ccemni+", "-j", FIREWALL_MOBILE, NULL);
    res |= execIptables(V4V6, "-A", FIREWALL, "-o", "usb+", "-j", FIREWALL_MOBILE, NULL);
    res |= execIptables(V4V6, "-A", FIREWALL, "-o", "cc2mni+", "-j", FIREWALL_MOBILE, NULL);
    res |= execIptables(V4V6, "-A", FIREWALL, "-o", "wlan+", "-j", FIREWALL_WIFI, NULL);
    //@}
    
    return 0;
}

int FirewallController::enableFirewall(void) {
    int res = 0;

    // flush any existing rules
    disableFirewall();

    // create default rule to drop all traffic
    res |= execIptables(V4V6, "-A", LOCAL_INPUT, "-j", "DROP", NULL);
    res |= execIptables(V4V6, "-A", LOCAL_OUTPUT, "-j", "REJECT", NULL);
    res |= execIptables(V4V6, "-A", LOCAL_FORWARD, "-j", "REJECT", NULL);

    return res;
}

int FirewallController::disableFirewall(void) {
    int res = 0;

    // flush any existing rules
    res |= execIptables(V4V6, "-F", LOCAL_INPUT, NULL);
    res |= execIptables(V4V6, "-F", LOCAL_OUTPUT, NULL);
    res |= execIptables(V4V6, "-F", LOCAL_FORWARD, NULL);

    return res;
}

int FirewallController::isFirewallEnabled(void) {
    // TODO: verify that rules are still in place near top
    return -1;
}

int FirewallController::setInterfaceRule(const char* iface, FirewallRule rule) {
    const char* op;
    if (rule == ALLOW) {
        op = "-I";
    } else {
        op = "-D";
    }

    int res = 0;
    res |= execIptables(V4V6, op, LOCAL_INPUT, "-i", iface, "-j", "RETURN", NULL);
    res |= execIptables(V4V6, op, LOCAL_OUTPUT, "-o", iface, "-j", "RETURN", NULL);
    return res;
}

int FirewallController::setEgressSourceRule(const char* addr, FirewallRule rule) {
    IptablesTarget target = V4;
    if (strchr(addr, ':')) {
        target = V6;
    }

    const char* op;
    if (rule == ALLOW) {
        op = "-I";
    } else {
        op = "-D";
    }

    int res = 0;
    res |= execIptables(target, op, LOCAL_INPUT, "-d", addr, "-j", "RETURN", NULL);
    res |= execIptables(target, op, LOCAL_OUTPUT, "-s", addr, "-j", "RETURN", NULL);
    return res;
}

int FirewallController::setEgressDestRule(const char* addr, int protocol, int port,
        FirewallRule rule) {
    IptablesTarget target = V4;
    if (strchr(addr, ':')) {
        target = V6;
    }

    char protocolStr[16];
    sprintf(protocolStr, "%d", protocol);

    char portStr[16];
    sprintf(portStr, "%d", port);

    const char* op;
    if (rule == ALLOW) {
        op = "-I";
    } else {
        op = "-D";
    }

    int res = 0;
    res |= execIptables(target, op, LOCAL_INPUT, "-s", addr, "-p", protocolStr,
            "--sport", portStr, "-j", "RETURN", NULL);
    res |= execIptables(target, op, LOCAL_OUTPUT, "-d", addr, "-p", protocolStr,
            "--dport", portStr, "-j", "RETURN", NULL);
    return res;
}

int FirewallController::setUidRule(int uid, FirewallRule rule) {
    char uidStr[16];
    sprintf(uidStr, "%d", uid);

    const char* op;
    if (rule == ALLOW) {
        op = "-I";
    } else {
        op = "-D";
    }

    int res = 0;

///M: Disable input chain for UID configure @{
    res |= execIptables(V4V6, "-D", LOCAL_INPUT, "-j", "DROP", NULL);
#if 0
    res |= execIptables(V4V6, op, LOCAL_INPUT, "-m", "owner", "--uid-owner", uidStr,
            "-j", "RETURN", NULL);
#endif
/// @}
            
    res |= execIptables(V4V6, op, LOCAL_OUTPUT, "-m", "owner", "--uid-owner", uidStr,
            "-j", "RETURN", NULL);
    return res;
}

int FirewallController::setEgressProtoRule(const char* proto, FirewallRule rule) {
    int protocol = 0;
    IptablesTarget target = V4;

    ALOGI("setEgressProtoRule:%s", proto);
    
    if(!strcmp(proto, "tcp")){
        protocol = PROTOCOL_TCP;
    }else if(!strcmp(proto, "udp")){
        protocol = PROTOCOL_UDP;
    }else if(!strcmp(proto, "gre")){
        protocol = PROTOCOL_GRE;
    }else if(!strcmp(proto, "icmp")){
        protocol = PROTOCOL_ICMP;
    }

    if(protocol == 0){
        return 0;
    }

    char protocolStr[16];
    sprintf(protocolStr, "%d", protocol);

    ALOGI("setEgressProtoRule:%s:%d", proto, protocol);

    const char* op;
    if (rule == ALLOW) {
        op = "-I";
    } else {
        op = "-D";
    }

    int res = 0;
    res |= execIptables(target, op, LOCAL_INPUT, "-p", protocolStr,
            "-j", "RETURN", NULL);
    res |= execIptables(target, op, LOCAL_OUTPUT, "-p", protocolStr,
            "-j", "RETURN", NULL);

    return res;
}

int FirewallController::setUdpForwarding(const char* inInterface, const char* extInterface, const char* ipAddr) {
    char cmdStr[128];
    IptablesTarget target = V4;
    int res = 0;

    ALOGD("setUdpForwarding:%s:%s:%s", inInterface, extInterface, ipAddr);

    //Delete the old IPTABLE rule    
    res |= execIptables(target, "-D", "FORWARD", "-i", inInterface, "-o", extInterface,"-m", "state", "--state", "ESTABLISHED,RELATED", "-j", "ACCEPT", NULL);
    
    res |= execIptables(target, "-A", "FORWARD", "-i", inInterface, "-o", extInterface, "-j", "ACCEPT", NULL);
    
    res |= execIptables(target, "-t", "nat", "-A", "PREROUTING", "-p", "udp", "-i", inInterface, "-j", "DNAT", "--to", ipAddr, NULL);

    return res;
}

int FirewallController::setUidFwRule(int uid, FirewallChinaRule chain, FirewallRule rule) {
    char uidStr[16];
    char cmdStr[128];
    int res = 0;
    const char* op;
    const char* fwChain;

    sprintf(uidStr, "%d", uid);

    if (rule == ALLOW) {
        op = "-I";
    } else {
        op = "-D";
    }

    if(chain == MOBILE) {
        fwChain = "mobile";
    }else{
        fwChain = "wifi";
    }

    res |= execIptables(V4, op, fwChain, "-m", "owner", "--uid-owner", uidStr,
            "-j", "REJECT", "--reject-with", "icmp-net-prohibited", NULL);
    res |= execIptables(V6, op, fwChain, "-m", "owner", "--uid-owner", uidStr,
            "-j", "REJECT", "--reject-with", "icmp6-adm-prohibited", NULL);

    return res;    
}

int FirewallController::clearFwChain(const char* chain) {
    int res = 0;

    if(chain != NULL){
        if(strlen(chain) > 0){
            res |= execIptables(V4V6, "-F", chain, NULL);
        }else{
            ALOGD("Clear all chain");
            res |= execIptables(V4V6, "-F", NULL);
        }
    }else{
        ALOGE("Chain is NULL");
    }

    return res;
}
