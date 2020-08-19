/**
  * Copyright (C) Milan Innovincy, B.V. - All Rights Reserved
  * Unauthorized copying of this file, via any medium is strictly prohibited
  * Proprietary and confidential
  * Created by yaniv on 05/02/2019.
  */
package com.mi.gwtf

import java.io.{File, PrintWriter}

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import ucar.nc2.{Dimension, NetcdfFile}

import scala.collection.JavaConverters._

class NetcdfImporter(netcdfDatasets: NetcdfDatasets) {

  import netcdfDatasets._

  private def getDimensionAttr(d: List[Dimension])(attr: String): Int =
    d.find(_.getShortName == attr).get.getLength


  def calculateBoundingBox(netcdfFile: NetcdfFile): ujson.Arr = {
    val lats = netcdfFile.getVariables.asScala.find(_.getShortName == "lat").get.read()
    val lons = netcdfFile.getVariables.asScala.find(_.getShortName == "lon").get.read()

    val latsList = (0 until lats.getSize.toInt).map(i => lats.getDouble(i))
    val lonsList = (0 until lons.getSize.toInt).map(i => lons.getDouble(i))

    val minLat = latsList.min
    val maxLat = latsList.max
    val minLon = lonsList.min
    val maxLon = lonsList.max

    val p1 = ujson.Arr(minLon, minLat)
    val p2 = ujson.Arr(maxLon, minLat)
    val p3 = ujson.Arr(maxLon, maxLat)
    val p4 = ujson.Arr(minLon, maxLat)

    ujson.Arr(p1, p2, p3, p4, p1)
  }

  private val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd")

  def importFiles(inputDirectory: String, printWriter: PrintWriter): Unit = {

    var boundingBoxJSON: ujson.Arr = null
    var dimensions: List[Dimension] = Nil
    var width: Int = 0
    var height: Int = 0
    var timestampCount: Int = 0
    var timestamps: ucar.ma2.Array = null
    val analytics = ujson.Arr()

    rawDatasets.headOption.foreach { descriptor: DatasetDescriptor =>
      val ncFullPath = new File(inputDirectory, descriptor.ncFilePath.get).toString
      val netcdfFile = NetcdfFile.open(ncFullPath)
      val gds = ucar.nc2.dt.grid.GridDataset.open(ncFullPath)

      if (boundingBoxJSON == null) {
        val bb = gds.getBoundingBox

        val (x1,y1) = (bb.getUpperLeftPoint.getLongitude, bb.getUpperLeftPoint.getLatitude)
        val (x2,y2) = (bb.getLowerRightPoint.getLongitude, bb.getLowerRightPoint.getLatitude)

        boundingBoxJSON = ujson.Arr(x1, y1, x2, y2)

        dimensions = netcdfFile.getDimensions.asScala.toList
        val getDim = getDimensionAttr(dimensions)(_)

        width = getDim("x")
        height = getDim("y")
        timestampCount = getDim("time")

        timestamps = netcdfFile.getVariables.asScala.find(_.getShortName == "time").get.read()
      }
    }

    val rastersByVar = rawDatasets.map { descriptor =>
      val varName = descriptor.varName
      val coverage = getCoverage(varName)
      val raster = coverage.getRenderedImage.getData()
      varName -> raster
    }.toMap

    (0 until timestampCount).foreach { tIndex =>

      val timestampInMinutesEPOC = timestamps.getLong(tIndex)
      val time = new DateTime(timestampInMinutesEPOC * 60 * 1000)
      val timeElementJson = ujson.Obj("time" -> dateFormat.print(time))

      descriptors.foreach { descriptor =>

        println("Preparing " + descriptor.modelShortName + " > " + descriptor.description)

        def getRawValue(descriptor: DatasetDescriptor, xIndex: Int, yIndex: Int, tIndex: Int): Double = {
          val raster = rastersByVar(descriptor.varName)
          val v = raster.getSampleDouble(xIndex, yIndex, tIndex)
          // TODO: consider improved data cleaning strategy, as a function per variable. Also, perhaps UI should get
          //   invalid/unknown values as null to possibly render differently
          if (v == -999.0) // value returned by the model to designate 'no value'
            0.0D
          else {
            val scaledValue = v * descriptor.coefficient
            if (scaledValue < 0)
              0.0D
            else
              scaledValue
          }
        }

        def getValue(descriptor: DatasetDescriptor, xIndex: Int, yIndex: Int, tIndex: Int): Double = {
          // For the desiredSoilWater, which is computed using two model variables, handle the computation
          // TODO: if and when there are additional computed variables, we can make this more generic and part of the
          //  descriptor and let SAF compute the values
          if (descriptor.key == "desiredSoilWater") {
            val availableSoilWaterDescriptor = rawDatasets.find(_.key == "availableSoilWater").get
            val deficitDescriptor = rawDatasets.find(_.key == "deficit").get

            val availableSoilWater = getRawValue(availableSoilWaterDescriptor, xIndex, yIndex, tIndex)
            val deficit = getRawValue(deficitDescriptor, xIndex, yIndex, tIndex)
            availableSoilWater + deficit
          }
          else {
            getRawValue(descriptor, xIndex, yIndex, tIndex)
          }
        }

        val pixels = (0 until height).map { yIndex =>
          (0 until width).map { xIndex =>
            getValue(descriptor, xIndex, yIndex, tIndex)
          }
        }.reverse // since GeoTools flips the grid
        timeElementJson.value += (descriptor.key -> pixels)
      }

      analytics.value.append(timeElementJson)

    }


    val result = ujson.Obj(
      "boundingBox" -> boundingBoxJSON,
      "dimensions" -> ujson.Arr(width, height),
      "analytics" -> analytics)

    val jsonOut: String = ujson.write(result, indent = -1)
    printWriter.write(jsonOut)
  }

}


object NetcdfImporter extends App {

  if (args.length < 2) {
    println(
      """
        |NetCDFImporter - import NetCDF files into SAF JSON format
        |
        |Usage:
        |
        |gwtf-netcdf <input dataset> <output file prefix>
        |
        |Note: in this version the dataset file types are hardcoded, and expected to include several inputs
        |in specific file name conventions. Please consult the code of NetcdfDatasets for more information.
        |
      """.stripMargin)
    System.exit(1)
  }

  val inputFilePath = args(0)
  val outputFilePrefix = args(1)

  val netcdfDatasets = new NetcdfDatasets(inputFilePath)

  val startTime = System.currentTimeMillis()
  val importer = new NetcdfImporter(netcdfDatasets)
  val outputPrintWriter = new PrintWriter(s"$outputFilePrefix-${netcdfDatasets.selectedDate}.json")
  try {
    importer.importFiles(
      netcdfDatasets.inputDirectory, outputPrintWriter)
  }
  finally {

    outputPrintWriter.flush()
    outputPrintWriter.close()

    val endTime = System.currentTimeMillis()

    println("Completed. Duration: " + (endTime - startTime) + " ms.")
  }

}

