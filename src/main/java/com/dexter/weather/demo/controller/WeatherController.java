package com.dexter.weather.demo.controller;

import com.dexter.weather.demo.constants.WeatherConstants;
import com.dexter.weather.demo.model.Weather;
import com.dexter.weather.demo.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.security.SignatureException;
import java.util.List;
import java.util.Map;

/**
 * @author dexter
 * @date 2019-06-14
 * @Desc Controller class for Providing outbound restful service.
 */
@SpringBootApplication
@Controller
@ComponentScan(basePackages={"com.dexter.weather.demo"})
public class WeatherController {

    private  static final Logger logger = LoggerFactory.getLogger(WeatherController.class);

    @Resource
    private WeatherService weatherService;

    @RequestMapping(value = "getResultByCityName",method= RequestMethod.POST)
    @ResponseBody
    public Weather getResultByCityName(@RequestBody String cityName) throws UnsupportedEncodingException, SignatureException {
        logger.info("Invoke getResultByCityName with parameter {}",cityName);
        return weatherService.getWeatherByCityName(cityName);
    }

    @RequestMapping(value = {"/", "/index"})
    public String IndexPage(Map<String, Object> model){
        logger.info("Invoke IndexPage ");
        List<String> countryList = weatherService.getDefaultWeatherLocations();
        model.put("countryList",countryList);
        return WeatherConstants.INDEX_PAGE;
    }
    public static void main(String[] args) {
        SpringApplication.run(WeatherController.class, args);
    }
}
