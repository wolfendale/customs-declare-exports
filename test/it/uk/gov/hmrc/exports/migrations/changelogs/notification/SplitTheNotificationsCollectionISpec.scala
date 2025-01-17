/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.exports.migrations.changelogs.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.client.MongoCollection
import org.bson.Document
import org.bson.types.ObjectId
import org.mongodb.scala.model.{IndexOptions, Indexes}
import uk.gov.hmrc.exports.base.IntegrationTestMigrationToolSpec
import uk.gov.hmrc.exports.migrations.changelogs.notification.SplitTheNotificationsCollectionISpec._

import scala.jdk.CollectionConverters._

class SplitTheNotificationsCollectionISpec extends IntegrationTestMigrationToolSpec {

  override val collectionUnderTest = "notifications"
  override val changeLog = new SplitTheNotificationsCollection()

  def getUnparsedNotificationsRepo: MongoCollection[Document] = getCollection("unparsedNotifications")

  override def beforeEach(): Unit = {
    super.beforeEach()
    removeAll(getCollection(collectionUnderTest))
    removeAll(getUnparsedNotificationsRepo)
  }

  "SplitNotificationsCollection" should {

    "drop 'detailsDocMissingIdx' index" in {
      val collection = getCollection(collectionUnderTest)
      collection.createIndex(Indexes.ascending("details"), IndexOptions().name("detailsDocMissingIdx"))

      changeLog.migrationFunction(database)

      collection.listIndexes.asScala.toList.map(_.getString("name")).contains("detailsDocMissingIdx") mustBe false
    }

    "not make any changes" when {

      "there is single ParsedNotification and corresponding UnparsedNotification" in {
        val notificationsRepo = getCollection(collectionUnderTest)
        val unparsedNotificationsRepo = getUnparsedNotificationsRepo
        notificationsRepo.insertOne(Document.parse(TestData_1.parsedNotificationAfterChanges))
        unparsedNotificationsRepo.insertOne(Document.parse(TestData_1.unparsedNotification))

        changeLog.migrationFunction(database)

        val parsedNotificationAfter = notificationsRepo.find().first()
        val unparsedNotificationAfter = unparsedNotificationsRepo.find().first()

        compareJson(parsedNotificationAfter.toJson, TestData_1.parsedNotificationAfterChanges)
        compareJson(unparsedNotificationAfter.toJson, TestData_1.unparsedNotification)

        val id = unparsedNotificationAfter.get("item", classOf[Document]).getString("id")
        id mustBe parsedNotificationAfter.getString("unparsedNotificationId")

        val actionId = unparsedNotificationAfter.get("item", classOf[Document]).getString("actionId")
        actionId mustBe parsedNotificationAfter.getString("actionId")
      }

      "there are multiple ParsedNotifications and complete set of corresponding UnparsedNotifications" in {
        val parsedNotification_1 = Document.parse(TestData_1.parsedNotificationAfterChanges)
        val parsedNotification_2 = Document.parse(TestData_2.parsedNotificationAfterChanges)
        val parsedNotification_3 = Document.parse(TestData_3.parsedNotificationAfterChanges)
        val unparsedNotification_1 = Document.parse(TestData_1.unparsedNotification)
        val unparsedNotification_2 = Document.parse(TestData_2.unparsedNotification)
        val unparsedNotification_3 = Document.parse(TestData_3.unparsedNotification)

        val allParsedNotifications = Seq(parsedNotification_1, parsedNotification_2, parsedNotification_3)
        val allUnparsedNotifications = Seq(unparsedNotification_1, unparsedNotification_2, unparsedNotification_3)
        val allNotificationsBeforeChanges = allParsedNotifications zip allUnparsedNotifications

        val notificationsRepo = getCollection(collectionUnderTest)
        val unparsedNotificationsRepo = getUnparsedNotificationsRepo

        allNotificationsBeforeChanges.foreach { case (parsed, unparsed) =>
          notificationsRepo.insertOne(parsed)
          unparsedNotificationsRepo.insertOne(unparsed)
        }

        changeLog.migrationFunction(database)

        val parsedNotificationsAfter = notificationsRepo.find().asScala.toSeq
        val unparsedNotificationsAfter = unparsedNotificationsRepo.find().asScala.toSeq
        parsedNotificationsAfter.length mustBe 3
        unparsedNotificationsAfter.length mustBe 3

        allNotificationsBeforeChanges.foreach { case (parsed, unparsed) =>
          val parsedNotificationAfter = parsedNotificationsAfter.find(_.get("_id", classOf[ObjectId]) == parsed.get("_id", classOf[ObjectId]))
          parsedNotificationAfter mustBe defined
          val unparsedNotificationAfter = unparsedNotificationsAfter.find(_.get("_id", classOf[ObjectId]) == unparsed.get("_id", classOf[ObjectId]))
          unparsedNotificationAfter mustBe defined

          compareJson(parsedNotificationAfter.get.toJson, parsed.toJson)
          compareJson(unparsedNotificationAfter.get.toJson, unparsed.toJson)
          unparsedNotificationAfter.get.get("item", classOf[Document]).getString("id") mustBe parsedNotificationAfter.get.getString(
            "unparsedNotificationId"
          )
          unparsedNotificationAfter.get.get("item", classOf[Document]).getString("actionId") mustBe parsedNotificationAfter.get.getString("actionId")
        }
      }
    }

    "add missing UnparsedNotifications and update ParsedNotifications' unparsedNotificationId fields" when {

      "there is single ParsedNotification" in {
        val parsedNotification_1 = Document.parse(TestData_1.parsedNotificationBeforeChanges)
        val allParsedNotificationsBeforeChanges = Seq(parsedNotification_1)

        val notificationsRepo = getCollection(collectionUnderTest)
        val unparsedNotificationsRepo = getUnparsedNotificationsRepo

        notificationsRepo.insertOne(parsedNotification_1)

        changeLog.migrationFunction(database)

        val parsedNotificationsAfter = notificationsRepo.find().asScala.toSeq
        val unparsedNotificationsAfter = unparsedNotificationsRepo.find().asScala.toSeq
        parsedNotificationsAfter.length mustBe 1
        unparsedNotificationsAfter.length mustBe 1

        allParsedNotificationsBeforeChanges.foreach { parsedNotificationBefore =>
          val id = parsedNotificationBefore.get("_id", classOf[ObjectId])
          val parsedAfterChanges = parsedNotificationsAfter.find(_.get("_id", classOf[ObjectId]) == id)

          parsedAfterChanges mustBe defined
          parsedAfterChanges.get.getString("actionId") mustBe parsedNotificationBefore.getString("actionId")
          parsedAfterChanges.get.containsKey("unparsedNotificationId") mustBe true
          parsedAfterChanges.get.containsKey("details") mustBe true
          parsedAfterChanges.get.containsKey("payload") mustBe false

          val unparsedNotificationId = parsedAfterChanges.get.getString("unparsedNotificationId")
          val unparsedAfterChanges = unparsedNotificationsAfter.find(_.get("item", classOf[Document]).getString("id") == unparsedNotificationId)

          unparsedAfterChanges mustBe defined
          unparsedAfterChanges.get.get("item", classOf[Document]).getString("actionId") mustBe parsedNotificationBefore.getString("actionId")
          unparsedAfterChanges.get.getString("status") mustBe "succeeded"

          if (parsedNotificationBefore.containsKey("payload")) {
            unparsedAfterChanges.get.get("item", classOf[Document]).getString("payload") mustBe parsedNotificationBefore.getString("payload")
          }
        }
      }

      "there are multiple ParsedNotifications coming as an outcome from a single payload" in {
        val testData = TestData_1
        val parsedNotification_1 = Document.parse(testData.parsedNotificationBeforeChanges("5fc0d62750c11c0797a24571"))
        val parsedNotification_2 = Document.parse(testData.parsedNotificationBeforeChanges("5fc0d62750c11c0797a24572"))
        val parsedNotification_3 = Document.parse(testData.parsedNotificationBeforeChanges("5fc0d62750c11c0797a24573"))
        val allParsedNotificationsBeforeChanges = Seq(parsedNotification_1, parsedNotification_2, parsedNotification_3)

        val notificationsRepo = getCollection(collectionUnderTest)
        val unparsedNotificationsRepo = getUnparsedNotificationsRepo

        allParsedNotificationsBeforeChanges.foreach(notificationsRepo.insertOne(_))

        changeLog.migrationFunction(database)

        val parsedNotificationsAfter = notificationsRepo.find().asScala.toSeq
        val unparsedNotificationsAfter = unparsedNotificationsRepo.find().asScala.toSeq
        parsedNotificationsAfter.length mustBe 3
        unparsedNotificationsAfter.length mustBe 1

        allParsedNotificationsBeforeChanges.foreach { parsedNotificationBefore =>
          val id = parsedNotificationBefore.get("_id", classOf[ObjectId])
          val parsedAfterChanges = parsedNotificationsAfter.find(_.get("_id", classOf[ObjectId]) == id)

          parsedAfterChanges mustBe defined
          parsedAfterChanges.get.getString("actionId") mustBe parsedNotificationBefore.getString("actionId")
          parsedAfterChanges.get.containsKey("unparsedNotificationId") mustBe true
          parsedAfterChanges.get.containsKey("details") mustBe true
          parsedAfterChanges.get.containsKey("payload") mustBe false

          val unparsedNotificationId = parsedAfterChanges.get.getString("unparsedNotificationId")
          val unparsedAfterChanges = unparsedNotificationsAfter.find(_.get("item", classOf[Document]).getString("id") == unparsedNotificationId)

          unparsedAfterChanges mustBe defined
          unparsedAfterChanges.get.get("item", classOf[Document]).getString("actionId") mustBe parsedNotificationBefore.getString("actionId")
          unparsedAfterChanges.get.getString("status") mustBe "succeeded"

          if (parsedNotificationBefore.containsKey("payload")) {
            unparsedAfterChanges.get.get("item", classOf[Document]).getString("payload") mustBe parsedNotificationBefore.getString("payload")
          }
        }
      }

      "there are multiple ParsedNotifications coming as an outcome from different payloads" when {

        "actionIds are the same" in {
          val parsedNotification_1 = Document.parse(TestData_1.parsedNotificationBeforeChanges)
          val parsedNotification_2 = Document.parse(TestData_2.parsedNotificationBeforeChanges)
          val parsedNotification_3 = Document.parse(TestData_3.parsedNotificationBeforeChanges)
          parsedNotification_1.put("actionId", "b1c09f1b-7c94-4e90-b754-7c5c71c44e11")
          parsedNotification_2.put("actionId", "b1c09f1b-7c94-4e90-b754-7c5c71c44e11")
          parsedNotification_3.put("actionId", "b1c09f1b-7c94-4e90-b754-7c5c71c44e11")
          val allParsedNotificationsBeforeChanges = Seq(parsedNotification_1, parsedNotification_2, parsedNotification_3)

          val notificationsRepo = getCollection(collectionUnderTest)
          val unparsedNotificationsRepo = getUnparsedNotificationsRepo

          allParsedNotificationsBeforeChanges.foreach(notificationsRepo.insertOne(_))

          changeLog.migrationFunction(database)

          val parsedNotificationsAfter = notificationsRepo.find().asScala.toSeq
          val unparsedNotificationsAfter = unparsedNotificationsRepo.find().asScala.toSeq
          parsedNotificationsAfter.length mustBe 3
          unparsedNotificationsAfter.length mustBe 3

          allParsedNotificationsBeforeChanges.foreach { parsedNotificationBefore =>
            val id = parsedNotificationBefore.get("_id", classOf[ObjectId])
            val parsedAfterChanges = parsedNotificationsAfter.find(_.get("_id", classOf[ObjectId]) == id)

            parsedAfterChanges mustBe defined
            parsedAfterChanges.get.getString("actionId") mustBe parsedNotificationBefore.getString("actionId")
            parsedAfterChanges.get.containsKey("unparsedNotificationId") mustBe true
            parsedAfterChanges.get.containsKey("details") mustBe true
            parsedAfterChanges.get.containsKey("payload") mustBe false

            val unparsedNotificationId = parsedAfterChanges.get.getString("unparsedNotificationId")
            val unparsedAfterChanges = unparsedNotificationsAfter.find(_.get("item", classOf[Document]).getString("id") == unparsedNotificationId)

            unparsedAfterChanges mustBe defined
            unparsedAfterChanges.get.get("item", classOf[Document]).getString("actionId") mustBe parsedNotificationBefore.getString("actionId")
            unparsedAfterChanges.get.getString("status") mustBe "succeeded"

            if (parsedNotificationBefore.containsKey("payload")) {
              unparsedAfterChanges.get.get("item", classOf[Document]).getString("payload") mustBe parsedNotificationBefore.getString("payload")
            }
          }
        }

        "actionIds are different" in {
          val parsedNotification_1 = Document.parse(TestData_1.parsedNotificationBeforeChanges)
          val parsedNotification_2 = Document.parse(TestData_2.parsedNotificationBeforeChanges)
          val parsedNotification_3 = Document.parse(TestData_3.parsedNotificationBeforeChanges)
          val allParsedNotificationsBeforeChanges = Seq(parsedNotification_1, parsedNotification_2, parsedNotification_3)

          val notificationsRepo = getCollection(collectionUnderTest)
          val unparsedNotificationsRepo = getUnparsedNotificationsRepo

          allParsedNotificationsBeforeChanges.foreach(notificationsRepo.insertOne(_))

          changeLog.migrationFunction(database)

          val parsedNotificationsAfter = notificationsRepo.find().asScala.toSeq
          val unparsedNotificationsAfter = unparsedNotificationsRepo.find().asScala.toSeq
          parsedNotificationsAfter.length mustBe 3
          unparsedNotificationsAfter.length mustBe 3

          allParsedNotificationsBeforeChanges.foreach { parsedNotificationBefore =>
            val id = parsedNotificationBefore.get("_id", classOf[ObjectId])
            val parsedAfterChanges = parsedNotificationsAfter.find(_.get("_id", classOf[ObjectId]) == id)

            parsedAfterChanges mustBe defined
            parsedAfterChanges.get.getString("actionId") mustBe parsedNotificationBefore.getString("actionId")
            parsedAfterChanges.get.containsKey("unparsedNotificationId") mustBe true
            parsedAfterChanges.get.containsKey("details") mustBe true
            parsedAfterChanges.get.containsKey("payload") mustBe false

            val unparsedNotificationId = parsedAfterChanges.get.getString("unparsedNotificationId")
            val unparsedAfterChanges = unparsedNotificationsAfter.find(_.get("item", classOf[Document]).getString("id") == unparsedNotificationId)

            unparsedAfterChanges mustBe defined
            unparsedAfterChanges.get.get("item", classOf[Document]).getString("actionId") mustBe parsedNotificationBefore.getString("actionId")
            unparsedAfterChanges.get.getString("status") mustBe "succeeded"

            if (parsedNotificationBefore.containsKey("payload")) {
              unparsedAfterChanges.get.get("item", classOf[Document]).getString("payload") mustBe parsedNotificationBefore.getString("payload")
            }
          }
        }
      }

      "there are multiple ParsedNotifications and a partial set of corresponding UnparsedNotifications" in {
        val parsedNotification_1 = Document.parse(TestData_1.parsedNotificationAfterChanges)
        val parsedNotification_2 = Document.parse(TestData_2.parsedNotificationBeforeChanges)
        val parsedNotification_3 = Document.parse(TestData_3.parsedNotificationBeforeChanges)
        val allParsedNotificationsBeforeChanges = Seq(parsedNotification_1, parsedNotification_2, parsedNotification_3)
        val unparsedNotification_1 = Document.parse(TestData_1.unparsedNotification)

        val notificationsRepo = getCollection(collectionUnderTest)
        val unparsedNotificationsRepo = getUnparsedNotificationsRepo

        allParsedNotificationsBeforeChanges.foreach(notificationsRepo.insertOne(_))
        unparsedNotificationsRepo.insertOne(unparsedNotification_1)

        changeLog.migrationFunction(database)

        val parsedNotificationsAfter = notificationsRepo.find().asScala.toSeq
        val unparsedNotificationsAfter = unparsedNotificationsRepo.find().asScala.toSeq
        parsedNotificationsAfter.length mustBe 3
        unparsedNotificationsAfter.length mustBe 3

        allParsedNotificationsBeforeChanges.foreach { parsedNotificationBefore =>
          val id = parsedNotificationBefore.get("_id", classOf[ObjectId])
          val parsedAfterChanges = parsedNotificationsAfter.find(_.get("_id", classOf[ObjectId]) == id)

          parsedAfterChanges mustBe defined
          parsedAfterChanges.get.getString("actionId") mustBe parsedNotificationBefore.getString("actionId")
          parsedAfterChanges.get.containsKey("unparsedNotificationId") mustBe true
          parsedAfterChanges.get.containsKey("details") mustBe true
          parsedAfterChanges.get.containsKey("payload") mustBe false

          val unparsedNotificationId = parsedAfterChanges.get.getString("unparsedNotificationId")
          val unparsedAfterChanges = unparsedNotificationsAfter.find(_.get("item", classOf[Document]).getString("id") == unparsedNotificationId)

          unparsedAfterChanges mustBe defined
          unparsedAfterChanges.get.get("item", classOf[Document]).getString("actionId") mustBe parsedNotificationBefore.getString("actionId")
          unparsedAfterChanges.get.getString("status") mustBe "succeeded"

          if (parsedNotificationBefore.containsKey("payload")) {
            unparsedAfterChanges.get.get("item", classOf[Document]).getString("payload") mustBe parsedNotificationBefore.getString("payload")
          }
        }
      }
    }

    "add missing UnparsedNotification and remove ParsedNotification" when {

      "there is unparsable Notification" in {
        val unparsableNotification = Document.parse(TestData_1.parsedNotificationBeforeChanges)
        unparsableNotification.remove("details")

        val notificationsRepo = getCollection(collectionUnderTest)
        val unparsedNotificationsRepo = getUnparsedNotificationsRepo

        notificationsRepo.insertOne(unparsableNotification)
        changeLog.migrationFunction(database)

        val parsedNotificationsAfter = notificationsRepo.find().asScala.toSeq
        val unparsedNotificationsAfter = unparsedNotificationsRepo.find().asScala.toSeq
        parsedNotificationsAfter.length mustBe 0
        unparsedNotificationsAfter.length mustBe 1

        unparsedNotificationsAfter.foreach { unparsedNotificationAfter =>
          unparsedNotificationAfter.get("item", classOf[Document]).getString("actionId") mustBe unparsableNotification.getString("actionId")
          unparsedNotificationAfter.get("item", classOf[Document]).getString("payload") mustBe unparsableNotification.getString("payload")
          unparsedNotificationAfter.getString("status") mustBe "todo"
        }
      }
    }

    "remove ParsedNotification" when {

      "there is unparsable Notification and corresponding UnparsedNotification" in {
        val unparsableNotification = Document.parse(TestData_1.parsedNotificationBeforeChanges)
        unparsableNotification.remove("details")
        val unparsedNotification = Document.parse(TestData_1.unparsedNotification)

        val notificationsRepo = getCollection(collectionUnderTest)
        val unparsedNotificationsRepo = getUnparsedNotificationsRepo

        notificationsRepo.insertOne(unparsableNotification)
        unparsedNotificationsRepo.insertOne(unparsedNotification)
        changeLog.migrationFunction(database)

        val parsedNotificationsAfter = notificationsRepo.find().asScala.toSeq
        val unparsedNotificationsAfter = unparsedNotificationsRepo.find().asScala.toSeq
        parsedNotificationsAfter.length mustBe 0
        unparsedNotificationsAfter.length mustBe 1

        unparsedNotificationsAfter.foreach { unparsedNotificationAfter =>
          unparsedNotificationAfter.get("item", classOf[Document]).getString("actionId") mustBe unparsedNotification
            .get("item", classOf[Document])
            .getString("actionId")
          unparsedNotificationAfter.get("item", classOf[Document]).getString("payload") mustBe unparsedNotification
            .get("item", classOf[Document])
            .getString("payload")
        }
      }
    }
  }

