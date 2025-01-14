package foo

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import dagger.Binds
import dagger.Module
import javax.inject.Inject

@MergeComponent(Unit::class, modules = [ABindingModule::class])
interface AppComponent {
  val b: B
}

interface ComponentBase {
  fun injectClass(): InjectClass
}

@Module
interface ABindingModule {
  @Binds
  fun bindAImpl(aImpl: AImpl): A
}

@Module
@ContributesTo(Unit::class)
interface BBindingModule {
  @Binds
  fun bindBImpl(bImpl: BImpl): B
}

interface A
class AImpl @Inject constructor() : A

interface B
class BImpl @Inject constructor() : B

class InjectClass @Inject constructor(val a: A, val b: B)

suspend fun main() {

  val component = DaggerAppComponent.create()

  component.injectClass()
}
