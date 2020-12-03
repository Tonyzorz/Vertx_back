package io.vertx.ms;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.log4j.Logger;

public abstract class MSMessageReturn {
	
	private static Logger logger = Logger.getLogger(MSMessageReturn.class);

	private static final String SERVER_FILE_MESSAGE = "file:/data/ms_message.properties";
	private static final String CLASSPATH_FILE_MESSAGE = "ms_message.properties";

	protected PropertiesConfiguration configurationMessage = null; // Contains various return messages
	
	/**
	 * ERR = Error_code
	 * VC = Validation_code
	 * XC = XmlValidation_code
	 * QC = Query_code
	 */
	public static String ERR_JSON_PARSE_CODE;
	public static String ERR_JSON_PARSE_MSG;
	public static String ERR_JSON_CREATE_CODE;
	public static String ERR_JSON_CREATE_MSG;
	public static String ERR_CREATE_CODE;
	public static String ERR_CREATE_MSG;
	public static String ERR_UPDATE_CODE;
	public static String ERR_UPDATE_MSG;
	public static String ERR_DELETE_CODE;
	public static String ERR_DELETE_MSG;
	public static String ERR_PARAM_MISS_CODE;
	public static String ERR_PARAM_MISS_MSG;	
	public static String ERR_MDT_PARAM_MISS_CODE;
	public static String ERR_MDT_PARAM_MISS_MSG;
	public static String ERR_WRONG_PARAM_CODE;
	public static String ERR_WRONG_PARAM_MSG;
	public static String ERR_RETRIEVE_DATA_CODE;
	public static String ERR_RETRIEVE_DATA_MSG;
	public static String ERR_DATATYPE_CODE;
	public static String ERR_DATATYPE_MSG;
	public static String ERR_INTERNAL_ERROR_CODE;
	public static String ERR_INTERNAL_ERROR_MSG;
	public static String SUCCESS_CODE;
	public static String SUCCESS_MSG;
	
	public static String STAT_SUCCESS;
	public static String STAT_ERROR;
	
	
	
	
	public MSMessageReturn() throws ConfigurationException {
		
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
		
		ERR_JSON_PARSE_CODE = "550";//configurationMessage.getString("ms_code.jsonParseError");
		ERR_JSON_PARSE_MSG = configurationMessage.getString("ms_msg.jsonParseError");
		ERR_JSON_CREATE_CODE = "551";//configurationMessage.getString("ms_code.jsonCreateError");
		ERR_JSON_CREATE_MSG = configurationMessage.getString("ms_msg.jsonCreateError");
		ERR_CREATE_CODE = "552";//configurationMessage.getString("ms_code.createError");
		ERR_CREATE_MSG = configurationMessage.getString("ms_msg.createError");
		ERR_UPDATE_CODE = "553";//configurationMessage.getString("ms_code.updateError");
		ERR_UPDATE_MSG = configurationMessage.getString("ms_msg.updateError");
		ERR_DELETE_CODE = "554";//configurationMessage.getString("ms_code.deleteError");
		ERR_DELETE_MSG = configurationMessage.getString("ms_msg.deleteError");
		ERR_PARAM_MISS_CODE = "555";//configurationMessage.getString("ms_code.paramMissingError");
		ERR_PARAM_MISS_MSG = configurationMessage.getString("ms_msg.paramMissingError");
		ERR_MDT_PARAM_MISS_CODE = "556";//configurationMessage.getString("ms_code.mandatoryParamMissingError");
		ERR_MDT_PARAM_MISS_MSG = configurationMessage.getString("ms_msg.mandatoryParamMissingError");
		ERR_WRONG_PARAM_CODE = "557";//configurationMessage.getString("ms_code.wrongParamError");
		ERR_WRONG_PARAM_MSG = configurationMessage.getString("ms_msg.wrongParamError");
		ERR_RETRIEVE_DATA_CODE = "558";//configurationMessage.getString("ms_code.retrieveDataError");
		ERR_RETRIEVE_DATA_MSG = configurationMessage.getString("ms_msg.retrieveDataError");
		ERR_DATATYPE_CODE = "559";//configurationMessage.getString("ms_code.dataTypeError");
		ERR_DATATYPE_MSG = configurationMessage.getString("ms_msg.dataTypeError");
		ERR_INTERNAL_ERROR_CODE = "560";//configurationMessage.getString("ms_code.internalError");
		ERR_INTERNAL_ERROR_MSG = configurationMessage.getString("ms_msg.internalError");
		SUCCESS_CODE = configurationMessage.getString("ms_code.success");
		SUCCESS_MSG = configurationMessage.getString("ms_msg.success");
		STAT_SUCCESS = configurationMessage.getString("ms_stat.success");
		STAT_ERROR = configurationMessage.getString("ms_stat.error");
	}

}
