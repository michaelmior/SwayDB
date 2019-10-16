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

package swaydb.java

import java.util.Optional

import swaydb.IO.ThrowableIO
import swaydb.Streamer

import scala.compat.java8.OptionConverters._

trait IOStreamer[A] { parent =>
  def head: IO[Throwable, Optional[A]]

  def next(previous: A): IO[Throwable, Optional[A]]

  def toScalaStreamer: Streamer[A, ThrowableIO] =
    new Streamer[A, swaydb.IO.ThrowableIO] {
      override def head: ThrowableIO[Option[A]] =
        parent.head.asScala.map(a => a.asScala)

      override def next(previous: A): ThrowableIO[Option[A]] =
        parent.next(previous).asScala.map(_.asScala)
    }
}