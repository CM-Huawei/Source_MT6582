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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/uio.h>
#include <dirent.h>
#include <limits.h>
#include <ctype.h>
#include <pthread.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <sys/inotify.h>

#include <cutils/fs.h>
#include <cutils/hashmap.h>
#include <cutils/multiuser.h>

#include <private/android_filesystem_config.h>

#include "fuse.h"

#include <cutils/log.h>
#include <cutils/properties.h>
#include <linux/msdos_fs.h>
#undef  LOG_TAG
#define LOG_TAG "sdcard"

/* README
 *
 * What is this?
 * 
 * sdcard is a program that uses FUSE to emulate FAT-on-sdcard style
 * directory permissions (all files are given fixed owner, group, and 
 * permissions at creation, owner, group, and permissions are not 
 * changeable, symlinks and hardlinks are not createable, etc.
 *
 * See usage() for command line options.
 *
 * It must be run as root, but will drop to requested UID/GID as soon as it
 * mounts a filesystem.  It will refuse to run if requested UID/GID are zero.
 *
 * Things I believe to be true:
 *
 * - ops that return a fuse_entry (LOOKUP, MKNOD, MKDIR, LINK, SYMLINK,
 * CREAT) must bump that node's refcount
 * - don't forget that FORGET can forget multiple references (req->nlookup)
 * - if an op that returns a fuse_entry fails writing the reply to the
 * kernel, you must rollback the refcount to reflect the reference the
 * kernel did not actually acquire
 *
 * This daemon can also derive custom filesystem permissions based on directory
 * structure when requested. These custom permissions support several features:
 *
 * - Apps can access their own files in /Android/data/com.example/ without
 * requiring any additional GIDs.
 * - Separate permissions for protecting directories like Pictures and Music.
 * - Multi-user separation on the same physical device.
 *
 * The derived permissions look like this:
 *
 * rwxrwx--x root:sdcard_rw     /
 * rwxrwx--- root:sdcard_pics   /Pictures
 * rwxrwx--- root:sdcard_av     /Music
 *
 * rwxrwx--x root:sdcard_rw     /Android
 * rwxrwx--x root:sdcard_rw     /Android/data
 * rwxrwx--- u0_a12:sdcard_rw   /Android/data/com.example
 * rwxrwx--x root:sdcard_rw     /Android/obb/
 * rwxrwx--- u0_a12:sdcard_rw   /Android/obb/com.example
 *
 * rwxrwx--- root:sdcard_all    /Android/user
 * rwxrwx--x root:sdcard_rw     /Android/user/10
 * rwxrwx--- u10_a12:sdcard_rw  /Android/user/10/Android/data/com.example
 */

#define FUSE_TRACE 0

#define LOG(x...) SLOGI(x)
#define ERROR(x...)  SLOGE(x)

#if FUSE_TRACE
#define TRACE(x...) SLOGD(x)
#else
#define TRACE(x...) do {} while (0)
#endif

#define FUSE_UNKNOWN_INO 0xffffffff

#define MOUNT_POINT_2 "/storage/sdcard1"

#define MTK_SHARE_SD_MAGIC_VOLUME_ID 60389

/* Maximum number of bytes to write in one request. */
#define MAX_WRITE (256 * 1024)

/* Maximum number of bytes to read in one request. */
#define MAX_READ (128 * 1024)

/* Largest possible request.
 * The request size is bounded by the maximum size of a FUSE_WRITE request because it has
 * the largest possible data payload. */
#define MAX_REQUEST_SIZE (sizeof(struct fuse_in_header) + sizeof(struct fuse_write_in) + MAX_WRITE)

/* Default number of threads. */
#define DEFAULT_NUM_THREADS 2

/* Pseudo-error constant used to indicate that no fuse status is needed
 * or that a reply has already been written. */
#define NO_STATUS 1

/* Path to system-provided mapping of package name to appIds */
static const char* const kPackagesListFile = "/data/system/packages.list";

/* Supplementary groups to execute with */
static const gid_t kGroups[1] = { AID_PACKAGE_INFO };

#ifdef MTK_SHARED_SDCARD
#define LIMIT_SDCARD_SIZE
#endif

#ifdef LIMIT_SDCARD_SIZE
#define DATA_FREE_SIZE_TH_NAME "data_free_size_th"
static long long internal_sdcard_free_size_threshold=0;
struct env_ioctl
{
	char *name;
	int name_len;
	char *value;
	int value_len;	
};
#define ENV_MAGIC	 'e'
#define ENV_READ		_IOW(ENV_MAGIC, 1, int)
#define ENV_WRITE 		_IOW(ENV_MAGIC, 2, int)
#endif

/* Permission mode for a specific node. Controls how file permissions
 * are derived for children nodes. */
typedef enum {
    /* Nothing special; this node should just inherit from its parent. */
    PERM_INHERIT,
    /* This node is one level above a normal root; used for legacy layouts
     * which use the first level to represent user_id. */
    PERM_LEGACY_PRE_ROOT,
    /* This node is "/" */
    PERM_ROOT,
    /* This node is "/Android" */
    PERM_ANDROID,
    /* This node is "/Android/data" */
    PERM_ANDROID_DATA,
    /* This node is "/Android/obb" */
    PERM_ANDROID_OBB,
    /* This node is "/Android/user" */
    PERM_ANDROID_USER,
} perm_t;

/* Permissions structure to derive */
typedef enum {
    DERIVE_NONE,
    DERIVE_LEGACY,
    DERIVE_UNIFIED,
} derive_t;

struct handle {
    int fd;
};

struct dirhandle {
    DIR *d;
};

struct node {
    __u32 refcount;
    __u64 nid;
    __u64 gen;

    /* State derived based on current position in hierarchy. */
    perm_t perm;
    userid_t userid;
    uid_t uid;
    gid_t gid;
    mode_t mode;

    struct node *next;          /* per-dir sibling list */
    struct node *child;         /* first contained file by this dir */
    struct node *parent;        /* containing directory */

    size_t namelen;
    char *name;
    /* If non-null, this is the real name of the file in the underlying storage.
     * This may differ from the field "name" only by case.
     * strlen(actual_name) will always equal strlen(name), so it is safe to use
     * namelen for both fields.
     */
    char *actual_name;

    /* If non-null, an exact underlying path that should be grafted into this
     * position. Used to support things like OBB. */
    char* graft_path;
    size_t graft_pathlen;
};

static int str_hash(void *key) {
    return hashmapHash(key, strlen(key));
}

/** Test if two string keys are equal ignoring case */
static bool str_icase_equals(void *keyA, void *keyB) {
    return strcasecmp(keyA, keyB) == 0;
}

static int int_hash(void *key) {
    return (int) key;
}

static bool int_equals(void *keyA, void *keyB) {
    return keyA == keyB;
}

/* Global data structure shared by all fuse handlers. */
struct fuse {
    pthread_mutex_t lock;

    __u64 next_generation;
    int fd;
    derive_t derive;
    bool split_perms;
    gid_t write_gid;
    struct node root;
    char obbpath[PATH_MAX];

    Hashmap* package_to_appid;
    Hashmap* appid_with_rw;
#ifdef LIMIT_SDCARD_SIZE
	__u64 free_size; //add by mtk for limit internal sdcard size
#endif
};

/* Private data used by a single fuse handler. */
struct fuse_handler {
    struct fuse* fuse;
    int token;

    /* To save memory, we never use the contents of the request buffer and the read
     * buffer at the same time.  This allows us to share the underlying storage. */
    union {
        __u8 request_buffer[MAX_REQUEST_SIZE];
        __u8 read_buffer[MAX_READ];
    };
};

static inline void *id_to_ptr(__u64 nid)
{
    return (void *) (uintptr_t) nid;
}

static inline __u64 ptr_to_id(void *ptr)
{
    return (__u64) (uintptr_t) ptr;
}

static void acquire_node_locked(struct node* node)
{
    node->refcount++;
    TRACE("ACQUIRE %p (%s) rc=%d\n", node, node->name, node->refcount);
}

static void remove_node_from_parent_locked(struct node* node);

static void release_node_locked(struct node* node)
{
    TRACE("RELEASE %p (%s) rc=%d\n", node, node->name, node->refcount);
    if (node->refcount > 0) {
        node->refcount--;
        if (!node->refcount) {
            TRACE("DESTROY %p (%s)\n", node, node->name);
            remove_node_from_parent_locked(node);

                /* TODO: remove debugging - poison memory */
            memset(node->name, 0xef, node->namelen);
            free(node->name);
            free(node->actual_name);
            memset(node, 0xfc, sizeof(*node));
            free(node);
        }
    } else {
        ERROR("Zero refcnt %p\n", node);
    }
}

static void add_node_to_parent_locked(struct node *node, struct node *parent) {
    node->parent = parent;
    node->next = parent->child;
    parent->child = node;
    acquire_node_locked(parent);
}

static void remove_node_from_parent_locked(struct node* node)
{
    if (node->parent) {
        if (node->parent->child == node) {
            node->parent->child = node->parent->child->next;
        } else {
            struct node *node2;
            node2 = node->parent->child;
            while (node2->next != node)
                node2 = node2->next;
            node2->next = node->next;
        }
        release_node_locked(node->parent);
        node->parent = NULL;
        node->next = NULL;
    }
}

/* Gets the absolute path to a node into the provided buffer.
 *
 * Populates 'buf' with the path and returns the length of the path on success,
 * or returns -1 if the path is too long for the provided buffer.
 */
