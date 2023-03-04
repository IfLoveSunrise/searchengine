package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.IndexingStatus;
import searchengine.model.SiteDB;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteDBRepository;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{
    private final SiteDBRepository siteDBRepository;
    private final PageRepository pageRepository;
    private final IndexingResponse indexingResponse = new IndexingResponse();
    private final SitesList sites;
    @Override
    public IndexingResponse startIndexing() {
        PageParser.running = true;
        for (Site site : sites.getSites()) {
            SiteDB siteDB = siteDBRepository.findByUrl(site.getUrl());
            System.out.println(siteDB);
            if (siteDB == null) {
                siteDB = new SiteDB();
                siteDB.setName(site.getName());
                siteDB.setUrl(site.getUrl());
            } else if (siteDB.getStatus() == IndexingStatus.INDEXING){
                indexingResponse.setResult(false);
                indexingResponse.setError("Индексация уже запущена");
                break;
            } else {
                System.out.println(siteDB + "3");
//                siteDBRepository.delete(siteDB);
            }

            System.out.println(siteDB + "4");
            SiteParser siteParser = new SiteParser(siteDBRepository, pageRepository, siteDB);
            siteParser.start();
        }

        System.out.println("111111111111111111111111111111111111111111111111111111111111111");
        indexingResponse.setResult(true);
        indexingResponse.setError(null);
        return indexingResponse;
    }

    @Override
    public IndexingResponse stopIndexing() {
        PageParser.running = false;
        System.out.println("STOOOOOOOOOOOOOOOOOOOOOOOOOOOOP");
        IndexingResponse indexingResponse = new IndexingResponse();
        indexingResponse.setResult(true);
        return indexingResponse;
    }

}
