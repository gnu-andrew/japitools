///////////////////////////////////////////////////////////////////////////////
// Japize - Output a machine-readable description of a Java API.
// Copyright (C) 2000  Stuart Ballard <sballard@wuffies.net>
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
//
// A link to the GNU General Public License is available at the
// japitools homepage, http://stuart.wuffies.net/japi/
///////////////////////////////////////////////////////////////////////////////

package net.wuffies.japi;

import java.lang.reflect.Modifier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.io.ObjectStreamClass;

/**
 * Process a Java API and emit a machine-readable description of the API,
 * suitable for comparison to other APIs. Specifically, the perl script
 * japicompat.pl can test APIs for source/binary compatibility with each other.
 * <p>
 * Recent changes:<br>
 * - 2000/06/25: Start supporting jode.bytecode (it suits my needs better than
 *   gnu.bytecode). Everything works except for serialVersionUIDs. I'm hoping
 *   that if I provide Jochen Hoenicke (the author of jode.bytecode) with an
 *   implementation of ObjectStreamClass.getSerialVersionUID() ported to
 *   jode.bytecode, he will incorporate it.<br>
 * - 2000/06/20: Prepare for supporting gnu.bytecode by abstracting all
 *   reflection calls into "Wrapper" classes. The class ReflectClass provides
 *   a reflection-based Wrapper class that should keep all functionality the
 *   same as it is now. The Wrapper classes are uncommented but mostly pretty
 *   trivial to understand.<br>
 * - 2000/06/16: Add a cool new feature at the suggestion of Edouard G.
 *   Parmelan: Japize now outputs SerialVersionUIDs. It will be interesting
 *   to try to re-implement *that* with gnu.bytecode... Also implemented
 *   translation of chars to their associated ints to avoid the funny char
 *   values that appeared in the output, and String mangling just in case
 *   a primitive constant String contained a newline.<br>
 * - 2000/06/16: Remove the kaffe bug hackaround now that kaffe has been fixed
 *   (2 days from discovery to fix - impressive). Left in the part that prints
 *   "!", which turns out to be unavoidable given the JLS. JDK does it too.<br>
 * - 2000/06/14: Finally got to commenting the class; fixed a bug where a
 *   static final value would not be recognized as a potential constant if it
 *   was protected; added support for knowing the "implicit" modifiers on
 *   interfaces and their members which Reflection should tell us, but
 *   doesn't.<br>
 *
 * @author Stuart Ballard &lt;<a href="mailto:sballard@wuffies.net">sballard@wuffies.net</a>&gt;
 */
public class Japize {

  /**
   * True if the jode.bytecode package is being used, false otherwise.
   */
  private static boolean useJode;

