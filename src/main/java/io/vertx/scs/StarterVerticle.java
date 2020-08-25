/**
 * @author Yeon-Bok, Lee(devop84@gmail.com)
 */
package io.vertx.scs;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.client.WebClient;

public class StarterVerticle extends AbstractVerticle {
	
	private static Logger logger = Logger.getLogger(StarterVerticle.class);
	private SharedData sharedData;
	private WebClient webClient;

	@Override
	public void start() {		
		
		logger.info("started StarterVerticle");
		sharedData = vertx.sharedData();

		int port = config().getInteger("http.port");
		String httpIP = config().getString("http.ip");
		
		int id = 4;
		WebClient client = WebClient.create(vertx);

		client.get(port, httpIP, "/instance/" + id).send(ar -> {

			if (ar.succeeded()) {

				logger.info("response from get " + httpIP + ":" + port + "/instance === success" );

				JsonArray temp = ar.result().bodyAsJsonArray();
				
				for (int i = 0; i < temp.size(); i++) {
					
					//System.out.println("listQueryJson.get("+i+") : "+listQueryJson.get(i).toString());
					
					JsonObject queryJsons = temp.getJsonObject(i);
					
					String worker_yn = queryJsons.getString("worker_yn");
					String instance_nm = queryJsons.getString("instance_nm");					
					Integer instance_cnt = queryJsons.getInteger("instance_cnt");
					
					if ( worker_yn == null ||  "".equals(worker_yn)
							|| instance_nm == null || "".equals(instance_nm)
							|| instance_cnt == null || instance_cnt < 0 )
						continue;
					
					if("Y".equals(worker_yn)) {
						vertx.deployVerticle(config().getString("scs_package_nm")+instance_nm, new DeploymentOptions().setConfig(config()).setWorker(true).setInstances(instance_cnt));
						logger.info("deployed " + instance_cnt + "  "+ instance_nm);
						
					}else {
						vertx.deployVerticle(config().getString("scs_package_nm")+instance_nm, new DeploymentOptions().setConfig(config()).setInstances(instance_cnt));
						logger.info("deployed " + instance_cnt + "  "+ instance_nm);

					}
					
				}
			} else {

				logger.info("response from get " + httpIP + ":" + port + "/queryManage === fail" );

			}

		});
	}
}