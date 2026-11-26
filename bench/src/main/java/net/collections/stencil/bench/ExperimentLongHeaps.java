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

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Variants of a min-heap of {@code long} values for the experiment on the arity and the
 * sift-down strategy ({@code --suite=longheaparity}); not part of stencil. All variants grow by
 * half their length, like {@code LongHeap}, and store the values as they are (min first only).
 * <p>
 * The standard sift-down compares the moving value with the smallest child at every level and
 * stops where it fits. The bottom-up sift-down (Floyd) moves the hole down to a leaf along the
 * smallest children without comparing the moving value, then sifts the value up from there:
 * fewer comparisons, because a value taken from the bottom of the heap usually belongs near
 * the bottom again.
 */
final class ExperimentLongHeaps {

    private ExperimentLongHeaps() {
    }

    /** The operations of the benchmarks. */
    interface Heap {
        void push(long v);

        long poll();

        long peek();

        void replaceTop(long v);

        boolean isEmpty();
    }

    /** Shared storage and growth. */
    abstract static class Base implements Heap {
        long[] heap = new long[16];
        int size;

        @Override
        public final boolean isEmpty() {
            return size == 0;
        }

        @Override
        public final long peek() {
            if (size == 0) {
                throw new NoSuchElementException();
            }
            return heap[0];
        }

        @Override
        public final void push(long v) {
            int n = size;
            if (n == heap.length) {
                heap = Arrays.copyOf(heap, n + (n >> 1) + 1);
            }
            siftUp(n, v);
            size = n + 1;
        }

        @Override
        public final long poll() {
            int n = size;
            if (n == 0) {
                throw new NoSuchElementException();
            }
            long top = heap[0];
            size = --n;
            if (n > 0) {
                siftDown(heap[n], n);
            }
            return top;
        }

        @Override
        public final void replaceTop(long v) {
            if (size == 0) {
                throw new NoSuchElementException();
            }
            siftDown(v, size);
        }

        abstract void siftUp(int i, long v);

        /** Fills the hole at the root with {@code v} in a heap of {@code n} values. */
        abstract void siftDown(long v, int n);
    }

    /** Binary heap, standard sift-down. */
    static final class Binary extends Base {
        @Override
        void siftUp(int i, long v) {
            final long[] h = heap;
            while (i > 0) {
                int p = (i - 1) >>> 1;
                long pv = h[p];
                if (v >= pv) {
                    break;
                }
                h[i] = pv;
                i = p;
            }
            h[i] = v;
        }

        @Override
        void siftDown(long v, int n) {
            final long[] h = heap;
            final int lastParent = (n - 2) >> 1;
            int i = 0;
            while (i <= lastParent) {
                int c = (i << 1) + 1;
                long cv = h[c];
                if (c + 1 < n && h[c + 1] < cv) {
                    cv = h[++c];
                }
                if (v <= cv) {
                    break;
                }
                h[i] = cv;
                i = c;
            }
            h[i] = v;
        }
    }

    /** 4-ary heap, standard sift-down (with a tournament for the smallest child). */
    static class Quaternary extends Base {
        @Override
        final void siftUp(int i, long v) {
            siftUpFrom(i, v, 0);
        }

        /** Sifts {@code v} up from the hole at {@code i}, not above {@code top}. */
        final void siftUpFrom(int i, long v, int top) {
            final long[] h = heap;
            while (i > top) {
                int p = (i - 1) >>> 2;
                long pv = h[p];
                if (v >= pv) {
                    break;
                }
                h[i] = pv;
                i = p;
            }
            h[i] = v;
        }

        /** Position of the smallest child of {@code i} (which has at least one child). */
        static int minChild(long[] h, int i, int n) {
            int c = (i << 2) + 1;
            int m = c;
            long mv = h[c];
            if (c + 3 < n) {
                long bv = h[c + 1];
                if (bv < mv) {
                    m = c + 1;
                    mv = bv;
                }
                int m2 = c + 2;
                long cv = h[c + 2];
                long dv = h[c + 3];
                if (dv < cv) {
                    m2 = c + 3;
                    cv = dv;
                }
                if (cv < mv) {
                    m = m2;
                }
            } else {
                for (int j = c + 1; j < n; j++) {
                    if (h[j] < mv) {
                        m = j;
                        mv = h[j];
                    }
                }
            }
            return m;
        }

        @Override
        void siftDown(long v, int n) {
            final long[] h = heap;
            final int lastParent = (n - 2) >> 2;
            int i = 0;
            while (i <= lastParent) {
                int m = minChild(h, i, n);
                long mv = h[m];
                if (v <= mv) {
                    break;
                }
                h[i] = mv;
                i = m;
            }
            h[i] = v;
        }
    }

    /** 4-ary heap, bottom-up sift-down. */
    static final class QuaternaryBottomUp extends Quaternary {
        @Override
        void siftDown(long v, int n) {
            final long[] h = heap;
            final int lastParent = (n - 2) >> 2;
            int i = 0;
            while (i <= lastParent) {
                int m = minChild(h, i, n);
                h[i] = h[m];
                i = m;
            }
            siftUpFrom(i, v, 0);
        }
    }

    /** 8-ary heap, bottom-up sift-down. */
    static final class OctonaryBottomUp extends Base {
        @Override
        void siftUp(int i, long v) {
            final long[] h = heap;
            while (i > 0) {
                int p = (i - 1) >>> 3;
                long pv = h[p];
                if (v >= pv) {
                    break;
                }
                h[i] = pv;
                i = p;
            }
            h[i] = v;
        }

