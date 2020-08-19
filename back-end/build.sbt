name := "gwtf-backend"

version := "0.1"

scalaVersion := "2.12.8"

val geoToolsVersion = "21-RC"

val netcdfJavaVersion = "4.6.11"

libraryDependencies ++= Seq(
  "org.geotools" % "gt-coverage" % geoToolsVersion,
  "org.geotools" % "gt-netcdf" % geoToolsVersion,
  "org.geotools" % "gt-geotiff" % geoToolsVersion,
  "org.geotools" % "gt-process" % geoToolsVersion,
  "org.geotools" % "gt-process-raster" % geoToolsVersion,
  "org.geotools" % "gt-shapefile" % geoToolsVersion,
  //
  "com.lihaoyi" %% "ujson" % "0.7.1",
  "com.lihaoyi" %% "upickle" % "0.7.1",
  //
  "com.github.tototoshi" %% "scala-csv" % "1.3.5",
  //
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  //
  "edu.ucar" % "cdm" % netcdfJavaVersion,
  "edu.ucar" % "netcdf4" % netcdfJavaVersion
)

excludeDependencies ++= Seq(
  "org.slf4j" % "slf4j-log4j12",
  "log4j" % "log4j"
)

resolvers ++= Seq(
  "boundless" at "http://repo.boundlessgeo.com/main/",
  "imageio" at "http://maven.geo-solutions.it",
  "osgeo" at "http://download.osgeo.org/webdav/geotools/",
  "unidata-all" at "https://artifacts.unidata.ucar.edu/repository/unidata-all/"
)
