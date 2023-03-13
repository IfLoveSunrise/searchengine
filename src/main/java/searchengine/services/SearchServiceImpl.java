package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResponse;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService{

    public SearchResponse search(String query, String url, int offset, int limit) {

        return new SearchResponse();
    }
}
