package io.vertx.blog.first;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import io.vertx.common.AES256Util;
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
public class QueryManage extends AbstractVerticle {

	private static Logger logger = Logger.getLogger(QueryManage.class);

	private JDBCClient jdbc;

	private EventBus eb;

	private SharedData sharedData;

	public JSONObject localSharedDataQueryJson = new JSONObject();

	// local property file from classpath of vertx.properties
	private PropertiesConfiguration configurationMessage = null;
	private PropertiesConfiguration configurationTable = null;

	private static final String SERVER_FILE = "file:/data/router.properties";
	private static final String CLASSPATH_FILE_MESSAGE = "message.properties";
	private static final String CLASSPATH_FILE_TABLE = "table.properties";

	private static String TABLE_NAME = null;
	private static String QUERY_STRING = null;
	private static String ROLE = null;
			
	private static String COL_ID = "queryId";
	private static String COL_QSTR = "queryString";
	private static String COL_DESC = "descript";
	private static String COL_SQLT = "sqlType";
	private static String COL_ROLE = "role";
	private static final String ADDR = "address";
	
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
		
		logger.info("started QueryManage");

		//System.out.println(Thread.currentThread().getId()+" [QueryManage] started");
		
		if("oracle".equals(config().getString("db"))) {
			COL_ID = "QUERYID";
			COL_QSTR = "QUERYSTRING";
			COL_DESC = "DESCRIPT";
			COL_SQLT = "SQLTYPE";
			COL_ROLE = "ROLE";
		}
		
		// Create a JDBC client
		jdbc = JDBCClient.createShared(vertx, config(), "Query_Manage");

		eb = vertx.eventBus();

		sharedData = vertx.sharedData();

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
		
		TABLE_NAME = String.valueOf(configurationTable.getProperty("table.name"));
		
		
		if (configurationMessage == null || configurationTable == null) {
			logger.warn("Sorry, unable to find property file(s)");
		}
		
		ROLE = config().getString("role").toLowerCase().replaceAll("'", "");

