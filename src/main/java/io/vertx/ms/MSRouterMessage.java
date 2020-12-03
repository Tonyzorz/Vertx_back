package io.vertx.ms;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class MSRouterMessage extends MSMessageReturn{

	private static Logger logger = Logger.getLogger(MSRouterMessage.class);

	public MSRouterMessage() throws ConfigurationException {

	}
	
	public void commonReturn(RoutingContext routingContext, String code, String msg, String stat) {
		
		String tempCode = code;
		String tempReason = msg;
		String tempStat = stat;
		
		tempReason = tempReason.replace("\"", "");
		tempStat = tempStat.replace("\"", "");
		//tempReason = tempReason.replace("\\", "");

		JsonObject message = new JsonObject();
		message.put("code", tempCode);
		message.put("message", tempReason);
		message.put("status", tempStat);
		
		JsonArray testing = new JsonArray();
		testing.add(message);
		
		logger.warn(code + " =========== " + tempReason);
			
		routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
		.end(message.toString());
		
	}

}
