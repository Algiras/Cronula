package com.ak.cronula.service

import java.util.UUID

import CronErrors._
import com.ak.cronula.model.CronJob

trait Cron[F[_]] {
  def getAll: F[Set[CronJob]]
  def get(id: UUID): F[Either[NotFoundException.type, CronJob]]
  def create(cronString: String): F[Either[ParseException.type, UUID]]
  def update(id: UUID, cronString: String): F[Either[CronException, Unit]]
  def delete(id: UUID): F[Unit]
}

object CronErrors {
  sealed trait CronException extends RuntimeException

  case object ParseException extends CronException
  case object NotFoundException extends CronException
}