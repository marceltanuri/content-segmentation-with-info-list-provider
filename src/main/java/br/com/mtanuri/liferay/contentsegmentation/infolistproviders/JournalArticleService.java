package br.com.mtanuri.liferay.contentsegmentation.infolistproviders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.model.AssetTag;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.asset.kernel.service.AssetTagLocalService;
import com.liferay.journal.model.JournalArticle;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.search.document.Document;
import com.liferay.portal.search.hits.SearchHit;
import com.liferay.portal.search.hits.SearchHits;
import com.liferay.portal.search.query.BooleanQuery;
import com.liferay.portal.search.query.MatchQuery;
import com.liferay.portal.search.query.Queries;
import com.liferay.portal.search.query.Query;
import com.liferay.portal.search.query.RangeTermQuery;
import com.liferay.portal.search.query.StringQuery;
import com.liferay.portal.search.searcher.SearchRequest;
import com.liferay.portal.search.searcher.SearchRequestBuilder;
import com.liferay.portal.search.searcher.SearchRequestBuilderFactory;
import com.liferay.portal.search.searcher.SearchResponse;
import com.liferay.portal.search.searcher.Searcher;
import com.liferay.portal.search.sort.SortOrder;
import com.liferay.portal.search.sort.Sorts;

@Component(service = JournalArticleSearch.class)
public class JournalArticleSearch {

	private static final String GLOBAL = "global";
	private static final String GROUP_FIELD = "groupId";
	private static final String SEGMENTADO = "segmentado";
	private static final String ASSET_CATEGORY_FIELD = "assetCategoryTitles_pt_BR";
	private static final long _48_HOURS_IN_MILLIS = 172800000L;
	private static final String MODIFIED_FIELD = "modified_sortable";
	private static final String ASSET_TAG_NAMES = "assetTagNames";
	private static final String ENTRY_CLASS_NAME = "entryClassName";
	private static final String ENTRY_CLASS_PK = "entryClassPK";

	private Log log = LogFactoryUtil.getLog(JournalArticleSearch.class);

	@Reference
	private AssetEntryLocalService assetEntryLocalService;

	@Reference
	private AssetTagLocalService assetTagLocalService;

	@Reference
	protected Queries queries;

	@Reference
	protected Searcher searcher;

	@Reference
	protected SearchRequestBuilderFactory searchRequestBuilderFactory;
	
	@Reference
	protected Sorts sorts;

	public List<AssetEntry> findGlobalArticles(long groupId) {
		MatchQuery groupIdQuery = queries.match(GROUP_FIELD, String.valueOf(groupId));

		MatchQuery entryClassNameQuery = queries.match(ENTRY_CLASS_NAME, JournalArticle.class.getName());
		MatchQuery assetCategoryQuery = queries.match(ASSET_CATEGORY_FIELD, GLOBAL);
		RangeTermQuery modifiedDateRangeQuery = buildTwoDaysAgoRangeTermQuery();

		return getJournalArticles(Arrays.asList(ENTRY_CLASS_PK), null, groupIdQuery, entryClassNameQuery,
				modifiedDateRangeQuery, assetCategoryQuery);
	}

	public List<AssetEntry> findTaggedArticles(long groupId, User user) {
		List<AssetEntry> articles = Collections.emptyList();

		if (user != null) {
			List<AssetTag> userTags = assetTagLocalService.getTags(User.class.getName(), user.getUserId());
			if (!isNullOrEmpty(userTags)) {
				StringQuery assetTagNamesQuery = queries.string(buildAssetTagClauseOR(ASSET_TAG_NAMES, userTags));
				MatchQuery groupIdQuery = queries.match(GROUP_FIELD, String.valueOf(groupId));
				MatchQuery entryClassNameQuery = queries.match(ENTRY_CLASS_NAME, JournalArticle.class.getName());
				MatchQuery assetCategoryQuery = queries.match(ASSET_CATEGORY_FIELD, SEGMENTADO);

				RangeTermQuery modifiedDateRangeQuery = buildTwoDaysAgoRangeTermQuery();

				articles = getJournalArticles(Arrays.asList(ENTRY_CLASS_PK, ASSET_TAG_NAMES), userTags, groupIdQuery,
						entryClassNameQuery, modifiedDateRangeQuery, assetCategoryQuery, assetTagNamesQuery);
			}
		}
		return articles;
	}

