# Production Releases

1. Update the `CHANGELOG.md` for the impending release.
2. Run `./release.sh <version>`.
3. Publish the release on the repo's releases tab.

# Snapshot Releases

Snapshot releases are automatically created whenever a commit to the `main` branch is pushed.

For other branches, maintainers can manually trigger a snapshot release from
the [publish-snapshot](https://github.com/square/anvil/actions/workflows/publish-snapshot.yml)
workflow. The version of that snapshot corresponds to the branch name. For example, a branch
named `feature/branch` will have a snapshot version of `feature-branch-SNAPSHOT`.

# Manually uploading a release

Depending on the version in the `gradle.properties` file it will be either a production or snapshot release.
```
./gradlew clean publish --no-daemon --no-parallel --no-build-cache
```

# Installing in Maven Local

```
./gradlew publishToMavenLocal
```
