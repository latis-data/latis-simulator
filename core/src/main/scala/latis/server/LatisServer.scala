package latis.server

import java.net.URI

import scala.concurrent.duration.*

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.syntax.all.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.module.catseffect.syntax.*

import latis.catalog.Catalog
import latis.catalog.FdmlCatalog
import latis.dataset.*
import latis.input.fdml.FdmlReader
import latis.input.JdbcAdapter
import latis.metadata.Metadata
import latis.model.*
import latis.ops.OperationRegistry
import latis.ops.Sine
import latis.server.Latis3ServerBuilder.*
import latis.service.dap2.Dap2Service
import latis.time.Time
import latis.util.ClockConfig
import latis.util.Identifier.id

object LatisServer extends IOApp {
  
  val getClockConfig: IO[ClockConfig] =
    latisConfigSource.at("clock").loadF[IO, ClockConfig]()
    
  //private val datasets = List(
  //  SineDataset(id"sine", 1.second, 1.minute, 60),
  //  ClockDataset(id"clock", clockConfig, 1.minute),
  //  //UpdatingDataset(FdmlReader.read(new URI("datasets/fdml/file.fdml"), false), 5.seconds),
  //  //dbDataset(5.seconds)
  //)
  /*
  the file dataset shows up as an AdaptedDataset in the TextEncoder, where are we losing the wrapper?
  it's finding the fdml file dataset
   */

  private val operationRegistry: OperationRegistry =
    OperationRegistry.default
      .add("sine", Sine.builder)

  override def run(args: List[String]): IO[ExitCode] = {
    (for {
      logger      <- Resource.eval(Slf4jLogger.create[IO])
      serverConf  <- Resource.eval(getServerConf)
      catalogConf <- Resource.eval(getCatalogConf)
      //fdmlCat     <- Resource.eval(
      //  FdmlCatalog.fromDirectory(catalogConf.dir, catalogConf.validate, operationRegistry)
      //)
      //catalog      = fdmlCat |+| Catalog(datasets*)
      clockConfig <- Resource.eval(getClockConfig)
      datasets     = List(
        SineDataset(id"sine", 1.second, 1.minute, 60),
        ClockDataset(id"clock", clockConfig.cadence, clockConfig.history)
      )
      catalog      = Catalog(datasets*)
      interfaces   = List(
        "dap2" -> new Dap2Service(catalog, operationRegistry)
      )
      server      <- mkServer(serverConf, defaultLandingPage, interfaces, logger)
    } yield server)
      .use(_ => IO.never)
      .as(ExitCode.Success)

  }

  private def dbDataset(period: FiniteDuration): Dataset = {
    val driver = "org.sqlite.JDBC"
    val uri = "jdbc:sqlite:///tmp/UpdatingDatabase.db"
    val model = (for {
      time <- Time.fromMetadata(
        Metadata(
          "id" -> "time",
          "type" -> "long",
          "units" -> "milliseconds since 1970-01-01"
        )
      )
      value = Scalar(id"value", DoubleValueType)
      f <- Function.from(time, value)
    } yield f).fold(throw _, identity)

    val config = JdbcAdapter.Config(
      "driver" -> driver,
      "table" -> "mytable",
      "user" -> "",
      "password" -> ""
    )
    val adapter = JdbcAdapter(model, config)
    val ds: Dataset = AdaptedDataset(Metadata(id"UpdatingDatabase"), model, adapter, URI.create(uri))
    UpdatingDataset(ds, period)
  }
}
