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

import play.api.libs.json.Json
import uk.gov.hmrc.exports.models.declaration.notifications.ParsedNotification
import uk.gov.hmrc.exports.models.declaration.submissions.EnhancedStatus.EnhancedStatus

import java.time.ZonedDateTime
import java.util.UUID

case class NotificationSummary(notificationId: UUID, dateTimeIssued: ZonedDateTime, enhancedStatus: EnhancedStatus)
    extends Ordered[NotificationSummary] {
  override def compare(that: NotificationSummary): Int = dateTimeIssued.compareTo(that.dateTimeIssued)
}

object NotificationSummary {

  implicit val formats = Json.format[NotificationSummary]

  def apply(notification: ParsedNotification, actions: Seq[Action], notificationSummaries: Seq[NotificationSummary]): NotificationSummary =
    new NotificationSummary(
      notificationId = notification.unparsedNotificationId,
      dateTimeIssued = notification.details.dateTimeIssued,
      enhancedStatus = EnhancedStatus(notification, actions, notificationSummaries)
    )
}
