# Play Framework & Pekko Migration - Quick Reference Guide

**Quick lookup for code transformations, commands, and common patterns during migration**

---

## Package Name Transformations

### Import Statement Changes

```java
// BEFORE (Akka)
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.UntypedAbstractActor;
import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import akka.routing.FromConfig;
import akka.routing.RouterConfig;
import akka.testkit.javadsl.TestKit;

// AFTER (Pekko)
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.UntypedAbstractActor;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.pattern.PatternsCS;
import org.apache.pekko.util.Timeout;
import org.apache.pekko.routing.FromConfig;
import org.apache.pekko.routing.RouterConfig;
import org.apache.pekko.testkit.javadsl.TestKit;
```

### Play Framework Imports

```java
// Pekko integration with Play
// BEFORE
import play.libs.akka.AkkaGuiceSupport;

// AFTER
import play.libs.pekko.PekkoGuiceSupport;
```

---

## Maven Dependency Changes

### Root POM Properties

```xml
<!-- BEFORE -->
<properties>
    <scala.major.version>2.12</scala.major.version>
    <scala.version>2.12.11</scala.version>
    <typesafe.akka.version>2.5.22</typesafe.akka.version>
    <play2.version>2.7.2</play2.version>
</properties>

<!-- AFTER -->
<properties>
    <scala.major.version>2.13</scala.major.version>
    <scala.version>2.13.12</scala.version>
    <pekko.version>1.0.2</pekko.version>
    <play2.version>3.0.1</play2.version>
</properties>
```

### Actor Dependencies

```xml
<!-- BEFORE (Akka) -->
<dependency>
    <groupId>com.typesafe.akka</groupId>
    <artifactId>akka-actor_${scala.major.version}</artifactId>
    <version>${typesafe.akka.version}</version>
</dependency>
<dependency>
    <groupId>com.typesafe.akka</groupId>
    <artifactId>akka-slf4j_${scala.major.version}</artifactId>
    <version>${typesafe.akka.version}</version>
</dependency>
<dependency>
    <groupId>com.typesafe.akka</groupId>
    <artifactId>akka-testkit_${scala.major.version}</artifactId>
    <version>${typesafe.akka.version}</version>
    <scope>test</scope>
</dependency>

<!-- AFTER (Pekko) -->
<dependency>
    <groupId>org.apache.pekko</groupId>
    <artifactId>pekko-actor_${scala.major.version}</artifactId>
    <version>${pekko.version}</version>
</dependency>
<dependency>
    <groupId>org.apache.pekko</groupId>
    <artifactId>pekko-slf4j_${scala.major.version}</artifactId>
    <version>${pekko.version}</version>
</dependency>
<dependency>
    <groupId>org.apache.pekko</groupId>
    <artifactId>pekko-testkit_${scala.major.version}</artifactId>
    <version>${pekko.version}</version>
    <scope>test</scope>
</dependency>
```

### Play Dependencies

```xml
<!-- BEFORE (Play 2.7 with Akka) -->
<dependency>
    <groupId>com.typesafe.play</groupId>
    <artifactId>play-akka-http-server_${scala.major.version}</artifactId>
    <version>${play2.version}</version>
</dependency>

<!-- AFTER (Play 3.0 with Pekko) -->
<dependency>
    <groupId>org.playframework</groupId>
    <artifactId>play-pekko-http-server_${scala.major.version}</artifactId>
    <version>${play2.version}</version>
</dependency>
```

**Note**: Play 3.0 also changed group ID from `com.typesafe.play` to `org.playframework`.

---

## Configuration File Changes

### application.conf Transformations

