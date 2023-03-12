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
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SiteDBRepository siteDBRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final IndexingResponse indexingResponse = new IndexingResponse();
    private final SitesList sites;

    @Override
    public IndexingResponse startIndexing() {
        if (SiteService.getCountInstances() > 0) {
            indexingResponse.setResult(true);
            indexingResponse.setError("Индексация уже запущена");
            return indexingResponse;
        }
        PageService.running = true;
        for (Site site : sites.getSites()) {
            SiteService.incrementCountInstances();
            SiteDB siteDB = siteDBRepository.findByUrl(site.getUrl());
            if (siteDB != null) {
                siteDBRepository.delete(siteDB);
            }
            SiteService siteService = new SiteService(siteDBRepository, pageRepository,
                    lemmaRepository, indexRepository);
            siteService.createSiteDB(site.getName(), site.getUrl());
            siteService.start();
        }
        indexingResponse.setResult(true);
        indexingResponse.setError(null);
        return indexingResponse;
    }

    @Override
    public IndexingResponse stopIndexing() {

        if (SiteService.getCountInstances() > 0) {
            indexingResponse.setResult(true);
            indexingResponse.setError(null);
            PageService.running = false;
        } else {
            indexingResponse.setResult(true);
            indexingResponse.setError("Индексация не запущена");
        }
        return indexingResponse;
    }

    @Override
    public IndexingResponse indexPage(String path) {
        SiteService.incrementCountInstances();
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
            SiteService siteService = new SiteService(siteDBRepository, pageRepository,
                    lemmaRepository, indexRepository);
            siteDB = siteService.createSiteDB(siteName, url);
        } else {
            siteDB.setStatus(IndexingStatus.INDEXING);
            siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
            siteDBRepository.saveAndFlush(siteDB);
        }

        List<Page> pageList = pageRepository.getPagesByPathAndSiteId(path.replaceFirst(siteDB.getUrl(), "/"), siteDB.getId());
        PageService pageService = new PageService(siteDBRepository, pageRepository,
                lemmaRepository, indexRepository, path, siteDB);
        if (!pageList.isEmpty()) {
            Page page = pageList.get(0);
            List<Integer> lemmaIds = indexRepository.getLemmaIdListByPageId(page.getId());
            for (int lemmaId : lemmaIds) {
                Optional<Lemma> lemmaOptional = lemmaRepository.findById(lemmaId);
                if (lemmaOptional.isPresent()) {
                    Lemma lemma = lemmaOptional.get();
                    int frequency = lemma.getFrequency() - 1;
                    if (frequency == 0) {
                        lemmaRepository.delete(lemma);
                    } else {
                        lemma.setFrequency(frequency);
                        lemmaRepository.saveAndFlush(lemma);
                    }
                }
            }
            pageRepository.delete(pageList.get(0));
        }

        Document document = pageService.getJsoupDocumentAndSavePage();

        try {
            lemmaService.lemmaAndIndexSave(lemmaService.getLemmasMap(document.toString()),
                    siteDB, pageService.getPage());
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
        siteDBRepository.saveAndFlush(siteDB);
        SiteService.decrementCountInstances();
        indexingResponse.setResult(true);
        indexingResponse.setError(null);
        return indexingResponse;
    }
}
