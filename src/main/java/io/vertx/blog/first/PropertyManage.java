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
public class PropertyManage extends AbstractVerticle {

	private static Logger logger = Logger.getLogger(PropertyManage.class);

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
	private static String COLUMN_IP = null;
	private static String COLUMN_PORT = null;
	private static String COLUMN_ROLES = null;
	
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
		logger.info("started PropertyManage");

		System.out.println(Thread.currentThread().getId()+" [Property] started");		

		// Create a JDBC client
		jdbc = JDBCClient.createShared(vertx, config(), "Property_Manage");

		eb = vertx.eventBus();
		
		messageReturn = new QueryMessage();

		// property files ????????????
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
		
		TABLE_NAME = configurationTable.getProperty("tableProp.name").toString();
		COLUMN_ID = configurationTable.getProperty("tableProp.column1").toString();
		COLUMN_IP = configurationTable.getProperty("tableProp.column2").toString();
		COLUMN_PORT = configurationTable.getProperty("tableProp.column3").toString();
		COLUMN_ROLES = configurationTable.getProperty("tableProp.column4").toString();
		
		if (configurationMessage == null || configurationTable == null) {
			logger.warn("Sorry, unable to find property file(s)");
		}

		/**
		 * query?????? ?????? ?????? ?????? ?????? ??????
		 */
		eb.consumer("vertx.property", message -> {

			JSONParser parser = new JSONParser();
			JSONObject json = new JSONObject();

			try {

				json = (JSONObject) parser.parse(message.body().toString());

			} catch (ParseException e) {

				e.printStackTrace();
				logger.error(e);
			}

			String address = json.get("address").toString();

			if (address.equals("getOneProperty")) {

				getOneProperty(message);

			} else if (address.equals("getAllProperty")) {

				getAllProperty(message);

			} else if (address.equals("addOneProperty")) {

				addOneProperty(message);

			} else if (address.equals("updateOneProperty")) {

				updateOneProperty(message);

			} else if (address.equals("deleteOneProperty")) {

				deleteOneProperty(message);
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
	 *                         String)/ ????????? ??????????????? queryConverter() ???????????? ?????? ??????????????? ?????????
	 * @param connection       = jdbc connection
	 * @param message          = router message reply purposes
	 */
	private void queryConnectionAll(String queryFinalString, SQLConnection connection, Message<Object> message) {

		logger.info("entered queryConnectionAll()");

		// ????????? ???????????? ?????????, Select?????? ???????????? ???????????? insert,update,delete?????? ???????????? ?????????
		String firstSixLetters = queryFinalString.substring(0, 6);

		// select ?????? ??????
		if (firstSixLetters.equalsIgnoreCase("select")) {

			logger.info("select query");

			connection.query(queryFinalString, finalResult -> {

				// DB?????? ?????? ?????????
				if (finalResult.succeeded()) {
					
					// ????????? ?????????
					if (finalResult.result().getNumRows() > 0) {

						logger.info("Succeeded getting data from select query");
						
						List<JsonObject> resultList = finalResult.result().getRows().stream()
								.collect(Collectors.toList());
						
						message.reply(resultList.toString());


						// ????????? ????????? ????????? ??????
					} else {

						logger.warn("No data found for select query");
						messageReturn.commonReturn(message, MessageReturn.QC_NO_SELECT_DATA_FOUND_CODE, MessageReturn.QC_NO_SELECT_DATA_FOUND_REASON);
					}

					// ????????? ????????? ????????????
				} else {

					// DB?????? ???????????? error code??? error ??????
					SQLException ex = (SQLException) finalResult.cause();
					logger.error("error occurred from database" + ex);
					messageReturn.dbReturn(message, ex);
				}

				connection.close();

			});

			// Update, insert, ?????? delete???
		} else {

			logger.info("update/insert/delete query");

			connection.update(queryFinalString, finalResult -> {

				// DB?????? ?????? ?????????
				if (finalResult.succeeded()) {

					// ????????? ?????????
					if (finalResult.result().getUpdated() > 0) {

						logger.info("succeeded performing insert/update/delete query");
						messageReturn.commonReturn(message, MessageReturn.QC_SUCCESS_DATA_AFFECTION_CODE, MessageReturn.QC_SUCCESS_DATA_AFFECTION_REASON);
						// ????????? ????????? ????????? ??????
					} else {

						logger.warn("No affect on data from insert/update/delete query");
						messageReturn.commonReturn(message, MessageReturn.QC_FAIL_DATA_AFFECTION_CODE, MessageReturn.QC_FAIL_DATA_AFFECTION_REASON);
					}

					// ????????? ????????? ????????????
				} else {

					// DB?????? ???????????? error code??? error ??????
					SQLException ex = (SQLException) finalResult.cause();
					logger.error("error occurred from insert/update/delete query : " + ex);
					messageReturn.dbReturn(message, ex);

				}

				connection.close();
			});
		}
	}

	private void deleteOneProperty(Message<Object> message) {

		logger.info("entered deleteOneProperty");

		try {
			
			JSONParser parser = new JSONParser();
			JSONObject json = new JSONObject();
			json = (JSONObject) parser.parse(message.body().toString());

			String id = json.get(COLUMN_ID).toString();

			jdbc.getConnection(ar -> {
				
				SQLConnection connection = ar.result();
				String queryFinalString = "DELETE FROM " + TABLE_NAME +" WHERE id='" + id + "'";
				queryConnectionAll(queryFinalString, connection, message);

			});

		} catch (ParseException e) {

			logger.info(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);
		}

	}

	private void updateOneProperty(Message<Object> message) {

		logger.info("entered updateOneProperty");

		try {
			JSONParser parser = new JSONParser();
			String bodyDataBeforeParsing = message.body().toString();

			JSONObject parsedBodyData = (JSONObject) parser.parse(bodyDataBeforeParsing);
			String id = parsedBodyData.get(COLUMN_ID).toString();
			String ip = parsedBodyData.get(COLUMN_IP).toString();
			String port = parsedBodyData.get(COLUMN_PORT).toString();
			String roles = parsedBodyData.get(COLUMN_ROLES).toString();

				
			jdbc.getConnection(ar -> {

				SQLConnection connection = ar.result();

					
					String queryFinalString = "UPDATE "+ TABLE_NAME
							+ " SET id=" + id + ", " 
							+ COLUMN_ID + "= '" + id + "', " 
							+ COLUMN_IP + "= '" + ip + "', " 
							+ COLUMN_PORT + "= '" + port + "', " 
							+ COLUMN_ROLES + "= '" + roles + "' " 
							+ " WHERE id="+ id;

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

	private void addOneProperty(Message<Object> message) {
		
		logger.info("entered addOneProperty");

		try {

			JSONParser parser = new JSONParser();

			JSONObject resultList = (JSONObject) parser.parse(message.body().toString());

			String id = resultList.get(COLUMN_ID).toString();
			String ip = resultList.get(COLUMN_IP).toString();
			String port = resultList.get(COLUMN_PORT).toString();
			String roles = resultList.get(COLUMN_ROLES).toString();
			
			jdbc.getConnection(ar -> {

				SQLConnection connection = ar.result();

				String queryFinalString = "INSERT INTO " + TABLE_NAME 
						+ " (" + COLUMN_ID  + ", " + COLUMN_IP + "," + COLUMN_PORT + ", " + COLUMN_ROLES + ") VALUES (" 
						+ id + ", '"+ ip + "', '"+ port +"', '" + roles + "')";

				queryConnectionAll(queryFinalString, connection, message);

			});

		} catch (ParseException e) {

			e.printStackTrace();
			logger.warn(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);

		}

	}

	private void getAllProperty(Message<Object> message) {

		logger.info("entered getAllProperty");

		jdbc.getConnection(ar -> {

			SQLConnection connection = ar.result();

			String queryFinalString = "SELECT * FROM " + TABLE_NAME;

			queryConnectionAll(queryFinalString, connection, message);

		});

	}

	private void getOneProperty(Message<Object> message) {

		logger.info("entered getOneProperty");

		try {
			JSONParser parser = new JSONParser();

			JSONObject json = (JSONObject) parser.parse(message.body().toString());

			jdbc.getConnection(ar -> {

				SQLConnection connection = ar.result();

				String queryFinalString = "SELECT * FROM "+ TABLE_NAME +" WHERE id=" + json.get("id");

				queryConnectionAll(queryFinalString, connection, message);

			});

		} catch (ParseException e) {

			e.printStackTrace();
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);

		}
	}
	
}
