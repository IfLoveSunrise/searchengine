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

import java.sql.Timestamp;
import java.util.Date;

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
            if (siteDB != null) {
                siteDBRepository.delete(siteDB);
            }
            siteDB = new SiteDB();
            siteDB.setName(site.getName());
            siteDB.setUrl(site.getUrl());
            siteDB.setStatus(IndexingStatus.INDEXING);
            siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
            siteDBRepository.save(siteDB);
            SiteParser siteParser = new SiteParser(siteDBRepository, pageRepository, siteDB);
            siteParser.start();
        }

        indexingResponse.setResult(true);
        indexingResponse.setError(null);
        return indexingResponse;
    }

    @Override
    public IndexingResponse stopIndexing() {

        IndexingResponse indexingResponse = new IndexingResponse();

        for (Site site : sites.getSites()) {
            if (!siteDBRepository.findByUrl(site.getUrl()).getStatus().equals(IndexingStatus.INDEXING)) {
                indexingResponse.setResult(false);
                indexingResponse.setError("Индексация не запущена");
            } else {
                indexingResponse.setResult(true);
                PageParser.running = false;
            }
        }
        return indexingResponse;
    }
}
