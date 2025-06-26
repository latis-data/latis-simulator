package latis.input

import java.net.URI

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.kernel.Clock
import cats.syntax.all.*
import fs2.Stream

import latis.data.*
import latis.data.Data.*
import latis.model.*

/**
 * Adapter to provide a Stream of current times.
 *
 * Given a [[cadence]], this produces a Sample with the current time
 * at the real-time rate defined by that cadence. Time values will be in
 * milliseconds since 1970. The time steps are slightly irregular by a
 * few ms.
 */
case class ClockAdapter(
  cadence: FiniteDuration) extends StreamingAdapter[Long] {
  //TODO: add lag: time before now

  private val source = Stream.iterate(0L)(_ + cadence.toMillis)

  def recordStream(uri: URI): Stream[IO, Long] = {
    val now = Clock[IO].realTime.map(_.toMillis)
    Stream.repeatEval(now).spaced(1.second) //.evalTap(IO.println)
  }

  def parseRecord(t: Long): Option[Sample] =
    Sample(DomainData(), RangeData(LongValue(t))).some

}

object ClockAdapter extends AdapterFactory {

  //TODO: use cadence property
  def apply(model: DataType, config: AdapterConfig): ClockAdapter =
    ClockAdapter(1.second)

}
