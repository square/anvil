package com.squareup.anvil.compiler.k2.ir;

import dagger.internal.Factory;
import kotlin.Metadata;
import kotlin.jvm.JvmStatic;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import javax.inject.Provider;

public final class TestClass_Factory implements Factory {
  @NotNull
  public static final Companion Companion = new Companion((DefaultConstructorMarker)null);
  @NotNull
  private final javax.inject.Provider param0;

  public TestClass_Factory(@NotNull javax.inject.Provider param0) {
    Intrinsics.checkNotNullParameter(param0, "param0");
    this.param0 = param0;
  }

  @NotNull
  public final javax.inject.Provider getParam0() {
    return this.param0;
  }

  @NotNull
  public final TestClass get() {
    return Companion.newInstance((String)param0.get());
  }

  //// $FF: synthetic method
  //// $FF: bridge method
  //public Object get() {
  //  return this.get();
  //}

  @JvmStatic
  @NotNull
  public static final TestClass_Factory create(@NotNull javax.inject.Provider param0) {
    return Companion.create(param0);
  }

  @JvmStatic
  @NotNull
  public static final TestClass newInstance(@NotNull String param0) {
    return Companion.newInstance(param0);
  }


  @Metadata(
      mv = {2, 0, 0},
      k = 1,
      xi = 48,
      d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u0016\u0010\u0004\u001a\u00020\u00052\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007H\u0007J\u0010\u0010\t\u001a\u00020\n2\u0006\u0010\u0006\u001a\u00020\bH\u0007¨\u0006\u000b"},
      d2 = {"Lfoo/TestClass_Factory$Companion;", "", "<init>", "()V", "create", "Lfoo/TestClass_Factory;", "param0", "Ljavax/inject/Provider;", "", "newInstance", "Lfoo/TestClass;", "root"}
  )
  public static final class Companion {
    private Companion() {
    }

    @JvmStatic
    @NotNull
    public final TestClass_Factory create(@NotNull Provider param0) {
      Intrinsics.checkNotNullParameter(param0, "param0");
      return new TestClass_Factory(param0);
    }

    @JvmStatic
    @NotNull
    public final TestClass newInstance(@NotNull String param0) {
      Intrinsics.checkNotNullParameter(param0, "param0");
      return new TestClass(param0);
    }

    // $FF: synthetic method
    public Companion(DefaultConstructorMarker $constructor_marker) {
      this();
    }
  }
}
