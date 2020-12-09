package com.ak.cronula

import cats.implicits._
import com.ak.cronula.config.ApplicationConfig
import com.ak.cronula.service.ActionLog.ActionRequest
import com.ak.cronula.service.{ActionLog, KafkaCron}
import com.wixpress.dst.greyhound.core.metrics
import org.http4s.implicits._
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

  def server(config: ApplicationConfig, cronService: KafkaCron, actionLogService: ActionLog)(implicit runtime: zio.Runtime[ZEnv]) = {
    val httpApp = CronulaRoutes.cronRoutes(cronService).orNotFound
    val finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)

    for {
      cronStream <- cronService.stream.flatMap(
        job => ZStream.fromEffect(actionLogService.record(ActionRequest(job.id)))
      ).runDrain.fork.toManaged_
      actionLogStream <- actionLogService.records.flatMap(action => {
        ZStream.fromEffect(zio.console.putStrLn(s"id: ${action.id}, issuer: ${action.issuerId}"))
      }).runDrain.fork.toManaged_
      server <- BlazeServerBuilder[AppTask](ExecutionContext.global)
        .bindHttp(config.server.port, config.server.host)
        .withHttpApp(finalHttpApp)
        .resource
        .toManaged
      _ <- ZIO.unit.toManaged(_ => ZIO.collectAllPar(Seq(cronStream.interruptFork, actionLogStream.interruptFork)))
    } yield server
  }

  def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    ZIO
      .runtime[ZEnv]
      .flatMap(implicit runtime =>
        (for {
          appConfig <- zio.config.config[ApplicationConfig].toManaged_
          cronService <- service.KafkaCron.make(appConfig.kafka)
          actionLogService <- service.ActionLog.make(appConfig.kafka)
          _ <- server(appConfig, cronService, actionLogService)(runtime)
          _ <- zio.console.getStrLn.toManaged_
        } yield ExitCode.success).use(ZIO(_))
      )
      .provideCustomLayer(
        TypesafeConfig.fromDefaultLoader(ApplicationConfig.applicationConfig) ++ metrics.GreyhoundMetrics.liveLayer
      )
      .catchAll(err => putStrLn(err.toString).as(ExitCode.failure))
  }

}