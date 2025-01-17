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

package uk.gov.hmrc.exports.models.declaration

import play.api.libs.json.{JsString, Json, JsonValidationError, Reads, Writes}

trait AuthorisationProcedureCode

object AuthorisationProcedureCode {
  case object Code1040 extends AuthorisationProcedureCode
  case object Code1007 extends AuthorisationProcedureCode
  case object CodeOther extends AuthorisationProcedureCode

  val values = Seq(Code1040, Code1007, CodeOther)

  lazy val lookupByValue: Map[String, AuthorisationProcedureCode] = values.map(entry => entry.toString -> entry).toMap

  implicit val reads: Reads[AuthorisationProcedureCode] = Reads.StringReads.collect(JsonValidationError("error.unknown"))(lookupByValue)
  implicit val writes: Writes[AuthorisationProcedureCode] = Writes(code => JsString(code.toString))
}

case class AuthorisationProcedureCodeChoice(code: AuthorisationProcedureCode)

object AuthorisationProcedureCodeChoice {
  implicit val format = Json.format[AuthorisationProcedureCodeChoice]
}
