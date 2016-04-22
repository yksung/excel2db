package com.wisenut.worker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wisenut.common.WNProperties;
import com.wisenut.util.StringUtil;

import QueryAPI530.Search;

public class WiseSearchWorker {
	final static Logger logger = LogManager.getLogger(WiseSearchWorker.class);
	
	public static final String SPACE = " ";
	public static final int AND_OPERATOR = 1;
	
	public Properties prop;
	public Search search;
	public String searchIP;
	public int searchPort;
	public int searchTimeout;
	
	public String sort;
	public String ranking;
	public String highlight;
	public String queryAnalyzer;
	
	public String collectionId;
	public String searchFields;
	public String documentFields;
	
	public String dateField;
	
	private ArrayList<HashMap<String,String>> resultList; // 연관 기사 리스트
	private ArrayList<String> docidList; // 연관 기사 DOCID 리스트
	
	public boolean debug = true;
	
	public WiseSearchWorker() throws Exception{
		WNProperties wnprop = WNProperties.getInstance("/wisenut.properties");
		
		searchIP = wnprop.getProperty("search.ip");
		searchPort = Integer.parseInt(wnprop.getProperty("search.port"));
		searchTimeout = Integer.parseInt(wnprop.getProperty("search.timeout"));
		
		search = new Search();
		
		sort = wnprop.getProperty("search.sort");
		ranking = wnprop.getProperty("search.ranking");
		highlight = wnprop.getProperty("search.highlight");
		queryAnalyzer = wnprop.getProperty("search.queryanalyzer");
		
		collectionId = wnprop.getProperty("search.collection.id");
		searchFields = wnprop.getProperty("search.collection.searchfields");
		documentFields = wnprop.getProperty("search.collection.documentfields");
		
		dateField = wnprop.getProperty("search.collection.datefield");
	}
	
	public void setSearchCondition(String query, int listNo, String startDate, String endDate, boolean docidSearch){
		search = new Search();
		
		int pageNum = 0;
		
		int ret = 0;
		
		ret = search.w3SetCodePage("UTF-8");
		ret = search.w3SetQueryLog(1);
		
		if(docidSearch){
			ret = search.w3SetCommonQuery("", 0);
		}else{
			ret = search.w3SetCommonQuery(query, 0);
		}
		
		String[] collectionArr = collectionId.split(",");
		for(String col : collectionArr){
			logger.debug(" - collection : " + col);			
			ret = search.w3AddCollection(col);
			
			String[] rankingArr = ranking.split(",");
			logger.debug(" - ranking : "+rankingArr[0]+", "+rankingArr[1]+", " + rankingArr[2]);
			ret = search.w3SetRanking(col, rankingArr[0], rankingArr[1], Integer.parseInt(rankingArr[2]));
			
			String[] hlArr = highlight.split(",");
			logger.debug(" - highlight : 1,1");
			ret = search.w3SetHighlight(col, Integer.parseInt(hlArr[0]), Integer.parseInt(hlArr[1]));
			
			logger.debug(" - sort : " + sort);
			ret = search.w3SetSortField(col, sort);
			
			String[] qaArr = queryAnalyzer.split(",");
			logger.debug(" - query analyzer : 1,1,1,1");
			ret = search.w3SetQueryAnalyzer(col, Integer.parseInt(qaArr[0]), Integer.parseInt(qaArr[1]), Integer.parseInt(qaArr[2]), Integer.parseInt(qaArr[3]));
			
			StringBuffer prefixQuery = new StringBuffer();
			if(docidSearch){
				prefixQuery.append("<DOCID:contains:"+query+">");
			}
			
			if(docidSearch){				
				logger.debug(" - date range : ");
				ret = search.w3SetDateRange(col, "1970/01/01", "2030/12/31");
			}
			
			logger.debug(" - prefixQuery : " + prefixQuery.toString());
			if(prefixQuery.length()>0){
				ret = search.w3SetPrefixQuery(col, prefixQuery.toString(), AND_OPERATOR);
			}
			
			logger.debug(" - search fields : " + searchFields);
			ret = search.w3SetSearchField(col, searchFields);
			
			logger.debug(" - document fields : " + documentFields);
			ret = search.w3SetDocumentField(col, documentFields);
			
			logger.debug(" - page info : " + pageNum + ", " + listNo);
			ret = search.w3SetPageInfo(col, pageNum, listNo);
			
			logger.debug(" - startDate : " + startDate);
			StringBuffer filterQuery = new StringBuffer();
			if(!"".equals(startDate)){
				filterQuery.append("<"+dateField+":gte:").append(startDate).append(">");
			}
			
			if(filterQuery.length()>0){
				filterQuery.append(SPACE);
			}
			
			logger.debug(" - endDate : " + endDate);
			if(!"".equals(endDate)){
				filterQuery.append("<"+dateField+":lte:").append(endDate).append(">");
			}
			
			logger.debug(" - filterQuery : " + filterQuery.toString());
			if(filterQuery.length()>0){
				ret = search.w3SetFilterQuery(col, filterQuery.toString());
			}
			
		}
		
		logger.debug(" - search ip : " + searchIP);
		logger.debug(" - search port : " + searchPort);
		logger.debug(" - search timeout : " + searchTimeout);
		ret = search.w3ConnectServer(searchIP, searchPort, searchTimeout);
		
		ret = search.w3ReceiveSearchQueryResult(0);
		if(ret != 0) {
            logger.error(search.w3GetErrorInfo() + " (Error Code : " + search.w3GetError() + " )");
        }
	}
		
