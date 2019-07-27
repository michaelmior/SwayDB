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

package swaydb.core.map.serializer

import swaydb.Error.IO.ErrorHandler
import swaydb.IO
import swaydb.IO._
import swaydb.core.data.{Time, Value}
import swaydb.core.io.reader.Reader
import swaydb.core.util.Bytes
import swaydb.core.util.TimeUtil._
import swaydb.data.slice.{Reader, Slice}
import swaydb.data.util.ByteSizeOf

import scala.annotation.implicitNotFound
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Deadline

@implicitNotFound("Type class implementation not found for ValueSerializer of type ${T}")
sealed trait ValueSerializer[T] {

  def write(value: T, bytes: Slice[Byte]): Unit

  def read(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, T]

  def read(bytes: Slice[Byte]): IO[swaydb.Error.IO, T] =
    read(Reader(bytes))

  def bytesRequired(value: T): Int
}

object ValueSerializer {

  def readDeadline(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, Option[Deadline]] =
    reader.readLongUnsigned() map {
      deadline =>
        if (deadline == 0)
          None
        else
          deadline.toDeadlineOption
    }

  def readTime(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, Time] =
    reader.readIntUnsigned() flatMap {
      timeSize =>
        if (timeSize == 0)
          Time.successEmpty
        else
          reader.read(timeSize) map (Time(_))
    }

  def readRemainingTime(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, Time] =
    reader.readRemaining() map {
      remaining =>
        if (remaining.isEmpty)
          Time.empty
        else
          Time(remaining)
    }

  def readValue(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, Option[Slice[Byte]]] =
    reader.readRemaining() map {
      remaining =>
        if (remaining.isEmpty)
          None
        else
          Some(remaining)
    }

  implicit object ValuePutSerializer extends ValueSerializer[Value.Put] {

    override def write(value: Value.Put, bytes: Slice[Byte]): Unit =
      bytes
        .addLongUnsigned(value.deadline.toNanos)
        .addIntUnsigned(value.time.size)
        .addAll(value.time.time)
        .addAll(value.value.getOrElse(Slice.emptyBytes))

    override def bytesRequired(value: Value.Put): Int =
      Bytes.sizeOf(value.deadline.toNanos) +
        Bytes.sizeOf(value.time.size) +
        value.time.size +
        value.value.map(_.size).getOrElse(0)

    override def read(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, Value.Put] =
      for {
        deadline <- readDeadline(reader)
        time <- readTime(reader)
        value <- readValue(reader)
      } yield {
        Value.Put(value, deadline, time)
      }
  }

  implicit object ValueUpdateSerializer extends ValueSerializer[Value.Update] {

    override def write(value: Value.Update, bytes: Slice[Byte]): Unit =
      bytes
        .addLongUnsigned(value.deadline.toNanos)
        .addIntUnsigned(value.time.size)
        .addAll(value.time.time)
        .addAll(value.value.getOrElse(Slice.emptyBytes))

    override def bytesRequired(value: Value.Update): Int =
      Bytes.sizeOf(value.deadline.toNanos) +
        Bytes.sizeOf(value.time.size) +
        value.time.size +
        value.value.map(_.size).getOrElse(0)

    override def read(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, Value.Update] =
      for {
        deadline <- readDeadline(reader)
        time <- readTime(reader)
        value <- readValue(reader)
      } yield {
        Value.Update(value, deadline, time)
      }
  }

  implicit object ValueRemoveSerializer extends ValueSerializer[Value.Remove] {

    override def write(value: Value.Remove, bytes: Slice[Byte]): Unit =
      bytes
        .addLongUnsigned(value.deadline.toNanos)
        .addAll(value.time.time)

    override def bytesRequired(value: Value.Remove): Int =
      Bytes.sizeOf(value.deadline.toNanos) +
        value.time.size

    override def read(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, Value.Remove] =
      for {
        deadline <- readDeadline(reader)
        time <- readRemainingTime(reader)
      } yield {
        Value.Remove(deadline, time)
      }
  }

