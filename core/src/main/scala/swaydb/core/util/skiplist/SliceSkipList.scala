/*
 * Copyright (c) 2020 Simer JS Plaha (simer.j@gmail.com - @simerplaha)
 *
 * This file is a part of SwayDB.
 *
 * SwayDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SwayDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with SwayDB. If not, see <https://www.gnu.org/licenses/>.
 *
 * Additional permission under the GNU Affero GPL version 3 section 7:
 * If you modify this Program, or any covered work, by linking or combining
 * it with other code, such other code is not for that reason alone subject
 * to any of the requirements of the GNU Affero GPL version 3.
 */

package swaydb.core.util.skiplist

import com.typesafe.scalalogging.LazyLogging
import swaydb.core.util.skiplist.KeyValue.Some
import swaydb.data.slice.Slice
import swaydb.data.util.SomeOrNoneCovariant

import scala.annotation.unchecked.uncheckedVariance

sealed trait KeyValue[+K, +V] extends SomeOrNoneCovariant[KeyValue[K, V], KeyValue.Some[K, V]] {
  override def noneC: KeyValue[Nothing, Nothing] = KeyValue.None
}

case object KeyValue {

  case object None extends KeyValue[Nothing, Nothing] {
    override def isNoneC: Boolean = true

    override def getC: Some[Nothing, Nothing] =
      throw new Exception(s"SkipList ${KeyValue.productPrefix} is of type ${this.productPrefix}")
  }

  object Some {
    @inline def apply[K, V](key: K, value: V, index: Int): Some[K, V] =
      new Some(key, value, index)
  }

  class Some[+K, +V](@volatile var key: K@uncheckedVariance = null.asInstanceOf[K],
                     @volatile var value: V@uncheckedVariance = null.asInstanceOf[K],
                     val index: Int) extends KeyValue[K, V] {
    override def isNoneC: Boolean = false

    override def getC: Some[K, V] = this

    @inline def toTuple =
      (key, value)
  }
}

object SliceSkipList {

  private def get[K, V](target: K,
                        slice: Slice[KeyValue.Some[K, V]],
                        hashIndex: Option[java.util.Map[K, KeyValue.Some[K, V]]])(implicit ordering: Ordering[K]): KeyValue[K, V] = {
    hashIndex match {
      case scala.Some(hashIndex) =>
        val value = hashIndex.get(target)
        if (value == null)
          KeyValue.None
        else
          value

      case None =>
        var start = 0
        var end = slice.size - 1

        while (start <= end) {
          val mid = start + (end - start) / 2
          val found = slice.get(mid)
          val compare = ordering.compare(found.key, target)
          if (compare == 0)
            if (found.value == null)
              return KeyValue.None
            else
              return found
          else if (compare < 0)
            start = mid + 1
          else
            end = mid - 1
        }

        KeyValue.None
    }
  }


  private def lower[K, V](target: K,
                          slice: Slice[KeyValue.Some[K, V]],
                          hashIndex: Option[java.util.Map[K, KeyValue.Some[K, V]]])(implicit ordering: Ordering[K]): KeyValue[K, V] = {
    var start = 0
    var end =
      hashIndex match {
        case scala.Some(hashIndex) =>
          val found = hashIndex.get(target)
          if (found == null)
            slice.size - 1
          else
            return slice
              .take(found.index)
              .reverse
              .find(_.value != null)
              .getOrElse(KeyValue.None)

        case None =>
          slice.size - 1
      }

    while (start <= end) {
      val mid = start + (end - start) / 2
      val found = slice.get(mid)
      val compare = ordering.compare(found.key, target)
      if (compare == 0)
        if (mid == 0)
          return KeyValue.None
        else
          return slice
            .take(mid)
            .reverse
            .find(_.value != null)
            .getOrElse(KeyValue.None)

      else if (compare < 0)
        start = mid + 1
      else
        end = mid - 1
    }

    if (start <= 0)
      KeyValue.None
    else
      slice
        .take(start)
        .reverse
        .find(_.value != null)
        .getOrElse(KeyValue.None)
  }

