package com.example.elk;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.http.HttpHost;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

public class ElasticsearchRESTClient {

	public static final String TRACKING_ID = "build_002";
	public static final String INDEX_AUDIT = "audit";

	public RestHighLevelClient getRestClient() {
		RestHighLevelClient client = new RestHighLevelClient(
				RestClient.builder(new HttpHost(ELKConstant.SERVER_HOST, 9200, "http")));
		return client;
	}

	public static Consumer<SearchHit> hitConsumer = (hit) -> {
		System.out.println(hit.getSourceAsString());
	};

	public static void main(String[] args) throws Exception {
		// tryTermsQuery();
		// tryAggregation();
		// tryBoolquery();
		tryMultiSearch();

	}

	public static void tryTermsQuery() throws Exception {
		try (RestHighLevelClient client = new ElasticsearchRESTClient().getRestClient()) {

			SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
			sourceBuilder.query(QueryBuilders.termQuery("trackingID", TRACKING_ID));
			sourceBuilder.from(0);
			sourceBuilder.size(5);
			sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));

			SearchRequest searchRequest = new SearchRequest();
			searchRequest.indices("indy");
			searchRequest.source(sourceBuilder);

			SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
			SearchHits hits = searchResponse.getHits();

			hits.forEach(hitConsumer);

		} catch (Exception e) {

		}
	}

	public static void tryAggregation() throws Exception {
		try (RestHighLevelClient client = new ElasticsearchRESTClient().getRestClient()) {
			SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
			sourceBuilder.query(QueryBuilders.termQuery("extra.trackingId", TRACKING_ID));
			sourceBuilder.size(0);
			TermsAggregationBuilder aggregation = AggregationBuilders.terms("by_eventType").field("eventType");
			sourceBuilder.aggregation(aggregation);

			SearchRequest searchRequest = new SearchRequest();
			searchRequest.indices("audit");
			searchRequest.source(sourceBuilder);

			SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
			SearchHits hits = searchResponse.getHits();

			// Retrieving Aggregations
			Aggregations aggregations = searchResponse.getAggregations();
			Terms byTypeAggregation = aggregations.get("by_eventType");

			byTypeAggregation.getBuckets().forEach(bucket -> {
				System.out.println(bucket.getKeyAsString() + "|" + bucket.getDocCount());
			});

			hits.forEach(hitConsumer);
		}
	}

	public static void tryBoolquery() throws Exception {
		try (RestHighLevelClient client = new ElasticsearchRESTClient().getRestClient()) {
			SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
			BoolQueryBuilder queryBuilders = QueryBuilders.boolQuery()
					.must(QueryBuilders.matchQuery("extra.trackingId", TRACKING_ID))
					.must(QueryBuilders.matchQuery("eventType", "ACCESS"));

			// sorting
			// sourceBuilder.sort(new FieldSortBuilder("timestamp").order(SortOrder.DESC));

			sourceBuilder.query(queryBuilders);
			
			/* Set the size to 1 to get the most recent record, together with sorting enabled */
			//sourceBuilder.size(1);
			sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));

			SearchRequest searchRequest = new SearchRequest();
			searchRequest.indices("audit");
			searchRequest.source(sourceBuilder);

			SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
			SearchHits hits = searchResponse.getHits();
			
			hits.forEach(hitConsumer);
		}
	}
	
	public static void tryMultiSearch() throws Exception{
		try (RestHighLevelClient client = new ElasticsearchRESTClient().getRestClient()) {
			MultiSearchRequest request = new MultiSearchRequest();
			TermQueryBuilder termQueryBuilder = QueryBuilders.termQuery("extra.trackingId", TRACKING_ID);
			FieldSortBuilder fsb = new FieldSortBuilder("timestamp");
			
			SearchRequest firstSearchRequest = new SearchRequest();   
			SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
			searchSourceBuilder.query(termQueryBuilder);
			searchSourceBuilder.sort(fsb.order(SortOrder.DESC));
			searchSourceBuilder.size(1);
			firstSearchRequest.source(searchSourceBuilder);
			firstSearchRequest.indices(INDEX_AUDIT);
			request.add(firstSearchRequest);
			
			SearchRequest secondSearchRequest = new SearchRequest();  
			searchSourceBuilder = new SearchSourceBuilder();
			searchSourceBuilder.query(termQueryBuilder);
			searchSourceBuilder.sort(fsb.order(SortOrder.ASC));
			searchSourceBuilder.size(1);
			secondSearchRequest.source(searchSourceBuilder);
			secondSearchRequest.indices(INDEX_AUDIT);
			request.add(secondSearchRequest);
			
			MultiSearchResponse response = client.msearch(request, RequestOptions.DEFAULT);
			MultiSearchResponse.Item firstResponse = response.getResponses()[0];   
			SearchResponse searchResponse = firstResponse.getResponse();           
			searchResponse.getHits().forEach(hitConsumer);
			
			MultiSearchResponse.Item secondResponse = response.getResponses()[1];  
			searchResponse = secondResponse.getResponse();
			searchResponse.getHits().forEach(hitConsumer);
		}
	}

}
