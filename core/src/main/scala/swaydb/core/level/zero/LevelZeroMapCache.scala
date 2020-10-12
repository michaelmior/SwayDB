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
 * If you modify this Program or any covered work, only by linking or combining
 * it with separate works, the licensors of this Program grant you additional
 * permission to convey the resulting work.
 */

package swaydb.core.level.zero

import swaydb.core.data.{Memory, MemoryOption}
import swaydb.core.function.FunctionStore
import swaydb.core.map.{MapCache, MapCacheBuilder, MapEntry}
import swaydb.core.merge.FixedMerger
import swaydb.core.segment.merge.{MergeStats, SegmentMerger}
import swaydb.core.util.skiplist.{SkipList, SkipListConcurrent, SkipListSeries}
import swaydb.data.{Atomic, OptimiseWrites}
import swaydb.data.order.{KeyOrder, TimeOrder}
import swaydb.data.slice.{Slice, SliceOption}
import swaydb.{Aggregator, Bag}

import scala.beans.BeanProperty
import scala.collection.mutable.ListBuffer

private[core] object LevelZeroMapCache {

  implicit def builder(implicit keyOrder: KeyOrder[Slice[Byte]],
                       timeOrder: TimeOrder[Slice[Byte]],
                       functionStore: FunctionStore,
                       optimiseWrites: OptimiseWrites,
                       atomic: Atomic): MapCacheBuilder[LevelZeroMapCache] =
    () => LevelZeroMapCache()

  object State {
    @inline def apply()(implicit keyOrder: KeyOrder[Slice[Byte]],
                        optimiseWrites: OptimiseWrites): State =
      new State(
        skipList = newSkipList(),
        hasRange = false,
        mutable = true
      )
  }

  class State(val skipList: SkipList[SliceOption[Byte], MemoryOption, Slice[Byte], Memory],
              @BeanProperty @volatile var hasRange: Boolean,
              @BeanProperty @volatile var mutable: Boolean)


  @inline def apply()(implicit keyOrder: KeyOrder[Slice[Byte]],
                      timeOrder: TimeOrder[Slice[Byte]],
                      functionStore: FunctionStore,
                      optimiseWrites: OptimiseWrites,
                      atomic: Atomic): LevelZeroMapCache =
    new LevelZeroMapCache(State())

  private[zero] def newSkipList()(implicit keyOrder: KeyOrder[Slice[Byte]],
                                  optimiseWrites: OptimiseWrites): SkipList[SliceOption[Byte], MemoryOption, Slice[Byte], Memory] =
    optimiseWrites match {
      case OptimiseWrites.RandomOrder =>
        SkipListConcurrent[SliceOption[Byte], MemoryOption, Slice[Byte], Memory](
          nullKey = Slice.Null,
          nullValue = Memory.Null
        )

      case OptimiseWrites.SequentialOrder(initialSkipListLength) =>
        SkipListSeries[SliceOption[Byte], MemoryOption, Slice[Byte], Memory](
          lengthPerSeries = initialSkipListLength,
          nullKey = Slice.Null,
          nullValue = Memory.Null
        )
    }

  @inline def insert(insert: Memory,
                     state: State)(implicit keyOrder: KeyOrder[Slice[Byte]],
                                   timeOrder: TimeOrder[Slice[Byte]],
                                   functionStore: FunctionStore): Unit =
    insert match {
      //if insert value is fixed, check the floor entry
      case insertValue: Memory.Fixed =>
        LevelZeroMapCache.insert(insert = insertValue, state = state)

      //slice the skip list to keep on the range's key-values.
      //if the insert is a Range stash the edge non-overlapping key-values and keep only the ranges in the skipList
      //that fall within the inserted range before submitting fixed values to the range for further splits.
      case insertRange: Memory.Range =>
        LevelZeroMapCache.insert(insert = insertRange, state = state)
    }

  /**
   * Inserts a [[Memory.Fixed]] key-value into skipList.
   */
  def insert(insert: Memory.Fixed,
             state: State)(implicit keyOrder: KeyOrder[Slice[Byte]],
                           timeOrder: TimeOrder[Slice[Byte]],
                           functionStore: FunctionStore): Unit =
    state.skipList.floor(insert.key) match {
      case floorEntry: Memory =>
        import keyOrder._

        floorEntry match {
          //if floor entry for input Fixed entry & if they keys match, do applyValue else simply add the new key-value.
          case floor: Memory.Fixed if floor.key equiv insert.key =>
            val mergedKeyValue =
              FixedMerger(
                newKeyValue = insert,
                oldKeyValue = floor
              ).asInstanceOf[Memory.Fixed]

            state.skipList.put(insert.key, mergedKeyValue)

          //if the floor entry is a range try to do a merge.
          case floorRange: Memory.Range if insert.key < floorRange.toKey =>

            val builder = MergeStats.buffer[Memory, ListBuffer](Aggregator.listBuffer)

            SegmentMerger.merge(
              newKeyValue = insert,
              oldKeyValue = floorRange,
              builder = builder,
              isLastLevel = false
            )

            val mergedKeyValues = builder.keyValues

            mergedKeyValues foreach {
              merged: Memory =>
                if (merged.isRange) state.setHasRange(true)
                state.skipList.put(merged.key, merged)
            }

          case _ =>
            state.skipList.put(insert.key, insert)
        }

      //if there is no floor, simply put.
      case Memory.Null =>
        state.skipList.put(insert.key, insert)
    }

  /**
   * Inserts the input [[Memory.Range]] key-value into skipList and always maintaining the previous state of
   * the skipList before applying the new state so that all read queries read the latest write.
   */
  def insert(insert: Memory.Range,
             state: State)(implicit keyOrder: KeyOrder[Slice[Byte]],
                           timeOrder: TimeOrder[Slice[Byte]],
                           functionStore: FunctionStore) = {
    import keyOrder._

    //value the start position of this range to fetch the range's start and end key-values for the skipList.
    val startKey =
      state.skipList.floor(insert.fromKey) mapS {
        case range: Memory.Range if insert.fromKey < range.toKey =>
          range.fromKey

        case _ =>
          insert.fromKey
      } getOrElse insert.fromKey

    val conflictingKeyValues = state.skipList.subMap(startKey, true, insert.toKey, false)
    if (conflictingKeyValues.isEmpty) {
      state.setHasRange(true) //set this before put so reads know to floor this skipList.
      state.skipList.put(insert.key, insert)
    } else {
      val oldKeyValues = Slice.of[Memory](conflictingKeyValues.size)

      conflictingKeyValues foreach {
        case (_, keyValue) =>
          oldKeyValues add keyValue
      }

      val builder = MergeStats.buffer[Memory, ListBuffer](Aggregator.fromBuilder(ListBuffer.newBuilder))

      SegmentMerger.merge(
        newKeyValues = Slice(insert),
        oldKeyValues = oldKeyValues,
        stats = builder,
        isLastLevel = false
      )

      val mergedKeyValues = builder.keyValues

      state.setHasRange(true) //set this before put so reads know to floor this skipList.

      oldKeyValues foreach {
        oldKeyValue =>
          state.skipList.remove(oldKeyValue.key)
      }

      mergedKeyValues foreach {
        keyValue =>
          state.skipList.put(keyValue.key, keyValue)
      }
    }
  }

  /**
   * @return the new SkipList is this write started a transactional write.
   */
  @inline private[zero] def put(entries: ListBuffer[MapEntry.Point[Slice[Byte], Memory]],
                                state: State)(implicit keyOrder: KeyOrder[Slice[Byte]],
                                              timeOrder: TimeOrder[Slice[Byte]],
                                              functionStore: FunctionStore): Unit =
    entries foreach {
      case remove @ MapEntry.Remove(_) =>
        //this does not occur in reality and should be type-safe instead of having this Exception.
        throw new IllegalAccessException(s"${MapEntry.productPrefix}.${remove.productPrefix} is not allowed in ${LevelZero.productPrefix}.")

      case MapEntry.Put(_, memory: Memory) =>
        LevelZeroMapCache.insert(insert = memory, state = state)
    }
}

