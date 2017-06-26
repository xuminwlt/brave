package brave.http;

import io.undertow.Undertow;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(2)
@State(Scope.Benchmark)
public abstract class HttpServerBenchmarks {

  Undertow server;
  OkHttpClient client;
  String baseUrl;

  protected String baseUrl() {
    return baseUrl;
  }

  @Setup(Level.Trial) public void init() throws Exception {
    baseUrl = "http://127.0.0.1:" + initServer();
    client = new OkHttpClient();
  }

  @TearDown(Level.Trial) public void close() throws Exception {
    if (server != null) server.stop();
    client.dispatcher().executorService().shutdown();
  }

  protected int initServer() throws ServletException {
    DeploymentInfo servletBuilder = Servlets.deployment()
        .setClassLoader(getClass().getClassLoader())
        .setContextPath("/")
        .setDeploymentName("test.war");

    init(servletBuilder);

    DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
    manager.deploy();
    server = Undertow.builder()
        .addHttpListener(0, "127.0.0.1")
        .setHandler(manager.start()).build();
    server.start();
    return ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
  }

  protected abstract void init(DeploymentInfo servletBuilder);

  @Benchmark public void server_get() throws Exception {
    get("/nottraced");
  }

  @Benchmark public void unsampledServer_get() throws Exception {
    get("/unsampled");
  }

  @Benchmark public void tracedServer_get() throws Exception {
    get("/traced");
  }

  void get(String path) throws IOException {
    client.newCall(new Request.Builder().url(baseUrl() + path).build())
        .execute()
        .body().close();
  }
}
