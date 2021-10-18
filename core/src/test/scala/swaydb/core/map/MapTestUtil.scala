/*
 * Copyright 2018 Simer JS Plaha (simer.j@gmail.com - @simerplaha)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package swaydb.core.map

import org.scalatest.matchers.should.Matchers._
import swaydb.IOValues._
import swaydb.core.TestCaseSweeper._
import swaydb.core.TestData._
import swaydb.core.io.file.ForceSaveApplier
import swaydb.core.map.counter.{CounterMap, PersistentCounterMap}
import swaydb.core.map.serializer.{MapEntryReader, MapEntryWriter}
import swaydb.core.map.timer.Timer
import swaydb.core.map.timer.Timer.PersistentTimer
import swaydb.core.sweeper.ByteBufferSweeper
import swaydb.core.sweeper.ByteBufferSweeper.{ByteBufferSweeperActor, State}
import swaydb.core.{TestCaseSweeper, TestExecutionContext}
import swaydb.data.config.MMAP
import swaydb.data.order.KeyOrder
import swaydb.data.slice.Slice
import swaydb.testkit.RunThis._
import swaydb.utils.OperatingSystem
import swaydb.{Bag, Glass}

import scala.concurrent.duration.DurationInt

object MapTestUtil {

  //windows requires special handling. If a Map was initially opened as a MMAP file
  //we cant reopen without cleaning it first because reopen will re-read the file's content
  //using FileChannel, and flushes it's content to a new file deleting the old File. But if it's
  //was previously opened as MMAP file (in a test) then it cannot be deleted without clean.
  def ensureCleanedForWindows(mmap: MMAP.Map)(implicit bufferCleaner: ByteBufferSweeperActor): Unit =
    if (OperatingSystem.isWindows && mmap.isMMAP) {
      val cleaner = bufferCleaner.actor
      val state = cleaner.receiveAllForce[Glass, State](state => state)
      eventual(30.seconds)(state.pendingClean.isEmpty shouldBe true)
    }

  //cannot be added to TestBase because PersistentMap cannot leave the map package.
  implicit class ReopenMap[K, V, C <: MapCache[K, V]](map: PersistentMap[K, V, C]) {
    def reopen(implicit keyOrder: KeyOrder[K],
               reader: MapEntryReader[MapEntry[K, V]],
               testCaseSweeper: TestCaseSweeper,
               mapCacheBuilder: MapCacheBuilder[C]) = {
      map.close()

      implicit val writer = map.writer
      implicit val forceSaveApplied = map.forceSaveApplier
      implicit val cleaner = map.bufferCleaner
      implicit val sweeper = map.fileSweeper

      ensureCleanedForWindows(map.mmap)

      Map.persistent[K, V, C](
        folder = map.path,
        mmap = MMAP.randomForMap(),
        flushOnOverflow = map.flushOnOverflow,
        fileSize = map.fileSize,
        dropCorruptedTailEntries = false
      ).runRandomIO.right.value.item.sweep()
    }
  }

  implicit class PersistentMapImplicit[K, V](map: PersistentMap[K, V, _]) {
    /**
     * Manages closing of Map accouting for Windows where
     * Memory-mapped files require in-memory ByteBuffer be cleared.
     */
    def ensureClose(): Unit = {
      map.close()
      ensureCleanedForWindows(map.mmap)(map.bufferCleaner)

      implicit val ec = TestExecutionContext.executionContext
      implicit val bag = Bag.future
      val isShut = (map.bufferCleaner.actor ask ByteBufferSweeper.Command.IsTerminated[Unit]).await(10.seconds)
      assert(isShut, "Is not shut")
    }
  }

  //cannot be added to TestBase because PersistentMap cannot leave the map package.
  implicit class ReopenCounter(counter: PersistentCounterMap) {
    def reopen(implicit bufferCleaner: ByteBufferSweeperActor,
               forceSaveApplier: ForceSaveApplier,
               writer: MapEntryWriter[MapEntry.Put[Slice[Byte], Slice[Byte]]],
               reader: MapEntryReader[MapEntry[Slice[Byte], Slice[Byte]]],
               testCaseSweeper: TestCaseSweeper): PersistentCounterMap = {
      counter.close
      ensureCleanedForWindows(counter.mmap)

      CounterMap.persistent(
        dir = counter.path,
        fileSize = counter.fileSize,
        mmap = MMAP.randomForMap(),
        mod = counter.mod
      ).value.sweep()
    }
  }

  implicit class ReopenTimer(timer: PersistentTimer) {
    def reopen(implicit bufferCleaner: ByteBufferSweeperActor,
               forceSaveApplier: ForceSaveApplier,
               writer: MapEntryWriter[MapEntry.Put[Slice[Byte], Slice[Byte]]],
               reader: MapEntryReader[MapEntry[Slice[Byte], Slice[Byte]]],
               testCaseSweeper: TestCaseSweeper): PersistentTimer = {
      timer.close
      ensureCleanedForWindows(timer.counter.mmap)

      val newTimer =
        Timer.persistent(
          path = timer.counter.path.getParent,
          mmap = MMAP.randomForMap(),
          mod = timer.counter.mod,
          fileSize = timer.counter.fileSize
        ).value

      newTimer.counter.sweep()

      newTimer
    }
  }
}
