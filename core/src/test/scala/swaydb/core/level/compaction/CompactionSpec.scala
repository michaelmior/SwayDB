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

package swaydb.core.level.compaction

import org.scalamock.scalatest.MockFactory
import org.scalatest.EitherValues._
import swaydb.Error.Segment.ErrorHandler
import swaydb.IO
import swaydb.IOValues._
import swaydb.core.CommonAssertions._
import swaydb.core.RunThis._
import swaydb.core.TestData._
import swaydb.core.actor.{FileSweeper, MemorySweeper}
import swaydb.core.data.Memory
import swaydb.core.level.NextLevel
import swaydb.core.segment.Segment
import swaydb.core.{TestBase, TestLimitQueues, TestTimer}
import swaydb.data.order.{KeyOrder, TimeOrder}
import swaydb.data.slice.Slice
import swaydb.data.util.StorageUnits._
import swaydb.serializers.Default._
import swaydb.serializers._

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

class CompactionSpec0 extends CompactionSpec

class CompactionSpec1 extends CompactionSpec {
  override def levelFoldersCount = 10
  override def mmapSegmentsOnWrite = true
  override def mmapSegmentsOnRead = true
  override def level0MMAP = true
  override def appendixStorageMMAP = true
}

class CompactionSpec2 extends CompactionSpec {
  override def levelFoldersCount = 10
  override def mmapSegmentsOnWrite = false
  override def mmapSegmentsOnRead = false
  override def level0MMAP = false
  override def appendixStorageMMAP = false
}

class CompactionSpec3 extends CompactionSpec {
  override def inMemoryStorage = true
}

sealed trait CompactionSpec extends TestBase with MockFactory {

  val keyValueCount = 1000

  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
  implicit val timeOrder: TimeOrder[Slice[Byte]] = TimeOrder.long
  implicit val timer = TestTimer.Empty

  implicit val maxOpenSegmentsCacheImplicitLimiter: FileSweeper.Enabled = TestLimitQueues.fileSweeper
  implicit val memorySweeperImplicitSweeper: Option[MemorySweeper.Both] = TestLimitQueues.memorySweeper

  "putForward" should {
    "return zero" when {
      "input Segments are empty" in {
        //levels are never invoked
        val thisLevel = mock[NextLevel]("thisLevel")
        val nextLevel = mock[NextLevel]("nextLevel")

        Compaction.putForward(Iterable.empty, thisLevel, nextLevel).right.value shouldBe IO.zero
      }
    }

    "remove Segments" when {
      "Segments from upper Level are merged into lower level" in {
        val thisLevel = mock[NextLevel]("thisLevel")
        val nextLevel = mock[NextLevel]("nextLevel")

        val keyValues = randomPutKeyValues(keyValueCount).groupedSlice(2)
        val segments = Seq(TestSegment(keyValues(0).toTransient).get, TestSegment(keyValues(1).toTransient).get)

        //next level should value a put for all the input Segments
        (nextLevel.put(_: Iterable[Segment])(_: ExecutionContext)) expects(*, *) onCall {
          (putSegments: Iterable[Segment], _) =>
            putSegments.map(_.path) shouldBe segments.map(_.path)
            IO.eitherUnit
        }

        //segments value removed
        (thisLevel.removeSegments(_: Iterable[Segment])) expects * onCall {
          putSegments: Iterable[Segment] =>
            putSegments.map(_.path) shouldBe segments.map(_.path)
            IO.Success(segments.size)
        }

        Compaction.putForward(segments, thisLevel, nextLevel).right.value.value shouldBe segments.size
      }
    }

    "return success" when {
      "it fails to remove Segments" in {
        val thisLevel = mock[NextLevel]("thisLevel")
        val nextLevel = mock[NextLevel]("nextLevel")

        val keyValues = randomPutKeyValues(keyValueCount).groupedSlice(2)
        val segments = Seq(TestSegment(keyValues(0).toTransient).get, TestSegment(keyValues(1).toTransient).get)

        //next level should value a put for all the input Segments
        (nextLevel.put(_: Iterable[Segment])(_: ExecutionContext)) expects(*, *) onCall {
          (putSegments: Iterable[Segment], _) =>
            putSegments.map(_.path) shouldBe segments.map(_.path)
            IO.eitherUnit
        }

        //segments value removed
        (thisLevel.removeSegments(_: Iterable[Segment])) expects * onCall {
          putSegments: Iterable[Segment] =>
            putSegments.map(_.path) shouldBe segments.map(_.path)
            IO.failed("Failed!")
        }

        Compaction.putForward(segments, thisLevel, nextLevel).right.value.value shouldBe segments.size
      }
    }
  }

