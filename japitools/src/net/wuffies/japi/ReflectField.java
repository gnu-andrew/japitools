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

class ReflectField implements FieldWrapper {
  private Field f;
  ReflectField(Field f) {
    this.f = f;
  }
  public int getModifiers() {
    return f.getModifiers();
  }
  public String getName() {
    return f.getName();
  }
  public String getType() {
    return ReflectClass.typeSig(f.getType());
  }
  public boolean isPrimitiveConstant() {
    int mods = f.getModifiers();
    // A nasty, evil, hacky heuristic which will guess right most of the time
    // and mess up horribly when it doesn't.
    if (Modifier.isStatic(mods) && Modifier.isFinal(mods) &&
        (Modifier.isPublic(mods) || Modifier.isProtected(mods)) &&
        (f.getType().isPrimitive() || getType().equals("java.lang.String"))) {
      return true;
    } else {
      return false;
    }
  }
  public Object getPrimitiveValue() {
    try {
      return f.get(null);
    } catch (IllegalAccessException e) {
      return null;
    }
  }
  public ClassWrapper getDeclaringClass() {
    return new ReflectClass(f.getDeclaringClass());
  }
  public boolean equals(Object o) {
    return o instanceof ReflectField &&
          ((ReflectField) o).getName().equals(getName());
  }
  public int compareTo(Object o) {
    return getName().compareTo(((ReflectField) o).getName());
  }
}
