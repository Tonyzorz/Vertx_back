package io.vertx.blog.first;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.codec.binary.StringUtils;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

public class QueryWebVerticle extends AbstractVerticle {

	private static Logger logger = Logger.getLogger(QueryWebVerticle.class);
	private SharedData sharedData;
	private WebClient webClient;

	private int port;
	private String httpIP;
	@Override
	public void start(Future<Void> fut) throws Exception {

		logger.info("started QueryWebVerticle");

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
		
		//queryManage
		router.get("/find").handler(this::find_getAllQueryManage);
		router.get("/find/:id").handler(this::find_getOneQueryManage);
		router.delete("/delete/:id").handler(this::delete_deleteOneQueryManage);
		router.post("/queryInsert").handler(this::query_addOneQueryManage);
		router.post("/queryUpdate").handler(this::update_updateOneQueryManage);
		router.get("/headerData").handler(this::get_headerData);
		router.post("/querySearch").handler(this::searchQueryManage);
		
		//property
		router.get("/propertyfind").handler(this::find_getAllProperty);
		router.get("/propertyfind/:id").handler(this::find_getOneProperty);
		router.delete("/propertyDelete/:id").handler(this::delete_deleteOneProperty);
		router.post("/propertyInsert").handler(this::query_addOneProperty);
		router.post("/propertyUpdate").handler(this::update_updateOneProperty);
		
		//instance
		router.get("/instancefind").handler(this::find_getAllInstance);
		router.get("/instancefind/:id/:role/:role_instance_id").handler(this::find_getOneInstance);
		router.get("/instancefind/:id").handler(this::find_getIDInstance);
 		router.delete("/instanceDelete/*").handler(this::delete_deleteOneInstance);
		router.post("/instanceInsert").handler(this::query_addOneInstance);
		router.post("/instanceUpdate").handler(this::update_updateOneInstance);
		

		// Create the HTTP server and pass the "accept" method to the request handler.
		vertx.createHttpServer().requestHandler(router::accept).listen(
				// Retrieve the port from the configuration,
				// default to 8090.
				config().getInteger("webLogic.port", 18090), next::handle);
	}

	private void default_main(RoutingContext routingContext) {

		HttpServerResponse response = routingContext.response();
		response.putHeader("content-type", "text/html").sendFile("webapp/WEB-INF/views/index.html").end();

	}

