# Analyse: Spring Boot Starter — Required vs. Optional Dependencies
Op basis van de aangeleverde assessment.md zou ik een Spring Boot 4.x-project met Java 21+ en Maven opzetten, waarbij we onderscheid maken tussen:
1. Required dependencies — noodzakelijk om aan de functionele en niet-functionele eisen te voldoen.
2. Recommended dependencies — niet strikt verplicht, maar sterk aanbevolen voor een onderhoudbare implementatie.
3. Optional dependencies — uitbreidingen voor documentatie, monitoring, persistence en containerization.

> De opdracht vraagt specifiek om een REST API, microservices, service-to-servicecommunicatie, token-based security (OAuth/JWT) en unit tests. Een persistente database is niet verplicht; een in-memory datastore volstaat.


| Dependency | Spring Boot Starter / Maven artifact | Waarom? |
|---|---|---|
| Spring Web MVC | `spring-boot-starter-webmvc` | REST controllers, endpoints, HTTP requests en JSON responses. |
| Spring Security | `spring-boot-starter-security` | Authenticatie, autorisatie en beveiliging van endpoints. |
| OAuth2 Resource Server | `spring-boot-starter-oauth2-resource-server` | JWT-validatie bij iedere API-call. |
| Spring Boot Test | `spring-boot-starter-test` | Unit tests, integratietests en Spring-testondersteuning. |

De opdracht vereist REST-endpoints voor:
- Customer CRUD
- Vehicle CRUD
- Interest rates
- Lease rate calculation
Spring MVC levert hiervoor de controllers, HTTP-mapping, requestverwerking en JSON-serialisatie.

Spring Security + OAuth2 Resource Server
De opdracht stelt twee beveiligingseisen:
- Gebruikers moeten ingelogd zijn voordat zij de API kunnen gebruiken.
- De identiteit moet bij iedere API-call worden gevalideerd.
  Een OAuth2 Resource Server met JWT is hiervoor geschikt. De API valideert het bearer token bij iedere beveiligde request.

De opdracht vereist unit tests. Spring Boot Test biedt de infrastructuur voor Spring-gerelateerde tests.


## Spring Validation
Voor de Customer API is validatie bijzonder relevant.
gebruik Java records voor DTO's en Bean Validation voor inputvalidatie.


## Spring Boot Actuator
Actuator biedt onder andere:
GET /actuator/health
GET /actuator/info

Dit is nuttig voor microservices en container deployments.

## Service-to-servicecommunicatie
De assessment stelt expliciet:
It should contain service-2-service communication.

Daarom moet de applicatie minimaal twee afzonderlijke services bevatten die met elkaar communiceren.
Bijvoorbeeld:


## Mijn keuze: RestClient
Voor deze assessment zou ik RestClient gebruiken.
Redenen:
- Native Spring Framework.
- Synchrone communicatie is voldoende.
- Geen noodzaak voor een reactive stack.
- Minder dependencies dan Spring Cloud OpenFeign.
- Goed te testen met een gemockte HTTP-server.


## Persistence: H2 of helemaal geen database?
De assessment staat een in-memory datastore toe. 
Je hebt dus twee mogelijkheden.
Optie A — Java in-memory repositories
Optie B — Spring Data JPA + H2

Voor deze assessment zou ik optie A kiezen want:
Geen database dependencies.
Zeer eenvoudige configuratie.
Snel te implementeren.
maar Gegevens verdwijnen bij een restart.

## Architectuur
Ik zou de applicatie opdelen in twee Spring Boot-services.
Service 1: lease-api
Verantwoordelijkheden:
- Customer CRUD.
- Vehicle CRUD.
- Leaseberekening.
- REST-endpoints.
- JWT-validatie.
  Service 2: interest-rate-service
  Verantwoordelijkheden:
- Rentetarieven beheren.
- Geldig rentetarief op basis van ingangsdatum bepalen.
- Rentetarieven beschikbaar stellen via REST.
  De lease-api vraagt het toepasselijke rentetarief op bij de interest-rate-service.
  Daarmee demonstreer je daadwerkelijk service-to-servicecommunicatie, zonder onnodig veel microservices te introduceren.