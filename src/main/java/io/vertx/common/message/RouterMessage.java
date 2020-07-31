package io.vertx.common.message;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.log4j.Logger;

import io.vertx.common.XmlConvert;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class RouterMessage extends MessageReturn{

	private static Logger logger = Logger.getLogger(RouterMessage.class);

	public RouterMessage() throws ConfigurationException {

	}
	
	public void commonReturn(RoutingContext routingContext, String code, String reason, boolean isXML) {
		
		String tempCode = code;
		String tempReason = reason;
		
		tempReason = tempReason.replace("\"", "");
		//tempReason = tempReason.replace("\\", "");

		JsonObject message = new JsonObject();
		message.put("code", tempCode);
		message.put("result", tempReason);
		
		JsonArray testing = new JsonArray();
		testing.add(message);
		
		logger.warn(code + " =========== " + tempReason);

		if(isXML) {
			XmlConvert xmlConvert = XmlConvert.xmlConvert(testing.toString());
			boolean success = xmlConvert.isSuccess();
			String xmlResult = xmlConvert.getXmlResult();
			
			if(success) {
				
				logger.info("Successfully converted json to xml");
				routingContext.response().putHeader("content-type", "application/xml; charset=utf-8")
				.end(xmlResult);
				
			} else {
				
				logger.error("Failed converting json to xml");
				routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
				.end(message.toString());
				
			}
			
		} else {
			
			routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
			.end(message.toString());
			
		}
		//Consider what status code to return .setstatusCode(?????)
		
	}

}
