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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with SwayDB. If not, see <https://www.gnu.org/licenses/>.
 */

package swaydb.data.slice

import java.nio.charset.{Charset, StandardCharsets}

/**
  * Wrapper around SliceReader to perform eager fetches.
  *
  * SliceReader is used internally and should not be exposed to the API.
  */
class BytesReader(slice: Slice[Byte]) {

  private val sliceReader = new SliceReader(slice)

  val size: Long =
    sliceReader.size.unsafeGet

  def hasAtLeast(size: Long): Boolean =
    sliceReader.hasAtLeast(size).unsafeGet

  def read(size: Int): Slice[Byte] =
    sliceReader.read(size).unsafeGet

  def moveTo(newPosition: Long): BytesReader = {
    sliceReader.moveTo(newPosition)
    this
  }

  def get(): Int =
    sliceReader.get().unsafeGet

  def hasMore: Boolean =
    sliceReader.hasMore.unsafeGet

  def getPosition: Int =
    sliceReader.getPosition

  def resetPosition(): BytesReader =
    new BytesReader(slice)

  def skip(skip: Long): BytesReader = {
    sliceReader.skip(skip)
    this
  }

  def readInt(): Int =
    sliceReader.readInt().unsafeGet

  def readIntUnsigned(): Int =
    sliceReader.readIntUnsigned().unsafeGet

  def readIntSigned(): Int =
    sliceReader.readIntSigned().unsafeGet

  def readLong(): Long =
    sliceReader.readLong().unsafeGet

  def readLongUnsigned(): Long =
    sliceReader.readLongUnsigned().unsafeGet

  def readLongSigned(): Long =
    sliceReader.readLongSigned().unsafeGet

  def remaining(): Long =
    sliceReader.remaining.unsafeGet

  def readRemaining(): Slice[Byte] =
    sliceReader.readRemaining().unsafeGet

  /**
    * Note: This function does not operate on the original Slice if the String being read is a
    * sub-slice of another Slice. Another copy of the Array that represent the String's bytes will be created.
    *
    * This is because
    * {{{
    *   new String(array, offset, size, charset)
    * }}}
    * requires an Array and not an Iterable. Slice currently does not expose the internal Array.
    */
  def readRemainingAsString(charset: Charset = StandardCharsets.UTF_8): String =
    sliceReader.readRemainingAsString(charset).unsafeGet

  def readString(size: Int, charset: Charset = StandardCharsets.UTF_8): String =
    sliceReader.readString(size, charset).unsafeGet

}