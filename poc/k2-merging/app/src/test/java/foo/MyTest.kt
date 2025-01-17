package foo

import org.junit.jupiter.api.Test

class MyTest {

  @Test
  fun `the appComponent is a thing`() {

    val factory = InjectClass_Factory({ "Butt" })

    val injectClass = factory

    val factoryName = factory.name

    assert(factoryName != null)
  }
}
