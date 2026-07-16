/*
 * Copyright (C) 2025, Tencent. All rights reserved.
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

package sun.security.provider;

import sun.security.util.OpenSSLUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * The native implementation with OpenSSL for Sun algorithms.
 */
final class NativeSun {

    private static final int SM3_DIGEST_LENGTH = 32;

    private static final boolean IS_NATIVE_CRYPTO_ENABLED;
    private static final OpenSSLSM3 OPENSSL_SM3;

    static {
        boolean enableNativeCrypto = Boolean.getBoolean(
                "jdk.sun.enableNativeCrypto");
        OpenSSLSM3 openSSLSM3 = null;
        if (enableNativeCrypto
                // OpenSSL libcrypto must be loaded before FFM resolves its symbols.
                && OpenSSLUtil.isOpenSSLLoaded()) {
            openSSLSM3 = OpenSSLSM3.tryCreate();
        }

        OPENSSL_SM3 = openSSLSM3;
        IS_NATIVE_CRYPTO_ENABLED = openSSLSM3 != null;
    }

    static boolean isNativeCryptoEnabled() {
        return IS_NATIVE_CRYPTO_ENABLED;
    }

    public static void sm3Digest(byte[] message, byte[] out, int outOffset) {
        if (message == null || out == null) {
            throw new NullPointerException("Input/output arrays must not be null");
        }

        if (outOffset < 0 || outOffset > out.length - SM3_DIGEST_LENGTH) {
            throw new IndexOutOfBoundsException("Output buffer too small");
        }

        OpenSSLSM3 sm3 = OPENSSL_SM3;
        if (sm3 == null) {
            throw new IllegalStateException("Native SM3 is not available");
        }

        sm3.digest(message, out, outOffset);
    }

    // FFM downcalls replace the previous libsuncrypto JNI bridge.
    private static final class OpenSSLSM3 {

        private final MethodHandle evpSM3;
        private final MethodHandle evpMDCTXNew;
        private final MethodHandle evpMDCTXFree;
        private final MethodHandle evpDigestInitEx;
        private final MethodHandle evpDigestUpdate;
        private final MethodHandle evpDigestFinalEx;

        private OpenSSLSM3(MethodHandle evpSM3,
                           MethodHandle evpMDCTXNew,
                           MethodHandle evpMDCTXFree,
                           MethodHandle evpDigestInitEx,
                           MethodHandle evpDigestUpdate,
                           MethodHandle evpDigestFinalEx) {
            this.evpSM3 = evpSM3;
            this.evpMDCTXNew = evpMDCTXNew;
            this.evpMDCTXFree = evpMDCTXFree;
            this.evpDigestInitEx = evpDigestInitEx;
            this.evpDigestUpdate = evpDigestUpdate;
            this.evpDigestFinalEx = evpDigestFinalEx;
        }

        static OpenSSLSM3 tryCreate() {
            try {
                return create();
            } catch (RuntimeException e) {
                System.err.println("Failed to initialize OpenSSL SM3: " + e);
                return null;
            }
        }

        @SuppressWarnings("restricted")
        private static OpenSSLSM3 create() {
            Linker linker = Linker.nativeLinker();
            SymbolLookup lookup = SymbolLookup.loaderLookup();
            MemoryLayout sizeT = linker.canonicalLayouts().get("size_t");

            MethodHandle evpSM3 = downcall(linker, lookup, "EVP_sm3",
                    FunctionDescriptor.of(ValueLayout.ADDRESS));
            MethodHandle evpMDCTXNew = downcall(linker, lookup, "EVP_MD_CTX_new",
                    FunctionDescriptor.of(ValueLayout.ADDRESS));
            MethodHandle evpMDCTXFree = downcall(linker, lookup, "EVP_MD_CTX_free",
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            MethodHandle evpDigestInitEx = downcall(linker, lookup,
                    "EVP_DigestInit_ex",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS));
            MethodHandle evpDigestUpdate = downcall(linker, lookup,
                    "EVP_DigestUpdate",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS, sizeT));
            MethodHandle evpDigestFinalEx = downcall(linker, lookup,
                    "EVP_DigestFinal_ex",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS));

            return new OpenSSLSM3(evpSM3, evpMDCTXNew, evpMDCTXFree,
                    evpDigestInitEx, evpDigestUpdate, evpDigestFinalEx);
        }

        @SuppressWarnings("restricted")
        private static MethodHandle downcall(Linker linker, SymbolLookup lookup,
                                             String name,
                                             FunctionDescriptor descriptor) {
            return linker.downcallHandle(lookup.findOrThrow(name), descriptor);
        }

        void digest(byte[] message, byte[] out, int outOffset) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment ctx = (MemorySegment) evpMDCTXNew.invokeExact();
                if (ctx.equals(MemorySegment.NULL)) {
                    throw new IllegalStateException("Failed to create SM3 context");
                }

                try {
                    digest(arena, ctx, message, out, outOffset);
                } finally {
                    evpMDCTXFree.invokeExact(ctx);
                }
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException("OpenSSL SM3 digest failed", t);
            }
        }

        private void digest(Arena arena, MemorySegment ctx, byte[] message,
                            byte[] out, int outOffset) throws Throwable {
            MemorySegment sm3 = (MemorySegment) evpSM3.invokeExact();
            if (sm3.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("SM3 algorithm not available");
            }

            int init = (int) evpDigestInitEx.invokeExact(ctx, sm3,
                    MemorySegment.NULL);
            if (init != 1) {
                throw new IllegalStateException("SM3 initialization failed");
            }

            MemorySegment input = arena.allocateFrom(ValueLayout.JAVA_BYTE,
                    message);
            int update = (int) evpDigestUpdate.invokeExact(ctx, input,
                    (long) message.length);
            if (update != 1) {
                throw new IllegalStateException("SM3 update failed");
            }

            MemorySegment digest = arena.allocate(SM3_DIGEST_LENGTH);
            MemorySegment digestLength = arena.allocate(ValueLayout.JAVA_INT);
            int fin = (int) evpDigestFinalEx.invokeExact(ctx, digest,
                    digestLength);
            if (fin != 1) {
                throw new IllegalStateException("SM3 finalization failed");
            }

            int actualDigestLength = digestLength.get(ValueLayout.JAVA_INT, 0);
            if (actualDigestLength != SM3_DIGEST_LENGTH) {
                throw new IllegalStateException("Invalid SM3 digest length");
            }

            MemorySegment.copy(digest, ValueLayout.JAVA_BYTE, 0,
                    out, outOffset, SM3_DIGEST_LENGTH);
        }
    }
}
