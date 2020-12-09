package com.ak.cronula.config

import java.util.UUID

case class Topics(cron: String, action: String)
case class KafkaConfig(host: String, port: Int, topics: Topics, group: Option[String], tenantId: Option[UUID])