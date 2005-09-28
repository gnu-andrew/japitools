///////////////////////////////////////////////////////////////////////////////
// Japize - Output a machine-readable description of a Java API.
// Copyright (C) 2000,2002,2003,2004  Stuart Ballard <stuart.a.ballard@gmail.com>
// 
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

package net.wuffies.japi;

import java.lang.reflect.Modifier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.io.Writer;
import java.util.Iterator;
import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.BufferedOutputStream;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPOutputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Process a Java API and emit a machine-readable description of the API,
 * suitable for comparison to other APIs. Specifically, the perl script
 * japicompat.pl can test APIs for source/binary compatibility with each other.
 *
 * @author Stuart Ballard &lt;<a href="mailto:stuart.a.ballard@gmail.com">stuart.a.ballard@gmail.com</a>&gt;
 */
public class Japize {

  /**
   * The path to scan for classes in.
   */
  private static List path = new ArrayList();

  /**
   * The package roots to scan in.
   */
  private static SortedSet roots = new TreeSet();

  /**
   * The packages to exclude.
   */
  private static SortedSet exclusions = new TreeSet();

  /**
   * The output writer to write results to.
   */
  private static PrintWriter out;

  /* Disambiguation rules */
  private static final int UNSPECIFIED = 0;
  private static final int EXPLICITLY = 1;
  private static final int APIS = 2;
  private static final int BYNAME = 3;
  private static final int PACKAGES = 4;
  private static final int CLASSES = 5;

  /**
   * Parse the program's arguments and perform the main processing.
   */
  public static void main(String[] args)
      throws NoSuchMethodException, IllegalAccessException, IOException, ClassNotFoundException {

    // Scan the arguments until the end of keywords is reached, interpreting
    // all the intermediate arguments and dealing with them as appropriate.
    int i = 0;
    boolean zipIt = true;
    String fileName = null;

    if (i < args.length && "unzip".equals(args[i])) {
      zipIt = false;
      i++;
    }
    if (i < args.length && "as".equals(args[i])) {
      fileName = args[++i];
      i++;
    }

    // The next word indicates the method used to decide whether an ambiguous
    // argument of the form a.b.c is a class or a package. Any other word is an
    // error, but checks further down will catch that.
    int disambig = UNSPECIFIED;
    if (i < args.length) {
      if ("explicitly".equals(args[i])) {
        disambig = EXPLICITLY;
      } else if ("apis".equals(args[i])) {
        disambig = APIS;
      } else if ("byname".equals(args[i])) {
        disambig = BYNAME;
      } else if ("packages".equals(args[i])) {
        disambig = PACKAGES;
      } else if ("classes".equals(args[i])) {
        disambig = CLASSES;
      }
      i++;
    }

    // Correct syntax requires that one of the previous cases must have matched,
    // and also that there be at least one more word. Both these cases, however,
    // can be errored below because they will result in both the path and the
    // set of roots being empty.
    if (i < args.length && disambig != UNSPECIFIED) {

      // Identify each argument that's prefixed with + or - and put it in
      // either the "roots" or the "exclusions" TreeSet as appropriate. Use
      // the disambiguation method specified above for arguments that do not
      // explicitly indicate if they are classes or packages.
      for (; i < args.length; i++) {
        char first = args[i].charAt(0);
        String pkgpath = args[i].substring(1);
        if (first == '+' || first == '-') {
          SortedSet setToAddTo = first == '+' ? roots : exclusions;

          // First identify *whether* it's ambiguous - and whether it's legal.
          int commapos = pkgpath.indexOf(',');
          
          // If it contains a comma, and doesn't have a dot or a comma after
          // that, then it's unambiguous.
          if (commapos >= 0) {
            if (pkgpath.indexOf(',', commapos + 1) >= 0 ||
                pkgpath.indexOf('.', commapos + 1) >= 0) {
              System.err.println("Illegal package/class name " + pkgpath +
                                 " - skipping");
            } else {
              setToAddTo.add(pkgpath);
            }

          // Otherwise it's ambiguous. Figure out what to do based on the
          // disambiguation rule set above.
          } else {
            switch (disambig) {
              case EXPLICITLY:
                System.err.println("Ambiguous package/class name " + pkgpath +
                                   " not allowed with 'explicitly' - skipping");
                break;
              case APIS:
                setToAddTo.add(toClassRoot(pkgpath));
                setToAddTo.add(pkgpath + ",");
                break;
              case BYNAME:
                int dotpos = pkgpath.lastIndexOf('.');
                if (Character.isUpperCase(pkgpath.charAt(dotpos + 1))) {
                  setToAddTo.add(toClassRoot(pkgpath));
                } else {
                  setToAddTo.add(pkgpath + ",");
                }
                break;
              case PACKAGES:
                setToAddTo.add(pkgpath + ",");
                break;
              case CLASSES:
                setToAddTo.add(toClassRoot(pkgpath));
                break;
            }
          }

        // If it doesn't start with + or -, it's a path component.
        } else {
          path.add(args[i]);
        }
      }
    }
    if (path.isEmpty() || roots.isEmpty()) printUsage();

    // We need to initialize the classpath to find classes in the correct
    // location.
    StringBuffer cp = new StringBuffer();
    for (Iterator j = path.iterator(); j.hasNext(); ) {
      if (cp.length() > 0) cp.append(File.pathSeparatorChar);
      cp.append(j.next());
    }
    setClasspath(cp.toString());

    // Figure out what output writer to use.
    if (fileName == null) {
      if (zipIt) {
        System.err.println("Note: for correct operation of tools that read japi files, it is strongly");
        System.err.println("recommended to use a filename ending in japi.gz for a compressed japi file.");
        out = new PrintWriter(new GZIPOutputStream(System.out));
      } else {
        System.err.println("Note: for correct operation of tools that read japi files, it is strongly");
        System.err.println("recommended to use a filename ending in japi for an uncompressed japi file.");
        out = new PrintWriter(System.out);
      }
    } else {

      // Japize will only create output to files ending in .japi (uncompressed)
      // or .japi.gz (compressed). It enforces this rule by adding .japi and .gz
      // to the specified filename if it doesn't already have them. If the user
      // specifies a .gz extension for uncompressed output, this is flagged as
      // an error - if it's really what they meant, they can specify x.gz.japi.
      if (fileName.endsWith(".gz")) {
        if (!zipIt) {
          System.err.println("Filename ending in .gz specified without zip output enabled.");
          System.err.println("Please either omit 'unzip' or specify a different filename (did you");
          System.err.println("mean '" + fileName + ".japi'?)");
          System.exit(1);
        }

        // Trim ".gz" off the end. It'll be re-added later, but ".japi" might
        // be inserted first.
        fileName = fileName.substring(0, fileName.length() - 3);
      }

      // Add ".japi" if it's not already there.
      if (!fileName.endsWith(".japi")) fileName += ".japi";

      // Produce an output writer - compressed or not, as appropriate.
      if (zipIt) {
        out = new PrintWriter(new GZIPOutputStream(new BufferedOutputStream(
              new FileOutputStream(fileName + ".gz"))));
      } else {
        out = new PrintWriter(new BufferedWriter(new FileWriter(fileName)));
      }
    }

    // Now actually go and japize the classes.
    doJapize();
    out.close();
  }

