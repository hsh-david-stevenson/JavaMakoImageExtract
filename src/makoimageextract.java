/*
 * -----------------------------------------------------------------------
 *  <copyright file="makoimageextract.java" company="Hybrid Software Helix Ltd">
 *      Copyright (c) 2026 Global Graphics Software Ltd. All rights reserved.
 *  </copyright>
 *  <summary>
 *  This example is provided on an "as is" basis and without warranty of any kind.
 *  Hybrid Software Helix Ltd. does not warrant or make any representations
 *  regarding the use or results of use of this example.
 *  </summary>
 * -----------------------------------------------------------------------
 */

import com.globalgraphics.JawsMako.jawsmakoIF.*;

import java.nio.file.Path;
import java.nio.file.Paths;

public class makoimageextract {

    public static void main(String[] args) {
        // Get arguments
        Cli.Options params = null;
        try {
            params = Cli.parseArgs(args);

            System.out.printf("       Input: %s\n", params.inputFile.toAbsolutePath());
            System.out.printf("      Output: %s\n", params.outputFile.toAbsolutePath());
            System.out.printf("Password set? %s\n", params.pdfPassword != null ? "yes" : "no");
            System.out.printf("       Alpha: %s\n", params.alpha ? "yes" : "no");
            System.out.printf("      Folder: %s\n", params.createFolder ? "yes" : "no");
            System.out.printf(" Folder path: %s\n", params.outputFolder);

        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.exit(2);
        }

        // Start Mako
        var mako = IJawsMako.create();
        IJawsMako.enableAllFeatures(mako);

        // Open document
        var document = IPDFInput.create(mako).open(params.inputFile.toString()).getDocument();

        // Process document
        int documentImageCount = 0;
        long numPages = document.getNumPages();

        for (long pageIndex = 0; pageIndex < numPages; pageIndex++) {
            int pageImageCount = findImagesOnPage(mako, params, document, pageIndex);
            documentImageCount += pageImageCount;
        }

        System.out.println("File " + params.inputFile + " contains " + documentImageCount
                + " images on " + numPages + " pages.");

    }

    /**
     * Returns number of images found on the page.
     */
    public static int findImagesOnPage(IJawsMako mako, Cli.Options params, IDocument document, long pageIndex) {

        // Get page
        IPage page = document.getPage(pageIndex);

        // Find the path nodes (images are painted onto paths using an image brush).
        var pathNodes = page.getContent().findChildrenOfType(eDOMNodeType.eDOMPathNode, true);

        System.out.println("-- Examining " + pathNodes.size() + " path nodes on page " + (pageIndex + 1));

        int imageCount = 0;

        for (long i = 0; i < pathNodes.size(); i++) {
            IDOMPathNode path = IDOMPathNode.fromRCObject(pathNodes.getitem(i).toRCObject());

            // Get the brush used to fill this path
            IDOMImage image = IDOMImage.Null();
            IDOMBrush brush = path.getFill();
            if (brush == null) continue;

            // See if this brush is an image brush or a masked image brush
            IDOMImageBrush imageBrush = IDOMImageBrush.fromRCObject(brush.toRCObject());
            IDOMMaskedBrush maskedBrush = IDOMMaskedBrush.fromRCObject(brush.toRCObject());

            if (imageBrush != null) {

                // If it's a masked image, get the image from the brush that is used to paint the content, rather than the mask
                if (maskedBrush != null) {
                    IDOMBrush contentBrush = maskedBrush.getBrush();
                    if (contentBrush != null)
                        imageBrush = IDOMImageBrush.fromRCObject(contentBrush.toRCObject());

                    if (imageBrush != null)
                        if (params.alpha)
                            image = maskedBrush.getSimpleImageBrush(mako.getFactory()).getImageSource();
                        else
                            image = imageBrush.getImageSource();
                } else image = imageBrush.getImageSource();
            }

            if (image == null)
                continue;

            boolean bigTiff = image.getImageFrame(mako.getFactory()).getRawBytesPerRow() *
                    image.getImageFrame(mako.getFactory()).getHeight() > 0x7fffffff;

            String outputFilePathStr = "%s/%s%d%s".formatted(params.outputFolder, params.basename, ++imageCount, params.ext);

            // Dump the image
            switch (params.ext) {
                case ".jpg":
                    IDOMJPEGImage.encode(mako, image,
                            IOutputStream.createToFile(mako.getFactory(), outputFilePathStr));
                    break;
                case ".tif":
                    IDOMTIFFImage.encode(mako, image,
                            IOutputStream.createToFile(mako.getFactory(), outputFilePathStr));
                    break;
                case ".png":
                    IDOMPNGImage.encode(mako, image,
                            IOutputStream.createToFile(mako.getFactory(), outputFilePathStr));
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported output extension: " + params.ext);
            }

            System.out.println("An image was saved as " + outputFilePathStr);
        }

        return imageCount;
    }

    public static String getPathExtension(Path path) {
        var pathName = path.toString();
        int pos = pathName.lastIndexOf('.');
        if (pos == -1)
            return "";

        return pathName.substring(pos).toLowerCase();
    }

    // Determine the associated format for a given path from the file extension
    public static eFileFormat FileFormatFromPath(String sPath) {
        Path pathName = Paths.get(sPath);
        String ext = getPathExtension(pathName);

        return switch (ext) {
            case ".pdf" -> eFileFormat.eFFPDF;
            case ".xps" -> eFileFormat.eFFXPS;
            case ".oxps" -> eFileFormat.eFFOXPS;
            case ".ps" -> eFileFormat.eFFPS;
            case ".pxl" -> eFileFormat.eFFPCLXL;
            case ".pcl" -> eFileFormat.eFFPCL5;
            default -> eFileFormat.eFFUnknown;
        };
    }

    public enum eImageFormat {
        eIFJPG,
        eIFPNG,
        eIFTIF
    }

    // Return file extension for given file format
    public static String ExtensionFromFormat(eImageFormat fmt) {
        if (fmt == eImageFormat.eIFPNG)
            return ".png";
        if (fmt == eImageFormat.eIFJPG)
            return ".jpg";
        if (fmt == eImageFormat.eIFTIF)
            return ".tif";
        return "";
    }
}