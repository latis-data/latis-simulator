ThisBuild / organization := "lasp"
ThisBuild / scalaVersion := "3.3.3"

val latisVersion = "cebc9fac" //TODO: update after SSE merged

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "ch.qos.logback"                % "logback-classic"            % "1.3.14" % Runtime,
    "com.github.latis-data.latis3" %% "latis3-core"                % latisVersion,
    "com.github.latis-data.latis3" %% "latis3-jdbc"                % latisVersion,
    "com.github.latis-data.latis3" %% "latis3-server"              % latisVersion,
    "com.github.latis-data.latis3" %% "dap2-service-interface"     % latisVersion,
    "com.github.latis-data.latis3" %% "latis3-service-interface"   % latisVersion,
    //"io.latis-data" %% "latis3-core"                % "0.1.0-SNAPSHOT",
    //"io.latis-data" %% "latis3-jdbc"                % "0.1.0-SNAPSHOT",
    //"io.latis-data" %% "latis3-server"              % "0.1.0-SNAPSHOT",
    //"io.latis-data" %% "dap2-service-interface"     % "0.1.0-SNAPSHOT",
    //"io.latis-data" %% "latis3-service-interface"   % "0.1.0-SNAPSHOT",
    "org.xerial"      % "sqlite-jdbc" % "3.50.1.0",
    "org.scalameta" %% "munit" % "0.7.29" % Test,
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
  ),
  resolvers ++= Seq(
    "Unidata" at "https://artifacts.unidata.ucar.edu/content/repositories/unidata-releases",
    "jitpack" at "https://jitpack.io"
  ),
  scalacOptions -= "-Xfatal-warnings",
  //scalacOptions += "-explain"
)

/**
 * Gets the `version` from the latter part of a <project>@<version> tag
 * plus decorations from git describe.
 */
lazy val gitSettings = Seq(
  git.useGitDescribe := true,
  git.gitDescribePatterns := Seq(s"${name.value}@*"),
  git.gitTagToVersionNumber := { tag: String =>
    tag.split("@") match {
      case Array(_, v) => Some(v)
      case _ => None
    }
  }
)

lazy val dockerSettings = Seq(
  docker / imageNames := {
    val registry = "docker-registry.pdmz.lasp.colorado.edu/web"
    val tag = if (isSnapshot.value) "dev" else version.value
    Seq(
      ImageName(s"$registry/${name.value}:$tag"),
      ImageName(s"$registry/${name.value}:latest")
    )
  },
  docker / dockerfile := {
    val mainclass = "latis.server.LatisServer"
    //val fdmlFiles = baseDirectory.value / "datasets/fdml"
    val fdmlFiles = file("datasets/fdml")
    val fdml = "-Dlatis.fdml.dir=\"/fdml\""
    val depClasspath = (Runtime / managedClasspath).value
    val intClasspath = (Runtime / internalDependencyAsJars).value
    val cp = (depClasspath ++ intClasspath).files.map { x =>
      s"/app/${x.getName}"
    }.mkString(":")

    val entryCommand = s"exec java $$JAVA_OPTS $fdml -cp $cp $mainclass"

    new Dockerfile {
      from("eclipse-temurin:17-jre-alpine")
      expose(8080)
      entryPoint("/bin/sh", "-c", entryCommand)
      copy(depClasspath.files, "/app/")
      copy(intClasspath.files, "/app/")
      copy(fdmlFiles, "/fdml")
    }
  },
  docker / buildOptions := BuildOptions(
    additionalArguments = Seq("--platform", "linux/amd64"),
    pullBaseImage = BuildOptions.Pull.Always
  )
)

lazy val core = project
  .enablePlugins(DockerPlugin)
  .enablePlugins(GitVersioning)
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(gitSettings)
  .settings(
    name := "latis-simulator"
  )

lazy val examples = project
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "examples",
    libraryDependencies ++= Seq(
      "org.tpolecat"   %% "doobie-core" % "1.0.0-RC9",
      "com.h2database"  % "h2"          % "2.2.224",
      //"org.xerial"      % "sqlite-jdbc" % "3.50.1.0",
    )
  )
