package com.squareup.anvil.compiler.k2.fir.internal

/**
 * Transforms the elements of the receiver collection and adds the results to a set.
 *
 * @param destination The destination set where the transformed
 *   elements are placed. By default, it is an empty mutable set.
 * @param transform Maps elements of the receiver collection to the output set.
 * @receiver The collection to be transformed.
 * @return A set containing the transformed elements from the receiver collection.
 */
internal inline fun <C : Collection<T>, T, R> C.mapToSet(
  destination: MutableSet<R> = mutableSetOf(),
  transform: (T) -> R,
): Set<R> = mapTo(destination, transform)

/**
 * Transforms the elements of the receiver collection and adds the results to a set.
 *
 * @param destination The destination set where the transformed
 *   elements are placed. By default, it is an empty mutable set.
 * @param transform A function that maps elements of the receiver collection to the output set.
 * @receiver The collection to be transformed.
 * @return A set containing the transformed elements from the receiver collection.
 */
public inline fun <C : Collection<T>, T, R : Any> C.mapNotNullToSet(
  destination: MutableSet<R> = mutableSetOf(),
  transform: (T) -> R?,
): Set<R> = mapNotNullTo(destination, transform)

/**
 * Transforms the elements of the receiver array and adds the results to a set.
 *
 * @param destination The destination set where the transformed
 *   elements are placed. By default, it is an empty mutable set.
 * @param transform Maps elements of the receiver collection to the output set.
 * @receiver The array to be transformed.
 * @return A set containing the transformed elements from the receiver array.
 */
internal inline fun <T, R> Array<T>.mapToSet(
  destination: MutableSet<R> = mutableSetOf(),
  transform: (T) -> R,
): Set<R> {
  return mapTo(destination, transform)
}