  implicit object ValueFunctionSerializer extends ValueSerializer[Value.Function] {
    override def write(value: Value.Function, bytes: Slice[Byte]): Unit =
      ValueSerializer.write((value.function, value.time.time))(bytes)(TupleOfBytesSerializer)

    override def bytesRequired(value: Value.Function): Int =
      ValueSerializer.bytesRequired((value.function, value.time.time))(TupleOfBytesSerializer)

    override def read(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, Value.Function] =
      ValueSerializer.read[(Slice[Byte], Slice[Byte])](reader) map {
        case (function, time) =>
          Value.Function(function, Time(time))
      }
  }

  implicit object ValueSliceApplySerializer extends ValueSerializer[Slice[Value.Apply]] {

    override def write(applies: Slice[Value.Apply], bytes: Slice[Byte]): Unit = {
      bytes.addIntUnsigned(applies.size)
      applies foreach {
        case value: Value.Update =>
          val bytesRequired = ValueSerializer.bytesRequired(value)
          ValueSerializer.write(value)(bytes.addIntUnsigned(0).addIntUnsigned(bytesRequired))

        case value: Value.Function =>
          val bytesRequired = ValueSerializer.bytesRequired(value)
          ValueSerializer.write(value)(bytes.addIntUnsigned(1).addIntUnsigned(bytesRequired))

        case value: Value.Remove =>
          val bytesRequired = ValueSerializer.bytesRequired(value)
          ValueSerializer.write(value)(bytes.addIntUnsigned(2).addIntUnsigned(bytesRequired))
      }
    }

    override def bytesRequired(value: Slice[Value.Apply]): Int =
    //also add the total number of entries.
      value.foldLeft(Bytes.sizeOf(value.size)) {
        case (total, function) =>
          function match {
            case value: Value.Update =>
              val bytesRequired = ValueSerializer.bytesRequired(value)
              total + Bytes.sizeOf(0) + Bytes.sizeOf(bytesRequired) + bytesRequired

            case value: Value.Function =>
              val bytesRequired = ValueSerializer.bytesRequired(value)
              total + Bytes.sizeOf(1) + Bytes.sizeOf(bytesRequired) + bytesRequired

            case value: Value.Remove =>
              val bytesRequired = ValueSerializer.bytesRequired(value)
              total + Bytes.sizeOf(2) + Bytes.sizeOf(bytesRequired) + bytesRequired
          }
      }

    override def read(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, Slice[Value.Apply]] =
      reader.readIntUnsigned() flatMap {
        count =>
          reader.foldLeftIO(Slice.create[Value.Apply](count)) {
            case (applies, reader) =>
              reader.readIntUnsigned() flatMap {
                id =>
                  reader.readIntUnsignedBytes() flatMap {
                    bytes =>
                      if (id == 0)
                        ValueSerializer.read[Value.Update](Reader(bytes)) map {
                          update =>
                            applies add update
                            applies
                        }
                      else if (id == 1)
                        ValueSerializer.read[Value.Function](Reader(bytes)) map {
                          update =>
                            applies add update
                            applies
                        }
                      else if (id == 2)
                        ValueSerializer.read[Value.Remove](Reader(bytes)) map {
                          update =>
                            applies add update
                            applies
                        }
                      else
                        IO.failed(s"Invalid id:$id")
                  }
              }
          }
      }
  }

  implicit object ValuePendingApplySerializer extends ValueSerializer[Value.PendingApply] {

    override def write(value: Value.PendingApply, bytes: Slice[Byte]): Unit =
      ValueSerializer.write(value.applies)(bytes)

    override def bytesRequired(value: Value.PendingApply): Int =
      ValueSerializer.bytesRequired(value.applies)

    override def read(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, Value.PendingApply] =
      ValueSerializer.read[Slice[Value.Apply]](reader) map Value.PendingApply
  }

