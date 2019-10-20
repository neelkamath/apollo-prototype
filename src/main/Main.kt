package com.neelkamath.apollo

import com.google.gson.Gson
import com.mongodb.client.MongoClients
import com.mongodb.client.model.Filters.eq
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import org.bson.Document
import java.io.File
import java.net.URI
import java.net.URLEncoder
import kotlin.math.round

private const val database =
    "mongodb://heroku_j2z4kg55:ll047cm6gqbklksejr9orastv1@ds337418.mlab.com:37418/heroku_j2z4kg55"
private val db = MongoClients.create("$database?retryWrites=false").getDatabase("heroku_j2z4kg55")
private const val token =
    "pk.eyJ1IjoibmVlbGthbWF0aCIsImEiOiJjanp0cHV4cjkwNGVyM21vYXVnYW5oYzU4In0.ioLnaLd2Awv1gMc4kI1FmA"

internal data class TagRequest(val tag: String)

internal data class TagResponse(val tags: List<String>)

private data class UserRequest(val id: String)

private data class UserResponse(val users: List<User>)

private data class User(
    val name: String,
    val id: String,
    val address: String,
    val longitude: Double,
    val latitude: Double,
    val tags: List<String>
)

private data class RoutesRequest(
    val address: String,
    val longitude: Double,
    val latitude: Double,
    val tags: List<String>
)

private data class RoutesResponse(val routes: List<RouteResponse>)

private data class RouteResponse(val route: Int, val ids: List<String>)

private data class GeocodeResponse(val features: List<Feature>)

private data class Feature(val geometry: Geometry)

/** The distance is most probably in meters. */
private data class MatrixResponse(val distances: List<List<Double>>)

/** The [coordinates] are the longitude and latitude respectively. */
private data class Geometry(val coordinates: List<Double>)

fun Application.main() {
    install(CallLogging)
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.AccessControlAllowHeaders)
        header(HttpHeaders.ContentType)
        header(HttpHeaders.AccessControlAllowOrigin)
        allowCredentials = true
        anyHost()
    }
    install(ContentNegotiation) { gson() }
    install(Routing) {
        route("tag") {
            get { call.respond(TagResponse(db.getCollection("tags").find().toList().map { it.getString("tag") })) }
            post {
                val collection = db.getCollection("tags")
                val document = Document("tag", call.receive<TagRequest>().tag)
                collection.insertOne(document)
                call.respond(HttpStatusCode.NoContent)
            }
            delete {
                db.getCollection("tags").deleteOne(eq("tag", call.receive<TagRequest>().tag))
                call.respond(HttpStatusCode.NoContent)
            }
        }
        route("user") {
            get { call.respond(UserResponse(getUsers())) }
            post {
                val document = with(call.receive<User>()) {
                    Document("name", name)
                        .append("id", id)
                        .append("address", address)
                        .append("tags", tags)
                        .append("longitude", longitude)
                        .append("latitude", latitude)
                }
                db.getCollection("users").insertOne(document)
                call.respond(HttpStatusCode.NoContent)
            }
            delete {
                db.getCollection("users").deleteOne(eq("id", call.receive<UserRequest>().id))
                call.respond(HttpStatusCode.NoContent)
            }
        }
        post("generate") {
            seedDb()
            val (destination, longitude, latitude, tags) = call.receive<RoutesRequest>()
            val destinationPoint = Geocode(longitude, latitude) // Using POI: val destinationPoint = getPoi(destination)
            val travellers = getUsers().filter { it.tags.intersect(tags).isNotEmpty() }.map {
                // Using POI: val dropPoint = getPoi(it.address, destinationPoint)
                val dropPoint = Geocode(it.longitude, it.latitude)

                Data(it.id, it.name, it.address, dropPoint, getDistance(destinationPoint, dropPoint))
            }
            val farthestDistance = travellers.map { it.distance }.max()!!
            val dataset = writeRoutes(
                Dataset(
                    Arrival(destination, destinationPoint, farthestDistance, radius = round(farthestDistance + 1)),
                    travellers
                )
            )
            val routes = dataset
                .routes
                .map { passenger ->
                    RouteResponse(passenger.route, dataset.routes.filter { it.route == passenger.route }.map { it.id })
                }
                .fold(mutableListOf<RouteResponse>()) { responses, response ->
                    if (response.route in responses.map { it.route }) return@fold responses
                    responses.apply { add(response) }
                }
            call.respond(RoutesResponse(routes))
        }
    }
}

private fun seedDb() {
    if (db.getCollection("tags").find().toList().isNotEmpty()) return
    seedTags()
    seedUsers()
}

private fun seedTags() = db.getCollection("tags").insertMany(
    listOf(
        Document("tag", "CSE"),
        Document("tag", "ME"),
        Document("tag", "ECE"),
        Document("tag", "1st Year"),
        Document("tag", "2nd Year"),
        Document("tag", "3rd Year"),
        Document("tag", "4th Year")
    )
)

private data class SeedData(val destinations: List<Destination>)

private data class Destination(
    val id: Int,
    val name: String,
    val address: String,
    val geocode: Geocode,
    val distance: Double
)

private fun seedUsers() = db.getCollection("users").insertMany(
    Gson()
        .fromJson(File("src/main/resources/seed.json").readText(), SeedData::class.java)
        .destinations
        .map { destination ->
            Document("name", destination.name)
                .append("id", destination.id.toString())
                .append("address", destination.address)
                .append("longitude", destination.geocode.longitude)
                .append("latitude", destination.geocode.latitude)
                .append("tags", db.getCollection("tags").find().toList().shuffled().map { it.getString("tag") }.take(1))
        }
)

private fun getUsers(): List<User> = db.getCollection("users").find().toList().map {
    User(
        it.getString("name"),
        it.getString("id"),
        it.getString("address"),
        it.getDouble("longitude"),
        it.getDouble("latitude"),
        it.getList("tags", String::class.java)
    )
}

/*
 We would've used the POI method (i.e., forward geocoding the human readable address), but free Map APIs don't do it
 well.
 */
/** The returned POI will be biased towards the [point] if one is present. */
private suspend fun getPoi(address: String, point: Geocode? = null): Geocode {
    val proximity = if (point == null) "" else with(point) { "&proximity=$longitude,$latitude" }
    val response = HttpClient().use {
        it.get<String>(
            URI(
                "https",
                "api.mapbox.com",
                "/geocoding/v5/mapbox.places/${URLEncoder.encode(address, "UTF-8")}.json",
                "limit=1&types=poi$proximity&access_token=$token",
                null
            ).toString()
        )
    }
    val body = Gson().fromJson(response, GeocodeResponse::class.java)
    return body.features[0].geometry.coordinates.let { Geocode(it[0], it[1]) }
}

private suspend fun getDistance(point1: Geocode, point2: Geocode): Double {
    val geocode1 = with(point1) { "$longitude,$latitude" }
    val geocode2 = with(point2) { "$longitude,$latitude" }
    return HttpClient { install(JsonFeature) }.use { client ->
        client.get<String>(
            URI(
                "https",
                "api.mapbox.com",
                "/directions-matrix/v1/mapbox/driving/$geocode1;$geocode2",
                "annotations=distance&access_token=$token",
                null
            ).toString()
        ).let { Gson().fromJson(it, MatrixResponse::class.java) }.distances[0].max()!!
    }
}