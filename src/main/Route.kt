package com.neelkamath.apollo

import com.google.gson.Gson
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun main() {
    val path = "src/main/resources/routes.json"
    File(path).writeText("""{"routes": []}""")
    val dataset = Gson().fromJson(File("src/main/resources/data.json").readText(), Dataset::class.java)
    val radius = with(dataset.arrival) { calculateDistance(geocode, calculateGeocode(geocode, radius)) }
    val angle = 90
    var route = 1
    for (degrees in 0..360 step angle) {
        val sectorStart = calculatePoint(getOrigin(), radius, convertToRadians(degrees))
        val sectorEnd = calculatePoint(getOrigin(), radius, convertToRadians(degrees + angle))
        dataset.destinations
            .associateWith {
                with(dataset.arrival.geocode) {
                    Geocode(it.geocode.longitude - longitude, it.geocode.latitude - latitude)
                }
            }
            .filter { isInsideSector(it.value, getOrigin(), sectorStart, sectorEnd, radius * radius) }
            .also { coordinates ->
                if (coordinates.isNotEmpty()) {
                    val routesDataset = Gson().fromJson(File(path).readText(), RoutesDataset::class.java)
                    for (coordinate in coordinates) routesDataset.routes.add(Route(route, coordinate.key.geocode))
                    File(path).writeText(Gson().toJson(routesDataset))
                    route++
                }
            }
    }
}

private data class RoutesDataset(val routes: MutableList<Route>)

private data class Route(val route: Int, val geocode: Geocode)

private fun calculateDistance(point1: Geocode, point2: Geocode): Double {
    val abscissa = point1.longitude - point2.longitude
    val ordinate = point1.latitude - point2.latitude
    return sqrt((abscissa * abscissa) + (ordinate * ordinate))
}

private fun getOrigin() = Geocode(.0, .0)

private data class Geocode(val longitude: Double, val latitude: Double)

private data class Dataset(val arrival: Arrival, val destinations: List<Data>)

/** [farthestDestination] are [radius] are in kilometers. */
private data class Arrival(
    val name: String,
    val geocode: Geocode,
    val farthestDestination: Double,
    val radius: Double
)

/** [distance] is in km. */
private data class Data(
    val id: Int,
    val name: String,
    val address: String,
    val geocode: Geocode,
    val distance: Double
)

/** Calculates the new [Geocode] at a [distance] (in km) from the original [Geocode]. */
private fun calculateGeocode(Geocode: Geocode, distance: Double): Geocode {
    val (longitude, latitude) = Geocode
    val longitudeOffset = distance / (111.32 * cos(latitude))
    val latitudeOffset = distance / 110.574
    return Geocode(longitude + longitudeOffset, latitude + latitudeOffset)
}

/**
 * Calculates the [geocode] of a point on a circle's circumference at an [angle] (in radians).
 *
 * The [geocode] are the origin, and you must specify the circle's [radius].
 */
private fun calculatePoint(geocode: Geocode, radius: Double, angle: Double): Geocode =
    Geocode(geocode.longitude + (radius * cos(angle)), geocode.latitude + (radius * sin(angle)))

private fun convertToRadians(degrees: Int): Double = degrees * PI / 180

private fun isInsideSector(
    point: Geocode,
    center: Geocode,
    sectorStart: Geocode,
    sectorEnd: Geocode,
    radiusSquared: Double
): Boolean {
    val relPoint = Geocode(point.longitude - center.longitude, point.latitude - center.latitude)
    return !areClockwise(sectorStart, relPoint) && areClockwise(sectorEnd, relPoint) && isWithinRadius(
        relPoint,
        radiusSquared
    )
}

private fun areClockwise(vector1: Geocode, vector2: Geocode) =
    -vector1.longitude * vector2.latitude + vector1.latitude * vector2.longitude > 0

private fun isWithinRadius(vector: Geocode, radiusSquared: Double) =
    (vector.longitude * vector.longitude) + (vector.latitude * vector.latitude) <= radiusSquared