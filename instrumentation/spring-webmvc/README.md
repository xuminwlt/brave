# brave-instrumentation-spring-webmvc
This module contains a tracing interceptor for [Spring WebMVC](https://docs.spring.io/spring/docs/current/spring-framework-reference/html/mvc.html)
`TracingHandlerInterceptor` extracts trace state from incoming requests.
Then, it reports Zipkin how long each request takes, along with relevant
tags like the http url.

## Configuration

Tracing always needs a bean of type `HttpTracing` configured. Make sure
it is in place before proceeding.

Then, configure `TracingHandlerInterceptor` in either XML or Java.

```xml
<mvc:interceptors>
  <bean class="brave.spring.servlet.TracingHandlerInterceptor">
  </bean>
</mvc:interceptors>
```

```java
@Configuration
@EnableWebMvc
class TracingConfig extends WebMvcConfigurerAdapter {
  @Bean AsyncHandlerInterceptor tracingInterceptor(HttpTracing httpTracing) {
    return TracingHandlerInterceptor.create(httpTracing);
  }

  @Autowired
  private AsyncHandlerInterceptor tracingInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(tracingInterceptor);
  }
}
```
