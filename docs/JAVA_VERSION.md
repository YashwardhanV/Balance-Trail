# Java version decision

## Decision

BalanceTrail targets **Java 21** and does not upgrade to Java 25 in this release.

Java 25 is the newest long-term-support release, and Spring Boot 3.5 is Java-25-ready. The upgrade is technically reasonable, but it is not required for an SDE-1 portfolio project. Java 21 is also an LTS release and the application has already been compiled, integration-tested, container-tested, browser-tested, and benchmarked on its Java 21 images.

Changing the runtime would require rebuilding both backend image stages, changing CI, rerunning all 17 tests, repeating the 1k/10k/100k benchmark matrix, and replacing the benchmark report. A version-number-only migration would add risk without demonstrating a new engineering capability.

Official references:

- [Oracle Java downloads](https://www.oracle.com/java/technologies/downloads/) identifies Java 25 as the latest LTS and Java 26 as the latest short-term feature release.
- [Oracle Java SE support roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html) lists Java 21 and Java 25 as LTS releases and Java 26 as non-LTS.
- [Spring Boot Java 25 support issue](https://github.com/spring-projects/spring-boot/issues/47245) records Java 25 readiness for Spring Boot 3.5.5 and later. BalanceTrail uses Spring Boot 3.5.16.

## Running on a computer with JDK 26 installed

The recommended command is unaffected by the host JDK:

```bash
docker compose up --build
```

The backend Dockerfile supplies Maven and Eclipse Temurin 21 for compilation, then Eclipse Temurin 21 for runtime. Docker does not use the host JDK 26 installation.

For running Maven directly on the host, install or select a JDK 21 distribution so local behavior matches Docker and CI. JDK 26 may be able to compile a Java 21 release target, but it is outside this project's tested toolchain and is not needed.

## Safe future Java 25 upgrade checklist

When there is a reason to upgrade:

1. Change `java.version` in `backend/pom.xml` to 25.
2. Change both backend Dockerfile images from Temurin 21 to Temurin 25.
3. Change the CI Java version from 21 to 25.
4. Update README, architecture, audit, and benchmark environment text.
5. Run all backend unit and PostgreSQL Testcontainers tests.
6. Rebuild the Compose stack and repeat the browser upload walkthrough.
7. Rerun all benchmark sizes three times and replace—not combine—the Java 21 metrics.

Do not claim a performance or security improvement merely because the Java version changed; measure any such claim separately.
