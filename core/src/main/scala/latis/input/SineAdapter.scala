package latis.input

import java.net.URI

import scala.concurrent.duration.*

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream

import latis.data.*
import latis.data.Data.*

/**
 * Sine wave dataset adapter.
 * 
 * Given a [[cadence]] and [[period]], this produces sine wave data samples
 * at the real-time rate defined by that cadence. Time values will be in 
 * milliseconds since 1970 and step by that same cadence. The single range 
 * variable will be the sine value from -1 to 1 that cycles with the given 
 * period. If [[batchFirst]] is defined, that many samples will be emitted 
 * rapidly before the metered cadence kicks in.
 */
case class SineAdapter(
  cadence: FiniteDuration,
  period: FiniteDuration,
  batchFirst: Int
) extends StreamingAdapter[Long] {

  private val source = Stream.iterate(0L)(_ + cadence.toMillis)

  def recordStream(uri: URI): Stream[IO, Long] =
    source.take(batchFirst).chunkAll.unchunks ++
      source.drop(batchFirst).metered[IO](cadence)

  def parseRecord(t: Long): Option[Sample] = {
    val sin = math.sin(2 * math.Pi * t.toDouble / period.toMillis)
    Sample(DomainData(t), RangeData(DoubleValue(sin))).some
  }

}
