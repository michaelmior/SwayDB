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
 */

package swaydb.multimap

import org.scalatest.OptionValues._
import swaydb.Bag
import swaydb.api.TestBaseEmbedded
import swaydb.data.slice.Slice
import swaydb.serializers.Serializer

class NestedOptionValueSpec extends TestBaseEmbedded {
  override val keyValueCount: Int = 1000
  implicit val bag = Bag.less

  "Option[Option[V]]" in {

    import swaydb.serializers.Default._

    val rootMap = swaydb.memory.MultiMap[Int, Option[String], Nothing, Bag.Less]().get

    rootMap.put(1, None)
    rootMap.contains(1) shouldBe true
    rootMap.get(1).value shouldBe None

    rootMap.put(2, None)
    rootMap.contains(2) shouldBe true
    rootMap.get(2).value shouldBe None

    rootMap.stream.materialize.toList should contain only((1, None), (2, None))
  }

  "Option[Empty[V]]" in {

    sealed trait Value
    object Value {
      case class NonEmpty(string: String) extends Value
      case object Empty extends Value
    }

    import swaydb.serializers.Default._

    implicit object OptionOptionStringSerializer extends Serializer[Option[Value]] {
      override def write(data: Option[Value]): Slice[Byte] =
        data match {
          case Some(value) =>
            value match {
              case Value.NonEmpty(string) =>
                val stringBytes = StringSerializer.write(string)
                val slice = Slice.create[Byte](stringBytes.size + 1)

                slice add 1
                slice addAll stringBytes

              case Value.Empty =>
                Slice(0.toByte)
            }

          case None =>
            Slice.emptyBytes
        }

      override def read(data: Slice[Byte]): Option[Value] =
        if (data.isEmpty)
          None
        else if (data.head == 0)
          Some(Value.Empty)
        else
          Some(Value.NonEmpty(StringSerializer.read(data)))
    }

    val rootMap = swaydb.memory.MultiMap[Int, Option[Value], Nothing, Bag.Less]().get

    rootMap.put(1, Some(Value.Empty))
    rootMap.put(2, Some(Value.NonEmpty("two")))
    rootMap.put(3, None)

    rootMap.get(2).value.value shouldBe Value.NonEmpty("two")

    rootMap.stream.materialize[Bag.Less].toList shouldBe List((1, Some(Value.Empty)), (2, Some(Value.NonEmpty("two"))), (3, None))
  }
}