static ssize_t get_node_path_locked(struct node* node, char* buf, size_t bufsize) {
    const char* name;
    size_t namelen;
    if (node->graft_path) {
        name = node->graft_path;
        namelen = node->graft_pathlen;
    } else if (node->actual_name) {
        name = node->actual_name;
        namelen = node->namelen;
    } else {
        name = node->name;
        namelen = node->namelen;
    }

    if (bufsize < namelen + 1) {
        return -1;
    }

    ssize_t pathlen = 0;
    if (node->parent && node->graft_path == NULL) {
        pathlen = get_node_path_locked(node->parent, buf, bufsize - namelen - 2);
        if (pathlen < 0) {
            return -1;
        }
        buf[pathlen++] = '/';
    }

    memcpy(buf + pathlen, name, namelen + 1); /* include trailing \0 */
    return pathlen + namelen;
}

/* Finds the absolute path of a file within a given directory.
 * Performs a case-insensitive search for the file and sets the buffer to the path
 * of the first matching file.  If 'search' is zero or if no match is found, sets
 * the buffer to the path that the file would have, assuming the name were case-sensitive.
 *
 * Populates 'buf' with the path and returns the actual name (within 'buf') on success,
 * or returns NULL if the path is too long for the provided buffer.
 */
static char* find_file_within(const char* path, const char* name,
        char* buf, size_t bufsize, int search)
{
    size_t pathlen = strlen(path);
    size_t namelen = strlen(name);
    size_t childlen = pathlen + namelen + 1;
    char* actual;

    if (bufsize <= childlen) {
        return NULL;
    }

    memcpy(buf, path, pathlen);
    buf[pathlen] = '/';
    actual = buf + pathlen + 1;
    memcpy(actual, name, namelen + 1);

    if (search && access(buf, F_OK)) {
        struct dirent* entry;
        DIR* dir = opendir(path);
        if (!dir) {
            ERROR("opendir %s failed: %s\n", path, strerror(errno));
            return actual;
        }
        while ((entry = readdir(dir))) {
            if (!strcasecmp(entry->d_name, name)) {
                /* we have a match - replace the name, don't need to copy the null again */
                memcpy(actual, entry->d_name, namelen);
                break;
            }
        }
        closedir(dir);
    }
    return actual;
}

static void attr_from_stat(struct fuse_attr *attr, const struct stat *s, const struct node* node)
{
    attr->ino = node->nid;
    attr->size = s->st_size;
    attr->blocks = s->st_blocks;
    attr->atime = s->st_atime;
    attr->mtime = s->st_mtime;
    attr->ctime = s->st_ctime;
    attr->atimensec = s->st_atime_nsec;
    attr->mtimensec = s->st_mtime_nsec;
    attr->ctimensec = s->st_ctime_nsec;
    attr->mode = s->st_mode;
    attr->nlink = s->st_nlink;

    attr->uid = node->uid;
    attr->gid = node->gid;

    /* Filter requested mode based on underlying file, and
     * pass through file type. */
    int owner_mode = s->st_mode & 0700;
    int filtered_mode = node->mode & (owner_mode | (owner_mode >> 3) | (owner_mode >> 6));
    attr->mode = (attr->mode & S_IFMT) | filtered_mode;
}

static int touch(char* path, mode_t mode) {
    int fd = open(path, O_RDWR | O_CREAT | O_EXCL | O_NOFOLLOW, mode);
    if (fd == -1) {
        if (errno == EEXIST) {
            return 0;
        } else {
            ERROR("Failed to open(%s): %s\n", path, strerror(errno));
            return -1;
        }
    }
    close(fd);
    return 0;
}

static void derive_permissions_locked(struct fuse* fuse, struct node *parent,
        struct node *node) {
    appid_t appid;

    /* By default, each node inherits from its parent */
    node->perm = PERM_INHERIT;
    node->userid = parent->userid;
    node->uid = parent->uid;
    node->gid = parent->gid;
    node->mode = parent->mode;

    if (fuse->derive == DERIVE_NONE) {
        return;
    }

    /* Derive custom permissions based on parent and current node */
    switch (parent->perm) {
    case PERM_INHERIT:
        /* Already inherited above */
        break;
    case PERM_LEGACY_PRE_ROOT:
        /* Legacy internal layout places users at top level */
        node->perm = PERM_ROOT;
        node->userid = strtoul(node->name, NULL, 10);
        break;
    case PERM_ROOT:
        /* Assume masked off by default. */
        node->mode = 0770;
        if (!strcasecmp(node->name, "Android")) {
            /* App-specific directories inside; let anyone traverse */
            node->perm = PERM_ANDROID;
            node->mode = 0771;
        } else if (fuse->split_perms) {
            if (!strcasecmp(node->name, "DCIM")
                    || !strcasecmp(node->name, "Pictures")) {
                node->gid = AID_SDCARD_PICS;
            } else if (!strcasecmp(node->name, "Alarms")
                    || !strcasecmp(node->name, "Movies")
                    || !strcasecmp(node->name, "Music")
                    || !strcasecmp(node->name, "Notifications")
                    || !strcasecmp(node->name, "Podcasts")
                    || !strcasecmp(node->name, "Ringtones")) {
                node->gid = AID_SDCARD_AV;
            }
        }
        break;
    case PERM_ANDROID:
        if (!strcasecmp(node->name, "data")) {
            /* App-specific directories inside; let anyone traverse */
            node->perm = PERM_ANDROID_DATA;
            node->mode = 0771;
        } else if (!strcasecmp(node->name, "obb")) {
            /* App-specific directories inside; let anyone traverse */
            node->perm = PERM_ANDROID_OBB;
            node->mode = 0771;
            /* Single OBB directory is always shared */
            node->graft_path = fuse->obbpath;
            node->graft_pathlen = strlen(fuse->obbpath);
        } else if (!strcasecmp(node->name, "user")) {
            /* User directories must only be accessible to system, protected
             * by sdcard_all. Zygote will bind mount the appropriate user-
             * specific path. */
            node->perm = PERM_ANDROID_USER;
            node->gid = AID_SDCARD_ALL;
            node->mode = 0770;
        }
        break;
    case PERM_ANDROID_DATA:
    case PERM_ANDROID_OBB:
        appid = (appid_t) hashmapGet(fuse->package_to_appid, node->name);
        if (appid != 0) {
            node->uid = multiuser_get_uid(parent->userid, appid);
        }
        node->mode = 0770;
        break;
    case PERM_ANDROID_USER:
        /* Root of a secondary user */
        node->perm = PERM_ROOT;
        node->userid = strtoul(node->name, NULL, 10);
        node->gid = AID_SDCARD_R;
        node->mode = 0771;
        break;
    }
}

/* Return if the calling UID holds sdcard_rw. */
static bool get_caller_has_rw_locked(struct fuse* fuse, const struct fuse_in_header *hdr) {
    /* No additional permissions enforcement */
    if (fuse->derive == DERIVE_NONE) {
        return true;
    }

    appid_t appid = multiuser_get_app_id(hdr->uid);
    
    if (appid == AID_SHELL) {
      /* for mtklogger with uid, shell, grant the write permisssion to them */
      TRACE("WARNING: appid is AID_SHELL. Grant the write permission to it\n");
      return true;
    }
    else if (hashmapContainsKey(fuse->appid_with_rw, (void*) appid)) {
       return (bool)hashmapGet(fuse->appid_with_rw, appid);
    }
    else {
       TRACE("WARNING: appid=%d is NOT in packages.list. Grant the write permission to it\n", appid);
       return true;
    }
}

/* Kernel has already enforced everything we returned through
 * derive_permissions_locked(), so this is used to lock down access
 * even further, such as enforcing that apps hold sdcard_rw. */
static bool check_caller_access_to_name(struct fuse* fuse,
        const struct fuse_in_header *hdr, const struct node* parent_node,
        const char* name, int mode, bool has_rw) {
    /* Always block security-sensitive files at root */
    if (parent_node && parent_node->perm == PERM_ROOT) {
        if (!strcasecmp(name, "autorun.inf")
                || !strcasecmp(name, ".android_secure")
                || !strcasecmp(name, "android_secure")) {
            return false;
        }
    }

    /* No additional permissions enforcement */
    if (fuse->derive == DERIVE_NONE) {
        return true;
    }

    /* Root always has access; access for any other UIDs should always
     * be controlled through packages.list. */
    if (hdr->uid == 0) {
        return true;
    }

    /* If asking to write, verify that caller either owns the
     * parent or holds sdcard_rw. */
    if (mode & W_OK) {
        if (parent_node && hdr->uid == parent_node->uid) {
            return true;
        }

        return has_rw;
    }

    /* No extra permissions to enforce */
    return true;
}

static bool check_caller_access_to_node(struct fuse* fuse,
        const struct fuse_in_header *hdr, const struct node* node, int mode, bool has_rw) {
    return check_caller_access_to_name(fuse, hdr, node->parent, node->name, mode, has_rw);
}

struct node *create_node_locked(struct fuse* fuse,
        struct node *parent, const char *name, const char* actual_name)
{
    struct node *node;
    size_t namelen = strlen(name);

    node = calloc(1, sizeof(struct node));
    if (!node) {
        return NULL;
    }
    node->name = malloc(namelen + 1);
    if (!node->name) {
        free(node);
        return NULL;
    }
    memcpy(node->name, name, namelen + 1);
    if (strcmp(name, actual_name)) {
        node->actual_name = malloc(namelen + 1);
        if (!node->actual_name) {
            free(node->name);
            free(node);
            return NULL;
        }
        memcpy(node->actual_name, actual_name, namelen + 1);
    }
    node->namelen = namelen;
    node->nid = ptr_to_id(node);
    node->gen = fuse->next_generation++;

    derive_permissions_locked(fuse, parent, node);
    acquire_node_locked(node);
    add_node_to_parent_locked(node, parent);
    return node;
}

