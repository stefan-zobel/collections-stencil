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

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;

/**
 * Runs a full garbage collection before every invocation of a build benchmark with at least
 * {@value #MIN_SIZE} entries, outside the measured time.
 * <p>
 * At that size, one invocation takes one to two seconds, so a measurement iteration holds a
 * single invocation. A G1 pause for the garbage of the previous invocations (several hundred
 * megabytes) then lands in one measurement and can double it. The allocation pattern of each
 * implementation is deterministic, so the same implementation is hit in every round, and the
 * artifact looks like a reproducible result. Smaller sizes run many invocations per iteration,
 * which spreads the collection cost evenly, so they are left alone.
 * <p>
 * Used as a parameter of the {@code build}, {@code buildPresized}, and {@code computeIfAbsent}
 * benchmark methods only; lookups and churn keep their state and are not affected. Callers
 * outside JMH pass {@code null}.
 */
@State(Scope.Thread)
public class GcBeforeLargeBuild {

    /** Smallest {@code size} parameter that gets a collection before each invocation. */
    static final int MIN_SIZE = 10_000_000;

    @Setup(Level.Invocation)
    public void collect(BenchmarkParams params) {
        String size = params.getParam("size");
        if (size != null && Integer.parseInt(size) >= MIN_SIZE) {
            System.gc();
        }
    }
}
