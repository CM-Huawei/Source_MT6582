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
#include <string.h>
#include <errno.h>

#define LOG_TAG "NetlinkEvent"
#include <cutils/log.h>

#include <sysutils/NetlinkEvent.h>
#include <cutils/properties.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <net/if.h>

#include <linux/if.h>
#include <linux/netfilter/nfnetlink.h>
#include <linux/netfilter_ipv4/ipt_ULOG.h>
/* From kernel's net/netfilter/xt_quota2.c */
const int QLOG_NL_EVENT  = 112;

#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <netinet/icmp6.h>
#include <fcntl.h>

const int NetlinkEvent::NlActionUnknown = 0;
const int NetlinkEvent::NlActionAdd = 1;
const int NetlinkEvent::NlActionRemove = 2;
const int NetlinkEvent::NlActionChange = 3;
const int NetlinkEvent::NlActionLinkUp = 4;
const int NetlinkEvent::NlActionLinkDown = 5;
const int NetlinkEvent::NlActionAddressUpdated = 6;
const int NetlinkEvent::NlActionAddressRemoved = 7;
const int NetlinkEvent::NlActionIPv6Enable = 100;		/*100*/
const int NetlinkEvent::NlActionIPv6Disable = 101;	/*101*/
const int NetlinkEvent::NlActionIPv6DNSUpdated = 102;		/*102*/

NetlinkEvent::NetlinkEvent() {
    mAction = NlActionUnknown;
    memset(mParams, 0, sizeof(mParams));
    mPath = NULL;
    mSubsystem = NULL;
}

NetlinkEvent::~NetlinkEvent() {
    int i;
    if (mPath)
        free(mPath);
    if (mSubsystem)
        free(mSubsystem);
    for (i = 0; i < NL_PARAMS_MAX; i++) {
        if (!mParams[i])
            break;
        free(mParams[i]);
    }
}

void NetlinkEvent::dump() {
    int i;
	SLOGD("NL action '%d'\n", mAction);
	SLOGD("NL subsystem '%s'\n", mSubsystem);

    for (i = 0; i < NL_PARAMS_MAX; i++) {
        if (!mParams[i])
            break;
        SLOGD("NL param '%s'\n", mParams[i]);
    }
}

/*
 * Decode a RTM_NEWADDR or RTM_DELADDR message.
 */
