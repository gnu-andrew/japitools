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

class ReflectConstructor implements CallWrapper {
  private Constructor c;
  ReflectConstructor(Constructor c) {
    this.c = c;
  }
  public int getModifiers() {
    return c.getModifiers();
  }
  public String[] getParameterTypes() {
    Class[] params = c.getParameterTypes();
    String[] res = new String[params.length];
    for (int i = 0; i < params.length; i++) {
      res[i] = ReflectClass.typeSig(params[i]);
    }
    return res;
  }
  public String[] getExceptionTypes() {
    Class[] excps = c.getExceptionTypes();
    String[] res = new String[excps.length];
    for (int i = 0; i < excps.length; i++) {
      res[i] = excps[i].getName();
    }
    return res;
  }
  public String getName() {
    return "";
  }
  public String getReturnType() {
    return "constructor";
  }
  public boolean isDup() {
    return false;
  }
  public ClassWrapper getDeclaringClass() {
    return new ReflectClass(c.getDeclaringClass());
  }
  public int compareTo(Object o) {
    return o instanceof ReflectMethod ? -1 :
           getSig().compareTo(((ReflectConstructor) o).getSig());
  }
  private String sig;
  private String getSig() {
    if (sig == null) {
      sig = getName() + "(";
      String[] params = getParameterTypes();
      String comma = "";
      for (int j = 0; j < params.length; j++) {
        sig += comma + params[j];
        comma = ",";
      }
      sig += ")";
    }
    return sig;
  }
}
