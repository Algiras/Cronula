package com.ak.cronula.service

import java.util.UUID

import com.wixpress.dst.greyhound.core.Serde
import io.circe.generic.auto._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class Key(tenantId: UUID, id: UUID)

object Key {
  implicit val cronulaKeyEncoder: Encoder[Key] = deriveEncoder[Key]
  implicit val cronulaKeyDecoder: Decoder[Key] = deriveDecoder[Key]

  val keySerde: Serde[Key] = circeSerde[Key]
}