  private def floor[K, V](target: K,
                          slice: Slice[KeyValue.Some[K, V]],
                          hashIndex: Option[java.util.Map[K, KeyValue.Some[K, V]]])(implicit ordering: Ordering[K]): KeyValue[K, V] = {
    var start = 0
    var end =
      hashIndex match {
        case scala.Some(hashIndex) =>
          val found = hashIndex.get(target)
          if (found == null)
            slice.size - 1
          else
            return found

        case None =>
          slice.size - 1
      }

    while (start <= end) {
      val mid = start + (end - start) / 2
      val found = slice.get(mid)
      val compare = ordering.compare(found.key, target)
      if (compare == 0)
        if (found.value != null)
          return found
        else
          return slice
            .take(mid)
            .reverse
            .find(_.value != null)
            .getOrElse(KeyValue.None)

      else if (compare < 0)
        start = mid + 1
      else
        end = mid - 1
    }

    if (start <= 0)
      KeyValue.None
    else
      slice
        .take(start)
        .reverse
        .find(_.value != null)
        .getOrElse(KeyValue.None)
  }

  private def higher[K, V](target: K,
                           slice: Slice[KeyValue.Some[K, V]],
                           hashIndex: Option[java.util.Map[K, KeyValue.Some[K, V]]])(implicit ordering: Ordering[K]): KeyValue[K, V] = {
    var start =
      hashIndex match {
        case scala.Some(hashIndex) =>
          val found = hashIndex.get(target)
          if (found == null)
            0
          else
            return slice
              .drop(found.index + 1)
              .find(_.value != null)
              .getOrElse(KeyValue.None)

        case None =>
          0
      }

    var end = slice.size - 1

    while (start <= end) {
      val mid = start + (end - start) / 2
      val found = slice.get(mid)
      val compare = ordering.compare(found.key, target)
      if (compare == 0)
        if (mid == slice.size - 1)
          return KeyValue.None
        else
          return slice
            .drop(mid + 1)
            .find(_.value != null)
            .getOrElse(KeyValue.None)

      else if (compare < 0)
        start = mid + 1
      else
        end = mid - 1
    }

    if (end >= slice.size - 1)
      KeyValue.None
    else
      slice
        .drop(end + 1)
        .find(_.value != null)
        .getOrElse(KeyValue.None)
  }

  private def ceiling[K, V](target: K,
                            slice: Slice[KeyValue.Some[K, V]],
                            hashIndex: Option[java.util.Map[K, KeyValue.Some[K, V]]])(implicit ordering: Ordering[K]): KeyValue[K, V] = {
    var start =
      hashIndex match {
        case scala.Some(hashIndex) =>
          val found = hashIndex.get(target)
          if (found == null)
            0
          else
            return found

        case None =>
          0
      }

    var end = slice.size - 1

    while (start <= end) {
      val mid = start + (end - start) / 2
      val found = slice.get(mid)
      val compare = ordering.compare(found.key, target)
      if (compare == 0)
        if (found.value != null)
          return found
        else
          return slice
            .drop(mid + 1)
            .find(_.value != null)
            .getOrElse(KeyValue.None)

      else if (compare < 0)
        start = mid + 1
      else
        end = mid - 1
    }

    if (end >= slice.size - 1)
      KeyValue.None
    else
      slice
        .drop(end + 1)
        .find(_.value != null)
        .getOrElse(KeyValue.None)
  }
}