  /**
   * Serializer for a tuple of Option bytes and sequence bytes.
   */
  implicit object SeqOfBytesSerializer extends ValueSerializer[Seq[Slice[Byte]]] {

    override def write(values: Seq[Slice[Byte]], bytes: Slice[Byte]): Unit =
      values foreach {
        value =>
          bytes
            .addIntUnsigned(value.size)
            .addAll(value)
      }

    override def bytesRequired(values: Seq[Slice[Byte]]): Int =
      values.foldLeft(0) {
        case (size, valueBytes) =>
          size + Bytes.sizeOf(valueBytes.size) + valueBytes.size
      }

    override def read(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, Seq[Slice[Byte]]] =
      reader.foldLeftIO(ListBuffer.empty[Slice[Byte]]) {
        case (result, reader) =>
          reader.readIntUnsigned() flatMap {
            size =>
              reader.read(size) map {
                bytes =>
                  result += bytes
              }
          }
      }
  }

  /**
   * Serializer for a tuple of Option bytes and sequence bytes.
   */
  implicit object TupleOfBytesSerializer extends ValueSerializer[(Slice[Byte], Slice[Byte])] {

    override def write(value: (Slice[Byte], Slice[Byte]), bytes: Slice[Byte]): Unit =
      SeqOfBytesSerializer.write(Seq(value._1, value._2), bytes)

    override def bytesRequired(value: (Slice[Byte], Slice[Byte])): Int =
      SeqOfBytesSerializer.bytesRequired(Seq(value._1, value._2))

    override def read(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, (Slice[Byte], Slice[Byte])] =
      SeqOfBytesSerializer.read(reader) flatMap {
        bytes =>
          if (bytes.size != 2)
            IO.failed(TupleOfBytesSerializer.getClass.getSimpleName + s".read did not return a tuple. Size = ${bytes.size}")
          else
            IO.Success(bytes.head, bytes.last)
      }
  }

  /**
   * Serializer for a tuple of Option bytes and sequence bytes.
   */
  implicit object TupleBytesAndOptionBytesSerializer extends ValueSerializer[(Slice[Byte], Option[Slice[Byte]])] {

    override def write(value: (Slice[Byte], Option[Slice[Byte]]), bytes: Slice[Byte]): Unit =
      value._2 match {
        case Some(second) =>
          bytes.addIntSigned(1)
          ValueSerializer.write[(Slice[Byte], Slice[Byte])]((value._1, second))(bytes)
        case None =>
          bytes.addIntSigned(0)
          bytes.addAll(value._1)
      }

    override def bytesRequired(value: (Slice[Byte], Option[Slice[Byte]])): Int =
      value._2 match {
        case Some(second) =>
          1 +
            ValueSerializer.bytesRequired[(Slice[Byte], Slice[Byte])](value._1, second)
        case None =>
          1 +
            value._1.size
      }

    override def read(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, (Slice[Byte], Option[Slice[Byte]])] =
      reader.readIntUnsigned() flatMap {
        id =>
          if (id == 0)
            reader.readRemaining() map {
              all =>
                (all, None)
            }
          else
            ValueSerializer.read[(Slice[Byte], Slice[Byte])](reader) map {
              case (left, right) =>
                (left, Some(right))
            }
      }
  }

