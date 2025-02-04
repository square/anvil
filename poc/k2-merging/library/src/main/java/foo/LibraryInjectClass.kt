package foo

import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(scope = Any::class, boundType = LibraryBinding::class)
class LibraryInjectClass @Inject constructor(override val param0: String): LibraryBinding {
}

interface LibraryBinding {
  val param0: String
}
