package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResponse;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService{
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    public SearchResponse search(String query, String url, int offset, int limit) {
        LemmaService lemmaService = new LemmaService(lemmaRepository,indexRepository);
        try {
            HashMap<String, Integer> queryLemmaMap = lemmaService.getLemmasMap(query);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println(query + " - " + url + " - " + offset +  " - " +  limit);


        return new SearchResponse();
    }
}
