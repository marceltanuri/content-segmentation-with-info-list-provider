package br.com.mtanuri.liferay.contentsegmentation.infolistproviders;

import java.util.List;
import java.util.Locale;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.info.list.provider.InfoListProvider;
import com.liferay.info.list.provider.InfoListProviderContext;
import com.liferay.info.pagination.Pagination;
import com.liferay.portal.kernel.model.Company;

/**
 * @author marceltanuri
 */

@Component(service = InfoListProvider.class)
public class PrincipalBannerInfoListProvider implements InfoListProvider<AssetEntry> {

	private static final String INFO_LIST_PROVIDER_NAME = "Principal Banner";

	@Reference
	private AssetEntryLocalService assetEntryLocalService;

	@Reference
	private JournalArticleService journalArticleService;

	@Override
	public List<AssetEntry> getInfoList(InfoListProviderContext infoListProviderContext) {

		List<AssetEntry> taggedArticles = journalArticleService.findTaggedArticles(infoListProviderContext);

		return !isNullOrEmpty(taggedArticles) ? taggedArticles
				: journalArticleService.findGlobalArticles(infoListProviderContext);
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

	private boolean isNullOrEmpty(List<AssetEntry> entries) {
		return entries == null || entries.isEmpty();
	}
}