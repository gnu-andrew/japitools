///////////////////////////////////////////////////////////////////////////////
// JapiSerialize - Output a machine-readable serialization of Java objects
// Copyright (C) 2002 C. Brian Jones <cbj@gnu.org>
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

import gnu.getopt.Getopt;
import JSX.ObjOut;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.LineNumberReader;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.ObjectOutputStream;
import java.io.NotSerializableException;

/**
 *
 * @author C. Brian Jones &lt;<a href="mailto:cbj@gnu.org">cbj@gnu.org</a>&gt;
 */
public class JapiSerialize
{
  private static String _outputDirectory = null;
  private static boolean _xml = false;
  private static final String VERSION = "1.0";
  private static final String XML_TYPE = ".xml";
  private static final String BINARY_TYPE = ".ser";
  
  public static void main (String[] argv)
  {
    Getopt g = new Getopt ("JapiSerialize", argv, "d:x");

    int c;
    String arg;
    while ((c = g.getopt ()) != -1)
    {
      switch (c)
      {
        case 'd':
          _outputDirectory = g.getOptarg ();
          break;
        case 'x':
          _xml = true;
          break;
        case '?':
          help ();
          break;
        default:
          help ();
          break;
      }
    }
    
    JapiSerialize japi = new JapiSerialize ();
    
    for (int i = g.getOptind(); i < argv.length ; i++)
    {
      File f = new File (argv[i]);
      if (f.exists ())
        japi.readFile (f);
      else
        japi.writeClass (argv[i]);
    }

    // In case AWT threads do not die appropriately
    System.exit (0);
  }
  
  private void readFile (File f)
  {
    try
    {
      LineNumberReader reader = new LineNumberReader (new BufferedReader (new FileReader (f)));
      String cls;
      while ((cls = reader.readLine ()) != null)
        writeClass (cls);
      reader.close ();
    }
    catch (Throwable t) 
    {
      System.err.println (t.getMessage ());
      System.exit (1);
    }
  }

  private Class _serial = null;
  private ObjectOutputStream _objout = null;  
  private File _file = null;
  
  private void writeClass (String clsName)
  {
    System.out.print (clsName);
    
    try
    {
      String filePrefix = clsName.replace ('.', File.separatorChar);
      StringBuffer buf = new StringBuffer (_outputDirectory);
      buf.append (File.separator);
      if (filePrefix.indexOf (File.separator) != -1)
        buf.append (filePrefix.substring (0, filePrefix.lastIndexOf (File.separator)));
      else
        buf.append (filePrefix);
      String dirPrefix = buf.toString ();
      File dir = new File (dirPrefix);
      if (! dir.isDirectory ())
        dir.mkdirs ();
    
      if (_xml)
      {
        _file = new File (_outputDirectory, filePrefix + XML_TYPE);
        _objout = new ObjOut (new BufferedOutputStream (new FileOutputStream (_file)));
      }
      else
      {
        _file = new File (_outputDirectory, filePrefix + BINARY_TYPE);        
        _objout = new ObjectOutputStream (new BufferedOutputStream (new FileOutputStream (_file)));
      }
      if (_serial == null)
      {
        _serial = Class.forName ("java.io.Serializable");
      }
    }
    catch (Throwable t)
    {
      System.err.println (": error, " + t.getMessage ());
      t.printStackTrace ();
      close (true);
      System.exit (1);
    }
      
    Object obj = null;
    boolean writeObj = false;
    try
    {
      Class cls = Class.forName (clsName);
      if (cls.isInterface ())
      {
        System.out.println (": ok, is interface");
        return;
      }
      int flags = cls.getModifiers ();
      if (! Modifier.isPublic (flags))
      {
        System.out.println (": ok, not public");
        return;
      }
      if (Modifier.isAbstract (flags))
      {
        System.out.println (": ok, abstract");
        return;
      }
      obj = getInstance (cls);
      if (_serial.isInstance (obj))
        writeObj = true;
    }
    catch (ClassNotFoundException cnfe)
    {
      System.out.println (": fail, class not found");
      close (true);
      return;
    }
    catch (UnsupportedOperationException e)
    {
      System.out.println (": fail, no instance");
      close (true);
      return;
    }
    catch (NoClassDefFoundError e)
    {
      System.out.println (": fail, class not found");
      close (true);
      return;
    }
    catch (Throwable t) 
    {
      System.out.println (": fail, " + t.getClass ().getName () + " : " + t.getMessage ());
//      t.printStackTrace (System.out);
      close (true);
      return;
    }
    
    try
    {
      if (writeObj)
      {
        _objout.writeObject (obj);
      }
    }
    catch (NotSerializableException e)
    {
      System.out.println (": ok, not serializable");
      close (true);
      return;
    }
    catch (SecurityException e)
    {
      System.out.println (": ok, security exception");
      close (true);
      return;
    }
    catch (org.omg.CORBA.BAD_OPERATION oe)
    {
      System.out.println (": ok, bad operation");
      close (true);
      return;
    }
    catch (NoClassDefFoundError e)
    {
      System.out.println (": fail, class not found");
      close (true);
      return;
    }
    catch (Throwable t)
    {
      System.out.println (": error, " + t.getMessage ());
//      t.printStackTrace ();
      close (true);
      return;
    }
    if (writeObj)
      System.out.println (": ok");
    else
      System.out.println (": ok, not serializable");

    close ();
  }

