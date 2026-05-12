package ua.uzhnu.collab.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * Аспект наскрізного логування для сервісного шару.
 *
 * <p>Автоматично логує час виконання кожного публічного методу у пакеті service. Реалізовано через
 * Spring AOP з використанням порад Around (навколо) для вимірювання тривалості.
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

  /** Pointcut: усі публічні методи у пакеті service. */
  @Pointcut("execution(public * ua.uzhnu.collab.service..*(..))")
  public void serviceLayerMethods() {}

  /** Порада Around: логує виклик методу, час виконання та виключення. */
  @Around("serviceLayerMethods()")
  public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
    String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
    String methodName = joinPoint.getSignature().getName();

    long startTime = System.currentTimeMillis();

    try {
      Object result = joinPoint.proceed();
      long duration = System.currentTimeMillis() - startTime;

      if (duration > 500) {
        log.warn("[SLOW] {}.{} — {} мс", className, methodName, duration);
      } else if (log.isDebugEnabled()) {
        log.debug("{}.{} — {} мс", className, methodName, duration);
      }

      return result;
    } catch (Exception ex) {
      long duration = System.currentTimeMillis() - startTime;
      log.error("{}.{} — ПОМИЛКА ({} мс): {}", className, methodName, duration, ex.getMessage());
      throw ex;
    }
  }
}