static int rename_node_locked(struct node *node, const char *name,
        const char* actual_name)
{
    size_t namelen = strlen(name);
    int need_actual_name = strcmp(name, actual_name);

    /* make the storage bigger without actually changing the name
     * in case an error occurs part way */
    if (namelen > node->namelen) {
        char* new_name = realloc(node->name, namelen + 1);
        if (!new_name) {
            return -ENOMEM;
        }
        node->name = new_name;
        if (need_actual_name && node->actual_name) {
            char* new_actual_name = realloc(node->actual_name, namelen + 1);
            if (!new_actual_name) {
                return -ENOMEM;
            }
            node->actual_name = new_actual_name;
        }
    }

    /* update the name, taking care to allocate storage before overwriting the old name */
    if (need_actual_name) {
        if (!node->actual_name) {
            node->actual_name = malloc(namelen + 1);
            if (!node->actual_name) {
                return -ENOMEM;
            }
        }
        memcpy(node->actual_name, actual_name, namelen + 1);
    } else {
        free(node->actual_name);
        node->actual_name = NULL;
    }
    memcpy(node->name, name, namelen + 1);
    node->namelen = namelen;
    return 0;
}

static struct node *lookup_node_by_id_locked(struct fuse *fuse, __u64 nid)
{
    if (nid == FUSE_ROOT_ID) {
        return &fuse->root;
    } else {
        return id_to_ptr(nid);
    }
}

static struct node* lookup_node_and_path_by_id_locked(struct fuse* fuse, __u64 nid,
        char* buf, size_t bufsize)
{
    struct node* node = lookup_node_by_id_locked(fuse, nid);
    if (node && get_node_path_locked(node, buf, bufsize) < 0) {
        node = NULL;
    }
    return node;
}

static struct node *lookup_child_by_name_locked(struct node *node, const char *name)
{
    for (node = node->child; node; node = node->next) {
        /* use exact string comparison, nodes that differ by case
         * must be considered distinct even if they refer to the same
         * underlying file as otherwise operations such as "mv x x"
         * will not work because the source and target nodes are the same. */
        if (!strcmp(name, node->name)) {
            return node;
        }
    }
    return 0;
}

static struct node* acquire_or_create_child_locked(
        struct fuse* fuse, struct node* parent,
        const char* name, const char* actual_name)
{
    struct node* child = lookup_child_by_name_locked(parent, name);
    if (child) {
        acquire_node_locked(child);
    } else {
        child = create_node_locked(fuse, parent, name, actual_name);
    }
    return child;
}

static void fuse_init(struct fuse *fuse, int fd, const char *source_path,
        gid_t write_gid, derive_t derive, bool split_perms) {
    pthread_mutex_init(&fuse->lock, NULL);

    fuse->fd = fd;
    fuse->next_generation = 0;
    fuse->derive = derive;
    fuse->split_perms = split_perms;
    fuse->write_gid = write_gid;

    memset(&fuse->root, 0, sizeof(fuse->root));
    fuse->root.nid = FUSE_ROOT_ID; /* 1 */
    fuse->root.refcount = 2;
    fuse->root.namelen = strlen(source_path);
    fuse->root.name = strdup(source_path);
    fuse->root.userid = 0;
    fuse->root.uid = AID_ROOT;

    /* Set up root node for various modes of operation */
    switch (derive) {
    case DERIVE_NONE:
        /* Traditional behavior that treats entire device as being accessible
         * to sdcard_rw, and no permissions are derived. */
        fuse->root.perm = PERM_ROOT;
        fuse->root.mode = 0775;
        fuse->root.gid = AID_SDCARD_RW;
        break;
    case DERIVE_LEGACY:
        /* Legacy behavior used to support internal multiuser layout which
         * places user_id at the top directory level, with the actual roots
         * just below that. Shared OBB path is also at top level. */
        fuse->root.perm = PERM_LEGACY_PRE_ROOT;
        fuse->root.mode = 0771;
        fuse->root.gid = AID_SDCARD_R;
        fuse->package_to_appid = hashmapCreate(256, str_hash, str_icase_equals);
        fuse->appid_with_rw = hashmapCreate(128, int_hash, int_equals);
        snprintf(fuse->obbpath, sizeof(fuse->obbpath), "%s/obb", source_path);
        fs_prepare_dir(fuse->obbpath, 0775, getuid(), getgid());
        break;
    case DERIVE_UNIFIED:
        /* Unified multiuser layout which places secondary user_id under
         * /Android/user and shared OBB path under /Android/obb. */
        fuse->root.perm = PERM_ROOT;
        fuse->root.mode = 0771;
        fuse->root.gid = AID_SDCARD_R;
        fuse->package_to_appid = hashmapCreate(256, str_hash, str_icase_equals);
        fuse->appid_with_rw = hashmapCreate(128, int_hash, int_equals);
        snprintf(fuse->obbpath, sizeof(fuse->obbpath), "%s/Android/obb", source_path);
        break;
    }
#ifdef LIMIT_SDCARD_SIZE
    struct statfs stat;
    if (statfs(fuse->root.name, &stat) < 0) {
        ERROR("get %s fs status fail \n",fuse->root.name);
		fuse->free_size =0;
    }else{
		fuse->free_size = stat.f_bfree*stat.f_bsize;
		LOG("[fuse_debug]fuse.free_size =%lld \n",fuse->free_size);
    }
#endif
}

static void fuse_status(struct fuse *fuse, __u64 unique, int err)
{
    struct fuse_out_header hdr;
    hdr.len = sizeof(hdr);
    hdr.error = err;
    hdr.unique = unique;
    write(fuse->fd, &hdr, sizeof(hdr));
}

static void fuse_reply(struct fuse *fuse, __u64 unique, void *data, int len)
{
    struct fuse_out_header hdr;
    struct iovec vec[2];
    int res;

    hdr.len = len + sizeof(hdr);
    hdr.error = 0;
    hdr.unique = unique;

    vec[0].iov_base = &hdr;
    vec[0].iov_len = sizeof(hdr);
    vec[1].iov_base = data;
    vec[1].iov_len = len;

    res = writev(fuse->fd, vec, 2);
    if (res < 0) {
        ERROR("*** REPLY FAILED *** %d\n", errno);
    }
}

static int fuse_reply_entry(struct fuse* fuse, __u64 unique,
        struct node* parent, const char* name, const char* actual_name,
        const char* path)
{
    struct node* node;
    struct fuse_entry_out out;
    struct stat s;

    if (lstat(path, &s) < 0) {
        return -errno;
    }

    pthread_mutex_lock(&fuse->lock);
    node = acquire_or_create_child_locked(fuse, parent, name, actual_name);
    if (!node) {
        pthread_mutex_unlock(&fuse->lock);
        return -ENOMEM;
    }
    memset(&out, 0, sizeof(out));
    attr_from_stat(&out.attr, &s, node);
    out.attr_valid = 10;
    out.entry_valid = 10;
    out.nodeid = node->nid;
    out.generation = node->gen;
    pthread_mutex_unlock(&fuse->lock);
    fuse_reply(fuse, unique, &out, sizeof(out));
    return NO_STATUS;
}

static int fuse_reply_attr(struct fuse* fuse, __u64 unique, const struct node* node,
        const char* path)
{
    struct fuse_attr_out out;
    struct stat s;

    if (lstat(path, &s) < 0) {
        return -errno;
    }
    memset(&out, 0, sizeof(out));
    attr_from_stat(&out.attr, &s, node);
    out.attr_valid = 10;
    fuse_reply(fuse, unique, &out, sizeof(out));
    return NO_STATUS;
}

static int handle_lookup(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header *hdr, const char* name)
{
    struct node* parent_node;
    char parent_path[PATH_MAX];
    char child_path[PATH_MAX];
    const char* actual_name;

    pthread_mutex_lock(&fuse->lock);
    parent_node = lookup_node_and_path_by_id_locked(fuse, hdr->nodeid,
            parent_path, sizeof(parent_path));
    TRACE("[%d] LOOKUP %s @ %llx (%s)\n", handler->token, name, hdr->nodeid,
        parent_node ? parent_node->name : "?");
    pthread_mutex_unlock(&fuse->lock);

    if (!parent_node || !(actual_name = find_file_within(parent_path, name,
            child_path, sizeof(child_path), 1))) {
        return -ENOENT;
    }
    if (!check_caller_access_to_name(fuse, hdr, parent_node, name, R_OK, false)) {
        return -EACCES;
    }

    return fuse_reply_entry(fuse, hdr->unique, parent_node, name, actual_name, child_path);
}

static int handle_forget(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header *hdr, const struct fuse_forget_in *req)
{
    struct node* node;

    pthread_mutex_lock(&fuse->lock);
    node = lookup_node_by_id_locked(fuse, hdr->nodeid);
    TRACE("[%d] FORGET #%lld @ %llx (%s)\n", handler->token, req->nlookup,
            hdr->nodeid, node ? node->name : "?");
    if (node) {
        __u64 n = req->nlookup;
        while (n--) {
            release_node_locked(node);
        }
    }
    pthread_mutex_unlock(&fuse->lock);
    return NO_STATUS; /* no reply */
}

static int handle_getattr(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header *hdr, const struct fuse_getattr_in *req)
{
    struct node* node;
    char path[PATH_MAX];

    pthread_mutex_lock(&fuse->lock);
    node = lookup_node_and_path_by_id_locked(fuse, hdr->nodeid, path, sizeof(path));
    TRACE("[%d] GETATTR flags=%x fh=%llx @ %llx (%s)\n", handler->token,
            req->getattr_flags, req->fh, hdr->nodeid, node ? node->name : "?");
    pthread_mutex_unlock(&fuse->lock);

    if (!node) {
        return -ENOENT;
    }
    if (!check_caller_access_to_node(fuse, hdr, node, R_OK, false)) {
        return -EACCES;
    }

    return fuse_reply_attr(fuse, hdr->unique, node, path);
}

