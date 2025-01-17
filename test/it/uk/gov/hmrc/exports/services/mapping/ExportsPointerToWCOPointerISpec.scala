package uk.gov.hmrc.exports.services.mapping

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Environment

class ExportsPointerToWCOPointerISpec extends AnyWordSpec with Matchers {

  private val environment = Environment.simple()

  private val exportsPointerToWCOPointer = new ExportsPointerToWCOPointer(environment)

  "ExportsPointerToWCOPointer.getWCOPointers" should {

    "return the expected WCO Pointers for the provided Exports Pointers" in {
      val dynamicExportsPointer = "declaration.items.98.additionalFiscalReferencesData.references.43.country"
      val expectedWCOPointersForDynamicExportsPointer = List("42A.67A.68A.98.55B.43.R119")
      exportsPointerToWCOPointer.getWCOPointers(dynamicExportsPointer) mustBe expectedWCOPointersForDynamicExportsPointer

      val exportsPointerForMucr = "declaration.mucr"
      val expectedWCOPointersForMucr = List("42A.67A.99A.1.171", "42A.67A.99A.1.D018", "42A.67A.99A.1.D019", "42A.67A.99A.1.D031")
      val actualWCOPointersForMucr = exportsPointerToWCOPointer.getWCOPointers(exportsPointerForMucr)
      expectedWCOPointersForMucr.foreach(actualWCOPointersForMucr must contain(_))
    }

    "return an empty List when WCO Pointers for the provided Exports Pointer cannot be found" in {
      exportsPointerToWCOPointer.getWCOPointers("declaration.some.field") mustBe List.empty
    }
  }
}
