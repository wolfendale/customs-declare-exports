/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.exports.services.mapping.goodsshipment.consignment

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.exports.models.declaration.Container
import wco.datamodel.wco.dec_dms._2.Declaration.GoodsShipment

class ContainerCodeBuilderSpec extends WordSpec with Matchers {

  "ContainerCodeBuilder" should {

    "correctly map ContainerCode instance from new model" when {
      "there are containers" in {
        val builder = new ContainerCodeBuilder
        val consignment = new GoodsShipment.Consignment

        builder.buildThenAdd(Seq(Container("id", Seq.empty)), consignment)

        consignment.getContainerCode.getValue should be("1")
      }

      "there are no containers" in {
        val builder = new ContainerCodeBuilder
        val consignment = new GoodsShipment.Consignment

        builder.buildThenAdd(Seq.empty, consignment)

        consignment.getContainerCode.getValue should be("0")
      }
    }
  }
}