```hocon
# BEFORE (Akka)
akka {
  loggers = ["akka.event.slf4j.Slf4jLogger"]
  loglevel = "INFO"
  stdout-loglevel = "DEBUG"
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
  log-config-on-start = off

  actor {
    provider = "akka.actor.LocalActorRefProvider"
    
    serializers {
      java = "akka.serialization.JavaSerializer"
    }
    
    default-dispatcher {
      fork-join-executor {
        parallelism-min = 8
        parallelism-factor = 32.0
        parallelism-max = 64
      }
    }
  }
}

# AFTER (Pekko)
pekko {
  loggers = ["org.apache.pekko.event.slf4j.Slf4jLogger"]
  loglevel = "INFO"
  stdout-loglevel = "DEBUG"
  logging-filter = "org.apache.pekko.event.slf4j.Slf4jLoggingFilter"
  log-config-on-start = off

  actor {
    provider = "org.apache.pekko.actor.LocalActorRefProvider"
    
    serializers {
      java = "org.apache.pekko.serialization.JavaSerializer"
    }
    
    default-dispatcher {
      fork-join-executor {
        parallelism-min = 8
        parallelism-factor = 32.0
        parallelism-max = 64
      }
    }
  }
}
```

### Remote Actor Configuration

```hocon
# BEFORE (Akka Remote)
akka {
  actor {
    provider = "akka.remote.RemoteActorRefProvider"
  }
  remote {
    enabled-transports = ["akka.remote.netty.tcp"]
    netty.tcp {
      hostname = "127.0.0.1"
      port = 2552
    }
  }
}

# AFTER (Pekko Remote)
pekko {
  actor {
    provider = "org.apache.pekko.remote.RemoteActorRefProvider"
  }
  remote {
    enabled-transports = ["org.apache.pekko.remote.netty.tcp"]
    netty.tcp {
      hostname = "127.0.0.1"
      port = 2552
    }
  }
}
```

---

## Code Pattern Transformations

### Actor Class Definition

```java
// BEFORE
import akka.actor.UntypedAbstractActor;
import akka.util.Timeout;

public class MyActor extends UntypedAbstractActor {
    protected static Timeout timeout = new Timeout(30, TimeUnit.SECONDS);
    
    @Override
    public void onReceive(Object message) throws Throwable {
        // Handle message
    }
}

// AFTER
import org.apache.pekko.actor.UntypedAbstractActor;
import org.apache.pekko.util.Timeout;

public class MyActor extends UntypedAbstractActor {
    protected static Timeout timeout = new Timeout(30, TimeUnit.SECONDS);
    
    @Override
    public void onReceive(Object message) throws Throwable {
        // Handle message (no logic change needed)
    }
}
```

### Actor Communication (Ask Pattern)

```java
// BEFORE
import akka.actor.ActorRef;
import akka.pattern.PatternsCS;
import akka.util.Timeout;

public CompletionStage<Result> handleRequest(
    ActorRef actorRef, Request request, Timeout timeout) {
    return PatternsCS.ask(actorRef, request, timeout)
        .thenApply(response -> {
            // Handle response
            return ok(Json.toJson(response));
        });
}

// AFTER
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.PatternsCS;
import org.apache.pekko.util.Timeout;

public CompletionStage<Result> handleRequest(
    ActorRef actorRef, Request request, Timeout timeout) {
    return PatternsCS.ask(actorRef, request, timeout)
        .thenApply(response -> {
            // Handle response (no logic change needed)
            return ok(Json.toJson(response));
        });
}
```

### Actor System Creation

```java
// BEFORE
import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

Config config = ConfigFactory.load();
ActorSystem system = ActorSystem.create("MySystem", config);

// AFTER
import org.apache.pekko.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

Config config = ConfigFactory.load();
ActorSystem system = ActorSystem.create("MySystem", config);
```

### Dependency Injection Module (Play)

