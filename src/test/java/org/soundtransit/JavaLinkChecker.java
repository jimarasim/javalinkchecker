package org.soundtransit;

import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

public class JavaLinkChecker extends Thread {
    private final int numBrowsers = 7;
    private final String seed = "https://www.soundtransit.org";
    private final String seekHrefStartsWith = "/";
    private final String loadMoreButtonCssSelector = "button[class*='load-more']";

    private static int semaphore = 0;
    public static Hashtable<String,String> toVisit = new Hashtable<String,String>();
    public static Hashtable<String,String> visited = new Hashtable<String,String>();
    public static Hashtable<String,String> pageNotFound = new Hashtable<String,String>();
    public static Hashtable<String,String> imagesFound = new Hashtable<String,String>();
    public static Hashtable<String,String> imagesNotFound = new Hashtable<String,String>();

    @Before
    public void Before(){
        //DOWNLOAD CHROMEDRIVER FROM http://chromedriver.storage.googleapis.com/index.html AND PLACE IN PROJECT DIRECTORY
        System.setProperty("webdriver.chrome.driver", "./chromedriver"); // FOR MAC
    }

    /**
     * THIS TEST LAUNCHES MULTIPLE BROWSERS TO VISIT ALL LINKS ON A SEED PAGE CONCURRENTLY
     * @throws Exception
     */
    @Test
    public void MultiBrowserTest() throws Exception{
        //launch one browser to get unique urls from a seed page
        WebDriver driver = new ChromeDriver();
        driver.get(seed);
        visited.put(seed, "SEED");
        GetUniqueHrefs(driver, seed);
        System.out.println("INITIAL SIZE:" + toVisit.size());
        driver.quit();

        //launch n browsers to visit urls found and find more on each page, until there are no more unique pages left. report pages not found.
        for(int i=0; i<numBrowsers; i++){
            (new JavaLinkChecker()).start();
        }

        //wait until all browsers/threads are finished
        while(semaphore > 0){
            Thread.sleep(5000);
        }

        //check status code for all image links found, and report images not found
        CheckImagesNotFound();

        //report pages visited and pages not found
        PrintWriter writer = new PrintWriter("linkcrawler.htm", "UTF-8");
        writer.write("<html><head></head><body>");

        writer.write("VISITED "+visited.size()+" UNIQUE PAGES, "+pageNotFound.size()+" PAGES NOT FOUND <br />\r\n");
        writer.write("PROCESSED "+imagesFound.size()+" UNIQUE IMAGES, "+imagesNotFound.size()+" IMAGES NOT FOUND <br />\r\n");

        for(String key:visited.keySet()) {
            writer.write("VISITED: <a href='"+key+"' target='_blank'>"+key+"</a> REFERRER: <a href='" + visited.get(key) + "' target='_blank'>"+visited.get(key)+"</a><br />\r\n");
        }

        for(String key:imagesFound.keySet()) {
            writer.write("PROCESSED: <a href='"+key+"' target='_blank'>"+key+"</a> REFERRER: <a href='" + imagesFound.get(key) + "' target='_blank'>"+imagesFound.get(key)+"</a><br />\r\n");
        }

        for(String key:pageNotFound.keySet()) {
            System.out.println("PAGE NOT FOUND: "+key+" REFERRER: " + pageNotFound.get(key));
            writer.write("PAGE NOT FOUND: <a href='"+key+"' target='_blank'>"+key+"</a> REFERRER: <a href='" + pageNotFound.get(key) + "' target='_blank'>"+pageNotFound.get(key)+"</a><br />\r\n");
        }

        for(String key:imagesNotFound.keySet()) {
            System.out.println("IMAGE NOT FOUND: "+key+" REFERRER: " + imagesNotFound.get(key));
            writer.write("IMAGE NOT FOUND: <a href='"+key+"' target='_blank'>"+key+"</a> REFERRER: <a href='" + imagesNotFound.get(key) + "' target='_blank'>"+imagesNotFound.get(key)+"</a><br />\r\n");
        }

        writer.write("</body></html>");
        writer.flush();
        writer.close();

    }

    /**
     * This is the thread function that launches a browser, and visits unique pages until none are found.  While it's running, it records pages that were not found.
     */
    public void run(){
        semaphore++;

        WebDriver driver = new ChromeDriver();

        String[] href;

        do {
            href = GetHrefToVisit();
            if(href != null) {
                System.out.println("VISITING: " + href[0] + " FROM: " + href[1]);
                driver.get(href[0]);
                //check if we've been re-directed
                String endUrl = driver.getCurrentUrl().toLowerCase();
                if(!endUrl.equals(href[0])) {
                    System.out.println("REDIRECTED: " + href[0] + " TO: " + endUrl);
                    //if redirected page is already marked visited, don't process it again. this will avoid visiting expensive pages like calendar more than once
                    if(visited.containsKey(endUrl)) {
                        continue;
                    }
                    //if redirected page is marked tovisit, move it to visited
                    else if(toVisit.containsKey(endUrl)) {
                        String endUrlReferrer = toVisit.remove(endUrl);
                        visited.put(endUrl, endUrlReferrer);
                    } else {
                        //if redirected page is not marked to visit or visited, record its passing
                        visited.put(endUrl, href[0]);
                    }
                }
                //don't get unique hrefs and images if the page is not found. this will avoid countless search pages
                if(!IsPageNotFound(driver,href)) {
                    GetUniqueHrefs(driver, href[0]);
                    GetUniqueImages(driver, href[0]);
                }
            }
        } while(href != null);

        driver.quit();

        semaphore--;
    }

