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

import org.mongodb.scala.model.{Filters, Updates}
import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.exports.connectors.CustomsDeclarationsConnector
import uk.gov.hmrc.exports.connectors.ead.CustomsDeclarationsInformationConnector
import uk.gov.hmrc.exports.metrics.ExportsMetrics
import uk.gov.hmrc.exports.metrics.ExportsMetrics.Timers
import uk.gov.hmrc.exports.models.declaration.ExportsDeclaration
import uk.gov.hmrc.exports.models.declaration.submissions.EnhancedStatus._
import uk.gov.hmrc.exports.models.declaration.submissions.StatusGroup.{StatusGroup, SubmittedStatuses}
import uk.gov.hmrc.exports.models.declaration.submissions._
import uk.gov.hmrc.exports.models.{Eori, FetchSubmissionPageData, Mrn, PageOfSubmissions}
import uk.gov.hmrc.exports.repositories.{DeclarationRepository, SubmissionRepository}
import uk.gov.hmrc.exports.services.mapping.{AmendmentMetaDataBuilder, CancellationMetaDataBuilder, ExportsPointerToWCOPointer}
import uk.gov.hmrc.exports.services.reversemapping.MappingContext
import uk.gov.hmrc.exports.services.reversemapping.declaration.ExportsDeclarationXmlParser
import uk.gov.hmrc.http.HeaderCarrier
import wco.datamodel.wco.documentmetadata_dms._2.MetaData

