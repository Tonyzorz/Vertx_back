package io.vertx.blog.first;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import io.vertx.common.message.MessageReturn;
import io.vertx.common.message.QueryMessage;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

/**
 * This is a verticle. A verticle is a _Vert.x component_. This verticle is
 * implemented in Java, but you can implement them in JavaScript, Groovy or even
 * Ruby.
 */
public class QueryVerticle extends AbstractVerticle {

	private static Logger logger = Logger.getLogger(QueryVerticle.class);

	private JDBCClient jdbc;

	private EventBus eb;

	private SharedData sharedData;

	public JSONObject localSharedDataQueryJson = new JSONObject();
	
	private PropertiesConfiguration configurationMessage = null;
	private PropertiesConfiguration configurationTable = null;
	
	private static final String SERVER_FILE = "file:/data/router.properties";
	private static final String CLASSPATH_FILE_MESSAGE = "message.properties";
	private static final String CLASSPATH_FILE_TABLE = "table.properties";
	
	private static String TABLE_COLUMN1 = null;
	
	private QueryMessage messageReturn;

	private static final String COL_ID = "query_id";
	private static final String ASYNCMAP_NAME = "mysql";
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
	@Override
	public void start(Future<Void> fut) throws ConfigurationException {
		logger.info("started QueryVerticle");

		//System.out.println(Thread.currentThread().getId()+" [QueryVerticle] started");		

		// Create a JDBC client
		jdbc = JDBCClient.createShared(vertx, config(), "My-Whisky-Collection");

		eb = vertx.eventBus();

		sharedData = vertx.sharedData();
		
		messageReturn = new QueryMessage();

		try {
			configurationMessage = new PropertiesConfiguration(SERVER_FILE);
		} catch (ConfigurationException e) {
			configurationMessage = new PropertiesConfiguration(CLASSPATH_FILE_MESSAGE);
		}

		configurationMessage.setReloadingStrategy(new FileChangedReloadingStrategy());
		configurationMessage.setAutoSave(true);
		
		try {
			configurationTable = new PropertiesConfiguration(SERVER_FILE);
		} catch (ConfigurationException e) {
			configurationTable = new PropertiesConfiguration(CLASSPATH_FILE_TABLE);
		}
		
		configurationTable.setReloadingStrategy(new FileChangedReloadingStrategy());
		configurationTable.setAutoSave(true);	
		
		TABLE_COLUMN1 = String.valueOf(configurationTable.getProperty("table.column1"));
		
		/**
		 * address를 통한 query실행
		 */
		eb.consumer("vertx.query", message -> {

			query(message);
		});
	}

	@Override
	public void stop() throws Exception {
		// Close the JDBC client.
		jdbc.close();
	}

