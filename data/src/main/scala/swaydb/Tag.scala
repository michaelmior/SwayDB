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

package swaydb

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * [[Tag]]s are used to tag databases operations (side-effects) into types that can be
 * used to build custom wrappers.
 */
trait Tag[T[_]] {
  def apply[A](a: => A): T[A]
  def foreach[A, B](a: A)(f: A => B): Unit
  def map[A, B](a: A)(f: A => B): T[B]
  def flatMap[A, B](fa: T[A])(f: A => T[B]): T[B]
  def success[A](value: A): T[A]
  def failure[A](exception: Throwable): T[A]
  def none[A]: T[Option[A]]
  def foldLeft[A, U](initial: U, after: Option[A], stream: swaydb.Stream[A, T], drop: Int, take: Option[Int])(operation: (U, A) => U): T[U]
  def collectFirst[A](previous: A, stream: swaydb.Stream[A, T])(condition: A => Boolean): T[Option[A]]
  def toFuture[A](a: T[A]): Future[A]
  def toIO[E: ErrorHandler, A](a: T[A], timeout: FiniteDuration): IO[E, A]
  def fromIO[E: ErrorHandler, A](a: IO[E, A]): T[A]
}

object Tag {

  implicit val optionTag: Tag[Option] =
    new Tag[Option] {
      override def apply[A](a: => A): Option[A] =
        Some(a)

      def isSuccess[A](a: Option[A]): Boolean =
        a.isDefined

      def isFailure[A](a: Option[A]): Boolean =
        a.isEmpty

      override def foreach[A, B](a: A)(f: A => B): Unit =
        f(a)

      override def map[A, B](a: A)(f: A => B): Option[B] =
        Some(f(a))

      override def flatMap[A, B](fa: Option[A])(f: A => Option[B]): Option[B] =
        fa.flatMap(f)

      override def success[A](value: A): Option[A] =
        Some(value)

      override def failure[A](exception: Throwable): Option[A] =
        None

      override def none[A]: Option[Option[A]] =
        None

      override def foldLeft[A, U](initial: U, after: Option[A], stream: Stream[A, Option], drop: Int, take: Option[Int])(operation: (U, A) => U): Option[U] =
        tryTag.foldLeft(initial, after, stream.toTry(10.seconds), drop, take)(operation).toOption //todo

      override def collectFirst[A](previous: A, stream: Stream[A, Option])(condition: A => Boolean): Option[Option[A]] =
        tryTag.collectFirst(previous, stream.toTry(10.seconds))(condition).toOption

      override def toFuture[A](a: Option[A]): Future[A] =
        a.map(Future.successful) getOrElse Future.failed(new Exception("None value"))

      override def toIO[E: ErrorHandler, A](a: Option[A], timeout: FiniteDuration): IO[E, A] =
        a.map(IO.Success(_)) getOrElse IO.failed(new Exception("None value"))

      override def fromIO[E: ErrorHandler, A](a: IO[E, A]): Option[A] =
        a.toOption
    }

