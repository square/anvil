package foo

import org.junit.jupiter.api.Test

class MyTest {

  @Test
  fun `the appComponent is a thing`() {

    val factory = InjectClass_Factory({ "Butt" })

    val injectClass = factory

    val factoryName = factory.name as javax.inject.Provider<String>

    println("######################### ${factoryName.get()}")

    assert(factoryName != null)
  }
}
