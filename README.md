## Content segmentation with InfoListProvider

Complex content segmentation rules can be achieved by using InfoListProvider.

Basically, you just have to create your implementation of InfoListProvider, declaring the type of the contents in your list, and return the desired list of contents using your own strategy of query.

Here is an example of a segmentation with the follow rules:

-   General objective: Find the banner to be displayed in the principal portal page
    
-   Rules:
    
    -   1. Find the most recent JournalArticle where at least one of its tags matches with at least one of the tags of the logged user
        
    -   2. If there is no JournalArticle in the filter above, so find the most recent JournalArticle with the assetCategoryTitle = 'global'.
        
    -   3. In the two filters above, it is only be accepted JournalArticles that have been published in the last 48 hours.
        
    -   4. If any of the conditions above were satisfied, so any JournalArticle will be displayed.
        

Here is my example of code used to cover those rules.

I’ve used elasticSearch queries instead of conventional SQL in order to gain better performance.

Source:
[https://github.com/marceltanuri/content-segmentation-with-info-list-provider](https://github.com/marceltanuri/content-segmentation-with-info-list-provider) 

References:

-   [https://help.liferay.com/hc/en-us/articles/360029067271-Creating-an-Information-List-Provider](https://help.liferay.com/hc/en-us/articles/360029067271-Creating-an-Information-List-Provider)
    
-   [https://help.liferay.com/hc/en-us/articles/360029046391-Search-Queries-and-Filters](https://help.liferay.com/hc/en-us/articles/360029046391-Search-Queries-and-Filters)
    
-   [https://help.liferay.com/hc/en-us/articles/360029046411-Building-Search-Queries-and-Filters](https://help.liferay.com/hc/en-us/articles/360029046411-Building-Search-Queries-and-Filters)
    
-   [https://github.com/liferay/liferay-portal/tree/7.2.x/modules/apps/portal-search/portal-search-api/src/main/java/com/liferay/portal/search/query](https://github.com/liferay/liferay-portal/tree/7.2.x/modules/apps/portal-search/portal-search-api/src/main/java/com/liferay/portal/search/query)