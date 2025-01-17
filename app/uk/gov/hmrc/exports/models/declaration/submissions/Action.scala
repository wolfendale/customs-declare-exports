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

package uk.gov.hmrc.exports.models.declaration.submissions

import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}

case class Action(
  id: String,
  requestType: RequestType,
  decId: Option[String],
  versionNo: Int,
  notifications: Option[Seq[NotificationSummary]] = None,
  requestTimestamp: ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"))
) {
  val latestNotificationSummary: Option[NotificationSummary] =
    notifications.flatMap(_.lastOption)
}

object Action {

  val defaultDateTimeZone: ZoneId = ZoneId.of("UTC")

  implicit val readLocalDateTimeFromString: Reads[ZonedDateTime] = implicitly[Reads[LocalDateTime]]
    .map(ZonedDateTime.of(_, ZoneId.of("UTC")))

  implicit val writes = Json.writes[Action]
  implicit val reads: Reads[Action] =
    ((__ \ "id").read[String] and
      (__ \ "requestType").read[RequestType] and
      (__ \ "decId").readNullable[String] and
      (__ \ "versionNo").read[Int] and
      (__ \ "notifications").readNullable[Seq[NotificationSummary]] and
      ((__ \ "requestTimestamp").read[ZonedDateTime] or (__ \ "requestTimestamp").read[ZonedDateTime](readLocalDateTimeFromString)))(Action.apply _)
}
