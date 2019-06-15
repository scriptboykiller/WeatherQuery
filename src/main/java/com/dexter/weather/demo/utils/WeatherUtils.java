package com.dexter.weather.demo.utils;

import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Date;

/**
 * @author dexter
 * @date 2019-06-14
 * @Desc Weather forecast utility class.
 */
@Component
public class WeatherUtils {

    private static String weatherUrl;
    @Value("${weather.url}")
    public void setWeatherUrl(String weatherUrl) {
        WeatherUtils.weatherUrl = weatherUrl;
    }


    private static String weatherPrivateKey;
    @Value("${weather.private.key}")
    public void setWeatherPrivateKey(String weatherPrivateKey) {
        WeatherUtils.weatherPrivateKey = weatherPrivateKey;
    }

    private static String weatherPublicKey;
    @Value("${weather.public.key}")
    public void setWeatherPublicKey(String weatherPublicKey) {
        WeatherUtils.weatherPublicKey = weatherPublicKey;
    }

    /**
     * Generate HmacSHA1 signature with given data string and key
     * @param data
     * @param key
     * @return
     * @throws SignatureException
     */
    private static String generateSignature(String data, String key) throws SignatureException {
        String result;
        try {
            // get an hmac_sha1 key from the raw key bytes
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            // get an hmac_sha1 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            // compute the hmac on input data bytes
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            result = Base64.encodeBase64String(rawHmac);
        }
        catch (Exception e) {
            throw new SignatureException("Failed to generate HMAC : " + e.getMessage());
        }
        return result;
    }

    /**
     * Generate the URL to get current weather
     * @param location
     * @param language
     * @param unit
     * @return
     */
    public static String generateGetDiaryWeatherURL(
            String location,
            String language,
            String unit
    )  throws SignatureException, UnsupportedEncodingException {
        String timestamp = String.valueOf(new Date().getTime());
        String params = "ts=" + timestamp + "&ttl=1800&uid=" + weatherPublicKey;
        String signature = URLEncoder.encode(generateSignature(params, weatherPrivateKey), "UTF-8");
        return weatherUrl + "?" + params + "&sig=" + signature + "&location=" + location + "&language=" + language + "&unit=" + unit;
    }

}