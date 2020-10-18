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

package swaydb.core.segment.assigner

import org.scalatest.OptionValues._
import swaydb.core.CommonAssertions._
import swaydb.core.TestData._
import swaydb.core.data.{Memory, Value}
import swaydb.core.io.file.Effect._
import swaydb.core.segment.SegmentIO
import swaydb.core.util.PipeOps._
import swaydb.core.{TestBase, TestCaseSweeper, TestForceSave, TestTimer}
import swaydb.data.RunThis._
import swaydb.data.config.MMAP
import swaydb.data.order.KeyOrder
import swaydb.data.slice.Slice
import swaydb.data.util.OperatingSystem
import swaydb.serializers.Default._
import swaydb.serializers._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class Segment_AssignerAssignKeyValues_Spec0 extends SegmentAssigner_AssignKeyValues_Spec {
  val keyValueCount = 100
}

class Segment_AssignerAssignKeyValues_Spec1 extends SegmentAssigner_AssignKeyValues_Spec {
  val keyValueCount = 100

  override def levelFoldersCount = 10
  override def mmapSegments = MMAP.On(OperatingSystem.isWindows, forceSave = TestForceSave.mmap())
  override def level0MMAP = MMAP.On(OperatingSystem.isWindows, forceSave = TestForceSave.mmap())
  override def appendixStorageMMAP = MMAP.On(OperatingSystem.isWindows, forceSave = TestForceSave.mmap())
}

class Segment_AssignerAssignKeyValues_Spec2 extends SegmentAssigner_AssignKeyValues_Spec {
  val keyValueCount = 100

  override def levelFoldersCount = 10
  override def mmapSegments = MMAP.Off(forceSave = TestForceSave.channel())
  override def level0MMAP = MMAP.Off(forceSave = TestForceSave.channel())
  override def appendixStorageMMAP = MMAP.Off(forceSave = TestForceSave.channel())
}

class Segment_AssignerAssignKeyValues_Spec3 extends SegmentAssigner_AssignKeyValues_Spec {
  val keyValueCount = 1000
  override def inMemoryStorage = true
}

sealed trait SegmentAssigner_AssignKeyValues_Spec extends TestBase {
  implicit val keyOrder = KeyOrder.default
  implicit val testTimer: TestTimer = TestTimer.Empty
  implicit def segmentIO: SegmentIO = SegmentIO.random

  def keyValueCount: Int

  "assign new key-value to the Segment" when {
    "both have the same key values" in {
      runThis(10.times, log = true) {
        TestCaseSweeper {
          implicit sweeper =>
            val keyValues = randomKeyValues(count = keyValueCount, startId = Some(1))
            val segment = TestSegment(keyValues)

            //assign the Segment's key-values to itself.
            /**
             * Test with no gaps
             */
            val noGaps = SegmentAssigner.assignUnsafeNoGaps(keyValues, Slice(segment))
            noGaps should have size 1
            noGaps.head.midOverlap.expectKeyValues() shouldBe keyValues

            /**
             * Test with gaps
             */
            val gaps = SegmentAssigner.assignUnsafeGaps[ListBuffer[Assignable]](keyValues, Slice(segment))
            gaps should have size 1
            gaps.head.headGap.result shouldBe empty
            gaps.head.midOverlap.expectKeyValues() shouldBe keyValues
            gaps.head.tailGap.result shouldBe empty
        }
      }
    }

    "segment is assigned" in {
      runThis(10.times, log = true) {
        TestCaseSweeper {
          implicit sweeper =>
            val segmentKeyValue = randomKeyValues(count = keyValueCount, startId = Some(0))

            val segment = TestSegment(segmentKeyValue)

            /**
             * Test with no gaps
             */
            val noGaps = SegmentAssigner.assignUnsafeNoGaps(Slice(segment), Slice(segment))
            noGaps should have size 1

            val assignedSegments = noGaps.head.midOverlap.expectSegments()
            assignedSegments should have size 1
            assignedSegments.head.segmentId shouldBe segment.segmentId

            /**
             * Test with gaps
             */
            val gaps = SegmentAssigner.assignUnsafeGaps[ListBuffer[Assignable]](Slice(segment), Slice(segment))
            gaps should have size 1
            gaps.head.headGap.result shouldBe empty
            gaps.head.tailGap.result shouldBe empty

            val assignedSegments2 = noGaps.head.midOverlap.expectSegments()
            assignedSegments2 should have size 1
            assignedSegments2.head.segmentId shouldBe segment.segmentId
        }
      }
    }
  }

