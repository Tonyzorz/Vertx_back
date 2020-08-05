package io.vertx.blog.first;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import io.vertx.common.ValidationUtil;
import io.vertx.common.XmlConvert;
import io.vertx.common.message.MessageReturn;
import io.vertx.common.message.QueryMessage;
import io.vertx.common.message.RouterMessage;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

/**
 * This is a verticle. A verticle is a _Vert.x component_. This verticle is
 * implemented in Java, but you can implement them in JavaScript, Groovy or even
 * Ruby.
 */
public class QueryRoute extends AbstractVerticle {

	private static Logger logger = Logger.getLogger(QueryRoute.class);

	private JDBCClient jdbc;

	private EventBus eb;

	private ClusterManager mgr = new HazelcastClusterManager();
	
	private VertxOptions options = new VertxOptions().setClusterManager(mgr);

	private SharedData sharedData;
	
	private ValidationUtil validUtil;

	private PropertiesConfiguration configuration = null;
	JsonObject jsonz = new JsonObject();
	
	boolean isXML = false;

	/**
	 * This method is called when the verticle is deployed. It creates a HTTP server
	 * and registers a simple request handler.
	 * <p/>
	 * Notice the `listen` method. It passes a lambda checking the port binding
	 * result. When the HTTP server has been bound on the port, it call the
	 * `complete` method to inform that the starting has completed. Else it reports
	 * the error.
	 *
	 * @param fut the future
	 * @throws ConfigurationException
	 */
	private static final String SERVER_FILE = "file:/data/router.properties";
	private static final String SERVER_FILE_MESSAGE = "file:/data/message.properties";
	private static final String CLASSPATH_FILE = "router.properties";
	private static final String CLASSPATH_FILE_MESSAGE = "message.properties";
	
	private static final String COL_ID = "queryId";
	private static final String COL_QSTR = "queryString";
	private static final String COL_DESC = "descript";
	private static final String COL_SQLT = "sqlType";
	private static final String ADDR = "address";
	
	private RouterMessage messageReturn;
	private QueryMessage eventBusReturn;
	
	@Override
	public void start(Future<Void> fut) throws ConfigurationException {

		logger.info("started QueryRoute");

		eb = vertx.eventBus();
		
		sharedData = vertx.sharedData();

		// property files 읽어오기
		try {
			
			configuration = new PropertiesConfiguration(SERVER_FILE);
			
		} catch (ConfigurationException e) {
			
			configuration = new PropertiesConfiguration(CLASSPATH_FILE);

		}

		configuration.setReloadingStrategy(new FileChangedReloadingStrategy());
		configuration.setAutoSave(true);

		if (configuration == null) {
			
			logger.warn("Sorry, unable to find property file(s)");
		
		}

		messageReturn = new RouterMessage();
		validUtil = new ValidationUtil();
		// routing 해주기
		startWebApp((http) -> completeStartup(http, fut));
		
	}
	
	private void startWebApp(Handler<AsyncResult<HttpServer>> next) {
		// Create a router object.
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

		// This body handler will be called for all routes
		router.route().handler(BodyHandler.create());

		// Bind "/" to our hello message.
		String home = configuration.getString("router.home");
		router.route(home).handler(routingContext -> {
			
			CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods);
			HttpServerResponse response = routingContext.response();
			response.putHeader("content-type", "text/html").sendFile("webapp/WEB-INF/views/index.html").end();
			
		});

		router.route("/assets/*").handler(StaticHandler.create("assets"));

		/**
		 * managing all routing with address related to query db
		 */
		String query = configuration.getString("router.query");
		String query_id = configuration.getString("router.query_id");

		router.post(query_id).handler(this::selectQuery); // query router
		router.get(query_id).handler(this::selectQuery);
		router.delete(query_id).handler(this::selectQuery);
		router.put(query_id).handler(this::selectQuery);
		router.patch(query_id).handler(this::selectQuery);
		
		/**
		 * managing query database routing
		 */
		String queryManage_id = configuration.getString("router.queryManage_id");
		String queryManage = configuration.getString("router.queryManage");

		router.get(queryManage).handler(this::getAllQueryManage);
		router.get(queryManage_id).handler(this::getOneQueryManage);
		router.post(queryManage).handler(this::addOneQueryManage);
		router.route(queryManage_id).method(HttpMethod.PATCH).method(HttpMethod.PUT)
				.handler(this::updateOneQueryManage);
		router.delete(queryManage_id).handler(this::deleteOneQueryManage);
		
