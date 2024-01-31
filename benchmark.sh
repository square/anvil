#!/bin/zsh

# e.g., 10-25-2020_20:45:16
NOW="$(date +'%m-%d-%Y_%R')"

# relative path to where reports are created.  Each report creates its own sub-directory
# replace ':' with '-' in order to support Windows
# e.g., 10-25-2020_20-45-16
REPORT_DIR=build/reports/profile_${NOW/:/-}

# relative path to the root directory of this project
PROJECT_ROOT=./benchmark

# where the sandboxed install of Gradle will go
PROFILER_GRADLE_HOME=~/.gradle-profiler-user-home

./gradlew createBenchmarkProject

gradle-profiler \
  --benchmark \
  --gradle-user-home $PROFILER_GRADLE_HOME \
  --scenario-file benchmark.scenarios \
  --project-dir "$PROJECT_ROOT" \
  --output-dir "$REPORT_DIR" \
  --no-daemon \
  --measure-config-time \
  --measure-local-build-cache
