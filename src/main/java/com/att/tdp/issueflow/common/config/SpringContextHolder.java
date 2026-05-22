package com.att.tdp.issueflow.common.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Caches the running {@link ApplicationContext} in a static field so that JPA entity listeners —
 * which are instantiated by reflection and never managed by Spring — can look up beans at callback
 * time via {@link #bean(Class)}.
 *
 * <p>This is the minimal workable bridge between JPA's reflective listener construction and
 * Spring's dependency-injection graph. The static field is populated once at startup and is safe to
 * read from any thread thereafter.
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

  private static ApplicationContext context;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    SpringContextHolder.context = applicationContext;
  }

  /**
   * Retrieves a bean by type from the running application context.
   *
   * @param type the required bean type
   * @param <T> the bean type
   * @return the bean instance
   */
  public static <T> T bean(Class<T> type) {
    return context.getBean(type);
  }
}
