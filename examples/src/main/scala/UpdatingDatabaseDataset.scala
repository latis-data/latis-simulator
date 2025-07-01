import cats.effect.*

import java.net.URI

import scala.concurrent.duration.*

import cats.effect.IO
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*

import latis.dataset.*
import latis.input.*
import latis.metadata.Metadata
import latis.model.*
import latis.time.Time
import latis.util.Identifier.id

object UpdatingDatabaseDataset extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {

    val driver = "org.sqlite.JDBC"
    val uri    = "jdbc:sqlite:///tmp/UpdatingDatabase.db"
    val xa = Transactor.fromDriverManager[IO](driver, uri, None)

    createDatabase(xa) >>
    IO.both(
      update(xa, 1.second),
      makeDataset(driver, uri, 5.seconds).samples.evalMap(IO.println).compile.drain
    ) >> IO(ExitCode.Success)
  }

  def makeDataset(driver: String, uri: String, period: FiniteDuration): Dataset = {
    val model = (for {
      time <- Time.fromMetadata(
        Metadata(
          "id"    -> "time",
          "type"  -> "long",
          "units" -> "milliseconds since 1970-01-01"
        )
      )
      value = Scalar(id"value", DoubleValueType)
      f <- Function.from(time, value)
    } yield f).fold(throw _, identity)

    val config = JdbcAdapter.Config(
      "driver"   -> driver,
      "table"    -> "mytable",
      "user"     -> "",
      "password" -> ""
    )
    val adapter = JdbcAdapter(model, config)
    val ds: Dataset = AdaptedDataset(Metadata(id"UpdatingDatabase"), model, adapter, URI.create(uri))
    UpdatingDataset(ds, period)
  }

  private def createDatabase(xa: Transactor[IO]): IO[Unit] = {
    (
      sql"DROP TABLE IF EXISTS mytable;".update.run,
      sql"CREATE TABLE mytable(time DOUBLE PRIMARY KEY, 'value' DOUBLE);".update.run
    ).tupled.void.transact(xa)
  }

  private def update(xa: Transactor[IO], period: FiniteDuration): IO[Unit] = {
    def loop: IO[Unit] = {
      for {
        now <- IO.realTime.map(_.toMillis)
        //_   <- IO.println(s"Inserting $now")
        _   <- sql"INSERT INTO mytable VALUES ($now, ${math.sqrt(now.toDouble)});".update.run.transact(xa)
        _   <- IO.sleep(period)
        _   <- loop
      } yield ()
    }

    loop
  }

}
