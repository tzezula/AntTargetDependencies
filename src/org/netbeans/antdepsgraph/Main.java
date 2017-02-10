package org.netbeans.antdepsgraph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.function.Predicate;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.Target;

/**
 *
 * @author Tomas Zezula
 */
public class Main {
    public static void main(String[] args) {
        Set<String> restrictions = new HashSet<>();
        File buildFile = null;
        for (int i = 0; i < args.length; i++ ) {
            if ("-r".equals(args[i])) {
                if (i+1 >= args.length) {
                    usage();
                }
                final String restriction = args[++i];
                final File rf = new File(restriction);
                if (!rf.canRead() || !rf.isFile()) {
                    error(5,"Rectriction on non existent file.");
                }
                restrictions.add(rf.getAbsolutePath());
            } else {
                if (buildFile != null) {
                    usage();
                }
                buildFile = new File(args[i]);
            }
        }
        if (buildFile == null) {
            usage();
        }
        if (!buildFile.canRead() || !buildFile.isFile()) {
            error(2, "Invalid build file: " + buildFile.getPath());
        }
        final File parent = buildFile.getParentFile();
        if (!parent.canWrite()) {
            error(3,"Cannot write to: " + parent.getAbsolutePath());
        }
        final File graphFile = new File(parent, stripExt(buildFile.getName())+".dot");
        try {
            try(final PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(graphFile), Charset.forName("UTF-8")))) {
                final Project prj = new Project();
                ProjectHelper.configureProject(prj, buildFile);
                printGraph(out, prj, (p) -> restrictions.isEmpty() || restrictions.contains(p));
            }
        } catch (IOException ioe) {
            error(4, "Cannot write: " + graphFile.getAbsolutePath());
        }
    }

    private static void printGraph(
            final PrintWriter out,
            final Project prj,
            final Predicate<String> restrictions) {
        printHeader(out, prj);
        final Hashtable<String, Target> targets = prj.getTargets();
        final String[] targetNames = targets.keySet().toArray(new String[targets.size()]);
        final Vector<Target> sorted = prj.topoSort(targetNames, targets, true);
        for (Target t : sorted) {
            printTarget(out, t, restrictions, targets);
        }
        printFooter(out);
    }

    private static void printHeader(
            final PrintWriter out,
            final Project prj) {
        out.printf("strict digraph \"%s\" {%n", prj.getName());
    }

    private static void printTarget(
            final PrintWriter out,
            final Target target,
            final Predicate<String> restrictions,
            final Map<String,Target> allTargets) {
        final String name = target.getName();
        final String loc = target.getLocation().getFileName();
        if (restrictions.test(loc) && isTopLevel(target, allTargets)) {
            final Enumeration<String> deps = target.getDependencies();
            while (deps.hasMoreElements()) {
                String dep = deps.nextElement();
                Target dt = allTargets.get(dep);
                if (restrictions.test(dt.getLocation().getFileName()) && isTopLevel(dt, allTargets)) {
                    out.printf("  \"%s\" -> \"%s\";%n", name, dep);
                }
            }
        }
    }

    private static void printFooter(final PrintWriter out) {
        out.println("}");   //NOI18N
    }

    private static boolean isTopLevel(
            final Target target,
            final Map<String,Target> allTargets) {
        final String name = target.getName();
        final int index = name.indexOf('.');
        if (index > 0 && index < name.length() -1 ) {
            return !allTargets.containsKey(name.substring(index+1));
        } else {
            return true;
        }
    }

    private static String stripExt(final String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ?
                fileName.substring(0, index) :
                fileName;
    }

    private static void usage() {
        error(1, "usage: AntDependenciesGraph [-r defining-build-file] build.xml");
    }

    private static void error(
            final int exitCode,
            final String message) {
        System.err.println(message);
        System.exit(exitCode);
    }
}
