package com.softknife.util.common;

import com.softknife.data.context.Context;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ContextConcurrencyTest {

    private static final int THREADS = 8;
    private static final int KEYS_PER_THREAD = 2_000;

    @Test(description = "Concurrent writers lose no entries")
    public void concurrentWritesAreAllKept() throws Exception {
        Context context = new Context();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < THREADS; t++) {
                final int thread = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int k = 0; k < KEYS_PER_THREAD; k++) {
                        context.setValue("t" + thread + "-k" + k, (Object) k);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        int found = 0;
        for (int t = 0; t < THREADS; t++) {
            for (int k = 0; k < KEYS_PER_THREAD; k++) {
                if (Integer.valueOf(k).equals(context.getValue("t" + t + "-k" + k))) {
                    found++;
                }
            }
        }
        assertEquals(found, THREADS * KEYS_PER_THREAD, "entries were lost under concurrent writes");
    }

    @Test(description = "A null value is still allowed")
    public void nullValueIsAllowed() throws Exception {
        Context context = new Context();
        context.setValue("k", (Object) null);
        assertNull(context.getValue("k"));
        assertTrue(true);
    }
}
