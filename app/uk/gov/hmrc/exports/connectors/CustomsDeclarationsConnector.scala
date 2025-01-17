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

package uk.gov.hmrc.exports.connectors

import com.google.inject.Inject
import play.api.Logging
import play.api.http.{ContentTypes, HeaderNames, Status}
import play.api.mvc.Codec
import play.mvc.Http.Status.ACCEPTED
import uk.gov.hmrc.exports.config.AppConfig
import uk.gov.hmrc.exports.controllers.util.CustomsHeaderNames
import uk.gov.hmrc.exports.controllers.util.CustomsHeaderNames.SubmissionConversationId
import uk.gov.hmrc.exports.metrics.ExportsMetrics
import uk.gov.hmrc.exports.metrics.ExportsMetrics.Timers
import uk.gov.hmrc.exports.models.CustomsDeclarationsResponse
import uk.gov.hmrc.exports.models.declaration.submissions.{Submission, SubmissionRequest}
import uk.gov.hmrc.http.HttpReads.is4xx
import uk.gov.hmrc.http.{HttpClient, _}

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CustomsDeclarationsConnector @Inject() (appConfig: AppConfig, httpClient: HttpClient, metrics: ExportsMetrics)(implicit ec: ExecutionContext)
    extends Logging {

  def submitDeclaration(eori: String, xml: String)(implicit hc: HeaderCarrier): Future[String] =
    post(eori, appConfig.submitDeclarationUri, xml).map(handleResponse)
  def submitAmendment(eori: String, xml: String)(implicit hc: HeaderCarrier): Future[String] =
    post(eori, appConfig.amendDeclarationUri, xml).map(handleResponse)

  def submitCancellation(submission: Submission, xml: String)(implicit hc: HeaderCarrier): Future[String] = {
    def actionId: String = submission.actions.find(_.requestType == SubmissionRequest).fold("Not a SubmissionRequest?")(_.id)
    val headerCarrier = if (appConfig.isUpstreamStubbed) hc.withExtraHeaders(SubmissionConversationId -> actionId) else hc
    post(submission.eori, appConfig.cancelDeclarationUri, xml)(headerCarrier).map(handleResponse)
  }

  private def handleResponse(response: CustomsDeclarationsResponse) = {
    logger.debug(s"CUSTOMS_DECLARATIONS response is  --> ${response.toString}")
    response match {
      case CustomsDeclarationsResponse(ACCEPTED, Some(conversationId)) => conversationId
      case CustomsDeclarationsResponse(status, Some(message)) =>
        throw new InternalServerException(s"Customs Declarations Service returned [$status] with error message: $message")
      case CustomsDeclarationsResponse(status, _) =>
        throw new InternalServerException(s"Customs Declarations Service returned [$status]")
    }
  }

  private def post(eori: String, uri: String, body: String)(implicit hc: HeaderCarrier): Future[CustomsDeclarationsResponse] = {
    logger.debug(s"CUSTOMS_DECLARATIONS request payload is -> $body")

    metrics.timeAsyncCall(Timers.upstreamCustomsDeclarationsTimer) {
      httpClient
        .POSTString[CustomsDeclarationsResponse](s"${appConfig.customsDeclarationsBaseUrl}$uri", body, headers = headers(eori))
        .recover { case error: Throwable =>
          logger.error(s"Error during submitting declaration: ${error.getMessage}")

          error match {
            case response: UpstreamErrorResponse if is4xx(response.statusCode) =>
              val conversationId = response.headers.get("X-Conversation-ID") match {
                case Some(data) => data.head
                case None       => "No conversation ID found"
              }

              CustomsDeclarationsResponse(Status.INTERNAL_SERVER_ERROR, Some(conversationId))
            case _ => CustomsDeclarationsResponse(Status.INTERNAL_SERVER_ERROR, Some(error.getMessage))
          }
        }
    }
  }

  private def headers(eori: String): Seq[(String, String)] = Seq(
    "X-Client-ID" -> appConfig.developerHubClientId,
    HeaderNames.ACCEPT -> s"application/vnd.hmrc.${appConfig.customsDeclarationsApiVersion}+xml",
    HeaderNames.CONTENT_TYPE -> ContentTypes.XML(Codec.utf_8),
    CustomsHeaderNames.XEoriIdentifierHeaderName -> eori
  )

  private val ApplicationErrorStatus = 4
  private val ServerErrorStatus = 5

  // noinspection ConvertExpressionToSAM
  private implicit val responseReader: HttpReads[CustomsDeclarationsResponse] =
    new HttpReads[CustomsDeclarationsResponse] {
      override def read(method: String, url: String, response: HttpResponse): CustomsDeclarationsResponse = {
        logger.debug(s"Response: ${response.status} => ${response.body}")
        getHttpResponseStatusType(response) match {
          case ApplicationErrorStatus =>
            throw UpstreamErrorResponse(
              message = "Invalid request made to Customs Declarations API",
              statusCode = response.status,
              reportAs = Status.INTERNAL_SERVER_ERROR,
              headers = response.headers
            )
          case ServerErrorStatus =>
            throw UpstreamErrorResponse(
              message = "Customs Declarations API unable to service request",
              statusCode = response.status,
              reportAs = Status.INTERNAL_SERVER_ERROR
            )
          case _ =>
            logger.debug(s"Received status:${response.status} with headers: ${response.headers}")
            CustomsDeclarationsResponse(
              response.status,
              Some(
                response
                  .header("X-Conversation-ID")
                  .getOrElse(
                    throw UpstreamErrorResponse(
                      message = "Conversation ID missing from Customs Declaration API response",
                      statusCode = response.status,
                      reportAs = Status.INTERNAL_SERVER_ERROR
                    )
                  )
              )
            )
        }
      }
    }

  private def getHttpResponseStatusType(response: HttpResponse): Int = response.status / 100

}
