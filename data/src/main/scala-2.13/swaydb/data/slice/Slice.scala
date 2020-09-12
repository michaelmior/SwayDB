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

package swaydb.data.slice

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

import swaydb.Aggregator
import swaydb.data.order.KeyOrder
import swaydb.data.slice.Slice.Sliced
import swaydb.data.util.{ByteOps, ByteSizeOf, SomeOrNoneCovariant}
import swaydb.data.{MaxKey, slice}

import scala.annotation.tailrec
import scala.annotation.unchecked.uncheckedVariance
import scala.collection._
import scala.reflect.ClassTag

/**
 * Documentation - http://swaydb.io/slice
 */
sealed trait SliceOption[+T] extends SomeOrNoneCovariant[SliceOption[T], Sliced[T]] {
  override def noneC: SliceOption[Nothing] = Slice.Null

  def isUnslicedOption: Boolean

  def asSliceOption(): SliceOption[T]

  def unsliceOption(): SliceOption[T] =
    if (this.isNoneC || this.getC.isEmpty)
      Slice.Null
    else
      this.getC.unslice()
}

object Slice {

  val emptyBytes = Slice.create[Byte](0)

  val emptyJavaBytes = Slice.create[java.lang.Byte](0)

  val someEmptyBytes = Some(emptyBytes)

  private[swaydb] val emptyEmptyBytes: Sliced[Sliced[Byte]] = Slice.empty[Sliced[Byte]]

  @inline final def empty[T: ClassTag] =
    Slice.create[T](0)

  final def range(from: Int, to: Int): Sliced[Int] = {
    val slice = Slice.create[Int](to - from + 1)
    (from to to) foreach slice.add
    slice
  }

  final def range(from: Char, to: Char): Sliced[Char] = {
    val slice = Slice.create[Char](26)
    (from to to) foreach slice.add
    slice.close()
  }

  final def range(from: Byte, to: Byte): Sliced[Byte] = {
    val slice = Slice.create[Byte](to - from + 1)
    (from to to) foreach {
      i =>
        slice add i.toByte
    }
    slice.close()
  }

  def fill[T: ClassTag](length: Int)(elem: => T): Sliced[T] =
    new Sliced[T](
      array = Array.fill(length)(elem),
      fromOffset = 0,
      toOffset = if (length == 0) -1 else length - 1,
      written = length
    )

  def createBytes(length: Int): Sliced[Byte] =
    Slice.create[Byte](length)

  @inline final def create[T: ClassTag](length: Int, isFull: Boolean = false): Sliced[T] =
    new Sliced(
      array = new Array[T](length),
      fromOffset = 0,
      toOffset = if (length == 0) -1 else length - 1,
      written = if (isFull) length else 0
    )

  def apply[T: ClassTag](data: Array[T]): Sliced[T] =
    if (data.length == 0)
      Slice.create[T](0)
    else
      new Sliced[T](
        array = data,
        fromOffset = 0,
        toOffset = data.length - 1,
        written = data.length
      )

  def from[T: ClassTag](iterator: Iterator[T], size: Int): Sliced[T] = {
    val slice = Slice.create[T](size)
    iterator foreach slice.add
    slice
  }

  def from[T: ClassTag](iterator: Iterable[T], size: Int): Sliced[T] = {
    val slice = Slice.create[T](size)
    iterator foreach slice.add
    slice
  }

  def from(byteBuffer: ByteBuffer) =
    new Sliced[Byte](
      array = byteBuffer.array(),
      fromOffset = byteBuffer.arrayOffset(),
      toOffset = byteBuffer.position() - 1,
      written = byteBuffer.position()
    )

  def from(byteBuffer: ByteBuffer, from: Int, to: Int) =
    new Sliced[Byte](
      array = byteBuffer.array(),
      fromOffset = from,
      toOffset = to,
      written = to - from + 1
    )

  @inline final def apply[T: ClassTag](data: T*): Sliced[T] =
    Slice(data.toArray)

  @inline final def writeInt[B: ClassTag](integer: Int)(implicit byteOps: ByteOps[B]): Sliced[B] =
    Slice.create[B](ByteSizeOf.int).addInt(integer)

  @inline final def writeBoolean[B: ClassTag](bool: Boolean)(implicit byteOps: ByteOps[B]): Sliced[B] =
    Slice.create[B](1).addBoolean(bool)