		/**
		 * property manage
		 */
		String property_id = configuration.getString("router.property_id");
		String property = configuration.getString("router.property");
		
		router.get(property).handler(this::getAllProperty);
		router.get(property_id).handler(this::getOneProperty);
		router.post(property).handler(this::addOneProperty);
		router.route(property_id).method(HttpMethod.PATCH).method(HttpMethod.PUT)
		.handler(this::updateOneProperty);
		router.delete(property_id).handler(this::deleteOneProperty);
		
		/**
		 * instance manage
		 */
		String instance_id = configuration.getString("router.instance_id");
		String instance = configuration.getString("router.instance");
		
		router.get(instance).handler(this::getAllInstance);
		router.get(instance_id).handler(this::getOneInstance);
		router.post(instance).handler(this::addOneInstance);
		router.route(instance_id).method(HttpMethod.PATCH).method(HttpMethod.PUT)
		.handler(this::updateOneInstance);
		router.delete(instance).handler(this::deleteOneInstance);

		/**
		 * managing query database updation
		 */
		String queryUpdate = configuration.getString("router.queryUpdate");
		String queryUpdate_id = configuration.getString("router.queryUpdate_id");

		router.get(queryUpdate_id).handler(this::queryUpdate);
		router.get(queryUpdate).handler(this::queryUpdate);
		
		// Create the HTTP server and pass the "accept" method to the request handler.
		vertx.createHttpServer().requestHandler(router::accept).listen(
				// Retrieve the port from the configuration,
				// default to 8080.
	    config().getInteger("http.port", 8085), next::handle);
		//18085, next::handle);
		
