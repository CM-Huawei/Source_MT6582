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

#define LOG_TAG "ION_DEBUG"
//#define LOG_NDEBUG 0
#include <sys/types.h>
#include <cutils/log.h>
#include <unistd.h>
#include "ion_debug.h"

#include <ctype.h>
#include <stdio.h>
#include <string.h>
#include <limits.h>
#include <pthread.h>
#include <unistd.h>
#include <cutils/log.h>
#include <sys/time.h>
#include <stdlib.h>
// 6f000000-6f01e000 rwxp 00000000 00:0c 16389419   /system/lib/libcomposer.so\n
// 012345678901234567890123456789012345678901234567890123456789
// 0         1         2         3         4         5
#define SO_MAX 160
map_info_t misopool[SO_MAX];
map_info_t *mifreelist = NULL;
int misocount = 0;
map_info_t *g_milist = NULL;
int milistcount = 0;
pthread_mutex_t ion_debug_lock = PTHREAD_MUTEX_INITIALIZER;
static _Unwind_Reason_Code trace_function(__unwind_context *context, void *arg)
{
    stack_crawl_state_t* state = (stack_crawl_state_t*)arg;
    if (state->count) {
        intptr_t ip = (intptr_t)_Unwind_GetIP(context);
        if (ip) {
            state->addrs[0] = ip;
            state->addrs++;
            state->count--;
            return _URC_NO_REASON;
        }
    }
    /*
     * If we run out of space to record the address or 0 has been seen, stop
     * unwinding the stack.
     */
    return _URC_END_OF_STACK;
}

inline
int get_backtrace(int *fp, intptr_t* addrs, size_t max_entries)
{
    stack_crawl_state_t state;
    state.count = max_entries;
    state.addrs = (intptr_t*)addrs;
    _Unwind_Backtrace(trace_function, (void*)&state);
    return max_entries - state.count;
}

int remove_map_info(map_info_t* local_milist, uintptr_t addr)
{
   map_info_t *mi = local_milist;
   map_info_t *prev = NULL;
   
   while(mi && !(addr >= mi->start && addr < mi->end && (mi->is_freed != true)))
   {
        prev = mi;
        mi = mi->next;
   }
   if(mi != NULL)
   {
        if(mi->is_global == true )
        {
                if(prev == NULL)
                {
                        g_milist = mi->next;
                }
                else
                {
                        prev->next = mi->next;
                }
                mi->next = mifreelist;
                mifreelist = mi;
        }
        else
        {
                mi->is_freed = true;
        }
	if(mi->start== addr)
	{
        	return 1;
	}
   }
   return 0;
}

int add_system_map_entry(const char *name,uintptr_t address,uintptr_t size)
{
        map_info_t *tmp;
        size_t name_len;
#if 1
pthread_mutex_lock(&ion_debug_lock);
	tmp = NULL;
	name_len = strlen(name);
        if (name_len && name[name_len - 1] == '\n')
        {
                name_len -= 1;
        }

        tmp = find_map_info(g_milist,address);
        if(tmp != NULL) 
	{
		remove_map_info(g_milist,address);
	}
	if(mifreelist == NULL)
        {
                mifreelist = misopool+misocount++;
                mifreelist->next = NULL;
        }
        tmp = mifreelist;
        mifreelist = mifreelist->next;
        memset(tmp,0,sizeof(map_info_t));
        tmp->start = address;
        tmp->end = address+size;
        tmp->is_readable = true;
        tmp->is_executable = true;
        tmp->is_freed = false;
        tmp->is_global = true;
        tmp->data = NULL;
        memcpy(tmp->name, name, name_len);
        tmp->name[name_len] = '\0';
	tmp->next = g_milist;
	g_milist = tmp;
#endif 
        ALOGE("[add_system_map_entry]so %s address 0x%x size %d has been load into memory ion_debug_lock 0x%x\n",name,address,size,&ion_debug_lock);
#if 1
/*
	p = g_milist;
	while(p != NULL)
	{
		ALOGE("[add_system_map_entry]current so %s  next %x\n",p->name,p->next);
		p = p->next;	
	}	*/
	pthread_mutex_unlock(&ion_debug_lock);
#endif
        return 1;
}
int remove_system_map_entry(const char *name,uintptr_t address)
{
	int ret = 0;
	//map_info_t *p;

#if 1
	pthread_mutex_lock(&ion_debug_lock);
	ret = remove_map_info(g_milist,address);
        ALOGE("[remove_system_map_entry]so %s address 0x%x has been unload ion_debug_lock 0x%x \n",name,address,&ion_debug_lock);
	/*p = g_milist;
        while(p != NULL)
        {
                ALOGE("[remove_system_map_entry]current so %s next %x\n",p->name,p->next);
                p = p->next;
        }*/

	pthread_mutex_unlock(&ion_debug_lock);	
#endif
	return ret;
}

