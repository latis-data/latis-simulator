package latis.server

import scala.concurrent.duration.*

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.syntax.all.*
import org.typelevel.log4cats.slf4j.Slf4jLogger

import latis.catalog.Catalog
import latis.catalog.FdmlCatalog
import latis.dataset.*
import latis.ops.OperationRegistry
import latis.ops.Sine
import latis.server.Latis3ServerBuilder.*
import latis.service.dap2.Dap2Service
import latis.util.Identifier.id

object LatisServer extends IOApp {

  private val datasets = List(
    SineDataset(id"sine", 1.second, 1.minute, 60),
    ClockDataset(id"clock", 1.second)
  )

  private val operationRegistry: OperationRegistry =
    OperationRegistry.default
      .add("sine", Sine.builder)

  override def run(args: List[String]): IO[ExitCode] =
    (for {
      logger      <- Resource.eval(Slf4jLogger.create[IO])
      serverConf  <- Resource.eval(getServerConf)
      catalogConf <- Resource.eval(getCatalogConf)
      fdmlCat     <- Resource.eval(
        FdmlCatalog.fromDirectory(catalogConf.dir, catalogConf.validate, operationRegistry)
      )
      catalog      = fdmlCat |+| Catalog(datasets*)
      interfaces   = List(
        "dap2" -> new Dap2Service(catalog, operationRegistry)
      )
      server      <- mkServer(serverConf, defaultLandingPage, interfaces, logger)
    } yield server)
      .use(_ => IO.never)
      .as(ExitCode.Success)
}
