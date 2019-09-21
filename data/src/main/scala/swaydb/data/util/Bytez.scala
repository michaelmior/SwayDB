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

package swaydb.data.util

import java.nio.charset.Charset

import swaydb.IO
import swaydb.data.slice.{ReaderBase, Slice}

private[swaydb] trait Bytez {

  def writeInt(int: Int, slice: Slice[Byte]): Unit = {
    slice add (int >>> 24).toByte
    slice add (int >>> 16).toByte
    slice add (int >>> 8).toByte
    slice add int.toByte
  }

  def readInt[E >: swaydb.Error.IO : IO.ExceptionHandler](reader: ReaderBase[E]): IO[E, Int] =
    reader.read(ByteSizeOf.int) map readInt

  def readInt(bytes: Slice[Byte]): Int =
    bytes(0).toInt << 24 |
      (bytes(1) & 0xff) << 16 |
      (bytes(2) & 0xff) << 8 |
      bytes(3) & 0xff

  def writeLong(long: Long, slice: Slice[Byte]): Unit = {
    slice add (long >>> 56).toByte
    slice add (long >>> 48).toByte
    slice add (long >>> 40).toByte
    slice add (long >>> 32).toByte
    slice add (long >>> 24).toByte
    slice add (long >>> 16).toByte
    slice add (long >>> 8).toByte
    slice add long.toByte
  }

  def readLong(bytes: Slice[Byte]): Long =
    (bytes(0).toLong << 56) |
      ((bytes(1) & 0xffL) << 48) |
      ((bytes(2) & 0xffL) << 40) |
      ((bytes(3) & 0xffL) << 32) |
      ((bytes(4) & 0xffL) << 24) |
      ((bytes(5) & 0xffL) << 16) |
      ((bytes(6) & 0xffL) << 8) |
      bytes(7) & 0xffL

  def readLong[E >: swaydb.Error.IO : IO.ExceptionHandler](reader: ReaderBase[E]): IO[E, Long] =
    reader.read(ByteSizeOf.long) map readLong

  def readBoolean[E >: swaydb.Error.IO : IO.ExceptionHandler](reader: ReaderBase[E]): IO[E, Boolean] =
    reader.get() flatMap {
      byte =>
        if (byte == 1)
          IO.`true`
        else
          IO.`false`
    }

  def readString[E >: swaydb.Error.IO : IO.ExceptionHandler](reader: ReaderBase[E], charset: Charset): IO[E, String] =
    reader.size flatMap {
      size =>
        reader.read((size - reader.getPosition).toInt) map (readString(_, charset))
    }

  def readString[E >: swaydb.Error.IO : IO.ExceptionHandler](size: Int,
                                                             reader: ReaderBase[E],
                                                             charset: Charset): IO[E, String] =
    reader.read(size) map (readString(_, charset))

  //TODO - readString is expensive. If the slice bytes are a sub-slice of another other Slice a copy of the array will be created.
  def readString(slice: Slice[Byte], charset: Charset): String =
    new String(slice.toArray, charset)

  def writeString(string: String,
                  bytes: Slice[Byte],
                  charsets: Charset): Slice[Byte] =
    bytes addAll string.getBytes(charsets)

  /** **************************************************
   * Duplicate functions here. This code
   * is crucial for read performance and the most frequently used.
   * Creating reader on each read will be expensive therefore the functions are repeated
   * for slice and reader.
   *
   * Need to re-evaluate this code and see if abstract functions can be used.
   * ************************************************/

  def writeSignedInt(x: Int, slice: Slice[Byte]): Unit =
    writeUnsignedInt((x << 1) ^ (x >> 31), slice)

  def readSignedInt[E >: swaydb.Error.IO : IO.ExceptionHandler](reader: ReaderBase[E]): IO[E, Int] =
    readUnsignedInt(reader) map {
      unsigned =>
        //Credit - https://github.com/larroy/varint-scala
        // undo even odd mapping
        val tmp = (((unsigned << 31) >> 31) ^ unsigned) >> 1
        // restore sign
        tmp ^ (unsigned & (1 << 31))
    }

  def readSignedInt[E >: swaydb.Error.IO : IO.ExceptionHandler](slice: Slice[Byte]): IO[E, Int] =
    readUnsignedInt(slice) map {
      unsigned =>
        //Credit - https://github.com/larroy/varint-scala
        // undo even odd mapping
        val tmp = (((unsigned << 31) >> 31) ^ unsigned) >> 1
        // restore sign
        tmp ^ (unsigned & (1 << 31))
    }

  def writeUnsignedInt(int: Int, slice: Slice[Byte]): Unit = {
    if (int > 0x0FFFFFFF || int < 0) slice.add((0x80 | int >>> 28).asInstanceOf[Byte])
    if (int > 0x1FFFFF || int < 0) slice.add((0x80 | ((int >>> 21) & 0x7F)).asInstanceOf[Byte])
    if (int > 0x3FFF || int < 0) slice.add((0x80 | ((int >>> 14) & 0x7F)).asInstanceOf[Byte])
    if (int > 0x7F || int < 0) slice.add((0x80 | ((int >>> 7) & 0x7F)).asInstanceOf[Byte])

    slice.add((int & 0x7F).asInstanceOf[Byte])
  }

  def writeUnsignedIntNonZero(int: Int): Slice[Byte] = {
    val slice = Slice.create[Byte](ByteSizeOf.varInt)
    var x = int
    while ((x & 0xFFFFF80) != 0L) {
      slice add ((x & 0x7F) | 0x80).toByte
      x >>>= 7
    }
    slice add (x & 0x7F).toByte
    slice.close()
  }

  def readUnsignedIntNonZero[E >: swaydb.Error.IO : IO.ExceptionHandler](slice: Slice[Byte]): IO[E, Int] =
    IO {
      var index = 0
      var i = 0
      var int = 0
      var read = 0
      do {
        read = slice(index)
        int |= (read & 0x7F) << i
        i += 7
        index += 1
        require(i <= 35)
      } while ((read & 0x80) != 0)

      int
    }

  def readUnsignedIntNonZeroWithByteSize[E >: swaydb.Error.IO : IO.ExceptionHandler](slice: Slice[Byte]): IO[E, (Int, Int)] =
    IO {
      var index = 0
      var i = 0
      var int = 0
      var read = 0
      do {
        read = slice(index)
        int |= (read & 0x7F) << i
        i += 7
        index += 1
        require(i <= 35)
      } while ((read & 0x80) != 0)

      (int, index)
    }

  def writeUnsignedIntReversed(int: Int): Slice[Byte] = {
    val slice = Slice.create[Byte](ByteSizeOf.varInt)

    slice.add((int & 0x7F).asInstanceOf[Byte])

    if (int > 0x7F || int < 0) slice.add((0x80 | ((int >>> 7) & 0x7F)).asInstanceOf[Byte])
    if (int > 0x3FFF || int < 0) slice.add((0x80 | ((int >>> 14) & 0x7F)).asInstanceOf[Byte])
    if (int > 0x1FFFFF || int < 0) slice.add((0x80 | ((int >>> 21) & 0x7F)).asInstanceOf[Byte])
    if (int > 0x0FFFFFFF || int < 0) slice.add((0x80 | int >>> 28).asInstanceOf[Byte])

    slice
  }

  def readUnsignedInt[E >: swaydb.Error.IO : IO.ExceptionHandler](reader: ReaderBase[E]): IO[E, Int] = {
    val beforeReadPosition = reader.getPosition
    reader.read(ByteSizeOf.varInt) map {
      slice =>
        var index = 0
        var byte = slice(index)
        var int: Int = byte & 0x7F

        while ((byte & 0x80) != 0) {
          index += 1
          byte = slice(index)

          int <<= 7
          int |= (byte & 0x7F)
        }

        reader.moveTo(beforeReadPosition + index + 1)
        int
    }
  }

  def readUnsignedInt[E >: swaydb.Error.IO : IO.ExceptionHandler](slice: Slice[Byte]): IO[E, Int] =
    IO(readUnsignedIntUnsafe(slice))

  def readUnsignedIntUnsafe[E >: swaydb.Error.IO : IO.ExceptionHandler](slice: Slice[Byte]): Int = {
    var index = 0
    var byte = slice(index)
    var int: Int = byte & 0x7F
    while ((byte & 0x80) != 0) {
      index += 1
      byte = slice(index)

      int <<= 7
      int |= (byte & 0x7F)
    }
    int
  }

  def readUnsignedIntWithByteSize[E >: swaydb.Error.IO : IO.ExceptionHandler](slice: Slice[Byte]): IO[E, (Int, Int)] =
    IO(readUnsignedIntWithByteSizeUnsafe(slice))

  def readUnsignedIntWithByteSizeUnsafe[E >: swaydb.Error.IO : IO.ExceptionHandler](slice: Slice[Byte]): (Int, Int) = {
    var index = 0
    var byte = slice(index)
    var int: Int = byte & 0x7F

    while ((byte & 0x80) != 0) {
      index += 1
      byte = slice(index)

      int <<= 7
      int |= (byte & 0x7F)
    }

    (int, index + 1)
  }

  /**
   * @return Tuple where the first integer is the unsigned integer and the second is the number of bytes read.
   */
  def readLastUnsignedInt[E >: swaydb.Error.IO : IO.ExceptionHandler](slice: Slice[Byte]): IO[E, (Int, Int)] =
    IO(readLastUnsignedIntUnsafe(slice))

  def readLastUnsignedIntUnsafe(slice: Slice[Byte]): (Int, Int) = {
    var index = slice.size - 1
    var byte = slice(index)
    var int: Int = byte & 0x7F

    while ((byte & 0x80) != 0) {
      index -= 1
      byte = slice(index)

      int <<= 7
      int |= (byte & 0x7F)
    }

    (int, slice.size - index)
  }

  def writeSignedLong(long: Long, slice: Slice[Byte]): Unit =
    writeUnsignedLong((long << 1) ^ (long >> 63), slice)

  def readSignedLong[E >: swaydb.Error.IO : IO.ExceptionHandler](reader: ReaderBase[E]): IO[E, Long] =
    readUnsignedLong(reader) map {
      unsigned =>
        // undo even odd mapping
        val tmp = (((unsigned << 63) >> 63) ^ unsigned) >> 1
        // restore sign
        tmp ^ (unsigned & (1L << 63))
    }

  def readSignedLong[E >: swaydb.Error.IO : IO.ExceptionHandler](slice: Slice[Byte]): IO[E, Long] =
    readUnsignedLong(slice) map {
      unsigned =>
        // undo even odd mapping
        val tmp = (((unsigned << 63) >> 63) ^ unsigned) >> 1
        // restore sign
        tmp ^ (unsigned & (1L << 63))
    }

  def writeUnsignedLong(long: Long, slice: Slice[Byte]): Unit = {
    if (long < 0) slice.add(0x81.toByte)
    if (long > 0xFFFFFFFFFFFFFFL || long < 0) slice.add((0x80 | ((long >>> 56) & 0x7FL)).asInstanceOf[Byte])
    if (long > 0x1FFFFFFFFFFFFL || long < 0) slice.add((0x80 | ((long >>> 49) & 0x7FL)).asInstanceOf[Byte])
    if (long > 0x3FFFFFFFFFFL || long < 0) slice.add((0x80 | ((long >>> 42) & 0x7FL)).asInstanceOf[Byte])
    if (long > 0x7FFFFFFFFL || long < 0) slice.add((0x80 | ((long >>> 35) & 0x7FL)).asInstanceOf[Byte])
    if (long > 0xFFFFFFFL || long < 0) slice.add((0x80 | ((long >>> 28) & 0x7FL)).asInstanceOf[Byte])
    if (long > 0x1FFFFFL || long < 0) slice.add((0x80 | ((long >>> 21) & 0x7FL)).asInstanceOf[Byte])
    if (long > 0x3FFFL || long < 0) slice.add((0x80 | ((long >>> 14) & 0x7FL)).asInstanceOf[Byte])
    if (long > 0x7FL || long < 0) slice.add((0x80 | ((long >>> 7) & 0x7FL)).asInstanceOf[Byte])

    slice.add((long & 0x7FL).asInstanceOf[Byte])
  }

  def readUnsignedLong[E >: swaydb.Error.IO : IO.ExceptionHandler](reader: ReaderBase[E]): IO[E, Long] = {
    val beforeReadPosition = reader.getPosition
    reader.read(ByteSizeOf.varLong) map {
      slice =>
        var index = 0
        var byte = slice(index)
        var long: Long = byte & 0x7F

        while ((byte & 0x80) != 0) {
          index += 1
          byte = slice(index)

          long <<= 7
          long |= (byte & 0x7F)
        }

        reader.moveTo(beforeReadPosition + index + 1)
        long
    }
  }

  def readUnsignedLong[E >: swaydb.Error.IO : IO.ExceptionHandler](slice: Slice[Byte]): IO[E, Long] =
    IO(readUnsignedLongUnsafe(slice))

  def readUnsignedLongUnsafe(slice: Slice[Byte]): Long = {
    var index = 0
    var byte = slice(index)
    var long: Long = byte & 0x7F

    while ((byte & 0x80) != 0) {
      index += 1
      byte = slice(index)

      long <<= 7
      long |= (byte & 0x7F)
    }

    long
  }

  def readUnsignedLongWithByteSize[E >: swaydb.Error.IO : IO.ExceptionHandler](slice: Slice[Byte]): IO[E, (Long, Int)] =
    IO(readUnsignedLongWithByteSizeUnsafe(slice))

  def readUnsignedLongWithByteSizeUnsafe(slice: Slice[Byte]): (Long, Int) = {
    var index = 0
    var byte = slice(index)
    var long: Long = byte & 0x7F

    while ((byte & 0x80) != 0) {
      index += 1
      byte = slice(index)

      long <<= 7
      long |= (byte & 0x7F)
    }

    (long, index + 1)
  }
}

private[swaydb] object Bytez extends Bytez
