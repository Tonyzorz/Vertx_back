package io.vertx.common.message;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;

import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;

public class QueryMessage extends MessageReturn{

	private static Logger logger = Logger.getLogger(QueryMessage.class);

	public QueryMessage() throws ConfigurationException {

	}
	
//	messageReturn.commonReturn(message, messageReturn.QC_UPDATED_SHAREDDATA);
//	messageReturn.dbReturn(message, ex);

	//returns sql exception code and error from db
	public void commonReturn(Message<Object> message, String code, String reason) {
		
		String tempCode = code;
		String tempReason = reason;
		
		tempReason = tempReason.replace("\"", "");
		//tempReason = tempReason.replace("\\", "");

		JsonObject replyMessage = new JsonObject();
		replyMessage.put("code", tempCode);
		replyMessage.put("reason", tempReason);
		
		JsonArray testing = new JsonArray();
		testing.add(replyMessage);
		
		logger.warn(tempCode + " =========== " + tempReason);
		
		message.reply(testing);
		
	}
	
	//returns sql exception code and error from db
	public void dbReturn(Message<Object> message, SQLException ex) {
		
		String code = String.valueOf(ex.getErrorCode());
		String reason = ex.getCause().getMessage().substring(0,
				ex.getCause().getMessage().indexOf("\n"));
		reason = reason.replace("\"", "");

		JsonObject replyMessage = new JsonObject();
		replyMessage.put("code", code);
		replyMessage.put("reason", reason);
		
		JsonArray testing = new JsonArray();
		testing.add(replyMessage);
		
		logger.warn(code + " =========== " + reason);

		message.reply(testing);
		
	}
	
	
	public void transactionReturn(Message<Object> message, String code, String reason, SQLConnection connection) {
		
		String tempCode = code;
		String tempReason = reason;
		
		tempReason = tempReason.replace("\"", "");
		//tempReason = tempReason.replace("\\", "");

		JsonObject replyMessage = new JsonObject();
		replyMessage.put("code", tempCode);
		replyMessage.put("reason", tempReason);
		
		JsonArray testing = new JsonArray();
		testing.add(replyMessage);
		
		logger.warn(tempCode + " =========== " + tempReason);
		
		List<Object> objList = new ArrayList<Object>();
		
		objList.add(testing);
		objList.add(connection);
		
		message.reply(connection);
		
	}
}