  private static String toClassRoot(String pkgpath) {
    StringBuffer sb = new StringBuffer(pkgpath);
    int dotpos = pkgpath.lastIndexOf('.');
    if (dotpos >= 0) {
      sb.setCharAt(dotpos, ',');
    } else {
      sb.insert(0, ',');
    }
    return sb.toString();
  }

  private static void progress(char ch) {
    System.err.print(ch);
    System.err.flush();
  }
  private static void progress(String str) {
    System.err.println();
    System.err.print(str);
    System.err.flush();
  }
  // See design/japi-spec.txt for why this ends in a comma rather than the usual period.
  private static final String J_LANG = "java.lang,";
  private static final String J_L_OBJECT = J_LANG + "Object";
  
  private static ClassWrapper jlObjectWrapper;
  private static HashSet objCalls = new HashSet();

  private static void doJapize()
      throws NoSuchMethodException, IllegalAccessException, IOException,
             ClassNotFoundException {

    // Print the header identifier. The syntax is "%%japi ver anything".
    // The "anything" is currently used for name/value pairs indicating the
    // creation date and creation tool.
    out.println("%%japi 0.9.7 creator=japize date=" +
        new SimpleDateFormat("yyyy/MM/dd_hh:mm:ss_z").format(new Date()));

    // Identify whether java.lang,Object fits into our list of things to
    // process. If it does, process it first, then add it to the list of
    // things to avoid (and remove it from the list of roots if it appears
    // there).
    if (checkIncluded(J_L_OBJECT)) {
      processClass(J_L_OBJECT);
      if (roots.contains(J_L_OBJECT)) roots.remove(J_L_OBJECT);
      exclusions.add(J_L_OBJECT);
    }

    // Then do the same thing with java.lang as a whole.
    SortedSet langRoots = roots.subSet(J_LANG, J_LANG + '/');
    if (checkIncluded(J_LANG)) {
      processPackage(J_LANG);
      exclusions.add(J_LANG);

    // Even if java.lang isn't included, java.lang.something might be...
    } else {
      processRootSet(langRoots);
    }
    
    // Remove all roots that are subpackages of java.lang.
    for (Iterator i = new TreeSet(langRoots).iterator(); i.hasNext(); ) {
      roots.remove(i.next());
    }
    // Note that the following code would be more efficient but requires
    // a fully-functional subSet() implementation. Doing it this way allows
    // for the possibility of a hacky partial subSet() implementation, which
    // could allow japize to run on Kaffe or Classpath. When they both get
    // full subSet implementations, this should be changed to the more
    // efficient code).
//  for (Iterator i = langRoots.iterator(); i.hasNext(); ) {
//    i.next(); i.remove();
//  }

    jlObjectWrapper = getClassWrapper(J_L_OBJECT.replace(',', '.'));
    CallWrapper[] calls = jlObjectWrapper.getCalls();
    for (int i = 0; i < calls.length; i++) {
      if (!"".equals(calls[i].getName())) objCalls.add(getObjComparableString(calls[i]));
    }

    // Now process all the roots that are left.
    processRootSet(roots);
    progress("");
  }