```java
// BEFORE
import akka.routing.FromConfig;
import akka.routing.RouterConfig;
import com.google.inject.AbstractModule;
import play.libs.akka.AkkaGuiceSupport;

public class ActorStartModule extends AbstractModule implements AkkaGuiceSupport {
    @Override
    protected void configure() {
        final RouterConfig config = new FromConfig();
        bindActor(MyActor.class, "my-actor", 
            props -> props.withRouter(config));
    }
}

// AFTER
import org.apache.pekko.routing.FromConfig;
import org.apache.pekko.routing.RouterConfig;
import com.google.inject.AbstractModule;
import play.libs.pekko.PekkoGuiceSupport;

public class ActorStartModule extends AbstractModule implements PekkoGuiceSupport {
    @Override
    protected void configure() {
        final RouterConfig config = new FromConfig();
        bindActor(MyActor.class, "my-actor", 
            props -> props.withRouter(config));
    }
}
```

### Test Class

```java
// BEFORE
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

public class MyActorTest {
    private static ActorSystem system;
    
    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }
    
    @Test
    public void testMyActor() {
        TestKit probe = new TestKit(system);
        ActorRef subject = system.actorOf(Props.create(MyActor.class));
        
        subject.tell(new MyMessage(), probe.getRef());
        Response response = probe.expectMsgClass(Response.class);
        
        Assert.assertNotNull(response);
    }
}

// AFTER
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.testkit.javadsl.TestKit;

public class MyActorTest {
    private static ActorSystem system;
    
    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }
    
    @Test
    public void testMyActor() {
        TestKit probe = new TestKit(system);
        ActorRef subject = system.actorOf(Props.create(MyActor.class));
        
        subject.tell(new MyMessage(), probe.getRef());
        Response response = probe.expectMsgClass(Response.class);
        
        Assert.assertNotNull(response);
    }
}
```

---

## Useful Commands

### Search and Replace Commands

#### Find all Akka imports
```bash
grep -r "import akka" --include="*.java" .
```

#### Find all Akka configuration
```bash
grep -r "akka\." --include="*.conf" .
```

#### Count files with Akka imports
```bash
find . -name "*.java" -type f -exec grep -l "import akka" {} \; | wc -l
```

#### List all actor classes
```bash
find . -name "*Actor.java" -type f
```

### Automated Search/Replace (Use with Caution!)

```bash
# Replace Java imports (review changes before committing!)
find . -name "*.java" -type f -exec sed -i 's/import akka\./import org.apache.pekko./g' {} \;

# Replace configuration (review changes before committing!)
find . -name "*.conf" -type f -exec sed -i 's/akka\./pekko./g' {} \;
find . -name "*.conf" -type f -exec sed -i 's/"akka\./"org.apache.pekko./g' {} \;
```

**⚠️ WARNING**: Always review automated changes carefully. Use version control and test thoroughly.

### Maven Commands

```bash
# Clean build
mvn clean install

# Skip tests during build
mvn clean install -DskipTests

# Run tests only
mvn test

# View dependency tree
mvn dependency:tree

# View dependency conflicts
mvn dependency:tree -Dverbose

# Build distribution package
cd service
mvn play2:dist

# Check for dependency updates
mvn versions:display-dependency-updates

# Check for plugin updates
mvn versions:display-plugin-updates
```

### Git Commands for Migration

```bash
# Create migration branch
git checkout -b migration/play3-pekko

# Stage changes
git add .

# Commit with descriptive message
git commit -m "Phase 3: Migrate Akka imports to Pekko in actor-core module"

# Push to remote
git push origin migration/play3-pekko

# Create checkpoint tag
git tag -a checkpoint-phase3 -m "Completed Phase 3: Pekko migration"
git push origin checkpoint-phase3
```

---

## Testing Commands

### Unit Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=MyActorTest

# Run specific test method
mvn test -Dtest=MyActorTest#testMyActor

# Run tests with coverage
mvn clean test jacoco:report
```

### Integration Tests

```bash
# Run integration tests (if separated)
mvn verify -P integration-tests

# Run with specific profile
mvn test -P jacoco
```

### Performance Tests

```bash
# Using JMeter (if configured)
mvn jmeter:jmeter

