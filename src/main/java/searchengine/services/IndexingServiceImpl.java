package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingData;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.PageParserData;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sites;

    @Override
    public IndexingResponse startIndexing() {
        IndexingResponse indexingResponse = checkingIndexingRunning();
        if (!indexingResponse.isResult()) return indexingResponse;

        for (SiteConfig siteConfig : sites.getSitesConfigList()) {
            SiteService.incrementCountInstances();
            IndexingData indexingData = new IndexingData();
            indexingData.setIndexingResponse(indexingResponse);
            indexingData.setUrl(siteConfig.getUrl());

            SiteService siteService = new SiteService(siteRepository, pageRepository,
                    lemmaRepository, indexRepository);

            indexingData = siteService.checkingAvailabilitySiteInDB(indexingData);
            if (!indexingData.getIndexingResponse().isResult()) {
                PageService.running = false;
                LemmaService.running = false;
                return indexingData.getIndexingResponse();
            }
            if (indexingData.getSite() != null) {
                siteRepository.delete(indexingData.getSite());
            }
            siteService.createSiteDB(siteConfig.getName(), siteConfig.getUrl());
            siteService.start();
        }
        return indexingResponse;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse indexingResponse = new IndexingResponse();
        if (SiteService.getCountInstances() > 0) {
            PageService.running = false;
            LemmaService.running = false;
            indexingResponse.setResult(true);
            indexingResponse.setError(null);
        } else {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация не запущена");
        }
        return indexingResponse;
    }

    @Override
    public IndexingResponse indexPage(String path) {
        SiteService siteService = new SiteService(siteRepository, pageRepository,
                lemmaRepository, indexRepository);

        IndexingResponse indexingResponse = checkingIndexingRunning();
        if (!indexingResponse.isResult()) return indexingResponse;
        SiteService.incrementCountInstances();

        IndexingData indexingData = checkingCorrectnessPath(path);
        if (!indexingData.getIndexingResponse().isResult()) return indexingData.getIndexingResponse();

        indexingData = siteService.checkingAvailabilitySiteInDB(indexingData);
        if (!indexingData.getIndexingResponse().isResult()) return indexingData.getIndexingResponse();
        Site site = indexingData.getSite();

        indexingData.setPageList(pageRepository.getPagesByPathAndSiteId(path.replaceFirst(site.getUrl(), "/"), site.getId()));
        indexingData.setPageService(new PageService(siteRepository, pageRepository,
                lemmaRepository, indexRepository, path, site));
        indexingData.setLemmaService(new LemmaService(lemmaRepository, indexRepository));
        indexingData = parseLemmas(indexingData);
        if (!indexingData.getIndexingResponse().isResult()) return indexingData.getIndexingResponse();

        site.setStatusTime(new Timestamp(new Date().getTime()).toString());
        site.setStatus(IndexingStatus.INDEXED);
        siteRepository.saveAndFlush(site);
        SiteService.decrementCountInstances();
        return indexingResponse;
    }

    public IndexingResponse checkingIndexingRunning () {
        IndexingResponse indexingResponse = new IndexingResponse();
        if (SiteService.getCountInstances() > 0) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация еще не остановлена, попробуйте позже");
            return indexingResponse;
        }
        indexingResponse.setResult(true);
        PageService.running = true;
        LemmaService.running = true;
        return indexingResponse;
    }

    public IndexingData checkingCorrectnessPath(String path) {
        IndexingData indexingData = new IndexingData();
        indexingData.setIndexingResponse(new IndexingResponse(true, null));

        Matcher matcher = Pattern.compile("(.+//[^/]+/).*").matcher(path);
        if (matcher.find()) {
            indexingData.setUrl(matcher.group(1));
        } else {
            indexingData.getIndexingResponse().setResult(false);
            indexingData.getIndexingResponse().setError("Адрес страницы указан неверно. " +
                    "Пример: https://example.com/news/");
            return indexingData;
        }

        boolean isSiteExist = false;
        for (SiteConfig siteConfig : sites.getSitesConfigList()) {
            if (siteConfig.getUrl().equals(indexingData.getUrl())) {
                isSiteExist = true;
                indexingData.setSiteName(siteConfig.getName());
            }
        }

        if (!isSiteExist) {
            indexingData.getIndexingResponse().setResult(false);
            indexingData.getIndexingResponse().setError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
        }
        return indexingData;
    }

    public IndexingData parseLemmas(IndexingData indexingData) {
        if (!indexingData.getPageList().isEmpty()) {
            Page page = indexingData.getPageList().get(0);
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
            pageRepository.delete(page);
        }
        return saveLemmas(indexingData);
    }

    public IndexingData saveLemmas(IndexingData indexingData) {
        PageParserData pageParserData = indexingData.getPageService().getJsoupDocumentAndSavePage();
        try {
            HashMap<String, Integer> lemmasMap = indexingData.getLemmaService().
                    getLemmasMap(pageParserData.getDocument().toString());
            indexingData.getLemmaService().lemmaAndIndexSave(lemmasMap, indexingData.getSite(),
                    pageParserData.getPage());
        } catch (IOException e) {
            indexingData.getSite().setStatusTime(new Timestamp(new Date().getTime()).toString());
            indexingData.getSite().setLastError(e.getMessage());
            indexingData.getSite().setStatus(IndexingStatus.FAILED);
            indexingData.getIndexingResponse().setResult(false);
            indexingData.getIndexingResponse().setError(e.getMessage());
        }
        return indexingData;
    }
}