  implicit val tryTag: Tag[Try] =
    new Tag[Try] {
      override def apply[A](a: => A): Try[A] =
        Try(a)

      override def map[A, B](a: A)(f: A => B): Try[B] =
        Try(f(a))

      override def foreach[A, B](a: A)(f: A => B): Unit =
        f(a)

      override def flatMap[A, B](fa: Try[A])(f: A => Try[B]): Try[B] =
        fa.flatMap(f)

      def isSuccess[A](a: Try[A]): Boolean =
        a.isSuccess

      def isFailure[A](a: Try[A]): Boolean =
        a.isFailure

      override def success[A](value: A): Try[A] =
        scala.util.Success(value)

      override def none[A]: Try[Option[A]] =
        scala.util.Success(None)

      override def toFuture[A](a: Try[A]): Future[A] =
        Future.fromTry(a)

      override def toIO[E: ErrorHandler, A](a: Try[A], timeout: FiniteDuration): IO[E, A] =
        IO.fromTry[E, A](a)

      override def fromIO[E: ErrorHandler, A](a: IO[E, A]): Try[A] =
        a.toTry

      override def failure[A](exception: Throwable): Try[A] =
        scala.util.Failure(exception)

      override def foldLeft[A, U](initial: U, after: Option[A], stream: swaydb.Stream[A, Try], drop: Int, take: Option[Int])(operation: (U, A) => U): Try[U] =
        sio.foldLeft(initial, after, stream.toIO[swaydb.Error.API](10.seconds), drop, take)(operation).toTry //use ioWrap and convert that result to try.

      @tailrec
      override def collectFirst[A](previous: A, stream: swaydb.Stream[A, Try])(condition: A => Boolean): Try[Option[A]] =
        stream.next(previous) match {
          case success @ scala.util.Success(Some(nextA)) =>
            if (condition(nextA))
              success
            else
              collectFirst(nextA, stream)(condition)

          case none @ scala.util.Success(None) =>
            none

          case failure @ scala.util.Failure(_) =>
            failure
        }
    }

  implicit val sio: Tag[IO.ApiIO] =
    new Tag[IO.ApiIO] {

      import swaydb.Error.API.ErrorHandler

      override def apply[A](a: => A): IO.ApiIO[A] =
        IO(a)

      def isSuccess[A](a: IO.ApiIO[A]): Boolean =
        a.isSuccess

      def isFailure[A](a: IO.ApiIO[A]): Boolean =
        a.isFailure

      override def map[A, B](a: A)(f: A => B): IO.ApiIO[B] =
        IO(f(a))

      override def foreach[A, B](a: A)(f: A => B): Unit =
        f(a)

      override def flatMap[A, B](fa: IO.ApiIO[A])(f: A => IO.ApiIO[B]): IO.ApiIO[B] =
        fa.flatMap(f)

      override def success[A](value: A): IO.ApiIO[A] =
        IO.successful(value)

      override def failure[A](exception: Throwable): IO.ApiIO[A] =
        IO.failed(exception)

      override def none[A]: IO.ApiIO[Option[A]] =
        IO.none

      override def toFuture[A](a: IO.ApiIO[A]): Future[A] =
        a.toFuture

      override def foldLeft[A, U](initial: U, after: Option[A], stream: swaydb.Stream[A, IO.ApiIO], drop: Int, take: Option[Int])(operation: (U, A) => U): IO.ApiIO[U] = {
        @tailrec
        def fold(previous: A, drop: Int, currentSize: Int, previousResult: U): IO.ApiIO[U] =
          if (take.contains(currentSize))
            IO.Success(previousResult)
          else
            stream.next(previous) match {
              case IO.Success(Some(next)) =>
                if (drop >= 1) {
                  fold(next, drop - 1, currentSize, previousResult)
                } else {
                  val nextResult =
                    try {
                      operation(previousResult, next)
                    } catch {
                      case exception: Throwable =>
                        return IO.failed(exception)
                    }
                  fold(next, drop, currentSize + 1, nextResult)
                }

              case IO.Success(None) =>
                IO.Success(previousResult)

              case IO.Failure(error) =>
                IO.Failure(error)
            }

        if (take.contains(0))
          IO.Success(initial)
        else
          after.map(stream.next).getOrElse(stream.headOption) match {
            case IO.Success(Some(first)) =>
              if (drop >= 1)
                fold(first, drop - 1, 0, initial)
              else {
                val next =
                  try {
                    operation(initial, first)
                  } catch {
                    case throwable: Throwable =>
                      return IO.failed(throwable)
                  }
                fold(first, drop, 1, next)
              }

            case IO.Success(None) =>
              IO.Success(initial)

            case IO.Failure(error) =>
              IO.Failure(error)
          }
      }

      @tailrec
      override def collectFirst[A](previous: A, stream: swaydb.Stream[A, IO.ApiIO])(condition: A => Boolean): IO.ApiIO[Option[A]] =
        stream.next(previous) match {
          case success @ IO.Success(Some(nextA)) =>
            if (condition(nextA))
              success
            else
              collectFirst(nextA, stream)(condition)

          case none @ IO.Success(None) =>
            none

          case failure @ IO.Failure(_) =>
            failure
        }
      override def toIO[E: ErrorHandler, A](a: IO.ApiIO[A], timeout: FiniteDuration): IO[E, A] = ???
      override def fromIO[E: ErrorHandler, A](a: IO[E, A]): IO.ApiIO[A] = ???
    }

