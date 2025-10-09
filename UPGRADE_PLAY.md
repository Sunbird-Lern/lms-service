# Play Framework 3.0.5 + Apache Pekko 1.0.2 Upgrade

## Overview

This guide covers the upgrade from Play Framework 2.7.2 + Akka 2.5.22 + Scala 2.12.11 to Play Framework 3.0.5 + Apache Pekko 1.0.2 + Scala 2.13.12.

## Why This Upgrade

1. End of Life Software: Play 2.7.2 and Akka 2.5.22 were released in 2019 and no longer receive security updates
2. License Compliance: Akka changed to Business Source License v1.1 requiring commercial licenses. Apache Pekko is Apache License 2.0
3. Security: Active maintenance and regular security patches
4. Modern Features: Scala 2.13 performance improvements and Play 3.0 enhancements

## Technology Stack

- Play Framework: 2.7.2 to 3.0.5
- Apache Pekko: 1.0.2 (replacing Akka 2.5.22)
- Scala: 2.12.11 to 2.13.12
- Java: 11 (compatible with 11, 17, 21)
- SLF4J: 2.0.9
- Logback: 1.4.14
- Jackson: 2.14.3
- Guice: 5.1.0

## Key Changes

### 1. Play Framework

- GroupId changed: com.typesafe.play to org.playframework
- play-akka-http-server replaced with play-pekko-http-server
- Logger API updated: Logger.logger to Logger(class)

### 2. Akka to Pekko Migration

All Akka imports changed to Pekko:
- import akka.actor.* to import org.apache.pekko.actor.*
- import akka.pattern.* to import org.apache.pekko.pattern.*
- import akka.stream.* to import org.apache.pekko.stream.*
- import akka.testkit.* to import org.apache.pekko.testkit.*

Configuration files updated:
- akka {} to pekko {}
- akka.actor.provider to pekko.actor.provider

### 3. Scala 2.13 Collection API

- filterKeys() to view.filterKeys().toMap
- mapValues() to view.mapValues().toMap
- JavaConverters to CollectionConverters
- Added explicit .asScala and .asJava conversions

### 4. Test Framework

- JavaTestKit.duration("10 second") to Duration.ofSeconds(10)
- UntypedAbstractActor to AbstractActor with createReceive()
- Import java.time.Duration for test timeouts

## Build Instructions

### Prerequisites

- Java 11
- Maven 3.6 or higher

### Mac Apple Silicon Users

Add this dependency to root pom.xml for native performance:

```xml
<dependency>
 <groupId>io.netty</groupId>
 <artifactId>netty-all</artifactId>
 <version>4.1.93.Final</version>
</dependency>
<dependency>
 <groupId>io.netty</groupId>
 <artifactId>netty-resolver-dns-native-macos</artifactId>
 <version>4.1.93.Final</version>
 <classifier>osx-aarch_64</classifier>
</dependency>
```

Update ```play.server.netty.transport = "jdk"``` from native (if required)

Update io.netty version to 4.1.93.Final in your dependency management.

### Build Steps

1. Clean build:
 - Completely skips compilation and execution of tests    
    ```bash
    mvn clean install -Dmaven.test.skip=true -U
    ```

 - OR (Skips running tests, but still compiles them.)

    ```bash
    mvn clean install -DskipTests
    ```

2. Package distribution:
```bash
cd service
mvn play2:dist
```

3. Extract and run:
```bash
cd target
tar xvzf lms-service-1.0-SNAPSHOT-dist.zip
cd lms-service-1.0-SNAPSHOT
./start
```

## Files Modified

### Maven POMs
All files

### Java Files
All files with Akka imports updated to use Pekko

### Scala Files
Updated for Scala 2.13 collection API compatibility

### Configuration Files
- service/conf/application.conf
- Updated akka to pekko namespaces

## Dependency Management

### Key Dependencies Added

```xml
<!-- Scala Runtime -->
<dependency>
    <groupId>org.scala-lang</groupId>
    <artifactId>scala-library</artifactId>
    <version>2.13.12</version>
</dependency>

<dependency>
    <groupId>org.scala-lang</groupId>
    <artifactId>scala-reflect</artifactId>
    <version>2.13.12</version>
</dependency>

<!-- Jackson Scala Module -->
<dependency>
    <groupId>com.fasterxml.jackson.module</groupId>
    <artifactId>jackson-module-scala_2.13</artifactId>
    <version>2.14.3</version>
</dependency>

<!-- SLF4J and Logback -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.9</version>
</dependency>

<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.4.14</version>
</dependency>
```

### Dependency Exclusions

Added exclusions to prevent Scala 2.12 and old Jackson conflicts:

```xml
<!-- Exclude Scala 2.12 Jackson from cloud-store-sdk -->
<dependency>
    <groupId>org.sunbird</groupId>
    <artifactId>cloud-store-sdk_2.12</artifactId>
    <version>1.4.7</version>
    <exclusions>
        <exclusion>
            <groupId>com.fasterxml.jackson.module</groupId>
            <artifactId>jackson-module-scala_2.12</artifactId>
        </exclusion>
        <exclusion>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- Exclude old Jackson from resteasy -->
<dependency>
    <groupId>org.jboss.resteasy</groupId>
    <artifactId>resteasy-jackson2-provider</artifactId>
    <version>3.1.3.Final</version>
    <exclusions>
        <exclusion>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>*</artifactId>
        </exclusion>
        <exclusion>
            <groupId>com.fasterxml.jackson.jaxrs</groupId>
            <artifactId>*</artifactId>
        </exclusion>
        <exclusion>
            <groupId>com.fasterxml.jackson.module</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```
