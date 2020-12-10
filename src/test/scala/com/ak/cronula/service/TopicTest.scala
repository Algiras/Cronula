package com.ak.cronula.service

import java.util.UUID

import com.ak.cronula.config.ApplicationConfig
import com.ak.cronula.model.Action
import com.ak.cronula.service.ActionLog.ActionRequest
import com.wixpress.dst.greyhound.core.metrics
import com.wixpress.dst.greyhound.core.metrics.GreyhoundMetrics
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import zio.Runtime
import zio.config.typesafe.TypesafeConfig
import zio.internal.Platform
import zio.stream.ZSink

class TopicTest extends Specification {
  "TopicTest" in {
    "Action Log" should {
      "record actions" in new Context {
        runtime.unsafeRun(service.use(actionlog => for {
          resId <- actionlog.record(ActionRequest(issuerId))
          resId2 <- actionlog.record(ActionRequest(issuerId))
          resId3 <- actionlog.record(ActionRequest(issuerId2))
          result <- actionlog.records.take(3).run(ZSink.collectAll[Action]).map(_.toList)
        } yield result must contain(
          Action(resId, issuerId),
          Action(resId2, issuerId),
          Action(resId3, issuerId2)
        )))
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
