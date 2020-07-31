package io.vertx.blog.first;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;
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
	private PropertiesConfiguration configuration = null;
	// private static final String SERVER_FILE = "file:/data/router.properties";
	// private static final String CLASSPATH_FILE = "router.properties";
	private WebClient webClient;

	@SuppressWarnings("deprecation")
	@Override
	public void start(Future<Void> fut) throws Exception {

		logger.info("started QueryWebVerticle");
		System.out.println("[QueryWebVerticle] started");

		sharedData = vertx.sharedData();

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
		
		//property
		router.get("/propertyfind").handler(this::find_getAllProperty);
		router.get("/propertyfind/:id").handler(this::find_getOneProperty);
		router.delete("/propertyDelete/:id").handler(this::delete_deleteOneProperty);
		router.post("/propertyInsert").handler(this::query_addOneProperty);
		router.post("/propertyUpdate").handler(this::update_updateOneProperty);
		
		//instance
		router.get("/instancefind").handler(this::find_getAllInstance);
		router.get("/instancefind/:id").handler(this::find_getOneInstance);
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

		Promise<Void> promise = Promise.promise();
		WebClient client = WebClient.create(vertx);

		// get all query
		client.get(8090, "192.168.11.6", "/queryManage").send(ar -> {

			if (ar.succeeded()) {

				JsonArray temp = ar.result().bodyAsJsonArray();
				int length = temp.size();

				JSONObject formatingJson = new JSONObject();
				formatingJson.put("count", length);

				jsonObject.put("queryString", formatingJson);

				// System.err.println("(1)" + jsonObject);

				promise.complete();

			} else {

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

		Promise<Void> promise = Promise.promise();
		WebClient client = WebClient.create(vertx);

		client.get(443, "spaceweather.rra.go.kr", "/api/kindex").ssl(true).send(ar -> {

			// System.err.println("(2)" +jsonObject);
			if (ar.succeeded()) {

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

		Promise<Void> promise = Promise.promise();
		WebClient client = WebClient.create(vertx);

		// weather data
		client.get(80, "bn.weatheri.co.kr", "/lguplus_d/now.php?lat=37.5175232&lon=126.8784512&name=lguplus_d")
				.send(ar -> {

					if (ar.succeeded()) {

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
	@SuppressWarnings("deprecation")
	private void get_headerData(RoutingContext routingContext) {

		JSONObject jsonObject = new JSONObject();

		// 성공 순서는 무관하지만 모든 실행 메소드들의 성공 여부가 중요시
		CompositeFuture.all(prepareQuery(jsonObject), prepareMagnetic(jsonObject), prepareWeather(jsonObject))
				.onComplete(ar -> {
					if (ar.succeeded()) {

						routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
						routingContext.response().end(jsonObject.toString());

					} else {

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

		System.out.println("??????????????!!!!!!!!!!!!!!!!!!!!!!!!!");
		WebClient client = WebClient.create(vertx);

		String str = routingContext.getBodyAsString();
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = new JSONObject();

		try {

			jsonObject = (JSONObject) parser.parse(str);

			String id = jsonObject.get("id").toString();

			client.put(8090, "192.168.11.6", "/queryManage/" + id).sendJson(jsonObject, ar -> {

				if (ar.succeeded()) {

					System.out.println("success");

				} else {

					System.out.println("fail");
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
	@SuppressWarnings("deprecation")
	private void find_getOneQueryManage(RoutingContext routingContext) {

		WebClient client = WebClient.create(vertx);

		String id = routingContext.request().getParam("id");

		client.get(8090, "192.168.11.6", "/queryManage/" + id).send(ar -> {

			if (ar.succeeded()) {

				JsonArray jsonArray = ar.result().bodyAsJsonArray();

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());

			} else {

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
	@SuppressWarnings("deprecation")
	private void query_addOneQueryManage(RoutingContext routingContext) {

		WebClient client = WebClient.create(vertx);

		JsonObject json = routingContext.getBodyAsJson();
		String id = (String) json.getValue("id");
		String queryString = (String) json.getValue("queryString");
		

		client.post(8090, "192.168.11.6", "/queryManage")
				.sendJsonObject(json, ar -> {
					if (ar.succeeded()) {

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
	@SuppressWarnings("deprecation")
	private void delete_deleteOneQueryManage(RoutingContext routingContext) {

		WebClient client = WebClient.create(vertx);

		JsonObject json = new JsonObject();
		String id = routingContext.request().getParam("id");

		client.delete(8090, "192.168.11.6", "/queryManage/" + id).send(ar -> {

			if (ar.succeeded()) {

				System.out.println("success");
				routingContext.response().end(ar.result().toString());

			} else {

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
	@SuppressWarnings("deprecation")
	private void find_getAllQueryManage(RoutingContext routingContext) {

		System.err.println("find_getAllQueryManagefind_getAllQueryManagefind_getAllQueryManageenetered??????????????!!!!!!!!!!!!!!?!?!?!?!???");
		WebClient client = WebClient.create(vertx);

		client.get(8090, "192.168.11.6", "/queryManage").send(ar -> {

			if (ar.succeeded()) {

				JsonArray jsonArray = ar.result().bodyAsJsonArray();

				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());

			} else {

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

		WebClient client = WebClient.create(vertx);

		String str = routingContext.getBodyAsString();
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = new JSONObject();

		try {

			jsonObject = (JSONObject) parser.parse(str);

			Long id = (Long) jsonObject.get("id");

			client.put(8090, "192.168.11.6", "/property/" + id).sendJson(jsonObject, ar -> {

				if (ar.succeeded()) {

					System.out.println("success");

				} else {

					System.out.println("fail");
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
	@SuppressWarnings("deprecation")
	private void find_getOneProperty(RoutingContext routingContext) {
		
		WebClient client = WebClient.create(vertx);
		
		String id = routingContext.request().getParam("id");
		
		client.get(8090, "192.168.11.6", "/property/" + id).send(ar -> {
			
			if (ar.succeeded()) {
				
				JsonArray jsonArray = ar.result().bodyAsJsonArray();
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());
				
			} else {
				
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
	@SuppressWarnings("deprecation")
	private void query_addOneProperty(RoutingContext routingContext) {
		
		WebClient client = WebClient.create(vertx);
		
		JsonObject json = routingContext.getBodyAsJson();
		String id = (String) json.getValue("id");
		String queryString = (String) json.getValue("queryString");
		
		
		client.post(8090, "192.168.11.6", "/property")
		.sendJsonObject(json, ar -> {
			if (ar.succeeded()) {
				
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
	@SuppressWarnings("deprecation")
	private void delete_deleteOneProperty(RoutingContext routingContext) {
		
		WebClient client = WebClient.create(vertx);
		
		JsonObject json = new JsonObject();
		String id = routingContext.request().getParam("id");
		
		client.delete(8090, "192.168.11.6", "/property/" + id).send(ar -> {
			
			if (ar.succeeded()) {
				
				System.out.println("success");
				routingContext.response().end(ar.result().toString());
				
			} else {
				
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
	@SuppressWarnings("deprecation")
	private void find_getAllProperty(RoutingContext routingContext) {
		
		System.err.println("enetered???????????????");
		WebClient client = WebClient.create(vertx);
		
		client.get(8090, "192.168.11.6", "/property").send(ar -> {
			
			if (ar.succeeded()) {
				
				JsonArray jsonArray = ar.result().bodyAsJsonArray();
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());
				
			} else {
				
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
		
		WebClient client = WebClient.create(vertx);
		
		String str = routingContext.getBodyAsString();
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = new JSONObject();
		
		try {
			
			jsonObject = (JSONObject) parser.parse(str);
			
			Long id = (Long) jsonObject.get("id");
			
			client.put(8090, "192.168.11.6", "/instance/" + id).sendJson(jsonObject, ar -> {
				
				if (ar.succeeded()) {
					
					System.out.println("success");
					
				} else {
					
					System.out.println("fail");
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
	@SuppressWarnings("deprecation")
	private void find_getOneInstance(RoutingContext routingContext) {
		
		WebClient client = WebClient.create(vertx);
		
		String id = routingContext.request().getParam("id");
		
		client.get(8090, "192.168.11.6", "/instance/" + id).send(ar -> {
			
			if (ar.succeeded()) {
				
				JsonArray jsonArray = ar.result().bodyAsJsonArray();
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());
				
			} else {
				
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
	@SuppressWarnings("deprecation")
	private void query_addOneInstance(RoutingContext routingContext) {
		
		WebClient client = WebClient.create(vertx);
		
		JsonObject json = routingContext.getBodyAsJson();
		
		client.post(8090, "192.168.11.6", "/instance")
		.sendJsonObject(json, ar -> {
			if (ar.succeeded()) {
				
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
	@SuppressWarnings("deprecation")
	private void delete_deleteOneInstance(RoutingContext routingContext) {
		
		System.err.println("entered delete_deleteOneInstance at QueryWebVerticle");
		WebClient client = WebClient.create(vertx);
		
		String id = String.valueOf(routingContext.request().getParam("id"));
		String role = String.valueOf(routingContext.request().getParam("role"));
		String role_instance_id = String.valueOf(routingContext.request().getParam("role_instance_id"));
		
		JsonObject json = new JsonObject();
		json.put("id", id);
		json.put("role", role);
		json.put("role_instance_id", role_instance_id);
		
		client.delete(8090, "192.168.11.6", "/instance").sendJson(json, ar -> {
			
			if (ar.succeeded()) {
				
				System.out.println("success");
				routingContext.response().end(ar.result().toString());
				
			} else {
				
				System.out.println("connection failure to myrouteverticle");
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
	@SuppressWarnings("deprecation")
	private void find_getAllInstance(RoutingContext routingContext) {
		
		logger.info("entered find_getAllInstance");
		WebClient client = WebClient.create(vertx);
		
		client.get(8090, "192.168.11.6", "/instance").send(ar -> {
			
			if (ar.succeeded()) {
				
				logger.info("entered find_getAllInstance");

				JsonArray jsonArray = ar.result().bodyAsJsonArray();
				
				routingContext.response().putHeader("Content-Type", "application/json;charset=UTF-8");
				routingContext.response().end(jsonArray.toString());
				
			} else {
				
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