  private static void processRootSet(SortedSet rootSet) 
      throws NoSuchMethodException, IllegalAccessException,
             ClassNotFoundException, IOException {

    // Process all roots in alphabetical order (note that the ordering is
    // implied by the use of a SortedSet).
    String skipping = null;
    for (Iterator i = rootSet.iterator(); i.hasNext(); ) {
      String root = (String) i.next();
      if (skipping != null) {
        if (root.compareTo(skipping) < 0) continue;
        skipping = null;
      }
      if (root.indexOf(',') < root.length() - 1) {
        processClass(root);
      } else {
        processPackage(root);
        skipping = root.substring(0, root.length() - 1) + "/";
      }
    }
  }
//- Sort all "plus" items - these will be our "roots".
//- Identify whether java.lang.Object falls within the scope of things to process.
  //If it does, process it as a class root and then add "-java.lang,Object" to
  //the list of exclusions.
//- Iteratively process each root in order. After processing each root, skip any
  //following roots that lie between "root" and "root/". Since slash sorts after
  //comma and period but before alphanumerics, this will exclude any subpackages
  //and classes but not anything like a.b.CD.

//- "Process" for a package root is a recursive function defined as follows:
  //- Scan all zips and directories for (a) classes in this package directly, and
    //(b) immediate subpackages of this package. Store everything that is found.
  //- Sort and then iterate over the items found in (a):
    //- Skip the class if there is an exclusion ("-" form) for this class
      //specified on the commandline.
    //- Otherwise Japize the class.
  //- Sort and then iterate over the items found in (b):
    //- If there is an exclusion for this subpackage found on the commandline,
      //skip it, but also do the following:
      //- Using SortedSet.subSet(), identify if there are any global roots that
        //lie between "excludedpkg" and "excludedpkg/". If there are, process
        //those in order using the appropriate process method for the type.
    //- If the package is not excluded, process it recursively using this
      //process method.

  // Process an individual class, by Japizing it.
  // FIXME: We should scan all zips and directories for this class, and only
  // Japize it if it's found. Optimization: on the first scan through, compare
  // all classes to all class roots and remove class roots that aren't found.
  // Also set a flag to indicate that this has been done - then you can skip
  // the scan for all subsequent class roots.
  static void processClass(String cls)
      throws NoSuchMethodException, IllegalAccessException,
             ClassNotFoundException {
    progress("Processing class " + cls + ":");
    japizeClass(cls);
  }
  static void processPackage(String pkg)
      throws NoSuchMethodException, IllegalAccessException,
             ClassNotFoundException, IOException {
    progress("Processing package " + pkg + ":");
    SortedSet classes = new TreeSet();
    SortedSet subpkgs = new TreeSet();

    // Scan the paths for classes and subpackages. Store everything in
    // classes and subpkgs.
    for (Iterator i = path.iterator(); i.hasNext(); ) {
      String pathElem = (String) i.next();
      scanForPackage(pathElem, pkg, classes, subpkgs);
    }

    // Iterate over the classes found, and Japize each in turn, unless they are
    // explicitly excluded.
    for (Iterator i = classes.iterator(); i.hasNext(); ) {
      String cls = (String) i.next();
      if (!exclusions.contains(cls)) japizeClass(cls);
    }

    // Iterate over the packages found, and process each in turn, unless they
    // are explicitly excluded. If they *are* explicitly excluded, check for and
    // process any roots that lie within the excluded package.
    for (Iterator i = subpkgs.iterator(); i.hasNext(); ) {
      String subpkg = (String) i.next();
      if (!exclusions.contains(subpkg)) {
        processPackage(subpkg);
      } else {
        // Identify any roots that lie within the excluded package and process
        // them. The '/' character sorts after '.' and ',', but before any
        // alphanumerics, so it covers a.b.c.d and a.b.c,D but not a.b.cd.
        processRootSet(roots.subSet(subpkg, subpkg + '/'));
      }
    }
  }
  static void scanForPackage(String pathElem, String pkg, SortedSet classes,
                             SortedSet subpkgs) throws IOException {
    if (new File(pathElem).isDirectory()) {
      scanDirForPackage(pathElem, pkg, classes, subpkgs);
    } else {
      scanZipForPackage(pathElem, pkg, classes, subpkgs);
    }
    progress('=');
  }

