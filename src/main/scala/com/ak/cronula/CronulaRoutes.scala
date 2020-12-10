package com.ak.cronula

import java.util.UUID

import cats.effect.Sync
import cats.implicits._
import com.ak.cronula.model.{Action, CronJob}
import com.ak.cronula.service.CronErrors
import com.ak.cronula.service.CronErrors.CronException
import cron4s.circe._
import fs2.Stream
import io.circe.Encoder
import io.circe.generic.auto._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes}

object CronulaRoutes {
  implicit val f = io.circe.jawn.CirceSupportParser.facade

  implicit def uuidEncoder[F[_]]: EntityEncoder[F, UUID] = jsonEncoderOf[F, UUID]
  implicit def actionEncoder[F[_]]: EntityEncoder[F, Action] = jsonEncoderOf[F, Action]
  implicit def cronEntityEncoder[F[_]]: EntityEncoder[F, CronJob] = jsonEncoderOf[F, CronJob]
  implicit def listEncoder[F[_], A](implicit enc: Encoder[A], env: EntityEncoder[F, A]): EntityEncoder[F, List[A]] = jsonEncoderOf[F, List[A]]

  implicit def cronEntityDecoder[F[_] : Sync]: EntityDecoder[F, CronRequest] = jsonOf[F, CronRequest]

  def cronRoutes[F[_] : Sync](C: service.Cron[F], actionStream: Stream[F, Action]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    def parseToResponse[T](resp: Either[CronException, T])(implicit entityEncoder: EntityEncoder[F, T]) = resp.fold(error => error match {
      case CronErrors.ParseException => BadRequest()
      case CronErrors.NotFoundException => NotFound()
    }, Ok(_))

    HttpRoutes.of[F] {
      case GET -> Root / "cron" / UUIDVar(id) => for {
        cronEth <- C.get(id)
        resp <- parseToResponse(cronEth)
      } yield resp
      case GET -> Root / "cron" =>
        for {
          crons <- C.getAll
          resp <- Ok(crons.toList)
        } yield resp
      case DELETE -> Root / "cron" / UUIDVar(id) => for {
        cronEth <- C.delete(id)
        resp <- parseToResponse(Right(cronEth))
      } yield resp
      case req@POST -> Root / "cron" => for {
        cronRequest <- req.as[CronRequest]
        result <- C.create(cronRequest.cron)
        resp <- parseToResponse(result)
      } yield resp
      case req@PUT -> Root / "cron" / UUIDVar(id) => for {
        cronRequest <- req.as[CronRequest]
        result <- C.update(id, cronRequest.cron)
        resp <- parseToResponse(result)
      } yield resp
      case GET -> Root / "tasks" => Ok(actionStream)
    }
  }

  case class CronRequest(cron: String)
}