  private def compareJson(actual: String, expected: String): Unit = {
    val mapper = new ObjectMapper

    val jsonActual = mapper.readTree(actual)
    val jsonExpected = mapper.readTree(expected)

    jsonActual mustBe jsonExpected
  }
}

object SplitTheNotificationsCollectionISpec {

  trait TestData {
    val parsedNotificationBeforeChanges: String
    val parsedNotificationAfterChanges: String
    val unparsedNotification: String
  }

  object TestData_1 extends TestData {

    override val parsedNotificationBeforeChanges: String = parsedNotificationBeforeChanges("5fc0d62750c11c0797a2456f")

    def parsedNotificationBeforeChanges(_id: String): String =
      s"""{
        |   "_id" : {"$$oid":"${_id}"},
        |   "actionId" : "b1c09f1b-7c94-4e90-b754-7c5c71c44e11",
        |   "payload" : "RhzDC5lT6ZrWSCHrtoy6wUkL2VdjXLNm8N6PdbyFLpE1jnBqigGsgHuS4CrPO5J8vLtkUZZe0nkNseSa9x5Epb3yuOgZYB6uV272UwCx2utsdqb2U5XEblaYNaWUdifZl9IJVNo39yy0A3XpWdB3avkKCxSn1qNW5Ar3CE4uSi1D5C4oEFUDoQqo484Y9Kt8BtQsabBzC8IaR2vUMhOEJHk5j7HJDIeQ0JGujOWY9QBm13LfAYfBoIrM3GYBJcgR45L9NfVIwVAlkkXbtsaqdVwpRyuayuy8VYyyhyFk4Uc3",
        |   "details" : {
        |       "mrn" : "MRN87878797",
        |       "dateTimeIssued" : "2020-11-27T10:37:10.918Z[UTC]",
        |       "status" : "UNKNOWN",
        |       "errors" : [
        |           {
        |               "validationCode" : "CDS12056",
        |               "pointer" : "42A"
        |           }
        |       ]
        |   }
        |}
        |""".stripMargin

