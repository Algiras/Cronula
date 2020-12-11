package com.ak.cronula.service

import java.util.UUID

import cats.instances.map
import com.ak.cronula.config.ApplicationConfig
import com.ak.cronula.model.Action
import com.ak.cronula.service.ActionLog.ActionRequest
import com.wixpress.dst.greyhound.core.metrics
import com.wixpress.dst.greyhound.core.metrics.GreyhoundMetrics
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import specs2.run
import zio.{Runtime, ZIO}
import zio.config.typesafe.TypesafeConfig
import zio.internal.Platform
import zio.stream.ZSink

class TopicTest extends Specification {
  "TopicTest" in {
    "Action Log" should {
      "record actions" in new Context {
        runtime.unsafeRun(service.flatMap(actionlog => for {
          resId <- actionlog.record(ActionRequest(issuerId)).toManaged_
          resId2 <- actionlog.record(ActionRequest(issuerId)).toManaged_
          resId3 <- actionlog.record(ActionRequest(issuerId2)).toManaged_
          stream <- actionlog.records
          result <- stream.take(3).run(ZSink.collectAll[Action]).map(_.toList).toManaged_
        } yield result must contain(
          Action(resId, issuerId),
          Action(resId2, issuerId),
          Action(resId3, issuerId2)
        )).use(ZIO(_)))
      }
    }
  }

  trait Context extends Scope {
    val tenantId: UUID = UUID.randomUUID()
    val issuerId: UUID = UUID.randomUUID()
    val issuerId2: UUID = UUID.randomUUID()

    val runtime = Runtime(zio.Runtime.default.environment ++ GreyhoundMetrics.live, Platform.default)

    val service = (for {
      appConfig <- zio.config.config[ApplicationConfig].toManaged_
      service <- ActionLog.make(appConfig.kafka.copy(tenantId = Some(tenantId)))
    } yield service).provideCustomLayer(
      TypesafeConfig.fromDefaultLoader(ApplicationConfig.applicationConfig) ++ metrics.GreyhoundMetrics.liveLayer
    )
  }
}
