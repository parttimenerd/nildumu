package nildumu;

import nildumu.mih.BitGraph;
import swp.util.Pair;
import swp.util.Utils;

import java.util.*;
import static nildumu.util.Util.p;

public class GraphRegistry {

    private boolean enabled = false;

    private List<Utils.Triple<String, String, CallGraph>> callGraphsPerTopic = new ArrayList<>();

    private List<Utils.Quadruple<Pair<String, String>, BitGraph, String, Boolean>> bitGraphsPerTopic = new ArrayList<>();

    private static GraphRegistry instance = new GraphRegistry();

    private GraphRegistry(){
    }

    public boolean enabled(){
        return enabled;
    }

    public void enable(){
        enabled = true;
    }

    public void disable(){
        enabled = false;
    }

    public void reset() {
        callGraphsPerTopic.clear();
        bitGraphsPerTopic.clear();
    }

    public static GraphRegistry get() {
        return instance;
    }

    /**
     * Creates a dot graph using the passed graphCreator if the registry is enabled.
     * Stores it under the topic.
     *
     * <b>Does not create any files, the svg file is created lazily</b>
     */
    public void store(String topic, String name, CallGraph cg){
        if (enabled){
            callGraphsPerTopic.add(new Utils.Triple<>(topic, name, cg));
        }
    }

    public List<Utils.Triple<String, String, CallGraph>> getCallGraphsPerTopic() {
        return callGraphsPerTopic;
    }

    public void store(String summary, String name, BitGraph graph, String s, boolean b) {
        if (enabled) {
            bitGraphsPerTopic.add(new Utils.Quadruple<>(p(summary, name), graph, s, b));
        }
    }

    public List<Utils.Quadruple<Pair<String, String>, BitGraph, String, Boolean>> getBitGraphsPerTopic() {
        return bitGraphsPerTopic;
    }
}
