# Play Framework & Pekko Migration Checklist

**Repository**: SNT01/lms-service  
**Migration Type**: Play 2.7.2 + Akka 2.5.22 → Play 3.0 + Pekko 1.0  
**Timeline**: 14-16 weeks

---

## Pre-Migration Checklist

### Week 0: Approval & Planning
- [ ] Review full compatibility report
- [ ] Review executive summary
- [ ] Present findings to stakeholders
- [ ] Secure budget approval ($200K-423K)
- [ ] Allocate team resources (3-4 devs, 2 QA, 1 DevOps)
- [ ] Set migration timeline (14-16 weeks)
- [ ] Assign migration team roles
- [ ] Schedule kickoff meeting

---

## Phase 1: Preparation & Assessment (Weeks 1-2)

### Environment Setup
- [ ] Create isolated migration branch (`migration/play3-pekko`)
- [ ] Set up isolated development environment
- [ ] Set up CI/CD for migration branch
- [ ] Document rollback procedure
- [ ] Test rollback procedure in staging

### Baseline Establishment
- [ ] Run current test suite and document results
- [ ] Measure and record current performance metrics:
  - [ ] Response time (avg, p95, p99)
  - [ ] Throughput (requests/sec)
  - [ ] Memory usage
  - [ ] CPU utilization
  - [ ] Error rates
- [ ] Document current code coverage
- [ ] Document all actor communication patterns
- [ ] List all external integrations and dependencies

### Test Suite Development
- [ ] Audit existing tests for completeness
- [ ] Add missing actor unit tests
- [ ] Add actor communication integration tests
- [ ] Create performance test suite
- [ ] Create regression test scenarios
- [ ] Set up load testing framework (JMeter/Gatling)

### Build System Validation
- [ ] Test play2-maven-plugin compatibility with Play 3.0
- [ ] Decision: Stick with Maven or migrate to SBT?
- [ ] If SBT: Create SBT build files
- [ ] Test build process in isolation
- [ ] Document build procedure

### Team Training
- [ ] Schedule Pekko training session
- [ ] Schedule Play 3.0 training session
- [ ] Share migration documentation with team
- [ ] Set up internal knowledge base
- [ ] Review Apache Pekko documentation
- [ ] Review Play 3.0 migration guides

---

## Phase 2: Dependency Updates (Weeks 3-4)

### Scala Version Upgrade (2.12 → 2.13)
- [ ] Update root pom.xml Scala version
- [ ] Update all module pom.xml Scala versions
- [ ] Update Scala library dependency
- [ ] Test compilation
- [ ] Fix Scala 2.13 compatibility issues
- [ ] Run test suite
- [ ] Fix broken tests

### Core Dependency Updates
- [ ] Update Jackson to 2.16.x or latest
  ```xml
  <jackson.version>2.16.x</jackson.version>
  ```
- [ ] Update Guice if needed
- [ ] Update Netty dependencies
- [ ] Update logging libraries (Logback, SLF4J)
- [ ] Update test dependencies (JUnit, Mockito, PowerMock)

### Dependency Conflict Resolution
- [ ] Run `mvn dependency:tree` and analyze
- [ ] Identify conflicting transitive dependencies
- [ ] Add exclusions where needed
- [ ] Validate all dependencies compatible with Scala 2.13
- [ ] Test build with updated dependencies
- [ ] Run full test suite
- [ ] Fix any test failures

### Validation
- [ ] Application builds successfully
- [ ] All tests pass
- [ ] Application starts and runs
- [ ] Smoke test critical features
- [ ] Performance comparable to baseline

---

## Phase 3: Akka to Pekko Migration (Weeks 5-7)

### Maven POM Updates

#### Root pom.xml
- [ ] Update typesafe.akka.version property (remove)
- [ ] Add pekko.version property (1.0.2)
- [ ] No Akka dependencies at root level

#### service/pom.xml
- [ ] Remove Akka dependencies:
  ```xml
  <!-- REMOVE -->
  <dependency>
    <groupId>com.typesafe.akka</groupId>
    <artifactId>akka-testkit_${scala.major.version}</artifactId>
    <version>${typesafe.akka.version}</version>
  </dependency>
  ```
- [ ] Add Pekko dependencies:
  ```xml
  <!-- ADD -->
  <dependency>
    <groupId>org.apache.pekko</groupId>
    <artifactId>pekko-testkit_${scala.major.version}</artifactId>
    <version>${pekko.version}</version>
  </dependency>
  ```
