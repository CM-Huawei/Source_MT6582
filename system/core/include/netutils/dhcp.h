/*
 * Copyright 2010, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *
 *     http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

#ifndef _NETUTILS_DHCP_H_
#define _NETUTILS_DHCP_H_

#include <sys/cdefs.h>
#include <arpa/inet.h>

__BEGIN_DECLS

extern int do_dhcp(char *iname);
extern int dhcp_do_request(const char *ifname,
                          char *ipaddr,
                          char *gateway,
                          uint32_t *prefixLength,
                          char *dns[],
                          char *server,
                          uint32_t *lease,
                          char *vendorInfo,
                          char *domain,
                          char *mtu);
extern int dhcp_do_request_renew(const char *ifname,
                                char *ipaddr,
                                char *gateway,
                                uint32_t *prefixLength,
                                char *dns[],
                                char *server,
                                uint32_t *lease,
                                char *vendorInfo,
                                char *domain,
                                char *mtu);
extern int dhcp_stop(const char *ifname);
extern int dhcp_release_lease(const char *ifname);
extern char *dhcp_get_errmsg();
extern char *dhcpv6_get_errmsg();
extern char *PD_get_errmsg();

extern int dhcpv6_do_request(const char *interface, char *ipaddr,
		char *dns1,
		char *dns2,
		uint32_t *lease, uint32_t *pid);
extern int dhcpv6_stop(const char *interface);
extern int dhcpv6_do_request_renew(const char *interface, const int pid, char *ipaddr,
		char *dns1,
		char *dns2,
		uint32_t *lease);

extern int dhcpv6_PD_request(const char *interface, char *prefix, uint32_t *lease);
extern int dhcpv6_PD_renew(const char *interface, char *prefix, uint32_t *lease);
extern int dhcpv6_PD_stop(const char *interface);


__END_DECLS

#endif /* _NETUTILS_DHCP_H_ */
