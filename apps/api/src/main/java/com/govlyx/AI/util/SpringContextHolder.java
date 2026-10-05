package com.govlyx.AI.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Utility that exposes the Spring {@link ApplicationContext} to non-bean
 * code paths (e.g. static factory methods in DTO classes).
 *
 * <p>Usage:
 * <pre>
 *     MyRepo repo = SpringContextHolder.getBean(MyRepo.class);
 * </pre>
 *
 * <p>Only use this where constructor/field injection is impossible (e.g. inside
 * a static method). Prefer normal injection everywhere else.
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext ctx;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.ctx = applicationContext;
    }

    /**
     * Returns the bean of the requested type from the application context.
     *
     * @param type the bean class
     * @param <T>  the bean type
     * @return the bean instance
     * @throws IllegalStateException if the context has not been initialised yet
     */
    public static <T> T getBean(Class<T> type) {
        if (ctx == null) {
            throw new IllegalStateException(
                    "SpringContextHolder: ApplicationContext has not been set yet.");
        }
        return ctx.getBean(type);
    }
}
