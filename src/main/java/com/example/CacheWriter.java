package com.example;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.infinispan.Cache;

/**
 * Will update the cache periodically with taxi information
 */
class CacheWriter {

    private static final Logger logger = LogManager.getLogger();

    private final Cache<Integer, Location> cache;
    private final ScheduledExecutorService scheduledExecutorService;
    private final long period;
    private final TimeUnit timeUnit;

    /**
     * @param cache
     *            The cache to write to
     * @param period
     *            period to write
     * @param timeUnit
     *            TimeUnit
     */
    public CacheWriter(Cache<Integer, Location> cache, long period, TimeUnit timeUnit) {
        this.period = period;
        this.timeUnit = timeUnit;
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        this.cache = cache;
    }

    void start() {
        scheduledExecutorService.scheduleAtFixedRate(this::doWrite, 1, period, timeUnit);
    }

    public void stop() {
        scheduledExecutorService.shutdownNow();
    }

    private void doWrite() {
        int count = 5000;
        Random random = new Random(9999L);
        // Singapole
        double sgLat = 1.288724;
        double sgLon = 103.842672;

        logger.info("Fill {} data from a random source", count);
        for (int i = 1; i <= 5000; i++) {
            // Range of serveral killometers
            double dlat = (random.nextDouble() - 0.5) * 0.20;
            double dlon = (random.nextDouble() - 0.5) * 0.40;
            cache.put(i, new Location("taxi-" + i, sgLat + dlat, sgLon + dlon));
        }
        logger.info("Finished cache writing, cache size is {}", cache.size());
    }
}