static int handle_setattr(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header *hdr, const struct fuse_setattr_in *req)
{
    bool has_rw;
    struct node* node;
    char path[PATH_MAX];
    struct timespec times[2];

    pthread_mutex_lock(&fuse->lock);
    has_rw = get_caller_has_rw_locked(fuse, hdr);
    node = lookup_node_and_path_by_id_locked(fuse, hdr->nodeid, path, sizeof(path));
    TRACE("[%d] SETATTR fh=%llx valid=%x @ %llx (%s)\n", handler->token,
            req->fh, req->valid, hdr->nodeid, node ? node->name : "?");
    pthread_mutex_unlock(&fuse->lock);

    if (!node) {
        return -ENOENT;
    }
    if (!check_caller_access_to_node(fuse, hdr, node, W_OK, has_rw)) {
        return -EACCES;
    }

    /* XXX: incomplete implementation on purpose.
     * chmod/chown should NEVER be implemented.*/

    if ((req->valid & FATTR_SIZE) && truncate(path, req->size) < 0) {
        return -errno;
    }

    /* Handle changing atime and mtime.  If FATTR_ATIME_and FATTR_ATIME_NOW
     * are both set, then set it to the current time.  Else, set it to the
     * time specified in the request.  Same goes for mtime.  Use utimensat(2)
     * as it allows ATIME and MTIME to be changed independently, and has
     * nanosecond resolution which fuse also has.
     */
    if (req->valid & (FATTR_ATIME | FATTR_MTIME)) {
        times[0].tv_nsec = UTIME_OMIT;
        times[1].tv_nsec = UTIME_OMIT;
        if (req->valid & FATTR_ATIME) {
            if (req->valid & FATTR_ATIME_NOW) {
              times[0].tv_nsec = UTIME_NOW;
            } else {
              times[0].tv_sec = req->atime;
              times[0].tv_nsec = req->atimensec;
            }
        }
        if (req->valid & FATTR_MTIME) {
            if (req->valid & FATTR_MTIME_NOW) {
              times[1].tv_nsec = UTIME_NOW;
            } else {
              times[1].tv_sec = req->mtime;
              times[1].tv_nsec = req->mtimensec;
            }
        }
        TRACE("[%d] Calling utimensat on %s with atime %ld, mtime=%ld\n",
                handler->token, path, times[0].tv_sec, times[1].tv_sec);
        if (utimensat(-1, path, times, 0) < 0) {
            return -errno;
        }
    }
    return fuse_reply_attr(fuse, hdr->unique, node, path);
}

static int handle_mknod(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const struct fuse_mknod_in* req, const char* name)
{
    bool has_rw;
    struct node* parent_node;
    char parent_path[PATH_MAX];
    char child_path[PATH_MAX];
    const char* actual_name;

    pthread_mutex_lock(&fuse->lock);
    has_rw = get_caller_has_rw_locked(fuse, hdr);
    parent_node = lookup_node_and_path_by_id_locked(fuse, hdr->nodeid,
            parent_path, sizeof(parent_path));
    TRACE("[%d] MKNOD %s 0%o @ %llx (%s)\n", handler->token,
            name, req->mode, hdr->nodeid, parent_node ? parent_node->name : "?");
    pthread_mutex_unlock(&fuse->lock);

    if (!parent_node || !(actual_name = find_file_within(parent_path, name,
            child_path, sizeof(child_path), 1))) {
        return -ENOENT;
    }
    if (!check_caller_access_to_name(fuse, hdr, parent_node, name, W_OK, has_rw)) {
        return -EACCES;
    }
    __u32 mode = (req->mode & (~0777)) | 0664;
    if (mknod(child_path, mode, req->rdev) < 0) {
        return -errno;
    }
    return fuse_reply_entry(fuse, hdr->unique, parent_node, name, actual_name, child_path);
}

static int handle_mkdir(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const struct fuse_mkdir_in* req, const char* name)
{
    bool has_rw;
    struct node* parent_node;
    char parent_path[PATH_MAX];
    char child_path[PATH_MAX];
    const char* actual_name;
#if !defined(MTK_2SDCARD_SWAP) && defined(MTK_SHARED_SDCARD)
    int   i;
    int   name_len;
#endif
    pthread_mutex_lock(&fuse->lock);
    has_rw = get_caller_has_rw_locked(fuse, hdr);
    parent_node = lookup_node_and_path_by_id_locked(fuse, hdr->nodeid,
            parent_path, sizeof(parent_path));
    TRACE("[%d] MKDIR %s 0%o @ %llx (%s)\n", handler->token,
            name, req->mode, hdr->nodeid, parent_node ? parent_node->name : "?");
    pthread_mutex_unlock(&fuse->lock);

    if (!parent_node || !(actual_name = find_file_within(parent_path, name,
            child_path, sizeof(child_path), 1))) {
        return -ENOENT;
    }
    if (!check_caller_access_to_name(fuse, hdr, parent_node, name, W_OK, has_rw)) {
        return -EACCES;
    }
    __u32 mode = (req->mode & (~0777)) | 0775;
    if (mkdir(child_path, mode) < 0) {
        return -errno;
    }

    /* When creating /Android/data and /Android/obb, mark them as .nomedia */
    if (parent_node->perm == PERM_ANDROID && !strcasecmp(name, "data")) {
        char nomedia[PATH_MAX];
        snprintf(nomedia, PATH_MAX, "%s/.nomedia", child_path);
        if (touch(nomedia, 0664) != 0) {
            ERROR("Failed to touch(%s): %s\n", nomedia, strerror(errno));
            return -ENOENT;
        }
    }
    if (parent_node->perm == PERM_ANDROID && !strcasecmp(name, "obb")) {
        char nomedia[PATH_MAX];
        snprintf(nomedia, PATH_MAX, "%s/.nomedia", fuse->obbpath);
        if (touch(nomedia, 0664) != 0) {
            ERROR("Failed to touch(%s): %s\n", nomedia, strerror(errno));
            return -ENOENT;
        }
    }


#if !defined(MTK_2SDCARD_SWAP) && defined(MTK_SHARED_SDCARD)
    name_len = strlen(name);
    if(!strcmp(fuse->root.name, parent_path)){
      const char* emulatedStorageSource = getenv("EMULATED_STORAGE_SOURCE");
      const char* emulatedStorageTarget = getenv("EMULATED_STORAGE_TARGET");
      char source_path[PATH_MAX];
      char target_path[PATH_MAX];
      
      for(i = 0 ; i < name_len ; i++){
        if(name[i] > '9' || name[i] < '0')
            break;
      }
      if(i == name_len){
        snprintf(source_path, PATH_MAX, "%s/%s", emulatedStorageSource, name);
        snprintf(target_path, PATH_MAX, "%s/%s", emulatedStorageTarget, name);
        
        /* it means the path is /data/media/[digital], it must be uid. So create a symlink in /storage/emulated/<uid> to /mnt/shell/emulated/<uid> for native process */
        if (symlink(source_path, target_path)) {
          LOG("Failed to symlink: %s -> %s ,%s(%d)", source_path, target_path, strerror(errno), errno);            
        }
      }
    }
#endif
    return fuse_reply_entry(fuse, hdr->unique, parent_node, name, actual_name, child_path);
}

static int handle_unlink(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const char* name)
{
    bool has_rw;
    struct node* parent_node;
    char parent_path[PATH_MAX];
    char child_path[PATH_MAX];

    pthread_mutex_lock(&fuse->lock);
    has_rw = get_caller_has_rw_locked(fuse, hdr);
    parent_node = lookup_node_and_path_by_id_locked(fuse, hdr->nodeid,
            parent_path, sizeof(parent_path));
    TRACE("[%d] UNLINK %s @ %llx (%s)\n", handler->token,
            name, hdr->nodeid, parent_node ? parent_node->name : "?");
    pthread_mutex_unlock(&fuse->lock);

    if (!parent_node || !find_file_within(parent_path, name,
            child_path, sizeof(child_path), 1)) {
        return -ENOENT;
    }
    if (!check_caller_access_to_name(fuse, hdr, parent_node, name, W_OK, has_rw)) {
        return -EACCES;
    }
    if (unlink(child_path) < 0) {
        return -errno;
    }
    return 0;
}

static int handle_rmdir(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const char* name)
{
    bool has_rw;
    struct node* parent_node;
    char parent_path[PATH_MAX];
    char child_path[PATH_MAX];

    pthread_mutex_lock(&fuse->lock);
    has_rw = get_caller_has_rw_locked(fuse, hdr);
    parent_node = lookup_node_and_path_by_id_locked(fuse, hdr->nodeid,
            parent_path, sizeof(parent_path));
    TRACE("[%d] RMDIR %s @ %llx (%s)\n", handler->token,
            name, hdr->nodeid, parent_node ? parent_node->name : "?");
    pthread_mutex_unlock(&fuse->lock);

    if (!parent_node || !find_file_within(parent_path, name,
            child_path, sizeof(child_path), 1)) {
        return -ENOENT;
    }
    if (!check_caller_access_to_name(fuse, hdr, parent_node, name, W_OK, has_rw)) {
        return -EACCES;
    }
    if (rmdir(child_path) < 0) {
        return -errno;
    }
    return 0;
}

