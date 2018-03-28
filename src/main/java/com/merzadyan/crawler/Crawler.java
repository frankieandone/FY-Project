package com.merzadyan.crawler;

import com.merzadyan.SOIRegistry;
import com.merzadyan.SentientAnalyser;
import com.merzadyan.Stock;
import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.parser.HtmlParseData;
import edu.uci.ics.crawler4j.url.WebURL;
import org.ahocorasick.trie.Trie;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;

public class Crawler extends WebCrawler {
    private static final Logger LOGGER = Logger.getLogger(Crawler.class.getName());
    
    private int linksVisited;
    
    private Trie trie;
    
    private HashMap<Stock, ArrayList<Integer>> soiScoreMap;
    
    /**
     * List of file extensions to filter out urls which are non-text, non-readable resources.
     */
    private static final Pattern FILTERS = constructPattern();
    
    Crawler() {
        linksVisited = 0;
        soiScoreMap = new HashMap<>();
        constructTrie();
    }
    
    private static Pattern constructPattern() {
        String[] EXCLUDED_URLS = {
                // Image.
                "mng", "pct", "bmp", "gif", "jpg", "jpeg", "png", "pst", "psp", "tif",
                "tiff", "ai", "drw", "dxf", "eps", "ps", "svg",
                
                // Video
                "3gp", "asf", "asx", "avi", "mov", "mp4", "mpg", "qt", "rm", "swf", "wmv",
                "m4a",
                
                // Audio.
                "mp3", "wma", "ogg", "wav", "ra", "aac", "mid", "au", "aiff",
                
                // Misc.
                "css", "js", "pdf", "doc", "exe", "bin", "rss", "zip", "rar"
        };
        StringBuilder urlsToFilter = new StringBuilder();
        for (int i = 0; i < EXCLUDED_URLS.length; i++) {
            urlsToFilter.append(EXCLUDED_URLS[i].toLowerCase());
            if (i != EXCLUDED_URLS.length - 1) {
                urlsToFilter.append("|");
            }
        }
        return compile("\".*(\\\\.(" + urlsToFilter.toString() + "))$");
    }
    
    private void constructTrie() {
        SOIRegistry soiRegistry = SOIRegistry.getInstance();
        if (soiRegistry == null) {
            LOGGER.debug("SOIRegistry is null.");
            return;
        }
        
        HashSet<Stock> stockSet = soiRegistry.getStockSet();
        ArrayList<String> companyKeys = new ArrayList<>();
        for (Stock stock : stockSet) {
            if (stock != null) {
                // NOTE: case does not matter when processed; case is ignored as stated in trie construction.
                if (stock.getCompany() != null && !stock.getCompany().isEmpty()) {
                    companyKeys.add(stock.getCompany());
                }
            }
        }
        
        // See https://github.com/robert-bor/aho-corasick
        trie = Trie.builder()
                // IMPORTANT: ignoreCase() must be called before adding keywords.
                .ignoreCase()
                .addKeywords(companyKeys)
                .build();
    }
    
    /**
     * This method receives two parameters. The first parameter is the page
     * in which we have discovered this new url and the second parameter is
     * the new url. You should implement this function to specify whether
     * the given url should be crawled or not (based on your crawling logic).
     * In this example, we are instructing the crawler to ignore urls that
     * have css, js, git, ... extensions and to only accept urls that start
     * with "http://www.ics.uci.edu/". In this case, we didn"t need the
     * referringPage parameter to make the decision.
     */
    @Override
    public boolean shouldVisit(Page referringPage, WebURL url) {
        // TODO: benchmark testing to see performance...
        
        String href = url.getURL().toLowerCase();
        // Determines whether href contains one of the file extensions (to not crawl).
        boolean matchesFilter = FILTERS.matcher(href).matches();
        
        // If the href contains one of the file extensions (to not crawl) then do not visit that page.
        if (matchesFilter) {
            return false;
        }
        
        // Only perform Aho-Corasick on non-seed URLs.
        if (linksVisited >= 1) {
            // Use Aho-Corasick to further filter which pages to ones containing info about select-companies.
            if (referringPage.getParseData() instanceof HtmlParseData) {
                HtmlParseData htmlParseData = (HtmlParseData) referringPage.getParseData();
                Document document = Jsoup.parseBodyFragment(htmlParseData.getHtml());
                
                // Using #firstMatch for optimisation purposes - as long as there is
                // at least one mention of a SOI then it is unnecessary to process further.
                return trie != null && trie.firstMatch(document.body().text()) != null;
            }
        }
        
        // Defaults to false since either links visited < 1 or referring page is not instance of HtmlParseData.
        return false;
    }
    
    /**
     * This function is called when a page is fetched and ready
     * to be processed by your program.
     */
    @Override
    public void visit(Page page) {
        String url = page.getWebURL().getURL();
        // ++linksVisited has a prefix operation - increment variable and get value.
        LOGGER.debug("links visited: " + ++linksVisited + " URL: " + url);
        
        if (page.getParseData() instanceof HtmlParseData) {
            HtmlParseData htmlParseData = (HtmlParseData) page.getParseData();
            String html = htmlParseData.getHtml();
            Document document = Jsoup.parseBodyFragment(html);
            Element body = document.body();
            String contentText = body.text();
            Set<WebURL> links = htmlParseData.getOutgoingUrls();
            LOGGER.debug("Number of outgoing links: " + links.size());
            LOGGER.debug("Text length: " + contentText.length());
            LOGGER.debug(contentText);
            
            // #identifyEntity returns a non-null result if the title contains a SOI.
            String company = SentientAnalyser.identifyEntity(document.title(), trie);
            // Exit page if the result is null.
            if (company == null) {
                return;
            }
            
            Stock stock = new Stock();
            stock.setCompany(company);
            
            int score = SentientAnalyser.findSentiment(contentText);
            LOGGER.debug("score: " + score);
            // Disregard -1 returns.
            if (score != -1 && soiScoreMap != null && soiScoreMap.containsKey(stock)) {
                // Initialise array list if null.
                soiScoreMap.computeIfAbsent(stock, k -> new ArrayList<>());
                soiScoreMap.get(stock).add(score);
            }
        }
    }
    
    /**
     * Called just before the termination of the current
     * crawler instance. It can be used for persisting in-memory data or other
     * finalization tasks.
     */
    @Override
    public void onBeforeExit() {
        
    }
    
    @Override
    protected void onPageBiggerThanMaxSize(String urlStr, long pageSize) {
        
    }
}