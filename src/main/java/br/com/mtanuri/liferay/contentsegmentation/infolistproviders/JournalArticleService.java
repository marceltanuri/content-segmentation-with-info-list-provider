package br.com.mtanuri.liferay.contentsegmentation.infolistproviders;

import java.util.ArrayList;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.model.AssetTag;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.asset.kernel.service.AssetTagLocalService;
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
import com.liferay.portal.search.query.RangeTermQuery;
import com.liferay.portal.search.query.StringQuery;
import com.liferay.portal.search.searcher.SearchRequest;
import com.liferay.portal.search.searcher.SearchRequestBuilder;
import com.liferay.portal.search.searcher.SearchRequestBuilderFactory;
import com.liferay.portal.search.searcher.SearchResponse;
import com.liferay.portal.search.searcher.Searcher;
import com.liferay.portal.search.sort.SortOrder;
import com.liferay.portal.search.sort.Sorts;

@Component(service = JournalArticleService.class)
public class JournalArticleService {

	private static final String GLOBAL = "global";
	private static final String GROUP_FIELD = "groupId";
	private static final String SEGMENTADO = "segmentado";
	private static final String ASSET_CATEGORY_FIELD = "assetCategoryTitles_pt_BR";
	private static final long _48_HOURS_IN_MILLIS = 172800000L;
	private static final String MODIFIED_FIELD = "modified_sortable";
	private static final String JOURNAL_CLASS = "com.liferay.journal.model.JournalArticle";
	private static final String ASSET_TAG_NAMES = "assetTagNames";
	private static final String ENTRY_CLASS_NAME = "entryClassName";
	private static final String ENTRY_CLASS_PK = "entryClassPK";

	private Log log = LogFactoryUtil.getLog(JournalArticleService.class);

	@Reference
	AssetEntryLocalService assetEntryLocalService;

	@Reference
	AssetTagLocalService assetTagLocalService;

	@Reference
	protected Queries queries;

	@Reference
	protected Sorts sorts;

	@Reference
	protected Searcher searcher;

	@Reference
	protected SearchRequestBuilderFactory searchRequestBuilderFactory;

