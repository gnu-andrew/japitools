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

class ReflectMethod implements CallWrapper {
  private Method m;
  private Class c; // The code for isDup needs a class to look for dups against.
  ReflectMethod(Method m, Class c) {
    this.m = m;
    this.c = c;
  }
  public int getModifiers() {
    return m.getModifiers();
  }
  public String[] getParameterTypes() {
    Class[] params = m.getParameterTypes();
    String[] res = new String[params.length];
    for (int i = 0; i < params.length; i++) {
      res[i] = params[i].getName();
    }
    return res;
  }
  public String[] getExceptionTypes() {
    Class[] excps = m.getExceptionTypes();
    String[] res = new String[excps.length];
    for (int i = 0; i < excps.length; i++) {
      res[i] = excps[i].getName();
    }
    return res;
  }
  public String getName() {
    return m.getName();
  }
  public String getReturnType() {
    return m.getReturnType().getName();
  }
  public ClassWrapper getDeclaringClass() {
    return new ReflectClass(m.getDeclaringClass());
  }
  public boolean isDup() {
    try {
      return !m.equals(c.getMethod(m.getName(), m.getParameterTypes()));
    } catch (NoSuchMethodException e) {
      return false;
    }
  }
}

