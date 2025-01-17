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

package uk.gov.hmrc.exports.services.mapping

import uk.gov.hmrc.exports.models.declaration.ExportsDeclaration
import uk.gov.hmrc.exports.services.mapping.declaration.DeclarationBuilder
import wco.datamodel.wco.dec_dms._2.Declaration
import wco.datamodel.wco.documentmetadata_dms._2.MetaData

import java.io.StringWriter
import javax.inject.Inject
import javax.xml.bind.{JAXBContext, JAXBElement, Marshaller}
import javax.xml.namespace.QName

class AmendmentMetaDataBuilder @Inject() (declarationBuilder: DeclarationBuilder) {

  def buildRequest(model: ExportsDeclaration, wcoPointers: Seq[String]): MetaData = {
    val metaData = new MetaData

    val element: JAXBElement[Declaration] = new JAXBElement[Declaration](
      new QName("urn:wco:datamodel:WCO:DEC-DMS:2", "Declaration"),
      classOf[Declaration],
      declarationBuilder.buildAmendment(model, wcoPointers)
    )
    metaData.setAny(element)

    metaData
  }
}

object AmendmentMetaDataBuilder {

  def toXml(metaData: MetaData): String = {
    lazy val jaxbContext = JAXBContext.newInstance(classOf[MetaData])
    val jaxbMarshaller = jaxbContext.createMarshaller

    jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

    val sw = new StringWriter
    jaxbMarshaller.marshal(metaData, sw)
    sw.toString
  }
}