	/**
	 * 쿼리 정보 호출
	 * 
	 * @param jsonObject
	 * @return
	 */
	private Future<Void> prepareQuery(JSONObject jsonObject) {

		logger.info("entered prepareQuery");

		Promise<Void> promise = Promise.promise();
		WebClient client = WebClient.create(vertx);

		logger.info("requesting to get " + httpIP + ":" + port + "/queryManage");

		// get all query
		client.get(port, httpIP, "/queryManage").send(ar -> {

			if (ar.succeeded()) {

				logger.info("response from get " + httpIP + ":" + port + "/queryManage === success" );

				JsonArray temp = ar.result().bodyAsJsonArray();
				int length = temp.size();

				JSONObject formatingJson = new JSONObject();
				formatingJson.put("count", length);

				jsonObject.put("queryString", formatingJson);

				promise.complete();

			} else {

				logger.info("response from get " + httpIP + ":" + port + "/queryManage === fail" );

				promise.fail(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}

		});

		return promise.future();
	}

	/**
	 * 자기장 정보 호출
	 * 
	 * @param jsonObject
	 * @return
	 */
	private Future<Void> prepareMagnetic(JSONObject jsonObject) {

		logger.info("entered prepareMagnetic");

		Promise<Void> promise = Promise.promise();
		WebClient client = WebClient.create(vertx);

		logger.info("requesting to get " + "spaceweather.rra.go.kr" + ":" + 443 + "/api/kindex");

		client.get(443, "spaceweather.rra.go.kr", "/api/kindex").ssl(true).send(ar -> {

			if (ar.succeeded()) {
				
				logger.info("response from get " + "spaceweather.rra.go.kr" + ":" + 443 + "/api/kindex === success" );

				JSONParser parser = new JSONParser();
				JSONObject temp1 = null;

				try {

					temp1 = (JSONObject) parser.parse(ar.result().body().toString());

					JSONObject temp2 = (JSONObject) temp1.get("kindex");
					Long currentP = (Long) temp2.get("currentP");
					Long currentK = (Long) temp2.get("currentK");

					JSONObject formatingJson = new JSONObject();
					formatingJson.put("currentP", currentP);
					formatingJson.put("currentK", currentK);

					jsonObject.put("magnetic", formatingJson);

				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					promise.fail(new JsonObject().put("error", ar.cause().getMessage()).encode());

				}

				promise.complete();

			} else {

				logger.info("response from get " + "spaceweather.rra.go.kr" + ":" + 443 + "/api/kindex === fail" );

				promise.fail(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}

		});

		return promise.future();
	}

	/**
	 * 날씨 정보 호출
	 * 
	 * @param jsonObject
	 * @return
	 */
	private Future<Void> prepareWeather(JSONObject jsonObject) {

		logger.info("entered prepareWeather");

		Promise<Void> promise = Promise.promise();
		WebClient client = WebClient.create(vertx);
		
		logger.info("requesting to get " + "bn.weatheri.co.kr" + ":" + 80 + "/lguplus_d/now.php?lat=37.5175232&lon=126.8784512&name=lguplus_d");

		// weather data
		client.get(80, "bn.weatheri.co.kr", "/lguplus_d/now.php?lat=37.5175232&lon=126.8784512&name=lguplus_d")
				.send(ar -> {

					if (ar.succeeded()) {
						
						logger.info("response from get " + "bn.weatheri.co.kr" + ":" + 80 + "/lguplus_d/now.php?lat=37.5175232&lon=126.8784512&name=lguplus_d === success" );

						JSONParser parser = new JSONParser();
						JSONObject temp1 = null;

						try {

							temp1 = (JSONObject) parser.parse(ar.result().body().toString());

							JSONObject temp2 = (JSONObject) temp1.get("weather");

							// weather api부터 Double 또는 Long으로 받아와 casting시 오류 발생. ex. 22, 22.5 등
							// 오류 발생 방지 차원에서 String으로 값을 변환 후 다시 Double으로 값 저장.
							String temperature = String.valueOf(temp2.get("temp"));
							Double temp = Double.parseDouble(temperature);
							String sky = (String) temp2.get("sky");

							JSONObject formatingJson = new JSONObject();
							formatingJson.put("temp", temp);
							formatingJson.put("sky", sky);

							// System.err.println("(3)" +jsonObject);
							jsonObject.put("weather", formatingJson);

						} catch (ParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							promise.fail(new JsonObject().put("error", ar.cause().getMessage()).encode());

						}

						promise.complete();

					} else {
						
						logger.info("response from get " + "bn.weatheri.co.kr" + ":" + 80 + "/lguplus_d/now.php?lat=37.5175232&lon=126.8784512&name=lguplus_d === fail" );

						promise.fail(new JsonObject().put("error", ar.cause().getMessage()).encode());

					}

				});

		return promise.future();

	}

	/**
	 * 해더 정보(자기장, 날씨, 쿼리 계수) 호출
	 * 
	 * @param routingContext
	 */
	private void get_headerData(RoutingContext routingContext) {

		logger.info("entered get_headerData");

		JSONObject jsonObject = new JSONObject();

		logger.info("requesting to prepareQuery(jsonObject), prepareMagnetic(jsonObject), prepareWeather(jsonObject)");
		// 성공 순서는 무관하지만 모든 실행 메소드들의 성공 여부가 중요시
		CompositeFuture.all(prepareQuery(jsonObject), prepareMagnetic(jsonObject), prepareWeather(jsonObject))
				.onComplete(ar -> {
					if (ar.succeeded()) {

						logger.info("response from prepareQuery(jsonObject), prepareMagnetic(jsonObject), prepareWeather(jsonObject) === success" );

						routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
						routingContext.response().end(jsonObject.toString());

					} else {

						logger.info("response from prepareQuery(jsonObject), prepareMagnetic(jsonObject), prepareWeather(jsonObject) === fail" );

						routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
						routingContext.response().end(ar.cause().getMessage());
					}
				});

/*
 * 		// 순서대로 하나 하나식 실행이 필요할때
				Future<Void> steps = prepareQuery(jsonObject)
						.compose(v -> prepareMagnetic(jsonObject).compose(vs -> prepareWeather(jsonObject)));
				
		// 모든 호출이 완료되면 setHandler 시작
		steps.onComplete(ar -> {
			if (ar.succeeded()) {
				// System.out.println(jsonObject);
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonObject.toString());

			} else {

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(ar.cause().getMessage());
			}
		});
*/

		// System.out.println("비동기 테스트용 =====" + jsonObject);

	}

	/**
	 * 쿼리 업데이트
	 * 
	 * @param routingContext
	 */
	private void update_updateOneQueryManage(RoutingContext routingContext) {

		logger.info("entered update_updateOneQueryManage");

		try {
			WebClient client = WebClient.create(vertx);
			
			String str = routingContext.getBodyAsString();
			JSONParser parser = new JSONParser();
			JSONObject jsonObject = new JSONObject();

			jsonObject = (JSONObject) parser.parse(str);

			String id = jsonObject.get("id").toString();

			logger.info("response from put " + httpIP + ":" + port + "/queryManage/:id === success" );

			client.put(port, httpIP, "/queryManage/" + id).sendJson(jsonObject, ar -> {

				if (ar.succeeded()) {
					
					logger.info("response from put " + httpIP + ":" + port + "/queryManage/:id === success" );

				} else {
					
					logger.info("response from put " + httpIP + ":" + port + "/queryManage/:id === fail" );

					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());

				}

			});

		} catch (ParseException e) {
			// TODO Auto-generated catch block
			logger.info(e);
			e.printStackTrace();

			routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
			routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());

		}

	}

