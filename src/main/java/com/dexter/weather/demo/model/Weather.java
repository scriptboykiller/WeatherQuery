package com.dexter.weather.demo.model;

import java.io.Serializable;

/**
 * @author dexter
 * @date 2019-06-14
 * @Desc Entity class for weather data model.
 */
public class Weather implements Serializable {

    private static final long serialVersionUID = 1L;

    private String city;
    private String datetime;
    private String weather;
    private String temperature;
    private String wind;

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDatetime() {
        return datetime;
    }

    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }

    public String getWeather() {
        return weather;
    }

    public void setWeather(String weather) {
        this.weather = weather;
    }

    public String getTemperature() {
        return temperature;
    }

    public void setTemperature(String temperature) {
        this.temperature = temperature;
    }

    public String getWind() {
        return wind;
    }

    public void setWind(String wind) {
        this.wind = wind;
    }
}