    override val parsedNotificationAfterChanges: String =
      """{
        |   "_id" : {"$oid":"5fc0d62750c11c0797a2456f"},
        |   "actionId" : "b1c09f1b-7c94-4e90-b754-7c5c71c44e11",
        |   "unparsedNotificationId" : "e4b75f7b-7c94-4e90-b754-7c5c31c45e63",
        |   "details" : {
        |       "mrn" : "MRN87878797",
        |       "dateTimeIssued" : "2020-11-27T10:37:10.918Z[UTC]",
        |       "status" : "UNKNOWN",
        |       "errors" : [
        |           {
        |               "validationCode" : "CDS12056",
        |               "pointer" : "42A"
        |           }
        |       ]
        |   }
        |}
        |""".stripMargin

    override val unparsedNotification: String =
      """{
        |    "_id" : {"$oid":"5fc0d62750c11c0797a24000"},
        |    "availableAt" : "2021-04-16T09:12:36.218Z",
        |    "updatedAt" : "2021-04-16T09:12:36.241Z",
        |    "failureCount" : 0,
        |    "status" : "succeeded",
        |    "receivedAt" : "2021-04-16T09:12:36.218Z",
        |    "item" : {
        |       "id" : "e4b75f7b-7c94-4e90-b754-7c5c31c45e63",
        |       "actionId" : "b1c09f1b-7c94-4e90-b754-7c5c71c44e11",
        |       "payload" : "RhzDC5lT6ZrWSCHrtoy6wUkL2VdjXLNm8N6PdbyFLpE1jnBqigGsgHuS4CrPO5J8vLtkUZZe0nkNseSa9x5Epb3yuOgZYB6uV272UwCx2utsdqb2U5XEblaYNaWUdifZl9IJVNo39yy0A3XpWdB3avkKCxSn1qNW5Ar3CE4uSi1D5C4oEFUDoQqo484Y9Kt8BtQsabBzC8IaR2vUMhOEJHk5j7HJDIeQ0JGujOWY9QBm13LfAYfBoIrM3GYBJcgR45L9NfVIwVAlkkXbtsaqdVwpRyuayuy8VYyyhyFk4Uc3"
        |    }
        |}
        |""".stripMargin
  }

