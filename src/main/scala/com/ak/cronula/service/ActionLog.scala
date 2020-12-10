package com.ak.cronula.service

import java.util.UUID

import com.ak.cronula.config.KafkaConfig
import com.ak.cronula.model.Action
import com.wixpress.dst.greyhound.core.consumer.RecordConsumer.Env
import com.wixpress.dst.greyhound.core.{Serde, Serdes}
import helpers.ShapesDerivation._
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import zio.ZManaged
import zio.blocking.Blocking

object ActionLog {
  case class ActionRequest(issuerId: UUID)

  implicit val cronulaEventEncoder: Encoder[Action] = deriveEncoder[Action]
  implicit val cronulaEventDecoder: Decoder[Action] = deriveDecoder[Action]

  val ActionSerde: Serde[Action] = Serdes.StringSerde.inmap(
    decode[Action](_).getOrElse(throw new RuntimeException("Failed parsing Cronula Event"))
  )(_.asJson.noSpaces)

  def make(kafkaConfig: KafkaConfig): ZManaged[Blocking with Env, Throwable, Topic[ActionRequest, Action]] =
    Topic.makeKafkaTopic[ActionRequest, Action](kafkaConfig, _.action, ActionSerde, (id, req) => Action(id, req.issuerId))
}
