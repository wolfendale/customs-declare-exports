/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.exports.services.mapping.declaration.consignment

import org.scalatest.{Matchers, WordSpec}
import testdata.ExportsDeclarationBuilder
import uk.gov.hmrc.exports.models.declaration.Country
import wco.datamodel.wco.dec_dms._2.Declaration

class IteneraryBuilderSpec extends WordSpec with Matchers with ExportsDeclarationBuilder {

  "IteneraryBuilder" should {

    "build then add" when {

      "no destination countries" in {

        // Given
        val model = aDeclaration(withoutOriginationCountry(), withoutDestinationCountry(), withoutRoutingCountries())
        val consignment = new Declaration.Consignment()

        // When
        new IteneraryBuilder().buildThenAdd(model, consignment)

        // Then
        consignment.getItinerary shouldBe empty
      }

      "with empty destination country" in {

        // Given
        val model = aDeclaration(withoutOriginationCountry(), withEmptyDestinationCountry(), withoutRoutingCountries())
        val consignment = new Declaration.Consignment()

        // When
        new IteneraryBuilder().buildThenAdd(model, consignment)

        // Then
        consignment.getItinerary shouldBe empty
      }

      "multiple routing countries" in {

        // Given
        val model = aDeclaration(withRoutingCountries(Seq(Country(Some("GB")), Country(Some("FR")))))
        val consignment = new Declaration.Consignment()

        // When
        new IteneraryBuilder().buildThenAdd(model, consignment)

        // Then
        consignment.getItinerary should have(size(2))
        consignment.getItinerary.get(0).getSequenceNumeric.intValue shouldBe 0
        consignment.getItinerary.get(1).getSequenceNumeric.intValue shouldBe 1
        consignment.getItinerary.get(0).getRoutingCountryCode.getValue shouldBe "GB"
        consignment.getItinerary.get(1).getRoutingCountryCode.getValue shouldBe "FR"
      }

      "no routing countries are provided and origin is GB" in {
        // Given
        val model = aDeclaration(withRoutingCountries(Seq()), withOriginationCountry(Country(Some("GB"))))
        val consignment = new Declaration.Consignment()

        // When
        new IteneraryBuilder().buildThenAdd(model, consignment)

        // Then
        consignment.getItinerary should have(size(1))
        consignment.getItinerary.get(0).getSequenceNumeric.intValue shouldBe 0
        consignment.getItinerary.get(0).getRoutingCountryCode.getValue shouldBe "GB"
      }

      "no routing countries are provided and origin is not GB" in {
        // Given
        val model = aDeclaration(withRoutingCountries(Seq()), withOriginationCountry(Country(Some("FR"))))
        val consignment = new Declaration.Consignment()

        // When
        new IteneraryBuilder().buildThenAdd(model, consignment)

        // Then
        consignment.getItinerary should have(size(0))
      }
    }
  }
}