static int handle_rename(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const struct fuse_rename_in* req,
        const char* old_name, const char* new_name)
{
    bool has_rw;
    struct node* old_parent_node;
    struct node* new_parent_node;
    struct node* child_node;
    char old_parent_path[PATH_MAX];
    char new_parent_path[PATH_MAX];
    char old_child_path[PATH_MAX];
    char new_child_path[PATH_MAX];
    const char* new_actual_name;
    int res;

    pthread_mutex_lock(&fuse->lock);
    has_rw = get_caller_has_rw_locked(fuse, hdr);
    old_parent_node = lookup_node_and_path_by_id_locked(fuse, hdr->nodeid,
            old_parent_path, sizeof(old_parent_path));
    new_parent_node = lookup_node_and_path_by_id_locked(fuse, req->newdir,
            new_parent_path, sizeof(new_parent_path));
    TRACE("[%d] RENAME %s->%s @ %llx (%s) -> %llx (%s)\n", handler->token,
            old_name, new_name,
            hdr->nodeid, old_parent_node ? old_parent_node->name : "?",
            req->newdir, new_parent_node ? new_parent_node->name : "?");
    if (!old_parent_node || !new_parent_node) {
        res = -ENOENT;
        goto lookup_error;
    }
    if (!check_caller_access_to_name(fuse, hdr, old_parent_node, old_name, W_OK, has_rw)) {
        res = -EACCES;
        goto lookup_error;
    }
    if (!check_caller_access_to_name(fuse, hdr, new_parent_node, new_name, W_OK, has_rw)) {
        res = -EACCES;
        goto lookup_error;
    }
    child_node = lookup_child_by_name_locked(old_parent_node, old_name);
    if (!child_node || get_node_path_locked(child_node,
            old_child_path, sizeof(old_child_path)) < 0) {
        res = -ENOENT;
        goto lookup_error;
    }
    acquire_node_locked(child_node);
    pthread_mutex_unlock(&fuse->lock);

    /* Special case for renaming a file where destination is same path
     * differing only by case.  In this case we don't want to look for a case
     * insensitive match.  This allows commands like "mv foo FOO" to work as expected.
     */
    int search = old_parent_node != new_parent_node
            || strcasecmp(old_name, new_name);
    if (!(new_actual_name = find_file_within(new_parent_path, new_name,
            new_child_path, sizeof(new_child_path), search))) {
        res = -ENOENT;
        goto io_error;
    }

    TRACE("[%d] RENAME %s->%s\n", handler->token, old_child_path, new_child_path);
    res = rename(old_child_path, new_child_path);
    if (res < 0) {
        res = -errno;
        goto io_error;
    }

    pthread_mutex_lock(&fuse->lock);
    res = rename_node_locked(child_node, new_name, new_actual_name);
    if (!res) {
        remove_node_from_parent_locked(child_node);
        add_node_to_parent_locked(child_node, new_parent_node);
    }
    goto done;

io_error:
    pthread_mutex_lock(&fuse->lock);
done:
    release_node_locked(child_node);
lookup_error:
    pthread_mutex_unlock(&fuse->lock);
    return res;
}

static int open_flags_to_access_mode(int open_flags) {
    if ((open_flags & O_ACCMODE) == O_RDONLY) {
        return R_OK;
    } else if ((open_flags & O_ACCMODE) == O_WRONLY) {
        return W_OK;
    } else {
        /* Probably O_RDRW, but treat as default to be safe */
        return R_OK | W_OK;
    }
}

static int handle_open(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const struct fuse_open_in* req)
{
    bool has_rw;
    struct node* node;
    char path[PATH_MAX];
    struct fuse_open_out out;
    struct handle *h;

    pthread_mutex_lock(&fuse->lock);
    has_rw = get_caller_has_rw_locked(fuse, hdr);
    node = lookup_node_and_path_by_id_locked(fuse, hdr->nodeid, path, sizeof(path));
    TRACE("[%d] OPEN 0%o @ %llx (%s)\n", handler->token,
            req->flags, hdr->nodeid, node ? node->name : "?");
    pthread_mutex_unlock(&fuse->lock);

    if (!node) {
        return -ENOENT;
    }
    if (!check_caller_access_to_node(fuse, hdr, node,
            open_flags_to_access_mode(req->flags), has_rw)) {
        return -EACCES;
    }
    h = malloc(sizeof(*h));
    if (!h) {
        return -ENOMEM;
    }
    TRACE("[%d] OPEN %s\n", handler->token, path);
    h->fd = open(path, req->flags);
    if (h->fd < 0) {
        free(h);
        return -errno;
    }
    out.fh = ptr_to_id(h);
    out.open_flags = 0;
    out.padding = 0;
    fuse_reply(fuse, hdr->unique, &out, sizeof(out));
    return NO_STATUS;
}

static int handle_read(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const struct fuse_read_in* req)
{
    struct handle *h = id_to_ptr(req->fh);
    __u64 unique = hdr->unique;
    __u32 size = req->size;
    __u64 offset = req->offset;
    int res;

    /* Don't access any other fields of hdr or req beyond this point, the read buffer
     * overlaps the request buffer and will clobber data in the request.  This
     * saves us 128KB per request handler thread at the cost of this scary comment. */

    TRACE("[%d] READ %p(%d) %u@%llu\n", handler->token,
            h, h->fd, size, offset);
    if (size > sizeof(handler->read_buffer)) {
        return -EINVAL;
    }
    res = pread64(h->fd, handler->read_buffer, size, offset);
    if (res < 0) {
        return -errno;
    }
    fuse_reply(fuse, unique, handler->read_buffer, res);
    return NO_STATUS;
}

static int handle_write(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const struct fuse_write_in* req,
        const void* buffer)
{
    struct fuse_write_out out;
    struct handle *h = id_to_ptr(req->fh);
    int res;

    TRACE("[%d] WRITE %p(%d) %u@%llu\n", handler->token,
            h, h->fd, req->size, req->offset);
#ifdef LIMIT_SDCARD_SIZE
	if(!strncmp( fuse->root.name,"/data/media",fuse->root.namelen)){
		//LOG("[fuse_debug] fuse.free_size 1 = %lld,root name =%s \n",fuse->free_size,fuse->root.name);
		pthread_mutex_lock(&fuse->lock);
		fuse->free_size -=req->size;
		pthread_mutex_unlock(&fuse->lock);
		//LOG("[fuse_debug] fuse.free_size 2 = %lld\n",fuse->free_size);
		if(fuse->free_size <= internal_sdcard_free_size_threshold){
			struct statfs stat;
    		if (statfs(fuse->root.name, &stat) < 0) {
		        ERROR("get %s fs status fail \n",fuse->root.name);
				fuse->free_size =0;
				return -errno;
    		}else{
    			pthread_mutex_lock(&fuse->lock);
				fuse->free_size = stat.f_bfree*stat.f_bsize;
				pthread_mutex_unlock(&fuse->lock);
    		}
			errno = ENOSPC;
			LOG("[fuse_debug] Oops fuse.free_size = %lld, less than internal sdcard free size threshold ,no space for write!!!!\n",fuse->free_size);
			return -errno;
		}
	}
#endif

    res = pwrite64(h->fd, buffer, req->size, req->offset);
    if (res < 0) {
        return -errno;
    }
    out.size = res;
    fuse_reply(fuse, hdr->unique, &out, sizeof(out));
    return NO_STATUS;
}

static int handle_statfs(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr)
{
    char path[PATH_MAX];
    struct statfs stat;
    struct fuse_statfs_out out;
    int res;

    pthread_mutex_lock(&fuse->lock);
    TRACE("[%d] STATFS\n", handler->token);
    res = get_node_path_locked(&fuse->root, path, sizeof(path));
    pthread_mutex_unlock(&fuse->lock);
    if (res < 0) {
        return -ENOENT;
    }
    if (statfs(fuse->root.name, &stat) < 0) {
        return -errno;
    }
    memset(&out, 0, sizeof(out));
    out.st.blocks = stat.f_blocks;
    out.st.bfree = stat.f_bfree;
    out.st.bavail = stat.f_bavail;
    out.st.files = stat.f_files;
    out.st.ffree = stat.f_ffree;
    out.st.bsize = stat.f_bsize;
    out.st.namelen = stat.f_namelen;
    out.st.frsize = stat.f_frsize;
#ifdef LIMIT_SDCARD_SIZE
	//LOG("[fuse_debug] root name =%s \n",fuse->root.name);
	if(!strncmp( fuse->root.name,"/data/media",fuse->root.namelen)){
		out.st.blocks -=internal_sdcard_free_size_threshold/out.st.bsize;
		//LOG("[fuse_debug] out.st.blocks =%lld \n",out.st.blocks);
		if(out.st.bfree <= internal_sdcard_free_size_threshold /out.st.bsize){
			out.st.bfree =0;
		}else{
			out.st.bfree -=internal_sdcard_free_size_threshold /out.st.bsize;			
		}
		//LOG("[fuse_debug] out.st.bfree =%lld \n",out.st.bfree);

		if(out.st.bavail <= internal_sdcard_free_size_threshold /out.st.bsize){
			out.st.bavail =0;
		}else{
			out.st.bavail -=internal_sdcard_free_size_threshold /out.st.bsize;
		}
		//LOG("[fuse_debug] out.st.bavail =%lld \n",out.st.bavail);
	}
#endif
	fuse_reply(fuse, hdr->unique, &out, sizeof(out));
    return NO_STATUS;
}

static int handle_release(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const struct fuse_release_in* req)
{
    struct handle *h = id_to_ptr(req->fh);

    TRACE("[%d] RELEASE %p(%d)\n", handler->token, h, h->fd);
    close(h->fd);
    free(h);
    return 0;
}

static int handle_fsync(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const struct fuse_fsync_in* req)
{
    int is_data_sync = req->fsync_flags & 1;
    struct handle *h = id_to_ptr(req->fh);
    int res;

    TRACE("[%d] FSYNC %p(%d) is_data_sync=%d\n", handler->token,
            h, h->fd, is_data_sync);
    res = is_data_sync ? fdatasync(h->fd) : fsync(h->fd);
    if (res < 0) {
        return -errno;
    }
    return 0;
}

