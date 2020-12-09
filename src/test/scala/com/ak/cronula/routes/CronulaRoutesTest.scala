package com.ak.cronula.routes

import java.util.UUID

import cats.effect.IO
import cats.implicits._
import com.ak.cronula.CronulaRoutes.CronRequest
import com.ak.cronula.model.Cron
import com.ak.cronula.model.Cron.CronJob
import com.ak.cronula.service.Cron
import com.ak.cronula.service.CronErrors.{NotFoundException, ParseException}
import com.ak.cronula.{CronulaRoutes, service}
import com.wixpress.common.specs2.JMock
import cron4s.CronExpr
import cron4s.circe._
import io.circe.Json
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.implicits._
import org.specs2.matcher.{MatchResult, Matcher}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class CronulaRoutesTest extends Specification with JMock {
  implicit val cronEntityDecoder: EntityDecoder[IO, CronJob] = jsonOf[IO, CronJob]
  implicit val cronEntitySeqDecoder: EntityDecoder[IO, Seq[CronJob]] = jsonOf[IO, Seq[CronJob]]
  implicit val cronRequestEncoder: EntityEncoder[IO, CronRequest] = jsonEncoderOf[IO, CronRequest]

  "CronulaRoutes" should {
      "get Cron task by ID" in new Context {
        checking {
          oneOf(cronService).get(id) willReturn IO.pure(Right(Cron.CronJob(id, cronExpr)))
        }

        check(
          Request(method = Method.GET, uri = routeFromStr(s"cron/$id")),
          Status.Ok,
          Some(be_===(CronJob(id, cronExpr)))
        )
      }

    "delete Cron task by ID" in new Context {
      checking {
        oneOf(cronService).delete(id) willReturn IO.pure(())
      }

      check(
        Request(method = Method.DELETE, uri = routeFromStr(s"cron/$id")),
        Status.Ok,
        Some(be_===(()))
      )
    }

    "return not found when task does not exists" in new Context {
      checking {
        oneOf(cronService).get(id) willReturn IO.pure(Left(NotFoundException))
      }

      check[Json](
        Request(method = Method.GET, uri = routeFromStr(s"cron/$id")),
        Status.NotFound,
      )
    }

     "get all Cron tasks" in new Context {
       val cronJobs = Set(
         Cron.CronJob(id, cronExpr),
         Cron.CronJob(id, cronExpr2)
       )

       checking {
         oneOf(cronService).getAll willReturn IO.pure(cronJobs)
       }

       check[Seq[CronJob]](
         Request(method = Method.GET, uri = uri"cron"),
         Status.Ok,
         Some(containAllOf(cronJobs.toSeq))
       )
     }

    "create Cron task" in new Context {
      checking {
        oneOf(cronService).create(cronString) willReturn IO.pure(Right(id))
      }

      check(
        Request(method = Method.POST, uri = uri"cron").withEntity(CronRequest(cronString)),
        Status.Ok,
        Some(be_===(id.toString))
      )
    }

    "fail when create fails" in new Context {
      val invalidCronString = "???"

      checking {
        oneOf(cronService).create(invalidCronString) willReturn IO.pure(Left(ParseException))
      }

      check[Json](
        Request(method = Method.POST, uri = uri"cron").withEntity(CronRequest(invalidCronString)),
        Status.BadRequest
      )
    }

    "update Cron task" in new Context {
      checking {
        oneOf(cronService).update(id, cronString) willReturn IO.pure(Right(()))
      }

      check(
        Request(method = Method.PUT, uri = routeFromStr(s"cron/$id")).withEntity(CronRequest(cronString)),
        Status.Ok,
        Some(be_===(()))
      )
    }

    "fail when update fails" in new Context {
      checking {
        oneOf(cronService).update(id, cronString) willReturn IO.pure(Left(ParseException))
      }

      check[Json](
        Request(method = Method.PUT, uri = routeFromStr(s"cron/$id")).withEntity(CronRequest(cronString)),
        Status.BadRequest
      )
    }
  }

  trait Context extends Scope {
    val id: UUID = UUID.randomUUID()
    val id2: UUID = UUID.randomUUID()
    val cronExpr: CronExpr = cron4s.Cron.parse("10-35 2,4,6 * ? * *").fold(throw _, identity)
    val cronExpr2: CronExpr = cron4s.Cron.parse("10-35 1,4,6 * ? * *").fold(throw _, identity)

    val cronString = "10-35 2,4,6 * ? * *"

    val cronService: Cron[IO] = mock[service.Cron[IO]]
    val routes = CronulaRoutes.cronRoutes[IO](cronService).orNotFound


    def routeFromStr(route: String): Uri = Uri.fromString(route).fold(throw _, identity)

    def check[A](request: Request[IO],
                 expectedStatus: Status,
                 expectedBody:   Option[Matcher[A]] = None)(
                  implicit ev: EntityDecoder[IO, A]
                ): MatchResult[Any] =  {
      val actualResp = routes.run(request).unsafeRunSync()

      actualResp.status must_=== expectedStatus
      if(expectedBody.isDefined) {
        actualResp.as[A].unsafeRunSync must expectedBody.get
      } else {
        actualResp.body.compile.toVector.unsafeRunSync must beEmpty
      }
    }
  }
}
