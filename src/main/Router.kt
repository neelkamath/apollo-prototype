package com.neelkamath.apollo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Pass the institute's [geocode] */
internal suspend fun writeRoutes(dataset: Dataset, geocode: Geocode): RoutesResponse {
    val routes = RoutesDataset(mutableListOf())
    val radius = with(dataset.arrival) { calculateDistance(geocode, calculateGeocode(geocode, radius)) }
    val angle = 1
    var route = 1
    for (degrees in 0 until 360 step angle) {
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
                    for (coordinate in coordinates) {
                        routes.routes.add(Route(route, coordinate.key.geocode, coordinate.key.id))
                    }
                    route++
                }
            }
    }
    val response = transformRoutes(routes, geocode)
    merge(response)
    return response
}

/** Transforms the [response] in place. */
private fun merge(response: RoutesResponse) {
    val capacity = 4 // Hardcode bus capacity to 4 due to lack of data
    var mergeComplete = false
    while (response.routes.size >= 4 && !mergeComplete) {
        mergeComplete = true
        for (index in response.routes.indices) {
            val getIndex = { i: Int -> i % response.routes.size }
            val passengers = response.routes[getIndex(index)].passengers
            val nextPassengers = response.routes[getIndex(index + 1)].passengers
            if (passengers.size + nextPassengers.size <= capacity) {
                mergeComplete = false
                passengers.addAll(nextPassengers)
                response.routes.removeAt(getIndex(index + 1))
            }
        }
    }
}

/** Pass the [geocode] of the institute controlling the [dataset]. */
private suspend fun transformRoutes(dataset: RoutesDataset, geocode: Geocode): RoutesResponse = RoutesResponse(
    dataset.routes
        .map { passenger ->
            RouteResponse(
                passenger.route,
                dataset
                    .routes
                    .filter { it.route == passenger.route }
                    .map {
                        val distance = getMatrix(geocode, it.geocode, Matrix.Distance)
                        val proximity = when {
                            distance < 10 -> Proximity.CLOSE
                            distance < 20 -> Proximity.NORMAL
                            else -> Proximity.FAR
                        }
                        Passenger(it.id, it.geocode.longitude, it.geocode.latitude, proximity)
                    }
                    .toMutableList()
            )
        }
        .fold(mutableListOf()) { responses, response ->
            if (response.route in responses.map { it.route }) return@fold responses
            responses.apply { add(response) }
        }
)

private data class RoutesDataset(val routes: MutableList<Route>)

private data class Route(val route: Int, val geocode: Geocode, val id: String)

private fun calculateDistance(point1: Geocode, point2: Geocode): Double {
    val abscissa = point1.longitude - point2.longitude
    val ordinate = point1.latitude - point2.latitude
    return sqrt((abscissa * abscissa) + (ordinate * ordinate))
}

private fun getOrigin() = Geocode(.0, .0)

internal data class Geocode(val longitude: Double, val latitude: Double)

internal data class Dataset(val arrival: Arrival, val destinations: List<Data>)

/** [farthestDestination] are [radius] are in kilometers. */
internal data class Arrival(
    val name: String,
    val geocode: Geocode,
    val farthestDestination: Double,
    val radius: Double
)

/** [distance] is in km. */
internal data class Data(
    val id: String,
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