/**
 * Ensures atomic and guarantee all or none writes to in-memory SkipList.
 *
 * Creates multi-layered SkipList.
 *
 * Currently all atomic operations defaults to using [[Glass]] which requires
 * blocking on conflicting in-memory SkipList updates. The cost of blocking
 * when concurrently writing and reading in-memory SkipList is very cheap.
 * The maximum time blocking time on benchmarking was between 0.006 to 0.019642 seconds.
 */
private[core] class LevelZeroMapCache private(state: LevelZeroMapCache.State)(implicit val keyOrder: KeyOrder[Slice[Byte]],
                                                                              timeOrder: TimeOrder[Slice[Byte]],
                                                                              functionStore: FunctionStore,
                                                                              atomic: Atomic) extends MapCache[Slice[Byte], Memory] {

  @inline private def write(entry: MapEntry[Slice[Byte], Memory], atomic: Boolean): Unit = {
    val entries = entry.entries

    if (entry.entriesCount > 1 || state.hasRange || entry.hasRange || entry.hasUpdate || entry.hasRemoveDeadline)
      if (atomic) {
        val sorted = entries.sortBy(_.key)(keyOrder)

        sorted.last match {
          case MapEntry.Put(_, last: Memory.Fixed) =>
            state.skipList.atomicWrite(from = sorted.head.key, to = last.key, toInclusive = true) {
              LevelZeroMapCache.put(
                entries = entries,
                state = state
              )
            }(Bag.glass)

          case MapEntry.Put(_, last: Memory.Range) =>
            state.skipList.atomicWrite(from = sorted.head.key, to = last.toKey, toInclusive = false) {
              LevelZeroMapCache.put(
                entries = entries,
                state = state
              )
            }(Bag.glass)

          case remove @ MapEntry.Remove(_) =>
            throw new IllegalAccessException(s"${MapEntry.productPrefix}.${remove.productPrefix} is not allowed in ${LevelZero.productPrefix}.")
        }

      } else {
        LevelZeroMapCache.put(
          entries = entries,
          state = state
        )
      }
    else
      entries.head applyPoint state.skipList
  }

  override def writeAtomic(entry: MapEntry[Slice[Byte], Memory]): Unit =
    write(entry = entry, atomic = atomic.enabled)

  override def writeNonAtomic(entry: MapEntry[Slice[Byte], Memory]): Unit =
    write(entry = entry, atomic = false)

  override def isEmpty: Boolean =
    state.skipList.isEmpty

  @inline def maxKeyValueCount: Int =
    state.skipList.size

  @inline def hasRange =
    state.getHasRange

  override def iterator: Iterator[(Slice[Byte], Memory)] =
    state.skipList.iterator

  @inline private def getRangeKeys(memory: Memory): (Slice[Byte], Slice[Byte], Boolean) =
    memory match {
      case fixed: Memory.Fixed =>
        (fixed.key, fixed.key, false)

      case Memory.Range(fromKey, toKey, _, _) =>
        (fromKey, toKey, true)
    }

  def headKeyOptimised: SliceOption[Byte] =
    if (atomic.enabled)
      state
        .skipList
        .atomicRead(getRangeKeys)(_.head())(Bag.glass)
        .flatMapSomeS(Slice.Null: SliceOption[Byte])(_.key)
    else
      state.skipList.headKey

  def lastKeyOptimised: SliceOption[Byte] =
    if (atomic.enabled)
      state
        .skipList
        .atomicRead(getRangeKeys)(_.last())(Bag.glass)
        .flatMapSomeS(Slice.Null: SliceOption[Byte])(_.key)
    else
      state.skipList.lastKey

  def headOptimised: MemoryOption =
    if (atomic.enabled)
      state.skipList.atomicRead(getRangeKeys)(_.head())(Bag.glass)
    else
      state.skipList.head()

  def lastOptimised: MemoryOption =
    if (atomic.enabled)
      state.skipList.atomicRead(getRangeKeys)(_.last())(Bag.glass)
    else
      state.skipList.last()

  def getOptimised(key: Slice[Byte]): MemoryOption =
    if (atomic.enabled)
      state.skipList.atomicRead(getRangeKeys)(_.get(key))(Bag.glass)
    else
      state.skipList.get(key)

  def floorOptimised(key: Slice[Byte]): MemoryOption =
    if (atomic.enabled)
      state.skipList.atomicRead(getRangeKeys)(_.floor(key))(Bag.glass)
    else
      state.skipList.floor(key)

  def lowerOptimised(key: Slice[Byte]): MemoryOption =
    if (atomic.enabled)
      state.skipList.atomicRead(getRangeKeys)(_.lower(key))(Bag.glass)
    else
      state.skipList.lower(key)

  def higherOptimised(key: Slice[Byte]): MemoryOption =
    if (atomic.enabled)
      state.skipList.atomicRead(getRangeKeys)(_.higher(key))(Bag.glass)
    else
      state.skipList.higher(key)

  def ceilingOptimised(key: Slice[Byte]): MemoryOption =
    if (atomic.enabled)
      state.skipList.atomicRead(getRangeKeys)(_.ceiling(key))(Bag.glass)
    else
      state.skipList.ceiling(key)

  def skipList =
    state.skipList
}
