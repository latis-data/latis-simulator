import java.net.URI

import scala.concurrent.duration.*

import cats.effect.*
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

object UpdatingDatabase extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {

    val driver = "org.sqlite.JDBC"
    val uri    = "jdbc:sqlite:///tmp/UpdatingDatabase.db"
    val xa = Transactor.fromDriverManager[IO](driver, uri, None)

    createDatabase(xa) >> update(xa, 1.second) >> IO(ExitCode.Success)
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
        _   <- IO.println(s"Inserting $now")
        _ <- sql"INSERT INTO mytable VALUES ($now, ${math.sqrt(now.toDouble)});".update.run.transact(xa)
        _ <- IO.sleep(period)
        _ <- loop
      } yield ()
    }

    loop
  }

}
