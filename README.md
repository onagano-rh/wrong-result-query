# wrong-result-query

This is a small JDG program to reproduce a query issue of [JDG-1002](https://issues.jboss.org/browse/JDG-1002)

There are only 2 classes. Firstly compile them by the following command:

    mvn compile

## To reproduce with JDG 7.0.0

The reproducer will exit with status code 1 when the issue reproduced.
You can run it by `mvn exec:exec`.
You'll get `ERROR` and `FATAL` log messages then the program will exit.

~~~
$ mvn exec:exec                                                        
[INFO] Scanning for projects...                      
[INFO]                                                                                                    
[INFO] ------------------------------------------------------------------------                           
[INFO] Building wrong-result-query 2.0.0             
[INFO] ------------------------------------------------------------------------                           
[INFO]                    
[INFO] --- exec-maven-plugin:1.3.2:exec (default-cli) @ wrong-result-query ---                            
10:46:15.024 [pool-7-thread-5] ERROR c.e.CacheReader:91 - It seems reproduced but reset the history and sleep for 1 minute for sure
10:46:15.857 [pool-7-thread-8] ERROR c.e.CacheReader:91 - It seems reproduced but reset the history and sleep for 1 minute for sure
10:46:16.059 [pool-7-thread-1] ERROR c.e.CacheReader:91 - It seems reproduced but reset the history and sleep for 1 minute for sure
10:46:16.181 [pool-7-thread-4] ERROR c.e.CacheReader:91 - It seems reproduced but reset the history and sleep for 1 minute for sure
10:46:16.730 [pool-7-thread-2] ERROR c.e.CacheReader:91 - It seems reproduced but reset the history and sleep for 1 minute for sure
10:46:16.854 [pool-7-thread-6] ERROR c.e.CacheReader:91 - It seems reproduced but reset the history and sleep for 1 minute for sure
10:46:16.948 [pool-7-thread-3] ERROR c.e.CacheReader:91 - It seems reproduced but reset the history and sleep for 1 minute for sure
10:46:17.533 [pool-7-thread-7] ERROR c.e.CacheReader:91 - It seems reproduced but reset the history and sleep for 1 minute for sure
10:47:21.525 [pool-7-thread-5] FATAL c.e.CacheReader:98 - 100 times returned different results with radius 2.0, query 292, direct 129
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 01:26 min
[INFO] Finished at: 2017-09-07T10:47:21+09:00
[INFO] Final Memory: 11M/375M
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.codehaus.mojo:exec-maven-plugin:1.3.2:exec (default-cli) on project wrong-result-query: Command execution failed. Process exited with an error: 1 (Exit value: 1) -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoExecutionException
~~~

Plain JDG 7.1.0 also has the same issue.

## To confirm the issue is fixed with JDG 7.0.0 and a patch of JDG-1020

Fistly, ask Red Hat Support for the patch, `infinispan-embedded-query-8.4.0.Final-redhat-2-jdg-1020.jar`
and install it into your local repository by the following command:

    $ mvn install:install-file
             -Dfile=/path/to/infinispan-embedded-query-8.4.0.Final-redhat-2-jdg-1020.jar
             -DgroupId=org.infinispan -DartifactId=infinispan-embedded-query
             -Dversion=8.4.0.Final-redhat-2-jdg-1020 -Dpackaging=jar

Then update the `pom.xml` as follows:

        <version.org.infinispan>8.4.0.Final-redhat-2</version.org.infinispan>
        ...
        <dependency>
            <groupId>org.infinispan</groupId>
            <artifactId>infinispan-embedded-query</artifactId>
            <version>8.4.0.Final-redhat-2-jdg-1020</version>
        </dependency>

This time `mvn exec:exec` runs forever without exitting.