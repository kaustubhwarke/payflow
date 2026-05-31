package com.payflow.concurrency;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Fine-grained, application-level concurrency control (mentor feedback 22.a).
 *
 * <p>Rather than serialising all transfers behind one coarse lock or relying solely on
 * database row locks, transfers are serialised per <em>account</em> using a fixed array of
 * lock stripes keyed by UPI ID. Two transfers touching disjoint accounts proceed in parallel;
 * only transfers contending for the same account block each other. This bounds memory (a fixed
 * number of locks) while giving per-key isolation.</p>
 *
 * <p>When two accounts must be locked together (sender + receiver), the stripes are acquired in
 * ascending index order to guarantee a global lock ordering and thus prevent deadlock.</p>
 */
@Component
public class StripedLockRegistry {

    /** Power-of-two stripe count keeps the modulo cheap and distribution even. */
    private static final int STRIPE_COUNT = 256;

    private final ReentrantLock[] stripes;

    public StripedLockRegistry() {
        this.stripes = new ReentrantLock[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new ReentrantLock();
        }
    }

    /**
     * Runs {@code action} while holding the lock for a single key.
     *
     * @param key    the account key (UPI ID)
     * @param action work to perform under exclusive access
     * @param <T>    result type
     * @return the result of {@code action}
     */
    public <T> T executeWithLock(String key, Supplier<T> action) {
        ReentrantLock lock = stripeFor(key);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Runs {@code action} while holding the locks for two keys, acquired in a deadlock-free
     * global order. If both keys map to the same stripe, only one lock is taken.
     *
     * @param firstKey  one account key
     * @param secondKey the other account key
     * @param action    work to perform under exclusive access to both accounts
     * @param <T>       result type
     * @return the result of {@code action}
     */
    public <T> T executeWithLocks(String firstKey, String secondKey, Supplier<T> action) {
        int firstIndex = indexFor(firstKey);
        int secondIndex = indexFor(secondKey);

        // Acquire in ascending stripe order to impose a consistent global lock ordering.
        int lowIndex = Math.min(firstIndex, secondIndex);
        int highIndex = Math.max(firstIndex, secondIndex);

        ReentrantLock low = stripes[lowIndex];
        low.lock();
        try {
            if (lowIndex == highIndex) {
                return action.get();
            }
            ReentrantLock high = stripes[highIndex];
            high.lock();
            try {
                return action.get();
            } finally {
                high.unlock();
            }
        } finally {
            low.unlock();
        }
    }

    private ReentrantLock stripeFor(String key) {
        return stripes[indexFor(key)];
    }

    private int indexFor(String key) {
        return Math.floorMod(key.hashCode(), STRIPE_COUNT);
    }
}