static int handle_flush(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr)
{
    TRACE("[%d] FLUSH\n", handler->token);
    return 0;
}

static int handle_opendir(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const struct fuse_open_in* req)
{
    struct node* node;
    char path[PATH_MAX];
    struct fuse_open_out out;
    struct dirhandle *h;

    pthread_mutex_lock(&fuse->lock);
    node = lookup_node_and_path_by_id_locked(fuse, hdr->nodeid, path, sizeof(path));
    TRACE("[%d] OPENDIR @ %llx (%s)\n", handler->token,
            hdr->nodeid, node ? node->name : "?");
    pthread_mutex_unlock(&fuse->lock);

    if (!node) {
        return -ENOENT;
    }
    if (!check_caller_access_to_node(fuse, hdr, node, R_OK, false)) {
        return -EACCES;
    }
    h = malloc(sizeof(*h));
    if (!h) {
        return -ENOMEM;
    }
    TRACE("[%d] OPENDIR %s\n", handler->token, path);
    h->d = opendir(path);
    if (!h->d) {
        free(h);
        return -errno;
    }
    out.fh = ptr_to_id(h);
    out.open_flags = 0;
    out.padding = 0;
    fuse_reply(fuse, hdr->unique, &out, sizeof(out));
    return NO_STATUS;
}

static int handle_readdir(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const struct fuse_read_in* req)
{
    char buffer[8192];
    struct fuse_dirent *fde = (struct fuse_dirent*) buffer;
    struct dirent *de;
    struct dirhandle *h = id_to_ptr(req->fh);

    TRACE("[%d] READDIR %p\n", handler->token, h);
    if (req->offset == 0) {
        /* rewinddir() might have been called above us, so rewind here too */
        TRACE("[%d] calling rewinddir()\n", handler->token);
        rewinddir(h->d);
    }
    /* cts 4.4_r1 has test cases for read/write file on /storage/sdcard0, /storage/sdcard1 and list all folder/file under /storage/sdcard0, /storage/sdcard1 to delete.
       CTS test case: run cts -c com.android.cts.appsecurity.AppSecurityTests -m testExternalStorageWrite
       .android_secure is bindmount on /mnt/secure/asec, which cannot be deleted and is protected by handle_unlink()->check_caller_access_to_name()
       We decide let other user space process not aware of .android_secure existence
    */
    do{
      de = readdir(h->d);
      if (!de) {
        return 0;
      }
    }while(!strcasecmp(de->d_name, ".android_secure")|| !strcasecmp(de->d_name, "android_secure"));
    fde->ino = FUSE_UNKNOWN_INO;
    /* increment the offset so we can detect when rewinddir() seeks back to the beginning */
    fde->off = req->offset + 1;
    fde->type = de->d_type;
    fde->namelen = strlen(de->d_name);
    memcpy(fde->name, de->d_name, fde->namelen + 1);
    fuse_reply(fuse, hdr->unique, fde,
            FUSE_DIRENT_ALIGN(sizeof(struct fuse_dirent) + fde->namelen));
    return NO_STATUS;
}

static int handle_releasedir(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const struct fuse_release_in* req)
{
    struct dirhandle *h = id_to_ptr(req->fh);

    TRACE("[%d] RELEASEDIR %p\n", handler->token, h);
    closedir(h->d);
    free(h);
    return 0;
}

static int handle_init(struct fuse* fuse, struct fuse_handler* handler,
        const struct fuse_in_header* hdr, const struct fuse_init_in* req)
{
    struct fuse_init_out out;

    TRACE("[%d] INIT ver=%d.%d maxread=%d flags=%x\n",
            handler->token, req->major, req->minor, req->max_readahead, req->flags);
    out.major = FUSE_KERNEL_VERSION;
    out.minor = MTK_FUSE_KERNEL_MINOR_VERSION;
    out.max_readahead = req->max_readahead;
    out.flags = FUSE_ATOMIC_O_TRUNC | FUSE_BIG_WRITES;
    out.max_background = 32;
    out.congestion_threshold = 32;
    out.max_write = MAX_WRITE;
    fuse_reply(fuse, hdr->unique, &out, sizeof(out));
    return NO_STATUS;
}

static int handle_ioctl(struct fuse* fuse, struct fuse_handler* handler,
                        const struct fuse_in_header* hdr, const struct fuse_ioctl_in* req)
{
    struct fuse_ioctl_out out = {0}; 
    char path[PATH_MAX];
    int res;
    pthread_mutex_lock(&fuse->lock);
    TRACE("[%d] IOCTL\n", handler->token);
    res  = get_node_path_locked(&fuse->root, path, sizeof(path)); 
    pthread_mutex_unlock(&fuse->lock);
    if(res < 0) 
        return -ENOENT;

    switch(req->cmd){
        case VFAT_IOCTL_GET_VOLUME_ID:
        if(!strcmp(fuse->root.name, path)){
            out.result = MTK_SHARE_SD_MAGIC_VOLUME_ID;
            fuse_reply(fuse, hdr->unique, &out, sizeof(out));
            return 0; 
        }
    }
    return 0;
}

static int handle_fuse_request(struct fuse *fuse, struct fuse_handler* handler,
        const struct fuse_in_header *hdr, const void *data, size_t data_len)
{
    switch (hdr->opcode) {
    case FUSE_LOOKUP: { /* bytez[] -> entry_out */
        const char* name = data;
        return handle_lookup(fuse, handler, hdr, name);
    }

    case FUSE_FORGET: {
        const struct fuse_forget_in *req = data;
        return handle_forget(fuse, handler, hdr, req);
    }

    case FUSE_GETATTR: { /* getattr_in -> attr_out */
        const struct fuse_getattr_in *req = data;
        return handle_getattr(fuse, handler, hdr, req);
    }

    case FUSE_SETATTR: { /* setattr_in -> attr_out */
        const struct fuse_setattr_in *req = data;
        return handle_setattr(fuse, handler, hdr, req);
    }

//    case FUSE_READLINK:
//    case FUSE_SYMLINK:
    case FUSE_MKNOD: { /* mknod_in, bytez[] -> entry_out */
        const struct fuse_mknod_in *req = data;
        const char *name = ((const char*) data) + sizeof(*req);
        return handle_mknod(fuse, handler, hdr, req, name);
    }

    case FUSE_MKDIR: { /* mkdir_in, bytez[] -> entry_out */
        const struct fuse_mkdir_in *req = data;
        const char *name = ((const char*) data) + sizeof(*req);
        return handle_mkdir(fuse, handler, hdr, req, name);
    }

    case FUSE_UNLINK: { /* bytez[] -> */
        const char* name = data;
        return handle_unlink(fuse, handler, hdr, name);
    }

    case FUSE_RMDIR: { /* bytez[] -> */
        const char* name = data;
        return handle_rmdir(fuse, handler, hdr, name);
    }

    case FUSE_RENAME: { /* rename_in, oldname, newname ->  */
        const struct fuse_rename_in *req = data;
        const char *old_name = ((const char*) data) + sizeof(*req);
        const char *new_name = old_name + strlen(old_name) + 1;
        return handle_rename(fuse, handler, hdr, req, old_name, new_name);
    }

//    case FUSE_LINK:
    case FUSE_OPEN: { /* open_in -> open_out */
        const struct fuse_open_in *req = data;
        return handle_open(fuse, handler, hdr, req);
    }

    case FUSE_READ: { /* read_in -> byte[] */
        const struct fuse_read_in *req = data;
        return handle_read(fuse, handler, hdr, req);
    }

    case FUSE_WRITE: { /* write_in, byte[write_in.size] -> write_out */
        const struct fuse_write_in *req = data;
        const void* buffer = (const __u8*)data + sizeof(*req);
        return handle_write(fuse, handler, hdr, req, buffer);
    }

    case FUSE_STATFS: { /* getattr_in -> attr_out */
        return handle_statfs(fuse, handler, hdr);
    }

    case FUSE_RELEASE: { /* release_in -> */
        const struct fuse_release_in *req = data;
        return handle_release(fuse, handler, hdr, req);
    }

    case FUSE_FSYNC: {
        const struct fuse_fsync_in *req = data;
        return handle_fsync(fuse, handler, hdr, req);
    }

//    case FUSE_SETXATTR:
//    case FUSE_GETXATTR:
//    case FUSE_LISTXATTR:
//    case FUSE_REMOVEXATTR:
    case FUSE_FLUSH: {
        return handle_flush(fuse, handler, hdr);
    }

    case FUSE_OPENDIR: { /* open_in -> open_out */
        const struct fuse_open_in *req = data;
        return handle_opendir(fuse, handler, hdr, req);
    }

    case FUSE_READDIR: {
        const struct fuse_read_in *req = data;
        return handle_readdir(fuse, handler, hdr, req);
    }

    case FUSE_RELEASEDIR: { /* release_in -> */
        const struct fuse_release_in *req = data;
        return handle_releasedir(fuse, handler, hdr, req);
    }

//    case FUSE_FSYNCDIR:
    case FUSE_INIT: { /* init_in -> init_out */
        const struct fuse_init_in *req = data;
        return handle_init(fuse, handler, hdr, req);
    }

    case FUSE_IOCTL: {
        const struct fuse_ioctl_in *req = data;
        return handle_ioctl(fuse, handler, hdr, req); 
    }   
    
    default: {
        TRACE("[%d] NOTIMPL op=%d uniq=%llx nid=%llx\n",
                handler->token, hdr->opcode, hdr->unique, hdr->nodeid);
        return -ENOSYS;
    }
    }
}

