package com.ak.cronula.model

import java.util.UUID
import cron4s.CronExpr

case class CronJob(id: UUID, cronString: CronExpr)
