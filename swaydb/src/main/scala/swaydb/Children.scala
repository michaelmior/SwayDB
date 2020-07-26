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
 * If you modify this Program or any covered work, only by linking or
 * combining it with separate works, the licensors of this Program grant
 * you additional permission to convey the resulting work.
 */

package swaydb

import swaydb.MultiMapKey.{MapEnd, MapStart, SubMap}
import swaydb.core.util.Times._
import swaydb.serializers._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{Deadline, FiniteDuration}

/**
 * Provides APIs to manage children/nested maps/child maps of [[MultiMap]].
 */
class Children[T, K, V, F, BAG[_]](map: Map[MultiMapKey[T, K], Option[V], PureFunction[MultiMapKey[T, K], Option[V], Apply.Map[Option[V]]], BAG],
                                   mapKey: Iterable[T],
                                   defaultExpiration: Option[Deadline])(implicit keySerializer: Serializer[K],
                                                                        tableSerializer: Serializer[T],
                                                                        valueSerializer: Serializer[V],
                                                                        bag: Bag[BAG]) {

  /**
   * Creates new or initialises the existing map.
   */
  def init[T2](key: T2)(implicit evT: T2 <:< T): BAG[MultiMap[T2, K, V, F, BAG]] =
    getOrPut(key = key, expireAt = None, forceClear = false)

  def init[T2, K2](key: T2, keyType: Class[K2])(implicit evT: T2 <:< T,
                                                evK: K2 <:< K): BAG[MultiMap[T2, K2, V, F, BAG]] =
    getOrPut(key = key, expireAt = None, forceClear = false)

  def init[T2, K2, V2](key: T2, keyType: Class[K2], valueType: Class[V2])(implicit evT: T2 <:< T,
                                                                          evK: K2 <:< K,
                                                                          evV: V2 <:< V): BAG[MultiMap[T2, K2, V2, F, BAG]] =
    getOrPut(key = key, expireAt = None, forceClear = false)

  def init[T2, K2, V2, F2](key: T2, keyType: Class[K2], valueType: Class[V2], functionType: Class[F2])(implicit evT: T2 <:< T,
                                                                                                       evK: K2 <:< K,
                                                                                                       evV: V2 <:< V,
                                                                                                       evF: F2 <:< F): BAG[MultiMap[T2, K2, V2, F2, BAG]] =
    getOrPut(key = key, expireAt = None, forceClear = false)

  /**
   * Clears existing entries before creating the Map.
   *
   * @note Put has slower immediate write performance for preceding key-value entries.
   *       Always use [[init]] if clearing existing entries is not required.
   */
  def replace[T2](key: T2)(implicit evT: T2 <:< T): BAG[MultiMap[T2, K, V, F, BAG]] =
    getOrPut(key, None, forceClear = true)

  def replace[T2, K2](key: T2, keyType: Class[K2])(implicit evT: T2 <:< T,
                                                   evK: K2 <:< K): BAG[MultiMap[T2, K2, V, F, BAG]] =
    getOrPut(key, None, forceClear = true)

  def replace[T2, K2, V2](key: T2, keyType: Class[K2], valueType: Class[V2])(implicit evT: T2 <:< T,
                                                                             evK: K2 <:< K,
                                                                             evV: V2 <:< V): BAG[MultiMap[T2, K2, V2, F, BAG]] =
    getOrPut(key, None, forceClear = true)

  def replace[T2, K2, V2, F2](key: T2, keyType: Class[K2], valueType: Class[V2], functionType: Class[F2])(implicit evT: T2 <:< T,
                                                                                                          evK: K2 <:< K,
                                                                                                          evV: V2 <:< V,
                                                                                                          evF: F2 <:< F): BAG[MultiMap[T2, K2, V2, F2, BAG]] =
    getOrPut(key, None, forceClear = true)

  /**
   * Creates new or initialises the existing map.
   */
  def init[T2](key: T2, expireAfter: FiniteDuration)(implicit evT: T2 <:< T): BAG[MultiMap[T2, K, V, F, BAG]] =
    getOrPut(key, Some(expireAfter.fromNow), forceClear = false)

  def init[T2, K2](key: T2, expireAfter: FiniteDuration, keyType: Class[K2])(implicit evT: T2 <:< T,
                                                                             evK: K2 <:< K): BAG[MultiMap[T2, K2, V, F, BAG]] =
    getOrPut(key, Some(expireAfter.fromNow), forceClear = false)

  def init[T2, K2, V2](key: T2, expireAfter: FiniteDuration, keyType: Class[K2], valueType: Class[V2])(implicit evT: T2 <:< T,
                                                                                                       evK: K2 <:< K,
                                                                                                       evV: V2 <:< V): BAG[MultiMap[T2, K2, V2, F, BAG]] =
    getOrPut(key, Some(expireAfter.fromNow), forceClear = false)

  def init[T2, K2, V2, F2](key: T2, expireAfter: FiniteDuration, keyType: Class[K2], valueType: Class[V2], functionType: Class[F2])(implicit evT: T2 <:< T,
                                                                                                                                    evK: K2 <:< K,
                                                                                                                                    evV: V2 <:< V,
                                                                                                                                    evF: F2 <:< F): BAG[MultiMap[T2, K2, V2, F2, BAG]] =
    getOrPut(key, Some(expireAfter.fromNow), forceClear = false)

  /**
   * Clears existing entries before creating the Map.
   *
   * @note Put has slower immediate write performance for preceding key-value entries.
   *       Always use [[init]] if clearing existing entries is not required.
   */
  def replace[T2](key: T2, expireAfter: FiniteDuration)(implicit evT: T2 <:< T): BAG[MultiMap[T2, K, V, F, BAG]] =
    getOrPut(key, Some(expireAfter.fromNow), forceClear = true)

  def replace[T2, K2](key: T2, expireAfter: FiniteDuration, keyType: Class[K2])(implicit evT: T2 <:< T,
                                                                                evK: K2 <:< K): BAG[MultiMap[T2, K2, V, F, BAG]] =
    getOrPut(key, Some(expireAfter.fromNow), forceClear = true)

  def replace[T2, K2, V2](key: T2, expireAfter: FiniteDuration, keyType: Class[K2], valueType: Class[V2])(implicit evT: T2 <:< T,
                                                                                                          evK: K2 <:< K,
                                                                                                          evV: V2 <:< V): BAG[MultiMap[T2, K2, V2, F, BAG]] =
    getOrPut(key, Some(expireAfter.fromNow), forceClear = true)

  def replace[T2, K2, V2, F2](key: T2, expireAfter: FiniteDuration, keyType: Class[K2], valueType: Class[V2], functionType: Class[F2])(implicit evT: T2 <:< T,
                                                                                                                                       evK: K2 <:< K,
                                                                                                                                       evV: V2 <:< V,
                                                                                                                                       evF: F2 <:< F): BAG[MultiMap[T2, K2, V2, F2, BAG]] =
    getOrPut(key, Some(expireAfter.fromNow), forceClear = true)

  /**
   * Creates new or initialises the existing map.
   */
  def init[T2](key: T2, expireAt: Deadline)(implicit evT: T2 <:< T): BAG[MultiMap[T2, K, V, F, BAG]] =
    getOrPut(key, Some(expireAt), forceClear = false)


  def init[T2, K2](key: T2, expireAt: Deadline, keyType: Class[K2])(implicit evT: T2 <:< T,
                                                                    evK: K2 <:< K): BAG[MultiMap[T2, K2, V, F, BAG]] =
    getOrPut(key, Some(expireAt), forceClear = false)

  def init[T2, K2, V2](key: T2, expireAt: Deadline, keyType: Class[K2], valueType: Class[V2])(implicit evT: T2 <:< T,
                                                                                              evK: K2 <:< K,
                                                                                              evV: V2 <:< V): BAG[MultiMap[T2, K2, V2, F, BAG]] =
    getOrPut(key, Some(expireAt), forceClear = false)

  def init[T2, K2, V2, F2](key: T2, expireAt: Deadline, keyType: Class[K2], valueType: Class[V2], functionType: Class[F2])(implicit evT: T2 <:< T,
                                                                                                                           evK: K2 <:< K,
                                                                                                                           evV: V2 <:< V,
                                                                                                                           evF: F2 <:< F): BAG[MultiMap[T2, K2, V2, F2, BAG]] =
    getOrPut(key, Some(expireAt), forceClear = false)

  /**
   * Clears existing entries before creating the Map.
   *
   * @note Put has slower immediate write performance for preceding key-value entries.
   *       Always use [[init]] if clearing existing entries is not required.
   */
  def replace[T2](key: T2, expireAt: Deadline)(implicit evT: T2 <:< T): BAG[MultiMap[T2, K, V, F, BAG]] =
    getOrPut(key = key, expireAt = Some(expireAt), forceClear = true)

  def replace[T2, K2](key: T2, expireAt: Deadline, keyType: Class[K2])(implicit evT: T2 <:< T,
                                                                       evK: K2 <:< K): BAG[MultiMap[T2, K2, V, F, BAG]] =
    getOrPut(key = key, expireAt = Some(expireAt), forceClear = true)

  def replace[T2, K2, V2](key: T2, expireAt: Deadline, keyType: Class[K2], valueType: Class[V2])(implicit evT: T2 <:< T,
                                                                                                 evK: K2 <:< K,
                                                                                                 evV: V2 <:< V): BAG[MultiMap[T2, K2, V2, F, BAG]] =
    getOrPut(key = key, expireAt = Some(expireAt), forceClear = true)

  def replace[T2, K2, V2, F2](key: T2, expireAt: Deadline, keyType: Class[K2], valueType: Class[V2], functionType: Class[F2])(implicit evT: T2 <:< T,
                                                                                                                              evK: K2 <:< K,
                                                                                                                              evV: V2 <:< V,
                                                                                                                              evF: F2 <:< F): BAG[MultiMap[T2, K2, V2, F2, BAG]] =
    getOrPut(key = key, expireAt = Some(expireAt), forceClear = true)

  /**
   * Clears existing entries before creating the Map.
   *
   * @note Put has slower immediate write performance for preceding key-value entries.
   *       Always use [[init]] if clearing existing entries is not required.
   */

  def replace[T2](key: T2, expireAt: Option[Deadline])(implicit evT: T2 <:< T): BAG[MultiMap[T2, K, V, F, BAG]] =
    getOrPut(key = key, expireAt = expireAt, forceClear = true)

  def replace[T2, K2](key: T2, expireAt: Option[Deadline], keyType: Class[K2])(implicit evT: T2 <:< T,
                                                                               evK: K2 <:< K): BAG[MultiMap[T2, K2, V, F, BAG]] =
    getOrPut(key = key, expireAt = expireAt, forceClear = true)

  def replace[T2, K2, V2](key: T2, expireAt: Option[Deadline], keyType: Class[K2], valueType: Class[V2])(implicit evT: T2 <:< T,
                                                                                                         evK: K2 <:< K,
                                                                                                         evV: V2 <:< V): BAG[MultiMap[T2, K2, V2, F, BAG]] =
    getOrPut(key = key, expireAt = expireAt, forceClear = true)

  def replace[T2, K2, V2, F2](key: T2, expireAt: Option[Deadline], keyType: Class[K2], valueType: Class[V2], functionType: Class[F2])(implicit evT: T2 <:< T,
                                                                                                                                      evK: K2 <:< K,
                                                                                                                                      evV: V2 <:< V,
                                                                                                                                      evF: F2 <:< F): BAG[MultiMap[T2, K2, V2, F2, BAG]] =
    getOrPut(key = key, expireAt = expireAt, forceClear = true)


  /**
   * Inserts a child map to this [[MultiMap]].
   *
   * @param key        key assign to the child
   * @param expireAt   expiration
   * @param forceClear if true, removes all existing entries before initialising the child Map.
   *                   Clear uses a range entry to clear existing key-values and inserts to [[Range]]
   *                   in [[swaydb.core.level.zero.LevelZero]]'s [[Map]] entries can be slower because it
   *                   requires skipList to be cloned on each insert. As the compaction progresses the
   *                   range entries will get applied the performance goes back to normal. But try to avoid
   *                   using clear.
   */
  private def getOrPut[T2, K2, V2, F2](key: T2, expireAt: Option[Deadline], forceClear: Boolean)(implicit evT: T2 <:< T,
                                                                                                 evK: K2 <:< K,
                                                                                                 evV: V2 <:< V,
                                                                                                 evF: F2 <:< F): BAG[MultiMap[T2, K2, V2, F2, BAG]] =
    bag.flatMap(get(key)) {
      case Some(_map) =>
        val map = _map.asInstanceOf[MultiMap[T2, K2, V2, F2, BAG]]

        if (forceClear)
          create(key = key, expireAt = expireAt, forceClear = forceClear, expire = false)
        else
          expireAt match {
            case Some(updatedExpiration) =>
              val newExpiration = defaultExpiration earlier updatedExpiration
              //if the expiration is not updated return the map.
              if (defaultExpiration contains newExpiration)
                bag.success(map)
              else // expiration is updated perform create.
                create(key = key, expireAt = Some(newExpiration), forceClear = false, expire = true)

            case None =>
              bag.success(map)
          }

      case None =>
        create(key = key, expireAt = expireAt, forceClear = false, expire = false)
    }

  /**
   * Flatten all nest children of this map.
   *
   * Requires a [[Bag.Sync]] instead of [[Bag.Async]].
   */
  def flatten[BAG[_]](implicit bag: Bag.Sync[BAG]): BAG[ListBuffer[MultiMap[T, K, V, F, BAG]]] =
    stream(bag).foldLeft(ListBuffer[MultiMap[T, K, V, F, BAG]]()) {
      case (buffer, childBag) =>
        val child = bag.getUnsafe(childBag)

        child foreach {
          child =>
            buffer += child
            val children = bag.getUnsafe(child.children.flatten[BAG])
            buffer ++= children
        }

        buffer
    }


  private def create[T2, K2, V2, F2](key: T2, expireAt: Option[Deadline], forceClear: Boolean, expire: Boolean)(implicit evT: T2 <:< T,
                                                                                                                evK: K2 <:< K,
                                                                                                                evV: V2 <:< V,
                                                                                                                evF: F2 <:< F): BAG[MultiMap[T2, K2, V2, F2, BAG]] = {
    val childMapKey: Iterable[T] = mapKey.toBuffer += key

    val expiration = expireAt earlier defaultExpiration

    val buffer = prepareRemove(expiration = expiration, forceClear = forceClear, expire = expire)

    bag.flatMap(buffer) {
      buffer =>
        buffer += Prepare.Put(MultiMapKey.SubMap[T](mapKey, key), None, expiration)
        buffer += Prepare.Put(MultiMapKey.MapStart[T](childMapKey), None, expiration)
        buffer += Prepare.Put(MultiMapKey.MapEntriesStart[T](childMapKey), None, expiration)
        buffer += Prepare.Put(MultiMapKey.MapEntriesEnd[T](childMapKey), None, expiration)
        buffer += Prepare.Put(MultiMapKey.SubMapsStart[T](childMapKey), None, expiration)
        buffer += Prepare.Put(MultiMapKey.SubMapsEnd[T](childMapKey), None, expiration)
        buffer += Prepare.Put(MultiMapKey.MapEnd[T](childMapKey), None, expiration)

        bag.transform(map.commit(buffer)) {
          _ =>
            MultiMap(
              map = map,
              mapKey = childMapKey,
              defaultExpiration = expiration
            ).asInstanceOf[MultiMap[T2, K2, V2, F2, BAG]]
        }
    }
  }

  /**
   * Returns a list of [[Prepare.Remove]] statements.
   *
   * @param expiration default expiration to set
   * @param forceClear remove the map
   * @param expire     updates the expiration only. If forceClear is true then this is ignored.
   * @return a list of [[Prepare.Remove]] statements.
   */
  private def prepareRemove(expiration: Option[Deadline],
                            forceClear: Boolean,
                            expire: Boolean): BAG[ListBuffer[Prepare[MultiMapKey[T, K], Option[V], Nothing]]] = {
    val buffer = ListBuffer.empty[Prepare[MultiMapKey[T, K], Option[V], Nothing]]

    //todo - use the map level BAG instead synchronous IO.ApiIO.
    if (forceClear || expire) {
      //ignore expiry if forceClear is set to true. ForceClear should remove instead of just setting a new expiry.
      val prepareRemoveExpiry =
        if (!forceClear && expire)
          expiration
        else
          None

      prepareRemove[IO.ApiIO](prepareRemoveExpiry) match {
        case IO.Right(removes) =>
          buffer ++= removes
          bag.success(buffer)

        case IO.Left(value) =>
          bag.failure(value.exception)
      }
    }
    else
      bag.success(buffer)
  }

  def remove(key: T): BAG[Boolean] =
    bag.flatMap(prepareRemove(key)) {
      buffer =>
        if (buffer.isEmpty)
          bag.success(false)
        else
          bag.transform(map.commit(buffer)) {
            _ =>
              true
          }
    }

  /**
   * Builds [[Prepare.Remove]] statements to remove the key's map and all that key's children.
   */
  private def prepareRemove(key: T): BAG[ListBuffer[Prepare[MultiMapKey[T, K], Option[V], Nothing]]] =
    bag.flatMap(get(key)) {
      case Some(child) =>
        val buffer = child.children.prepareRemove(expiration = None, forceClear = true, expire = false)

        bag.transform(buffer) {
          buffer =>
            buffer ++= prepareRemoveSubMap(key, None)
        }

      case None =>
        bag.success(ListBuffer.empty)
    }

  /**
   * Builds [[Prepare.Remove]] statements for a child with the key.
   */
  private def prepareRemoveSubMap(key: T, expire: Option[Deadline]): Seq[Prepare.Remove[MultiMapKey[T, K]]] = {
    val childMapKey = mapKey.toBuffer += key

    Seq(
      Prepare.Remove(SubMap(mapKey, key), None, expire),
      Prepare.Remove(MapStart(childMapKey), Some(MapEnd(childMapKey)), expire)
    )
  }

  /**
   * Builds [[Prepare.Remove]] statements for all children of this map.
   */
  private def prepareRemove[BAG[_]](expire: Option[Deadline])(implicit bag: Bag.Sync[BAG]): BAG[ListBuffer[Prepare.Remove[MultiMapKey[T, K]]]] =
    stream(bag).foldLeft(ListBuffer.empty[Prepare.Remove[MultiMapKey[T, K]]]) {
      case (buffer, childBag) =>
        val child = bag.getUnsafe(childBag)

        child foreach {
          child =>
            buffer ++= prepareRemoveSubMap(child.mapKey.last, expire)
            val childPrepares = bag.getUnsafe(child.children.prepareRemove(expire))
            buffer ++= childPrepares
        }

        buffer
    }

  /**
   * Returns the child Map
   */

  def get[T2](key: T2)(implicit evT: T2 <:< T): BAG[Option[MultiMap[T2, K, V, F, BAG]]] =
    get(key, bag)

  def get[T2, K2](key: T2, keyType: Class[K2])(implicit evT: T2 <:< T,
                                               evK: K2 <:< K): BAG[Option[MultiMap[T2, K2, V, F, BAG]]] =
    get(key, bag)

  def get[T2, K2, V2](key: T2, keyType: Class[K2], valueType: Class[V2])(implicit evT: T2 <:< T,
                                                                         evK: K2 <:< K,
                                                                         evV: V2 <:< V): BAG[Option[MultiMap[T2, K2, V2, F, BAG]]] =
    get(key, bag)

  def get[T2, K2, V2, F2](key: T2, keyType: Class[K2], valueType: Class[V2], functionType: Class[F2])(implicit evT: T2 <:< T,
                                                                                                      evK: K2 <:< K,
                                                                                                      evV: V2 <:< V,
                                                                                                      evF: F2 <:< F): BAG[Option[MultiMap[T2, K2, V2, F2, BAG]]] =
    get(key, bag)

  private def get[T2, K2, V2, F2, BAG[_]](key: T2, bag: Bag[BAG])(implicit evT: T2 <:< T,
                                                                  evK: K2 <:< K,
                                                                  evV: V2 <:< V,
                                                                  evF: F2 <:< F): BAG[Option[MultiMap[T2, K2, V2, F2, BAG]]] = {
    val mapPrefix: Iterable[T] = mapKey.toBuffer += key
    implicit val b: Bag[BAG] = bag

    bag.map(map.getKeyDeadline(MapStart(mapPrefix), bag)) {
      case Some((_, deadline)) =>
        Some(
          MultiMap[T, K, V, F, BAG](
            map = map.toBag[BAG],
            mapKey = mapPrefix,
            defaultExpiration = deadline
          ).asInstanceOf[MultiMap[T2, K2, V2, F2, BAG]]
        )

      case None =>
        None
    }
  }

  /**
   * Keys of all child Maps.
   */
  def keys: Stream[T] =
    map
      .after(MultiMapKey.SubMapsStart(mapKey))
      .keys
      .stream
      .takeWhile {
        case MultiMapKey.SubMap(parentKey, _) =>
          parentKey == mapKey

        case _ =>
          false
      }
      .collect {
        case MultiMapKey.SubMap(_, dataKey) =>
          dataKey
      }

  //todo - flatten Options and BAG.
  def stream: Stream[BAG[Option[MultiMap[T, K, V, F, BAG]]]] =
    keys.map(key => get(key))

  private def stream[BAG[_]](bag: Bag[BAG]): Stream[BAG[Option[MultiMap[T, K, V, F, BAG]]]] =
    keys.map(key => get(key, bag))

  def isEmpty: BAG[Boolean] =
    bag.transform(keys.headOrNull) {
      head =>
        head == null
    }

  def nonEmpty: BAG[Boolean] =
    bag.transform(isEmpty)(!_)
}