	/**
	 * 하나의 쿼리 조회
	 * 
	 * @param routingContext
	 */
	private void find_getOneQueryManage(RoutingContext routingContext) {

		logger.info("entered find_getOneQueryManage");

		WebClient client = WebClient.create(vertx);

		String id = routingContext.request().getParam("id");
		
		logger.info("requesting to get " + httpIP + ":" + port + "/queryManage/:id");

		client.get(port, httpIP, "/queryManage/" + id).send(ar -> {

			if (ar.succeeded()) {
				
				logger.info("response from get " + httpIP + ":" + port + "/queryManage/:id === success" );

				JsonArray jsonArray = ar.result().bodyAsJsonArray();

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());

			} else {
				
				logger.info("response from get " + httpIP + ":" + port + "/queryManage/:id === fail" );

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
	}

	/**
	 * 쿼리 등록
	 * 
	 * @param routingContext
	 */
	private void searchQueryManage(RoutingContext routingContext) {

		logger.info("entered searchQueryManage");

		WebClient client = WebClient.create(vertx);

		JsonObject json = routingContext.getBodyAsJson();
		JsonObject sendJson = new JsonObject();
		
		String from = json.getString("from");
		String queryString = json.getString("searchQueryString");
		String descript = json.getString("searchDescript");
		String sqlType = json.getString("searchSqlType");
		String role = json.getString("searchRole");

		
		if("searchLike".equals(from)) {
			
			if(!"".equals(queryString)) {
				
				sendJson.put("queryString", queryString);
			}
			if(!"".equals(descript)) {
				
				sendJson.put("descript", descript);
			}
			
		} else {
			if(!"".equals(sqlType)) {
				
				sendJson.put("sqlType", sqlType);
			}
			if(!"".equals(role)) {
				
				sendJson.put("role", role);
			}
		}
		
		logger.info("requesting to post " + httpIP + ":" + port + "/queryManage");

		client.post(port, httpIP, "/queryManage/search")
				.sendJsonObject(sendJson, ar -> {
					if (ar.succeeded()) {
						
						logger.info("response from post " + httpIP + ":" + port + "/queryManage/search === success" );

						JSONParser parser = new JSONParser();
						JSONObject jsonObject = new JSONObject();
						JSONArray array = new JSONArray();
						try {
							array = (JSONArray) parser.parse(ar.result().bodyAsString());
							//jsonObject = (JSONObject) parser.parse(ar.result().bodyAsString());
							//JsonArray jsonArray = ar.result().bodyAsJsonArray();

							routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
							routingContext.response().end(array.toString());

						} catch (ParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();

							routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
							routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());

						}

					} else {
						
						logger.info("response from post " + httpIP + ":" + port + "/queryManage === fail" );

						routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
						routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
					}
				});

	}
	
	/**
	 * 쿼리 등록
	 * 
	 * @param routingContext
	 */
	private void query_addOneQueryManage(RoutingContext routingContext) {

		logger.info("entered query_addOneQueryManage");

		WebClient client = WebClient.create(vertx);

		JsonObject json = routingContext.getBodyAsJson();
		String id = (String) json.getValue("id");
		String queryString = (String) json.getValue("queryString");
		
		logger.info("requesting to post " + httpIP + ":" + port + "/queryManage");

		client.post(port, httpIP, "/queryManage")
				.sendJsonObject(json, ar -> {
					if (ar.succeeded()) {
						
						logger.info("response from post " + httpIP + ":" + port + "/queryManage === success" );

						JSONParser parser = new JSONParser();
						JSONObject jsonObject = new JSONObject();
						JSONArray array = new JSONArray();
						try {
							array = (JSONArray) parser.parse(ar.result().bodyAsString());
							//jsonObject = (JSONObject) parser.parse(ar.result().bodyAsString());

							routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
							routingContext.response().end(array.toString());

						} catch (ParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();

							routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
							routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());

						}

					} else {
						
						logger.info("response from post " + httpIP + ":" + port + "/queryManage === fail" );

						routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
						routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
					}
				});

	}

