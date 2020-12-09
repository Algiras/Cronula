package com.ak.cronula.model

import java.util.UUID
import cron4s._

object Cron {
  case class CronJob(id: UUID, cronString: CronExpr)
  case class CronJobEdit(cronString: CronExpr)
}
