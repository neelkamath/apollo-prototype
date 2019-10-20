#!/usr/bin/env bash

./gradlew build
heroku container:push web -a apollo-prototype
heroku container:release web -a apollo-prototype
