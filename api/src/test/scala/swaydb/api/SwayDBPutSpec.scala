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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with SwayDB. If not, see <https://www.gnu.org/licenses/>.
 */

package swaydb

import org.scalatest.OptionValues._
import swaydb.api.TestBaseEmbedded
import swaydb.core.CommonAssertions._
import swaydb.core.IOValues._
import swaydb.core.RunThis._
import swaydb.data.IO
import swaydb.serializers.Default._

import scala.concurrent.duration._

class SwayDBPutSpec0 extends SwayDBPutSpec {
  val keyValueCount: Int = 1000

  override def newDB(): Map[Int, String, IO] =
    swaydb.persistent.Map[Int, String](dir = randomDir).value
}

class SwayDBPutSpec1 extends SwayDBPutSpec {

  val keyValueCount: Int = 10000

  override def newDB(): Map[Int, String, IO] =
    swaydb.persistent.Map[Int, String](randomDir, mapSize = 1.byte).value
}

class SwayDBPutSpec2 extends SwayDBPutSpec {

  val keyValueCount: Int = 10000

  override def newDB(): Map[Int, String, IO] =
    swaydb.memory.Map[Int, String](mapSize = 1.byte).value
}

class SwayDBPutSpec3 extends SwayDBPutSpec {
  val keyValueCount: Int = 10000

  override def newDB(): Map[Int, String, IO] =
    swaydb.memory.Map[Int, String]().value
}

class SwayDBPutSpec4 extends SwayDBPutSpec {

  val keyValueCount: Int = 10000

  override def newDB(): Map[Int, String, IO] =
    swaydb.memory.zero.Map[Int, String](mapSize = 1.byte).value
}

class SwayDBPutSpec5 extends SwayDBPutSpec {
  val keyValueCount: Int = 10000

  override def newDB(): Map[Int, String, IO] =
    swaydb.memory.zero.Map[Int, String]().value
}

sealed trait SwayDBPutSpec extends TestBaseEmbedded {

  val keyValueCount: Int

  def newDB(): Map[Int, String, IO]

  def doGet(db: Map[Int, String, IO]) = {
    (1 to keyValueCount) foreach {
      i =>
        db.expiration(i).value shouldBe empty
        db.get(i).value.value shouldBe s"$i new"
    }
  }

