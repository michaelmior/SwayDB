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

package swaydb.core.level.compaction.task

import org.scalamock.scalatest.MockFactory
import swaydb.core.TestData._
import swaydb.core.data.Memory
import swaydb.core.level.Level
import swaydb.core.segment.Segment
import swaydb.core.{TestBase, TestCaseSweeper, TestTimer}
import swaydb.data.NonEmptyList
import swaydb.data.order.KeyOrder
import swaydb.data.slice.Slice
import swaydb.serializers.Default._
import swaydb.serializers._

import scala.collection.SortedSet

class CompactionTasker_run_Spec extends TestBase with MockFactory {

  implicit val timer = TestTimer.Empty
  implicit val keyOrder = KeyOrder.default
  implicit val segmentOrdering = keyOrder.on[Segment](_.minKey)

  "Level is empty" in {
    TestCaseSweeper {
      implicit sweeper =>
        val segment = TestSegment(Slice(Memory.put(1)))
        val level = TestLevel()

        val tasks = CompactionTasker.run(Slice(segment), NonEmptyList(level), Int.MaxValue)

        tasks should contain only
          CompactionTask.Task(
            targetLevel = level,
            data = SortedSet(segment)
          )
    }
  }

  "all levels is empty" in {
    TestCaseSweeper {
      implicit sweeper =>
        val segment = TestSegment(Slice(Memory.put(1)))
        //multiple nested Levels but
        val level = TestLevel(nextLevel = Some(TestLevel(nextLevel = Some(TestLevel(nextLevel = Some(TestLevel()))))))

        val tasks = CompactionTasker.run(Slice(segment), NonEmptyList(level, level.nextLevels.map(_.asInstanceOf[Level])), Int.MaxValue)

        tasks should contain only
          CompactionTask.Task(
            targetLevel = level,
            data = SortedSet(segment)
          )

        tasks.head.targetLevel.levelNumber shouldBe level.levelNumber
    }
  }

  "all levels are empty but last Level has OVERLAPPING key-values" in {
    TestCaseSweeper {
      implicit sweeper =>
        //segment gets assigned to last Level with overlapping key-values.
        val segment = TestSegment(Slice(Memory.put(1)))
        //multiple nested Levels but last level is non-empty with the same key-values as the segment
        val lastLevel = TestLevel(keyValues = Slice(Memory.put(1)))
        val level = TestLevel(nextLevel = Some(TestLevel(nextLevel = Some(TestLevel(nextLevel = Some(lastLevel))))))

        val tasks = CompactionTasker.run(Slice(segment), NonEmptyList(level, level.nextLevels.map(_.asInstanceOf[Level])), Int.MaxValue)

        tasks should contain only
          CompactionTask.Task(
            targetLevel = lastLevel,
            data = SortedSet(segment)
          )

        tasks.head.targetLevel.levelNumber shouldBe lastLevel.levelNumber
    }
  }

  "all levels are empty but last Level has NON-OVERLAPPING key-values" in {
    TestCaseSweeper {
      implicit sweeper =>
        //segment gets assigned to last Level with overlapping key-values.
        val segment = TestSegment(Slice(Memory.put(1)))
        //multiple nested Levels but last level is non-empty and does not overlap the Segment's key-values
        //so expect the first level to get the assignment
        val lastLevel = TestLevel(keyValues = Slice(Memory.put(2)))
        val level = TestLevel(nextLevel = Some(TestLevel(nextLevel = Some(TestLevel(nextLevel = Some(lastLevel))))))

        val tasks = CompactionTasker.run(Slice(segment), NonEmptyList(level, level.nextLevels.map(_.asInstanceOf[Level])), Int.MaxValue)

        tasks should contain only
          CompactionTask.Task(
            targetLevel = level,
            data = SortedSet(segment)
          )

        tasks.head.targetLevel.levelNumber shouldBe level.levelNumber
    }
  }

  "head and last Level has NON-OVERLAPPING key-values and all other levels are empty" in {
    TestCaseSweeper {
      implicit sweeper =>
        //input key-value is 3

        //head level has key-value 1
        //nextLevel is empty
        //nextLevel is empty
        //last level has key-value 2

        val segment = TestSegment(Slice(Memory.put(3)))
        val lastLevel = TestLevel(keyValues = Slice(Memory.put(2)))
        val level = TestLevel(keyValues = Slice(Memory.put(1)), nextLevel = Some(TestLevel(nextLevel = Some(TestLevel(nextLevel = Some(lastLevel))))))

        val tasks = CompactionTasker.run(Slice(segment), NonEmptyList(level, level.nextLevels.map(_.asInstanceOf[Level])), Int.MaxValue)

        //expect segment to get assigned to first level.
        tasks should contain only
          CompactionTask.Task(
            targetLevel = level,
            data = SortedSet(segment)
          )

        tasks.head.targetLevel.levelNumber shouldBe level.levelNumber
    }
  }

