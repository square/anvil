# Production Releases

1. Checkout `origin/main`.
2. Update the `CHANGELOG.md` file with the changes of this release (the format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
   * Copy the template for the next unreleased version at the top.
   * Delete unused section in the new release.
   * Update the links at the bottom of the CHANGELOG.md file and don't forget to change the link for the unreleased version.
3. Update the version in `gradle.properties` and remove the `-SNAPSHOT` suffix.
4. Commit the changes and create a tag:
   ```
   git commit -am "Releasing v0.1.0."
   git tag v1.0.0
   ```
5. Update the version in `gradle.properties` and add the `-SNAPSHOT` suffix.
6. Commit the change:
   ```
   git commit -am "Prepare next development version."
   ```
7. Push the two commits. This will start a Github action that publishes the release to Maven Central and creates a new release on Github.   
   ```
   git push && git push --tags
   ```
8. Close and release the staging repository at [Sonatype](https://s01.oss.sonatype.org).

# Snapshot Releases

Snapshot releases are automatically created whenever a commit to the `main` is pushed.  

# Manually uploading a release

Depending on the version in the `gradle.properties` file it will be either a production or snapshot release.
```
./gradlew clean publish --no-daemon --no-parallel --no-build-cache && cd gradle-plugin && ./gradlew clean publish publishPlugins --no-daemon --no-parallel --no-build-cache && cd ..
```

# Installing in Maven Local

```
./gradlew publishToMavenLocal && cd gradle-plugin && ./gradlew publishToMavenLocal && cd ..
```
