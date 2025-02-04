package foo

import junit.framework.TestCase.assertTrue
import org.junit.jupiter.api.Test

class MyTest {

  @Test
  fun `the appComponent is a thing`() {
    assertTrue(DaggerAppComponent.factory().create("Hello").injectClass().param0.equals("Hello"))
  }
}