  @inline final def writeUnsignedInt[B: ClassTag](integer: Int)(implicit byteOps: ByteOps[B]): Sliced[B] =
    Slice.create[B](ByteSizeOf.varInt).addUnsignedInt(integer).close()

  @inline final def writeLong[B: ClassTag](num: Long)(implicit byteOps: ByteOps[B]): Sliced[B] =
    Slice.create[B](ByteSizeOf.long).addLong(num)

  @inline final def writeUnsignedLong[B: ClassTag](num: Long)(implicit byteOps: ByteOps[B]): Sliced[B] =
    Slice.create[B](ByteSizeOf.varLong).addUnsignedLong(num).close()

  @inline final def writeString[B](string: String, charsets: Charset = StandardCharsets.UTF_8)(implicit byteOps: ByteOps[B]): Sliced[B] =
    byteOps.writeString(string, charsets)

  @inline final def intersects[T](range1: (Sliced[T], Sliced[T]),
                                  range2: (Sliced[T], Sliced[T]))(implicit ordering: Ordering[Sliced[T]]): Boolean =
    intersects((range1._1, range1._2, true), (range2._1, range2._2, true))

  def within[T](key: Sliced[T],
                minKey: Sliced[T],
                maxKey: MaxKey[Sliced[T]])(implicit keyOrder: KeyOrder[Sliced[T]]): Boolean = {
    import keyOrder._
    key >= minKey && {
      maxKey match {
        case swaydb.data.MaxKey.Fixed(maxKey) =>
          key <= maxKey
        case swaydb.data.MaxKey.Range(_, maxKey) =>
          key < maxKey
      }
    }
  }

  def minMax[T](left: Option[(Sliced[T], Sliced[T], Boolean)],
                right: Option[(Sliced[T], Sliced[T], Boolean)])(implicit keyOrder: Ordering[Sliced[T]]): Option[(Sliced[T], Sliced[T], Boolean)] = {
    for {
      lft <- left
      rht <- right
    } yield minMax(lft, rht)
  } orElse left.orElse(right)

  def minMax[T](left: (Sliced[T], Sliced[T], Boolean),
                right: (Sliced[T], Sliced[T], Boolean))(implicit keyOrder: Ordering[Sliced[T]]): (Sliced[T], Sliced[T], Boolean) = {
    val min = keyOrder.min(left._1, right._1)
    val maxCompare = keyOrder.compare(left._2, right._2)
    if (maxCompare == 0)
      (min, left._2, left._3 || right._3)
    else if (maxCompare < 0)
      (min, right._2, right._3)
    else
      (min, left._2, left._3)
  }

  /**
   * Boolean indicates if the toKey is inclusive.
   */
  def intersects[T](range1: (Sliced[T], Sliced[T], Boolean),
                    range2: (Sliced[T], Sliced[T], Boolean))(implicit ordering: Ordering[Sliced[T]]): Boolean = {
    import ordering._

    def check(range1: (Sliced[T], Sliced[T], Boolean),
              range2: (Sliced[T], Sliced[T], Boolean)): Boolean =
      if (range1._3 && range2._3)
        range1._1 >= range2._1 && range1._1 <= range2._2 ||
          range1._2 >= range2._1 && range1._2 <= range2._2
      else if (!range1._3 && range2._3)
        range1._1 >= range2._1 && range1._1 <= range2._2 ||
          range1._2 > range2._1 && range1._2 < range2._2
      else if (range1._3 && !range2._3)
        range1._1 >= range2._1 && range1._1 < range2._2 ||
          range1._2 >= range2._1 && range1._2 < range2._2
      else //both are false
        range1._1 >= range2._1 && range1._1 < range2._2 ||
          range1._2 > range2._1 && range1._2 < range2._2

    check(range1, range2) || check(range2, range1)
  }

  implicit class SlicesImplicits[T: ClassTag](slices: Sliced[Sliced[T]]) {
    /**
     * Closes this Slice and children Slices which disables
     * more data to be written to any of the Slices.
     */
    @inline final def closeAll(): Sliced[Sliced[T]] = {
      val newSlices = Slice.create[Sliced[T]](slices.close().size)
      slices foreach {
        slice =>
          newSlices.insert(slice.close())
      }
      newSlices
    }
  }

  implicit class OptionByteSliceImplicits(slice: Option[Sliced[Byte]]) {

    @inline final def unslice(): Option[Sliced[Byte]] =
      slice flatMap {
        slice =>
          if (slice.isEmpty)
            None
          else
            Some(slice.unslice())
      }
  }

