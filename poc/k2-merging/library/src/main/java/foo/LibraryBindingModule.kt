package foo

import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module

@ContributesTo(scope = Any::class)
@Module
interface LibraryBindingModule {
  @Binds
  fun libraryBinding(libraryInjectClass: LibraryInjectClass): LibraryBinding
}