  object TestData_2 extends TestData {
    override val parsedNotificationBeforeChanges: String =
      """{
        |   "_id" : {"$oid":"5fc0d62750c11c0797a2457a"},
        |   "actionId" : "b1c09f1b-7c94-4e90-b754-7c5c71c44e22",
        |   "payload" : "RhzDC5lT6ZrWSCHrtoy6wUkL2VdjXLNm8N6PdbyFLpE1jnBqigGsgHuS4CrPO5J8vLtkUZZe0nkNseSa9x5Epb3yuOgZYB6uV272UwCx2utsdqb2U5XEblaYNaWUdifZl9IJVNo39yy0A3XpWdB3avkKCxSn1qNW5Ar3CE4uSi1D5C4oEFUDoQqo484Y9Kt8BtQsabBzC8IaR2vUMhOEJHk5j7HJDIeQ0JGujOWY9QBm13LfAYfBoIshbf874fhbsfW4hfHY6fhvs5dfSFgqdVwpRyuayuy8VYyyhyFk4Uc3",
        |   "details" : {
        |       "mrn" : "MRN87878797",
        |       "dateTimeIssued" : "2020-11-27T10:37:11.917Z[UTC]",
        |       "status" : "UNKNOWN",
        |       "errors" : [
        |           {
        |               "validationCode" : "CDS12056",
        |               "pointer" : "42A"
        |           }
        |       ]
        |   }
        |}
        |""".stripMargin

