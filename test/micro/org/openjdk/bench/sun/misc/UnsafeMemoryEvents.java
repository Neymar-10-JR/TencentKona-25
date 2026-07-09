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

package org.openjdk.bench.sun.misc;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import jdk.internal.misc.Unsafe;
import jdk.jfr.Recording;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED"})
@SuppressWarnings("removal")
public class UnsafeMemoryEvents {

    private static final Unsafe U = Unsafe.getUnsafe();
    private static final String EVENT_ALLOCATE = "jdk.UnsafeAllocateMemory";
    private static final String EVENT_REALLOCATE = "jdk.UnsafeReallocateMemory";
    private static final String EVENT_FREE = "jdk.UnsafeFreeMemory";

    @Param({"false", "true"})
    public boolean jfrEnabled;

    private Recording recording;

    @Setup(Level.Trial)
    public void setupRecording() {
        if (jfrEnabled) {
            recording = new Recording();
            recording.enable(EVENT_ALLOCATE).withThreshold(Duration.ofNanos(0));
            recording.enable(EVENT_REALLOCATE).withThreshold(Duration.ofNanos(0));
            recording.enable(EVENT_FREE).withThreshold(Duration.ofNanos(0));
            recording.start();
        }
    }

    @TearDown(Level.Trial)
    public void tearDownRecording() {
        if (recording != null) {
            recording.stop();
            recording.close();
        }
    }

    @Benchmark
    public long allocateMemory(AllocateState state) {
        long address = U.allocateMemory(state.size);
        state.address = address;
        return address;
    }

    @Benchmark
    public long reallocateMemory(ReallocateState state) {
        long address = U.reallocateMemory(state.address, state.size * 2);
        state.address = address;
        return address;
    }

    @Benchmark
    public void freeMemory(FreeState state) {
        U.freeMemory(state.address);
        state.address = 0;
    }

    @State(Scope.Thread)
    public static class AllocateState {
        @Param({"64", "1024"})
        public long size;

        long address;

        @TearDown(Level.Invocation)
        public void tearDown() {
            if (address != 0) {
                U.freeMemory(address);
                address = 0;
            }
        }
    }

    @State(Scope.Thread)
    public static class ReallocateState {
        @Param({"64", "1024"})
        public long size;

        long address;

        @Setup(Level.Invocation)
        public void setup() {
            address = U.allocateMemory(size);
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            if (address != 0) {
                U.freeMemory(address);
                address = 0;
            }
        }
    }

    @State(Scope.Thread)
    public static class FreeState {
        @Param({"64", "1024"})
        public long size;

        long address;

        @Setup(Level.Invocation)
        public void setup() {
            address = U.allocateMemory(size);
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            if (address != 0) {
                U.freeMemory(address);
                address = 0;
            }
        }
    }
}
