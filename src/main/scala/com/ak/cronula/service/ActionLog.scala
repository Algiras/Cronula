package com.ak.cronula.service

import java.util.UUID

import com.ak.cronula.config.KafkaConfig
import com.ak.cronula.model.Action
import com.ak.cronula.service.ActionLog.ActionRequest
import com.ak.cronula.service.Key.KeySerde
import com.wixpress.dst.greyhound.core
import com.wixpress.dst.greyhound.core.consumer.RecordConsumer.Env
import com.wixpress.dst.greyhound.core.consumer.domain.{ConsumerRecord, ConsumerSubscription}
import com.wixpress.dst.greyhound.core.consumer.{OffsetReset, RecordConsumer, RecordConsumerConfig, domain}
import com.wixpress.dst.greyhound.core.producer.{Producer, ProducerConfig, ProducerRecord}
import com.wixpress.dst.greyhound.core.{Serde, Serdes}
import helpers.ShapesDerivation._
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import zio.blocking.Blocking
import zio.stream.ZStream
import zio.{Queue, RIO, ZIO, ZManaged}

trait ActionLog {
  def record(action: ActionRequest): RIO[Blocking, UUID]
  def records: ZStream[Any, Nothing, Action]
}

object ActionLog {
  case class ActionRequest(issuerId: UUID)

  implicit val cronulaEventEncoder: Encoder[Action] = deriveEncoder[Action]
  implicit val cronulaEventDecoder: Decoder[Action] = deriveDecoder[Action]

  val ActionSerde: Serde[Action] = Serdes.StringSerde.inmap(
    decode[Action](_).getOrElse(throw new RuntimeException("Failed parsing Cronula Event"))
  )(_.asJson.noSpaces)

  def make(kafkaConfig: KafkaConfig): ZManaged[Blocking with Env, Throwable, ActionLog] = {
    val kafkaConfigStr = s"${kafkaConfig.host}:${kafkaConfig.port}"
    val topic = kafkaConfig.topics.action
    val group = kafkaConfig.group.getOrElse(RecordConsumerConfig.makeClientId)

    for {
      queue <- Queue.unbounded[Action].toManaged(_.shutdown)
      tenantId <- kafkaConfig.tenantId.map(ZIO(_)).getOrElse(ZIO(UUID.randomUUID())).toManaged_
      producer <- Producer.make(ProducerConfig(kafkaConfigStr))
      _ <- RecordConsumer.make(
        core.consumer.RecordConsumerConfig(
          kafkaConfigStr,
          group,
          ConsumerSubscription.Topics(Set(topic)),
          offsetReset = OffsetReset.Earliest
        ),
        domain.RecordHandler {
          record: ConsumerRecord[Key, Action] =>
            record.key match {
              case Some(key) if key.tenantId == tenantId => queue.offer(record.value)
              case _ => ZIO.unit
            }
        }.withDeserializers(KeySerde, ActionSerde)
      ).useForever.fork.toManaged_
    } yield new ActionLog {
      override def record(actionRequest: ActionRequest): ZIO[Blocking, Throwable, UUID] = for {
        recordId <- ZIO(UUID.randomUUID())
        _ <- producer.produce[Key, Action](
          record = ProducerRecord(
            topic = topic,
            value = Action(recordId, actionRequest.issuerId),
            key = Some(Key(tenantId, recordId)),
          ),
          keySerializer = KeySerde,
          valueSerializer = ActionSerde)
      } yield recordId

      override def records: ZStream[Any, Nothing, Action] = ZStream.fromQueue(queue)
    }
  }
}
