# HDFView Testing Guide

This guide explains how to run tests for HDFView locally and in CI environments.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Test Organization](#test-organization)
- [Running Tests Locally](#running-tests-locally)
- [Test Execution Options](#test-execution-options)
- [Simulating CI Environment](#simulating-ci-environment)
- [Troubleshooting](#troubleshooting)
- [CI/CD Testing](#cicd-testing)

---

## Prerequisites

### Required

1. **Java 21** - Installed and configured
2. **Maven 3.9+** - Build system
3. **HDF5 Native Libraries** - Configured in `build.properties`
4. **HDF4 Native Libraries** - Configured in `build.properties`
5. HDFView must be built with testing enabled

### Optional (for UI tests)

5. **Display Server** - X11 on Linux, native on macOS/Windows
6. **Xvfb** - Virtual framebuffer for headless testing (Linux only)
7. **A window manager** - required alongside Xvfb; without one no shell becomes active and
   SWTBot's widget lookups fail. `metacity` is enough.

### Configuration

Ensure your `build.properties` file has the correct paths:

```properties
# HDF5 Library Configuration
hdf5.lib.dir=/path/to/hdf5/lib

# HDF4 Library Configuration
hdf.lib.dir=/path/to/hdf4/lib

# Runtime library path
platform.hdf.lib=/path/to/hdf5/lib
```

---

## Quick Start

### Run All Tests

This will attempt to run the UI tests, which open and close HDFView windows and interact with them during the testing process.

```bash
# Run all tests (object module + UI tests)
mvn test
```

### Run Specific Tests

#### By Module

The object module tests HDF5 object model and file operations, and does not require a display.

The UI module (hdfview) tests SWT widgets, menus, dialogs, etc. It needs a display, and Xvfb is sufficient - it runs that way on Linux CI. A window manager must be running on that display as well: SWTBot resolves widgets against the active shell, and on a bare X server nothing ever becomes active. `ci-linux.yml` starts Xvfb and metacity and runs the suite as an advisory (non-blocking) step.

As of the Linux CI enablement work, 97 of 106 UI tests pass. The remaining failures are tracked separately: five are tests whose data files live in per-class subdirectories that the harness's flat working directory never reaches, and the rest are compound-datatype bugs.

```bash
# Object module only (no display needed)
mvn test -pl object

# UI tests only (requires display)
mvn test -pl hdfview
```

#### By Tag

```bash
# Unit tests only
mvn test -Dgroups="unit"

# Combined tags (OR)
mvn test -Dgroups="ui | integration"
```

#### By Test Class


```bash
# Run single test class
mvn test -Dtest=TestHDFViewMenu

# Run multiple test classes
mvn test -Dtest=TestHDFViewMenu,TestTreeViewFiles

# Run test method pattern
mvn test -Dtest=TestHDFViewMenu#verifyOpenButtonEnabled
```

## Test Organization

HDFView uses JUnit 5 with tag-based test organization:

### Test Tags

| Tag | Purpose | Count | Display Required |
|-----|---------|-------|------------------|
| `@Tag("unit")` | Fast unit tests | TBD | No |
| `@Tag("integration")` | Integration tests | 17 | Varies |
| `@Tag("ui")` | GUI/SWT tests | 17 | Yes |

### Test Modules

```
hdfview/
├── object/                    # HDF object model
│   └── src/test/java/
│       └── object/            # 15 test classes, 149 tests (no display)
│           ├── DatasetTest.java
│           ├── GroupTest.java
│           ├── AttributeTest.java
│           ├── H5FileTest.java
│           └── ... (11 more test classes)
│
└── hdfview/                   # GUI application
    └── src/test/java/
        └── uitest/            # 16 UI test classes (~83 tests)
            ├── TestAll.java   # Test suite
            ├── TestHDFViewMenu.java
            ├── TestTreeViewFiles.java
            └── ... (13 more test classes)
```

### Maven Surefire Executions

Tests run in multiple phases:

1. **default-test**: Unit tests (`@Tag("unit")`)
2. **unit-tests**: Unit tests in parallel (4 threads)
3. **integration-tests**: Integration tests serially
4. **ui-tests**: UI tests serially with display config

---

## Test Execution Options

### Parallel Execution

Unit tests run in parallel by default (4 threads). To change:

```bash
# Run with 8 threads
mvn test -DthreadCount=8

# Disable parallel execution
mvn test -Dparallel=none
```

### Test Debugging

```bash
# Debug specific test (suspend until debugger attaches)
mvn test -Dtest=TestHDFViewMenu -Dmaven.surefire.debug

# Custom debug port
mvn test -Dtest=TestHDFViewMenu \
  -Dmaven.surefire.debug="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000"
```

### Skipping Tests

```bash
# Skip test compilation and execution
mvn package -DskipTests

# Compile but don't run tests
mvn package -Dmaven.test.skip.exec=true
```

---

## Quick Reference Commands

```bash
# Most common test commands

# 1. Run all tests
mvn test

# 2. Run object tests only (fast)
mvn test -pl object

# 3. Run UI tests with Xvfb (start a window manager on the same display first)
Xvfb :99 -screen 0 1280x1024x24 &
DISPLAY=:99 metacity --sm-disable &
DISPLAY=:99 mvn test -pl hdfview

# 4. Run specific test
mvn test -Dtest=TestHDFViewMenu

# 5. Run with coverage
mvn test jacoco:report

# 6. Skip tests during build
mvn package -DskipTests

# 7. Clean and test
mvn clean test

# 8. Continue on failures
mvn test -Dmaven.test.failure.ignore=true
```