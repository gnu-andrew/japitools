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
import jode.bytecode.*;
import java.util.Vector;
import java.lang.reflect.Modifier;
import jode.obfuscator.ClassIdentifier;
import org.gnu.java.util.collections.Arrays;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.gnu.java.util.collections.Comparator;

class JodeClass implements ClassWrapper {
  private ClassInfo c;
  private ClassIdentifier ident;
  public JodeClass(String name) {
    this(ClassInfo.forName(name));
  }
  JodeClass(ClassInfo c) {
    c.loadInfo(ClassInfo.MOSTINFO);
    this.c = c;
  }
  public int getModifiers() {
    return c.getModifiers();
  }
  public String getName() {
    return c.getName();
  }
  public ClassWrapper getSuperclass() {
    if (isInterface()) return null;
    ClassInfo s = c.getSuperclass();
    return s == null ? null : new JodeClass(s);
  }
  public ClassWrapper[] getInterfaces() {
    ClassInfo[] ifaces = c.getInterfaces();
    ClassWrapper[] res = new JodeClass[ifaces.length];
    for (int i = 0; i < ifaces.length; i++) {
      res[i] = new JodeClass(ifaces[i]);
    }
    return res;
  }
  public boolean isSerializable() {
    return ClassInfo.forName("java.io.Serializable").implementedBy(c);
  }

