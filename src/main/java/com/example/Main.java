package com.example;

import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.Query;
import org.hibernate.search.query.dsl.Unit;
import org.hibernate.search.spatial.impl.Point;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.Index;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;

public class Main {
    private static Logger log = LogManager.getLogger();

    public static void main(String[] args) {
        log.info("Start");
        Main test = new Main();
        test.test();
        test.cacheManager.stop();
        log.info("End");
    }
    
    private static void safeSleep(long l) {
        try {
            Thread.sleep(l);
        } catch (InterruptedException ignore) {}
    }

    private EmbeddedCacheManager cacheManager;
    private Cache<Integer, Location> cache;
    private Random random = new Random(9999L);

    public Main() {
        GlobalConfiguration gc = new GlobalConfigurationBuilder()
                .clusteredDefault()
                    .transport().addProperty("configurationFile",
                            "default-configs/default-jgroups-udp.xml")
                .globalJmxStatistics()
                    .allowDuplicateDomains(true).enable()
                .build();
        Configuration dc = new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.REPL_ASYNC)
                .indexing()
                    .index(Index.ALL)
                    .addProperty("default.directory_provider", "ram")
                    .addProperty("hibernate.search.option", "valueForOption")
                    .addProperty("default.indexmanager", "near-real-time")
                    .addProperty("default.worker.execution", "async")
                    .addProperty("default.worker.thread_pool.size", "32")
                    .addProperty("default.optimizer.operation_limit.max", "1500")
//                    .addProperty("hibernate.search.lucene_version", "LUCENE_CURRENT")
//                    .addIndexedEntity(Location.class)
                .jmxStatistics().enable()
                .build();
        this.cacheManager = new DefaultCacheManager(gc, dc);
        this.cache = cacheManager.getCache();
    }

    public void test() {
        log.info("Run test");
        int count = 1;
        double radius = 2.51;
        // singapole
        double sgLat = 1.288724;
        double sgLon = 103.842672;

        for (int i = 0; i < 5000; i++) {
            // range of serveral killometers
            double dlat = (random.nextDouble() - 0.5) * 0.38;
            double dlon = (random.nextDouble() - 0.5) * 0.21;
            cache.put(count, new Location("taxi-" + count, sgLat + dlat, sgLon + dlon));
            count++;
        }

//        log.info("Wait for a while");
//        safeSleep(30000L);

        int resQuery = 0;
        SearchManager searchManager = Search.getSearchManager(cache);
        Query query = searchManager
                .buildQueryBuilderForClass(Location.class)
                .get()
                .spatial()
                // .onDefaultCoordinates()
                .within(radius, Unit.KM)
                .ofLatitude(sgLat)
                .andLongitude(sgLon)
                .createQuery();
        CacheQuery cacheQuery = searchManager.getQuery(query);
        List list = cacheQuery.list();
        resQuery = list.size();
        log.info("Query returns {} results", resQuery);

        int resDirect = 0;
        for (Location loc : cache.values()) {
            double dist = Point.fromCoordinates(loc).getDistanceTo(sgLat, sgLon);
            if (dist < 0.0d)
                log.error("Something wrong with {}, dist is {}", loc, dist);
            if (dist <= radius)
                resDirect++;
        }
        log.info("Direct filtering returns {} results", resDirect);

        if (resQuery != resDirect) {
            log.error("Results don't match: query {}, direct {}", resQuery, resDirect);
        } else {
            log.info("Results match: {}", resQuery);
        }
    }
}