  trait Async[T[_]] extends Tag[T] {
    def fromFuture[A](a: Future[A]): T[A]
  }

  implicit def future(implicit ec: ExecutionContext): Async[Future] =
    new Async[Future] {
      override def apply[A](a: => A): Future[A] =
        Future(a)

      override def map[A, B](a: A)(f: A => B): Future[B] =
        Future(f(a))

      override def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] =
        fa.flatMap(f)

      override def success[A](value: A): Future[A] =
        Future.successful(value)

      override def failure[A](exception: Throwable): Future[A] =
        Future.failed(exception)

      override def none[A]: Future[Option[A]] =
        Future.successful(None)

      override def foreach[A, B](a: A)(f: A => B): Unit =
        f(a)

      override def toFuture[A](a: Future[A]): Future[A] =
        a

      override def fromFuture[A](a: Future[A]): Future[A] =
        a

      override def foldLeft[A, U](initial: U, after: Option[A], stream: swaydb.Stream[A, Future], drop: Int, take: Option[Int])(operation: (U, A) => U): Future[U] = {
        def fold(previous: A, drop: Int, currentSize: Int, previousResult: U): Future[U] =
          if (take.contains(currentSize))
            Future.successful(previousResult)
          else
            stream
              .next(previous)
              .flatMap {
                case Some(next) =>
                  if (drop >= 1) {
                    fold(next, drop - 1, currentSize, previousResult)
                  } else {
                    try {
                      val newResult = operation(previousResult, next)
                      fold(next, drop, currentSize + 1, newResult)
                    } catch {
                      case throwable: Throwable =>
                        Future.failed(throwable)
                    }
                  }

                case None =>
                  Future.successful(previousResult)
              }

        if (take.contains(0))
          Future.successful(initial)
        else
          after.map(stream.next).getOrElse(stream.headOption) flatMap {
            case Some(first) =>
              if (drop >= 1) {
                fold(first, drop - 1, 0, initial)
              } else {
                try {
                  val nextResult = operation(initial, first)
                  fold(first, drop, 1, nextResult)
                } catch {
                  case throwable: Throwable =>
                    Future.failed(throwable)
                }
              }

            case None =>
              Future.successful(initial)
          }
      }

      override def collectFirst[A](previous: A, stream: swaydb.Stream[A, Future])(condition: A => Boolean): Future[Option[A]] =
        stream.next(previous) flatMap {
          case some @ Some(nextA) =>
            if (condition(nextA))
              Future.successful(some)
            else
              collectFirst(nextA, stream)(condition)

          case None =>
            Future.successful(None)
        }
      override def toIO[E: ErrorHandler, A](a: Future[A], timeout: FiniteDuration): IO[E, A] = ???
      override def fromIO[E: ErrorHandler, A](a: IO[E, A]): Future[A] = ???
    }

  implicit class TagImplicits[A, T[_] : Tag](a: T[A])(implicit tag: Tag[T]) {
    @inline def map[B](f: A => B): T[B] =
      tag.flatMap(a) {
        a =>
          tag.map[A, B](a)(f)
      }

    @inline def flatMap[B](f: A => T[B]): T[B] =
      tag.flatMap(a)(f)
  }
}