    override val parsedNotificationAfterChanges: String =
      """{
        |   "_id" : {"$oid":"5fc0d62750c11c0797a2457a"},
        |   "actionId" : "b1c09f1b-7c94-4e90-b754-7c5c71c44e22",
        |   "unparsedNotificationId" : "e4b75f7b-7c94-4e90-b754-7c5c31c45e64",
        |   "details" : {
        |       "mrn" : "MRN87878798",
        |       "dateTimeIssued" : "2020-11-27T10:37:10.918Z[UTC]",
        |       "status" : "UNKNOWN",
        |       "errors" : [
        |           {
        |               "validationCode" : "CDS12056",
        |               "pointer" : "42A"
        |           }
        |       ]
        |   }
        |}
        |""".stripMargin

    override val unparsedNotification: String =
      """{
        |    "_id" : {"$oid":"5fc0d62750c11c0797a24001"},
        |    "availableAt" : "2021-04-16T09:12:36.218Z",
        |    "updatedAt" : "2021-04-16T09:12:36.241Z",
        |    "failureCount" : 0,
        |    "status" : "succeeded",
        |    "receivedAt" : "2021-04-16T09:12:36.218Z",
        |    "item" : {
        |       "id" : "e4b75f7b-7c94-4e90-b754-7c5c31c45e64",
        |       "actionId" : "b1c09f1b-7c94-4e90-b754-7c5c71c44e22",
        |       "payload" : "RhzDC5lT6ZrWSCHrtoy6wUkL2VdjXLNm8N6PdbyFLpE1jnBqigGsgHuS4CrPO5J8vLtkUZZe0nkNseSa9x5Epb3yuOgZYB6uV272UwCx2utsdqb2U5XEblaYNaWUdifZl9IJVNo39yy0A3XpWdB3avkKCxSn1qNW5Ar3CE4uSi1D5C4oEFUDoQqo484Y9Kt8BtQsabBzC8IaR2vUMhOEJHk5j7HJDIeQ0JGujOWY9QBm13LfAYfBoIshbf874fhbsfW4hfHY6fhvs5dfSFgqdVwpRyuayuy8VYyyhyFk4Uc3"
        |    }
        |}
        |""".stripMargin
  }