  /**
   * Process a directory as entered on the command line (ie, a root of the
   * class hierarchy - the same thing that would appear in a Classpath).
   *
   * @param pathElem The name of the directory to process.
   * @param pkg The package to scan for.
   * @param classes A set to add classes found to.
   * @param subpkgs A set to add subpackages found to.
   */
  static void scanDirForPackage(String pathElem, String pkg, SortedSet classes,
                             SortedSet subpkgs) throws IOException {

    // Replace dot by slash and remove the trailing comma. It's the caller's
    // responsibility to ensure that the last character is a comma.
    pkg = pkg.substring(0, pkg.length() - 1);
    String pkgf = pkg.replace('.', '/');

    // If there is a directory of the appropriate name, recurse over it.
    File dir = new File(pathElem, pkgf);

    // Iterate over the files and directories within this directory.
    String[] entries = dir.list();
    for (int i = 0; i < entries.length; i++) {
      File f2 = new File(dir, entries[i]);

      // If the entry is another directory, add the package associated with
      // it to the set of subpackages.
      // "-" entry for it.
      if (f2.isDirectory()) {
        subpkgs.add(pkg + '.' + entries[i] + ',');

      // If the entry is a file ending with ".class", add the class name to
      // the set of classes.
      } else if (entries[i].endsWith(".class")) {
        classes.add(pkg + ',' +
            entries[i].substring(0, entries[i].length() - 6));
      }
    }
  }

  /**
   * Process a zipfile as entered on the command line (ie, a root of the
   * class hierarchy - the same thing that would appear in a Classpath).
   *
   * @param pathElem The name of the zipfile to process.
   * @param pkg The package to scan for.
   * @param classes A set to add classes found to.
   * @param subpkgs A set to add subpackages found to.
   */
  static void scanZipForPackage(String pathElem, String pkg, SortedSet classes,
                             SortedSet subpkgs) throws IOException {

    // Replace dot by slash and remove the trailing comma. It's the caller's
    // responsibility to ensure that the last character is a comma.
    pkg = pkg.substring(0, pkg.length() - 1);
    String pkgf = pkg.replace('.', '/') + '/';

    // Iterate over all the entries in the zipfile.
    ZipFile z = new ZipFile(pathElem);
    Enumeration ents = z.entries();
    while (ents.hasMoreElements()) {
      String ze = ((ZipEntry)ents.nextElement()).getName();
  
      // If the entry is a class file and located in the package we are looking
      // for, process it.
      if (ze.startsWith(pkgf) && ze.endsWith(".class")) {

        // Trim off the package bit that we already know and the .class suffix.
        ze = ze.substring(pkgf.length(), ze.length() - 6);

        // If it's directly in the package we're processing, add it to classes.
        // If it's in a subpackage, add the top-level subpackage to subpkgs.
        if (ze.indexOf('/') >= 0) {
          subpkgs.add(pkg + '.' + ze.substring(0, ze.indexOf('/')) + ',');
        } else {
          classes.add(pkg + ',' + ze);
        }
      }
    }
  }

  /**
   * Print a usage message.
   */
  private static void printUsage() {
    System.err.println("Usage: japize [unzip] [as <name>] apis <zipfile>|<dir> ... +|-<pkg> ...");
    System.err.println("At least one +pkg is required. 'name' will have .japi and/or .gz");
    System.err.println("appended if appropriate.");
    System.err.println("The word 'apis' can be replaced by 'explicitly', 'byname', 'packages' or");
    System.err.println("'classes'. These values indicate whether something of the form a.b.C should");
    System.err.println("be treated as a class or a package. Use 'a.b,C' or 'a.b.c,' to be explicit.");
    System.exit(1);
  }

  /**
   * Construct a String consisting of every super-interface of a class
   * separated by "*".
   *
   * @param c The class to process.
   * @param s Initially "" should be passed; during recursion the string
   * produced so far is passed. This is used to ensure the same interface
   * does not appear twice in the string.
   * @return The name of every super-interface of c, separated by "*" and
   * with a leading "*".
   */
  public static String mkIfaceString(ClassWrapper c, String s) {
    return mkIfaceString(c, s, null, c);
  }
  /**
   * Construct a String consisting of every super-interface of a class
   * separated by "*".
   *
   * @param c The class to process.
   * @param s Initially "" should be passed; during recursion the string
   * produced so far is passed. This is used to ensure the same interface
   * does not appear twice in the string.
   * @param ctype If non-null, all interfaces will first be bound against
   * this type before being displayed.
   * @param wrapper The wrapper to verify type parameters against.
   * @return The name of every super-interface of c, separated by "*" and
   * with a leading "*".
   */
  public static String mkIfaceString(ClassWrapper c, String s, ClassType ctype, GenericWrapper wrapper) {

    // First iterate over the class's direct superinterfaces.
    ClassType[] ifaces = c.getInterfaces();
    for (int i = 0; i < ifaces.length; i++) {

      // Bind the interface against ctype, if supplied.
      ClassType iface = ifaces[i];
      if (ctype != null) iface = (ClassType) iface.bind(ctype);

      // If the string does not already contain the interface, and the
      // interface is public/protected, then add it to the string and
      // also process *its* superinterfaces, recursively.
      String repr = iface.getJavaRepr(wrapper);
      if ((s + "*").indexOf("*" + repr + "*") < 0) {
        int mods = iface.getWrapper().getModifiers();
        if (Modifier.isPublic(mods) || Modifier.isProtected(mods)) {
          s += "*" + repr;
        }
        s = mkIfaceString(iface.getWrapper(), s, iface, wrapper);
      }
    }

    // Finally, recursively process the class's superclass, if it has one.
    ClassType sup = c.getSuperclass();
    if (sup != null) {
      if (ctype != null) sup = (ClassType) sup.bind(ctype);
      s = mkIfaceString(sup.getWrapper(), s, sup, wrapper);
    }
    return s;
  }