bool NetlinkEvent::parseIfAddrMessage(int type, struct ifaddrmsg *ifaddr,
                                      int rtasize) {
    struct rtattr *rta;
    struct ifa_cacheinfo *cacheinfo = NULL;
    char addrstr[INET6_ADDRSTRLEN] = "";

    // Sanity check.
    if (type != RTM_NEWADDR && type != RTM_DELADDR) {
        SLOGE("parseIfAddrMessage on incorrect message type 0x%x\n", type);
        return false;
    }

    // For log messages.
    const char *msgtype = (type == RTM_NEWADDR) ? "RTM_NEWADDR" : "RTM_DELADDR";

    for (rta = IFA_RTA(ifaddr); RTA_OK(rta, rtasize);
         rta = RTA_NEXT(rta, rtasize)) {
        if (rta->rta_type == IFA_ADDRESS) {
            // Only look at the first address, because we only support notifying
            // one change at a time.
            if (*addrstr != '\0') {
                SLOGE("Multiple IFA_ADDRESSes in %s, ignoring\n", msgtype);
                continue;
            }

            // Convert the IP address to a string.
            if (ifaddr->ifa_family == AF_INET) {
                struct in_addr *addr4 = (struct in_addr *) RTA_DATA(rta);
                if (RTA_PAYLOAD(rta) < sizeof(*addr4)) {
                    SLOGE("Short IPv4 address (%d bytes) in %s",
                          RTA_PAYLOAD(rta), msgtype);
                    continue;
                }
                inet_ntop(AF_INET, addr4, addrstr, sizeof(addrstr));
            } else if (ifaddr->ifa_family == AF_INET6) {
                struct in6_addr *addr6 = (struct in6_addr *) RTA_DATA(rta);
                if (RTA_PAYLOAD(rta) < sizeof(*addr6)) {
                    SLOGE("Short IPv6 address (%d bytes) in %s",
                          RTA_PAYLOAD(rta), msgtype);
                    continue;
                }
                inet_ntop(AF_INET6, addr6, addrstr, sizeof(addrstr));
            } else {
                SLOGE("Unknown address family %d\n", ifaddr->ifa_family);
                continue;
            }

            // Find the interface name.
            char ifname[IFNAMSIZ + 1];
            if (!if_indextoname(ifaddr->ifa_index, ifname)) {
                SLOGE("Unknown ifindex %d in %s", ifaddr->ifa_index, msgtype);
                return false;
            }

            // Fill in interface information.
            mAction = (type == RTM_NEWADDR) ? NlActionAddressUpdated :
                                              NlActionAddressRemoved;
            mSubsystem = strdup("net");
            asprintf(&mParams[0], "ADDRESS=%s/%d", addrstr,
                     ifaddr->ifa_prefixlen);
            asprintf(&mParams[1], "INTERFACE=%s", ifname);
            asprintf(&mParams[2], "FLAGS=%u", ifaddr->ifa_flags);
            asprintf(&mParams[3], "SCOPE=%u", ifaddr->ifa_scope);
        } else if (rta->rta_type == IFA_CACHEINFO) {
            // Address lifetime information.
            if (cacheinfo) {
                // We only support one address.
                SLOGE("Multiple IFA_CACHEINFOs in %s, ignoring\n", msgtype);
                continue;
            }

            if (RTA_PAYLOAD(rta) < sizeof(*cacheinfo)) {
                SLOGE("Short IFA_CACHEINFO (%d vs. %d bytes) in %s",
                      RTA_PAYLOAD(rta), sizeof(cacheinfo), msgtype);
                continue;
            }

            cacheinfo = (struct ifa_cacheinfo *) RTA_DATA(rta);
            asprintf(&mParams[4], "PREFERRED=%u", cacheinfo->ifa_prefered);
            asprintf(&mParams[5], "VALID=%u", cacheinfo->ifa_valid);
            asprintf(&mParams[6], "CSTAMP=%u", cacheinfo->cstamp);
            asprintf(&mParams[7], "TSTAMP=%u", cacheinfo->tstamp);
        }
    }

    if (addrstr[0] == '\0') {
        SLOGE("No IFA_ADDRESS in %s\n", msgtype);
        return false;
    }

    return true;
}

/*
 * Parse an binary message from a NETLINK_ROUTE netlink socket.
 */
