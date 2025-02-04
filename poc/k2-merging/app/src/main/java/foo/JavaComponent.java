package foo;

import dagger.BindsInstance;
import dagger.Component;

//@Component(modules = MyTypeBindingModule.class)
public interface JavaComponent {
  LibraryBinding injectClass();
  //@Component.Factory
  interface Factory {
    JavaComponent create(@BindsInstance String name);
  }
}
