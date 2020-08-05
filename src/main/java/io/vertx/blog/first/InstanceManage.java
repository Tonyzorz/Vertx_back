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
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

/**
 * This is a verticle. A verticle is a _Vert.x component_. This verticle is
 * implemented in Java, but you can implement them in JavaScript, Groovy or even
 * Ruby.
 */
public class InstanceManage extends AbstractVerticle {

	private static Logger logger = Logger.getLogger(InstanceManage.class);

	private JDBCClient jdbc;

	private EventBus eb;

	// local property file from classpath of vertx.properties
	private PropertiesConfiguration configurationMessage = null;
	private PropertiesConfiguration configurationTable = null;

	private static final String SERVER_FILE = "file:/data/router.properties";
	private static final String CLASSPATH_FILE_MESSAGE = "message.properties";
	private static final String CLASSPATH_FILE_TABLE = "table.properties";

	private static String TABLE_NAME = null;
	private static String COLUMN_ID = null;
	private static String COLUMN_ROLE = null;
	private static String COLUMN_ROLE_INSTANCE_ID = null;
	private static String COLUMN_INSTANCE_NM = null;
	private static String COLUMN_INSTANCE_CNT = null;
	private static String COLUMN_INSTANCE_DESC = null;
	private static String COLUMN_WORKER_YN = null;
	
	private QueryMessage messageReturn;

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
		logger.info("started InstanceManage");

		System.out.println(Thread.currentThread().getId()+" [Property] started");		

		// Create a JDBC client
		jdbc = JDBCClient.createShared(vertx, config(), "Instance_Manage");

		eb = vertx.eventBus();
		
		messageReturn = new QueryMessage();

		// property files 읽어오기
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
		
		TABLE_NAME = String.valueOf(configurationTable.getProperty("tableInst.name"));
		COLUMN_ID = String.valueOf(configurationTable.getProperty("tableInst.column1"));
		COLUMN_ROLE = String.valueOf(configurationTable.getProperty("tableInst.column2"));
		COLUMN_ROLE_INSTANCE_ID = String.valueOf(configurationTable.getProperty("tableInst.column3"));
		COLUMN_INSTANCE_NM = String.valueOf(configurationTable.getProperty("tableInst.column4"));
		COLUMN_INSTANCE_CNT = String.valueOf(configurationTable.getProperty("tableInst.column5"));
		COLUMN_INSTANCE_DESC = String.valueOf(configurationTable.getProperty("tableInst.column6"));
		COLUMN_WORKER_YN = String.valueOf(configurationTable.getProperty("tableInst.column7"));
		
		if (configurationMessage == null || configurationTable == null) {
			logger.warn("Sorry, unable to find property file(s)");
		}

