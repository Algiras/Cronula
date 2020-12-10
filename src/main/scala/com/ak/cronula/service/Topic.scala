package com.ak.cronula.service

import java.util.UUID

import com.ak.cronula.config.{KafkaConfig, Topics}
import com.ak.cronula.service.Key.KeySerde
import com.wixpress.dst.greyhound.core
import com.wixpress.dst.greyhound.core.Serde
import com.wixpress.dst.greyhound.core.consumer.domain.{ConsumerRecord, ConsumerSubscription}
import com.wixpress.dst.greyhound.core.consumer.{OffsetReset, RecordConsumer, RecordConsumerConfig, domain}
import com.wixpress.dst.greyhound.core.metrics.GreyhoundMetrics
import com.wixpress.dst.greyhound.core.producer.{Producer, ProducerConfig, ProducerRecord}
import zio.blocking.Blocking
import zio.stream.ZStream
import zio._

trait Topic[VReq, V] {
  def record(action: VReq): RIO[Blocking, UUID]
  def records: ZStream[Any, Nothing, V]
}

object Topic {
  def makeKafkaTopic[VReq, V](kafkaConfig: KafkaConfig,
                              getTopicName: Topics => String,
                              valueSerde: Serde[V],
                              fromRequest: (UUID, VReq) => V): ZManaged[ZEnv with GreyhoundMetrics, Throwable, Topic[VReq, V]] = {
    val kafkaConfigStr = s"${kafkaConfig.host}:${kafkaConfig.port}"
    val topic = getTopicName(kafkaConfig.topics)
    val group = kafkaConfig.group.getOrElse(RecordConsumerConfig.makeClientId)

    for {
      queue <- Queue.unbounded[V].toManaged(_.shutdown)
      tenantId <- kafkaConfig.tenantId.map(ZIO.effect(_)).getOrElse(ZIO(UUID.randomUUID())).toManaged_
      producer <- Producer.make(ProducerConfig(kafkaConfigStr))
      _ <- RecordConsumer.make(
        core.consumer.RecordConsumerConfig(
          kafkaConfigStr,
          group,
          ConsumerSubscription.Topics(Set(topic)),
          offsetReset = OffsetReset.Earliest
        ),
        domain.RecordHandler {
          record: ConsumerRecord[Key, V] =>
            record.key match {
              case Some(key) if key.tenantId == tenantId => queue.offer(record.value)
              case _ => ZIO.unit
            }
        }.withDeserializers(KeySerde, valueSerde)
      )
    } yield new Topic[VReq, V] {
      override def record(actionRequest: VReq): ZIO[Blocking, Throwable, UUID] = for {
        recordId <- ZIO(UUID.randomUUID())
        _ <- producer.produce[Key, V](
          record = ProducerRecord(
            topic = topic,
            value = fromRequest(recordId, actionRequest),
            key = Some(Key(tenantId, recordId)),
          ),
          keySerializer = KeySerde,
          valueSerializer = valueSerde)
      } yield recordId

      override def records: ZStream[Any, Nothing, V] = ZStream.fromQueueWithShutdown(queue)
    }
  }
}