  implicit class SeqByteSliceImplicits(slice: Seq[Sliced[Byte]]) {

    @inline final def unslice(): Seq[Sliced[Byte]] =
      if (slice.isEmpty)
        slice
      else
        slice.map(_.unslice())
  }

  /**
   * http://www.swaydb.io/slice/byte-slice
   */
  implicit class ByteSliceImplicits[B](slice: Sliced[B])(implicit byteOps: ByteOps[B]) extends ByteSlice[B] {

    @inline final def addByte(value: B): Sliced[B] = {
      slice insert value
      slice
    }

    @inline final def addBytes(anotherSlice: Sliced[B]): Sliced[B] = {
      slice.addAll(anotherSlice)
      slice
    }

    @inline final def addBoolean(bool: Boolean): Sliced[B] = {
      byteOps.writeBoolean(bool, slice)
      slice
    }

    @inline final def readBoolean(): Boolean =
      slice.get(0) == 1

    @inline final def addInt(integer: Int): Sliced[B] = {
      byteOps.writeInt(integer, slice)
      slice
    }

    @inline final def readInt(): Int =
      byteOps.readInt(slice)

    @inline final def dropUnsignedInt(): Sliced[B] = {
      val (_, byteSize) = readUnsignedIntWithByteSize()
      slice drop byteSize
    }

    @inline final def addSignedInt(integer: Int): Sliced[B] = {
      byteOps.writeSignedInt(integer, slice)
      slice
    }

    @inline final def readSignedInt(): Int =
      byteOps.readSignedInt(slice)

    @inline final def addUnsignedInt(integer: Int): Sliced[B] = {
      byteOps.writeUnsignedInt(integer, slice)
      slice
    }

    @inline final def addNonZeroUnsignedInt(integer: Int): Sliced[B] = {
      byteOps.writeUnsignedIntNonZero(integer, slice)
      slice
    }

    @inline final def readUnsignedInt(): Int =
      byteOps.readUnsignedInt(slice)

    @inline final def readUnsignedIntWithByteSize(): (Int, Int) =
      byteOps.readUnsignedIntWithByteSize(slice)

    @inline final def readNonZeroUnsignedIntWithByteSize(): (Int, Int) =
      byteOps.readUnsignedIntNonZeroWithByteSize(slice)

    @inline final def addLong(num: Long): Sliced[B] = {
      byteOps.writeLong(num, slice)
      slice
    }

    @inline final def readLong(): Long =
      byteOps.readLong(slice)

    @inline final def addUnsignedLong(num: Long): Sliced[B] = {
      byteOps.writeUnsignedLong(num, slice)
      slice
    }

    @inline final def readUnsignedLong(): Long =
      byteOps.readUnsignedLong(slice)

    @inline final def readUnsignedLongWithByteSize(): (Long, Int) =
      byteOps.readUnsignedLongWithByteSize(slice)

    @inline final def readUnsignedLongByteSize(): Int =
      byteOps.readUnsignedLongByteSize(slice)

    @inline final def addSignedLong(num: Long): Sliced[B] = {
      byteOps.writeSignedLong(num, slice)
      slice
    }

    @inline final def readSignedLong(): Long =
      byteOps.readSignedLong(slice)

    @inline final def addString(string: String, charsets: Charset = StandardCharsets.UTF_8): Sliced[B] = {
      byteOps.writeString(string, slice, charsets)
      slice
    }

    @inline final def addStringUTF8(string: String): Sliced[B] = {
      byteOps.writeString(string, slice, StandardCharsets.UTF_8)
      slice
    }

    @inline final def readString(charset: Charset = StandardCharsets.UTF_8): String =
      byteOps.readString(slice, charset)

    @inline final def toByteBufferWrap: ByteBuffer =
      slice.toByteBufferWrap

    @inline final def toByteBufferDirect: ByteBuffer =
      slice.toByteBufferDirect

    @inline final def toByteArrayOutputStream: ByteArrayInputStream =
      slice.toByteArrayInputStream

    @inline final def createReader(): SliceReader[B] =
      SliceReader(slice)
  }

  implicit class JavaByteSliced(sliced: Sliced[java.lang.Byte]) {
    def cast: Sliced[Byte] =
      sliced.asInstanceOf[Sliced[Byte]]
  }

  implicit class ScalaByteSliced(sliced: Sliced[Byte]) {
    def cast: Sliced[java.lang.Byte] =
      sliced.asInstanceOf[Sliced[java.lang.Byte]]
  }

