package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

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
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final IndexingResponse indexingResponse = new IndexingResponse();
    private final SitesList sites;

    @Override
    public IndexingResponse startIndexing() {
        if (SiteService.getCountInstances() > 0) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация еще не остановлена, попробуйте позже");
            return indexingResponse;
        }
        PageService.running = true;
        LemmaService.running = true;
        for (SiteConfig siteConfig : sites.getSitesConfigList()) {
            SiteService.incrementCountInstances();
            Site site = siteRepository.findByUrl(siteConfig.getUrl());
            if (site != null) {
                siteRepository.delete(site);
            }
            SiteService siteService = new SiteService(siteRepository, pageRepository,
                    lemmaRepository, indexRepository);
            siteService.createSiteDB(siteConfig.getName(), siteConfig.getUrl());
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
            LemmaService.running = false;
        } else {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация не запущена");
        }
        return indexingResponse;
    }

    @Override
    public IndexingResponse indexPage(String path) {
        if (SiteService.getCountInstances() > 0) {
            indexingResponse.setResult(true);
            indexingResponse.setError("Индексация еще не остановлена, попробуйте позже");
            return indexingResponse;
        }
        PageService.running = true;
        LemmaService.running = true;
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
        for (SiteConfig siteConfig : sites.getSitesConfigList()) {
            if (siteConfig.getUrl().equals(url)) {
                isSiteExist = true;
                siteName = siteConfig.getName();
            }
        }

        if (!isSiteExist) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
            return indexingResponse;
        }

        Site site = siteRepository.findByUrl(url);
        if (site == null) {
            SiteService siteService = new SiteService(siteRepository, pageRepository,
                    lemmaRepository, indexRepository);
            site = siteService.createSiteDB(siteName, url);
        } else {
            site.setStatus(IndexingStatus.INDEXING);
            site.setStatusTime(new Timestamp(new Date().getTime()).toString());
            siteRepository.saveAndFlush(site);
        }

        List<Page> pageList = pageRepository.getPagesByPathAndSiteId(path.replaceFirst(site.getUrl(), "/"), site.getId());
        PageService pageService = new PageService(siteRepository, pageRepository,
                lemmaRepository, indexRepository, path, site);
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
                    site, pageService.getPage());
        } catch (IOException e) {
            e.printStackTrace();
            site.setStatusTime(new Timestamp(new Date().getTime()).toString());
            site.setLastError(e.getMessage());
            site.setStatus(IndexingStatus.FAILED);
            indexingResponse.setResult(false);
            indexingResponse.setError(e.getMessage());

        }

        site.setStatusTime(new Timestamp(new Date().getTime()).toString());
        site.setStatus(IndexingStatus.INDEXED);
        siteRepository.saveAndFlush(site);
        SiteService.decrementCountInstances();
        indexingResponse.setResult(true);
        indexingResponse.setError(null);
        return indexingResponse;
    }
}
