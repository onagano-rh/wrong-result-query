package com.example;

import static org.junit.Assert.assertEquals;

import java.util.List;

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

    private static int doSpatialQuery(double radius, Unit unit, double latitude, double longitude) {
        SearchManager searchManager = Search.getSearchManager(cache);
        Query query = searchManager.buildQueryBuilderForClass(CacheEntity.class).get().spatial()
                .within(radius, Unit.KM)
                .ofLatitude(CacheWriter.CENTER_LATITUDE)
                .andLongitude(CacheWriter.CENTER_LONGITUDE)
                .createQuery();
        CacheQuery cacheQuery = searchManager.getQuery(query);
        List<Object> list = cacheQuery.list();
        return list.size();
    }

    private static double calcDistanceByHS(double latitude, double longitude, Coordinates from) {
        return Point.fromCoordinates(from).getDistanceTo(latitude, longitude);
    }

    @Test
    public void testSpatialQuery() {
        cache.put("key01", new CacheEntity("ce01", 1.3260100000000001, 103.91293999999999));
        cache.put("key02", new CacheEntity("ce02", 1.27329, 103.91422));

        assertEquals("Not indexed yet", 0, doSpatialQuery(2.5, Unit.KM, 1.15999, 103.9927));

        try {
            Thread.sleep(10000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals("Still should be zero", 0, doSpatialQuery(2.5, Unit.KM, 1.15999, 103.9927));
    }

    @Test
    public void testDistanceCalculation() {
        CacheEntity p0 = new CacheEntity("ce00", 1.15999, 103.9927);
        CacheEntity p1 = new CacheEntity("ce01", 1.3260100000000001, 103.91293999999999);
        CacheEntity p2 = new CacheEntity("ce02", 1.27329, 103.91422);
        assertEquals(20.47958802591737, calcDistanceByHS(p1.getLatitude(), p1.getLongitude(), p0), 0.00000000001);
        assertEquals(15.32442864117991, calcDistanceByHS(p2.getLatitude(), p2.getLongitude(), p0), 0.00000000001);
    }
}
