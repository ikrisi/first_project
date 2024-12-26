package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.BadResponse;
import searchengine.dto.PageRelevance;
import searchengine.dto.SearchData;
import searchengine.dto.SearchResponse;
import searchengine.model.*;
import searchengine.repositories.IndexesRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.LemmaService;
import searchengine.services.SearchService;

import java.io.IOException;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class SearchServiceImpl implements SearchService {
    private final LemmaService lemmaService;
    //private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexesRepository indexesRepository;
    private List<Page> findPages = new ArrayList<>();
    private List<SearchData> listData = new ArrayList<>();
    private int flagAllSitesForSearch;
    private List<Lemma> lemmasInSite = new ArrayList<>();
    private final SitesList sitesList;

    @Override
    public ResponseEntity search(String query, String site, int offset, int limit) {
        clearLists();
        flagAllSitesForSearch = 0;
        ResponseEntity<Object> responseEntity = checkInputSiteAndQuery(query, site);
        if (responseEntity != null) {
            return responseEntity;
        }
        List<WebSite> resultListSites = checkListSites(site);
        try {
            lemmasInSite = findLemmasInSites(query);

            if (lemmasInSite.isEmpty()) {
                return ResponseEntity.ok(new SearchResponse(true, 0, new ArrayList<>()));
            }
            List<Lemma> sortedLemmas = sortLemmas(lemmasInSite);
            findPagesWithLemmas(resultListSites, sortedLemmas);
            List<PageRelevance> pagesRelevance = setRelevanceAndGetSort(sortedLemmas);
            generateResultData(pagesRelevance, offset, limit, lemmasInSite);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok(new SearchResponse(true, findPages.size(), listData));
    }

    public ResponseEntity<Object> checkInputSiteAndQuery(String query, String site) {
        WebSite findWebsite = siteRepository.findByUrl(site);
        if (findWebsite == null) {
            setFlagAllSitesForSearch();
        }
        if (query.isBlank()) {
            log.error("Передан пустой поисковый запрос");
            return ResponseEntity.badRequest().body(new BadResponse(false, "Ошибка поиска. Передан пустой поисковый запрос"));
        }
        if (findWebsite != null && !findWebsite.getStatus().equals(StatusType.INDEXED)) {
            log.error("Сайт не проиндексирован");
            return ResponseEntity.badRequest().body(new BadResponse(false, "Ошибка поиска. Сайт не проиндексирован"));
        }
        if (findWebsite == null && siteRepository.findIndexed() == null) {
            log.error("Сайты не проиндексированы");
            return ResponseEntity.badRequest().body(new BadResponse(false, "Ошибка поиска. Сайты не проиндексированы"));
        }
        return null;
    }

    public List<String> findLemmas(String query) throws IOException {
        return new ArrayList<>(lemmaService.getLemmas(query).keySet());
    }

    public List<Lemma> findLemmasInSites(String query) throws IOException {
        List<Lemma> lemmasInSites = new ArrayList<>();
        for (String lemma : findLemmas(query)) {
            if (lemmaRepository.findLemma(lemma) != null) {
                for (WebSite webSite : siteRepository.findIndexed()) {
                    Lemma lemmaInSite = lemmaRepository.getLemmaInSite(lemma, webSite.getId());
                    if (lemmaInSite != null) {
                        lemmasInSites.add(lemmaInSite);
                    }
                }
            }
        }
        return lemmasInSites;
    }


    public List<WebSite> checkListSites(String siteUrl) {
        List<WebSite> checkedListSites = new ArrayList<>();
        if (flagAllSitesForSearch == 0) {
            WebSite findWebsite = siteRepository.findByUrl(siteUrl);
            checkedListSites.add(findWebsite);
        } else {
            for (Site site : sitesList.getSites()) {
                WebSite webSiteinDB = siteRepository.findByUrl(site.getUrl());
                if (webSiteinDB.getStatus().equals(StatusType.INDEXED)) {
                    checkedListSites.add(webSiteinDB);
                }
            }
        }
        return checkedListSites;
    }


    public List<Lemma> sortLemmas(List<Lemma> lemmas) {
        return lemmas.stream()
                .sorted(Comparator.comparing(Lemma::getFrequency))
                .toList();
    }

    public void setFlagAllSitesForSearch() {
        flagAllSitesForSearch++;
    }

    public void findPagesWithLemmas(List<WebSite> webSites, List<Lemma> lemmas) {
        List<Indexes> findIndexes;
        for (WebSite webSite : webSites) {
            for (Lemma lemma : lemmas) {
                findIndexes = indexesRepository.findBySiteId(lemma.getLemma(), webSite.getId());
                fillPageList(findIndexes);
            }
        }
    }

    public void fillPageList(List<Indexes> indexesList) {
        for (Indexes indexes : indexesList) {
            if(!findPages.contains(indexes.getPage())){
                findPages.add(indexes.getPage());
            }
        }
    }

    public void generateResultData(List<PageRelevance> pagesRelevance, int offset, int limit, List<Lemma> lemmas) throws IOException {
        List<String> lemmasString = new ArrayList<>();
        String text;
        for (Lemma lemma : lemmas) {
            lemmasString.add(lemma.getLemma());
        }
        List<SearchData> middleListData = new ArrayList<>();
        for (PageRelevance pageRelevance : pagesRelevance) {
            middleListData.add(createSearchData(pageRelevance));
        }
        int bound = Math.min(offset + limit, middleListData.size());
        for (int i = offset; i < bound; i++) {
            text = lemmaService.deleteTags(pagesRelevance.get(i).getPage().getContent());
            middleListData.get(i).setSnippet(createAndGetSnippet(text, lemmasString));
            listData.add(middleListData.get(i));
        }
    }

    public String createAndGetSnippet(String text, List<String> lemmas) throws IOException {
        LuceneMorphology luceneMorphRus = new RussianLuceneMorphology();
        LuceneMorphology luceneMorphEng = new EnglishLuceneMorphology();
        StringBuilder result = new StringBuilder();
        TreeMap<Integer, String> mapOfLemma = new TreeMap<>();
        String lemmaWord;
        List<String> resultLemmas = new ArrayList<>();

        StringBuilder word = new StringBuilder();
        for (char symbol : text.toCharArray()) {
            result.append(symbol);
            if (Character.isLetter(symbol)) {
                word.append(symbol);
            } else {
                lemmaWord = !word.isEmpty() ? word.toString().toLowerCase() : ".";
                try {
                    lemmaWord = luceneMorphRus.getNormalForms(lemmaWord).get(0);
                    if (lemmas.contains(lemmaWord)) {
                        findAndSelectionWords(result, word, mapOfLemma, resultLemmas, lemmaWord);
                    }
                    word = new StringBuilder();

                } catch (WrongCharaterException eRus) {
                    try {
                        lemmaWord = luceneMorphEng.getNormalForms(lemmaWord).get(0);
                        if (lemmas.contains(lemmaWord)) {
                            findAndSelectionWords(result, word, mapOfLemma, resultLemmas, lemmaWord);
                        }
                        word = new StringBuilder();

                    } catch (WrongCharaterException eEng) {
                        word = new StringBuilder();
                    }
                }
            }
        }
        return createSnippet(mapOfLemma, result);
    }

    public String createSnippet(TreeMap<Integer, String> mapOfLemma, StringBuilder result) {
        String resultString = "";
        int lengthPartOfSnippet = Math.max(160 / mapOfLemma.size(), 20);
        int currentIndex = -lengthPartOfSnippet;
        int lengthResult = result.length() - 1;
        for (Map.Entry<Integer, String> positionLemma: mapOfLemma.entrySet()) {
            int indexLemma = positionLemma.getKey();
            String lemmaWord = positionLemma.getValue();
            int lengthLemma = lemmaWord.length();
            if (currentIndex + lengthPartOfSnippet <= indexLemma + lengthLemma) {
                int indexBegin = Math.max(indexLemma - lengthPartOfSnippet, 0);
                int indexEnd = Math.min(indexLemma + lengthLemma + lengthPartOfSnippet, lengthResult);
                String partOfText = result.substring(indexBegin, indexEnd);
                if (indexBegin != 0) {
                    partOfText = getTextBeforeLemma(partOfText);
                }
                if (indexEnd != lengthResult) {
                    resultString = resultString + (". . .") + getTextAfterLemma(partOfText);
                }
                currentIndex = indexLemma + lengthLemma - 1;
            }
        }
        return resultString + (". . .");
    }
    public String getTextBeforeLemma(String text) {
        String split = text.split("\\s", 2)[0];
        return text.substring(split.length()).trim();
    }

    public String getTextAfterLemma(String text) {
        return text.substring(0, text.lastIndexOf(' '));
    }

    public void findAndSelectionWords(StringBuilder result, StringBuilder word, TreeMap<Integer, String> mapOfLemma,
                                      List<String> resultLemmas, String lemmaWord) {
        String stringBuilder = result.substring(0, result.length() - word.length() - 1);
        result.delete(0, result.length()).append(stringBuilder);
        word.insert(0, "<b>").append("</b> ");
        result.append(word);
        if (!resultLemmas.contains(lemmaWord)) {
            resultLemmas.add(lemmaWord);
            mapOfLemma.put(result.indexOf(word.toString()), word.toString());
        }
    }

    public SearchData createSearchData(PageRelevance pageRelevance) {
        WebSite webSite;
        webSite = siteRepository.findById(pageRelevance.getPage().getWebSiteId().getId()).get();
        String site = webSite.getUrl();
        String siteName = webSite.getName();
        String uri = pageRelevance.getPage().getPath();
        String title = Jsoup.parse(pageRelevance.getPage().getContent()).title();
        String snippet = "";
        float relevance = pageRelevance.getRelRelevance();
        return new SearchData(site, siteName, uri, title, snippet, relevance);
    }

    public List<PageRelevance> setRelevanceAndGetSort(List<Lemma> lemmas) {
        List<PageRelevance> pagesRelevance = new ArrayList<>();
        Float maxAbsRelevance = 0f;
        for (Page page : findPages) {
            PageRelevance pageRelevance = new PageRelevance(page);
            for (Lemma lemma : lemmas) {
                if (page.getWebSiteId().getId() == lemma.getWebSite().getId()) {
                    if (indexesRepository.findIndex(page.getId(), lemma.getId()) != null) {
                        pageRelevance.setAbsRelevance(pageRelevance.getAbsRelevance() +
                                indexesRepository.findIndex(page.getId(), lemma.getId()).getQuantity());
                    }
                }
            }
            if (maxAbsRelevance < pageRelevance.getAbsRelevance()) {
                maxAbsRelevance = pageRelevance.getAbsRelevance();
            }
            pagesRelevance.add(pageRelevance);
        }
        return setRelRelevanceAndSort(pagesRelevance, maxAbsRelevance);
    }

    public List<PageRelevance> setRelRelevanceAndSort(List<PageRelevance> pagesRelevance, Float maxAbsRelevance) {
        for (PageRelevance pageRelevance : pagesRelevance) {
            pageRelevance.setRelRelevance(pageRelevance.getAbsRelevance() / maxAbsRelevance);
        }
        return sortPagesRelevance(pagesRelevance);
    }

    public List<PageRelevance> sortPagesRelevance(List<PageRelevance> pagesRelevance) {
        return pagesRelevance.stream()
                .sorted(Comparator.comparingDouble(PageRelevance::getRelRelevance).reversed())
                .toList();
    }

    public void clearLists() {
        findPages.clear();
        listData.clear();
        lemmasInSite.clear();
    }

}