package com.dexter.weather.demo;

import com.dexter.weather.demo.constants.WeatherConstants;
import com.dexter.weather.demo.controller.WeatherController;
import com.dexter.weather.demo.service.WeatherService;
import junit.framework.TestCase;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.CollectionUtils;

import java.net.URL;
import java.util.List;

/**
 * @author dexter
 * @date 2019-06-14
 * @Desc Test case for weather demo application.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes=WeatherController.class,webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DemoApplicationTests {

    private static final Logger logger = LoggerFactory.getLogger(DemoApplicationTests.class);

    @LocalServerPort
    private int port;

    private URL base;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WeatherService weatherService;

    @Before
    public void setUp() throws Exception {
        String url = String.format("http://localhost:%d/", port);
        logger.info(String.format("port is : [%d]", port));
        this.base = new URL(url);
    }

    @Test
    public void getResultByCityName() {

        // Use Optional to replace null object.(Java 8)
        ResponseEntity<String> response = this.restTemplate.postForEntity(
                this.base.toString() + "/getResultByCityName", WeatherConstants.TEST_PARAM_SYDNEY,String.class, java.util.Optional.empty());
        logger.info(String.format("Test result：%s", response.getBody()));

        if(StringUtils.isNotEmpty(response.getBody())){
            JSONObject jsonObject = JSONObject.fromObject(response.getBody());
            TestCase.assertNotNull(jsonObject.get(WeatherConstants.TEST_RESP_CITY));
            TestCase.assertNotNull(jsonObject.get(WeatherConstants.TEST_RESP_DATETIME));
            TestCase.assertNotNull(jsonObject.get(WeatherConstants.TEST_RESP_WEATHER));
            TestCase.assertNotNull(jsonObject.get(WeatherConstants.TEST_RESP_TEMPERATURE));
            TestCase.assertNotNull(jsonObject.get(WeatherConstants.TEST_RESP_WIND));
        }else {
            TestCase.fail();
        }

    }

    @Test
    public void getDefaultLoations() {
        List<String> locations = weatherService.getDefaultWeatherLocations();
        if(!CollectionUtils.isEmpty(locations)){
            TestCase.assertTrue(locations.size()>=3);
        }else{
            TestCase.fail();
        }
    }

}