static map_info_t* parse_maps_line(const char* line)
{
    unsigned long int start;
    unsigned long int end;
    map_info_t* mi = NULL;
    char permissions[5];
    int name_pos;
    if (sscanf(line, "%lx-%lx %4s %*x %*x:%*x %*d%n", &start, &end,
            permissions, &name_pos) != 3) {
        return NULL;
    }

    while (isspace(line[name_pos])) {
        name_pos += 1;
    }
    const char* name = line + name_pos;
    size_t name_len = strlen(name);
    if (name_len && name[name_len - 1] == '\n') {
        name_len -= 1;
    }
    if(name_len > STRING_MAX)
    	mi = calloc(1, sizeof(map_info_t) + name_len - STRING_MAX + 1);
    else
	mi = calloc(1, sizeof(map_info_t));
    if (mi) {
        mi->start = start;
        mi->end = end;
        mi->is_readable = strlen(permissions) == 4 && permissions[0] == 'r';
        mi->is_executable = strlen(permissions) == 4 && permissions[2] == 'x';
	mi->is_freed = false;
	mi->is_global = false;
        mi->data = NULL;
        memcpy(mi->name, name, name_len);
        mi->name[name_len] = '\0';
        printf("Parsed map: start=0x%08x, end=0x%08x, "
                "is_readable=%d, is_executable=%d, name=%s\n",
                mi->start, mi->end, mi->is_readable, mi->is_executable, mi->name);

    }
    return mi;
}
map_info_t* load_map_info_list(pid_t tid) {
    char path[PATH_MAX];
    char line[1024];
    FILE* fp;
    map_info_t* milist = NULL;
    snprintf(path, PATH_MAX, "/proc/%d/maps", tid);
    fp = fopen(path, "r");
    if (fp) {
        while(fgets(line, sizeof(line), fp)) {
            map_info_t* mi = parse_maps_line(line);
            if (mi) {
                mi->next = milist;
                milist = mi;
            }
        }
        fclose(fp);
    }
    return milist;
}

void free_map_info_list(map_info_t* milist) {
    //pthread_mutex_lock(&ion_debug_lock);
    while (milist) {
        map_info_t* next = milist->next;
	if(milist->is_global != true)
        	free(milist);
	else
	{
		milist->next = mifreelist;
		mifreelist = milist;
	} 
        milist = next;
    }
    //pthread_mutex_unlock(&ion_debug_lock);
}

const map_info_t* find_map_info(const map_info_t* milist, uintptr_t addr) {
    const map_info_t* mi;
    mi = milist; 
    //pthread_mutex_lock(&ion_debug_lock);
    while (mi && !(addr >= mi->start && addr < mi->end && (mi->is_freed != true))) {
        mi = mi->next;
    }
    //pthread_mutex_unlock(&ion_debug_lock);
    return mi;
}

bool is_readable_map(const map_info_t* milist, uintptr_t addr) {
    const map_info_t* mi = find_map_info(milist, addr);
    return mi && mi->is_readable;
}

bool is_executable_map(const map_info_t* milist, uintptr_t addr) {
    const map_info_t* mi = find_map_info(milist, addr);
    return mi && mi->is_executable;
}
//static pthread_mutex_t g_my_map_info_list_mutex = PTHREAD_MUTEX_INITIALIZER;
static map_info_t* g_my_map_info_list = NULL;

typedef struct {
    uint32_t refs;
} my_map_info_data_t;

