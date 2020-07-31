package io.vertx.common;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;

public class XmlConvert {

	private static Logger logger = Logger.getLogger(XmlConvert.class);

	private boolean success;
	private String xmlResult;
	
	public XmlConvert(boolean success, String xmlResult) {
		this.success = success;
		this.xmlResult = xmlResult;
	}

	public static XmlConvert xmlConvert(String beforeXml){
		
		String xmlResult;
		try {
			
			JSONArray jsonData2 = new JSONArray(beforeXml);
			
			JSONObject finaljson = new JSONObject();
			finaljson.put("data", jsonData2);
			
			//System.out.println("data before xml : " + finaljson);
			
			JSONArray finalArray = new JSONArray();
			finalArray.put(finaljson);
			
			xmlResult = XML.toString(finalArray, "resultData");
			xmlResult = filterDecode(xmlResult);
			
			//System.err.println("data after xml ssssssssssss: " + xmlResult);
			
		} catch(JSONException e) {
			
			return new XmlConvert(false, null);
		}
		
		return new XmlConvert(true, xmlResult);
		
	}
	
	public static String filterDecode(String url){

		   url = url.replaceAll("&amp;", "&")
		     .replaceAll("&lt;", "<")
		     .replaceAll("&gt;", ">")
		     .replaceAll("&apos;", "\'")
		     .replaceAll("&quot;", "\"")
		     .replaceAll("&nbsp;", " ")
		     .replaceAll("&copy;", "@")
		     .replaceAll("&reg;", "?");
		   return url;
	}
	
	public boolean isSuccess() {
		return success;
	}


	public void setSuccess(boolean success) {
		this.success = success;
	}


	public String getXmlResult() {
		return xmlResult;
	}


	public void setXmlResult(String xmlResult) {
		this.xmlResult = xmlResult;
	}



}
