package com.squareup.scopes

import kotlin.reflect.KClass
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class SingleIn(val clazz: KClass<*>)
