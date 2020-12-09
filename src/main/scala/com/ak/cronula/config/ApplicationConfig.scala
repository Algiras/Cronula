package com.ak.cronula.config

import zio.config.magnolia.DeriveConfigDescriptor.descriptor
import zio.config.magnolia.DeriveConfigDescriptor._

case class ApplicationConfig(server: ServerConfig, kafka: KafkaConfig)

object ApplicationConfig {
  val applicationConfig = descriptor[ApplicationConfig]
}