- [ ] Update Play dependencies to 3.0.x
  ```xml
  <play2.version>3.0.x</play2.version>
  ```
- [ ] Update scala.major.version to 2.13
  ```xml
  <scala.major.version>2.13</scala.major.version>
  ```

#### actor-core/pom.xml
- [ ] Remove Akka dependencies (akka-actor, akka-slf4j)
- [ ] Add Pekko dependencies (pekko-actor, pekko-slf4j)
- [ ] Update version references
- [ ] Update Scala version

#### course-actors-common/pom.xml
- [ ] Remove all Akka dependencies
- [ ] Add corresponding Pekko dependencies
- [ ] Update Scala version
- [ ] Test compilation

#### Other module pom.xml files
- [ ] course-actors/pom.xml
- [ ] enrolment-actor/pom.xml
- [ ] actor-util/pom.xml
- [ ] common-util/pom.xml

### Java Source Code Updates

#### Core Actor Classes
- [ ] `actor-core/src/main/java/org/sunbird/actor/core/BaseActor.java`
  - [ ] Update imports: `akka.*` → `org.apache.pekko.*`
  - [ ] Review for deprecated API usage
  - [ ] Test compilation
  
- [ ] `actor-core/src/main/java/org/sunbird/actor/service/BaseMWService.java`
  - [ ] Update imports
  - [ ] Update remote actor configuration strings
  - [ ] Test actor system initialization

- [ ] `actor-core/src/main/java/org/sunbird/actor/router/RequestRouter.java`
  - [ ] Update imports
  - [ ] Test routing behavior

- [ ] `actor-core/src/main/java/org/sunbird/actor/router/BackgroundRequestRouter.java`
  - [ ] Update imports
  - [ ] Test background routing

#### Service Layer
- [ ] `service/app/modules/ActorStartModule.java`
  - [ ] Update imports: `akka.*` → `org.apache.pekko.*`
  - [ ] Update `AkkaGuiceSupport` → `PekkoGuiceSupport`
  - [ ] Test DI binding
  
- [ ] `service/app/controllers/BaseController.java`
  - [ ] Update imports
  - [ ] Update `ActorRef`, `ActorSelection` usage
  - [ ] Update `PatternsCS` usage
  - [ ] Test actor response handling

#### Business Actor Classes (26 files)
- [ ] HealthActor.java
- [ ] CourseManagementActor.java
- [ ] CourseBatchManagementActor.java
- [ ] CourseEnrolmentActor.java
- [ ] ContentConsumptionActor.java
- [ ] CertificateActor.java
- [ ] CourseBatchCertificateActor.java
- [ ] SearchHandlerActor.java
- [ ] PageManagementActor.java
- [ ] CacheManagementActor.java
- [ ] QRCodeDownloadManagementActor.java
- [ ] BulkUploadManagementActor.java
- [ ] EsSyncActor.java
- [ ] BulkUploadBackgroundJobActor.java
- [ ] CourseBatchNotificationActor.java
- [ ] BackgroundJobManagerActor.java
- [ ] GroupAggregatesActor.java
- [ ] CollectionSummaryAggregateActor.java
- [ ] ExhaustJobActor.java
- [ ] (And 7 more actor files)

**For each actor file**:
- [ ] Update imports
- [ ] Verify compilation
- [ ] Check for deprecated API usage

#### Utility Classes
- [ ] `actorutil/impl/InterServiceCommunicationImpl.java`
  - [ ] Update imports
  - [ ] Update `Patterns.ask` usage
  
- [ ] All controller classes (15 files in service/app/controllers/)
  - [ ] Update imports
  - [ ] Verify actor communication code

#### Filters
- [ ] `service/app/filters/CustomGzipFilter.java`
  - [ ] Update Akka Stream imports if used
  
- [ ] `service/app/filters/ResponseFilter.scala`
  - [ ] Update imports
  - [ ] Update Scala syntax for 2.13 if needed

### Configuration Updates

#### service/conf/application.conf
- [ ] Global search/replace: `akka {` → `pekko {`
- [ ] Update logger references:
  ```hocon
  # BEFORE
  loggers = ["akka.event.slf4j.Slf4jLogger"]
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
  
  # AFTER
  loggers = ["org.apache.pekko.event.slf4j.Slf4jLogger"]
  logging-filter = "org.apache.pekko.event.slf4j.Slf4jLoggingFilter"
  ```
