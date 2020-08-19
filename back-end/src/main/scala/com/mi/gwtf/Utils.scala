/**
  * Copyright (C) Milan Innovincy, B.V. - All Rights Reserved
  * Unauthorized copying of this file, via any medium is strictly prohibited
  * Proprietary and confidential
  * Created by yaniv on 28/02/2019.
  */
package com.mi.gwtf

import java.io.PrintWriter

object Utils {

  def writeOutputAsJSON(output: Seq[Seq[String]], outputFilePath: String): Unit = {
    val out = new PrintWriter(outputFilePath)

    out.print(
      output
        .map(line => line.map("\"" + _ + "\"").mkString("[",",","]"))
        .mkString("[", ",", "]")
    )

    out.flush()
    out.close()
  }
}
