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
  private int modifiers;
  private String[] parameterTypes;
  private String[] exceptionTypes;
  private String name;
  private String returnType;
  private JodeClass jc;
  JodeMethod(MethodInfo m, JodeClass jc) {
    modifiers = m.getModifiers();
    parameterTypes = TypeSignature.getParameterTypes(m.getType());
    exceptionTypes = m.getExceptions();
    if (exceptionTypes == null) exceptionTypes = new String[0];
    name = m.getName();
    if ("<init>".equals(name)) {
      name = "";
      returnType = "constructor";
    } else {
      returnType = TypeSignature.getReturnType(m.getType());
    }
    this.jc = jc;
  }
  public int getModifiers() {
    return modifiers;
  }
  public String[] getParameterTypes() {
    return parameterTypes;
  }
  public String[] getExceptionTypes() {
    return exceptionTypes;
  }
  public String getName() {
    return name;
  }
  public String getReturnType() {
    return returnType;
  }
  public ClassWrapper getDeclaringClass() {
    return jc;
  }
  public boolean isDup() {
    return false;
  }
  public boolean equals(Object o) {
    if (!(o instanceof JodeMethod)) return false;
    return getSig().equals(((JodeMethod) o).getSig());
  }
  public int compareTo(Object o) {
    int res = getSig().compareTo(((JodeMethod) o).getSig());
    return res;
  }
  private String sig;
  private String getSig() {
    if (sig == null) {
      sig = getName() + "(";
      String comma = "";
      for (int j = 0; j < parameterTypes.length; j++) {
        sig += comma + parameterTypes[j];
        comma = ",";
      }
      sig += ")";
    }
    return sig;
  }
  public String toString() {
    return getSig();
  }
}