- [ ] Update actor provider:
  ```hocon
  # BEFORE
  provider = "akka.actor.LocalActorRefProvider"
  
  # AFTER
  provider = "org.apache.pekko.actor.LocalActorRefProvider"
  ```
- [ ] Update remote configuration (if used):
  ```hocon
  # BEFORE
  akka.actor.provider=akka.remote.RemoteActorRefProvider
  akka.remote.enabled-transports = ["akka.remote.netty.tcp"]
  
  # AFTER
  pekko.actor.provider=org.apache.pekko.remote.RemoteActorRefProvider
  pekko.remote.enabled-transports = ["org.apache.pekko.remote.netty.tcp"]
  ```
- [ ] Validate all dispatcher configurations
- [ ] Validate all actor deployment configurations
- [ ] Check for any hardcoded "akka" strings in settings

#### Other Configuration Files
- [ ] `course-mw/sunbird-util/cache-utils/src/test/resources/application.conf`
  - [ ] Update akka → pekko
  
- [ ] `course-mw/sunbird-util/sunbird-platform-core/common-util/src/main/resources/application.conf`
  - [ ] Update akka → pekko

### Test Files Updates

#### Test Classes (20+ files)
- [ ] `HealthActorTest.java`
- [ ] `CourseBatchManagementActorTest.java`
- [ ] `CourseEnrolmentTest.scala`
- [ ] All other test files using akka-testkit

**For each test file**:
- [ ] Update imports
- [ ] Update `TestKit` usage
- [ ] Update `ActorSystem` creation
- [ ] Verify test compilation
- [ ] Run tests

### Build & Validation
- [ ] Build entire project: `mvn clean install`
- [ ] Fix any compilation errors
- [ ] Run unit tests: `mvn test`
- [ ] Fix failing tests
- [ ] Run integration tests
- [ ] Verify actor system starts correctly
- [ ] Test actor message passing
- [ ] Validate dispatcher configurations work
- [ ] Check for any runtime errors in logs

---

## Phase 4: Play Framework Upgrade (Weeks 8-10)

### Play Dependencies Update

#### service/pom.xml
- [ ] Update Play version:
  ```xml
  <play2.version>3.0.x</play2.version>  <!-- Latest 3.0 -->
  ```
- [ ] Update play2-maven-plugin (if compatible):
  ```xml
  <play2.plugin.version>1.0.0-rcX</play2.plugin.version>
  ```
  Or migrate to SBT if plugin doesn't support Play 3.0
  
- [ ] Update Play dependencies:
  - [ ] play-guice_2.13
  - [ ] play-netty-server_2.13
  - [ ] play_2.13
  - [ ] filters-helpers_2.13
  - [ ] play-pekko-http-server_2.13 (was play-akka-http-server)
  - [ ] play-specs2_2.13
  - [ ] play-logback_2.13

### Controller Updates

#### Review Play 3.0 Breaking Changes
- [ ] Review Play 3.0 migration guide
- [ ] List deprecated APIs used in project
- [ ] Plan refactoring for each deprecated API

#### Update Controllers
- [ ] Review `Controller` base class changes
- [ ] Update request/response handling if needed
- [ ] Update JSON parsing/serialization
- [ ] Update action composition if used
- [ ] Check for WebSocket usage (API may have changed)

#### Update Filters
- [ ] Update `EssentialFilter` usage in CustomGzipFilter
- [ ] Update filter binding in application.conf
- [ ] Test filter chain

#### Update Modules
- [ ] `modules/StartModule.java` - Review Guice bindings
- [ ] `modules/ActorStartModule.java` - Already updated in Phase 3
- [ ] `modules/OnRequestHandler.java` - Check for API changes
- [ ] `modules/ErrorHandler.java` - Update error handling if needed

### Configuration Updates
- [ ] Review application.conf for Play 3.0 changes
- [ ] Update HTTP server configuration
- [ ] Update any Play-specific settings
- [ ] Update routes file if needed

### Build System
If Maven plugin doesn't support Play 3.0:
- [ ] Create build.sbt file
- [ ] Create project/plugins.sbt
- [ ] Migrate Maven profiles to SBT
- [ ] Test SBT build
- [ ] Update CI/CD for SBT