		eb.consumer("vertx.selectQuery", message -> {

			this.selectQueryEvb(message);
		});

	}

	private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
		
		if (http.succeeded()) {
			
			logger.info("created all routes");
			fut.complete();
			
		} else {
			
			fut.fail(http.cause());
			
		}
	}

	@Override
	public void stop() throws Exception {
		jdbc.close();
	}

	private void xmlCheck(RoutingContext routingContext) {
		
		String value = routingContext.request().getHeader("Content-Type");

		Pattern pattern = Pattern.compile(".*application/xml.*");
		Matcher matcher;
		
		if(value != null) {
			matcher = pattern.matcher(value);
			boolean check = matcher.find();
			isXML = check ? true : false;
		} else {
			isXML = false;
		}
	}
	
	private void queryUpdate(RoutingContext routingContext) {

		logger.info("entered queryUpdate");

		JSONObject jsonRoutingData = new JSONObject();
		String queryNum = routingContext.request().getParam(COL_ID);

		/**
		 * queryNum이 없을시 전체 업데이트 있으면 해당 query만 업데이트
		 */
		if (queryNum == null) {

			jsonRoutingData.put(ADDR, "sharedDataQueryUpdateAll");

		} else {

			jsonRoutingData.put(ADDR, "sharedDataQueryUpdateOne");
			jsonRoutingData.put(COL_ID, queryNum);
		}

		logger.info("attempting to connect to vertx.sharedDataQueryUpdate verticle");

		eb.request("vertx.sharedDataQueryUpdate", jsonRoutingData.toString(), reply -> {
			
			if(reply.succeeded()) {
				
				logger.info("finished connection with vertx.sharedDataQueryUpdate eventbus");
				routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
				.end(reply.result().body().toString());
				
			} else {
				logger.error("failed executing inside vertx.sharedDataQueryUpdate");
				messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
			}

		});

	}

	
	private void selectQuery(RoutingContext routingContext) {

		logger.info("entered selectQuery");

		try {
			
			String test = routingContext.getBody().toString();
			JsonObject json;
			
			if("".equals(test)) {
				
				json = new JsonObject();
				
			} else {
				
				json = new JsonObject(routingContext.getBody().toString());
			}
			
			xmlCheck(routingContext);

			// query 번호 받기
			String idString = routingContext.request().getParam(COL_ID);

			// body에 값이 있을때 validation vertx 실행
			if (!json.isEmpty()) {
				
				boolean validationValid = this.checkSql(json.toString());
				
				// validation 문제 없을시 참
				if (validationValid) {

					// 쿼리 id json에 저장
					json.put(COL_ID, idString);
					
					// query verticle
					logger.info("attempting to connect to vertx.query verticle");
					
					eb.request("vertx.query", json.toString(), result -> {
						
						logger.info("finished connection with vertx.query verticle");

						if (result.succeeded()) {
							
							String res = result.result().body().toString();

							// data를 xml로 리턴
							if (isXML) {

								logger.info("About to convert json to xml");

								res = res.replaceAll("[<]", "<![CDATA[");
								res = res.replaceAll("[>]", "]]>");
								
								XmlConvert xmlConvert = XmlConvert.xmlConvert(res);
								boolean success = xmlConvert.isSuccess();
								String xmlResult = xmlConvert.getXmlResult();
								
								if(success) {
									
									logger.info("Successfully converted json to xml");
									routingContext.response().putHeader("content-type", "application/xml; charset=utf-8")
									.end(xmlResult);
									
								} else {
									
									logger.error("Failed converting json to xml or is not in xml converting format");
									routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
									.end(res);

								}
								
								// json으로 리턴
							} else {
								
								logger.info("Successfully returned data in json format");
								routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
								.end(res);
							}

						} else {
							
							logger.error("failed executing inside vertx.query");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
					});

					// validation verticle통해 결과가 false, body에 문제가 있다
				} else {
					
					logger.warn("Validation problem occurred");
					messageReturn.commonReturn(routingContext, MessageReturn.VC_PROBLEM_CODE, MessageReturn.VC_PROBLEM_REASON, isXML);
					
				}

				// body가 없으니 validation vertx실행안하고 바로 vertx.query를 실행한다
			} else {

				// eventbus를 통해 verticle에 보낼 json data
				JsonObject jsons = new JsonObject();
				jsons.put(COL_ID, idString);

				// query verticle
				logger.info("attempting to connect to vertx.query verticle");
				
				eb.request("vertx.query", jsons.toString(), result -> {
					
					logger.info("finished connection with vertx.query verticle");

					if (result.succeeded()) {
						
						String res = result.result().body().toString();

						// data를 xml로 리턴
						if (isXML) {

							logger.info("About to convert json to xml");
							
							res = res.replaceAll("[<]", "<![CDATA[");
							res = res.replaceAll("[>]", "]]>");

							XmlConvert xmlConvert = XmlConvert.xmlConvert(res);
							boolean success = xmlConvert.isSuccess();
							String xmlResult = xmlConvert.getXmlResult();
							
							if(success) {
								
								logger.info("Successfully converted json to xml");
								routingContext.response().putHeader("content-type", "application/xml; charset=utf-8")
								.end(xmlResult);
								
							} else {
								
								logger.error("Failed converting json to xml or is not in xml converting format");
								routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
								.end(res);

							}
							
							// json으로 리턴
						} else {
							
							logger.info("Successfully returned data in json format");
							routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
							.end(res);
						}
					} else {
						
						logger.error("failed executing inside vertx.query");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);

					}

				});
			}

		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);

			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);

		}

	}
	
	private void selectQueryEvb(Message<Object> message) {

		logger.info("entered selectQuery");
		
		JSONParser parser = new JSONParser();

		try {
			
			JSONObject json = (JSONObject) parser.parse(message.body().toString());
			
			// query 번호 받기
			String idString = json.get(COL_ID).toString();

			// body에 값이 있을때 validation vertx 실행
			if (!json.isEmpty()) {
				
				boolean validationValid = this.checkSql(json.toString());
				
				// validation 문제 없을시 참
				if (validationValid) {

					// 쿼리 id json에 저장
					json.put(COL_ID, idString);
					
					// query verticle
					logger.info("attempting to connect to vertx.query verticle");
					
					eb.request("vertx.query", json.toString(), result -> {
						
						logger.info("finished connection with vertx.query verticle");

						if (result.succeeded()) {
							
							String res = result.result().body().toString();
								
							logger.info("Successfully returned data in json format");
							message.reply(res);

						} else {
							
							logger.error("failed executing inside vertx.query");
							eventBusReturn.commonReturn(message, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON);
						}
					});

					// validation verticle통해 결과가 false, body에 문제가 있다
				} else {
					
					logger.warn("Validation problem occurred");
					eventBusReturn.commonReturn(message, MessageReturn.VC_PROBLEM_CODE, MessageReturn.VC_PROBLEM_REASON);
					
				}

				// body가 없으니 validation vertx실행안하고 바로 vertx.query를 실행한다
			} else {

				// eventbus를 통해 verticle에 보낼 json data
				JsonObject jsons = new JsonObject();
				jsons.put(COL_ID, idString);

				// query verticle
				logger.info("attempting to connect to vertx.query verticle");
				
				eb.request("vertx.query", jsons.toString(), result -> {
					
					logger.info("finished connection with vertx.query verticle");

					if (result.succeeded()) {
						
						String res = result.result().body().toString();
							
						logger.info("Successfully returned data in json format");
						message.reply(res);
						
					} else {
						
						logger.error("failed executing inside vertx.query");
						eventBusReturn.commonReturn(message, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON);

					}

				});
			}

		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			eventBusReturn.commonReturn(message, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			eventBusReturn.commonReturn(message, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON);

			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			eventBusReturn.commonReturn(message, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON);

		}

	}

	/**
	 * Insert one query data
	 * 
	 * @param routingContext
	 */
	private void addOneQueryManage(RoutingContext routingContext) {

		logger.info("Entered addOneQueryManage");

	try {
		JsonObject json = routingContext.getBodyAsJson();
		json.put(ADDR, "addOneQueryManage");
		
		boolean isValid =this.checkQureyMange(json.toString(), routingContext);
		
		if(isValid) {
			logger.info("attempting to connect to vertx.queryManage verticle");

			
			eb.request("vertx.queryManage", json.toString(), reply -> {
				
				// 요청 성공시
				if(reply.succeeded()) {
					
					logger.info("vertx.queryManage success");
					String res = reply.result().body().toString();

					logger.info("Successfully returned data in json format");
					routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
					.end(res);
					
					// 요청 실패시
				} else {
					
					logger.error("failed executing inside vertx.queryManage");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);

				}
				
			});
		}
			
		// 정보를 입력하지 않았을 시 
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		} 
		
	}

	/**
	 * Select one query data with specified id
	 * 
	 * @param routingContext
	 */
	private void getOneQueryManage(RoutingContext routingContext) {

		logger.info("Entered getOneQueryManage");
		
		try {
			final String id = routingContext.request().getParam(COL_ID);
	
			// 올바른 정보 입력
			if (!id.equals(null)) {
				
				JsonObject json = new JsonObject();
				json.put(COL_ID, id);
				json.put(ADDR, "getOneQueryManage");
	
				logger.info("attempting to connect to vertx.queryManage verticle");

				eb.request("vertx.queryManage", json.toString(), reply -> {
	
					// 요청 성공시
					if (reply.succeeded()) {
						
						logger.info("vertx.queryManage success");
						String res = reply.result().body().toString();
	
						logger.info("Successfully returned data in json format");
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(res);
	
						// 요청 실패시
					} else {
	
						logger.error("failed executing inside vertx.queryManage");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
	
					}
				});
	
				// 아이디 입력하지 않았을 경우
			} else {
	
				logger.error("wrong input value");
				messageReturn.commonReturn(routingContext, MessageReturn.RC_WRONG_DATA_CODE, MessageReturn.RC_WRONG_DATA_REASON, isXML);
			}
			
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		}  

	}

	/**
	 * Updates query's queryString with specified id
	 * 
	 * @param routingContext
	 */
	private void updateOneQueryManage(RoutingContext routingContext) {

		logger.info("Entered updateOneQueryManage");
		
		try {
			
			final String id = routingContext.request().getParam(COL_ID);

			JsonObject json = routingContext.getBodyAsJson();
			json.put(ADDR, "updateOneQueryManage");
			json.put(COL_ID, id);
			
			boolean isValid = this.checkQureyMange(json.toString(), routingContext);
			
			logger.info("attempting to connect to vertx.queryManage verticle");
			
			if(isValid) {

				eb.request("vertx.queryManage", json.toString(), reply -> {

					// 요청 성공시
					if (reply.succeeded()) {

						logger.info("vertx.queryManage success");
						String res = reply.result().body().toString();

						logger.info("Successfully returned data in json format");
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(res);
						
					} else {

						logger.error("failed executing inside vertx.queryManage");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}

				});

				// 아이디 또는 body를 입력하지 않았을 경우
			} /*else {
				
				logger.error("wrong input value");
				messageReturn.commonReturn(routingContext, MessageReturn.RC_WRONG_DATA_CODE, MessageReturn.RC_WRONG_DATA_REASON, isXML);

			}*/
			
		// 정보를 입력하지 않았을 시 
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		}  

	}

	/**
	 * Deletes one query with specified id
	 * 
	 * @param routingContext
	 */
	private void deleteOneQueryManage(RoutingContext routingContext) {
		
		logger.info("Entered deleteOneQueryManage");
		
		try {

			String id = routingContext.request().getParam(COL_ID);
			JsonObject json = routingContext.getBodyAsJson();
			json.put(ADDR, "deleteOneQueryManage");
			json.put(COL_ID, id);
			
			boolean isValid = this.checkQureyMange(json.toString(), routingContext);

			// 올바른 정보 입력
			if (isValid) {
				
				logger.info("attempting to connect to vertx.queryManage verticle");

				eb.request("vertx.queryManage", json.toString(), reply -> {
		
					// queryManage Verticle 요청 성공시
					if (reply.succeeded()) {
						
						logger.info("vertx.queryManage success");
						String res = reply.result().body().toString();

						logger.info("Successfully returned data in json format");
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(res);
		
						// query verticle와 연결 실패시
					} else {
		
						logger.error("failed executing inside vertx.queryManage");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
				});
		
				// 아이디를 입력하지 않았다
			}/* else {
		
				logger.error("wrong input value");
				messageReturn.commonReturn(routingContext, MessageReturn.RC_WRONG_DATA_CODE, MessageReturn.RC_WRONG_DATA_REASON, isXML);
		
			}*/
		
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		}  
	}

	/**
	 * Retrieves all query info
	 * 
	 * @param routingContext
	 */
	private void getAllQueryManage(RoutingContext routingContext) {
		
		logger.info("Entered getAllQueryManage");
		
		try {

			JsonObject json = new JsonObject();
			json.put(ADDR, "getAllQueryManage");
		
			logger.info("attempting to connect to vertx.queryManage verticle");

			eb.request("vertx.queryManage", json.toString(), reply -> {
		
				// queryManage Verticle 요청 성공시
				if (reply.succeeded()) {
					
					logger.info("vertx.queryManage success");
					
					String res = reply.result().body().toString();
				
					logger.info("Successfully returned data in json format");
					routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
					.end(res);
		
					// 요청 실패시
				} else {
		
					logger.error("failed executing inside vertx.queryManage");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
				}
			});
		
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		}  
		
	}
	
	/**
	 * Insert one query data
	 * 
	 * @param routingContext
	 */
	private void addOneProperty(RoutingContext routingContext) {
		
		logger.info("Entered addOneProperty");
		
		try {
			JsonObject json = routingContext.getBodyAsJson();
			json.put(ADDR, "addOneProperty");
			
			logger.info("attempting to connect to vertx.property verticle");

			eb.request("vertx.property", json.toString(), reply -> {
				
				// 요청 성공시
				if(reply.succeeded()) {
					logger.info("vertx.property success");
					
					String res = reply.result().body().toString();
				
					logger.info("Successfully returned data in json format");
					routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
					.end(res);
					
					// 요청 실패시
				} else {
					
					logger.error("failed executing inside vertx.property");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					
				}
				
			});
			
			// 정보를 입력하지 않았을 시 
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		} 
		
		
	}
	
	/**
	 * Select one query data with specified id
	 * 
	 * @param routingContext
	 */
	private void getOneProperty(RoutingContext routingContext) {
		
		logger.info("Entered getOneProperty");
		
		try {
			final String id = routingContext.request().getParam(COL_ID);
			
			// 올바른 정보 입력
			if (!id.equals(null)) {
				
				JsonObject json = new JsonObject();
				json.put(COL_ID, id);
				json.put(ADDR, "getOneProperty");
				
				logger.info("attempting to connect to vertx.property verticle");

				eb.request("vertx.property", json.toString(), reply -> {
					
					// 요청 성공시
					if (reply.succeeded()) {
						
						logger.info("vertx.property success");
						
						String res = reply.result().body().toString();
					
						logger.info("Successfully returned data in json format");
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(res);
						
						// 요청 실패시
					} else {
						
						logger.error("failed executing inside vertx.property");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						
					}
				});
				
				// 아이디 입력하지 않았을 경우
			} else {
				
				logger.error("wrong input value");
				messageReturn.commonReturn(routingContext, MessageReturn.RC_WRONG_DATA_CODE, MessageReturn.RC_WRONG_DATA_REASON, isXML);
				
			}
			
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		} 
		
	}
	
	/**
	 * Updates query's queryString with specified id
	 * 
	 * @param routingContext
	 */
	private void updateOneProperty(RoutingContext routingContext) {
		
		logger.info("Entered updateOneProperty");
		
		try {
			
			final String id = routingContext.request().getParam(COL_ID);
			
			JsonObject json = routingContext.getBodyAsJson();
			
			// 올바른 정보 입력
			if (id != null && json != null) {
				
				json.put(ADDR, "updateOneProperty");
				json.put(COL_ID, id);
				
				logger.info("attempting to connect to vertx.property verticle");

				eb.request("vertx.property", json.toString(), reply -> {
					
					// 요청 성공시
					if (reply.succeeded()) {
						
						logger.info("vertx.property success");
						
						String res = reply.result().body().toString();
					
						logger.info("Successfully returned data in json format");
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(res);
						
						// 요청 실패시
					} else {
						
						logger.error("failed executing inside vertx.queryManage");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
					
				});
				
				// 아이디 또는 body를 입력하지 않았을 경우
			} else {
				
				logger.error("wrong input value");
				messageReturn.commonReturn(routingContext, MessageReturn.RC_WRONG_DATA_CODE, MessageReturn.RC_WRONG_DATA_REASON, isXML);
				
			}
			
			// 정보를 입력하지 않았을 시 
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		} 
		
	}
	
	/**
	 * Deletes one query with specified id
	 * 
	 * @param routingContext
	 */
	private void deleteOneProperty(RoutingContext routingContext) {
		
		logger.info("Entered deleteOneProperty");
		
		try {
			
			String id = routingContext.request().getParam(COL_ID);
			JsonObject json = new JsonObject();

			// 올바른 정보 입력
			if (!id.equals(null)) {
				
				json.put(ADDR, "deleteOneProperty");
				json.put(COL_ID, id);
				
				logger.info("attempting to connect to vertx.property verticle");

				eb.request("vertx.property", json.toString(), reply -> {
					
					// queryManage Verticle 요청 성공시
					if (reply.succeeded()) {
						
						logger.info("vertx.property success");
						
						String res = reply.result().body().toString();
					
						logger.info("Successfully returned data in json format");
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(res);
						
						// query verticle와 연결 실패시
					} else {
						
						logger.error("failed executing inside vertx.queryManage");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
				});
				
				// 아이디를 입력하지 않았다
			} else {
				
				logger.error("wrong input value");
				messageReturn.commonReturn(routingContext, MessageReturn.RC_WRONG_DATA_CODE, MessageReturn.RC_WRONG_DATA_REASON, isXML);
				
			}
			
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		} 
	}
	
	/**
	 * Retrieves all query info
	 * 
	 * @param routingContext
	 */
	private void getAllProperty(RoutingContext routingContext) {
		
		logger.info("Entered getAllProperty");
		
		try {
			
			JsonObject json = new JsonObject();
			json.put(ADDR, "getAllProperty");
			
			logger.info("attempting to connect to vertx.property verticle");

			eb.request("vertx.property", json.toString(), reply -> {
				
				// queryManage Verticle 요청 성공시
				if (reply.succeeded()) {
					
					logger.info("vertx.property success");
					
					String res = reply.result().body().toString();
				
					logger.info("Successfully returned data in json format");
					routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
					.end(res);
					
					// 요청 실패시
				} else {
					
					logger.error("failed executing inside vertx.queryManage");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
				}
			});
			
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		} 
		
	}
	
	/**
	 * Insert one query data
	 * 
	 * @param routingContext
	 */
	private void addOneInstance(RoutingContext routingContext) {
		
		logger.info("Entered addOneInstance");
		
		try {
			JsonObject json = routingContext.getBodyAsJson();
			json.put(ADDR, "addOneInstance");
			
			logger.info("attempting to connect to vertx.instance verticle");

			eb.request("vertx.instance", json.toString(), reply -> {
				
				// 요청 성공시
				if(reply.succeeded()) {
					
					logger.info("vertx.instance success");
					
					String res = reply.result().body().toString();
				
					logger.info("Successfully returned data in json format");
					routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
					.end(res);
					
					// 요청 실패시
				} else {
					
					logger.error("failed executing inside vertx.queryManage");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					
				}
				
			});
			
			// 정보를 입력하지 않았을 시 
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		} 
		
		
	}
	
	/**
	 * Select one query data with specified id
	 * 
	 * @param routingContext
	 */
	private void getOneInstance(RoutingContext routingContext) {
		
		logger.info("Entered getOneInstance");
		
		try {
			final String id = routingContext.request().getParam(COL_ID);
			
			// 올바른 정보 입력
			if (!id.equals(null)) {
				
				JsonObject json = new JsonObject();
				json.put(COL_ID, id);
				json.put(ADDR, "getOneInstance");
				
				logger.info("attempting to connect to vertx.instance verticle");

				eb.request("vertx.instance", json.toString(), reply -> {
					
					// 요청 성공시
					if (reply.succeeded()) {
						
						logger.info("vertx.instance success");
						
						String res = reply.result().body().toString();
					
						logger.info("Successfully returned data in json format");
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(res);
						
						// 요청 실패시
					} else {
						
						logger.error("failed executing inside vertx.queryManage");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						
					}
				});
				
				// 아이디 입력하지 않았을 경우
			} else {
				
				logger.error("wrong input value");
				messageReturn.commonReturn(routingContext, MessageReturn.RC_WRONG_DATA_CODE, MessageReturn.RC_WRONG_DATA_REASON, isXML);
				
			}
			
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		} 
		
	}
	
	/**
	 * Updates query's queryString with specified id
	 * 
	 * @param routingContext
	 */
	private void updateOneInstance(RoutingContext routingContext) {
		
		logger.info("Entered updateOneInstance");
		
		try {
			
			final String id = routingContext.request().getParam(COL_ID);
			
			JsonObject json = routingContext.getBodyAsJson();
			
			// 올바른 정보 입력
			if (id != null && json != null) {
				
				json.put(ADDR, "updateOneInstance");
				json.put(COL_ID, id);
				
				logger.info("attempting to connect to vertx.instance verticle");

				eb.request("vertx.instance", json.toString(), reply -> {
					
					// 요청 성공시
					if (reply.succeeded()) {
						
						logger.info("vertx.instance success");
						
						String res = reply.result().body().toString();
					
						logger.info("Successfully returned data in json format");
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(res);
						
						// 요청 실패시
					} else {
						
						logger.error("failed executing inside vertx.instance");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
					
				});
				
				// 아이디 또는 body를 입력하지 않았을 경우
			} else {
				
				logger.error("wrong input value");
				messageReturn.commonReturn(routingContext, MessageReturn.RC_WRONG_DATA_CODE, MessageReturn.RC_WRONG_DATA_REASON, isXML);
			}
			
			// 정보를 입력하지 않았을 시 
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		} 
		
	}
	
	/**
	 * Deletes one query with specified id
	 * 
	 * @param routingContext
	 */
	private void deleteOneInstance(RoutingContext routingContext) {
		
		logger.info("Entered deleteOneInstance");
		
		try {
			
			JsonObject json = routingContext.getBodyAsJson();
			
			// 올바른 정보 입력
			if (json != null) {
				
				json.put(ADDR, "deleteOneInstance");
				
				logger.info("attempting to connect to vertx.instance verticle");

				eb.request("vertx.instance", json.toString(), reply -> {
					
					// queryManage Verticle 요청 성공시
					if (reply.succeeded()) {
						
						logger.info("vertx.instance success");
						
						String res = reply.result().body().toString();
					
						logger.info("Successfully returned data in json format");
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(res);
						
						// query verticle와 연결 실패시
					} else {
						
						logger.error("failed executing inside vertx.instance");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
				});
				
				// 아이디를 입력하지 않았다
			} else {
				
				logger.error("wrong input value");
				messageReturn.commonReturn(routingContext, MessageReturn.RC_WRONG_DATA_CODE, MessageReturn.RC_WRONG_DATA_REASON, isXML);
				
			}
			
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		} 
	}
	
	/**
	 * Retrieves all query info
	 * 
	 * @param routingContext
	 */
	private void getAllInstance(RoutingContext routingContext) {
		
		logger.info("Entered getAllInstance");
		
		try {
			
			JsonObject json = new JsonObject();
			json.put(ADDR, "getAllInstance");
			
			logger.info("attempting to connect to vertx.instance verticle");

			eb.request("vertx.instance", json.toString(), reply -> {
				
				// queryManage Verticle 요청 성공시
				if (reply.succeeded()) {
					
					logger.info("vertx.instance success");
					
					String res = reply.result().body().toString();
				
					logger.info("Successfully returned data in json format");
					routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
					.end(res);
					
					// 요청 실패시
				} else {
					
					logger.error("failed executing inside vertx.instance");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
				}
			});
			
		} catch(NullPointerException e) {
			
			logger.error("NullPointerException occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON, isXML);
	
		} catch(DecodeException e) {
			
			logger.error("DecodeException has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_DECODE_EXCEPTION_CODE, MessageReturn.RC_DECODE_EXCEPTION_REASON, isXML);
	
			
		} catch(Exception e) {
			
			logger.error("Exception has occurred");
			messageReturn.commonReturn(routingContext, MessageReturn.RC_EXCEPTION_CODE, MessageReturn.RC_EXCEPTION_REASON, isXML);
	
		} 
		
	}
	
	private boolean checkSql(String beforeValidation) {
		
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = new JSONObject();
		ArrayList<String> jsonData = new ArrayList<String>();
		
		try {
			
			jsonObject = (JSONObject) parser.parse(beforeValidation);
			
			for (Object key : jsonObject.keySet()) {
				
				if(!validUtil.isSqlSpecialCharacters(jsonObject.get(key).toString())) {
					return false;
				}
			}
			

		} catch (ParseException e) {
			
			System.out.println(e);
			
			return false;
		}
		
		return true;
	}
	
	private boolean checkQureyMange(String beforeValidation, RoutingContext routingContext) {
		
		logger.info("Entered checkQureyMange");

		JSONParser parser = new JSONParser();
		JSONObject jsonObject = new JSONObject();		
		
		try {
			
			jsonObject = (JSONObject) parser.parse(beforeValidation);
			
			String address = jsonObject.get(ADDR).toString();
			
			if("addOneQueryManage".equals(address)) {
				
				logger.info("checkQureyMange case addOneQueryManage");
				
				if(!jsonObject.containsKey(COL_ID) || !jsonObject.containsKey(COL_QSTR) || !jsonObject.containsKey(COL_DESC) || !jsonObject.containsKey(COL_SQLT)) {
					logger.error("Not Contains Key");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_WRONGKEY_CODE, MessageReturn.RC_WRONGKEY_REASON, isXML);
					return false;
				} else if( !this.checkParam( jsonObject, routingContext) ) {
					return false;
				}
				
			}else if("updateOneQueryManage".equals(address)) {
				
				logger.info("checkQureyMange case updateOneQueryManage");
				
				if(!jsonObject.containsKey(COL_ID) || !jsonObject.containsKey(COL_QSTR) || !jsonObject.containsKey(COL_DESC) || !jsonObject.containsKey(COL_SQLT)) {
					logger.error("Not Contains Key");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_WRONGKEY_CODE, MessageReturn.RC_WRONGKEY_REASON, isXML);
					return false;
				} else if( !this.checkParam( jsonObject, routingContext) ) {
					return false;
				}
				
			}else if("deleteOneQueryManage".equals(address)) {
				
				logger.info("checkQureyMange case deleteOneQueryManage");
				
				if(!jsonObject.containsKey(COL_ID) || !jsonObject.containsKey(COL_SQLT)) {
					logger.error("Not Contains Key");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_WRONGKEY_CODE, MessageReturn.RC_WRONGKEY_REASON, isXML);
					return false;
				} else if( !this.checkParam( jsonObject, routingContext) ) {
					return false;
				}
				
			}

		} catch (ParseException e) {
			
			System.out.println(e);
			
		}
		
		return true;
	}
	
	private boolean checkParam(JSONObject jsonObject, RoutingContext routingContext) {
		
		logger.info("Entered checkParam");
		
		for (Object key : jsonObject.keySet()) {
			
			Object obj = jsonObject.get(key);
			String jsonStr = obj.toString();					
			
			if(COL_ID.equals(key)) {
				
				//if(!(obj instanceof Integer || obj instanceof Long)){
				if(!(obj instanceof String)){	
					logger.error(key+" parameter type error");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
					return false;
				}else if(jsonStr.length() <= 0 || jsonStr.length() > 20) {
					logger.error(key+" parameter length error");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_LENGTHERROR_CODE, key + MessageReturn.RC_LENGTHERROR_REASON, isXML);
					return false;
				}
				
			}else if(COL_QSTR.equals(key)) {
				
				if(!(obj instanceof String)){
					logger.error(key+" parameter type error");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
					return false;
				}else if(jsonStr.length() <= 0 || jsonStr.length() > 2000) {
					logger.error(key+" parameter length error");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_LENGTHERROR_CODE, key + MessageReturn.RC_LENGTHERROR_REASON, isXML);
					return false;
				}
				
			}else if(COL_DESC.equals(key)) {
				
				if(!(obj instanceof String)){
					logger.error(key+" parameter type error");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
					return false;
				}else if(jsonStr.length() <= 0 || jsonStr.length() > 2000) {
					logger.error(key+" parameter length error");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_LENGTHERROR_CODE, key + MessageReturn.RC_LENGTHERROR_REASON, isXML);
					return false;
				}
				
			}else if(COL_SQLT.equals(key)) {
				
				if(!(obj instanceof String)){
					logger.error(key+" parameter type error");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
					return false;
				}else if(jsonStr.length() <= 0 || jsonStr.length() > 45) {
					logger.error(key+" parameter length error");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_LENGTHERROR_CODE, key + MessageReturn.RC_LENGTHERROR_REASON, isXML);
					return false;
				}
			}					
			
		}
		
		return true;
		
	}
	
	
}