		/**
		 * query문에 관한 수정 삭제 등록 조회
		 */
		eb.consumer("vertx.instance", message -> {

			JSONParser parser = new JSONParser();
			JSONObject json = new JSONObject();

			try {

				json = (JSONObject) parser.parse(message.body().toString());

			} catch (ParseException e) {

				e.printStackTrace();
				logger.error(e);
			}

			String address = json.get("address").toString();

			if (address.equals("getOneInstance")) {

				getOneInstance(message);

			} else if (address.equals("getAllInstance")) {

				getAllInstance(message);

			} else if (address.equals("addOneInstance")) {

				addOneInstance(message);

			} else if (address.equals("updateOneInstance")) {

				updateOneInstance(message);

			} else if (address.equals("deleteOneInstance")) {

				deleteOneInstance(message);
			} else if (address.equals("getIDInstance")) {
			
				getIDInstance(message);
			} 
		});


	}

	@Override
	public void stop() throws Exception {
		// Close the JDBC client.
		jdbc.close();
	}

	/**
	 * 
	 * @param queryFinalString = must first go through queryConverter(JSONObject,
	 *                         String)/ 단순한 쿼리문에서 queryConverter() 메소드를 통한 컨버팅이된 결과값
	 * @param connection       = jdbc connection
	 * @param message          = router message reply purposes
	 */
	private void queryConnectionAll(String queryFinalString, SQLConnection connection, Message<Object> message) {

		logger.info("entered queryConnectionAll()");

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

	private void deleteOneInstance(Message<Object> message) {

		logger.info("entered deleteOneInstance");

		try {
			JSONParser parser = new JSONParser();
			JSONObject parsedBodyData = new JSONObject();
			
			parsedBodyData = (JSONObject) parser.parse(message.body().toString());

			String id = parsedBodyData.get(COLUMN_ID).toString();
			String role = parsedBodyData.get(COLUMN_ROLE).toString();
			String role_instance_id = parsedBodyData.get(COLUMN_ROLE_INSTANCE_ID).toString();

			jdbc.getConnection(ar -> {
				SQLConnection connection = ar.result();

				String queryFinalString = "DELETE FROM " + TABLE_NAME 
										+ " WHERE id='" + id + "' " 
										+ " and role ='" + role + "' " 
										+ " and role_instance_id ='" + role_instance_id + "'";
				queryConnectionAll(queryFinalString, connection, message);


			});

		} catch (ParseException e) {

			logger.info(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);

		}

	}

	private void updateOneInstance(Message<Object> message) {

		logger.info("entered updateOneInstance");

		try {
			JSONParser parser = new JSONParser();
			String bodyDataBeforeParsing = message.body().toString();

			JSONObject parsedBodyData = (JSONObject) parser.parse(bodyDataBeforeParsing);
			
			String id = parsedBodyData.get(COLUMN_ID).toString();
			String role = parsedBodyData.get(COLUMN_ROLE).toString();
			String role_instance_id = parsedBodyData.get(COLUMN_ROLE_INSTANCE_ID).toString();
			String instance_nm = parsedBodyData.get(COLUMN_INSTANCE_NM).toString();
			String instance_cnt = parsedBodyData.get(COLUMN_INSTANCE_CNT).toString();
			String instance_desc = parsedBodyData.get(COLUMN_INSTANCE_DESC).toString();
			String worker_yn = parsedBodyData.get(COLUMN_WORKER_YN).toString();
				
			jdbc.getConnection(ar -> {

				SQLConnection connection = ar.result();

					
				String queryFinalString = "UPDATE "+ TABLE_NAME
						+ " SET id=" + id + ", " 
						+ COLUMN_ID + "= '" + id + "', " 
						+ COLUMN_ROLE + "= '" + role + "', " 
						+ COLUMN_ROLE_INSTANCE_ID + "= '" + role_instance_id + "', " 
						+ COLUMN_INSTANCE_NM + "= '" + instance_nm + "', " 
						+ COLUMN_INSTANCE_CNT + "= '" + instance_cnt + "', " 
						+ COLUMN_INSTANCE_DESC + "= '" + instance_desc + "', " 
						+ COLUMN_WORKER_YN + "= '" + worker_yn + "' " 
						+ " WHERE " + COLUMN_ID + "="+ id + " and " 
						+ COLUMN_ROLE + " = '" + role + "' and "
						+ COLUMN_ROLE_INSTANCE_ID + " = '" + role_instance_id + "'";
				
				queryConnectionAll(queryFinalString, connection, message);

				connection.close();
			});

		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.warn(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);

		} catch (NullPointerException e) {
			logger.warn(e);
			messageReturn.commonReturn(message, MessageReturn.RC_NULL_POINTER_EXCEPTION_CODE, MessageReturn.RC_NULL_POINTER_EXCEPTION_REASON);
		}

	}

	private void addOneInstance(Message<Object> message) {
		
		logger.info("entered addOneInstance");

		try {
			JSONParser parser = new JSONParser();

			JSONObject parsedBodyData = (JSONObject) parser.parse(message.body().toString());

			String id = parsedBodyData.get(COLUMN_ID).toString();
			String role = parsedBodyData.get(COLUMN_ROLE).toString();
			String role_instance_id = parsedBodyData.get(COLUMN_ROLE_INSTANCE_ID).toString();
			String instance_nm = parsedBodyData.get(COLUMN_INSTANCE_NM).toString();
			String instance_cnt = parsedBodyData.get(COLUMN_INSTANCE_CNT).toString();
			String instance_desc = parsedBodyData.get(COLUMN_INSTANCE_DESC).toString();
			String worker_yn = parsedBodyData.get(COLUMN_WORKER_YN).toString();
			
			jdbc.getConnection(ar -> {

				SQLConnection connection = ar.result();

				String queryFinalString = "INSERT INTO " + TABLE_NAME 
						+ " (" + COLUMN_ID  + ", " + COLUMN_ROLE + "," + COLUMN_ROLE_INSTANCE_ID + ", " + COLUMN_INSTANCE_NM + ", " + COLUMN_INSTANCE_CNT + ", " + COLUMN_INSTANCE_DESC + ", " + COLUMN_WORKER_YN +") VALUES (" 
						+ id + ", '"+ role + "', '"+ role_instance_id + "', '"+ instance_nm +"', '" + instance_cnt + "', '"+instance_desc + "', '"+ worker_yn+ "' )";

				queryConnectionAll(queryFinalString, connection, message);

			});

		} catch (ParseException e) {

			e.printStackTrace();
			logger.warn(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);

		}

	}

	private void getAllInstance(Message<Object> message) {

		logger.info("entered getAllInstance");

		jdbc.getConnection(ar -> {

			SQLConnection connection = ar.result();

			String queryFinalString = "SELECT * FROM " + TABLE_NAME;

			queryConnectionAll(queryFinalString, connection, message);

		});

	}

	private void getOneInstance(Message<Object> message) {

		logger.info("entered getOneInstance");

		JSONParser parser = new JSONParser();
		try {

			JSONObject json = (JSONObject) parser.parse(message.body().toString());

			String id = json.get(COLUMN_ID).toString();
			String role = json.get(COLUMN_ROLE).toString();
			String role_instance_id = json.get(COLUMN_ROLE_INSTANCE_ID).toString();
			
			jdbc.getConnection(ar -> {

				SQLConnection connection = ar.result();

				String queryFinalString = "SELECT * FROM "+ TABLE_NAME 
						+" WHERE id='" + id
						+"'and  role='" + role
						+"'and  role_instance_id='" + role_instance_id + "'";

				queryConnectionAll(queryFinalString, connection, message);

			});

		} catch (ParseException e) {

			e.printStackTrace();
			logger.error(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);

		}
	}
	
	private void getIDInstance(Message<Object> message) {

		logger.info("entered getIDInstance");

		JSONParser parser = new JSONParser();
		try {

			JSONObject json = (JSONObject) parser.parse(message.body().toString());

			String id = json.get(COLUMN_ID).toString();
			
			jdbc.getConnection(ar -> {

				SQLConnection connection = ar.result();

				String queryFinalString = "SELECT * FROM "+ TABLE_NAME 
						+" WHERE id=" + id;


				queryConnectionAll(queryFinalString, connection, message);

			});

		} catch (ParseException e) {

			e.printStackTrace();
			logger.error(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);

		}
	}
	
}