	/**
	 * 하나의 쿼리 삭제
	 * 
	 * @param routingContext
	 */
	private void delete_deleteOneQueryManage(RoutingContext routingContext) {

		logger.info("entered delete_deleteOneQueryManage");

		WebClient client = WebClient.create(vertx);

		String id = routingContext.request().getParam("id");
		
		JsonObject json = new JsonObject(routingContext.getBodyAsString());
		
		logger.info("requesting to delete " + httpIP + ":" + port + "/queryManage/:id");

		client.delete(port, httpIP, "/queryManage/" + id).sendJsonObject(json, ar -> {

			if (ar.succeeded()) {
				
				logger.info("response from delete " + httpIP + ":" + port + "/queryManage/:id === success" );

				routingContext.response().end(ar.result().toString());

			} else {
				
				logger.info("response from delete " + httpIP + ":" + port + "/queryManage === fail/:id" );

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});

	}

	/**
	 * 모든 쿼리 조회
	 * 
	 * @param routingContext
	 */
	private void find_getAllQueryManage(RoutingContext routingContext) {

		logger.info("entered find_getAllQueryManage");

		WebClient client = WebClient.create(vertx);
		
		logger.info("requesting to get " + httpIP + ":" + port + "/queryManage");

		client.get(port, httpIP, "/queryManage").send(ar -> {

			if (ar.succeeded()) {
				
				logger.info("response from get " + httpIP + ":" + port + "/queryManage === success" );

				JsonArray jsonArray = ar.result().bodyAsJsonArray();

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());

			} else {
				
				logger.info("response from get " + httpIP + ":" + port + "/queryManage === fail" );

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}

		});

	}
	
	
	
