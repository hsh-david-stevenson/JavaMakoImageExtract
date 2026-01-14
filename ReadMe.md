# Java Mako Image Extract

Mako Image Extract looks for images on every page of the specimen PDF (or other supported PDL) then dumps them as an image file.

```plain
Mako Image Extract v1.2.0

   makoimageextract input.xxx [output.yyy] [parameter=setting] [parameter=setting] ...
 Where:
   input.xxx              source file from which to extract pages, where xxx is pdf, oxps, xps, pxl (PCL/XL) or pcl (PCL5).
   output.yyy             file to write the output to, where yyy is png, jpg or tif.
                             If no output file is declared, <input>.png is assumed.
                             Large images (>2GB) will force output type to TIFF
   parameter=setting      one or more settings, described below.

Parameters:
   p[assword]=<password>  PDF password, if required to open the file.
   a[lpha]=yes|no         If the image is masked, write TIFF or PNG output with an alpha channel. Default: yes
   f[older]=yes|no        Create a folder to contain the output, named according to the output file name.
   -h, --help             Show this help.
   ```

## How it works

Images are not a node type in Mako. Instead you will find them inside image or masked brushes, which are then used as fills, strokes, or opacity (soft) masks used in bona-fide nodes (for example, but not limited to, paths or glyphs nodes).

The way an everyday plain image will be represented is to be put inside an `IDOMImageBrush`, which is then used to fill a rectangular path that defines the area the image occupies. To find them, _Mako Image Extract_ begins by collecting all the nodes on the page that are paths:

```Java
var pathNodes = page.getContent().findChildrenOfType(eDOMNodeType.eDOMPathNode, true);
```

It then examines each path node by getting its fill (with `path.getFill()`). If this is successful then the type of brush is determined by casting to an `IDOMImageBrush` and `IDOMMaskedBrush`:

```Java
IDOMImageBrush imageBrush = IDOMImageBrush.fromRCObject(brush.toRCObject());
IDOMMaskedBrush maskedBrush = IDOMMaskedBrush.fromRCObject(brush.toRCObject());
```

The result of the cast will be non-null if the cast succeeded. From here the image is retrieved with `getImageSource()`, with an additional step needed for a masked image. The image is then encoded for the requested image type and written to disk, in the case below to PNG:

```Java
IDOMPNGImage.encode(mako, image, IOutputStream.createToFile(mako.getFactory(), outputFilePathStr));
```

## Useful sample code

* Finding objects of a given type
* Image brushes and how they are used in Mako