import javax.inject.{Inject, Singleton}
import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionService @Inject() (
  customsDeclarationsConnector: CustomsDeclarationsConnector,
  customsDeclarationsInformationConnector: CustomsDeclarationsInformationConnector,
  submissionRepository: SubmissionRepository,
  declarationRepository: DeclarationRepository,
  exportsPointerToWCOPointer: ExportsPointerToWCOPointer,
  cancelMetaDataBuilder: CancellationMetaDataBuilder,
  amendmentMetaDataBuilder: AmendmentMetaDataBuilder,
  exportsDeclarationXmlParser: ExportsDeclarationXmlParser,
  wcoMapperService: WcoMapperService,
  metrics: ExportsMetrics
)(implicit executionContext: ExecutionContext)
    extends Logging {

  def fetchFirstPage(eori: String, statusGroups: Seq[StatusGroup], limit: Int): Future[PageOfSubmissions] = {
    // When multiple StatusGroup(s) are provided, the fetch proceeds in sequence group by group.
    // When a page/batch of Submissions (the first page actually, 1 to limit) is found for a group,
    // that page is the result of the fetch. The successive groups are ignored.

    @nowarn("msg=match may not be exhaustive")
    def loopOnGroups(f: StatusGroup => Future[Seq[Submission]])(cond: Seq[Submission] => Boolean)(seq: Seq[StatusGroup]): Future[Seq[Submission]] =
      seq match {
        case head :: tail => f(head).filter(cond).recoverWith { case _: Throwable => loopOnGroups(f)(cond)(tail) }
        case Nil          => Future.successful(Seq.empty)
      }

    for {
      submissions <- loopOnGroups(submissionRepository.fetchFirstPage(eori, _, limit))(_.nonEmpty)(statusGroups)
      totalSubmissionsInGroup <- countSubmissionsInGroup(eori, submissions)
    } yield {
      val statusGroup = submissions.headOption.flatMap(_.latestEnhancedStatus.map(toStatusGroup)).getOrElse(SubmittedStatuses)
      PageOfSubmissions(statusGroup, totalSubmissionsInGroup, submissions)
    }
  }

  def fetchFirstPage(eori: String, statusGroup: StatusGroup, limit: Int): Future[PageOfSubmissions] =
    // Fetch first page of submissions for the provided StatusGroup
    fetchPage(eori, statusGroup, () => submissionRepository.fetchFirstPage(eori, statusGroup, limit))

  def fetchPage(eori: String, statusGroup: StatusGroup, fetchPageData: FetchSubmissionPageData): Future[PageOfSubmissions] = {

    val fetchFunction = (fetchPageData.datetimeForPreviousPage, fetchPageData.datetimeForNextPage, fetchPageData.page) match {
      case (Some(datetimeForPreviousPage), _, _) =>
        // datetimeForPreviousPage provided => fetching the page BEFORE the last one returned
        () => submissionRepository.fetchPreviousPage(eori, statusGroup, datetimeForPreviousPage, fetchPageData.limit)

      case (_, Some(datetimeForNextPage), _) =>
        // datetimeForPreviousPage NOT provided and datetimeForNextPage provided => fetching the page AFTER the last one returned
        () => submissionRepository.fetchNextPage(eori, statusGroup, datetimeForNextPage, fetchPageData.limit)

      case (_, _, Some(page)) =>
        // datetimeForPreviousPage and datetimeForNextPage NOT provided and page provided => fetching a specific page
        () => submissionRepository.fetchLoosePage(eori, statusGroup, page, fetchPageData.limit)

      case _ =>
        // datetimeForPreviousPage, datetimeForNextPage and page NOT provided => fetching the last page
        () => submissionRepository.fetchLastPage(eori, statusGroup, fetchPageData.limit)
    }

    fetchPage(eori, statusGroup, fetchFunction)
  }

  private def fetchPage(eori: String, statusGroup: StatusGroup, f: () => Future[Seq[Submission]]): Future[PageOfSubmissions] =
    for {
      submissions <- f()
      totalSubmissionsInGroup <- countSubmissionsInGroup(eori, submissions)
    } yield PageOfSubmissions(statusGroup, totalSubmissionsInGroup, submissions)

  private def countSubmissionsInGroup(eori: String, submissions: Seq[Submission]): Future[Int] =
    // When no submissions are found, there is no need to run an unnecessary 'count' query.
    submissions.headOption
      .flatMap(_.latestEnhancedStatus.map(toStatusGroup))
      .fold(Future.successful(0))(submissionRepository.countSubmissionsInGroup(eori, _))

  def findSubmission(eori: String, id: String): Future[Option[Submission]] =
    submissionRepository.findById(eori, id)

  def findSubmissionsByLrn(eori: String, lrn: String): Future[Seq[Submission]] =
    submissionRepository.findByLrn(eori, lrn)

  def markCompleted(eori: Eori, id: String): Future[Option[ExportsDeclaration]] =
    declarationRepository.markStatusAsComplete(eori, id)

  def submit(declaration: ExportsDeclaration)(implicit hc: HeaderCarrier): Future[Submission] =
    metrics.timeAsyncCall(ExportsMetrics.submissionMonitor) {
      logProgress(declaration.id, "Beginning Declaration Submission")

      val metaData = metrics.timeCall(Timers.submissionProduceMetaDataTimer)(wcoMapperService.produceMetaData(declaration))

      val lrn = wcoMapperService
        .declarationLrn(metaData)
        .getOrElse(throw new IllegalArgumentException("A LRN is required"))

      val ducr = wcoMapperService
        .declarationDucr(metaData)
        .getOrElse(throw new IllegalArgumentException("A DUCR is required"))

      val payload = metrics.timeCall(Timers.submissionConvertToXmlTimer)(wcoMapperService.toXml(metaData))

      logProgress(declaration.id, "Submitting new declaration to the Declaration API")
      for {
        // Submit the declaration to the Dec API
        // Revert the declaration status back to DRAFT if it fails
        actionId: String <- metrics.timeAsyncCall(Timers.submissionSendToDecApiTimer)(submit(declaration, payload))
        _ = logProgress(declaration.id, "Submitted new declaration to the Declaration API Successfully")

        // Create the Submission with action
        action = Action(id = actionId, SubmissionRequest, decId = Some(declaration.id), versionNo = 1)

        submission <- metrics.timeAsyncCall(Timers.submissionFindOrCreateSubmissionTimer)(
          submissionRepository.create(Submission(declaration, lrn, ducr, action))
        )
        _ = logProgress(declaration.id, "New submission creation completed")
      } yield submission
    }

  def fetchExternalAmendmentToUpdateSubmission(mrn: Mrn, eori: Eori, actionId: String, submissionId: String)(
    implicit hc: HeaderCarrier
  ): Future[Option[Submission]] =
    customsDeclarationsInformationConnector.fetchMrnFullDeclaration(mrn.value, None) flatMap { xml =>
      exportsDeclarationXmlParser.fromXml(MappingContext(eori.value), xml.toString).toOption match {
        case Some(declaration) =>
          val update = for {
            _ <- declarationRepository.create(declaration)
            submission <- submissionRepository.updateAction(submissionId, actionId, declaration.id)
          } yield submission

          update flatMap { submission =>
            updateDecId(submission, actionId, declaration.id)
          }
        case _ =>
          Future.successful(None)
      }
    }

  private def updateDecId(updatedSubmission: Option[Submission], actionId: String, declarationId: String): Future[Option[Submission]] = {

    def findAction(submission: Submission): Action => Boolean = { action =>
      action.id == actionId && action.versionNo == submission.latestVersionNo
    }

    updatedSubmission match {
      case Some(submission) if submission.actions.exists(findAction(submission)) =>
        submissionRepository.findOneAndUpdate(Filters.eq("uuid", submission.uuid), Updates.set("latestDecId", declarationId))
      case submission => Future.successful(submission)
    }
  }

  private def isSubmissionAlreadyCancelled(submission: Submission): Boolean =
    submission.actions.find(_.requestType == CancellationRequest) match {
      case Some(action) => action.latestNotificationSummary.fold(false)(_.enhancedStatus == CUSTOMS_POSITION_GRANTED)
      case _            => false
    }

  private def logProgress(declarationId: String, message: String): Unit =
    logger.info(s"Declaration [$declarationId]: $message")

  private def submit(declaration: ExportsDeclaration, payload: String)(implicit hc: HeaderCarrier): Future[String] =
    customsDeclarationsConnector.submitDeclaration(declaration.eori, payload).recoverWith { case throwable: Throwable =>
      logProgress(declaration.id, "Submission failed")
      declarationRepository.revertStatusToDraft(declaration) flatMap { _ =>
        logProgress(declaration.id, "Reverted declaration to DRAFT")
        Future.failed[String](throwable)
      }
    }

  def cancel(eori: String, cancellation: SubmissionCancellation)(implicit hc: HeaderCarrier): Future[CancellationStatus] =
    submissionRepository.findOne(Json.obj("eori" -> eori, "uuid" -> cancellation.submissionId)).flatMap {
      case Some(submission) if isSubmissionAlreadyCancelled(submission) => Future.successful(CancellationAlreadyRequested)
      case Some(submission)                                             => sendCancellationRequest(submission, cancellation)
      case _                                                            => Future.successful(NotFound)
    }

  private def sendCancellationRequest(submission: Submission, cancellation: SubmissionCancellation)(
    implicit hc: HeaderCarrier
  ): Future[CancellationStatus] = {
    val metadata: MetaData = cancelMetaDataBuilder.buildRequest(
      cancellation.functionalReferenceId,
      cancellation.mrn,
      cancellation.statementDescription,
      cancellation.changeReason,
      submission.eori
    )

    val xml: String = wcoMapperService.toXml(metadata)
    customsDeclarationsConnector.submitCancellation(submission, xml).flatMap { actionId =>
      updateSubmissionWithCancellationAction(cancellation.mrn, actionId, submission)
    }
  }

  private def updateSubmissionWithCancellationAction(mrn: String, actionId: String, submission: Submission): Future[CancellationStatus] = {
    val newAction = Action(id = actionId, CancellationRequest, decId = Some(submission.uuid), versionNo = submission.latestVersionNo)
    submissionRepository.addAction(mrn, newAction).map {
      case Some(_) => CancellationRequestSent
      case None    => NotFound
    }
  }

  def amend(eori: Eori, amendment: SubmissionAmendment, declaration: ExportsDeclaration)(implicit hc: HeaderCarrier): Future[String] =
    metrics.timeAsyncCall(ExportsMetrics.amendmentMonitor) {
      logProgress(amendment.declarationId, "Beginning Amend Submission.")

      val submissionLookup = submissionRepository.findOne(Json.obj("eori" -> eori, "uuid" -> amendment.submissionId)) map {
        case Some(submission) => submission
        case _                => throw new NoSuchElementException("No submission matching eori and id was found.")
      }

      (for {
        submission <- submissionLookup
        actionId <- sendAmendmentRequest(declaration, submission, amendment.fieldPointers)
      } yield actionId).recoverWith { case throwable: Throwable =>
        logProgress(declaration.id, "Amendment failed")
        declarationRepository.revertStatusToAmendmentDraft(declaration) flatMap { _ =>
          logProgress(declaration.id, "Reverted amendment to AMENDMENT_DRAFT")
          Future.failed[String](throwable)
        }
      }
    }

  private def sendAmendmentRequest(declaration: ExportsDeclaration, submission: Submission, fieldPointers: Seq[String])(
    implicit hc: HeaderCarrier
  ): Future[String] = {
    val wcoPointers = fieldPointers.flatMap(exportsPointerToWCOPointer.getWCOPointers).distinct
    val metadata = metrics.timeCall(Timers.amendmentProduceMetaDataTimer)(amendmentMetaDataBuilder.buildRequest(declaration, wcoPointers))
    val xml = metrics.timeCall(Timers.amendmentConvertToXmlTimer)(wcoMapperService.toXml(metadata))

    logProgress(declaration.id, "Submitting amendment request to the Declaration API")
    for {
      actionId <- metrics.timeCall(Timers.amendmentSendToDecApiTimer)(customsDeclarationsConnector.submitAmendment(declaration.eori, xml))
      _ = logProgress(declaration.id, "Submitted amendment request to the Declaration API Successfully")
      _ = logProgress(declaration.id, "Appending amendment action to submission...")
      _ <- updateSubmissionWithAmendmentAction(actionId, declaration.id, submission)
    } yield actionId
  }

  private def updateSubmissionWithAmendmentAction(actionId: String, decId: String, submission: Submission): Future[Unit] = {
    val newAction = Action(id = actionId, AmendmentRequest, decId = Some(decId), versionNo = submission.latestVersionNo)
    submissionRepository.addAction(submission.uuid, newAction).map {
      case Some(_) => logger.info(s"Updated submission with id ${submission.uuid} successfully with amendment action.")
      case _       => throw new NoSuchElementException(s"Failed to find submission with id ${submission.uuid} to update with amendment action.")
    }
  }
}
