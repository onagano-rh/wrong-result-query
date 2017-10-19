package com.example;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.apache.lucene.search.Query;
import org.hibernate.search.query.dsl.Unit;
import org.hibernate.search.spatial.Coordinates;
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
import org.junit.BeforeClass;
import org.junit.Test;

public class MainTest {
    private static EmbeddedCacheManager cacheManager;
    private static Cache<String, CacheEntity> cache;

    @BeforeClass
    public static void setup() {
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
//                    .addIndexedEntity(CacheEntity.class)
                .jmxStatistics().enable()
                .build();
        cacheManager = new DefaultCacheManager(gc, dc);
        cache = cacheManager.getCache();
    }

    private static List<CacheEntity> doSpatialQuery(double radius, Unit unit, double latitude, double longitude) {
        SearchManager searchManager = Search.getSearchManager(cache);
        Query query = searchManager.buildQueryBuilderForClass(CacheEntity.class).get().spatial()
                .within(radius, unit)
                .ofLatitude(latitude)
                .andLongitude(longitude)
                .createQuery();
        CacheQuery<CacheEntity> cacheQuery = searchManager.getQuery(query, CacheEntity.class);
        return cacheQuery.list();
    }

    private static double calcDistanceByHS(Coordinates from, double latitude, double longitude) {
        return Point.fromCoordinates(from).getDistanceTo(latitude, longitude);
    }
    
    private static void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignore) {
        }
    }
    
    private static void putRandomData(Coordinates center, double deltaLatitude, double deltaLongitude, int num) {
        Random random = new Random();
        for (int i = 0; i < num; i++) {
            double dlat = (random.nextDouble() - 0.5) * deltaLatitude;
            double dlon = (random.nextDouble() - 0.5) * deltaLongitude;
            cache.put(String.valueOf(i),
                    new CacheEntity("taxi-" + i, center.getLatitude() + dlat, center.getLongitude() + dlon));
        }
    }
    
    @Test
    public void testReturnedRadius() {
        CacheEntity p0 = new CacheEntity("ce00", 1.15999, 103.9927);
        putRandomData(p0, 0.90, 1.80, 10000);
        safeSleep(10000L);
        List<CacheEntity> results = doSpatialQuery(2.5, Unit.KM, p0.getLatitude(), p0.getLongitude());
        for (CacheEntity c : results) {
            double d = calcDistanceByHS(p0, c.getLatitude(), c.getLongitude());
            assertTrue(c + " is distant of " + d, d <= 2.5);
        }
    }

    @Test
    public void testDistanceCalculation() {
        CacheEntity p0 = new CacheEntity("ce00", 1.15999, 103.9927);
        CacheEntity p1 = new CacheEntity("ce01", 1.3260100000000001, 103.91293999999999);
        CacheEntity p2 = new CacheEntity("ce02", 1.27329, 103.91422);
        CacheEntity p3 = new CacheEntity("ce03", 1.297660, 103.829837);
        assertEquals(20.47958802591737, calcDistanceByHS(p0, p1.getLatitude(), p1.getLongitude()), 0.00000000001);
        assertEquals(15.32442864117991, calcDistanceByHS(p0, p2.getLatitude(), p2.getLongitude()), 0.00000000001);
        assertEquals(23.70958543796764, calcDistanceByHS(p0, p3.getLatitude(), p3.getLongitude()), 0.0001);
    }
}
