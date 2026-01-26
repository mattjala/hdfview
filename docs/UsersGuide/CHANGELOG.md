HDFView version 3.4.1

# 🔺 HDFView Changelog
All notable changes to this project will be documented in this file. This document describes the differences between this release and the previous HDFView release, platforms tested, and known problems in this release.

# 🔗 Quick Links
* [HDFView releases](https://github.com/HDFGroup/hdfview/releases)
* [HDFView source](https://github.com/HDFGroup/hdfview)
* [Getting help, questions, or comments](https://github.com/HDFGroup/hdfview/issues)

## 📖 Contents
* [Executive Summary](#-executive-summary-hdfview-version-430)
* [New Features & Improvements](#-new-features--improvements)
* [Bug Fixes](#-bug-fixes)
* [Platforms Tested](#%EF%B8%8F-platforms-tested)
* [Known Problems](#-known-problems)

# 🔆 Executive Summary: HDFView Version 3.4.1

This is small release to patch some visual bugs discovered after the publication of version 3.4.0.

## Minor Bug Fixes

* **[GH #456](https://github.com/HDFGroup/hdfview/issues/456)** - On MacOS, the top-level "settings" option did not open the User Options menu.

* **[GH #460](https://github.com/HDFGroup/hdfview/issues/460)** - The GUI window always displayed the application version as "HDFView-99.99.99".

# ☑️ Platforms Tested

HDFView is built and tested on the following platforms with **HDF 4.3.1** and **HDF5 2.0.0**:

* Linux (x86_64, aarch64)
* Windows
* macOS

Current test results and detailed platform information are available in the [GitHub repository](https://github.com/HDFGroup/hdfview).

# ⛔ Known Problems

* **PATH pointing to other HDF4/5 installations**: If the environment path points to a directory including HDF4/5 installations, then these installations may be loaded by HDFView instead of the bundled HDF4/5 versions, causing the application to fail to launch with a "failed to launch JVM" error. This can be resolved by either removing those directories from the PATH, or removing the HDF4/5 installations from that directory.

Please report any new problems found to the [HDFView issue tracker](https://github.com/HDFGroup/hdfview/issues).
