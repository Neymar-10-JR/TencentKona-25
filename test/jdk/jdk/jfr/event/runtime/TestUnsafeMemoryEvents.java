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
import static jdk.test.lib.Asserts.assertNE;
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
    private static final long REALLOCATE_NULL_ADDRESS_BYTES = 256;
    private static final long ZERO_SIZE_REALLOCATE_SOURCE_BYTES = 512;

    public static void main(String[] args) throws Exception {
        long allocatedAddress = 0;
        long reallocatedAddress = 0;
        long nullAddressReallocated = 0;
        long zeroSizeOriginalAddress = 0;
        long zeroSizeAddressToFree = 0;
        try (Recording recording = new Recording()) {
            recording.enable(EventNames.UnsafeAllocateMemory)
                    .withThreshold(Duration.ofNanos(0));
            recording.enable(EventNames.UnsafeReallocateMemory)
                    .withThreshold(Duration.ofNanos(0));
            recording.enable(EventNames.UnsafeFreeMemory)
                    .withThreshold(Duration.ofNanos(0));
            recording.start();

            allocatedAddress = UNSAFE.allocateMemory(ALLOCATE_BYTES);
            long originalAddress = allocatedAddress;
            reallocatedAddress = UNSAFE.reallocateMemory(allocatedAddress, REALLOCATE_BYTES);
            allocatedAddress = 0;
            long freedAddress = reallocatedAddress;
            UNSAFE.freeMemory(freedAddress);
            reallocatedAddress = 0;

            assertEquals(UNSAFE.allocateMemory(0), 0L,
                    "Zero-size allocation should return the null address");

            nullAddressReallocated = UNSAFE.reallocateMemory(0, REALLOCATE_NULL_ADDRESS_BYTES);
            assertNE(nullAddressReallocated, 0L,
                    "Reallocation from the null address should allocate memory");
            long nullAddressAllocation = nullAddressReallocated;
            UNSAFE.freeMemory(nullAddressAllocation);
            nullAddressReallocated = 0;

            zeroSizeAddressToFree = UNSAFE.allocateMemory(ZERO_SIZE_REALLOCATE_SOURCE_BYTES);
            zeroSizeOriginalAddress = zeroSizeAddressToFree;
            assertNE(zeroSizeOriginalAddress, 0L,
                    "Allocation for zero-size reallocation should succeed");
            assertEquals(UNSAFE.reallocateMemory(zeroSizeOriginalAddress, 0), 0L,
                    "Zero-size reallocation should return the null address");
            zeroSizeAddressToFree = 0;

            UNSAFE.freeMemory(0);
            assertEquals(UNSAFE.reallocateMemory(0, 0), 0L,
                    "Reallocation of the null address to zero size should return the null address");

            recording.stop();
            List<RecordedEvent> events = Events.fromRecording(recording);
            verifyAllocateEvent(events, ALLOCATE_BYTES, originalAddress);
            verifyReallocateEvent(events, originalAddress, REALLOCATE_BYTES, freedAddress);
            verifyFreeEvent(events, freedAddress);
            verifyAllocateEvent(events, 0, 0);
            verifyReallocateEvent(events, 0, REALLOCATE_NULL_ADDRESS_BYTES,
                    nullAddressAllocation);
            verifyFreeEvent(events, nullAddressAllocation);
            verifyAllocateEvent(events, ZERO_SIZE_REALLOCATE_SOURCE_BYTES,
                    zeroSizeOriginalAddress);
            verifyReallocateEvent(events, zeroSizeOriginalAddress, 0, 0);
            verifyFreeEvent(events, zeroSizeOriginalAddress);
            verifyReallocateEvent(events, 0, 0, 0);
            verifyFreeEventCount(events, 0, 2);
        } finally {
            if (reallocatedAddress != 0) {
                UNSAFE.freeMemory(reallocatedAddress);
            }
            if (allocatedAddress != 0) {
                UNSAFE.freeMemory(allocatedAddress);
            }
            if (nullAddressReallocated != 0) {
                UNSAFE.freeMemory(nullAddressReallocated);
            }
            if (zeroSizeAddressToFree != 0) {
                UNSAFE.freeMemory(zeroSizeAddressToFree);
            }
        }
    }

    private static void verifyAllocateEvent(List<RecordedEvent> events, long bytes,
                                            long allocatedAddress) {
        RecordedEvent event = findAllocateEvent(events, bytes, allocatedAddress);
        assertGTE(event.getDuration().toNanos(), 0L, "Duration should not be negative");
        assertEquals(event.getLong("bytes"), bytes, "Wrong allocate size");
        assertEquals(event.getLong("address"), allocatedAddress, "Wrong allocated address");
    }

    private static void verifyReallocateEvent(List<RecordedEvent> events, long originalAddress,
                                              long bytes, long reallocatedAddress) {
        RecordedEvent event = findReallocateEvent(events, originalAddress, bytes,
                reallocatedAddress);
        assertGTE(event.getDuration().toNanos(), 0L, "Duration should not be negative");
        assertEquals(event.getLong("bytes"), bytes, "Wrong reallocate size");
        assertEquals(event.getLong("address"), originalAddress, "Wrong original address");
        assertEquals(event.getLong("newAddress"), reallocatedAddress, "Wrong reallocated address");
    }

    private static void verifyFreeEvent(List<RecordedEvent> events, long freedAddress) {
        RecordedEvent event = findFreeEvent(events, freedAddress);
        assertGTE(event.getDuration().toNanos(), 0L, "Duration should not be negative");
        assertEquals(event.getLong("address"), freedAddress, "Wrong free address");
    }

    private static void verifyFreeEventCount(List<RecordedEvent> events, long address,
                                             long expectedEventCount) {
        long count = events.stream()
                .filter(event -> event.getEventType().getName().equals(EventNames.UnsafeFreeMemory))
                .filter(event -> event.getLong("address") == address)
                .count();
        assertEquals(count, expectedEventCount, "Wrong number of free events");
    }

    private static RecordedEvent findAllocateEvent(List<RecordedEvent> events, long bytes,
                                                   long address) {
        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals(EventNames.UnsafeAllocateMemory)
                    && event.getLong("bytes") == bytes && event.getLong("address") == address) {
                return event;
            }
        }
        fail("Could not find allocate event with bytes=" + bytes + ", address=" + address);
        return null;
    }

    private static RecordedEvent findReallocateEvent(List<RecordedEvent> events,
                                                      long address, long bytes, long newAddress) {
        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals(EventNames.UnsafeReallocateMemory)
                    && event.getLong("address") == address && event.getLong("bytes") == bytes
                    && event.getLong("newAddress") == newAddress) {
                return event;
            }
        }
        fail("Could not find reallocate event with address=" + address + ", bytes=" + bytes
                + ", newAddress=" + newAddress);
        return null;
    }

    private static RecordedEvent findFreeEvent(List<RecordedEvent> events, long address) {
        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals(EventNames.UnsafeFreeMemory)
                    && event.getLong("address") == address) {
                return event;
            }
        }
        fail("Could not find free event with address=" + address);
        return null;
    }
}
