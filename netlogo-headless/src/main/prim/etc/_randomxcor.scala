// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.prim.etc

import org.nlogo.nvm.{ Context, Reporter }

class _randomxcor extends Reporter {
  override def report(context: Context): java.lang.Double =
    Double.box(report_1(context))
  def report_1(context: Context): Double = {
    val min = world.minPxcor - 0.5
    val max = world.maxPxcor + 0.5
    min + context.job.random.nextDouble * (max - min)
  }
}
