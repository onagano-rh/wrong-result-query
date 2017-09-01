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
    private static Cache<Integer, Location> cache;
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
//                    .addIndexedEntity(Location.class)
                .jmxStatistics().enable()
                .build();
        cacheManager = new DefaultCacheManager(gc, dc);
        cache = cacheManager.getCache();
    }

    public static void main(String[] args) {
        log.info("Start");
        if (args.length < 2) {
            usage();
        }

        long writeInterval = Long.parseLong(args[0]);
        long queryInterval = Long.parseLong(args[1]);

        CacheWriter cacheWriter = new CacheWriter(cache, writeInterval, TimeUnit.SECONDS);
        cacheWriter.start();

        CacheReader cacheReader = new CacheReader(cache, queryInterval, TimeUnit.MILLISECONDS);
        cacheReader.start();

        log.info("End");
    }

    private static void usage() {
        System.out.println("Usage: Main [WRITE_INTERVAL_SECS] [QUERY_INTERVAL_MS]");
        System.out.println();
        System.out.println("       WRITE_INTERVAL_SECS : interval in seconds to write taxi information to the cache, without purging");
        System.out.println("       QUERY_INTERVAL_MS : interval in millis between one query and another");
        System.exit(0);
    }
}
