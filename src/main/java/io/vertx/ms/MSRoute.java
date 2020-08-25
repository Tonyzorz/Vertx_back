package io.vertx.ms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import io.vertx.common.ValidationUtil;
import io.vertx.common.XmlConvert;
import io.vertx.common.message.MessageReturn;
import io.vertx.common.message.RouterMessage;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

/**
 * This is a verticle. A verticle is a _Vert.x component_. This verticle is
 * implemented in Java, but you can implement them in JavaScript, Groovy or even
 * Ruby.
 */
public class MSRoute extends AbstractVerticle {

	private static Logger logger = Logger.getLogger(MSRoute.class);

	private JDBCClient jdbc;

	private EventBus eb;

	private ClusterManager mgr = new HazelcastClusterManager();
	
	private VertxOptions options = new VertxOptions().setClusterManager(mgr);

	private SharedData sharedData;
	
	private ValidationUtil validUtil;

	private PropertiesConfiguration configuration = null;
	JsonObject jsonz = new JsonObject();
	
	boolean isXML = false;
	Integer pararmCnt = null;
	int test;
	boolean chk = true;

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
	private static final String SERVER_FILE = "file:/data/ms_router.properties";
	private static final String CLASSPATH_FILE = "ms_router.properties";
	
	private static final String COL_ID = "queryId";
	private static final String COL_QSTR = "queryString";
	private static final String COL_DESC = "descript";
	private static final String COL_SQLT = "sqlType";
	private static final String ADDR = "address";
	
	private RouterMessage messageReturn;
	