  private Object getInstance (Class cls)
  {
    try
    {
      Object obj = cls.newInstance ();
      return obj;
    }
    catch (Throwable t) { }
        
    // Try to find a simple constructor using primitive arguments
    Constructor[] constructors = cls.getConstructors ();    
    for (int index = 0; index < constructors.length; index++)
    {
      int flags = constructors[index].getModifiers ();
      if (Modifier.isPublic (flags))
      {
        Class[] params = constructors[index].getParameterTypes ();
        // Avoid constructors with parameters with the same type as cls
        boolean ignoreConstructor = false;
        for (int j = 0; j < params.length; j++)
        {
          if (cls.isAssignableFrom (params[j]))
          {
            ignoreConstructor = true;
            break;
          }
        }
        if (ignoreConstructor)
          continue;

        try
        {
          Object[] initargs = getParameters (params);
          Object obj = constructors[index].newInstance (initargs);
          return obj;
        }
        catch (Throwable t)
        {
          continue;
        }
      }
    }
    
    throw new UnsupportedOperationException ("unable to create instance");
  }

  private Object[] getParameters (Class[] params)
  {
    Object[] initargs = new Object[params.length];
    boolean paramsOkay = true;
    for (int j = 0; j < params.length; j++)
    {
      if (isPrimitive (params[j]))
        initargs[j] = getPrimitiveValue (params[j]);
      else
        initargs[j] = getInstance (params[j]);
    }
    return initargs;
  }
  
  private boolean isPrimitive (Class c)
  {
    if (c.isPrimitive ())
      return true;
    if (c.isAssignableFrom (java.lang.String.class))
      return true;
    return false;
  }

  private Object getPrimitiveValue (Class c)
  {
    if (c.isAssignableFrom (java.lang.Boolean.TYPE))
      return Boolean.TRUE;
    else if (c.isAssignableFrom (java.lang.Byte.TYPE))
      return new Byte ((byte)0);
    else if (c.isAssignableFrom (java.lang.Character.TYPE))
      return new Character ('a');
    else if (c.isAssignableFrom (java.lang.Short.TYPE))
      return new Short ((short)0);
    else if (c.isAssignableFrom (java.lang.Integer.TYPE))
      return new Integer (0);
    else if (c.isAssignableFrom (java.lang.Long.TYPE))
      return new Long (0);
    else if (c.isAssignableFrom (java.lang.Float.TYPE))
      return new Float (0.0f);
    else if (c.isAssignableFrom (java.lang.Double.TYPE))
      return new Double (0.0d);
    else if (c.isAssignableFrom (java.lang.String.class))
      return new String ("a");
    return null;
  }
  
  private void close (boolean delete)
  {    
    if (_objout != null)
    {
      try
      {
        _objout.flush ();
        _objout.close ();
      }
      catch (Throwable t) { }    
      try
      {
        if (delete)
          if (_file != null)
            _file.delete ();
        
      }
      catch (Throwable t) { }
    }
  }

  private void close ()
  {
    close (false);
  }
  
  private static void help ()
  {
    System.err.println ("JapiSerialize " + VERSION + ", (c) C. Brian Jones");
    System.err.println ();
    System.err.println ("japiserialize [OPTION] [FILE|CLASS]");
    System.err.println ("  -d <directory>    output directory");
    System.err.println ("  -x                xml output, default is binary");
    System.exit (1);
  }
}