  "assign KeyValues to the first Segment if there is only one Segment" when {
    "noGap" in {
      TestCaseSweeper {
        implicit sweeper =>
          val keyValues = randomizedKeyValues(10, startId = Some(0))

          val segmentKeyValues = randomizedKeyValues(10, startId = Some(0))(TestTimer.Incremental())
          val segment = TestSegment(segmentKeyValues)

          val result = SegmentAssigner.assignUnsafeNoGaps(keyValues, List(segment))
          result.size shouldBe 1
          val assignment = result.head
          assignment.segment.path shouldBe segment.path

          assignment.midOverlap.expectKeyValues() shouldBe keyValues.toList
      }
    }

    "gaps" in {
      runThis(5.times, log = true) {
        TestCaseSweeper {
          implicit sweeper =>
            val headGap = randomizedKeyValues(100, startId = Some(0))
            val midKeyValues = randomizedKeyValues(100, startId = Some(headGap.nextKey(incrementBy = randomIntMax(2))))
            val tailGap = randomizedKeyValues(100, startId = Some(midKeyValues.nextKey(incrementBy = randomIntMax(2))))

            //create the Segment with only mid key-values
            val segment = TestSegment(midKeyValues)

            val newKeyValues = headGap ++ midKeyValues ++ tailGap

            val result = SegmentAssigner.assignUnsafeGaps[ListBuffer[Assignable]](newKeyValues, List(segment))
            result.size shouldBe 1
            val assignment = result.head
            assignment.segment.path shouldBe segment.path

            assignment.headGap.result.expectKeyValues() shouldBe headGap
            assignment.midOverlap.expectKeyValues() shouldBe midKeyValues
            assignment.tailGap.result.expectKeyValues() shouldBe tailGap
        }
      }
    }
  }

  "assign KeyValues to second Segment when none of the keys belong to the first Segment" in {
    TestCaseSweeper {
      implicit sweeper =>
        val segment1 = TestSegment(Slice(Memory.put(1), Memory.Range(2, 10, Value.FromValue.Null, Value.remove(10.seconds.fromNow))))
        val segment2 = TestSegment(Slice(Memory.put(10)))
        val segments = Seq(segment1, segment2)

        val result =
          SegmentAssigner.assignUnsafeNoGaps(
            assignables =
              Slice(
                randomFixedKeyValue(10),
                randomRangeKeyValue(11, 20),
                randomFixedKeyValue(20)
              ),
            segments = segments
          )

        result.size shouldBe 1
        result.head.segment.path shouldBe segment2.path
    }
  }

  "assign gap KeyValue to the first Segment if the first Segment already has a key-value assigned to it" in {
    TestCaseSweeper {
      implicit sweeper =>
        val segment1 = TestSegment(Slice(randomFixedKeyValue(1), randomRangeKeyValue(2, 10)))
        val segment2 = TestSegment(Slice(randomFixedKeyValue(20)))
        val segments = Seq(segment1, segment2)

        //1 belongs to first Segment, 15 is a gap key and since first segment is not empty, it will value assigned 15.
        val keyValues =
          Slice(
            Memory.put(1, 1),
            Memory.put(15),
            Memory.Range(16, 20, Value.FromValue.Null, Value.update(16))
          )

        val result = SegmentAssigner.assignUnsafeNoGaps(keyValues, segments)
        result.size shouldBe 1
        result.head.segment.path shouldBe segment1.path
        result.head.midOverlap.expectKeyValues() shouldBe keyValues
    }
  }

  "assign gap KeyValue to the second Segment if the first Segment has no key-value assigned to it" in {
    runThis(10.times, log = true) {
      TestCaseSweeper {
        implicit sweeper =>
          val segment1KeyValues = Slice(randomFixedKeyValue(1), randomRangeKeyValue(2, 10))
          val segment2KeyValues = Slice(randomFixedKeyValue(20))

          val segment1 = TestSegment(segment1KeyValues)
          val segment2 = TestSegment(segment2KeyValues)
          val segments = Seq(segment1, segment2)

          //15 is a gap key but no key-values are assigned to segment1 so segment2 will value this key-value.
          val keyValues =
            Slice(
              randomFixedKeyValue(15),
              randomRangeKeyValue(20, 100)
            )

          val result = SegmentAssigner.assignUnsafeNoGaps(keyValues, segments)
          result.size shouldBe 1
          result.head.segment.path shouldBe segment2.path
          result.head.midOverlap.expectKeyValues() shouldBe keyValues
      }
    }
  }

