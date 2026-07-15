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

package gc.g1;

/*
 * @test TestHumongousAllocationStackTrace
 * @summary Test that G1 humongous allocation debug logging prints the allocating Java thread and stack.
 * @requires vm.gc.G1
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver gc.g1.TestHumongousAllocationStackTrace
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.concurrent.CountDownLatch;

public class TestHumongousAllocationStackTrace {
    private static final String PLATFORM_THREAD_NAME = "HumongousPlatformAllocator";
    private static final String VIRTUAL_THREAD_NAME = "HumongousVirtualAllocator";
    private static final String CONCURRENT_THREAD_PREFIX = "HumongousConcurrentAllocator-";
    private static final int CONCURRENT_THREAD_COUNT = 4;

    public static void main(String[] args) throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                "-XX:+UseG1GC",
                "-Xms128m",
                "-Xmx128m",
                "-XX:G1HeapRegionSize=1m",
                "-Xlog:gc+humongous=debug",
                HumongousAllocator.class.getName());

        output.shouldHaveExitValue(0);
        output.outputTo(System.out);

        assertAllocationLogged(output, PLATFORM_THREAD_NAME,
                HumongousAllocator.PlatformHumongousAllocator.class.getName());
        assertAllocationLogged(output, VIRTUAL_THREAD_NAME,
                HumongousAllocator.VirtualHumongousAllocator.class.getName());
        for (int i = 0; i < CONCURRENT_THREAD_COUNT; i++) {
            assertAllocationLogged(output, CONCURRENT_THREAD_PREFIX + i,
                    HumongousAllocator.ConcurrentHumongousAllocator.class.getName());
        }
    }

    private static void assertAllocationLogged(OutputAnalyzer output, String threadName,
                                               String allocatorClass) {
        output.shouldContain("Humongous allocation succeeded");
        output.shouldContain("thread \"" + threadName + "\"");
        output.shouldContain("Java stack for humongous allocation");
        output.shouldContain(allocatorClass + ".allocateHumongous");

        String log = output.getOutput();
        int allocation = log.indexOf("thread \"" + threadName + "\"");
        int stack = log.indexOf(allocatorClass + ".allocateHumongous", allocation);
        int nextAllocation = log.indexOf("Humongous allocation succeeded", allocation + 1);
        if (stack == -1 || (nextAllocation != -1 && stack > nextAllocation)) {
            throw new RuntimeException("Stack trace for " + threadName
                    + " was interleaved with another humongous allocation log");
        }
    }

    public static class HumongousAllocator {
        private static final Object[] SINKS = new Object[2 + CONCURRENT_THREAD_COUNT];

        public static void main(String[] args) throws Exception {
            runPlatformAllocation();
            runVirtualAllocation();
            runConcurrentAllocations();
        }

        private static void runPlatformAllocation() throws InterruptedException {
            Thread thread = new Thread(PlatformHumongousAllocator::allocateHumongous,
                    PLATFORM_THREAD_NAME);
            thread.start();
            thread.join();
        }

        private static void runVirtualAllocation() throws InterruptedException {
            Thread thread = Thread.ofVirtual()
                    .name(VIRTUAL_THREAD_NAME)
                    .start(VirtualHumongousAllocator::allocateHumongous);
            thread.join();
        }

        private static void runConcurrentAllocations() throws InterruptedException {
            CountDownLatch ready = new CountDownLatch(CONCURRENT_THREAD_COUNT);
            CountDownLatch start = new CountDownLatch(1);
            Thread[] threads = new Thread[CONCURRENT_THREAD_COUNT];

            for (int i = 0; i < CONCURRENT_THREAD_COUNT; i++) {
                int index = i;
                threads[i] = new Thread(() -> {
                    ready.countDown();
                    await(start);
                    ConcurrentHumongousAllocator.allocateHumongous(index);
                }, CONCURRENT_THREAD_PREFIX + i);
                threads[i].start();
            }

            ready.await();
            start.countDown();
            for (Thread thread : threads) {
                thread.join();
            }
        }

        private static void await(CountDownLatch latch) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        private static class PlatformHumongousAllocator {
            private static void allocateHumongous() {
                SINKS[0] = new byte[700 * 1024];
            }
        }

        private static class VirtualHumongousAllocator {
            private static void allocateHumongous() {
                SINKS[1] = new byte[700 * 1024];
            }
        }

        private static class ConcurrentHumongousAllocator {
            private static void allocateHumongous(int index) {
                SINKS[2 + index] = new byte[700 * 1024];
            }
        }
    }
}
