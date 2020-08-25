package io.vertx.scs;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

public class SCSRoute extends AbstractVerticle {
	
	
	private static Logger logger = Logger.getLogger(SCSRoute.class);
	private SharedData sharedData;
	private WebClient webClient;
	
	private int port;
	private String httpIP;
	@Override
	public void start(Future<Void> fut) throws Exception {

		logger.info("started SCSRoute");
		System.out.println("[SCSRoute] started");

		sharedData = vertx.sharedData();

		port = config().getInteger("http.port");
		httpIP = config().getString("http.ip");
		// routing 해주기
		startWebApp((http) -> completeStartup(http, fut));

	}

	private void startWebApp(Handler<AsyncResult<HttpServer>> next) {

		Router router = Router.router(vertx);

		Set<String> allowedHeaders = new HashSet<>();
		allowedHeaders.add("x-requested-with");
		allowedHeaders.add("Access-Control-Allow-Origin");
		allowedHeaders.add("origin");
		allowedHeaders.add("Content-Type");
		allowedHeaders.add("accept");
		allowedHeaders.add("X-PINGARUNER");

		Set<HttpMethod> allowedMethods = new HashSet<>();
		allowedMethods.add(HttpMethod.GET);
		allowedMethods.add(HttpMethod.POST);
		allowedMethods.add(HttpMethod.DELETE);
		allowedMethods.add(HttpMethod.PUT);

		router.route().handler(CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods));

		router.route("/*").handler(BodyHandler.create());
		router.get("/").handler(this::default_main);

		router.post("/stations/list/:current_user_id").handler(this::retrieve_station_list);
		router.post("/stations/create/:current_user_id").handler(this::create_station);
		router.get("/stations/details/:station_id/:company_id/:current_user_id").handler(this::retrieve_station_info);
		router.post("/stations/update/:station_id/:user_id").handler(this::update_station);
		router.post("/stations/delete/:station_id/:company_id/:user_id").handler(this::delete_station);
		router.post("/stations/chk_input_data/:company_id").handler(this::chk_input_data_hp);
		
		router.post("/stations/vertxSync").handler(this::vertxSync);
		router.post("/stations/checkNInsert").handler(this::checkNInsert);
		
		router.post("/stations/mission_common/:mission_id").handler(this::mission_common_hp);
		router.post("/stations/mission_into_station/:company_id").handler(this::mission_into_station_hp);
		router.post("/stations/insert_mission/:mission_id").handler(this::insert_mission_hp);
		router.post("/stations/update_mission/:mission_id").handler(this::update_mission_hp);

