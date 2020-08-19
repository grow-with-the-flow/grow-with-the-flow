/**
  * Copyright (C) Milan Innovincy, B.V. - All Rights Reserved
  * Unauthorized copying of this file, via any medium is strictly prohibited
  * Proprietary and confidential
  * Created by yaniv on 13/02/2019.
  */
package com.mi.gwtf

import java.io.File

import org.geotools.coverage.grid.GridCoverage2D
import org.geotools.gce.geotiff.GeoTiffReader
import org.geotools.geometry.Envelope2D
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.geotools.util.factory.Hints
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.opengis.referencing.crs.CoordinateReferenceSystem
import ucar.nc2.{Dimension, NetcdfFile}

import scala.collection.JavaConverters._

class NetcdfDatasets(val inputDirectory: String) {

  val datasetName = "20180601"
  val selectedDate = "20180529"
  val modelFilePrefix = s"${datasetName}0000_fews_aaenmaas"
  val descriptors = List(

    DatasetDescriptor("deficit", "Ssdtot", "Vocht tekort", "Total soil water saturation deficit", Some(s"${modelFilePrefix}_Ssdtot.nc"), Some(s"${modelFilePrefix}_Ssdtot_4.tif"), "m", "mm", 1000),
    DatasetDescriptor("measuredPrecipitation", "Pact",  "Regenval", "Measured precipitation", Some(s"${modelFilePrefix}_P.im.nc"), Some(s"${modelFilePrefix}_P.im_4.tif"), "mm", "mm", 1),
    DatasetDescriptor("evapotranspiration", "ETact", "Evapotranspiratie", "Total actual evapotranspiration", Some(s"${modelFilePrefix}_ETact.nc"), Some(s"${modelFilePrefix}_ETact_4.tif"), "m", "mm", -1000),
    DatasetDescriptor("availableSoilWater", "S01", "Vochtgehalte", "Available soil water", Some(s"${modelFilePrefix}_S01.nc"), Some(s"${modelFilePrefix}_S01_4.tif"), "m", "mm", 1000),
    DatasetDescriptor("desiredSoilWater", "S01", "Gewenst vochtgehalte", "(S01 + Ssdtot) Desired soil water", None, None, "m", "mm", 1000)
  )

  val rawDatasets: List[DatasetDescriptor] = descriptors.filter(!_.isComputedDataset)

  private def getDimensionAttr(d: List[Dimension])(attr: String): Int =
    d.find(_.getShortName == attr).get.getLength


  private val fmt = DateTimeFormat.forPattern("yyyy-MM-dd")

  // first key is the date, second key is the variable name
  val (coverages, boundingBox, dates) = importFiles()


//  val variables: Seq[String] = descriptors.map(_.varName)
  val datesAsString: Seq[String] = dates.map(d => fmt.print(d))

  def getCoverage(varName: String): GridCoverage2D = coverages(varName)


  private def createEnvelope(crs: CoordinateReferenceSystem, p1x: Double, p1y: Double, p2x: Double, p2y: Double) =
    new Envelope2D(crs, p1x, p1y, p2x-p1x, p2y-p1y)


  private def calculateBoundingBox(netcdfFile: NetcdfFile): Envelope2D = {
    val lats = netcdfFile.getVariables.asScala.find(_.getShortName == "lat").get.read()
    val lons = netcdfFile.getVariables.asScala.find(_.getShortName == "lon").get.read()

    val latsList = (0 until lats.getSize.toInt).map(i => lats.getDouble(i))
    val lonsList = (0 until lons.getSize.toInt).map(i => lons.getDouble(i))

    val minLat = latsList.min
    val maxLat = latsList.max
    val minLon = lonsList.min
    val maxLon = lonsList.max

    createEnvelope(DefaultGeographicCRS.WGS84, minLon, minLat, maxLon, maxLat)
  }


  private def importFiles(): (Map[String, GridCoverage2D], Envelope2D, List[DateTime]) = {

    val primeNetcdfFile = NetcdfFile.open(new File(inputDirectory, rawDatasets.head.ncFilePath.get).toString)
    val timestamps = primeNetcdfFile.getVariables.asScala.find(_.getShortName == "time").get.read()
    val dimensions = primeNetcdfFile.getDimensions.asScala.toList
    val getDim = getDimensionAttr(dimensions)(_)

    val timestampCount = getDim("time")

    val dates = (0 until timestampCount).map { tIndex =>

      val timestampInMinutesEPOC = timestamps.getLong(tIndex)
      new DateTime(timestampInMinutesEPOC * 60 * 1000)
    }.toList

    val boundingBox = calculateBoundingBox(primeNetcdfFile)

    val coverages = rawDatasets.map { descriptor =>
      val tiffFile = new File(inputDirectory, descriptor.tiffFilePath.get)
      val geoTiffReader = new GeoTiffReader(tiffFile, new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, true))
      val coverage: GridCoverage2D = geoTiffReader.read(null)
      descriptor.varName -> coverage
    }.toMap

    (coverages, boundingBox, dates)
  }

}

case class DatasetDescriptor(key: String,
                             modelShortName: String,
                             varName: String,
                             description: String,
                             ncFilePath: Option[String],
                             tiffFilePath: Option[String],
                             sourceUnit: String,
                             targetUnit: String,
                             coefficient: Double) {
  assert(ncFilePath.isEmpty == tiffFilePath.isEmpty)

  def isComputedDataset: Boolean = ncFilePath.isEmpty
}