		// Gets query Data from db and sets to local and sharedata
		jdbc.getConnection(ar -> {

			JSONParser parser = new JSONParser();
			SQLConnection connection = ar.result();

//			connection.query("SELECT * FROM " + TABLE_NAME + " WHERE "+ COL_ROLE +"= '"+ROLE+"'", results -> {
			connection.query("SELECT * FROM " + TABLE_NAME, results -> {
				// ?????? ?????? ??????
				List<JsonObject> fromDBQueryList = results.result().getRows();

				JSONObject tempData = new JSONObject();
				String tempId = "";
				String tempQuery = "";
				String sqlType = "";

				logger.info("before saving to SharedData");

				for (int i = 0; i < fromDBQueryList.size(); i++) {

					try {

						tempData = (JSONObject) parser
								.parse(fromDBQueryList.get(i).toString());

					} catch (ParseException e) {

						e.printStackTrace();
						logger.error(e);
					}
					
					tempId = tempData.get(COL_ID).toString();
					String tempEnc = tempData.get(COL_QSTR).toString();
					sqlType = tempData.get(COL_SQLT).toString().toLowerCase();
					
					try {
						String tempDec = AES256Util.AES_Decode(tempEnc);
						//System.out.println("id : "+tempId+" queryString : "+tempDec);
						
						tempQuery = tempDec;
						
					} catch (InvalidKeyException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (NoSuchAlgorithmException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (NoSuchPaddingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (InvalidAlgorithmParameterException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IllegalBlockSizeException e) {
						// TODO Auto-generated catch block
						
						//????????? ?????? ?????? ??????????????? ?????? ?????? 
						try {

							tempData = (JSONObject) parser
									.parse(fromDBQueryList.get(i).toString());

						} catch (ParseException ex) {

							ex.printStackTrace();
							logger.error(ex);
						}						
						
						tempId = tempData.get(COL_ID).toString();
						tempQuery = tempData.get(COL_QSTR).toString();
						sqlType = tempData.get(COL_SQLT).toString().toLowerCase();
						
					} catch (BadPaddingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					//System.out.println("sqlType : "+sqlType+" id : "+tempId+" queryString : "+tempQuery);
					
					this.setAsyncMapT(sharedData, sqlType, tempId, tempQuery);

				}
				
				/*sharedData.getAsyncMap("query", result -> {

					if (result.succeeded()) {

						AsyncMap<Object, Object> map = result.result();
						
						for (int i = 0; i < tempDataList.size(); i++) {
							
							JSONObject temp = tempDataList.get(i);
							
							//System.out.println("temp : "+temp.toJSONString());
							
							map.put(temp.get("id").toString(), temp.get("queryString"), resPut -> {

								if (resPut.succeeded()) {

									// Successfully got the value
									logger.info("saved SharedData data");

								} else {

									logger.error("failed putting value of " + temp.get("id") + "from query");
								}
							});
						}

					} else {

						logger.error("failed receiving map value from query");
					}
				});*/
			});

			//}
		});

		/**
		 * query?????? ?????? ?????? ?????? ?????? ??????
		 */
		eb.consumer("vertx.queryManage", message -> {

			JSONParser parser = new JSONParser();
			JSONObject json = new JSONObject();

			try {

				json = (JSONObject) parser.parse(message.body().toString());

			} catch (ParseException e) {

				e.printStackTrace();
				logger.error(e);
			}

			String address = json.get(ADDR).toString();

			if (address.equals("getOneQueryManage")) {

				getOneQueryManage(message);

			} else if (address.equals("getAllQueryManage")) {

				getAllQueryManage(message);

			} else if (address.equals("addOneQueryManage")) {

				addOneQueryManage(message);

			} else if (address.equals("updateOneQueryManage")) {

				updateOneQueryManage(message);

			} else if (address.equals("deleteOneQueryManage")) {

				deleteOneQueryManage(message);
			} else if (address.equals("searchEqualQueryManage")) {
			
				searchEqualQueryManage(message);
			} else if (address.equals("searchLikeQueryManage")) {
			
				searchLikeQueryManage(message);
			} else if (address.equals("getAllQueryManageCount")) {
			
				getAllQueryManageCount(message);
			} else if (address.equals("getAllQueryManagePage")) {
			
				getAllQueryManagePage(message);
			}
		});

		eb.consumer("vertx.sharedDataQueryUpdate", message -> {

			JSONParser parser = new JSONParser();
			JSONObject json = new JSONObject();

			try {

				json = (JSONObject) parser.parse(message.body().toString());

			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			String address = json.get(ADDR).toString();

			if (address.equals("sharedDataQueryUpdateAll")) {

				sharedDataQueryUpdateAll(message);

			} else if (address.equals("sharedDataQueryUpdateOne")) {

				sharedDataQueryUpdateOne(message);
			}
		});

	}

	private void sharedDataQueryUpdateOne(Message<Object> message) {

		logger.info("entered sharedDataQueryUpdateOne");
		
		jdbc.getConnection(ar -> {

			SQLConnection connection = ar.result();
			JSONParser parser = new JSONParser();
			JSONObject messageJson = new JSONObject();

			try {

				messageJson = (JSONObject) parser.parse(message.body().toString());

			} catch (ParseException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			String queryFinalString = "SELECT * FROM "+TABLE_NAME+" WHERE "+ COL_ID +"= '" + messageJson.get(COL_ID) + "' AND "+ COL_ROLE +"= '"+ROLE+"'";

			connection.query(queryFinalString, finalResult -> {

				// DB?????? ?????? ?????????
				if (finalResult.succeeded()) {
					
					if(finalResult.result().getNumRows() > 0) {
						
						logger.info("Succeeded getting data from select query");
						// ?????? ?????? ??????
						List<JsonObject> fromDBQueryList = finalResult.result().getRows();
						
						JSONObject tempData = new JSONObject();
						String tempId = "";
						String tempQuery = "";
						String sqlType = "";

						for (int i = 0; i < fromDBQueryList.size(); i++) {

							try {

								tempData = (JSONObject) parser
										.parse(fromDBQueryList.get(i).toString());

							} catch (ParseException e) {

								e.printStackTrace();
								logger.error(e);
							}

							tempId = tempData.get(COL_ID).toString();
							tempQuery = tempData.get(COL_QSTR).toString();
							sqlType = tempData.get(COL_SQLT).toString().toLowerCase();
						}

						
						
						try {
							String afterConverted = AES256Util.AES_Decode(tempQuery);
							
							this.setAsyncMapT(sharedData, sqlType, tempId, afterConverted);

							messageReturn.commonReturn(message, MessageReturn.QC_UPDATED_SHAREDDATA_CODE, MessageReturn.QC_UPDATED_SHAREDDATA_REASON);

							
						} catch (InvalidKeyException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (UnsupportedEncodingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (NoSuchAlgorithmException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (NoSuchPaddingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (InvalidAlgorithmParameterException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IllegalBlockSizeException e) {
							// TODO Auto-generated catch block
							
							this.setAsyncMapT(sharedData, sqlType, tempId, tempQuery);

							messageReturn.commonReturn(message, MessageReturn.QC_UPDATED_SHAREDDATA_CODE, MessageReturn.QC_UPDATED_SHAREDDATA_REASON);
							
						} catch (BadPaddingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
					} else {

						logger.warn("No data found for select query");
						messageReturn.commonReturn(message, MessageReturn.QC_NO_SELECT_DATA_FOUND_CODE, MessageReturn.QC_NO_SELECT_DATA_FOUND_REASON);
					}					

					// ????????? ????????? ????????????
				} else {

					// DB?????? ???????????? error code??? error ??????
					SQLException ex = (SQLException) finalResult.cause();
					messageReturn.dbReturn(message, ex);

				}

				connection.close();

			});
		});

	}

	private void sharedDataQueryUpdateAll(Message<Object> message) {

		logger.info("entered sharedDataQueryUpdateAll");

		jdbc.getConnection(ar -> {

			SQLConnection connection = ar.result();

			JSONParser parser = new JSONParser();
			String queryFinalString = "SELECT * FROM "+TABLE_NAME+" WHERE "+ COL_ROLE + "= '"+ROLE+"'";

			connection.query(queryFinalString, finalResult -> {

				// DB?????? ?????? ?????????
				if (finalResult.succeeded()) {					
					
					if(finalResult.result().getNumRows() > 0) {
						
						// ?????? ?????? ??????
						List<JsonObject> fromDBQueryList = finalResult.result().getRows();

						JSONObject tempData = new JSONObject();
						String tempId = "";
						String tempQuery = "";
						String sqlType = "";

						for (int i = 0; i < fromDBQueryList.size(); i++) {

							try {

								tempData = (JSONObject) parser
										.parse(fromDBQueryList.get(i).toString());

							} catch (ParseException e) {

								e.printStackTrace();
								logger.error(e);
							}

							tempId = tempData.get(COL_ID).toString();
							tempQuery = tempData.get(COL_QSTR).toString();
							sqlType = tempData.get(COL_SQLT).toString().toLowerCase();
							
							try {
								String afterConverted = AES256Util.AES_Decode(tempQuery);
								
								this.setAsyncMapT(sharedData, sqlType, tempId, afterConverted);
								
							} catch (InvalidKeyException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (UnsupportedEncodingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (NoSuchAlgorithmException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (NoSuchPaddingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (InvalidAlgorithmParameterException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IllegalBlockSizeException e) {
								// TODO Auto-generated catch block
								this.setAsyncMapT(sharedData, sqlType, tempId, tempQuery);

								messageReturn.commonReturn(message, MessageReturn.QC_UPDATED_SHAREDDATA_CODE, MessageReturn.QC_UPDATED_SHAREDDATA_REASON);

							} catch (BadPaddingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

						}

						logger.info("Succeeded getting data from select query");

						messageReturn.commonReturn(message, MessageReturn.QC_UPDATED_SHAREDDATA_CODE, MessageReturn.QC_UPDATED_SHAREDDATA_REASON);
						
					} else {
						
						logger.warn("No data found for select query");
						messageReturn.commonReturn(message, MessageReturn.QC_NO_SELECT_DATA_FOUND_CODE, MessageReturn.QC_NO_SELECT_DATA_FOUND_REASON);

					}

					// ????????? ????????? ????????????
				} else {

					// DB?????? ???????????? error code??? error ??????
					SQLException ex = (SQLException) finalResult.cause();
					messageReturn.dbReturn(message, ex);

				}

			});
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

						List<JsonObject> resultList = finalResult.result().getRows().stream()
								.collect(Collectors.toList());
						
						JsonObject toSharedData = new JsonObject();

						for(int i = 0; i < resultList.size(); i++) {
							
							try {
								
								JsonObject tempPlace = new JsonObject(resultList.get(i).toString());
								
								String tempId = String.valueOf(tempPlace.getValue(COL_ID));
								tempPlace.remove(COL_ID);
								
								String beforeConverted = tempPlace.getValue(COL_QSTR).toString();
								String afterConverted = AES256Util.AES_Decode(beforeConverted);
								
								tempPlace.put(COL_QSTR, afterConverted);
								
								toSharedData.put(tempId, tempPlace);

								resultList.get(i).put(COL_QSTR, afterConverted);

							} catch (InvalidKeyException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (UnsupportedEncodingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (NoSuchAlgorithmException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (NoSuchPaddingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (InvalidAlgorithmParameterException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IllegalBlockSizeException e) {
								
								//??????????????? ?????? ?????? 
								JsonObject tempPlace = new JsonObject(resultList.get(i).toString());
								
								String tempId = String.valueOf(tempPlace.getValue(COL_ID));
								String beforeConverted = tempPlace.getValue(COL_QSTR).toString();
								
								tempPlace.put(COL_QSTR, beforeConverted);

								toSharedData.put(tempId, tempPlace);

							} catch (BadPaddingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
						}
						message.reply(resultList.toString());
						
						logger.info("Succeeded getting data from select query");

						// ????????? ????????? ????????? ??????
					} else {

						logger.warn("No data found for select query");
						messageReturn.commonReturn(message, MessageReturn.QC_NO_SELECT_DATA_FOUND_CODE, MessageReturn.QC_NO_SELECT_DATA_FOUND_REASON);
					}

					// ????????? ????????? ????????????
				} else {

					// DB?????? ???????????? error code??? error ??????
					SQLException ex = (SQLException) finalResult.cause();
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
					messageReturn.dbReturn(message, ex);

				}

				connection.close();
			});
		}
	}

	private void deleteOneQueryManage(Message<Object> message) {

		logger.info("entered deleteOneQueryManage");

		JSONParser parser = new JSONParser();
		JSONObject json = new JSONObject();

		try {
			json = (JSONObject) parser.parse(message.body().toString());

			String id = json.get(COL_ID).toString();
			String sqlType = json.get(COL_SQLT).toString().toLowerCase();

			jdbc.getConnection(ar -> {
				SQLConnection connection = ar.result();

//				String queryFinalString = "DELETE FROM " + TABLE_NAME +" WHERE "+ COL_ID +" ='" + id + "'";
				String queryFinalString = "DELETE FROM " + TABLE_NAME +" WHERE "+COL_ID+"='" + id + "'";

				queryConnectionAll(queryFinalString, connection, message);
				
				//System.out.println("sqlType : "+sqlType+" id : "+id);
				
				this.remAsyncMapT(sharedData, sqlType, id);
			});

		} catch (ParseException e) {

			logger.info(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);

		}

	}

	private void updateOneQueryManage(Message<Object> message) {

		logger.info("entered updateOneQueryManage");

		JSONParser parser = new JSONParser();
		String bodyDataBeforeParsing = message.body().toString();

		try {

			JSONObject parsedBodyData = (JSONObject) parser.parse(bodyDataBeforeParsing);
			String id = parsedBodyData.get(COL_ID).toString();
			String queryString = parsedBodyData.get(COL_QSTR).toString();
			String desc = parsedBodyData.get(COL_DESC).toString();
			String sqlType = parsedBodyData.get(COL_SQLT).toString().toLowerCase();
			String role = parsedBodyData.get(COL_ROLE).toString().toLowerCase();
			 
			try {
				String encodedValue = AES256Util.AES_Encode( queryString );
				
				jdbc.getConnection(ar -> {

					SQLConnection connection = ar.result();
					
					String queryFinalString = "UPDATE "+ TABLE_NAME
					+ " SET "+ COL_ID +"='" + id + "', " 
					+ COL_QSTR + "= '" + encodedValue + "', " 
					+ COL_DESC + " = '" + desc + "', " 
					+ COL_SQLT + " = '" + sqlType + "', " 
					+ COL_ROLE + " = '" + role + "' " 
//					+ "WHERE "+ COL_ID + " = '"+ id +"' AND " + COL_ROLE + " = '" + ROLE + "'";
					+ "WHERE " + COL_ID + "= '"+ id +"'";

					queryConnectionAll(queryFinalString, connection, message);
					
					//System.out.println("sqlType : "+sqlType+" id : "+id+" queryString : "+queryString);
					
					this.setAsyncMapT(sharedData, sqlType, id, queryString);

					connection.close();
				});
				
				
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			logger.warn(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);

		} catch (NullPointerException e) {
			logger.warn(e);
		}

	}

	private void addOneQueryManage(Message<Object> message) {

		logger.info("entered addOneQueryManage");

		JSONParser parser = new JSONParser();

		try {

			JSONObject resultList = (JSONObject) parser.parse(message.body().toString());
			
			try {
				String queryString = resultList.get(COL_QSTR).toString();
				String encodedValue = AES256Util.AES_Encode(queryString);
				String id = resultList.get(COL_ID).toString();
				String desc = resultList.get(COL_DESC).toString();
				String sqlType = resultList.get(COL_SQLT).toString().toLowerCase();				
				String role = resultList.get(COL_ROLE).toString().toLowerCase();				

				jdbc.getConnection(ar -> {

					SQLConnection connection = ar.result();
					
					String queryFinalString = "INSERT INTO "+TABLE_NAME+" ("+ COL_ID + ", "+COL_QSTR+", " + COL_DESC + ", " + COL_SQLT + ", "+COL_ROLE +" ) VALUES ('"+id+"', '"
							+encodedValue+"', '"+desc+"', '"+sqlType+"', '"+role+"')";
					
					//System.out.println("queryFinalString : " + queryFinalString);

					queryConnectionAll(queryFinalString, connection, message);
					
					//System.out.println("sqlType : "+sqlType+" id : "+id+" queryString : "+queryString);
					
					this.setAsyncMapT(sharedData, sqlType, id, queryString );
				});
				
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} catch (ParseException e) {

			logger.warn(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);

		}

	}

	private void getAllQueryManage(Message<Object> message) {

		logger.info("entered getAllQueryManage");

		jdbc.getConnection(ar -> {

			SQLConnection connection = ar.result();

			String queryFinalString = "SELECT * FROM "+TABLE_NAME;

			queryConnectionAll(queryFinalString, connection, message);

		});

	}
	
	private void getAllQueryManagePage(Message<Object> message) {
		
		logger.info("entered getAllQueryManagePage");
		JSONParser parser = new JSONParser();

		jdbc.getConnection(ar -> {
			
			SQLConnection connection = ar.result();
			try {

				JSONObject json = (JSONObject) parser.parse(message.body().toString());

				String startList = json.get("startList").toString();
				String listSize = json.get("listSize").toString();
				
//				String queryFinalString = "SELECT * FROM "+TABLE_NAME + " limit " + startList + ", " + listSize ;
//				System.err.println("?????? ?????? ::: " + queryFinalString); 
//				queryConnectionAll(queryFinalString, connection, message);
				
				///////////////////////////
				String queryFinalString = "SELECT * FROM " + TABLE_NAME;
			
				JSONObject searchJson = new JSONObject();
				
				if(json.containsKey(COL_SQLT)) {
					if(!"".equals(json.get(COL_SQLT))) {
						searchJson.put(COL_SQLT, json.get(COL_SQLT));
					} 
				}
				if(json.containsKey(COL_ID)) {
					if(!"".equals(json.get(COL_ID))) {
						searchJson.put(COL_ID, json.get(COL_ID));
						
					}
				}
				if(json.containsKey(COL_ROLE)) {
					if(!"".equals(json.get(COL_ROLE))) {
						searchJson.put(COL_ROLE, json.get(COL_ROLE));

					}
				}
				if(json.containsKey(COL_QSTR)) {
					if(!"".equals(json.get(COL_QSTR))) {
						searchJson.put(COL_QSTR, json.get(COL_QSTR));

					}
				} 
				if(json.containsKey(COL_DESC)) {
					if(!"".equals(json.get(COL_DESC))) {
						searchJson.put(COL_DESC, json.get(COL_DESC));

					}
				}

				//order by
				String orderBy = "";
				if(json.containsKey("sortName")) {
					
					if(!"".equals(json.get("sortName"))) {
						orderBy = " ORDER BY " + json.get("sortName").toString() + " " + json.get("sortOrder");
						
					}
				}
				
				System.err.println("this is json from queryRoute === " + json);
				
				if("".equals(json.get("search"))) {
					
					queryFinalString = "SELECT * FROM "+TABLE_NAME  + orderBy + " limit " + startList + ", " + listSize;
					
				} else {
					
					String edit = "SELECT * FROM " + TABLE_NAME + " WHERE ";
					
					for(Object key : searchJson.keySet()) {
						edit = edit + key.toString() + " LIKE '%" + searchJson.get(key).toString() + "%' and ";
						System.err.println(edit);
					}
					
					
					edit = edit.substring(0, edit.length() - 4) + orderBy +" limit " + startList + ", " + listSize ;
					System.err.println(edit);
					queryFinalString = edit;
				}
				queryConnectionAll(queryFinalString, connection, message);

			
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			
			
		});
		
	}

	private void getOneQueryManage(Message<Object> message) {

		logger.info("entered getOneQueryManage");

		JSONParser parser = new JSONParser();
		try {

			JSONObject json = (JSONObject) parser.parse(message.body().toString());

			jdbc.getConnection(ar -> {

				SQLConnection connection = ar.result();

				String queryFinalString = "SELECT * FROM "+TABLE_NAME+" WHERE "+ COL_ID + "= '" + json.get(COL_ID).toString() + "'";

				queryConnectionAll(queryFinalString, connection, message);

			});

		} catch (ParseException e) {

			logger.warn(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);

		}
	}
	
	private void searchEqualQueryManage(Message<Object> message) {
		
		logger.info("entered searchQueryManage");
		
		JSONParser parser = new JSONParser();
		try {
			
			JSONObject json = (JSONObject) parser.parse(message.body().toString());
			json.remove("address");
			
			jdbc.getConnection(ar -> {
				
				SQLConnection connection = ar.result();
			
				String edit = "SELECT * FROM " + TABLE_NAME + " WHERE ";
				
				
				if(json.containsKey(COL_SQLT) && json.containsKey("role")) {
					
					edit = edit + COL_SQLT +" = '" + json.get(COL_SQLT) 
						+ "' and " + COL_ROLE + " ='" + json.get(COL_ROLE) + "'";
					
				} else if(json.containsKey("sqlType")) {
					
					edit = edit + COL_SQLT +" = '" + json.get(COL_SQLT) 
					+ "'";
					
				} else {
					
					edit = edit  + COL_ROLE + " ='" + json.get(COL_ROLE) 
					+ "'";
					
				}
				
				String startList = json.get("startList").toString();
				String listSize = json.get("listSize").toString();
				
				edit = edit + " LIMIT " + startList + ", " + listSize;
				
				System.err.println(edit);
				//String queryFinalString = "SELECT * FROM "+TABLE_NAME+" WHERE ID= '" + json.get("id").toString() + "' AND ROLE= '"+ROLE+"'";
				String queryFinalString = edit;
				
				queryConnectionAll(queryFinalString, connection, message);
				
			});
			
		} catch (ParseException e) {
			
			logger.warn(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);
			
		}
	}
	
	private void searchLikeQueryManage(Message<Object> message) {
		
		logger.info("entered searchQueryManage");
		
		JSONParser parser = new JSONParser();
		try {
			
			JSONObject json = (JSONObject) parser.parse(message.body().toString());
			json.remove("address");
			
			jdbc.getConnection(ar -> {
				
				SQLConnection connection = ar.result();
				
				String edit = "SELECT * FROM " + TABLE_NAME + " WHERE ";
				
				
				if(json.containsKey(COL_QSTR) && json.containsKey(COL_DESC)) {
					
					edit = edit + COL_QSTR + " LIKE '%" + json.get(COL_QSTR) 
					+ "%' AND " + COL_DESC + " LIKE '%" + json.get(COL_DESC) + "%'";
					
				} else if(json.containsKey(COL_QSTR)) {
					
					edit = edit + COL_QSTR + " LIKE '%" + json.get(COL_QSTR) 
					+ "%'";
					
				} else {
					
					edit = edit + COL_DESC + " LIKE '%" + json.get(COL_DESC) 
					+ "%'";
					
				}
				String startList = json.get("startList").toString();
				String listSize = json.get("listSize").toString();
				
				edit = edit + " LIMIT " + startList + ", " + listSize;
				System.err.println(edit);
				//String queryFinalString = "SELECT * FROM "+TABLE_NAME+" WHERE ID= '" + json.get("id").toString() + "' AND ROLE= '"+ROLE+"'";
				String queryFinalString = edit;
				
				queryConnectionAll(queryFinalString, connection, message);
				
			});
			
		} catch (ParseException e) {
			
			logger.warn(e);
			messageReturn.commonReturn(message, MessageReturn.QC_PARSE_EXCEPTION_CODE, MessageReturn.QC_PARSE_EXCEPTION_REASON);
			
		}
	}
	
	private void getAllQueryManageCount(Message<Object> message) {

		logger.info("entered getAllQueryManageCount");

		jdbc.getConnection(ar -> {

			SQLConnection connection = ar.result();
			try {
				String queryFinalString = "SELECT COUNT(*) AS count FROM " + TABLE_NAME;
				JSONParser parser = new JSONParser();

			
				JSONObject json = (JSONObject) parser.parse(message.body().toString());
				JSONObject searchJson = new JSONObject();
				
				if(json.containsKey(COL_SQLT)) {
					if(!"".equals(json.get(COL_SQLT))) {
						searchJson.put(COL_SQLT, json.get(COL_SQLT));
					} 
				}
				if(json.containsKey(COL_ID)) {
					if(!"".equals(json.get(COL_ID))) {
						searchJson.put(COL_ID, json.get(COL_ID));
						
					}
				}
				if(json.containsKey(COL_ROLE)) {
					if(!"".equals(json.get(COL_ROLE))) {
						searchJson.put(COL_ROLE, json.get(COL_ROLE));

					}
				}
				if(json.containsKey(COL_QSTR)) {
					if(!"".equals(json.get(COL_QSTR))) {
						searchJson.put(COL_QSTR, json.get(COL_QSTR));

					}
				} 
				if(json.containsKey(COL_DESC)) {
					if(!"".equals(json.get(COL_DESC))) {
						searchJson.put(COL_DESC, json.get(COL_DESC));

					}
				}
				
				String orderBy = "";
				if(json.containsKey("sortName")) {
					
					//order by
					if(!"".equals(json.get("sortName"))) {
						orderBy = " ORDER BY " + json.get("sortName").toString() + " " + json.get("sortOrder");
						
					}
				}
//				System.err.println("this is my address ==" + search);
				System.err.println("this is json from queryRoute === " + json);
				
				if("".equals(json.get("search"))) {
					
					queryFinalString = "SELECT COUNT(*) AS count FROM " + TABLE_NAME + orderBy;
					
				} else {
					
					String edit = "SELECT COUNT(*) AS count FROM " + TABLE_NAME + " WHERE ";
					
					for(Object key : searchJson.keySet()) {
						edit = edit + key.toString() + " LIKE '%" + searchJson.get(key).toString() + "%' and ";
						System.err.println(edit);
					}
					
					edit = edit.substring(0, edit.length() - 4) + orderBy;
					System.err.println(edit);
					queryFinalString = edit;
				}

				connection.query(queryFinalString, finalResult -> {
	
					// DB?????? ?????? ?????????
					if (finalResult.succeeded()) {
	
						// ????????? ?????????
						if (finalResult.result().getNumRows() > 0) {
	
							List<JsonObject> resultList = finalResult.result().getRows().stream()
									.collect(Collectors.toList());
							
							String value = resultList.get(0).getValue("count").toString();
							message.reply(value.toString());
							System.out.println("count ==== " + value);
							logger.info("Succeeded getting data from select query");
	
							// ????????? ????????? ????????? ??????
						} else {
	
							logger.warn("No data found for select query");
							messageReturn.commonReturn(message, MessageReturn.QC_NO_SELECT_DATA_FOUND_CODE, MessageReturn.QC_NO_SELECT_DATA_FOUND_REASON);
						}
	
						// ????????? ????????? ????????????
					} else {
	
						// DB?????? ???????????? error code??? error ??????
						SQLException ex = (SQLException) finalResult.cause();
						messageReturn.dbReturn(message, ex);
	
					}
	
					connection.close();
	
				});
				
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

	}
	
	public void setAsyncMapT(SharedData sharedData, String asyncMapName, String key, Object insertValue) {

		sharedData.getAsyncMap(asyncMapName, result -> {

			if (result.succeeded()) {

				AsyncMap<Object, Object> map = result.result();

				map.put(key, insertValue, resPut -> {

					if (resPut.succeeded()) {

						// Successfully got the value
						//logger.info("saved SharedData data");

					} else {

						logger.error("failed putting value of " + key + "from " + asyncMapName);
					}
				});

			} else {

				logger.error("failed receiving map value from " + asyncMapName);
			}
		});

	}
	
	public void remAsyncMapT(SharedData sharedData, String asyncMapName, String key) {

		sharedData.getAsyncMap(asyncMapName, result -> {

			if (result.succeeded()) {

				AsyncMap<Object, Object> map = result.result();

				map.remove(key, resPut -> {

					if (resPut.succeeded()) {

						// Successfully got the value
						logger.info("removed SharedData data");

					} else {

						logger.error("failed removing value of " + key + "from " + asyncMapName);
					}
				});

			} else {

				logger.error("failed removing map value from " + asyncMapName);
			}
		});

	}

}
