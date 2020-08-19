/**
  * Copyright (C) Milan Innovincy, B.V. - All Rights Reserved
  * Unauthorized copying of this file, via any medium is strictly prohibited
  * Proprietary and confidential
  * Created by yaniv on 21/02/2019.
  */
package com.mi.gwtf

import java.io.File

import org.geotools.data.DataStoreFinder
import org.geotools.data.simple.SimpleFeatureCollection
import org.geotools.factory.CommonFactoryFinder
import org.geotools.geometry.Envelope2D
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.joda.time.DateTime
import org.locationtech.jts.geom._
import org.opengis.feature.simple.SimpleFeature
import org.opengis.geometry.BoundingBox

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer


// Takes a SHP file, in which shapes are annotated with soil type, and generates a raster in which each pixel
// represents a soil type.
object SoilMapGenerator extends App {

  private val geometryFactory = new GeometryFactory()

  if (args.length < 4) {
    println(
      """
        |SoilMapGenerator - generates a raster soil map, out of a vector map (shape file)
        |
        |Usage:
        |
        |gwtf-soilmap <input-file-path> <output-file-path> <bounding-box> <grid-dimensions>
        |
        |input-file-path   The input file path should exclude the extension (.shp or .prj)
        |output-file-path  The output will be generated in this path
        |bounding-box      The bounding box coordinates in which the soil map is defined (x,y,width,height).
        |                  Must be given in WGS84
        |grid-dimensions   The size of the output grid (columns,rows)
        |
      """.stripMargin)
    System.exit(1)
  }

  private val inputFilePath = args(0)
  private val outputFilePath = args(1)
  private val boundingBoxParams = args(2).split(',')
  private val gridDimensions = args(3).split(',').map(_.toInt)

  /**
    * Generates a raster soil map
    *
    * The input is a soil map shapefile, such as
    * Grondsoortenkaart van Nederland 2006
    * that contains polygons or multipolygons, annotated with soil type.
    * It then creates a grid of given dimensions, and produces a raster in which each pixel is labeled with the soil type.
    *
    * if a pixel falls into multiple shapes, then the shape that covers maximum of the pixel
    * is used.
    *
    * @param soilShapeFile shape file path, excluding the .shp extension
    * @param soilTypePropertyName the name of the parameter in the ShapeFile that represents the soil type in each feature
    * @param boundingBox the bounding box of the output, which should be a subset of the map
    * @param gridDimensions the dimensions (width, height) of the output grid (the requested resolution)
    */
  def generateMap(soilShapeFile: String, soilTypePropertyName: String,
                  boundingBox: BoundingBox, gridDimensions: (Int,Int)): Seq[Seq[String]] = {

    val filterFactory = CommonFactoryFinder.getFilterFactory2

    val (refBoundingBox, soilFeatures) = loadFeatures(soilShapeFile, boundingBox)

    println("Amount of soil features loaded is: " + soilFeatures.size)

    val (pixelWidth, pixelHeight) = gridDimensions

    (0 until pixelHeight).map { yIndex =>
      (0 until pixelWidth).map { xIndex =>

        // For the nice ASCII output
        if (xIndex == 0) {
          println()
          printf("[%03d] ", yIndex)
        }

        // build the pixel geometry
        val (pixelGeometry, pixelBoundingBox) = createPixelGeometry(refBoundingBox, pixelWidth, pixelHeight)(xIndex, yIndex)

        // Now pick only the shapes that are relevant for the current pixel, and put them in a Scala list for easier manipulations
        val pixelFilter = filterFactory.bbox(filterFactory.property(soilFeatures.getSchema.getGeometryDescriptor.getLocalName), pixelBoundingBox)
        val pixelFeatures = soilFeatures.subCollection(pixelFilter).features
        val pixelFeaturesBuffer = new ArrayBuffer[SimpleFeature]()
        while (pixelFeatures.hasNext) {
          pixelFeaturesBuffer.append(pixelFeatures.next)
        }
        pixelFeatures.close()


        pixelFeaturesBuffer.toList match {
          case Nil =>
            print("x")
            ""
          case List(feature) =>
            print(".")
            feature.getProperty(soilTypePropertyName).getValue.toString
          case list =>
            list.find(_.getDefaultGeometryProperty.getValue.asInstanceOf[MultiPolygon].contains(pixelGeometry)) match {
              case Some(f) =>
                print("+")
                f.getProperty(soilTypePropertyName).getValue.toString
              case None =>
                val soilOfMax = list.maxBy { feature =>
                  val multiPolygon = feature.getDefaultGeometryProperty.getValue.asInstanceOf[MultiPolygon]

                  if (multiPolygon.isValid)
                    pixelGeometry.intersection(multiPolygon).getArea
                  else
                    0
                }.getProperty(soilTypePropertyName).getValue.toString
                print("o")
                soilOfMax
            }
        }
      }
    }
  }

