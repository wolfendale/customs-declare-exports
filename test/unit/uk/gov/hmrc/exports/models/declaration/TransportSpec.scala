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

import play.api.libs.json.{JsSuccess, Json}
import uk.gov.hmrc.exports.base.UnitSpec
import uk.gov.hmrc.exports.models.declaration.ModeOfTransportCode.{Maritime, Rail}
import uk.gov.hmrc.exports.models.declaration.Transport.expressConsignmentPointer
import uk.gov.hmrc.exports.services.AlteredField
import uk.gov.hmrc.exports.services.AlteredField.constructAlteredField

class TransportSpec extends UnitSpec {

  "Transport formats" should {

    val json = Json.obj(
      "expressConsignment" -> Json.obj("answer" -> "Yes"),
      "transportPayment" -> Json.obj("paymentMethod" -> "payment-method"),
      "containers" -> Json.arr(Json.obj("id" -> "container-id", "seals" -> Json.arr(Json.obj("id" -> "seal-id")))),
      "borderModeOfTransportCode" -> Json.obj("code" -> "3"),
      "meansOfTransportOnDepartureType" -> "means-of-transport-on-departure",
      "meansOfTransportOnDepartureIDNumber" -> "means-of-transport-on-departure-id-number",
      "transportCrossingTheBorderNationality" -> Json.obj("countryName" -> "crossing-the-border-nationality"),
      "meansOfTransportCrossingTheBorderType" -> "crossing-the-border-type",
      "meansOfTransportCrossingTheBorderIDNumber" -> "crossing-the-border-id-number"
    )

    val transport = Transport(
      expressConsignment = Some(YesNoAnswer.yes),
      transportPayment = Some(TransportPayment(paymentMethod = "payment-method")),
      containers = Some(Seq(Container(id = "container-id", seals = Seq(Seal(id = "seal-id"))))),
      borderModeOfTransportCode = Some(TransportLeavingTheBorder(Some(ModeOfTransportCode.Road))),
      meansOfTransportOnDepartureType = Some("means-of-transport-on-departure"),
      meansOfTransportOnDepartureIDNumber = Some("means-of-transport-on-departure-id-number"),
      transportCrossingTheBorderNationality = Some(TransportCountry(Some("crossing-the-border-nationality"))),
      meansOfTransportCrossingTheBorderType = Some("crossing-the-border-type"),
      meansOfTransportCrossingTheBorderIDNumber = Some("crossing-the-border-id-number")
    )

    "convert Transport object to JSON" in {

      val resultJson = Transport.format.writes(transport)

      resultJson mustBe json
    }

    "convert JSON to Transport object" in {

      val resultTransport = Transport.format.reads(json)

      resultTransport mustBe JsSuccess(transport)
    }
  }

