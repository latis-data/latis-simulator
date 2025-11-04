package latis.util

import scala.concurrent.duration.FiniteDuration
import pureconfig.ConfigReader

case class ClockConfig (
  cadence: FiniteDuration,
  history: FiniteDuration
) derives ConfigReader

