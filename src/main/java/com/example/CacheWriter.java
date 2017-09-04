package com.example;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.infinispan.Cache;

/**
 * Will update the cache periodically with taxi information
 */
class CacheWriter {

    private static final Logger logger = LogManager.getLogger();
    
    public static final int BATCH_SIZE = Integer.parseInt(System.getProperty("repro.batch.size", "5000"));
    public static final double CENTER_LATITUDE = Double.parseDouble(System.getProperty("repro.center.latitude", "1.288724"));
    public static final double CENTER_LONGITUDE = Double.parseDouble(System.getProperty("repro.center.longitude", "103.842672"));

    private final Cache<String, CacheEntity> cache;
    private final ScheduledExecutorService scheduledExecutorService;
    private final long period;
    private final TimeUnit timeUnit;
    private final int numThreads;
    
    private int count = 0;
    private ThreadLocalRandom random = ThreadLocalRandom.current();

    /**
     * @param cache
     *            The cache to write to
     * @param period
     *            period to write
     * @param timeUnit
     *            TimeUnit
     * @param numThreads
     *            Number of threads writing
     */
    public CacheWriter(Cache<String, CacheEntity> cache, long period, TimeUnit timeUnit, int numThreads) {
        this.period = period;
        this.timeUnit = timeUnit;
        scheduledExecutorService = Executors.newScheduledThreadPool(numThreads);
        this.cache = cache;
        this.numThreads = numThreads;
    }

    public CacheWriter(Cache<String, CacheEntity> cache, long period, TimeUnit timeUnit) {
        this(cache, period, timeUnit, 1);
    }

    void start() {
        logger.info("Start");
        IntStream.range(0, numThreads).forEach(i -> {
            scheduledExecutorService.scheduleAtFixedRate(this::doWrite, 1, period, timeUnit);
        });
    }

    public void stop() {
        logger.info("Stopping...");
        scheduledExecutorService.shutdownNow();
        logger.info("Stopped");
    }

    private void doWrite() {
        logger.info("Fill {} data from a random source", BATCH_SIZE);
        for (int i = 0; i < BATCH_SIZE; i++) {
            // Range of serveral killometers
            synchronized (this) {
                double dlat = (random.nextDouble() - 0.5) * 0.20;
                double dlon = (random.nextDouble() - 0.5) * 0.40;
                count++;
                cache.put(String.valueOf(count),
                        new CacheEntity("taxi-" + count, CENTER_LATITUDE + dlat, CENTER_LONGITUDE + dlon));
            }
        }
        logger.info("Finished cache writing, cache size is {}", cache.size());
    }
}