    /**
     * Gather a list of unique hrefs that are not documents, and have not yet been marked "tovisit" or "visited".
     * @param driver - webdriver being used
     * @param currentHref - current href navigated to.
     */
    public void GetUniqueHrefs(WebDriver driver, String currentHref) {
        CheckAndClickLoadMoreButton(driver);
        List<WebElement> hrefs = driver.findElements(By.cssSelector("a[href^='"+seekHrefStartsWith+"']"));
        for(WebElement we:hrefs) {
            try {
                String href = we.getAttribute("href").toLowerCase(); //hash considers different casing different keys, but we don't want to visit the same page twice, so convert to lower case
                if (
                        href.startsWith(seed) &&
                                !href.endsWith(".docx") &&
                                !href.endsWith(".ics") &&
                                !href.endsWith(".pptx") &&
                                !href.endsWith(".zip") &&
                                !toVisit.containsKey(href) &&
                                !visited.containsKey(href)
                        ) {
                    toVisit.put(href, currentHref);
                }
            } catch (StaleElementReferenceException serex) {
                System.out.println("STALE ELEMENT EXCEPTION");
                break;
            }
        }
    }

    /**
     * Gather a list of unique image src and source srcset urls for checking response codes later.
     * @param driver
     * @param currentHref
     */
    public void GetUniqueImages(WebDriver driver, String currentHref) {
        //get img src urls
        List<WebElement> images = driver.findElements(By.cssSelector("img[src]"));
        for(WebElement image:images) {
            String imageUrl = image.getAttribute("src");

            //append baseurl to src, if not already included
            if(imageUrl.startsWith("/")) {
                imageUrl = seed + imageUrl;
            }

            //add unique image src urls to imagesFound table
            if(!imagesFound.containsKey(imageUrl)) {
                imagesFound.put(imageUrl,currentHref);
            }
        }

        //get source srcset and image srcset urls
        List<WebElement> srcsets = driver.findElements(By.cssSelector("source[srcset],img[srcset]"));
        for(WebElement srcset:srcsets) {
            String imageUrl = srcset.getAttribute("srcset");

            //remove "1x" etc from srcset
            if(imageUrl.contains(" ")) {
                imageUrl = imageUrl.substring(0, imageUrl.indexOf(" "));
            }

            //append baseurl to src, if not already included
            if(imageUrl.startsWith("/")) {
                imageUrl = seed + imageUrl;
            }

            //add unique srcset urls to imagesFound table
            if(!imagesFound.containsKey(imageUrl)) {
                imagesFound.put(imageUrl,currentHref);
            }
        }
    }

    /**
     * Get one of the unique hrefs gathered to visit, and move it from "toVisit" to "visited".
     * @return
     */
    public String[] GetHrefToVisit() {
        String[] hrefToVisit = new String[2];

        System.out.println("TOVISIT: " + toVisit.size() + " VISITED: " + visited.size());

        //DEBUG: if(toVisit.isEmpty() || visited.size()>99)
        if(toVisit.isEmpty()) {
            return null;
        } else {
            Enumeration enumeration = toVisit.keys();
            hrefToVisit[0] = (String)enumeration.nextElement();
            hrefToVisit[1] = toVisit.remove(hrefToVisit[0]);
            visited.put(hrefToVisit[0],hrefToVisit[1]);

            return hrefToVisit;
        }
    }

    /**
     * If there's a load more button on the page, click it until it's gone, for a maximum of 100 clicks.
     * @param driver
     */
    public void CheckAndClickLoadMoreButton(WebDriver driver) {
        int max = 100;
        while(driver.findElements(By.cssSelector(loadMoreButtonCssSelector)).size() > 0 && --max > 0) {
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight)");
            WebElement loadMoreButton = driver.findElement(By.cssSelector(loadMoreButtonCssSelector));
            if(!loadMoreButton.isDisplayed()) {
                break;
            } else {
                loadMoreButton.click();
                try {
                    Thread.sleep(1000);
                } catch (Exception ex) {
                }
            }
        }
    }

    /**
     * Check if the current page is a 404 page.  If it is, added to the pageNotFound hashtable for later reporting and return true.
     * @param driver
     * @param href array where first value is current url and second value is where the url was found.
     * @return true if page is a 404 page
     */
    public boolean IsPageNotFound(WebDriver driver, String[]href) {
        if(driver.findElements(By.xpath("//title[contains(text(),'Page Not Found')] | //title[contains(text(),'Page not found')] | //title[contains(text(),'404 Not Found')]")).size() >= 1) {
            pageNotFound.put(href[0],href[1]);
            return true;
        }
        else {
            return false;
        }
    }

    public void CheckImagesNotFound() {

        //check for broken images
        for(String key:imagesFound.keySet()) {
            System.out.println("IMAGE:'"+key+"' REFERRER:'"+imagesFound.get(key)+"'");

            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(key).openConnection();
                connection.setRequestMethod("HEAD");
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    throw new Exception("IMAGE NOT FOUND");
                }
            } catch (Exception ex) {
                imagesNotFound.put(key,imagesFound.get(key));
            }
        }
    }
}