  "Transport.createDiff" should {
    val baseFieldPointer = Transport.pointer
    "produce the expected ExportsDeclarationDiff instance" when {
      "no differences exist between the two versions" in {
        val transport = Transport()
        transport.createDiff(transport, Transport.pointer) mustBe Seq.empty[AlteredField]
      }

      "the original version's expressConsignment field has a different value to this one" in {
        val fieldPointer = s"${baseFieldPointer}.$expressConsignmentPointer"
        withClue("both versions have Some values but values are different") {
          val transport = Transport(expressConsignment = Some(YesNoAnswer.yes))
          val originalValue = YesNoAnswer.no
          transport.createDiff(transport.copy(expressConsignment = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.expressConsignment.get)
          )
        }

        withClue("the original version's expressConsignment field is None but this one has Some value") {
          val transport = Transport(expressConsignment = Some(YesNoAnswer.yes))
          val originalValue = None
          transport.createDiff(transport.copy(expressConsignment = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.expressConsignment)
          )
        }

        withClue("the original version's expressConsignment field is Some but this one has None as its value") {
          val transport = Transport(expressConsignment = None)
          val originalValue = Some(YesNoAnswer.no)
          transport.createDiff(transport.copy(expressConsignment = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.expressConsignment)
          )
        }

        withClue("both versions have None values") {
          val transport = Transport(expressConsignment = None)
          transport.createDiff(transport, Transport.pointer) mustBe Seq.empty
        }
      }

      "the original version's transportPayment field has a different value to this one" in {
        val fieldPointer = s"${Transport.pointer}.${TransportPayment.pointer}"
        withClue("both versions have Some values but values are different") {
          val transport = Transport(transportPayment = Some(TransportPayment("latest")))
          val originalValue = TransportPayment("original")
          transport.createDiff(transport.copy(transportPayment = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.transportPayment.get)
          )
        }

        withClue("the original version's transportPayment field is None but this one has Some value") {
          val transport = Transport(transportPayment = Some(TransportPayment("latest")))
          val originalValue = None
          transport.createDiff(transport.copy(transportPayment = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.transportPayment)
          )
        }

        withClue("the original version's transportPayment field is Some but this one has None as its value") {
          val transport = Transport(transportPayment = None)
          val originalValue = Some(TransportPayment("original"))
          transport.createDiff(transport.copy(transportPayment = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.transportPayment)
          )
        }

        withClue("both versions have None values") {
          val transport = Transport(transportPayment = None)
          transport.createDiff(transport, Transport.pointer) mustBe Seq.empty
        }
      }

      "the original version's containers field has a different value to this one" in {
        val fieldPointer = s"${Transport.pointer}.${Container.pointer}.1.id"
        withClue("both versions have Some non-empty containers values but values are different") {
          val transport = Transport(containers = Some(Seq(Container("latest", Seq.empty[Seal]))))
          val originalValue = Seq(Container("original", Seq.empty[Seal]))
          transport.createDiff(transport.copy(containers = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue(0).id, (transport.containers.get)(0).id)
          )
        }
      }

      "the original version's borderModeOfTransportCode field has a different value to this one" in {
        val fieldPointer = s"${Transport.pointer}.${TransportLeavingTheBorder.pointer}"
        withClue("both versions have Some(Some) values but values are different") {
          val transport = Transport(borderModeOfTransportCode = Some(TransportLeavingTheBorder(Some(Maritime))))
          val originalValue = TransportLeavingTheBorder(Some(Rail))
          transport.createDiff(transport.copy(borderModeOfTransportCode = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.borderModeOfTransportCode.get)
          )
        }

        withClue("the original version's transportPayment field is Some(None) but this one has Some(Some) value") {
          val transport = Transport(borderModeOfTransportCode = Some(TransportLeavingTheBorder(Some(Maritime))))
          val originalValue = TransportLeavingTheBorder(None)
          transport.createDiff(transport.copy(borderModeOfTransportCode = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.borderModeOfTransportCode.get)
          )
        }

        withClue("the original version's transportPayment field is None but this one has Some(Some) value") {
          val transport = Transport(borderModeOfTransportCode = Some(TransportLeavingTheBorder(Some(Maritime))))
          val originalValue = None
          transport.createDiff(transport.copy(borderModeOfTransportCode = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.borderModeOfTransportCode)
          )
        }

        withClue("the original version's transportPayment field is None but this one has Some(None) as its value") {
          val transport = Transport(borderModeOfTransportCode = Some(TransportLeavingTheBorder(None)))
          val originalValue = None
          transport.createDiff(transport.copy(borderModeOfTransportCode = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.borderModeOfTransportCode)
          )
        }

        withClue("the original version's transportPayment field is Some(None) but this one has None as its value") {
          val transport = Transport(borderModeOfTransportCode = None)
          val originalValue = Some(TransportLeavingTheBorder(None))
          transport.createDiff(transport.copy(borderModeOfTransportCode = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.borderModeOfTransportCode)
          )
        }

        withClue("both versions have None values") {
          val transport = Transport(borderModeOfTransportCode = None)
          transport.createDiff(transport, Transport.pointer) mustBe Seq.empty
        }
      }

      "the original version's meansOfTransportOnDepartureType field has a different value to this one" in {
        val fieldPointer = s"${Transport.pointer}.${Transport.transportOnDeparturePointer}"
        withClue("both versions have Some values but values are different") {
          val transport = Transport(meansOfTransportOnDepartureType = Some("latest"))
          val originalValue = "original"
          transport.createDiff(transport.copy(meansOfTransportOnDepartureType = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.meansOfTransportOnDepartureType.get)
          )
        }

        withClue("the original version's meansOfTransportOnDepartureType field is None but this one has Some value") {
          val transport = Transport(meansOfTransportOnDepartureType = Some("latest"))
          val originalValue = None
          transport.createDiff(transport.copy(meansOfTransportOnDepartureType = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.meansOfTransportOnDepartureType)
          )
        }

        withClue("the original version's meansOfTransportOnDepartureType field is Some but this one has None as its value") {
          val transport = Transport(meansOfTransportOnDepartureType = None)
          val originalValue = "original"
          transport.createDiff(transport.copy(meansOfTransportOnDepartureType = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, Some(originalValue), transport.meansOfTransportOnDepartureType)
          )
        }

        withClue("both versions have None values") {
          val transport = Transport(meansOfTransportOnDepartureType = None)
          transport.createDiff(transport, Transport.pointer) mustBe Seq.empty
        }
      }

      "the original version's meansOfTransportOnDepartureIDNumber field has a different value to this one" in {
        val fieldPointer = s"${Transport.pointer}.${Transport.transportOnDepartureIdPointer}"
        withClue("both versions have Some values but values are different") {
          val transport = Transport(meansOfTransportOnDepartureIDNumber = Some("latest"))
          val originalValue = "original"
          transport.createDiff(transport.copy(meansOfTransportOnDepartureIDNumber = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.meansOfTransportOnDepartureIDNumber.get)
          )
        }

        withClue("the original version's meansOfTransportOnDepartureIDNumber field is None but this one has Some value") {
          val transport = Transport(meansOfTransportOnDepartureIDNumber = Some("latest"))
          val originalValue = None
          transport.createDiff(transport.copy(meansOfTransportOnDepartureIDNumber = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.meansOfTransportOnDepartureIDNumber)
          )
        }

        withClue("the original version's meansOfTransportOnDepartureIDNumber field is Some but this one has None as its value") {
          val transport = Transport(meansOfTransportOnDepartureIDNumber = None)
          val originalValue = "original"
          transport.createDiff(transport.copy(meansOfTransportOnDepartureIDNumber = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, Some(originalValue), transport.meansOfTransportOnDepartureIDNumber)
          )
        }

        withClue("both versions have None values") {
          val transport = Transport(meansOfTransportOnDepartureIDNumber = None)
          transport.createDiff(transport, Transport.pointer) mustBe Seq.empty
        }
      }

      "the original version's transportCrossingTheBorderNationality field has a different value to this one" in {
        val fieldPointer = s"${Transport.pointer}.${TransportCountry.pointer}"
        withClue("both versions have Some(Some) values but values are different") {
          val transport = Transport(transportCrossingTheBorderNationality = Some(TransportCountry(Some("latest"))))
          val originalValue = TransportCountry(Some("original"))
          transport.createDiff(transport.copy(transportCrossingTheBorderNationality = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.transportCrossingTheBorderNationality.get)
          )
        }

        withClue("the original version's transportPayment field is Some(None) but this one has Some(Some) value") {
          val transport = Transport(transportCrossingTheBorderNationality = Some(TransportCountry(Some("latest"))))
          val originalValue = TransportCountry(None)
          transport.createDiff(transport.copy(transportCrossingTheBorderNationality = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.transportCrossingTheBorderNationality.get)
          )
        }

        withClue("the original version's transportPayment field is None but this one has Some(Some) value") {
          val transport = Transport(transportCrossingTheBorderNationality = Some(TransportCountry(Some("latest"))))
          val originalValue = None
          transport.createDiff(transport.copy(transportCrossingTheBorderNationality = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.transportCrossingTheBorderNationality)
          )
        }

        withClue("the original version's transportPayment field is None but this one has Some(None) as its value") {
          val transport = Transport(transportCrossingTheBorderNationality = Some(TransportCountry(None)))
          val originalValue = None
          transport.createDiff(transport.copy(transportCrossingTheBorderNationality = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.transportCrossingTheBorderNationality)
          )
        }

        withClue("both versions have None values") {
          val transport = Transport(transportCrossingTheBorderNationality = None)
          transport.createDiff(transport, Transport.pointer) mustBe Seq.empty
        }
      }

      "the original version's meansOfTransportCrossingTheBorderType field has a different value to this one" in {
        val fieldPointer = s"${Transport.pointer}.${Transport.transportCrossingTheBorderPointer}"
        withClue("both versions have Some values but values are different") {
          val transport = Transport(meansOfTransportCrossingTheBorderType = Some("latest"))
          val originalValue = "original"
          transport.createDiff(transport.copy(meansOfTransportCrossingTheBorderType = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.meansOfTransportCrossingTheBorderType.get)
          )
        }

        withClue("the original version's meansOfTransportCrossingTheBorderType field is None but this one has Some value") {
          val transport = Transport(meansOfTransportCrossingTheBorderType = Some("latest"))
          val originalValue = None
          transport.createDiff(transport.copy(meansOfTransportCrossingTheBorderType = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.meansOfTransportCrossingTheBorderType)
          )
        }

        withClue("the original version's meansOfTransportCrossingTheBorderType field is Some but this one has None as its value") {
          val transport = Transport(meansOfTransportCrossingTheBorderType = None)
          val originalValue = "original"
          transport.createDiff(transport.copy(meansOfTransportCrossingTheBorderType = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, Some(originalValue), transport.meansOfTransportCrossingTheBorderType)
          )
        }

        withClue("both versions have None values") {
          val transport = Transport(meansOfTransportCrossingTheBorderType = None)
          transport.createDiff(transport, Transport.pointer) mustBe Seq.empty
        }
      }

      "the original version's meansOfTransportCrossingTheBorderIDNumber field has a different value to this one" in {
        val fieldPointer = s"${Transport.pointer}.${Transport.transportCrossingTheBorderIdPointer}"
        withClue("both versions have Some values but values are different") {
          val transport = Transport(meansOfTransportCrossingTheBorderIDNumber = Some("latest"))
          val originalValue = "original"
          transport.createDiff(transport.copy(meansOfTransportCrossingTheBorderIDNumber = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.meansOfTransportCrossingTheBorderIDNumber.get)
          )
        }

        withClue("the original version's meansOfTransportCrossingTheBorderIDNumber field is None but this one has Some value") {
          val transport = Transport(meansOfTransportCrossingTheBorderIDNumber = Some("latest"))
          val originalValue = None
          transport.createDiff(transport.copy(meansOfTransportCrossingTheBorderIDNumber = originalValue), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, originalValue, transport.meansOfTransportCrossingTheBorderIDNumber)
          )
        }

        withClue("the original version's meansOfTransportCrossingTheBorderIDNumber field is Some but this one has None as its value") {
          val transport = Transport(meansOfTransportCrossingTheBorderIDNumber = None)
          val originalValue = "original"
          transport.createDiff(transport.copy(meansOfTransportCrossingTheBorderIDNumber = Some(originalValue)), Transport.pointer) mustBe Seq(
            constructAlteredField(fieldPointer, Some(originalValue), transport.meansOfTransportCrossingTheBorderIDNumber)
          )
        }

        withClue("both versions have None values") {
          val transport = Transport(meansOfTransportCrossingTheBorderIDNumber = None)
          transport.createDiff(transport, Transport.pointer) mustBe Seq.empty
        }
      }
    }
  }
}
