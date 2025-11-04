import java.net.URI

import scala.concurrent.duration.*

import cats.effect.*
import cats.effect.IO

import latis.dataset.*
import latis.input.*
import latis.metadata.Metadata
import latis.model.*
import latis.time.Time
import latis.util.Identifier.id

object UpdatingFileDataset extends IOApp {

  // Run server then:
  // curl --no-buffer "http://localhost:8080/dap2/clock.txt?sine(%22PT10S%22)" -o /tmp/file.txt
  def run(args: List[String]): IO[ExitCode] = {

    val model = (for {
      time <- Time.fromMetadata(Metadata(
        "id"    -> "time",
        "type"  -> "double",
        "units" -> "milliseconds since 1970-01-01"
      ))
      value = Scalar(id"value", DoubleValueType)
      f    <- Function.from(time, value)
    } yield f).fold(throw _, identity)

    val adapter = TextAdapter(model)
    val ds: Dataset = AdaptedDataset(
      Metadata(id"UpdatingFile"),
      model,
      adapter,
      URI.create("file:///tmp/file.txt")
    )

    UpdatingDataset(ds, 5.seconds).samples.evalMap(IO.println).compile.drain >>
      IO(ExitCode.Success)
  }

}