  /**
   * Write out API information for a given class. Nothing will be written if
   * the class is not public/protected.
   *
   * @param n The name of the class to process.
   * @return true if the class was public/protected, false if not.
   */
  public static boolean japizeClass(String n)
      throws NoSuchMethodException, IllegalAccessException {
    try {

      // De-mangle the class name.
      if (n.charAt(0) == ',') n = n.substring(1);
      n = n.replace(',', '.');

      // Get a ClassWrapper to work on.
      ClassWrapper c = getClassWrapper(n);

      // Load the class and check its accessibility.
      int mods = c.getModifiers();
      if (!Modifier.isPublic(mods) && !Modifier.isProtected(mods)) {
        progress('-');
        return false;
      }

      // Construct the basic strings that will be used in the output.
      String entry = toClassRoot(c.getName()) + "!";
      String classEntry = entry;
      String type;
      if (c.isEnum()) {
        type = "enum";
      } else if (c.isAnnotation()) {
        type = "annotation";
      } else if (c.isInterface()) {
        type = "interface";
      } else {
        type = "class";
      }

      type += getTypeParamStr(c);
      
      if (c.isInterface()) {
        mods |= Modifier.ABSTRACT; // Interfaces are abstract by definition,

      } else {

        // Classes that happen to be Serializable get their SerialVersionUID
        // output as well. The separation by the '#' character from the rest
        // of the type string has mnemonic value for Brits, as the SVUID is a
        // special sort of 'hash' of the class.
        if (c.isSerializable()) {
          type += "#" + c.getSerialVersionUID();
        }
      }

      // Iterate over the class's superclasses adding them to its "type" name,
      // skipping any superclasses that are not public/protected.
      int smods = mods;
      ClassType supt = c.getSuperclass();
      while (supt != null) {
        ClassWrapper sup = supt.getWrapper();
        smods = sup.getModifiers();
        if (!Modifier.isPublic(smods) && !Modifier.isProtected(smods)) {
          progress('^');
        } else {
          type += ":" + supt.getJavaRepr(c);
        }
        if (sup.getSuperclass() == null) {
          supt = null;
        } else {
          supt = (ClassType) sup.getSuperclass().bind(supt);
        }
      }
      type += mkIfaceString(c, "");

      // Print out the japi entry for the class itself.
      printEntry(entry, type, mods, c.isDeprecated(), false);

      // Get the class's members.
      Map fieldMap = new HashMap();
      Map callMap = new HashMap();
      getFieldsAndCalls(c, null, fieldMap, callMap);
      BoundField[] fields = new BoundField[fieldMap.size()];
      fieldMap.values().toArray(fields);
      Arrays.sort(fields);
      BoundCall[] calls = new BoundCall[callMap.size()];
      callMap.values().toArray(calls);
      Arrays.sort(calls);

      // Iterate over the fields in the class.
      for (int i = 0; i < fields.length; i++) {

        // Fields that are declared in a non-public superclass are not accessible.
        // Skip them.
        int dmods = fields[i].getDeclaringClass().getModifiers();
        if (!Modifier.isPublic(dmods) && !Modifier.isProtected(dmods)) {
          progress('>');
          continue;
        }

        // Get the modifiers and type of the field.
        mods = fields[i].getModifiers();

        // Fields of interfaces are *always* public, static and final, although
        // wrapper implementations are inconsistent about telling us this.
        if (fields[i].getDeclaringClass().isInterface()) {
          mods |= Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC;
        }
        type = fields[i].getType().getTypeSig(c);

        if (!fields[i].getDeclaringClass().getName().equals(c.getName())) {
          type += "=" + fields[i].getDeclaringClass().getName();
        }

        // A static, final field is a primitive constant if it is initialized to
        // a compile-time constant.
        if (fields[i].isPrimitiveConstant()) {
          Object o = fields[i].getPrimitiveValue();

          // Character values get int-ized to keep the output nice and 7bit.
          if (o instanceof Character) {
            type += ":" + (int)((Character)o).charValue();
          
          // String values get newlines and backslashes escaped to stop them from
          // going onto a second line.
          } else if (o instanceof String) {
            String val = (String)o;
            StringBuffer sb = new StringBuffer('\"');
            int p = 0, q = 0;
            while (q >= 0) {
              q = val.indexOf("\n", p);
              int r = val.indexOf("\\", p);
              if (r >= 0 && (r < q || q < 0)) q = r;
              if (q >= 0) {
                sb.append(val.substring(p, q));
                sb.append('\\');
                if (val.charAt(q) == '\\') sb.append('\\'); else sb.append('n');
                p = ++q;
              }
            }
            sb.append(val.substring(p));
            type += ":" + sb;

          // Floats and doubles get their toRaw*Bits() value printed as well as
          // their actual value.
          } else if (o instanceof Float) {
            type += ':' + o.toString() + '/' +
                Integer.toHexString(Float.floatToRawIntBits(
                                    ((Float) o).floatValue()));
          } else if (o instanceof Double) {
            type += ':' + o.toString() + '/' +
                Long.toHexString(Double.doubleToRawLongBits(
                                 ((Double) o).doubleValue()));

          // Other types just get output.
          } else {
            type += ":" + o;
          }
        }

        // Output the japi entry for the field.
        printEntry(classEntry + "#" + fields[i].getName(), type, mods,
                   fields[i].isDeprecated(), fields[i].isEnumField());
      }

      // Iterate over the methods and constructors in the class.
      for (int i = 0; i < calls.length; i++) {

        // Methods that are declared in a non-public superclass are not
        // publically accessible. Skip them.
        int dmods = calls[i].getDeclaringClass().getModifiers();
        if (!Modifier.isPublic(dmods) && !Modifier.isProtected(dmods)) {
          progress('}');
          continue;
        }

        // Skip calls called <init> and <clinit>. Constructors are handled
        // with an empty method name, and class initializers are never part of
        // the public API.
        if ("<init>".equals(calls[i].getName()) ||
            "<clinit>".equals(calls[i].getName())) {
          continue;
        }

        // Skip methods in interfaces that are also defined identically in
        // Object. Specifically, it needs to be defined in Object with
        // *exactly* the same parameter types, return types *and* thrown
        // exceptions (because it *is* legal for an interface to specify,
        // say, "Object clone();" and thereby specify that implementors must
        // not throw CloneNotSupportedException from their clone method.
        // Surprisingly, Cloneable doesn't do this...)
        if (c.isInterface()) {
          if (objCalls.contains(getObjComparableString(calls[i]))) {
            progress(';');
            continue;
          }
        }

        // Construct the name of the method, of the form Class!method(params).
        entry = classEntry + calls[i].getName() + "(";
        Type[] params = calls[i].getParameterTypes();
        String comma = "";
        for (int j = 0; j < params.length; j++) {
          entry += comma + params[j].getTypeSig(calls[i]);
          comma = ",";
        }
        entry += ")";
        if (calls[i].getExclude14()) {
          entry += "+";
        } else if (calls[i].getExclude15()) {
          entry += "-";
        }

        // Construct the "type" field, of the form returnType*exception*except2...
        type = "";

        // ... but if it's a generic method it gets the type parameters first
        type += getTypeParamStr(calls[i]);

        Type rtnType = calls[i].getReturnType();
        type += (rtnType == null) ? "constructor" : rtnType.getTypeSig(calls[i]);
        NonArrayRefType[] excps = calls[i].getExceptionTypes();
        for (int j = 0; j < excps.length; j++) {
          if (includeException(excps, j)) type += "*" + excps[j].getJavaRepr(calls[i]);
        }

        // Get the modifiers for this method. Methods of interfaces are
        // by definition public and abstract, although wrapper implementations
        // are inconsistent about telling us this.
        int mmods = calls[i].getModifiers();
        if (c.isInterface()) {
          mmods |= Modifier.ABSTRACT | Modifier.PUBLIC;
        }

        // Methods of final classes are by definition final
        if (Modifier.isFinal(c.getModifiers())) {
          mmods |= Modifier.FINAL;
        }

        // Constructors are never final. The verifier should enforce this
        // so this should always be a no-op, except for when the line above
        // set it.
        if ("".equals(calls[i].getName())) {
          mmods &= ~Modifier.FINAL;
        }

        // Print the japi entry for the method.
        printEntry(entry, type, mmods, calls[i].isDeprecated(), false);
      }

      // Return true because we did parse this class.
      progress('+');
      return true;
    } catch (NoClassDefFoundError e) {
      System.err.println("\nFailed to Japize " + n + ": " + e);
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("\nFailed to Japize " + n + ": " + e);
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      System.err.println("\nFailed to Japize " + n + ": " + e);
      e.printStackTrace();
    } catch (IndexOutOfBoundsException e) {
      System.err.println("\nFailed to Japize " + n + ": " + e);
      e.printStackTrace();
    } catch (RuntimeException e) {
      System.err.println("\nFailed to Japize " + n + ": " + e);
      e.printStackTrace();
    }
    return false;
  }

