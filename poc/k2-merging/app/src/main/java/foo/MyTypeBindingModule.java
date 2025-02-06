package foo;

import dagger.Binds;

@dagger.Module
interface MyTypeBindingModule {
  @Binds
  MyType bindMyType(InjectClass myType);
}
