package com.splitbuys.backend.push;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PushConfig {
    /** Two workers and a bounded queue: this is a 1-CPU box, and a burst must be dropped, not allowed to pile up in memory. */
    @Bean(destroyMethod = "shutdown")
    ThreadPoolExecutor pushPool() {
        AtomicInteger n = new AtomicInteger();
        ThreadFactory threads = r -> {
            Thread t = new Thread(r, "push-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200), threads, new ThreadPoolExecutor.AbortPolicy());
    }
}
