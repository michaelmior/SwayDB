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

package swaydb.core.log

import com.typesafe.scalalogging.LazyLogging
import swaydb.config.{ForceSave, MMAP}

import java.nio.file.Path

protected class MemoryLog[K, V, C <: LogCache[K, V]](val cache: C,
                                                     flushOnOverflow: Boolean,
                                                     val fileSize: Int) extends Log[K, V, C] with LazyLogging {

  private var currentBytesWritten: Long = 0
  var skipListKeyValuesMaxCount: Int = 0

  override val uniqueFileNumber: Long =
    Log.uniqueFileNumberGenerator.next

  def delete: Unit = ()

  override def writeSync(entry: LogEntry[K, V]): Boolean =
    synchronized(writeNoSync(entry))

  override def writeNoSync(entry: LogEntry[K, V]): Boolean = {
    val entryTotalByteSize = entry.totalByteSize
    if (flushOnOverflow || currentBytesWritten == 0 || ((currentBytesWritten + entryTotalByteSize) <= fileSize)) {
      cache.writeAtomic(entry)
      skipListKeyValuesMaxCount += entry.entriesCount
      currentBytesWritten += entryTotalByteSize
      true
    } else {
      false
    }
  }

  override def mmap: MMAP.Log =
    MMAP.Off(ForceSave.Off)

  override def close(): Unit =
    ()

  override def exists: Boolean =
    true

  override def pathOption: Option[Path] =
    None
}
