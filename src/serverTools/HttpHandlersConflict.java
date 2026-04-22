package serverTools;

public class HttpHandlersConflict extends RuntimeException {
    public HttpHandlersConflict(String message) {
        super(message);
    }
}
