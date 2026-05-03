package com.order.order.AOP;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;


@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceLayer() {
    }

    @Around("serviceLayer()")
    public Object logAround(ProceedingJoinPoint jp) throws Throwable {
        String cls = jp.getTarget().getClass().getSimpleName();
        String method = jp.getSignature().getName();

        log.debug("→ {}.{}() called with {} args", cls, method, jp.getArgs().length);
        long start = System.currentTimeMillis();

        try {
            Object result = jp.proceed();
            log.debug("← {}.{}() completed in {}ms",
                    cls, method, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            log.error("✗ {}.{}() threw {} after {}ms",
                    cls, method,
                    ex.getClass().getSimpleName(),
                    System.currentTimeMillis() - start);
            throw ex;
        }
    }
}
