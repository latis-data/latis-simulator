package latis.dataset

import scala.concurrent.duration.*

import latis.input.*
import latis.metadata.*
import latis.model.*
import latis.time.Time
import latis.util.Identifier
import latis.util.Identifier.id

/**
 * Sine wave time series dataset.
 *
 * This makes a dataset using the [[SineAdapter]].
 */
object SineDataset {

  private val model: DataType =
    (for {
      time <- Time.fromMetadata(Metadata(
        "id" -> "time",
        "units" -> "milliseconds since 1970-01-01",
        "type" -> "long"
      ))
      value = Scalar(id"value", DoubleValueType)
      func <- Function.from(time, value)
    } yield func).fold(throw _, identity)

  def apply(
    id: Identifier,
    cadence: FiniteDuration,
    period: FiniteDuration,
    batchFirst: Int = 0
  ): Dataset = new AdaptedDataset(
    Metadata(id),
    model,
    SineAdapter(cadence, period, batchFirst),
    null
  )
}