        @Override
        void siftDown(long v, int n) {
            final long[] h = heap;
            final int lastParent = (n - 2) >> 3;
            int i = 0;
            while (i <= lastParent) {
                int c = (i << 3) + 1;
                int end = Math.min(c + 8, n);
                int m = c;
                long mv = h[c];
                for (int j = c + 1; j < end; j++) {
                    long jv = h[j];
                    if (jv < mv) {
                        m = j;
                        mv = jv;
                    }
                }
                h[i] = mv;
                i = m;
            }
            while (i > 0) {
                int p = (i - 1) >>> 3;
                long pv = h[p];
                if (v >= pv) {
                    break;
                }
                h[i] = pv;
                i = p;
            }
            h[i] = v;
        }
    }

    /** 4-ary heap, bottom-up sift-down, smallest child found by a plain loop. */
    static final class QuaternaryBottomUpLoop extends Base {
        @Override
        void siftUp(int i, long v) {
            final long[] h = heap;
            while (i > 0) {
                int p = (i - 1) >>> 2;
                long pv = h[p];
                if (v >= pv) {
                    break;
                }
                h[i] = pv;
                i = p;
            }
            h[i] = v;
        }

        @Override
        void siftDown(long v, int n) {
            final long[] h = heap;
            final int lastParent = (n - 2) >> 2;
            int i = 0;
            while (i <= lastParent) {
                int c = (i << 2) + 1;
                int end = Math.min(c + 4, n);
                int m = c;
                long mv = h[c];
                for (int j = c + 1; j < end; j++) {
                    long jv = h[j];
                    if (jv < mv) {
                        m = j;
                        mv = jv;
                    }
                }
                h[i] = mv;
                i = m;
            }
            while (i > 0) {
                int p = (i - 1) >>> 2;
                long pv = h[p];
                if (v >= pv) {
                    break;
                }
                h[i] = pv;
                i = p;
            }
            h[i] = v;
        }
    }

    /** 8-ary heap, standard sift-down, smallest child found by a plain loop. */
    static final class Octonary extends Base {
        @Override
        void siftUp(int i, long v) {
            final long[] h = heap;
            while (i > 0) {
                int p = (i - 1) >>> 3;
                long pv = h[p];
                if (v >= pv) {
                    break;
                }
                h[i] = pv;
                i = p;
            }
            h[i] = v;
        }

        @Override
        void siftDown(long v, int n) {
            final long[] h = heap;
            final int lastParent = (n - 2) >> 3;
            int i = 0;
            while (i <= lastParent) {
                int c = (i << 3) + 1;
                int end = Math.min(c + 8, n);
                int m = c;
                long mv = h[c];
                for (int j = c + 1; j < end; j++) {
                    long jv = h[j];
                    if (jv < mv) {
                        m = j;
                        mv = jv;
                    }
                }
                if (v <= mv) {
                    break;
                }
                h[i] = mv;
                i = m;
            }
            h[i] = v;
        }
    }

    /** 16-ary heap, bottom-up sift-down, smallest child found by a plain loop. */
    static final class HexadecimalBottomUp extends Base {
        @Override
        void siftUp(int i, long v) {
            final long[] h = heap;
            while (i > 0) {
                int p = (i - 1) >>> 4;
                long pv = h[p];
                if (v >= pv) {
                    break;
                }
                h[i] = pv;
                i = p;
            }
            h[i] = v;
        }

        @Override
        void siftDown(long v, int n) {
            final long[] h = heap;
            final int lastParent = (n - 2) >> 4;
            int i = 0;
            while (i <= lastParent) {
                int c = (i << 4) + 1;
                int end = Math.min(c + 16, n);
                int m = c;
                long mv = h[c];
                for (int j = c + 1; j < end; j++) {
                    long jv = h[j];
                    if (jv < mv) {
                        m = j;
                        mv = jv;
                    }
                }
                h[i] = mv;
                i = m;
            }
            while (i > 0) {
                int p = (i - 1) >>> 4;
                long pv = h[p];
                if (v >= pv) {
                    break;
                }
                h[i] = pv;
                i = p;
            }
            h[i] = v;
        }
    }

    /** 4-ary heap, standard sift-down, smallest child found by a plain loop. */
    static final class QuaternaryLoop extends Base {
        @Override
        void siftUp(int i, long v) {
            final long[] h = heap;
            while (i > 0) {
                int p = (i - 1) >>> 2;
                long pv = h[p];
                if (v >= pv) {
                    break;
                }
                h[i] = pv;
                i = p;
            }
            h[i] = v;
        }

        @Override
        void siftDown(long v, int n) {
            final long[] h = heap;
            final int lastParent = (n - 2) >> 2;
            int i = 0;
            while (i <= lastParent) {
                int c = (i << 2) + 1;
                int end = Math.min(c + 4, n);
                int m = c;
                long mv = h[c];
                for (int j = c + 1; j < end; j++) {
                    long jv = h[j];
                    if (jv < mv) {
                        m = j;
                        mv = jv;
                    }
                }
                if (v <= mv) {
                    break;
                }
                h[i] = mv;
                i = m;
            }
            h[i] = v;
        }
    }
}
