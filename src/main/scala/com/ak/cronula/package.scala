package com.ak

import zio.ZIO
import zio.interop.catz._
import zio.stream.ZStream

package object cronula {
  // copied because of version conflicts from https://github.com/zio/interop-cats/commit/05a4920ece6b99f2cdf844c3184e633b72c035a8#diff-0a8af19ea41e6cd8097197558323622c3a31681541b6b1fc0bc2e857ce2dd4cfR22
  private[cronula] def toFs2Stream[R, E, A](stream: ZStream[R, E, A]): fs2.Stream[ZIO[R, E, *], A] =
    fs2.Stream.resource(stream.process.toResourceZIO).flatMap { pull =>
      fs2.Stream.repeatEval(pull.optional).unNoneTerminate.flatMap { chunk =>
        fs2.Stream.chunk(fs2.Chunk.indexedSeq(chunk))
      }
    }
}
