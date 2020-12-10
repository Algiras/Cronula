package com.ak.cronula.e2e

import java.util.UUID
import java.util.concurrent.Executors

import cats.effect.Blocker
import cats.implicits._
import com.ak.cronula.CronulaRoutes.CronRequest
import com.ak.cronula.config._
import com.ak.cronula.e2e.CronulaE2E.{AppTask, TestEnv}
import com.ak.cronula.model.{Action, CronJob}
import com.ak.cronula.service.ActionLog.ActionRequest
import com.ak.cronula.service.{ActionLog, KafkaCron, Topic}
import com.ak.cronula.{Main, TestUtils}
import com.wixpress.dst.greyhound.core.metrics
import com.wixpress.dst.greyhound.core.metrics.GreyhoundMetrics
import cron4s.CronExpr
import cron4s.circe._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.client.{Client, JavaNetClientBuilder}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import zio.config.typesafe.TypesafeConfig
import zio.internal.Platform
import zio.interop.catz._
import zio.stream.ZSink
import zio.{ZEnv, _}


class CronulaE2E extends Specification {
  "Cronula" should {
    "provide a way to schedule a cron job" >> new Context {
      runtime.unsafeRun(service.use(env => for {
        id <- env.client.create(cronExpr)
        res <- TestUtils.retry(env.client.get(id))(_.isDefined)
      } yield res must beSome(CronJob(id, cronExpr)))
      )
    }

    "provide a way to re-schedule a cron job" >> new Context {
      runtime.unsafeRun(service.use(env => for {
        id <- env.client.create(cronExpr)
        _ <- TestUtils.retry(env.client.get(id))(_.isDefined)
        _ <- env.client.update(id, cronExpr2)
        res <- TestUtils.retry(env.client.get(id))(res => res.find(_.cronString == cronExpr2).isDefined)
      } yield res must beSome(CronJob(id, cronExpr2))
      ))
    }

    "provide a way to delete a cron job" >> new Context {
      runtime.unsafeRun(service.use(env => for {
        id <- env.client.create(cronExpr)
        _ <- TestUtils.retry(env.client.get(id))(_.isDefined)
        _ <- env.client.delete(id)
        res <- TestUtils.retry(env.client.get(id))(_.isEmpty)
      } yield res must beNone
      ))
    }

    "provide a way to list scheduled jobs" >> new Context {
      runtime.unsafeRun(service.use(env => for {
        id1 <- env.client.create(cronExpr)
        id2 <- env.client.create(cronExpr)
        res <- TestUtils.retry(env.client.getAll)(res => Set(id1, id2).subsetOf(res.map(_.id).toSet))
      } yield res must contain(
        CronJob(id1, cronExpr),
        CronJob(id2, cronExpr)
      ))
      )
    }

    "schedule job should be recorded in actionLog" >> new Context {
      runtime.unsafeRun(service.use(env => for {
        id1 <- env.client.create(cronExpr)
        id2 <- env.client.create(cronExpr)
        _ <- TestUtils.retry(env.client.getAll)(res => Set(id1, id2).subsetOf(res.map(_.id).toSet))
        actions <- env.actionLog.records.take(5).run(ZSink.collectAll[Action]).map(_.toList)
      } yield actions.map(_.issuerId) must contain(id1, id2)
      ))
    }
  }

  trait Context extends Scope {
    val tenantId: UUID = UUID.randomUUID()

    val cronExpr: CronExpr = cron4s.Cron.parse("* * * ? * *").fold(throw _, identity)
    val cronExpr2: CronExpr = cron4s.Cron.parse("10-34 2,4,6 * ? * *").fold(throw _, identity)

    val runtime = Runtime(zio.Runtime.default.environment ++ GreyhoundMetrics.live, Platform.default)

    def service: ZManaged[zio.ZEnv, Throwable, TestEnv] = ZIO.runtime[ZEnv].toManaged_.flatMap(implicit env =>
      for {
        config <- zio.config.config[ApplicationConfig].toManaged_
        kafkaConf = config.kafka.copy(tenantId = Some(tenantId))
        kafkaCronTopic <- KafkaCron.kafkaTopic(kafkaConf)
        kafka <- KafkaCron.make(kafkaCronTopic)
        actionLog <- ActionLog.make(kafkaConf)
        app <- Main.server(config, kafka, actionLog)
      } yield {
        val httpClient: Client[AppTask] = {
          val blockingPool = Executors.newFixedThreadPool(5)
          val blocker = Blocker.liftExecutorService(blockingPool)

          JavaNetClientBuilder[AppTask](blocker).create
        }

        CronulaE2E.TestEnv(CronulaE2E.CronClient(httpClient, app.baseUri), actionLog)
      }
    ).provideCustomLayer(
      TypesafeConfig.fromDefaultLoader(ApplicationConfig.applicationConfig) ++ metrics.GreyhoundMetrics.liveLayer
    )
  }

}

object CronulaE2E {
  type AppTask[A] = RIO[ZEnv, A]

  case class TestEnv(client: CronClient, actionLog: Topic[ActionRequest, Action])

  case class CronClient(httpClient: Client[AppTask], uri: Uri) {
    implicit val cronEntityDecoder: EntityDecoder[AppTask, CronJob] = jsonOf[AppTask, CronJob]
    implicit val cronEntitySeqDecoder: EntityDecoder[AppTask, Seq[CronJob]] = jsonOf[AppTask, Seq[CronJob]]
    implicit val cronRequestEncoder: EntityEncoder[AppTask, CronRequest] = jsonEncoderOf[AppTask, CronRequest]

    def get(id: UUID): AppTask[Option[CronJob]] = httpClient.expectOption[CronJob](
      Request[AppTask](method = Method.GET, uri = uri.withPath(s"cron/$id"))
    )

    def create(cronExpr: CronExpr): AppTask[UUID] = httpClient.expect[String](
      Request[AppTask](method = Method.POST, uri = uri.withPath(s"cron"))
        .withEntity(CronRequest(cronExpr.toString))
    ).map(UUID.fromString)

    def update(id: UUID, cronExpr: CronExpr): AppTask[Unit] = httpClient.expect[Unit](
      Request[AppTask](method = Method.PUT, uri = uri.withPath(s"cron/$id"))
        .withEntity(CronRequest(cronExpr.toString))
    )

    def delete(id: UUID): AppTask[Unit] = httpClient.expect[Unit](
      Request[AppTask](method = Method.DELETE, uri = uri.withPath(s"cron/$id"))
    )

    def getAll: AppTask[Seq[CronJob]] = httpClient.expect[Seq[CronJob]](
      Request[AppTask](method = Method.GET, uri = uri.withPath("cron"))
    )
  }

}