package com.acme.support.channel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
class DeskWebConfig implements WebMvcConfigurer {
    @Override public void addCorsMappings(CorsRegistry registry){
        registry.addMapping("/fragments/**").allowedOriginPatterns("*").allowedMethods("*").allowCredentials(true);
        registry.addMapping("/tickets/**").allowedOriginPatterns("*").allowedMethods("*").allowCredentials(true);
    }
    @Bean CorsConfigurationSource corsConfigurationSource(){
        CorsConfiguration cfg=new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.addAllowedMethod("*");
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",cfg);
        return source;
    }
}