	/**
	 * 쿼리 업데이트
	 * 
	 * @param routingContext
	 */
	private void update_updateOneProperty(RoutingContext routingContext) {

		logger.info("entered update_updateOneProperty");

		WebClient client = WebClient.create(vertx);

		String str = routingContext.getBodyAsString();
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = new JSONObject();

		try {

			jsonObject = (JSONObject) parser.parse(str);

			Long id = (Long) jsonObject.get("id");
			
			logger.info("requesting to put " + httpIP + ":" + port + "/property/:id");

			client.put(port, httpIP, "/property/" + id).sendJson(jsonObject, ar -> {

				if (ar.succeeded()) {
					logger.info("response from put " + httpIP + ":" + port + "/property/:id === success" );

				} else {

					logger.info("response from put " + httpIP + ":" + port + "/property/:id === fail" );
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());

				}

			});

		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

			routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
			routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());

		}

	}
	
	/**
	 * 하나의 쿼리 조회
	 * 
	 * @param routingContext
	 */
	private void find_getOneProperty(RoutingContext routingContext) {
		
		logger.info("entered find_getOneProperty");

		WebClient client = WebClient.create(vertx);
		
		String id = routingContext.request().getParam("id");
		
		logger.info("requesting to get " + httpIP + ":" + port + "/property/:id");

		client.get(port, httpIP, "/property/" + id).send(ar -> {
			
			if (ar.succeeded()) {
				
				logger.info("response from get " + httpIP + ":" + port + "/property/:id === success" );

				JsonArray jsonArray = ar.result().bodyAsJsonArray();
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());
				
			} else {
				
				logger.info("response from get " + httpIP + ":" + port + "/property/:id === fail" );

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
	}
	
	/**
	 * 쿼리 등록
	 * 
	 * @param routingContext
	 */
	private void query_addOneProperty(RoutingContext routingContext) {
		
		logger.info("entered query_addOneProperty");

		WebClient client = WebClient.create(vertx);
		
		JsonObject json = routingContext.getBodyAsJson();
		String id = (String) json.getValue("id");
		String queryString = (String) json.getValue("queryString");
		
		logger.info("requesting to post " + httpIP + ":" + port + "/property");

		client.post(port, httpIP, "/property")
		.sendJsonObject(json, ar -> {
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/property === success" );

				JSONParser parser = new JSONParser();
				JSONObject jsonObject = new JSONObject();
				
				try {
					
					jsonObject = (JSONObject) parser.parse(ar.result().bodyAsString());
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(jsonObject.toString());
					
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());
					
				}
				
			} else {
				
				logger.info("response from post " + httpIP + ":" + port + "/property === fail" );

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		
	}
	
	/**
	 * 하나의 쿼리 삭제
	 * 
	 * @param routingContext
	 */
	private void delete_deleteOneProperty(RoutingContext routingContext) {
		
		logger.info("entered delete_deleteOneProperty");

		WebClient client = WebClient.create(vertx);
		
		JsonObject json = new JsonObject();
		String id = routingContext.request().getParam("id");
		
		logger.info("requesting to delete " + httpIP + ":" + port + "/property/:id");

		client.delete(port, httpIP, "/property/" + id).send(ar -> {
			
			if (ar.succeeded()) {
				
				logger.info("response from delete " + httpIP + ":" + port + "/property/:id === success" );

				routingContext.response().end(ar.result().toString());
				
			} else {
				
				logger.info("response from delete " + httpIP + ":" + port + "/property/:id === fail" );

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		
	}
	
	/**
	 * 모든 쿼리 조회
	 * 
	 * @param routingContext
	 */
	private void find_getAllProperty(RoutingContext routingContext) {
		
		logger.info("entered find_getAllProperty");
		
		WebClient client = WebClient.create(vertx);
		
		logger.info("requesting to get " + httpIP + ":" + port + "/property");

		client.get(port, httpIP, "/property").send(ar -> {
			
			if (ar.succeeded()) {
				
				logger.info("response from get " + httpIP + ":" + port + "/property === success" );

				JsonArray jsonArray = ar.result().bodyAsJsonArray();
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());
				
			} else {
				
				logger.info("response from get " + httpIP + ":" + port + "/property === fail" );

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
			
		});
		
	}
	
	/**
	 * 쿼리 업데이트
	 * 
	 * @param routingContext
	 */
	private void update_updateOneInstance(RoutingContext routingContext) {
		
		logger.info("entered update_updateOneInstance");

		WebClient client = WebClient.create(vertx);
		
		String str = routingContext.getBodyAsString();
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = new JSONObject();
		
		try {
			
			jsonObject = (JSONObject) parser.parse(str);
			
			Long id = (Long) jsonObject.get("id");
			
			logger.info("requesting to put " + httpIP + ":" + port + "/instance/:id");

			client.put(port, httpIP, "/instance/" + id).sendJson(jsonObject, ar -> {
				
				if (ar.succeeded()) {
					
					logger.info("response from put " + httpIP + ":" + port + "/instance/:id === success" );
					
				} else {
					
					logger.info("response from put " + httpIP + ":" + port + "/instance/:id === fail" );
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
					
				}
				
			});
			
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.error(e);
			
			routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
			routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());
			
		}
		
	}
	
	/**
	 * 하나의 쿼리 조회
	 * 
	 * @param routingContext
	 */
	private void find_getOneInstance(RoutingContext routingContext) {
		
		logger.info("entered find_getOneInstance");
		
		WebClient client = WebClient.create(vertx);
		
		String id = routingContext.request().getParam("id");
		String role = routingContext.request().getParam("role");
		String role_instance_id = routingContext.request().getParam("role_instance_id");
		
		logger.info("requesting to get " + httpIP + ":" + port + "/instance/:id");

		client.get(port, httpIP, "/instance/" + id + "/" + role + "/" + role_instance_id).send(ar -> {
			
			if (ar.succeeded()) {
				
				logger.info("response from get " + httpIP + ":" + port + "/instance/:id === success" );

				JsonArray jsonArray = ar.result().bodyAsJsonArray();
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());
				
			} else {
				
				logger.info("response from get " + httpIP + ":" + port + "/instance/:id === fail" );

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
	}
	
	/**
	 * 하나의 쿼리 조회
	 * 
	 * @param routingContext
	 */
	private void find_getIDInstance(RoutingContext routingContext) {
		
		
		logger.info("entered find_getIDInstance");
		
		WebClient client = WebClient.create(vertx);
		
		String id = routingContext.request().getParam("id");
		
		logger.info("requesting to get " + httpIP + ":" + port + "/instance/:id");
		
		client.get(port, httpIP, "/instance/" + id).send(ar -> {
			
			if (ar.succeeded()) {
				
				logger.info("response from get " + httpIP + ":" + port + "/instance/:id === success" );
				
				JsonArray jsonArray = ar.result().bodyAsJsonArray();
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());
				
			} else {
				
				logger.info("response from get " + httpIP + ":" + port + "/instance/:id === fail" );
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
	}
	
	/**
	 * 쿼리 등록
	 * 
	 * @param routingContext
	 */
	private void query_addOneInstance(RoutingContext routingContext) {
		
		logger.info("entered query_addOneInstance");

		WebClient client = WebClient.create(vertx);
		
		JsonObject json = routingContext.getBodyAsJson();
		
		logger.info("requesting to post " + httpIP + ":" + port + "/instance");

		client.post(port, httpIP, "/instance")
		.sendJsonObject(json, ar -> {
			
			if (ar.succeeded()) {
				
				logger.info("response from post " + httpIP + ":" + port + "/instance === success" );

				JSONParser parser = new JSONParser();
				//JSONObject jsonObject = new JSONObject();
				
				try {
					
					JSONArray jsonarray = (JSONArray) parser.parse(ar.result().bodyAsString());
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(jsonarray.toString());
					
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
					routingContext.response().end(new JsonObject().put("error", e.getMessage()).toString());
					
				}
				
			} else {
				logger.info("response from post " + httpIP + ":" + port + "/instance === fail" );

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		
	}
	
	/**
	 * 하나의 쿼리 삭제
	 * 
	 * @param routingContext
	 */
	private void delete_deleteOneInstance(RoutingContext routingContext) {
		
		logger.info("entered delete_deleteOneInstance");

		WebClient client = WebClient.create(vertx);
		
		String id = String.valueOf(routingContext.request().getParam("id"));
		String role = String.valueOf(routingContext.request().getParam("role"));
		String role_instance_id = String.valueOf(routingContext.request().getParam("role_instance_id"));
		
		JsonObject json = new JsonObject();
		json.put("id", id);
		json.put("role", role);
		json.put("role_instance_id", role_instance_id);
		
		logger.info("requesting to delete " + httpIP + ":" + port + "/instance");

		client.delete(port, httpIP, "/instance").sendJson(json, ar -> {
			
			if (ar.succeeded()) {
				
				logger.info("response from delete " + httpIP + ":" + port + "/instance === success" );
				
				routingContext.response().end(ar.result().toString());
				
			} else {
				
				logger.info("response from delete " + httpIP + ":" + port + "/instance === fail" );
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(new JsonObject().put("error", ar.cause().getMessage()).encode());
			}
		});
		
	}
	
	/**
	 * 모든 쿼리 조회
	 * 
	 * @param routingContext
	 */
	private void find_getAllInstance(RoutingContext routingContext) {
		
		logger.info("entered find_getAllInstance");
		
		WebClient client = WebClient.create(vertx);
		
		logger.info("requesting to get " + httpIP + ":" + port + "/instance");

		client.get(port, httpIP, "/instance").send(ar -> {
			
			if (ar.succeeded()) {
				
				logger.info("response from get " + httpIP + ":" + port + "/instance === success" );

				JsonArray jsonArray = ar.result().bodyAsJsonArray();
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());
				
			} else {
				
				logger.info("response from get " + httpIP + ":" + port + "/instance === fail" );

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

	public QueryWebVerticle() {
		// TODO Auto-generated constructor stub
	}

}
