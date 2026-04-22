import com.sun.net.httpserver.HttpExchange;
import serverTools.*;
import java.io.*;

import java.util.*;

public class TestServer extends FrogServer {
    Date date = new Date(106, Calendar.JULY, 20);
    long curMillis = date.getTime();
    short seqNum = 0;

    public TestServer(int port) {
        super(port);
    }


    @CustomHttpHandler("/test/random/uuid")
    @HttpMethodTypes(HttpMethods.GET)
    private void randomHandler(HttpExchange exchange){
        Random random = new Random();
        String res = "\""+
                HexFormat.of().toHexDigits(random.nextInt())+"-"+
                HexFormat.of().toHexDigits(random.nextInt())+"-"+
                HexFormat.of().toHexDigits(random.nextInt())+"-"+
                HexFormat.of().toHexDigits(random.nextInt())
                +"\"";
        echo(exchange, res);
        System.out.println(res);
    }


    @CustomHttpHandler("/test/random/snowflake")
    @HttpMethodTypes(HttpMethods.GET)
    private void randomHandler2(HttpExchange exchange){
        Map<String, String> map = getParameters(exchange.getRequestURI().getQuery());
        byte datacenter = Byte.parseByte(map.get("datacenter"));
        byte machine = Byte.parseByte(map.get("machine"));
        long res=0;
        long current = System.currentTimeMillis();
        if(current!=curMillis){
            curMillis=current;
            seqNum=0;
        }
        else {
            seqNum++;
        }
        res+=curMillis-date.getTime();
        res*=32;
        res+=datacenter;
        res*=32;
        res+=machine;
        res*=4096;
        res+=seqNum;
        System.out.println(res);
        echo(exchange, ""+res);
    }

}
