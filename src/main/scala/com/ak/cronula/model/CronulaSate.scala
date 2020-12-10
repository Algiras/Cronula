package com.ak.cronula.model

import java.util.UUID
import cron4s.CronExpr
import io.circe.{Decoder, Encoder}
import cron4s.circe._
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import helpers.ShapesDerivation._

case class CronulaSate(jobs: Map[UUID, CronJob] = Map.empty[UUID, CronJob])

object CronulaSate {
  val empty = new CronulaSate()

 object CronulaSateEvents {
   sealed trait CronulaEvent

   case class RecordJob(id: UUID, cronString: CronExpr) extends CronulaEvent
   case class DeleteJob(id: UUID) extends CronulaEvent
   case class Updatejob(id: UUID, cronString: CronExpr) extends CronulaEvent
   case object Clean extends CronulaEvent

   implicit val cronulaEventEncoder: Encoder[CronulaEvent] = deriveEncoder[CronulaEvent]
   implicit val cronulaEventDecoder: Decoder[CronulaEvent] = deriveDecoder[CronulaEvent]
 }

  def process(event: CronulaSateEvents.CronulaEvent, state: CronulaSate = CronulaSate()): CronulaSate = event match {
    case CronulaSateEvents.RecordJob(id, cronString) => state.copy(jobs = state.jobs + (id -> CronJob(id, cronString)))
    case CronulaSateEvents.DeleteJob(id) => state.copy(jobs = state.jobs - id)
    case CronulaSateEvents.Updatejob(id, cronString) => state.copy(
      jobs = state.jobs.get(id).map(_ => state.jobs + (id -> CronJob(id, cronString))).getOrElse(state.jobs))
    case CronulaSateEvents.Clean => CronulaSate()
  }
}
