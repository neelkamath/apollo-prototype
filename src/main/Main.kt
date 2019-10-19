package com.neelkamath.apollo

import com.mongodb.client.MongoClients
import com.mongodb.client.model.Filters.eq
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import org.bson.Document

internal data class TagRequest(val tag: String)

internal data class TagResponse(val tags: List<String>)

private data class UserRequest(val id: String)

private data class UserResponse(val users: List<User>)

private data class User(val name: String, val id: String, val address: String, val tags: List<String>)

private data class RoutesRequest(val tags: List<String>)

private data class RoutesResponse(val routes: List<Route>)

fun Application.main() {
    val url = "mongodb://heroku_j2z4kg55:ll047cm6gqbklksejr9orastv1@ds337418.mlab.com:37418/heroku_j2z4kg55"
    val db = MongoClients.create("$url?retryWrites=false").getDatabase("heroku_j2z4kg55")
    install(CallLogging)
    install(ContentNegotiation) { gson() }
    install(Routing) {
        route("tag") {
            get { call.respond(TagResponse(db.getCollection("tags").find().toList().map { it.getString("tag") })) }
            post {
                val collection = db.getCollection("tags")
                val document = Document("tag", call.receive<TagRequest>().tag)
                collection.insertOne(document)
            }
            delete { db.getCollection("tags").deleteOne(eq("tag", call.receive<TagRequest>().tag)) }
        }
        route("user") {
            get {
                val users = db.getCollection("users").find().toList().map {
                    User(
                        it.getString("name"),
                        it.getString("id"),
                        it.getString("address"),
                        it.getList("tags", String::class.java)
                    )
                }
                call.respond(UserResponse(users))
            }
            post {
                val document = with(call.receive<User>()) {
                    Document("name", name).append("id", id).append("address", address).append("tags", tags)
                }
                db.getCollection("users").insertOne(document)
            }
            delete { db.getCollection("users").deleteOne(eq("id", call.receive<UserRequest>().id)) }
        }
        post("routes") { call.respond(RoutesResponse(listOf())) }
    }
}