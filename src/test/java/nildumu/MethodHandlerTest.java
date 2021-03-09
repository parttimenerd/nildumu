package nildumu;

import nildumu.mih.MethodInvocationHandler;
import org.junit.jupiter.api.Test;

public class MethodHandlerTest {

    @Test
    public void testParseExampleStrings(){
        MethodInvocationHandler.getExamplePropLines().forEach(MethodInvocationHandler::parse);
    }
}
