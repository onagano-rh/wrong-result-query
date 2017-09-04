package com.example;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
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
    
    private static final int HISTORY_SIZE = 10;

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
            executorService.submit(new QueryTask(i));
        });
    }
    
    private class QueryTask implements Runnable {
        private double radius;
        private Queue<Integer> diffHistory = new LinkedList<Integer>();
        
        QueryTask(int id) {
            this.radius = (id + 1) * 0.4;
        }
        
        public void run() {
            logger.info("Transaction spatialQuery started with radius{}", radius);
            int loopCount = 1;
            
            while (true) {

                int queryCount = queryResultFor(radius);
                int directCount = directResultFor(radius);
                logger.debug("Query count: {}, direct count: {}", queryCount, directCount);

                int diff = directCount - queryCount;
                diffHistory.add(diff);
                if (diffHistory.size() > HISTORY_SIZE) diffHistory.remove();
                if (diff != 0 && diffHistory.size() >= HISTORY_SIZE && hasAllTheSameElements(diffHistory)) {
                    // getting a wrong result consistently
                    logger.error("Reproduced with radius {}, query {}, direct {}", radius, queryCount, directCount);
                }
                
                if (loopCount % 1000 == 0) logger.info("Executing {} loops", loopCount);
                
                loopCount++;
                LockSupport.parkNanos(sleepTimeUnit.toNanos(sleep));
            }
            
        }
        
    }

    private int queryResultFor(double radius) {
        int queryCount = 0;
        SearchManager searchManager = Search.getSearchManager(cache);
        Query query = searchManager.buildQueryBuilderForClass(CacheEntity.class).get().spatial()
                .within(radius, Unit.KM)
                .ofLatitude(CacheWriter.CENTER_LATITUDE)
                .andLongitude(CacheWriter.CENTER_LONGITUDE)
                .createQuery();
        CacheQuery cacheQuery = searchManager.getQuery(query);
        List<Object> list = cacheQuery.list();
        queryCount = list.size();
        return queryCount;
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
