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

class JodeMethod implements CallWrapper {
  private MethodInfo m;
  private JodeClass jc;
  JodeMethod(MethodInfo m, JodeClass jc) {
    this.m = m;
    this.jc = jc;
  }
  public int getModifiers() {
    return m.getModifiers();
  }
  public String[] getParameterTypes() {
    String[] res = TypeSignature.getParameterTypes(m.getType());
    for (int i = 0; i < res.length; i++) {
      res[i] = JodeClass.typeFor(res[i]);
    }
    return res;
  }
  public String[] getExceptionTypes() {
    String[] res = m.getExceptions();
    if (res == null) return new String[0];
//  for (int i = 0; i < res.length; i++) {
//    res[i] = JodeClass.typeFor(res[i]);
//  }
    return res;
  }
  public String getName() {
    String name = m.getName();
    return "<init>".equals(name) ? "" : name;
  }
  public String getReturnType() {
    String name = m.getName();
    return "<init>".equals(name) ? "constructor" :
                                   JodeClass.typeFor(TypeSignature.getReturnType(m.getType()));
  }
  public ClassWrapper getDeclaringClass() {
    return jc;
  }
  public boolean isDup() {
    return false;
  }
  public boolean equals(Object o) {
    if (!(o instanceof JodeMethod)) return false;
    JodeMethod jm = (JodeMethod)o;
    if (!jm.getName().equals(getName())) return false;
    String[] p = getParameterTypes();
    String[] op = jm.getParameterTypes();
    if (p.length != op.length) return false;
    for (int i = 0; i < p.length; i++) {
      if (!p[i].equals(op[i])) return false;
    }
    return true;
  }
}

