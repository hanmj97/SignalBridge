package dev.signalbridge.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
  @Override public void addCorsMappings(CorsRegistry registry) {
    // Trusted localhost Vite dev origins ONLY. Real deployment uses same-origin HTTPS.
    registry.addMapping("/api/v1/**")
      .allowedOrigins("http://localhost:5173","http://127.0.0.1:5173")
      .allowedMethods("GET","POST","PATCH","DELETE")
      .allowedHeaders("Content-Type","Accept","X-CSRF-TOKEN")
      .allowCredentials(true);
  }
}