  object TestData_3 extends TestData {
    override val parsedNotificationBeforeChanges: String =
      """{
        |   "_id" : {"$oid":"5fc0d62750c11c0797a2458b"},
        |   "actionId" : "b1c09f1b-7c94-4e90-b754-7c5c71c44e33",
        |   "payload" : "RhzDC5lT6ZrWSCHrtoy6wUkL2VdjXLNm8N6PdbyFLpE1jnBqigGsgHuS4CrPO5J8vLtkUZZe0nkNseSa9x5Epb3yuOgZYB6uV272UwCx2utsdqb2U5XEblaYNaWUdifZl9IJVNo39yy0A3XpWdB3avkKCxSn1qNW5Ar3CE4uSi1D5C4oEFUDoQqo484Y9Kt8BtQsabBzC8IaR2vUMhOEJHk5j7HJDIeQ0JGujOWY9QBm13LfAYfBoIshbf874fhbsfW4hfHYhs84yb5f8cDJdgjDFJ6854rgh356usgDY473",
        |   "details" : {
        |       "mrn" : "MRN87878797",
        |       "dateTimeIssued" : "2020-11-27T10:37:12.917Z[UTC]",
        |       "status" : "UNKNOWN",
        |       "errors" : [
        |           {
        |               "validationCode" : "CDS12056",
        |               "pointer" : "42A"
        |           }
        |       ]
        |   }
        |}
        |""".stripMargin

