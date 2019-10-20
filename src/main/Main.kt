package com.neelkamath.apollo

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import kotlin.math.round

private const val database =
    "mongodb://heroku_j2z4kg55:ll047cm6gqbklksejr9orastv1@ds337418.mlab.com:37418/heroku_j2z4kg55"
private val db = MongoClients.create("$database?retryWrites=false").getDatabase("heroku_j2z4kg55")
private const val token =
    "pk.eyJ1IjoibmVlbGthbWF0aCIsImEiOiJjanp0cHV4cjkwNGVyM21vYXVnYW5oYzU4In0.ioLnaLd2Awv1gMc4kI1FmA"

private data class TagRequest(val tag: String)

private data class TagResponse(val tags: List<String>)

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

internal data class RoutesResponse(val routes: MutableList<RouteResponse>)

internal data class RouteResponse(val route: Int, val passengers: MutableList<Passenger>)

internal data class Passenger(val id: String, val longitude: Double, val latitude: Double, val proximity: Proximity)

internal enum class Proximity { CLOSE, NORMAL, FAR }

/** [distances] and [durations] are in meters and seconds respectively */
internal data class MatrixResponse(val distances: List<List<Double>>, val durations: List<List<Double>>)

private data class EtaRequest(
    @SerializedName("current_longitude")
    val currentLongitude: Double,
    @SerializedName("current_latitude")
    val currentLatitude: Double,
    @SerializedName("destination_longitude")
    val destinationLongitude: Double,
    @SerializedName("destination_latitude")
    val destinationLatitude: Double
)

private data class EtaResponse(val eta: Double)

fun Application.main() {
    (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger("org.mongodb.driver").level = Level.ERROR
    seedDb()
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
            val (destination, longitude, latitude, tags) = call.receive<RoutesRequest>()
            val destinationPoint = Geocode(longitude, latitude)
            val travellers = getUsers().filter { it.tags.intersect(tags).isNotEmpty() }.map {
                val dropPoint = Geocode(it.longitude, it.latitude)
                val distance = getDistance(destinationPoint, dropPoint)
                Data(it.id, it.name, it.address, dropPoint, distance)
            }
            val farthestDistance = travellers.map { it.distance }.max()!!
            call.respond(
                writeRoutes(
                    Dataset(
                        Arrival(destination, destinationPoint, farthestDistance, radius = round(farthestDistance + 1)),
                        travellers
                    ),
                    destinationPoint
                )
            )
        }
        post("eta") {
            val matrix = with(call.receive<EtaRequest>()) {
                getDistance(
                    Geocode(currentLongitude, currentLatitude), Geocode(destinationLongitude, destinationLatitude)
                )
            }
            call.respond(EtaResponse(matrix))
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

private fun geocode(point: Geocode) = with(point) { "$longitude,$latitude" }

internal suspend fun getDistance(point1: Geocode, point2: Geocode): Double =
    HttpClient { install(JsonFeature) }.use { client ->
        client.get<String>(
            URI(
                "https",
                "api.mapbox.com",
                "/directions-matrix/v1/mapbox/driving/${geocode(point1)};${geocode(point2)}",
                "annotations=distance&access_token=$token",
                null
            ).toString()
        ).let { Gson().fromJson(it, MatrixResponse::class.java) }.distances[0][1]
    }