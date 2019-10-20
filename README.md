# Apollo Prototype

![Bus](bus.jpg)

This prototype was built at the hackathon [InOut 6.0](https://hackinout.co/). This is a server which generates bus routes. My teammate [Ashik](https://github.com/ashikmeerankutty/inout-6.0) made the [frontend](http://abusive-zinc.surge.sh/). The project is named Apollo, after the Greek god from whom travelers would seek favor from.

This project is a hack for the _Future Mobility_ track. Transit for masses currently sucks. Since humans plan the routes, changing them is slow, expensive, and impossible to modify on a day-to-day basis. Since Apollo is a program, it can easily be used to generate the most optimal routes on demand. Usually, both students and administrators had unnecessary inefficiencies for days such as when only a particular part of the school wasn't using the bus (e.g., a day on which only middle school children have exams). Now, the institution doesn't need to waste fuel on partially empty vehicles, the drivers don't need to waste time figuring out who they have to wait to pick up or drop off on a particular day, and the passengers needn't take meaninglessly longer trips.

**How It Works**

1. Get every rider’s unique ID and address, and convert the human readable address to a geocode.
2. Find the greatest distance in km from the arrival point, and set that as the radius of a circle whose center is the arrival point. Make the radius slightly larger than the farthest destination since it’d be complicated to find and assign destinations present on the sector’s outline.
3. Use the parametric equation of a circle to create sectors for the buses to travel in.
4. Use a Distance Matrix API to create the route in each sector. For underpopulated buses, merge the right sector's passengers into its route. If a sector’s bus is overpopulated, add another bus to its sector. Do this until all the routes are filled. If there are any remaining seats in a couple of buses, it'd be negligible since a human planning the routes (even after Apollo has taken care of it) would only achieve suboptimal results.
5. Optimize the generated routes for practicality (e.g., move the passengers' residential locations from a narrow road to a main road).

**Implemented features**

Of course, programs aren't always as smart as humans. The exception is made when InOut participants are writing the program. Apollo will automatically give you the following benefits.
- Buses only go to the nearest stop on the main road, which saves the driver the hassle of figuring out how to drive as close as possible to the pick-up/drop-off point without significantly wasting the other passengers' time.
- Outliers (i.e., people who live ridiculously far away) will be specially highlighted to help aid in human intervention. Apollo is highly automated, but it is also very configurable.
- Passengers and their family members can track where the passenger is while the passenger is in the bus (i.e., GPS and ETA). Since buses are tracked, it will be known how long it’ll take for the bus to reach the school in the afternoon (in case the bus has to first travel to the school that day before it even starts its actual journey).

**Ideated, But Not Implemented**

Here are some features we could've implemented if we had more than 24 hours to work on the project.
- Since it's common for institutes to rent vehicles, they'll have the ability to specify the transport sizes they have available. Regardless of what you choose, you'll be able to see both the most optimal as well as the best suited methods.
- If there are passengers scattered around more than 90 degrees around the institute, then the generator will divide the total number of degrees (i.e., 360 degrees) by 90 degrees. This will be the bare minimum number of routes to generate. If this did'nt happen, buses would take up taking excessively long journeys (just imagine a single bus starting and stopping at the same building only once, but picking up half the population of the institute which are scattered in different directions).
- If human intervention was used to move manually place a certain person's area of residence, it would be saved so that when routes are regenerated, the moved points are'nt lost.
- Time should be factored in as well. Every bus should start from a decent time (i.e., not one from an hour before all the others). If there are two points seemingly close to each other, but don’t have roads connecting them, move the point to the next sector’s route. You’ll know whether to do this if the amount of time is disproportionate to the distance between the two points.

## Installation

You can try out the HTTP API using the development server `https://apollo.herokuapp.com`.

## Usage

### Deployment

1. `chmod +x release.sh`
1. `./release.sh`

### Server

- Development: `./gradlew -t assemble & ./gradlew run`
- Production: 
    1. `docker build -t apollo  .`
    1. To serve at `http://localhost:80`, run `docker run --rm -p 80:80 apollo`. You can change the port by setting the `PORT` environment variable (e.g., `docker run --rm -e PORT=8080 -p 8080:8080 apollo`). The container `EXPOSE`s port `80`.
- Mock: `prism mock openapi.yaml`
  
### Documentation  
    
- Development: `redoc-cli serve openapi.yaml -w`
- Production: `redoc-cli bundle openapi.yaml -o apollo.html --title 'Apollo'`
- Test: `spectral lint openapi.yaml`

## License

This project is under the [MIT License](LICENSE).