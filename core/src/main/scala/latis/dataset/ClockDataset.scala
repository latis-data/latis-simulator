package latis.dataset

import scala.concurrent.duration.*

import latis.input.*
import latis.metadata.*
import latis.model.*
import latis.time.Time
import latis.util.Identifier
import latis.util.Identifier.id

/**
 * Real-time time series dataset.
 *
 * This makes a dataset using the [[ClockAdapter]].
 */
object ClockDataset {

  private val model: DataType =
    (for {
      time <- Time.fromMetadata(Metadata(
        "id"    -> "time",
        "units" -> "milliseconds since 1970-01-01",
        "type"  -> "long"
      ))
      func <- Function.from(Index(), time)
    } yield func).fold(throw _, identity)

  def apply(
    id: Identifier,
    cadence: FiniteDuration
  ): Dataset = new AdaptedDataset(
    Metadata(id),
    model,
    ClockAdapter(cadence),
    null
  )
}
