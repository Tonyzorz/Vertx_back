package io.vertx.common.message;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.log4j.Logger;

public abstract class MessageReturn {
	
	private static Logger logger = Logger.getLogger(MessageReturn.class);

	private static final String SERVER_FILE_MESSAGE = "file:/data/message.properties";
	private static final String CLASSPATH_FILE_MESSAGE = "message.properties";

	protected PropertiesConfiguration configurationMessage = null; // Contains various return messages
	
	/**
	 * RC = Router_code
	 * VC = Validation_code
	 * XC = XmlValidation_code
	 * QC = Query_code
	 */
	public static String RC_EXCEPTION_CODE;
	public static String RC_EXCEPTION_REASON;
	public static String RC_DECODE_EXCEPTION_CODE;
	public static String RC_DECODE_EXCEPTION_REASON;
	public static String RC_WRONG_DATA_CODE;
	public static String RC_WRONG_DATA_REASON;
	public static String RC_VERTICLE_FAIL_CODE;
	public static String RC_VERTICLE_FAIL_REASON;
	public static String RC_NULL_POINTER_EXCEPTION_CODE;
	public static String RC_NULL_POINTER_EXCEPTION_REASON;
	public static String RC_WRONGKEY_CODE;
	public static String RC_WRONGKEY_REASON;
	public static String RC_LENGTHERROR_CODE;
	public static String RC_LENGTHERROR_REASON;
	public static String RC_TYPEERROR_CODE;
	public static String RC_TYPEERROR_REASON;
	
	public static String VC_PROBLEM_CODE;
	public static String VC_PROBLEM_REASON;
	
	public static String QC_UPDATED_SHAREDDATA_CODE;
	public static String QC_UPDATED_SHAREDDATA_REASON;
	public static String QC_FAIL_SHAREDDATA_CODE;
	public static String QC_FAIL_SHAREDDATA_REASON;
	public static String QC_WRONG_QUERY_FORMAT_CODE;
	public static String QC_WRONG_QUERY_FORMAT_REASON;
	public static String QC_NO_SELECT_DATA_FOUND_CODE;
	public static String QC_NO_SELECT_DATA_FOUND_REASON;
	public static String QC_FAIL_DATA_AFFECTION_CODE;
	public static String QC_FAIL_DATA_AFFECTION_REASON;
	public static String QC_SUCCESS_DATA_AFFECTION_CODE;
	public static String QC_SUCCESS_DATA_AFFECTION_REASON;
	public static String QC_PARSE_EXCEPTION_CODE;
	public static String QC_PARSE_EXCEPTION_REASON;
	
	
	
	
	public MessageReturn() throws ConfigurationException {
		
		try {

			configurationMessage = new PropertiesConfiguration(SERVER_FILE_MESSAGE);

		} catch (ConfigurationException e) {
			
			configurationMessage = new PropertiesConfiguration(CLASSPATH_FILE_MESSAGE);

		}
		
		configurationMessage.setReloadingStrategy(new FileChangedReloadingStrategy());
		configurationMessage.setAutoSave(true);
		
		if (configurationMessage == null) {
			
			logger.warn("Sorry, unable to find property file(s)");
		
		}	
		
		RC_EXCEPTION_CODE = configurationMessage.getString("router_code.exception");
		RC_EXCEPTION_REASON = configurationMessage.getString("router_reason.exception");
		RC_DECODE_EXCEPTION_CODE = configurationMessage.getString("router_code.decodeException");
		RC_DECODE_EXCEPTION_REASON = configurationMessage.getString("router_reason.decodeException");
		RC_WRONG_DATA_CODE = configurationMessage.getString("router_code.wrongData");
		RC_WRONG_DATA_REASON = configurationMessage.getString("router_reason.wrongData");
		RC_VERTICLE_FAIL_CODE = configurationMessage.getString("router_code.failedExecution");
		RC_VERTICLE_FAIL_REASON = configurationMessage.getString("router_reason.failedExecution");
		RC_NULL_POINTER_EXCEPTION_CODE = configurationMessage.getString("router_code.nullPointerException");
		RC_NULL_POINTER_EXCEPTION_REASON = configurationMessage.getString("router_reason.nullPointerException");
		VC_PROBLEM_CODE = configurationMessage.getString("validation_code.problem");
		VC_PROBLEM_REASON = configurationMessage.getString("validation_reason.problem");
		QC_UPDATED_SHAREDDATA_CODE = configurationMessage.getString("query_code.updatedSharedData");
		QC_UPDATED_SHAREDDATA_REASON = configurationMessage.getString("query_reason.updatedSharedData");
		QC_FAIL_SHAREDDATA_CODE = configurationMessage.getString("query_code.failSharedData");
		QC_FAIL_SHAREDDATA_REASON = configurationMessage.getString("query_reason.failSharedData");
		QC_WRONG_QUERY_FORMAT_CODE = configurationMessage.getString("query_code.wrongQueryFormat");
		QC_WRONG_QUERY_FORMAT_REASON = configurationMessage.getString("query_reason.wrongQueryFormat");
		QC_NO_SELECT_DATA_FOUND_CODE = configurationMessage.getString("query_code.noSelectDataFound");
		QC_NO_SELECT_DATA_FOUND_REASON = configurationMessage.getString("query_reason.noSelectDataFound");
		QC_FAIL_DATA_AFFECTION_CODE = configurationMessage.getString("query_code.failDataAffection");
		QC_FAIL_DATA_AFFECTION_REASON = configurationMessage.getString("query_reason.failDataAffection");
		QC_SUCCESS_DATA_AFFECTION_CODE = configurationMessage.getString("query_code.successDataAffection");
		QC_SUCCESS_DATA_AFFECTION_REASON = configurationMessage.getString("query_reason.successDataAffection");
		QC_PARSE_EXCEPTION_CODE = configurationMessage.getString("query_code.parseException");
		QC_PARSE_EXCEPTION_REASON = configurationMessage.getString("query_reason.parseException");
		RC_WRONGKEY_CODE = configurationMessage.getString("router_code.wrongKey");
		RC_WRONGKEY_REASON = configurationMessage.getString("router_reason.wrongKey");
		RC_LENGTHERROR_CODE = configurationMessage.getString("router_code.lengthError");
		RC_LENGTHERROR_REASON = configurationMessage.getString("router_reason.lengthError");
		RC_TYPEERROR_CODE = configurationMessage.getString("router_code.typeError");
		RC_TYPEERROR_REASON = configurationMessage.getString("router_reason.typeError");
	}

}