class SliceSkipList[OK, OV, K <: OK, V <: OV](@volatile private[skiplist] var slice: Slice[KeyValue.Some[K, V]],
                                              private[skiplist] val hashIndex: Option[java.util.Map[K, KeyValue.Some[K, V]]],
                                              val nullKey: OK,
                                              val nullValue: OV,
                                              val extendBy: Double)(implicit ordering: Ordering[K]) extends SkipListBatchable[OK, OV, K, V] with SkipList[OK, OV, K, V] with LazyLogging { self =>
  private var extensionCount = 0

  private def iterator(): Iterator[KeyValue.Some[K, V]] =
    new Iterator[KeyValue.Some[K, V]] {
      var nextOne: Some[K, V] = null
      val sliceIterator = self.slice.iterator

      override def hasNext: Boolean =
        if (sliceIterator.hasNext) {
          nextOne = sliceIterator.next()
          while (nextOne.value == null && sliceIterator.hasNext)
            nextOne = sliceIterator.next()

          nextOne.value != null
        } else {
          false
        }

      override def next(): Some[K, V] =
        nextOne
    }

  private def extend(): Unit = {
    val newSize = slice.size * extendBy
    val newSlice = Slice.of[KeyValue.Some[K, V]](newSize.toInt)
    newSlice addAll slice
    this.slice = newSlice
    extensionCount += 1
    if (extensionCount > 2)
      logger.warn(s"Write optimised SkipList extended $extensionCount times.")
  }

  override def put(key: K, value: V): Unit = {
    val lastOrNull = slice.lastOrNull
    if (lastOrNull == null) {
      if (slice.isFull) extend()
      val keyValue = KeyValue.Some(key, value, slice.size)
      slice add keyValue
      hashIndex foreach (_.put(key, keyValue))
    } else if (ordering.gt(key, lastOrNull.key)) {
      if (slice.isFull) extend()
      val keyValue = KeyValue.Some(key, value, slice.size)
      slice add keyValue
      hashIndex foreach (_.put(key, keyValue))
    } else {
      SliceSkipList.get(key, slice, hashIndex) match {
        case some: Some[K, V] =>
          some.value = value

        case KeyValue.None =>
          val newSlice = Slice.of[KeyValue.Some[K, V]](slice.size + 1)

          slice foreach {
            existing =>
              val existingKeyCompare = ordering.compare(existing.key, key)

              if (existingKeyCompare < 0) {
                newSlice add existing
              } else if (existingKeyCompare > 0) {
                val tail = slice.drop(newSlice.size)
                val keyValue = KeyValue.Some(key, value, newSlice.size)

                newSlice add keyValue
                hashIndex foreach (_.put(key, keyValue))

                tail foreach {
                  tail =>
                    val keyValue = KeyValue.Some(tail.key, tail.value, newSlice.size)
                    newSlice add keyValue
                    hashIndex foreach (_.put(tail.key, keyValue))
                }

                this.slice = newSlice
                return
              } else {
                //the above get which uses binarySearch and hashIndex should've already
                //handled cases where put key exists.
                throw new Exception("Get should updated this.")
              }
          }

          this.slice = newSlice
      }
    }
  }

  override def putIfAbsent(key: K, value: V): Boolean =
    SliceSkipList.get(key, slice, hashIndex) match {
      case KeyValue.None =>
        put(key, value)
        true

      case _: Some[K, V] =>
        false
    }

  override def get(target: K): OV =
    SliceSkipList.get(target, slice, hashIndex) match {
      case KeyValue.None =>
        nullValue

      case some: Some[K, V] =>
        some.value
    }

  override def remove(key: K): Unit =
    SliceSkipList.get(key, slice, hashIndex).foreachC {
      some =>
        hashIndex.foreach(_.remove(key))
        some.value = null.asInstanceOf[V]
    }

  override def lower(key: K): OV =
    SliceSkipList.lower(key, slice, hashIndex) match {
      case KeyValue.None =>
        nullValue

      case some: Some[K, V] =>
        some.value
    }

  override def lowerKey(key: K): OK =
    SliceSkipList.lower(key, slice, hashIndex) match {
      case KeyValue.None =>
        nullKey

      case some: Some[K, V] =>
        some.key
    }

  override def floor(key: K): OV =
    SliceSkipList.floor(key, slice, hashIndex) match {
      case KeyValue.None =>
        nullValue

      case some: Some[K, V] =>
        some.value
    }

  override def floorKeyValue(key: K): Option[(K, V)] =
    SliceSkipList.floor(key, slice, hashIndex) match {
      case KeyValue.None =>
        scala.None

      case some: Some[K, V] =>
        scala.Some(some.toTuple)
    }

  override def higher(key: K): OV =
    SliceSkipList.higher(key, slice, hashIndex) match {
      case KeyValue.None =>
        nullValue

      case some: Some[K, V] =>
        some.value
    }

  override def higherKey(key: K): OK =
    SliceSkipList.higher(key, slice, hashIndex) match {
      case KeyValue.None =>
        nullKey

      case some: Some[K, V] =>
        some.key
    }

  override def higherKeyValue(key: K): Option[(K, V)] =
    SliceSkipList.higher(key, slice, hashIndex) match {
      case KeyValue.None =>
        scala.None

      case some: Some[K, V] =>
        scala.Some(some.toTuple)
    }

  override def ceiling(key: K): OV =
    SliceSkipList.ceiling(key, slice, hashIndex) match {
      case KeyValue.None =>
        nullValue

      case some: Some[K, V] =>
        some.value
    }

  override def ceilingKey(key: K): OK =
    SliceSkipList.ceiling(key, slice, hashIndex) match {
      case KeyValue.None =>
        nullKey

      case some: Some[K, V] =>
        some.key
    }

  override def isEmpty: Boolean =
    hashIndex match {
      case scala.Some(value) =>
        value.isEmpty

      case None =>
        iterator().isEmpty
    }

  override def nonEmpty: Boolean =
    !isEmpty

  override def clear(): Unit = {
    this.slice = Slice.empty
    hashIndex.foreach(_.clear())
  }

  override def size: Int =
    slice.size

  override def contains(key: K): Boolean =
    SliceSkipList.get(key, slice, hashIndex).isSomeC

  private def headOrNullSome(): Some[K, V] = {
    val head = slice.headOrNull
    if (head == null)
      null
    else if (head.value == null)
      slice.find(_.value != null).orNull
    else
      head
  }

  private def lastOrNullSome(): Some[K, V] = {
    val last = slice.lastOrNull
    if (last == null)
      null
    else if (last.value == null)
      slice.reverse.find(_.value != null).orNull
    else
      last
  }

  override def headKey: OK = {
    val some = headOrNullSome()
    if (some == null)
      nullKey
    else
      some.key
  }

  override def lastKey: OK = {
    val some = lastOrNullSome()
    if (some == null)
      nullKey
    else
      some.key
  }

  override def count(): Int =
    iterator().size

  override def last(): OV = {
    val some = lastOrNullSome()
    if (some == null)
      nullValue
    else
      some.value
  }

  override def head(): OV = {
    val some = headOrNullSome()
    if (some == null)
      nullValue
    else
      some.value
  }

  override def headKeyValue: Option[(K, V)] = {
    val some = headOrNullSome()
    if (some == null)
      scala.None
    else
      scala.Some((some.key, some.value))
  }

  override def values(): Iterable[V] =
    new Iterable[V] {
      override def iterator: Iterator[V] =
        self.iterator().map(_.value)
    }

  override def foldLeft[R](r: R)(f: (R, (K, V)) => R): R =
    iterator().foldLeft(r) {
      case (r, some) =>
        f(r, (some.key, some.value))
    }

  override def foreach[R](f: (K, V) => R): Unit =
    iterator() foreach {
      keyValue =>
        f(keyValue.key, keyValue.value)
    }

  override def asScala: Iterable[(K, V)] =
    new Iterable[(K, V)] {
      override def iterator: Iterator[(K, V)] =
        self.iterator().map(_.toTuple)
    }

  override def subMap(from: K, to: K): Iterable[(K, V)] = ???

  override def subMap(from: K, fromInclusive: Boolean, to: K, toInclusive: Boolean): Iterable[(K, V)] = ???

  override def batch(batches: Iterable[SkipList.Batch[K, V]]): Unit = ???

  override def put(keyValues: Iterable[(K, V)]): Unit = ???
}