static void handle_fuse_requests(struct fuse_handler* handler)
{
    struct fuse* fuse = handler->fuse;
    for (;;) {
        ssize_t len = read(fuse->fd,
                handler->request_buffer, sizeof(handler->request_buffer));
        if (len < 0) {
            if (errno != EINTR) {
                ERROR("[%d] handle_fuse_requests: errno=%d\n", handler->token, errno);
            }
            continue;
        }

        if ((size_t)len < sizeof(struct fuse_in_header)) {
            ERROR("[%d] request too short: len=%zu\n", handler->token, (size_t)len);
            continue;
        }

        const struct fuse_in_header *hdr = (void*)handler->request_buffer;
        if (hdr->len != (size_t)len) {
            ERROR("[%d] malformed header: len=%zu, hdr->len=%u\n",
                    handler->token, (size_t)len, hdr->len);
            continue;
        }

        const void *data = handler->request_buffer + sizeof(struct fuse_in_header);
        size_t data_len = len - sizeof(struct fuse_in_header);
        __u64 unique = hdr->unique;
        int res = handle_fuse_request(fuse, handler, hdr, data, data_len);

        /* We do not access the request again after this point because the underlying
         * buffer storage may have been reused while processing the request. */

        if (res != NO_STATUS) {
            if (res) {
                TRACE("[%d] ERROR %d\n", handler->token, res);
            }
            fuse_status(fuse, unique, res);
        }
    }
}

static void* start_handler(void* data)
{
    struct fuse_handler* handler = data;
    handle_fuse_requests(handler);
    return NULL;
}

static bool remove_str_to_int(void *key, void *value, void *context) {
    Hashmap* map = context;
    hashmapRemove(map, key);
    free(key);
    return true;
}

static bool remove_int_to_null(void *key, void *value, void *context) {
    Hashmap* map = context;
    hashmapRemove(map, key);
    return true;
}

static int read_package_list(struct fuse *fuse) {
    pthread_mutex_lock(&fuse->lock);

    hashmapForEach(fuse->package_to_appid, remove_str_to_int, fuse->package_to_appid);
    hashmapForEach(fuse->appid_with_rw, remove_int_to_null, fuse->appid_with_rw);

    FILE* file = fopen(kPackagesListFile, "r");
    if (!file) {
        ERROR("failed to open package list: %s\n", strerror(errno));
        pthread_mutex_unlock(&fuse->lock);
        return -1;
    }

    char buf[512];
    bool is_found = false;
    while (fgets(buf, sizeof(buf), file) != NULL) {
        char package_name[512];
        int appid;
        char gids[512];

        is_found = false;
        if (sscanf(buf, "%s %d %*d %*s %*s %s", package_name, &appid, gids) == 3) {
            char* package_name_dup = strdup(package_name);
            hashmapPut(fuse->package_to_appid, package_name_dup, (void*) appid);

            char* token = strtok(gids, ",");
            while (token != NULL) {
                if (strtoul(token, NULL, 10) == fuse->write_gid) {
                    hashmapPut(fuse->appid_with_rw, (void*) appid, (void*) 1);
                    is_found = true;
                    break;
                }
                token = strtok(NULL, ",");
            }
            if (is_found == false) {
                if (!hashmapContainsKey(fuse->appid_with_rw, (void*) appid)){
                    hashmapPut(fuse->appid_with_rw, (void*) appid, (void*) 0);
                }
            }
        }
    }

    TRACE("read_package_list: found %d packages, %d with write_gid\n",
            hashmapSize(fuse->package_to_appid),
            hashmapSize(fuse->appid_with_rw));
    fclose(file);
    pthread_mutex_unlock(&fuse->lock);
    return 0;
}

static void watch_package_list(struct fuse* fuse) {
    struct inotify_event *event;
    char event_buf[512];

    int nfd = inotify_init();
    if (nfd < 0) {
        ERROR("inotify_init failed: %s\n", strerror(errno));
        return;
    }

    bool active = false;
    while (1) {
        if (!active) {
            int res = inotify_add_watch(nfd, kPackagesListFile, IN_DELETE_SELF);
            if (res == -1) {
                if (errno == ENOENT || errno == EACCES) {
                    /* Framework may not have created yet, sleep and retry */
                    ERROR("missing packages.list; retrying\n");
                    sleep(3);
                    continue;
                } else {
                    ERROR("inotify_add_watch failed: %s\n", strerror(errno));
                    return;
                }
            }

            /* Watch above will tell us about any future changes, so
             * read the current state. */
            if (read_package_list(fuse) == -1) {
                ERROR("read_package_list failed: %s\n", strerror(errno));
                return;
            }
            active = true;
        }

        int event_pos = 0;
        int res = read(nfd, event_buf, sizeof(event_buf));
        if (res < (int) sizeof(*event)) {
            if (errno == EINTR)
                continue;
            ERROR("failed to read inotify event: %s\n", strerror(errno));
            return;
        }

        while (res >= (int) sizeof(*event)) {
            int event_size;
            event = (struct inotify_event *) (event_buf + event_pos);

            TRACE("inotify event: %08x\n", event->mask);
            if ((event->mask & IN_IGNORED) == IN_IGNORED) {
                /* Previously watched file was deleted, probably due to move
                 * that swapped in new data; re-arm the watch and read. */
                active = false;
            }

            event_size = sizeof(*event) + event->len;
            res -= event_size;
            event_pos += event_size;
        }
    }
}

static int ignite_fuse(struct fuse* fuse, int num_threads)
{
    struct fuse_handler* handlers;
    int i;

    handlers = malloc(num_threads * sizeof(struct fuse_handler));
    if (!handlers) {
        ERROR("cannot allocate storage for threads\n");
        return -ENOMEM;
    }

    for (i = 0; i < num_threads; i++) {
        handlers[i].fuse = fuse;
        handlers[i].token = i;
    }

    /* When deriving permissions, this thread is used to process inotify events,
     * otherwise it becomes one of the FUSE handlers. */
    i = (fuse->derive == DERIVE_NONE) ? 1 : 0;
    for (; i < num_threads; i++) {
        pthread_t thread;
        int res = pthread_create(&thread, NULL, start_handler, &handlers[i]);
        if (res) {
            ERROR("failed to start thread #%d, error=%d\n", i, res);
            goto quit;
        }
    }

    if (fuse->derive == DERIVE_NONE) {
        handle_fuse_requests(&handlers[0]);
    } else {
        watch_package_list(fuse);
    }

    ERROR("terminated prematurely\n");

    /* don't bother killing all of the other threads or freeing anything,
     * should never get here anyhow */
quit:
    exit(1);
}

static int usage()
{
    ERROR("usage: sdcard [OPTIONS] <source_path> <dest_path>\n"
            "    -u: specify UID to run as\n"
            "    -g: specify GID to run as\n"
            "    -w: specify GID required to write (default sdcard_rw, requires -d or -l)\n"
            "    -t: specify number of threads to use (default %d)\n"
            "    -d: derive file permissions based on path\n"
            "    -l: derive file permissions based on legacy internal layout\n"
            "    -s: split derived permissions for pics, av\n"
            "\n", DEFAULT_NUM_THREADS);
    return 1;
}

static int run(const char* source_path, const char* dest_path, uid_t uid,
        gid_t gid, gid_t write_gid, int num_threads, derive_t derive,
        bool split_perms) {
    int fd;
    char opts[256];
    int res;
    struct fuse fuse;

    /* cleanup from previous instance, if necessary */
    umount2(dest_path, 2);

    fd = open("/dev/fuse", O_RDWR);
    if (fd < 0){
        ERROR("cannot open fuse device: %s\n", strerror(errno));
        return -1;
    }

    snprintf(opts, sizeof(opts),
            "fd=%i,rootmode=40000,default_permissions,allow_other,user_id=%d,group_id=%d",
            fd, uid, gid);

    res = mount("/dev/fuse", dest_path, "fuse", MS_NOSUID | MS_NODEV, opts);
    if (res < 0) {
        ERROR("cannot mount fuse filesystem: %s\n", strerror(errno));
        goto error;
    }

    res = setgroups(sizeof(kGroups) / sizeof(kGroups[0]), kGroups);
    if (res < 0) {
        ERROR("cannot setgroups: %s\n", strerror(errno));
        goto error;
    }

    res = setgid(gid);
    if (res < 0) {
        ERROR("cannot setgid: %s\n", strerror(errno));
        goto error;
    }

    res = setuid(uid);
    if (res < 0) {
        ERROR("cannot setuid: %s\n", strerror(errno));
        goto error;
    }

    fuse_init(&fuse, fd, source_path, write_gid, derive, split_perms);

    umask(0);
#if !defined(MTK_2SDCARD_SWAP) && defined(MTK_SHARED_SDCARD)
{
    DIR *d;
    struct dirent *de;
    struct stat st;
    
    d = opendir(fuse.root.name);
    if(d == 0) {
        LOG("Cannot open '%s': %s\n", fuse.root.name, strerror(errno));
        return -1;
    }
    
    while((de = readdir(d))) {
        char stat_path[PATH_MAX];
        char *name = de->d_name;
    
        if(name[0] == '.') {
            if(name[1] == 0) continue;
            if((name[1] == '.') && (name[2] == 0)) continue;
        }
    
        /*
         * We could use d_type if HAVE_DIRENT_D_TYPE is defined, but reiserfs
         * always returns DT_UNKNOWN, so we just use stat() for all cases.
         */
        if (strlen(fuse.root.name) + strlen(de->d_name) + 1 > sizeof(stat_path))
            continue;
        snprintf(stat_path, PATH_MAX, "%s/%s",fuse.root.name,de->d_name);
        stat(stat_path, &st);
    
        if (S_ISDIR(st.st_mode)) {
          int name_len = strlen(de->d_name);
          int i;
          const char* emulatedStorageSource = getenv("EMULATED_STORAGE_SOURCE");
          const char* emulatedStorageTarget = getenv("EMULATED_STORAGE_TARGET");
          char source_path[PATH_MAX];
          char target_path[PATH_MAX];
          
          /* Create symlink for original exist uid folder */
          for(i = 0 ; i < name_len ; i++){
            if(de->d_name[i] > '9' || de->d_name[i] < '0')
                break;
          }
          if(i == name_len){
            snprintf(source_path, PATH_MAX, "%s/%s", emulatedStorageSource, de->d_name);
            snprintf(target_path, PATH_MAX, "%s/%s", emulatedStorageTarget, de->d_name);
            
            /* it means the path is /data/media/[digital], it must be uid. So create a symlink in /storage/emulated/<uid> to /mnt/shell/emulated/<uid> for native process */
            if (symlink(source_path, target_path)) {
              LOG("Failed to symlink: %s -> %s ,%s(%d)", source_path, target_path, strerror(errno), errno);            
            }
          }
        } else {
          continue;
        }
    }
    
    closedir(d);
}   
#endif
    res = ignite_fuse(&fuse, num_threads);

    /* we do not attempt to umount the file system here because we are no longer
     * running as the root user */

error:
    close(fd);
    return res;
}

