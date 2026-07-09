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

/*
 * @test
 * @summary Tests -XX:CompileCommand=requirefullprofile.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileCommand=requirefullprofile,compiler.oracle.TestRequireFullProfileCommand::target
 *                   compiler.oracle.TestRequireFullProfileCommand
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileCommand=option,compiler.oracle.TestRequireFullProfileCommand::target,requirefullprofile
 *                   compiler.oracle.TestRequireFullProfileCommand
 */

package compiler.oracle;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

import java.lang.reflect.Executable;

public class TestRequireFullProfileCommand {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final String OPTION = "requirefullprofile";

    public static void main(String[] args) throws Exception {
        Executable target = getMethod("target");
        Executable other = getMethod("other");

        Asserts.assertEQ(Boolean.TRUE, WB.getMethodBooleanOption(target, OPTION),
                "requirefullprofile should be set for target method");
        Asserts.assertNull(WB.getMethodBooleanOption(other, OPTION),
                "requirefullprofile should not be set for other method");
    }

    private static int target() {
        return 42;
    }

    private static int other() {
        return 0;
    }

    private static Executable getMethod(String name) throws Exception {
        return TestRequireFullProfileCommand.class.getDeclaredMethod(name);
    }
}