    override val parsedNotificationAfterChanges: String =
      """{
        |   "_id" : {"$oid":"5fc0d62750c11c0797a2458b"},
        |   "actionId" : "b1c09f1b-7c94-4e90-b754-7c5c71c44e33",
        |   "unparsedNotificationId" : "e4b75f7b-7c94-4e90-b754-7c5c31c45e65",
        |   "details" : {
        |       "mrn" : "MRN87878798",
        |       "dateTimeIssued" : "2020-11-27T10:37:10.918Z[UTC]",
        |       "status" : "UNKNOWN",
        |       "errors" : [
        |           {
        |               "validationCode" : "CDS12056",
        |               "pointer" : "42A"
        |           }
        |       ]
        |   }
        |}
        |""".stripMargin

    override val unparsedNotification: String =
      """{
        |    "_id" : {"$oid":"5fc0d62750c11c0797a24002"},
        |    "availableAt" : "2021-04-16T09:12:36.218Z",
        |    "updatedAt" : "2021-04-16T09:12:36.241Z",
        |    "failureCount" : 0,
        |    "status" : "succeeded",
        |    "receivedAt" : "2021-04-16T09:12:36.218Z",
        |    "item" : {
        |       "id" : "e4b75f7b-7c94-4e90-b754-7c5c31c45e65",
        |       "actionId" : "b1c09f1b-7c94-4e90-b754-7c5c71c44e33",
        |       "payload" : "RhzDC5lT6ZrWSCHrtoy6wUkL2VdjXLNm8N6PdbyFLpE1jnBqigGsgHuS4CrPO5J8vLtkUZZe0nkNseSa9x5Epb3yuOgZYB6uV272UwCx2utsdqb2U5XEblaYNaWUdifZl9IJVNo39yy0A3XpWdB3avkKCxSn1qNW5Ar3CE4uSi1D5C4oEFUDoQqo484Y9Kt8BtQsabBzC8IaR2vUMhOEJHk5j7HJDIeQ0JGujOWY9QBm13LfAYfBoIshbf874fhbsfW4hfHYhs84yb5f8cDJdgjDFJ6854rgh356usgDY473"
        |    }
        |}
        |""".stripMargin
  }
}