	private void query(Message<Object> message) {
		logger.info("entered query");

		jdbc.getConnection(ar -> {

			SQLConnection connection = ar.result();
			
			//String asyncMapName = "asyncmap";
			//String key = "query";
			String asyncMapName = ASYNCMAP_NAME;
			
			JSONParser parser = new JSONParser();
			
			try {
				JSONObject messageJson = (JSONObject) parser.parse(message.body().toString());
				
				String queryNum = messageJson.get(COL_ID).toString();				
				
				sharedData.getAsyncMap(asyncMapName, results -> {

					if (results.succeeded()) {

						AsyncMap<Object, Object> map = results.result();

						map.get(queryNum, resPut -> {

							if (resPut.succeeded()) {

								// Successfully got the value
								logger.info("received SharedData data");

								//JSONObject temp = callback((JSONObject) resPut.result());
								
								String result = resPut.result().toString();
								
								System.out.println("result : "+result);
								
								this.getQuerySharedData(result, messageJson, message, connection);
								
								
							} else {

								logger.error("failed receiving value of " + queryNum + "from " + asyncMapName);
								messageReturn.commonReturn(message, MessageReturn.QC_FAIL_SHAREDDATA_CODE, MessageReturn.QC_FAIL_SHAREDDATA_REASON);

							}
						});

					} else {

						logger.error("failed receiving map value from " + asyncMapName);
						messageReturn.commonReturn(message, MessageReturn.QC_FAIL_SHAREDDATA_CODE, MessageReturn.QC_FAIL_SHAREDDATA_REASON);
					}
				});
				
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);
			}						
			
		});

	}

	/**
	 * 
	 * 받은 Json 데이터들을 조회된 쿼리에 대입해주는 메소드 쿼리 Column은 숫자 입력 가능, 조회시 숫자 데이터 출력
	 * 
	 * @param messageJson = Body에서 받은 변경 값들
	 * @param queryString = 변경해야할 스트링 쿼리
	 * 
	 * @return queryString = 변경된 스트링 값을 리턴
	 */
	private String queryConverter(JSONObject messageJson, String queryString) {

		queryString = queryString.toLowerCase();

		for (Object key : messageJson.keySet()) {

			Object keyvalue = messageJson.get(key);
			String keyReplace = "#{" + key + "}";

			queryString = queryString.replace(keyReplace.toLowerCase(), keyvalue.toString());
		}

		logger.info("converted query");

		return queryString;
	}

	/**
	 * checks if whole query String contains any problem queryConverter()를통해 받은
	 * String값에 문제 없는지 체크하는 메소드
	 * 
	 * @param queryString = converted value from queryConverter(messageJson,
	 *                    queryString)
	 * @param message
	 * @return true = queryString has no problem false = there is a problem detected
	 */
	private boolean queryConverterCheckError(String queryString, Message<Object> message) {

		if (queryString.contains("#{")) {

			logger.warn("쿼리 형식이 잘못되었습니다, there is a problem with converted query, check your body syntax");
			messageReturn.commonReturn(message, MessageReturn.QC_WRONG_QUERY_FORMAT_CODE, MessageReturn.QC_WRONG_QUERY_FORMAT_REASON);
			return false;
		}

		logger.info("no error found inside query");

		return true;
	}

	/**
	 * 
	 * @param queryFinalString = must first go through queryConverter(JSONObject,
	 *                         String)/ 단순한 쿼리문에서 queryConverter() 메소드를 통한 컨버팅이된 결과값
	 * @param connection       = jdbc connection
	 * @param message          = router message reply purposes
	 */
	private void queryConnectionAll(String queryFinalString, SQLConnection connection, Message<Object> message) {

		// 쿼리들 구분하기 위해서, Select쿼리 실행하는 메소드는 insert,update,delete쿼리 메소드랑 다르다
		String firstSixLetters = queryFinalString.substring(0, 6);

		// select 쿼리 실행
		if (firstSixLetters.equalsIgnoreCase("select")) {

			logger.info("select query");

			connection.query(queryFinalString, finalResult -> {

				// DB조회 결과 성공시
				if (finalResult.succeeded()) {

					// 결과가 있을시
					if (finalResult.result().getNumRows() > 0) {

						List<JsonObject> resultList = finalResult.result().getRows().stream()
								.collect(Collectors.toList());
						message.reply(resultList.toString());

						logger.info("Succeeded getting data from select query");

						// 조회는 됐지만 결과는 없다
					} else {

						logger.warn("No data found for select query");
						messageReturn.commonReturn(message, MessageReturn.QC_NO_SELECT_DATA_FOUND_CODE, MessageReturn.QC_NO_SELECT_DATA_FOUND_REASON);

					}

					// 조회가 제대로 안되었다
				} else {

					// DB에서 보내주는 error code와 error 내용
					SQLException ex = (SQLException) finalResult.cause();
					logger.error("error occurred from select query : " + ex);
					messageReturn.dbReturn(message, ex);
				}

				connection.close();

			});

			// Update, insert, 또는 delete문
		} else {

			logger.info("update/insert/delete query");

			connection.update(queryFinalString, finalResult -> {

				// DB조회 결과 성공시
				if (finalResult.succeeded()) {

					// 결과가 있을시
					if (finalResult.result().getUpdated() > 0) {

						logger.info("succeeded performing insert/update/delete query");
						messageReturn.commonReturn(message, MessageReturn.QC_SUCCESS_DATA_AFFECTION_CODE, MessageReturn.QC_SUCCESS_DATA_AFFECTION_REASON);
						
						// 조회는 됐지만 결과는 없다
					} else {

						logger.warn("No affect on data from insert/update/delete query");
						messageReturn.commonReturn(message, MessageReturn.QC_FAIL_DATA_AFFECTION_CODE, MessageReturn.QC_FAIL_DATA_AFFECTION_REASON);

					}

					// 조회가 제대로 안되었다
				} else {

					// DB에서 보내주는 error code와 error 내용
					SQLException ex = (SQLException) finalResult.cause();
					logger.error("error occurred from insert/update/delete query : " + ex);
					messageReturn.dbReturn(message, ex);

				}

				connection.close();
			});
		}
	}
	
	private void getQuerySharedData(String queryString, JSONObject messageJson, Message<Object> message, SQLConnection connection) {
		
		/**
		 * queryString = query문 queryFinalString = query문안에 받은 value들 대입 positiveResult
		 * = query문 대입시 적상이면 true, 문제 있을시 false
		 */
		//String queryString = queryObject.get(TABLE_COLUMN1).toString();
		String queryFinalString = queryConverter(messageJson, queryString);
		boolean positiveResult = queryConverterCheckError(queryFinalString, message);

		if (positiveResult) {
			// select or insert/update/delete
			queryConnectionAll(queryFinalString, connection, message);
		}

		connection.close();
	}

}
