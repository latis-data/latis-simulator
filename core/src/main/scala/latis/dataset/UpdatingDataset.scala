package latis.dataset

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all.*
import fs2.Pull
import fs2.Stream

import latis.data.*
import latis.metadata.Metadata
import latis.model.*
import latis.ops.Operation
import latis.ops.Selection
import latis.ops.UnaryOperation
import latis.util.LatisException
import latis.util.dap2.parser.ast
import latis.util.dap2.parser.ast.Gt
import latis.util.dap2.parser.ast.GtEq
import latis.util.Identifier.id

/**
 * Dataset that streams updates at a given frequency.
 *
 * This Dataset wraps another Dataset keeping track of the latest Sample
 * and periodically makes a request of that Dataset with a time Selection
 * since the latest time.
 */
case class UpdatingDataset(
  ds: Dataset,
  period: FiniteDuration
) extends Dataset {
  //TODO: have event trigger update (see util in tailemetry)
  //TODO: CANCEL, update keeps running after user cancels (LATISTLM-28)

  def metadata: Metadata = ds.metadata

  def model: DataType = ds.model

  def samples: Stream[IO, Sample] = {
    val start: Option[Sample] = None
    val io = Ref[IO].of(start).map { ref =>
      pullSamplesAndSetLast(ds.samples, ref).stream ++ //current samples
      Stream.fixedRate[IO](period).evalMap { _ =>
        streamSinceLast(ref)
      }.flatten
    }
    Stream.eval(io).flatten
  }

  def streamSinceLast(ref: Ref[IO, Option[Sample]]): IO[Stream[IO, Sample]] =
    ref.get.map { last =>
      //Stream.exec(IO.println(s"last: $last")) ++
      pullSamplesAndSetLast(moreSamples(last), ref).stream
    }

  private def pullSamplesAndSetLast(
    samples: Stream[IO, Sample],
    ref: Ref[IO, Option[Sample]]
  ): Pull[IO, Sample, Unit] = {
    samples.pull.uncons1.flatMap {
      case Some((head, tail)) =>
        //Pull.eval(IO.println(s"head: $head")) >>
        Pull.eval(ref.set(Some(head))) >>
        Pull.output1(head) >> pullSamplesAndSetLast(tail, ref)
      case None =>
        //Pull.eval(IO.println(s"pull done")) >>
        Pull.done
    }
  }


  /** Requests newer samples from the wrapped dataset given the last sample. */
  private def moreSamples(lastSample: Option[Sample]): Stream[IO, Sample] = {
    //TODO: review native time value consequences
    //  some numeric values could be confused to ISO 8601 which is checked first
    //  use Time variable to convert time value to ISO?
    //TODO: terminate for time upper bound ("take" does terminate)
    lastSample match {
      case Some(Sample(DomainData(Integer(t)), _)) => moreSamples(t.toString)
      case Some(Sample(DomainData(Real(t)), _))    => moreSamples(t.toString)
      case Some(Sample(DomainData(Text(t)), _))    => moreSamples(t)
      case None    => Stream.empty
      case Some(s) => Stream.raiseError(LatisException(s"Bad Sample: $s"))
    }
  }

  /** Requests newer samples from the wrapped dataset given the last time. */
  private def moreSamples(lastTime: String): Stream[IO, Sample] = {
    // Replace lower bound time selection
    //TODO: be careful about order of operation, especially with unit conversion/formatting
    //  just append it and rely on compiler?
    //  use replace to preserve order, but need to update wrapped dataset ops
    val ops = ds.operations.filterNot(isLowerBoundTimeSelection) :+
      Selection(id"time", Gt, lastTime)
    Stream.eval(IO.println(s"More samples since $lastTime")) >> //debug
      ds.withOperations(ops).samples
  }

  private def isLowerBoundTimeSelection(operation: Operation) = operation match {
    //TODO: use model to get domain id instead of assuming "time"
    case Selection(id, op, _) => (id.asString == "time") && (op == Gt || op == GtEq)
    case _ => false
  }

  def operations: List[UnaryOperation] = ds.operations

  /** Push operations down to wrapped dataset. */
  def withOperation(op: UnaryOperation): Dataset = {
    val ds2 = ds.withOperation(op)
    UpdatingDataset(ds2, period)
  }

  def unsafeForce(): MemoizedDataset = ???
}
