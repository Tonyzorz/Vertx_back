package io.vertx.blog.first;

import java.util.List;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

/**
 * This is a verticle. A verticle is a _Vert.x component_. This verticle is
 * implemented in Java, but you can implement them in JavaScript, Groovy or even
 * Ruby.
 */
public class PreStarterVerticle extends AbstractVerticle {

	private static Logger logger = Logger.getLogger(PreStarterVerticle.class);

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
		
		logger.info("started PreStarterVerticle");

		System.out.println("[PreStarterVerticle] started");
		// Create a JDBC client
		jdbc = JDBCClient.createShared(vertx, config(), "Vertx-Instance");

		eb = vertx.eventBus();
		
		jdbc.getConnection(ar -> {
			
			SQLConnection connection = ar.result();
			
			System.out.println("role : "+config().getString("role"));
			
			connection.query("SELECT * FROM INSTANCE WHERE ROLE= 'adm'", results -> {					
				
				
				if(results.succeeded()) {
					
					List<JsonObject> listQueryJson = results.result().getRows();					

					
					for (int i = 0; i < listQueryJson.size(); i++) {
						
						//System.out.println("listQueryJson.get("+i+") : "+listQueryJson.get(i).toString());
						
						JsonObject queryJsons = listQueryJson.get(i);
						
						String worker_yn = queryJsons.getString("worker_yn");
						String instance_nm = queryJsons.getString("instance_nm");					
						Integer instance_cnt = queryJsons.getInteger("instance_cnt");
						
						if ( worker_yn == null ||  "".equals(worker_yn)
								|| instance_nm == null || "".equals(instance_nm)
								|| instance_cnt == null || instance_cnt < 0 )
							continue;
						
						if("Y".equals(worker_yn)) {
							vertx.deployVerticle(config().getString("package_nm")+instance_nm, new DeploymentOptions().setConfig(config()).setWorker(true).setInstances(instance_cnt));
							logger.info("deployed " + instance_cnt + "  "+ instance_nm);
							
						}else {
							vertx.deployVerticle(config().getString("package_nm")+instance_nm, new DeploymentOptions().setConfig(config()).setInstances(instance_cnt));
							logger.info("deployed " + instance_cnt + "  "+ instance_nm);

						}
						
					}
					
				}
				
				connection.close();
				
			});
		});

	}

}
