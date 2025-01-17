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

package uk.gov.hmrc.exports.migrations.changelogs.submission

import uk.gov.hmrc.exports.base.IntegrationTestMigrationToolSpec
import uk.gov.hmrc.exports.migrations.changelogs.submission.AddSubmissionFieldsForAmendISpec.{submissionAfterMigration, submissionBeforeMigration}

class AddSubmissionFieldsForAmendISpec extends IntegrationTestMigrationToolSpec {

  override val collectionUnderTest = "submissions"
  override val changeLog = new AddSubmissionFieldsForAmend()

  "AddSubmissionFieldsForAmend" should {

    "not update a Submission document" when {
      "the document already has a 'latestDecId' field" in {
        runTest(submissionAfterMigration, submissionAfterMigration)
      }
    }

    "update a Submission document" when {
      "the document does not have 'latestDecId' field" in {
        runTest(submissionBeforeMigration, submissionAfterMigration)
      }
    }
  }
}

object AddSubmissionFieldsForAmendISpec {

  val submissionBeforeMigration =
    """{
      |  "_id" : "63d66e937810526f3351847d",
      |  "uuid" : "b140390f-56d4-4302-887f-5971886cb0e7",
      |  "eori" : "LU167499736454300",
      |  "lrn" : "MNllQR6rcV",
      |  "ducr" : "9CF857491229489-1S228",
      |  "actions" : [
      |    {
      |      "id" : "7ed4d825-8cc6-4d9f-abc2-05fa92d65e85",
      |      "requestType" : "SubmissionRequest",
      |      "requestTimestamp" : "2022-05-11T09:43:41.962Z[UTC]",
      |      "notifications" : [
      |        {
      |          "notificationId" : "b389d173-f5d0-44a8-9307-670890d32625",
      |          "dateTimeIssued" : "2022-06-17T10:10:36Z[UTC]",
      |          "enhancedStatus" : "ERRORS"
      |        }
      |      ]
      |    }
      |  ],
      |  "mrn" : "20GBFYLCAYVUPGJPJPYF",
      |  "enhancedStatusLastUpdated" : "2020-12-01T17:33:31Z[UTC]",
      |  "latestEnhancedStatus" : "ERRORS"
      |}""".stripMargin

  val submissionAfterMigration =
    """{
      |  "_id" : "63d66e937810526f3351847d",
      |  "uuid" : "b140390f-56d4-4302-887f-5971886cb0e7",
      |  "eori" : "LU167499736454300",
      |  "lrn" : "MNllQR6rcV",
      |  "ducr" : "9CF857491229489-1S228",
      |  "actions" : [
      |    {
      |      "id" : "7ed4d825-8cc6-4d9f-abc2-05fa92d65e85",
      |      "requestType" : "SubmissionRequest",
      |      "requestTimestamp" : "2022-05-11T09:43:41.962Z[UTC]",
      |      "notifications" : [
      |        {
      |          "notificationId" : "b389d173-f5d0-44a8-9307-670890d32625",
      |          "dateTimeIssued" : "2022-06-17T10:10:36Z[UTC]",
      |          "enhancedStatus" : "ERRORS"
      |        }
      |      ]
      |    }
      |  ],
      |  "mrn" : "20GBFYLCAYVUPGJPJPYF",
      |  "enhancedStatusLastUpdated" : "2020-12-01T17:33:31Z[UTC]",
      |  "latestEnhancedStatus" : "ERRORS",
      |  "latestDecId" : "b140390f-56d4-4302-887f-5971886cb0e7",
      |  "latestVersionNo" : 1,
      |  "blockAmendments" : false
      |}""".stripMargin
}
