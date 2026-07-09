/*
 * Copyright (c) 2026, Tencent. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.jfr.events;

import jdk.jfr.Category;
import jdk.jfr.DataAmount;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.MemoryAddress;
import jdk.jfr.Name;
import jdk.jfr.internal.MirrorEvent;
import jdk.jfr.internal.Type;

@Name(Type.EVENT_NAME_PREFIX + "UnsafeReallocateMemory")
@Label("Unsafe Reallocate Memory")
@Category("Java Application")
@Description("Native memory reallocated through jdk.internal.misc.Unsafe.reallocateMemory")
@StackFilter("jdk.internal.misc.Unsafe")
public final class UnsafeReallocateMemoryEvent extends MirrorEvent {

    @Label("Address")
    @Description("Original native memory address")
    @MemoryAddress
    public long address;

    @Label("Bytes")
    @Description("Requested reallocation size")
    @DataAmount
    public long bytes;

    @Label("New Address")
    @Description("Returned native memory address")
    @MemoryAddress
    public long newAddress;
}