  private def createPixelGeometry(boundingBox: BoundingBox, pixelWidth: Int, pixelHeight: Int)
                                 (xIndex: Int, yIndex: Int): (Polygon, Envelope2D) = {

    val stepX = boundingBox.getWidth / pixelWidth
    val stepY = boundingBox.getHeight / pixelHeight

    val minX = boundingBox.getMinX + stepX*xIndex
    val maxX = boundingBox.getMinX + stepX*(xIndex+1)
    val minY = boundingBox.getMinY + stepY*yIndex
    val maxY = boundingBox.getMinY + stepY*(yIndex+1)

    (geometryFactory.createPolygon(Array(
      new Coordinate(minX, minY),
      new Coordinate(maxX, minY),
      new Coordinate(maxX, maxY),
      new Coordinate(minX, maxY),
      new Coordinate(minX, minY))), new Envelope2D(boundingBox.getCoordinateReferenceSystem, minX, minY, maxX-minX, maxY-minY))
  }


  private def loadFeatures(soilShapeFile: String, boundingBox: BoundingBox): (ReferencedEnvelope, SimpleFeatureCollection) = {
    val file = new File(soilShapeFile + ".shp")

    val map = Map("url" -> file.toURI.toURL).asJava

    val dataStore = DataStoreFinder.getDataStore(map)
    val typeName = dataStore.getTypeNames()(0)
    val source = dataStore.getFeatureSource(typeName)

    // We need the given bounding box in the CRS of the given shape file
    val schema = source.getSchema
    val targetCRS = schema.getCoordinateReferenceSystem
    val referencedBoundingBox = new ReferencedEnvelope(boundingBox)
    val targetCRSBoundingBox = referencedBoundingBox.transform(targetCRS, true)

    // Keep only the shapes that are within the boundaries of our bounding box
    val filterFactory = CommonFactoryFinder.getFilterFactory2
    val filter = filterFactory.bbox(filterFactory.property(schema.getGeometryDescriptor.getLocalName), targetCRSBoundingBox)
    val collection = source.getFeatures(filter)

    println(bboxString(targetCRSBoundingBox))

    (targetCRSBoundingBox, collection)
  }

  // The area for which we would like to get the soil map
  private val boundingBox = new Envelope2D(
    DefaultGeographicCRS.WGS84,
    boundingBoxParams(0).toDouble,
    boundingBoxParams(1).toDouble,
    boundingBoxParams(2).toInt,
    boundingBoxParams(3).toInt)

  private def bboxString(boundingBox: BoundingBox) = {
    val minX = boundingBox.getMinX
    val minY = boundingBox.getMinY
    val maxX = boundingBox.getMaxX
    val maxY = boundingBox.getMaxY
    s"$minX,$minY,$maxX,$minY,$maxX,$maxY,$minX,$maxY,$minX,$minY"
  }

  private val startTime = new DateTime()
  println("Starting to generate pixel map: " + startTime.toString("dd/MM/YYYY hh:mm"))
  val output = generateMap(inputFilePath,
  "OMSCHRIJVI", boundingBox, (gridDimensions(0), gridDimensions(1)))

  Utils.writeOutputAsJSON(output, outputFilePath)

  println()
  println("Finished! Duration: " + (new DateTime().getMillis - startTime.getMillis)/1000 + " sec" )

}
