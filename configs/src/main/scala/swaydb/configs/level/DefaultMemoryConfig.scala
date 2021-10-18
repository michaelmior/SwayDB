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

package swaydb.configs.level

import com.typesafe.scalalogging.LazyLogging
import swaydb.data.accelerate.{Accelerator, LevelZeroMeter}
import swaydb.data.compaction.{LevelMeter, LevelThrottle, LevelZeroThrottle}
import swaydb.data.config._
import swaydb.data.{Atomic, OptimiseWrites}

import scala.concurrent.duration._

object DefaultMemoryConfig extends LazyLogging {

  /**
   * Default configuration for 2 leveled Memory database.
   */
  def apply(mapSize: Int,
            appliedFunctionsMapSize: Int,
            clearAppliedFunctionsOnBoot: Boolean,
            minSegmentSize: Int,
            maxKeyValuesPerSegment: Int,
            deleteDelay: FiniteDuration,
            acceleration: LevelZeroMeter => Accelerator,
            levelZeroThrottle: LevelZeroMeter => LevelZeroThrottle,
            lastLevelThrottle: LevelMeter => LevelThrottle,
            optimiseWrites: OptimiseWrites,
            atomic: Atomic): SwayDBMemoryConfig =
    ConfigWizard
      .withMemoryLevel0(
        mapSize = mapSize,
        appliedFunctionsMapSize = appliedFunctionsMapSize,
        clearAppliedFunctionsOnBoot = clearAppliedFunctionsOnBoot,
        optimiseWrites = optimiseWrites,
        atomic = atomic,
        acceleration = acceleration,
        throttle = levelZeroThrottle
      )
      .withMemoryLevel1(
        minSegmentSize = minSegmentSize,
        maxKeyValuesPerSegment = maxKeyValuesPerSegment,
        deleteDelay = deleteDelay,
        throttle = lastLevelThrottle
      )
}
