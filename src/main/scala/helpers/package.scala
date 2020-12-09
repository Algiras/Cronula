import io.circe.generic.extras.Configuration
import io.circe.{Decoder, Encoder}

package object helpers {
  object ShapesDerivation {
    implicit val genDevConfig: Configuration =  Configuration.default.withDiscriminator("type")

    import io.circe.shapes
    import shapeless.{Coproduct, Generic}

    implicit def encodeAdtNoDiscr[A, Repr <: Coproduct](implicit
                                                        gen: Generic.Aux[A, Repr],
                                                        encodeRepr: Encoder[Repr]
                                                       ): Encoder[A] = encodeRepr.contramap(gen.to)

    implicit def decodeAdtNoDiscr[A, Repr <: Coproduct](implicit
                                                        gen: Generic.Aux[A, Repr],
                                                        decodeRepr: Decoder[Repr]
                                                       ): Decoder[A] = decodeRepr.map(gen.from)

  }

}