	private String buildAssetTagClauseOR(String field, List<AssetTag> values) {
		StringBuilder sb = new StringBuilder();
		sb.append(field + ":(");
		for (int i = 0; i < values.size(); i++) {
			sb.append("\"").append(values.get(i)).append("\"");
			if (i < values.size() - 1) {
				sb.append(" OR ");
			}
		}
		return sb.append(")").toString();
	}

	private SearchRequestBuilder buildSearchRequestBuilder(boolean setAndSearch, List<String> fields) {
		SearchRequestBuilder searchRequestBuilder = searchRequestBuilderFactory.builder();

		searchRequestBuilder.emptySearchEnabled(true);
		searchRequestBuilder.entryClassNames(JournalArticle.class.getName());
		searchRequestBuilder.fields(fields.toArray(new String[fields.size()]));
		searchRequestBuilder.sorts(sorts.field(MODIFIED_FIELD, SortOrder.DESC));

		searchRequestBuilder.withSearchContext(searchContext -> {
			searchContext.setCompanyId(PortalUtil.getDefaultCompanyId());
			searchContext.setAndSearch(setAndSearch);
		});

		return searchRequestBuilder;
	}

	private RangeTermQuery buildTwoDaysAgoRangeTermQuery() {
		long currentTimeMillis = System.currentTimeMillis();
		long twoDaysAgoInMillis = currentTimeMillis - (_48_HOURS_IN_MILLIS);

		return queries.rangeTerm(MODIFIED_FIELD, true, true, String.valueOf(twoDaysAgoInMillis),
				String.valueOf(currentTimeMillis));
	}
	
	private boolean containsUserTags(List<AssetTag> userTags, List<Object> list) {
		for (AssetTag tag : userTags) {
			if (list.contains(tag.getName())) {
				return true;
			}
		}
		return false;
	}
	
	private List<AssetEntry> convertSearchHitIntoAssetEntry(List<SearchHit> searchHitsList, List<AssetTag> userTags) {
		List<AssetEntry> articles = new ArrayList<>(10);
		
		for (SearchHit searchHit : searchHitsList) {
			
			Document doc = searchHit.getDocument();
			log.debug("classPK: " + doc.getLong(ENTRY_CLASS_PK));
			
			// verify if tags has the exact same name, elastic search is
			// breaking
			// whitespaces and losing precision :(
			if (isNullOrEmpty(userTags)
					|| (!isNullOrEmpty(userTags) && containsUserTags(userTags, doc.getValues(ASSET_TAG_NAMES)))) {
				
				AssetEntry article = assetEntryLocalService.fetchEntry(JournalArticle.class.getName(),
						doc.getLong(ENTRY_CLASS_PK));
				articles.add(article);
			}
		}
		return articles;
	}
	
	private List<AssetEntry> getJournalArticles(List<String> fields, List<AssetTag> tags, Query ... queries){
		BooleanQuery booleanQuery = this.queries.booleanQuery();
		booleanQuery.addMustQueryClauses(queries);
		
		SearchRequestBuilder searchRequestBuilder = buildSearchRequestBuilder(true, fields);
		SearchRequest searchRequest = searchRequestBuilder.query(booleanQuery).build();
		
		List<SearchHit> searchHitsList = getSearcHits(searchRequest);
		
		return convertSearchHitIntoAssetEntry(searchHitsList, tags);
	}

	private List<SearchHit> getSearcHits(SearchRequest searchRequest) {
		SearchResponse searchResponse = searcher.search(searchRequest);
		SearchHits searchHits = searchResponse.getSearchHits();

		return searchHits.getSearchHits();
	}

	private boolean isNullOrEmpty(List<AssetTag> entries) {
		return entries == null || entries.isEmpty();
	}
}
