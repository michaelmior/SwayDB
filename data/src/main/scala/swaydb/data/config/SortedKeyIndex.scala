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
 */

package swaydb.data.config

import swaydb.Compression
import swaydb.data.util.Java.JavaFunction
import scala.jdk.CollectionConverters._

sealed trait SortedKeyIndex
object SortedKeyIndex {

  def enableJava(prefixCompression: PrefixCompression,
                 enablePositionIndex: Boolean,
                 ioStrategy: JavaFunction[IOAction, IOStrategy],
                 compressions: JavaFunction[UncompressedBlockInfo, java.lang.Iterable[Compression]]) =
    Enable(
      prefixCompression = prefixCompression,
      enablePositionIndex = enablePositionIndex,
      ioStrategy = ioStrategy.apply,
      compressions = compressions.apply(_).asScala
    )

  case class Enable(prefixCompression: PrefixCompression,
                    enablePositionIndex: Boolean,
                    ioStrategy: IOAction => IOStrategy,
                    compressions: UncompressedBlockInfo => Iterable[Compression]) extends SortedKeyIndex
}