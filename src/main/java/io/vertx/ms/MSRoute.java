package io.vertx.ms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mchange.v1.util.IteratorUtils;

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

	private static Logger logger = Logger.getLogger(MSRoute_test.class);

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
	
	private static String COL_ID = "queryId";
	private static String COL_QSTR = "queryString";
	private static String COL_DESC = "descript";
	private static String COL_SQLT = "sqlType";
	private static final String ADDR = "address";
	private static String DB_TYPE = "2"; //mysql
	
	private RouterMessage messageReturn;
	private MSRouterMessage msMessageReturn;
	
	@Override
	public void start(Future<Void> fut) throws ConfigurationException {

		logger.info("started MSRoute");
		
		if("oracle".equals(config().getString("db"))) {
			COL_ID = "QUERYID";
			COL_QSTR = "QUERYSTRING";
			COL_DESC = "DESCRIPT";
			COL_SQLT = "SQLTYPE";
			DB_TYPE = "3"; //oracle
		}

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
		msMessageReturn = new MSRouterMessage();
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
		String ms_uploadMission = configuration.getString("router.ms_uploadMission");
		String ms_reverseUploadMission = configuration.getString("router.ms_reverseUploadMission");
		String ms_serviceList = configuration.getString("router.ms_serviceList");
		String ms_commandByService = configuration.getString("router.ms_commandByService");
		String geo_list = configuration.getString("router.geo_list");
		String geo_details = configuration.getString("router.geo_details");
		String geo_create = configuration.getString("router.geo_create");
		String geo_update = configuration.getString("router.geo_update");
		String geo_delete = configuration.getString("router.geo_delete");
		String geo_uploadMission = configuration.getString("router.geo_uploadGeofence");
		
		String ms_test = "/mission/test";
		
		router.post(ms_list).handler(this::getAllMission);
		router.put(ms_create).handler(this::insertMission);
		router.put(ms_update).handler(this::updateMission);
		router.post(ms_delete).handler(this::deleteMission);
		router.get(ms_details).handler(this::getMissionDetail);
		router.get(ms_uploadMission).handler(this::getMissionToUpload);
		router.post(ms_reverseUploadMission).handler(this::getReverseMissionToUpload);
		router.get(ms_serviceList).handler(this::getServiceTypeList);
		router.post(ms_commandByService).handler(this::getCommandByServiceType);
		router.post(geo_list).handler(this::getGeofence);
		router.get(geo_details).handler(this::getGeofenceDetail);
		router.put(geo_create).handler(this::insertGeofence);
		router.put(geo_update).handler(this::updateGeofence);
		router.post(geo_delete).handler(this::deleteGeofence);
		router.get(geo_uploadMission).handler(this::getGeofenceToUpload);
		router.put(ms_test).handler(this::insertTest);
		
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
	
	/**
	 * Insert one query data
	 * 
	 * @param routingContext
	 */
	private void insertMission(RoutingContext routingContext) {

		logger.info("Entered insertMission");
		JSONParser parser = new JSONParser();

		try {
			JsonObject json = routingContext.getBodyAsJson();
			json.put(ADDR, "insertMission");
			
			System.out.println("json : "+json.toString());
			
			List<JSONObject> cmdList = new ArrayList<JSONObject>();
			List<JSONObject> MISSION = new ArrayList<JSONObject>();
			List<JSONObject> COMMAND = new ArrayList<JSONObject>();
			List<JSONObject> ATTRIBUTE = new ArrayList<JSONObject>();
			String mission_id = "";
			
			boolean isValid = this.checkMSParam(json.toString(), routingContext, cmdList);
			
			if(pararmCnt == null) {
				logger.info("code : "+MSMessageReturn.ERR_CREATE_CODE+", message : "+MSMessageReturn.ERR_CREATE_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_CREATE_CODE, MSMessageReturn.ERR_CREATE_MSG, MSMessageReturn.STAT_ERROR);
			}
			
			System.out.println("isValid : "+isValid);
			
			if(isValid) {
				
				JSONObject queryId = new JSONObject();
				queryId.put(COL_ID, DB_TYPE+"002");
				
				Future<String> fut1 = this.itemValidator(routingContext, queryId, parser, cmdList);
				
				fut1.compose(result ->{
					Promise<String> promise = Promise.promise();
					test = 1;
					
					Future<String> fut2 = this.chkMissionId(promise, routingContext);
					
					this.reChkMissionId(fut2, promise, routingContext);
					
					return promise.future();
				}).compose(missionId ->{
					Promise<String> promise = Promise.promise();
					System.out.println("cmdList : "+cmdList.toString());					
					
					try {
						
						//grid_details 필드 순서 맞추는 용
						Map<String,Object> missionMap = (Map<String,Object>) new Gson().fromJson(json.toString(), Map.class);
						JSONObject mission = (JSONObject) parser.parse(json.toString());
						System.out.println("mission : "+mission.toString());
						String comp_id = "";
						
						//System.out.println("mission : "+mission.toString());
						
						mission.remove(ADDR);
						mission.remove(COL_ID);
						mission.remove("commands");
						mission.remove("count");
						mission.put("mission_id", missionId);
						
						//System.out.println("mission : "+mission.toString());
						
						System.out.println("missionMap : "+missionMap.toString());
						
						Map<String,String> gridDetailsMap = (Map<String, String>) missionMap.get("grid_details");
						String gridDetails = gridDetailsMap != null ? gridDetailsMap.toString().replace("=", ":") : "";
						
						mission.put("grid_details", gridDetails);
						
						System.out.println("mission2 : "+mission.toString());
						
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
						
						promise.complete(missionId);
						
					} catch (ParseException e1) {
						// TODO Auto-generated catch block
						logger.error("failed parsing json insertMission");
						promise.fail(e1.getCause());
					}	
					
					return promise.future();
				}).compose(missionId ->{
					Promise<String> promise = Promise.promise();
					JSONObject insQueryId = MISSION.get(0);//new JSONObject();
					insQueryId.put(COL_ID, DB_TYPE+"004");
					System.out.println("insertMission : "+insQueryId.toString());
					
					//Mission Table Insert
					eb.request("vertx.selectQuery", insQueryId.toString(), reply -> {
						
						if(reply.succeeded()) {	
							
							try {
								JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
								JSONObject res =(JSONObject) result.get(0);
								System.out.println("result : "+result.toString());
								
								//인써트 성공
								if("10002".equals(res.get("code").toString())) {
									promise.complete(missionId);
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
				}).compose(missionId ->{
					Promise<String> promise = Promise.promise();
					
					List<Future> futureList = new ArrayList<>();
					//CommandList Table insert
					for(int i=0; i<COMMAND.size(); i++) {
						JSONObject insQueryId = COMMAND.get(i);//new JSONObject();
						insQueryId.put(COL_ID, DB_TYPE+"005");
						insQueryId.put("mission_sub_id", "");						
						
						//System.out.println("insertCommandList : "+insQueryId.toString());
						//Mission Table Insert
						Future<String> fut2 = this.insertList(insQueryId, routingContext);
						futureList.add(fut2);
							
					}
					
					CompositeFuture.all(futureList).onComplete(ar2 -> {
						System.out.println("insertCommandList complete");
						
						if(ar2.succeeded()) {
							//System.out.println("success complete");
							promise.complete(missionId);
						}else {
							//System.out.println("fail complete");
							promise.fail("fail!");
						}
						
					});
					
					return promise.future();
				}).compose(missionId ->{
					Promise<String> promise = Promise.promise();
					
					List<Future> futureList = new ArrayList<>();
					
					//AttributeList Table insert
					for(int i=0; i<ATTRIBUTE.size(); i++) {
						JSONObject insQueryId = ATTRIBUTE.get(i);//new JSONObject();
						insQueryId.put(COL_ID, DB_TYPE+"006");
						
						//System.out.println("insertAttributeList : "+insQueryId.toString());
						
						Future<String> fut2 = this.insertList(insQueryId, routingContext);
						futureList.add(fut2);
							
					}
					
					CompositeFuture.all(futureList).onComplete(ar2 -> {
						//System.out.println("insertAttributeList complete");
						
						if(ar2.succeeded()) {
							//System.out.println("success complete");
							promise.complete(missionId);
						}else {
							//System.out.println("fail complete");
							promise.fail("fail!");
						}
						
					});
					
					return promise.future();
				}).onSuccess(missionId->{
					
					JSONObject colId = new JSONObject();					
					colId.put(COL_ID, DB_TYPE+"010");
					colId.put("mission_id", missionId);					
					
					if(MISSION.get(0).get("comp_id") != null) {
						colId.put("comp_id", MISSION.get(0).get("comp_id").toString());
						System.out.println("insertMission comp_id : "+colId.get("comp_id").toString());
					}
					
					System.out.println("insertMission getMissions : "+colId.toString());
					
					//Mission Table Insert
					Future<JSONObject> fut2 = Future.future(promise -> eb.request("vertx.selectQuery", colId.toString(), reply -> {
						
						if(reply.succeeded()) {	
							
							try {
								JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
								JSONObject res =(JSONObject) result.get(0);
								String code = "";
								
								//쿼리 오류 확인
								if(res.get("code")!=null) {
									code= res.get("code").toString();
								}
								
								System.out.println("getMissions result : "+result.toString());
								System.out.println("getMissions res : "+res.toString());
								
								//JSONObject mission =(JSONObject) reply.result().body();
								//System.out.println("mission : "+mission.toString());
								
								//쿼리 오류 발생 시
								if( !"".equals(code) &&  !"10002".equals(code)) {
									logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
								}else if( !"".equals(code) && "10001".equals(code)){									
									logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found mission : "+missionId, MSMessageReturn.STAT_ERROR);
								}else {
									
									promise.complete(res);
									
								}
								
							} catch (ParseException e) {
								promise.fail("fail!");
								logger.error("failed parsing json insertMission");
								messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
							}
							
							
						}else {
							logger.error("failed executing inside vertx.selectQuery");							
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
						}
					}));
					
					
					fut2.compose(mission -> {
						Promise<List<Object>> promise = Promise.promise();
						
						colId.put(COL_ID, DB_TYPE+"011");
						
						eb.request("vertx.selectQuery", colId.toString(), reply -> {
							
							if(reply.succeeded()) {	
								
								try {
									JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
									JSONObject res =(JSONObject) result.get(0);
									String code = "";
									
									//쿼리 오류 확인
									if(res.get("code")!=null) {
										code= res.get("code").toString();
									}
									
									System.out.println("getCommandList result : "+result.toString());
									System.out.println("getCommandList res : "+res.toString());
									
									//JSONObject mission =(JSONObject) reply.result().body();
									//System.out.println("mission : "+mission.toString());
									
									//쿼리 오류 발생 시
									if( !"".equals(code) &&  !"10002".equals(code)) {
										logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
										msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
									}else if( !"".equals(code) && "10001".equals(code)){									
										logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
										msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found attributeslist : "+missionId, MSMessageReturn.STAT_ERROR);
									}else {
										
										List<Object> lst = new ArrayList<Object>();
										lst.add(mission);
										lst.add(result);
										
										promise.complete(lst);
										
									}
									
								} catch (ParseException e) {
									promise.fail("fail!");
									logger.error("failed parsing json insertMission");
									messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
								}
								
								
							}else {
								logger.error("failed executing inside vertx.selectQuery");							
								messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
							}
						});
						
						
						return promise.future();
						
					}).compose(objLst -> {
						Promise<List<Object>> promise = Promise.promise();
						
						colId.put(COL_ID, DB_TYPE+"012");
						
						eb.request("vertx.selectQuery", colId.toString(), reply -> {
							
							if(reply.succeeded()) {	
								
								try {
									JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
									JSONObject res =(JSONObject) result.get(0);
									String code = "";
									
									//쿼리 오류 확인
									if(res.get("code")!=null) {
										code= res.get("code").toString();
									}
									
									System.out.println("getAttributeList result : "+result.toString());
									System.out.println("getAttributeList res : "+res.toString());
									
									//JSONObject mission =(JSONObject) reply.result().body();
									//System.out.println("mission : "+mission.toString());
									
									//쿼리 오류 발생 시
									if( !"".equals(code) &&  !"10002".equals(code)) {
										logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
										msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
									}else if( !"".equals(code) && "10001".equals(code)){									
										logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
										msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found attributeslist : "+missionId, MSMessageReturn.STAT_ERROR);
									}else {
										
										objLst.add(result);
										
										promise.complete(objLst);
									}
									
								} catch (ParseException e) {
									promise.fail("fail!");
									logger.error("failed parsing json insertMission");
									messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
								}
								
								
							}else {
								logger.error("failed executing inside vertx.selectQuery");							
								messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
							}
						});
						
						
						return promise.future();
						
					}).onSuccess(objList->{
						
						JSONObject res = (JSONObject) objList.get(0);
						JSONArray commandArr = (JSONArray) objList.get(1);
						JSONArray attributeArr = (JSONArray) objList.get(2);
						
						System.out.println("mission res :"+res.toJSONString());
						System.out.println("commandArr res :"+commandArr.toJSONString());
						System.out.println("attributeArr res :"+attributeArr.toJSONString());
						
						List<Map<String, Object>> commands = new ArrayList<Map<String, Object>>();
						
						
						for(int i=0; i<commandArr.size(); i++) {
							Map<String, Object> map = new LinkedHashMap<>();
							
							JSONObject command = (JSONObject) commandArr.get(i);
							String comId = command.get("CMD_ID").toString();
							
							map.put("command", command.get("CMD_CODE").toString());
							
							for(int j=0; j<attributeArr.size(); j++) {
								
								JSONObject attribute = (JSONObject) attributeArr.get(j);
								String attrComId = attribute.get("CMD_ID").toString();
								
								if(attrComId.equals(comId)) {
									String attrNm= attribute.get("ATTR_NAME").toString();
									String attrVal = attribute.get("ATTR_VAL").toString();
									
									map.put(attrNm, attrVal);
								}
								
							}
							
							commands.add(map);
						}
						
						//조회 필드 순서 때문에 재입력함(JsonObject 형식은 순서X)
						Map<String, Object> result = new LinkedHashMap<>();
						
						result.put("mission_id", res.get("MISSION_ID"));
						result.put("mission_name", res.get("MISSION_NAME"));
						result.put("user_id", res.get("USER_ID"));
						result.put("roundtrip", res.get("ROUNDTRIP"));
						result.put("repeat", res.get("REPEAT"));
						result.put("is_grid", res.get("IS_GRID"));
						result.put("service_type", res.get("SERVICE_TYPE"));
						String gridString = "";
						if(res.get("GRID_DETAILS") != null && "".equals(res.get("GRID_DETAILS").toString())) {
							gridString = res.get("GRID_DETAILS").toString();
							Map<String,String> grid_details = (Map<String,String>) new Gson().fromJson(gridString, Map.class);	
							result.put("grid_details",grid_details);
						}
						result.put("total_time", res.get("TOTAL_TIME"));
						result.put("total_distance", res.get("TOTAL_DISTANCE"));
						result.put("count", commandArr.size()+"");//int -> string 변환
						result.put("commands", commands);
						result.put("created_at",res.get("CREATEDAT"));
						
						System.out.println("result res : "+result.toString());
						
						Gson gson = new Gson();
						
						System.out.println("result res2 : "+gson.toJson(result).toString());
						
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(gson.toJson(result));
						
					}).onFailure(objList->{
						logger.info("code : "+MSMessageReturn.ERR_CREATE_CODE+", message : "+MSMessageReturn.ERR_CREATE_MSG);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_CREATE_CODE, MSMessageReturn.ERR_CREATE_MSG, MSMessageReturn.STAT_ERROR);
					});
					
				}).onFailure(missionId->{
					logger.info("code : "+MSMessageReturn.ERR_WRONG_PARAM_CODE+", message : "+MSMessageReturn.ERR_WRONG_PARAM_MSG);
					msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG, MSMessageReturn.STAT_ERROR);
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
	private void getMissionDetail(RoutingContext routingContext) {

		logger.info("Entered getMissionDetail");
		JSONParser parser = new JSONParser();
		
		try {
			//JsonObject json = routingContext.getBodyAsJson();
			//json.put(ADDR, "getMissionDetail");
			
			String missionId = routingContext.pathParam("mission_id");
			String compId = routingContext.pathParam("company_id");
	
			JSONObject colId = new JSONObject();					
			colId.put(COL_ID, DB_TYPE+"010");
			colId.put("mission_id", missionId);
			colId.put("comp_id", compId);
			
			System.out.println("getMissionDetail getMissions : "+colId.toString());
			
			//Mission Table Insert
			Future<JSONObject> fut2 = Future.future(promise -> eb.request("vertx.selectQuery", colId.toString(), reply -> {
				
				if(reply.succeeded()) {	
					
					try {
						System.out.println("getMissions reply result : "+reply.result().body().toString());
						JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
						JSONObject res =(JSONObject) result.get(0);
						String code = "";
						
						//쿼리 오류 확인
						if(res.get("code")!=null) {
							code= res.get("code").toString();
						}
						
						System.out.println("getMissions result : "+result.toString());
						System.out.println("getMissions res : "+res.toString());
						
						//JSONObject mission =(JSONObject) reply.result().body();
						//System.out.println("mission : "+mission.toString());
						
						//쿼리 오류 발생 시
						if( !"".equals(code) &&  !"10002".equals(code)) {
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
						}else if( !"".equals(code) && "10001".equals(code)){									
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found mission : "+missionId, MSMessageReturn.STAT_ERROR);
						}else {
							
							promise.complete(res);
							
						}
						
					} catch (ParseException e) {
						promise.fail("fail!");
						logger.error("failed parsing json insertMission");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
					
					
				}else {
					logger.error("failed executing inside vertx.selectQuery");							
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
				}
			}));
			
			
			fut2.compose(mission -> {
				Promise<List<Object>> promise = Promise.promise();
				
				colId.put(COL_ID, DB_TYPE+"011");
				
				eb.request("vertx.selectQuery", colId.toString(), reply -> {
					
					if(reply.succeeded()) {	
						
						try {
							JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
							JSONObject res =(JSONObject) result.get(0);
							String code = "";
							
							//쿼리 오류 확인
							if(res.get("code")!=null) {
								code= res.get("code").toString();
							}
							
							System.out.println("getCommandList result : "+result.toString());
							System.out.println("getCommandList res : "+res.toString());
							
							//JSONObject mission =(JSONObject) reply.result().body();
							//System.out.println("mission : "+mission.toString());
							
							//쿼리 오류 발생 시
							if( !"".equals(code) &&  !"10002".equals(code)) {
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
							}else if( !"".equals(code) && "10001".equals(code)){									
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found attributeslist : "+missionId, MSMessageReturn.STAT_ERROR);
							}else {
								
								List<Object> lst = new ArrayList<Object>();
								lst.add(mission);
								lst.add(result);
								
								promise.complete(lst);
								
							}
							
						} catch (ParseException e) {
							promise.fail("fail!");
							logger.error("failed parsing json insertMission");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
						
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");							
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
				});
				
				
				return promise.future();
				
			}).compose(objLst -> {
				Promise<List<Object>> promise = Promise.promise();
				
				colId.put(COL_ID, DB_TYPE+"012");
				
				eb.request("vertx.selectQuery", colId.toString(), reply -> {
					
					if(reply.succeeded()) {	
						
						try {
							JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
							JSONObject res =(JSONObject) result.get(0);
							String code = "";
							
							//쿼리 오류 확인
							if(res.get("code")!=null) {
								code= res.get("code").toString();
							}
							
							System.out.println("getAttributeList result : "+result.toString());
							System.out.println("getAttributeList res : "+res.toString());
							
							//JSONObject mission =(JSONObject) reply.result().body();
							//System.out.println("mission : "+mission.toString());
							
							//쿼리 오류 발생 시
							if( !"".equals(code) &&  !"10002".equals(code)) {
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
							}else if( !"".equals(code) && "10001".equals(code)){									
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found attributeslist : "+missionId, MSMessageReturn.STAT_ERROR);
							}else {
								
								objLst.add(result);
								
								promise.complete(objLst);
							}
							
						} catch (ParseException e) {
							promise.fail("fail!");
							logger.error("failed parsing json insertMission");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
						
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");							
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
				});
				
				
				return promise.future();
				
			}).onSuccess(objList->{
				
				JSONObject res = (JSONObject) objList.get(0);
				JSONArray commandArr = (JSONArray) objList.get(1);
				JSONArray attributeArr = (JSONArray) objList.get(2);
				
				System.out.println("mission res :"+res.toJSONString());
				System.out.println("commandArr res :"+commandArr.toJSONString());
				System.out.println("attributeArr res :"+attributeArr.toJSONString());
				
				List<Map<String, Object>> commands = new ArrayList<Map<String, Object>>();
				
				
				for(int i=0; i<commandArr.size(); i++) {
					Map<String, Object> map = new LinkedHashMap<>();
					
					JSONObject command = (JSONObject) commandArr.get(i);
					String comId = command.get("CMD_ID").toString();
					
					map.put("command", command.get("CMD_CODE").toString());
					
					for(int j=0; j<attributeArr.size(); j++) {
						
						JSONObject attribute = (JSONObject) attributeArr.get(j);
						String attrComId = attribute.get("CMD_ID").toString();
						
						if(attrComId.equals(comId)) {
							String attrNm= attribute.get("ATTR_NAME").toString();
							String attrVal = attribute.get("ATTR_VAL").toString();
							
							map.put(attrNm, attrVal);
						}
						
					}
					
					commands.add(map);
				}
				
				//조회 필드 순서 때문에 재입력함(JsonObject 형식은 순서X)
				Map<String, Object> result = new LinkedHashMap<>();
				
				result.put("mission_id", res.get("MISSION_ID"));
				result.put("mission_name", res.get("MISSION_NAME"));
				result.put("user_id", res.get("USER_ID"));
				result.put("roundtrip", res.get("ROUNDTRIP"));
				result.put("repeat", res.get("REPEAT"));
				result.put("is_grid", res.get("IS_GRID"));
				result.put("service_type", res.get("SERVICE_TYPE"));
				String gridString = "";
				if(res.get("GRID_DETAILS") != null && "".equals(res.get("GRID_DETAILS").toString())) {
					gridString = res.get("GRID_DETAILS").toString();
					Map<String,String> grid_details = (Map<String,String>) new Gson().fromJson(gridString, Map.class);	
					result.put("grid_details",grid_details);
				}
				result.put("total_time", res.get("TOTAL_TIME"));
				result.put("total_distance", res.get("TOTAL_DISTANCE"));
				result.put("count", commandArr.size()+"");//int -> string 변환
				result.put("commands", commands);
				result.put("created_at",res.get("CREATEDAT"));
				
				System.out.println("result res : "+result.toString());
				
				Gson gson = new Gson();
				
				System.out.println("result res2 : "+gson.toJson(result).toString());
				
				routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
				.end(gson.toJson(result).replace("\\", ""));
				
			}).onFailure(objList->{
				logger.info("code : "+MSMessageReturn.ERR_CREATE_CODE+", message : "+MSMessageReturn.ERR_CREATE_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_CREATE_CODE, MSMessageReturn.ERR_CREATE_MSG, MSMessageReturn.STAT_ERROR);
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
	 * Updates query's queryString with specified id
	 * 
	 * @param routingContext
	 */
	private void updateMission(RoutingContext routingContext) {

		logger.info("Entered updateMission");
		JSONParser parser = new JSONParser();
		
		try {
			
			JsonObject json = routingContext.getBodyAsJson();
			json.put(ADDR, "updateMission");
			
			List<JSONObject> cmdList = new ArrayList<JSONObject>();
			List<JSONObject> MISSION = new ArrayList<JSONObject>();
			List<JSONObject> COMMAND = new ArrayList<JSONObject>();
			List<JSONObject> ATTRIBUTE = new ArrayList<JSONObject>();
			
			String mission_id = json.getString("mission_id");
			String company_id = json.getString("comp_id");
			
			if( "".equals(mission_id) || mission_id == null) {
				logger.error("code : "+MSMessageReturn.ERR_MDT_PARAM_MISS_CODE+", message : "+MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+"mission_id");
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_MDT_PARAM_MISS_CODE, MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+"mission_id", MSMessageReturn.STAT_ERROR);
			}
			
			boolean isValid = this.checkMSParam(json.toString(), routingContext, cmdList);
			
			System.out.println("isValid : "+isValid);
			
			logger.info("attempting to connect to vertx.queryManage verticle");
			
			if(isValid) {
				
				JSONObject queryId = new JSONObject();
				queryId.put(COL_ID, DB_TYPE+"002");
				
				Future<String> fut1 = this.itemValidator(routingContext, queryId, parser, cmdList);

				fut1.compose(ar ->{
					Promise<Void> promise = Promise.promise();
					System.out.println("cmdList : "+cmdList.toString());
					
					try {
						//grid_details 필드 순서 맞추는 용
						Map<String,Object> missionMap = (Map<String,Object>) new Gson().fromJson(json.toString(), Map.class);
						JSONObject mission = (JSONObject) parser.parse(json.toString());
						String comp_id = "";
						
						//System.out.println("mission : "+mission.toString());
						
						mission.remove(ADDR);
						mission.remove(COL_ID);
						mission.remove("commands");
						mission.remove("count");
						mission.put("mission_id", mission_id);
						
						//System.out.println("mission : "+mission.toString());
						
						Map<String,String> gridDetailsMap = (Map<String, String>) missionMap.get("grid_details");
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
									command.put("mission_id", mission_id);
									
									COMMAND.add(command);
									
								}else {
									JSONObject attribute = new JSONObject();
									
									attribute.put("attr_name", key);
									attribute.put("cmd_id", cmdId);
									attribute.put("attr_val", val);
									attribute.put("cmd_code", cmdCode);
									attribute.put("comp_id", comp_id);
									attribute.put("mission_id", mission_id);
									
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
						logger.error("failed parsing json updateMission");
						promise.fail(e1.getCause());
					}	
					
					return promise.future();
				}).compose(result ->{
					Promise<String> promise = Promise.promise();
					
					JSONObject queryParam2 = new JSONObject();
					queryParam2.put(COL_ID, DB_TYPE+"010");
					queryParam2.put("mission_id", mission_id);
					queryParam2.put("comp_id", company_id);
					
					eb.request("vertx.selectQuery", queryParam2.toString(), reply -> {
						
						if(reply.succeeded()) {
							
							JSONArray missions = null;					
							
							String res = reply.result().body().toString();
							
							System.out.println("res : "+res);					

							try {
								
								missions = (JSONArray) parser.parse(res);
								
								System.out.println("missions : "+missions.toJSONString());
								System.out.println("missions : "+missions.get(0));
								
								JSONObject queryRes = (JSONObject) missions.get(0);
								String queryResCd = "";
								if(queryRes.get("code") != null) {
									queryResCd = queryRes.get("code").toString();
								}
								
								//정상 조회 시
								if( "".equals(queryResCd) && missions.size() >= 0 ) {
									promise.complete(mission_id);
								}
								//쿼리 결과만 리턴 되고 그 결과가 정상(10002)이 아닐 때
								else if( !"10002".equals(queryResCd) ) {
									logger.error("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
									promise.fail(mission_id);
								}
								
							} catch (ParseException e) {
								// TODO Auto-generated catch block
								logger.error("failed parsing json updateMission");
								promise.fail(e.getCause());
							}
							
						} else {
			
							logger.error("failed executing inside vertx.queryManage");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
					});
					
					return promise.future();
				
				}).compose(result ->{
					Promise<String> promise = Promise.promise();
					
					JSONObject queryParam2 = new JSONObject();
					queryParam2.put(COL_ID, DB_TYPE+"007");
					queryParam2.put("mission_id", result);
					
					System.out.println("DeleteAttributes param : "+queryParam2.toString());
					
					//DeleteAttributes
					eb.request("vertx.selectQuery", queryParam2.toString(), reply -> {
						
						if(reply.succeeded()) {		
							
							JSONArray attributes = null;
							
							String res = reply.result().body().toString();
							
							System.out.println("res : "+res);
							
							try {
								
								attributes = (JSONArray) parser.parse(res);
								
								System.out.println("attributes : "+attributes.toJSONString());
								System.out.println("attributes : "+attributes.get(0));
								
								JSONObject queryRes = (JSONObject) attributes.get(0);
								String queryResCd = queryRes.get("code").toString();
								
								//정상 삭제 시
								if( "10002".equals(queryResCd) ) {
									logger.info("DeleteAttributes Success : "+result);								
									promise.complete(mission_id);
								}else {
									logger.error("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
									promise.fail(mission_id);
								}
								
							} catch (ParseException e) {
								// TODO Auto-generated catch block
								logger.error("failed parsing json updateMission");
								promise.fail(e.getCause());
							}
							
							//promise.complete(result);
							
						} else {
			
							logger.error("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
							promise.fail(result);
						}
					});				
					
					return promise.future();
					
				}).compose(result ->{
					Promise<String> promise = Promise.promise();
					
					JSONObject queryParam2 = new JSONObject();
					queryParam2.put(COL_ID, DB_TYPE+"008");
					queryParam2.put("mission_id", result);
					
					System.out.println("DeleteCommands param : "+queryParam2.toString());
					
					//DeleteCommands
					eb.request("vertx.selectQuery", queryParam2.toString(), reply -> {
						
						if(reply.succeeded()) {		
							
							JSONArray commands = null;
							
							String res = reply.result().body().toString();
							
							System.out.println("res : "+res);
							
							try {
								
								commands = (JSONArray) parser.parse(res);
								
								System.out.println("commands : "+commands.toJSONString());
								System.out.println("commands : "+commands.get(0));
								
								JSONObject queryRes = (JSONObject) commands.get(0);
								String queryResCd = queryRes.get("code").toString();
								
								//정상 삭제 시
								if( "10002".equals(queryResCd) ) {
									logger.info("DeleteCommands Success : "+result);								
									promise.complete(mission_id);
								}else {
									logger.error("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
									promise.fail(mission_id);
								}
								
							} catch (ParseException e) {
								// TODO Auto-generated catch block
								logger.error("failed parsing json updateMission");
								promise.fail(e.getCause());
							}
							
							//promise.complete(result);
							
						} else {
			
							logger.error("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
							promise.fail(result);
						}
					});
					
					return promise.future();
					
				}).compose(result ->{
					Promise<String> promise = Promise.promise();
					
					JSONObject queryParam2 = new JSONObject();
					queryParam2.put(COL_ID, DB_TYPE+"009");
					queryParam2.put("mission_id", result);
					
					System.out.println("DeleteMission param : "+queryParam2.toString());
					
					//DeleteMission
					eb.request("vertx.selectQuery", queryParam2.toString(), reply -> {
						
						if(reply.succeeded()) {		
							
							JSONArray missions = null;
							
							String res = reply.result().body().toString();
							
							System.out.println("res : "+res);
							
							try {
								
								missions = (JSONArray) parser.parse(res);
								
								System.out.println("missions : "+missions.toJSONString());
								System.out.println("missions : "+missions.get(0));
								
								JSONObject queryRes = (JSONObject) missions.get(0);
								String queryResCd = queryRes.get("code").toString();
								
								//정상 삭제 시
								if( "10002".equals(queryResCd) ) {
									logger.info("DeleteMission Success : "+result);								
									promise.complete(mission_id);
								}else {
									logger.error("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
									promise.fail(mission_id);
								}
								
							} catch (ParseException e) {
								// TODO Auto-generated catch block
								logger.error("failed parsing json updateMission");
								promise.fail(e.getCause());
							}
							
							//promise.complete(result);
							
						} else {
			
							logger.error("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR) ;
							promise.fail(result);
						}
					});				
									
					return promise.future();
					
				}).compose(missionId ->{
					Promise<String> promise = Promise.promise();
					JSONObject insQueryId = MISSION.get(0);//new JSONObject();
					insQueryId.put(COL_ID, DB_TYPE+"004");
					System.out.println("insertMission  : "+insQueryId.toString());
					
					//Mission Table Insert
					eb.request("vertx.selectQuery", insQueryId.toString(), reply -> {
						
						if(reply.succeeded()) {	
							
							try {
								JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
								JSONObject res =(JSONObject) result.get(0);
								System.out.println("result : "+result.toString());
								System.out.println("res : "+res.toString());
								//인써트 성동
								if("10002".equals(res.get("code").toString())) {
									promise.complete(missionId);
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
				}).compose(missionId ->{
					Promise<String> promise = Promise.promise();
					
					List<Future> futureList = new ArrayList<>();
					//CommandList Table insert
					for(int i=0; i<COMMAND.size(); i++) {
						JSONObject insQueryId = COMMAND.get(i);//new JSONObject();
						insQueryId.put(COL_ID, DB_TYPE+"005");
						insQueryId.put("mission_sub_id", "");						
						
						//System.out.println("insertCommandList : "+insQueryId.toString());
						//Mission Table Insert
						Future<String> fut2 = this.insertList(insQueryId, routingContext);
						futureList.add(fut2);
							
					}
					
					CompositeFuture.all(futureList).onComplete(ar2 -> {
						System.out.println("insertCommandList complete");
						
						if(ar2.succeeded()) {
							//System.out.println("success complete");
							promise.complete(missionId);
						}else {
							//System.out.println("fail complete");
							promise.fail("fail!");
						}
						
					});
					
					return promise.future();
				}).compose(missionId ->{
					Promise<String> promise = Promise.promise();
					
					List<Future> futureList = new ArrayList<>();
					
					//AttributeList Table insert
					for(int i=0; i<ATTRIBUTE.size(); i++) {
						JSONObject insQueryId = ATTRIBUTE.get(i);//new JSONObject();
						insQueryId.put(COL_ID, DB_TYPE+"006");
						
						//System.out.println("insertAttributeList : "+insQueryId.toString());
						
						Future<String> fut2 = this.insertList(insQueryId, routingContext);
						futureList.add(fut2);
							
					}
					
					CompositeFuture.all(futureList).onComplete(ar2 -> {
						//System.out.println("insertAttributeList complete");
						
						if(ar2.succeeded()) {
							//System.out.println("success complete");
							promise.complete(missionId);
						}else {
							//System.out.println("fail complete");
							promise.fail("fail!");
						}
						
					});
					
					return promise.future();
				}).onSuccess(missionId->{
					
					JSONObject colId = new JSONObject();					
					colId.put(COL_ID, DB_TYPE+"010");
					colId.put("mission_id", missionId);					
					
					if(MISSION.get(0).get("comp_id") != null) {
						colId.put("comp_id", MISSION.get(0).get("comp_id").toString());
						System.out.println("insertMission comp_id : "+colId.get("comp_id").toString());
					}
					
					System.out.println("insertMission getMissions : "+colId.toString());
					
					//Mission Table Insert
					Future<JSONObject> fut2 = Future.future(promise -> eb.request("vertx.selectQuery", colId.toString(), reply -> {
						
						if(reply.succeeded()) {	
							
							try {
								JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
								JSONObject res =(JSONObject) result.get(0);
								String code = "";
								
								//쿼리 오류 확인
								if(res.get("code")!=null) {
									code= res.get("code").toString();
								}
								
								System.out.println("getMissions result : "+result.toString());
								System.out.println("getMissions res : "+res.toString());
								
								//JSONObject mission =(JSONObject) reply.result().body();
								//System.out.println("mission : "+mission.toString());
								
								//쿼리 오류 발생 시
								if( !"".equals(code) &&  !"10002".equals(code)) {
									logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
								}else if( !"".equals(code) && "10001".equals(code)){									
									logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found mission : "+missionId, MSMessageReturn.STAT_ERROR);
								}else {
									
									promise.complete(res);
									
								}
								
							} catch (ParseException e) {
								promise.fail("fail!");
								logger.error("failed parsing json insertMission");
								messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
							}
							
							
						}else {
							logger.error("failed executing inside vertx.selectQuery");							
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
						}
					}));
					
					
					fut2.compose(mission -> {
						Promise<List<Object>> promise = Promise.promise();
						
						colId.put(COL_ID, DB_TYPE+"011");
						
						eb.request("vertx.selectQuery", colId.toString(), reply -> {
							
							if(reply.succeeded()) {	
								
								try {
									JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
									JSONObject res =(JSONObject) result.get(0);
									String code = "";
									
									//쿼리 오류 확인
									if(res.get("code")!=null) {
										code= res.get("code").toString();
									}
									
									System.out.println("getCommandList result : "+result.toString());
									System.out.println("getCommandList res : "+res.toString());
									
									//JSONObject mission =(JSONObject) reply.result().body();
									//System.out.println("mission : "+mission.toString());
									
									//쿼리 오류 발생 시
									if( !"".equals(code) &&  !"10002".equals(code)) {
										logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
										msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
									}else if( !"".equals(code) && "10001".equals(code)){									
										logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
										msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found attributeslist : "+missionId, MSMessageReturn.STAT_ERROR);
									}else {
										
										List<Object> lst = new ArrayList<Object>();
										lst.add(mission);
										lst.add(result);
										
										promise.complete(lst);
										
									}
									
								} catch (ParseException e) {
									promise.fail("fail!");
									logger.error("failed parsing json insertMission");
									messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
								}
								
								
							}else {
								logger.error("failed executing inside vertx.selectQuery");							
								messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
							}
						});
						
						
						return promise.future();
						
					}).compose(objLst -> {
						Promise<List<Object>> promise = Promise.promise();
						
						colId.put(COL_ID, DB_TYPE+"012");
						
						eb.request("vertx.selectQuery", colId.toString(), reply -> {
							
							if(reply.succeeded()) {	
								
								try {
									JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
									JSONObject res =(JSONObject) result.get(0);
									String code = "";
									
									//쿼리 오류 확인
									if(res.get("code")!=null) {
										code= res.get("code").toString();
									}
									
									System.out.println("getAttributeList result : "+result.toString());
									System.out.println("getAttributeList res : "+res.toString());
									
									//JSONObject mission =(JSONObject) reply.result().body();
									//System.out.println("mission : "+mission.toString());
									
									//쿼리 오류 발생 시
									if( !"".equals(code) &&  !"10002".equals(code)) {
										logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
										msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
									}else if( !"".equals(code) && "10001".equals(code)){									
										logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
										msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found attributeslist : "+missionId, MSMessageReturn.STAT_ERROR);
									}else {
										
										objLst.add(result);
										
										promise.complete(objLst);
									}
									
								} catch (ParseException e) {
									promise.fail("fail!");
									logger.error("failed parsing json insertMission");
									messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
								}
								
								
							}else {
								logger.error("failed executing inside vertx.selectQuery");							
								messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
							}
						});
						
						
						return promise.future();
						
					}).onSuccess(objList->{
						
						JSONObject res = (JSONObject) objList.get(0);
						JSONArray commandArr = (JSONArray) objList.get(1);
						JSONArray attributeArr = (JSONArray) objList.get(2);
						
						System.out.println("mission res :"+res.toJSONString());
						System.out.println("commandArr res :"+commandArr.toJSONString());
						System.out.println("attributeArr res :"+attributeArr.toJSONString());
						
						List<Map<String, Object>> commands = new ArrayList<Map<String, Object>>();
						
						
						for(int i=0; i<commandArr.size(); i++) {
							Map<String, Object> map = new LinkedHashMap<>();
							
							JSONObject command = (JSONObject) commandArr.get(i);
							String comId = command.get("CMD_ID").toString();
							
							map.put("command", command.get("CMD_CODE").toString());
							
							for(int j=0; j<attributeArr.size(); j++) {
								
								JSONObject attribute = (JSONObject) attributeArr.get(j);
								String attrComId = attribute.get("CMD_ID").toString();
								
								if(attrComId.equals(comId)) {
									String attrNm= attribute.get("ATTR_NAME").toString();
									String attrVal = attribute.get("ATTR_VAL").toString();
									
									map.put(attrNm, attrVal);
								}
								
							}
							
							commands.add(map);
						}
						
						//조회 필드 순서 때문에 재입력함(JsonObject 형식은 순서X)
						Map<String, Object> result = new LinkedHashMap<>();
						
						result.put("mission_id", res.get("MISSION_ID"));
						result.put("mission_name", res.get("MISSION_NAME"));
						result.put("user_id", res.get("USER_ID"));
						result.put("roundtrip", res.get("ROUNDTRIP"));
						result.put("repeat", res.get("REPEAT"));
						result.put("is_grid", res.get("IS_GRID"));
						result.put("service_type", res.get("SERVICE_TYPE"));
						String gridString = "";
						if(res.get("GRID_DETAILS") != null && "".equals(res.get("GRID_DETAILS").toString())) {
							gridString = res.get("GRID_DETAILS").toString();
							Map<String,String> grid_details = (Map<String,String>) new Gson().fromJson(gridString, Map.class);	
							result.put("grid_details",grid_details);
						}
						result.put("total_time", res.get("TOTAL_TIME"));
						result.put("total_distance", res.get("TOTAL_DISTANCE"));
						result.put("count", commandArr.size()+"");//int -> string 변환
						result.put("commands", commands);
						result.put("created_at",res.get("CREATEDAT"));
						
						System.out.println("result res : "+result.toString());
						
						Gson gson = new Gson();
						
						System.out.println("result res2 : "+gson.toJson(result).toString());
						
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(gson.toJson(result));
						
					}).onFailure(objList->{
						logger.info("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
					});
					
				}).onFailure(missionId->{
					logger.info("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
					msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
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
	 * Deletes one query with specified id
	 * 
	 * @param routingContext
	 */
	private void deleteMission(RoutingContext routingContext) {
		
		logger.info("Entered deleteMission");
		JSONParser parser = new JSONParser();
		
		try {

			JsonObject json = routingContext.getBodyAsJson();
			//json.put(ADDR, "deleteMission");
			
			String mission_id = json.getString("mission_id");
			
			if( "".equals(mission_id) || mission_id == null) {
				logger.error("code : "+MSMessageReturn.ERR_MDT_PARAM_MISS_CODE+", message : "+MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+"mission_id");
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_MDT_PARAM_MISS_CODE, MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+"mission_id", MSMessageReturn.STAT_ERROR);
			}
			
			this.checkMSParam(json.toString(), routingContext, null);			
			
			
			System.out.println("deleteMission param : "+json.toString());
				
			JSONObject queryParam = new JSONObject();
			queryParam.put(COL_ID, DB_TYPE+"003");
			queryParam.put("mission_id", mission_id);
			
			Future<String> fut1 = Future.future(promise -> eb.request("vertx.selectQuery", queryParam.toString(), reply -> {
				
				if(reply.succeeded()) {
					
					JSONArray missions = null;					
					
					String res = reply.result().body().toString();
					
					System.out.println("res : "+res);					

					try {
						
						missions = (JSONArray) parser.parse(res);
						
						System.out.println("missions : "+missions.toJSONString());
						System.out.println("missions : "+missions.get(0));
						
						JSONObject queryRes = (JSONObject) missions.get(0);
						String queryResCd = "";
						if(queryRes.get("code") != null) {
							queryResCd = queryRes.get("code").toString();
						}
						
						//정상 조회 시
						if( "".equals(queryResCd) && missions.size() >= 0 ) {
							promise.complete(mission_id);
						}
						//쿼리 결과만 리턴 되고 그 결과가 정상(10002)이 아닐 때
						else if( !"10002".equals(queryResCd) ) {
							logger.error("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR);
							promise.fail(mission_id);
						}
						
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						logger.error("failed parsing json deleteMission");
						promise.fail(e.getCause());
					}
					
				} else {
	
					logger.error("failed executing inside vertx.queryManage");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
				}
			}));
			
			
			fut1.compose(result ->{
				Promise<String> promise = Promise.promise();
				
				JSONObject queryParam2 = new JSONObject();
				queryParam2.put(COL_ID, DB_TYPE+"007");
				queryParam2.put("mission_id", result);
				
				System.out.println("DeleteAttributes param : "+queryParam2.toString());
				
				//DeleteAttributes
				eb.request("vertx.selectQuery", queryParam2.toString(), reply -> {
					
					if(reply.succeeded()) {		
						
						JSONArray attributes = null;
						
						String res = reply.result().body().toString();
						
						System.out.println("res : "+res);
						
						try {
							
							attributes = (JSONArray) parser.parse(res);
							
							System.out.println("attributes : "+attributes.toJSONString());
							System.out.println("attributes : "+attributes.get(0));
							
							JSONObject queryRes = (JSONObject) attributes.get(0);
							String queryResCd = queryRes.get("code").toString();
							
							//정상 삭제 시
							if( "10002".equals(queryResCd) ) {
								logger.info("DeleteAttributes Success : "+result);								
								promise.complete(mission_id);
							}else {
								logger.error("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR);
								promise.fail(mission_id);
							}
							
						} catch (ParseException e) {
							// TODO Auto-generated catch block
							logger.error("failed parsing json deleteMission");
							promise.fail(e.getCause());
						}
						
						//promise.complete(result);
						
					} else {
		
						logger.error("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR);
						promise.fail(result);
					}
				});				
				
				return promise.future();
				
			}).compose(result ->{
				Promise<String> promise = Promise.promise();
				
				JSONObject queryParam2 = new JSONObject();
				queryParam2.put(COL_ID, DB_TYPE+"008");
				queryParam2.put("mission_id", result);
				
				System.out.println("DeleteCommands param : "+queryParam2.toString());
				
				//DeleteCommands
				eb.request("vertx.selectQuery", queryParam2.toString(), reply -> {
					
					if(reply.succeeded()) {		
						
						JSONArray commands = null;
						
						String res = reply.result().body().toString();
						
						System.out.println("res : "+res);
						
						try {
							
							commands = (JSONArray) parser.parse(res);
							
							System.out.println("commands : "+commands.toJSONString());
							System.out.println("commands : "+commands.get(0));
							
							JSONObject queryRes = (JSONObject) commands.get(0);
							String queryResCd = queryRes.get("code").toString();
							
							//정상 삭제 시
							if( "10002".equals(queryResCd) ) {
								logger.info("DeleteCommands Success : "+result);								
								promise.complete(mission_id);
							}else {
								logger.error("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR);
								promise.fail(mission_id);
							}
							
						} catch (ParseException e) {
							// TODO Auto-generated catch block
							logger.error("failed parsing json deleteMission");
							promise.fail(e.getCause());
						}
						
						//promise.complete(result);
						
					} else {
		
						logger.error("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR);
						promise.fail(result);
					}
				});
				
				return promise.future();
				
			}).compose(result ->{
				Promise<String> promise = Promise.promise();
				
				JSONObject queryParam2 = new JSONObject();
				queryParam2.put(COL_ID, DB_TYPE+"009");
				queryParam2.put("mission_id", result);
				
				System.out.println("DeleteMission param : "+queryParam2.toString());
				
				//DeleteMission
				eb.request("vertx.selectQuery", queryParam2.toString(), reply -> {
					
					if(reply.succeeded()) {		
						
						JSONArray missions = null;
						
						String res = reply.result().body().toString();
						
						System.out.println("res : "+res);
						
						try {
							
							missions = (JSONArray) parser.parse(res);
							
							System.out.println("missions : "+missions.toJSONString());
							System.out.println("missions : "+missions.get(0));
							
							JSONObject queryRes = (JSONObject) missions.get(0);
							String queryResCd = queryRes.get("code").toString();
							
							//정상 삭제 시
							if( "10002".equals(queryResCd) ) {
								logger.info("DeleteMission Success : "+result);								
								promise.complete(mission_id);
							}else {
								logger.error("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR);
								promise.fail(mission_id);
							}
							
						} catch (ParseException e) {
							// TODO Auto-generated catch block
							logger.error("failed parsing json deleteMission");
							promise.fail(e.getCause());
						}
						
						//promise.complete(result);
						
					} else {
		
						logger.error("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR) ;
						promise.fail(result);
					}
				});				
								
				return promise.future();
				
			}).onSuccess(reslut ->{				
				logger.info("code : "+MSMessageReturn.SUCCESS_CODE+", message : "+MSMessageReturn.SUCCESS_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.SUCCESS_CODE, MSMessageReturn.SUCCESS_MSG, MSMessageReturn.STAT_SUCCESS);
			}).onFailure(result ->{
				logger.info("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR);
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
			
			if(json.isEmpty() || json.getString("comp_id") == null ) {
				routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
				.end("");
			}
		
			logger.info("attempting to connect to vertx.queryManage verticle");
			json.put(COL_ID, DB_TYPE+"000");
			//json.put("isXML", true);
			
			boolean isXML = false;			
			
			Future<JSONArray> fut1 = Future.future(promise -> eb.request("vertx.selectQuery", json.toString(), reply -> {
				
				
				if(reply.succeeded()) {
					
					JSONParser parser = new JSONParser();
					JSONArray missions = null;
					JSONObject result = new JSONObject();
					
					String res = reply.result().body().toString();
					
					System.out.println("res : "+res);
					
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
				
				json.put(COL_ID, DB_TYPE+"001");
				
				Promise<Void> promise = Promise.promise();
				
				eb.request("vertx.selectQuery", json.toString(), reply -> {
					
					if(reply.succeeded()) {
						
						JSONParser parser = new JSONParser();
						JSONArray attrs = null;
						JSONObject result = new JSONObject();
						
						String res = reply.result().body().toString();
						
						System.out.println("res2 : "+res);
						
						try {
							
							attrs = (JSONArray) parser.parse(res);
							
							System.out.println("attrs : "+attrs.toJSONString());
							System.out.println("missons2 : "+missions.toJSONString());
							
							List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
							
							for (int i = 0; i < missions.size(); i++) {
								
								JSONObject mission = (JSONObject) missions.get(i);
								
								//조회 결과가 없으면
								if(mission.get("code") != null && "10001".equals(mission.get("code").toString())) {
									
									result.put("count", "0");
									result.put("missions", list);
									
									
								}else {
									for (int j = 0; j < attrs.size(); j++) {
										
										JSONObject attr = (JSONObject) attrs.get(j);
										
										if(mission.get("MISSION_ID") != null && mission.get("MISSION_ID").equals(attr.get("MISSION_ID"))) {
											
											Integer attr_code = Integer.parseInt(attr.get("ATTR_CODE").toString()) ;
											
											if(attr_code == 16001)
												mission.put("lat",attr.get("ATTR_VAL"));
											else if (attr_code == 16002)
												mission.put("lon",attr.get("ATTR_VAL"));
											
											mission.remove("roundtrip");
											mission.remove("repeat");
											mission.remove("is_grid");
											mission.remove("grid_details");
											mission.remove("total_time");
											mission.remove("total_distance");
										}
										
									}
									
									Map<String, Object> rstl = new LinkedHashMap<>();
									
									rstl.put("mission_name", mission.get("MISSION_NAME"));
									rstl.put("service_type", mission.get("SERVICE_TYPE"));
									rstl.put("user_id", mission.get("USER_ID"));
									rstl.put("mission_id", mission.get("MISSION_ID"));
									rstl.put("created_at",mission.get("CREATEDAT"));
									rstl.put("lon", mission.get("lon"));
									rstl.put("comp_id", mission.get("COMP_ID"));
									rstl.put("lat", mission.get("lat"));
									
									list.add(rstl);
									
									System.out.println("missons3 : "+list.toString());
									
									
									result.put("count", missions.size()+"");
									result.put("missions", list);
								}
								
							}

							routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
							.end(result.toString());

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
	
	/**
	 * Select one query data with specified id
	 * 
	 * @param routingContext
	 */
	private void getMissionToUpload(RoutingContext routingContext) {

		logger.info("Entered getMissionToUpload");
		JSONParser parser = new JSONParser();
		
		try {
			//JsonObject json = routingContext.getBodyAsJson();
			//json.put(ADDR, "getMissionDetail");
			
			String missionId = routingContext.pathParam("mission_id");
			String compId = routingContext.pathParam("company_id");
			
			if( "".equals(missionId) || missionId == null) {
				logger.error("code : "+MSMessageReturn.ERR_MDT_PARAM_MISS_CODE+", message : "+MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+"mission_id");
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_MDT_PARAM_MISS_CODE, MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+"mission_id", MSMessageReturn.STAT_ERROR);
			}
	
			JSONObject colId = new JSONObject();					
			colId.put(COL_ID, DB_TYPE+"010");
			colId.put("mission_id", missionId);
			colId.put("comp_id", compId);
			
			System.out.println("getMissionToUpload getMissions : "+colId.toString());
			
			//Mission Table Insert
			Future<JSONObject> fut2 = Future.future(promise -> eb.request("vertx.selectQuery", colId.toString(), reply -> {
				
				if(reply.succeeded()) {	
					
					try {
						System.out.println("getMissions reply result : "+reply.result().body().toString());
						JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
						JSONObject res =(JSONObject) result.get(0);
						String code = "";
						
						//쿼리 오류 확인
						if(res.get("code")!=null) {
							code= res.get("code").toString();
						}
						
						System.out.println("getMissions result : "+result.toString());
						System.out.println("getMissions res : "+res.toString());
						
						//JSONObject mission =(JSONObject) reply.result().body();
						//System.out.println("mission : "+mission.toString());
						
						//쿼리 오류 발생 시
						if( !"".equals(code) &&  !"10002".equals(code)) {
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
						}else if( !"".equals(code) && "10001".equals(code)){									
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found mission : "+missionId, MSMessageReturn.STAT_ERROR);
						}else {
							
							promise.complete(res);
							
						}
						
					} catch (ParseException e) {
						promise.fail("fail!");
						logger.error("failed parsing json insertMission");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
					
					
				}else {
					logger.error("failed executing inside vertx.selectQuery");							
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
				}
			}));
			
			
			fut2.compose(mission -> {
				Promise<List<Object>> promise = Promise.promise();
				
				colId.put(COL_ID, DB_TYPE+"012");
				
				eb.request("vertx.selectQuery", colId.toString(), reply -> {
					
					if(reply.succeeded()) {	
						
						try {
							JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
							JSONObject res =(JSONObject) result.get(0);
							String code = "";
							
							//쿼리 오류 확인
							if(res.get("code")!=null) {
								code= res.get("code").toString();
							}
							
							System.out.println("getAttributeList result : "+result.toString());
							System.out.println("getAttributeList res : "+res.toString());
							
							//JSONObject mission =(JSONObject) reply.result().body();
							//System.out.println("mission : "+mission.toString());
							
							//쿼리 오류 발생 시
							if( !"".equals(code) &&  !"10002".equals(code)) {
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
							}else if( !"".equals(code) && "10001".equals(code)){									
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found mission : "+missionId, MSMessageReturn.STAT_ERROR);
							}else {
								
								List<Object> lst = new ArrayList<Object>();
								lst.add(mission);
								
								
								LinkedHashMap<String, ArrayList<ArrayList<String>>> mapAttrList = new LinkedHashMap<String, ArrayList<ArrayList<String>>>(); 
								
								for(int i=0; i<result.size(); i++) {
									JSONObject attrRow =(JSONObject) result.get(i);
									ArrayList<String> arrayAttrValues = new ArrayList<String>();
									
									//ArrayList<String> resKeys = IteratorUtils.toArrayList(attrRow.keySet().iterator(),attrRow.size());
									ArrayList<String> resVals = IteratorUtils.toArrayList(attrRow.values().iterator(),attrRow.size());
									
									for(int j=0; j<attrRow.size(); j++) {										
										arrayAttrValues.add(String.valueOf(resVals.get(j)));										
									}
									
									ArrayList<ArrayList<String>> arrayAttrGroup = new ArrayList<ArrayList<String>>();
									String strCmdId = "";									
									if(attrRow.get("CMD_ID") != null) {
										strCmdId  = attrRow.get("CMD_ID").toString();
									}
									boolean bExist = mapAttrList.containsKey(strCmdId);
									if(!bExist) {
										// 신규로 값을 추가
										arrayAttrGroup.add(arrayAttrValues);
									}else {
										// 기존의 값에 추가하여 다시 LinkedHashMap에 설정한다.
										arrayAttrGroup = mapAttrList.get(strCmdId);
										arrayAttrGroup.add(arrayAttrValues);
									}
									mapAttrList.put(strCmdId, arrayAttrGroup);
								}
								
								lst.add(mapAttrList);
								
								
								promise.complete(lst);
							}
							
						} catch (ParseException e) {
							promise.fail("fail!");
							logger.error("failed parsing json insertMission");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
						
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");							
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
				});
				
				
				return promise.future();
				
			}).compose(objLst -> {
				Promise<List<Object>> promise = Promise.promise();
				
				colId.put(COL_ID, DB_TYPE+"013");
				colId.put("CMD_TYPE", "1");
				
				eb.request("vertx.selectQuery", colId.toString(), reply -> {
					
					if(reply.succeeded()) {	
						
						try {							
							JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
							JSONObject res =(JSONObject) result.get(0);
														
							String code = "";
							
							//쿼리 오류 확인
							if(res.get("code")!=null) {
								code= res.get("code").toString();
							}
							
							System.out.println("getAttributesByType result : "+result.toString());
							System.out.println("getAttributesByType res : "+res.toString());
							
							//JSONObject mission =(JSONObject) reply.result().body();
							//System.out.println("mission : "+mission.toString());
							
							//쿼리 오류 발생 시
							if( !"".equals(code) &&  !"10002".equals(code)) {
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
							}else if( !"".equals(code) && "10001".equals(code)){									
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found attributesByType : "+missionId, MSMessageReturn.STAT_ERROR);
							}else {
								
								// command_type = 1을 조건으로 Drone Command의 attribute
								LinkedHashMap<String, ArrayList<ArrayList<String>>> mapDroneAttrList = new LinkedHashMap<String, ArrayList<ArrayList<String>>>();								// 
								LinkedHashMap<String, ArrayList<String>> mapDroneAttrList2 = new LinkedHashMap<String, ArrayList<String>>();
								
								Map<String,Map<String,String>> attrMap = new HashMap<String,Map<String,String>>();
								
								// 모든 attribute를 cmd_code key로 map에 저장
								Map<String,String> attr = null;
								
								for(int i=0; i<result.size(); i++) {
									JSONObject attrRow =(JSONObject) result.get(i);									
									ArrayList<String> arrayAttrValues = new ArrayList<String>();
									ArrayList<String> arrayAttrValues2 = new ArrayList<String>();
									
									ArrayList<String> resKeys = IteratorUtils.toArrayList(attrRow.keySet().iterator(),attrRow.size());
									ArrayList<String> resVals = IteratorUtils.toArrayList(attrRow.values().iterator(),attrRow.size());
									
									String strAttrCode = "";
									String strCmdCode = "";
									String strAttrName = "";
									String strAttrType = "";
									
									for(int j=0; j<attrRow.size(); j++) {
										
										arrayAttrValues.add(String.valueOf(resVals.get(j)));
										
										if( "ATTR_CODE".equals(resKeys.get(j).toString()) ) {
											strAttrCode = String.valueOf(resVals.get(j));
										}else if("CMD_CODE".equals(resKeys.get(j).toString())) {
											strCmdCode = String.valueOf(resVals.get(j));
										}else if("ATTR_NAME".equals(resKeys.get(j).toString())) {
											strAttrName = String.valueOf(resVals.get(j));
										}else if("ATTR_TYPE".equals(resKeys.get(j).toString())) {
											strAttrType = String.valueOf(resVals.get(j));
										}
										
									}
									
									arrayAttrValues2.add(strCmdCode);
									arrayAttrValues2.add(strAttrName);
									
									mapDroneAttrList2.put(strAttrCode, arrayAttrValues2);
									
									attr = attrMap.get(strCmdCode);
									
									if(attr == null) attr = new HashMap<String,String>();
									
									attr.put(strAttrName, strAttrType);
									
									attrMap.put(strCmdCode, attr);
									
									
									ArrayList<ArrayList<String>> arrayAttrGroup = new ArrayList<ArrayList<String>>();
									String cmdCode = "";									
									if(attrRow.get("CMD_CODE") != null) {
										cmdCode  = attrRow.get("CMD_CODE").toString();
									}
									boolean bExist = mapDroneAttrList.containsKey(cmdCode);
									if(!bExist) {
										// 신규로 값을 추가
										arrayAttrGroup.add(arrayAttrValues);
									}else {
										// 기존의 값에 추가하여 다시 LinkedHashMap에 설정한다.
										arrayAttrGroup = mapDroneAttrList.get(cmdCode);
										arrayAttrGroup.add(arrayAttrValues);
									}
									mapDroneAttrList.put(cmdCode, arrayAttrGroup);
								}
								
								objLst.add(mapDroneAttrList);
								objLst.add(mapDroneAttrList2);
								objLst.add(attrMap);
								
								promise.complete(objLst);
								
							}
							
						} catch (ParseException e) {
							promise.fail("fail!");
							logger.error("failed parsing json insertMission");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
						
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");							
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
				});
				
				
				return promise.future();
				
			}).compose(objLst -> {
				Promise<List<Object>> promise = Promise.promise();
				
				colId.put(COL_ID, DB_TYPE+"011");
				
				eb.request("vertx.selectQuery", colId.toString(), reply -> {
					
					if(reply.succeeded()) {	
						
						try {
							JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
							JSONObject res =(JSONObject) result.get(0);
							String code = "";
							
							//쿼리 오류 확인
							if(res.get("code")!=null) {
								code= res.get("code").toString();
							}
							
							System.out.println("getCommandList result : "+result.toString());
							System.out.println("getCommandList res : "+res.toString());
							
							//JSONObject mission =(JSONObject) reply.result().body();
							//System.out.println("mission : "+mission.toString());
							
							//쿼리 오류 발생 시
							if( !"".equals(code) &&  !"10002".equals(code)) {
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
							}else if( !"".equals(code) && "10001".equals(code)){									
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found attributeslist : "+missionId, MSMessageReturn.STAT_ERROR);
							}else {
								
								LinkedHashMap<String, ArrayList<String>> mapCmdList = new LinkedHashMap<String, ArrayList<String>>();
								// Json parse 시 순서 변경으로 인텍스 확인 
								//int cmdCode = 0;
								
								
								for(int i=0; i<result.size(); i++) {
									JSONObject cmdRow =(JSONObject) result.get(i);
									ArrayList<String> arrayCmdValues = new ArrayList<String>();
									
									ArrayList<String> resKeys = IteratorUtils.toArrayList(cmdRow.keySet().iterator(),cmdRow.size());
									ArrayList<String> resVals = IteratorUtils.toArrayList(cmdRow.values().iterator(),cmdRow.size());
									//int cmdId = 1;
									
									
									for(int j=0; j<cmdRow.size(); j++) {
										
										arrayCmdValues.add( String.valueOf(resVals.get(j)) );
										
										/*if( "CMD_ID".equals(resKeys.get(j).toString()) ) {
											cmdId = j;
										}else if( "CMD_CODE".equals(resKeys.get(j).toString()) ) {
											cmdCode = j;
										}*/
									}
									
									//mapCmdList.put(arrayCmdValues.get(cmdId), arrayCmdValues);
									mapCmdList.put(arrayCmdValues.get(1), arrayCmdValues);
								}
								
								objLst.add(mapCmdList);
								//objLst.add(cmdCode);
								
								promise.complete(objLst);
								
							}
							
						} catch (ParseException e) {
							promise.fail("fail!");
							logger.error("failed parsing json insertMission");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
						
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");							
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
				});
				
				
				return promise.future();
				
			}).onSuccess(objList->{
				
				Map<String, JsonArray> result = createWaypoints(routingContext, objList, null, null);
				
				routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
				.end(new Gson().toJson(result).replace("\\", ""));
				
			}).onFailure(objList->{
				logger.info("code : "+MSMessageReturn.ERR_CREATE_CODE+", message : "+MSMessageReturn.ERR_CREATE_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_CREATE_CODE, MSMessageReturn.ERR_CREATE_MSG, MSMessageReturn.STAT_ERROR);
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
	
	private void getReverseMissionToUpload(RoutingContext routingContext) {

		logger.info("Entered getReverseMissionToUpload");
		JSONParser parser = new JSONParser();
		
		try {
			JsonObject json = routingContext.getBodyAsJson();
			json.put(ADDR, "getReverseMissionToUpload");
			
			String missionId = json.getString("mission_id");
			String compId = json.getString("comp_id");
			String waypointNum = json.getString("waypoint_num");
			String direction = json.getString("direction");
			
			if( "".equals(missionId) || missionId == null) {
				logger.error("code : "+MSMessageReturn.ERR_MDT_PARAM_MISS_CODE+", message : "+MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+"mission_id");
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_MDT_PARAM_MISS_CODE, MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+"mission_id", MSMessageReturn.STAT_ERROR);
			}
	
			JSONObject colId = new JSONObject();					
			colId.put(COL_ID, DB_TYPE+"010");
			colId.put("mission_id", missionId);
			colId.put("comp_id", compId);
			
			System.out.println("getReverseMissionToUpload getMissions : "+colId.toString());
			
			//Mission Table Insert
			Future<JSONObject> fut2 = Future.future(promise -> eb.request("vertx.selectQuery", colId.toString(), reply -> {
				
				if(reply.succeeded()) {	
					
					try {
						System.out.println("getMissions reply result : "+reply.result().body().toString());
						JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
						JSONObject res =(JSONObject) result.get(0);
						String code = "";
						
						//쿼리 오류 확인
						if(res.get("code")!=null) {
							code= res.get("code").toString();
						}
						
						System.out.println("getMissions result : "+result.toString());
						System.out.println("getMissions res : "+res.toString());
						
						//JSONObject mission =(JSONObject) reply.result().body();
						//System.out.println("mission : "+mission.toString());
						
						//쿼리 오류 발생 시
						if( !"".equals(code) &&  !"10002".equals(code)) {
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
						}else if( !"".equals(code) && "10001".equals(code)){									
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found mission : "+missionId, MSMessageReturn.STAT_ERROR);
						}else {
							
							promise.complete(res);
							
						}
						
					} catch (ParseException e) {
						promise.fail("fail!");
						logger.error("failed parsing json insertMission");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
					
					
				}else {
					logger.error("failed executing inside vertx.selectQuery");							
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
				}
			}));
			
			
			fut2.compose(mission -> {
				Promise<List<Object>> promise = Promise.promise();
				
				colId.put(COL_ID, DB_TYPE+"012");
				
				eb.request("vertx.selectQuery", colId.toString(), reply -> {
					
					if(reply.succeeded()) {	
						
						try {
							JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
							JSONObject res =(JSONObject) result.get(0);
							String code = "";
							
							//쿼리 오류 확인
							if(res.get("code")!=null) {
								code= res.get("code").toString();
							}
							
							System.out.println("getAttributeList result : "+result.toString());
							System.out.println("getAttributeList res : "+res.toString());
							
							//JSONObject mission =(JSONObject) reply.result().body();
							//System.out.println("mission : "+mission.toString());
							
							//쿼리 오류 발생 시
							if( !"".equals(code) &&  !"10002".equals(code)) {
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
							}else if( !"".equals(code) && "10001".equals(code)){									
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found mission : "+missionId, MSMessageReturn.STAT_ERROR);
							}else {
								
								List<Object> lst = new ArrayList<Object>();
								lst.add(mission);
								
								
								LinkedHashMap<String, ArrayList<ArrayList<String>>> mapAttrList = new LinkedHashMap<String, ArrayList<ArrayList<String>>>(); 
								
								for(int i=0; i<result.size(); i++) {
									JSONObject attrRow =(JSONObject) result.get(i);
									ArrayList<String> arrayAttrValues = new ArrayList<String>();
									
									//ArrayList<String> resKeys = IteratorUtils.toArrayList(attrRow.keySet().iterator(),attrRow.size());
									ArrayList<String> resVals = IteratorUtils.toArrayList(attrRow.values().iterator(),attrRow.size());
									
									for(int j=0; j<attrRow.size(); j++) {										
										arrayAttrValues.add(String.valueOf(resVals.get(j)));										
									}
									
									ArrayList<ArrayList<String>> arrayAttrGroup = new ArrayList<ArrayList<String>>();
									String strCmdId = "";									
									if(attrRow.get("CMD_ID") != null) {
										strCmdId  = attrRow.get("CMD_ID").toString();
									}
									boolean bExist = mapAttrList.containsKey(strCmdId);
									if(!bExist) {
										// 신규로 값을 추가
										arrayAttrGroup.add(arrayAttrValues);
									}else {
										// 기존의 값에 추가하여 다시 LinkedHashMap에 설정한다.
										arrayAttrGroup = mapAttrList.get(strCmdId);
										arrayAttrGroup.add(arrayAttrValues);
									}
									mapAttrList.put(strCmdId, arrayAttrGroup);
								}
								
								lst.add(mapAttrList);
								
								
								promise.complete(lst);
							}
							
						} catch (ParseException e) {
							promise.fail("fail!");
							logger.error("failed parsing json insertMission");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
						
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");							
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
				});
				
				
				return promise.future();
				
			}).compose(objLst -> {
				Promise<List<Object>> promise = Promise.promise();
				
				colId.put(COL_ID, DB_TYPE+"013");
				colId.put("CMD_TYPE", "1");
				
				eb.request("vertx.selectQuery", colId.toString(), reply -> {
					
					if(reply.succeeded()) {	
						
						try {							
							JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
							JSONObject res =(JSONObject) result.get(0);
														
							String code = "";
							
							//쿼리 오류 확인
							if(res.get("code")!=null) {
								code= res.get("code").toString();
							}
							
							System.out.println("getAttributesByType result : "+result.toString());
							System.out.println("getAttributesByType res : "+res.toString());
							
							//JSONObject mission =(JSONObject) reply.result().body();
							//System.out.println("mission : "+mission.toString());
							
							//쿼리 오류 발생 시
							if( !"".equals(code) &&  !"10002".equals(code)) {
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
							}else if( !"".equals(code) && "10001".equals(code)){									
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found attributesByType : "+missionId, MSMessageReturn.STAT_ERROR);
							}else {
								
								// command_type = 1을 조건으로 Drone Command의 attribute
								LinkedHashMap<String, ArrayList<ArrayList<String>>> mapDroneAttrList = new LinkedHashMap<String, ArrayList<ArrayList<String>>>();								// 
								LinkedHashMap<String, ArrayList<String>> mapDroneAttrList2 = new LinkedHashMap<String, ArrayList<String>>();
								
								Map<String,Map<String,String>> attrMap = new HashMap<String,Map<String,String>>();
								
								// 모든 attribute를 cmd_code key로 map에 저장
								Map<String,String> attr = null;
								
								for(int i=0; i<result.size(); i++) {
									JSONObject attrRow =(JSONObject) result.get(i);									
									ArrayList<String> arrayAttrValues = new ArrayList<String>();
									ArrayList<String> arrayAttrValues2 = new ArrayList<String>();
									
									ArrayList<String> resKeys = IteratorUtils.toArrayList(attrRow.keySet().iterator(),attrRow.size());
									ArrayList<String> resVals = IteratorUtils.toArrayList(attrRow.values().iterator(),attrRow.size());
									
									String strAttrCode = "";
									String strCmdCode = "";
									String strAttrName = "";
									String strAttrType = "";
									
									for(int j=0; j<attrRow.size(); j++) {
										
										arrayAttrValues.add(String.valueOf(resVals.get(j)));
										
										if( "ATTR_CODE".equals(resKeys.get(j).toString()) ) {
											strAttrCode = String.valueOf(resVals.get(j));
										}else if("CMD_CODE".equals(resKeys.get(j).toString())) {
											strCmdCode = String.valueOf(resVals.get(j));
										}else if("ATTR_NAME".equals(resKeys.get(j).toString())) {
											strAttrName = String.valueOf(resVals.get(j));
										}else if("ATTR_TYPE".equals(resKeys.get(j).toString())) {
											strAttrType = String.valueOf(resVals.get(j));
										}
										
									}
									
									arrayAttrValues2.add(strCmdCode);
									arrayAttrValues2.add(strAttrName);
									
									mapDroneAttrList2.put(strAttrCode, arrayAttrValues2);
									
									attr = attrMap.get(strCmdCode);
									
									if(attr == null) attr = new HashMap<String,String>();
									
									attr.put(strAttrName, strAttrType);
									
									attrMap.put(strCmdCode, attr);
									
									
									ArrayList<ArrayList<String>> arrayAttrGroup = new ArrayList<ArrayList<String>>();
									String cmdCode = "";									
									if(attrRow.get("CMD_CODE") != null) {
										cmdCode  = attrRow.get("CMD_CODE").toString();
									}
									boolean bExist = mapDroneAttrList.containsKey(cmdCode);
									if(!bExist) {
										// 신규로 값을 추가
										arrayAttrGroup.add(arrayAttrValues);
									}else {
										// 기존의 값에 추가하여 다시 LinkedHashMap에 설정한다.
										arrayAttrGroup = mapDroneAttrList.get(cmdCode);
										arrayAttrGroup.add(arrayAttrValues);
									}
									mapDroneAttrList.put(cmdCode, arrayAttrGroup);
								}
								
								objLst.add(mapDroneAttrList);
								objLst.add(mapDroneAttrList2);
								objLst.add(attrMap);
								
								promise.complete(objLst);
								
							}
							
						} catch (ParseException e) {
							promise.fail("fail!");
							logger.error("failed parsing json insertMission");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
						
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");							
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
				});
				
				
				return promise.future();
				
			}).compose(objLst -> {
				Promise<List<Object>> promise = Promise.promise();
				
				colId.put(COL_ID, DB_TYPE+"011");
				
				eb.request("vertx.selectQuery", colId.toString(), reply -> {
					
					if(reply.succeeded()) {	
						
						try {
							JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
							JSONObject res =(JSONObject) result.get(0);
							String code = "";
							
							//쿼리 오류 확인
							if(res.get("code")!=null) {
								code= res.get("code").toString();
							}
							
							System.out.println("getCommandList result : "+result.toString());
							System.out.println("getCommandList res : "+res.toString());
							
							//JSONObject mission =(JSONObject) reply.result().body();
							//System.out.println("mission : "+mission.toString());
							
							//쿼리 오류 발생 시
							if( !"".equals(code) &&  !"10002".equals(code)) {
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
							}else if( !"".equals(code) && "10001".equals(code)){									
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found attributeslist : "+missionId, MSMessageReturn.STAT_ERROR);
							}else {
								
								LinkedHashMap<String, ArrayList<String>> mapCmdList = new LinkedHashMap<String, ArrayList<String>>();
								// Json parse 시 순서 변경으로 인텍스 확인 
								//int cmdCode = 0;
								
								
								for(int i=0; i<result.size(); i++) {
									JSONObject cmdRow =(JSONObject) result.get(i);
									ArrayList<String> arrayCmdValues = new ArrayList<String>();
									
									ArrayList<String> resKeys = IteratorUtils.toArrayList(cmdRow.keySet().iterator(),cmdRow.size());
									ArrayList<String> resVals = IteratorUtils.toArrayList(cmdRow.values().iterator(),cmdRow.size());
									//int cmdId = 1;
									
									
									for(int j=0; j<cmdRow.size(); j++) {
										
										arrayCmdValues.add( String.valueOf(resVals.get(j)) );
										
										/*if( "CMD_ID".equals(resKeys.get(j).toString()) ) {
											cmdId = j;
										}else if( "CMD_CODE".equals(resKeys.get(j).toString()) ) {
											cmdCode = j;
										}*/
									}
									
									//mapCmdList.put(arrayCmdValues.get(cmdId), arrayCmdValues);
									mapCmdList.put(arrayCmdValues.get(1), arrayCmdValues);
								}
								
								objLst.add(mapCmdList);
								//objLst.add(cmdCode);
								
								promise.complete(objLst);
								
							}
							
						} catch (ParseException e) {
							promise.fail("fail!");
							logger.error("failed parsing json insertMission");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
						
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");							
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
				});
				
				
				return promise.future();
				
			}).onSuccess(objList->{
				
				
				Map<String, JsonArray> result = createWaypoints(routingContext, objList, direction, waypointNum);
				
				routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
				.end(new Gson().toJson(result).replace("\\", ""));
				
			}).onFailure(objList->{
				logger.info("code : "+MSMessageReturn.ERR_CREATE_CODE+", message : "+MSMessageReturn.ERR_CREATE_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_CREATE_CODE, MSMessageReturn.ERR_CREATE_MSG, MSMessageReturn.STAT_ERROR);
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
	
	private void getServiceTypeList(RoutingContext routingContext) {

		logger.info("Entered getServiceTypeList");
		JSONParser parser = new JSONParser();
		
		try {
	
			JSONObject colId = new JSONObject();					
			colId.put(COL_ID, DB_TYPE+"014");
			
			System.out.println("getServiceTypeList : "+colId.toString());
			
			eb.request("vertx.selectQuery", colId.toString(), reply -> {
				
				if(reply.succeeded()) {	
					
					try {
						System.out.println("getServiceTypeList reply result : "+reply.result().body().toString());
						JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
						JSONObject res =(JSONObject) result.get(0);
						String code = "";
						
						//쿼리 오류 확인
						if(res.get("code")!=null) {
							code= res.get("code").toString();
						}
						
						System.out.println("getServiceTypeList result : "+result.toString());
						System.out.println("getServiceTypeList res : "+res.toString());
						
						//JSONObject mission =(JSONObject) reply.result().body();
						//System.out.println("mission : "+mission.toString());
						
						//쿼리 오류 발생 시
						if( !"".equals(code) &&  !"10002".equals(code)) {
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
						}else if( !"".equals(code) && "10001".equals(code)){									
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found service type : ", MSMessageReturn.STAT_ERROR);
						}else {
							
							List<Map<String, String>> lst = new ArrayList<Map<String, String>>();
							
							Map<String, String> map;
							
							for(int i=0; i<result.size(); i++) {
								
								res =(JSONObject) result.get(i);
								
								ArrayList<String> resKeys = IteratorUtils.toArrayList(res.keySet().iterator(),res.size());
								ArrayList<String> resVals = IteratorUtils.toArrayList(res.values().iterator(),res.size());								
								
								map = new HashMap<>();
								
								for(int j=0; j<res.size(); j++) {
									
									if("SVC_TYPE_CODE".equals(resKeys.get(j).toString())) {
										map.put("service_type_id", String.valueOf(resVals.get(j)));
									}else if("SVC_TYPE_NAME".equals(resKeys.get(j).toString())) {
										map.put("service_type_name", String.valueOf(resVals.get(j)));
									}									
																		
								}
								
								lst.add(map);
																
							}
							
							
							Map<String, Object> rslt = new HashMap<>();
							
							rslt.put("count", String.valueOf(result.size()));
							rslt.put("service_types", lst);
							
							routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
							.end(new Gson().toJson(rslt));
							
						}
						
					} catch (ParseException e) {
						logger.error("failed parsing json insertMission");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
					
					
				}else {
					logger.error("failed executing inside vertx.selectQuery");							
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
	
	private void getCommandByServiceType(RoutingContext routingContext) {

		logger.info("Entered getCommandByServiceType");
		JSONParser parser = new JSONParser();
		
		try {
			
			JsonObject json = routingContext.getBodyAsJson();
			json.put(ADDR, "getCommandByService");
			
			String svcType = json.getString("service_type");
	
			JSONObject colId = new JSONObject();					
			colId.put(COL_ID, DB_TYPE+"016");
			colId.put("service_type", svcType);
			
			System.out.println("getCommandByServiceType : "+colId.toString());
			
			Future<List<Object>> fut2 = Future.future(promise -> eb.request("vertx.selectQuery", colId.toString(), reply -> {
				
				if(reply.succeeded()) {	
					
					try {
						System.out.println("getCommandByServiceType reply result : "+reply.result().body().toString());
						JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
						JSONObject res =(JSONObject) result.get(0);
						String code = "";
						
						//쿼리 오류 확인
						if(res.get("code")!=null) {
							code= res.get("code").toString();
						}
						
						System.out.println("getCommandByServiceType result : "+result.toString());
						System.out.println("getCommandByServiceType res : "+res.toString());
						
						//JSONObject mission =(JSONObject) reply.result().body();
						//System.out.println("mission : "+mission.toString());
						
						//쿼리 오류 발생 시
						if( !"".equals(code) &&  !"10002".equals(code)) {
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
						}else if( !"".equals(code) && "10001".equals(code)){									
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found service type : ", MSMessageReturn.STAT_ERROR);
						}else {
							
							List<Map<String, String>> lst = new ArrayList<Map<String, String>>();
							
							Map<String, String> map;
							
							for(int i=0; i<result.size(); i++) {
								
								res =(JSONObject) result.get(i);
								
								ArrayList<String> resKeys = IteratorUtils.toArrayList(res.keySet().iterator(),res.size());
								ArrayList<String> resVals = IteratorUtils.toArrayList(res.values().iterator(),res.size());								
								
								map = new HashMap<>();
								
								for(int j=0; j<res.size(); j++) {
									
									/*if("CMD_CODE".equals(resKeys.get(j).toString())) {
										map.put("CMD_CODE", String.valueOf(resVals.get(j)));
									}else if("SVC_TYPE_CODE".equals(resKeys.get(j).toString())) {
										map.put("SVC_TYPE_CODE", String.valueOf(resVals.get(j)));
									}else if("CMD_NAME".equals(resKeys.get(j).toString())) {
										map.put("CMD_NAME", String.valueOf(resVals.get(j)));
									}else if("SVC_TYPE_NAME".equals(resKeys.get(j).toString())) {
										map.put("SVC_TYPE_NAME", String.valueOf(resVals.get(j)));
									}else if("CMD_TYPE".equals(resKeys.get(j).toString())) {
										map.put("CMD_TYPE", String.valueOf(resVals.get(j)));
									}*/
									
									map.put(resKeys.get(j).toString(), String.valueOf(resVals.get(j)));
																		
								}
								
								lst.add(map);
																
							}
							
							List<Object> rlst = new ArrayList<Object>();
							
							rlst.add(lst);
							
							promise.complete(rlst);
						}
						
					} catch (ParseException e) {
						logger.error("failed parsing json insertMission");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
					
					
				}else {
					logger.error("failed executing inside vertx.selectQuery");							
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
				}
			}));
			
			fut2.compose(objLst -> {
				Promise<List<Object>> promise = Promise.promise();
				
				colId.put(COL_ID, DB_TYPE+"013");
				colId.put("CMD_TYPE", "0");
				
				eb.request("vertx.selectQuery", colId.toString(), reply -> {
					
					if(reply.succeeded()) {	
						
						try {							
							JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
							JSONObject res =(JSONObject) result.get(0);
														
							String code = "";
							
							//쿼리 오류 확인
							if(res.get("code")!=null) {
								code= res.get("code").toString();
							}
							
							System.out.println("getAttributesByType result : "+result.toString());
							System.out.println("getAttributesByType res : "+res.toString());
							
							//JSONObject mission =(JSONObject) reply.result().body();
							//System.out.println("mission : "+mission.toString());
							
							//쿼리 오류 발생 시
							if( !"".equals(code) &&  !"10002".equals(code)) {
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
							}else if( !"".equals(code) && "10001".equals(code)){									
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found attributesByType : ", MSMessageReturn.STAT_ERROR);
							}else {
								
								List<Map<String, String>> lst = new ArrayList<Map<String, String>>();
								
								Map<String, String> map;
								
								for(int i=0; i<result.size(); i++) {
									res =(JSONObject) result.get(i);
									
									ArrayList<String> resKeys = IteratorUtils.toArrayList(res.keySet().iterator(),res.size());
									ArrayList<String> resVals = IteratorUtils.toArrayList(res.values().iterator(),res.size());
									
									map = new HashMap<>();
									
									String strAttrCode = "";
									String strCmdCode = "";
									String strAttrName = "";
									String strAttrType = "";
									
									for(int j=0; j<res.size(); j++) {
										
										/*if( "ATTR_CODE".equals(resKeys.get(j).toString()) ) {
											strAttrCode = String.valueOf(resVals.get(j));
										}else if("CMD_CODE".equals(resKeys.get(j).toString())) {
											strCmdCode = String.valueOf(resVals.get(j));
										}else if("ATTR_NAME".equals(resKeys.get(j).toString())) {
											strAttrName = String.valueOf(resVals.get(j));
										}else if("ATTR_TYPE".equals(resKeys.get(j).toString())) {
											strAttrType = String.valueOf(resVals.get(j));
										}*/
										
										map.put(resKeys.get(j).toString(), String.valueOf(resVals.get(j)));
										
									}
									
									lst.add(map);
									
								}
								
								objLst.add(lst);
								
								promise.complete(objLst);
								
							}
							
						} catch (ParseException e) {
							promise.fail("fail!");
							logger.error("failed parsing json insertMission");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
						
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");							
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
				});
				
				
				return promise.future();
				
			}).compose(objLst ->{
				
				Promise<List<Object>> promise = Promise.promise();
				
				colId.put(COL_ID, DB_TYPE+"015");
				colId.put("service_type", svcType);
				
				eb.request("vertx.selectQuery", colId.toString(), reply -> {
					
					if(reply.succeeded()) {	
						
						try {
							System.out.println("getServiceTypeList reply result : "+reply.result().body().toString());
							JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
							JSONObject res =(JSONObject) result.get(0);
							String code = "";
							
							//쿼리 오류 확인
							if(res.get("code")!=null) {
								code= res.get("code").toString();
							}
							
							System.out.println("getServiceTypeList result : "+result.toString());
							System.out.println("getServiceTypeList res : "+res.toString());
							
							//JSONObject mission =(JSONObject) reply.result().body();
							//System.out.println("mission : "+mission.toString());
							
							//쿼리 오류 발생 시
							if( !"".equals(code) &&  !"10002".equals(code)) {
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
							}else if( !"".equals(code) && "10001".equals(code)){									
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found service type : ", MSMessageReturn.STAT_ERROR);
							}else {
								
								List<Map<String, String>> lst = new ArrayList<Map<String, String>>();
								
								Map<String, String> map;
								
								for(int i=0; i<result.size(); i++) {
									
									res =(JSONObject) result.get(i);
									
									ArrayList<String> resKeys = IteratorUtils.toArrayList(res.keySet().iterator(),res.size());
									ArrayList<String> resVals = IteratorUtils.toArrayList(res.values().iterator(),res.size());								
									
									map = new HashMap<>();
									
									for(int j=0; j<res.size(); j++) {
										
										/*if("SVC_TYPE_CODE".equals(resKeys.get(j).toString())) {
											map.put("service_type_id", String.valueOf(resVals.get(j)));
										}else if("SVC_TYPE_NAME".equals(resKeys.get(j).toString())) {
											map.put("service_type_name", String.valueOf(resVals.get(j)));
										}*/
										
										map.put(resKeys.get(j).toString(), String.valueOf(resVals.get(j)));
																			
									}
									
									lst.add(map);
																	
								}
								
								
								objLst.add(lst);
								
								promise.complete(objLst);
							}
							
						} catch (ParseException e) {
							logger.error("failed parsing json insertMission");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
						
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");							
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
				});
				
				return promise.future();
				
			}).onSuccess(objList->{
				
				List<Map<String, String>> cmdBySvcList = (List<Map<String, String>>) objList.get(0);
				List<Map<String, String>> mapDroneAttrList = (List<Map<String, String>>) objList.get(1);
				List<Map<String, String>> svcList = (List<Map<String, String>>) objList.get(2);
				
				System.out.println("cmdBySvcList : "+cmdBySvcList.toString());
				System.out.println("mapDroneAttrList : "+mapDroneAttrList.toString());
				System.out.println("svcList : "+svcList.toString());
				
				Map<String, Object> rslt = new HashMap<>();
				
				List<Map<String, String>> lst = new ArrayList<Map<String, String>>();
				
				LinkedHashMap<String, String> map;
				
				for(int i=0; i<cmdBySvcList.size(); i++) {
					
					map = new LinkedHashMap<>();
					
					String cmdCode = cmdBySvcList.get(i).get("CMD_CODE");
					String svcTypeCode = cmdBySvcList.get(i).get("SVC_TYPE_CODE");
					String svcTypeName = cmdBySvcList.get(i).get("SVC_TYPE_NAME");
					String cmdName = cmdBySvcList.get(i).get("CMD_NAME");
					
					map.put("command_num", cmdCode);
					map.put("service_type", svcTypeName);
					map.put("command_name", cmdName);
					
					for(int j=0; j<mapDroneAttrList.size(); j++) {
						
						String cmdCodeAttr = mapDroneAttrList.get(j).get("CMD_CODE");
						
						if(cmdCodeAttr.equals(cmdCode)) {
							String attrNm = mapDroneAttrList.get(j).get("ATTR_NAME");
							
							map.put(attrNm, "");
							
						}
						
					}
					
					lst.add(map);
				}
				
				StringBuffer sb = new StringBuffer();
				for(int k=0; k<svcList.size(); k++) {
					
					String svc = svcList.get(k).get("SVC_TYPE_NAME");
					
					if(k != 0) {
						sb.append(",");
					}
					sb.append(svc.trim());
					
				}
				
				rslt.put("service_type", sb.toString());
				rslt.put("count", String.valueOf(cmdBySvcList.size()));
				rslt.put("commands", lst);
				
				routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
				.end(new Gson().toJson(rslt));
				
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
	 * Retrieves all query info
	 * 
	 * @param routingContext
	 */
	private void getGeofence(RoutingContext routingContext) {
		
		logger.info("Entered getGeofence");
		
		try {

			//JsonObject json = new JsonObject();
			//json.put(ADDR, "getAllMission");
			
			JsonObject json = routingContext.getBodyAsJson();
			
			String compId = json.getString("comp_id");
			
			if("".equals(compId) || compId == null){				
				logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Comp_id is null", MSMessageReturn.STAT_ERROR);				
			}
		
			logger.info("attempting to connect to vertx.queryManage verticle");
			json.put(COL_ID, DB_TYPE+"026");
			json.put("compId", compId);
			
			//json.put("isXML", true);
			
			boolean isXML = false;			
			
			Future<JSONArray> fut1 = Future.future(promise -> eb.request("vertx.selectQuery", json.toString(), reply -> {
				
				
				if(reply.succeeded()) {
					
					JSONParser parser = new JSONParser();
					JSONArray geofences = null;
					JSONObject result = new JSONObject();
					
					String res = reply.result().body().toString();
					
					System.out.println("res : "+res);
					
					try {
						
						geofences = (JSONArray) parser.parse(res);
						
						System.out.println("geofences : "+geofences.toJSONString());
						
						//result.put("count", temp1.size()+"");
						//result.put("missions", temp1);

					} catch (ParseException e) {
						// TODO Auto-generated catch block
						promise.fail(e.getCause());
						logger.error("failed parsing json getAllGeofence");

					}
					
					promise.complete(geofences);
					
				}else {
					promise.fail(reply.cause());
					logger.error("failed executing inside vertx.selectQuery");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
				}
				
			}));
			
			fut1.compose(geofences -> {
				
				json.put(COL_ID, DB_TYPE+"019");
				
				Promise<Void> promise = Promise.promise();
				
				eb.request("vertx.selectQuery", json.toString(), reply -> {
					
					if(reply.succeeded()) {
						
						JSONParser parser = new JSONParser();
						JSONArray geopoints = null;
						JSONObject result = new JSONObject();
						
						String res = reply.result().body().toString();
						
						System.out.println("res2 : "+res);
						
						try {
							
							geopoints = (JSONArray) parser.parse(res);
							
							System.out.println("geopoints : "+geopoints.toJSONString());
							System.out.println("geofences2 : "+geofences.toJSONString());
							
							List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
							
							for (int i = 0; i < geofences.size(); i++) {
								
								JSONObject geofence = (JSONObject) geofences.get(i);
								
								//조회 결과가 없으면
								if(geofence.get("code") != null && "10001".equals(geofence.get("code").toString())) {
									
									result.put("count", "0");
									result.put("geofences", list);									
									
								}else {
									for (int j = 0; j < geopoints.size(); j++) {
										
										JSONObject attr = (JSONObject) geopoints.get(j);
										
										if(geofence.get("GEO_ID").equals(attr.get("GEO_ID"))) {										
											geofence.put("lat0",attr.get("GEO_LAT"));
											geofence.put("lon0",attr.get("GEO_LON"));
										}
										
									}
									
									Map<String, Object> rstl = new LinkedHashMap<>();
									
									rstl.put("comp_id", geofence.get("COMP_ID"));
									rstl.put("geo_id", geofence.get("GEO_ID"));								
									rstl.put("geo_name", geofence.get("GEO_NAME"));								
									rstl.put("user_id", geofence.get("USER_ID"));
									rstl.put("geo_type", geofence.get("GEO_TYPE"));
									rstl.put("geo_action", geofence.get("GEO_ACTION"));
									rstl.put("max_alt", geofence.get("MAX_ALT"));								
									rstl.put("created_at",geofence.get("CREATEDAT"));
									rstl.put("lat0", geofence.get("lat0"));
									rstl.put("lon0", geofence.get("lon0"));
									
									
									list.add(rstl);
									
									//System.out.println("missons3 : "+list.toString());
									
									result.put("count", geofences.size()+"");
									result.put("geofences", list);
								}
								
							}
							
							

							routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
							.end(result.toString());

						} catch (ParseException e) {
							// TODO Auto-generated catch block
							promise.fail(e.getCause());
							logger.error("failed parsing json getGeofences");

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
	private void getGeofenceDetail(RoutingContext routingContext) {

		logger.info("Entered getGeofenceDetail");
		JSONParser parser = new JSONParser();
		
		try {
			//JsonObject json = routingContext.getBodyAsJson();
			//json.put(ADDR, "getMissionDetail");
			
			String geoId = routingContext.pathParam("geo_id");
			String compId = routingContext.pathParam("comp_id");
	
			JSONObject colId = new JSONObject();					
			colId.put(COL_ID, DB_TYPE+"027");
			colId.put("geo_id", geoId);
			colId.put("comp_id", compId);
			
			System.out.println("getGeofenceDetail getGeofences : "+colId.toString());
			
			//getGeofenceDetail Table
			Future<JSONObject> fut2 = Future.future(promise -> eb.request("vertx.selectQuery", colId.toString(), reply -> {
				
				if(reply.succeeded()) {	
					
					try {
						System.out.println("getGeofences reply result : "+reply.result().body().toString());
						JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
						JSONObject res =(JSONObject) result.get(0);
						String code = "";
						
						//쿼리 오류 확인
						if(res.get("code")!=null) {
							code= res.get("code").toString();
						}
						
						System.out.println("getGeofences result : "+result.toString());
						System.out.println("getGeofences res : "+res.toString());
						
						//JSONObject mission =(JSONObject) reply.result().body();
						//System.out.println("mission : "+mission.toString());
						
						//쿼리 오류 발생 시
						if( !"".equals(code) &&  !"10002".equals(code)) {
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
						}else if( !"".equals(code) && "10001".equals(code)){									
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found mission : "+geoId, MSMessageReturn.STAT_ERROR);
						}else {
							
							promise.complete(res);
							
						}
						
					} catch (ParseException e) {
						promise.fail("fail!");
						logger.error("failed parsing json insertMission");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
					
					
				}else {
					logger.error("failed executing inside vertx.selectQuery");							
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
				}
			}));
			
			
			fut2.compose(geofence -> {
				Promise<List<Object>> promise = Promise.promise();
				
				colId.put(COL_ID, DB_TYPE+"020");
				
				eb.request("vertx.selectQuery", colId.toString(), reply -> {
					
					if(reply.succeeded()) {	
						
						try {
							JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
							JSONObject res =(JSONObject) result.get(0);
							String code = "";
							
							//쿼리 오류 확인
							if(res.get("code")!=null) {
								code= res.get("code").toString();
							}
							
							System.out.println("getGeoPoints result : "+result.toString());
							System.out.println("getGeoPoints res : "+res.toString());
							
							//JSONObject mission =(JSONObject) reply.result().body();
							//System.out.println("mission : "+mission.toString());
							
							//쿼리 오류 발생 시
							if( !"".equals(code) &&  !"10002".equals(code)) {
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
							}else if( !"".equals(code) && "10001".equals(code)){									
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found getGeoPoints : "+geoId, MSMessageReturn.STAT_ERROR);
							}else {
								
								List<Object> lst = new ArrayList<Object>();
								lst.add(geofence);
								lst.add(result);
								
								promise.complete(lst);
								
							}
							
						} catch (ParseException e) {
							promise.fail("fail!");
							logger.error("failed parsing json insertMission");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
						
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");							
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
				});
				
				
				return promise.future();
				
			}).onSuccess(objList->{
				
				JSONObject res = (JSONObject) objList.get(0);
				JSONArray geoPointArr = (JSONArray) objList.get(1);				
				
				System.out.println("geofence res :"+res.toJSONString());
				System.out.println("geoPointArr res :"+geoPointArr.toJSONString());
				
				List<Map<String, Object>> commands = new ArrayList<Map<String, Object>>();
				
				
				for(int i=0; i<geoPointArr.size(); i++) {
					Map<String, Object> map = new LinkedHashMap<>();
					
					JSONObject point = (JSONObject) geoPointArr.get(i);
					String order = point.get("GEO_ORDER").toString();
					String lat = point.get("GEO_LAT").toString();
					String lon = point.get("GEO_LON").toString();
					
					map.put("idx", order);
					map.put("lat", lat);
					map.put("lng", lon);
										
					commands.add(map);
				}
				
				//조회 필드 순서 때문에 재입력함(JsonObject 형식은 순서X)
				Map<String, Object> result = new LinkedHashMap<>();
				
				result.put("geo_id", res.get("GEO_ID"));
				result.put("geo_name", res.get("GEO_NAME"));
				result.put("user_id", res.get("USER_ID"));
				result.put("geo_type", res.get("GEO_TYPE"));
				result.put("geo_action", res.get("GEO_ACTION"));
				result.put("max_alt", res.get("MAX_ALT"));
				result.put("count", geoPointArr.size()+"");//int -> string 변환
				result.put("commands", commands);
				result.put("created_at",res.get("CREATEDAT"));
				
				System.out.println("result res : "+result.toString());
				
				Gson gson = new Gson();
				
				System.out.println("result res2 : "+gson.toJson(result).toString());
				
				routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
				.end(gson.toJson(result).replace("\\", ""));
				
			}).onFailure(objList->{
				logger.info("code : "+MSMessageReturn.ERR_CREATE_CODE+", message : "+MSMessageReturn.ERR_CREATE_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_CREATE_CODE, MSMessageReturn.ERR_CREATE_MSG, MSMessageReturn.STAT_ERROR);
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
	
	private void insertGeofence(RoutingContext routingContext) {

		logger.info("Entered insertGeofence");
		JSONParser parser = new JSONParser();

		try {
			JsonObject json = routingContext.getBodyAsJson();
			json.put(ADDR, "insertGeofence");
			
			System.out.println("json : "+json.toString());
			
			List<JSONObject> cmdList = new ArrayList<JSONObject>();
			List<JSONObject> GEOFENCE = new ArrayList<JSONObject>();
			List<JSONObject> GEOPOINT = new ArrayList<JSONObject>();
			
			String comp_id = json.getString("comp_id");
			
			JSONObject geofence = new JSONObject();
			geofence.put("comp_id", comp_id);
			geofence.put("geo_name", json.getString("geo_name"));
			geofence.put("user_id", json.getString("user_id"));
			geofence.put("geo_type", json.getString("geo_type"));
			geofence.put("geo_action", json.getString("geo_action"));
			geofence.put("max_alt", json.getString("max_alt"));
			
			GEOFENCE.add(geofence);
			
			boolean isValid = this.checkGEOParam(json.toString(), routingContext, cmdList);
			
			System.out.println("isValid : "+isValid);
			
			if(isValid) {
				
				for(int i=0; i<cmdList.size(); i++) {
					JSONObject cmdObj = cmdList.get(i);
					
					JSONObject tempObj = new JSONObject();
					
					tempObj.put("geo_order", cmdObj.get("idx"));
					tempObj.put("geo_lat", cmdObj.get("lat"));
					tempObj.put("geo_lon", cmdObj.get("lng"));
					tempObj.put("comp_id", json.getString("comp_id"));
					
					GEOPOINT.add(tempObj);
				}
				
				Future<String> fut1 = Future.future(promise ->{
					test = 1;
					
					Future<String> fut2 = this.chkGeofenceId((Promise<String>) promise, routingContext);
					
					this.reChkGeofenceId(fut2, (Promise<String>) promise, routingContext);
					
					promise.future();
				});
				
				
				fut1.compose(geofenceId ->{
					Promise<String> promise = Promise.promise();
					
					GEOFENCE.get(0).put("geo_id", geofenceId);
					
					for(int i=0; i<GEOPOINT.size(); i++) {
						GEOPOINT.get(i).put("geo_id", geofenceId);
					}
					
					JSONObject insQueryId = GEOFENCE.get(0);//new JSONObject();
					insQueryId.put(COL_ID, DB_TYPE+"025");
					System.out.println("insertGeofence : "+insQueryId.toString());
					
					//Geofence Table Insert
					eb.request("vertx.selectQuery", insQueryId.toString(), reply -> {
						
						if(reply.succeeded()) {	
							
							try {
								JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
								JSONObject res =(JSONObject) result.get(0);
								System.out.println("result : "+result.toString());
								
								//인써트 성공
								if("10002".equals(res.get("code").toString())) {
									promise.complete(geofenceId);
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
				}).compose(geofenceId ->{
					Promise<String> promise = Promise.promise();
					
					List<Future> futureList = new ArrayList<>();
					//Geopoint Table insert
					for(int i=0; i<GEOPOINT.size(); i++) {
						JSONObject insQueryId = GEOPOINT.get(i);//new JSONObject();
						insQueryId.put(COL_ID, DB_TYPE+"021");					
						
						//System.out.println("insertCommandList : "+insQueryId.toString());
						//Mission Table Insert
						Future<String> fut2 = this.insertList(insQueryId, routingContext);
						futureList.add(fut2);
							
					}
					
					CompositeFuture.all(futureList).onComplete(ar2 -> {
						System.out.println("insertCommandList complete");
						
						if(ar2.succeeded()) {
							//System.out.println("success complete");
							promise.complete(geofenceId);
						}else {
							//System.out.println("fail complete");
							promise.fail("fail!");
						}
						
					});
					
					return promise.future();
				}).onSuccess(geofenceId->{
					
					JSONObject colId = new JSONObject();					
					colId.put(COL_ID, DB_TYPE+"027");
					colId.put("geo_id", geofenceId);
					colId.put("comp_id", comp_id);
					
					System.out.println("getGeofenceDetail getGeofences : "+colId.toString());
					
					//getGeofenceDetail Table
					Future<JSONObject> fut2 = Future.future(promise -> eb.request("vertx.selectQuery", colId.toString(), reply -> {
						
						if(reply.succeeded()) {	
							
							try {
								System.out.println("getGeofences reply result : "+reply.result().body().toString());
								JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
								JSONObject res =(JSONObject) result.get(0);
								String code = "";
								
								//쿼리 오류 확인
								if(res.get("code")!=null) {
									code= res.get("code").toString();
								}
								
								System.out.println("getGeofences result : "+result.toString());
								System.out.println("getGeofences res : "+res.toString());
								
								//JSONObject mission =(JSONObject) reply.result().body();
								//System.out.println("mission : "+mission.toString());
								
								//쿼리 오류 발생 시
								if( !"".equals(code) &&  !"10002".equals(code)) {
									logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
								}else if( !"".equals(code) && "10001".equals(code)){									
									logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found mission : "+geofenceId, MSMessageReturn.STAT_ERROR);
								}else {
									
									promise.complete(res);
									
								}
								
							} catch (ParseException e) {
								promise.fail("fail!");
								logger.error("failed parsing json insertMission");
								messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
							}
							
							
						}else {
							logger.error("failed executing inside vertx.selectQuery");							
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
						}
					}));
					
					
					fut2.compose(geofences -> {
						Promise<List<Object>> promise = Promise.promise();
						
						colId.put(COL_ID, DB_TYPE+"020");
						String geoId = (String) colId.get("geo_id");
						
						eb.request("vertx.selectQuery", colId.toString(), reply -> {
							
							if(reply.succeeded()) {	
								
								try {
									JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
									JSONObject res =(JSONObject) result.get(0);
									String code = "";
									
									//쿼리 오류 확인
									if(res.get("code")!=null) {
										code= res.get("code").toString();
									}
									
									System.out.println("getGeoPoints result : "+result.toString());
									System.out.println("getGeoPoints res : "+res.toString());
									
									//JSONObject mission =(JSONObject) reply.result().body();
									//System.out.println("mission : "+mission.toString());
									
									//쿼리 오류 발생 시
									if( !"".equals(code) &&  !"10002".equals(code)) {
										logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
										msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
									}else if( !"".equals(code) && "10001".equals(code)){									
										logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
										msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found getGeoPoints : "+geoId, MSMessageReturn.STAT_ERROR);
									}else {
										
										List<Object> lst = new ArrayList<Object>();
										lst.add(geofences);
										lst.add(result);
										
										promise.complete(lst);
										
									}
									
								} catch (ParseException e) {
									promise.fail("fail!");
									logger.error("failed parsing json insertMission");
									messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
								}
								
								
							}else {
								logger.error("failed executing inside vertx.selectQuery");							
								messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
							}
						});
						
						
						return promise.future();
						
					}).onSuccess(objList->{
						
						JSONObject res = (JSONObject) objList.get(0);
						JSONArray geoPointArr = (JSONArray) objList.get(1);				
						
						System.out.println("geofence res :"+res.toJSONString());
						System.out.println("geoPointArr res :"+geoPointArr.toJSONString());
						
						List<Map<String, Object>> commands = new ArrayList<Map<String, Object>>();
						
						
						for(int i=0; i<geoPointArr.size(); i++) {
							Map<String, Object> map = new LinkedHashMap<>();
							
							JSONObject point = (JSONObject) geoPointArr.get(i);
							String order = point.get("GEO_ORDER").toString();
							String lat = point.get("GEO_LAT").toString();
							String lon = point.get("GEO_LON").toString();
							
							map.put("idx", order);
							map.put("lat", lat);
							map.put("lng", lon);
												
							commands.add(map);
						}
						
						//조회 필드 순서 때문에 재입력함(JsonObject 형식은 순서X)
						Map<String, Object> result = new LinkedHashMap<>();
						
						result.put("geo_id", res.get("GEO_ID"));
						result.put("geo_name", res.get("GEO_NAME"));
						result.put("user_id", res.get("USER_ID"));
						result.put("geo_type", res.get("GEO_TYPE"));
						result.put("geo_action", res.get("GEO_ACTION"));
						result.put("max_alt", res.get("MAX_ALT"));
						result.put("count", geoPointArr.size()+"");//int -> string 변환
						result.put("commands", commands);
						result.put("created_at",res.get("CREATEDAT"));
						
						System.out.println("result res : "+result.toString());
						
						Gson gson = new Gson();
						
						System.out.println("result res2 : "+gson.toJson(result).toString());
						
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(gson.toJson(result).replace("\\", ""));
						
					}).onFailure(objList->{
						logger.info("code : "+MSMessageReturn.ERR_CREATE_CODE+", message : "+MSMessageReturn.ERR_CREATE_MSG);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_CREATE_CODE, MSMessageReturn.ERR_CREATE_MSG, MSMessageReturn.STAT_ERROR);
					});
					
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
	
	private void updateGeofence(RoutingContext routingContext) {

		logger.info("Entered updateGeofence");
		JSONParser parser = new JSONParser();

		try {
			JsonObject json = routingContext.getBodyAsJson();
			json.put(ADDR, "updateGeofence");
			
			System.out.println("json : "+json.toString());
			
			List<JSONObject> cmdList = new ArrayList<JSONObject>();
			List<JSONObject> GEOFENCE = new ArrayList<JSONObject>();
			List<JSONObject> GEOPOINT = new ArrayList<JSONObject>();
			
			String geo_id = json.getString("geo_id");
			String comp_id = json.getString("comp_id");
			
			if( "".equals(geo_id) || geo_id == null) {
				logger.error("code : "+MSMessageReturn.ERR_MDT_PARAM_MISS_CODE+", message : "+MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+"geo_id");
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_MDT_PARAM_MISS_CODE, MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+"geo_id", MSMessageReturn.STAT_ERROR);
			}
			
			boolean isValid = this.checkGEOParam(json.toString(), routingContext, cmdList);
			
			System.out.println("isValid : "+isValid);
			
			if(isValid) {
								
				JSONObject queryParam = new JSONObject();
				queryParam.put(COL_ID, DB_TYPE+"027");
				queryParam.put("geo_id", geo_id);
				queryParam.put("comp_id", comp_id);
				
				Future<String> fut1 = Future.future(promise -> eb.request("vertx.selectQuery", queryParam.toString(), reply -> {
					
					if(reply.succeeded()) {
						
						JSONArray gefences = null;					
						
						String res = reply.result().body().toString();
						
						System.out.println("res : "+res);					

						try {
							
							gefences = (JSONArray) parser.parse(res);
							
							System.out.println("gefences : "+gefences.toJSONString());
							System.out.println("gefences : "+gefences.get(0));
							
							JSONObject queryRes = (JSONObject) gefences.get(0);
							String queryResCd = "";
							if(queryRes.get("code") != null) {
								queryResCd = queryRes.get("code").toString();
							}
							
							//정상 조회 시
							if( "".equals(queryResCd) && gefences.size() >= 0 ) {
								promise.complete(geo_id);
							}
							//쿼리 결과만 리턴 되고 그 결과가 정상(10002)이 아닐 때
							else if( !"10002".equals(queryResCd) ) {
								logger.error("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
								promise.fail(geo_id);
							}
							
						} catch (ParseException e) {
							// TODO Auto-generated catch block
							logger.error("failed parsing json deleteMission");
							promise.fail(e.getCause());
						}
						
					} else {
		
						logger.error("failed executing inside vertx.queryManage");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
				}));
				
				
				fut1.compose(result ->{
					Promise<String> promise = Promise.promise();
					
					JSONObject queryParam2 = new JSONObject();
					queryParam2.put(COL_ID, DB_TYPE+"023");
					queryParam2.put("geo_id", result);
					
					System.out.println("DeleteGeopoints param : "+queryParam2.toString());
					
					//DeleteAttributes
					eb.request("vertx.selectQuery", queryParam2.toString(), reply -> {
						
						if(reply.succeeded()) {		
							
							JSONArray geopoints = null;
							
							String res = reply.result().body().toString();
							
							System.out.println("res : "+res);
							
							try {
								
								geopoints = (JSONArray) parser.parse(res);
								
								System.out.println("geopoints : "+geopoints.toJSONString());
								System.out.println("geopoints : "+geopoints.get(0));
								
								JSONObject queryRes = (JSONObject) geopoints.get(0);
								String queryResCd = queryRes.get("code").toString();
								
								//정상 삭제 시
								if( "10002".equals(queryResCd) ) {
									logger.info("DeleteGeopoints Success : "+result);								
									promise.complete(geo_id);
								}else {
									logger.error("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
									promise.fail(geo_id);
								}
								
							} catch (ParseException e) {
								// TODO Auto-generated catch block
								logger.error("failed parsing json deleteGeopoints");
								promise.fail(e.getCause());
							}
							
							//promise.complete(result);
							
						} else {
			
							logger.error("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
							promise.fail(result);
						}
					});				
					
					return promise.future();
					
				}).compose(result ->{
					Promise<String> promise = Promise.promise();
					
					JSONObject queryParam2 = new JSONObject();
					queryParam2.put(COL_ID, DB_TYPE+"022");
					queryParam2.put("geo_id", result);
					
					System.out.println("DeleteGeofence param : "+queryParam2.toString());
					
					//DeleteMission
					eb.request("vertx.selectQuery", queryParam2.toString(), reply -> {
						
						if(reply.succeeded()) {		
							
							JSONArray geofences = null;
							
							String res = reply.result().body().toString();
							
							System.out.println("res : "+res);
							
							try {
								
								geofences = (JSONArray) parser.parse(res);
								
								System.out.println("geofences : "+geofences.toJSONString());
								System.out.println("geofences : "+geofences.get(0));
								
								JSONObject queryRes = (JSONObject) geofences.get(0);
								String queryResCd = queryRes.get("code").toString();
								
								//정상 삭제 시
								if( "10002".equals(queryResCd) ) {
									logger.info("DeleteGeofence Success : "+result);								
									promise.complete(geo_id);
								}else {
									logger.error("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
									promise.fail(geo_id);
								}
								
							} catch (ParseException e) {
								// TODO Auto-generated catch block
								logger.error("failed parsing json deleteGeofence");
								promise.fail(e.getCause());
							}
							
							//promise.complete(result);
							
						} else {
			
							logger.error("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR) ;
							promise.fail(result);
						}
					});				
									
					return promise.future();
					
				}).compose(geofenceId ->{
					Promise<String> promise = Promise.promise();
					
					JSONObject geofence = new JSONObject();
					geofence.put("comp_id", comp_id);
					geofence.put("geo_name", json.getString("geo_name"));
					geofence.put("user_id", json.getString("user_id"));
					geofence.put("geo_type", json.getString("geo_type"));
					geofence.put("geo_action", json.getString("geo_action"));
					geofence.put("max_alt", json.getString("max_alt"));
					
					GEOFENCE.add(geofence);
					
					for(int i=0; i<cmdList.size(); i++) {
						JSONObject cmdObj = cmdList.get(i);
						
						JSONObject tempObj = new JSONObject();
						
						tempObj.put("geo_order", cmdObj.get("idx"));
						tempObj.put("geo_lat", cmdObj.get("lat"));
						tempObj.put("geo_lon", cmdObj.get("lng"));
						tempObj.put("comp_id", json.getString("comp_id"));
						
						GEOPOINT.add(tempObj);
					}
					
					GEOFENCE.get(0).put("geo_id", geofenceId);
					
					for(int i=0; i<GEOPOINT.size(); i++) {
						GEOPOINT.get(i).put("geo_id", geofenceId);
					}
					
					JSONObject insQueryId = GEOFENCE.get(0);//new JSONObject();
					insQueryId.put(COL_ID, DB_TYPE+"025");
					System.out.println("insertGeofence : "+insQueryId.toString());
					
					//Geofence Table Insert
					eb.request("vertx.selectQuery", insQueryId.toString(), reply -> {
						
						if(reply.succeeded()) {	
							
							try {
								JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
								JSONObject res =(JSONObject) result.get(0);
								System.out.println("result : "+result.toString());
								
								//인써트 성공
								if("10002".equals(res.get("code").toString())) {
									promise.complete(geofenceId);
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
				}).compose(geofenceId ->{
					Promise<String> promise = Promise.promise();
					
					List<Future> futureList = new ArrayList<>();
					//Geopoint Table insert
					for(int i=0; i<GEOPOINT.size(); i++) {
						JSONObject insQueryId = GEOPOINT.get(i);//new JSONObject();
						insQueryId.put(COL_ID, DB_TYPE+"021");					
						
						//System.out.println("insertCommandList : "+insQueryId.toString());
						//Mission Table Insert
						Future<String> fut2 = this.insertList(insQueryId, routingContext);
						futureList.add(fut2);
							
					}
					
					CompositeFuture.all(futureList).onComplete(ar2 -> {
						System.out.println("insertCommandList complete");
						
						if(ar2.succeeded()) {
							//System.out.println("success complete");
							promise.complete(geofenceId);
						}else {
							//System.out.println("fail complete");
							promise.fail("fail!");
						}
						
					});
					
					return promise.future();
				}).onSuccess(geofenceId->{
					
					JSONObject colId = new JSONObject();					
					colId.put(COL_ID, DB_TYPE+"027");
					colId.put("geo_id", geofenceId);
					colId.put("comp_id", comp_id);
					
					System.out.println("getGeofenceDetail getGeofences : "+colId.toString());
					
					//getGeofenceDetail Table
					Future<JSONObject> fut2 = Future.future(promise -> eb.request("vertx.selectQuery", colId.toString(), reply -> {
						
						if(reply.succeeded()) {	
							
							try {
								System.out.println("getGeofences reply result : "+reply.result().body().toString());
								JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
								JSONObject res =(JSONObject) result.get(0);
								String code = "";
								
								//쿼리 오류 확인
								if(res.get("code")!=null) {
									code= res.get("code").toString();
								}
								
								System.out.println("getGeofences result : "+result.toString());
								System.out.println("getGeofences res : "+res.toString());
								
								//JSONObject mission =(JSONObject) reply.result().body();
								//System.out.println("mission : "+mission.toString());
								
								//쿼리 오류 발생 시
								if( !"".equals(code) &&  !"10002".equals(code)) {
									logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
								}else if( !"".equals(code) && "10001".equals(code)){									
									logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found mission : "+geofenceId, MSMessageReturn.STAT_ERROR);
								}else {
									
									promise.complete(res);
									
								}
								
							} catch (ParseException e) {
								promise.fail("fail!");
								logger.error("failed parsing json insertMission");
								messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
							}
							
							
						}else {
							logger.error("failed executing inside vertx.selectQuery");							
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
						}
					}));
					
					
					fut2.compose(geofences -> {
						Promise<List<Object>> promise = Promise.promise();
						
						colId.put(COL_ID, DB_TYPE+"020");
						String geoId = (String) colId.get("geo_id");
						
						eb.request("vertx.selectQuery", colId.toString(), reply -> {
							
							if(reply.succeeded()) {	
								
								try {
									JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
									JSONObject res =(JSONObject) result.get(0);
									String code = "";
									
									//쿼리 오류 확인
									if(res.get("code")!=null) {
										code= res.get("code").toString();
									}
									
									System.out.println("getGeoPoints result : "+result.toString());
									System.out.println("getGeoPoints res : "+res.toString());
									
									//JSONObject mission =(JSONObject) reply.result().body();
									//System.out.println("mission : "+mission.toString());
									
									//쿼리 오류 발생 시
									if( !"".equals(code) &&  !"10002".equals(code)) {
										logger.info("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
										msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
									}else if( !"".equals(code) && "10001".equals(code)){									
										logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
										msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found getGeoPoints : "+geoId, MSMessageReturn.STAT_ERROR);
									}else {
										
										List<Object> lst = new ArrayList<Object>();
										lst.add(geofences);
										lst.add(result);
										
										promise.complete(lst);
										
									}
									
								} catch (ParseException e) {
									promise.fail("fail!");
									logger.error("failed parsing json insertMission");
									messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
								}
								
								
							}else {
								logger.error("failed executing inside vertx.selectQuery");							
								messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
							}
						});
						
						
						return promise.future();
						
					}).onSuccess(objList->{
						
						JSONObject res = (JSONObject) objList.get(0);
						JSONArray geoPointArr = (JSONArray) objList.get(1);				
						
						System.out.println("geofence res :"+res.toJSONString());
						System.out.println("geoPointArr res :"+geoPointArr.toJSONString());
						
						List<Map<String, Object>> commands = new ArrayList<Map<String, Object>>();
						
						
						for(int i=0; i<geoPointArr.size(); i++) {
							Map<String, Object> map = new LinkedHashMap<>();
							
							JSONObject point = (JSONObject) geoPointArr.get(i);
							String order = point.get("GEO_ORDER").toString();
							String lat = point.get("GEO_LAT").toString();
							String lon = point.get("GEO_LON").toString();
							
							map.put("idx", order);
							map.put("lat", lat);
							map.put("lng", lon);
												
							commands.add(map);
						}
						
						//조회 필드 순서 때문에 재입력함(JsonObject 형식은 순서X)
						Map<String, Object> result = new LinkedHashMap<>();
						
						result.put("geo_id", res.get("GEO_ID"));
						result.put("geo_name", res.get("GEO_NAME"));
						result.put("user_id", res.get("USER_ID"));
						result.put("geo_type", res.get("GEO_TYPE"));
						result.put("geo_action", res.get("GEO_ACTION"));
						result.put("max_alt", res.get("MAX_ALT"));
						result.put("count", geoPointArr.size()+"");//int -> string 변환
						result.put("commands", commands);
						result.put("created_at",res.get("CREATEDAT"));
						
						System.out.println("result res : "+result.toString());
						
						Gson gson = new Gson();
						
						System.out.println("result res2 : "+gson.toJson(result).toString());
						
						routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
						.end(gson.toJson(result).replace("\\", ""));
						
					}).onFailure(objList->{
						logger.info("code : "+MSMessageReturn.ERR_UPDATE_CODE+", message : "+MSMessageReturn.ERR_UPDATE_MSG);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_UPDATE_CODE, MSMessageReturn.ERR_UPDATE_MSG, MSMessageReturn.STAT_ERROR);
					});
					
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
	
	private void deleteGeofence(RoutingContext routingContext) {
		
		logger.info("Entered deleteGeofence");
		JSONParser parser = new JSONParser();
		
		try {

			JsonObject json = routingContext.getBodyAsJson();
			//json.put(ADDR, "deleteMission");
			
			String geo_id = json.getString("geo_id");
			String comp_id = json.getString("comp_id");
			
			if( "".equals(geo_id) || geo_id == null) {
				logger.error("code : "+MSMessageReturn.ERR_MDT_PARAM_MISS_CODE+", message : "+MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+"mission_id");
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_MDT_PARAM_MISS_CODE, MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+"mission_id", MSMessageReturn.STAT_ERROR);
			}
						
			System.out.println("deleteGeofence param : "+json.toString());
				
			JSONObject queryParam = new JSONObject();
			queryParam.put(COL_ID, DB_TYPE+"027");
			queryParam.put("geo_id", geo_id);
			queryParam.put("comp_id", comp_id);
			
			Future<String> fut1 = Future.future(promise -> eb.request("vertx.selectQuery", queryParam.toString(), reply -> {
				
				if(reply.succeeded()) {
					
					JSONArray gefences = null;					
					
					String res = reply.result().body().toString();
					
					System.out.println("res : "+res);					

					try {
						
						gefences = (JSONArray) parser.parse(res);
						
						System.out.println("gefences : "+gefences.toJSONString());
						System.out.println("gefences : "+gefences.get(0));
						
						JSONObject queryRes = (JSONObject) gefences.get(0);
						String queryResCd = "";
						if(queryRes.get("code") != null) {
							queryResCd = queryRes.get("code").toString();
						}
						
						//정상 조회 시
						if( "".equals(queryResCd) && gefences.size() >= 0 ) {
							promise.complete(geo_id);
						}
						//쿼리 결과만 리턴 되고 그 결과가 정상(10002)이 아닐 때
						else if( !"10002".equals(queryResCd) ) {
							logger.error("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR);
							promise.fail(geo_id);
						}
						
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						logger.error("failed parsing json deleteMission");
						promise.fail(e.getCause());
					}
					
				} else {
	
					logger.error("failed executing inside vertx.queryManage");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
				}
			}));
			
			
			fut1.compose(result ->{
				Promise<String> promise = Promise.promise();
				
				JSONObject queryParam2 = new JSONObject();
				queryParam2.put(COL_ID, DB_TYPE+"023");
				queryParam2.put("geo_id", result);
				
				System.out.println("DeleteGeopoints param : "+queryParam2.toString());
				
				//DeleteAttributes
				eb.request("vertx.selectQuery", queryParam2.toString(), reply -> {
					
					if(reply.succeeded()) {		
						
						JSONArray geopoints = null;
						
						String res = reply.result().body().toString();
						
						System.out.println("res : "+res);
						
						try {
							
							geopoints = (JSONArray) parser.parse(res);
							
							System.out.println("geopoints : "+geopoints.toJSONString());
							System.out.println("geopoints : "+geopoints.get(0));
							
							JSONObject queryRes = (JSONObject) geopoints.get(0);
							String queryResCd = queryRes.get("code").toString();
							
							//정상 삭제 시
							if( "10002".equals(queryResCd) ) {
								logger.info("DeleteGeopoints Success : "+result);								
								promise.complete(geo_id);
							}else {
								logger.error("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR);
								promise.fail(geo_id);
							}
							
						} catch (ParseException e) {
							// TODO Auto-generated catch block
							logger.error("failed parsing json deleteGeopoints");
							promise.fail(e.getCause());
						}
						
						//promise.complete(result);
						
					} else {
		
						logger.error("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR);
						promise.fail(result);
					}
				});				
				
				return promise.future();
				
			}).compose(result ->{
				Promise<String> promise = Promise.promise();
				
				JSONObject queryParam2 = new JSONObject();
				queryParam2.put(COL_ID, DB_TYPE+"022");
				queryParam2.put("geo_id", result);
				
				System.out.println("DeleteGeofence param : "+queryParam2.toString());
				
				//DeleteMission
				eb.request("vertx.selectQuery", queryParam2.toString(), reply -> {
					
					if(reply.succeeded()) {		
						
						JSONArray geofences = null;
						
						String res = reply.result().body().toString();
						
						System.out.println("res : "+res);
						
						try {
							
							geofences = (JSONArray) parser.parse(res);
							
							System.out.println("geofences : "+geofences.toJSONString());
							System.out.println("geofences : "+geofences.get(0));
							
							JSONObject queryRes = (JSONObject) geofences.get(0);
							String queryResCd = queryRes.get("code").toString();
							
							//정상 삭제 시
							if( "10002".equals(queryResCd) ) {
								logger.info("DeleteGeofence Success : "+result);								
								promise.complete(geo_id);
							}else {
								logger.error("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR);
								promise.fail(geo_id);
							}
							
						} catch (ParseException e) {
							// TODO Auto-generated catch block
							logger.error("failed parsing json deleteGeofence");
							promise.fail(e.getCause());
						}
						
						//promise.complete(result);
						
					} else {
		
						logger.error("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR) ;
						promise.fail(result);
					}
				});				
								
				return promise.future();
				
			}).onSuccess(reslut ->{				
				logger.info("code : "+MSMessageReturn.SUCCESS_CODE+", message : "+MSMessageReturn.SUCCESS_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.SUCCESS_CODE, MSMessageReturn.SUCCESS_MSG, MSMessageReturn.STAT_SUCCESS);
			}).onFailure(result ->{
				logger.info("code : "+MSMessageReturn.ERR_DELETE_CODE+", message : "+MSMessageReturn.ERR_DELETE_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_DELETE_CODE, MSMessageReturn.ERR_DELETE_MSG, MSMessageReturn.STAT_ERROR);
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
	
	private void getGeofenceToUpload(RoutingContext routingContext) {

		logger.info("Entered getGeofenceToUpload");
		JSONParser parser = new JSONParser();
		
		try {
			//JsonObject json = routingContext.getBodyAsJson();
			//json.put(ADDR, "getMissionDetail");
			
			String geoId = routingContext.pathParam("geo_id");
			String compId = routingContext.pathParam("company_id");
	
			JSONObject colId = new JSONObject();					
			colId.put(COL_ID, DB_TYPE+"027");
			colId.put("geo_id", geoId);
			colId.put("comp_id", compId);
			
			System.out.println("getGeofenceToUpload getGeofences : "+colId.toString());
			
			//Mission Table Insert
			Future<JSONObject> fut2 = Future.future(promise -> eb.request("vertx.selectQuery", colId.toString(), reply -> {
				
				if(reply.succeeded()) {	
					
					try {
						System.out.println("getGeofences reply result : "+reply.result().body().toString());
						JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
						JSONObject res =(JSONObject) result.get(0);
						String code = "";
						
						//쿼리 오류 확인
						if(res.get("code")!=null) {
							code= res.get("code").toString();
						}
						
						System.out.println("getGeofences result : "+result.toString());
						System.out.println("getGeofences res : "+res.toString());
						
						//JSONObject mission =(JSONObject) reply.result().body();
						//System.out.println("mission : "+mission.toString());
						
						//쿼리 오류 발생 시
						if( !"".equals(code) &&  !"10002".equals(code)) {
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
						}else if( !"".equals(code) && "10001".equals(code)){									
							logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found mission : "+geoId, MSMessageReturn.STAT_ERROR);
						}else {
							
							promise.complete(res);
							
						}
						
					} catch (ParseException e) {
						promise.fail("fail!");
						logger.error("failed parsing json insertMission");
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
					}
					
					
				}else {
					logger.error("failed executing inside vertx.selectQuery");							
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
				}
			}));
			
			
			fut2.compose(geofence -> {
				Promise<List<Object>> promise = Promise.promise();
				
				colId.put(COL_ID, DB_TYPE+"020");
				
				eb.request("vertx.selectQuery", colId.toString(), reply -> {
					
					if(reply.succeeded()) {	
						
						try {
							JSONArray result = (JSONArray) parser.parse(reply.result().body().toString());
							JSONObject res =(JSONObject) result.get(0);
							String code = "";
							
							//쿼리 오류 확인
							if(res.get("code")!=null) {
								code= res.get("code").toString();
							}
							
							System.out.println("getGeoPoints result : "+result.toString());
							System.out.println("getGeoPoints res : "+res.toString());
							
							//JSONObject mission =(JSONObject) reply.result().body();
							//System.out.println("mission : "+mission.toString());
							
							//쿼리 오류 발생 시
							if( !"".equals(code) &&  !"10002".equals(code)) {
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG, MSMessageReturn.STAT_ERROR);
							}else if( !"".equals(code) && "10001".equals(code)){									
								logger.info("code : "+MSMessageReturn.ERR_RETRIEVE_DATA_CODE+", message : "+MSMessageReturn.ERR_RETRIEVE_DATA_MSG);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_RETRIEVE_DATA_CODE, MSMessageReturn.ERR_RETRIEVE_DATA_MSG+"Not found getGeoPoints : "+geoId, MSMessageReturn.STAT_ERROR);
							}else {
								
								List<Object> lst = new ArrayList<Object>();
								lst.add(geofence);
								lst.add(result);
								
								promise.complete(lst);
								
							}
							
						} catch (ParseException e) {
							promise.fail("fail!");
							logger.error("failed parsing json insertMission");
							messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);
						}
						
						
					}else {
						logger.error("failed executing inside vertx.selectQuery");							
						messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
					}
				});
				
				
				return promise.future();
				
			}).onSuccess(objList->{
				
				JSONObject res = (JSONObject) objList.get(0);
				JSONArray geoPointArr = (JSONArray) objList.get(1);				
				
				System.out.println("geofence res :"+res.toJSONString());
				System.out.println("geoPointArr res :"+geoPointArr.toJSONString());
				
				List<Map<String, Object>> commands = new ArrayList<Map<String, Object>>();
				List<Map<String, Object>> lst = new ArrayList<Map<String, Object>>();
				
				Map<String, Object> map;
				
				for(int i=0; i<geoPointArr.size(); i++) {
					map = new LinkedHashMap<>();
					
					JSONObject point = (JSONObject) geoPointArr.get(i);
					String order = point.get("GEO_ORDER").toString();
					String lat = point.get("GEO_LAT").toString();
					String lng = point.get("GEO_LON").toString();
					
					map.put("idx", order);
					map.put("lat", lat);
					map.put("lng", lng);
										
					commands.add(map);
				}
				
				
				
				for(int i=0; i<commands.size(); i++) {
					
					Map<String, Object> cmdMap = commands.get(i);
					
					String lat = (String) cmdMap.get("lat");
					String lng = (String) cmdMap.get("lng");
					String idx = (String) cmdMap.get("idx");
					
					map = new LinkedHashMap<>();
					
					map.put("target_system", new Integer("1"));
					map.put("target_component", new Integer("1"));
					map.put("lat", new Double(lat));
					map.put("lng", new Double(lng));
					map.put("idx", new Integer(idx));
					map.put("count", new Integer(geoPointArr.size()));
					
					lst.add(map);
					
				}
				
				//조회 필드 순서 때문에 재입력함(JsonObject 형식은 순서X)
				Map<String, Object> result = new LinkedHashMap<>();
				
				result.put("geo_type", res.get("GEO_TYPE"));
				result.put("geo_action", res.get("GEO_ACTION"));
				result.put("max_alt", res.get("MAX_ALT"));				
				result.put("commands", lst);				
				
				System.out.println("result res : "+result.toString());
				
				Gson gson = new Gson();
				
				System.out.println("result res2 : "+gson.toJson(result).toString());
				
				routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
				.end(gson.toJson(result).replace("\\", ""));
				
			}).onFailure(objList->{
				logger.info("code : "+MSMessageReturn.ERR_CREATE_CODE+", message : "+MSMessageReturn.ERR_CREATE_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_CREATE_CODE, MSMessageReturn.ERR_CREATE_MSG, MSMessageReturn.STAT_ERROR);
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
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_INTERNAL_ERROR_CODE, MSMessageReturn.ERR_INTERNAL_ERROR_MSG+MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.STAT_ERROR);
						return false;
					}
					
					JSONArray jsonArr = (JSONArray) obj; 
					
					System.out.println("jsonArr : "+jsonArr.toJSONString());
					
					JSONObject fstCom = (JSONObject) jsonArr.get(0);
					JSONObject lstCom = (JSONObject) jsonArr.get(jsonArr.size()-1);
					
					if( !"22".equals( fstCom.get("command") ) ) {
						logger.error("First command is TakeOff 22. InPut command code: "+fstCom.get("command").toString());
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG+"First command is TakeOff 22. InPut command code: "+fstCom.get("command").toString(), MSMessageReturn.STAT_ERROR);
						return false;
					}
					
					if( !( "20".equals( lstCom.get("command") ) || "21".equals( lstCom.get("command") ) ) ) {
						logger.error("Last command is Landing 21 or Return to Lanch 20. InPut command code: "+lstCom.get("command").toString());
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG+"Last command is Landing 21 or Return to Lanch 20. InPut command code: "+lstCom.get("command").toString(), MSMessageReturn.STAT_ERROR);
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
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_JSON_PARSE_CODE, MSMessageReturn.ERR_JSON_PARSE_MSG, MSMessageReturn.STAT_ERROR);
						return false;
					}
					
					String repeat = obj.toString();
					
					if(!repeat.matches("[-+]?\\d*")){
						logger.error(key+" wrong param error");
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG, MSMessageReturn.STAT_ERROR);
						return false;
					}
					
				}else if("roundtrip".equals(key)) {
					
					if(!(obj instanceof String)){
						logger.error(key+" parameter type error");
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_JSON_PARSE_CODE, MSMessageReturn.ERR_JSON_PARSE_MSG, MSMessageReturn.STAT_ERROR);
						return false;
					}
					
					if( !( "true".equals( jsonStr ) || "false".equals( jsonStr ) ) ) {
						logger.error("RoundTrip value is true or false string RoundTrip value: "+jsonStr);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG, MSMessageReturn.STAT_ERROR);
						return false;
					}
					
				}else if("is_grid".equals(key) ) {
					
					if(!(obj instanceof String)){
						logger.error(key+" parameter type error");
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_JSON_PARSE_CODE, MSMessageReturn.ERR_JSON_PARSE_MSG, MSMessageReturn.STAT_ERROR);
						return false;
					}
					
					if( !( "true".equals( jsonStr ) || "false".equals( jsonStr ) ) ) {
						logger.error("RoundTrip value is true or false string RoundTrip value: "+jsonStr);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG, MSMessageReturn.STAT_ERROR);
						return false;
					}
					
					
				}else if("count".equals(key) ) {
					
					if(!(obj instanceof String)){
						logger.error(key+" parameter type error");
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_JSON_PARSE_CODE, MSMessageReturn.ERR_JSON_PARSE_MSG, MSMessageReturn.STAT_ERROR);
						return false;
					}
					
					String repeat = obj.toString();
					
					if(!repeat.matches("[-+]?\\d*")){
						logger.error(key+" parameter type error");
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_CREATE_CODE, MSMessageReturn.ERR_CREATE_MSG, MSMessageReturn.STAT_ERROR);
						return false;
					}
					
					pararmCnt = Integer.parseInt(obj.toString());
				}
				
				if(jsonStr.length() <= 0 ) {
					logger.error("code : "+MSMessageReturn.ERR_WRONG_PARAM_CODE+", message : "+MSMessageReturn.ERR_WRONG_PARAM_MSG);
					msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG, MSMessageReturn.STAT_ERROR);
					return false;
				}
				
			}
		
		} catch (ParseException e) {
			
			System.out.println(e);
			logger.error("code : "+MSMessageReturn.ERR_JSON_PARSE_CODE+", message : "+MSMessageReturn.ERR_JSON_PARSE_MSG);
			msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_JSON_PARSE_CODE, MSMessageReturn.ERR_JSON_PARSE_MSG, MSMessageReturn.STAT_ERROR);
			return false;
			
		}
		
		return true;
		
	}
	
	private boolean checkGEOParam(String beforeValidation, RoutingContext routingContext, List<JSONObject> cmdList) {
		
		logger.info("Entered checkGEOParam");
		
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
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_JSON_PARSE_CODE, MSMessageReturn.ERR_JSON_PARSE_MSG+MSMessageReturn.ERR_JSON_PARSE_CODE, MSMessageReturn.STAT_ERROR);
						return false;
					}
					
					JSONArray jsonArr = (JSONArray) obj; 
					
					System.out.println("jsonArr : "+jsonArr.toJSONString());
					
					for (int i = 0; i < jsonArr.size(); i++) {
						
						JSONObject param = (JSONObject) jsonArr.get(i);
												
						cmdList.add(param);
						
					}
					System.out.println("cmdList : "+cmdList.toString());
					
				}
				
				if(jsonStr.length() <= 0 ) {
					logger.error("code : "+MSMessageReturn.ERR_WRONG_PARAM_CODE+", message : "+MSMessageReturn.ERR_WRONG_PARAM_MSG);
					msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG, MSMessageReturn.STAT_ERROR);
					return false;
				}
				
			}
		
		} catch (ParseException e) {
			
			System.out.println(e);
			logger.error("code : "+MSMessageReturn.ERR_JSON_PARSE_CODE+", message : "+MSMessageReturn.ERR_JSON_PARSE_MSG);
			msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_JSON_PARSE_CODE, MSMessageReturn.ERR_JSON_PARSE_MSG, MSMessageReturn.STAT_ERROR);
			return false;
			
		}
		
		return true;
		
	}
	
	private Future<String> chkMissionId(Promise<String> promise, RoutingContext routingContext){
		Promise<String> promise2 = Promise.promise();
		String uuid = UUID.randomUUID().toString();
		//String mission_id = "ms_00"+test;
		String mission_id = "M"+uuid.substring(10,23);
		
		JSONObject queryId = new JSONObject();
		queryId.put(COL_ID, DB_TYPE+"003");
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
	
	private Future<String> chkGeofenceId(Promise<String> promise, RoutingContext routingContext){
		Promise<String> promise2 = Promise.promise();
		String uuid = UUID.randomUUID().toString();
		//String mission_id = "ms_00"+test;
		String geo_id = "G"+uuid.substring(10,23);
		
		JSONObject queryId = new JSONObject();
		queryId.put(COL_ID, DB_TYPE+"020");
		queryId.put("geo_id", geo_id);
		
		eb.request("vertx.selectQuery", queryId.toString(), reply -> {
			
			if(reply.succeeded()) {
				
				String res = reply.result().body().toString();
				
				System.out.println("getGeofences : "+res);
				
				if("[{\"code\":\"10001\",\"reason\":\"쿼리를 통해 조회된 결과가 없습니다\"}]".equals(res)) {
					promise.complete(geo_id);
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
	
	private void reChkGeofenceId(Future<String> fut, Promise<String> promise, RoutingContext routingContext) {
		
		fut.compose(result -> {
			Promise<String> promise2 = Promise.promise();
			System.out.println(test+" result : "+result);
			if(!"fail".equals(result)) {							
				System.out.println("test : "+test);
				promise.complete(result);
			}else {
				System.out.println("fail~~~~ : ");
				Future<String> fut2 = this.chkGeofenceId(promise, routingContext);
				this.reChkGeofenceId(fut2, promise, routingContext);
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
					
					//인써트 성공
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
	
	private void insertTest(RoutingContext routingContext) {

		logger.info("Entered insertMission");
		JSONParser parser = new JSONParser();

		try {
			JsonObject json = routingContext.getBodyAsJson();			
			json.put(COL_ID, "100");
			
			Future<String> fut1 = Future.future(promise -> eb.request("vertx.query_transaction", json.toString(), reply -> {
				
				if(reply.succeeded()) {
					
					List<Object> resList = (List<Object>) reply.result().body(); 
					
					try {
						
						JSONArray res = (JSONArray) parser.parse(resList.get(0).toString());
						
						System.out.println("res : "+res);
						
						
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						logger.error("failed parsing json addOneQueryManage");
						promise.fail(e.getCause());
					}	
										
					if(false) {
						String msg = "Wrong Command count";
						
						logger.error(msg);
						promise.fail(msg);
						messageReturn.commonReturn(routingContext, MessageReturn.RC_TYPEERROR_CODE, msg, isXML);
						return;
					}
					
					promise.complete("test");
					
				}else {
					logger.error("failed executing inside vertx.selectQuery");
					promise.fail("failed executing inside vertx.selectQuery");
					messageReturn.commonReturn(routingContext, MessageReturn.RC_VERTICLE_FAIL_CODE, MessageReturn.RC_VERTICLE_FAIL_REASON, isXML);					
				}
			}));
			
			
			fut1.compose(ar ->{
				Promise<Void> promise = Promise.promise();
				JSONObject insQueryId = new JSONObject();
				insQueryId.put(COL_ID, DB_TYPE+"004");
				System.out.println("insQueryId : "+insQueryId.toString());
				
				//Mission Table Insert
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
	};
	
	private Future<String> itemValidator(RoutingContext routingContext, JSONObject queryId, JSONParser parser, List<JSONObject> cmdList){
		
		logger.info("Entered itemValidator");
		
		return Future.future(promise -> eb.request("vertx.selectQuery", queryId.toString(), reply -> {
			
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
						String cmdCode = attr.get("CMD_CODE").toString();
						String attrName = attr.get("ATTR_NAME").toString();
						String isMandatory = "";
						
						if(attr.get("IS_MANDATORY") != null) {
							isMandatory = attr.get("IS_MANDATORY").toString();
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
								
								String msg = "No Mandatory Attribute ATTR NAME : "+attrName;
								// 전문 내 해당 UI 속성 중 Mandatory 항목이 없는 경우
								logger.error(msg);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_MDT_PARAM_MISS_CODE, MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+msg, MSMessageReturn.STAT_ERROR);
								return;
								
							}else if(cmdMap.containsKey(attrName)) {
								// 전문 내 해당 UI 속성 중 Mandatory 항목은 있지만 값이 없는 경우
								String val = cmdMap.get(attrName).toString();
								
								if("".equals(val) || val == null) {
									
									String msg = "No Mandatory Attribute value ATTR NAME : "+attrName;
									
									logger.error(msg);
									//promise.fail(msg);
									msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_MDT_PARAM_MISS_CODE, MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+msg, MSMessageReturn.STAT_ERROR);
									return;
								}
								
								//System.out.println(attrName+" val : "+val.toString());
								
							}
							
						}
					}
					
					// 전문 내 count 와 실제 들어온 command 개수와 값 비교
					if(cmdList.size() != pararmCnt) {
						String msg = "command count";
						
						logger.error(msg);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG+msg, MSMessageReturn.STAT_ERROR);
						
						//promise.fail(msg);
						return;
					}
					
					//전문에 있는 속성 이름이 기준정보 테이블에 있는 command 별 속성 이름과 일치하는 지 비교
					for (int i = 0; i < cmdList.size(); i++) {
						
						JSONObject cmd = (JSONObject) cmdList.get(i);
						String cmdCode = cmd.get("CMD_CODE").toString();
						
						ArrayList<String> arrAttrNames = attrMap.get(cmdCode);
						
						if(arrAttrNames == null) {
							String msg = "No Attribute (CMD_ID : "+cmdCode+")";
							
							//항목이 전혀 없는 Command 경우
							logger.error(msg);
							//promise.fail(msg);
							msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG+msg, MSMessageReturn.STAT_ERROR);								
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
								String msg = "Wrong Attribute (CMD_ID : "+cmdCode+", ATTR_NAME : "+key+")";
								// 전문 내 해당 UI 속성 중 Attribute 항목이 없는 경우
								logger.error(msg);
								//promise.fail(msg);
								msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_MDT_PARAM_MISS_CODE, MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+msg, MSMessageReturn.STAT_ERROR);										
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
	}
	
	private Map<String, JsonArray> createWaypoints(RoutingContext routingContext, List<Object> objList, String direction, String waypointNum) {
		
		JSONObject res = (JSONObject) objList.get(0);
		LinkedHashMap<String, ArrayList<ArrayList<String>>> mapAttrList = (LinkedHashMap<String, ArrayList<ArrayList<String>>>) objList.get(1);
		LinkedHashMap<String, ArrayList<ArrayList<String>>> mapDroneAttrList = (LinkedHashMap<String, ArrayList<ArrayList<String>>>) objList.get(2);
		//LinkedHashMap<String, ArrayList<ArrayList<String>>> mapDroneAttrList2 = (LinkedHashMap<String, ArrayList<ArrayList<String>>>) objList.get(3);
		LinkedHashMap<String, ArrayList<String>> mapDroneAttrList2 = (LinkedHashMap<String, ArrayList<String>>) objList.get(3);
		Map<String,Map<String,String>> attrMap = (HashMap<String,Map<String,String>>) objList.get(4);
		LinkedHashMap<String, ArrayList<String>> mapCmdList = (LinkedHashMap<String, ArrayList<String>>) objList.get(5);
		//int cmdCodeIdx = (int) objList.get(6);
						
		System.out.println("mission res :"+res.toJSONString());
		System.out.println("mapAttrList res :"+mapAttrList.toString());
		System.out.println("mapDroneAttrList res :"+mapDroneAttrList.toString());
		System.out.println("mapDroneAttrList2 res :"+mapDroneAttrList2.toString());
		System.out.println("mapCmdList res :"+mapCmdList.toString());
		//System.out.println("cmdCodeIdx res :"+cmdCodeIdx);
		
		LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> mapCmdListGroupOut = new LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>>();
		
		
		int wayPointCnt = 0;
		
		for(String strKey : mapCmdList.keySet()) {
			
			String strCmdId = strKey;
			String strCmdCode = mapCmdList.get(strCmdId).get(0);//CMD_CODE
			ArrayList<ArrayList<String>> arrayRows = mapDroneAttrList.get(strCmdCode);
			
			// Default_Value 설정
			LinkedHashMap<String, String> mapCmd = new LinkedHashMap<String, String>();
			for(int i=0; i<arrayRows.size(); i++ ) {
				
				ArrayList<String> arrayRow = arrayRows.get(i);
				
				// ATTR_NAME
				String strAttrName = arrayRow.get(1);
				if(null == strAttrName) strAttrName = "";
				
				// DEFAILT_VALUE
				String strDefVal = arrayRow.get(6);
				if(null == strDefVal) strDefVal = "";
				
				if( i == 0 ) mapCmd.put("command", strCmdCode);
				mapCmd.put(strAttrName, strDefVal);
				
				
				if("16".equals(strCmdCode) && "param2".equals(strAttrName)) {
					mapCmd.put(strAttrName, Integer.toString(++wayPointCnt));							
				}else {
					mapCmd.put(strAttrName, strDefVal);
				}						
				
			}
			
			LinkedHashMap<String, LinkedHashMap<String, String>> mapCmdListTemp = new LinkedHashMap<String, LinkedHashMap<String, String>>();
			mapCmdListTemp.put(strCmdCode, mapCmd);
			
			ArrayList<ArrayList<String>> arrayUIAttrRows = mapAttrList.get(strCmdId);
			
			if(arrayUIAttrRows != null && !arrayUIAttrRows.isEmpty()) {
				for(int i=0; i<arrayUIAttrRows.size(); i++) {
					
					ArrayList<String> arrayUIAttrRow = arrayUIAttrRows.get(i);
					String strRefAttrCode = arrayUIAttrRow.get(3);// REF_ATTR_CODE 값
					String strUIAttrValue = arrayUIAttrRow.get(6);// ATTR_VALUE 값
					
					if( null == strRefAttrCode || strRefAttrCode.isEmpty() ) continue;
					
					ArrayList<String> arrayValues = mapDroneAttrList2.get(strRefAttrCode);
					String strDroneCmdCode = arrayValues.get(0);// CMD_CODE 값
					String strDroneAttrName = arrayValues.get(1);// ATTR_NAME 값
					
					if(mapCmdListTemp.containsKey(strDroneCmdCode)) {
						
						LinkedHashMap<String, String> mapCmdNew = new LinkedHashMap<String, String>();
						mapCmdNew = mapCmdListTemp.get(strDroneCmdCode);
						mapCmdNew.put(strDroneAttrName, strUIAttrValue);
						mapCmdListTemp.put(strDroneCmdCode, mapCmdNew);
						
						continue;
						
					}else {
						
						ArrayList<ArrayList<String>> arrayRowsNew = mapDroneAttrList.get(strDroneCmdCode);
						LinkedHashMap<String, String> mapCmdNew = new LinkedHashMap<String, String>();
						for(int j=0; j < arrayRowsNew.size(); j++) {
						
							ArrayList<String> arrayRow = arrayRowsNew.get(j);
							
							// ATTR_NAME
							String strAttrName = arrayRow.get(1);
							if(null == strAttrName) strAttrName = "";
							
							// DEFAILT_VALUE
							String strDefVal = arrayRow.get(6);
							if(null == strDefVal) strDefVal = "";
							
							if( j == 0 ) mapCmdNew.put("command", strDroneCmdCode);
							mapCmdNew.put(strAttrName, strDefVal);
							
						}
						
						mapCmdNew.put(strDroneAttrName, strUIAttrValue);
						mapCmdListTemp.put(strDroneCmdCode, mapCmdNew);
						
					}
					
				}
				
			}
			
			ArrayList<LinkedHashMap<String, String>> arrayCmds = new ArrayList<LinkedHashMap<String, String>>();
			for(String strKeyTemp : mapCmdListTemp.keySet()) {
				arrayCmds.add(mapCmdListTemp.get(strKeyTemp));												
			}
			String strMapKey = strCmdId + "-" + strCmdCode;
			mapCmdListGroupOut.put(strMapKey, arrayCmds);					
			
		}
		
		
		LinkedHashMap<String, String> mapMission = new LinkedHashMap<String, String>();
		
		ArrayList<String> resKeys = IteratorUtils.toArrayList(res.keySet().iterator(),res.size());
		ArrayList<String> resVals = IteratorUtils.toArrayList(res.values().iterator(),res.size());
						
		for(int i=0; i<res.size(); i++ ) {
			mapMission.put(resKeys.get(i), resVals.get(i));
		}
		
		System.out.println("mapMission : "+mapMission.toString());
		
		System.out.println("mapCmdListGroupOut : "+mapCmdListGroupOut.toString());
		
		
		int nCntRepeat = 0;
		String strRepeat = mapMission.get("REPEAT");
		if(null != strRepeat && !strRepeat.isEmpty()) {
			nCntRepeat = Integer.parseInt(strRepeat);
		}
		
		boolean bRoundTrip = false;
		String strRoundTrip = mapMission.get("ROUNDTRIP");
		if(null != strRoundTrip && !strRoundTrip.isEmpty()) {
			if("true".equals(strRoundTrip)) bRoundTrip = true;
		}
		
		ArrayList<LinkedHashMap<String, String>> arrayCmdListOut = new ArrayList<LinkedHashMap<String, String>>();
		ArrayList<LinkedHashMap<String, String>> arrayRoundTrip = new ArrayList<LinkedHashMap<String, String>>();
		ArrayList<LinkedHashMap<String, String>> arrayRepeat = new ArrayList<LinkedHashMap<String, String>>();
		
		ArrayList<String> arrayMapping = new ArrayList<String>();
		for(String strKey3 : mapCmdListGroupOut.keySet()) {
			String strUICmdCode = strKey3.substring(strKey3.lastIndexOf("-")+1);
			arrayMapping.add(strKey3);
		}
		
		ArrayList<String> arrayWayPoints = new ArrayList<String>();
		ArrayList<String> arrayWPRoundTrip = new ArrayList<String>();
		ArrayList<String> arrayWPRepeat = new ArrayList<String>();
		
		ArrayList<String> arrayWayPointTemp = new ArrayList<String>();
		
		for(int i=0; i<arrayMapping.size(); i++) {
			
			// strValue( cmd_id + "-" + cmd_code 로 구성됨) ex) "1-22", "2-16", "3-16", .. "5-21"
			String strValue = arrayMapping.get(i);
			String strUICmdCode = strValue.substring(strValue.lastIndexOf("-")+1);
			if("16".equals(strUICmdCode)) arrayWayPointTemp.add(String.valueOf(i));
		}
		
		// RoundTrip 관련
		
		if(bRoundTrip) {
			// mapCmdListGroupOut 키를 기준으로 round-trip 하고, 최종적으로 drone command를 풀어서 기술하는 방법으로 구현
			// UI Command 리스트에서 마지막 WayPoint를 제회한 부분은 Reverse 하여 기존 WayPoint에 추가하여 RoundTrip을 생성한다.
			
			
			// 마지막 WayPoint는 reverse WayPoint 구간에서 제외하기 위해 "-2"를 함
			// EX) ""로 묶인 WayPoint들이 reverse 하여 생성된 구간임
			//     W1 -> W2 -> W3 ==> W1 -> W2 -> W3 -> "W2 -> W1"
			
			String strLastWPName = arrayWayPointTemp.get(arrayWayPointTemp.size() - 2);
			int nLastIndex = arrayWayPointTemp.indexOf(strLastWPName);
			
			ArrayList<String> arrayReverse = new ArrayList<String>();
			for(int j=0; j<=nLastIndex; j++) {
				arrayReverse.add(arrayWayPointTemp.get(j));
			}
			
			// Reverse 된 WayPoint를 구성한다.
			Collections.reverse(arrayReverse);
			
			// 기존 WayPoint에 Reverse된 WayPoint를 추가한다.
			int nCnt = arrayWayPointTemp.size();
			for(int k=0; k<nCnt; k++) {
				arrayWPRoundTrip.add(arrayWayPointTemp.get(k));
			}
			arrayWPRoundTrip.addAll(arrayReverse);
			
			//기존 waypoint의 "Do Change Speed" 값을 얻어온다.
			ArrayList<String> arraySpeedsN = new ArrayList<String>();
			for(int i=0; i<arrayWayPointTemp.size(); i++) {
				
				int nIndex = Integer.parseInt(arrayWayPointTemp.get(i));
				String strKey = arrayMapping.get(nIndex);
				ArrayList<LinkedHashMap<String, String>> arrayCmds = mapCmdListGroupOut.get(strKey);
				for(int j=0; j<arrayCmds.size(); j++) {
					
					LinkedHashMap<String, String> mapCmd = arrayCmds.get(j);
					String strCmdCode = mapCmd.get("command");
					if("178".equals(strCmdCode)) { // "do change speed" Command
						arraySpeedsN.add(mapCmd.get("param2"));// speed 항목
						break;
					}
					
				}
				
			}
			
			// reverse 구간이 된 waypoint "Do Change Speed" 값을 얻어온다.
			ArrayList<String> arraySpeedsR = new ArrayList<String>();
			for(int i=0; i<arrayReverse.size(); i++) {
				
				int nIndex = Integer.parseInt(arrayReverse.get(i));
				String strKey = arrayMapping.get(nIndex);
				ArrayList<LinkedHashMap<String, String>> arrayCmds = mapCmdListGroupOut.get(strKey);
				for(int j=0; j<arrayCmds.size(); j++) {
					
					LinkedHashMap<String, String> mapCmd = arrayCmds.get(j);
					String strCmdCode = mapCmd.get("command");
					if("178".equals(strCmdCode)) { // "do change speed" Command
						arraySpeedsR.add(mapCmd.get("param2"));// speed 항목
						break;
					}
					
				}
				
			}
			
			// 기존 waypoint의 "Head" 값을 얻어온다.
			ArrayList<String> arrayHeadsN = new ArrayList<String>();
			for(int i=0; i<arrayWayPointTemp.size(); i++) {
				
				int nIndex = Integer.parseInt(arrayWayPointTemp.get(i));
				String strKey = arrayMapping.get(nIndex);
				ArrayList<LinkedHashMap<String, String>> arrayCmds = mapCmdListGroupOut.get(strKey);
				for(int j=0; j<arrayCmds.size(); j++) {
					
					LinkedHashMap<String, String> mapCmd = arrayCmds.get(j);
					String strCmdCode = mapCmd.get("command");
					if("16".equals(strCmdCode)) { // "waypoint" Command
						arrayHeadsN.add(mapCmd.get("param4"));// head 항목
						break;
					}
					
				}
				
			}
			
			// reverse 구간이 된 waypoint "Head" 값을 얻어온다.
			ArrayList<String> arrayHeadsR = new ArrayList<String>();
			for(int i=0; i<arrayReverse.size(); i++) {
				
				int nIndex = Integer.parseInt(arrayReverse.get(i));
				String strKey = arrayMapping.get(nIndex);
				ArrayList<LinkedHashMap<String, String>> arrayCmds = mapCmdListGroupOut.get(strKey);
				for(int j=0; j<arrayCmds.size(); j++) {
					
					LinkedHashMap<String, String> mapCmd = arrayCmds.get(j);
					String strCmdCode = mapCmd.get("command");
					if("16".equals(strCmdCode)) { // "waypoint" Command
						arrayHeadsR.add(mapCmd.get("param4"));// head 항목
						break;
					}
					
				}
				
			}
			
			// mapping하여 처리된 UI Command를 Drone Command로 풀어 놓는다.
			ArrayList<LinkedHashMap<String, String>> arrayCmdRoundTripTemp = new ArrayList<LinkedHashMap<String, String>>();
			for(int i=0; i<arrayWPRoundTrip.size(); i++) {
				
				int nIndex = Integer.parseInt(arrayWPRoundTrip.get(i));
				String strKey = arrayMapping.get(nIndex);
				ArrayList<LinkedHashMap<String, String>> arrayCmds = mapCmdListGroupOut.get(strKey);
				arrayCmdRoundTripTemp.addAll(arrayCmds);
				
			}
			
			// 변환 로직을 적용하여 "Do Change Speed"의 값을 재설정한다.
			// 변환 로직: round-trip이 기준이 되는 waypoint를 제외한 reverse된 waypoint를 기존 waypoint에 붙여 생성한다.
			ArrayList<String> arraySpeeds = new ArrayList<String>();
			arraySpeeds.addAll(arraySpeedsN);
			arraySpeeds.remove(arraySpeeds.size()-1); // round-trip 기준 waypoint
			arraySpeeds.addAll(arraySpeedsR);
			arraySpeeds.add(arraySpeedsR.get(arraySpeedsR.size() -1));
			
			// 변환 로직을 적용하여 "Head"의 값을 재설정한다.
			ArrayList<String> arrayHeads = new ArrayList<String>();
			arrayHeads.addAll(arrayHeadsN);
			arrayHeads.remove(arrayHeads.size()-1); // round-trip 기준 waypoint
			arrayHeads.addAll(arrayHeadsR);
			arrayHeads.add(arrayHeadsR.get(arrayHeadsR.size() -1));
			
			
			ArrayList<LinkedHashMap<String, String>> arrayCmdRoundTrip = new ArrayList<LinkedHashMap<String, String>>();
			int nIndexSpeed = 0, nIndexHead = 0, n = 0;
			for( int i = 0; i<arrayCmdRoundTripTemp.size(); i++) {
				
				// 다른 객체에 설정
				LinkedHashMap<String, String> mapCmd = arrayCmdRoundTripTemp.get(i);
				LinkedHashMap<String, String> mapNew = new LinkedHashMap<String, String>();
				
				for(String strKey2 : mapCmd.keySet()) {
					
					if("178".equals(mapCmd.get("command"))) { // "do change speed" Command
						
						if("param2".equals(strKey2)) {
							mapNew.put("param2", arraySpeeds.get(nIndexSpeed)); // "speed" 항목 값 재설정
							nIndexSpeed++;
						}else {
							mapNew.put(strKey2, mapCmd.get(strKey2));
						}
						
					}else if ( "16".equals(mapCmd.get("command")) ) { // "WayPoint" Command
						
						if("param4".equals(strKey2)) {
							
							mapNew.put("param4", arrayHeads.get(nIndexHead)); // "head" 항목 값 재설정
							
							// 역방향 head 재설정
							if(direction != null) {
								if("-1".equals(direction)) {
									if(n > 0) {
										nIndexHead++;
									}
									if(nIndexHead == 0 && n == 0) {
										n++;
									}
									
								}else {
									nIndexHead++;
								}
							}else {
								nIndexHead++;
							}
							
						}else {
							mapNew.put(strKey2, mapCmd.get(strKey2));
						}
						
					}
					
				}
				
				if(!mapNew.isEmpty()) {
					arrayCmdRoundTrip.add(mapNew);
				}
				
			}
			
			arrayRoundTrip.addAll(arrayCmdRoundTrip);
			
		}else {
			// no 왕복 비행
			
			// 역방향 head 재설정
			if(direction != null) {
				if("-1".equals(direction)) {
					
					// mapping하여 처리된 UI Command를 Drone Command로 풀어 놓는다.
					ArrayList<LinkedHashMap<String, String>> arrayCmdRoundTrip = new ArrayList<LinkedHashMap<String, String>>();
					for(int i=0; i<arrayWayPointTemp.size(); i++) {
						
						int nIndex = Integer.parseInt(arrayWayPointTemp.get(i));
						String strKey = arrayMapping.get(nIndex);
						ArrayList<LinkedHashMap<String, String>> arrayCmds = mapCmdListGroupOut.get(strKey);
						arrayCmdRoundTrip.addAll(arrayCmds);
						
					}
					
					arrayRoundTrip.addAll(arrayCmdRoundTrip);
					
				}else {
					
					String strLastWPName = arrayWayPointTemp.get(arrayWayPointTemp.size() - 2);
					int nLastIndex = arrayWayPointTemp.indexOf(strLastWPName);
					
					ArrayList<String> arrayReverse = new ArrayList<String>();
					for(int j=0; j<=nLastIndex; j++) {
						arrayReverse.add(arrayWayPointTemp.get(j));
					}
										
					// 기존 WayPoint에 WayPoint를 추가한다.
					int nCnt = arrayWayPointTemp.size();
					for(int k=0; k<nCnt; k++) {
						arrayWPRoundTrip.add(arrayWayPointTemp.get(k));
					}					
					
					//기존 waypoint의 "Do Change Speed" 값을 얻어온다.
					ArrayList<String> arraySpeedsN = new ArrayList<String>();
					for(int i=0; i<arrayWayPointTemp.size(); i++) {
						
						int nIndex = Integer.parseInt(arrayWayPointTemp.get(i));
						String strKey = arrayMapping.get(nIndex);
						ArrayList<LinkedHashMap<String, String>> arrayCmds = mapCmdListGroupOut.get(strKey);
						for(int j=0; j<arrayCmds.size(); j++) {
							
							LinkedHashMap<String, String> mapCmd = arrayCmds.get(j);
							String strCmdCode = mapCmd.get("command");
							if("178".equals(strCmdCode)) { // "do change speed" Command
								arraySpeedsN.add(mapCmd.get("param2"));// speed 항목
								break;
							}
							
						}
						
					}
					
					// reverse 구간이 된 waypoint "Do Change Speed" 값을 얻어온다.
					ArrayList<String> arraySpeedsR = new ArrayList<String>();
					for(int i=0; i<arrayReverse.size(); i++) {
						
						int nIndex = Integer.parseInt(arrayReverse.get(i));
						String strKey = arrayMapping.get(nIndex);
						ArrayList<LinkedHashMap<String, String>> arrayCmds = mapCmdListGroupOut.get(strKey);
						for(int j=0; j<arrayCmds.size(); j++) {
							
							LinkedHashMap<String, String> mapCmd = arrayCmds.get(j);
							String strCmdCode = mapCmd.get("command");
							if("178".equals(strCmdCode)) { // "do change speed" Command
								arraySpeedsR.add(mapCmd.get("param2"));// speed 항목
								break;
							}
							
						}
						
					}
					
					// 기존 waypoint의 "Head" 값을 얻어온다.
					ArrayList<String> arrayHeadsN = new ArrayList<String>();
					for(int i=0; i<arrayWayPointTemp.size(); i++) {
						
						int nIndex = Integer.parseInt(arrayWayPointTemp.get(i));
						String strKey = arrayMapping.get(nIndex);
						ArrayList<LinkedHashMap<String, String>> arrayCmds = mapCmdListGroupOut.get(strKey);
						for(int j=0; j<arrayCmds.size(); j++) {
							
							LinkedHashMap<String, String> mapCmd = arrayCmds.get(j);
							String strCmdCode = mapCmd.get("command");
							if("16".equals(strCmdCode)) { // "waypoint" Command
								arrayHeadsN.add(mapCmd.get("param4"));// head 항목
								break;
							}
							
						}
						
					}
					
					// reverse 구간이 된 waypoint "Head" 값을 얻어온다.
					ArrayList<String> arrayHeadsR = new ArrayList<String>();
					for(int i=0; i<arrayReverse.size(); i++) {
						
						int nIndex = Integer.parseInt(arrayReverse.get(i));
						String strKey = arrayMapping.get(nIndex);
						ArrayList<LinkedHashMap<String, String>> arrayCmds = mapCmdListGroupOut.get(strKey);
						for(int j=0; j<arrayCmds.size(); j++) {
							
							LinkedHashMap<String, String> mapCmd = arrayCmds.get(j);
							String strCmdCode = mapCmd.get("command");
							if("16".equals(strCmdCode)) { // "waypoint" Command
								arrayHeadsR.add(mapCmd.get("param4"));// head 항목
								break;
							}
							
						}
						
					}
					
					// mapping하여 처리된 UI Command를 Drone Command로 풀어 놓는다.
					ArrayList<LinkedHashMap<String, String>> arrayCmdRoundTripTemp = new ArrayList<LinkedHashMap<String, String>>();
					for(int i=0; i<arrayWPRoundTrip.size(); i++) {
						
						int nIndex = Integer.parseInt(arrayWPRoundTrip.get(i));
						String strKey = arrayMapping.get(nIndex);
						ArrayList<LinkedHashMap<String, String>> arrayCmds = mapCmdListGroupOut.get(strKey);
						arrayCmdRoundTripTemp.addAll(arrayCmds);
						
					}
					
					// 변환 로직을 적용하여 "Do Change Speed"의 값을 재설정한다.
					// 변환 로직: round-trip이 기준이 되는 waypoint를 제외한 reverse된 waypoint를 기존 waypoint에 붙여 생성한다.
					ArrayList<String> arraySpeeds = new ArrayList<String>();
					arraySpeeds.addAll(arraySpeedsN);
					arraySpeeds.remove(arraySpeeds.size()-1); // round-trip 기준 waypoint
					arraySpeeds.addAll(arraySpeedsR);
					arraySpeeds.add(arraySpeedsR.get(arraySpeedsR.size() -1));
					
					// 변환 로직을 적용하여 "Head"의 값을 재설정한다.
					ArrayList<String> arrayHeads = new ArrayList<String>();
					arrayHeads.addAll(arrayHeadsN);
					/*arrayHeads.remove(arrayHeads.size()-1); // round-trip 기준 waypoint
					arrayHeads.addAll(arrayHeadsR);
					arrayHeads.add(arrayHeadsR.get(arrayHeadsR.size() -1));*/
					
					
					ArrayList<LinkedHashMap<String, String>> arrayCmdRoundTrip = new ArrayList<LinkedHashMap<String, String>>();
					int nIndexSpeed = 0, nIndexHead = 0, n = 0;
					for( int i = 0; i<arrayCmdRoundTripTemp.size(); i++) {
						
						// 다른 객체에 설정
						LinkedHashMap<String, String> mapCmd = arrayCmdRoundTripTemp.get(i);
						LinkedHashMap<String, String> mapNew = new LinkedHashMap<String, String>();
						
						for(String strKey2 : mapCmd.keySet()) {
							
							if("178".equals(mapCmd.get("command"))) { // "do change speed" Command
								
								if("param2".equals(strKey2)) {
									mapNew.put("param2", arraySpeeds.get(nIndexSpeed)); // "speed" 항목 값 재설정
									nIndexSpeed++;
								}else {
									mapNew.put(strKey2, mapCmd.get(strKey2));
								}
								
							}else if ( "16".equals(mapCmd.get("command")) ) { // "WayPoint" Command
								
								if("param4".equals(strKey2)) {
									
									if(nIndexHead == 0 && n == 0) {
										mapNew.put("param4", arrayHeads.get(arrayHeads.size()-1)); // "head" 항목 값 재설정
									}else {
										mapNew.put("param4", arrayHeads.get(nIndexHead)); // "head" 항목 값 재설정
									}
									
									// 역방향 head 재설정
									if(direction != null) {
										if("-1".equals(direction)) {
											if(n > 0) {
												nIndexHead++;
											}
											if(nIndexHead == 0 && n == 0) {
												n++;
											}
											
										}else {
											nIndexHead++;
										}
									}else {
										nIndexHead++;
									}
									
								}else {
									mapNew.put(strKey2, mapCmd.get(strKey2));
								}
								
							}else {
								mapNew.put(strKey2, mapCmd.get(strKey2));
							}
							
						}
						
						if(!mapNew.isEmpty()) {
							arrayCmdRoundTrip.add(mapNew);
						}
						
					}
					
					arrayRoundTrip.addAll(arrayCmdRoundTrip);
					
				}
			}else {
				
				// mapping하여 처리된 UI Command를 Drone Command로 풀어 놓는다.
				ArrayList<LinkedHashMap<String, String>> arrayCmdRoundTrip = new ArrayList<LinkedHashMap<String, String>>();
				for(int i=0; i<arrayWayPointTemp.size(); i++) {
					
					int nIndex = Integer.parseInt(arrayWayPointTemp.get(i));
					String strKey = arrayMapping.get(nIndex);
					ArrayList<LinkedHashMap<String, String>> arrayCmds = mapCmdListGroupOut.get(strKey);
					arrayCmdRoundTrip.addAll(arrayCmds);
					
				}
				
				arrayRoundTrip.addAll(arrayCmdRoundTrip);
				
			}
			
		}
		
		
		// Repeat 관련
		
		int num = 0;
		if(nCntRepeat <= 0) {
			arrayRepeat.addAll(arrayRoundTrip);
		}else {
			
			for(int i=0; i<nCntRepeat; i++) {
				arrayRepeat.addAll(arrayRoundTrip);
				if(bRoundTrip && i == 0) {
					arrayRoundTrip.remove(0);
					
					for(int j=0; j<arrayRoundTrip.size(); j++) {
						
						LinkedHashMap<String, String> roundTripCmd = arrayRoundTrip.get(j);
						String commandVal = roundTripCmd.get("command");
						if("16".equals(commandVal)) {
							break;
						}
						num++;
					}
					
					for( int k=0; k<num; k++) {
						arrayRoundTrip.remove(0);
					}
					
				}
			}
			
		}
		
		// TakeOff, Land ( or Return to launch) 기존 Command를 붙여 Command List를 생성한다,
		
		// TakeOff Command
		ArrayList<LinkedHashMap<String, String>> mapTakeOffCmds = new ArrayList<LinkedHashMap<String, String>>();
		String strkey = arrayMapping.get(0);
		mapTakeOffCmds = mapCmdListGroupOut.get(strkey);
		arrayCmdListOut.addAll(mapTakeOffCmds);
		
		// Other Command Lists
		arrayCmdListOut.addAll(arrayRepeat);
		
		// Land ( or Return to launch) Command
		ArrayList<LinkedHashMap<String, String>> mapLandCmds = new ArrayList<LinkedHashMap<String, String>>();
		String strkey2 = arrayMapping.get(arrayMapping.size() - 1);
		mapLandCmds = mapCmdListGroupOut.get(strkey2);
		arrayCmdListOut.addAll(mapLandCmds);
		
		
		// map 자료 구조 -> json String 변환
		Gson gsonObjA = new GsonBuilder().create();
		String strCmds = gsonObjA.toJson(arrayCmdListOut);
		
		List<LinkedHashMap<String, String>> cmdLst = (List<LinkedHashMap<String, String>>) new Gson().fromJson(strCmds, List.class);
		// Command parsing
		List<LinkedHashMap<String, Object>> rslt = new ArrayList<LinkedHashMap<String, Object>>();
		
		Map<String, String> attr = null;
		
		System.out.println("strCmds : "+strCmds);
		System.out.println("cmdLst : "+cmdLst.toString());
		
		// 역비행 관련
		if(direction != null) {
			
			List<Map<String, String>> oldCmds = (List<Map<String, String>>) new Gson().fromJson(strCmds, List.class);
			List<Map<String, String>> newCmds = new ArrayList<Map<String, String>>();
			
			if(!"-1".equals(direction) && !"1".equals(direction)) {
				logger.info("code : "+MSMessageReturn.ERR_WRONG_PARAM_CODE+", message : "+MSMessageReturn.ERR_WRONG_PARAM_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG+"direction", MSMessageReturn.STAT_ERROR);
			}
			
			if(Integer.parseInt(waypointNum) <= 0) {
				logger.info("code : "+MSMessageReturn.ERR_WRONG_PARAM_CODE+", message : "+MSMessageReturn.ERR_WRONG_PARAM_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG+"waypoint_num", MSMessageReturn.STAT_ERROR);
			}
			
			int wayCnt = 0;
			int totCnt = oldCmds.size();
			boolean flg = false;
			
			int no = 1;
			
			for(int i=0; i<totCnt; i++) {
				
				Map<String, String> map = oldCmds.get(i);
				String cmdCode = map.get("command");
				
				if("16".equals(cmdCode)) {
					map.put("param3", String.valueOf(no));
					no++;
				}
				
				if(cmdCode == null || "".equals(cmdCode)) {
					logger.info("code : "+MSMessageReturn.ERR_WRONG_PARAM_CODE+", message : "+MSMessageReturn.ERR_WRONG_PARAM_MSG);
					msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG+"command", MSMessageReturn.STAT_ERROR);
				}
				
				// 역방향인 경우
				if("-1".equals(direction)) {
					
					// 첫 command는 제외 (takeoff)
					if(i==0) continue;
					
					// 입력받은 waypoint_num 값과 같은 경우 loop 종료
					if(wayCnt == Integer.parseInt(waypointNum)) break;
					
					// waypoint 카운팅 첫 waypoint 부터 command 저장
					if("16".equals(cmdCode)) {
						map.put("param2", "-"+map.get("param2")); // 역방향일 경우 마이너스 붙임
						wayCnt++;
						flg = true;
					}
					
					if(flg) newCmds.add(map);
				}else if("1".equals(direction)){
					// 순방향인 경우
					
					// waypoint 카운팅
					if("16".equals(cmdCode)) wayCnt++;
					
					// 입력받은 waypoint_num 값은 같은 경우 loop 시작
					if(wayCnt >= Integer.parseInt(waypointNum)) {
						newCmds.add(map);
					}
					
				}
				
			}
			
			// 입력받은 waypoint_num 이 waypoint 수 보다 큰 경우 exception 발생
			if(Integer.parseInt(waypointNum) > wayCnt) {
				logger.info("code : "+MSMessageReturn.ERR_WRONG_PARAM_CODE+", message : "+MSMessageReturn.ERR_WRONG_PARAM_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_WRONG_PARAM_CODE, MSMessageReturn.ERR_WRONG_PARAM_MSG+"waypoint_num", MSMessageReturn.STAT_ERROR);
			}
			
			// 역방향 처리
			if("-1".equals(direction)) {
				Collections.reverse(newCmds);				
			}
			
			oldCmds = newCmds;
			
			strCmds = gsonObjA.toJson(oldCmds);
			
			cmdLst = (List<LinkedHashMap<String, String>>) new Gson().fromJson(strCmds, List.class);
			
			System.out.println("strCmds2 : "+strCmds);
			System.out.println("cmdLst2 : "+cmdLst.toString());
			
		}
		
		
		int no = 1;
		for(int i=0; i<cmdLst.size(); i++) {
			Map<String, String> mapCmd = cmdLst.get(i);
			String strKey = mapCmd.get("command");
			// 역비행 요청이 아닐시에만
			if("16".equals(strKey) && direction == null) {
				mapCmd.put("param3", String.valueOf(no));
				no++;
			}
			
			attr = attrMap.get(strKey);
			
			if(attr == null) {
				
				String msg = "CMD_CODE : "+strkey+"(CONVERTING VALUE)";
				
				logger.info("code : "+MSMessageReturn.ERR_MDT_PARAM_MISS_CODE+", message : "+MSMessageReturn.ERR_MDT_PARAM_MISS_MSG);
				msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_MDT_PARAM_MISS_CODE, MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+msg, MSMessageReturn.STAT_ERROR);
			}
			
			Iterator<String> it = mapCmd.keySet().iterator();
			
			// change and add to new command list
			LinkedHashMap<String, Object> newAttr = new LinkedHashMap<String, Object>();
			while(it.hasNext()) {
				
				String key = it.next();
				String type = attr.get(key);
				String val = mapCmd.get(key);
				
				if("command".equals(key) || "Integer".equals(type)) {
					
					if(val == null || "".equals(val)) {
						
						String msg = "ATTR/VALUE : "+key+"/"+val+"(CONVERTING VALUE)";
						
						logger.info("code : "+MSMessageReturn.ERR_MDT_PARAM_MISS_CODE+", message : "+MSMessageReturn.ERR_MDT_PARAM_MISS_MSG);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_MDT_PARAM_MISS_CODE, MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+msg, MSMessageReturn.STAT_ERROR);
						
					}
					newAttr.put(key, new Integer(val));
					
				}else if("Float".equals(type)) {
					if(val == null || "".equals(val)) {
						
						String msg = "ATTR/VALUE : "+key+"/"+val+"(CONVERTING VALUE)";
						
						logger.info("code : "+MSMessageReturn.ERR_MDT_PARAM_MISS_CODE+", message : "+MSMessageReturn.ERR_MDT_PARAM_MISS_MSG);
						msMessageReturn.commonReturn(routingContext, MSMessageReturn.ERR_MDT_PARAM_MISS_CODE, MSMessageReturn.ERR_MDT_PARAM_MISS_MSG+msg, MSMessageReturn.STAT_ERROR);
						
					}
					newAttr.put(key, new Double(val));
				}
				
			}
			rslt.add(newAttr);
		}
		
		String val = new Gson().toJson(rslt);
		
		Map<String, JsonArray> result = new LinkedHashMap<>();
		JsonParser parser2 = new JsonParser();
		JsonElement elem = parser2.parse(val);
		JsonArray jsonArrayVal = elem.getAsJsonArray();
		
		result.put("commands", jsonArrayVal);
		
		
		System.out.println("result : "+result.toString());
		
		return result;
		
	}
}