  implicit class SliceImplicit[T](slice: Sliced[T]) {
    @inline final def add(value: T): Sliced[T] = {
      slice.insert(value)
      slice
    }

    @inline final def addAll(values: Sliced[T]): Sliced[T] = {
      if (values.nonEmpty) slice.insertAll(values)
      slice
    }

    @inline final def addAll(values: Array[T]): Sliced[T] = {
      if (values.nonEmpty) slice.insertAll(values)
      slice
    }
  }

  implicit class SliceImplicitClassTag[T: ClassTag](slice: Sliced[T]) {
    @inline final def append(other: Sliced[T]): Sliced[T] = {
      val merged = Slice.create[T](slice.size + other.size)
      merged addAll slice
      merged addAll other
      merged
    }

    @inline final def append(other: T): Sliced[T] = {
      val merged = Slice.create[T](slice.size + 1)
      merged addAll slice
      merged add other
      merged
    }
  }

  @inline final def newBuilder[T: ClassTag](sizeHint: Int): Slice.SliceBuilder[T] =
    new slice.Slice.SliceBuilder[T](sizeHint)

  private[swaydb] def newAggregator[T: ClassTag](sizeHint: Int): Aggregator[T, Sliced[T]] =
    Aggregator.fromBuilder[T, Sliced[T]](newBuilder[T](sizeHint))

  final case object Null extends SliceOption[Nothing] {
    override val isNoneC: Boolean = true
    override def getC: Sliced[Nothing] = throw new Exception("Slice is of type Null")
    override def isUnslicedOption: Boolean = true
    override def asSliceOption(): SliceOption[Nothing] = this
  }

  class SliceBuilder[A: ClassTag](sizeHint: Int) extends mutable.Builder[A, Sliced[A]] {
    //max is used to in-case sizeHit == 0 which is possible for cases where (None ++ Some(Slice[T](...)))
    protected var slice: Sliced[A] = Slice.create[A]((sizeHint * 2) max 100)

    def extendSlice(by: Int) = {
      val extendedSlice = Slice.create[A](slice.size * by)
      extendedSlice addAll slice
      slice = extendedSlice
    }

    @tailrec
    final override def addOne(x: A): this.type =
      try {
        slice add x
        this
      } catch {
        case _: ArrayIndexOutOfBoundsException => //Extend slice.
          extendSlice(by = 2)
          addOne(x)
      }

    def clear() =
      slice = Slice.create[A](slice.size)

    def result(): Sliced[A] =
      slice.close()
  }

  class SliceFactory(sizeHint: Int) extends ClassTagIterableFactory[Sliced] {

    def from[A](source: IterableOnce[A])(implicit evidence: ClassTag[A]): Sliced[A] =
      (newBuilder[A] ++= source).result()

    def empty[A](implicit evidence: ClassTag[A]): Sliced[A] =
      Slice.create[A](sizeHint)

    def newBuilder[A](implicit evidence: ClassTag[A]): mutable.Builder[A, Sliced[A]] =
      new SliceBuilder[A](sizeHint)
  }

  /**
   * An Iterable type that holds offset references to an Array without creating copies of the original array when creating
   * sub-slices.
   *
   * @param array      Array to create Slices for
   * @param fromOffset start offset
   * @param toOffset   end offset
   * @param written    items written
   * @tparam T The type of this Slice
   */

  //@formatter:off
  class Sliced[+T] private[slice](array: Array[T],
                                 fromOffset: Int,
                                 toOffset: Int,
                                 written: Int)(implicit val iterableEvidence: ClassTag[T]@uncheckedVariance) extends SliceBase[T](array, fromOffset, toOffset, written)
                                                                                                                with SliceOption[T]
                                                                                                                with IterableOps[T, Sliced, Sliced[T]]
                                                                                                                with EvidenceIterableFactoryDefaults[T, Sliced, ClassTag]
                                                                                                                with StrictOptimizedIterableOps[T, Sliced, Sliced[T]] {
                                                                                                                //@formatter:on

    override val isNoneC: Boolean =
      false

    override def getC: Sliced[T] =
      this

    override def selfSlice: Sliced[T] =
      this

    override def evidenceIterableFactory: SliceFactory =
      new SliceFactory(size)

    //Ok - why is iterableFactory required when there is ClassTagIterableFactory.
    override def iterableFactory: IterableFactory[Sliced] =
      new ClassTagIterableFactory.AnyIterableDelegate[Sliced](evidenceIterableFactory)
  }
}