	@Override
	public void start(Future<Void> fut) throws ConfigurationException {

		logger.info("started MSRoute");

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
		/*String home = configuration.getString("router.home");
		router.route(home).handler(routingContext -> {
			
			CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods);
			HttpServerResponse response = routingContext.response();
			response.putHeader("content-type", "text/html").sendFile("webapp/WEB-INF/views/index.html").end();
			
		});*/

		router.route("/assets/*").handler(StaticHandler.create("assets"));
		/**
		 * managing query database routing
		 */
		String ms_list = configuration.getString("router.ms_list");		
		String ms_details = configuration.getString("router.ms_details");
		String ms_create = configuration.getString("router.ms_create");
		String ms_update = configuration.getString("router.ms_update");
		String ms_delete = configuration.getString("router.ms_delete");
		
		router.post(ms_list).handler(this::getAllMission);
		router.put(ms_create).handler(this::addOneQueryManage);
		router.put(ms_update).handler(this::updateOneQueryManage);
		router.post(ms_delete).handler(this::deleteOneQueryManage);
		router.get(ms_details).handler(this::getOneQueryManage);
		
		
		// Create the HTTP server and pass the "accept" method to the request handler.
		vertx.createHttpServer().requestHandler(router::accept).listen(
				// Retrieve the port from the configuration,
				// default to 8080.
	    config().getInteger("ms.port", 8085), next::handle);
		//18085, next::handle);
		

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
			String idString = routingContext.request().getParam(ADDR);

			// body에 값이 있을때 validation vertx 실행
			if (!json.isEmpty()) {
				
				boolean validationValid = this.checkSql(json.toString());
				
				// validation 문제 없을시 참
				if (validationValid) {

					// 쿼리 id json에 저장
					json.put(ADDR, idString);
					
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
				jsons.put(ADDR, idString);

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

	/**
	 * Insert one query data
	 * 
	 * @param routingContext
	 */
	private void addOneQueryManage(RoutingContext routingContext) {

		logger.info("Entered addOneQueryManage");
		JSONParser parser = new JSONParser();

		try {
			JsonObject json = routingContext.getBodyAsJson();
			json.put(ADDR, "addOneQueryManage");
			
			List<JSONObject> cmdList = new ArrayList<JSONObject>();
			List<JSONObject> MISSION = new ArrayList<JSONObject>();
			List<JSONObject> COMMAND = new ArrayList<JSONObject>();
			List<JSONObject> ATTRIBUTE = new ArrayList<JSONObject>();
			
			boolean isValid = this.checkMSParam(json.toString(), routingContext, cmdList);
			
			System.out.println("isValid : "+isValid);
			
			if(isValid) {
				
				JSONObject queryId = new JSONObject();
				queryId.put(COL_ID, "2002");
				
				Future<String> fut1 = Future.future(promise -> eb.request("vertx.selectQuery", queryId.toString(), reply -> {
					
					if(reply.succeeded()) {
						
						JSONArray attrbutes = null;					
						
						String res = reply.result().body().toString();
						
						System.out.println("res : "+res);					
	
						try {
							
							attrbutes = (JSONArray) parser.parse(res);
							
							System.out.println("missions : "+attrbutes.toJSONString());
							
							//List<JSONObject> mandatoryList = new ArrayList<JSONObject>();
							LinkedHashMap<String,ArrayList<String>> mandatoryMap = new LinkedHashMap<String,ArrayList<String>>();
							LinkedHashMap<String,ArrayList<String>> attrMap = new LinkedHashMap<String,ArrayList<String>>();
							
							for (int i = 0; i < attrbutes.size(); i++) {
								JSONObject attr = (JSONObject) attrbutes.get(i);
								String cmdCode = attr.get("cmd_code").toString();
								String attrName = attr.get("attr_name").toString();
								String isMandatory = "";
								
								if(attr.get("is_mandatory") != null) {
									isMandatory = attr.get("is_mandatory").toString();
								}
								
								if(!"".equals(isMandatory) && isMandatory != null && "T".equals(isMandatory)) {								
									
									ArrayList<String> arrManAttrNames = new ArrayList<String>();
									
									if(!mandatoryMap.containsKey(cmdCode)) {
										//신규 cmd_code에 Madatory 속성 이름을  ArrayList에 추가한다.
										arrManAttrNames.add(attrName);
										mandatoryMap.put(cmdCode, arrManAttrNames);
									}else {
										//기존 cmd_code에 해당하는 Madatory 속성 이름을 가져와서 ArrayList에 추가한다.
										arrManAttrNames = mandatoryMap.get(cmdCode);
										arrManAttrNames.add(attrName);
										mandatoryMap.put(cmdCode, arrManAttrNames);
									}
								}
								
								ArrayList<String> arrAttrNames = new ArrayList<String>();
								if(!attrMap.containsKey(cmdCode)) {
									//신규 cmd_code에 Madatory 속성 이름을  ArrayList에 추가한다.
									arrAttrNames.add(attrName);
									attrMap.put(cmdCode, arrAttrNames);
								}else {
									//기존 cmd_code에 해당하는 Madatory 속성 이름을 가져와서 ArrayList에 추가한다.
									arrAttrNames = attrMap.get(cmdCode);
									arrAttrNames.add(attrName);
									attrMap.put(cmdCode, arrAttrNames);
								}
							}
							
							System.out.println("mandatoryMap : "+mandatoryMap.toString());
							System.out.println("attrMap : "+attrMap.toString());
							
							for (int i = 0; i < cmdList.size(); i++) {
								
								JSONObject cmd = (JSONObject) cmdList.get(i);
								String cmdCode = cmd.get("CMD_CODE").toString();
								
								ArrayList<String> arrManAttrNames = mandatoryMap.get(cmdCode);
								
								if(arrManAttrNames == null) {
									//Mandatory 항목이 전혀 없는 Command 경우
									continue;
								}
								
								JSONObject value = (JSONObject) parser.parse(cmd.get("VALUE").toString());
								Map<String, Object> cmdMap = null;
								
								try {
									cmdMap = new ObjectMapper().readValue(value.toJSONString(), Map.class);
									
									//System.out.println("map : "+map.toString());
									
								} catch (JsonParseException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (JsonMappingException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								
								//System.out.println(cmdCode+" cmdMap : "+cmdMap.toString());
								//System.out.println(cmdCode+" arrManAttrNames : "+arrManAttrNames.toString());
								
								//전문 내 Mandatory 항목의 값이 있는지 확인한다.
								for (int j = 0; j < arrManAttrNames.size(); j++) {
									String attrName = arrManAttrNames.get(j);
									
									if(!cmdMap.containsKey(attrName)) {
										
										String msg = "No Mandatory Attribute ATTR_NAME : "+attrName;
										// 전문 내 해당 UI 속성 중 Mandatory 항목이 없는 경우
										logger.error(msg);
										messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, msg, isXML);
										return;
										
									}else if(cmdMap.containsKey(attrName)) {
										// 전문 내 해당 UI 속성 중 Mandatory 항목은 있지만 값이 없는 경우
										String val = cmdMap.get(attrName).toString();
										
										if("".equals(val) || val == null) {
											
											String msg = "No Mandatory Attribute value ATTR_NAME : "+attrName;
											
											logger.error(msg);
											promise.fail(msg);
											messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, msg, isXML);
											return;
										}
										
										//System.out.println(attrName+" val : "+val.toString());
										
									}
									
								}
							}
							
							// 전문 내 count 와 실제 들어온 command 개수와 값 비교
							if(cmdList.size() != pararmCnt) {
								String msg = "Wrong Command count";
								
								logger.error(msg);
								promise.fail(msg);
								messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, msg, isXML);
								return;
							}
							
							//전문에 있는 속성 이름이 기준정보 테이블에 있는 command 별 속성 이름과 일치하는 지 비교
							for (int i = 0; i < cmdList.size(); i++) {
								
								JSONObject cmd = (JSONObject) cmdList.get(i);
								String cmdCode = cmd.get("CMD_CODE").toString();
								
								ArrayList<String> arrAttrNames = attrMap.get(cmdCode);
								
								if(arrAttrNames == null) {
									String msg = "No Attribute CMD_ID : "+cmdCode;
									
									//항목이 전혀 없는 Command 경우
									logger.error(msg);
									promise.fail(msg);
									messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, msg, isXML);								
									return;
								}
								
								JSONObject value = (JSONObject) parser.parse(cmd.get("VALUE").toString());
								Map<String, Object> cmdMap = null;
								
								try {
									cmdMap = new ObjectMapper().readValue(value.toJSONString(), Map.class);
									
									//System.out.println("map : "+map.toString());
									
								} catch (JsonParseException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (JsonMappingException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								
								//System.out.println(cmdCode+" cmdMap : "+cmdMap.toString());
								//System.out.println(cmdCode+" arrAttrNames : "+arrAttrNames.toString());
								
								Iterator<String> it = cmdMap.keySet().iterator();
								
								//전문 내 command code에 해당하는 attribute들을 얻어와 그 값을 기준으로 DB 테이블의  Attribute 이름과 비교한다.
								while(it.hasNext()) {
									String key = it.next().trim();
									
									//전문 내 "command" 속성 항목은 Attribute 테이블에 포함되어 있지 않아 Skip
									if("command".equals(key)) continue;
									
									if(!arrAttrNames.contains(key)) {
										String msg = "Wrong Attribute CMD_ID : "+cmdCode+" ATTR_NAME : "+key;
										// 전문 내 해당 UI 속성 중 Attribute 항목이 없는 경우
										logger.error(msg);
										promise.fail(msg);
										messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, msg, isXML);
										return;
									}
									
								}
								
							}
							
							//result.put("count", temp1.size()+"");
							//result.put("missions", temp1);
							
							promise.complete("test");
	
						} catch (ParseException e) {
							// TODO Auto-generated catch block
							logger.error("failed parsing json addOneQueryManage");
							promise.fail(e.getCause());
						}
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");
						promise.fail("failed executing inside vertx.selectQuery");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
					
				}));
				
				fut1.compose(result ->{
					Promise<String> promise = Promise.promise();
					test = 1;
					
					Future<String> fut2 = this.chkMissionId(promise, routingContext);
					
					this.reChkMissionId(fut2, promise, routingContext);
					
					return promise.future();
				}).compose(missionId ->{
					Promise<Void> promise = Promise.promise();
					System.out.println("cmdList : "+cmdList.toString());
					
					try {
						
						JSONObject mission = (JSONObject) parser.parse(json.toString());
						String comp_id = "";
						
						//System.out.println("mission : "+mission.toString());
						
						mission.remove(ADDR);
						mission.remove(COL_ID);
						mission.remove("commands");
						mission.remove("count");
						mission.put("mission_id", missionId);
						
						//System.out.println("mission : "+mission.toString());
						
						Map<String,String> gridDetailsMap = (Map<String, String>) mission.get("grid_details");
						String gridDetails = gridDetailsMap != null ? gridDetailsMap.toString().replace("=", ":") : "";
						
						mission.put("grid_details", gridDetails);
						
						System.out.println("mission : "+mission.toString());
						
						comp_id = (String) mission.get("comp_id");
						
						MISSION.add(mission);
						
					
						for (int i = 0; i < cmdList.size(); i++) {
							JSONObject cmd = (JSONObject) cmdList.get(i);
							String cmdCode = cmd.get("CMD_CODE").toString();							
							String cmdId = new String().valueOf(i+1);
							
							JSONObject value = (JSONObject) parser.parse(cmd.get("VALUE").toString());
							
							Map<String, Object> cmdMap = null;
							
							try {
								cmdMap = new ObjectMapper().readValue(value.toJSONString(), Map.class);
								
								//System.out.println("map : "+map.toString());
								
							} catch (JsonParseException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (JsonMappingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
							//System.out.println(cmdCode+" cmdMap : "+cmdMap.toString());
							
							Set<String> keys = cmdMap.keySet();
							Iterator<String> it = keys.iterator();
							
							while(it.hasNext()) {
								
								String key = it.next();
								String val = cmdMap.get(key).toString();
								
								if("command".equals(key)) {
									
									JSONObject command = new JSONObject();
									
									command.put("cmd_id", cmdId);
									command.put("cmd_code", cmdCode);
									command.put("cmd_order", cmdId);
									command.put("cmd_type", "0");
									command.put("mission_id", missionId);
									
									COMMAND.add(command);
									
								}else {
									JSONObject attribute = new JSONObject();
									
									attribute.put("attr_name", key);
									attribute.put("cmd_id", cmdId);
									attribute.put("attr_val", val);
									attribute.put("cmd_code", cmdCode);
									attribute.put("comp_id", comp_id);
									attribute.put("mission_id", missionId);
									
									ATTRIBUTE.add(attribute);
								}
							}
							
						}
						
						System.out.println("MISSION : "+MISSION.toString());
						System.out.println("COMMAND : "+COMMAND.toString());
						System.out.println("ATTRIBUTE : "+ATTRIBUTE.toString());
						
						promise.complete();
						
					} catch (ParseException e1) {
						// TODO Auto-generated catch block
						logger.error("failed parsing json addOneQueryManage");
						promise.fail(e1.getCause());
					}	
					
					return promise.future();
				}).compose(ar ->{
					Promise<Void> promise = Promise.promise();
					JSONObject insQueryId = MISSION.get(0);//new JSONObject();
					insQueryId.put(COL_ID, "2004");
					System.out.println("insQueryId : "+insQueryId.toString());
					
					eb.request("vertx.selectQuery", insQueryId.toString(), reply -> {
						
						if(reply.succeeded()) {	
							
							try {
								JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
								JSONObject res =(JSONObject) result.get(0);
								System.out.println("result : "+result.toString());
								System.out.println("res : "+res.toString());
								//인써트 성동
								if("10002".equals(res.get("code").toString())) {
									promise.complete();
								}else {
									logger.error("failed executing inside vertx.selectQuery");
									promise.fail("failed executing inside vertx.selectQuery");
									messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
								}
								
							} catch (ParseException e) {
								// TODO Auto-generated catch block
								logger.error("failed parsing json addOneQueryManage");
								promise.fail(e.getCause());
							}
							
							
						}else {
							logger.error("failed executing inside vertx.selectQuery");
							promise.fail("failed executing inside vertx.selectQuery");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
						}
					});	
					
					
					return promise.future();
				}).compose(ar ->{
					Promise<Void> promise = Promise.promise();
					
					List<Future> futureList = new ArrayList<>();
					
					for(int i=0; i<COMMAND.size(); i++) {
						JSONObject insQueryId = COMMAND.get(i);//new JSONObject();
						insQueryId.put(COL_ID, "2005");
						insQueryId.put("mission_sub_id", "");						
						
						System.out.println("insQueryId : "+insQueryId.toString());
						
						Future<String> fut2 = this.insertList(insQueryId, routingContext);
						futureList.add(fut2);
							
					}
					
					CompositeFuture.all(futureList).onComplete(ar2 -> {
						System.out.println("insertCommandList complete");
						
						if(ar2.succeeded()) {
							System.out.println("success complete");
							promise.complete();
						}else {
							System.out.println("fail complete");
							promise.fail("fail!");
						}
						
					});
					
					return promise.future();
				}).compose(ar ->{
					Promise<Void> promise = Promise.promise();
					
					List<Future> futureList = new ArrayList<>();
					
					for(int i=0; i<ATTRIBUTE.size(); i++) {
						JSONObject insQueryId = ATTRIBUTE.get(i);//new JSONObject();
						insQueryId.put(COL_ID, "2006");
						
						System.out.println("insQueryId : "+insQueryId.toString());
						
						Future<String> fut2 = this.insertList(insQueryId, routingContext);
						futureList.add(fut2);
							
					}
					
					CompositeFuture.all(futureList).onComplete(ar2 -> {
						System.out.println("insertAttributeList complete");
						
						if(ar2.succeeded()) {
							System.out.println("success complete");
							promise.complete();
						}else {
							System.out.println("fail complete");
							promise.fail("fail!");
						}
						
					});
					
					return promise.future();
				});
				
				//System.out.println("result : "+result.toString());
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
	private void getAllMission(RoutingContext routingContext) {
		
		logger.info("Entered getAllMission");
		
		try {

			//JsonObject json = new JsonObject();
			//json.put(ADDR, "getAllMission");
			
			JsonObject json = routingContext.getBodyAsJson();
		
			logger.info("attempting to connect to vertx.queryManage verticle");
			json.put("queryId", "2000");
			//json.put("isXML", true);
			
			boolean isXML = false;			
			
			Future<JSONArray> fut1 = Future.future(promise -> eb.request("vertx.selectQuery", json.toString(), reply -> {
				
				
				if(reply.succeeded()) {
					
					JSONParser parser = new JSONParser();
					JSONArray missions = null;
					JSONObject result = new JSONObject();
					
					String res = reply.result().body().toString();
					
					System.out.println("res : "+res);
					
					//xml 리턴 테스트용 사용X
					if(isXML) {
						res = res.replaceAll("[<]", "<![CDATA[");
						res = res.replaceAll("[>]", "]]>");
					}

					try {
						
						missions = (JSONArray) parser.parse(res);
						
						System.out.println("missions : "+missions.toJSONString());
						
						//result.put("count", temp1.size()+"");
						//result.put("missions", temp1);

					} catch (ParseException e) {
						// TODO Auto-generated catch block
						promise.fail(e.getCause());
						logger.error("failed parsing json getAllMission");

					}
					
					promise.complete(missions);
					
				}else {
					promise.fail(reply.cause());
					logger.error("failed executing inside vertx.selectQuery");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
				}
				
			}));
			
			fut1.compose(missions -> {
				
				json.put("queryId", "2001");
				
				Promise<Void> promise = Promise.promise();
				
				eb.request("vertx.selectQuery", json.toString(), reply -> {
					
					if(reply.succeeded()) {
						
						JSONParser parser = new JSONParser();
						JSONArray attrs = null;
						JSONObject result = new JSONObject();
						
						String res = reply.result().body().toString();
						
						System.out.println("res2 : "+res);
						
						//xml 리턴 테스트용 사용X
						if(isXML) {
							res = res.replaceAll("[<]", "<![CDATA[");
							res = res.replaceAll("[>]", "]]>");
						}

						try {
							
							attrs = (JSONArray) parser.parse(res);
							
							System.out.println("attrs : "+attrs.toJSONString());
							System.out.println("missons2 : "+missions.toJSONString());
							
							for (int i = 0; i < missions.size(); i++) {
								
								JSONObject mission = (JSONObject) missions.get(i);
								
								for (int j = 0; j < attrs.size(); j++) {
									
									JSONObject attr = (JSONObject) attrs.get(j);
									
									if(mission.get("mission_id").equals(attr.get("mission_id"))) {
										
										Integer attr_code = Integer.parseInt(attr.get("attr_code").toString()) ;
										
										if(attr_code == 16001)
											mission.put("lat",attr.get("attr_val"));
										else if (attr_code == 16002)
											mission.put("lon",attr.get("attr_val"));
										
										mission.remove("roundtrip");
										mission.remove("repeat");
										mission.remove("is_grid");
										mission.remove("grid_details");
										mission.remove("total_time");
										mission.remove("total_distance");
									}
									
								}
								
							}
							
							System.out.println("missons3 : "+missions.toJSONString());
							
							
							result.put("count", missions.size()+"");
							result.put("missions", missions);
							
							logger.info("About to convert json to xml");

							//xml 리턴 테스트용 사용X
							if(isXML) {
								XmlConvert xmlConvert = XmlConvert.xmlConvert("["+result.toString()+"]");
								boolean success = xmlConvert.isSuccess();
								String xmlResult = xmlConvert.getXmlResult();
								
								if(success) {
									
									logger.info("Successfully converted json to xml");
									routingContext.response().putHeader("content-type", "application/xml; charset=utf-8")
									.end(xmlResult);
									
								} else {
									
									logger.error("Failed converting json to xml or is not in xml converting format");
									routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
									.end(result.toString());

								}
							//json 리턴
							} else {
								routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
								.end(result.toString());
							}

						} catch (ParseException e) {
							// TODO Auto-generated catch block
							promise.fail(e.getCause());
							logger.error("failed parsing json getAllMission");

						}
						
						promise.complete();
						
					}else {
						promise.fail(reply.cause());
						logger.error("failed executing inside vertx.selectQuery");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
					
					
				});
				
				return promise.future();
				
			});
			
			
			/*eb.request("vertx.selectQuery", json.toString(), reply -> {
				
				
				if(reply.succeeded()) {
				
					JSONParser parser = new JSONParser();
					JSONArray temp1 = null;
					JSONObject result = new JSONObject();
					
					String res = reply.result().body().toString();
					
					//xml 리턴 테스트용 사용X
					if(isXML) {
						res = res.replaceAll("[<]", "<![CDATA[");
						res = res.replaceAll("[>]", "]]>");
					}

					try {
						
						temp1 = (JSONArray) parser.parse(res);
						
						System.out.println("temp1 : "+temp1.toJSONString());
						
						result.put("count", temp1.size()+"");
						result.put("missions", temp1);

					} catch (ParseException e) {
						// TODO Auto-generated catch block
						logger.error("failed parsing json getAllMission");

					}
					
					logger.info("About to convert json to xml");

					//xml 리턴 테스트용 사용X
					if(isXML) {
						XmlConvert xmlConvert = XmlConvert.xmlConvert("["+result.toString()+"]");
						boolean success = xmlConvert.isSuccess();
						String xmlResult = xmlConvert.getXmlResult();
						
						if(success) {
							
							logger.info("Successfully converted json to xml");
							routingContext.response().putHeader("content-type", "application/xml; charset=utf-8")
							.end(xmlResult);
							
						} else {
							
							logger.error("Failed converting json to xml or is not in xml converting format");
							routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
							.end(result.toString());

						}
					//json 리턴
					} else {
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(result.toString());
					}
					
				} else {
					
					logger.error("failed executing inside vertx.selectQuery");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					
				}
				
			});
*/
		
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
	
	private boolean checkMSParam(String beforeValidation, RoutingContext routingContext, List<JSONObject> cmdList) {
		
		logger.info("Entered checkMSParam");
		
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = new JSONObject();
		
		try {
			
			jsonObject = (JSONObject) parser.parse(beforeValidation);
		
			for (Object key : jsonObject.keySet()) {
				
				Object obj = jsonObject.get(key);
				String jsonStr = obj.toString();
				
				if("commands".equals(key)) {				
					if(!(obj instanceof JSONArray)){	
						logger.error(key+" parameter type error");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
						return false;
					}
					
					JSONArray jsonArr = (JSONArray) obj; 
					
					System.out.println("jsonArr : "+jsonArr.toJSONString());
					
					JSONObject fstCom = (JSONObject) jsonArr.get(0);
					JSONObject lstCom = (JSONObject) jsonArr.get(jsonArr.size()-1);
					
					if( !"22".equals( fstCom.get("command") ) ) {
						logger.error("First command is TakeOff 22. InPut command code: "+fstCom.get("command").toString());
						messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
						return false;
					}
					
					if( !( "20".equals( lstCom.get("command") ) || "21".equals( lstCom.get("command") ) ) ) {
						logger.error("Last command is Landing 22 or Return to Lanch 20. InPut command code: "+lstCom.get("command").toString());
						messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
						return false;
					}
					
					for (int i = 0; i < jsonArr.size(); i++) {
						
						JSONObject cmd = new JSONObject();
						JSONObject param = (JSONObject) jsonArr.get(i);
						
						cmd.put("CMD_CODE", param.get("command"));
						cmd.put("VALUE", param.toString());
						
						cmdList.add(cmd);
						
					}
					System.out.println("cmdList : "+cmdList.toString());
					
				}else if("repeat".equals(key)) {
					
					if(!(obj instanceof String)){
						logger.error(key+" parameter type error");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
						return false;
					}
					
					String repeat = obj.toString();
					
					if(!repeat.matches("[-+]?\\d*")){
						logger.error(key+" parameter type error");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
						return false;
					}
					
				}else if("roundtrip".equals(key) || "is_grid".equals(key) ) {
					
					if(!(obj instanceof String)){
						logger.error(key+" parameter type error");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
						return false;
					}
					
					if( !( "true".equals( jsonStr ) || "false".equals( jsonStr ) ) ) {
						logger.error("RoundTrip value is true or false string RoundTrip value: "+jsonStr);
						messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
						return false;
					}
					
				}else if("count".equals(key) ) {
					
					if(!(obj instanceof String)){
						logger.error(key+" parameter type error");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
						return false;
					}
					
					String repeat = obj.toString();
					
					if(!repeat.matches("[-+]?\\d*")){
						logger.error(key+" parameter type error");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, key + MessageReturn.RC_TYPEERROR_REASON, isXML);
						return false;
					}
					
					pararmCnt = Integer.parseInt(obj.toString());
				}
				
				if(jsonStr.length() <= 0 ) {
					logger.error(key+" parameter length error");
					messageReturn.commonReturn(routingContext, MessageReturn.VC_PROBLEM_CODE, key + MessageReturn.VC_PROBLEM_REASON, isXML);
					return false;
				}
				
			}
		
		} catch (ParseException e) {
			
			System.out.println(e);
			
		}
		
		return true;
		
	}
	
	private Future<String> chkMissionId(Promise<String> promise, RoutingContext routingContext){
		Promise<String> promise2 = Promise.promise();
		String uuid = UUID.randomUUID().toString();
		//String mission_id = "ms_00"+test;
		String mission_id = "M"+uuid.substring(10,23);
		
		JSONObject queryId = new JSONObject();
		queryId.put(COL_ID, "2003");
		queryId.put("mission_id", mission_id);
		
		eb.request("vertx.selectQuery", queryId.toString(), reply -> {
			
			if(reply.succeeded()) {
				
				String res = reply.result().body().toString();
				
				System.out.println("getMissions : "+res);
				
				if("[{\"code\":\"10001\",\"reason\":\"쿼리를 통해 조회된 결과가 없습니다\"}]".equals(res)) {
					promise.complete(mission_id);
				}else {
					test++;					
					promise2.complete("fail");
				}
				
			}else {
				logger.error("failed executing inside vertx.selectQuery");				
				messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
			}
		});
		return promise2.future();
	}
	
	private void reChkMissionId(Future<String> fut, Promise<String> promise, RoutingContext routingContext) {
		
		fut.compose(result -> {
			Promise<String> promise2 = Promise.promise();
			System.out.println(test+" result : "+result);
			if(!"fail".equals(result)) {							
				System.out.println("test : "+test);
				promise.complete(result);
			}else {
				System.out.println("fail~~~~ : ");
				Future<String> fut2 = this.chkMissionId(promise, routingContext);
				this.reChkMissionId(fut2, promise, routingContext);
			}
			return promise2.future();
		});
	}
	
	private Future<String> insertList(JSONObject insQueryId, RoutingContext routingContext){
		Promise<String> promise2 = Promise.promise();
		JSONParser parser = new JSONParser();
		
		eb.request("vertx.selectQuery", insQueryId.toString(), reply -> {
			
			if(reply.succeeded()) {	
				
				try {
					JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
					JSONObject res =(JSONObject) result.get(0);
					System.out.println("result : "+result.toString());
					System.out.println("res : "+res.toString());
					//인써트 성동
					if("10002".equals(res.get("code").toString())) {
						promise2.complete();
					}else {
						logger.error("failed executing inside vertx.selectQuery");
						promise2.fail("failed executing inside vertx.selectQuery");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
					
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					logger.error("failed parsing json addOneQueryManage");
					promise2.fail(e.getCause());
				}
				
			}else {
				logger.error("failed executing inside vertx.selectQuery");
				promise2.fail("failed executing inside vertx.selectQuery");
				messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
			}
		});
		
		return promise2.future();
	}
	
}
