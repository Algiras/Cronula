package com.ak.cronula.model

import java.util.UUID

import com.ak.cronula.model.CronulaSate.CronulaSateEvents.{Clean, CronulaEvent, DeleteJob, RecordJob, Updatejob}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class CronulaStateTest extends Specification {
    "CronulaState" should {
      "create jobs" in new Context {
        processState(
          RecordJob(id, cronExpr)
        ).jobs.get(id) must beSome(CronJob(id, cronExpr))
      }

      "update if creating with existing id" in new Context {
        processState(
          RecordJob(id, cronExpr),
          RecordJob(id, cronExpr2)
        ).jobs.get(id) must beSome(CronJob(id, cronExpr2))
      }

      "delete jobs" in new Context {
        processState(
          RecordJob(id, cronExpr),
          DeleteJob(id)
        ).jobs.get(id) must beNone
      }

      "deleting non exiting job has no effect" in new Context {
        processState(
          RecordJob(id, cronExpr),
          DeleteJob(id2)
        ).jobs.get(id) must beSome(CronJob(id, cronExpr))
      }

      "update jobs" in new Context {
        processState(
          RecordJob(id, cronExpr),
          Updatejob(id, cronExpr2)
        ).jobs.get(id) must beSome(CronJob(id, cronExpr2))
      }

      "updating non existing job has no effect" in new Context {
        processState(
          Updatejob(id, cronExpr),
        ).jobs.get(id) must beNone
      }

      "clean event deletes all records" in new Context {
        val initialState = processState(
          RecordJob(id, cronExpr),
          RecordJob(id2, cronExpr2),
        )

        initialState.jobs.values must contain(
          CronJob(id, cronExpr),
          CronJob(id2, cronExpr2)
        )

        processWithState(initialState)(
          Clean
        ).jobs must beEmpty
      }
    }

    trait Context extends Scope {
      val id = UUID.randomUUID()
      val id2 = UUID.randomUUID()
      val cronExpr = cron4s.Cron.parse("10-35 2,4,6 * ? * *").fold(throw _, identity)
      val cronExpr2 = cron4s.Cron.parse("10-34 2,4,6 * ? * *").fold(throw _, identity)

      def processState(event: CronulaEvent, rest: CronulaEvent*): CronulaSate =
        processWithState(CronulaSate.empty)(event, rest: _*)

      def processWithState(initial: CronulaSate)(event: CronulaEvent, rest: CronulaEvent*) = (event +: rest)
        .foldLeft(initial)((state, event) => CronulaSate.process(event, state))
    }
}
