package searchengine.services;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import searchengine.dto.indexing.PageParserData;
import searchengine.model.IndexingStatus;
import searchengine.model.Page;
import searchengine.model.SiteDB;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

public class PageService extends RecursiveTask<SiteDB> {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final String url;
    private final SiteDB site;
    private Page page;
    public static boolean running = true;

    public PageService(SiteRepository siteRepository, PageRepository pageRepository,
                       LemmaRepository lemmaRepository, IndexRepository indexRepository,
                       String url, SiteDB siteDB) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.url = url;
        this.site = siteDB;
    }

    @Override
    protected SiteDB compute() {
        System.out.println(url);

        List<PageService> pageServiceList = new CopyOnWriteArrayList<>();
        Set<String> linkSet = new CopyOnWriteArraySet<>();

        if (!running) stopIndexing();

        String path = url.replaceFirst(site.getUrl(), "/");
        AtomicBoolean pageTestBoolean = new AtomicBoolean(false);
        for (Page pageTest : pageRepository.getPagesByPath(path)) {
            if (pageTest.getSite().getId() == site.getId()) {
                pageTestBoolean.set(true);
            }
        }

        if (running && !pageTestBoolean.get()) {
            Document document = getJsoupDocumentAndSavePage().getDocument();
            if (document != null) {
                for (Element element : document.select("a[href]")) {
                    if (!running) {
                        break;
                    }
                    String link = element.absUrl("href");
                    int pointCount = StringUtils.countMatches(link.replace(url, ""), ".");
                    if (!link.isEmpty() && link.startsWith(url) && !link.contains("#") && pointCount == 0
                            && running && !link.equals(url) && !linkSet.contains(link)) {
                        linkSet.add(link);
                        PageService pageService = new PageService(siteRepository, pageRepository,
                                lemmaRepository, indexRepository, link, site);
                        pageService.fork();
                        pageServiceList.add(pageService);
                    }
                }
            }

            for (PageService link : pageServiceList) {
                if (!running) {
                    break;
                }
                link.join();
            }
        }
        return site;
    }

    public PageParserData getJsoupDocumentAndSavePage() {
        PageParserData pageParserData = new PageParserData();
        Document document;
        try {
            Thread.sleep(2000);
            Connection connection = Jsoup.connect(url).ignoreHttpErrors(true).ignoreContentType(true);
            document = connection.get();
            if (connection.response().statusCode() == 200) {
                page = new Page();
                page.setSite(site);
                page.setPath(url.replaceFirst(site.getUrl(), "/"));
                page.setCode(connection.response().statusCode());
                page.setContent(document.toString());
                site.setStatusTime(new Timestamp(new Date().getTime()).toString());
                pageParserData.setPage(page);
                pageParserData.setDocument(document);
                pageRepository.saveAndFlush(page);
                LemmaService lemmaService = new LemmaService(lemmaRepository, indexRepository);
                HashMap<String, Integer> lemmaMap = lemmaService.getLemmasMap(document.toString());
                lemmaService.lemmaAndIndexSave(lemmaMap, site, page);
            }
        } catch (Exception e) {
            pageParserData.setError(e.getMessage());
        }
        return pageParserData;
    }

    public Page getPage() {
        return page;
    }

    public void stopIndexing() {
        site.setStatusTime(new Timestamp(new Date().getTime()).toString());
        site.setLastError("Индексация остановлена пользователем");
        site.setStatus(IndexingStatus.FAILED);
        siteRepository.saveAndFlush(site);
    }
}