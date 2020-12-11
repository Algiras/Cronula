package com.ak.cronula

import com.wixpress.dst.greyhound.core
import com.wixpress.dst.greyhound.core.{Headers, Serde, Serdes}
import helpers.ShapesDerivation._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import zio.{Chunk, Task, ZIO}

package object service {
  def circeSerde[T](implicit decoder: Decoder[T], encoder: Encoder[T]): Serde[T] = new Serde[T] {
    override def deserialize(topic: core.Topic, headers: Headers, data: Chunk[Byte]): Task[T] =
      Serdes.StringSerde.deserialize(topic, headers, data).flatMap(str => ZIO.fromEither(decode[T](str)))

    override def serialize(topic: String, value: T): Task[Chunk[Byte]] =
      Serdes.StringSerde.serialize(topic, value.asJson.noSpaces)
  }
}
