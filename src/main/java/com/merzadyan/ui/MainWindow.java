package com.merzadyan.ui;

import com.merzadyan.Common;
import com.merzadyan.SOIRegistry;
import com.merzadyan.Stock;
import com.merzadyan.TextAreaAppender;
import com.merzadyan.crawler.CrawlerManager;
import com.merzadyan.crawler.CrawlerTerminationListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.apache.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class MainWindow extends Application {
    private static final Logger LOGGER = Logger.getLogger(MainWindow.class.getName());
    
    private CrawlerManager crawlerManager;
    private ResultsCallback resultsCallback;
    
    /**
     * Indicates the current state of the crawlers. True if crawling is currently being performed.
     * False if crawling has not yet been started or the #onTermination callback has not been called.
     */
    private boolean currentlyCrawling;
    
    /*
     * Main tab.
     */
    @FXML
    private TextArea consoleTextArea;
    
    @FXML
    private Label hhmmssLbl;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> timerHandler;
    
    /*
     * Config tab.
     */
    @FXML
    private TextField userAgentNameTextField,
            dataDumpTextField;
    @FXML
    private Slider numberOfCrawlersSlider,
            maxDepthOfCrawlingSlider,
            politenessDelaySlider,
            includeHTTPSPagesSlider,
            includeBinaryContentCrawlingSlider,
            resumableCrawlingSlider;
    
    /*
     * SOI Registry tab.
     */
    @FXML
    private TextField companyNameTextField,
            tickerSymbolTextField,
            stockExchangeTextField;
    
    @FXML
    private ListView soiRegistryListView;
    private ObservableList<Stock> soiObservableList;
    
    /*
     * Seed URLs tab.
     */
    @FXML
    private ComboBox testModeComboBox;
    
    @FXML
    private Slider testSlider;
    
    @FXML
    private TextField seedUrlTextField;
    
    @FXML
    private Button addSeedUrlBtn,
            removeSeedUrlBtn;
    
    @FXML
    private ListView seedUrlsListView;
    
    private class ResultsCallback implements CrawlerTerminationListener {
        @Override
        public void onTermination(HashMap<Stock, ArrayList<Integer>> soiScoreMap) {
            LOGGER.debug("#onTermination");
            
            stopTimer();
            
            currentlyCrawling = false;
            
            if (soiScoreMap == null) {
                return;
            }
            
            try {
                soiScoreMap.forEach((stock, scores) -> {
                    String out = ("Stock: " + stock.getCompany()) +
                            " Symbol: " + stock.getSymbol() +
                            " Stock Exchange: " + stock.getStockExchange() +
                            " Sentiment Score: " + stock.getLatestSentimentScore();
                    LOGGER.debug("result: " + out);
                });
            } catch (Exception e) {
                LOGGER.fatal(e);
            }
        }
    }
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        setUserAgentStylesheet(STYLESHEET_MODENA);
        
        Scene scene = new Scene(FXMLLoader.load(getClass().getResource("main.fxml")));
        primaryStage.setScene(scene);
        // Disable resizing ability of the window.
        primaryStage.setResizable(false);
        primaryStage.show();
    }
    
    /**
     * Called after @FXML annotated members have been injected.
     */
    @FXML
    public void initialize() {
        /*
         * Main tab.
         */
        TextAreaAppender.setTextArea(consoleTextArea);
        consoleTextArea.appendText("Started application.\n");
        
        resultsCallback = new ResultsCallback();
        crawlerManager = new CrawlerManager(resultsCallback);
        
        /*
         * Config tab.
         */
        userAgentNameTextField.setText(crawlerManager.getUserAgentString());
        dataDumpTextField.setText(crawlerManager.getCrawlStorageFolder());
        
        // Use on, off values instead of 0, 1 in sliders.
        StringConverter<Double> binaryLabelFormat = (new StringConverter<Double>() {
            @Override
            public String toString(Double v) {
                if (v == 0) {
                    return "off";
                }
                return "on";
            }
            
            @Override
            public Double fromString(String v) {
                if (v.equals("off")) {
                    return 0d;
                }
                return 1d;
            }
        });
        
        includeHTTPSPagesSlider.setLabelFormatter(binaryLabelFormat);
        includeBinaryContentCrawlingSlider.setLabelFormatter(binaryLabelFormat);
        resumableCrawlingSlider.setLabelFormatter(binaryLabelFormat);
        
        /*
         * SOI Registry tab.
         */
        // Retrieve the dictionary of stocks from SOIRegistry.
        SOIRegistry soiRegistry = SOIRegistry.getInstance();
        // Adapt the hash set to an array list.
        ArrayList<Stock> list = new ArrayList<>(soiRegistry.getStockSet());
        soiObservableList = FXCollections.observableList(list);
        // Customise list view cells.
        soiRegistryListView.setCellFactory(param -> new ListCell<Stock>() {
            @Override
            protected void updateItem(Stock item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty) {
                    setText(null);
                } else {
                    setText(item.getCompany() + " <<<" + item.getSymbol() +
                            ">>> [" + item.getStockExchange() + "]");
                }
            }
        });
        // Sort stocks alphabetically.
        soiObservableList.sort((o1, o2) -> o1.getCompany().compareToIgnoreCase(o2.getCompany()));
        soiRegistryListView.setItems(soiObservableList);
        
        /*
         * Seed URLs tab.
         */
        testModeComboBox.getItems().clear();
        // TODO: reconsider item text and corresponding test operation; also use constant strings.
        testModeComboBox.getItems().addAll(
                CrawlerManager.MODE.TEST_MODE_ONE,
                CrawlerManager.MODE.TEST_MODE_TWO,
                CrawlerManager.MODE.TEST_MODE_THREE
        );
        
        testSlider.setLabelFormatter(binaryLabelFormat);
        toggleMode();
        testSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                toggleMode();
            }
        });
    }
    
    public void startCrawlers() {
        LOGGER.debug("startCrawlers");
        
        if (currentlyCrawling) {
            return;
        }
        
        try {
            // TODO: TextArea cannot handle vast amount of input text from log4j and becomes unresponsive.
            // See https://stackoverflow.com/questions/33078241/javafx-application-freeze-when-i-append-log4j-to-textarea
            // TODO: different approach - instead of reducing output (muting crawler4j/Stanford logs) OR increasing
            // buffer size of GUI console: keep one non-static instance of the GUI console and retrieve my logs i.e.
            // relevant tracing info and analysis results?
            if (crawlerManager == null) {
                LOGGER.fatal("CrawlerManager: null");
                return;
            }
            
            if (testSlider.getValue() == 1d) {
                String operation = (String) testModeComboBox.getValue();
                LOGGER.debug("Test Mode: " + operation);
                crawlerManager.setTest(true);
                crawlerManager.setTestMode((String) testModeComboBox.getValue());
            } else {
                crawlerManager.setTest(false);
                crawlerManager.setTestMode(null);
            }
            startTimer();
            crawlerManager.startNonBlockingCrawl();
        } catch (Exception ex) {
            LOGGER.debug(ex);
        }
    }
    
    public void stopCrawlers() {
        LOGGER.debug("stopCrawlers");
        
        if (!currentlyCrawling) {
            return;
        }
        
        if (crawlerManager != null) {
            crawlerManager.stopCrawl();
        }
    }
    
    public void saveConfigs() {
        // Guard against null strings.
        String userAgentName = userAgentNameTextField.getText().trim();
        String dataDump = userAgentNameTextField.getText().trim();
        if (!Common.isNullOrEmptyString(userAgentName)) {
            crawlerManager.setUserAgentString(userAgentNameTextField.getText().trim());
        }
        if (!Common.isNullOrEmptyString(dataDump)) {
            crawlerManager.setCrawlStorageFolder(dataDumpTextField.getText().trim());
        }
        
        crawlerManager.setNumberOfCrawlers((int) numberOfCrawlersSlider.getValue());
        crawlerManager.setMaxDepthOfCrawling((int) maxDepthOfCrawlingSlider.getValue());
        crawlerManager.setPolitenessDelay((int) politenessDelaySlider.getValue());
        crawlerManager.setIncludeHttpsPages(includeHTTPSPagesSlider.getValue() == 1d);
        crawlerManager.setIncludeBinaryContentInCrawling(includeBinaryContentCrawlingSlider.getValue() == 1d);
        crawlerManager.setResumableCrawling(resumableCrawlingSlider.getValue() == 1d);
        
        // Provide visual feedback for the saving-configs action.
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information Dialog");
        alert.setHeaderText(null);
        alert.setContentText("Saved configs!");
        alert.showAndWait();
    }
    
    /**
     * Resets the UI controls in the configs tab.
     */
    public void resetConfigs() {
        userAgentNameTextField.setText(CrawlerManager.DEFAULT.DEFAULT_USER_AGENT_STRING);
        dataDumpTextField.setText(CrawlerManager.DEFAULT.DEFAULT_CRAWL_STORAGE_FOLDER);
        numberOfCrawlersSlider.setValue(CrawlerManager.DEFAULT.DEFAULT_NUMBER_OF_CRAWLERS);
        maxDepthOfCrawlingSlider.setValue(CrawlerManager.DEFAULT.DEFAULT_MAX_DEPTH_OF_CRAWLING);
        politenessDelaySlider.setValue(CrawlerManager.DEFAULT.DEFAULT_POLITENESS_DELAY);
        includeHTTPSPagesSlider.setValue(adapt(CrawlerManager.DEFAULT.DEFAULT_INCLUDE_HTTPS_PAGES));
        includeBinaryContentCrawlingSlider.setValue(adapt(CrawlerManager.DEFAULT.DEFAULT_INCLUDE_BINARY_CONTENT_IN_CRAWLING));
        resumableCrawlingSlider.setValue(adapt(CrawlerManager.DEFAULT.DEFAULT_RESUMABLE_CRAWLING));
    }
    
    /**
     * Controls and toggles the states (enable/disable) of UI controls based on the current value
     * of the testSlider in Seed URLs tab.
     */
    private void toggleMode() {
        if (testSlider.getValue() == 1d) {
            testModeComboBox.setDisable(false);
            seedUrlTextField.setDisable(true);
            addSeedUrlBtn.setDisable(true);
            removeSeedUrlBtn.setDisable(true);
        } else {
            testModeComboBox.setDisable(true);
            seedUrlTextField.setDisable(false);
            addSeedUrlBtn.setDisable(false);
            removeSeedUrlBtn.setDisable(false);
        }
    }
    
    /**
     * Converts boolean value to corresponding double value.
     *
     * @param bool
     * @return
     */
    private double adapt(boolean bool) {
        return bool ? 1d : 0d;
    }
    
    /**
     * Starts internal timer used to calculate the elapsed time for the crawling process.
     */
    private void startTimer() {
        // IMPORTANT: #currentTimeMillis returns one hour before - add one hour to correct this.
        final long startTime = System.currentTimeMillis() + (60 * 60 * 1000);
        final SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
        
        final Runnable updateElapsedTimeRunnable = () -> {
            // Avoid throwing IllegalStateException by running from a non-JavaFX thread.
            Platform.runLater(
                    () -> {
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        hhmmssLbl.setText(formatter.format(new Date(elapsedTime)));
                    }
            );
        };
        // Schedule automatically cancelling/nullifying the timer if not cancelled already by then.
        int autoCancelBy = 4,
                // Schedule task to run every x milliseconds.
                scheduleEvery = 1000;
        timerHandler =
                scheduler.scheduleAtFixedRate(updateElapsedTimeRunnable, 0, scheduleEvery, MILLISECONDS);
        scheduler.schedule(() -> {
            timerHandler.cancel(true);
        }, autoCancelBy, HOURS);
    }
    
    /**
     * Stops internal timer used to calculate the elapsed time for the crawling process.
     */
    private void stopTimer() {
        if (timerHandler != null) {
            scheduler.schedule(() -> {
                timerHandler.cancel(true);
            }, 0, MILLISECONDS);
        }
    }
    
}
