/*
 * Copyright (c) 2026, Tencent. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.jfr.event.runtime;

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertGTE;
import static jdk.test.lib.Asserts.fail;

import java.time.Duration;
import java.util.List;

import jdk.internal.misc.Unsafe;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          jdk.jfr
 * @run main/othervm jdk.jfr.event.runtime.TestUnsafeMemoryEvents
 */
public class TestUnsafeMemoryEvents {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long ALLOCATE_BYTES = 64;
    private static final long REALLOCATE_BYTES = 128;

    public static void main(String[] args) throws Exception {
        long allocatedAddress = 0;
        long reallocatedAddress = 0;
        try (Recording recording = new Recording()) {
            recording.enable(EventNames.UnsafeAllocateMemory).withThreshold(Duration.ofNanos(0));
            recording.enable(EventNames.UnsafeReallocateMemory).withThreshold(Duration.ofNanos(0));
            recording.enable(EventNames.UnsafeFreeMemory).withThreshold(Duration.ofNanos(0));
            recording.start();

            allocatedAddress = UNSAFE.allocateMemory(ALLOCATE_BYTES);
            long originalAddress = allocatedAddress;
            reallocatedAddress = UNSAFE.reallocateMemory(allocatedAddress, REALLOCATE_BYTES);
            allocatedAddress = 0;
            long freedAddress = reallocatedAddress;
            UNSAFE.freeMemory(freedAddress);
            reallocatedAddress = 0;

            recording.stop();
            List<RecordedEvent> events = Events.fromRecording(recording);
            verifyAllocateEvent(events, originalAddress);
            verifyReallocateEvent(events, originalAddress, freedAddress);
            verifyFreeEvent(events, freedAddress);
        } finally {
            if (reallocatedAddress != 0) {
                UNSAFE.freeMemory(reallocatedAddress);
            }
            if (allocatedAddress != 0) {
                UNSAFE.freeMemory(allocatedAddress);
            }
        }
    }

    private static void verifyAllocateEvent(List<RecordedEvent> events, long allocatedAddress) {
        RecordedEvent event = findEvent(events, EventNames.UnsafeAllocateMemory, "address", allocatedAddress);
        assertGTE(event.getDuration().toNanos(), 0L, "Duration should not be negative");
        assertEquals(event.getLong("bytes"), ALLOCATE_BYTES, "Wrong allocate size");
        assertEquals(event.getLong("address"), allocatedAddress, "Wrong allocated address");
    }

    private static void verifyReallocateEvent(List<RecordedEvent> events, long originalAddress, long reallocatedAddress) {
        RecordedEvent event = findEvent(events, EventNames.UnsafeReallocateMemory, "newAddress", reallocatedAddress);
        assertGTE(event.getDuration().toNanos(), 0L, "Duration should not be negative");
        assertEquals(event.getLong("bytes"), REALLOCATE_BYTES, "Wrong reallocate size");
        assertEquals(event.getLong("address"), originalAddress, "Wrong original address");
        assertEquals(event.getLong("newAddress"), reallocatedAddress, "Wrong reallocated address");
    }

    private static void verifyFreeEvent(List<RecordedEvent> events, long freedAddress) {
        RecordedEvent event = findEvent(events, EventNames.UnsafeFreeMemory, "address", freedAddress);
        assertGTE(event.getDuration().toNanos(), 0L, "Duration should not be negative");
        assertEquals(event.getLong("address"), freedAddress, "Wrong free address");
    }

    private static RecordedEvent findEvent(List<RecordedEvent> events, String eventName, String fieldName, long expectedValue) {
        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals(eventName) && event.getLong(fieldName) == expectedValue) {
                return event;
            }
        }
        fail("Could not find event " + eventName + " with " + fieldName + "=" + expectedValue);
        return null;
    }
}
