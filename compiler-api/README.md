# Compiler-API

Anvil is composed of several independent code generators that provide hints for the later stages
in the build graph when all modules and components are merged together. These code generators 
are similar to annotation processors in the way that they look for a specific annotation like 
`@ContributesTo` and then generate necessary code. 

If you see yourself repeating the same code structures and patterns for your dependency injection
setup over and over again, then you can extend Anvil and implement your own code generator via the `CodeGenerator` interface.

## Steps
It's recommended to create your own annotation for your use case to not conflict with Anvil and
its own annotations. The annotation needs to live in its own module separate from the code 
generator, e.g. `:sample:annotation`.
```kotlin
annotation class MyAnnotation
```
Later you'll use this annotation to give your code generator a hint for work to perform.

In the code generator module `:sample:code-generator` you need to import the `compiler-api` 
artifact:
```groovy
dependencies {
  api "com.squareup.anvil:compiler-api:${latest_version}"
  implementation "com.squareup.anvil:compiler-utils:${latest_version}"

  // Optional:
  compileOnly "com.google.auto.service:auto-service-annotations:1.0"
  kapt "com.google.auto.service:auto-service:1.0"
}
```

After that implement the `CodeGenerator` interface:
```kotlin
@AutoService(CodeGenerator::class)
class SampleCodeGenerator : CodeGenerator {
  override fun isApplicable(context: AnvilContext): Boolean = true

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles
      .classesAndInnerClass(module)
      .filter { it.hasAnnotation(FqName("sample.MyAnnotation"), module) }
      .map { clazz ->
        // ...
        createGeneratedFile(
          codeGenDir = codeGenDir,
          packageName = // ...
          fileName = // ...
          content = // ...
        )
      }
      .toList()
  }
}
```

Note that the sample code generator is annotated with `@AutoService`. It's recommended to use the
[AutoService](https://github.com/google/auto/tree/master/service) library to generate necessary 
code for `ServiceLoader` API. Anvil uses this mechanism to load your custom code generator.

You can generate as many classes and files as you wish. You can even generate code that uses Anvil
or Dagger annotations and Anvil will process these files. The `generateCode()` function is called 
multiple times until no code generators generate code anymore.

To use your new code generator in any module you need to add the module to the Anvil classpath:
```groovy
plugins {
  id 'com.squareup.anvil' version "${latest_version}"
}

dependencies {
  anvil project(':sample:code-generator')
  implementation project(':sample:annotation')
}
```

## Limitations
Anvil code generators are no replacement for Java annotation processing or Kotlin symbol processing.
If you want to generate code independent of Anvil, then it's better to rely on an annotation 
processor, KSP or your own Kotlin compiler plugin instead.

Anvil code generators can only generate new code and not modify or remove existing code. For that
you need to create your own compiler plugin.
