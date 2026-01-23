# HDFView Version Management

## Release Version Control

The HDFView release version is controlled by two sets of files that must be updated together prior to performing a release:

### 1. VERSION File

Update the `HDFVIEW_VERSION` property in the `VERSION` file at the project root:

```properties
HDFVIEW_VERSION=3.5.0
```

### 2. Maven POM Files

Update the `<version>` tags in the following files:

- **pom.xml** (root) - Project version
- **repository/pom.xml** - Parent version
- **object/pom.xml** - Parent version
- **hdfview/pom.xml** - Parent version and object dependency version

For releases, use the version without `-SNAPSHOT` suffix (e.g., `3.5.0`).
For development, use the version with `-SNAPSHOT` suffix (e.g., `99.99.99-SNAPSHOT` or `3.5.99-SNAPSHOT`).