typedef enum {
    NORMAL_BOOT = 0,
    META_BOOT = 1,
    RECOVERY_BOOT = 2,    
    SW_REBOOT = 3,
    FACTORY_BOOT = 4,
    ADVMETA_BOOT = 5,
    ATE_FACTORY_BOOT = 6,
    ALARM_BOOT = 7,
    UNKNOWN_BOOT
} BOOT_MODE;
static int get_boot_mode(void)
{
  int fd;
  size_t s;
  char boot_mode[2];
  memset(boot_mode, 0x00, sizeof(boot_mode));

  fd = open("/sys/class/BOOT/BOOT/boot/boot_mode", O_RDWR);
  if (fd < 0)
  {
    printf("fail to open: %s\n", "/sys/class/BOOT/BOOT/boot/boot_mode");
    return 0;
  }

  s = read(fd, (void *)boot_mode, sizeof(char));
  close(fd);

  if(s <= 0)
  {
    ERROR("could not read boot mode sys file\n");
    return 0;
  }

  return atoi(boot_mode);
}

static char *replace(const char *src, char *token, char *target) {
    static char buffer[1024];
    char *pch;
    if( !(pch = strstr(src, token)))
    {
        strcpy(buffer, src);
        return buffer;
    }
    strncpy( buffer, src, pch-src);
    buffer[pch-src]=0;
    sprintf( buffer+(pch-src), "%s%s", target, pch+strlen(token));
    return buffer;

}
#ifdef LIMIT_SDCARD_SIZE
static int get_env_value(char * name,char * value,unsigned int value_max_len){
	struct env_ioctl env_ioctl_obj;
	int env_fd;
	int ret=0;
	unsigned int name_len=strlen(name)+1;
	if((env_fd = open("/proc/lk_env", O_RDWR)) < 0) {
		ERROR("Open env fail for read %s.\n",name);
		goto FAIL_RUTURN;
	}
	if(!(env_ioctl_obj.name = malloc(name_len))) {
		ERROR("Allocate Memory for env name fail.\n");
		goto FREE_FD;
	}else{
		memset(env_ioctl_obj.name,0x0,name_len);
	}
	if(!(env_ioctl_obj.value = malloc(value_max_len))){
		ERROR("Allocate Memory for env value fail.\n");
		goto FREE_ALLOCATE_NAME;
	}else{
		memset(env_ioctl_obj.value,0x0,value_max_len);
	}
	env_ioctl_obj.name_len = name_len;
	env_ioctl_obj.value_len = value_max_len;
	memcpy(env_ioctl_obj.name, name, name_len);
	if(ret = ioctl(env_fd, ENV_READ, &env_ioctl_obj)) {
		ERROR("Get env for %s check fail ret = %d, errno = %d.\n", name,ret, errno);
		goto FREE_ALLOCATE_VALUE;
	}
	if(env_ioctl_obj.value) {
		memcpy(value,env_ioctl_obj.value,env_ioctl_obj.value_len);
		LOG("%s  = %s \n", env_ioctl_obj.name,env_ioctl_obj.value);
	} else {
		ERROR("%s is not be set.\n",name);
		goto FREE_ALLOCATE_VALUE;		
	}

	free(env_ioctl_obj.name);
	free(env_ioctl_obj.value);
	close(env_fd);
	return ret;
FREE_ALLOCATE_VALUE:
	free(env_ioctl_obj.value);
FREE_ALLOCATE_NAME:
	free(env_ioctl_obj.name);
FREE_FD:
	close(env_fd);
FAIL_RUTURN:
	return -1;

}
#endif
int main(int argc, char **argv)
{
    int res;
    const char *source_path = NULL;
    const char *dest_path = NULL;
    uid_t uid = 0;
    gid_t gid = 0;
    gid_t write_gid = AID_SDCARD_RW;
    int num_threads = DEFAULT_NUM_THREADS;
    derive_t derive = DERIVE_NONE;
    bool split_perms = false;
    int i;
    struct rlimit rlim;

    LOG("starting sdcard service\n");
    int opt;
    while ((opt = getopt(argc, argv, "u:g:w:t:dls")) != -1) {
        switch (opt) {
            case 'u':
                uid = strtoul(optarg, NULL, 10);
                break;
            case 'g':
                gid = strtoul(optarg, NULL, 10);
                break;
            case 'w':
                write_gid = strtoul(optarg, NULL, 10);
                break;
            case 't':
                num_threads = strtoul(optarg, NULL, 10);
                break;
            case 'd':
                derive = DERIVE_UNIFIED;
                break;
            case 'l':
                derive = DERIVE_LEGACY;
                break;
            case 's':
                split_perms = true;
                break;
            case '?':
            default:
                return usage();
        }
    }

    for (i = optind; i < argc; i++) {
        char* arg = argv[i];
        if (!source_path) {
            source_path = arg;
        } else if (!dest_path) {
            dest_path = arg;
        } else if (!uid) {
            uid = strtoul(arg, NULL, 10);
        } else if (!gid) {
            gid = strtoul(arg, NULL, 10);
        } else {
            ERROR("too many arguments\n");
            return usage();
        }
    }

    if (!source_path) {
        ERROR("no source path specified\n");
        return usage();
    }
    if (!dest_path) {
        ERROR("no dest path specified\n");
        return usage();
    }
    if (!uid || !gid) {
        ERROR("uid and gid must be nonzero\n");
        return usage();
    }
    if (num_threads < 1) {
        ERROR("number of threads must be at least 1\n");
        return usage();
    }
    if (split_perms && derive == DERIVE_NONE) {
        ERROR("cannot split permissions without deriving\n");
        return usage();
    }

    rlim.rlim_cur = 8192;
    rlim.rlim_max = 8192;
    if (setrlimit(RLIMIT_NOFILE, &rlim)) {
        ERROR("Error setting RLIMIT_NOFILE, errno = %d\n", errno);
    }

#if defined(MTK_2SDCARD_SWAP) && defined(MTK_SHARED_SDCARD)
    if (!strcmp(source_path, "/data/media")) {
        int hasExternalSd = 0;
        int fd_externalsd;
        char internal_sd_path[PROPERTY_VALUE_MAX];
          
        property_get("internal_sd_path", internal_sd_path, "");
        LOG("internal_sd_path='%s'\n", internal_sd_path);

        if (strlen(internal_sd_path) > 0) {
            dest_path = internal_sd_path;           
        }
        else {
            if (get_boot_mode() != FACTORY_BOOT && get_boot_mode() != ATE_FACTORY_BOOT) { 
                /*    
                          The sdcard is mounted via VOLD except FACTORY mode.
                          If via vold, we need to consider SWAP feature
                      */        
                
                fd_externalsd = open("/dev/block/mmcblk1", O_RDONLY);
                if (fd_externalsd < 0){
                    TRACE("External sd doesn't exist (%d)\n", errno);
                    hasExternalSd = 0;
                }
                else {
                    TRACE("External sd exist.\n");
                    hasExternalSd = 1;
                    close(fd_externalsd);
                }           
            } 
            else {
                hasExternalSd = 0; 
                LOG("This is FACTORY mode, we DON'T need consider MTK_2SDCARD_SWAP feature\n");
            }

			LOG("hasExternalSd=%d, internal_sd_path='%s'\n", hasExternalSd, internal_sd_path);
            if (hasExternalSd) {
                dest_path = MOUNT_POINT_2;
            }                
        }
        /* For secondary storage, need to change write group to media_rw(1023) */
        if(!strcmp(dest_path, MOUNT_POINT_2)) {
            write_gid = AID_MEDIA_RW;
        }
    }
#ifdef LIMIT_SDCARD_SIZE
	char *tmp=malloc(30);
	if(!tmp){
		ERROR("malloc value fail !\n");		
	}else{
		if(!get_env_value(DATA_FREE_SIZE_TH_NAME,tmp,30)){
			internal_sdcard_free_size_threshold =strtoll(tmp,NULL,10);
			LOG("[fuse_debug]internal_sdcard_free_size_threshold = %lld \n",internal_sdcard_free_size_threshold);
		}else{
			ERROR("get %s from lk_env fail\n",DATA_FREE_SIZE_TH_NAME);
		}
	}
	free(tmp);
#endif
#endif

    LOG("source_path=%s, dest_path='%s', write_gid=%d\n", source_path, dest_path, write_gid);
    res = run(source_path, dest_path, uid, gid, write_gid, num_threads, derive, split_perms);
    return res < 0 ? 1 : 0;
}