  private static String getTypeParamStr(GenericWrapper wrapper) {
    TypeParam[] tparams = wrapper.getTypeParams();
    String type = "";
    if (tparams != null) {
      type += "<";
      for (int i = 0; i < tparams.length; i++) {
        if (i > 0) type += ",";
        for (int j = 0; j < tparams[i].getBounds().length; j++) {
          if (j > 0) type += "&";
          type += tparams[i].getBounds()[j].getTypeSig(wrapper);
        }
      }
      type += ">";
    }
    return type;
  }

  /**
   * Load all the fields and calls for a particular class, taking inheritance into account.
   * fieldMap and callMap will be maps from string to BoundField and BoundCall respectively.
   * You can ignore the strings and just sort the values afterwards.
   */
   // Plan to handle overriding:
   // * BoundCall defines exclude15 and exclude14 fields and corresponding getters. Both default to false.
   // * It's an exception to end up with both true :)
   // * If bind() is called on an item with exclude15=true it returns this.
   // * If bind() is called on an item with exclude14=true, exclude14 is true in the result.
   // * The bind() method checks to see whether the nonGenericSig of its return value is different than its
   //   own. If it is, its return value gets created with exclude14=true.
   // * There's a new bind14() method which returns an exact clone of the BoundCall but with exclude15 set
   //   and all generic information dropped - all type params replaced with their bounds etc. This probably
   //   needs a new getNonGenericType() method on Type, which probably ought to be used in bindWithFallback().
   //   BUT if bind14 is called on something that's exclude14 already, it returns null.
   // * In the loop marked HERE below, where we're going through and binding all the calls, we look to see
   //   whether the newly-bound method has exclude14 set. If it is, we create a new entry for the newly-bound
   //   method (with the new nonGenericSig) and update the existing one to the result of bind14() on the
   //   original (or drop the entry entirely if bind14() gives null).
   // * When outputting, we output a "-" after anything with exclude15 and a "+" after anything with
   //   exclude14.
   // - Process bridge methods but create them with exclude15 right off the bat? Seems
   //   reasonable. That means creating the BoundCall and instantly bind14()ing it.
   // NOTE: This algorithm does not handle methods that differ only in return value but are all present.
   // BUT it seems likely that an algorithm like this will work for that situation. The trick is that
   // when there *is* such a "confusion", we want getNonGenericTypeSig to include the return value for
   // correct behavior. But for other cases we don't. Do we? Perhaps the existence of bridge methods to
   // "mask out" the subclass versions as exclude15 might be enough here. But it is relying on the
   // compiler to get that right...
   // Perhaps getNonGenericSig on something that's exclude15 includes the return type? Not sure, at all.
  private static void getFieldsAndCalls(ClassWrapper outer, ClassType ctype, Map fieldMap, Map callMap) {
    ClassWrapper c = (ctype == null) ? outer : ctype.getWrapper();

    ClassType[] ifaces = c.getInterfaces();
    for (int i = 0; i < ifaces.length; i++) {
      ClassType iface = ifaces[i];
      if (ctype != null) iface = (ClassType) iface.bind(ctype);
      getFieldsAndCalls(outer, iface, fieldMap, callMap);
    }
    ClassType sup = c.getSuperclass();
    if (sup != null) {
      if (ctype != null) sup = (ClassType) sup.bind(ctype);
      getFieldsAndCalls(outer, sup, fieldMap, callMap);
    }
    FieldWrapper[] fields = c.getFields();
    for (int i = 0; i < fields.length; i++) {
      fieldMap.put(fields[i].getName(), new BoundField(fields[i]));
    }
    CallWrapper[] calls = c.getCalls();
    for (int i = 0; i < calls.length; i++) {
      // JDK15: skip bridge methods (the ACC_VOLATILE bit corresponds to the ACC_BRIDGE bit)
      if (Modifier.isVolatile(calls[i].getModifiers())) continue;

      BoundCall call = new BoundCall(calls[i], outer);
      if (ctype == null || call.isInheritable()) {
        callMap.put(call.getNonGenericSig(), call);
      }
    }
    if (ctype != null) {
      for (Iterator i = fieldMap.entrySet().iterator(); i.hasNext(); ) {
        Map.Entry ent = (Map.Entry) i.next();
        ent.setValue(((BoundField) ent.getValue()).bind(ctype));
      }
      for (Iterator i = new ArrayList(callMap.entrySet()).iterator(); i.hasNext(); ) {
        Map.Entry ent = (Map.Entry) i.next();
        String nonGenSig = (String) ent.getKey();
        BoundCall call = (BoundCall) ent.getValue();
        if (!call.getNonGenericSig().equals(nonGenSig)) throw new RuntimeException("unmatched sigs: " + nonGenSig + ", " + call.getNonGenericSig());
        BoundCall boundCall = call.bind(ctype);
        if (boundCall.getExclude14() && !call.getExclude14()) {
          callMap.put(nonGenSig, call.bind14());
        }
        callMap.put(boundCall.getNonGenericSig(), boundCall);
      }
    }
  }

