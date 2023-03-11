package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteDBRepository;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{
    private final SiteDBRepository siteDBRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final IndexingResponse indexingResponse = new IndexingResponse();
    private final SitesList sites;
    @Override
    public IndexingResponse startIndexing() {
        if (SiteParserService.getCountInstances() > 0) {
            indexingResponse.setResult(true);
            indexingResponse.setError("Индексация уже запущена");
            return indexingResponse;
        }
        PagesParserService.running = true;
        for (Site site : sites.getSites()) {
            SiteParserService.incrementCountInstances();
            SiteDB siteDB = siteDBRepository.findByUrl(site.getUrl());
            if (siteDB != null) {
                siteDBRepository.delete(siteDB);
            }
            SiteParserService siteParserService = new SiteParserService(siteDBRepository, pageRepository,
                    lemmaRepository, indexRepository);
            siteParserService.createSiteDB(site.getName(), site.getUrl());
            siteParserService.start();
        }
        indexingResponse.setResult(true);
        indexingResponse.setError(null);
        return indexingResponse;
    }

    @Override
    public IndexingResponse stopIndexing() {

        if (SiteParserService.getCountInstances() > 0) {
            indexingResponse.setResult(true);
            indexingResponse.setError(null);
            PagesParserService.running = false;
        } else {
            indexingResponse.setResult(true);
            indexingResponse.setError("Индексация не запущена");
        }
        return indexingResponse;
    }

    @Override
    public IndexingResponse indexPage(String path) {
        SiteParserService.incrementCountInstances();
        LemmaService lemmaService = new LemmaService(lemmaRepository, indexRepository);

        String url;
        Matcher matcher = Pattern.compile("(.+//[^/]+/).*").matcher(path);
        if (matcher.find()) {
            url = matcher.group(1);
        } else {
            indexingResponse.setResult(false);
            indexingResponse.setError("Адрес страницы указан неверно. Пример: https://example.com/news/");
            return indexingResponse;
        }

        boolean isSiteExist = false;
        String siteName = "";
        for (Site site : sites.getSites()) {
            if (site.getUrl().equals(url)) {
                isSiteExist = true;
                siteName = site.getName();
            }
        }

        if (!isSiteExist) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
            return indexingResponse;
        }

        SiteDB siteDB = siteDBRepository.findByUrl(url);
        if (siteDB == null) {
            SiteParserService siteParserService = new SiteParserService(siteDBRepository, pageRepository,
                    lemmaRepository, indexRepository);
            siteDB = siteParserService.createSiteDB(siteName, url);
        } else {
            siteDB.setStatus(IndexingStatus.INDEXING);
            siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
            siteDBRepository.save(siteDB);
        }

        Page page = pageRepository.getPageByPathAndSiteDBId(path.replaceFirst(siteDB.getUrl(), "/"),
                siteDB.getId());
        PagesParserService pagesParserService = new PagesParserService(siteDBRepository, pageRepository,
                lemmaRepository, indexRepository, path, siteDB);
        if (page != null) {
//            lemmaService.lemmaFrequencyDecrement(siteDB);
            pageRepository.delete(page);
        }

        Document document = pagesParserService.getJsoupDocumentAndSavePage();

        try {
            lemmaService.lemmaAndIndexSave(lemmaService.getLemmasMap(document.toString()),
                    siteDB, pagesParserService.getPage());
        } catch (IOException e) {
            e.printStackTrace();
            siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
            siteDB.setLastError(e.getMessage());
            siteDB.setStatus(IndexingStatus.FAILED);
            indexingResponse.setResult(false);
            indexingResponse.setError(e.getMessage());

        }

        siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
        siteDB.setStatus(IndexingStatus.INDEXED);
        siteDBRepository.save(siteDB);
        SiteParserService.decrementCountInstances();
        indexingResponse.setResult(true);
        indexingResponse.setError(null);
        return indexingResponse;
    }
}
