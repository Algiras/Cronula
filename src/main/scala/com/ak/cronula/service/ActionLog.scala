package com.ak.cronula.service

import java.util.UUID

import com.ak.cronula.config.KafkaConfig
import com.ak.cronula.model.Action
import com.wixpress.dst.greyhound.core.Serde
import helpers.ShapesDerivation._
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

object ActionLog {
  case class ActionRequest(issuerId: UUID)

  implicit val cronulaEventEncoder: Encoder[Action] = deriveEncoder[Action]
  implicit val cronulaEventDecoder: Decoder[Action] = deriveDecoder[Action]

  val actionSerde: Serde[Action] = circeSerde[Action]

  def kafkaTopic(kafkaConfig: KafkaConfig) =
    Topic.makeKafkaTopic[ActionRequest, Action](kafkaConfig, _.action, actionSerde, (id, req) => Action(id, req.issuerId))
}
