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

package swaydb.core.map.timer

import java.nio.file.Path

import swaydb.IO
import swaydb.core.actor.ByteBufferSweeper.ByteBufferSweeperActor
import swaydb.core.data.Time
import swaydb.core.io.file.ForceSaveApplier
import swaydb.core.map.MapEntry
import swaydb.core.map.counter.Counter
import swaydb.core.map.serializer.{MapEntryReader, MapEntryWriter}
import swaydb.data.config.MMAP
import swaydb.data.slice.Slice
import swaydb.data.slice.Slice._

private[core] trait Timer {
  val isEmptyTimer: Boolean

  def next: Time

  def close: Unit
}

private[core] object Timer {
  val defaultKey = Slice.emptyBytes

  def memory(): Timer =
    new Timer {
      val memory = Counter.memory()

      override val isEmptyTimer: Boolean =
        false

      override def next: Time =
        Time(memory.next)

      override def close: Unit =
        memory.close
    }

  def empty: Timer =
    new Timer {
      override val isEmptyTimer: Boolean =
        true

      override val next: Time =
        Time.empty

      override val close: Unit =
        ()
    }

  def persistent(path: Path,
                 mmap: MMAP.Map,
                 mod: Long,
                 flushCheckpointSize: Long)(implicit bufferCleaner: ByteBufferSweeperActor,
                                            forceSaveApplier: ForceSaveApplier,
                                            writer: MapEntryWriter[MapEntry.Put[Sliced[Byte], Sliced[Byte]]],
                                            reader: MapEntryReader[MapEntry[Sliced[Byte], Sliced[Byte]]]): IO[swaydb.Error.Map, Timer] =
    Counter.persistent(
      path = path,
      mmap = mmap,
      mod = mod,
      flushCheckpointSize = flushCheckpointSize
    ) transform {
      counter =>
        new Timer {
          override val isEmptyTimer: Boolean =
            false

          override def next: Time =
            Time(counter.next)

          override def close: Unit =
            counter.close
        }
    }
}
