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

package uk.gov.hmrc.exports.services

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => meq}
import org.mockito.ArgumentMatchers._
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.invocation.InvocationOnMock
import org.scalatest.Assertion
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.exports.base.{MockMetrics, UnitSpec}
import uk.gov.hmrc.exports.connectors.CustomsDeclarationsConnector
import uk.gov.hmrc.exports.connectors.ead.CustomsDeclarationsInformationConnector
import uk.gov.hmrc.exports.models.FetchSubmissionPageData.DEFAULT_LIMIT
import uk.gov.hmrc.exports.models.declaration.submissions.EnhancedStatus.{CUSTOMS_POSITION_GRANTED, WITHDRAWN}
import uk.gov.hmrc.exports.models.declaration.submissions.StatusGroup._
import uk.gov.hmrc.exports.models.declaration.submissions._
import uk.gov.hmrc.exports.models.{Eori, FetchSubmissionPageData, PageOfSubmissions}
import uk.gov.hmrc.exports.repositories.{DeclarationRepository, SubmissionRepository}
import uk.gov.hmrc.exports.services.mapping.{AmendmentMetaDataBuilder, CancellationMetaDataBuilder, ExportsPointerToWCOPointer}
import uk.gov.hmrc.exports.services.notifications.receiptactions.SendEmailForDmsDocAction
import uk.gov.hmrc.exports.services.reversemapping.declaration.ExportsDeclarationXmlParser
import uk.gov.hmrc.exports.util.ExportsDeclarationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import wco.datamodel.wco.documentmetadata_dms._2.MetaData

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class SubmissionServiceSpec extends UnitSpec with ExportsDeclarationBuilder with MockMetrics {

  private implicit val hc: HeaderCarrier = mock[HeaderCarrier]
  private val customsDeclarationsConnector: CustomsDeclarationsConnector = mock[CustomsDeclarationsConnector]
  private val submissionRepository: SubmissionRepository = mock[SubmissionRepository]
  private val declarationRepository: DeclarationRepository = mock[DeclarationRepository]
  private val exportsPointerToWCOPointer: ExportsPointerToWCOPointer = mock[ExportsPointerToWCOPointer]
  private val cancelMetaDataBuilder: CancellationMetaDataBuilder = mock[CancellationMetaDataBuilder]
  private val amendMetaDataBuilder: AmendmentMetaDataBuilder = mock[AmendmentMetaDataBuilder]
  private val wcoMapperService: WcoMapperService = mock[WcoMapperService]
  private val sendEmailForDmsDocAction: SendEmailForDmsDocAction = mock[SendEmailForDmsDocAction]
  private val mockCustomsDeclarationsInformationConnector = mock[CustomsDeclarationsInformationConnector]
  private val mockExportsDeclarationXmlParser = mock[ExportsDeclarationXmlParser]

  private val submissionService = new SubmissionService(
    customsDeclarationsConnector = customsDeclarationsConnector,
    submissionRepository = submissionRepository,
    declarationRepository = declarationRepository,
    exportsPointerToWCOPointer = exportsPointerToWCOPointer,
    cancelMetaDataBuilder = cancelMetaDataBuilder,
    amendmentMetaDataBuilder = amendMetaDataBuilder,
    customsDeclarationsInformationConnector = mockCustomsDeclarationsInformationConnector,
    exportsDeclarationXmlParser = mockExportsDeclarationXmlParser,
    wcoMapperService = wcoMapperService,
    metrics = exportsMetrics
  )(ExecutionContext.global)

  override def afterEach(): Unit = {
    reset(
      customsDeclarationsConnector,
      submissionRepository,
      declarationRepository,
      exportsPointerToWCOPointer,
      cancelMetaDataBuilder,
      amendMetaDataBuilder,
      wcoMapperService,
      sendEmailForDmsDocAction
    )
    super.afterEach()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(submissionRepository.countSubmissionsInGroup(any(), any())).thenReturn(Future.successful(1))
  }
  private val eori = "eori"
  private val id = "id"
  private val xml = "xml"
  private val submission = Submission(id, eori, "lrn", None, "ducr", latestDecId = Some(id))

  "SubmissionService.cancel" should {
    val notification = Some(Seq(new NotificationSummary(UUID.randomUUID(), ZonedDateTime.now(), CUSTOMS_POSITION_GRANTED)))
    val submissionCancelled = Submission(
      id,
      eori,
      "lrn",
      None,
      "ducr",
      None,
      None,
      List(Action(id = "conv-id", requestType = CancellationRequest, notifications = notification, decId = Some(id), versionNo = 1)),
      latestDecId = Some(id)
    )
    val cancellation = SubmissionCancellation(id, "ref-id", "mrn", "description", "reason")

    "submit and delegate to repository and iterates version number in cancel action from submission" when {
      "submission exists" which {
        "copies version number to cancel action from submission" in {
          when(cancelMetaDataBuilder.buildRequest(any(), any(), any(), any(), any())).thenReturn(mock[MetaData])
          when(wcoMapperService.toXml(any())).thenReturn(xml)
          when(customsDeclarationsConnector.submitCancellation(any(), any())(any())).thenReturn(Future.successful("conv-id"))
          when(submissionRepository.findOne(any[JsValue])).thenReturn(Future.successful(Some(submission)))

          val captor: ArgumentCaptor[Action] = ArgumentCaptor.forClass(classOf[Action])

          when(submissionRepository.addAction(any[String](), any[Action]())).thenReturn(Future.successful(Some(submission)))

          submissionService.cancel(eori, cancellation).futureValue mustBe CancellationRequestSent

          verify(submissionRepository)
            .addAction(any[String](), captor.capture())

          captor.getValue.decId mustBe submission.latestDecId
          captor.getValue.versionNo mustBe submission.latestVersionNo
        }
      }

      "submission is missing" in {
        when(cancelMetaDataBuilder.buildRequest(any(), any(), any(), any(), any())).thenReturn(mock[MetaData])
        when(wcoMapperService.toXml(any())).thenReturn(xml)
        when(customsDeclarationsConnector.submitCancellation(any(), any())(any())).thenReturn(Future.successful("conv-id"))
        when(submissionRepository.findOne(any[JsValue])).thenReturn(Future.successful(None))

        submissionService.cancel(eori, cancellation).futureValue mustBe NotFound
      }

      "submission exists and previously cancelled" in {
        when(cancelMetaDataBuilder.buildRequest(any(), any(), any(), any(), any())).thenReturn(mock[MetaData])
        when(wcoMapperService.toXml(any())).thenReturn(xml)
        when(customsDeclarationsConnector.submitCancellation(any(), any())(any())).thenReturn(Future.successful("conv-id"))
        when(submissionRepository.findOne(any[JsValue])).thenReturn(Future.successful(Some(submissionCancelled)))

        submissionService.cancel(eori, cancellation).futureValue mustBe CancellationAlreadyRequested
      }
    }
  }

  private val cancelledSubmissions = List(submission.copy(latestEnhancedStatus = Some(WITHDRAWN)))

  "SubmissionService.fetchFirstPage" should {

    "fetch the 1st page of the 1st StatusGroup containing submissions" in {
      val captor: ArgumentCaptor[StatusGroup] = ArgumentCaptor.forClass(classOf[StatusGroup])

      def isCancelledGroup(invocation: InvocationOnMock) =
        invocation.getArgument(1).asInstanceOf[StatusGroup] == CancelledStatuses

      when(submissionRepository.countSubmissionsInGroup(any(), captor.capture())).thenAnswer { invocation: InvocationOnMock =>
        Future.successful(if (isCancelledGroup(invocation)) 1 else 0)
      }

      when(submissionRepository.fetchFirstPage(any(), captor.capture(), any[Int])).thenAnswer { invocation: InvocationOnMock =>
        Future.successful(if (isCancelledGroup(invocation)) cancelledSubmissions else Seq.empty)
      }

      val statuses = List(ActionRequiredStatuses, RejectedStatuses, SubmittedStatuses, CancelledStatuses)
      verifPageOfSubmissions(submissionService.fetchFirstPage(eori, statuses, DEFAULT_LIMIT).futureValue)

      val numberOfInvocations = 4
      verify(submissionRepository, times(numberOfInvocations)).fetchFirstPage(any(), any(), any())
    }

    "fetch the first page of a specific StatusGroup" in {
      when(submissionRepository.fetchFirstPage(any(), any[StatusGroup], any[Int])).thenReturn(Future.successful(cancelledSubmissions))

      verifPageOfSubmissions(submissionService.fetchFirstPage(eori, CancelledStatuses, DEFAULT_LIMIT).futureValue)
    }
  }

  "SubmissionService.fetchPage" should {

    val now = ZonedDateTime.now

    def fetchSubmissionPageData(
      datetimeForPreviousPage: Option[ZonedDateTime] = Some(now),
      datetimeForNextPage: Option[ZonedDateTime] = Some(now.plusSeconds(1L)),
      page: Option[Int] = Some(2)
    ): FetchSubmissionPageData =
      FetchSubmissionPageData(List(CancelledStatuses), datetimeForPreviousPage, datetimeForNextPage, page, DEFAULT_LIMIT)

    "call submissionRepository.fetchNextPage" when {
      "in FetchSubmissionPageData, statusGroup and datetimeForNextPage are provided and" when {
        "datetimeForPreviousPage is not provided" in {
          when(submissionRepository.fetchNextPage(any(), any[StatusGroup], any[ZonedDateTime], any[Int]))
            .thenReturn(Future.successful(cancelledSubmissions))

          verifPageOfSubmissions(submissionService.fetchPage(eori, CancelledStatuses, fetchSubmissionPageData(None)).futureValue)

          verify(submissionRepository).fetchNextPage(any(), eqTo(CancelledStatuses), eqTo(now.plusSeconds(1L)), eqTo(DEFAULT_LIMIT))

          verify(submissionRepository, never).fetchLastPage(any(), any(), any())
          verify(submissionRepository, never).fetchLoosePage(any(), any(), any(), any())
          verify(submissionRepository, never).fetchPreviousPage(any(), any(), any(), any())
        }
      }
    }

    "call submissionRepository.fetchPreviousPage" when {
      "in FetchSubmissionPageData, statusGroup and datetimeForPreviousPage are provided" in {
        when(submissionRepository.fetchPreviousPage(any(), any[StatusGroup], any[ZonedDateTime], any[Int]))
          .thenReturn(Future.successful(cancelledSubmissions))

        verifPageOfSubmissions(submissionService.fetchPage(eori, CancelledStatuses, fetchSubmissionPageData()).futureValue)

        verify(submissionRepository).fetchPreviousPage(any(), eqTo(CancelledStatuses), eqTo(now), eqTo(DEFAULT_LIMIT))

        verify(submissionRepository, never).fetchLastPage(any(), any(), any())
        verify(submissionRepository, never).fetchLoosePage(any(), any(), any(), any())
        verify(submissionRepository, never).fetchNextPage(any(), any(), any(), any())
      }
    }

    "call submissionRepository.fetchLoosePage" when {
      "in FetchSubmissionPageData, statusGroup and page are provided and" when {
        "datetimeForNextPage and datetimeForPreviousPage are not provided" in {
          when(submissionRepository.fetchLoosePage(any(), any[StatusGroup], any[Int], any[Int]))
            .thenReturn(Future.successful(cancelledSubmissions))

          verifPageOfSubmissions(submissionService.fetchPage(eori, CancelledStatuses, fetchSubmissionPageData(None, None)).futureValue)

          verify(submissionRepository).fetchLoosePage(any(), eqTo(CancelledStatuses), eqTo(2), eqTo(DEFAULT_LIMIT))

          verify(submissionRepository, never).fetchLastPage(any(), any(), any())
          verify(submissionRepository, never).fetchNextPage(any(), any(), any(), any())
          verify(submissionRepository, never).fetchPreviousPage(any(), any(), any(), any())
        }
      }
    }

    "call submissionRepository.fetchLastPage" when {
      "in FetchSubmissionPageData, only the statusGroup is provided" in {
        when(submissionRepository.fetchLastPage(any(), any[StatusGroup], any[Int]))
          .thenReturn(Future.successful(cancelledSubmissions))

        verifPageOfSubmissions(submissionService.fetchPage(eori, CancelledStatuses, fetchSubmissionPageData(None, None, None)).futureValue)

        verify(submissionRepository).fetchLastPage(any(), eqTo(CancelledStatuses), eqTo(DEFAULT_LIMIT))

        verify(submissionRepository, never).fetchLoosePage(any(), any(), any(), any())
        verify(submissionRepository, never).fetchNextPage(any(), any(), any(), any())
        verify(submissionRepository, never).fetchPreviousPage(any(), any(), any(), any())
      }
    }
  }

  private def verifPageOfSubmissions(pageOfSubmissions: PageOfSubmissions): Assertion = {
    pageOfSubmissions.statusGroup mustBe CancelledStatuses
    pageOfSubmissions.submissions mustBe cancelledSubmissions
    pageOfSubmissions.totalSubmissionsInGroup mustBe 1
  }

  "SubmissionService.submit" should {

    def theSubmissionCreated(): Submission = {
      val captor: ArgumentCaptor[Submission] = ArgumentCaptor.forClass(classOf[Submission])
      verify(submissionRepository).create(captor.capture())
      captor.getValue
    }

    "submit to the Dec API" when {
      val declaration = aDeclaration()

      val dateTimeIssued = ZonedDateTime.now(ZoneOffset.UTC)

      val newAction =
        Action(id = "conv-id", requestType = SubmissionRequest, requestTimestamp = dateTimeIssued, decId = Some(declaration.id), versionNo = 1)

      val submission = Submission(declaration, "lrn", "mrn", newAction)

      "declaration is valid" in {
        // Given
        when(wcoMapperService.produceMetaData(any())).thenReturn(mock[MetaData])
        when(wcoMapperService.declarationLrn(any())).thenReturn(Some("lrn"))
        when(wcoMapperService.declarationDucr(any())).thenReturn(Some("ducr"))
        when(wcoMapperService.toXml(any())).thenReturn(xml)
        when(submissionRepository.create(any())).thenReturn(Future.successful(submission))
        when(customsDeclarationsConnector.submitDeclaration(any(), any())(any())).thenReturn(Future.successful("conv-id"))

        // When
        submissionService.submit(declaration).futureValue mustBe submission

        // Then
        val submissionCreated = theSubmissionCreated()
        val actionGenerated = submissionCreated.actions.head
        submissionCreated mustBe Submission(declaration, "lrn", "ducr", newAction.copy(requestTimestamp = actionGenerated.requestTimestamp))

        actionGenerated.id mustBe "conv-id"
        actionGenerated.requestType mustBe SubmissionRequest

        verify(submissionRepository, never).findOne(any[String], any[String])
        verify(sendEmailForDmsDocAction, never).execute(any[String])
      }
    }

    "throw exception" when {
      "missing LRN" in {
        when(wcoMapperService.produceMetaData(any())).thenReturn(mock[MetaData])
        when(wcoMapperService.declarationLrn(any())).thenReturn(None)
        when(wcoMapperService.declarationDucr(any())).thenReturn(Some("ducr"))

        intercept[IllegalArgumentException] {
          submissionService.submit(aDeclaration()).futureValue
        }
      }

      "missing DUCR" in {
        when(wcoMapperService.produceMetaData(any())).thenReturn(mock[MetaData])
        when(wcoMapperService.declarationLrn(any())).thenReturn(Some("lrn"))
        when(wcoMapperService.declarationDucr(any())).thenReturn(None)

        intercept[IllegalArgumentException] {
          submissionService.submit(aDeclaration()).futureValue
        }
      }
    }
  }

  "SubmissionService.amend" should {
    val fieldPointers = Seq("pointers")
    val wcoPointers = Seq("wco")
    val amendmentId = "amendmentId"
    val actionId = "actionId"
    val submissionAmendment = SubmissionAmendment(id, amendmentId, fieldPointers)
    val dec = aDeclaration(withId(amendmentId), withEori(eori), withConsignmentReferences(mrn = Some("mrn")))
    val metadata = mock[MetaData]

    "throw appropriate exception and revert the status of the amendment to AMENDMENT_DRAFT" when {
      "initial submission lookup does not return a submission" in {
        when(submissionRepository.findOne(any[JsValue])).thenReturn(Future.successful(None))
        when(amendMetaDataBuilder.buildRequest(any(), any())).thenReturn(metadata)
        when(customsDeclarationsConnector.submitAmendment(any(), any())(any())).thenReturn(Future.successful(actionId))
        when(submissionRepository.addAction(any(), any())).thenReturn(Future.successful(Some(submission)))
        when(declarationRepository.revertStatusToAmendmentDraft(any())).thenReturn(Future.successful(Some(dec)))

        assertThrows[java.util.NoSuchElementException] {
          await(submissionService.amend(Eori(eori), submissionAmendment, dec))
        }
        verify(declarationRepository).revertStatusToAmendmentDraft(meq(dec))
      }
      "updating submission with amendment action fails" in {
        when(submissionRepository.findOne(any[JsValue])).thenReturn(Future.successful(Some(submission)))
        when(exportsPointerToWCOPointer.getWCOPointers(any())).thenReturn(Seq(""))
        when(amendMetaDataBuilder.buildRequest(any(), any())).thenReturn(metadata)
        when(wcoMapperService.toXml(any())).thenReturn(xml)
        when(customsDeclarationsConnector.submitAmendment(any(), any())(any())).thenReturn(Future.successful(actionId))
        when(submissionRepository.addAction(any(), any())).thenReturn(Future.successful(None))
        when(declarationRepository.revertStatusToAmendmentDraft(any())).thenReturn(Future.successful(Some(dec)))

        assertThrows[java.util.NoSuchElementException] {
          await(submissionService.amend(Eori(eori), submissionAmendment, dec))
        }
        verify(declarationRepository).revertStatusToAmendmentDraft(meq(dec))
      }
    }

    "call expected methods and return an actionId" in {
      when(submissionRepository.findOne(any[JsValue])).thenReturn(Future.successful(Some(submission)))
      when(exportsPointerToWCOPointer.getWCOPointers(any())).thenReturn(wcoPointers)
      when(amendMetaDataBuilder.buildRequest(any(), any())).thenReturn(metadata)
      when(wcoMapperService.toXml(any())).thenReturn(xml)
      when(customsDeclarationsConnector.submitAmendment(any(), any())(any())).thenReturn(Future.successful(actionId))
      when(submissionRepository.addAction(any(), any())).thenReturn(Future.successful(Some(submission)))

      val result = submissionService.amend(Eori(eori), submissionAmendment, dec)

      verify(submissionRepository).findOne(meq(Json.obj("eori" -> eori, "uuid" -> submission.uuid)))
      verify(exportsPointerToWCOPointer).getWCOPointers(fieldPointers.head)
      verify(amendMetaDataBuilder).buildRequest(meq(dec), meq(wcoPointers))
      verify(wcoMapperService).toXml(meq(metadata))
      verify(customsDeclarationsConnector).submitAmendment(meq(eori), meq(xml))(any())
      val captor: ArgumentCaptor[Action] = ArgumentCaptor.forClass(classOf[Action])
      verify(submissionRepository).addAction(meq(id), captor.capture())

      captor.getValue.id mustBe actionId
      captor.getValue.requestType mustBe AmendmentRequest
      captor.getValue.decId mustBe Some(amendmentId)
      captor.getValue.versionNo mustBe submission.latestVersionNo
      await(result) mustBe actionId
    }
  }
}
