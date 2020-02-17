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
 */

package swaydb

import swaydb.data.accelerate.LevelZeroMeter
import swaydb.data.compaction.LevelMeter

import scala.concurrent.duration.{Deadline, FiniteDuration}

/**
 * A [[MapSet]] is simply a wrapper around [[Set]] to provide
 * [[Map]] like API on [[Set]] storage format.
 */
case class MapSet[K, V, F, BAG[_]](set: Set[(K, V), F, BAG])(implicit bag: Bag[BAG]) extends SwayMap[K, V, F, BAG] { self =>

  private final val nullValue: V = nullValue

  def put(key: K, value: V): BAG[OK] =
    set.add((key, value))

  def put(key: K, value: V, expireAfter: FiniteDuration): BAG[OK] =
    set.add((key, value), expireAfter.fromNow)

  def put(key: K, value: V, expireAt: Deadline): BAG[OK] =
    set.add((key, value), expireAt)

  def put(keyValues: (K, V)*): BAG[OK] =
    put(keyValues)

  def put(keyValues: Stream[(K, V)]): BAG[OK] =
    set.add(keyValues)

  def put(keyValues: Iterable[(K, V)]): BAG[OK] =
    set.add(keyValues)

  def put(keyValues: Iterator[(K, V)]): BAG[OK] =
    set.add(keyValues)

  def remove(key: K): BAG[OK] =
    set.remove((key, nullValue))

  def remove(from: K, to: K): BAG[OK] =
    set.remove((from, nullValue), (to, nullValue))

  def remove(keys: K*): BAG[OK] =
    set.remove(keys.map(key => (key, nullValue)))

  def remove(keys: Stream[K]): BAG[OK] =
    set.remove(keys.map((_, nullValue)))

  def remove(keys: Iterable[K]): BAG[OK] =
    remove(keys.iterator)

  def remove(keys: Iterator[K]): BAG[OK] =
    set.remove(keys.map((_, nullValue)))

  def expire(key: K, after: FiniteDuration): BAG[OK] =
    set.expire((key, nullValue), after)

  def expire(key: K, at: Deadline): BAG[OK] =
    set.expire((key, nullValue), at)

  def expire(from: K, to: K, after: FiniteDuration): BAG[OK] =
    set.expire((from, nullValue), (to, nullValue), after.fromNow)

  def expire(from: K, to: K, at: Deadline): BAG[OK] =
    set.expire((from, nullValue), (to, nullValue), at)

  def expire(keys: (K, Deadline)*): BAG[OK] =
    expire(keys)

  def expire(keys: Stream[(K, Deadline)]): BAG[OK] =
    set.expire {
      keys.map {
        case (key, deadline) =>
          ((key, nullValue), deadline)
      }
    }

  def expire(keys: Iterable[(K, Deadline)]): BAG[OK] =
    expire(keys.iterator)

  def expire(keys: Iterator[(K, Deadline)]): BAG[OK] =
    set.expire {
      keys.map {
        case (key, deadline) =>
          ((key, nullValue), deadline)
      }
    }

  def clear(): BAG[OK] =
    set.clear()

  def registerFunction[PF <: F](function: PF)(implicit ev: PF <:< swaydb.PureFunction.OnKey[(K, V), Nothing, Apply.Set[Nothing]]): BAG[OK] =
    set.registerFunction(function)

  def applyFunction[PF <: F](from: K, to: K, function: PF)(implicit ev: PF <:< swaydb.PureFunction.OnKey[(K, V), Nothing, Apply.Set[Nothing]]): BAG[OK] =
    set.applyFunction(from = (from, nullValue), to = (to, nullValue), function = function)

  def applyFunction[PF <: F](key: K, function: PF)(implicit ev: PF <:< swaydb.PureFunction.OnKey[(K, V), Nothing, Apply.Set[Nothing]]): BAG[OK] =
    set.applyFunction((key, nullValue), function)

  def commit[PF <: F](prepare: Prepare[(K, V), Nothing, PF]*)(implicit ev: PF <:< swaydb.PureFunction.OnKey[(K, V), Nothing, Apply.Set[Nothing]]): BAG[OK] =
    set.commit(prepare)

  def commit[PF <: F](prepare: Stream[Prepare[(K, V), Nothing, PF]])(implicit ev: PF <:< swaydb.PureFunction.OnKey[(K, V), Nothing, Apply.Set[Nothing]]): BAG[OK] =
    set.commit(prepare)

  def commit[PF <: F](prepare: Iterable[Prepare[(K, V), Nothing, PF]])(implicit ev: PF <:< swaydb.PureFunction.OnKey[(K, V), Nothing, Apply.Set[Nothing]]): BAG[OK] =
    set.commit(prepare)

  /**
   * Returns target value for the input key.
   */
  def get(key: K): BAG[Option[V]] =
    bag.map(set.get((key, nullValue)))(_.map(_._2))

  /**
   * Returns target full key for the input partial key.
   *
   * This function is mostly used for Set databases where partial ordering on the Key is provided.
   */
  def getKey(key: K): BAG[Option[K]] =
    bag.map(set.get((key, nullValue)))(_.map(_._1))

  def getKeyValue(key: K): BAG[Option[(K, V)]] =
    set.get((key, nullValue))

  def contains(key: K): BAG[Boolean] =
    set.contains((key, nullValue))

  def mightContain(key: K): BAG[Boolean] =
    set.mightContain((key, nullValue))

  def mightContainFunction(functionId: K): BAG[Boolean] =
    set.mightContainFunction((functionId, nullValue))

  def levelZeroMeter: LevelZeroMeter =
    set.levelZeroMeter

  def levelMeter(levelNumber: Int): Option[LevelMeter] =
    set.levelMeter(levelNumber)

  def sizeOfSegments: Long =
    set.sizeOfSegments

  def expiration(key: K): BAG[Option[Deadline]] =
    set.expiration((key, nullValue))

  def timeLeft(key: K): BAG[Option[FiniteDuration]] =
    bag.map(expiration(key))(_.map(_.timeLeft))

  def from(key: K): MapSet[K, V, F, BAG] =
    copy(set = set.from((key, nullValue)))

  def before(key: K): MapSet[K, V, F, BAG] =
    copy(set = set.before((key, nullValue)))

  def fromOrBefore(key: K): MapSet[K, V, F, BAG] =
    copy(set = set.fromOrBefore((key, nullValue)))

  def after(key: K): MapSet[K, V, F, BAG] =
    copy(set = set.after((key, nullValue)))

  def fromOrAfter(key: K): MapSet[K, V, F, BAG] =
    copy(set = set.fromOrAfter((key, nullValue)))

  def headOption: BAG[Option[(K, V)]] =
    set.headOption

  def stream: Stream[(K, V)] =
    set.stream

  def iterator[BAG[_]](implicit bag: Bag.Sync[BAG]): Iterator[BAG[(K, V)]] =
    set.iterator

  def sizeOfBloomFilterEntries: BAG[Int] =
    set.sizeOfBloomFilterEntries

  def isEmpty: BAG[Boolean] =
    set.isEmpty

  def nonEmpty: BAG[Boolean] =
    set.nonEmpty

  def lastOption: BAG[Option[(K, V)]] =
    set.lastOption

  def reverse: MapSet[K, V, F, BAG] =
    copy(set.reverse)

  /**
   * Returns an Async API of type O where the [[Bag]] is known.
   */
  def toBag[X[_]](implicit bag: Bag[X]): MapSet[K, V, F, X] =
    copy(set = set.toBag[X])

  def close(): BAG[Unit] =
    set.close()

  def delete(): BAG[Unit] =
    set.delete()

  override def toString(): String =
    classOf[MapSet[_, _, _, BAG]].getClass.getSimpleName

}