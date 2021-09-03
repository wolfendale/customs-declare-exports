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

package uk.gov.hmrc.exports.services.mapping.declaration

import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import testdata.ExportsDeclarationBuilder
import uk.gov.hmrc.exports.base.UnitSpec
import uk.gov.hmrc.exports.services.mapping.AuthorisationHoldersBuilder
import uk.gov.hmrc.exports.services.mapping.declaration.consignment.DeclarationConsignmentBuilder
import uk.gov.hmrc.exports.services.mapping.goodsshipment.GoodsShipmentBuilder
import uk.gov.hmrc.exports.services.mapping.governmentagencygoodsitem.AdditionalInformationBuilder
import wco.datamodel.wco.dec_dms._2.Declaration

class DeclarationBuilderTest extends UnitSpec with ExportsDeclarationBuilder {

  private val functionCodeBuilder: FunctionCodeBuilder = mock[FunctionCodeBuilder]
  private val functionalReferenceIdBuilder: FunctionalReferenceIdBuilder = mock[FunctionalReferenceIdBuilder]
  private val typeCodeBuilder: TypeCodeBuilder = mock[TypeCodeBuilder]
  private val goodsItemQuantityBuilder: GoodsItemQuantityBuilder = mock[GoodsItemQuantityBuilder]
  private val agentBuilder: AgentBuilder = mock[AgentBuilder]
  private val specificCircumstancesCodeBuilder: SpecificCircumstancesCodeBuilder = mock[SpecificCircumstancesCodeBuilder]
  private val exitOfficeBuilder: ExitOfficeBuilder = mock[ExitOfficeBuilder]
  private val borderTransportMeansBuilder: BorderTransportMeansBuilder = mock[BorderTransportMeansBuilder]
  private val exporterBuilder: ExporterBuilder = mock[ExporterBuilder]
  private val declarantBuilder: DeclarantBuilder = mock[DeclarantBuilder]
  private val invoiceAmountBuilder: InvoiceAmountBuilder = mock[InvoiceAmountBuilder]
  private val supervisingOfficeBuilder: SupervisingOfficeBuilder = mock[SupervisingOfficeBuilder]
  private val totalPackageQuantityBuilder: TotalPackageQuantityBuilder = mock[TotalPackageQuantityBuilder]
  private val declarationConsignmentBuilder: DeclarationConsignmentBuilder = mock[DeclarationConsignmentBuilder]
  private val authorisationHoldersBuilder: AuthorisationHoldersBuilder = mock[AuthorisationHoldersBuilder]
  private val currencyExchangeBuilder: CurrencyExchangeBuilder = mock[CurrencyExchangeBuilder]
  private val goodsShipmentBuilder: GoodsShipmentBuilder = mock[GoodsShipmentBuilder]
  private val identificationBuilder: IdentificationBuilder = mock[IdentificationBuilder]
  private val submitterBuilder: SubmitterBuilder = mock[SubmitterBuilder]
  private val amendmentBuilder: AmendmentBuilder = mock[AmendmentBuilder]
  private val additionalInformationBuilder: AdditionalInformationBuilder = mock[AdditionalInformationBuilder]

  private val builder = new DeclarationBuilder(
    functionCodeBuilder,
    functionalReferenceIdBuilder,
    typeCodeBuilder,
    goodsItemQuantityBuilder,
    agentBuilder,
    specificCircumstancesCodeBuilder,
    exitOfficeBuilder,
    borderTransportMeansBuilder,
    exporterBuilder,
    declarantBuilder,
    invoiceAmountBuilder,
    supervisingOfficeBuilder,
    totalPackageQuantityBuilder,
    declarationConsignmentBuilder,
    authorisationHoldersBuilder,
    currencyExchangeBuilder,
    goodsShipmentBuilder,
    identificationBuilder,
    submitterBuilder,
    amendmentBuilder,
    additionalInformationBuilder
  )

  "DeclarationBuilder on buildDeclaration" should {

    "call all builders" in {
      val inputDeclaration = aDeclaration()
      builder.buildDeclaration(inputDeclaration)

      verify(functionCodeBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(functionalReferenceIdBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(typeCodeBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(goodsItemQuantityBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(agentBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(specificCircumstancesCodeBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(exitOfficeBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(borderTransportMeansBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(exporterBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(declarantBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(invoiceAmountBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(supervisingOfficeBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(totalPackageQuantityBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(declarationConsignmentBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(authorisationHoldersBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(currencyExchangeBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
      verify(goodsShipmentBuilder).buildThenAdd(eqTo(inputDeclaration), any[Declaration])
    }
  }

  "Build Cancellation" should {
    "build and append to Declaration" in {
      val declaration = builder.buildCancellation("ref", "id", "description", "reason", "eori")

      verify(functionCodeBuilder).buildThenAdd("13", declaration)
      verify(typeCodeBuilder).buildThenAdd("INV", declaration)
      verify(functionalReferenceIdBuilder).buildThenAdd("ref", declaration)
      verify(identificationBuilder).buildThenAdd("id", declaration)
      verify(submitterBuilder).buildThenAdd("eori", declaration)
      verify(amendmentBuilder).buildThenAdd("reason", declaration)
      verify(additionalInformationBuilder).buildThenAdd("description", declaration)
    }
  }

}
