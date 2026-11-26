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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Calls every benchmark method of the list and deque adapters once, outside JMH, and checks that
 * all implementations compute the same results. This is a functional test of the adapters, not
 * a measurement.
 */
class SequenceBenchAdaptersTest {

    private static final List<Class<? extends AbstractIntListBench>> LISTS = List.of(
            StencilIntListBench.class, FastutilIntListBench.class, HppcIntListBench.class,
            EclipseIntListBench.class, SpeigerIntListBench.class, JdkIntListBench.class);

    private static final List<Class<? extends AbstractIntDequeBench>> DEQUES = List.of(
            StencilIntDequeBench.class, FastutilIntDequeBench.class, HppcIntDequeBench.class,
            SpeigerIntDequeBench.class, JdkIntDequeBench.class);

    @Test
    void listAdaptersAgree() throws ReflectiveOperationException {
        List<Object> expected = null;
        for (Class<? extends AbstractIntListBench> cls : LISTS) {
            AbstractIntListBench bench = cls.getDeclaredConstructor().newInstance();
            bench.size = 1400;
            bench.dist = KeyDist.RANDOM;
            bench.setUpData();
            List<Object> results = new ArrayList<>();
            // insertRemove changes the list: get and iterate after it see the changes.
            for (String op : List.of("get", "iterate", "insertRemove", "get", "iterate", "insertRemove")) {
                results.add(op + "=" + cls.getMethod(op).invoke(bench));
            }
            Object built = cls.getMethod("build", GcBeforeLargeBuild.class).invoke(bench, (Object) null);
            results.add("build=" + built.getClass().getMethod("size").invoke(built));
            if (expected == null) {
                expected = results;
            } else {
                assertEquals(expected, results, cls.getSimpleName());
            }
        }
        assertEquals("build=1400", expected.getLast());
    }

    @Test
    void dequeAdaptersAgree() throws ReflectiveOperationException {
        List<Object> expected = null;
        for (Class<? extends AbstractIntDequeBench> cls : DEQUES) {
            AbstractIntDequeBench bench = cls.getDeclaredConstructor().newInstance();
            bench.size = 1400;
            bench.dist = KeyDist.RANDOM;
            bench.setUpData();
            List<Object> results = new ArrayList<>();
            // fifo and window change the deque: twice each, so that later calls depend on earlier ones.
            for (String op : List.of("fifo", "window", "stack", "fifo", "window", "stack")) {
                results.add(op + "=" + cls.getMethod(op).invoke(bench));
            }
            if (expected == null) {
                expected = results;
            } else {
                assertEquals(expected, results, cls.getSimpleName());
            }
        }
    }
}