  /**
   * Get a string containing the name, parameter types and thrown exceptions
   * for a particular method. Returns null on a constructor. Designed to
   * allow comparing interface methods against Object methods. NOTE that this will
   * potentially not work correctly if generic methods are ever added to Object
   * itself (because of "@0" etc meaning different things). Oh, how I hope that
   * never happens...
   */
  private static String getObjComparableString(CallWrapper call) throws ClassNotFoundException {
    if (call.getName().equals("")) return null;
    String s = call.getName() + "(";
    Type[] params = call.getParameterTypes();
    for (int i = 0; i < params.length; i++) {
      if (i > 0) s += ",";
      s += params[i].getTypeSig(call);
    }
    s += ")" + call.getReturnType().getTypeSig(call);
    NonArrayRefType[] excps = call.getExceptionTypes();
    TreeSet exstrs = new TreeSet();
    for (int i = 0; i < excps.length; i++) {
      if (includeException(excps, i)) exstrs.add(excps[i].getJavaRepr(call));
    }
    for (Iterator i = exstrs.iterator(); i.hasNext(); ) {
      s += "*" + i.next();
    }
    return s;
  }


  /**
   * Print a japi file entry. The format of a japi file entry is space-separated
   * with 3 fields - the name of the "thing", the modifiers, and the type
   * (which generally includes more information than *just* the type; see the
   * implementation of japizeClass for what actually gets passed in here).
   * The modifiers are represented as a four-letter string consisting of 1
   * character each for the accessibility ([P]ublic or [p]rotected), the
   * abstractness ([a]bstract or [c]oncrete), the staticness ([s]tatic or
   * [i]nstance) and the finalness ([f]inal or [n]onfinal).
   *
   * @param thing The name of the "thing" (eg class, field, etc) to print.
   * @param type The contents of the "type" field.
   * @param mods The modifiers of the thing, as returned by {Class, Field,
   * Method, Constructor}.getModifiers().
   * @param deprecated Whether the thing is deprecated.
   */
  public static void printEntry(String thing, String type, int mods,
                                boolean deprecated, boolean enumField) {
    if (!Modifier.isPublic(mods) && !Modifier.isProtected(mods)) return;
    if (thing.startsWith("java.lang,Object!")) out.print('+');
    if (thing.startsWith("java.lang,") ||
        thing.startsWith("java.lang.")) out.print('+');
    out.print(thing);
    out.print(' ');
    out.print(Modifier.isPublic(mods) ? 'P' : 'p');
    out.print(Modifier.isAbstract(mods) ? 'a' : 'c');
    out.print(Modifier.isStatic(mods) ? 's' : 'i');
    out.print(enumField ? 'e' : Modifier.isFinal(mods) ? 'f' : 'n');
    out.print(deprecated ? 'd' : 'u');
    out.print(' ');
    out.println(type);
  }