  /**
   * Parse the program's arguments and perform the main processing.
   */
  public static void main(String[] args)
      throws NoSuchMethodException, IllegalAccessException, IOException, ClassNotFoundException {

    // If invoked with no args, give a usage message.
    if (args.length == 0) {
      System.err.println("Usage: Japize class <classname> ... > output.japi");
      System.err.println("   or: Japize [packages | jode] [<zipfile> | <dir> | +<pkg> | -<pkg>] ... > output.japi");

    // If the first arg is "class", iterate over the remaining args and process
    // each one as a class name.
    } else if ("class".equals(args[0])) {
        System.err.println("WARNING: The 'class' option gives wrong information in some situations.\n");
        System.err.println("         Consider using the 'jode' option with a zipfile or directory instead.\n");
      for (int i = 1; i < args.length; i++) {
        japizeClass(getClassWrapper(args[i]));
      }

    // If the first arg is "packages", iterate over the arguments two times.
    } else if ("packages".equals(args[0]) || "jode".equals(args[0])) {

      useJode = "jode".equals(args[0]);

      // The first iteration identifies each argument that's prefixed with + or
      // - and puts it in a Hashtable with the value "+" or "-".
      Hashtable pkgs = new Hashtable();
      for (int i = 1; i < args.length; i++) {
        if (args[i].startsWith("-") || args[i].startsWith("+")) {
          pkgs.put(args[i].substring(1), args[i].substring(0, 1));
        }
      }

      // If we are using Jode, we need to initialize Jode's classpath to
      // find classes in the correct location.
      if (useJode) {
        StringBuffer cp = new StringBuffer();
        for (int i = 1; i < args.length; i++) {
          if (!(args[i].startsWith("-") || args[i].startsWith("+"))) {
            if (cp.length() > 0) cp.append(':');
            cp.append(args[i]);
          }
        }
//      cp.append(System.getProperty("java.class.path"));
        JodeClass.setClassPath(cp.toString());
      } else {
        System.err.println("WARNING: The 'packages' option gives wrong information in some situations.\n");
        System.err.println("         Consider using the 'jode' option instead.\n");
      }


      // The second iteration picks up the remaining args and identifies
      // whether they are directories or files, then calls the appropriate
      // method to process them (passing the hashtable as a parameter to let
      // the processing method include the right stuff).
      for (int i = 1; i < args.length; i++) {
        if (!(args[i].startsWith("-") || args[i].startsWith("+"))) {
          if (new File(args[i]).isDirectory()) {
            processDir(args[i], pkgs);
          } else {
            processZip(args[i], pkgs);
          }
        }
      }

    // Bad args get a usage message.
    } else {
      System.err.println("Usage: Japize class <classname> ... > output.japi");
      System.err.println("   or: Japize [packages | jode] [<zipfile> | <dir> | +<pkg> | -<pkg>] ... > output.japi");
    }

    // It seems that doing all of the <clinit>s that happen during asking
    // for compile time constants causes a Toolkit thread to start, so
    // we need this to terminate.
    System.exit(0);
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

    // First iterate over the class's direct superinterfaces.
    ClassWrapper[] ifaces = c.getInterfaces();
    for (int i = 0; i < ifaces.length; i++) {

      // If the string does not already contain the interface, and the
      // interface is public/protected, then add it to the string and
      // also process *its* superinterfaces, recursively.
      if ((s + "*").indexOf("*" + ifaces[i].getName() + "*") < 0) {
        int mods = ifaces[i].getModifiers();
        if (Modifier.isPublic(mods) || Modifier.isProtected(mods)) {
          s += "*" + ifaces[i].getName();
        }
        s = mkIfaceString(ifaces[i], s);
      }
    }

    // Finally, recursively process the class's superclass, if it has one.
    if (c.getSuperclass() != null) {
      s = mkIfaceString(c.getSuperclass(), s);
    }
    return s;
  }

  /**
   * Write out API information for a given class. Nothing will be written if
   * the class is not public/protected.
   *
   * @param c A ClassWrapper of the class to process.
   * @return true if the class was public/protected, false if not.
   */
  public static boolean japizeClass(ClassWrapper c)
      throws NoSuchMethodException, IllegalAccessException, ClassNotFoundException {

    // Load the class and check its accessibility.
    int mods = c.getModifiers();
    if (!Modifier.isPublic(mods) && !Modifier.isProtected(mods)) return false;

    // Construct the basic strings that will be used in the output.
    String entry = c.getName() + "#";
    String type = "class";
    if (c.isInterface()) {
      type = "interface";
      mods |= Modifier.ABSTRACT; // Interfaces are abstract by definition,
                                 // but reflection implementations are
                                 // inconsistent in telling us this.
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
    ClassWrapper sup = c;
    int smods = mods;
    while (sup.getSuperclass() != null) {
      sup = sup.getSuperclass();
      smods = sup.getModifiers();
      if (!Modifier.isPublic(smods) && !Modifier.isProtected(smods)) {
        System.err.print("^");
      } else {
        type += ":" + sup.getName();
      }
    }
    type += mkIfaceString(c, "");

    // Print out the japi entry for the class itself.
    printEntry(entry, type, mods);

    // Get the class's members.
    FieldWrapper[] fields = c.getFields();
    CallWrapper[] calls = c.getCalls();

    // Iterate over the fields in the class.
    for (int i = 0; i < fields.length; i++) {

      // Fields that are declared in a non-public superclass are not accessible.
      // Skip them.
      int dmods = fields[i].getDeclaringClass().getModifiers();
      if (!Modifier.isPublic(dmods) && !Modifier.isProtected(dmods)) {
        System.err.print(">");
        continue;
      }

      // Get the modifiers and type of the field.
      mods = fields[i].getModifiers();

      // Fields of interfaces are *always* public, static and final, although
      // reflection implementations are inconsistent about telling us this.
      if (fields[i].getDeclaringClass().isInterface()) {
        mods |= Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC;
      }
      type = fields[i].getType();

      // A static, final field is a primitive constant if it is initialized to
      // a compile-time constant. We cannot check for being initialized to a
      // compile time constant, so we just get its value and hope...
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
            int r = val.indexOf("\\");
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

        // Other types just get output. This means that we are also testing the
        // ability to turn floats and doubles consistently into Strings.
        } else {
          type += ":" + o;
        }
      }

      // Output the japi entry for the field.
      printEntry(c.getName() + "#" + fields[i].getName(), type, mods);
    }

