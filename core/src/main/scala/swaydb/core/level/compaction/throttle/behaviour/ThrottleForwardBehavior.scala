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
 * If you modify this Program or any covered work, only by linking or combining
 * it with separate works, the licensors of this Program grant you additional
 * permission to convey the resulting work.
 */

package swaydb.core.level.compaction.throttle.behaviour

import com.typesafe.scalalogging.LazyLogging
import swaydb.ActorWire
import swaydb.core.level.Level
import swaydb.core.level.compaction.committer.CompactionCommitter
import swaydb.core.level.compaction.lock.LastLevelLocker
import swaydb.core.level.compaction.throttle.ThrottleCompactor.ForwardResponse
import swaydb.core.level.compaction.throttle.{ThrottleCompactor, ThrottleCompactorState, ThrottleLevelState}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Implements compaction functions.
 */
private[throttle] object ThrottleForwardBehavior extends LazyLogging {

  def forward(level: Level,
              state: ThrottleCompactorState,
              replyTo: ActorWire[ForwardResponse, Unit])(implicit committer: ActorWire[CompactionCommitter.type, Unit],
                                                         locker: ActorWire[LastLevelLocker, Unit],
                                                         ec: ExecutionContext,
                                                         self: ActorWire[ThrottleCompactor, Unit]): Future[ThrottleCompactorState] =
    state match {
      case _: ThrottleCompactorState.Terminated =>
        logger.error(s"${state.name}: Forward failed because compaction is terminated!")
        replyTo.send(_.forwardFailed(level))
        Future.successful(state)

      case state: ThrottleCompactorState.Sleeping =>
        if (level.levelNumber + 1 != state.levels.head.levelNumber) {
          logger.error(s"${state.name}: Cannot forward Level. Forward Level(${level.levelNumber}) is not previous level of Level(${state.levels.head.levelNumber})")
          replyTo.send(_.forwardFailed(level))
          Future.successful(state)
        } else {
          ThrottleWakeUpBehavior
            .wakeUp(state.copy(context = state.context.copy(levels = state.context.levels prepend level)))
            .map {
              newState =>
                val newContext =
                  newState.context.copy(
                    levels = state.context.levels,
                    compactionStates = newState.context.compactionStates.filter(_._1 == level)
                  )

                replyTo.send(_.forwardSuccessful(level))
                newState.updateContext(newContext)
            }
        }
    }

  def forwardSuccessful(level: Level,
                        state: ThrottleCompactorState): Future[ThrottleCompactorState] =
    state match {
      case _: ThrottleCompactorState.Terminated =>
        logger.error(s"${state.name}: Forward success ignored because compaction is terminated!")
        Future.successful(state)

      case state: ThrottleCompactorState.Sleeping =>
        if (state.levels.last.levelNumber + 1 != level.levelNumber) {
          logger.error(s"${state.name}: Forward success update failed because Level(${state.levels.last.levelNumber}) + 1 != Leve(${level.levelNumber})")
          Future.successful(state)
        } else {
          Future.successful(state.copy(state.context.copy(levels = state.context.levels append level)))
        }
    }

  def forwardFailed(level: Level,
                    state: ThrottleCompactorState): Future[ThrottleCompactorState] =
    if (state.levels.last.levelNumber + 1 != level.levelNumber) {
      logger.error(s"${state.name}: Forward success update failed because Level(${state.levels.last.levelNumber}) + 1 != Leve(${level.levelNumber})")
      Future.successful(state)
    } else {
      Future.successful(state.updateContext(state.context.copy(levels = state.context.levels append level)))
    }

}
