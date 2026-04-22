package serverTools;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class FrogServer {
    private final HttpServer httpServer;
    private final Map<HttpExchange, StringBuilder> echoBuffers;
    public FrogServer(int port){
        echoBuffers = new HashMap<>();
        try{
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        }
        catch (IOException IOE){
            throw new RuntimeException(IOE);
        }

        List<Method> filteredMethods = getAllMethods(getClass()).stream()
                .filter(method -> method.isAnnotationPresent(CustomHttpHandler.class))
                .filter(method -> method.getParameterCount()==1)
                .filter(method -> HttpExchange.class.isAssignableFrom(method.getParameterTypes()[0]))
                .toList();
        packHandlers(filteredMethods).forEach(httpServer::createContext);
        System.out.println("Server started on http://localhost:"+port);
    }


    public void start(){
        httpServer.start();
    }

    public void stop(){
        httpServer.stop(0);
    }


    private List<Method> getAllMethods(Class<?> clz){
        if(clz==FrogServer.class) return new ArrayList<>();
        List<Method> methods = getAllMethods(clz.getSuperclass());
        methods.addAll(Arrays.asList(clz.getDeclaredMethods()));
        return methods;
    }

    private Map<String, HttpHandlerWrapper> packHandlers(Iterable<Method> methods){
        Map<String, HttpHandlerWrapper> handlerWrapperMap = new HashMap<>();
        for(Method m: methods){
            String path = m.getAnnotation(CustomHttpHandler.class).value();
            HttpHandlerWrapper wrapper = handlerWrapperMap.getOrDefault(path, new HttpHandlerWrapper());
            wrapper.addHandler(m);
            handlerWrapperMap.put(path, wrapper);
        }
        return handlerWrapperMap;
    }

    private class HttpHandlerWrapper implements HttpHandler{
        private final Map<String, Method> handlersMap;
        private HttpHandlerWrapper(){
            handlersMap = new HashMap<>();
        }

        private void addHandler(Method method){
            if(method.isAnnotationPresent(HttpMethodTypes.class)){
                HttpMethods[] httpMethods = method.getAnnotation(HttpMethodTypes.class).value();
                for(HttpMethods httpMethod: httpMethods){
                    if(handlersMap.containsKey(httpMethod.toString())) throw new HttpHandlersConflict("Method "+httpMethod.toString()+" is already used for this context!!!");
                    handlersMap.put(httpMethod.toString(), method);
                }
            }
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if(handlersMap.containsKey(exchange.getRequestMethod())){
                try{
                    Method handler = handlersMap.get(exchange.getRequestMethod());
                    handler.setAccessible(true);
                    handler.invoke(FrogServer.this, exchange);
                    if(echoBuffers.containsKey(exchange)){
                        byte[] bytes = echoBuffers.get(exchange).toString().getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                        try(OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                        echoBuffers.remove(exchange);
                    }

                }
                catch (IllegalAccessException _){

                }
                catch (Exception e){
                    System.out.println("Server caught an error: "+e.getCause());
                    throw new RuntimeException(e);
                }
            }
        }
    }

    protected void echo(HttpExchange exchange, String message){
        if(message==null || exchange==null) throw new NullPointerException();
        if(!echoBuffers.containsKey(exchange)){
            echoBuffers.put(exchange, new StringBuilder());
        }
        StringBuilder sb = echoBuffers.get(exchange).append(message);
        sb.append("\n");
    }

    protected Map<String, String> getParameters(String parameters){
        return Arrays.stream(parameters.split("&"))
                .map(this::parameterToPair)
                .filter(pair->pair[0]!=null)
                .collect(HashMap::new,
                        (map, pair)->{map.put(pair[0], pair[1]);},
                        HashMap::putAll);
    }

    private String[] parameterToPair(String parameter){
        String[] pair = parameter.split("=");
        if(pair.length==0) return new String[]{null, null};
        if(pair.length==1) return new String[]{pair[0], null};
        return pair;
    }

}
