package com.ecommerce.workflow.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class AiRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(AiRateLimiter.class);

    private final Semaphore concurrentCalls;
    private final long rateLimitIntervalMs;
    private volatile long lastCallTime = 0;

    public AiRateLimiter() {
        this.concurrentCalls = new Semaphore(15);
        this.rateLimitIntervalMs = 100;
    }

    public boolean tryAcquire(long timeoutMs) {
        try {
            long now = System.currentTimeMillis();
            long waitTime = rateLimitIntervalMs - (now - lastCallTime);
            if (waitTime > 0) {
                Thread.sleep(waitTime);
            }

            boolean acquired = concurrentCalls.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            if (acquired) {
                lastCallTime = System.currentTimeMillis();
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void release() {
        concurrentCalls.release();
    }

    public int getAvailablePermits() {
        return concurrentCalls.availablePermits();
    }
}