    // Iterate over the methods and constructors in the class.
    for (int i = 0; i < calls.length; i++) {

      // Methods that are declared in a non-public superclass are not
      // publically accessible. Skip them.
      int dmods = calls[i].getDeclaringClass().getModifiers();
      if (!Modifier.isPublic(dmods) && !Modifier.isProtected(dmods)) {
        System.err.print("}");
        continue;
      }

      // For some reason the JDK returns values for static initializers
      // sometimes. Skip calls called <init> and <clinit>.
      if ("<init>".equals(calls[i].getName()) ||
          "<clinit>".equals(calls[i].getName())) {
        System.err.print("<");
        continue;
      }

      // This makes sure we do not print an identical method twice. It can
      // never happen except for a rare case where an interface inherits the
      // same method from two different superinterfaces (or from a
      // superinterface and java.lang.Object). The 1.2 Collections architecture
      // is a wonderful test-bed for this case :)
      if (calls[i].isDup()) {
        System.err.print("!");
        continue;
      }

      // Construct the name of the method, of the form Class#method(params).
      entry = c.getName() + "#" + calls[i].getName() + "(";
      String[] params = calls[i].getParameterTypes();
      String comma = "";
      for (int j = 0; j < params.length; j++) {
        entry += comma + params[j];
        comma = ",";
      }
      entry += ")";

      // Construct the "type" field, of the form returnType*exception*except2...
      type = calls[i].getReturnType();
      String[] excps = calls[i].getExceptionTypes();
      for (int j = 0; j < excps.length; j++) {
        type += "*" + excps[j];
      }

      // Get the modifiers for this method. Methods of interfaces are
      // by definition public and abstract, although reflection implementations
      // are inconsistent about telling us this.
      int mmods = calls[i].getModifiers();
      if (c.isInterface()) {
        mmods |= Modifier.ABSTRACT | Modifier.PUBLIC;
      }

      // Print the japi entry for the method.
      printEntry(entry, type, mmods);
    }

