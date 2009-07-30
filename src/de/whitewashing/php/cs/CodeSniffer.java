/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.whitewashing.php.cs;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.netbeans.api.extexecution.input.InputProcessor;
import org.netbeans.modules.php.project.util.PhpProgram;
import java.io.File;
import java.util.concurrent.Future;
import org.netbeans.api.extexecution.ExecutionDescriptor;
import org.netbeans.api.extexecution.ExecutionService;
import org.netbeans.api.extexecution.ExternalProcessBuilder;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;
import org.openide.loaders.DataObject;
import org.openide.text.Line;

/**
 *
 * @author benny
 */
public class CodeSniffer extends PhpProgram {

    public static final String PARAM_STANDARD = "--standard=Zend";
    public static final String PARAM_REPORT = "--report=xml";
    public static final String OUTPUT_REDIRECT = System.getProperty("java.io.tmpdir") + "/nb-phpcs-log.xml";
    public static final File XML_LOG = new File(System.getProperty("java.io.tmpdir"), "nb-phpcs-log.xml"); // NOI18N

    public StringBuilder output = null;

    public CodeSniffer(String command) {
        super(command);
    }

    public void execute(FileObject fo) {
        final File parent = FileUtil.toFile(fo.getParent());

        ExternalProcessBuilder externalProcessBuilder = new ExternalProcessBuilder(this.getProgram())
                .workingDirectory(parent)
                .addArgument(PARAM_REPORT)
                .addArgument(fo.getNameExt());

        this.output = new StringBuilder();

        ExecutionDescriptor descriptor = new ExecutionDescriptor()
                .frontWindow(false)
                .controllable(false)
                .outProcessorFactory(
                    new ExecutionDescriptor.InputProcessorFactory() {
                        public InputProcessor newInputProcessor(InputProcessor defaultProcessor) {
                            return new InputProcessor() {
                                boolean started = false;
                                public void processInput(char[] chars) throws IOException {
                                    output.append(chars);
                                }

                                public void reset() throws IOException {
                                }

                                public void close() throws IOException {
                                }
                            };
                        }
                    }
                );

        ExecutionService service = ExecutionService.newService(externalProcessBuilder,
                descriptor, "PHP Coding Standards");


        Future<Integer> task = service.run();
        try {
            task.get();
        } catch (InterruptedException ex) {
            Exceptions.printStackTrace(ex);
            return;
        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            return;
        }

        annotateWithCodingStandardHints(fo);
    }

    private void annotateWithCodingStandardHints(FileObject fo)
    {
        System.err.println("### MANUEL PICHLER ###");
        System.err.print(this.output.toString());
        System.err.flush();

        StringBuilder sb = new StringBuilder(this.output.substring(this.output.indexOf("<")));

        CodeSnifferXmlLogParser parser = new CodeSnifferXmlLogParser();
        CodeSnifferXmlLogResult rs = parser.parse(sb);

        try {
            DataObject d = DataObject.find(fo);
            
            LineCookie cookie = (LineCookie)d.getCookie(LineCookie.class);

            Line.Set lineSet = null;
            Line line = null;
            for(int i = 0; i < rs.getCsErrors().size(); i++) {
                lineSet = cookie.getLineSet();
                line = lineSet.getOriginal(rs.getCsErrors().get(i).getLineNum());
                rs.getCsErrors().get(i).attach(line);
            }

            for(int i = 0; i < rs.getCsWarnings().size(); i++) {
                lineSet = cookie.getLineSet();
                line = lineSet.getOriginal(rs.getCsWarnings().get(i).getLineNum());
                rs.getCsWarnings().get(i).attach(line);
            }
        } catch (DataObjectNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
