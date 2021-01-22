package io.vertx.ms;

import java.util.List;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.jdbc.JDBCClient;

/**
 * This is a verticle. A verticle is a _Vert.x component_. This verticle is
 * implemented in Java, but you can implement them in JavaScript, Groovy or even
 * Ruby.
 */
public class MSStarterVerticle extends AbstractVerticle {

	private static Logger logger = Logger.getLogger(MSStarterVerticle.class);

	private JDBCClient jdbc;

	private EventBus eb;

	private SharedData sharedData;

	private JSONObject jsons = new JSONObject();

	/**
	 * This method is called when the verticle is deployed. It creates a HTTP server
	 * and registers a simple request handler.
	 * <p/>
	 * Notice the `listen` method. It passes a lambda checking the port binding
	 * result. When the HTTP server has been bound on the port, it call the
	 * `complete` method to inform that the starting has completed. Else it reports
	 * the error.
	 *
	 * @param fut
	 *            the future
	 */
	@Override
	public void start(Future<Void> fut) {
		
		logger.info("started MSStarterVerticle");

		System.out.println("[MSStarterVerticle] started");
		
		eb = vertx.eventBus();
		
		JsonObject json = new JsonObject();
		json.put("id", 3);
		json.put("address", "getIDInstance");
		
		eb.request("vertx.instance", json.toString(), reply -> {
			
			// 요청 성공시
			if (reply.succeeded()) {
				
				logger.info("vertx.instance success");
				
				String res = reply.result().body().toString();				
				System.out.println("res :"+res);
				
				
				JSONParser parser = new JSONParser();
				JSONArray temp1 = null;

				try {

					temp1 = (JSONArray) parser.parse(res);
					
					System.out.println("temp1 : "+temp1.toJSONString());
					
					for (int i = 0; i < temp1.size(); i++) {
					    JSONObject explrObject = (JSONObject) temp1.get(i);
					    
					    String role_instance_id = "";
					    String instance_nm = "";					    
					    Long instance_cnt_l;
					    
					    if("oracle".equals(config().getString("db"))) {
					    	role_instance_id = (String) explrObject.get("ROLE_INSTANCE_ID");
					    	instance_nm = (String) explrObject.get("INSTANCE_NM");					
							instance_cnt_l = (Long) explrObject.get("INSTANCE_CNT");
						}else {
							role_instance_id = (String) explrObject.get("role_instance_id");
							instance_nm = (String) explrObject.get("instance_nm");					
							instance_cnt_l = (Long) explrObject.get("instance_cnt");
						}
					    Integer instance_cnt = (int) (long) instance_cnt_l;
					    
					    System.out.println(i+" : "+explrObject.toJSONString());
					    
					    if("1".equals(role_instance_id)) {					    	
					    	vertx.deployVerticle(config().getString("ms_package_nm")+instance_nm, new DeploymentOptions().setConfig(config()).setWorker(true).setInstances(instance_cnt));
							logger.info("deployed " + instance_cnt + "  "+ instance_nm);
					    }
					}

				} catch (ParseException e) {
					// TODO Auto-generated catch block
					logger.error("failed parsing json vertx.instance");

				}
				
			
				logger.info("Successfully returned data in json format");
				
				// 요청 실패시
			} else {
				
				logger.error("failed executing inside vertx.instance");
			}
		});

	}

}