  /**
   * Trivial utility method to get the wrapper for a superclass or null if there
   * isn't one.
   */
  static ClassWrapper getWrapper(ClassType t) {
    return t == null ? null : t.getWrapper();
  }

  /**
   * Check to see if an exception should be included in the list of exceptions.
   * Subclasses of RuntimeException and Error should be omitted, as should
   * subclasses of other exceptions also thrown.
   */
  static boolean includeException(NonArrayRefType[] excps, int index)
      throws ClassNotFoundException {
    boolean isSuper = false;
    ClassType excp;
    if (excps[index] instanceof ClassType) {
      excp = (ClassType) excps[index];
    } else {
      TypeParam tp = (TypeParam) excps[index];
      while (tp.getPrimaryConstraint() instanceof TypeParam) {
        tp = (TypeParam) tp.getPrimaryConstraint();
      }
      excp = (ClassType) tp.getPrimaryConstraint();
    }
    for (ClassWrapper supclass = excp.getWrapper();
         supclass != null;
         supclass = getWrapper(supclass.getSuperclass())) {
      String supname = supclass.getName();
      if ("java.lang.RuntimeException".equals(supname) ||
          "java.lang.Error".equals(supname)) {
        return false;
      }
      if (isSuper) {
        for (int i = 0; i < excps.length; i++) {
          if (i != index && excps[i] instanceof ClassType &&
              supname.equals(((ClassType) excps[i]).getName())) return false;
        }
      }
      isSuper = true;
    }
    return true;
  }
  
  /**
   * Check a class name against the global 'roots' and 'exclusions' sets
   * to see if it should be included. A class should be included if
   * it is inside a package that has a roots entry, and not inside a deeper
   * package that has an exclusions entry.
   *
   * @param cname the name of the class to check.
   * @return true if the class should be included, false if not.
   */
  public static boolean checkIncluded(String cname) {

    if (roots.contains(cname)) return true;
    if (exclusions.contains(cname)) return false;
    
    // Loop backwards over the "."s in the class's name.
    int i = cname.indexOf(',');
    while (i >= 0) {
      cname = cname.substring(0, i);
      String mangled = cname + ',';

      // Check whether there is an entry for the package name up to the ".".
      // If so we know what to do so we return the result; otherwise we
      // continue at the next ".".
      if (roots.contains(mangled)) return true;
      if (exclusions.contains(mangled)) return false;
      i = cname.lastIndexOf('.');
    }

    // If we ran out of dots before finding a match, we need to check the root
    // package.
    return roots.contains(",");
  }

  /**
   * Set the classpath for the appropriate implementation we are using.
   *
   * @param cp The classpath to set.
   */
  public static void setClasspath(String cp) throws IOException {
    ClassFile.setClasspath(cp);
  }
  /**
   * Construct the appropriate type of ClassWrapper object for the processing we
   * are doing. 
   *
   * @param className The fully-qualified name of the class to get a wrapper
   * for.
   * @return A ClassWrapper object for the specified class.
   */
  public static ClassWrapper getClassWrapper(String className) 
      throws  ClassNotFoundException {
    return ClassFile.forName(className);
  }
}
