/*
 * Copyright 2026 The collections-stencil authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.collections.stencil.bench;

/** Key distributions used by the benchmarks. */
public enum KeyDist {

    /** Distinct pseudo-random keys (bijective mix of 1..n; 32 or 64 bits). */
    RANDOM,

    /** Keys 0..n-1. Includes key 0 and exposes weak hash mixing. */
    SEQUENTIAL;

    /** murmur3 fmix32: a bijection on 32-bit ints, so distinct inputs give distinct keys. */
    static int fmix32(int x) {
        x ^= x >>> 16;
        x *= 0x85EB_CA6B;
        x ^= x >>> 13;
        x *= 0xC2B2_AE35;
        x ^= x >>> 16;
        return x;
    }

    /** murmur3 fmix64: a bijection on 64-bit longs. */
    static long fmix64(long x) {
        x ^= x >>> 33;
        x *= 0xFF51_AFD7_ED55_8CCDL;
        x ^= x >>> 33;
        x *= 0xC4CE_B9FE_1A85_EC53L;
        x ^= x >>> 33;
        return x;
    }

    /** The {@code i}-th 64-bit key in the population; {@code i >= size} yields keys that are absent. */
    long longKey(int i) {
        return switch (this) {
            case RANDOM -> fmix64(i + 1);
            case SEQUENTIAL -> i;
        };
    }

    /**
     * The {@code i}-th string key in the population; {@code i >= size} yields keys that are
     * absent. RANDOM: the hex digits of a 64-bit key (up to 16 characters, hash codes spread
     * well). SEQUENTIAL: the decimal digits of {@code i}; such strings have similar hash codes,
     * which exposes weak hash mixing.
     */
    String stringKey(int i) {
        return switch (this) {
            case RANDOM -> Long.toHexString(fmix64(i + 1));
            case SEQUENTIAL -> Integer.toString(i);
        };
    }

    /** The {@code i}-th key in the population; {@code i >= size} yields keys that are absent. */
    int key(int i) {
        return switch (this) {
            case RANDOM -> fmix32(i + 1);
            case SEQUENTIAL -> i;
        };
    }
}
