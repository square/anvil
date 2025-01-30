package foo

import com.apple.eawt.Application
import com.squareup.anvil.annotations.MergeComponent
import dagger.BindsInstance
import dagger.Component
import javax.inject.Inject

// @MergeComponent(scope = Unit::class, modules = [ABindingModule::class])
// interface AppComponent {
//   val b: B
// }
//
// @ContributesTo(scope = Unit::class)
// interface ComponentBase {
//   fun injectClass(): InjectClass
// }
//
// @Module
// interface ABindingModule {
//   @Binds
//   fun bindAImpl(aImpl: AImpl): A
// }
//
// @Module
// @ContributesTo(scope = Unit::class)
// interface BBindingModule {
//   @Binds
//   fun bindBImpl(bImpl: BImpl): B
// }
//
// interface A
// class AImpl @Inject constructor() : A
//
// interface B
// class BImpl @Inject constructor() : B

// class InjectClass @Inject constructor(val a: A, val b: B)

// @MergeComponent(Unit::class)
class InjectClass @Inject constructor(val name: String)

@Component
interface AppComponent {
  @Component.Factory
  interface Factory {
    fun create(@BindsInstance name: String): AppComponent
  }
  fun injectClass(): InjectClass
}

suspend fun main() {

  // val component = DaggerAppComponent.create() as AppComponent
  //
  // val isCorrect = component is ComponentBase
  //
  // println("isCorrect: $isCorrect")
  //
  // component.injectClass()
}
