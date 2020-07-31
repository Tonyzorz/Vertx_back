package io.vertx.blog.first;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class MongoVerticle extends AbstractVerticle {

	private static Logger logger = Logger.getLogger(MongoVerticle.class);

	private MongoClient mongo;

	private EventBus eb;

	@Override
	public void start(Future<Void> fut) {
		mongo = MongoClient.createShared(vertx, config(), "MyPoolName");
		
		eb = vertx.eventBus();
		
		eb.consumer("vertx.mong"
				+ "odb", message ->{
			getAll(message);
		});
		
	}

	public void stop() {
		mongo.close();
	}

	private void getAll(Message<Object> message) {
	    mongo.find("Whisky", new JsonObject(), results -> {
	      List<JsonObject> objects = results.result();
	      List<Whisky> whiskies = objects.stream().map(Whisky::new).collect(Collectors.toList());
//	      routingContext.response()
//	          .putHeader("content-type", "application/json; charset=utf-8")
//	          .end(Json.encodePrettily(whiskies));
	      System.out.println("MongoVerticle value " + whiskies.toString());
	      message.reply(whiskies.toString());
	    });
	}
	
	private void createSomeData(Handler<AsyncResult<Void>> next, Future<Void> fut) {
		Whisky bowmore = new Whisky("Bowmore 15 Years Laimrig", "Scotland, Islay");
		Whisky talisker = new Whisky("Talisker 57° North", "Scotland, Island");
		JSONParser parser = new JSONParser();
		JsonObject jsonObject;
		try {
			jsonObject = (JsonObject) parser.parse(talisker.toString());
			System.out.println(bowmore.toString());
			// Do we have data in the collection ?
			mongo.count("whiskies-it", new JsonObject(), count -> {
				if (count.succeeded()) {
					if (count.result() == 0) {
						// no whiskies, insert data
						mongo.insert("whiskies-it", jsonObject, ar -> {
							if (ar.failed()) {
								fut.fail(ar.cause());
							} else {
								mongo.insert("whiskies-it", jsonObject, ar2 -> {
									if (ar2.failed()) {
										fut.failed();
									} else {
										next.handle(Future.<Void>succeededFuture());
									}
								});
							}
						});
					} else {
						next.handle(Future.<Void>succeededFuture());
					}
				} else {
					// report the error
					fut.fail(count.cause());
				}
			});
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
