# brave-instrumentation-sparkjava

This module contains tracing filters and exception handlers for [SparkJava](http://sparkjava.com/)
The filters extract trace state from incoming requests. Then, they
reports Zipkin how long each request takes, along with relevant tags
like the http url. The exception handler ensures any errors are also
sent to Zipkin.

To enable tracing you need to add `before`, `exception` and `afterAfter`
hooks:
```java
sparkTracing = SparkTracing.create(tracing);
Spark.before(sparkTracing.before());
Spark.exception(Exception.class, sparkTracing.exception(new ExceptionHandlerImpl()));
Spark.afterAfter(sparkTracing.afterAfter());

// any routes you add are now traced, such as the below
Spark.get("/foo", (req, res) -> "bar");
```
