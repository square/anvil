# Compiler-Utils

Contains several utility functions used within Anvil and external `CodeGenerator`s. There's no 
API stability guaranteed for these functions. They can be changed or removed in every single
release. They're not documented, and the behavior can change. So use at your own risk. 

The artifact comes with testing utilities for easier end-to-end tests for `CodeGenerators`. You 
can add the testing utilities to your project through test fixtures:
```groovy
testImplementation testFixtures('dev.zacsweers.anvil:compiler-utils:VERSION')
```
