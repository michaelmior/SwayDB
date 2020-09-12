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

package swaydb.core.util

import com.typesafe.scalalogging.LazyLogging
import swaydb.IO
import swaydb.core.segment.Segment
import swaydb.data.Reserve
import swaydb.data.order.KeyOrder
import swaydb.data.slice.Slice
import swaydb.data.slice.Slice._


import scala.collection.mutable.ListBuffer
import scala.concurrent.Promise

/**
 * Reserves a range of keys for processing by a single thread.
 *
 * This is used to ensure that multiple threads do not concurrent perform compaction on overlapping keys within
 * the same Level.
 */
private[core] object ReserveRange extends LazyLogging {

  case class Range[T](from: Sliced[Byte],
                      to: Sliced[Byte],
                      toInclusive: Boolean,
                      reserve: Reserve[T])

  object Range {
    implicit def ErrorHandler[T] = new IO.ExceptionHandler[Range[T]] {
      override def toException(f: Range[T]): Throwable = throw new UnsupportedOperationException("Exception on Range")
      override def toError(e: Throwable): Range[T] = throw e
    }
  }

  case class State[T](ranges: ListBuffer[Range[T]])

  def create[T](): State[T] =
    State(ListBuffer.empty)

  def get[T](from: Sliced[Byte],
             to: Sliced[Byte])(implicit state: State[T],
                               ordering: KeyOrder[Sliced[Byte]]): Option[T] =
    state.synchronized {
      state
        .ranges
        .find(range => ordering.equiv(from, range.from) && ordering.equiv(to, range.to))
        .flatMap(_.reserve.info.get())
    }

  def reserveOrGet[T](from: Sliced[Byte],
                      to: Sliced[Byte],
                      toInclusive: Boolean,
                      info: T)(implicit state: State[T],
                               ordering: KeyOrder[Sliced[Byte]]): Option[T] =
    state.synchronized {
      reserveOrGetRange(
        from = from,
        to = to,
        toInclusive = toInclusive,
        info = info
      ) match {
        case IO.Left(range) =>
          range.reserve.info.get()

        case IO.Right(_) =>
          None
      }
    }

  def reserveOrListen[T](from: Sliced[Byte],
                         to: Sliced[Byte],
                         toInclusive: Boolean,
                         info: T)(implicit state: State[T],
                                  ordering: KeyOrder[Sliced[Byte]]): IO[Promise[Unit], Sliced[Byte]] =
    state.synchronized {
      reserveOrGetRange(
        from = from,
        to = to,
        toInclusive = toInclusive,
        info = info
      ) match {
        case IO.Left(range) =>
          val promise = Promise[Unit]()
          range.reserve.savePromise(promise)
          IO.Left[Promise[Unit], Sliced[Byte]](promise)(IO.ExceptionHandler.PromiseUnit)

        case IO.Right(value) =>
          IO.Right[Promise[Unit], Sliced[Byte]](value)(IO.ExceptionHandler.PromiseUnit)
      }
    }

  def free[T](from: Sliced[Byte])(implicit state: State[T],
                                  ordering: KeyOrder[Sliced[Byte]]): Unit =
    state.synchronized {
      state
        .ranges
        .find(range => ordering.equiv(from, range.from))
        .foreach {
          range =>
            state.ranges -= range
            Reserve.setFree(range.reserve)
        }
    }

  def isUnreserved[T](from: Sliced[Byte],
                      to: Sliced[Byte],
                      toInclusive: Boolean)(implicit state: State[T],
                                            ordering: KeyOrder[Sliced[Byte]]): Boolean =
    state
      .ranges
      .forall {
        range =>
          !Slice.intersects(
            range1 = (range.from, range.to, range.toInclusive),
            range2 = (from, to, toInclusive)
          )
      }

  def isUnreserved[T](segment: Segment)(implicit state: State[T],
                                        ordering: KeyOrder[Sliced[Byte]]): Boolean =
    isUnreserved(
      from = segment.minKey,
      to = segment.maxKey.maxKey,
      toInclusive = segment.maxKey.inclusive
    )

  private def reserveOrGetRange[T](from: Sliced[Byte],
                                   to: Sliced[Byte],
                                   toInclusive: Boolean,
                                   info: T)(implicit state: State[T],
                                            ordering: KeyOrder[Sliced[Byte]]): IO[ReserveRange.Range[T], Sliced[Byte]] =
    state.synchronized {
      state
        .ranges
        .find(range => Slice.intersects((from, to, toInclusive), (range.from, range.to, range.toInclusive)))
        .map(IO.Left(_)(Range.ErrorHandler))
        .getOrElse {
          state.ranges += ReserveRange.Range(from, to, toInclusive, Reserve.busy(info, "ReserveRange"))
          val waitingCount = state.ranges.size
          //Helps debug situations if too many threads and try to compact into the same Segment.
          if (waitingCount >= 100) logger.warn(s"Too many listeners: $waitingCount")
          IO.Right[ReserveRange.Range[T], Sliced[Byte]](from)
        }
    }
}
