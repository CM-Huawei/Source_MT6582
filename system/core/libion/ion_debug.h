#ifndef _CORKSCREW_MAP_INFO_H
#define _CORKSCREW_MAP_INFO_H
#include <linux/ion_drv.h>
#include <sys/types.h>
#include <stdbool.h>
#include <linux/ion.h>
#include <unwind.h>
#ifdef __cplusplus
extern "C" {
#endif

#define BACKTRACE_SIZE 10
#define STRING_MAX 180
#ifdef HAVE_UNWIND_CONTEXT_STRUCT
typedef struct _Unwind_Context __unwind_context;
#else
typedef _Unwind_Context __unwind_context;
#endif
typedef struct
{
    size_t count;
    intptr_t* addrs;
} stack_crawl_state_t;

typedef struct
{
  intptr_t start_address;
  char *name;
} mapping_info_t;
typedef struct map_info {
    struct map_info* next;
    uintptr_t start;
    uintptr_t end;
    bool is_readable;
    bool is_executable;
    bool is_freed;
    bool is_global;
    void* data; // arbitrary data associated with the map by the user, initially NULL
    char name[STRING_MAX];
} map_info_t;
/* Loads memory map from /proc/<tid>/maps. */
map_info_t* load_map_info_list(pid_t tid);

/* Frees memory map. */
void free_map_info_list(map_info_t* milist);

/* Finds the memory map that contains the specified address. */
const map_info_t* find_map_info(const map_info_t* milist, uintptr_t addr);
/* remove entry which contains the specified address in memory map */
int remove_map_info(map_info_t* local_milist, uintptr_t addr);
/* Returns true if the addr is in an readable map. */
bool is_readable_map(const map_info_t* milist, uintptr_t addr);

/* Returns true if the addr is in an executable map. */
bool is_executable_map(const map_info_t* milist, uintptr_t addr);

/* Acquires a reference to the memory map for this process.
 * The result is cached and refreshed automatically.
 * Make sure to release the map info when done. */
void acquire_my_map_info_list();

/* Releases a reference to the map info for this process that was
 * previous acquired using acquire_my_map_info_list(). */
void release_my_map_info_list();
int get_backtrace(int *fp, intptr_t* addrs, size_t max_entries);
int add_system_map_entry(const char *name,uintptr_t address,uintptr_t size);
int remove_system_map_entry(const char *name,uintptr_t address);
int ion_record_debug_info(int fd,struct ion_handle *handle,unsigned int address, unsigned int length, unsigned int function_name);
#ifdef __cplusplus
}
#endif

#endif // _CORKSCREW_MAP_INFO_H
                                                                           
