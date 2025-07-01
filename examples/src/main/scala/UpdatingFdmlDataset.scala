import java.net.URI

import scala.concurrent.duration.*

import cats.effect.*
import cats.effect.IO

import latis.dataset.*
import latis.input.*
import latis.input.fdml.FdmlReader

object UpdatingFdmlDataset extends IOApp {

  // Run server then:
  // curl --no-buffer "http://localhost:8080/dap2/clock.txt?sine(%22PT10S%22)" -o /tmp/file.txt
  def run(args: List[String]): IO[ExitCode] = {

    val ds = FdmlReader.read(new URI("datasets/fdml/file.fdml"), false)
    UpdatingDataset(ds, 5.seconds).samples.evalMap(IO.println).compile.drain >>
      IO(ExitCode.Success)
  }

}
