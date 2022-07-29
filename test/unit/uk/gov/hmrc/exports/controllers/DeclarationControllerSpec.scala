/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.exports.controllers

import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => eqRef, _}
import org.mockito.BDDMockito._
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.auth.core.{AuthConnector, InsufficientEnrolments}
import uk.gov.hmrc.exports.base.{AuthTestSupport, UnitSpec}
import uk.gov.hmrc.exports.controllers.request.ExportsDeclarationRequest
import uk.gov.hmrc.exports.models._
import uk.gov.hmrc.exports.models.declaration.ExportsDeclaration.REST.writes
import uk.gov.hmrc.exports.models.declaration.{DeclarationStatus, ExportsDeclaration}
import uk.gov.hmrc.exports.services.DeclarationService
import uk.gov.hmrc.exports.util.ExportsDeclarationBuilder

import java.time.Instant
import scala.concurrent.Future

class DeclarationControllerSpec extends UnitSpec with GuiceOneAppPerSuite with AuthTestSupport with ExportsDeclarationBuilder {

  SharedMetricRegistries.clear()

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].to(mockAuthConnector), bind[DeclarationService].to(declarationService))
    .build()

  private val declarationService: DeclarationService = mock[DeclarationService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector, declarationService)
  }

  "POST /declarations" should {
    val post = FakeRequest("POST", "/declarations")

    "return 201" when {
      "request is valid" in {
        withAuthorizedUser()
        val request = aDeclarationRequest()
        val declaration = aDeclaration(withType(DeclarationType.STANDARD), withId("id"), withEori(userEori))
        given(declarationService.create(any[ExportsDeclaration])).willReturn(Future.successful(declaration))

        val result: Future[Result] = route(app, post.withJsonBody(toJson(request))).get

        status(result) must be(CREATED)
        contentAsJson(result) mustBe toJson(declaration)
        theDeclarationCreated.eori mustBe userEori.value
      }
    }

    "return 400" when {
      "invalid json" in {
        withAuthorizedUser()
        val payload = Json.toJson(aDeclarationRequest()).as[JsObject] - "type"
        val result: Future[Result] = route(app, post.withJsonBody(payload)).get

        status(result) must be(BAD_REQUEST)
        contentAsJson(result) mustBe Json.obj("message" -> "Bad Request", "errors" -> Json.arr("/type: error.path.missing"))
        verifyNoInteractions(declarationService)
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(InsufficientEnrolments())

        val result: Future[Result] = route(app, post.withJsonBody(toJson(aDeclarationRequest()))).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(declarationService)
      }
    }
  }

  "GET /" should {
    val get = FakeRequest("GET", "/declarations")

    "return 200" when {
      "valid request" in {
        withAuthorizedUser()
        val declaration = aDeclaration(withId("id"), withEori(userEori))
        given(declarationService.find(any[DeclarationSearch], any[Page], any[DeclarationSort]))
          .willReturn(Future.successful(Paginated(declaration)))

        val result: Future[Result] = route(app, get).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(Paginated(declaration))
        theSearch mustBe DeclarationSearch(eori = userEori, status = None)
      }

      "request has valid pagination" in {
        withAuthorizedUser()
        val declaration = aDeclaration(withId("id"), withEori(userEori))
        given(declarationService.find(any[DeclarationSearch], any[Page], any[DeclarationSort]))
          .willReturn(Future.successful(Paginated(declaration)))

        val get = FakeRequest("GET", "/declarations?page-index=1&page-size=100")
        val result: Future[Result] = route(app, get).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(Paginated(declaration))
        thePagination mustBe Page(1, 100)
      }

      "request has valid search params" in {
        withAuthorizedUser()
        val declaration = aDeclaration(withId("id"), withEori(userEori))
        given(declarationService.find(any[DeclarationSearch], any[Page], any[DeclarationSort]))
          .willReturn(Future.successful(Paginated(declaration)))

        val get = FakeRequest("GET", "/declarations?status=COMPLETE")
        val result: Future[Result] = route(app, get).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(Paginated(declaration))
        theSearch mustBe DeclarationSearch(eori = userEori, status = Some(DeclarationStatus.COMPLETE))
      }

      "request has invalid search params" in {
        withAuthorizedUser()
        val declaration = aDeclaration(withId("id"), withEori(userEori))
        given(declarationService.find(any[DeclarationSearch], any[Page], any[DeclarationSort]))
          .willReturn(Future.successful(Paginated(declaration)))

        val get = FakeRequest("GET", "/declarations?status=invalid")
        val result: Future[Result] = route(app, get).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(Paginated(declaration))
        theSearch mustBe DeclarationSearch(eori = userEori, status = None)
      }

      "request has sorting ascending sort params" in {
        withAuthorizedUser()
        val declaration = aDeclaration(withId("id"), withEori(userEori))
        given(declarationService.find(any[DeclarationSearch], any[Page], any[DeclarationSort]))
          .willReturn(Future.successful(Paginated(declaration)))

        val get = FakeRequest("GET", "/declarations?sort-by=updatedDateTime&sort-direction=asc")
        val result: Future[Result] = route(app, get).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(Paginated(declaration))
        theSort mustBe DeclarationSort(by = SortBy.UPDATED, direction = SortDirection.ASC)
      }

      "request has sorting descending sort params" in {
        withAuthorizedUser()
        val declaration = aDeclaration(withId("id"), withEori(userEori))
        given(declarationService.find(any[DeclarationSearch], any[Page], any[DeclarationSort]))
          .willReturn(Future.successful(Paginated(declaration)))

        val get = FakeRequest("GET", "/declarations?sort-by=createdDateTime&sort-direction=des")
        val result: Future[Result] = route(app, get).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(Paginated(declaration))
        theSort mustBe DeclarationSort(by = SortBy.CREATED, direction = SortDirection.DES)
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(InsufficientEnrolments())

        val result: Future[Result] = route(app, get).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(declarationService)
      }
    }

    def theSearch: DeclarationSearch = {
      val captor: ArgumentCaptor[DeclarationSearch] = ArgumentCaptor.forClass(classOf[DeclarationSearch])
      verify(declarationService).find(captor.capture(), any[Page], any[DeclarationSort])
      captor.getValue
    }

    def theSort: DeclarationSort = {
      val captor: ArgumentCaptor[DeclarationSort] = ArgumentCaptor.forClass(classOf[DeclarationSort])
      verify(declarationService).find(any[DeclarationSearch], any[Page], captor.capture())
      captor.getValue
    }

    def thePagination: Page = {
      val captor: ArgumentCaptor[Page] = ArgumentCaptor.forClass(classOf[Page])
      verify(declarationService).find(any[DeclarationSearch], captor.capture(), any[DeclarationSort])
      captor.getValue
    }
  }

  "GET /declarations/:id" should {
    val get = FakeRequest("GET", "/declarations/id")

    "return 200" when {
      "request is valid" in {
        withAuthorizedUser()
        val declaration = aDeclaration(withId("id"), withEori(userEori))
        given(declarationService.findOne(any[Eori](), anyString())).willReturn(Future.successful(Some(declaration)))

        val result: Future[Result] = route(app, get).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(declaration)
        verify(declarationService).findOne(userEori, "id")
      }
    }

    "return 404" when {
      "id is not found" in {
        withAuthorizedUser()
        given(declarationService.findOne(any(), anyString())).willReturn(Future.successful(None))

        val result: Future[Result] = route(app, get).get

        status(result) must be(NOT_FOUND)
        contentAsString(result) mustBe empty
        verify(declarationService).findOne(eqRef(userEori), eqRef("id"))
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(InsufficientEnrolments())

        val result: Future[Result] = route(app, get).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(declarationService)
      }
    }
  }

  "DELETE /declarations/:id" should {
    val delete = FakeRequest("DELETE", "/declarations/id")

    "return 204" when {
      "request is valid" in {
        withAuthorizedUser()
        val declaration = aDeclaration(withId("id"), withEori(userEori), withStatus(DeclarationStatus.DRAFT))
        given(declarationService.findOne(any(), anyString())).willReturn(Future.successful(Some(declaration)))
        given(declarationService.deleteOne(any[ExportsDeclaration])).willReturn(Future.successful(true))

        val result: Future[Result] = route(app, delete).get

        status(result) must be(NO_CONTENT)
        contentAsString(result) mustBe empty
        verify(declarationService).findOne(userEori, "id")
        verify(declarationService).deleteOne(declaration)
      }
    }

    "return 400" when {
      "declaration is COMPLETE" in {
        withAuthorizedUser()
        val declaration = aDeclaration(withId("id"), withEori(userEori), withStatus(DeclarationStatus.COMPLETE))
        given(declarationService.findOne(any(), anyString())).willReturn(Future.successful(Some(declaration)))

        val result: Future[Result] = route(app, delete).get

        status(result) must be(BAD_REQUEST)
        contentAsJson(result) mustBe Json.obj("message" -> "Cannot remove a declaration once it is COMPLETE")
        verify(declarationService).findOne(userEori, "id")
        verify(declarationService, never).deleteOne(declaration)
      }
    }

    "return 204" when {
      "id is not found" in {
        withAuthorizedUser()
        given(declarationService.findOne(any(), anyString())).willReturn(Future.successful(None))

        val result: Future[Result] = route(app, delete).get

        status(result) must be(NO_CONTENT)
        contentAsString(result) mustBe empty
        verify(declarationService).findOne(userEori, "id")
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(InsufficientEnrolments())

        val result: Future[Result] = route(app, delete).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(declarationService)
      }
    }
  }

  "PUT /declarations/:id" should {
    val put = FakeRequest("PUT", "/declarations/id")

    "return 200" when {
      "request is valid" in {
        withAuthorizedUser()
        val request = aDeclarationRequest()
        val declaration = aDeclaration(withStatus(DeclarationStatus.DRAFT), withType(DeclarationType.STANDARD), withId("id"), withEori(userEori))
        given(declarationService.update(any[ExportsDeclaration])).willReturn(Future.successful(Some(declaration)))

        val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(declaration)
        val updatedDeclaration = theDeclarationUpdated
        updatedDeclaration.eori mustBe userEori.value
        updatedDeclaration.id mustBe "id"
      }
    }

    "return 404" when {
      "declaration is not found - on find" in {
        withAuthorizedUser()
        val request = aDeclarationRequest()
        given(declarationService.update(any[ExportsDeclaration])).willReturn(Future.successful(None))

        val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get

        status(result) must be(NOT_FOUND)
        contentAsString(result) mustBe empty
      }

      "declaration is not found - on update" in {
        withAuthorizedUser()
        val request = aDeclarationRequest()
        given(declarationService.update(any[ExportsDeclaration])).willReturn(Future.successful(None))

        val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get

        status(result) must be(NOT_FOUND)
        contentAsString(result) mustBe empty
      }
    }

    "return 400" when {

      "invalid json" in {
        withAuthorizedUser()
        val payload = Json.toJson(aDeclarationRequest()).as[JsObject] - "type"
        val result: Future[Result] = route(app, put.withJsonBody(payload)).get

        status(result) must be(BAD_REQUEST)
        contentAsJson(result) mustBe Json.obj("message" -> "Bad Request", "errors" -> Json.arr("/type: error.path.missing"))
        verifyNoInteractions(declarationService)
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(InsufficientEnrolments())

        val result: Future[Result] = route(app, put.withJsonBody(toJson(aDeclarationRequest()))).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(declarationService)
      }
    }
  }

  "GET /draft-declaration/:parentId" should {
    val newId = "newId"
    val parentId = "parentId"
    val get = FakeRequest("GET", "/draft-declaration/parentId")

    "return 200" when {
      "a draft declaration with 'parentDeclarationId' equal to the given parentId is found" in {
        withAuthorizedUser()
        when(declarationService.findOrCreateDraftFromParent(any[Eori](), refEq(parentId))(any()))
          .thenReturn(Future.successful((DeclarationService.FOUND, newId)))

        val result: Future[Result] = route(app, get).get

        status(result) must be(OK)
        contentAsJson(result).as[String] mustBe newId
      }
    }

    "return 201" when {
      "a draft declaration with 'parentDeclarationId' equal to the given parentId is created" in {
        withAuthorizedUser()
        when(declarationService.findOrCreateDraftFromParent(any[Eori](), refEq(parentId))(any()))
          .thenReturn(Future.successful((DeclarationService.CREATED, newId)))

        val result: Future[Result] = route(app, get).get

        status(result) must be(CREATED)
        contentAsJson(result).as[String] mustBe newId
      }
    }
  }

  def aDeclarationRequest() =
    ExportsDeclarationRequest(createdDateTime = Instant.now, updatedDateTime = Instant.now, `type` = DeclarationType.STANDARD)

  def theDeclarationCreated: ExportsDeclaration = {
    val captor: ArgumentCaptor[ExportsDeclaration] = ArgumentCaptor.forClass(classOf[ExportsDeclaration])
    verify(declarationService).create(captor.capture)
    captor.getValue
  }

  def theDeclarationUpdated: ExportsDeclaration = {
    val captor: ArgumentCaptor[ExportsDeclaration] = ArgumentCaptor.forClass(classOf[ExportsDeclaration])
    verify(declarationService).update(captor.capture)
    captor.getValue
  }
}