	public List<AssetEntry> findGlobalArticles(long groupId) {

		try {

			MatchQuery groupIdQuery = queries.match(GROUP_FIELD, String.valueOf(groupId));

			MatchQuery entryClassNameQuery = queries.match(ENTRY_CLASS_NAME, JOURNAL_CLASS);
			MatchQuery assetCategoryQuery = queries.match(ASSET_CATEGORY_FIELD, GLOBAL);

			long currentTimeMillis = System.currentTimeMillis();
			long twoDaysAgoInMillis = currentTimeMillis - (_48_HOURS_IN_MILLIS);

			RangeTermQuery modifiedDateRangeQuery = queries.rangeTerm(MODIFIED_FIELD, true, true,
					String.valueOf(twoDaysAgoInMillis), String.valueOf(currentTimeMillis));

			BooleanQuery booleanQuery = queries.booleanQuery();
			booleanQuery.addMustQueryClauses(groupIdQuery, entryClassNameQuery, modifiedDateRangeQuery,
					assetCategoryQuery);

			SearchRequestBuilder searchRequestBuilder = searchRequestBuilderFactory.builder();

			searchRequestBuilder.emptySearchEnabled(true);
			searchRequestBuilder.entryClassNames(JOURNAL_CLASS);
			searchRequestBuilder.fields(ENTRY_CLASS_PK);
			searchRequestBuilder.sorts(sorts.field(MODIFIED_FIELD, SortOrder.DESC));

			searchRequestBuilder.withSearchContext(searchContext -> {
				searchContext.setCompanyId(PortalUtil.getDefaultCompanyId());
			});

			SearchRequest searchRequest = searchRequestBuilder.query(booleanQuery).build();

			SearchResponse searchResponse = searcher.search(searchRequest);
			SearchHits searchHits = searchResponse.getSearchHits();
			List<SearchHit> searchHitsList = searchHits.getSearchHits();

			List<AssetEntry> articles = new ArrayList<AssetEntry>(10);

			for (SearchHit searchHit : searchHitsList) {
				Document doc = searchHit.getDocument();
				log.debug("classPK: " + doc.getLong(ENTRY_CLASS_PK));
				AssetEntry article = assetEntryLocalService.fetchEntry(JOURNAL_CLASS, doc.getLong(ENTRY_CLASS_PK));
				articles.add(article);
				return articles;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public List<AssetEntry> findTaggedArticles(long groupId, User user) {
		if (user != null) {
			List<AssetTag> userTags = assetTagLocalService.getTags(User.class.getName(), user.getUserId());
			if (userTags == null || userTags.size() == 0) {
				return null;
			}

			try {

				String[] tagNames = new String[userTags.size()];
				for (int i = 0; i < userTags.size(); i++) {
					AssetTag assetTag = userTags.get(i);
					tagNames[i] = assetTag.getName();
				}

				StringQuery assetTagNamesQuery = queries.string(buildClauseOR(ASSET_TAG_NAMES, tagNames));
				MatchQuery groupIdQuery = queries.match(GROUP_FIELD, String.valueOf(groupId));
				MatchQuery entryClassNameQuery = queries.match(ENTRY_CLASS_NAME, JOURNAL_CLASS);
				MatchQuery assetCategoryQuery = queries.match(ASSET_CATEGORY_FIELD, SEGMENTADO);

				long currentTimeMillis = System.currentTimeMillis();
				long twoDaysAgoInMillis = currentTimeMillis - (_48_HOURS_IN_MILLIS);

				RangeTermQuery modifiedDateRangeQuery = queries.rangeTerm(MODIFIED_FIELD, true, true,
						String.valueOf(twoDaysAgoInMillis), String.valueOf(currentTimeMillis));

				BooleanQuery booleanQuery = queries.booleanQuery();
				booleanQuery.addMustQueryClauses(groupIdQuery, entryClassNameQuery, modifiedDateRangeQuery,
						assetCategoryQuery, assetTagNamesQuery);

				SearchRequestBuilder searchRequestBuilder = searchRequestBuilderFactory.builder();

				searchRequestBuilder.emptySearchEnabled(true);
				searchRequestBuilder.entryClassNames(JOURNAL_CLASS);
				searchRequestBuilder.fields(ENTRY_CLASS_PK, ASSET_TAG_NAMES);
				searchRequestBuilder.sorts(sorts.field(MODIFIED_FIELD, SortOrder.DESC));

				searchRequestBuilder.withSearchContext(searchContext -> {
					searchContext.setCompanyId(PortalUtil.getDefaultCompanyId());
					searchContext.setAndSearch(true);
				});

				SearchRequest searchRequest = searchRequestBuilder.query(booleanQuery).build();

				SearchResponse searchResponse = searcher.search(searchRequest);
				SearchHits searchHits = searchResponse.getSearchHits();
				List<SearchHit> searchHitsList = searchHits.getSearchHits();

				List<AssetEntry> articles = new ArrayList<AssetEntry>(10);

				for (SearchHit searchHit : searchHitsList) {

					Document doc = searchHit.getDocument();
					log.debug("classPK: " + doc.getLong(ENTRY_CLASS_PK));

					// verify if tags has the exact same name, elastic search is
					// breaking
					// whitespaces and losing precision :(
					for (String string : tagNames) {
						if (doc.getValues(ASSET_TAG_NAMES).contains(string)) {
							AssetEntry article = assetEntryLocalService.fetchEntry(JOURNAL_CLASS,
									doc.getLong("entryClassPK"));
							articles.add(article);
							return articles;
						}
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private String buildClauseOR(String field, String[] values) {
		StringBuilder sb = new StringBuilder();
		sb.append(field + ":(");
		for (int i = 0; i < values.length; i++) {
			sb.append("\"").append(values[i]).append("\"");
			if (i < values.length - 1) {
				sb.append(" OR ");
			}
		}
		return sb.append(")").toString();
	}
}