bool NetlinkEvent::parseBinaryNetlinkMessage(char *buffer, int size) {
    const struct nlmsghdr *nh;
	const int MAX_DNS = 4;

    for (nh = (struct nlmsghdr *) buffer;
         NLMSG_OK(nh, size) && (nh->nlmsg_type != NLMSG_DONE);
         nh = NLMSG_NEXT(nh, size)) {

        if (nh->nlmsg_type == RTM_NEWLINK) {
            int len = nh->nlmsg_len - sizeof(*nh);
            struct ifinfomsg *ifi;

            if (sizeof(*ifi) > (size_t) len) {
                SLOGE("Got a short RTM_NEWLINK message\n");
                continue;
            }

            ifi = (ifinfomsg *)NLMSG_DATA(nh);
            if ((ifi->ifi_flags & IFF_LOOPBACK) != 0) {
                continue;
            }

            struct rtattr *rta = (struct rtattr *)
              ((char *) ifi + NLMSG_ALIGN(sizeof(*ifi)));
            len = NLMSG_PAYLOAD(nh, sizeof(*ifi));

            while(RTA_OK(rta, len)) {
                switch(rta->rta_type) {
                case IFLA_IFNAME:
                    char buffer[16 + IFNAMSIZ];
                    snprintf(buffer, sizeof(buffer), "INTERFACE=%s",
                             (char *) RTA_DATA(rta));
                    mParams[0] = strdup(buffer);
                    mAction = (ifi->ifi_flags & IFF_LOWER_UP) ?
                      NlActionLinkUp : NlActionLinkDown;
                    mSubsystem = strdup("net");
					
					/*mtk80842 for modem stateless/stateful IPv6*/					
					if(mAction == NlActionLinkDown){
						char proc[64];
						snprintf(proc, sizeof(proc), "/proc/sys/net/ipv6/conf/%s/ra_info_flag", 
								(char *) RTA_DATA(rta));
						
						int fd = open(proc, O_WRONLY);
						if (fd < 0) {
							SLOGE("Failed to open ra_info_flag (%s)", strerror(errno));
						} else {
							if (write(fd, "0", 1) != 1) {
								SLOGE("Failed to write ra_info_flag (%s)", strerror(errno));
							}
							SLOGD("clear RA flag done");
							close(fd);	
						}
					}
					/*mtk80842 for IPv6 RDNSS */					
					if(mAction == NlActionLinkDown){
						char prefix_prop_name[PROPERTY_KEY_MAX];	
						char plen_prop_name[PROPERTY_KEY_MAX];	
						char prop_value[PROPERTY_VALUE_MAX] = {'\0'};
						size_t i = 0;
						for(; i < MAX_DNS; i++){
							snprintf(prefix_prop_name, sizeof(prefix_prop_name), 
								"net.ipv6.%s.dns%i", (char *) RTA_DATA(rta),i+1);
							if (property_get(prefix_prop_name, prop_value, NULL)) { 
								property_set(prefix_prop_name, ""); 
							} else{
								SLOGI("clear %d IPv6 DNS for %s", i, (char *) RTA_DATA(rta));
								break;
							}
						}
					}

					/*mtk80842 for IPv6 tethering*/					
					if(mAction == NlActionLinkDown){	
						char prefix_prop_name[PROPERTY_KEY_MAX];	
						char plen_prop_name[PROPERTY_KEY_MAX];	
						char prop_value[PROPERTY_VALUE_MAX] = {'\0'};	
						snprintf(prefix_prop_name, sizeof(prefix_prop_name), 
							"net.ipv6.%s.prefix", (char *) RTA_DATA(rta));	
						
						if (property_get("net.ipv6.tether", prop_value, NULL)) {	
							if(0 == strcmp(prop_value, ((char *)RTA_DATA(rta)))){		
								if (property_get(prefix_prop_name, prop_value, NULL)) {	
#ifndef MTK_IPV6_TETHER_PD_MODE
							        property_set("net.ipv6.lastprefix", prop_value);
#endif
									SLOGD("set last prefix as %s\n", prop_value);
							    }
							} else	{
								SLOGW("%s is not a tether interface\n", (char *)RTA_DATA(rta));
							}	
						}

						if (property_get(prefix_prop_name, prop_value, NULL)) {	
							property_set(prefix_prop_name, "");	
						}	
						snprintf(plen_prop_name, sizeof(plen_prop_name), 
							"net.ipv6.%s.plen", (char *) RTA_DATA(rta));	
						if (property_get(plen_prop_name, prop_value, NULL)) {	
							property_set(plen_prop_name, "");	
							}					
						}
					
                    break;
                }

                rta = RTA_NEXT(rta, len);
            }

        } else if (nh->nlmsg_type == QLOG_NL_EVENT) {
            char *devname;
            ulog_packet_msg_t *pm;
            size_t len = nh->nlmsg_len - sizeof(*nh);
            if (sizeof(*pm) > len) {
                SLOGE("Got a short QLOG message\n");
                continue;
            }
            pm = (ulog_packet_msg_t *)NLMSG_DATA(nh);
            devname = pm->indev_name[0] ? pm->indev_name : pm->outdev_name;
            asprintf(&mParams[0], "ALERT_NAME=%s", pm->prefix);
            asprintf(&mParams[1], "INTERFACE=%s", devname);
            mSubsystem = strdup("qlog");
            mAction = NlActionChange;

        } else if (nh->nlmsg_type == RTM_NEWADDR ||
                   nh->nlmsg_type == RTM_DELADDR) {
            int len = nh->nlmsg_len - sizeof(*nh);
            struct ifaddrmsg *ifa;

            if (sizeof(*ifa) > (size_t) len) {
                SLOGE("Got a short RTM_xxxADDR message\n");
                continue;
            }

            ifa = (ifaddrmsg *)NLMSG_DATA(nh);
            size_t rtasize = IFA_PAYLOAD(nh);
            if (!parseIfAddrMessage(nh->nlmsg_type, ifa, rtasize)) {
                continue;
            }
        } else if(nh->nlmsg_type == RTM_NEWPREFIX){

				struct prefixmsg *prefix = (prefixmsg *)NLMSG_DATA(nh);
				int len = nh->nlmsg_len;
				struct rtattr * tb[RTA_MAX+1];
				char if_name[IFNAMSIZ] = "";
				
				if (nh->nlmsg_type != RTM_NEWPREFIX) {
					SLOGE("Not a prefix: %08x %08x %08x\n",
						nh->nlmsg_len, nh->nlmsg_type, nh->nlmsg_flags);
					continue;
				}
			
				len -= NLMSG_LENGTH(sizeof(*prefix));
				if (len < 0) {
					SLOGE("BUG: wrong nlmsg len %d\n", len);
					continue;
				}
					
				if (prefix->prefix_family != AF_INET6) {
					SLOGE("wrong family %d\n", prefix->prefix_family);
					continue;
				}
				if (prefix->prefix_type != 3 /*prefix opt*/) {
					SLOGE( "wrong ND type %d\n", prefix->prefix_type);
					continue;
				}
				if_indextoname(prefix->prefix_ifindex, if_name);
				
				{ 
					int max = RTA_MAX;
				    struct rtattr *rta = RTM_RTA(prefix);
					memset(tb, 0, sizeof(struct rtattr *) * (max + 1));
					while (RTA_OK(rta, len)) {
						if ((rta->rta_type <= max) && (!tb[rta->rta_type]))
							tb[rta->rta_type] = rta;
						rta = RTA_NEXT(rta,len);
					}
					if (len)
						SLOGE("!!!Deficit %d, rta_len=%d\n", len, rta->rta_len);
				}
				if (tb[PREFIX_ADDRESS] && (0 == strncmp(if_name, "ccmni", 2))) {
					struct in6_addr *pfx;
					char abuf[256];
					char prefix_prop_name[PROPERTY_KEY_MAX];
					char plen_prop_name[PROPERTY_KEY_MAX];
					char prefix_value[PROPERTY_VALUE_MAX] = {'\0'};
					char plen_value[4]; 
					
					pfx = (struct in6_addr *)RTA_DATA(tb[PREFIX_ADDRESS]);
			
					memset(abuf, '\0', sizeof(abuf));
					const char* addrStr = inet_ntop(AF_INET6, pfx, abuf, sizeof(abuf));

					snprintf(prefix_prop_name, sizeof(prefix_prop_name), 
						"net.ipv6.%s.prefix", if_name);
					property_get(prefix_prop_name, prefix_value, NULL);
					if(NULL != addrStr && strcmp(addrStr, prefix_value)){
						SLOGI("%s new prefix: %s, len=%d\n", if_name, addrStr, prefix->prefix_len);  

						property_set(prefix_prop_name, addrStr);
						snprintf(plen_prop_name, sizeof(plen_prop_name), 
								"net.ipv6.%s.plen", if_name);
						snprintf(plen_value, sizeof(plen_value), 
								"%d", prefix->prefix_len);						
						property_set(plen_prop_name, plen_value);
						{
			                char buffer[16 + IFNAMSIZ];
			                snprintf(buffer, sizeof(buffer), "INTERFACE=%s", if_name);
			                mParams[0] = strdup(buffer);							
						    mAction = NlActionIPv6Enable;
							mSubsystem = strdup("net");
						}
					} else {
						SLOGD("get an exist prefix: = %s\n", addrStr);
					} 					
				}else{
					SLOGD("ignore prefix of %s\n", if_name);
				}
	/*		
				if (prefix->prefix_flags & IF_PREFIX_ONLINK)
					;
				if (prefix->prefix_flags & IF_PREFIX_AUTOCONF)
					;
	*/	
        }else if(nh->nlmsg_type == RTM_NEWNDUSEROPT) {
            int len = nh->nlmsg_len - sizeof(*nh);
            struct nduseroptmsg *ndmsg;

			SLOGI("Got a RTM_NEWNDUSEROPT message\n");

            if (sizeof(*ndmsg) > (size_t) len) {
                SLOGE("Got a short RTM_NEWNDUSEROPT message\n");
                continue;
            }

            ndmsg = (nduseroptmsg *)NLMSG_DATA(nh);
			if ( ndmsg->nduseropt_icmp_type != ND_ROUTER_ADVERT ) {
				SLOGE("ignoring non-Router Advertisement message");
				continue;
			}
			{
				char if_name[IFNAMSIZ] = "";
				struct nd_opt_hdr *opt;
				unsigned short opt_len;
				
				if_indextoname(ndmsg->nduseropt_ifindex, if_name);
				SLOGD("get nduseropt message from %s",if_name);
				
				opt = (struct nd_opt_hdr *) (ndmsg + 1);
				opt_len = ndmsg->nduseropt_opts_len;
			
				while(opt_len >= sizeof (struct nd_opt_hdr)) {
					size_t nd_opt_len = opt->nd_opt_len;
			
					if (nd_opt_len == 0 || opt_len < (nd_opt_len << 3)){
						SLOGE("short opt len of nduseropt message");
						break;
					}
			
					if (opt->nd_opt_type == ND_OPT_RDNSS) {
						struct nd_opt_rdnss *rdnss_opt;
						struct in6_addr *addr;
						char* dnss[MAX_DNS];  /* max dns server count*/
						size_t opt_l;
						size_t i = 0;	
						bool need_update = false;
						char rdnss_prop_name[PROPERTY_KEY_MAX]; 
						char prop_value[PROPERTY_VALUE_MAX] = {'\0'};
						char buf[INET6_ADDRSTRLEN + 1];
						rdnss_opt = (struct nd_opt_rdnss *) opt;
						opt_l = opt->nd_opt_len;

						for (addr = (struct in6_addr *) (rdnss_opt + 1); opt_l >= 2; addr++, opt_l -= 2) {
							int dns_change;
							if (!inet_ntop (AF_INET6, addr, buf, sizeof (buf))){
								strcpy(buf, "[invalid]");
								SLOGE("nduseropt, inet_ntop invalid address!");
								continue;
							}
					
							snprintf(rdnss_prop_name, sizeof(rdnss_prop_name), 
										"net.ipv6.%s.dns%d", if_name,i+1);
							
							if (property_get(rdnss_prop_name, prop_value, NULL)) {	
								if (0 == strncmp(buf, prop_value, sizeof(buf))){
									SLOGD("nduseropt, get exist DNS server: %s", buf);
									i++;
									continue;
								}
							}
							
							SLOGI("nduseropt, get DNS server: %s from RA",buf);
							property_set(rdnss_prop_name, buf); 
							if(i < MAX_DNS){
								dnss[i] = strdup(buf);
								need_update = true;
								i++;
							}
							else{
								SLOGW("nduseropt, get too much DNS server");
								break;
							}
						}
						if(need_update){
							// Fill in DNS information.
							mAction = NlActionIPv6DNSUpdated;
							mSubsystem = strdup("net");
							asprintf(&mParams[0], "INTERFACE=%s", if_name);
							asprintf(&mParams[1], "DNSNumber=%d", i);
							size_t j = 0;
							while(j < i && j < MAX_DNS && dnss[j] != NULL){
								SLOGI("nduseropt, update IPv6 DNS%d as %s", j+1, dnss[j]);
								//asprintf(&mParams[2+j], "DNS%d=%s", j+1, dnss[j]);
								free(dnss[j]);
								j++;
							}
						}			
					} else {
						SLOGI("get non-RDNSS nduseropt message");
					}
			
					opt_len -= opt->nd_opt_len << 3;
					opt = (struct nd_opt_hdr *) ((uint8_t *) opt + (opt->nd_opt_len << 3));
				}
			}
        } else {
                SLOGD("Unexpected netlink message. type=0x%x\n", nh->nlmsg_type);
        }
    }

    return true;
}

