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
import java.lang.reflect.*;
import java.io.ObjectStreamClass;

class ReflectClass implements ClassWrapper {
  private Class c;
  public ReflectClass(String name) throws ClassNotFoundException {
    this(Class.forName(name));
  }
  ReflectClass(Class c) {
    this.c = c;
  }
  public int getModifiers() {
    return c.getModifiers();
  }
  public String getName() {
    return c.getName();
  }
  public ClassWrapper getSuperclass() {
    Class s = c.getSuperclass();
    return s == null ? null : new ReflectClass(s);
  }
  public ClassWrapper[] getInterfaces() {
    Class[] ifaces = c.getInterfaces();
    ClassWrapper[] res = new ClassWrapper[ifaces.length];
    for (int i = 0; i < ifaces.length; i++) {
      res[i] = new ReflectClass(ifaces[i]);
    }
    return res;
  }
  public boolean isSerializable() {
    ObjectStreamClass stream = ObjectStreamClass.lookup(c);
    return stream != null;
  }
  public long getSerialVersionUID() {
    ObjectStreamClass stream = ObjectStreamClass.lookup(c);
    return stream.getSerialVersionUID();
  } 
  public FieldWrapper[] getFields() {
    Field[] fields = c.getFields();
    FieldWrapper[] res = new FieldWrapper[fields.length];
    for (int i = 0; i < fields.length; i++) {
      System.out.println(fields[i]);
      res[i] = new ReflectField(fields[i]);
    }
    return res;
  }
  public CallWrapper[] getCalls() {
    Method[] methods = c.getMethods();
    Constructor[] constructors = c.getConstructors();
    CallWrapper[] res = new CallWrapper[methods.length + constructors.length];
    for (int i = 0; i < constructors.length; i++) {
      res[i] = new ReflectConstructor(constructors[i]);
    }
    for (int i = 0; i < methods.length; i++) {
      res[i + constructors.length] = new ReflectMethod(methods[i], c);
    }
    return res;
  }
  public boolean isInterface() {
    return c.isInterface();
  }
}

