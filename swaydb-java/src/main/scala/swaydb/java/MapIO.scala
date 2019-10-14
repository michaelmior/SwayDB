/*
 * Copyright (c) 2019 Simer Plaha (@simerplaha)
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
 */

package swaydb.java

import java.util.Optional
import java.util.function.{BiFunction, Consumer, Predicate}

import swaydb.Prepare
import swaydb.data.accelerate.LevelZeroMeter
import swaydb.data.compaction.LevelMeter
import swaydb.java.data.util.Java._
import swaydb.java.data.util.KeyVal

import scala.collection.JavaConverters._
import scala.compat.java8.DurationConverters._
import scala.compat.java8.OptionConverters._

/**
 * IOMap database API.
 *
 * For documentation check - http://swaydb.io/tag/
 */
case class MapIO[K, V, F](asScala: swaydb.Map[K, V, _, swaydb.IO.ThrowableIO]) {

  implicit val exceptionHandler = swaydb.IO.ExceptionHandler.Throwable

  private implicit def toIO[Throwable, R](io: swaydb.IO[scala.Throwable, R]): IO[scala.Throwable, R] = new IO[scala.Throwable, R](io)

  def put(key: K, value: V): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.put(key, value)

  def put(key: K, value: V, expireAfter: java.time.Duration): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.put(key, value, expireAfter.toScala)

  def put(keyValues: KeyVal[K, V]*): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.put(keyValues.map(_.toTuple))

  def put(keyValues: StreamIO[KeyVal[K, V]]): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.put(keyValues.asScala.map(_.toTuple))

  def put(keyValues: java.util.Iterator[KeyVal[K, V]]): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.put(keyValues.asScala.map(_.toTuple).toIterable)

  def remove(key: K): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.remove(key)

  def remove(from: K, to: K): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.remove(from, to)

  def remove(keys: K*): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.remove(keys)

  def remove(keys: StreamIO[K]): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.remove(keys.asScala)

  def remove(keys: java.util.Iterator[K]): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.remove(keys.asScala.toIterable)

  def expire(key: K, after: java.time.Duration): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.expire(key, after.toScala)

  def expire(from: K, to: K, after: java.time.Duration): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.expire(from, to, after.toScala)

  def expire(keys: KeyVal[K, java.time.Duration]*): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.expire(
      keys map {
        keyValue =>
          (keyValue.key, keyValue.value.toScala.fromNow)
      }
    )

  def expire(keys: StreamIO[KeyVal[K, java.time.Duration]]): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.expire(keys.asScala.map(_.toScala))

  def expire(keys: java.util.Iterator[(K, java.time.Duration)]): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.expire(keys.asScala.map(_.asScalaDeadline).toIterable)

  def update(key: K, value: V): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.update(key, value)

  def update(from: K, to: K, value: V): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.update(from, to, value)

  def update(keyValues: KeyVal[K, V]*): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.update(keyValues.map(_.toTuple))

  def update(keyValues: StreamIO[KeyVal[K, V]]): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.update(keyValues.asScala.map(_.toTuple))

  def update(keyValues: java.util.Iterator[KeyVal[K, V]]): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.update(keyValues.asScala.map(_.toTuple).toIterable)

  def clear(): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.clear()

  def registerFunction[PF <: F with swaydb.java.PureFunction[K, V]](function: PF): IO[scala.Throwable, swaydb.IO.Done] = {
    val scalaMap = asScala.asInstanceOf[swaydb.Map[K, V, swaydb.PureFunction[K, V], swaydb.IO.ThrowableIO]]
    scalaMap.registerFunction(function.asScala)
  }

  def applyFunction[PF <: F with swaydb.java.PureFunction[K, V]](key: K, function: PF): IO[scala.Throwable, swaydb.IO.Done] = {
    val scalaMap = asScala.asInstanceOf[swaydb.Map[K, V, swaydb.PureFunction[K, V], swaydb.IO.ThrowableIO]]
    scalaMap.applyFunction(key, function.asScala)
  }

  def applyFunction[PF <: F with swaydb.java.PureFunction[K, V]](from: K, to: K, function: PF): IO[scala.Throwable, swaydb.IO.Done] = {
    val scalaMap = asScala.asInstanceOf[swaydb.Map[K, V, swaydb.PureFunction[K, V], swaydb.IO.ThrowableIO]]
    scalaMap.applyFunction(from, to, function.asScala)
  }

  def commit(prepare: Prepare[K, V]*): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.commit(prepare)

  def commit(prepare: StreamIO[Prepare[K, V]]): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.commit(prepare.asScala)

  def commit(prepare: java.util.Iterator[Prepare[K, V]]): IO[scala.Throwable, swaydb.IO.Done] =
    asScala.commit(prepare.asScala.toIterable)

  def get(key: K): IO[scala.Throwable, Optional[V]] =
    asScala.get(key).map(_.asJava)

  def getKey(key: K): IO[scala.Throwable, Optional[K]] =
    asScala.getKey(key).map(_.asJava)

  def getKeyValue(key: K): IO[scala.Throwable, Optional[KeyVal[K, V]]] =
    asScala.getKeyValue(key).map(_.map(KeyVal(_)).asJava)

  def contains(key: K): IO[scala.Throwable, Boolean] =
    asScala.contains(key)

  def mightContain(key: K): IO[scala.Throwable, Boolean] =
    asScala.mightContain(key)

  def mightContainFunction(functionId: K): IO[scala.Throwable, Boolean] =
    asScala.mightContainFunction(functionId)

  def keys =
    SetIO(asScala.keys)

  def level0Meter: LevelZeroMeter =
    asScala.levelZeroMeter

  def levelMeter(levelNumber: Int): Optional[LevelMeter] =
    asScala.levelMeter(levelNumber).asJava

  def sizeOfSegments: Long =
    asScala.sizeOfSegments

  def keySize(key: K): Int =
    asScala.keySize(key)

  def valueSize(value: V): Int =
    asScala.valueSize(value)

  def timeLeft(key: K): IO[scala.Throwable, Optional[java.time.Duration]] =
    new IO(
      asScala.timeLeft(key).map {
        case Some(duration) =>
          Optional.of(duration.toJava)

        case None =>
          Optional.empty()
      }
    )

  def from(key: K): MapIO[K, V, F] =
    copy(asScala.from(key))

  def before(key: K): MapIO[K, V, F] =
    copy(asScala.before(key))

  def fromOrBefore(key: K): MapIO[K, V, F] =
    copy(asScala.fromOrBefore(key))

  def after(key: K): MapIO[K, V, F] =
    copy(asScala.after(key))

  def fromOrAfter(key: K): MapIO[K, V, F] =
    copy(asScala.fromOrAfter(key))

  def headOptional: IO[scala.Throwable, Optional[KeyVal[K, V]]] =
    asScala.headOption.map(_.map(KeyVal(_)).asJava)

  def drop(count: Int): StreamIO[KeyVal[K, V]] =
    new StreamIO(asScala.drop(count).map(_.asKeyVal))

  def dropWhile(function: Predicate[KeyVal[K, V]]): StreamIO[KeyVal[K, V]] =
    Stream.fromScala(
      asScala
        .map(_.asKeyVal)
        .dropWhile(function.test)
    )

  def take(count: Int): StreamIO[KeyVal[K, V]] =
    Stream.fromScala(asScala.take(count).map(_.asKeyVal))

  def takeWhile(function: Predicate[KeyVal[K, V]]): StreamIO[KeyVal[K, V]] =
    Stream.fromScala(
      asScala
        .map(_.asKeyVal)
        .takeWhile(function.test)
    )

  def map[B](function: JavaFunction[KeyVal[K, V], B]): StreamIO[B] =
    Stream.fromScala(
      asScala map {
        case (key: K, value: V) =>
          function.apply(KeyVal(key, value))
      }
    )

  def flatMap[B](function: JavaFunction[KeyVal[K, V], StreamIO[B]]): StreamIO[B] =
    Stream.fromScala(
      asScala.flatMap {
        case (key: K, value: V) =>
          function.apply(KeyVal(key, value)).asScala
      }
    )

  def forEach(function: Consumer[KeyVal[K, V]]): StreamIO[Unit] =
    Stream.fromScala(
      asScala foreach {
        case (key: K, value: V) =>
          function.accept(KeyVal(key, value))
      }
    )

  def filter(function: Predicate[KeyVal[K, V]]): StreamIO[KeyVal[K, V]] =
    Stream.fromScala(
      asScala
        .map(_.asKeyVal)
        .filter(function.test)
    )

  def filterNot(function: Predicate[KeyVal[K, V]]): StreamIO[KeyVal[K, V]] =
    Stream.fromScala(
      asScala
        .map(_.asKeyVal)
        .filterNot(function.test)
    )

  def foldLeft[B](initial: B)(function: BiFunction[B, KeyVal[K, V], B]): IO[scala.Throwable, B] =
    stream.foldLeft(initial, function)

  def size: IO[scala.Throwable, Int] =
    asScala.size

  def stream: StreamIO[KeyVal[K, V]] =
    new StreamIO(asScala.stream.map(_.asKeyVal))

  def sizeOfBloomFilterEntries: IO[scala.Throwable, Int] =
    asScala.sizeOfBloomFilterEntries

  def isEmpty: IO[scala.Throwable, Boolean] =
    asScala.isEmpty

  def nonEmpty: IO[scala.Throwable, Boolean] =
    asScala.nonEmpty

  def lastOptional: IO[scala.Throwable, Optional[KeyVal[K, V]]] =
    asScala.lastOption.map(_.map(KeyVal(_)).asJava)

  def reverse: MapIO[K, V, F] =
    copy(asScala.reverse)

  def close(): IO[scala.Throwable, Unit] =
    asScala.close()

  def delete(): IO[scala.Throwable, Unit] =
    asScala.delete()

  override def toString(): String =
    classOf[MapIO[_, _, _]].getClass.getSimpleName
}