  // This code is partially based on GNU Classpath's implementation which is
  // (c) FSF and licensed under the GNU Library General Public License. Some of
  // the modifications to make it work on ClassInfos are based on code in
  // the jode.obfuscator package.
  public long getSerialVersionUID() {
    FieldInfo f = c.findField("serialVersionUID", "J");
    if (f != null) {
      int mods = f.getModifiers();
      if (Modifier.isFinal(mods) && Modifier.isStatic(mods)) {
        Long l = (Long)f.getConstant();
        if (l != null) return l.longValue();
      }
    }

    // c didn't define serialVersionUID, so we have to compute it
    try {
      final MessageDigest md = MessageDigest.getInstance("SHA");
      OutputStream digest = new OutputStream() {
        public void write(int b) {
          md.update((byte) b);
        }

        public void write(byte[] data, int offset, int length) {
          md.update(data, offset, length);
        }
      };
      DataOutputStream data_out = new DataOutputStream(digest);

      data_out.writeUTF(c.getName());

      int modifiers = c.getModifiers();
      // just look at interesting bits
      modifiers = modifiers & (Modifier.ABSTRACT  | Modifier.FINAL |
                               Modifier.INTERFACE | Modifier.PUBLIC);
      data_out.writeInt(modifiers);

      ClassInfo[] interfaces = c.getInterfaces();
      Arrays.sort(interfaces, new Comparator() {
        public int compare(Object o1, Object o2) {
          return
              ((ClassInfo)o1).getName().compareTo(((ClassInfo)o2).getName());
        }
      });
      for (int i = 0; i < interfaces.length; i++) {
        data_out.writeUTF(interfaces[i].getName());
      }

      FieldInfo field;
      FieldInfo[] fields = c.getFields();
      Arrays.sort(fields, new Comparator() {
        public int compare(Object o1, Object o2) {
          return compare((FieldInfo)o1, (FieldInfo)o2);
        }
        public int compare(FieldInfo f1, FieldInfo f2) {
          if (f1.getName().equals(f2.getName())) {
            return f1.getType().compareTo(f2.getType());
          } else {
            return f1.getName().compareTo(f2.getName());
          }
        }
      });

      for (int i = 0; i < fields.length; i++) {
        field = fields[i];
        modifiers = field.getModifiers();
        if (Modifier.isPrivate (modifiers) &&
            (Modifier.isStatic(modifiers) ||
             Modifier.isTransient(modifiers))) {
          continue;
        }

        data_out.writeUTF(field.getName());
        data_out.writeInt(modifiers);
        data_out.writeUTF(field.getType());
      }

      MethodInfo method;
      MethodInfo[] methods = c.getMethods();
      Arrays.sort(methods, new Comparator() {
        public int compare(Object o1, Object o2) {
          return compare((MethodInfo)o1, (MethodInfo)o2);
        }
        public int compare(MethodInfo m1, MethodInfo m2) {
          if (m1.getName().equals(m2.getName())) {
            return m1.getType().compareTo(m2.getType());
          } else {
            return m1.getName().compareTo(m2.getName());
          }
        }
      });

      for (int i = 0; i < methods.length; i++) {
        method = methods[i];
        modifiers = method.getModifiers();
        if (Modifier.isPrivate(modifiers)) {
          continue;
        }

        data_out.writeUTF(method.getName());
        data_out.writeInt(modifiers);
        // the replacement of '/' with '.' was needed to make computed
        // SUID's agree with those computed by JDK
        data_out.writeUTF(method.getType().replace('/', '.'));
      }

      data_out.close ();
      byte[] sha = md.digest ();
      long result = 0;
      int len = sha.length < 8 ? sha.length : 8;
      for (int i=0; i < len; i++)
        result += (long)(sha[i] & 0xFF) << (8 * i);
  
      return result;
    }
    catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("The SHA algorithm was not found to use in " +
                                 "computing the Serial Version UID for class " +
                                 c.getName());
    } catch (IOException ioe) {
      throw new RuntimeException(ioe.getMessage());
    }
  }
  private void addFields(Vector v) {
    FieldInfo[] fields = c.getFields();
    for (int i = 0; i < fields.length; i++) {
      JodeField jf = new JodeField(fields[i], this);
      if (!v.contains(jf)) v.addElement(jf);
    }
    JodeClass sc = (JodeClass)getSuperclass();
    if (sc != null) sc.addFields(v);
    JodeClass[] ifaces = (JodeClass[])getInterfaces();
    for (int i = 0; i < ifaces.length; i++) {
      ifaces[i].addFields(v);
    }
  }
  public FieldWrapper[] getFields() {
    Vector v = new Vector();
    addFields(v);
    FieldWrapper[] res = new FieldWrapper[v.size()];
    v.copyInto(res);
    return res;
  }
  private void addMethods(Vector v, boolean constr) {
    MethodInfo[] fields = c.getMethods();
    for (int i = 0; i < fields.length; i++) {
      JodeMethod jm = new JodeMethod(fields[i], this);
      if ((constr || !"".equals(jm.getName())) && !v.contains(jm)) {
        v.addElement(jm);
      }
    }
    JodeClass sc = (JodeClass)getSuperclass();
    if (sc != null) sc.addMethods(v, false);
    if (isInterface()) {
      JodeClass[] ifaces = (JodeClass[])getInterfaces();
      for (int i = 0; i < ifaces.length; i++) {
        ifaces[i].addMethods(v, false);
      }
    }
  }
  public CallWrapper[] getCalls() {
    Vector v = new Vector();
    addMethods(v, true);
    CallWrapper[] res = new CallWrapper[v.size()];
    v.copyInto(res);
    return res;
  }
  public boolean isInterface() {
    return c.isInterface();
  }
  public static void setClassPath(String cp) {
    ClassInfo.setClassPath(cp);
  }
  static String typeFor(String sigType) {
    if (sigType.startsWith("[")) {
      return sigType.replace('/', '.');
    } else if (sigType.length() == 1) {
      switch (sigType.charAt(0)) {
        case 'Z': return "boolean";
        case 'B': return "byte";
        case 'C': return "char";
        case 'D': return "double";
        case 'F': return "float";
        case 'I': return "int";
        case 'J': return "long";
        case 'S': return "short";
        case 'V': return "void";
        default: throw new IllegalArgumentException(sigType);
      }
    } else if (sigType.startsWith("L") && sigType.endsWith(";")) {
      return sigType.substring(1, sigType.length() - 1).replace('/', '.');
    } else {
      throw new IllegalArgumentException(sigType);
    }
  }
}

