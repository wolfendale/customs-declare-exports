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

package unit.uk.gov.hmrc.exports.base

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.exports.connectors.CustomsDeclarationsConnector
import uk.gov.hmrc.exports.metrics.ExportsMetrics
import uk.gov.hmrc.exports.models.CustomsDeclarationsResponse
import uk.gov.hmrc.exports.repositories.{NotificationRepository, SubmissionRepository}
import uk.gov.hmrc.exports.services.{NotificationService, SubmissionService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

object UnitTestMockBuilder extends MockitoSugar {

  def buildCustomsDeclarationsConnectorMock: CustomsDeclarationsConnector = {
    val customsDeclarationsConnectorMock: CustomsDeclarationsConnector = mock[CustomsDeclarationsConnector]
    when(customsDeclarationsConnectorMock.submitDeclaration(any(), any())(any()))
      .thenReturn(Future.successful(CustomsDeclarationsResponse.empty))
    when(customsDeclarationsConnectorMock.submitCancellation(any(), any())(any()))
      .thenReturn(Future.successful(CustomsDeclarationsResponse.empty))
    customsDeclarationsConnectorMock
  }

  def buildExportsMetricsMock: ExportsMetrics = {
    val exportsMetricsMock: ExportsMetrics = mock[ExportsMetrics]
    when(exportsMetricsMock.startTimer(any())).thenCallRealMethod()
    exportsMetricsMock
  }

  def buildSubmissionRepositoryMock: SubmissionRepository = {
    val submissionRepositoryMock: SubmissionRepository = mock[SubmissionRepository]
    when(submissionRepositoryMock.findAllSubmissionsForEori(any())).thenReturn(Future.successful(Seq.empty))
    when(submissionRepositoryMock.findSubmissionByMrn(any())).thenReturn(Future.successful(None))
    when(submissionRepositoryMock.findSubmissionByConversationId(any())).thenReturn(Future.successful(None))
    when(submissionRepositoryMock.findSubmissionByUuid(any())).thenReturn(Future.successful(None))
    when(submissionRepositoryMock.save(any())).thenReturn(Future.successful(false))
    when(submissionRepositoryMock.updateMrn(any(), any())).thenReturn(Future.successful(None))
    when(submissionRepositoryMock.addAction(any(), any())).thenReturn(Future.successful(None))
    submissionRepositoryMock
  }

  def buildSubmissionServiceMock: SubmissionService = {
    val submissionServiceMock: SubmissionService = mock[SubmissionService]
    implicit val hc: HeaderCarrier = mock[HeaderCarrier]
    when(submissionServiceMock.getAllSubmissionsForUser(any())).thenReturn(Future.successful(Seq.empty))
    when(submissionServiceMock.getSubmission(any())).thenReturn(Future.successful(None))
    when(submissionServiceMock.getSubmissionByConversationId(any())).thenReturn(Future.successful(None))
    when(submissionServiceMock.save(any(), any())(any())).thenReturn(Future.successful(Left("")))
    when(submissionServiceMock.cancelDeclaration(any(), any())(any())).thenReturn(Future.successful(Left("")))
    submissionServiceMock
  }

  def buildNotificationRepositoryMock: NotificationRepository = {
    val notificationRepositoryMock: NotificationRepository = mock[NotificationRepository]
    when(notificationRepositoryMock.findNotificationsByConversationId(any())).thenReturn(Future.successful(Seq.empty))
    when(notificationRepositoryMock.findNotificationsByConversationIds(any())).thenReturn(Future.successful(Seq.empty))
    when(notificationRepositoryMock.save(any())).thenReturn(Future.successful(false))
    notificationRepositoryMock
  }

  def buildNotificationServiceMock: NotificationService = {
    val notificationServiceMock: NotificationService = mock[NotificationService]
    when(notificationServiceMock.getAllNotificationsForUser(any())).thenReturn(Future.successful(Seq.empty))
    when(notificationServiceMock.getNotificationsForSubmission(any())).thenReturn(Future.successful(Seq.empty))
    when(notificationServiceMock.saveAll(any())).thenReturn(Future.successful(Left("")))
    when(notificationServiceMock.save(any())).thenReturn(Future.successful(Left("")))
    notificationServiceMock
  }
}