  "copyForwardForEach" should {
    "not copy" when {
      "it's the last Level and is empty" in {
        Compaction.copyForwardForEach(Slice(TestLevel())) shouldBe 0
      }

      "it's the last Level and is non empty" in {
        val keyValues = randomPutKeyValues(keyValueCount).toMemory
        val level = TestLevel(keyValues = keyValues)
        level.isEmpty shouldBe false
        Compaction.copyForwardForEach(level.reverseLevels.toSlice) shouldBe 0
        if (persistent)
          assertGet(keyValues, level.reopen)
      }
    }

    "copy all Segments to last level" when {
      "no Segments overlap" in {
        val allKeyValues = randomPutKeyValues(keyValueCount, startId = Some(1)).toMemory
        val keyValues = allKeyValues.groupedSlice(5)

        val level5 = TestLevel(keyValues = keyValues(4), segmentSize = 1.kb)
        val level4 = TestLevel(nextLevel = Some(level5), keyValues = keyValues(3), segmentSize = 10.bytes)
        val level3 = TestLevel(nextLevel = Some(level4), keyValues = keyValues(2), segmentSize = 10.bytes)
        val level2 = TestLevel(nextLevel = Some(level3), keyValues = keyValues(1), segmentSize = 10.bytes)
        val level1 = TestLevel(nextLevel = Some(level2), keyValues = keyValues(0), segmentSize = 10.bytes)

        //        level1.foreachLevel(_.segmentsCount() should be > 1)

        val expectedCopiedSegments = level1.foldLeftLevels(0)(_ + _.segmentsCount()) - level5.segmentsCount()
        Compaction.copyForwardForEach(level1.reverseLevels.toSlice) shouldBe expectedCopiedSegments
        //all top levels shouldBe empty
        level1.mapLevels(level => level).dropRight(1).foreach(_.isEmpty shouldBe true)

        assertReads(allKeyValues, level1)
        assertReads(allKeyValues, level2)
        assertReads(allKeyValues, level3)
        assertReads(allKeyValues, level4)
        assertReads(allKeyValues, level5)

        assertGet(allKeyValues, level1.reopen)
      }
    }

    "copy Segments to last level" when {
      "some Segments overlap" in {
        val allKeyValues = randomPutKeyValues(keyValueCount, addPutDeadlines = false, startId = Some(1)).toMemory

        val keyValues = allKeyValues.groupedSlice(5)

        val level5 = TestLevel(keyValues = Slice(keyValues(3).last) ++ keyValues(4), segmentSize = 2.kb)
        val level4 = TestLevel(nextLevel = Some(level5), keyValues = Slice(keyValues(2).last) ++ keyValues(3), segmentSize = 2.kb)
        val level3 = TestLevel(nextLevel = Some(level4), keyValues = Slice(keyValues(1).last) ++ keyValues(2), segmentSize = 2.kb)
        val level2 = TestLevel(nextLevel = Some(level3), keyValues = Slice(keyValues(0).last) ++ keyValues(1), segmentSize = 2.kb)
        val level1 = TestLevel(nextLevel = Some(level2), keyValues = keyValues(0), segmentSize = 2.kb)

        Compaction.copyForwardForEach(level1.reverseLevels.toSlice)

        //top levels are level, second last level value all overlapping Segments, last Level gets the rest.
        level1.isEmpty shouldBe true
        level2.isEmpty shouldBe true
        level3.isEmpty shouldBe true
        level4.isEmpty shouldBe false
        level5.isEmpty shouldBe false

        assertReads(allKeyValues, level1)
        assertGet(allKeyValues, level1.reopen)
      }
    }
  }

  "runLastLevelCompaction" should {
    "not run compaction" when {
      "level is not the last Level" in {
        val level = mock[NextLevel]("level")
        level.hasNextLevel _ expects() returns false

        Compaction.runLastLevelCompaction(level, true, 100, 0) shouldBe IO.zero
      }

      "remaining compactions are 0" in {
        val level = mock[NextLevel]("level")
        level.hasNextLevel _ expects() returns true

        Compaction.runLastLevelCompaction(level, true, remainingCompactions = 0, 10) shouldBe IO.Success(10)
      }
    }

    "invoke keep invoking refresh" when {
      "remaining compactions are non zero" in {
        val segments: ListBuffer[Segment] =
          (1 to 10).flatMap({
            i =>
              if (i % 2 == 0)
                Some(
                  TestSegment(
                    Slice(
                      Memory.put(i, i, Some(expiredDeadline())),
                      Memory.put(i + 1, i + 1, Some(expiredDeadline()))
                    ).toTransient
                  ).get
                )
              else
                None
          })(collection.breakOut)

        val level = mock[NextLevel]("level")
        level.hasNextLevel _ expects() returns true repeat 6.times
        level.segmentsInLevel _ expects() returning segments repeat 5.times

        (level.refresh(_: Segment)(_: ExecutionContext)) expects(*, *) onCall {
          (segment: Segment, _) =>
            segments find (_.path == segment.path) shouldBe defined
            segments -= segment
            Right(segment.delete)
        } repeat 5.times

        Compaction.runLastLevelCompaction(
          level = level,
          checkExpired = true,
          remainingCompactions = 5,
          segmentsCompacted = 0
        ) shouldBe IO.Success(5)
      }
    }

    "invoke collapse" when {
      "checkExpired is false" in {
        val segments: ListBuffer[Segment] =
          (1 to 10).flatMap({
            i =>
              if (i % 2 == 0)
                Some(
                  TestSegment(
                    Slice(
                      Memory.put(i, i, Some(expiredDeadline())),
                      Memory.put(i + 1, i + 1, Some(expiredDeadline()))
                    ).toTransient
                  ).get
                )
              else
                None
          })(collection.breakOut)

        val level = mock[NextLevel]("level")
        level.hasNextLevel _ expects() returns true repeat 3.times
        level.takeSmallSegments _ expects * onCall {
          count: Int =>
            segments.take(count)
        } repeat 2.times

        (level.collapse(_: Iterable[Segment])(_: ExecutionContext)) expects(*, *) onCall {
          (segmentsToCollapse: Iterable[Segment], _) =>
            segmentsToCollapse foreach (segment => segments find (_.path == segment.path) shouldBe defined)
            segments --= segmentsToCollapse
            Right(IO(segmentsToCollapse.size))
        } repeat 2.times

        Compaction.runLastLevelCompaction(
          level = level,
          checkExpired = false,
          remainingCompactions = 5,
          segmentsCompacted = 0
        ) shouldBe IO.Success(5)
      }
    }
  }
}