void show_my_map_info_list(map_info_t* milist){
    while (milist) 
    {
     	map_info_t* next = milist->next;
        printf("Parsed map: start=0x%08x, end=0x%08x, "
              		"is_readable=%d, is_executable=%d ,is_freed=%d,is_global=%d, name=%s\n",
            		milist->start, milist->end, milist->is_readable, milist->is_executable,milist->is_freed,milist->is_global, milist->name);
	milist = next;
    }
}
void acquire_my_map_info_list() {
    pthread_mutex_lock(&ion_debug_lock);
//    ALOGE("[acquire_my_map_info_list] ion_debug_lock 0x%x\n",&ion_debug_lock);
    if (!g_milist) 
    {
        g_milist = load_map_info_list(getpid());
	milistcount = 1;
    }
    else
    {
	milistcount++;
    }
    pthread_mutex_unlock(&ion_debug_lock);
}

void release_my_map_info_list() {
    pthread_mutex_lock(&ion_debug_lock); 
 //   ALOGE("[release_my_map_info_list] ion_debug_lock 0x%x\n",&ion_debug_lock);

    if (g_milist) 
    {
	if(milistcount == 1)
	{
		free_map_info_list(g_milist);
		g_milist = NULL;
	}
	else if(milistcount <= 0)
	{
		printf("[ERROR] release g_milist but milistcount is equal or less than zero\n");
	}
	else
	{
		milistcount--;
	}
    }
    pthread_mutex_unlock(&ion_debug_lock);
}
int ion_record_debug_info(int fd,struct ion_handle *handle,unsigned int address, unsigned int length, unsigned int function_name)
{
        unsigned int cmd,i;
        int ret = 0;
        ion_sys_data_t arg;
        map_info_t* tmp;

        //set second level command
        cmd = ION_CMD_SYSTEM;

        //set third leyel command
        arg.sys_cmd = ION_SYS_RECORD;

        //record ION function name for record fucntion
        arg.record_param.action = function_name;
        arg.record_param.pid = getpid();
        arg.record_param.group_id = 0;
        if((ION_FUNCTION_MMAP == function_name) || (ION_FUNCTION_SHARE == function_name) || (ION_FUNCTION_SHARE_CLOSE == function_name))
        {
                arg.record_param.fd  = (fd>>16)&0xffff;
        }
	else if(ION_FUNCTION_CHECK_ENABLE == function_name)
	{
		ret = ion_custom_ioctl(fd,cmd,(void *)&arg);
        	return ret;
	}
        else
        {
                arg.record_param.fd  = fd;
        }
        arg.record_param.handle = handle;
        arg.record_param.buffer = NULL;
        arg.record_param.client = NULL;

        if(address > 0)
        {
                arg.record_param.address = address;
                arg.record_param.length = length;
                arg.record_param.address_type = ADDRESS_USER_VIRTUAL;
        }
        else
        {
                arg.record_param.address = 0;
                arg.record_param.length = 0;
                arg.record_param.address_type = ADDRESS_MAX;
        }

        //get backtrace
        arg.record_param.backtrace_num = get_backtrace(__builtin_frame_address(0), arg.record_param.backtrace, BACKTRACE_SIZE);
        if(arg.record_param.backtrace_num == 0)
        {
                ALOGE("[ion_record_debug_info] ERROR!!!! can't get backtrace !!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
        pthread_mutex_lock(&ion_debug_lock);

        //get mapping info
        for(i = 0;i < arg.record_param.backtrace_num;i++)
        {
                //ALOGE("ION_%d: %x",arg.record_param.action,arg.record_param.backtrace[i]);
		if(g_milist == NULL)
		{
			ALOGE("g_milist is NULL\n");
			break;
		}
                tmp = find_map_info(g_milist,arg.record_param.backtrace[i]);
                if(tmp != NULL)
                {
                        arg.record_param.mapping_record[i].name = tmp->name;
                        arg.record_param.mapping_record[i].address = tmp->start;
                        arg.record_param.mapping_record[i].size = tmp->end - tmp->start +1;
                        //ALOGE("start address 0x%x name %s\n",arg.record_param.mapping_record[i].address, arg.record_param.mapping_record[i].name);
                }
                else
                {
                        ALOGE("Address 0x%x can't get libinfo from system map\n",arg.record_param.backtrace[i]);
                }
        }
        pthread_mutex_unlock(&ion_debug_lock);
        if((ION_FUNCTION_MMAP == function_name) || (ION_FUNCTION_SHARE == function_name) || (ION_FUNCTION_SHARE_CLOSE == function_name))
        {
                ret = ion_custom_ioctl(fd&0xffff,cmd,(void *)&arg);
        }
        else
        {
                ret = ion_custom_ioctl(fd,cmd,(void *)&arg);
        }
        return ret;
}
