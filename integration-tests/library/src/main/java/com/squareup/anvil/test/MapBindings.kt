package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.MapKey
import javax.inject.Named

@MapKey
public annotation class BindingKey(val value: String)

@ContributesMultibinding(AppScope::class, ignoreQualifier = true)
@Named("abc")
@BindingKey("1")
public object MapBinding1 : ParentType

@ContributesMultibinding(AppScope::class)
@Named("abc")
@BindingKey("2")
public object MapBinding2 : ParentType

@ContributesMultibinding(AppScope::class)
@BindingKey("3")
public object MapBinding3 : ParentType
