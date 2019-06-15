package com.dexter.weather.demo.service;

import com.dexter.weather.demo.model.Weather;

import java.io.UnsupportedEncodingException;
import java.security.SignatureException;
import java.util.List;

/**
 * @author dexter
 * @date 2019-06-13 17:46
 * @Desc Weather service interface.
 */
public interface WeatherService  {

    /**
     * Get weather data by city name.
     *
     * @param cityName
     * @return
     * @throws UnsupportedEncodingException
     * @throws SignatureException
     */
    Weather getWeatherByCityName(String cityName) throws UnsupportedEncodingException, SignatureException;

    /**
     * Get default weather locations.
     *
     * @return
     */
    List<String> getDefaultWeatherLocations();

}