### Validation
- [ ] Build project successfully
- [ ] Run tests
- [ ] Start application
- [ ] Test all endpoints
- [ ] Verify filters working
- [ ] Check logs for errors

---

## Phase 5: Testing & Validation (Weeks 11-13)

### Unit Testing
- [ ] Run full unit test suite
- [ ] Achieve >= 80% code coverage
- [ ] Fix all failing tests
- [ ] Add tests for any gaps found
- [ ] Test actor message handling
- [ ] Test actor lifecycle (start, stop, restart)
- [ ] Test error handling and recovery
- [ ] Test timeout scenarios

### Integration Testing
- [ ] Test actor-to-actor communication
- [ ] Test controller-to-actor communication
- [ ] Test database interactions
- [ ] Test external service calls
- [ ] Test authentication/authorization
- [ ] Test all API endpoints
- [ ] End-to-end test critical workflows

### Performance Testing

#### Load Testing
- [ ] Set up load testing environment
- [ ] Replicate production-like data volume
- [ ] Run load tests with JMeter/Gatling
- [ ] Compare metrics with baseline:
  - [ ] Response time (should be within 10%)
  - [ ] Throughput (should be equal or better)
  - [ ] Error rate (should be comparable)
  - [ ] Resource utilization

#### Stress Testing
- [ ] Test system under extreme load
- [ ] Identify breaking points
- [ ] Test recovery after stress
- [ ] Validate actor system handles load

#### Soak Testing
- [ ] Run application for extended period (24-48 hours)
- [ ] Monitor for memory leaks
- [ ] Monitor for resource exhaustion
- [ ] Validate stability under sustained load

### Actor System Validation
- [ ] Test all actor routing configurations
- [ ] Test all dispatcher configurations
- [ ] Validate actor pool sizes appropriate
- [ ] Test actor supervision strategies
- [ ] Test actor message handling under load
- [ ] Monitor mailbox sizes
- [ ] Test actor restart scenarios

### Regression Testing
- [ ] Execute full regression test suite
- [ ] Test all known edge cases
- [ ] Re-test all previously fixed bugs
- [ ] Test error scenarios
- [ ] Test failure recovery

### Compatibility Testing
- [ ] Test with all supported clients
- [ ] Test API backward compatibility
- [ ] Test database compatibility
- [ ] Test with all external integrations

### Security Testing
- [ ] Run security vulnerability scan
- [ ] Test authentication mechanisms
- [ ] Test authorization rules
- [ ] Test input validation
- [ ] Review dependency vulnerabilities
- [ ] Test for common vulnerabilities (OWASP Top 10)

### User Acceptance Testing
- [ ] Deploy to QA environment
- [ ] Execute UAT test scenarios
- [ ] Test critical business workflows
- [ ] Get stakeholder sign-off

### Documentation
- [ ] Document all test results
- [ ] Document any issues found and fixed
- [ ] Update technical documentation
- [ ] Update API documentation
- [ ] Create release notes

---

## Phase 6: Deployment (Week 14)

### Pre-Deployment Preparation
- [ ] Review all test results
- [ ] Confirm all tests passing
- [ ] Review performance metrics acceptable
- [ ] Get final stakeholder approval
- [ ] Schedule deployment window
- [ ] Brief operations team
- [ ] Prepare rollback plan
- [ ] Set up enhanced monitoring

### Staging Deployment
- [ ] Deploy to staging environment
- [ ] Run smoke tests
- [ ] Run full test suite in staging
- [ ] Monitor for 24-48 hours
- [ ] Performance testing in staging
- [ ] UAT in staging
- [ ] Document any issues
- [ ] Fix critical issues before production

### Production Deployment Planning
- [ ] Create deployment runbook
- [ ] Schedule low-traffic deployment window
- [ ] Coordinate with all teams
- [ ] Prepare communication to users (if needed)
- [ ] Set up war room for deployment
- [ ] Ensure rollback artifacts ready

### Database Preparation (if migrations needed)
- [ ] Test database migrations in staging
- [ ] Create database backup procedure
- [ ] Create database rollback procedure
- [ ] Test database rollback

### Monitoring Setup
- [ ] Set up enhanced application monitoring
- [ ] Set up actor system metrics:
  - [ ] Mailbox size
  - [ ] Processing time
  - [ ] Message throughput
  - [ ] Actor restarts
