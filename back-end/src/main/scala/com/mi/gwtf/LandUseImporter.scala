/**
  * Copyright (C) Milan Innovincy, B.V. - All Rights Reserved
  * Unauthorized copying of this file, via any medium is strictly prohibited
  * Proprietary and confidential
  * Created by yaniv on 21/02/2019.
  */
package com.mi.gwtf

import java.io.File

import scala.io.Source

object LandUseImporter extends App {

  if (args.length < 2) {
    println(
      """
        |LandUseImporter - import land-use ASCII file into SAF JSON format
        |
        |Usage:
        |
        |gwtf-landuse <input file path> <output file path>
        |
      """.stripMargin)
    System.exit(1)
  }

  val inputFilePath = args(0)
  val outputFilePath = args(1)

  val source = Source.fromFile(new File(inputFilePath))
  val lines = source.getLines().drop(6).toList // drop the header lines
  source.close()

  val mapping = Map(
    1 -> "Gras",
    2 -> "Mais" ,
    3 -> "Aardappelen",
    4 -> "Bieten",
    5 -> "Granen"
  )

  val out = lines.map { line =>
    line.trim.split("\\s+").toList.map { element =>
      val code = element.toDouble.toInt
      mapping.getOrElse(code, "")
    }
  }

  println("Done!")
  println("Total no-value pixels: " + out.flatten.count(_ == ""))
  println("Out of: " + out.flatten.size)

  Utils.writeOutputAsJSON(out, outputFilePath)
}
