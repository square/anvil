package foo;

import dagger.BindsInstance;
import dagger.Component;

@Component
public interface JavaComponent {
  InjectClass getInjectClass();
  @Component.Factory
  interface Factory {
    JavaComponent create(@BindsInstance String name);
  }
}
