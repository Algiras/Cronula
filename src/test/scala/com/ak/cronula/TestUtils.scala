package com.ak.cronula

import com.ak.cronula.Main.AppTask
import zio.ZIO
import zio.duration.Duration

import scala.concurrent.duration._

object TestUtils {
  def retry[A](action: AppTask[A])(
    repeatUntil: A => Boolean,
    duration: Duration = Duration.fromScala(5.seconds)
  ): ZIO[zio.ZEnv, Throwable, A] = {
    def repeatedAction: ZIO[zio.ZEnv, Throwable, A] = for {
      res <- action
      finalResult <- if(repeatUntil(res)) ZIO(res) else for {
        _ <- ZIO.sleep(Duration.fromScala(100.milliseconds))
        fr <- repeatedAction
      } yield fr
    } yield finalResult

    ZIO.raceAll(
      repeatedAction.map(Option(_)),
      Seq(ZIO.sleep(duration).map(_ => None))
    ).flatMap {
      case Some(value) => ZIO(value)
      case None => ZIO.fail(new RuntimeException("Retry failed"))
    }
  }
}
