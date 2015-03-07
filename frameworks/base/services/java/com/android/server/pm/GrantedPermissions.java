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

package com.android.server.pm;

import android.content.pm.ApplicationInfo;

import java.util.HashSet;

class GrantedPermissions {
    int pkgFlags;
    /// M: [FlagExt] Flags for MTK internal use
    int mtkFlags;

    HashSet<String> grantedPermissions = new HashSet<String>();

    int[] gids;

    GrantedPermissions(int pkgFlags) {
        setFlags(pkgFlags);
    }

    /// M: [FlagExt] Additional constructor for MTK flags
    GrantedPermissions(int pkgFlags, int mtkFlags) {
        setFlags(pkgFlags);
        setMtkFlags(mtkFlags);
    }

    @SuppressWarnings("unchecked")
    GrantedPermissions(GrantedPermissions base) {
        pkgFlags = base.pkgFlags;
        /// M: [FlagExt] copy mtkFlags
        mtkFlags = base.mtkFlags;
        grantedPermissions = (HashSet<String>) base.grantedPermissions.clone();

        if (base.gids != null) {
            gids = base.gids.clone();
        }
    }

    void setFlags(int pkgFlags) {
        /// M: Directly set pkgFlags with the parameter
        this.pkgFlags = pkgFlags;
    }

    /// M: [FlagExt] mtkFlags set up function
    void setMtkFlags(int mtkFlags) {
        this.mtkFlags = mtkFlags;
    }
}
