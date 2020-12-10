package com.ak.cronula.service

import java.util.UUID

import com.ak.cronula.TestUtils
import com.ak.cronula.config.ApplicationConfig
import com.ak.cronula.model.CronJob
import com.ak.cronula.service.CronErrors.NotFoundException
import com.wixpress.dst.greyhound.core.metrics
import com.wixpress.dst.greyhound.core.metrics.GreyhoundMetrics
import cron4s.CronExpr
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import zio._
import zio.config.typesafe.TypesafeConfig
import zio.internal.Platform
import zio.interop.catz._
import zio.stream.ZSink

import scala.language.reflectiveCalls

class KafkaCronTest extends Specification {
  "KafkaCron" should {
    "store CronJob" in new Context {
      runtime.unsafeRun(
        service.use(service => for {
        cronRes <- service.create(cronExpr.toString).flatMap(ZIO.fromEither(_))
        dbRecord <- TestUtils.retry(service.get(cronRes))(_.isRight)
      } yield dbRecord must beRight(CronJob(cronRes, cronExpr))))
    }

    "update CronJob" in new Context {
      runtime.unsafeRun(
        service.use(service => for {
          cronRes <- service.create(cronExpr.toString).flatMap(ZIO.fromEither(_))
          _ <- TestUtils.retry(service.get(cronRes))(_.isRight)
          _ <- service.update(cronRes, cronExpr2.toString)
          dbRecord <- TestUtils.retry(service.get(cronRes))(_.map(_.cronString).fold(_ => false, _  == cronExpr2))
        } yield dbRecord must beRight(CronJob(cronRes, cronExpr2))))
    }

    "delete CronJob" in new Context {
      runtime.unsafeRun(
        service.use(service => for {
          cronRes <- service.create(cronExpr.toString).flatMap(ZIO.fromEither(_))
          _ <- TestUtils.retry(service.get(cronRes))(_.isRight)
          _ <- service.delete(cronRes)
          dbRecord <- TestUtils.retry(service.get(cronRes))(_.isLeft)
        } yield dbRecord must beLeft(NotFoundException)))
    }

    "get all CronJobs" in new Context {
      runtime.unsafeRun(
        service.use(service => for {
          cronRes <- service.create(cronExpr.toString).flatMap(ZIO.fromEither(_))
          cronRes2 <- service.create(cronExpr2.toString).flatMap(ZIO.fromEither(_))
          all <- TestUtils.retry(service.getAll)(_.size == 2)
        } yield all must contain(
          CronJob(cronRes, cronExpr),
          CronJob(cronRes2, cronExpr2)
        )))
    }

    "stream messages using crons" in new Context {
      runtime.unsafeRun(
        service.use(service => for {

          id <- service.create(cronExpr.toString).flatMap(ZIO.fromEither(_))
          _ <- TestUtils.retry(service.get(id))(_.isRight)
          finalState <- service.stream.take(3).run(ZSink.collectAll[CronJob]).map(_.toSeq)
        } yield finalState must_=== Range(0, 3).map(_ => CronJob(id, cronExpr))))
    }
  }

  trait Context extends Scope {
    val tenantId: UUID = UUID.randomUUID()

    val id: UUID = UUID.randomUUID()
    val cronExpr: CronExpr = cron4s.Cron.parse("* * * ? * *").fold(throw _, identity)
    val cronExpr2: CronExpr = cron4s.Cron.parse("10-34 2,4,6 * ? * *").fold(throw _, identity)

    val runtime = Runtime(zio.Runtime.default.environment ++ GreyhoundMetrics.live, Platform.default)

    val service: ZManaged[zio.ZEnv, Throwable, KafkaCron] = (for {
      appConfig <- zio.config.config[ApplicationConfig].toManaged_
      kafkaTopic <- KafkaCron.kafkaTopic(appConfig.kafka.copy(tenantId = Some(tenantId)))
      service <- KafkaCron.make(kafkaTopic)
    } yield service).provideCustomLayer(
        TypesafeConfig.fromDefaultLoader(ApplicationConfig.applicationConfig) ++ metrics.GreyhoundMetrics.liveLayer
      )
  }
}