		// Create the HTTP server and pass the "accept" method to the request handler.
		vertx.createHttpServer().requestHandler(router::accept).listen(
			// Retrieve the port from the configuration,
			// default to 8090.
			config().getInteger("scsLogic.port", 8089), next::handle);
	}

	private void default_main(RoutingContext routingContext) {

		HttpServerResponse response = routingContext.response();
		response.putHeader("content-type", "text/html").sendFile("webapp/WEB-INF/views/index.html").end();

	}

	private void vertxSync(RoutingContext routingContext) {
		
		JsonObject jsonObject = routingContext.getBodyAsJson();
		
		//Future<JsonObject> step1 = retrieve_whisky(jsonObject);
		
		//works but how to handle error? 
		retrieve_whisky(jsonObject).compose(ar -> {
			
			System.out.println("retrieve_whisky :::: " + ar);
			
			return insert_whisky(ar);
			
		}).compose(ar -> {
			
			System.out.println("insert_whisky :::: " + ar);

			Promise<JsonObject> promise = Promise.promise();
			
			routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
			routingContext.response().end(ar.toString());
			
			return promise.future();
			
		}).onFailure(ar -> {
			
			routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
			routingContext.response().end(ar.getMessage());
			
		});
		
	}
	
	String tempName = "";
	private void checkNInsert(RoutingContext routingContext) {
		
		JsonObject jsonObject = routingContext.getBodyAsJson();
		
		//Future<JsonObject> step1 = retrieve_whisky(jsonObject);
		
		//works but how to handle error? 
		tempName = jsonObject.getString("name");
		
		retrieve_check(jsonObject).compose(ar -> {
			
			System.out.println("retrieve_whisky :::: " + ar);
			
			JsonObject txt = new JsonObject();
			txt.put("name", tempName);
			return insert_whisky(txt);
			
		}).compose(ar -> {
			
			System.out.println("insert_whisky :::: " + ar);
			
			Promise<JsonObject> promise = Promise.promise();
			
			routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
			routingContext.response().end(ar.toString());
			
			return promise.future();
			
		}).onFailure(ar -> {
			
			routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
			routingContext.response().end(ar.getMessage());
			
		});
		
	}
	
	private Future<JsonObject> retrieve_whisky(JsonObject jsonObject) {
		
		logger.info("entered retrieve_station_list");
		
		WebClient client = WebClient.create(vertx);
		Promise<JsonObject> promise = Promise.promise();
		
		System.out.println("about to send :: " + jsonObject);
		client.post(port, httpIP, "/query/1000")
			.sendJsonObject(jsonObject, ar -> {
				if (ar.succeeded()) {
					
					logger.info("response from post " + httpIP + ":" + port + "/query/50000 === success" );
					
					JSONParser parser = new JSONParser();
					
					try {
						
						
						JsonArray array = ar.result().bodyAsJsonArray();
						String str = array.getValue(0).toString();
						JsonObject afterEdit = new JsonObject(str);
						
						if(afterEdit.containsKey("code")) {
							
							promise.fail(ar.result().bodyAsString());

						} else {
							
							promise.complete(afterEdit);
						}
						
						
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
	//					promise.fail("fail");
					}
					
				} else {
					
					logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );
					promise.fail("failure from retrieve_whisky");
				}
			});
		return promise.future();
		
	}
	
	private Future<JsonObject> retrieve_check(JsonObject jsonObject) {
		
		logger.info("entered retrieve_station_list");
		
		WebClient client = WebClient.create(vertx);
		Promise<JsonObject> promise = Promise.promise();
		
		System.out.println("about to send :: " + jsonObject);
		client.post(port, httpIP, "/query/7500")
		.sendJsonObject(jsonObject, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/query/50000 === success" );
				
				JSONParser parser = new JSONParser();
				
				try {
					
					JsonArray array = ar.result().bodyAsJsonArray();
					String str = array.getValue(0).toString();
					JsonObject afterEdit = new JsonObject(str);
					
//					if(afterEdit.containsKey("code")) {
//						
//						promise.fail(ar.result().bodyAsString());
//						
//					} else {
//						
//						promise.complete(afterEdit);
//					}
//					
					//코드가 있다는 뜻은(현재는 코드, 나중엔 다른 결과값으로 비교 가능) 조회 결과가 없다 란 뜻으로   
					if(afterEdit.containsKey("code")) {
						
						System.out.println("DATA DOES NOT EXIST");
						promise.complete(afterEdit);
						
					}else {
						
						System.err.println("DATA EXISTS~~~~~~~~~");
						String name = afterEdit.getString("name");
						tempName = name + "1";
						name = tempName;
						afterEdit.put("name", name);
						retrieve_check(afterEdit);
					}
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					//					promise.fail("fail");
				}
				
			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );
				promise.fail("failure from retrieve_whisky");
			}
		});
		return promise.future();
		
	}
	
	private Future<JsonObject> insert_whisky(JsonObject jsonObject) {
		
		logger.info("entered retrieve_station_list");
		
		WebClient client = WebClient.create(vertx);
//		Promise<Void> promise = Promise.promise();
		
		Future<JsonObject> future = Future.future();
		System.out.println("about to send :: " + jsonObject);
		client.post(port, httpIP, "/query/1002")
		.sendJsonObject(jsonObject, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/query/50000 === success" );
				
				JSONParser parser = new JSONParser();
				
				try {
					
					JsonArray array = ar.result().bodyAsJsonArray();
					String str = array.getValue(0).toString();
					JsonObject afterEdit = new JsonObject(str);
					
					future.complete(afterEdit);
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					//					promise.fail("fail");
				}
				
			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );
				
			}
		});
		return future;
		
	}
	
	private void retrieve_station_list(RoutingContext routingContext) {
		
		logger.info("entered retrieve_station_list");

		WebClient client = WebClient.create(vertx);
		
		JsonObject test = routingContext.getBodyAsJson();
		
		logger.info("requesting to post " + httpIP + ":" + port + "/query/50000");
		
		//client.post(port, httpIP, "/query/50000")
		client.post(port, httpIP, "/query/50000")
		.sendJsonObject(test, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/query/50000 === success" );

				try {

					///same data as StationServer logic
					
					JsonArray array = ar.result().bodyAsJsonArray();
					
					for(int i = 0; i < array.size(); i++) {
						array.getJsonObject(i).put("create_id", "");
						array.getJsonObject(i).put("create_name", "");
						array.getJsonObject(i).put("create_date", "");
						array.getJsonObject(i).put("update_date", "");
						array.getJsonObject(i).put("user_id", "");
						array.getJsonObject(i).put("chk_station", "");
					}
					
					JsonObject afterEdit = new JsonObject();
					afterEdit.put("stations", array);
					afterEdit.put("count", array.size());
					
					System.out.println("after adjusting :: " + afterEdit.toString());
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(afterEdit.toString());

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();

					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());

				}

			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});

	}
	
	private Future<Void> retrieve_station_info(RoutingContext routingContext) {
		
		logger.info("entered retrieve_station_info");
		Promise<Void> promise = Promise.promise();

		WebClient client = WebClient.create(vertx);
		
		String current_user_id = routingContext.request().getParam("current_user_id");
		String comp_id = routingContext.request().getParam("company_id");
		String station_id = routingContext.request().getParam("station_id");

		JsonObject json = new JsonObject();
		json.put("current_user_id", current_user_id);
		json.put("comp_id", comp_id);
		json.put("station_id", station_id);

		System.out.println("json data sending  ::: " + json);
		
		logger.info("requesting to post " + httpIP + ":" + port + "/query/50002");
		
		//client.post(port, httpIP, "/query/50000")
		client.post(port, httpIP, "/query/50002")
		.sendJsonObject(json, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/query/50002 === success" );
				
				JSONParser parser = new JSONParser();
				
				try {
					String testing = ar.result().bodyAsString();
					System.err.println("(1) ===== " + testing);
					JSONArray array = (JSONArray) parser.parse(testing);
					System.err.println("(2) ===== " + array);

					String value = array.get(0).toString();
					System.err.println("(3) ===== " + value);
					
					JSONObject jsonObject = (JSONObject) parser.parse(value);
					jsonObject.put("user_id", "");
					jsonObject.put("chk_station", "");
					System.err.println("(4) ===== " + jsonObject);

					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(jsonObject.toString());
					
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());
					
				}
				
			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		return promise.future();
		
	}
	
	private Future<Void> update_station(RoutingContext routingContext) {
		
		logger.info("entered update_station");
		Promise<Void> promise = Promise.promise();
		
		WebClient client = WebClient.create(vertx);

		String user_id = routingContext.request().getParam("user_id");
		String station_id = routingContext.request().getParam("station_id");
		
		JsonObject json = routingContext.getBodyAsJson();
		json.put("user_id", user_id);
		json.put("station_id", station_id);
		
		System.out.println("json data sending  update_station ::: " + json);
		
		logger.info("requesting to post " + httpIP + ":" + port + "/query/50003");
		
		//client.post(port, httpIP, "/query/50000")
		client.post(port, httpIP, "/query/50003")
		.sendJsonObject(json, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/query/50003 === success" );
				
				try {
					String testing = ar.result().bodyAsString();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(testing.toString());
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());
					
				}
				
			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		return promise.future();
		
	}
	
	private Future<Void> delete_station(RoutingContext routingContext) {
		
		logger.info("entered delete_station");
		Promise<Void> promise = Promise.promise();
		
		WebClient client = WebClient.create(vertx);
		
		JsonObject json = routingContext.getBodyAsJson();
		
		System.out.println("json data sending  delete_station ::: " + json);
		
		logger.info("requesting to post " + httpIP + ":" + port + "/query/50004");
		
		//client.post(port, httpIP, "/query/50000")
		client.post(port, httpIP, "/query/50004")
		.sendJsonObject(json, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/query/50004 === success" );
				
				JSONParser parser = new JSONParser();
				
				try {
					String testing = ar.result().bodyAsString();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(testing.toString());
					
					//routingContext.put("body", jsonarray).response().end();
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());
					
				}
				
			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		return promise.future();
		
	}
	
	private Future<Void> chk_input_data_hp(RoutingContext routingContext) {
		
		logger.info("entered chk_input_data_hp");
		Promise<Void> promise = Promise.promise();
		
		WebClient client = WebClient.create(vertx);
		
		JsonObject json = routingContext.getBodyAsJson();
		
		int queryId = 0;
		
		if(json.containsKey("station_name")) {
			queryId = 50020;
			
		} else if (json.containsKey("serial")) {
			queryId = 50021;

		} else if (json.containsKey("ctn")) {
			queryId = 50022;

		} else if (json.containsKey("drone_id")) {
			queryId = 50023;

		} else if (json.containsKey("station_ip")) {
			queryId = 50024;

		} else if (json.containsKey("station_id")) {
			queryId = 50025;

		}
		
		System.out.println("json data sending  chk_input_data_hp ::: " + json);
		
		String path = "/query/" + queryId;
		logger.info("requesting to post " + httpIP + ":" + port + path);
		
		//client.post(port, httpIP, "/query/50000")
		client.post(port, httpIP, path)
		.sendJsonObject(json, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + path + " === success" );
				
				try {
					String testing = ar.result().bodyAsString();
					testing = testing.replace("(*)", "");
					JsonArray array = new JsonArray(testing);
					JsonObject jsonObj = array.getJsonObject(0);
					System.out.println("result is " + jsonObj);
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(jsonObj.toString());
					
					//routingContext.put("body", jsonarray).response().end();
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());
					
				}
				
			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		return promise.future();
		
	}
	


	private Future<Void> mission_common_hp(RoutingContext routingContext) {
		
		logger.info("entered mission_common_hp");
		Promise<Void> promise = Promise.promise();
		
		WebClient client = WebClient.create(vertx);
		
		JsonObject json = routingContext.getBodyAsJson();
		
		System.out.println("json data sending  mission_common_hp ::: " + json);
		
		logger.info("requesting to post " + httpIP + ":" + port + "/query/50010");
		
		//client.post(port, httpIP, "/query/50000")
		client.post(port, httpIP, "/query/50010")
		.sendJsonObject(json, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/query/50010 === success" );
				
				JSONParser parser = new JSONParser();
				
				try {
					String testing = ar.result().bodyAsString();
					JSONArray array = (JSONArray) parser.parse(testing);
					String value = array.get(0).toString();
					
					JSONObject jsonObject = (JSONObject) parser.parse(value);
					jsonObject.put("mission_id", "");
					jsonObject.put("wayPointIdx", 0);
					jsonObject.put("wayPoint", "");
					jsonObject.put("landing", "");
					jsonObject.put("comp_id", "");
					jsonObject.put("user_id", "");
					jsonObject.put("attr_name", "");
					jsonObject.put("attr_val", "");
					jsonObject.put("cmd_id", "");
					
					System.err.println("(4) ===== " + jsonObject);
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(jsonObject.toString());
					
					//routingContext.put("body", jsonarray).response().end();
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());
					
				}
				
			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		return promise.future();
		
	}
	
	private Future<Void> mission_into_station_hp(RoutingContext routingContext) {
		
		logger.info("entered mission_into_station_hp");
		Promise<Void> promise = Promise.promise();
		
		WebClient client = WebClient.create(vertx);
		
		String company_id = routingContext.request().getParam("company_id");
		
		JsonObject json = new JsonObject();
		json.put("comp_id", company_id);
		
		System.out.println("json data sending  mission_into_station_hp ::: " + json);
		
		logger.info("requesting to post " + httpIP + ":" + port + "/query/50005");
		
		//client.post(port, httpIP, "/query/50000")
		client.post(port, httpIP, "/query/50005")
		.sendJsonObject(json, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/query/50005 === success" );
				
				JSONParser parser = new JSONParser();
				
				try {
					
					JsonArray array = ar.result().bodyAsJsonArray();
					
					for(int i = 0; i < array.size(); i++) {
						array.getJsonObject(i).put("serial", "");
						array.getJsonObject(i).put("manufacturer", "");
						array.getJsonObject(i).put("model_name", "");
						array.getJsonObject(i).put("ctn", "");
						array.getJsonObject(i).put("station_sec", "");
						array.getJsonObject(i).put("ground_lat", "");
						array.getJsonObject(i).put("ground_lon", "");
						array.getJsonObject(i).put("station_ip", "");
						array.getJsonObject(i).put("station_port", "");
						array.getJsonObject(i).put("drone_name", "");
						array.getJsonObject(i).put("comp_id", "");
						
						array.getJsonObject(i).put("create_id", "");
						array.getJsonObject(i).put("create_name", "");
						array.getJsonObject(i).put("create_date", "");
						array.getJsonObject(i).put("update_date", "");
						array.getJsonObject(i).put("user_id", "");
						
					}
					
					JsonObject afterEdit = new JsonObject();
					afterEdit.put("count", array.size());
					afterEdit.put("stations", array);
					
					System.out.println("after adjusting :: " + afterEdit.toString());
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(afterEdit.toString());
					
					//routingContext.put("body", jsonarray).response().end();
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());
					
				}
				
			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		return promise.future();
		
	}
	
	private Future<Void> insert_mission_hp(RoutingContext routingContext) {
		
		logger.info("entered insert_mission_hp");
		Promise<Void> promise = Promise.promise();
		
		WebClient client = WebClient.create(vertx);
		
		String mission_id = routingContext.request().getParam("mission_id");
		
		JsonObject json = routingContext.getBodyAsJson();
		json.put("mission_id", mission_id);
		
		System.out.println("json data sending  insert_mission_hp ::: " + json);
		
		logger.info("requesting to post " + httpIP + ":" + port + "/query/50011");
		
		//client.post(port, httpIP, "/query/50000")
		client.post(port, httpIP, "/query/50011")
		.sendJsonObject(json, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/query/50011 === success" );
				
				try {
					
					JsonObject message = new JsonObject();
					message.put("code", 200);
					message.put("message", "SUCCESS");
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(message.toString());
					
					//routingContext.put("body", jsonarray).response().end();
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());
					
				}
				
			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		return promise.future();
		
	}
	
	private Future<Void> update_mission_hp(RoutingContext routingContext) {
		
		logger.info("entered update_mission_hp");
		Promise<Void> promise = Promise.promise();
		
		WebClient client = WebClient.create(vertx);
		
		String mission_id = routingContext.request().getParam("mission_id");
		
		JsonObject json = routingContext.getBodyAsJson();
		json.put("mission_id", mission_id);
		
		System.out.println("json data sending  update_mission_hp ::: " + json);
		
		logger.info("requesting to post " + httpIP + ":" + port + "/query/50012");
		
		//client.post(port, httpIP, "/query/50000")
		client.post(port, httpIP, "/query/50012")
		.sendJsonObject(json, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/query/50012 === success" );
				
				try {
					
					JsonObject message = new JsonObject();
					message.put("code", 200);
					message.put("message", "SUCCESS");
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(message.toString());
					
					//routingContext.put("body", jsonarray).response().end();
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());
					
				}
				
			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		return promise.future();
		
	}

	private void create_station(RoutingContext routingContext) {
		
		logger.info("entered create_station");
		
		WebClient client = WebClient.create(vertx);
		
		String id = routingContext.request().getParam("current_user_id");
		
		JsonObject test = routingContext.getBodyAsJson();
		System.out.println("id from SS ::: " + test);
		
		logger.info("requesting to post " + httpIP + ":" + port + "/query/50001");
		
		//client.post(port, httpIP, "/query/50000")
		client.post(port, httpIP, "/query/50001")
		.sendJsonObject(test, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/query/50001 === success" );
				
				try {
					///same data as StationServer logic
					
					JsonArray array = ar.result().bodyAsJsonArray();
					
					for(int i = 0; i < array.size(); i++) {
						array.getJsonObject(i).put("create_id", "");
						array.getJsonObject(i).put("create_name", "");
						array.getJsonObject(i).put("create_date", "");
						array.getJsonObject(i).put("update_date", "");
						array.getJsonObject(i).put("user_id", "");
						array.getJsonObject(i).put("chk_station", "");
					}
					
					JsonObject afterEdit = new JsonObject();
					afterEdit.put("stations", array);
					afterEdit.put("count", array.size());
					
					System.out.println("after adjusting :: " + afterEdit.toString());
					
					///
					JsonArray jsonarray = ar.result().bodyAsJsonArray();
					//jsonObject = (JSONObject) parser.parse(ar.result().bodyAsString());
					System.out.println("got from scs ========" + jsonarray);
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(afterEdit.toString());
					
					//routingContext.put("body", jsonarray).response().end();
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());
					
				}
				
			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/retrieve_station_list === fail" );
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		
	}

	private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
		if (http.succeeded()) {
			fut.complete();
		} else {
			fut.fail(http.cause());
		}
	}

	private void end() {
		// TODO Auto-generated method stub

	}

}
