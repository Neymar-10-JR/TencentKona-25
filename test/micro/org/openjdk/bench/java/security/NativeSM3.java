/*
 * Copyright (C) 2026, Tencent. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation. Tencent designates
 * this particular file as subject to the "Classpath" exception as provided
 * in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License version 2 for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openjdk.bench.java.security;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks SM3 through the public MessageDigest API.
 *
 * The benchmark verifies that the actual Native SM3 state matches
 * {@code benchmark.nativeSm3.enabled}. For the native run, the VM must also
 * specify {@code jdk.sun.enableNativeCrypto=true} and a valid
 * {@code jdk.openssl.cryptoLibPath}.
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsAppend = {
        "--add-opens=java.base/sun.security.provider=ALL-UNNAMED"
})
public class NativeSM3 {
    private static final String EXPECT_NATIVE_SM3 = "benchmark.nativeSm3.enabled";

    @Param({"64", "16384"})
    private int length;

    private byte[] input;
    private MessageDigest digest;

    @Setup
    public void setup() throws Exception {
        verifyNativeSM3State();
        input = new byte[length];
        new Random(1234567890).nextBytes(input);
        digest = MessageDigest.getInstance("SM3");
    }

    @Benchmark
    public byte[] digest() {
        return digest.digest(input);
    }

    private static void verifyNativeSM3State() throws Exception {
        Class<?> nativeSun = Class.forName("sun.security.provider.NativeSun");
        Method isNativeCryptoEnabled = nativeSun.getDeclaredMethod("isNativeCryptoEnabled");
        isNativeCryptoEnabled.setAccessible(true);

        boolean expected = Boolean.getBoolean(EXPECT_NATIVE_SM3);
        boolean actual = (boolean) isNativeCryptoEnabled.invoke(null);
        if (actual != expected) {
            throw new IllegalStateException("Expected Native SM3 enabled=" + expected
                    + ", but was " + actual);
        }
    }
}
