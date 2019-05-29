package nildumu.eval.tools;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import nildumu.eval.*;

/**
 * Abstract class for analysis tools.
 */
public abstract class AbstractTool implements Comparable<AbstractTool> {

    public final String name;
    public final boolean supportsFunctions;

    protected AbstractTool(String name, boolean supportsFunctions) {
        this.name = name;
        this.supportsFunctions = supportsFunctions;
    }

    /**
     * Create a packet and place all generated files into the passed folder.
     */
    public abstract AnalysisPacket createPacket(TestProgram program, Path folder);

    protected Path writeOrDie(Path folder, String filename, String content){
        Path path = folder.resolve(filename);
        try {
            Files.write(path, Collections.singleton(content));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
        return path;
    }

    @Override
    public String toString() {
        return name;
    }

    public static List<AbstractTool> getDefaultTools(){
        return Arrays.asList(
                new NildumuDemoTool(2, 2),
               // new NildumuDemoTool(5, 5),
               new Flowcheck(),
          //      new LeakWatch(),
                new ApproxFlow(2),
            //   new ApproxFlow(5),
               new ApproxFlow(),
               new Nildumu(2, 2)//,
               // new NildumuDemoTool(),
                //new Nildumu()
              //  new Nildumu(5, 5)//,
               //new Quail()
        );
    }

    public static List<AbstractTool> getDefaultToolsWithoutVariations(){
        return Arrays.asList(
                //new NildumuDemoTool(2, 2),
                //new NildumuDemoTool(5, 5),
                new Flowcheck(),
               // new LeakWatch(),
                new ApproxFlow(),
                //new NildumuDemoTool()//,
                new Nildumu()
                //new Nildumu()//,
                //new Quail()
        );
    }

    @Override
    public int compareTo(AbstractTool o) {
        return name.compareTo(o.name);
    }

    public boolean isInterprocedural(){
        return supportsFunctions;
    }

    public AbstractTool setUnwindingLimit(int limit){ return this; }
}
