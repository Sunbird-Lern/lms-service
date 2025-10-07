# Play Framework Upgrade & Akka to Apache Pekko Migration Compatibility Report

**Repository**: SNT01/lms-service  
**Report Date**: January 2025  
**Current Analysis**: Migration Feasibility Study

---

## Executive Summary

This report provides a comprehensive analysis of upgrading Play Framework and migrating from Akka to Apache Pekko in the lms-service repository. The analysis covers current state, migration paths, compatibility issues, benefits, drawbacks, and detailed recommendations.

### Key Findings

1. **Current Technology Stack**:
   - Play Framework: **2.7.2** (Released May 2019, EOL)
   - Akka: **2.5.22** (Released April 2019, EOL) 
   - Scala: **2.12.11**
   - Java: **11**
   - Build Tool: **Maven with SBT-like play2-maven-plugin**

2. **Migration Necessity**: 
   - ✅ **CRITICAL**: Both Play 2.7.x and Akka 2.5.x are End-of-Life and no longer receive security updates
   - ✅ **LICENSE CONCERN**: Akka changed to Business Source License (BSL) v1.1 starting from version 2.7+ (September 2022)
   - ✅ **RECOMMENDED**: Migration to Apache Pekko (Apache 2.0 license) and modern Play Framework

3. **Impact Assessment**:
   - **68 Java files** contain Akka imports (124 total import statements)
   - **26 Actor classes** across the codebase
   - **18 Actor files** in course-mw module
   - Heavy integration with Play Framework's Akka support
   - Configuration-heavy deployment with router configurations

---

## Table of Contents

