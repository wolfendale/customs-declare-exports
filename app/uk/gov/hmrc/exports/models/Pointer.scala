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

package uk.gov.hmrc.exports.models

import play.api.libs.json._
import uk.gov.hmrc.exports.models
import uk.gov.hmrc.exports.models.PointerSectionType.PointerSectionType

object PointerSectionType extends Enumeration {
  type PointerSectionType = Value
  val FIELD, SEQUENCE = Value
  implicit val format: Format[models.PointerSectionType.Value] = Format(Reads.enumNameReads(PointerSectionType), Writes.enumNameWrites)
}

case class PointerSection(value: String, `type`: PointerSectionType) {
  lazy val pattern: String = `type` match {
    case PointerSectionType.FIELD    => value
    case PointerSectionType.SEQUENCE => "$"
  }

  override def toString: String = `type` match {
    case PointerSectionType.FIELD    => value
    case PointerSectionType.SEQUENCE => "#" + value
  }
}

object PointerSection {
  private val SEQUENCE_REGEX = "^#(\\d*)$".r
  implicit val format: Format[PointerSection] =
    Format[PointerSection](Reads(js => js.validate[String].map(PointerSection(_))), Writes(section => JsString(section.toString)))

  def apply(value: String): PointerSection = SEQUENCE_REGEX.findFirstMatchIn(value).map(_.group(1)) match {
    case Some(sequence) => PointerSection(sequence, PointerSectionType.SEQUENCE)
    case _              => PointerSection(value, PointerSectionType.FIELD)
  }
}

case class Pointer(sections: Seq[PointerSection]) {
  //  Converts a pointer into it's pattern form
  // e.g. ABC.DEF.*.GHI (if the pointer contains a sequence index)
  // e.g. ABC.DEF.GHI (if the pointer doesnt contain a sequence)
  lazy val pattern: PointerPattern = PointerPattern(sections.map(_.pattern).mkString("."))

  // Converts a pointer to it's string form preserving the type
  // e.g. ABC.DEF.#1.GHI (if the pointer contains a sequence with index 1)
  // e.g. ABC.DEF.GHI (if the pointer doesnt contain a sequence)
  override def toString: String = sections.map(_.toString).mkString(".")
}

object Pointer {
  implicit val format: Format[Pointer] =
    Format(Reads(js => js.validate[String].map(Pointer(_))), Writes(pointer => JsString(pointer.toString)))

  def apply(sections: String): Pointer = Pointer(sections.split("\\.").toIndexedSeq.map(PointerSection(_)))
}

case class PointerPattern(sections: List[PointerPatternSection]) {
  def matches(that: PointerPattern): Boolean =
    if (sections.size != that.sections.size) {
      false
    } else {
      val statuses = for (i <- sections.indices) yield sections(i).matches(that.sections(i))
      statuses.forall(identity)
    }

  override def toString: String = sections.map(_.value).mkString(".")
}

object PointerPattern {
  def apply(pattern: String): PointerPattern = PointerPattern(pattern.split("\\.").map(PointerPatternSection(_)).toList)
}

case class PointerPatternSection(value: String) {
  val sequential: Boolean = value.startsWith("$")
  val sequenceIndex: Option[String] = if (sequential) {
    PointerPatternSection.SEQUENCE_REGEX.findFirstMatchIn(value).map(_.group(1)).filter(_.nonEmpty)
  } else None

  def matches(that: PointerPatternSection): Boolean =
    if (this.sequential && that.sequential) {
      this.sequenceIndex == that.sequenceIndex || this.sequenceIndex.isEmpty || that.sequenceIndex.isEmpty
    } else {
      this.value == that.value
    }
}

object PointerPatternSection {
  private val SEQUENCE_REGEX = "^\\$(\\d*)$".r
}
