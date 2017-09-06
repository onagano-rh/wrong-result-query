package com.example;

import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.Index;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

public class Main {
    private static Logger log = LogManager.getLogger();

    private static EmbeddedCacheManager cacheManager;
    private static Cache<String, CacheEntity> cache;
    static {
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

    public static void main(String[] args) {
        log.info("Begin main");

        int numThreads = Integer.parseInt(args[0]);
        long queryInterval = Long.parseLong(args[1]);

        // Wait 1 second, insert 10000 (or -Drepro.batch.size=<nnn>) entries, and wait forever.
        CacheWriter cacheWriter = new CacheWriter(cache, Long.MAX_VALUE, TimeUnit.SECONDS);
        cacheWriter.start();
        
        while (cache.size() < CacheWriter.BATCH_SIZE) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ignore) {}
        }
        log.info("Cache filled with {} entries", cache.size());

        // Repeat query, direct filtering, and sleep for queryInterval in numThreads threads.
        CacheReader cacheReader = new CacheReader(cache, queryInterval, TimeUnit.MILLISECONDS, numThreads);
        cacheReader.start();

        log.info("End main");
    }
}