    // Return true because we did parse this class.
    return true;
  }

  /**
   * Print a japi file entry. The format of a japi file entry is space-separated
   * with 6 fields - the name of the "thing", the accessibility (public or
   * protected), the abstractness (abstract or concrete), the staticness
   * (static or instance), the finalness (final or nonfinal), and the type
   * (which generally includes more information than *just* the type; see the
   * implementation of japizeClass for what actually gets passed in here).
   *
   * @param thing The name of the "thing" (eg class, field, etc) to print.
   * @param type The contents of the "type" field.
   * @param mods The modifiers of the thing, as returned by {Class, Field,
   * Method, Constructor}.getModifiers().
   */
  public static void printEntry(String thing, String type, int mods) {
    if (!Modifier.isPublic(mods) && !Modifier.isProtected(mods)) return;
    System.out.print(thing + " ");
    System.out.print(Modifier.isPublic(mods) ? "public " : "protected ");
    System.out.print(Modifier.isAbstract(mods) ? "abstract " : "concrete ");
    System.out.print(Modifier.isStatic(mods) ? "static " : "instance ");
    System.out.print(Modifier.isFinal(mods) ? "final " : "nonfinal ");
    System.out.println(type);
  }

  /**
   * Recursively process a directory scanning for classes that match the
   * hashtable constructed in main. As an optimization, this method is called
   * only on the specific subdirectories that have been marked "+", because
   * no classes outside these directories are relevant. However it still needs
   * to be aware of "-" entries and exclude them.
   *
   * @param f A File object representing the directory to scan.
   * @param pkgs The hashtable as constructed in main.
   * @param prefix The name of the package represented by this directory; for
   * example, if the root directory being scanned is /usr/local/classes and
   * f represents /usr/local/classes/java/awt, prefix would be "java.awt".
   */
  public static void processPlusDir(File f, Hashtable pkgs, String prefix)
      throws NoSuchMethodException, IllegalAccessException, IOException, ClassNotFoundException {

    // Iterate over the files and directories within this directory.
    String[] entries = f.list();
    for (int i = 0; i < entries.length; i++) {
      File f2 = new File(f, entries[i]);

      // If the entry is another directory, then scan it unless there is a
      // "-" entry for it.
      if (f2.isDirectory()) {
        String prefix2 = prefix + "." + entries[i];
        if (!"-".equals(pkgs.get(prefix2))) {
          processPlusDir(f2, pkgs, prefix2);
        }

      // If the entry is a file ending with ".class", then process that class
      // unless there is a "-" entry for it.
      } else if (entries[i].endsWith(".class")) {
        String className = prefix + "." +
            entries[i].substring(0, entries[i].length() - 6);
        if (!"-".equals(pkgs.get(className))) {
          if (japizeClass(getClassWrapper(className))) {
            System.err.print("*");
          } else {
            System.err.print("-");
          }
        } else {
          System.err.print("_");
        }
      }
    }
    System.err.flush();
  }

  /**
   * Process a directory as entered on the command line (ie, a root of the
   * class hierarchy - the same thing that would appear in a Classpath).
   * The implementation is slightly optimized by only starting recursion at
   * package names with "+" entries in the hashtable, rather than scanning
   * the whole tree and filtering.
   *
   * @param dname The name of the directory to process.
   * @param pkgs The hashtable as created in main.
   */
  public static void processDir(String dname, Hashtable pkgs)
      throws NoSuchMethodException, IllegalAccessException, IOException, ClassNotFoundException {

    // Iterate over the entries in the hashtable.
    Enumeration entries = pkgs.keys();
    System.err.println("Scanning " + dname);
    while (entries.hasMoreElements()) {
      String pkg = (String)entries.nextElement();

      // If the entry is a "+" entry, then process it.
      if ("+".equals(pkgs.get(pkg))) {
        System.err.println("...Processing " + pkg);
        String pkgf = pkg.replace('.', '/');

        // If there is a *file* of the appropriate name, then process that
        // single class.
        if (new File(dname, pkgf + ".class").isFile()) {
          if (japizeClass(getClassWrapper(pkg))) {
            System.err.print("*");
          } else {
            System.err.print("-");
          }
        }

        // If there is a directory of the appropriate name, recurse over it.
        File dir = new File(dname, pkgf);
        if (dir.isDirectory()) {
          processPlusDir(dir, pkgs, pkg);
        }
        System.err.println("\n...Done processing " + pkg);
      }
    }
    System.err.println("Done scanning " + dname);
  }
  
  /**
   * Check a class name against a hashtable like the one constructed in
   * main() to see if it should be included. A class should be included if
   * it is inside a package that has a "+" entry, and not inside a deeper
   * package that has a "-" entry.
   *
   * @param cname the name of the class to check.
   * @param pkgs The hashtable, as constructed in main, of packages to include.
   * @return true if the class should be included, false if not.
   */
  public static boolean chkClass(String cname, Hashtable pkgs) {

    // Loop backwards over the "."s in the class's name.
    int i = cname.length();
    while (i >= 0) {
      cname = cname.substring(0, i);

      // Check whether there is an entry for the package name up to the ".".
      String x = (String)pkgs.get(cname);

      // If so we know what to do so we return the result; otherwise we
      // continue at the next ".".
      if ("+".equals(x)) {
        return true;
      } else if ("-".equals(x)) {
        return false;
      }
      i = cname.lastIndexOf('.');
    }

    // If we ran out of dots before finding a match, we don't want to include
    // the class.
    return false;
  }

  /**
   * Construct the appropriate type of ClassWrapper object for the processing we
   * are doing. Returns a JodeClass if we are using Jode, or a ReflectClass ifi
   * not.
   *
   * @param className The fully-qualified name of the class to get a wrapper
   * for.
   * @return A ClassWrapper object for the specified class.
   */
  public static ClassWrapper getClassWrapper(String className) 
      throws  ClassNotFoundException {
    if (useJode) {
      return new JodeClass(className);
    } else {
      return new ReflectClass(className);
    }
  }

  /**
   * Iterate over a zip file processing all the appropriate classes.
   *
   * @param fname The name of the zip file to scan.
   * @param pkgs the hashtable as created in main.
   */
  public static void processZip(String fname, Hashtable pkgs)
      throws NoSuchMethodException, IllegalAccessException, IOException, ClassNotFoundException {

    // Iterate over all the entries in the zipfile.
    ZipFile z = new ZipFile(fname);
    Enumeration ents = z.entries();
    System.err.println("Scanning " + fname);
    while (ents.hasMoreElements()) {
      String ze = ((ZipEntry)ents.nextElement()).getName();

      // If the entry is a class file, then drop the "class" suffix, replace
      // the "/"s with "."s, and check whether the resulting class name
      // matches the hashtable. If so, process it. Otherwise skip it.
      if (ze.endsWith(".class")) {
        ze = ze.substring(0, ze.length() - 6).replace('/', '.');
        if (chkClass(ze, pkgs)) {
          if (japizeClass(getClassWrapper(ze))) {
            System.err.print("*");
          } else {
            System.err.print("-");
          }
        } else {
          System.err.print("_");
        }
      }
      System.err.flush();
    }
    System.err.println("\nDone scanning " + fname);
  }
}
