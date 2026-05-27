import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;

public final class Cli {

    public static final class Options {
        public Path inputFile;
        public Path outputFile;

        public String outputFolder;
        public String basename;
        public String ext;

        public String pdfPassword = null;
        public boolean alpha = true;          // default: yes
        public boolean createFolder = false;  // default: no
    }

    public static Options parseArgs(String[] args) {
        if (args == null || args.length == 0) {
            throw usageError("Missing arguments.");
        }

        // --help / -h anywhere
        for (String a : args) {
            if (a == null) continue;
            String t = a.trim();
            if (t.equals("--help") || t.equals("-h") || t.equals("/?")) {
                throw usageError(""); // prints usage
            }
        }

        Options opt = new Options();

        // 1) Input is required, and must be first token (by your syntax)
        String inputToken = safeTrim(args[0]);
        if (inputToken.isEmpty() || inputToken.contains("=")) {
            throw usageError("First argument must be input.xxx (a file path), not a parameter.");
        }
        opt.inputFile = Paths.get(inputToken);

        int i = 1;

        // 2) Optional output file is next token if it does NOT contain '=' and is not help
        if (i < args.length) {
            String maybeOutput = safeTrim(args[i]);
            if (!maybeOutput.isEmpty()
                    && !maybeOutput.contains("=")
                    && !isHelpToken(maybeOutput)) {
                opt.outputFile = Paths.get(maybeOutput);
                i++;
            }
        }
        if (opt.outputFile == null) {
            opt.outputFile = defaultOutputFromInput(opt.inputFile);
        }

        String name = opt.outputFile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        opt.basename = (dot > 0) ? name.substring(0, dot) : name;
        opt.ext  = (dot > 0) ? name.substring(dot) : ""; // includes the "."

        // 3) Remaining tokens are parameter=setting
        for (; i < args.length; i++) {
            String token = safeTrim(args[i]);
            if (token.isEmpty()) continue;

            if (isHelpToken(token)) {
                throw usageError("");
            }

            int eq = token.indexOf('=');
            if (eq <= 0 || eq == token.length() - 1) {
                throw usageError(STR."Bad parameter format: '\{token}'. Expected parameter=setting.");
            }

            String key = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = token.substring(eq + 1).trim();

            applyParam(opt, key, value);
        }

        // 4) Validate extensions
        validateInputExt(opt.inputFile);
        validateOutputExt(opt.outputFile);

        // 5) If folder=yes, create a folder named according to output file name,
        //    and place outputFile inside it (preserving the output file name).
        opt.outputFolder = ".";
        if (opt.createFolder) {
            opt.outputFolder = ensureOutputFolder(opt.outputFile);
        }

        return opt;
    }

    private static boolean isHelpToken(String token) {
        String t = token.trim();
        return t.equals("--help") || t.equals("-h") || t.equals("/?");
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static void applyParam(Options opt, String key, String value) {
        switch (key) {
            case "p":
            case "password":
                if (value.isEmpty()) {
                    throw usageError("password cannot be empty.");
                }
                opt.pdfPassword = value;
                return;

            case "a":
            case "alpha":
                opt.alpha = parseYesNo(value, "alpha");
                return;

            case "f":
            case "folder":
                opt.createFolder = parseYesNo(value, "folder");
                return;

            default:
                throw usageError("Unknown parameter: '" + key + "'.");
        }
    }

    private static boolean parseYesNo(String value, String paramName) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.equals("yes")) return true;
        if (v.equals("no")) return false;
        throw usageError("Invalid value for " + paramName + ": '" + value + "'. Expected yes|no.");
    }

    private static Path defaultOutputFromInput(Path inputFile) {
        Path parent = inputFile.getParent();
        String fileName = inputFile.getFileName().toString();

        int dot = fileName.lastIndexOf('.');
        String base = (dot > 0) ? fileName.substring(0, dot) : fileName;

        String outName = base + ".png";
        return (parent == null) ? Paths.get(outName) : parent.resolve(outName);
    }

    private static void validateInputExt(Path inputFile) {
        String ext = getLowerExt(inputFile);
        // xxx is pdf, oxps, xps, pxl (PCL/XL) or pcl (PCL5).
        if (!(ext.equals("pdf") || ext.equals("oxps") || ext.equals("xps") || ext.equals("pxl") || ext.equals("pcl"))) {
            throw usageError("Unsupported input extension: ." + ext
                    + " (expected pdf, oxps, xps, pxl, pcl).");
        }
    }

    private static void validateOutputExt(Path outputFile) {
        String ext = getLowerExt(outputFile);
        // yyy is png, jpg or tif.
        if (!(ext.equals("png") || ext.equals("jpg") || ext.equals("tif"))) {
            throw usageError("Unsupported output extension: ." + ext
                    + " (expected png, jpg, tif).");
        }
    }

    private static String getLowerExt(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Creates an output folder named after the output file (without extension),
     * and returns a new output path inside that folder.
     *
     * Example:
     *   outputFile: /tmp/out.png  -> folder: /tmp/out/  -> output: /tmp/out/out.png
     */
    private static String ensureOutputFolder(Path outputFile) {
        Path parent = outputFile.getParent();
        String outName = outputFile.getFileName().toString();

        int dot = outName.lastIndexOf('.');
        String base = (dot > 0) ? outName.substring(0, dot) : outName;

        Path folderPath = (parent == null) ? Paths.get(base) : parent.resolve(base);

        try {
            Files.createDirectories(folderPath);
        } catch (IOException e) {
            throw usageError("Failed to create output folder '" + folderPath + "': " + e.getMessage());
        }

        return folderPath.toString();
    }

    private static IllegalArgumentException usageError(String message) {
        String m = (message == null) ? "" : message.trim();
        if (!m.isEmpty()) {
            m = m + "\n\n";
        }
        return new IllegalArgumentException(m + usageText());
    }

    public static String usageText() {
        return ""
                + "Mako Image Extract v1.2.0\n\n"
                + "   makoimageextract input.xxx [output.yyy] [parameter=setting] [parameter=setting] ...\n"
                + " Where:\n"
                + "   input.xxx              source file from which to extract pages, where xxx is pdf, oxps, xps, pxl (PCL/XL) or pcl (PCL5).\n"
                + "   output.yyy             file to write the output to, where yyy is png, jpg or tif.\n"
                + "                             If no output file is declared, <input>.png is assumed.\n"
                + "                             Large images (>2GB) will force output type to TIFF\n"
                + "   parameter=setting      one or more settings, described below.\n\n"
                + "Parameters:\n"
                + "   p[assword]=<password>  PDF password, if required to open the file.\n"
                + "   a[lpha]=yes|no         If the image is masked, write TIFF or PNG output with an alpha channel. Default: yes\n"
                + "   f[older]=yes|no        Create a folder to contain the output, named according to the output file name.\n"
                + "   -h, --help             Show this help.\n";
    }
}