1. [Current State Analysis](#current-state-analysis)
2. [Migration Paths](#migration-paths)
3. [Detailed Compatibility Analysis](#detailed-compatibility-analysis)
4. [Code Impact Assessment](#code-impact-assessment)
5. [Benefits](#benefits)
6. [Drawbacks and Challenges](#drawbacks-and-challenges)
7. [Risk Assessment](#risk-assessment)
8. [Migration Strategy](#migration-strategy)
9. [Effort Estimation](#effort-estimation)
10. [Recommendations](#recommendations)

---

## 1. Current State Analysis

### 1.1 Play Framework Usage

**Current Version**: 2.7.2 (May 2019)

#### Dependencies
```xml
<play2.version>2.7.2</play2.version>
<play2.plugin.version>1.0.0-rc5</play2.plugin.version>

Dependencies:
- play-guice_2.12
- play-netty-server_2.12
- play_2.12
- filters-helpers_2.12
- play-akka-http-server_2.12 (Critical: Tight Akka coupling)
- play-specs2_2.12
- play-logback_2.12
```

#### Key Features Used
- **HTTP Controllers**: Extensive use of Play controllers
- **Dependency Injection**: Guice-based DI with custom modules
- **Filters**: Custom Gzip filter, Access log filter, Response filter
- **Configuration**: Typesafe Config for application settings
- **JSON Handling**: Jackson integration for JSON processing
- **Akka Integration**: Deep integration via `play-akka-http-server`

#### Play Version Status
- **2.7.x**: Last release 2.7.9 (March 2020) - **EOL**
- **2.8.x**: Last release 2.8.22 (September 2023) - **Maintenance Only**
- **2.9.x**: Last release 2.9.5 (October 2024) - **Maintenance/EOL**
- **3.0.x**: Current LTS (Released May 2023) - **Active Support**

### 1.2 Akka Framework Usage

**Current Version**: 2.5.22 (April 2019)

#### Akka Dependencies Across Modules

1. **service/pom.xml**:
   ```xml
   <typesafe.akka.version>2.5.22</typesafe.akka.version>
   - akka-testkit_2.12 (test scope)
   ```

2. **actor-core/pom.xml**:
   ```xml
   - akka-actor_2.12:2.5.22
   - akka-slf4j_2.12:2.5.22
   ```

3. **course-actors-common/pom.xml**:
   ```xml
   - akka-actor_2.12:2.5.22
   - akka-slf4j_2.12:2.5.22
   - akka-testkit_2.12:2.5.22 (test scope)
   ```

4. **Multiple other modules** in course-mw hierarchy

#### Akka Features Used

1. **Actor Model**:
   - `UntypedAbstractActor` - Base actor class (10 usages)
   - Custom actor implementations (26+ actor classes)
   - Actor hierarchies and supervision

2. **Actor Communication**:
   - `ActorRef` - Actor references for message passing
   - `ActorSelection` - Dynamic actor lookup
   - `Patterns.ask` / `PatternsCS.ask` - Request-response pattern
   - `tell` - Fire-and-forget messaging
   - `Timeout` - Message timeout handling

3. **Routing**:
   - `FromConfig` - Router configuration from application.conf
   - `RouterConfig` - Router setup
   - Dispatcher configuration (5 custom dispatchers)
   - Pool routers (smallest-mailbox-pool)

4. **Remote Actors** (Limited):
   - RemoteActorRefProvider configuration support
   - Akka remote netty transport (not actively used in current config)

5. **Testing**:
   - `akka-testkit` for actor testing
   - `TestKit` class usage
   - Actor test infrastructure

#### Critical Akka Usage Points

1. **BaseActor.java** (actor-core):
   ```java
   public abstract class BaseActor extends UntypedAbstractActor {
       protected static Timeout timeout = new Timeout(AKKA_WAIT_TIME, TimeUnit.SECONDS);
       // Core actor implementation
   }
   ```

2. **BaseController.java** (service):
   ```java
   public CompletionStage<Result> actorResponseHandler(
       Object actorRef,
       org.sunbird.common.request.Request request,
       Timeout timeout, ...)
   ```

3. **ActorStartModule.java**:
   ```java
   public class ActorStartModule extends AbstractModule implements AkkaGuiceSupport {
       // Binds actors for DI with router config
   }
   ```

4. **application.conf** - Extensive Akka configuration:
   - Actor system configuration
   - 5 custom dispatchers
   - 17 actor deployment configurations
   - Router settings

### 1.3 Build System

**Build Tool**: Maven with Play2 Maven Plugin

- Not using SBT directly
- Using `play2-maven-plugin:1.0.0-rc5` 
- Multi-module Maven project structure
- Maven profiles for different build scenarios

**Note**: The title mentions "using SBT" but the project actually uses **Maven**, not SBT. The Play2 Maven Plugin provides similar functionality to SBT's Play plugin.

---

## 2. Migration Paths

### 2.1 Play Framework Upgrade Paths

#### Option 1: Play 2.8.x (Conservative)
- **Status**: Maintenance mode, limited support
- **Akka Version**: 2.6.x
- **Pros**: Smaller migration effort, incremental path
- **Cons**: Still EOL soon, Akka still under BSL for newer versions
- **Recommendation**: ⚠️ **Not Recommended** (temporary solution only)

#### Option 2: Play 2.9.x (Intermediate)
- **Status**: End of active development (October 2024)
- **Akka Version**: 2.6.x
- **Pros**: More stable than 2.8.x, better maintained
- **Cons**: Already approaching EOL, Akka license issues remain
- **Recommendation**: ⚠️ **Conditional** (if rapid migration needed)

#### Option 3: Play 3.0.x with Pekko (Recommended)
- **Status**: Current LTS, Active development
- **Actor Framework**: Apache Pekko (default in Play 3.0+)
- **Pros**: 
  - Modern, actively supported
  - Apache 2.0 license throughout
  - Built-in Pekko support
  - Long-term viability
- **Cons**: 
  - Significant breaking changes
  - Requires comprehensive testing
  - Learning curve for Pekko
- **Recommendation**: ✅ **Highly Recommended** for long-term sustainability

### 2.2 Akka to Pekko Migration Paths

#### Understanding Apache Pekko

**Apache Pekko** is a fork of Akka 2.6.x maintained under the Apache Software Foundation:
- **License**: Apache License 2.0 (Open Source)
- **Origin**: Fork of Akka 2.6.21 (last open-source version)
- **Current Version**: 1.0.x series
- **Compatibility**: Drop-in replacement with package name changes
- **Support**: Active Apache project with community backing

#### Direct Migration: Akka 2.5.x → Pekko 1.0.x

**Steps**:
1. Update dependencies from `com.typesafe.akka` to `org.apache.pekko`
2. Change import statements from `akka.*` to `org.apache.pekko.*`
3. Update configuration from `akka.*` to `pekko.*`
4. Test thoroughly

**Challenges**:
- Akka 2.5.x has significant API differences from 2.6.x (Pekko base)
- May need intermediate upgrade to Akka 2.6.x for smoother transition
- Some deprecated APIs in 2.5.x removed in Pekko

#### Staged Migration: Akka 2.5.x → Akka 2.6.x → Pekko 1.0.x

**Recommended Approach**:

1. **Stage 1**: Upgrade Akka 2.5.22 → 2.6.21 (last open-source)
   - Address deprecations
   - Update to typed actors if beneficial
   - Test compatibility

2. **Stage 2**: Migrate Akka 2.6.21 → Pekko 1.0.x
   - Package rename migration
   - Configuration updates
   - Dependency updates

**Benefits**:
- Reduces risk through incremental changes
- Easier to isolate issues
- Can validate at each stage

---

## 3. Detailed Compatibility Analysis

### 3.1 Play Framework Compatibility Matrix

| Play Version | Akka/Pekko Version | Scala Version | Java Version | Status | License |
|--------------|-------------------|---------------|--------------|---------|---------|
| 2.7.x | Akka 2.5.x | 2.11, 2.12 | 8, 11 | EOL | Apache 2.0 |
| 2.8.x | Akka 2.6.x | 2.12, 2.13 | 8, 11 | Maintenance | Apache 2.0 |
| 2.9.x | Akka 2.6.x | 2.13, 3.x | 11, 17, 21 | EOL | Apache 2.0 |
| 3.0.x | Pekko 1.0.x | 2.13, 3.x | 11, 17, 21 | Active LTS | Apache 2.0 |

### 3.2 Breaking Changes in Play 3.0

#### Major Changes:
1. **Pekko by Default**: 
   - Play 3.0 uses Apache Pekko instead of Akka
   - All `akka.*` references in Play → `pekko.*`

2. **Package Renames**:
   - `play.api.libs.concurrent.ActorSystemProvider` → Updated for Pekko
   - Play's internal actor integration uses Pekko

3. **Scala Version**:
   - Minimum Scala 2.13 (current project uses 2.12)
   - Scala 3 support available

4. **Java Version**:
   - Minimum Java 11 (current: Java 11 ✓)
   - Java 17 recommended
   - Java 21 supported

5. **Configuration Changes**:
   - `akka.*` config keys → `pekko.*`
   - Some configuration structure changes

6. **Dependency Changes**:
   - `play-akka-http-server` → `play-pekko-http-server`
   - Guice DI remains but updated

7. **API Changes**:
   - Some deprecated APIs removed
   - Cleaner async APIs with CompletionStage

### 3.3 Akka to Pekko Migration Details

#### Package Name Changes
```java
// Before (Akka)
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.UntypedAbstractActor;
import akka.pattern.Patterns;
import akka.util.Timeout;

// After (Pekko)
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.UntypedAbstractActor;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
```

#### Configuration Changes
```hocon
# Before (Akka)
akka {
  loggers = ["akka.event.slf4j.Slf4jLogger"]
  actor {
    provider = "akka.actor.LocalActorRefProvider"
  }
}

# After (Pekko)
pekko {
  loggers = ["org.apache.pekko.event.slf4j.Slf4jLogger"]
  actor {
    provider = "org.apache.pekko.actor.LocalActorRefProvider"
  }
}
```

#### Dependency Changes
```xml
<!-- Before (Akka) -->
<dependency>
    <groupId>com.typesafe.akka</groupId>
    <artifactId>akka-actor_2.12</artifactId>
    <version>2.5.22</version>
</dependency>

<!-- After (Pekko) -->
<dependency>
    <groupId>org.apache.pekko</groupId>
    <artifactId>pekko-actor_2.13</artifactId>
    <version>1.0.2</version>
</dependency>
```

### 3.4 API Compatibility

#### Compatible APIs (No Changes Needed)
- ✅ Actor message sending (`tell`, `ask`)
- ✅ Actor lifecycle hooks
- ✅ Supervision strategies
- ✅ Router configurations
- ✅ Dispatcher configurations
- ✅ Timeout handling
- ✅ Future/CompletionStage patterns

#### APIs Requiring Updates
- ⚠️ `UntypedAbstractActor` → Still supported but `AbstractActor` recommended
- ⚠️ Some serialization configurations may need updates
- ⚠️ Remote actor configurations (if used) need validation
- ⚠️ Test utilities namespace changes

#### Deprecated/Removed in Pekko
- ❌ Some Akka 2.5.x deprecated APIs completely removed
- ❌ Legacy actor patterns may need refactoring
- ❌ Some Akka Streams APIs if used (not evident in this codebase)

---

## 4. Code Impact Assessment

### 4.1 Files Requiring Changes

#### High Impact (Core Changes Required)
1. **All POM files** (8+ files):
   - Dependency version updates
   - Artifact ID changes (akka → pekko)
   - Group ID changes
   - Scala version updates (2.12 → 2.13)

2. **Configuration Files** (3 files):
   - `service/conf/application.conf` - Main configuration
   - Other application.conf files in modules
   - All `akka.*` → `pekko.*` namespace changes

3. **Actor Base Classes** (2 critical files):
   - `actor-core/src/main/java/org/sunbird/actor/core/BaseActor.java`
   - `course-actors-common/src/main/java/org/sunbird/actor/base/BaseActor.java`
   - Import statement updates
   - Potential API modernization

4. **Module Configuration** (3 files):
   - `service/app/modules/ActorStartModule.java`
   - `service/app/modules/StartModule.java`
   - Guice bindings for Pekko actors

5. **Controllers** (15+ files):
   - All controllers using actor communication
   - `BaseController.java` - Actor response handler
   - Import updates
   - CompletionStage handling

#### Medium Impact (Import Changes Primarily)
6. **Actor Implementations** (26 files):
   - All actor classes extending BaseActor
   - Import statement updates
   - Minimal logic changes expected

7. **Service/Utility Classes** (10 files):
   - `InterServiceCommunicationImpl.java`
   - `BaseMWService.java`
   - Actor system initialization
   - Pattern matching utilities

8. **Filters** (3 files):
   - `CustomGzipFilter.java`
   - `ResponseFilter.scala`
   - Akka Stream integration points

#### Low Impact (Test Updates)
9. **Test Files** (20+ files):
   - Actor test classes
   - TestKit usage
   - Test configuration
   - Import updates

### 4.2 Estimated Line Changes

| Category | Files | Estimated Lines | Complexity |
|----------|-------|-----------------|------------|
| Import Statements | 68 | ~200-300 | Low |
| Configuration | 3 | ~100-150 | Medium |
| POM Dependencies | 8 | ~50-80 | Low |
| Actor Initialization | 5 | ~50-100 | Medium |
| Controller Updates | 15 | ~100-200 | Medium |
| Test Updates | 20 | ~150-250 | Low |
| Documentation | Various | ~50-100 | Low |
| **Total Estimate** | **~120+** | **~700-1180** | **Medium** |

### 4.3 Critical Code Sections

#### 1. BaseActor.java (actor-core)
**Current**:
```java
import akka.actor.UntypedAbstractActor;
import akka.util.Timeout;

public abstract class BaseActor extends UntypedAbstractActor {
    public static final int AKKA_WAIT_TIME = 30;
    protected static Timeout timeout = new Timeout(AKKA_WAIT_TIME, TimeUnit.SECONDS);
    
    @Override
    public void onReceive(Object message) throws Throwable {
        // Actor message handling
    }
}
```

**Impact**: Core class affecting all actors - needs careful migration and testing.

#### 2. ActorStartModule.java
**Current**:
```java
import akka.routing.FromConfig;
import play.libs.akka.AkkaGuiceSupport;

public class ActorStartModule extends AbstractModule implements AkkaGuiceSupport {
    @Override
    protected void configure() {
        final RouterConfig config = new FromConfig();
        for (ACTOR_NAMES actor : ACTOR_NAMES.values()) {
            bindActor(actor.getActorClass(), actor.getActorName(), 
                (props) -> props.withRouter(config));
        }
    }
}
```

**Impact**: DI configuration for all actors - critical for application startup.

#### 3. application.conf
**Current**: 210 lines of Akka configuration including:
- Actor system settings
- 5 custom dispatchers
- 17 actor deployments with routing
- Remote actor configuration (potential)

**Impact**: All `akka.*` keys need migration to `pekko.*`.

### 4.4 Testing Impact

#### Test Infrastructure
- **akka-testkit** → **pekko-testkit**
- Test utility imports need updates
- Actor test patterns remain similar

#### Test Files Affected
- `HealthActorTest.java` (Ignored but needs update)
- Multiple actor test files in course-actors-common
- Controller test files using actor mocking

#### Test Coverage Risk
- Comprehensive regression testing required
- Actor communication patterns
- Timeout and error handling
- Concurrent operations

---

## 5. Benefits

### 5.1 License Benefits
✅ **Open Source Freedom**:
- Apache License 2.0 for both Play 3.0 and Pekko
- No commercial license restrictions
- Freedom to use, modify, and distribute
- No future license uncertainty

✅ **Cost Savings**:
- No license fees for Akka (BSL 1.1 requires license for production use with revenue > $25M)
- Reduced legal compliance overhead
- No vendor lock-in concerns

### 5.2 Security Benefits
✅ **Active Security Support**:
- Play 3.0 receives regular security updates
- Pekko maintained by Apache Foundation
- Community-driven vulnerability management
- Current versions vs. 5-year-old dependencies

✅ **Dependency Updates**:
- Modern Jackson version (2.13.5 → 2.16+)
- Updated Netty for HTTP handling
- Latest security patches

### 5.3 Technical Benefits
✅ **Performance Improvements**:
- Modern JVM optimizations in Play 3.0
- Better async handling with virtual threads (Java 21)
- Improved HTTP/2 support
- Optimized actor dispatch in Pekko

✅ **Modern Features**:
- Java 17/21 support with modern language features
- Better async/await patterns
- Improved developer experience
- Enhanced debugging capabilities

✅ **Stability**:
- Mature, production-tested framework (Play 3.0)
- Based on battle-tested Akka 2.6.x code (Pekko)
- Long-term support commitment
- Large community and ecosystem

✅ **Future-Proofing**:
- Active development and roadmap
- Growing Pekko ecosystem
- Industry trend toward Apache projects
- Sustainable long-term technology choice

### 5.4 Ecosystem Benefits
✅ **Community Support**:
- Active Play Framework community
- Growing Apache Pekko adoption
- Better documentation and resources
- Community-driven improvements

✅ **Integration**:
- Better integration with modern tools
- Cloud-native improvements
- Container-friendly defaults
- Kubernetes operator support

---

## 6. Drawbacks and Challenges

### 6.1 Migration Effort
❌ **Significant Development Time**:
- Estimated 3-6 weeks for full migration
- Multiple developers may be needed
- Careful coordination required across modules

❌ **Code Changes**:
- ~700-1180 lines of code changes estimated
- 120+ files affected
- All modules need updates simultaneously

❌ **Testing Overhead**:
- Comprehensive regression testing required
- Actor behavior validation
- Integration testing across all modules
- Performance testing under load

### 6.2 Learning Curve
❌ **New Framework Knowledge**:
- Team needs to learn Play 3.0 differences
- Pekko namespace and minor API changes
- Updated best practices
- New debugging approaches

❌ **Documentation Gap**:
- Migration guides exist but are generic
- Project-specific issues require custom solutions
- Less Stack Overflow content for Pekko vs. Akka
- Some Akka documentation not yet available for Pekko

### 6.3 Technical Challenges
❌ **Breaking Changes**:
- Scala version upgrade (2.12 → 2.13) required for Play 3.0
- Some deprecated APIs removed
- Configuration changes required throughout
- Potential for subtle behavioral differences

❌ **Dependency Conflicts**:
- Transitive dependency management complexity
- Some libraries may still depend on Akka
- Version alignment across all modules
- Potential for classpath conflicts during migration

❌ **Backward Compatibility**:
- Cannot run mixed Akka/Pekko environments
- All-or-nothing migration required
- No gradual rollout option
- Rollback complexity if issues found

### 6.4 Risk Factors
❌ **Production Stability Risk**:
- Potential for unforeseen issues in production
- Actor behavior changes could cause subtle bugs
- Performance characteristics may differ
- Increased monitoring needed post-migration

❌ **Build System Complexity**:
- play2-maven-plugin may have limited Play 3.0 support
- May need to evaluate alternative build approaches
- CI/CD pipeline updates required
- Build time may increase initially

❌ **Third-Party Dependencies**:
- Some dependencies may not be compatible with Play 3.0/Scala 2.13
- Custom dependency management may be needed
- Library updates may introduce other breaking changes
- Version conflict resolution

### 6.5 Business Impact
❌ **Downtime Risk**:
- Deployment requires careful planning
- Potential for extended downtime if issues occur
- Rollback strategy needed
- User impact during migration

❌ **Resource Allocation**:
- Development team time diverted from features
- QA resources for extended testing period
- DevOps time for infrastructure updates
- Management overhead

❌ **Timeline Pressure**:
- Security updates needed urgently (EOL software)
- Business pressure for new features
- Limited testing time in production-like environments
- Coordination with stakeholder schedules

---

## 7. Risk Assessment

### 7.1 Risk Matrix

| Risk Category | Likelihood | Impact | Severity | Mitigation Priority |
|---------------|------------|--------|----------|-------------------|
| Actor behavior changes | Medium | High | **Critical** | P0 |
| Build system issues | Medium | Medium | **High** | P1 |
| Dependency conflicts | High | Medium | **High** | P1 |
| Performance degradation | Low | High | **High** | P1 |
| Production bugs | Medium | High | **Critical** | P0 |
| Timeline overrun | High | Medium | **Medium** | P2 |
| Third-party incompatibility | Medium | Medium | **Medium** | P2 |
| Knowledge gaps | Medium | Low | **Low** | P3 |

### 7.2 Critical Risks

#### Risk 1: Actor Communication Failures
- **Description**: Subtle changes in actor behavior causing message loss or deadlocks
- **Probability**: Medium (15-25%)
- **Impact**: Service degradation or failure
- **Mitigation**:
  - Extensive actor testing with test scenarios
  - Gradual rollout with canary deployments
  - Enhanced monitoring for actor mailbox metrics
  - Comprehensive integration tests

#### Risk 2: play2-maven-plugin Compatibility
- **Description**: Maven plugin may not fully support Play 3.0
- **Probability**: Medium-High (30-40%)
- **Impact**: Cannot build or deploy application
- **Mitigation**:
  - Evaluate plugin compatibility early
  - Consider migration to SBT if needed
  - Alternative: Use Play's standalone packaging
  - Test build process in isolated environment

#### Risk 3: Regression Bugs in Production
- **Description**: Edge cases not caught in testing causing production issues
- **Probability**: Medium (20-30%)
- **Impact**: Service outages, data inconsistencies
- **Mitigation**:
  - Feature flags for gradual feature enablement
  - Robust rollback strategy
  - Extended QA cycle with production-like data
  - Staged rollout (dev → staging → production)

### 7.3 Medium Risks

#### Risk 4: Dependency Conflicts
- **Description**: Transitive dependencies causing classpath conflicts
- **Probability**: High (40-50%)
- **Impact**: Build failures or runtime errors
- **Mitigation**:
  - Dependency analysis tools (maven dependency:tree)
  - Explicit dependency management
  - Exclusions for conflicting transitive deps
  - Version alignment strategy

#### Risk 5: Configuration Errors
- **Description**: Missed akka→pekko config transformations
- **Probability**: Medium (25-35%)
- **Impact**: Runtime failures, degraded performance
- **Mitigation**:
  - Configuration validation scripts
  - Automated search/replace with verification
  - Configuration testing in isolation
  - Peer review of all config changes

#### Risk 6: Test Coverage Gaps
- **Description**: Insufficient test coverage for migrated code
- **Probability**: Medium (20-30%)
- **Impact**: Undetected bugs in production
- **Mitigation**:
  - Measure code coverage before/after
  - Add tests for critical actor paths
  - Manual testing of key workflows
  - Load testing to validate performance

### 7.4 Low Risks

#### Risk 7: Learning Curve Impact
- **Description**: Team unfamiliarity with Pekko/Play 3.0
- **Probability**: High (certain)
- **Impact**: Slower development, potential mistakes
- **Mitigation**:
  - Training sessions on Pekko/Play 3.0
  - Documentation and runbooks
  - Pair programming for complex changes
  - External consulting if needed

### 7.5 Risk Response Plan

#### High Priority (P0) Risks
1. **Comprehensive Testing Strategy**:
   - Unit tests: All actor classes
   - Integration tests: Actor communication patterns
   - End-to-end tests: Critical user workflows
   - Performance tests: Load and stress testing
   - Chaos testing: Failure scenarios

2. **Monitoring Enhancement**:
   - Actor system metrics (mailbox size, processing time)
   - Application performance monitoring (APM)
   - Error tracking and alerting
   - User experience monitoring

3. **Rollback Plan**:
   - Maintain previous version deployment artifacts
   - Database migration rollback scripts
   - Quick rollback procedure documented
   - Rollback testing in staging

#### Medium Priority (P1) Risks
4. **Build Process Validation**:
   - Early prototype build with Play 3.0
   - CI/CD pipeline testing
   - Alternative build tool evaluation
   - Build artifact validation

5. **Dependency Management**:
   - Dependency audit before migration
   - Version conflict resolution strategy
   - Testing with all dependency combinations
   - Vendor support verification

#### Lower Priority (P2-P3) Risks
6. **Knowledge Transfer**:
   - Migration documentation
   - Code review process
   - Knowledge sharing sessions
   - External resources and training

---

## 8. Migration Strategy

### 8.1 Recommended Approach

#### **Phased Migration Strategy** (Recommended)

This approach minimizes risk through incremental changes and validation at each step.

**Phase 1: Preparation & Assessment** (1-2 weeks)
1. ✅ Complete this compatibility analysis (Done)
2. 🔄 Set up isolated development environment
3. 🔄 Create comprehensive test suite baseline
4. 🔄 Document all current actor behaviors
5. 🔄 Measure current performance baselines
6. 🔄 Audit all dependencies for compatibility
7. 🔄 Set up monitoring and metrics collection

**Phase 2: Dependency Updates** (1-2 weeks)
1. 🔄 Update Scala version 2.12 → 2.13
2. 🔄 Update Java libraries to compatible versions
3. 🔄 Update Jackson, Guice, and other key dependencies
4. 🔄 Resolve any dependency conflicts
5. 🔄 Test build process with updated dependencies
6. 🔄 Validate application functionality

**Phase 3: Akka to Pekko Migration** (2-3 weeks)
1. 🔄 Update all Maven POMs:
   - Replace Akka dependencies with Pekko
   - Update version numbers
   - Update artifact IDs and group IDs

2. 🔄 Update Java source files:
   - Search and replace: `import akka.` → `import org.apache.pekko.`
   - Validate each file compiles
   - Update deprecated API usage

3. 🔄 Update configuration files:
   - Transform akka.* → pekko.* in all .conf files
   - Update actor system names
   - Validate configuration syntax

4. 🔄 Update Scala files (if any):
   - Same package transformation
   - Update Scala version syntax if needed

5. 🔄 Validate and test:
   - Run full test suite
   - Fix any test failures
   - Validate actor behaviors

**Phase 4: Play Framework Upgrade** (2-3 weeks)
1. 🔄 Update Play dependencies to 3.0.x:
   - Update all play-* dependencies
   - Update play2-maven-plugin (or migrate to SBT)
   - Update Scala library version

2. 🔄 Refactor for Play 3.0 APIs:
   - Update deprecated controller methods
   - Update filter implementations
   - Update module configurations
   - Update routing if needed

3. 🔄 Update Play configuration:
   - Migrate application.conf for Play 3.0
   - Update any Play-specific settings
   - Update HTTP server configuration

4. 🔄 Build and test:
   - Resolve build issues
   - Run full test suite
   - Integration testing

**Phase 5: Testing & Validation** (2-3 weeks)
1. 🔄 Unit testing:
   - All actor classes
   - All service classes
   - All controllers
   - Edge cases and error handling

2. 🔄 Integration testing:
   - Actor communication patterns
   - Database interactions
   - External service integrations
   - End-to-end workflows

3. 🔄 Performance testing:
   - Load testing
   - Stress testing
   - Comparison with baseline metrics
   - Resource utilization analysis

4. 🔄 User acceptance testing:
   - Critical business workflows
   - Edge cases from production
   - Real-world scenarios

**Phase 6: Deployment** (1 week)
1. 🔄 Staging deployment:
   - Deploy to staging environment
   - Extended validation period
   - Monitor for issues

2. 🔄 Production preparation:
   - Backup current production
   - Prepare rollback plan
   - Brief operations team
   - Set up enhanced monitoring

3. 🔄 Production deployment:
   - Deploy during low-traffic window
   - Gradual traffic shift (if possible)
   - Monitor closely
   - Be ready to rollback

4. 🔄 Post-deployment:
   - Monitor for 48-72 hours
   - Collect performance metrics
   - Validate all functionality
   - Document any issues

**Total Estimated Timeline: 9-14 weeks**

### 8.2 Alternative: Big Bang Migration

**Not Recommended** but included for completeness.

#### Approach
- All changes in one large effort
- Single deployment event
- All-or-nothing migration

#### Pros
- Faster calendar time (6-8 weeks)
- No intermediate states to maintain
- Simpler coordination

#### Cons
- ❌ Very high risk
- ❌ Difficult to isolate issues
- ❌ Large rollback scope
- ❌ Extended downtime likely
- ❌ Testing challenges

**Recommendation**: Only consider if absolute time pressure and strong confidence in codebase understanding.

### 8.3 Build System Considerations

#### Current: Maven with play2-maven-plugin

**Compatibility Concern**: The `play2-maven-plugin` may have limited or no support for Play 3.0.

**Options**:

**Option A: Continue with Maven** (If plugin supports Play 3.0)
- Pros: No build system change, familiar to team
- Cons: Plugin may be outdated, limited community support
- Action: Research plugin compatibility early in Phase 1

**Option B: Migrate to SBT** (Play's native build tool)
- Pros: First-class Play support, better ecosystem
- Cons: Build system migration overhead, team learning curve
- Action: Evaluate if Maven plugin doesn't support Play 3.0

**Option C: Gradle** (Alternative)
- Pros: Modern build tool, good Play support via plugins
- Cons: Another migration, less common for Play
- Action: Consider if both Maven and SBT are problematic

**Recommendation**: 
1. First attempt Maven path with Play 3.0
2. If blocked, migrate to SBT (2-3 weeks additional effort)
3. Gradle as last resort

### 8.4 Testing Strategy

#### Test Pyramid

1. **Unit Tests** (70% coverage target):
   - All actor classes
   - Service layer
   - Utility classes
   - Message handling logic

2. **Integration Tests** (20% coverage):
   - Actor communication
   - Database interactions
   - External APIs
   - Module integration

3. **End-to-End Tests** (10% coverage):
   - Critical user workflows
   - Business processes
   - Full stack testing

#### Test Types

| Test Type | Coverage | Tools | Priority |
|-----------|----------|-------|----------|
| Unit Tests | All actors, services | JUnit, Mockito | P0 |
| Integration | Actor communication | TestKit | P0 |
| Component | Modules | Play Test helpers | P1 |
| API Tests | REST endpoints | REST Assured | P1 |
| Performance | Load/stress | JMeter, Gatling | P1 |
| Regression | Known issues | Manual + Automated | P0 |
| Security | Auth, inputs | OWASP ZAP | P2 |
| Compatibility | Browser/clients | Selenium (if UI) | P2 |

#### Actor-Specific Testing

Critical actor test scenarios:
- ✅ Message handling for all message types
- ✅ Error handling and recovery
- ✅ Timeout scenarios
- ✅ Concurrent message processing
- ✅ Actor lifecycle (start, stop, restart)
- ✅ Supervisor strategy validation
- ✅ Router behavior with pool sizes
- ✅ Dispatcher allocation and threading

### 8.5 Rollback Strategy

#### Rollback Triggers
- Critical functionality broken
- Performance degradation > 30%
- Data inconsistencies detected
- Unresolved production errors

#### Rollback Procedure
1. Stop accepting new traffic
2. Switch load balancer to previous version
3. Revert database migrations (if any)
4. Restore previous configuration
5. Validate previous version functioning
6. Post-mortem to understand failure

#### Rollback Testing
- Test rollback procedure in staging
- Measure rollback time (target: < 15 minutes)
- Document rollback steps
- Train operations team

---

## 9. Effort Estimation

### 9.1 Development Effort

| Phase | Tasks | Developer Days | Calendar Time |
|-------|-------|----------------|---------------|
| **Phase 1: Preparation** | Analysis, planning, environment setup | 5-8 days | 1-2 weeks |
| **Phase 2: Dependencies** | Update deps, resolve conflicts | 5-8 days | 1-2 weeks |
| **Phase 3: Pekko Migration** | Code changes, imports, config | 10-15 days | 2-3 weeks |
| **Phase 4: Play Upgrade** | Framework update, API changes | 10-15 days | 2-3 weeks |
| **Phase 5: Testing** | All test types, bug fixes | 10-15 days | 2-3 weeks |
| **Phase 6: Deployment** | Staging, production, monitoring | 3-5 days | 1 week |
| **Contingency** | Unexpected issues, learning | 5-10 days | 1-2 weeks |
| **TOTAL** | | **48-76 days** | **10-16 weeks** |

**Note**: Developer days assume 1 full-time developer. Multiple developers can parallelize some work but coordination overhead applies.

### 9.2 Resource Requirements

#### Development Team
- **Lead Developer**: 1 person, full-time
  - Scala/Java expertise
  - Akka/Pekko knowledge
  - Play Framework experience
  - Architecture decisions

- **Backend Developers**: 2-3 people, 50-75% time
  - Code changes
  - Testing
  - Bug fixes
  - Code reviews

#### QA Team
- **QA Engineers**: 2 people, 50% time
  - Test plan development
  - Test execution
  - Regression testing
  - Bug reporting

- **Performance Tester**: 1 person, 25% time
  - Load testing
  - Performance comparison
  - Bottleneck identification

#### DevOps Team
- **DevOps Engineer**: 1 person, 25% time
  - Build pipeline updates
  - Deployment procedures
  - Monitoring setup
  - Infrastructure validation

#### Optional: External Support
- **Consultant/Expert**: As needed
  - Play/Pekko migration expertise
  - Complex issue resolution
  - Code review and guidance
  - Training

### 9.3 Cost Estimation

**Assumptions**:
- Average developer rate: $100-150/hour
- QA rate: $75-100/hour
- DevOps rate: $100-125/hour

| Resource | Hours | Cost Range |
|----------|-------|------------|
| Lead Developer | 480-640 | $48,000-96,000 |
| Backend Developers (3) | 720-1080 | $72,000-162,000 |
| QA Engineers (2) | 320-480 | $24,000-48,000 |
| Performance Tester | 80-120 | $6,000-12,000 |
| DevOps Engineer | 80-120 | $8,000-15,000 |
| External Consultant | 40-80 | $6,000-12,000 |
| **TOTAL** | **1,720-2,520** | **$164,000-345,000** |

**Additional Costs**:
- Testing infrastructure: $2,000-5,000
- Training/resources: $1,000-3,000
- Contingency (20%): $33,000-70,000

**Total Estimated Cost**: **$200,000-423,000**

**Note**: Costs can vary significantly based on:
- Team location and rates
- Existing team expertise
- Project complexity uncovered during work
- External support needs

### 9.4 Timeline with Parallel Work

#### Optimized Timeline (Multiple developers)

```
Weeks 1-2:  [Phase 1: Preparation & Setup]
            [Build environment, analysis, planning]

Weeks 3-4:  [Phase 2: Dependencies]
            [Update deps, test compatibility]

Weeks 5-7:  [Phase 3: Pekko Migration]
            Developer 1: Actor core + utilities
            Developer 2: Controllers + services
            Developer 3: Configuration + build

Weeks 8-10: [Phase 4: Play Framework Upgrade]
            All devs: Framework changes
            QA: Test plan development

Weeks 11-13: [Phase 5: Testing]
             Devs: Bug fixes
             QA: Comprehensive testing
             DevOps: Deployment prep

Week 14:    [Phase 6: Deployment]
            Staging deployment + validation
            Production deployment
            Post-deployment monitoring

Week 15-16: [Stabilization]
            Bug fixes
            Performance tuning
            Documentation
```

**Optimized Timeline**: 14-16 weeks with 3-4 developers working in parallel.

---

## 10. Recommendations

### 10.1 Immediate Actions (Do Now)

1. **✅ Accept this Report**:
   - Review findings with stakeholders
   - Get buy-in for migration effort
   - Allocate budget and resources

2. **🔧 Set Up Isolated Development Environment** (Week 1):
   - Clone repository to isolated branch
   - Set up development environment
   - Establish baseline metrics
   - Document current behavior

3. **📊 Measure Current System** (Week 1-2):
   - Performance benchmarks
   - Test coverage analysis
   - Actor communication patterns
   - Error rates and logs

4. **🔍 Validate play2-maven-plugin Compatibility** (Week 1):
   - Research plugin support for Play 3.0
   - Test basic Play 3.0 build in isolation
   - Decide on build tool strategy early
   - Avoid late-stage surprises

5. **📚 Team Training** (Week 1-2):
   - Pekko documentation review
   - Play 3.0 migration guides
   - Set up internal knowledge base
   - Schedule training sessions

### 10.2 Migration Decision

#### ✅ **RECOMMENDED: Proceed with Full Migration to Play 3.0 + Pekko**

**Rationale**:
1. **Security Imperative**: Current versions (Play 2.7.2, Akka 2.5.22) are 5+ years old and receive no security updates
2. **License Compliance**: Avoid future Akka BSL 1.1 license complications
3. **Long-term Sustainability**: Play 3.0 + Pekko is the industry-standard open-source path
4. **Community Support**: Active development and growing ecosystem

**Conditions for Success**:
- Allocate sufficient resources (3-4 developers)
- Allow adequate timeline (14-16 weeks)
- Follow phased migration approach
- Comprehensive testing at each phase
- Strong rollback strategy

### 10.3 Migration Approach

#### ✅ **RECOMMENDED: Phased Migration**

**Approach**:
1. Phase 1: Preparation (1-2 weeks)
2. Phase 2: Dependencies (1-2 weeks)  
3. Phase 3: Pekko Migration (2-3 weeks)
4. Phase 4: Play Upgrade (2-3 weeks)
5. Phase 5: Testing (2-3 weeks)
6. Phase 6: Deployment (1 week)

**Key Principles**:
- Validate at each phase
- Test continuously
- Don't skip to later phases
- Address issues immediately
- Document everything

#### ❌ **NOT RECOMMENDED: Partial Upgrade to Play 2.8/2.9**

**Reasons**:
- Still uses Akka (license issues remain)
- Versions approaching EOL
- Double migration effort
- No long-term benefit

### 10.4 Build Tool Strategy

#### **Recommendation Sequence**:

1. **First**: Attempt Maven with play2-maven-plugin
   - Validate Play 3.0 compatibility
   - Test in isolated environment
   - Decision point: Week 2

2. **If Maven fails**: Migrate to SBT
   - Play's native and best-supported build tool
   - Add 2-3 weeks to timeline
   - Provides better long-term support

3. **Last resort**: Gradle
   - Only if both Maven and SBT problematic
   - Less common for Play projects

### 10.5 Risk Mitigation Priorities

#### Priority 0 (Critical)
1. ✅ **Comprehensive Test Suite**: Develop before migration starts
2. ✅ **Actor Behavior Documentation**: Document all current behaviors
3. ✅ **Rollback Plan**: Test rollback procedure in staging
4. ✅ **Monitoring Enhancement**: Set up detailed actor system monitoring

#### Priority 1 (High)
5. ✅ **Build Process Validation**: Early testing of build toolchain
6. ✅ **Dependency Audit**: Identify conflicts before coding starts
7. ✅ **Performance Baselines**: Measure current performance for comparison
8. ✅ **Staged Rollout**: Deploy to staging extensively before production

#### Priority 2 (Medium)
9. ✅ **Knowledge Transfer**: Train team on Pekko/Play 3.0
10. ✅ **External Expertise**: Have consultant on standby
11. ✅ **Extended QA**: Allow extra time for testing
12. ✅ **Documentation**: Document all changes and learnings

### 10.6 Success Criteria

The migration will be considered successful when:

1. **Functional Criteria**:
   - ✅ All existing features work identically
   - ✅ No regression bugs in production
   - ✅ All tests pass
   - ✅ Actors communicate correctly

2. **Performance Criteria**:
   - ✅ Response times within 10% of baseline
   - ✅ Throughput equal or better
   - ✅ Resource utilization comparable
   - ✅ No memory leaks

3. **Quality Criteria**:
   - ✅ Code coverage maintained or improved
   - ✅ No critical or high-severity bugs
   - ✅ Successful load testing
   - ✅ Security scan passes

4. **Business Criteria**:
   - ✅ Zero unplanned downtime
   - ✅ User experience unchanged
   - ✅ All SLAs met
   - ✅ Stakeholder approval

### 10.7 Post-Migration Actions

After successful migration:

1. **Monitoring** (First month):
   - Enhanced monitoring of actor systems
   - Performance tracking
   - Error rate monitoring
   - User feedback collection

2. **Optimization** (Months 1-3):
   - Performance tuning based on production data
   - Actor pool size optimization
   - Dispatcher configuration tuning
   - Caching improvements

3. **Documentation** (Month 1):
   - Update all technical documentation
   - Create runbooks for common issues
   - Document lessons learned
   - Share knowledge with team

4. **Team Enablement**:
   - Training on new architecture
   - Best practices for Pekko/Play 3.0
   - Code review standards
   - Contribution guidelines

5. **Continuous Improvement**:
   - Stay current with Play updates
   - Monitor Pekko ecosystem
   - Adopt new features incrementally
   - Regular dependency updates

### 10.8 Alternative if Migration Not Feasible

If full migration cannot be undertaken:

#### **Minimum Viable Action Plan**:

1. **Security Updates Only** (Not ideal):
   - Apply critical security patches where available
   - Implement additional security layers (WAF, API gateway)
   - Increase monitoring and threat detection
   - **Risk**: Limited patches available for EOL software

2. **Containerization and Isolation**:
   - Deploy in isolated network segments
   - Strict firewall rules
   - Regular security scanning
   - **Risk**: Doesn't fix underlying issues

3. **Plan for Future Migration**:
   - Document technical debt
   - Allocate budget for future migration
   - Set timeline (within 12 months)
   - **Risk**: Increasing technical debt

**Strong Recommendation**: These alternatives are stop-gaps only. Full migration should be prioritized.

---

## 11. Conclusion

### Summary of Findings

The lms-service repository currently uses **Play Framework 2.7.2** (EOL) and **Akka 2.5.22** (EOL), both released in 2019. This presents significant risks:

1. **Security Risk**: No security updates for 5+ year old software
2. **License Risk**: Future Akka versions use BSL 1.1 (commercial restrictions)  
3. **Sustainability Risk**: Outdated technology limiting future development
4. **Compliance Risk**: Potential license violations with Akka BSL

### Final Recommendation

**✅ PROCEED with migration to Play Framework 3.0 + Apache Pekko**

This migration is **essential** for:
- Security and compliance
- Long-term sustainability  
- Open-source freedom
- Community support

**Timeline**: 14-16 weeks with dedicated team  
**Cost**: $200,000-423,000 (depending on resources)  
**Risk**: Medium (manageable with proper planning)  
**Benefit**: High (security, license, sustainability)

### Next Steps

1. **Immediate** (This week):
   - Review and approve this report
   - Allocate resources and budget
   - Set up isolated development environment

2. **Week 1-2**:
   - Validate build tool compatibility
   - Establish performance baselines
   - Begin team training
   - Set up comprehensive test suite

3. **Week 3+**:
   - Begin phased migration following strategy in Section 8
   - Regular progress reviews
   - Continuous testing and validation

### Critical Success Factors

The migration will succeed if:
- ✅ Adequate resources allocated (3-4 developers)
- ✅ Sufficient time allowed (14-16 weeks)
- ✅ Phased approach followed rigorously
- ✅ Testing not compromised
- ✅ Strong technical leadership
- ✅ Stakeholder support maintained

### Risk Warning

**Not migrating** presents worse risks than migrating:
- Continued use of EOL software
- Increasing security vulnerabilities  
- Potential license compliance issues
- Growing technical debt
- Eventual forced emergency migration

---

## Appendices

### Appendix A: Detailed Dependency Matrix

#### Current Akka Dependencies

| Module | Dependency | Version | License |
|--------|------------|---------|---------|
| service | akka-testkit_2.12 | 2.5.22 | Apache 2.0 |
| actor-core | akka-actor_2.12 | 2.5.22 | Apache 2.0 |
| actor-core | akka-slf4j_2.12 | 2.5.22 | Apache 2.0 |
| course-actors-common | akka-actor_2.12 | 2.5.22 | Apache 2.0 |
| course-actors-common | akka-slf4j_2.12 | 2.5.22 | Apache 2.0 |
| course-actors-common | akka-testkit_2.12 | 2.5.22 | Apache 2.0 |
| actor-util | akka-actor_2.12 | 2.5.22 | Apache 2.0 |

#### Target Pekko Dependencies

| Module | Dependency | Version | License |
|--------|------------|---------|---------|
| service | pekko-testkit_2.13 | 1.0.2 | Apache 2.0 |
| actor-core | pekko-actor_2.13 | 1.0.2 | Apache 2.0 |
| actor-core | pekko-slf4j_2.13 | 1.0.2 | Apache 2.0 |
| course-actors-common | pekko-actor_2.13 | 1.0.2 | Apache 2.0 |
| course-actors-common | pekko-slf4j_2.13 | 1.0.2 | Apache 2.0 |
| course-actors-common | pekko-testkit_2.13 | 1.0.2 | Apache 2.0 |
| actor-util | pekko-actor_2.13 | 1.0.2 | Apache 2.0 |

### Appendix B: Configuration Transformation Examples

#### application.conf Transformation

**Before (Akka)**:
```hocon
akka {
  loggers = ["akka.event.slf4j.Slf4jLogger"]
  loglevel = "INFO"
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
  
  actor {
    provider = "akka.actor.LocalActorRefProvider"
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

**After (Pekko)**:
```hocon
pekko {
  loggers = ["org.apache.pekko.event.slf4j.Slf4jLogger"]
  loglevel = "INFO"
  logging-filter = "org.apache.pekko.event.slf4j.Slf4jLoggingFilter"
  
  actor {
    provider = "org.apache.pekko.actor.LocalActorRefProvider"
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

### Appendix C: Actor Files Inventory

#### Core Actor Files
1. `actor-core/src/main/java/org/sunbird/actor/core/BaseActor.java`
2. `actor-core/src/main/java/org/sunbird/actor/router/RequestRouter.java`
3. `actor-core/src/main/java/org/sunbird/actor/router/BackgroundRequestRouter.java`
4. `actor-core/src/main/java/org/sunbird/actor/service/BaseMWService.java`

#### Business Actor Files (18+)
1. HealthActor.java
2. CourseManagementActor.java
3. CourseBatchManagementActor.java
4. CourseEnrolmentActor.java
5. ContentConsumptionActor.java
6. CertificateActor.java
7. CourseBatchCertificateActor.java
8. SearchHandlerActor.java
9. PageManagementActor.java
10. CacheManagementActor.java
11. QRCodeDownloadManagementActor.java
12. BulkUploadManagementActor.java
13. EsSyncActor.java
14. BulkUploadBackgroundJobActor.java
15. CourseBatchNotificationActor.java
16. BackgroundJobManagerActor.java
17. GroupAggregatesActor.java
18. CollectionSummaryAggregateActor.java
19. ExhaustJobActor.java

### Appendix D: Maven POM Files to Update

1. `/pom.xml` (root)
2. `/service/pom.xml`
3. `/course-mw/pom.xml`
4. `/course-mw/course-actors-common/pom.xml`
5. `/course-mw/course-actors/pom.xml`
6. `/course-mw/enrolment-actor/pom.xml`
7. `/course-mw/sunbird-util/sunbird-platform-core/actor-core/pom.xml`
8. `/course-mw/sunbird-util/sunbird-platform-core/actor-util/pom.xml`
9. `/course-mw/sunbird-util/sunbird-platform-core/common-util/pom.xml`

### Appendix E: Key References

#### Official Documentation
- **Play Framework 3.0**: https://www.playframework.com/documentation/3.0.x/Home
- **Apache Pekko**: https://pekko.apache.org/docs/pekko/current/
- **Play Migration Guide**: https://www.playframework.com/documentation/3.0.x/Migration30
- **Pekko Migration Guide**: https://pekko.apache.org/docs/pekko/current/project/migration-guides.html

#### Community Resources
- **Play Framework GitHub**: https://github.com/playframework/playframework
- **Apache Pekko GitHub**: https://github.com/apache/pekko
- **Play Framework Google Group**: https://groups.google.com/g/play-framework
- **Apache Pekko Mailing Lists**: https://pekko.apache.org/community/

#### Articles and Guides
- "Migrating from Akka to Pekko" - Apache Pekko Documentation
- "Play 3.0 Breaking Changes" - Play Framework Documentation
- "Why Pekko?" - Apache Pekko Project Overview

### Appendix F: Glossary

- **Akka**: Original actor framework by Lightbend (now under BSL 1.1 license)
- **Apache Pekko**: Open-source fork of Akka 2.6.x under Apache License 2.0
- **Actor Model**: Concurrency model using actors as fundamental units
- **BSL**: Business Source License - source-available but not open source
- **EOL**: End of Life - no longer supported or updated
- **Play Framework**: Web framework for Java and Scala
- **SBT**: Scala Build Tool - native build tool for Play projects
- **UntypedAbstractActor**: Base class for untyped actors in Akka/Pekko

---

## Document Information

**Document Version**: 1.0  
**Author**: GitHub Copilot Coding Agent  
**Date**: January 2025  
**Status**: Final - Ready for Review  
**Confidentiality**: Internal Use

### Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-01 | Copilot Agent | Initial comprehensive report |

### Document Approval

This document should be reviewed and approved by:
- [ ] Technical Lead / Architect
- [ ] Engineering Manager
- [ ] Product Owner
- [ ] Security Team
- [ ] Legal/Compliance (for license review)

---

**END OF REPORT**
