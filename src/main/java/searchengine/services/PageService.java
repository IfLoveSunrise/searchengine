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
import searchengine.model.IndexingStatus;
import searchengine.model.Page;
import searchengine.model.SiteDB;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteDBRepository;

public class PageService extends RecursiveTask<SiteDB> {
    private final SiteDBRepository siteDBRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final String url;
    private final SiteDB siteDB;
    private Page page;
    public static boolean running = true;

    public PageService(SiteDBRepository siteDBRepository, PageRepository pageRepository,
                       LemmaRepository lemmaRepository, IndexRepository indexRepository,
                       String url, SiteDB siteDB) {
        this.siteDBRepository = siteDBRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.url = url;
        this.siteDB = siteDB;
    }

    @Override
    protected SiteDB compute() {
        System.out.println(url);

        List<PageService> pageServiceList = new CopyOnWriteArrayList<>();
        Set<String> linkSet = new CopyOnWriteArraySet<>();

        if (!running) stopIndexing();

        String path = url.replaceFirst(siteDB.getUrl(), "/");
        AtomicBoolean pageTestBoolean = new AtomicBoolean(false);
        for (Page pageTest : pageRepository.getPagesByPath(path)) {
            if (pageTest.getSite().getId() == siteDB.getId()) {
                pageTestBoolean.set(true);
            }
        }

        if (running && !pageTestBoolean.get()) {
            Document document = getJsoupDocumentAndSavePage();
            if (document != null) {
                for (Element element : document.select("a[href]")) {
                    String link = element.absUrl("href");
                    int pointCount = StringUtils.countMatches(link.replace(url, ""), ".");
                    if (!link.isEmpty() && link.startsWith(url) && !link.contains("#") && pointCount == 0
                            && running && !link.equals(url) && !linkSet.contains(link)) {
                        linkSet.add(link);
                        PageService pageService = new PageService(siteDBRepository, pageRepository,
                                lemmaRepository, indexRepository, link, siteDB);
                        pageService.fork();
                        pageServiceList.add(pageService);
                    }
                }
            }

            for (PageService link : pageServiceList) {
                link.join();
            }
        }
        return siteDB;
    }

    public Document getJsoupDocumentAndSavePage() {
        Document document;
        try {
            Thread.sleep(2000);
            Connection connection = Jsoup.connect(url).ignoreHttpErrors(true).ignoreContentType(true);
            document = connection.get();
            if (connection.response().statusCode() == 200) {
                page = new Page();
                page.setSite(siteDB);
                page.setPath(url.replaceFirst(siteDB.getUrl(), "/"));
                page.setCode(connection.response().statusCode());
                page.setContent(document.toString());
                siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
                pageRepository.saveAndFlush(page);
                LemmaService lemmaService = new LemmaService(lemmaRepository, indexRepository);
                HashMap<String, Integer> lemmaMap = lemmaService.getLemmasMap(document.toString());
                lemmaService.lemmaAndIndexSave(lemmaMap, siteDB, page);
            }

        } catch (Exception e) {
            document = null;
            e.printStackTrace();
        }

        return document;
    }

    public Page getPage() {
        return page;
    }

    public void stopIndexing() {
        siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
        siteDB.setLastError("Индексация остановлена пользователем");
        siteDB.setStatus(IndexingStatus.FAILED);
        siteDBRepository.saveAndFlush(siteDB);
    }
}