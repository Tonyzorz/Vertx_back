package io.vertx.common;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class ValidationUtil {

	private static Logger logger = Logger.getLogger(ValidationUtil.class);

	private static final String CLASSPATH_FILE_MESSAGE = "message.properties";
	private PropertiesConfiguration configurationMessage = null; // Contains various return messages
	
	private final static String REG_SPECIAL_CHARACTERS = "^[^:?*/|\'<>\\\"]{1,}?$";
	private final static String REG_EMAIL = "^([a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)";
	private final static String REG_URL = "^((https?:\\/\\/)?(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[a-z]{2,24}\\b([-a-zA-Z0-9@:%_\\+.~#?&//=]*))";
	private final static String REG_DIGITS = "^\\d+$";
	private final static String REG_NUMBER = "^-?\\d+$";
	private final static String REG_SQL_INJECTION = "^[^:;<>()&'\"+#*=@]{1,}?$";
	private final static String REG_SQL_INJECTION2 = ".*[|]{2,}.*|.*[-]{2,}.*";

	public ValidationUtil() {

		// property files 읽어오기
		try {

			configurationMessage = new PropertiesConfiguration(CLASSPATH_FILE_MESSAGE);

		} catch (ConfigurationException e) {

		}

		configurationMessage.setReloadingStrategy(new FileChangedReloadingStrategy());
		configurationMessage.setAutoSave(true);

		if (configurationMessage == null) {

			logger.warn("Sorry, unable to find property file(s)");

		}
	}

	/**
	 * Checks for validation of a json
	 * 
	 * @param beforeValidation
	 * @return true if everything is ok, false if there is a validation error
	 */
	public boolean checkValidation(String beforeValidation) {
		
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = new JSONObject();
		ArrayList<String> jsonData = new ArrayList<String>();
		
		try {
			
			jsonObject = (JSONObject) parser.parse(beforeValidation);
			
			for (Object key : jsonObject.keySet()) {
				
				jsonData.add((String) String.valueOf(jsonObject.get(key)));
			}
			// System.out.println("[ValidationVerticle.checkValidation()] jsonData.toString
			// : " + jsonData.toString());

			Pattern p = Pattern.compile("[^A-Za-z0-9,_.ㄱ-ㅎㅏ-ㅣ가-힣\\- ]");
			Matcher m = null;

			for (int i = 0; i < jsonData.size(); i++) {
				// System.out.println("[ValidationVerticle.checkValidation()] jsonData.get(i) :
				// " + jsonData.get(i));
				m = p.matcher(jsonData.get(i));
				
				while (m.find()) {
					
					return false;
				}
			}

		} catch (ParseException e) {
			
			System.out.println(e);
			
			return false;
		}

		return true;
	}
	
	private static boolean isPattern(String regexp, String str) {
		try {
			Pattern p = Pattern.compile(regexp);
			return p.matcher(str).matches();
		} catch(Exception e) {
			return false;
		}
	}
	
	/**
	 * URL 체크
	 * @param param
	 * @return
	 */
	public static boolean isURL(String param) {
		return isPattern(REG_URL, param);
	}

	/**
	 * 음수 양수 허용 숫자 체크
	 * @param param
	 * @return
	 */
	public static boolean isNumber(String param) {
		return isPattern(REG_NUMBER, param);
	}
	/**
	 * 양수만 허용
	 * @param param
	 * @return
	 */
	public static boolean isDigits(String param) {
		return isPattern(REG_DIGITS, param);
	}
	
	/**
	 * 이메일 체크
	 * @param param
	 * @return
	 */
	public static boolean isEmail(String param) {
		return isPattern(REG_EMAIL, param);
	}
	
	/**
	 * 웹에서 꺼려하는 특수 문자 체크 [^:?*\\/|\'<>\\\]
	 * @param param
	 * @return 위 특수문자가 없으면 true
	 */
	public static boolean isSpecialCharacters(String param) {
		return isPattern(REG_SPECIAL_CHARACTERS, param);
	}
	
	/**
	 * SQL에서 꺼려하는 특수 문자 체크 [^:;<>()&'"+#*=@], ||, --
	 * @param param
	 * @return 위 특수문자가 없으면 true
	 */
	public static boolean isSqlSpecialCharacters(String param) {
		if(isPattern(REG_SQL_INJECTION, param)) {
			return !isPattern(REG_SQL_INJECTION2, param);
		}else {
			return false;
		}	
	}
	
	/**
	 * 문자열 최대 길이 체크 널 값은 허용 안합 
	 * @param str 문자열
	 * @param size 최대 길이
	 * @return 최대값 보다 작거나 같으면 true
	 */
	public static boolean isMaxLengthNotNull(String str, int size) {
		return (str != null && str.trim().length() <= size);
	}
	/**
	 * 문자열 최소 길이 체크 널 값은 허용 안함
	 * @param str 문자열
	 * @param size 최소길이
	 * @return 최소값 보다 크거나 같으면 true
	 */
	public static boolean isMinLengthNotNull(String str, int size) {
		return (str != null && str.trim().length() >= size);
	}
}