# Using Gatling (if configured)
mvn gatling:test
```

---

## Common Issues and Solutions

### Issue 1: Compilation Error - Package Not Found

**Error**:
```
[ERROR] package akka.actor does not exist
```

**Solution**:
- Verify Pekko dependency added to pom.xml
- Check Scala version matches (2.13)
- Run `mvn clean install -U` to force update

### Issue 2: ClassNotFoundException at Runtime

**Error**:
```
java.lang.ClassNotFoundException: akka.actor.ActorRef
```

**Solution**:
- Some transitive dependency still pulls Akka
- Run `mvn dependency:tree | grep akka`
- Add exclusions for Akka in problematic dependencies
- Ensure all modules use Pekko

### Issue 3: Configuration Not Loaded

**Error**:
```
No configuration setting found for key 'pekko.actor'
```

**Solution**:
- Check application.conf uses `pekko` not `akka`
- Verify configuration file in correct location
- Check for typos in configuration keys
- Use quotes for fully-qualified class names

### Issue 4: Actor System Not Starting

**Error**:
```
Unable to create actor system
```

**Solution**:
- Check logger configuration: `org.apache.pekko.event.slf4j.Slf4jLogger`
- Verify actor provider is correct
- Check for conflicting actor system names
- Review logs for detailed error message

### Issue 5: Test Failures

**Error**:
```
akka.testkit not found
```

**Solution**:
- Update test dependencies to pekko-testkit
- Update test imports
- Check TestKit initialization
- Verify actor system creation in tests

---

## Verification Checklist

After each phase, verify:

```bash
# 1. Project builds successfully
mvn clean install
# Expected: BUILD SUCCESS

# 2. No Akka references remain
grep -r "import akka" --include="*.java" .
grep -r "com.typesafe.akka" --include="*.xml" .
grep -r "akka\." --include="*.conf" .
# Expected: No results

# 3. All tests pass
mvn test
# Expected: All tests pass

# 4. Application starts
mvn play2:run  # or equivalent
# Expected: Application starts without errors

# 5. Verify Pekko is loaded
# Check logs for: "org.apache.pekko.actor.ActorSystem"
```

---

## Rollback Commands

If issues occur and rollback needed:

```bash
# Discard all changes in working directory
git reset --hard HEAD

# Revert to specific commit
git reset --hard <commit-hash>

# Revert specific file
git checkout HEAD -- path/to/file

# Create rollback branch for investigation
git checkout -b rollback-investigation
```

---

## Documentation Updates Needed

After migration, update:

- [ ] README.md - Update tech stack section
- [ ] CONTRIBUTING.md - Update development setup
- [ ] Architecture docs - Update actor system diagrams
- [ ] API docs - Update if needed
- [ ] Deployment docs - Update build commands
- [ ] Troubleshooting guide - Add Pekko-specific issues

---

## Quick Links

- **Pekko Documentation**: https://pekko.apache.org/docs/pekko/current/
- **Play 3.0 Documentation**: https://www.playframework.com/documentation/3.0.x/
- **Migration Guide**: https://pekko.apache.org/docs/pekko/current/project/migration-guides.html
- **Play Migration Guide**: https://www.playframework.com/documentation/3.0.x/Migration30

---

## Version Compatibility Matrix

| Component | Version | Scala | Java | Notes |
|-----------|---------|-------|------|-------|
| Pekko | 1.0.2 | 2.12, 2.13, 3.x | 8, 11, 17, 21 | Current stable |
| Play | 3.0.1 | 2.13, 3.x | 11, 17, 21 | Requires Pekko |
| Scala | 2.13.12 | - | 8, 11, 17, 21 | For Play 3.0 |
| Jackson | 2.16.x | - | 8, 11, 17, 21 | Current stable |

---

**Quick Reference Version**: 1.0  
**Last Updated**: January 2025  
**For**: lms-service migration to Play 3.0 + Pekko 1.0
