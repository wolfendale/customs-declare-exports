/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.exports.models.declaration

import play.api.libs.json.Json

case class Submission(
  eori: String,
  conversationId: String,
  ducr: Option[String] = None,
  lrn: Option[String] = None,
  mrn: Option[String] = None,
  submittedTimestamp: Long = System.currentTimeMillis(),
  status: String,
  isCancellationRequested: Boolean = false
)

//case class Submission(
//  uuid: String,
//  eori: String,
//  ducr: Option[String],
//  lrn: String,
//  mrn: Option[String],
//  status: String,
//  actions: Seq[String]
//)

object Submission {
  implicit val formats = Json.format[Submission]
}
