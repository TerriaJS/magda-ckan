package au.csiro.data61.magda.model

import java.time.Duration
import java.time.Instant
import spray.json._
import au.csiro.data61.magda.model.temporal._
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.MediaTypes
import com.monsanto.labs.mwundo.GeoJson._
import com.monsanto.labs.mwundo.GeoJsonFormats._
import java.util.regex.Pattern

package misc {
  sealed trait FacetType {
    def id: String
  }
  object FacetType {
    val all = Seq(Publisher, Year, Format)

    private val idToFacet = all.groupBy(_.id).mapValues(_.head)

    def fromId(id: String): Option[FacetType] = idToFacet get id
  }
  case object Publisher extends FacetType {
    override def id = "publisher"
  }
  case object Year extends FacetType {
    override def id = "year"
  }
  case object Format extends FacetType {
    override def id = "format"
  }

  case class FacetSearchResult(
    hitCount: Int,
    options: Seq[FacetOption])

  case class Facet(
    id: FacetType,
    options: Seq[FacetOption])

  case class FacetOption(
    value: String,
    hitCount: Option[Int] = None,
    matched: Option[Boolean] = None)

  case class DataSet(
      identifier: String,
      catalog: String,
      title: Option[String] = None,
      description: Option[String] = None,
      issued: Option[Instant] = None,
      modified: Option[Instant] = None,
      language: Option[String] = None,
      publisher: Option[Agent] = None,
      accrualPeriodicity: Option[Periodicity] = None,
      spatial: Option[Location] = None,
      temporal: Option[PeriodOfTime] = None,
      theme: Seq[String] = List(),
      keyword: Seq[String] = List(),
      contactPoint: Option[Agent] = None,
      distributions: Option[Seq[Distribution]] = None,
      landingPage: Option[String] = None,
      // TODO: Investigate making this only exist in ElasticSearch
      years: Option[List[String]] = None) {

    def uniqueId: String = java.net.URLEncoder.encode(catalog + "/" + identifier, "UTF-8")
  }

  case class Agent(
    name: Option[String] = None,
    homePage: Option[String] = None,
    email: Option[String] = None,
    extraFields: Map[String, String] = Map())

  case class Location(
    text: String,
    geoJson: Option[Geometry] = None)

  object Location {
    val geoJsonPattern = "\\{\"type\": \".+\",.*\\}".r
    val emptyPolygonPattern = "POLYGON \\(\\(0 0, 0 0, 0 0, 0 0\\)\\)".r
    val polygonPattern = "POLYGON \\(\\(((-?\\d+ -?\\d+\\,?\\s?)+)\\)\\)".r

    def apply(string: String): Location = {
      Location(string, string match {
        case geoJsonPattern() => {
          Some(Protocols.GeometryFormat.read(string.parseJson))
        }
        case emptyPolygonPattern() => None
        case polygonPattern(polygonCoords, _) =>
          val coords = polygonCoords.split(",")
            .map { stringCoords =>
              try {
                val Array(x, y) = stringCoords.trim.split("\\s").map(_.toDouble)
                Coordinate(x, y)
              } catch {
                case e => println(stringCoords); throw e
              }
            }.toSeq

          Some(Polygon(Seq(coords)))
        case _ => None
      })
    }
  }

  case class Distribution(
    title: String,
    description: Option[String] = None,
    issued: Option[Instant] = None,
    modified: Option[Instant] = None,
    license: Option[License] = None,
    rights: Option[License] = None,
    accessURL: Option[String] = None,
    downloadURL: Option[String] = None,
    byteSize: Option[Int] = None,
    mediaType: Option[MediaType] = None,
    format: Option[String] = None)

  object Distribution {
    private val extensionRegex = new scala.util.matching.Regex("\\.([^./]+)$", "extension")
    // TODO: Push into config
    private val formatToMimeType: Map[String, MediaType] = Map(
      "GeoJSON" -> MediaTypes.`application/json`,
      "KML" -> MediaTypes.`application/vnd.google-earth.kml+xml`,
      "CSV" -> MediaTypes.`text/csv`,
      "JSON" -> MediaTypes.`application/json`,
      "SHP" -> MediaTypes.`application/octet-stream`
    )

    private val caseInsensitiveFormatToMimeType = formatToMimeType.map {
      case (key: String, mediaType: MediaType) =>
        Map(
          key.toUpperCase -> mediaType,
          key.toLowerCase -> mediaType,
          key -> mediaType
        )
    }.reduce(_ ++ _)

    private val urlToFormat = Map(
      ".*.geojson^" -> "GeoJSON",
      ".*?.*service=wms.*" -> "WMS",
      ".*?.*service=wfs.*" -> "WFS",
      ".*.(shp|shz|dbf)^" -> "SHP"
    )

    private def mediaTypeFromMimeType(mimeType: String): Option[MediaType] = MediaType.parse(mimeType) match {
      case Right(mediaType) => Some(mediaType)
      case Left(_)          => None
    }

    def mediaTypeFromFormat(format: String): Option[MediaType] = caseInsensitiveFormatToMimeType.get(format)
      .orElse(MediaTypes.forExtensionOption(format.toLowerCase()))

    private def mediaTypeFromExtension(url: String): Option[MediaType] = extensionRegex
      .findFirstIn(url)
      .flatMap { case extensionRegex(extension) => MediaTypes.forExtensionOption(extension) }

    def parseMediaType(mimeType: Option[String], rawFormat: Option[String], url: Option[String]) = mimeType
      .flatMap(mediaTypeFromMimeType(_))
      .orElse(rawFormat flatMap (mediaTypeFromFormat(_)))
      .orElse(url flatMap (mediaTypeFromExtension(_)))

    def formatFromUrl(url: String) = urlToFormat
      .view
      .filter { case (regex, _) => regex.matches(url) }
      .map { case (_, format) => format }
      .headOption

    def parseFormat(rawFormat: Option[String], url: Option[String], parsedMediaType: Option[MediaType]): Option[String] = rawFormat
      .orElse(url.flatMap(Distribution.formatFromUrl(_)))
      .orElse(parsedMediaType.map(_.subType))

  }

  case class License(name: String, url: String)

  trait Protocols extends DefaultJsonProtocol with temporal.Protocols {

    implicit val licenseFormat = jsonFormat2(License.apply)
    implicit object FacetTypeFormat extends JsonFormat[FacetType] {
      override def write(facetType: FacetType): JsString = JsString.apply(facetType.id)
      override def read(json: JsValue): FacetType = FacetType.fromId(json.convertTo[String]).get
    }
    implicit object MediaTypeFormat extends JsonFormat[MediaType] {
      override def write(mediaType: MediaType): JsString = JsString.apply(mediaType.value)
      override def read(json: JsValue): MediaType = MediaType.parse(json.convertTo[String]).right.get
    }
    implicit object GeometryFormat extends JsonFormat[Geometry] {
      override def write(geometry: Geometry): JsValue = geometry match {
        case point: Point           => PointFormat.write(point)
        case point: MultiPoint      => MultiPointFormat.write(point)
        case point: LineString      => LineStringFormat.write(point)
        case point: MultiLineString => MultiLineStringFormat.write(point)
        case point: Polygon         => PolygonFormat.write(point)
        case point: MultiPolygon    => MultiPolygonFormat.write(point)
      }
      override def read(json: JsValue): Geometry = json match {
        case JsObject(jsObj) => jsObj.get("type") match {
          case Some(JsString("Point"))           => PointFormat.read(json)
          case Some(JsString("MultiPoint"))      => MultiPointFormat.read(json)
          case Some(JsString("LineString"))      => LineStringFormat.read(json)
          case Some(JsString("MultiLineString")) => MultiLineStringFormat.read(json)
          case Some(JsString("Polygon"))         => PolygonFormat.read(json)
          case Some(JsString("MultiPolygon"))    => MultiPolygonFormat.read(json)
          case _                                 => deserializationError(s"'$json' is not a valid geojson shape")
        }
        case _ => deserializationError(s"'$json' is not a valid geojson shape")
      }
    }
    implicit val distributionFormat = jsonFormat11(Distribution.apply)
    implicit val locationFormat = jsonFormat2(Location.apply)
    implicit val agentFormat = jsonFormat4(Agent.apply)
    implicit val dataSetFormat = jsonFormat17(DataSet.apply)
    implicit val facetOptionFormat = jsonFormat3(FacetOption.apply)
    implicit val facetFormat = jsonFormat2(Facet.apply)
    implicit val facetSearchResultFormat = jsonFormat2(FacetSearchResult.apply)
  }

  object Protocols extends Protocols {

  }
}