  "assign gap Range KeyValue to all Segments that fall within the Range's toKey" in {
    TestCaseSweeper {
      implicit sweeper =>
        // 1 - 10(exclusive)
        val segment1 = TestSegment(Slice(Memory.put(1), Memory.Range(2, 10, Value.FromValue.Null, Value.remove(None))))
        // 20 - 20
        val segment2 = TestSegment(Slice(Memory.remove(20)))
        //21 - 30
        val segment3 = TestSegment(Slice(Memory.Range(21, 30, Value.FromValue.Null, Value.remove(None)), Memory.put(30)))
        //40 - 60
        val segment4 = TestSegment(Slice(Memory.remove(40), Memory.Range(41, 50, Value.FromValue.Null, Value.remove(None)), Memory.put(60)))
        //70 - 80
        val segment5 = TestSegment(Slice(Memory.put(70), Memory.remove(80)))
        val segments = Seq(segment1, segment2, segment3, segment4, segment5)

        //15 is a gap key but no key-values are assigned to segment1 so segment2 will value this key-value an it will be split across.
        //all next overlapping Segments.
        val keyValues =
        Slice(
          Memory.Range(15, 50, Value.remove(None), Value.update(10))
        )

        def assertResult(assignments: Iterable[Assignment[Nothing]]) = {
          assignments.size shouldBe 3
          assignments.find(_.segment == segment2).value.midOverlap.expectKeyValues() should contain only Memory.Range(15, 21, Value.remove(None), Value.update(10))
          assignments.find(_.segment == segment3).value.midOverlap.expectKeyValues() should contain only Memory.Range(21, 40, Value.FromValue.Null, Value.update(10))
          assignments.find(_.segment == segment4).value.midOverlap.expectKeyValues() should contain only Memory.Range(40, 50, Value.FromValue.Null, Value.update(10))
        }

        assertResult(SegmentAssigner.assignUnsafeNoGaps(keyValues, segments))
    }
  }

  "assign key value to the first segment when the key is the new smallest" in {
    TestCaseSweeper {
      implicit sweeper =>
        val segment1 = TestSegment(Slice(randomFixedKeyValue(1), randomFixedKeyValue(2)))
        val segment2 = TestSegment(Slice(randomFixedKeyValue(4), randomFixedKeyValue(5)))

        //segment1 - 1 - 2
        //segment2 - 4 - 5
        val segments = Seq(segment1, segment2)

        val assignments = SegmentAssigner.assignUnsafeNoGaps(Slice(Memory.put(0)), segments)
        assignments.size shouldBe 1
        assignments.head.segment.path shouldBe segment1.path
    }
  }

  "assign key value to the first segment and split out to other Segment when the key is the new smallest and the range spreads onto other Segments" in {
    TestCaseSweeper {
      implicit sweeper =>
        val segment1 = TestSegment(Slice(Memory.put(1), Memory.put(2)))
        val segment2 = TestSegment(Slice(Memory.put(4), Memory.put(5)))
        val segment3 = TestSegment(Slice(Memory.Range(6, 10, Value.remove(None), Value.update(10)), Memory.remove(10)))

        //segment1 - 1 - 2
        //segment2 - 4 - 5
        //segment3 - 6 - 10
        val segments = Seq(segment1, segment2, segment3)

        //insert range 0 - 20. This overlaps all 3 Segment and key-values will value sliced and distributed to all Segments.
        val assignments = SegmentAssigner.assignUnsafeNoGaps(Slice(Memory.Range(0, 20, Value.put(0), Value.remove(None))), segments)
        assignments.size shouldBe 3
        assignments.find(_.segment == segment1).value.midOverlap.expectKeyValues() should contain only Memory.Range(0, 4, Value.put(0), Value.remove(None))
        assignments.find(_.segment == segment2).value.midOverlap.expectKeyValues() should contain only Memory.Range(4, 6, Value.FromValue.Null, Value.remove(None))
        assignments.find(_.segment == segment3).value.midOverlap.expectKeyValues() should contain only Memory.Range(6, 20, Value.FromValue.Null, Value.remove(None))
    }
  }

