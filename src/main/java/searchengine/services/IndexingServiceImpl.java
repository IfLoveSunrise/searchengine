package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
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
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{
    private final SiteDBRepository siteDBRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexingResponse indexingResponse = new IndexingResponse();
    private final SitesList sites;
    @Override
    public IndexingResponse startIndexing() {
        if (SiteParserService.getCountInstances() > 0) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация уже запущена");
            return indexingResponse;
        }
        PagesParserService.running = true;
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
            SiteParserService siteParserService = new SiteParserService(siteDBRepository, pageRepository, siteDB);
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
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация не запущена");
        }
        return indexingResponse;
    }

    @Override
    public IndexingResponse indexPage(String path) {
        String url;
        Matcher matcher = Pattern.compile("(.+//[^/]+/).+").matcher(path);
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
            siteDB = new SiteDB();
            siteDB.setName(siteName);
            siteDB.setUrl(url);
            siteDB.setStatus(IndexingStatus.INDEXING);
            siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
        }



        Connection connection = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) " +
                        "Gecko/20070725 Firefox/2.0.0.6")
                .referrer("http://www.google.com");
        Document document;
        try {
            document = connection.get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }



        Page page = pageRepository.findByPathAndSiteDBId(path, siteDB.getId());
        if (page != null) {
            pageRepository.delete(page);
        }
        page = new Page();
        page.setSite(siteDB);
        page.setPath(path.replaceFirst(siteDB.getUrl(), "/"));
        page.setCode(connection.response().statusCode());
        page.setContent(document.toString());
        siteDBRepository.save(siteDB);
        pageRepository.save(page);

        LemmaService lemmaService = new LemmaService();
        HashMap<String, Integer> lemmaMap;
        try {
            lemmaMap = lemmaService.getLemmasMap(document.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (String lemmaString : lemmaMap.keySet()) {
            Lemma lemma = new Lemma();
            lemma.setSite(siteDB);
            lemma.setLemma(lemmaString);
            lemma.setFrequency(1);
            lemmaRepository.save(lemma);

            Index index = new Index();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(lemmaMap.get(lemmaString));
            indexRepository.save(index);
        }

        indexingResponse.setResult(true);
        indexingResponse.setError(null);
        return indexingResponse;
    }
}