  "head has OVERLAPPING key-values and last level is non-empty" in {
    TestCaseSweeper {
      implicit sweeper =>
        //input key-value is 1

        //head level has key-value 1
        //nextLevel is empty
        //nextLevel is empty
        //last level has key-value 2

        val segment = TestSegment(Slice(Memory.put(1)))
        val lastLevel = TestLevel(keyValues = Slice(Memory.put(2)))
        val level = TestLevel(keyValues = Slice(Memory.put(1)), nextLevel = Some(TestLevel(nextLevel = Some(TestLevel(nextLevel = Some(lastLevel))))))

        val tasks = CompactionTasker.run(Slice(segment), NonEmptyList(level, level.nextLevels.map(_.asInstanceOf[Level])), Int.MaxValue)

        //expect segment to get assigned to first level.
        tasks should contain only
          CompactionTask.Task(
            targetLevel = level,
            data = SortedSet(segment)
          )

        tasks.head.targetLevel.levelNumber shouldBe level.levelNumber
    }
  }

  "head has key-values assign but there also exists a non-overlapping key-value" in {
    TestCaseSweeper {
      implicit sweeper =>
        //input 2 segments with key-values 1 and 3 (3 is copyable)

        //head level has key-value 1
        //nextLevel is empty
        //nextLevel is empty
        //last level has key-value 2

        val segments = Slice(TestSegment(Slice(Memory.put(1))), TestSegment(Slice(Memory.put(3))))
        val lastLevel = TestLevel(keyValues = Slice(Memory.put(2)))
        val level = TestLevel(keyValues = Slice(Memory.put(1)), nextLevel = Some(TestLevel(nextLevel = Some(TestLevel(nextLevel = Some(lastLevel))))))

        val tasks = CompactionTasker.run(segments, NonEmptyList(level, level.nextLevels.map(_.asInstanceOf[Level])), Int.MaxValue)

        //expect segment to get assigned to first level.
        tasks should contain only
          CompactionTask.Task(
            targetLevel = level,
            data = SortedSet(segments.head, segments.last)
          )

        tasks.head.targetLevel.levelNumber shouldBe level.levelNumber
    }
  }

  "assign all overlapping Segments to their respective levels" when {
    "NO GAPS" in {
      TestCaseSweeper {
        implicit sweeper =>
          //input Segments - 1, 2, 3, 4

          val segments =
            Slice(
              TestSegment(Slice(Memory.put(1))),
              TestSegment(Slice(Memory.put(2))),
              TestSegment(Slice(Memory.put(3))),
              TestSegment(Slice(Memory.put(4)))
            )

          val level4 = TestLevel(keyValues = Slice(Memory.put(4)))
          val level3 = TestLevel(keyValues = Slice(Memory.put(3)), nextLevel = Some(level4))
          val level2 = TestLevel(keyValues = Slice(Memory.put(2)), nextLevel = Some(level3))
          val level1 = TestLevel(keyValues = Slice(Memory.put(1)), nextLevel = Some(level2))

          val tasks = CompactionTasker.run(segments, NonEmptyList(level1, level1.nextLevels.map(_.asInstanceOf[Level])), Int.MaxValue).toList

          tasks should have size 4

          tasks.head shouldBe
            CompactionTask.Task(
              targetLevel = level1,
              data = SortedSet(segments.head)
            )

          tasks(1) shouldBe
            CompactionTask.Task(
              targetLevel = level2,
              data = SortedSet(segments(1))
            )

          tasks(2) shouldBe
            CompactionTask.Task(
              targetLevel = level3,
              data = SortedSet(segments(2))
            )

          tasks(3) shouldBe
            CompactionTask.Task(
              targetLevel = level4,
              data = SortedSet(segments(3))
            )
      }
    }

    "GAPS" in {
      TestCaseSweeper {
        implicit sweeper =>
          //input Segments - 1, 2, 3, 4, 6, 7

          val segments =
            Slice(
              TestSegment(Slice(Memory.put(1))),
              TestSegment(Slice(Memory.put(2))),
              TestSegment(Slice(Memory.put(3))),
              TestSegment(Slice(Memory.put(4))),
              TestSegment(Slice(Memory.put(5))),
              TestSegment(Slice(Memory.put(6))),
              TestSegment(Slice(Memory.put(7)))
            )

          val level4 = TestLevel(keyValues = Slice(Memory.put(4)))
          val level3 = TestLevel(keyValues = Slice(Memory.put(3)), nextLevel = Some(level4))
          val level2 = TestLevel(keyValues = Slice(Memory.put(2)), nextLevel = Some(level3))
          val level1 = TestLevel(keyValues = Slice(Memory.put(1)), nextLevel = Some(level2))

          val tasks = CompactionTasker.run(segments, NonEmptyList(level1, level1.nextLevels.map(_.asInstanceOf[Level])), Int.MaxValue).toList

          tasks should have size 4

          tasks.head shouldBe
            CompactionTask.Task(
              targetLevel = level1,
              data = SortedSet(segments.head) ++ segments.takeRight(3)
            )

          tasks(1) shouldBe
            CompactionTask.Task(
              targetLevel = level2,
              data = SortedSet(segments(1))
            )

          tasks(2) shouldBe
            CompactionTask.Task(
              targetLevel = level3,
              data = SortedSet(segments(2))
            )

          tasks(3) shouldBe
            CompactionTask.Task(
              targetLevel = level4,
              data = SortedSet(segments(3))
            )
      }
    }
  }
}
