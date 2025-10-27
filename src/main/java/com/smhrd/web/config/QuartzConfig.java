package com.smhrd.web.config;

import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import java.io.IOException;
import java.util.Properties;

@Configuration
public class QuartzConfig {
    
    private final ApplicationContext applicationContext;
    
    public QuartzConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    /**
     * Spring Bean으로 Quartz Job을 생성하기 위한 Factory
     * (Autowiring 지원)
     */
    @Bean
    public JobFactory jobFactory() {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }
    
    /**
     * Quartz Scheduler Factory Bean
     */
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(JobFactory jobFactory) throws IOException {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setJobFactory(jobFactory);
        factory.setQuartzProperties(quartzProperties());
        factory.setWaitForJobsToCompleteOnShutdown(true);
        factory.setAutoStartup(true);
        return factory;
    }
    
    /**
     * Quartz 설정 속성
     */
    private Properties quartzProperties() throws IOException {
        PropertiesFactoryBean propertiesFactoryBean = new PropertiesFactoryBean();
        propertiesFactoryBean.setLocation(new ClassPathResource("/quartz.properties"));
        propertiesFactoryBean.afterPropertiesSet();
        return propertiesFactoryBean.getObject();
    }
    
    /**
     * Spring Bean Autowiring을 지원하는 JobFactory
     */
    public static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {
        private ApplicationContext applicationContext;
        
        public void setApplicationContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }
        
        @Override
        protected Object createJobInstance(org.quartz.spi.TriggerFiredBundle bundle) throws Exception {
            Object job = super.createJobInstance(bundle);
            applicationContext.getAutowireCapableBeanFactory().autowireBean(job);
            return job;
        }
    }
}
