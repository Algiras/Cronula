package com.ak.cronula.service

import java.time.temporal.ChronoUnit
import java.util.UUID

import cats.syntax.either._
import com.ak.cronula.Main.AppTask
import com.ak.cronula.config.KafkaConfig
import com.ak.cronula.model.{CronJob, CronulaSate}
import com.ak.cronula.model.CronulaSate.CronulaSateEvents
import com.ak.cronula.model.CronulaSate.CronulaSateEvents.CronulaEvent
import com.ak.cronula.service.CronErrors._
import com.wixpress.dst.greyhound.core.consumer.RecordConsumer.Env
import com.wixpress.dst.greyhound.core.metrics.GreyhoundMetrics
import com.wixpress.dst.greyhound.core.{Serde, Serdes}
import cron4s.lib.javatime._
import io.circe.parser.decode
import io.circe.syntax._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.stream.ZStream

import scala.concurrent.duration._

trait KafkaCron extends Cron[AppTask]{
  def getAll: AppTask[Set[CronJob]]
  def get(id: UUID): AppTask[Either[NotFoundException.type, CronJob]]
  def create(cronString: String): AppTask[Either[ParseException.type, UUID]]
  def update(id: UUID, cronString: String): AppTask[Either[CronException, Unit]]
  def delete(id: UUID): AppTask[Unit]

  def stream: ZStream[Clock, Throwable, CronJob]
}

object KafkaCron {
  val CronEventSerde: Serde[CronulaEvent] = Serdes.StringSerde.inmap(
    decode[CronulaEvent](_).getOrElse(throw new RuntimeException("Failed parsing Cronula Event"))
  )(_.asJson.noSpaces)

  def kafkaTopic(kafkaConfig: KafkaConfig): ZManaged[zio.ZEnv with GreyhoundMetrics, Throwable, Topic[CronulaEvent, CronulaEvent]] =
    Topic.makeKafkaTopic[CronulaEvent, CronulaEvent](kafkaConfig, _.cron, CronEventSerde, (_, event) => event)

  def make(topic: Topic[CronulaEvent, CronulaEvent]): ZManaged[Blocking with Env, Throwable, KafkaCron] = for {
    state <- Ref.make(CronulaSate.empty).toManaged_
    _ <- topic.records.flatMap(
      event => ZStream.fromEffect(state.update(CronulaSate.process(event, _)))
    ).runDrain.fork.toManaged(_.interruptFork)
  } yield new KafkaCron {
    override def getAll: ZIO[Any, Nothing, Set[CronJob]] = state.get.map(state => state.jobs.values.toSet)

    override def get(id: UUID): ZIO[Any, Nothing, Either[NotFoundException.type, CronJob]] = state.get
      .map(state => state.jobs.get(id).map(Right(_)).getOrElse(Left(NotFoundException)))

    private def recordEvent(cronulEvent: CronulaSateEvents.CronulaEvent) = topic.record(cronulEvent)

    override def create(cronString: String): ZIO[Blocking, Throwable, Either[ParseException.type, UUID]] = for {
      encodedCronStrEither <- ZIO(cron4s.Cron.parse(cronString).leftMap(_ => ParseException))
      id <- RIO(UUID.randomUUID())
      _ <- encodedCronStrEither.fold(_ => ZIO.unit, encodedCronStr => recordEvent(CronulaSateEvents.RecordJob(id, encodedCronStr)))
    } yield encodedCronStrEither.map(_ => id)

    override def update(id: UUID, cronString: String): ZIO[Blocking, Throwable, Either[ParseException.type, Unit]] = for {
      encodedCronStrEither <- ZIO(cron4s.Cron.parse(cronString).leftMap(_ => ParseException))
      _ <- encodedCronStrEither.fold(_ => ZIO.unit, encodedCronStr => recordEvent(CronulaSateEvents.Updatejob(id, encodedCronStr)))
    } yield encodedCronStrEither.map(_ => ())

    override def delete(id: UUID): ZIO[Blocking, Throwable, Unit] = recordEvent(CronulaSateEvents.DeleteJob(id)).unit

    def stream: ZStream[Clock, Throwable, CronJob] = for {
      from <- ZStream.repeatEffect(ZIO.sleep(duration.Duration.fromScala(1.second)).flatMap(_ => zio.clock.currentDateTime))
      state <- ZStream.fromEffect(state.get)
      res <- ZStream.fromEffect(ZIO.collectAll(state.jobs.toList.map(job => job._2.cronString.next(from) match {
        case Some(next) if from.until(next, ChronoUnit.SECONDS).seconds <= 1.second => ZIO(Some(job._2))
        case Some(_) => ZIO(None)
        case None => ZIO.fail(new RuntimeException("No more time...???"))
      }))).map(_.flatten)
      job <- ZStream.fromIterable(res)
    } yield job
  }
}
