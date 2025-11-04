package latis.ops

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.MILLISECONDS

import cats.syntax.all.*

import latis.data.*
import latis.data.Data.DoubleValue
import latis.model.*
import latis.time.Time
import latis.util.Identifier.id
import latis.util.LatisException

/**
 * Transforms a Dataset of Times only into a time series of sine values.
 */
case class Sine(period: FiniteDuration) extends MapOperation {

  override def mapFunction(model: DataType): Sample => Sample =
    sample => sample match {
      case Sample(_, RangeData(Number(t))) =>
        //TODO: make sure time has units of ms
        // Round to seconds to improve reproducibility
        val sin = math.sin(2 * math.Pi * Math.round(t / 1000) / period.toSeconds)
        Sample(DomainData(t), RangeData(DoubleValue(sin)))
    }

  override def applyToModel(model: DataType): Either[LatisException, DataType] =
    model match {
      case Function(_: Index, t: Time) =>
        Function.from(t, Scalar(id"value", DoubleValueType))
      case _ =>
        val msg = "The Sine operation expects a dataset of times only."
        LatisException(msg).asLeft
    }
}

object Sine {

  /**
   * Constructs a Sine operation with an optional period as an ISO 8601
   * duration string, defaulting to one minute.
   */
  def builder: OperationBuilder = (args: List[String]) => args match {
    case Nil      => Sine(1.minute).asRight
    case p :: Nil => Either.catchNonFatal {
      val duration = FiniteDuration(java.time.Duration.parse(p).toMillis, MILLISECONDS)
      Sine(duration)
    }.leftMap(LatisException(_))
    case _        => LatisException("Sine operation expects no more tha one argument").asLeft
  }
}