  "debugger" in {
    TestCaseSweeper {
      implicit sweeper =>
        val segment1 = TestSegment(Slice(Memory.put(1), Memory.Range(26074, 26075, Value.FromValue.Null, Value.update(Slice.Null, None))))
        val segment2 = TestSegment(Slice(Memory.put(26075), Memory.Range(28122, 28123, Value.FromValue.Null, Value.update(Slice.Null, None))))
        val segment3 = TestSegment(Slice(Memory.put(28123), Memory.Range(32218, 32219, Value.FromValue.Null, Value.update(Slice.Null, None))))
        val segment4 = TestSegment(Slice(Memory.put(32219), Memory.Range(40410, 40411, Value.FromValue.Null, Value.update(Slice.Null, None))))
        val segment5 = TestSegment(Slice(Memory.put(74605), Memory.put(100000)))

        val segments = Seq(segment1, segment2, segment3, segment4, segment5)

        val assignments = SegmentAssigner.assignUnsafeNoGaps(Slice(Memory.put(1), Memory.put(100000)), segments)
        assignments.size shouldBe 2
        assignments.find(_.segment == segment1).value.midOverlap.expectKeyValues() should contain only Memory.put(1)
        assignments.find(_.segment == segment5).value.midOverlap.expectKeyValues() should contain only Memory.put(100000)
    }
  }

  "assign key value to the last segment when the key is the new largest" in {
    TestCaseSweeper {
      implicit sweeper =>
        val segment1 = TestSegment(Slice(Memory.put(1), Memory.put(2)))
        val segment2 = TestSegment(Slice(Memory.put(4), Memory.put(5)))
        val segment3 = TestSegment(Slice(Memory.put(6), Memory.put(7)))
        val segment4 = TestSegment(Slice(Memory.put(8), Memory.put(9)))
        val segments = Seq(segment1, segment2, segment3, segment4)

        SegmentAssigner.assignUnsafeNoGaps(Slice(Memory.put(10, "ten")), segments) ==> {
          assignments =>
            assignments.size shouldBe 1
            assignments.head.segment.path shouldBe segment4.path
            assignments.head.midOverlap.expectKeyValues() should contain only Memory.put(10, "ten")
        }

        SegmentAssigner.assignUnsafeNoGaps(Slice(Memory.remove(10)), segments) ==> {
          assignments =>
            assignments.size shouldBe 1
            assignments.head.segment.path shouldBe segment4.path
            assignments.head.midOverlap.expectKeyValues() should contain only Memory.remove(10)
        }

        SegmentAssigner.assignUnsafeNoGaps(Slice(Memory.Range(10, 20, Value.put(10), Value.remove(None))), segments) ==> {
          assignments =>
            assignments.size shouldBe 1
            assignments.head.segment.path shouldBe segment4.path
            assignments.head.midOverlap.expectKeyValues() should contain only Memory.Range(10, 20, Value.put(10), Value.remove(None))
        }
    }
  }

  "assign all KeyValues to their target Segments" in {
    TestCaseSweeper {
      implicit sweeper =>
        val keyValues = Slice(randomFixedKeyValue(1), randomFixedKeyValue(2), randomFixedKeyValue(3), randomFixedKeyValue(4), randomFixedKeyValue(5))
        val segment1 = TestSegment(Slice(randomFixedKeyValue(key = 1)))
        val segment2 = TestSegment(Slice(randomFixedKeyValue(key = 2)))
        val segment3 = TestSegment(Slice(randomFixedKeyValue(key = 3)))
        val segment4 = TestSegment(Slice(randomFixedKeyValue(key = 4)))
        val segment5 = TestSegment(Slice(randomFixedKeyValue(key = 5)))

        val segments = List(segment1, segment2, segment3, segment4, segment5)

        val result = SegmentAssigner.assignUnsafeNoGaps(keyValues, segments)
        result.size shouldBe 5

        //sort them by the fileId, so it's easier to test
        val resultArray = result.toArray.sortBy(_.segment.path.fileId._1)

        resultArray(0).segment.path shouldBe segment1.path
        resultArray(0).midOverlap should have size 1
        resultArray(0).midOverlap.head.key shouldBe (1: Slice[Byte])

        resultArray(1).segment.path shouldBe segment2.path
        resultArray(1).midOverlap should have size 1
        resultArray(1).midOverlap.head.key shouldBe (2: Slice[Byte])

        resultArray(2).segment.path shouldBe segment3.path
        resultArray(2).midOverlap should have size 1
        resultArray(2).midOverlap.head.key shouldBe (3: Slice[Byte])

        resultArray(3).segment.path shouldBe segment4.path
        resultArray(3).midOverlap should have size 1
        resultArray(3).midOverlap.head.key shouldBe (4: Slice[Byte])

        resultArray(4).segment.path shouldBe segment5.path
        resultArray(4).midOverlap should have size 1
        resultArray(4).midOverlap.head.key shouldBe (5: Slice[Byte])
    }
  }
}