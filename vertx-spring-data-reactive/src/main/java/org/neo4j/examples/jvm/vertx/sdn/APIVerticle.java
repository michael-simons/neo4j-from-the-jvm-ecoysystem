package org.neo4j.examples.jvm.vertx.sdn;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.Status;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.healthchecks.HealthCheckHandler;
import io.vertx.reactivex.ext.healthchecks.HealthChecks;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;

import org.neo4j.driver.Driver;
import org.neo4j.examples.jvm.vertx.sdn.movies.MovieRepository;
import org.neo4j.examples.jvm.vertx.sdn.movies.PeopleRepository;
import org.neo4j.examples.jvm.vertx.sdn.movies.Person;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public final class APIVerticle extends AbstractVerticle {

	private final int port;

	private final Driver driver;
	private final MovieRepository movieService;
	private final PeopleRepository peopleRepository;

	public APIVerticle(@Value("${server.port:8080}") int port,
		Driver driver, MovieRepository movieService,
		PeopleRepository peopleRepository
	) {
		this.port = port;
		this.driver = driver;

		this.movieService = movieService;
		this.peopleRepository = peopleRepository;
	}

	@Override
	public void start(Promise<Void> startPromise) {

		var liveness = HealthChecks.create(vertx);
		liveness.register("alive", promise -> promise.complete(Status.OK()));

		var readiness = HealthChecks.create(vertx);
		readiness.register("alive", this::runHealthCheckQuery);
		var router = Router.router(vertx);

		router.get("/api/movies").handler(this::getAllMovies);
		router.post("/api/people").handler(BodyHandler.create()).handler(this::createNewPerson);
		router.get("/management/health/liveness").handler(HealthCheckHandler.createWithHealthChecks(liveness));
		router.get("/management/health/readiness").handler(HealthCheckHandler.createWithHealthChecks(readiness));

		vertx
			.createHttpServer()
			.requestHandler(router)
			.rxListen(port)
			.subscribe();
	}

	void getAllMovies(RoutingContext ctx) {

		var response = ctx.response().putHeader("content-type", "text/event-stream")
			.setStatusCode(200)
			.setChunked(true);

		this.movieService.findAll(Sort.by("title").ascending())
			.map(m -> Buffer.buffer("data: ").appendString(Json.encode(m)).appendString("\n\n"))
			.subscribe(response.toSubscriber());
	}

	void createNewPerson(RoutingContext ctx) {

		this.peopleRepository.save(ctx.getBody().toJsonObject().mapTo(Person.class))
			.map(Json::encode)
			.map(Buffer::buffer)
			.subscribe((b, e) -> ctx.response()
				.putHeader("content-type", "application/json")
				.setStatusCode(201)
				.end(b)
			);
	}

	void runHealthCheckQuery(io.vertx.reactivex.core.Promise<Status> statusPromise) {

		Flowable.using(
			driver::rxSession,
			s -> s.writeTransaction(tx -> tx.run("CALL dbms.components() YIELD name, edition WHERE name = 'Neo4j Kernel' RETURN edition").consume()),
			session -> Observable.fromPublisher(session.close()).subscribe()
		)
		.map((resultSummary) -> {
			var serverInfo = resultSummary.server();
			return Status.OK(new JsonObject().put("server", serverInfo.version() + "@" + serverInfo.address())); })
		.doOnError(e -> statusPromise.complete(Status.KO()))
		.subscribe(statusPromise::complete);
	}
}
