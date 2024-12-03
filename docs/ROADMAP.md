# **Intro**

The last year has been an eventful one for Anvil, with much of the focus on fixing incremental compilation issues and fortifying our integration test capabilities. There has also been a lot of discussion and investigations around the different paths forward in terms of both K2 support and the long-term vision of the library. This doc aims to provide some transparency around where things have settled, where we’re headed, and why various choices were made.

# **Roadmap**

The long term vision is currently bucketed into three major milestones:

1. Introduce support for FIR and the K2 compiler. This will tentatively happen in two phases:  
   1. K2 baseline support for all primary Anvil functionality (contributions and merging), with KAPT utilized instead of KSP. (For those that need Dagger-KSP interop or K2 support now, we endorse Zac Sweers's Anvil-KSP fork: [https://github.com/ZacSweers/anvil](https://github.com/ZacSweers/anvil).)  
   2. K2 support for generating dagger factories.  
2. Anvil 3.0 \- Introduce Anvil Component generation, remove dependency on both Dagger and KAPT. At this point Anvil will become a completely independent Kotlin compiler plugin which broadly follows the existing contract and API surface. We don't currently have plans for KMP support since it's not something we need internally, but it's something we're open to re-evaluating after the release of Anvil 3.0.  
3. Long-tail: Evolve the API to match new capabilities and take advantage of our lack of dependency on Dagger, with a focus on ease of migration for consumers.

# **Context**

This section focuses on providing context for how we ended up with the roadmap items above and the decisions made along the way.

## **Breaking from Dagger**

The decision to break our dependency on Dagger stems from a number of constraints Anvil is limited by today, including:

* Needing to maintain compatibility with Dagger’s API, tooling choices, and internal structure  
  * Any API and code we generate must ultimately map back to Components, Modules, and binds/provides methods.  
  * We are only required to interop with KAPT because of needing to interop with Dagger. The same would go for KSP because Dagger now supports KSP. Without Dagger, neither KAPT nor KSP are necessary and we can drastically simplify the sequence of tooling that must all work together.  
  * Generated Component structures and factory expectations place limitations on things like utilizing internal constructors as well as newer Kotlin features.  
* Performance. Because Dagger \+ Anvil are executed sequentially, it is almost always going to result in slower build times compared to a pure Dagger world or pure Anvil compiler plugin world. This is true even with a heavily optimized module structure and performance enhancements like directly generating Dagger factories.  
* High-level framework behaviors and expectations. Dagger has many core requirements for how a dependency graph is pieced together which we may want to change in the future.   
  * For example, provided dependencies are non-singletons by default. They must be explicitly scoped AND be contributed to a module included in a given scope in order to have a single unique instance. This is something we’ve seen result in a lot of confusion and bugs over the years and would be one candidate to change in a non-Dagger world.
* Error reporting. Users must interpret errors from both Dagger and Anvil which can result in additional time debugging and a lack of clarity. We're limited in how much we can improve error messaging as well as the underlying validations.

## **KSP Support**

The topic of KSP support is something that has been in the air for us for quite some time, and we ultimately decided to not support KSP for a couple reasons which we’ll cover below.

### **Dagger-KSP, K2**

We would only strictly need KSP to support projects that choose to use Dagger-KSP. With our long-term vision of removing Dagger, it would result in a lot of engineering and maintenance time that could instead be spent on direct K2 support, which is necessary for the long term vision anyway.

In the short- to mid-term, KSP does solve some other issues for us including having improved incremental compilation support (compared to the lack of APIs available with K1) and existing K2 support. One path we evaluated was moving forward with KSP support as an interim update while skipping some other steps and working on Anvil 3.0 in parallel. As part of this evaluation, we utilized Zac Sweers's Anvil-KSP fork and spiked migrating Square’s internal projects to use KSP. While we were able to get everything functional, the main friction point we ran into was performance. 

### **Performance**

After benchmarking our projects on Anvil-KSP we saw notable performance regressions which were too great for us to commit to moving to at this time. Using one of our flagship apps, the benchmarks showed a 178s (or 92%) increase in incremental build times and a 159s (or 38%) increase in clean build times.

One option we considered here was the possibility of upstreaming Anvil-KSP to the main repo while not using it internally. The primary reason for not doing so is that Square’s internal projects are used as a form of gold-standard for flushing out bugs and use cases when we are testing out future changes and ensuring reliability of releases. Even with KSP support upstreamed, any future releases would effectively be un-battle-tested. Because we would not be using KSP ourselves, we would also be poorly positioned to provide support with any Anvil KSP issues that come up.

Returning to performance, it’s worth noting that Square’s internal projects have been blocked from upgrading to Kotlin 2.0 for other reasons, which means that we’ve only been able to benchmark using KSP \+ Kotlin 1.9 \+ K1, not KSP \+ Kotlin 2.0 \+ K2, and that our results aren’t necessarily representative of what other projects may see. This is something we still plan to benchmark once we’re able to, but we also need to continue moving forward in the meantime.
