package foo

import junit.framework.TestCase.assertTrue
import org.junit.jupiter.api.Test

class MyTest {

  @Test
  fun `the appComponent is a thing`() {
    assertTrue(DaggerJavaComponent.factory().create("Hello").injectClass.name.equals("Hello"))
  }
}
