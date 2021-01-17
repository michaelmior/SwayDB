/*
 * Copyright (c) 2021 Simer JS Plaha (simer.j@gmail.com - @simerplaha)
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
 * If you modify this Program or any covered work, only by linking or combining
 * it with separate works, the licensors of this Program grant you additional
 * permission to convey the resulting work.
 */

package swaydb.core.level.compaction.throttle.behaviour

import com.typesafe.scalalogging.LazyLogging
import swaydb.DefActor
import swaydb.core.level.compaction.committer.CompactionCommitter
import swaydb.core.level.compaction.lock.LastLevelLocker
import swaydb.core.level.compaction.throttle.{ThrottleCompactor, ThrottleCompactorContext}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Implements compaction functions.
 */
private[throttle] object ThrottleExtendBehavior extends LazyLogging {

  def extensionSuccessful(state: ThrottleCompactorContext)(implicit committer: DefActor[CompactionCommitter.type, Unit],
                                                           locker: DefActor[LastLevelLocker, Unit],
                                                           ec: ExecutionContext,
                                                           self: DefActor[ThrottleCompactor, Unit]): Future[ThrottleCompactorContext] =
    ThrottleWakeUpBehavior.wakeUp(state)

  def extensionFailed(state: ThrottleCompactorContext): Future[ThrottleCompactorContext] = {
    logger.error("Failed to extend")
    Future.successful(state)
  }
}
