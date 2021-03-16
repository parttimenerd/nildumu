package nildumu.eval.tools;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

import nildumu.eval.*;

/**
 * Tool that analyses Java bytecode and supports methods, just there for later use
 */
public abstract class JavaBytecodeBasedTool extends AbstractTool {

    final Path javaLibForCompilation;

    protected JavaBytecodeBasedTool(String name, Path javaLibForCompilation, int unwind) {
        super(name, unwind, ".java");
        this.javaLibForCompilation = javaLibForCompilation;
    }

    public abstract String generateJavaSourceCode(TestProgram program, String name);

    public abstract String getShellCommand(TestProgram program, String name,
                                           Path javaByteCodeFile,
                                           PathFormatter formatter);

    public abstract LeakageParser getLeakageParser(TestProgram program);

    @Override
    public AnalysisPacket createPacket(TestProgram program, Path folder) {
        String name = program.getUniqueCodeName("");
        String sourceCode = generateJavaSourceCode(program, name);
        Path sourceFile = folder.resolve(name + ".java");
        try {
            Files.write(sourceFile, Collections.singleton(sourceCode));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return new AnalysisPacket(this, program) {
            @Override
            public String getShellCommand(PathFormatter formatter, Duration timeLimit) {
                return String.format("cd %s; javac %s %s.java; %s",
                        formatter.format(folder),
                        javaLibForCompilation == null ? "" :
                                String.format("-cp %s:.", javaLibForCompilation.toAbsolutePath()),
                        name,
                        JavaBytecodeBasedTool.this.getShellCommand(program,
                                name,
                                folder,
                                formatter));
            }

            @Override
            public LeakageParser getLeakageParser() {
                return JavaBytecodeBasedTool.this.getLeakageParser(program);
            }
        };
    }
}
