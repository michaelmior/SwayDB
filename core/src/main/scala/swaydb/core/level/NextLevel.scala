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

package swaydb.core.level

import swaydb.core.data.Memory
import swaydb.core.level.compaction.CompactResult
import swaydb.core.level.zero.LevelZeroMapCache
import swaydb.core.map.Map
import swaydb.core.segment.assigner.Assignable
import swaydb.core.segment.block.segment.data.TransientSegment
import swaydb.core.segment.{Segment, SegmentOption}
import swaydb.core.util.AtomicRanges
import swaydb.data.compaction.{LevelMeter, Throttle}
import swaydb.data.config.PushForwardStrategy
import swaydb.data.slice.Slice
import swaydb.{Error, IO}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

object NextLevel {

  def foreachRight[T](level: NextLevel, f: NextLevel => T): Unit = {
    level.nextLevel foreach {
      nextLevel =>
        foreachRight(nextLevel, f)
    }
    f(level)
  }

  def reverseNextLevels(level: NextLevel): ListBuffer[NextLevel] = {
    val levels = ListBuffer.empty[NextLevel]
    NextLevel.foreachRight(
      level = level,
      f = level =>
        levels += level
    )
    levels
  }
}

/**
 * Levels that can have upper Levels or Levels that upper Levels can merge Segments or Maps into.
 */
trait NextLevel extends LevelRef {

  def pathDistributor: PathsDistributor

  def throttle: LevelMeter => Throttle

  def isCopyable(minKey: Slice[Byte], maxKey: Slice[Byte], maxKeyInclusive: Boolean): Boolean

  def isCopyable(map: Map[Slice[Byte], Memory, LevelZeroMapCache]): Boolean

  def partitionCopyable(segments: Iterable[Segment]): (Iterable[Segment], Iterable[Segment])

  def isNonEmpty(): Boolean

  def pushForwardStrategy: PushForwardStrategy

  def mightContainFunction(key: Slice[Byte]): Boolean

  def merge(segment: Assignable.Collection,
            removeDeletedRecords: Boolean)(implicit ec: ExecutionContext): Future[Iterable[CompactResult[SegmentOption, Iterable[TransientSegment]]]]

  def mergeMap(map: Map[Slice[Byte], Memory, LevelZeroMapCache],
               removeDeletedRecords: Boolean)(implicit ec: ExecutionContext): Future[Iterable[CompactResult[SegmentOption, Iterable[TransientSegment]]]]

  def mergeMaps(map: Iterable[Map[Slice[Byte], Memory, LevelZeroMapCache]],
                removeDeletedRecords: Boolean)(implicit ec: ExecutionContext): Future[Iterable[CompactResult[SegmentOption, Iterable[TransientSegment]]]]

  def merge(segments: Iterable[Assignable.Collection],
            removeDeletedRecords: Boolean)(implicit ec: ExecutionContext): Future[Iterable[CompactResult[SegmentOption, Iterable[TransientSegment]]]]

  def refresh(segment: Iterable[Segment],
              removeDeletedRecords: Boolean): IO[Error.Level, Iterable[CompactResult[Segment, Slice[TransientSegment]]]]

  def collapse(segments: Iterable[Segment],
               removeDeletedRecords: Boolean)(implicit ec: ExecutionContext): Future[LevelCollapseResult]

  def commit(mergeResult: CompactResult[SegmentOption, Iterable[TransientSegment]]): IO[Error.Level, Unit]

  def commit(mergeResult: Iterable[CompactResult[SegmentOption, Iterable[TransientSegment]]]): IO[Error.Level, Unit]

  def commit(collapsed: LevelCollapseResult.Collapsed): IO[Error.Level, Unit]

  def commit(old: Iterable[Segment],
             merged: Iterable[CompactResult[SegmentOption, Iterable[TransientSegment]]]): IO[Error.Level, Unit]

  def remove(segments: Iterable[Segment]): IO[swaydb.Error.Level, Unit]

  def meter: LevelMeter

  def reverseNextLevels: ListBuffer[NextLevel] = {
    val levels = ListBuffer.empty[NextLevel]
    NextLevel.foreachRight(
      level = this,
      f = level =>
        levels += level
    )
    levels
  }

  def levelSize: Long

  def minSegmentSize: Int

  def lastSegmentId: Option[Long]

  def openedFiles(): Long

  def pendingDeletes(): Long

  def take(count: Int): Slice[Segment]

  def takeSmallSegments(size: Int): Iterable[Segment]

  def takeLargeSegments(size: Int): Iterable[Segment]

  def takeSegments(size: Int,
                   condition: Segment => Boolean): Iterable[Segment]

  def segments(): Iterable[Segment]

  def deleteNoSweep: IO[swaydb.Error.Level, Unit]

  def deleteNoSweepNoClose(): IO[swaydb.Error.Level, Unit]

  def closeNoSweepNoRelease(): IO[swaydb.Error.Level, Unit]
}