	public void search(String query, int listNo, String startDate, String endDate){
		resultList = new ArrayList<HashMap<String,String>>();
		docidList = new ArrayList<String>();
		
		// property에 지정한 모든 필드 검색
		setSearchCondition(query, listNo, startDate, endDate, false);
		
		int totalResultCount = 0;
		String[] collectionArr = collectionId.split(",");
		for(String col : collectionArr){
			totalResultCount += search.w3GetResultTotalCount(col);
		}
		
		logger.debug("############################################# ");
		logger.debug("### Query : " + query);
		logger.debug("### Total Result Count : " + totalResultCount);
		logger.debug("############################################# ");
		
		int count = search.w3GetResultCount(collectionId);
		
		String[] documentFieldsArr = documentFields.split(",");
		for(int i=0; i<count; i++){
			HashMap<String,String> articleResultMap = new HashMap<String, String>();
			
			for(String dfield : documentFieldsArr){
				// DOCID 결과값은 별도로 따로 저장.
				if(dfield.equals("DOCID")){
					docidList.add(search.w3GetField(collectionId, dfield, i));
				}
				articleResultMap.put(dfield, search.w3GetField(collectionId, dfield, i));
			}
						
			resultList.add(articleResultMap);
		}
		
		search.w3CloseServer();
	}
	
	public void docidSearch(String query, int listNo, String startDate, String endDate){
		resultList = new ArrayList<HashMap<String,String>>();
		docidList = new ArrayList<String>();
		
		// docid만 검색.
		setSearchCondition(query, listNo, startDate, endDate, true);
		
		int totalResultCount = 0;
		String[] collectionArr = collectionId.split(",");
		for(String col : collectionArr){
			totalResultCount += search.w3GetResultTotalCount(col);
		}
		
		logger.debug("############################################# ");
		logger.debug("### Query : " + query);
		logger.debug("### Total Result Count : " + totalResultCount);
		logger.debug("############################################# ");
		
		int count = search.w3GetResultCount(collectionId);

		String[] documentFieldsArr = documentFields.split(",");
		for(int i=0; i<count; i++){
			HashMap<String,String> articleResultMap = new HashMap<String, String>();
			
			for(String dfield : documentFieldsArr){
				// DOCID 결과값은 별도로 따로 저장.
				if(dfield.equals("DOCID")){
					docidList.add(search.w3GetField(collectionId, dfield, i));
				}
				articleResultMap.put(dfield, search.w3GetField(collectionId, dfield, i));
			}
						
			resultList.add(articleResultMap);
		}
		
		search.w3CloseServer();
	}
	
	public String getDOCIDListAsJson(){
		return StringUtil.objectToString(docidList);
	}
	
	public ArrayList<String> getDOCIDList(){
		return docidList;
	}
	
	
	public String getResultListAsJson(){
		return StringUtil.objectToString(resultList);
	}
	
	public ArrayList<HashMap<String,String>> getResultList(){
		return resultList;
	}
}