/* If the string between 'str' and 'end' begins with 'prefixlen' characters
 * from the 'prefix' array, then return 'str + prefixlen', otherwise return
 * NULL.
 */
static const char*
has_prefix(const char* str, const char* end, const char* prefix, size_t prefixlen)
{
    if ((end-str) >= (ptrdiff_t)prefixlen && !memcmp(str, prefix, prefixlen))
        return str + prefixlen;
    else
        return NULL;
}

/* Same as strlen(x) for constant string literals ONLY */
#define CONST_STRLEN(x)  (sizeof(x)-1)

/* Convenience macro to call has_prefix with a constant string literal  */
#define HAS_CONST_PREFIX(str,end,prefix)  has_prefix((str),(end),prefix,CONST_STRLEN(prefix))


/*
 * Parse an ASCII-formatted message from a NETLINK_KOBJECT_UEVENT
 * netlink socket.
 */
bool NetlinkEvent::parseAsciiNetlinkMessage(char *buffer, int size) {
    const char *s = buffer;
    const char *end;
    int param_idx = 0;
    int i;
    int first = 1;

    if (size == 0)
        return false;

    /* Ensure the buffer is zero-terminated, the code below depends on this */
    buffer[size-1] = '\0';

    end = s + size;
    while (s < end) {
        if (first) {
            const char *p;
            /* buffer is 0-terminated, no need to check p < end */
            for (p = s; *p != '@'; p++) {
                if (!*p) { /* no '@', should not happen */
                    return false;
                }
            }
            mPath = strdup(p+1);
            first = 0;
        } else {
            const char* a;
            if ((a = HAS_CONST_PREFIX(s, end, "ACTION=")) != NULL) {
                if (!strcmp(a, "add"))
                    mAction = NlActionAdd;
                else if (!strcmp(a, "remove"))
                    mAction = NlActionRemove;
                else if (!strcmp(a, "change"))
                    mAction = NlActionChange;
            } else if ((a = HAS_CONST_PREFIX(s, end, "SEQNUM=")) != NULL) {
                mSeq = atoi(a);
            } else if ((a = HAS_CONST_PREFIX(s, end, "SUBSYSTEM=")) != NULL) {
                mSubsystem = strdup(a);
            } else if (param_idx < NL_PARAMS_MAX) {
                mParams[param_idx++] = strdup(s);
            }
        }
        s += strlen(s) + 1;
    }
    return true;
}

bool NetlinkEvent::decode(char *buffer, int size, int format) {
    if (format == NetlinkListener::NETLINK_FORMAT_BINARY) {
        return parseBinaryNetlinkMessage(buffer, size);
    } else {
        return parseAsciiNetlinkMessage(buffer, size);
    }
}

const char *NetlinkEvent::findParam(const char *paramName) {
    size_t len = strlen(paramName);
    for (int i = 0; i < NL_PARAMS_MAX && mParams[i] != NULL; ++i) {
        const char *ptr = mParams[i] + len;
        if (!strncmp(mParams[i], paramName, len) && *ptr == '=')
            return ++ptr;
    }

    SLOGE("NetlinkEvent::FindParam(): Parameter '%s' not found", paramName);
    return NULL;
}
