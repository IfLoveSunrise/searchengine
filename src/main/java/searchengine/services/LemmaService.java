package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LemmaService {
    public HashMap<String, Integer> getLemmasMap(String text) throws IOException {
        HashMap<String, Integer> lemmasMap = new HashMap<>();
        LuceneMorphology luceneMorph = new RussianLuceneMorphology();

        String[] words = text.replaceAll("[^А-яЁё]+", " ").toLowerCase(Locale.ROOT).split("\\s+");

        for (String word : words) {
            if (word.length() > 1 ) {
                List<String> wordBaseForms = luceneMorph.getMorphInfo(word);
                for (String wordBaseForm : wordBaseForms) {
                    if (!wordBaseForm.matches(".+[А-Я]$")) {
                        String lemma = word.replace("|.+$", "");
                        lemmasMap.put(lemma, lemmasMap.containsKey(lemma) ? lemmasMap.get(lemma) + 1 : 1);
                    }
                }
            }
        }
        return lemmasMap;
    }
}
