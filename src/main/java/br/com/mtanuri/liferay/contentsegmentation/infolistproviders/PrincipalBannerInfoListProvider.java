package br.com.mtanuri.liferay.contentsegmentation.infolistproviders;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.model.AssetTag;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.asset.kernel.service.AssetTagLocalService;
import com.liferay.info.list.provider.InfoListProvider;
import com.liferay.info.list.provider.InfoListProviderContext;
import com.liferay.info.pagination.Pagination;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;


/**
 * @author marceltanuri
 */

@Component(service = InfoListProvider.class)
public class PrincipalBannerInfoListProvider implements InfoListProvider<AssetEntry> {

	private static final String INFO_LIST_PROVIDER_NAME = "Principal Banner";
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

	private Log log = LogFactoryUtil.getLog(PrincipalBannerInfoListProvider.class);

	@Override
	public List<AssetEntry> getInfoList(InfoListProviderContext infoListProviderContext) {

		List<AssetEntry> taggedArticles = findTaggedArticles(infoListProviderContext);

		if (taggedArticles != null && !taggedArticles.isEmpty()) {
			return taggedArticles;
		}

		List<AssetEntry> globalArticles = findGlobalArticles(infoListProviderContext);

		if (globalArticles != null && !globalArticles.isEmpty()) {
			return globalArticles;
		}

		return null;
	}

	private List<AssetEntry> findTaggedArticles(InfoListProviderContext infoListProviderContext) {
		User user = infoListProviderContext.getUser();

		Optional<Group> groupOptional = infoListProviderContext.getGroupOptional();
		long groupId = groupOptional.get().getGroupId();

		if (user != null) {
			List<AssetTag> userTags = assetTagLocalService.getTags(User.class.getName(), user.getUserId());
			if (userTags == null || userTags.size() == 0) {
				return null;
			}

			try {

				String[] tagNames = new String[userTags.size()];
				List<Object> tagNamesList = new ArrayList<Object>(userTags.size());
				for (int i = 0; i < userTags.size(); i++) {
					AssetTag assetTag = userTags.get(i);
					tagNames[i] = assetTag.getName();
					tagNamesList.add(assetTag.getName());
				}

				StringQuery assetTageNamesQuery = queries.string(buildClauseOR(ASSET_TAG_NAMES, tagNames));
				MatchQuery groupIdQuery = queries.match(GROUP_FIELD, String.valueOf(groupId));
				MatchQuery entryClassNameQuery = queries.match(ENTRY_CLASS_NAME, JOURNAL_CLASS);
				MatchQuery assetCategoryQuery = queries.match(ASSET_CATEGORY_FIELD, SEGMENTADO);

				long currentTimeMillis = System.currentTimeMillis();
				long twoDaysAgoInMillis = currentTimeMillis - (_48_HOURS_IN_MILLIS);

				RangeTermQuery modifiedDateRangeQuery = queries.rangeTerm(MODIFIED_FIELD, true, true,
						String.valueOf(twoDaysAgoInMillis), String.valueOf(currentTimeMillis));

				BooleanQuery booleanQuery = queries.booleanQuery();
				booleanQuery.addMustQueryClauses(groupIdQuery, entryClassNameQuery, modifiedDateRangeQuery,
						assetCategoryQuery, assetTageNamesQuery);

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

					// verify if tags has the exact same name, elastic search is breaking
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

	private List<AssetEntry> findGlobalArticles(InfoListProviderContext infoListProviderContext) {

		try {

			MatchQuery groupIdQuery = queries.match(GROUP_FIELD,
					String.valueOf(infoListProviderContext.getGroupOptional().get().getGroupId()));

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

	@Override
	public List<AssetEntry> getInfoList(InfoListProviderContext infoListProviderContext, Pagination pagination,
			com.liferay.info.sort.Sort sort) {
		return assetEntryLocalService.getTopViewedEntries(new String[0], !sort.isReverse(), pagination.getStart(),
				pagination.getEnd());
	}

	@Override
	public int getInfoListCount(InfoListProviderContext infoListProviderContext) {
		Company company = infoListProviderContext.getCompany();

		return assetEntryLocalService.getCompanyEntriesCount(company.getCompanyId());
	}

	@Override
	public String getLabel(Locale locale) {
		return INFO_LIST_PROVIDER_NAME;
	}
}