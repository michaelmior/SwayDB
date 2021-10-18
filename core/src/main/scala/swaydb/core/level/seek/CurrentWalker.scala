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

package swaydb.core.level.seek

import swaydb.core.data.KeyValue
import swaydb.core.level.LevelSeek
import swaydb.core.segment.ref.search.ThreadReadState
import swaydb.data.slice.Slice

trait CurrentWalker {

  def levelNumber: String

  def get(key: Slice[Byte], readState: ThreadReadState): KeyValue.PutOption

  def higher(key: Slice[Byte], readState: ThreadReadState): LevelSeek[KeyValue]

  def lower(key: Slice[Byte], readState: ThreadReadState): LevelSeek[KeyValue]
}
