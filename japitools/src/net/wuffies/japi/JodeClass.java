///////////////////////////////////////////////////////////////////////////////
// Japize - Output a machine-readable description of a Java API.
// Copyright (C) 2000-2002  Stuart Ballard <sballard@netreach.net>
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
import jode.bytecode.*;
import java.util.TreeSet;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Modifier;
import jode.obfuscator.ClassIdentifier;
import java.util.Arrays;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

class JodeClass implements ClassWrapper {
  private static final Map loaded = new HashMap();
  ClassInfo c;
  private ClassIdentifier ident;
  public JodeClass(String name) {
    this(ClassInfo.forName(name));
  }
  JodeClass(ClassInfo c) {
    this.c = c;
    synchronized (loaded) {
      Integer refCount = (Integer) loaded.get(c.getName());
      if (refCount == null) {
        refCount = new Integer(1);
      } else {
        refCount = new Integer(refCount.intValue() + 1);
      }
      loaded.put(c.getName(), refCount);
    }
  }
  public void finalize() {
    if (c != null) {
      synchronized (loaded) {
        Integer refCount = (Integer) loaded.get(c.getName());
        if (refCount == null) refCount = new Integer(1);
        refCount = new Integer(refCount.intValue() - 1);
        if (refCount.intValue() < 0) refCount = new Integer(0);
        loaded.put(c.getName(), refCount);
        if (refCount.intValue() <= 0) {
          synchronized (c) {
            c.dropInfo(ClassInfo.FULLINFO);
          }
        }
      }
    }
  }
  public int getModifiers() {
    return c.getModifiers();
  }
  public String getName() {
    return c.getName();
  }
  public ClassWrapper getSuperclass() {
    ClassInfo s;
    synchronized (c) {
      c.loadInfo(ClassInfo.HIERARCHY);
      if (isInterface()) return null;
      s = c.getSuperclass();
    }
    return s == null ? null : new JodeClass(s);
  }
  public ClassWrapper[] getInterfaces() {
    ClassInfo[] ifaces;
    synchronized (c) {
      c.loadInfo(ClassInfo.HIERARCHY);
      ifaces = c.getInterfaces();
    }
    ClassWrapper[] res = new JodeClass[ifaces.length];
    for (int i = 0; i < ifaces.length; i++) {
      res[i] = new JodeClass(ifaces[i]);
    }
    return res;
  }
  private static final ClassInfo serializable =
      ClassInfo.forName("java.io.Serializable");
  public boolean isSerializable() {
    synchronized (c) {
      c.loadInfo(ClassInfo.HIERARCHY);
      return serializable.implementedBy(c);
    }
  }

  // This code is partially based on GNU Classpath's implementation which is
  // (c) FSF and licensed under the GNU Library General Public License. Some of
  // the modifications to make it work on ClassInfos are based on code in
  // the jode.obfuscator package.
  public long getSerialVersionUID() {
    synchronized (c) {
      c.loadInfo(ClassInfo.HIERARCHY | ClassInfo.FIELDS | ClassInfo.METHODS |
                 ClassInfo.CONSTANTS);
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
  }
  private void addFields(TreeSet fieldSet) {
    FieldInfo[] fields;
    synchronized (c) {
      c.loadInfo(ClassInfo.FIELDS);
      fields = c.getFields();
      for (int i = 0; i < fields.length; i++) {
        JodeField jf = new JodeField(fields[i], this);
        fieldSet.add(jf);
      }
    }
    JodeClass sc = (JodeClass)getSuperclass();
    if (sc != null) sc.addFields(fieldSet);
    JodeClass[] ifaces = (JodeClass[])getInterfaces();
    for (int i = 0; i < ifaces.length; i++) {
      ifaces[i].addFields(fieldSet);
    }
  }
  public FieldWrapper[] getFields() {
    TreeSet fieldSet = new TreeSet();
    addFields(fieldSet);
    FieldWrapper[] res = new FieldWrapper[fieldSet.size()];
    res = (FieldWrapper[]) fieldSet.toArray(res);
    return res;
  }
  private void addMethods(TreeSet methodSet, boolean constr) {
    MethodInfo[] methods;
    synchronized (c) {
      c.loadInfo(ClassInfo.METHODS);
      methods = c.getMethods();
      for (int i = 0; i < methods.length; i++) {
        JodeMethod jm = new JodeMethod(methods[i], this);
        if (!"<clinit>".equals(jm.getName()) &&
            (constr || !"".equals(jm.getName()))) {
          methodSet.add(jm);
        }
      }
    }
    JodeClass sc = (JodeClass)getSuperclass();
    if (sc != null) sc.addMethods(methodSet, false);
    if (isInterface()) {
      JodeClass[] ifaces = (JodeClass[])getInterfaces();
      for (int i = 0; i < ifaces.length; i++) {
        ifaces[i].addMethods(methodSet, false);
      }
    }
  }
  public CallWrapper[] getCalls() {
    TreeSet callSet = new TreeSet();
    addMethods(callSet, true);
    CallWrapper[] res = new CallWrapper[callSet.size()];
    res = (CallWrapper[]) callSet.toArray(res);
    return res;
  }
  public boolean isInterface() {
    return c.isInterface();
  }
  public static void setClassPath(String cp) {
    ClassInfo.setClassPath(cp);
  }
}

