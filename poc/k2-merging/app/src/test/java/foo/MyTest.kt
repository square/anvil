package foo

import org.junit.jupiter.api.Test

class MyTest {

  @Test
  fun `the appComponent is a thing`() {

    val component = DaggerAppComponent.create()

    assert(component is ComponentBase)
  }
}
