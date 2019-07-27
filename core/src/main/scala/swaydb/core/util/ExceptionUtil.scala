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

package swaydb.core.util

import com.typesafe.scalalogging.LazyLogging
import swaydb.IO

private[core] object ExceptionUtil extends LazyLogging {

  def logFailure(message: => String, failure: IO.Failure[swaydb.Error, _]): Unit =
    logFailure(message, failure.error)

  def logFailure(message: => String, error: swaydb.Error): Unit =
    error match {
      case swaydb.Error.Unknown(exception) =>
        logger.error(message, exception)

      case _: swaydb.Error =>
        if (logger.underlying.isTraceEnabled) logger.trace(message, error.exception)
    }
}
