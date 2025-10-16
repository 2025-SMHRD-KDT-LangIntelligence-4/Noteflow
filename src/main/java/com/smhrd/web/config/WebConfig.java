package com.smhrd.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebConfig ---------------------------------------- 정적 리소스(js/css/images 등) 및
 * 모듈 import 경로 설정 ES Module(import/export) 구조를 안정적으로 지원하기 위한 설정 포함
 * ----------------------------------------
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	/**
	 * ✅ 정적 리소스 핸들러 설정 - /js/** 요청 → classpath:/static/js/ 매핑 - /css/** 요청 →
	 * classpath:/static/css/ 매핑 - /images/** 요청 → classpath:/static/images/ 매핑 -
	 * 브라우저 캐시를 1시간(3600초) 유지
	 */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/").setCachePeriod(3600);

		registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/").setCachePeriod(3600);

		registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/")
				.setCachePeriod(3600);

		registry.addResourceHandler("/fonts/**").addResourceLocations("classpath:/static/fonts/").setCachePeriod(3600);
	}

	/**
	 * ✅ CORS 설정 (필요 시) - 모듈 import 간 요청이 cross-origin으로 인식되는 경우 대비 - 동일 도메인 내에서는
	 * 사실상 영향 없음 - 단, 프론트에서 fetch() 또는 import 시 경로를 절대경로(/js/...)로 지정했을 때 안정성 확보
	 */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**").allowedOrigins("http://localhost:8080") // 개발 환경 기준
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS").allowedHeaders("*").allowCredentials(true)
				.maxAge(3600);
	}

}
