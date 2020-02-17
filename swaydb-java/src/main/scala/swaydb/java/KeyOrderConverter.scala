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

package swaydb.java

import swaydb.data.order.KeyOrder
import swaydb.data.slice.Slice
import swaydb.java.data.slice.ByteSlice
import swaydb.serializers.Serializer

protected object KeyOrderConverter {

  def toScalaKeyOrder[K](comparatorIO: IO[KeyComparator[ByteSlice], KeyComparator[K]],
                         keySerializer: Serializer[K]) =
    if (comparatorIO.isRight)
      new KeyOrder[Slice[Byte]] {
        val comparator = comparatorIO.getRight

        override def compare(left: Slice[Byte], right: Slice[Byte]): Int = {
          val leftKey = keySerializer.read(left)
          val rightKey = keySerializer.read(right)
          comparator.compare(leftKey, rightKey)
        }

        private[swaydb] override def comparableKey(data: Slice[Byte]): Slice[Byte] =
          data
      }
    else
      new KeyOrder[Slice[Byte]] {
        val comparator = comparatorIO.getLeft
        override def compare(x: Slice[Byte], y: Slice[Byte]): Int =
          comparator.compare(ByteSlice(x), ByteSlice(y))

        private[swaydb] override def comparableKey(data: Slice[Byte]): Slice[Byte] =
          data
      }
}
