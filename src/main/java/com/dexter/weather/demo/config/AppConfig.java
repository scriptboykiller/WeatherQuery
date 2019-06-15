package com.dexter.weather.demo.config;

import com.dexter.weather.demo.model.Weather;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author dexter
 * @date 2019-06-14
 * @Desc Spring boot configuration file.
 */
@Configuration
public class AppConfig {
    @Bean
    public Weather weather() {
        return new Weather();
    }
}
