/*
 * Copyright (c) 2021 Simer JS Plaha (simer.j@gmail.com - @simerplaha)
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

package swaydb.core.level.compaction.selector

import swaydb.core.data.Memory
import swaydb.core.level.Level
import swaydb.core.level.zero.{LevelZero, LevelZeroMapCache}
import swaydb.core.segment.Segment
import swaydb.core.util.AtomicRanges
import swaydb.data.slice.Slice

sealed trait CompactionTask

object CompactionTask {

  sealed trait Segments extends CompactionTask

  case class CompactSegments(sourceLevel: Level,
                             sourceReservation: AtomicRanges.Key[Slice[Byte]],
                             sourceSegments: Iterable[Segment],
                             targetLevel: Level,
                             targetReservation: AtomicRanges.Key[Slice[Byte]],
                             targetSegments: Iterable[Segment]) extends CompactionTask.Segments

  case class CollapseSegments(level: Level,
                              reservation: AtomicRanges.Key[Slice[Byte]],
                              segments: Iterable[Segment]) extends CompactionTask.Segments

  case class RefreshSegments(level: Level,
                             reservation: AtomicRanges.Key[Slice[Byte]],
                             segments: Iterable[Segment]) extends CompactionTask.Segments

  case class CompactMaps(levelZero: LevelZero,
                         targetLevel: Level,
                         targetReservations: AtomicRanges.Key[Slice[Byte]],
                         segments: Iterable[swaydb.core.map.Map[Slice[Byte], Memory, LevelZeroMapCache]]) extends CompactionTask.Segments

}