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

package swaydb.data.slice

import com.typesafe.scalalogging.LazyLogging
import swaydb.utils.SomeOrNoneCovariant

import scala.annotation.tailrec
import scala.collection.compat.IterableOnce
import scala.collection.generic.CanBuildFrom
import scala.collection.{IterableLike, mutable}
import scala.reflect.ClassTag

/**
 * Documentation - http://swaydb.io/slice
 */
sealed trait SliceOption[+T] extends SomeOrNoneCovariant[SliceOption[T], Slice[T]] {
  override def noneC: SliceOption[Nothing] = Slice.Null

  def isUnslicedOption: Boolean

  def asSliceOption(): SliceOption[T]

  def unsliceOption(): SliceOption[T] =
    if (this.isNoneC || this.getC.isEmpty)
      Slice.Null
    else
      this.getC.unslice()
}

object Slice extends SliceCompanionBase with LazyLogging {

  final case object Null extends SliceOption[Nothing] {
    override val isNoneC: Boolean = true
    override def getC: Slice[Nothing] = throw new Exception("Slice is of type Null")
    override def isUnslicedOption: Boolean = true
    override def asSliceOption(): SliceOption[Nothing] = this
  }

  class SliceBuilder[T: ClassTag](maxSize: Int) extends mutable.Builder[T, Slice[T]] {
    //max is used to in-case sizeHit == 0 which is possible for cases where (None ++ Some(Slice[T](...)))
    protected var slice: Slice[T] = Slice.of[T](maxSize max 16)

    @inline def extendSlice(by: Int) = {
      val extendedSlice = Slice.of[T](slice.size * by)
      extendedSlice addAll slice
      slice = extendedSlice
    }

    @tailrec
    final override def +=(x: T): this.type =
      if (!slice.isFull) {
        slice add x
        this
      } else {
        extendSlice(by = 2)
        +=(x)
      }

    override def ++=(xs: IterableOnce[T]): SliceBuilder.this.type = {
      this.slice = slice.addAllOrNew(items = xs, expandBy = 2)
      this
    }


    def clear() =
      slice = Slice.of[T](slice.size)

    def result: Slice[T] =
      slice.close()
  }

  implicit def canBuildFrom[T: ClassTag]: CanBuildFrom[Slice[_], T, Slice[T]] =
    new CanBuildFrom[Slice[_], T, Slice[T]] {
      def apply(from: Slice[_]) =
        new SliceBuilder[T](from.size)

      def apply(): mutable.Builder[T, Slice[T]] = {
        //Use an Array or another data-type instead of Slice if dynamic extensions are required.
        //Dynamic extension is disabled so that we do not do unnecessary copying just for the sake of convenience.
        //Slice is used heavily internally and we should avoid all operations that might be expensive.
        val exception = new Exception("Cannot create slice with no size defined. If dynamic extension is required consider using another data-type.")
        logger.error(exception.getMessage, exception)
        throw exception
      }
    }
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
class Slice[+T] private[slice](array: Array[T],
                               fromOffset: Int,
                               toOffset: Int,
                               written: Int)(implicit protected[this] implicit val tag: ClassTag[T]) extends SliceBase[T](array, fromOffset, toOffset, written)
                                                                                                        with SliceOption[T]
                                                                                                        with IterableLike[T, Slice[T]] { self =>
//@formatter:on

  override val isNoneC: Boolean =
    false

  override def getC: Slice[T] =
    this

  override def selfSlice: Slice[T] =
    this

  override protected[this] def newBuilder: scala.collection.mutable.Builder[T, Slice[T]] =
    new Slice.SliceBuilder[T](this.size max 1)

}
