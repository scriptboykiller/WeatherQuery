package com.dexter.weather.demo.service.Impl;

import com.dexter.weather.demo.constants.WeatherConstants;
import com.dexter.weather.demo.model.Weather;
import com.dexter.weather.demo.service.WeatherService;
import com.dexter.weather.demo.utils.WeatherUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.SignatureException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @author dexter
 * @date 2019-06-13
 * @Desc Weather service class to support querying weather data from third-party API.
 */
@Service
public class WeatherServiceImpl implements WeatherService {

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private Weather weather;

    @Value("${weather.language}")
    private String WEATHER_LANG;

    @Value("${weather.unit}")
    private String WEATHER_UNIT;

    @Value("${weather.default.locations}")
    private String WEATHER_LOCATIONS;

    @Override
    public Weather getWeatherByCityName(String cityName) throws UnsupportedEncodingException, SignatureException {
        String uri= WeatherUtils.generateGetDiaryWeatherURL(cityName,WEATHER_LANG,WEATHER_UNIT);
        return this.getWeatherData(uri,cityName);
    }

    @Override
    public List<String> getDefaultWeatherLocations(){
        List<String> defaultLocations = Arrays.asList(WEATHER_LOCATIONS.split(","));
        return defaultLocations;

    }

    /**
     * Get weather data from rest service and generate the dto.
     * @param uri
     * @param city
     * @return com.dexter.weather.demo.model.Weather
     */
    private Weather getWeatherData(String uri,String city) {
        ResponseEntity<String> response = restTemplate.exchange(URI.create(uri), HttpMethod.GET,null, String.class);
        String strBody = StringUtils.EMPTY;


        if(response.getStatusCodeValue()==200){
            strBody=response.getBody();
        }

        // Convert response to json object.
        JSONObject jsonObject = JSONObject.fromObject(strBody);
        JSONArray results = jsonObject.getJSONArray(WeatherConstants.JSON_RESP_RESULTS);
        JSONObject result = results.getJSONObject(0);
        JSONObject todayResult = result.getJSONObject(WeatherConstants.JSON_RESP_NOW);

        weather.setCity(city);
        weather.setWeather(todayResult.getString(WeatherConstants.JSON_RESP_TEXT));
        weather.setTemperature(todayResult.getString(WeatherConstants.JSON_RESP_TEMPERATURE)+WeatherConstants.JSON_RESP_TEMPERATURE_UNIT);
        weather.setWind(todayResult.getString(WeatherConstants.JSON_RESP_WIND)+WeatherConstants.JSON_RESP_WIND_UNIT);
        weather.setDatetime(this.ConvertDateTime(result.getString(WeatherConstants.JSON_RESP_UPDATE_TIME)));

        return weather;
    }


    /**
     * Convert offsetDatetime to CHINA local time.(Java 8)
     * @param dateTime
     * @return java.lang.String
     */
    private String ConvertDateTime(String dateTime){
        String newTime;
        OffsetDateTime ausDateTime = OffsetDateTime.parse(dateTime);
        OffsetDateTime beijingDateTime = ausDateTime.withOffsetSameInstant(ZoneOffset.ofHours(8));
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("EEEE hh:mm a",Locale.ENGLISH);
        newTime = beijingDateTime.format(formatter);
        return newTime;
    }
}
