package nildumu.eval;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.*;

public class PacketList implements Iterable<AnalysisPacket> {

    private final List<AnalysisPacket> packets;

    public PacketList(List<AnalysisPacket> packets) {
        this.packets = packets;
    }

    public PacketList() {
        this(new ArrayList<>());
    }


    public String getTemciConfig(Duration timeLimit){
        return packets.stream()
                .filter(p -> !p.emptyPacket)
                .map(p -> p.getShellCommandWithAbsolutePaths(timeLimit))
                .collect(Collectors.joining("\n"));
    }

    public void writeTemciConfigOrDie(String filename, Duration timeLimit){
        try {
            Files.write(Paths.get(filename), Collections.singleton(getTemciConfig(timeLimit)));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    public int size() {
        return packets.size();
    }

    public boolean isEmpty() {
        return packets.isEmpty();
    }

    public Iterator<AnalysisPacket> iterator() {
        return packets.iterator();
    }

    public boolean add(AnalysisPacket packet) {
        return packets.add(packet);
    }

    public boolean addAll(Collection<? extends AnalysisPacket> c) {
        return packets.addAll(c);
    }

    @Override
    public boolean equals(Object o) {
        return packets.equals(o);
    }

    @Override
    public int hashCode() {
        return packets.hashCode();
    }

    public Stream<AnalysisPacket> stream() {
        return packets.stream();
    }

    public static Collector<AnalysisPacket, ?, PacketList> collector(){
        return Collectors.collectingAndThen(Collectors.toList(), PacketList::new);
    }
}