- [ ] Set up alerts for anomalies
- [ ] Set up error tracking
- [ ] Set up user experience monitoring
- [ ] Create monitoring dashboards

### Production Deployment
- [ ] Execute deployment runbook
- [ ] Deploy new version
- [ ] Run smoke tests
- [ ] Monitor logs for errors
- [ ] Monitor metrics dashboards
- [ ] Test critical workflows
- [ ] Gradually increase traffic (if using canary/blue-green)

### Post-Deployment Validation
- [ ] Monitor for 24 hours minimum
- [ ] Validate all metrics within acceptable range
- [ ] Check error rates
- [ ] Verify user reports/complaints
- [ ] Performance comparison with baseline
- [ ] Stakeholder sign-off

### Rollback Readiness
- [ ] Monitor for rollback triggers:
  - [ ] Critical functionality broken
  - [ ] Performance degradation > 30%
  - [ ] Error rate spike
  - [ ] Data inconsistencies
- [ ] Execute rollback if needed
- [ ] Post-mortem if rollback occurred

---

## Post-Migration (Weeks 15-16)

### Stabilization
- [ ] Continue monitoring for 2 weeks
- [ ] Fix any minor issues found
- [ ] Tune performance if needed
- [ ] Optimize actor configurations
- [ ] Address user feedback

### Documentation
- [ ] Update all technical documentation
- [ ] Document lessons learned
- [ ] Create troubleshooting guides
- [ ] Update runbooks
- [ ] Document new architecture

### Knowledge Transfer
- [ ] Conduct training sessions for team
- [ ] Share migration experience
- [ ] Document best practices for Pekko/Play 3.0
- [ ] Update onboarding materials

### Cleanup
- [ ] Remove old migration branches
- [ ] Archive old build artifacts
- [ ] Clean up temporary infrastructure
- [ ] Update dependency management

### Retrospective
- [ ] Conduct migration retrospective
- [ ] Document what went well
- [ ] Document what could be improved
- [ ] Share insights with organization

---

## Ongoing Maintenance

### Regular Tasks
- [ ] Monitor application health
- [ ] Review and act on alerts
- [ ] Keep dependencies updated
- [ ] Monitor Pekko/Play releases
- [ ] Apply security patches promptly

### Quarterly Reviews
- [ ] Review performance metrics
- [ ] Review error rates
- [ ] Review actor system health
- [ ] Plan optimizations
- [ ] Update documentation

---

## Emergency Rollback Procedure

If critical issues occur:

1. **Stop Traffic**
   - [ ] Switch load balancer to maintenance mode
   - [ ] Stop new requests

2. **Revert Deployment**
   - [ ] Deploy previous version artifacts
   - [ ] Revert configuration changes
   - [ ] Revert database migrations (if any)

3. **Validate Rollback**
   - [ ] Start application
   - [ ] Run smoke tests
   - [ ] Verify critical functionality
   - [ ] Resume traffic gradually

4. **Communicate**
   - [ ] Notify stakeholders
   - [ ] Document issues encountered
   - [ ] Plan remediation

5. **Post-Mortem**
   - [ ] Analyze root cause
   - [ ] Document lessons learned
   - [ ] Plan fixes
   - [ ] Schedule retry

---

## Success Criteria

Migration is complete when:
- [x] All code migrated to Pekko and Play 3.0
- [x] All tests passing
- [x] Performance within 10% of baseline
- [x] Zero regression bugs in production
- [x] Monitoring shows healthy system
- [x] Stakeholder approval received
- [x] Documentation updated
- [x] Team trained on new stack

---

## Notes & Issues

**Use this section to track issues during migration**:

| Date | Issue | Severity | Resolution | Status |
|------|-------|----------|------------|--------|
| | | | | |

---

## Contacts

**Migration Team**:
- Lead Developer: _______________
- Backend Dev 1: _______________
- Backend Dev 2: _______________
- Backend Dev 3: _______________
- QA Lead: _______________
- QA Engineer: _______________
- DevOps Lead: _______________

**Escalation**:
- Technical Lead: _______________
- Engineering Manager: _______________
- On-call Support: _______________

---

**Checklist Version**: 1.0  
**Last Updated**: January 2025  
**Status**: Ready for Use

**Based on**: PLAY_FRAMEWORK_PEKKO_MIGRATION_REPORT.md
