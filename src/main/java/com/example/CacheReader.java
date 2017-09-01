package com.example;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.List;
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

    private final Cache<Integer, Location> cache;
    private final long sleep;
    private final TimeUnit sleepTimeUnit;
    private final int numThreads;

    private static final double RADIUS = 2.51;
    private static final double X = 103.842672;
    private static final double Y = 1.288724;

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

    CacheReader(Cache<Integer, Location> cache, long sleep, TimeUnit sleepTimeUnit, int numThreads) {
        this.cache = cache;
        this.sleep = sleep;
        this.sleepTimeUnit = sleepTimeUnit;
        this.numThreads = numThreads;
    }

    CacheReader(Cache<Integer, Location> cache, long sleep, TimeUnit sleepTimeUnit) {
        this(cache, sleep, sleepTimeUnit, 1);
    }

    void start() {
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        IntStream.range(0, numThreads).forEach(i -> {
            executorService.submit(this::doQuery);
        });
    }

    private void doQuery() {
        System.out.println("Transaction spatialQuery started");
        SearchManager searchManager = Search.getSearchManager(cache);

        while (true) {
            long start = System.nanoTime();

            Query query = searchManager.buildQueryBuilderForClass(Location.class).get().spatial()
                    .within(RADIUS, Unit.KM).ofLatitude(Y).andLongitude(X).createQuery();

            CacheQuery cacheQuery = searchManager.getQuery(query);
            List<Object> list = cacheQuery.list();

            logger.info("spatialQuery Time taken to search (ms): {}", NANOSECONDS.toMillis(System.nanoTime() - start));

            int directCount = 0;
            for (Location taxi : cache.values()) {
                if (Point.fromCoordinates(taxi).getDistanceTo(Y, X) <= RADIUS) {
                    directCount++;
                }
            }

            logger.info("{},{}", list.size(), directCount);
            if (list.size() != directCount) {
                logger.error("number of records does not tally: {},{}", list.size(), directCount);
            }
            LockSupport.parkNanos(sleepTimeUnit.toNanos(sleep));
        }
    }

}
