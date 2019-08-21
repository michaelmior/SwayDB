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

package swaydb.data.config

sealed trait Cache {
  def toOption: Option[Cache.Enabled] =
    this match {
      case Cache.Disable =>
        None
      case cache: Cache.Enabled =>
        Some(cache)
    }
}
object Cache {

  case object Disable extends Cache

  sealed trait Enabled extends Cache
  case class EnableBlockCache(blockSize: Int,
                              capacity: Int,
                              queue: ActorQueue) extends Enabled

  case class EnableKeyValueCache(capacity: Int,
                                 queue: ActorQueue) extends Enabled

  case class EnableBoth(blockSize: Int,
                        capacity: Int,
                        queue: ActorQueue) extends Enabled
}