  /**
   * Serializer for a tuple of Option bytes and sequence bytes.
   */
  implicit object IntMapListBufferSerializer extends ValueSerializer[mutable.Map[Int, Iterable[(Slice[Byte], Slice[Byte])]]] {
    val formatId = 0.toByte

    override def write(map: mutable.Map[Int, Iterable[(Slice[Byte], Slice[Byte])]], bytes: Slice[Byte]): Unit = {
      bytes add formatId
      map foreach {
        case (int, tuples) =>
          bytes addIntUnsigned int
          bytes addIntUnsigned tuples.size
          tuples foreach {
            case (left, right) =>
              bytes addIntUnsigned left.size
              bytes addAll left
              bytes addIntUnsigned right.size
              bytes addAll right
          }
      }
    }

    override def read(reader: Reader[swaydb.Error.IO]): IO[swaydb.Error.IO, mutable.Map[Int, Iterable[(Slice[Byte], Slice[Byte])]]] =
      reader.get() flatMap {
        format =>
          if (format != formatId)
            IO.Failure(swaydb.Error.Unknown(new Exception(s"Invalid formatID: $format")))
          else
            reader.foldLeftIO(mutable.Map.empty[Int, Iterable[(Slice[Byte], Slice[Byte])]]) {
              case (map, reader) =>
                reader.readIntUnsigned() flatMap {
                  int =>
                    reader.readIntUnsigned() flatMap {
                      tuplesCount =>
                        (1 to tuplesCount) mapIO {
                          _ =>
                            for {
                              leftSize <- reader.readIntUnsigned()
                              left <- reader.read(leftSize)
                              rightSize <- reader.readIntUnsigned()
                              right <- reader.read(rightSize)
                            } yield {
                              (left, right)
                            }
                        } map {
                          bytes =>
                            map.put(int, bytes)
                            map
                        }
                    }
                }
            }
      }

    /**
     * Calculates the number of bytes required with minimal information about the RangeFilter.
     */
    def optimalBytesRequired(numberOfRanges: Int,
                             maxUncommonBytesToStore: Int,
                             rangeFilterCommonPrefixes: Iterable[Int]): Int =
      ByteSizeOf.byte + //formatId
        rangeFilterCommonPrefixes.foldLeft(0)(_ + Bytes.sizeOf(_)) + //common prefix bytes sizes
        //Bytes.sizeOf(numberOfRanges) because there can only be a max of numberOfRanges per group so ByteSizeOf.int is not required.
        (Bytes.sizeOf(numberOfRanges) * rangeFilterCommonPrefixes.size) + //tuples count per common prefix count
        (numberOfRanges * Bytes.sizeOf(maxUncommonBytesToStore) * 2) +
        (numberOfRanges * maxUncommonBytesToStore * 2) //store the bytes itself, * 2 because it's a tuple.


    /**
     * This is not currently used by RangeFilter, [[optimalBytesRequired]] is used instead
     * for faster calculation without long iterations. The size is almost always accurate and very rarely adds a few extra bytes.
     * See tests.
     */
    override def bytesRequired(map: mutable.Map[Int, Iterable[(Slice[Byte], Slice[Byte])]]): Int =
      map.foldLeft(ByteSizeOf.byte) {
        case (totalSize, (int, tuples)) =>
          Bytes.sizeOf(int) +
            Bytes.sizeOf(tuples.size) +
            tuples.foldLeft(0) {
              case (totalSize, (left, right)) =>
                Bytes.sizeOf(left.size) +
                  left.size +
                  Bytes.sizeOf(right.size) +
                  right.size +
                  totalSize
            } + totalSize
      }
  }

  def writeBytes[T](value: T)(implicit serializer: ValueSerializer[T]): Slice[Byte] = {
    val bytesRequired = ValueSerializer.bytesRequired(value)
    val bytes = Slice.create[Byte](bytesRequired)
    serializer.write(value, bytes)
    bytes
  }

  def write[T](value: T)(bytes: Slice[Byte])(implicit serializer: ValueSerializer[T]): Unit =
    serializer.write(value, bytes)

  def read[T](value: Slice[Byte])(implicit serializer: ValueSerializer[T]): IO[swaydb.Error.IO, T] =
    serializer.read(value)

  def read[T](reader: Reader[swaydb.Error.IO])(implicit serializer: ValueSerializer[T]): IO[swaydb.Error.IO, T] =
    serializer.read(reader)

  def bytesRequired[T](value: T)(implicit serializer: ValueSerializer[T]): Int =
    serializer.bytesRequired(value)
}
