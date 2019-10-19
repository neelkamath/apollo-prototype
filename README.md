# Apollo Prototype

This is a prototype server built at InOut 6.0. It generates bus routes based on who is in the bus.

## Installation

You can try out the HTTP API using the development server `https://apollo.herokuapp.com`.

## Usage

### Server

- Development: `./gradlew -t assemble & ./gradlew run`
- Test: `./gradlew test`
- Production: 
    1. `docker build -t apollo  .`
    1. To serve at `http://localhost:80`, run `docker run --rm -p 80:80 apollo`. You can change the port by setting the `PORT` environment variable (e.g., `docker run --rm -e PORT=8080 -p 8080:8080 apollo`). The container `EXPOSE`s port `80`.
- Mock: `prism mock openapi.yaml`
  
### Documentation  
    
- Development: `redoc-cli serve openapi.yaml -w`
- Production: `redoc-cli bundle openapi.yaml -o redoc-static.html --title 'Apollo'`
- Test: `spectral lint openapi.yaml`

## License

This project is under the [MIT License](LICENSE).