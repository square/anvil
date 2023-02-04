#!/bin/bash

./gradlew :integration-tests:library:clean :integration-tests:library:assemble --no-build-cache -Doverride_config-generateDaggerFactoriesWithAnvil=false

mv integration-tests/library/build/libs/library.jar integration-tests/library/dagger.jar

./gradlew :integration-tests:library:clean :integration-tests:library:assemble --no-build-cache -Doverride_config-generateDaggerFactoriesWithAnvil=true

mv integration-tests/library/build/libs/library.jar integration-tests/library/build/libs/anvil.jar
mv integration-tests/library/dagger.jar integration-tests/library/build/libs/dagger.jar

diffuse diff --jar integration-tests/library/build/libs/dagger.jar integration-tests/library/build/libs/anvil.jar
