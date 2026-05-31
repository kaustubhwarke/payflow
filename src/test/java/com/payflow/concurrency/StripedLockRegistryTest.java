package com.payflow.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StripedLockRegistry} (AAA pattern).
 */
class StripedLockRegistryTest {

    private final StripedLockRegistry registry = new StripedLockRegistry();

    @Test
    void executeWithLock_returnsSupplierResult() {
        // Act
        String result = registry.executeWithLock("alice@okaxis", () -> "done");

        // Assert
        assertThat(result).isEqualTo("done");
    }

    @Test
    void executeWithLocks_distinctStripes_returnsSupplierResult() {
        // Arrange: 'a' (97) and 'b' (98) map to distinct stripes -> exercises the two-lock path
        // Act
        Integer result = registry.executeWithLocks("a", "b", () -> 42);

        // Assert
        assertThat(result).isEqualTo(42);
    }

    @Test
    void executeWithLocks_sameStripe_takesSingleLock() {
        // Arrange: identical keys map to the same stripe -> exercises the single-lock branch
        // Act
        Integer result = registry.executeWithLocks("same", "same", () -> 7);

        // Assert
        assertThat(result).isEqualTo(7);
    }

    @Test
    void executeWithLock_serialisesConcurrentMutationOfSameKey() throws InterruptedException {
        // Arrange
        AtomicInteger counter = new AtomicInteger();
        int threads = 50;
        int incrementsPerThread = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(threads);

        // Act
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    registry.executeWithLock("account", () -> {
                        counter.set(counter.get() + 1); // non-atomic on purpose; lock must serialise
                        return null;
                    });
                }
                done.countDown();
            });
        }
        boolean finished = done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        // Assert
        assertThat(finished).isTrue();
        assertThat(counter.get()).isEqualTo(threads * incrementsPerThread);
    }
}
