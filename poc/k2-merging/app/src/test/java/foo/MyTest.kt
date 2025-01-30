package foo

import org.junit.jupiter.api.Test

class MyTest {

  @Test
  fun `the appComponent is a thing`() {

    val factory = InjectClass_Factory({ "Banana" }) as dagger.internal.Factory<InjectClass>

    val injectClass = factory.get()

    // val factoryName = factory.name as dagger.internal.Provider<String>
    //
    // println("######################### ${factoryName.get()}")
    //
    assert(injectClass.name != null)
  }
}
