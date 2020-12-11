package com.ak.cronula

import cats.implicits._
import com.ak.cronula.config.ApplicationConfig
import com.ak.cronula.model.Action
import com.ak.cronula.service.ActionLog.ActionRequest
import com.ak.cronula.service.{KafkaCron, Topic}
import com.wixpress.dst.greyhound.core.consumer.RecordConsumer
import com.wixpress.dst.greyhound.core.consumer.RecordConsumer.Env
import com.wixpress.dst.greyhound.core.metrics
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import zio._
import zio.config.typesafe.TypesafeConfig
import zio.console.putStrLn
import zio.interop.catz._
import zio.stream.ZStream

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

object Main extends App {
  type AppTask[A] = RIO[ZEnv, A]

  def server(config: ApplicationConfig, cronService: KafkaCron, actionLogTopic: Topic[ActionRequest, Action])(implicit runtime: zio.Runtime[ZEnv]): ZManaged[Env, Throwable, Server[AppTask]] = {
    for {
      recordings <- ZIO.accessM[RecordConsumer.Env](env => ZIO(actionLogTopic.records.provide(env).map(toFs2Stream(_)))).toManaged_
      httpApp = CronulaRoutes.cronRoutes(cronService, recordings.toResource).orNotFound
      finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)
      cronStream <- cronService.stream.flatMap(
        job => ZStream.fromEffect(actionLogTopic.record(ActionRequest(job.id)))
      ).runDrain.fork.toManaged_
      server <- BlazeServerBuilder[AppTask](ExecutionContext.global)
        .bindHttp(config.server.port, config.server.host)
        .withHttpApp(finalHttpApp)
        .resource
        .toManaged
      _ <- ZIO.unit.toManaged(_ => cronStream.interruptFork)
    } yield server
  }

  def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    ZIO
      .runtime[ZEnv]
      .flatMap(implicit runtime =>
        (for {
          appConfig <- zio.config.config[ApplicationConfig].toManaged_
          cronKafkaTopic <- service.KafkaCron.kafkaTopic(appConfig.kafka)
          cronService <- service.KafkaCron.make(cronKafkaTopic)
          actionLogTopic <- service.ActionLog.kafkaTopic(appConfig.kafka)
          _ <- server(appConfig, cronService, actionLogTopic)(runtime)
          _ <- zio.console.getStrLn.toManaged_
        } yield ExitCode.success).use(ZIO(_))
      )
      .provideCustomLayer(
        TypesafeConfig.fromDefaultLoader(ApplicationConfig.applicationConfig) ++ metrics.GreyhoundMetrics.liveLayer
      )
      .catchAll(err => putStrLn(err.toString).as(ExitCode.failure))
  }

}