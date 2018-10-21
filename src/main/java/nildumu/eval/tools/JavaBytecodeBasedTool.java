package nildumu.eval.tools;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import nildumu.eval.*;

/**
 * Tool that analyses Java bytecode
 */
public abstract class JavaBytecodeBasedTool extends AbstractTool {

    final static String MAIN_CLASS_NAME = "Main";

    final Path javaLibForCompilation;

    protected JavaBytecodeBasedTool(String name, Path javaLibForCompilation) {
        super(name);
        this.javaLibForCompilation = javaLibForCompilation;
    }

    /**
     * Generates Java source code within the default package and with a class with name
     * {@value MAIN_CLASS_NAME}
     */
    public abstract String generateJavaSourceCode(TestProgram program);

    public abstract String getShellCommand(TestProgram program, Path javaByteCodeFile,
                                           PathFormatter formatter);

    public abstract LeakageParser getLeakageParser(TestProgram program);

    @Override
    public AnalysisPacket createPacket(TestProgram program, Path folder) {
        String sourceCode = generateJavaSourceCode(program);
        Path sourceFile = folder.resolve(MAIN_CLASS_NAME + ".java");
        try {
            Files.write(sourceFile, Collections.singleton(sourceCode));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return new AnalysisPacket(this, program) {
            @Override
            public String getShellCommand(PathFormatter formatter) {
                return String.format("cd %s; javac %s %s.java; %s",
                        formatter.format(folder),
                        javaLibForCompilation == null ? "" :
                                String.format("-cp %s:.", javaLibForCompilation.toAbsolutePath()),
                        MAIN_CLASS_NAME,
                        JavaBytecodeBasedTool.this.getShellCommand(program,
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