  "Put" when {
    "Put" in {
      val db = newDB()

      (1 to keyValueCount) foreach { i => db.put(i, i.toString).value }
      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Put & Expire" in {
      val db = newDB()

      val deadline =
        eitherOne(4.seconds.fromNow, expiredDeadline())

      (1 to keyValueCount) foreach { i => db.put(i, i.toString).value }
      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.expire(i, deadline).value),
        right = db.expire(1, keyValueCount, deadline).value
      )
      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)
      sleep(deadline)
      doGet(db)

      db.close().get
    }

    "Put & Remove" in {
      val db = newDB()

      (1 to keyValueCount) foreach { i => db.put(i, i.toString).value }
      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.remove(i).value),
        right = db.remove(1, keyValueCount).value
      )
      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Put & Update" in {
      val db = newDB()

      (1 to keyValueCount) foreach { i => db.put(i, i.toString).value }
      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.update(i, value = "updated").value),
        right = db.update(1, keyValueCount, value = "updated").value
      )
      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)
    }
  }

  "Put" when {
    "Remove" in {
      val db = newDB()

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.remove(i).value),
        right = db.remove(1, keyValueCount).value
      )

      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Remove & Put" in {
      val db = newDB()

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.remove(i).value),
        right = db.remove(1, keyValueCount).value
      )
      (1 to keyValueCount) foreach { i => db.put(i, i.toString).value }
      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Remove & Update" in {
      val db = newDB()

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.remove(i).value),
        right = db.remove(1, keyValueCount).value
      )
      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.update(i, value = "updated").value),
        right = db.update(1, keyValueCount, value = "updated").value
      )

      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Remove & Expire" in {
      val db = newDB()

      val deadline = eitherOne(2.seconds.fromNow, expiredDeadline())

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.remove(i).value),
        right = db.remove(1, keyValueCount).value
      )
      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.expire(i, deadline).value),
        right = db.expire(1, keyValueCount, deadline).value
      )

      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Remove & Remove" in {
      val db = newDB()

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.remove(i).value),
        right = db.remove(1, keyValueCount).value
      )

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.remove(i).value),
        right = db.remove(1, keyValueCount).value
      )

      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }
  }

  "Put" when {
    "Update" in {
      val db = newDB()

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.update(i, value = "old updated").value),
        right = db.update(1, keyValueCount, value = "old updated").value
      )
      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Update & Put" in {
      val db = newDB()

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.update(i, value = "updated").value),
        right = db.update(1, keyValueCount, value = "updated").value
      )

      (1 to keyValueCount) foreach { i => db.put(i, i.toString).value }

      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Update & Update" in {
      val db = newDB()

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.update(i, value = "updated 1").value),
        right = db.update(1, keyValueCount, value = "updated 1").value
      )

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.update(i, value = "updated 2").value),
        right = db.update(1, keyValueCount, value = "updated 2").value
      )

      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Update & Expire" in {
      val db = newDB()

      val deadline = eitherOne(2.seconds.fromNow, expiredDeadline())

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.update(i, value = "updated 1").value),
        right = db.update(1, keyValueCount, value = "updated 1").value
      )

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.expire(i, deadline).value),
        right = db.expire(1, keyValueCount, deadline).value
      )

      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Update & Remove" in {
      val db = newDB()

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.update(i, value = "updated 1").value),
        right = db.update(1, keyValueCount, value = "updated 1").value
      )

      (1 to keyValueCount) foreach { i => db.remove(i).value }

      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }
      doGet(db)

      db.close().get
    }
  }

  "Put" when {
    "Expire" in {
      val db = newDB()

      val deadline = eitherOne(expiredDeadline(), 2.seconds.fromNow)

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.expire(i, deadline).value),
        right = db.expire(1, keyValueCount, deadline).value
      )

      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Expire & Remove" in {
      val db = newDB()
      //if the deadline is either expired or delay it does not matter in this case because the underlying key-values are removed.
      val deadline = eitherOne(expiredDeadline(), 2.seconds.fromNow)

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.expire(i, deadline).value),
        right = db.expire(1, keyValueCount, deadline).value
      )
      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.remove(i).value),
        right = db.remove(1, keyValueCount).value
      )
      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Expire & Update" in {
      val db = newDB()

      val deadline = eitherOne(expiredDeadline(), 2.seconds.fromNow)

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.expire(i, deadline).value),
        right = db.expire(1, keyValueCount, deadline).value
      )

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.update(i, value = "updated").value),
        right = db.update(1, keyValueCount, value = "updated").value
      )
      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Expire & Expire" in {
      val db = newDB()

      val deadline = eitherOne(expiredDeadline(), 2.seconds.fromNow)
      val deadline2 = eitherOne(expiredDeadline(), 4.seconds.fromNow)

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.expire(i, deadline).value),
        right = db.expire(1, keyValueCount, deadline).value
      )

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.expire(i, deadline2).value),
        right = db.expire(1, keyValueCount, deadline2).value
      )

      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }

    "Expire & Put" in {
      val db = newDB()

      val deadline = eitherOne(expiredDeadline(), 4.seconds.fromNow)

      eitherOne(
        left = (1 to keyValueCount) foreach (i => db.expire(i, deadline).value),
        right = db.expire(1, keyValueCount, deadline).value
      )

      (1 to keyValueCount) foreach { i => db.put(i, i.toString).value }

      (1 to keyValueCount) foreach { i => db.put(i, s"$i new").value }

      doGet(db)

      db.close().get
    }
  }

  "clear" when {
    "empty" in {
      val db = newDB()

      db.isEmpty.get shouldBe true
      db.clear().value
      db.isEmpty.get shouldBe true
    }

    "not empty" in {
      val db = newDB()

      (1 to keyValueCount) foreach { i => db.put(i, i.toString).value }
      db.isEmpty.get shouldBe false

      db.clear().value
      db.isEmpty.get shouldBe true
    }
  }
}
