package com.example;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.Query;
import org.hibernate.search.query.dsl.Unit;
import org.hibernate.search.spatial.impl.Point;
import org.infinispan.Cache;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;

/**
 * Query the cache periodically
 */
class CacheReader {

    private static final Logger logger = LogManager.getLogger();

    private final Cache<String, CacheEntity> cache;
    private final long sleep;
    private final TimeUnit sleepTimeUnit;
    private final int numThreads;
    
    public static final int HISTORY_SIZE = Integer.parseInt(System.getProperty("repro.history.size", "100"));
    private volatile boolean reproduced = false;

    /**
     * @param cache
     *            Cache to query
     * @param sleep
     *            sleep between queries
     * @param sleepTimeUnit
     *            TimeUnit for the sleep
     * @param numThreads
     *            number of threads to use
     */

    CacheReader(Cache<String, CacheEntity> cache, long sleep, TimeUnit sleepTimeUnit, int numThreads) {
        this.cache = cache;
        this.sleep = sleep;
        this.sleepTimeUnit = sleepTimeUnit;
        this.numThreads = numThreads;
    }

    CacheReader(Cache<String, CacheEntity> cache, long sleep, TimeUnit sleepTimeUnit) {
        this(cache, sleep, sleepTimeUnit, 1);
    }

    void start() {
        logger.info("Start");
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        IntStream.range(0, numThreads).forEach(i -> {
            // Use different radius parameter to differentiate queries those are cached.
            double radius = (i + 1) * 0.4;
            executorService.submit(() -> doQuery(radius));
        });
    }
    
    private void doQuery(double radius) {
        logger.info("Transaction spatialQuery started with radius {}", radius);
        int loopCount = 0;
        Queue<Integer> diffHistory = new LinkedList<Integer>();
        boolean reproducedFirstTime = false;

        while (!reproduced) {
            if (loopCount % 1000 == 0) logger.info("Executing {}th loop", loopCount);

            int queryCount = queryResultFor(radius).size();
            int directCount = directResultFor(radius);
            logger.debug("Query count: {}, direct count: {}", queryCount, directCount);

            int diff = directCount - queryCount;
            if (diff != 0) logger.info("Number of records does not tally: {},{}", queryCount, directCount);

            diffHistory.add(diff);
            if (diffHistory.size() > HISTORY_SIZE) diffHistory.remove();

            if (diff != 0 && diffHistory.size() >= HISTORY_SIZE && hasAllTheSameElements(diffHistory)) {
                // Getting a wrong result consistently
                if (!reproducedFirstTime) {
                    logger.error("It seems reproduced but reset the history and sleep for 1 minute for sure");
                    reproducedFirstTime = true;
                    diffHistory = new LinkedList<Integer>();
                    try {
                        Thread.sleep(60000L);
                    } catch (InterruptedException ignore) {}
                }else {
                    logger.fatal("{} times returned different results with radius {}, query {}, direct {}",
                            HISTORY_SIZE, radius, queryCount, directCount);
                    reproduced = true;
                }
            }

            loopCount++;
            LockSupport.parkNanos(sleepTimeUnit.toNanos(sleep));
        }
        System.exit(1);
    }

    private List<CacheEntity> queryResultFor(double radius) {
        SearchManager searchManager = Search.getSearchManager(cache);
        Query query = searchManager.buildQueryBuilderForClass(CacheEntity.class).get().spatial()
                .within(radius, Unit.KM)
                .ofLatitude(CacheWriter.CENTER_LATITUDE)
                .andLongitude(CacheWriter.CENTER_LONGITUDE)
                .createQuery();
        CacheQuery<CacheEntity> cacheQuery = searchManager.getQuery(query, CacheEntity.class);
        return cacheQuery.list();
    }
    
    private int directResultFor(double radius) {
        int directCount = 0;
        for (CacheEntity taxi : cache.values()) {
            if (Point.fromCoordinates(taxi).getDistanceTo(CacheWriter.CENTER_LATITUDE, CacheWriter.CENTER_LONGITUDE)
                    <= radius) {
                directCount++;
            }
        }
        return directCount;
    }

    // is there a better stream way?
    private static boolean hasAllTheSameElements(Queue<Integer> queue) {
        boolean first = true;
        int lastValue = -1;
        for (int i : queue) {
            if (first) {
                first = false;
            } else {
                if (lastValue != i) return false;                
            }
            lastValue = i;
        }
        return true;
    }
}
