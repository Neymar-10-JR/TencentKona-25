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

public class TestHumongousAllocationStackTrace {
    private static final String THREAD_NAME = "HumongousAllocatorThread";

    public static void main(String[] args) throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                "-XX:+UseG1GC",
                "-Xms32m",
                "-Xmx32m",
                "-XX:G1HeapRegionSize=1m",
                "-Xlog:gc+humongous=debug",
                HumongousAllocator.class.getName());

        output.shouldHaveExitValue(0);
        output.shouldContain("Humongous allocation succeeded");
        output.shouldContain("thread \"" + THREAD_NAME + "\"");
        output.shouldContain("Java stack for humongous allocation");
        output.shouldContain("gc.g1.TestHumongousAllocationStackTrace$HumongousAllocator.allocateHumongous");
    }

    public static class HumongousAllocator {
        private static volatile Object sink;

        public static void main(String[] args) throws Exception {
            Thread thread = new Thread(HumongousAllocator::allocateHumongous, THREAD_NAME);
            thread.start();
            thread.join();
        }

        private static void allocateHumongous() {
            sink = new byte[700 * 1024];
        }
    }
}
